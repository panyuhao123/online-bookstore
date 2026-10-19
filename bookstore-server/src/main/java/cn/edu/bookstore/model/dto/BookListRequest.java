package cn.edu.bookstore.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 书单新建 / 编辑请求参数。
 *
 * @author 毕设项目组
 */
@Data
public class BookListRequest {

    @NotBlank(message = "书单名称不能为空")
    @Size(max = 50, message = "书单名称不能超过 50 个字")
    private String name;

    @Size(max = 200, message = "书单简介不能超过 200 个字")
    private String description;

    /**
     * 封面图地址（可选）。
     *
     * <p>限制为站内 /files 路径或 http(s) 外链，拒绝 javascript: 这类会被浏览器当作脚本执行的协议 ——
     * 封面地址是用户可控输入，将来若被渲染成 &lt;a href&gt; 就成了存储型 XSS 的入口。
     */
    @Size(max = 500, message = "封面地址过长")
    @Pattern(regexp = "^(|/files/.*|https?://.*)$", message = "封面地址格式不正确")
    private String coverUrl;

    /** 是否公开：1-公开 0-仅自己可见，默认公开 */
    private Integer isPublic = 1;
}
