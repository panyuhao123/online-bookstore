package cn.edu.bookstore.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员实体，对应表 admin（Web 管理后台账号，与 APP 用户分开）。
 *
 * @author 毕设项目组
 */
@Data
@TableName("admin")
public class Admin {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录账号 */
    private String username;

    /** 密码密文：BCrypt（新数据）或 MD5(明文 + salt)（待升级的历史数据） */
    private String password;

    /** 历史遗留盐值：BCrypt 数据下为空串（盐已编码进密文），仅为兼容旧 MD5 记录保留 */
    private String salt;

    private String realName;

    /** 状态：1-正常 0-禁用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
