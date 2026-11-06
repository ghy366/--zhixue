<template>
  <div class="an-analysis">
    <AnalysisKpi :items="kpis" />

    <div v-if="!s.total" class="an-panel">
      <div class="an-empty">
        <div class="an-empty-icon" v-html="svgIcon('star')" />
        <p>还没有课程评价数据</p>
        <small>学生完成课程评价后，这里会展示星级分布、评分与热度的关系以及评论热词</small>
      </div>
    </div>

    <template v-else>
      <div class="an-grid an-grid-main">
        <section class="an-panel an-panel-wide">
          <header class="an-panel-head">
            <div>
              <div class="an-panel-title"><span class="an-title-icon" v-html="svgIcon('star')" />评分 vs 报名热度</div>
              <div class="an-panel-sub">横轴为课程平均评分，纵轴为本轮报名人次 · 验证「高分课是否真的抢手」</div>
            </div>
            <div class="an-panel-side an-box-focus">
              <template v-if="s.correlation != null">
                <b>r = {{ s.correlation }}</b>
                <span>{{ corrText }}</span>
              </template>
              <template v-else>样本不足，暂无法计算相关性</template>
            </div>
          </header>

          <div class="an-chart-scroll">
            <EChart :option="scOption" fill />
          </div>

          <div class="an-legend">
            <span><i class="rv-sw best" />4.5 星+</span>
            <span><i class="rv-sw good" />4-4.5 星</span>
            <span><i class="rv-sw mid" />3-4 星</span>
            <span><i class="rv-sw low" />3 星以下</span>
            <span class="an-muted">圆点大小 = 评价人数</span>
          </div>

          <div class="an-insight-row">
            <div class="an-insight">
              <span>评分与热度</span>
              <b>{{ corrText }}</b>
            </div>
            <div class="an-insight">
              <span>最高评分课</span>
              <b>{{ topRated?.courseName || '—' }}（{{ topRated?.avg ?? '—' }} 分）</b>
            </div>
            <div class="an-insight">
              <span>最抢手课</span>
              <b>{{ hotRated?.courseName || '—' }}（{{ hotRated?.applied ?? 0 }} 人）</b>
            </div>
          </div>
        </section>

        <section class="an-panel an-panel-side-panel">
          <header class="an-panel-head an-panel-head-stack">
            <div>
              <div class="an-panel-title"><span class="an-title-icon" v-html="svgIcon('bar-chart')" />星级分布</div>
              <div class="an-panel-sub">共 {{ s.total }} 条评价</div>
            </div>
          </header>

          <div class="an-bars">
            <div v-for="d in distribution" :key="d.star" class="rv-star-row">
              <span class="rv-star-label">{{ '★'.repeat(d.star) }}<i>{{ '☆'.repeat(5 - d.star) }}</i></span>
              <div class="an-bar-track">
                <i class="an-bar-fill" :class="starClass(d.star)" :style="{ width: Math.max(d.count ? 4 : 0, d.rate) + '%' }" />
              </div>
              <span class="an-bar-val">{{ d.count }}</span>
            </div>
          </div>

          <div class="an-mini-title rv-dims-title">三个维度均分</div>
          <div class="rv-dims">
            <div v-for="d in dims" :key="d.key" class="rv-dim">
              <b>{{ d.avg }}</b>
              <span>{{ d.label }}</span>
              <div class="rv-dim-track"><i :style="{ width: (d.avg / 5 * 100) + '%' }" /></div>
            </div>
          </div>

          <!-- 四维雷达：综合/教学/内容/作业 -->
          <div class="r4-wrap" v-if="s.total">
            <div class="r4-title">维度雷达 <small>满分 5 星</small></div>
            <EChart :option="r4Option" height="190px" />
          </div>
        </section>
      </div>

      <div class="an-grid an-grid-2">
        <section class="an-panel">
          <header class="an-panel-head">
            <div>
              <div class="an-panel-title"><span class="an-title-icon" v-html="svgIcon('message-square')" />评论热词</div>
              <div class="an-panel-sub">从 {{ s.total }} 条评论文本中提取 · 字号越大出现越多</div>
            </div>
          </header>

          <div v-if="cloud.length" class="rv-cloud">
            <span
              v-for="(w, i) in cloud"
              :key="w.word"
              class="rv-word"
              :class="'rv-wc' + (i % 5)"
              :style="{ fontSize: wordSize(w.count) + 'px' }"
              :title="`出现 ${w.count} 次`"
            >{{ w.word }}</span>
          </div>
          <div v-else class="an-empty">
            <div class="an-empty-icon" v-html="svgIcon('message-square')" />
            <p>评论文本还太少，暂时没有热词</p>
          </div>
        </section>

        <section class="an-panel">
          <header class="an-panel-head">
            <div>
              <div class="an-panel-title"><span class="an-title-icon" v-html="svgIcon('file-text')" />最新评价</div>
              <div class="an-panel-sub">匿名评价不展示评价人</div>
            </div>
          </header>

          <div v-if="recent.length" class="rv-recent">
            <div v-for="(r, i) in recent" :key="i" class="rv-review">
              <div class="rv-review-head">
                <b>{{ r.courseName }}</b>
                <span class="rv-review-stars">{{ '★'.repeat(r.rating || 0) }}<i>{{ '☆'.repeat(5 - (r.rating || 0)) }}</i></span>
                <small>{{ fmtDate(r.createdAt) }}</small>
              </div>
              <p>{{ r.content || '（无文字评价）' }}</p>
            </div>
          </div>
          <div v-else class="an-empty">
            <div class="an-empty-icon" v-html="svgIcon('file-text')" />
            <p>暂无评价</p>
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
import { PALETTE, TOOLTIP, valAxis } from '@/utils/echarts'

