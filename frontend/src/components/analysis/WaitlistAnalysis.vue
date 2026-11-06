<template>
  <div class="an-analysis">
    <AnalysisKpi :items="kpis" />

    <div v-if="!s.joined" class="an-panel">
      <div class="an-empty">
        <div class="an-empty-icon" v-html="svgIcon('hourglass')" />
        <p>本轮还没有学生加入候补</p>
        <small>课程满员后，未被录取的学生可以进入候补队列</small>
      </div>
    </div>

    <template v-else>
      <div class="an-grid an-grid-main">
        <section class="an-panel an-panel-wide">
          <header class="an-panel-head">
            <div>
              <div class="an-panel-title"><span class="an-title-icon" v-html="svgIcon('hourglass')" />候补流转漏斗</div>
              <div class="an-panel-sub">从加入队列到确认成功，名额在每一次流转中重新分配</div>
            </div>
            <div class="an-panel-side">
              <div class="an-rate-pill"><b>{{ s.confirmRate }}%</b><span>总确认成功率</span></div>
            </div>
          </header>

          <div class="an-chart-scroll">
            <EChart :option="funnelOption" fill />
          </div>

          <div class="an-funnel-foot">
            <div class="an-funnel-metric"><span>递补通知率</span><b>{{ s.promotionRate }}%</b></div>
            <div class="an-funnel-metric"><span>递补后确认率</span><b>{{ s.confirmOfPromoted }}%</b></div>
            <div class="an-funnel-metric warn"><span>超时释放率</span><b>{{ s.timeoutRate }}%</b></div>
            <div class="an-funnel-metric"><span>仍在队列中</span><b>{{ s.active }} 人</b></div>
          </div>
        </section>

        <section class="an-panel">
          <header class="an-panel-head">
            <div>
              <div class="an-panel-title"><span class="an-title-icon" v-html="svgIcon('users')" />候补队列 Top {{ queues.length }}</div>
              <div class="an-panel-sub">按加入候补人数排序</div>
            </div>
          </header>

          <div class="an-queue">
            <div v-for="(q, i) in queues" :key="q.courseId" class="an-queue-row">
              <span class="an-queue-rank" :class="{ top: i < 3 }">{{ String(i + 1).padStart(2, '0') }}</span>
              <div class="an-queue-main">
                <div class="an-queue-top">
                  <b>{{ q.courseName }}</b>
                  <span>{{ q.joined }} 人候补</span>
                </div>
                <div class="an-queue-bar">
                  <i :style="{ width: Math.max(6, q.joined / maxQueue * 100) + '%' }" />
                </div>
                <div class="an-queue-meta">
                  <span class="ok">确认 {{ q.confirmed }}</span>
                  <span class="wait">等待 {{ q.waiting }}</span>
                  <span v-if="q.timeout" class="bad">超时 {{ q.timeout }}</span>
                  <span class="muted">容量 {{ q.capacity }} · 已录 {{ q.approved }}</span>
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
              <div class="an-panel-title"><span class="an-title-icon" v-html="svgIcon('clock')" />等待时长分布</div>
              <div class="an-panel-sub">从加入候补到收到递补通知的耗时</div>
            </div>
            <div class="an-panel-side an-side-text">
              <span>平均 <b>{{ fmt(s.avgWaitMinutes) }}</b> · 中位 <b>{{ fmt(s.medianWaitMinutes) }}</b></span>
            </div>
          </header>

          <div class="an-bars">
            <div v-for="b in waitBuckets" :key="b.label" class="an-bar-row">
              <span class="an-bar-label">{{ b.label }}</span>
              <div class="an-bar-track">
                <i class="an-bar-fill violet" :style="{ width: Math.max(b.count ? 4 : 0, b.count / maxWait * 100) + '%' }" />
              </div>
              <span class="an-bar-val">{{ b.count }}</span>
            </div>
          </div>
        </section>

        <section class="an-panel">
          <header class="an-panel-head">
            <div>
              <div class="an-panel-title"><span class="an-title-icon" v-html="svgIcon('bar-chart')" />状态构成与加入节奏</div>
              <div class="an-panel-sub">当前队列结构 & 最近 7 天新增</div>
            </div>
          </header>

          <div class="an-status-strip">
            <span
              v-for="st in statuses"
              :key="st.key"
              class="an-status-seg"
              :class="'st-' + st.key"
              :style="{ width: (st.count / s.joined * 100) + '%' }"
              :title="`${st.label} ${st.count} 人`"
            />
          </div>
          <div class="an-status-legend">
            <span v-for="st in statuses" :key="st.key" :class="'st-' + st.key">
              <i />{{ st.label }} <b>{{ st.count }}</b>
            </span>
          </div>

          <div class="an-mini-title">最近 7 天加入候补</div>
          <div class="an-spark">
            <div v-for="t in trend" :key="t.date" class="an-spark-col">
              <span class="an-spark-val">{{ t.count || '' }}</span>
              <i :style="{ height: Math.round(6 + (t.count || 0) / maxTrend * 46) + 'px' }" />
              <small>{{ t.date }}</small>
            </div>
          </div>
        </section>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import AnalysisKpi from './AnalysisKpi.vue'

