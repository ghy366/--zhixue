<template>
  <div class="stats-page liquid">
    <!-- ===== 学域星图简报（三端通用 · 含 three.js 3D 核心） ===== -->
    <div class="ark-brief" v-if="!bigScreen">
      <span class="ark-corner tl" aria-hidden="true" /><span class="ark-corner br" aria-hidden="true" />
      <div class="ab-left">
        <div class="ab-kicker">ZHIXUE · COURSE ATLAS <em>// {{ briefCode }}</em></div>
        <div class="ab-title">
          <span class="ab-mark">◤</span>
          <b>{{ t('学习总览') }}</b>
          <span class="ab-role">{{ t(roleTactical) }}</span>
        </div>
        <div class="ab-hello">{{ t('欢迎回来，') }}<b>{{ auth.user?.name || t('同学') }}</b><span class="ab-slash">/</span>{{ roleTip }}</div>
        <div class="ab-meta">
          <span class="ab-led" aria-hidden="true" />{{ t('系统在线') }}
          <i aria-hidden="true" />
          <span>ID / {{ auth.user?.no || '—' }}</span>
          <i aria-hidden="true" />
          <span>轮次 / {{ systemConfig.currentRound || '—' }}</span>
          <i aria-hidden="true" />
          <span :class="systemConfig.roundOpen ? 'ab-on' : 'ab-off'">{{ systemConfig.roundOpen ? t('选课进行中') : t('选课未开放') }}</span>
          <i aria-hidden="true" />
          <span class="ab-clock">{{ nowText.slice(-8) }}</span>
        </div>
      </div>
      <div class="ab-right"><ArkHud /></div>
      <div class="ab-hazard" aria-hidden="true" />
    </div>

    <!-- ===== 教师 / 管理员 KPI（唯一一处） ===== -->
    <div v-if="!isStudent" class="stats-grid">
      <div v-for="c in statCards" :key="c.label" class="stat-card">
        <div class="stat-icon" :class="c.color" v-html="svgIcon(c.icon)" />
        <div>
          <div class="stat-value">{{ c.value }}</div>
          <div class="stat-label">{{ c.label }}</div>
          <div v-if="c.sub" class="stat-sub">{{ c.sub }}</div>
        </div>
      </div>
    </div>

    <!-- ===== 管理员端：教务数据大屏（深色风格） ===== -->
    <template v-if="isAdmin">
      <section class="ds-screen" ref="dsRef">
        <div class="ds-head">
          <div class="ds-title"><span class="ds-logo" v-html="svgIcon('sparkles')" />{{ t('智学选课 · 教务数据大屏') }}</div>
          <div class="ds-head-right">
            <span class="ds-pill">{{ dsSemName }} · {{ t('第 {n} 轮', { n: dsOv.round || systemConfig.currentRound || 1 }) }} · {{ systemConfig.roundOpen ? t('选课进行中') : t('选课未开放') }}</span>
            <button class="ds-fs-btn" @click="toggleDsScreen">{{ dsBig ? t('退出全屏') : t('全屏') }}</button>
          </div>
        </div>

        <div class="ds-kpis">
          <div v-for="k in dsKpis" :key="k.label" class="ds-kpi">
            <div class="ds-kpi-num" :style="{ color: k.color }">{{ k.value }}<small>{{ k.unit }}</small></div>
            <div class="ds-kpi-label">{{ k.label }}</div>
            <div class="ds-kpi-sub">{{ k.sub }}</div>
            <svg class="ds-spark" viewBox="0 0 120 30" preserveAspectRatio="none">
              <polyline :points="k.spark" fill="none" :stroke="k.color" stroke-width="2" stroke-linecap="round" opacity=".8" />
            </svg>
          </div>
        </div>

        <div class="ds-grid">
          <div class="ds-panel">
            <div class="ds-p-title">{{ t('选课热度') }} <small>{{ t('本轮非必修 · TOP6 · 悬停查看完整课程名') }}</small></div>
            <div v-if="dsBars.length" class="ds-bars">
              <div v-for="b in dsBars" :key="b.full" class="ds-bar-row" :title="b.full">
                <span class="ds-bar-name">{{ b.name }}</span>
                <div class="ds-bar-track"><i class="ds-bar" :style="{ width: b.h + '%' }" /></div>
                <span class="ds-bar-val">{{ b.val }}</span>
              </div>
            </div>
            <div v-else class="ds-empty">{{ t('暂无报名数据') }}</div>
          </div>

          <div class="ds-panel">
            <div class="ds-p-title">{{ t('三轮报名占比') }}</div>
            <div v-if="dsDonutRows.length" class="ds-chart-box"><EChart :option="dsDonut" fill /></div>
            <div v-else class="ds-empty">{{ t('暂无轮次数据') }}</div>
          </div>

          <div class="ds-panel">
            <div class="ds-p-title">{{ t('近30分钟操作趋势') }} <small>{{ t('每 30 秒刷新') }}</small></div>
            <div v-if="dsTimeline.length" class="ds-chart-box"><EChart :option="dsTrend" fill /></div>
            <div v-else class="ds-empty">{{ t('暂无操作数据') }}</div>
          </div>
        </div>

        <div class="ds-panel ds-flow">
          <div class="ds-flow-head">
            <div class="ds-p-title" style="margin:0">{{ t('选课流程 · 名额流动') }} <small>{{ t('节点下标注所属轮次') }}</small></div>
            <div class="ds-flow-legend">
              <span v-for="(s, i) in dsSteps" :key="s.name"><i :style="{ background: s.color }" />{{ s.name }}</span>
            </div>
          </div>
          <div class="ds-steps">
            <template v-for="(s, i) in dsSteps" :key="s.name">
              <div class="ds-step" :class="{ on: i <= flowStage }">
                <div class="ds-dot" :class="{ on: i <= flowStage }" :style="i <= flowStage ? { background: s.color, boxShadow: `0 0 14px ${s.color}88` } : {}">{{ i + 1 }}</div>
                <div class="ds-step-name">{{ s.name }}</div>
                <div class="ds-step-round">{{ s.round }}</div>
              </div>
              <div v-if="i < dsSteps.length - 1" class="ds-link" :class="{ on: i < flowStage }"><i /></div>
            </template>
          </div>
        </div>
      </section>
    </template>

    <!-- ===== 学生端：个人数据驾驶舱 ===== -->
    <div v-if="isStudent" ref="cockpitRef" class="cockpit" :class="{ 'bs-mode': bigScreen }">
      <!-- 大屏顶栏 -->
      <div class="cp-head">
        <div class="cp-title">
          <span class="cp-logo" v-html="svgIcon('sparkles')" />
          <b>{{ bigScreen ? t('个人数据大屏') : t('我的数据驾驶舱') }}</b>
          <span class="cp-sem-chip" v-if="gradSummary?.currentSemesterLabel" :title="t('按入学年级与当前日期推算的学籍学期')">
            <span class="icon" v-html="svgIcon('calendar')" />{{ gradSummary.currentSemesterLabel }}
          </span>
          <span class="cp-round-chip" v-if="systemConfig.currentRound">{{ t('第 {n} 轮', { n: systemConfig.currentRound }) }} · {{ systemConfig.roundOpen ? t('选课进行中') : t('选课已关闭') }}</span>
        </div>
        <div class="cp-head-side">
          <span v-if="bigScreen" class="cp-clock">{{ nowText }}</span>
          <button v-if="!bigScreen" class="cp-primary-btn" @click="$router.push('/courses')">
            <span class="cp-btn-ico" v-html="svgIcon('grid')" />{{ t('去选课') }}
          </button>
          <button class="cp-screen-btn" @click="toggleBigScreen">
            {{ bigScreen ? t('退出大屏') : t('进入大屏模式') }}
          </button>
        </div>
      </div>

      <!-- KPI 指标条 -->
      <div class="cp-kpis">
        <div v-for="k in kpis" :key="k.label" class="cp-kpi" :style="{ '--kc': k.color }">
          <span class="cp-kpi-ico" v-html="svgIcon(k.icon)" />
          <div class="cp-kpi-body">
            <div class="cp-kpi-num">{{ k.value }}<small v-if="k.unit">{{ k.unit }}</small></div>
            <div class="cp-kpi-label">{{ k.label }}</div>
            <div v-if="k.sub" class="cp-kpi-sub">{{ k.sub }}</div>
            <div v-if="k.bar" class="cp-kpi-bar"><i :style="{ width: k.bar.pct + '%' }" /></div>
          </div>
        </div>
      </div>

      <!-- 主体：左列大图表 / 右列指标栈（稀疏面板只在数据充足或大屏模式出现，首页保持干净） -->
      <div class="cp-grid">
        <div class="cp-col cp-col-main">
          <!-- 今日课程 + 学习负载 -->
          <section class="cp-panel cp-today">
            <div class="cp-p-title">{{ t('今日课程') }} <small>{{ weekLabel }}</small></div>
            <div v-if="todayCourses.length" class="cp-today-list" :class="{ sparse: todayCourses.length <= 2 }">
              <div v-for="tc in todayCourses" :key="tc.name + tc.from" class="cp-today-row" :class="{ now: tc.ongoing, past: tc.done, pend: tc.pending }">
                <span class="cp-today-time">{{ tc.from }} – {{ fmtMin(tc.to) }}</span>
                <div class="cp-today-body">
                  <b>{{ tc.name }}</b>
                  <span>{{ tc.room }}<template v-if="tc.teacherName"> · 👨‍🏫 {{ tc.teacherName }}</template><template v-if="tc.credit"> · {{ tc.credit }} {{ t('学分') }}</template></span>
                </div>
                <em v-if="tc.pending" class="cp-wait">{{ t('待录取') }}</em>
                <em v-else-if="tc.ongoing" class="cp-live">{{ t('进行中') }}</em>
                <em v-else-if="tc.done" class="cp-done">{{ t('已结束') }}</em>
                <em v-else-if="tc.name + tc.from === nextKey" class="cp-next">{{ t('{n} 分钟后', { n: todayMeta.nextIn }) }}</em>
              </div>
              <!-- 今日时间轴：首课到末课的时间带 + 当前时刻红线 -->
              <div class="cp-today-tl">
                <div class="cp-tl-bar">
                  <span v-for="(s, si) in todayTimeline.segs" :key="si" class="cp-tl-seg" :class="{ on: s.on, past: s.past, pend: s.pend }" :style="{ left: s.left + '%', width: s.width + '%' }"></span>
                  <span class="cp-tl-now" :style="{ left: todayTimeline.nowPct + '%' }"></span>
                </div>
                <div class="cp-tl-labels"><span>{{ fmtMin(todayTimeline.first) }}</span><span>{{ fmtMin(todayTimeline.last) }}</span></div>
              </div>
              <div class="cp-today-summary">
                <span>{{ t('今日共 {n} 节', { n: todayMeta.total }) }}</span>
                <template v-if="todayMeta.pending"> · <span class="cp-ts-wait">{{ t('待录取 {n} 节', { n: todayMeta.pending }) }}</span></template>
                <template v-if="todayMeta.done"> · <span>{{ t('已结束 {n} 节', { n: todayMeta.done }) }}</span></template>
                <template v-if="todayMeta.nextIn != null"> · <span class="cp-ts-next">{{ t('距下一节 {n} 分钟', { n: todayMeta.nextIn }) }}</span></template>
                <template v-else-if="!todayMeta.pending"> · <span>{{ t('今日课程已全部结束') }}</span></template>
              </div>
            </div>
            <div v-else class="cp-nodata">{{ t('今日无课 · 自由日 🎉') }}</div>
          </section>
          <!-- 轮次趋势 -->
          <section class="cp-panel cp-trend" :class="{ 'cp-trend-compact': !trendMeaningful }">
            <div class="cp-p-title">{{ trendMeaningful ? t('选课轮次趋势') : t('本轮选课概览') }} <small>{{ trendMeaningful ? t('每轮报了几门课、投了多少分') : t('本轮报名与积分池使用') }}</small></div>
            <div v-if="trendMeaningful" class="cp-chart-fill"><EChart :option="trendOption" fill /></div>
            <div v-else-if="activeRound" class="cp-trend-summary">
              <div class="cts-item"><b>{{ t('第 {n} 轮', { n: activeRound.round }) }}</b><span>{{ t('当前轮次') }}</span></div>
              <div class="cts-item"><b>{{ t('{n} 门', { n: activeRound.count }) }}</b><span>{{ t('已报名课程') }}</span></div>
              <div class="cts-item"><b>{{ t('{n} 分', { n: activeRound.invested }) }}</b><span>{{ t('累计投入') }}</span></div>
              <div class="cts-item"><b>{{ t('{n} 分', { n: activeRound.remaining + ' / ' + activeRound.pool }) }}</b><span>{{ t('积分池余额') }}</span></div>
            </div>
            <div v-else class="cp-nodata">{{ t('还没有报名记录') }}</div>
          </section>

          <!-- 课程竞争气泡：意向稀少时图表空旷，收进大屏模式 -->
          <section v-if="bigScreen || bubbleLayout.length >= 3" class="cp-panel cp-bubble">
            <div class="cp-p-title">{{ t('课程竞争气泡') }} <small>{{ t('每个泡 = 意向中（待录取）的一门课 · 越靠右投分越高 · 越靠上竞争越激烈') }}</small></div>
            <div class="cp-chart-fill cp-bubble-fill">
              <svg viewBox="0 0 560 250" class="di-bubble" role="img" :aria-label="t('课程竞争气泡图')">
                <line v-for="t in [0, 10, 20, 30, 40, 50]" :key="'bx' + t" :x1="bx(t)" :x2="bx(t)" :y1="16" :y2="250 - 30" class="di-b-grid" />
                <text v-for="t in [0, 10, 20, 30, 40, 50]" :key="'bxt' + t" :x="bx(t)" :y="250 - 12" text-anchor="middle" class="di-b-axis">{{ t }}</text>
                <line v-for="t in yTicks" :key="'by' + t" :x1="36" :x2="560 - 14" :y1="by(t)" :y2="by(t)" class="di-b-grid" />
                <text v-for="t in yTicks" :key="'byt' + t" :x="30" :y="by(t) + 3" text-anchor="end" class="di-b-axis">{{ t }}%</text>
                <text :x="560 - 14" :y="250 - 12" text-anchor="end" class="di-b-axis">{{ t('我的投分 →') }}</text>
                <text x="36" y="12" class="di-b-axis">{{ t('课程热度 ↑') }}</text>
                <g v-for="o in bubbleLayout" :key="o.b.id" class="di-bubble-g" :class="o.b.status">
                  <circle :cx="bx(o.x)" :cy="by(o.y)" :r="o.r" class="di-bubble" />
                  <text v-if="o.label" :x="o.lx" :y="o.ly" text-anchor="middle" class="di-b-name">{{ o.b.name.slice(0, 6) }}</text>
                  <title>{{ o.b.name }} · {{ t('投分 {n}', { n: o.b.points }) }} · {{ t('热度 {n}%', { n: o.b.heat }) }} · {{ t('{n} 学分', { n: o.b.credit }) }} · {{ o.b.status === 'approved' ? t('已录取') : t('待录取') }}</title>
                </g>
              </svg>
            </div>
          </section>

        </div>

        <div class="cp-col cp-col-side">
          <!-- 毕业进度环 -->
          <section class="cp-panel cp-ring">
            <div class="cp-p-title">{{ t('毕业进度') }} <small>{{ t('仅计已通过课程') }}</small></div>
            <div class="cp-ring-box">
              <!-- 毕业要求总学分为 0 / 未配置时不显示百分比，避免误导性的 0%「已达标」 -->
              <div v-if="gradSummary && !gradConfigured" class="cp-ring-empty">
                <b>{{ t('暂未配置毕业要求') }}</b>
                <span>{{ t('已获得 {n} 学分', { n: gradSummary?.total?.earned ?? 0 }) }}</span>
              </div>
              <template v-else>
                <svg viewBox="0 0 140 140" role="img" :aria-label="t('毕业进度环')">
                  <defs>
                    <linearGradient id="cpRingGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                      <stop offset="0%" stop-color="#2bb187" />
                      <stop offset="100%" stop-color="#22d3ee" />
                    </linearGradient>
                  </defs>
                  <g transform="rotate(-90 70 70)">
                    <circle cx="70" cy="70" r="58" class="cp-ring-track" />
                    <circle cx="70" cy="70" r="58" class="cp-ring-fill" stroke="url(#cpRingGrad)"
                      :stroke-dasharray="`${gradPct / 100 * RING_C} ${RING_C}`" />
                  </g>
                </svg>
                <div class="cp-ring-center">
                  <b>{{ gradPct }}<small>%</small></b>
                  <span>{{ t('已获得 {n} 学分', { n: (gradSummary?.total?.earned ?? 0) + ' / ' + (gradSummary?.total?.required ?? 0) }) }}</span>
                  <em :class="{ ok: !gradSummary?.total?.gap }">{{ gradSummary?.total?.gap ? t('距离目标还差 {n} 学分', { n: gradSummary.total.gap }) : t('已达标') }}</em>
                </div>
              </template>
            </div>
          </section>

          <!-- 学分结构雷达（信息与进度环重叠，收进大屏模式） -->
          <section v-if="bigScreen" class="cp-panel cp-radar">
            <div class="cp-p-title">{{ t('学分结构') }} <small>{{ t('已获 / 要求') }}</small></div>
            <div class="cp-chart-fill"><EChart :option="radarOption" fill /></div>
          </section>


          <!-- 待办 + 已录取 -->
          <section class="cp-panel cp-todo">
            <div class="cp-p-title">{{ t('待办') }} <small v-if="studentPending.length">{{ t('{n} 门待录取', { n: studentPending.length }) }}</small></div>
            <div v-if="studentPending.length" class="cp-todo-list">
              <div v-for="e in studentPending.slice(0, 4)" :key="e.courseId" class="cp-todo-row">
                <b>{{ e.courseName }}</b>
                <span>{{ t('投入 {n} 分', { n: e.points ?? 0 }) }}</span>
              </div>
            </div>
            <div v-else class="cp-todo-ok">✓ {{ t('暂无待办，一切就绪') }}</div>
            <div class="cp-p-title" style="margin-top:12px">{{ t('已录取课程') }} <small>{{ t('{n} 门', { n: studentApproved.length }) }}</small></div>
            <div class="cp-chips">
              <span v-for="c in studentApproved" :key="c.courseId" class="cp-chip">{{ c.courseName }}</span>
              <span v-if="!studentApproved.length" class="cp-nodata">{{ t('还没有录取课程') }}</span>
            </div>
            <div v-if="loadStats.exams.length" class="cp-exams">
              <div class="cp-p-title" style="margin-top:12px">{{ t('考试安排') }}</div>
              <div v-for="x in loadStats.exams" :key="x.name" class="cp-todo-row"><b>{{ x.name }}</b><span>{{ x.time }}</span></div>
            </div>
          </section>
        </div>
      </div>
    </div>

    <!-- ===== 教师端：我教授的课程（首页只列需关注的课程，完整列表见"我的课程"页） ===== -->
    <div v-if="isTeacher" class="section">
      <div class="section-header">
        <div class="section-title">
          <span v-html="svgIcon('book-open')" />{{ t('我教授的课程') }}
          <span class="sec-sub">{{ t('共 {n} 门 · 超额 {m} 门', { n: teacherCourses.length, m: teacherOverCount }) }}</span>
        </div>
        <div class="section-link" @click="$router.push('/teach-courses')">{{ t('全部课程 →') }}</div>
      </div>
      <div v-if="teacherFocusCourses.length" class="course-grid course-grid-flat course-grid-3">
        <div v-for="c in teacherFocusCourses" :key="c.id" class="course-card" :class="{ 'course-req': (c.category || '').includes('必修') }">
          <span class="course-cat" :class="'cat-' + (c.category || '其他')">{{ c.category || t('其他') }}</span>
          <div class="course-name">{{ c.name }}</div>
          <div class="course-meta">
            <span><span v-html="svgIcon('clock')" /> {{ c.time || t('时间待安排') }}</span>
            <span><span v-html="svgIcon('map-pin')" /> {{ c.location || t('教室待定') }}</span>
          </div>
          <div class="course-footer">
            <div style="flex:1;display:flex;align-items:center;gap:8px;">
              <div class="progress-bar"><div class="progress-fill" :style="{ width: capPercent(c) + '%' }" /></div>
              <span class="enroll-count">{{ c.stat?.total || 0 }}/{{ c.capacity }}</span>
            </div>
            <span v-if="c.oversubscribed" class="status-tag status-pending" :title="t('报名人数超过容量，截止后统一按积分结算')">{{ t('报名超额') }}</span>
            <span v-else class="status-tag status-approved">{{ t('正常') }}</span>
          </div>
        </div>
      </div>
      <div v-else class="empty-state">
        <div class="empty-icon" v-html="svgIcon('book-open')" />
        <p>{{ t('暂无教授课程') }}</p>
      </div>
    </div>

    <!-- ===== 教师端：教务通知（真实数据） ===== -->
    <div v-if="isTeacher" class="section">
      <div class="section-header">
        <div class="section-title"><span v-html="svgIcon('megaphone')" />{{ t('教务通知') }}</div>
        <div class="section-link" @click="$router.push('/announcements')">{{ t('公告板 →') }}</div>
      </div>
      <div v-if="notices.length" class="notice-list">
        <div v-for="a in notices" :key="a.id" class="notice-row" @click="$router.push('/announcements')">
          <b>{{ a.title }}</b>
          <span>{{ fmtDay(a.createdAt) }}</span>
        </div>
      </div>
      <div v-else class="empty-state"><p>{{ t('暂无公告') }}</p></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount, onUnmounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import http from '@/api'
