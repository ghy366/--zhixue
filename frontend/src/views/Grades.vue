<template>
  <div class="zx-page stats-page liquid">
    <div class="page-head">
      <h2>{{ t('📊 成绩查询') }}</h2>
      <button class="btn btn-outline btn-sm" @click="printTranscript">{{ t('🖨 导出成绩单 PDF') }}</button>
    </div>

    <!-- 学期筛选行（胶囊式 + 口径说明） -->
    <div class="gr-filter">
      <span class="gf-label">{{ t('学期') }}</span>
      <el-select class="sem-pill" v-model="selectedSemester" style="width:280px">
        <template #prefix><span class="sem-cal" v-html="svgIcon('calendar')" /></template>
        <el-option v-for="s in semesterOptions" :key="String(s.id)" :label="s.label" :value="s.id" />
      </el-select>
      <small class="gf-note">{{ scopeText }}</small>
    </div>

    <!-- 接口失败：错误提示 + 重试 -->
    <div v-if="loadError" class="gr-error">
      ⚠ {{ loadError }}
      <button class="btn btn-ghost btn-sm" @click="load">{{ t('重试') }}</button>
    </div>

    <!-- 加载骨架 -->
    <div v-if="loading && !grades.length" class="gr-loading" v-loading="true" :element-loading-text="t('成绩加载中…')" />

    <!-- 空状态 -->
    <el-empty v-else-if="!loading && !grades.length && !loadError" :description="t('暂无已录取课程的成绩记录')" />

    <template v-if="grades.length">
      <!-- ===== 成绩概览统计（口径分明：GPA=全部学期累计，其余=当前选中学期） ===== -->
      <div class="gr-stats-row">
        <div class="gr-stat gpa">
          <b>{{ gpa == null ? '—' : animGpa }}</b>
          <span>{{ t('累计 GPA') }}</span>
          <small>{{ t('全部学期 · 学分加权 · 4.0 制') }}</small>
        </div>
        <div class="gr-stat">
          <b :class="avgCls">{{ scoredRows.length ? animAvg : '—' }}</b>
          <span>{{ t('平均分') }}</span>
          <small>{{ selectedSemesterName }} · {{ t('{n} 门已录入', { n: scoredCount }) }}</small>
        </div>
        <div class="gr-stat">
          <b class="good">{{ scoredRows.length ? animMax : '—' }}</b>
          <span>{{ t('最高分') }}</span>
          <small>{{ selectedSemesterName }} · {{ t('仅已录入') }}</small>
        </div>
        <div class="gr-stat">
          <b :class="minCls">{{ scoredRows.length ? animMin : '—' }}</b>
          <span>{{ t('最低分') }}</span>
          <small>{{ selectedSemesterName }} · {{ t('未录入不计入') }}</small>
        </div>
        <div class="gr-stat">
          <b>{{ animScored }} <i class="gr-of">/ {{ grades.length }}</i></b>
          <span>{{ t('已录入 / 总课程') }}</span>
          <small>{{ t('已通过学分 {n}（成绩≥60）', { n: animEarned }) }}</small>
        </div>
        <div class="gr-stat gpa ranking">
          <b>{{ rankText }}</b>
          <span>{{ t('专业排名') }}</span>
          <small>{{ rankSub }}</small>
        </div>
      </div>

      <!-- ===== 类别均分 + 趋势/单轮概览 ===== -->
      <div class="gr-head">
        <div class="gr-card">
          <div class="gr-title">{{ t('类别均分') }} <small>{{ t('按当前筛选范围 · 虚线：及格 60 / 优秀 90') }}</small></div>
          <div class="gr-cats">
            <div v-for="g in catRows" :key="g.key" class="gr-cat-row">
              <span class="gr-cat-label">{{ g.key }}</span>
              <div class="gr-cat-track">
                <i :class="g.cls" :style="{ width: Math.min(100, g.avg) + '%' }" />
                <em class="tick" style="left: 60%" />
                <em class="tick" style="left: 90%" />
              </div>
              <span class="gr-cat-val">{{ g.avg }}<small class="gr-cat-n">{{ g.n }} {{ t('门') }}</small></span>
            </div>
            <div v-if="!catRows.length" class="gr-nodata">{{ t('暂无有效成绩') }}</div>
          </div>
        </div>
        <div class="gr-card">
          <div class="gr-title">{{ t('学期成绩概览') }} <small>{{ selectedSemesterName }}</small></div>
          <div class="gr-single">
            <div class="gs-item"><b :class="avgCls">{{ scoredRows.length ? animAvg : '—' }}</b><span>{{ t('学期平均分') }}</span></div>
            <div class="gs-item"><b>{{ semesterGpaText === '—' ? '—' : animSemGpa }}</b><span>{{ t('学期 GPA') }}</span></div>
            <div class="gs-item"><b>{{ animScored }}</b><span>{{ t('已录入课程') }}</span></div>
          </div>
          <div class="t-note">{{ t('完成更多学期后，这里将展示各学期均分与 GPA 的变化趋势') }}</div>
        </div>
      </div>

      <!-- ===== 成绩明细 ===== -->
      <el-table v-if="filtered.length" :data="filtered" v-loading="loading" style="width:100%" row-key="courseId"
                class="gr-table" @row-click="openDrawer">
        <el-table-column prop="courseName" :label="t('课程名')" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.courseName }}
            <el-tag v-if="row.examType === 'makeup'" size="small" type="warning" effect="plain" class="gr-exam-tag">
              {{ t('补考') }}<template v-if="row.attempt > 1"> · {{ row.attempt }}</template>
            </el-tag>
            <el-tag v-else-if="row.examType === 'retake'" size="small" type="danger" effect="plain" class="gr-exam-tag">
              {{ t('重修') }}<template v-if="row.attempt > 1"> · {{ row.attempt }}</template>
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="teacherName" :label="t('教师')" width="110" />
        <el-table-column prop="category" :label="t('类型')" width="95" align="center" />
        <el-table-column prop="credit" :label="t('学分')" width="65" align="center" />
        <el-table-column :label="t('成绩')" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="isEntered(row)" :type="scoreTag(row.totalScore)" size="small">{{ row.totalScore }}</el-tag>
            <span v-else class="muted">{{ t('未录入') }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('状态')" width="150" align="center">
          <template #default="{ row }">
            <template v-if="isEntered(row)">
              <span class="st-text" :class="'st-' + scoreTag(row.totalScore)">{{ statusText(row.totalScore) }}</span>
              <span v-if="row.badge" class="gd-badge" :class="row.badge">{{ badgeText(row) }}</span>
            </template>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('补考 / 重修')" width="128" align="center">
          <template #default="{ row }">
            <button v-if="row.badge === 'makeup_todo' && row.makeup" class="btn btn-warning btn-sm"
                    @click.stop="registerMakeup(row)">{{ t('补考报名') }}</button>
            <span v-else-if="row.badge === 'makeup_registered'" class="muted">{{ t('已报名') }}</span>
            <button v-else-if="row.badge === 'retake_todo' && row.retake" class="btn btn-danger btn-sm"
                    @click.stop="applyRetake(row)">{{ t('重修报名') }}</button>
            <span v-else-if="row.badge === 'retake_registered'" class="muted">{{ t('待教务安排') }}</span>
            <span v-else-if="row.badge === 'retaking' && row.retake?.teachingClassNo" class="muted">{{ t('已进班') }}</span>
            <button v-else-if="row.badge === 'retaking' && row.retake" class="btn btn-outline btn-sm"
                    @click.stop="openDrawer(row)">{{ t('选跟班班级') }}</button>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('成绩定位')" width="130">
          <template #default="{ row }">
            <div v-if="isEntered(row)" class="gr-pos">
              <div class="gr-pos-track">
                <i :style="{ left: Math.min(100, Math.max(0, Number(row.totalScore))) + '%' }" />
                <em class="tick" style="left: 60%" /><em class="tick" style="left: 90%" />
              </div>
            </div>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('绩点')" width="75" align="center">
          <template #default="{ row }">{{ isEntered(row) ? scoreToGpa(Number(row.totalScore), row.examType).toFixed(2) : '—' }}</template>
        </el-table-column>
        <el-table-column :label="t('明细')" min-width="150">
          <template #default="{ row }">
            <span v-if="row.regularScore != null || row.finalScore != null" class="muted">
              {{ t('平时') }} {{ row.regularScore ?? '—' }} · {{ t('期末') }} {{ row.finalScore ?? '—' }}
            </span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="grades.length && !filtered.length" :description="t('该学期暂无成绩记录（成绩数据归属于当前学期）')" />
      <div class="gr-footnote">{{ t('口径：平均分/最高/最低仅统计已录入成绩（未录入不计入、不当 0 分）；GPA = Σ(绩点×课程学分) ÷ Σ学分，绩点换算与后端一致（90→4.0 · 85→3.7 · 80→3.3 · 75→3.0 · 70→2.7 · 65→2.3 · 60→2.0 · <60→0），补考通过按 60 分档封顶 2.0 计绩点、重修按实际分数；总评 = 平时×40% + 期末×60%；定位条 60 及格 / 90 优秀。挂科课程可补考、补考不过可重修，重修通过即获得学分。') }}</div>
    </template>

    <!-- ===== 补考 / 重修入口：常驻面板，避免只在挂科行内出现导致找不到 ===== -->
    <div class="gd-flow-panel">
      <div class="gd-flow-head">
        <b>{{ t('补考 / 重修') }}</b>
        <span class="muted">{{ t('待处理 {n} 项', { n: pendingFlows.length }) }}</span>
      </div>
      <div v-if="!pendingFlows.length" class="gd-flow-empty">
        {{ t('当前没有待处理的补考或重修。补考未通过或缺考后，系统会自动生成「重修报名」入口。') }}
      </div>
      <div v-else class="gd-flow-list">
        <div v-for="f in pendingFlows" :key="f.kind + f.id" class="gd-flow-row">
          <span class="gd-badge" :class="flowBadge(f)">
            {{ f.kind === 'makeup' ? t('补考') : t('重修') }}
          </span>
          <b>{{ f.row.courseName }}</b>
          <small>{{ f.kind === 'makeup' ? makeupStatusText(f.status) : retakeStatusText(f.status) }}</small>
          <button v-if="f.canAct" class="btn btn-sm" :class="f.kind === 'makeup' ? 'btn-warning' : 'btn-danger'"
                  @click="f.kind === 'makeup' ? registerMakeup(f.row) : applyRetake(f.row)">
            {{ f.kind === 'makeup' ? t('补考报名') : t('重修报名') }}
          </button>
          <span v-else-if="f.kind === 'retake' && (f.status === 'APPLIED' || f.status === 'APPROVED')" class="muted">
            {{ t('待教务安排') }}
          </span>
          <span v-else-if="f.kind === 'retake' && f.status === 'ENROLLED' && f.row.retake?.teachingClassNo" class="muted">
            {{ t('已进班') }}
          </span>
          <button v-else-if="f.kind === 'retake'" class="btn btn-outline btn-sm" @click="openDrawer(f.row)">
            {{ f.status === 'ENROLLED' ? t('更换跟班班级') : t('选跟班班级') }}
          </button>
        </div>
      </div>
    </div>

    <!-- ===== 修读流水抽屉：历次修读 + 补考报名状态 + 跟班重修选班 ===== -->
    <el-drawer v-model="drawerOpen" :title="drawerRow?.courseName ? t('修读流水 · {n}', { n: drawerRow.courseName }) : t('修读流水')" size="560px"
               :lock-scroll="false" :modal="true" @closed="releaseBodyScrollLock">
      <div v-if="drawerRow" class="gd-drawer">
        <div class="gd-flow">
          <div v-for="(h, i) in drawerHistory" :key="i" class="gd-flow-item" :class="{ pass: Number(h.totalScore) >= 60 }">
            <span class="gd-dot" />
            <div class="gd-flow-main">
              <b>{{ typeText(h.examType) }} · {{ t('第 {n} 次修读', { n: h.attemptNo }) }}</b>
              <small>{{ h.semesterNo ? t('第 {n} 学期', { n: h.semesterNo }) : '' }} · {{ t('总评') }} {{ h.totalScore ?? '—' }}</small>
            </div>
            <em>{{ Number(h.totalScore) >= 60 ? t('通过') : t('未通过') }}</em>
          </div>
          <div v-if="!drawerHistory.length" class="muted">{{ t('暂无流水记录') }}</div>
        </div>

        <div v-if="drawerRow.makeup" class="gd-block">
          <div class="gd-block-title">{{ t('补考') }}</div>
          <p>{{ t('状态') }}：{{ makeupStatusText(drawerRow.makeup.status) }}
            <template v-if="drawerRow.makeup.examTime"> · {{ t('考试时间') }} {{ drawerRow.makeup.examTime.replace('T', ' ') }}</template>
            <template v-if="drawerRow.makeup.location"> · {{ t('地点') }} {{ drawerRow.makeup.location }}</template>
          </p>
          <button v-if="drawerRow.badge === 'makeup_todo'" class="btn btn-warning btn-sm" @click="registerMakeup(drawerRow)">
            {{ t('报名本次补考') }}
          </button>
        </div>

        <div v-if="drawerRow.retake" class="gd-block">
          <div class="gd-block-title">{{ t('重修') }}</div>
          <p>{{ t('状态') }}：{{ retakeStatusText(drawerRow.retake.status) }} · {{ t('形式') }}：{{ formText(drawerRow.retake.form) }}</p>
          <p v-if="drawerRow.retake.teachingClassNo" class="gd-class-info">
            {{ t('教学班') }}：<b>{{ drawerRow.retake.teachingClassNo }}</b>
            <template v-if="drawerRow.retake.teachingClassTime"> · {{ drawerRow.retake.teachingClassTime }}</template>
            <template v-if="drawerRow.retake.teachingClassLocation"> · {{ drawerRow.retake.teachingClassLocation }}</template>
            <template v-if="drawerRow.retake.teachingClassTeacher"> · {{ drawerRow.retake.teachingClassTeacher }}</template>
          </p>
          <button v-if="drawerRow.badge === 'retake_todo'" class="btn btn-danger btn-sm" @click="applyRetake(drawerRow)">
            {{ t('报名重修') }}
          </button>
          <p v-if="drawerRow.retake.status === 'APPLIED'" class="muted">
            {{ t('已报名，等待教务统一安排教学班（组班或跟班）。') }}
          </p>
          <template v-if="!drawerRow.retake.teachingClassNo && (drawerRow.retake.status === 'APPROVED' || drawerRow.retake.status === 'ENROLLED')">
            <div class="gd-block-title" style="margin-top:12px">{{ t('跟班重修 · 选择教学班') }}</div>
            <div v-if="drawerLoading" class="muted">{{ t('加载可选教学班…') }}</div>
            <div v-else class="gd-classes">
              <label v-for="c in classes" :key="c.courseId" class="gd-class" :class="{ on: chosenClassId === c.courseId, conflict: c.conflict }">
                <input type="radio" :value="c.courseId" v-model="chosenClassId" />
                <div>
                  <b>{{ c.name }}</b>
                  <small>{{ c.teacherName }} · {{ c.time || t('时间待定') }} · {{ c.location || t('地点待定') }}</small>
                  <span v-if="c.conflict" class="gd-conflict">{{ t('与当前课表冲突') }}</span>
                </div>
              </label>
              <div v-if="!classes.length" class="muted">{{ t('暂无同课程教学班可选') }}</div>
            </div>
            <button class="btn btn-primary btn-sm" style="margin-top:10px" :disabled="!chosenClassId" @click="chooseClass">
              {{ t('确认跟班并写入课表') }}
            </button>
          </template>
        </div>

        <div v-if="!drawerRow.makeup && !drawerRow.retake" class="muted">{{ t('该课程没有进行中的补考或重修流程') }}</div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api'
import { useCountUp } from '@/utils/useCountUp'
import { t } from '@/i18n'
import { semesterTitle, isZsbUser } from '@/utils/semester'

interface Grade {
  courseId: number; courseName: string; teacherName: string; category: string
  credit: number; regularScore: number | null; finalScore: number | null; totalScore: number | null
  round?: number
  examType?: string | null; attempt?: number | null
  failed?: boolean; retaking?: boolean
  semesterNo?: number | null; semesterLabel?: string | null
  badge?: string
  history?: any[]
  makeup?: { id: number; status: string; attemptNo?: number; score?: number | null; examTime?: string | null; location?: string | null } | null
  retake?: { id: number; status: string; attemptNo?: number; form?: string | null; targetClassId?: number | null; score?: number | null
    teachingClassNo?: string | null; teachingClassTime?: string | null
    teachingClassLocation?: string | null; teachingClassTeacher?: string | null } | null
}

/* ===== 修读流水抽屉 + 补考/重修报名 ===== */
const drawerOpen = ref(false)
const drawerRow = ref<Grade | null>(null)
const drawerHistory = ref<any[]>([])
const classes = ref<any[]>([])
const chosenClassId = ref<number | null>(null)
const drawerLoading = ref(false)

/** 常驻「补考 / 重修」入口的数据源：进行中的补考册与重修申请 */
const pendingFlows = computed(() => {
  const out: { kind: 'makeup' | 'retake'; id: number; row: Grade; status: string; canAct: boolean }[] = []
  for (const g of grades.value) {
    if (g.makeup && ['ELIGIBLE', 'REGISTERED', 'SCHEDULED'].includes(g.makeup.status)) {
      out.push({ kind: 'makeup', id: g.makeup.id, row: g, status: g.makeup.status, canAct: g.makeup.status === 'ELIGIBLE' })
    }
    if (g.retake && ['ELIGIBLE', 'APPLIED', 'APPROVED', 'ENROLLED'].includes(g.retake.status)) {
      out.push({ kind: 'retake', id: g.retake.id, row: g, status: g.retake.status, canAct: g.retake.status === 'ELIGIBLE' })
    }
  }
  return out
})
/** 面板徽章样式：待报名 / 已报名待教务安排 / 重修中 */
function flowBadge(f: { kind: 'makeup' | 'retake'; status: string }): string {
  if (f.kind === 'makeup') {
    return f.status === 'REGISTERED' || f.status === 'SCHEDULED' ? 'makeup_registered' : 'makeup_todo'
  }
  if (f.status === 'ENROLLED') return 'retaking'
  if (f.status === 'APPLIED' || f.status === 'APPROVED') return 'retake_registered'
  return 'retake_todo'
}

const BADGE_TEXT: Record<string, string> = {
  makeup_todo: '待补考',
  makeup_registered: '补考已报名',
  retake_todo: '待重修',
  retake_registered: '重修已报名',
  retaking: '重修中',
  makeup_passed: '已通过·补考',
}
function badgeText(row: Grade): string {
  if (row.badge === 'retake_passed') return t('已通过·重修{n}', { n: row.retake?.attemptNo ?? '' })
  return row.badge ? t(BADGE_TEXT[row.badge] || row.badge) : ''
}
function typeText(tp?: string | null): string {
  return tp === 'makeup' ? t('补考') : tp === 'retake' ? t('重修') : t('正考')
}
function makeupStatusText(s: string): string {
  const map: Record<string, string> = {
    ELIGIBLE: '待报名', REGISTERED: '已报名', SCHEDULED: '已排考',
    PASSED: '已通过', FAILED: '未通过', ABSENT: '缺考', CANCELLED: '已取消',
  }
  return t(map[s] || s)
}
function retakeStatusText(s: string): string {
  const map: Record<string, string> = {
    ELIGIBLE: '待报名', APPLIED: '已报名', APPROVED: '已安排', ENROLLED: '已进班',
    PASSED: '已通过', FAILED: '未通过', CANCELLED: '已取消',
  }
  return t(map[s] || s)
}
function formText(f?: string | null): string {
  return f === 'GROUP' ? t('组班') : f === 'FOLLOW' ? t('跟班') : f === 'SELF' ? t('自修') : t('未定')
}

async function openDrawer(row: Grade) {
  drawerRow.value = row
  drawerOpen.value = true
  drawerHistory.value = row.history || []
  classes.value = []
  chosenClassId.value = null
  try {
    const r: any = await http.get('/grades/mine/records', { params: { courseId: row.courseId } })
    drawerHistory.value = r?.data?.history || drawerHistory.value
  } catch {}
  if (row.retake && ['APPLIED', 'APPROVED'].includes(row.retake.status)) {
    loadClasses(row)
  }
}

async function loadClasses(row: Grade) {
  drawerLoading.value = true
  try {
    const r: any = await http.get('/retake/classes', { params: { courseId: row.courseId } })
    classes.value = r?.data || []
  } catch {
    classes.value = []
  }
  drawerLoading.value = false
}

async function registerMakeup(row: Grade) {
  if (!row.makeup) return
  try {
    const r: any = await http.post(`/makeup/${row.makeup.id}/register`)
    ElMessage.success(r?.message || t('补考报名成功'))
    await load()
    if (drawerOpen.value) await openDrawer(grades.value.find(g => g.courseId === row.courseId) || row)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || t('报名失败'))
  }
}

