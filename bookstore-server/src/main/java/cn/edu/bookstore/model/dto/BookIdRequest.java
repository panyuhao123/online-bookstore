package cn.edu.bookstore.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 只携带一个图书 ID 的请求参数。
 *
 * <p>浏览历史的「记录一次浏览」与到货提醒的「订阅」入参结构完全相同，
 * 都是「对某一本书做一次动作」。共用一个 DTO 而不是各写一份，
 * 是为了让两处校验口径天然一致（都要求 bookId 非空），
 * 避免将来有人只给其中一个补了校验、另一个漏掉。
 *
 * <p>之所以不直接写成 {@code @RequestBody Long bookId}：裸值请求体在参数校验、
 * 接口文档生成、前端联调三处都不友好（拿不到字段名，出错时只能提示「参数不能为空」）。 
 *
 * @author 毕设项目组
 */
@Data
public class BookIdRequest {

    @NotNull(message = "图书 ID 不能为空")
    private Long bookId;
}
