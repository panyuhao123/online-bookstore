package cn.edu.bookstore.model.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 管理端图书新增/编辑请求参数。
 *
 * @author 毕设项目组
 */
@Data
public class BookSaveRequest {

    @NotNull(message = "请选择图书分类")
    private Long categoryId;

    @NotBlank(message = "书名不能为空")
    @Size(max = 100, message = "书名过长")
    private String title;

    @Size(max = 50, message = "作者名过长")
    private String author;

    @Size(max = 50, message = "出版社名过长")
    private String publisher;

    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0.01", message = "价格必须大于 0")
    private BigDecimal price;

    @NotNull(message = "库存不能为空")
    @Min(value = 0, message = "库存不能为负数")
    private Integer stock;

    @Size(max = 255, message = "封面地址过长")
    private String coverUrl;

    @Size(max = 1000, message = "图书简介过长")
    private String description;

    /** 状态：1-上架 0-下架，缺省按上架处理 */
    private Integer status = 1;
}
