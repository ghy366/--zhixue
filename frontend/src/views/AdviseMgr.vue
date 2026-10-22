<template>
  <div class="page-wrap stats-page liquid">
    <!-- 概览统计 -->
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-icon blue" v-html="svgIcon('users')" />
        <div><div class="stat-value">{{ overview.teacherCount ?? 0 }}</div><div class="stat-label">导师总数</div></div>
      </div>
      <div class="stat-card">
        <div class="stat-icon green" v-html="svgIcon('check-circle')" />
        <div><div class="stat-value">{{ overview.totalApproved ?? 0 }}</div><div class="stat-label">已确认配对</div></div>
      </div>
      <div class="stat-card">
        <div class="stat-icon amber" v-html="svgIcon('hourglass')" />
        <div><div class="stat-value">{{ overview.totalPending ?? 0 }}</div><div class="stat-label">待审批申请</div></div>
      </div>
      <div class="stat-card">
        <div class="stat-icon purple" v-html="svgIcon('star')" />
        <div><div class="stat-value">{{ overview.teachersWithSpace ?? 0 }}</div><div class="stat-label">有空位导师</div></div>
      </div>
    </div>

    <!-- 全局配置 -->
    <div class="section">
      <div class="section-header">
        <div class="section-title" v-html="svgIcon('settings') + ' 全局配置'" />
      </div>
      <div class="cfg-row">
        <label class="cfg-item">
          <el-switch v-model="cfg.open" />
          <span>开启导师选择</span>
        </label>
        <label class="cfg-item">
          <span>默认容量</span>
          <el-input-number v-model="cfg.defaultCapacity" :min="1" :max="99" size="small" style="width:100px" />
        </label>
        <el-button type="primary" size="small" :loading="cfgSaving" @click="saveCfg">保存配置</el-button>
        <span class="cfg-tip">新导师未单独设置容量时，使用此默认值</span>
      </div>
    </div>

    <!-- 导师容量管理 -->
    <div class="section">
      <div class="section-header">
        <div class="section-title" v-html="svgIcon('graduation-cap') + ' 导师容量管理'" />
      </div>
      <div class="data-table">
        <table>
          <thead>
            <tr><th>导师</th><th>工号</th><th>学院</th><th>已指导</th><th>容量</th><th>状态</th></tr>
          </thead>
          <tbody>
            <tr v-for="a in caps" :key="a.teacherId">
              <td><b>{{ a.teacherName }}</b></td>
              <td class="mono">{{ a.teacherNo }}</td>
              <td>{{ a.teacherCollege || '—' }}</td>
              <td>
                {{ (a.approved ?? 0) + (a.pending ?? 0) }}
                <span class="sub-note">(已确认 {{ a.approved ?? 0 }} + 待确认 {{ a.pending ?? 0 }})</span>
              </td>
              <td>
                <el-input-number
                  :model-value="a.capacity"
                  :min="1" :max="99" size="small" style="width:100px"
                  :disabled="savingId === a.teacherId"
                  @update:model-value="(v:any) => updateCap(a, v)"
                />
              </td>
              <td>
                <span class="status-tag" :class="(a.approved ?? 0) >= (a.capacity ?? 999) ? 'status-pending' : 'status-approved'">
                  {{ (a.approved ?? 0) >= (a.capacity ?? 999) ? '已满' : '可接受' }}
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="empty-state" v-if="!caps.length" style="padding:40px;color:var(--text-light);text-align:center">暂无导师数据</div>
    </div>

    <!-- 全部申请 -->
    <div class="section">
      <div class="section-header">
        <div class="section-title" v-html="svgIcon('file-text') + ' 全部申请（' + list.length + '）'" />
      </div>
      <div class="data-table" v-if="list.length">
        <table>
          <thead>
            <tr><th>学生</th><th>导师</th><th>论文方向</th><th>状态</th><th>时间</th><th>操作</th></tr>
          </thead>
          <tbody>
            <tr v-for="r in list" :key="r.id">
              <td>
                <b>{{ r.studentName }}</b>
                <span class="sub-note">({{ r.studentNo }})</span>
              </td>
              <td>{{ r.teacherName }}</td>
              <td>{{ r.topic || '—' }}</td>
              <td>
                <span class="status-tag" :class="statusCls(r.status)">{{ statusText(r.status) }}</span>
              </td>
              <td class="mono">{{ fmtTime(r.createdAt) }}</td>
              <td>
                <el-button size="small" plain @click="removeRow(r)">删除</el-button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="empty-state" v-else style="padding:40px;color:var(--text-light);text-align:center">暂无申请记录</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api'

