package cn.edu.bookstore.model.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Set;

/**
 * 图书查询条件：支持关键词模糊搜索、分类筛选与价格区间筛选。
 *
 * <p>分页能力由 {@link BasePageQuery} 提供。
 *
 * @author 毕设项目组
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BookQuery extends BasePageQuery {

    /** 搜索关键词，匹配书名或作者 */
    private String keyword;

    /** 分类 ID，为空表示不筛选 */
    private Long categoryId;

    /** 价格下限（含），为空表示不限制 */
    private BigDecimal minPrice;

    /** 价格上限（含），为空表示不限制 */
    private BigDecimal maxPrice;

    /**
     * 排序方式：default（综合，销量优先）/ sales（销量）/ price_asc（价格从低到高）/ price_desc（价格从高到低）。
     *
     * <p>用户端列表页的排序切换需要真实生效，因此由前端显式传入；
     * 为空或非法值时按 default 处理，保证老客户端行为不变。
     */
    private String sort;

    /** 允许的排序值，用于白名单校验 */
    private static final Set<String> ALLOWED_SORTS = Set.of("default", "sales", "price_asc", "price_desc");

    /**
     * 规范化排序参数：非法值一律回落 default。
     *
     * <p>为什么不用枚举直接反序列化：非法值会直接抛 400，
     * 而排序只是展示偏好，静默回落比报错更贴近用户预期。
     */
    public void normalizeSort() {
        if (sort == null || sort.isBlank() || !ALLOWED_SORTS.contains(sort.trim())) {
            sort = "default";
            return;
        }
        sort = sort.trim();
    }

    /**
     * 规范化价格区间。
     *
     * <p>两个取舍：
     * <ul>
     *     <li>负数一律视为未填写 —— 价格不可能为负，静默忽略比抛错更贴近用户预期；</li>
     *     <li>「最低价 > 最高价」时自动对调，而不是返回参数错误：
     *         用户在筛选栏里填反了顺序很常见，直接按他表达的范围查询体验更好。</li>
     * </ul>
     */
    public void normalizePriceRange() {
        if (minPrice != null && minPrice.signum() < 0) {
            minPrice = null;
        }
        if (maxPrice != null && maxPrice.signum() < 0) {
            maxPrice = null;
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            BigDecimal temp = minPrice;
            minPrice = maxPrice;
            maxPrice = temp;
        }
    }
}
