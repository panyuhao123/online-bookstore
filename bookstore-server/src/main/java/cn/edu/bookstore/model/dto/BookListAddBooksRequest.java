package cn.edu.bookstore.model.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 书单批量加书请求参数。
 *
 * <p>一次最多 50 本：书单是人工整理的内容，一次加几百本几乎没有真实场景，
 * 但放开上限会让「一次请求里塞满 ID」成为放大攻击（每个 ID 都要查一次图书表）。
 *
 * @author 毕设项目组
 */
@Data
public class BookListAddBooksRequest {

    @NotEmpty(message = "请至少选择一本图书")
    @Size(max = 50, message = "一次最多加入 50 本图书")
    private List<Long> bookIds;
}
