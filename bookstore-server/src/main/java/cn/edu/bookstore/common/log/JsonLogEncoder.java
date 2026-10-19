package cn.edu.bookstore.common.log;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.encoder.EncoderBase;
import cn.edu.bookstore.common.RequestContext;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 结构化日志编码器：把一条日志编码成一行 JSON。
 *
 * <h3>为什么不用现成的 logstash-logback-encoder</h3>
 * 常见的做法是引入 {@code net.logstash.logback:logstash-logback-encoder}，但那是为了对接
 * ELK 生态、支持大量可选字段与多种输出格式。本项目只需要「一行一条标准 JSON」，
 * 用 Jackson（已随 spring-boot-starter-web 引入）手写不到百行即可，还少一个依赖。
 * 与 {@code CacheCircuitBreaker} 自行实现的取舍一致：<b>能力够用就不引依赖</b>。
 *
 * <h3>为什么生产环境要用 JSON 日志</h3>
 * 文本日志是给人看的，机器很难可靠地解析（正则一改就崩）。JSON 日志让采集端
 * （Filebeat / Vector / 云日志服务）能直接按字段解析与索引，才能做
 * 「按 requestId 聚合一条请求的全部日志」「统计 5xx 出现次数」这类操作。
 * 本地开发仍保留文本格式 —— 人读文本远快于读 JSON。
 *
 * <h3>字段说明</h3>
 * <pre>
 *   timestamp        时间（毫秒级，本地时区）
 *   level            日志级别
 *   thread           线程名（排查线程池/异步问题时必需）
 *   logger           日志来源类名
 *   requestId        链路 ID，与响应头 X-Request-Id 一致（来自 MDC，可能缺失）
 *   message          格式化后的日志正文
 *   exception        异常类名（仅异常日志）
 *   exceptionMessage 异常信息（仅异常日志）
 *   stackTrace       完整堆栈（仅异常日志）
 * </pre>
 *
 * @author 毕设项目组
 */
public class JsonLogEncoder extends EncoderBase<ILoggingEvent> {

    /** Jackson 线程安全，可复用单例（logback 会在多个线程上调用 encode） */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    @Override
    public byte[] encode(ILoggingEvent event) {
        if (event == null) {
            return null;
        }
        // LinkedHashMap 保证字段顺序稳定：日志文件用肉眼扫、或 grep 某个字段时都更友好
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("timestamp", TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(event.getTimeStamp())));
        node.put("level", event.getLevel().toString());
        node.put("thread", event.getThreadName());
        node.put("logger", event.getLoggerName());

        Map<String, String> mdc = event.getMDCPropertyMap();
        String requestId = mdc == null ? null : mdc.get(RequestContext.REQUEST_ID_KEY);
        if (requestId != null && !requestId.isBlank()) {
            node.put("requestId", requestId);
        }

        node.put("message", event.getFormattedMessage());

        IThrowableProxy throwable = event.getThrowableProxy();
        if (throwable != null) {
            node.put("exception", throwable.getClassName());
            node.put("exceptionMessage", throwable.getMessage());
            node.put("stackTrace", ThrowableProxyUtil.asString(throwable));
        }

        try {
            byte[] json = MAPPER.writeValueAsBytes(node);
            // 不直接返回 json：JsonLogEncoder 的输出由 RollingFileAppender 逐条写入，
            // 必须自己补换行符，否则所有日志会挤在一行里（消费端只认「一行一条」）。
            byte[] line = new byte[json.length + 1];
            System.arraycopy(json, 0, line, 0, json.length);
            line[json.length] = '\n';
            return line;
        } catch (Exception e) {
            // 兜底：日志编码失败绝不能反过来把业务搞崩，也不能让这条日志凭空消失。
            // 退回一条最简 JSON（只保证结构合法），把「编码失败」这件事本身记下来。
            String fallback = "{\"level\":\"" + event.getLevel()
                    + "\",\"logger\":\"" + event.getLoggerName()
                    + "\",\"message\":\"" + escape(event.getFormattedMessage())
                    + "\",\"encodeError\":\"" + escape(String.valueOf(e.getMessage())) + "\"}\n";
            return fallback.getBytes(StandardCharsets.UTF_8);
        }
    }

    /** 极简转义：仅在兜底路径使用，正常路径由 Jackson 负责转义 */
    private String escape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", " ").replace("\n", " ");
    }

    /** 无头部内容（JSON 行式日志不需要） */
    @Override
    public byte[] headerBytes() {
        return null;
    }

    /** 无尾部内容 */
    @Override
    public byte[] footerBytes() {
        return null;
    }
}
