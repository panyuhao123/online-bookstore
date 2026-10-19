package cn.edu.bookstore.service.impl;

import cn.edu.bookstore.common.BusinessException;
import cn.edu.bookstore.common.ErrorCode;
import cn.edu.bookstore.common.security.LoginAttemptGuard;
import cn.edu.bookstore.common.security.TokenRevocationService;
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
 *     <li>密码以 BCrypt 密文存储；历史 MD5(明文 + 盐) 数据在登录成功后透明升级（见 {@link #upgradeHashIfNeeded}）；</li>
 *     <li>连续失败达阈值即临时锁定该账号（见 {@link LoginAttemptGuard}），
 *         因为图形验证码只能挡无脑脚本，挡不住「认真填对验证码再慢速试密码」；</li>
 *     <li>退出登录会在服务端吊销当前令牌（见 {@link TokenRevocationService}），
 *         而不是只靠客户端删掉本地令牌 —— 后者挡不住被复制走的令牌继续使用；</li>
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

    /** 登录失败计数的账号域，与管理员（admin）隔离，避免同名账号互相影响 */
    private static final String SCOPE_USER = "user";

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final AppProperties appProperties;
    private final CaptchaService captchaService;
    private final LoginAttemptGuard loginAttemptGuard;
    private final TokenRevocationService tokenRevocationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO register(RegisterRequest request) {
        // 1. 手机号唯一性校验
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, request.getPhone()));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该手机号已注册，请直接登录");
        }

        // 2. 以 BCrypt 生成密文，绝不存明文（BCrypt 自带盐，salt 列写空串占位）
        User user = new User();
        user.setPhone(request.getPhone());
        user.setSalt(PasswordUtil.NO_LEGACY_SALT);
        user.setPassword(PasswordUtil.encode(request.getPassword()));
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

        // 2. 锁定检查：放在验证码之后、查库之前。先过验证码，是为了避免「未过验证码就能探测账号是否被锁」；
        //    放在查库之前，是为了让锁定期间的请求连数据库都不碰。
        loginAttemptGuard.checkNotLocked(SCOPE_USER, request.getPhone());

        // 3. 账号密码校验
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, request.getPhone()));

        // 账号不存在、密码错误统一文案，避免泄露账号是否存在；
        // 不存在的账号同样计入失败次数，否则「不存在的账号永不锁定」本身就是枚举信号。
        if (user == null || !PasswordUtil.matches(request.getPassword(), user.getSalt(), user.getPassword())) {
            loginAttemptGuard.recordFailure(SCOPE_USER, request.getPhone());
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "手机号或密码错误");
        }

        // 密码正确即清零失败计数：避免「上周忘了密码试错 3 次 + 今天手滑 2 次」被算成连续失败
        loginAttemptGuard.reset(SCOPE_USER, request.getPhone());

        if (Integer.valueOf(0).equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被禁用，请联系管理员");
        }

        // 4. 哈希升级：老数据（MD5 + 盐）此刻手握明文，正是重写为 BCrypt 的最好时机
        upgradeHashIfNeeded(user, request.getPassword());

        String token = jwtUtil.generateToken(user.getId(), ROLE_USER);
        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setExpireHours(appProperties.getJwt().getExpireHours());
        vo.setUser(UserVO.from(user));
        log.info("用户登录成功 userId={}", user.getId());
        return vo;
    }

    @Override
    public void logout(String token) {
        // 把当前令牌的编号写入吊销黑名单：此后该令牌在认证过滤器处即被拒绝，
        // 「退出登录」才真正在服务端生效，而不是只依赖客户端把令牌删掉。
        tokenRevocationService.revokeToken(token, ROLE_USER);
    }

    @Override
    public UserVO getCurrentUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return UserVO.from(user);
    }

    /**
     * 历史哈希透明升级：仅当库中密文还不是 BCrypt 时改写当前账号那一行。
     *
     * <p>放在登录成功之后、签发令牌之前，理由是此刻已确认密码正确且明文在手，
     * 无需用户改密即可完成迁移。升级失败只记日志不抛出——用户密码是对的，
     * 不该因为一次写入失败而登录不了；本次不写成功，下次登录会再试。
     *
     * <p>这里刻意不把登录方法整体纳入事务：单条 UPDATE 自身即为一个原子操作，
     * 而升级失败必须被吞掉，放进同一事务反而会牵连令牌签发等只读流程。
     */
    private void upgradeHashIfNeeded(User user, String rawPassword) {
        if (!PasswordUtil.needsUpgrade(user.getPassword())) {
            return;
        }
        try {
            // 只 set 需要变更的字段，其余字段保持 null，MyBatis-Plus 不会覆盖原值
            User patch = new User();
            patch.setId(user.getId());
            patch.setPassword(PasswordUtil.encode(rawPassword));
            patch.setSalt(PasswordUtil.NO_LEGACY_SALT);
            userMapper.updateById(patch);
            log.info("已将 userId={} 的密码哈希从历史算法升级为 BCrypt", user.getId());
        } catch (Exception e) {
            log.warn("密码哈希升级失败，本次登录不受影响，userId={}，原因：{}", user.getId(), e.getMessage());
        }
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
