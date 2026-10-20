package cn.edu.bookstore.model.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量发货入参。
 *
 * <p>上限 100 单：批量发货本质是「运营勾一页」，不是「全店重跑一次」。
 * 放开到无上限会让一次请求长时间占用连接池，且部分成功时操作员很难判断到底发了哪些。
 *
 * @author 毕设项目组
 */
@Data
public class BatchShipRequest {

    /** 待发货的订单 ID 列表 */
    @NotEmpty(message = "请至少选择一笔订单")
    @Size(max = 100, message = "单次批量发货不能超过 100 笔")
    private List<Long> ids;
}
