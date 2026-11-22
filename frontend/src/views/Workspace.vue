<template>
  <div class="ws-page stats-page liquid">
    <!-- 空状态 -->
    <div v-if="emptyState" class="empty-card">{{ emptyState }}</div>

    <!-- 教师端：左侧收集总览 + 学生列表 / 右侧详情 -->
    <template v-else-if="isTeacher">
      <div class="ws-layout">
        <div class="ws-side">
          <!-- 论文收集总览 -->
          <div class="ws-collect">
            <div class="ws-collect-title">📄 {{ t('论文收集') }}</div>
            <div class="ws-collect-stats">
              <div class="ws-cs ok"><b>{{ submittedCount }}</b><span>{{ t('已提交') }}</span></div>
              <div class="ws-cs bad"><b>{{ unsubmittedCount }}</b><span>{{ t('未提交') }}</span></div>
              <div class="ws-cs amber"><b>{{ pendingReviewCount }}</b><span>{{ t('待批阅') }}</span></div>
            </div>
            <div class="ws-collect-bar">
              <i class="ok" :style="{ width: collectPct + '%' }" />
              <i class="bad" :style="{ width: (100 - collectPct) + '%' }" />
            </div>
            <div class="ws-collect-foot">{{ t('提交率 {pct}% · 共 {n} 名学生', { pct: collectPct, n: teacherStudents.length }) }}</div>
          </div>

          <!-- 评阅任务（三方评分链：教务指派的交叉评阅） -->
          <div v-if="reviewTasks.length" class="ws-collect rt-box">
            <div class="ws-collect-title">🧮 {{ t('评阅任务') }}
              <span class="rt-badge" :class="{ done: reviewTasks.every(x => x.scored) }">
                {{ reviewTasks.filter(x => x.scored).length }} / {{ reviewTasks.length }}
              </span>
            </div>
            <div v-for="rt in reviewTasks" :key="rt.advisorId" class="rt-item">
              <div class="rt-head">
                <b>{{ rt.studentName }}</b>
                <small>{{ rt.studentNo }} · {{ rt.advisorName }} 指导</small>
              </div>
              <div v-if="rt.topic" class="rt-topic">🎯 {{ rt.topic }}</div>
              <template v-if="!rt.scored">
                <div class="rt-form">
                  <input type="number" v-model.number="rt._score" min="0" max="100" class="rt-score" :placeholder="t('评分')">
                  <input v-model="rt._comment" class="rt-comment" :placeholder="t('评语（可选）')">
                  <button class="rt-btn" :disabled="rt._score == null || rt._score < 0 || rt._score > 100" @click="submitReview(rt)">
                    {{ t('提交评分') }}
                  </button>
                </div>
              </template>
              <div v-else class="rt-done">✅ {{ t('已评 {n} 分', { n: rt.reviewerScore }) }}<span v-if="rt.reviewerComment"> · {{ rt.reviewerComment }}</span></div>
            </div>
          </div>

          <!-- 筛选 -->
          <div class="ws-filter">
            <button v-for="f in FILTERS" :key="f.key" class="ws-filter-btn"
                    :class="{ active: curFilter === f.key }" @click="curFilter = f.key">
              {{ t(f.label) }} {{ f.count() }}
            </button>
          </div>

          <div class="section-title" style="margin-top:4px">{{ t('我的学生（{n}）', { n: filteredStudents.length }) }}</div>
          <div id="ws-tea-students">
            <div v-for="s in filteredStudents" :key="s.advisorId"
                 class="ws-teacher-stu-card"
                 :class="{ active: s.advisorId === selectedAdvisorId }"
                 @click="selectStudent(s.advisorId)">
              <div style="flex-shrink:0">
                <div class="ws-stu-avatar">{{ s.student.name?.charAt(0) }}</div>
              </div>
              <div style="flex:1;min-width:0">
                <div style="font-weight:600;font-size:14px;display:flex;align-items:center;gap:6px">
                  {{ s.student.name }}
                  <span v-if="stuPapers(s).count" class="ws-ver-badge" :class="stuPapers(s).status">v{{ stuPapers(s).count }}</span>
                  <span v-else class="ws-ver-badge none">{{ t('未提交') }}</span>
                </div>
                <div style="font-size: 12px;color:var(--text-light);margin-top:2px">{{ s.student.college || '' }} · {{ s.student.major || '' }}</div>
                <div v-if="s.topic" style="font-size: 12px;color:var(--primary);margin-top:3px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">🎯 {{ s.topic }}</div>
                <div v-if="stuPapers(s).count" class="ws-stu-sub">
                  {{ paperLabel(stuPapers(s).status) }} · {{ fmtTime(stuPapers(s).lastAt) }}
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="ws-main">
          <div v-if="!wsDetail && loading" style="color:var(--text-light);text-align:center;padding:40px">{{ t('加载中...') }}</div>
          <WorkspaceDetail v-else-if="wsDetail" :key="'t' + selectedAdvisorId" :ws="wsDetail" role="teacher" :advisor-id="selectedAdvisorId" :start-tab="teacherStartTab" @refresh="reloadDetail" />
        </div>
      </div>
    </template>

    <!-- 学生端：进度面板 + 详情 -->
    <template v-else-if="isStudent">
      <div v-if="!wsDetail && loading" style="color:var(--text-light);text-align:center;padding:40px">{{ t('加载中...') }}</div>
      <template v-else-if="wsDetail">
        <!-- 我的课题进度（与「流程」Tab 同源：开题 → 中期 → 论文终稿 → 答辩） -->
        <div class="stu-progress">
          <div class="sp-head">
            <b>🎓 {{ t('我的课题进度') }}</b>
            <span class="sp-stage">{{ msDoneCount }} / {{ msList.length || 4 }}</span>
          </div>
          <div class="sp-steps" v-if="msList.length">
            <div v-for="(m, i) in msList" :key="m.id" class="sp-step" :class="msState(i)">
              <i class="sp-dot">
                <svg v-if="m.status === 'passed'" viewBox="0 0 16 16" class="sp-check"><path d="M3 8.5l3.2 3.2L13 5" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/></svg>
                <span v-else-if="m.status === 'returned'" class="sp-mark">!</span>
                <span v-else-if="m.status === 'submitted'" class="sp-mark">…</span>
              </i>
              <span>{{ t(m.stageName) }}</span>
            </div>
          </div>
          <div class="sp-meta">
            <span>{{ t('论文') }} <b>v{{ stuPaperCount }}</b></span>
            <span v-if="stuPaperCount" class="sp-status" :class="stuPaperStatus">{{ paperLabel(stuPaperStatus) }}</span>
            <span v-if="plagInfo" class="sp-status" :class="plagChipCls">🔍 {{ plagChipText }}</span>
            <span v-if="defenseTotal != null" class="sp-thesis">🎯 {{ t('毕设总评') }} <b>{{ defenseTotal }}</b><small>（{{ t('导师 40% + 评阅 20% + 答辩 40%') }}）</small></span>
            <span v-if="deadline" class="sp-deadline" :class="deadlineCls">{{ t('要求截止：') }}<b>{{ deadlineText }}</b></span>
            <span v-if="!msAllDone" class="sp-hint">{{ msNextHint }}</span>
            <span v-else class="sp-hint done">🎉 {{ t('全部流程已完成') }}</span>
          </div>
        </div>

        <!-- 我的待办（真实业务数据驱动，点击跳转对应标签） -->
        <div v-if="todos.length" class="stu-todos">
          <div class="std-head">
            <b>📌 {{ t('我的待办') }}</b>
            <span class="std-count">{{ t('{n} 项', { n: todos.length }) }}</span>
          </div>
          <button v-for="td in todos" :key="td.key" class="std-item" :class="{ urgent: td.urgent, danger: td.danger }" @click="goTab(td.tab)">
            <span class="std-ico">{{ td.icon }}</span>
            <span class="std-text">{{ td.text }}</span>
            <span class="std-go">{{ td.tab === 'paper' ? t('去上传') : td.tab === 'req' ? t('去查看') : t('去处理') }} →</span>
          </button>
        </div>

        <WorkspaceDetail :key="'s' + selectedAdvisorId" :ws="wsDetail" role="student" :advisor-id="selectedAdvisorId" :start-tab="jumpTab" @refresh="reloadDetail" />
      </template>
    </template>

    <!-- 管理员 -->
    <template v-else>
      <div class="empty-card">{{ t('管理员暂未开放工作台') }}</div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import http from '@/api'
