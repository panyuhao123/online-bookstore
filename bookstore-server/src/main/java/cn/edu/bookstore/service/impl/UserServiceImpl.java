package cn.edu.bookstore.service.impl;

import cn.edu.bookstore.common.BusinessException;
import cn.edu.bookstore.common.ErrorCode;
import cn.edu.bookstore.common.util.JwtUtil;
import cn.edu.bookstore.common.util.PasswordUtil;
import cn.edu.bookstore.config.AppProperties;
import cn.edu.bookstore.entity.User;
import cn.edu.bookstore.mapper.UserMapper;
import cn.edu.bookstore.model.dto.LoginRequest;
import cn.edu.bookstore.model.dto.RegisterRequest;
import cn.edu.bookstore.model.vo.LoginVO;
import cn.edu.bookstore.model.vo.UserVO;
import cn.edu.bookstore.service.CaptchaService;
import cn.edu.bookstore.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户业务实现。
 *
 * <p>安全约定：
 * <ul>
 *     <li>登录失败时统一返回「手机号或密码错误」，不区分账号是否存在，防止账号枚举；</li>
 *     <li>日志只记录手机号（必要时），绝不记录明文密码与盐值。</li>
 * </ul>
 *
 * @author 毕设项目组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    /** APP 用户角色标识，写入 JWT */
    private static final String ROLE_USER = "user";

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final AppProperties appProperties;
    private final CaptchaService captchaService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO register(RegisterRequest request) {
        // 1. 手机号唯一性校验
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, request.getPhone()));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该手机号已注册，请直接登录");
        }

        // 2. 生成随机盐并加密，绝不存明文
        String salt = PasswordUtil.generateSalt();
        User user = new User();
        user.setPhone(request.getPhone());
        user.setSalt(salt);
        user.setPassword(PasswordUtil.encrypt(request.getPassword(), salt));
        user.setNickname(resolveNickname(request));
        user.setStatus(1);

        userMapper.insert(user);
        log.info("用户注册成功 userId={} phone={}", user.getId(), user.getPhone());
        return UserVO.from(user);
    }

    @Override
    public LoginVO login(LoginRequest request) {
        // 1. 验证码校验（放在最前面，省得无效验证码请求打数据库）
        captchaService.verify(request.getCaptchaKey(), request.getCaptchaCode());

        // 2. 账号密码校验
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, request.getPhone()));

        // 账号不存在、密码错误、账号被禁用统一文案，避免泄露账号是否存在
        if (user == null || !PasswordUtil.matches(request.getPassword(), user.getSalt(), user.getPassword())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "手机号或密码错误");
        }
        if (Integer.valueOf(0).equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被禁用，请联系管理员");
        }

        String token = jwtUtil.generateToken(user.getId(), ROLE_USER);
        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setExpireHours(appProperties.getJwt().getExpireHours());
        vo.setUser(UserVO.from(user));
        log.info("用户登录成功 userId={}", user.getId());
        return vo;
    }

    @Override
    public UserVO getCurrentUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return UserVO.from(user);
    }

    /** 昵称缺省值：用户 + 手机号后 4 位 */
    private String resolveNickname(RegisterRequest request) {
        if (request.getNickname() != null && !request.getNickname().isBlank()) {
            return request.getNickname();
        }
        String phone = request.getPhone();
        return "用户" + phone.substring(phone.length() - 4);
    }
}
