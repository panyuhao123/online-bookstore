package cn.edu.bookstore.model.vo;

import cn.edu.bookstore.entity.BookList;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理端书单展示对象。
 *
 * <p>比前台多带两样东西：
 * <ol>
 *     <li><b>手机号</b>。后台处理违规书单必须能定位到人，而昵称可以随便改、也可能为空，
 *         手机号是唯一能对上账号的标识。这里下发的是<b>脱敏手机号</b>（138****8000）——
 *         后台确实要能认人，但没有必要把完整号码铺在列表页上。</li>
 *     <li><b>status 与 isPublic 同时保留</b>。两者语义正交（平台是否隐藏 / 用户是否愿意公开），
 *         后台必须分别看到，才能判断「这是用户自己设为私密，还是平台处置过」。</li>
 * </ol>
 *
 * @author 毕设项目组
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminBookListVO extends BookListVO {

    /** 创建者脱敏手机号（138****8000），用于在后台定位到具体账号 */
    private String ownerPhone;

    public static AdminBookListVO from(BookList list) {
        if (list == null) {
            return null;
        }
        BookListVO base = BookListVO.from(list);
        AdminBookListVO vo = new AdminBookListVO();
        vo.setId(base.getId());
        vo.setName(base.getName());
        vo.setDescription(base.getDescription());
        vo.setCoverUrl(base.getCoverUrl());
        vo.setIsPublic(base.getIsPublic());
        vo.setBookCount(base.getBookCount());
        vo.setStatus(base.getStatus());
        vo.setOwnerId(base.getOwnerId());
        vo.setOwnerNickname(base.getOwnerNickname());
        vo.setMine(base.getMine());
        vo.setCreateTime(base.getCreateTime());
        vo.setUpdateTime(base.getUpdateTime());
        return vo;
    }
}
