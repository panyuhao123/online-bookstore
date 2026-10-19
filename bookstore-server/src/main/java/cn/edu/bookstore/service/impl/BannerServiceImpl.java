package cn.edu.bookstore.service.impl;

import cn.edu.bookstore.entity.Banner;
import cn.edu.bookstore.mapper.BannerMapper;
import cn.edu.bookstore.model.vo.BannerVO;
import cn.edu.bookstore.service.BannerService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 首页轮播图业务实现（前台）。
 *
 * <p>缓存键固定为 {@code 'visible'}（而不是按参数生成）：该方法没有参数，
 * 显式写出常量键便于在 redis-cli 里直接定位
 * （{@code GET bookstore:banner::visible}），排查「首页怎么还是旧图」时很省事。
 *
 * @author 毕设项目组
 */
@Service
@RequiredArgsConstructor
public class BannerServiceImpl implements BannerService {

    private final BannerMapper bannerMapper;

    @Override
    @Cacheable(value = "banner", key = "'visible'")
    public List<BannerVO> visibleList() {
        List<Banner> list = bannerMapper.selectList(new LambdaQueryWrapper<Banner>()
                .eq(Banner::getStatus, 1)
                .orderByAsc(Banner::getSort)
                .orderByAsc(Banner::getId));
        if (list == null) {
            return List.of();
        }
        return list.stream().map(BannerVO::from).toList();
    }
}
