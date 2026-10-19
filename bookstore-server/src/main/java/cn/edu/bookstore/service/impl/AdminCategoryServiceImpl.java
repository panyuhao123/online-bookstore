package cn.edu.bookstore.service.impl;

import cn.edu.bookstore.common.BusinessException;
import cn.edu.bookstore.common.ErrorCode;
import cn.edu.bookstore.entity.Book;
import cn.edu.bookstore.entity.Category;
import cn.edu.bookstore.mapper.BookMapper;
import cn.edu.bookstore.mapper.CategoryMapper;
import cn.edu.bookstore.model.dto.CategorySaveRequest;
import cn.edu.bookstore.model.vo.CategoryVO;
import cn.edu.bookstore.service.AdminCategoryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 管理端分类业务实现。
 *
 * <p>约束：
 * <ul>
 *     <li>分类名称不可重复（409）；</li>
 *     <li>分类下存在图书时禁止删除，避免图书变成孤儿数据（应先迁移或删除图书）。</li>
 * </ul>
 *
 * <p>缓存：分类的任何变更都会清空 APP 端分类列表缓存；
 * 改名还会连带清空图书详情缓存（详情里冗余了分类名称）。
 *
 * @author 毕设项目组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCategoryServiceImpl implements AdminCategoryService {

    private final CategoryMapper categoryMapper;
    private final BookMapper bookMapper;

    @Override
    public List<CategoryVO> list() {
        List<Category> categories = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>().orderByDesc(Category::getSort).orderByAsc(Category::getId));
        if (categories == null) {
            return List.of();
        }
        // 逐个分类统计图书数：分类数量本就有限（十几个），逐条 COUNT 的开销远小于
        // 引入一次全表分组查询带来的复杂度，属于明确的数据量权衡
        return categories.stream().map(category -> {
            CategoryVO vo = CategoryVO.from(category);
            vo.setBookCount(nvl(bookMapper.selectCount(
                    new LambdaQueryWrapper<Book>().eq(Book::getCategoryId, category.getId()))));
            return vo;
        }).toList();
    }

    /** 兜底：COUNT 理论上不返回 null，转换为 0 可让前端直接做数值比较 */
    private Long nvl(Long value) {
        return value == null ? 0L : value;
    }

    @Override
    @CacheEvict(value = "category", allEntries = true)
    public Long create(CategorySaveRequest request) {
        checkNameDuplicate(request.getName(), null);
        Category category = toEntity(request);
        categoryMapper.insert(category);
        log.info("管理端新增分类 categoryId={} name={}", category.getId(), category.getName());
        return category.getId();
    }

    @Override
    @CacheEvict(value = {"category", "book"}, allEntries = true)
    public void update(Long id, CategorySaveRequest request) {
        Category existed = requireCategory(id);
        checkNameDuplicate(request.getName(), id);

        Category category = toEntity(request);
        category.setId(existed.getId());
        categoryMapper.updateById(category);
        log.info("管理端修改分类 categoryId={}", id);
    }

    @Override
    @CacheEvict(value = "category", allEntries = true)
    public void delete(Long id) {
        requireCategory(id);
        Long bookCount = bookMapper.selectCount(
                new LambdaQueryWrapper<Book>().eq(Book::getCategoryId, id));
        if (bookCount != null && bookCount > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该分类下仍有图书，请先调整图书后再删除");
        }
        categoryMapper.deleteById(id);
        log.info("管理端删除分类 categoryId={}", id);
    }

    private Category requireCategory(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "分类 ID 不能为空");
        }
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "分类不存在");
        }
        return category;
    }

    private void checkNameDuplicate(String name, Long selfId) {
        Long count = categoryMapper.selectCount(new LambdaQueryWrapper<Category>()
                .eq(Category::getName, name)
                .ne(selfId != null, Category::getId, selfId));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该分类名称已存在");
        }
    }

    private Category toEntity(CategorySaveRequest request) {
        Category category = new Category();
        category.setName(request.getName().trim());
        category.setSort(request.getSort() == null || request.getSort() < 0 ? 0 : request.getSort());
        category.setRemark(request.getRemark() == null || request.getRemark().isBlank()
                ? null : request.getRemark().trim());
        return category;
    }
}
