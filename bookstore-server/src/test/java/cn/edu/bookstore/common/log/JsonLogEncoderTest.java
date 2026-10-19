package cn.edu.bookstore.common.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 结构化日志编码器测试。
 *
 * <p>核心诉求只有一条：<b>输出必须是一行合法 JSON</b>。日志采集端按行切分、
 * 按字段解析，一旦某条日志不是合法 JSON（比如异常信息里的换行没被转义），
 * 采集链路就会在那一条上断掉或错位。因此这里用 Jackson 反向解析来验证。
 *
 * @author 毕设项目组
 */
@DisplayName("结构化 JSON 日志编码器")
class JsonLogEncoderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonLogEncoder encoder;
    private LoggerContext loggerContext;

    @BeforeEach
    void setUp() {
        encoder = new JsonLogEncoder();
        loggerContext = new LoggerContext();
    }

    /** 构造一条日志事件；mdc 传 null 表示无链路上下文 */
    private LoggingEvent event(Level level, String message, Throwable throwable, Map<String, String> mdc) {
        return new LoggingEvent("cn.edu.bookstore.Test", loggerContext.getLogger("cn.edu.bookstore.Test"),
                level, message, throwable, null) {
            @Override
            public Map<String, String> getMDCPropertyMap() {
                return mdc;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(byte[] encoded) throws Exception {
        return MAPPER.readValue(encoded, Map.class);
    }

    @Test
    @DisplayName("普通日志：输出一行合法 JSON，含时间/级别/线程/来源/正文")
    void encodesPlainEventAsJson() throws Exception {
        byte[] encoded = encoder.encode(event(Level.INFO, "用户登录成功 userId=1", null, null));

        assertNotNull(encoded);
        Map<String, Object> node = parse(encoded);
        assertEquals("INFO", node.get("level"));
        assertEquals("用户登录成功 userId=1", node.get("message"));
        assertEquals("cn.edu.bookstore.Test", node.get("logger"));
        assertNotNull(node.get("timestamp"));
        assertNotNull(node.get("thread"));
        // 没有链路上下文时不应输出空字符串字段（避免采集端把 "" 当成有效值）
        assertFalse(node.containsKey("requestId"));
    }

    @Test
    @DisplayName("每条日志以换行结尾：保证「一行一条」")
    void endsWithNewline() {
        byte[] encoded = encoder.encode(event(Level.INFO, "hello", null, null));

        assertNotNull(encoded);
        assertTrue(encoded.length > 0);
        assertEquals('\n', (char) encoded[encoded.length - 1]);
        // 除结尾换行外，正文里不应出现裸换行（否则一条日志会被拆成多行）
        String text = new String(encoded, StandardCharsets.UTF_8);
        assertEquals(1, text.split("\n").length);
    }

    @Test
    @DisplayName("正文里的换行与引号被转义，仍是单行合法 JSON")
    void escapesMultilineMessage() throws Exception {
        byte[] encoded = encoder.encode(event(Level.WARN, "第一行\n第二行\t带\"引号\"", null, null));

        String text = new String(encoded, StandardCharsets.UTF_8);
        assertEquals(1, text.split("\n").length, "多行正文必须被转义成单行");
        Map<String, Object> node = parse(encoded);
        assertEquals("第一行\n第二行\t带\"引号\"", node.get("message"), "转义后解析回来应与原文一致");
    }

    @Test
    @DisplayName("带 MDC 上下文时输出链路 ID（与响应头 X-Request-Id 一致）")
    void includesRequestIdFromMdc() throws Exception {
        Map<String, String> mdc = new LinkedHashMap<>();
        mdc.put("requestId", "abc123def456");

        byte[] encoded = encoder.encode(event(Level.INFO, "下单成功", null, mdc));

        assertEquals("abc123def456", parse(encoded).get("requestId"));
    }

    @Test
    @DisplayName("异常日志：输出异常类、异常信息与完整堆栈")
    void includesExceptionDetails() throws Exception {
        Throwable ex = new IllegalArgumentException("库存不足");
        ex.fillInStackTrace();

        byte[] encoded = encoder.encode(event(Level.ERROR, "下单失败", ex, null));

        Map<String, Object> node = parse(encoded);
        assertEquals("java.lang.IllegalArgumentException", node.get("exception"));
        assertEquals("库存不足", node.get("exceptionMessage"));
        String stackTrace = String.valueOf(node.get("stackTrace"));
        assertTrue(stackTrace.contains("IllegalArgumentException"), "堆栈应包含异常类名");
        assertTrue(stackTrace.contains("includesExceptionDetails"), "堆栈应包含抛出位置");
        // 堆栈里的换行同样必须被转义，否则整条日志会被拆成很多行
        assertEquals(1, new String(encoded, StandardCharsets.UTF_8).split("\n").length);
    }

    @Test
    @DisplayName("non-null 约定：空事件返回 null 而不是抛异常")
    void nullEventReturnsNull() {
        assertNull(encoder.encode(null));
        assertNull(encoder.headerBytes());
        assertNull(encoder.footerBytes());
    }
}
