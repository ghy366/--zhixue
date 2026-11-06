<template>
  <div class="page-wrap stats-page liquid">
    <div class="section-header" style="justify-content:flex-start;gap:16px;position:static;background:transparent;backdrop-filter:none">
      <div class="section-title" v-html="svgIcon('graduation-cap') + ' ' + t('毕业设计导师指导')"></div>
    </div>

    <!-- ===== 概览 KPI ===== -->
    <div class="stats-grid">
      <div class="stat-card"><div class="stat-icon blue" v-html="svgIcon('target')" /><div><div class="stat-value">{{ stats.approved ?? 0 }}<span class="sub"> / {{ stats.capacity ?? '—' }}</span></div><div class="stat-label">{{ t('指导名额占用') }}</div></div></div>
      <div class="stat-card"><div class="stat-icon green" v-html="svgIcon('check-circle')" /><div><div class="stat-value">{{ approved.length }}</div><div class="stat-label">{{ t('进行中学生') }}</div></div></div>
      <div class="stat-card"><div class="stat-icon amber" v-html="svgIcon('hourglass')" /><div><div class="stat-value">{{ pending.length }}</div><div class="stat-label">{{ t('待确认申请') }}</div></div></div>
      <div class="stat-card"><div class="stat-icon red" v-html="svgIcon('x-circle')" /><div><div class="stat-value">{{ (stats.rejected ?? 0) + (stats.cancelled ?? 0) }}</div><div class="stat-label">{{ t('已拒绝 / 取消') }}</div></div></div>
    </div>

    <!-- ===== 容量 + 课题进度 ===== -->
    <div class="adv-analysis">
      <div class="section adv-panel">
        <div class="section-header"><div class="section-title">{{ t('指导容量') }}</div></div>
        <div class="adv-cap">
          <svg viewBox="0 0 150 150" class="adv-ring" role="img" :aria-label="t('指导容量占用')">
            <g transform="rotate(-90 75 75)">
              <circle cx="75" cy="75" r="62" class="adv-ring-track" />
              <circle
                cx="75" cy="75" r="62"
                class="adv-ring-fill"
                :class="{ full: capPct >= 100 }"
                :stroke-dasharray="`${capPct / 100 * RING_C} ${RING_C}`"
                stroke-linecap="round"
              />
            </g>
            <text x="75" y="72" text-anchor="middle" class="adv-ring-num">{{ stats.approved ?? 0 }}</text>
            <text x="75" y="90" text-anchor="middle" class="adv-ring-sub">{{ t('/ {n} 名额', { n: stats.capacity ?? '—' }) }}</text>
          </svg>
          <div class="adv-cap-side">
            <div class="adv-cap-line"><span>{{ t('剩余名额') }}</span><b>{{ remain }}</b></div>
            <div class="adv-cap-line"><span>{{ t('占用率') }}</span><b>{{ capPct }}%</b></div>
            <div class="adv-status-strip" v-if="stats.capacity != null">
              <i class="st-seg seg-ok" :style="{ width: segW(stats.approved) + '%' }" :title="t('已录取 {n}', { n: stats.approved })" />
              <i class="st-seg seg-warn" :style="{ width: segW(stats.pending) + '%' }" :title="t('待确认 {n}', { n: stats.pending })" />
              <i class="st-seg seg-bad" :style="{ width: segW((stats.rejected ?? 0) + (stats.cancelled ?? 0)) + '%' }" :title="t('拒绝/取消 {n}', { n: (stats.rejected ?? 0) + (stats.cancelled ?? 0) })" />
            </div>
            <div class="adv-legend">
              <span><i class="lg-ok" />{{ t('已录取 {n}', { n: stats.approved ?? 0 }) }}</span>
              <span><i class="lg-warn" />{{ t('待确认 {n}', { n: stats.pending ?? 0 }) }}</span>
              <span><i class="lg-bad" />{{ t('拒绝/取消 {n}', { n: (stats.rejected ?? 0) + (stats.cancelled ?? 0) }) }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="section adv-panel">
        <div class="section-header">
          <div class="section-title">{{ t('学生课题进度') }}</div>
          <span class="adv-link" @click="$router.push('/workspace')">{{ t('去工作台 →') }}</span>
        </div>
        <div v-if="progressRows.length" class="adv-progress">
          <div v-for="p in progressRows" :key="p.advisorId" class="adv-prog-row">
            <div class="adv-prog-head">
              <b>{{ p.name }}</b>
              <span class="adv-prog-topic" :title="p.topic">{{ p.topic || t('课题待定') }}</span>
              <span class="adv-prog-stage">{{ p.stage }} / 5</span>
            </div>
            <div class="adv-steps">
              <div v-for="(st, i) in STEPS" :key="st" class="adv-step" :class="{ done: i < p.stage, cur: i === p.stage }">
                <i class="adv-step-dot" />
                <span>{{ t(st) }}</span>
              </div>
            </div>
            <div class="adv-prog-meta">
              <span>{{ t('论文') }} <b>{{ p.paperCount }}</b> {{ t('版') }}</span>
              <span v-if="p.paperCount" class="adv-paper-status" :class="p.paperStatus">{{ paperLabel(p.paperStatus) }}</span>
              <span v-if="p.grade != null" class="adv-grade">{{ t('{n} 分', { n: p.grade }) }}</span>
            </div>
          </div>
        </div>
        <div v-else class="adv-nodata">{{ t('还没有已确认的学生——通过申请后，这里会展示每位学生的课题进度') }}</div>
      </div>
    </div>

    <!-- ===== 自选课题审批（学生自拟 → 导师通过写入课题 / 打回修改） ===== -->
    <div class="section" v-if="proposals.length">
      <div class="section-header">
        <div class="section-title">{{ t('自选课题审批') }}</div>
        <div class="adv-chips">
          <button class="cat-tab" :class="{ active: pFilter === 'pending' }" @click="pFilter = 'pending'">{{ t('待审核') }} {{ pPending.length }}</button>
          <button class="cat-tab" :class="{ active: pFilter === '' }" @click="pFilter = ''">{{ t('全部') }} {{ proposals.length }}</button>
        </div>
      </div>
      <div class="data-table">
        <table>
          <thead>
            <tr><th>{{ t('学号') }}</th><th>{{ t('姓名') }}</th><th>{{ t('专业') }}</th><th>{{ t('自拟课题') }}</th><th>{{ t('方向') }}</th><th>{{ t('选题说明') }}</th><th>{{ t('状态') }}</th><th>{{ t('操作') }}</th></tr>
          </thead>
          <tbody>
            <tr v-for="p in filteredProposals" :key="p.id">
              <td>{{ p.studentNo }}</td>
              <td><b>{{ p.studentName }}</b></td>
              <td>{{ p.studentMajor || '-' }}</td>
              <td style="max-width:220px"><b>{{ p.title }}</b></td>
              <td>{{ p.direction || '—' }}</td>
              <td style="max-width:200px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap" :title="p.description">{{ p.description || '—' }}</td>
              <td>
                <span class="status-tag" :class="proposalTag(p.status)">{{ proposalText(p.status) }}</span>
                <div v-if="p.status === 'rejected' && p.teacherReply" class="aa-t-reply" :title="p.teacherReply">{{ t('打回：') }}{{ p.teacherReply }}</div>
              </td>
              <td>
                <template v-if="p.status === 'pending'">
                  <button class="btn btn-success btn-sm" @click="decideProposal(p, 'approve')">{{ t('通过') }}</button>
                  <button class="btn btn-danger btn-sm" @click="decideProposal(p, 'reject')">{{ t('打回') }}</button>
                </template>
                <span v-else-if="p.status === 'approved'" class="aa-t-sync">✓ {{ t('已写入课题') }}</span>
                <span v-else style="color:var(--text-light);font-size:12px">{{ t('已处理') }}</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- ===== 申请列表 ===== -->
    <div class="section" v-if="total">
      <div class="section-header">
        <div class="section-title">{{ t('申请列表') }}</div>
        <div class="adv-chips">
          <button class="cat-tab" :class="{ active: filter === '' }" @click="filter = ''">{{ t('全部') }} {{ total }}</button>
          <button class="cat-tab" :class="{ active: filter === 'pending' }" @click="filter = 'pending'">{{ t('待确认') }} {{ pending.length }}</button>
          <button class="cat-tab" :class="{ active: filter === 'approved' }" @click="filter = 'approved'">{{ t('进行中') }} {{ approved.length }}</button>
          <button class="cat-tab" :class="{ active: filter === 'done' }" @click="filter = 'done'">{{ t('已处理') }} {{ done.length }}</button>
        </div>
      </div>
      <div class="data-table">
        <table>
          <thead>
            <tr><th>{{ t('学号') }}</th><th>{{ t('姓名') }}</th><th>{{ t('专业') }}</th><th>{{ t('毕设题目') }}</th><th>{{ t('状态') }}</th><th>{{ t('申请说明') }}</th><th>{{ t('操作') }}</th></tr>
          </thead>
          <tbody>
            <tr v-for="s in filtered" :key="s.id">
              <td>{{ s.studentNo }}</td>
              <td><b>{{ s.studentName }}</b></td>
              <td>{{ s.studentMajor || '-' }}</td>
              <td><b>{{ s.topic || '—' }}</b></td>
              <td>
                <span class="status-tag" :class="phaseTag(s.status)">{{ statusText(s.status) }}</span>
              </td>
              <td style="max-width:200px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap" :title="s.preferenceText">{{ s.preferenceText || '—' }}</td>
              <td>
                <template v-if="s.status === 'pending'">
                  <button class="btn btn-success btn-sm" @click="decide(s, 'approve')">{{ t('通过') }}</button>
                  <button class="btn btn-danger btn-sm" @click="decide(s, 'reject')">{{ t('拒绝') }}</button>
                </template>
                <button v-else-if="s.status === 'approved'" class="btn btn-outline btn-sm" @click="$router.push({ path: '/workspace', query: { student: s.id, tab: 'chat' } })">{{ t('发起对话') }}</button>
                <span v-else class="btn btn-ghost btn-sm" style="cursor:not-allowed;color:var(--text-light)">{{ t('已处理') }}</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
    <div v-else class="empty-state">
      <div class="empty-icon" v-html="svgIcon('graduation-cap')" />
      <div class="empty-title">{{ t('暂无指导学生') }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api'
import { useAuthStore } from '@/stores/auth'
import { t } from '@/i18n'

const auth = useAuthStore()
const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`

const list = ref<any[]>([])
const stats = ref<any>({})
const workspaces = ref<any[]>([])
const filter = ref('')

const RING_C = 2 * Math.PI * 62
const STEPS = ['导师确认', '确定课题', '发布要求', '提交论文', '批阅通过']

const approved = computed(() => list.value.filter(s => s.status === 'approved'))
const pending = computed(() => list.value.filter(s => s.status === 'pending'))
const done = computed(() => list.value.filter(s => s.status === 'rejected' || s.status === 'cancelled'))
const total = computed(() => list.value.length)

const filtered = computed(() => {
  if (filter.value === 'pending') return pending.value
  if (filter.value === 'approved') return approved.value
  if (filter.value === 'done') return done.value
  return list.value
})

const capPct = computed(() => {
  const cap = Number(stats.value.capacity) || 0
  return cap <= 0 ? 0 : Math.min(100, Math.round(((stats.value.approved || 0) / cap) * 100))
})
const remain = computed(() => Math.max(0, (Number(stats.value.capacity) || 0) - (stats.value.approved || 0)))
const segW = (n: any) => {
  const all = (stats.value.approved || 0) + (stats.value.pending || 0) + (stats.value.rejected || 0) + (stats.value.cancelled || 0)
  return all <= 0 ? 0 : Math.max(n ? 2 : 0, ((Number(n) || 0) / all) * 100)
}

/* 每个已录取学生的课题进度（5 段） */
const progressRows = computed(() => {
  return (workspaces.value || []).map(ws => {
    const papers: any[] = ws.papers || []
    const latest = papers[0] || null
    let stage = 1
    if (ws.topic && String(ws.topic).trim()) stage = 2
    if (ws.latestRequirement) stage = 3
    if (papers.length > 0) stage = 4
    if (latest && latest.reviewStatus === 'approved') stage = 5
    return {
      advisorId: ws.advisorId,
      name: ws.student?.name || t('未知学生'),
      topic: ws.topic,
      stage,
      paperCount: papers.length,
      paperStatus: latest?.reviewStatus || null,
      grade: latest?.grade ?? null,
    }
  })
})

function paperLabel(s: string | null) {
  if (s === 'approved') return t('已通过')
  if (s === 'revised') return t('需修改')
  return t('待批阅')
}
function statusText(s: string) {
  if (s === 'approved') return t('进行中')
  if (s === 'pending') return t('待确认')
  if (s === 'rejected') return t('已拒绝')
  if (s === 'cancelled') return t('已取消')
  return s
}
function phaseTag(s: string) {
  if (s === 'approved') return 'status-approved'
  if (s === 'pending') return 'status-pending'
  if (s === 'rejected' || s === 'cancelled') return 'status-rejected'
  return 'status-approved'
}

/* ===== 自选课题审批 ===== */
const proposals = ref<any[]>([])
const pFilter = ref<'pending' | ''>('')
const pPending = computed(() => proposals.value.filter(p => p.status === 'pending'))
const filteredProposals = computed(() => (pFilter.value === 'pending' ? pPending.value : proposals.value))
function proposalText(s: string) {
  return ({ pending: t('待审核'), approved: t('已通过'), rejected: t('已打回'), withdrawn: t('已撤回') } as Record<string, string>)[s] || s
}
function proposalTag(s: string) {
  if (s === 'approved') return 'status-approved'
  if (s === 'pending') return 'status-pending'
  if (s === 'rejected') return 'status-rejected'
  return 'status-approved'
}

async function decideProposal(p: any, decision: string) {
  let reply = ''
  if (decision === 'reject') {
    try {
      const res = await ElMessageBox.prompt(
        t('打回「{t}」，请填写打回理由（学生可见，便于修改后重新提交）：', { t: p.title }),
        t('打回自选课题'), {
          type: 'warning',
          confirmButtonText: t('打回'),
          cancelButtonText: t('取消'),
          inputPlaceholder: t('如：题目范围过大 / 与专业关联不足 / 请补充技术路线…'),
          inputValidator: (v: string) => (v && v.trim() ? true : t('请填写打回理由')),
        }
      )
      reply = res.value || ''
    } catch { return }
  } else {
    try {
      await ElMessageBox.confirm(t('通过「{t}」？课题将写入该学生的双选进度。', { t: p.title }), t('通过自选课题'),
        { type: 'success', confirmButtonText: t('通过'), cancelButtonText: t('取消') })
    } catch { return }
  }
  try {
    const r: any = await http.post('/advisor/teacher/proposals/decide/' + p.id, { decision, reply })
    ElMessage.success(r?.message || (decision === 'approve' ? t('已通过') : t('已打回')))
    await load()
  } catch (e: any) { ElMessage.error(e?.message || t('操作失败')) }
}

async function load() {
  try {
    const [r, st, ws, pp]: any[] = await Promise.all([
      http.get('/advisor/teacher/list'),
      http.get('/advisor/teacher/stats').catch(() => null),
      http.get('/workspace/teacher/students').catch(() => null),
      http.get('/advisor/teacher/proposals').catch(() => null),
    ])
    list.value = r?.data || []
    stats.value = st?.data || {}
    workspaces.value = ws?.data || []
    proposals.value = pp?.data || []
  } catch {}
}

async function decide(s: any, decision: string) {
  try {
    await ElMessageBox.confirm(
      decision === 'approve' ? t('确认通过「{n}」的毕设申请？', { n: s.studentName }) : t('确认拒绝「{n}」的毕设申请？', { n: s.studentName }),
      t('提示'), {
        type: 'warning',
        confirmButtonText: t('确定'),
        cancelButtonText: t('取消'),
      }
    )
  } catch { return }
  try {
    await http.post('/advisor/teacher/decide', { id: s.id, decision })
    ElMessage.success(decision === 'approve' ? t('已通过') : t('已拒绝'))
    await load()
  } catch (e: any) { ElMessage.error(e?.message || t('操作失败')) }
}

onMounted(load)
</script>

<style scoped>
.page-wrap { padding: 4px 0; display: flex; flex-direction: column; gap: 16px; }
.empty-state { text-align: center; padding: 60px 20px; color: var(--text-light); }
.empty-icon { font-size: 48px; opacity: 0.3; margin-bottom: 10px; }
.status-tag { font-size: 12px; padding: 1px 8px; border-radius: 999px; }
.status-approved { background: rgba(16,185,129,0.15); color: #10b981; }
.status-pending { background: rgba(245,158,11,0.15); color: #f59e0b; }
.status-rejected { background: rgba(239,68,68,0.15); color: #ef4444; }
.sub { font-size: 13px; color: var(--text-light); margin-left: 2px; }
.stat-icon.red { background: rgba(239,68,68,.15); color: #ef4444; }

/* ===== 分析双列 ===== */
.adv-analysis { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1.35fr); gap: 16px; }
@media (max-width: 960px) { .adv-analysis { grid-template-columns: minmax(0, 1fr); } }
.adv-link { font-size: 12.5px; color: var(--primary); cursor: pointer; font-weight: 600; }

/* ===== 自选课题审批 ===== */
.aa-t-reply { font-size: 11.5px; color: #be123c; margin-top: 3px; max-width: 160px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.aa-t-sync { font-size: 12px; color: var(--primary); font-weight: 600; }

/* 容量环 */
.adv-cap { display: flex; align-items: center; gap: 22px; padding: 8px 4px 4px; flex-wrap: wrap; }
.adv-ring { width: 148px; height: 148px; flex-shrink: 0; }
.adv-ring-track { fill: none; stroke: rgba(148,163,184,.16); stroke-width: 13; }
.adv-ring-fill { fill: none; stroke: #2bb187; stroke-width: 13; transition: stroke-dasharray .8s cubic-bezier(.22,.9,.36,1); }
.adv-ring-fill.full { stroke: #f43f5e; }
.adv-ring-num { font-size: 30px; font-weight: 800; fill: var(--text); }
.adv-ring-sub { font-size: 12px; fill: var(--text-light); }
.adv-cap-side { flex: 1; min-width: 180px; display: flex; flex-direction: column; gap: 10px; }
.adv-cap-line { display: flex; justify-content: space-between; font-size: 13px; color: var(--text-light); padding: 7px 12px; border: 1px solid var(--border); border-radius: 10px; }
.adv-cap-line b { color: var(--text); font-size: 15px; }
.adv-status-strip { display: flex; gap: 2px; height: 12px; border-radius: 99px; overflow: hidden; background: rgba(148,163,184,.12); }
.st-seg { display: block; height: 100%; transition: width .6s cubic-bezier(.22,.9,.36,1); }
.seg-ok { background: #10b981; }
.seg-warn { background: #f59e0b; }
.seg-bad { background: #ef4444; }
.adv-legend { display: flex; flex-wrap: wrap; gap: 10px; font-size: 12px; color: var(--text-light); }
.adv-legend i { display: inline-block; width: 9px; height: 9px; border-radius: 3px; margin-right: 5px; }
.adv-legend .lg-ok { background: #10b981; }
.adv-legend .lg-warn { background: #f59e0b; }
.adv-legend .lg-bad { background: #ef4444; }

/* 课题进度 */
.adv-progress { display: flex; flex-direction: column; gap: 12px; padding-top: 6px; }
.adv-prog-row { padding: 11px 14px; border: 1px solid var(--border); border-radius: 12px; background: color-mix(in srgb, var(--card-bg) 55%, transparent); display: flex; flex-direction: column; gap: 9px; }
.adv-prog-head { display: flex; align-items: baseline; gap: 10px; }
.adv-prog-head b { font-size: 13.5px; color: var(--text); flex-shrink: 0; }
.adv-prog-topic { font-size: 12px; color: var(--text-light); min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; }
.adv-prog-stage { font-size: 12px; font-weight: 800; color: var(--primary); flex-shrink: 0; font-variant-numeric: tabular-nums; }
.adv-steps { display: flex; align-items: center; }
.adv-step { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 4px; position: relative; }
.adv-step::before {
  content: ''; position: absolute; top: 5px; left: -50%; width: 100%; height: 2px;
  background: rgba(148,163,184,.25);
}
.adv-step:first-child::before { display: none; }
.adv-step.done::before { background: #2bb187; }
.adv-step-dot { width: 11px; height: 11px; border-radius: 50%; background: rgba(148,163,184,.3); border: 2px solid transparent; z-index: 1; }
.adv-step.done .adv-step-dot { background: #2bb187; box-shadow: 0 2px 8px rgba(0,0,0,.16); }
.adv-step.cur .adv-step-dot { background: var(--card-bg); border-color: #2bb187; }
.adv-step span { font-size: 12px; color: var(--text-light); white-space: nowrap; }
.adv-step.done span, .adv-step.cur span { color: var(--text); font-weight: 600; }
.adv-prog-meta { display: flex; align-items: center; gap: 12px; font-size: 12px; color: var(--text-light); }
.adv-prog-meta b { color: var(--text); }
.adv-paper-status { font-size: 12px; font-weight: 700; padding: 1px 8px; border-radius: 999px; }
.adv-paper-status.pending { color: #f59e0b; background: rgba(245,158,11,.14); }
.adv-paper-status.revised { color: #3b82f6; background: rgba(59,130,246,.14); }
.adv-paper-status.approved { color: #059669; background: rgba(16,185,129,.14); }
.adv-grade { color: var(--primary); font-weight: 800; }
.adv-nodata { font-size: 12.5px; color: var(--text-light); opacity: .8; padding: 20px 4px; text-align: center; }

/* 筛选 chips */
.adv-chips { display: flex; gap: 6px; flex-wrap: wrap; }
</style>
