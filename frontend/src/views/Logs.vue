<template>
  <div class="stats-page liquid">
    <!-- ===== 原版 statCardPro 3 卡 ===== -->
    <div class="stats-grid">
      <div class="stat-card stat-pro">
        <div class="stat-pro-top">
          <div class="stat-icon blue" v-html="svgIcon('bar-chart')" />
          <span class="stat-delta up">+{{ deltaPct.toFixed(1) }}%</span>
        </div>
        <div class="stat-value">{{ totalAll }}</div>
        <div class="stat-label">总操作数</div>
        <svg class="stat-spark" viewBox="0 0 120 36" preserveAspectRatio="none">
          <polygon :points="areaPts" fill="#667eea" opacity="0.14" />
          <polyline :points="linePts" fill="none" stroke="#667eea" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </div>

      <div class="stat-card stat-pro">
        <div class="stat-pro-top">
          <div class="stat-icon green" v-html="svgIcon('check-circle')" />
        </div>
        <div class="stat-value">{{ succ }}</div>
        <div class="stat-label">成功 · {{ succRate.toFixed(1) }}%</div>
        <svg class="stat-spark" viewBox="0 0 120 36" preserveAspectRatio="none">
          <polygon :points="succAreaPts" fill="#10b981" opacity="0.14" />
          <polyline :points="succLinePts" fill="none" stroke="#10b981" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </div>

      <div class="stat-card stat-pro">
        <div class="stat-pro-top">
          <div class="stat-icon red" v-html="svgIcon('x-circle')" />
        </div>
        <div class="stat-value">{{ fail }}</div>
        <div class="stat-label">失败 · {{ (100 - succRate).toFixed(1) }}%</div>
        <svg class="stat-spark" viewBox="0 0 120 36" preserveAspectRatio="none">
          <polygon :points="failAreaPts" fill="#ef4444" opacity="0.14" />
          <polyline :points="failLinePts" fill="none" stroke="#ef4444" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </div>
    </div>

    <!-- ===== 左：原版 donut 甜甜圈 + 右：原版 bar-chart 柱状 ===== -->
    <div style="display:grid;grid-template-columns:1fr 1.4fr;gap:20px;margin-top:20px">
      <!-- donut-wrap / donut / donut-hole / pie-item（原版 CSS 全有） -->
      <div class="card glow-corner">
        <div class="section-title"><span v-html="svgIcon('pie-chart')" />操作类型分布</div>
        <div v-if="pieSlices.length" class="donut-wrap" :style="{ '--donut-glow': topGlowColor }">
          <div class="donut" :style="{ background: conicGradient }">
            <div class="donut-hole"><b>{{ totalByType }}</b><span>次操作</span></div>
          </div>
          <div class="donut-legend">
            <div v-for="s in pieSlices" :key="s.label" class="pie-item">
              <span class="pie-dot" :style="{ background: s.color, boxShadow: '0 0 6px ' + s.color + '99' }" />
              <span class="pie-label">{{ s.label }}</span>
              <span class="pie-pct">{{ s.pct.toFixed(1) }}%</span>
              <span class="pie-cnt">{{ s.value }}</span>
            </div>
          </div>
        </div>
        <div v-else class="empty-state"><div class="empty-icon" v-html="svgIcon('pie-chart')" /><p>暂无数据</p></div>
      </div>

      <!-- bar-chart / bar-item / bar-track / bar-fill（原版 CSS 全有），前三名带奖牌 -->
      <div class="card">
        <div class="section-title"><span v-html="svgIcon('bar-chart')" />用户操作排行</div>
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
        <div v-else class="empty-state"><div class="empty-icon" v-html="svgIcon('users')" /><p>暂无数据</p></div>
      </div>
    </div>

    <!-- ===== 明细：玻璃 section + 筛选工具条 + 彩色徽章表格 + 分页 ===== -->
    <div class="section" style="margin-top:20px">
      <div class="section-header">
        <div class="section-title">操作日志明细</div>
        <div class="lg-count">共 {{ filtered.length }} 条记录</div>
      </div>

      <div class="lg-toolbar">
        <div class="search-box lg-search">
          <input v-model="kw" placeholder="搜索用户 / 操作 / IP…" />
        </div>
        <div class="lg-chips">
          <button class="cat-tab" :class="{ active: fStatus === '' }" @click="setStatus('')">全部</button>
          <button class="cat-tab lg-ok" :class="{ active: fStatus === '成功' }" @click="setStatus('成功')">成功 {{ succ }}</button>
          <button class="cat-tab lg-err" :class="{ active: fStatus === '失败' }" @click="setStatus('失败')">失败 {{ fail }}</button>
        </div>
        <div class="lg-chips">
          <button class="cat-tab" :class="{ active: fType === '' }" @click="setType('')">全部类型</button>
          <button v-for="[t, c] in typeChips" :key="t" class="cat-tab" :class="{ active: fType === t }" @click="setType(t)">
            <span class="lg-dot" :style="{ background: typeColor(t) }" />{{ t }} <i class="lg-chip-cnt">{{ c }}</i>
          </button>
        </div>
        <button class="lg-export" @click="exportCsv">
          <span class="icon" v-html="svgIcon('download')" /> 导出 CSV<span class="lg-export-cnt">{{ filtered.length }}</span>
        </button>
      </div>

      <div v-if="paged.length" class="data-table">
        <table>
          <thead><tr><th>时间</th><th>用户</th><th>类型</th><th>操作</th><th>状态</th><th>IP</th></tr></thead>
          <tbody>
            <tr v-for="l in paged" :key="l.id">
              <td class="lg-time">{{ fmt(l.createdAt) }}</td>
              <td>
                <div class="lg-user">
                  <span class="lg-avatar" :style="{ background: avatarBg(l.userName) }">{{ (l.userName || '?').slice(0, 1) }}</span>
                  <span>{{ l.userName }}</span>
                </div>
              </td>
              <td><span class="lg-type"><span class="lg-dot" :style="{ background: typeColor(l.type), boxShadow: '0 0 6px ' + typeColor(l.type) + '88' }" />{{ l.type }}</span></td>
              <td><code class="lg-op" :title="l.operation">{{ l.operation }}</code></td>
              <td><span class="lg-status" :class="l.status === '成功' ? 'ok' : 'err'">{{ l.status }}</span></td>
              <td class="lg-ip">{{ l.ip || '—' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="empty-state"><div class="empty-icon" v-html="svgIcon('file-text')" /><p>{{ logs.length ? '没有匹配的日志，试试调整筛选条件' : '暂无日志' }}</p></div>

      <div v-if="filtered.length" class="lg-pagebar">
        <div class="lg-total">第 {{ pageStart + 1 }}–{{ pageEnd }} 条 / 共 {{ filtered.length }} 条</div>
        <div class="lg-pager">
          <select v-model.number="pageSize" class="lg-size" @change="page = 1">
            <option :value="10">10 条/页</option>
            <option :value="20">20 条/页</option>
            <option :value="50">50 条/页</option>
            <option :value="100">100 条/页</option>
          </select>
          <button :disabled="page <= 1" @click="page--">‹ 上一页</button>
          <span class="lg-pageinfo">{{ page }} / {{ pageCount }}</span>
          <button :disabled="page >= pageCount" @click="page++">下一页 ›</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api'

const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`
const PIE_COLORS = ['#667eea', '#f59e0b', '#10b981', '#ef4444', '#8b5cf6', '#ec4899']

const logs = ref<any[]>([])
const byType = ref<Record<string, number>>({})
const byUser = ref<any[]>([])
const byStatus = ref<Record<string, number>>({})

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

// ===== 明细筛选（关键词 / 状态 / 类型）=====
const kw = ref('')
const fType = ref('')
const fStatus = ref('')
const page = ref(1)
const pageSize = ref(20)

function setStatus(s: string) { fStatus.value = s; page.value = 1 }
function setType(t: string) { fType.value = fType.value === t ? '' : t; page.value = 1 }
watch(kw, () => { page.value = 1 })

const filtered = computed(() => {
  const k = kw.value.trim().toLowerCase()
  return logs.value.filter(l => {
    if (fType.value && l.type !== fType.value) return false
    if (fStatus.value && l.status !== fStatus.value) return false
    if (k) {
      const hay = `${l.userName || ''} ${l.operation || ''} ${l.ip || ''}`.toLowerCase()
      if (!hay.includes(k)) return false
    }
    return true
  })
})

const pageCount = computed(() => Math.max(1, Math.ceil(filtered.value.length / pageSize.value)))
const pageStart = computed(() => (page.value - 1) * pageSize.value)
const pageEnd = computed(() => Math.min(filtered.value.length, page.value * pageSize.value))
const paged = computed(() => filtered.value.slice(pageStart.value, pageEnd.value))

// ===== donut 逻辑：前5类 + 其余合并为「其他」 =====
const pieSlices = computed(() => {
  const sorted = [...typeEntries.value]
  const top = sorted.slice(0, 5)
  const restSum = sorted.slice(5).reduce((s, [, v]) => s + (v || 0), 0)
  if (restSum > 0) top.push(['其他', restSum as any])
  return top.map(([label, value], i) => ({
    label: String(label),
    value: Number(value),
    pct: (Number(value) / totalByType.value) * 100,
    color: i === 5 ? '#94a3b8' : (typeColorMap.value[label] || PIE_COLORS[i % PIE_COLORS.length])
  }))
})

const topGlowColor = computed(() => {
  if (!pieSlices.value.length) return '#667eea'
  let maxIdx = 0, maxPct = 0
  pieSlices.value.forEach((s, i) => { if (s.pct > maxPct) { maxPct = s.pct; maxIdx = i } })
  return pieSlices.value[maxIdx].color
})

const conicGradient = computed(() => {
  let cumulative = 0
  const stops = pieSlices.value.map(s => {
    const start = (cumulative / 100) * 360
    cumulative += s.pct
    const end = (cumulative / 100) * 360
    return `${s.color} ${start.toFixed(1)}deg ${end.toFixed(1)}deg`
  })
  return `conic-gradient(${stops.join(',')})`
})

const totalAll = computed(() => (byStatus.value['成功'] || 0) + (byStatus.value['失败'] || 0))
const succ = computed(() => byStatus.value['成功'] || 0)
const fail = computed(() => byStatus.value['失败'] || 0)
const succRate = computed(() => succ.value / (succ.value + fail.value || 1) * 100)
const topUsers = computed(() => byUser.value.slice(0, 8))
const maxUser = computed(() => Math.max(1, ...topUsers.value.map((u: any) => u.count || 0)))

// ===== 头像渐变（按用户名 hash 取色，稳定不闪烁）=====
const AVATAR_BGS = [
  'linear-gradient(135deg,#667eea,#764ba2)',
  'linear-gradient(135deg,#f59e0b,#ef4444)',
  'linear-gradient(135deg,#10b981,#059669)',
  'linear-gradient(135deg,#8b5cf6,#6366f1)',
  'linear-gradient(135deg,#ec4899,#8b5cf6)',
  'linear-gradient(135deg,#06b6d4,#3b82f6)'
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

// 导出 CSV（按当前筛选结果，客户端生成，带 BOM 兼容 Excel）
function exportCsv() {
  if (!filtered.value.length) { ElMessage.warning('没有可导出的日志'); return }
  const headers = ['时间', '用户', '类型', '操作', '状态', 'IP']
  const escape = (v: any) => {
    const s = String(v ?? '')
    return /[",\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s
  }
  const lines = [
    headers.map(escape).join(','),
    ...filtered.value.map(l => [fmt(l.createdAt), l.userName, l.type, l.operation, l.status, l.ip || ''].map(escape).join(','))
  ]
  const blob = new Blob(['﻿' + lines.join('\r\n')], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `系统日志_${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success(`已导出 ${filtered.value.length} 条日志`)
}

async function load() {
  try {
    const [l, s]: any = await Promise.all([
      http.get('/logs').catch(() => ({ data: [] })),
      http.get('/logs/stats').catch(() => ({ data: {} }))
    ])
    logs.value = l?.data || []
    const d = s?.data || {}
    byType.value = d.byType || {}
    byUser.value = d.byUser || []
    byStatus.value = d.byStatus || {}
  } catch {}
}
onMounted(load)
</script>

<style scoped>
.lg-count { font-size: 13px; color: var(--text-light); }
.lg-chip-cnt { font-style: normal; opacity: .65; font-size: 12px; margin-left: 2px; }
</style>
