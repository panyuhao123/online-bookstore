<template>
  <footer class="app-footer">
    <div class="footer-inner">
      <div class="footer-section">
        <h4>关于我们</h4>
        <p>线上书店 —— 软件工程本科毕业设计项目</p>
      </div>
      <div class="footer-section">
        <h4>技术栈</h4>
        <p>Vue3 + Vite6 + Element Plus + Pinia</p>
      </div>
      <div class="footer-section">
        <h4>联系方式</h4>
        <p>演示账号：13800138000 / 123456</p>
      </div>
    </div>
    <div class="copyright">© 2026 线上书店 PC 端 · 仅供教学演示</div>
  </footer>
</template>

<style scoped>
.app-footer {
  background: #2c2f36;
  color: #a0a4ab;
  padding: 32px 0 16px;
  margin-top: 40px;
}

.footer-inner {
  max-width: var(--content-max);
  margin: 0 auto;
  padding: 0 20px;
  display: flex;
  gap: 60px;
}

.footer-section h4 {
  color: #fff;
  font-size: 15px;
  margin-bottom: 12px;
}

.footer-section p {
  font-size: 13px;
  line-height: 1.8;
}

.copyright {
  text-align: center;
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid #3d4048;
  font-size: 12px;
}
</style>
