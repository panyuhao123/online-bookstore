package cn.edu.bookstore.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 未登录购物车合并请求。
 *
 * <p>场景：用户在未登录状态下把商品加入本地购物车（存在手机 storage 里），
 * 登录后由 APP 把这份「离线购物车」提交上来，与服务端购物车合并。
 *
 * <p>约束：单次最多 50 种商品（正常用户不会超过，也避免被当成批量写入接口滥用）；
 * 单种数量 1~99（与购物车规则保持一致）。
 *
 * @author 毕设项目组
 */
@Data
public class CartMergeRequest {

    @NotEmpty(message = "本地购物车为空，无需合并")
    @Size(max = 50, message = "单次最多同步 50 种商品")
    @Valid
    private List<Item> items;

    @Data
    public static class Item {

        @NotNull(message = "图书 ID 不能为空")
        private Long bookId;

        @NotNull(message = "数量不能为空")
        @Min(value = 1, message = "数量至少为 1")
        @Max(value = 99, message = "单个商品最多购买 99 件")
        private Integer quantity;
    }
}
