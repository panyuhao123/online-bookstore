package cn.edu.bookstore.model.vo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * 统一分页返回结构。
 *
 * <p>为什么不用 MyBatis-Plus 的 IPage 直接返回：IPage 序列化后字段冗余
 * （orders / optimizeCountSql 等），APP 端解析不便，因此统一转换为精简结构。
 *
 * @param <T> 列表元素类型
 * @author 毕设项目组
 */
@Data
public class PageVO<T> {

    /** 当前页数据 */
    private List<T> list;

    /** 总记录数 */
    private long total;

    /** 当前页码，从 1 开始 */
    private long pageNum;

    /** 每页条数 */
    private long pageSize;

    /** 总页数 */
    private long pages;

    /** 将 MyBatis-Plus 分页结果转换为精简结构（不做元素转换） */
    public static <T> PageVO<T> of(IPage<T> page) {
        return of(page, Function.identity());
    }

    /** 将 MyBatis-Plus 分页结果转换并映射元素类型（如 Entity -> VO） */
    public static <E, T> PageVO<T> of(IPage<E> page, Function<E, T> mapper) {
        PageVO<T> vo = new PageVO<>();
        List<E> records = page.getRecords();
        vo.setList(records == null ? Collections.emptyList() : records.stream().map(mapper).toList());
        vo.setTotal(page.getTotal());
        vo.setPageNum(page.getCurrent());
        vo.setPageSize(page.getSize());
        vo.setPages(page.getPages());
        return vo;
    }
}
