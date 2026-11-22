<template>
  <div class="page-wrap stats-page liquid rd-page">
    <!-- ===== 常驻运行状态条 ===== -->
    <div class="rd-statusbar">
      <div class="rsb-round">
        <span class="rsb-no">{{ t('第 {n} 轮', { n: config.currentRound ?? '—' }) }}</span>
        <span class="rd-pill" :class="config.roundOpen ? 'rd-pill-green' : 'rd-pill-amber'">{{ config.roundOpen ? t('进行中') : t('已关闭') }}</span>
      </div>
      <div class="rsb-item"><span class="rsb-label">{{ t('倒计时') }}</span><span class="rsb-val">{{ countdownText || t('—') }}</span></div>
      <div class="rsb-item"><span class="rsb-label">{{ t('阶段') }}</span><span class="rsb-val">{{ phaseLabel(derivedPhase || config.currentPhase) }}</span></div>
      <div class="rsb-item"><span class="rsb-label">{{ t('每学期学分上限') }}</span><span class="rsb-val">{{ config.maxCreditPerSemester ?? '—' }} {{ t('分') }}</span></div>
      <div class="rsb-item"><span class="rsb-label">{{ t('新学期') }}</span><span class="rsb-val"><span class="rd-pill" :class="config.newSemesterReady ? 'rd-pill-green' : 'rd-pill-amber'">{{ config.newSemesterReady ? t('已开启') : t('未开启') }}</span></span></div>
      <div class="rsb-item"><span class="rsb-label">{{ t('轮次进度') }}</span><span class="rsb-val">{{ t('已完成 {n} / 3 轮 · 可开启第 {m} 轮', { n: roundProgress, m: unlockedRound }) }}</span></div>
      <span class="rd-auto-tag" :class="{ on: schedule.autoSchedule }"><span class="rd-auto-dot"></span>{{ schedule.autoSchedule ? t('自动排班已开启') : t('手动模式') }}</span>
    </div>

    <!-- ===== Tab 导航 ===== -->
    <div class="rd-tabs">
      <button class="rd-tab" :class="{ on: rdTab === 'run' }" @click="rdTab = 'run'">{{ t('运行控制') }}</button>
      <button class="rd-tab" :class="{ on: rdTab === 'schedule' }" @click="rdTab = 'schedule'">{{ t('时间安排') }}</button>
      <button class="rd-tab" :class="{ on: rdTab === 'params' }" @click="rdTab = 'params'">{{ t('规则与参数') }}</button>
      <button class="rd-tab" :class="{ on: rdTab === 'plans' }" @click="rdTab = 'plans'">
        <span v-html="svgIcon('graduation-cap')" />{{ t('差异化培养方案') }}
      </button>
    </div>

    <!-- ===== Tab1 运行控制（默认） ===== -->
    <div v-show="rdTab === 'run'" class="rd-pane">
      <div class="section rd-danger">
        <div class="section-header">
          <div class="section-title" v-html="svgIcon('alert-triangle') + ' ' + t('手动控制')"></div>
          <span class="rd-danger-hint">{{ t('操作实时生效，影响全体学生') }}</span>
        </div>
        <div class="rd-manual">
          <div class="rd-manual-row">
            <div class="rd-manual-info">
              <b>{{ t('当前状态：') }}{{ config.roundOpen ? t('选课开放中') : t('选课已关闭') }}</b>
              <span>{{ t('直接开/关本轮选课（第 {n} 轮）；关闭时自动结算待录取报名并解锁下一轮', { n: config.currentRound ?? '—' }) }}{{ roundBlockReason ? t('　⚠ ') + roundBlockReason : '' }}</span>
            </div>
            <button v-if="!config.roundOpen" class="btn btn-success" :disabled="!!roundBlockReason" @click="setRoundOpen(true)">{{ roundBlockReason ? t('本轮未解锁') : t('开启本轮') }}</button>
            <button v-else class="btn btn-danger" @click="setRoundOpen(false)">{{ t('关闭本轮') }}</button>
          </div>
          <div class="rd-manual-row">
            <div class="rd-manual-info">
              <b>{{ t('立即结算本轮') }}</b>
              <span>{{ t('按积分从高到低统一录取当前轮的待录取报名（同分随机抽签）；仅可结算当前已解锁的轮次，第 3 轮先到先得无需结算') }}</span>
            </div>
            <button class="btn btn-primary" :disabled="settling || roundLocked || (config.currentRound ?? 1) >= 3" @click="settleNow">{{ settling ? t('结算中…') : t('立即结算') }}</button>
          </div>
          <div class="rd-manual-row">
            <div class="rd-manual-info">
              <b>{{ t('切换轮次') }}</b>
              <span>{{ t('只能切换到已解锁轮次（当前最高第 {n} 轮）；需先关闭本轮，关闭时自动结算并解锁下一轮', { n: unlockedRound }) }}</span>
            </div>
            <div class="rd-round-ctl">
              <el-input-number v-model="manualRound" :min="1" :max="unlockedRound" />
              <button class="btn btn-primary" :disabled="config.roundOpen || manualRound > unlockedRound" @click="switchRound">{{ t('切换') }}</button>
            </div>
          </div>
          <div class="rd-manual-row">
            <div class="rd-manual-info">
              <b>{{ t('进入新学期 / 释放名额') }}</b>
              <span>{{ t('开启新学期并解锁第 1 轮选课：推进学期偏移，旧报名自动归档、名额按学期口径满额释放；报名记录、成绩与学分一条都不删，候补与待处理申请仅标记归档。未开启新学期时无法开启第 1 轮。') }}</span>
            </div>
            <button class="btn btn-primary" :disabled="releasing" @click="releaseCapacity">{{ releasing ? t('处理中…') : t('进入新学期') }}</button>
          </div>
        </div>
      </div>

      <!-- 开放时间轴 -->
      <div class="section">
        <div class="section-header">
          <div class="section-title" v-html="svgIcon('clock') + ' ' + t('开放时间轴总览')"></div>
          <span class="rd-tl-hint">{{ t('三轮全局窗口 + 各年级错峰时刻 · 红色虚线为当前时间') }}</span>
        </div>
        <div v-if="tlDomain" class="rd-tl-wrap">
          <svg class="rd-tl-svg" :viewBox="`0 0 ${TLW} ${TLH}`" role="img" :aria-label="t('三轮开放时间轴')">
            <defs>
              <linearGradient id="rdTlIndigo" x1="0" y1="0" x2="1" y2="0">
                <stop offset="0%" stop-color="#2bb187" /><stop offset="100%" stop-color="#38bdf8" />
              </linearGradient>
              <linearGradient id="rdTlAmber" x1="0" y1="0" x2="1" y2="0">
                <stop offset="0%" stop-color="#f59e0b" /><stop offset="100%" stop-color="#fbbf24" />
              </linearGradient>
            </defs>

            <!-- 当前时间线 -->
            <g v-if="tlNowX != null">
              <line :x1="tlNowX" :x2="tlNowX" :y1="24" :y2="TLH - 30" class="rd-tl-now" />
              <rect :x="Math.max(2, tlNowX - 20)" y="6" width="40" height="16" rx="8" class="rd-tl-now-pill" />
              <text :x="Math.max(22, tlNowX)" y="17.5" text-anchor="middle" class="rd-tl-now-text">{{ t('现在') }}</text>
            </g>

            <!-- 每轮一条轨道 -->
            <g v-for="row in tlRows" :key="row.roundNo">
              <rect x="0" :y="row.y - 22" :width="TLW" height="48" rx="10" class="rd-tl-rowbg" :class="{ cur: row.current }" />
              <text x="30" :y="row.y + 4" text-anchor="middle" class="rd-tl-badge" :class="{ cur: row.current }">{{ t('第{n}轮', { n: row.roundNo }) }}</text>
              <line :x1="GUTTER" :x2="TLW - 12" :y1="row.y" :y2="row.y" class="rd-tl-track" />

              <template v-if="row.openAt">
                <rect
                  :x="row.x0" :y="row.y - 9"
                  :width="Math.max(6, row.x1 - row.x0)" height="18" rx="9"
                  class="rd-tl-bar" :class="['r' + row.roundNo, { cur: row.current, past: row.past }]"
                />
                <rect
                  v-if="row.elapsedW > 0"
                  :x="row.x0" :y="row.y - 9" :width="row.elapsedW" height="18" rx="9"
                  class="rd-tl-elapsed" :class="'r' + row.roundNo"
                />
                <text :x="row.x0" :y="row.y - 15" text-anchor="middle" class="rd-tl-time">{{ fmtTL(row.openAt) }}</text>
                <text v-if="row.closeAt" :x="row.x1" :y="row.x1 - row.x0 < 120 ? row.y + 26 : row.y - 15" text-anchor="middle" class="rd-tl-time" :class="{ drop: row.x1 - row.x0 < 120 }">{{ fmtTL(row.closeAt) }}</text>
                <text v-else :x="TLW - 14" :y="row.y - 15" text-anchor="end" class="rd-tl-time open">{{ t('未设截止 →') }}</text>
