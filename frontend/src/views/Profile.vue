<template>
  <div class="zx-page stats-page liquid">
    <div class="page-head">
      <h2>👤 个人信息</h2>
      <span class="page-tip">提示：现在也可以直接点右上角头像打开</span>
    </div>
    <ProfilePanel @logout="handleLogout" />
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import ProfilePanel from '@/components/ProfilePanel.vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

function handleLogout() {
  auth.logout()
  router.push('/login')
}
</script>

<style scoped>
.zx-page { display: flex; flex-direction: column; gap: 14px; max-width: 720px; }
.page-head { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.page-head h2 { margin: 0; color: var(--text); font-size: 20px; }
.page-tip { font-size: 12px; color: var(--text-light); }
</style>
