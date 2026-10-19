package cn.edu.bookstore.model.vo;

import cn.edu.bookstore.entity.User;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 管理端用户视图对象。
 *
 * <p>在 {@link UserVO} 基础上补充：
 * <ul>
 *     <li>status —— 后台要区分正常/禁用并支持启停操作；</li>
 *     <li>orderCount / totalAmount —— 后台用户列表需要体现用户价值，
 *         这两项由 orders 表聚合而来，不在 user 表中冗余存储。</li>
 * </ul>
 *
 * <p>安全：不含 password 与 salt。
 *
 * @author 毕设项目组
 */
@Data
public class AdminUserVO {

    private Long id;

    private String phone;

    private String nickname;

    private String avatarUrl;

    /** 状态：1-正常 0-禁用 */
    private Integer status;

    /** 累计订单数（不含已取消） */
    private Long orderCount;

    /** 累计消费金额（不含已取消与待支付） */
    private BigDecimal totalAmount;

    private LocalDateTime createTime;

    public static AdminUserVO from(User user) {
        if (user == null) {
            return null;
        }
        AdminUserVO vo = new AdminUserVO();
        vo.setId(user.getId());
        vo.setPhone(user.getPhone());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }
}
