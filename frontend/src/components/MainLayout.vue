<template>
  <div class="app">
    <!-- 侧边栏（原版 .sidebar 结构）：默认收起，悬停展开，可固定 -->
    <aside
      class="sidebar" :class="{ collapsed: !expanded, pinned, 'mobile-open': mobileOpen }"
      aria-label="主导航"
      @mouseenter="sideEnter" @mouseleave="sideLeave"
      @focusin="sideEnter" @focusout="sideLeave"
    >
      <div class="sidebar-logo">
        <div class="logo-icon">智</div>
        <span class="logo-text">智学选课<span class="logo-sub">ZHIXUE · COURSE ATLAS</span></span>
        <div
          class="sidebar-toggle" role="button" tabindex="0"
          :title="expanded ? t('取消固定，收起侧边栏') : t('固定展开侧边栏')"
          :aria-pressed="String(pinned)"
          @click="togglePinned" @keydown.enter.prevent="togglePinned"
        >‹</div>
      </div>
      <div class="ark-hazard" aria-hidden="true" />
      <nav class="sidebar-nav">
        <template v-for="item in navItems" :key="item.page">
          <!-- 学域分组：分组边界上的编号 + 名称，收起态只留一个星点 -->
          <div v-if="item.groupStart" class="ark-group" aria-hidden="true">
            <i /><span>学域 {{ item.groupNo }} · {{ t(item.group) }}</span>
          </div>
          <a
            class="nav-item"
            :class="{ active: $route.path === '/' + item.page, 'nav-item-ai': item.page === 'chat' }"
            :title="t(item.label) + ' · ' + t(item.hint)"
            tabindex="0"
            @click="go(item.page)"
            @keydown.enter.prevent="go(item.page)"
          >
            <span class="nav-icon" v-html="svgIcon(item.icon)" />
            <span>{{ t(item.label) }}</span>
            <span class="nav-code">{{ item.code }}</span>
          </a>
        </template>
      </nav>
      <!-- 用户信息：固定沉底；头像弹个人菜单（个人中心/主题切换/通知/退出登录） -->
      <div class="sidebar-footer side-user-box">
        <div class="user-menu-anchor">
          <button class="side-user" :class="{ 'menu-open': showUserMenu, 'has-unread': !!unreadTotal }" :title="t('个人菜单')" @click="toggleUserMenu">
            <div class="side-avatar" v-html="userAvatar" />
            <div class="side-user-detail">
              <span class="side-user-name">{{ auth.user?.name || t('同学') }}</span>
              <span class="side-user-meta">{{ t(ROLES[auth.user?.role] || '用户') }}</span>
            </div>
            <svg class="side-user-chevron" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m18 15-6-6-6 6"/></svg>
          </button>
          <div v-if="showUserMenu" class="user-menu">
            <button class="um-item" @click="openProfile">
              <span class="icon" v-html="svgIcon('user')" /><span>{{ t('个人档案') }}</span>
            </button>
            <button class="um-item" @click="onMenuToggleTheme">
              <span class="icon" v-html="svgIcon(theme === 'dark' ? 'sun' : 'moon')" />
              <span>{{ t(theme === 'dark' ? '日间模式' : '夜间模式') }}</span>
            </button>
            <button class="um-item" @click="onMenuToggleLang">
              <span class="icon" v-html="svgIcon('globe')" />
              <span>{{ getLocale() === 'zh' ? 'English' : '中文' }}</span>
            </button>
            <button class="um-item notif-trigger" @click="openNotifPanel">
              <span class="icon" v-html="svgIcon('bell')" /><span>{{ t('消息通知') }}</span>
              <em v-if="unreadTotal" class="um-badge">{{ unreadTotal > 99 ? '99+' : unreadTotal }}</em>
            </button>
            <div class="um-divider" />
            <button class="um-item um-danger" @click="logout">
              <span class="icon" v-html="svgIcon('log-out')" /><span>{{ t('退出登录') }}</span>
            </button>
          </div>
          <div v-if="showNotif" class="notif-panel" ref="notifPanelEl">
            <div class="notif-header">
              <span>{{ t('消息通知') }}<em v-if="unreadTotal" class="notif-unread">{{ t('（{n} 条未读）', { n: unreadTotal }) }}</em></span>
              <span v-if="notifList.length" class="notif-clear" @click="markAllRead">{{ t('全部已读') }}</span>
            </div>
            <div class="notif-tabs">
              <button class="notif-tab" :class="{ on: notifFilter === 'all' }" @click="notifFilter = 'all'">{{ t('全部') }}</button>
              <button class="notif-tab" :class="{ on: notifFilter === 'unread' }" @click="notifFilter = 'unread'">
                {{ t('未读') }}<em v-if="msgUnread">{{ msgUnread }}</em>
              </button>
              <span class="notif-live" :class="{ on: sseConnected }" :title="sseConnected ? t('实时推送已连接') : t('实时推送未连接，轮询中')">
                <i />{{ t(sseConnected ? '实时' : '轮询') }}
              </span>
            </div>
            <div v-if="annUnread > 0" class="notif-ann" @click="goAnnounce">
              📣 {{ t('有 {n} 条公告未读，点击前往查看', { n: annUnread }) }}
            </div>
            <div v-if="notifLoading && !notifList.length" class="notif-loading">{{ t('加载中…') }}</div>
            <div v-else-if="filteredNotif.length" class="notif-list">
              <div
                v-for="n in filteredNotif" :key="n.id"
                class="notif-item" :class="{ unread: !n.read }"
                @click="openItem(n)"
              >
                <div class="notif-title">{{ n.title }}</div>
                <div class="notif-text">{{ n.content }}</div>
                <div class="notif-time" :title="fullTime(n.createdAt)">{{ formatTime(n.createdAt) }}</div>
              </div>
            </div>
            <div v-else class="notif-empty">{{ t(notifFilter === 'unread' ? '没有未读消息了 🎉' : '暂无消息') }}</div>
          </div>
        </div>
      </div>
    </aside>

    <!-- 主内容区 -->
    <main class="main">
      <header class="topbar" :class="{ scrolled }">
        <div class="topbar-left">
          <button class="topbar-icon-btn mobile-menu-btn" :title="t('菜单')" @click="mobileOpen = !mobileOpen">
            <span class="icon" v-html="svgIcon('menu')" />
          </button>
          <div class="page-title"><span class="pt-no">{{ currentNo }}</span>{{ currentTitle }}</div>
        </div>
        <div class="topbar-right ark-top-reading">
          <span class="ark-led" aria-hidden="true" />
          <span>ATLAS <b>ONLINE</b></span>
          <span class="ark-top-sep" aria-hidden="true" />
          <span>{{ t(ROLES[auth.user?.role] || '用户') }} <b>/ {{ roleCode }}</b></span>
          <span class="ark-top-sep" aria-hidden="true" />
          <span class="ab-clock">{{ clockText }}</span>
        </div>
      </header>

      <div class="content" ref="contentEl">
        <div class="scroll-progress" :style="{ width: scrollPct + '%' }" aria-hidden="true" />
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
      :title="t('个人档案')"
      direction="rtl"
      size="460px"
      class="profile-drawer"
    >
      <ProfilePanel @logout="logoutFromPanel" />
    </el-drawer>

    <!-- 公告弹窗：点通知横幅直接查看，不再跳转任何页面 -->
    <el-dialog
      v-model="annDialog"
      class="ann-dialog"
      :title="t('公告通知')"
      width="min(560px, calc(100vw - 32px))"
      :close-on-click-modal="true"
    >
      <template #header>
        <div class="ann-head">
          <span class="ann-head-title">📣 {{ t('公告通知') }}</span>
          <span class="ann-head-sub">{{ t('共 {n} 条公告，{m} 条未读', { n: annItems.length, m: annUnreadCount }) }}</span>
        </div>
      </template>

      <div v-if="annLoading" class="ann-state">{{ t('加载中…') }}</div>
      <div v-else-if="!annItems.length" class="ann-state">{{ t('暂无公告') }}</div>
      <div v-else class="ann-list">
        <div v-for="a in annItems" :key="a.id" class="ann-card" :class="{ unread: !a.read }">
          <div class="ann-card-head">
            <span v-if="!a.read" class="ann-dot" />
            <span v-if="a.pinned" class="ann-chip pin">📌 {{ t('置顶') }}</span>
            <span v-if="a.type" class="ann-chip">{{ a.type }}</span>
            <span class="ann-card-title">{{ a.title }}</span>
          </div>
          <div class="ann-card-content">{{ a.content || t('（无正文）') }}</div>
          <div class="ann-card-foot">
            <span class="ann-meta">{{ a.publisherName || t('管理员') }} · {{ formatAnnTime(a.createdAt) }}</span>
            <button v-if="!a.read" class="ann-read-btn" :disabled="annBusy !== null" @click="markAnnRead(a)">
              {{ annBusy === a.id ? t('处理中…') : t('标为已读') }}
            </button>
            <span v-else class="ann-read-done">✓ {{ t('已读') }}</span>
          </div>
        </div>
      </div>

      <template #footer>
        <div class="ann-foot">
          <button class="ann-all-btn" :disabled="!annUnreadCount || annBusy !== null" @click="markAnnAllRead">
            {{ annBusy === -1 ? t('处理中…') : t('全部标为已读') }}
          </button>
          <button class="ann-close-btn" @click="annDialog = false">{{ t('关闭') }}</button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { homePath } from '@/utils/home'
