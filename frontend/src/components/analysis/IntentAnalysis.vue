<template>
  <div class="an-analysis">
    <AnalysisKpi :items="kpis" />

    <div v-if="!s.intents" class="an-panel">
      <div class="an-empty">
        <div class="an-empty-icon" v-html="svgIcon('bookmark')" />
        <p>本轮还没有在册选课意向</p>
        <small>学生把课程加入意向单并预填积分后，这里会展示意向到录取的完整流转</small>
      </div>
    </div>

    <template v-else>
      <div class="an-grid an-grid-main">
        <section class="an-panel an-panel-wide">
          <header class="an-panel-head">
            <div>
              <div class="an-panel-title"><span class="an-title-icon" v-html="svgIcon('bookmark')" />意向流转图</div>
              <div class="an-panel-sub">在册意向 → 是否报名该课 → 最终结果，条带宽度按人次比例</div>
            </div>
            <div class="an-panel-side">
              <div class="an-rate-pill"><b>{{ s.convertRate }}%</b><span>意向兑现率</span></div>
            </div>
          </header>

          <div v-if="thinSample" class="an-flow-compact">
            <div class="afc-step"><b>{{ s.intents || 0 }}</b><span>在册意向</span></div>
            <div class="afc-arrow">→</div>
            <div class="afc-step"><b>{{ s.enrolled || 0 }}</b><span>已报名兑现</span></div>
            <div class="afc-arrow">→</div>
            <div class="afc-step"><b>{{ s.approved || 0 }}</b><span>意向课录取</span></div>
            <div class="afc-arrow">→</div>
            <div class="afc-step"><b>{{ s.unrolled || 0 }}</b><span>尚未报名</span></div>
            <p class="afc-note">样本较少（少于 5 条），暂以摘要呈现；数据积累后自动展示完整流转图</p>
          </div>
          <div v-else class="an-chart-scroll">
            <EChart :option="sankeyOption" fill />
          </div>

          <div class="an-legend">
            <span><i class="an-swatch" style="background:#22d3ee" />已报名</span>
            <span><i class="an-swatch" style="background:#f59e0b" />尚未报名</span>
            <span class="an-muted">意向转报名后即从意向单移除，统计仅覆盖当前在册意向</span>
          </div>
        </section>

        <section class="an-panel an-panel-side-panel">
          <header class="an-panel-head an-panel-head-stack">
            <div>
              <div class="an-panel-title"><span class="an-title-icon" v-html="svgIcon('coins')" />投分偏差</div>
              <div class="an-panel-sub">实际投分 − 预填意向分（仅统计已报名且有预填分的意向）</div>
            </div>
          </header>

          <div class="an-dist-stats">
            <div class="an-dist-stat ok"><b>{{ s.exactRate }}%</b><span>投分与意向一致</span></div>
            <div class="an-dist-stat"><b>{{ s.avgAbsDelta }}</b><span>平均偏差（分）</span></div>
            <div class="an-dist-stat"><b>{{ s.avgDesired }}</b><span>平均预填意向分</span></div>
            <div class="an-dist-stat cut"><b>{{ deltaTotal }}</b><span>可对比样本</span></div>
          </div>

          <div v-if="!deltaTotal" class="an-empty">
            <div class="an-empty-icon" v-html="svgIcon('coins')" />
            <p>暂无可对比的投分样本</p>
            <small>需要意向带预填分、且学生本轮已报名该课程</small>
          </div>

          <div v-else class="an-bars an-delta-bars">
            <div v-for="b in deltaBuckets" :key="b.label" class="an-bar-row">
              <span class="an-bar-label">{{ b.label }}</span>
              <div class="an-bar-track">
                <i class="an-bar-fill" :class="bucketClass(b.label)" :style="{ width: Math.max(b.count ? 4 : 0, b.count / deltaMax * 100) + '%' }" />
              </div>
              <span class="an-bar-val">{{ b.count }}</span>
            </div>
          </div>

          <div class="an-insight-row">
            <div class="an-insight">
              <span>整体倾向</span>
              <b>{{ tendency }}</b>
            </div>
            <div class="an-insight">
              <span>完全一致</span>
              <b>{{ s.exactCount }} 人次</b>
            </div>
          </div>
        </section>
      </div>

      <div class="an-grid an-grid-2">
        <section class="an-panel">
          <header class="an-panel-head">
            <div>
              <div class="an-panel-title"><span class="an-title-icon" v-html="svgIcon('bar-chart')" />意向课程排行</div>
              <div class="an-panel-sub">按在册意向数排序 · 条带为意向数</div>
            </div>
            <div class="an-panel-side"><span class="an-mini-title">取前 {{ courseRows.length }} 门</span></div>
          </header>

          <div v-if="courseRows.length">
            <div v-for="(c, i) in courseRows" :key="c.courseId" class="rank-row">
              <div class="rank-name">
                <span class="rank-idx" :class="{ top: i < 3 }">{{ i + 1 }}</span>
                {{ c.courseName }}
                <span class="rank-sub">{{ c.teacherName || '' }} · 兑现 {{ c.enrolled }} · 录取 {{ c.approved }}</span>
              </div>
              <div class="rank-bar-wrap"><div class="rank-bar" :style="{ width: Math.round(c.intents * 100 / cMax) + '%' }" /></div>
              <div class="rank-val">{{ c.intents }} 意向</div>
            </div>
          </div>
          <div v-else class="an-empty">
            <div class="an-empty-icon" v-html="svgIcon('bar-chart')" />
            <p>暂无课程意向</p>
          </div>
        </section>

        <section class="an-panel">
          <header class="an-panel-head">
            <div>
              <div class="an-panel-title"><span class="an-title-icon" v-html="svgIcon('clock')" />意向收集节奏</div>
              <div class="an-panel-sub">最近 7 天新增意向数</div>
            </div>
            <div class="an-panel-side">
              <div class="an-rate-pill"><b>{{ trendTotal }}</b><span>7 天合计</span></div>
            </div>
          </header>

          <div class="an-spark">
            <div v-for="t in trend" :key="t.date" class="an-spark-col">
              <span class="an-spark-val">{{ t.count || '' }}</span>
              <i :style="{ height: (t.count ? Math.max(10, Math.round((t.count || 0) / trendMax * 80)) : 3) + '%' }" />
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
import EChart from '@/components/charts/EChart.vue'
import { TOOLTIP } from '@/utils/echarts'

