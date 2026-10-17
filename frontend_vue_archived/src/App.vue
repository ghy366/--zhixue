<template>
  <RouterView />
</template>

<script setup>
import { onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/store/auth'

const router = useRouter()
const auth = useAuthStore()

function onExpired() {
  auth.logout()
  router.replace('/login')
}
onMounted(() => window.addEventListener('auth-expired', onExpired))
onUnmounted(() => window.removeEventListener('auth-expired', onExpired))
</script>
