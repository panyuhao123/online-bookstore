/** 分类模块接口（免登录可浏览） */
import { get } from '../common/request'

/** 分类列表 -> [{id, name, sort, remark}] */
export const getCategories = () => get('/api/category/list')
