package cn.edu.bookstore.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统配置实体，对应表 sys_config（极简 key-value 结构）。
 *
 * <p>为什么需要一张配置表，而不是把运费规则继续写在 application.yml：
 * yml 里的值只有「重启服务」才能改。而「满多少包邮」是运营会反复调整的经营参数
 * （大促期间改成满 59 包邮是常事），把它固定在启动配置里，等于每次调价都要发版重启。
 *
 * <p>边界很明确：<b>只放运行期可热改的业务参数</b>。
 * JWT 密钥、数据库口令、支付渠道这类「决定进程如何启动」的配置仍然留在 yml 里 ——
 * 它们若能被运行时改写，会变成一个绕过部署流程改配置的后门。
 * 需要展示给管理端看时，用 {@code editable=0} 标记为只读行。
 *
 * <p>值统一按字符串存，读取方自行转换。这样加一个布尔开关、一个金额、一个字符串
 * 都不需要改表结构，代价是类型转换必须在读取处显式处理（见 FreightService 的容错逻辑）。
 *
 * @author 毕设项目组
 */
@Data
@TableName("sys_config")
public class SysConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 配置键，如 freight.free-threshold */
    private String configKey;

    /** 配置值（统一字符串存储） */
    private String configValue;

    /** 配置说明，管理端展示用 */
    private String description;

    /** 是否允许在管理端修改：1-可改 0-只读展示 */
    private Integer editable;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
