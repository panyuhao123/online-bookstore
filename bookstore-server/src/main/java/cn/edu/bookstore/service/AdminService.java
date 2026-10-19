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

    /**
     * 管理员退出登录：在服务端吊销当前令牌。
     *
     * <p>管理端令牌能操作商品、订单与用户，比 APP 端更敏感，
     * 因此退出登录必须真正作废令牌，而不能只依赖客户端把它删掉。
     * 同样采用「尽力而为 + 幂等」语义，令牌缺失或已过期时静默返回。
     *
     * @param token Authorization 头中的令牌原文，可为 null
     */
    void logout(String token);

    /** 根据令牌中的 adminId 查询管理员信息 */
    AdminVO getInfo(Long adminId);
}
