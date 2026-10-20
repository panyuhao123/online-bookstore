package cn.edu.bookstore.service.impl;

import cn.edu.bookstore.entity.Category;
import cn.edu.bookstore.mapper.CategoryMapper;
import cn.edu.bookstore.model.vo.CategoryBookCountVO;
import cn.edu.bookstore.model.vo.CategoryVO;
import cn.edu.bookstore.service.CategoryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 分类业务实现（APP 端）。
 *
 * <p>缓存说明：分类是全站高频读取、极少变更的典型「读多写少」数据，
 * 因此整表列表缓存在 Redis；后台对分类的任何增删改都会清空该缓存，
 * 保证管理端调整后 APP 端立刻可见（缓存一致性）。
 *
 * <p>书目数说明：{@code bookCount} 统计的是<b>上架</b>图书数（见
 * {@link CategoryMapper#selectOnSaleBookCounts()}）。该值会随图书增删与上下架变化，
 * 所以管理端的图书写操作也必须一并清空本缓存 —— 只清 {@code book}/{@code hotBook}
 * 会让「小说 12 本」在新增图书后一直停在 12，属于静默的数据陈旧。
 *
 * @author 毕设项目组
 */
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;

    @Override
    @Cacheable(value = "category", key = "'list'")
    public List<CategoryVO> list() {
        List<Category> categories = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>().orderByDesc(Category::getSort).orderByAsc(Category::getId));
        if (categories == null || categories.isEmpty()) {
            return Collections.emptyList();
        }

        // 一次分组统计全部分类的书目数，避免逐分类 COUNT 的 N+1
        List<CategoryBookCountVO> stats = categoryMapper.selectOnSaleBookCounts();
        Map<Long, Long> countByCategory = stats == null
                ? Map.of()
                : stats.stream()
                        .filter(stat -> stat.getCategoryId() != null && stat.getBookCount() != null)
                        .collect(Collectors.toMap(
                                CategoryBookCountVO::getCategoryId,
                                CategoryBookCountVO::getBookCount,
                                Long::sum));

        return categories.stream().map(category -> {
            CategoryVO vo = CategoryVO.from(category);
            // 空分类不在聚合结果里，必须显式补 0：前端拿 null 会渲染出「共 null 本」
            vo.setBookCount(countByCategory.getOrDefault(category.getId(), 0L));
            return vo;
        }).toList();
    }
}
