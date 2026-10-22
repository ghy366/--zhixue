<template>
  <div class="zx-page stats-page liquid">
    <div class="page-head">
      <h2>📝 我的选课</h2>
      <el-button type="primary" plain size="small" @click="batchMode = !batchMode">
        {{ batchMode ? '退出批量' : '批量退课' }}
      </el-button>
    </div>

    <div class="credits-row">
      <div class="credit-chip">已选学分 <b>{{ totalCredits }}</b></div>
      <div class="credit-chip">本学期上限 <b>{{ maxCredit || '不限' }}</b></div>
      <div class="credit-chip">已选 <b>{{ approved.length }}</b> 门</div>
      <div class="credit-chip" v-if="pending.length">待审核 <b>{{ pending.length }}</b></div>
    </div>

    <el-tabs v-model="tab" class="zx-tabs">
      <el-tab-pane label="已选课程" name="approved">
        <el-table :data="approved" v-loading="loading" style="width:100%">
          <el-table-column v-if="batchMode" width="44">
            <template #header><el-checkbox v-model="allApproved" @change="toggleAllApproved" /></template>
            <template #default="{ row }">
              <el-checkbox :model-value="checkedApproved.includes(row.id)" :disabled="isRequired(row)" @change="(v:any)=>toggleOne(row.id, v)" />
            </template>
          </el-table-column>
          <el-table-column prop="courseName" label="课程名" />
          <el-table-column prop="teacherName" label="教师" width="140" />
          <el-table-column prop="credit" label="学分" width="80" align="center" />
          <el-table-column prop="category" label="类型" width="90" align="center" />
          <el-table-column label="成绩" width="100" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.grade" type="success" size="small">{{ row.grade }}</el-tag>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140" align="right">
            <template #default="{ row }">
              <el-tooltip :disabled="!isRequired(row)" content="必修课由教学计划自动安排，不能退课" placement="top">
                <span>
                  <el-button size="small" text type="danger" :disabled="batchMode || isRequired(row)" @click="drop(row.id)">退课</el-button>
                </span>
              </el-tooltip>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="待审核" name="pending">
        <el-table :data="pending" v-loading="loading" style="width:100%">
          <el-table-column prop="courseName" label="课程名" />
          <el-table-column prop="teacherName" label="教师" width="140" />
          <el-table-column prop="credit" label="学分" width="80" align="center" />
          <el-table-column label="积分投入" width="100" align="center">
            <template #default="{ row }">{{ row.points ?? 0 }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100" align="center">
            <el-tag type="warning" size="small">待审核</el-tag>
          </el-table-column>
          <el-table-column label="操作" width="100" align="right">
            <template #default="{ row }">
              <el-button size="small" text @click="cancel(row.id)">取消</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="候补" name="waitlist">
        <el-table :data="waitlist" v-loading="loading" style="width:100%">
          <el-table-column prop="courseName" label="课程名" />
          <el-table-column prop="teacherName" label="教师" width="140" />
          <el-table-column label="位置" width="100" align="center">
            <template #default="{ row }">第 {{ row.position || '—' }} 位</template>
          </el-table-column>
          <el-table-column label="状态" width="120" align="center">
            <el-tag :type="wlTagType(row.status)" size="small">{{ wlText(row.status) }}</el-tag>
          </el-table-column>
          <el-table-column label="操作" width="100" align="right">
            <template #default="{ row }">
              <el-button size="small" text :disabled="row.status !== 'waiting' && row.status !== 'confirming'" @click="cancelWait(row)">取消</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-button v-if="batchMode && checkedApproved.length" type="danger" plain @click="batchDrop">
      批量退选（{{ checkedApproved.length }} 门）
    </el-button>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api'

interface Enrollment { id: number; courseId: number; courseName: string; teacherName: string; credit: number; category: string; status: string; points?: number; grade?: string }
interface Waitlist { waitlistId: number; courseId: number; courseName: string; teacherName: string; position: number; status: string }

const loading = ref(false)
const tab = ref('approved')
const enrollments = ref<Enrollment[]>([])
const waitlists = ref<Waitlist[]>([])
const config = ref<any>({})
const batchMode = ref(false)
const checkedApproved = ref<number[]>([])

const approved = computed(() => enrollments.value.filter(e => e.status === 'approved'))
const pending = computed(() => enrollments.value.filter(e => e.status === 'pending'))
// 必修课（公共必修/专业必修）由系统自动排课，后端不允许退课
const isRequired = (r: Enrollment) => (r.category || '').includes('必修')
const removable = computed(() => approved.value.filter(e => !isRequired(e)))
const totalCredits = computed(() => approved.value.reduce((s, e) => s + (e.credit || 0), 0))
const maxCredit = computed(() => config.value.maxCreditPerSemester)
const allApproved = computed(() => removable.value.length > 0 && checkedApproved.value.length === removable.value.length)

function toggleAllApproved(v: boolean) {
  checkedApproved.value = v ? removable.value.map(e => e.id) : []
}
function toggleOne(id: number, v: boolean) {
  if (v && !checkedApproved.value.includes(id)) checkedApproved.value.push(id)
  if (!v) checkedApproved.value = checkedApproved.value.filter(x => x !== id)
}

