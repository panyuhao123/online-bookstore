package cn.edu.bookstore.controller;

import cn.edu.bookstore.common.Result;
import cn.edu.bookstore.common.log.OpLog;
import cn.edu.bookstore.model.dto.BannerRequest;
import cn.edu.bookstore.model.vo.BannerVO;
import cn.edu.bookstore.service.AdminBannerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端首页轮播图接口。
 *
 * <p>全部位于 /api/admin/**，由 Spring Security 强制校验管理员身份，
 * 并额外用 @PreAuthorize 做方法级双保险。
 *
 * <p>写操作全部标注 @OpLog：轮播图是直接面向所有访客的展示位，
 * 一旦出现「首页图片变成了不该有的内容」，操作日志是唯一能追到人的地方。
 *
 * @author 毕设项目组
 */
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/admin/banner")
@RequiredArgsConstructor
public class AdminBannerController {

    private final AdminBannerService adminBannerService;

    /** 全部轮播图（含已下架），按排序值升序 */
    @GetMapping("/list")
    public Result<List<BannerVO>> list() {
        return Result.success(adminBannerService.list());
    }

    /** 新增轮播图（数量超上限时返回 422） */
    @OpLog(module = "轮播图管理", action = "新增轮播图")
    @PostMapping
    public Result<BannerVO> create(@Valid @RequestBody BannerRequest request) {
        return Result.success(adminBannerService.create(request));
    }

    /** 编辑轮播图 */
    @OpLog(module = "轮播图管理", action = "编辑轮播图")
    @PutMapping("/{id}")
    public Result<BannerVO> update(@PathVariable Long id, @Valid @RequestBody BannerRequest request) {
        return Result.success(adminBannerService.update(id, request));
    }

    /**
     * 上架 / 下架：PUT /api/admin/banner/3/status?status=1
     *
     * <p>单列一个接口而不是让前端走「整条编辑」：下架是运营最高频的动作
     * （活动结束、图片有误），只需要一个布尔切换，让前端把整条记录回传一次
     * 反而容易把别的字段改坏。
     */
    @OpLog(module = "轮播图管理", action = "变更轮播图状态")
    @PutMapping("/{id}/status")
    public Result<BannerVO> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        return Result.success(adminBannerService.updateStatus(id, status));
    }

    /** 删除轮播图 */
    @OpLog(module = "轮播图管理", action = "删除轮播图")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminBannerService.remove(id);
        return Result.success();
    }
}
