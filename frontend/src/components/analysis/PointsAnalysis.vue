<template>
  <div class="an-analysis">
    <AnalysisKpi :items="kpis" />

    <div class="an-grid an-grid-main">
      <section class="an-panel an-panel-wide">
        <header class="an-panel-head">
          <div>
            <div class="an-panel-title"><span class="an-title-icon" v-html="svgIcon('bar-chart')" />投分箱线图</div>
            <div class="an-panel-sub">Top {{ boxRows.length }} 热门课程 · 五分位数与最低录取线（悬停看详情）</div>
          </div>
          <div class="an-panel-side an-box-focus">
            <template v-if="focusCourse">
              <b>{{ focusCourse.courseName }}</b>
              <span>{{ focusCourse.applied }} 人报名 · 录取 {{ focusCourse.approved }} · 录取线 {{ focusCourse.cutline == null ? '全部录取' : focusCourse.cutline + ' 分' }}</span>
            </template>
            <template v-else>悬停查看课程 · 点击切换右侧投分分布</template>
          </div>
        </header>

        <div v-if="boxRows.length" class="an-chart-scroll ec-host">
          <EChart :option="boxOption" fill />
        </div>
        <div v-else class="an-empty">
          <div class="an-empty-icon" v-html="svgIcon('coins')" />
          <p>本轮还没有选修课报名数据</p>
          <small>切换到其他轮次，或等学生提交选课后再来看</small>
        </div>
      </section>

      <section v-if="selectedCourse" class="an-panel an-panel-side-panel">
        <header class="an-panel-head an-panel-head-stack">
          <div>
            <div class="an-panel-title"><span class="an-title-icon" v-html="svgIcon('target')" />单课投分分布</div>
            <div class="an-panel-sub">柱 = 各分段人数 · 点 = 采样投分（颜色区分结果）</div>
          </div>
          <div class="an-select-wrap">
            <select v-model.number="selectedId" class="an-select" aria-label="选择课程">
              <option v-for="c in courseOptions" :key="c.courseId" :value="c.courseId">
                {{ c.courseName }} · {{ c.applied }} 人
              </option>
            </select>
          </div>
        </header>

        <div class="an-dist-stats">
          <div class="an-dist-stat"><b>{{ selectedCourse.applied }}</b><span>报名</span></div>
          <div class="an-dist-stat ok"><b>{{ selectedCourse.approved }}</b><span>录取</span></div>
          <div class="an-dist-stat cut"><b>{{ selectedCourse.cutline == null ? '—' : selectedCourse.cutline }}</b><span>录取线</span></div>
          <div class="an-dist-stat"><b>{{ selectedCourse.box.median }}</b><span>中位分</span></div>
        </div>

        <EChart :option="distOption" fill />

        <div class="an-legend an-legend-dots">
          <span><i class="dot ok" />已录取</span>
          <span><i class="dot bad" />未录取</span>
          <span><i class="dot wait" />待结算</span>
        </div>

        <div class="an-insight-row">
          <div class="an-insight">
            <span>竞争比</span>
            <b>{{ selectedCourse.approved ? (selectedCourse.applied / selectedCourse.approved).toFixed(1) : '—' }} : 1</b>
          </div>
          <div class="an-insight">
            <span>同分落选</span>
            <b>{{ selectedCourse.tieRejected }} 人</b>
          </div>
          <div class="an-insight">
            <span>高分落选</span>
            <b>{{ selectedCourse.highRejected }} 人</b>
          </div>
        </div>
      </section>
    </div>

    <!-- ===== 竞争态势散点 ===== -->
    <div class="section an-panel" style="margin-top:14px">
      <header class="an-panel-head">
        <div>
          <div class="an-panel-title"><span class="an-title-icon" v-html="svgIcon('target')" />竞争态势散点</div>
          <div class="an-panel-sub">X = 平均投分 · Y = 录取线 · 气泡 = 报名人数 · 虚线为「线 = 均分」分界</div>
        </div>
        <div class="an-legend">
          <span><i class="dot over" />超额课程</span>
          <span><i class="dot norm" />未超额</span>
        </div>
      </header>
      <div v-if="scatterRows.length">
        <EChart :option="cmpOption" height="300px" />
      </div>
      <div v-else class="an-empty">
        <div class="an-empty-icon" v-html="svgIcon('target')" />
        <p>暂无报名数据</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AnalysisKpi from './AnalysisKpi.vue'
import EChart from '@/components/charts/EChart.vue'
import { catAxis, TOOLTIP, valAxis } from '@/utils/echarts'

const props = defineProps<{ data: any }>()

