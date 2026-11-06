<template>
  <div class="an-analysis">
    <AnalysisKpi :items="kpis" />

    <div v-if="!s.scheduledCourses" class="an-panel">
      <div class="an-empty">
        <div class="an-empty-icon" v-html="svgIcon('building')" />
        <p>还没有解析到排课时间</p>
        <small>课程时间格式应形如「周二 14:00-15:40 / 周四 08:00-09:40」</small>
      </div>
    </div>

    <template v-else>
      <div class="an-grid an-grid-main">
        <section class="an-panel an-panel-wide">
          <header class="an-panel-head">
            <div>
              <div class="an-panel-title"><span class="an-title-icon" v-html="svgIcon('grid')" />排课时段热力图</div>
              <div class="an-panel-sub">星期 × 时段 · 第 {{ data.round }} 轮 · <small style="color:var(--primary);font-weight:700">{{ metricLabel }}</small></div>
            </div>
            <div class="an-chips" style="margin-bottom:6px">
              <button v-for="m in METRICS" :key="m.key" :class="{ active: metric === m.key }" @click="metric = m.key">{{ m.label }}</button>
            </div>
            <div class="an-panel-side an-heat-detail">
              <template v-if="hoverCell">
                <b>{{ hoverCell.day }} {{ hoverCell.range }}</b>
                <span>{{ hoverCell.courses }} 门课 · {{ hoverCell.students }} 人次</span>
                <em v-if="hoverCell.names?.length">{{ hoverCell.names.join('、') }}{{ hoverCell.more ? ' 等 ' + (hoverCell.names.length + hoverCell.more) + ' 门' : '' }}</em>
              </template>
              <template v-else>
                <b>悬停查看时段</b>
                <span>覆盖 {{ s.coverage }}% 的可用时段</span>
              </template>
            </div>
          </header>

          <div class="an-chart-scroll">
            <EChart :option="heatOption" fill />
          </div>
          <div class="an-heat-foot">
            <div class="an-heat-scale">
              <span>低</span>
              <i />
              <span>高</span>
            </div>
            <div class="an-heat-note">
              空闲时段 {{ s.freeSlots }} 个 · 平均班级规模 {{ s.avgClassSize }} 人
            </div>
          </div>
        </section>

        <section class="an-panel">
          <header class="an-panel-head">
            <div>
              <div class="an-panel-title"><span class="an-title-icon" v-html="svgIcon('map-pin')" />热门教学地点</div>
              <div class="an-panel-sub">按开课门数排序 · 人次 / 教室容量</div>
            </div>
          </header>

          <div class="an-loc-list">
            <div v-for="(loc, i) in data.locations" :key="loc.name" class="an-loc-row">
              <span class="an-loc-rank" :class="{ top: i < 3 }">{{ String(i + 1).padStart(2, '0') }}</span>
              <div class="an-loc-main">
                <div class="an-loc-top">
                  <b>{{ loc.name }}</b>
                  <span>{{ loc.courses }} 门 · {{ loc.students }}/{{ loc.capacity }} 人</span>
                </div>
                <div class="an-loc-bar">
                  <i :style="{ width: Math.max(6, loc.courses / maxLoc * 100) + '%' }" />
                </div>
              </div>
            </div>
          </div>
        </section>
      </div>

      <div class="an-grid an-grid-2">
        <section class="an-panel">
          <header class="an-panel-head">
            <div>
              <div class="an-panel-title"><span class="an-title-icon" v-html="svgIcon('building')" />教学楼排课分布</div>
              <div class="an-panel-sub">同一楼宇内的课程与选课人次</div>
            </div>
          </header>

          <div class="an-bars">
            <div v-for="b in data.buildings" :key="b.name" class="an-bar-row">
              <span class="an-bar-label">{{ b.name }}</span>
              <div class="an-bar-track">
                <i class="an-bar-fill cyan" :style="{ width: Math.max(b.courses ? 4 : 0, b.courses / maxBuilding * 100) + '%' }" />
              </div>
              <span class="an-bar-val">{{ b.courses }} 门</span>
            </div>
          </div>
        </section>

        <section class="an-panel">
          <header class="an-panel-head">
            <div>
              <div class="an-panel-title"><span class="an-title-icon" v-html="svgIcon('calendar')" />一周排课节奏</div>
              <div class="an-panel-sub">按星期汇总课程数</div>
            </div>
          </header>

          <div class="an-daychart">
            <div v-for="d in data.dayLoad" :key="d.day" class="an-day-col">
              <span class="an-day-val">{{ d.courses }}</span>
              <div class="an-day-track">
                <i :style="{ height: Math.max(d.courses ? 8 : 2, d.courses / maxDay * 100) + '%' }" />
              </div>
              <small>{{ d.day }}</small>
              <em>{{ d.students }} 人次</em>
            </div>
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
import { PALETTE, TOOLTIP } from '@/utils/echarts'

const props = defineProps<{ data: any }>()