const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`

const overview = reactive<any>({
  teacherCount: 0, totalApproved: 0, totalPending: 0, teachersWithSpace: 0
})
const cfg = reactive<{ open: boolean; defaultCapacity: number }>({ open: true, defaultCapacity: 15 })
const caps = ref<any[]>([])
const list = ref<any[]>([])
const savingId = ref<number | null>(null)
const cfgSaving = ref(false)

const STATUS: Record<string, string> = {
  approved: '已确认', pending: '待审批', rejected: '已拒绝', cancelled: '已取消'
}
const statusText = (s: string) => STATUS[s] || s || '—'
const statusCls = (s: string) =>
  s === 'approved' ? 'status-approved'
  : s === 'pending' ? 'status-pending'
  : s === 'rejected' ? 'status-rejected' : 'status-cancelled'

const fmtTime = (t: number) => (t ? new Date(t).toLocaleString('zh-CN') : '—')
// 后端业务失败走 ApiResponse.message；HTTP 异常时取 response.data.message，避免只显示 "status code 500"
const errMsg = (e: any, fallback: string) =>
  e?.response?.data?.message || (typeof e?.message === 'string' && !/status code/.test(e.message) ? e.message : fallback)

async function load() {
  try {
    // 概览：导师数 / 已确认 / 待审批 / 有空位
    const [o, c, l] = await Promise.all([
      http.get('/advisor/admin/overview'),
      http.get('/advisor/admin/capacities'),
      http.get('/advisor/admin/list')
    ])
    const od: any = o?.data || {}
    Object.assign(overview, {
      teacherCount: od.teacherCount ?? 0,
      totalApproved: od.totalApproved ?? 0,
      totalPending: od.totalPending ?? 0,
      teachersWithSpace: od.teachersWithSpace ?? 0
    })
    if (od.config) {
      cfg.open = !!od.config.open
      cfg.defaultCapacity = od.config.defaultCapacity ?? 15
    }
    caps.value = c?.data || []
    list.value = l?.data || []
  } catch (e: any) {
    ElMessage.warning(errMsg(e, '加载失败'))
  }
}

// 导师容量：后端唯一入口 POST /advisor/admin/cfg { teacherId, teacherCapacity }
async function updateCap(a: any, v: number) {
  if (v === undefined || v === null) return
  if (savingId.value !== null) return   // 防连点：一次只允许一个提交
  const val = Number(v)
  if (!Number.isFinite(val) || val < 1) {
    ElMessage.warning('容量至少为 1')
    load()
    return
  }
  savingId.value = a.teacherId
  try {
    const r: any = await http.post('/advisor/admin/cfg', { teacherId: a.teacherId, teacherCapacity: val })
    a.capacity = val
    ElMessage.success(r?.message || `${a.teacherName} 容量已更新为 ${val}`)
  } catch (e: any) {
    ElMessage.error(errMsg(e, '更新失败'))
    load()
  } finally {
    savingId.value = null
  }
}

async function saveCfg() {
  cfgSaving.value = true
  try {
    const r: any = await http.post('/advisor/admin/cfg', {
      open: cfg.open,
      defaultCapacity: cfg.defaultCapacity
    })
    ElMessage.success(r?.message || '配置已保存')
    load()
  } catch (e: any) {
    ElMessage.error(errMsg(e, '保存失败'))
  } finally {
    cfgSaving.value = false
  }
}

async function removeRow(r: any) {
  try {
    await ElMessageBox.confirm(
      `确定删除「${r.studentName} → ${r.teacherName}」这条申请记录？`, '删除申请',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch { return }
  try {
    const res: any = await http.delete(`/advisor/admin/${r.id}`)
    ElMessage.success(res?.message || '已删除')
    load()
  } catch (e: any) {
    ElMessage.error(errMsg(e, '删除失败'))
  }
}

onMounted(load)
</script>

<style scoped>
.page-wrap { padding: 4px 0; }
.cfg-row { display: flex; align-items: center; gap: 22px; flex-wrap: wrap; }
.cfg-item { display: inline-flex; align-items: center; gap: 8px; font-size: 13px; color: var(--text-light); }
.cfg-tip { font-size: 12px; color: var(--text-lighter); }
.mono { font-variant-numeric: tabular-nums; font-size: 13px; }
.sub-note { color: var(--text-lighter); font-size: 12px; }
.status-tag.status-rejected { background: rgba(239,68,68,.14); color: #ef4444; }
.status-tag.status-cancelled { background: rgba(148,163,184,.16); color: var(--text-light); }
</style>
