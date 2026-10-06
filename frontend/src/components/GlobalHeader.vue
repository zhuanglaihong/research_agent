<template>
  <a-layout-header class="header">
    <RouterLink to="/" class="brand">
      <span class="mark" aria-hidden="true">R</span>
      <strong>research_agent</strong>
    </RouterLink>
    <nav class="navigation"><RouterLink to="/demo">交互演示</RouterLink><RouterLink v-if="!staticDemo" to="/">本地工作台</RouterLink></nav>
  </a-layout-header>
</template>

<script setup lang="ts">
const staticDemo = import.meta.env.VITE_STATIC_DEMO === 'true'
</script>

<style scoped>
.header { background: #fff; padding: 0 24px; display: flex; justify-content: space-between; align-items: center; }
.brand { display: inline-flex; align-items: center; gap: 12px; color: #1677ff; font-size: 18px; }
.mark { width: 34px; height: 34px; border-radius: 10px; display: grid; place-items: center; background: #1677ff; color: #fff; }
.navigation { display:flex; gap:18px; font-size:13px; }
.navigation a { color:#475467; }
.navigation a.router-link-active { color:#1677ff; font-weight:600; }
</style>
