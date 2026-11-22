<template>
  <div class="page-wrap stats-page liquid">
    <!-- ===== 顶部 sticky 导航栏 ===== -->
    <header class="ad-topbar">
      <div class="ad-brand">
        <div class="ad-brand-icon">
          <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 10v6M2 10l10-5 10 5-10 5z"/><path d="M6 12v5c3 3 9 3 12 0v-5"/></svg>
        </div>
        <div class="ad-brand-text">
          <div class="ad-title">{{ t('导师配对管理') }}</div>
          <div class="ad-subtitle">{{ t('共 {n} 位导师 · {c} 个学院', { n: caps.length, c: treeCols.length }) }}</div>
        </div>
      </div>
      <button class="ad-search-trigger" @click="openCommand" :aria-label="t('打开搜索')">
        <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>
        <span class="ad-search-ph">{{ filters.keyword || t('搜索导师 / 工号 / 学院，支持拼音首字母…') }}</span>
        <kbd class="ad-kbd">{{ isMac ? '⌘' : 'Ctrl+' }} K</kbd>
      </button>
    </header>

    <!-- 概览统计 -->
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-icon blue" v-html="svgIcon('users')" />
        <div><div class="stat-value">{{ overview.teacherCount ?? 0 }}</div><div class="stat-label">{{ t('导师总数') }}</div></div>
      </div>
      <div class="stat-card">
        <div class="stat-icon green" v-html="svgIcon('check-circle')" />
        <div><div class="stat-value">{{ overview.totalApproved ?? 0 }}</div><div class="stat-label">{{ t('已确认配对') }}</div></div>
      </div>
      <div class="stat-card">
        <div class="stat-icon amber" v-html="svgIcon('hourglass')" />
        <div><div class="stat-value">{{ overview.totalPending ?? 0 }}</div><div class="stat-label">{{ t('待审批申请') }}</div></div>
      </div>
      <div class="stat-card">
        <div class="stat-icon purple" v-html="svgIcon('star')" />
        <div><div class="stat-value">{{ overview.teachersWithSpace ?? 0 }}</div><div class="stat-label">{{ t('有空位导师') }}</div></div>
      </div>
    </div>

    <!-- 全局配置 -->
    <div class="section">
      <div class="section-header">
        <div class="section-title" v-html="svgIcon('settings') + ' ' + t('全局配置')" />
      </div>
      <div class="cfg-row">
        <label class="cfg-item">
          <el-switch v-model="cfg.open" />
          <span>{{ t('开启导师选择') }}</span>
        </label>
        <label class="cfg-item">
          <span>{{ t('默认容量') }}</span>
          <el-input-number v-model="cfg.defaultCapacity" :min="1" :max="99" size="small" style="width:100px" />
        </label>
        <el-button type="primary" size="small" :loading="cfgSaving" @click="saveCfg">{{ t('保存配置') }}</el-button>
        <span class="cfg-tip">{{ t('新导师未单独设置容量时，使用此默认值') }}</span>
      </div>
    </div>

    <!-- 导师容量管理：左侧学院树 + 右侧范围列表 -->
    <div class="section">
      <div class="section-header">
        <div class="section-title" v-html="svgIcon('graduation-cap') + ' ' + t('导师容量管理')" />
        <span class="um-count-tip">{{ t('共 {n} 位导师 · {c} 个学院', { n: caps.length, c: treeCols.length }) }}</span>
      </div>

      <!-- 筛选轨道 chips：学院 / 状态，读写同一个 filters -->
      <div class="ad-filtercard">
        <div class="ad-chip-row">
          <div v-for="chip in chipDefs" :key="chip.key" class="ad-chip-wrap">
            <button class="ad-chip" :class="{ on: chip.active }" @click="toggleChip(chip.key)">
              <span class="ad-chip-label">{{ chip.label }}</span>
              <span v-if="chip.value" class="ad-chip-value">{{ chip.value }}</span>
              <svg class="ic ad-chip-arrow" :class="{ open: openChip === chip.key }" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="m6 9 6 6 6-6"/></svg>
            </button>
            <div v-if="openChip === chip.key" class="ad-dropdown" @click.stop>
              <button class="ad-opt" :class="{ on: !chip.current }" @click="chip.clear(); openChip = null">
                <span>{{ t('全部') }}{{ chip.label }}</span><span v-if="!chip.current" class="ad-opt-check">✓</span>
              </button>
              <button v-for="o in chip.options" :key="String(o.value)" class="ad-opt" :class="{ on: chip.current === o.value }" @click="chip.set(o.value); openChip = null">
                <span>{{ o.label }}<span v-if="o.count !== undefined" class="ad-opt-n">{{ o.count }}</span></span>
                <span v-if="chip.current === o.value" class="ad-opt-check">✓</span>
              </button>
            </div>
          </div>
          <button v-if="hasFilter" class="ad-reset" @click="clearFilters">{{ t('清空') }}</button>
        </div>
      </div>

      <div class="um-split">
        <aside class="um-tree">
          <div class="um-tree-h"><b>{{ t('按学院检索') }}</b><span>{{ treeCols.length }} {{ t('个学院') }}</span></div>
          <div class="um-tree-search">
            <el-input v-model="treeKw" :placeholder="t('搜索学院 / 导师 / 工号')" size="small" clearable />
          </div>
          <div class="um-tree-body">
            <button class="um-root" :class="{ on: !filters.college && !filters.teacherId }" @click="selectCollege(null)">
              {{ t('全部导师') }}<span class="um-n">{{ caps.length }}</span>
            </button>
            <div v-for="col in treeCols" :key="col.name" class="um-col">
              <button class="um-node" :class="{ on: filters.college === col.name }" @click="selectCollege(col.name)">
                <span class="um-caret" @click.stop="toggleCol(col.name)">{{ isOpen(col.name) ? '▾' : '▸' }}</span>{{ col.name }}<span class="um-n">{{ col.list.length }}</span>
              </button>
              <div v-if="isOpen(col.name)">
                <button v-for="a in col.list" :key="a.teacherId" class="um-leaf"
                        :class="{ on: filters.teacherId === a.teacherId }" @click="selectTeacher(a)">
                  {{ a.teacherName }}<span class="um-n">{{ a.teacherNo }}</span>
                </button>
              </div>
            </div>
            <div v-if="!treeCols.length" class="um-tree-empty">{{ t('没有匹配的导师') }}</div>
          </div>
        </aside>

        <section class="um-main">
          <div class="um-list-h">
            <div class="um-list-title"><span class="um-dot"></span><b>{{ scopeLabel }}</b><span class="um-scope-n">{{ filteredCaps.length }} {{ t('位') }}</span></div>
            <div class="um-list-actions">
              <el-input v-model="filters.keyword" :placeholder="t('搜索导师 / 工号')" size="small" clearable style="width:170px" />
              <span class="um-per-label">{{ t('每页') }}</span>
              <select v-model.number="pageSize" class="um-per"><option v-for="n in [10, 20, 50, 100]" :key="n" :value="n">{{ n }}</option></select>
            </div>
          </div>
          <div class="um-hint">{{ t('点击左侧学院或导师快速定位，点击任意一行查看完整档案并调整容量') }}</div>

          <div class="data-table">
            <table>
              <thead>
                <tr><th>{{ t('导师') }}</th><th>{{ t('工号') }}</th><th>{{ t('学院') }}</th><th>{{ t('已指导') }}</th><th>{{ t('容量') }}</th><th>{{ t('状态') }}</th><th>{{ t('操作') }}</th></tr>
              </thead>
              <tbody>
                <tr v-for="a in paged" :key="a.teacherId" class="um-row" @click="openCap(a)">
                  <td><b>{{ a.teacherName }}</b></td>
                  <td class="mono">{{ a.teacherNo }}</td>
                  <td>{{ a.teacherCollege || '—' }}</td>
                  <td>
                    {{ (a.approved ?? 0) + (a.pending ?? 0) }}
                    <span class="sub-note">({{ t('已确认 {n}', { n: a.approved ?? 0 }) }} + {{ t('待确认 {n}', { n: a.pending ?? 0 }) }})</span>
                  </td>
                  <td class="mono">{{ a.capacity }}</td>
                  <td>
                    <span class="status-tag" :class="(a.approved ?? 0) >= (a.capacity ?? 999) ? 'status-pending' : 'status-approved'">
                      {{ (a.approved ?? 0) >= (a.capacity ?? 999) ? t('已满') : t('可接受') }}
                    </span>
                  </td>
                  <td><button class="um-detail" @click.stop="openCap(a)">{{ t('详情') }} ›</button></td>
                </tr>
                <tr v-if="!paged.length"><td colspan="7">
                  <div class="ad-empty">
                    <div class="ad-empty-title">{{ t('没有符合条件的导师') }}</div>
                    <div v-if="suggestChips.length" class="ad-suggest">
                      <span class="ad-suggest-label">{{ t('是不是想找') }}</span>
                      <button v-for="s in suggestChips" :key="s" class="ad-suggest-chip" @click="filters.keyword = s">{{ s }}</button>
                    </div>
                    <button v-if="hasFilter" class="ad-empty-reset" @click="clearFilters">{{ t('清空筛选') }}</button>
                  </div>
                </td></tr>
              </tbody>
            </table>
          </div>

          <div v-if="filteredCaps.length > pageSize" class="pg-bar">
            <span class="pg-info">{{ t('共 {n} 条 · 第 {p} / {total} 页', { n: filteredCaps.length, p: page, total: totalPages }) }}</span>
            <div class="pg-btns">
              <button class="pg-btn" :disabled="page <= 1" @click="page--">‹</button>
              <template v-for="(p, i) in pageItems" :key="i">
                <span v-if="p === '…'" class="pg-dots">…</span>
                <button v-else class="pg-btn" :class="{ active: p === page }" @click="page = p">{{ p }}</button>
              </template>
              <button class="pg-btn" :disabled="page >= totalPages" @click="page++">›</button>
            </div>
          </div>
        </section>
      </div>
    </div>

    <Teleport to="body">
      <div v-if="detail" class="um-mask" @click="detail = null">
        <div class="um-drawer" @click.stop>
          <div class="um-drawer-h">
            <div><b>{{ detail.teacherName }}</b><span>{{ detail.teacherNo }}</span></div>
            <button class="um-close" @click="detail = null">×</button>
          </div>
          <div class="um-drawer-body">
            <div v-for="f in detailFields" :key="f.label" class="um-field"><span>{{ f.label }}</span><b>{{ f.value || '—' }}</b></div>
            <div class="um-cap-edit">
              <div class="um-cap-title">{{ t('调整容量') }}</div>
              <div class="um-cap-row">
                <el-input-number v-model="draftCap" :min="1" :max="99" size="small" style="width:120px" :disabled="savingId === detail.teacherId" />
                <button class="btn btn-primary btn-sm" :disabled="savingId === detail.teacherId || draftCap === detail.capacity" @click="saveCap">
                  {{ savingId === detail.teacherId ? t('保存中…') : t('保存容量') }}
                </button>
              </div>
              <div class="um-cap-tip">{{ t('新导师未单独设置容量时，使用全局默认容量') }}</div>
            </div>
            <div v-if="detailApplies.length" class="um-cap-title" style="margin-top:18px">{{ t('该导师的申请（{n}）', { n: detailApplies.length }) }}</div>
            <div v-for="r in detailApplies" :key="r.id" class="um-apply">
              <div><b>{{ r.studentName }}</b><span class="sub-note">（{{ r.studentNo }}）</span></div>
              <div class="um-apply-topic">{{ r.topic || '—' }}</div>
              <span class="status-tag" :class="statusCls(r.status)">{{ statusText(r.status) }}</span>
            </div>
          </div>
          <div class="um-drawer-f">
            <button class="btn btn-outline btn-sm" @click="detail = null">{{ t('关闭') }}</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- ================= 命令面板 ================= -->
    <Teleport to="body">
      <div v-if="cmdOpen" class="cmd-overlay" @click="closeCommand(false)">
        <div class="cmd-panel" @click.stop>
          <div class="cmd-input-row">
            <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>
            <input ref="cmdInput" v-model="cmdTerm" :placeholder="t('搜索导师 / 工号 / 学院，支持拼音首字母…')" />
            <kbd class="ad-kbd">esc</kbd>
          </div>
          <div class="cmd-results">
            <template v-if="cmdItems.length">
              <div v-if="cmdTeachers.length" class="cmd-group">
                <div class="cmd-group-h">{{ t('导师') }} <span>{{ cmdTeachers.length }}</span></div>
                <button v-for="(a, i) in cmdTeachers" :key="a.teacherId" class="cmd-item" :class="{ active: cmdCursor === i }" @mouseenter="cmdCursor = i" @click="selectCmd(cmdItems[i])">
                  <span class="cmd-item-ico user"><svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/></svg></span>
                  <span class="cmd-item-main"><b>{{ a.teacherName }}</b><small>{{ a.teacherNo }} · {{ a.teacherCollege || '—' }} · {{ t('容量 {n}', { n: a.capacity ?? 0 }) }}</small></span>
                  <span class="cmd-enter" v-if="cmdCursor === i">↵</span>
                </button>
              </div>
              <div v-if="cmdColleges.length" class="cmd-group">
                <div class="cmd-group-h">{{ t('学院') }} <span>{{ cmdColleges.length }}</span></div>
                <button v-for="(o, j) in cmdColleges" :key="o.name" class="cmd-item" :class="{ active: cmdCursor === cmdTeachers.length + j }" @mouseenter="cmdCursor = cmdTeachers.length + j" @click="selectCmd(cmdItems[cmdTeachers.length + j])">
                  <span class="cmd-item-ico org"><svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M3 21h18M5 21V7l7-4 7 4v14M9 21v-6h6v6"/></svg></span>
                  <span class="cmd-item-main"><b>{{ o.name }}</b><small>{{ o.count }} {{ t('位导师') }}</small></span>
                  <span class="cmd-enter" v-if="cmdCursor === cmdTeachers.length + j">↵</span>
                </button>
              </div>
            </template>
            <div v-else class="cmd-empty">{{ t('没有联想结果，试试导师姓名 / 工号 / 拼音首字母') }}</div>
          </div>
          <div class="cmd-footer">
            <span><kbd class="ad-kbd">↑</kbd><kbd class="ad-kbd">↓</kbd> {{ t('导航') }}</span>
            <span><kbd class="ad-kbd">↵</kbd> {{ t('选中') }}</span>
            <span class="cmd-footer-tip">{{ t('选中导师看档案 · 选中学院即筛选') }}</span>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, toRefs, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api'
