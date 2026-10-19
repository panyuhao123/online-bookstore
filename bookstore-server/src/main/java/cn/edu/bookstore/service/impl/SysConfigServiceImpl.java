package cn.edu.bookstore.service.impl;

import cn.edu.bookstore.common.BusinessException;
import cn.edu.bookstore.common.ErrorCode;
import cn.edu.bookstore.entity.SysConfig;
import cn.edu.bookstore.mapper.SysConfigMapper;
import cn.edu.bookstore.service.SysConfigService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 系统配置业务实现。
 *
 * <p>读取路径上的容错策略是「坏值不抛异常，只回落默认值并打日志」：
 * 配置值是人手填的字符串，填成 "abc" 只会让解析失败。
 * 如果这里抛异常，一次误填就会让**整个下单流程**不可用 ——
 * 而后台配置页本身还能打开（它只读原始字符串），运营会陷入
 * 「后台看起来一切正常，用户却下不了单」的困境。
 * 因此解析失败一律降级为默认值，并在日志里留下明确线索。
 *
 * @author 毕设项目组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysConfigServiceImpl implements SysConfigService {

    private final SysConfigMapper sysConfigMapper;

    @Override
    public List<SysConfig> list() {
        List<SysConfig> list = sysConfigMapper.selectList(
                new LambdaQueryWrapper<SysConfig>().orderByAsc(SysConfig::getId));
        return list == null ? List.of() : list;
    }

    @Override
    public String getValue(String key, String defaultValue) {
        SysConfig config = findByKey(key);
        if (config == null || config.getConfigValue() == null || config.getConfigValue().isBlank()) {
            return defaultValue;
        }
        return config.getConfigValue().trim();
    }

    @Override
    public boolean getBool(String key, boolean defaultValue) {
        String raw = getValue(key, null);
        if (raw == null) {
            return defaultValue;
        }
        // 只把 "1" 与 "true" 当成立：写成 "0"/"false"/"no" 都算假，
        // 写成无法识别的字符串则回落默认值 —— 避免把 "null"、"off" 之类误判成开启。
        if ("1".equals(raw) || "true".equalsIgnoreCase(raw)) {
            return true;
        }
        if ("0".equals(raw) || "false".equalsIgnoreCase(raw)) {
            return false;
        }
        log.warn("配置项 {} 的值 {} 不是合法布尔值，已回落默认值 {}", key, raw, defaultValue);
        return defaultValue;
    }

    @Override
    public int getInt(String key, int defaultValue) {
        String raw = getValue(key, null);
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            log.warn("配置项 {} 的值 {} 不是合法整数，已回落默认值 {}", key, raw, defaultValue);
            return defaultValue;
        }
    }

    @Override
    public BigDecimal getDecimal(String key, BigDecimal defaultValue) {
        String raw = getValue(key, null);
        if (raw == null) {
            return defaultValue;
        }
        try {
            BigDecimal value = new BigDecimal(raw);
            // 负数金额没有业务含义（「负运费」会变成给用户倒贴钱），一律回落默认值
            if (value.signum() < 0) {
                log.warn("配置项 {} 的值为负数 {}，已回落默认值 {}", key, raw, defaultValue);
                return defaultValue;
            }
            return value;
        } catch (NumberFormatException e) {
            log.warn("配置项 {} 的值 {} 不是合法金额，已回落默认值 {}", key, raw, defaultValue);
            return defaultValue;
        }
    }

    @Override
    public SysConfig update(String key, String value) {
        SysConfig config = findByKey(key);
        if (config == null) {
            // 只允许改已有的键：键名是代码里按名字读取的，凭空新增一行不会影响任何逻辑，
            // 反而会让管理端出现「改了但没生效」的幽灵配置。
            throw new BusinessException(ErrorCode.NOT_FOUND, "配置项不存在：" + key);
        }
        if (!Integer.valueOf(1).equals(config.getEditable())) {
            throw new BusinessException(ErrorCode.BIZ_ERROR, "该项为只读配置，不支持在后台修改");
        }
        config.setConfigValue(value);
        sysConfigMapper.updateById(config);
        log.info("系统配置已更新 key={} value={}", key, value);
        return config;
    }

    /** 按键查询，不存在返回 null */
    private SysConfig findByKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return sysConfigMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key));
    }
}
