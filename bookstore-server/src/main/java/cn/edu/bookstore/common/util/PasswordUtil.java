package cn.edu.bookstore.common.util;

import cn.hutool.crypto.digest.DigestUtil;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.regex.Pattern;

/**
 * 密码工具类：BCrypt 自适应慢哈希 + 历史 MD5(明文 + 盐) 兼容。
 *
 * <p>演进说明：早期版本按需求文档实现为 MD5(明文 + 每用户随机盐)，能防彩虹表，
 * 但 MD5 本身是「快哈希」——单卡每秒可计算数十亿次，加盐无法抵消这一根本缺陷。
 * 现改为 BCrypt（自带盐、可调迭代轮数、计算代价高），同时保留对存量 MD5 密文的校验能力，
 * 老账号在下次登录成功后自动重写为 BCrypt，对用户完全透明、无需强制改密。
 *
 * <p>安全约定：
 * <ul>
 *     <li>密文形态判定只看格式前缀，不依赖任何数据库标记字段；</li>
 *     <li>明文密码与盐值绝不写日志、绝不返回前端；</li>
 *     <li>BCrypt 密文固定 60 字符，可容纳在既有 {@code password VARCHAR(64)} 列中，无需改表。</li>
 * </ul>
 *
 * @author 毕设项目组
 */
public final class PasswordUtil {

    /**
     * BCrypt 强度（代价因子，迭代 2^n 轮）。
     * 10 为业界常规取值，单次运算约数十毫秒——既让离线爆破代价足够高，又不至于拖慢登录。
     */
    public static final int BCRYPT_STRENGTH = 10;

    /** 库中 BCrypt 密文的形态：$2a$10$ 前缀 + 22 位盐 + 31 位摘要（base64 字母表） */
    private static final Pattern BCRYPT_PATTERN =
            Pattern.compile("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

    /**
     * BCrypt 方案的盐值占位符。
     *
     * <p>BCrypt 把盐编码在密文自身里，不需要额外的 salt 列；
     * 但既有表结构中 {@code salt} 为 {@code NOT NULL}，为不做破坏性改表，
     * 新数据统一写入空串占位。
     */
    public static final String NO_LEGACY_SALT = "";

    /** BCrypt 编码器：无状态且线程安全，全局复用一个实例即可 */
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(BCRYPT_STRENGTH);

    private PasswordUtil() {
    }

    /**
     * 生成密文。新数据一律使用 BCrypt。
     *
     * @param rawPassword 明文密码
     * @return BCrypt 密文（60 字符）
     */
    public static String encode(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        return ENCODER.encode(rawPassword);
    }

    /** 判断库中密文是否已是 BCrypt 形态 */
    public static boolean isBcrypt(String storedPassword) {
        return storedPassword != null && BCRYPT_PATTERN.matcher(storedPassword).matches();
    }

    /**
     * 判断密文是否需要升级为 BCrypt。
     *
     * <p>返回 true 表示这是一条历史 MD5 数据，调用方应在登录成功后重写该行。
     */
    public static boolean needsUpgrade(String storedPassword) {
        return storedPassword != null && !storedPassword.isEmpty() && !isBcrypt(storedPassword);
    }

    /**
     * 校验明文密码是否与库中密文一致。
     *
     * <p>自动识别存储形态：BCrypt 密文走 BCrypt 校验；历史 MD5 密文回退到
     * {@code MD5(明文 + salt)}，使哈希升级对存量账号透明。
     *
     * @param rawPassword     用户提交的明文
     * @param salt            历史盐值（BCrypt 数据下为空串，不会被使用）
     * @param storedPassword  库中密文
     */
    public static boolean matches(String rawPassword, String salt, String storedPassword) {
        if (rawPassword == null || storedPassword == null) {
            return false;
        }
        if (isBcrypt(storedPassword)) {
            return ENCODER.matches(rawPassword, storedPassword);
        }
        return legacyMd5Matches(rawPassword, salt, storedPassword);
    }

    /**
     * 历史算法：{@code MD5(明文 + 8 位随机盐)}。
     *
     * <p>仅用于兼容存量数据，新注册用户不再生成这种密文。
     * 待存量账号全部完成透明升级后可整体删除本方法。
     */
    private static boolean legacyMd5Matches(String rawPassword, String salt, String storedPassword) {
        if (salt == null || salt.isEmpty()) {
            return false;
        }
        return DigestUtil.md5Hex(rawPassword + salt).equals(storedPassword);
    }
}
