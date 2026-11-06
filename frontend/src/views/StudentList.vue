<template>
  <div class="page-wrap stats-page liquid">
    <div class="stats-grid">
      <div class="stat-card"><div class="stat-icon green" v-html="svgIcon('check-circle')" /><div><div class="stat-value">{{ approvedCount }}</div><div class="stat-label">{{ t('已录取') }}</div></div></div>
      <div class="stat-card"><div class="stat-icon amber" v-html="svgIcon('hourglass')" /><div><div class="stat-value">{{ pendingCount }}</div><div class="stat-label">{{ t('待录取') }}</div></div></div>
      <div class="stat-card"><div class="stat-icon blue" v-html="svgIcon('users')" /><div><div class="stat-value">{{ totalCount }}</div><div class="stat-label">{{ t('总报名') }}</div></div></div>
    </div>

    <!-- 课程选择器 -->
    <div class="section">
      <div class="section-header">
        <div class="section-title">{{ t('选择课程') }}</div>
        <div class="tab-group">
          <button v-for="c in courses" :key="c.id" class="tab-btn"
            :class="{active: selectedCourse?.id === c.id}" @click="selectCourse(c)">
            {{ c.name }}
          </button>
        </div>
      </div>

      <div v-if="selectedCourse && currentStudents.length">
        <!-- ===== 学生画像 ===== -->
        <div class="sl-profile" v-if="totalCount">
          <div class="sl-panel">
            <div class="sl-title">{{ t('投分分布') }} <small>{{ t('平均 {a} 分 · 最高 {m} 分', { a: avgPoints, m: maxPoints }) }}</small></div>
            <div v-if="pointBuckets.length" class="sl-points">
              <div v-for="b in pointBuckets" :key="b.label" class="sl-point-row">
                <span class="sl-point-label">{{ b.label }}</span>
                <div class="sl-point-track"><i :style="{ width: Math.max(b.count ? 5 : 0, b.count / maxBucket * 100) + '%' }" /></div>
                <span class="sl-point-val">{{ b.count }}</span>
              </div>
            </div>
            <div v-else class="sl-nodata">{{ t('暂无投分数据') }}</div>
          </div>
          <div class="sl-panel">
            <div class="sl-title">{{ t('年级构成') }} <small>{{ t('共 {n} 名学生', { n: totalCount }) }}</small></div>
            <div v-if="gradeRows.length" class="sl-grades">
              <div v-for="g in gradeRows" :key="g.label" class="sl-point-row">
                <span class="sl-point-label">{{ g.label }}</span>
                <div class="sl-point-track"><i class="violet" :style="{ width: Math.max(g.count ? 5 : 0, g.count / gradeMax * 100) + '%' }" /></div>
                <span class="sl-point-val">{{ t('{n} 人', { n: g.count }) }}</span>
              </div>
            </div>
            <div v-else class="sl-nodata">{{ t('学生资料未填写年级') }}</div>
          </div>
        </div>

        <div class="data-table">
          <table>
            <thead>
              <tr><th>{{ t('学号') }}</th><th>{{ t('姓名') }}</th><th>{{ t('专业') }}</th><th>{{ t('班级') }}</th><th>{{ t('状态') }}</th><th>{{ t('操作') }}</th></tr>
            </thead>
          <tbody>
            <tr v-for="s in currentStudents" :key="s.enrollmentId">
              <td>{{ s.studentNo }}</td>
              <td>
                <button class="stu-name" @click="openProfile(s)" :title="t('查看学生画像')">{{ s.studentName }}</button>
              </td>
              <td>{{ s.studentMajor || '-' }}</td>
              <td>{{ s.studentClass || '-' }}</td>
              <td>
                <span class="status-tag" :class="statusCls(s.status)">{{ statusText(s.status) }}</span>
              </td>
              <td>
                <button v-if="s.status === 'approved'" class="btn btn-primary btn-sm" @click="goGrade(s)">{{ t('录入成绩') }}</button>
                <span v-else class="btn btn-ghost btn-sm" style="cursor:not-allowed;color:var(--text-light)">{{ t('待结算') }}</span>
              </td>
            </tr>
          </tbody>
          </table>
        </div>
      </div>
      <div v-else class="empty-state">
        <div class="empty-icon" v-html="svgIcon('users')" />
        <div class="empty-title">{{ selectedCourse ? t('该课程暂无选课学生') : t('请选择一门课程') }}</div>
      </div>
    </div>

    <!-- ===== 学生画像弹窗 ===== -->
    <el-dialog v-model="profileVisible" :title="profile ? t('{n} 的学生画像', { n: profile.student.name }) : t('学生画像')" width="760px" align-center class="spf-dialog" @opened="resetProfileScroll">
      <div v-loading="profileLoading" style="min-height:200px">
        <template v-if="profile">
          <!-- 头部信息 + 预警 -->
          <div class="spf-head">
            <div class="spf-avatar">{{ (profile.student.name || '?').charAt(0) }}</div>
            <div class="spf-info">
              <b>{{ profile.student.name }}</b>
              <span>{{ profile.student.no }} · {{ profile.student.major || '—' }}<template v-if="profile.student.className"> · {{ profile.student.className }}</template><template v-if="profile.student.grade"> · {{ t('{n} 级', { n: profile.student.grade }) }}</template></span>
            </div>
            <div class="spf-warnings">
              <span v-for="(w, i) in profile.warnings" :key="i" class="spf-warn" :class="w.level">{{ w.text }}</span>
              <span v-if="!profile.warnings.length" class="spf-warn ok">{{ t('选课行为稳定') }}</span>
            </div>
          </div>

          <!-- 毕业进度 + 学分 -->
          <div class="spf-row">
            <div class="spf-block">
              <div class="spf-block-title">{{ t('毕业进度') }} <span v-if="nearGraduation" class="spf-near">{{ t('接近毕业') }}</span></div>
              <div class="spf-grad">
                <div class="spf-grad-bar">
                  <i :style="{ width: Math.min(100, profile.graduation.total?.percent || 0) + '%' }" />
                </div>
                <div class="spf-grad-text">
                  <b>{{ profile.graduation.total?.percent || 0 }}%</b>
                  {{ t('已获 {e} / {r} 学分', { e: profile.graduation.total?.earned || 0, r: profile.graduation.total?.required || 0 }) }}
                  <em v-if="profile.graduation.total?.gap">{{ t('还差 {n}', { n: profile.graduation.total.gap }) }}</em>
                  <em v-else class="ok">{{ t('已达标') }}</em>
                </div>
              </div>
            </div>
            <div class="spf-block">
              <div class="spf-block-title">{{ t('学分与成绩') }}</div>
              <div class="spf-stats">
                <div class="spf-stat"><b>{{ profile.stats.totalCredits }}</b><span>{{ t('总学分') }}</span></div>
                <div class="spf-stat"><b>{{ profile.stats.semesterCredits }}</b><span>{{ t('本学期') }}</span></div>
                <div class="spf-stat"><b>{{ profile.stats.avgScore ?? '—' }}</b><span>{{ t('平均分') }}</span></div>
                <div class="spf-stat"><b>{{ profile.stats.gpa ?? '—' }}</b><span>GPA</span></div>
                <div class="spf-stat"><b :class="{ warn: profile.stats.dropped }">{{ profile.stats.dropped }}</b><span>{{ t('退课') }}</span></div>
                <div class="spf-stat"><b :class="{ warn: profile.stats.waitlist }">{{ profile.stats.waitlist }}</b><span>{{ t('候补') }}</span></div>
              </div>
            </div>
          </div>

          <!-- 成绩分布 -->
          <div class="spf-block" style="margin-top:12px">
            <div class="spf-block-title">{{ t('成绩分布') }} <small>{{ t('已公布成绩 {n} 门', { n: scoredCount }) }}</small></div>
            <div class="spf-dist">
              <div v-for="(b, i) in profile.gradeDist" :key="i" class="spf-dist-col">
                <span class="spf-dist-num">{{ b.count || '' }}</span>
                <div class="spf-dist-bar"><i :style="{ height: distH(b.count) + 'px' }" :class="i < 6 ? 'low' : i >= 9 ? 'top' : 'mid'" /></div>
                <small>{{ b.low }}+</small>
              </div>
            </div>
          </div>

          <!-- 已修课程列表 -->
          <div class="spf-block" style="margin-top:12px">
            <div class="spf-block-title">{{ t('修读课程（{n}）', { n: profile.courses.length }) }}</div>
            <div class="spf-courses">
              <table>
                <thead><tr><th>{{ t('课程') }}</th><th>{{ t('类型') }}</th><th>{{ t('学分') }}</th><th>{{ t('轮次') }}</th><th>{{ t('投分') }}</th><th>{{ t('成绩') }}</th><th>{{ t('状态') }}</th></tr></thead>
                <tbody>
                  <tr v-for="c in profile.courses" :key="c.enrollmentId">
                    <td>{{ c.courseName }}</td>
                    <td>{{ c.category || '—' }}</td>
                    <td>{{ c.credit }}</td>
                    <td>{{ t('第 {n} 轮', { n: c.round }) }}</td>
                    <td>{{ c.points ?? 0 }}</td>
                    <td><b :class="scoreCls(c.totalScore)">{{ c.totalScore ?? '—' }}</b></td>
                    <td>{{ statusText(c.status) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </template>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import http from '@/api'
import { useAuthStore } from '@/stores/auth'
import { t } from '@/i18n'

const auth = useAuthStore()
const router = useRouter()
const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`

const courses = ref<any[]>([])
const selectedCourse = ref<any>(null)
const currentStudents = ref<any[]>([])

function statusCls(s: string) {
  if (s === 'approved') return 'status-approved'
  if (s === 'pending') return 'status-pending'
  return 'status-rejected'
}
function statusText(s: string) {
  if (s === 'approved') return t('已录取')
  if (s === 'pending') return t('待录取')
  return t('未录取')
}

const approvedCount = computed(() => currentStudents.value.filter(s => s.status === 'approved').length)
const pendingCount = computed(() => currentStudents.value.filter(s => s.status !== 'approved').length)
const totalCount = computed(() => currentStudents.value.length)

/* ===== 学生画像 ===== */
const pointBuckets = computed(() => {
  if (!currentStudents.value.some(s => s.points != null)) return []
  const edges: Array<[number, number, string]> = [
    [0, 9, t('0-9 分')], [10, 19, t('10-19 分')], [20, 29, t('20-29 分')], [30, 39, t('30-39 分')], [40, 100, t('40 分+')],
  ]
  return edges.map(([lo, hi, label]) => ({
    label,
    count: currentStudents.value.filter(s => {
      const p = Number(s.points) || 0
      return p >= lo && p <= hi
    }).length,
  }))
})
const maxBucket = computed(() => Math.max(1, ...pointBuckets.value.map(b => b.count)))
const avgPoints = computed(() => {
  const pts = currentStudents.value.filter(s => s.points != null).map(s => Number(s.points))
  return pts.length ? (pts.reduce((a, b) => a + b, 0) / pts.length).toFixed(1) : '—'
})
const maxPoints = computed(() => {
  const pts = currentStudents.value.filter(s => s.points != null).map(s => Number(s.points))
  return pts.length ? Math.max(...pts) : '—'
})
const gradeRows = computed(() => {
  const map = new Map<string, number>()
  for (const s of currentStudents.value) {
    if (s.studentGrade == null) continue
    const label = t('{n} 级', { n: s.studentGrade })
    map.set(label, (map.get(label) || 0) + 1)
  }
  return Array.from(map.entries())
    .map(([label, count]) => ({ label, count }))
    .sort((a, b) => b.label.localeCompare(a.label))
})
const gradeMax = computed(() => Math.max(1, ...gradeRows.value.map(g => g.count)))

/* ===== 学生画像弹窗 ===== */
const profileVisible = ref(false)
const profileLoading = ref(false)
const profile = ref<any>(null)
const scoredCount = computed(() => (profile.value?.courses || []).filter((c: any) => c.totalScore != null).length)
const nearGraduation = computed(() => (profile.value?.graduation?.total?.percent || 0) >= 85)
const distMax = computed(() => Math.max(1, ...(profile.value?.gradeDist || []).map((b: any) => b.count)))
function distH(count: number) {
  return count ? Math.max(4, Math.round(count * 42 / distMax.value)) : 0
}
function scoreCls(v: any) {
  if (v == null) return ''
  const n = Number(v)
  if (n >= 90) return 'gold'
  if (n >= 80) return 'good'
  if (n >= 60) return 'mid'
  return 'bad'
}
async function openProfile(s: any) {
  profileVisible.value = true
  profileLoading.value = true
  profile.value = null
  try {
    const r: any = await http.get(`/students/${s.studentId}/profile`)
    profile.value = r?.data || null
  } catch (e: any) {
    ElMessage.error(e?.message || t('学生画像加载失败'))
    profileVisible.value = false
  } finally {
    profileLoading.value = false
  }
}
/** element-plus 打开时会滚动到聚焦元素，把对话框拉出视口——打开后重置滚动 */
function resetProfileScroll() {
  const body = document.querySelector('.spf-dialog .el-dialog__body')
  if (body) body.scrollTop = 0
  const overlay = document.querySelector('.el-overlay-dialog')
  if (overlay) overlay.scrollTop = 0
}

async function loadCourses() {
  try {
    const r: any = await http.get(`/courses/teacher/${auth.user?.id}`)
    courses.value = r?.data || []
    if (courses.value.length) selectCourse(courses.value[0])
  } catch {}
}

async function selectCourse(c: any) {
  selectedCourse.value = c
  try {
    const r: any = await http.get(`/enrollments/course/${c.id}`)
    currentStudents.value = r?.data || []
  } catch { currentStudents.value = [] }
}

function goGrade(s: any) {
  router.push({ name: 'GradeMgr', query: { courseId: selectedCourse.value.id } })
}

onMounted(loadCourses)
</script>

<style scoped>
.page-wrap { padding: 4px 0; }
.tab-group { display: flex; gap: 6px; flex-wrap: wrap; }
.tab-btn { padding: 6px 14px; border-radius: 999px; border: 1px solid var(--border); background: transparent; color: var(--text-light); font-size: 13px; cursor: pointer; transition: all .15s; }
.tab-btn:hover { color: var(--text); }
.tab-btn.active { background: var(--primary-deep); color: #fff; border-color: var(--primary-deep); }
.status-tag { font-size: 12px; padding: 1px 8px; border-radius: 999px; }
.status-approved { background: rgba(16,185,129,0.15); color: #10b981; }
.status-pending { background: rgba(245,158,11,0.15); color: #f59e0b; }
.status-rejected { background: rgba(239,68,68,0.15); color: #ef4444; }
.empty-state { text-align: center; padding: 60px 20px; color: var(--text-light); }
.empty-icon { font-size: 48px; opacity: 0.3; margin-bottom: 10px; }

/* ===== 学生画像弹窗 ===== */
:deep(.spf-dialog .el-dialog__body) { max-height: 72vh; overflow-y: auto; padding-top: 10px; }
.stu-name { background: none; border: none; padding: 0; color: var(--primary); font-weight: 600; cursor: pointer; font-size: 13px; }
.stu-name:hover { text-decoration: underline; }
.spf-head { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; padding-bottom: 12px; border-bottom: 1px solid var(--border); }
.spf-avatar { width: 46px; height: 46px; border-radius: 50%; background: linear-gradient(135deg, #2bb187, #38bdf8); color: #fff; display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 18px; flex-shrink: 0; }
.spf-info { display: flex; flex-direction: column; gap: 2px; min-width: 180px; }
.spf-info b { font-size: 16px; color: var(--text); }
.spf-info span { font-size: 12px; color: var(--text-light); }
.spf-warnings { display: flex; flex-direction: column; gap: 4px; margin-left: auto; }
.spf-warn { font-size: 12px; padding: 2px 10px; border-radius: 999px; }
.spf-warn.warn { color: #e11d48; background: rgba(244,63,94,.12); }
.spf-warn.info { color: #d97706; background: rgba(245,158,11,.12); }
.spf-warn.ok { color: #059669; background: rgba(16,185,129,.12); }

.spf-row { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1.2fr); gap: 12px; margin-top: 12px; }
@media (max-width: 700px) { .spf-row { grid-template-columns: minmax(0, 1fr); } }
.spf-block { padding: 12px 14px; border: 1px solid var(--border); border-radius: 11px; }
.spf-block-title { font-size: 12.5px; font-weight: 700; color: var(--text); margin-bottom: 9px; }
.spf-block-title small { font-weight: 500; color: var(--text-light); margin-left: 5px; }
.spf-near { color: #059669; background: rgba(16,185,129,.12); font-size: 12px; padding: 1px 8px; border-radius: 999px; margin-left: 6px; }

.spf-grad-bar { height: 12px; border-radius: 99px; background: rgba(148,163,184,.13); overflow: hidden; margin-bottom: 8px; }
.spf-grad-bar i { display: block; height: 100%; border-radius: 99px; background: linear-gradient(90deg, #2bb187, #38bdf8); transition: width .6s cubic-bezier(.22,.9,.36,1); }
.spf-grad-text { font-size: 12px; color: var(--text-light); }
.spf-grad-text b { color: var(--primary); font-size: 15px; margin-right: 5px; }
.spf-grad-text em { font-style: normal; color: #e11d48; font-weight: 700; margin-left: 6px; }
.spf-grad-text em.ok { color: #059669; }

.spf-stats { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 7px; }
.spf-stat { padding: 7px 6px; border: 1px solid var(--border); border-radius: 9px; text-align: center; }
.spf-stat b { display: block; font-size: 15px; color: var(--text); font-variant-numeric: tabular-nums; }
.spf-stat b.warn { color: #e11d48; }
.spf-stat span { font-size: 12px; color: var(--text-light); }

.spf-dist { display: flex; align-items: flex-end; gap: 6px; height: 64px; }
.spf-dist-col { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 3px; height: 100%; justify-content: flex-end; }
.spf-dist-num { font-size: 12px; font-weight: 700; color: var(--text); font-variant-numeric: tabular-nums; }
.spf-dist-bar { width: 100%; display: flex; align-items: flex-end; justify-content: center; }
.spf-dist-bar i { display: block; width: 70%; border-radius: 4px 4px 0 0; }
.spf-dist-bar i.low { background: rgba(244,63,94,.55); }
.spf-dist-bar i.mid { background: rgba(43,177,135,.6); }
.spf-dist-bar i.top { background: rgba(245,158,11,.65); }
.spf-dist-col small { font-size: 12px; color: var(--text-light); }

.spf-courses { max-height: 240px; overflow-y: auto; }
.spf-courses table { width: 100%; border-collapse: collapse; font-size: 12px; }
.spf-courses th, .spf-courses td { padding: 6px 9px; text-align: left; border-bottom: 1px solid var(--border); }
.spf-courses th { color: var(--text-light); font-weight: 600; background: rgba(148,163,184,.06); }
.spf-courses b.gold { color: #d97706; }
.spf-courses b.good { color: #059669; }
.spf-courses b.mid { color: #2563eb; }
.spf-courses b.bad { color: #e11d48; }

/* ===== 学生画像面板 ===== */
.sl-profile { display: grid; grid-template-columns: minmax(0, 1.15fr) minmax(0, 1fr); gap: 14px; margin-bottom: 16px; }
@media (max-width: 900px) { .sl-profile { grid-template-columns: minmax(0, 1fr); } }
.sl-panel { padding: 13px 15px; border: 1px solid var(--border); border-radius: 12px; background: color-mix(in srgb, var(--card-bg) 55%, transparent); }
.sl-title { font-size: 13px; font-weight: 700; color: var(--text); margin-bottom: 10px; }
.sl-title small { font-weight: 500; color: var(--text-light); margin-left: 8px; font-size: 12px; }
.sl-points, .sl-grades { display: flex; flex-direction: column; gap: 9px; }
.sl-point-row { display: grid; grid-template-columns: 64px minmax(0, 1fr) 52px; align-items: center; gap: 10px; }
.sl-point-label { font-size: 12px; color: var(--text-light); white-space: nowrap; }
.sl-point-track { height: 9px; border-radius: 99px; background: rgba(148,163,184,.14); overflow: hidden; }
.sl-point-track i { display: block; height: 100%; border-radius: 99px; background: linear-gradient(90deg, #06b6d4, #34d399); animation: slGrow .7s cubic-bezier(.22,.9,.36,1) both; }
.sl-point-track i.violet { background: linear-gradient(90deg, #2bb187, #38bdf8); }
@keyframes slGrow { from { transform: scaleX(0); transform-origin: left; } to { transform: scaleX(1); } }
.sl-point-val { text-align: right; font-size: 12px; font-weight: 700; color: var(--text); font-variant-numeric: tabular-nums; }
.sl-nodata { font-size: 12px; color: var(--text-light); opacity: .75; padding: 14px 0; text-align: center; }
</style>
