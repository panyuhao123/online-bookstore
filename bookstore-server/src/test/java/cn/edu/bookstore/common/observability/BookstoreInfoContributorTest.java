package cn.edu.bookstore.common.observability;

import cn.edu.bookstore.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 运行信息贡献器测试。
 *
 * <p>这个端点是匿名可访问的，因此测试要同时守住两条线：
 * <ol>
 *     <li><b>该有的要有</b>：关键开关必须在，否则「一眼看清系统形态」的价值不成立；</li>
 *     <li><b>不该有的绝不能有</b>：密钥、数据库口令、对象存储凭证等任何秘密都不得出现。</li>
 * </ol>
 * 第二条是本测试里最重要的一条断言 —— 端点一旦被匿名访问，泄露就是不可逆的。
 *
 * @author 毕设项目组
 */
@DisplayName("运行信息（/actuator/info）")
class BookstoreInfoContributorTest {

    private AppProperties appProperties;
    private MockEnvironment environment;
    private BookstoreInfoContributor contributor;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        // 配置里放上「绝不能出现在 info 里」的秘密，用于反向断言
        appProperties.getJwt().setSecret("super-secret-key-should-never-appear-0123456789");
        appProperties.getStorage().getMinio().setSecretKey("minio-secret-should-never-appear");
        appProperties.getPay().setType("mock");
        appProperties.getStorage().setType("local");
        appProperties.getJwt().setExpireHours(24);

        environment = new MockEnvironment();
        contributor = new BookstoreInfoContributor(appProperties, environment);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> details() {
        Info.Builder builder = new Info.Builder();
        contributor.contribute(builder);
        Info info = builder.build();
        Object bookstore = info.getDetails().get("bookstore");
        assertNotNull(bookstore, "info 中必须包含 bookstore 段");
        return (Map<String, Object>) bookstore;
    }

    @Test
    @DisplayName("输出关键开关的运行时真实值")
    void exposesRuntimeSwitches() {
        Map<String, Object> detail = details();

        assertEquals("mock", detail.get("payType"));
        assertEquals("local", detail.get("storageType"));
        assertEquals(24, detail.get("jwtExpireHours"));
        assertEquals(true, detail.get("tokenRevocationEnabled"));
        assertEquals(true, detail.get("loginGuardEnabled"));
        assertEquals(5, detail.get("loginGuardMaxFailures"));
        assertEquals(true, detail.get("cacheEnabled"));
        assertEquals(true, detail.get("docsEnabled"));
        assertEquals(true, detail.get("metricsEnabled"));
    }

    @Test
    @DisplayName("未指定 profile 时显示 default（而不是空数组）")
    void fallsBackToDefaultProfile() {
        assertEquals(List.of("default"), details().get("profiles"));
    }

    @Test
    @DisplayName("指定了 profile 时如实显示（便于确认「这个实例到底加载了哪份配置」）")
    void showsActiveProfiles() {
        environment.setActiveProfiles("prod");

        assertEquals(List.of("prod"), details().get("profiles"));
    }

    @Test
    @DisplayName("安全红线：绝不输出任何密钥或凭证")
    void neverLeaksSecrets() {
        String rendered = details().toString();

        assertFalse(rendered.contains("super-secret-key-should-never-appear"), "JWT 密钥泄露");
        assertFalse(rendered.contains("minio-secret-should-never-appear"), "对象存储密钥泄露");
        // 连字段名都不该出现，避免给攻击者提供「去哪里找」的线索
        assertFalse(rendered.contains("secret"), "不应出现 secret 相关字段");
        assertFalse(rendered.contains("password"), "不应出现 password 相关字段");
        assertFalse(rendered.contains("accessKey"), "不应出现 accessKey 字段");
    }

    @Test
    @DisplayName("关闭开关后 info 如实反映（改配置即生效，无需改代码）")
    void reflectsDisabledSwitches() {
        appProperties.getJwt().getRevocation().setEnabled(false);
        appProperties.getLoginGuard().setEnabled(false);
        appProperties.getObservability().setMetricsEnabled(false);

        Map<String, Object> detail = details();

        assertEquals(false, detail.get("tokenRevocationEnabled"));
        assertEquals(false, detail.get("loginGuardEnabled"));
        assertEquals(false, detail.get("metricsEnabled"));
        assertTrue(detail.keySet().containsAll(
                List.of("profiles", "payType", "storageType", "jwtExpireHours", "docsEnabled")));
    }
}
