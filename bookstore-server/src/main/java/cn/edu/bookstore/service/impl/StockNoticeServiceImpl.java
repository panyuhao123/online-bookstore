package cn.edu.bookstore.service.impl;

import cn.edu.bookstore.common.BusinessException;
import cn.edu.bookstore.common.ErrorCode;
import cn.edu.bookstore.entity.Book;
import cn.edu.bookstore.entity.StockNotice;
import cn.edu.bookstore.mapper.BookMapper;
import cn.edu.bookstore.mapper.StockNoticeMapper;
import cn.edu.bookstore.model.vo.PageVO;
import cn.edu.bookstore.model.vo.StockNoticeVO;
import cn.edu.bookstore.service.StockNoticeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 到货提醒业务实现（用户侧）。
 *
 * @author 毕设项目组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockNoticeServiceImpl implements StockNoticeService {

    private final StockNoticeMapper stockNoticeMapper;
    private final BookMapper bookMapper;

    @Override
    public StockNoticeVO subscribe(Long userId, Long bookId) {
        Book book = bookMapper.selectById(bookId);
        if (book == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "图书不存在或已被删除");
        }
        // 图书已下架时提示订阅也没意义：即便补了货，下架图书也买不到。
        if (!Integer.valueOf(1).equals(book.getStatus())) {
            throw new BusinessException(ErrorCode.BIZ_ERROR, "该书已下架，暂时无法购买");
        }
        if (book.getStock() != null && book.getStock() > 0) {
            throw new BusinessException(ErrorCode.BIZ_ERROR,
                    "《" + book.getTitle() + "》当前有货，可直接下单购买");
        }

        StockNotice existing = stockNoticeMapper.selectOne(new LambdaQueryWrapper<StockNotice>()
                .eq(StockNotice::getUserId, userId)
                .eq(StockNotice::getBookId, bookId));

        if (existing == null) {
            StockNotice notice = new StockNotice();
            notice.setUserId(userId);
            notice.setBookId(bookId);
            notice.setStatus(StockNotice.STATUS_PENDING);
            stockNoticeMapper.insert(notice);
            log.info("订阅到货提醒 userId={} bookId={}", userId, bookId);
            return StockNoticeVO.of(notice, book);
        }

        if (Integer.valueOf(StockNotice.STATUS_PENDING).equals(existing.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "已订阅该书的到货提醒，补货后会通知你");
        }
        // 已取消 / 已通知过：复用原记录重新置为待通知。
        // 这里不能物理删除后重插 —— (user_id, book_id) 上有唯一键，
        // 反复订阅/取消会不断产生新行，唯一键也就失去意义。
        existing.setStatus(StockNotice.STATUS_PENDING);
        existing.setNotifyTime(null);
        stockNoticeMapper.updateById(existing);
        log.info("重新订阅到货提醒 userId={} bookId={}", userId, bookId);
        return StockNoticeVO.of(existing, book);
    }

    @Override
    public PageVO<StockNoticeVO> myNotices(Long userId, Long pageNum, Long pageSize) {
        Long num = (pageNum == null || pageNum < 1) ? 1L : pageNum;
        Long size = (pageSize == null || pageSize < 1) ? 10L : Math.min(pageSize, 50L);

        IPage<StockNotice> page = stockNoticeMapper.selectPage(new Page<>(num, size),
                new LambdaQueryWrapper<StockNotice>()
                        .eq(StockNotice::getUserId, userId)
                        // 已取消的不展示：用户的「取消」语义就是「别再出现在我的列表里」
                        .ne(StockNotice::getStatus, StockNotice.STATUS_CANCELLED)
                        .orderByDesc(StockNotice::getCreateTime));

        List<StockNotice> records = page.getRecords() == null ? List.of() : page.getRecords();
        Map<Long, Book> bookMap = loadBookMap(records.stream().map(StockNotice::getBookId).toList());

        PageVO<StockNoticeVO> vo = new PageVO<>();
        // 图书被物理删除时跳过该条，避免用户点进一条 404 的记录
        vo.setList(records.stream()
                .map(notice -> StockNoticeVO.of(notice, bookMap.get(notice.getBookId())))
                .filter(item -> item.getBookTitle() != null)
                .toList());
        vo.setTotal(page.getTotal());
        vo.setPageNum(page.getCurrent());
        vo.setPageSize(page.getSize());
        vo.setPages(page.getPages());
        return vo;
    }

    @Override
    public void unsubscribe(Long userId, Long bookId) {
        StockNotice existing = stockNoticeMapper.selectOne(new LambdaQueryWrapper<StockNotice>()
                .eq(StockNotice::getUserId, userId)
                .eq(StockNotice::getBookId, bookId));
        if (existing == null) {
            // 取消一个本来就没有的订阅，结果与预期一致，直接返回成功（接口幂等）
            return;
        }
        existing.setStatus(StockNotice.STATUS_CANCELLED);
        stockNoticeMapper.updateById(existing);
        log.info("取消到货提醒订阅 userId={} bookId={}", userId, bookId);
    }

    /** 按 ID 批量取图书，返回 ID -> 图书 */
    private Map<Long, Book> loadBookMap(List<Long> bookIds) {
        if (bookIds == null || bookIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Book> books = bookMapper.selectBatchIds(bookIds);
        if (books == null || books.isEmpty()) {
            return Collections.emptyMap();
        }
        return books.stream().collect(Collectors.toMap(Book::getId, Function.identity(), (a, b) -> a));
    }
}