import ProfilePanel from '@/components/ProfilePanel.vue'
import http from '@/api'
import { t, getLocale, toggleLocale } from '@/i18n'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const ROLES: Record<string, string> = { student: '学生', teacher: '教师', admin: '管理员' }
const ROLE_CODE: Record<string, string> = { student: 'STUDENT', teacher: 'TEACHER', admin: 'ADMIN' }
const roleCode = computed(() => ROLE_CODE[auth.user?.role || 'student'] || 'OPERATOR')

// ======== 菜单：学域星图术语（label 显示 / hint 保留原功能名 / code 为英文代号）========
const NAV: Record<string, any[]> = {
  student: [
    { page: 'dashboard', icon: 'home', label: '学习总览', hint: '首页', code: 'HOME', group: '概览' },
    { page: 'courses', icon: 'grid', label: '课程目录', hint: '课程浏览', code: 'CRS', group: '选课中心' },
    { page: 'intents', icon: 'cart', label: '选课意向', hint: '我的意向', code: 'INT', group: '选课中心' },
    { page: 'my-enrollments', icon: 'clipboard', label: '我的课程', hint: '我的课程', code: 'MY', group: '选课中心' },
    { page: 'waitlist', icon: 'hourglass', label: '候补队列', hint: '候补队列', code: 'W/L', group: '选课中心' },
    { page: 'schedule', icon: 'calendar', label: '课表日程', hint: '我的课表', code: 'SCH', group: '学业记录' },
    { page: 'grades', icon: 'award', label: '成绩档案', hint: '成绩查询', code: 'REC', group: '学业记录' },
    { page: 'reviews', icon: 'message-square', label: '课程评价', hint: '课程评价', code: 'EVL', group: '学业记录' },
    { page: 'graduation', icon: 'target', label: '毕业进度', hint: '毕业进度', code: 'PRO', group: '学业记录' },
    { page: 'suggestions', icon: 'inbox', label: '意见反馈', hint: '建议箱', code: 'MSG', group: '学业记录' },
    { page: 'advisor-apply', icon: 'graduation-cap', label: '导师匹配', hint: '毕业设计导师', code: 'ADV', group: '课题与毕业' },
    { page: 'workspace', icon: 'folder', label: '课题工作台', hint: '毕业设计工作台', code: 'OPS', group: '课题与毕业' },
    { page: 'chat', icon: 'sparkles', label: 'AI 学业助手', hint: '智能导航 / AI 助手', code: 'AI', group: '智能助手' },
  ],
  teacher: [
    { page: 'dashboard', icon: 'home', label: '学习总览', hint: '首页', code: 'HOME', group: '概览' },
    { page: 'teach-courses', icon: 'book-open', label: '授课课程', hint: '我的课程', code: 'CRS', group: '教学管理' },
    { page: 'student-list', icon: 'users', label: '学生名册', hint: '选课学生', code: 'RSTR', group: '教学管理' },
    { page: 'drop-requests', icon: 'inbox', label: '退课申请', hint: '退课审批', code: 'DRP', group: '教学管理' },
    { page: 'grade-mgr', icon: 'edit', label: '成绩录入', hint: '成绩管理', code: 'REC', group: '教学管理' },
    { page: 'reviews', icon: 'message-square', label: '课程评价', hint: '课程评价', code: 'EVL', group: '教学管理' },
    { page: 'advise', icon: 'users', label: '课题指导', hint: '毕业设计指导', code: 'ADV', group: '课题与毕业' },
    { page: 'advisor-apply', icon: 'book-open', label: '课题档案', hint: '课题库', code: 'ARCV', group: '课题与毕业' },
    { page: 'workspace', icon: 'folder', label: '课题工作台', hint: '毕业设计工作台', code: 'OPS', group: '课题与毕业' },
    { page: 'chat', icon: 'sparkles', label: 'AI 学业助手', hint: '智能导航 / AI 助手', code: 'AI', group: '智能助手' },
  ],
  admin: [
    { page: 'dashboard', icon: 'home', label: '学习总览', hint: '首页', code: 'HOME', group: '概览' },
    { page: 'rounds', icon: 'refresh', label: '选课轮次', hint: '选课轮次', code: 'RND', group: '教务管理' },
    { page: 'semesters', icon: 'calendar', label: '学期设置', hint: '学期管理', code: 'TERM', group: '教务管理' },
    { page: 'classrooms', icon: 'building', label: '教室管理', hint: '教室管理', code: 'FAC', group: '教务管理' },
    { page: 'course-mgr', icon: 'book', label: '课程总库', hint: '课程管理', code: 'DB', group: '基础数据' },
    { page: 'user-mgr', icon: 'users', label: '用户管理', hint: '用户管理', code: 'ID', group: '基础数据' },
    { page: 'announce', icon: 'megaphone', label: '公告管理', hint: '公告管理', code: 'BC', group: '基础数据' },
    { page: 'ai-channels', icon: 'sparkles', label: 'AI 通道', hint: 'AI 通道配置', code: 'CHN', group: '基础数据' },
    { page: 'suggestions', icon: 'inbox', label: '意见反馈', hint: '建议箱', code: 'MSG', group: '基础数据' },
    { page: 'stats', icon: 'bar-chart', label: '统计报表', hint: '统计报表', code: 'ANL', group: '数据审计' },
    { page: 'graduation-audit', icon: 'graduation-cap', label: '毕业审核', hint: '毕业审核', code: 'AUD', group: '数据审计' },
    { page: 'advise-mgr', icon: 'graduation-cap', label: '导师管理', hint: '导师管理', code: 'ADV', group: '数据审计' },
    { page: 'logs', icon: 'file-text', label: '操作日志', hint: '系统日志', code: 'LOG', group: '数据审计' },
    { page: 'chat', icon: 'sparkles', label: 'AI 学业助手', hint: '智能导航 / AI 助手', code: 'AI', group: '智能助手' },
  ],
}
/** 给每项补上分组起始标记与两位小节号，收起态只保留星点 */
const navItems = computed(() => {
  const list = NAV[auth.user?.role || 'student'] || NAV.student
  let idx = -1
  let last = ''
  return list.map(item => {
    let groupStart = false
    if (item.group !== last) { idx++; groupStart = true; last = item.group }
    return { ...item, groupStart, groupNo: String(idx + 1).padStart(2, '0') }
  })
})

