<template>
  <aside class="sidebar">
    <div class="sidebar-logo">
      <div class="logo-icon">智</div>
      <span>智学选课</span>
    </div>

    <nav class="sidebar-nav">
      <a
        v-for="n in navItems"
        :key="n.page"
        class="nav-item"
        :class="{ active: n.page === current }"
        @click="go(n.page)"
      >
        <span class="nav-icon"><Icon :name="n.icon" /></span>
        <span>{{ n.label }}</span>
      </a>
    </nav>

    <div class="sidebar-footer">
      <button class="ai-entry-btn" @click="ui.toggleAi()" title="AI 智能体对话">
        <Icon name="sparkles" />
        <span>AI 助手</span>
        <i class="ai-entry-dot" v-show="!ui.aiOpen"></i>
      </button>

      <div class="time-widget">
        <div class="time-widget-time">{{ clock.time }}</div>
        <div class="time-widget-date">{{ clock.date }}</div>
        <div class="time-widget-week">{{ clock.week }}</div>
      </div>

      <div class="round-info" v-if="round">
        <div>当前选课</div>
        <div class="round-num">第 {{ round.currentRound }} 轮</div>
        <span class="round-status" :class="round.roundOpen ? 'round-open' : 'round-closed'">
          {{ round.roundOpen ? '进行中' : '已关闭' }}
        </span>
        <div class="round-left" v-if="countdown">{{ countdown }}</div>
      </div>
    </div>
  </aside>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '@/components/Icon.vue'
import { NAV } from '@/config/nav'
import { useAuthStore } from '@/store/auth'
import { useUiStore } from '@/store/ui'
import { api } from '@/api/client'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const ui = useUiStore()

const current = computed(() => route.name || route.path.replace('/', ''))
const navItems = computed(() => NAV[auth.role] || [])

function go(page) { router.push('/' + page) }

const clock = reactive({ time: '', date: '', week: '' })
let clockTimer = null
function tickClock() {
  const d = new Date()
  const p = (n) => (n < 10 ? '0' + n : '' + n)
  const w = ['日', '一', '二', '三', '四', '五', '六']
  clock.time = `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
  clock.date = `${d.getFullYear()}年${p(d.getMonth() + 1)}月${p(d.getDate())}日`
  clock.week = '星期' + w[d.getDay()]
}

const round = ref(null)
const countdown = ref('')
let roundTimer = null
async function loadRound() {
  const r = await api('GET', '/system/config')
  if (r.success) round.value = r.data
}
function tickCountdown() {
  if (!round.value) return
  const c = round.value
  const target = c.roundOpen ? c.roundCloseAt : c.roundOpenAt
  if (!target) { countdown.value = ''; return }
  const diff = new Date(target).getTime() - Date.now()
  if (diff <= 0) { countdown.value = c.roundOpen ? '本轮已结束' : '尚未开放'; return }
  const d = Math.floor(diff / 86400000)
  const h = Math.floor((diff % 86400000) / 3600000)
  const m = Math.floor((diff % 3600000) / 60000)
  const s = Math.floor((diff % 60000) / 1000)
  countdown.value = c.roundOpen ? `距结束 ${d}天${h}时${m}分${s}秒` : `距开放 ${d}天${h}时${m}分${s}秒`
}

onMounted(() => {
  tickClock(); clockTimer = setInterval(tickClock, 1000)
  loadRound(); roundTimer = setInterval(tickCountdown, 1000)
})
onUnmounted(() => {
  clearInterval(clockTimer); clearInterval(roundTimer)
})
</script>
