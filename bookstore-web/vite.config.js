import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

// Vite 配置：开发端口固定 5176（已在后端 CORS 白名单内），/api 代理到后端避免跨域
//
// 关于 importStyle: false ——
// main.js 里已经 `app.use(ElementPlus)` 并整体引入了 `element-plus/dist/index.css`，
// 样式是全量的。若这里保持默认的按需样式，解析器会为每个用到的组件再生成一条
// `element-plus/es/components/xxx/style/css` 深层导入：既和全量 CSS 完全重复，
// 又因为组件是「用到才被发现」的，Vite 每次访问新页面都会发现一批新依赖并触发
// 重新预构建（dev server 会在预构建里重启，正在跑的端到端脚本会整片失败）。
// 关掉它，预构建只认 element-plus 本体这一个包，一次性完成，之后不再变。
const elementPlusResolver = () => ElementPlusResolver({ importStyle: false })

export default defineConfig({
  plugins: [
    vue(),
    AutoImport({ resolvers: [elementPlusResolver()] }),
    Components({ resolvers: [elementPlusResolver()] })
  ],
  optimizeDeps: {
    // `element-plus/es` 必须显式列出：unplugin-vue-components 是从这个深层入口导入组件的，
    // 而 main.js 走的是 `element-plus` 本体，两者不在一张图里。不列出来的话，
    // Vite 会在**首次打开页面时**才发现它，于是「重新预构建 → 页面自动 reload」，
    // 端到端脚本刚好跑在这一刻就会整片失败（现象是断言全挂但接口其实都是好的）。
    include: ['element-plus', 'element-plus/es', '@element-plus/icons-vue']
  },
  server: {
    port: 5176,
    host: true,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      },
      '/files': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      }
    }
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  }
})
