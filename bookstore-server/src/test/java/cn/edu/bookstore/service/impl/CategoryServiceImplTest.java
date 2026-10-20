package cn.edu.bookstore.service.impl;

import cn.edu.bookstore.entity.Category;
import cn.edu.bookstore.mapper.CategoryMapper;
import cn.edu.bookstore.model.vo.CategoryBookCountVO;
import cn.edu.bookstore.model.vo.CategoryVO;
import cn.edu.bookstore.support.MybatisPlusTestSupport;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CategoryServiceImpl} 单元测试（前台分类列表接口）。
 *
 * <p>测的是「{@code bookCount} 回填是否可信」：这个数字直接搬到页面上给用户看，
 * 一旦在某条分支上返回 {@code null} 或漏掉某个分类，前端渲染出来就是
 * 「小说 0」「共 null 本」这类一眼假的数据，而且不会报错。
 * 因此重点断言<b>缺失分类必须补 0</b>，而不是只断言「有数据的那几个对了」。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CategoryServiceImpl —— 分类书目数回填")
class CategoryServiceImplTest {

    @Mock private CategoryMapper categoryMapper;

    @InjectMocks private CategoryServiceImpl categoryService;

    @BeforeAll
    static void initMybatisPlusMetadata() {
        MybatisPlusTestSupport.initTableInfo(Category.class);
    }

    private Category category(long id, String name, int sort) {
        Category c = new Category();
        c.setId(id);
        c.setName(name);
        c.setSort(sort);
        return c;
    }

    private CategoryBookCountVO stat(long categoryId, long count) {
        CategoryBookCountVO vo = new CategoryBookCountVO();
        vo.setCategoryId(categoryId);
        vo.setBookCount(count);
        return vo;
    }

    @Test
    @DisplayName("聚合结果回填到对应分类，空分类补 0 而不是 null")
    void fillsBookCountAndDefaultsToZero() {
        when(categoryMapper.selectList(any())).thenReturn(List.of(
                category(1L, "小说", 9),
                category(2L, "诗歌", 5)));
        // 诗歌（2）没有任何上架图书，不出现在 GROUP BY 结果里
        when(categoryMapper.selectOnSaleBookCounts()).thenReturn(List.of(stat(1L, 12L)));

        List<CategoryVO> list = categoryService.list();

        assertEquals(2, list.size());
        assertEquals(1L, list.get(0).getId());
        assertEquals(12L, list.get(0).getBookCount());

        CategoryVO empty = list.get(1);
        assertEquals(2L, empty.getId());
        assertNotNull(empty.getBookCount(), "空分类必须补 0，返回 null 会让前端渲染出「共 null 本」");
        assertEquals(0L, empty.getBookCount());
    }

    @Test
    @DisplayName("聚合结果里 categoryId 或 bookCount 为 null 的行必须跳过，不能让整表 500")
    void skipsMalformedStatRows() {
        when(categoryMapper.selectList(any())).thenReturn(List.of(category(1L, "小说", 9)));
        CategoryBookCountVO broken = new CategoryBookCountVO();
        when(categoryMapper.selectOnSaleBookCounts()).thenReturn(List.of(broken, stat(1L, 3L)));

        List<CategoryVO> list = categoryService.list();

        // Collectors.toMap 遇到 null key 会抛 NPE，这里靠 filter 挡掉
        assertEquals(1, list.size());
        assertEquals(3L, list.get(0).getBookCount());
    }

    @Test
    @DisplayName("聚合查询返回 null → 全部按 0 处理，接口不报错")
    void toleratesNullStats() {
        when(categoryMapper.selectList(any())).thenReturn(List.of(category(1L, "小说", 9)));
        when(categoryMapper.selectOnSaleBookCounts()).thenReturn(null);

        List<CategoryVO> list = categoryService.list();

        assertEquals(1, list.size());
        assertEquals(0L, list.get(0).getBookCount());
    }

    @Test
    @DisplayName("分类表为空 → 直接返回空列表，不再发一次聚合查询")
    void emptyCategoriesSkipsStatQuery() {
        when(categoryMapper.selectList(any())).thenReturn(List.of());

        assertEquals(List.of(), categoryService.list());
        verify(categoryMapper, never()).selectOnSaleBookCounts();
    }

    @Test
    @DisplayName("原排序规则不回归：sort 倒序、id 升序")
    void keepsOriginalOrdering() {
        when(categoryMapper.selectList(any())).thenReturn(List.of(category(1L, "小说", 9)));
        when(categoryMapper.selectOnSaleBookCounts()).thenReturn(List.of());

        categoryService.list();

        ArgumentCaptor<Wrapper<Category>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(categoryMapper).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment().replace("`", "").replaceAll("\\s+", " ");
        assertTrue(sql.contains("ORDER BY sort DESC"), "排序规则被改动：" + sql);
        assertTrue(sql.contains("id ASC"), "排序规则被改动：" + sql);
    }
}
