<template>
  <div class="zx-page stats-page liquid">
    <div class="page-head">
      <h2>📚 {{ t('课程浏览') }}</h2>
      <el-input v-model="keyword" :placeholder="t('搜索课程名 / 教师 / 课程编号')" clearable :prefix-icon="SearchIcon" class="kw-input" />
    </div>

    <el-alert
      v-if="myWindow && myWindow.staggered && !myWindow.myOpen && currentRound < 3"
      type="warning"
      :closable="false"
      show-icon
      class="stagger-banner"
      :title="t('错峰开放：你所在的 {grade} 级将于 {time} 开放本轮选课，到点前可先浏览课程、加入意向', { grade: myWindow.grade, time: fmtAt(myWindow.myOpenAt) })"
    />

    <!-- 实时选课战况：静态热榜（最近 3 条），滚动流分散注意力且信息与竞争线重复 -->
    <div class="war-banner" v-if="recent.length && currentRound < 3">
      <span class="war-live"><i class="war-dot" />{{ t('实时战况') }}</span>
      <div class="war-static">
        <span class="war-item" v-for="(w, i) in topWars" :key="i">
          <b>{{ w.student }}</b> {{ t('投') }} <em>{{ w.points }}</em> {{ t('分报名') }} <b class="war-course">《{{ w.courseName }}》</b>
        </span>
      </div>
      <span class="war-more" v-if="recent.length > topWars.length">{{ t('+{n} 条', { n: recent.length - topWars.length }) }}</span>
    </div>

    <div class="filter-bar">
      <el-select v-model="selectedCategory" :placeholder="t('全部类别')" clearable style="width:160px">
        <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
      </el-select>
      <el-select v-model="sortBy" style="width:150px">
        <el-option :label="t('默认排序')" value="default" />
        <el-option :label="t('竞争激烈优先')" value="competition" />
        <el-option :label="t('剩余名额优先')" value="remain" />
        <el-option :label="t('学分高优先')" value="credit" />
      </el-select>
      <el-tag type="info" effect="plain">{{ t('共 {n} 门', { n: total }) }}</el-tag>
      <el-tag v-if="maxCourses" :type="enrolledCount >= maxCourses ? 'danger' : 'success'" effect="plain">
        {{ t('本学期最多可选 {max} 门选修 · 已选 {sel}/{max}', { max: maxCourses, sel: enrolledCount }) }}
      </el-tag>
      <el-tag v-if="canSelect" type="warning" effect="plain">{{ t('剩余积分 {n}', { n: remaining }) }}</el-tag>
      <span class="legend-note" v-if="currentRound < 3">
        <i class="lg-dot hot" />{{ t('火爆') }} <i class="lg-dot warm" />{{ t('较热') }} <i class="lg-dot cool" />{{ t('正常') }} <i class="lg-dot cold" />{{ t('冷门') }}
      </span>
    </div>

    <!-- 骨架屏：首次加载且无数据 -->
    <div v-if="loading && !courses.length" class="course-grid">
      <div v-for="i in 8" :key="'sk' + i" class="course-card sk-card">
        <div class="sk-line w60" />
        <div class="sk-line w90" />
        <div class="sk-line w75" />
        <div class="sk-bar" />
        <div class="sk-line w40" />
      </div>
    </div>

    <div v-else class="course-grid" v-loading="loading">
      <div
        v-for="(c, i) in visibleCourses"
        :key="c.id"
        class="course-card"
        :style="{ '--i': i % PAGE_SIZE }"
        :class="{ 'cc-selected': c.myIntent, 'cc-approved': c.myStatus === 'approved' }"
      >
        <!-- 右上角收藏式意向按钮：仅在「选课未开放」时用于收藏意向 -->
        <button
          v-if="!canSelect && c.myStatus !== 'approved' && c.myStatus !== 'pending'"
          class="intent-btn"
          :class="{ active: c.myIntent }"
          :title="c.myIntent ? t('移出意向') : t('加入意向（选课未开始，可先收藏）')"
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
          <span>🎓 {{ c.credit }} {{ t('学分') }}</span>
          <span>🏛️ {{ c.location }}</span>
          <span v-if="c.prereqName" class="cc-prereq" :title="t('需先通过《{n}》才能选修', { n: c.prereqName })">⚠ {{ t('先修') }}：{{ c.prereqName }}</span>
        </div>
        <div class="cc-meta">
          <span>📅 {{ c.time || t('时间待定') }}</span>
          <span>🎯 {{ capacityText(c) }}</span>
        </div>
        <div class="cc-progress" :class="'st-' + capState(c)">
          <div class="cc-progress-fill" :class="{ 'is-full': capState(c) === 'full', 'is-warn': capState(c) === 'warn' }" :style="{ width: capacityPercent(c) + '%' }" />
        </div>
        <div v-if="!c.capacity" class="cc-cap-note">⚠ {{ t('未设容量，以实际报名为准') }}</div>
        <!-- 入选积分参考：实时竞争线 / 上轮录取线（第三轮先到先得，无积分；无任何数据时整块隐藏，避免空噪声） -->
        <div v-if="hasGuide(c)" class="cc-guide" :title="guideTip(c)">
          <div class="cc-guide-head">
            <span v-if="(c.guide?.bidCount || 0) > 0" class="cc-heat" :class="heat(c).cls">{{ heat(c).label }}</span>
            <span v-if="(c.guide?.bidCount || 0) > 0" class="cc-bidcount" :class="heat(c).cls">{{ bidCountText(c) }}</span>
            <span class="cc-guide-src" :class="recClass(c)">{{ guideText(c) }}</span>
            <span v-if="(c.guide?.topPoints || 0) > 0" class="cc-guide-top">{{ t('最高 {n} 分', { n: c.guide.topPoints }) }}</span>
          </div>
          <div v-if="guideRec(c) != null || (c.myStatus === 'pending' && c.myPoints != null)" class="cc-guide-track">
            <div class="cc-guide-bar" />
            <i
              v-if="guideRec(c) != null"
              class="cc-guide-mark"
              :class="riskClass(guideRec(c))"
              :style="{ left: guidePos(guideRec(c)) + '%' }"
            >{{ guideRec(c) }}</i>
            <i
              v-if="c.myStatus === 'pending' && c.myPoints != null"
              class="cc-guide-mine"
              :style="{ left: guidePos(c.myPoints) + '%' }"
            >{{ t('我 {n}', { n: c.myPoints }) }}</i>
          </div>
          <!-- #3 投点热度：历史轮次录取线迷你趋势 -->
          <div v-if="miniTrend(c).length" class="cc-mini-trend" :title="t('录取线走势：') + miniTrend(c).map((x: any) => t('第{r}轮 {p}分', { r: x.round, p: x.cutline })).join(' → ')">
            <span class="cmt-label">{{ t('线') }}</span>
            <span class="cmt-bars">
              <i v-for="(t, ti) in miniTrend(c)" :key="ti" class="cmt-bar" :class="{ full: t.fullAdmitted }" :style="{ height: t.h + '%' }" />
            </span>
            <span class="cmt-val">{{ miniTrend(c)[miniTrend(c).length - 1].cutline }} {{ t('分') }}</span>
          </div>
        </div>
        <div class="cc-foot">
          <!-- 已录取 -->
          <template v-if="c.myStatus === 'approved'">
            <el-tag type="success" effect="plain" size="small">{{ t('已选') }}</el-tag>
            <el-button size="small" text :disabled="busyId === c.id" @click="enroll(c, true)">{{ busyId === c.id ? t('退课中…') : t('退课') }}</el-button>
          </template>
          <!-- 已报名待录取 -->
          <template v-else-if="c.myStatus === 'pending'">
            <el-tag type="warning" effect="plain" size="small">{{ t('待录取 {n}分', { n: c.myPoints || 0 }) }}</el-tag>
            <el-button size="small" text :disabled="busyId === c.id" @click="cancelEnroll(c)">{{ busyId === c.id ? t('取消中…') : t('取消报名') }}</el-button>
          </template>
          <!-- 候补队列中：显示排位 + 退出候补 -->
          <template v-else-if="c.myWaitStatus">
            <el-tag type="primary" effect="plain" size="small">
              {{ t('候补中') }}<template v-if="c.myWaitPosition"> · {{ t('第 {n} 位', { n: c.myWaitPosition }) }}</template>
            </el-tag>
            <el-button size="small" text :disabled="busyId === c.id" @click="cancelWait(c)">{{ busyId === c.id ? t('退出中…') : t('退出候补') }}</el-button>
          </template>
          <!-- 选课开放中：直接报名 -->
          <template v-else-if="canSelect">
            <el-button v-if="!c.full" size="small" type="success" :disabled="busyId === c.id" @click="openPoints(c)">{{ t('选课') }}</el-button>
            <el-button v-else size="small" type="primary" plain :disabled="busyId === c.id" @click="enroll(c, false)">
              {{ busyId === c.id ? t('加入中…') : t('候补') }}
            </el-button>
          </template>
          <!-- 选课未开放：只能先收藏意向 -->
          <el-button v-else-if="c.myIntent" size="small" type="warning" plain :disabled="busyId === c.id" @click="openPoints(c)">
            {{ t('意向中 {n}分', { n: c.myIntentPoints }) }}
          </el-button>
          <el-button v-else size="small" class="intent-cta" :disabled="busyId === c.id" @click="openPoints(c)">{{ t('加入意向') }}</el-button>
          <el-button size="small" text @click="viewDetail(c)">{{ t('详情') }}</el-button>
        </div>
      </div>
      <!-- 空状态：区分「筛选无结果」与「暂无课程」 -->
      <div v-if="!visibleCourses.length && !loading" class="cc-empty">
        <div class="cc-empty-icon">🔍</div>
        <template v-if="hasFilter">
          <div class="cc-empty-title">{{ t('没有找到符合条件的课程') }}</div>
          <div class="cc-empty-sub">{{ t('试试更换关键词或清空筛选条件') }}</div>
          <el-button size="small" round @click="clearFilters">{{ t('清空搜索与筛选') }}</el-button>
        </template>
        <template v-else>
          <div class="cc-empty-title">{{ t('暂无可选课程') }}</div>
          <div class="cc-empty-sub">{{ t('当前轮次没有开放选修课程，请等待管理员开放') }}</div>
          <el-button size="small" round :disabled="reloadErr" @click="load">{{ t('重新加载') }}</el-button>
        </template>
        <div v-if="reloadErr" class="cc-empty-sub cc-err">{{ t('课程加载失败：') }}{{ reloadErr }}<el-button size="small" text type="primary" @click="load">{{ t('重试') }}</el-button></div>
      </div>
      <!-- 服务端分页：筛选/排序都由后端完成，切页只拉当页 24 门 -->
      <div v-if="total > PAGE_SIZE" class="load-more">
        <el-pagination
          layout="prev, pager, next, jumper"
          :total="total"
          :page-size="PAGE_SIZE"
          :current-page="page"
          background
          @current-change="(p: number) => { page === p ? null : (page = p) }"
        />
      </div>
    </div>

    <el-dialog v-model="detailVisible" :title="detailCourse?.name" width="520px" append-to-body>
      <div v-if="detailCourse" class="detail-body">
        <div class="dd-row"><b>{{ t('课程编号') }}</b><span>{{ detailCourse.courseCode }}</span></div>
        <div class="dd-row"><b>{{ t('教师') }}</b><span>{{ detailCourse.teacherName }}</span></div>
        <div class="dd-row"><b>{{ t('学分') }}</b><span>{{ detailCourse.credit }}</span></div>
        <div class="dd-row"><b>{{ t('类别') }}</b><span>{{ detailCourse.category }}</span></div>
        <div class="dd-row"><b>{{ t('上课时间') }}</b><span>{{ detailCourse.time }}</span></div>
        <div class="dd-row"><b>{{ t('教室') }}</b><span>{{ detailCourse.location }}</span></div>
        <div class="dd-row"><b>{{ t('校区') }}</b><span>{{ detailCourse.campus }}</span></div>
        <div class="dd-row"><b>{{ t('容量 / 已选') }}</b><span>{{ detailCourse.capacity }} / {{ detailCourse.stat?.total || 0 }}</span></div>
        <div class="dd-row" v-if="currentRound < 3"><b>{{ t('入选参考') }}</b><span>{{ guideText(detailCourse) || t('暂无参考线') }}<template v-if="detailCourse.guide?.history?.cutline != null">{{ t('（第 {r} 轮录取线 {p} 分）', { r: detailCourse.guide.history.round, p: detailCourse.guide.history.cutline }) }}</template></span></div>
        <!-- 本轮投分分布直方图 -->
        <div class="dd-dist" v-if="currentRound < 3 && distOf(detailCourse).some((n:number) => n > 0)">
          <b>{{ t('本轮投分分布') }}</b>
          <div class="dd-dist-chart">
            <div class="dd-dist-col" v-for="(n, bi) in distOf(detailCourse)" :key="bi" :title="t('{a}-{b} 分：{n} 人', { a: bi * 5, b: bi * 5 + 4, n })">
              <span class="dd-dist-num" v-show="n > 0">{{ n }}</span>
              <i class="dd-dist-bar" :class="{ mine: myBucket(detailCourse, bi) }" :style="{ height: distH(n, detailCourse) + '%' }" />
              <span class="dd-dist-lab">{{ bi * 5 }}</span>
            </div>
          </div>
          <span class="dd-dist-note">{{ t('横轴为投分区间下限（每格 5 分），橙色柱表示你的投入落在该区间') }}</span>
        </div>
        <!-- 历年录取线走势 -->
        <div class="dd-dist dd-trend" v-if="trendPts.length >= 1">
          <b>{{ t('历年录取线走势') }}</b>
          <svg viewBox="0 0 460 150" class="dd-trend-svg" role="img" :aria-label="t('历年录取线走势折线图')">
            <line x1="36" y1="120" x2="448" y2="120" class="tt-axis" />
            <line x1="36" y1="12" x2="36" y2="120" class="tt-axis" />
            <text x="30" y="24" text-anchor="end" class="tt-y">50</text>
            <text x="30" y="122" text-anchor="end" class="tt-y">0</text>
            <polyline :points="trendLine" class="tt-line" />
            <g v-for="(p, i) in trendPts" :key="i">
              <circle :cx="p.x" :cy="p.y" r="5" class="tt-dot" :class="{ full: p.full }" />
              <text :x="p.x" :y="p.y - 10" text-anchor="middle" class="tt-val">{{ p.cutline }}</text>
              <text :x="p.x" y="138" text-anchor="middle" class="tt-round">{{ t('第{n}轮', { n: p.round }) }}</text>
            </g>
          </svg>
          <span class="dd-dist-note">{{ t('历轮最低录取积分；空心点为全员录取轮次（无落选、竞争不足，仅供参考）') }}</span>
        </div>
        <!-- 选了这门课的人也选了 -->
        <div class="dd-also">
          <b>{{ t('选了这门课的人也选了') }}</b>
          <div v-if="alsoLoading" class="dd-also-empty">{{ t('计算中…') }}</div>
          <div v-else-if="!alsoTook.length" class="dd-also-empty">{{ t('暂无共选记录') }}</div>
          <div v-else class="dd-also-chips">
            <button v-for="tk in alsoTook" :key="tk.courseId" class="dd-also-chip"
              :title="`${tk.category} · ${tk.credit} ${t('学分')} · ${tk.teacherName} · ${tk.time || ''}`"
              @click="openAlso(tk)">
              {{ tk.name }}<i>{{ t('{n} 人共选', { n: tk.count }) }}</i>
            </button>
          </div>
        </div>
        <div class="dd-row dd-row-col"><b>{{ t('课程简介') }}</b><span>{{ detailCourse.intro || '—' }}</span></div>
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
      :swap-candidates="swapCandidates"
      @submit="submitPoints"
      @remove="removeIntent"
    />

    <!-- 候补换课保底弹窗：满员课加入候补时可挂「递补确认后自动退掉某课」 -->
    <el-dialog v-model="waitSwapVisible" :title="t('候补 · 换课保底')" width="420px" append-to-body>
      <div class="ws-body">
        <div class="ws-course">{{ waitCourse?.name }}</div>
        <el-checkbox v-model="waitSwapOn">{{ t('换课保底：递补确认后自动退掉一门已选课') }}</el-checkbox>
        <el-select
          v-if="waitSwapOn"
          v-model="waitSwapFrom"
          :placeholder="t('选择要换掉的已选课程')"
          size="small"
          class="ws-select"
        >
          <el-option
            v-for="s in swapCandidates"
            :key="s.courseId"
            :value="s.courseId"
            :label="`《${s.courseName}》`"
          />
        </el-select>
        <div class="ws-tip" v-if="waitSwapOn">
          {{ t('递补名额到达后确认即自动退掉《A》并录取《B》（原子换课）；确认前可放弃，保底课不受影响。') }}
        </div>
      </div>
      <template #footer>
        <el-button @click="waitSwapVisible = false">{{ t('取消') }}</el-button>
        <el-button type="primary" :loading="busyId === waitCourse?.id" @click="confirmJoinWait">{{ t('加入候补') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import http from '@/api'
import IntentPointsDialog from '@/components/IntentPointsDialog.vue'
import { t } from '@/i18n'

const SearchIcon = Search

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

/* ===== 服务端分页：筛选/排序/分页参数全部传后端，响应 {items,total,categories} ===== */
const total = ref(0)
const page = ref(1)
const categories = ref<string[]>([])

/** 已选选修门数（口径与后端门数上限一致）：从 /enrollments/mine 统计（分页后课程列表只有当页，状态不全） */
const enrolledCount = ref(0)
/** 本人进行中的报名（pending/approved）：换课保底候选 */
const mineEnrollments = ref<any[]>([])
/** 可作为换课保底的课程：进行中的非必修报名 */
const swapCandidates = computed(() =>
  mineEnrollments.value.filter((x: any) =>
    (x.status === 'pending' || x.status === 'approved') && !(x.category || '').includes('必修')
  )
)

const selectableCourses = computed(() => courses.value)

const sortBy = ref<'default' | 'competition' | 'remain' | 'credit'>('default')

/* 服务端已完成筛选与排序，这里只做透传 */
const filteredCourses = computed(() => courses.value)

/** 是否有筛选条件（用于空状态区分） */
const hasFilter = computed(() => !!(keyword.value.trim() || selectedCategory.value || sortBy.value !== 'default'))
function clearFilters() {
  keyword.value = ''
  selectedCategory.value = null
  sortBy.value = 'default'
  page.value = 1
}

/* ===== 分页：每页 24 门，切页/筛选由服务端返回对应页 ===== */
const PAGE_SIZE = 24
const visibleCourses = computed(() => filteredCourses.value)

/** 本学期选课门数上限（系统配置 maxCoursesPerSemester） */
const maxCourses = computed(() => Number(systemConfig.value.maxCoursesPerSemester) || 0)

/** 容量进度口径：报名占位数 stat.total（与后端 full / oversubscribed 同口径，原误用 approved 导致进度偏小） */
function capacityPercent(c: any) {
  const cap = c.capacity || 0
  if (cap <= 0) return 0
  return Math.min(100, ((c.stat?.total || 0) / cap) * 100)
}
/** 进度条状态：full 满员红 / warn ≥80% 预警橙 / ok 正常绿 */
function capState(c: any): 'full' | 'warn' | 'ok' {
  if ((c.capacity || 0) <= 0) return 'ok'
  return c.full ? 'full' : (capacityPercent(c) >= 80 ? 'warn' : 'ok')
}
/** 已选/容量文案：容量 0 时给出明确占位，避免 0/0 误导 */
function capacityText(c: any): string {
  const cap = c.capacity || 0
  return cap <= 0 ? t('未设容量') : `${c.stat?.total || 0}/${cap}`
}

/* ---------- 入选积分参考 ---------- */
const poolMax = computed(() => pointPool.value || 50)
/** 建议投分：优先实时竞争线（本轮报名已超容量），否则用上一轮录取线 */
function guideRec(c: any): number | null {
  const g = c.guide
  if (!g) return null
  if (g.safeLine != null) return g.safeLine
  return g.history?.cutline ?? null
}
function guideText(c: any): string {
  const g = c.guide
  if (!g) return ''
  if (g.safeLine != null) return c.full ? t('当前录取线 {n} 分', { n: g.safeLine }) : t('实时竞争线 {n} 分', { n: g.safeLine })
  const rec = g.history?.cutline
  if (rec != null) return t('上轮录取线 {n} 分', { n: rec })
  if (g.bidCount > 0) return t('报名未满员 · 暂无竞争线')
  return t('尚无人投点 · 投分即可选上')
}
/** 卡片是否显示积分参考块：完全没数据就不显示，避免一排"0 分/暂无"噪声 */
function hasGuide(c: any): boolean {
  if (currentRound.value >= 3) return false
  const g = c.guide
  if (!g) return false
  return (g.bidCount || 0) > 0 || (g.topPoints || 0) > 0 || g.safeLine != null || g.history?.cutline != null
}
function recClass(c: any): string {
  const rec = guideRec(c)
  return rec != null ? riskClass(rec) : 'none'
}
function riskClass(v: number): string {
  return v >= 40 ? 'hot' : v >= 25 ? 'warm' : 'cool'
}
function guidePos(v: number): number {
  return Math.min(100, Math.max(0, (v / poolMax.value) * 100))
}
function guideTip(c: any): string {
  const g = c.guide
  if (!g) return ''
  if (g.safeLine != null)
    return t('本轮已有 {bid} 人报名（容量 {cap}），按投分从高到低排，当前第 {cap} 名为 {line} 分——投分高于此线才稳', { bid: g.bidCount, cap: g.capacity, line: g.safeLine })
  if (g.history?.cutline != null)
    return t('第 {r} 轮 {a} 人报名，最低录取分 {c} 分，可作为本轮参考', { r: g.history.round, a: g.history.applied, c: g.history.cutline })
  return t('本轮报名未满员，暂无竞争线；投分后若名额被投高分者占满，需超过第 {n} 名的分数', { n: g.capacity })
}
/** 竞争热度：报名人数 / 容量 */
function heat(c: any): { label: string; cls: string } {
  const g = c.guide
  const cap = c.capacity || 0
  const ratio = g && cap > 0 ? g.bidCount / cap : 0
  if (ratio >= 1.5) return { label: t('火爆'), cls: 'hot' }
  if (ratio >= 1) return { label: t('较热'), cls: 'warm' }
  if (ratio >= 0.5) return { label: t('正常'), cls: 'cool' }
  return { label: t('冷门'), cls: 'cold' }
}
/** #3 当前投点人数提示 */
function bidCountText(c: any): string {
  const g = c.guide
  if (!g) return ''
  return t('{n} 人已投', { n: g.bidCount || 0 })
}
/** #3 卡片迷你录取线趋势（最近 5 轮） */
function miniTrend(c: any): any[] {
  const trend = (c.guide?.trend || []).filter((t: any) => t.cutline != null)
  if (!trend.length) return []
  const max = Math.max(1, ...trend.map((t: any) => t.cutline))
  return trend.slice(-5).map((t: any) => ({ ...t, h: Math.max(18, Math.round(t.cutline / max * 100)) }))
}

// ===== 历年录取线走势（guide.trend，仅统计有落选者的轮次） =====
const trendPts = computed(() => {
  const trend = detailCourse.value?.guide?.trend || []
  const pts = trend.filter((t: any) => t.cutline != null)
  if (!pts.length) return []
  const L = 44, R = 448, top = 20, bottom = 118
  return pts.map((t: any, i: number) => ({
    x: pts.length === 1 ? (L + R) / 2 : L + (R - L) * i / (pts.length - 1),
    y: bottom - (t.cutline / 50) * (bottom - top),
    round: t.round,
    cutline: t.cutline,
    full: !!t.fullAdmitted,
  }))
})
const trendLine = computed(() => trendPts.value.map((p: any) => `${p.x},${p.y}`).join(' '))

// ===== 协同过滤：选了这门课的人也选了 =====
const alsoTook = ref<any[]>([])
const alsoLoading = ref(false)
async function viewDetail(c: any) {
  detailCourse.value = c
  detailVisible.value = true
  alsoTook.value = []
  alsoLoading.value = true
  try {
    const r = await http.get(`/courses/${c.id}/also-took?n=6`)
    alsoTook.value = r?.data || []
  } catch { alsoTook.value = [] }
  alsoLoading.value = false
}
function openAlso(tk: any) {
  const found = courses.value.find(c => c.id === tk.courseId)
  if (found) viewDetail(found)
  else ElMessage.info(t('《{name}》不在当前可选课程列表中', { name: tk.name }))
}

/* ===== 详情弹窗：本轮投分分布直方图 ===== */
/* ===== 实时战况流 ===== */
const recent = ref<any[]>([])
const warItems = computed(() => {
  return recent.value.map(w => ({
    student: w.student || t('同学'),
    points: w.points ?? 0,
    courseName: w.courseName || '',
  }))
})
const topWars = computed(() => warItems.value.slice(0, 3))
let warTimer: any = null
async function loadRecent() {
  try {
    const r = await http.get('/enrollments/recent', { params: { limit: 12 } })
    const next = r?.data || []
    recent.value = next
  } catch { /* 战况流失败不影响主页面 */ }
}
onMounted(() => { loadRecent(); warTimer = setInterval(loadRecent, 15000) })
onBeforeUnmount(() => { if (warTimer) clearInterval(warTimer) })

function distOf(c: any): number[] {
  const d = c?.guide?.dist?.counts
  return Array.isArray(d) && d.length === 10 ? d : new Array(10).fill(0)
}
function distH(n: number, c: any): number {
  const max = Math.max(...distOf(c), 1)
  return n > 0 ? Math.max(8, Math.round(n * 100 / max)) : 0
}
function myBucket(c: any, bi: number): boolean {
  const p = c?.myStatus === 'pending' ? c.myPoints : (c?.myIntent ? c.myIntentPoints : null)
  return p != null && Math.floor(p / 5) === bi
}

/** 操作防重：同一时间只允许一张卡在操作 */
const busyId = ref<number | null>(null)
const reloadErr = ref('')

async function enroll(c: any, isDrop: boolean) {
  if (busyId.value) return
  try {
    if (isDrop) {
      await ElMessageBox.confirm(t('确定退选「{name}」？', { name: c.name }), t('提示'), { type: 'warning', confirmButtonText: t('确定'), cancelButtonText: t('取消') })
      busyId.value = c.id
      await http.post('/enrollments/drop', { courseId: c.id })
      ElMessage.success(t('已退选'))
    } else {
      // 候补：有在修选修课时弹「换课保底」选择，否则直接加入队列
      if (swapCandidates.value.length) {
        waitCourse.value = c
        waitSwapOn.value = false
        waitSwapFrom.value = swapCandidates.value[0].courseId
        waitSwapVisible.value = true
        return
      }
      busyId.value = c.id
      await http.post('/waitlist/join', { courseId: c.id })
      ElMessage.success(t('已加入候补队列'))
    }
    load()
  } catch (e: any) {
    if (e !== 'cancel' && e?.message !== 'cancel' && !e?.toString?.().includes('cancel'))
      ElMessage.error(e?.message || t('操作失败'))
  } finally { busyId.value = null }
}

// ===== 候补换课保底弹窗 =====
const waitSwapVisible = ref(false)
const waitCourse = ref<any>(null)
const waitSwapOn = ref(false)
const waitSwapFrom = ref<number | null>(null)
async function confirmJoinWait() {
  const c = waitCourse.value
  if (!c) return
  if (waitSwapOn.value && !waitSwapFrom.value) { ElMessage.warning(t('请选择要换掉的已选课程')); return }
  busyId.value = c.id
  try {
    await http.post('/waitlist/join', {
      courseId: c.id,
      swapFromCourseId: waitSwapOn.value ? waitSwapFrom.value : null,
    })
    ElMessage.success(waitSwapOn.value
      ? t('已加入候补并挂上换课保底：递补确认后自动换课')
      : t('已加入候补队列'))
    waitSwapVisible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || t('操作失败'))
  } finally { busyId.value = null }
}

/** 退出候补队列 */
async function cancelWait(c: any) {
  if (busyId.value) return
  try {
    await ElMessageBox.confirm(t('确定退出《{name}》的候补队列？退出后需重新排队。', { name: c.name }), t('退出候补'),
      { type: 'warning', confirmButtonText: t('确定退出'), cancelButtonText: t('再想想') })
  } catch { return }
  busyId.value = c.id
  try {
    await http.post('/waitlist/cancel', { courseId: c.id })
    ElMessage.success(t('已退出候补'))
    await load()
  } catch (e: any) { ElMessage.error(e?.message || t('操作失败')) } finally { busyId.value = null }
}

/** 打开投入积分弹窗：选课开放 → 正式报名；未开放 → 收藏意向 */
function openPoints(c: any) {
  pendingCourse.value = c
  dialogMode.value = canSelect.value ? 'enroll' : 'intent'
  pointsVisible.value = true
}

/** 取消已提交的报名（pending）：撤销报名并退回积分 */
async function cancelEnroll(c: any) {
  if (busyId.value) return
  try {
    await ElMessageBox.confirm(
      t('确定取消《{name}》的报名？已投入的 {p} 积分将退回。', { name: c.name, p: c.myPoints ?? 0 }),
      t('取消报名'), { type: 'warning', confirmButtonText: t('确定取消'), cancelButtonText: t('再想想') }
    )
  } catch { return }
  busyId.value = c.id
  try {
    const r: any = await http.post('/enrollments/drop', { courseId: c.id })
    ElMessage.success(r?.message || t('已撤销报名'))
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || t('取消失败'))
  } finally { busyId.value = null }
}

