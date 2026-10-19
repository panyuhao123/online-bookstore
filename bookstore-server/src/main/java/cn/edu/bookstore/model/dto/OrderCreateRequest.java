package cn.edu.bookstore.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 提交订单请求参数。
 *
 * <p>cartIds 为空时，服务端取购物车中所有「已勾选」的商品下单，
 * 与 APP 端「勾选后提交」的交互一致；传了 cartIds 则只结算这些条目。
 *
 * @author 毕设项目组
 */
@Data
public class OrderCreateRequest {

    /** 收货地址 ID */
    @NotNull(message = "请选择收货地址")
    private Long addressId;

    /** 参与下单的购物车条目 ID，可为空 */
    private List<Long> cartIds;
}
