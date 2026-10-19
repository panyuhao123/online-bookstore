package cn.edu.bookstore.service.impl;

import cn.edu.bookstore.common.BusinessException;
import cn.edu.bookstore.common.ErrorCode;
import cn.edu.bookstore.entity.SysConfig;
import cn.edu.bookstore.mapper.SysConfigMapper;
import cn.edu.bookstore.support.MybatisPlusTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SysConfigServiceImpl} 单元测试。
 *
 * <p>这个类本身逻辑很浅，但它是「运费规则」的唯一数据来源，因此值得把两条最容易出事的
 * 边界固定下来：
 * <ul>
 *     <li><b>解析失败必须回落默认值</b>，绝不能抛异常。配置值由人手在后台填写，
 *         填成 "abc" 只会让解析失败；若在这里抛异常，一次误填就会让整个下单流程不可用，
 *         而配置页自己还好好的 —— 运营会陷入「后台一切正常、用户就是下不了单」的困境；</li>
 *     <li><b>只允许改已有的键</b>。键名是代码里按名字读取的，凭空新增一行不会被任何逻辑消费，
 *         运营却以为改动已生效。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SysConfigServiceImpl —— 配置读取容错与改值约束")
class SysConfigServiceImplTest {

    @Mock private SysConfigMapper sysConfigMapper;

    @InjectMocks private SysConfigServiceImpl sysConfigService;

    @BeforeAll
    static void initMybatisPlusMetadata() {
        MybatisPlusTestSupport.initTableInfo(SysConfig.class);
    }

    private SysConfig config(String key, String value) {
        SysConfig c = new SysConfig();
        c.setId(1L);
        c.setConfigKey(key);
        c.setConfigValue(value);
        c.setEditable(1);
        return c;
    }

    private void stub(String key, String value) {
        when(sysConfigMapper.selectOne(any())).thenReturn(value == null ? null : config(key, value));
    }

    // ============================================================== 读取

    @Nested
    @DisplayName("读取与容错")
    class Reading {

        @Test
        @DisplayName("list() 按主键升序返回；Mapper 返回 null 时降级为空列表")
        void listReturnsEmptyWhenMapperReturnsNull() {
            when(sysConfigMapper.selectList(any())).thenReturn(null);
            assertEquals(List.of(), sysConfigService.list());
        }

        @Test
        @DisplayName("键不存在 / 值为空串或纯空白 → 一律返回默认值")
        void fallsBackWhenMissingOrBlank() {
            assertEquals("99", sysConfigService.getValue("absent.key", "99"));

            stub("freight.free-threshold", "   ");
            assertEquals("99", sysConfigService.getValue("freight.free-threshold", "99"));
        }

        @Test
        @DisplayName("取值时自动 trim —— 后台误输的空格不该影响解析")
        void trimsValue() {
            stub("freight.free-threshold", "  59  ");
            assertEquals("59", sysConfigService.getValue("freight.free-threshold", "99"));
            assertEquals(59, sysConfigService.getInt("freight.free-threshold", 99));
        }

        @Test
        @DisplayName("布尔值只认 1/true 与 0/false；「off」「null」这类无法识别的写法回落默认值")
        void parsesBooleanStrictly() {
            stub("freight.enabled", "1");
            assertTrue(sysConfigService.getBool("freight.enabled", false));
            stub("freight.enabled", "TRUE");
            assertTrue(sysConfigService.getBool("freight.enabled", false));
            stub("freight.enabled", "0");
            assertFalse(sysConfigService.getBool("freight.enabled", true));
            stub("freight.enabled", "false");
            assertFalse(sysConfigService.getBool("freight.enabled", true));

            // 关键：认不出来的写法必须回落默认值，而不是「非 1 即假」——
            // 否则把 "null"、"off" 误判成「关闭」，运费规则会被静默关掉。
            stub("freight.enabled", "off");
            assertTrue(sysConfigService.getBool("freight.enabled", true));
        }

        @Test
        @DisplayName("整数解析失败 → 回落默认值，不抛异常")
        void fallsBackOnInvalidInt() {
            stub("some.count", "abc");
            assertEquals(7, sysConfigService.getInt("some.count", 7));
        }

        @Test
        @DisplayName("金额解析失败或为负数 → 回落默认值（负运费等于给用户倒贴钱）")
        void fallsBackOnInvalidOrNegativeDecimal() {
            stub("freight.base-fee", "八块");
            assertEquals(0, new BigDecimal("8.00")
                    .compareTo(sysConfigService.getDecimal("freight.base-fee", new BigDecimal("8.00"))));

            stub("freight.base-fee", "-3");
            assertEquals(0, new BigDecimal("8.00")
                    .compareTo(sysConfigService.getDecimal("freight.base-fee", new BigDecimal("8.00"))));
        }

        @Test
        @DisplayName("金额正常解析：保留原值精度，由调用方决定如何取整")
        void parsesDecimal() {
            stub("freight.free-threshold", "59.50");
            assertEquals(0, new BigDecimal("59.50")
                    .compareTo(sysConfigService.getDecimal("freight.free-threshold", BigDecimal.ZERO)));
        }
    }

    // ============================================================== 改值

    @Nested
    @DisplayName("改值约束 update()")
    class Updating {

        @Test
        @DisplayName("键不存在 → 404（拒绝「顺手新建」，否则拼错的键会变成永不生效的幽灵配置）")
        void rejectsUnknownKey() {
            stub("freight.freeThreadhold", null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> sysConfigService.update("freight.freeThreadhold", "59"));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getErrorCode().getCode());
            verify(sysConfigMapper, never()).updateById(any(SysConfig.class));
        }

        @Test
        @DisplayName("只读配置（editable=0）→ 422，且不落库")
        void rejectsReadOnlyKey() {
            SysConfig readOnly = config("jwt.secret", "xxx");
            readOnly.setEditable(0);
            when(sysConfigMapper.selectOne(any())).thenReturn(readOnly);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> sysConfigService.update("jwt.secret", "new-secret"));

            assertEquals(ErrorCode.BIZ_ERROR.getCode(), ex.getErrorCode().getCode());
            assertTrue(ex.getMessage().contains("只读"));
            verify(sysConfigMapper, never()).updateById(any(SysConfig.class));
        }

        @Test
        @DisplayName("正常改值：写回并返回更新后的记录")
        void updatesEditableKey() {
            when(sysConfigMapper.selectOne(any())).thenReturn(config("freight.free-threshold", "99"));

            SysConfig updated = sysConfigService.update("freight.free-threshold", "59");

            ArgumentCaptor<SysConfig> captor = ArgumentCaptor.forClass(SysConfig.class);
            verify(sysConfigMapper).updateById(captor.capture());
            assertEquals("59", captor.getValue().getConfigValue());
            assertEquals("59", updated.getConfigValue());
            // 键名不允许被改动（控制器只传值），否则读配置的代码会瞬间全部读不到
            assertEquals("freight.free-threshold", captor.getValue().getConfigKey());
        }
    }
}
