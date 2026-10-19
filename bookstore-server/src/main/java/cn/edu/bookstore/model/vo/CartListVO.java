package cn.edu.bookstore.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 购物车列表返回结构：条目 + 已勾选合计。
 *
 * <p>合计金额由服务端计算，避免 APP 端重复实现金额逻辑导致不一致。
 *
 * @author 毕设项目组
 */
@Data
public class CartListVO {

    /** 购物车条目 */
    private List<CartVO> list;

    /** 已勾选商品的合计金额 */
    private BigDecimal checkedAmount;

    /** 已勾选商品的总件数 */
    private Integer checkedCount;
}
