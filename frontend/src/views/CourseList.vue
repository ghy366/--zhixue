<template>
  <div class="zx-page stats-page liquid">
    <div class="page-head">
      <h2>📚 课程浏览</h2>
      <el-input v-model="keyword" placeholder="搜索课程名 / 教师" clearable style="width:280px" />
    </div>

    <el-alert
      v-if="myWindow && myWindow.staggered && !myWindow.myOpen && currentRound < 3"
      type="warning"
      :closable="false"
      show-icon
      class="stagger-banner"
      :title="`错峰开放：你所在的 ${myWindow.grade} 级将于 ${fmtAt(myWindow.myOpenAt)} 开放本轮选课，到点前可先浏览课程、加入意向`"
    />

    <div class="filter-bar">
      <el-select v-model="selectedCategory" placeholder="全部类别" clearable style="width:160px" @change="load">
        <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
      </el-select>
      <el-tag type="info" effect="plain">共 {{ filteredCourses.length }} 门</el-tag>
    </div>

    <div v-loading="loading" class="course-grid">
      <div
        v-for="(c, i) in filteredCourses"
        :key="c.id"
        class="course-card"
        :style="{ '--i': i }"
        :class="{ 'cc-selected': c.myIntent, 'cc-approved': c.myStatus === 'approved' }"
      >
        <!-- 右上角收藏式意向按钮：仅在「选课未开放」时用于收藏意向 -->
        <button
          v-if="!canSelect && c.myStatus !== 'approved' && c.myStatus !== 'pending'"
          class="intent-btn"
          :class="{ active: c.myIntent }"
          :title="c.myIntent ? '移出意向' : '加入意向（选课未开始，可先收藏）'"
          @click="toggleIntent(c)"
        >
          <span class="icon" v-html="svgIcon('cart')" />
        </button>
        <div class="cc-top">
          <div class="cc-name">{{ c.name }}</div>
          <span class="course-cat" :class="'cat-' + c.category">{{ c.category }}</span>
        </div>
        <div class="cc-meta">
          <span>👨‍🏫 {{ c.teacherName }}</span>
          <span>🎓 {{ c.credit }} 学分</span>
          <span>🏛️ {{ c.location }}</span>
        </div>
        <div class="cc-meta">
          <span>📅 {{ c.time }}</span>
          <span>🎯 {{ c.stat?.total || 0 }}/{{ c.capacity }}</span>
        </div>
        <div class="cc-progress">
          <div class="cc-progress-fill" :class="{ 'is-full': c.full }" :style="{ width: capacityPercent(c) + '%' }" />
        </div>
        <div class="cc-foot">
          <!-- 已录取 -->
          <template v-if="c.myStatus === 'approved'">
            <el-tag type="success" effect="plain" size="small">已选</el-tag>
            <el-button size="small" text @click="enroll(c, true)">退课</el-button>
          </template>
          <!-- 已报名待录取 -->
          <template v-else-if="c.myStatus === 'pending'">
            <el-tag type="warning" effect="plain" size="small">待录取 {{ c.myPoints || 0 }}分</el-tag>
            <el-button size="small" text @click="cancelEnroll(c)">取消报名</el-button>
          </template>
          <!-- 选课开放中：直接报名 -->
          <template v-else-if="canSelect">
            <el-button v-if="!c.full" size="small" type="success" @click="openPoints(c)">选课</el-button>
            <el-button v-else size="small" type="primary" plain @click="enroll(c, false)">候补</el-button>
          </template>
          <!-- 选课未开放：只能先收藏意向 -->
          <el-button v-else-if="c.myIntent" size="small" type="warning" plain @click="openPoints(c)">
            意向中 {{ c.myIntentPoints }}分
          </el-button>
          <el-button v-else size="small" type="success" plain @click="openPoints(c)">加入意向</el-button>
          <el-button size="small" text @click="viewDetail(c)">详情</el-button>
        </div>
      </div>
      <el-empty v-if="!filteredCourses.length" description="暂无课程" />
    </div>

    <el-dialog v-model="detailVisible" :title="detailCourse?.name" width="520px">
      <div v-if="detailCourse" class="detail-body">
        <div class="dd-row"><b>课程编号</b><span>{{ detailCourse.courseCode }}</span></div>
        <div class="dd-row"><b>教师</b><span>{{ detailCourse.teacherName }}</span></div>
        <div class="dd-row"><b>学分</b><span>{{ detailCourse.credit }}</span></div>
        <div class="dd-row"><b>类别</b><span>{{ detailCourse.category }}</span></div>
        <div class="dd-row"><b>上课时间</b><span>{{ detailCourse.time }}</span></div>
        <div class="dd-row"><b>教室</b><span>{{ detailCourse.location }}</span></div>
        <div class="dd-row"><b>校区</b><span>{{ detailCourse.campus }}</span></div>
        <div class="dd-row"><b>容量 / 已选</b><span>{{ detailCourse.capacity }} / {{ detailCourse.stat?.total || 0 }}</span></div>
        <div class="dd-row dd-row-col"><b>课程简介</b><span>{{ detailCourse.intro || '—' }}</span></div>
      </div>
    </el-dialog>

    <IntentPointsDialog
      v-model="pointsVisible"
      :course="pendingCourse"
      :current-points="pendingCourse && pendingCourse.myIntent ? (pendingCourse.myIntentPoints ?? null) : null"
      :remaining="remaining"
      :submitting="pointsSubmitting"
      :round="currentRound"
      :max-points="pointPool"
      :mode="dialogMode"
      @submit="submitPoints"
      @remove="removeIntent"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api'
