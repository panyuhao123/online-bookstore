/**
 * 省市区三级联动数据。
 * App / H5 端无内置 mode=region 选择器，只能用 multiSelector + 本地数据自行实现，
 * 详见 https://uniapp.dcloud.net.cn/component/picker.html
 */
import { REGION_TREE } from './region-data'

/** 省级列表 */
export const PROVINCES = Object.keys(REGION_TREE)

/** 某省下的市列表 */
export function getCities(province) {
  return Object.keys(REGION_TREE[province] || {})
}

/** 某市下的区/县列表 */
export function getDistricts(province, city) {
  return (REGION_TREE[province] || {})[city] || []
}

/**
 * 把已保存的省/市/区反查为三列下标（用于编辑回填）。
 * 省级或市级未命中返回 null（说明数据已变更），区县级未命中退回第一项。
 */
export function resolveIndex(province, city, district) {
  const pi = PROVINCES.indexOf(province)
  if (pi < 0) return null
  const ci = getCities(province).indexOf(city)
  if (ci < 0) return null
  const di = getDistricts(province, city).indexOf(district)
  return [pi, ci, di < 0 ? 0 : di]
}
