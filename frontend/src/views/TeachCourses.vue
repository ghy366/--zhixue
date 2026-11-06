<template>
  <div class="page-wrap stats-page liquid">
    <!-- ===== 教学概览 ===== -->
    <div class="stats-grid">
      <div class="stat-card"><div class="stat-icon blue" v-html="svgIcon('book-open')" /><div><div class="stat-value">{{ courses.length }} <small style="font-size:13px;color:var(--text-light)">/ {{ teacherMax }}</small></div><div class="stat-label">{{ t('授课课程（每学期上限）') }}</div></div></div>
      <div class="stat-card"><div class="stat-icon green" v-html="svgIcon('users')" /><div><div class="stat-value">{{ totalApplied }}</div><div class="stat-label">{{ t('总报名人次') }}</div></div></div>
      <div class="stat-card"><div class="stat-icon purple" v-html="svgIcon('check-circle')" /><div><div class="stat-value">{{ totalApproved }}</div><div class="stat-label">{{ t('已录取学生') }}</div></div></div>
      <div class="stat-card"><div class="stat-icon amber" v-html="svgIcon('target')" /><div><div class="stat-value">{{ overCount }}</div><div class="stat-label">{{ t('报名超额课程') }}</div></div></div>
    </div>

    <!-- ===== 容量调整建议：需求远高于名额时提示加开班级 ===== -->
    <div v-if="capacitySuggestions.length" class="tc-suggest">
      <div class="tc-suggest-title" v-html="svgIcon('alert-triangle') + ' ' + t('容量调整建议')"></div>
      <div v-for="s in capacitySuggestions" :key="s.id" class="tc-suggest-row">
        <b>{{ s.name }}</b>
        <span>{{ t('报名需求') }} <i>{{ s.demand }}</i> {{ t('人 / 容量') }} <i>{{ s.capacity }}</i> {{ t('人，超出') }} <i>{{ s.pct }}%</i></span>
        <em>{{ t('建议加开 {n} 个班，或将每班容量扩至 {c} 人', { n: s.classes, c: s.suggestCap }) }}</em>
      </div>
    </div>

    <div v-if="!courses.length && !loading" class="empty-state">
      <div class="empty-icon" v-html="svgIcon('book-open')" />
      <div class="empty-title">{{ t('暂无教授课程') }}</div>
    </div>

    <!-- ===== 课程仪表卡 ===== -->
    <div v-else class="tc-grid">
      <div v-for="c in courses" :key="c.id" class="tc-card" :class="{ over: c.oversubscribed }">
        <div class="tc-head">
          <div class="tc-title">
            <b>{{ c.name }}</b>
            <span class="course-cat" :class="'cat-' + c.category">{{ c.category }}</span>
          </div>
          <span class="tc-status" :class="c.oversubscribed ? 'warn' : 'ok'">
            {{ c.oversubscribed ? t('超额 +{n}', { n: (c.stat?.total || 0) - (c.capacity || 0) }) : t('正常') }}
          </span>
        </div>

        <div class="tc-body">
          <svg viewBox="0 0 90 90" class="tc-ring" role="img" :aria-label="c.name + ' ' + t('容量占用')">
            <g transform="rotate(-90 45 45)">
              <circle cx="45" cy="45" r="37" class="tc-ring-track" />
              <circle
                cx="45" cy="45" r="37"
                class="tc-ring-fill"
                :class="ringCls(c)"
                :stroke-dasharray="`${ringPct(c) / 100 * RING_C} ${RING_C}`"
                stroke-linecap="round"
              />
            </g>
            <text x="45" y="43" text-anchor="middle" class="tc-ring-num">{{ c.stat?.total || 0 }}</text>
            <text x="45" y="57" text-anchor="middle" class="tc-ring-sub">{{ t('/ {n} 容量', { n: c.capacity }) }}</text>
          </svg>

          <div class="tc-stats">
            <div class="tc-stat"><b>{{ c.stat?.approved || 0 }}</b><span>{{ t('已录取') }}</span></div>
            <div class="tc-stat"><b>{{ (c.stat?.total || 0) - (c.stat?.approved || 0) }}</b><span>{{ t('待结算') }}</span></div>
            <div class="tc-stat"><b>{{ waitingOf(c) }}</b><span>{{ t('候补中') }}</span></div>
            <div class="tc-stat"><b>{{ competition(c) }}</b><span>{{ t('竞争比') }}</span></div>
          </div>
        </div>

        <div class="tc-meta">
          <span>👨‍🏫 {{ c.teacherName || '—' }}</span>
          <span>🎓 {{ t('{n} 学分', { n: c.credit || 0 }) }}</span>
          <span>🏛️ {{ c.location || t('待定') }}</span>
        </div>
        <div class="tc-meta"><span>📅 {{ c.time || t('时间待安排') }}</span></div>

        <div v-if="c.guide?.topPoints" class="tc-comp">
          <span>{{ t('当前最高投分') }} <b>{{ c.guide.topPoints }}</b> {{ t('分') }}</span>
          <span v-if="c.guide.safeLine != null" class="tc-comp-line">{{ t('实时竞争线 {n} 分', { n: c.guide.safeLine }) }}</span>
        </div>

        <div class="tc-foot">
          <button class="btn btn-outline btn-sm" @click="$router.push({ path: '/student-list', query: { courseId: c.id } })">{{ t('查看学生') }}</button>
          <button class="btn btn-outline btn-sm" @click="$router.push({ path: '/grade-mgr', query: { courseId: c.id } })">{{ t('成绩管理') }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import http from '@/api'
import { useAuthStore } from '@/stores/auth'
import { t } from '@/i18n'
const auth = useAuthStore()
const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`
const loading = ref(false)

const courses = ref<any[]>([])
const waitMap = ref<Record<number, number>>({})

const RING_C = 2 * Math.PI * 37
const ringPct = (c: any) => {
  const cap = c.capacity || 0
  return cap <= 0 ? 0 : Math.min(100, Math.round(((c.stat?.total || 0) / cap) * 100))
}
const ringCls = (c: any) => (ringPct(c) >= 100 ? 'full' : ringPct(c) >= 70 ? 'warm' : '')
const waitingOf = (c: any) => waitMap.value[c.id] ?? 0
const competition = (c: any) => {
  const cap = c.capacity || 0
  return cap <= 0 ? '—' : ((c.stat?.total || 0) / cap).toFixed(1) + ':1'
}

const totalApplied = computed(() => courses.value.reduce((s, c) => s + (c.stat?.total || 0), 0))
const totalApproved = computed(() => courses.value.reduce((s, c) => s + (c.stat?.approved || 0), 0))
const overCount = computed(() => courses.value.filter(c => c.oversubscribed).length)

/** 容量调整建议：报名需求 ≥ 容量×1.2 时建议加开班级（数据来自 guide.bidCount / stat.total） */
const capacitySuggestions = computed(() =>
  courses.value
    .map(c => {
      const demand = Math.max(c.stat?.total || 0, c.guide?.bidCount || 0)
      const cap = c.capacity || 0
      if (cap <= 0 || demand < cap * 1.2) return null
      const classes = Math.max(1, Math.ceil((demand - cap) / cap))
      return {
        id: c.id, name: c.name, demand, capacity: cap,
        pct: Math.round((demand - cap) * 100 / cap),
        classes,
        suggestCap: Math.ceil(demand / (classes + 1)),
      }
    })
    .filter(Boolean) as any[]
)

async function load() {
  loading.value = true
  try {
    const [r, cfg]: any[] = await Promise.all([
      http.get(`/courses/teacher/${auth.user?.id}`),
      http.get('/system/config').catch(() => null),
    ])
    courses.value = r?.data || []
    teacherMax.value = Number(cfg?.data?.teacherMaxCourses) || 3
    // 每门课的候补人数（课程数少，逐个拉取即可）
    const results = await Promise.allSettled(
      courses.value.map(c => http.get(`/waitlist/course/${c.id}`))
    )
    const map: Record<number, number> = {}
    courses.value.forEach((c, i) => {
      const list = results[i].status === 'fulfilled' ? (results[i] as any).value?.data || [] : []
      map[c.id] = list.length
    })
    waitMap.value = map
  } catch {}
  loading.value = false
}
onMounted(load)
</script>

<style scoped>
/* ===== 容量调整建议 ===== */
.tc-suggest { margin-top: 14px; padding: 12px 16px; border: 1px solid rgba(245,158,11,.4); border-radius: 12px; background: rgba(245,158,11,.1); }
.tc-suggest-title { font-size: 13.5px; font-weight: 700; color: #f59e0b; display: flex; align-items: center; gap: 6px; }
.tc-suggest-title :deep(.icon) { width: 16px; height: 16px; }
.tc-suggest-row { display: flex; flex-wrap: wrap; align-items: baseline; gap: 10px; margin-top: 8px; font-size: 13px; }
.tc-suggest-row b { color: var(--text); }
.tc-suggest-row span, .tc-suggest-row em { color: var(--text-light); font-size: 12.5px; }
.tc-suggest-row i { font-style: normal; font-weight: 700; color: #fbbf24; }
.tc-suggest-row em { font-style: normal; color: #fbbf24; }

.page-wrap { padding: 4px 0; display: flex; flex-direction: column; gap: 16px; }
.empty-state { text-align: center; padding: 60px 20px; color: var(--text-light); }
.empty-icon { font-size: 48px; opacity: 0.3; margin-bottom: 10px; }

/* 课程仪表卡 */
.tc-grid { display: grid; gap: 14px; grid-template-columns: repeat(auto-fill, minmax(340px, 1fr)); }
.tc-card {
  position: relative; overflow: hidden;
  background: var(--card-bg); border: 1px solid var(--border); border-radius: 14px;
  padding: 16px 18px; display: flex; flex-direction: column; gap: 11px;
  transition: transform .25s cubic-bezier(.2,.7,.3,1), border-color .25s, box-shadow .25s;
}
.tc-card:hover { transform: translateY(-4px); border-color: rgba(43,177,135,.5); box-shadow: 0 12px 28px -10px rgba(31,166,122,.35); }
.tc-card.over { border-color: rgba(245,158,11,.45); }
.tc-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 8px; }
.tc-title { display: flex; flex-direction: column; gap: 5px; min-width: 0; }
.tc-title b { font-size: 15px; color: var(--text); }
.tc-status { flex-shrink: 0; font-size: 12px; font-weight: 700; padding: 2px 9px; border-radius: 999px; }
.tc-status.ok { color: #059669; background: rgba(16,185,129,.12); }
.tc-status.warn { color: #d97706; background: rgba(245,158,11,.14); }

.tc-body { display: flex; align-items: center; gap: 16px; }
.tc-ring { width: 92px; height: 92px; flex-shrink: 0; }
.tc-ring-track { fill: none; stroke: rgba(148,163,184,.16); stroke-width: 9; }
.tc-ring-fill { fill: none; stroke: #2bb187; stroke-width: 9; transition: stroke-dasharray .7s cubic-bezier(.22,.9,.36,1); }
.tc-ring-fill.warm { stroke: #f59e0b; }
.tc-ring-fill.full { stroke: #f43f5e; }
.tc-ring-num { font-size: 17px; font-weight: 800; fill: var(--text); }
.tc-ring-sub { font-size: 10px; fill: var(--text-light); }

.tc-stats { flex: 1; display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.tc-stat { padding: 8px 10px; border: 1px solid var(--border); border-radius: 10px; display: flex; flex-direction: column; gap: 1px; }
.tc-stat b { font-size: 16px; color: var(--text); font-variant-numeric: tabular-nums; }
.tc-stat span { font-size: 12px; color: var(--text-light); }

.tc-meta { font-size: 12px; color: var(--text-light); display: flex; gap: 12px; flex-wrap: wrap; }
.tc-comp { display: flex; gap: 12px; font-size: 12px; color: var(--text-light); padding: 7px 11px; border-radius: 9px; background: rgba(43,177,135,.07); border: 1px dashed rgba(43,177,135,.3); }
.tc-comp b { color: var(--primary); }
.tc-comp-line { color: #d97706; font-weight: 600; }
.tc-foot { display: flex; justify-content: flex-end; gap: 8px; }

@media (max-width: 760px) {
  .tc-grid { grid-template-columns: 1fr; }
  .tc-stats { grid-template-columns: repeat(4, 1fr); }
  .tc-stat { padding: 6px; }
  .tc-stat span { font-size: 12px; }
}
</style>
