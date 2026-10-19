package cn.edu.bookstore.model.dto;

import lombok.Data;

import java.util.List;

/**
 * 结算金额预览请求参数。
 *
 * <p>语义与 {@link OrderCreateRequest} 的「选谁下单」部分完全一致，只是预览不会落库：
 * {@code cartIds} 为空表示结算购物车中所有已勾选的条目，传了则只结算这些条目。
 *
 * <p>刻意<b>不复用</b> OrderCreateRequest：后者带 {@code @NotNull addressId}，
 * 在「还没选地址就想先看看要付多少钱」的交互下会直接被校验拦掉（422），
 * 而那恰恰是结算页最常见的首屏状态。两个 DTO 字段重合但约束不同，
 * 硬合并只会让其中一个场景的校验语义变模糊。
 *
 * @author 毕设项目组
 */
@Data
public class OrderSettleRequest {

    /** 参与结算的购物车条目 ID，可为空 */
    private List<Long> cartIds;
}