const props = defineProps<{ data: any }>()

const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`

const s = computed<any>(() => props.data?.summary || {})
/* 样本稀薄（<5 条意向）时桑基图会拉伸成无意义巨条，降级为紧凑摘要 */
const thinSample = computed(() => (s.value.intents || 0) < 5)
const flow = computed<any[]>(() => props.data?.flow || [])
const deltaBuckets = computed<any[]>(() => props.data?.deltaBuckets || [])
const courseRows = computed<any[]>(() => props.data?.courses || [])
const trend = computed<any[]>(() => props.data?.trend || [])

const byKey = (k: string) => flow.value.find((f: any) => f.key === k) || { count: 0, rate: 0 }

const kpis = computed(() => [
  { label: '在册意向', value: s.value.intents || 0, hint: `${s.value.students || 0} 名学生 · ${s.value.courses || 0} 门课程`, icon: 'bookmark', tone: 'blue' },
  { label: '已报名兑现', value: s.value.enrolled || 0, hint: `兑现率 ${s.value.convertRate || 0}%`, icon: 'refresh', tone: 'purple' },
  { label: '意向课录取', value: s.value.approved || 0, hint: `候补 ${s.value.waitlist || 0} · 落选 ${s.value.rejected || 0}`, icon: 'check-circle', tone: 'green' },
  { label: '尚未报名', value: s.value.unrolled || 0, hint: '意向仍在单中，等待提交或转化', icon: 'hourglass', tone: 'amber' },
])

/* ===== 桑基图（ECharts）===== */
const sankeyOption = computed(() => {
  const g = (k: string) => byKey(k).count || 0
  const nodes = [
    { name: '在册意向', itemStyle: { color: '#2bb187' } },
    { name: '已报名', itemStyle: { color: '#22d3ee' } },
    { name: '尚未报名', itemStyle: { color: '#f59e0b' } },
    { name: '成功录取', itemStyle: { color: '#34d399' } },
    { name: '进入候补', itemStyle: { color: '#38bdf8' } },
    { name: '落选', itemStyle: { color: '#fb7185' } },
    { name: '待结算', itemStyle: { color: '#94a3b8' } },
    { name: '又退课', itemStyle: { color: '#64748b' } },
  ]
  const links = [
    { source: '在册意向', target: '已报名', value: g('enrolled') },
    { source: '在册意向', target: '尚未报名', value: g('unrolled') },
    { source: '已报名', target: '成功录取', value: g('approved') },
    { source: '已报名', target: '进入候补', value: g('waitlist') },
    { source: '已报名', target: '落选', value: g('rejected') },
    { source: '已报名', target: '待结算', value: g('pending') },
    { source: '已报名', target: '又退课', value: g('dropped') },
  ].filter(l => l.value > 0)
  return {
    tooltip: { trigger: 'item', ...TOOLTIP },
    series: [{
      type: 'sankey',
      left: 24, right: 120, top: 16, bottom: 16,
      nodeWidth: 14, nodeGap: 16,
      data: nodes.filter(n => links.some(l => l.source === n.name || l.target === n.name)),
      links,
      emphasis: { focus: 'adjacency' },
      label: { color: '#94a3b8', fontSize: 11 },
      lineStyle: { color: 'gradient', opacity: .35, curveness: .5 },
      itemStyle: { borderRadius: 4 },
      animationDuration: 900,
    }],
  }
})

/* ===== 偏差与排行 ===== */
const deltaMax = computed(() => Math.max(1, ...deltaBuckets.value.map(b => b.count || 0)))
const deltaTotal = computed(() => deltaBuckets.value.reduce((a, b) => a + (b.count || 0), 0))
const bucketClass = (label: string) =>
  label.includes('调低') ? 'violet' : label.includes('一致') ? 'cyan' : 'rose'
const tendency = computed(() => {
  const low = deltaBuckets.value.slice(0, 2).reduce((a, b) => a + b.count, 0)
  const high = deltaBuckets.value.slice(3).reduce((a, b) => a + b.count, 0)
  if (!deltaTotal.value) return '—'
  if (low > high * 1.2) return '普遍调低投分'
  if (high > low * 1.2) return '普遍调高投分'
  return '基本持平'
})

const cMax = computed(() => Math.max(1, ...courseRows.value.map(c => c.intents || 0)))
const trendMax = computed(() => Math.max(1, ...trend.value.map(t => t.count || 0)))
const trendTotal = computed(() => trend.value.reduce((a, b) => a + (b.count || 0), 0))
</script>
