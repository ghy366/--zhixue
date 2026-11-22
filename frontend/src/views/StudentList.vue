<template>
  <div class="page-wrap stats-page liquid sl-page">
    <!-- ===== 顶部 sticky 导航栏 ===== -->
    <header class="sl-topbar">
      <div class="sl-brand">
        <div class="sl-brand-icon">
          <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/></svg>
        </div>
        <div class="sl-brand-text">
          <div class="sl-title">{{ t('学生名单') }}</div>
          <div class="sl-subtitle">{{ selectedCourse ? selectedCourse.courseName + ' · ' + selectedCourse.classNo : t('请选择教学班') }} · {{ t('共 {n} 人', { n: totalCount }) }}</div>
        </div>
      </div>
      <button class="sl-search-trigger" @click="openCommand" :aria-label="t('打开搜索')">
        <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>
        <span class="sl-search-ph">{{ filters.keyword || t('搜索学号 / 姓名 / 专业 / 班级，支持拼音首字母…') }}</span>
        <kbd class="sl-kbd">{{ isMac ? '⌘' : 'Ctrl+' }} K</kbd>
      </button>
    </header>

    <div class="stats-grid">
      <div class="stat-card"><div class="stat-icon green" v-html="svgIcon('check-circle')" /><div><div class="stat-value">{{ approvedCount }}</div><div class="stat-label">{{ t('已录取') }}</div></div></div>
      <div class="stat-card"><div class="stat-icon amber" v-html="svgIcon('hourglass')" /><div><div class="stat-value">{{ pendingCount }}</div><div class="stat-label">{{ t('待录取') }}</div></div></div>
      <div class="stat-card"><div class="stat-icon blue" v-html="svgIcon('users')" /><div><div class="stat-value">{{ totalCount }}</div><div class="stat-label">{{ t('总报名') }}</div></div></div>
    </div>

    <!-- 课程选择器 -->
    <div class="section">
      <div class="section-header">
        <div class="section-title">{{ t('选择教学班') }}</div>
        <div class="tab-group">
          <button v-for="c in courses" :key="c.id" class="tab-btn"
            :class="{active: selectedCourse?.id === c.id}" @click="selectCourse(c)">
            {{ c.courseName }} · {{ c.classNo }}
          </button>
        </div>
      </div>

      <!-- 筛选轨道 chips：状态，读写同一个 filters -->
      <div class="sl-filtercard">
        <div class="sl-chip-row">
          <button class="sl-chip" :class="{ on: !filters.status }" @click="filters.status = ''">{{ t('全部学生') }}</button>
          <button class="sl-chip" :class="{ on: filters.status === 'approved' }" @click="filters.status = 'approved'">{{ t('已录取') }} <span class="sl-chip-n">{{ studentsAll.filter(s => s.status === 'approved').length }}</span></button>
          <button class="sl-chip" :class="{ on: filters.status === 'pending' }" @click="filters.status = 'pending'">{{ t('待录取') }} <span class="sl-chip-n">{{ studentsAll.filter(s => s.status !== 'approved').length }}</span></button>
          <button v-if="hasFilter" class="sl-reset" @click="clearFilters">{{ t('清空') }}</button>
        </div>
      </div>

      <div v-if="selectedCourse && currentStudents.length">
        <!-- ===== 学生画像 ===== -->
        <div class="sl-profile" v-if="totalCount">
          <div class="sl-panel">
            <div class="sl-title">{{ t('投分分布') }} <small>{{ t('平均 {a} 分 · 最高 {m} 分', { a: avgPoints, m: maxPoints }) }}</small></div>
            <div v-if="pointBuckets.length" class="sl-points">
              <div v-for="b in pointBuckets" :key="b.label" class="sl-point-row">
                <span class="sl-point-label">{{ b.label }}</span>
                <div class="sl-point-track"><i :style="{ width: Math.max(b.count ? 5 : 0, b.count / maxBucket * 100) + '%' }" /></div>
                <span class="sl-point-val">{{ b.count }}</span>
              </div>
            </div>
            <div v-else class="sl-nodata">{{ t('暂无投分数据') }}</div>
          </div>
          <div class="sl-panel">
            <div class="sl-title">{{ t('年级构成') }} <small>{{ t('共 {n} 名学生', { n: totalCount }) }}</small></div>
            <div v-if="gradeRows.length" class="sl-grades">
              <div v-for="g in gradeRows" :key="g.label" class="sl-point-row">
                <span class="sl-point-label">{{ g.label }}</span>
                <div class="sl-point-track"><i class="violet" :style="{ width: Math.max(g.count ? 5 : 0, g.count / gradeMax * 100) + '%' }" /></div>
                <span class="sl-point-val">{{ t('{n} 人', { n: g.count }) }}</span>
              </div>
            </div>
            <div v-else class="sl-nodata">{{ t('学生资料未填写年级') }}</div>
          </div>
        </div>

        <div class="data-table">
          <table>
            <thead>
              <tr><th>{{ t('学号') }}</th><th>{{ t('姓名') }}</th><th>{{ t('专业') }}</th><th>{{ t('班级') }}</th><th>{{ t('状态') }}</th><th>{{ t('操作') }}</th></tr>
            </thead>
          <tbody>
            <tr v-for="s in paged" :key="s.enrollmentId" class="sl-row" @click="openProfile(s)">
              <td>{{ s.studentNo }}</td>
              <td>
                <button class="stu-name" @click.stop="openProfile(s)" :title="t('查看学生画像')">{{ s.studentName }}</button>
              </td>
              <td>{{ s.studentMajor || '-' }}</td>
              <td>{{ s.studentClass || '-' }}</td>
              <td>
                <span class="status-tag" :class="statusCls(s.status)">{{ statusText(s.status) }}</span>
                <span v-if="s.source === 'retake'" class="status-tag status-retake">{{ t('重修') }}</span>
              </td>
              <td>
                <button v-if="s.status === 'approved'" class="btn btn-primary btn-sm" @click.stop="goGrade(s)">{{ t('录入成绩') }}</button>
                <span v-else class="btn btn-ghost btn-sm" style="cursor:not-allowed;color:var(--text-light)">{{ t('待结算') }}</span>
              </td>
            </tr>
          </tbody>
          </table>
          <div v-if="suggestChips.length && !paged.length" class="sl-suggest">
            <span class="sl-suggest-label">{{ t('是不是想找') }}</span>
            <button v-for="sug in suggestChips" :key="sug" class="sl-suggest-chip" @click="filters.keyword = sug">{{ sug }}</button>
          </div>
          <div v-if="filtered.length > pageSize" class="sl-pagebar">
            <span class="sl-pageinfo">{{ t('共 {n} 人 · 第 {p} / {total} 页', { n: filtered.length, p: page, total: totalPages }) }}</span>
            <div class="sl-pager">
              <button class="sl-pg-btn" :disabled="page <= 1" @click="page--">‹</button>
              <button v-for="p in pageItems" :key="p" class="sl-pg-btn" :class="{ active: p === page }" @click="page = p">{{ p }}</button>
              <button class="sl-pg-btn" :disabled="page >= totalPages" @click="page++">›</button>
            </div>
          </div>
        </div>
      </div>
      <div v-else class="empty-state">
        <div class="empty-icon" v-html="svgIcon('users')" />
        <div class="empty-title">{{ selectedCourse ? t('该课程暂无选课学生') : t('请选择一门课程') }}</div>
      </div>
    </div>

    <!-- ===== 学生画像弹窗 ===== -->
    <el-dialog v-model="profileVisible" :title="profile ? t('{n} 的学生画像', { n: profile.student.name }) : t('学生画像')" width="760px" align-center class="spf-dialog" @opened="resetProfileScroll">
      <div v-loading="profileLoading" style="min-height:200px">
        <template v-if="profile">
          <!-- 头部信息 + 预警 -->
          <div class="spf-head">
            <div class="spf-avatar">{{ (profile.student.name || '?').charAt(0) }}</div>
            <div class="spf-info">
              <b>{{ profile.student.name }}</b>
              <span>{{ profile.student.no }} · {{ profile.student.major || '—' }}<template v-if="profile.student.className"> · {{ profile.student.className }}</template><template v-if="profile.student.grade"> · {{ t('{n} 级', { n: profile.student.grade }) }}</template></span>
            </div>
            <div class="spf-warnings">
              <span v-for="(w, i) in profile.warnings" :key="i" class="spf-warn" :class="w.level">{{ w.text }}</span>
              <span v-if="!profile.warnings.length" class="spf-warn ok">{{ t('选课行为稳定') }}</span>
            </div>
          </div>

          <!-- 毕业进度 + 学分 -->
          <div class="spf-row">
            <div class="spf-block">
              <div class="spf-block-title">{{ t('毕业进度') }} <span v-if="nearGraduation" class="spf-near">{{ t('接近毕业') }}</span></div>
              <div class="spf-grad">
                <div class="spf-grad-bar">
                  <i :style="{ width: Math.min(100, profile.graduation.total?.percent || 0) + '%' }" />
                </div>
                <div class="spf-grad-text">
                  <b>{{ profile.graduation.total?.percent || 0 }}%</b>
                  {{ t('已获 {e} / {r} 学分', { e: profile.graduation.total?.earned || 0, r: profile.graduation.total?.required || 0 }) }}
                  <em v-if="profile.graduation.total?.gap">{{ t('还差 {n}', { n: profile.graduation.total.gap }) }}</em>
                  <em v-else class="ok">{{ t('已达标') }}</em>
                </div>
              </div>
            </div>
            <div class="spf-block">
              <div class="spf-block-title">{{ t('学分与成绩') }}</div>
              <div class="spf-stats">
                <div class="spf-stat"><b>{{ profile.stats.totalCredits }}</b><span>{{ t('总学分') }}</span></div>
                <div class="spf-stat"><b>{{ profile.stats.semesterCredits }}</b><span>{{ t('本学期') }}</span></div>
                <div class="spf-stat"><b>{{ profile.stats.avgScore ?? '—' }}</b><span>{{ t('平均分') }}</span></div>
                <div class="spf-stat"><b>{{ profile.stats.gpa ?? '—' }}</b><span>GPA</span></div>
                <div class="spf-stat"><b :class="{ warn: profile.stats.dropped }">{{ profile.stats.dropped }}</b><span>{{ t('退课') }}</span></div>
                <div class="spf-stat"><b :class="{ warn: profile.stats.waitlist }">{{ profile.stats.waitlist }}</b><span>{{ t('候补') }}</span></div>
              </div>
            </div>
          </div>

          <!-- 成绩分布 -->
          <div class="spf-block" style="margin-top:12px">
            <div class="spf-block-title">{{ t('成绩分布') }} <small>{{ t('已公布成绩 {n} 门', { n: scoredCount }) }}</small></div>
            <div class="spf-dist">
              <div v-for="(b, i) in profile.gradeDist" :key="i" class="spf-dist-col">
                <span class="spf-dist-num">{{ b.count || '' }}</span>
                <div class="spf-dist-bar"><i :style="{ height: distH(b.count) + 'px' }" :class="i < 6 ? 'low' : i >= 9 ? 'top' : 'mid'" /></div>
                <small>{{ b.low }}+</small>
              </div>
            </div>
          </div>

          <!-- 已修课程列表 -->
          <div class="spf-block" style="margin-top:12px">
            <div class="spf-block-title">{{ t('修读课程（{n}）', { n: profile.courses.length }) }}</div>
            <div class="spf-courses">
              <table>
                <thead><tr><th>{{ t('课程') }}</th><th>{{ t('类型') }}</th><th>{{ t('学分') }}</th><th>{{ t('轮次') }}</th><th>{{ t('投分') }}</th><th>{{ t('成绩') }}</th><th>{{ t('状态') }}</th></tr></thead>
                <tbody>
                  <tr v-for="c in profile.courses" :key="c.enrollmentId">
                    <td>{{ c.courseName }}</td>
                    <td>{{ c.category || '—' }}</td>
                    <td>{{ c.credit }}</td>
                    <td>{{ t('第 {n} 轮', { n: c.round }) }}</td>
                    <td>{{ c.points ?? 0 }}</td>
                    <td><b :class="scoreCls(c.totalScore)">{{ c.totalScore ?? '—' }}</b></td>
                    <td>{{ statusText(c.status) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </template>
      </div>
    </el-dialog>

    <!-- ================= 命令面板 ================= -->
    <Teleport to="body">
      <div v-if="cmdOpen" class="cmd-overlay" @click="closeCommand(false)">
        <div class="cmd-panel" @click.stop>
          <div class="cmd-input-row">
            <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>
            <input ref="cmdInput" v-model="cmdTerm" :placeholder="t('搜索学号 / 姓名 / 专业 / 班级，支持拼音首字母…')" />
            <kbd class="sl-kbd">esc</kbd>
          </div>
          <div class="cmd-results">
            <template v-if="cmdItems.length">
              <div v-if="cmdStudents.length" class="cmd-group">
                <div class="cmd-group-h">{{ t('学生') }} <span>{{ cmdStudents.length }}</span></div>
                <button v-for="(s, i) in cmdStudents" :key="s.enrollmentId" class="cmd-item" :class="{ active: cmdCursor === i }" @mouseenter="cmdCursor = i" @click="selectCmd(cmdItems[i])">
                  <span class="cmd-item-ico user"><svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/></svg></span>
                  <span class="cmd-item-main"><b>{{ s.studentName }}</b><small>{{ s.studentNo }} · {{ s.studentMajor || '—' }} · {{ s.studentClass || '—' }}</small></span>
                  <span class="cmd-enter" v-if="cmdCursor === i">↵</span>
                </button>
              </div>
              <div v-if="cmdCourses.length" class="cmd-group">
                <div class="cmd-group-h">{{ t('课程') }} <span>{{ cmdCourses.length }}</span></div>
                <button v-for="(c, j) in cmdCourses" :key="c.id" class="cmd-item" :class="{ active: cmdCursor === cmdStudents.length + j }" @mouseenter="cmdCursor = cmdStudents.length + j" @click="selectCmd(cmdItems[cmdStudents.length + j])">
                  <span class="cmd-item-ico org"><svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M4 4.5A2.5 2.5 0 0 1 6.5 2H20v15H6.5A2.5 2.5 0 0 0 4 19.5z"/></svg></span>
                  <span class="cmd-item-main"><b>{{ c.name }}</b><small>{{ t('切换到此课程') }}</small></span>
                  <span class="cmd-enter" v-if="cmdCursor === cmdStudents.length + j">↵</span>
                </button>
              </div>
            </template>
            <div v-else class="cmd-empty">{{ t('没有联想结果，试试学号 / 姓名 / 拼音首字母') }}</div>
          </div>
          <div class="cmd-footer">
            <span><kbd class="sl-kbd">↑</kbd><kbd class="sl-kbd">↓</kbd> {{ t('导航') }}</span>
            <span><kbd class="sl-kbd">↵</kbd> {{ t('选中') }}</span>
            <span class="cmd-footer-tip">{{ t('选中学生看画像 · 选中课程即切换') }}</span>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, toRefs, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import http from '@/api'
import { useAuthStore } from '@/stores/auth'
import { t } from '@/i18n'
import { diceCoefficient } from '@/utils/similarity'
import { initialsOf } from '@/utils/pinyin'

const auth = useAuthStore()
const router = useRouter()
const isMac = typeof navigator !== 'undefined' && /Mac|iPhone|iPad/.test(navigator.platform)
const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`

const courses = ref<any[]>([])
const selectedCourse = ref<any>(null)
const currentStudents = ref<any[]>([])
const studentsAll = ref<any[]>([])
// 单一状态源：列表状态 chips / 搜索 / 命令面板读写同一 filters
const filters = reactive({ keyword: '' as string, status: '' as string })
const page = ref(1)
const pageSize = ref(20)

const filtered = computed(() => {
  const k = filters.keyword.trim().toLowerCase()
  return studentsAll.value.filter(s => {
    if (filters.status === 'approved' && s.status !== 'approved') return false
    if (filters.status === 'pending' && s.status === 'approved') return false
    if (k && !(`${s.studentNo || ''} ${s.studentName || ''} ${s.studentMajor || ''} ${s.studentClass || ''}`.toLowerCase().includes(k))) return false
    return true
  })
})
const totalPages = computed(() => Math.max(1, Math.ceil(filtered.value.length / pageSize.value)))
const paged = computed(() => filtered.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value))
const pageItems = computed(() => Array.from({ length: totalPages.value }, (_, i) => i + 1).filter(p => totalPages.value <= 7 || p === 1 || p === totalPages.value || Math.abs(p - page.value) <= 1))
const hasFilter = computed(() => !!(filters.keyword || filters.status))
function clearFilters() { filters.keyword = ''; filters.status = ''; page.value = 1 }
watch([() => filters.keyword, () => filters.status], () => { page.value = 1 })
watch(totalPages, v => { if (page.value > v) page.value = v })

function statusCls(s: string) {
  if (s === 'approved') return 'status-approved'
  if (s === 'pending') return 'status-pending'
  return 'status-rejected'
}
function statusText(s: string) {
  if (s === 'approved') return t('已录取')
  if (s === 'pending') return t('待录取')
  return t('未录取')
}

const approvedCount = computed(() => currentStudents.value.filter(s => s.status === 'approved').length)
const pendingCount = computed(() => currentStudents.value.filter(s => s.status !== 'approved').length)
const totalCount = computed(() => currentStudents.value.length)

/* ===== 学生画像 ===== */
const pointBuckets = computed(() => {
  if (!currentStudents.value.some(s => s.points != null)) return []
  const edges: Array<[number, number, string]> = [
    [0, 9, t('0-9 分')], [10, 19, t('10-19 分')], [20, 29, t('20-29 分')], [30, 39, t('30-39 分')], [40, 100, t('40 分+')],
  ]
  return edges.map(([lo, hi, label]) => ({
    label,
    count: currentStudents.value.filter(s => {
      const p = Number(s.points) || 0
      return p >= lo && p <= hi
    }).length,
  }))
})
const maxBucket = computed(() => Math.max(1, ...pointBuckets.value.map(b => b.count)))
const avgPoints = computed(() => {
  const pts = currentStudents.value.filter(s => s.points != null).map(s => Number(s.points))
  return pts.length ? (pts.reduce((a, b) => a + b, 0) / pts.length).toFixed(1) : '—'
})
const maxPoints = computed(() => {
  const pts = currentStudents.value.filter(s => s.points != null).map(s => Number(s.points))
  return pts.length ? Math.max(...pts) : '—'
})
const gradeRows = computed(() => {
  const map = new Map<string, number>()
  for (const s of currentStudents.value) {
    if (s.studentGrade == null) continue
    const label = t('{n} 级', { n: s.studentGrade })
    map.set(label, (map.get(label) || 0) + 1)
  }
  return Array.from(map.entries())
    .map(([label, count]) => ({ label, count }))
    .sort((a, b) => b.label.localeCompare(a.label))
})
const gradeMax = computed(() => Math.max(1, ...gradeRows.value.map(g => g.count)))

/* ===== 学生画像弹窗 ===== */
const profileVisible = ref(false)
const profileLoading = ref(false)
const profile = ref<any>(null)
const scoredCount = computed(() => (profile.value?.courses || []).filter((c: any) => c.totalScore != null).length)
const nearGraduation = computed(() => (profile.value?.graduation?.total?.percent || 0) >= 85)
const distMax = computed(() => Math.max(1, ...(profile.value?.gradeDist || []).map((b: any) => b.count)))
function distH(count: number) {
  return count ? Math.max(4, Math.round(count * 42 / distMax.value)) : 0
}
function scoreCls(v: any) {
  if (v == null) return ''
  const n = Number(v)
  if (n >= 90) return 'gold'
  if (n >= 80) return 'good'
  if (n >= 60) return 'mid'
  return 'bad'
}
async function openProfile(s: any) {
  profileVisible.value = true
  profileLoading.value = true
  profile.value = null
  try {
    const r: any = await http.get(`/students/${s.studentId}/profile`)
    profile.value = r?.data || null
  } catch (e: any) {
    ElMessage.error(e?.message || t('学生画像加载失败'))
    profileVisible.value = false
  } finally {
    profileLoading.value = false
  }
}
/** element-plus 打开时会滚动到聚焦元素，把对话框拉出视口——打开后重置滚动 */
function resetProfileScroll() {
  const body = document.querySelector('.spf-dialog .el-dialog__body')
  if (body) body.scrollTop = 0
  const overlay = document.querySelector('.el-overlay-dialog')
  if (overlay) overlay.scrollTop = 0
}

async function loadCourses() {
  try {
    // 我的教学班（平行班）：成员 = 挂班在读学生，而非整门课的年级大名单
    const r: any = await http.get('/teaching-classes', { params: { teacherId: auth.user?.id } })
    courses.value = r?.data || []
    if (!courses.value.length) return
    selectCourse(courses.value[0])
  } catch {}
}

async function selectCourse(c: any) {
  selectedCourse.value = c
  try {
    const r: any = await http.get(`/teaching-classes/${c.id}/members`)
    currentStudents.value = r?.data || []
    studentsAll.value = currentStudents.value
    page.value = 1
  } catch { currentStudents.value = []; studentsAll.value = [] }
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
  for (const s of studentsAll.value) { names.add(s.studentName); if (s.studentNo) names.add(s.studentNo) }
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
function pyOf(s: any) { return initialsOf(`${s.studentName || ''} ${s.studentNo || ''} ${s.studentMajor || ''} ${s.studentClass || ''}`) }
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
const cmdStudents = computed(() => {
  const qq = cmdTerm.value.trim()
  const arr = !qq ? studentsAll.value.slice(0, 8)
    : studentsAll.value.filter(s => matchText(`${s.studentName || ''} ${s.studentNo || ''} ${s.studentMajor || ''} ${s.studentClass || ''}`, pyOf(s), qq)).slice(0, 8)
  return arr
})
const cmdCourses = computed(() => {
  const qq = cmdTerm.value.trim()
  return courses.value.filter(c => !qq || matchText(c.name, initialsOf(c.name), qq)).slice(0, 6)
})
type CmdItem = { kind: 'student' | 'course'; raw: any }
const cmdItems = computed<CmdItem[]>(() => [
  ...cmdStudents.value.map(s => ({ kind: 'student' as const, raw: s })),
  ...cmdCourses.value.map(c => ({ kind: 'course' as const, raw: c })),
])
watch(cmdItems, () => { if (cmdCursor.value >= cmdItems.value.length) cmdCursor.value = 0 })
function selectCmd(item: CmdItem | undefined) {
  if (!item) return
  if (item.kind === 'student') { openProfile(item.raw); cmdOpen.value = false; cmdTerm.value = ''; filters.keyword = '' }
  else { selectCourse(item.raw); cmdOpen.value = false; cmdTerm.value = ''; filters.keyword = '' }
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

function goGrade(s: any) {
  router.push({ name: 'GradeMgr', query: { courseId: selectedCourse.value.courseId, classId: selectedCourse.value.id } })
}

onMounted(() => { loadCourses(); window.addEventListener('keydown', onGlobalKey) })
onBeforeUnmount(() => { window.removeEventListener('keydown', onGlobalKey) })
</script>

<style scoped>
.page-wrap { padding: 4px 0; }
.tab-group { display: flex; gap: 6px; flex-wrap: wrap; }
.tab-btn { padding: 6px 14px; border-radius: 999px; border: 1px solid var(--border); background: transparent; color: var(--text-light); font-size: 13px; cursor: pointer; transition: all .15s; }
.tab-btn:hover { color: var(--text); }
.tab-btn.active { background: var(--primary-deep); color: #fff; border-color: var(--primary-deep); }
.status-tag { font-size: 12px; padding: 1px 8px; border-radius: 999px; }
.status-approved { background: rgba(16,185,129,0.15); color: #10b981; }
.status-pending { background: rgba(245,158,11,0.15); color: #f59e0b; }
.status-rejected { background: rgba(239,68,68,0.15); color: #ef4444; }
.status-retake { background: rgba(124,58,237,0.14); color: #7c3aed; margin-left: 5px; }
.empty-state { text-align: center; padding: 60px 20px; color: var(--text-light); }
.empty-icon { font-size: 48px; opacity: 0.3; margin-bottom: 10px; }

/* ===== 学生画像弹窗 ===== */
:deep(.spf-dialog .el-dialog__body) { max-height: 72vh; overflow-y: auto; padding-top: 10px; }
.stu-name { background: none; border: none; padding: 0; color: var(--primary); font-weight: 600; cursor: pointer; font-size: 13px; }
.stu-name:hover { text-decoration: underline; }
.spf-head { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; padding-bottom: 12px; border-bottom: 1px solid var(--border); }
.spf-avatar { width: 46px; height: 46px; border-radius: 50%; background: linear-gradient(135deg, #2bb187, #38bdf8); color: #fff; display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 18px; flex-shrink: 0; }
.spf-info { display: flex; flex-direction: column; gap: 2px; min-width: 180px; }
.spf-info b { font-size: 16px; color: var(--text); }
.spf-info span { font-size: 12px; color: var(--text-light); }
.spf-warnings { display: flex; flex-direction: column; gap: 4px; margin-left: auto; }
.spf-warn { font-size: 12px; padding: 2px 10px; border-radius: 999px; }
.spf-warn.warn { color: #e11d48; background: rgba(244,63,94,.12); }
.spf-warn.info { color: #d97706; background: rgba(245,158,11,.12); }
.spf-warn.ok { color: #059669; background: rgba(16,185,129,.12); }

.spf-row { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1.2fr); gap: 12px; margin-top: 12px; }
@media (max-width: 700px) { .spf-row { grid-template-columns: minmax(0, 1fr); } }
.spf-block { padding: 12px 14px; border: 1px solid var(--border); border-radius: 11px; }
.spf-block-title { font-size: 12.5px; font-weight: 700; color: var(--text); margin-bottom: 9px; }
.spf-block-title small { font-weight: 500; color: var(--text-light); margin-left: 5px; }
.spf-near { color: #059669; background: rgba(16,185,129,.12); font-size: 12px; padding: 1px 8px; border-radius: 999px; margin-left: 6px; }

.spf-grad-bar { height: 12px; border-radius: 99px; background: rgba(148,163,184,.13); overflow: hidden; margin-bottom: 8px; }
.spf-grad-bar i { display: block; height: 100%; border-radius: 99px; background: linear-gradient(90deg, #2bb187, #38bdf8); transition: width .6s cubic-bezier(.22,.9,.36,1); }
.spf-grad-text { font-size: 12px; color: var(--text-light); }
.spf-grad-text b { color: var(--primary); font-size: 15px; margin-right: 5px; }
.spf-grad-text em { font-style: normal; color: #e11d48; font-weight: 700; margin-left: 6px; }
.spf-grad-text em.ok { color: #059669; }

.spf-stats { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 7px; }
.spf-stat { padding: 7px 6px; border: 1px solid var(--border); border-radius: 9px; text-align: center; }
.spf-stat b { display: block; font-size: 15px; color: var(--text); font-variant-numeric: tabular-nums; }
.spf-stat b.warn { color: #e11d48; }
.spf-stat span { font-size: 12px; color: var(--text-light); }

.spf-dist { display: flex; align-items: flex-end; gap: 6px; height: 64px; }
.spf-dist-col { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 3px; height: 100%; justify-content: flex-end; }
.spf-dist-num { font-size: 12px; font-weight: 700; color: var(--text); font-variant-numeric: tabular-nums; }
.spf-dist-bar { width: 100%; display: flex; align-items: flex-end; justify-content: center; }
.spf-dist-bar i { display: block; width: 70%; border-radius: 4px 4px 0 0; }
.spf-dist-bar i.low { background: rgba(244,63,94,.55); }
.spf-dist-bar i.mid { background: rgba(43,177,135,.6); }
.spf-dist-bar i.top { background: rgba(245,158,11,.65); }
.spf-dist-col small { font-size: 12px; color: var(--text-light); }

.spf-courses { max-height: 240px; overflow-y: auto; }
.spf-courses table { width: 100%; border-collapse: collapse; font-size: 12px; }
.spf-courses th, .spf-courses td { padding: 6px 9px; text-align: left; border-bottom: 1px solid var(--border); }
.spf-courses th { color: var(--text-light); font-weight: 600; background: rgba(148,163,184,.06); }
.spf-courses b.gold { color: #d97706; }
.spf-courses b.good { color: #059669; }
.spf-courses b.mid { color: #2563eb; }
.spf-courses b.bad { color: #e11d48; }

/* ===== 学生画像面板 ===== */
.sl-profile { display: grid; grid-template-columns: minmax(0, 1.15fr) minmax(0, 1fr); gap: 14px; margin-bottom: 16px; }
@media (max-width: 900px) { .sl-profile { grid-template-columns: minmax(0, 1fr); } }
.sl-panel { padding: 13px 15px; border: 1px solid var(--border); border-radius: 12px; background: color-mix(in srgb, var(--card-bg) 55%, transparent); }
.sl-title { font-size: 13px; font-weight: 700; color: var(--text); margin-bottom: 10px; }
.sl-title small { font-weight: 500; color: var(--text-light); margin-left: 8px; font-size: 12px; }
.sl-points, .sl-grades { display: flex; flex-direction: column; gap: 9px; }
.sl-point-row { display: grid; grid-template-columns: 64px minmax(0, 1fr) 52px; align-items: center; gap: 10px; }
.sl-point-label { font-size: 12px; color: var(--text-light); white-space: nowrap; }
.sl-point-track { height: 9px; border-radius: 99px; background: rgba(148,163,184,.14); overflow: hidden; }
.sl-point-track i { display: block; height: 100%; border-radius: 99px; background: linear-gradient(90deg, #06b6d4, #34d399); animation: slGrow .7s cubic-bezier(.22,.9,.36,1) both; }
.sl-point-track i.violet { background: linear-gradient(90deg, #2bb187, #38bdf8); }
@keyframes slGrow { from { transform: scaleX(0); transform-origin: left; } to { transform: scaleX(1); } }
.sl-point-val { text-align: right; font-size: 12px; font-weight: 700; color: var(--text); font-variant-numeric: tabular-nums; }
.sl-nodata { font-size: 12px; color: var(--text-light); opacity: .75; padding: 14px 0; text-align: center; }

/* ================= 检索体验升级：顶栏 / 筛选 chips / 命令面板 / 分页 ================= */
.sl-page { --g: #10b981; --g-dark: #059669; --g-soft: rgba(16,185,129,.10); }
.ic { display: block; }
.sl-topbar { position: sticky; top: 0; z-index: 40; display: flex; align-items: center; gap: 16px; flex-wrap: wrap; padding: 12px 16px; background: color-mix(in srgb, var(--card, #fff) 86%, transparent); backdrop-filter: saturate(1.4) blur(14px); -webkit-backdrop-filter: saturate(1.4) blur(14px); border: 0.5px solid var(--border, #e5e7eb); border-radius: 16px; box-shadow: 0 1px 2px rgba(16,24,40,.03), 0 8px 24px rgba(16,24,40,.04); }
.sl-brand { display: flex; align-items: center; gap: 12px; flex: 0 0 auto; }
.sl-brand-icon { width: 40px; height: 40px; border-radius: 12px; display: grid; place-items: center; color: #fff; background: linear-gradient(135deg, #34d399, var(--g-dark)); box-shadow: 0 4px 12px rgba(16,185,129,.32); }
.sl-brand-icon .ic { width: 21px; height: 21px; }
.sl-title { font-size: 17px; font-weight: 700; color: var(--text, #0f172a); line-height: 1.2; }
.sl-subtitle { font-size: 12px; color: var(--text-lighter, #94a3b8); margin-top: 2px; }
.sl-search-trigger { flex: 1 1 320px; max-width: 520px; min-width: 180px; height: 38px; display: flex; align-items: center; gap: 9px; padding: 0 10px 0 14px; border: 1px solid var(--border, #e5e7eb); border-radius: 999px; background: var(--card, #fff); color: var(--text-lighter, #94a3b8); cursor: pointer; transition: all .18s; font-family: inherit; }
.sl-search-trigger:hover { border-color: var(--g); box-shadow: 0 0 0 3px var(--g-soft); }
.sl-search-trigger .ic { width: 17px; height: 17px; flex: 0 0 auto; }
.sl-search-ph { flex: 1; text-align: left; font-size: 13px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.sl-kbd { font-family: inherit; font-size: 11px; line-height: 1; white-space: nowrap; padding: 4px 7px; border-radius: 6px; border: 1px solid var(--border, #e5e7eb); background: color-mix(in srgb, var(--card, #fff) 92%, #000 8%); color: var(--text-light, #64748b); flex: 0 0 auto; }
.sl-filtercard { padding: 10px 14px; border: 0.5px solid var(--border); border-radius: 12px; background: var(--card); margin: 10px 0 14px; }
.sl-chip-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.sl-chip { display: inline-flex; align-items: center; gap: 6px; height: 30px; padding: 0 12px; border: 1px solid var(--border); border-radius: 999px; background: var(--card); color: var(--text-light); font-family: inherit; font-size: 12.5px; cursor: pointer; transition: all .16s; }
.sl-chip:hover { border-color: var(--g); color: var(--g); }
.sl-chip.on { background: var(--g-soft); border-color: var(--g); color: var(--g-dark); font-weight: 600; }
.sl-chip-n { font-size: 11px; padding: 1px 6px; border-radius: 999px; background: rgba(0,0,0,.06); font-variant-numeric: tabular-nums; }
.sl-chip.on .sl-chip-n { background: rgba(255,255,255,.4); }
.sl-reset { border: none; background: none; color: var(--text-lighter); font-family: inherit; font-size: 12.5px; cursor: pointer; padding: 6px 8px; margin-left: auto; }
.sl-reset:hover { color: #ef4444; }
.sl-row { cursor: pointer; }
.sl-row:hover { background: var(--g-soft); }
.sl-suggest { display: flex; align-items: center; gap: 7px; flex-wrap: wrap; padding: 12px 14px; border-top: 1px solid var(--border); }
.sl-suggest-label { font-size: 12px; color: var(--text-light); }
.sl-suggest-chip { border: 1px solid var(--border); background: var(--card); color: var(--g-dark); font-family: inherit; font-size: 12.5px; padding: 5px 11px; border-radius: 999px; cursor: pointer; transition: all .15s; }
.sl-suggest-chip:hover { border-color: var(--g); background: var(--g-soft); }
.sl-pagebar { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 10px 14px; border-top: 1px solid var(--border); }
.sl-pageinfo { font-size: 12px; color: var(--text-light); }
.sl-pager { display: flex; align-items: center; gap: 4px; }
.sl-pg-btn { min-width: 28px; height: 28px; padding: 0 7px; border: 1px solid var(--border); border-radius: 8px; background: var(--card); color: var(--text-light); font-family: inherit; font-size: 12px; cursor: pointer; }
.sl-pg-btn:hover:not(:disabled) { border-color: var(--g); color: var(--g); }
.sl-pg-btn.active { background: var(--g); border-color: var(--g); color: #fff; font-weight: 700; }
.sl-pg-btn:disabled { opacity: .4; cursor: not-allowed; }
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
.cmd-footer .sl-kbd { padding: 2px 6px; }
.cmd-footer-tip { margin-left: auto; color: var(--g); }
@keyframes cmdFade { from { opacity: 0 } to { opacity: 1 } }
@keyframes cmdUp { from { opacity: 0; transform: translateY(-10px) scale(.985) } to { opacity: 1; transform: none } }
@media (max-width: 1100px) { .sl-search-trigger { order: 3; flex-basis: 100%; max-width: none; } }
@media (max-width: 560px) { .sl-topbar { padding: 12px; } .sl-kbd { display: none; } }
</style>