import { t } from '@/i18n'
import { ElMessage } from 'element-plus'
import WorkspaceDetail from './WorkspaceDetail.vue'

const route = useRoute()
const auth = useAuthStore()
const role = computed(() => auth.user?.role || 'student')
const isStudent = computed(() => role.value === 'student')
const isTeacher = computed(() => role.value === 'teacher')

const teacherStudents = ref<any[]>([])
const myWorkspaces = ref<any[]>([])
const teacherStartTab = ref<'req' | 'paper' | 'anno' | 'chat' | null>(null)

/* ===== 评阅任务（三方评分链：教务指派的交叉评阅） ===== */
const reviewTasks = ref<any[]>([])
async function loadReviewTasks() {
  if (!isTeacher.value) { reviewTasks.value = []; return }
  try {
    const r: any = await http.get('/workspace/my-review-tasks')
    reviewTasks.value = (r?.data || []).map((x: any) => ({ ...x, _score: null, _comment: '' }))
  } catch { reviewTasks.value = [] }
}
async function submitReview(rt: any) {
  try {
    await http.post('/workspace/review-score', { advisorId: rt.advisorId, score: rt._score, comment: rt._comment })
    ElMessage.success(t('评阅评分已提交'))
    await loadReviewTasks()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || t('提交失败'))
  }
}
const wsDetail = ref<any>(null)
const loading = ref(true)
const selectedAdvisorId = ref<number | null>(null)
const curFilter = ref<'all' | 'submitted' | 'unsubmitted' | 'review'>('all')

