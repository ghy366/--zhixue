<template>
  <div class="zx-page stats-page liquid">
    <div class="page-head">
      <h2>{{ t('📝 我的选课') }}</h2>
      <div class="ph-actions">
        <el-button type="warning" plain size="small" @click="openResult(true)">{{ t('本轮选课结果') }}</el-button>
        <el-button type="primary" plain size="small" @click="batchMode = !batchMode">
          {{ batchMode ? t('退出批量') : t('批量退课') }}
        </el-button>
      </div>
    </div>

    <!-- ===== 本轮战报：录取 / 待录取 / 落选 / 候补 一目了然 ===== -->
    <div class="me-battle" v-if="enrollments.length || waitlists.length">
      <div class="mb-item ok"><b>{{ approved.length }}</b><span>{{ t('已录取') }}</span></div>
      <div class="mb-item amber"><b>{{ pending.length }}</b><span>{{ t('待录取') }}</span></div>
      <div class="mb-item red" v-if="rejected.length"><b>{{ rejected.length }}</b><span>{{ t('未录取') }}</span></div>
      <div class="mb-item blue"><b>{{ activeWaitlists.length }}</b><span>{{ t('候补中') }}</span></div>
      <div class="mb-item blue" v-if="swaps.length"><b>{{ swaps.length }}</b><span>{{ t('换课保底') }}</span></div>
      <div class="mb-item gold"><b>{{ investedPoints }}</b><span>{{ t('投入积分') }}</span></div>
      <div class="mb-item" v-if="refundedPoints"><b>{{ refundedPoints }}</b><span>{{ t('未录取退还') }}</span></div>
    </div>

    <!-- ===== 换课保底进行中：原子互换语义提示 ===== -->
    <el-alert
      v-if="swaps.length"
      type="info"
      :closable="false"
      show-icon
      class="me-swap-banner"
    >
      <template #title>
        {{ t('换课保底进行中：') }}
        <span v-for="(s, i) in swaps" :key="s.enrollmentId">
          <i v-if="i">；</i>《{{ s.fromCourseName }}》⇄ 《{{ s.toCourseName }}》<b v-if="s.points">（投 {{ s.points }} 分）</b>
        </span>
        {{ t('。结算时新课录取则自动退掉原课完成互换；未录取则原课原样保留、投分退还。') }}
      </template>
    </el-alert>

        <!-- ===== 学期筛选（选课记录按学期查看） ===== -->
    <div class="me-sem-bar">
      <span class="me-sem-label">{{ t('学期') }}</span>
      <el-select class="sem-pill" v-model="viewSem" style="width:300px" @change="onSemChange">
        <template #prefix><span class="sem-cal" v-html="svgIcon('calendar')" /></template>
        <el-option v-for="o in semOptions" :key="String(o.no)" :label="o.label" :value="o.no" />
      </el-select>
      <span class="me-sem-note">{{ t('选课与退课仅限当前学期；切换学期查看历史选课记录（成绩已定档不可退）') }}</span>
    </div>

    <!-- ===== 学分与成绩总览 ===== -->
    <div class="me-head">
      <div class="me-card me-ring-card">
        <svg viewBox="0 0 120 120" class="me-ring" role="img" :aria-label="t('学分占用进度环')">
          <g transform="rotate(-90 60 60)">
            <circle cx="60" cy="60" r="50" class="me-ring-track" />
            <circle
              cx="60" cy="60" r="50"
              class="me-ring-fill"
              :class="{ over: overLimit }"
              :stroke-dasharray="`${creditPct / 100 * RING_C} ${RING_C}`"
              stroke-linecap="round"
            />
          </g>
          <text x="60" y="57" text-anchor="middle" class="me-ring-num">{{ electiveCredits }}</text>
          <text x="60" y="74" text-anchor="middle" class="me-ring-sub">/ {{ maxCredit || t('不限') }} {{ t('选修上限') }}</text>
        </svg>
