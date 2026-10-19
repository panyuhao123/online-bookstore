package cn.edu.bookstore.service;

import cn.edu.bookstore.model.dto.LoginRequest;
import cn.edu.bookstore.model.dto.RegisterRequest;
import cn.edu.bookstore.model.vo.LoginVO;
import cn.edu.bookstore.model.vo.UserVO;

/**
 * 用户业务接口。
 *
 * <p>服务层不依赖 HttpServletRequest / Response，便于单元测试与复用。
 *
 * @author 毕设项目组
 */
public interface UserService {

    /**
     * 用户注册。
     *
     * @param request 注册参数
     * @return 注册成功的用户信息
     */
    UserVO register(RegisterRequest request);

    /**
     * 用户登录。
     *
     * @param request 登录参数
     * @return 令牌与用户信息
     */
    LoginVO login(LoginRequest request);

    /**
     * 获取当前登录用户信息。
     *
     * @param userId 当前登录用户 ID（由拦截器解析令牌后传入）
     * @return 用户信息
     */
    UserVO getCurrentUser(Long userId);
}
