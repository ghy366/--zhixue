<template>
  <div class="page-wrap stats-page liquid rd-page">
    <!-- ===== 顶部统计 ===== -->
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-icon blue" v-html="svgIcon('refresh')" />
        <div><div class="stat-value">第 {{ config.currentRound ?? '—' }} 轮</div><div class="stat-label">当前选课轮次</div></div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" :class="config.roundOpen ? 'green' : 'amber'" v-html="svgIcon(config.roundOpen ? 'check-circle' : 'x-circle')" />
        <div>
          <div class="rd-state-line"><span class="rd-pill" :class="config.roundOpen ? 'rd-pill-green' : 'rd-pill-amber'">{{ config.roundOpen ? '进行中' : '已关闭' }}</span></div>
          <div class="stat-label">轮次状态{{ countdownText ? ' · ' + countdownText : '' }}</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon purple" v-html="svgIcon('coins')" />
        <div><div class="stat-value">{{ config.maxCreditPerSemester ?? '—' }}</div><div class="stat-label">每学期学分上限</div></div>
      </div>
      <div class="stat-card">
        <div class="stat-icon amber" v-html="svgIcon('target')" />
        <div><div class="stat-value rd-phase-value">{{ phaseLabel(derivedPhase || config.currentPhase) }}</div><div class="stat-label">当前阶段</div></div>
      </div>
    </div>

    <!-- ===== 三轮录取机制 ===== -->
    <div class="section">
      <div class="section-header">
        <div class="section-title" v-html="svgIcon('sparkles') + ' 三轮录取机制'"></div>
        <span class="rd-auto-tag" :class="{ on: schedule.autoSchedule }">
          <span class="rd-auto-dot"></span>{{ schedule.autoSchedule ? '自动排班已开启' : '手动模式' }}
        </span>
      </div>
      <div class="rd-rounds">
        <div class="rd-round-card" :class="{ current: config.currentRound === 1 }">
          <div class="rd-round-head">
            <div class="rd-round-no">第一轮</div>
            <span class="rd-pill rd-pill-blue">积分选课</span>
          </div>
          <div class="rd-round-pool">积分池：<b>50</b> 分（系统发放，自由分配）</div>
          <ul class="rd-round-rules">
            <li>窗口期内<b>任意时间</b>提交都有效，<b>不用卡点抢</b>。</li>
            <li>截止后系统<b>按积分从高到低</b>统一录取，名额满为止。</li>
            <li><b>同分随机抽签</b>，结果公示后查看。</li>
            <li>选中后支持退课，积分全额返还。</li>
          </ul>
        </div>

        <div class="rd-round-card" :class="{ current: config.currentRound === 2 }">
          <div class="rd-round-head">
            <div class="rd-round-no">第二轮</div>
            <span class="rd-pill rd-pill-blue">积分选课 · 重置</span>
          </div>
          <div class="rd-round-pool">积分池：<b>重新发放 50</b> 分，回到 50 重新投分</div>
          <ul class="rd-round-rules">
            <li>规则与第一轮完全一致：截止后统一排序、同分随机抽签。</li>
            <li>开放第一轮<b>落选 / 退课</b>空出的名额。</li>
            <li>可在本轮重新分配积分，争取心仪课程。</li>
          </ul>
        </div>

        <div class="rd-round-card fcfc" :class="{ current: config.currentRound === 3 }">
          <div class="rd-round-head">
            <div class="rd-round-no">第三轮</div>
            <span class="rd-pill rd-pill-amber">先到先得 · 无积分</span>
          </div>
          <div class="rd-round-pool">仅剩前两轮<b>剩余空位</b></div>
          <ul class="rd-round-rules">
            <li>传统<b>抢课模式</b>，按报名顺序录取。</li>
            <li><b>无需投入积分</b>，点「确认加入」即报名。</li>
            <li>名额满即止，先到先得。</li>
          </ul>
        </div>
      </div>
    </div>

    <!-- ===== 时间窗口与自动排班 ===== -->
    <div class="section">
      <div class="section-header">
        <div class="section-title" v-html="svgIcon('calendar') + ' 三轮时间窗口'"></div>
        <div style="display:flex;align-items:center;gap:14px">
          <label class="rd-switch-label">
            <el-switch v-model="schedule.autoSchedule" />
            <span>到点自动开放/结束</span>
          </label>
          <button class="btn btn-primary btn-sm" :disabled="savingSchedule" @click="saveSchedule">{{ savingSchedule ? '保存中…' : '保存时间设置' }}</button>
        </div>
      </div>
      <div class="rd-sched">
        <div v-for="s in schedule.rows" :key="s.roundNo" class="rd-sched-row" :class="{ current: s.roundNo === config.currentRound }">
          <div class="rd-round-badge" :class="{ current: s.roundNo === config.currentRound }">第{{ s.roundNo }}轮</div>
          <div class="rd-sched-fields">
            <el-date-picker v-model="s.openAt" type="datetime" placeholder="开放时间" format="MM-DD HH:mm" :clearable="true" />
            <span class="rd-arrow" v-html="svgIcon('arrow-right')"></span>
            <el-date-picker v-model="s.closeAt" type="datetime" placeholder="结束时间" format="MM-DD HH:mm" :clearable="true" />
          </div>
          <span class="rd-pill rd-pill-sm" :class="rowStatus(s).cls">{{ rowStatus(s).text }}</span>
        </div>
        <div class="rd-sched-tip" v-html="svgIcon('alert-triangle') + ' 开启自动排班后，系统每 30 秒巡检一次：到开放时间自动开放对应轮次，到结束时间自动关闭并进入下一轮。手动开关/切轮会退出自动模式。'"></div>
      </div>
    </div>

    <!-- ===== 错峰开放配置（按年级） ===== -->
    <div class="section">
      <div class="section-header">
        <div class="section-title" v-html="svgIcon('layers') + ' 错峰开放（按年级）'"></div>
        <button class="btn btn-primary btn-sm" :disabled="savingStagger" @click="saveStagger">{{ savingStagger ? '保存中…' : '保存错峰配置' }}</button>
      </div>
      <div class="rd-stagger">
        <div class="rd-stagger-tip" v-html="svgIcon('alert-triangle') + ' 仅第 1、2 轮积分选课生效：各年级在全局窗口内按各自时间错峰提交，统一截止后的排序/抽签结果不受影响。第三轮先到先得不错峰，全员同时开放。未配置的年级跟随全局开放时间。'"></div>
        <div v-if="unknownStudents > 0" class="rd-stagger-unknown">
          有 {{ unknownStudents }} 名学生尚未设置年级，这些学生不会参与错峰（跟随全局开放时间）。可在「用户管理」编辑用户补录年级，或要求学生在注册时使用含入学年份的学号（自动推导）。
        </div>
        <div v-for="rn in [1, 2]" :key="rn" class="rd-stagger-round">
          <div class="rd-stagger-round-head">
            <span class="rd-round-badge" :class="{ current: rn === config.currentRound }">第{{ rn }}轮</span>
            <button class="btn btn-sm" @click="addStaggerRow(rn)">+ 添加年级</button>
          </div>
          <div v-if="!staggerRows(rn).length" class="rd-stagger-empty">未配置错峰，本轮全部年级同时开放</div>
          <div v-for="row in staggerRows(rn)" :key="row._key" class="rd-stagger-row">
            <el-select v-model="row.grade" filterable allow-create default-first-option size="small" style="width:170px" placeholder="选择年级">
              <el-option v-for="g in gradeOptions" :key="g.grade" :label="g.count == null ? g.grade + ' 级' : g.grade + ' 级 · ' + g.count + ' 人'" :value="g.grade" />
            </el-select>
            <el-date-picker v-model="row.openAt" type="datetime" placeholder="该年级开放时间" format="MM-DD HH:mm" style="flex:1" />
            <button class="btn btn-danger btn-sm" @click="removeStaggerRow(row)">删除</button>
          </div>
        </div>
      </div>
    </div>

    <!-- ===== 手动控制 + 参数配置 ===== -->
    <div class="rd-grid">
      <div class="section">
        <div class="section-header"><div class="section-title" v-html="svgIcon('settings') + ' 手动控制'"></div></div>
        <div class="rd-manual">
          <div class="rd-manual-row">
            <div class="rd-manual-info">
              <b>当前状态：{{ config.roundOpen ? '选课开放中' : '选课已关闭' }}</b>
              <span>直接开/关本轮选课（第 {{ config.currentRound ?? '—' }} 轮）</span>
            </div>
            <button v-if="!config.roundOpen" class="btn btn-success btn-sm" @click="setRoundOpen(true)">开启本轮</button>
            <button v-else class="btn btn-danger btn-sm" @click="setRoundOpen(false)">关闭本轮</button>
          </div>
          <div class="rd-manual-row">
            <div class="rd-manual-info">
              <b>切换轮次</b>
              <span>直接跳转到指定轮次（1 ~ 3）</span>
            </div>
            <div class="rd-round-ctl">
              <el-input-number v-model="manualRound" :min="1" :max="3" size="small" />
              <button class="btn btn-primary btn-sm" @click="switchRound">切换</button>
            </div>
          </div>
        </div>
      </div>

      <div class="section">
        <div class="section-header">
          <div class="section-title" v-html="svgIcon('edit') + ' 参数配置'"></div>
          <button class="btn btn-primary btn-sm" :disabled="saving" @click="saveConfig">{{ saving ? '保存中…' : '保存配置' }}</button>
        </div>
        <div class="rd-form">
          <div class="rc-row">
            <label>本轮开放时间</label>
            <el-date-picker v-model="form.roundOpenAt" type="datetime" placeholder="选择开放时间" format="YYYY-MM-DD HH:mm" />
          </div>
          <div class="rc-row">
            <label>本轮关闭时间</label>
            <el-date-picker v-model="form.roundCloseAt" type="datetime" placeholder="选择关闭时间" format="YYYY-MM-DD HH:mm" />
          </div>
          <div class="rc-row">
            <label>选课阶段</label>
            <el-select v-model="form.currentPhase" style="width:220px">
              <el-option v-for="p in PHASE_OPTIONS" :key="p.value" :label="p.label" :value="p.value" />
            </el-select>
          </div>
          <div class="rc-row">
            <label>每学期学分上限</label>
            <el-input-number v-model="form.maxCreditPerSemester" :min="10" :max="60" />
          </div>
          <div class="rc-row">
            <label>候补确认时限</label>
            <el-input-number v-model="form.confirmMinutes" :min="5" :max="240" :step="5" />
            <span class="rd-unit">分钟</span>
          </div>
          <div class="rd-form-tip">开放/关闭时间与阶段写入轮次配置；学分上限与候补时限独立生效。</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api'