import { t } from '@/i18n'
import { diceCoefficient } from '@/utils/similarity'
import { initialsOf } from '@/utils/pinyin'

const isMac = typeof navigator !== 'undefined' && /Mac|iPhone|iPad/.test(navigator.platform)
const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`

const overview = reactive<any>({
  teacherCount: 0, totalApproved: 0, totalPending: 0, teachersWithSpace: 0
})
const cfg = reactive<{ open: boolean; defaultCapacity: number }>({ open: true, defaultCapacity: 15 })
const caps = ref<any[]>([])
const list = ref<any[]>([])
const savingId = ref<number | null>(null)
const cfgSaving = ref(false)

// —— 单一状态源：筛选 chips / 左侧学院树 / 命令面板 / 搜索框读写同一 filters ——
const filters = reactive({ keyword: '' as string, college: null as string | null, teacherId: null as number | null, capacity: '' as string })
const { keyword: kw, college: collegeFilter, teacherId: teacherIdFilter } = toRefs(filters)
const treeKw = ref('')
const openCols = ref<Record<string, boolean>>({})
const page = ref(1)
const pageSize = ref(20)

const treeCols = computed(() => {
  const q = treeKw.value.trim().toLowerCase()
  const map = new Map<string, any[]>()
  for (const a of caps.value) {
    const c = a.teacherCollege || t('未分配学院')
    if (!map.has(c)) map.set(c, [])
    map.get(c)!.push(a)
  }
  return [...map.entries()]
    .map(([name, list]) => ({
      name,
      list: q ? list.filter(a =>
        name.toLowerCase().includes(q) || (a.teacherName || '').toLowerCase().includes(q) || (a.teacherNo || '').toLowerCase().includes(q)
      ) : list,
      total: list.length,
    }))
    .filter(c => c.list.length > 0)
    .sort((a, b) => b.total - a.total || a.name.localeCompare(b.name, 'zh'))
})
const isOpen = (c: string) => openCols.value[c] === true || !!treeKw.value.trim()   // 搜索时自动展开
function toggleCol(c: string) {
  if (treeKw.value.trim()) { treeKw.value = ''; openCols.value = { ...openCols.value, [c]: true }; return }
  openCols.value = { ...openCols.value, [c]: !openCols.value[c] }
}
function selectCollege(c: string | null) {
  collegeFilter.value = c; teacherIdFilter.value = null; page.value = 1
  if (c && !isOpen(c)) openCols.value = { ...openCols.value, [c]: true }
}
function selectTeacher(a: any) {
  collegeFilter.value = null; teacherIdFilter.value = a.teacherId; page.value = 1
  openCap(a)   // 树里点导师直接打开档案抽屉
}
function clearFilters() {
  filters.keyword = ''; filters.college = null; filters.teacherId = null; filters.capacity = ''
  treeKw.value = ''; page.value = 1
}

const q = () => kw.value.trim().toLowerCase()
const filteredCaps = computed(() => caps.value.filter(a => {
  if (teacherIdFilter.value != null && a.teacherId !== teacherIdFilter.value) return false
  if (collegeFilter.value && (a.teacherCollege || t('未分配学院')) !== collegeFilter.value) return false
  if (filters.capacity === 'full' && (a.approved ?? 0) < (a.capacity ?? 999)) return false
  if (filters.capacity === 'available' && (a.approved ?? 0) >= (a.capacity ?? 999)) return false
  const s = q()
  if (s && !((a.teacherName || '').toLowerCase().includes(s) || (a.teacherNo || '').toLowerCase().includes(s) || (a.teacherCollege || '').toLowerCase().includes(s))) return false
  return true
}))
const totalPages = computed(() => Math.max(1, Math.ceil(filteredCaps.value.length / pageSize.value)))
const paged = computed(() => filteredCaps.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value))
const pageItems = computed<(number | '…')[]>(() => {
  const t = totalPages.value, c = page.value
  if (t <= 7) return Array.from({ length: t }, (_, i) => i + 1)
  const set = new Set([1, 2, t - 1, t, c - 1, c, c + 1].filter(p => p >= 1 && p <= t))
  const arr = [...set].sort((a, b) => a - b)
  const out: (number | '…')[] = []
  let prev = 0
  for (const p of arr) { if (p - prev > 1) out.push('…'); out.push(p); prev = p }
  return out
})
watch([collegeFilter, teacherIdFilter, kw, () => filters.capacity, pageSize], () => { page.value = 1 })
watch(totalPages, (v) => { if (page.value > v) page.value = v })

const scopeLabel = computed(() => {
  if (teacherIdFilter.value != null) {
    const a = caps.value.find(x => x.teacherId === teacherIdFilter.value)
    return a ? a.teacherName : t('全部导师')
  }
  return collegeFilter.value ? `${collegeFilter.value} · ${t('导师')}` : t('全部导师')
})

// —— 行详情抽屉（与用户管理一致）——
const detail = ref<any>(null)
const draftCap = ref<number>(1)
function openCap(a: any) { detail.value = a; draftCap.value = a.capacity ?? 1 }
const detailFields = computed(() => !detail.value ? [] : [
  { label: t('工号'), value: detail.value.teacherNo },
  { label: t('学院'), value: detail.value.teacherCollege },
  { label: t('已确认'), value: t('{n} 人', { n: detail.value.approved ?? 0 }) },
  { label: t('待确认'), value: t('{n} 人', { n: detail.value.pending ?? 0 }) },
  { label: t('已指导合计'), value: t('{n} 人', { n: (detail.value.approved ?? 0) + (detail.value.pending ?? 0) }) },
  { label: t('当前容量'), value: t('{n} 人', { n: detail.value.capacity ?? 0 }) },
  { label: t('状态'), value: (detail.value.approved ?? 0) >= (detail.value.capacity ?? 999) ? t('已满') : t('可接受') },
])
// 该导师名下申请（按姓名兜底，后端未返回 teacherId 时仍可用）
const detailApplies = computed(() => !detail.value ? [] : list.value.filter((r: any) =>
  (detail.value.teacherId != null && r.teacherId === detail.value.teacherId) || r.teacherName === detail.value.teacherName
))
async function saveCap() {
  if (!detail.value) return
  await updateCap(detail.value, draftCap.value)
}

const STATUS: Record<string, string> = {
  approved: '已确认', pending: '待审批', rejected: '已拒绝', cancelled: '已取消'
}
const statusText = (s: string) => { const v = STATUS[s] || s; return v ? t(v) : '—' }
const statusCls = (s: string) =>
  s === 'approved' ? 'status-approved'
  : s === 'pending' ? 'status-pending'
  : s === 'rejected' ? 'status-rejected' : 'status-cancelled'

// 后端业务失败走 ApiResponse.message；HTTP 异常时取 response.data.message，避免只显示 "status code 500"
const errMsg = (e: any, fallback: string) =>
  e?.response?.data?.message || (typeof e?.message === 'string' && !/status code/.test(e.message) ? e.message : fallback)

async function load() {
  try {
    // 概览：导师数 / 已确认 / 待审批 / 有空位
    const [o, c, l] = await Promise.all([
      http.get('/advisor/admin/overview'),
      http.get('/advisor/admin/capacities'),
      http.get('/advisor/admin/list')
    ])
    const od: any = o?.data || {}
    Object.assign(overview, {
      teacherCount: od.teacherCount ?? 0,
      totalApproved: od.totalApproved ?? 0,
      totalPending: od.totalPending ?? 0,
      teachersWithSpace: od.teachersWithSpace ?? 0
    })
    if (od.config) {
      cfg.open = !!od.config.open
      cfg.defaultCapacity = od.config.defaultCapacity ?? 15
    }
    caps.value = c?.data || []
    list.value = l?.data || []
  } catch (e: any) {
    ElMessage.warning(errMsg(e, t('加载失败')))
  }
}

// 导师容量：后端唯一入口 POST /advisor/admin/cfg { teacherId, teacherCapacity }
async function updateCap(a: any, v: number) {
  if (v === undefined || v === null) return
  if (savingId.value !== null) return   // 防连点：一次只允许一个提交
  const val = Number(v)
  if (!Number.isFinite(val) || val < 1) {
    ElMessage.warning(t('容量至少为 1'))
    load()
    return
  }
  savingId.value = a.teacherId
  try {
    const r: any = await http.post('/advisor/admin/cfg', { teacherId: a.teacherId, teacherCapacity: val })
    a.capacity = val
    ElMessage.success(r?.message || t('{name} 容量已更新为 {n}', { name: a.teacherName, n: val }))
  } catch (e: any) {
    ElMessage.error(errMsg(e, t('更新失败')))
    load()
  } finally {
    savingId.value = null
  }
}

async function saveCfg() {
  cfgSaving.value = true
  try {
    const r: any = await http.post('/advisor/admin/cfg', {
      open: cfg.open,
      defaultCapacity: cfg.defaultCapacity
    })
    ElMessage.success(r?.message || t('配置已保存'))
    load()
  } catch (e: any) {
    ElMessage.error(errMsg(e, t('保存失败')))
  } finally {
    cfgSaving.value = false
  }
}

/* ===== 筛选轨道 chips ===== */
type ChipKey = 'college' | 'capacity'
const openChip = ref<ChipKey | null>(null)
function toggleChip(k: ChipKey) { openChip.value = openChip.value === k ? null : k }
const collegeOptions = computed(() => [...new Set(caps.value.map(a => a.teacherCollege || t('未分配学院')))].sort((a, b) => a.localeCompare(b, 'zh')))
const chipDefs = computed(() => [
  {
    key: 'college' as ChipKey, label: t('学院'), active: !!filters.college, value: filters.college || '', current: filters.college,
    options: collegeOptions.value.map(c => ({ value: c, label: c, count: caps.value.filter(a => (a.teacherCollege || t('未分配学院')) === c).length })),
    clear: () => { filters.college = null; filters.teacherId = null }, set: (v: any) => { filters.college = v as string; filters.teacherId = null },
  },
  {
    key: 'capacity' as ChipKey, label: t('容量'), active: !!filters.capacity,
    value: filters.capacity === 'full' ? t('已满') : filters.capacity === 'available' ? t('可接受') : '', current: filters.capacity,
    options: [
      { value: 'full', label: t('已满'), count: caps.value.filter(a => (a.approved ?? 0) >= (a.capacity ?? 999)).length },
      { value: 'available', label: t('可接受'), count: caps.value.filter(a => (a.approved ?? 0) < (a.capacity ?? 999)).length },
    ],
    clear: () => { filters.capacity = '' }, set: (v: any) => { filters.capacity = v as string },
  },
])
const hasFilter = computed(() => !!(kw.value || filters.college || filters.teacherId != null || filters.capacity))

/* ===== 空结果 Dice 纠错 ===== */
function charDice(a: string, b: string): number {
  const A = new Set(Array.from(a.toLowerCase().replace(/\s+/g, '')))
  const B = new Set(Array.from(b.toLowerCase().replace(/\s+/g, '')))
  if (!A.size || !B.size) return 0
  let inter = 0
  for (const c of A) if (B.has(c)) inter++
  return (2 * inter) / (A.size + B.size)
}
const suggestChips = computed(() => {
  const k = kw.value.trim()
  if (!k) return []
  const names = new Set<string>()
  for (const a of caps.value) { names.add(a.teacherName); if (a.teacherNo) names.add(a.teacherNo) }
  for (const c of collegeOptions.value) names.add(c)
  const scored = new Map<string, number>()
  for (const n of names) {
    const score = Math.max(diceCoefficient(k, n), charDice(k, n))
    if (score >= 0.34) scored.set(n, score)
  }
  return [...scored.entries()].sort((a, b) => b[1] - a[1]).slice(0, 5).map(([n]) => n)
})

/* ===== 命令面板 ===== */
const cmdOpen = ref(false)
const cmdTerm = ref('')
const cmdCursor = ref(0)
const cmdInput = ref<HTMLInputElement | null>(null)
function pyOf(a: any) { return initialsOf(`${a.teacherName || ''} ${a.teacherNo || ''} ${a.teacherCollege || ''}`) }
function matchText(haystack: string, py: string, qq: string): boolean {
  const ql = qq.toLowerCase().replace(/\s+/g, '')
  if (!ql) return true
  if ((haystack || '').toLowerCase().replace(/\s+/g, '').includes(ql)) return true
  return !!py && py.includes(ql)
}
function openCommand() { cmdTerm.value = kw.value; cmdCursor.value = 0; cmdOpen.value = true; nextTick(() => cmdInput.value?.focus()) }
function closeCommand(clear: boolean) {
  cmdOpen.value = false
  if (clear) { cmdTerm.value = ''; kw.value = '' }
  else { cmdTerm.value = kw.value }
}
watch(cmdTerm, v => { if (cmdOpen.value) kw.value = v })
const cmdTeachers = computed(() => {
  const qq = cmdTerm.value.trim()
  const arr = !qq ? caps.value.slice(0, 8)
    : caps.value.filter(a => matchText(`${a.teacherName || ''} ${a.teacherNo || ''} ${a.teacherCollege || ''}`, pyOf(a), qq)).slice(0, 8)
  return arr
})
const cmdColleges = computed(() => {
  const qq = cmdTerm.value.trim()
  if (!qq) return []
  return collegeOptions.value.filter(c => matchText(c, initialsOf(c), qq))
    .map(c => ({ name: c, count: caps.value.filter(a => (a.teacherCollege || t('未分配学院')) === c).length })).slice(0, 6)
})
type CmdItem = { kind: 'teacher' | 'college'; raw: any }
const cmdItems = computed<CmdItem[]>(() => [
  ...cmdTeachers.value.map(a => ({ kind: 'teacher' as const, raw: a })),
  ...cmdColleges.value.map(c => ({ kind: 'college' as const, raw: c })),
])
watch(cmdItems, () => { if (cmdCursor.value >= cmdItems.value.length) cmdCursor.value = 0 })
function selectCmd(item: CmdItem | undefined) {
  if (!item) return
  if (item.kind === 'teacher') { openCap(item.raw); cmdOpen.value = false; cmdTerm.value = ''; kw.value = '' }
  else { filters.college = item.raw.name; filters.teacherId = null; cmdOpen.value = false; cmdTerm.value = ''; kw.value = ''; ElMessage.success(t('已按学院筛选：{n}', { n: item.raw.name })) }
}
function onGlobalKey(e: KeyboardEvent) {
  const meta = e.metaKey || e.ctrlKey
  if (meta && e.key.toLowerCase() === 'k') { e.preventDefault(); cmdOpen.value ? closeCommand(false) : openCommand(); return }
  if (!cmdOpen.value) return
  if (e.key === 'Escape') { e.preventDefault(); closeCommand(false) }
  else if (e.key === 'ArrowDown') { e.preventDefault(); if (cmdItems.value.length) cmdCursor.value = (cmdCursor.value + 1) % cmdItems.value.length }
  else if (e.key === 'ArrowUp') { e.preventDefault(); if (cmdItems.value.length) cmdCursor.value = (cmdCursor.value - 1 + cmdItems.value.length) % cmdItems.value.length }
  else if (e.key === 'Enter') { e.preventDefault(); selectCmd(cmdItems.value[cmdCursor.value]) }
}

onMounted(() => { load(); window.addEventListener('keydown', onGlobalKey) })
onBeforeUnmount(() => { window.removeEventListener('keydown', onGlobalKey) })
</script>

<style scoped>
.page-wrap { padding: 4px 0; }
.cfg-row { display: flex; align-items: center; gap: 22px; flex-wrap: wrap; }
.cfg-item { display: inline-flex; align-items: center; gap: 8px; font-size: 13px; color: var(--text-light); }
.cfg-tip { font-size: 12px; color: var(--text-lighter); }
.mono { font-variant-numeric: tabular-nums; font-size: 13px; }
.sub-note { color: var(--text-lighter); font-size: 12px; }
.status-tag.status-rejected { background: rgba(239,68,68,.14); color: #ef4444; }
.status-tag.status-cancelled { background: rgba(148,163,184,.16); color: var(--text-light); }

/* 左侧组织树 + 右侧范围列表（与用户管理页同一套视觉） */
.um-count-tip { margin-left: auto; font-size: 12px; color: var(--text-lighter); }
.um-split { display: grid; grid-template-columns: 232px minmax(0, 1fr); gap: 12px; align-items: start; }
.um-tree { border: 0.5px solid var(--border); border-radius: 12px; background: var(--card); overflow: hidden; }
.um-tree-h { display: flex; align-items: center; justify-content: space-between; padding: 10px 12px; border-bottom: 0.5px solid var(--border); font-size: 12.5px; color: var(--text); }
.um-tree-h span { font-size: 11px; color: var(--text-lighter); }
.um-tree-search { padding: 8px 10px; border-bottom: 0.5px solid var(--border); }
.um-tree-body { max-height: 470px; overflow-y: auto; padding: 6px; }
.um-root, .um-node, .um-leaf { display: flex; align-items: center; gap: 6px; width: 100%; text-align: left; border: none; background: none; font-family: inherit; font-size: 12.5px; color: var(--text-light); cursor: pointer; border-radius: 8px; padding: 7px 8px; }
.um-root:hover, .um-node:hover, .um-leaf:hover { background: var(--primary-bg, rgba(43,177,135,.1)); color: var(--primary); }
.um-root.on, .um-node.on, .um-leaf.on { background: var(--primary, #2bb187); color: #fff; }
.um-root.on .um-n, .um-node.on .um-n, .um-leaf.on .um-n { color: rgba(255,255,255,.9); background: rgba(255,255,255,.18); }
.um-node.on .um-caret { color: rgba(255,255,255,.9); }
.um-n { margin-left: auto; font-size: 11px; color: var(--text-lighter); background: rgba(0,0,0,.05); border-radius: 999px; padding: 1px 6px; flex: 0 0 auto; }
.um-caret { width: 10px; color: var(--text-lighter); font-size: 11px; flex: 0 0 auto; }
.um-node { font-weight: 600; color: var(--text); }
.um-leaf { padding-left: 30px; font-size: 12px; }
.um-tree-empty { padding: 16px; text-align: center; font-size: 12px; color: var(--text-lighter); }
.um-main { min-width: 0; }
.um-list-h { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; padding: 10px 12px; border: 0.5px solid var(--border); border-radius: 12px; background: var(--primary-bg, rgba(43,177,135,.09)); margin-bottom: 6px; }
.um-list-title { display: flex; align-items: center; gap: 8px; font-size: 13px; color: var(--primary); }
.um-scope-n { font-size: 11.5px; color: var(--text-light); background: rgba(0,0,0,.05); padding: 2px 8px; border-radius: 999px; }
.um-list-actions { margin-left: auto; display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
.um-per-label { font-size: 12px; color: var(--text-light); }
.um-per { background: var(--card); color: var(--text); border: 0.5px solid var(--border); border-radius: 6px; padding: 3px 6px; font-family: inherit; font-size: 12px; }
.um-clear { border: none; background: none; color: var(--primary); font-family: inherit; font-size: 12px; cursor: pointer; padding: 4px 6px; }
.um-dot { display: inline-block; width: 6px; height: 6px; border-radius: 50%; background: var(--primary); flex: 0 0 auto; }
.um-hint { padding: 0 4px 8px; font-size: 11.5px; color: var(--text-lighter); }

/* 行详情抽屉（Teleport 到 body，避免被 .stats-page > * 的定位规则覆盖） */
.um-row { cursor: pointer; }
.um-row:hover { background: var(--primary-bg, rgba(43,177,135,.06)); }
.um-detail { border: none; background: none; color: var(--primary); font-family: inherit; font-size: 12px; cursor: pointer; padding: 4px 6px; white-space: nowrap; }
.um-mask { position: fixed !important; inset: 0; background: rgba(0,0,0,.38); z-index: 3000 !important; display: flex; justify-content: flex-end; }
.um-drawer { width: 380px; max-width: 90vw; height: 100%; background: var(--card); border-left: 0.5px solid var(--border); display: flex; flex-direction: column; }
.um-drawer-h { display: flex; align-items: center; padding: 16px 18px; border-bottom: 0.5px solid var(--border); }
.um-drawer-h b { font-size: 15px; color: var(--text); }
.um-drawer-h span { display: block; font-size: 12px; color: var(--text-lighter); margin-top: 2px; }
.um-close { margin-left: auto; border: none; background: none; font-size: 22px; color: var(--text-light); cursor: pointer; line-height: 1; }
.um-drawer-body { flex: 1; overflow-y: auto; padding: 12px 18px; }
.um-field { display: flex; justify-content: space-between; gap: 12px; padding: 10px 0; border-bottom: 0.5px solid var(--border); font-size: 12.5px; }
.um-field span { color: var(--text-lighter); flex: 0 0 auto; }
.um-field b { color: var(--text); font-weight: 500; text-align: right; }
.um-drawer-f { display: flex; gap: 8px; flex-wrap: wrap; padding: 14px 18px; border-top: 0.5px solid var(--border); }
.um-cap-edit { margin-top: 18px; padding: 14px; border: 0.5px solid var(--border); border-radius: 10px; background: var(--primary-bg, rgba(43,177,135,.05)); }
.um-cap-title { font-size: 12.5px; font-weight: 600; color: var(--text); margin-bottom: 10px; }
.um-cap-row { display: flex; align-items: center; gap: 10px; }
.um-cap-tip { margin-top: 8px; font-size: 11.5px; color: var(--text-lighter); }
.um-apply { position: relative; padding: 10px 0; border-bottom: 0.5px solid var(--border); font-size: 12.5px; color: var(--text); }
.um-apply .um-apply-topic { margin: 4px 0 6px; font-size: 12px; color: var(--text-light); }
.um-apply .status-tag { display: inline-block; }

/* ================= 检索体验升级：顶栏 / 筛选 chips / 命令面板 / 空结果 ================= */
.page-wrap { --g: #10b981; --g-dark: #059669; --g-soft: rgba(16,185,129,.10); }
.ic { display: block; }
.ad-topbar { position: sticky; top: 0; z-index: 40; display: flex; align-items: center; gap: 16px; flex-wrap: wrap; padding: 12px 16px; background: color-mix(in srgb, var(--card, #fff) 86%, transparent); backdrop-filter: saturate(1.4) blur(14px); -webkit-backdrop-filter: saturate(1.4) blur(14px); border: 0.5px solid var(--border, #e5e7eb); border-radius: 16px; box-shadow: 0 1px 2px rgba(16,24,40,.03), 0 8px 24px rgba(16,24,40,.04); }
.ad-brand { display: flex; align-items: center; gap: 12px; flex: 0 0 auto; }
.ad-brand-icon { width: 40px; height: 40px; border-radius: 12px; display: grid; place-items: center; color: #fff; background: linear-gradient(135deg, #34d399, var(--g-dark)); box-shadow: 0 4px 12px rgba(16,185,129,.32); }
.ad-brand-icon .ic { width: 21px; height: 21px; }
.ad-title { font-size: 17px; font-weight: 700; color: var(--text, #0f172a); line-height: 1.2; }
.ad-subtitle { font-size: 12px; color: var(--text-lighter, #94a3b8); margin-top: 2px; }
.ad-search-trigger { flex: 1 1 320px; max-width: 520px; min-width: 180px; height: 38px; display: flex; align-items: center; gap: 9px; padding: 0 10px 0 14px; border: 1px solid var(--border, #e5e7eb); border-radius: 999px; background: var(--card, #fff); color: var(--text-lighter, #94a3b8); cursor: pointer; transition: all .18s; font-family: inherit; }
.ad-search-trigger:hover { border-color: var(--g); box-shadow: 0 0 0 3px var(--g-soft); }
.ad-search-trigger .ic { width: 17px; height: 17px; flex: 0 0 auto; }
.ad-search-ph { flex: 1; text-align: left; font-size: 13px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.ad-kbd { font-family: inherit; font-size: 11px; line-height: 1; white-space: nowrap; padding: 4px 7px; border-radius: 6px; border: 1px solid var(--border, #e5e7eb); background: color-mix(in srgb, var(--card, #fff) 92%, #000 8%); color: var(--text-light, #64748b); flex: 0 0 auto; }
/* 筛选卡片需盖住下方树区：atlas-theme 的 `.stats-page.liquid .section > *` 会把卡片压到 z-index:1，
   同层 DOM 靠后的树区会遮住下拉。此处用同等特异性 + !important 提到 6
   （低于顶栏 40、抽屉 3000、命令面板 4000），让下拉浮在树之上 */
.stats-page.liquid .section > .ad-filtercard { z-index: 6 !important; }
.ad-filtercard { padding: 12px 14px; border: 0.5px solid var(--border); border-radius: 14px; background: var(--card); margin: 12px 0; position: relative; z-index: 6; }
.ad-chip-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.ad-chip-wrap { position: relative; }
.ad-chip { display: inline-flex; align-items: center; gap: 6px; height: 32px; padding: 0 11px; border: 1px solid var(--border); border-radius: 999px; background: var(--card); color: var(--text-light); font-family: inherit; font-size: 12.5px; cursor: pointer; transition: all .16s; }
.ad-chip:hover { border-color: var(--g); color: var(--g); }
.ad-chip.on { background: var(--g-soft); border-color: var(--g); color: var(--g-dark); font-weight: 600; }
.ad-chip-label { white-space: nowrap; }
.ad-chip-value { max-width: 140px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--g-dark); }
.ad-chip-arrow { width: 13px; height: 13px; transition: transform .16s; }
.ad-chip-arrow.open { transform: rotate(180deg); }
.ad-reset { border: none; background: none; color: var(--text-lighter); font-family: inherit; font-size: 12.5px; cursor: pointer; padding: 6px 8px; }
.ad-reset:hover { color: #ef4444; }
.ad-dropdown { position: absolute; top: calc(100% + 6px); left: 0; z-index: 30; min-width: 190px; max-height: 320px; overflow-y: auto; padding: 6px; border: 0.5px solid var(--border); border-radius: 12px; background: var(--card); box-shadow: 0 12px 32px rgba(16,24,40,.14); }
.ad-opt { display: flex; align-items: center; justify-content: space-between; gap: 12px; width: 100%; border: none; background: none; font-family: inherit; font-size: 12.5px; color: var(--text-light); cursor: pointer; padding: 8px 10px; border-radius: 8px; text-align: left; }
.ad-opt:hover { background: var(--g-soft); color: var(--g); }
.ad-opt.on { background: var(--g-soft); color: var(--g-dark); font-weight: 600; }
.ad-opt-n { margin-left: 8px; font-size: 11px; color: var(--text-lighter); font-variant-numeric: tabular-nums; }
.ad-opt-check { color: var(--g); font-weight: 700; }
.ad-empty { display: flex; flex-direction: column; align-items: center; text-align: center; padding: 32px 20px; }
.ad-empty-title { font-size: 14px; font-weight: 600; color: var(--text); }
.ad-suggest { display: flex; align-items: center; gap: 7px; flex-wrap: wrap; justify-content: center; margin-top: 14px; }
.ad-suggest-label { font-size: 12px; color: var(--text-light); }
.ad-suggest-chip { border: 1px solid var(--border); background: var(--card); color: var(--g-dark); font-family: inherit; font-size: 12.5px; padding: 6px 12px; border-radius: 999px; cursor: pointer; transition: all .15s; }
.ad-suggest-chip:hover { border-color: var(--g); background: var(--g-soft); }
.ad-empty-reset { margin-top: 14px; border: none; background: none; color: var(--g); font-family: inherit; font-size: 12.5px; cursor: pointer; text-decoration: underline; text-underline-offset: 3px; }
.cmd-overlay { position: fixed; inset: 0; z-index: 4000; display: flex; justify-content: center; align-items: flex-start; padding: 14vh 16px 16px; background: rgba(15,23,42,.42); backdrop-filter: blur(8px); -webkit-backdrop-filter: blur(8px); animation: cmdFade .16s ease; }
.cmd-panel { width: 100%; max-width: 600px; max-height: 68vh; display: flex; flex-direction: column; border: 0.5px solid var(--border); border-radius: 16px; overflow: hidden; background: var(--card); box-shadow: 0 24px 70px rgba(15,23,42,.34); animation: cmdUp .2s cubic-bezier(.16,1,.3,1); }
.cmd-input-row { display: flex; align-items: center; gap: 11px; padding: 14px 16px; border-bottom: 0.5px solid var(--border); }
.cmd-input-row .ic { width: 19px; height: 19px; color: var(--g); flex: 0 0 auto; }
.cmd-input-row input { flex: 1; min-width: 0; border: none; outline: none; background: transparent; color: var(--text); font-family: inherit; font-size: 15px; }
.cmd-input-row input::placeholder { color: var(--text-lighter); }
.cmd-results { flex: 1; overflow-y: auto; padding: 8px; }
.cmd-group + .cmd-group { margin-top: 6px; }
.cmd-group-h { display: flex; align-items: center; gap: 7px; padding: 6px 10px; font-size: 11px; font-weight: 700; letter-spacing: .04em; color: var(--text-lighter); text-transform: uppercase; }
.cmd-group-h span { font-weight: 500; }
.cmd-item { display: flex; align-items: center; gap: 11px; width: 100%; text-align: left; border: none; background: none; font-family: inherit; cursor: pointer; padding: 9px 10px; border-radius: 10px; color: var(--text); }
.cmd-item.active { background: var(--g-soft); }
.cmd-item-ico { width: 32px; height: 32px; border-radius: 9px; display: grid; place-items: center; flex: 0 0 auto; }
.cmd-item-ico .ic { width: 17px; height: 17px; }
.cmd-item-ico.user { background: rgba(16,185,129,.13); color: var(--g); }
.cmd-item-ico.org { background: rgba(59,130,246,.13); color: #3b82f6; }
.cmd-item-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.cmd-item-main b { font-size: 13px; font-weight: 600; color: var(--text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cmd-item-main small { font-size: 11px; color: var(--text-lighter); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cmd-enter { color: var(--g); font-size: 14px; flex: 0 0 auto; }
.cmd-empty { padding: 36px 16px; text-align: center; color: var(--text-lighter); font-size: 13px; }
.cmd-footer { display: flex; align-items: center; gap: 16px; padding: 10px 16px; border-top: 0.5px solid var(--border); background: color-mix(in srgb, var(--card) 94%, #000 6%); font-size: 11px; color: var(--text-lighter); }
.cmd-footer span { display: inline-flex; align-items: center; gap: 4px; }
.cmd-footer .ad-kbd { padding: 2px 6px; }
.cmd-footer-tip { margin-left: auto; color: var(--g); }
@keyframes cmdFade { from { opacity: 0 } to { opacity: 1 } }
@keyframes cmdUp { from { opacity: 0; transform: translateY(-10px) scale(.985) } to { opacity: 1; transform: none } }
@media (max-width: 1100px) { .ad-search-trigger { order: 3; flex-basis: 100%; max-width: none; } }
@media (max-width: 860px) { .um-split { grid-template-columns: 1fr; } }
@media (max-width: 560px) { .ad-topbar { padding: 12px; } .ad-kbd { display: none; } }
</style>
