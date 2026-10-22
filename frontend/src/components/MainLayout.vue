<template>
  <div class="app">
    <!-- 侧边栏（原版 .sidebar 结构） -->
    <aside class="sidebar" :class="{ collapsed: collapsed }">
      <div class="sidebar-logo">
        <div class="logo-icon">智</div>
        <span class="logo-text">学选课</span>
        <div class="sidebar-toggle" @click="collapsed = !collapsed">‹</div>
      </div>
      <nav class="sidebar-nav">
        <a
          v-for="item in navItems"
          :key="item.page"
          class="nav-item"
          :class="{ active: $route.path === '/' + item.page }"
          @click="go(item.page)"
        >
          <span class="nav-icon" v-html="svgIcon(item.icon)" />
          <span>{{ item.label }}</span>
        </a>
      </nav>
      <div class="sidebar-footer">
        <button class="ai-entry-btn" title="AI 智能体对话" @click="$router.push('/chat')">
          <span class="icon" v-html="svgIcon('sparkles')" />
          <span>AI 助手</span>
          <i class="ai-entry-dot" />
        </button>
        <div class="time-widget">
          <div class="time-widget-time">{{ timeText }}</div>
          <div class="time-widget-date">{{ dateText }}</div>
          <div class="time-widget-week">{{ weekText }}</div>
        </div>
        <div class="round-info" v-html="roundInfoHtml" />
      </div>
    </aside>

    <!-- 主内容区 -->
    <main class="main">
      <header class="topbar" :class="{ scrolled }">
        <div class="page-title">{{ currentTitle }}</div>
        <div class="topbar-right">
          <button class="topbar-icon-btn" @click="toggleTheme" title="切换主题">
            <span class="icon" v-html="svgIcon(theme === 'dark' ? 'sun' : 'moon')" />
          </button>
          <div style="position:relative">
            <button class="topbar-icon-btn" title="通知" @click="showNotif = !showNotif">
              <span class="icon" v-html="svgIcon('bell')" />
              <span v-if="notifCount" class="notif-dot" />
            </button>
            <div v-if="showNotif" class="notif-panel">
              <div class="notif-header">
                <span>通知中心</span>
                <span v-if="notifList.length" class="notif-clear" @click="notifList = []; notifCount = 0">清空</span>
              </div>
              <div v-if="notifList.length" class="notif-list">
                <div v-for="n in notifList" :key="n.id" class="notif-item">
                  <div class="notif-text">{{ n.text }}</div>
                  <div class="notif-time">{{ formatTime(n.time) }}</div>
                </div>
              </div>
              <div v-else class="notif-empty">暂无新通知</div>
            </div>
          </div>
          <button class="user-info" title="个人中心" @click="showProfile = true">
            <div class="avatar" v-html="userAvatar" />
            <div class="user-detail">
              <span class="user-name">{{ auth.user?.name || '同学' }}</span>
              <span class="user-meta">{{ ROLES[auth.user?.role] || '用户' }}</span>
            </div>
          </button>
          <button class="btn btn-ghost btn-sm" @click="logout">退出</button>
        </div>
      </header>

      <div class="content" ref="contentEl">
        <router-view v-slot="{ Component }">
          <transition name="page-fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </div>
    </main>

    <!-- 个人中心抽屉：点右上角用户区打开，不再单独占侧边栏 -->
    <el-drawer
      v-model="showProfile"
      title="个人中心"
      direction="rtl"
      size="460px"
      class="profile-drawer"
    >
      <ProfilePanel @logout="logoutFromPanel" />
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import ProfilePanel from '@/components/ProfilePanel.vue'
import http from '@/api'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const ROLES: Record<string, string> = { student: '学生', teacher: '老师', admin: '管理员' }

