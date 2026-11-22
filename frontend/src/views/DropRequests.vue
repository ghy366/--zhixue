<template>
  <div class="page-wrap stats-page liquid dr-page">
    <!-- ===== 顶部 sticky 导航栏 ===== -->
    <header class="dr-topbar">
      <div class="dr-brand">
        <div class="dr-brand-icon">
          <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><path d="m16 17 5-5-5-5M21 12H9"/></svg>
        </div>
        <div class="dr-brand-text">
          <div class="dr-title">{{ t('退课审批') }}</div>
          <div class="dr-subtitle">{{ t('待处理 {n} · 共 {m} 条申请', { n: pendingCount, m: list.length }) }}</div>
        </div>
      </div>
      <button class="dr-search-trigger" @click="openCommand" :aria-label="t('打开搜索')">
        <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>
        <span class="dr-search-ph">{{ filters.keyword || t('搜索学生 / 学号 / 课程，支持拼音首字母…') }}</span>
        <kbd class="dr-kbd">{{ isMac ? '⌘' : 'Ctrl+' }} K</kbd>
      </button>
      <div class="dr-top-actions">
        <button class="dr-btn dr-btn-outline" @click="load">
          <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12a9 9 0 1 1-3-6.7L21 8"/><path d="M21 3v5h-5"/></svg>
          <span>{{ t('刷新') }}</span>
        </button>
      </div>
    </header>

    <div class="stats-grid">
      <div class="stat-card"><div class="stat-icon amber">⏳</div><div><div class="stat-value">{{ pendingCount }}</div><div class="stat-label">{{ t('待处理') }}</div></div></div>
      <div class="stat-card"><div class="stat-icon green">✓</div><div><div class="stat-value">{{ approvedCount }}</div><div class="stat-label">{{ t('已同意') }}</div></div></div>
      <div class="stat-card"><div class="stat-icon blue">✕</div><div><div class="stat-value">{{ rejectedCount }}</div><div class="stat-label">{{ t('已驳回') }}</div></div></div>
    </div>

    <div class="section">
      <div class="section-header">
        <div class="section-title">{{ t('退课申请') }}</div>
        <div class="tab-group">
          <button class="tab-btn" :class="{ active: filters.status === 'pending' }" @click="filters.status = 'pending'">{{ t('待处理') }}</button>
          <button class="tab-btn" :class="{ active: filters.status === 'all' }" @click="filters.status = 'all'">{{ t('全部') }}</button>
          <button class="tab-btn" :class="{ active: filters.status === 'approved' }" @click="filters.status = 'approved'">{{ t('已同意') }}</button>
          <button class="tab-btn" :class="{ active: filters.status === 'rejected' }" @click="filters.status = 'rejected'">{{ t('已驳回') }}</button>
        </div>
      </div>

      <!-- 筛选轨道 chips：关键词，读写同一个 filters -->
      <div class="dr-filtercard">
        <div class="dr-chip-row">
          <div class="dr-search-inline">
            <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>
            <input v-model="filters.keyword" :placeholder="t('搜索学生 / 学号 / 课程…')" />
          </div>
          <button v-if="hasFilter" class="dr-reset" @click="clearFilters">{{ t('清空') }}</button>
        </div>
      </div>

      <div class="data-table">
        <table>
          <thead>
            <tr>
              <th>{{ t('学生') }}</th><th>{{ t('学号') }}</th><th>{{ t('课程') }}</th>
              <th>{{ t('轮次') }}</th><th>{{ t('申请时间') }}</th><th>{{ t('状态') }}</th><th>{{ t('操作') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in paged" :key="r.id">
              <td>{{ r.studentName }}</td>
              <td>{{ r.studentNo || '-' }}</td>
              <td>{{ r.courseName }}</td>
              <td>{{ t('第 {n} 轮', { n: r.roundNo }) }}</td>
              <td>{{ fmt(r.createdAt) }}</td>
              <td><span class="dr-status" :class="r.status">{{ statusText(r.status) }}</span></td>
              <td>
                <template v-if="r.status === 'pending'">
                  <button class="btn btn-primary btn-sm" @click="approve(r)">{{ t('同意退课') }}</button>
                  <button class="btn btn-outline btn-sm" @click="reject(r)">{{ t('驳回') }}</button>
                </template>
                <span v-else class="dr-remark" :title="r.remark || ''">{{ r.remark || '-' }}</span>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-if="!shown.length" class="dr-empty">
          <b>{{ list.length ? t('没有匹配的退课申请') : t('暂无退课申请') }}</b>
          <div v-if="suggestChips.length" class="dr-suggest">
            <span class="dr-suggest-label">{{ t('是不是想找') }}</span>
            <button v-for="s in suggestChips" :key="s" class="dr-suggest-chip" @click="filters.keyword = s">{{ s }}</button>
          </div>
          <button v-if="hasFilter" class="dr-empty-reset" @click="clearFilters">{{ t('清空筛选') }}</button>
        </div>
        <div v-if="shown.length > pageSize" class="dr-pagebar">
          <span class="dr-pageinfo">{{ t('共 {n} 条 · 第 {p} / {total} 页', { n: shown.length, p: page, total: totalPages }) }}</span>
          <div class="dr-pager">
            <button class="dr-pg-btn" :disabled="page <= 1" @click="page--">‹</button>
            <button v-for="p in pageItems" :key="p" class="dr-pg-btn" :class="{ active: p === page }" @click="page = p">{{ p }}</button>
            <button class="dr-pg-btn" :disabled="page >= totalPages" @click="page++">›</button>
          </div>
        </div>
      </div>
    </div>

    <!-- ================= 命令面板 ================= -->
    <Teleport to="body">
      <div v-if="cmdOpen" class="cmd-overlay" @click="closeCommand(false)">
        <div class="cmd-panel" @click.stop>
          <div class="cmd-input-row">
            <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>
            <input ref="cmdInput" v-model="cmdTerm" :placeholder="t('搜索学生 / 学号 / 课程，支持拼音首字母…')" />
            <kbd class="dr-kbd">esc</kbd>
          </div>
          <div class="cmd-results">
            <template v-if="cmdItems.length">
              <div v-if="cmdRows.length" class="cmd-group">
                <div class="cmd-group-h">{{ t('退课申请') }} <span>{{ cmdRows.length }}</span></div>
                <button v-for="(r, i) in cmdRows" :key="r.id" class="cmd-item" :class="{ active: cmdCursor === i }" @mouseenter="cmdCursor = i" @click="selectCmd(cmdItems[i])">
                  <span class="cmd-item-ico user"><svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/></svg></span>
                  <span class="cmd-item-main"><b>{{ r.studentName }} · {{ r.courseName }}</b><small>{{ r.studentNo || '—' }} · {{ statusText(r.status) }} · {{ t('第 {n} 轮', { n: r.roundNo }) }}</small></span>
                  <span class="cmd-enter" v-if="cmdCursor === i">↵</span>
                </button>
              </div>
            </template>
            <div v-else class="cmd-empty">{{ t('没有联想结果，试试姓名 / 学号 / 课程 / 拼音首字母') }}</div>
          </div>
          <div class="cmd-footer">
            <span><kbd class="dr-kbd">↑</kbd><kbd class="dr-kbd">↓</kbd> {{ t('导航') }}</span>
            <span><kbd class="dr-kbd">↵</kbd> {{ t('选中') }}</span>
            <span class="cmd-footer-tip">{{ t('选中申请定位到该条') }}</span>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api'
import { t } from '@/i18n'
import { diceCoefficient } from '@/utils/similarity'
import { initialsOf } from '@/utils/pinyin'

const isMac = typeof navigator !== 'undefined' && /Mac|iPhone|iPad/.test(navigator.platform)
const list = ref<any[]>([])
// 单一状态源：状态 Tab / 搜索 / 命令面板读写同一 filters
const filters = reactive({ keyword: '' as string, status: 'pending' as string })
const page = ref(1)
const pageSize = ref(20)
const shown = computed(() => {
  const k = filters.keyword.trim().toLowerCase()
  return list.value.filter(r => {
    if (filters.status !== 'all' && r.status !== filters.status) return false
    if (k && !(`${r.studentName || ''} ${r.studentNo || ''} ${r.courseName || ''}`.toLowerCase().includes(k))) return false
    return true
  })
})
const totalPages = computed(() => Math.max(1, Math.ceil(shown.value.length / pageSize.value)))
const paged = computed(() => shown.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value))
const pageItems = computed(() => Array.from({ length: totalPages.value }, (_, i) => i + 1).filter(p => totalPages.value <= 7 || p === 1 || p === totalPages.value || Math.abs(p - page.value) <= 1))
const hasFilter = computed(() => !!(filters.keyword || filters.status !== 'pending'))
function clearFilters() { filters.keyword = ''; filters.status = 'pending'; page.value = 1 }
watch([() => filters.keyword, () => filters.status], () => { page.value = 1 })
watch(totalPages, v => { if (page.value > v) page.value = v })
const pendingCount = computed(() => list.value.filter(r => r.status === 'pending').length)
const approvedCount = computed(() => list.value.filter(r => r.status === 'approved').length)
const rejectedCount = computed(() => list.value.filter(r => r.status === 'rejected').length)

async function load() {
  try {
    const r: any = await http.get('/drop-requests')
    list.value = r?.data || []
  } catch (e: any) {
    ElMessage.error(e?.message || t('加载退课申请失败'))
  }
}

async function approve(r: any) {
  try {
    await ElMessageBox.confirm(
      t('同意后《{n}》将立即从 {s} 的已录取课程中移除，确定？', { n: r.courseName, s: r.studentName }),
      t('同意退课'), { type: 'warning', confirmButtonText: t('确定'), cancelButtonText: t('取消') })
  } catch { return }
  try {
    const res: any = await http.post(`/drop-requests/${r.id}/approve`, {})
    ElMessage.success(res?.message || t('已同意退课'))
    load()
  } catch (e: any) { ElMessage.error(e?.message || t('操作失败')) }
}

async function reject(r: any) {
  let remark = ''
  try {
    const v = await ElMessageBox.prompt(t('可填写驳回原因，学生会在消息通知里看到'), t('驳回退课申请'), {
      type: 'warning', confirmButtonText: t('确定'), cancelButtonText: t('取消'),
      inputPlaceholder: t('驳回原因（选填）')
    })
    remark = v?.value || ''
  } catch { return }
  try {
    const res: any = await http.post(`/drop-requests/${r.id}/reject`, { remark })
    ElMessage.success(res?.message || t('已驳回申请'))
    load()
  } catch (e: any) { ElMessage.error(e?.message || t('操作失败')) }
}

function statusText(s: string) {
  return s === 'pending' ? t('待处理') : s === 'approved' ? t('已同意') : t('已驳回')
}
function fmt(ms: number) {
  if (!ms) return '-'
  const d = new Date(ms), p = (n: number) => String(n).padStart(2, '0')
  return `${d.getMonth() + 1}-${d.getDate()} ${p(d.getHours())}:${p(d.getMinutes())}`
}

/* ===== 空结果 Dice ===== */
function charDice(a: string, b: string): number {
  const A = new Set(Array.from(a.toLowerCase().replace(/\s+/g, '')))
  const B = new Set(Array.from(b.toLowerCase().replace(/\s+/g, '')))
  if (!A.size || !B.size) return 0
  let inter = 0
  for (const c of A) if (B.has(c)) inter++
  return (2 * inter) / (A.size + B.size)
}
const suggestChips = computed(() => {
  const k = filters.keyword.trim()
  if (!k) return []
  const names = new Set<string>()
  for (const r of list.value) { names.add(r.studentName); if (r.studentNo) names.add(r.studentNo); if (r.courseName) names.add(r.courseName) }
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
function pyOf(r: any) { return initialsOf(`${r.studentName || ''} ${r.studentNo || ''} ${r.courseName || ''}`) }
function matchText(haystack: string, py: string, qq: string): boolean {
  const ql = qq.toLowerCase().replace(/\s+/g, '')
  if (!ql) return true
  if ((haystack || '').toLowerCase().replace(/\s+/g, '').includes(ql)) return true
  return !!py && py.includes(ql)
}
function openCommand() { cmdTerm.value = filters.keyword; cmdCursor.value = 0; cmdOpen.value = true; nextTick(() => cmdInput.value?.focus()) }
function closeCommand(clear: boolean) {
  cmdOpen.value = false
  if (clear) { cmdTerm.value = ''; filters.keyword = '' }
  else { cmdTerm.value = filters.keyword }
}
watch(cmdTerm, v => { if (cmdOpen.value) filters.keyword = v })
const cmdRows = computed(() => {
  const qq = cmdTerm.value.trim()
  const arr = !qq ? list.value.slice(0, 8)
    : list.value.filter(r => matchText(`${r.studentName || ''} ${r.studentNo || ''} ${r.courseName || ''}`, pyOf(r), qq)).slice(0, 8)
  return arr
})
const cmdItems = computed(() => cmdRows.value.map(r => ({ kind: 'row' as const, raw: r })))
watch(cmdItems, () => { if (cmdCursor.value >= cmdItems.value.length) cmdCursor.value = 0 })
function selectCmd(item: { kind: string; raw: any } | undefined) {
  if (!item) return
  filters.status = item.raw.status; filters.keyword = ''
  cmdOpen.value = false; cmdTerm.value = ''
  page.value = 1
  ElMessage.success(t('已定位：{n}', { n: item.raw.studentName }))
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
.stat-icon { width: 38px; height: 38px; display: flex; align-items: center; justify-content: center; border-radius: 10px; font-size: 18px; font-weight: 700; }
.stat-icon.amber { background: rgba(245,158,11,.12); color: #d97706; }
.stat-icon.green { background: rgba(43,177,135,.12); color: #16a34a; }
.stat-icon.blue { background: rgba(56,189,248,.12); color: #0284c7; }
.dr-status { display: inline-block; padding: 2px 10px; border-radius: 99px; font-size: 12px; font-weight: 600; }
.dr-status.pending { background: rgba(245,158,11,.14); color: #d97706; }
.dr-status.approved { background: rgba(43,177,135,.14); color: #16a34a; }
.dr-status.rejected { background: rgba(148,163,184,.16); color: var(--text-light); }
.dr-remark { font-size: 12px; color: var(--text-light); }
.dr-empty { padding: 40px 20px; text-align: center; font-size: 13px; color: var(--text-light); }
.dr-empty b { display: block; font-size: 14px; color: var(--text); }
.data-table .btn-sm + .btn-sm { margin-left: 6px; }

/* ================= 检索体验升级：顶栏 / 筛选 chips / 命令面板 / 分页 ================= */
.dr-page { --g: #10b981; --g-dark: #059669; --g-soft: rgba(16,185,129,.10); }
.ic { display: block; }
.dr-topbar { position: sticky; top: 0; z-index: 40; display: flex; align-items: center; gap: 16px; flex-wrap: wrap; padding: 12px 16px; background: color-mix(in srgb, var(--card, #fff) 86%, transparent); backdrop-filter: saturate(1.4) blur(14px); -webkit-backdrop-filter: saturate(1.4) blur(14px); border: 0.5px solid var(--border, #e5e7eb); border-radius: 16px; box-shadow: 0 1px 2px rgba(16,24,40,.03), 0 8px 24px rgba(16,24,40,.04); }
.dr-brand { display: flex; align-items: center; gap: 12px; flex: 0 0 auto; }
.dr-brand-icon { width: 40px; height: 40px; border-radius: 12px; display: grid; place-items: center; color: #fff; background: linear-gradient(135deg, #34d399, var(--g-dark)); box-shadow: 0 4px 12px rgba(16,185,129,.32); }
.dr-brand-icon .ic { width: 21px; height: 21px; }
.dr-title { font-size: 17px; font-weight: 700; color: var(--text, #0f172a); line-height: 1.2; }
.dr-subtitle { font-size: 12px; color: var(--text-lighter, #94a3b8); margin-top: 2px; }
.dr-search-trigger { flex: 1 1 320px; max-width: 520px; min-width: 180px; height: 38px; display: flex; align-items: center; gap: 9px; padding: 0 10px 0 14px; border: 1px solid var(--border, #e5e7eb); border-radius: 999px; background: var(--card, #fff); color: var(--text-lighter, #94a3b8); cursor: pointer; transition: all .18s; font-family: inherit; }
.dr-search-trigger:hover { border-color: var(--g); box-shadow: 0 0 0 3px var(--g-soft); }
.dr-search-trigger .ic { width: 17px; height: 17px; flex: 0 0 auto; }
.dr-search-ph { flex: 1; text-align: left; font-size: 13px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.dr-kbd { font-family: inherit; font-size: 11px; line-height: 1; white-space: nowrap; padding: 4px 7px; border-radius: 6px; border: 1px solid var(--border, #e5e7eb); background: color-mix(in srgb, var(--card, #fff) 92%, #000 8%); color: var(--text-light, #64748b); flex: 0 0 auto; }
.dr-top-actions { display: flex; align-items: center; gap: 8px; flex: 0 0 auto; margin-left: auto; }
.dr-btn { display: inline-flex; align-items: center; justify-content: center; gap: 6px; height: 36px; padding: 0 14px; border-radius: 10px; font-family: inherit; font-size: 13px; font-weight: 600; cursor: pointer; transition: all .16s; white-space: nowrap; border: 1px solid transparent; }
.dr-btn .ic { width: 15px; height: 15px; }
.dr-btn-outline { background: var(--card, #fff); color: var(--text, #334155); border-color: var(--border, #e5e7eb); }
.dr-btn-outline:hover { border-color: var(--g); color: var(--g); }
.dr-filtercard { padding: 10px 14px; border: 0.5px solid var(--border); border-radius: 12px; background: var(--card); margin: 10px 0 14px; }
.dr-chip-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.dr-search-inline { display: flex; align-items: center; gap: 8px; height: 34px; padding: 0 12px; border: 1px solid var(--border); border-radius: 999px; background: var(--card); min-width: 260px; flex: 0 1 340px; }
.dr-search-inline .ic { width: 15px; height: 15px; color: var(--text-lighter); flex: 0 0 auto; }
.dr-search-inline input { flex: 1; min-width: 0; border: none; outline: none; background: transparent; color: var(--text); font-family: inherit; font-size: 13px; }
.dr-search-inline input::placeholder { color: var(--text-lighter); }
.dr-search-inline:focus-within { border-color: var(--g); box-shadow: 0 0 0 3px var(--g-soft); }
.dr-reset { border: none; background: none; color: var(--text-lighter); font-family: inherit; font-size: 12.5px; cursor: pointer; padding: 6px 8px; margin-left: auto; }
.dr-reset:hover { color: #ef4444; }
.dr-suggest { display: flex; align-items: center; gap: 7px; flex-wrap: wrap; justify-content: center; margin-top: 14px; }
.dr-suggest-label { font-size: 12px; color: var(--text-light); }
.dr-suggest-chip { border: 1px solid var(--border); background: var(--card); color: var(--g-dark); font-family: inherit; font-size: 12.5px; padding: 5px 11px; border-radius: 999px; cursor: pointer; transition: all .15s; }
.dr-suggest-chip:hover { border-color: var(--g); background: var(--g-soft); }
.dr-empty-reset { margin-top: 12px; border: none; background: none; color: var(--g); font-family: inherit; font-size: 12.5px; cursor: pointer; text-decoration: underline; text-underline-offset: 3px; }
.dr-pagebar { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 10px 14px; border-top: 1px solid var(--border); }
.dr-pageinfo { font-size: 12px; color: var(--text-light); }
.dr-pager { display: flex; align-items: center; gap: 4px; }
.dr-pg-btn { min-width: 28px; height: 28px; padding: 0 7px; border: 1px solid var(--border); border-radius: 8px; background: var(--card); color: var(--text-light); font-family: inherit; font-size: 12px; cursor: pointer; }
.dr-pg-btn:hover:not(:disabled) { border-color: var(--g); color: var(--g); }
.dr-pg-btn.active { background: var(--g); border-color: var(--g); color: #fff; font-weight: 700; }
.dr-pg-btn:disabled { opacity: .4; cursor: not-allowed; }
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
.cmd-item-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.cmd-item-main b { font-size: 13px; font-weight: 600; color: var(--text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cmd-item-main small { font-size: 11px; color: var(--text-lighter); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cmd-enter { color: var(--g); font-size: 14px; flex: 0 0 auto; }
.cmd-empty { padding: 36px 16px; text-align: center; color: var(--text-lighter); font-size: 13px; }
.cmd-footer { display: flex; align-items: center; gap: 16px; padding: 10px 16px; border-top: 0.5px solid var(--border); background: color-mix(in srgb, var(--card) 94%, #000 6%); font-size: 11px; color: var(--text-lighter); }
.cmd-footer span { display: inline-flex; align-items: center; gap: 4px; }
.cmd-footer .dr-kbd { padding: 2px 6px; }
.cmd-footer-tip { margin-left: auto; color: var(--g); }
@keyframes cmdFade { from { opacity: 0 } to { opacity: 1 } }
@keyframes cmdUp { from { opacity: 0; transform: translateY(-10px) scale(.985) } to { opacity: 1; transform: none } }
@media (max-width: 1100px) { .dr-search-trigger { order: 3; flex-basis: 100%; max-width: none; } .dr-top-actions { order: 2; } }
@media (max-width: 560px) { .dr-topbar { padding: 12px; } .dr-kbd { display: none; } .dr-search-inline { min-width: 0; } }
</style>
