package cn.edu.bookstore.model.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理端评价查询条件。
 *
 * @author 毕设项目组
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminReviewQuery extends BasePageQuery {

    /** 图书 ID 精确筛选 */
    private Long bookId;

    /** 图书标题或评价内容关键词，模糊匹配 */
    private String keyword;

    /** 星级筛选 1~5 */
    private Integer rating;

    /** 状态筛选：1-正常 0-已隐藏，为空表示全部 */
    private Integer status;
}
