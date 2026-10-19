package cn.edu.bookstore.model.vo;

import cn.edu.bookstore.entity.OrderItem;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单明细视图对象。
 *
 * @author 毕设项目组
 */
@Data
public class OrderItemVO {

    private Long bookId;

    private String bookTitle;

    private String bookCover;

    /** 下单时单价快照 */
    private BigDecimal price;

    private Integer quantity;

    private BigDecimal subtotal;

    public static OrderItemVO from(OrderItem item) {
        if (item == null) {
            return null;
        }
        OrderItemVO vo = new OrderItemVO();
        vo.setBookId(item.getBookId());
        vo.setBookTitle(item.getBookTitle());
        vo.setBookCover(item.getBookCover());
        vo.setPrice(item.getPrice());
        vo.setQuantity(item.getQuantity());
        vo.setSubtotal(item.getSubtotal());
        return vo;
    }
}
