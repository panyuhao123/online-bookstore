import request from './request'

/** 获取图形验证码 → { captchaKey, imageBase64 } */
export function getCaptcha() {
  return request.get('/api/captcha')
}
