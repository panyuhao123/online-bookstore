package cn.edu.bookstore.service.impl;

import cn.edu.bookstore.common.BusinessException;
import cn.edu.bookstore.common.ErrorCode;
import cn.edu.bookstore.entity.Book;
import cn.edu.bookstore.entity.Category;
import cn.edu.bookstore.entity.OrderItem;
import cn.edu.bookstore.mapper.BookMapper;
import cn.edu.bookstore.mapper.CategoryMapper;
import cn.edu.bookstore.mapper.OrderItemMapper;
import cn.edu.bookstore.model.dto.AdminBookQuery;
import cn.edu.bookstore.model.dto.BookSaveRequest;
import cn.edu.bookstore.model.vo.BookVO;
import cn.edu.bookstore.model.vo.CategoryVO;
import cn.edu.bookstore.model.vo.PageVO;
import cn.edu.bookstore.service.AdminBookService;
import cn.edu.bookstore.service.CategoryService;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端图书业务实现。
 *
 * <p>关键约束：
 * <ul>
 *     <li>分类名称一次性构建 Map 后批量回填，避免逐条查分类的 N+1 查询；</li>
 *     <li>同名且同作者的图书视为重复数据，新增/修改均拦截（409）；</li>
 *     <li>已被下单过的图书禁止物理删除，避免订单明细里的图书快照失去来源，
 *         管理端应改用「下架」。</li>
 * </ul>
 *
 * <p>缓存：图书的增 / 改 / 上下架 / 删都会改变 {@code /api/category/list} 里的
 * {@code bookCount}（该字段统计上架图书数），因此四个写方法都要连带清空 {@code category} 缓存。
 * 漏掉任何一个，前台分类上的书目数就会一直停在旧值且没有任何报错。
 *
 * @author 毕设项目组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminBookServiceImpl implements AdminBookService {

    /** 上架状态 */
    private static final int STATUS_ON_SALE = 1;

    /** 下架状态 */
    private static final int STATUS_OFF_SALE = 0;

    private final BookMapper bookMapper;
    private final CategoryMapper categoryMapper;
    private final OrderItemMapper orderItemMapper;
    private final CategoryService categoryService;

    @Override
    public PageVO<BookVO> page(AdminBookQuery query) {
        query.normalize();
        query.normalizePriceRange();

        LambdaQueryWrapper<Book> wrapper = new LambdaQueryWrapper<Book>()
                .eq(query.getStatus() != null, Book::getStatus, query.getStatus())
                .eq(query.getCategoryId() != null, Book::getCategoryId, query.getCategoryId())
                .ge(query.getMinPrice() != null, Book::getPrice, query.getMinPrice())
                .le(query.getMaxPrice() != null, Book::getPrice, query.getMaxPrice())
                .and(StrUtil.isNotBlank(query.getKeyword()), w -> w
                        .like(Book::getTitle, query.getKeyword())
                        .or()
                        .like(Book::getAuthor, query.getKeyword()))
                .orderByDesc(Book::getSales)
                .orderByDesc(Book::getId);

        IPage<Book> page = bookMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);

        Map<Long, String> categoryNameMap = loadCategoryNameMap();
        return PageVO.of(page, book -> {
            BookVO vo = BookVO.from(book);
            vo.setCategoryName(categoryNameMap.get(book.getCategoryId()));
            return vo;
        });
    }

    @Override
    public BookVO detail(Long id) {
        BookVO vo = BookVO.from(requireBook(id));
        vo.setCategoryName(loadCategoryNameMap().get(vo.getCategoryId()));
        return vo;
    }

    @Override
    @CacheEvict(value = {"book", "hotBook", "category"}, allEntries = true)
    public Long create(BookSaveRequest request) {
        validateCategory(request.getCategoryId());
        checkDuplicate(null, request.getTitle(), request.getAuthor());

        Book book = toEntity(request);
        book.setSales(0);
        bookMapper.insert(book);
        log.info("管理端新增图书 bookId={} title={}", book.getId(), book.getTitle());
        return book.getId();
    }

    @Override
    @CacheEvict(value = {"book", "hotBook", "category"}, allEntries = true)
    public void update(Long id, BookSaveRequest request) {
        Book existed = requireBook(id);
        validateCategory(request.getCategoryId());
        checkDuplicate(id, request.getTitle(), request.getAuthor());

        Book book = toEntity(request);
        book.setId(id);
        // 销量由下单流程累加，管理端不可改写
        book.setSales(existed.getSales());
        bookMapper.updateById(book);
        log.info("管理端修改图书 bookId={}", id);
    }

    @Override
    @CacheEvict(value = {"book", "hotBook", "category"}, allEntries = true)
    public void updateStatus(Long id, Integer status) {
        requireBook(id);
        if (status == null || (status != STATUS_ON_SALE && status != STATUS_OFF_SALE)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "状态值不合法，仅支持 1-上架 / 0-下架");
        }
        Book book = new Book();
        book.setId(id);
        book.setStatus(status);
        bookMapper.updateById(book);
        log.info("管理端修改图书上下架 bookId={} status={}", id, status);
    }

    @Override
    @CacheEvict(value = {"book", "hotBook", "category"}, allEntries = true)
    public void delete(Long id) {
        requireBook(id);
        Long ordered = orderItemMapper.selectCount(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getBookId, id));
        if (ordered != null && ordered > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该图书已产生订单记录，请改为下架而非删除");
        }
        bookMapper.deleteById(id);
        log.info("管理端删除图书 bookId={}", id);
    }

    /** 查询图书，不存在即抛业务异常 */
    private Book requireBook(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "图书 ID 不能为空");
        }
        Book book = bookMapper.selectById(id);
        if (book == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "图书不存在");
        }
        return book;
    }

    /** 校验分类存在性：后台新增时若分类被删，避免写入脏数据 */
    private void validateCategory(Long categoryId) {
        Long count = categoryMapper.selectCount(
                new LambdaQueryWrapper<Category>().eq(Category::getId, categoryId));
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "所选分类不存在，请重新选择");
        }
    }

    /** 同名同作者判重；author 为空时只按书名判重 */
    private void checkDuplicate(Long id, String title, String author) {
        Long count = bookMapper.selectCount(new LambdaQueryWrapper<Book>()
                .eq(Book::getTitle, title)
                .eq(StrUtil.isNotBlank(author), Book::getAuthor, author)
                .ne(id != null, Book::getId, id));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "已存在同名图书，请确认后再提交");
        }
    }

    private Book toEntity(BookSaveRequest request) {
        Book book = new Book();
        book.setCategoryId(request.getCategoryId());
        book.setTitle(request.getTitle().trim());
        book.setAuthor(trimToNull(request.getAuthor()));
        book.setPublisher(trimToNull(request.getPublisher()));
        book.setPrice(request.getPrice());
        book.setStock(request.getStock());
        book.setCoverUrl(trimToNull(request.getCoverUrl()));
        book.setDescription(trimToNull(request.getDescription()));
        book.setStatus(request.getStatus() != null && request.getStatus() == STATUS_ON_SALE
                ? STATUS_ON_SALE : STATUS_OFF_SALE);
        return book;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 分类 ID -> 名称映射（复用 APP 端分类接口，分类数据量小，全量加载成本可忽略） */
    private Map<Long, String> loadCategoryNameMap() {
        List<CategoryVO> categories = categoryService.list();
        Map<Long, String> map = new HashMap<>();
        if (categories != null) {
            categories.forEach(c -> map.put(c.getId(), c.getName()));
        }
        return map;
    }
}
