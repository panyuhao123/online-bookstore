package cn.edu.bookstore.service;

import cn.edu.bookstore.model.dto.AdminUserQuery;
import cn.edu.bookstore.model.vo.AdminUserSummaryVO;
import cn.edu.bookstore.model.vo.AdminUserVO;
import cn.edu.bookstore.model.vo.PageVO;

/**
 * 管理端用户业务接口。
 *
 * <p>后台只做「查看 + 启用/禁用」，不提供管理员新建或改资料的功能：
 * APP 用户由注册产生，后台改资料会绕过用户本人的意愿。
 *
 * @author 毕设项目组
 */
public interface AdminUserService {

    /** 用户分页：支持手机号 / 昵称模糊搜索与状态筛选 */
    PageVO<AdminUserVO> page(AdminUserQuery query);

    /** 用户汇总指标（总数 / 正常 / 禁用 / 本月新增） */
    AdminUserSummaryVO summary();

    /** 启用 / 禁用用户：status=1 启用，status=0 禁用 */
    void updateStatus(Long id, Integer status);
}
