package cn.edu.bookstore.controller;

import cn.edu.bookstore.common.Result;
import cn.edu.bookstore.model.vo.BannerVO;
import cn.edu.bookstore.service.BannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 首页轮播图接口（前台，匿名可访问）。
 *
 * <p>轮播图是首页首屏数据，未登录用户也必须能拿到 —— 把它放进登录后才可见的接口里，
 * 等于要求每个访客先注册再看到首页。
 *
 * <p>这里只提供「读」：写操作全部在 {@link AdminBannerController}。
 * 前端拿到的是完整列表（不分页）：轮播图本身有数量上限（8 张），
 * 分页在这里只是徒增一层前端状态，没有任何实际收益。
 *
 * @author 毕设项目组
 */
@RestController
@RequestMapping("/api/banner")
@RequiredArgsConstructor
public class BannerController {

    private final BannerService bannerService;

    /** 前台可见的轮播图（仅上架，按排序值升序） */
    @GetMapping("/list")
    public Result<List<BannerVO>> list() {
        return Result.success(bannerService.visibleList());
    }
}
