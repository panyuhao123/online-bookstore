package cn.edu.bookstore.model.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理端书单查询条件。
 *
 * <p>后台的筛选维度和前台不同：前台只关心「公开的」，后台关心的是
 * 「有没有该处理的书单」——因此多了 status（是否已隐藏）与 userId（某个用户的全部书单）。
 *
 * @author 毕设项目组
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminBookListQuery extends BasePageQuery {

    /** 书单名称或简介关键词，模糊匹配 */
    private String keyword;

    /** 治理状态筛选：1-正常 0-已隐藏，为空表示全部 */
    private Integer status;

    /** 创建者用户 ID 精确筛选（处理某个用户的违规书单时用） */
    private Long userId;

    /** 是否公开筛选：1-公开 0-仅自己可见，为空表示全部 */
    private Integer isPublic;
}
