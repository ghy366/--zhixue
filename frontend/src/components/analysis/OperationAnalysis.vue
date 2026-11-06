<template>
  <div class="an-analysis">
    <AnalysisKpi :items="kpis" />

    <!-- #31 供需缺口 -->
    <section class="an-panel">
      <header class="an-panel-head">
        <div>
          <div class="an-panel-title"><span class="an-title-icon" v-html="svgIcon('cart')" />供需缺口 · 建议加开班级</div>
          <div class="an-panel-sub">当前轮需求超过名额的课程，缺口越大越优先加开</div>
        </div>
        <div class="an-panel-side">
          <div class="an-rate-pill"><b>{{ s.supplyShortage || 0 }}</b><span>门课程供不应求</span></div>
        </div>
      </header>
      <div v-if="supplyGap.length" class="an-queue">
        <div v-for="(g, i) in supplyGap" :key="g.courseId" class="an-queue-row">
          <span class="an-queue-rank" :class="{ top: i < 3 }">{{ String(i + 1).padStart(2, '0') }}</span>
          <div class="an-queue-main">
            <div class="an-queue-top">
              <b>{{ g.courseName }}</b>
              <span class="op-time">{{ g.time || '时间未定' }}</span>
            </div>
            <div class="an-queue-bar">
              <i class="op-bar-hot" :style="{ width: Math.min(100, g.ratio * 100) + '%' }" />
            </div>
            <div class="an-queue-meta">
              <span class="muted">报名 {{ g.applied }} · 容量 {{ g.capacity }}</span>
              <span v-if="g.deficit > 0" class="bad">缺口 {{ g.deficit }} 人</span>
              <span v-else class="ok">容量充足</span>
            </div>
          </div>
          <span class="op-suggest">{{ g.suggest }}</span>
        </div>
      </div>
      <div v-else class="an-empty" style="padding: 20px 0"><small>当前轮没有供不应求的课程</small></div>
    </section>

    <div class="an-grid an-grid-2">
      <!-- #27 余量热力 -->
      <section class="an-panel">
        <header class="an-panel-head">
          <div>
            <div class="an-panel-title"><span class="an-title-icon" v-html="svgIcon('grid')" />名额余量热力</div>
            <div class="an-panel-sub">星期 × 时段 · 颜色越红名额越紧张，最紧 {{ s.worstCell }}</div>
          </div>
        </header>
        <div class="op-heat">
          <div class="op-heat-grid">
            <span class="op-heat-corner">时段</span>
            <span v-for="d in 7" :key="'h' + d" class="op-heat-day">{{ heat[0]?.[d - 1]?.day }}</span>
            <template v-for="(row, p) in heat" :key="'r' + p">
              <span class="op-heat-period">{{ row[0]?.period }}</span>
              <div
                v-for="(cell, d) in row" :key="p + '-' + d"
                class="op-heat-cell"
                :style="heatStyle(cell)"
                :title="`${cell.day} ${cell.period} · 满员率 ${cell.tension}% · 剩余 ${cell.remaining}/${cell.capacity}${cell.names.length ? ' · ' + cell.names.join('、') + (cell.more ? ' 等' : '') : ''}`"
              >
                <b v-if="cell.capacity">{{ cell.tension }}%</b>
                <small v-if="cell.capacity">余 {{ cell.remaining }}</small>
              </div>
            </template>
          </div>
          <div class="op-heat-legend">
            <span>空闲</span><i v-for="n in 6" :key="n" :style="{ background: heatColor((n - 1) / 5 * 100) }" /><span>紧张</span>
          </div>
        </div>
      </section>

      <!-- #34 退课统计 -->
      <section class="an-panel">
        <header class="an-panel-head">
          <div>
            <div class="an-panel-title"><span class="an-title-icon" v-html="svgIcon('refresh')" />退课统计</div>
            <div class="an-panel-sub">全轮累计退课率 {{ s.dropoutRate }}%（{{ s.totalDropped }} 次）· 报名 ≥3 人的课程参与排行</div>
          </div>
        </header>
        <div v-if="dropoutRank.length" class="an-bars">
          <div v-for="d in dropoutRank" :key="d.courseId" class="an-bar-row">
            <span class="an-bar-label" :title="d.courseName">{{ d.courseName }}</span>
            <div class="an-bar-track">
              <i class="an-bar-fill rose" :style="{ width: Math.max(5, d.rate / maxDrop * 100) + '%' }" />
            </div>
            <span class="an-bar-val">{{ d.rate }}%</span>
          </div>
        </div>
        <div v-else class="an-empty" style="padding: 20px 0"><small>暂无退课数据</small></div>
        <div v-if="roundDrops.length" class="an-mini-title">各轮退课节奏</div>
        <div v-if="roundDrops.length" class="an-spark">
          <div v-for="t in roundDrops" :key="t.round" class="an-spark-col">
            <span class="an-spark-val">{{ t.dropped || '' }}</span>
            <i style="background: linear-gradient(180deg,#fb7185,#f43f5e)" :style="{ height: Math.round(6 + (t.dropped || 0) / maxRoundDrop * 46) + 'px' }" />
            <small>第{{ t.round }}轮</small>
          </div>
        </div>
      </section>
    </div>

    <!-- #35 毕业预警 -->
    <section class="an-panel">
      <header class="an-panel-head">
        <div>
          <div class="an-panel-title"><span class="an-title-icon" v-html="svgIcon('graduation-cap')" />毕业预警</div>
          <div class="an-panel-sub">学分缺口 ≥10 分高危 · ≥5 分中危 · 未过必修课单独标注</div>
        </div>
        <div class="an-panel-side an-side-text">
          <span class="ok">安全 {{ s.riskSafe || 0 }}</span>
          <span class="wait">关注 {{ s.riskLow || 0 }}</span>
          <span class="warn2">中危 {{ s.riskMid || 0 }}</span>
          <span class="bad">高危 {{ s.riskHigh || 0 }}</span>
        </div>
      </header>

      <div class="an-grid an-grid-2">
        <div>
          <div class="an-mini-title">按专业分布</div>
          <div class="data-table op-table">
            <table>
              <thead><tr><th>专业</th><th>学生数</th><th>高危</th><th>平均缺口</th></tr></thead>
              <tbody>
                <tr v-for="m in byMajor" :key="m.major">
                  <td><b>{{ m.major }}</b></td>
                  <td>{{ m.students }}</td>
                  <td><span v-if="m.high" class="op-badge high">{{ m.high }}</span><span v-else>0</span></td>
                  <td>{{ m.avgGap }} 分</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
        <div>
          <div class="an-mini-title">缺口最大学生 Top {{ riskStudents.length }}</div>
          <div class="data-table op-table">
            <table>
              <thead><tr><th>学生</th><th>专业</th><th>缺口</th><th>主要缺</th></tr></thead>
              <tbody>
                <tr v-for="u in riskStudents" :key="u.studentId">
                  <td><b>{{ u.name }}</b> <small class="op-no">{{ u.no }}</small></td>
                  <td>{{ u.major }}</td>
                  <td><span class="op-badge" :class="u.level" :title="u.hardGap > u.gap ? `硬缺口 ${u.hardGap} 分，在修预计可抵扣 ${u.hardGap - u.gap} 分` : ''">{{ u.gap }} 分</span></td>
                  <td>
                    <small>{{ gapText(u.gaps) }}</small>
                    <small v-if="u.missingRequired.length" class="bad op-miss">未过必修 {{ u.missingRequired.length }} 门</small>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import AnalysisKpi from './AnalysisKpi.vue'
