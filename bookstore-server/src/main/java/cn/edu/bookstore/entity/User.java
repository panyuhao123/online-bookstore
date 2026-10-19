package cn.edu.bookstore.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * APP 用户实体，对应表 user。
 *
 * <p>密码存储：库中存在的是 MD5(明文 + salt)，明文与盐均不落日志、不返回前端。
 *
 * @author 毕设项目组
 */
@Data
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 手机号，登录账号 */
    private String phone;

    /** 密码随机盐值 */
    private String salt;

    /** 密码密文 = MD5(明文 + salt) */
    private String password;

    private String nickname;

    private String avatarUrl;

    /** 状态：1-正常 0-禁用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
