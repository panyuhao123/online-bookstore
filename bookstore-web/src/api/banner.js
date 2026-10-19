import request from './request'

/**
 * 首页轮播图（免登录）。
 *
 * <p>服务端已经按「仅上架 + 在投放期内 + 按排序值升序」筛好，前端拿到什么就渲染什么，
 * 不再做二次过滤 —— 可见性规则只应存在一处，两处都写迟早会不一致。
 *
 * @returns {Promise<Array<{id, title, subTitle, imageUrl, bgColor, linkUrl, sort}>>}
 */
export function getBanners() {
  return request.get('/api/banner/list')
}
