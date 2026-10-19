package cn.edu.bookstore.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 订单状态枚举。
 *
 * <p>与数据库 orders.status 字段一一对应；对外统一用 text 展示，
 * 避免 APP 端再写一份状态文案映射导致前后端不一致。
 *
 * @author 毕设项目组
 */
@Getter
@AllArgsConstructor
public enum OrderStatus {

    /** 下单后的初始状态，等待用户点击「模拟支付」 */
    PENDING_PAY(1, "待支付"),

    /** 模拟支付完成 */
    PAID(2, "已支付"),

    /** 管理员发货（拓展功能） */
    SHIPPED(3, "已发货"),

    COMPLETED(4, "已完成"),

    CANCELLED(5, "已取消");

    private final int code;

    private final String text;

    /** 根据状态码取文案，未知状态返回「未知」 */
    public static String textOf(Integer code) {
        if (code == null) {
            return "未知";
        }
        return Arrays.stream(values())
                .filter(s -> s.code == code)
                .map(OrderStatus::getText)
                .findFirst()
                .orElse("未知");
    }
}