// ======== 菜单（原版 NAV 定义）========
const NAV: Record<string, any[]> = {
  student: [
    { page: 'dashboard', icon: 'home', label: '首页' },
    { page: 'courses', icon: 'grid', label: '课程浏览' },
    { page: 'intents', icon: 'cart', label: '我的意向' },
    { page: 'my-enrollments', icon: 'clipboard', label: '我的课程' },
    { page: 'waitlist', icon: 'hourglass', label: '候补队列' },
    { page: 'schedule', icon: 'calendar', label: '我的课表' },
    { page: 'grades', icon: 'award', label: '成绩查询' },
    { page: 'reviews', icon: 'message-square', label: '课程评价' },
    { page: 'graduation', icon: 'target', label: '毕业进度' },
    { page: 'advisor-apply', icon: 'graduation-cap', label: '毕业设计导师' },
    { page: 'workspace', icon: 'folder', label: '毕业设计工作台' },
  ],
  teacher: [
    { page: 'dashboard', icon: 'home', label: '首页' },
    { page: 'teach-courses', icon: 'book-open', label: '我的课程' },
    { page: 'approve', icon: 'check-circle', label: '选课审批' },
    { page: 'student-list', icon: 'users', label: '选课学生' },
    { page: 'grade-mgr', icon: 'edit', label: '成绩管理' },
    { page: 'reviews', icon: 'message-square', label: '课程评价' },
    { page: 'advise', icon: 'users', label: '毕业设计指导' },
    { page: 'workspace', icon: 'folder', label: '毕业设计工作台' },
  ],
  admin: [
    { page: 'dashboard', icon: 'home', label: '首页' },
    { page: 'rounds', icon: 'refresh', label: '选课轮次' },
    { page: 'semesters', icon: 'calendar', label: '学期管理' },
    { page: 'classrooms', icon: 'building', label: '教室管理' },
    { page: 'course-mgr', icon: 'book', label: '课程管理' },
    { page: 'user-mgr', icon: 'users', label: '用户管理' },
    { page: 'announce', icon: 'megaphone', label: '公告管理' },
    { page: 'stats', icon: 'bar-chart', label: '统计报表' },
    { page: 'advise-mgr', icon: 'graduation-cap', label: '导师管理' },
    { page: 'logs', icon: 'file-text', label: '系统日志' },
  ],
}
const navItems = computed(() => NAV[auth.user?.role || 'student'] || NAV.student)

// ======== 原版工具 ========
const svgIcon = (name: string) => `<svg class="icon"><use href="#i-${name}"/></svg>`
const userAvatar = computed(() => {
  const map: Record<string, string> = { student: '🎓', teacher: '👨‍🏫', admin: '👑' }
  const role = auth.user?.role || 'student'
  if (auth.user?.avatar) return `<img src="${auth.user.avatar}" style="width:100%;height:100%;border-radius:50%;object-fit:cover"/>`
  return map[role] || '👤'
})

// ======== 通知（后端暂未实现 SSE，跳过）========
const notifCount = ref(0)
const notifList = ref<any[]>([])
const showNotif = ref(false)
let notifyES: EventSource | null = null
function connectNotify() {
  // 后端没有 /api/notify/subscribe 这个 SSE 接口，跳过，避免 net::ERR_ABORTED 刷屏
  // 原版也是假 SSE（app.js 里实际是 WorkBuddy Cloud 的 notify 模拟，后端 JAR 没实现）
  return
}
function disconnectNotify() {
  if (notifyES) { try { notifyES.close() } catch {}; notifyES = null }
}

// ======== 状态 ========
const collapsed = ref(false)
const theme = ref<'light' | 'dark'>((localStorage.getItem('zhixue_theme') as any) || 'dark')
const showProfile = ref(false)

// 透明顶栏：滚动后渐变为磨砂玻璃
const scrolled = ref(false)
function onWindowScroll() { scrolled.value = window.scrollY > 6 }

// ======== 时间 widget ========
const timeText = ref('00:00:00')
const dateText = ref('2026年01月01日')
const weekText = ref('星期一')
let timeTimer: number | null = null
function tick() {
  const WEEK = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
  const now = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  timeText.value = `${p(now.getHours())}:${p(now.getMinutes())}:${p(now.getSeconds())}`
  dateText.value = `${now.getFullYear()}年${p(now.getMonth() + 1)}月${p(now.getDate())}日`
  weekText.value = WEEK[now.getDay()]
}

// ======== 选课轮次（原版 renderRoundInfo 逻辑）========
const roundInfo = ref<Record<string, any>>({})
const roundInfoHtml = ref('')
let roundTimer: number | null = null
async function loadRoundInfo() {
  try {
    const r: any = await http.get('/system/config')
    const c = r?.data || r
    roundInfo.value = c
    const closeAt = c.roundCloseAt ? new Date(c.roundCloseAt) : null
    const openAt = c.roundOpenAt ? new Date(c.roundOpenAt) : null
    const now = Date.now()
    let target: Date | null = null, label = ''
    if (closeAt && closeAt.getTime() > now) { target = closeAt; label = '距本轮结束' }
    else if (openAt && openAt.getTime() > now) { target = openAt; label = '距开放' }
    const p = (n: number) => String(n).padStart(2, '0')
    if (!target) {
      roundInfoHtml.value = `
        <div>当前选课</div>
        <div class="round-num">第 ${c.currentRound} 轮</div>
        <span class="round-status ${c.roundOpen ? 'round-open' : 'round-closed'}">${c.roundOpen ? '进行中' : '已关闭'}</span>
        <div class="round-left">${c.roundOpen ? '未设结束时间' : ''}</div>`
      return
    }
    const tick = () => {
      const diff = Math.max(0, target!.getTime() - Date.now())
      const d = Math.floor(diff / 86400000)
      const h = Math.floor((diff % 86400000) / 3600000)
      const m = Math.floor((diff % 3600000) / 60000)
      const s = Math.floor((diff % 60000) / 1000)
      roundInfoHtml.value = `
        <div>当前选课</div>
        <div class="round-num">第 ${c.currentRound} 轮</div>
        <span class="round-status ${c.roundOpen ? 'round-open' : 'round-closed'}">${c.roundOpen ? '进行中' : '已关闭'}</span>
        <div class="round-left">${label} ${p(d)}天 ${p(h)}:${p(m)}:${p(s)}</div>`
    }
    tick()
    if (roundTimer) clearInterval(roundTimer)
    roundTimer = setInterval(tick, 1000)
  } catch { /* ignore */ }
}

