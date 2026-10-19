package cn.edu.bookstore.model.vo;

import cn.edu.bookstore.entity.BookList;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 管理端书单详情：在 {@link AdminBookListVO} 之上带出条目明细。
 *
 * <p>沿用「详情 = 列表 + 明细」的继承写法（与前台 {@link BookListDetailVO} 保持一致），
 * 好处是前端两个接口的字段名天然是同一套，不必维护两份相似结构。
 *
 * <p>后台看条目时**不做任何过滤**：已下架的图书、已被隐藏的条目都要照常返回，
 * 否则管理员根本看不到「这个人到底收录了什么」，也就无从判断该不该处置。
 *
 * @author 毕设项目组
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminBookListDetailVO extends AdminBookListVO {

    /** 书单内图书（按书单内排序） */
    private List<BookVO> books;

    public static AdminBookListDetailVO of(BookList list, List<BookVO> books) {
        AdminBookListVO base = AdminBookListVO.from(list);
        AdminBookListDetailVO vo = new AdminBookListDetailVO();
        if (base != null) {
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
            vo.setOwnerPhone(base.getOwnerPhone());
        }
        vo.setBooks(books == null ? List.of() : books);
        return vo;
    }
}
