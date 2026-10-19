package cn.edu.bookstore.service.impl;

import cn.edu.bookstore.common.BusinessException;
import cn.edu.bookstore.common.ErrorCode;
import cn.edu.bookstore.entity.Banner;
import cn.edu.bookstore.mapper.BannerMapper;
import cn.edu.bookstore.model.dto.BannerRequest;
import cn.edu.bookstore.model.vo.BannerVO;
import cn.edu.bookstore.support.MybatisPlusTestSupport;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AdminBannerServiceImpl} 单元测试。
 *
 * <p>轮播图管理看着是标准 CRUD，但有三处必须钉住：
 * <ul>
 *     <li><b>数量上限</b>：首页轮播超过 8 张没人翻得完，也拖慢首屏 —— 上限要在服务端拦，
 *         不能只靠前端按钮置灰；</li>
 *     <li><b>空字符串统一存 null</b>：否则「清空图片地址」会存下 ""，
 *         库里出现两种「空」，前台回退渲染渐变的判断就会出现分叉；</li>
 *     <li><b>状态只认 0/1</b>：其它值一律 400，而不是「非 0 即上架」地猜。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdminBannerServiceImpl —— 上限、状态校验与空值归一")
class AdminBannerServiceImplTest {

    @Mock private BannerMapper bannerMapper;

    @InjectMocks private AdminBannerServiceImpl adminBannerService;

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

    private BannerRequest request(String title) {
        BannerRequest r = new BannerRequest();
        r.setTitle(title);
        r.setStatus(1);
        return r;
    }

    // ============================================================== 查询

    @Nested
    @DisplayName("列表 list()")
    class ListQuery {

        @Test
        @DisplayName("Mapper 返回 null → 降级为空列表")
        void emptyWhenMapperReturnsNull() {
            when(bannerMapper.selectList(any())).thenReturn(null);
            assertEquals(List.of(), adminBannerService.list());
        }

        @Test
        @DisplayName("按排序值升序返回，且包含已下架的（后台要能看到并重新上架）")
        void ordersBySortAndIncludesOffline() {
            when(bannerMapper.selectList(any())).thenReturn(List.of(
                    banner(2L, "第二张", 1, 0),
                    banner(1L, "第一张", 0, 1)));

            List<BannerVO> list = adminBannerService.list();

            ArgumentCaptor<Wrapper<Banner>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(bannerMapper).selectList(captor.capture());
            String sql = captor.getValue().getSqlSegment().replace("`", "");
            assertTrue(sql.contains("sort"), "未按排序值排序：" + sql);
            // 后台列表不能过滤 status，否则下架后就再也找不回来
            assertTrue(!sql.contains("status"), "后台列表不应过滤状态：" + sql);
            assertEquals(2, list.size());
        }
    }

    // ============================================================== 新增

    @Nested
    @DisplayName("新增 create()")
    class Create {

        @Test
        @DisplayName("达到上限 → 422，且不落库（服务端拦，不能只靠前端置灰按钮）")
        void rejectsWhenFull() {
            when(bannerMapper.selectCount(null)).thenReturn(8L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> adminBannerService.create(request("第九张")));

            assertEquals(ErrorCode.BIZ_ERROR.getCode(), ex.getErrorCode().getCode());
            assertTrue(ex.getMessage().contains("8"));
            verify(bannerMapper, never()).insert(any(Banner.class));
        }

        @Test
        @DisplayName("未给排序值 → 排到末尾（给 0 会莫名插到第一张前面）")
        void defaultsSortToTail() {
            when(bannerMapper.selectCount(null)).thenReturn(3L);
            when(bannerMapper.selectList(any())).thenReturn(List.of(
                    banner(1L, "A", 0, 1), banner(2L, "B", 5, 1)));

            adminBannerService.create(request("新轮播"));

            ArgumentCaptor<Banner> captor = ArgumentCaptor.forClass(Banner.class);
            verify(bannerMapper).insert(captor.capture());
            assertEquals(6, captor.getValue().getSort());
        }

        @Test
        @DisplayName("显式给了排序值则尊重它；status 只认 0 作为下架，其余一律上架")
        void respectsExplicitSortAndStatus() {
            when(bannerMapper.selectCount(null)).thenReturn(0L);

            BannerRequest req = request("指定排序");
            req.setSort(1);
            req.setStatus(0);
            adminBannerService.create(req);

            ArgumentCaptor<Banner> captor = ArgumentCaptor.forClass(Banner.class);
            verify(bannerMapper).insert(captor.capture());
            assertEquals(1, captor.getValue().getSort());
            assertEquals(0, captor.getValue().getStatus());

            BannerRequest unspecified = request("未指定状态");
            unspecified.setStatus(null);
            adminBannerService.create(unspecified);

            // 换一个全新的 captor：同一个 captor 被两次 verify 复用会重复累积捕获值，
            // 想取「第二次插入」反而会拿到第一次的（这里第一张是下架、第二张是上架，最容易看错）
            ArgumentCaptor<Banner> second = ArgumentCaptor.forClass(Banner.class);
            verify(bannerMapper, org.mockito.Mockito.times(2)).insert(second.capture());
            assertEquals(1, second.getAllValues().get(1).getStatus());
        }

