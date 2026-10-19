package cn.edu.bookstore.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 首页轮播图新增 / 编辑请求参数。
 *
 * @author 毕设项目组
 */
@Data
public class BannerRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 50, message = "标题不能超过 50 个字")
    private String title;

    @Size(max = 100, message = "副标题不能超过 100 个字")
    private String subTitle;

    /** 背景图地址；留空时前台回退渲染 bgColor */
    @Size(max = 500, message = "图片地址过长")
    @Pattern(regexp = "^(|/files/.*|https?://.*)$", message = "图片地址格式不正确")
    private String imageUrl;

    /**
     * 无图时的背景色或渐变。
     *
     * <p>用正则限制成「颜色值 或 linear-gradient(...)」两种形态：
     * 这个字段会直接写进前端的 style 属性，若允许任意字符串，
     * 攻击者可以塞入 {@code background:url(...)} 之类的构造把外部资源引入页面。
     */
    @Size(max = 120, message = "背景样式过长")
    @Pattern(regexp = "^(|#[0-9a-fA-F]{3,8}|(linear|radial)-gradient\\([^;{}\"']*\\))$",
            message = "背景样式只支持颜色值或渐变")
    private String bgColor;

    /** 点击跳转路径（站内路径如 /books?sort=sales，或 http(s) 外链） */
    @Size(max = 200, message = "跳转地址过长")
    @Pattern(regexp = "^(|/[^\\s]*|https?://.*)$", message = "跳转地址必须是站内路径或 http(s) 链接")
    private String linkUrl;

    /** 排序值，越小越靠前；为空按 0 处理 */
    private Integer sort;

    /** 状态：1-上架 0-下架；为空按上架处理 */
    private Integer status;
}
