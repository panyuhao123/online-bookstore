import App from './App'
import { createSSRApp } from 'vue'

/**
 * uni-app Vue3 工程入口。
 * 官方约定：必须导出 createApp 方法，且使用 createSSRApp 创建应用。
 */
export function createApp() {
  const app = createSSRApp(App)
  return {
    app
  }
}
