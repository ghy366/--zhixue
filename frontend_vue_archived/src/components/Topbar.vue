<template>
  <header class="topbar">
    <div class="page-title">{{ title }}</div>
    <div class="topbar-right">
      <button class="topbar-icon-btn" @click="ui.toggleTheme()" :title="ui.theme === 'dark' ? '切换为浅色' : '切换为深色'">
        <Icon :name="ui.theme === 'dark' ? 'sun' : 'moon'" />
      </button>

      <div class="topbar-drop" ref="notifRef">
        <button class="topbar-icon-btn" @click="toggleNotif()" title="通知中心">
          <Icon name="bell" />
          <span class="notif-dot" v-if="unreadTotal > 0"></span>
        </button>
        <div class="user-dropdown notif-panel" v-show="notifOpen" style="right:0">
          <div class="notif-header">
            <span>通知中心</span>
            <a class="notif-readall" @click="markAllRead">全部已读</a>
          </div>
          <div class="notif-list">
            <div v-if="!notifs.length" class="notif-item">
              <div class="notif-item-title" style="color:var(--text-light)">暂无消息</div>
            </div>
            <div
              v-for="n in notifs"
              :key="n.id"
              class="notif-item"
              :class="{ unread: !n.read }"
              @click="readOne(n)"
            >
              <div class="notif-item-title">{{ n.title }}</div>
              <div class="notif-item-sub" v-if="n.content">{{ n.content }}</div>
              <div class="notif-item-sub">{{ formatDateTime(n.createdAt) }}</div>
            </div>
            <div class="notif-item notif-ann-link" @click="openAnnounceList">
              <div class="notif-item-title">
                <Icon name="megaphone" /> 系统公告
                <span v-if="annUnread > 0" style="color:var(--danger)"> · {{ annUnread }} 条未读</span>
              </div>
              <div class="notif-item-sub">{{ anns[0] ? anns[0].title + ' · 点击查看全部' : '暂无公告' }}</div>
            </div>
          </div>
        </div>
      </div>

      <div class="topbar-drop">
        <div class="user-info" @click="toggleUser()">
          <div class="avatar">
            <img v-if="isImgAvatar" :src="user.avatar" alt="" />
            <template v-else>{{ avatarText }}</template>
          </div>
          <div class="user-detail">
            <span class="user-name">{{ user?.name }}</span>
            <span class="user-meta">{{ roleLabel }} · {{ user?.no }}</span>
          </div>
        </div>
        <div class="user-dropdown" v-show="userOpen" style="right:0">
          <div class="user-dropdown-item" @click="goProfile"><Icon name="user" /> 个人信息</div>
          <div class="user-dropdown-item" @click="goSettings"><Icon name="settings" /> 账号设置</div>
          <div class="user-dropdown-divider"></div>
          <div class="user-dropdown-item" style="color:var(--danger)" @click="logout"><Icon name="log-out" /> 退出登录</div>
        </div>
      </div>
    </div>

    <!-- 全部公告弹窗 -->
    <div class="modal-mask" v-if="annModal" @click.self="annModal = false">
      <div class="modal" style="max-width:640px;text-align:left">
        <div class="modal-header"><h3>系统公告</h3><button class="modal-close" @click="annModal = false">✕</button></div>
        <div class="modal-body" style="max-height:60vh;overflow:auto">
          <div v-if="!allAnns.length" style="color:var(--text-light);font-size:13px">暂无公告</div>
          <div v-for="a in allAnns" :key="a.id" class="announce-card" style="margin-bottom:10px">
            <div class="announce-head"><b>{{ a.title }}</b><span class="status-tag status-approved" style="margin-left:8px">{{ a.type || '系统公告' }}</span></div>
            <div class="announce-body">{{ a.content }}</div>
            <div style="font-size:12px;color:var(--text-light);margin-top:6px">{{ formatDateTime(a.createdAt) }}</div>
          </div>
        </div>
      </div>
    </div>
  </header>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '@/components/Icon.vue'
import { useAuthStore } from '@/store/auth'
import { useUiStore } from '@/store/ui'
import { api, clearAuth, getToken } from '@/api/client'
import { ROLES } from '@/config/nav'
import { toast, formatDateTime } from '@/utils/dom'

const props = defineProps({ title: { type: String, default: '首页' } })
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const ui = useUiStore()

const user = computed(() => auth.user || {})
const roleLabel = computed(() => ROLES[auth.role] || '')
const avatarText = computed(() => (user.value?.name || '👤').slice(0, 1))
const isImgAvatar = computed(() => String(user.value?.avatar || '').startsWith('data:'))
const title = computed(() => route.meta.title || props.title)

const notifOpen = ref(false)
const userOpen = ref(false)
const notifs = ref([])
const anns = ref([])
const allAnns = ref([])
const annUnread = ref(0)
const unreadTotal = ref(0)
const annModal = ref(false)

let es = null

async function loadNotifs() {
  const [mr, ur, ar] = await Promise.all([
    api('GET', '/notify/messages'),
    api('GET', '/notify/unread-count'),
    api('GET', '/announcements?limit=3'),
  ])
  notifs.value = mr.success ? mr.data || [] : []
  anns.value = ar.success ? ar.data || [] : []
  if (ur.success && ur.data) {
    annUnread.value = ur.data.announcement || 0
    unreadTotal.value = (ur.data.count || 0) + (ur.data.announcement || 0)
  }
}

function connectSSE() {
  const token = getToken()
  if (!token || !window.EventSource) return
  try {
    const base = import.meta.env.VITE_API_BASE || ''
    es = new EventSource(base + '/api/notify/subscribe?token=' + encodeURIComponent(token))
    es.addEventListener('msg', (ev) => {
      let d = {}
      try { d = JSON.parse(ev.data) } catch { return }
      toast((d.title ? d.title + '：' : '') + (d.content || '新消息'), d.title && d.title.includes('录取') ? 'success' : 'info')
      loadNotifs()
    })
  } catch { /* ignore */ }
}
function disconnectSSE() { if (es) { try { es.close() } catch { /* ignore */ } es = null } }

function toggleNotif() { notifOpen.value = !notifOpen.value; userOpen.value = false; if (notifOpen.value) loadNotifs() }
function toggleUser() { userOpen.value = !userOpen.value; notifOpen.value = false }

async function readOne(n) {
  if (!n.read) {
    await api('POST', '/notify/read/' + n.id)
    n.read = true
    if (unreadTotal.value > 0) unreadTotal.value--
  }
}
async function markAllRead() {
  await api('POST', '/notify/read-all')
  await api('POST', '/announcements/read-all')
  notifs.value.forEach((n) => (n.read = true))
  annUnread.value = 0
  unreadTotal.value = 0
}
async function openAnnounceList() {
  notifOpen.value = false
  const r = await api('GET', '/announcements')
  allAnns.value = r.success ? r.data || [] : []
  annModal.value = true
  await api('POST', '/announcements/read-all')
  annUnread.value = 0
  unreadTotal.value = Math.max(0, unreadTotal.value - (r.success ? r.data.length : 0))
}

function goProfile() { userOpen.value = false; router.push('/profile') }
function goSettings() { userOpen.value = false; router.push('/profile') }
function logout() {
  api('POST', '/auth/logout').catch(() => {})
  clearAuth()
  auth.logout()
  router.replace('/login')
}

onMounted(() => { loadNotifs(); connectSSE() })
onUnmounted(disconnectSSE)
</script>