/** 收藏式意向按钮：一键加入（默认 10 分）/ 确认后移出 */
async function toggleIntent(c: any) {
  if (busyId.value) return
  if (c.myIntent) {
    try {
      await ElMessageBox.confirm(t('确定移出《{name}》的意向？移出后需重新添加。', { name: c.name }), t('移出意向'),
        { type: 'warning', confirmButtonText: t('确定移出'), cancelButtonText: t('保留') })
    } catch { return }
  }
  busyId.value = c.id
  try {
    if (c.myIntent) {
      await http.post('/intents/remove', { courseId: c.id })
      ElMessage.success({ message: t('已移出意向：{name}', { name: c.name }), duration: 1500 })
    } else {
      await http.post('/intents/add', { courseId: c.id, points: 10 })
      ElMessage.success({ message: t('已加入意向：{name}（默认 10 分，点「意向中」可调整）', { name: c.name }), duration: 2500 })
    }
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || t('操作失败'))
  } finally { busyId.value = null }
}

/**
 * 确认提交：
 * - 换课保底（swapFrom 非空）：调 /enrollments/swap 原子换课——第 1/2 轮登记保底（A 保留，结算时 B 录取才退 A）；
 *   第 3 轮当场互换。选不上新课原课绝不受影响；
 * - 普通报名（enroll）：调 /enrollments/enroll 正式报名，当场扣积分、进「待录取」；
 * - 选课未开放（intent）：只收藏到意向单，等开放后再转正式。
 */
