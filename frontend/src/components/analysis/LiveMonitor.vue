<template>
  <div class="an-analysis">
    <AnalysisKpi :items="kpis" />

    <!-- 状态条 -->
    <div class="an-panel lv-strip">
      <div class="lv-live">
        <span class="an-live-dot" />实时
        <span class="lv-server">服务器时间 {{ data?.serverTime || '—' }}</span>
      </div>
      <div class="lv-round">
        <span class="rd-pill" :class="s.roundOpen ? 'lv-pill-on' : 'lv-pill-off'">
          第 {{ s.round || '—' }} 轮 · {{ s.roundOpen ? '开放中' : '未开放' }}
        </span>
        <span v-if="countdown" class="lv-count">{{ countdown }}</span>
      </div>
    </div>

    <div class="an-grid an-grid-main">
      <section class="an-panel an-panel-wide">
        <header class="an-panel-head">
          <div>
            <div class="an-panel-title"><span class="an-title-icon" v-html="svgIcon('activity')" />操作节奏 · 最近 30 分钟</div>
            <div class="an-panel-sub">面积为本轮全部 API 操作，绿线为报名/候补类操作</div>
          </div>
          <div class="an-panel-side an-box-focus">
            <template v-if="s.peakOps">
              <b>{{ s.peakOps }} 次/分</b>
              <span>瞬时峰值出现在 {{ s.peakMinute }}</span>
            </template>
            <template v-else>最近半小时很安静</template>
          </div>
        </header>

        <div class="an-chart-scroll">
          <EChart :option="timelineOption" fill />
        </div>

        <div class="an-legend">
          <span><i class="lv-swatch ops" />全部操作</span>
          <span><i class="lv-swatch enroll" />报名 / 候补</span>
          <span class="an-muted">每 5 秒自动刷新 · 数据变化时平滑过渡</span>
        </div>
      </section>

      <section class="an-panel an-panel-side-panel">
        <header class="an-panel-head an-panel-head-stack">
          <div>
            <div class="an-panel-title"><span class="an-title-icon" v-html="svgIcon('zap')" />最新动态</div>
            <div class="an-panel-sub">选课相关操作实时流出</div>
          </div>
        </header>

        <div v-if="feed.length" class="lv-feed">
          <div v-for="(f, i) in feed" :key="i + f.time" class="lv-feed-row" :style="{ animationDelay: (i * 40) + 'ms' }">
            <span class="lv-feed-time">{{ f.time }}</span>
            <span class="lv-feed-user">{{ f.userName || '匿名' }}</span>
            <span class="lv-feed-action">{{ f.action }}</span>
            <span class="lv-feed-status" :class="f.status === '成功' ? 'ok' : 'bad'">{{ f.status }}</span>
          </div>
        </div>
        <div v-else class="an-empty">
          <div class="an-empty-icon" v-html="svgIcon('inbox')" />
          <p>暂无选课操作记录</p>
          <small>学生开始选课后，这里会实时滚动最新动态</small>
        </div>
      </section>
    </div>

    <section class="an-panel">
      <header class="an-panel-head">
        <div>
          <div class="an-panel-title"><span class="an-title-icon" v-html="svgIcon('bar-chart')" />课程报名赛跑</div>
          <div class="an-panel-sub">本轮非必修课程 · 按报名/容量占比排序 · 条上为报名数（含待录取）</div>
        </div>
        <div class="an-panel-side">
          <div class="an-rate-pill"><b>{{ s.pendingNow || 0 }}</b><span>本轮待录取人次</span></div>
        </div>
      </header>

      <div v-if="race.length" class="lv-race">
        <div v-for="r in race" :key="r.courseId" class="lv-race-row" :title="`${r.courseName} · 报名 ${r.applied}/${r.capacity} · 录取 ${r.approved} · 候补 ${r.waiting}`">
          <div class="lv-race-name">
            {{ r.courseName }}
            <small>{{ r.teacherName || '' }}</small>
          </div>
          <div class="lv-race-track">
            <i
              class="lv-race-fill"
              :class="{ full: r.fillRate >= 100 }"
              :style="{ width: Math.min(100, r.fillRate) + '%' }"
            />
            <i
              v-if="r.capacity"
              class="lv-race-cap"
              :style="{ left: Math.min(100, (r.approved / r.capacity) * 100) + '%' }"
            />
          </div>
          <div class="lv-race-val">
            <b>{{ r.applied }}</b> / {{ r.capacity }}
            <em v-if="r.waiting">候补 {{ r.waiting }}</em>
          </div>
        </div>
      </div>
      <div v-else class="an-empty">
        <div class="an-empty-icon" v-html="svgIcon('bar-chart')" />
        <p>本轮还没有报名数据</p>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import http from '@/api'