import EChart from '@/components/charts/EChart.vue'
import ArkHud from '@/components/ArkHud.vue'
import { PALETTE, TOOLTIP } from '@/utils/echarts'
import { useCountUp } from '@/utils/useCountUp'
import { t } from '@/i18n'

const auth = useAuthStore()
const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`

const systemConfig = reactive<Record<string, any>>({})
const role = computed(() => auth.user?.role || 'student')
const isStudent = computed(() => role.value === 'student')
const isTeacher = computed(() => role.value === 'teacher')
const isAdmin = computed(() => role.value === 'admin')

const roleTactical = computed(() => isStudent.value ? t('同学') : isTeacher.value ? t('老师') : t('管理员'))
const briefCode = computed(() => isStudent.value ? 'STD' : isTeacher.value ? 'TCH' : 'ADM')
const roleTip = computed(() =>
  isStudent.value ? t('本学期课程已就绪，可随时发起选课') :
  isTeacher.value ? t('授课安排已同步，等待下一轮选课') :
  t('星图数据链路正常，管理权限已确认')
)

// ===== API 数据 =====
const remaining = ref(0)
const POINTS_PER_ROUND = 50

const studentApproved = ref<any[]>([])
const studentPending = ref<any[]>([])
// 已获学分：统一取毕业进度接口（有效成绩 + 已录取 + 模块上限），接口异常时回退本地统计
const studentCredits = ref(0)
// 已选学分：已录取课程的全部学分（含未通过/未出成绩）
const studentCreditsEnrolled = ref(0)

const teacherCourses = ref<any[]>([])

const adminUsers = ref<any[]>([])
const adminCourses = ref<any[]>([])
const recentLogs = ref<any[]>([])

// ===== 教务数据大屏 =====
const dsOv = ref<any>({})
const dsHot = ref<any[]>([])
const dsRounds = ref<any[]>([])
const dsTimeline = ref<any[]>([])
let dsTimer: any = null

const fmtNum = (n: any) => (Number(n) || 0).toLocaleString('en-US')
const dsSemName = computed(() => {
  const d = new Date()
  const year = d.getMonth() >= 7 ? d.getFullYear() : d.getFullYear() - 1
  return t('{n}秋季选课周期', { n: year })
})

/* ===== 主题联动：data-theme 切换时自增，驱动图表文字/配色跟随深浅色 ===== */
const themeTick = ref(0)
let themeObs: MutationObserver | null = null
const isDarkChart = computed(() => {
  void themeTick.value
  return document.documentElement.getAttribute('data-theme') === 'dark'
})

/* 大屏提示框：白底柔和阴影（浅色），深色主题沿用暗色皮肤 */
const LIGHT_TOOLTIP = {
  backgroundColor: 'rgba(255,255,255,.96)',
  borderColor: '#DCEFEA',
  borderWidth: 1,
  textStyle: { color: '#243B40', fontSize: 12 },
  extraCssText: 'border-radius: 10px; box-shadow: 0 8px 24px rgba(38,173,145,.18);',
}

/* 数字滚动：管理员大屏 KPI（数据到达/30 秒轮询更新时滚动） */
const animApplied = useCountUp(() => Number(dsOv.value.applied) || 0, { format: fmtNum })
const animFillRate = useCountUp(() => Number(dsOv.value.fillRate) || 0, { decimals: 1 })
const animWaiting = useCountUp(() => Number(dsOv.value.waiting) || 0, { format: fmtNum })

const dsKpis = computed(() => {
  const c1 = isDarkChart.value ? '#3cc39a' : '#26AD91'
  const c2 = isDarkChart.value ? '#f59e0b' : '#16866F'
  const c3 = '#38BDF8'
  return [
  { label: t('选课人次（本轮选修）'), sub: t('当前轮次非必修报名，不含退课'), value: animApplied.value, unit: '', color: c1,
    spark: '0,22 12,18 24,20 36,13 48,16 60,10 72,14 84,7 96,11 108,5 120,8' },
  { label: t('课程平均满员率'), sub: t('本轮已录取人数 ÷ 课程容量'), value: animFillRate.value, unit: '%', color: c2,
    spark: '0,24 12,20 24,22 36,17 48,19 60,12 72,16 84,10 96,13 108,7 120,10' },
  { label: t('候补总人数'), sub: t('当前轮次各课程候补队列合计'), value: animWaiting.value, unit: '', color: c3,
    spark: '0,20 12,23 24,17 36,21 48,14 60,18 72,11 84,15 96,9 108,12 120,6' },
  ]
})

/* TOP6 热度条（横向，课程名完整展示，超长省略 + 悬停提示） */
const dsBars = computed(() => {
  const rows = [...dsHot.value].filter((x: any) => !x.required).slice(0, 6)
  const max = Math.max(1, ...rows.map((r: any) => r.applied || 0))
  return rows.map((r: any) => ({
    name: r.courseName || '',
    full: r.courseName,
    val: fmtNum(r.applied),
    h: Math.max(8, Math.round((r.applied || 0) / max * 100)),
  }))
})

/* 三轮报名占比环图 */
const dsDonutRows = computed(() => dsRounds.value.filter((r: any) => (r.applied || 0) > 0))
/* 大屏统一配色：浅色 = 主色青绿 / 深青绿 / 辅助天蓝；深色主题保留原亮色系 */
const dsDonut = computed(() => {
  const rows = dsDonutRows.value
  const total = rows.reduce((s: number, r: any) => s + (r.applied || 0), 0) || 1
  const DS_COLORS = isDarkChart.value ? ['#3cc39a', '#38bdf8', '#f59e0b'] : ['#26AD91', '#38BDF8', '#16866F']
  return {
    tooltip: { trigger: 'item', ...(isDarkChart.value ? TOOLTIP : LIGHT_TOOLTIP) },
    legend: {
      orient: 'vertical', right: 4, top: 'center', itemWidth: 10, itemHeight: 10, itemGap: 12,
      textStyle: { color: isDarkChart.value ? '#aecbe8' : '#7B9294', fontSize: 12 },
      formatter: (name: string) => {
        const r = rows.find((x: any) => `第${x.round}轮` === name)
        return `${name}  ${Math.round((r?.applied || 0) * 100 / total)}%`
      },
    },
    title: {
      text: t('{n}轮', { n: rows.length }), subtext: t('报名轮次'), left: '30%', top: '36%', textAlign: 'center',
      textStyle: { color: isDarkChart.value ? '#e8f2ff' : '#243B40', fontSize: 21, fontWeight: 700 },
      subtextStyle: { color: isDarkChart.value ? '#a9c1e0' : '#7B9294', fontSize: 12 },
    },
    series: [{
      type: 'pie', radius: ['56%', '76%'], center: ['32%', '50%'], label: { show: false },
      data: rows.map((r: any, i: number) => ({ name: `第${r.round}轮`, value: r.applied, itemStyle: { color: DS_COLORS[i % 3] } })),
    }],
  }
})

/* 近30分钟操作趋势折线（数据源：/stats/live，operation_log 真实日志逐分钟聚合） */
const dsTrend = computed(() => {
  const axisText = isDarkChart.value ? '#a9c1e0' : '#7B9294'
  return {
  grid: { left: 34, right: 12, top: 14, bottom: 24 },
  tooltip: { trigger: 'axis', ...(isDarkChart.value ? TOOLTIP : LIGHT_TOOLTIP) },
  xAxis: {
    type: 'category', boundaryGap: false, data: dsTimeline.value.map((p: any) => p.minute),
    axisLabel: { color: axisText, fontSize: 12, interval: 4 },
    axisLine: { lineStyle: { color: isDarkChart.value ? 'rgba(90,120,170,.35)' : '#DCEFEA' } },
  },
  yAxis: { type: 'value', splitLine: { lineStyle: { color: isDarkChart.value ? 'rgba(90,120,170,.16)' : 'rgba(123,146,148,.16)' } }, axisLabel: { color: axisText, fontSize: 12 } },
  series: [{
    type: 'line', smooth: true, data: dsTimeline.value.map((p: any) => p.ops),
    symbol: 'circle', symbolSize: 5, lineStyle: { color: '#38bdf8', width: 2 }, itemStyle: { color: '#38bdf8' },
    areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [
      { offset: 0, color: isDarkChart.value ? 'rgba(56,189,248,.26)' : 'rgba(56,189,248,.18)' }, { offset: 1, color: 'rgba(56,189,248,0)' },
    ] } },
  }],
  }
})

/* 选课流程（原"三轮录取·名额流动"）：节点下标注所属轮次（橙仅用于"统一结算"特殊状态） */
const dsSteps = computed(() => [
  { name: t('积分报名'), round: t('第 1 / 2 轮'), color: '#26AD91' },
  { name: t('统一结算'), round: t('第 1 / 2 轮末'), color: '#f59e0b' },
  { name: t('递补拼接'), round: t('结算后'), color: '#7B9294' },
  { name: t('先到先得'), round: t('第 3 轮'), color: '#38BDF8' },
  { name: t('候补递补'), round: t('全程'), color: '#5CD4B2' },
])
const flowStage = computed(() => {
  const r = Number(dsOv.value.round || systemConfig.currentRound || 1)
  return r >= 3 ? 4 : r >= 2 ? 2 : 0
})

// ===== home-hero =====

// ===== 教师端：教务通知（真实数据） =====
const notices = ref<any[]>([])
const fmtDay = (s: string) => { const d = new Date(s); return isNaN(+d) ? '' : `${d.getMonth() + 1}-${d.getDate()}` }
async function loadNotices() {
  try { const r: any = await http.get('/announcements?all=1'); notices.value = (r?.data || []).slice(0, 3) } catch {}
}

// ===== 教师 / 管理员 KPI 卡（数字滚动） =====
const animTeacherCourses = useCountUp(() => teacherCourses.value.length)
const animTeacherEnrolled = useCountUp(() => teacherCourses.value.reduce((s: number, c: any) => s + (c.stat?.total || 0), 0))
const animTeacherOver = useCountUp(() => teacherCourses.value.filter((c: any) => c.oversubscribed).length)
const statCards = computed(() => {
  if (isTeacher.value) {
    return [
      { icon: 'book-open', color: 'blue',  value: animTeacherCourses.value, label: t('我教授的课程') },
      { icon: 'users',     color: 'green', value: animTeacherEnrolled.value, label: t('总报名人数') },
      { icon: 'hourglass', color: 'amber', value: animTeacherOver.value, label: t('报名超额课程') },
    ]
  }
  return [
    { icon: 'users',  color: 'blue',   value: animAdminStudents.value, label: t('学生总数'), sub: t('全校学生账号数') },
    { icon: 'user',   color: 'green',  value: animAdminTeachers.value, label: t('教师总数'), sub: t('全校教师账号数') },
    { icon: 'book',   color: 'purple', value: animAdminCourses.value, label: t('课程总数'), sub: t('含必修与选修') },
  ]
})

/** 教师首页只列需关注的课程：超额优先，其后按报名率降序，最多 6 门 */
const teacherOverCount = computed(() => teacherCourses.value.filter((c: any) => c.oversubscribed).length)
const teacherFocusCourses = computed(() => {
  const sorted = [...teacherCourses.value].sort((a: any, b: any) =>
    ((b.oversubscribed ? 1 : 0) - (a.oversubscribed ? 1 : 0)) ||
    ((b.stat?.total || 0) / (b.capacity || 1)) - ((a.stat?.total || 0) / (a.capacity || 1)))
  return sorted.slice(0, 6)
})

const adminStudentCount = computed(() => adminUsers.value.filter((u: any) => u.role === 'student').length)
const adminTeacherCount = computed(() => adminUsers.value.filter((u: any) => u.role === 'teacher').length)
/* 管理员统计卡滚动（需在上方 computed 之后声明：immediate watch 同步读初值） */
const animAdminStudents = useCountUp(() => adminStudentCount.value)
const animAdminTeachers = useCountUp(() => adminTeacherCount.value)
const animAdminCourses = useCountUp(() => adminCourses.value.length)
const adminTotalEnrolled = computed(() => adminCourses.value.reduce((s: number, c: any) => s + (c.stat?.total || 0), 0))
const adminOpenCourses = computed(() => adminCourses.value.length)

// ===== 驾驶舱数据 =====
const studentEnrollments = ref<any[]>([])
const coursesAll = ref<any[]>([])
const gradSummary = ref<any>(null)
const pointTotal = ref(50)
const courseById = computed(() => {
  const m: Record<number, any> = {}
  for (const c of coursesAll.value) m[c.id] = c
  return m
})

/* 选修口径：排除公共必修/专业必修（必修由系统自动排课，不计入选课统计） */
const isElective = (e: any) => {
  const c = courseById.value[e.courseId]
  return !!c && !(c.category || '').includes('必修')
}
const studentApprovedElective = computed(() => studentApproved.value.filter(isElective))
const studentPendingElective = computed(() => studentPending.value.filter(isElective))

const kpis = computed(() => [
  { icon: 'coins',     color: '#f59e0b', value: animRemaining.value, unit: `/${pointTotal.value}`, label: t('剩余积分') },
    // 选修口径：必修课由系统自动排课，不计入选课统计
    { icon: 'check-circle', color: '#34d399', value: animApprovedElective.value, unit: t('门'), label: t('已录取选修课'),
      sub: studentPendingElective.value.length ? t('另有 {n} 门待录取', { n: studentPendingElective.value.length }) : '' },
    { icon: 'star',      color: '#0ea5e9', value: animCredits.value, unit: t('分'), label: t('已获学分') },
    // 学分缺口：按选修口径展示（毕业进度环已在右侧卡片展示总进度）
    { icon: 'target',    color: '#2bb187', value: gradConfigured.value ? animElectiveGap.value : '—', unit: gradConfigured.value ? t('分') : '', label: t('学分缺口'),
      sub: gapSub.value, bar: gradConfigured.value ? { pct: electivePct.value } : undefined },
    { icon: 'clock',     color: '#22d3ee', value: animSessions.value, unit: t('节'), label: t('每周课时'),
      sub: t('上课 {n} 天', { n: loadStats.value.days }) },
])

const fmtMin = (v: number) => `${String(Math.floor(v / 60)).padStart(2, '0')}:${String(v % 60).padStart(2, '0')}`
const gradPct = computed(() => Math.min(100, Math.max(0, Number(gradSummary.value?.total?.percent) || 0)))
/* 毕业要求是否已配置（总学分 > 0）；未配置时进度环降级为提示文案 */
const gradConfigured = computed(() => Number(gradSummary.value?.total?.required) > 0)
/* 选修学分缺口：毕业要求中「选修」模块的差额（必修由系统自动排课，无需学生操心） */
const electiveCat = computed(() => {
  const cats: any[] = gradSummary.value?.categories || []
  return cats.find(x => x.key === '选修') || null
})
const electiveGap = computed(() => Math.max(0, Number(electiveCat.value?.gap) || 0))
const electivePct = computed(() => {
  const req = Number(electiveCat.value?.required) || 0
  const got = Number(electiveCat.value?.earned) || 0
  return req <= 0 ? 0 : Math.min(100, Math.round(got * 100 / req))
})
/* 行动建议：按学分从大到小贪心，估算再修几门推荐选修课能修满（推荐表不含足够学分时退回分数口径） */
const electiveCoursesNeeded = computed(() => {
  if (electiveGap.value <= 0) return null
  const rec: any[] = (gradSummary.value?.recommend || []).filter((r: any) => r.group === '选修' && Number(r.credit) > 0)
  rec.sort((a, b) => Number(b.credit) - Number(a.credit))
  let sum = 0, n = 0
  for (const r of rec) { sum += Number(r.credit); n++; if (sum >= electiveGap.value) return n }
  return null
})
/* 选修建满后的下一步：优先提示通识/实践等仍可选课补足的模块缺口 */
const otherGapCat = computed(() => {
  const cats: any[] = gradSummary.value?.categories || []
  return cats.find(x => x.key !== '选修' && x.key !== '必修' && Number(x.gap) > 0) || null
})
const gapSub = computed(() => {
  if (!gradConfigured.value) return t('暂未配置毕业要求')
  if (electiveGap.value > 0) {
    const n = electiveCoursesNeeded.value
    return n ? t('再修 {n} 门选修课即可修满', { n }) : t('选修还差 {n} 分', { n: electiveGap.value })
  }
  const other = otherGapCat.value
  if (other) return t('选修已修满 · {m}还差 {n} 分', { m: t(other.key), n: other.gap })
  return t('已满足毕业要求')
})
const RING_C = 2 * Math.PI * 58

/* 轮次趋势：固定展示全部三轮，无数据的轮次计 0 */
const ROUND_COUNT = 3
const roundRows = computed(() => {
  const map = new Map<number, { round: number; count: number; invested: number }>()
  for (const e of studentEnrollments.value) {
    if (e.status === 'dropped' || e.round == null) continue
    const r = map.get(e.round) || { round: e.round, count: 0, invested: 0 }
    r.count++
    r.invested += Number(e.points) || 0
    map.set(e.round, r)
  }
  const pool = Number(pointTotal.value) || 50
  return Array.from({ length: ROUND_COUNT }, (_, i) => {
    const r = map.get(i + 1) || { round: i + 1, count: 0, invested: 0 }
    return { ...r, round: i + 1, remaining: Math.max(0, pool - r.invested), pool }
  })
})
/* 单轮数据时趋势图降级为大数字摘要（有 ≥2 轮数据才出柱线图） */
const activeRound = computed(() => roundRows.value.find(r => r.count > 0) || null)
const trendMeaningful = computed(() => roundRows.value.filter(r => r.count > 0).length >= 2)
const trendOption = computed(() => ({
  grid: { left: 40, right: 40, top: 28, bottom: 28 },
  tooltip: { trigger: 'axis', ...TOOLTIP },
  legend: { data: [t('报名门数'), t('投入积分')], textStyle: { color: '#94a3b8', fontSize: 11 }, top: 0, right: 0, itemWidth: 12, itemHeight: 8 },
  xAxis: { type: 'category', data: roundRows.value.map(r => t('第{n}轮', { n: r.round })), axisLine: { lineStyle: { color: 'rgba(148,163,184,.35)' } }, axisTick: { show: false }, axisLabel: { color: '#94a3b8', fontSize: 11 } },
  yAxis: [
    { type: 'value', name: t('门'), max: Math.max(2, ...roundRows.value.map(r => r.count)), nameTextStyle: { color: '#94a3b8', fontSize: 10 }, splitLine: { lineStyle: { color: 'rgba(148,163,184,.14)' } }, axisLabel: { color: '#94a3b8', fontSize: 10 } },
    { type: 'value', name: t('分'), max: pointTotal.value, nameTextStyle: { color: '#94a3b8', fontSize: 10 }, splitLine: { show: false }, axisLabel: { color: '#94a3b8', fontSize: 10 } },
  ],
  series: [
    {
      name: t('报名门数'), type: 'bar', barWidth: '38%', barMaxWidth: 42,
      data: roundRows.value.map(r => r.count),
      itemStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [
        { offset: 0, color: 'rgba(43,177,135,.85)' }, { offset: 1, color: 'rgba(43,177,135,.25)' },
      ] }, borderRadius: [5, 5, 0, 0] },
    },
    {
      name: t('投入积分'), type: 'line', yAxisIndex: 1, smooth: true,
      data: roundRows.value.map(r => r.invested),
      lineStyle: { color: '#f59e0b', width: 2.5 },
      itemStyle: { color: '#f59e0b' }, symbolSize: 7,
      areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [
        { offset: 0, color: 'rgba(245,158,11,.25)' }, { offset: 1, color: 'rgba(245,158,11,0)' },
      ] } },
    },
  ],
}))

/* 学分结构雷达（ECharts） */
const radarOption = computed(() => {
  const cats: any[] = gradSummary.value?.categories || []
  return {
    tooltip: { trigger: 'item', ...TOOLTIP },
    radar: {
      indicator: cats.map(c => ({ name: c.key, max: 100 })),
      radius: '64%', center: ['50%', '54%'],
      axisName: { color: '#94a3b8', fontSize: 11 },
      splitLine: { lineStyle: { color: 'rgba(148,163,184,.22)' } },
      splitArea: { show: false },
      axisLine: { lineStyle: { color: 'rgba(148,163,184,.25)' } },
    },
    series: [{
      type: 'radar',
      data: [{
        value: cats.map(c => (c.required ? Math.min(100, Math.round(c.earned * 100 / c.required)) : 0)),
        name: t('完成度'),
        areaStyle: { color: 'rgba(43,177,135,.3)' },
        lineStyle: { color: '#4cc9a3', width: 2 },
        itemStyle: { color: '#2bb187' }, symbolSize: 5,
      }],
    }],
  }
})

/* 气泡：只展示意向中（已报名待录取）的课程 */
const bubbleRows = computed(() => {
  const rows = studentEnrollments.value.filter(e => e.status === 'pending')
  return rows.map(e => {
    const c = courseById.value[e.courseId]
    const cap = c?.capacity || 0
    const heat = cap > 0 ? Math.round(((c?.guide?.bidCount || 0) / cap) * 100) : 0
    return {
      id: e.enrollmentId, name: e.courseName, points: Number(e.points) || 0,
      heat, credit: e.credit || 0, status: e.status,
    }
  }).slice(0, 40)
})
const bubbleLayout = computed(() => {
  const groups = new Map<number, any[]>()
  for (const b of bubbleRows.value) {
    const arr = groups.get(b.points) || []
    arr.push(b)
    groups.set(b.points, arr)
  }
  const out: Array<{ b: any; x: number; y: number; r: number; lx: number; ly: number; label: boolean }> = []
  const obstacles: Array<{ x: number; y: number; w: number; h: number }> = []
  const hits = (x: number, y: number, w: number, h: number) =>
    obstacles.some(o => Math.abs(o.x - x) < (o.w + w) / 2 && Math.abs(o.y - y) < (o.h + h) / 2)
  for (const arr of groups.values()) {
    arr.forEach((b, i) => {
      const jitter = arr.length > 1 ? (i - (arr.length - 1) / 2) * 1.5 : 0
      const x = Math.max(0, Math.min(50, b.points + jitter))
      const r = 5 + b.credit * 1.6
      const cx = bx(x), cy = by(b.heat)
      // 标签中心限制在绘图区内（左 36 轴、右 14 边距，标签宽约 66），避免文字被截断
      const lx = Math.max(36 + 34, Math.min(560 - 14 - 34, cx))
      out.push({ b, x, y: b.heat, r, lx, ly: 0, label: false })
      obstacles.push({ x: cx, y: cy, w: r * 2 + 10, h: r * 2 })
      const w = 66, h = 13
      let ly = Math.max(12, cy - r - 7)
      let label = !hits(lx, ly, w, h)
      if (!label) {
        const below = cy + r + 12
        if (!hits(lx, below, w, h)) { ly = below; label = true }
      }
      if (label) {
        out[out.length - 1].ly = ly
        out[out.length - 1].label = true
        obstacles.push({ x: cx, y: ly, w, h })
      }
    })
  }
  return out
})
const bx = (v: number) => 36 + (Math.max(0, Math.min(50, v)) / 50) * (560 - 36 - 14)
const yMax = computed(() => Math.min(200, Math.max(20, ...bubbleRows.value.map(b => b.heat))))
const yTicks = computed(() => [0, Math.round(yMax.value / 2), yMax.value])
const by = (v: number) => 250 - 30 - (Math.max(0, Math.min(yMax.value, v)) / yMax.value) * (250 - 16 - 30)

/* 学习负载 / 今日课程 */
function parseSessions(time: string): number {
  if (!time) return 0
  return (time.match(/周[一二三四五六日]\s*\d{2}:\d{2}-\d{2}:\d{2}/g) || []).length
}
const loadStats = computed(() => {
  let sessions = 0, days = new Set<string>()
  const exams: Array<{ name: string; time: string }> = []
  for (const e of studentApproved.value) {
    const c = courseById.value[e.courseId]
    if (!c) continue
    sessions += parseSessions(c.time || '')
    const m = (c.time || '').match(/周[一二三四五六日]/g) || []
    m.forEach(d => days.add(d))
    if (c.examTime && String(c.examTime).trim()) exams.push({ name: c.courseName || e.courseName, time: String(c.examTime) })
  }
  return { sessions, days: days.size, exams: exams.slice(0, 4) }
})

/* 学生 KPI 数字滚动（依赖上方 electiveGap/loadStats 等声明） */
const animRemaining = useCountUp(() => remaining.value)
const animApprovedElective = useCountUp(() => studentApprovedElective.value.length)
const animCredits = useCountUp(() => studentCredits.value)
const animElectiveGap = useCountUp(() => electiveGap.value)
const animSessions = useCountUp(() => loadStats.value.sessions)

const WEEK_NAMES = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
// WEEK_NAMES 同时用于与后端课程时间串（周X）做逻辑匹配，此处仅翻译显示点
const weekLabel = computed(() => t(WEEK_NAMES[new Date().getDay()]))
/* 当前分钟数（30s 刷新）：驱动「进行中/已结束/距下一节」实时变化 */
const nowMin = ref(new Date().getHours() * 60 + new Date().getMinutes())
let nowTimer: any = null
onMounted(() => { nowTimer = setInterval(() => { const d = new Date(); nowMin.value = d.getHours() * 60 + d.getMinutes() }, 30000) })
onUnmounted(() => { if (nowTimer) clearInterval(nowTimer) })
const todayCourses = computed(() => {
  const today = WEEK_NAMES[new Date().getDay()]
  const cur = nowMin.value
  const rows: Array<{ name: string; from: string; start: number; to: number; room: string; teacherName: string; credit: number | null; ongoing: boolean; done: boolean; pending: boolean }> = []
  const seen = new Set<string>() // 去重：同一课程同一时段只显示一次（历史数据存在重复报名行）
  const collect = (list: any[], pending: boolean) => {
    const re = /(周[一二三四五六日])\s*(\d{2}:\d{2})-(\d{2}:\d{2})/g
    for (const e of list) {
      const c = courseById.value[e.courseId]
      if (!c?.time) continue
      let m
      while ((m = re.exec(c.time))) {
        if (m[1] !== today) continue
        const [fh, fm] = m[2].split(':').map(Number)
        const [th, tm] = m[3].split(':').map(Number)
        const start = fh * 60 + fm, end = th * 60 + tm
        const key = c.id + '-' + start
        if (seen.has(key)) continue
        seen.add(key)
        rows.push({
          name: c.name, from: m[2],
          start, to: end,
          room: c.location || t('教室待定'),
          teacherName: c.teacherName || '',
          credit: c.credit ?? null,
          ongoing: !pending && cur >= start && cur < end,
          done: !pending && cur >= end,
          pending,
        })
      }
    }
  }
  collect(studentApproved.value, false)
  collect(studentPending.value, true) // 待录取的课也进今日课表，用徽章区分
  return rows.sort((a, b) => a.from.localeCompare(b.from))
})
/* 今日概况：总数 / 已结束 / 待录取 / 下一节倒计时 */
const todayMeta = computed(() => {
  const rows = todayCourses.value
  const next = rows.find(r => nowMin.value < r.start) || null
  return {
    total: rows.length,
    done: rows.filter(r => r.done).length,
    pending: rows.filter(r => r.pending).length,
    nextIn: next ? next.start - nowMin.value : null,
    nextKey: next ? next.name + next.from : '',
  }
})
const nextKey = computed(() => todayMeta.value.nextKey)
/* 今日时间轴：首课→末课时间带，段落=每节课，红线=当前时刻 */
const todayTimeline = computed(() => {
  const rows = todayCourses.value
  if (!rows.length) return { first: 0, last: 0, segs: [] as Array<{ left: number; width: number; on: boolean; past: boolean }>, nowPct: 0 }
  const first = Math.min(...rows.map(r => r.start))
  const last = Math.max(...rows.map(r => r.to))
  const span = Math.max(1, last - first)
  const segs = rows.map(r => ({
    left: ((r.start - first) / span) * 100,
    width: Math.max(1.5, ((r.to - r.start) / span) * 100),
    on: r.ongoing, past: nowMin.value >= r.to, pend: r.pending,
  }))
  const nowPct = Math.max(0, Math.min(100, ((nowMin.value - first) / span) * 100))
  return { first, last, segs, nowPct }
})

/* ===== 大屏模式 ===== */
const cockpitRef = ref<HTMLElement | null>(null)
const bigScreen = ref(false)
const dsRef = ref<HTMLElement | null>(null)
const dsBig = ref(false)
const nowText = ref('')
let clockTimer: any = null
function tickClock() {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  nowText.value = `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${t(WEEK_NAMES[d.getDay()])} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}
async function toggleBigScreen() {
  if (!bigScreen.value) {
    try { await cockpitRef.value?.requestFullscreen() } catch {}
  } else {
    try { await document.exitFullscreen() } catch {}
  }
}
async function toggleDsScreen() {
  if (!dsBig.value) {
    try { await dsRef.value?.requestFullscreen() } catch {}
  } else {
    try { await document.exitFullscreen() } catch {}
  }
}
function onFsChange() {
  bigScreen.value = document.fullscreenElement === cockpitRef.value
  dsBig.value = document.fullscreenElement === dsRef.value
}
onMounted(() => {

  if (isTeacher.value) loadNotices()
  document.addEventListener('fullscreenchange', onFsChange)
  clockTimer = setInterval(tickClock, 1000)
  tickClock()
  // 观察 data-theme 切换，驱动图表配色重算
  themeObs = new MutationObserver(() => { themeTick.value++ })
  themeObs.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] })
})
onBeforeUnmount(() => {
  document.removeEventListener('fullscreenchange', onFsChange)
  clearInterval(clockTimer)
  themeObs?.disconnect()
  if (dsTimer) clearInterval(dsTimer)
})