import IntentPointsDialog from '@/components/IntentPointsDialog.vue'

// 必修课已由系统自动安排进课表，不再出现在选课列表
const EXCLUDED_CATEGORIES = ['公共必修', '专业必修']

const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`

const loading = ref(false)
const courses = ref<any[]>([])
const keyword = ref('')
const selectedCategory = ref<string | null>(null)
const detailVisible = ref(false)
const detailCourse = ref<any>(null)

// 投入积分弹窗
const pointsVisible = ref(false)
const pendingCourse = ref<any>(null)
const pointsSubmitting = ref(false)
const remaining = ref(0)
// 当前轮次与本轮积分池（来自 /enrollments/points）
const currentRound = ref(1)
const pointPool = ref(50)
// 我的错峰开放窗口（来自 /system/my-window）
const myWindow = ref<any>(null)
// 系统当前轮次状态（来自 /system/config）
const systemConfig = ref<any>({})
function fmtAt(s: string) { return s ? String(s).replace('T', ' ').substring(5, 16) : '' }

/**
 * 本轮选课是否已经真正开放给本人：
 * 轮次开放 + （若配置了错峰）本人窗口已到点。
 * 开放 → 点「选课」直接报名；未开放 → 只能先加入意向。
 */
const canSelect = computed(() => {
  if (!systemConfig.value.roundOpen) return false
  const w = myWindow.value
  if (w && w.staggered && !w.myOpen) return false
  return true
})

/** 弹窗语义：enroll=正式报名（扣积分、进待录取），intent=选课前的收藏意向 */
const dialogMode = ref<'enroll' | 'intent'>('intent')

/** 可参与选课的课程（剔除公共必修 / 专业必修） */
const selectableCourses = computed(() =>
  courses.value.filter(c => !EXCLUDED_CATEGORIES.includes(c.category))
)

const categories = computed(() => {
  const set = new Set<string>()
  selectableCourses.value.forEach(c => c.category && set.add(c.category))
  return Array.from(set)
})

const filteredCourses = computed(() => {
  let list = selectableCourses.value
  if (keyword.value) {
    const k = keyword.value.toLowerCase()
    list = list.filter(c =>
      c.name.toLowerCase().includes(k) ||
      (c.teacherName || '').toLowerCase().includes(k)
    )
  }
  if (selectedCategory.value) list = list.filter(c => c.category === selectedCategory.value)
  return list
})

function capacityPercent(c: any) {
  if (!c.capacity) return 0
  return Math.min(100, (((c.stat?.approved || 0)) / c.capacity) * 100)
}

function viewDetail(c: any) { detailCourse.value = c; detailVisible.value = true }

async function enroll(c: any, isDrop: boolean) {
  try {
    if (isDrop) {
      await ElMessageBox.confirm(`确定退选「${c.name}」？`, '提示', { type: 'warning' })
      await http.post('/enrollments/drop', { courseId: c.id })
      ElMessage.success('已退选')
    } else {
      // 候补：后端是 POST /waitlist/join {courseId}
      await http.post('/waitlist/join', { courseId: c.id })
      ElMessage.success('已加入候补队列')
    }
    load()
  } catch (e: any) { ElMessage.error(e?.message || '操作失败') }
}

/** 打开投入积分弹窗：选课开放 → 正式报名；未开放 → 收藏意向 */
function openPoints(c: any) {
  pendingCourse.value = c
  dialogMode.value = canSelect.value ? 'enroll' : 'intent'
  pointsVisible.value = true
}

/** 取消已提交的报名（pending）：撤销报名并退回积分 */
async function cancelEnroll(c: any) {
  try {
    await ElMessageBox.confirm(
      `确定取消《${c.name}》的报名？已投入的 ${c.myPoints ?? 0} 积分将退回。`,
      '取消报名', { type: 'warning', confirmButtonText: '确定取消', cancelButtonText: '再想想' }
    )
  } catch { return }
  try {
    const r: any = await http.post('/enrollments/drop', { courseId: c.id })
    ElMessage.success(r?.message || '已撤销报名')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '取消失败')
  }
}

/** 收藏式意向按钮：一键加入（默认 10 分）/ 一键移出 */
async function toggleIntent(c: any) {
  try {
    if (c.myIntent) {
      await http.post('/intents/remove', { courseId: c.id })
      ElMessage.success({ message: `已移出意向：${c.name}`, duration: 1500 })
    } else {
      await http.post('/intents/add', { courseId: c.id, points: 10 })
      ElMessage.success({ message: `已加入意向：${c.name}（默认 10 分，点「意向中」可调整）`, duration: 2500 })
    }
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

/**
 * 确认提交：
 * - 选课开放中（enroll）：直接调 /enrollments/enroll 正式报名，当场扣积分、进「待录取」；
 * - 选课未开放（intent）：只收藏到意向单，等开放后再转正式。
 */
async function submitPoints(points: number) {
  const c = pendingCourse.value
  if (!c || pointsSubmitting.value) return
  pointsSubmitting.value = true
  const enrollMode = dialogMode.value === 'enroll'
  try {
    if (enrollMode) {
      const r: any = await http.post('/enrollments/enroll', { courseId: c.id, points })
      // 报名成功后清掉同一门课的旧意向，避免意向单里残留「已报名」的课程
      if (c.myIntent) { try { await http.post('/intents/remove', { courseId: c.id }) } catch {} }
      ElMessage.success({ message: r?.message || `报名成功！已投入 ${points} 积分`, duration: 3200 })
    } else {
      // 后端 POST /intents/add 为 upsert，同一课程会覆盖积分
      await http.post('/intents/add', { courseId: c.id, points })
      const isEdit = !!c.myIntent
      ElMessage.success(isEdit
        ? `已把「${c.name}」调整为 ${points} 分`
        : `已加入意向：${c.name}（投入 ${points} 分，选课开放后可一键转正式选课）`)
    }
    pointsVisible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    pointsSubmitting.value = false
  }
}

/** 移出意向 */
async function removeIntent() {
  const c = pendingCourse.value
  if (!c) return
  try {
    await http.post('/intents/remove', { courseId: c.id })
    ElMessage.success('已移出意向')
    pointsVisible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '移除失败')
  }
}

async function load() {
  loading.value = true
  try {
    const [r, p, cfg]: any[] = await Promise.all([
      http.get('/courses'),
      http.get('/enrollments/points').catch(() => null),
      http.get('/system/config').catch(() => null)
    ])
    courses.value = r?.data || []
    remaining.value = p?.data?.remaining ?? 0
    currentRound.value = p?.data?.round ?? 1
    pointPool.value = p?.data?.total ?? 50
    systemConfig.value = cfg?.data || {}
  } catch {}
  try {
    const w: any = await http.get('/system/my-window')
    myWindow.value = w?.data || null
  } catch { myWindow.value = null }
  loading.value = false
}
onMounted(load)
</script>

<style scoped>
.zx-page { display: flex; flex-direction: column; gap: 16px; }
.page-head { display: flex; align-items: center; justify-content: space-between; }
.page-head h2 { margin: 0; color: var(--text, #f1f5f9); font-size: 20px; }

.filter-bar { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.stagger-banner { margin-bottom: 2px; }

.course-grid { display: grid; gap: 14px; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); }
.course-card {
  position: relative; overflow: hidden;
  background: var(--card-bg, #0f172a); border: 1px solid var(--border, var(--border));
  border-radius: 12px; padding: 16px 18px; display: flex; flex-direction: column; gap: 10px;
  transition: transform .28s cubic-bezier(.2,.7,.3,1), border-color .28s, box-shadow .28s, background .28s;
  will-change: transform;
}

/* 入场：依次淡入上浮（用 backwards，避免 fill-mode 卡住 hover 的 transform） */
.course-card { animation: ccIn .5s cubic-bezier(.2,.7,.3,1) backwards; animation-delay: calc(var(--i, 0) * 45ms); }
@keyframes ccIn {
  from { opacity: 0; transform: translateY(16px) scale(.985); }
  to   { opacity: 1; transform: none; }
}

/* 悬停光泽扫过 */
.course-card::before {
  content: ''; position: absolute; inset: 0; pointer-events: none;
  background: linear-gradient(115deg, transparent 32%, rgba(255,255,255,.22) 50%, transparent 68%);
  transform: translateX(-130%);
  transition: transform .75s cubic-bezier(.2,.7,.3,1);
}
.course-card:hover::before { transform: translateX(130%); }
/* 亮色主题下白色高光看不见，改用主色调 */
html:not([data-theme="dark"]) .course-card::before {
  background: linear-gradient(115deg, transparent 32%, rgba(79,70,229,.12) 50%, transparent 68%);
}

/* 顶部渐变高亮条：悬停从左侧展开 */
.course-card::after {
  content: ''; position: absolute; top: 0; left: 0; right: 0; height: 2px;
  background: linear-gradient(90deg, var(--primary), var(--accent));
  transform: scaleX(0); transform-origin: left;
  transition: transform .35s cubic-bezier(.2,.7,.3,1);
}
.course-card:hover::after { transform: scaleX(1); }

/* 悬停：上浮 + 描边发光 */
.course-card:hover {
  transform: translateY(-5px);
  border-color: var(--primary);
  box-shadow: 0 12px 28px -10px rgba(79,70,229,.35), 0 4px 12px -4px rgba(0,0,0,.12);
}
.course-card:hover .cc-name { color: var(--primary); }
.course-card:hover .course-cat { transform: translateY(-1px) scale(1.06); }
.course-card:hover .cc-meta span { color: var(--text); }

.cc-approved { border-color: #10b981; }
.cc-approved::after { background: linear-gradient(90deg, #10b981, #34d399); transform: scaleX(1); animation: ccGlow 2.8s ease-in-out infinite; }
@keyframes ccGlow { 0%, 100% { opacity: .45; } 50% { opacity: 1; } }

.cc-selected { border-color: #f59e0b; background: rgba(245,158,11,0.05); }
.cc-selected::after { background: linear-gradient(90deg, #f59e0b, #fbbf24); transform: scaleX(1); }

.cc-top { display: flex; justify-content: space-between; align-items: flex-start; gap: 8px; padding-right: 42px; }
/* 收藏式意向按钮微调（基础样式在全局 .intent-btn） */
.intent-btn { z-index: 2; }
.intent-btn .icon { display: block; }
.cc-name { font-size: 15px; font-weight: 600; color: var(--text); transition: color .25s; }
.cc-meta { font-size: 12px; color: var(--text-light, #94a3b8); display: flex; gap: 12px; flex-wrap: wrap; }
.cc-meta span { transition: color .25s; }
.cc-progress { height: 4px; background: var(--border); border-radius: 4px; overflow: hidden; }
.cc-progress-fill {
  height: 100%; border-radius: 4px;
  background: linear-gradient(90deg, #22c55e, #4ade80);
  transition: width .4s ease;
  transform-origin: left;
  animation: ccBar .9s cubic-bezier(.2,.8,.2,1) both;
  animation-delay: calc(var(--i, 0) * 45ms + 120ms);
}
@keyframes ccBar { from { transform: scaleX(0); } to { transform: scaleX(1); } }
/* 满员进度条：红色 + 呼吸 */
.cc-progress-fill.is-full {
  background: linear-gradient(90deg, #ef4444, #f97316);
  animation: ccBar .9s cubic-bezier(.2,.8,.2,1) both,
             ccFullPulse 1.8s ease-in-out 1.2s infinite;
  animation-delay: calc(var(--i, 0) * 45ms + 120ms), 0s;
}
@keyframes ccFullPulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(239,68,68,.45); }
  50%      { box-shadow: 0 0 8px 2px rgba(239,68,68,.45); }
}
.cc-foot { display: flex; justify-content: flex-end; gap: 8px; flex-wrap: wrap; }
.cc-foot :deep(.el-button) { transition: transform .2s, box-shadow .2s; }
.cc-foot :deep(.el-button:hover) { transform: translateY(-2px); box-shadow: 0 4px 12px -4px rgba(0,0,0,.3); }
.cc-foot :deep(.el-button:active) { transform: translateY(0) scale(.97); }

.detail-body { display: flex; flex-direction: column; gap: 10px; }
.dd-row { display: flex; gap: 12px; font-size: 14px; color: var(--text); }
.dd-row b { color: var(--text-light, #94a3b8); min-width: 90px; flex-shrink: 0; }
.dd-row-col { flex-direction: column; }
.dd-row-col b { margin-bottom: 4px; }

.course-cat { font-size: 11px; padding: 2px 8px; border-radius: 999px; background: rgba(59,130,246,0.15); color: #60a5fa; transition: transform .25s cubic-bezier(.2,.7,.3,1); }
.cat-公共必修, .cat-专业必修 { background: rgba(16,185,129,0.15); color: #34d399; }
.cat-公共选修, .cat-专业选修, .cat-选修 { background: rgba(59,130,246,0.15); color: #60a5fa; }
.cat-通识选修 { background: rgba(168,85,247,0.15); color: #c084fc; }
.cat-实践选修 { background: rgba(245,158,11,0.15); color: #fbbf24; }

/* 尊重无障碍：系统开启"减少动态"时全部关闭 */
@media (prefers-reduced-motion: reduce) {
  .course-card,
  .course-card::before,
  .course-card::after,
  .cc-progress-fill,
  .cc-progress-fill.is-full,
  .course-cat,
  .cc-foot :deep(.el-button) {
    animation: none !important;
    transition: none !important;
  }
  .course-card:hover { transform: none; }
}
</style>
