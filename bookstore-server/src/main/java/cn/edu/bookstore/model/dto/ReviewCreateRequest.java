package cn.edu.bookstore.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发表评价请求参数。
 *
 * <p>不接收 userId：评价人从登录上下文取，防止伪造成他人发表评价。
 *
 * @author 毕设项目组
 */
@Data
public class ReviewCreateRequest {

    /** 来源订单 ID：服务端据此校验「订单属于当前用户 + 已完成 + 含该书」 */
    @NotNull(message = "请指定来源订单")
    private Long orderId;

    @NotNull(message = "请指定要评价的图书")
    private Long bookId;

    @NotNull(message = "请选择星级评分")
    @Min(value = 1, message = "星级最低 1 星")
    @Max(value = 5, message = "星级最高 5 星")
    private Integer rating;

    @Size(max = 500, message = "评价内容不能超过 500 字")
    private String content;

    /** 是否匿名：默认不匿名 */
    private Boolean anonymous = Boolean.FALSE;
}
