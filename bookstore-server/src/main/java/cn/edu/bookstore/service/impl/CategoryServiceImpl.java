package cn.edu.bookstore.service.impl;

import cn.edu.bookstore.entity.Category;
import cn.edu.bookstore.mapper.CategoryMapper;
import cn.edu.bookstore.model.vo.CategoryVO;
import cn.edu.bookstore.service.CategoryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 分类业务实现（APP 端）。
 *
 * <p>缓存说明：分类是全站高频读取、极少变更的典型「读多写少」数据，
 * 因此整表列表缓存在 Redis；后台对分类的任何增删改都会清空该缓存，
 * 保证管理端调整后 APP 端立刻可见（缓存一致性）。
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
        if (categories == null) {
            return Collections.emptyList();
        }
        return categories.stream().map(CategoryVO::from).toList();
    }
}
