package cn.edu.bookstore.model.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 购物车条目视图对象（含图书快照信息，APP 端无需二次请求图书接口）。
 *
 * @author 毕设项目组
 */
@Data
public class CartVO {

    /** 购物车记录 ID */
    private Long id;

    private Long bookId;

    /** 书名 */
    private String title;

    private String coverUrl;

    /** 当前单价（实时取图书表，价格变动会反映到购物车） */
    private BigDecimal price;

    /** 库存，用于限制最大可买数量 */
    private Integer stock;

    private Integer quantity;

    /** 是否勾选：1-已选 0-未选 */
    private Integer checked;

    /** 小计 = price * quantity */
    private BigDecimal subtotal;

    /** 库存是否充足（不足时 APP 端置灰并提示） */
    private Boolean stockEnough;
}