import AnalysisKpi from './AnalysisKpi.vue'
import EChart from '@/components/charts/EChart.vue'
import { catAxis, TOOLTIP, valAxis } from '@/utils/echarts'

const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`

const data = ref<any>(null)
const nowTick = ref(Date.now())
let timer: any = null
let clock: any = null

async function fetch() {
  try {
    const r: any = await http.get('/stats/live')
    data.value = r?.data || null
  } catch { /* 保留上一次数据，避免轮询闪空 */ }
}

onMounted(() => {
  fetch()
  timer = setInterval(fetch, 5000)
  clock = setInterval(() => { nowTick.value = Date.now() }, 1000)
})
onBeforeUnmount(() => { clearInterval(timer); clearInterval(clock) })

const s = computed<any>(() => data.value?.summary || {})
const timeline = computed<any[]>(() => data.value?.timeline || [])
const race = computed<any[]>(() => data.value?.race || [])
const feed = computed<any[]>(() => data.value?.feed || [])

/* KPI 数字缓动（轮询变化时滚动过渡，翻牌感） */
const disp = reactive({ enrollLastHour: 0, opsLastHour: 0, pendingNow: 0, approvedNow: 0 })
const TWEEN_KEYS: Array<keyof typeof disp> = ['enrollLastHour', 'opsLastHour', 'pendingNow', 'approvedNow']
function tweenTo(key: keyof typeof disp, target: number) {
  const from = disp[key]
  const delta = target - from
  if (!delta) { disp[key] = target; return }
  const start = performance.now(), dur = 600
  const step = (now: number) => {
    const p = Math.min(1, (now - start) / dur)
    disp[key] = Math.round(from + delta * (1 - Math.pow(1 - p, 3)))
    if (p < 1) requestAnimationFrame(step)
  }
  requestAnimationFrame(step)
}
watch(() => data.value?.summary, (sum) => {
  if (!sum) return
  for (const k of TWEEN_KEYS) tweenTo(k, Number(sum[k]) || 0)
})

const kpis = computed(() => [
  { label: '近 1 小时报名', value: disp.enrollLastHour, hint: `今日报名 ${s.value.todayEnrolls || 0} 次`, icon: 'cart', tone: 'blue' },
  { label: '近 1 小时操作', value: disp.opsLastHour, hint: `峰值 ${s.value.peakOps || 0} 次/分`, icon: 'activity', tone: 'purple' },
  { label: '本轮待录取', value: disp.pendingNow, hint: `已录取 ${disp.approvedNow} 人次`, icon: 'hourglass', tone: 'amber' },
  { label: '轮次状态', value: s.value.roundOpen ? '开放中' : '未开放', hint: `第 ${s.value.round || '—'} 轮`, icon: s.value.roundOpen ? 'check-circle' : 'x-circle', tone: s.value.roundOpen ? 'green' : 'rose' },
])

/* 倒计时 */
const countdown = computed(() => {
  const close = data.value?.closeAt
  if (!close || !s.value.roundOpen) return ''
  const diff = new Date(close).getTime() - nowTick.value
  if (diff <= 0) return ''
  const sec = Math.floor(diff / 1000)
  const h = Math.floor(sec / 3600), m = Math.floor((sec % 3600) / 60), ss = sec % 60
  const p = (n: number) => String(n).padStart(2, '0')
  return h > 0 ? `距结束 ${h}:${p(m)}:${p(ss)}` : `距结束 ${p(m)}:${p(ss)}`
})

/* 时间线图表（ECharts）：轮询更新时数据平滑过渡 */
const timelineOption = computed(() => ({
  grid: { left: 44, right: 18, top: 24, bottom: 30 },
  tooltip: { trigger: 'axis', ...TOOLTIP },
  legend: { show: false },
  xAxis: { ...catAxis(timeline.value.map(p => p.minute)), boundaryGap: false, axisLabel: { color: '#94a3b8', fontSize: 10, interval: 4 } },
  yAxis: { ...valAxis('次/分') },
  animationDurationUpdate: 500,
  series: [
    {
      name: '全部操作', type: 'line', smooth: true, symbol: 'none',
      data: timeline.value.map(p => p.ops),
      lineStyle: { color: '#2bb187', width: 2 },
      areaStyle: { color: 'rgba(43,177,135,.16)' },
    },
    {
      name: '报名/候补', type: 'line', smooth: true,
      symbol: 'circle', symbolSize: 6,
      data: timeline.value.map(p => p.enrolls),
      lineStyle: { color: '#10b981', width: 2 },
      itemStyle: { color: '#10b981' },
    },
  ],
}))
</script>

<style scoped>
.lv-strip { display: flex; align-items: center; justify-content: space-between; gap: 14px; flex-wrap: wrap; padding: 12px 18px; }
.lv-live { display: inline-flex; align-items: center; gap: 8px; font-size: 13px; font-weight: 700; color: #059669; }
.lv-server { font-size: 12px; font-weight: 500; color: var(--text-light, #94a3b8); }
.lv-round { display: inline-flex; align-items: center; gap: 10px; }
.lv-pill-on { padding: 3px 12px; border-radius: 999px; font-size: 12.5px; font-weight: 700; color: #059669; background: rgba(16,185,129,.12); border: 1px solid rgba(16,185,129,.35); }
.lv-pill-off { padding: 3px 12px; border-radius: 999px; font-size: 12.5px; font-weight: 700; color: #64748b; background: rgba(100,116,139,.12); border: 1px solid rgba(100,116,139,.3); }
.lv-count { font-size: 13px; font-weight: 800; color: #d97706; font-variant-numeric: tabular-nums; }

.lv-enroll-dot { fill: #10b981; stroke: #fff; stroke-width: 1; }
.lv-swatch { display: inline-block; width: 14px; height: 4px; border-radius: 2px; }
.lv-swatch.ops { background: #2bb187; }
.lv-swatch.enroll { background: #10b981; }

.lv-feed { display: flex; flex-direction: column; max-height: 320px; overflow-y: auto; }
.lv-feed-row {
  display: flex; align-items: center; gap: 8px; padding: 7px 4px;
  font-size: 12px; border-bottom: 1px dashed var(--border, rgba(148,163,184,.18));
  animation: lvIn .4s ease backwards;
}
@keyframes lvIn { from { opacity: 0; transform: translateX(-8px); } to { opacity: 1; transform: none; } }
.lv-feed-time { color: var(--text-light, #94a3b8); font-variant-numeric: tabular-nums; flex-shrink: 0; }
.lv-feed-user { font-weight: 700; color: var(--text, #f1f5f9); flex-shrink: 0; max-width: 76px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.lv-feed-action { color: var(--text-light, #94a3b8); min-width: 0; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.lv-feed-status { flex-shrink: 0; font-size: 12px; font-weight: 700; padding: 1px 7px; border-radius: 999px; }
.lv-feed-status.ok { color: #059669; background: rgba(16,185,129,.12); }
.lv-feed-status.bad { color: #e11d48; background: rgba(244,63,94,.12); }

.lv-race { display: flex; flex-direction: column; gap: 9px; }
.lv-race-row { display: grid; grid-template-columns: 168px minmax(0, 1fr) 128px; align-items: center; gap: 12px; }
.lv-race-name { font-size: 12.5px; font-weight: 600; color: var(--text, #f1f5f9); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.lv-race-name small { color: var(--text-light, #94a3b8); font-weight: 400; margin-left: 5px; }
.lv-race-track { position: relative; height: 14px; border-radius: 99px; background: var(--an-fill, rgba(148,163,184,.14)); overflow: visible; }
.lv-race-fill {
  display: block; height: 100%; border-radius: 99px;
  background: linear-gradient(90deg, #2bb187, #22d3ee);
  transition: width .8s cubic-bezier(.22, .9, .36, 1);
}
.lv-race-fill.full { background: linear-gradient(90deg, #f43f5e, #fb923c); }
.lv-race-cap {
  position: absolute; top: -3px; bottom: -3px; width: 2.5px; border-radius: 1px;
  background: var(--text, #f1f5f9); opacity: .75;
  transition: left .8s cubic-bezier(.22, .9, .36, 1);
}
.lv-race-val { font-size: 12px; color: var(--text-light, #94a3b8); text-align: right; font-variant-numeric: tabular-nums; }
.lv-race-val b { color: var(--text, #f1f5f9); font-size: 13.5px; }
.lv-race-val em { font-style: normal; color: #d97706; font-weight: 700; margin-left: 6px; font-size: 12px; }

@media (max-width: 900px) {
  .lv-race-row { grid-template-columns: 110px minmax(0, 1fr) 96px; }
}
</style>
