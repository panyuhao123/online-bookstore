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
 * <p>密码存储：库里是 BCrypt 密文（自带盐）；历史数据可能仍是 MD5(明文 + salt)，
 * 由 {@code PasswordUtil} 在校验时自动识别，并在登录成功后重写为 BCrypt。
 * 明文与盐均不落日志、不返回前端。
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

    /** 历史遗留盐值：BCrypt 数据下为空串（盐已编码进密文），仅为兼容旧 MD5 记录保留 */
    private String salt;

    /** 密码密文：BCrypt（新数据）或 MD5(明文 + salt)（待升级的历史数据） */
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
