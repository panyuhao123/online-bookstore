package cn.edu.bookstore.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 图书评价汇总（详情页顶部展示）。
 *
 * @author 毕设项目组
 */
@Data
public class ReviewSummaryVO {

    /** 平均分（无评价时为 null，前端显示「暂无评分」而不是 0 分） */
    private BigDecimal avgRating;

    /** 评价总条数 */
    private Integer reviewCount;

    /**
     * 各星级评价数，固定返回 5→1 星五个元素（没有的星级补 0）。
     *
     * <p>固定长度是为了前端能直接按顺序渲染评分分布条，
     * 不用处理「某个星级缺失」的分支。
     */
    private List<RatingDistributionVO> distribution;

    /** 单个星级对应的评价数量 */
    @Data
    public static class RatingDistributionVO {
        /** 星级：5~1 */
        private Integer rating;
        /** 该星级的评价条数 */
        private Long count;

        public RatingDistributionVO() {
        }

        public RatingDistributionVO(Integer rating, Long count) {
            this.rating = rating;
            this.count = count;
        }
    }
}