const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`

const props = defineProps<{ data: any }>()

const s = computed<any>(() => props.data?.summary || {})
const supplyGap = computed<any[]>(() => props.data?.supplyGap || [])
const heat = computed<any[][]>(() => props.data?.heat || [])
const dropoutRank = computed<any[]>(() => props.data?.dropoutRank || [])
const roundDrops = computed<any[]>(() => props.data?.roundDrops || [])
const byMajor = computed<any[]>(() => props.data?.byMajor || [])
const riskStudents = computed<any[]>(() => props.data?.riskStudents || [])

const maxDrop = computed(() => Math.max(1, ...dropoutRank.value.map(d => d.rate || 0)))
const maxRoundDrop = computed(() => Math.max(1, ...roundDrops.value.map(d => d.dropped || 0)))

const kpis = computed(() => [
  { label: '供不应求课程', value: s.value.supplyShortage || 0, hint: '按缺口排序，前 3 门建议优先加开', icon: 'cart', tone: 'amber' },
  { label: '最紧张时段', value: s.value.worstCell || '—', hint: `满员率 ${s.value.worstTension || 0}%`, icon: 'grid', tone: 'red' },
  { label: '累计退课率', value: (s.value.dropoutRate || 0) + '%', hint: `共 ${s.value.totalDropped || 0} 次退课`, icon: 'refresh', tone: 'purple' },
  { label: '毕业高危学生', value: s.value.riskHigh || 0, hint: `中危 ${s.value.riskMid || 0} · 关注 ${s.value.riskLow || 0}`, icon: 'graduation-cap', tone: (s.value.riskHigh || 0) > 0 ? 'red' : 'green' },
])

function heatColor(t: number) {
  // 0=绿(空闲) → 60=黄 → 100=红(紧张)
  if (t <= 0) return 'rgba(148,163,184,.10)'
  if (t < 60) {
    const p = t / 60
    return `color-mix(in srgb, #10b981 ${Math.round((1 - p) * 100)}%, #fbbf24)`
  }
  const p = (t - 60) / 40
  return `color-mix(in srgb, #fbbf24 ${Math.round((1 - p) * 100)}%, #ef4444)`
}
function heatStyle(cell: any) {
  if (!cell.capacity) return { background: 'rgba(148,163,184,.06)', opacity: .55 }
  return { background: heatColor(cell.tension) }
}
function gapText(gaps: any[]) {
  const hit = (gaps || []).filter((g: any) => g.gap > 0).map((g: any) => g.key + '缺' + g.gap)
  if (hit.length) return hit.join(' / ')
  const inProg = (gaps || []).some((g: any) => (g.inProgress || 0) > 0)
  return inProg ? '在修可覆盖' : '已修满'
}
</script>

