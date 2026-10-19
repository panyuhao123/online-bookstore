package cn.edu.bookstore.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 修改购物车条目请求参数（改数量 / 改勾选状态）。
 *
 * <p>两个字段都可为空，只更新传入的字段，避免 APP 改勾选时误改数量。
 *
 * @author 毕设项目组
 */
@Data
public class CartUpdateRequest {

    /** 新的购买数量，为空表示不修改 */
    @Min(value = 1, message = "购买数量至少为 1")
    @Max(value = 99, message = "单个商品购买数量不能超过 99")
    private Integer quantity;

    /** 新的勾选状态：1-已选 0-未选，为空表示不修改 */
    private Integer checked;
}
