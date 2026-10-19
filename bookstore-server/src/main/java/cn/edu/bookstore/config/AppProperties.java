package cn.edu.bookstore.config;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * 集中配置：所有可配置项统一在 application.yml / 环境变量中管理。
 *
 * <p>约定：
 * <ul>
 *     <li>密钥、数据库口令等敏感信息只出现在 .env / 环境变量或本地 application-local.yml，
 *         绝不硬编码、不提交到代码库；</li>
 *     <li>服务启动时立即校验关键配置，缺失即快速失败，避免运行到一半才报错。</li>
 * </ul>
 *
 * @author 毕设项目组
 */
@Data
@Slf4j
@Validated
@ConfigurationProperties(prefix = "bookstore")
public class AppProperties {

    /** JWT 相关配置 */
    private Jwt jwt = new Jwt();

    /** 文件上传相关配置 */
    private Upload upload = new Upload();

    /** 跨域白名单 */
    private Cors cors = new Cors();

    /** 文件存储方式（本地磁盘 / MinIO 对象存储） */
    private Storage storage = new Storage();

    /** 缓存配置 */
    private Cache cache = new Cache();

    /** 高德开放平台配置（地理编码地址自动解析） */
    private Gaode gaode = new Gaode();

    /** 订单相关配置 */
    private Order order = new Order();

    /** 支付相关配置 */
    private Pay pay = new Pay();

    /** JWT 令牌配置 */
    @Data
    public static class Jwt {
        /** 签名密钥，生产环境必须通过环境变量注入 */
        @NotBlank(message = "bookstore.jwt.secret 不能为空")
        private String secret = "bookstore-app-dev-secret-please-change-in-production";

        /** 访问令牌有效期（小时），毕设场景可放长一些，避免频繁重新登录 */
        private int expireHours = 72;
    }

    /** 文件上传配置 */
    @Data
    public static class Upload {
        /** 文件落盘根目录，相对路径时基于服务运行目录 */
        private String baseDir = "./upload";

        /** 单文件大小上限（MB） */
        private int maxSizeMb = 5;

        /** 对外访问的 URL 前缀，如 /files */
        private String accessPrefix = "/files";
    }

    /** 跨域配置 */
    @Data
    public static class Cors {
        /**
         * 允许的前端来源。开发环境可用 http://localhost:5173 等；
         * 生产环境必须写明确域名，禁止 "*"。
         */
        private List<String> allowedOrigins = List.of("http://localhost:5173", "http://localhost:8080");
    }

    /** 文件存储配置 */
    @Data
    public static class Storage {

        /** 存储类型：local-本地磁盘（默认） / minio-对象存储 */
        private String type = "local";

        private Minio minio = new Minio();

        /** MinIO 对象存储配置 */
        @Data
        public static class Minio {
            /** 服务地址，如 http://127.0.0.1:9000 */
            private String endpoint = "http://127.0.0.1:9000";

            /** 访问密钥，生产环境必须通过环境变量注入 */
            private String accessKey = "minioadmin";

            /** 密钥，生产环境必须通过环境变量注入 */
            private String secretKey = "minioadmin";

            /** 存放封面的存储桶，服务启动时自动创建并设为公开读 */
            private String bucket = "bookstore-cover";

            /** 对外访问地址；留空表示与 endpoint 相同 */
            private String publicBaseUrl = "";
        }
    }

    /** 缓存配置 */
    @Data
    public static class Cache {

        /**
         * 是否启用缓存。
         *
         * <p>关闭后 Spring 缓存切面不会注册，@Cacheable 等注解成为空操作 ——
         * 排障或压测时想绕开缓存，改这一个开关即可，无需改代码。
         */
        private boolean enabled = true;

        /** 缓存有效期（分钟） */
        private int ttlMinutes = 30;

        /** 缓存键统一前缀，便于按业务清理 */
        private String keyPrefix = "bookstore:";
    }

    /**
     * 高德开放平台 —— Web服务API 配置。
     *
     * <p>安全说明：
     * <ul>
     *     <li>key 必须配置，secret 可选；secret 留空时自动降级为「仅 key」模式，
     *         此时高德控制台需选择「key 安全模式」；</li>
     *     <li>secret 用于数字签名 sig=MD5(sort(params) + secret)，防止他人盗用 key；</li>
     *     <li>key 和 secret 只在 application-local.yml 或环境变量中配置，绝不硬编码。</li>
     * </ul>
     */
    @Data
    public static class Gaode {
        /** Web服务API key（高德控制台申请） */
        @NotBlank(message = "bookstore.gaode.key 不能为空")
        private String key = "";

        /** 签名密钥（高德控制台「签名管理」开启后生成）；留空则仅用 key 模式 */
        private String secret = "";

        /** API 基址 */
        private String baseUrl = "https://restapi.amap.com/v3/geocode";

        /** 单次请求超时（毫秒） */
        private int timeoutMs = 5000;

        /** 同一用户地址解析接口限流窗口（秒） */
        private int rateLimitSeconds = 10;

        /** 多候选地址最多返回条数（防止刷接口） */
        private int maxCandidates = 5;
    }

    /** 订单配置 */
    @Data
    public static class Order {

        /**
         * 待支付订单的超时时间（分钟）。
         *
         * <p>超过该时长仍未支付的订单会被定时任务自动取消并回补库存。
         */
        private int timeoutMinutes = 30;

