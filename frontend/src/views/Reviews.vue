<template>
  <div class="page-wrap stats-page liquid">
    <!-- 教师视角：我教课程的评价汇总 -->
    <template v-if="isTeacher">
      <div class="stats-grid">
        <div class="stat-card"><div class="stat-icon amber" v-html="svgIcon('star')" /><div><div class="stat-value">{{ data.totalAvg?.toFixed(1) || '—' }}</div><div class="stat-label">{{ t('课程平均分') }}</div></div></div>
        <div class="stat-card"><div class="stat-icon blue" v-html="svgIcon('message-square')" /><div><div class="stat-value">{{ data.totalCount || 0 }}</div><div class="stat-label">{{ t('总评价数') }}</div></div></div>
        <div class="stat-card"><div class="stat-icon green" v-html="svgIcon('check-circle')" /><div><div class="stat-value">{{ goodRate }}%</div><div class="stat-label">{{ t('好评率（4 星+）') }}</div></div></div>
        <div class="stat-card"><div class="stat-icon purple" v-html="svgIcon('book')" /><div><div class="stat-value">{{ (data.courses || []).length }}</div><div class="stat-label">{{ t('授课课程') }}</div></div></div>
      </div>

      <!-- ===== 评价分析：星级分布 + 三维雷达 ===== -->
      <div class="rv-analysis" v-if="data.totalCount">
        <div class="section rv-an-panel">
          <div class="section-header"><div class="section-title">{{ t('星级分布') }}</div><span class="rv-an-sub">{{ t('共 {n} 条评价', { n: data.totalCount }) }}</span></div>
          <div class="rv-stars">
            <div v-for="d in data.dist || []" :key="d.star" class="rv-star-row">
              <span class="rv-star-label">{{ '★'.repeat(d.star) }}<i>{{ '☆'.repeat(5 - d.star) }}</i></span>
              <div class="rv-star-track"><i class="rv-star-fill" :style="{ width: Math.max(d.count ? 4 : 0, d.rate) + '%' }" /></div>
              <span class="rv-star-val">{{ d.count }}</span>
            </div>
          </div>
        </div>

        <div class="section rv-an-panel">
          <div class="section-header"><div class="section-title">{{ t('教学维度雷达') }}</div><span class="rv-an-sub">{{ t('我的课 vs 全校课程均分') }}</span></div>
          <div class="rv-radar-wrap">
            <svg viewBox="0 0 220 200" class="rv-radar" role="img" :aria-label="t('教学维度雷达图')">
              <!-- 网格 -->
              <polygon v-for="lv in 4" :key="'g' + lv" :points="polyGrid(lv / 5)" class="rv-radar-grid" />
              <line v-for="(a, i) in 3" :key="'ax' + i" :x1="RC" :y1="RY" :x2="radarPt(1, i)[0]" :y2="radarPt(1, i)[1]" class="rv-radar-axis" />
              <text v-for="(a, i) in 3" :key="'al' + i" :x="radarPt(1.22, i)[0]" :y="radarPt(1.22, i)[1] + 4" text-anchor="middle" class="rv-radar-label">{{ t(DIMS[i].label) }}</text>
              <!-- 全校均分 -->
              <polygon :points="polyAt(schoolDims)" class="rv-radar-school" />
              <!-- 我的课 -->
              <polygon :points="polyAt(myDims)" class="rv-radar-mine" />
              <circle v-for="(p, i) in myRadarPts" :key="'mp' + i" :cx="p[0]" :cy="p[1]" r="3.5" class="rv-radar-dot" />
            </svg>
            <div class="rv-radar-legend">
              <span><i class="lg-mine" />{{ t('我的课程') }}</span>
              <span><i class="lg-school" />{{ t('全校均分') }}</span>
            </div>
            <div class="rv-radar-vals">
              <div v-for="d in DIMS" :key="d.key" class="rv-radar-val">
                <b>{{ myDims[d.key] }}</b>
                <span>{{ t(d.label) }}<i :class="{ up: myDims[d.key] >= schoolDims[d.key], down: myDims[d.key] < schoolDims[d.key] }">{{ diffSign(myDims[d.key], schoolDims[d.key]) }}</i></span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="section">
        <div class="section-header">
          <div class="section-title">{{ t('课程评价汇总') }}</div>
        </div>
        <div v-if="(data.courses || []).length" class="data-table">
          <table>
            <thead>
              <tr><th>{{ t('课程') }}</th><th>{{ t('评价数') }}</th><th>{{ t('综合分') }}</th><th>{{ t('教学') }}</th><th>{{ t('内容') }}</th><th>{{ t('作业') }}</th><th>{{ t('最新评价') }}</th></tr>
            </thead>
            <tbody>
              <tr v-for="c in data.courses" :key="c.courseId">
                <td><b>{{ c.courseName }}</b></td>
                <td>{{ c.count }}</td>
                <td><span class="rating" :class="ratingCls(c.avg)">{{ fmt(c.avg) }}</span></td>
                <td>{{ fmt(c.teachingAvg) }}</td>
                <td>{{ fmt(c.contentAvg) }}</td>
                <td>{{ fmt(c.homeworkAvg) }}</td>
                <td class="review-preview" v-if="c.latest">{{ formatLatest(c.latest) }}</td>
                <td v-else class="review-preview">—</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else class="empty-state">
          <div class="empty-icon" v-html="svgIcon('star')" />
          <div class="empty-title">{{ t('暂无课程评价') }}</div>
        </div>
      </div>
    </template>

    <!-- 学生视角：待评价 + 我的评价 -->
    <template v-else>
      <!-- ===== 学期筛选（评教按学期开评，参照真实高校） ===== -->
      <div class="rv-sem-bar">
        <span class="rv-sem-label">{{ t('学期') }}</span>
        <el-select class="sem-pill" v-model="selectedSem" style="width:280px">
          <template #prefix><span class="sem-cal" v-html="svgIcon('calendar')" /></template>
        <el-option v-for="o in semOptions" :key="o.no" :label="o.label" :value="o.no" />
        </el-select>
        <span class="rv-sem-note">{{ t('评教按修读学期进行 · 我的平均评分统计全部学期') }}</span>
      </div>
      <div class="stats-grid">
        <div class="stat-card"><div class="stat-icon amber" v-html="svgIcon('edit')" /><div><div class="stat-value">{{ pendingFiltered.length }}</div><div class="stat-label">{{ t('待评价课程') }}</div></div></div>
        <div class="stat-card"><div class="stat-icon green" v-html="svgIcon('check-circle')" /><div><div class="stat-value">{{ mineFiltered.length }}</div><div class="stat-label">{{ t('我已评价') }}</div></div></div>
        <div class="stat-card"><div class="stat-icon blue" v-html="svgIcon('star')" /><div><div class="stat-value">{{ myAvg }}</div><div class="stat-label">{{ t('我的平均评分') }}</div></div></div>
      </div>

      <div class="section">
        <div class="section-header"><div class="section-title">{{ t('待评价课程') }}</div></div>
        <div v-if="pendingFiltered.length" class="data-table">
          <table>
            <thead><tr><th>{{ t('课程') }}</th><th>{{ t('教师') }}</th><th>{{ t('类型') }}</th><th>{{ t('学分') }}</th><th style="width:120px">{{ t('操作') }}</th></tr></thead>
            <tbody>
              <tr v-for="c in pendingFiltered" :key="c.courseId">
                <td><b>{{ c.courseName }}</b></td>
                <td>{{ c.teacherName }}</td>
                <td>{{ c.category }}</td>
                <td>{{ c.credit }}</td>
                <td><el-button type="primary" size="small" @click="openForm(c)">{{ t('去评价') }}</el-button></td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else class="empty-state">
          <div class="empty-icon" v-html="svgIcon('check-circle')" />
          <div class="empty-title">{{ t('该学期暂无待评价课程') }}</div>
          <div class="empty-sub">{{ t('修读结束后即可对课程进行评价') }}</div>
        </div>
      </div>

      <div class="section" v-if="mineFiltered.length">
        <div class="section-header"><div class="section-title">{{ t('我的评价') }}</div></div>
        <div class="data-table">
          <table>
            <thead><tr><th>{{ t('课程') }}</th><th>{{ t('教师') }}</th><th>{{ t('综合') }}</th><th>{{ t('教学') }}</th><th>{{ t('内容') }}</th><th>{{ t('作业') }}</th><th>{{ t('评语') }}</th><th style="width:120px">{{ t('操作') }}</th></tr></thead>
            <tbody>
              <tr v-for="m in mineFiltered" :key="m.reviewId">
                <td><b>{{ m.courseName }}</b></td>
                <td>{{ m.teacherName }}</td>
                <td><span class="rating" :class="ratingCls(m.rating)">{{ m.rating }}</span></td>
                <td>{{ m.teaching }}</td>
                <td>{{ m.contentScore }}</td>
                <td>{{ m.homework }}</td>
                <td class="review-preview">{{ m.content || '—' }} <span v-if="m.anonymous" class="review-anon">{{ t('匿名') }}</span></td>
                <td><el-button size="small" @click="openEdit(m)">{{ t('修改') }}</el-button></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </template>

    <!-- 评价弹窗（学生） -->
    <el-dialog v-model="dialogVisible" :title="editTarget ? t('修改评价') : t('课程评价')" width="480px" append-to-body align-center>
      <div class="review-form" v-if="formCourse">
        <div class="rf-course">
          <b>{{ formCourse.courseName }}</b>
          <span class="muted">{{ formCourse.teacherName }}</span>
        </div>
        <div class="rf-row"><span class="rf-label">{{ t('综合评分') }}</span><el-rate v-model="form.rating" /></div>
        <div class="rf-row"><span class="rf-label">{{ t('教学质量') }}</span><el-rate v-model="form.teaching" /></div>
        <div class="rf-row"><span class="rf-label">{{ t('课程内容') }}</span><el-rate v-model="form.contentScore" /></div>
        <div class="rf-row"><span class="rf-label">{{ t('作业负担') }}</span><el-rate v-model="form.homework" /></div>
        <el-input v-model="form.content" type="textarea" :rows="3" maxlength="200" show-word-limit :placeholder="t('写点上课感受吧（选填）')" />
        <el-checkbox v-model="form.anonymous">{{ t('匿名评价') }}</el-checkbox>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('取消') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">{{ t('提交评价') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api'