const props = defineProps<{ data: any }>()

import EChart from '@/components/charts/EChart.vue'
import { TOOLTIP } from '@/utils/echarts'
const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`

const s = computed<any>(() => props.data?.summary || {})
const waitBuckets = computed<any[]>(() => props.data?.waitBuckets || [])
const statuses = computed<any[]>(() => props.data?.statuses || [])
const queues = computed<any[]>(() => props.data?.queues || [])
const trend = computed<any[]>(() => props.data?.trend || [])

const maxQueue = computed(() => Math.max(1, ...queues.value.map(q => q.joined || 0)))
const maxWait = computed(() => Math.max(1, ...waitBuckets.value.map(b => b.count || 0)))
const maxTrend = computed(() => Math.max(1, ...trend.value.map(t => t.count || 0)))

const kpis = computed(() => [
  { label: '累计加入候补', value: s.value.joined || 0, hint: `当前仍在队列 ${s.value.active || 0} 人`, icon: 'hourglass', tone: 'blue' },
  { label: '收到递补通知', value: (s.value.promotionRate || 0) + '%', hint: `${s.value.promoted || 0} 人获得递补机会`, icon: 'refresh', tone: 'purple' },
  { label: '递补后确认成功', value: (s.value.confirmOfPromoted || 0) + '%', hint: `${s.value.confirmed || 0} 人成功选上课`, icon: 'check-circle', tone: 'green' },
  { label: '平均等待时长', value: fmt(s.value.avgWaitMinutes), hint: `中位 ${fmt(s.value.medianWaitMinutes)}`, icon: 'clock', tone: 'amber' },
])

function fmt(mins: number) {
  const m = Number(mins) || 0
  if (m <= 0) return '0 分钟'
  if (m < 60) return Math.round(m) + ' 分钟'
  if (m < 1440) return (m / 60).toFixed(1) + ' 小时'
  return (m / 1440).toFixed(1) + ' 天'
}

const funnelData = computed(() => props.data?.funnel || [])
const funnelOption = computed(() => ({
  tooltip: { trigger: 'item', formatter: '{b}：{c} 人', ...TOOLTIP },
  series: [{
    type: 'funnel',
    left: '12%', width: '76%', top: 12, bottom: 12,
    sort: 'none', gap: 6,
    minSize: '24%',
    label: { position: 'inside', formatter: '{b}', color: '#fff', fontSize: 12, fontWeight: 600 },
    data: funnelData.value.map((f: any, i: number) => ({
      name: f.label + ' ' + f.count,
      value: Math.max(1, f.count),
      itemStyle: [{ color: '#1fa67a' }, { color: '#0891b2' }, { color: '#059669' }][i] || '#94a3b8',
    })),
    emphasis: { label: { fontSize: 14 } },
    animationDuration: 900,
  }],
}))
</script>

