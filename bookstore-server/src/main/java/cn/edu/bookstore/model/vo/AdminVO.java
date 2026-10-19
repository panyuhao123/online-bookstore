package cn.edu.bookstore.model.vo;

import cn.edu.bookstore.entity.Admin;
import lombok.Data;

/**
 * 管理员视图对象（不含密码与盐值等敏感字段）。
 *
 * @author 毕设项目组
 */
@Data
public class AdminVO {

    private Long id;

    private String username;

    private String realName;

    private Integer status;

    public static AdminVO from(Admin admin) {
        if (admin == null) {
            return null;
        }
        AdminVO vo = new AdminVO();
        vo.setId(admin.getId());
        vo.setUsername(admin.getUsername());
        vo.setRealName(admin.getRealName());
        vo.setStatus(admin.getStatus());
        return vo;
    }
}
