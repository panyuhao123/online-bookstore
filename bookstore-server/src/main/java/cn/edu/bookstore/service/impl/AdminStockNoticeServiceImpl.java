package cn.edu.bookstore.service.impl;

import cn.edu.bookstore.common.BusinessException;
import cn.edu.bookstore.common.ErrorCode;
import cn.edu.bookstore.entity.StockNotice;
import cn.edu.bookstore.mapper.BookMapper;
import cn.edu.bookstore.mapper.StockNoticeMapper;
import cn.edu.bookstore.model.vo.PageVO;
import cn.edu.bookstore.model.vo.StockNoticeSummaryVO;
import cn.edu.bookstore.service.AdminStockNoticeService;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 到货提醒管理实现（管理端）。
 *
 * @author 毕设项目组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminStockNoticeServiceImpl implements AdminStockNoticeService {

    private final StockNoticeMapper stockNoticeMapper;
    private final BookMapper bookMapper;

    @Override
    public PageVO<StockNoticeSummaryVO> summary(String keyword, Boolean onlyWaiting,
                                               Long pageNum, Long pageSize) {
        Long num = (pageNum == null || pageNum < 1) ? 1L : pageNum;
        Long size = (pageSize == null || pageSize < 1) ? 10L : Math.min(pageSize, 50L);
        // 默认只看「还有人在等」的图书：这才是补货清单。
        boolean waiting = !Boolean.FALSE.equals(onlyWaiting);
        // 关键词两侧的空白先去掉；字段为空时传 null，让 SQL 里的 (#{keyword} IS NULL) 短路成立，
        // 免得写成 LIKE '%%' 白扫一遍索引
        String kw = StrUtil.trimToNull(keyword);

        long total = stockNoticeMapper.countSummary(kw, waiting);
        List<StockNoticeSummaryVO> list = total == 0
                ? List.of()
                : stockNoticeMapper.pageSummary(kw, waiting, (num - 1) * size, size);

        PageVO<StockNoticeSummaryVO> vo = new PageVO<>();
        vo.setList(list);
        vo.setTotal(total);
        vo.setPageNum(num);
        vo.setPageSize(size);
        vo.setPages(size <= 0 ? 0 : (total + size - 1) / size);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int markNotified(Long bookId) {
        if (bookId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "图书 ID 不能为空");
        }
        if (bookMapper.selectById(bookId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "图书不存在或已被删除");
        }
        int rows = stockNoticeMapper.update(null, new LambdaUpdateWrapper<StockNotice>()
                .set(StockNotice::getStatus, StockNotice.STATUS_NOTIFIED)
                .set(StockNotice::getNotifyTime, LocalDateTime.now())
                .eq(StockNotice::getBookId, bookId)
                .eq(StockNotice::getStatus, StockNotice.STATUS_PENDING));
        log.info("标记到货提醒已通知 bookId={} 条数={}", bookId, rows);
        return rows;
    }
}
