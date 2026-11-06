<template>
  <div class="an-analysis">
    <AnalysisKpi :items="kpis" />

    <div class="an-grid an-grid-main">
      <section class="an-panel an-panel-wide">
        <header class="an-panel-head">
          <div>
            <div class="an-panel-title"><span class="an-title-icon" v-html="svgIcon('cpu')" />请求节奏 · 近 30 分钟</div>
            <div class="an-panel-sub">逐分钟操作量、报名类操作与失败请求，峰值 {{ s.peakMinute }}</div>
          </div>
          <div class="an-panel-side">
            <div class="an-rate-pill"><b>{{ fmtQps(s.qpsPeak) }}</b><span>峰值 QPS</span></div>
          </div>
        </header>
        <div class="an-chart-scroll">
          <EChart :option="timelineOption" fill />
        </div>
      </section>

      <section class="an-panel">
        <header class="an-panel-head">
          <div>
            <div class="an-panel-title"><span class="an-title-icon" v-html="svgIcon('alert-triangle')" />实时告警</div>
            <div class="an-panel-sub">流量、失败率、异常账号与课程拥挤自动巡检</div>
          </div>
        </header>
        <div class="an-alerts">
          <div v-for="a in alerts" :key="a.title" class="an-alert" :class="'lvl-' + a.level">
            <span class="an-alert-icon" :class="'lvl-' + a.level" v-html="alertIcon(a.level)" />
            <div class="an-alert-body">
              <div class="an-alert-title">{{ a.title }}</div>
              <div class="an-alert-detail">{{ a.detail }}</div>
            </div>
            <span class="an-alert-time">{{ a.time }}</span>
          </div>
        </div>
      </section>
    </div>

    <div class="an-grid an-grid-2">
      <section class="an-panel">
        <header class="an-panel-head">
          <div>
            <div class="an-panel-title"><span class="an-title-icon" v-html="svgIcon('x-circle')" />失败接口 Top {{ failEndpoints.length }}</div>
            <div class="an-panel-sub">近 1 小时 · 失败率 {{ s.failRate }}%（{{ s.failLastHour }}/{{ s.opsLastHour }} 次请求）</div>
          </div>
        </header>
        <div v-if="failEndpoints.length" class="an-bars">
          <div v-for="f in failEndpoints" :key="f.operation" class="an-bar-row">
            <span class="an-bar-label mono" :title="f.operation">{{ shortOp(f.operation) }}</span>
            <div class="an-bar-track">
              <i class="an-bar-fill rose" :style="{ width: Math.max(6, f.count / maxFail * 100) + '%' }" />
            </div>
            <span class="an-bar-val">{{ f.count }}</span>
          </div>
        </div>
        <div v-else class="an-empty" style="padding: 26px 0">
          <div class="an-empty-icon" v-html="svgIcon('check-circle')" />
          <p>近 1 小时没有失败请求</p>
        </div>
      </section>

      <section class="an-panel">
        <header class="an-panel-head">
          <div>
            <div class="an-panel-title"><span class="an-title-icon" v-html="svgIcon('users')" />高频账号监控</div>
            <div class="an-panel-sub">近 1 小时操作最频繁的账号，超 120 次/时标记疑似脚本</div>
          </div>
        </header>
        <div v-if="topUsers.length" class="an-queue">
          <div v-for="(u, i) in topUsers" :key="u.userName" class="an-queue-row">
            <span class="an-queue-rank" :class="{ top: i < 3 }">{{ String(i + 1).padStart(2, '0') }}</span>
            <div class="an-queue-main">
              <div class="an-queue-top">
                <b>{{ u.userName }}</b>
                <span v-if="u.suspicious" class="an-chip-sus">⚠ 疑似脚本</span>
              </div>
              <div class="an-queue-bar">
                <i :style="{ width: Math.max(6, u.ops / maxUserOps * 100) + '%' }" />
              </div>
            </div>
            <span class="an-user-ops">{{ u.ops }} 次</span>
          </div>
        </div>
        <div v-else class="an-empty" style="padding: 26px 0">
          <div class="an-empty-icon" v-html="svgIcon('users')" />
          <p>近 1 小时暂无操作记录</p>
        </div>
      </section>
    </div>

    <section class="an-panel">
      <header class="an-panel-head">
        <div>
          <div class="an-panel-title"><span class="an-title-icon" v-html="svgIcon('target')" />拥挤课程 · 容量风控</div>
          <div class="an-panel-sub">满员且有候补的课程建议扩容或引导分流至同类课</div>
        </div>
        <div class="an-panel-side an-side-text">
          <span>满员拥挤 <b>{{ s.crowded || 0 }}</b> 门 · 当前待结算 <b>{{ s.pendingNow || 0 }}</b> 条</span>
        </div>
      </header>
      <div v-if="hotCourses.length" class="data-table an-table-scroll">
        <table>
          <thead><tr><th>课程</th><th>报名</th><th>容量</th><th>满员率</th><th>候补队列</th><th>状态</th></tr></thead>
          <tbody>
            <tr v-for="c in hotCourses" :key="c.courseId">
              <td><b>{{ c.courseName }}</b></td>
              <td>{{ c.applied }}</td>
              <td>{{ c.capacity }}</td>
              <td>{{ c.fillRate }}%</td>
              <td>{{ c.waiting }} 人</td>
              <td>
                <span v-if="c.crowded" class="an-badge bad">满员 · 有候补</span>
                <span v-else class="an-badge ok">正常</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="an-empty" style="padding: 20px 0">
        <small>当前轮暂无报名数据</small>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import AnalysisKpi from './AnalysisKpi.vue'