async function submitPoints(points: number, swapFrom?: number | null) {
  const c = pendingCourse.value
  if (!c || pointsSubmitting.value) return
  pointsSubmitting.value = true
  const enrollMode = dialogMode.value === 'enroll'
  try {
    if (enrollMode && swapFrom) {
      const r: any = await http.post('/enrollments/swap', { fromCourseId: swapFrom, toCourseId: c.id, points })
      if (c.myIntent) { try { await http.post('/intents/remove', { courseId: c.id }) } catch {} }
      ElMessage.success({ message: r?.message || t('换课保底已挂起'), duration: 3600 })
    } else if (enrollMode) {
      const r: any = await http.post('/enrollments/enroll', { courseId: c.id, points })
      // 报名成功后清掉同一门课的旧意向，避免意向单里残留「已报名」的课程
      if (c.myIntent) { try { await http.post('/intents/remove', { courseId: c.id }) } catch {} }
      ElMessage.success({ message: r?.message || t('报名成功！已投入 {n} 积分', { n: points }), duration: 3200 })
    } else {
      // 后端 POST /intents/add 为 upsert，同一课程会覆盖积分
      await http.post('/intents/add', { courseId: c.id, points })
      const isEdit = !!c.myIntent
      ElMessage.success(isEdit
        ? t('已把「{name}」调整为 {p} 分', { name: c.name, p: points })
        : t('已加入意向：{name}（投入 {p} 分，选课开放后可一键转正式选课）', { name: c.name, p: points }))
    }
    pointsVisible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || t('操作失败'))
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
    ElMessage.success(t('已移出意向'))
    pointsVisible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || t('移除失败'))
  }
}

