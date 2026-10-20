package cn.edu.bookstore.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CsvUtil} 单元测试。
 *
 * <p>测的重点不是「拼字符串」这种显然正确的事，而是三个会在真实 Excel 里翻车的点：
 * 逗号裂列、双引号吃掉后半行、以及以 = 开头的收货地址被当公式执行。
 * 这三类问题在浏览器预览里都看不出来 —— 只有下载下来用 Excel 打开才会暴露。
 */
@DisplayName("CsvUtil —— 单元格转义")
class CsvUtilTest {

    @Test
    @DisplayName("普通文本原样输出")
    void plainTextUntouched() {
        assertEquals("张三", CsvUtil.escape("张三"));
        assertEquals("202610200001", CsvUtil.escape("202610200001"));
    }

    @Test
    @DisplayName("null 与空串都输出空单元格")
    void nullBecomesEmpty() {
        assertEquals("", CsvUtil.escape(null));
        assertEquals("", CsvUtil.escape(""));
    }

    @Test
    @DisplayName("含逗号的字段整体加引号，避免裂成两列")
    void commaIsQuoted() {
        assertEquals("\"北京市, 海淀区\"", CsvUtil.escape("北京市, 海淀区"));
    }

    @Test
    @DisplayName("含双引号的字段：外层加引号，内部引号翻倍")
    void quoteIsDoubled() {
        assertEquals("\"他说\"\"好的\"\"\"", CsvUtil.escape("他说\"好的\""));
    }

    @Test
    @DisplayName("含换行的字段加引号（否则一行变两行）")
    void newlineIsQuoted() {
        assertEquals("\"第一行\n第二行\"", CsvUtil.escape("第一行\n第二行"));
    }

    @Test
    @DisplayName("以 = + - @ 开头的字段前置单引号，防止被 Excel 当公式执行")
    void formulaInjectionIsNeutralised() {
        assertEquals("'=1+1", CsvUtil.escape("=1+1"));
        assertEquals("'+86", CsvUtil.escape("+86"));
        assertEquals("'-12", CsvUtil.escape("-12"));
        assertEquals("'@SUM(A1)", CsvUtil.escape("@SUM(A1)"));
    }

    @Test
    @DisplayName("BOM 存在且为单个不可见字符")
    void bomPresent() {
        assertEquals(1, CsvUtil.BOM.length());
        assertEquals('\uFEFF', CsvUtil.BOM.charAt(0));
    }

    @Test
    @DisplayName("整行拼接：逗号分隔 + CRLF 结尾，且末尾不多出空列")
    void lineJoinsWithCrlf() {
        String line = CsvUtil.line("A", 1, null, "含,逗号");
        assertEquals("A,1,,\"含,逗号\"\r\n", line);
        assertTrue(line.endsWith("\r\n"));
    }
}