</template>
              <text v-else :x="GUTTER + 8" :y="row.y + 4" class="rd-tl-time">{{ t('未设置时间') }}</text>

              <!-- 年级错峰标记 -->
              <g v-for="m in row.marks" :key="m.key">
                <path :d="diamondTL(m.x, row.y, 6)" class="rd-tl-mark" :class="{ cur: row.current }" />
                <text :x="m.x" :y="row.y + 32" text-anchor="middle" class="rd-tl-mark-label">{{ m.label }}</text>
              </g>
            </g>

            <!-- 时间刻度 -->
            <g>
              <line v-for="t in tlTicks" :key="'tk' + t.v" :x1="t.x" :x2="t.x" :y1="TLH - 30" :y2="TLH - 25" class="rd-tl-tick" />
              <text v-for="t in tlTicks" :key="'tt' + t.v" :x="t.x" :y="TLH - 11" text-anchor="middle" class="rd-tl-axis">{{ t.label }}</text>
            </g>
          </svg>
        </div>
        <div v-else class="rd-tl-empty">{{ t('尚未设置任何开放时间——在「时间安排」保存后，这里会展示完整的时间轴。') }}</div>
      </div>
    </div>

    <!-- ===== Tab2 时间安排 ===== -->
    <div v-show="rdTab === 'schedule'" class="rd-pane">
      <div class="section">
        <div class="section-header">
          <div class="section-title" v-html="svgIcon('calendar') + ' ' + t('三轮时间窗口')"></div>
          <button class="btn btn-primary btn-sm" :disabled="savingSchedule" @click="saveSchedule">{{ savingSchedule ? t('保存中…') : t('保存时间设置') }}</button>
        </div>
        <label class="rd-switch-label">
          <el-switch v-model="schedule.autoSchedule" />
          <span>{{ t('到点自动开放/结束') }}</span>
        </label>
        <div class="rd-sched">
          <div v-for="s in schedule.rows" :key="s.roundNo" class="rd-sched-row" :class="{ current: s.roundNo === config.currentRound }">
            <div class="rd-round-badge" :class="{ current: s.roundNo === config.currentRound }">{{ t('第{n}轮', { n: s.roundNo }) }}</div>
            <div class="rd-sched-fields">
              <el-date-picker v-model="s.openAt" type="datetime" :placeholder="t('开放时间')" format="MM-DD HH:mm" :clearable="true" />
              <span class="rd-arrow" v-html="svgIcon('arrow-right')"></span>
              <el-date-picker v-model="s.closeAt" type="datetime" :placeholder="t('结束时间')" format="MM-DD HH:mm" :clearable="true" />
            </div>
            <span class="rd-pill rd-pill-sm" :class="rowStatus(s).cls">{{ rowStatus(s).text }}</span>
          </div>
          <div class="rd-sched-tip" v-html="svgIcon('alert-triangle') + ' ' + t('开启自动排班后，系统每 30 秒巡检一次：到开放时间自动开放对应轮次，到结束时间自动关闭并进入下一轮。手动开关/切轮会退出自动模式。')"></div>
        </div>
      </div>

      <div class="section">
        <div class="section-header">
          <div class="section-title" v-html="svgIcon('layers') + ' ' + t('错峰开放（按年级）')"></div>
          <button class="btn btn-primary btn-sm" :disabled="savingStagger" @click="saveStagger">{{ savingStagger ? t('保存中…') : t('保存错峰配置') }}</button>
        </div>
        <div class="rd-stagger">
          <div class="rd-stagger-tip" v-html="svgIcon('alert-triangle') + ' ' + t('仅第 1、2 轮积分选课生效：各年级在全局窗口内按各自时间错峰提交，统一截止后的排序/抽签结果不受影响。第三轮先到先得不错峰，全员同时开放。未配置的年级跟随全局开放时间。')"></div>
          <div v-if="unknownStudents > 0" class="rd-stagger-unknown">
            {{ t('有 {n} 名学生尚未设置年级，这些学生不会参与错峰（跟随全局开放时间）。可在「用户管理」编辑用户补录年级，或要求学生在注册时使用含入学年份的学号（自动推导）。', { n: unknownStudents }) }}
          </div>
          <div v-for="rn in [1, 2]" :key="rn" class="rd-stagger-round">
            <div class="rd-stagger-round-head">
              <span class="rd-round-badge" :class="{ current: rn === config.currentRound }">{{ t('第{n}轮', { n: rn }) }}</span>
              <button class="btn btn-sm rd-add-grade" @click="addStaggerRow(rn)">{{ t('+ 添加年级') }}</button>
            </div>
            <div v-if="!staggerRows(rn).length" class="rd-stagger-empty">{{ t('未配置错峰，本轮全部年级同时开放') }}</div>
            <div v-for="row in staggerRows(rn)" :key="row._key" class="rd-stagger-row">
              <el-select v-model="row.grade" filterable allow-create default-first-option size="small" style="width:150px" :placeholder="t('选择年级')">
                <el-option v-for="g in gradeOptions" :key="g.grade" :label="g.count == null ? t('{n} 级', { n: g.grade }) : t('{n} 级 · {m} 人', { n: g.grade, m: g.count })" :value="g.grade" />
              </el-select>
              <el-date-picker v-model="row.openAt" type="datetime" :placeholder="t('该年级开放时间')" format="MM-DD HH:mm" style="width:240px" />
              <button class="btn btn-danger btn-sm" @click="removeStaggerRow(row)">{{ t('删除') }}</button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ===== Tab3 规则与参数 ===== -->
    <div v-show="rdTab === 'params'" class="rd-pane">
      <details class="section rd-details">
        <summary class="section-title" v-html="svgIcon('sparkles') + ' ' + t('三轮录取机制（玩法说明）')"></summary>
        <div class="rd-rounds">
          <div class="rd-round-card" :class="{ current: config.currentRound === 1 }">
            <div class="rd-round-head">
              <div class="rd-round-no">{{ t('第一轮') }}</div>
              <span class="rd-pill rd-pill-blue">{{ t('积分选课') }}</span>
            </div>
            <div class="rd-round-pool">{{ t('积分池：') }}<b>50</b> {{ t('分（系统发放，自由分配）') }}</div>
            <ul class="rd-round-rules">
              <li v-html="t('窗口期内<b>任意时间</b>提交都有效，<b>不用卡点抢</b>。')"></li>
              <li v-html="t('截止后系统<b>按积分从高到低</b>统一录取，名额满为止。')"></li>
              <li v-html="t('<b>同分随机抽签</b>，结果公示后查看。')"></li>
              <li>{{ t('选中后支持退课，积分全额返还。') }}</li>
            </ul>
          </div>

          <div class="rd-round-card" :class="{ current: config.currentRound === 2 }">
            <div class="rd-round-head">
              <div class="rd-round-no">{{ t('第二轮') }}</div>
              <span class="rd-pill rd-pill-blue">{{ t('积分选课 · 重置') }}</span>
            </div>
            <div class="rd-round-pool">{{ t('积分池：') }}<b>{{ t('重新发放 50') }}</b> {{ t('分，回到 50 重新投分') }}</div>
            <ul class="rd-round-rules">
              <li>{{ t('规则与第一轮完全一致：截止后统一排序、同分随机抽签。') }}</li>
              <li v-html="t('开放第一轮<b>落选 / 退课</b>空出的名额。')"></li>
              <li>{{ t('可在本轮重新分配积分，争取心仪课程。') }}</li>
            </ul>
          </div>

          <div class="rd-round-card fcfc" :class="{ current: config.currentRound === 3 }">
            <div class="rd-round-head">
              <div class="rd-round-no">{{ t('第三轮') }}</div>
              <span class="rd-pill rd-pill-amber">{{ t('先到先得 · 无积分') }}</span>
            </div>
            <div class="rd-round-pool" v-html="t('仅剩前两轮<b>剩余空位</b>')"></div>
            <ul class="rd-round-rules">
              <li v-html="t('传统<b>抢课模式</b>，按报名顺序录取。')"></li>
              <li v-html="t('<b>无需投入积分</b>，点「确认加入」即报名。')"></li>
              <li>{{ t('名额满即止，先到先得。') }}</li>
            </ul>
          </div>
        </div>
      </details>

      <div class="rd-grid">
        <div class="section">
          <div class="section-header">
            <div class="section-title" v-html="svgIcon('settings') + ' ' + t('选课策略')"></div>
            <button class="btn btn-primary btn-sm" :disabled="saving" @click="saveStrategy">{{ saving ? t('保存中…') : t('保存策略') }}</button>
          </div>
          <div class="rd-form">
            <div class="rc-row">
              <label>{{ t('每学期学分上限') }}</label>
              <el-input-number v-model="form.maxCreditPerSemester" :min="1" :max="60" />
            </div>
            <div class="rc-row">
              <label>{{ t('每学期门数上限') }}</label>
              <el-input-number v-model="form.maxCoursesPerSemester" :min="1" :max="15" />
              <span class="rd-unit">{{ t('门（选课/候补/结算统一校验）') }}</span>
            </div>
            <div class="rc-row">
              <label>{{ t('候补确认时限') }}</label>
              <el-input-number v-model="form.confirmMinutes" :min="5" :max="240" :step="5" />
              <span class="rd-unit">{{ t('分钟') }}</span>
            </div>
            <div class="rc-row">
              <label>{{ t('教师授课上限') }}</label>
              <el-input-number v-model="form.teacherMaxCourses" :min="1" :max="10" />
              <span class="rd-unit">{{ t('门 / 学期 · 教师工作量约束') }}</span>
            </div>
            <div class="rc-row">
              <label>{{ t('选课阶段') }}</label>
              <el-select v-model="form.currentPhase" style="width:220px">
                <el-option v-for="p in PHASE_OPTIONS" :key="p.value" :label="t(p.label)" :value="p.value" />
              </el-select>
            </div>
            <div class="rd-form-tip">{{ t('学分上限与候补时限独立生效；开放/关闭时间在「时间安排」中设置。') }}</div>
          </div>
        </div>

        <div class="section">
          <div class="section-header">
            <div class="section-title" v-html="svgIcon('graduation-cap') + ' ' + t('毕业要求学分')"></div>
            <button class="btn btn-primary btn-sm" :disabled="saving" @click="saveGradReqs">{{ saving ? t('保存中…') : t('保存毕业要求') }}</button>
          </div>
          <div class="rd-form">
            <div class="rc-row">
              <label>{{ t('必修') }}</label>
              <el-input-number v-model="form.gradReqRequired" :min="0" :max="60" />
              <span class="rd-unit">{{ t('学分') }}</span>
            </div>
            <div class="rc-row">
              <label>{{ t('公共选修') }}</label>
              <el-input-number v-model="form.gradReqGeneral" :min="0" :max="60" />
              <span class="rd-unit">{{ t('学分 · 华厦 8 分') }}</span>
            </div>
            <div class="rc-row">
              <label>{{ t('专业选修') }}</label>
              <el-input-number v-model="form.gradReqElective" :min="0" :max="60" />
              <span class="rd-unit">{{ t('学分') }}</span>
            </div>
            <div class="rd-form-tip">{{ t('合计 {n} 学分 · 必修 / 公共选修 / 专业选修 三类分别达标（对齐厦门华厦学院，公共选修须修满 8 分）；不同年级可在下方单独设置', { n: gradReqTotal }) }}</div>
          </div>
        </div>
      </div>
    </div>

  </div>
  <!-- ===== Tab4 差异化培养方案（内嵌独立管理组件） ===== -->
  <div v-show="rdTab === 'plans'" class="rd-pane">
    <TrainingPlans embedded />
  </div>

