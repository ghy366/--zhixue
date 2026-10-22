<template>
  <div class="zx-page stats-page liquid">
    <!-- ===== 顶部栏 ===== -->
    <div class="page-head">
      <div class="page-title">
        我的课表
        <span class="page-sub">已录取课程的上课安排 · 选课结果确定后按此课表上课</span>
      </div>
      <el-radio-group v-model="period" size="default">
        <el-radio-button label="week">周视图</el-radio-button>
        <el-radio-button label="day">日视图</el-radio-button>
      </el-radio-group>
    </div>

    <!-- ===== 学期/周次 + 导出 ===== -->
    <div class="week-nav">
      <button class="wn-btn" @click="changeWeek(-1)" :disabled="week<=1">‹</button>
      <button ref="weekBtn" class="wn-center" @click="openWeekPicker">
        <span class="wn-sem">{{ semInfo?.name || '当前学期' }}</span>
        <span class="wn-sep">第</span>
        <span class="wn-week">{{ week }}</span>
        <span class="wn-sep">周</span>
        <span class="wn-chev">›</span>
      </button>
      <button class="wn-btn" @click="changeWeek(1)" :disabled="week>=18">›</button>
      <button class="wn-reset" @click="week = curWeek">🔄 重置</button>
      <button class="wn-export" @click="exportIcs">📅 导出日历</button>
    </div>

    <!-- ===== 周视图 ===== -->
    <div v-if="period === 'week'" class="schedule-grid">
      <div class="sg-header">
        <div class="sg-cell time-col">
          <div class="sg-month">{{ monthLabel }}</div>
        </div>
        <div v-for="d in weekDays" :key="d.key" class="sg-cell">
          <div class="sg-name">{{ d.name }}</div>
          <div class="sg-num" :class="{ today: d.isToday }">{{ d.num }}</div>
        </div>
      </div>
      <div v-for="(p, ti) in periods" :key="ti" class="sg-row">
        <div class="sg-cell time-col">
          <div class="ti-label">{{ ['一二节','三四节','五六节','七八节','九十节'][ti] }}</div>
          <div class="ti-time">{{ p.from }}</div>
        </div>
        <div v-for="d in weekDays" :key="d.key" class="sg-cell day-col">
          <div
            v-for="(c, idx) in cellClasses(d.key, p.key)"
            :key="idx"
            class="class-block"
            :style="{ background: c.bg }"
            @click="viewClass(c)"
          >
            <div class="cb-name">{{ c.name }}</div>
            <div class="cb-meta">{{ c.classroom }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- ===== 日视图 ===== -->
    <div v-else class="day-view">
      <div class="dv-date">{{ dayLabel }}</div>
      <div v-if="todayClasses.length" class="dv-list">
        <div v-for="(c, idx) in todayClasses" :key="idx" class="dv-card" :style="{ borderLeft: '4px solid ' + palette[idx % palette.length] }">
          <div class="dv-head">
            <div class="dv-name">{{ c.name }}</div>
            <div class="dv-time">{{ c.timeLabel }}</div>
          </div>
          <div class="dv-meta">{{ c.teacher }} · {{ c.classroom }}</div>
        </div>
      </div>
      <el-empty v-else description="今日无课" />
    </div>

    <!-- ===== 智能选课推荐 ===== -->
    <div class="section gen-section">
      <div class="section-header">
        <div class="section-title">🧠 智能选课推荐</div>
        <span class="section-sub">勾选想学的课程 → 系统推荐互不冲突的选课组合；🔒 锁定课程优先入选</span>
      </div>

      <div v-if="cands.length" class="cand-list">
        <div v-for="c in cands" :key="c.id" class="cand-row">
          <label class="cand-label">
            <input type="checkbox" class="cand-check" v-model="checkedIds" :value="c.id" />
            <span class="cand-name">{{ c.name }}</span>
          </label>
          <span class="cand-meta">{{ c.time || '' }} · {{ c.location || '' }} · {{ c.credit || 0 }} 学分</span>
          <span class="cand-campus">{{ c.campus || '主校区' }}</span>
          <button class="cand-lock" :class="{ locked: locks.has(c.id) }" @click="toggleLock(c.id)" :title="locks.has(c.id) ? '已锁定：优先入选' : '锁定：优先加入课表'">
            {{ locks.has(c.id) ? '🔒' : '🔓' }}
          </button>
        </div>

        <div class="cand-actions">
          <button class="btn btn-ghost btn-sm" @click="resetGen">🔄 重置</button>
          <button class="btn btn-primary btn-sm" :disabled="generating || !checkedIds.length" @click="generate">
            {{ generating ? '⏳ 生成中...' : '✨ 生成推荐方案' }}
          </button>
        </div>
      </div>
      <div v-else class="cand-empty">没有更多可选的候选课程 🎉</div>

      <div v-if="result" class="rec-panel">
        <div class="rec-head">
          ✨ 推荐方案：可加入 <b>{{ result.recommended.length }}</b> 门 ·
          合计 <b>{{ result.recommended.reduce((s,c)=>s+(c.credit||0),0) }}</b> 学分
          <span v-if="result.rejected.length" class="rec-rejected-tip">
            · {{ result.rejected.length }} 门因时间冲突排除
          </span>
        </div>
        <div class="data-table">
          <table>
            <thead><tr><th>课程</th><th>上课时间</th><th>学分</th><th>报名/容量</th><th>结果</th></tr></thead>
            <tbody>
              <tr v-for="c in result.recommended" :key="'ok-'+c.courseId">
                <td><b>{{ c.name }}</b></td>
                <td style="font-size:13px;color:var(--text-light)">{{ c.time || '' }}</td>
                <td>{{ c.credit || 0 }}</td>
                <td style="font-size:12px;color:var(--text-light)">{{ statOf(c.courseId) }}</td>
                <td><span class="status-tag status-approved">推荐选入</span></td>
              </tr>
              <tr v-for="id in result.rejected" :key="'no-'+id" class="rec-rejected">
                <td>{{ candMap.get(id)?.name || ('课程#'+id) }}</td>
                <td style="font-size:13px;color:var(--text-light)">{{ candMap.get(id)?.time || '' }}</td>
                <td>{{ candMap.get(id)?.credit || '' }}</td>
                <td style="font-size:12px;color:var(--text-light)">{{ statOf(id) }}</td>
                <td><span class="status-tag status-rejected">时间冲突</span></td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="rec-actions">
          <button v-if="result.recommended.length" class="btn btn-primary btn-sm" @click="addIntents">🛒 把推荐课程加入意向单</button>
        </div>
        <div class="rec-tip">推荐结果只是<strong>选课建议</strong>，不会自动选课；「报名/容量」可帮你判断竞争激烈程度。点「加入意向单」后，可在意向单页面调整投入积分并一键提交。</div>
      </div>
    </div>

    <!-- ===== 周次选择器（teleport到body避免被page容器截断） ===== -->
    <Teleport to="body">
    <div v-if="showWeekPicker" class="wk-overlay" @click.self="showWeekPicker = false">
      <div class="wk-sheet" :style="{ top: sheetPos.top + 'px', left: sheetPos.left + 'px' }">
        <div class="wk-sheet-head">
          <button class="wk-btn-text" @click="showWeekPicker = false">取消</button>
          <span class="wk-sheet-title">选择周次</span>
          <button class="wk-btn-text primary" @click="confirmWeekPicker">确认</button>
        </div>
        <div class="wk-body">
          <div class="wk-col">
            <div class="wk-col-title">学期</div>
            <div class="wk-opt active">{{ semInfo?.name || '当前学期' }}</div>
          </div>
          <div class="wk-col">
            <div class="wk-col-title">周次</div>
            <div class="wk-weeks">
              <div
                v-for="i in 18" :key="i"
                class="wk-opt"
                :class="{ active: i === weekSel, cur: i === curWeek }"
                @click="weekSel = i"
              >
                第{{ i }}周<span v-if="i === curWeek" class="wk-now">本周</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    </Teleport>

    <!-- ===== 课程详情弹窗 ===== -->
    <el-dialog v-model="showClass" :title="currentClass?.name" width="420px">
      <div v-if="currentClass" class="detail-body">
        <div><b>教师：</b>{{ currentClass.teacher }}</div>
        <div><b>教室：</b>{{ currentClass.classroom }}</div>
        <div><b>时间：</b>{{ currentClass.dayLabel }} 第 {{ currentClass.period }} 节</div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import http from '@/api'
import { ElMessage } from 'element-plus'

interface ScheduleItem { courseId: number; name: string; teacher: string; classroom: string; dayOfWeek: number; period: string; time?: string }
interface Course { id: number; name: string; time?: string; location?: string; credit?: number; campus?: string; teacher?: string; capacity?: number; stat?: { total?: number } }
interface SemInfo { name?: string; start?: string }
interface RecResult { recommended: Course[]; rejected: number[] }

const items = ref<ScheduleItem[]>([])
const period = ref<'week' | 'day'>('week')
const showClass = ref(false)
const currentClass = ref<ScheduleItem | null>(null)
const semInfo = ref<SemInfo | null>(null)
const curWeek = ref(1)
const week = ref(1)
const weekSel = ref(1)
const showWeekPicker = ref(false)
const weekBtn = ref<HTMLElement | null>(null)
const sheetPos = ref({ top: 0, left: 0 })

const cands = ref<Course[]>([])
const checkedIds = ref<number[]>([])
const locks = ref<Set<number>>(new Set())
const generating = ref(false)
const result = ref<RecResult | null>(null)
const candMap = new Map<number, Course>()

const WEEK0_DEFAULT = new Date(2026, 8, 7) // 2026-09-07
const palette = ['#16a34a', '#3b82f6', '#a855f7', '#f59e0b', '#ec4899', '#14b8a6', '#ef4444', '#6366f1']
const DAYS = ['日', '一', '二', '三', '四', '五', '六']
const periods = [
  { key: '1-2', from: '08:00', to: '09:40' },
  { key: '3-4', from: '10:00', to: '11:40' },
  { key: '5-6', from: '14:00', to: '15:40' },
  { key: '7-8', from: '16:00', to: '17:40' },
  { key: '9-10', from: '19:00', to: '20:40' },
]

function bgOf(name: string) {
  let h = 0
  for (let i = 0; i < name.length; i++) h = (h * 31 + name.charCodeAt(i)) >>> 0
  return palette[h % palette.length] + 'cc'
}
function cellClasses(day: number, periodKey: string) {
  return items.value.filter(c => c.dayOfWeek === day && c.period === periodKey)
    .map(c => ({ ...c, bg: bgOf(c.name) }))
}
const week0 = computed(() => {
  const s = semInfo.value
  if (s?.start) {
    const d = new Date(s.start)
    if (!isNaN(d.getTime())) return d
  }
  return WEEK0_DEFAULT
})
const mondayOfWeek = computed(() => {
  const m = new Date(week0.value)
  m.setDate(m.getDate() + (week.value - 1) * 7)
  return m
})
const monthLabel = computed(() => {
  const m = mondayOfWeek.value
  const next = new Date(m); next.setDate(next.getDate() + 6)
  if (m.getMonth() === next.getMonth()) return `${m.getMonth()+1}月`
  return `${m.getMonth()+1}月-${next.getMonth()+1}月`
})
const weekDays = computed(() => {
  const today = new Date()
  const curDow = today.getDay() || 7
  const arr = []
  for (let i = 0; i < 7; i++) {
    const dt = new Date(mondayOfWeek.value)
    dt.setDate(dt.getDate() + i)
    arr.push({
      key: i + 1,
      name: '周' + DAYS[(i + 1) % 7],
      num: dt.getDate(),
      isToday: dt.toDateString() === today.toDateString(),
    })
  }
  return arr
})
const todayClasses = computed(() => {
  const curDow = new Date().getDay() || 7
  return items.value.filter(c => c.dayOfWeek === curDow).map(c => ({
    ...c,
    timeLabel: (() => {
      const p = periods.find(pp => pp.key === c.period)
      return p ? `${p.from}-${p.to}` : ''
    })(),
  }))
})
const dayLabel = computed(() => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')} 周${DAYS[d.getDay()]}`
})

