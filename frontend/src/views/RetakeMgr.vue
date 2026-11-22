<template>
  <div class="page-wrap rm-page">
    <!-- ===== 顶部 sticky 导航栏 ===== -->
    <header class="rt-topbar">
      <div class="rt-brand">
        <div class="rt-brand-icon">
          <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 12a9 9 0 1 0 3-6.7L3 8"/><path d="M3 3v5h5"/></svg>
        </div>
        <div class="rt-brand-text">
          <div class="rt-title">{{ t('重修管理') }}</div>
          <div class="rt-subtitle">{{ t('共 {n} 条报名 · {c} 个教学班', { n: rows.length, c: classes.length }) }}</div>
        </div>
      </div>
      <button class="rt-search-trigger" @click="openCommand" :aria-label="t('打开搜索')">
        <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>
        <span class="rt-search-ph">{{ filters.keyword || t('搜索学号 / 姓名 / 课程，支持拼音首字母…') }}</span>
        <kbd class="rt-kbd">{{ isMac ? '⌘' : 'Ctrl+' }} K</kbd>
      </button>
      <div class="rt-top-actions">
        <button class="rt-btn rt-btn-outline" @click="load">
          <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12a9 9 0 1 1-3-6.7L21 8"/><path d="M21 3v5h-5"/></svg>
          <span>{{ t('刷新') }}</span>
        </button>
      </div>
    </header>

    <!-- 筛选轨道 chips：状态 / 形式，读写同一个 filters -->
    <div class="rt-filtercard">
      <div class="rt-chip-row">
        <div v-for="chip in chipDefs" :key="chip.key" class="rt-chip-wrap">
          <button class="rt-chip" :class="{ on: chip.active }" @click="toggleChip(chip.key)">
            <span class="rt-chip-label">{{ chip.label }}</span>
            <span v-if="chip.value" class="rt-chip-value">{{ chip.value }}</span>
            <svg class="ic rt-chip-arrow" :class="{ open: openChip === chip.key }" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="m6 9 6 6 6-6"/></svg>
          </button>
          <div v-if="openChip === chip.key" class="rt-dropdown" @click.stop>
            <button class="rt-opt" :class="{ on: !chip.current }" @click="chip.clear(); openChip = null">
              <span>{{ t('全部') }}{{ chip.label }}</span><span v-if="!chip.current" class="rt-opt-check">✓</span>
            </button>
            <button v-for="o in chip.options" :key="String(o.value)" class="rt-opt" :class="{ on: chip.current === o.value }" @click="chip.set(o.value); openChip = null">
              <span>{{ o.label }}<span v-if="o.count !== undefined" class="rt-opt-n">{{ o.count }}</span></span>
              <span v-if="chip.current === o.value" class="rt-opt-check">✓</span>
            </button>
          </div>
        </div>
        <button v-if="hasFilter" class="rt-reset" @click="clearFilters">{{ t('清空') }}</button>
      </div>
    </div>

    <div class="rm-tabs">
      <button class="tab-btn" :class="{ active: tab === 'summary' }" @click="tab = 'summary'">{{ t('报名汇总') }}</button>
      <button class="tab-btn" :class="{ active: tab === 'detail' }" @click="tab = 'detail'">{{ t('明细与定形式') }}</button>
      <button class="tab-btn" :class="{ active: tab === 'classes' }" @click="tab = 'classes'">{{ t('教学班') }}</button>
    </div>

    <!-- 开班规则与费用（真实华厦规则） -->
    <div class="rm-rules">
      <b>开班规则：</b>单门课报名 ≥{{ summary[0]?.groupThreshold ?? 30 }} 人单独开班、安排教师授课；&lt;{{ summary[0]?.groupThreshold ?? 30 }} 人每门课配一名专任教师负责，默认按「自主学习（学习通线上平台，教师发通知、布置学习）」方式处理。
      <b>重修考试：</b>第 16 周（教务系统单独「重修考试」环节）。
      <b>费用：</b>200 元/学分（依据华厦教字〔2017〕8号），缴费后原则上不退费，报名前需考虑清楚。
      <b>毕业班通道：</b>毕业班/结业生可申请开放所有学期的课程；普通在校生只能修当学期开设的课程。
    </div>

    <!-- 汇总：课程 × 形式，达到门槛可组班 -->
    <div v-if="tab === 'summary'" class="data-table">
      <table>
        <thead>
          <tr>
            <th>{{ t('课程') }}</th><th>{{ t('报名总数') }}</th><th>{{ t('待报名') }}</th><th>{{ t('已报名') }}</th>
            <th>{{ t('组班') }}</th><th>{{ t('跟班') }}</th><th>{{ t('自修') }}</th><th>{{ t('未定形式') }}</th>
            <th>{{ t('组班门槛') }}</th><th>{{ t('操作') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="s in summary" :key="s.courseId">
            <td>{{ s.courseName }}</td>
            <td><b>{{ s.total }}</b></td>
            <td>{{ s.eligible }}</td>
            <td>{{ s.applied }}</td>
            <td>{{ s.group }}</td>
            <td>{{ s.follow }}</td>
            <td>{{ s.self }}</td>
            <td>{{ s.unset }}</td>
            <td>
              {{ s.groupThreshold }}
              <span v-if="s.canGroup" class="rm-ok">{{ t('可组班') }}</span>
            </td>
            <td>
              <button class="btn btn-primary btn-sm" :disabled="!s.total" @click="openGroup(s)">{{ t('一键组班') }}</button>
            </td>
          </tr>
          <tr v-if="!summary.length"><td colspan="10" class="rm-empty">{{ t('暂无重修报名') }}</td></tr>
        </tbody>
      </table>
    </div>

    <!-- 明细 -->
    <div v-else-if="tab === 'detail'" class="data-table">
      <table>
        <thead>
          <tr>
            <th>{{ t('学号') }}</th><th>{{ t('姓名') }}</th><th>{{ t('学院') }}</th><th>{{ t('年级') }}</th>
            <th>{{ t('课程') }}</th><th>{{ t('状态') }}</th>
            <th>{{ t('形式') }}</th><th>{{ t('教学班') }}</th><th>{{ t('成绩') }}</th><th>{{ t('费用（元）') }}</th><th>{{ t('次数') }}</th><th>{{ t('操作') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in pagedDetail" :key="r.id">
            <td>{{ r.studentNo }}</td>
            <td>{{ r.studentName }}</td>
            <td>{{ r.college || '—' }}</td>
            <td>{{ r.grade ? t('{n} 级', { n: r.grade }) : '—' }}</td>
            <td>{{ r.courseName }}</td>
            <td><span class="rm-tag" :class="r.status">{{ r.statusText }}</span></td>
            <td>{{ r.formText }}</td>
            <td>
              <span v-if="r.teachingClassNo" class="rm-class">{{ r.teachingClassNo }}</span>
              <span v-else>{{ r.targetClassName || '—' }}</span>
              <small v-if="r.teachingClassTime" class="rm-time">{{ r.teachingClassTime }}</small>
            </td>
            <td>{{ r.score ?? '—' }}</td>
            <td class="rm-fee">{{ r.fee ?? '—' }}</td>
            <td>{{ r.attemptNo ?? 1 }}</td>
            <td>
              <button v-if="!['PASSED','FAILED','CANCELLED'].includes(r.status)"
                      class="btn btn-outline btn-sm" @click="openForm(r)">{{ t('定形式') }}</button>
              <span v-else class="muted">—</span>
            </td>
          </tr>
          <tr v-if="!filtered.length"><td colspan="12">
            <div class="rt-empty">
              <div class="rt-empty-title">{{ t('暂无重修明细') }}</div>
              <div v-if="suggestChips.length" class="rt-suggest">
                <span class="rt-suggest-label">{{ t('是不是想找') }}</span>
                <button v-for="s in suggestChips" :key="s" class="rt-suggest-chip" @click="filters.keyword = s">{{ s }}</button>
              </div>
              <button v-if="hasFilter" class="rt-empty-reset" @click="clearFilters">{{ t('清空筛选') }}</button>
            </div>
          </td></tr>
        </tbody>
      </table>
      <div v-if="filtered.length > pageSize" class="rt-pagebar">
        <span class="rt-pageinfo">{{ t('共 {n} 条 · 第 {p} / {total} 页', { n: filtered.length, p: page, total: detailPages }) }}</span>
        <div class="rt-pager">
          <button class="rt-pg-btn" :disabled="page <= 1" @click="page--">‹</button>
          <button v-for="p in detailPageItems" :key="p" class="rt-pg-btn" :class="{ active: p === page }" @click="page = p">{{ p }}</button>
          <button class="rt-pg-btn" :disabled="page >= detailPages" @click="page++">›</button>
        </div>
      </div>
    </div>

    <!-- 教学班：组班开出的真实班级 -->
    <div v-else class="data-table">
      <table>
        <thead>
          <tr>
            <th>{{ t('教学班号') }}</th><th>{{ t('课程') }}</th><th>{{ t('授课教师') }}</th><th>{{ t('上课时间') }}</th>
            <th>{{ t('地点') }}</th><th>{{ t('人数/容量') }}</th><th>{{ t('状态') }}</th><th>{{ t('操作') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="c in classes" :key="c.id">
            <td><b>{{ c.classNo }}</b></td>
            <td>{{ c.courseName }}</td>
            <td>{{ c.teacherName || '—' }}</td>
            <td>{{ c.time || t('待排课') }}</td>
            <td>{{ c.location || '—' }}</td>
            <td>{{ c.memberCount }} / {{ c.capacity ?? '—' }}</td>
            <td><span class="rm-tag" :class="c.status">{{ c.statusText }}</span></td>
            <td class="rm-ops">
              <button class="btn btn-outline btn-sm" @click="openMembers(c)">{{ t('名单') }}</button>
              <button class="btn btn-outline btn-sm" :disabled="c.status === 'CLOSED'" @click="openSchedule(c)">{{ t('排课') }}</button>
              <button class="btn btn-danger btn-sm" :disabled="c.status === 'CLOSED'" @click="closeClass(c)">{{ t('停开') }}</button>
            </td>
          </tr>
          <tr v-if="!classes.length"><td colspan="8" class="rm-empty">{{ t('暂无教学班，请在「报名汇总」里一键组班') }}</td></tr>
        </tbody>
      </table>
    </div>

    <!-- 定形式 -->
    <el-dialog v-model="dialogOpen" :title="t('设置重修形式')" width="470px" class="rm-dialog">
      <div v-if="current" class="rm-form">
        <p class="rm-who">{{ current.studentName }} · {{ current.courseName }}</p>
        <label>{{ t('形式') }}
          <el-select v-model="form.form" style="width:100%">
            <el-option :label="t('组班（集中开班）')" value="GROUP" />
            <el-option :label="t('跟班（插入现有教学班）')" value="FOLLOW" />
            <el-option :label="t('自主学习（学习通线上，专任教师负责）')" value="SELF" />
          </el-select>
        </label>
        <label v-if="form.form === 'FOLLOW'">{{ t('跟班教学班') }}
          <el-select v-model="form.targetClassId" style="width:100%" :placeholder="t('选择同学期同课程教学班')">
            <el-option v-for="c in classes2" :key="c.courseId" :value="c.courseId"
                       :label="`${c.name} · ${c.teacherName} · ${c.time || t('时间待定')}`" />
          </el-select>
          <small class="rm-note">{{ t('跟班后系统会自动写入该生的课表（0 积分，不占普通选课名额）') }}</small>
        </label>
        <small v-if="form.form === 'GROUP'" class="rm-note">{{ t('组班需在「报名汇总」里一键开班并排课，开班后学生会自动进班') }}</small>
      </div>
      <template #footer>
        <button class="btn btn-ghost btn-sm" @click="dialogOpen = false">{{ t('取消') }}</button>
        <button class="btn btn-primary btn-sm" @click="saveForm">{{ t('保存') }}</button>
      </template>
    </el-dialog>

    <!-- 一键组班：建班 + 排课 -->
    <el-dialog v-model="groupOpen" :title="t('一键组班 · 开班排课')" width="520px" class="rm-dialog">
      <div v-if="group" class="rm-form">
        <p class="rm-who">{{ group.courseName }} · {{ t('{n} 条重修记录', { n: group.total }) }}</p>
        <label>{{ t('授课教师') }}
          <el-select v-model="gform.teacherId" style="width:100%" clearable :placeholder="t('默认沿用原课程教师')">
            <el-option v-for="tt in teachers" :key="tt.id" :value="tt.id" :label="`${tt.name}（${tt.college || t('教师')}）`" />
          </el-select>
        </label>
        <label>{{ t('上课时间') }}
          <el-input v-model="gform.time" :placeholder="t('如 周六 08:00-09:40 / 周六 10:00-11:40（留空沿用原课程时间）')" />
        </label>
        <label>{{ t('上课地点') }}
          <el-select v-model="gform.location" filterable allow-create default-first-option clearable
                     popper-class="rm-drop" :placeholder="t('从教室管理中选择，或留空沿用原课程地点')" style="width:100%">
            <el-option v-for="c in classrooms" :key="c.id" :value="c.name"
                       :label="`${c.name}（${c.capacity ?? '—'} 座）`" />
          </el-select>
        </label>
        <label>{{ t('容量') }}
          <el-input v-model.number="gform.capacity" type="number" :placeholder="t('默认 30 人')" />
        </label>
        <small class="rm-note">{{ t('开班后：教学班号自动生成，已报名学生会进班并写入课表；同一课程已有开班中的班级时直接追加。') }}</small>
      </div>
      <template #footer>
        <button class="btn btn-ghost btn-sm" @click="groupOpen = false">{{ t('取消') }}</button>
        <button class="btn btn-primary btn-sm" :disabled="grouping" @click="submitGroup">
          {{ grouping ? t('开班中…') : t('确认开班') }}
        </button>
      </template>
    </el-dialog>

    <!-- 排课 -->
    <el-dialog v-model="scheduleOpen" :title="t('教学班排课 · {n}', { n: scheduleTarget?.classNo || '' })" width="500px" class="rm-dialog">
      <div v-if="scheduleTarget" class="rm-form">
        <p class="rm-who">{{ scheduleTarget.courseName }}</p>
        <label>{{ t('授课教师') }}
          <el-select v-model="sform.teacherId" style="width:100%" clearable>
            <el-option v-for="tt in teachers" :key="tt.id" :value="tt.id" :label="tt.name" />
          </el-select>
        </label>
        <label>{{ t('上课时间') }}<el-input v-model="sform.time" :placeholder="t('如 周六 08:00-09:40 / 周六 10:00-11:40')" /></label>
        <label>{{ t('上课地点') }}
          <el-select v-model="sform.location" filterable allow-create default-first-option clearable
                     popper-class="rm-drop" :placeholder="t('从教室管理中选择')" style="width:100%">
            <el-option v-for="c in classrooms" :key="c.id" :value="c.name"
                       :label="`${c.name}（${c.capacity ?? '—'} 座）`" />
          </el-select>
        </label>
        <label>{{ t('容量') }}<el-input v-model.number="sform.capacity" type="number" /></label>
        <small class="rm-note">{{ t('保存后，已进班学生的课表会立即同步新的时间与地点。') }}</small>
      </div>
      <template #footer>
        <button class="btn btn-ghost btn-sm" @click="scheduleOpen = false">{{ t('取消') }}</button>
        <button class="btn btn-primary btn-sm" @click="saveSchedule">{{ t('保存排课') }}</button>
      </template>
    </el-dialog>

    <!-- 名单 -->
    <el-dialog v-model="membersOpen" :title="t('教学班名单 · {n}', { n: membersTarget?.classNo || '' })" width="620px" class="rm-dialog">
      <div class="data-table rm-mini">
        <table>
          <thead><tr><th>{{ t('学号') }}</th><th>{{ t('姓名') }}</th><th>{{ t('班级') }}</th><th>{{ t('状态') }}</th><th>{{ t('成绩') }}</th></tr></thead>
          <tbody>
            <tr v-for="m in members" :key="m.retakeId">
              <td>{{ m.studentNo }}</td><td>{{ m.studentName }}</td><td>{{ m.studentClass || '—' }}</td>
              <td>{{ m.statusText }}</td><td>{{ m.score ?? '—' }}</td>
            </tr>
            <tr v-if="!members.length"><td colspan="5" class="rm-empty">{{ t('暂无学生') }}</td></tr>
          </tbody>
        </table>
      </div>
    </el-dialog>

    <!-- ================= 命令面板 ================= -->
    <Teleport to="body">
      <div v-if="cmdOpen" class="cmd-overlay" @click="closeCommand(false)">
        <div class="cmd-panel" @click.stop>
          <div class="cmd-input-row">
            <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>
            <input ref="cmdInput" v-model="cmdTerm" :placeholder="t('搜索学号 / 姓名 / 课程，支持拼音首字母…')" />
            <kbd class="rt-kbd">esc</kbd>
          </div>
          <div class="cmd-results">
            <template v-if="cmdItems.length">
              <div v-if="cmdRows.length" class="cmd-group">
                <div class="cmd-group-h">{{ t('重修记录') }} <span>{{ cmdRows.length }}</span></div>
                <button v-for="(r, i) in cmdRows" :key="r.id" class="cmd-item" :class="{ active: cmdCursor === i }" @mouseenter="cmdCursor = i" @click="selectCmd(cmdItems[i])">
                  <span class="cmd-item-ico user"><svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/></svg></span>
                  <span class="cmd-item-main"><b>{{ r.studentName }} · {{ r.courseName }}</b><small>{{ r.studentNo }} · {{ r.statusText }} · {{ r.college || '—' }}</small></span>
                  <span class="cmd-enter" v-if="cmdCursor === i">↵</span>
                </button>
              </div>
              <div v-if="cmdCourses.length" class="cmd-group">
                <div class="cmd-group-h">{{ t('课程') }} <span>{{ cmdCourses.length }}</span></div>
                <button v-for="(co, j) in cmdCourses" :key="co.courseId" class="cmd-item" :class="{ active: cmdCursor === cmdRows.length + j }" @mouseenter="cmdCursor = cmdRows.length + j" @click="selectCmd(cmdItems[cmdRows.length + j])">
                  <span class="cmd-item-ico org"><svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M4 4.5A2.5 2.5 0 0 1 6.5 2H20v15H6.5A2.5 2.5 0 0 0 4 19.5z"/></svg></span>
                  <span class="cmd-item-main"><b>{{ co.courseName }}</b><small>{{ co.total }} {{ t('人报名') }}</small></span>
                  <span class="cmd-enter" v-if="cmdCursor === cmdRows.length + j">↵</span>
                </button>
              </div>
            </template>
            <div v-else class="cmd-empty">{{ t('没有联想结果，试试姓名 / 学号 / 课程 / 拼音首字母') }}</div>
          </div>
          <div class="cmd-footer">
            <span><kbd class="rt-kbd">↑</kbd><kbd class="rt-kbd">↓</kbd> {{ t('导航') }}</span>
            <span><kbd class="rt-kbd">↵</kbd> {{ t('选中') }}</span>
            <span class="cmd-footer-tip">{{ t('选中记录看详情 · 选中课程看汇总') }}</span>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, toRefs, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api'
import { t } from '@/i18n'
import { diceCoefficient } from '@/utils/similarity'
import { initialsOf } from '@/utils/pinyin'

const isMac = typeof navigator !== 'undefined' && /Mac|iPhone|iPad/.test(navigator.platform)
const tab = ref<'summary' | 'detail' | 'classes'>('summary')
const rows = ref<any[]>([])
const summary = ref<any[]>([])
const classes = ref<any[]>([])
const teachers = ref<any[]>([])
const classrooms = ref<any[]>([])
// 单一状态源：筛选 chips / 搜索 / 命令面板读写同一 filters
const filters = reactive({ keyword: '' as string, status: '' as string, form: '' as string, courseId: null as number | null })
const { keyword: kw, status, form: formFilter } = toRefs(filters)
const page = ref(1)
const pageSize = ref(20)

const statusOptions = [
  { k: 'ELIGIBLE', label: '待报名' },
  { k: 'APPLIED', label: '已报名' },
  { k: 'APPROVED', label: '已安排' },
  { k: 'ENROLLED', label: '已进班' },
  { k: 'PASSED', label: '已通过' },
  { k: 'FAILED', label: '未通过' },
]
const formOptions = [
  { k: 'GROUP', label: '组班' },
  { k: 'FOLLOW', label: '跟班' },
  { k: 'SELF', label: '自修' },
]

const filtered = computed(() => {
  const k = kw.value.trim().toLowerCase()
  return rows.value.filter(r => {
    if (filters.courseId != null && r.courseId !== filters.courseId) return false
    if (status.value && r.status !== status.value) return false
    if (formFilter.value && r.form !== formFilter.value) return false
    if (k && !(`${r.studentNo || ''} ${r.studentName || ''} ${r.courseName || ''} ${r.college || ''}`.toLowerCase().includes(k))) return false
    return true
  })
})
const detailPages = computed(() => Math.max(1, Math.ceil(filtered.value.length / pageSize.value)))
const pagedDetail = computed(() => filtered.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value))
const detailPageItems = computed(() => Array.from({ length: detailPages.value }, (_, i) => i + 1).filter(p => detailPages.value <= 7 || p === 1 || p === detailPages.value || Math.abs(p - page.value) <= 1))
watch([kw, () => filters.courseId, status, formFilter], () => { page.value = 1 })
watch(detailPages, v => { if (page.value > v) page.value = v })

async function load() {
  try {
    const [a, b, c]: any[] = await Promise.all([
      http.get('/retake'), http.get('/retake/summary'), http.get('/teaching-classes', { params: { source: 'RETAKE' } }),
    ])
    rows.value = a?.data || []
    summary.value = b?.data || []
    classes.value = c?.data || []
  } catch (e: any) { ElMessage.error(e?.message || t('加载失败')) }
}

async function loadTeachers() {
  if (teachers.value.length) return
  try { const r: any = await http.get('/courses/teachers'); teachers.value = r?.data || [] } catch {}
}

async function loadClassrooms() {
  if (classrooms.value.length) return
  try {
    const r: any = await http.get('/classrooms')
    classrooms.value = (r?.data || []).slice().sort((a: any, b: any) =>
      String(a.building || a.name).localeCompare(String(b.building || b.name), 'zh') ||
      String(a.name).localeCompare(String(b.name), 'zh'))
  } catch {}
}

/* ===== 定形式 ===== */
const dialogOpen = ref(false)
const current = ref<any>(null)
const classes2 = ref<any[]>([])
const form = ref<any>({ form: 'SELF', targetClassId: null })
async function openForm(r: any) {
  current.value = r
  form.value = { form: r.form || 'SELF', targetClassId: r.targetClassId ?? null }
  classes2.value = []
  dialogOpen.value = true
  if (form.value.form === 'FOLLOW') await loadClasses(r.courseId)
}
watch(() => form.value.form, async (v) => {
  if (v === 'FOLLOW' && current.value && !classes2.value.length) await loadClasses(current.value.courseId)
})
async function loadClasses(courseId: number) {
  try {
    const r: any = await http.get('/retake/classes', { params: { courseId } })
    classes2.value = r?.data || []
  } catch { classes2.value = [] }
}
async function saveForm() {
  if (form.value.form === 'FOLLOW' && !form.value.targetClassId) {
    ElMessage.warning(t('跟班重修必须选择教学班'))
    return
  }
  try {
    await http.put(`/retake/${current.value.id}/form`, {
      form: form.value.form,
      targetClassId: form.value.form === 'FOLLOW' ? form.value.targetClassId : null,
    })
    ElMessage.success(t('形式已保存'))
    dialogOpen.value = false
    await load()
  } catch (e: any) { ElMessage.error(e?.message || t('保存失败')) }
}

/* ===== 一键组班（建班 + 排课） ===== */
const groupOpen = ref(false)
const grouping = ref(false)
const group = ref<any>(null)
const gform = ref<any>({ teacherId: null, time: '', location: '', capacity: null })
async function openGroup(s: any) {
  group.value = s
  gform.value = { teacherId: null, time: '', location: '', capacity: null }
  groupOpen.value = true
  await loadTeachers()
  await loadClassrooms()
}
async function submitGroup() {
  grouping.value = true
  try {
    const r: any = await http.post('/teaching-classes/group', {
      courseId: group.value.courseId,
      teacherId: gform.value.teacherId || null,
      time: gform.value.time || null,
      location: gform.value.location || null,
      capacity: gform.value.capacity || null,
    })
    ElMessage.success(r?.message || t('组班成功'))
    groupOpen.value = false
    tab.value = 'classes'
    await load()
  } catch (e: any) { ElMessage.error(e?.message || t('组班失败')) } finally { grouping.value = false }
}

/* ===== 排课 ===== */
const scheduleOpen = ref(false)
const scheduleTarget = ref<any>(null)
const sform = ref<any>({ teacherId: null, time: '', location: '', capacity: null })
async function openSchedule(c: any) {
  scheduleTarget.value = c
  sform.value = { teacherId: c.teacherId ?? null, time: c.time || '', location: c.location || '', capacity: c.capacity ?? null }
  scheduleOpen.value = true
  await loadTeachers()
  await loadClassrooms()
}
async function saveSchedule() {
  try {
    await http.put(`/teaching-classes/${scheduleTarget.value.id}/schedule`, {
      teacherId: sform.value.teacherId || null,
      time: sform.value.time,
      location: sform.value.location,
      capacity: sform.value.capacity || null,
    })
    ElMessage.success(t('排课已保存'))
    scheduleOpen.value = false
    await load()
  } catch (e: any) { ElMessage.error(e?.message || t('保存失败')) }
}

/* ===== 停开 / 名单 ===== */
async function closeClass(c: any) {
  try {
    await ElMessageBox.confirm(
      t('停开教学班 {n}？该班学生会退回「已同意」，可重新定形式或重新组班，课表同步撤销。', { n: c.classNo }),
      t('确认停开'), { type: 'warning' })
  } catch { return }
  try {
    const r: any = await http.put(`/teaching-classes/${c.id}/close`, {})
    ElMessage.success(r?.message || t('已停开'))
    await load()
  } catch (e: any) { ElMessage.error(e?.message || t('操作失败')) }
}

const membersOpen = ref(false)
const membersTarget = ref<any>(null)
const members = ref<any[]>([])
async function openMembers(c: any) {
  membersTarget.value = c
  members.value = []
  membersOpen.value = true
  try { const r: any = await http.get(`/teaching-classes/${c.id}/members`); members.value = r?.data || [] } catch {}
}

/* ===== 筛选轨道 chips ===== */
type ChipKey = 'status' | 'form'
const openChip = ref<ChipKey | null>(null)
function toggleChip(k: ChipKey) { openChip.value = openChip.value === k ? null : k }
const chipDefs = computed(() => [
  {
    key: 'status' as ChipKey, label: t('状态'), active: !!status.value,
    value: statusOptions.find(o => o.k === status.value)?.label ? t(statusOptions.find(o => o.k === status.value)!.label) : '', current: status.value,
    options: statusOptions.map(o => ({ value: o.k, label: t(o.label), count: rows.value.filter(r => r.status === o.k).length })),
    clear: () => { status.value = '' }, set: (v: any) => { status.value = v as string },
  },
  {
    key: 'form' as ChipKey, label: t('形式'), active: !!formFilter.value,
    value: formOptions.find(o => o.k === formFilter.value)?.label ? t(formOptions.find(o => o.k === formFilter.value)!.label) : '', current: formFilter.value,
    options: formOptions.map(o => ({ value: o.k, label: t(o.label), count: rows.value.filter(r => r.form === o.k).length })),
    clear: () => { formFilter.value = '' }, set: (v: any) => { formFilter.value = v as string },
  },
])
const hasFilter = computed(() => !!(kw.value || filters.status || filters.form || filters.courseId != null))
function clearFilters() {
  filters.keyword = ''; filters.status = ''; filters.form = ''; filters.courseId = null; page.value = 1
}

/* ===== 空结果 Dice ===== */
function charDice(a: string, b: string): number {
  const A = new Set(Array.from(a.toLowerCase().replace(/\s+/g, '')))
  const B = new Set(Array.from(b.toLowerCase().replace(/\s+/g, '')))
  if (!A.size || !B.size) return 0
  let inter = 0
  for (const c of A) if (B.has(c)) inter++
  return (2 * inter) / (A.size + B.size)
}
const suggestChips = computed(() => {
  const k = kw.value.trim()
  if (!k) return []
  const names = new Set<string>()
  for (const r of rows.value) { names.add(r.studentName); if (r.studentNo) names.add(r.studentNo); if (r.courseName) names.add(r.courseName) }
  const scored = new Map<string, number>()
  for (const n of names) {
    const score = Math.max(diceCoefficient(k, n), charDice(k, n))
    if (score >= 0.34) scored.set(n, score)
  }
  return [...scored.entries()].sort((a, b) => b[1] - a[1]).slice(0, 5).map(([n]) => n)
})

/* ===== 命令面板 ===== */
const cmdOpen = ref(false)
const cmdTerm = ref('')
const cmdCursor = ref(0)
const cmdInput = ref<HTMLInputElement | null>(null)
function pyOf(r: any) { return initialsOf(`${r.studentName || ''} ${r.studentNo || ''} ${r.courseName || ''}`) }
function matchText(haystack: string, py: string, qq: string): boolean {
  const ql = qq.toLowerCase().replace(/\s+/g, '')
  if (!ql) return true
  if ((haystack || '').toLowerCase().replace(/\s+/g, '').includes(ql)) return true
  return !!py && py.includes(ql)
}
function openCommand() { cmdTerm.value = kw.value; cmdCursor.value = 0; cmdOpen.value = true; nextTick(() => cmdInput.value?.focus()) }
function closeCommand(clear: boolean) {
  cmdOpen.value = false
  if (clear) { cmdTerm.value = ''; kw.value = '' }
  else { cmdTerm.value = kw.value }
}
watch(cmdTerm, v => { if (cmdOpen.value) kw.value = v })
const cmdRows = computed(() => {
  const qq = cmdTerm.value.trim()
  const arr = !qq ? rows.value.slice(0, 8)
    : rows.value.filter(r => matchText(`${r.studentName || ''} ${r.studentNo || ''} ${r.courseName || ''}`, pyOf(r), qq)).slice(0, 8)
  return arr
})
const cmdCourses = computed(() => {
  const qq = cmdTerm.value.trim()
  return summary.value.filter(s => !qq || matchText(s.courseName, initialsOf(s.courseName), qq)).slice(0, 6)
})
type CmdItem = { kind: 'row' | 'course'; raw: any }
const cmdItems = computed<CmdItem[]>(() => [
  ...cmdRows.value.map(r => ({ kind: 'row' as const, raw: r })),
  ...cmdCourses.value.map(s => ({ kind: 'course' as const, raw: s })),
])
watch(cmdItems, () => { if (cmdCursor.value >= cmdItems.value.length) cmdCursor.value = 0 })
function selectCmd(item: CmdItem | undefined) {
  if (!item) return
  if (item.kind === 'row') { filters.courseId = item.raw.courseId; tab.value = 'detail'; cmdOpen.value = false; cmdTerm.value = ''; kw.value = ''; ElMessage.success(t('已定位：{n}', { n: item.raw.studentName })) }
  else { filters.courseId = item.raw.courseId; tab.value = 'summary'; cmdOpen.value = false; cmdTerm.value = ''; kw.value = ''; ElMessage.success(t('已筛选课程：{n}', { n: item.raw.courseName })) }
}
function onGlobalKey(e: KeyboardEvent) {
  const meta = e.metaKey || e.ctrlKey
  if (meta && e.key.toLowerCase() === 'k') { e.preventDefault(); cmdOpen.value ? closeCommand(false) : openCommand(); return }
  if (!cmdOpen.value) return
  if (e.key === 'Escape') { e.preventDefault(); closeCommand(false) }
  else if (e.key === 'ArrowDown') { e.preventDefault(); if (cmdItems.value.length) cmdCursor.value = (cmdCursor.value + 1) % cmdItems.value.length }
  else if (e.key === 'ArrowUp') { e.preventDefault(); if (cmdItems.value.length) cmdCursor.value = (cmdCursor.value - 1 + cmdItems.value.length) % cmdItems.value.length }
  else if (e.key === 'Enter') { e.preventDefault(); selectCmd(cmdItems.value[cmdCursor.value]) }
}

onMounted(() => { load(); window.addEventListener('keydown', onGlobalKey) })
onBeforeUnmount(() => { window.removeEventListener('keydown', onGlobalKey) })
</script>

<style scoped>
.page-wrap { display: flex; flex-direction: column; gap: 14px; }
.rm-head { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 10px; }
.rm-head h2 { margin: 0; font-size: 20px; color: var(--text); }
.rm-actions { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }

/* 开班规则与费用说明（真实华厦规则） */
.rm-rules {
  padding: 12px 16px; border-radius: 12px; margin-bottom: 6px;
  border: 1px solid rgba(31, 143, 124, 0.3); background: rgba(31, 143, 124, 0.07);
  font-size: 12.5px; line-height: 1.8; color: var(--text);
}
.rm-rules b { font-weight: 800; color: var(--el-color-primary, #1f8f7c); }
.rm-fee { font-variant-numeric: tabular-nums; font-weight: 600; }

.rm-tabs { display: flex; gap: 8px; }
.tab-btn { padding: 6px 16px; border-radius: 999px; border: 1px solid var(--border); background: transparent; color: var(--text-light); font-size: 13px; cursor: pointer; }
.tab-btn.active { background: var(--primary-deep); color: #fff; border-color: var(--primary-deep); }
.data-table { background: var(--card); border: 1px solid var(--border); border-radius: 12px; overflow: auto; }
.data-table table { width: 100%; border-collapse: collapse; font-size: 13px; }
.data-table th, .data-table td { padding: 9px 12px; text-align: left; border-bottom: 1px solid var(--border); white-space: nowrap; }
.data-table th { color: var(--text-light); font-weight: 600; font-size: 12px; }
.rm-tag { font-size: 11px; font-weight: 700; padding: 1px 8px; border-radius: 999px; background: rgba(37,99,235,.1); color: #1d4ed8; }
.rm-tag.PASSED, .rm-tag.OPEN { background: rgba(5,150,105,.12); color: #047857; }
.rm-tag.FAILED, .rm-tag.CLOSED { background: rgba(225,29,72,.1); color: #be123c; }
.rm-tag.ENROLLED, .rm-tag.APPROVED { background: rgba(124,58,237,.1); color: #6d28d9; }
.rm-ok { margin-left: 6px; font-size: 11px; color: #047857; font-weight: 700; }
.rm-empty { text-align: center; color: var(--text-light); padding: 26px; }
.rm-form { display: flex; flex-direction: column; gap: 14px; }
.rm-form label { display: flex; flex-direction: column; gap: 6px; font-size: 12px; font-weight: 600; letter-spacing: .02em; color: var(--text-light); }

/* ===== 弹窗与表单控件 ===== */
.rm-dialog :deep(.el-dialog) { border-radius: 18px; overflow: hidden; }
.rm-dialog :deep(.el-dialog__header) { margin: 0; padding: 18px 22px 14px; border-bottom: 1px solid var(--border); }
.rm-dialog :deep(.el-dialog__title) {
  font-family: var(--atlas-serif); font-size: 15px; font-weight: 600;
  letter-spacing: .08em; color: var(--text);
}
.rm-dialog :deep(.el-dialog__body) { padding: 18px 22px 6px; }
.rm-dialog :deep(.el-dialog__footer) { padding: 14px 22px 18px; border-top: 1px dashed var(--border); }
.rm-form :deep(.el-input__wrapper),
.rm-form :deep(.el-select__wrapper) {
  min-height: 38px; padding: 1px 12px; font-size: 13px;
  border-radius: 11px;
  background: var(--card-bg-2, #f3f7f5);
  box-shadow: 0 0 0 1px var(--border) inset;
  transition: box-shadow .18s, background .18s;
}
.rm-form :deep(.el-input__wrapper:hover),
.rm-form :deep(.el-select__wrapper:hover) { box-shadow: 0 0 0 1px color-mix(in srgb, var(--primary) 45%, var(--border)) inset; }
.rm-form :deep(.el-input__wrapper.is-focus),
.rm-form :deep(.el-select__wrapper.is-focused) {
  background: var(--card);
  box-shadow: 0 0 0 1.5px var(--primary) inset, 0 0 0 4px var(--primary-bg, rgba(31, 143, 124, .1));
}
.rm-form :deep(.el-input__inner),
.rm-form :deep(.el-select__selected-item) { font-size: 13px; color: var(--text); }
.rm-form :deep(.el-input__inner::placeholder),
.rm-form :deep(.el-select__placeholder) { color: var(--text-lighter); }
.rm-form :deep(.el-select__caret),
.rm-form :deep(.el-input__icon) { color: var(--text-lighter); }

:global(.rm-drop) {
  border: 1px solid var(--border); border-radius: 12px;
  box-shadow: var(--shadow-md, 0 12px 28px rgba(16, 42, 52, .16));
  padding: 5px;
}
:global(.rm-drop .el-select-dropdown__item) {
  height: 34px; line-height: 34px; font-size: 13px;
  border-radius: 8px; padding: 0 10px; margin: 1px 0;
}
:global(.rm-drop .el-select-dropdown__item.is-hovering) { background: var(--primary-bg, rgba(31, 143, 124, .1)); }
:global(.rm-drop .el-select-dropdown__item.is-selected) { color: var(--primary, #1f8f7c); font-weight: 600; }
.rm-who { margin: 0; font-size: 13px; font-weight: 700; color: var(--text); }
.rm-note { color: var(--text-lighter); font-size: 11px; line-height: 1.6; }
.rm-class { font-weight: 700; color: var(--primary-deep); }
.rm-time { display: block; font-size: 11px; color: var(--text-lighter); }
.rm-ops { display: flex; gap: 6px; }
.rm-mini { max-height: 360px; }
.muted { color: var(--text-lighter); }

/* ================= 检索体验升级：顶栏 / 筛选 chips / 命令面板 / 空结果 ================= */
.rm-page { --g: #10b981; --g-dark: #059669; --g-soft: rgba(16,185,129,.10); }
.ic { display: block; }
.rt-topbar { position: sticky; top: 0; z-index: 40; display: flex; align-items: center; gap: 16px; flex-wrap: wrap; padding: 12px 16px; background: color-mix(in srgb, var(--card, #fff) 86%, transparent); backdrop-filter: saturate(1.4) blur(14px); -webkit-backdrop-filter: saturate(1.4) blur(14px); border: 0.5px solid var(--border, #e5e7eb); border-radius: 16px; box-shadow: 0 1px 2px rgba(16,24,40,.03), 0 8px 24px rgba(16,24,40,.04); }
.rt-brand { display: flex; align-items: center; gap: 12px; flex: 0 0 auto; }
.rt-brand-icon { width: 40px; height: 40px; border-radius: 12px; display: grid; place-items: center; color: #fff; background: linear-gradient(135deg, #34d399, var(--g-dark)); box-shadow: 0 4px 12px rgba(16,185,129,.32); }
.rt-brand-icon .ic { width: 21px; height: 21px; }
.rt-title { font-size: 17px; font-weight: 700; color: var(--text, #0f172a); line-height: 1.2; }
.rt-subtitle { font-size: 12px; color: var(--text-lighter, #94a3b8); margin-top: 2px; }
.rt-search-trigger { flex: 1 1 320px; max-width: 520px; min-width: 180px; height: 38px; display: flex; align-items: center; gap: 9px; padding: 0 10px 0 14px; border: 1px solid var(--border, #e5e7eb); border-radius: 999px; background: var(--card, #fff); color: var(--text-lighter, #94a3b8); cursor: pointer; transition: all .18s; font-family: inherit; }
.rt-search-trigger:hover { border-color: var(--g); box-shadow: 0 0 0 3px var(--g-soft); }
.rt-search-trigger .ic { width: 17px; height: 17px; flex: 0 0 auto; }
.rt-search-ph { flex: 1; text-align: left; font-size: 13px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.rt-kbd { font-family: inherit; font-size: 11px; line-height: 1; white-space: nowrap; padding: 4px 7px; border-radius: 6px; border: 1px solid var(--border, #e5e7eb); background: color-mix(in srgb, var(--card, #fff) 92%, #000 8%); color: var(--text-light, #64748b); flex: 0 0 auto; }
.rt-top-actions { display: flex; align-items: center; gap: 8px; flex: 0 0 auto; margin-left: auto; }
.rt-btn { display: inline-flex; align-items: center; justify-content: center; gap: 6px; height: 36px; padding: 0 14px; border-radius: 10px; font-family: inherit; font-size: 13px; font-weight: 600; cursor: pointer; transition: all .16s; white-space: nowrap; border: 1px solid transparent; }
.rt-btn .ic { width: 15px; height: 15px; }
.rt-btn-outline { background: var(--card, #fff); color: var(--text, #334155); border-color: var(--border, #e5e7eb); }
.rt-btn-outline:hover { border-color: var(--g); color: var(--g); }
.rt-filtercard { padding: 12px 14px; border: 0.5px solid var(--border); border-radius: 14px; background: var(--card); }
.rt-chip-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.rt-chip-wrap { position: relative; }
.rt-chip { display: inline-flex; align-items: center; gap: 6px; height: 32px; padding: 0 11px; border: 1px solid var(--border); border-radius: 999px; background: var(--card); color: var(--text-light); font-family: inherit; font-size: 12.5px; cursor: pointer; transition: all .16s; }
.rt-chip:hover { border-color: var(--g); color: var(--g); }
.rt-chip.on { background: var(--g-soft); border-color: var(--g); color: var(--g-dark); font-weight: 600; }
.rt-chip-label { white-space: nowrap; }
.rt-chip-value { max-width: 140px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--g-dark); }
.rt-chip-arrow { width: 13px; height: 13px; transition: transform .16s; }
.rt-chip-arrow.open { transform: rotate(180deg); }
.rt-reset { border: none; background: none; color: var(--text-lighter); font-family: inherit; font-size: 12.5px; cursor: pointer; padding: 6px 8px; }
.rt-reset:hover { color: #ef4444; }
.rt-dropdown { position: absolute; top: calc(100% + 6px); left: 0; z-index: 30; min-width: 190px; max-height: 320px; overflow-y: auto; padding: 6px; border: 0.5px solid var(--border); border-radius: 12px; background: var(--card); box-shadow: 0 12px 32px rgba(16,24,40,.14); }
.rt-opt { display: flex; align-items: center; justify-content: space-between; gap: 12px; width: 100%; border: none; background: none; font-family: inherit; font-size: 12.5px; color: var(--text-light); cursor: pointer; padding: 8px 10px; border-radius: 8px; text-align: left; }
.rt-opt:hover { background: var(--g-soft); color: var(--g); }
.rt-opt.on { background: var(--g-soft); color: var(--g-dark); font-weight: 600; }
.rt-opt-n { margin-left: 8px; font-size: 11px; color: var(--text-lighter); font-variant-numeric: tabular-nums; }
.rt-opt-check { color: var(--g); font-weight: 700; }
.rt-empty { display: flex; flex-direction: column; align-items: center; text-align: center; padding: 28px 20px; }
.rt-empty-title { font-size: 14px; font-weight: 600; color: var(--text); }
.rt-suggest { display: flex; align-items: center; gap: 7px; flex-wrap: wrap; justify-content: center; margin-top: 12px; }
.rt-suggest-label { font-size: 12px; color: var(--text-light); }
.rt-suggest-chip { border: 1px solid var(--border); background: var(--card); color: var(--g-dark); font-family: inherit; font-size: 12.5px; padding: 6px 12px; border-radius: 999px; cursor: pointer; transition: all .15s; }
.rt-suggest-chip:hover { border-color: var(--g); background: var(--g-soft); }
.rt-empty-reset { margin-top: 12px; border: none; background: none; color: var(--g); font-family: inherit; font-size: 12.5px; cursor: pointer; text-decoration: underline; text-underline-offset: 3px; }
.rt-pagebar { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 10px 14px; border-top: 1px solid var(--border); }
.rt-pageinfo { font-size: 12px; color: var(--text-light); }
.rt-pager { display: flex; align-items: center; gap: 4px; }
.rt-pg-btn { min-width: 28px; height: 28px; padding: 0 7px; border: 1px solid var(--border); border-radius: 8px; background: var(--card); color: var(--text-light); font-family: inherit; font-size: 12px; cursor: pointer; }
.rt-pg-btn:hover:not(:disabled) { border-color: var(--g); color: var(--g); }
.rt-pg-btn.active { background: var(--g); border-color: var(--g); color: #fff; font-weight: 700; }
.rt-pg-btn:disabled { opacity: .4; cursor: not-allowed; }
.cmd-overlay { position: fixed; inset: 0; z-index: 4000; display: flex; justify-content: center; align-items: flex-start; padding: 14vh 16px 16px; background: rgba(15,23,42,.42); backdrop-filter: blur(8px); -webkit-backdrop-filter: blur(8px); animation: cmdFade .16s ease; }
.cmd-panel { width: 100%; max-width: 600px; max-height: 68vh; display: flex; flex-direction: column; border: 0.5px solid var(--border); border-radius: 16px; overflow: hidden; background: var(--card); box-shadow: 0 24px 70px rgba(15,23,42,.34); animation: cmdUp .2s cubic-bezier(.16,1,.3,1); }
.cmd-input-row { display: flex; align-items: center; gap: 11px; padding: 14px 16px; border-bottom: 0.5px solid var(--border); }
.cmd-input-row .ic { width: 19px; height: 19px; color: var(--g); flex: 0 0 auto; }
.cmd-input-row input { flex: 1; min-width: 0; border: none; outline: none; background: transparent; color: var(--text); font-family: inherit; font-size: 15px; }
.cmd-input-row input::placeholder { color: var(--text-lighter); }
.cmd-results { flex: 1; overflow-y: auto; padding: 8px; }
.cmd-group + .cmd-group { margin-top: 6px; }
.cmd-group-h { display: flex; align-items: center; gap: 7px; padding: 6px 10px; font-size: 11px; font-weight: 700; letter-spacing: .04em; color: var(--text-lighter); text-transform: uppercase; }
.cmd-group-h span { font-weight: 500; }
.cmd-item { display: flex; align-items: center; gap: 11px; width: 100%; text-align: left; border: none; background: none; font-family: inherit; cursor: pointer; padding: 9px 10px; border-radius: 10px; color: var(--text); }
.cmd-item.active { background: var(--g-soft); }
.cmd-item-ico { width: 32px; height: 32px; border-radius: 9px; display: grid; place-items: center; flex: 0 0 auto; }
.cmd-item-ico .ic { width: 17px; height: 17px; }
.cmd-item-ico.user { background: rgba(16,185,129,.13); color: var(--g); }
.cmd-item-ico.org { background: rgba(59,130,246,.13); color: #3b82f6; }
.cmd-item-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.cmd-item-main b { font-size: 13px; font-weight: 600; color: var(--text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cmd-item-main small { font-size: 11px; color: var(--text-lighter); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cmd-enter { color: var(--g); font-size: 14px; flex: 0 0 auto; }
.cmd-empty { padding: 36px 16px; text-align: center; color: var(--text-lighter); font-size: 13px; }
.cmd-footer { display: flex; align-items: center; gap: 16px; padding: 10px 16px; border-top: 0.5px solid var(--border); background: color-mix(in srgb, var(--card) 94%, #000 6%); font-size: 11px; color: var(--text-lighter); }
.cmd-footer span { display: inline-flex; align-items: center; gap: 4px; }
.cmd-footer .rt-kbd { padding: 2px 6px; }
.cmd-footer-tip { margin-left: auto; color: var(--g); }
@keyframes cmdFade { from { opacity: 0 } to { opacity: 1 } }
@keyframes cmdUp { from { opacity: 0; transform: translateY(-10px) scale(.985) } to { opacity: 1; transform: none } }
.rt-tabs { margin-top: 2px; }
@media (max-width: 1100px) { .rt-search-trigger { order: 3; flex-basis: 100%; max-width: none; } .rt-top-actions { order: 2; } }
@media (max-width: 560px) { .rt-topbar { padding: 12px; } .rt-kbd { display: none; } }
</style>