async function applyRetake(row: Grade) {
  if (!row.retake) return
  const fee = 200 * (Number((row as any).credit) || 0)
  try {
    await ElMessageBox.confirm(
      t('《{name}》重修费 {fee} 元（200 元/学分 · 华厦教字〔2017〕8号）；缴费后原则上不退费，报名前请考虑清楚。确认报名？',
        { name: row.courseName, fee }),
      t('重修报名'), { type: 'warning', confirmButtonText: t('确认报名'), cancelButtonText: t('暂不报名') })
  } catch { return }
  try {
    const r: any = await http.post(`/retake/${row.retake.id}/apply`)
    ElMessage.success(r?.message || t('重修报名成功'))
    await load()
    if (drawerOpen.value) await openDrawer(grades.value.find(g => g.courseId === row.courseId) || row)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || t('报名失败'))
  }
}

async function chooseClass() {
  const row = drawerRow.value
  if (!row?.retake || !chosenClassId.value) return
  try {
    const r: any = await http.post(`/retake/${row.retake.id}/choose-class`, { courseId: chosenClassId.value })
    ElMessage.success(r?.message || t('已加入跟班重修'))
    drawerOpen.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || t('选班失败'))
  }
}

const loading = ref(false)
const loadError = ref('')
const grades = ref<Grade[]>([])

