package cn.edu.bookstore.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 加入购物车请求参数。
 *
 * @author 毕设项目组
 */
@Data
public class CartAddRequest {

    @NotNull(message = "图书 ID 不能为空")
    private Long bookId;

    /** 购买数量，默认 1 */
    @NotNull(message = "购买数量不能为空")
    @Min(value = 1, message = "购买数量至少为 1")
    @Max(value = 99, message = "单个商品购买数量不能超过 99")
    private Integer quantity = 1;
}
