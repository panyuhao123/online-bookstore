import request from './request'

/** 地址列表（默认地址排最前） */
export function getAddressList() {
  return request.get('/api/address/list')
}

/** 新增地址：{receiver, phone, province, city, district, provinceAdcode?, cityAdcode?, districtAdcode?, detail, isDefault?} */
export function addAddress(data) {
  return request.post('/api/address/add', data)
}

/** 编辑地址 */
export function updateAddress(addressId, data) {
  return request.put('/api/address/' + addressId, data)
}

/** 删除地址 */
export function removeAddress(addressId) {
  return request.delete('/api/address/' + addressId)
}

/** 设为默认地址 */
export function setDefaultAddress(addressId) {
  return request.put('/api/address/' + addressId + '/default')
}

/**
 * 地址自动解析（高德地理编码）
 * @param {string} addressText 用户输入的完整地址文本
 * @returns 候选列表 GeocodeResult { candidates:[{formattedAddress,province,city,district,districtAdcode}], count, uniqueMatch }
 */
export function geocodeAddress(addressText) {
  return request.post('/api/address/geocode', { address: addressText })
}