async function load() {
  loading.value = true
  reloadErr.value = ''
  try {
    const params: any = { page: page.value, size: PAGE_SIZE, electiveOnly: true }
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    if (selectedCategory.value) params.category = selectedCategory.value
    if (sortBy.value !== 'default') params.sort = sortBy.value
    const [r, p, cfg, mine]: any[] = await Promise.all([
      http.get('/courses', { params }),
      http.get('/enrollments/points').catch(() => null),
      http.get('/system/config').catch(() => null),
      http.get('/enrollments/mine').catch(() => null),
    ])
    // 服务端分页响应 {items,total,categories}；旧后端（未重启）返回数组时本地切片分页兼容
    const d = r?.data
    if (Array.isArray(d)) {
      // 类别下拉：旧后端无 facets，从全量数组提取
      if (!categories.value.length) {
        const set = new Set<string>()
        d.forEach((c: any) => { if (c.category && !c.category.includes('必修')) set.add(c.category) })
        categories.value = Array.from(set)
      }
      // 旧后端忽略 electiveOnly：本地剔除必修后再切片
      const elective = d.filter((c: any) => !(c.category || '').includes('必修'))
      const start = (page.value - 1) * PAGE_SIZE
      courses.value = elective.slice(start, start + PAGE_SIZE)
      total.value = elective.length
    } else {
      courses.value = d?.items || []
      total.value = d?.total || 0
      if (d?.categories?.length) categories.value = d.categories
    }
    // 已选选修门数：从当前轮报名记录统计（列表分页后 myStatus 只覆盖当页）
    mineEnrollments.value = mine?.data || []
    enrolledCount.value = (mine?.data || []).filter((x: any) =>
      (x.status === 'pending' || x.status === 'approved') && !(x.category || '').includes('必修')
    ).length
    remaining.value = p?.data?.remaining ?? 0
    currentRound.value = p?.data?.round ?? 1
    pointPool.value = p?.data?.total ?? 50
    systemConfig.value = cfg?.data || {}
  } catch (e: any) {
    reloadErr.value = e?.message || t('网络异常，请稍后重试')
  }
  try {
    const w: any = await http.get('/system/my-window')
    myWindow.value = w?.data || null
  } catch { myWindow.value = null }
  loading.value = false
}
/* ===== 分页联动：关键字防抖 300ms；筛选变化回第 1 页；切页重新请求 ===== */
let kwTimer: any = null
watch(keyword, () => {
  clearTimeout(kwTimer)
  kwTimer = setTimeout(() => { page.value === 1 ? load() : (page.value = 1) }, 300)
})
watch([selectedCategory, sortBy], () => { page.value === 1 ? load() : (page.value = 1) })
watch(page, load)
onMounted(load)
</script>

