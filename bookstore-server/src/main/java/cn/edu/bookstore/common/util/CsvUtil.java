package cn.edu.bookstore.common.util;

/**
 * CSV 文本拼装工具。
 *
 * <p>不引第三方库（如 OpenCSV）：本项目的导出只有「一种表头 + 若干行文本」，
 * 引一个库来拼接字符串反而是负担。真正需要小心的是下面三件事：
 *
 * <ol>
 *   <li><b>转义</b>：字段里出现逗号、双引号、换行时必须整体加引号，内部引号成对翻倍，
 *       否则一行会在 Excel 里裂成两列；</li>
 *   <li><b>CSV 注入</b>：以 {@code = + - @} 开头的单元格会被 Excel 当公式执行，
 *       收货地址这类用户可控字段必须前置单引号降级为文本；</li>
 *   <li><b>BOM</b>：不带 UTF-8 BOM 的中文 CSV 用 Excel 直接打开是乱码，
 *       而演示场景里「双击打开」就是最常见的用法。</li>
 * </ol>
 *
 * @author 毕设项目组
 */
public final class CsvUtil {

    /** UTF-8 BOM：让 Excel 认出编码，避免中文乱码 */
    public static final String BOM = "\uFEFF";

    /** 会被 Excel 解释为公式的起始字符 */
    private static final String FORMULA_PREFIX = "=+-@";

    private CsvUtil() {
    }

    /**
     * 转义单个单元格。
     *
     * @param value 原始值，null 视为空串
     */
    public static String escape(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        if (text.isEmpty()) {
            return "";
        }
        boolean needQuote = text.indexOf(',') >= 0
                || text.indexOf('"') >= 0
                || text.indexOf('\n') >= 0
                || text.indexOf('\r') >= 0;
        // 公式注入：只对以危险字符开头的单元格加单引号前缀（Excel 会把它当纯文本标记）
        if (FORMULA_PREFIX.indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (!needQuote) {
            return text;
        }
        return '"' + text.replace("\"", "\"\"") + '"';
    }

    /** 拼一行：单元格用逗号分隔，行尾换行（CRLF 兼容 Excel 与 WPS） */
    public static String line(Object... cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(cells[i]));
        }
        return sb.append("\r\n").toString();
    }
}
