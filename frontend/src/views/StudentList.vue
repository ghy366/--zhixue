<template>
  <div class="page-wrap stats-page liquid">
    <div class="stats-grid">
      <div class="stat-card"><div class="stat-icon green" v-html="svgIcon('check-circle')" /><div><div class="stat-value">{{ approvedCount }}</div><div class="stat-label">已录取</div></div></div>
      <div class="stat-card"><div class="stat-icon amber" v-html="svgIcon('hourglass')" /><div><div class="stat-value">{{ pendingCount }}</div><div class="stat-label">待审批</div></div></div>
      <div class="stat-card"><div class="stat-icon blue" v-html="svgIcon('users')" /><div><div class="stat-value">{{ totalCount }}</div><div class="stat-label">总报名</div></div></div>
    </div>

    <!-- 课程选择器 -->
    <div class="section">
      <div class="section-header">
        <div class="section-title">选择课程</div>
        <div class="tab-group">
          <button v-for="c in courses" :key="c.id" class="tab-btn"
            :class="{active: selectedCourse?.id === c.id}" @click="selectCourse(c)">
            {{ c.name }}
          </button>
        </div>
      </div>

      <div v-if="selectedCourse && currentStudents.length">
        <div class="data-table">
          <table>
            <thead>
              <tr><th>学号</th><th>姓名</th><th>专业</th><th>班级</th><th>状态</th><th>操作</th></tr>
            </thead>
            <tbody>
              <tr v-for="s in currentStudents" :key="s.enrollmentId">
                <td>{{ s.studentNo }}</td>
                <td>{{ s.studentName }}</td>
                <td>{{ s.studentMajor || '-' }}</td>
                <td>{{ s.studentClass || '-' }}</td>
                <td>
                  <span class="status-tag" :class="statusCls(s.status)">{{ statusText(s.status) }}</span>
                </td>
                <td>
                  <button v-if="s.status === 'approved'" class="btn btn-primary btn-sm" @click="goGrade(s)">录入成绩</button>
                  <span v-else class="btn btn-ghost btn-sm" style="cursor:not-allowed;color:var(--text-light)">待审批</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
      <div v-else class="empty-state">
        <div class="empty-icon" v-html="svgIcon('users')" />
        <div class="empty-title">{{ selectedCourse ? '该课程暂无选课学生' : '请选择一门课程' }}</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import http from '@/api'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`

const courses = ref<any[]>([])
const selectedCourse = ref<any>(null)
const currentStudents = ref<any[]>([])

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

const approvedCount = computed(() => currentStudents.value.filter(s => s.status === 'approved').length)
const pendingCount = computed(() => currentStudents.value.filter(s => s.status !== 'approved').length)
const totalCount = computed(() => currentStudents.value.length)

async function loadCourses() {
  try {
    const r: any = await http.get(`/courses/teacher/${auth.user?.id}`)
    courses.value = r?.data || []
    if (courses.value.length) selectCourse(courses.value[0])
  } catch {}
}

async function selectCourse(c: any) {
  selectedCourse.value = c
  try {
    const r: any = await http.get(`/enrollments/course/${c.id}`)
    currentStudents.value = r?.data || []
  } catch { currentStudents.value = [] }
}

function goGrade(s: any) {
  router.push({ name: 'GradeMgr', query: { courseId: selectedCourse.value.id } })
}

onMounted(loadCourses)
</script>

<style scoped>
.page-wrap { padding: 4px 0; }
.tab-group { display: flex; gap: 6px; flex-wrap: wrap; }
.tab-btn { padding: 6px 14px; border-radius: 999px; border: 1px solid var(--border); background: transparent; color: var(--text-light); font-size: 13px; cursor: pointer; transition: all .15s; }
.tab-btn:hover { color: var(--text); }
.tab-btn.active { background: var(--primary); color: #fff; border-color: var(--primary); }
.status-tag { font-size: 11px; padding: 1px 8px; border-radius: 999px; }
.status-approved { background: rgba(16,185,129,0.15); color: #10b981; }
.status-pending { background: rgba(245,158,11,0.15); color: #f59e0b; }
.status-rejected { background: rgba(239,68,68,0.15); color: #ef4444; }
.empty-state { text-align: center; padding: 60px 20px; color: var(--text-light); }
.empty-icon { font-size: 48px; opacity: 0.3; margin-bottom: 10px; }
</style>
