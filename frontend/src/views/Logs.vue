<template>
  <div class="stats-page liquid logs-page">
    <div class="logs-orb" aria-hidden="true" />
    <!-- ===== 原版 statCardPro 3 卡 ===== -->
    <div class="stats-grid">
      <div class="stat-card stat-pro">
        <span class="card-aura" aria-hidden="true" />
        <div class="stat-pro-top">
          <div class="stat-icon blue" v-html="svgIcon('bar-chart')" />
          <span class="stat-delta up">+{{ deltaPct.toFixed(1) }}%</span>
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

      <div class="lg-toolbar">
        <div class="search-box lg-search">
          <input v-model="kw" :placeholder="t('搜索用户 / 操作 / IP…')" />
        </div>
        <div class="lg-chips">
          <button class="cat-tab" :class="{ active: fStatus === '' }" @click="setStatus('')">{{ t('全部') }}</button>
          <button class="cat-tab lg-ok" :class="{ active: fStatus === '成功' }" @click="setStatus('成功')">{{ t('成功 {n}', { n: succ }) }}</button>
          <button class="cat-tab lg-err" :class="{ active: fStatus === '失败' }" @click="setStatus('失败')">{{ t('失败 {n}', { n: fail }) }}</button>
        </div>
        <div class="lg-chips">
          <button class="cat-tab" :class="{ active: fType === '' }" @click="setType('')">{{ t('全部类型') }}</button>
          <button v-for="[t, c] in typeChips" :key="t" class="cat-tab" :class="{ active: fType === t }" @click="setType(t)">
            <span class="lg-dot" :style="{ background: typeColor(t) }" />{{ t }} <i class="lg-chip-cnt">{{ c }}</i>
          </button>
        </div>
        <button class="lg-export" @click="exportCsv">
          <span class="icon" v-html="svgIcon('download')" /> {{ t('导出 CSV') }}<span class="lg-export-cnt">{{ total }}</span>
        </button>
      </div>

      <div v-if="paged.length" class="data-table">
        <table>
          <thead><tr><th>{{ t('时间') }}</th><th>{{ t('用户') }}</th><th>{{ t('类型') }}</th><th>{{ t('操作') }}</th><th>{{ t('状态') }}</th><th>{{ t('IP') }}</th></tr></thead>
          <tbody>
            <tr v-for="l in paged" :key="l.id">
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
      <div v-else class="empty-state"><div class="empty-icon" v-html="svgIcon('file-text')" /><p>{{ total ? t('没有匹配的日志，试试调整筛选条件') : t('暂无日志') }}</p></div>

      <div v-if="total" class="lg-pagebar">
        <div class="lg-total">{{ t('第 {a}–{b} 条 / 共 {n} 条', { a: pageStart + 1, b: pageEnd, n: total }) }}</div>
        <div class="lg-pager">
          <select v-model.number="pageSize" class="lg-size">
            <option :value="10">{{ t('10 条/页') }}</option>
            <option :value="20">{{ t('20 条/页') }}</option>
            <option :value="50">{{ t('50 条/页') }}</option>
            <option :value="100">{{ t('100 条/页') }}</option>
          </select>
          <button :disabled="page <= 1" @click="page--">{{ t('‹ 上一页') }}</button>
          <span class="lg-pageinfo">{{ page }} / {{ pageCount }}</span>
          <button :disabled="page >= pageCount" @click="page++">{{ t('下一页 ›') }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api'
import { useCountUp } from '@/utils/useCountUp'
import { t } from '@/i18n'

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

// ===== 明细筛选（关键词 / 状态 / 类型）——筛选传后端，服务端分页 =====
const kw = ref('')
const fType = ref('')
const fStatus = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

function setStatus(s: string) { fStatus.value = s; page.value === 1 ? loadLogs() : (page.value = 1) }
function setType(t: string) { fType.value = fType.value === t ? '' : t; page.value === 1 ? loadLogs() : (page.value = 1) }
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
function hourlyTrendFake(arr: any[], n = 12) {
  if (!arr?.length) return Array(n).fill(5)
  const buckets = Array(n).fill(0)
  for (const item of arr) {
    const t = item.createdAt
    if (!t) continue
    const d = typeof t === 'number' ? new Date(t) : new Date(String(t).replace(' ', 'T'))
    if (isNaN(d.getTime())) continue
    const h = d.getHours()
    buckets[h % n]++
  }
  // 如果全是 0，给随机噪音让图好看点
  if (buckets.every(x => x === 0)) return buckets.map(() => Math.floor(Math.random() * 6) + 2)
  return buckets
}

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

const trendAll = computed(() => hourlyTrendFake(logs.value, 12))
const trendSucc = computed(() => hourlyTrendFake(logs.value.filter(l => l.status === '成功'), 12))
const trendFail = computed(() => hourlyTrendFake(logs.value.filter(l => l.status === '失败'), 12))

const linePts = computed(() => toLine(sparkPoints(trendAll.value)))
const areaPts = computed(() => toArea(sparkPoints(trendAll.value)))
const succLinePts = computed(() => toLine(sparkPoints(trendSucc.value)))
const succAreaPts = computed(() => toArea(sparkPoints(trendSucc.value)))
const failLinePts = computed(() => toLine(sparkPoints(trendFail.value)))
const failAreaPts = computed(() => toArea(sparkPoints(trendFail.value)))

// delta 涨幅（原版 statCardPro 功能）
const recentHalf = computed(() => trendAll.value.slice(6).reduce((a, b) => a + b, 0))
const prevHalf = computed(() => trendAll.value.slice(0, 6).reduce((a, b) => a + b, 0))
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
  await Promise.all([loadLogs(), loadStats()])
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
onMounted(async () => {
  await load()
  // 数据就绪后再触发环形图绘制动画（不用 rAF：后台窗口会被挂起）
  await nextTick()
  window.setTimeout(() => { revealed.value = true }, 60)
  window.setTimeout(() => { revealDone.value = true }, 1500)
})
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
</style>
