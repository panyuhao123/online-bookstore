package cn.edu.bookstore.model.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 购物车合并结果。
 *
 * <p>为什么不是简单返回成功/失败：本地购物车里的商品可能已经下架、库存被抢光，
 * 这些都是正常业务情况而不是错误。把「跳过了哪些、为什么跳过」返回给 APP，
 * 用户就能看到「2 件商品已合并，1 件因库存不足未合并」这种明确反馈，
 * 而不是合并后莫名其妙少了一件。
 *
 * @author 毕设项目组
 */
@Data
public class CartMergeResultVO {

    /** 成功合并的商品种类数 */
    private int mergedCount;

    /** 合并后购物车的商品总件数 */
    private int totalQuantity;

    /** 被跳过的商品及原因（已下架 / 库存不足） */
    private List<SkippedItem> skipped = new ArrayList<>();

    /** 追加一条跳过记录 */
    public void addSkipped(Long bookId, String title, String reason) {
        SkippedItem item = new SkippedItem();
        item.setBookId(bookId);
        item.setTitle(title);
        item.setReason(reason);
        skipped.add(item);
    }

    @Data
    public static class SkippedItem {
        private Long bookId;
        private String title;
        private String reason;
    }
}
