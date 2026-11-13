<template>
  <div class="page-wrap stats-page liquid">
    <div class="stats-grid">
      <div class="stat-card"><div class="stat-icon amber">⏳</div><div><div class="stat-value">{{ pendingCount }}</div><div class="stat-label">{{ t('待处理') }}</div></div></div>
      <div class="stat-card"><div class="stat-icon green">✓</div><div><div class="stat-value">{{ approvedCount }}</div><div class="stat-label">{{ t('已同意') }}</div></div></div>
      <div class="stat-card"><div class="stat-icon blue">✕</div><div><div class="stat-value">{{ rejectedCount }}</div><div class="stat-label">{{ t('已驳回') }}</div></div></div>
    </div>

    <div class="section">
      <div class="section-header">
        <div class="section-title">{{ t('退课申请') }}</div>
        <div class="tab-group">
          <button class="tab-btn" :class="{ active: tab === 'pending' }" @click="tab = 'pending'">{{ t('待处理') }}</button>
          <button class="tab-btn" :class="{ active: tab === 'all' }" @click="tab = 'all'">{{ t('全部') }}</button>
          <button class="btn btn-outline btn-sm" @click="load">{{ t('刷新') }}</button>
        </div>
      </div>

      <div class="data-table">
        <table>
          <thead>
            <tr>
              <th>{{ t('学生') }}</th><th>{{ t('学号') }}</th><th>{{ t('课程') }}</th>
              <th>{{ t('轮次') }}</th><th>{{ t('申请时间') }}</th><th>{{ t('状态') }}</th><th>{{ t('操作') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in shown" :key="r.id">
              <td>{{ r.studentName }}</td>
              <td>{{ r.studentNo || '-' }}</td>
              <td>{{ r.courseName }}</td>
              <td>{{ t('第 {n} 轮', { n: r.roundNo }) }}</td>
              <td>{{ fmt(r.createdAt) }}</td>
              <td><span class="dr-status" :class="r.status">{{ statusText(r.status) }}</span></td>
              <td>
                <template v-if="r.status === 'pending'">
                  <button class="btn btn-primary btn-sm" @click="approve(r)">{{ t('同意退课') }}</button>
                  <button class="btn btn-outline btn-sm" @click="reject(r)">{{ t('驳回') }}</button>
                </template>
                <span v-else class="dr-remark" :title="r.remark || ''">{{ r.remark || '-' }}</span>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-if="!shown.length" class="dr-empty">{{ t('暂无退课申请') }}</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api'
import { t } from '@/i18n'

const list = ref<any[]>([])
const tab = ref<'pending' | 'all'>('pending')
const shown = computed(() => tab.value === 'pending' ? list.value.filter(r => r.status === 'pending') : list.value)
const pendingCount = computed(() => list.value.filter(r => r.status === 'pending').length)
const approvedCount = computed(() => list.value.filter(r => r.status === 'approved').length)
const rejectedCount = computed(() => list.value.filter(r => r.status === 'rejected').length)

async function load() {
  try {
    const r: any = await http.get('/drop-requests')
    list.value = r?.data || []
  } catch (e: any) {
    ElMessage.error(e?.message || t('加载退课申请失败'))
  }
}

async function approve(r: any) {
  try {
    await ElMessageBox.confirm(
      t('同意后《{n}》将立即从 {s} 的已录取课程中移除，确定？', { n: r.courseName, s: r.studentName }),
      t('同意退课'), { type: 'warning', confirmButtonText: t('确定'), cancelButtonText: t('取消') })
  } catch { return }
  try {
    const res: any = await http.post(`/drop-requests/${r.id}/approve`, {})
    ElMessage.success(res?.message || t('已同意退课'))
    load()
  } catch (e: any) { ElMessage.error(e?.message || t('操作失败')) }
}

async function reject(r: any) {
  let remark = ''
  try {
    const v = await ElMessageBox.prompt(t('可填写驳回原因，学生会在消息通知里看到'), t('驳回退课申请'), {
      type: 'warning', confirmButtonText: t('确定'), cancelButtonText: t('取消'),
      inputPlaceholder: t('驳回原因（选填）')
    })
    remark = v?.value || ''
  } catch { return }
  try {
    const res: any = await http.post(`/drop-requests/${r.id}/reject`, { remark })
    ElMessage.success(res?.message || t('已驳回申请'))
    load()
  } catch (e: any) { ElMessage.error(e?.message || t('操作失败')) }
}

function statusText(s: string) {
  return s === 'pending' ? t('待处理') : s === 'approved' ? t('已同意') : t('已驳回')
}
function fmt(ms: number) {
  if (!ms) return '-'
  const d = new Date(ms), p = (n: number) => String(n).padStart(2, '0')
  return `${d.getMonth() + 1}-${d.getDate()} ${p(d.getHours())}:${p(d.getMinutes())}`
}

onMounted(load)
</script>

<style scoped>
.stat-icon { width: 38px; height: 38px; display: flex; align-items: center; justify-content: center; border-radius: 10px; font-size: 18px; font-weight: 700; }
.stat-icon.amber { background: rgba(245,158,11,.12); color: #d97706; }
.stat-icon.green { background: rgba(43,177,135,.12); color: #16a34a; }
.stat-icon.blue { background: rgba(56,189,248,.12); color: #0284c7; }
.dr-status { display: inline-block; padding: 2px 10px; border-radius: 99px; font-size: 12px; font-weight: 600; }
.dr-status.pending { background: rgba(245,158,11,.14); color: #d97706; }
.dr-status.approved { background: rgba(43,177,135,.14); color: #16a34a; }
.dr-status.rejected { background: rgba(148,163,184,.16); color: var(--text-light); }
.dr-remark { font-size: 12px; color: var(--text-light); }
.dr-empty { padding: 40px; text-align: center; font-size: 13px; color: var(--text-light); }
.data-table .btn-sm + .btn-sm { margin-left: 6px; }
</style>