const props = defineProps<{ data: any }>()

const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`

const s = computed<any>(() => props.data?.summary || {})
const distribution = computed<any[]>(() => props.data?.distribution || [])
const dims = computed<any[]>(() => props.data?.dims || [])
const scatter = computed<any[]>(() => props.data?.scatter || [])
const cloud = computed<any[]>(() => props.data?.cloud || [])
const recent = computed<any[]>(() => props.data?.recent || [])

const kpis = computed(() => [
  { label: '评价总数', value: s.value.total || 0, hint: `${s.value.students || 0} 名学生参与`, icon: 'file-text', tone: 'blue' },
  { label: '平均评分', value: s.value.avgRating || 0, hint: '满分 5 星', icon: 'star', tone: 'amber' },
  { label: '好评率（4 星+）', value: (s.value.goodRate || 0) + '%', hint: `覆盖 ${s.value.ratedCourses || 0} 门课程`, icon: 'check-circle', tone: 'green' },
  { label: '教学水平均分', value: s.value.avgTeaching || 0, hint: `内容 ${s.value.avgContent ?? '—'} · 作业 ${s.value.avgHomework ?? '—'}`, icon: 'award', tone: 'purple' },
])

const corrText = computed(() => {
  const r = s.value.correlation
  if (r == null) return '样本不足'
  if (r >= 0.6) return '强正相关 · 高分课明显更抢手'
  if (r >= 0.3) return '中度正相关 · 高分课略更抢手'
  if (r > -0.3) return '基本无关 · 评分不影响热度'
  return '负相关 · 高分课反而遇冷'
})

const topRated = computed(() => [...scatter.value].sort((a, b) => b.avg - a.avg)[0] || null)
const hotRated = computed(() => scatter.value[0] || null)

/* ===== 四维雷达：综合/教学/内容/作业（满分 5） ===== */
const R4C = 90
const R4Y = 76
const R4R = 52
const R4_KEYS = ['综合', '教学', '内容', '作业'] as const
const R4_AXES: Array<[number, number]> = [[0, -1], [0.866, -0.5], [0.866, 0.5], [0, 1]]
function r4Val(i: number): number {
  return [Number(s.value.avgRating) || 0, Number(s.value.avgTeaching) || 0,
    Number(s.value.avgContent) || 0, Number(s.value.avgHomework) || 0][i]
}
function r4Pt(f: number, i: number): [number, number] {
  return [R4C + R4_AXES[i][0] * R4R * f, R4Y + R4_AXES[i][1] * R4R * f]
}
function r4Grid(f: number): string {
  return [0, 1, 2, 3].map(i => r4Pt(f, i).map(n => n.toFixed(1)).join(',')).join(' ')
}
function r4Poly(): string {
  return [0, 1, 2, 3]
    .map(i => r4Pt(Math.max(0, Math.min(5, r4Val(i))) / 5, i).map(n => n.toFixed(1)).join(','))
    .join(' ')
}

/* ===== 评分 vs 报名热度散点（ECharts）===== */
const scOption = computed(() => ({
  grid: { left: 46, right: 26, top: 26, bottom: 40 },
  tooltip: { trigger: 'item', ...TOOLTIP, formatter: (p: any) => p.data.name + '<br>均分 ' + p.value[0] + '（' + p.data.count + ' 人评价）<br>本轮报名 ' + p.value[1] + ' 人' },
  xAxis: { ...valAxis('平均评分'), min: 1, max: 5, name: '评分', nameTextStyle: { color: '#94a3b8' } },
  yAxis: { ...valAxis('报名人次') },
  series: [{
    name: '课程', type: 'scatter',
    data: scatter.value.map((c: any) => ({
      value: [c.avg, c.applied], name: c.courseName, count: c.count,
      symbolSize: 9 + Math.min(16, Math.sqrt(c.count || 1) * 2.6),
      itemStyle: { color: c.avg >= 4.5 ? '#f59e0b' : c.avg >= 4 ? '#34d399' : c.avg >= 3 ? '#38bdf8' : '#fb7185', borderColor: 'rgba(255,255,255,.7)', borderWidth: 1 },
    })),
    label: {
      show: true, position: 'top', color: '#94a3b8', fontSize: 10,
      formatter: (p: any) => p.data.count >= 2 ? p.data.name.slice(0, 6) : '',
    },
    animationDuration: 900,
  }],
}))

/* ===== 四维雷达（ECharts）===== */
const r4Option = computed(() => ({
  tooltip: { trigger: 'item', ...TOOLTIP },
  radar: {
    indicator: [
      { name: '综合', max: 5 }, { name: '教学', max: 5 }, { name: '内容', max: 5 }, { name: '作业', max: 5 },
    ],
    radius: '62%', center: ['50%', '52%'],
    axisName: { color: '#94a3b8', fontSize: 11 },
    splitLine: { lineStyle: { color: 'rgba(148,163,184,.25)' } },
    splitArea: { show: false },
    axisLine: { lineStyle: { color: 'rgba(148,163,184,.3)' } },
  },
  series: [{
    type: 'radar',
    data: [{
      value: [Number(s.value.avgRating) || 0, Number(s.value.avgTeaching) || 0, Number(s.value.avgContent) || 0, Number(s.value.avgHomework) || 0],
      name: '维度均分',
      areaStyle: { color: 'rgba(43,177,135,.3)' },
      lineStyle: { color: '#2bb187', width: 2 },
      itemStyle: { color: '#2bb187' },
      symbolSize: 5,
    }],
    animationDuration: 900,
  }],
}))

const starClass = (star: number) => (star >= 4 ? 'violet' : star === 3 ? 'cyan' : 'rose')

/* 散点图布局 */
const W = 820
const H = 300
const PAD_L = 38
const PAD_R = 20
const PAD_T = 20
const PAD_B = 36
const maxApplied = computed(() => Math.max(5, ...scatter.value.map(p => p.applied || 0)))
const yTicks = computed(() => {
  const m = maxApplied.value
  return [0, Math.round(m / 2), m]
})
const sx = (v: number) => PAD_L + ((Math.max(1, Math.min(5, v)) - 1) / 4) * (W - PAD_L - PAD_R)
const py = (v: number) => H - PAD_B - (Math.max(0, v) / maxApplied.value) * (H - PAD_T - PAD_B)
const dotR = (count: number) => 4 + Math.min(6, Math.sqrt(count || 1) * 1.4)
const shortName = (n: string) => (n || '').length > 8 ? n.slice(0, 7) + '…' : n

/* 词云字号：按词频相对缩放 */
const maxWord = computed(() => Math.max(1, ...cloud.value.map(w => w.count || 0)))
const minWord = computed(() => Math.min(...(cloud.value.length ? cloud.value.map(w => w.count || 0) : [1])))
const wordSize = (c: number) => {
  if (maxWord.value <= minWord.value) return 15
  return 12 + ((c - minWord.value) / (maxWord.value - minWord.value)) * 14
}

const fmtDate = (t: string) => (t ? String(t).replace('T', ' ').substring(5, 16) : '')
</script>

<style scoped>
.rv-dot circle { stroke: rgba(255, 255, 255, .6); stroke-width: 1; opacity: .85; animation: rvPop .45s cubic-bezier(0.22, 1, 0.36, 1) backwards; }
@keyframes rvPop { from { transform: scale(0); } }
.rv-dot.best circle { fill: #f59e0b; }
.rv-dot.good circle { fill: #34d399; }
.rv-dot.mid circle { fill: #60a5fa; }
.rv-dot.low circle { fill: #fb7185; }
.rv-dot circle:hover { opacity: 1; }
.rv-dot-label { fill: var(--text-light, #94a3b8); font-size: 12px; font-weight: 600; }
.rv-sw { display: inline-block; width: 9px; height: 9px; border-radius: 50%; }
.rv-sw.best { background: #f59e0b; }
.rv-sw.good { background: #34d399; }
.rv-sw.mid { background: #60a5fa; }
.rv-sw.low { background: #fb7185; }

.rv-star-row { display: grid; grid-template-columns: 92px minmax(0, 1fr) 34px; align-items: center; gap: 9px; }
.rv-star-label { font-size: 12px; color: #f59e0b; letter-spacing: 1px; }
.rv-star-label i { font-style: normal; color: var(--text-lighter, rgba(148, 163, 184, .5)); }
.rv-dims-title { margin-top: 16px; }
.rv-dims { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }

/* 四维雷达 */
.r4-wrap { margin-top: 14px; text-align: center; }
.r4-title { font-size: 12px; font-weight: 700; color: var(--text-light); text-align: left; margin-bottom: 4px; }
.r4-title small { font-weight: 500; font-size: 12px; }
.r4-svg { width: 190px; height: 168px; display: block; margin: 0 auto; }
.r4-grid { fill: none; stroke: rgba(148,163,184,.28); stroke-width: 1; }
.r4-axis { stroke: rgba(148,163,184,.3); stroke-width: 1; }
.r4-poly { fill: rgba(43,177,135,.24); stroke: #2bb187; stroke-width: 2; transform-origin: 90px 76px; animation: rvExpand .75s cubic-bezier(.22,.9,.36,1) backwards; }
@keyframes rvExpand { from { transform: scale(0); opacity: 0; } }
.r4-label { fill: var(--text-light); font-size: 12px; font-weight: 700; }
.r4-val { fill: var(--primary); font-size: 12px; }
.rv-dim { display: flex; flex-direction: column; gap: 4px; padding: 10px 12px; border: 1px solid var(--border, rgba(148,163,184,.2)); border-radius: 10px; }
.rv-dim b { font-size: 17px; color: var(--primary, #2bb187); }
.rv-dim span { font-size: 12px; color: var(--text-light, #94a3b8); }
.rv-dim-track { height: 5px; border-radius: 99px; background: var(--an-fill, rgba(148,163,184,.14)); overflow: hidden; }
.rv-dim-track i { display: block; height: 100%; border-radius: 99px; background: linear-gradient(90deg, #2bb187, #22d3ee); }

.rv-cloud { display: flex; flex-wrap: wrap; gap: 6px 12px; align-items: baseline; line-height: 2.1; }
.rv-word { font-weight: 700; cursor: default; transition: transform .2s, opacity .2s; }
.rv-word:hover { transform: scale(1.12); opacity: .85; }
.rv-wc0 { color: #2bb187; }
.rv-wc1 { color: #0891b2; }
.rv-wc2 { color: #0ea5e9; }
.rv-wc3 { color: #059669; }
.rv-wc4 { color: #d97706; }

.rv-recent { display: flex; flex-direction: column; gap: 10px; max-height: 340px; overflow-y: auto; }
.rv-review { padding: 10px 12px; border: 1px solid var(--border, rgba(148,163,184,.2)); border-radius: 10px; background: color-mix(in srgb, var(--card-bg, #0f172a) 55%, transparent); }
.rv-review-head { display: flex; align-items: baseline; gap: 8px; }
.rv-review-head b { font-size: 12.5px; color: var(--text, #f1f5f9); }
.rv-review-stars { color: #f59e0b; font-size: 12px; letter-spacing: 1px; }
.rv-review-stars i { font-style: normal; color: var(--text-lighter, rgba(148, 163, 184, .5)); }
.rv-review-head small { margin-left: auto; color: var(--text-light, #94a3b8); font-size: 12px; }
.rv-review p { margin: 5px 0 0; font-size: 12px; line-height: 1.65; color: var(--text-light, #94a3b8); display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }

@media (max-width: 760px) {
  .rv-dims { grid-template-columns: minmax(0, 1fr); }
  .rv-star-row { grid-template-columns: 72px minmax(0, 1fr) 30px; }
}
</style>
