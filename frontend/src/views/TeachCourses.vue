<template>
  <div class="page-wrap stats-page liquid">
    <!-- ===== 学期切换：默认全部（当前学期），可按培养学期筛选 ===== -->
    <div class="tc-sembar">
      <span class="tc-sembar-label">{{ t('授课学期') }}</span>
      <el-select v-model="semFilter" size="default" style="width: 220px" clearable :placeholder="t('全部学期')">
        <el-option v-for="o in semOptions" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
      <span class="tc-sembar-tip">{{ t('按教学班查看授课任务：每学期不超过 {n} 门', { n: teacherMax }) }}</span>
    </div>

    <!-- ===== 教学概览 ===== -->
    <div class="stats-grid">
      <div class="stat-card"><div class="stat-icon blue" v-html="svgIcon('book-open')" /><div><div class="stat-value">{{ classes.length }} <small style="font-size:13px;color:var(--text-light)">/ {{ teacherMax }} 门</small></div><div class="stat-label">{{ t('本学期教学班（平行班）') }}</div></div></div>
      <div class="stat-card"><div class="stat-icon green" v-html="svgIcon('users')" /><div><div class="stat-value">{{ totalStudents }}</div><div class="stat-label">{{ t('班里学生总数') }}</div></div></div>
      <div class="stat-card"><div class="stat-icon purple" v-html="svgIcon('check-circle')" /><div><div class="stat-value">{{ courseCount }}</div><div class="stat-label">{{ t('覆盖课程门数') }}</div></div></div>
      <div class="stat-card"><div class="stat-icon amber" v-html="svgIcon('target')" /><div><div class="stat-value">{{ avgClassSize }}</div><div class="stat-label">{{ t('平均班额') }}</div></div></div>
    </div>

    <div v-if="!classes.length && !loading" class="empty-state">
      <div class="empty-icon" v-html="svgIcon('book-open')" />
      <div class="empty-title">{{ t('本学期暂无教学班') }}</div>
    </div>

    <!-- ===== 教学班仪表卡 ===== -->
    <div v-else class="tc-grid">
      <div v-for="c in classes" :key="c.id" class="tc-card">
        <div class="tc-head">
          <div class="tc-title">
            <b>{{ c.courseName }}</b>
            <span class="course-cat" :class="'cat-' + c.category">{{ c.category }}</span>
          </div>
          <span class="tc-status ok">{{ t('正常') }}</span>
        </div>

        <div class="tc-body">
          <svg viewBox="0 0 90 90" class="tc-ring" role="img" :aria-label="c.courseName + ' ' + t('容量占用')">
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
            <text x="45" y="43" text-anchor="middle" class="tc-ring-num">{{ c.memberCount || 0 }}</text>
            <text x="45" y="57" text-anchor="middle" class="tc-ring-sub">{{ t('/ {n} 容量', { n: c.capacity }) }}</text>
          </svg>

          <div class="tc-stats">
            <div class="tc-stat"><b>{{ c.classNo.split('-').pop() }}</b><span>{{ t('班级序号') }}</span></div>
            <div class="tc-stat"><b>{{ c.credit ?? '—' }}</b><span>{{ t('学分') }}</span></div>
            <div class="tc-stat"><b>{{ c.semesterNo }}</b><span>{{ t('开课学期') }}</span></div>
            <div class="tc-stat"><b>{{ Math.round((c.memberCount || 0) / (c.capacity || 1) * 100) + '%' }}</b><span>{{ t('班额占用') }}</span></div>
          </div>
        </div>

        <div class="tc-meta">
          <span>🏷️ {{ c.classNo }}</span>
          <span>🎓 {{ t('{n} 学分', { n: c.credit || 0 }) }}</span>
          <span>🏛️ {{ c.location || t('待定') }}</span>
        </div>
        <div class="tc-meta"><span>📅 {{ c.time || t('时间待安排') }}</span></div>

        <div class="tc-foot">
          <button class="btn btn-outline btn-sm" @click="$router.push({ path: '/student-list', query: { classId: c.id, courseId: c.courseId } })">{{ t('查看学生') }}</button>
          <button class="btn btn-outline btn-sm" @click="$router.push({ path: '/grade-mgr', query: { classId: c.id, courseId: c.courseId } })">{{ t('成绩管理') }}</button>
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
import { semesterTitle } from '@/utils/semester'
const auth = useAuthStore()
const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`
const loading = ref(false)

/** 我的教学班：一行 = 一个平行班（班号/课程/时间地点/班额/在读人数） */
const allClasses = ref<any[]>([])
const curSem = ref<number>(0)
const semFilter = ref<number | null>(null)
const teacherMax = ref(6)
/* 只展示选中学期的班：教师的班跨多个年级学期，可按培养学期切片 */
const classes = computed(() => {
  if (semFilter.value == null) return allClasses.value
  return allClasses.value.filter(c => (c.semesterNo ?? null) === semFilter.value)
})
const semOptions = computed(() => {
  const set = new Set<number>()
  for (const c of allClasses.value) if (c.semesterNo) set.add(c.semesterNo)
  if (curSem.value) set.add(curSem.value)
  const list = [...set].sort((a, b) => a - b)
  return list.map(n => ({ value: n, label: semesterTitle(n) + (n === curSem.value ? `（${t('进行中')}）` : '') }))
})

const RING_C = 2 * Math.PI * 37
const ringPct = (c: any) => {
  const cap = c.capacity || 0
  return cap <= 0 ? 0 : Math.min(100, Math.round(((c.memberCount || 0) / cap) * 100))
}
const ringCls = (c: any) => (ringPct(c) >= 100 ? 'full' : ringPct(c) >= 70 ? 'warm' : '')

const totalStudents = computed(() => classes.value.reduce((s, c) => s + (c.memberCount || 0), 0))
const courseCount = computed(() => new Set(classes.value.map(c => c.courseId)).size)
const avgClassSize = computed(() => {
  const n = classes.value.length
  return n ? Math.round(totalStudents.value / n) : '—'
})

async function load() {
  loading.value = true
  try {
    const [r, cfg, cur]: any[] = await Promise.all([
      http.get('/teaching-classes', { params: { teacherId: auth.user?.id } }),
      http.get('/system/config').catch(() => null),
      http.get('/system/current-semester').catch(() => null),
    ])
    allClasses.value = r?.data || []
    teacherMax.value = Number(cfg?.data?.teacherMaxCourses) || 6
    curSem.value = Number(cur?.data?.semesterNo) || 0
  } catch {}
  loading.value = false
}
onMounted(load)
</script>

<style scoped>
.page-wrap { padding: 4px 0; display: flex; flex-direction: column; gap: 16px; }
.tc-sembar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.tc-sembar-label { font-size: 13px; font-weight: 700; color: var(--text); }
.tc-sembar-tip { font-size: 12.5px; color: var(--text-lighter); }
.empty-state { text-align: center; padding: 60px 20px; color: var(--text-light); }
.empty-icon { font-size: 48px; opacity: 0.3; margin-bottom: 10px; }

/* 教学班仪表卡 */
.tc-grid { display: grid; gap: 14px; grid-template-columns: repeat(auto-fill, minmax(340px, 1fr)); }
.tc-card {
  position: relative; overflow: hidden;
  background: var(--card-bg); border: 1px solid var(--border); border-radius: 14px;
  padding: 16px 18px; display: flex; flex-direction: column; gap: 11px;
  transition: transform .25s cubic-bezier(.2,.7,.3,1), border-color .25s, box-shadow .25s;
}
.tc-card:hover { transform: translateY(-4px); border-color: rgba(43,177,135,.5); box-shadow: 0 12px 28px -10px rgba(31,166,122,.35); }
.tc-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 8px; }
.tc-title { display: flex; flex-direction: column; gap: 5px; min-width: 0; }
.tc-title b { font-size: 15px; color: var(--text); }
.tc-status { flex-shrink: 0; font-size: 12px; font-weight: 700; padding: 2px 9px; border-radius: 999px; }
.tc-status.ok { color: #059669; background: rgba(16,185,129,.12); }

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
.tc-stat b { font-size: 15px; color: var(--text); font-variant-numeric: tabular-nums; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tc-stat span { font-size: 12px; color: var(--text-light); }

.tc-meta { font-size: 12px; color: var(--text-light); display: flex; gap: 12px; flex-wrap: wrap; }
.tc-foot { display: flex; justify-content: flex-end; gap: 8px; }

@media (max-width: 760px) {
  .tc-grid { grid-template-columns: 1fr; }
  .tc-stats { grid-template-columns: repeat(4, 1fr); }
  .tc-stat { padding: 6px; }
  .tc-stat span { font-size: 12px; }
}
</style>
