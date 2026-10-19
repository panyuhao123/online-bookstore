package cn.edu.bookstore.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 收货地址实体，对应表 address。
 *
 * @author 毕设项目组
 */
@Data
@TableName("address")
public class Address {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 收件人 */
    private String receiver;

    private String phone;

    private String province;

    private String city;

    private String district;

    /** 省份 adcode 编码（高德行政区划码） */
    private String provinceAdcode;

    /** 城市 adcode 编码 */
    private String cityAdcode;

    /** 区县 adcode 编码 */
    private String districtAdcode;

    /** 详细地址 */
    private String detail;

    /** 是否默认：1-默认 0-普通 */
    private Integer isDefault;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
