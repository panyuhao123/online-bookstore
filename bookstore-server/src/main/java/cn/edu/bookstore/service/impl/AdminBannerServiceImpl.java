package cn.edu.bookstore.service.impl;

import cn.edu.bookstore.common.BusinessException;
import cn.edu.bookstore.common.ErrorCode;
import cn.edu.bookstore.entity.Banner;
import cn.edu.bookstore.mapper.BannerMapper;
import cn.edu.bookstore.model.dto.BannerRequest;
import cn.edu.bookstore.model.vo.BannerVO;
import cn.edu.bookstore.service.AdminBannerService;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 首页轮播图管理实现（管理端）。
 *
 * <p>每个写方法都带 {@code @CacheEvict(value = "banner", allEntries = true)}：
 * 该项目采用「按缓存名整体清空」而不是「按 key 精确清空」的策略 ——
 * 前台只有一个缓存键，整体清空的代价为零，却免去了「新增/删除时不知道该清哪个键」
 * 的推断工作（新增时甚至没有旧键可清）。
 *
 * <p>由于外层包了「事务感知缓存管理器」，清理动作会推迟到事务提交之后执行，
 * 因此不会出现「事务还没提交就清空缓存、并发请求把旧值回填」的窗口。
 *
 * @author 毕设项目组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminBannerServiceImpl implements AdminBannerService {

    /** 轮播图数量上限：首页轮播超过 8 张就没人会翻完，也拖慢首屏 */
    private static final int MAX_BANNERS = 8;

    private final BannerMapper bannerMapper;

    @Override
    public List<BannerVO> list() {
        List<Banner> list = bannerMapper.selectList(new LambdaQueryWrapper<Banner>()
                .orderByAsc(Banner::getSort)
                .orderByAsc(Banner::getId));
        return list == null ? List.of() : list.stream().map(BannerVO::from).toList();
    }

    @Override
    @CacheEvict(value = "banner", allEntries = true)
    public BannerVO create(BannerRequest request) {
        long total = bannerMapper.selectCount(null);
        if (total >= MAX_BANNERS) {
            throw new BusinessException(ErrorCode.BIZ_ERROR,
                    "最多配置 " + MAX_BANNERS + " 张轮播图，请先删除不再使用的");
        }
        Banner banner = new Banner();
        applyRequest(banner, request);
        // 未给排序值时才自动排到末尾；运营显式指定了就必须尊重 ——
        // 否则「我想把它放第一位」这种最常见的诉求会静默失效（页面顺序不变、后台也不报错）。
        banner.setSort(request.getSort() == null ? nextSort() : request.getSort());
        banner.setStatus(Integer.valueOf(0).equals(request.getStatus()) ? 0 : 1);
        bannerMapper.insert(banner);
        log.info("新增轮播图 id={} title={}", banner.getId(), banner.getTitle());
        return BannerVO.from(banner);
    }

    @Override
    @CacheEvict(value = "banner", allEntries = true)
    public BannerVO update(Long id, BannerRequest request) {
        Banner banner = requireBanner(id);
        applyRequest(banner, request);
        if (request.getSort() != null) {
            banner.setSort(request.getSort());
        }
        if (request.getStatus() != null) {
            banner.setStatus(Integer.valueOf(0).equals(request.getStatus()) ? 0 : 1);
        }
        bannerMapper.updateById(banner);
        log.info("编辑轮播图 id={} title={}", id, banner.getTitle());
        return BannerVO.from(banner);
    }

    @Override
    @CacheEvict(value = "banner", allEntries = true)
    public BannerVO updateStatus(Long id, Integer status) {
        if (!Integer.valueOf(0).equals(status) && !Integer.valueOf(1).equals(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "状态只能是 0（下架）或 1（上架）");
        }
        Banner banner = requireBanner(id);
        banner.setStatus(status);
        bannerMapper.updateById(banner);
        log.info("轮播图{} id={}", status == 1 ? "上架" : "下架", id);
        return BannerVO.from(banner);
    }

    @Override
    @CacheEvict(value = "banner", allEntries = true)
    public void remove(Long id) {
        requireBanner(id);
        bannerMapper.deleteById(id);
        log.info("删除轮播图 id={}", id);
    }

    /** 查询轮播图，不存在即抛 404 */
    private Banner requireBanner(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "轮播图 ID 不能为空");
        }
        Banner banner = bannerMapper.selectById(id);
        if (banner == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "轮播图不存在或已被删除");
        }
        return banner;
    }

    /** 把请求字段写入实体（不含 sort / status，这两个字段在各自方法里单独处理） */
    private void applyRequest(Banner banner, BannerRequest request) {
        banner.setTitle(request.getTitle().trim());
        banner.setSubTitle(request.getSubTitle());
        // 空字符串统一存成 null：否则「清空图片地址」会存下 ""，
        // 前台判断 StrUtil.isBlank 也能过，但库里出现两种「空」会让数据看起来不一致。
        banner.setImageUrl(StrUtil.blankToDefault(request.getImageUrl(), null));
        banner.setBgColor(StrUtil.blankToDefault(request.getBgColor(), null));
        banner.setLinkUrl(StrUtil.blankToDefault(request.getLinkUrl(), null));
    }

    /** 下一个排序值 = 当前最大值 + 1 */
    private int nextSort() {
        List<Banner> all = bannerMapper.selectList(new LambdaQueryWrapper<Banner>()
                .select(Banner::getSort));
        return all.stream().map(Banner::getSort).filter(Objects::nonNull)
                .max(Integer::compareTo).orElse(0) + 1;
    }
}
