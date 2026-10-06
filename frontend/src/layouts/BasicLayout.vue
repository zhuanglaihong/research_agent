<template>
  <a-layout class="basic-layout">
    <GlobalHeader />
    <a-layout-content class="content">
      <router-view />
    </a-layout-content>
    <GlobalFooter />
  </a-layout>
</template>

<script setup lang="ts">
import GlobalHeader from '@/components/GlobalHeader.vue'
import GlobalFooter from '@/components/GlobalFooter.vue'
</script>

<style scoped>
.basic-layout {
  min-height: 100vh;
  background: transparent;
}

.content {
  width: 100%;
  padding: 0;
  background: none;
  margin: 0;
}
</style>
