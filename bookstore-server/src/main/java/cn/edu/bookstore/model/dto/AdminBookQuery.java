package cn.edu.bookstore.model.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理端图书查询条件：在 APP 端查询条件基础上增加「状态」筛选。
 *
 * <p>继承而非改写 {@link BookQuery}，保证分页参数修正（pageSize 上限保护）
 * 与 APP 端完全一致，后台传超大 pageSize 同样拖不垮数据库。
 *
 * @author 毕设项目组
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminBookQuery extends BookQuery {

    /** 图书状态：1-上架 0-下架；为空表示不筛选 */
    private Integer status;
}
