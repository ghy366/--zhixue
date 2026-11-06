<template>
  <div class="page-wrap sg-page">
    <!-- ===== 顶部横幅 ===== -->
    <div class="sg-hero">
      <div class="sg-hero-main">
        <h1 class="sg-hero-title">{{ t('建议箱') }}</h1>
        <p class="sg-hero-sub">{{ isStudent
          ? t('想加什么功能、哪里用着别扭，直接说——你的建议管理员都能看到，处理结果会通过站内通知告诉你。')
          : t('学生提交的功能建议与优化反馈汇总在这里，处理后系统会自动通知提交人。') }}</p>
      </div>
      <div class="sg-hero-stats">
        <div class="sg-hstat"><b>{{ animReceived }}</b><span>{{ t('收到建议') }}</span></div>
        <div class="sg-hstat"><b>{{ animPending }}</b><span>{{ t('待处理') }}</span></div>
        <div class="sg-hstat"><b>{{ animResolved }}</b><span>{{ t('已采纳/完成') }}</span></div>
      </div>
    </div>

    <!-- ===== 学生/教师：写建议 ===== -->
    <div v-if="isStudent" class="section">
      <div class="section-header">
        <div class="section-title" v-html="svgIcon('edit') + ' ' + t('写建议')"></div>
      </div>
      <div class="sg-compose">
        <div class="sg-cats">
          <button v-for="c in CATEGORIES" :key="c" type="button" class="sg-cat" :class="{ on: form.category === c }" @click="form.category = c">{{ t(c) }}</button>
        </div>
        <el-input v-model="form.content" type="textarea" :rows="4" maxlength="2000" show-word-limit
          :placeholder="t('例如：希望课程列表支持按上课时间筛选 / 意向页面的按钮太小不好点 / 想要一个导出课表的按钮…')" />
        <div class="sg-compose-foot">
          <span class="sg-compose-tip">{{ t('提交后管理员可见并会回复处理结果，请勿填写个人敏感信息') }}</span>
          <button class="btn btn-primary" :disabled="submitting || form.content.trim().length < 5" @click="submit">
            {{ submitting ? t('提交中…') : t('提交建议') }}
          </button>
        </div>
      </div>
    </div>

    <!-- ===== 管理员：状态过滤卡 ===== -->
    <div v-else class="sg-filter-row">
      <button type="button" class="sg-fcard" :class="{ on: filter === 'ALL' }" @click="filter = 'ALL'">
        <b>{{ animReceived }}</b><span>{{ t('全部') }}</span>
      </button>
      <button type="button" class="sg-fcard amber" :class="{ on: filter === 'PENDING' }" @click="filter = 'PENDING'">
        <b>{{ animPending }}</b><span>{{ t('待处理') }}</span>
      </button>
      <button type="button" class="sg-fcard teal" :class="{ on: filter === 'ACCEPTED' }" @click="filter = 'ACCEPTED'">
        <b>{{ animAccepted }}</b><span>{{ t('已采纳') }}</span>
      </button>
      <button type="button" class="sg-fcard green" :class="{ on: filter === 'DONE' }" @click="filter = 'DONE'">
        <b>{{ animDone }}</b><span>{{ t('已完成') }}</span>
      </button>
      <button type="button" class="sg-fcard gray" :class="{ on: filter === 'REJECTED' }" @click="filter = 'REJECTED'">
        <b>{{ animRejected }}</b><span>{{ t('未采纳') }}</span>
      </button>
    </div>

    <!-- ===== 建议列表 ===== -->
    <div class="section">
      <div class="section-header">
        <div class="section-title" v-html="svgIcon('inbox') + ' ' + (isStudent ? t('我的建议') : t('全部建议'))"></div>
        <span class="sg-count">{{ t('{n} 条', { n: items.length }) }}</span>
      </div>

      <div v-if="!items.length" class="sg-empty">
        <el-empty :image-size="90" :description="isStudent ? t('还没有建议——第一条建议往往是最有价值的') : t('该状态下暂无建议')" />
      </div>

      <div v-else class="sg-list">
        <article v-for="s in items" :key="s.id" class="sg-item">
          <div class="sg-item-head">
            <span class="sg-cat-tag" :class="catCls(s.category)">{{ t(s.category) }}</span>
            <span class="sg-status" :class="statusCls(s.status)">{{ s.statusCn || statusCn(s.status) }}</span>
            <span class="sg-item-meta">{{ (isStudent ? '' : (s.userName || t('匿名')) + ' · ') + fmtTime(s.createdAt) }}</span>
          </div>
          <p class="sg-item-content">{{ s.content }}</p>

          <div v-if="s.adminReply && s.status !== 'PENDING'" class="sg-reply">
            <b>{{ t('管理员回复') }}<template v-if="s.handledAt"> · {{ fmtTime(s.handledAt) }}</template></b>
            <p>{{ s.adminReply }}</p>
          </div>

          <!-- 管理员处理区 -->
          <div v-if="!isStudent" class="sg-handle">
            <el-input v-model="s._reply" type="textarea" :rows="2" maxlength="1000" show-word-limit
              :placeholder="t('回复内容（婉拒时必填，会通知提交人）')" />
            <div class="sg-handle-btns">
              <button class="btn btn-primary btn-sm" @click="handle(s, 'ACCEPTED')">{{ t('采纳') }}</button>
              <button class="btn btn-success btn-sm" @click="handle(s, 'DONE')">{{ t('标记完成') }}</button>
              <button class="btn btn-sm sg-btn-reject" @click="handle(s, 'REJECTED')">{{ t('婉拒') }}</button>
              <span class="sg-flex"></span>
              <button class="btn btn-danger btn-sm" @click="remove(s)">{{ t('删除') }}</button>
            </div>
          </div>
        </article>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api'
