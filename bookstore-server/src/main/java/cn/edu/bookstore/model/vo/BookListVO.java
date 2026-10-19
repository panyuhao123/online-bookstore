package cn.edu.bookstore.model.vo;

import cn.edu.bookstore.entity.BookList;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 书单视图对象（列表用，不含书单内图书明细）。
 *
 * <p>不含 books 是有意为之：列表页每张卡片只需要「名称 / 简介 / 共 N 本 / 创建者」，
 * 把每张书单的图书全部带出来会让响应体随书单大小线性膨胀。
 * 需要明细时再请求书单详情。
 *
 * @author 毕设项目组
 */
@Data
public class BookListVO {

    private Long id;

    private String name;

    private String description;

    /**
     * 封面地址。
     *
     * <p>服务端在用户没设封面时用书单内第一本书的封面补上，
     * 这样列表页不必为「有些书单没封面」写一套占位逻辑。
     * 书单为空时仍为 null，由前端渲染统一占位图。
     */
    private String coverUrl;

    /** 是否公开：1-公开 0-仅自己可见 */
    private Integer isPublic;

    /** 书单内图书数量 */
    private Integer bookCount;

    /**
     * 平台治理状态：1-正常 0-已隐藏。
     *
     * <p>用户端也要下发这个字段：书单被平台隐藏后，「我的书单」里仍保留该条记录
     * （不凭空消失，否则用户会以为数据丢了），前端据此渲染「该内容已被隐藏」的提示。
     * 广场列表只下发 status=1 的条目，因此那里恒为 1。
     */
    private Integer status;

    /** 创建者用户 ID */
    private Long ownerId;

    /** 创建者昵称，列表页展示「@某某的书单」 */
    private String ownerNickname;

    /**
     * 当前登录者是否为创建者。
     *
     * <p>由服务端算好下发，而不是让前端拿 ownerId 与自己的 userId 比对 ——
     * 前端比对会把「谁能编辑」这条规则复制到客户端，一旦两边更新不同步就会出现
     * 「按钮能点但接口 403」。服务端下发的答案就是接口的答案，天然一致。
     */
    private Boolean mine;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public static BookListVO from(BookList list) {
        if (list == null) {
            return null;
        }
        BookListVO vo = new BookListVO();
        vo.setId(list.getId());
        vo.setName(list.getName());
        vo.setDescription(list.getDescription());
        vo.setCoverUrl(list.getCoverUrl());
        vo.setIsPublic(list.getIsPublic());
        vo.setBookCount(list.getBookCount());
        vo.setStatus(list.getStatus());
        vo.setOwnerId(list.getUserId());
        vo.setCreateTime(list.getCreateTime());
        vo.setUpdateTime(list.getUpdateTime());
        return vo;
    }
}