async function load() {
  loading.value = true
  try {
    const [e, w, c] = await Promise.all([
      http.get('/enrollments/mine'),
      http.get('/waitlist/mine'),
      http.get('/system/config'),
    ])
    // 后端字段：enrollmentId / teacherName / credit / category，统一成前端短名
    enrollments.value = (e?.data || []).map((x: any) => ({
      id: x.enrollmentId,
      courseId: x.courseId,
      courseName: x.courseName,
      teacherName: x.teacherName || '',
      credit: x.credit || 0,
      category: x.category || '',
      status: x.status,
      points: x.points ?? 0,
      grade: x.grade,
    }))
    waitlists.value = (w?.data || []).map((x: any) => ({
      waitlistId: x.waitlistId,
      courseId: x.courseId,
      courseName: x.courseName,
      teacherName: x.teacherName || '',
      position: x.position,
      status: x.status || 'waiting',
    }))
    config.value = c?.data || {}
  } catch (err: any) {
    ElMessage.error(err?.message || '加载选课数据失败')
  }
  loading.value = false
}

function wlText(s: string) {
  return s === 'waiting' ? '候补中'
    : s === 'confirming' ? '待确认递补'
    : s === 'confirmed' ? '已递补'
    : s === 'cancelled' ? '已取消'
    : s === 'timeout' ? '已过期'
    : (s || '—')
}
function wlTagType(s: string) {
  return s === 'waiting' ? 'info'
    : s === 'confirming' ? 'warning'
    : s === 'confirmed' ? 'success'
    : 'danger'
}

async function drop(id: number) {
  const row = approved.value.find(r => r.id === id)
  if (!row?.courseId) { ElMessage.error('未找到该选课记录，请刷新后重试'); return }
  try {
    await ElMessageBox.confirm(`确定退选《${row.courseName}》？投入的积分将返还。`, '退课确认', {
      type: 'warning', confirmButtonText: '确定退课', cancelButtonText: '再想想'
    })
  } catch { return }
  try {
    const r = await http.post('/enrollments/drop', { courseId: row.courseId })
    ElMessage.success(r?.message || '已退选')
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '退课失败')
  }
}
async function batchDrop() {
  try {
    await ElMessageBox.confirm(`确定批量退选 ${checkedApproved.value.length} 门？`, '提示', { type: 'warning' })
  } catch { return }
  try {
    const r = await http.post('/enrollments/drop-batch', { courseIds: checkedApproved.value })
    ElMessage.success(r?.message || '批量退选完成')
    checkedApproved.value = []
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '批量退课失败')
  }
}
// 待审核 → 撤销报名：后端没有 DELETE /enrollments/{id}，统一走 /enrollments/drop（pending 会退积分）
async function cancel(id: number) {
  const row = pending.value.find(r => r.id === id)
  if (!row?.courseId) { ElMessage.error('未找到该报名记录，请刷新后重试'); return }
  try {
    await ElMessageBox.confirm(`确定取消《${row.courseName}》的报名？已投入的 ${row.points ?? 0} 积分将返还。`, '取消报名', {
      type: 'warning', confirmButtonText: '确定取消', cancelButtonText: '再想想'
    })
  } catch { return }
  try {
    const r = await http.post('/enrollments/drop', { courseId: row.courseId })
    ElMessage.success(r?.message || '已取消报名')
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '取消失败')
  }
}
async function cancelWait(row: Waitlist) {
  try {
    await ElMessageBox.confirm(`确定取消《${row.courseName}》的候补？`, '取消候补', {
      type: 'warning', confirmButtonText: '确定取消', cancelButtonText: '再想想'
    })
  } catch { return }
  try {
    const r = await http.post('/waitlist/cancel', { courseId: row.courseId })
    ElMessage.success(r?.message || '已取消候补')
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '取消候补失败')
  }
}

onMounted(load)
</script>

<style scoped>
.zx-page { display: flex; flex-direction: column; gap: 14px; }
.page-head { display: flex; justify-content: space-between; align-items: center; }
.page-head h2 { margin: 0; color: var(--text); font-size: 20px; }

.credits-row { display: flex; gap: 10px; flex-wrap: wrap; }
.credit-chip {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 8px; padding: 6px 14px; font-size: 13px; color: var(--text-light);
}
/* 亮色主题必须用深绿，否则 #4ade80 贴白底看不清 */
.credit-chip b { color: var(--green-text); margin-left: 4px; font-weight: 700; }

:deep(.el-tabs__item) { color: var(--text-light) !important; font-size: 14px; }
:deep(.el-tabs__item.is-active) { color: var(--green-text) !important; font-weight: 600; }
:deep(.el-tabs__active-bar) { background-color: var(--green-text) !important; }
:deep(.el-table) {
  --el-table-tr-bg-color: var(--card);
  --el-table-header-bg-color: var(--table-head-bg);
  --el-table-border-color: var(--grid-line);
  --el-table-text-color: var(--text);
  --el-table-header-text-color: var(--green-text);
  --el-table-row-hover-bg-color: var(--card-bg-2);
  background: var(--card);
  border-radius: 8px; overflow: hidden;
  font-size: 14px;
}
:deep(.el-table th.el-table__cell) { font-weight: 600; }
.muted { color: var(--text-light); }
</style>