<div class="me-ring-side">
          <div class="me-ring-title" :class="{ warn: overLimit }">
            {{ overLimit ? t('超出选修学分上限 {n} 学分', { n: electiveCredits - (maxCredit || 0) }) : t('学期选修学分占用（必修不占额度）') }}
          </div>
          <div class="me-ring-line"><span>{{ t('已选课程') }}</span><b>{{ animApproved }} {{ t('门') }}</b></div>
          <div class="me-ring-line"><span>{{ t('本学期选修门数') }}</span><b :class="{ amber: overCourseLimit }">{{ animApprovedElective }} / {{ maxCourses || t('不限') }} {{ t('门') }}</b></div>
          <div class="me-ring-line"><span>{{ t('待审核') }}</span><b :class="{ amber: pending.length }">{{ pending.length }} {{ t('门') }}</b></div>
          <div class="me-ring-line"><span>{{ t('候补中') }}</span><b>{{ animWaitlists }} {{ t('门') }}</b></div>
        </div>
      </div>

      <div class="me-card me-mix-card">
        <div class="me-card-title">{{ t('学分类型构成') }}</div>
        <div class="me-mix-bar" v-if="totalCredits">
          <i v-for="g in mixRows" :key="g.key" :class="g.cls" :style="{ width: g.pct + '%' }" :title="`${g.key} ${g.credit} ${t('学分')}`" />
        </div>
        <div v-else class="me-empty-line">{{ t('还没有已录取的课程') }}</div>
        <div class="me-mix-legend">
          <span v-for="g in mixRows" :key="g.key"><i :class="g.cls" />{{ g.key }} <b>{{ g.credit }}</b></span>
        </div>
      </div>

      <div class="me-card me-grade-card">
        <div class="me-card-title">{{ t('成绩概览') }}</div>
        <div class="me-grade-grid">
          <div class="me-grade-item"><b :class="gradeCls(avgGrade)">{{ gradedRows.length ? animAvgGrade : '—' }}</b><span>{{ t('平均分') }}</span></div>
          <div class="me-grade-item"><b :class="gradeCls(maxGrade)">{{ gradedRows.length ? animMaxGrade : '—' }}</b><span>{{ t('最高分') }}</span></div>
          <div class="me-grade-item"><b>{{ animGraded }}</b><span>{{ t('已出成绩') }}</span></div>
          <div class="me-grade-item"><b>{{ animUngraded }}</b><span>{{ t('未出成绩') }}</span></div>
        </div>
      </div>
    </div>

    <!-- ===== 三轮选课轨迹时间线 ===== -->
    <div class="me-card me-timeline-card">
      <div class="me-card-title">{{ t('三轮选课轨迹') }}</div>
      <div class="tl-grid" v-loading="tlLoading">
        <div class="tl-col" v-for="r in roundRows" :key="r.round" :class="{ cur: r.round === currentRoundNo }">
          <div class="tl-head">
            <span class="tl-badge">{{ t('第 {n} 轮', { n: r.round }) }}</span>
            <span v-if="r.round === currentRoundNo" class="tl-cur-tag">{{ t('进行中') }}</span>
            <span class="tl-sum">{{ r.ok }} {{ t('录取') }}<i>/</i>{{ r.fail }} {{ t('落选') }}<i>/</i>{{ r.points }} {{ t('分') }}</span>
          </div>
          <div class="tl-list" v-if="r.rows.length">
            <div class="tl-item" v-for="row in r.rows" :key="row.id" :class="'st-' + row.status">
              <i class="tl-dot" />
              <div class="tl-item-main">
                <b class="tl-name" :title="row.courseName">{{ row.courseName }}</b>
                <span v-if="row.advisorName" class="tl-adv">{{ t('导师') }}：{{ row.advisorName }}</span>
                <span class="tl-sub">{{ row.category || '—' }} · {{ row.credit }} {{ t('学分') }}</span>
              </div>
              <div class="tl-item-side">
                <span class="tl-pts" v-if="r.round < 3">{{ row.points ?? 0 }} {{ t('分') }}</span>
                <span class="tl-pts na" v-else>{{ t('先到先得') }}</span>
                <span class="tl-status" :class="'tl-st-' + row.status">{{ stText(row.status) }}</span>
              </div>
            </div>
          </div>
          <div class="tl-empty" v-else>{{ t('本轮未参与选课') }}</div>
        </div>
      </div>
    </div>

    <el-tabs v-model="tab" class="zx-tabs">
      <el-tab-pane :label="t('已选课程')" name="approved">
        <el-table :data="approved" v-loading="loading" style="width:100%">
          <el-table-column v-if="batchMode" width="44">
            <template #header><el-checkbox v-model="allApproved" @change="toggleAllApproved" /></template>
            <template #default="{ row }">
              <el-checkbox :model-value="checkedApproved.includes(row.id)" :disabled="isRequired(row)" @change="(v:any)=>toggleOne(row.id, v)" />
            </template>
          </el-table-column>
          <el-table-column prop="courseName" :label="t('课程名')" min-width="160">
            <template #default="{ row }">
              <span>{{ row.courseName }}</span>
              <el-tooltip v-if="row.swapLocked" :content="t('该课已挂为换课保底：新课录取后本课自动退掉；新课落选则本课原样保留')" placement="top">
                <el-tag type="primary" effect="plain" size="small" class="me-swap-tag">🔄 {{ t('换课锁定') }}</el-tag>
              </el-tooltip>
            </template>
          </el-table-column>
          <!-- 毕设课显示学生双选确认的「毕业设计导师」，其余课程显示授课教师。
               两者事实源不同：advisorName 来自 graduate_advisors(approved)，
               teacherName 来自 courses.teacher_id，不能混用。 -->
          <el-table-column :label="t('教师 / 导师')" width="140">
            <template #default="{ row }">
              <span v-if="row.advisorName" class="me-advisor">
                {{ row.advisorName }}<i>{{ t('导师') }}</i>
              </span>
              <span v-else>{{ row.teacherName || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="credit" :label="t('学分')" width="70" align="center" />
          <el-table-column :label="t('类型')" width="100" align="center">
            <template #default="{ row }">
              <span class="me-cat" :class="catClass(row.category)">{{ row.category || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('投分')" width="70" align="center">
            <template #default="{ row }">{{ row.points ?? 0 }}</template>
          </el-table-column>
          <el-table-column :label="t('成绩')" width="90" align="center">
            <template #default="{ row }">
              <span v-if="row.grade != null && row.grade !== ''" class="me-grade" :class="gradeCls(row.grade)">{{ row.grade }}</span>
              <span v-else class="muted">{{ t('未录入') }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('操作')" width="140" align="right">
            <template #default="{ row }">
              <el-tooltip :disabled="!isRequired(row)" :content="t('必修课由教学计划自动安排，不能退课')" placement="top">
                <span>
                  <el-button v-if="row.semesterNo === null || row.semesterNo === currentSemesterNo" size="small" text class="row-drop" :disabled="batchMode || isRequired(row)" @click="drop(row.id)">{{ t('退课') }}</el-button>
                </span>
              </el-tooltip>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane :label="t('待审核')" name="pending">
        <el-table :data="pending" v-loading="loading" style="width:100%">
          <el-table-column prop="courseName" :label="t('课程名')">
            <template #default="{ row }">
              <span>{{ row.courseName }}</span>
              <el-tag v-if="row.swapTarget" type="primary" effect="plain" size="small" class="me-swap-tag">
                🔄 {{ t('换课保底') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="teacherName" :label="t('教师')" width="140" />
          <el-table-column prop="credit" :label="t('学分')" width="80" align="center" />
          <el-table-column :label="t('积分投入')" width="100" align="center">
            <template #default="{ row }">{{ row.points ?? 0 }}</template>
          </el-table-column>
          <el-table-column :label="t('状态')" width="100" align="center">
            <el-tag type="warning" size="small">{{ t('待审核') }}</el-tag>
          </el-table-column>
          <el-table-column :label="t('操作')" width="110" align="right">
            <template #default="{ row }">
              <el-button size="small" text @click="cancel(row.id)">
                {{ row.swapTarget ? t('取消换课') : t('取消') }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane :label="t('候补')" name="waitlist">
        <el-table :data="waitlist" v-loading="loading" style="width:100%">
          <el-table-column prop="courseName" :label="t('课程名')">
            <template #default="{ row }">
              <span>{{ row.courseName }}</span>
              <el-tooltip v-if="row.swapFromName" :content="t('候补确认后将自动退掉《{n}》完成换课', { n: row.swapFromName })" placement="top">
                <el-tag type="primary" effect="plain" size="small" class="me-swap-tag">🔄 {{ t('换课保底') }}</el-tag>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column prop="teacherName" :label="t('教师')" width="140" />
          <el-table-column :label="t('位置')" width="100" align="center">
            <template #default="{ row }">{{ t('第 {n} 位', { n: row.position || '—' }) }}</template>
          </el-table-column>
          <el-table-column :label="t('状态')" width="120" align="center">
            <el-tag :type="wlTagType(row.status)" size="small">{{ wlText(row.status) }}</el-tag>
          </el-table-column>
          <el-table-column :label="t('操作')" width="100" align="right">
            <template #default="{ row }">
              <el-button size="small" text :disabled="row.status !== 'waiting' && row.status !== 'confirming'" @click="cancelWait(row)">{{ t('取消') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-button v-if="batchMode && checkedApproved.length" type="danger" plain @click="batchDrop">
      {{ t('批量退选（{n} 门）', { n: checkedApproved.length }) }}
    </el-button>

    <!-- ===== 选课结果光球动画（沿用登录页光球风格） ===== -->
    <Teleport to="body">
      <div v-if="showResult" class="rez-mask" @click.self="closeResult">
        <div class="rez-card" :class="{ win: resultOk > 0 }">
          <div class="rez-globs" aria-hidden="true">
            <i v-for="n in 7" :key="n" :style="globStyle(n)" />
          </div>
          <div class="rez-head">
            <span class="rez-round">{{ t('第 {n} 轮选课结果', { n: resultRound }) }}</span>
            <button class="rez-x" @click="closeResult">✕</button>
          </div>
          <div class="rez-body">
            <div class="rez-orb" :class="{ win: resultOk > 0 }">
              <span>{{ resultOk }}</span><small>{{ t('中选') }}</small>
            </div>
            <div class="rez-stats">
              <div class="rez-stat ok"><b>{{ resultOk }}</b><span>{{ t('录取') }}</span></div>
              <div class="rez-stat bad"><b>{{ resultFail }}</b><span>{{ t('落选') }}</span></div>
              <div class="rez-stat"><b>{{ resultPending }}</b><span>{{ t('待审核') }}</span></div>
            </div>
            <div class="rez-list" v-if="resultItems.length">
              <div v-for="it in resultItems" :key="it.id" class="rez-item" :class="'st-' + it.status">
                <i class="rez-dot" />
                <span class="rez-name">{{ it.courseName }}</span>
                <span class="rez-pts">{{ it.points }} {{ t('分') }}</span>
                <span class="rez-tag">
                  {{ it.status === 'approved' ? t('中选') : it.status === 'rejected' ? t('落选') : t('待审核') }}
                </span>
                <span v-if="it.status === 'rejected'" class="rez-why">{{ t('原因：投分未进入本轮录取范围') }}</span>
              </div>
            </div>
            <div class="rez-alts" v-if="alts.length">
              <div class="rez-alts-title">{{ t('落选替代推荐 · 同类课程仍有名额') }}</div>
              <div class="rez-alt-row" v-for="a in alts" :key="a.id">
                <b>{{ a.name }}</b>
                <span class="rez-alt-meta">{{ a.credit }} {{ t('学分') }} · {{ a.time }}</span>
                <span class="rez-alt-seat">{{ a.stat?.total || 0 }}/{{ a.capacity }}</span>
              </div>
            </div>
            <div class="rez-tip">{{ t('落选课程的投分已全额返还，可在下方「三轮选课轨迹」查看历史结果。') }}</div>
          </div>
          <div class="rez-foot">
            <button class="rez-btn" @click="closeResult">{{ t('知道了') }}</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import http from '@/api'
import { useCountUp } from '@/utils/useCountUp'
import { t } from '@/i18n'

interface Enrollment { id: number; courseId: number; courseName: string; teacherName: string; credit: number; category: string; status: string; points?: number; grade?: string; swapTarget?: boolean; swapLocked?: boolean; advisorName?: string | null }
interface Waitlist { waitlistId: number; courseId: number; courseName: string; teacherName: string; position: number; status: string; swapFromName?: string }

const loading = ref(false)
const tab = ref('approved')
const auth = useAuthStore()
const enrollments = ref<Enrollment[]>([])
/* ===== 学期切换：查看各学期选课记录（默认当前学期） ===== */
const viewSem = ref<number | null>(null)
const semOptions = ref<{ no: number; label: string }[]>([])
const currentSemesterNo = ref<number | null>(null)

function myGradeOf(): number {
  let g = 0
  try { g = Number(JSON.parse(localStorage.getItem('zhixue_user') || '{}').grade) || 0 } catch {}
  return g
}
function semesterLabelOf(no: number): string {
  const g = myGradeOf()
  if (g < 2000) return `第 ${no} 学期`
  const startYear = g + Math.floor((no - 1) / 2)
  return `${startYear}-${startYear + 1}学年${no % 2 === 1 ? '第一' : '第二'}学期`
}

async function loadSemOptions() {
  try {
    // 学期序单一事实来源：后端（含学期滚动 offset）
    const ms: any = await http.get('/enrollments/my-semester')
    currentSemesterNo.value = ms?.data?.semesterNo ?? mySemesterNo()
  } catch { currentSemesterNo.value = mySemesterNo() }
  const set = new Map<number, string>()
  try {
    const r: any = await http.get('/enrollments/scores')
    for (const g of (r?.data || [])) {
      if (g.semesterNo != null && !set.has(g.semesterNo)) {
        set.set(g.semesterNo, g.semesterLabel || semesterLabelOf(g.semesterNo))
      }
    }
  } catch {}
  const list = [...set.entries()].sort((a, b) => b[0] - a[0]).map(([no, label]) => ({ no, label }))
  const cur = currentSemesterNo.value
  if (cur != null && !list.some(o => o.no === cur)) list.unshift({ no: cur, label: semesterLabelOf(cur) })
  semOptions.value = list
  if (viewSem.value == null || !list.some(o => o.no === viewSem.value)) viewSem.value = cur
}
const waitlists = ref<Waitlist[]>([])
const config = ref<any>({})
const batchMode = ref(false)
const checkedApproved = ref<number[]>([])
/* ===== 换课保底（Swap）：进行中的保底换课（新课 pending + swapFrom 标记） ===== */
const swaps = ref<any[]>([])
const swapTargetIds = computed(() => new Set(swaps.value.map((s: any) => s.toCourseId)))
const swapFromIds = computed(() => new Set(swaps.value.map((s: any) => s.fromCourseId)))

const approved = computed(() => enrollments.value.filter(e => e.status === 'approved'))
const pending = computed(() => enrollments.value.filter(e => e.status === 'pending'))
// 必修课（公共必修/专业必修）由系统自动排课，后端不允许退课
const isRequired = (r: Enrollment) => (r.category || '').includes('必修')
const removable = computed(() => approved.value.filter(e => !isRequired(e)))
const totalCredits = computed(() => approved.value.reduce((s, e) => s + (e.credit || 0), 0))
const maxCredit = computed(() => config.value.maxCreditPerSemester)
const maxCourses = computed(() => config.value.maxCoursesPerSemester)
// 门数上限口径：只统计非必修课（必修由系统自动排课，不占额度），与后端 enroll/结算/候补一致
const approvedElective = computed(() => approved.value.filter(e => !(e.category || '').includes('必修')))
const overCourseLimit = computed(() => !!maxCourses.value && approvedElective.value.length > maxCourses.value)
// 候补中：只计仍在排队 / 待确认递补的记录（已取消 / 已递补 / 已过期不计）
const rejected = computed(() => enrollments.value.filter(e => e.status === 'rejected'))
const investedPoints = computed(() => enrollments.value.filter(e => e.status !== 'rejected').reduce((s2, e) => s2 + (e.points || 0), 0))
const refundedPoints = computed(() => rejected.value.reduce((s2, e) => s2 + (e.points || 0), 0))
const activeWaitlists = computed(() =>
  waitlists.value.filter(w => w.status === 'waiting' || w.status === 'confirming')
)
const allApproved = computed(() => removable.value.length > 0 && checkedApproved.value.length === removable.value.length)

/* ===== 总览可视化 ===== */
// 学分圆环口径：与后端学分上限一致，只统计非必修学分（必修由教学计划自动安排，不占选修学分上限）
const electiveCredits = computed(() => approvedElective.value.reduce((s, e) => s + (e.credit || 0), 0))
const RING_C = 2 * Math.PI * 50
const overLimit = computed(() => !!maxCredit.value && electiveCredits.value > maxCredit.value)
const creditPct = computed(() => {
  const max = Number(maxCredit.value) || 0
  if (max <= 0) return electiveCredits.value > 0 ? 100 : 0
  return Math.min(100, Math.round(electiveCredits.value * 100 / max))
})

function catKey(c: string): '必修' | '选修' | '通识' | '实践' {
  const s = c || ''
  if (s.includes('通识')) return '通识'
  if (s.includes('实践')) return '实践'
  if (s.includes('选修')) return '选修'
  return '必修'
}
const MIX_CLS: Record<string, string> = { '必修': 'c-bx', '选修': 'c-xx', '通识': 'c-ts', '实践': 'c-sj' }
const mixRows = computed(() => {
  const g: Record<string, number> = { '必修': 0, '选修': 0, '通识': 0, '实践': 0 }
  for (const e of approved.value) g[catKey(e.category)] += e.credit || 0
  return (Object.keys(g) as Array<keyof typeof g>)
    .map(key => ({ key, credit: g[key], cls: MIX_CLS[key], pct: totalCredits.value ? Math.round(g[key] * 100 / totalCredits.value) : 0 }))
    .filter(r => r.credit > 0)
})
function catClass(c: string) { return MIX_CLS[catKey(c)] }

const gradedRows = computed(() =>
  approved.value.filter(e => e.grade != null && e.grade !== '' && !isNaN(Number(e.grade)))
)
const avgGrade = computed(() => {
  const g = gradedRows.value
  return g.length ? (g.reduce((s, e) => s + Number(e.grade), 0) / g.length).toFixed(1) : '—'
})
const maxGrade = computed(() => {
  const g = gradedRows.value
  return g.length ? Math.max(...g.map(e => Number(e.grade))) : '—'
})
/* 统计数字滚动（环统计 + 成绩概览；'—' 占位不滚动） */
const animApproved = useCountUp(() => approved.value.length)
const animApprovedElective = useCountUp(() => approvedElective.value.length)
const animPending = useCountUp(() => pending.value.length)
const animWaitlists = useCountUp(() => activeWaitlists.value.length)
const animAvgGrade = useCountUp(() => (gradedRows.value.length ? Number(avgGrade.value) : 0))
const animMaxGrade = useCountUp(() => (gradedRows.value.length ? Number(maxGrade.value) : 0))
const animGraded = useCountUp(() => gradedRows.value.length)
const animUngraded = useCountUp(() => approved.value.length - gradedRows.value.length)

function gradeCls(v: any) {
  const n = Number(v)
  if (isNaN(n)) return ''
  if (n >= 90) return 'g-gold'
  if (n >= 80) return 'g-green'
  if (n >= 60) return 'g-blue'
  return 'g-red'
}

function toggleAllApproved(v: boolean) {
  checkedApproved.value = v ? removable.value.map(e => e.id) : []
}
function toggleOne(id: number, v: boolean) {
  if (v && !checkedApproved.value.includes(id)) checkedApproved.value.push(id)
  if (!v) checkedApproved.value = checkedApproved.value.filter(x => x !== id)
}

async function load() {
  loading.value = true
  try {
    const [e, w, c, s] = await Promise.all([
      http.get('/enrollments/mine' + (viewSem.value != null ? '?semester=' + viewSem.value : '')),
      http.get('/waitlist/mine'),
      http.get('/system/config'),
      http.get('/enrollments/swaps').catch(() => null),
    ])    // 后端字段：enrollmentId / teacherName / credit / category，统一成前端短名
    swaps.value = s?.data || []
    enrollments.value = (e?.data || []).map((x: any) => ({
      id: x.enrollmentId,
      courseId: x.courseId,
      courseName: x.courseName,
      teacherName: x.teacherName || '',
      credit: x.credit || 0,
      category: x.category || '',
      status: x.status,
      points: x.points ?? 0,
      advisorName: x.advisorName || null,
      semesterNo: x.semesterNo ?? null,
      grade: x.grade,
      // 换课状态：新课（保底换课目标）/ 原课（被保底锁定）
      swapTarget: swapTargetIds.value.has(x.courseId),
      swapLocked: x.status === 'approved' && swapFromIds.value.has(x.courseId),
    }))
    waitlists.value = (w?.data || []).map((x: any) => ({
      waitlistId: x.waitlistId,
      courseId: x.courseId,
      courseName: x.courseName,
      teacherName: x.teacherName || '',
      position: x.position,
      status: x.status || 'waiting',
      swapFromName: x.swapFromName || '',
    }))
    config.value = c?.data || {}
  } catch (err: any) {
    ElMessage.error(err?.message || t('加载选课数据失败'))
  }
  loading.value = false
}

function wlText(s: string) {
  return s === 'waiting' ? t('候补中')
    : s === 'confirming' ? t('待确认递补')
    : s === 'confirmed' ? t('已递补')
    : s === 'cancelled' ? t('已取消')
    : s === 'timeout' ? t('已过期')
    : (s || '—')
}
function wlTagType(s: string) {
  return s === 'waiting' ? 'info'
    : s === 'confirming' ? 'warning'
    : s === 'confirmed' ? 'success'
    : 'danger'
}

async function drop(id: number) {
  const row = approved.value.find(r => r.id === id)
  if (!row?.courseId) { ElMessage.error(t('未找到该选课记录，请刷新后重试')); return }
  const locked = !!row.swapLocked
  try {
    await ElMessageBox.confirm(
      locked
        ? t('《{n}》已挂为换课保底，退掉后保底失效（新课转为普通报名）。确定退选？', { n: row.courseName })
        : t('确定退选《{n}》？投入的积分将返还。', { n: row.courseName }),
      t('退课确认'), {
        type: 'warning', confirmButtonText: t('确定退课'), cancelButtonText: t('再想想')
      })
  } catch { return }
  try {
    const r = await http.post('/enrollments/drop', { courseId: row.courseId })
    ElMessage.success(r?.message || t('已退选'))
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || t('退课失败'))
  }
}
async function batchDrop() {
  try {
    await ElMessageBox.confirm(t('确定批量退选 {n} 门？', { n: checkedApproved.value.length }), t('提示'), { type: 'warning', confirmButtonText: t('确定'), cancelButtonText: t('取消') })
  } catch { return }
  try {
    const r = await http.post('/enrollments/drop-batch', { courseIds: checkedApproved.value })
    ElMessage.success(r?.message || t('批量退选完成'))
    checkedApproved.value = []
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || t('批量退课失败'))
  }
}
// 待审核 → 撤销报名：后端没有 DELETE /enrollments/{id}，统一走 /enrollments/drop（pending 会退积分）
async function cancel(id: number) {
  const row = pending.value.find(r => r.id === id)
  if (!row?.courseId) { ElMessage.error(t('未找到该报名记录，请刷新后重试')); return }
  const isSwap = !!row.swapTarget
  try {
    await ElMessageBox.confirm(
      isSwap
        ? t('确定取消《{n}》的换课保底？原课继续保留，已投入的 {p} 积分将返还。', { n: row.courseName, p: row.points ?? 0 })
        : t('确定取消《{n}》的报名？已投入的 {p} 积分将返还。', { n: row.courseName, p: row.points ?? 0 }),
      isSwap ? t('取消换课') : t('取消报名'), {
        type: 'warning', confirmButtonText: t('确定取消'), cancelButtonText: t('再想想')
      })
  } catch { return }
  try {
    const r = await http.post('/enrollments/drop', { courseId: row.courseId })
    ElMessage.success(r?.message || (isSwap ? t('已取消换课') : t('已取消报名')))
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || t('取消失败'))
  }
}
async function cancelWait(row: Waitlist) {
  try {
    await ElMessageBox.confirm(t('确定取消《{n}》的候补？', { n: row.courseName }), t('取消候补'), {
      type: 'warning', confirmButtonText: t('确定取消'), cancelButtonText: t('再想想')
    })
  } catch { return }
  try {
    const r = await http.post('/waitlist/cancel', { courseId: row.courseId })
    ElMessage.success(r?.message || t('已取消候补'))
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || t('取消候补失败'))
  }
}

onMounted(async () => { await loadSemOptions(); await load(); loadTimeline().then(() => maybeShowResult()) })

/* ===== 三轮选课轨迹时间线 ===== */
interface TlRow { id: number; courseName: string; category: string; credit: number; points: number; status: string }
const tlLoading = ref(false)
const currentRoundNo = ref(1)
const roundRows = ref<Array<{ round: number; rows: TlRow[]; ok: number; fail: number; points: number }>>([])
function stText(s: string) {
  return s === 'approved' ? t('已录取')
    : s === 'pending' ? t('待审核')
    : s === 'rejected' ? t('已落选')
    : s === 'dropped' ? t('已退课')
    : (s || '—')
}
async function onSemChange() {
  await load()
  await loadTimeline()
}

async function loadTimeline() {
  tlLoading.value = true
  try {
    const cfg = await http.get('/system/config').catch(() => null)
    currentRoundNo.value = cfg?.data?.currentRound || 1
    const rs = await Promise.all([1, 2, 3].map(r =>
      http.get('/enrollments/mine', { params: { round: r, semester: viewSem.value } }).catch(() => null)
    ))
    roundRows.value = [1, 2, 3].map((r, i) => {
      const rows: TlRow[] = (rs[i]?.data || [])
        // 必修课由系统自动排课，不属于学生主动选课，不进轨迹
        .filter((x: any) => !(x.category || '').includes('必修'))
        .map((x: any) => ({
        id: x.enrollmentId,
        courseName: x.courseName || t('（课程已删除）'),
        category: x.category || '',
        credit: x.credit || 0,
        points: x.points ?? 0,
        status: x.status || 'pending',
      }))
      return {
        round: r,
        rows,
        ok: rows.filter(x => x.status === 'approved').length,
        fail: rows.filter(x => x.status === 'rejected').length,
        points: rows.reduce((s, x) => s + (x.points || 0), 0),
      }
    })
  } catch { /* 时间线失败不影响主页面 */ }
  tlLoading.value = false
}

/* ===== 选课结果光球动画 ===== */
const showResult = ref(false)
const resultRound = ref(1)
const resultItems = ref<TlRow[]>([])
const alts = ref<any[]>([])
const rezKey = ref('')
const resultOk = computed(() => resultItems.value.filter(i => i.status === 'approved').length)
const resultFail = computed(() => resultItems.value.filter(i => i.status === 'rejected').length)
const resultPending = computed(() => resultItems.value.filter(i => i.status === 'pending').length)

const GLOB_COLORS = [
  'rgba(43,177,135,.55)', 'rgba(56,189,248,.5)', 'rgba(34,211,238,.45)',
  'rgba(74,222,128,.4)', 'rgba(244,114,182,.4)',
]
function globStyle(n: number) {
  const size = 70 + ((n * 53) % 110)
  const left = (n * 37) % 100
  return {
    left: left + '%',
    width: size + 'px',
    height: size + 'px',
    background: `radial-gradient(circle at 35% 30%, ${GLOB_COLORS[(n - 1) % GLOB_COLORS.length]}, transparent 68%)`,
    animationDelay: ((n * 0.7) % 4).toFixed(2) + 's',
    animationDuration: 7 + (n % 4) + 's',
  }
}

/** 找到最近一个有终态结果（录取/落选）的轮次 */
function latestResultRound() {
  return [...roundRows.value].reverse()
    .find(r => r.rows.some(x => x.status === 'approved' || x.status === 'rejected'))
}

function makeKey(r: number) {
  return `zhixue_rez_${auth.user?.id || 'me'}_${r}_${resultOk.value}_${resultFail.value}`
}

function openResult(force = false) {
  const target = latestResultRound()
    || roundRows.value.find(r => r.round === currentRoundNo.value && r.rows.length)
  let rows = target
    ? target.rows.filter(x => ['approved', 'rejected', 'pending'].includes(x.status))
    : []
  // 兜底：时间线无数据时用当前在册课程构造结果
  if (!rows.length && enrollments.value.length) {
    rows = enrollments.value.map((e: any) => ({
      id: e.enrollmentId,
      courseName: e.courseName || t('（课程已删除）'),
      category: e.category || '',
      credit: e.credit || 0,
      points: e.points ?? 0,
      status: e.status,
    })) as TlRow[]
  }
  resultRound.value = target?.round ?? currentRoundNo.value
  resultItems.value = rows
  rezKey.value = makeKey(resultRound.value)
  alts.value = []
  showResult.value = true
  if (rows.length) loadAlts()
}

function maybeShowResult() {
  const target = latestResultRound()
  if (!target) return
  resultRound.value = target.round
  resultItems.value = target.rows.filter(x => ['approved', 'rejected', 'pending'].includes(x.status))
  const key = makeKey(target.round)
  if (localStorage.getItem(key)) return
  rezKey.value = key
  showResult.value = true
  loadAlts()
}

function closeResult() {
  showResult.value = false
  if (rezKey.value) localStorage.setItem(rezKey.value, '1')
}

/** 落选课程推荐：同类且有余额 */
async function loadAlts() {
  const bad = [...new Set(resultItems.value.filter(i => i.status === 'rejected').map(i => i.category))]
  if (!bad.length) return
  try {
    const r: any = await http.get('/courses')
    const enrolled = new Set(enrollments.value.map(e => e.courseId))
    const pool = (r?.data || []).filter((c: any) => !enrolled.has(c.id) && !c.full)
    const sameCat = pool.filter((c: any) => bad.includes(c.category))
    alts.value = (sameCat.length ? sameCat : pool).slice(0, 3)
  } catch { alts.value = [] }
}
</script>

<style scoped>
.zx-page { display: flex; flex-direction: column; gap: 14px; }
.page-head { display: flex; justify-content: space-between; align-items: center; }
.page-head h2 { margin: 0; color: var(--text); font-size: 20px; }

/* ===== 学分与成绩总览 ===== */
.me-head { display: grid; grid-template-columns: auto minmax(0, 1fr) minmax(0, 1.1fr); gap: 14px; align-items: stretch; }
@media (max-width: 1100px) { .me-head { grid-template-columns: 1fr 1fr; } }
@media (max-width: 760px) { .me-head { grid-template-columns: minmax(0, 1fr); } }
.me-card { background: var(--card); border: 1px solid var(--border); border-radius: 13px; padding: 14px 16px; }
.me-card-title { font-size: 13px; font-weight: 700; color: var(--text); margin-bottom: 10px; }

/* ===== 三轮选课轨迹时间线 ===== */
.me-timeline-card { margin-bottom: 14px; }
.tl-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
@media (max-width: 860px) { .tl-grid { grid-template-columns: minmax(0, 1fr); } }
.tl-col { border: 1px solid var(--border); border-radius: 11px; padding: 10px 12px; background: rgba(148,163,184,.05); min-height: 120px; }
.tl-col.cur { border-color: rgba(43,177,135,.45); background: rgba(43,177,135,.06); }
.tl-head { display: flex; align-items: center; gap: 7px; margin-bottom: 9px; flex-wrap: wrap; }
.tl-badge { font-size: 12px; font-weight: 800; color: var(--text); }
.tl-cur-tag { font-size: 12px; font-weight: 700; color: #4cc9a3; background: rgba(43,177,135,.14); padding: 1px 7px; border-radius: 999px; }
.tl-sum { margin-left: auto; font-size: 12px; color: var(--text-light); white-space: nowrap; }
.tl-sum i { font-style: normal; margin: 0 3px; opacity: .5; }
.tl-list { display: flex; flex-direction: column; gap: 6px; }
.tl-item { display: flex; align-items: center; gap: 8px; padding: 6px 8px; border-radius: 8px; background: rgba(148,163,184,.08); }
.tl-dot { width: 8px; height: 8px; border-radius: 99px; flex-shrink: 0; }
.tl-item.st-approved .tl-dot { background: #10b981; box-shadow: 0 2px 8px rgba(0,0,0,.16); }
.tl-item.st-pending .tl-dot { background: #f59e0b; box-shadow: 0 0 6px rgba(245,158,11,.5); }
.tl-item.st-rejected .tl-dot { background: #f43f5e; }
.tl-item.st-dropped .tl-dot { background: #94a3b8; }
.tl-item-main { min-width: 0; flex: 1; }
.tl-name { display: block; font-size: 12px; font-weight: 700; color: var(--text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tl-sub { font-size: 12px; color: var(--text-light); }
.tl-item-side { text-align: right; flex-shrink: 0; }
.tl-pts { display: block; font-size: 12px; font-weight: 800; color: var(--text); font-variant-numeric: tabular-nums; }
.tl-pts.na { font-weight: 600; font-size: 12px; color: var(--text-light); }
.tl-status { font-size: 12px; font-weight: 700; padding: 1px 6px; border-radius: 999px; }
.tl-st-approved { color: #059669; background: rgba(16,185,129,.13); }
.tl-st-pending { color: #d97706; background: rgba(245,158,11,.14); }
.tl-st-rejected { color: #e11d48; background: rgba(244,63,94,.13); }
.tl-st-dropped { color: #64748b; background: rgba(100,116,139,.16); }
.tl-empty { font-size: 12px; color: var(--text-light); padding: 18px 0; text-align: center; }

.me-ring-card { display: flex; align-items: center; gap: 14px; }
.me-ring { width: 108px; height: 108px; flex-shrink: 0; }
.me-ring-track { fill: none; stroke: rgba(148,163,184,.16); stroke-width: 11; }
.me-ring-fill { fill: none; stroke: #10b981; stroke-width: 11; transition: stroke-dasharray .7s cubic-bezier(.22,.9,.36,1); }
.me-ring-fill.over { stroke: #f43f5e; }
.me-ring-num { font-size: 24px; font-weight: 800; fill: var(--text); }
.me-ring-sub { font-size: 12px; fill: var(--text-light); }
.me-ring-side { display: flex; flex-direction: column; gap: 5px; }
.me-ring-title { font-size: 13px; font-weight: 700; color: var(--text); }
.me-ring-title.warn { color: #e11d48; }
.me-ring-line { display: flex; justify-content: space-between; gap: 14px; font-size: 12px; color: var(--text-light); }
.me-ring-line b { color: var(--text); font-variant-numeric: tabular-nums; }
.me-ring-line b.amber { color: #d97706; }

.me-mix-bar { display: flex; height: 14px; border-radius: 99px; overflow: hidden; background: rgba(148,163,184,.12); margin: 4px 0 12px; }
.me-mix-bar i { display: block; height: 100%; transition: width .6s cubic-bezier(.22,.9,.36,1); }
.me-mix-bar .c-bx { background: linear-gradient(90deg, #2bb187, #4cc9a3); }
.me-mix-bar .c-xx { background: linear-gradient(90deg, #0ea5e9, #22d3ee); }
.me-mix-bar .c-ts { background: linear-gradient(90deg, #38bdf8, #7dd3fc); }
.me-mix-bar .c-sj { background: linear-gradient(90deg, #f59e0b, #fbbf24); }
.me-mix-legend { display: flex; flex-wrap: wrap; gap: 8px 14px; font-size: 12px; color: var(--text-light); }
.me-mix-legend i { display: inline-block; width: 10px; height: 10px; border-radius: 3px; margin-right: 5px; vertical-align: middle; }
.me-mix-legend .c-bx { background: #2bb187; }
.me-mix-legend .c-xx { background: #0ea5e9; }
.me-mix-legend .c-ts { background: #38bdf8; }
.me-mix-legend .c-sj { background: #f59e0b; }
.me-mix-legend b { color: var(--text); margin-left: 2px; }
.me-empty-line { font-size: 12.5px; color: var(--text-light); padding: 16px 0; }

.me-grade-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 9px; }
@media (max-width: 480px) { .me-grade-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
.me-grade-item { padding: 9px 10px; border: 1px solid var(--border); border-radius: 10px; text-align: center; display: flex; flex-direction: column; gap: 2px; }
.me-grade-item b { font-size: 17px; color: var(--text); font-variant-numeric: tabular-nums; }
.me-grade-item span { font-size: 12px; color: var(--text-light); }

/* 毕业设计导师：与授课教师区分，青绿徽章标注 */
.me-advisor { display: inline-flex; align-items: center; gap: 5px; color: var(--green-text); font-weight: 600; }
.me-advisor i { font-style: normal; font-size: 11px; font-weight: 500; padding: 1px 6px; border-radius: 999px; background: rgba(16,185,129,.14); color: #0e7a58; }
[data-theme="dark"] .me-advisor i { background: rgba(24,214,192,.16); color: #8ff2e4; }

/* 类型与成绩徽章 */
.me-cat { font-size: 12px; padding: 2px 9px; border-radius: 999px; white-space: nowrap; }
.me-cat.c-bx { background: rgba(100,116,139,.14); color: #475569; }
.me-cat.c-xx { background: rgba(31,166,122,.14); color: #0e7a58; }
.me-cat.c-ts { background: rgba(56,189,248,.16); color: #0369a1; }
.me-cat.c-sj { background: rgba(245,158,11,.16); color: #b45309; }
.me-grade { font-size: 13px; font-weight: 800; padding: 2px 10px; border-radius: 8px; font-variant-numeric: tabular-nums; }
.me-grade.g-gold { color: #d97706; background: rgba(245,158,11,.16); }
.me-grade.g-green { color: #059669; background: rgba(16,185,129,.14); }
.me-grade.g-blue { color: #2563eb; background: rgba(59,130,246,.13); }
.me-grade.g-red { color: #e11d48; background: rgba(244,63,94,.13); }

:deep(.el-tabs__item) { color: var(--text-light) !important; font-size: 14px; }
:deep(.el-tabs__item.is-active) { color: var(--green-text) !important; font-weight: 600; }
:deep(.el-tabs__active-bar) { background-color: var(--green-text) !important; }
:deep(.el-table) {
  --el-table-tr-bg-color: var(--card);
  --el-table-header-bg-color: var(--table-head-bg);
  --el-table-border-color: var(--grid-line);
  --el-table-text-color: var(--text);
  --el-table-header-text-color: var(--green-text);
  --el-table-row-hover-bg-color: var(--card-bg-2);
  background: var(--card);
  border-radius: 8px; overflow: hidden;
  font-size: 14px;
}
:deep(.el-table th.el-table__cell) { font-weight: 600; }
.muted { color: var(--text-light); }
.ph-actions { display: flex; gap: 8px; align-items: center; }

/* ===== 选课结果光球动画 ===== */
.rez-mask {
  position: fixed; inset: 0; z-index: 3000;
  background: rgba(6, 10, 20, 0.78);
  backdrop-filter: blur(10px); -webkit-backdrop-filter: blur(10px);
  display: flex; align-items: center; justify-content: center; padding: 20px;
  animation: rezFade .3s ease;
}
@keyframes rezFade { from { opacity: 0 } to { opacity: 1 } }
.rez-card {
  position: relative; width: 470px; max-width: 100%; max-height: 88vh; overflow: hidden;
  display: flex; flex-direction: column;
  background: linear-gradient(165deg, rgba(30,41,59,.96), rgba(15,23,42,.98));
  border: 1px solid rgba(148,163,184,.22);
  border-radius: 20px; box-shadow: 0 30px 90px rgba(0,0,0,.6);
  animation: rezRise .45s cubic-bezier(.22,.9,.36,1);
}
.rez-card.win { border-color: rgba(52,211,153,.4); box-shadow: 0 30px 90px rgba(0,0,0,.35); }
@keyframes rezRise { from { transform: translateY(22px) scale(.96); opacity: 0 } to { transform: none; opacity: 1 } }

/* 背景光球 */
.rez-globs { position: absolute; inset: 0; overflow: hidden; pointer-events: none; }
.rez-globs i {
  position: absolute; bottom: -160px; border-radius: 50%;
  filter: blur(22px); opacity: .75;
  animation-name: rezFloat; animation-timing-function: ease-in-out; animation-iteration-count: infinite;
}
@keyframes rezFloat {
  0%   { transform: translateY(0) scale(1); }
  50%  { transform: translateY(-62vh) scale(1.18); }
  100% { transform: translateY(-120vh) scale(.92); }
}

.rez-head {
  position: relative; z-index: 1;
  display: flex; align-items: center; justify-content: space-between;
  padding: 16px 20px 10px;
}
.rez-round { font-size: 15px; font-weight: 700; color: #e2e8f0; letter-spacing: .5px; }
.rez-x {
  background: rgba(148,163,184,.14); border: none; color: #cbd5e1;
  width: 26px; height: 26px; border-radius: 50%; cursor: pointer; font-size: 12px;
}
.rez-x:hover { background: rgba(148,163,184,.26); }

.rez-body { position: relative; z-index: 1; padding: 4px 20px 8px; overflow-y: auto; }

/* 中选光球 */
.rez-orb {
  width: 108px; height: 108px; margin: 6px auto 14px; border-radius: 50%;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  background: radial-gradient(circle at 32% 28%, rgba(148,163,184,.35), rgba(30,41,59,.9) 70%);
  border: 1px solid rgba(148,163,184,.35);
  box-shadow: 0 0 40px rgba(148,163,184,.25), inset 0 0 30px rgba(255,255,255,.06);
  animation: orbBreath 3s ease-in-out infinite;
}
.rez-orb.win {
  background: radial-gradient(circle at 32% 28%, rgba(110,231,183,.75), rgba(16,185,129,.35) 45%, rgba(15,23,42,.95) 78%);
  border-color: rgba(52,211,153,.6);
  box-shadow: 0 2px 8px rgba(0,0,0,.16), inset 0 0 34px rgba(255,255,255,.12);
}
.rez-orb span { font-size: 34px; font-weight: 800; color: #fff; line-height: 1; }
.rez-orb small { font-size: 12px; color: rgba(255,255,255,.8); margin-top: 4px; letter-spacing: 1px; }
@keyframes orbBreath {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.045); }
}

.rez-stats { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-bottom: 14px; }
.rez-stat {
  text-align: center; padding: 9px 6px; border-radius: 10px;
  background: rgba(148,163,184,.1); border: 1px solid rgba(148,163,184,.16);
}
.rez-stat b { display: block; font-size: 20px; font-weight: 800; color: #e2e8f0; font-variant-numeric: tabular-nums; }
.rez-stat span { font-size: 12px; color: #94a3b8; }
.rez-stat.ok b { color: #34d399; }
.rez-stat.bad b { color: #fb7185; }

.rez-list { display: flex; flex-direction: column; gap: 6px; margin-bottom: 10px; }
.rez-item {
  display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
  padding: 8px 10px; border-radius: 9px; font-size: 12px;
  background: rgba(148,163,184,.08);
}
.rez-dot { width: 7px; height: 7px; border-radius: 50%; flex-shrink: 0; background: #94a3b8; }
.rez-item.st-approved .rez-dot { background: #34d399; box-shadow: 0 0 6px rgba(52,211,153,.7); }
.rez-item.st-rejected .rez-dot { background: #fb7185; }
.rez-item.st-pending .rez-dot { background: #fbbf24; }
.rez-name { flex: 1; min-width: 0; font-weight: 600; color: #e2e8f0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rez-pts { color: #cbd5e1; font-variant-numeric: tabular-nums; }
.rez-tag { font-size: 12px; font-weight: 700; padding: 1px 7px; border-radius: 999px; background: rgba(148,163,184,.16); color: #cbd5e1; }
.rez-item.st-approved .rez-tag { background: rgba(16,185,129,.18); color: #6ee7b7; }
.rez-item.st-rejected .rez-tag { background: rgba(244,63,94,.18); color: #fda4af; }
.rez-item.st-pending .rez-tag { background: rgba(245,158,11,.18); color: #fcd34d; }
.rez-why { flex-basis: 100%; font-size: 12px; color: #fda4af; padding-left: 15px; }

.rez-alts { margin-top: 4px; padding: 10px 12px; border-radius: 10px; background: rgba(43,177,135,.1); border: 1px solid rgba(43,177,135,.22); }
.rez-alts-title { font-size: 12px; font-weight: 700; color: #9adfcb; margin-bottom: 7px; }
.rez-alt-row { display: flex; align-items: center; gap: 8px; font-size: 12px; padding: 4px 0; }
.rez-alt-row b { color: #e2e8f0; flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rez-alt-meta { color: #94a3b8; font-size: 12px; }
.rez-alt-seat { color: #6ee7b7; font-size: 12px; font-variant-numeric: tabular-nums; }

.rez-tip { margin-top: 10px; font-size: 12px; color: #94a3b8; line-height: 1.5; }

.rez-foot { position: relative; z-index: 1; padding: 10px 20px 16px; }
.rez-btn {
  width: 100%; padding: 10px; border: none; border-radius: 10px; cursor: pointer;
  font-size: 13.5px; font-weight: 600; color: #fff;
  background: linear-gradient(135deg, #2bb187, #38bdf8);
  transition: all .18s;
}
.rez-btn:hover { transform: translateY(-1px); box-shadow: 0 8px 24px rgba(43,177,135,.45); }
@media (prefers-reduced-motion: reduce) {
  .rez-globs i, .rez-orb { animation: none !important; }
}
/* 行内退课：平时低调灰色，hover 才显红（避免整屏红点） */
.el-button.row-drop { color: var(--text-light); }
.el-button.row-drop:hover, .el-button.row-drop:focus { color: var(--danger); }
/* ===== 本轮战报条 ===== */
.me-battle { display: grid; grid-template-columns: repeat(auto-fit, minmax(120px, 1fr)); gap: 10px; }
.mb-item { padding: 12px 14px; border-radius: 12px; border: 1px solid var(--border); background: var(--card-bg);
  display: flex; align-items: baseline; gap: 8px; }
.mb-item b { font-size: 22px; font-weight: 800; color: var(--text); font-variant-numeric: tabular-nums; }
.mb-item span { font-size: 12px; color: var(--text-light); }
.mb-item.ok b { color: #059669; }
.mb-item.amber b { color: #d97706; }
.mb-item.red b { color: #e11d48; }
.mb-item.blue b { color: #0284c7; }
.mb-item.gold b { color: #b45309; }
html.dark .mb-item.ok b { color: #34d399; }
html.dark .mb-item.amber b { color: #fbbf24; }
html.dark .mb-item.red b { color: #fb7185; }
html.dark .mb-item.blue b { color: #38bdf8; }
html.dark .mb-item.gold b { color: #fbbf24; }
.tl-adv { font-size: 11px; font-weight: 700; color: #0d7a6c; background: rgba(13,148,136,.1);
  padding: 1px 7px; border-radius: 999px; margin-left: 6px; }
/* ===== 学期筛选条 ===== */
.me-sem-bar { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; flex-wrap: wrap; }
.me-sem-label { font-size: 13px; color: var(--text-light); }
.me-sem-note { font-size: 12px; color: var(--text-lighter); }
/* ===== 换课保底 ===== */
.me-swap-banner { border-radius: 10px; }
.me-swap-banner b { color: #0369a1; font-weight: 700; }
html.dark .me-swap-banner b { color: #7dd3fc; }
.me-swap-tag { margin-left: 6px; }

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