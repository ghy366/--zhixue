<template>
  <div class="page-wrap stats-page liquid">
    <div class="stats-grid">
      <div class="stat-card"><div class="stat-icon blue" v-html="svgIcon('book')" /><div><div class="stat-value">{{ courses.length }}</div><div class="stat-label">{{ t('教学班（平行班）') }}</div></div></div>
      <div class="stat-card"><div class="stat-icon green" v-html="svgIcon('check-circle')" /><div><div class="stat-value">{{ gradedCount }}</div><div class="stat-label">{{ t('已批改') }}</div></div></div>
      <div class="stat-card"><div class="stat-icon amber" v-html="svgIcon('hourglass')" /><div><div class="stat-value">{{ pendingCount }}</div><div class="stat-label">{{ t('待批改') }}</div></div></div>
    </div>
    <div class="section">
      <div class="section-header">
        <div class="section-title">{{ t('选择教学班') }}</div>
        <div class="tab-group">
          <button v-for="c in courses" :key="c.id" class="tab-btn" :class="{active: selectedCourse?.id === c.id}" @click="selectCourse(c)">{{ c.courseName }} · {{ c.classNo }}</button>
        </div>
      </div>
      <div v-if="selectedCourse">
        <!-- ===== 正考 / 补考 / 重修：三份名单来源不同，必须分开录分 ===== -->
        <div class="gm-batch-note" style="padding: 4px 0 10px">
          {{ t('名单：本教学班在读学生（{n} 人）· 挂科学生可在行内选择按补考/重修录分', { n: students.length }) }}
        </div>

        <!-- ===== 成绩录入进度 ===== -->
        <div class="gm-progress" v-if="students.length && batch === 'normal'">
          <div class="gm-progress-head">
            <span>{{ t('成绩录入进度 · {n}', { n: selectedCourse.courseName + ' · ' + selectedCourse.classNo }) }}</span>
            <b>{{ t('{g} / {s} 人（{p}%）', { g: gradedStudents.length, s: students.length, p: gradePct }) }}</b>
          </div>
          <div class="gm-progress-track">
            <i :style="{ width: gradePct + '%' }" :class="{ done: gradePct === 100 }" />
          </div>
          <div class="gm-progress-tip" :class="{ ok: gradePct === 100 }">
            {{ gradePct === 100 ? t('全部录入完成 · 下方可查看成绩分布') : t('还有 {n} 人未录入总评', { n: students.length - gradedStudents.length }) }}
          </div>
        </div>

        <!-- ===== 成绩分析 ===== -->
        <div class="gm-analysis" v-if="gradedStudents.length && batch === 'normal'">
          <div class="gm-kpis">
            <div class="gm-kpi"><b>{{ avgTotal }}</b><span>{{ t('平均总评') }}</span></div>
            <div class="gm-kpi ok"><b>{{ passRate }}%</b><span>{{ t('及格率') }}</span></div>
            <div class="gm-kpi amber"><b>{{ excellentRate }}%</b><span>{{ t('优秀率（90+）') }}</span></div>
          </div>
          <div class="gm-charts">
            <div class="gm-chart">
              <div class="gm-chart-title">{{ t('总评分布 · 10 分一段') }}</div>
              <svg viewBox="0 0 460 205" role="img" :aria-label="t('总评分布直方图')">
                <line v-for="t in [0, 25, 50, 75, 100]" :key="'gy' + t" :x1="G_LEFT" :x2="460 - 14" :y1="gy(t)" :y2="gy(t)" class="gm-grid" />
                <text v-for="t in [0, 25, 50, 75, 100]" :key="'gt' + t" :x="G_LEFT - 6" :y="gy(t) + 3" text-anchor="end" class="gm-axis">{{ t }}</text>
                <rect
                  v-for="(b, i) in gradeDist" :key="'gb' + i"
                  :x="gx(i * 10) + 3" :y="gy(b.count)"
                  :width="(460 - G_LEFT - 14) / 10 - 6" :height="Math.max(b.count ? 3 : 0, gy(0) - gy(b.count))"
                  rx="3" class="gm-bar" :class="i < 6 ? 'low' : i >= 9 ? 'top' : 'mid'"
                >
                  <title>{{ t('{a}–{b} 分 · {c} 人', { a: i * 10, b: i === 9 ? 100 : i * 10 + 9, c: b.count }) }}</title>
                </rect>
                <text v-for="t in [0, 20, 40, 60, 80, 100]" :key="'gx' + t" :x="gx(t)" :y="205 - 8" text-anchor="middle" class="gm-axis">{{ t }}</text>
                <line :x1="gx(60)" :x2="gx(60)" :y1="12" :y2="gy(0)" class="gm-refline rose" />
                <text :x="gx(60) + 3" y="10" class="gm-refcap rose">{{ t('及格') }}</text>
                <line :x1="gx(90)" :x2="gx(90)" :y1="12" :y2="gy(0)" class="gm-refline gold" />
                <text :x="gx(90) + 3" y="10" class="gm-refcap gold">{{ t('优秀') }}</text>
              </svg>
            </div>
            <div class="gm-chart" v-if="scorePairs.length">
              <div class="gm-chart-title">{{ t('平时分 × 期末分 · 虚线为两者相等') }}</div>
              <svg viewBox="0 0 460 205" role="img" :aria-label="t('平时分与期末分散点图')">
                <line v-for="t in [0, 25, 50, 75, 100]" :key="'sy' + t" :x1="S_PAD" :x2="460 - 14" :y1="sy(t)" :y2="sy(t)" class="gm-grid" />
                <line v-for="t in [0, 25, 50, 75, 100]" :key="'sx' + t" :x1="sxx(t)" :x2="sxx(t)" :y1="12" :y2="205 - 30" class="gm-grid" />
                <text v-for="t in [0, 50, 100]" :key="'st' + t" :x="S_PAD - 6" :y="sy(t) + 3" text-anchor="end" class="gm-axis">{{ t }}</text>
                <text v-for="t in [0, 50, 100]" :key="'sx2' + t" :x="sxx(t)" :y="205 - 12" text-anchor="middle" class="gm-axis">{{ t }}</text>
                <text :x="460 - 14" :y="205 - 12" text-anchor="end" class="gm-axis-label">{{ t('平时分 →') }}</text>
                <text :x="S_PAD" y="10" class="gm-axis-label">{{ t('期末分 ↑') }}</text>
                <line :x1="sxx(0)" :y1="sy(0)" :x2="sxx(100)" :y2="sy(100)" class="gm-diag" />
                <g v-for="(p, i) in scorePairs" :key="'sp' + i" class="gm-dot" :class="p.total >= 60 ? 'ok' : 'bad'">
                  <circle :cx="sxx(p.regular)" :cy="sy(p.final)" r="4.2" />
                  <title>{{ t('{n} · 平时 {r} · 期末 {f} · 总评 {s}', { n: p.name, r: p.regular, f: p.final, s: p.total }) }}</title>
                </g>
              </svg>
            </div>
            <div class="gm-chart gm-empty" v-else>
              <p>{{ t('平时分与期末分都录入后，这里会展示两者的对照散点') }}</p>
            </div>
          </div>
        </div>

        <div class="data-table">
          <table>
            <thead>
              <tr><th>{{ t('学号') }}</th><th>{{ t('姓名') }}</th><th>{{ t('专业') }}</th><th>{{ t('平时成绩') }}</th><th>{{ t('期末成绩') }}</th><th>{{ t('总评') }}</th><th>{{ t('性质') }}</th><th>{{ t('操作') }}</th></tr>
            </thead>
            <tbody>
              <tr v-for="s in students" :key="s.studentId">
                <td>{{ s.studentNo }}</td>
                <td>{{ s.studentName }}</td>
                <td>{{ s.studentMajor }}</td>
                <td>
                  <el-input v-if="editingId === s.studentId" v-model.number="editRegular" size="small" style="width:80px" type="number" min="0" max="100" />
                  <span v-else>{{ fmt(s.regularScore) }}</span>
                </td>
                <td>
                  <el-input v-if="editingId === s.studentId" v-model.number="editFinal" size="small" style="width:80px" type="number" min="0" max="100" />
                  <span v-else>{{ fmt(s.finalScore) }}</span>
                </td>
                <td><b>{{ fmt(s.totalScore) }}</b></td>
                <td>
                  <template v-if="batch !== 'normal'">
                    <span class="gm-etag" :class="batch">{{ batch === 'makeup' ? t('补考') : t('重修') }}</span>
                    <span v-if="s.flowStatus" class="gm-flow">{{ flowText(s.flowStatus) }}</span>
                  </template>
                  <template v-else-if="editingId === s.studentId && isFailedRow(s)">
                    <div class="gm-exam-type">
                      <label :class="{ on: editExamType === 'makeup' }"><input type="radio" value="makeup" v-model="editExamType" /> {{ t('补考') }}</label>
                      <label :class="{ on: editExamType === 'retake' }"><input type="radio" value="retake" v-model="editExamType" /> {{ t('重修') }}</label>
                    </div>
                  </template>
                  <template v-else>
                    <span v-if="s.examType === 'makeup'" class="gm-etag makeup">{{ t('补考') }}<template v-if="s.attempt > 1"> · 第{{ s.attempt }}次</template></span>
                    <span v-else-if="s.examType === 'retake'" class="gm-etag retake">{{ t('重修') }}<template v-if="s.attempt > 1"> · 第{{ s.attempt }}次</template></span>
                    <span v-else-if="isFailedRow(s)" class="gm-etag todo">{{ t('待补考') }}</span>
                    <span v-else class="muted">—</span>
                  </template>
                </td>
                <td>
                  <template v-if="editingId === s.studentId">
                    <button class="btn btn-success btn-sm" @click="saveGrade(s)">{{ t('保存') }}</button>
                    <button class="btn btn-ghost btn-sm" @click="editingId = null">{{ t('取消') }}</button>
                  </template>
                  <button v-else class="btn btn-outline btn-sm" @click="startEdit(s)">
                    {{ batch === 'makeup' ? t('录入补考成绩') : batch === 'retake' ? t('录入重修成绩')
                      : s.totalScore != null ? (isFailedRow(s) ? t('录补考/重修') : t('批改')) : t('录入成绩') }}
                  </button>
                </td>
              </tr>
              <tr v-if="!students.length"><td colspan="8" style="text-align:center;color:var(--text-light);padding:24px">
                {{ batch === 'makeup' ? t('该课程暂无补考学生') : batch === 'retake' ? t('该课程暂无重修学生') : t('该课程暂无学生') }}
              </td></tr>
            </tbody>
          </table>
        </div>
      </div>
      <div v-else class="empty-state">
        <div class="empty-icon" v-html="svgIcon('edit')" />
        <div class="empty-title">{{ t('请选择一门课程') }}</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '@/api'
