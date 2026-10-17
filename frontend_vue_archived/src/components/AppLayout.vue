<template>
  <div class="app">
    <Sidebar />
    <main class="main">
      <Topbar />
      <div class="content">
        <RouterView v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </RouterView>
      </div>
    </main>
    <AiChatPanel />
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import Sidebar from '@/components/Sidebar.vue'
import Topbar from '@/components/Topbar.vue'
import AiChatPanel from '@/components/ai/AiChatPanel.vue'
import { useUiStore } from '@/store/ui'

const ui = useUiStore()
onMounted(() => ui.applyTheme(ui.theme))
</script>