// ======== 路由跳转 ========
function go(page: string) {
  router.push('/' + page)
}
const currentTitle = computed(() => {
  const path = route.path.replace(/^\//, '').split('/')[0]
  const item = [...navItems.value].reverse().find(n => n.page === path)
  return item?.label || '首页'
})

// 切换页面时主内容区回到顶部
const contentEl = ref<HTMLElement | null>(null)
watch(() => route.path, () => {
  nextTick(() => {
    const c = contentEl.value || document.querySelector('.content')
    if (c) (c as HTMLElement).scrollTo({ top: 0, behavior: 'auto' })
  })
})

// ======== 主题切换 ========
function toggleTheme() {
  theme.value = theme.value === 'dark' ? 'light' : 'dark'
  localStorage.setItem('zhixue_theme', theme.value)
  document.documentElement.setAttribute('data-theme', theme.value)
}

function formatTime(t: any) {
  const d = new Date(t)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

// ======== 登出 ========
async function logout() {
  try {
    await ElMessageBox.confirm('确定退出登录？', '提示', { type: 'warning' })
  } catch { return }
  auth.logout()
  router.push('/login')
}

// ======== 个人中心（抽屉） ========
function logoutFromPanel() {
  showProfile.value = false
  logout()
}

onMounted(() => {
  document.documentElement.setAttribute('data-theme', theme.value)
  auth.loadFromStorage()
  tick(); timeTimer = setInterval(tick, 1000) as any
  loadRoundInfo()
  connectNotify()
  window.addEventListener('scroll', onWindowScroll, { passive: true })
  onWindowScroll()
})
onBeforeUnmount(() => {
  if (timeTimer) clearInterval(timeTimer)
  if (roundTimer) clearInterval(roundTimer)
  disconnectNotify()
  window.removeEventListener('scroll', onWindowScroll)
})
</script>

<style scoped>
.app { display: flex; min-height: 100vh; }
.icon { display: inline-block; width: 18px; height: 18px; }
.icon svg { width: 100%; height: 100%; }

/* 右上角用户入口（按钮化，点击打开个人中心抽屉） */
.user-info {
  background: transparent; border: none; font: inherit; color: inherit;
  padding: 4px 8px; border-radius: 10px; cursor: pointer;
  transition: background .15s;
}
.user-info:hover { background: var(--card-bg-2); }
.user-info:active { background: var(--border); }

/* 通知面板 */
.notif-panel {
  position: absolute; top: 42px; right: 0; width: 340px; max-height: 420px;
  background: var(--card); border: 1px solid var(--border, #334155);
  border-radius: 10px; box-shadow: 0 12px 40px rgba(0,0,0,0.3);
  z-index: 100; overflow: hidden; display: flex; flex-direction: column;
}
.notif-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 10px 14px; font-size: 13px; font-weight: 600; border-bottom: 1px solid var(--border);
}
.notif-clear { font-weight: 400; font-size: 12px; color: var(--primary, #4ade80); cursor: pointer; }
.notif-clear:hover { opacity: 0.8; }
.notif-list { overflow-y: auto; flex: 1; }
.notif-item { padding: 10px 14px; border-bottom: 1px solid var(--border, #334155); }
.notif-item:hover { background: rgba(255,255,255,0.03); }
.notif-text { font-size: 13px; color: var(--text); line-height: 1.4; }
.notif-time { font-size: 11px; color: var(--text-light); margin-top: 4px; }
.notif-empty { padding: 40px; text-align: center; font-size: 13px; color: var(--text-light); }
.notif-dot {
  position: absolute; top: 4px; right: 4px; width: 8px; height: 8px;
  border-radius: 50%; background: #ef4444; border: 2px solid var(--card-bg);
}

/* 路由切换淡入动画 */
.page-fade-enter-active, .page-fade-leave-active { transition: opacity .2s ease, transform .2s ease; }
.page-fade-enter-from { opacity: 0; transform: translateY(10px); }
.page-fade-leave-to { opacity: 0; transform: translateY(-10px); }
@media (prefers-reduced-motion: reduce) {
  .page-fade-enter-active, .page-fade-leave-active { transition: none; }
  .page-fade-enter-from, .page-fade-leave-to { transform: none; }
}
</style>