const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`

const PHASE_OPTIONS = [
  { value: 'auto',    label: '自动（按时间推导）' },
  { value: 'regular', label: '正常选课 regular' },
  { value: 'batch',   label: '批次选课 batch' },
  { value: 'reselect',label: '补选阶段 reselect' },
  { value: 'closed',  label: '已结束 closed' }
]
const PHASE_LABELS: Record<string, string> = { auto: '自动推导', regular: '正常选课', batch: '批次选课', reselect: '补选阶段', closed: '已结束' }
const phaseLabel = (v: any) => PHASE_LABELS[v] || (v ? String(v) : '—')

const config = ref<any>({})
const derivedPhase = ref<string>('')
const form = reactive<any>({ roundOpenAt: null, roundCloseAt: null, currentPhase: 'auto', maxCreditPerSemester: 24, confirmMinutes: 30 })
const manualRound = ref<number>(1)
const saving = ref(false)
const savingSchedule = ref(false)
const schedule = reactive<{ autoSchedule: boolean; rows: any[] }>({ autoSchedule: false, rows: [] })
const stagger = reactive<{ rows: any[] }>({ rows: [] })
const savingStagger = ref(false)
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
    form.confirmMinutes = config.value.confirmMinutes ?? 30
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
    if (!r.grade || !r.openAt) { ElMessage.warning('请为每条错峰配置填写年级和开放时间'); return }
    const key = r.roundNo + ':' + r.grade
    if (seen.has(key)) { ElMessage.warning(`第 ${r.roundNo} 轮的 ${r.grade} 级配置重复了`); return }
    seen.add(key)
  }
  savingStagger.value = true
  try {
    const r: any = await http.post('/system/stagger', stagger.rows.map(x => ({ roundNo: x.roundNo, grade: x.grade, openAt: iso(x.openAt) })))
    ElMessage.success(r?.message || '错峰配置已保存')
    await loadStagger()
    loadGradeStats()
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') }
  finally { savingStagger.value = false }
}

/* ---------- 倒计时 ---------- */
const countdownText = computed(() => {
  const n = now.value
  if (config.value.roundOpen && config.value.roundCloseAt) {
    const diff = new Date(config.value.roundCloseAt).getTime() - n
    if (diff > 0) return '距结束 ' + dur(diff)
  }
  if (!config.value.roundOpen && config.value.roundOpenAt) {
    const diff = new Date(config.value.roundOpenAt).getTime() - n
    if (diff > 0) return '距开放 ' + dur(diff)
  }
  return ''
})
function dur(ms: number): string {
  const s = Math.floor(ms / 1000)
  const d = Math.floor(s / 86400), h = Math.floor((s % 86400) / 3600), m = Math.floor((s % 3600) / 60), ss = s % 60
  if (d > 0) return `${d}天${h}小时`
  if (h > 0) return `${h}小时${m}分`
  return `${m}分${ss}秒`
}

/* ---------- 三轮时间窗 ---------- */
function rowStatus(s: any) {
  if (!s.openAt || !s.closeAt) return { text: '未设置', cls: 'rd-pill-gray' }
  const n = now.value, o = s.openAt.getTime(), c = s.closeAt.getTime()
  if (n < o) return { text: '未开始', cls: 'rd-pill-amber' }
  if (n <= c) return { text: '进行中', cls: 'rd-pill-green' }
  return { text: '已结束', cls: 'rd-pill-gray' }
}
function iso(d: any): string | null {
  if (!d) return null
  const x = new Date(d), p = (n: number) => String(n).padStart(2, '0')
  return `${x.getFullYear()}-${p(x.getMonth() + 1)}-${p(x.getDate())}T${p(x.getHours())}:${p(x.getMinutes())}:${p(x.getSeconds())}`
}
async function saveSchedule() {
  for (const s of schedule.rows) {
    if (s.openAt && s.closeAt && s.closeAt.getTime() <= s.openAt.getTime()) {
      ElMessage.warning(`第 ${s.roundNo} 轮的结束时间必须晚于开放时间`); return
    }
  }
  savingSchedule.value = true
  try {
    await http.post('/system/round-schedule', {
      autoSchedule: schedule.autoSchedule,
      schedules: schedule.rows.map(s => ({ roundNo: s.roundNo, openAt: iso(s.openAt), closeAt: iso(s.closeAt) }))
    })
    ElMessage.success('时间设置已保存')
    await load()
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') }
  finally { savingSchedule.value = false }
}

/* ---------- 手动控制 ---------- */
async function setRoundOpen(open: boolean) {
  try {
    await ElMessageBox.confirm(
      open ? `开启第 ${config.value.currentRound} 轮选课？学生将立即可以选课。` : `关闭第 ${config.value.currentRound} 轮选课？学生将无法继续选课。`,
      open ? '开启选课' : '关闭选课',
      { type: 'warning', confirmButtonText: open ? '开启' : '关闭', cancelButtonText: '取消' }
    )
    await http.post('/system/round-open', { roundOpen: open })
    ElMessage.success(open ? '本轮选课已开启' : '本轮选课已关闭')
    load()
  } catch (e: any) { if (e !== 'cancel') ElMessage.error(e?.message || '操作失败') }
}
async function switchRound() {
  if (manualRound.value === config.value.currentRound) { ElMessage.info('已经是第 ' + manualRound.value + ' 轮了'); return }
  try {
    await ElMessageBox.confirm(`切换到第 ${manualRound.value} 轮？手动切换将退出自动排班模式。`, '切换轮次',
      { type: 'warning', confirmButtonText: '切换', cancelButtonText: '取消' })
    await http.post('/system/round', { currentRound: manualRound.value })
    ElMessage.success('已切换到第 ' + manualRound.value + ' 轮')
    load()
  } catch (e: any) { if (e !== 'cancel') ElMessage.error(e?.message || '操作失败') }
}

/* ---------- 参数保存（分接口） ---------- */
async function saveConfig() {
  if (form.roundOpenAt && form.roundCloseAt && form.roundCloseAt.getTime() <= form.roundOpenAt.getTime()) {
    ElMessage.warning('关闭时间必须晚于开放时间'); return
  }
  saving.value = true
  const warns: string[] = []
  try {
    await http.post('/system/phase', {
      roundOpenAt: iso(form.roundOpenAt), roundCloseAt: iso(form.roundCloseAt), currentPhase: form.currentPhase
    })
  } catch (e: any) { warns.push('时间/阶段：' + (e?.message || '失败')) }
  try {
    await http.post('/system/max-credit', { maxCreditPerSemester: form.maxCreditPerSemester })
  } catch (e: any) { warns.push('学分上限：' + (e?.message || '失败') + '（需重启后端生效）') }
  try {
    await http.post('/system/confirm-minutes', { confirmMinutes: form.confirmMinutes })
  } catch (e: any) { warns.push('候补时限：' + (e?.message || '失败')) }
  if (warns.length) ElMessage.warning('部分保存失败：' + warns.join('；'))
  else ElMessage.success('配置已保存')
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
.rd-state-line { margin-bottom: 2px; }
.rd-pill { display: inline-flex; align-items: center; padding: 3px 12px; border-radius: 999px; font-size: 13px; font-weight: 600; }
.rd-pill-sm { padding: 2px 10px; font-size: 12px; }
.rd-pill-green { color: #059669; background: rgba(16,185,129,.12); border: 1px solid rgba(16,185,129,.3); }
.rd-pill-amber { color: #d97706; background: rgba(245,158,11,.12); border: 1px solid rgba(245,158,11,.3); }
.rd-pill-gray  { color: #64748b; background: rgba(100,116,139,.12); border: 1px solid rgba(100,116,139,.3); }
.rd-phase-value { font-size: 22px; }

/* 三轮录取机制卡片 */
.rd-rounds { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; }
@media (max-width: 960px) { .rd-rounds { grid-template-columns: 1fr; } }
.rd-round-card { display: flex; flex-direction: column; gap: 10px; padding: 16px 16px 18px;
  border-radius: 14px; border: 1px solid var(--border); background: color-mix(in srgb, var(--card-bg) 60%, transparent);
  position: relative; transition: all .25s; }
.rd-round-card:hover { border-color: rgba(99,102,241,.4); transform: translateY(-3px); box-shadow: 0 10px 26px -12px rgba(99,102,241,.35); }
.rd-round-card.current { border-color: rgba(99,102,241,.6); box-shadow: 0 0 0 3px rgba(99,102,241,.14); }
.rd-round-card.current::after { content: '当前轮次'; position: absolute; top: -10px; right: 14px; font-size: 11px; font-weight: 700;
  color: #fff; background: linear-gradient(135deg, #6366f1, #8b5cf6); padding: 3px 10px; border-radius: 999px; box-shadow: 0 4px 10px rgba(99,102,241,.35); }
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
.rd-pill-blue { color: #6366f1; background: rgba(99,102,241,.12); border: 1px solid rgba(99,102,241,.3); }

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
.rd-sched-row:hover { border-color: rgba(99,102,241,.4); }
.rd-sched-row.current { border-color: rgba(99,102,241,.55); box-shadow: 0 0 0 3px rgba(99,102,241,.12); }
.rd-round-badge { width: 52px; text-align: center; font-size: 13px; font-weight: 700; color: var(--text-light);
  background: rgba(100,116,139,.1); border-radius: 10px; padding: 8px 0; flex-shrink: 0; }
.rd-round-badge.current { color: #fff; background: linear-gradient(135deg, #6366f1, #8b5cf6); box-shadow: 0 4px 12px rgba(99,102,241,.35); }
.rd-sched-fields { display: flex; align-items: center; gap: 8px; flex: 1; }
.rd-arrow { color: var(--text-light); display: flex; }
.rd-arrow .icon { width: 14px; height: 14px; }
.rd-sched-tip { display: flex; align-items: flex-start; gap: 8px; font-size: 12.5px; color: var(--text-light);
  padding: 10px 14px; border-radius: 10px; background: rgba(245,158,11,.07); border: 1px dashed rgba(245,158,11,.35); line-height: 1.6; }
.rd-sched-tip .icon { width: 15px; height: 15px; flex-shrink: 0; margin-top: 2px; color: #d97706; }
.rd-switch-label { display: inline-flex; align-items: center; gap: 8px; font-size: 13px; color: var(--text-light); cursor: pointer; }

/* 错峰开放配置 */
.rd-stagger { display: flex; flex-direction: column; gap: 12px; }
.rd-stagger-tip { display: flex; align-items: flex-start; gap: 8px; font-size: 12.5px; color: var(--text-light);
  padding: 10px 14px; border-radius: 10px; background: rgba(99,102,241,.06); border: 1px dashed rgba(99,102,241,.35); line-height: 1.6; }
.rd-stagger-tip .icon { width: 15px; height: 15px; flex-shrink: 0; margin-top: 2px; color: #6366f1; }
.rd-stagger-round { display: flex; flex-direction: column; gap: 8px; padding: 12px 14px; border-radius: 12px;
  border: 1px solid var(--border); background: color-mix(in srgb, var(--card-bg) 60%, transparent); }
.rd-stagger-round-head { display: flex; align-items: center; gap: 10px; }
.rd-stagger-empty { font-size: 12.5px; color: var(--text-light); opacity: .8; padding: 4px 2px; }
.rd-stagger-unknown { font-size: 12.5px; color: var(--text-light); line-height: 1.6;
  padding: 8px 14px; border-radius: 10px; background: rgba(245,158,11,.08); border: 1px dashed rgba(245,158,11,.4); }
.rd-stagger-row { display: flex; align-items: center; gap: 10px; }

/* 双列区 */
.rd-grid { display: grid; grid-template-columns: 1fr 1.3fr; gap: 16px; margin-top: 16px; }
@media (max-width: 960px) { .rd-grid { grid-template-columns: 1fr; } }

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
</style>
