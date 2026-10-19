package cn.edu.bookstore.model.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 浏览历史合并请求参数。
 *
 * <p>用于「未登录时逛了几本书 → 登录后把刚才的浏览记录带上来」这一场景：
 * 前端把本地 localStorage 里的图书 ID 按浏览时间倒序提交，服务端逐条落库。
 *
 * <p>上限 100 与单个用户的留存上限保持一致：超出部分本来就会被裁剪掉，
 * 提前拒绝可以省掉一次无意义的写入。
 *
 * @author 毕设项目组
 */
@Data
public class HistoryMergeRequest {

    /** 本地待合并的图书 ID，按浏览时间倒序（新的在前） */
    @NotEmpty(message = "待合并的浏览记录不能为空")
    @Size(max = 100, message = "一次最多合并 100 条浏览记录")
    private List<Long> bookIds;
}
