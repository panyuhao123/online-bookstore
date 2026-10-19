package cn.edu.bookstore.service;

import cn.edu.bookstore.model.dto.AdminBookQuery;
import cn.edu.bookstore.model.dto.BookSaveRequest;
import cn.edu.bookstore.model.vo.BookVO;
import cn.edu.bookstore.model.vo.PageVO;

/**
 * 管理端图书业务（Web 后台图书管理）。
 *
 * <p>与 APP 端 {@link BookService} 分离的原因：APP 端是只读且强制只展示上架图书，
 * 管理端需要看下架商品、写操作与数据校验规则也不同。
 *
 * @author 毕设项目组
 */
public interface AdminBookService {

    /** 后台图书分页：支持关键词、分类、上下架状态筛选 */
    PageVO<BookVO> page(AdminBookQuery query);

    /** 后台图书详情（含下架图书） */
    BookVO detail(Long id);

    /** 新增图书，返回图书 ID */
    Long create(BookSaveRequest request);

    /** 修改图书 */
    void update(Long id, BookSaveRequest request);

    /** 上下架：status 取 1（上架）或 0（下架） */
    void updateStatus(Long id, Integer status);

    /** 删除图书；已被下单过的图书不允许删除，应改为下架 */
    void delete(Long id);
}
