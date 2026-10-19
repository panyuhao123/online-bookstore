package cn.edu.bookstore.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配置启动自检单元测试（{@link AppProperties#validate()}）。
 *
 * <p>为什么这几条断言值得单独写测试：
 * JWT 密钥是整套鉴权体系的唯一秘密 —— 只要它为空、可猜（历史占位值）或过短，
 * 任何人都能自行签发合法令牌冒充任意用户，而系统表面上看不出任何异常。
 * 这类「配错了但能跑起来」的问题最危险，必须在启动时用快速失败挡住，
 * 并把「什么算合法密钥」固化进测试，避免后续有人为了图方便又把默认值加回来。
 *
 * <p>本测试是纯单元测试：直接 new 出配置对象，不启动 Spring 容器，毫秒级完成。
 */
class AppPropertiesTest {

    /** 一个合法的密钥样例：46 位随机串，满足 >= 32 的要求 */
    private static final String VALID_SECRET = "local-dev-only-bookstore-jwt-secret-8f3a2c19d7b64e05";

    private static AppProperties props(String secret) {
        AppProperties p = new AppProperties();
        p.getJwt().setSecret(secret);
        return p;
    }

    @Test
    @DisplayName("密钥为空 → 拒绝启动，并提示配置位置")
    void emptySecretShouldFailFast() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> props("").validate());
        assertTrue(ex.getMessage().contains("未配置"), "错误信息应说明未配置，实际：" + ex.getMessage());
        assertTrue(ex.getMessage().contains("JWT_SECRET"), "错误信息应指明环境变量名");
    }

    @Test
    @DisplayName("密钥为 null → 拒绝启动（不能抛 NPE）")
    void nullSecretShouldFailFast() {
        assertThrows(IllegalStateException.class, () -> props(null).validate());
    }

    @Test
    @DisplayName("密钥仍是历史默认占位值 → 拒绝启动（该值已随代码库公开）")
    void placeholderSecretShouldFailFast() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> props("bookstore-app-dev-secret-please-change-in-production").validate());
        assertTrue(ex.getMessage().contains("占位值"), "实际：" + ex.getMessage());
    }

    @Test
    @DisplayName("密钥长度不足 32 位 → 拒绝启动")
    void tooShortSecretShouldFailFast() {
        // 恰好 31 位，边界值
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> props("a".repeat(31)).validate());
        assertTrue(ex.getMessage().contains("长度不能少于"), "实际：" + ex.getMessage());
        assertTrue(ex.getMessage().contains("31"), "错误信息应带上当前长度，便于排查");
    }

    @Test
    @DisplayName("密钥恰好 32 位 → 通过（边界值放行）")
    void exactlyMinLengthSecretShouldPass() {
        assertDoesNotThrow(() -> props("a".repeat(32)).validate());
    }

    @Test
    @DisplayName("密钥首尾带空白 → 通过校验，且被自动 trim 回写")
    void secretShouldBeTrimmed() {
        AppProperties p = props("  " + VALID_SECRET + "\n");
        assertDoesNotThrow(p::validate);
        assertEquals(VALID_SECRET, p.getJwt().getSecret(), "校验后应回写去掉首尾空白后的密钥");
    }

    @Test
    @DisplayName("未配置高德 key → 不阻断启动（可选能力）")
    void missingGaodeKeyShouldNotBlockStartup() {
        AppProperties p = props(VALID_SECRET);
        p.getGaode().setKey("");
        assertDoesNotThrow(p::validate);
    }

    @Test
    @DisplayName("默认配置 + 合法密钥 → 整体自检通过")
    void defaultConfigWithValidSecretShouldPass() {
        assertDoesNotThrow(() -> props(VALID_SECRET).validate());
    }

    @Test
    @DisplayName("令牌有效期默认收敛为 24 小时，且吊销功能默认开启")
    void defaultTokenPolicyIsTightened() {
        AppProperties p = props(VALID_SECRET);

        assertEquals(24, p.getJwt().getExpireHours(),
                "初版为 72 小时，已收敛为 24；这条断言防止有人为了「少登几次」悄悄改回去");
        assertTrue(p.getJwt().getRevocation().isEnabled(),
                "令牌吊销应默认开启，否则退出登录只在客户端生效");
    }

    @Test
    @DisplayName("令牌有效期非法（<=0 或 >720）→ 拒绝启动")
    void invalidExpireHoursShouldFailFast() {
        AppProperties zero = props(VALID_SECRET);
        zero.getJwt().setExpireHours(0);
        IllegalStateException ex = assertThrows(IllegalStateException.class, zero::validate);
        assertTrue(ex.getMessage().contains("expire-hours"), "实际：" + ex.getMessage());

        AppProperties tooLong = props(VALID_SECRET);
        tooLong.getJwt().setExpireHours(721);
        assertThrows(IllegalStateException.class, tooLong::validate,
                "有效期超过 720 小时（30 天）已接近「永久令牌」，应直接拒绝");
    }

    @Test
    @DisplayName("令牌有效期边界值 1 与 720 → 通过")
    void expireHoursBoundaryPasses() {
        AppProperties one = props(VALID_SECRET);
        one.getJwt().setExpireHours(1);
        assertDoesNotThrow(one::validate);

        AppProperties max = props(VALID_SECRET);
        max.getJwt().setExpireHours(720);
        assertDoesNotThrow(max::validate);
    }
}
