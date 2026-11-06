<template>
  <div class="an-analysis">
    <AnalysisKpi :items="kpis" />

    <div v-if="!s.scored" class="an-panel">
      <div class="an-empty">
        <div class="an-empty-icon" v-html="svgIcon('award')" />
        <p>还没有可分析的成绩数据</p>
        <small>成绩由教师或管理员录入后，这里会展示总分分布、课程对比与年级差异</small>
      </div>
    </div>

    <template v-else>
      <div class="an-grid an-grid-main">
        <section class="an-panel an-panel-wide">
          <header class="an-panel-head">
            <div>
              <div class="an-panel-title"><span class="an-title-icon" v-html="svgIcon('bar-chart')" />课程平均分对比</div>
              <div class="an-panel-sub">按录入人数排序 · 均分横条与及格 / 优秀参考线</div>
            </div>
            <div class="an-panel-side an-box-focus">
              <template v-if="focusCourse">
                <b>{{ focusCourse.courseName }}</b>
                <span>均分 {{ focusCourse.avg }} · 最高 {{ focusCourse.max }} · 最低 {{ focusCourse.min }} · 及格率 {{ focusCourse.passRate }}%</span>
              </template>
              <template v-else>悬停查看课程明细</template>
            </div>
          </header>

          <div v-if="!rows.length" class="an-empty">
            <div class="an-empty-icon" v-html="svgIcon('bar-chart')" />
            <p>暂无课程成绩</p>
          </div>

          <template v-else>
            <div class="an-chart-scroll">
              <EChart :option="avgOption" fill />
            </div>

            <div class="an-legend an-legend-box">
              <span><i class="box" />课程平均分</span>
              <span><i class="tick rose" />及格线 60</span>
              <span><i class="tick gold" />优秀线 90</span>
            </div>
          </template>
        </section>

        <section class="an-panel an-panel-side-panel">
          <header class="an-panel-head an-panel-head-stack">
            <div>
              <div class="an-panel-title"><span class="an-title-icon" v-html="svgIcon('target')" />平时分 × 期末分</div>
              <div class="an-panel-sub">每个圆点是一条成绩记录，虚线为平时 = 期末</div>
            </div>
          </header>

          <div class="an-dist-stats">
            <div class="an-dist-stat"><b>{{ s.avgRegular }}</b><span>平均平时分</span></div>
            <div class="an-dist-stat"><b>{{ s.avgFinal }}</b><span>平均期末分</span></div>
            <div class="an-dist-stat ok"><b>{{ s.correlation == null ? '—' : s.correlation }}</b><span>相关系数 r</span></div>
            <div class="an-dist-stat"><b>{{ s.scatterCount }}</b><span>样本量</span></div>
          </div>

          <div v-if="!scatter.length" class="an-empty">
            <div class="an-empty-icon" v-html="svgIcon('target')" />
            <p>暂无同时含平时分与期末分的记录</p>
          </div>

          <template v-else>
            <div class="an-chart-scroll">
              <EChart :option="scOption" fill />
            </div>

            <div class="an-legend an-legend-dots">
              <span><i class="dot ok" />总分及格</span>
              <span><i class="dot bad" />总分不及格</span>
              <span><i class="dot na" />无总分</span>
              <span class="an-muted">最多展示 400 个采样点</span>
            </div>

            <div class="an-insight-row">
              <div class="an-insight">
                <span>线性相关性</span>
                <b>{{ corrText }}</b>
              </div>
              <div class="an-insight">
                <span>期末普遍高于平时</span>
                <b>{{ s.avgFinal > s.avgRegular ? '是' : s.avgFinal < s.avgRegular ? '否' : '持平' }}</b>
              </div>
              <div class="an-insight">
                <span>分差（期末 − 平时）</span>
                <b>{{ diffText }}</b>
              </div>
            </div>
          </template>
        </section>
      </div>

      <div class="an-grid an-grid-2">
        <section class="an-panel">
          <header class="an-panel-head">
            <div>
              <div class="an-panel-title"><span class="an-title-icon" v-html="svgIcon('clipboard')" />总分分布</div>
              <div class="an-panel-sub">全部已录入成绩 · 10 分一段</div>
            </div>
            <div class="an-panel-side">
              <div class="an-rate-pill"><b>{{ gMax }}</b><span>人数最多一段</span></div>
            </div>
          </header>

          <div class="an-chart-scroll">
            <EChart :option="histOption" fill />
          </div>
        </section>

        <section class="an-panel">
          <header class="an-panel-head">
            <div>
              <div class="an-panel-title"><span class="an-title-icon" v-html="svgIcon('users')" />年级成绩对比</div>
              <div class="an-panel-sub">各入学年级学生的总分均值</div>
            </div>
            <div class="an-panel-side"><span class="an-mini-title">共 {{ cohorts.length }} 个年级</span></div>
          </header>

          <div v-if="cohorts.length" class="an-bars">
            <div v-for="c in cohorts" :key="c.grade" class="an-bar-row">
              <span class="an-bar-label">{{ c.grade }} 级</span>
              <div class="an-bar-track">
                <i class="an-bar-fill violet" :style="{ width: Math.min(100, c.avg) + '%' }" />
              </div>
              <span class="an-bar-val">{{ c.avg }}</span>
            </div>
          </div>
          <div v-else class="an-empty">
            <div class="an-empty-icon" v-html="svgIcon('users')" />
            <p>暂无年级数据</p>
            <small>学生资料未填写入学年级时不参与统计</small>
          </div>
        </section>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import AnalysisKpi from './AnalysisKpi.vue'