/* ===== 学期筛选：成绩归属修读学期（真实高校口径）——每学期成绩在该学期查询 ===== */
interface SemOpt { id: number; label: string }
const semesterOptions = ref<SemOpt[]>([])
const currentSemesterNo = ref<number | null>(null)
const selectedSemester = ref<number | null>(null)

const selectedSemesterName = computed(() =>
  semesterOptions.value.find(s => s.id === selectedSemester.value)?.label || t('全部学期'))
const filtered = computed(() =>
  selectedSemester.value == null || selectedSemester.value === 'all'
    ? grades.value
    : grades.value.filter(g => (g.semesterNo ?? -1) === selectedSemester.value))
const scopeText = computed(() =>
  selectedSemester.value === currentSemesterNo.value
    ? t('当前学期 · 累计 GPA 统计全部学期')
    : t('历史学期 · 累计 GPA 统计全部学期'))

/** 已录入 = totalScore 有效，且不是「成绩行存在但平时/期末均未填」（后端此时会算出 0 分，非真实 0 分） */
function isEntered(g: Grade): boolean {
  if (g.totalScore == null || g.totalScore === '' || isNaN(Number(g.totalScore))) return false
  if (Number(g.totalScore) === 0 && g.regularScore == null && g.finalScore == null) return false
  return true
}

