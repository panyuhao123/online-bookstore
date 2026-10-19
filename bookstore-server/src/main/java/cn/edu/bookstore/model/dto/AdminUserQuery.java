package cn.edu.bookstore.model.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理端用户查询条件：支持手机号 / 昵称模糊搜索与状态筛选。
 *
 * @author 毕设项目组
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminUserQuery extends BasePageQuery {

    /** 搜索关键词，匹配手机号或昵称 */
    private String keyword;

    /** 状态：1-正常 0-禁用；为空表示不筛选 */
    private Integer status;
}