import { useCountUp } from '@/utils/useCountUp'
import { useAuthStore } from '@/stores/auth'
import { t } from '@/i18n'
const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`

const auth = useAuthStore()
/** 管理员看到处理台；学生/教师看到提交箱（教师也可以提建议） */
const isStudent = computed(() => (auth.user?.role || 'student') !== 'admin')

const CATEGORIES = ['功能建议', '体验优化', '问题反馈', '其他']
const STATUS_CN: Record<string, string> = { PENDING: '待处理', ACCEPTED: '已采纳', DONE: '已完成', REJECTED: '未采纳' }
const CAT_CLS: Record<string, string> = { 功能建议: 'sc0', 体验优化: 'sc1', 问题反馈: 'sc2' }
const STATUS_CLS: Record<string, string> = { PENDING: 'st-pending', ACCEPTED: 'st-accepted', DONE: 'st-done', REJECTED: 'st-rejected' }

const form = reactive({ category: '功能建议', content: '' })
const submitting = ref(false)
const mine = ref<any[]>([])
const adminItems = ref<any[]>([])
const filter = ref('ALL')
const summary = ref<any>({ total: 0, pending: 0, accepted: 0, done: 0, rejected: 0 })

const items = computed(() => (isStudent.value ? mine.value : adminItems.value))
const pendingCount = computed(() => mine.value.filter(s => s.status === 'PENDING').length)
const resolvedCount = computed(() => mine.value.filter(s => s.status === 'ACCEPTED' || s.status === 'DONE').length)

/* 统计数字滚动：学生端（我的建议）与管理端（汇总+过滤卡）共用 */
const animReceived = useCountUp(() => isStudent.value ? mine.value.length : (Number(summary.value.total) || 0))
const animPending = useCountUp(() => isStudent.value ? pendingCount.value : (Number(summary.value.pending) || 0))
const animResolved = useCountUp(() => isStudent.value ? resolvedCount.value : ((Number(summary.value.accepted) || 0) + (Number(summary.value.done) || 0)))
const animAccepted = useCountUp(() => Number(summary.value.accepted) || 0)
const animDone = useCountUp(() => Number(summary.value.done) || 0)
const animRejected = useCountUp(() => Number(summary.value.rejected) || 0)

async function load() {
  if (isStudent.value) {
    try {
      const r: any = await http.get('/suggestions/mine')
      mine.value = r?.data || []
    } catch {}
  } else {
    try {
      const r: any = await http.get('/suggestions', { params: filter.value === 'ALL' ? {} : { status: filter.value } })
      const d = r?.data || {}
      adminItems.value = (d.items || []).map((x: any) => ({ ...x, _reply: x.adminReply || '' }))
      if (d.summary) summary.value = d.summary
    } catch {}
  }
}
watch(filter, load)

async function submit() {
  if (form.content.trim().length < 5) return
  submitting.value = true
  try {
    const r: any = await http.post('/suggestions', { category: form.category, content: form.content.trim() })
    ElMessage.success(r?.message || t('建议已提交'))
    form.content = ''
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || t('提交失败'))
  } finally { submitting.value = false }
}

async function handle(s: any, status: string) {
  if (status === 'REJECTED' && !(s._reply || '').trim()) {
    ElMessage.warning(t('婉拒时请填写回复说明原因')); return
  }
  try {
    const r: any = await http.post(`/suggestions/${s.id}/handle`, { status, reply: (s._reply || '').trim() })
    ElMessage.success(r?.message || t('已更新'))
    load()
  } catch (e: any) { ElMessage.error(e?.message || t('操作失败')) }
}

async function remove(s: any) {
  try {
    await ElMessageBox.confirm(t('确定删除这条建议？删除后不可恢复。'), t('删除建议'),
      { type: 'warning', confirmButtonText: t('删除'), cancelButtonText: t('取消') })
  } catch { return }
  try {
    const r: any = await http.delete(`/suggestions/${s.id}`)
    ElMessage.success(r?.message || t('已删除'))
    load()
  } catch (e: any) { ElMessage.error(e?.message || t('删除失败')) }
}

/* ---------- 工具 ---------- */
const pad = (n: number) => String(n).padStart(2, '0')
function fmtTime(ts: any): string {
  if (!ts) return '—'
  const d = new Date(ts)
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
function statusCn(st: string) { return t(STATUS_CN[st] || '待处理') }
function catCls(c: string) { return CAT_CLS[c] || 'sc3' }
function statusCls(st: string) { return STATUS_CLS[st] || 'st-pending' }

onMounted(load)
</script>

<style scoped>
.page-wrap { padding: 4px 0; }

/* ===== 顶部横幅 ===== */
.sg-hero { display: flex; align-items: flex-end; justify-content: space-between; gap: 18px; flex-wrap: wrap; margin-bottom: 16px; }
.sg-hero-title { margin: 0 0 6px; font-size: 24px; font-weight: 800; color: var(--text); }
.sg-hero-sub { margin: 0; font-size: 13px; line-height: 1.65; color: var(--text-light); max-width: 620px; }
.sg-hero-stats { display: flex; gap: 26px; padding: 12px 20px; border-radius: 14px;
  background: color-mix(in srgb, var(--card-bg) 70%, transparent); border: 1px solid var(--border); }
.sg-hstat { display: flex; flex-direction: column; align-items: center; gap: 2px; min-width: 64px; }
.sg-hstat b { font-size: 20px; font-weight: 800; color: var(--text); font-variant-numeric: tabular-nums; }
.sg-hstat span { font-size: 11.5px; color: var(--text-light); }

/* ===== 写建议 ===== */
.sg-compose { display: flex; flex-direction: column; }
.sg-cats { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 10px; }
.sg-cat { padding: 6px 14px; border-radius: 999px; border: 1px solid var(--border); background: transparent;
  color: var(--text-light); font-size: 13px; cursor: pointer; transition: all .2s; font-family: inherit; }
.sg-cat:hover { color: var(--text); border-color: var(--text-light); }
.sg-cat.on { border-color: var(--primary); color: var(--primary); background: rgba(43, 177, 135, .08); font-weight: 600; }
.sg-compose-foot { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 10px; }
.sg-compose-tip { font-size: 12px; color: var(--text-light); opacity: .85; }

/* ===== 管理员过滤卡 ===== */
.sg-filter-row { display: grid; grid-template-columns: repeat(5, 1fr); gap: 10px; margin-bottom: 16px; }
@media (max-width: 860px) { .sg-filter-row { grid-template-columns: repeat(3, 1fr); } }
.sg-fcard { display: flex; flex-direction: column; align-items: center; gap: 2px; padding: 12px 8px;
  border-radius: 12px; border: 1px solid var(--border); background: color-mix(in srgb, var(--card-bg) 60%, transparent);
  cursor: pointer; transition: all .2s; font-family: inherit; }
.sg-fcard:hover { border-color: var(--text-light); }
.sg-fcard b { font-size: 19px; font-weight: 800; color: var(--text); font-variant-numeric: tabular-nums; }
.sg-fcard span { font-size: 12px; color: var(--text-light); }
.sg-fcard.on { border-color: var(--primary); box-shadow: 0 0 0 2px rgba(43, 177, 135, .14); }
.sg-fcard.on b, .sg-fcard.on span { color: var(--primary-deep); }
html.dark .sg-fcard.on b, html.dark .sg-fcard.on span { color: var(--primary); }
.sg-fcard.amber b { color: #d97706; } html.dark .sg-fcard.amber b { color: #fbbf24; }
.sg-fcard.teal b { color: #0d9488; } html.dark .sg-fcard.teal b { color: #2dd4bf; }
.sg-fcard.green b { color: #059669; } html.dark .sg-fcard.green b { color: #34d399; }
.sg-fcard.gray b { color: #64748b; } html.dark .sg-fcard.gray b { color: #94a3b8; }

/* ===== 列表 ===== */
.sg-count { font-size: 12px; color: var(--text-light); }
.sg-empty { padding: 8px 0; }
.sg-list { display: flex; flex-direction: column; gap: 12px; }
.sg-item { padding: 14px 16px; border-radius: 14px; border: 1px solid var(--border);
  background: color-mix(in srgb, var(--card-bg) 60%, transparent); }
.sg-item-head { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; flex-wrap: wrap; }
.sg-cat-tag { font-size: 12px; font-weight: 600; padding: 2px 10px; border-radius: 999px; flex-shrink: 0; }
.sc0 { color: #0d9488; background: rgba(13, 148, 136, .12); }
.sc1 { color: #0369a1; background: rgba(3, 105, 161, .12); }
.sc2 { color: #be185d; background: rgba(190, 24, 93, .12); }
.sc3 { color: #64748b; background: rgba(100, 116, 139, .12); }
html.dark .sc0 { color: #2dd4bf; } html.dark .sc1 { color: #38bdf8; }
html.dark .sc2 { color: #fb7185; } html.dark .sc3 { color: #94a3b8; }
.sg-status { font-size: 12px; font-weight: 700; padding: 2px 10px; border-radius: 999px; flex-shrink: 0; }
.st-pending { color: #d97706; background: rgba(245, 158, 11, .12); border: 1px solid rgba(245, 158, 11, .3); }
.st-accepted { color: #0d9488; background: rgba(13, 148, 136, .12); border: 1px solid rgba(13, 148, 136, .3); }
.st-done { color: #059669; background: rgba(16, 185, 129, .12); border: 1px solid rgba(16, 185, 129, .3); }
.st-rejected { color: #64748b; background: rgba(100, 116, 139, .12); border: 1px solid rgba(100, 116, 139, .3); }
html.dark .st-pending { color: #fbbf24; }
html.dark .st-accepted { color: #2dd4bf; }
html.dark .st-done { color: #34d399; }
html.dark .st-rejected { color: #94a3b8; }
.sg-item-meta { margin-left: auto; font-size: 12px; color: var(--text-light); }
.sg-item-content { margin: 0; font-size: 13.5px; line-height: 1.7; color: var(--text); white-space: pre-wrap; word-break: break-word; }

/* 管理员回复块 */
.sg-reply { margin-top: 10px; padding: 10px 12px; border-radius: 10px;
  background: rgba(43, 177, 135, .07); border: 1px dashed rgba(43, 177, 135, .35); }
.sg-reply b { font-size: 12px; color: var(--primary); }
html.dark .sg-reply b { color: var(--primary); }
.sg-reply p { margin: 4px 0 0; font-size: 13px; line-height: 1.65; color: var(--text); white-space: pre-wrap; word-break: break-word; }

/* 管理员处理区 */
.sg-handle { margin-top: 10px; padding-top: 10px; border-top: 1px dashed var(--border);
  display: flex; flex-direction: column; gap: 8px; }
.sg-handle-btns { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.sg-flex { flex: 1; }
.sg-btn-reject { background: transparent; color: #d97706; border: 1px solid rgba(245, 158, 11, .45); }
.sg-btn-reject:hover { background: rgba(245, 158, 11, .08); }
html.dark .sg-btn-reject { color: #fbbf24; }
</style>
