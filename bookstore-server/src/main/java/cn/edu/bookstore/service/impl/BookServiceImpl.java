package cn.edu.bookstore.service.impl;

import cn.edu.bookstore.common.BusinessException;
import cn.edu.bookstore.common.ErrorCode;
import cn.edu.bookstore.entity.Book;
import cn.edu.bookstore.entity.Category;
import cn.edu.bookstore.mapper.BookMapper;
import cn.edu.bookstore.mapper.CategoryMapper;
import cn.edu.bookstore.model.dto.BookQuery;
import cn.edu.bookstore.model.vo.BookRatingStatVO;
import cn.edu.bookstore.model.vo.BookVO;
import cn.edu.bookstore.model.vo.PageVO;
import cn.edu.bookstore.service.BookService;
import cn.edu.bookstore.service.ReviewService;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 图书业务实现。
 *
 * <p>性能考虑：
 * <ul>
 *     <li>分类名称通过一次查询构建 Map 后批量回填，避免逐条查分类造成 N+1 查询；</li>
 *     <li>图书详情读多写少，缓存到 Redis（key = book::id）；
 *         列表页因带分页与关键词参数、命中率低且容易脏，不做缓存；</li>
 *     <li>写操作（后台改书、下架、删除，以及下单/取消导致的库存变化）
 *         都会清理 book 缓存，保证缓存与数据库不会长期不一致。</li>
 * </ul>
 *
 * @author 毕设项目组
 */
@Service
@RequiredArgsConstructor
public class BookServiceImpl implements BookService {

    /** 上架状态 */
    private static final int STATUS_ON_SALE = 1;

    /** 热门榜最多返回多少条：首页展示用，超过这个数既没意义也浪费带宽 */
    private static final int MAX_HOT_LIMIT = 20;

    private final BookMapper bookMapper;
    private final CategoryMapper categoryMapper;
    private final ReviewService reviewService;

    @Override
    public PageVO<BookVO> page(BookQuery query) {
        query.normalize();
        query.normalizePriceRange();
        query.normalizeSort();

        LambdaQueryWrapper<Book> wrapper = new LambdaQueryWrapper<Book>()
                .eq(Book::getStatus, STATUS_ON_SALE)
                .eq(query.getCategoryId() != null, Book::getCategoryId, query.getCategoryId())
                .ge(query.getMinPrice() != null, Book::getPrice, query.getMinPrice())
                .le(query.getMaxPrice() != null, Book::getPrice, query.getMaxPrice())
                .and(StrUtil.isNotBlank(query.getKeyword()), w -> w
                        .like(Book::getTitle, query.getKeyword())
                        .or()
                        .like(Book::getAuthor, query.getKeyword()));
        applySort(wrapper, query.getSort());

        IPage<Book> page = bookMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);

        Map<Long, String> categoryNameMap = loadCategoryNameMap();
        PageVO<BookVO> result = PageVO.of(page, book -> {
            BookVO vo = BookVO.from(book);
            vo.setCategoryName(categoryNameMap.get(book.getCategoryId()));
            return vo;
        });
        applyRatings(result.getList());
        return result;
    }

    /**
     * 按用户选择的排序方式组装 ORDER BY。
     *
     * <p>价格排序后追加 id 倒序作为次要排序键：价格相同时顺序保持稳定，
     * 否则分页会出现「第二页重复第一页数据」的经典问题。
     *
     * @param sort 已在 {@link BookQuery#normalizeSort()} 中做过白名单校验
     */
    private void applySort(LambdaQueryWrapper<Book> wrapper, String sort) {
        switch (sort) {
            case "price_asc" -> wrapper.orderByAsc(Book::getPrice).orderByDesc(Book::getId);
            case "price_desc" -> wrapper.orderByDesc(Book::getPrice).orderByDesc(Book::getId);
            case "sales" -> wrapper.orderByDesc(Book::getSales).orderByDesc(Book::getId);
            default -> wrapper.orderByDesc(Book::getSales).orderByDesc(Book::getId);
        }
    }

    @Override
    @Cacheable(value = "book", key = "#id")
    public BookVO detail(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "图书 ID 不能为空");
        }
        Book book = bookMapper.selectById(id);
        if (book == null || !Integer.valueOf(STATUS_ON_SALE).equals(book.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "图书不存在或已下架");
        }
        BookVO vo = BookVO.from(book);
        vo.setCategoryName(loadCategoryNameMap().get(book.getCategoryId()));
        // 评分一并写入缓存：详情页的评分变化只由「新增/隐藏/删除评价」触发，
        // 那三处都已显式清理 book 缓存，不会出现缓存比分落后于评价的情况。
        applyRatings(List.of(vo));
        return vo;
    }

    /**
     * 批量回填评分与评价数。
     *
     * <p>整页图书一次 GROUP BY 查询搞定，而不是循环里逐本查评价表 ——
     * 列表页每页 10~20 本，前者是 1 次查询，后者是 20 次。
     *
     * <p>没有评价的图书也要显式把 {@code reviewCount} 置 0：
     * 让前端拿到「确定的空值」，而不是去判断字段是否存在。
     */
    private void applyRatings(List<BookVO> books) {
        if (books == null || books.isEmpty()) {
            return;
        }
        List<Long> ids = books.stream().map(BookVO::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, BookRatingStatVO> stats = reviewService.statsByBookIds(ids);
        for (BookVO vo : books) {
            BookRatingStatVO stat = stats.get(vo.getId());
            if (stat == null) {
                vo.setReviewCount(0);
            } else {
                vo.setAvgRating(stat.getAvgRating());
                vo.setReviewCount(stat.getReviewCount() == null ? 0 : stat.getReviewCount());
            }
        }
    }

    /** 一次性加载分类 ID -> 名称映射（分类表数据量小，全量加载成本可忽略） */
    private Map<Long, String> loadCategoryNameMap() {
        List<Category> categories = categoryMapper.selectList(null);
        Map<Long, String> map = new HashMap<>();
        if (categories != null) {
            map.putAll(categories.stream()
                    .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a)));
        }
        return map;
    }

    @Override
    public Map<Long, Book> mapByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<Book> books = bookMapper.selectBatchIds(ids);
        return books.stream().collect(Collectors.toMap(Book::getId, Function.identity(), (a, b) -> a));
    }

    @Override
    @Cacheable(value = "hotBook", key = "#limit")
    public List<BookVO> hotBooks(int limit) {
        int size = Math.min(Math.max(limit, 1), MAX_HOT_LIMIT);

        // 按销量倒序：sales 上有 idx_book_sales 索引，排序可以走索引而不产生临时表。
        // 限制 LIMIT 而不是把全部上架图书查回来再截断 —— 首页接口要尽量少读数据。
        List<Book> books = bookMapper.selectList(new LambdaQueryWrapper<Book>()
                .eq(Book::getStatus, STATUS_ON_SALE)
                .orderByDesc(Book::getSales)
                .orderByDesc(Book::getId)
                .last("LIMIT " + size));

        Map<Long, String> categoryNameMap = loadCategoryNameMap();
        List<BookVO> list = books.stream().map(book -> {
            BookVO vo = BookVO.from(book);
            vo.setCategoryName(categoryNameMap.get(book.getCategoryId()));
            return vo;
        }).toList();
        applyRatings(list);
        return list;
    }
}
