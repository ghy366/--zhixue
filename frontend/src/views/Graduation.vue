<template>
  <div class="page-wrap stats-page liquid">

    <!-- ===== 视图 Tab ===== -->
    <div class="grad-tabs">
      <button class="grad-tab" :class="{ on: gradTab === 'overview' }" @click="gradTab = 'overview'">{{ t('毕业总览') }}</button>
      <button class="grad-tab" :class="{ on: gradTab === 'semesters' }" @click="gradTab = 'semesters'">{{ t('按学期进度') }}</button>
    </div>
    <!-- ===== 毕业进度主视觉 ===== -->
    <div class="grad-hero" v-if="summary" v-show="gradTab === 'overview'">
      <!-- 左：总进度大环 -->
      <div class="grad-hero-left">
        <div class="grad-ring-box">
          <svg viewBox="0 0 220 220" role="img" :aria-label="t('毕业总进度环')">
            <defs>
              <linearGradient id="gradRing" x1="0%" y1="0%" x2="100%" y2="100%">
                <stop offset="0%" stop-color="#2bb187" />
                <stop offset="55%" stop-color="#0d9488" />
                <stop offset="100%" stop-color="#14b8a6" />
              </linearGradient>
            </defs>
            <circle cx="110" cy="110" r="92" class="ring-halo" />
            <g transform="rotate(-90 110 110)">
              <circle cx="110" cy="110" r="88" class="ring-track" />
              <circle
                cx="110" cy="110" r="88"
                class="ring-fill"
                stroke="url(#gradRing)"
                :stroke-dasharray="`${(clampPct / 100) * BIG_C} ${BIG_C}`"
              />
            </g>
            <!-- 刻度：每 25% 一格 -->
            <g class="ring-ticks">
              <line v-for="t in [0, 25, 50, 75]" :key="'tk' + t"
                :x1="110 + 76 * Math.cos((t - 90) * Math.PI / 180)"
                :y1="110 + 76 * Math.sin((t - 90) * Math.PI / 180)"
                :x2="110 + 88 * Math.cos((t - 90) * Math.PI / 180)"
                :y2="110 + 88 * Math.sin((t - 90) * Math.PI / 180)"
              />
            </g>
          </svg>
          <div class="ring-center">
            <span class="ring-badge" :class="appApproved ? 'ok' : (allDone ? 'ok' : 'going')">
              {{ appApproved ? t('🎓 已批准毕业') : (allDone ? t('已满足毕业要求') : t('毕业要求进行中')) }}
            </span>
            <div class="ring-pct">{{ dispPct }}<small>%</small></div>
            <div class="ring-sub">{{ t('已获') }} <b>{{ dispEarned }}</b> / {{ sumRequired }} {{ t('学分') }}</div>
            <div class="ring-gap" :class="{ done: !sumGap && allDone }">
              {{ allDone ? t('全部达标 · 恭喜') : (sumGap ? t('还差 {n} 学分', { n: sumGap }) : (coursesGap ? t('课程数还差 {n} 门', { n: coursesGap }) : t('必修课未全部通过'))) }}
            </div>
          </div>
        </div>
      </div>

      <!-- 右：四类别构成 -->
      <div class="grad-hero-right">
        <div class="ghr-head">
          <b>{{ t('毕业要求构成') }}</b>
          <span class="ghr-tip">{{ t('{a} / {b} 类达标 · 已获学分按「总评 ≥ 60」统计', { a: doneCount, b: cats.length }) }}</span>
        </div>
        <div class="ghr-req">
          <span class="ghr-req-tag" :class="summary.requirementSource === 'grade' ? 'grade' : 'default'">
            {{ summary.requirementLabel || t('全校默认要求') }}
          </span>
          <span class="ghr-req-note">{{ t('学分 + 课程数 双达标') }}</span>
        </div>

        <div v-for="c in cats" :key="c.key" class="cat-row" :class="{ done: c.done }">
          <div class="cat-line">
            <span class="cat-ico" :style="{ background: softBg(c.key), color: CAT_COLORS[c.key] }">
              <span v-html="svgIcon(icons[c.key] || 'star')" />
            </span>
            <span class="cat-name">{{ c.key }}</span>
            <div class="cat-bar">
              <i :style="{ width: pctOf(c) + '%', background: barGrad(c) }" />
              <em v-if="!c.done" :style="{ left: pctOf(c) + '%' }" />
            </div>
            <span class="cat-nums"><b :style="{ color: c.done ? '#34d399' : CAT_COLORS[c.key] }">{{ c.earned }}</b>/{{ c.required }}</span>
            <span class="cat-state" :class="c.done ? 'ok' : 'gap'">{{ c.done ? t('达标') : t('差 {n}', { n: c.gap }) }}</span>
          </div>
          <!-- 必修：未通过课程清单（本轮已在修的标「重修中」） -->
          <div v-if="c.key === '必修' && missingRow(c).length" class="cat-missing">
            <span class="cm-label">{{ t('未通过 / 未修：') }}</span>
            <span v-for="d in missingRow(c).slice(0, 6)" :key="d.name" class="cm-chip" :class="{ retaking: d.retaking }">
              {{ d.name }}<i v-if="d.retaking" class="cm-flag">{{ t('重修中') }}</i>
            </span>
            <span v-if="missingRow(c).length > 6" class="cm-more">{{ t('等 {n} 门', { n: missingRow(c).length }) }}</span>
          </div>
        </div>

        <!-- 课程数双达标进度 -->
        <div v-if="coursesProgress && coursesProgress.required > 0" class="cat-row" :class="{ done: coursesProgress.done }">
          <div class="cat-line">
            <span class="cat-ico" style="background: rgba(100,116,139,.14); color: #475569;">
              <span v-html="svgIcon('clipboard')" />
            </span>
            <span class="cat-name">{{ t('课程数') }}</span>
            <div class="cat-bar">
              <i :style="{ width: (coursesProgress.percent || 0) + '%', background: 'linear-gradient(90deg,#475569,#94a3b8)' }" />
              <em v-if="!coursesProgress.done" :style="{ left: (coursesProgress.percent || 0) + '%' }" />
            </div>
            <span class="cat-nums"><b :style="{ color: coursesProgress.done ? '#34d399' : '#475569' }">{{ coursesProgress.earned }}</b>/{{ coursesProgress.required }}</span>
            <span class="cat-state" :class="coursesProgress.done ? 'ok' : 'gap'">{{ coursesProgress.done ? t('达标') : t('差 {n}', { n: coursesProgress.gap }) }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- ===== 毕业申请（申请-审核工作流） ===== -->
    <div class="section grad-apply-section" v-if="allDone">
      <div class="ga-apply">
        <div class="ga-apply-main">
          <b>{{ appApproved ? t('🎓 恭喜！毕业申请已批准，学位授予完成') : appPending ? t('毕业申请审核中——教务正在核验你的学分与课程') : appRejected ? t('毕业申请被驳回：') + (appRemark || '') + t('（补充要求后可重新提交）') : t('你已满足全部毕业要求，可向教务提交毕业申请') }}</b>
          <span v-if="thesisScore != null" class="ga-thesis-score">🎯 {{ t('毕设总评') }} <b>{{ thesisScore }}</b><small>{{ t('（导师 40% + 答辩 60%）') }}</small></span>
        </div>
        <button v-if="!applicationStatus || applicationStatus === 'NONE' || applicationStatus === 'REJECTED'"
          class="btn btn-primary" :disabled="applying" @click="applyGraduation">
          {{ applying ? t('提交中…') : applicationStatus === 'REJECTED' ? t('重新提交申请') : t('提交毕业申请') }}
        </button>
        <span v-else-if="appPending" class="ga-pending-pill">{{ t('审核中') }}</span>
        <span v-else-if="appApproved" class="ga-ok-pill">{{ t('已授予学位') }}</span>
      </div>
    </div>

    <!-- ===== 必修课 · 按学期进度（培养方案：每学期必修不同，挂一学期必修即无法毕业） ===== -->
    <div class="section" v-if="semesters.length" v-show="gradTab === 'semesters'">
      <div class="section-header">
        <div class="section-title" v-html="svgIcon('calendar') + ' ' + t('必修 · 按学期进度')" />
        <span class="rec-note">{{ t('培养方案每学期排定不同必修课 · 任一学期必修未通过都无法毕业') }}</span>
      </div>
      <div class="sem-filter">
        <span class="sem-filter-label">{{ t('查看') }}</span>
        <el-select class="sem-pill" v-model="semView" style="width:320px">
          <template #prefix><span class="sem-cal" v-html="svgIcon('calendar')" /></template>
        <el-option v-for="o in semViewOptions" :key="String(o.no)" :label="o.label" :value="o.no" />
        </el-select>
      </div>
      <!-- 总览视图：全部学期时间线 -->
      <div class="sem-track" v-if="semView === 'all'">
        <div v-for="s in semesters" :key="s.semesterNo" class="sem-row"
          :class="{ done: s.done && !s.future, current: s.current, future: s.future }">
          <div class="sem-head">
            <span class="sem-badge">{{ s.label }}</span>
            <span class="sem-year">{{ s.schoolYear }}</span>
            <span v-if="s.current" class="sem-cur">{{ t('当前学期') }}</span>
            <span v-else-if="s.future" class="sem-future">{{ t('未开始') }}</span>
            <span class="sem-stat">
              {{ t('{a}/{b} 门通过 · {c}/{d} 学分', { a: s.passed, b: s.total, c: s.earned, d: s.credits }) }}
            </span>
            <span class="sem-state" :class="s.future ? 'future' : (s.done ? 'ok' : 'gap')">
              {{ s.future ? t('未开始') : (s.done ? t('全部通过') : t('差 {n} 门', { n: s.total - s.passed })) }}
            </span>
          </div>
          <div class="sem-chips">
            <span v-for="c in s.courses" :key="c.name" class="cm-chip"
              :class="{ retaking: c.retaking, ok: c.passed, fail: c.failed, pending: c.pendingScore && !s.future }">
              {{ c.name }}<em v-if="c.score != null" class="cm-score">{{ c.score }}分</em><i v-if="c.retaking" class="cm-flag">{{ t('重修中') }}</i><i v-else-if="c.pendingScore && !s.future" class="cm-flag pending-flag">{{ t('待出成绩') }}</i>
            </span>
          </div>
        </div>
      </div>
    <!-- 单学期视图：该学期学分进度环 + 课程明细（嵌在学期 section 内，随 Tab 显隐） -->
    <div class="sem-focus" v-if="semView !== 'all' && focusSem">
      <div class="semf-ring-box">
        <svg viewBox="0 0 140 140" role="img" :aria-label="t('学期学分进度环')">
          <circle cx="70" cy="70" r="58" class="semf-track" />
          <circle cx="70" cy="70" r="58" class="semf-fill"
            :class="{ ok: focusSem.done }"
            :stroke-dasharray="`${semfPct / 100 * SEMF_C} ${SEMF_C}`" />
        </svg>
        <div class="semf-center">
          <b>{{ semfPct }}<small>%</small></b>
          <span>{{ t('已获 {a}/{b} 学分', { a: focusSem.earned, b: focusSem.credits }) }}</span>
        </div>
      </div>
      <div class="semf-body">
        <div class="semf-head">
          <span class="sem-badge">{{ focusSem.label }}</span>
          <span class="sem-year">{{ focusSem.schoolYear }}</span>
          <span v-if="focusSem.current" class="sem-cur">{{ t('当前学期') }}</span>
          <span v-else-if="focusSem.future" class="sem-future">{{ t('未开始') }}</span>
        </div>
        <div class="semf-stats">
          <span>{{ t('{a}/{b} 门通过', { a: focusSem.passed, b: focusSem.total }) }}</span>
          <span class="dot">·</span>
          <span :class="focusSem.done ? 'ok' : 'gap'">{{ focusSem.future ? t('未开始') : (focusSem.done ? t('全部通过') : t('差 {n} 门', { n: focusSem.total - focusSem.passed })) }}</span>
        </div>
        <div class="semf-courses">
          <span v-for="c in focusSem.courses" :key="c.name" class="cm-chip"
            :class="{ retaking: c.retaking, ok: c.passed, fail: c.failed, pending: c.pendingScore && !focusSem.future }">
            {{ c.name }}<em v-if="c.score != null" class="cm-score">{{ c.score }}分</em><i v-if="c.retaking" class="cm-flag">{{ t('重修中') }}</i><i v-else-if="c.pendingScore && !focusSem.future" class="cm-flag pending-flag">{{ t('待出成绩') }}</i>
          </span>
          <span v-if="!focusSem.courses.length" class="semf-nocourse">{{ t('本学期无必修课（毕设/实习环节）') }}</span>
        </div>
      </div>
    </div>

    </div>

    <!-- ===== 推荐可修课程 ===== -->
    <div class="section" v-if="summary?.recommend?.length" v-show="gradTab === 'overview'">
      <div class="section-header">
        <div class="section-title" v-html="svgIcon('sparkles') + ' ' + t('推荐可修 · 定向补齐差额')" />
        <span class="rec-note">{{ t('按缺口从大到小排序') }}</span>
      </div>
      <div class="course-grid">
        <div v-for="c in summary.recommend" :key="c.courseId" class="course-card" :style="{ '--cc': CAT_COLORS[groupOf(c.category)] || '#2bb187' }">
          <div class="rc-top">
            <span class="rc-cat">{{ c.category }}</span>
            <span class="rc-credit">+{{ c.credit }} {{ t('学分') }}</span>
          </div>
          <div class="course-name">{{ c.name }}</div>
          <div class="course-teacher">
            <span v-html="svgIcon('user')" /> {{ c.teacherName || c.teacher || '—' }}
          </div>
          <div class="course-foot">
            <span class="rc-group">{{ t('补「{n}」缺口', { n: groupOf(c.category) }) }}</span>
            <button class="rc-btn" :disabled="enrollingId === c.courseId" @click="openPoints(c)">
              {{ enrollingId === c.courseId ? t('加入中…') : t('去修读') }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <IntentPointsDialog
      v-model="pointsVisible"
      :course="pendingCourse"
      :current-points="null"
      :remaining="remaining"
      :submitting="!!enrollingId"
      :round="currentRound"
      :max-points="pointPool"
      @submit="submitIntent"
    />

    <div class="empty-state" v-if="!summary && !loading">
      <div class="empty-icon" v-html="svgIcon('target')" />
      <div class="empty-title">{{ t('暂无毕业进度数据') }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api'
import IntentPointsDialog from '@/components/IntentPointsDialog.vue'
import { t } from '@/i18n'
const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`

/* ===== 视图 Tab：毕业总览 / 按学期进度 ===== */
const gradTab = ref<'overview' | 'semesters'>('overview')
const icons: Record<string, string> = { '必修': 'book', '选修': 'book-open', '通识': 'layers', '实践': 'flask' }
const CAT_COLORS: Record<string, string> = { '必修': '#0f6f52', '选修': '#0284c7', '通识': '#0369a1', '实践': '#b45309' }
const CAT_BG: Record<string, string> = {
  '必修': 'rgba(43,177,135,.14)', '选修': 'rgba(14,165,233,.14)',
  '通识': 'rgba(13,148,136,.14)', '实践': 'rgba(245,158,11,.14)',
}
const softBg = (k: string) => CAT_BG[k] || 'rgba(43,177,135,.14)'
const barGrad = (c: any) => c.done
  ? 'linear-gradient(90deg, #0f6f52, #14b8a6)'
  : `linear-gradient(90deg, ${CAT_COLORS[c.key] || '#2bb187'}, ${c.key === '实践' ? '#b45309' : c.key === '选修' ? '#0f766e' : '#0f766e'})`
const groupOf = (category: string) => {
  if (!category) return '选修'
  if (category.includes('必修')) return '必修'
  if (category.includes('通识')) return '通识'
  if (category.includes('实践')) return '实践'
  return '选修'
}

/* ---------- 环 ---------- */
const BIG_C = 2 * Math.PI * 88
const clampPct = computed(() => sumRequired.value ? Math.min(100, Math.round(sumEarned.value * 100 / sumRequired.value)) : 0)
const pctOf = (c: any) => (c?.required ? Math.min(100, Math.round(c.earned * 100 / c.required)) : 0)

const cats = computed<any[]>(() => summary.value?.categories || [])
const missingList = computed<string[]>(() => summary.value?.missingRequired || [])
/** 必修缺失明细（带重修中标志；后端未升级时退化为纯名单） */
const missingDetailList = computed<any[]>(() => summary.value?.missingDetails || [])
function missingRow(c: any): any[] {
  if (c.key !== '必修') return []
  if (missingDetailList.value.length || summary.value?.missingDetails) return missingDetailList.value
  return (c.missing || missingList.value || []).map((n: string) => ({ name: n, retaking: false }))
}
const doneCount = computed(() => cats.value.filter(c => c.done).length)

/** 必修课按培养方案学期分组进度（后端 GraduationService.summary().semesters） */
const semesters = computed<any[]>(() => summary.value?.semesters || [])

/* ===== 学期视图切换：全部（时间线）/ 单学期（学分进度环） ===== */
const semView = ref<number | 'all'>('all')
const semViewOptions = computed(() => [
  { no: 'all' as const, label: t('全部学期总览') },
  ...semesters.value.map(s => ({
    no: s.semesterNo as number,
    label: `${s.label} · ${s.schoolYear || ''}${s.current ? t('（进行中）') : ''}`,
  })),
])
const focusSem = computed(() => semesters.value.find(s => s.semesterNo === semView.value) || null)
const SEMF_C = 2 * Math.PI * 58
const semfPct = computed(() => {
  const f = focusSem.value
  if (!f || !f.credits) return 0
  return Math.min(100, Math.round((f.earned / f.credits) * 100))
})

/* ===== 毕业申请（申请-审核工作流） ===== */
const applicationStatus = ref('NONE')
const appPending = computed(() => applicationStatus.value === 'PENDING')
const appApproved = computed(() => applicationStatus.value === 'APPROVED')
const appRemark = computed(() => summary.value?.applicationRemark || '')
const thesisScore = ref<number | null>(null)
const applying = ref(false)

async function applyGraduation() {
  if (applying.value) return
  applying.value = true
  try {
    const r: any = await http.post('/graduation/apply')
    ElMessage.success(r?.message || t('毕业申请已提交'))
    applicationStatus.value = 'PENDING'
  } catch (e: any) {
    ElMessage.error(e?.message || t('提交失败'))
  } finally { applying.value = false }
}

/* 学分统计口径（还原原版判定）：每个类别最多按「要求学分」计入总进度，
   一类修满不能抵扣其他类别的缺口；总达标需四类全部达标（含必修逐门通过）。 */
const sumEarned = computed(() => cats.value.reduce((s, c) => s + Math.min(Number(c.earned) || 0, Number(c.required) || 0), 0))
const sumRequired = computed(() => cats.value.reduce((s, c) => s + (Number(c.required) || 0), 0))
const sumGap = computed(() => Math.max(0, sumRequired.value - sumEarned.value))
const coursesProgress = computed<any>(() => summary.value?.courses || null)
const coursesGap = computed(() => coursesProgress.value ? Math.max(0, (Number(coursesProgress.value.required) || 0) - (Number(coursesProgress.value.earned) || 0)) : 0)
const allDone = computed(() =>
  cats.value.length > 0 && cats.value.every(c => c.done)
  && (!coursesProgress.value || Number(coursesProgress.value.required) <= 0 || coursesProgress.value.done))

/* 环心数字随环生长滚动 */
const dispPct = ref(0)
const dispEarned = ref(0)
const loading = ref(false)
const summary = ref<any>(null)
const enrollingId = ref<number | null>(null)
const pointsVisible = ref(false)
const pendingCourse = ref<any>(null)
const remaining = ref(0)
const currentRound = ref(1)
const pointPool = ref(50)

watch([sumEarned, sumRequired], () => {
  if (!cats.value.length) return
  const targets = { pct: clampPct.value, earned: sumEarned.value }
  const from = { pct: dispPct.value, earned: dispEarned.value }
  const start = performance.now(), dur = 750
  const step = (now: number) => {
    const p = Math.min(1, (now - start) / dur)
    const e = 1 - Math.pow(1 - p, 3)
    dispPct.value = Math.round(from.pct + (targets.pct - from.pct) * e)
    dispEarned.value = Math.round(from.earned + (targets.earned - from.earned) * e)
    if (p < 1) requestAnimationFrame(step)
  }
  requestAnimationFrame(step)
})
/* 及格线：总评 ≥ 60 才算「获得」该课学分。
   后端若按「已录取课程」累计（含未出成绩/不及格），这里按成绩二次校正各类别已获学分。 */
const PASS_LINE = 60
function applyPassedCredits(rows: any) {
  if (!Array.isArray(rows) || !summary.value) return
  const seen = new Set<number>()
  const earned: Record<string, number> = { '必修': 0, '选修': 0, '通识': 0, '实践': 0 }
  for (const row of rows) {
    const id = Number(row.courseId)
    if (seen.has(id)) continue
    seen.add(id)
    if (row.totalScore == null || Number(row.totalScore) < PASS_LINE) continue
    const g = groupOf(String(row.category || ''))
    earned[g] += Number(row.credit) || 0
  }
  summary.value.categories = summary.value.categories.map((c: any) => {
    const got = earned[c.key] ?? 0
    const req = Number(c.required) || 0
    return { ...c, earned: got, gap: Math.max(0, req - got), done: got >= req }
  })
}
async function load() {
  loading.value = true
  try {
    const [r, p, sc, ma]: any[] = await Promise.all([
      http.get('/graduation/summary'),
      http.get('/enrollments/points').catch(() => null),
      http.get('/enrollments/scores').catch(() => null),
      http.get('/graduation/my-application').catch(() => null)
    ])
    // 兼容两种返回形态：{success,data} 或直接对象
    const body = r && r.data !== undefined ? r.data : r
    summary.value = body && body.categories ? body : null
    if (body?.applicationStatus) applicationStatus.value = body.applicationStatus
    // 毕设总评快照（申请记录里带出）
    const myApp = ma && ma.data !== undefined ? ma.data : ma
    thesisScore.value = myApp && myApp.thesisScore != null ? Number(myApp.thesisScore) : null
    applyPassedCredits(sc?.data)
    remaining.value = p?.data?.remaining ?? 0
    currentRound.value = p?.data?.round ?? 1
    pointPool.value = p?.data?.total ?? 50
    if (!summary.value) {
      // 后端刚启动时偶发空响应，延迟重试一次
      setTimeout(async () => {
        try {
          const r2: any = await http.get('/graduation/summary')
          const b2 = r2 && r2.data !== undefined ? r2.data : r2
          if (b2 && b2.categories) { summary.value = b2; applyPassedCredits(sc?.data) }
        } catch {}
      }, 600)
    }
  } catch {
    ElMessage.error(t('毕业进度加载失败（服务可能刚重启），请刷新页面重试'))
  }
  loading.value = false
}
function openPoints(c: any) {
  pendingCourse.value = c
  pointsVisible.value = true
}
async function submitIntent(points: number) {
  const c = pendingCourse.value
  if (!c || enrollingId.value) return
  enrollingId.value = c.courseId
  try {
    await http.post('/intents/add', { courseId: c.courseId, points })
    ElMessage.success(t('已加入意向：{name}（投入 {n} 分）', { name: c.name, n: points }))
    pointsVisible.value = false
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || t('操作失败'))
  } finally {
    enrollingId.value = null
  }
}
onMounted(load)
</script>

<style scoped>
.page-wrap { padding: 4px 0; display: flex; flex-direction: column; gap: 16px; }

/* ===== 主视觉卡 ===== */
.grad-hero {
  display: flex; gap: 28px; align-items: stretch; flex-wrap: wrap;
  padding: 26px 28px; border-radius: 18px;
  border: 1px solid var(--border); background: var(--card-bg);
  position: relative; overflow: hidden;
}
.grad-hero::before {
  content: ''; position: absolute; inset: 0; pointer-events: none;
  background:
    radial-gradient(420px 220px at 8% 0%, rgba(43,177,135,.08), transparent 60%),
    radial-gradient(380px 240px at 100% 100%, rgba(20,184,166,.07), transparent 60%);
}
.grad-hero-left { display: flex; align-items: center; flex-shrink: 0; }
.grad-ring-box { position: relative; width: 216px; height: 216px; }
.grad-ring-box svg { width: 100%; height: 100%; overflow: visible; }
.ring-halo { fill: none; stroke: rgba(43,177,135,.10); stroke-width: 26; filter: blur(6px); }
.ring-track { fill: none; stroke: rgba(148,163,184,.16); stroke-width: 13; }
.ring-fill {
  fill: none; stroke-width: 13; stroke-linecap: round;
  transition: stroke-dasharray .9s cubic-bezier(.22,.9,.36,1);
  filter: drop-shadow(0 0 10px rgba(43,177,135,.45));
}
.ring-ticks line { stroke: rgba(148,163,184,.35); stroke-width: 1.2; }
.ring-center {
  position: absolute; inset: 0; display: flex; flex-direction: column;
  align-items: center; justify-content: center; gap: 4px; text-align: center;
}
.ring-badge {
  font-size: 12px; font-weight: 700; padding: 2px 10px; border-radius: 999px; letter-spacing: .5px;
}
.ring-badge.ok { color: #059669; background: rgba(16,185,129,.13); border: 1px solid rgba(16,185,129,.3); }
.ring-badge.going { color: #2bb187; background: rgba(43,177,135,.12); border: 1px solid rgba(43,177,135,.3); }
.ring-pct { font-size: 42px; font-weight: 800; line-height: 1; color: var(--text); font-variant-numeric: tabular-nums; }
.ring-pct small { font-size: 20px; color: var(--text-light); margin-left: 2px; }
.ring-sub { font-size: 12px; color: var(--text-light); }
.ring-sub b { color: var(--text); font-size: 13.5px; }
.ring-gap { font-size: 12px; font-weight: 700; color: #d97706; }
.ring-gap.done { color: #059669; }

/* 右侧类别构成 */
.grad-hero-right { flex: 1; min-width: 320px; display: flex; flex-direction: column; justify-content: center; gap: 6px; }
.ghr-head { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 10px; }
/* 毕业要求来源（按年级 / 全校默认） */
.ghr-req { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.ghr-req-tag { font-size: 12.5px; font-weight: 700; padding: 4px 12px; border-radius: 999px; }
.ghr-req-tag.default { color: #475569; background: rgba(100, 116, 139, .12); border: 1px solid rgba(100, 116, 139, .3); }
.ghr-req-tag.grade { color: #0d9488; background: rgba(13, 148, 136, .12); border: 1px solid rgba(13, 148, 136, .35); }
html.dark .ghr-req-tag.default { color: #a8b3c2; }
html.dark .ghr-req-tag.grade { color: #2dd4bf; }
.ghr-req-note { font-size: 12px; color: var(--text-light); }
.ghr-head b { font-size: 15px; color: var(--text); }
.ghr-tip { font-size: 12px; color: var(--text-light); }
.cat-row { padding: 9px 12px; border-radius: 12px; border: 1px solid transparent; transition: all .2s; }
.cat-row:hover { background: rgba(148,163,184,.06); border-color: var(--border); }
.cat-row.done { background: rgba(16,185,129,.045); }
.cat-line { display: flex; align-items: center; gap: 10px; }
.cat-ico {
  width: 30px; height: 30px; border-radius: 9px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
}
.cat-ico .icon { width: 15px; height: 15px; }
.cat-name { font-size: 13px; font-weight: 700; color: var(--text); width: 34px; flex-shrink: 0; }
.cat-bar {
  flex: 1; position: relative; height: 10px; border-radius: 99px;
  background: rgba(148,163,184,.13); overflow: visible;
}
.cat-bar i {
  position: absolute; left: 0; top: 0; height: 100%; border-radius: 99px;
  transition: width .8s cubic-bezier(.22,.9,.36,1);
  box-shadow: 0 1px 6px rgba(0,0,0,.12);
}
.cat-bar em {
  position: absolute; top: -2.5px; bottom: -2.5px; width: 3px; border-radius: 2px;
  background: rgba(244,63,94,.55);
  animation: cmGapPulse 2.2s ease-in-out infinite;
}
@keyframes cmGapPulse { 0%,100% { opacity: .5 } 50% { opacity: 1 } }
.cat-nums { font-size: 12px; color: var(--text-light); flex-shrink: 0; font-variant-numeric: tabular-nums; }
.cat-nums b { font-size: 14px; }
.cat-state { font-size: 12px; font-weight: 700; width: 42px; text-align: right; flex-shrink: 0; }
.cat-state.ok { color: #059669; }
.cat-state.gap { color: #d97706; }

/* 必修未通过清单 */
.cat-missing { display: flex; flex-wrap: wrap; gap: 5px; margin: 8px 0 0 40px; align-items: center; }
.cm-label { font-size: 12px; color: var(--text-light); }
.cm-chip {
  font-size: 12px; font-weight: 600; color: #e11d48;
  background: rgba(244,63,94,.1); border: 1px dashed rgba(244,63,94,.35);
  padding: 1px 9px; border-radius: 999px; display: inline-flex; align-items: center; gap: 5px;
}
/* 重修中：挂科但本轮已在修（必修自动重排 / 重修班），药丸转琥珀色提示补救进行中 */
.cm-chip.retaking { color: #b45309; background: rgba(217,119,6,.09); border-color: rgba(217,119,6,.45); }
.cm-flag {
  font-style: normal; font-size: 10.5px; font-weight: 700; color: #b45309;
  background: rgba(217,119,6,.14); border-radius: 999px; padding: 0 6px; line-height: 16px;
}
html.dark .cm-chip { color: #fb7185; }
html.dark .cm-chip.retaking, html.dark .cm-flag { color: #fbbf24; }
.cm-more { font-size: 12px; color: var(--text-light); font-weight: 600; }

/* ===== 必修 · 按学期进度（培养方案时间线） ===== */
.sem-track { display: flex; flex-direction: column; gap: 10px; }
.sem-row {
  padding: 12px 14px; border-radius: 12px; border: 1px solid var(--border);
  background: color-mix(in srgb, var(--card-bg) 55%, transparent);
  border-left: 3px solid rgba(148,163,184,.4);
  transition: all .25s;
}
.sem-row.done { border-left-color: rgba(16,185,129,.6); background: rgba(16,185,129,.04); }
.sem-row.current { border-left-color: var(--primary); box-shadow: 0 0 0 3px rgba(43,177,135,.1); }
.sem-row.future { opacity: .62; }
.sem-head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-bottom: 8px; }
.sem-badge { font-size: 12.5px; font-weight: 800; color: var(--text); }
.sem-row.current .sem-badge { color: var(--primary); }
.sem-year { font-size: 12px; color: var(--text-light); }
.sem-cur {
  font-size: 11px; font-weight: 700; color: #fff; padding: 2px 9px; border-radius: 999px;
  background: linear-gradient(135deg, #2bb187, #38bdf8); box-shadow: 0 2px 8px rgba(43,177,135,.3);
}
.sem-future { font-size: 11px; font-weight: 700; color: #64748b; background: rgba(100,116,139,.12); padding: 2px 9px; border-radius: 999px; }
.sem-stat { margin-left: auto; font-size: 12px; color: var(--text-light); font-variant-numeric: tabular-nums; }
.sem-state { font-size: 12px; font-weight: 700; flex-shrink: 0; }
.sem-state.ok { color: #059669; }
.sem-state.gap { color: #d97706; }
.sem-state.future { color: #64748b; }
html.dark .sem-state.ok { color: #34d399; }
html.dark .sem-state.gap { color: #fbbf24; }
.sem-chips { display: flex; flex-wrap: wrap; gap: 6px; }
.cm-chip.ok { color: #059669; background: rgba(16,185,129,.08); border: 1px solid rgba(16,185,129,.3); }
.cm-chip.fail { color: #e11d48; background: rgba(244,63,94,.1); border: 1px dashed rgba(244,63,94,.35); }
html.dark .cm-chip.ok { color: #34d399; }
.cm-score { font-style: normal; font-size: 10.5px; opacity: .8; margin-left: 4px; font-variant-numeric: tabular-nums; }
.cm-chip.pending { color: #475569; background: rgba(100,116,139,.08); border: 1px solid rgba(100,116,139,.3); }
.pending-flag { font-style: normal; font-size: 10.5px; font-weight: 700; color: #475569; background: rgba(100,116,139,.12); border-radius: 999px; padding: 0 6px; line-height: 16px; }
html.dark .cm-chip.pending, html.dark .pending-flag { color: #94a3b8; }

/* ===== 推荐课程 ===== */
.rec-note { font-size: 12px; color: var(--text-light); }
.course-grid { display: grid; gap: 14px; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); }
.course-card {
  --cc: #2bb187;
  position: relative; overflow: hidden;
  background: var(--card-bg); border: 1px solid var(--border); border-radius: 14px;
  padding: 16px 18px; display: flex; flex-direction: column; gap: 9px;
  transition: transform .25s cubic-bezier(.2,.7,.3,1), border-color .25s, box-shadow .25s;
}
.course-card:hover {
  transform: translateY(-4px);
  border-color: color-mix(in srgb, var(--cc) 45%, var(--border));
  box-shadow: 0 12px 28px -10px color-mix(in srgb, var(--cc) 35%, transparent);
}
.rc-top { display: flex; justify-content: space-between; align-items: center; }
.rc-cat {
  font-size: 12px; font-weight: 700; padding: 2px 10px; border-radius: 999px;
  color: var(--cc); background: color-mix(in srgb, var(--cc) 12%, transparent);
}
.rc-credit {
  font-size: 12px; font-weight: 800; color: #059669;
  background: rgba(16,185,129,.12); padding: 2px 10px; border-radius: 999px;
}
.course-name { font-size: 15px; font-weight: 700; color: var(--text); line-height: 1.4; }
.course-teacher { font-size: 12px; color: var(--text-light); display: flex; align-items: center; gap: 5px; }
.course-teacher .icon { width: 13px; height: 13px; }
.course-foot { display: flex; justify-content: space-between; align-items: center; margin-top: 2px; }
.rc-group { font-size: 12px; color: var(--text-light); }
.rc-btn {
  border: none; cursor: pointer; font-size: 12.5px; font-weight: 700; color: #fff;
  background: linear-gradient(135deg, color-mix(in srgb, var(--cc) 60%, #0d3a2c), color-mix(in srgb, var(--cc) 42%, #0d3a2c));
  padding: 7px 18px; border-radius: 999px;
  transition: transform .15s, box-shadow .2s, opacity .2s;
  box-shadow: 0 3px 10px rgba(0,0,0,.25);
}
.rc-btn:hover:not(:disabled) { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0,0,0,.3); }
.rc-btn:disabled { opacity: .55; cursor: not-allowed; }

.empty-state { text-align: center; padding: 60px 20px; color: var(--text-light); }
.empty-icon { font-size: 48px; opacity: 0.3; margin-bottom: 10px; }

/* ===== 毕业申请条 ===== */
.grad-apply-section { border-color: rgba(43,177,135,.35); }
.ga-apply { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
.ga-apply-main { flex: 1; min-width: 240px; font-size: 13.5px; font-weight: 600; color: var(--text); display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.ga-thesis-score {
  font-size: 12px; font-weight: 600; color: var(--text-light);
  padding: 4px 12px; border-radius: 999px;
  border: 1px dashed color-mix(in srgb, var(--primary) 45%, transparent);
  background: color-mix(in srgb, var(--primary) 6%, transparent);
}
.ga-thesis-score b { font-size: 15px; color: var(--primary); margin: 0 2px; }
.ga-thesis-score small { color: var(--text-lighter); font-weight: 500; }
.ga-pending-pill, .ga-ok-pill { font-size: 12.5px; font-weight: 700; padding: 6px 16px; border-radius: 999px; }
.ga-pending-pill { color: #d97706; background: rgba(245,158,11,.12); border: 1px solid rgba(245,158,11,.35); }
.ga-ok-pill { color: #fff; background: linear-gradient(135deg, #2bb187, #0d9488); box-shadow: 0 3px 10px rgba(43,177,135,.35); }
html.dark .ga-pending-pill { color: #fbbf24; }

@media (max-width: 860px) {
  .grad-hero { flex-direction: column; align-items: center; padding: 20px; }
  .grad-hero-right { width: 100%; }
}
/* ===== 学期筛选 + 单学期聚焦视图 ===== */
.sem-filter { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.sem-filter-label { font-size: 12.5px; color: var(--text-light); }
.sem-focus { display: flex; align-items: center; gap: 28px; flex-wrap: wrap;
  padding: 20px 24px; border-radius: 14px; border: 1px solid var(--border);
  background: color-mix(in srgb, var(--card-bg) 55%, transparent); }
.sem-focus:has(.sem-cur) { border-color: rgba(43,177,135,.35); }
.semf-ring-box { position: relative; width: 150px; height: 150px; flex-shrink: 0; }
.semf-ring-box svg { width: 100%; height: 100%; transform: rotate(-90deg); }
.semf-track { fill: none; stroke: rgba(148,163,184,.16); stroke-width: 11; }
.semf-fill { fill: none; stroke: var(--primary); stroke-width: 11; stroke-linecap: round; transition: stroke-dasharray .9s cubic-bezier(.22,.9,.36,1); }
.semf-fill.ok { stroke: #34d399; }
html.dark .semf-fill.ok { stroke: #2dd4bf; }
.semf-center { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 3px; }
.semf-center b { font-size: 30px; font-weight: 800; color: var(--text); line-height: 1; font-variant-numeric: tabular-nums; }
.semf-center b small { font-size: 14px; color: var(--text-light); }
.semf-center span { font-size: 11.5px; color: var(--text-light); }
.semf-body { flex: 1; min-width: 300px; display: flex; flex-direction: column; gap: 10px; }
.semf-head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.semf-stats { display: flex; align-items: center; gap: 8px; font-size: 13px; color: var(--text); font-weight: 600; }
.semf-stats .dot { color: var(--text-lighter); }
.semf-stats .ok { color: #059669; }
.semf-stats .gap { color: #d97706; }
html.dark .semf-stats .ok { color: #34d399; }
html.dark .semf-stats .gap { color: #fbbf24; }
.semf-courses { display: flex; flex-wrap: wrap; gap: 6px; }
.semf-nocourse { font-size: 12.5px; color: var(--text-light); padding: 6px 0; }

/* ===== 视图 Tab ===== */
.grad-tabs { display: flex; gap: 4px; margin-bottom: 2px; border-bottom: 1px solid var(--border); }
.grad-tab {
  appearance: none; border: none; background: transparent; cursor: pointer;
  font-size: 14px; font-weight: 600; color: var(--text-light);
  padding: 10px 18px 12px; position: relative; transition: color .2s, background .2s;
  border-radius: 10px 10px 0 0; font-family: inherit;
}
.grad-tab:hover { color: var(--text); background: color-mix(in srgb, var(--card-bg) 55%, transparent); }
.grad-tab.on { color: var(--primary); }
.grad-tab.on::after {
  content: ''; position: absolute; left: 14px; right: 14px; bottom: -1px; height: 2.5px;
  border-radius: 3px; background: var(--primary);
}

@media (prefers-reduced-motion: reduce) {
  .cat-bar i, .cat-bar em, .ring-fill { animation: none !important; transition: none !important; }
}

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