/* ===== 统计（只统计已录入成绩；0 分若平时/期末齐全视为真实 0 分） ===== */
const scoredRows = computed(() => filtered.value.filter(isEntered))
const scoredCount = computed(() => scoredRows.value.length)
const avgScore = computed(() => {
  const g = scoredRows.value
  return g.length ? (g.reduce((s, x) => s + Number(x.totalScore), 0) / g.length).toFixed(1) : '—'
})
const maxScore = computed(() => {
  const g = scoredRows.value
  return g.length ? Math.max(...g.map(x => Number(x.totalScore))) : '—'
})
const minScore = computed(() => {
  const g = scoredRows.value
  return g.length ? Math.min(...g.map(x => Number(x.totalScore))) : '—'
})
const avgCls = computed(() => gradeCls(Number(avgScore.value)))
const minCls = computed(() => (scoredRows.value.length && Number(minScore.value) < 60 ? 'bad' : ''))
function gradeCls(n: number) {
  if (isNaN(n)) return ''
  if (n >= 90) return 'gold'
  if (n >= 80) return 'good'
  if (n >= 60) return 'mid'
  return 'bad'
}
/* ===== 累计 GPA / 已通过学分：后端成绩流水有效记录（唯一口径，与毕业判定同源） ===== */
const gpa = ref<number | null>(null)
const earnedCredits = ref<number>(0)
async function loadGpa() {
  try {
    const r: any = await http.get('/graduation/gpa')
    const d = r?.data || {}
    const v = Number(d.gpa)
    gpa.value = Number.isFinite(v) ? v : null
    earnedCredits.value = Number(d.earnedCredit) || 0
  } catch {}
}

