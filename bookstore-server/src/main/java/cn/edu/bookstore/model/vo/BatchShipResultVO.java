package cn.edu.bookstore.model.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量发货结果。
 *
 * <p>返回「成功几笔 + 跳过原因」而不是整体失败：运营勾选时经常混进已发货或已取消的单，
 * 一次性全部拒绝会让操作员被迫逐单排查；告诉他哪几笔被跳过、为什么跳过，才改得动。
 *
 * @author 毕设项目组
 */
@Data
public class BatchShipResultVO {

    /** 本次实际发货成功的笔数 */
    private Integer shipped;

    /** 未发货的订单及原因（订单号 + 原因），全部成功时为空列表 */
    private List<Skipped> skipped = new ArrayList<>();

    @Data
    public static class Skipped {

        private Long id;

        private String orderNo;

        private String reason;

        public Skipped() {
        }

        public Skipped(Long id, String orderNo, String reason) {
            this.id = id;
            this.orderNo = orderNo;
            this.reason = reason;
        }
    }
}
