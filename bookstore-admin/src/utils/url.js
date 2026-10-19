import { getBaseUrl } from './auth'

/**
 * 拼接封面图片的完整访问地址。
 *
 * 后端上传接口返回的是相对路径（/files/cover/xxx.jpg），图片本身由后端静态映射提供，
 * 因此必须带上后端地址才能显示；这样更换后端地址时封面不会失效。
 */
export function fileUrl(path) {
  if (!path) return ''
  if (path.startsWith('http://') || path.startsWith('https://')) return path
  return getBaseUrl() + (path.startsWith('/') ? path : '/' + path)
}