</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api'
import TrainingPlans from './TrainingPlans.vue'
import { t } from '@/i18n'
const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`

const PHASE_OPTIONS = [
  { value: 'auto',    label: '自动（按时间推导）' },
  { value: 'regular', label: '正常选课 regular' },
  { value: 'batch',   label: '批次选课 batch' },
  { value: 'reselect',label: '补选阶段 reselect' },
  { value: 'closed',  label: '已结束 closed' }
]
const PHASE_LABELS: Record<string, string> = { auto: '自动推导', regular: '正常选课', batch: '批次选课', reselect: '补选阶段', closed: '已结束' }
const phaseLabel = (v: any) => PHASE_LABELS[v] ? t(PHASE_LABELS[v]) : (v ? String(v) : '—')

const rdTab = ref<'run' | 'schedule' | 'params' | 'plans'>('run')
const config = ref<any>({})
/** 第 1 轮从关闭→开启，必须先「进入新学期」；标记由后端开轮后消费 */
const needNewSemester = computed(() => !config.value.roundOpen
  && (config.value.currentRound ?? 1) === 1
  && !config.value.newSemesterReady)
const roundProgress = computed(() => Math.max(0, Math.min(3, Number(config.value.roundProgress ?? 0))))
const unlockedRound = computed(() => Math.min(3, roundProgress.value + 1))
const roundLocked = computed(() => (config.value.currentRound ?? 1) > unlockedRound.value)
const roundBlockReason = computed(() => {
  const cur = config.value.currentRound ?? 1
  if (config.value.roundOpen) return ''
  if (cur > unlockedRound.value)
    return t('第 {n} 轮尚未解锁：请先完成第 {m} 轮（关闭本轮即自动结算）', { n: cur, m: unlockedRound.value })
  if (cur === 1 && !config.value.newSemesterReady) return t('第 1 轮需先「进入新学期」才能开启')
  if (cur < unlockedRound.value) return t('第 {n} 轮已完成，请切换到第 {m} 轮', { n: cur, m: unlockedRound.value })
  return ''
})
const derivedPhase = ref<string>('')
const form = reactive<any>({ roundOpenAt: null, roundCloseAt: null, currentPhase: 'auto', maxCreditPerSemester: 24, maxCoursesPerSemester: 2, confirmMinutes: 30, teacherMaxCourses: 3, gradReqRequired: 18, gradReqGeneral: 8, gradReqElective: 8, gradReqPractice: 0, gradReqCourses: 0 })
const manualRound = ref<number>(1)
const saving = ref(false)
const savingSchedule = ref(false)
const schedule = reactive<{ autoSchedule: boolean; rows: any[] }>({ autoSchedule: false, rows: [] })
const stagger = reactive<{ rows: any[] }>({ rows: [] })
const savingStagger = ref(false)
const gradReqItems = [
  { key: 'gradReqRequired', label: '必修' },
  { key: 'gradReqGeneral', label: '公共选修' },
  { key: 'gradReqElective', label: '专业选修' },
]
const gradReqTotal = computed(() =>
  (Number(form.gradReqRequired) || 0) + (Number(form.gradReqGeneral) || 0) + (Number(form.gradReqElective) || 0))

const gradeStats = ref<any[]>([])          // 学生表里实际存在的年级 + 人数
const unknownStudents = ref(0)             // 还没有年级的学生数（grade 为空）
// 后端年级分布接口不可用时（例如后端没重启、接口 404）的兜底候选，
// 与注册页年级下拉保持一致，管理员仍可正常配置错峰。
const DEFAULT_GRADES = [2021, 2022, 2023, 2024, 2025, 2026]
const gradeOptions = computed(() => {
  if (gradeStats.value.length) return gradeStats.value
  return DEFAULT_GRADES.map(g => ({ grade: g, count: null as number | null }))
})
let staggerKey = 0
const now = ref(Date.now())
let timer: any = null

/* ---------- 加载 ---------- */
async function load() {
  try {
    const r: any = await http.get('/system/config')
    config.value = r?.data || r
    form.roundOpenAt = config.value.roundOpenAt ? new Date(config.value.roundOpenAt) : null
    form.roundCloseAt = config.value.roundCloseAt ? new Date(config.value.roundCloseAt) : null
    form.currentPhase = config.value.currentPhase || 'auto'
    form.maxCreditPerSemester = config.value.maxCreditPerSemester ?? 24
    form.maxCoursesPerSemester = config.value.maxCoursesPerSemester ?? 2
    form.confirmMinutes = config.value.confirmMinutes ?? 30
    form.teacherMaxCourses = config.value.teacherMaxCourses ?? 3
    form.gradReqRequired = config.value.gradReqRequired ?? 18
    form.gradReqGeneral = config.value.gradReqGeneral ?? 8
    form.gradReqElective = config.value.gradReqElective ?? 8
    form.gradReqPractice = 0
    form.gradReqCourses = 0
    manualRound.value = config.value.currentRound || 1
  } catch {}
  try {
    const p: any = await http.get('/system/phase')
    derivedPhase.value = p?.data?.currentPhase || ''
  } catch { derivedPhase.value = '' }
  try {
    const s: any = await http.get('/system/round-schedule')
    schedule.autoSchedule = !!s?.data?.autoSchedule
    const list = s?.data?.schedules || []
    schedule.rows = [1, 2, 3].map(n => {
      const hit: any = list.find((x: any) => x.roundNo === n) || {}
      return { roundNo: n, openAt: hit.openAt ? new Date(hit.openAt) : null, closeAt: hit.closeAt ? new Date(hit.closeAt) : null }
    })
  } catch {}
  await loadStagger()
  loadGradeStats()
}

/* ---------- 错峰开放 ---------- */
function staggerRows(roundNo: number) { return stagger.rows.filter(r => r.roundNo === roundNo) }
function loadGradeStats() {
  http.get('/system/grade-stats').then((r: any) => {
    gradeStats.value = r?.data?.items || []
    unknownStudents.value = r?.data?.unknownCount || 0
  }).catch(() => {})
}
function addStaggerRow(roundNo: number) {
  stagger.rows.push({ _key: ++staggerKey, roundNo, grade: gradeStats.value[0]?.grade ?? null, openAt: null })
}
function removeStaggerRow(row: any) { const i = stagger.rows.indexOf(row); if (i >= 0) stagger.rows.splice(i, 1) }
async function loadStagger() {
  try {
    const g: any = await http.get('/system/stagger')
    stagger.rows = (g?.data || []).map((x: any) => ({ _key: ++staggerKey, roundNo: x.roundNo, grade: x.grade, openAt: x.openAt ? new Date(x.openAt) : null }))
  } catch {}
}
async function saveStagger() {
  const seen = new Set<string>()
  for (const r of stagger.rows) {
    if (typeof r.grade === 'string') r.grade = Number(r.grade)   // 手动新建的年级是字符串，归一成数字
    if (!r.grade || !r.openAt) { ElMessage.warning(t('请为每条错峰配置填写年级和开放时间')); return }
    const key = r.roundNo + ':' + r.grade
    if (seen.has(key)) { ElMessage.warning(t('第 {n} 轮的 {g} 级配置重复了', { n: r.roundNo, g: r.grade })); return }
    seen.add(key)
  }
  savingStagger.value = true
  try {
    const r: any = await http.post('/system/stagger', stagger.rows.map(x => ({ roundNo: x.roundNo, grade: x.grade, openAt: iso(x.openAt) })))
    ElMessage.success(r?.message || t('错峰配置已保存'))
    await loadStagger()
    loadGradeStats()
  } catch (e: any) { ElMessage.error(e?.message || t('保存失败')) }
  finally { savingStagger.value = false }
}

/* ---------- 倒计时 ---------- */
const countdownText = computed(() => {
  const n = now.value
  if (config.value.roundOpen && config.value.roundCloseAt) {
    const diff = new Date(config.value.roundCloseAt).getTime() - n
    if (diff > 0) return t('距结束 {t}', { t: dur(diff) })
  }
  if (!config.value.roundOpen && config.value.roundOpenAt) {
    const diff = new Date(config.value.roundOpenAt).getTime() - n
    if (diff > 0) return t('距开放 {t}', { t: dur(diff) })
  }
  return ''
})
function dur(ms: number): string {
  const s = Math.floor(ms / 1000)
  const d = Math.floor(s / 86400), h = Math.floor((s % 86400) / 3600), m = Math.floor((s % 3600) / 60), ss = s % 60
  if (d > 0) return t('{d}天{h}小时', { d, h })
  if (h > 0) return t('{h}小时{m}分', { h, m })
  return t('{m}分{s}秒', { m, s: ss })
}

/* ---------- 开放时间轴 ---------- */
const TLW = 860
const GUTTER = 64
const TL_STEP = 56
const TLH = 210
const tlPad = (n: number) => String(n).padStart(2, '0')
function fmtTL(d: Date | null | undefined): string {
  if (!d) return ''
  return `${tlPad(d.getMonth() + 1)}-${tlPad(d.getDate())} ${tlPad(d.getHours())}:${tlPad(d.getMinutes())}`
}
const diamondTL = (x: number, y: number, r: number) => `M${x},${y - r} L${x + r},${y} L${x},${y + r} L${x - r},${y} Z`
const tlStamps = computed(() => {
  const arr: number[] = []
  for (const s of schedule.rows) {
    if (s.openAt) arr.push(s.openAt.getTime())
    if (s.closeAt) arr.push(s.closeAt.getTime())
  }
  for (const g of stagger.rows) if (g.openAt) arr.push(g.openAt.getTime())
  return arr
})
const tlDomain = computed(() => {
  const stamps = tlStamps.value
  if (!stamps.length) return null
  let lo = Math.min(...stamps), hi = Math.max(...stamps)
  if (hi - lo < 30 * 60000) hi = lo + 30 * 60000
  const pad = (hi - lo) * 0.04
  return { lo: lo - pad, hi: hi + pad }
})
const tlX = (t: number) => GUTTER + ((t - tlDomain.value!.lo) / (tlDomain.value!.hi - tlDomain.value!.lo)) * (TLW - GUTTER - 16)
const tlNowX = computed(() => {
  if (!tlDomain.value) return null
  const n = now.value
  if (n < tlDomain.value.lo || n > tlDomain.value.hi) return null
  return tlX(n)
})
const tlTicks = computed(() => {
  if (!tlDomain.value) return []
  const { lo, hi } = tlDomain.value
  return [0, .25, .5, .75, 1].map(v => ({ v, x: GUTTER + v * (TLW - GUTTER - 16), label: fmtTL(new Date(lo + v * (hi - lo))) }))
})
const tlRows = computed(() => {
  if (!tlDomain.value) return []
  const n = now.value
  return [1, 2, 3].map((rn, i) => {
    const s: any = schedule.rows.find((x: any) => x.roundNo === rn) || {}
    const open = s.openAt as Date | null
    const close = s.closeAt as Date | null
    const x0 = open ? tlX(open.getTime()) : null
    const x1 = close ? tlX(close.getTime()) : TLW - 14
    const endT = close ? close.getTime() : tlDomain.value!.hi
    let elapsedW = 0
    if (open && n > open.getTime() && n < endT) elapsedW = Math.max(3, tlX(Math.min(n, endT)) - x0!)
    const marks = stagger.rows
      .filter(g => g.roundNo === rn && g.openAt)
      .map((g: any) => {
        let delay = ''
        if (open) {
          const m = Math.round((g.openAt.getTime() - open.getTime()) / 60000)
          delay = m > 0 ? t(' +{m}分', { m }) : m < 0 ? t(' {m}分', { m }) : t(' 同时开放')
        }
        return { key: rn + ':' + g.grade, x: tlX(g.openAt.getTime()), label: t('{g}级', { g: g.grade }) + delay }
      })
    return {
      roundNo: rn, y: 46 + i * TL_STEP, current: config.value.currentRound === rn,
      openAt: open, closeAt: close, x0, x1, elapsedW, past: !!close && close.getTime() < n, marks,
    }
  })
})

/* ---------- 三轮时间窗 ---------- */
function rowStatus(s: any) {
  if (!s.openAt || !s.closeAt) return { text: t('未设置'), cls: 'rd-pill-gray' }
  const n = now.value, o = s.openAt.getTime(), c = s.closeAt.getTime()
  if (n < o) return { text: t('未开始'), cls: 'rd-pill-amber' }
  if (n <= c) return { text: t('进行中'), cls: 'rd-pill-green' }
  return { text: t('已结束'), cls: 'rd-pill-gray' }
}
function iso(d: any): string | null {
  if (!d) return null
  const x = new Date(d), p = (n: number) => String(n).padStart(2, '0')
  return `${x.getFullYear()}-${p(x.getMonth() + 1)}-${p(x.getDate())}T${p(x.getHours())}:${p(x.getMinutes())}:${p(x.getSeconds())}`
}
async function saveSchedule() {
  for (const s of schedule.rows) {
    if (s.openAt && s.closeAt && s.closeAt.getTime() <= s.openAt.getTime()) {
      ElMessage.warning(t('第 {n} 轮的结束时间必须晚于开放时间', { n: s.roundNo })); return
    }
  }
  savingSchedule.value = true
  try {
    await http.post('/system/round-schedule', {
      autoSchedule: schedule.autoSchedule,
      schedules: schedule.rows.map(s => ({ roundNo: s.roundNo, openAt: iso(s.openAt), closeAt: iso(s.closeAt) }))
    })
    ElMessage.success(t('时间设置已保存'))
    await load()
  } catch (e: any) { ElMessage.error(e?.message || t('保存失败')) }
  finally { savingSchedule.value = false }
}

/* ---------- 手动控制 ---------- */
const settling = ref(false)
async function settleNow() {
  try {
    await ElMessageBox.confirm(
      t('立即结算第 {n} 轮？将按投入积分从高到低统一录取待录取报名（同分随机抽签），未录取者积分退还。', { n: config.value.currentRound ?? '—' }),
      t('结算本轮'),
      { type: 'warning', confirmButtonText: t('结算'), cancelButtonText: t('取消') }
    )
  } catch { return }
  settling.value = true
  try {
    const r: any = await http.post('/enrollments/settle', {})
    ElMessage.success((r?.message || t('结算完成')) + ' · 学习轨迹已生成')
    load()
  } catch (e: any) { ElMessage.error(e?.message || t('结算失败')) }
  finally { settling.value = false }
}
async function setRoundOpen(open: boolean) {
  if (open && roundBlockReason.value) {
    ElMessage.warning(roundBlockReason.value)
    return
  }
  const firstRoundStart = open && !config.value.roundOpen && (config.value.currentRound ?? 1) === 1
  try {
    await ElMessageBox.confirm(
      open
        ? t('开启第 {n} 轮选课？学生将立即可以选课。', { n: config.value.currentRound })
          + (firstRoundStart ? t(' 新学期名额已按学期口径释放，全部报名与成绩记录保留。') : '')
        : t('关闭第 {n} 轮选课？学生将无法继续选课。', { n: config.value.currentRound }),
      open ? t('开启选课') : t('关闭选课'),
      { type: 'warning', confirmButtonText: open ? t('开启') : t('关闭'), cancelButtonText: t('取消') }
    )
    const r: any = await http.post('/system/round-open', { roundOpen: open })
    ElMessage.success(r?.message || (open ? t('本轮选课已开启') : t('本轮选课已关闭')))
    load()
  } catch (e: any) { if (e !== 'cancel') ElMessage.error(e?.message || t('操作失败')) }
}

/* ---------- 进入新学期（按学期口径释放名额，不删数据） ---------- */
const releasing = ref(false)
async function releaseCapacity() {
  try {
    await ElMessageBox.confirm(
      t('进入新学期？将推进学期偏移并解锁第 1 轮选课，旧报名自动归档、名额满额释放；报名记录、成绩与学分全部保留，不会删除任何数据，仅候补与待处理申请标记为归档。已开启时重复点击不会再次推进学期。'),
      t('进入新学期'),
      { type: 'info', confirmButtonText: t('确认进入'), cancelButtonText: t('取消') }
    )
  } catch { return }
  releasing.value = true
  try {
    const r: any = await http.post('/system/release-capacity', {})
    ElMessage.success(r?.message || t('容量已释放'))
    load()
  } catch (e: any) { ElMessage.error(e?.message || t('释放失败')) }
  finally { releasing.value = false }
}
async function switchRound() {
  if (config.value.roundOpen) { ElMessage.warning(t('请先关闭本轮选课，关闭后会自动结算并解锁下一轮')); return }
  if (manualRound.value > unlockedRound.value) {
    ElMessage.warning(t('第 {n} 轮尚未解锁：请先完成第 {m} 轮', { n: manualRound.value, m: unlockedRound.value })); return
  }
  if (manualRound.value === config.value.currentRound) { ElMessage.info(t('已经是第 {n} 轮了', { n: manualRound.value })); return }
  try {
    await ElMessageBox.confirm(t('切换到第 {n} 轮？手动切换将退出自动排班模式。', { n: manualRound.value }), t('切换轮次'),
      { type: 'warning', confirmButtonText: t('切换'), cancelButtonText: t('取消') })
    await http.post('/system/round', { currentRound: manualRound.value })
    ElMessage.success(t('已切换到第 {n} 轮', { n: manualRound.value }))
    load()
  } catch (e: any) { if (e !== 'cancel') ElMessage.error(e?.message || t('操作失败')) }
}

/* ---------- 参数保存（分接口） ---------- */
async function saveStrategy() {
  if (form.roundOpenAt && form.roundCloseAt && form.roundCloseAt.getTime() <= form.roundOpenAt.getTime()) {
    ElMessage.warning(t('关闭时间必须晚于开放时间')); return
  }
  saving.value = true
  const warns: string[] = []
  try {
    await http.post('/system/phase', {
      roundOpenAt: iso(form.roundOpenAt), roundCloseAt: iso(form.roundCloseAt), currentPhase: form.currentPhase
    })
  } catch (e: any) { warns.push(t('时间/阶段：') + (e?.message || t('失败'))) }
  try {
    await http.post('/system/max-credit', { maxCreditPerSemester: form.maxCreditPerSemester })
  } catch (e: any) { warns.push(t('学分上限：') + (e?.message || t('失败'))) }
  try {
    await http.post('/system/max-courses', { maxCoursesPerSemester: form.maxCoursesPerSemester })
  } catch (e: any) { warns.push(t('门数上限：') + (e?.message || t('失败'))) }
  try {
    await http.post('/system/teacher-max-courses', { teacherMaxCourses: form.teacherMaxCourses })
  } catch (e: any) { warns.push(t('教师授课上限：') + (e?.message || t('失败'))) }
  try {
    await http.post('/system/confirm-minutes', { confirmMinutes: form.confirmMinutes })
  } catch (e: any) { warns.push(t('候补时限：') + (e?.message || t('失败'))) }
  if (warns.length) ElMessage.warning(t('部分保存失败：') + warns.join(t('；')))
  else ElMessage.success(t('策略已保存'))
  saving.value = false
  load()
}

async function saveGradReqs() {
  saving.value = true
  try {
    const r: any = await http.post('/system/graduation-reqs', {
      gradReqRequired: form.gradReqRequired, gradReqElective: form.gradReqElective,
      gradReqGeneral: form.gradReqGeneral, gradReqPractice: form.gradReqPractice,
      gradReqCourses: form.gradReqCourses
    })
    ElMessage.success(r?.message || t('毕业要求已更新'))
  } catch (e: any) { ElMessage.error(t('毕业要求：') + (e?.message || t('保存失败'))) }
  saving.value = false
  load()
}



onMounted(() => {
  load()
  timer = setInterval(() => { now.value = Date.now() }, 1000)
})
onBeforeUnmount(() => clearInterval(timer))
</script>

<style scoped>
.page-wrap { padding: 4px 0; }

/* 状态药丸 */
.rd-pill { display: inline-flex; align-items: center; padding: 3px 12px; border-radius: 999px; font-size: 13px; font-weight: 600; }
.rd-pill-sm { padding: 2px 10px; font-size: 12px; }
.rd-pill-green { color: #059669; background: rgba(16,185,129,.12); border: 1px solid rgba(16,185,129,.3); }
.rd-pill-amber { color: #d97706; background: rgba(245,158,11,.12); border: 1px solid rgba(245,158,11,.3); }
.rd-pill-gray  { color: #64748b; background: rgba(100,116,139,.12); border: 1px solid rgba(100,116,139,.3); }

/* ===== 常驻运行状态条 ===== */
.rd-statusbar {
  position: sticky; top: 0; z-index: 6;
  display: flex; align-items: center; gap: 24px; flex-wrap: wrap;
  padding: 12px 18px; margin-bottom: 14px;
  border-radius: 14px;
  background: color-mix(in srgb, var(--card-bg) 88%, transparent);
  border: 1px solid var(--border);
  box-shadow: 0 8px 24px -16px rgba(0, 0, 0, .3);
  backdrop-filter: blur(10px);
}
.rsb-round { display: flex; align-items: center; gap: 10px; }
.rsb-no { font-size: 19px; font-weight: 800; color: var(--text); letter-spacing: .5px; }
.rsb-item { display: flex; align-items: baseline; gap: 7px; }
.rsb-label { font-size: 12px; color: var(--text-light); }
.rsb-val { font-size: 14.5px; font-weight: 700; color: var(--text); font-variant-numeric: tabular-nums; }
.rd-statusbar .rd-auto-tag { margin-left: auto; }

/* ===== Tab 导航 ===== */
.rd-tabs { display: flex; gap: 4px; margin-bottom: 16px; border-bottom: 1px solid var(--border); }
.rd-tab {
  appearance: none; border: none; background: transparent; cursor: pointer;
  font-size: 14px; font-weight: 600; color: var(--text-light);
  padding: 10px 18px 12px; position: relative; transition: color .2s, background .2s;
  border-radius: 10px 10px 0 0; font-family: inherit;
}
.rd-tab:hover { color: var(--text); background: color-mix(in srgb, var(--card-bg) 55%, transparent); }
.rd-tab.on { color: var(--primary); }
.rd-tab.on::after {
  content: ''; position: absolute; left: 14px; right: 14px; bottom: -1px; height: 2.5px;
  border-radius: 3px; background: var(--primary);
}
.rd-pane { display: flex; flex-direction: column; gap: 16px; }

/* ===== 危险区（手动控制） ===== */
.rd-danger { border-color: rgba(245, 158, 11, .45); }
.rd-danger .section-title { color: #d97706; }
html.dark .rd-danger .section-title { color: #fbbf24; }
.rd-danger-hint { font-size: 12px; color: #d97706; background: rgba(245,158,11,.1); border: 1px solid rgba(245,158,11,.3); padding: 3px 10px; border-radius: 999px; }
html.dark .rd-danger-hint { color: #fbbf24; }

/* ===== 折叠说明（三轮录取机制） ===== */
.rd-details { padding: 0; overflow: hidden; }
.rd-details summary { list-style: none; cursor: pointer; padding: 14px 18px; display: flex; align-items: center; gap: 8px; user-select: none; }
.rd-details summary::-webkit-details-marker { display: none; }
.rd-details summary::after { content: '▸'; margin-left: auto; color: var(--text-light); transition: transform .2s; font-size: 13px; }
.rd-details[open] summary::after { transform: rotate(90deg); }
.rd-details[open] summary { border-bottom: 1px dashed var(--border); }
.rd-details .rd-rounds { padding: 16px 18px 18px; }

/* 添加年级按钮（可见的虚线幽灵样式） */
.rd-add-grade { border: 1px dashed var(--primary); color: var(--primary); background: transparent; }
.rd-add-grade:hover { background: rgba(43, 177, 135, .08); }

/* 三轮录取机制卡片 */
.rd-rounds { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; }
@media (max-width: 960px) { .rd-rounds { grid-template-columns: 1fr; } }
.rd-round-card { display: flex; flex-direction: column; gap: 10px; padding: 16px 16px 18px;
  border-radius: 14px; border: 1px solid var(--border); background: color-mix(in srgb, var(--card-bg) 60%, transparent);
  position: relative; transition: all .25s; }
.rd-round-card:hover { border-color: rgba(43,177,135,.4); transform: translateY(-3px); box-shadow: 0 10px 26px -12px rgba(43,177,135,.35); }
.rd-round-card.current { border-color: rgba(43,177,135,.6); box-shadow: 0 0 0 3px rgba(43,177,135,.14); }
.rd-round-card.current::after { content: '当前轮次'; position: absolute; top: -10px; right: 14px; font-size: 12px; font-weight: 700;
  color: #fff; background: linear-gradient(135deg, #2bb187, #38bdf8); padding: 3px 10px; border-radius: 999px; box-shadow: 0 4px 10px rgba(43,177,135,.35); }
.rd-round-card.fcfc { border-color: rgba(245,158,11,.35); }
.rd-round-card.fcfc.current { border-color: rgba(245,158,11,.6); box-shadow: 0 0 0 3px rgba(245,158,11,.16); }
.rd-round-card.fcfc.current::after { background: linear-gradient(135deg, #f59e0b, #fbbf24); box-shadow: 0 4px 10px rgba(245,158,11,.35); }
.rd-round-head { display: flex; align-items: center; justify-content: space-between; }
.rd-round-no { font-size: 16px; font-weight: 700; color: var(--text); }
.rd-round-pool { font-size: 12.5px; color: var(--text-light); background: var(--card-bg, rgba(148,163,184,.08)); border: 1px solid var(--border);
  border-radius: 8px; padding: 7px 10px; }
.rd-round-pool b { color: var(--primary); }
.rd-round-card.fcfc .rd-round-pool b { color: #d97706; }
.rd-round-rules { margin: 0; padding-left: 18px; display: flex; flex-direction: column; gap: 6px; }
.rd-round-rules li { font-size: 12.5px; line-height: 1.55; color: var(--text-light); }
.rd-round-rules li b { color: var(--text); }
.rd-pill-blue { color: #2bb187; background: rgba(43,177,135,.12); border: 1px solid rgba(43,177,135,.3); }

/* 自动排班标签 */
.rd-auto-tag { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; color: #64748b; background: rgba(100,116,139,.1);
  border: 1px solid rgba(100,116,139,.25); padding: 4px 12px; border-radius: 999px; }
.rd-auto-tag.on { color: #059669; background: rgba(16,185,129,.1); border-color: rgba(16,185,129,.3); }
.rd-auto-dot { width: 7px; height: 7px; border-radius: 50%; background: currentColor; }
.rd-auto-tag.on .rd-auto-dot { animation: rdBlink 1.6s ease-in-out infinite; }
@keyframes rdBlink { 0%,100% { opacity: 1; } 50% { opacity: .35; } }

/* 三轮时间窗 */
.rd-sched { display: flex; flex-direction: column; gap: 10px; }
.rd-sched-row { display: flex; align-items: center; gap: 14px; padding: 10px 14px; border-radius: 12px;
  background: color-mix(in srgb, var(--card-bg) 60%, transparent); border: 1px solid var(--border); transition: all .25s; }
.rd-sched-row:hover { border-color: rgba(43,177,135,.4); }
.rd-sched-row.current { border-color: rgba(43,177,135,.55); box-shadow: 0 0 0 3px rgba(43,177,135,.12); }
.rd-round-badge { width: 52px; text-align: center; font-size: 13px; font-weight: 700; color: var(--text-light);
  background: rgba(100,116,139,.1); border-radius: 10px; padding: 8px 0; flex-shrink: 0; }
.rd-round-badge.current { color: #fff; background: var(--primary-deep); box-shadow: 0 2px 8px rgba(0,0,0,.25); }
.rd-sched-fields { display: flex; align-items: center; gap: 8px; flex: 1; }
.rd-arrow { color: var(--text-light); display: flex; }
.rd-arrow .icon { width: 14px; height: 14px; }
.rd-sched-tip { display: flex; align-items: flex-start; gap: 8px; font-size: 12.5px; color: var(--text-light);
  padding: 10px 14px; border-radius: 10px; background: rgba(245,158,11,.07); border: 1px dashed rgba(245,158,11,.35); line-height: 1.6; }
.rd-sched-tip .icon { width: 15px; height: 15px; flex-shrink: 0; margin-top: 2px; color: #d97706; }
.rd-switch-label { display: inline-flex; align-items: center; gap: 8px; font-size: 13px; color: var(--text-light); cursor: pointer; }

/* 开放时间轴 */
.rd-tl-hint { font-size: 12px; color: var(--text-light); }
.rd-tl-svg { display: block; width: 100%; height: auto; }
.rd-tl-rowbg { fill: rgba(100, 116, 139, .05); }
.rd-tl-rowbg.cur { fill: rgba(99, 102, 241, .08); }
.rd-tl-badge { fill: var(--text-light); font-size: 12px; font-weight: 700; }
.rd-tl-badge.cur { fill: #2bb187; }
.rd-tl-track { stroke: rgba(148, 163, 184, .25); stroke-width: 1; stroke-dasharray: 3 4; }
.rd-tl-bar { opacity: .5; }
.rd-tl-bar.r1, .rd-tl-bar.r2 { fill: url(#rdTlIndigo); }
.rd-tl-bar.r3 { fill: url(#rdTlAmber); }
.rd-tl-bar.cur { opacity: 1; }
.rd-tl-bar.past { opacity: .28; }
.rd-tl-elapsed { opacity: .45; }
.rd-tl-elapsed.r1, .rd-tl-elapsed.r2 { fill: #1fa67a; }
.rd-tl-elapsed.r3 { fill: #d97706; }
.rd-tl-time { fill: var(--text-light); font-size: 12px; font-variant-numeric: tabular-nums; }
.rd-tl-time.open { fill: #d97706; }
.rd-tl-now { stroke: #f43f5e; stroke-width: 1.4; stroke-dasharray: 4 3; }
.rd-tl-now-pill { fill: #f43f5e; }
.rd-tl-now-text { fill: #fff; font-size: 12px; font-weight: 700; }
.rd-tl-tick { stroke: rgba(148, 163, 184, .5); stroke-width: 1; }
.rd-tl-axis { fill: var(--text-light); font-size: 12px; font-variant-numeric: tabular-nums; }
.rd-tl-mark { fill: #2bb187; stroke: #fff; stroke-width: 1; }
.rd-tl-mark.cur { fill: #1fa67a; }
.rd-tl-mark-label { fill: var(--text-light); font-size: 12px; font-weight: 600; }
.rd-tl-empty { font-size: 13px; color: var(--text-light); padding: 24px; text-align: center; border: 1px dashed var(--border); border-radius: 12px; }

/* 错峰开放配置 */
.rd-stagger { display: flex; flex-direction: column; gap: 12px; }
.rd-stagger-tip { display: flex; align-items: flex-start; gap: 8px; font-size: 12.5px; color: var(--text-light);
  padding: 10px 14px; border-radius: 10px; background: rgba(43,177,135,.06); border: 1px dashed rgba(43,177,135,.35); line-height: 1.6; }
.rd-stagger-tip .icon { width: 15px; height: 15px; flex-shrink: 0; margin-top: 2px; color: #2bb187; }
.rd-stagger-round { display: flex; flex-direction: column; gap: 8px; padding: 12px 14px; border-radius: 12px;
  border: 1px solid var(--border); background: color-mix(in srgb, var(--card-bg) 60%, transparent); }
.rd-stagger-round-head { display: flex; align-items: center; gap: 10px; }
.rd-stagger-empty { font-size: 12.5px; color: var(--text-light); opacity: .8; padding: 4px 2px; }
.rd-stagger-unknown { font-size: 12.5px; color: var(--text-light); line-height: 1.6;
  padding: 8px 14px; border-radius: 10px; background: rgba(245,158,11,.08); border: 1px dashed rgba(245,158,11,.4); }
.rd-stagger-row { display: flex; align-items: center; gap: 10px; }

/* 双列区 */
.rd-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-top: 0; }
@media (max-width: 1080px) { .rd-grid { grid-template-columns: 1fr; } }

/* 手动控制 */
.rd-manual { display: flex; flex-direction: column; gap: 10px; }
.rd-manual-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 14px;
  border-radius: 12px; border: 1px solid var(--border); background: color-mix(in srgb, var(--card-bg) 60%, transparent); }
.rd-manual-info { display: flex; flex-direction: column; gap: 3px; }
.rd-manual-info b { font-size: 13.5px; color: var(--text); }
.rd-manual-info span { font-size: 12px; color: var(--text-light); }
.rd-round-ctl { display: flex; align-items: center; gap: 8px; }

/* 参数表单 */
.rd-form { display: flex; flex-direction: column; gap: 12px; }
.rc-row { display: flex; gap: 12px; align-items: center; }
.rc-row label { width: 120px; font-size: 13px; color: var(--text-light); flex-shrink: 0; }
.rd-unit { font-size: 12.5px; color: var(--text-light); }
.rd-form-tip { font-size: 12px; color: var(--text-light); opacity: .85; padding-top: 2px; }

/* 按年级差异化培养方案 */
.rd-greq-actions { display: flex; align-items: center; gap: 10px; }
.rd-greq-tip { display: flex; align-items: flex-start; gap: 8px; font-size: 12.5px; color: var(--text-light);
  padding: 10px 14px; border-radius: 10px; background: rgba(43, 177, 135, .06);
  border: 1px dashed rgba(43, 177, 135, .35); line-height: 1.6; margin-bottom: 12px; }
.rd-greq-tip .icon { width: 15px; height: 15px; flex-shrink: 0; margin-top: 2px; color: var(--primary); }
.rd-greq { display: flex; flex-direction: column; gap: 8px; }
.rd-greq-row { display: grid; grid-template-columns: 112px minmax(110px, 1fr) minmax(110px, 1fr) repeat(5, minmax(92px, 1fr)) auto;
  gap: 8px; align-items: center; }
.rd-greq-row.head { padding: 0 4px; }
.rd-greq-row.head span { font-size: 12px; font-weight: 700; color: var(--text-light); }
@media (max-width: 1180px) {
  .rd-greq-row { grid-template-columns: 112px repeat(3, minmax(96px, 1fr)); }
  .rd-greq-row.head { display: none; }
}
</style>