import EChart from '@/components/charts/EChart.vue'
import { TOOLTIP } from '@/utils/echarts'
const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`

const props = defineProps<{ data: any }>()

const s = computed<any>(() => props.data?.summary || {})
const timeline = computed<any[]>(() => props.data?.timeline || [])
const alerts = computed<any[]>(() => props.data?.alerts || [])
const failEndpoints = computed<any[]>(() => props.data?.failByEndpoint || [])
const topUsers = computed<any[]>(() => props.data?.topUsers || [])
const hotCourses = computed<any[]>(() => props.data?.hotCourses || [])

const maxFail = computed(() => Math.max(1, ...failEndpoints.value.map(f => f.count || 0)))
const maxUserOps = computed(() => Math.max(1, ...topUsers.value.map(u => u.ops || 0)))

const healthTone = computed(() => {
  const v = s.value.healthScore || 0
  return v >= 80 ? 'green' : v >= 60 ? 'blue' : v >= 40 ? 'amber' : 'red'
})

const kpis = computed(() => [
  { label: '系统健康分', value: (s.value.healthScore || 0) + ' 分', hint: healthText(s.value.healthScore || 0), icon: 'cpu', tone: healthTone.value },
  { label: '当前 QPS', value: fmtQps(s.value.qpsNow), hint: `峰值 ${fmtQps(s.value.qpsPeak)} · ${s.value.peakMinute || '—'}`, icon: 'refresh', tone: 'blue' },
  { label: '近 1 小时请求', value: s.value.opsLastHour || 0, hint: `报名类 ${s.value.enrollLastHour || 0} 次 · 活跃 ${s.value.activeUsers || 0} 人`, icon: 'bar-chart', tone: 'purple' },
  { label: '失败率', value: (s.value.failRate || 0) + '%', hint: `${s.value.failLastHour || 0} 次失败 · 今日 ${s.value.todayFails || 0} 次`, icon: 'alert-triangle', tone: (s.value.failRate || 0) > 5 ? 'amber' : 'green' },
])

function fmtQps(v: any) {
  return (Number(v) || 0).toFixed(2)
}
function healthText(v: number) {
  return v >= 80 ? '运行健康' : v >= 60 ? '轻度波动' : v >= 40 ? '需要关注' : '高风险状态'
}
function shortOp(op: string) {
  if (!op) return '未知'
  return op.replace('POST ', '').replace('GET ', '')
}
function alertIcon(level: string) {
  if (level === 'danger') return svgIcon('alert-triangle')
  if (level === 'warn') return svgIcon('bell')
  return svgIcon('check-circle')
}

const timelineOption = computed(() => ({
  tooltip: { trigger: 'axis', ...TOOLTIP },
  legend: { data: ['全部操作', '报名类', '失败'], textStyle: { color: '#94a3b8' }, top: 0 },
  grid: { left: 8, right: 8, top: 30, bottom: 0, containLabel: true },
  xAxis: { type: 'category', data: timeline.value.map(t => t.minute), axisLabel: { color: '#94a3b8', interval: 4 } },
  yAxis: { type: 'value', splitLine: { lineStyle: { color: 'rgba(148,163,184,.15)' } }, axisLabel: { color: '#94a3b8' } },
  series: [
    { name: '全部操作', type: 'bar', barWidth: '46%', itemStyle: { color: '#2bb187', borderRadius: [4, 4, 0, 0] }, data: timeline.value.map(t => t.ops) },
    { name: '报名类', type: 'line', smooth: true, symbol: 'none', itemStyle: { color: '#22d3ee' }, data: timeline.value.map(t => t.enrolls) },
    { name: '失败', type: 'line', smooth: true, symbol: 'none', lineStyle: { type: 'dashed' }, itemStyle: { color: '#f43f5e' }, data: timeline.value.map(t => t.fails) },
  ],
}))
</script>

<style scoped>
.mono { font-family: 'JetBrains Mono', Consolas, monospace; font-size: 12px; max-width: 220px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.an-chip-sus { color: #fbbf24; font-size: 12px; font-weight: 700; }
.an-user-ops { flex: none; align-self: center; font-size: 13px; font-weight: 700; opacity: .8; }
.an-badge { display: inline-block; padding: 1px 8px; border-radius: 999px; font-size: 12px; font-weight: 600; }
.an-badge.bad { background: rgba(239, 68, 68, .13); color: #dc2626; }
.an-badge.ok { background: rgba(16, 185, 129, .13); color: #059669; }
.an-table-scroll { overflow: auto; max-height: 320px; }
</style>
