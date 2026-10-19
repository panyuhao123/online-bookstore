/** 图书模块接口（免登录可浏览） */
import { get } from '../common/request'

/**
 * 图书分页：{keyword?, categoryId?, pageNum, pageSize}
 * -> PageVO：{list, total, pageNum, pageSize, pages}
 */
export const pageBooks = (params) => get('/api/book/page', params)

/** 图书详情 -> BookVO（opts.silent=true 时失败不弹提示，由调用方回退处理） */
export const getBook = (id, opts = {}) => get('/api/book/' + id, null, opts)