import EChart from '@/components/charts/EChart.vue'
import { catAxis, PALETTE, TOOLTIP, valAxis } from '@/utils/echarts'

const props = defineProps<{ data: any }>()

const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`

const s = computed<any>(() => props.data?.summary || {})
const courses = computed<any[]>(() => props.data?.courses || [])
const rows = computed<any[]>(() => courses.value.slice(0, 10))
const scatter = computed<any[]>(() => props.data?.scatter || [])
const dist = computed<any[]>(() => props.data?.dist || [])
const cohorts = computed<any[]>(() => props.data?.cohorts || [])
const hoverId = ref<number | null>(null)

const focusCourse = computed<any>(() => {
  if (hoverId.value != null) {
    return rows.value.find(c => c.courseId === hoverId.value) || rows.value[0] || null
  }
  return null
})

const courseName = (id: number) =>
  courses.value.find(c => c.courseId === id)?.courseName || '已删除课程'

const kpis = computed(() => [
  { label: '已录成绩', value: s.value.records || 0, hint: `覆盖 ${s.value.courses || 0} 门课程`, icon: 'file-text', tone: 'blue' },
  { label: '全体平均分', value: s.value.avg || 0, hint: `最高 ${s.value.max ?? '—'} · 最低 ${s.value.min ?? '—'}`, icon: 'target', tone: 'purple' },
  { label: '及格率', value: (s.value.passRate || 0) + '%', hint: `${s.value.failCount || 0} 人不及格`, icon: 'check-circle', tone: 'green' },
  { label: '优秀率', value: (s.value.excellentRate || 0) + '%', hint: `90 分以上 ${s.value.excellentCount || 0} 人`, icon: 'award', tone: 'amber' },
])

/* ===== 课程平均分对比（ECharts 横向条形）===== */
const avgRows = computed(() => rows.value.map(r => ({ name: (r.courseName || '').slice(0, 8), avg: r.avg || 0 })))
const avgOption = computed(() => ({
  grid: { left: 110, right: 40, top: 16, bottom: 30 },
  tooltip: { trigger: 'item', ...TOOLTIP, formatter: (p: any) => p.name + ' 均分 ' + p.value + ' 分' },
  xAxis: { ...valAxis('平均分'), max: 100 },
  yAxis: { ...catAxis([...avgRows.value].reverse().map(r => r.name)) },
  series: [{
    name: '平均分', type: 'bar', barWidth: '55%',
    data: [...avgRows.value].reverse().map(r => ({
      value: r.avg,
      itemStyle: { color: r.avg >= 90 ? '#f59e0b' : r.avg >= 60 ? '#2bb187' : '#fb7185', borderRadius: [0, 6, 6, 0] },
    })),
    markLine: {
      silent: true, symbol: 'none',
      lineStyle: { type: 'dashed', color: 'rgba(148,163,184,.6)' },
      data: [
        { yAxis: 60, label: { formatter: '及格 60', color: '#94a3b8', fontSize: 10 } },
        { yAxis: 90, label: { formatter: '优秀 90', color: '#94a3b8', fontSize: 10 } },
      ],
    },
    animationDuration: 900,
  }],
}))

/* ===== 平时分 × 期末分散点（ECharts）===== */
const scOption = computed(() => ({
  grid: { left: 40, right: 18, top: 20, bottom: 34 },
  tooltip: { trigger: 'item', ...TOOLTIP, formatter: (p: any) => p.data.name + '<br>平时 ' + p.value[0] + ' · 期末 ' + p.value[1] },
  xAxis: { ...valAxis('平时分'), max: 100 },
  yAxis: { ...valAxis('期末分'), max: 100 },
  series: [{
    name: '成绩', type: 'scatter',
    data: scPoints.value.map(p => ({
      value: [p.r, p.f], name: p.name,
      itemStyle: { color: (p.r + p.f) / 2 >= 60 ? 'rgba(52,211,153,.85)' : 'rgba(251,113,133,.85)', borderColor: 'rgba(255,255,255,.7)', borderWidth: 1 },
    })),
    symbolSize: 11,
    markLine: {
      silent: true, symbol: 'none',
      lineStyle: { color: 'rgba(148,163,184,.5)', type: 'dashed' },
      data: [[{ coord: [0, 100] }, { coord: [100, 0] }]],
      label: { formatter: '平时 = 期末', color: '#94a3b8', fontSize: 10 },
    },
    animationDuration: 900,
  }],
}))
const scPoints = computed(() =>
  scatter.value.filter((p: any) => p.r != null && p.f != null).map((p: any) => ({
    name: courseName(p.c), r: p.r, f: p.f,
  }))
)

/* 相关性描述（模板洞察使用） */
const corrText = computed(() => {
  const r = s.value.correlation
  if (r == null) return '样本不足'
  if (r >= 0.7) return '强相关'
  if (r >= 0.4) return '中度相关'
  if (r > -0.4) return '弱相关'
  return '负相关'
})
const diffText = computed(() => {
  const d = Number(s.value.avgFinal) - Number(s.value.avgRegular)
  if (!s.value.scatterCount) return '—'
  return (d > 0 ? '+' : '') + d.toFixed(1) + ' 分'
})

/* ===== 总分分布直方图（ECharts）===== */
const histBins = computed(() => dist.value.map((b: any) => ({
  label: b.low + '-' + (b.low >= 90 ? 100 : b.low + 9),
  count: b.count || 0,
})))
const histOption = computed(() => ({
  grid: { left: 40, right: 14, top: 24, bottom: 30 },
  tooltip: { trigger: 'axis', ...TOOLTIP },
  xAxis: { ...catAxis(histBins.value.map(b => b.label)), axisLabel: { color: '#94a3b8', fontSize: 9.5, interval: 1 } },
  yAxis: { ...valAxis('人数') },
  series: [{
    name: '人数', type: 'bar', barWidth: '62%',
    data: histBins.value.map((b, i) => ({
      value: b.count,
      itemStyle: { color: i * 10 < 60 ? 'rgba(244,63,94,.55)' : i * 10 >= 90 ? 'rgba(245,158,11,.6)' : 'rgba(43,177,135,.55)', borderRadius: [4, 4, 0, 0] },
    })),
    markLine: {
      silent: true, symbol: 'none',
      lineStyle: { color: 'rgba(148,163,184,.4)', type: 'dashed' },
      data: [{ xAxis: '60-69', label: { formatter: '及格线', color: '#94a3b8', fontSize: 10 } }],
    },
    animationDuration: 900,
  }],
}))
</script>
