<template>
  <div class="page-wrap stats-page liquid">
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

      <div class="um-split">
        <aside class="um-tree">
          <div class="um-tree-h"><b>{{ t('按学院检索') }}</b><span>{{ treeCols.length }} {{ t('个学院') }}</span></div>
          <div class="um-tree-search">
            <el-input v-model="treeKw" :placeholder="t('搜索学院 / 导师 / 工号')" size="small" clearable />
          </div>
          <div class="um-tree-body">
            <button class="um-root" :class="{ on: !collegeFilter && !teacherIdFilter }" @click="selectCollege(null)">
              {{ t('全部导师') }}<span class="um-n">{{ caps.length }}</span>
            </button>
            <div v-for="col in treeCols" :key="col.name" class="um-col">
              <button class="um-node" :class="{ on: collegeFilter === col.name }" @click="selectCollege(col.name)">
                <span class="um-caret" @click.stop="toggleCol(col.name)">{{ isOpen(col.name) ? '▾' : '▸' }}</span>{{ col.name }}<span class="um-n">{{ col.list.length }}</span>
              </button>
              <div v-if="isOpen(col.name)">
                <button v-for="a in col.list" :key="a.teacherId" class="um-leaf"
                        :class="{ on: teacherIdFilter === a.teacherId }" @click="selectTeacher(a)">
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
              <el-input v-model="kw" :placeholder="t('搜索导师 / 工号')" size="small" clearable style="width:170px" />
              <span class="um-per-label">{{ t('每页') }}</span>
              <select v-model.number="pageSize" class="um-per"><option v-for="n in [10, 20, 50, 100]" :key="n" :value="n">{{ n }}</option></select>
              <button v-if="collegeFilter || teacherIdFilter || kw" class="um-clear" @click="clearFilters">{{ t('清空筛选') }}</button>
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
                <tr v-if="!paged.length"><td colspan="7" style="text-align:center;padding:28px;color:var(--text-light)">{{ t('没有符合条件的导师') }}</td></tr>
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api'
import { t } from '@/i18n'

const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`

const overview = reactive<any>({
  teacherCount: 0, totalApproved: 0, totalPending: 0, teachersWithSpace: 0
})
const cfg = reactive<{ open: boolean; defaultCapacity: number }>({ open: true, defaultCapacity: 15 })
const caps = ref<any[]>([])
const list = ref<any[]>([])
const savingId = ref<number | null>(null)
const cfgSaving = ref(false)

// —— 左侧学院树 + 右侧范围检索 / 分页（与用户管理一致）——
const treeKw = ref('')
const kw = ref('')
const collegeFilter = ref<string | null>(null)
const teacherIdFilter = ref<number | null>(null)
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
function clearFilters() { collegeFilter.value = null; teacherIdFilter.value = null; kw.value = ''; treeKw.value = ''; page.value = 1 }

const q = () => kw.value.trim().toLowerCase()
const filteredCaps = computed(() => caps.value.filter(a => {
  if (teacherIdFilter.value != null && a.teacherId !== teacherIdFilter.value) return false
  if (collegeFilter.value && (a.teacherCollege || t('未分配学院')) !== collegeFilter.value) return false
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
watch([collegeFilter, teacherIdFilter, kw, pageSize], () => { page.value = 1 })
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

onMounted(load)
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
</style>
