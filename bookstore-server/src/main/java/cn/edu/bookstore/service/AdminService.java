package cn.edu.bookstore.service;

import cn.edu.bookstore.model.dto.AdminLoginRequest;
import cn.edu.bookstore.model.vo.AdminLoginVO;
import cn.edu.bookstore.model.vo.AdminVO;

/**
 * 管理员业务（Web 管理后台登录）。
 *
 * @author 毕设项目组
 */
public interface AdminService {

    /** 管理员登录，返回令牌 */
    AdminLoginVO login(AdminLoginRequest request);

    /** 根据令牌中的 adminId 查询管理员信息 */
    AdminVO getInfo(Long adminId);
}