import { useAuthStore } from '@/stores/auth'
import { t } from '@/i18n'

const auth = useAuthStore()
const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`
const isTeacher = computed(() => auth.user?.role === 'teacher')

const data = ref<any>({ courses: [], totalCount: 0, totalAvg: 0 })
const pending = ref<any[]>([])
const mine = ref<any[]>([])
/* ===== 学期筛选（评教按学期开评）：选项从待评价+已评价聚合，默认最近有待评价的学期 ===== */
const selectedSem = ref<number | null>(null)
const currentSemesterNo = ref<number | null>(null)
const semOptions = ref<{ no: number; label: string }[]>([])

function mySemesterNo(): number {
  let g = 0
  try { g = Number(JSON.parse(localStorage.getItem('zhixue_user') || '{}').grade) || 0 } catch {}
  const t0 = new Date()
  const y = t0.getFullYear(), m = t0.getMonth() + 1
  if (g < 2000) return 1
  return Math.max(1, m >= 8 ? (y - g) * 2 + 1 : (m === 1 ? (y - g) * 2 - 1 : (y - g) * 2))
}

function rebuildSemOptions() {
  currentSemesterNo.value = mySemesterNo()
  const set = new Map<number, string>()
  for (const c of pending.value) {
    if (c.semesterNo != null && !set.has(c.semesterNo)) set.set(c.semesterNo, c.semesterLabel || `第 ${c.semesterNo} 学期`)
  }
  for (const m of mine.value) {
    if (m.semesterNo != null && !set.has(m.semesterNo)) set.set(m.semesterNo, m.semesterLabel || `第 ${m.semesterNo} 学期`)
  }
  const list = [...set.entries()].sort((a, b) => b[0] - a[0]).map(([no, label]) => ({ no, label }))
  const cur = currentSemesterNo.value
  if (cur != null && !list.some(o => o.no === cur)) list.unshift({ no: cur, label: `第 ${cur} 学期` })
  semOptions.value = list
  // 默认：最近一个有待评价课程的学期；没有则当前学期
  const firstPending = [...pending.value].sort((a, b) => (b.semesterNo ?? 0) - (a.semesterNo ?? 0))[0]
  selectedSem.value = firstPending?.semesterNo ?? cur
}

const pendingFiltered = computed(() =>
  pending.value.filter(c => selectedSem.value == null || (c.semesterNo ?? -1) === selectedSem.value))
const mineFiltered = computed(() =>
  mine.value.filter(m => selectedSem.value == null || (m.semesterNo ?? -1) === selectedSem.value))

const dialogVisible = ref(false)
const submitting = ref(false)
const formCourse = ref<any>(null)
const editTarget = ref<any>(null)
const form = ref({ rating: 5, teaching: 5, contentScore: 5, homework: 3, anonymous: false, content: '' })

const myAvg = computed(() => {
  if (!mine.value.length) return '—'
  return (mine.value.reduce((s, m) => s + (m.rating || 0), 0) / mine.value.length).toFixed(1)
})

/* ===== 教师端评价分析 ===== */
const DIMS = [
  { key: 'teaching', label: '教学' },
  { key: 'content', label: '内容' },
  { key: 'homework', label: '作业' },
] as const
type DimVals = { teaching: number; content: number; homework: number }
const goodRate = computed(() => data.value.goodRate ?? 0)
const schoolDims = computed<DimVals>(() => ({
  teaching: data.value.schoolAvg?.teaching || 0,
  content: data.value.schoolAvg?.content || 0,
  homework: data.value.schoolAvg?.homework || 0,
}))
const myDims = computed<DimVals>(() => ({
  teaching: data.value.myTeaching || 0,
  content: data.value.myContent || 0,
  homework: data.value.myHomework || 0,
}))

/* 三维雷达：RC/RY 为圆心，RR 半径，顶角向上 */
const RC = 110
const RY = 92
const RR = 62
const AXES: Array<[number, number]> = [[0, -1], [-0.866, 0.5], [0.866, 0.5]]
const clamp5 = (v: number) => Math.max(0, Math.min(5, Number(v) || 0))
function radarPt(f: number, axis: number): [number, number] {
  return [RC + AXES[axis][0] * RR * f, RY + AXES[axis][1] * RR * f]
}
function polyGrid(f: number): string {
  return [0, 1, 2].map(i => radarPt(f, i).map(n => n.toFixed(1)).join(',')).join(' ')
}
function polyAt(vals: DimVals): string {
  return [vals.teaching, vals.content, vals.homework]
    .map((v, i) => radarPt(clamp5(v) / 5, i).map(n => n.toFixed(1)).join(','))
    .join(' ')
}
const myRadarPts = computed(() =>
  [myDims.value.teaching, myDims.value.content, myDims.value.homework]
    .map((v, i) => radarPt(clamp5(v) / 5, i))
)
function diffSign(mine: number, school: number): string {
  const d = Number(mine) - Number(school)
  if (Math.abs(d) < 0.05) return ' ' + t('持平')
  return (d > 0 ? ' ↑' : ' ↓') + Math.abs(d).toFixed(1)
}

function fmt(v: any) { return v != null && v !== undefined ? Number(v).toFixed(1) : '—' }
function ratingCls(v: any) {
  const n = Number(v) || 0
  if (n >= 4.5) return 'c-green'
  if (n >= 3.5) return 'c-blue'
  if (n > 0) return 'c-amber'
  return ''
}
function formatLatest(l: any) {
  if (typeof l === 'string') return l.slice(0, 30)
  if (l?.content) return l.content.slice(0, 30)
  return '—'
}

function openForm(c: any) {
  formCourse.value = c
  editTarget.value = null
  form.value = { rating: 5, teaching: 5, contentScore: 5, homework: 3, anonymous: false, content: '' }
  dialogVisible.value = true
}

function openEdit(m: any) {
  formCourse.value = { courseId: m.courseId, courseName: m.courseName, teacherName: m.teacherName }
  editTarget.value = m
  form.value = { rating: m.rating || 5, teaching: m.teaching || 5, contentScore: m.contentScore || 5, homework: m.homework || 3, anonymous: !!m.anonymous, content: m.content || '' }
  dialogVisible.value = true
}

async function submit() {
  if (!formCourse.value) return
  if (!form.value.rating) { ElMessage.warning(t('请先选择综合评分')); return }
  submitting.value = true
  try {
    const r: any = await http.post('/reviews', {
      courseId: formCourse.value.courseId,
      rating: form.value.rating,
      teaching: form.value.teaching,
      contentScore: form.value.contentScore,
      homework: form.value.homework,
      anonymous: form.value.anonymous,
      content: form.value.content || null,
    })
    if (r?.success === false || r?.data?.success === false) {
      ElMessage.error(r?.message || r?.data?.msg || t('提交失败'))
    } else {
      ElMessage.success(editTarget.value ? t('评价已更新') : t('评价已提交，感谢反馈'))
      dialogVisible.value = false
      await load()
    }
  } catch {
    ElMessage.error(t('提交失败，请稍后重试'))
  }
  submitting.value = false
}

async function load() {
  if (isTeacher.value) {
    try {
      const r: any = await http.get(`/reviews/teacher/${auth.user?.id}`)
      data.value = r?.data || { courses: [], totalCount: 0, totalAvg: 0 }
    } catch {}
    return
  }
  try {
    const [p, m]: any[] = await Promise.all([http.get('/reviews/pending'), http.get('/reviews/mine')])
    pending.value = p?.data || []
    mine.value = m?.data || []
    rebuildSemOptions()
  } catch {}
}

onMounted(load)
</script>

<style scoped>
.page-wrap { padding: 4px 0; }
.empty-state { text-align: center; padding: 60px 20px; color: var(--text-light); }
.empty-icon { font-size: 48px; opacity: 0.3; margin-bottom: 10px; }
.empty-sub { font-size: 12px; color: var(--text-lighter); margin-top: 6px; }
.rating { font-weight: 700; }
.c-green { color: #10b981; }
.c-blue { color: #3b82f6; }
.c-amber { color: #f59e0b; }
.review-preview { max-width: 180px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 12px; color: var(--text-light); }
.review-anon { font-size: 12px; color: var(--text-lighter); border: 1px solid var(--border); border-radius: 10px; padding: 1px 7px; margin-left: 6px; }

.review-form { display: flex; flex-direction: column; gap: 14px; }
.rf-course { display: flex; align-items: baseline; gap: 10px; }
.rf-course .muted { color: var(--text-light); font-size: 13px; }
.rf-row { display: flex; align-items: center; gap: 12px; }
.rf-label { width: 70px; font-size: 14px; color: var(--text); flex-shrink: 0; }

/* ===== 评价分析面板 ===== */
.rv-analysis { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1.15fr); gap: 16px; margin-bottom: 16px; }
@media (max-width: 960px) { .rv-analysis { grid-template-columns: minmax(0, 1fr); } }
.rv-an-sub { font-size: 12px; color: var(--text-light); }
.rv-stars { display: flex; flex-direction: column; gap: 12px; padding: 8px 2px 4px; }
.rv-star-row { display: grid; grid-template-columns: 96px minmax(0, 1fr) 36px; align-items: center; gap: 10px; }
.rv-star-label { font-size: 13px; color: #f59e0b; letter-spacing: 1px; }
.rv-star-label i { font-style: normal; color: var(--text-lighter); opacity: .6; }
.rv-star-track { height: 9px; border-radius: 99px; background: rgba(148,163,184,.14); overflow: hidden; }
.rv-star-fill { display: block; height: 100%; border-radius: 99px; background: linear-gradient(90deg, #f59e0b, #fbbf24); animation: rvStarGrow .8s cubic-bezier(.22,.9,.36,1) both; }
@keyframes rvStarGrow { from { transform: scaleX(0); transform-origin: left; } to { transform: scaleX(1); } }
.rv-star-val { text-align: right; font-size: 12.5px; font-weight: 700; color: var(--text); font-variant-numeric: tabular-nums; }

.rv-radar-wrap { display: flex; align-items: center; gap: 18px; padding: 6px 4px 2px; flex-wrap: wrap; }
.rv-radar { width: 208px; height: 190px; flex-shrink: 0; }
.rv-radar-grid { fill: none; stroke: rgba(148,163,184,.28); stroke-width: 1; }
.rv-radar-axis { stroke: rgba(148,163,184,.3); stroke-width: 1; }
.rv-radar-label { fill: var(--text-light); font-size: 12px; font-weight: 700; }
.rv-radar-school { fill: rgba(148,163,184,.16); stroke: #94a3b8; stroke-width: 1.4; stroke-dasharray: 4 3; }
.rv-radar-mine { fill: rgba(43,177,135,.24); stroke: #2bb187; stroke-width: 2; }
.rv-radar-dot { fill: #2bb187; stroke: #fff; stroke-width: 1.2; }
/* 雷达从中心展开（一次性入场） */
.rv-radar-mine, .rv-radar-school {
  transform-origin: 110px 92px;
  animation: rvExpand .75s cubic-bezier(.22, .9, .36, 1) backwards;
}
.rv-radar-school { animation-delay: .12s; }
@keyframes rvExpand { from { transform: scale(0); opacity: 0; } }

.rv-radar-legend { display: flex; flex-direction: column; gap: 7px; font-size: 12px; color: var(--text-light); }
.rv-radar-legend i { display: inline-block; width: 14px; height: 4px; border-radius: 2px; margin-right: 6px; vertical-align: middle; }
.rv-radar-legend .lg-mine { background: #2bb187; }
.rv-radar-legend .lg-school { background: #94a3b8; }
.rv-radar-vals { display: flex; flex-direction: column; gap: 8px; margin-left: auto; }
.rv-radar-val { padding: 7px 12px; border: 1px solid var(--border); border-radius: 10px; min-width: 104px; }
.rv-radar-val b { font-size: 15px; color: var(--text); margin-right: 8px; }
.rv-radar-val span { font-size: 12px; color: var(--text-light); }
.rv-radar-val i { font-style: normal; font-weight: 700; margin-left: 4px; }
.rv-radar-val i.up { color: #059669; }
.rv-radar-val i.down { color: #e11d48; }
/* ===== 学期筛选条 ===== */
.rv-sem-bar { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; flex-wrap: wrap; }
.rv-sem-label { font-size: 13px; color: var(--text-light); }
.rv-sem-note { font-size: 12px; color: var(--text-lighter); }

/* ===== 学期胶囊选择器（顶部胶囊式） ===== */
:deep(.sem-pill .el-select__wrapper) {
  background: rgba(43, 177, 135, .10);
  border-radius: 999px;
  box-shadow: 0 0 0 1px rgba(43, 177, 135, .28) inset;
  min-height: 38px;
  padding: 4px 16px;
  transition: box-shadow .2s, background .2s;
}
:deep(.sem-pill .el-select__wrapper.is-hovering) {
  background: rgba(43, 177, 135, .16);
  box-shadow: 0 0 0 1px rgba(43, 177, 135, .55) inset;
}
:deep(.sem-pill .el-select__placeholder),
:deep(.sem-pill .el-select__selected-item) {
  color: var(--primary);
  font-weight: 700;
}
:deep(.sem-pill .el-select__caret) { color: var(--primary); }
:deep(.sem-pill .sem-cal) { width: 15px; height: 15px; color: var(--primary); display: block; }
.sem-cal :deep(svg) { width: 15px; height: 15px; display: block; }
</style>