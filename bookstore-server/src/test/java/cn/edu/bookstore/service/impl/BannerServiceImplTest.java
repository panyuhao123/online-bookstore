package cn.edu.bookstore.service.impl;

import cn.edu.bookstore.entity.Banner;
import cn.edu.bookstore.mapper.BannerMapper;
import cn.edu.bookstore.model.vo.BannerVO;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link BannerServiceImpl} 单元测试（前台读接口）。
 *
 * <p>前台唯一不能出错的地方是<b>「已下架的绝不能露出来」</b>：运营下架一张
 * 有问题的图片，靠的就是这个过滤条件。因此这里直接断言 SQL 片段，
 * 而不是只断言「返回了几条」—— 后者在 mock 场景下永远成立，等于没测。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BannerServiceImpl —— 前台只暴露已上架轮播")
class BannerServiceImplTest {

    @Mock private BannerMapper bannerMapper;

    @InjectMocks private BannerServiceImpl bannerService;

    @BeforeAll
    static void initMybatisPlusMetadata() {
        MybatisPlusTestSupport.initTableInfo(Banner.class);
    }

    private Banner banner(long id, String title, int sort, int status) {
        Banner b = new Banner();
        b.setId(id);
        b.setTitle(title);
        b.setSort(sort);
        b.setStatus(status);
        return b;
    }

    @Test
    @DisplayName("过滤条件必须带 status=1，并按排序值升序")
    void filtersOnlineAndOrdersBySort() {
        when(bannerMapper.selectList(any())).thenReturn(List.of(banner(1L, "新学期", 0, 1)));

        List<BannerVO> list = bannerService.visibleList();

        ArgumentCaptor<Wrapper<Banner>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(bannerMapper).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment().replace("`", "").replaceAll("\\s+", " ");
        assertTrue(sql.contains("status"), "未按上架状态过滤：" + sql);
        assertTrue(sql.contains("sort"), "未按排序值排序：" + sql);
        assertEquals(1, list.size());
        assertEquals("新学期", list.get(0).getTitle());
    }

    @Test
    @DisplayName("Mapper 返回 null → 降级为空列表（首页无轮播时前端自行隐藏该区块）")
    void degradesToEmptyList() {
        when(bannerMapper.selectList(any())).thenReturn(null);
        assertEquals(List.of(), bannerService.visibleList());
    }
}
