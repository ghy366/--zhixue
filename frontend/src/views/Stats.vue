<template>
  <div class="stats-page liquid">
    <!-- ===== 原版 stats-grid + stat-card ===== -->
    <div class="stats-grid">
      <div class="stat-card"><div class="stat-icon blue"   v-html="svgIcon('clipboard')" />
        <div><div class="stat-value">{{ disp.applied }}</div><div class="stat-label">本轮报名（选修）</div></div></div>
      <div class="stat-card"><div class="stat-icon green"  v-html="svgIcon('check-circle')" />
        <div><div class="stat-value">{{ disp.approved }}</div><div class="stat-label">已录取</div></div></div>
      <div class="stat-card"><div class="stat-icon purple" v-html="svgIcon('target')" />
        <div><div class="stat-value">{{ disp.fillRate }}%</div><div class="stat-label">平均满员率</div></div></div>
      <div class="stat-card"><div class="stat-icon amber"  v-html="svgIcon('hourglass')" />
        <div><div class="stat-value">{{ disp.fullCount }}</div><div class="stat-label">满员课程 · 候补 {{ disp.waiting }} 人</div></div></div>
    </div>

    <!-- ===== 原版 stats-two 双排行 ===== -->
    <div class="stats-two">
      <div class="section">
        <div class="section-header"><div class="section-title"><span v-html="svgIcon('bar-chart')" />课程热度排行（本轮报名）</div></div>
        <template v-if="hotFiltered.length">
          <div v-for="(x, i) in hotFiltered" :key="x.courseId" class="rank-row">
            <div class="rank-name">
              <span class="rank-idx" :class="{ top: i < 3 }">{{ i + 1 }}</span>
              {{ x.courseName }}
              <span class="rank-sub">{{ x.teacherName || '' }}</span>
            </div>
            <div class="rank-bar-wrap"><div class="rank-bar" :style="{ width: Math.round((x.applied || 0) * 100 / hyMax) + '%' }" /></div>
            <div class="rank-val">{{ x.applied }} 人</div>
          </div>
        </template>
        <div v-else class="empty-state"><div class="empty-icon" v-html="svgIcon('bar-chart')" /><p>暂无报名数据</p></div>
      </div>

      <div class="section">
        <div class="section-header"><div class="section-title"><span v-html="svgIcon('target')" />满员率排行</div></div>
        <template v-if="fillFiltered.length">
          <div v-for="x in fillFiltered" :key="x.courseId" class="rank-row">
            <div class="rank-name">{{ x.courseName }}<span class="rank-sub">{{ x.approved }}/{{ x.capacity }}</span></div>
            <div class="rank-bar-wrap"><div class="rank-bar fill" :class="{ full: x.fillRate >= 100 }" :style="{ width: Math.min(100, x.fillRate) + '%' }" /></div>
            <div class="rank-val">{{ x.fillRate }}%</div>
          </div>
        </template>
        <div v-else class="empty-state"><div class="empty-icon" v-html="svgIcon('target')" /><p>暂无数据</p></div>
      </div>
    </div>

    <!-- ===== 轮次趋势 ===== -->
    <div class="section">
      <div class="section-header"><div class="section-title"><span v-html="svgIcon('refresh')" />轮次报名趋势</div></div>
      <template v-if="rounds.length">
        <div v-for="x in rounds" :key="x.round" class="rank-row">
          <div class="rank-name">第 {{ x.round }} 轮</div>
          <div class="rank-bar-wrap"><div class="rank-bar round" :style="{ width: Math.round((x.applied || 0) * 100 / rmMax) + '%' }" /></div>
          <div class="rank-val">报名 {{ x.applied }} · 录取 {{ x.approved }} · 未录取 {{ x.rejected }} · 退课 {{ x.dropped }}</div>
        </div>
      </template>
      <div v-else class="empty-state"><div class="empty-icon" v-html="svgIcon('refresh')" /><p>暂无数据</p></div>
    </div>

    <!-- ===== 教师工作量 ===== -->
    <div class="section">
      <div class="section-header"><div class="section-title"><span v-html="svgIcon('users')" />教师工作量</div></div>
      <div v-if="teachers.length" class="data-table">
        <table>
          <thead><tr><th>教师</th><th>学院</th><th>开设课程</th><th>本轮录取学生</th><th>收到评价</th><th>平均评分</th></tr></thead>
          <tbody>
            <tr v-for="t in teachers" :key="t.teacherName">
              <td><b>{{ t.teacherName }}</b></td>
              <td>{{ t.college || '—' }}</td>
              <td>{{ t.courseCount }}</td>
              <td>{{ t.studentCount }}</td>
              <td>{{ t.reviewCount }}</td>
              <td>
                <span v-if="t.reviewCount" class="rating-cell">
                  <span class="rating-stars">{{ '★★★★★'.slice(0, Math.round(t.avgRating)) }}</span>
                  <span class="rating-num">{{ t.avgRating }}</span>
                </span>
                <span v-else>—</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="empty-state"><div class="empty-icon" v-html="svgIcon('users')" /><p>暂无教师数据</p></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import http from '@/api'

const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`

const ov = reactive<any>({ round: 1, totalCourses: 0, totalUsers: 0, applied: 0, approved: 0, fillRate: 0, fullCount: 0, waiting: 0 })
const hot = ref<any[]>([])
const fill = ref<any[]>([])
const rounds = ref<any[]>([])
const teachers = ref<any[]>([])

const hotFiltered = computed(() => hot.value.filter(x => !x.required).slice(0, 10))
const fillFiltered = computed(() => fill.value.filter(x => !x.required && x.capacity > 0).slice(0, 10))
const hyMax = computed(() => Math.max(1, ...hotFiltered.value.map(x => x.applied || 0)))
const rmMax = computed(() => Math.max(1, ...rounds.value.map(x => x.applied || 0)))

async function load() {
  try {
    const r: any = await http.get('/stats/report')
    const d = r?.data || {}
    if (d.overview) Object.assign(ov, d.overview)
    hot.value = d.hot || []
    fill.value = d.fill || []
    rounds.value = d.rounds || []
    teachers.value = d.teachers || []
    countUp({
      applied: ov.applied || 0,
      approved: ov.approved || 0,
      fillRate: Math.round((ov.fillRate || 0) * 100),
      fullCount: ov.fullCount || 0,
      waiting: ov.waiting || 0,
    })
  } catch {}
}

/* 数字滚动计数动画 */
const disp = reactive<any>({ applied: 0, approved: 0, fillRate: 0, fullCount: 0, waiting: 0 })
let rafId = 0
function countUp(target: Record<string, number>, dur = 950) {
  cancelAnimationFrame(rafId)
  const from: Record<string, number> = {}
  for (const k of Object.keys(target)) from[k] = disp[k] || 0
  const start = performance.now()
  const step = (now: number) => {
    const p = Math.min(1, (now - start) / dur)
    const e = 1 - Math.pow(1 - p, 3) /* easeOutCubic */
    for (const k of Object.keys(target)) disp[k] = Math.round(from[k] + (target[k] - from[k]) * e)
    if (p < 1) rafId = requestAnimationFrame(step)
  }
  rafId = requestAnimationFrame(step)
}
onMounted(load)
</script>
