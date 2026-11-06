<template>
  <div class="an-analysis">
    <AnalysisKpi :items="kpis" />

    <div v-if="!s.students" class="an-panel">
      <div class="an-empty">
        <div class="an-empty-icon" v-html="svgIcon('target')" />
        <p>本轮还没有选修课报名数据</p>
        <small>学生开始报名后，这里会评估分配公平性</small>
      </div>
    </div>

    <template v-else>
      <div class="an-grid an-grid-2">
        <section class="an-panel">
          <header class="an-panel-head">
            <div>
              <div class="an-panel-title"><span class="an-title-icon" v-html="svgIcon('users')" />学生录取分布</div>
              <div class="an-panel-sub">本轮每人录取门数 · 基尼系数 {{ s.gini }}（0 为完全均衡）</div>
            </div>
            <div class="an-panel-side an-side-text">
              <span>共 <b>{{ s.students }}</b> 名学生参与</span>
            </div>
          </header>
          <div class="an-chart-scroll">
            <EChart :option="distOption" fill />
          </div>
        </section>

        <section class="an-panel">
          <header class="an-panel-head">
            <div>
              <div class="an-panel-title"><span class="an-title-icon" v-html="svgIcon('target')" />个人录取率分布</div>
              <div class="an-panel-sub">报名 ≥2 门学生的录取率区间，全落空过多说明策略保护不足</div>
            </div>
          </header>
          <div class="an-bars">
            <div v-for="(b, i) in rateBuckets" :key="b.label" class="an-bar-row">
              <span class="an-bar-label">{{ b.label }}</span>
              <div class="an-bar-track">
                <i class="an-bar-fill" :class="['red', 'amber', 'violet', 'blue', 'green'][i]" :style="{ width: Math.max(b.count ? 4 : 0, b.count / maxRate * 100) + '%' }" />
              </div>
              <span class="an-bar-val">{{ b.count }} 人</span>
            </div>
          </div>
          <div class="an-funnel-foot">
            <div class="an-funnel-metric"><span>整体录取率</span><b>{{ s.approvalRate }}%</b></div>
            <div class="an-funnel-metric"><span>报录比</span><b>{{ s.competition }} : 1</b></div>
            <div class="an-funnel-metric"><span>平均录取线</span><b>{{ s.avgCutline }} 分</b></div>
            <div class="an-funnel-metric warn"><span>年级录取率极差</span><b>{{ s.gradeGap }} pp</b></div>
          </div>
        </section>
      </div>

      <div class="an-grid an-grid-main">
        <section class="an-panel an-panel-wide">
          <header class="an-panel-head">
            <div>
              <div class="an-panel-title"><span class="an-title-icon" v-html="svgIcon('refresh')" />各轮公平性趋势</div>
              <div class="an-panel-sub">平均录取线、录取率与同分抽签落选数的轮次变化</div>
            </div>
          </header>
          <div class="an-chart-scroll">
            <EChart :option="trendOption" fill />
          </div>
        </section>

        <section class="an-panel">
          <header class="an-panel-head">
            <div>
              <div class="an-panel-title"><span class="an-title-icon" v-html="svgIcon('coins')" />同分抽签热点</div>
              <div class="an-panel-sub">压线同分抽签落选最多的课程 · 共 {{ s.lotteryCourses }} 门触发抽签</div>
            </div>
            <div class="an-panel-side">
              <div class="an-rate-pill"><b>{{ s.tieShare }}%</b><span>落选中抽签占比</span></div>
            </div>
          </header>
          <div class="an-queue">
            <div v-for="(q, i) in lottery" :key="q.courseId" class="an-queue-row">
              <span class="an-queue-rank" :class="{ top: i < 3 }">{{ String(i + 1).padStart(2, '0') }}</span>
              <div class="an-queue-main">
                <div class="an-queue-top"><b>{{ q.courseName }}</b><span>抽签落选 {{ q.tieRejected }} 人</span></div>
                <div class="an-queue-bar"><i style="background: linear-gradient(90deg,#f59e0b,#f97316)" :style="{ width: Math.max(8, q.tieRejected / maxTie * 100) + '%' }" /></div>
                <div class="an-queue-meta">
                  <span class="muted">录取线 {{ q.cutline ?? '—' }} · 报名 {{ q.applied }} · 容量 {{ q.capacity }}</span>
                </div>
              </div>
            </div>
            <div v-if="!lottery.length" class="an-empty" style="padding: 20px 0">
              <small>本轮没有触发同分抽签，很平稳</small>
            </div>
          </div>
        </section>
      </div>

      <div class="an-grid an-grid-2">
        <section class="an-panel">
          <header class="an-panel-head">
            <div>
              <div class="an-panel-title"><span class="an-title-icon" v-html="svgIcon('bar-chart')" />课程落选构成</div>
              <div class="an-panel-sub">抽签落选 vs 冲突落选（分数高于录取线仍被拒）</div>
            </div>
          </header>
          <div class="data-table an-table-scroll">
            <table>
              <thead><tr><th>课程</th><th>报名</th><th>录取</th><th>录取率</th><th>录取线</th><th>抽签落选</th><th>冲突落选</th></tr></thead>
              <tbody>
                <tr v-for="c in courses" :key="c.courseId">
                  <td><b>{{ c.courseName }}</b></td>
                  <td>{{ c.applied }}</td>
                  <td>{{ c.approved }}</td>
                  <td>{{ c.approvalRate }}%</td>
                  <td>{{ c.cutline ?? '—' }}</td>
                  <td><span v-if="c.tieRejected" class="an-badge warn">{{ c.tieRejected }}</span><span v-else>0</span></td>
                  <td><span v-if="c.highRejected" class="an-badge bad">{{ c.highRejected }}</span><span v-else>0</span></td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section class="an-panel">
          <header class="an-panel-head">
            <div>
              <div class="an-panel-title"><span class="an-title-icon" v-html="svgIcon('graduation-cap')" />年级间均衡度</div>
              <div class="an-panel-sub">各年级学生的报名与录取表现</div>
            </div>
          </header>
          <div class="an-bars">
            <div v-for="g in grades" :key="g.grade" class="an-bar-row">
              <span class="an-bar-label">{{ g.grade }} 级</span>
              <div class="an-bar-track">
                <i class="an-bar-fill blue" :style="{ width: Math.max(4, g.approvalRate / maxGradeRate * 100) + '%' }" />
              </div>
              <span class="an-bar-val">{{ g.approvalRate }}%</span>
            </div>
          </div>
          <div class="an-funnel-foot">
            <div class="an-funnel-metric"><span>覆盖年级</span><b>{{ grades.length }} 个</b></div>
            <div class="an-funnel-metric"><span>极差</span><b>{{ s.gradeGap }} pp</b></div>
          </div>
        </section>
      </div>
    </template>
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
const approveDist = computed<any[]>(() => props.data?.approveDist || [])
const rateBuckets = computed<any[]>(() => props.data?.rateBuckets || [])
const roundTrend = computed<any[]>(() => props.data?.roundTrend || [])
const courses = computed<any[]>(() => props.data?.courses || [])
const lottery = computed<any[]>(() => props.data?.lottery || [])
const grades = computed<any[]>(() => props.data?.grades || [])

