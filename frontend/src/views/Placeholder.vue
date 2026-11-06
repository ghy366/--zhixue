<template>
  <div class="placeholder">
    <div class="ph-icon">🛠️</div>
    <h2>{{ t((route.meta.title as string) || '页面') }}</h2>
    <p>{{ t('Vue 迁移中 —— 阶段④将实现完整功能') }}</p>
    <div class="ph-info">
      <div><b>{{ t('路由：') }}</b>{{ route.path }}</div>
      <div><b>{{ t('角色：') }}</b>{{ auth.user?.role }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { t } from '@/i18n'
const route = useRoute()
const auth = useAuthStore()
</script>

<style scoped>
.placeholder {
  background: var(--card); border: 1px solid var(--border);
  border-radius: 12px; padding: 60px 40px; text-align: center;
}
.ph-icon { font-size: 48px; margin-bottom: 16px; }
.placeholder h2 { margin: 0 0 8px; color: var(--text); font-size: 22px; }
.placeholder p { margin: 0; color: var(--text-lighter); font-size: 14px; }
.ph-info {
  margin-top: 24px; padding: 14px; background: rgba(15,23,42,0.6);
  border-radius: 8px; display: inline-block; text-align: left;
  font-size: 12px; color: var(--text-light); line-height: 1.8;
}
.ph-info b { color: #4ade80; }
</style>
