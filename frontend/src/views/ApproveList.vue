<template>
  <div class="page-wrap">
    <!-- ===== 第一阶段：课程列表 ===== -->
    <template v-if="!currentCourse">
      <div class="stats-grid">
        <div class="stat-card">
          <div class="stat-icon amber" v-html="svgIcon('hourglass')" />
          <div><div class="stat-value">{{ needApprove.length }}</div><div class="stat-label">超容量·需审批</div></div>
        </div>
        <div class="stat-card">
          <div class="stat-icon blue" v-html="svgIcon('book')" />
          <div><div class="stat-value">{{ courses.length }}</div><div class="stat-label">全部授课</div></div>
        </div>
      </div>

      <div class="section">
        <div class="section-header">
          <div class="section-title">待审批课程（{{ needApprove.length }}）</div>
        </div>
        <div v-if="needApprove.length" class="data-table">
          <table>
            <thead><tr><th>课程</th><th>容量</th><th>报名人数</th><th>状态</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="c in needApprove" :key="c.id">
                <td><b>{{ c.name }}</b></td>
                <td>{{ c.capacity }}</td>
                <td>{{ c.stat?.total || 0 }} 人</td>
                <td><span class="status-tag status-pending">超容量</span></td>
                <td><button class="btn btn-primary btn-sm" @click="enterDetail(c)">去审批</button></td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else class="empty-state">
          <div class="empty-icon" v-html="svgIcon('check-circle')" />
          <div class="empty-title">暂无需要审批的课程</div>
        </div>
      </div>

      <div class="section">
        <div class="section-header">
          <div class="section-title">我的全部课程（{{ courses.length }}）</div>
        </div>
        <div v-if="courses.length" class="data-table">
          <table>
            <thead><tr><th>课程</th><th>容量</th><th>报名</th><th>已录取</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="c in courses" :key="c.id">
                <td><b>{{ c.name }}</b></td>
                <td>{{ c.capacity }}</td>
                <td>{{ c.stat?.total || 0 }}</td>
                <td>{{ c.stat?.approved || 0 }}</td>
                <td><button class="btn btn-outline btn-sm" @click="enterDetail(c)">查看报名</button></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </template>

    <!-- ===== 第二阶段：选课详情 ===== -->
    <template v-else>
      <div class="section">
        <div class="section-header">
          <div class="section-title">审批「{{ currentCourse.name }}」选课（容量 {{ currentCourse.capacity }}）</div>
          <button class="btn btn-ghost btn-sm" @click="currentCourse = null">← 返回列表</button>
        </div>

        <div class="approve-toolbar">
          <div class="approve-count">已选 <b>{{ selected.size }}</b> / {{ currentCourse.capacity }} 人（报名共 {{ enrollments.length }} 人）</div>
          <div style="display:flex;gap:8px;">
            <button class="btn btn-outline btn-sm" @click="autoByPoints">按积分自动录取前{{ currentCourse.capacity }}人</button>
            <button class="btn btn-success btn-sm" @click="submitApprove" :disabled="submitting">
              {{ submitting ? '提交中…' : '提交审批结果' }}
            </button>
          </div>
        </div>

        <div class="data-table">
          <div v-for="e in enrollments" :key="e.studentId" class="approve-student-row" :class="{ selected: selected.has(e.studentId) }">
            <input type="checkbox" :checked="selected.has(e.studentId)" @change="toggleOne(e.studentId)" />
            <div class="stu-info">
              <div class="stu-name">{{ e.studentName }}</div>
              <div class="stu-meta">
                {{ e.studentNo }} · {{ e.studentMajor || '' }} · 状态：
                <span class="status-tag" :class="statusCls(e.status)">{{ statusText(e.status) }}</span>
              </div>
            </div>
            <span class="stu-points">{{ e.points }} 分</span>
          </div>
          <div v-if="!enrollments.length" class="empty-state">
            <div class="empty-icon" v-html="svgIcon('users')" />
            <div class="empty-title">暂无报名学生</div>
          </div>
        </div>
      </div>

      <div class="section">
        <div class="section-header">
          <div class="section-title">候补队列</div>
        </div>
        <div v-if="waitlist.length" class="data-table">
          <table>
            <thead><tr><th>学号</th><th>姓名</th><th>专业</th><th>状态</th><th>位置</th></tr></thead>
            <tbody>
              <tr v-for="w in waitlist" :key="w.id">
                <td>{{ w.studentNo }}</td>
                <td>{{ w.studentName }}</td>
                <td>{{ w.studentMajor || '-' }}</td>
                <td>{{ w.status === 'confirming' ? '待确认' : '候补中' }}</td>
                <td>第 {{ w.position }} 位</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else class="empty-state">
          <div class="empty-icon" v-html="svgIcon('hourglass')" />
          <div class="empty-title">暂无候补学生</div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`

const courses = ref<any[]>([])
const needApprove = computed(() => courses.value.filter(c => c.oversubscribed))

const currentCourse = ref<any>(null)
const enrollments = ref<any[]>([])
const waitlist = ref<any[]>([])
const selected = ref(new Set<number>())
const submitting = ref(false)

function statusCls(s: string) {
  if (s === 'approved') return 'status-approved'
  if (s === 'pending') return 'status-pending'
  return 'status-rejected'
}
function statusText(s: string) {
  if (s === 'approved') return '已录取'
  if (s === 'pending') return '待审批'
  return '未录取'
}

async function loadCourses() {
  try {
    const r: any = await http.get(`/courses/teacher/${auth.user?.id}`)
    courses.value = r?.data || []
  } catch {}
}

async function enterDetail(course: any) {
  currentCourse.value = course
  selected.value = new Set()
  try {
    const [eR, wR]: any[] = await Promise.all([
      http.get(`/enrollments/course/${course.id}`),
      http.get(`/waitlist/course/${course.id}`),
    ])
    enrollments.value = eR?.data || []
    waitlist.value = wR?.data || []
    // 默认勾选已录取的
    const set = new Set<number>()
    enrollments.value.forEach((e: any) => { if (e.status === 'approved') set.add(e.studentId) })
    selected.value = set
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  }
}

function toggleOne(studentId: number) {
  const set = new Set(selected.value)
  if (set.has(studentId)) set.delete(studentId)
  else set.add(studentId)
  selected.value = set
}

function autoByPoints() {
  const capacity = currentCourse.value.capacity
  // 规则：已录取保留 → 剩余名额按积分降序（列表已排好序）自动预选
  const set = new Set<number>()
  let need = capacity
  // 先保留已录取
  enrollments.value.forEach((e: any) => {
    if (e.status === 'approved' && need > 0) { set.add(e.studentId); need-- }
  })
  // 再按列表顺序（积分降序）预选
  enrollments.value.forEach((e: any) => {
    if (need <= 0) return
    if (set.has(e.studentId)) return
    if (e.status === 'rejected') return  // 跳过明确未录取的
    set.add(e.studentId); need--
  })
  selected.value = set
  ElMessage.info('已按积分自动预选至容量上限，积分相同处可手动调整')
}

async function submitApprove() {
  try { submitting.value = true } catch {}
  try {
    await ElMessage.info('正在提交…') // 占位
  } catch {}
  submitting.value = true
  try {
    await http.post('/enrollments/approve', {
      courseId: currentCourse.value.id,
      studentIds: [...selected.value],
    })
    ElMessage.success('审批结果已提交')
    // 刷新
    await enterDetail(currentCourse.value)
    await loadCourses()
  } catch (e: any) {
    ElMessage.error(e?.message || '提交失败')
  } finally {
    submitting.value = false
  }
}

onMounted(loadCourses)
</script>

<style scoped>
.page-wrap { padding: 4px 0; }
.empty-state { text-align: center; padding: 40px 20px; color: var(--text-light); }
.empty-icon { font-size: 40px; opacity: 0.3; margin-bottom: 8px; }
.approve-toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; padding: 10px 14px; background: var(--card-bg); border: 1px solid var(--border); border-radius: 8px; font-size: 13px; }
.approve-count b { color: var(--primary); }
.approve-student-row { display: flex; align-items: center; gap: 12px; padding: 10px 14px; border: 1px solid var(--border); border-radius: 8px; margin-bottom: 8px; background: var(--card-bg); cursor: pointer; transition: all .15s; }
.approve-student-row.selected { border-color: var(--primary); background: rgba(59,130,246,0.08); }
.approve-student-row input[type="checkbox"] { width: 16px; height: 16px; cursor: pointer; }
.stu-info { flex: 1; }
.stu-name { font-weight: 600; font-size: 14px; }
.stu-meta { font-size: 12px; color: var(--text-light); margin-top: 2px; }
.stu-points { font-weight: 700; color: #f59e0b; font-size: 13px; }
.status-tag { font-size: 11px; padding: 1px 8px; border-radius: 999px; }
.status-approved { background: rgba(16,185,129,0.15); color: #10b981; }
.status-pending { background: rgba(245,158,11,0.15); color: #f59e0b; }
.status-rejected { background: rgba(239,68,68,0.15); color: #ef4444; }
</style>
