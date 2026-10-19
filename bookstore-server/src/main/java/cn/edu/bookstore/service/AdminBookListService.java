package cn.edu.bookstore.service;

import cn.edu.bookstore.model.dto.AdminBookListQuery;
import cn.edu.bookstore.model.vo.AdminBookListDetailVO;
import cn.edu.bookstore.model.vo.AdminBookListVO;
import cn.edu.bookstore.model.vo.PageVO;

/**
 * 书单治理接口（管理端）。
 *
 * <p>核心约定：<b>隐藏优先于删除</b>。违规书单的常规处置是把 status 置 0，
 * 前台立即不可见，但数据仍在——可追溯、可申诉、可恢复；
 * 物理删除只留给「用户自己删掉后又要求彻底清除」这类垃圾数据清理场景。
 *
 * <p>这一层不受前台可见性规则约束：私密书单、已隐藏书单都必须能被管理员查到，
 * 否则「治理」这件事本身就无从开始。
 *
 * @author 毕设项目组
 */
public interface AdminBookListService {

    /** 书单分页（可按名称/简介、治理状态、创建者、公开状态筛选） */
    PageVO<AdminBookListVO> page(AdminBookListQuery query);

    /** 书单详情（含条目，不做任何可见性过滤） */
    AdminBookListDetailVO detail(Long id);

    /** 隐藏 / 恢复：status=0 隐藏，1 恢复；重复设置同一状态不做无谓写入 */
    void updateStatus(Long id, Integer status);

    /** 彻底删除书单（连同条目） */
    void delete(Long id);
}