const approvedList = computed(() => studentApproved.value)
const pendingCourses = computed(() => teacherCourses.value.filter((c: any) => c.oversubscribed))

function capPercent(c: any) {
  const cap = c.capacity || 0
  if (!cap) return 0
  return Math.min(100, Math.round(((c.stat?.total || 0) / cap) * 100))
}

function tlIcon(type: string) {
  const m: Record<string, string> = { '登录': 'user', '选课': 'book', '退课': 'x-circle', '审批': 'check-circle' }
  return m[type] || 'cpu'
}
function timeAgo(s: any) {
  if (!s) return ''
  const t = typeof s === 'number' ? new Date(s) : new Date(String(s).replace(' ', 'T'))
  if (isNaN(t.getTime())) return String(s)
  const m = Math.floor((Date.now() - t.getTime()) / 60000)
  if (m < 1) return t('刚刚')
  if (m < 60) return t('{n} 分钟前', { n: m })
  const h = Math.floor(m / 60)
  if (h < 24) return t('{n} 小时前', { n: h })
  return t('{n} 天前', { n: Math.floor(h / 24) })
}

async function loadData() {
  try {
    const r: any = await http.get('/system/config')
    Object.assign(systemConfig, r?.data || {})
  } catch {}

  if (isStudent.value) {
    try {
      const [pR, mR, cR, gR] = await Promise.all([
        http.get('/enrollments/points').catch(() => null),
        http.get('/enrollments/mine').catch(() => ({ data: [] })),
        http.get('/courses').catch(() => null),
        http.get('/graduation/summary').catch(() => null),
      ])
      remaining.value = pR?.data?.remaining ?? 60
      pointTotal.value = pR?.data?.total ?? 50
      const mine: any[] = mR?.data || []
      studentEnrollments.value = mine
      studentApproved.value = mine.filter(e => e.status === 'approved')
      studentPending.value = mine.filter(e => e.status === 'pending')
      // 已获学分：统一取毕业进度接口（有效成绩 + 已录取 + 模块上限），与毕业页一致
      const passed = (e: any) => e.grade != null && e.grade !== '' && !isNaN(Number(e.grade)) && Number(e.grade) >= 60
      coursesAll.value = cR?.data || []
      gradSummary.value = gR?.data || null
      studentCredits.value = gradSummary.value?.total?.earned != null
        ? Number(gradSummary.value.total.earned)
        : studentApproved.value.filter(passed).reduce((s: number, e: any) => s + (e.credit || 0), 0)
      // 已选学分：已录取课程的全部学分（含未通过 / 未出成绩）
      studentCreditsEnrolled.value = studentApproved.value.reduce((s: number, e: any) => s + (e.credit || 0), 0)
    } catch {}
  } else if (isTeacher.value) {
    try {
      const r: any = await http.get(`/courses/teacher/${auth.user?.id}`)
      teacherCourses.value = r?.data || []
    } catch {}
  } else if (isAdmin.value) {
    try {
      const [uR, cR, lR, rpR] = await Promise.all([
        http.get('/admin/users').catch(() => ({ data: [] })),
        http.get('/courses?category=全部').catch(() => ({ data: [] })),
        // 首页只用最近 6 条：必须走服务端分页。/logs 无参会把整张日志表
        // （已增长到 17MB）拉回前端解析，长时间占死主线程 → 点击菜单无响应/页面卡死
        http.get('/logs', { params: { page: 1, size: 6 } }).catch(() => ({ data: null })),
        http.get('/stats/report').catch(() => null),
      ])
      // /admin/users 带分页参数时返回 {items,total}，不带参数返回裸数组：统一归一为数组，
      // 防止未来该接口默认分页化后下方 filter/length 静默失效
      const ud: any = uR?.data
      adminUsers.value = Array.isArray(ud) ? ud : (ud?.items || [])
      adminCourses.value = cR?.data || []
      const logData: any = lR?.data
      const logRows: any[] = Array.isArray(logData) ? logData : (logData?.items || [])
      recentLogs.value = logRows.slice(0, 6)
      const rd = rpR?.data || {}
      dsOv.value = rd.overview || {}
      dsHot.value = rd.hot || []
      dsRounds.value = rd.rounds || []
    } catch {}

    // 大屏时间线：立即取 + 30 秒轮询
    const fetchLive = async () => {
      try {
        const r: any = await http.get('/stats/live')
        dsTimeline.value = r?.data?.timeline || []
      } catch { /* 保留上一次数据 */ }
    }
    fetchLive()
    if (dsTimer) clearInterval(dsTimer)
    dsTimer = setInterval(fetchLive, 30000)
  }
}

