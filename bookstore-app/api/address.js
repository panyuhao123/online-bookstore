/** 收货地址模块接口（需登录） */
import { get, post, put, del } from '../common/request'

/** 地址列表（默认地址排最前） */
export const getAddressList = () => get('/api/address/list')

/** 新增地址：{receiver, phone, province, city, district, detail, isDefault?} */
export const addAddress = (data) => post('/api/address/add', data)

/** 编辑地址 */
export const updateAddress = (addressId, data) => put('/api/address/' + addressId, data)

/** 删除地址 */
export const removeAddress = (addressId) => del('/api/address/' + addressId)

/** 设为默认地址 */
export const setDefaultAddress = (addressId) => put('/api/address/' + addressId + '/default')
