package cn.edu.bookstore.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理端分类新增/编辑请求参数。
 *
 * @author 毕设项目组
 */
@Data
public class CategorySaveRequest {

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 20, message = "分类名称过长")
    private String name;

    /** 排序值，越大越靠前；缺省为 0 */
    private Integer sort = 0;

    @Size(max = 100, message = "备注过长")
    private String remark;
}