const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`

const s = computed<any>(() => props.data?.summary || {})
const boxRows = computed<any[]>(() => (props.data?.courses || []).slice(0, 10))
const courseOptions = computed<any[]>(() => props.data?.courses || [])
const selectedId = ref<number | null>(null)
const hoverId = ref<number | null>(null)

watch(
  () => props.data,
  (d) => {
    const list = d?.courses || []
    if (list.length) selectedId.value = list[0].courseId
  },
  { immediate: true }
)

const selectedCourse = computed<any>(() => {
  const list = props.data?.courses || []
  return list.find((c: any) => c.courseId === selectedId.value) || list[0] || null
})
const focusCourse = computed<any>(() => {
  if (hoverId.value != null) {
    return boxRows.value.find(c => c.courseId === hoverId.value) || selectedCourse.value
  }
  return selectedCourse.value
})

const kpis = computed(() => [
  { label: '参与竞争的课程', value: s.value.activeCourses || 0, hint: `共 ${s.value.electiveCourses || 0} 门可选课程`, icon: 'book-open', tone: 'blue' },
  { label: '投分人次', value: s.value.applied || 0, hint: `录取 ${s.value.approved || 0} 人次`, icon: 'users', tone: 'green' },
  { label: '平均录取线', value: s.value.avgCutline ? s.value.avgCutline + ' 分' : '—', hint: '仅统计产生落选的课程', icon: 'target', tone: 'purple' },
  { label: '同分抽签落选', value: s.value.tieRejected || 0, hint: `高分落选 ${s.value.highRejected || 0} 人次`, icon: 'alert-triangle', tone: 'rose' },
])

function shortName(n: string) { return (n || '').length > 8 ? n.slice(0, 7) + '…' : n }

/* ===== 箱线图 + 录取线（ECharts）===== */
const boxOption = computed(() => ({
  grid: { left: 44, right: 18, top: 18, bottom: 64 },
  tooltip: { trigger: 'item', ...TOOLTIP },
  xAxis: {
    ...catAxis(boxRows.value.map(c => shortName(c.courseName))),
    axisLabel: { color: '#94a3b8', fontSize: 10, interval: 0, rotate: 32 },
  },
  yAxis: { ...valAxis('投分') },
  series: [
    {
      name: '投分分布', type: 'boxplot',
      data: boxRows.value.map(c => [c.box.min, c.box.q1, c.box.median, c.box.q3, c.box.max]),
      itemStyle: { color: 'rgba(43,177,135,.55)', borderColor: '#9adfcb', borderWidth: 2 },
      emphasis: { itemStyle: { borderColor: '#b9e8da' } },
      animationDuration: 800,
    },
    {
      name: '最低录取线', type: 'scatter', symbol: 'diamond', symbolSize: 12,
      data: boxRows.value.map(c => [shortName(c.courseName), c.cutline == null ? 0 : c.cutline]),
      itemStyle: { color: '#fb7185' },
      tooltip: { formatter: (p: any) => p.value[0] + ' 最低录取线 ' + (p.value[1] || '—') + ' 分' },
    },
  ],
}))

/* ===== 单课投分分布（柱 = 分段人数，点 = 采样）===== */
const distOption = computed(() => {
  const pts = selectedCourse.value?.points || []
  const bins = new Array(10).fill(0)
  for (const p of pts) bins[Math.max(0, Math.min(9, Math.floor(p.p / 5)))]++
  const cats = bins.map((_, i) => (i * 5) + '-' + (i * 5 + 4))
  return {
    grid: { left: 40, right: 14, top: 20, bottom: 30 },
    tooltip: { trigger: 'axis', ...TOOLTIP },
    xAxis: { ...catAxis(cats), axisLabel: { color: '#94a3b8', fontSize: 9.5, interval: 1 } },
    yAxis: { ...valAxis('人数') },
    series: [
      { name: '人数', type: 'bar', barWidth: '62%', data: bins, itemStyle: { color: 'rgba(43,177,135,.5)', borderRadius: [4, 4, 0, 0] } },
      {
        name: '投分采样', type: 'scatter',
        data: pts.map((p: any) => ({ value: [Math.min(49, p.p), 1.2], s: p.s })),
        symbolSize: 9,
        itemStyle: { color: (p: any) => p.value[2] === 'approved' ? '#34d399' : p.value[2] === 'rejected' ? '#fb7185' : '#fbbf24', opacity: .95 },
        tooltip: { formatter: (p: any) => p.value[0] + ' 分 · ' + (p.value[2] === 'approved' ? '已录取' : p.value[2] === 'rejected' ? '未录取' : '待结算') },
      },
    ],
  }
})

/* ===== 竞争态势散点（ECharts）===== */
const scatterRows = computed<any[]>(() =>
  (props.data?.courses || []).filter((c: any) => (c.applied || 0) > 0)
)
const cmpYMax = computed(() => {
  const lines = scatterRows.value.map(c => c.cutline).filter((v: any) => v != null)
  return Math.max(20, ...(lines.length ? lines : [20]))
})
const cmpOption = computed(() => ({
  grid: { left: 44, right: 22, top: 24, bottom: 40 },
  tooltip: {
    trigger: 'item', ...TOOLTIP,
    formatter: (p: any) => p.name + '<br>平均投分 ' + p.value[0] + ' · 线 ' + (p.value[1] || '无') + '<br>报名 ' + p.data.applied + '/' + p.data.capacity,
  },
  xAxis: { ...valAxis('平均投分'), max: 50 },
  yAxis: { ...valAxis('录取线'), max: cmpYMax.value },
  series: [{
    name: '课程', type: 'scatter',
    data: scatterRows.value.map(c => ({
      value: [c.box?.avg || 0, c.cutline == null ? 0 : c.cutline],
      name: c.courseName,
      applied: c.applied, capacity: c.capacity,
      symbolSize: 8 + Math.min(14, Math.sqrt(c.applied || 1) * 2.2),
      itemStyle: { color: (c.applied || 0) > (c.capacity || 0) ? 'rgba(244,63,94,.92)' : 'rgba(43,177,135,.9)', borderColor: 'rgba(255,255,255,.85)', borderWidth: 1.5 },
    })),
    markLine: {
      silent: true, symbol: 'none',
      lineStyle: { color: 'rgba(148,163,184,.5)', type: 'dashed' },
      data: [[{ coord: [0, 0] }, { coord: [50, 50] }]],
      label: { formatter: '线 = 均分', color: '#94a3b8', fontSize: 10 },
    },
    animationDuration: 900,
  }],
}))
</script>