<style scoped>
.op-time { opacity: .55; font-size: 12px; }
.op-suggest { flex: none; align-self: center; font-size: 12px; color: #fbbf24; max-width: 190px; text-align: right; }
.op-bar-hot { background: linear-gradient(90deg, #f59e0b, #ef4444); }
.op-heat { display: flex; flex-direction: column; gap: 10px; }
.op-heat-grid {
  display: grid;
  grid-template-columns: 44px repeat(7, 1fr);
  gap: 4px;
}
.op-heat-corner { font-size: 12px; opacity: .5; display: grid; place-items: center; }
.op-heat-day { font-size: 12px; opacity: .65; text-align: center; padding: 2px 0; }
.op-heat-period { font-size: 12px; opacity: .65; display: grid; place-items: center; }
.op-heat-cell {
  min-height: 44px; border-radius: 8px; padding: 4px;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  cursor: default; transition: transform .15s ease;
}
.op-heat-cell:hover { transform: scale(1.06); z-index: 1; }
.op-heat-cell b { font-size: 12px; }
.op-heat-cell small { font-size: 12px; opacity: .8; }
.op-heat-legend { display: flex; align-items: center; gap: 6px; justify-content: center; font-size: 12px; opacity: .7; }
.op-heat-legend i { width: 22px; height: 10px; border-radius: 3px; display: inline-block; }
.op-table { max-height: 260px; overflow: auto; }
.op-no { opacity: .5; margin-left: 4px; }
.op-miss { display: block; }
.op-badge { display: inline-block; padding: 1px 8px; border-radius: 999px; font-size: 12px; font-weight: 700; }
.op-badge.high { background: rgba(239, 68, 68, .16); color: #f87171; }
.op-badge.mid { background: rgba(245, 158, 11, .16); color: #fbbf24; }
.op-badge.low { background: rgba(6, 182, 212, .14); color: #67e8f9; }
.op-badge.safe { background: rgba(16, 185, 129, .14); color: #34d399; }
.warn2 { color: #fbbf24; }
</style>