const maxRate = computed(() => Math.max(1, ...rateBuckets.value.map(b => b.count || 0)))
const maxTie = computed(() => Math.max(1, ...lottery.value.map(q => q.tieRejected || 0)))
const maxGradeRate = computed(() => Math.max(1, ...grades.value.map(g => g.approvalRate || 0)))

function scoreTone(v: number) {
  return v >= 80 ? 'green' : v >= 60 ? 'blue' : v >= 40 ? 'amber' : 'red'
}
const scoreToneVal = computed(() => scoreTone(s.value.fairnessScore || 0))

const kpis = computed(() => [
  { label: '分配公平指数', value: (s.value.fairnessScore || 0) + ' 分', hint: scoreText(s.value.fairnessScore || 0), icon: 'target', tone: scoreToneVal.value },
  { label: '本轮录取率', value: (s.value.approvalRate || 0) + '%', hint: `${s.value.approved || 0}/${s.value.applied || 0} · 报录比 ${s.value.competition}:1`, icon: 'check-circle', tone: 'blue' },
  { label: '同分抽签落选', value: s.value.tieRejected || 0, hint: `占落选 ${s.value.tieShare || 0}% · ${s.value.lotteryCourses || 0} 门触发抽签`, icon: 'coins', tone: 'amber' },
  { label: '录取分布基尼', value: s.value.gini ?? '—', hint: `冲突落选 ${s.value.highRejected || 0} 人 · 年级极差 ${s.value.gradeGap || 0}pp`, icon: 'users', tone: 'purple' },
])

