<template>
  <div class="stats-page liquid logs-page">
    <div class="logs-orb" aria-hidden="true" />

    <!-- ===== 顶部 sticky 导航栏 ===== -->
    <header class="lg-topbar">
      <div class="lg-brand">
        <div class="lg-brand-icon">
          <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 4v16h16"/><path d="m7 15 3-4 3 2 5-6"/></svg>
        </div>
        <div class="lg-brand-text">
          <div class="lg-title">{{ t('系统日志') }}</div>
          <div class="lg-subtitle">{{ t('共 {n} 条操作记录', { n: total }) }}</div>
        </div>
      </div>
      <button class="lg-search-trigger" @click="openCommand" :aria-label="t('打开搜索')">
        <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>
        <span class="lg-search-ph">{{ filters.keyword || t('搜索用户 / 操作 / IP / 类型，支持拼音首字母…') }}</span>
        <kbd class="lg-kbd">{{ isMac ? '⌘' : 'Ctrl+' }} K</kbd>
      </button>
      <div class="lg-top-actions">
        <button class="lg-btn lg-btn-outline" @click="exportCsv">
          <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M12 4v12m0 0 4-4m-4 4-4-4"/><path d="M4 20h16"/></svg>
          <span>{{ t('导出 CSV') }}</span>
        </button>
      </div>
    </header>

    <!-- ===== 原版 statCardPro 3 卡 ===== -->
    <div class="stats-grid">
      <div class="stat-card stat-pro">
        <span class="card-aura" aria-hidden="true" />
        <div class="stat-pro-top">
          <div class="stat-icon blue" v-html="svgIcon('bar-chart')" />
          <span class="delta-cap">{{ t('较前15分钟') }}</span>
          <span class="stat-delta" :class="deltaPct >= 0 ? 'up' : 'down'">{{ deltaPct >= 0 ? '+' : '' }}{{ deltaPct.toFixed(1) }}%</span>
        </div>
        <div class="stat-value">{{ totalAll }}</div>
        <div class="stat-label">{{ t('总操作数') }}</div>
        <svg class="stat-spark" viewBox="0 0 120 36" preserveAspectRatio="none">
          <polygon :points="areaPts" fill="#14b8a6" opacity="0.14" />
          <polyline :points="linePts" fill="none" stroke="#14b8a6" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </div>

      <div class="stat-card stat-pro">
        <span class="card-aura" aria-hidden="true" />
        <div class="stat-pro-top">
          <div class="stat-icon green" v-html="svgIcon('check-circle')" />
        </div>
        <div class="stat-value">{{ animSucc }}</div>
        <div class="stat-label">{{ t('成功 · {p}%', { p: succRate.toFixed(1) }) }}</div>
        <svg class="stat-spark" viewBox="0 0 120 36" preserveAspectRatio="none">
          <polygon :points="succAreaPts" fill="#10b981" opacity="0.14" />
          <polyline :points="succLinePts" fill="none" stroke="#10b981" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </div>

      <div class="stat-card stat-pro">
        <span class="card-aura" aria-hidden="true" />
        <div class="stat-pro-top">
          <div class="stat-icon red" v-html="svgIcon('x-circle')" />
        </div>
        <div class="stat-value">{{ animFail }}</div>
        <div class="stat-label">{{ t('失败 · {p}%', { p: (100 - succRate).toFixed(1) }) }}</div>
        <svg class="stat-spark" viewBox="0 0 120 36" preserveAspectRatio="none">
          <polygon :points="failAreaPts" fill="#ef4444" opacity="0.14" />
          <polyline :points="failLinePts" fill="none" stroke="#ef4444" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </div>
    </div>

    <!-- ===== 左：类型分布环形图 + 右：用户操作排行 ===== -->
    <div class="logs-two-col">
      <!-- 交互式 SVG 环形图：悬停扇区/图例联动，中心显示明细 -->
      <div class="card glow-corner donut-card">
        <div class="donut-head">
          <div class="section-title"><span v-html="svgIcon('pie-chart')" />{{ t('操作类型分布') }}</div>
          <span class="donut-hint">{{ t('悬停查看明细') }}</span>
        </div>
        <div v-if="pieSlices.length" class="donut-wrap donut-wrap-v2">
          <div class="donut-stage" @mouseleave="hoverIdx = -1">
            <div class="donut-halo" :style="{ '--halo': topGlowColor }" />
            <svg class="donut-svg" viewBox="0 0 200 200" role="img" :aria-label="t('操作类型分布环形图')">
              <defs>
                <linearGradient v-for="s in segments" :id="`dlg-${s.i}`" :key="'g' + s.i"
                  gradientUnits="userSpaceOnUse" x1="50" y1="30" x2="152" y2="170">
                  <stop offset="0%" :stop-color="s.color" />
                  <stop offset="100%" :stop-color="s.color" stop-opacity=".55" />
                </linearGradient>
              </defs>
              <circle class="donut-track" cx="100" cy="100" r="62" />
              <circle class="donut-orbit" cx="100" cy="100" r="88" />
              <g transform="rotate(-90 100 100)">
                <circle v-for="s in segments" :key="'s' + s.i" class="donut-seg"
                  :class="{ active: hoverIdx === s.i, dim: hoverIdx !== -1 && hoverIdx !== s.i }"
                  cx="100" cy="100" r="62" :style="segStyle(s)"
                  @mouseenter="hoverIdx = s.i" />
              </g>
            </svg>
            <div class="donut-core">
              <Transition name="core-swap" mode="out-in">
                <div v-if="hoverSlice" :key="'h' + hoverIdx" class="core-inner">
                  <b>{{ hoverSlice.pct.toFixed(1) }}<i>%</i></b>
                  <span>{{ t(hoverSlice.label) }}</span>
                  <em>{{ t('{n} 次', { n: hoverSlice.value.toLocaleString() }) }}</em>
                </div>
                <div v-else key="total" class="core-inner">
                  <b>{{ totalByType.toLocaleString() }}</b>
                  <span>{{ t('次操作') }}</span>
                  <em>{{ t('共 {n} 类', { n: pieSlices.length }) }}</em>
                </div>
              </Transition>
            </div>
          </div>
          <div class="donut-legend" @mouseleave="hoverIdx = -1">
            <div v-for="(s, i) in pieSlices" :key="s.label" class="pie-item"
              :class="{ on: hoverIdx === i }"
              :style="{ '--c': s.color, '--w': s.pct.toFixed(2) + '%' }"
              @mouseenter="hoverIdx = i">
              <span class="pie-dot" :style="{ background: s.color, animationDelay: (i * 0.35) + 's' }" />
              <span class="pie-label">{{ t(s.label) }}</span>
              <span class="pie-pct">{{ s.pct.toFixed(1) }}%</span>
              <span class="pie-cnt">{{ s.value.toLocaleString() }}</span>
            </div>
          </div>
        </div>
        <div v-else class="empty-state"><div class="empty-icon" v-html="svgIcon('pie-chart')" /><p>{{ t('暂无数据') }}</p></div>
      </div>

      <!-- bar-chart / bar-item / bar-track / bar-fill（原版 CSS 全有），前三名带奖牌 -->
      <div class="card bar-card">
        <div class="section-title"><span v-html="svgIcon('bar-chart')" />{{ t('用户操作排行') }}</div>
        <div v-if="topUsers.length" class="bar-chart">
          <div v-for="(u, i) in topUsers" :key="u.userName" class="bar-item">
            <div class="bar-label">
              <span v-if="i < 3" class="bar-rank" :class="'r' + (i + 1)">{{ i + 1 }}</span>
              <span :title="u.userName">{{ u.userName }}</span>
            </div>
            <div class="bar-track"><div class="bar-fill" :style="{ height: Math.max(2, u.count / maxUser * 100) + '%', animationDelay: (i * 60) + 'ms' }" /></div>
            <div class="bar-value">{{ u.count }}</div>
          </div>
        </div>
        <div v-else class="empty-state"><div class="empty-icon" v-html="svgIcon('users')" /><p>{{ t('暂无数据') }}</p></div>
      </div>
    </div>

    <!-- ===== 操作时间热力：24 小时 × 7 天 ===== -->
    <div class="section" style="margin-top:20px">
      <div class="section-header">
        <div class="section-title"><span v-html="svgIcon('clock')" />{{ t('操作时间热力') }}</div>
        <div class="hm-meta" v-if="heat.max">
          {{ t('最活跃：') }}<b>{{ t(heat.peakDay) }} {{ t('{h} 时', { h: heat.peakHour }) }}</b>{{ t('（{n} 次）', { n: heat.peakCount }) }} · {{ t('共 {n} 条操作', { n: heat.total }) }}
        </div>
      </div>
      <div v-if="heat.max" class="hm-wrap">
        <div class="hm-row hm-axis">
          <span class="hm-day-label"></span>
          <div class="hm-cells">
            <span v-for="h in 24" :key="'h' + h" class="hm-hour" :class="{ major: (h - 1) % 6 === 0 }">{{ h - 1 }}</span>
          </div>
        </div>
        <div v-for="(row, d) in heat.rows" :key="d" class="hm-row">
          <span class="hm-day-label">{{ t(days[d]) }}</span>
          <div class="hm-cells">
            <span
              v-for="(v, h) in row" :key="h"
              class="hm-cell"
              :class="{ peak: isPeak(d, h) }"
              :style="{ background: cellColor(v) }"
              :title="t('{d} {a}:00–{b}:00 · {n} 次操作', { d: t(days[d]), a: h, b: h + 1, n: v })"
            />
          </div>
        </div>
        <div class="hm-scale">
          <span>{{ t('少') }}</span>
          <i v-for="a in 5" :key="a" :style="{ background: `rgba(43,177,135,${0.12 + a * 0.16})` }" />
          <span>{{ t('多') }}</span>
          <em>{{ t('格子越亮，说明该时段操作越密集——看学生到底几点抢课') }}</em>
        </div>
      </div>
      <div v-else class="empty-state"><div class="empty-icon" v-html="svgIcon('clock')" /><p>{{ t('暂无日志数据') }}</p></div>
    </div>

    <!-- ===== 明细：玻璃 section + 筛选工具条 + 彩色徽章表格 + 分页 ===== -->
    <div class="section" style="margin-top:20px">
      <div class="section-header">
        <div class="section-title">{{ t('操作日志明细') }}</div>
        <div class="lg-count">{{ t('共 {n} 条记录', { n: total }) }}</div>
      </div>

      <!-- 筛选轨道 chips：状态 / 类型，读写同一个 filters -->
      <div class="lg-toolbar">
        <div class="lg-chips">
          <button class="cat-tab" :class="{ active: filters.status === '' }" @click="setStatus('')">{{ t('全部') }}</button>
          <button class="cat-tab lg-ok" :class="{ active: filters.status === '成功' }" @click="setStatus('成功')">{{ t('成功 {n}', { n: succ }) }}</button>
          <button class="cat-tab lg-err" :class="{ active: filters.status === '失败' }" @click="setStatus('失败')">{{ t('失败 {n}', { n: fail }) }}</button>
        </div>
        <div class="lg-chips">
          <button class="cat-tab" :class="{ active: filters.type === '' }" @click="setType('')">{{ t('全部类型') }}</button>
          <button v-for="[ty, c] in typeChips" :key="ty" class="cat-tab" :class="{ active: filters.type === ty }" @click="setType(ty)">
            <span class="lg-dot" :style="{ background: typeColor(ty) }" />{{ ty }} <i class="lg-chip-cnt">{{ c }}</i>
          </button>
        </div>
        <button v-if="hasFilter" class="lg-reset" @click="clearFilters">{{ t('清空') }}</button>
      </div>

      <div v-if="paged.length" class="data-table">
        <table>
          <thead><tr><th>{{ t('时间') }}</th><th>{{ t('用户') }}</th><th>{{ t('类型') }}</th><th>{{ t('操作') }}</th><th>{{ t('状态') }}</th><th>{{ t('IP') }}</th></tr></thead>
          <tbody>
            <tr v-for="l in paged" :key="l.id" class="lg-row" @click="lgDetail = l">
              <td class="lg-time">{{ fmt(l.createdAt) }}</td>
              <td>
                <div class="lg-user">
                  <span class="lg-avatar" :style="{ background: avatarBg(l.userName) }">{{ (l.userName || '?').slice(0, 1) }}</span>
                  <span>{{ l.userName }}</span>
                </div>
              </td>
              <td><span class="lg-type"><span class="lg-dot" :style="{ background: typeColor(l.type), boxShadow: '0 0 6px ' + typeColor(l.type) + '88' }" />{{ t(l.type) }}</span></td>
              <td><code class="lg-op" :title="l.operation">{{ l.operation }}</code></td>
              <td><span class="lg-status" :class="l.status === '成功' ? 'ok' : 'err'">{{ t(l.status) }}</span></td>
              <td class="lg-ip">{{ l.ip || '—' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="lg-empty">
        <div class="lg-empty-title">{{ total ? t('没有匹配的日志，试试调整筛选条件') : t('暂无日志') }}</div>
        <div v-if="suggestChips.length" class="lg-suggest">
          <span class="lg-suggest-label">{{ t('是不是想找') }}</span>
          <button v-for="s in suggestChips" :key="s" class="lg-suggest-chip" @click="filters.keyword = s">{{ s }}</button>
        </div>
        <button v-if="hasFilter" class="lg-empty-reset" @click="clearFilters">{{ t('清空筛选') }}</button>
      </div>

      <div v-if="total" class="lg-pagebar">
        <div class="lg-total">{{ t('第 {a}–{b} 条 / 共 {n} 条', { a: pageStart + 1, b: pageEnd, n: total }) }}</div>
        <div class="lg-pager">
          <select v-model.number="pageSize" class="lg-size">
            <option :value="10">{{ t('10 条/页') }}</option>
            <option :value="20">{{ t('20 条/页') }}</option>
            <option :value="50">{{ t('50 条/页') }}</option>
            <option :value="100">{{ t('100 条/页') }}</option>
          </select>
          <button :disabled="page <= 1" @click="page--">‹</button>
          <span class="lg-pageinfo">{{ page }} / {{ pageCount }}</span>
          <button :disabled="page >= pageCount" @click="page++">›</button>
        </div>
      </div>
    </div>

    <!-- ================= 命令面板 ================= -->
    <Teleport to="body">
      <div v-if="cmdOpen" class="cmd-overlay" @click="closeCommand(false)">
        <div class="cmd-panel" @click.stop>
          <div class="cmd-input-row">
            <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>
            <input ref="cmdInput" v-model="cmdTerm" :placeholder="t('搜索用户 / 操作 / IP / 日志类型，支持拼音首字母…')" />
            <kbd class="lg-kbd">esc</kbd>
          </div>
          <div class="cmd-results">
            <template v-if="cmdItems.length">
              <div v-if="cmdLogs.length" class="cmd-group">
                <div class="cmd-group-h">{{ t('日志') }} <span>{{ cmdLogs.length }}</span></div>
                <button v-for="(l, i) in cmdLogs" :key="l.id" class="cmd-item" :class="{ active: cmdCursor === i }" @mouseenter="cmdCursor = i" @click="selectCmd(cmdItems[i])">
                  <span class="cmd-item-ico user"><span class="cmd-avatar" :style="{ background: avatarBg(l.userName) }">{{ (l.userName || '?').slice(0, 1) }}</span></span>
                  <span class="cmd-item-main"><b>{{ l.userName }} · {{ t(l.type) }}</b><small>{{ l.operation }} · {{ t(l.status) }} · {{ fmt(l.createdAt) }}</small></span>
                  <span class="cmd-enter" v-if="cmdCursor === i">↵</span>
                </button>
              </div>
              <div v-if="cmdFilters.length" class="cmd-group">
                <div class="cmd-group-h">{{ t('快捷筛选') }} <span>{{ cmdFilters.length }}</span></div>
                <button v-for="(o, j) in cmdFilters" :key="o.value" class="cmd-item" :class="{ active: cmdCursor === cmdLogs.length + j }" @mouseenter="cmdCursor = cmdLogs.length + j" @click="selectCmd(cmdItems[cmdLogs.length + j])">
                  <span class="cmd-item-ico org"><svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M4 6h16M4 12h16M4 18h16"/></svg></span>
                  <span class="cmd-item-main"><b>{{ o.label }}</b><small>{{ t('按此类型筛选日志') }}</small></span>
                  <span class="cmd-enter" v-if="cmdCursor === cmdLogs.length + j">↵</span>
                </button>
              </div>
            </template>
            <div v-else class="cmd-empty">{{ cmdLoading ? t('搜索中…') : t('没有联想结果，试试用户 / 操作 / 拼音首字母') }}</div>
          </div>
          <div class="cmd-footer">
            <span><kbd class="lg-kbd">↑</kbd><kbd class="lg-kbd">↓</kbd> {{ t('导航') }}</span>
            <span><kbd class="lg-kbd">↵</kbd> {{ t('选中') }}</span>
            <span class="cmd-footer-tip">{{ t('选中日志看详情 · 选中类型即筛选') }}</span>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- ================= 日志详情抽屉 ================= -->
    <Teleport to="body">
      <div v-if="lgDetail" class="lg-mask" @click="lgDetail = null">
        <div class="lg-drawer" @click.stop>
          <div class="lg-drawer-h">
            <div><b>{{ lgDetail.userName }}</b><span>{{ t(lgDetail.type) }} · {{ t(lgDetail.status) }}</span></div>
            <button class="lg-close" @click="lgDetail = null">×</button>
          </div>
          <div class="lg-drawer-body">
            <div v-for="f in detailFields" :key="f.label" class="lg-field"><span>{{ f.label }}</span><b>{{ f.value || '—' }}</b></div>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, toRefs, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api'
import { useCountUp } from '@/utils/useCountUp'
import { t } from '@/i18n'
import { diceCoefficient } from '@/utils/similarity'
import { initialsOf } from '@/utils/pinyin'

const isMac = typeof navigator !== 'undefined' && /Mac|iPhone|iPad/.test(navigator.platform)
const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`
const PIE_COLORS = ['#34d399', '#22d3ee', '#60a5fa', '#f59e0b', '#a78bfa', '#94a3b8']

const logs = ref<any[]>([])
const byType = ref<Record<string, number>>({})
const byUser = ref<any[]>([])
const byStatus = ref<Record<string, number>>({})

/* ===== 操作时间热力（24 小时 × 7 天） ===== */
const days = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
const heat = ref<any>({ rows: [], max: 0, total: 0, peakDay: null, peakHour: null, peakCount: 0 })
function cellColor(v: number): string {
  if (!v || !heat.value.max) return 'rgba(148,163,184,.07)'
  return `rgba(43,177,135,${(0.14 + (v / heat.value.max) * 0.78).toFixed(3)})`
}
function isPeak(d: number, h: number): boolean {
  return heat.value.peakDay === days[d] && heat.value.peakHour === h
}

const typeEntries = computed(() => Object.entries(byType.value || {}).sort((a, b) => b[1] - a[1]))
const totalByType = computed(() => typeEntries.value.reduce((s, [, v]) => s + (v || 0), 0) || 1)

// 类型 → 颜色（与 donut 配色一致，按占比排序固定映射）
const typeColorMap = computed(() => {
  const m: Record<string, string> = {}
  typeEntries.value.forEach(([t], i) => { m[t] = PIE_COLORS[i % PIE_COLORS.length] })
  return m
})
const typeColor = (t: string) => typeColorMap.value[t] || '#94a3b8'

const typeChips = computed(() => typeEntries.value.slice(0, 6))

// ===== 单一状态源：chips / 命令面板 / 搜索框读写同一个 filters =====
const filters = reactive({ keyword: '' as string, type: '' as string, status: '' as string })
const { keyword: kw, type: fType, status: fStatus } = toRefs(filters)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

function setStatus(s: string) { filters.status = s; page.value === 1 ? loadLogs() : (page.value = 1) }
function setType(ty: string) { filters.type = filters.type === ty ? '' : ty; page.value === 1 ? loadLogs() : (page.value = 1) }
const hasFilter = computed(() => !!(kw.value || filters.type || filters.status))
function clearFilters() {
  kw.value = ''; filters.type = ''; filters.status = ''
  page.value === 1 ? loadLogs() : (page.value = 1)
}
let kwTimer: any = null
watch(kw, () => {
  clearTimeout(kwTimer)
  // 关键词防抖 300ms，输入停顿后才请求
  kwTimer = setTimeout(() => { page.value === 1 ? loadLogs() : (page.value = 1) }, 300)
})
watch(page, loadLogs)
watch(pageSize, () => { page.value === 1 ? loadLogs() : (page.value = 1) })

const pageCount = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
const pageStart = computed(() => (total.value === 0 ? 0 : (page.value - 1) * pageSize.value))
const pageEnd = computed(() => Math.min(total.value, page.value * pageSize.value))
/** 当页日志行（服务端已筛选 + 分页；旧后端返回数组时在 loadLogs 内本地切片兼容） */
const paged = ref<any[]>([])

// ===== donut 逻辑：前5类 + 其余合并为「其他」 =====
const pieSlices = computed(() => {
  const sorted = [...typeEntries.value]
  const top = sorted.slice(0, 5).map(([label, value]) => [label, Number(value)] as [string, number])
  const restSum = sorted.slice(5).reduce((s, [, v]) => s + (Number(v) || 0), 0)
  const otherIdx = top.findIndex(([label]) => label === '其他')
  if (restSum > 0) {
    // 已有真实「其他」类型时直接并入，避免图例出现两个同名项
    if (otherIdx >= 0) top[otherIdx][1] += restSum
    else top.push(['其他', restSum])
  }
  return top.map(([label, value], i) => ({
    label,
    value,
    pct: (value / totalByType.value) * 100,
    color: typeColorMap.value[label] || (i === 5 ? '#94a3b8' : PIE_COLORS[i % PIE_COLORS.length])
  }))
})

// ===== 环形图交互：悬停联动 =====
const hoverIdx = ref(-1)
const hoverSlice = computed(() => (hoverIdx.value >= 0 ? pieSlices.value[hoverIdx.value] : null))

const CIRC = 2 * Math.PI * 62
const revealed = ref(false)
const revealDone = ref(false)

const segments = computed(() => {
  let acc = 0
  return pieSlices.value.map((s, i) => {
    const start = (acc / 100) * CIRC
    acc += s.pct
    const len = (s.pct / 100) * CIRC
    const gap = Math.min(6, len * 0.4)
    return { ...s, i, start, dash: Math.max(len - gap, 1.5) }
  })
})

function segStyle(s: any) {
  const dash = revealed.value ? s.dash : 0
  return {
    stroke: `url(#dlg-${s.i})`,
    strokeDasharray: `${dash} ${CIRC - dash}`,
    strokeDashoffset: `${-s.start}`,
    transitionDelay: revealDone.value ? '0ms' : `${s.i * 70}ms`,
    color: s.color
  }
}

const topGlowColor = computed(() => {
  if (!pieSlices.value.length) return '#14b8a6'
  let maxIdx = 0, maxPct = 0
  pieSlices.value.forEach((s, i) => { if (s.pct > maxPct) { maxPct = s.pct; maxIdx = i } })
  return pieSlices.value[maxIdx].color
})

const totalAll = computed(() => (byStatus.value['成功'] || 0) + (byStatus.value['失败'] || 0))
const succ = computed(() => byStatus.value['成功'] || 0)
const fail = computed(() => byStatus.value['失败'] || 0)
const succRate = computed(() => succ.value / (succ.value + fail.value || 1) * 100)

/* 统计卡数字滚动（成功/失败次数，stats 到达时滚动） */
const animSucc = useCountUp(() => succ.value)
const animFail = useCountUp(() => fail.value)
const topUsers = computed(() => byUser.value.slice(0, 8))
const maxUser = computed(() => Math.max(1, ...topUsers.value.map((u: any) => u.count || 0)))

// ===== 头像渐变（按用户名 hash 取色，稳定不闪烁）=====
const AVATAR_BGS = [
  'linear-gradient(135deg,#4f46e5,#3730a3)',
  'linear-gradient(135deg,#b45309,#9a3412)',
  'linear-gradient(135deg,#0f766e,#115e59)',
  'linear-gradient(135deg,#0369a1,#075985)',
  'linear-gradient(135deg,#be185d,#9d174d)',
  'linear-gradient(135deg,#0e7490,#155e75)'
]
function avatarBg(name: string) {
  let h = 0
  for (const ch of String(name || '')) h = (h * 31 + ch.charCodeAt(0)) >>> 0
  return AVATAR_BGS[h % AVATAR_BGS.length]
}

// sparkline 生成器（原版 sparkSvg 逻辑）
function sparkPoints(vals: number[], w = 120, h = 36) {
  const n = vals.length
  const max = Math.max(...vals, 1)
  const min = Math.min(...vals)
  const range = (max - min) || 1
  return vals.map((x, i) => {
    const px = (i / (n - 1)) * w
    const py = h - 4 - ((x - min) / range) * (h - 12)
    return [px, py] as const
  })
}
function toLine(points: readonly (readonly [number, number])[]) {
  return points.map(([x, y]) => `${x.toFixed(1)},${y.toFixed(1)}`).join(' ')
}
function toArea(points: readonly (readonly [number, number])[], w = 120, h = 36) {
  return toLine(points) + ` ${w},${h} 0,${h}`
}

// ===== 趋势数据源:/stats/live 的最近 30 分钟逐分钟真实聚合(此前是当前页 10 行造假分桶) =====
const liveTrend = ref<{ minute: string; ops: number; fails: number }[]>([])
async function loadLiveTrend() {
  try {
    const r: any = await http.get('/stats/live')
    const tl = r?.data?.timeline
    liveTrend.value = Array.isArray(tl) ? tl.map((t: any) => ({ minute: t.minute, ops: t.ops || 0, fails: t.fails || 0 })) : []
  } catch { /* 保留旧数据 */ }
}

const trendAll = computed(() => liveTrend.value.map(t => t.ops))
const trendSucc = computed(() => liveTrend.value.map(t => Math.max(0, t.ops - t.fails)))
const trendFail = computed(() => liveTrend.value.map(t => t.fails))

const linePts = computed(() => toLine(sparkPoints(trendAll.value)))
const areaPts = computed(() => toArea(sparkPoints(trendAll.value)))
const succLinePts = computed(() => toLine(sparkPoints(trendSucc.value)))
const succAreaPts = computed(() => toArea(sparkPoints(trendSucc.value)))
const failLinePts = computed(() => toLine(sparkPoints(trendFail.value)))
const failAreaPts = computed(() => toArea(sparkPoints(trendFail.value)))

// delta 涨幅:最近 15 分钟 vs 前 15 分钟(真实分钟桶,不再是比较造假数据的两半)
const recentHalf = computed(() => trendAll.value.slice(15).reduce((a, b) => a + b, 0))
const prevHalf = computed(() => trendAll.value.slice(0, 15).reduce((a, b) => a + b, 0))
const deltaPct = computed(() => prevHalf.value ? ((recentHalf.value - prevHalf.value) / prevHalf.value * 100) : 0)

function fmt(t: any) {
  if (!t) return '—'
  return String(t).replace('T', ' ').substring(0, 19)
}

// 导出 CSV（按当前筛选条件的**全量**结果——不带分页参数走后端兼容模式；带 BOM 兼容 Excel）
async function exportCsv() {
  try {
    const params: any = {}
    if (fType.value) params.type = fType.value
    if (fStatus.value) params.status = fStatus.value
    if (kw.value.trim()) params.keyword = kw.value.trim()
    const r: any = await http.get('/logs', { params })
    const rows: any[] = Array.isArray(r?.data) ? r.data : (r?.data?.items || [])
    if (!rows.length) { ElMessage.warning(t('没有可导出的日志')); return }
    const headers = [t('时间'), t('用户'), t('类型'), t('操作'), t('状态'), t('IP')]
    const escape = (v: any) => {
      const s = String(v ?? '')
      return /[",\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s
    }
    const lines = [
      headers.map(escape).join(','),
      ...rows.map(l => [fmt(l.createdAt), l.userName, l.type, l.operation, l.status, l.ip || ''].map(escape).join(','))
    ]
    const blob = new Blob(['﻿' + lines.join('\r\n')], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${t('系统日志')}_${new Date().toISOString().slice(0, 10)}.csv`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success(t('已导出 {n} 条日志', { n: rows.length }))
  } catch (e: any) {
    ElMessage.error(e?.message || t('导出失败'))
  }
}

/** 日志明细：服务端分页 + 筛选（type/status/keyword 传后端） */
async function loadLogs() {
  try {
    const params: any = { page: page.value, size: pageSize.value }
    if (fType.value) params.type = fType.value
    if (fStatus.value) params.status = fStatus.value
    if (kw.value.trim()) params.keyword = kw.value.trim()
    const r: any = await http.get('/logs', { params })
    const d = r?.data
    if (Array.isArray(d)) {
      // 旧后端（未重启）：不支持 type/status/keyword 参数 → 本地过滤 + 切片分页兼容
      const k = kw.value.trim().toLowerCase()
      const list = d.filter(l => {
        if (fType.value && l.type !== fType.value) return false
        if (fStatus.value && l.status !== fStatus.value) return false
        if (k) {
          const hay = `${l.userName || ''} ${l.operation || ''} ${l.ip || ''}`.toLowerCase()
          if (!hay.includes(k)) return false
        }
        return true
      })
      const start = (page.value - 1) * pageSize.value
      paged.value = list.slice(start, start + pageSize.value)
      total.value = list.length
    } else {
      paged.value = d?.items || []
      total.value = d?.total || 0
    }
  } catch {}
}

async function load() {
  await Promise.all([loadLogs(), loadStats(), loadLiveTrend()])
}

async function loadStats() {
  try {
    const [s, hm]: any = await Promise.all([
      http.get('/logs/stats').catch(() => ({ data: {} })),
      http.get('/logs/heatmap').catch(() => ({ data: {} }))
    ])
    const d = s?.data || {}
    byType.value = d.byType || {}
    byUser.value = d.byUser || []
    byStatus.value = d.byStatus || {}
    heat.value = hm?.data?.rows?.length ? hm.data : { rows: [], max: 0, total: 0, peakDay: null, peakHour: null, peakCount: 0 }
  } catch {}
}
/* ===== 详情抽屉 ===== */
const lgDetail = ref<any>(null)
const detailFields = computed(() => !lgDetail.value ? [] : [
  { label: t('时间'), value: fmt(lgDetail.value.createdAt) },
  { label: t('用户'), value: lgDetail.value.userName },
  { label: t('操作类型'), value: t(lgDetail.value.type) },
  { label: t('操作'), value: lgDetail.value.operation },
  { label: t('状态'), value: t(lgDetail.value.status) },
  { label: t('IP 地址'), value: lgDetail.value.ip },
])

/* ===== 空结果 Dice 纠错（bigram + 单字） ===== */
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
  for (const l of paged.value) { if (l.userName) names.add(l.userName); if (l.operation) names.add(l.operation) }
  for (const e of typeEntries.value) names.add(e[0])
  const scored = new Map<string, number>()
  for (const n of names) {
    const score = Math.max(diceCoefficient(k, n), charDice(k, n))
    if (score >= 0.34) scored.set(n, score)
  }
  return [...scored.entries()].sort((a, b) => b[1] - a[1]).slice(0, 5).map(([n]) => n)
})

/* ===== 命令面板：日志池懒加载 + 类型快捷筛选 ===== */
const logPool = ref<any[]>([])
const logPoolLoaded = ref(false)
const cmdLoading = ref(false)
async function ensureLogPool() {
  if (logPoolLoaded.value || cmdLoading.value) return
  cmdLoading.value = true
  try {
    const r: any = await http.get('/logs')
    const arr = Array.isArray(r?.data) ? r.data : (r?.data?.items || [])
    logPool.value = arr.slice(0, 300).map((l: any) => ({ ...l, _py: initialsOf(`${l.userName || ''} ${l.operation || ''} ${l.type || ''} ${l.ip || ''}`) }))
    logPoolLoaded.value = true
  } catch {} finally { cmdLoading.value = false }
}
const cmdOpen = ref(false)
const cmdTerm = ref('')
const cmdCursor = ref(0)
const cmdInput = ref<HTMLInputElement | null>(null)
function matchText(haystack: string, py: string, q: string): boolean {
  const ql = q.toLowerCase().replace(/\s+/g, '')
  if (!ql) return true
  if ((haystack || '').toLowerCase().replace(/\s+/g, '').includes(ql)) return true
  return !!py && py.includes(ql)
}
function openCommand() {
  cmdTerm.value = kw.value; cmdCursor.value = 0; cmdOpen.value = true
  ensureLogPool()
  nextTick(() => cmdInput.value?.focus())
}
function closeCommand(clear: boolean) {
  cmdOpen.value = false
  if (clear) { cmdTerm.value = ''; kw.value = '' }
  else { cmdTerm.value = kw.value }
}
watch(cmdTerm, v => { if (cmdOpen.value) kw.value = v })
const cmdLogs = computed(() => {
  const q = cmdTerm.value.trim()
  const arr = !q ? logPool.value.slice(0, 8)
    : logPool.value.filter(l => matchText(`${l.userName || ''} ${l.operation || ''} ${l.type || ''} ${l.ip || ''}`, l._py || '', q)).slice(0, 8)
  return arr
})
const cmdFilters = computed(() => {
  const q = cmdTerm.value.trim()
  return typeEntries.value.map(([ty, c]) => ({ value: ty, label: ty, count: c }))
    .filter(o => !q || matchText(o.label, initialsOf(o.label), q)).slice(0, 6)
})
type CmdItem = { kind: 'log' | 'filter'; raw: any }
const cmdItems = computed<CmdItem[]>(() => [
  ...cmdLogs.value.map(l => ({ kind: 'log' as const, raw: l })),
  ...cmdFilters.value.map(o => ({ kind: 'filter' as const, raw: o })),
])
watch(cmdItems, () => { if (cmdCursor.value >= cmdItems.value.length) cmdCursor.value = 0 })
function selectCmd(item: CmdItem | undefined) {
  if (!item) return
  if (item.kind === 'log') { lgDetail.value = item.raw; cmdOpen.value = false; cmdTerm.value = ''; kw.value = '' }
  else { setType(item.raw.value); cmdOpen.value = false; cmdTerm.value = ''; kw.value = ''; ElMessage.success(t('已按类型筛选：{n}', { n: item.raw.value })) }
}
function onGlobalKey(e: KeyboardEvent) {
  const meta = e.metaKey || e.ctrlKey
  if (meta && e.key.toLowerCase() === 'k') {
    e.preventDefault()
    cmdOpen.value ? closeCommand(false) : openCommand()
    return
  }
  if (!cmdOpen.value) return
  if (e.key === 'Escape') { e.preventDefault(); closeCommand(false) }
  else if (e.key === 'ArrowDown') { e.preventDefault(); if (cmdItems.value.length) cmdCursor.value = (cmdCursor.value + 1) % cmdItems.value.length }
  else if (e.key === 'ArrowUp') { e.preventDefault(); if (cmdItems.value.length) cmdCursor.value = (cmdCursor.value - 1 + cmdItems.value.length) % cmdItems.value.length }
  else if (e.key === 'Enter') { e.preventDefault(); selectCmd(cmdItems.value[cmdCursor.value]) }
}

onMounted(async () => {
  await load()
  // 数据就绪后再触发环形图绘制动画（不用 rAF：后台窗口会被挂起）
  await nextTick()
  window.setTimeout(() => { revealed.value = true }, 60)
  window.setTimeout(() => { revealDone.value = true }, 1500)
  window.addEventListener('keydown', onGlobalKey)
})
onBeforeUnmount(() => { window.removeEventListener('keydown', onGlobalKey) })
</script>

<style scoped>
.lg-count { font-size: 13px; color: var(--text-light); }
.lg-chip-cnt { font-style: normal; opacity: .65; font-size: 12px; margin-left: 2px; }

/* ============================================================
   系统日志页增强：呼吸光晕 + 更通透的玻璃 + 交互环形图
   ============================================================ */
@keyframes logsBlobPulse { 0%, 100% { opacity: .24; } 50% { opacity: .44; } }
@keyframes logsBlobPulseDark { 0%, 100% { opacity: .12; } 50% { opacity: .26; } }
@keyframes logsOrbFloat { from { transform: translate3d(0, 0, 0) scale(1); } to { transform: translate3d(34px, -28px, 0) scale(1.14); } }
@keyframes logsBreathe { 0%, 100% { transform: scale(1); opacity: .42; } 50% { transform: scale(1.1); opacity: .7; } }
@keyframes logsAura { 0%, 100% { transform: translate3d(0, 0, 0) scale(1); opacity: .3; } 50% { transform: translate3d(-10px, 10px, 0) scale(1.18); opacity: .55; } }
@keyframes logsOrbit { to { transform: rotate(360deg); } }
@keyframes logsDotPulse { 0%, 100% { transform: scale(1); } 50% { transform: scale(1.4); } }
@keyframes logsBarGlow { 0%, 100% { box-shadow: 0 0 14px rgba(139, 92, 246, .32); } 50% { box-shadow: 0 0 24px rgba(139, 92, 246, .58); } }
@keyframes coreIn { from { opacity: 0; transform: translateY(7px) scale(.94); } to { opacity: 1; transform: none; } }
@keyframes coreOut { from { opacity: 1; transform: none; } to { opacity: 0; transform: translateY(-7px) scale(.94); } }

/* —— 背景光斑：静态（动画导致大面积模糊层滚动掉帧）—— */

/* 额外粉色氛围光球（呼吸 + 缓慢漂浮） */
.stats-page.logs-page > .logs-orb {
  position: absolute;
  z-index: 0;
  width: 340px;
  height: 340px;
  left: 40%;
  top: 24%;
  border-radius: 50%;
  pointer-events: none;
  background: radial-gradient(circle at 36% 34%, rgba(244, 114, 182, .5), rgba(244, 114, 182, 0) 68%);
  filter: blur(72px);
  opacity: .14;
}
html[data-theme="dark"] .stats-page.logs-page > .logs-orb { opacity: .12; }

/* —— 玻璃升级：更通透、更强折射 —— */
.stats-page.liquid.logs-page .stat-card,
.stats-page.liquid.logs-page .section {
  background: linear-gradient(148deg, rgba(255, 255, 255, .64), rgba(255, 255, 255, .30));
  backdrop-filter: blur(12px) saturate(1.4);
  border-color: rgba(255, 255, 255, .72);
}
.stats-page.liquid.logs-page .card {
  background: linear-gradient(148deg, rgba(255, 255, 255, .64), rgba(255, 255, 255, .30));
  backdrop-filter: blur(12px) saturate(1.4);
  border: 1px solid rgba(255, 255, 255, .72);
  border-radius: 22px 26px 20px 28px;
  box-shadow:
    0 10px 30px rgba(31, 38, 135, .10),
    inset 0 1px 1px rgba(255, 255, 255, .85),
    inset 0 -12px 24px rgba(99, 102, 241, .08);
  animation: zxGlassIn .55s cubic-bezier(.22, .9, .36, 1) both;
}
html[data-theme="dark"] .stats-page.liquid.logs-page .stat-card,
html[data-theme="dark"] .stats-page.liquid.logs-page .section,
html[data-theme="dark"] .stats-page.liquid.logs-page .card {
  background: linear-gradient(148deg, rgba(30, 41, 59, .66), rgba(30, 41, 59, .40));
  border-color: rgba(255, 255, 255, .10);
}

/* —— 统计卡呼吸光晕（按卡片配色） —— */
.stats-page.liquid.logs-page .stat-card > .card-aura {
  position: absolute;
  z-index: 0;
  width: 180px;
  height: 180px;
  right: -48px;
  top: -66px;
  border-radius: 50%;
  pointer-events: none;
  background: radial-gradient(circle, rgba(100,116,139,.14), transparent 68%);
  filter: blur(38px);
  opacity: .32;
  animation: logsAura 6.4s ease-in-out infinite;
}
/* 统计卡改为上中下堆叠：顶栏 / 数值 / 标签，sparkline 回到右下角绝对定位 */
.stats-page.liquid.logs-page .stat-card.stat-pro {
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  gap: 2px;
}
.stats-page.liquid.logs-page .stat-card.stat-pro .stat-pro-top {
  width: 100%;
  margin-bottom: 8px;
}
.stats-page.liquid.logs-page .delta-cap {
  margin-left: auto;
  margin-right: 8px;
  font-size: 11px;
  color: var(--text-light, #94a3b8);
}
.stats-page.liquid.logs-page .stat-card > .stat-spark {
  position: absolute;
  right: 10px;
  bottom: 8px;
  width: 46%;
  height: 40px;
  z-index: 1;
}
.stats-page.liquid.logs-page .stat-card:nth-child(1) .card-aura { --aura: #667eea; }
.stats-page.liquid.logs-page .stat-card:nth-child(2) .card-aura { --aura: #10b981; animation-delay: -2.1s; }
.stats-page.liquid.logs-page .stat-card:nth-child(3) .card-aura { --aura: #ef4444; animation-delay: -4.2s; }
html[data-theme="dark"] .stats-page.liquid.logs-page .stat-card > .card-aura { opacity: .22; }

/* —— 两列布局（窄屏自动堆叠） —— */
/* 仅在日志页解除外壳的 min-width 限制，避免宽表格把整页撑宽 */
:global(.main:has(.logs-page)) { min-width: 0; }
:global(.main:has(.logs-page) > .content) { min-width: 0; }
.logs-page { overflow: clip; }
.logs-page .section .data-table { overflow-x: auto; }
.logs-page .logs-two-col {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.4fr);
  gap: 20px;
  margin-top: 20px;
}
@media (max-width: 1400px) {
  /* 窄于 1400 时左右卡片会挤到环形图内部换行，直接改为上下堆叠 */
  .logs-page .logs-two-col { grid-template-columns: minmax(0, 1fr); }
}

/* —— 环形图卡片 —— */
.logs-page .donut-card { display: flex; flex-direction: column; }
.logs-page .donut-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.logs-page .donut-hint {
  font-size: 12px;
  letter-spacing: .3px;
  color: var(--text-light);
  padding: 3px 9px;
  border-radius: 999px;
  border: 1px dashed rgba(148, 163, 184, .45);
  opacity: .85;
  white-space: nowrap;
}
.logs-page .donut-wrap-v2 { flex: 1; padding: 12px 2px 2px; gap: 18px; align-items: center; justify-content: center; }
.logs-page .donut-stage {
  position: relative;
  width: 206px;
  height: 206px;
  flex: 0 0 206px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.logs-page .donut-halo {
  position: absolute;
  inset: 12%;
  border-radius: 50%;
  pointer-events: none;
  background: radial-gradient(circle, color-mix(in srgb, var(--halo, #667eea) 42%, transparent), transparent 70%);
  filter: blur(26px);
  animation: logsBreathe 5.2s ease-in-out infinite;
}
.logs-page .donut-svg {
  width: 100%;
  height: 100%;
  overflow: visible;
  filter: drop-shadow(0 14px 26px rgba(31, 38, 135, .16));
}
.logs-page .donut-track { fill: none; stroke: var(--primary-bg, rgba(148, 163, 184, .16)); stroke-width: 18; opacity: .6; }
.logs-page .donut-orbit {
  fill: none;
  stroke: var(--primary, #667eea);
  stroke-width: 1.4;
  stroke-dasharray: .5 7.5;
  stroke-linecap: round;
  opacity: .3;
  transform-origin: 100px 100px;
  animation: logsOrbit 34s linear infinite;
}
.logs-page .donut-seg {
  fill: none;
  stroke-width: 18;
  stroke-linecap: butt;
  cursor: pointer;
  transition:
    stroke-dasharray .95s cubic-bezier(.22, .9, .36, 1),
    stroke-width .3s cubic-bezier(0.22, 1, 0.36, 1),
    opacity .3s ease,
    filter .35s ease;
  will-change: stroke-width, opacity;
}
.logs-page .donut-seg.dim { opacity: .26; }
.logs-page .donut-seg.active { stroke-width: 26; filter: drop-shadow(0 0 9px currentColor); }

.logs-page .donut-stage > .donut-core {
  position: absolute;
  inset: 27%;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  pointer-events: none;
  background: linear-gradient(160deg, rgba(255, 255, 255, .93), rgba(255, 255, 255, .66));
  backdrop-filter: blur(10px) saturate(1.4);
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, .95), 0 8px 22px rgba(31, 38, 135, .10);
}
html[data-theme="dark"] .logs-page .donut-stage > .donut-core {
  background: linear-gradient(160deg, rgba(30, 41, 59, .94), rgba(30, 41, 59, .74));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, .08), 0 8px 22px rgba(0, 0, 0, .35);
}
.logs-page .core-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  line-height: 1.15;
  padding: 4px;
  max-width: 100%;
}
.logs-page .core-inner b {
  font-size: clamp(19px, 1.2vw + 8px, 24px);
  color: var(--text);
  font-variant-numeric: tabular-nums;
  letter-spacing: -.5px;
}
.logs-page .core-inner b i { font-style: normal; font-size: .6em; margin-left: 1px; opacity: .75; }
.logs-page .core-inner span {
  font-size: 12px;
  color: var(--text-light);
  margin-top: 2px;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.logs-page .core-inner em { font-style: normal; font-size: 12px; color: var(--text-light); opacity: .7; margin-top: 1px; }
.core-swap-enter-active { animation: coreIn .26s cubic-bezier(.22, .9, .36, 1) both; }
.core-swap-leave-active { animation: coreOut .18s ease both; }

/* —— 图例：染色底 + 联动高亮 —— */
.logs-page .donut-legend { min-width: 168px; max-width: 560px; gap: 4px; }
.logs-page .donut-legend .pie-item {
  position: relative;
  overflow: hidden;
  padding: 7px 11px;
  border-radius: 11px;
  border: 1px solid transparent;
  transition: background .2s, transform .2s, border-color .2s;
}
.logs-page .donut-legend .pie-item::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: var(--w, 0%);
  border-radius: inherit;
  pointer-events: none;
  background: linear-gradient(90deg, color-mix(in srgb, var(--c) 18%, transparent), transparent 92%);
  opacity: .85;
}
.logs-page .donut-legend .pie-item > * { position: relative; z-index: 1; }
.logs-page .donut-legend .pie-item:hover,
.logs-page .donut-legend .pie-item.on {
  background: rgba(148, 163, 184, .07);
  border-color: color-mix(in srgb, var(--c) 32%, transparent);
  transform: translateX(3px);
}
.logs-page .donut-legend .pie-dot { animation: logsDotPulse 3.4s ease-in-out infinite; }

/* —— 柱状图：呼吸辉光 + 悬停抬升 —— */
.logs-page .bar-card { display: flex; flex-direction: column; }
.logs-page .bar-card .bar-chart { flex: 1; }
.logs-page .bar-item { transition: transform .28s cubic-bezier(0.22, 1, 0.36, 1); }
.logs-page .bar-item:hover { transform: translateY(-4px); }
.logs-page .bar-item:hover .bar-label { color: var(--text); }
.logs-page .bar-fill {
  animation: growBar .9s cubic-bezier(.4, 0, .2, 1) backwards;
}

/* —— 表格：斑马纹 + 徽章微光 —— */
.logs-page .section .data-table tbody tr:nth-child(even) td { background: rgba(148, 163, 184, .045); }
html[data-theme="dark"] .logs-page .section .data-table tbody tr:nth-child(even) td { background: rgba(255, 255, 255, .018); }
.logs-page .section .data-table tbody tr:hover td { background: rgba(109, 40, 217, .07); }
html[data-theme="dark"] .logs-page .section .data-table tbody tr:hover td { background: rgba(139, 92, 246, .13); }
.logs-page .lg-chips .cat-tab.active { box-shadow: 0 6px 16px rgba(109, 40, 217, .26); }

/* —— 操作时间热力 —— */
.hm-meta { font-size: 12.5px; color: var(--text-light); }
.hm-meta b { color: var(--primary, #2bb187); }
.hm-wrap { display: flex; flex-direction: column; gap: 4px; margin-top: 6px; overflow-x: auto; }
.hm-row { display: grid; grid-template-columns: 44px minmax(0, 1fr); gap: 10px; align-items: center; }
.hm-day-label { font-size: 12px; color: var(--text-light); text-align: right; }
.hm-cells { display: grid; grid-template-columns: repeat(24, minmax(0, 1fr)); gap: 3px; }
.hm-cell {
  display: block; aspect-ratio: 1 / .82; min-height: 14px; border-radius: 4px;
  transition: transform .18s, box-shadow .18s;
}
.hm-cell:hover { transform: scale(1.25); box-shadow: 0 0 0 1.5px rgba(99, 102, 241, .8); z-index: 1; }
.hm-cell.peak { box-shadow: 0 0 0 1.5px #f59e0b; }
.hm-axis .hm-cells { gap: 3px; }
.hm-hour { font-size: 11px; color: var(--text-light); opacity: .85; text-align: center; }
.hm-hour.major { opacity: .95; font-weight: 700; }
.hm-scale { display: flex; align-items: center; gap: 5px; margin-top: 8px; font-size: 12px; color: var(--text-light); }
.hm-scale i { width: 18px; height: 9px; border-radius: 3px; display: inline-block; }
.hm-scale em { font-style: normal; margin-left: auto; opacity: .7; }
@media (max-width: 760px) {
  .hm-scale em { display: none; }
  .hm-row { grid-template-columns: 34px minmax(0, 1fr); }
}

/* —— 减少动态偏好 —— */
@media (prefers-reduced-motion: reduce) {
  .logs-page.stats-page.liquid::before,
  .logs-page.stats-page.liquid::after,
  .logs-page .logs-orb,
  .logs-page .stat-card > .card-aura,
  .logs-page .donut-halo,
  .logs-page .donut-orbit,
  .logs-page .donut-legend .pie-dot,
  .logs-page .bar-fill { animation: none !important; }
  .logs-page .donut-seg { transition: none; }
}
html[data-theme="dark"] .cat-tab { color: #c5cdd9; }

/* ================= 检索体验升级：顶栏 / 命令面板 / 抽屉 / 空结果 ================= */
.logs-page { --g: #10b981; --g-dark: #059669; --g-soft: rgba(16,185,129,.10); }
.ic { display: block; }
.lg-topbar { position: sticky; top: 0; z-index: 40; display: flex; align-items: center; gap: 16px; flex-wrap: wrap; padding: 12px 16px; margin-bottom: 18px; background: color-mix(in srgb, var(--card, #fff) 86%, transparent); backdrop-filter: saturate(1.4) blur(14px); -webkit-backdrop-filter: saturate(1.4) blur(14px); border: 0.5px solid var(--border, #e5e7eb); border-radius: 16px; box-shadow: 0 1px 2px rgba(16,24,40,.03), 0 8px 24px rgba(16,24,40,.04); }
.lg-brand { display: flex; align-items: center; gap: 12px; flex: 0 0 auto; }
.lg-brand-icon { width: 40px; height: 40px; border-radius: 12px; display: grid; place-items: center; color: #fff; background: linear-gradient(135deg, #34d399, var(--g-dark)); box-shadow: 0 4px 12px rgba(16,185,129,.32); }
.lg-brand-icon .ic { width: 21px; height: 21px; }
.lg-title { font-size: 17px; font-weight: 700; color: var(--text, #0f172a); line-height: 1.2; }
.lg-subtitle { font-size: 12px; color: var(--text-lighter, #94a3b8); margin-top: 2px; }
.lg-search-trigger { flex: 1 1 320px; max-width: 520px; min-width: 180px; height: 38px; display: flex; align-items: center; gap: 9px; padding: 0 10px 0 14px; border: 1px solid var(--border, #e5e7eb); border-radius: 999px; background: var(--card, #fff); color: var(--text-lighter, #94a3b8); cursor: pointer; transition: all .18s; font-family: inherit; }
.lg-search-trigger:hover { border-color: var(--g); box-shadow: 0 0 0 3px var(--g-soft); }
.lg-search-trigger .ic { width: 17px; height: 17px; flex: 0 0 auto; }
.lg-search-ph { flex: 1; text-align: left; font-size: 13px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.lg-kbd { font-family: inherit; font-size: 11px; line-height: 1; white-space: nowrap; padding: 4px 7px; border-radius: 6px; border: 1px solid var(--border, #e5e7eb); background: color-mix(in srgb, var(--card, #fff) 92%, #000 8%); color: var(--text-light, #64748b); flex: 0 0 auto; }
.lg-top-actions { display: flex; align-items: center; gap: 8px; flex: 0 0 auto; margin-left: auto; }
.lg-btn { display: inline-flex; align-items: center; justify-content: center; gap: 6px; height: 36px; padding: 0 14px; border-radius: 10px; font-family: inherit; font-size: 13px; font-weight: 600; cursor: pointer; transition: all .16s; white-space: nowrap; border: 1px solid transparent; }
.lg-btn .ic { width: 15px; height: 15px; }
.lg-btn-outline { background: var(--card, #fff); color: var(--text, #334155); border-color: var(--border, #e5e7eb); }
.lg-btn-outline:hover { border-color: var(--g); color: var(--g); }
.lg-reset { border: none; background: none; color: var(--text-lighter); font-family: inherit; font-size: 12.5px; cursor: pointer; padding: 6px 8px; margin-left: auto; }
.lg-reset:hover { color: #ef4444; }
.lg-row { cursor: pointer; }
.lg-row:hover { background: var(--g-soft) !important; }
.lg-empty { display: flex; flex-direction: column; align-items: center; text-align: center; padding: 40px 20px; }
.lg-empty-title { font-size: 14px; font-weight: 600; color: var(--text); }
.lg-suggest { display: flex; align-items: center; gap: 7px; flex-wrap: wrap; justify-content: center; margin-top: 16px; }
.lg-suggest-label { font-size: 12px; color: var(--text-light); }
.lg-suggest-chip { border: 1px solid var(--border); background: var(--card); color: var(--g-dark); font-family: inherit; font-size: 12.5px; padding: 6px 12px; border-radius: 999px; cursor: pointer; transition: all .15s; }
.lg-suggest-chip:hover { border-color: var(--g); background: var(--g-soft); }
.lg-empty-reset { margin-top: 16px; border: none; background: none; color: var(--g); font-family: inherit; font-size: 12.5px; cursor: pointer; text-decoration: underline; text-underline-offset: 3px; }
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
.cmd-item-ico.user { background: rgba(16,185,129,.13); }
.cmd-item-ico.org { background: rgba(59,130,246,.13); color: #3b82f6; }
.cmd-avatar { width: 24px; height: 24px; border-radius: 50%; display: grid; place-items: center; color: #fff; font-size: 12px; font-weight: 600; }
.cmd-item-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.cmd-item-main b { font-size: 13px; font-weight: 600; color: var(--text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cmd-item-main small { font-size: 11px; color: var(--text-lighter); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cmd-enter { color: var(--g); font-size: 14px; flex: 0 0 auto; }
.cmd-empty { padding: 36px 16px; text-align: center; color: var(--text-lighter); font-size: 13px; }
.cmd-footer { display: flex; align-items: center; gap: 16px; padding: 10px 16px; border-top: 0.5px solid var(--border); background: color-mix(in srgb, var(--card) 94%, #000 6%); font-size: 11px; color: var(--text-lighter); }
.cmd-footer span { display: inline-flex; align-items: center; gap: 4px; }
.cmd-footer .lg-kbd { padding: 2px 6px; }
.cmd-footer-tip { margin-left: auto; color: var(--g); }
@keyframes cmdFade { from { opacity: 0 } to { opacity: 1 } }
@keyframes cmdUp { from { opacity: 0; transform: translateY(-10px) scale(.985) } to { opacity: 1; transform: none } }
.lg-mask { position: fixed !important; inset: 0; background: rgba(0,0,0,.42); z-index: 3500 !important; display: flex; justify-content: flex-end; animation: cmdFade .16s ease; }
.lg-drawer { width: 400px; max-width: 92vw; height: 100%; background: var(--card); border-left: 0.5px solid var(--border); display: flex; flex-direction: column; animation: drawerIn .22s cubic-bezier(.16,1,.3,1); }
@keyframes drawerIn { from { transform: translateX(24px); opacity: .6 } to { transform: none; opacity: 1 } }
.lg-drawer-h { display: flex; align-items: center; padding: 18px 20px; border-bottom: 0.5px solid var(--border); }
.lg-drawer-h b { font-size: 16px; color: var(--text); }
.lg-drawer-h span { display: block; font-size: 12px; color: var(--text-lighter); margin-top: 3px; }
.lg-close { margin-left: auto; border: none; background: none; font-size: 24px; color: var(--text-light); cursor: pointer; line-height: 1; }
.lg-drawer-body { flex: 1; overflow-y: auto; padding: 12px 20px; }
.lg-field { display: flex; justify-content: space-between; gap: 12px; padding: 11px 0; border-bottom: 0.5px solid var(--border); font-size: 12.5px; }
.lg-field span { color: var(--text-lighter); flex: 0 0 auto; }
.lg-field b { color: var(--text); font-weight: 500; text-align: right; word-break: break-all; }
@media (max-width: 1100px) { .lg-search-trigger { order: 3; flex-basis: 100%; max-width: none; } .lg-top-actions { order: 2; } }
@media (max-width: 560px) { .lg-topbar { padding: 12px; } .lg-kbd { display: none; } }
</style>
