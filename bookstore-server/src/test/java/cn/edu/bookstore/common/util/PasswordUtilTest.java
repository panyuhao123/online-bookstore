package cn.edu.bookstore.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PasswordUtil} 单元测试：覆盖 BCrypt 生成/校验与历史 MD5 兼容两条路径。
 *
 * <p>历史密文样例直接取自 {@code db/seed.sql}，因此本测试顺带保证
 * 「seed 里的演示账号在升级后依然登录得进去」——即透明迁移不会把老账号锁在门外。
 *
 * @author 毕设项目组
 */
@DisplayName("密码工具：BCrypt + 历史 MD5 兼容")
class PasswordUtilTest {

    /** seed.sql 中演示用户 13800138000 / 123456 的历史密文与盐 */
    private static final String SEED_USER_MD5 = "58ab4bd28431ad3abf469ff87e3658d6";
    private static final String SEED_USER_SALT = "user888";

    /** seed.sql 中管理员 admin / 123456 的历史密文与盐 */
    private static final String SEED_ADMIN_MD5 = "95719c0d6509e437a5182cd4c3e8cb73";
    private static final String SEED_ADMIN_SALT = "admin888";

    @Nested
    @DisplayName("生成密文")
    class Encode {

        @Test
        @DisplayName("产出 BCrypt 形态，且长度可容纳于 VARCHAR(64)")
        void encodeProducesBcrypt() {
            String hash = PasswordUtil.encode("123456");
            assertTrue(PasswordUtil.isBcrypt(hash), "新生成的密文应被识别为 BCrypt：" + hash);
            assertEquals(60, hash.length(), "BCrypt 密文固定 60 字符");
            assertTrue(hash.length() <= 64, "必须仍能放进既有 password VARCHAR(64) 列");
        }

        @Test
        @DisplayName("同一明文两次加密结果不同（盐由 BCrypt 内置随机生成）")
        void encodeIsSalted() {
            assertNotEquals(PasswordUtil.encode("123456"), PasswordUtil.encode("123456"));
        }

        @Test
        @DisplayName("空密码直接拒绝，不产出无人能校验的密文")
        void encodeRejectsBlank() {
            assertThrows(IllegalArgumentException.class, () -> PasswordUtil.encode(null));
            assertThrows(IllegalArgumentException.class, () -> PasswordUtil.encode(""));
        }
    }

    @Nested
    @DisplayName("校验密文")
    class Matches {

        @Test
        @DisplayName("BCrypt 密文：正确密码通过，错误密码不通过")
        void matchesBcrypt() {
            String hash = PasswordUtil.encode("Abc123456");
            assertTrue(PasswordUtil.matches("Abc123456", PasswordUtil.NO_LEGACY_SALT, hash));
            assertFalse(PasswordUtil.matches("Abc123457", PasswordUtil.NO_LEGACY_SALT, hash));
            assertFalse(PasswordUtil.matches("abc123456", PasswordUtil.NO_LEGACY_SALT, hash), "密码区分大小写");
        }

        @Test
        @DisplayName("历史 MD5 密文：仍可用原密码登录（升级不能把老账号锁在门外）")
        void matchesLegacyMd5() {
            assertTrue(PasswordUtil.matches("123456", SEED_USER_SALT, SEED_USER_MD5));
            assertTrue(PasswordUtil.matches("123456", SEED_ADMIN_SALT, SEED_ADMIN_MD5));
            assertFalse(PasswordUtil.matches("1234567", SEED_USER_SALT, SEED_USER_MD5));
        }

        @Test
        @DisplayName("历史 MD5 密文缺盐时不通过，避免退化成裸 MD5 比对")
        void legacyMd5RequiresSalt() {
            assertFalse(PasswordUtil.matches("123456", null, SEED_USER_MD5));
            assertFalse(PasswordUtil.matches("123456", "", SEED_USER_MD5));
        }

        @Test
        @DisplayName("空入参一律拒绝，不抛异常")
        void nullSafe() {
            assertFalse(PasswordUtil.matches(null, "s", SEED_USER_MD5));
            assertFalse(PasswordUtil.matches("123456", "s", null));
            assertFalse(PasswordUtil.matches("123456", "s", ""));
        }

        @Test
        @DisplayName("含中文与特殊字符的密码同样可正确校验")
        void supportsNonAscii() {
            String raw = "书店@2026";
            assertTrue(PasswordUtil.matches(raw, PasswordUtil.NO_LEGACY_SALT, PasswordUtil.encode(raw)));
            assertFalse(PasswordUtil.matches("书店@2027", PasswordUtil.NO_LEGACY_SALT, PasswordUtil.encode(raw)));
        }
    }

    @Nested
    @DisplayName("形态判定与升级判定")
    class Upgrade {

        @Test
        @DisplayName("BCrypt 密文无需升级")
        void bcryptNeedsNoUpgrade() {
            assertTrue(PasswordUtil.isBcrypt(PasswordUtil.encode("123456")));
            assertFalse(PasswordUtil.needsUpgrade(PasswordUtil.encode("123456")));
        }

        @Test
        @DisplayName("历史 MD5 密文需要升级")
        void legacyNeedsUpgrade() {
            assertFalse(PasswordUtil.isBcrypt(SEED_USER_MD5));
            assertTrue(PasswordUtil.needsUpgrade(SEED_USER_MD5));
        }

        @Test
        @DisplayName("空密文不触发升级，避免对空行做无意义写入")
        void blankNeedsNoUpgrade() {
            assertFalse(PasswordUtil.needsUpgrade(null));
            assertFalse(PasswordUtil.needsUpgrade(""));
            assertFalse(PasswordUtil.isBcrypt(null));
        }

        @Test
        @DisplayName("形似 BCrypt 但长度/字符不合规的串不误判为 BCrypt")
        void patternIsStrict() {
            assertFalse(PasswordUtil.isBcrypt("$2a$10$tooshort"));
            assertFalse(PasswordUtil.isBcrypt("$1a$10$" + "a".repeat(53)), "只认 $2a/$2b/$2y");
        }
    }
}
