package cn.edu.bookstore.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
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

    /**
     * JWT 密钥最小长度。
     *
     * <p>HS256 的密钥强度取决于其长度，业界建议不低于 256 bit（32 字节），
     * 因此这里要求至少 32 个字符。旧版本只要求 16 位，强度不足，已上调。
     */
    private static final int MIN_JWT_SECRET_LENGTH = 32;

    /**
     * 历史版本中出现过的占位密钥，一律视为「已公开的密钥」，禁止用于启动。
     *
     * <p>它们曾随代码库提交，任何人都能拿到；只要还在用，令牌就能被伪造。
     */
    private static final List<String> PLACEHOLDER_JWT_SECRETS = List.of(
            "bookstore-app-dev-secret-please-change-in-production",
            "please-change-in-production",
            "change-me", "changeme", "changeit", "your-secret-key", "secret", "123456"
    );

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

    /**
     * 运费规则的兜底默认值。
     *
     * <p>运行期真正生效的规则来自 sys_config 表（管理端可改），这里只在
     * 「配置表读不到对应键」时兜底。保留这一层是因为配置表可能被人误清空，
     * 而运费是下单流程的必经环节 —— 兜底能让下单不至于因为一次误操作彻底不可用。
     */
    private Freight freight = new Freight();

    /** 支付相关配置 */
    private Pay pay = new Pay();

    /** 接口文档开关（Knife4j / springdoc 的 /doc.html 入口） */
    private Docs docs = new Docs();

    /** 登录失败锁定（防在线暴力破解） */
    private LoginGuard loginGuard = new LoginGuard();

    /** 可观测性（业务埋点） */
    private Observability observability = new Observability();

    /**
     * 可观测性配置。
     *
     * <p>系统能「被观测」是运维的基本前提：出问题时如果只有日志、没有指标，
     * 就只能靠人肉翻日志猜。这里把关键业务事件（登录成败、账号锁定、令牌吊销、
     * 下单、支付入账）注册成 Micrometer 计数器，通过 {@code /actuator/metrics} 查询。
     *
     * <p>为什么做成开关而不是一直开着：埋点本身极轻（内存里维护计数器），
     * 但保留开关有两个用处 —— 一是可以做「开启/关闭埋点」的耗时对比实验，
     * 二是万一某个计数器名与未来引入的监控系统冲突，能一键关掉而不必改代码。
     */
    @Data
    public static class Observability {

        /**
         * 是否启用业务埋点。
         *
         * <p>true-注册并递增业务计数器（默认）；false-所有埋点方法变为空操作。
         */
        private boolean metricsEnabled = true;
    }

    /**
     * 登录失败锁定配置。
     *
     * <p>为什么需要：图形验证码只能挡住「无脑脚本」，挡不住「人工慢速试密码」。
     * 攻击者拿到一个手机号后，可以每次认真填对验证码、再猜一个密码，
     * 这类攻击在验证码视角下完全正常。真正有效的手段是**限制同一账号的连续失败次数**：
     * 失败 N 次后把该账号临时锁住，把「无限次尝试」压成「每 15 分钟 5 次」，
     * 使穷举在时间维度上不可行。
     *
     * <p>与「账号是否真实存在」无关：不存在的账号同样计数，避免通过响应差异反推账号是否存在。
     */
    @Data
    public static class LoginGuard {

        /**
         * 是否启用登录失败锁定。
         *
         * <p>留了开关是为了应急：万一管理员账号被锁且无法等待解锁，
         * 可临时设 false 重启服务恢复登录（这是唯一「必然可用」的出口）。
         */
        private boolean enabled = true;

        /**
         * 触发锁定所需的连续失败次数。
         *
         * <p>默认 5 次：给正常用户留了足够的「手滑」空间（家人手机的密码框很容易输错两三次），
         * 同时把攻击者的尝试速率压到很低。
         */
        private int maxFailures = 5;

        /**
         * 失败计数的窗口时长（分钟）。
         *
         * <p>计数不是永久的：窗口内失败累计到阈值才锁定；窗口过后计数自动清零。
         * 若不设窗口，正常用户长期偶发输错（几个月错 5 次）也会被误锁。
         */
        private int windowMinutes = 30;

        /** 触发锁定后的锁定时长（分钟）；到期自动解锁，无需人工干预 */
        private int lockMinutes = 15;
    }

    /**
     * 接口文档配置。
     *
     * <p>为什么需要一个显式开关：Knife4j 的 /doc.html 是一个静态页面，
     * 仅靠 {@code knife4j.enable=false} 并不会让它消失 —— 页面照样能打开，
     * 只是取不到接口数据。要真正「在生产环境关掉文档入口」，
     * 必须在安全配置里把相关路径从白名单摘掉并显式拒绝。
     */
    @Data
    public static class Docs {
        /**
         * 是否开放接口文档入口。
         *
         * <p>true-开放（本地开发/答辩演示需要看文档）；false-关闭（生产环境）。
         * application-prod.yml 中已设为 false。
         */
        private boolean enabled = true;
    }

    /** JWT 令牌配置 */
    @Data
    public static class Jwt {
        /**
         * 签名密钥。
         *
         * <p>默认留空 —— 不提供可用默认值。真实密钥必须由
         * application-local.yml（本地开发，不入库）或环境变量 JWT_SECRET（生产）注入。
         * 留空时启动直接失败（见 {@link #validate()}），避免用公开默认密钥签发令牌。
         */
        private String secret = "";

        /**
         * 访问令牌有效期（小时）。
         *
         * <p>取值是一次安全与体验的取舍：太长则令牌一旦泄露，攻击者能用的时间窗口就长
         * （JWT 无法像会话那样随时作废，只能靠吊销黑名单或等它过期）；太短则用户频繁被踢回登录页。
         *
         * <p>本项目从初版的 72 小时收敛为 24 小时（生产 12 小时），
         * 理由是 72 小时已超出「一次移动端会话」的合理范围，
         * 而退出登录现在会在服务端吊销令牌，缩短有效期不会再带来额外的重登困扰。
         */
        private int expireHours = 24;

        /** 令牌吊销配置（退出登录在服务端生效） */
        private Revocation revocation = new Revocation();

        /**
         * 令牌吊销（jti 黑名单）配置。
         *
         * <p>无状态 JWT 的退出登录只在客户端删令牌，服务端仍认这枚令牌直到它过期。
         * 打开本项后，退出登录会把令牌编号写入黑名单，每个请求校验一次，使退出真正生效。
         *
         * <p>留开关的用途：万一黑名单逻辑出现故障导致正常用户被大面积拒绝，
         * 可立即设 {@code JWT_REVOCATION_ENABLED=false} 重启恢复访问（此时回到「登出仅客户端生效」的旧行为）。
         */
        @Data
        public static class Revocation {
            /** 是否启用令牌吊销校验 */
            private boolean enabled = true;
        }
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

        /**
         * 缓存清理是否推迟到「事务提交之后」执行。
         *
         * <p>开启（默认）：带 {@code @Transactional} 的写操作在事务提交成功后才清理缓存，
         * 消除「事务尚未提交就把缓存清空、并发请求趁机把旧值回填」的不一致窗口。
         * 事务回滚时不再做无意义的清理（数据没变，缓存本来就与数据库一致）。
         *
         * <p>关闭：回到「方法返回即清理」的旧行为。保留这个开关有两个用途 ——
         * 一是做「提交前清理 vs 提交后清理」的影响对照实验，二是万一新行为引发问题可一键回退。
         */
        private boolean evictAfterCommit = true;
    }

    /**
     * 高德开放平台 —— Web服务API 配置。
     *
     * <p>安全说明：
     * <ul>
     *     <li>key 可选：缺失时地址自动解析功能不可用（调用时返回友好错误），但服务照常启动，
     *         不因第三方配置缺失就阻断整个系统；</li>
     *     <li>secret 可选；留空时自动降级为「仅 key」模式，此时高德控制台需选择「key 安全模式」；</li>
     *     <li>secret 用于数字签名 sig=MD5(sort(params) + secret)，防止他人盗用 key；</li>
     *     <li>key 和 secret 只在 application-local.yml 或环境变量中配置，绝不硬编码。</li>
     * </ul>
     */
    @Data
    public static class Gaode {
        /** Web服务API key（高德控制台申请）；留空表示未启用地址解析能力 */
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
     * 运费规则的兜底默认值（仅当 sys_config 表中读不到对应键时生效）。
     *
     * <p>三项与配置表中的键一一对应：
     * {@code freight.enabled} / {@code freight.free-threshold} / {@code freight.base-fee}。
     */
    @Data
    public static class Freight {

        /**
         * 是否收取运费。
         *
         * <p>false 表示全场包邮。做成开关而不是「把运费设成 0」，
         * 是为了让「大促期间全场包邮、活动结束恢复」可以一键来回切，
         * 而不必记住原来的运费金额是多少。
         */
        private boolean enabled = true;

        /**
         * 包邮门槛（元）。达到或超过该金额免运费。
         *
         * <p>默认 99 元是国内电商最常见的档位：既能推动凑单，
         * 又不至于让「买一本书」这类小额订单全部免运费。
         */
        private BigDecimal freeThreshold = new BigDecimal("99");

        /** 未达门槛时的运费（元） */
        private BigDecimal baseFee = new BigDecimal("8.00");
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
        validateJwtSecret();
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
        // 运费兜底值：金额为负等于给用户倒贴钱，必须挡住；
        // enabled=true 但没有门槛也没有基础运费，等于「收 0 元运费」，同样是配置写错，
        // 与其静默按包邮处理，不如在启动期就说清楚。
        if (freight.getFreeThreshold() == null || freight.getFreeThreshold().signum() < 0) {
            throw new IllegalStateException(
                    "配置校验失败：bookstore.freight.free-threshold 不能为负数");
        }
        if (freight.getBaseFee() == null || freight.getBaseFee().signum() < 0) {
            throw new IllegalStateException(
                    "配置校验失败：bookstore.freight.base-fee 不能为负数");
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

        // 登录失败锁定：阈值写错会让保护彻底失效（比如 0 次就锁、或负数导致永不触发），
        // 这两类错误在运行时都很难察觉，所以在启动期就拦住。
        if (loginGuard.getMaxFailures() <= 0 || loginGuard.getMaxFailures() > 100) {
            throw new IllegalStateException(
                    "配置校验失败：bookstore.login-guard.max-failures 需在 1~100 之间");
        }
        if (loginGuard.getWindowMinutes() <= 0) {
            throw new IllegalStateException("配置校验失败：bookstore.login-guard.window-minutes 必须大于 0");
        }
        if (loginGuard.getLockMinutes() <= 0) {
            throw new IllegalStateException("配置校验失败：bookstore.login-guard.lock-minutes 必须大于 0");
        }
        if (!loginGuard.isEnabled()) {
            log.warn("安全提示：登录失败锁定已被关闭（bookstore.login-guard.enabled=false），"
                    + "账号可被无限次尝试口令，请确认这是临时排障行为");
        }

        // 令牌有效期：过长会把「令牌泄露」的风险窗口放大到难以接受的程度
        // （JWT 一旦签发，在过期前无法被远程作废，除非命中吊销黑名单）。
        if (jwt.getExpireHours() <= 0 || jwt.getExpireHours() > 720) {
            throw new IllegalStateException(
                    "配置校验失败：bookstore.jwt.expire-hours 需在 1~720 小时之间，当前为 " + jwt.getExpireHours());
        }
        if (!jwt.getRevocation().isEnabled()) {
            log.warn("安全提示：令牌吊销已关闭（bookstore.jwt.revocation.enabled=false），"
                    + "退出登录将只在客户端生效，已登出的令牌在过期前仍然可用");
        }

        // 高德 key 属「可选能力」：没配只是地址自动解析用不了，不该拖垮整个服务。
        boolean gaodeEnabled = gaode.getKey() != null && !gaode.getKey().isBlank();
        if (!gaodeEnabled) {
            log.warn("提示：未配置 bookstore.gaode.key，地址自动解析功能不可用（手动填写地址不受影响）。"
                    + "需要该功能时请在环境变量 GAODE_KEY 或 application-local.yml 中配置。");
        }

        log.info("配置加载完成：jwtExpireHours={}, tokenRevocationEnabled={}, uploadBaseDir={}, storageType={}, "
                        + "allowedOrigins={}, orderTimeoutMinutes={}, autoCancelEnabled={}, payType={}, "
                        + "gaodeEnabled={}, docsEnabled={}, loginGuardEnabled={}, maxFailures={}, lockMinutes={}, "
                        + "metricsEnabled={}, freightEnabled={}, freightFreeThreshold={}, freightBaseFee={}",
                jwt.getExpireHours(), jwt.getRevocation().isEnabled(), upload.getBaseDir(), storage.getType(),
                cors.getAllowedOrigins(), order.getTimeoutMinutes(), order.isAutoCancelEnabled(), pay.getType(),
                gaodeEnabled, docs.isEnabled(), loginGuard.isEnabled(),
                loginGuard.getMaxFailures(), loginGuard.getLockMinutes(),
                observability.isMetricsEnabled(),
                freight.isEnabled(), freight.getFreeThreshold(), freight.getBaseFee());
    }

    /**
     * JWT 密钥校验（快速失败的核心）。
     *
     * <p>密钥是整套鉴权体系的唯一秘密：一旦为空、可猜（历史占位值）或过短，
     * 任何人都能自行签发出合法令牌冒充任意用户。因此这里的策略是「宁可起不来」：
     * <ol>
     *     <li>空值 → 拒绝启动（application.yml 不再提供可用默认值）；</li>
     *     <li>命中历史占位值 → 拒绝启动（该值已随代码库公开，等于没有秘密）；</li>
     *     <li>长度 &lt; 32 → 拒绝启动（HS256 建议密钥长度不小于 256 bit）。</li>
     * </ol>
     */
    private void validateJwtSecret() {
        String secret = jwt.getSecret() == null ? "" : jwt.getSecret().trim();
        if (secret.isEmpty()) {
            throw new IllegalStateException("配置校验失败：bookstore.jwt.secret 未配置。"
                    + "本地开发请在 application-local.yml 中配置；"
                    + "生产环境请通过环境变量 JWT_SECRET 注入长度 >= " + MIN_JWT_SECRET_LENGTH + " 的随机串。");
        }
        String lower = secret.toLowerCase();
        if (PLACEHOLDER_JWT_SECRETS.contains(lower) || lower.contains("please-change-in-production")) {
            throw new IllegalStateException("配置校验失败：bookstore.jwt.secret 仍是历史默认占位值，"
                    + "该值已随代码库公开、不能作为密钥使用；请更换为长度 >= "
                    + MIN_JWT_SECRET_LENGTH + " 的随机串（可用 openssl rand -base64 48 生成）。");
        }
        if (secret.length() < MIN_JWT_SECRET_LENGTH) {
            throw new IllegalStateException("配置校验失败：bookstore.jwt.secret 长度不能少于 "
                    + MIN_JWT_SECRET_LENGTH + " 位（当前 " + secret.length() + " 位）。"
                    + "可用 openssl rand -base64 48 生成一个随机密钥。");
        }
        // 回写去掉首尾空格后的值，避免「配置里带了换行/空格」导致签名行为难以排查
        jwt.setSecret(secret);
    }
}
