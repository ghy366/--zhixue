<template>
  <div class="page-wrap board-page">
    <div class="section">
      <div class="section-header">
        <div class="section-title" v-html="svgIcon('megaphone') + ' ' + t('公告板（{n} 条）', { n: list.length })"></div>
        <button v-if="unreadCount" class="btn btn-outline btn-sm" @click="readAll">{{ t('全部标为已读') }}</button>
      </div>

      <!-- 类型筛选 + 搜索 -->
      <div class="board-filters">
        <div class="board-tabs">
          <button class="board-tab" :class="{ on: typeFilter === '' }" @click="typeFilter = ''">{{ t('全部') }}</button>
          <button
            v-for="ty in types" :key="ty"
            class="board-tab" :class="{ on: typeFilter === ty }" @click="typeFilter = ty"
          >{{ t(ty) }}</button>
        </div>
        <div class="board-search">
          <span class="icon" v-html="svgIcon('search')" />
          <input v-model="keyword" :placeholder="t('搜索公告标题或内容')" />
        </div>
      </div>

      <div class="board-list">
        <div
          v-for="a in filtered" :key="a.id"
          class="board-item" :class="{ unread: !a.read, open: expandedId === a.id }"
          @click="toggle(a)"
        >
          <div class="bi-head">
            <span v-if="!a.read" class="bi-dot" />
            <span v-if="a.pinned" class="bi-pin">{{ t('📌 置顶') }}</span>
            <span v-if="a.type" class="bi-type">{{ a.type }}</span>
            <span class="bi-title">{{ a.title }}</span>
          </div>
          <div class="bi-content">{{ a.content }}</div>
          <div class="bi-meta">
            <span>{{ a.publisherName || t('管理员') }}</span>
            <span :title="fullTime(a.createdAt)">{{ format(a.createdAt) }}</span>
            <span v-if="!a.read" class="bi-unread-tip">{{ t('点击展开标记已读') }}</span>
          </div>
        </div>
        <div v-if="!filtered.length" class="empty-state">
          <div class="empty-icon" v-html="svgIcon('megaphone')" />
          <div class="empty-title">{{ list.length ? t('没有匹配的公告') : t('暂无公告') }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api'
import { t } from '@/i18n'
const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`

const list = ref<any[]>([])
const typeFilter = ref('')
const keyword = ref('')
const expandedId = ref<number | null>(null)

const types = computed(() => [...new Set(list.value.map(a => a.type).filter(Boolean))] as string[])
const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return list.value.filter(a =>
    (!typeFilter.value || a.type === typeFilter.value) &&
    (!kw || (a.title || '').toLowerCase().includes(kw) || (a.content || '').toLowerCase().includes(kw)))
})
const unreadCount = computed(() => list.value.filter(a => !a.read).length)

/** 相对时间：今天/昨天带 HH:MM，更早带日期 */
function format(v: any) {
  if (!v) return ''
  const d = new Date(v), now = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  const hm = `${p(d.getHours())}:${p(d.getMinutes())}`
  const dayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  if (d.getTime() >= dayStart) return t('今天 {t}', { t: hm })
  if (d.getTime() >= dayStart - 86_400_000) return t('昨天 {t}', { t: hm })
  return `${d.getMonth() + 1}-${d.getDate()} ${hm}`
}
function fullTime(v: any) {
  if (!v) return ''
  const d = new Date(v)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

async function load() {
  try {
    const r: any = await http.get('/announcements')  // 不带 all=1：非管理员只返回未过期公告，并附带个人 read 标记
    list.value = r?.data || []
  } catch {}
}

/** 展开 = 阅读 = 标记已读（再点收起） */
async function toggle(a: any) {
  expandedId.value = expandedId.value === a.id ? null : a.id
  if (expandedId.value === a.id && !a.read) {
    a.read = true
    try { await http.post(`/announcements/${a.id}/read`) } catch {}
  }
}

async function readAll() {
  try {
    await http.post('/announcements/read-all')
    list.value.forEach(a => { a.read = true })
    ElMessage.success(t('公告已全部标为已读'))
  } catch (e: any) { ElMessage.error(e?.message || t('操作失败')) }
}

onMounted(load)
</script>

<style scoped>
.page-wrap { padding: 4px 0; }
.board-filters { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin-bottom: 12px; }
.board-tabs { display: flex; gap: 6px; flex-wrap: wrap; }
.board-tab {
  padding: 4px 12px; border-radius: 99px; cursor: pointer; font-size: 12.5px;
  background: transparent; border: 1px solid var(--border); color: var(--text-light);
  transition: all .15s;
}
.board-tab:hover { color: var(--text); border-color: rgba(43,177,135,.4); }
.board-tab.on { background: rgba(43,177,135,.12); border-color: rgba(43,177,135,.45); color: var(--primary); font-weight: 600; }
.board-search {
  margin-left: auto; display: flex; align-items: center; gap: 6px;
  padding: 5px 12px; border-radius: 99px; border: 1px solid var(--border); background: var(--card-bg);
  min-width: 220px;
}
.board-search .icon { width: 15px; height: 15px; color: var(--text-light); flex: 0 0 auto; }
.board-search input {
  border: none; outline: none; background: transparent; color: var(--text);
  font-size: 13px; width: 100%;
}
.board-list { display: flex; flex-direction: column; gap: 10px; }
.board-item {
  background: var(--card-bg); border: 1px solid var(--border); border-radius: 12px;
  padding: 14px 16px; cursor: pointer; transition: border-color .15s, box-shadow .15s;
}
.board-item:hover { border-color: rgba(43,177,135,.45); box-shadow: 0 2px 10px rgba(15,60,45,.06); }
.board-item.unread { border-left: 3px solid var(--primary); }
.bi-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 6px; }
.bi-dot { width: 7px; height: 7px; border-radius: 50%; background: #ef4444; flex: 0 0 auto; }
.bi-pin { font-size: 11.5px; color: #d97706; background: rgba(245,158,11,.12); border: 1px solid rgba(245,158,11,.35); padding: 1px 8px; border-radius: 99px; flex: 0 0 auto; }
.bi-type {
  font-size: 11.5px; color: var(--primary); background: rgba(43,177,135,.1);
  border: 1px solid rgba(43,177,135,.3); padding: 1px 8px; border-radius: 99px; flex: 0 0 auto;
}
.bi-title { font-weight: 600; font-size: 14px; color: var(--text); }
.bi-content {
  font-size: 13px; color: var(--text-light); line-height: 1.65; white-space: pre-wrap;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
  margin-bottom: 8px;
}
.board-item.open .bi-content { display: block; -webkit-line-clamp: unset; overflow: visible; }
.bi-meta { display: flex; align-items: center; gap: 14px; font-size: 12px; color: var(--text-light); }
.bi-unread-tip { margin-left: auto; color: var(--primary); }
.board-item.open .bi-unread-tip { display: none; }
.empty-state { text-align: center; padding: 60px 20px; color: var(--text-light); }
.empty-icon :deep(svg.icon) { width: 44px; height: 44px; opacity: .3; }
.empty-title { margin-top: 8px; font-size: 13.5px; }
</style>
