package cn.edu.bookstore.service;

import cn.edu.bookstore.model.vo.BannerVO;

import java.util.List;

/**
 * 首页轮播图业务接口（前台）。
 *
 * @author 毕设项目组
 */
public interface BannerService {

    /**
     * 前台可见的轮播图（仅上架，按排序值升序）。
     *
     * <p>这是首页首屏必经的读接口，因此结果走 Redis 缓存；
     * 管理端任何一次增删改都会清理该缓存（见 AdminBannerService）。
     * 缓存价值在这里比 sys_config 高得多：首页访问量最大，而 Banner 几天才改一次，
     * 命中率极高，且失效时机完全由管理端写入操作决定 —— 不存在「谁改了数据却没清缓存」的隐患。
     */
    List<BannerVO> visibleList();
}
