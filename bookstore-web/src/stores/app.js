import { defineStore } from 'pinia'
import { ref } from 'vue'

/** 全局应用状态：侧边栏折叠、对比栏等 */
export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(false)

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  return { sidebarCollapsed, toggleSidebar }
})
