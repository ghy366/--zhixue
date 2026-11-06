<template>
  <div ref="el" class="ec-wrap" :style="fill ? { height: '100%', flex: '1', minHeight: '120px' } : { height: height }" />
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import echarts from '@/utils/echarts'

const props = defineProps<{ option: any; height?: string; fill?: boolean }>()
const el = ref<HTMLDivElement | null>(null)
let chart: any = null
let ro: ResizeObserver | null = null

function render() {
  if (!el.value) return
  try {
    if (!chart) chart = echarts.init(el.value)
    chart.setOption(props.option, true)
  } catch (err: any) {
    // 初始化/配置错误写到 dataset，便于页面侧排查（正常情况不应触发）
    el.value.dataset.ecErr = String(err?.message || err).slice(0, 200)
    console.error('[EChart]', err)
  }
}

onMounted(() => {
  render()
  ro = new ResizeObserver(() => {
    try { chart?.resize() } catch {}
  })
  ro.observe(el.value)
})

watch(() => props.option, () => render(), { deep: true })

onBeforeUnmount(() => {
  ro?.disconnect()
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.ec-wrap { width: 100%; min-width: 0; }
</style>
