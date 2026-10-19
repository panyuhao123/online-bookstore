package cn.edu.bookstore.common.storage;

import cn.hutool.core.util.IdUtil;

/**
 * 文件名工具：统一生成「时间戳 + 随机串」形式的文件名。
 *
 * <p>为什么不用原始文件名：
 * <ul>
 *     <li>中文/空格/特殊字符在 URL 与对象存储里都要转义，容易出问题；</li>
 *     <li>用户上传的原始文件名可能包含 ../ 等路径穿越片段，直接拼接有安全风险。</li>
 * </ul>
 *
 * @author 毕设项目组
 */
public final class FileNameUtil {

    private FileNameUtil() {
    }

    /** 生成不重复的文件名，如 1792131584915-3f9a2b1c.jpg */
    public static String generateName(String ext) {
        return System.currentTimeMillis() + "-" + IdUtil.fastSimpleUUID().substring(0, 8) + "." + ext;
    }

    /** 取小写扩展名，无扩展名时兜底为 jpg */
    public static String extractExt(String originalFilename) {
        if (originalFilename == null) {
            return "jpg";
        }
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) {
            return "jpg";
        }
        return originalFilename.substring(dot + 1).toLowerCase();
    }
}