// ======== 顶栏星图读数：实时时钟 + 当前页小节号 ========
const clockText = ref('')
let clockTimer: number | null = null
function tickClock() {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  clockText.value = `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}
/** 跨角色兜底标题：管理员偶尔会打开学生专属页、公告板等不在本角色菜单里的路由 */
const PAGE_TITLES: Record<string, string> = {
  dashboard: '学习总览', courses: '课程目录', intents: '选课意向', 'my-enrollments': '我的课程',
  waitlist: '候补队列', schedule: '课表日程', grades: '成绩档案', reviews: '课程评价',
  graduation: '毕业进度', suggestions: '意见反馈', 'advisor-apply': '导师匹配', workspace: '课题工作台',
  chat: 'AI 学业助手', 'teach-courses': '授课课程', 'student-list': '学生名册', 'grade-mgr': '成绩录入',
  advise: '课题指导', 'drop-requests': '退课申请', rounds: '选课轮次', semesters: '学期设置', classrooms: '教室管理',
  'course-mgr': '课程总库', 'user-mgr': '用户管理', announce: '公告管理', stats: '统计报表',
  'advise-mgr': '导师管理', logs: '操作日志', 'graduation-audit': '毕业审核',
  'training-plans': '培养方案', profile: '个人档案', announcements: '公告列表',
  'ai-channels': 'AI 通道配置',
}
/** /student/dashboard → dashboard（否则第一段是角色名，匹配不到菜单） */
const currentPage = computed(() => {
  const seg = route.path.replace(/^\//, '').split('/')
  return ['student', 'teacher', 'admin'].includes(seg[0]) ? (seg[1] || 'dashboard') : seg[0]
})
const currentNo = computed(() => {
  const item = [...navItems.value].reverse().find(n => n.page === currentPage.value)
  return item?.groupNo ? `学域 ${item.groupNo} · ` : ''
})

// ======== 原版工具 ========
const svgIcon = (name: string) => `<svg class="icon"><use href="#i-${name}"/></svg>`
const userAvatar = computed(() => {
  const map: Record<string, string> = { student: '🎓', teacher: '👨‍🏫', admin: '👑' }
  const role = auth.user?.role || 'student'
  const a = auth.user?.avatar || ''
  // 预设头像是纯文本 emoji，只有真实图片地址才渲染 img，否则会请求 /👤 导致 500 与裂图
  if (/^(data:image\/|https?:\/\/|\/)/i.test(a)) {
    const safe = a.replace(/"/g, '&quot;')
    return `<img src="${safe}" style="width:100%;height:100%;border-radius:50%;object-fit:cover"/>`
  }
  if (a) return a.replace(/[&<>]/g, '')
  return map[role] || '👤'
})

// ======== 通知中心（SSE 实时推送 + 轮询兜底 + 公告未读横幅）========
const notifList = ref<any[]>([])
const showNotif = ref(false)
const notifLoading = ref(false)
const msgUnread = ref(0)        // 站内消息未读数
const annUnread = ref(0)        // 公告未读数（横幅展示，点击跳公告页）
const unreadTotal = computed(() => msgUnread.value + annUnread.value)
const notifFilter = ref<'all' | 'unread'>('all')
const sseConnected = ref(false)
const notifPanelEl = ref<HTMLElement | null>(null)
let notifTimer: number | null = null
let es: EventSource | null = null
let sseTokenAtConnect = ''
let refreshTimer: number | null = null   // SSE 到达后的防抖刷新

function notifyAllowed() {
  return !!(auth.token || localStorage.getItem('zhixue_token'))
}

const filteredNotif = computed(() =>
  notifFilter.value === 'unread' ? notifList.value.filter(n => !n.read) : notifList.value)

async function loadNotifications(silent = false) {
  if (!notifyAllowed()) return
  if (!silent) notifLoading.value = true
  try {
    const [m, u]: any[] = await Promise.all([
      http.get('/notify/messages').catch(() => null),
      http.get('/notify/unread-count').catch(() => null),
    ])
    if (m?.data) notifList.value = m.data
    msgUnread.value = Number(u?.data?.count) || 0
    annUnread.value = Number(u?.data?.announcement) || 0
  } catch {}
  notifLoading.value = false
}

/** SSE 事件到达 → 1s 防抖后静默刷新（高频事件不放大请求量） */
function scheduleSilentRefresh() {
  if (refreshTimer) return
  refreshTimer = window.setTimeout(() => {
    refreshTimer = null
    loadNotifications(true)
  }, 1000)
}

function connectSSE() {
  const token = (auth.token || localStorage.getItem('zhixue_token') || '') as string
  if (!token) return
  // token 变化（重新登录）时重建连接
  if (es && sseTokenAtConnect === token) return
  if (es) { es.close(); es = null }
  try {
    es = new EventSource(`/api/notify/subscribe?token=${encodeURIComponent(token)}`)
  } catch { return }
  sseTokenAtConnect = token
  es.onopen = () => { sseConnected.value = true }
  es.onerror = () => { sseConnected.value = false }  // EventSource 自动重连，轮询持续兜底
  es.addEventListener('msg', () => scheduleSilentRefresh())   // 站内消息 / 公告广播
}

function toggleNotif() {
  showNotif.value = !showNotif.value
  if (showNotif.value) { notifFilter.value = 'all'; loadNotifications() }
}

function closeNotif() { showNotif.value = false }

async function readOne(n: any) {
  if (n?.read) return
  try {
    await http.post(`/notify/read/${n.id}`)
    n.read = true
    msgUnread.value = Math.max(0, msgUnread.value - 1)
  } catch {}
}

/** 点击通知：标记已读 + 按 type 跳转对应业务页 */
const TYPE_ROUTE: Record<string, string> = {
  enroll: '/my-enrollments',
  waitlist_confirm: '/waitlist',
  waitlist_timeout: '/waitlist',
  drop_request: '/drop-requests',
  drop_approved: '/my-enrollments',
  drop_rejected: '/my-enrollments',
}
async function openItem(n: any) {
  await readOne(n)
  const target = TYPE_ROUTE[n.type]
  closeNotif()
  if (target) router.push(target)
}

// ======== 公告弹窗：点通知横幅直接查看，不做任何路由跳转 ========
const annDialog = ref(false)
const annItems = ref<any[]>([])
const annLoading = ref(false)
const annBusy = ref<number | null>(null)   // 正在标记的公告 id；-1 = 全部已读
const annUnreadCount = computed(() => annItems.value.filter(a => !a.read).length)

function formatAnnTime(v: any) {
  if (!v) return ''
  const d = new Date(v), p = (n: number) => String(n).padStart(2, '0')
  return `${d.getMonth() + 1}-${d.getDate()} ${p(d.getHours())}:${p(d.getMinutes())}`
}

async function openAnnDialog() {
  annDialog.value = true
  annLoading.value = true
  try {
    const r: any = await http.get('/announcements')
    const list = (r?.data || []) as any[]
    // 未读在前，其次置顶、发布时间倒序
    annItems.value = list.sort((a, b) =>
      (Number(!!a.read) - Number(!!b.read)) ||
      (Number(!!b.pinned) - Number(!!a.pinned)) ||
      ((b.createdAt || 0) - (a.createdAt || 0)))
  } catch (e: any) {
    annItems.value = []
    ElMessage.error(e?.message || t('公告加载失败'))
  }
  annLoading.value = false
}

async function markAnnRead(a: any) {
  if (a.read || annBusy.value !== null) return
  annBusy.value = a.id
  try {
    await http.post(`/announcements/${a.id}/read`)
    a.read = true
    annUnread.value = Math.max(0, annUnread.value - 1)
    await loadNotifications(true)   // 未读数以服务端为准
  } catch (e: any) {
    ElMessage.error(e?.message || t('标记失败'))
  }
  annBusy.value = null
}

async function markAnnAllRead() {
  if (!annUnreadCount.value || annBusy.value !== null) return
  annBusy.value = -1
  try {
    await http.post('/announcements/read-all')
    annItems.value.forEach(a => { a.read = true })
    annUnread.value = 0
    await loadNotifications(true)
  } catch (e: any) {
    ElMessage.error(e?.message || t('标记失败'))
  }
  annBusy.value = null
}

function goAnnounce() {
  closeNotif()
  openAnnDialog()   // 不再跳 /announcements，也不跳管理员端 /announce
}

/** 全部已读：站内消息 + 公告一起清（服务端为准，静默刷新） */
async function markAllRead() {
  try {
    await Promise.all([
      http.post('/notify/read-all'),
      http.post('/announcements/read-all').catch(() => null),
    ])
    await loadNotifications(true)
  } catch {}
}

function connectNotify() {
  loadNotifications()
  connectSSE()
  if (notifTimer) clearInterval(notifTimer)
  // 25s 轮询兜底：SSE 已连接时仅作保险；页面隐藏时跳过请求
  notifTimer = setInterval(() => {
    if (!document.hidden) loadNotifications(true)
  }, 25000) as any
  document.addEventListener('visibilitychange', onVisChange)
  document.addEventListener('click', onDocClick, true)
  document.addEventListener('keydown', onEscKey)
}
function disconnectNotify() {
  if (notifTimer) { clearInterval(notifTimer); notifTimer = null }
  if (refreshTimer) { clearTimeout(refreshTimer); refreshTimer = null }
  if (es) { es.close(); es = null; sseConnected.value = false }
  document.removeEventListener('visibilitychange', onVisChange)
  document.removeEventListener('click', onDocClick, true)
  document.removeEventListener('keydown', onEscKey)
}
function onVisChange() {
  if (!document.hidden && notifyAllowed()) loadNotifications(true)
}
function onDocClick(e: Event) {
  const t = e.target as HTMLElement
  if (showNotif.value && !t.closest('.notif-panel') && !t.closest('.notif-trigger')) closeNotif()
  if (showUserMenu.value && !t.closest('.user-menu-anchor')) showUserMenu.value = false
}
function onEscKey(e: KeyboardEvent) {
  if (e.key !== 'Escape') return
  if (showNotif.value) closeNotif()
  showUserMenu.value = false
}

// ======== 状态 ========
// 默认展开：主题化后的菜单名需要可见；用户点箭头收起后记住选择
const pinned = ref(localStorage.getItem('zhixue_sidebar_pinned') !== '0')
const hovering = ref(false)        // 悬停/键盘焦点临时展开
const mobileOpen = ref(false)      // 移动端抽屉
// AI 助手页自带一层导航：外层侧栏收成 72px 窄轨，避免双层侧栏把内容挤窄
// （窄轨上图标仍可点击，导航不丢失；悬停时以覆盖层形式临时展开，不推挤内容）
const chatMode = computed(() => route.path.replace(/\/+$/, '') === '/chat')
const expanded = computed(() =>
  chatMode.value
    ? (hovering.value || mobileOpen.value)   // AI 页：仅悬停/移动端展开（pinned 不生效，防双层侧栏挤压）
    : (pinned.value || hovering.value || mobileOpen.value))

// ======== 左下角个人菜单（头像点击弹出：个人中心/主题切换/通知中心）========
const showUserMenu = ref(false)
function toggleUserMenu() {
  showUserMenu.value = !showUserMenu.value
  if (showUserMenu.value) closeNotif()
}
function openProfile() {
  showUserMenu.value = false
  showProfile.value = true
}
function onMenuToggleTheme(e: MouseEvent) {
  showUserMenu.value = false
  toggleTheme(e)
}
/** 菜单内切换中文/English */
function onMenuToggleLang() {
  toggleLocale()
}
/** 菜单内的「通知中心」：关菜单、开通知面板（同为头像上方弹出） */
function openNotifPanel() {
  showUserMenu.value = false
  toggleNotif()
}

// 鼠标快速进出防闪烁：收起延迟 120ms，进入即取消
let collapseTimer: number | null = null
const isMobile = () => window.matchMedia('(max-width: 480px)').matches
function sideEnter() {
  if (isMobile()) return
  if (collapseTimer) { clearTimeout(collapseTimer); collapseTimer = null }
  hovering.value = true
}
function sideLeave() {
  if (isMobile()) return
  if (collapseTimer) clearTimeout(collapseTimer)
  collapseTimer = window.setTimeout(() => { hovering.value = false; collapseTimer = null }, 120)
}
function togglePinned() {
  pinned.value = !pinned.value
  localStorage.setItem('zhixue_sidebar_pinned', pinned.value ? '1' : '0')
}

const theme = ref<'light' | 'dark'>((localStorage.getItem('zhixue_theme') as any) || 'dark')
const showProfile = ref(false)

// 透明顶栏：滚动后渐变为磨砂玻璃
const scrolled = ref(false)
function onWindowScroll() { scrolled.value = window.scrollY > 6 }

// ======== 内容区滚动进度条 ========
const scrollPct = ref(0)
function onContentScroll() {
  const el = contentEl.value
  if (!el) { scrollPct.value = 0; return }
  const max = el.scrollHeight - el.clientHeight
  scrollPct.value = max > 4 ? Math.min(100, (el.scrollTop / max) * 100) : 0
}

// ======== 路由跳转 ========
function go(page: string) {
  if (page === 'dashboard') { router.push(homePath(auth.user?.role)); return }
  router.push('/' + page)
}
const currentTitle = computed(() => {
  const item = [...navItems.value].reverse().find(n => n.page === currentPage.value)
  return t(item?.label || PAGE_TITLES[currentPage.value] || '学习总览')
})

// 切换页面时主内容区回到顶部
const contentEl = ref<HTMLElement | null>(null)
watch(() => route.path, () => {
  nextTick(() => {
    const c = contentEl.value || document.querySelector('.content')
    if (c) (c as HTMLElement).scrollTo({ top: 0, behavior: 'auto' })
  })
})

// ======== 主题切换（View Transitions 圆形扩散，降级为直接切换）========
function applyTheme(t: string) {
  theme.value = t as any
  localStorage.setItem('zhixue_theme', t)
  document.documentElement.setAttribute('data-theme', t)
}
function toggleTheme(e: MouseEvent) {
  const next = theme.value === 'dark' ? 'light' : 'dark'
  const doc = document as any
  if (!doc.startViewTransition || window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    applyTheme(next); return
  }
  const x = e.clientX, y = e.clientY
  const vt = doc.startViewTransition(() => { applyTheme(next) })
  vt.ready.then(() => {
    const r = Math.hypot(Math.max(x, window.innerWidth - x), Math.max(y, window.innerHeight - y))
    document.documentElement.animate(
      { clipPath: [`circle(0px at ${x}px ${y}px)`, `circle(${r}px at ${x}px ${y}px)`] },
      { duration: 480, easing: 'ease-in-out', pseudoElement: '::view-transition-new(root)' }
    )
  }).catch(() => {})
}

/** 相对时间：刚刚 / N分钟前 / 今天 HH:MM / 昨天 HH:MM / M-D HH:MM */
function formatTime(t_: any) {
  if (!t_) return ''
  const d = new Date(t_), now = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  const hm = `${p(d.getHours())}:${p(d.getMinutes())}`
  const diff = now.getTime() - d.getTime()
  if (diff < 60_000) return t('刚刚')
  if (diff < 3_600_000) return t('{n} 分钟前', { n: Math.floor(diff / 60_000) })
  const dayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  if (d.getTime() >= dayStart) return t('今天 {t}', { t: hm })
  if (d.getTime() >= dayStart - 86_400_000) return t('昨天 {t}', { t: hm })
  return `${d.getMonth() + 1}-${d.getDate()} ${hm}`
}
function fullTime(t: any) {
  if (!t) return ''
  const d = new Date(t)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

// ======== 登出 ========
async function logout() {
  try {
    await ElMessageBox.confirm(t('确定退出登录？'), t('提示'), {
      type: 'warning',
      confirmButtonText: t('确定'),
      cancelButtonText: t('取消'),
    })
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
  tickClock()
  clockTimer = window.setInterval(tickClock, 1000)
  connectNotify()
  window.addEventListener('scroll', onWindowScroll, { passive: true })
  contentEl.value?.addEventListener('scroll', onContentScroll, { passive: true })
  onWindowScroll()
  onContentScroll()
})
onBeforeUnmount(() => {
  disconnectNotify()
  if (clockTimer) { clearInterval(clockTimer); clockTimer = null }
  if (collapseTimer) { clearTimeout(collapseTimer); collapseTimer = null }
  window.removeEventListener('scroll', onWindowScroll)
  contentEl.value?.removeEventListener('scroll', onContentScroll)
})
</script>

<style scoped>
.app { display: flex; min-height: 100vh; }
.icon { display: inline-block; width: 18px; height: 18px; }
.icon svg { width: 100%; height: 100%; }

/* ===== 学域星图：侧栏标识 / 顶栏读数 ===== */
.sidebar-logo .logo-text { display: flex; flex-direction: column; gap: 3px; }
.logo-sub {
  font-family: var(--ark-mono); font-size: 8.5px; font-weight: 500;
  letter-spacing: .12em; color: var(--text-light); white-space: nowrap;
}
.ark-hazard { flex: 0 0 auto; }
.pt-no {
  display: inline-block; margin-right: 10px;
  font-family: var(--ark-mono); font-size: 10px; letter-spacing: .1em;
  color: var(--ark-y, #74e4c4); opacity: .85; vertical-align: 2px;
}
.ark-top-reading { margin-left: auto; }
@media (max-width: 900px) { .ark-top-reading .ark-top-sep, .ark-top-reading > span:nth-child(4) { display: none; } }
@media (max-width: 640px) { .ark-top-reading { display: none; } }

/* 顶栏左侧：移动端汉堡 + 页面标题 */
.topbar-left { display: flex; align-items: center; gap: 10px; min-width: 0; }
.mobile-menu-btn { display: none; }

/* 内容区滚动进度条 */
.scroll-progress {
  position: sticky; top: 0; z-index: 30;
  height: 3px; width: 0; margin-bottom: -3px;
  background: linear-gradient(90deg, var(--primary), #38bdf8, #22d3ee);
  border-radius: 0 99px 99px 0;
  transition: width .12s linear;
  pointer-events: none;
}

/* 侧边栏底部用户区：沉底 + 收起态适配 */
.side-user-box {
  display: flex; align-items: center;
  margin-top: auto; padding: 12px; border-top: 1px solid var(--border);
}
.user-menu-anchor { position: relative; flex: 1; min-width: 0; }
.side-user:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; }
.side-user {
  flex: 1; min-width: 0; width: 100%; display: flex; align-items: center; gap: 9px;
  padding: 7px 9px; border-radius: 12px; cursor: pointer;
  background: rgba(255,255,255,0.62); border: 1px solid var(--border);
  font: inherit; color: inherit; text-align: left;
  transition: background .2s ease-out, border-color .2s ease-out;
}
.side-user:hover, .side-user.menu-open { background: rgba(43,177,135,0.10); border-color: rgba(43,177,135,0.42); }
.side-user:active { background: rgba(43,177,135,0.16); }
.side-user-chevron {
  width: 14px; height: 14px; margin-left: auto; flex: 0 0 auto;
  color: var(--text-light); opacity: .7;
  transition: transform .22s cubic-bezier(.22,.9,.36,1), color .15s;
}
.side-user.menu-open .side-user-chevron { transform: rotate(180deg); color: var(--primary); }
.side-avatar {
  width: 34px; height: 34px; flex: 0 0 auto; border-radius: 50%; overflow: hidden;
  display: flex; align-items: center; justify-content: center; font-size: 16px;
  background: linear-gradient(135deg, rgba(43,177,135,0.9), rgba(56,189,248,0.85)); color: #fff;
}
.side-avatar :deep(img) { width: 100%; height: 100%; border-radius: 50%; object-fit: cover; }
.side-user-detail { display: flex; flex-direction: column; min-width: 0; line-height: 1.25; }
.side-user-name {
  font-size: 13px; font-weight: 700; color: var(--text);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.side-user-meta { font-size: 11.5px; color: var(--text-light); }

/* 个人菜单：头像上方弹出（个人中心 / 主题切换 / 退出登录） */
.user-menu {
  position: absolute; bottom: calc(100% + 10px); left: 0; z-index: 60;
  width: 208px; padding: 6px;
  background: var(--card); border: 1px solid var(--border);
  border-radius: 14px;
  box-shadow: 0 16px 44px rgba(6, 24, 18, 0.16), 0 4px 12px rgba(6, 24, 18, 0.08);
  animation: umIn .18s cubic-bezier(.22,.9,.36,1);
  transform-origin: bottom left;
}
@keyframes umIn { from { opacity: 0; transform: translateY(6px) scale(.97); } to { opacity: 1; transform: none; } }
.um-item {
  display: flex; align-items: center; gap: 10px; width: 100%;
  padding: 9px 11px; border: none; border-radius: 9px; cursor: pointer;
  background: transparent; font: inherit; font-size: 13px; color: var(--text);
  text-align: left; transition: background .14s, color .14s;
}
.um-item .icon { width: 16px; height: 16px; color: var(--text-light); transition: color .14s; }
.um-item:hover { background: rgba(43,177,135,0.10); color: var(--primary); }
.um-item:hover .icon { color: var(--primary); }
.um-badge {
  margin-left: auto; min-width: 18px; height: 18px; padding: 0 5px;
  display: inline-flex; align-items: center; justify-content: center;
  background: #ef4444; color: #fff; font-size: 10.5px; font-weight: 700;
  border-radius: 99px; font-style: normal; font-variant-numeric: tabular-nums;
}
/* 有未读时头像右上角小红点（菜单收起时也能注意到新消息） */
.side-user { position: relative; }
.side-user.has-unread::after {
  content: ''; position: absolute; top: 4px; left: 36px;
  width: 9px; height: 9px; border-radius: 50%;
  background: #ef4444; border: 2px solid var(--card);
  animation: umDot 2s ease infinite;
}
@keyframes umDot { 0%,100% { opacity: 1; } 50% { opacity: .45; } }
.sidebar.collapsed .side-user.has-unread::after { left: 24px; }
.um-danger:hover { background: rgba(220,38,38,0.08); color: #dc2626; }
.um-danger:hover .icon { color: #dc2626; }
.um-divider { height: 1px; margin: 5px 8px; background: var(--border); }

/* 收起态：头像卡片纵向排列，菜单从头像位置弹出 */
.sidebar.collapsed .side-user-box { padding: 10px 8px; flex-direction: column; gap: 8px; }
.sidebar.collapsed .side-user {
  flex: 0 0 auto; padding: 3px; justify-content: center;
  background: transparent; border-color: transparent;
}
.sidebar.collapsed .side-user:hover,
.sidebar.collapsed .side-user.menu-open { background: rgba(43,177,135,0.10); border-color: rgba(43,177,135,0.42); }
.sidebar.collapsed .side-user-detail { display: none; }
.sidebar.collapsed .side-user-chevron { display: none; }

/* 文字随展开淡入 */
.side-user-detail { opacity: 1; transition: opacity .2s ease; }
.sidebar.collapsed .side-user-detail { opacity: 0; }

/* 通知面板 */
.notif-panel {
  position: absolute; bottom: calc(100% + 10px); left: 0; width: 340px; max-height: 420px;
  background: var(--card); border: 1px solid var(--border, #334155);
  border-radius: 14px; box-shadow: 0 16px 44px rgba(6,24,18,0.16), 0 4px 12px rgba(6,24,18,0.08);
  z-index: 100; overflow: hidden; display: flex; flex-direction: column;
  animation: umIn .18s cubic-bezier(.22,.9,.36,1); transform-origin: bottom left;
}
.notif-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 10px 14px; font-size: 13px; font-weight: 600; border-bottom: 1px solid var(--border);
}
.notif-clear { font-weight: 400; font-size: 12px; color: var(--primary, #4ade80); cursor: pointer; }
.notif-clear:hover { opacity: 0.8; }
/* 未读筛选 Tab + 实时状态 */
.notif-tabs {
  display: flex; align-items: center; gap: 6px;
  padding: 8px 14px 4px;
}
.notif-tab {
  padding: 3px 10px; border-radius: 99px; cursor: pointer;
  background: transparent; border: 1px solid var(--border); color: var(--text-light);
  font-size: 12px; transition: all .15s; display: inline-flex; align-items: center; gap: 4px;
}
.notif-tab em {
  font-style: normal; font-size: 10.5px; line-height: 1;
  background: #ef4444; color: #fff; border-radius: 99px; padding: 2px 5px;
}
.notif-tab:hover { color: var(--text); border-color: rgba(43,177,135,.4); }
.notif-tab.on { background: rgba(43,177,135,.12); border-color: rgba(43,177,135,.45); color: var(--primary); font-weight: 600; }
.notif-live {
  margin-left: auto; display: inline-flex; align-items: center; gap: 5px;
  font-size: 11px; color: var(--text-light);
}
.notif-live i {
  width: 7px; height: 7px; border-radius: 50%; background: #94a3b8; transition: background .2s;
}
.notif-live.on { color: var(--primary); }
.notif-live.on i { background: #22c55e; box-shadow: 0 0 0 3px rgba(34,197,94,.18); }
/* 公告未读横幅 */
.notif-ann {
  margin: 8px 14px 0; padding: 9px 12px; border-radius: 10px; cursor: pointer;
  background: linear-gradient(135deg, rgba(43,177,135,.12), rgba(56,189,248,.08));
  border: 1px solid rgba(43,177,135,.35);
  color: var(--green-text, #15803d); font-size: 12.5px; font-weight: 600;
  transition: background .15s, border-color .15s;
}
.notif-ann:hover { background: linear-gradient(135deg, rgba(43,177,135,.2), rgba(56,189,248,.13)); border-color: rgba(43,177,135,.55); }
[data-theme="dark"] .notif-ann { color: #4cc9a3; }
.notif-list { overflow-y: auto; flex: 1; }
.notif-item { padding: 10px 14px; border-bottom: 1px solid var(--border, #334155); cursor: pointer; }
.notif-item:hover { background: rgba(255,255,255,0.03); }
.notif-item.unread { background: rgba(43,177,135,0.07); }
.notif-item.unread .notif-title::before {
  content: ''; display: inline-block; width: 6px; height: 6px; border-radius: 50%;
  background: #ef4444; margin-right: 6px; vertical-align: middle;
}
.notif-title { font-size: 13px; font-weight: 600; color: var(--text); line-height: 1.4; }
.notif-text { font-size: 12px; color: var(--text-light); line-height: 1.5; margin-top: 2px; }
.notif-time { font-size: 12px; color: var(--text-light); margin-top: 4px; }
.notif-empty { padding: 40px; text-align: center; font-size: 13px; color: var(--text-light); }
.notif-loading { padding: 36px; text-align: center; font-size: 12.5px; color: var(--text-light); }
.notif-unread { font-style: normal; font-weight: 400; color: var(--text-light); font-size: 12px; }
.notif-dot {
  position: absolute; top: -2px; right: -4px; min-width: 16px; height: 16px; padding: 0 4px;
  display: flex; align-items: center; justify-content: center;
  border-radius: 99px; background: #ef4444; border: 2px solid var(--card-bg);
  color: #fff; font-size: 12px; font-weight: 700; line-height: 1;
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

<!-- el-dialog 挂到 body 下，scoped 样式不覆盖，故单独写全局样式 -->
<style>
.ann-dialog.el-dialog,
.ann-dialog .el-dialog {
  background: var(--card, #fff);
  border: 1px solid var(--border, #e2e8f0);
  border-radius: 16px;
  box-shadow: 0 24px 64px rgba(6, 24, 18, .22);
  overflow: hidden;
}
.ann-dialog .el-dialog__header {
  margin: 0; padding: 16px 20px 12px;
  border-bottom: 1px solid var(--border, #e2e8f0);
}
.ann-dialog .el-dialog__body {
  padding: 14px 20px; max-height: min(60vh, 520px); overflow-y: auto;
}
.ann-dialog .el-dialog__footer {
  padding: 12px 20px 16px; border-top: 1px solid var(--border, #e2e8f0);
}
.ann-head {
  display: flex; align-items: baseline; gap: 10px; flex-wrap: wrap; padding-right: 26px;
}
.ann-head-title { font-size: 15px; font-weight: 700; color: var(--text, #1e293b); }
.ann-head-sub { font-size: 12px; color: var(--text-light, #64748b); }
.ann-state { padding: 36px 0; text-align: center; font-size: 13px; color: var(--text-light, #64748b); }
.ann-list { display: flex; flex-direction: column; gap: 10px; }
.ann-card {
  padding: 12px 14px; border-radius: 12px;
  background: var(--card-bg, rgba(255,255,255,.5));
  border: 1px solid var(--border, #e2e8f0);
}
.ann-card.unread { background: rgba(43,177,135,.07); border-color: rgba(43,177,135,.38); }
.ann-card-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.ann-dot { width: 7px; height: 7px; flex: 0 0 auto; border-radius: 50%; background: #ef4444; }
.ann-chip {
  padding: 1px 8px; border-radius: 99px; font-size: 11px; line-height: 1.6;
  color: var(--primary, #15803d); background: rgba(43,177,135,.12);
  border: 1px solid rgba(43,177,135,.30);
}
.ann-chip.pin { color: #b45309; background: rgba(245,158,11,.12); border-color: rgba(245,158,11,.30); }
.ann-card-title { font-size: 13.5px; font-weight: 700; color: var(--text, #1e293b); }
.ann-card-content {
  margin-top: 7px; font-size: 12.5px; line-height: 1.65;
  white-space: pre-wrap; word-break: break-word; color: var(--text-light, #475569);
}
.ann-card-foot { display: flex; align-items: center; gap: 10px; margin-top: 10px; }
.ann-meta { font-size: 11.5px; color: var(--text-light, #64748b); }
.ann-read-btn, .ann-all-btn, .ann-close-btn {
  padding: 5px 12px; border-radius: 8px; cursor: pointer;
  font: inherit; font-size: 12px; font-weight: 600; transition: all .15s;
}
.ann-read-btn { margin-left: auto; color: #fff; background: var(--primary, #2bb187); border: 1px solid var(--primary, #2bb187); }
.ann-all-btn { color: #fff; background: var(--primary, #2bb187); border: 1px solid var(--primary, #2bb187); }
.ann-read-btn:hover, .ann-all-btn:hover { filter: brightness(1.08); }
.ann-read-btn:disabled, .ann-all-btn:disabled { opacity: .45; cursor: not-allowed; }
.ann-read-done { margin-left: auto; font-size: 12px; color: var(--primary, #2bb187); }
.ann-foot { display: flex; align-items: center; gap: 10px; }
.ann-close-btn {
  color: var(--text, #1e293b); background: transparent; border: 1px solid var(--border, #e2e8f0);
}
.ann-close-btn:hover { border-color: var(--primary, #2bb187); color: var(--primary, #2bb187); }
[data-theme="dark"] .ann-dialog .el-dialog__headerbtn .el-dialog__close { color: var(--text-light, #94a3b8); }
@media (max-width: 480px) {
  .ann-dialog .el-dialog__body { padding: 12px 14px; }
  .ann-dialog .el-dialog__header, .ann-dialog .el-dialog__footer { padding-left: 14px; padding-right: 14px; }
}
</style>