function openWeekPicker() {
  if (weekBtn.value) {
    const r = weekBtn.value.getBoundingClientRect()
    sheetPos.value = { top: r.bottom + 4, left: r.left }
  }
  weekSel.value = week.value
  showWeekPicker.value = true
}
function changeWeek(d: number) {
  const n = Math.min(18, Math.max(1, week.value + d))
  if (n !== week.value) week.value = n
}
function confirmWeekPicker() {
  week.value = weekSel.value
  showWeekPicker.value = false
}
function viewClass(c: any) { currentClass.value = c; showClass.value = true }
function statOf(id: number): string {
  const c = candMap.get(id)
  if (!c) return '—'
  return `${(c.stat?.total || 0)}/${(c.capacity || 0)}`
}
function toggleLock(id: number) {
  const s = locks.value
  if (s.has(id)) s.delete(id); else s.add(id)
  locks.value = new Set(s)
}

async function generate() {
  if (!checkedIds.value.length) { ElMessage.warning('请至少勾选一门候选课程'); return }
  generating.value = true
  try {
    const r: any = await http.post('/schedule/generate', {
      candidates: checkedIds.value,
      locks: [...locks.value].filter(id => checkedIds.value.includes(id)),
    })
    if (!r?.success) { ElMessage.error(r?.message || '生成失败'); return }
    const schedule = r.data?.schedule || []
    const addedIds = new Set(r.data?.added || [])
    const recommended = schedule.filter((c: any) => addedIds.has(c.courseId))
    result.value = { recommended, rejected: r.data?.rejected || [] }
    ElMessage[r.data?.rejected?.length ? 'warning' : 'success'](
      r.data?.rejected?.length
        ? `推荐方案：${recommended.length} 门可加入，${r.data.rejected.length} 门存在时间冲突`
        : `推荐方案：${recommended.length} 门互不冲突`
    )
  } catch (e: any) { ElMessage.error('生成失败：' + (e?.message || e)) }
  finally { generating.value = false }
}
function resetGen() {
  checkedIds.value = []
  locks.value = new Set()
  result.value = null
}
async function addIntents() {
  if (!result.value) return
  const token = localStorage.getItem('zhixue_token')
  let ok = 0
  for (const c of result.value.recommended) {
    try {
      await fetch('/api/intents', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
        body: JSON.stringify({ courseId: c.courseId }),
      })
      ok++
    } catch {}
  }
  ElMessage.success(ok ? `已把 ${ok} 门推荐课程加入意向单` : '加入失败')
}