/** 与后端 GradeService.scoreToGpa 一致的 4.0 制换算（90→4 85→3.7 80→3.3 75→3 70→2.7 65→2.3 60→2 <60→0）。
    补考（makeup）通过一律按 60 分档封顶 2.0 计绩点——真实高校「补考通过不刷绩点」规则；重修按实际分数 */
function scoreToGpa(s: number, examType?: string | null): number {
  const p = s >= 90 ? 4.0 : s >= 85 ? 3.7 : s >= 80 ? 3.3 : s >= 75 ? 3.0
    : s >= 70 ? 2.7 : s >= 65 ? 2.3 : s >= 60 ? 2.0 : 0.0
  return examType === 'makeup' ? Math.min(2.0, p) : p
}

const gpaText = computed(() => gpa.value != null ? gpa.value.toFixed(2) : '—')

/* ===== 类别均分：按课程类别聚合，展示有效成绩数量 ===== */
const CAT_KEYS = ['必修', '选修', '通识', '实践']
const CAT_CLS: Record<string, string> = { '必修': 'c-bx', '选修': 'c-xx', '通识': 'c-ts', '实践': 'c-sj' }
function catOf(c: string) {
  const s = c || ''
  if (s.includes('通识')) return '通识'
  if (s.includes('实践')) return '实践'
  if (s.includes('选修')) return '选修'
  return '必修'
}
const catRows = computed(() => {
  const map: Record<string, number[]> = {}
  for (const g of scoredRows.value) {
    const k = catOf(g.category)
    ;(map[k] = map[k] || []).push(Number(g.totalScore))
  }
  return CAT_KEYS
    .filter(k => map[k]?.length)
    .map(k => ({
      key: k,
      cls: CAT_CLS[k],
      n: map[k].length,
      avg: (map[k].reduce((s, v) => s + v, 0) / map[k].length).toFixed(1),
    }))
})

/* ===== 学期 GPA：当前选中学期的学分加权 GPA ===== */
const semesterGpaText = computed(() => {
  const scored = scoredRows.value.filter(g => g.credit > 0)
  if (!scored.length) return '—'
  const weighted = scored.reduce((s, g) => s + scoreToGpa(Number(g.totalScore), g.examType) * g.credit, 0)
  const credits = scored.reduce((s, g) => s + g.credit, 0)
  return credits > 0 ? (weighted / credits).toFixed(2) : '—'
})

/* ===== 统计卡数字滚动（GPA 2 位小数、均分 1 位，'—' 时保持占位） ===== */
const animGpa = useCountUp(() => gpa.value ?? 0, { decimals: 2 })
const animAvg = useCountUp(() => (scoredRows.value.length ? Number(avgScore.value) : 0), { decimals: 1 })
const animMax = useCountUp(() => (scoredRows.value.length ? Number(maxScore.value) : 0))
const animMin = useCountUp(() => (scoredRows.value.length ? Number(minScore.value) : 0))
const animScored = useCountUp(() => scoredCount.value)
const animEarned = useCountUp(() => earnedCredits.value)
const animSemGpa = useCountUp(() => (semesterGpaText.value === '—' ? 0 : Number(semesterGpaText.value)), { decimals: 2 })

function scoreTag(s: number) {
  if (s >= 90) return 'success'
  if (s >= 60) return 'primary'
  return 'danger'
}
function statusText(s: number) {
  if (s >= 90) return t('优秀')
  if (s >= 80) return t('良好')
  if (s >= 70) return t('中等')
  if (s >= 60) return t('及格')
  return t('不及格')
}

/* ===== 专业排名（同专业学生按学分加权 GPA） ===== */
const rank = ref<number | null>(null)
const rankTotal = ref(0)
const rankPct = ref<number | null>(null)
const rankMajor = ref('')
const rankText = computed(() => (rank.value != null && rankTotal.value > 0) ? rank.value : '—')
const rankSub = computed(() => {
  if (rank.value == null || rankTotal.value <= 0) return rankMajor.value ? t('{m} · 暂无可比数据', { m: rankMajor.value }) : t('暂无同专业数据')
  const pct = rankPct.value != null ? ' · ' + t('前 {p}%', { p: rankPct.value }) : ''
  return t('{m} · 共 {n} 人', { m: rankMajor.value, n: rankTotal.value }) + pct
})
async function loadRanking() {
  try {
    const r: any = await http.get('/graduation/ranking')
    rank.value = r?.data?.rank ?? null
    rankTotal.value = r?.data?.total ?? 0
    rankPct.value = r?.data?.percentile ?? null
    rankMajor.value = r?.data?.major || ''
  } catch {}
}

