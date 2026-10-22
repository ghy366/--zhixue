<template>
  <div class="page-wrap stats-page liquid">
    <div class="section-header" style="justify-content:flex-start;gap:16px;position:static;background:transparent;backdrop-filter:none">
      <div class="section-title" v-html="svgIcon('graduation-cap') + ' 毕业设计导师指导'"></div>
    </div>
    <div class="stats-grid">
      <div class="stat-card"><div class="stat-icon green" v-html="svgIcon('check-circle')" /><div><div class="stat-value">{{ approved.length }}</div><div class="stat-label">进行中</div></div></div>
      <div class="stat-card"><div class="stat-icon amber" v-html="svgIcon('hourglass')" /><div><div class="stat-value">{{ pending.length }}</div><div class="stat-label">待确认</div></div></div>
      <div class="stat-card"><div class="stat-icon blue" v-html="svgIcon('users')" /><div><div class="stat-value">{{ total }}</div><div class="stat-label">总学生数</div></div></div>
    </div>

    <div class="section" v-if="total">
      <div class="data-table">
        <table>
          <thead>
            <tr><th>学号</th><th>姓名</th><th>专业</th><th>毕设题目</th><th>状态</th><th>申请说明</th><th>操作</th></tr>
          </thead>
          <tbody>
            <tr v-for="s in list" :key="s.id">
              <td>{{ s.studentNo }}</td>
              <td>{{ s.studentName }}</td>
              <td>{{ s.studentMajor || '-' }}</td>
              <td><b>{{ s.topic || '—' }}</b></td>
              <td>
                <span class="status-tag" :class="phaseTag(s.status)">{{ s.status }}</span>
              </td>
              <td style="max-width:200px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap" :title="s.preferenceText">{{ s.preferenceText || '—' }}</td>
              <td>
                <template v-if="s.status === 'pending'">
                  <button class="btn btn-success btn-sm" @click="decide(s, 'approve')">通过</button>
                  <button class="btn btn-danger btn-sm" @click="decide(s, 'reject')">拒绝</button>
                </template>
                <button v-else-if="s.status === 'approved'" class="btn btn-outline btn-sm" @click="$router.push('/chat')">发起对话</button>
                <span v-else class="btn btn-ghost btn-sm" style="cursor:not-allowed;color:var(--text-light)">已处理</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
    <div v-else class="empty-state">
      <div class="empty-icon" v-html="svgIcon('graduation-cap')" />
      <div class="empty-title">暂无指导学生</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`

const list = ref<any[]>([])

const approved = computed(() => list.value.filter(s => s.status === 'approved'))
const pending = computed(() => list.value.filter(s => s.status === 'pending'))
const total = computed(() => list.value.length)

function phaseTag(s: string) {
  if (s === 'approved') return 'status-approved'
  if (s === 'pending') return 'status-pending'
  if (s === 'rejected' || s === 'cancelled') return 'status-rejected'
  return 'status-approved'
}

async function load() {
  try {
    const r: any = await http.get('/advisor/teacher/list')
    list.value = r?.data || []
  } catch {}
}

async function decide(s: any, decision: string) {
  try {
    await ElMessageBox.confirm(
      decision === 'approve' ? `确认通过「${s.studentName}」的毕设申请？` : `确认拒绝「${s.studentName}」的毕设申请？`,
      '提示', { type: 'warning' }
    )
  } catch { return }
  try {
    await http.post('/advisor/teacher/decide', { id: s.id, decision })
    ElMessage.success(decision === 'approve' ? '已通过' : '已拒绝')
    await load()
  } catch (e: any) { ElMessage.error(e?.message || '操作失败') }
}

onMounted(load)
</script>

<style scoped>
.page-wrap { padding: 4px 0; }
.empty-state { text-align: center; padding: 60px 20px; color: var(--text-light); }
.empty-icon { font-size: 48px; opacity: 0.3; margin-bottom: 10px; }
.status-tag { font-size: 11px; padding: 1px 8px; border-radius: 999px; }
.status-approved { background: rgba(16,185,129,0.15); color: #10b981; }
.status-pending { background: rgba(245,158,11,0.15); color: #f59e0b; }
.status-rejected { background: rgba(239,68,68,0.15); color: #ef4444; }
</style>