/* ===== 学生端课题进度（与「流程」Tab 同源：开题 → 中期 → 论文终稿 → 答辩） ===== */
const myWs = computed(() => myWorkspaces.value[0] || null)
const stuPapersMine = computed(() => ((myWs.value?.papers || []) as any[]).filter((p: any) => p.uploadedBy !== 'teacher'))
const latestMine = computed(() => stuPapersMine.value[0] || null)
const stuPaperCount = computed(() => stuPapersMine.value.length)
const stuPaperStatus = computed(() => (latestMine.value ? (latestMine.value.reviewStatus || 'pending') : 'none'))
const deadline = computed(() => myWs.value?.latestRequirement?.deadline || null)
const deadlineText = computed(() => {
  if (!deadline.value) return t('未设置')
  const end = new Date(deadline.value).getTime()
  const days = Math.ceil((end - Date.now()) / 86400000)
  if (Date.now() > end) {
    const submittedAfter = latestMine.value?.createdAt && new Date(latestMine.value.createdAt).getTime() > end
    return submittedAfter ? t('已截止（截止前已提交）') : t('已截止')
  }
  return days >= 1 ? t('剩 {n} 天', { n: days }) : t('即将截止')
})
const deadlineCls = computed(() => {
  if (!deadline.value) return ''
  const end = new Date(deadline.value).getTime()
  if (Date.now() > end) return 'overdue'
  const days = Math.ceil((end - Date.now()) / 86400000)
  return days <= 3 ? 'soon' : ''
})

