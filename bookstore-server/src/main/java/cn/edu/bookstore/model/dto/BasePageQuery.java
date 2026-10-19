package cn.edu.bookstore.model.dto;

import lombok.Data;

/**
 * 分页查询基类。
 *
 * <p>抽取目的：APP 端与管理端的各类分页条件（图书、订单、用户）都需要
 * 页码与页大小，避免每处重复声明、也避免漏掉 pageSize 上限保护。
 *
 * @author 毕设项目组
 */
@Data
public class BasePageQuery {

    /** 每页条数上限：无论前端传多大都不超过该值，防止大分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 50L;

    /** 页码，从 1 开始，默认 1 */
    private Long pageNum = 1L;

    /** 每页条数，默认 10 */
    private Long pageSize = 10L;

    /** 修正非法分页参数：页码最小为 1，页大小限制在 1 ~ 50 */
    public void normalize() {
        if (pageNum == null || pageNum < 1) {
            pageNum = 1L;
        }
        if (pageSize == null || pageSize < 1) {
            pageSize = 10L;
        }
        if (pageSize > MAX_PAGE_SIZE) {
            pageSize = MAX_PAGE_SIZE;
        }
    }
}
