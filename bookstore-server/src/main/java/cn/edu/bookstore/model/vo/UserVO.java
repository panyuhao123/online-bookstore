package cn.edu.bookstore.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户对外视图对象。
 *
 * <p>关键：不包含 password 与 salt，避免敏感字段随接口泄漏。
 *
 * @author 毕设项目组
 */
@Data
public class UserVO {

    private Long id;

    private String phone;

    private String nickname;

    private String avatarUrl;

    private LocalDateTime createTime;

    /** 由实体转换，过滤敏感字段 */
    public static UserVO from(cn.edu.bookstore.entity.User user) {
        if (user == null) {
            return null;
        }
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setPhone(user.getPhone());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }
}