        /** 是否启用「超时订单自动取消」定时任务 */
        private boolean autoCancelEnabled = true;

        /**
         * 定时任务执行周期（cron 表达式）。
         *
         * <p>默认每 5 分钟扫一次。答辩演示时若不希望等，可调成每分钟，
         * 或直接调管理端的手动触发接口立即执行一次。
         */
        private String autoCancelCron = "0 */5 * * * ?";

        /**
         * 单次执行最多处理的订单数。
         *
         * <p>给批量取消加上限，避免历史积压过多时一次性开一个超长事务，
         * 把数据库连接和锁占用太久。
         */
        private int autoCancelBatchSize = 200;
    }

    /**
     * 支付配置。
     *
     * <p>设计思路与「文件存储」一致：把「用哪种支付渠道」抽成配置项，
     * 业务代码只依赖 PayService 接口，换渠道不改业务逻辑。
     */
    @Data
    public static class Pay {

        /**
         * 支付渠道：mock-模拟支付（默认） / alipay-支付宝沙箱。
         *
         * <p>默认 mock 是为了保证答辩现场零外部依赖：
         * 不需要联网、不需要沙箱账号，点一下就能完成支付、跑通订单流转。
         */
        private String type = "mock";

        /** 模拟支付配置 */
        private Mock mock = new Mock();

        /** 支付宝沙箱配置 */
        private Alipay alipay = new Alipay();

        /** 模拟支付：不需要任何参数，仅作为配置结构占位，便于后续扩展 */
        @Data
        public static class Mock {
            /**
             * 模拟支付时人为延迟的毫秒数。
             *
             * <p>默认 0。设为几百毫秒可以更真实地演示「支付中」的 loading 状态，
             * 也方便在演示时观察前端的加载交互。
             */
            private long delayMillis = 0;
        }

        /** 支付宝沙箱配置：真实值必须通过环境变量注入，不得写进代码库 */
        @Data
        public static class Alipay {

            /** 沙箱应用 APPID */
            private String appId = "";

            /** 应用私钥（PKCS8，Base64，不含头尾标识） */
            private String privateKey = "";

            /** 支付宝公钥，用于验证异步通知的签名 */
            private String alipayPublicKey = "";

            /** 网关地址：沙箱为 https://openapi-sandbox.dl.alipaydev.com/gateway.do */
            private String gatewayUrl = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";

            /** 异步通知地址（必须公网可达，本地调试需内网穿透） */
            private String notifyUrl = "";

            /** 同步跳转地址（用户支付完成后浏览器跳回的页面） */
            private String returnUrl = "";

            /** 签名算法，支付宝沙箱使用 RSA2 */
            private String signType = "RSA2";

            /** 支付单超时时间（分钟），与订单超时时间保持一致更符合直觉 */
            private int timeoutMinutes = 30;
        }
    }

    /**
     * 启动自检：配置不合法时直接抛异常终止启动（快速失败）。
     */
    @PostConstruct
    public void validate() {
        if (jwt.getSecret().length() < 16) {
            throw new IllegalStateException("配置校验失败：bookstore.jwt.secret 长度不能少于 16 位");
        }
        if (upload.getMaxSizeMb() <= 0 || upload.getMaxSizeMb() > 50) {
            throw new IllegalStateException("配置校验失败：bookstore.upload.max-size-mb 需在 1~50 之间");
        }
        if (cors.getAllowedOrigins().contains("*")) {
            log.warn("安全提示：CORS 当前允许任意来源（*），生产环境请改为明确的前端域名");
        }
        if (!List.of("local", "minio").contains(storage.getType())) {
            throw new IllegalStateException(
                    "配置校验失败：bookstore.storage.type 只能是 local 或 minio，当前为 " + storage.getType());
        }
        if (order.getTimeoutMinutes() <= 0) {
            throw new IllegalStateException("配置校验失败：bookstore.order.timeout-minutes 必须大于 0");
        }
        if (order.getAutoCancelBatchSize() <= 0 || order.getAutoCancelBatchSize() > 5000) {
            throw new IllegalStateException(
                    "配置校验失败：bookstore.order.auto-cancel-batch-size 需在 1~5000 之间");
        }
        if (!List.of("mock", "alipay").contains(pay.getType())) {
            throw new IllegalStateException(
                    "配置校验失败：bookstore.pay.type 只能是 mock 或 alipay，当前为 " + pay.getType());
        }
        if ("alipay".equals(pay.getType())) {
            // 选支付宝却不给凭证，启动时就说清楚，避免等用户点了支付才发现
            if (pay.getAlipay().getAppId().isBlank() || pay.getAlipay().getPrivateKey().isBlank()) {
                throw new IllegalStateException("配置校验失败：pay.type=alipay 时必须配置 "
                        + "bookstore.pay.alipay.app-id 与 private-key（建议用环境变量注入）");
            }
            log.warn("支付渠道已切换为支付宝沙箱（appId={}），请确认已配置回调地址且沙箱账号可用",
                    pay.getAlipay().getAppId());
        }
        log.info("配置加载完成：jwtExpireHours={}, uploadBaseDir={}, storageType={}, allowedOrigins={}, "
                        + "orderTimeoutMinutes={}, autoCancelEnabled={}, payType={}",
                jwt.getExpireHours(), upload.getBaseDir(), storage.getType(), cors.getAllowedOrigins(),
                order.getTimeoutMinutes(), order.isAutoCancelEnabled(), pay.getType());
    }
}
