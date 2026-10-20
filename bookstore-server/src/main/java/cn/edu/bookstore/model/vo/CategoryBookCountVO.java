package cn.edu.bookstore.model.vo;

import lombok.Data;

/**
 * 分类书目数聚合结果（按 category_id 分组）。
 *
 * <p>用于分类列表：一次 GROUP BY 拿到全部分类的在售图书数，避免逐分类 COUNT 造成 N+1。
 * 分类数量有限（十几个）时 N+1 也可接受，但该接口被 APP 首页、分类页、PC 商城同时读取，
 * 属于全站最高频的查询之一，因此选择在数据库侧一次算完。
 *
 * @author 毕设项目组
 */
@Data
public class CategoryBookCountVO {

    private Long categoryId;

    /** 该书目数只统计上架（status=1）图书 */
    private Long bookCount;
}
