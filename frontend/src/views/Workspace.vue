<template>
  <div class="ws-page stats-page liquid">
    <!-- 空状态 -->
    <div v-if="emptyState" class="empty-card">{{ emptyState }}</div>

    <!-- 教师端：左侧学生列表 + 右侧详情 -->
    <template v-else-if="isTeacher">
      <div style="display:flex;gap:16px">
        <div style="width:260px;flex-shrink:0">
          <div class="section-title">我的学生（{{ teacherStudents.length }}）</div>
          <div id="ws-tea-students">
            <div v-for="s in teacherStudents" :key="s.advisorId"
                 class="ws-teacher-stu-card"
                 :class="{ active: s.advisorId === selectedAdvisorId }"
                 @click="selectStudent(s.advisorId)">
              <div style="flex-shrink:0">
                <div style="width:40px;height:40px;border-radius:50%;background:linear-gradient(135deg,#6366f1,#a855f7);display:flex;align-items:center;justify-content:center;color:#fff;font-weight:600">{{ s.student.name?.charAt(0) }}</div>
              </div>
              <div style="flex:1;min-width:0">
                <div style="font-weight:600;font-size:14px">{{ s.student.name }}</div>
                <div style="font-size:11px;color:var(--text-light);margin-top:2px">{{ s.student.college || '' }} · {{ s.student.major || '' }}</div>
                <div v-if="s.topic" style="font-size:11px;color:var(--primary);margin-top:3px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">🎯 {{ s.topic }}</div>
              </div>
            </div>
          </div>
        </div>
        <div style="flex:1;min-width:0">
          <div v-if="!wsDetail && loading" style="color:var(--text-light);text-align:center;padding:40px">加载中...</div>
          <WorkspaceDetail v-else-if="wsDetail" :ws="wsDetail" role="teacher" :advisor-id="selectedAdvisorId" />
        </div>
      </div>
    </template>

    <!-- 学生端：直接渲染详情 -->
    <template v-else-if="isStudent">
      <div v-if="!wsDetail && loading" style="color:var(--text-light);text-align:center;padding:40px">加载中...</div>
      <WorkspaceDetail v-else-if="wsDetail" :ws="wsDetail" role="student" :advisor-id="selectedAdvisorId" />
    </template>

    <!-- 管理员 -->
    <template v-else>
      <div class="empty-card">管理员暂未开放工作台</div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import http from '@/api'
import WorkspaceDetail from './WorkspaceDetail.vue'

const auth = useAuthStore()
const role = computed(() => auth.user?.role || 'student')
const isStudent = computed(() => role.value === 'student')
const isTeacher = computed(() => role.value === 'teacher')

const teacherStudents = ref<any[]>([])
const wsDetail = ref<any>(null)
const loading = ref(true)
const selectedAdvisorId = ref<number | null>(null)

const emptyState = computed(() => {
  if (loading.value) return null
  if (isStudent.value && !wsDetail.value) return '暂未确认导师，先去「毕业设计导师」页面选择吧 👆'
  if (isTeacher.value && teacherStudents.value.length === 0) return '暂无已确认的学生 👀'
  return null
})

function selectStudent(id: number) {
  selectedAdvisorId.value = id
  loadDetail(id)
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
      if (list.length > 0) {
        selectedAdvisorId.value = list[0].advisorId
        await loadDetail(list[0].advisorId)
      }
    } else if (isTeacher.value) {
      const r: any = await http.get('/workspace/teacher/students')
      teacherStudents.value = r?.data || []
      if (teacherStudents.value.length > 0) {
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
})
</script>
