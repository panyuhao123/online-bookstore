package cn.edu.bookstore.service;

import cn.edu.bookstore.entity.SysConfig;

import java.math.BigDecimal;
import java.util.List;

/**
 * 系统配置业务接口。
 *
 * <p><b>刻意不做缓存</b>。sys_config 是个位数量级的小表，且只在
 * 「下单」「结算预览」「打开管理端配置页」这几条路径上被读；
 * InnoDB 的缓冲池会把它整表留在内存里，多查一次的代价可以忽略。
 * 反过来，一旦加了缓存就要处理「管理端改了配置、前台却还读旧值」的失效问题 ——
 * 这类 bug 在演示现场极难解释（明明改成了满 59 包邮，前台还写着 99）。
 * 用一次主键/唯一键查询换掉一整类一致性问题，这笔交易是划算的。
 *
 * @author 毕设项目组
 */
public interface SysConfigService {

    /** 全部配置项（管理端配置页展示用），按主键升序 */
    List<SysConfig> list();

    /** 按键取值；键不存在或值为空时返回 defaultValue */
    String getValue(String key, String defaultValue);

    /** 按键取布尔值；值只认 "1"/"true"（忽略大小写）为真，其余一律返回 defaultValue */
    boolean getBool(String key, boolean defaultValue);

    /** 按键取整数；解析失败（被人手填成非数字）时返回 defaultValue */
    int getInt(String key, int defaultValue);

    /** 按键取金额；解析失败或为负数时返回 defaultValue */
    BigDecimal getDecimal(String key, BigDecimal defaultValue);

    /**
     * 修改配置值（仅允许改值，不允许改键，也不允许新增）。
     *
     * @throws cn.edu.bookstore.common.BusinessException 键不存在（404）或该键标记为只读（422）
     */
    SysConfig update(String key, String value);
}
