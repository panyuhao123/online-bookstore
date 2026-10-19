import { defineConfig, mergeConfig } from 'vitest/config'
import viteConfig from './vite.config.js'

/**
 * 管理端单元测试配置。
 *
 * 刻意独立于 vite.config.js：dev / build 的行为一个字都不动，
 * 想回滚只需删掉本文件与几个 *.spec.js。
 * 通过 mergeConfig 复用 vite 的 alias，保证测试里 `@/` 的写法与源码完全一致
 * （alias 只有一份，不会出现「改了 vite 配置忘了改测试配置」的漂移）。
 */
export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      // 被测模块（登录态存取、axios 拦截器）直接依赖 localStorage 与 window
      environment: 'jsdom',
      globals: true,
      include: ['src/**/*.spec.js'],
      // 每个用例前自动清理 mock 调用记录，避免用例之间互相污染
      clearMocks: true,
      restoreMocks: true,
      reporters: 'default',
      // 用 threads 而不是默认的 forks（多进程）：vite 会把 `/@vite/env` 这个虚拟模块
      // 落到 `os.tmpdir()/<projectId>/<mode>/<sha1>`，vitest 内部有一张「同一路径只写
      // 一次」的去重表，但那张表不跨进程 —— forks 模式下多个 worker 会并发写同一个
      // 文件，在写临时目录受限的环境里会抛 EPERM 并中断收集阶段（表现为「悄悄少跑
      // 测试文件」）。threads 同进程共享去重表，只写一次，问题消失。
      // 本项目管理端用例很少，单进程开销可忽略。
      pool: 'threads'
    }
  })
)
