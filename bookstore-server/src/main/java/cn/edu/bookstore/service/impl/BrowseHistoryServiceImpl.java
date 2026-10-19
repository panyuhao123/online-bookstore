package cn.edu.bookstore.service.impl;

import cn.edu.bookstore.entity.Book;
import cn.edu.bookstore.entity.BrowseHistory;
import cn.edu.bookstore.mapper.BookMapper;
import cn.edu.bookstore.mapper.BrowseHistoryMapper;
import cn.edu.bookstore.model.vo.HistoryItemVO;
import cn.edu.bookstore.model.vo.PageVO;
import cn.edu.bookstore.service.BrowseHistoryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 浏览历史业务实现。
 *
 * <p>几个关键取舍：
 * <ul>
 *     <li><b>表上不设行数上限，由本类裁剪</b>。上限属于可调的业务策略（100 条是
 *         「够回溯但不至于变成数据堆积」的经验值），写成触发器或约束后调整就得改表。
 *         裁剪时机放在每次写入之后，保证读路径永远是「已经裁剪过的」状态。</li>
 *     <li><b>删单条用「删除行」而不是「打删除标记」</b>。浏览历史没有审计价值，
 *         用户点删除就是希望它消失；留标记只会让后续查询都要多带一个条件。</li>
 *     <li><b>合并时不让旧记录覆盖新记录</b>。用户可能在这台设备登录后又浏览了几本，
 *         再在另一台设备点合并，此时不该把更新的时间戳改成更旧的。</li>
 * </ul>
 *
 * @author 毕设项目组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BrowseHistoryServiceImpl implements BrowseHistoryService {

    /** 单个用户保留的浏览记录上限 */
    private static final int MAX_PER_USER = 100;

    private final BrowseHistoryMapper browseHistoryMapper;
    private final BookMapper bookMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean record(Long userId, Long bookId) {
        if (userId == null || bookId == null) {
            return false;
        }
        // 图书不存在就静默跳过：记录浏览是「顺手做的事」，
        // 为一个不存在的 ID 抛异常会干扰用户正常的浏览动作。
        Book book = bookMapper.selectById(bookId);
        if (book == null) {
            log.debug("浏览记录跳过不存在的图书 userId={} bookId={}", userId, bookId);
            return false;
        }

        upsert(userId, bookId, LocalDateTime.now());
        trim(userId);
        return true;
    }

    @Override
    public PageVO<HistoryItemVO> page(Long userId, Long pageNum, Long pageSize) {
        Long num = (pageNum == null || pageNum < 1) ? 1L : pageNum;
        Long size = (pageSize == null || pageSize < 1) ? 10L : Math.min(pageSize, 50L);

        IPage<BrowseHistory> page = browseHistoryMapper.selectPage(new Page<>(num, size),
                new LambdaQueryWrapper<BrowseHistory>()
                        .eq(BrowseHistory::getUserId, userId)
                        .orderByDesc(BrowseHistory::getViewTime));

        List<BrowseHistory> records = page.getRecords() == null ? List.of() : page.getRecords();
        Map<Long, Book> bookMap = loadBookMap(records.stream().map(BrowseHistory::getBookId).toList());

        PageVO<HistoryItemVO> vo = new PageVO<>();
        // 图书被物理删除时不返回该条：浏览历史的条目必须可点击，
        // 返回一条点进去 404 的记录只会让用户困惑。被下架的图书则保留（status=0，前端置灰）。
        vo.setList(records.stream()
                .map(record -> HistoryItemVO.of(bookMap.get(record.getBookId()), record.getViewTime()))
                .filter(item -> item.getBookId() != null && item.getTitle() != null)
                .toList());
        vo.setTotal(page.getTotal());
        vo.setPageNum(page.getCurrent());
        vo.setPageSize(page.getSize());
        vo.setPages(page.getPages());
        return vo;
    }

    /**
     * 合并未登录期间产生的本地浏览记录。
     *
     * <p><b>返回值口径</b>：只统计真正新插入的行，不把「刷新了已有记录的时间」算进去 ——
     * 前端要拿这个数字告诉用户「同步了几条」，把刷新也算上就会给出一个
     * 与列表条数增量对不上的数字。
     *
     * @param bookIds 按浏览时间倒序（第 0 条最新）
     * @return 新增条数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int merge(Long userId, List<Long> bookIds) {
        if (bookIds == null || bookIds.isEmpty()) {
            return 0;
        }
        // 本地记录是按浏览时间倒序提交的，这里用「当前时刻 - 序号秒」还原出相对先后关系：
        // 第 0 条最新、越靠后越旧。只用秒级偏移而不是真实本地时间戳，
        // 是因为前端提交的是 ID 列表、不带时间，客户端时间也未必可信（可被随意修改）。
        LocalDateTime base = LocalDateTime.now();
        List<Long> distinct = bookIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, Book> bookMap = loadBookMap(distinct);

        int added = 0;
        for (int i = 0; i < distinct.size(); i++) {
            Long bookId = distinct.get(i);
            if (!bookMap.containsKey(bookId)) {
                continue;
            }
            if (i >= MAX_PER_USER) {
                // 超出保留上限的旧记录不必写库，写了也会立刻被裁掉
                break;
            }
            LocalDateTime viewTime = base.minusSeconds(i);
            if (upsert(userId, bookId, viewTime)) {
                added++;
            }
        }
        trim(userId);
        log.info("合并浏览历史 userId={} 提交={} 新增={}", userId, distinct.size(), added);
        return added;
    }

    @Override
    public void removeOne(Long userId, Long bookId) {
        browseHistoryMapper.delete(new LambdaQueryWrapper<BrowseHistory>()
                .eq(BrowseHistory::getUserId, userId)
                .eq(BrowseHistory::getBookId, bookId));
    }

    @Override
    public void clear(Long userId) {
        browseHistoryMapper.delete(new LambdaQueryWrapper<BrowseHistory>()
                .eq(BrowseHistory::getUserId, userId));
        log.info("清空浏览历史 userId={}", userId);
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    /**
     * 写入或刷新一条浏览记录。
     *
     * <p>返回值刻意只表示「这条记录是新增的」，而不是「这次调用改动了数据库」：
     * {@link #merge} 要把结果回报给前端（「已同步 N 条浏览记录」），
     * 而「库中本来就有、只是把浏览时间往前挪了一秒」如果也计入新增，
     * 用户会看到「新增 3 条」而实际只多了 2 条 —— 数字对不上，前端也就失去了告知的价值。
     * 刷新时间这件事在方法内部完成，调用方不需要知道。
     *
     * @param viewTime 新的浏览时间
     * @return true 表示新插入了一行；false 表示库中已有该记录（时间可能被推进，但不计新增）
     */
    private boolean upsert(Long userId, Long bookId, LocalDateTime viewTime) {
        BrowseHistory existing = browseHistoryMapper.selectOne(new LambdaQueryWrapper<BrowseHistory>()
                .eq(BrowseHistory::getUserId, userId)
                .eq(BrowseHistory::getBookId, bookId));
        if (existing == null) {
            BrowseHistory history = new BrowseHistory();
            history.setUserId(userId);
            history.setBookId(bookId);
            history.setViewTime(viewTime);
            browseHistoryMapper.insert(history);
            return true;
        }
        // 已有记录的浏览时间更新（或不变）时不动它：合并本地记录的场景下，
        // 本地的「几小时前浏览」不该把库里的「刚刚浏览过」改回去。
        if (existing.getViewTime() == null || viewTime.isAfter(existing.getViewTime())) {
            existing.setViewTime(viewTime);
            browseHistoryMapper.updateById(existing);
        }
        return false;
    }

    /**
     * 裁剪超出上限的旧记录。
     *
     * <p>实现上先查主键再批量删，而不是写一条带子查询的 DELETE：
     * MySQL 不允许在 DELETE 的子查询里引用同一张表，绕开需要再套一层派生表，
     * 可读性反而更差，而在 100 条量级下一次额外查询的成本可以忽略。
     */
    private void trim(Long userId) {
        long total = browseHistoryMapper.selectCount(new LambdaQueryWrapper<BrowseHistory>()
                .eq(BrowseHistory::getUserId, userId));
        if (total <= MAX_PER_USER) {
            return;
        }
        long exceed = total - MAX_PER_USER;
        List<BrowseHistory> stale = browseHistoryMapper.selectList(new LambdaQueryWrapper<BrowseHistory>()
                .select(BrowseHistory::getId)
                .eq(BrowseHistory::getUserId, userId)
                .orderByAsc(BrowseHistory::getViewTime)
                .last("LIMIT " + exceed));
        if (stale.isEmpty()) {
            return;
        }
        List<Long> ids = new ArrayList<>(stale.stream().map(BrowseHistory::getId).toList());
        browseHistoryMapper.deleteBatchIds(ids);
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
