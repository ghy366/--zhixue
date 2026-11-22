<template>
  <div class="page-wrap mm-page">
    <!-- ===== 顶部 sticky 导航栏 ===== -->
    <header class="mk-topbar">
      <div class="mk-brand">
        <div class="mk-brand-icon">
          <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 11l3 3L22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/></svg>
        </div>
        <div class="mk-brand-text">
          <div class="mk-title">{{ t('补考管理') }}</div>
          <div class="mk-subtitle">{{ t('共 {n} 条补考记录', { n: rows.length }) }}</div>
        </div>
      </div>
      <button class="mk-search-trigger" @click="openCommand" :aria-label="t('打开搜索')">
        <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>
        <span class="mk-search-ph">{{ filters.keyword || t('搜索学生 / 学号 / 课程 / 学院，支持拼音首字母…') }}</span>
        <kbd class="mk-kbd">{{ isMac ? '⌘' : 'Ctrl+' }} K</kbd>
      </button>
      <div class="mk-top-actions">
        <button class="mk-btn mk-btn-outline" @click="exportList">{{ t('导出名单') }}</button>
        <button class="mk-btn mk-btn-solid" @click="generate">+ {{ t('一键生成补考册') }}</button>
      </div>
    </header>

    <!-- 页头：标题 + 操作（保留 eyebrow 风格；筛选移到 chips 轨道） -->
    <section class="mm-hero">
      <div class="mm-hero-copy">
        <span class="mm-eyebrow"><i class="mm-pulse"></i>{{ t('教务 · 考务') }}</span>
        <h2>{{ t('补考报名、排考、成绩与状态全流程跟踪') }}</h2>
      </div>
    </section>

    <!-- 筛选轨道 chips：学期 / 状态，读写同一个 filters -->
    <div class="mk-filtercard">
      <div class="mk-chip-row">
        <div v-for="chip in chipDefs" :key="chip.key" class="mk-chip-wrap">
          <button class="mk-chip" :class="{ on: chip.active }" @click="toggleChip(chip.key)">
            <span class="mk-chip-label">{{ chip.label }}</span>
            <span v-if="chip.value" class="mk-chip-value">{{ chip.value }}</span>
            <svg class="ic mk-chip-arrow" :class="{ open: openChip === chip.key }" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="m6 9 6 6 6-6"/></svg>
          </button>
          <div v-if="openChip === chip.key" class="mk-dropdown" @click.stop>
            <button class="mk-opt" :class="{ on: !chip.current }" @click="chip.clear(); openChip = null">
              <span>{{ t('全部') }}{{ chip.label }}</span><span v-if="!chip.current" class="mk-opt-check">✓</span>
            </button>
            <button v-for="o in chip.options" :key="String(o.value)" class="mk-opt" :class="{ on: chip.current === o.value }" @click="chip.set(o.value); openChip = null">
              <span>{{ o.label }}<span v-if="o.count !== undefined" class="mk-opt-n">{{ o.count }}</span></span>
              <span v-if="chip.current === o.value" class="mk-opt-check">✓</span>
            </button>
          </div>
        </div>
        <button v-if="hasFilter" class="mk-reset" @click="clearFilters">{{ t('清空') }}</button>
      </div>
    </div>

    <!-- 补考报名窗口：决定学生端「补考报名」按钮是否可点 -->
    <section class="mm-card mm-config">
      <header class="mm-card-head">
        <span class="mm-card-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"
               stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="3.2" />
            <path d="M12 3.2v2.1M12 18.7v2.1M3.2 12h2.1M18.7 12h2.1M5.8 5.8l1.5 1.5M16.7 16.7l1.5 1.5M18.2 5.8l-1.5 1.5M7.3 16.7l-1.5 1.5" />
          </svg>
        </span>
        <div class="mm-card-title">
          <h3>{{ t('补考报名窗口与参数') }}</h3>
          <p>{{ t('未设置窗口时学生可随时报名；窗口结束后按钮自动置灰。') }}</p>
        </div>
      </header>
      <div class="mm-config-grid">
        <label class="mm-field">
          <span>{{ t('开始') }}</span>
          <el-input v-model="cfg.makeupOpenAt" type="datetime-local" size="small" />
        </label>
        <label class="mm-field">
          <span>{{ t('结束') }}</span>
          <el-input v-model="cfg.makeupCloseAt" type="datetime-local" size="small" />
        </label>
        <label class="mm-field">
          <span>{{ t('补考封顶分') }}</span>
          <el-input v-model.number="cfg.makeupScoreCap" type="number" size="small" />
        </label>
        <label class="mm-field">
          <span>{{ t('补考次数上限') }}</span>
          <el-input v-model.number="cfg.makeupMaxAttempts" type="number" size="small" />
        </label>
        <label class="mm-field">
          <span>{{ t('重修次数上限') }}</span>
          <el-input v-model.number="cfg.maxRetakeAttempts" type="number" size="small" />
        </label>
        <button class="mm-btn solid mm-save" @click="saveConfig">{{ t('保存参数') }}</button>
      </div>
    </section>

    <!-- 指标卡 -->
    <section class="mm-stats">
      <article class="mm-stat" style="--accent: var(--el-color-primary, #1f8f7c)">
        <div class="mm-stat-head"><span>{{ t('补考记录') }}</span><i></i></div>
        <b>{{ rows.length }}</b>
        <small>{{ t('全部记录') }}</small>
      </article>
      <article class="mm-stat" style="--accent: var(--el-color-warning, #a67c34)">
        <div class="mm-stat-head"><span>{{ t('待报名') }}</span><i></i></div>
        <b>{{ countBy('ELIGIBLE') }}</b>
        <small>{{ t('等待学生报名') }}</small>
      </article>
      <article class="mm-stat" style="--accent: #7c6cf0">
        <div class="mm-stat-head"><span>{{ t('已报名/已排考') }}</span><i></i></div>
        <b>{{ countBy('REGISTERED') + countBy('SCHEDULED') }}</b>
        <small>{{ t('已进入考试流程') }}</small>
      </article>
      <article class="mm-stat" style="--accent: var(--el-color-success, #16805f)">
        <div class="mm-stat-head"><span>{{ t('已通过') }}</span><i></i></div>
        <b>{{ countBy('PASSED') }}</b>
        <small>{{ t('成绩达到及格线') }}</small>
      </article>
      <article class="mm-stat" style="--accent: var(--el-color-danger, #c85346)">
        <div class="mm-stat-head"><span>{{ t('未通过/缺考') }}</span><i></i></div>
        <b>{{ countBy('FAILED') + countBy('ABSENT') }}</b>
        <small>{{ t('需进入重修流程') }}</small>
      </article>
    </section>

    <!-- 名单：课程汇总（默认）/ 明细列表 -->
    <section class="mm-card mm-table-card">
      <header class="mm-table-head">
        <div>
          <h3>{{ t('补考名单') }}</h3>
          <p>{{ listView === 'course'
            ? t('按课程汇总——补考以「课」为单位组织排考；点击「进入名单」查看学生明细')
            : t('按学期与状态筛选，支持排考与弃考操作') }}</p>
        </div>
        <span class="mm-count"><b>{{ filtered.length }}</b> {{ t('条记录') }}</span>
      </header>

      <!-- 视图切换 -->
      <div class="mm-viewseg">
        <button :class="{ on: listView === 'course' }" @click="listView = 'course'">{{ t('按课程汇总') }}</button>
        <button :class="{ on: listView === 'list' }" @click="listView = 'list'">{{ t('明细列表') }}</button>
      </div>

      <!-- 课程汇总视图 -->
      <div v-if="listView === 'course'" class="mm-table-scroll">
        <table class="mm-table mm-csum">
          <thead>
            <tr>
              <th>{{ t('课程') }}</th>
              <th>{{ t('学期') }}</th>
              <th>{{ t('记录数') }}</th>
              <th>{{ t('待报名') }}</th>
              <th>{{ t('已报名') }}</th>
              <th>{{ t('已排考') }}</th>
              <th>{{ t('已通过') }}</th>
              <th>{{ t('未通过/缺考') }}</th>
              <th>{{ t('涉及学院') }}</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="g in courseGroups" :key="g.key" class="mk-row" @click="enterCourse(g)">
              <td><div class="mm-strong">{{ g.course }}</div></td>
              <td><span class="mm-chip soft">{{ g.semesterLabel || '—' }}</span></td>
              <td class="mm-cnum"><b>{{ g.total }}</b></td>
              <td class="mm-cnum">{{ g.eligible }}</td>
              <td class="mm-cnum">{{ g.registered }}</td>
              <td class="mm-cnum">{{ g.scheduled }}</td>
              <td class="mm-cnum">{{ g.passed }}</td>
              <td class="mm-cnum" :class="{ bad: g.failedAbsent }">{{ g.failedAbsent }}</td>
              <td><small>{{ t('{n} 个学院', { n: g.collegeCount }) }}</small></td>
              <td class="mm-ops"><button class="mm-op primary" @click.stop="enterCourse(g)">{{ t('进入名单') }}</button></td>
            </tr>
            <tr v-if="!courseGroups.length">
              <td colspan="10">
                <div class="mk-empty">
                  <span class="mk-empty-icon">∅</span>
                  <b>{{ rows.length ? t('没有匹配的补考记录') : t('暂无补考记录') }}</b>
                  <small>{{ rows.length ? t('试试调整筛选条件') : t('点击右上角「一键生成补考册」创建本学期的补考名单') }}</small>
                  <button v-if="hasFilter" class="mk-empty-reset" @click="clearFilters">{{ t('清空筛选') }}</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        <div class="mm-course-tip">{{ t('补考排考以课程为单位：同一门课的考生集中排考（教室/监考/时间），进入名单后可逐个学生排考或弃考。') }}</div>
      </div>

      <!-- 明细列表视图 -->
      <div v-else class="mm-table-scroll">
        <table class="mm-table">
          <thead>
            <tr>
              <th>{{ t('学生') }}</th>
              <th>{{ t('学院') }} / {{ t('专业') }}</th>
              <th>{{ t('年级') }}</th>
              <th>{{ t('课程') }}</th>
              <th>{{ t('学期') }}</th>
              <th>{{ t('状态') }}</th>
              <th>{{ t('成绩') }}</th>
              <th>{{ t('考试安排') }}</th>
              <th>{{ t('监考') }}</th>
              <th class="mm-th-ops">{{ t('操作') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in paged" :key="r.id" class="mk-row" @click="openDetail(r)">
              <td>
                <div class="mm-student">
                  <span class="mm-avatar">{{ initial(r.studentName) }}</span>
                  <div class="mm-student-text">
                    <b>{{ r.studentName }}</b>
                    <small>{{ r.studentNo }}</small>
                  </div>
                </div>
              </td>
              <td>
                <div class="mm-strong">{{ r.college || '—' }}</div>
                <small v-if="r.major">{{ r.major }}</small>
              </td>
              <td><span class="mm-chip">{{ r.grade ? t('{n} 级', { n: r.grade }) : '—' }}</span></td>
              <td>
                <div class="mm-strong">{{ r.courseName }}</div>
                <small v-if="r.teacherName || r.credit">
                  {{ r.teacherName || '' }}<template v-if="r.credit"> · {{ r.credit }} {{ t('学分') }}</template>
                </small>
              </td>
              <td><span class="mm-chip soft">{{ r.semesterLabel || '—' }}</span></td>
              <td><span class="mm-tag" :class="r.status"><i></i>{{ r.statusText }}</span></td>
              <td>
                <span v-if="r.score != null" class="mm-score" :class="Number(r.score) >= 60 ? 'pass' : 'fail'">{{ r.score }}</span>
                <span v-else class="mm-muted">—</span>
              </td>
              <td>
                <div class="mm-strong">{{ r.examTime ? r.examTime.replace('T', ' ') : t('待安排') }}</div>
                <small>{{ r.location || '—' }}</small>
              </td>
              <td><span class="mm-strong">{{ r.invigilator || '—' }}</span></td>
              <td class="mm-ops">
                <template v-if="!['PASSED','FAILED','ABSENT','CANCELLED'].includes(r.status)">
                  <button class="mm-op primary" @click.stop="openSchedule(r)">{{ t('排考') }}</button>
                  <button class="mm-op danger" @click.stop="markAbsent(r)">{{ t('弃考') }}</button>
                </template>
                <span v-else class="mm-muted">—</span>
              </td>
            </tr>
            <tr v-if="!paged.length">
              <td colspan="10">
                <div class="mk-empty">
                  <span class="mk-empty-icon">∅</span>
                  <b>{{ rows.length ? t('没有匹配的补考记录') : t('暂无补考记录') }}</b>
                  <small>{{ rows.length ? t('试试调整筛选条件') : t('点击右上角「一键生成补考册」创建本学期的补考名单') }}</small>
                  <div v-if="suggestChips.length" class="mk-suggest">
                    <span class="mk-suggest-label">{{ t('是不是想找') }}</span>
                    <button v-for="s in suggestChips" :key="s" class="mk-suggest-chip" @click="filters.keyword = s">{{ s }}</button>
                  </div>
                  <button v-if="hasFilter" class="mk-empty-reset" @click="clearFilters">{{ t('清空筛选') }}</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="listView === 'list' && filtered.length > pageSize" class="mk-pagebar">
        <span class="mk-pageinfo">{{ t('共 {n} 条 · 第 {p} / {total} 页', { n: filtered.length, p: page, total: totalPages }) }}</span>
        <div class="mk-pager">
          <button class="mk-pg-btn" :disabled="page <= 1" @click="page--">‹</button>
          <button v-for="p in pageItems" :key="p" class="mk-pg-btn" :class="{ active: p === page }" @click="page = p">{{ p }}</button>
          <button class="mk-pg-btn" :disabled="page >= totalPages" @click="page++">›</button>
        </div>
      </div>
    </section>

    <el-dialog v-model="scheduleOpen" :title="t('补考排考')" width="440px" class="mm-dialog">
      <div class="mm-form">
        <div v-if="current" class="mm-who">
          <span class="mm-who-avatar">{{ initial(current.studentName) }}</span>
          <span class="mm-who-txt">
            <b>{{ current.studentName }}</b>
            <small>{{ current.courseName }}<template v-if="current.semesterLabel"> · {{ current.semesterLabel }}</template></small>
          </span>
        </div>
        <label class="mm-field">
          <span class="mm-lb">
            <svg viewBox="0 0 24 24"><rect x="3" y="4.5" width="18" height="16.5" rx="2.5"/><path d="M8 2.5v4M16 2.5v4M3 10h18"/></svg>
            {{ t('考试时间') }}
          </span>
          <el-date-picker v-model="form.examTime" type="datetime" format="YYYY-MM-DD HH:mm"
                          value-format="YYYY-MM-DDTHH:mm" :placeholder="t('选择考试日期与时间')"
                          popper-class="mm-date" style="width:100%" />
        </label>
        <label class="mm-field">
          <span class="mm-lb">
            <svg viewBox="0 0 24 24"><path d="M12 21s7-5.4 7-11a7 7 0 1 0-14 0c0 5.6 7 11 7 11z"/><circle cx="12" cy="10" r="2.5"/></svg>
            {{ t('考试地点') }}
          </span>
          <el-select v-model="form.location" filterable allow-create default-first-option
                     popper-class="mm-drop" :placeholder="t('从教室管理中选择，或直接输入')" style="width:100%">
            <el-option v-for="c in classrooms" :key="c.id" :value="c.name"
                       :label="`${c.name}（${c.capacity ?? '—'} 座）`" />
          </el-select>
          <small class="mm-hint">{{ t('来源「教室管理」，亦可直接输入临时地点') }}</small>
        </label>
        <label class="mm-field">
          <span class="mm-lb">
            <svg viewBox="0 0 24 24"><circle cx="12" cy="8" r="4"/><path d="M4.5 21a7.5 7.5 0 0 1 15 0"/></svg>
            {{ t('监考教师') }}
          </span>
          <el-select v-model="form.invigilator" filterable allow-create default-first-option
                     popper-class="mm-drop" :placeholder="t('从教师名单中选择，或直接输入')" style="width:100%">
            <el-option v-for="tt in teachers" :key="tt.id" :value="tt.name"
                       :label="`${tt.name}（${tt.college || t('教师')}）`" />
          </el-select>
          <small class="mm-hint">{{ t('来源「用户管理」教师，亦可直接输入') }}</small>
        </label>
      </div>
      <template #footer>
        <button class="mm-btn ghost" @click="scheduleOpen = false">{{ t('取消') }}</button>
        <button class="mm-btn solid" @click="saveSchedule">{{ t('保存') }}</button>
      </template>
    </el-dialog>

    <!-- ================= 命令面板 ================= -->
    <Teleport to="body">
      <div v-if="cmdOpen" class="cmd-overlay" @click="closeCommand(false)">
        <div class="cmd-panel" @click.stop>
          <div class="cmd-input-row">
            <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>
            <input ref="cmdInput" v-model="cmdTerm" :placeholder="t('搜索学生 / 学号 / 课程 / 学院，支持拼音首字母…')" />
            <kbd class="mk-kbd">esc</kbd>
          </div>
          <div class="cmd-results">
            <template v-if="cmdItems.length">
              <div v-if="cmdRows.length" class="cmd-group">
                <div class="cmd-group-h">{{ t('补考记录') }} <span>{{ cmdRows.length }}</span></div>
                <button v-for="(r, i) in cmdRows" :key="r.id" class="cmd-item" :class="{ active: cmdCursor === i }" @mouseenter="cmdCursor = i" @click="selectCmd(cmdItems[i])">
                  <span class="cmd-item-ico user"><svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/></svg></span>
                  <span class="cmd-item-main"><b>{{ r.studentName }} · {{ r.courseName }}</b><small>{{ r.studentNo }} · {{ r.statusText }} · {{ r.semesterLabel || '—' }}</small></span>
                  <span class="cmd-enter" v-if="cmdCursor === i">↵</span>
                </button>
              </div>
              <div v-if="cmdCourses.length" class="cmd-group">
                <div class="cmd-group-h">{{ t('课程') }} <span>{{ cmdCourses.length }}</span></div>
                <button v-for="(co, j) in cmdCourses" :key="co.name" class="cmd-item" :class="{ active: cmdCursor === cmdRows.length + j }" @mouseenter="cmdCursor = cmdRows.length + j" @click="selectCmd(cmdItems[cmdRows.length + j])">
                  <span class="cmd-item-ico org"><svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M4 4.5A2.5 2.5 0 0 1 6.5 2H20v15H6.5A2.5 2.5 0 0 0 4 19.5z"/></svg></span>
                  <span class="cmd-item-main"><b>{{ co.name }}</b><small>{{ co.count }} {{ t('条记录') }}</small></span>
                  <span class="cmd-enter" v-if="cmdCursor === cmdRows.length + j">↵</span>
                </button>
              </div>
            </template>
            <div v-else class="cmd-empty">{{ t('没有联想结果，试试姓名 / 学号 / 课程 / 拼音首字母') }}</div>
          </div>
          <div class="cmd-footer">
            <span><kbd class="mk-kbd">↑</kbd><kbd class="mk-kbd">↓</kbd> {{ t('导航') }}</span>
            <span><kbd class="mk-kbd">↵</kbd> {{ t('选中') }}</span>
            <span class="cmd-footer-tip">{{ t('选中记录看档案 · 选中课程即筛选') }}</span>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- ================= 补考记录详情抽屉 ================= -->
    <Teleport to="body">
      <div v-if="mkDetail" class="mk-mask" @click="mkDetail = null">
        <div class="mk-drawer" @click.stop>
          <div class="mk-drawer-h">
            <div><b>{{ mkDetail.studentName }}</b><span>{{ mkDetail.studentNo }} · {{ t(mkDetail.statusText || '') }}</span></div>
            <button class="mk-close" @click="mkDetail = null">×</button>
          </div>
          <div class="mk-drawer-body">
            <div v-for="f in detailFields" :key="f.label" class="mk-field"><span>{{ f.label }}</span><b>{{ f.value || '—' }}</b></div>
          </div>
          <div class="mk-drawer-f">
            <button class="mk-btn mk-btn-outline" @click="mkDetail = null">{{ t('关闭') }}</button>
            <button v-if="!['PASSED','FAILED','ABSENT','CANCELLED'].includes(mkDetail.status)" class="mk-btn mk-btn-solid" @click="editFromDetail(mkDetail)">{{ t('排考') }}</button>
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
const rows = ref<any[]>([])
// 单一状态源：chips / 搜索 / 命令面板读写同一 filters
const filters = reactive({ keyword: '' as string, status: '' as string, semester: '' as string, course: '' as string, college: '' as string })
const { status, semester: semesterLabel } = toRefs(filters)
const page = ref(1)
const pageSize = ref(20)
const semesterOptions = ref<string[]>([])
const cfg = ref<any>({ makeupOpenAt: '', makeupCloseAt: '', makeupScoreCap: 60, makeupMaxAttempts: 1, maxRetakeAttempts: 2 })

const collegeOptions = computed(() => {
  const set = new Set<string>()
  for (const r of rows.value) if (r.college) set.add(r.college)
  return Array.from(set).sort((a, b) => a.localeCompare(b, 'zh'))
})

const statusOptions = [
  { k: 'ELIGIBLE', label: '待报名' },
  { k: 'REGISTERED', label: '已报名' },
  { k: 'SCHEDULED', label: '已排考' },
  { k: 'PASSED', label: '已通过' },
  { k: 'FAILED', label: '未通过' },
  { k: 'ABSENT', label: '缺考' },
]

const filtered = computed(() => {
  const k = filters.keyword.trim().toLowerCase()
  return rows.value.filter(r => {
    if (status.value && r.status !== status.value) return false
    if (semesterLabel.value && r.semesterLabel !== semesterLabel.value) return false
    if (filters.college && r.college !== filters.college) return false
    if (filters.course && r.courseName !== filters.course) return false
    if (k && !(`${r.studentName || ''} ${r.studentNo || ''} ${r.courseName || ''} ${r.college || ''} ${r.major || ''}`.toLowerCase().includes(k))) return false
    return true
  })
})
/* ===== 名单视图：按课程汇总（默认，补考以「课」为单位组织排考）/ 明细列表 ===== */
const listView = ref<'course' | 'list'>('course')
const courseGroups = computed(() => {
  const map = new Map<string, any>()
  for (const r of filtered.value) {
    const key = r.courseName + '#' + (r.semesterLabel || '')
    let g = map.get(key)
    if (!g) {
      g = { key, course: r.courseName, semesterLabel: r.semesterLabel || '', total: 0, eligible: 0, registered: 0, scheduled: 0, passed: 0, failedAbsent: 0, colleges: new Set<string>() }
      map.set(key, g)
    }
    g.total++
    if (r.status === 'ELIGIBLE') g.eligible++
    else if (r.status === 'REGISTERED') g.registered++
    else if (r.status === 'SCHEDULED') g.scheduled++
    else if (r.status === 'PASSED') g.passed++
    else if (r.status === 'FAILED' || r.status === 'ABSENT') g.failedAbsent++
    if (r.college) g.colleges.add(r.college)
  }
  const out = Array.from(map.values()).map(g => ({ ...g, collegeCount: g.colleges.size }))
  out.sort((a, b) => b.total - a.total)
  return out
})
function enterCourse(g: any) { filters.course = g.course; listView.value = 'list'; page.value = 1 }

const totalPages = computed(() => Math.max(1, Math.ceil(filtered.value.length / pageSize.value)))
const paged = computed(() => filtered.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value))
const pageItems = computed(() => Array.from({ length: totalPages.value }, (_, i) => i + 1).filter(p => totalPages.value <= 7 || p === 1 || p === totalPages.value || Math.abs(p - page.value) <= 1))
watch([() => filters.keyword, status, semesterLabel, () => filters.course, () => filters.college], () => { page.value = 1 })
watch(totalPages, v => { if (page.value > v) page.value = v })
function countBy(s: string) { return filtered.value.filter(r => r.status === s).length }
function initial(name: string) { return (name || '?').trim().slice(0, 1) }

/* ===== 详情抽屉 ===== */
const mkDetail = ref<any>(null)
const detailFields = computed(() => !mkDetail.value ? [] : [
  { label: t('学号'), value: mkDetail.value.studentNo },
  { label: t('学院'), value: mkDetail.value.college },
  { label: t('专业'), value: mkDetail.value.major },
  { label: t('年级'), value: mkDetail.value.grade ? `${mkDetail.value.grade} ${t('级')}` : '' },
  { label: t('课程'), value: mkDetail.value.courseName },
  { label: t('学期'), value: mkDetail.value.semesterLabel },
  { label: t('成绩'), value: mkDetail.value.score ?? '—' },
  { label: t('考试安排'), value: mkDetail.value.examTime ? String(mkDetail.value.examTime).replace('T', ' ') : '—' },
  { label: t('地点'), value: mkDetail.value.location },
  { label: t('监考'), value: mkDetail.value.invigilator },
])
function openDetail(r: any) { mkDetail.value = r }
function editFromDetail(r: any) { mkDetail.value = null; openSchedule(r) }

async function load() {
  try {
    const r: any = await http.get('/makeup')
    rows.value = r?.data || []
    syncSemesterOptions()
  } catch (e: any) { ElMessage.error(e?.message || t('加载失败')) }
}
/** 学期下拉：学期设置里的学期名 + 名单里实际出现的学期名 */
async function loadSemesters() {
  try {
    const r: any = await http.get('/semesters')
    semesterOptions.value = (r?.data || []).map((s: any) => s.name).filter(Boolean)
    syncSemesterOptions()
  } catch {}
}
function syncSemesterOptions() {
  const set = new Set<string>(semesterOptions.value)
  rows.value.forEach(x => { if (x.semesterLabel) set.add(x.semesterLabel) })
  semesterOptions.value = Array.from(set)
}
async function loadConfig() {
  try {
    const r: any = await http.get('/system/config')
    const c = r?.data || {}
    cfg.value = {
      makeupOpenAt: c.makeupOpenAt ? String(c.makeupOpenAt).slice(0, 16) : '',
      makeupCloseAt: c.makeupCloseAt ? String(c.makeupCloseAt).slice(0, 16) : '',
      makeupScoreCap: c.makeupScoreCap ?? 60,
      makeupMaxAttempts: c.makeupMaxAttempts ?? 1,
      maxRetakeAttempts: c.maxRetakeAttempts ?? 2,
      retakeGroupThreshold: c.retakeGroupThreshold ?? 15,
    }
  } catch {}
}
async function saveConfig() {
  try {
    await http.post('/system/makeup-config', { ...cfg.value })
    ElMessage.success(t('参数已保存'))
  } catch (e: any) { ElMessage.error(e?.message || t('保存失败')) }
}
async function generate() {
  try {
    const r: any = await http.post('/makeup/generate', { semesterLabel: semesterLabel.value || null })
    ElMessage.success(r?.message || t('生成完成'))
    await load()
    await exportList()
  } catch (e: any) { ElMessage.error(e?.message || t('生成失败')) }
}

const scheduleOpen = ref(false)
const current = ref<any>(null)
const form = ref<any>({ examTime: '', location: '', invigilator: '' })
const classrooms = ref<any[]>([])
const teachers = ref<any[]>([])
async function loadClassrooms() {
  try {
    const r: any = await http.get('/classrooms')
    classrooms.value = (r?.data || []).slice().sort((a: any, b: any) =>
      String(a.building || a.name).localeCompare(String(b.building || b.name), 'zh') ||
      String(a.name).localeCompare(String(b.name), 'zh'))
  } catch {}
}
async function loadTeachers() {
  try { const r: any = await http.get('/courses/teachers'); teachers.value = r?.data || [] } catch {}
}
function openSchedule(r: any) {
  current.value = r
  form.value = {
    examTime: r.examTime ? String(r.examTime).slice(0, 16) : '',
    location: r.location || '',
    invigilator: r.invigilator || '',
  }
  scheduleOpen.value = true
}
async function saveSchedule() {
  try {
    await http.put(`/makeup/${current.value.id}/schedule`, { ...form.value })
    ElMessage.success(t('排考已保存'))
    scheduleOpen.value = false
    await load()
  } catch (e: any) { ElMessage.error(e?.message || t('保存失败')) }
}
async function markAbsent(r: any) {
  try {
    await ElMessageBox.confirm(t('确认将 {n} 的《{c}》标记为弃考？系统会立即生成重修申请。', { n: r.studentName, c: r.courseName }), t('确认弃考'), { type: 'warning' })
  } catch { return }
  try {
    const res: any = await http.put(`/makeup/${r.id}/absent`)
    ElMessage.success(res?.message || t('已标记弃考'))
    await load()
  } catch (e: any) { ElMessage.error(e?.message || t('操作失败')) }
}
async function exportList() {
  try {
    const blob: any = await http.get('/makeup/export', {
      params: semesterLabel.value ? { semesterLabel: semesterLabel.value } : {},
      responseType: 'blob',
    })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = semesterLabel.value ? `补考名册-${semesterLabel.value}.xlsx` : '补考名册.xlsx'
    a.click()
    URL.revokeObjectURL(url)
  } catch (e: any) { ElMessage.error(e?.message || t('导出失败')) }
}

/* ===== 筛选轨道 chips ===== */
type ChipKey = 'semester' | 'college' | 'status' | 'course'
const openChip = ref<ChipKey | null>(null)
function toggleChip(k: ChipKey) { openChip.value = openChip.value === k ? null : k }
const courseOptions = computed(() => [...new Set(rows.value.map(r => r.courseName).filter(Boolean))].sort((a, b) => a.localeCompare(b, 'zh')))
const chipDefs = computed(() => [
  {
    key: 'semester' as ChipKey, label: t('学期'), active: !!semesterLabel.value, value: semesterLabel.value || '', current: semesterLabel.value,
    options: semesterOptions.value.map(s => ({ value: s, label: s, count: rows.value.filter(r => r.semesterLabel === s).length })),
    clear: () => { semesterLabel.value = '' }, set: (v: any) => { semesterLabel.value = v as string },
  },
  {
    key: 'college' as ChipKey, label: t('学院'), active: !!filters.college, value: filters.college || '', current: filters.college,
    options: collegeOptions.value.map(c => ({ value: c, label: c, count: rows.value.filter(r => r.college === c).length })),
    clear: () => { filters.college = '' }, set: (v: any) => { filters.college = v as string },
  },
  {
    key: 'status' as ChipKey, label: t('状态'), active: !!status.value,
    value: statusOptions.find(o => o.k === status.value)?.label ? t(statusOptions.find(o => o.k === status.value)!.label) : '', current: status.value,
    options: statusOptions.map(o => ({ value: o.k, label: t(o.label), count: rows.value.filter(r => r.status === o.k).length })),
    clear: () => { status.value = '' }, set: (v: any) => { status.value = v as string },
  },
  {
    key: 'course' as ChipKey, label: t('课程'), active: !!filters.course, value: filters.course || '', current: filters.course,
    options: courseOptions.value.map(c => ({ value: c, label: c, count: rows.value.filter(r => r.courseName === c).length })),
    clear: () => { filters.course = '' }, set: (v: any) => { filters.course = v as string },
  },
])
const hasFilter = computed(() => !!(filters.keyword || filters.status || filters.semester || filters.course || filters.college))
function clearFilters() {
  filters.keyword = ''; filters.status = ''; filters.semester = ''; filters.course = ''; filters.college = ''; page.value = 1
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
  const k = filters.keyword.trim()
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
function pyOf(r: any) { return initialsOf(`${r.studentName || ''} ${r.studentNo || ''} ${r.courseName || ''} ${r.college || ''}`) }
function matchText(haystack: string, py: string, qq: string): boolean {
  const ql = qq.toLowerCase().replace(/\s+/g, '')
  if (!ql) return true
  if ((haystack || '').toLowerCase().replace(/\s+/g, '').includes(ql)) return true
  return !!py && py.includes(ql)
}
function openCommand() { cmdTerm.value = filters.keyword; cmdCursor.value = 0; cmdOpen.value = true; nextTick(() => cmdInput.value?.focus()) }
function closeCommand(clear: boolean) {
  cmdOpen.value = false
  if (clear) { cmdTerm.value = ''; filters.keyword = '' }
  else { cmdTerm.value = filters.keyword }
}
watch(cmdTerm, v => { if (cmdOpen.value) filters.keyword = v })
const cmdRows = computed(() => {
  const qq = cmdTerm.value.trim()
  const arr = !qq ? rows.value.slice(0, 8)
    : rows.value.filter(r => matchText(`${r.studentName || ''} ${r.studentNo || ''} ${r.courseName || ''} ${r.college || ''}`, pyOf(r), qq)).slice(0, 8)
  return arr
})
const cmdCourses = computed(() => {
  const qq = cmdTerm.value.trim()
  return courseOptions.value.map(c => ({ name: c, count: rows.value.filter(r => r.courseName === c).length }))
    .filter(o => !qq || matchText(o.name, initialsOf(o.name), qq)).slice(0, 6)
})
type CmdItem = { kind: 'row' | 'course'; raw: any }
const cmdItems = computed<CmdItem[]>(() => [
  ...cmdRows.value.map(r => ({ kind: 'row' as const, raw: r })),
  ...cmdCourses.value.map(c => ({ kind: 'course' as const, raw: c })),
])
watch(cmdItems, () => { if (cmdCursor.value >= cmdItems.value.length) cmdCursor.value = 0 })
function selectCmd(item: CmdItem | undefined) {
  if (!item) return
  if (item.kind === 'row') { mkDetail.value = item.raw; cmdOpen.value = false; cmdTerm.value = ''; filters.keyword = '' }
  else { filters.course = item.raw.name; cmdOpen.value = false; cmdTerm.value = ''; filters.keyword = ''; ElMessage.success(t('已按课程筛选：{n}', { n: item.raw.name })) }
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

onMounted(() => { load(); loadSemesters(); loadConfig(); loadClassrooms(); loadTeachers(); window.addEventListener('keydown', onGlobalKey) })
onBeforeUnmount(() => { window.removeEventListener('keydown', onGlobalKey) })
</script>

<style scoped>
.mm-page { display: flex; flex-direction: column; gap: 16px; }

/* ===== 页头 ===== */
.mm-hero {
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
  padding: 22px 24px;
  border: 1px solid var(--border);
  border-radius: 18px;
  background:
    radial-gradient(640px 260px at 6% -30%, rgba(31, 143, 124, .2), transparent 62%),
    linear-gradient(128deg, var(--card), var(--card-bg-2, var(--card)));
  box-shadow: 0 1px 2px rgba(16, 42, 52, .04), 0 14px 34px rgba(16, 42, 52, .05);
}
.mm-hero::after {
  content: '';
  position: absolute;
  right: -88px;
  top: -120px;
  width: 300px;
  height: 300px;
  border-radius: 50%;
  border: 1px dashed rgba(31, 143, 124, .28);
  box-shadow: 0 0 0 44px rgba(31, 143, 124, .045), 0 0 0 88px rgba(31, 143, 124, .025);
  pointer-events: none;
}
.mm-hero-copy { position: relative; z-index: 1; }
.mm-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: .12em;
  text-transform: uppercase;
  color: var(--el-color-primary, #1f8f7c);
}
.mm-pulse {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  box-shadow: 0 0 0 4px rgba(31, 143, 124, .16);
}
.mm-hero h2 {
  margin: 8px 0 4px;
  font-size: 24px;
  line-height: 1.2;
  letter-spacing: .01em;
  color: var(--text);
}
.mm-hero p { margin: 0; font-size: 13px; color: var(--text-light); }
.mm-actions { position: relative; z-index: 1; display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.mm-filter { width: 146px; }
.mm-filter :deep(.el-input__wrapper),
.mm-filter :deep(.el-select__wrapper) {
  border-radius: 10px;
  box-shadow: 0 0 0 1px var(--border) inset;
  background: color-mix(in srgb, var(--card) 86%, transparent);
}
.mm-filter :deep(.el-input__wrapper.is-focus),
.mm-filter :deep(.el-select__wrapper.is-focused) {
  box-shadow: 0 0 0 1px var(--el-color-primary, #1f8f7c) inset;
}

/* ===== 通用按钮 ===== */
.mm-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 36px;
  padding: 0 16px;
  border-radius: 10px;
  border: 1px solid transparent;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: transform .16s ease, box-shadow .16s ease, border-color .16s ease, color .16s ease;
}
.mm-btn.solid {
  background: linear-gradient(135deg, var(--el-color-primary, #1f8f7c), var(--el-color-primary-dark-2, #167462));
  color: #fff;
  box-shadow: 0 7px 18px rgba(31, 143, 124, .22);
}
.mm-btn.solid:hover { transform: translateY(-1px); box-shadow: 0 11px 24px rgba(31, 143, 124, .28); }
.mm-btn.ghost { background: transparent; border-color: var(--border); color: var(--text-light); }
.mm-btn.ghost:hover { border-color: var(--el-color-primary, #1f8f7c); color: var(--el-color-primary, #1f8f7c); }
.mm-plus { font-size: 16px; font-weight: 400; line-height: 1; }

/* ===== 卡片 ===== */
.mm-card {
  border: 1px solid var(--border);
  border-radius: 16px;
  background: var(--card);
  box-shadow: 0 1px 2px rgba(16, 42, 52, .035), 0 10px 28px rgba(16, 42, 52, .04);
}

/* ===== 参数窗口 ===== */
.mm-config { padding: 18px 20px 20px; }
.mm-card-head { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.mm-card-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  flex: none;
  border-radius: 11px;
  color: var(--el-color-primary, #1f8f7c);
  background: color-mix(in srgb, var(--el-color-primary, #1f8f7c) 12%, transparent);
}
.mm-card-icon svg { width: 20px; height: 20px; }
.mm-card-title h3 { margin: 0; font-size: 14px; color: var(--text); }
.mm-card-title p { margin: 3px 0 0; font-size: 12px; color: var(--text-lighter); }
.mm-config-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(140px, 1fr)) auto;
  gap: 12px;
  align-items: end;
}
.mm-field { display: flex; flex-direction: column; gap: 5px; }
.mm-field > span { font-size: 11px; font-weight: 600; letter-spacing: .04em; color: var(--text-lighter); }
.mm-save { height: 32px; padding: 0 18px; white-space: nowrap; }

/* ===== 指标卡 ===== */
.mm-stats { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 12px; }
.mm-stat {
  position: relative;
  overflow: hidden;
  padding: 14px 16px 13px;
  border: 1px solid var(--border);
  border-radius: 14px;
  background: var(--card);
  box-shadow: 0 1px 2px rgba(16, 42, 52, .035);
  transition: transform .18s ease, box-shadow .18s ease;
}
.mm-stat::before {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 3px;
  background: var(--accent);
}
.mm-stat::after {
  content: '';
  position: absolute;
  right: -26px;
  bottom: -34px;
  width: 92px;
  height: 92px;
  border-radius: 50%;
  background: var(--accent);
  opacity: .075;
  pointer-events: none;
}
.mm-stat:hover { transform: translateY(-2px); box-shadow: 0 12px 26px rgba(16, 42, 52, .09); }
.mm-stat-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.mm-stat-head span { font-size: 12px; font-weight: 600; color: var(--text-light); }
.mm-stat-head i { width: 7px; height: 7px; border-radius: 50%; background: var(--accent); box-shadow: 0 0 0 4px color-mix(in srgb, var(--accent) 14%, transparent); }
.mm-stat b { display: block; margin-top: 7px; font-size: 28px; line-height: 1; letter-spacing: -.02em; color: var(--text); font-family: var(--atlas-mono, inherit); }
.mm-stat small { display: block; margin-top: 6px; font-size: 11px; color: var(--text-lighter); }

/* ===== 名单 ===== */
.mm-table-card { overflow: hidden; }
.mm-table-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 20px 14px;
  border-bottom: 1px solid var(--border);
}
.mm-table-head h3 { margin: 0; font-size: 15px; color: var(--text); }
.mm-table-head p { margin: 3px 0 0; font-size: 12px; color: var(--text-lighter); }
.mm-count {
  flex: none;
  padding: 5px 11px;
  border-radius: 999px;
  font-size: 12px;
  color: var(--text-light);
  background: color-mix(in srgb, var(--el-color-primary, #1f8f7c) 9%, transparent);
}
.mm-count b { color: var(--el-color-primary, #1f8f7c); font-size: 13px; }
.mm-table-scroll { max-height: 560px; overflow: auto; }
.mm-table { width: 100%; border-collapse: separate; border-spacing: 0; font-size: 13px; }
.mm-table thead th {
  position: sticky;
  top: 0;
  z-index: 1;
  padding: 10px 14px;
  text-align: left;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: .04em;
  color: var(--text-lighter);
  background: var(--table-head-bg, #eef4f1);
  border-bottom: 1px solid var(--border);
  white-space: nowrap;
}
.mm-table tbody td {
  padding: 12px 14px;
  border-bottom: 1px solid var(--border);
  color: var(--text);
  vertical-align: middle;
}
.mm-table tbody tr { transition: background .14s ease; }
.mm-table tbody tr:hover td { background: color-mix(in srgb, var(--el-color-primary, #1f8f7c) 4%, transparent); }
.mm-table tbody tr:last-child td { border-bottom: none; }
.mm-student { display: flex; align-items: center; gap: 10px; min-width: 132px; }
.mm-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  flex: none;
  border-radius: 50%;
  font-size: 13px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(140deg, var(--el-color-primary, #1f8f7c), var(--el-color-primary-dark-2, #167462));
  box-shadow: 0 4px 10px rgba(31, 143, 124, .2);
}
.mm-student-text { display: flex; flex-direction: column; gap: 2px; }
.mm-student-text b { font-size: 13px; color: var(--text); }
.mm-student-text small, .mm-table tbody td small { font-size: 11px; color: var(--text-lighter); }
.mm-strong { font-weight: 600; color: var(--text); }
.mm-muted { color: var(--text-lighter); }
.mm-chip {
  display: inline-flex;
  align-items: center;
  padding: 2px 9px;
  border-radius: 7px;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-color-primary, #1f8f7c);
  background: color-mix(in srgb, var(--el-color-primary, #1f8f7c) 10%, transparent);
  white-space: nowrap;
}
.mm-chip.soft { color: var(--text-light); background: color-mix(in srgb, var(--text-light) 9%, transparent); }
.mm-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  white-space: nowrap;
  background: rgba(47, 156, 140, .1);
  color: #217a6d;
}
.mm-tag i { width: 6px; height: 6px; border-radius: 50%; background: currentColor; }
.mm-tag.ELIGIBLE { background: rgba(217, 119, 6, .12); color: var(--el-color-warning, #a67c34); }
.mm-tag.REGISTERED, .mm-tag.SCHEDULED { background: rgba(124, 108, 240, .13); color: #6d5bd0; }
.mm-tag.PASSED { background: rgba(5, 150, 105, .12); color: var(--el-color-success, #16805f); }
.mm-tag.FAILED, .mm-tag.ABSENT { background: rgba(225, 29, 72, .11); color: var(--el-color-danger, #c85346); }
.mm-tag.CANCELLED { background: rgba(100, 116, 139, .14); color: var(--text-light); }
.mm-score {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 38px;
  padding: 3px 9px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 800;
  font-family: var(--atlas-mono, inherit);
}
.mm-score.pass { color: var(--el-color-success, #16805f); background: rgba(5, 150, 105, .1); }
.mm-score.fail { color: var(--el-color-danger, #c85346); background: rgba(225, 29, 72, .1); }
.mm-th-ops { text-align: right !important; }
.mm-ops { display: flex; gap: 6px; justify-content: flex-end; white-space: nowrap; }
.mm-op {
  height: 28px;
  padding: 0 12px;
  border-radius: 8px;
  border: 1px solid var(--border);
  background: transparent;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-light);
  cursor: pointer;
  transition: .16s ease;
}
.mm-op.primary { color: var(--el-color-primary, #1f8f7c); border-color: color-mix(in srgb, var(--el-color-primary, #1f8f7c) 35%, transparent); }
.mm-op.primary:hover { background: color-mix(in srgb, var(--el-color-primary, #1f8f7c) 12%, transparent); }
.mm-op.danger { color: var(--el-color-danger, #c85346); border-color: color-mix(in srgb, var(--el-color-danger, #c85346) 30%, transparent); }
.mm-op.danger:hover { background: color-mix(in srgb, var(--el-color-danger, #c85346) 10%, transparent); }
.mm-empty { display: flex; flex-direction: column; align-items: center; gap: 5px; padding: 34px 20px; }
.mm-empty-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 46px;
  height: 46px;
  margin-bottom: 4px;
  border-radius: 50%;
  border: 1px dashed var(--border);
  font-size: 20px;
  color: var(--text-lighter);
}
.mm-empty b { font-size: 13px; color: var(--text-light); }
.mm-empty small { font-size: 12px; color: var(--text-lighter); }

/* ===== 排考弹窗 ===== */
.mm-dialog :deep(.el-dialog) { border-radius: 18px; overflow: hidden; }
.mm-dialog :deep(.el-dialog__header) {
  margin: 0; padding: 18px 22px 14px; border-bottom: 1px solid var(--border);
}
.mm-dialog :deep(.el-dialog__title) {
  font-family: var(--atlas-serif); font-size: 15px; font-weight: 600;
  letter-spacing: .08em; color: var(--text);
}
.mm-dialog :deep(.el-dialog__body) { padding: 18px 22px 6px; }
.mm-dialog :deep(.el-dialog__footer) {
  padding: 14px 22px 18px; border-top: 1px dashed var(--border);
  display: flex; justify-content: flex-end; gap: 10px;
}
.mm-form { display: flex; flex-direction: column; gap: 15px; }
.mm-who { display: flex; align-items: center; gap: 10px; }
.mm-who-avatar {
  width: 34px; height: 34px; flex: 0 0 34px; border-radius: 50%;
  display: grid; place-items: center;
  font-size: 14px; font-weight: 700; color: var(--primary-deep, #167462);
  background: var(--primary-bg, rgba(31, 143, 124, .1));
  border: 1px solid color-mix(in srgb, var(--primary) 26%, transparent);
}
.mm-who-txt { display: flex; flex-direction: column; line-height: 1.35; }
.mm-who-txt b { font-size: 13.5px; color: var(--text); }
.mm-who-txt small { font-size: 11.5px; color: var(--text-lighter); }
.mm-field { display: flex; flex-direction: column; gap: 6px; }
.mm-lb {
  display: flex; align-items: center; gap: 6px;
  font-size: 12px; font-weight: 600; letter-spacing: .02em; color: var(--text-light);
}
.mm-lb svg {
  width: 13px; height: 13px; flex: 0 0 13px;
  fill: none; stroke: var(--primary, #1f8f7c); stroke-width: 1.7;
  stroke-linecap: round; stroke-linejoin: round; opacity: .9;
}
.mm-field :deep(.el-input__wrapper),
.mm-field :deep(.el-select__wrapper) {
  min-height: 38px; padding: 1px 12px; font-size: 13px;
  border-radius: 11px;
  background: var(--card-bg-2, #f3f7f5);
  box-shadow: 0 0 0 1px var(--border) inset;
  transition: box-shadow .18s, background .18s;
}
.mm-field :deep(.el-input__wrapper:hover),
.mm-field :deep(.el-select__wrapper:hover) { box-shadow: 0 0 0 1px color-mix(in srgb, var(--primary) 45%, var(--border)) inset; }
.mm-field :deep(.el-input__wrapper.is-focus),
.mm-field :deep(.el-select__wrapper.is-focused) {
  background: var(--card);
  box-shadow: 0 0 0 1.5px var(--primary) inset, 0 0 0 4px var(--primary-bg, rgba(31, 143, 124, .1));
}
.mm-field :deep(.el-input__inner),
.mm-field :deep(.el-select__selected-item) { font-size: 13px; color: var(--text); }
.mm-field :deep(.el-input__inner::placeholder),
.mm-field :deep(.el-select__placeholder) { color: var(--text-lighter); }
.mm-field :deep(.el-select__caret),
.mm-field :deep(.el-input__icon) { color: var(--text-lighter); }
.mm-hint { font-size: 11px; color: var(--text-lighter); margin-left: 1px; }

:global(.mm-drop) {
  border: 1px solid var(--border); border-radius: 12px;
  box-shadow: var(--shadow-md, 0 12px 28px rgba(16, 42, 52, .16));
  padding: 5px;
}
:global(.mm-drop .el-select-dropdown__item) {
  height: 34px; line-height: 34px; font-size: 13px;
  border-radius: 8px; padding: 0 10px; margin: 1px 0;
}
:global(.mm-drop .el-select-dropdown__item.is-hovering) { background: var(--primary-bg, rgba(31, 143, 124, .1)); }
:global(.mm-drop .el-select-dropdown__item.is-selected) { color: var(--primary, #1f8f7c); font-weight: 600; }
:global(.mm-drop .el-select-dropdown__empty) { font-size: 12px; color: var(--text-lighter); padding: 14px 0; }
:global(.mm-date.el-popper) {
  border: 1px solid var(--border); border-radius: 12px;
  box-shadow: var(--shadow-md, 0 12px 28px rgba(16, 42, 52, .16));
}
:global(.mm-date .el-picker-panel__icon-btn) { color: var(--text-light); }
:global(.mm-date .el-date-picker__header-label) { color: var(--text); font-weight: 600; }
:global(.mm-date .el-date-table td.current:not(.disabled) .el-date-table-cell__text) { background: var(--primary, #1f8f7c); }
:global(.mm-date .el-date-table td.today .el-date-table-cell__text) { color: var(--primary, #1f8f7c); font-weight: 700; }

@media (max-width: 1180px) {
  .mm-config-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .mm-save { grid-column: span 3; justify-self: start; }
  .mm-stats { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}
@media (max-width: 760px) {
  .mm-hero { flex-direction: column; align-items: flex-start; }
  .mm-actions { width: 100%; }
  .mm-filter { flex: 1 1 140px; width: auto; }
  .mm-config-grid { grid-template-columns: 1fr 1fr; }
  .mm-save { grid-column: span 2; }
  .mm-stats { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

/* ================= 检索体验升级：顶栏 / 筛选 chips / 命令面板 / 抽屉 / 分页 ================= */
.mm-page { --g: #10b981; --g-dark: #059669; --g-soft: rgba(16,185,129,.10); }
.ic { display: block; }
.mk-topbar { position: sticky; top: 0; z-index: 40; display: flex; align-items: center; gap: 16px; flex-wrap: wrap; padding: 12px 16px; background: color-mix(in srgb, var(--card, #fff) 86%, transparent); backdrop-filter: saturate(1.4) blur(14px); -webkit-backdrop-filter: saturate(1.4) blur(14px); border: 0.5px solid var(--border, #e5e7eb); border-radius: 16px; box-shadow: 0 1px 2px rgba(16,24,40,.03), 0 8px 24px rgba(16,24,40,.04); }
.mk-brand { display: flex; align-items: center; gap: 12px; flex: 0 0 auto; }
.mk-brand-icon { width: 40px; height: 40px; border-radius: 12px; display: grid; place-items: center; color: #fff; background: linear-gradient(135deg, #34d399, var(--g-dark)); box-shadow: 0 4px 12px rgba(16,185,129,.32); }
.mk-brand-icon .ic { width: 21px; height: 21px; }
.mk-title { font-size: 17px; font-weight: 700; color: var(--text, #0f172a); line-height: 1.2; }
.mk-subtitle { font-size: 12px; color: var(--text-lighter, #94a3b8); margin-top: 2px; }
.mk-search-trigger { flex: 1 1 320px; max-width: 520px; min-width: 180px; height: 38px; display: flex; align-items: center; gap: 9px; padding: 0 10px 0 14px; border: 1px solid var(--border, #e5e7eb); border-radius: 999px; background: var(--card, #fff); color: var(--text-lighter, #94a3b8); cursor: pointer; transition: all .18s; font-family: inherit; }
.mk-search-trigger:hover { border-color: var(--g); box-shadow: 0 0 0 3px var(--g-soft); }
.mk-search-trigger .ic { width: 17px; height: 17px; flex: 0 0 auto; }
.mk-search-ph { flex: 1; text-align: left; font-size: 13px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.mk-kbd { font-family: inherit; font-size: 11px; line-height: 1; white-space: nowrap; padding: 4px 7px; border-radius: 6px; border: 1px solid var(--border, #e5e7eb); background: color-mix(in srgb, var(--card, #fff) 92%, #000 8%); color: var(--text-light, #64748b); flex: 0 0 auto; }
.mk-top-actions { display: flex; align-items: center; gap: 8px; flex: 0 0 auto; margin-left: auto; }
.mk-btn { display: inline-flex; align-items: center; justify-content: center; gap: 6px; height: 36px; padding: 0 14px; border-radius: 10px; font-family: inherit; font-size: 13px; font-weight: 600; cursor: pointer; transition: all .16s; white-space: nowrap; border: 1px solid transparent; }
.mk-btn-solid { background: var(--g); color: #fff; box-shadow: 0 2px 8px rgba(16,185,129,.26); }
.mk-btn-solid:hover { background: var(--g-dark); }
.mk-btn-outline { background: var(--card, #fff); color: var(--text, #334155); border-color: var(--border, #e5e7eb); }
.mk-btn-outline:hover { border-color: var(--g); color: var(--g); }
.mk-filtercard { padding: 12px 14px; border: 0.5px solid var(--border); border-radius: 14px; background: var(--card); }
.mk-chip-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.mk-chip-wrap { position: relative; }
.mk-chip { display: inline-flex; align-items: center; gap: 6px; height: 32px; padding: 0 11px; border: 1px solid var(--border); border-radius: 999px; background: var(--card); color: var(--text-light); font-family: inherit; font-size: 12.5px; cursor: pointer; transition: all .16s; }
.mk-chip:hover { border-color: var(--g); color: var(--g); }
.mk-chip.on { background: var(--g-soft); border-color: var(--g); color: var(--g-dark); font-weight: 600; }
.mk-chip-label { white-space: nowrap; }
.mk-chip-value { max-width: 140px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--g-dark); }
.mk-chip-arrow { width: 13px; height: 13px; transition: transform .16s; }
.mk-chip-arrow.open { transform: rotate(180deg); }
.mk-reset { border: none; background: none; color: var(--text-lighter); font-family: inherit; font-size: 12.5px; cursor: pointer; padding: 6px 8px; }
.mk-reset:hover { color: #ef4444; }
.mk-dropdown { position: absolute; top: calc(100% + 6px); left: 0; z-index: 30; min-width: 190px; max-height: 320px; overflow-y: auto; padding: 6px; border: 0.5px solid var(--border); border-radius: 12px; background: var(--card); box-shadow: 0 12px 32px rgba(16,24,40,.14); }
.mk-opt { display: flex; align-items: center; justify-content: space-between; gap: 12px; width: 100%; border: none; background: none; font-family: inherit; font-size: 12.5px; color: var(--text-light); cursor: pointer; padding: 8px 10px; border-radius: 8px; text-align: left; }
.mk-opt:hover { background: var(--g-soft); color: var(--g); }
.mk-opt.on { background: var(--g-soft); color: var(--g-dark); font-weight: 600; }
.mk-opt-n { margin-left: 8px; font-size: 11px; color: var(--text-lighter); font-variant-numeric: tabular-nums; }
.mk-opt-check { color: var(--g); font-weight: 700; }
.mk-row { cursor: pointer; }
.mk-row:hover { background: var(--g-soft); }
.mk-empty { display: flex; flex-direction: column; align-items: center; text-align: center; padding: 28px 20px; }
.mk-empty-icon { font-size: 32px; color: var(--g); opacity: .55; }
.mk-empty b { font-size: 14px; color: var(--text); margin-top: 4px; }
.mk-empty small { font-size: 12px; color: var(--text-lighter); margin-top: 4px; }
.mk-suggest { display: flex; align-items: center; gap: 7px; flex-wrap: wrap; justify-content: center; margin-top: 14px; }
.mk-suggest-label { font-size: 12px; color: var(--text-light); }
.mk-suggest-chip { border: 1px solid var(--border); background: var(--card); color: var(--g-dark); font-family: inherit; font-size: 12.5px; padding: 6px 12px; border-radius: 999px; cursor: pointer; transition: all .15s; }
.mk-suggest-chip:hover { border-color: var(--g); background: var(--g-soft); }
.mk-empty-reset { margin-top: 14px; border: none; background: none; color: var(--g); font-family: inherit; font-size: 12.5px; cursor: pointer; text-decoration: underline; text-underline-offset: 3px; }
.mk-pagebar { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 10px 14px; border-top: 1px solid var(--border); }
.mk-pageinfo { font-size: 12px; color: var(--text-light); }
.mk-pager { display: flex; align-items: center; gap: 4px; }
.mk-pg-btn { min-width: 28px; height: 28px; padding: 0 7px; border: 1px solid var(--border); border-radius: 8px; background: var(--card); color: var(--text-light); font-family: inherit; font-size: 12px; cursor: pointer; }
.mk-pg-btn:hover:not(:disabled) { border-color: var(--g); color: var(--g); }
.mk-pg-btn.active { background: var(--g); border-color: var(--g); color: #fff; font-weight: 700; }
.mk-pg-btn:disabled { opacity: .4; cursor: not-allowed; }
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
.cmd-footer .mk-kbd { padding: 2px 6px; }
.cmd-footer-tip { margin-left: auto; color: var(--g); }
@keyframes cmdFade { from { opacity: 0 } to { opacity: 1 } }
@keyframes cmdUp { from { opacity: 0; transform: translateY(-10px) scale(.985) } to { opacity: 1; transform: none } }
.mk-mask { position: fixed !important; inset: 0; background: rgba(0,0,0,.42); z-index: 3500 !important; display: flex; justify-content: flex-end; animation: cmdFade .16s ease; }
.mk-drawer { width: 400px; max-width: 92vw; height: 100%; background: var(--card); border-left: 0.5px solid var(--border); display: flex; flex-direction: column; animation: drawerIn .22s cubic-bezier(.16,1,.3,1); }
@keyframes drawerIn { from { transform: translateX(24px); opacity: .6 } to { transform: none; opacity: 1 } }
.mk-drawer-h { display: flex; align-items: center; padding: 18px 20px; border-bottom: 0.5px solid var(--border); }
.mk-drawer-h b { font-size: 16px; color: var(--text); }
.mk-drawer-h span { display: block; font-size: 12px; color: var(--text-lighter); margin-top: 3px; }
.mk-close { margin-left: auto; border: none; background: none; font-size: 24px; color: var(--text-light); cursor: pointer; line-height: 1; }
.mk-drawer-body { flex: 1; overflow-y: auto; padding: 12px 20px; }
.mk-field { display: flex; justify-content: space-between; gap: 12px; padding: 11px 0; border-bottom: 0.5px solid var(--border); font-size: 12.5px; }
.mk-field span { color: var(--text-lighter); flex: 0 0 auto; }
.mk-field b { color: var(--text); font-weight: 500; text-align: right; word-break: break-all; }
.mk-drawer-f { display: flex; gap: 8px; flex-wrap: wrap; padding: 14px 20px; border-top: 0.5px solid var(--border); }
@media (max-width: 1100px) { .mk-search-trigger { order: 3; flex-basis: 100%; max-width: none; } .mk-top-actions { order: 2; } }
@media (max-width: 560px) { .mk-topbar { padding: 12px; } .mk-kbd { display: none; } }

/* ===== 名单视图切换 ===== */
.mm-viewseg { display: inline-flex; padding: 3px; border-radius: 10px; background: color-mix(in srgb, var(--text-light) 8%, transparent); border: 1px solid var(--border); }
.mm-viewseg button { border: none; background: transparent; cursor: pointer; padding: 5px 14px; border-radius: 8px; font-family: inherit; font-size: 12.5px; font-weight: 700; color: var(--text-light); transition: all .15s; }
.mm-viewseg button.on { background: var(--card-bg, #fff); color: var(--el-color-primary, #1f8f7c); box-shadow: 0 1px 6px rgba(0,0,0,.14); }
.mm-csum td { vertical-align: middle; }
.mm-cnum { font-variant-numeric: tabular-nums; text-align: center; }
.mm-cnum.bad { color: #e11d48; font-weight: 700; }
.mm-csum .mm-cnum b { color: var(--text); }
.mm-course-tip { margin-top: 10px; padding: 9px 12px; border-radius: 10px; background: color-mix(in srgb, var(--text-light) 7%, transparent); font-size: 12px; color: var(--text-light); }

</style>
