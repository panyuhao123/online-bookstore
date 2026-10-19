package cn.edu.bookstore.model.vo;

import cn.edu.bookstore.entity.Banner;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 首页轮播图视图对象。
 *
 * @author 毕设项目组
 */
@Data
public class BannerVO {

    private Long id;

    private String title;

    private String subTitle;

    /** 背景图地址；为空时前端用 bgColor 渲染 */
    private String imageUrl;

    private String bgColor;

    private String linkUrl;

    private Integer sort;

    /** 状态：1-上架 0-下架 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public static BannerVO from(Banner banner) {
        if (banner == null) {
            return null;
        }
        BannerVO vo = new BannerVO();
        vo.setId(banner.getId());
        vo.setTitle(banner.getTitle());
        vo.setSubTitle(banner.getSubTitle());
        vo.setImageUrl(banner.getImageUrl());
        vo.setBgColor(banner.getBgColor());
        vo.setLinkUrl(banner.getLinkUrl());
        vo.setSort(banner.getSort());
        vo.setStatus(banner.getStatus());
        vo.setCreateTime(banner.getCreateTime());
        vo.setUpdateTime(banner.getUpdateTime());
        return vo;
    }
}