        @Test
        @DisplayName("标题去空格；空的图片地址/背景色/跳转链接统一存 null（避免库里出现两种「空」）")
        void normalizesBlankFields() {
            when(bannerMapper.selectCount(null)).thenReturn(0L);

            BannerRequest req = request("  新学期书单  ");
            req.setImageUrl("");
            req.setBgColor("   ");
            req.setLinkUrl(null);
            adminBannerService.create(req);

            ArgumentCaptor<Banner> captor = ArgumentCaptor.forClass(Banner.class);
            verify(bannerMapper).insert(captor.capture());
            assertEquals("新学期书单", captor.getValue().getTitle());
            assertNull(captor.getValue().getImageUrl());
            assertNull(captor.getValue().getBgColor());
            assertNull(captor.getValue().getLinkUrl());
        }
    }

    // ============================================================== 编辑

    @Nested
    @DisplayName("编辑与上下架")
    class Update {

        @Test
        @DisplayName("编辑不存在的轮播图 → 404")
        void updateNotFound() {
            when(bannerMapper.selectById(9L)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> adminBannerService.update(9L, request("改标题")));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getErrorCode().getCode());
        }

        @Test
        @DisplayName("编辑时未传 sort/status → 保留原值，不被重置")
        void updateKeepsSortAndStatusWhenAbsent() {
            when(bannerMapper.selectById(1L)).thenReturn(banner(1L, "旧标题", 3, 0));

            BannerRequest req = new BannerRequest();
            req.setTitle("新标题");
            adminBannerService.update(1L, req);

            ArgumentCaptor<Banner> captor = ArgumentCaptor.forClass(Banner.class);
            verify(bannerMapper).updateById(captor.capture());
            assertEquals("新标题", captor.getValue().getTitle());
            assertEquals(3, captor.getValue().getSort());
            assertEquals(0, captor.getValue().getStatus());
        }

        @Test
        @DisplayName("上下架：状态非 0/1 → 400（不猜「非 0 即上架」）")
        void rejectsInvalidStatus() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> adminBannerService.updateStatus(1L, 7));

            assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getErrorCode().getCode());
            verify(bannerMapper, never()).updateById(any(Banner.class));
        }

        @Test
        @DisplayName("上下架：轮播图不存在 → 404")
        void statusNotFound() {
            when(bannerMapper.selectById(9L)).thenReturn(null);

            assertThrows(BusinessException.class, () -> adminBannerService.updateStatus(9L, 0));
        }

        @Test
        @DisplayName("上下架只改 status 一个字段，不回传整条记录")
        void updateStatusTouchesOnlyStatus() {
            when(bannerMapper.selectById(1L)).thenReturn(banner(1L, "活动图", 2, 1));

            BannerVO vo = adminBannerService.updateStatus(1L, 0);

            ArgumentCaptor<Banner> captor = ArgumentCaptor.forClass(Banner.class);
            verify(bannerMapper).updateById(captor.capture());
            assertEquals(0, captor.getValue().getStatus());
            assertEquals("活动图", captor.getValue().getTitle());
            assertEquals(0, vo.getStatus());
        }
    }

    // ============================================================== 删除

    @Nested
    @DisplayName("删除 remove()")
    class Remove {

        @Test
        @DisplayName("删除不存在的轮播图 → 404（不静默成功，否则会让人以为删除生效了）")
        void notFound() {
            when(bannerMapper.selectById(9L)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> adminBannerService.remove(9L));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getErrorCode().getCode());
            verify(bannerMapper, never()).deleteById(any(Long.class));
        }

        @Test
        @DisplayName("正常删除：物理删除（轮播图没有审计价值，删了就该消失）")
        void deletes() {
            when(bannerMapper.selectById(1L)).thenReturn(banner(1L, "过期活动", 0, 0));

            adminBannerService.remove(1L);

            verify(bannerMapper).deleteById(1L);
        }

        @Test
        @DisplayName("ID 为空 → 400，不查库")
        void rejectsNullId() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> adminBannerService.remove(null));

            assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getErrorCode().getCode());
        }
    }
}