function scoreText(v: number) {
  return v >= 80 ? '分配非常均衡' : v >= 60 ? '总体均衡，个别偏斜' : v >= 40 ? '存在明显偏斜' : '分布失衡，建议干预'
}

const distOption = computed(() => ({
  tooltip: { trigger: 'axis', formatter: (ps: any) => `${ps[0].name}：${ps[0].value} 人`, ...TOOLTIP },
  grid: { left: 8, right: 8, top: 18, bottom: 0, containLabel: true },
  xAxis: { type: 'category', data: approveDist.value.map(d => d.label), axisLabel: { color: '#94a3b8' }, axisLine: { lineStyle: { color: '#e2e8f0' } } },
  yAxis: { type: 'value', splitLine: { lineStyle: { color: 'rgba(148,163,184,.15)' } }, axisLabel: { color: '#94a3b8' } },
  series: [{
    type: 'bar', barWidth: '46%',
    data: approveDist.value.map((d, i) => ({
      value: d.count,
      itemStyle: { color: i === 0 ? '#ef4444' : ['#1fa67a', '#0891b2', '#059669', '#0ea5e9', '#f59e0b'][Math.min(i - 1, 4)], borderRadius: [6, 6, 0, 0] },
    })),
    animationDuration: 700,
  }],
}))

const trendOption = computed(() => ({
  tooltip: { trigger: 'axis', ...TOOLTIP },
  legend: { data: ['平均录取线', '录取率%', '抽签落选'], textStyle: { color: '#94a3b8' }, top: 0 },
  grid: { left: 8, right: 8, top: 30, bottom: 0, containLabel: true },
  xAxis: { type: 'category', data: roundTrend.value.map(t => `第${t.round}轮`), axisLabel: { color: '#94a3b8' } },
  yAxis: [
    { type: 'value', name: '分', splitLine: { lineStyle: { color: 'rgba(148,163,184,.15)' } }, axisLabel: { color: '#94a3b8' } },
    { type: 'value', name: '%', splitLine: { show: false }, axisLabel: { color: '#94a3b8' } },
  ],
  series: [
    { name: '平均录取线', type: 'bar', barWidth: '32%', itemStyle: { color: '#1fa67a', borderRadius: [5, 5, 0, 0] }, data: roundTrend.value.map(t => t.avgCutline) },
    { name: '录取率%', type: 'line', yAxisIndex: 1, smooth: true, itemStyle: { color: '#059669' }, data: roundTrend.value.map(t => t.approvalRate) },
    { name: '抽签落选', type: 'line', yAxisIndex: 1, smooth: true, itemStyle: { color: '#f59e0b' }, data: roundTrend.value.map(t => t.tieRejected) },
  ],
}))
</script>

<style scoped>
.an-badge { display: inline-block; padding: 1px 8px; border-radius: 999px; font-size: 12px; font-weight: 600; }
.an-badge.warn { background: rgba(245, 158, 11, .14); color: #d97706; }
.an-badge.bad { background: rgba(239, 68, 68, .13); color: #dc2626; }
.an-table-scroll { overflow: auto; max-height: 320px; }
</style>