import { useAuthStore } from '@/stores/auth'
import { t } from '@/i18n'

const auth = useAuthStore()
const route = useRoute()
const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`

const courses = ref<any[]>([])
const students = ref<any[]>([])
const selectedCourse = ref<any>(null)
const editingId = ref<number | null>(null)
const editRegular = ref<number | null>(null)
const editFinal = ref<number | null>(null)
/** 已挂科记录再录入 = 补考 / 重修（真实高校：先补考，不过再重修）；正常成绩留空 */
const editExamType = ref('')

/** 正考 / 补考 / 重修：名单来源不同（已录取 / 补考册 / 重修录取），必须分开录分 */
const batch = ref<'normal' | 'makeup' | 'retake'>('normal')
const batchTabs = [
  { k: 'normal', label: '正考' },
  { k: 'makeup', label: '补考' },
  { k: 'retake', label: '重修' },
]
const makeupCap = ref(60)
function switchBatch(k: 'normal' | 'makeup' | 'retake') {
  if (batch.value === k) return
  batch.value = k
  editingId.value = null
  students.value = []
  loadStudents()
}
function flowText(s: string) {
  const map: Record<string, string> = {
    ELIGIBLE: '待报名', REGISTERED: '已报名', SCHEDULED: '已排考', APPLIED: '已报名',
    APPROVED: '已安排', ENROLLED: '已进班', PASSED: '已通过', FAILED: '未通过',
    ABSENT: '缺考', CANCELLED: '已取消',
  }
  return t(map[s] || s)
}

/** 挂科行：已录入总评且 < 60（补考/重修录入对象） */
function isFailedRow(s: any): boolean {
  return s.totalScore != null && Number(s.totalScore) < 60
}

function fmt(v: any) { return v != null ? v : '—' }
const gradedCount = computed(() => students.value.filter(s => s.totalScore != null).length)
const pendingCount = computed(() => students.value.filter(s => s.totalScore == null).length)

/* ===== 成绩分析 ===== */
const gradedStudents = computed(() => students.value.filter(s => s.totalScore != null))
const avgTotal = computed(() => {
  const g = gradedStudents.value
  return g.length ? (g.reduce((s, x) => s + (Number(x.totalScore) || 0), 0) / g.length).toFixed(1) : '—'
})
const passRate = computed(() => {
  const g = gradedStudents.value
  return g.length ? Math.round(g.filter(x => Number(x.totalScore) >= 60).length * 100 / g.length) : 0
})
const excellentRate = computed(() => {
  const g = gradedStudents.value
  return g.length ? Math.round(g.filter(x => Number(x.totalScore) >= 90).length * 100 / g.length) : 0
})
/* 录入进度 */
const gradePct = computed(() =>
  students.value.length ? Math.round(gradedStudents.value.length * 100 / students.value.length) : 0
)
const gradeDist = computed(() => {
  const buckets = new Array(10).fill(0)
  for (const s of gradedStudents.value) {
    const t = Math.max(0, Math.min(100, Number(s.totalScore) || 0))
    buckets[Math.min(9, Math.floor(t / 10))]++
  }
  return buckets.map((count, i) => ({ low: i * 10, count }))
})
const scorePairs = computed(() =>
  students.value
    .filter(s => s.regularScore != null && s.finalScore != null)
    .map(s => ({
      name: s.studentName, regular: Number(s.regularScore), final: Number(s.finalScore), total: s.totalScore,
    }))
)

const G_LEFT = 32
const gx = (v: number) => G_LEFT + (v / 100) * (460 - G_LEFT - 14)
const gy = (c: number) => {
  const max = Math.max(1, ...gradeDist.value.map(b => b.count))
  return 190 - (c / max) * (190 - 18) - 15
}
const S_PAD = 30
const sxx = (v: number) => S_PAD + (v / 100) * (460 - S_PAD - 14)
const sy = (v: number) => 190 - (v / 100) * (190 - 30) - 12

async function loadCourses() {
  try {
    // 我的教学班（平行班）：成绩按班录入,名单 = 挂班在读学生
    const r: any = await http.get('/teaching-classes', { params: { teacherId: auth.user?.id } })
    courses.value = r?.data || []
    if (courses.value.length && !selectedCourse.value) {
      // 支持从"学生名册"页带 classId 跳转过来直接定位班级
      const qc = Number(route.query.classId)
      const target = courses.value.find(c => c.id === qc)
      selectedCourse.value = target || courses.value[0]
      await loadStudents()
    }
  } catch {}
}
async function loadStudents() {
  if (!selectedCourse.value) return
  try {
    const r: any = await http.get(`/grades/course/${selectedCourse.value.courseId}`, { params: { classId: selectedCourse.value.id } })
    students.value = r?.data || []
  } catch { students.value = [] }
}
function selectCourse(c: any) { selectedCourse.value = c; editingId.value = null; batch.value = 'normal'; loadStudents() }
function startEdit(s: any) {
  editingId.value = s.studentId
  editRegular.value = s.regularScore ?? null
  editFinal.value = s.finalScore ?? null
  editExamType.value = batch.value !== 'normal' ? batch.value : (isFailedRow(s) ? 'makeup' : '')
}
function validScore(v: any): boolean {
  if (v === null || v === '' || v === undefined) return true // 允许留空 = 未录入
  const n = Number(v)
  return !isNaN(n) && n >= 0 && n <= 100
}
async function saveGrade(s: any) {
  if (!validScore(editRegular.value) || !validScore(editFinal.value)) {
    ElMessage.warning(t('成绩需为 0–100 的数字，留空表示未录入'))
    return
  }
  // 补考 tab 强制 makeup；重修 tab 强制 retake；正考 tab 按挂科行手动选择
  const examType = batch.value !== 'normal' ? batch.value : (editExamType.value || undefined)
  const raw = (Number(editRegular.value || 0) * 0.4) + (Number(editFinal.value || 0) * 0.6)
  if (examType === 'makeup' && raw > makeupCap.value) {
    ElMessage.warning(t('补考总评 {r} 超过封顶 {c} 分，保存后将按 {c} 分折算', { r: raw.toFixed(1), c: makeupCap.value }))
  }
  try {
    const r: any = await http.post('/grades', {
      studentId: s.studentId,
      courseId: selectedCourse.value.courseId,
      classId: selectedCourse.value.id,
      regularScore: editRegular.value === '' ? null : editRegular.value,
      finalScore: editFinal.value === '' ? null : editFinal.value,
      examType,
    })
    ElMessage.success(r?.message || (examType
      ? (examType === 'makeup' ? t('补考成绩已保存') : t('重修成绩已保存'))
      : t('成绩已保存')))
    editingId.value = null
    await loadStudents()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || t('保存失败'))
  }
}

async function loadConfig() {
  try {
    const r: any = await http.get('/system/config')
    const c = r?.data || {}
    if (c.makeupScoreCap != null && Number(c.makeupScoreCap) > 0) makeupCap.value = Number(c.makeupScoreCap)
  } catch {}
}

onMounted(() => { loadConfig(); loadCourses() })
</script>

<style scoped>
.page-wrap { padding: 4px 0; }
.tab-group { display: flex; gap: 6px; flex-wrap: wrap; }
.tab-btn { padding: 6px 14px; border-radius: 999px; border: 1px solid var(--border); background: transparent; color: var(--text-light); font-size: 13px; cursor: pointer; transition: all .15s; }
.tab-btn:hover { color: var(--text); }
.tab-btn.active { background: var(--primary-deep); color: #fff; border-color: var(--primary-deep); }

/* 正考 / 补考 / 重修 批次切换 */
.gm-batch-tabs { display: flex; align-items: center; gap: 8px; margin: 4px 0 16px; flex-wrap: wrap; }
.gm-batch-btn { padding: 6px 18px; border-radius: 999px; border: 1px solid var(--border); background: transparent; color: var(--text-light); font-size: 13px; cursor: pointer; transition: all .15s; }
.gm-batch-btn:hover { color: var(--text); }
.gm-batch-btn.active { background: var(--primary-deep); color: #fff; border-color: var(--primary-deep); }
.gm-batch-note { font-size: 12px; color: var(--text-lighter); }
.gm-flow { margin-left: 6px; font-size: 11px; color: var(--text-lighter); }
.empty-state { text-align: center; padding: 60px 20px; color: var(--text-light); }
.empty-icon { font-size: 48px; opacity: 0.3; margin-bottom: 10px; }

/* ===== 成绩分析面板 ===== */
.gm-analysis { margin-bottom: 16px; padding: 14px 16px; border: 1px solid var(--border); border-radius: 12px; background: color-mix(in srgb, var(--card-bg) 55%, transparent); }

/* ===== 录入进度 ===== */
.gm-progress {
  margin-bottom: 14px; padding: 12px 16px;
  border: 1px solid var(--border); border-radius: 12px;
  background: color-mix(in srgb, var(--card-bg) 55%, transparent);
}
.gm-progress-head { display: flex; justify-content: space-between; align-items: center; gap: 10px; font-size: 13px; color: var(--text-light); margin-bottom: 8px; flex-wrap: wrap; }
.gm-progress-head b { color: var(--text); font-variant-numeric: tabular-nums; }
.gm-progress-track { height: 10px; border-radius: 99px; background: rgba(148,163,184,.16); overflow: hidden; }
.gm-progress-track i { display: block; height: 100%; border-radius: 99px; background: linear-gradient(90deg, #2bb187, #38bdf8); transition: width .6s cubic-bezier(.22,.9,.36,1); }
.gm-progress-track i.done { background: linear-gradient(90deg, #10b981, #34d399); }
.gm-progress-tip { margin-top: 6px; font-size: 12px; color: #f59e0b; }
.gm-progress-tip.ok { color: #34d399; }
.gm-kpis { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; margin-bottom: 12px; }
.gm-kpi { padding: 9px 12px; border: 1px solid var(--border); border-radius: 10px; display: flex; flex-direction: column; gap: 1px; }
.gm-kpi b { font-size: 16px; color: var(--text); font-variant-numeric: tabular-nums; }
.gm-kpi span { font-size: 12px; color: var(--text-light); }
.gm-kpi.ok b { color: #059669; }
.gm-kpi.amber b { color: #d97706; }
.gm-charts { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
@media (max-width: 900px) { .gm-charts { grid-template-columns: 1fr; } .gm-kpis { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
.gm-chart svg { width: 100%; height: auto; display: block; }
.gm-chart-title { font-size: 12px; font-weight: 700; color: var(--text-light); margin-bottom: 4px; }
.gm-grid { stroke: rgba(148,163,184,.2); stroke-width: 1; }
.gm-axis { fill: var(--text-light); font-size: 12px; font-variant-numeric: tabular-nums; }
.gm-axis-label { fill: var(--text-light); font-size: 12px; font-weight: 600; }
.gm-bar { opacity: .85; }
.gm-bar.low { fill: rgba(244,63,94,.5); }
.gm-bar.mid { fill: rgba(43,177,135,.55); }
.gm-bar.top { fill: rgba(245,158,11,.6); }
.gm-refline { stroke-width: 1.2; stroke-dasharray: 4 3; }
.gm-refline.rose { stroke: rgba(244,63,94,.62); }
.gm-refline.gold { stroke: rgba(245,158,11,.66); }
.gm-refcap { font-size: 12px; font-weight: 700; }
.gm-refcap.rose { fill: #f43f5e; }
.gm-refcap.gold { fill: #f59e0b; }
.gm-diag { stroke: rgba(148,163,184,.55); stroke-width: 1.2; stroke-dasharray: 5 4; }
.gm-dot circle { opacity: .85; stroke: rgba(255,255,255,.6); stroke-width: .8; }
.gm-dot.ok circle { fill: #34d399; }
.gm-dot.bad circle { fill: #fb7185; }
.gm-empty { display: flex; align-items: center; justify-content: center; min-height: 160px; border: 1px dashed var(--border); border-radius: 10px; }
.gm-empty p { font-size: 12px; color: var(--text-light); margin: 0; padding: 20px; text-align: center; }
/* 成绩分析面板样式 v2 */

/* ===== 性质列 + 补考/重修录入 ===== */
.gm-etag { display: inline-block; font-size: 11.5px; font-weight: 700; padding: 2px 9px; border-radius: 999px; white-space: nowrap; }
.gm-etag.makeup { color: #b45309; background: rgba(217,119,6,.12); border: 1px solid rgba(217,119,6,.35); }
.gm-etag.retake { color: #be123c; background: rgba(190,18,60,.09); border: 1px solid rgba(190,18,60,.35); }
.gm-etag.todo { color: #64748b; background: rgba(100,116,139,.1); border: 1px dashed rgba(100,116,139,.4); }
html.dark .gm-etag.makeup { color: #fbbf24; }
html.dark .gm-etag.retake { color: #fb7185; }
html.dark .gm-etag.todo { color: #94a3b8; }
.gm-exam-type { display: flex; align-items: center; gap: 8px; }
.gm-exam-type label {
  display: inline-flex; align-items: center; gap: 4px; cursor: pointer;
  font-size: 12px; font-weight: 600; color: var(--text-light);
  padding: 2px 9px; border-radius: 999px; border: 1px solid var(--border); transition: all .15s;
}
.gm-exam-type label.on { color: var(--primary); border-color: var(--primary); background: rgba(43,177,135,.08); }
.gm-exam-type input { accent-color: var(--primary); margin: 0; }
</style>
