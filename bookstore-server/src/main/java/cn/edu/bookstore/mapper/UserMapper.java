package cn.edu.bookstore.mapper;

import cn.edu.bookstore.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 用户数据访问层。
 *
 * <p>单表查询直接复用 MyBatis-Plus 的 BaseMapper；复杂查询在对应 XML 中编写。
 *
 * @author 毕设项目组
 */
public interface UserMapper extends BaseMapper<User> {
}