onMounted(() => {
  auth.loadFromStorage()
  loadData()
})
</script>

<style scoped>
/* ===== 学域星图简报（三端共用头部） ===== */
.ark-brief {
  position: relative; display: flex; align-items: stretch; gap: 12px;
  min-height: 198px; margin-bottom: 22px; overflow: hidden;
  background: linear-gradient(100deg, rgba(9, 13, 18, .97) 0%, rgba(15, 21, 28, .9) 58%, rgba(9, 13, 18, .62) 100%);
  border: 1px solid var(--ark-line-strong, rgba(210, 225, 235, .26));
  border-radius: 3px;
  clip-path: polygon(0 0, calc(100% - 18px) 0, 100% 18px, 100% 100%, 0 100%);
  box-shadow: 0 14px 36px rgba(0, 0, 0, .28);
}
.ark-brief::before {
  content: ''; position: absolute; inset: 0; pointer-events: none;
  background-image:
    linear-gradient(rgba(116, 228, 196, .035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(116, 228, 196, .035) 1px, transparent 1px);
  background-size: 28px 28px;
  mask-image: linear-gradient(90deg, rgba(0, 0, 0, .7), transparent 78%);
}
.ark-brief::after {
  content: ''; position: absolute; left: 0; right: 0; top: -60%; height: 60%;
  background: linear-gradient(180deg, transparent, rgba(116, 228, 196, .06), transparent);
  animation: abScan 7s linear infinite; pointer-events: none;
}
@keyframes abScan { 0% { transform: translateY(0); } 100% { transform: translateY(280%); } }

.ab-left { position: relative; z-index: 2; flex: 1; min-width: 0; padding: 20px 8px 22px 26px; display: flex; flex-direction: column; justify-content: center; gap: 8px; }
.ab-kicker {
  font-family: var(--ark-mono); font-size: 9.5px; letter-spacing: .18em;
  color: var(--ark-dim, #8b98a4); white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.ab-kicker em { font-style: normal; color: var(--ark-y, #74e4c4); }
.ab-title { display: flex; align-items: center; gap: 12px; line-height: 1.1; }
.ab-title b { font-size: 30px; font-weight: 800; letter-spacing: .06em; color: #eef3f6; }
.ab-mark { color: var(--ark-y, #74e4c4); font-size: 17px; line-height: 1; }
.ab-role {
  font-family: var(--ark-mono); font-size: 10px; letter-spacing: .12em;
  padding: 3px 9px; color: #0a0d10; background: var(--ark-y, #74e4c4);
  clip-path: polygon(0 0, 100% 0, 100% calc(100% - 5px), calc(100% - 5px) 100%, 0 100%);
}
.ab-hello { font-size: 13px; color: #c3cdd6; }
.ab-hello b { color: var(--ark-y, #74e4c4); font-weight: 700; }
.ab-slash { margin: 0 10px; color: var(--ark-line-strong, rgba(210, 225, 235, .26)); }
.ab-meta {
  display: flex; align-items: center; flex-wrap: wrap; gap: 10px;
  margin-top: 4px; font-family: var(--ark-mono); font-size: 10.5px;
  letter-spacing: .08em; color: var(--ark-dim, #8b98a4);
}
.ab-meta i { width: 1px; height: 12px; background: var(--ark-line-strong, rgba(210, 225, 235, .26)); }
.ab-meta .ab-led {
  width: 7px; height: 7px; border-radius: 50%; background: #4ad6a0;
  box-shadow: 0 0 0 3px rgba(74, 214, 160, .15); animation: arkPulse 2.2s ease-in-out infinite;
}
.ab-meta .ab-on { color: var(--ark-y, #74e4c4); }
.ab-meta .ab-off { color: var(--ark-red, #ff4a3d); }
.ab-meta .ab-clock { color: #eef3f6; font-variant-numeric: tabular-nums; }

.ab-right { position: relative; z-index: 1; flex: 0 0 196px; width: 196px; min-height: 198px; margin-right: 12px; align-self: center; }
.ab-hazard {
  position: absolute; left: 0; right: 0; bottom: 0; height: 5px;
  background: repeating-linear-gradient(-45deg, var(--ark-y, #74e4c4) 0 9px, #151a10 9px 18px);
  opacity: .68;
}

/* 日间模式：简报保持深色质感，页面底色改宣纸白 */
html:not([data-theme="dark"]) .stats-page.liquid {
  background: linear-gradient(180deg, #e8ebe6 0%, #f0f2ec 50%, #e8ebe6 100%);
}

@media (max-width: 900px) {
  .ab-right { flex-basis: 150px; width: 150px; }
  .ab-title b { font-size: 25px; }
}
@media (max-width: 760px) {
  .ark-brief { min-height: 172px; }
  .ab-left { padding: 18px 116px 20px 18px; }
  .ab-right { position: absolute; right: 4px; top: 18px; width: 122px; height: 122px; min-height: 0; flex: none; opacity: .62; }
  .ab-right :deep(.ark-hud) { min-height: 0; }
  .ab-title b { font-size: 22px; }
  .ab-kicker { font-size: 8px; letter-spacing: .1em; }
  .ab-meta { gap: 7px; font-size: 9.5px; }
}
@media (prefers-reduced-motion: reduce) {
  .ark-brief::after, .ab-meta .ab-led { animation: none; }
}

/* ===== 驾驶舱 ===== */
/* 管理员统计卡口径说明 */
.stat-sub { font-size: 12px; color: var(--text-light); margin-top: 2px; }
/* 浅色主题：页面容器统一为浅青绿渐变（#EAF8F5 系），深色主题沿用全局变量 */
html:not([data-theme="dark"]) .stats-page {
  background: linear-gradient(180deg, #EAF8F5 0%, #F0FAF7 50%, #EAF8F5 100%);
}
.cockpit { display: flex; flex-direction: column; gap: 14px; }
.cp-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.cp-title { display: flex; align-items: center; gap: 10px; font-size: 16px; color: var(--text); }
.cp-logo { width: 34px; height: 34px; border-radius: 10px; display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, rgba(43,177,135,.18), rgba(34,211,238,.14)); color: var(--primary); }
.cp-logo .icon { width: 17px; height: 17px; }
.cp-round-chip { font-size: 12px; color: #059669; background: rgba(16,185,129,.11); border: 1px solid rgba(16,185,129,.28);
  padding: 3px 11px; border-radius: 999px; font-weight: 600; }
/* 当前学期 chip（参照真实高校：首页显著位置展示学籍学期） */
.cp-sem-chip { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; color: #0d7a6c;
  background: rgba(13,148,136,.10); border: 1px solid rgba(13,148,136,.32); padding: 3px 12px;
  border-radius: 999px; font-weight: 600; line-height: 1.2; }
.cp-sem-chip .icon { width: 13px; height: 13px; display: block; flex-shrink: 0; }
html.dark .cp-sem-chip { color: #4cc9a3; }
.cp-head-side { display: flex; align-items: center; gap: 12px; }
.cp-clock { font-size: 14px; font-weight: 700; color: var(--primary); font-variant-numeric: tabular-nums; letter-spacing: .5px; }
/* 主操作：去选课（深绿实心） */
.cp-primary-btn { display: inline-flex; align-items: center; gap: 7px; border: none; cursor: pointer;
  font-size: 12.5px; font-weight: 800; letter-spacing: .5px; color: #fff;
  background: var(--green-grad); padding: 8px 20px; border-radius: 999px;
  box-shadow: 0 3px 10px rgba(15,111,82,.3);
  transition: transform .2s ease-out, box-shadow .2s ease-out, filter .2s ease-out; }
.cp-primary-btn:hover { transform: translateY(-1px); box-shadow: 0 5px 14px rgba(15,111,82,.38); filter: brightness(1.06); }
.cp-primary-btn:active { transform: translateY(0); filter: brightness(.95); box-shadow: 0 2px 6px rgba(15,111,82,.28); }
.cp-primary-btn:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; }
.cp-btn-ico { display: inline-flex; }
.cp-btn-ico :deep(.icon) { width: 14px; height: 14px; }
/* 次要操作：进入/退出大屏（描边幽灵按钮，不抢主操作焦点） */
.cp-screen-btn { border: 1px solid var(--border); cursor: pointer; font-size: 12.5px; font-weight: 600;
  color: var(--text-light); background: var(--card-bg); padding: 7px 16px; border-radius: 999px;
  transition: background .2s ease-out, border-color .2s ease-out, color .2s ease-out, transform .15s ease-out; }
.cp-screen-btn:hover { background: var(--primary-bg); border-color: rgba(31,166,122,.4); color: var(--primary); }
.cp-screen-btn:active { transform: scale(.97); }
.cp-screen-btn:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; }

/* KPI */
.cp-kpis { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 12px; }
@media (max-width: 1400px) { .cp-kpis { grid-template-columns: repeat(4, minmax(0, 1fr)); } }
@media (max-width: 1100px) { .cp-kpis { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 620px) { .cp-kpis { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
.cp-kpi {
  --kc: #2bb187;
  position: relative; overflow: hidden;
  display: flex; align-items: center; gap: 12px;
  padding: 15px 16px; border-radius: 14px;
  background: var(--card-bg); border: 1px solid var(--border);
}
/* KPI 为纯展示卡：保持静态，不设悬停动效（只有可点击卡片才允许 hover 反馈） */
.cp-kpi-ico { width: 38px; height: 38px; border-radius: 11px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  background: color-mix(in srgb, var(--kc) 14%, transparent); color: var(--kc); }
.cp-kpi-ico .icon { width: 18px; height: 18px; }
.cp-kpi-num { font-size: 24px; font-weight: 800; color: var(--text); line-height: 1.05; font-variant-numeric: tabular-nums; }
.cp-kpi-num small { font-size: 12px; color: var(--text-light); font-weight: 600; margin-left: 2px; }
.cp-kpi-label { font-size: 12px; color: var(--text-light); margin-top: 2px; }
/* 学分缺口卡：选修模块迷你进度条 */
.cp-kpi-bar { height: 4px; border-radius: 999px; background: color-mix(in srgb, var(--kc) 14%, transparent); margin-top: 7px; overflow: hidden; }
.cp-kpi-bar i { display: block; height: 100%; border-radius: 999px;
  background: linear-gradient(90deg, var(--kc), color-mix(in srgb, var(--kc) 55%, #38bdf8));
  transition: width .7s cubic-bezier(.22,.9,.36,1); }

/* 主体：左列大图表 / 右列指标栈，列内自适应填满 */
.cp-grid { display: grid; grid-template-columns: minmax(0, 7fr) minmax(0, 5fr); gap: 14px; align-items: stretch; }
.cp-col { display: flex; flex-direction: column; gap: 14px; min-width: 0; }
.cp-col-main .cp-trend { flex: 1.25; }
.cp-col-main .cp-bubble { flex: 1; }
.cp-panel { display: flex; flex-direction: column; border-radius: 14px; padding: 15px 17px;
  background: var(--card-bg); border: 1px solid var(--border); box-shadow: var(--shadow); min-width: 0; min-height: 0; }
.cp-chart-fill { flex: 1; min-height: 170px; display: flex; }
.cp-chart-fill .ec-wrap { width: 100%; }
.cp-bubble-fill { align-items: center; }
.cp-bubble-fill .di-bubble { width: 100%; height: auto; max-height: 100%; flex: 0 1 auto; }
.cp-ring { align-items: stretch; }
.cp-ring-box { flex: 1; display: flex; align-items: center; justify-content: center; min-height: 168px; }
.cp-ring-box svg { width: min(100%, 176px); height: auto; }
@media (max-width: 1100px) {
  .cp-grid { grid-template-columns: minmax(0, 1fr); }
}
.cp-p-title { font-size: 13px; font-weight: 700; color: var(--text); margin-bottom: 9px; }
.cp-p-title small { font-weight: 500; font-size: 12px; color: var(--text-light); margin-left: 6px; }
.cp-nodata { font-size: 12px; color: var(--text-light); opacity: .75; padding: 22px 0; text-align: center; }

/* 毕业环（容器为弹性居中，见 cp-grid 区的 cp-ring-box 基础规则） */
.cp-ring-box { position: relative; }
/* 毕业要求未配置时的降级态 */
.cp-ring-empty { flex: 1; align-self: stretch; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 7px; min-height: 168px; text-align: center; }
.cp-ring-empty b { font-size: 14.5px; font-weight: 700; color: var(--text); }
.cp-ring-empty span { font-size: 12px; color: var(--text-light); }
.cp-ring-track { fill: none; stroke: rgba(148,163,184,.16); stroke-width: 11; }
.cp-ring-fill { fill: none; stroke-width: 11; stroke-linecap: round; transition: stroke-dasharray .9s cubic-bezier(.22,.9,.36,1); }
.cp-ring-center { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 2px; }
.cp-ring-center .cp-ring-courses { font-size: 11px; color: var(--text-light); margin-top: 2px; font-variant-numeric: tabular-nums; }
.cp-ring-center .cp-ring-courses.ok { color: #059669; }
html.dark .cp-ring-center .cp-ring-courses.ok { color: #34d399; }
.cp-ring-center b { font-size: 31px; font-weight: 800; color: var(--text); line-height: 1; }
.cp-ring-center b small { font-size: 15px; color: var(--text-light); }
.cp-ring-center span { font-size: 12px; color: var(--text-light); }
.cp-ring-center em { font-style: normal; font-size: 12px; font-weight: 700; color: #fbbf24; }
.cp-ring-center em.ok { color: #059669; }

/* 气泡（沿用原样式类） */
.di-bubble { width: 100%; height: auto; display: block; }
.di-b-grid { stroke: rgba(148,163,184,.16); stroke-width: 1; }
.di-b-axis { fill: var(--text-light); font-size: 12px; font-variant-numeric: tabular-nums; }
.di-bubble-g .di-bubble { fill: rgba(52,211,153,.55); stroke: rgba(255,255,255,.7); stroke-width: 1; opacity: .92; }
.di-bubble-g.pending .di-bubble { fill: rgba(245,158,11,.55); }
.di-b-name { fill: var(--text-light); font-size: 12px; font-weight: 600; }

/* 今日课程 */
.cp-today-list { display: flex; flex-direction: column; gap: 7px; margin-bottom: 10px; }
.cp-today-row { display: flex; align-items: center; gap: 10px; padding: 9px 10px; border-radius: 10px;
  background: color-mix(in srgb, var(--card-bg) 42%, transparent); }
.cp-today-row.now { border-color: rgba(16,185,129,.45); background: rgba(16,185,129,.07); }
.cp-today-time { font-size: 12px; font-weight: 800; color: var(--primary); font-variant-numeric: tabular-nums; width: 42px; flex-shrink: 0; }
.cp-today-body { flex: 1; min-width: 0; }
.cp-today-body b { display: block; font-size: 12.5px; color: var(--text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cp-today-body span { font-size: 12px; color: var(--text-light); }
.cp-live { font-style: normal; font-size: 12px; font-weight: 700; color: #059669; background: rgba(16,185,129,.13);
  padding: 2px 9px; border-radius: 999px; flex-shrink: 0; animation: cpPulse 1.8s ease-in-out infinite; }
@keyframes cpPulse { 0%,100% { opacity: .6 } 50% { opacity: 1 } }
.cp-today-row.past { opacity: .55; }
/* 待录取课：琥珀色虚线框，与已录取课区分 */
.cp-today-row.pend { border: 1px dashed rgba(217,119,6,.45); background: rgba(217,119,6,.05); }
.cp-wait { font-style: normal; font-size: 12px; font-weight: 700; color: #b45309; background: rgba(217,119,6,.12);
  padding: 2px 9px; border-radius: 999px; flex-shrink: 0; }
.cp-ts-wait { color: #b45309; font-weight: 700; }
.cp-done { font-style: normal; font-size: 12px; color: var(--text-light); background: rgba(100,116,139,.12);
  padding: 2px 9px; border-radius: 999px; flex-shrink: 0; }
.cp-next { font-style: normal; font-size: 12px; font-weight: 700; color: #0284c7; background: rgba(14,165,233,.12);
  padding: 2px 9px; border-radius: 999px; flex-shrink: 0; }
/* 今日时间轴 + 概况 */
.cp-today-tl { margin: 6px 2px 2px; }
.cp-tl-bar { position: relative; height: 8px; border-radius: 999px; background: color-mix(in srgb, var(--text-light) 14%, transparent); }
.cp-tl-seg { position: absolute; top: 0; height: 100%; border-radius: 999px; background: rgba(43,177,135,.22); transition: background .3s; }
.cp-tl-seg.past { background: rgba(43,177,135,.42); }
.cp-tl-seg.pend { background: rgba(217,119,6,.32); }
.cp-tl-seg.on { background: var(--primary); box-shadow: 0 0 8px rgba(43,177,135,.55); }
.cp-tl-now { position: absolute; top: -3px; width: 2px; height: 14px; background: #ef4444; border-radius: 2px; transform: translateX(-1px); }
.cp-tl-labels { display: flex; justify-content: space-between; font-size: 11px; color: var(--text-lighter); margin-top: 5px; font-variant-numeric: tabular-nums; }
.cp-today-summary { font-size: 12px; color: var(--text-light); padding: 4px 2px 0; }
.cp-ts-next { color: #0284c7; font-weight: 700; }
.bs-mode .cp-tl-bar { background: rgba(148,163,184,.14); }
.bs-mode .cp-ts-next { color: #38bdf8; }
.cp-load { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
.cp-load-item { text-align: center; padding: 9px 6px; background: color-mix(in srgb, var(--card-bg) 42%, transparent); border-radius: 10px; }
.cp-load-item b { display: block; font-size: 19px; font-weight: 800; color: var(--text); }
.cp-load-item.warn b { color: #d97706; }
.cp-load-item span { font-size: 12px; color: var(--text-light); }

/* 待办 / 已录取 */
.cp-todo-list { display: flex; flex-direction: column; gap: 6px; }
.cp-todo-row { display: flex; justify-content: space-between; align-items: center; gap: 8px; font-size: 12px;
  padding: 7px 11px; border: 1px dashed var(--border); border-radius: 9px; }
.cp-todo-row b { color: var(--text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cp-todo-row span { color: var(--text-light); flex-shrink: 0; }
.cp-todo-ok { font-size: 12px; color: #059669; padding: 10px 2px; }
.cp-chips { display: flex; flex-wrap: wrap; gap: 6px; }
.cp-chip { font-size: 12px; font-weight: 600; color: var(--text); background: rgba(43,177,135,.1);
  border: 1px solid rgba(43,177,135,.25); padding: 3px 11px; border-radius: 999px; }

/* ===== 大屏模式：深色控制中心 ===== */
.cockpit.bs-mode {
  --text: #f1f5f9;
  --text-light: #94a3b8;
  --text-lighter: #64748b;
  --border: rgba(148,163,184,.18);
  --card-bg: rgba(15,23,42,.55);
  --primary: #4cc9a3;

  position: fixed; inset: 0; z-index: 2000;
  background:
    radial-gradient(900px 500px at 12% -8%, rgba(43,177,135,.16), transparent 55%),
    radial-gradient(800px 520px at 105% 108%, rgba(34,211,238,.12), transparent 55%),
    #0a1120;
  padding: 26px 34px; overflow-y: auto;
  display: flex; flex-direction: column; gap: 18px;
}
.bs-mode .cp-title { font-size: 21px; }
.bs-mode .cp-logo { width: 42px; height: 42px; background: linear-gradient(135deg, rgba(43,177,135,.35), rgba(34,211,238,.25)); }
.bs-mode .cp-logo .icon { width: 21px; height: 21px; }
.bs-mode .cp-clock { font-size: 19px; color: #22d3ee; }
.bs-mode .cp-kpi { padding: 20px 22px; border-radius: 16px; background: rgba(148,163,184,.055);
  border-color: rgba(148,163,184,.16); backdrop-filter: blur(10px); }
.bs-mode .cp-kpi-num { font-size: 33px; }
.bs-mode .cp-kpi-label { font-size: 12.5px; }
.bs-mode .cp-panel { background: rgba(148,163,184,.055); border-color: rgba(148,163,184,.16);
  backdrop-filter: blur(10px); padding: 18px 20px; border-radius: 17px; }
.bs-mode .cp-p-title { font-size: 15px; }
/* 大屏下网格撑满剩余视口：左列两大图表均分；右列各面板弹性分配、以最小高度兜底 */
.bs-mode .cp-grid { flex: 1; min-height: 0; }
.bs-mode .cp-col { min-height: 0; }
.bs-mode .cp-chart-fill { min-height: 190px; }
.bs-mode .cp-col-side .cp-panel { flex: 1 1 auto; min-height: 0; }
.bs-mode .cp-ring { min-height: 185px; }
.bs-mode .cp-radar .cp-chart-fill { min-height: 150px; }
.bs-mode .cp-today { min-height: 140px; }
.bs-mode .cp-todo { min-height: 150px; }
.bs-mode .cp-ring-box svg { width: min(100%, 196px); }
.bs-mode .cp-ring-center b { font-size: 38px; }
.bs-mode .di-b-name { fill: #94a3b8; }
.bs-mode .cp-today-row, .bs-mode .cp-todo-row { background: rgba(148,163,184,.05); }
.bs-mode .cp-screen-btn { background: transparent; border-color: rgba(148,163,184,.42); color: #e2e8f0; }
.bs-mode .cp-screen-btn:hover { background: rgba(148,163,184,.12); border-color: rgba(148,163,184,.6); color: #fff; }

@media (prefers-reduced-motion: reduce) {
  .cp-kpi, .cp-kpi:hover, .cp-live { animation: none !important; transition: none !important; }
}

/* ===== 教务数据大屏（管理员端，浅薄荷绿风格；深色主题见文末 data-theme 覆盖） ===== */
.ds-screen {
  position: relative; overflow: hidden;
  display: flex; flex-direction: column; gap: 13px;
  padding: 18px 20px 20px; border-radius: 18px;
  background:
    radial-gradient(760px 320px at 8% -12%, rgba(56,189,248,.10), transparent 60%),
    radial-gradient(680px 300px at 102% 112%, rgba(38,173,145,.10), transparent 60%),
    linear-gradient(160deg, #FFFFFF 0%, #EAF8F5 55%, #D5F3EC 100%);
  border: 1px solid #DCEFEA;
  box-shadow: 0 14px 38px rgba(38,173,145,.12);
}
.ds-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.ds-title { display: flex; align-items: center; gap: 10px; font-size: 16.5px; font-weight: 800; color: #243B40; letter-spacing: 1px; }
.ds-logo { width: 32px; height: 32px; border-radius: 9px; display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, rgba(38,173,145,.16), rgba(56,189,248,.14)); color: #26AD91;
  border: 1px solid rgba(38,173,145,.30); }
.ds-logo :deep(.icon) { width: 17px; height: 17px; }
.ds-head-right { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.ds-pill { font-size: 12px; font-weight: 700; color: #16866F; padding: 4px 14px; border-radius: 999px;
  background: rgba(38,173,145,.10); border: 1px solid rgba(38,173,145,.26); letter-spacing: .5px; }
.ds-fs-btn { cursor: pointer; font-size: 12px; font-weight: 700; color: #16866F; padding: 4px 14px; border-radius: 999px;
  background: rgba(38,173,145,.10); border: 1px solid rgba(38,173,145,.26); letter-spacing: .5px;
  transition: background .2s, color .2s; }
.ds-fs-btn:hover { background: rgba(38,173,145,.22); color: #16866F; }

/* 全屏：布局自适应铺满，文字同步放大保持清晰 */
.ds-screen:fullscreen {
  border-radius: 0; padding: 28px 36px 32px;
  overflow: auto;
  background: linear-gradient(160deg, #FFFFFF 0%, #EAF8F5 55%, #D5F3EC 100%);
}
.ds-screen:fullscreen .ds-title { font-size: 24px; }
.ds-screen:fullscreen .ds-kpi-num { font-size: 38px; }
.ds-screen:fullscreen .ds-kpi-label { font-size: 14px; }
.ds-screen:fullscreen .ds-p-title { font-size: 16px; }
.ds-screen:fullscreen .ds-bar-name { flex-basis: 180px; }
.ds-screen:fullscreen .ds-bars { min-height: 220px; }
.ds-screen:fullscreen .ds-chart-box { min-height: 220px; }
.ds-screen:fullscreen .ds-dot { width: 52px; height: 52px; font-size: 20px; }

/* KPI 卡：白色卡片 + 细边框 + 柔和阴影 */
.ds-kpis { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.ds-kpi {
  position: relative; overflow: hidden;
  padding: 14px 16px 10px; border-radius: 12px;
  background: #FFFFFF; border: 1px solid #DCEFEA;
  box-shadow: 0 6px 16px rgba(38,173,145,.08);
}
.ds-kpi-num { font-size: 26px; font-weight: 800; line-height: 1.05; font-variant-numeric: tabular-nums; }
.ds-kpi-num small { font-size: 13px; margin-left: 2px; opacity: .85; }
.ds-kpi-label { font-size: 12px; color: #243B40; margin-top: 3px; font-weight: 600; }
.ds-kpi-sub { font-size: 12px; color: #7B9294; margin-top: 1px; }
.ds-spark { position: absolute; right: 10px; bottom: 10px; width: 96px; height: 26px; opacity: .85; }

/* 三列面板 */
.ds-grid { display: grid; grid-template-columns: 1.12fr 1fr 1fr; gap: 12px; }
@media (max-width: 1000px) { .ds-grid { grid-template-columns: 1fr; } .ds-kpis { grid-template-columns: 1fr; } }
.ds-panel {
  display: flex; flex-direction: column; min-width: 0;
  padding: 13px 15px; border-radius: 12px;
  background: #FFFFFF; border: 1px solid #DCEFEA;
  box-shadow: 0 6px 16px rgba(38,173,145,.08);
}
.ds-p-title { font-size: 13px; font-weight: 700; color: #243B40; margin-bottom: 8px; }
.ds-p-title small { font-weight: 500; font-size: 12px; color: #7B9294; margin-left: 6px; }
.ds-empty { flex: 1; display: flex; align-items: center; justify-content: center; font-size: 12px; color: #7B9294; padding: 26px 0; }

/* 热度条（横向条形图：课程名完整可读，超长省略 + title 悬停全名） */
.ds-bars { flex: 1; display: flex; flex-direction: column; justify-content: space-evenly; gap: 6px; min-height: 168px; padding-top: 2px; }
.ds-bar-row { display: flex; align-items: center; gap: 10px; min-width: 0; }
.ds-bar-name { flex: 0 0 128px; min-width: 0; font-size: 12px; color: #243B40;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap; text-align: right; }
.ds-bar-track { flex: 1; min-width: 0; height: 14px; border-radius: 7px;
  background: rgba(38,173,145,.12); overflow: hidden; }
.ds-bar { display: block; height: 100%; border-radius: 7px;
  background: linear-gradient(90deg, #26AD91 0%, #3CC39A 100%);
  box-shadow: 0 2px 8px rgba(0,0,0,.16);
  transition: width .8s cubic-bezier(.22,.9,.36,1); }
.ds-bar-val { flex: 0 0 44px; font-size: 12px; font-weight: 800; color: #16866F; font-variant-numeric: tabular-nums; }

/* 图表盒 */
.ds-chart-box { flex: 1; min-height: 168px; display: flex; }

/* 名额流动 */
.ds-flow-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; flex-wrap: wrap; }
.ds-flow-legend { display: flex; gap: 14px; flex-wrap: wrap; }
.ds-flow-legend span { display: inline-flex; align-items: center; gap: 5px; font-size: 12px; color: #7B9294; }
.ds-flow-legend i { width: 8px; height: 8px; border-radius: 50%; display: inline-block; }
.ds-steps { display: flex; align-items: flex-start; gap: 4px; padding: 16px 6px 4px; }
.ds-step { display: flex; flex-direction: column; align-items: center; gap: 4px; flex-shrink: 0; }
.ds-dot { width: 42px; height: 42px; border-radius: 50%; display: flex; align-items: center; justify-content: center;
  font-size: 16px; font-weight: 800; color: #7B9294;
  background: rgba(38,173,145,.10); transition: all .5s; }
.ds-dot.on { color: #fff; animation: dsPulse 2.4s ease-in-out infinite; }
.ds-step-name { font-size: 12px; font-weight: 600; color: #7B9294; }
.ds-step.on .ds-step-name { color: #243B40; }
.ds-step-round { font-size: 12px; color: #7B9294; opacity: .8; }
@keyframes dsPulse { 0%,100% { transform: scale(1) } 50% { transform: scale(1.08) } }
.ds-link { flex: 1; min-width: 14px; position: relative; height: 42px; }
.ds-link::before { content: ''; position: absolute; left: 0; right: 0; top: 50%; height: 2px;
  background: #DCEFEA; border-radius: 1px; }
.ds-link i { position: absolute; left: 50%; top: 50%; width: 6px; height: 6px; border-radius: 50%;
  transform: translate(-50%, -50%); background: rgba(38,173,145,.35); }
.ds-link.on::before { background: linear-gradient(90deg, rgba(38,173,145,.7), rgba(56,189,248,.7)); }
.ds-link.on i { background: #26AD91; box-shadow: 0 2px 8px rgba(0,0,0,.16); }
@media (max-width: 720px) {
  .ds-steps { flex-wrap: wrap; gap: 12px; justify-content: center; }
  .ds-link { display: none; }
}

/* ===== 深色主题：教务数据大屏保留原深蓝配色（主题切换能力不变） ===== */
html[data-theme="dark"] .ds-screen {
  background:
    radial-gradient(760px 320px at 8% -12%, rgba(56,189,248,.12), transparent 60%),
    radial-gradient(680px 300px at 102% 112%, rgba(60,195,154,.10), transparent 60%),
    linear-gradient(160deg, #0a1428 0%, #0b1830 55%, #0a1226 100%);
  border: 1px solid rgba(70,110,180,.32);
  box-shadow: 0 14px 38px rgba(6,16,38,.45), inset 0 1px 0 rgba(140,180,255,.12);
}
html[data-theme="dark"] .ds-title { color: #e8f2ff; }
html[data-theme="dark"] .ds-logo {
  background: linear-gradient(135deg, rgba(56,189,248,.22), rgba(43,177,135,.2)); color: #38bdf8;
  border-color: rgba(57,214,255,.35); }
html[data-theme="dark"] .ds-pill {
  color: #9fd8ff; background: rgba(57,214,255,.09); border-color: rgba(57,214,255,.32); }
html[data-theme="dark"] .ds-fs-btn {
  color: #9fd8ff; background: rgba(57,214,255,.09); border-color: rgba(57,214,255,.32); }
html[data-theme="dark"] .ds-fs-btn:hover { background: rgba(57,214,255,.2); color: #d6f1ff; }
html[data-theme="dark"] .ds-kpi {
  background: rgba(16,34,64,.5); border-color: rgba(70,110,180,.3); box-shadow: none; }
html[data-theme="dark"] .ds-kpi-label { color: #cfe3ff; }
html[data-theme="dark"] .ds-kpi-sub { color: #a9c1e0; }
html[data-theme="dark"] .ds-panel {
  background: rgba(16,34,64,.5); border-color: rgba(70,110,180,.3); box-shadow: none; }
html[data-theme="dark"] .ds-p-title { color: #cfe3ff; }
html[data-theme="dark"] .ds-p-title small { color: #a9c1e0; }
html[data-theme="dark"] .ds-empty { color: #a9c1e0; }
html[data-theme="dark"] .ds-bar-name { color: #cfe3ff; }
html[data-theme="dark"] .ds-bar-track { background: rgba(70,110,180,.20); }
html[data-theme="dark"] .ds-bar {
  background: linear-gradient(90deg, #2bb187 0%, #3cc39a 100%); }
html[data-theme="dark"] .ds-bar-val { color: #9fe4cc; }
html[data-theme="dark"] .ds-flow-legend span { color: #aecbe8; }
html[data-theme="dark"] .ds-dot { color: #9db8d8; background: rgba(70,110,180,.3); }
html[data-theme="dark"] .ds-dot.on { color: #07101f; }
html[data-theme="dark"] .ds-step-name { color: #aecbe8; }
html[data-theme="dark"] .ds-step.on .ds-step-name { color: #eaf4ff; }
html[data-theme="dark"] .ds-step-round { color: #8fa8cc; opacity: 1; }
html[data-theme="dark"] .ds-link::before { background: rgba(70,110,180,.35); }
html[data-theme="dark"] .ds-link i { background: rgba(120,160,220,.45); }
html[data-theme="dark"] .ds-link.on i { background: #3cc39a; box-shadow: 0 2px 8px rgba(0,0,0,.16); }
/* 首页课程卡扁平化（外层 section 已是卡片，避免套卡） */
.cockpit .course-grid-flat .course-card { border: none; background: var(--card-bg-2); box-shadow: none; }
html[data-theme="dark"] .cockpit .course-grid-flat .course-card { background: rgba(255,255,255,.045); }
/* hero 右侧轮次标签 + 今日课程稀疏大行 */
.home-hero-slim { padding: 15px 22px; }
.home-hero-round { display: flex; flex-direction: column; align-items: flex-end; gap: 3px; }
.hhr-num { font-size: 15px; font-weight: 700; color: var(--primary); font-variant-numeric: tabular-nums; }
.hhr-status { font-size: 11.5px; padding: 2px 9px; border-radius: 10px; }
.hhr-status.open { background: rgba(52,211,153,.15); color: #6ee7b7; }
.hhr-status.closed { background: rgba(248,113,113,.15); color: #fca5a5; }
html:not([data-theme="dark"]) .hhr-status.open { background: #d1fae5; color: #047857; }
html:not([data-theme="dark"]) .hhr-status.closed { background: #fee2e2; color: #b91c1c; }
.cp-today-list.sparse { display: flex; flex-direction: column; gap: 10px; }
.cp-today-list.sparse .cp-today-row { padding: 14px 14px; }
.cp-today-list.sparse .cp-today-body b { font-size: 15px; }
.cp-today-list.sparse .cp-today-time { font-size: 14px; width: 96px; }
.cp-ring-center span { font-size: 11.5px; white-space: nowrap; }
.cp-ring-center em { font-size: 11px; white-space: nowrap; }

/* 教师首页：课程卡 3 列对齐 + 通知行 */
.course-grid-3 { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.notice-list { display: flex; flex-direction: column; }
.notice-row { display: flex; align-items: center; justify-content: space-between; gap: 12px;
  padding: 10px 8px; border-bottom: 1px dashed var(--border); font-size: 13px; cursor: pointer;
  border-radius: 8px; transition: background .2s ease-out; }
.notice-row:hover { background: rgba(31,166,122,.05); }
.notice-row:last-child { border-bottom: none; }
.notice-row b { color: var(--text); font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.notice-row span { color: var(--text-lighter); font-size: 12px; flex-shrink: 0; }
/* 单轮趋势降级摘要 */
.cp-trend-summary { flex: 1; display: flex; align-items: center; justify-content: space-around; min-height: 120px; padding: 12px 6px; gap: 10px; }
.cts-item { display: flex; flex-direction: column; align-items: center; gap: 5px; }
.cts-item b { font-size: 26px; font-weight: 800; color: var(--primary-light, #3cc39a); font-variant-numeric: tabular-nums; }
.cts-item span { font-size: 12px; color: var(--text-light); }

/* ===== 首页驾驶舱精简：稀疏模块让位大屏模式，常规面板均分列高 ===== */
.cockpit .cp-panel.cp-trend-compact { flex: 0 0 auto; }
.cockpit:not(.bs-mode) .cp-today,
.cockpit:not(.bs-mode) .cp-todo,
.cockpit:not(.bs-mode) .cp-ring { flex: 1 1 auto; }
.cp-kpi-sub { font-size: 12px; color: var(--text-light); margin-top: 1px; }
</style>