/** 导出成绩单 PDF：打印窗口单开全部学期成绩（浏览器「另存为 PDF」） */
async function printTranscript() {
  const rows = grades.value.filter(isEntered)
  if (!rows.length) { ElMessage.warning(t('暂无成绩可导出')); return }
  const stu = JSON.parse(localStorage.getItem('zhixue_user') || '{}')
  const bySem = new Map()
  for (const g of rows) {
    const no = g.semesterNo ?? 0
    if (!bySem.has(no)) bySem.set(no, { label: semesterTitle(no, isZsbUser()), list: [] })
    bySem.get(no).list.push(g)
  }
  const gpaTxt = gpa.value != null ? gpa.value.toFixed(2) : '—'
  const earned = earnedCredits.value
  const w = window.open('', '_blank')
  if (!w) { ElMessage.error(t('浏览器拦截了弹窗，请允许弹出窗口')); return }
  const esc = (v: any) => String(v ?? '')
  const semHtml = [...bySem.entries()].sort((a, b) => b[0] - a[0]).map(([no, grp]) => `
    <h3>${esc(grp.label)}</h3>
    <table>
      <thead><tr><th>课程</th><th>类型</th><th>学分</th><th>平时</th><th>期末</th><th>总评</th><th>绩点</th></tr></thead>
      <tbody>${grp.list.map(g => `
        <tr>
          <td>${esc(g.courseName)}</td><td>${esc(g.category)}</td><td>${g.credit ?? ''}</td>
          <td>${g.regularScore ?? '—'}</td><td>${g.finalScore ?? '—'}</td><td>${g.totalScore ?? '—'}</td>
          <td>${scoreToGpa(Number(g.totalScore), g.examType).toFixed(2)}</td>
        </tr>`).join('')}</tbody>
    </table>`).join('')
  w.document.write(`<!DOCTYPE html><html lang="zh"><head><meta charset="utf-8"><title>成绩单</title>
    <style>
      body { font-family: 'Microsoft YaHei', sans-serif; padding: 32px; color: #1f2937; }
      h1 { text-align: center; font-size: 22px; margin: 0 0 4px; }
      .meta { text-align: center; color: #6b7280; font-size: 13px; margin-bottom: 20px; }
      h3 { font-size: 15px; margin: 18px 0 8px; color: #0f766e; }
      table { width: 100%; border-collapse: collapse; font-size: 12.5px; margin-bottom: 8px; }
      th, td { border: 1px solid #d1d5db; padding: 6px 10px; text-align: left; }
      th { background: #f0faf7; }
      .sum { margin-top: 16px; font-size: 14px; }
      .sum b { color: #0f766e; }
      @media print { body { padding: 0; } }
    </style></head><body>
    <h1>智学选课 · 学生成绩单</h1>
    <div class="meta">姓名：${esc(stu.name)}　学号：${esc(stu.no)}　专业：${esc(stu.major)}　打印时间：${new Date().toLocaleString('zh-CN')}</div>
    ${semHtml}
    <div class="sum">累计 GPA：<b>${gpaTxt}</b>　已通过学分：<b>${earned}</b>　已通过课程：<b>${rows.filter(g => Number(g.totalScore) >= 60).length} 门</b></div>
    <script>window.onload = () => setTimeout(() => window.print(), 300)<\/script>
    </body></html>`)
  w.document.close()
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const [r, me] = await Promise.all([
      http.get('/enrollments/scores'),
      http.get('/auth/me').catch(() => null),
    ])
    // 学期序单一事实来源：后端（含学期滚动 offset）；失败时回退本地推算
    try {
      const ms: any = await http.get('/enrollments/my-semester')
      currentSemesterNo.value = ms?.data?.semesterNo ?? null
    } catch {
      let myGrade = 0
      try { myGrade = Number(JSON.parse(localStorage.getItem('zhixue_user') || '{}').grade) || 0 } catch {}
      if (myGrade < 2000) {
        const mu = me?.data?.data || me?.data || {}
        myGrade = Number(mu.grade) || 0
      }
      const today = new Date()
      const y = today.getFullYear(), m = today.getMonth() + 1
      let sem = 1
      if (myGrade >= 2000) {
        sem = Math.max(1, m >= 8 ? (y - myGrade) * 2 + 1 : (m === 1 ? (y - myGrade) * 2 - 1 : (y - myGrade) * 2))
      }
      currentSemesterNo.value = sem
    }
    const allGrades = (r?.data?.list || r?.data || []) as Grade[]
    // 未来学期不可能有成绩：过滤掉 semesterNo > 当前学期的记录（培养方案提前排课/误录数据的兜底）
    const curSem = currentSemesterNo.value
    grades.value = curSem != null
      ? allGrades.filter(g => g.semesterNo == null || g.semesterNo <= curSem)
      : allGrades
    // 学期选项：从成绩的修读学期聚合（学期序降序），当前学期标注「进行中」
    const semSet = new Map<number, string>()
    for (const g of grades.value) {
      if (g.semesterNo != null && !semSet.has(g.semesterNo)) {
        semSet.set(g.semesterNo, semesterTitle(g.semesterNo, isZsbUser()))
      }
    }
    const list: SemOpt[] = [...semSet.entries()]
      .sort((a, b) => b[0] - a[0])
      .map(([no, label]) => ({
        id: no,
        label: no === currentSemesterNo.value ? `${label}（${t('进行中')}）` : label,
      }))
    // 当前学期（尚无成绩也保留选项）：学年学期名与后端 semesterLabel 同公式
    const cur = currentSemesterNo.value
    let myGrade = 0
    try { myGrade = Number(JSON.parse(localStorage.getItem('zhixue_user') || '{}').grade) || 0 } catch {}
    if (myGrade < 2000) {
      const mu = me?.data?.data || me?.data || {}
      myGrade = Number(mu.grade) || 0
    }
    if (cur != null && myGrade >= 2000 && !list.some(o => o.id === cur)) {
      const startYear = myGrade + Math.floor((cur - 1) / 2)
      const label = `${startYear}-${startYear + 1}学年${cur % 2 === 1 ? '第一' : '第二'}学期（${t('进行中')}）`
      list.unshift({ id: cur, label })
    }
    semesterOptions.value = [{ id: 'all', label: t('全部学期总览') }, ...list]
    // 默认选中当前学期；当前学期尚无成绩时选最近一个有成绩的学期
    selectedSemester.value = list.some(o => o.id === currentSemesterNo.value)
      ? currentSemesterNo.value
      : 'all'
  } catch {
    loadError.value = t('成绩数据加载失败（服务可能刚重启），请重试')
  }
  loading.value = false
  loadRanking()
  loadGpa()
}
onMounted(load)

/* 兜底：Element Plus 弹层偶发残留 el-popup-parent--hidden（锁 body 滚动），
   会导致成绩档案页「往下滚后滑不上去」。抽屉关闭/组件卸载时强制解锁。 */
