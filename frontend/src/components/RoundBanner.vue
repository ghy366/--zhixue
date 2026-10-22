<template>
  <div v-if="visible" class="login-round-banner lrb-left" style="display:block">
    <div class="lrb-head">
      <span class="lrb-badge">第 {{ config.currentRound || 1 }} 轮</span>
      <span class="lrb-pulse" />
      <span class="lrb-status">{{ statusText }}</span>
      <span class="lrb-label">{{ labelText }}</span>
    </div>
    <div class="lrb-count">
      <div class="lrb-cell"><b data-num="00">{{ num(diff.d) }}</b><span>天</span></div>
      <i>:</i>
      <div class="lrb-cell"><b data-num="00">{{ num(diff.h) }}</b><span>时</span></div>
      <i>:</i>
      <div class="lrb-cell"><b data-num="00">{{ num(diff.m) }}</b><span>分</span></div>
      <i>:</i>
      <div class="lrb-cell"><b data-num="00">{{ num(diff.s) }}</b><span>秒</span></div>
    </div>
    <div class="lrb-progress">
      <div class="lrb-progress-track">
        <div class="lrb-progress-fill" :style="{ width: progress + '%' }" />
      </div>
      <span class="lrb-progress-label">进度 {{ progress }}%</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount, computed } from 'vue'
import http from '@/api'

const visible = ref(false)
const config = reactive<Record<string, any>>({})
const diff = reactive({ d: 0, h: 0, m: 0, s: 0 })
const statusText = ref('选课进行中')
const labelText = ref('距本轮结束')
const progress = ref(0)
let timer: number | null = null
let endAt: Date | null = null
let startAt: Date | null = null
let refreshPending = false

function num(n: number) { return String(Math.max(0, n)).padStart(2, '0') }
function parseISO(s: any): Date | null {
  if (!s) return null
  try { const t = new Date(s); return isNaN(t.getTime()) ? null : t } catch { return null }
}

function tick() {
  const now = new Date()
  const ms = endAt ? endAt.getTime() - now.getTime() : 0
  if (ms <= 0) {
    diff.d = diff.h = diff.m = diff.s = 0
    if (!refreshPending) {
      refreshPending = true
      setTimeout(loadData, 1500)
    }
    return
  }
  diff.d = Math.floor(ms / 86400000)
  diff.h = Math.floor((ms % 86400000) / 3600000)
  diff.m = Math.floor((ms % 3600000) / 60000)
  diff.s = Math.floor((ms % 60000) / 1000)
  if (startAt && endAt) {
    const total = endAt.getTime() - startAt.getTime()
    progress.value = Math.max(0, Math.min(100, Math.round((now.getTime() - startAt.getTime()) * 100 / total)))
  }
}

async function loadData() {
  refreshPending = false
  try {
    const res: any = await http.get('/system/config')
    const c = res?.data || {}
    Object.assign(config, c)
    const ca = parseISO(c.roundCloseAt)
    const oa = parseISO(c.roundOpenAt)
    const now = new Date()
    if (ca && ca.getTime() > now.getTime()) {
      visible.value = true
      startAt = oa
      endAt = ca
      tick()
    } else {
      visible.value = false
    }
  } catch {
    visible.value = false
  }
}

onMounted(() => {
  loadData()
  timer = window.setInterval(tick, 1000)
})
onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer)
})

defineExpose({ reload: loadData })
</script>

<style scoped>
/* 所有视觉样式在 src/styles/original.css 里（原版 login-round-banner / lrb-* 类名）
   这里只定义原版没有但我们需要的 keyframes */
@keyframes lrbPing {
  0%   { box-shadow: 0 0 0 0 rgba(16,185,129,0.45); }
  70%  { box-shadow: 0 0 0 9px rgba(16,185,129,0); }
  100% { box-shadow: 0 0 0 0 rgba(16,185,129,0); }
}
</style>
