package cn.edu.bookstore.controller;

import cn.edu.bookstore.common.Result;
import cn.edu.bookstore.common.log.OpLog;
import cn.edu.bookstore.entity.SysConfig;
import cn.edu.bookstore.model.dto.SysConfigUpdateRequest;
import cn.edu.bookstore.service.SysConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端系统配置接口（当前主要是运费规则）。
 *
 * <p>只提供「列出」与「改值」两个动作，<b>没有新增与删除</b>：
 * 配置项的意义是「代码里有一处固定逻辑在读它」。
 * 若允许后台随意新增一个键，它永远不会被任何代码读到，只会成为一条
 * 看着有用、实则无人消费的数据；删除则更危险 —— 键没了，读配置的代码
 * 会静默回落到默认值，运营却以为自己改过的东西还在生效。
 * 因此新增配置属于发版动作，改值才是运营动作。
 *
 * @author 毕设项目组
 */
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/admin/config")
@RequiredArgsConstructor
public class AdminSysConfigController {

    private final SysConfigService sysConfigService;

    /** 全部配置项（含说明与是否可改） */
    @GetMapping("/list")
    public Result<List<SysConfig>> list() {
        return Result.success(sysConfigService.list());
    }

    /**
     * 修改配置值：PUT /api/admin/config/freight.free-threshold
     *
     * <p>键不存在返回 404、键标记为只读返回 422 —— 都刻意不做「键不存在就顺手创建」
     * 的兜底：那会把一个拼写错误（freight.free-threshold 打成 freight.freeThreadhold）
     * 悄悄变成一条永久无效的配置，而运营看到的是「保存成功」。
     *
     * <p>标注 @OpLog：运费门槛直接影响用户实付金额，改动必须留痕。
     */
    @OpLog(module = "系统配置", action = "修改配置")
    @PutMapping("/{key}")
    public Result<SysConfig> update(@PathVariable String key,
                                    @Valid @RequestBody SysConfigUpdateRequest request) {
        return Result.success(sysConfigService.update(key, request.getConfigValue()));
    }
}
