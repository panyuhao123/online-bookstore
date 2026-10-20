package cn.edu.bookstore.mapper;

import cn.edu.bookstore.entity.User;
import cn.edu.bookstore.model.vo.AdminUserSummaryVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

/**
 * 用户数据访问层。
 *
 * <p>单表查询直接复用 MyBatis-Plus 的 BaseMapper；复杂查询在对应 XML 中编写。
 *
 * @author 毕设项目组
 */
public interface UserMapper extends BaseMapper<User> {

    /**
     * 用户管理页的汇总指标。
     *
     * <p>「本月新增」用 create_time 与本月第一天比较，而不是按 30 天滚动：
     * 运营口径里的「本月」就是自然月，滚动窗口会在月初把上个月的尾巴算进来。
     */
    @Select("SELECT COUNT(*) AS total, "
            + "COALESCE(SUM(status = 1), 0) AS active, "
            + "COALESCE(SUM(status = 0), 0) AS disabled, "
            + "COALESCE(SUM(create_time >= DATE_FORMAT(CURDATE(), '%Y-%m-01')), 0) AS new_this_month "
            + "FROM `user`")
    AdminUserSummaryVO selectAdminSummary();
}