/* 四节点里程碑（与 WorkspaceDetail「流程」Tab 同一接口同一口径） */
const msList = ref<any[]>([])
async function loadMs() {
  if (!selectedAdvisorId.value) return
  try {
    const r: any = await http.get('/workspace/milestones', { params: { advisorId: selectedAdvisorId.value } })
    msList.value = r?.data || []
  } catch { msList.value = [] }
  try {
    const r2: any = await http.get('/workspace/plagiarism', { params: { advisorId: selectedAdvisorId.value } })
    plagInfo.value = r2?.data || null
  } catch { plagInfo.value = null }
}
const plagInfo = ref<any>(null)
const plagChipCls = computed(() => {
  const st = plagInfo.value?.status
  return st === 'passed' ? 'ok' : st === 'submitted' ? 'amber' : st === 'returned' ? 'bad' : ''
})
const plagChipText = computed(() => {
  const st = plagInfo.value?.status
  if (st === 'passed') return t('查重达标 {n}%', { n: plagInfo.value.repeatRate })
  if (st === 'submitted') return t('查重待审核')
  if (st === 'returned') return t('查重已退回')
  return t('查重未登记')
})
const msDoneCount = computed(() => msList.value.filter((m: any) => m.status === 'passed').length)
const msAllDone = computed(() => msList.value.length > 0 && msList.value.every((m: any) => m.status === 'passed'))
const defenseTotal = computed(() => {
  const d = msList.value.find((m: any) => m.stage === 'defense')
  return d && d.totalScore != null ? d.totalScore : null
})
const msNextHint = computed(() => {
  const sub = msList.value.find((m: any) => m.status === 'submitted')
  if (sub) return t('{n}已提交，等待导师审核', { n: sub.stageName })
  const ret = msList.value.find((m: any) => m.status === 'returned')
  if (ret) return t('{n}被退回，请按评语修改后重新提交', { n: ret.stageName })
  const next = msList.value.find((m: any) => m.status === 'none')
  if (next) return t('下一步：提交「{n}」材料', { n: next.stageName })
  return t('全部流程已完成 🎉')
})
/* 节点状态：done 已通过 / cur 当前进行（待审核或首个可提交）/ revise 被退回 / todo 未解锁 */
function msState(i: number) {
  const m = msList.value[i]
  if (!m) return { todo: true }
  if (m.status === 'passed') return { done: true }
  if (m.status === 'submitted') return { cur: true }
  if (m.status === 'returned') return { revise: true }
  const firstOpen = msList.value.find((x: any) => x.status !== 'passed')
  return firstOpen && m.id === firstOpen.id ? { cur: true } : { todo: true }
}

/* ===== 我的待办（全部由真实业务数据推导，点击跳转工作台对应标签） ===== */
const jumpTab = ref<'req' | 'paper' | null>(null)
function goTab(t: 'req' | 'paper') {
  jumpTab.value = null
  // 触发 WorkspaceDetail 内部 watch（值变化才生效，先清空再赋值）
  requestAnimationFrame(() => { jumpTab.value = t })
}
const todos = computed(() => {
  const ws = myWs.value
  if (!ws) return []
  const out: { key: string; text: string; tab: 'req' | 'paper'; icon: string; urgent?: boolean; danger?: boolean }[] = []
  if (!ws.topic || !String(ws.topic).trim()) {
    out.push({ key: 'topic', text: t('毕业设计课题尚未确定，与导师沟通确定题目'), tab: 'req', icon: '🎯' })
  }
  const req = ws.latestRequirement
  if (req) {
    if (stuPaperCount.value === 0) {
      const d = req.deadline ? new Date(req.deadline).getTime() : null
      const urgent = d != null && d - Date.now() > 0 && d - Date.now() <= 3 * 86400000
      out.push({ key: 'upload', text: t('毕业设计要求已发布，还未上传论文'), tab: 'paper', icon: '📤', urgent })
    }
    if (latestMine.value?.reviewStatus === 'revised') {
      out.push({ key: 'revise', text: t('论文 v{n} 需修改：请按批注意见上传新版本', { n: latestMine.value.versionNo }), tab: 'paper', icon: '✏️', urgent: true })
    } else if (latestMine.value?.reviewStatus === 'pending') {
      out.push({ key: 'review', text: t('论文 v{n} 已提交，等待导师批阅', { n: latestMine.value.versionNo }), tab: 'paper', icon: '⏳' })
    }
    const d = req.deadline ? new Date(req.deadline).getTime() : null
    if (d != null) {
      if (Date.now() > d) {
        const submittedAfter = latestMine.value?.createdAt && new Date(latestMine.value.createdAt).getTime() > d
        if (!submittedAfter && stuPaperCount.value === 0) {
          out.push({ key: 'overdue', text: t('要求已截止且未收到提交，请尽快联系导师'), tab: 'req', icon: '🚨', danger: true })
        }
      } else {
        const days = Math.ceil((d - Date.now()) / 86400000)
        if (days <= 3 && stuPaperCount.value > 0 && latestMine.value?.createdAt && new Date(latestMine.value.createdAt).getTime() < d) {
          out.push({ key: 'deadline', text: t('要求将于 {n} 天后截止（{date}），可继续更新论文', { n: days, date: new Date(d).toLocaleDateString('zh-CN') }), tab: 'paper', icon: '⏰', urgent: days <= 1 })
        }
      }
    }
  }
  return out
})

