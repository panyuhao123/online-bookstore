package cn.edu.bookstore.service;

import cn.edu.bookstore.model.dto.BannerRequest;
import cn.edu.bookstore.model.vo.BannerVO;

import java.util.List;

/**
 * 首页轮播图管理接口（管理端）。
 *
 * <p>所有写操作都必须清理前台缓存 —— 这一条约束集中在本接口的实现类里，
 * 而不是散落在 Controller 上，避免将来新增一个写接口时忘了清理。
 *
 * @author 毕设项目组
 */
public interface AdminBannerService {

    /** 全部轮播图（含已下架），按排序值升序 */
    List<BannerVO> list();

    /** 新增轮播图 */
    BannerVO create(BannerRequest request);

    /** 编辑轮播图 */
    BannerVO update(Long id, BannerRequest request);

    /** 上下架：status=1 上架 / 0 下架 */
    BannerVO updateStatus(Long id, Integer status);

    /** 删除轮播图 */
    void remove(Long id);
}
