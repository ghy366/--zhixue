<template>
  <div class="page-wrap stats-page liquid">
    <div class="ga-hero">
      <div class="ga-hero-main">
        <h2>{{ t('🎓 毕业审核') }}</h2>
        <p>{{ t('学生满足全部毕业要求后提交申请，教务逐项核验学分/课程数/GPA 后批准或驳回；批准即授予学位。') }}</p>
      </div>
      <div class="ga-hero-stats">
        <div class="ga-hstat"><b>{{ pending.length }}</b><span>{{ t('待审核') }}</span></div>
        <div class="ga-hstat"><b>{{ approved }}</b><span>{{ t('已批准') }}</span></div>
        <div class="ga-hstat"><b>{{ items.length }}</b><span>{{ t('累计申请') }}</span></div>
      </div>
    </div>

    <div class="section">
      <div class="section-header">
        <div class="section-title" v-html="svgIcon('graduation-cap') + ' ' + t('毕业申请列表')"></div>
        <button class="btn btn-ghost btn-sm" @click="load">{{ t('刷新') }}</button>
      </div>

      <el-empty v-if="!items.length" :description="t('还没有学生提交毕业申请')" />

      <div v-else class="data-table">
        <table>
          <thead>
            <tr>
              <th>{{ t('学号') }}</th><th>{{ t('姓名') }}</th><th>{{ t('学院 / 专业') }}</th><th>{{ t('年级') }}</th>
              <th>{{ t('学分进度') }}</th><th>{{ t('课程数') }}</th><th>{{ t('GPA') }}</th>
              <th>{{ t('申请时间') }}</th><th>{{ t('状态') }}</th><th>{{ t('审核') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="a in items" :key="a.id">
              <td><b>{{ a.studentNo }}</b></td>
              <td>{{ a.studentName }}</td>
              <td class="ga-muted">{{ a.college || '—' }} / {{ a.major || '—' }}</td>
              <td>{{ a.grade ? t('{n} 级', { n: a.grade }) : '—' }}</td>
              <td>
                <div class="ga-credit-bar"><i :style="{ width: creditPct(a) + '%' }" /></div>
                <small class="ga-muted">{{ a.creditsEarned }} / {{ a.creditsRequired }}</small>
              </td>
              <td>{{ a.courseCount }}</td>
              <td><b class="ga-gpa">{{ a.gpa != null ? Number(a.gpa).toFixed(2) : '—' }}</b></td>
              <td class="ga-muted">{{ fmt(a.applyTime) }}</td>
              <td>
                <span class="ga-status" :class="'st-' + a.status.toLowerCase()">{{ statusCn(a.status) }}</span>
                <small v-if="a.remark" class="ga-remark" :title="a.remark">{{ a.remark }}</small>
              </td>
              <td>
                <template v-if="a.status === 'PENDING'">
                  <div class="ga-actions">
                    <el-input v-model="a._remark" size="small" :placeholder="t('备注（驳回必填）')" style="width:150px" />
                    <button class="btn btn-success btn-sm" @click="audit(a, 'APPROVED')">{{ t('批准') }}</button>
                    <button class="btn btn-sm ga-reject" @click="audit(a, 'REJECTED')">{{ t('驳回') }}</button>
                  </div>
                </template>
                <span v-else class="ga-muted">{{ a.reviewerName ? t('由 {n} 审核', { n: a.reviewerName }) : '—' }}</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api'
import { t } from '@/i18n'
const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`

const items = ref<any[]>([])
const pending = computed(() => items.value.filter(a => a.status === 'PENDING'))
const approved = computed(() => items.value.filter(a => a.status === 'APPROVED').length)

function creditPct(a: any) {
  const req = Number(a.creditsRequired) || 0
  return req <= 0 ? 0 : Math.min(100, Math.round((Number(a.creditsEarned) || 0) * 100 / req))
}
function statusCn(s: string) {
  return s === 'PENDING' ? t('待审核') : s === 'APPROVED' ? t('已批准 🎓') : t('已驳回')
}
function fmt(ts: any) {
  if (!ts) return '—'
  const d = new Date(ts)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

async function load() {
  try {
    const r: any = await http.get('/graduation/applications')
    items.value = (r?.data || []).map((x: any) => ({ ...x, _remark: '' }))
  } catch (e: any) { ElMessage.error(e?.message || t('加载失败')) }
}

async function audit(a: any, status: string) {
  const remark = (a._remark || '').trim()
  if (status === 'REJECTED' && !remark) { ElMessage.warning(t('驳回时请填写原因')); return }
  try {
    if (status === 'APPROVED') {
      await ElMessageBox.confirm(t('批准 {n} 的毕业申请？批准后将授予学位。', { n: a.studentName }), t('批准毕业'),
        { type: 'warning', confirmButtonText: t('批准'), cancelButtonText: t('取消') })
    }
  } catch { return }
  try {
    const r: any = await http.post(`/graduation/applications/${a.id}/audit`, { status, remark })
    ElMessage.success(r?.message || t('已更新'))
    load()
  } catch (e: any) { ElMessage.error(e?.message || t('操作失败')) }
}

onMounted(load)
</script>

<style scoped>
.page-wrap { padding: 4px 0; display: flex; flex-direction: column; gap: 16px; }
.ga-hero { display: flex; justify-content: space-between; align-items: flex-end; gap: 18px; flex-wrap: wrap; }
.ga-hero h2 { margin: 0 0 6px; font-size: 22px; font-weight: 800; color: var(--text); }
.ga-hero p { margin: 0; font-size: 13px; color: var(--text-light); max-width: 640px; line-height: 1.65; }
.ga-hero-stats { display: flex; gap: 22px; padding: 12px 20px; border-radius: 14px;
  background: color-mix(in srgb, var(--card-bg) 70%, transparent); border: 1px solid var(--border); }
.ga-hstat { display: flex; flex-direction: column; align-items: center; gap: 2px; min-width: 60px; }
.ga-hstat b { font-size: 20px; font-weight: 800; color: var(--text); font-variant-numeric: tabular-nums; }
.ga-hstat span { font-size: 11.5px; color: var(--text-light); }
.ga-muted { color: var(--text-lighter); font-size: 12px; }
.ga-credit-bar { height: 8px; border-radius: 99px; background: rgba(148,163,184,.15); overflow: hidden; width: 110px; }
.ga-credit-bar i { display: block; height: 100%; border-radius: 99px; background: linear-gradient(90deg, #2bb187, #38bdf8); }
.ga-gpa { font-variant-numeric: tabular-nums; color: var(--primary); }
.ga-status { font-size: 12px; font-weight: 700; padding: 2px 10px; border-radius: 999px; }
.ga-status.st-pending { color: #d97706; background: rgba(245,158,11,.12); border: 1px solid rgba(245,158,11,.3); }
.ga-status.st-approved { color: #059669; background: rgba(16,185,129,.12); border: 1px solid rgba(16,185,129,.3); }
.ga-status.st-rejected { color: #64748b; background: rgba(100,116,139,.12); border: 1px solid rgba(100,116,139,.3); }
html.dark .ga-status.st-pending { color: #fbbf24; }
html.dark .ga-status.st-approved { color: #34d399; }
html.dark .ga-status.st-rejected { color: #94a3b8; }
.ga-remark { display: block; margin-top: 2px; max-width: 160px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--text-lighter); }
.ga-actions { display: flex; align-items: center; gap: 6px; }
.ga-reject { background: transparent; color: #d97706; border: 1px solid rgba(245,158,11,.45); }
.ga-reject:hover { background: rgba(245,158,11,.08); }
html.dark .ga-reject { color: #fbbf24; }
:deep(.el-table) { display: none; }
</style>
