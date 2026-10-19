package cn.edu.bookstore.common.util;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;

/**
 * 密码工具类：MD5 + 随机盐。
 *
 * <p>需求规格说明书 4.4 要求「用户密码数据库加密存储（MD5 加密）」。
 * 直接裸 MD5 会被彩虹表反查，因此实现为 MD5(明文 + 每用户随机盐)，
 * 既满足文档的 MD5 要求，也避免相同明文得到相同密文。
 *
 * <p>严禁把明文密码与盐写入日志。
 *
 * @author 毕设项目组
 */
public final class PasswordUtil {

    /** 盐值长度 */
    private static final int SALT_LENGTH = 8;

    private PasswordUtil() {
    }

    /** 生成随机盐 */
    public static String generateSalt() {
        return IdUtil.fastSimpleUUID().substring(0, SALT_LENGTH);
    }

    /** 加密：MD5(明文 + 盐) */
    public static String encrypt(String rawPassword, String salt) {
        return DigestUtil.md5Hex(rawPassword + salt);
    }

    /** 校验明文密码是否与库中密文一致 */
    public static boolean matches(String rawPassword, String salt, String encryptedPassword) {
        if (rawPassword == null || salt == null || encryptedPassword == null) {
            return false;
        }
        return encrypt(rawPassword, salt).equals(encryptedPassword);
    }
}
