package cn.edu.bookstore.service.impl;

import cn.edu.bookstore.entity.Book;
import cn.edu.bookstore.entity.Category;
import cn.edu.bookstore.mapper.BookMapper;
import cn.edu.bookstore.mapper.CategoryMapper;
import cn.edu.bookstore.mapper.OrdersMapper;
import cn.edu.bookstore.model.vo.BookVO;
import cn.edu.bookstore.model.vo.SalesTrendVO;
import cn.edu.bookstore.service.AdminStatsService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理端数据统计实现（后台看板）。
 *
 * @author 毕设项目组
 */
@Service
@RequiredArgsConstructor
public class AdminStatsServiceImpl implements AdminStatsService {

    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 趋势最多看 90 天：再多的话折线图挤在一起也读不出信息，同时避免拉取过多数据 */
    private static final int MAX_TREND_DAYS = 90;

    /** 排行榜最多 50 条 */
    private static final int MAX_TOP_LIMIT = 50;

    private final OrdersMapper ordersMapper;
    private final BookMapper bookMapper;
    private final CategoryMapper categoryMapper;

    @Override
    public List<SalesTrendVO> salesTrend(int days) {
        int span = Math.min(Math.max(days, 1), MAX_TREND_DAYS);
        LocalDate today = LocalDate.now();
        LocalDate firstDay = today.minusDays(span - 1L);

        // 数据库侧 GROUP BY 出有订单的日期，再把「空日期」补齐为 0。
        // 补齐这一步放在 Java 里做而不是用 SQL 生成日期序列，
        // 是因为 MySQL 生成连续日期需要递归 CTE 或辅助表，成本远高于几十次内存循环。
        List<SalesTrendVO> rows = ordersMapper.selectDailyTrend(firstDay.atStartOfDay());
        Map<String, SalesTrendVO> byDate = rows == null ? Map.of()
                : rows.stream().collect(Collectors.toMap(
                        SalesTrendVO::getDate, Function.identity(), (a, b) -> a, LinkedHashMap::new));

        List<SalesTrendVO> result = new ArrayList<>(span);
        for (int i = 0; i < span; i++) {
            String date = firstDay.plusDays(i).format(DAY_FORMATTER);
            SalesTrendVO point = byDate.get(date);
            if (point == null) {
                point = new SalesTrendVO();
                point.setDate(date);
                point.setOrderCount(0L);
                point.setAmount(BigDecimal.ZERO);
            }
            result.add(point);
        }
        return result;
    }

    @Override
    public List<BookVO> topSellingBooks(int limit) {
        int size = Math.min(Math.max(limit, 1), MAX_TOP_LIMIT);

        // 不筛 status：后台看的是销售表现，已下架图书的历史销量同样有分析价值。
        // 排序走 idx_book_sales 索引，LIMIT 保证只读需要的行。
        List<Book> books = bookMapper.selectList(new LambdaQueryWrapper<Book>()
                .gt(Book::getSales, 0)
                .orderByDesc(Book::getSales)
                .orderByDesc(Book::getId)
                .last("LIMIT " + size));

        Map<Long, String> categoryNameMap = categoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));

        return books.stream().map(book -> {
            BookVO vo = BookVO.from(book);
            vo.setCategoryName(categoryNameMap.get(book.getCategoryId()));
            return vo;
        }).toList();
    }
}