const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`

const s = computed<any>(() => props.data?.summary || {})
const hoverCell = ref<any>(null)

/* ===== 热度指标开关：报名人次 / 课程数 / 拥挤度 ===== */
const METRICS = [
  { key: 'students', label: '报名人次' },
  { key: 'courses', label: '课程数' },
  { key: 'load', label: '拥挤度' },
] as const
const metric = ref<'students' | 'courses' | 'load'>('students')
const metricLabel = computed(() => METRICS.find(m => m.key === metric.value)?.label || '')
function metricVal(cell: any): number {
  if (metric.value === 'courses') return cell.courses || 0
  if (metric.value === 'load') {
    const cap = Number(cell.capacity) || 0
    return cap <= 0 ? 0 : Math.round((cell.students || 0) * 100 / cap)
  }
  return cell.students || 0
}
const maxMetric = computed(() => {
  const cells = (props.data?.cells || []).flat()
  return Math.max(1, ...cells.map((c: any) => metricVal(c)))
})

const maxCell = computed(() => {
  const cells = (props.data?.cells || []).flat()
  return Math.max(1, ...cells.map((c: any) => c.courses || 0))
})
const maxLoc = computed(() => Math.max(1, ...(props.data?.locations || []).map((l: any) => l.courses || 0)))
const maxBuilding = computed(() => Math.max(1, ...(props.data?.buildings || []).map((b: any) => b.courses || 0)))
const maxDay = computed(() => Math.max(1, ...(props.data?.dayLoad || []).map((d: any) => d.courses || 0)))

const kpis = computed(() => [
  { label: '已排课程', value: s.value.scheduledCourses || 0, hint: `第 ${s.value.round || 1} 轮 · 课程库共 ${s.value.totalCourses || 0} 门`, icon: 'book-open', tone: 'blue' },
  { label: '时段覆盖率', value: (s.value.coverage || 0) + '%', hint: `空闲 ${s.value.freeSlots || 0} 个时段格`, icon: 'grid', tone: 'green' },
  { label: '最热星期', value: s.value.peakDay || '—', hint: `${s.value.peakPeriod || ''} · ${s.value.peakCourses || 0} 门课`, icon: 'calendar', tone: 'amber' },
  { label: '最热教学楼', value: s.value.topBuilding || '—', hint: `${s.value.topBuildingCount || 0} 门课程`, icon: 'building', tone: 'purple' },
])

/* ===== 热力图（ECharts）===== */
const heatOption = computed(() => {
  const days = props.data?.days || []
  const periods = props.data?.periods || []
  const cells = props.data?.cells || []
  const values: any[] = []
  let maxV = 1
  cells.forEach((row: any[], p: number) => {
    row.forEach((cell: any, d: number) => {
      let v = 0
      if (metric.value === 'courses') v = cell.courses || 0
      else if (metric.value === 'load') v = cell.capacity > 0 ? Math.round((cell.students || 0) * 100 / cell.capacity) : 0
      else v = cell.students || 0
      if (v > maxV) maxV = v
      values.push([d, p, v])
    })
  })
  const isLoad = metric.value === 'load'
  return {
    grid: { left: 76, right: 16, top: 10, bottom: 44 },
    tooltip: {
      trigger: 'item', ...TOOLTIP,
      formatter: (p: any) => {
        const cell = cells[p.value[1]]?.[p.value[0]] || {}
        return cell.day + ' ' + (cell.period || '') + '<br>' + (cell.courses || 0) + ' 门课 · ' + (cell.students || 0) + ' 人次' + (isLoad ? '<br>拥挤度 ' + p.value[2] + '%' : '')
      },
    },
    xAxis: { type: 'category', data: days, splitArea: { show: true, areaStyle: { color: ['rgba(148,163,184,.04)', 'rgba(148,163,184,.08)'] } }, axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: '#94a3b8', fontSize: 11 } },
    yAxis: { type: 'category', data: [...periods].reverse(), splitArea: { show: true, areaStyle: { color: ['rgba(148,163,184,.04)', 'rgba(148,163,184,.08)'] } }, axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: '#94a3b8', fontSize: 11 } },
    visualMap: {
      min: 0, max: isLoad ? 100 : maxV,
      calculable: false, orient: 'horizontal', left: 'center', bottom: 2,
      textStyle: { color: '#94a3b8', fontSize: 10 },
      inRange: { color: isLoad ? ['#a8e6a1', '#f7d774', '#ef6b4a'] : ['#d5f2e8', '#4cc9a3', '#1fa67a'] },
      show: true, itemHeight: 90, itemWidth: 12,
    },
    series: [{
      name: metricLabel.value, type: 'heatmap',
      data: values,
      label: { show: true, color: 'rgba(15,23,42,.75)', fontSize: 10, formatter: (p: any) => isLoad && p.value[2] ? p.value[2] + '%' : p.value[2] || '' },
      emphasis: { itemStyle: { shadowBlur: 8, shadowColor: 'rgba(0,0,0,.3)' } },
      animationDuration: 700,
    }],
  }
})
</script>