function exportIcs() {
  if (!items.value.length) { ElMessage.error('暂无可导出的课表'); return }
  const pad = (n: number) => String(n).padStart(2, '0')
  const dtUTC = (d: Date, t: string) => {
    const [hh, mm] = (t || '00:00').split(':').map(Number)
    const dt = new Date(d); dt.setHours(hh, mm, 0, 0)
    return dt.toISOString().replace(/[-:]/g, '').slice(0, 15) + 'Z'
  }
  const lines = ['BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//Zhixue//Schedule//CN', 'CALSCALE:GREGORIAN']
  const seen = new Set<string>()
  items.value.forEach((c, idx) => {
    const key = `${c.courseId}-${c.dayOfWeek}-${c.period}`
    if (seen.has(key)) return; seen.add(key)
    const ti = periods.findIndex(p => p.key === c.period)
    if (ti < 0) return
    const start = periods[ti].from
    const end = periods[ti].to
    const day = new Date(mondayOfWeek.value)
    day.setDate(day.getDate() + c.dayOfWeek - 1)
    lines.push('BEGIN:VEVENT')
    lines.push('UID:zhixue-' + (c.courseId || idx) + '-' + c.dayOfWeek + '-' + c.period + '@zhixue.local')
    lines.push('DTSTAMP:' + new Date().toISOString().replace(/[-:]/g, '').slice(0, 15) + 'Z')
    lines.push('DTSTART:' + dtUTC(day, start))
    lines.push('DTEND:' + dtUTC(day, end))
    lines.push('SUMMARY:' + (c.name || '课程'))
    lines.push('LOCATION:' + (c.classroom || ''))
    lines.push('DESCRIPTION:' + (c.teacher ? '教师:' + c.teacher : '') + ' 每周重复，共16周')
    lines.push('RRULE:FREQ=WEEKLY;COUNT=16')
    lines.push('END:VEVENT')
  })
  lines.push('END:VCALENDAR')
  const blob = new Blob([lines.join('\r\n')], { type: 'text/calendar;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = 'zhixue-schedule.ics'
  document.body.appendChild(a); a.click(); a.remove()
  setTimeout(() => URL.revokeObjectURL(a.href), 2000)
  ElMessage.success('已导出 .ics 日历（16周重复），可用手机日历 / Outlook / Google 日历导入')
}

onMounted(async () => {
  // 1) 学期信息
  try {
    const semR: any = await http.get('/semesters/current')
    semInfo.value = semR?.data?.name ? { name: semR.data.name, start: semR.data.startDate } : null
  } catch { semInfo.value = null }

  // 2) 当前周次
  const today = new Date()
  const w0 = semInfo.value?.start ? new Date(semInfo.value.start) : WEEK0_DEFAULT
  curWeek.value = Math.min(18, Math.max(1, Math.floor((today.getTime() - w0.getTime()) / 86400000 / 7) + 1))
  week.value = curWeek.value
  weekSel.value = curWeek.value

  // 3) 我的课表
  try {
    const mineR: any = await http.get('/enrollments/mine')
    const mine = mineR?.data || []
    const courseIds = [...new Set(mine.map((e: any) => e.courseId))]
    const allR: any = await http.get('/courses')
    const allCourses = allR?.data || []
    items.value = allCourses
      .filter((c: any) => courseIds.includes(c.id))
      .flatMap((c: any) => parseSchedule(c))

    // 4) 智能推荐候选（必修课已自动排入课表，不再作为候选）
    const curIds = new Set(courseIds)
    cands.value = allCourses.filter((c: any) =>
      !curIds.has(c.id) && !(c.category || '').includes('必修')
    )
    cands.value.forEach(c => candMap.set(c.id, c))
    checkedIds.value = cands.value.map(c => c.id)
  } catch {
    ElMessage.error('课表数据加载失败（服务可能刚重启），请刷新页面重试')
  }
})

function parseSchedule(course: any): ScheduleItem[] {
  const WEEK_MAP: Record<string, number> = {
    '周一': 1, '周二': 2, '周三': 3, '周四': 4, '周五': 5, '周六': 6, '周日': 7,
  }
  function timeToPeriod(raw: string): string | null {
    const hh = raw.split(':')[0]
    const h = parseInt(hh, 10)
    if (h === 8)  return '1-2'
    if (h === 10) return '3-4'
    if (h === 14) return '5-6'
    if (h === 16) return '7-8'
    if (h === 19) return '9-10'
    return null
  }
  const raw: string = course.time || course.schedule || ''
  if (!raw) return []
  const out: ScheduleItem[] = []
  const re = /(周[一二三四五六日天])\s*(\d{1,2}:\d{2})\s*-\s*(\d{1,2}:\d{2})/g
  let m
  while ((m = re.exec(raw))) {
    const dayKey = m[1]
    const from = m[2]
    const day = WEEK_MAP[dayKey]
    const period = timeToPeriod(from)
    if (day && period) {
      out.push({
        courseId: course.id,
        name: course.name,
        teacher: course.teacherName || course.teacher,
        classroom: course.classroom || course.location,
        dayOfWeek: day,
        period,
        time: raw,
      })
    }
  }
  return out
}
</script>

<style scoped>
.zx-page { display: flex; flex-direction: column; gap: 14px; }
.page-head { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 10px; }
.page-title { font-size: 20px; font-weight: 700; color: var(--text); }
.page-sub { display: block; font-size: 12px; color: var(--text-light); font-weight: 400; margin-top: 2px; }

/* ===== 周次导航 ===== */
.week-nav {
  display: flex; align-items: center; gap: 8px;
  background: var(--card); border: 1px solid var(--border);
  border-radius: 10px; padding: 8px 12px; box-shadow: var(--shadow);
}
.wn-btn {
  width: 32px; height: 32px; border-radius: 8px; border: 1px solid var(--border);
  background: var(--bg); color: var(--text); font-size: 18px; line-height: 1;
  cursor: pointer; display: flex; align-items: center; justify-content: center;
  transition: all .15s;
}
.wn-btn:hover:not(:disabled) { border-color: var(--primary); color: var(--primary); }
.wn-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.wn-center {
  display: flex; align-items: center; gap: 4px;
  padding: 6px 18px; border-radius: 8px;
  background: linear-gradient(135deg, rgba(99,102,241,0.15), rgba(168,85,247,0.15));
  border: 1px solid rgba(99,102,241,0.3);
  cursor: pointer; transition: all .15s; color: var(--text);
  font-size: 14px;
}
.wn-center:hover { border-color: var(--primary); box-shadow: 0 0 10px rgba(99,102,241,0.25); }
.wn-sem { font-weight: 600; color: var(--primary); }
.wn-sep { color: var(--text-light); }
.wn-week { font-weight: 700; color: var(--primary); font-size: 16px; }
.wn-chev { color: var(--text-light); margin-left: 4px; }
.wn-reset {
  padding: 6px 14px; border-radius: 8px; border: 1px solid var(--border);
  background: var(--bg); color: var(--text-light); font-size: 13px; cursor: pointer;
  transition: all .15s;
}
.wn-reset:hover { border-color: var(--primary); color: var(--primary); }
.wn-export {
  padding: 6px 14px; border-radius: 8px; border: none;
  background: linear-gradient(135deg, #6366f1, #a855f7);
  color: #fff; font-size: 13px; font-weight: 500; cursor: pointer;
  transition: all .15s; margin-left: auto;
}
.wn-export:hover { transform: translateY(-1px); box-shadow: 0 4px 12px rgba(99,102,241,0.4); }

/* ===== 课表网格 ===== */
.schedule-grid { background: var(--card); border: 1px solid var(--grid-line); border-radius: 12px; overflow: hidden; }
.sg-header { display: grid; grid-template-columns: 80px repeat(7, 1fr); border-bottom: 1px solid var(--grid-line); }
.sg-row { display: grid; grid-template-columns: 80px repeat(7, 1fr); border-top: 1px solid var(--grid-line); }
.sg-cell { border-right: 1px solid var(--grid-line); padding: 6px; min-height: 64px; display: flex; flex-direction: column; align-items: center; justify-content: flex-start; gap: 4px; }
.sg-cell.time-col { justify-content: center; background: var(--bg); }
.sg-cell:last-child { border-right: none; }
.sg-name { font-size: 12px; color: var(--text-light); }
.sg-num { font-size: 14px; font-weight: 700; color: var(--text); }
.sg-num.today { color: #fff; background: var(--primary); border-radius: 50%; width: 24px; height: 24px; display: flex; align-items: center; justify-content: center; }
.sg-month { font-size: 12px; color: var(--green-text); font-weight: 600; }
.ti-label { font-size: 12px; font-weight: 600; color: var(--green-text); }
.ti-time { font-size: 10px; color: var(--text-light); }
.class-block { width: 100%; border-radius: 6px; padding: 5px 7px; cursor: pointer; color: #fff; text-align: left; transition: transform .1s; margin-top: 2px; }
.class-block:hover { transform: scale(1.02); }
.cb-name { font-size: 11px; font-weight: 600; line-height: 1.2; }
.cb-meta { font-size: 9px; opacity: 0.9; margin-top: 1px; }

/* ===== 日视图 ===== */
.day-view { display: flex; flex-direction: column; gap: 10px; }
.dv-date { font-size: 16px; color: var(--green-text); font-weight: 600; }
.dv-list { display: flex; flex-direction: column; gap: 10px; }
.dv-card { background: var(--card); border: 1px solid var(--border); border-radius: 8px; padding: 12px 16px; border-left-width: 4px; }
.dv-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.dv-name { font-size: 15px; color: var(--text); font-weight: 600; }
.dv-time { font-size: 13px; color: var(--green-text); }
.dv-meta { font-size: 12px; color: var(--text-light); }

/* ===== 周次选择器 ===== */
.wk-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.35);
  z-index: 2000;
}
.wk-sheet {
  position: fixed; width: 360px;
  background: var(--card); border: 1px solid var(--border);
  border-radius: 12px; padding: 12px 0 18px;
  box-shadow: 0 8px 30px rgba(0,0,0,0.2);
  animation: wkDrop .18s ease;
}
@keyframes wkDrop { from { transform: translateY(-6px); opacity: 0.4; } to { transform: translateY(0); opacity: 1; } }
.wk-sheet-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.wk-sheet-title { font-size: 15px; font-weight: 700; color: var(--text); }
.wk-btn-text { background: none; border: none; font-size: 14px; color: var(--text-light); cursor: pointer; }
.wk-btn-text.primary { color: var(--primary); font-weight: 600; }
.wk-body { display: flex; gap: 16px; }
.wk-col { flex: 1; }
.wk-col-title { font-size: 13px; color: var(--text-light); margin-bottom: 8px; }
.wk-weeks { display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; }
.wk-opt {
  padding: 8px 6px; border-radius: 8px; border: 1px solid var(--border);
  text-align: center; font-size: 13px; color: var(--text); cursor: pointer;
  background: var(--bg); transition: all .15s; position: relative;
}
.wk-opt:hover { border-color: var(--primary); }
.wk-opt.active { background: var(--primary); color: #fff; border-color: var(--primary); }
.wk-opt.cur::after { content: '本周'; position: absolute; top: -6px; right: -4px; font-size: 10px; background: #f59e0b; color: #fff; padding: 1px 5px; border-radius: 6px; }
.wk-now { display: none; }

/* ===== 智能选课推荐 ===== */
.gen-section { margin-top: 20px; }
.section-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; margin-bottom: 10px; }
.section-title { font-size: 15px; font-weight: 700; color: var(--text); }
.section-sub { font-size: 12px; color: var(--text-light); }
.cand-list { background: var(--card); border: 1px solid var(--border); border-radius: 12px; max-height: 340px; overflow-y: auto; }
.cand-row { display: flex; align-items: center; gap: 12px; padding: 10px 14px; border-bottom: 1px dashed var(--border); font-size: 13px; flex-wrap: wrap; }
.cand-row:last-child { border-bottom: none; }
.cand-row:hover { background: var(--bg); }
.cand-label { display: flex; align-items: center; gap: 8px; flex: 1; min-width: 0; cursor: pointer; }
.cand-check { width: 16px; height: 16px; accent-color: var(--primary); cursor: pointer; flex-shrink: 0; }
.cand-name { font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 220px; }
.cand-meta { color: var(--text-light); font-size: 12px; flex: 1; min-width: 140px; }
.cand-campus { background: var(--primary-bg); color: var(--primary); padding: 2px 10px; border-radius: 10px; font-size: 12px; white-space: nowrap; }
.cand-lock { width: 30px; height: 30px; border-radius: 8px; display: flex; align-items: center; justify-content: center; background: var(--bg); border: 1px solid var(--border); font-size: 15px; cursor: pointer; transition: all .15s; flex-shrink: 0; }
.cand-lock:hover { border-color: var(--primary); transform: scale(1.08); }
.cand-lock.locked { background: #fef3c7; border-color: #f59e0b; box-shadow: 0 0 8px rgba(245,158,11,0.4); }
.cand-actions { display: flex; justify-content: flex-end; gap: 10px; padding: 12px 14px; border-top: 1px solid var(--border); background: var(--card); position: sticky; bottom: 0; }
.cand-empty { padding: 28px; text-align: center; color: var(--text-light); font-size: 14px; }
.rec-panel { margin-top: 14px; background: var(--card); border: 1px solid var(--border); border-radius: 12px; padding: 16px; }
.rec-head { font-size: 14px; color: var(--text); font-weight: 600; margin-bottom: 10px; }
.rec-head b { color: var(--primary); }
.rec-rejected-tip { color: var(--danger) !important; }
.rec-actions { margin-top: 12px; display: flex; gap: 10px; }
.rec-tip { margin-top: 10px; font-size: 12px; color: var(--text-light); line-height: 1.6; }
.rec-tip strong { color: var(--text); }
.rec-rejected { opacity: 0.55; }
.data-table { overflow-x: auto; }
.data-table table { width: 100%; border-collapse: collapse; font-size: 13px; }
.data-table th, .data-table td { padding: 8px 12px; text-align: left; border-bottom: 1px solid var(--border); }
.data-table th { background: var(--bg); color: var(--text-light); font-weight: 600; }

.status-tag { padding: 2px 10px; border-radius: 10px; font-size: 12px; font-weight: 500; }
.status-approved { background: rgba(34,197,94,0.15); color: #22c55e; }
.status-rejected { background: rgba(239,68,68,0.15); color: #ef4444; }

.btn { padding: 6px 14px; border-radius: 8px; font-size: 13px; font-weight: 500; cursor: pointer; transition: all .15s; border: 1px solid transparent; }
.btn-primary { background: var(--primary); color: #fff; }
.btn-primary:hover:not(:disabled) { background: var(--primary-dark); }
.btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-ghost { background: transparent; border-color: var(--border); color: var(--text-light); }
.btn-ghost:hover { border-color: var(--primary); color: var(--primary); }
.btn-sm { padding: 5px 12px; font-size: 12px; }

.detail-body { display: flex; flex-direction: column; gap: 8px; font-size: 14px; color: var(--text); }
.detail-body b { color: var(--green-text); margin-right: 6px; }
</style>
