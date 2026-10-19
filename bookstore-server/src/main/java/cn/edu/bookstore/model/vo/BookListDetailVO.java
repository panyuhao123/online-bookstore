package cn.edu.bookstore.model.vo;

import cn.edu.bookstore.entity.BookList;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 书单详情视图对象：在书单基本信息之上带出图书明细。
 *
 * <p>用继承而不是组合，是为了让前端「列表接口的字段」与「详情接口的字段」
 * 天然是同一套（详情 = 列表 + books），不必维护两个相似结构。
 *
 * @author 毕设项目组
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BookListDetailVO extends BookListVO {

    /**
     * 书单内图书（按加入顺序）。
     *
     * <p>图书已下架时依然返回，status 字段会告诉前端置灰 ——
     * 直接把下架图书从书单里抹掉会让用户以为「我加的书凭空少了一本」。
     */
    private List<BookVO> books;

    public static BookListDetailVO of(BookList list, List<BookVO> books) {
        BookListDetailVO vo = new BookListDetailVO();
        BookListVO base = BookListVO.from(list);
        vo.setId(base.getId());
        vo.setName(base.getName());
        vo.setDescription(base.getDescription());
        vo.setCoverUrl(base.getCoverUrl());
        vo.setIsPublic(base.getIsPublic());
        vo.setBookCount(base.getBookCount());
        vo.setOwnerId(base.getOwnerId());
        vo.setOwnerNickname(base.getOwnerNickname());
        vo.setMine(base.getMine());
        vo.setCreateTime(base.getCreateTime());
        vo.setUpdateTime(base.getUpdateTime());
        vo.setBooks(books == null ? List.of() : books);
        return vo;
    }
}
