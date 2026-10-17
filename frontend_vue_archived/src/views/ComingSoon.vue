<template>
  <div class="page">
    <div class="coming-soon">
      <div class="cs-icon"><Icon :name="icon" /></div>
      <h2 style="margin:0;color:var(--text)">{{ title }}</h2>
      <p>该模块正在从原生 JS 迁移至 Vue3（本阶段占位页）。</p>
      <p style="font-size:13px">后端接口与数据已就绪，下一步将在此接入完整交互。</p>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import Icon from '@/components/Icon.vue'

const route = useRoute()
const title = computed(() => route.meta.title || '页面')
const icon = computed(() => 'grid')
</script>
