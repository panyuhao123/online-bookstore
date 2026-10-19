import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// Vite 配置：开发端口固定 5173（已在后端 CORS 白名单内）
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    // 监听 0.0.0.0，便于局域网内其他机器（演示用平板 / 他人电脑）访问后台
    host: true
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  }
})