function releaseBodyScrollLock() {
  nextTick(() => {
    const b = document.body
    b.classList.remove('el-popup-parent--hidden')
    if (b.style.overflow === 'hidden') b.style.overflow = ''
  })
}
watch(drawerOpen, (open) => { if (!open) releaseBodyScrollLock() })
onBeforeUnmount(releaseBodyScrollLock)
</script>

<style scoped>
.zx-page { display: flex; flex-direction: column; gap: 14px; }
.page-head { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 10px; }
.page-head h2 { margin: 0; color: var(--text); font-size: 20px; }
.gr-filter { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.gf-label { font-size: 12px; color: var(--text-light); }
.gf-note { font-size: 12px; color: var(--text-lighter); }

.gr-error {
  display: flex; align-items: center; gap: 12px;
  background: rgba(239,68,68,.08); border: 1px solid rgba(239,68,68,.3);
  border-radius: 10px; padding: 10px 16px; color: #f87171; font-size: 13px;
}
.gr-loading { min-height: 160px; background: var(--card); border: 1px solid var(--border); border-radius: 13px; }

:deep(.el-table) {
  --el-table-tr-bg-color: var(--card);
  --el-table-header-bg-color: var(--border);
  --el-table-border-color: var(--border);
  --el-table-text-color: var(--text);
  --el-table-header-text-color: var(--primary);
  background: var(--card); border-radius: 8px; overflow: hidden;
}
.muted { color: var(--text-lighter); font-size: 12px; }
.st-text { font-size: 12px; font-weight: 600; }
.st-text.st-success { color: #059669; }
.st-text.st-primary { color: #3b82f6; }
.st-text.st-danger { color: #e11d48; }

/* 补考/重修标记 */
.gr-exam-tag { margin-left: 5px; }
.gr-table :deep(.el-table__row) { cursor: pointer; }
.gd-badge {
  display: inline-block; margin-left: 6px; font-size: 11px; font-weight: 700;
  padding: 1px 8px; border-radius: 999px; vertical-align: 1px; white-space: nowrap;
}
.gd-badge.makeup_todo { color: #b45309; background: rgba(217,119,6,.12); border: 1px dashed rgba(217,119,6,.5); }
.gd-badge.makeup_registered { color: #1d4ed8; background: rgba(37,99,235,.1); border: 1px solid rgba(37,99,235,.35); }
.gd-badge.retake_todo { color: #be123c; background: rgba(190,18,60,.08); border: 1px dashed rgba(190,18,60,.4); }
.gd-badge.retake_registered { color: #b45309; background: rgba(217,119,6,.1); border: 1px solid rgba(217,119,6,.35); }
.gd-badge.retaking { color: #7c3aed; background: rgba(124,58,237,.1); border: 1px solid rgba(124,58,237,.35); }
.gd-badge.makeup_passed, .gd-badge.retake_passed { color: #047857; background: rgba(5,150,105,.1); border: 1px solid rgba(5,150,105,.35); }
html.dark .gd-badge.makeup_todo { color: #fbbf24; }
html.dark .gd-badge.retake_todo { color: #fb7185; }
html.dark .gd-badge.retake_registered { color: #fbbf24; }
html.dark .gd-badge.retaking { color: #c4b5fd; }

/* ===== 常驻补考 / 重修入口 ===== */
.gd-flow-panel { margin-top: 16px; background: var(--card); border: 1px solid var(--border); border-radius: 12px; padding: 14px 16px; }
.gd-flow-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.gd-flow-head b { font-size: 14px; color: var(--text); }
.gd-flow-empty { font-size: 12.5px; color: var(--text-lighter); line-height: 1.7; }
.gd-flow-list { display: flex; flex-direction: column; gap: 8px; }
.gd-flow-row { display: flex; align-items: center; gap: 10px; padding: 8px 10px; border: 1px solid var(--border); border-radius: 9px; }
.gd-flow-row b { font-size: 13px; color: var(--text); }
.gd-flow-row small { font-size: 12px; color: var(--text-lighter); flex: 1; }
.gd-flow-row .gd-badge { margin-left: 0; }

/* ===== 修读流水抽屉 ===== */
.gd-drawer { display: flex; flex-direction: column; gap: 18px; }
.gd-flow { display: flex; flex-direction: column; gap: 0; position: relative; }
.gd-flow-item { display: flex; align-items: flex-start; gap: 10px; padding: 10px 0 14px 18px; position: relative; border-left: 2px solid var(--border); }
.gd-dot { position: absolute; left: -7px; top: 14px; width: 12px; height: 12px; border-radius: 50%; background: #e11d48; border: 2px solid var(--card); }
.gd-flow-item.pass .gd-dot { background: #059669; }
.gd-flow-main { flex: 1; display: flex; flex-direction: column; gap: 2px; }
.gd-flow-main b { font-size: 13px; color: var(--text); }
.gd-flow-main small { font-size: 12px; color: var(--text-lighter); }
.gd-flow-item em { font-style: normal; font-size: 12px; font-weight: 700; color: #e11d48; }
.gd-flow-item.pass em { color: #059669; }
.gd-block { border: 1px solid var(--border); border-radius: 10px; padding: 12px 14px; }
.gd-block-title { font-size: 13px; font-weight: 700; color: var(--text); margin-bottom: 6px; }
.gd-block p { margin: 0 0 8px; font-size: 12.5px; color: var(--text-light); }
.gd-classes { display: flex; flex-direction: column; gap: 8px; }
.gd-class { display: flex; gap: 10px; align-items: flex-start; padding: 10px 12px; border: 1px solid var(--border); border-radius: 9px; cursor: pointer; }
.gd-class.on { border-color: var(--primary); background: rgba(37,99,235,.06); }
.gd-class.conflict { border-style: dashed; }
.gd-class b { display: block; font-size: 13px; color: var(--text); }
.gd-class small { display: block; font-size: 12px; color: var(--text-lighter); margin-top: 2px; }
.gd-conflict { display: inline-block; margin-top: 4px; font-size: 11px; color: #be123c; }
.gd-class-info { padding: 8px 10px; border-radius: 8px; background: rgba(37,99,235,.06); }
.gd-class-info b { color: var(--primary-deep); }

/* ===== 概览统计行：统一高度/圆角/字号 ===== */
.gr-stats-row { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 12px; }
@media (max-width: 1280px) { .gr-stats-row { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 720px)  { .gr-stats-row { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
.gr-stat {
  background: var(--card); border: 1px solid var(--border); border-radius: 12px;
  padding: 13px 16px; display: flex; flex-direction: column; gap: 2px; min-height: 92px;
}
.gr-stat b { font-size: 24px; color: var(--text); font-variant-numeric: tabular-nums; line-height: 1.15; }
.gr-stat b.gold { color: #d97706; }
.gr-stat b.good { color: #059669; }
.gr-stat b.mid { color: #2563eb; }
.gr-stat b.bad { color: #e11d48; }
.gr-stat .gr-of { font-style: normal; font-size: 14px; color: var(--text-lighter); }
.gr-stat span { font-size: 12.5px; font-weight: 600; color: var(--text-light); }
.gr-stat small { font-size: 12px; color: var(--text-lighter); }
.gr-stat.gpa {
  background: linear-gradient(135deg, rgba(245,158,11,.14), rgba(56,189,248,.08));
  border-color: rgba(245,158,11,.35);
}
.gr-stat.gpa b { color: #fbbf24; font-family: 'JetBrains Mono', Consolas, monospace; }

/* ===== 类别均分 + 趋势卡 ===== */
.gr-head { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1.35fr); gap: 14px; align-items: stretch; }
@media (max-width: 1000px) { .gr-head { grid-template-columns: minmax(0, 1fr); } }
.gr-card { background: var(--card); border: 1px solid var(--border); border-radius: 13px; padding: 14px 16px; }
.gr-title { font-size: 13px; font-weight: 700; color: var(--text); margin-bottom: 12px; display: flex; align-items: center; flex-wrap: wrap; gap: 6px; }
.gr-title small { font-weight: 500; font-size: 12px; color: var(--text-light); }
.gr-cats { display: flex; flex-direction: column; gap: 12px; }
.gr-cat-row { display: grid; grid-template-columns: 44px minmax(0, 1fr) 84px; align-items: center; gap: 10px; }
.gr-cat-label { font-size: 12px; color: var(--text-light); }
.gr-cat-track { position: relative; height: 11px; border-radius: 99px; background: rgba(148,163,184,.13); }
.gr-cat-track i { position: absolute; left: 0; top: 0; height: 100%; border-radius: 99px; transition: width .6s cubic-bezier(.22,.9,.36,1); }
.gr-cat-track i.c-bx { background: linear-gradient(90deg, #2bb187, #4cc9a3); }
.gr-cat-track i.c-xx { background: linear-gradient(90deg, #0ea5e9, #38bdf8); }
.gr-cat-track i.c-ts { background: linear-gradient(90deg, #38bdf8, #7dd3fc); }
.gr-cat-track i.c-sj { background: linear-gradient(90deg, #f59e0b, #fbbf24); }
.gr-cat-val { display: flex; align-items: baseline; justify-content: flex-end; gap: 6px; font-size: 13px; font-weight: 800; color: var(--text); font-variant-numeric: tabular-nums; }
.gr-cat-n { font-size: 12px; font-weight: 500; color: var(--text-lighter); }
.gr-nodata { font-size: 12px; color: var(--text-light); opacity: .75; padding: 8px 0; }

/* ===== 趋势折线（限宽限高：桌面端 ≤300px 高） ===== */
.trend-wrap { max-width: 840px; margin: 0 auto; }
.trend-svg { width: 100%; height: auto; display: block; }
.t-legend { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; font-weight: 500; color: var(--text-light); margin-left: auto; }
.t-legend .lg { width: 14px; height: 3px; border-radius: 2px; display: inline-block; }
.t-legend .lg.avg { background: #38bdf8; }
.t-legend .lg.gpa { background: #7dd3fc; }
.t-note { margin-top: 8px; font-size: 12px; color: var(--text-lighter); text-align: center; }
.t-axis { font-size: 12px; fill: var(--text-light); font-weight: 600; }
.t-right { font-size: 12px; fill: var(--text-lighter); }
.t-grid line { stroke: rgba(148,163,184,.18); stroke-dasharray: 3 4; }
.t-grid text { font-size: 12px; fill: var(--text-light); }
.t-line { fill: none; stroke-width: 2.5; stroke-linecap: round; stroke-linejoin: round; }
.t-line.avg { stroke: #38bdf8; filter: drop-shadow(0 2px 6px rgba(56,189,248,.35)); }
.t-line.gpa { stroke: #7dd3fc; filter: drop-shadow(0 2px 6px rgba(125,211,252,.35)); }
.t-dot { stroke: var(--card); stroke-width: 2; }
.t-dot.avg { fill: #38bdf8; }
.t-dot.gpa { fill: #7dd3fc; }
.t-val { font-size: 12px; font-weight: 700; font-variant-numeric: tabular-nums; }
.t-val.avg-t { fill: #7dd3fc; }
.t-val.gpa-t { fill: #c4b5fd; }
.t-x { font-size: 12px; fill: var(--text-light); }

/* ===== 单轮概览 ===== */
.gr-single { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
.gs-item { text-align: center; padding: 12px 8px; border: 1px solid var(--border); border-radius: 10px; background: var(--bg); }
.gs-item b { display: block; font-size: 22px; color: var(--text); font-variant-numeric: tabular-nums; }
.gs-item span { font-size: 12px; color: var(--text-light); }

/* 成绩定位条（紧凑版） */
.gr-pos-track { position: relative; height: 8px; border-radius: 99px; background: rgba(148,163,184,.13); }
.gr-pos-track i {
  position: absolute; top: -3.5px; width: 14px; height: 14px; border-radius: 50%;
  transform: translateX(-50%); background: var(--primary); border: 2.5px solid #fff;
  box-shadow: 0 1px 4px rgba(0,0,0,.3);
}
.gr-pos-track .tick, .gr-cat-track .tick {
  position: absolute; top: -3px; width: 2px; height: 17px; border-radius: 1px;
  background: rgba(148,163,184,.5);
}
.gr-pos-track .tick { height: 13px; top: -2px; }
.gr-footnote { font-size: 12px; color: var(--text-lighter); line-height: 1.7; opacity: .85; }

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