const FILTERS = [
  { key: 'all', label: '全部', count: () => teacherStudents.value.length },
  { key: 'submitted', label: '已提交', count: () => teacherStudents.value.filter(s => stuPapers(s).count > 0).length },
  { key: 'unsubmitted', label: '未提交', count: () => teacherStudents.value.filter(s => stuPapers(s).count === 0).length },
  { key: 'review', label: '待批阅', count: () => teacherStudents.value.filter(s => stuPapers(s).count > 0 && stuPapers(s).status === 'pending').length },
] as const

/** 学生提交的论文（排除教师上传的参考材料） */
function stuPapers(s: any): { count: number; status: string; lastAt: any } {
  const mine = (s.papers || []).filter((p: any) => p.uploadedBy !== 'teacher')
  const latest = mine[0] || null
  return {
    count: mine.length,
    status: latest ? (latest.reviewStatus || 'pending') : 'none',
    lastAt: latest?.createdAt || null,
  }
}
function paperLabel(s: string) {
  const m = ({ pending: '待批阅', revised: '需修改', approved: '已通过', none: '未提交' } as Record<string, string>)[s]
  return m ? t(m) : s
}
function fmtTime(v: any) {
  if (!v) return ''
  try { return new Date(v).toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' }) + ' ' + t('提交') } catch { return '' }
}

const submittedCount = computed(() => teacherStudents.value.filter(s => stuPapers(s).count > 0).length)
const unsubmittedCount = computed(() => teacherStudents.value.length - submittedCount.value)
const pendingReviewCount = computed(() =>
  teacherStudents.value.filter(s => stuPapers(s).count > 0 && stuPapers(s).status === 'pending').length
)
const collectPct = computed(() =>
  teacherStudents.value.length === 0 ? 0 : Math.round(submittedCount.value * 100 / teacherStudents.value.length)
)
const filteredStudents = computed(() => {
  if (curFilter.value === 'submitted') return teacherStudents.value.filter(s => stuPapers(s).count > 0)
  if (curFilter.value === 'unsubmitted') return teacherStudents.value.filter(s => stuPapers(s).count === 0)
  if (curFilter.value === 'review') return teacherStudents.value.filter(s => stuPapers(s).count > 0 && stuPapers(s).status === 'pending')
  return teacherStudents.value
})

const emptyState = computed(() => {
  if (loading.value) return null
  if (isStudent.value && !wsDetail.value) return t('暂未确认导师，先去「毕业设计导师」页面选择吧 👆')
  if (isTeacher.value && teacherStudents.value.length === 0) return t('暂无已确认的学生 👀')
  return null
})

function selectStudent(id: number) {
  selectedAdvisorId.value = id
  loadDetail(id)
}

async function reloadDetail() {
  if (selectedAdvisorId.value == null) return
  try {
    const r: any = await http.get('/workspace/advisor/' + selectedAdvisorId.value)
    wsDetail.value = r?.data || null
  } catch {}
  // 详情刷新时同步进度条（流程节点状态可能已变化）
  loadMs()
}

async function loadDetail(aid: number) {
  loading.value = true
  try {
    const r: any = await http.get('/workspace/advisor/' + aid)
    wsDetail.value = r?.data || null
  } catch {} finally {
    loading.value = false
  }
}

async function load() {
  loading.value = true
  try {
    if (isStudent.value) {
      const r: any = await http.get('/workspace/my')
      const list = r?.data || []
      myWorkspaces.value = list
      if (list.length > 0) {
        selectedAdvisorId.value = list[0].advisorId
        await Promise.all([loadDetail(list[0].advisorId), loadMs()])
      } else {
        loading.value = false   // 尚未确认导师：结束加载态，让空态引导文案显示
      }
    } else if (isTeacher.value) {
      const r: any = await http.get('/workspace/teacher/students')
      teacherStudents.value = r?.data || []
      // 支持从「发起对话」跳转：?student=<advisorId>&tab=chat
      const qStudent = Number(route.query.student)
      const qTab = route.query.tab === 'chat' ? 'chat' : null
      if (qStudent && teacherStudents.value.some(s => s.advisorId === qStudent)) {
        selectedAdvisorId.value = qStudent
        if (qTab) teacherStartTab.value = 'chat'
        await loadDetail(qStudent)
      } else if (teacherStudents.value.length > 0) {
        selectedAdvisorId.value = teacherStudents.value[0].advisorId
        await loadDetail(selectedAdvisorId.value!)
      } else {
        loading.value = false
      }
    }
  } catch {
    loading.value = false
  }
}

onMounted(() => {
  auth.loadFromStorage()
  load()
  loadReviewTasks()
})
</script>

<style scoped>
.ws-layout { display: flex; gap: 16px; align-items: flex-start; }
.ws-side { width: 280px; flex-shrink: 0; display: flex; flex-direction: column; gap: 10px; }
.ws-main { flex: 1; min-width: 0; }

/* 论文收集总览 */
.ws-collect { padding: 13px 15px; border: 1px solid var(--border); border-radius: 13px; background: var(--card); }
.ws-collect-title { font-size: 13px; font-weight: 700; color: var(--text); margin-bottom: 10px; }
.ws-collect-stats { display: grid; grid-template-columns: repeat(3, 1fr); gap: 7px; margin-bottom: 10px; }
.ws-cs { padding: 7px 6px; border-radius: 9px; text-align: center; display: flex; flex-direction: column; gap: 1px; }
.ws-cs b { font-size: 16px; font-variant-numeric: tabular-nums; }
.ws-cs span { font-size: 12px; }
.ws-cs.ok { background: rgba(16,185,129,.1); }
.ws-cs.ok b, .ws-cs.ok span { color: #34d399; }
.ws-cs.bad { background: rgba(239,68,68,.1); }
.ws-cs.bad b, .ws-cs.bad span { color: #fb7185; }
.ws-cs.amber { background: rgba(245,158,11,.12); }
.ws-cs.amber b, .ws-cs.amber span { color: #fbbf24; }
.ws-collect-bar { display: flex; height: 9px; border-radius: 99px; overflow: hidden; background: rgba(148,163,184,.14); }
.ws-collect-bar .ok { background: linear-gradient(90deg, #10b981, #34d399); transition: width .6s cubic-bezier(.22,.9,.36,1); }
.ws-collect-bar .bad { background: rgba(239,68,68,.45); transition: width .6s cubic-bezier(.22,.9,.36,1); }
.ws-collect-foot { margin-top: 8px; font-size: 12px; color: var(--text-light); }

/* 筛选 */
.ws-filter { display: flex; gap: 5px; flex-wrap: wrap; }
.ws-filter-btn {
  padding: 4px 11px; border-radius: 999px; border: 1px solid var(--border); background: transparent;
  color: var(--text-light); font-size: 12px; cursor: pointer; transition: all .15s;
}
.ws-filter-btn:hover { color: var(--text); }
.ws-filter-btn.active { background: var(--primary); color: #fff; border-color: var(--primary); }

/* 学生卡 */
.ws-stu-avatar {
  width: 40px; height: 40px; border-radius: 50%;
  background: var(--primary-deep);
  display: flex; align-items: center; justify-content: center; color: #fff; font-weight: 600;
}
.ws-ver-badge {
  font-size: 12px; font-weight: 800; padding: 1px 8px; border-radius: 999px; flex-shrink: 0;
}
.ws-ver-badge.pending { color: #f59e0b; background: rgba(245,158,11,.14); }
.ws-ver-badge.revised { color: #3b82f6; background: rgba(59,130,246,.14); }
.ws-ver-badge.approved { color: #059669; background: rgba(16,185,129,.14); }
.ws-ver-badge.none { color: #94a3b8; background: rgba(148,163,184,.14); }
.ws-stu-sub { font-size: 12px; color: var(--text-lighter); margin-top: 3px; }

@media (max-width: 960px) {
  .ws-layout { flex-direction: column; }
  .ws-side { width: 100%; }
}
.empty-card { padding: 40px; text-align: center; color: var(--text-light); background: var(--card); border: 1px solid var(--border); border-radius: 12px; }

/* ===== 学生端课题进度 ===== */
.stu-progress { padding: 15px 18px; border: 1px solid var(--border); border-radius: 13px; background: var(--card); margin-bottom: 16px; }
.sp-head { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 14px; }
.sp-head b { font-size: 14.5px; color: var(--text); }
.sp-stage { font-size: 12px; font-weight: 800; color: var(--primary); font-variant-numeric: tabular-nums; }
.sp-steps { display: flex; align-items: center; margin-bottom: 12px; }
.sp-step { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 5px; position: relative; }
.sp-step::before { content: ''; position: absolute; top: 8px; left: -50%; width: 100%; height: 2.5px; background: rgba(148,163,184,.25); }
.sp-step:first-child::before { display: none; }
.sp-step.done::before { background: #2bb187; }
.sp-dot {
  width: 18px; height: 18px; border-radius: 50%; z-index: 1;
  display: flex; align-items: center; justify-content: center;
  background: var(--card); border: 2px solid rgba(148,163,184,.35);
  color: transparent; font-size: 11px; font-weight: 800;
}
.sp-step.done .sp-dot { background: #2bb187; border-color: #2bb187; color: #fff; box-shadow: 0 2px 8px rgba(0,0,0,.16); }
.sp-step.cur .sp-dot { border: 2.5px solid #2bb187; background: var(--card); }
.sp-step.cur .sp-dot::after { content: ''; width: 6px; height: 6px; border-radius: 50%; background: #2bb187; }
.sp-step.revise .sp-dot { border-color: #f59e0b; color: #d97706; background: rgba(245,158,11,.1); }
.sp-step.todo .sp-dot { background: rgba(148,163,184,.15); }
.sp-check { width: 10px; height: 10px; }
.sp-mark { line-height: 1; }
.sp-step span:last-child { font-size: 12px; color: var(--text-light); white-space: nowrap; }
.sp-step.done span:last-child, .sp-step.cur span:last-child { color: var(--text); font-weight: 600; }
.sp-step.revise span:last-child { color: #d97706; font-weight: 600; }
.sp-meta { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; font-size: 12px; color: var(--text-light); }
.sp-meta b { color: var(--text); }
.sp-status { font-size: 12px; font-weight: 700; padding: 1px 9px; border-radius: 999px; }
.sp-status.pending { color: #f59e0b; background: rgba(245,158,11,.14); }
.sp-status.revised { color: #3b82f6; background: rgba(59,130,246,.14); }
.sp-status.approved { color: #059669; background: rgba(16,185,129,.14); }
.sp-deadline b { color: #d97706; }
.sp-deadline.soon b { color: #ea580c; }
.sp-deadline.overdue b { color: #be123c; }
.sp-thesis {
  display: inline-flex; align-items: center; gap: 3px;
  font-size: 12px; color: var(--text-light);
  padding: 2px 10px; border-radius: 999px;
  border: 1px dashed color-mix(in srgb, var(--primary) 45%, transparent);
  background: color-mix(in srgb, var(--primary) 6%, transparent);
}
.sp-thesis b { color: var(--primary); font-size: 13.5px; }
.sp-thesis small { color: var(--text-lighter); font-weight: 500; }
.sp-hint { margin-left: auto; color: var(--primary); font-weight: 600; }
.sp-hint.done { color: #059669; }

/* ===== 我的待办 ===== */
.stu-todos {
  padding: 13px 15px; border: 1px solid color-mix(in srgb, var(--primary) 30%, var(--border));
  border-radius: 13px; background: color-mix(in srgb, var(--primary) 4%, var(--card)); margin-bottom: 16px;
}
.std-head { display: flex; align-items: baseline; gap: 8px; margin-bottom: 10px; }
.std-head b { font-size: 14px; color: var(--text); }
.std-count { font-size: 12px; font-weight: 800; color: var(--primary); }
.std-item {
  width: 100%; display: flex; align-items: center; gap: 10px; text-align: left;
  padding: 9px 12px; margin-bottom: 7px; border: 1px solid var(--border); border-radius: 10px;
  background: var(--card); cursor: pointer; transition: border-color .15s, transform .15s, box-shadow .15s;
  font-family: inherit;
}
.std-item:last-child { margin-bottom: 0; }
.std-item:hover { border-color: var(--primary); transform: translateX(3px); box-shadow: 0 4px 12px -6px rgba(43,177,135,.4); }
.std-item.urgent { border-left: 3px solid #f59e0b; }
.std-item.danger { border-left: 3px solid #f43f5e; }
.std-ico { font-size: 15px; flex-shrink: 0; }
.std-text { flex: 1; font-size: 13px; color: var(--text); line-height: 1.5; }
.std-item.urgent .std-text { color: #b45309; }
.std-item.danger .std-text { color: #be123c; font-weight: 600; }
.std-go { font-size: 12px; color: var(--primary); font-weight: 600; flex-shrink: 0; }

/* ===== 评阅任务 ===== */
.rt-box { border-color: color-mix(in srgb, var(--primary) 30%, transparent); }
.rt-badge { font-size: 11px; font-weight: 700; color: var(--primary); background: color-mix(in srgb, var(--primary) 12%, transparent); padding: 2px 8px; border-radius: 999px; margin-left: 6px; }
.rt-badge.done { color: #16a34a; background: rgba(22,163,74,.12); }
.rt-item { border-top: 1px dashed var(--border); padding: 8px 0 6px; }
.rt-head { display: flex; align-items: baseline; gap: 6px; }
.rt-head b { font-size: 13.5px; color: var(--text); }
.rt-head small { font-size: 11.5px; color: var(--text-light); }
.rt-topic { font-size: 12px; color: var(--primary); margin-top: 2px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.rt-form { display: flex; gap: 6px; margin-top: 6px; }
.rt-score { width: 64px; padding: 5px 8px; border: 1px solid var(--border); border-radius: 8px; background: var(--card-bg); color: var(--text); font-family: inherit; }
.rt-comment { flex: 1; min-width: 0; padding: 5px 8px; border: 1px solid var(--border); border-radius: 8px; background: var(--card-bg); color: var(--text); font-family: inherit; font-size: 12px; }
.rt-btn { padding: 5px 10px; border: none; border-radius: 8px; background: var(--primary); color: #fff; font-family: inherit; font-size: 12px; font-weight: 700; cursor: pointer; }
.rt-btn:disabled { opacity: .45; cursor: not-allowed; }
.rt-done { margin-top: 6px; font-size: 12.5px; color: #16a34a; }


/* ===== 课题进度卡视觉升级 ===== */
.stu-progress { padding: 18px 22px; border-radius: 16px; background: linear-gradient(135deg, color-mix(in srgb, var(--primary) 5%, var(--card)), var(--card)); box-shadow: 0 10px 26px -20px rgba(0, 0, 0, .5); }
.sp-dot { width: 22px; height: 22px; border: 2.5px solid rgba(148, 163, 184, .4); }
.sp-step::before { top: 10px; height: 3px; border-radius: 2px; }
.sp-step.done::before { background: linear-gradient(90deg, #2bb187, #4ade80); }
.sp-step.cur .sp-dot { border-color: #2bb187; box-shadow: 0 0 0 5px rgba(43, 177, 135, .16); animation: sp-pulse 1.8s ease-in-out infinite; }
@keyframes sp-pulse { 0%, 100% { box-shadow: 0 0 0 5px rgba(43, 177, 135, .15); } 50% { box-shadow: 0 0 0 8px rgba(43, 177, 135, .07); } }
.sp-step span:last-child { font-size: 12.5px; }
.sp-stage { padding: 2px 10px; border-radius: 999px; background: color-mix(in srgb, var(--primary) 10%, transparent); }

</style>