<style scoped>
.zx-page { display: flex; flex-direction: column; gap: 16px; }
.page-head { display: flex; align-items: center; justify-content: space-between; gap: 14px; flex-wrap: wrap; }
.page-head h2 { margin: 0; color: var(--text, #f1f5f9); font-size: 20px; }
.kw-input { width: 300px; max-width: 100%; }

.filter-bar { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.stagger-banner { margin-bottom: 2px; }

/* 同行等高：不用 align-items:start（那会让卡片按内容各自收缩，热门卡更高导致不齐）。
   默认 stretch 让同行卡片顶部对齐且高度一致；卡片 flex column + cc-foot margin-top:auto
   保证额外信息（积分参考/热度）只在自己卡内增高，按钮行始终沉底。 */
.course-grid { display: grid; gap: 14px; grid-template-columns: 1fr; }
@media (min-width: 780px)  { .course-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (min-width: 1150px) { .course-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (min-width: 1600px) { .course-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); } }
/* 空状态 / 加载更多 / 骨架屏独占一行 */
.cc-empty, .load-more { grid-column: 1 / -1; }
.course-card {
  position: relative; overflow: hidden;
  background: var(--card-bg, #0f172a); border: 1px solid var(--border, var(--border));
  border-radius: 12px; padding: 16px 18px; display: flex; flex-direction: column; gap: 10px;
  transition: transform .28s cubic-bezier(.2,.7,.3,1), border-color .28s, box-shadow .28s, background .28s;
  /* 统一最小高度：短内容卡片与热门卡片同行时高度一致 */
  min-height: 208px;
  /* 滚动性能：51 张卡不做 will-change 预提升（合成层过多反而卡死） */
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
  background: linear-gradient(115deg, transparent 32%, rgba(31,166,122,.12) 50%, transparent 68%);
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
  box-shadow: 0 12px 28px -10px rgba(31,166,122,.35), 0 4px 12px -4px rgba(0,0,0,.12);
}
.course-card:hover /* 先修要求标记（琥珀警示，悬停提示完整规则） */
.cc-prereq { color: #b45309; background: rgba(217,119,6,.1); border-radius: 6px; padding: 0 6px; font-size: 11.5px; }
html.dark .cc-prereq { color: #fbbf24; }
.cc-name { color: var(--primary); }
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
/* 预警：≥80% 橙色 */
.cc-progress-fill.is-warn { background: linear-gradient(90deg, #f59e0b, #fbbf24); }
/* 满员进度条：红色 + 呼吸（呼吸只动 opacity，合成器处理；box-shadow 动画会逐帧重绘） */
.cc-progress-fill.is-full {
  background: linear-gradient(90deg, #ef4444, #f97316);
  box-shadow: 0 0 8px 2px rgba(239,68,68,.4);
  animation: ccBar .9s cubic-bezier(.2,.8,.2,1) both,
             ccFullPulse 1.8s ease-in-out 1.2s infinite;
  animation-delay: calc(var(--i, 0) * 45ms + 120ms), 0s;
}
@keyframes ccFullPulse {
  0%, 100% { opacity: 1; }
  50%      { opacity: .68; }
}
/* 容量未设置：进度条禁用灰 */
.cc-progress.st-ok:has(+ .cc-cap-note) .cc-progress-fill { background: rgba(148,163,184,.4); }
.cc-cap-note { font-size: 12px; color: #b45309; }

/* ===== 空状态 ===== */
.cc-empty { text-align: center; padding: 56px 20px 40px; display: flex; flex-direction: column; align-items: center; gap: 8px; }
.cc-empty-icon { font-size: 46px; opacity: .5; }
.cc-empty-title { font-size: 15px; font-weight: 700; color: var(--text); }
.cc-empty-sub { font-size: 12.5px; color: var(--text-light); margin-bottom: 6px; }
.cc-empty-sub.cc-err { color: #be123c; display: flex; align-items: center; gap: 4px; }
.load-more { display: flex; justify-content: center; padding: 4px 0 8px; }

/* ===== 骨架屏 ===== */
.sk-card { pointer-events: none; min-height: 168px; }
.sk-line { height: 13px; border-radius: 6px; background: linear-gradient(90deg, rgba(148,163,184,.14), rgba(148,163,184,.26), rgba(148,163,184,.14)); background-size: 200% 100%; animation: skShimmer 1.4s ease-in-out infinite; margin: 3px 0; }
.sk-line.w90 { width: 90%; } .sk-line.w75 { width: 75%; } .sk-line.w60 { width: 60%; height: 17px; } .sk-line.w40 { width: 40%; }
.sk-bar { height: 5px; border-radius: 99px; margin: 10px 0 6px; background: linear-gradient(90deg, rgba(148,163,184,.14), rgba(148,163,184,.3), rgba(148,163,184,.14)); background-size: 200% 100%; animation: skShimmer 1.4s ease-in-out infinite; }
@keyframes skShimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }
.cc-foot { display: flex; justify-content: flex-end; gap: 8px; flex-wrap: wrap; margin-top: auto; padding-top: 4px; }
.cc-foot :deep(.el-button) { transition: transform .2s, box-shadow .2s; }
.cc-foot :deep(.el-button:hover) { transform: translateY(-2px); box-shadow: 0 4px 12px -4px rgba(0,0,0,.3); }
.cc-foot :deep(.el-button:active) { transform: translateY(0) scale(.97); }

/* ===== 候补换课保底弹窗 ===== */
.ws-body { display: flex; flex-direction: column; gap: 10px; }
.ws-course { font-size: 15px; font-weight: 600; color: var(--text); }
/* 复选框做成醒目的可选卡片，避免淡色框看不清 */
.ws-body :deep(.el-checkbox) {
  width: 100%; padding: 10px 12px; border-radius: 10px;
  background: rgba(56,189,248,.13); border: 1px solid rgba(56,189,248,.55);
  align-items: flex-start;
}
.ws-body :deep(.el-checkbox .el-checkbox__label) { font-size: 13px; color: var(--text); font-weight: 600; line-height: 1.5; }
.ws-select { width: 100%; }
.ws-tip {
  font-size: 12px; color: var(--text-light); line-height: 1.6;
  background: rgba(148,163,184,.12); border: 1px solid rgba(148,163,184,.3);
  border-radius: 8px; padding: 8px 11px;
}
html.dark .ws-tip { background: rgba(148,163,184,.10); border-color: rgba(148,163,184,.26); }

.detail-body { display: flex; flex-direction: column; gap: 10px; }
.dd-row { display: flex; gap: 12px; font-size: 14px; color: var(--text); }
.dd-row b { color: var(--text-light, #94a3b8); min-width: 90px; flex-shrink: 0; }
.dd-row-col { flex-direction: column; }
.dd-row-col b { margin-bottom: 4px; }

/* 详情弹窗：本轮投分分布直方图 */
.dd-dist { display: flex; flex-direction: column; gap: 6px; padding: 10px 12px; border: 1px solid var(--border); border-radius: 10px; background: rgba(148,163,184,.06); }
.dd-dist > b { font-size: 13px; color: var(--text); }
.dd-dist-chart { display: flex; align-items: flex-end; gap: 5px; height: 92px; }
.dd-dist-col { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: flex-end; gap: 3px; height: 100%; }
.dd-dist-num { font-size: 12px; font-weight: 700; color: var(--text); line-height: 1; }
.dd-dist-bar { display: block; width: 70%; max-width: 26px; border-radius: 4px 4px 2px 2px; background: linear-gradient(180deg, #4cc9a3, #2bb187); min-height: 3px; transition: height .4s cubic-bezier(.22,.9,.36,1); }
.dd-dist-bar.mine { background: linear-gradient(180deg, #fbbf24, #f59e0b); }
.dd-dist-lab { font-size: 12px; color: var(--text-light, #94a3b8); line-height: 1; }
.dd-dist-note { font-size: 12px; color: var(--text-light, #94a3b8); }

/* ===== 历年录取线走势 ===== */
.dd-trend-svg { width: 100%; height: auto; }
.tt-axis { stroke: rgba(148,163,184,.4); stroke-width: 1; }
.tt-y { font-size: 12px; fill: var(--text-light, #94a3b8); }
.tt-line { fill: none; stroke: #38bdf8; stroke-width: 2.5; stroke-linecap: round; stroke-linejoin: round; filter: drop-shadow(0 2px 4px rgba(56,189,248,.35)); }
.tt-dot { fill: #2bb187; stroke: #fff; stroke-width: 1.6; }
.tt-dot.full { fill: #0b1220; stroke: #38bdf8; stroke-width: 2; }
.tt-val { font-size: 12px; font-weight: 700; fill: #7dd3fc; }
.tt-round { font-size: 12px; fill: var(--text-light, #94a3b8); }

/* ===== 选了这门课的人也选了 ===== */
.dd-also { display: flex; flex-direction: column; gap: 8px; padding: 10px 12px; border: 1px solid var(--border); border-radius: 10px; background: rgba(148,163,184,.06); }
.dd-also-empty { font-size: 12px; color: var(--text-light, #94a3b8); }
.dd-also-chips { display: flex; flex-wrap: wrap; gap: 6px; }
.dd-also-chip { display: inline-flex; align-items: center; gap: 6px; padding: 5px 10px; border: 1px solid rgba(56,189,248,.35); border-radius: 999px; background: rgba(56,189,248,.1); color: var(--text, #e2e8f0); font-size: 12px; cursor: pointer; transition: all .15s; }
.dd-also-chip:hover { background: rgba(56,189,248,.22); transform: translateY(-1px); }
.dd-also-chip i { font-style: normal; font-size: 12px; color: #7dd3fc; font-weight: 700; }

/* ===== 实时战况流 ===== */
.war-banner { display: flex; align-items: center; gap: 14px; background: var(--card); border: 1px solid rgba(244,63,94,.28); border-radius: 12px; padding: 9px 16px; margin-bottom: 12px; overflow: hidden; }
.war-live { display: flex; align-items: center; gap: 7px; font-size: 12.5px; font-weight: 800; color: #fb7185; flex-shrink: 0; white-space: nowrap; }
.war-dot { width: 8px; height: 8px; border-radius: 99px; background: #f43f5e; }
@keyframes war-blink { 0%, 100% { opacity: 1; box-shadow: 0 0 6px rgba(244,63,94,.8); } 50% { opacity: .25; box-shadow: none; } }
.war-static { flex: 1; display: flex; gap: 28px; overflow: hidden; min-width: 0; }
.war-static .war-item { white-space: nowrap; }
.war-more { flex-shrink: 0; font-size: 12px; color: var(--text-lighter); white-space: nowrap; }

/* 卡片主操作：实心深薄荷，与次操作（详情 text）拉开层级 */
.intent-cta {
  background: linear-gradient(135deg, #168263, #0e6b4f);
  border: none;
  color: #fff;
  font-weight: 600;
}
.intent-cta:hover, .intent-cta:focus {
  background: linear-gradient(135deg, #12755a, #0a5c44);
  border: none;
  color: #fff;
}
.war-item { display: inline-flex; align-items: center; gap: 4px; font-size: 12.5px; color: var(--text-light); }
.war-item b { color: var(--text); font-weight: 700; }
.war-item b.war-course { color: #4cc9a3; }
.war-item em { font-style: normal; font-weight: 800; color: #f59e0b; font-variant-numeric: tabular-nums; }

.course-cat { font-size: 12px; padding: 2px 8px; border-radius: 999px; background: rgba(31,166,122,0.14); color: #0e7a58; transition: transform .25s cubic-bezier(.2,.7,.3,1); }

/* ===== 入选积分参考条 ===== */
.cc-guide { display: flex; flex-direction: column; gap: 2px; }
.cc-guide-head { display: flex; align-items: center; gap: 8px; font-size: 12px; }
.cc-heat { padding: 1px 7px; border-radius: 999px; font-size: 12px; font-weight: 700; flex-shrink: 0; }
.cc-heat.hot  { color: #f43f5e; background: rgba(244,63,94,.12); }
.cc-heat.warm { color: #d97706; background: rgba(245,158,11,.12); }
.cc-heat.cool { color: #059669; background: rgba(16,185,129,.12); }
.cc-heat.cold { color: #64748b; background: rgba(100,116,139,.12); }
.cc-guide-src { font-weight: 600; color: var(--text-light, #94a3b8); min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cc-guide-src.hot  { color: #f43f5e; }
.cc-guide-src.warm { color: #d97706; }
.cc-guide-src.cool { color: #059669; }
.cc-guide-top { margin-left: auto; color: var(--text-light, #94a3b8); opacity: .8; flex-shrink: 0; }
.cc-bidcount { font-size: 12px; font-weight: 700; flex-shrink: 0; opacity: .9; }
.cc-bidcount.hot  { color: #f43f5e; }
.cc-bidcount.warm { color: #d97706; }
.cc-bidcount.cool { color: #059669; }
.cc-bidcount.cold { color: #64748b; }
/* #3 迷你录取线趋势条 */
.cc-mini-trend { display: flex; align-items: flex-end; gap: 8px; margin-top: 6px; padding-top: 6px; border-top: 1px dashed var(--border, rgba(148,163,184,.18)); }
.cmt-label { font-size: 12px; opacity: .55; align-self: center; }
.cmt-bars { flex: 1; display: flex; align-items: flex-end; gap: 3px; height: 18px; }
.cmt-bar { flex: 1; min-width: 5px; max-width: 22px; border-radius: 2px 2px 0 0; background: linear-gradient(180deg, #22d3ee, #2bb187); opacity: .85; }
.cmt-bar.full { background: linear-gradient(180deg, #34d399, #059669); }
.cmt-val { font-size: 12px; font-weight: 700; opacity: .75; flex-shrink: 0; }

.cc-guide-track { position: relative; height: 30px; }
.cc-guide-bar {
  position: absolute; left: 0; right: 0; top: 50%; transform: translateY(-50%);
  height: 6px; border-radius: 99px;
  background: linear-gradient(90deg, rgba(16,185,129,.28) 0%, rgba(16,185,129,.22) 50%, rgba(245,158,11,.3) 72%, rgba(239,68,68,.34) 100%);
  border: 1px solid var(--border, rgba(148,163,184,.2));
}
.cc-guide-mark, .cc-guide-mine {
  position: absolute; transform: translateX(-50%);
  font-style: normal; font-size: 12px; font-weight: 700; line-height: 1;
  padding: 2px 6px; border-radius: 999px; white-space: nowrap;
}
/* 建议投分标记：在轨道上方 */
.cc-guide-mark { top: -2px; }
.cc-guide-mark::after {
  content: ''; position: absolute; left: 50%; top: 100%; transform: translateX(-50%);
  width: 2px; height: 13px; border-radius: 1px; opacity: .75;
}
.cc-guide-mark.cool::after { background: #10b981; }
.cc-guide-mark.warm::after { background: #f59e0b; }
.cc-guide-mark.hot::after  { background: #ef4444; }
.cc-guide-mark.cool { color: #059669; background: rgba(16,185,129,.14); }
.cc-guide-mark.warm { color: #d97706; background: rgba(245,158,11,.14); }
.cc-guide-mark.hot  { color: #e11d48; background: rgba(244,63,94,.14); }
/* 我的投分标记：在轨道下方 */
.cc-guide-mine { bottom: -3px; color: #fff; background: var(--primary-deep); box-shadow: 0 2px 6px rgba(0,0,0,.3); }
.cc-guide-mine::before {
  content: ''; position: absolute; left: 50%; bottom: 100%; transform: translateX(-50%);
  width: 2px; height: 12px; border-radius: 1px; background: #38bdf8; opacity: .7;
}

.legend-note { display: inline-flex; align-items: center; gap: 5px; font-size: 12px; color: var(--text-light, #94a3b8); margin-left: auto; }
.lg-dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; margin-left: 6px; }
.lg-dot:first-child { margin-left: 0; }
.lg-dot.hot  { background: #f43f5e; }
.lg-dot.warm { background: #d97706; }
.lg-dot.cool { background: #059669; }
.lg-dot.cold { background: #64748b; }
.cat-公共必修, .cat-专业必修 { background: rgba(100,116,139,0.14); color: #475569; }
.cat-公共选修, .cat-专业选修, .cat-选修 { background: rgba(31,166,122,0.14); color: #0e7a58; }
.cat-通识选修 { background: rgba(56,189,248,0.16); color: #0369a1; }
.cat-实践选修 { background: rgba(245,158,11,0.16); color: #b45309; }

/* 尊重无障碍：系统开启"减少动态"时全部关闭 */
@media (prefers-reduced-motion: reduce) {
  .course-card,
  .course-card::before,
  .course-card::after,
  .cc-progress-fill,
  .cc-progress-fill.is-full,
  .course-cat,
  .cc-guide-mark,
  .cc-guide-mine,
  .cc-foot :deep(.el-button) {
    animation: none !important;
    transition: none !important;
  }
  .course-card:hover { transform: none; }
}
</style>
