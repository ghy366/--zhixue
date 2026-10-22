<template>
  <div class="page-wrap stats-page liquid">
    <div class="stats-grid">
      <div class="stat-card"><div class="stat-icon blue" v-html="svgIcon('book')" /><div><div class="stat-value">{{ courses.length }}</div><div class="stat-label">授课课程</div></div></div>
      <div class="stat-card"><div class="stat-icon green" v-html="svgIcon('check-circle')" /><div><div class="stat-value">{{ gradedCount }}</div><div class="stat-label">已批改</div></div></div>
      <div class="stat-card"><div class="stat-icon amber" v-html="svgIcon('hourglass')" /><div><div class="stat-value">{{ pendingCount }}</div><div class="stat-label">待批改</div></div></div>
    </div>
    <div class="section">
      <div class="section-header">
        <div class="section-title">选择课程</div>
        <div class="tab-group">
          <button v-for="c in courses" :key="c.id" class="tab-btn" :class="{active: selectedCourse?.id === c.id}" @click="selectCourse(c)">{{ c.name }}</button>
        </div>
      </div>
      <div v-if="selectedCourse">
        <div class="data-table">
          <table>
            <thead>
              <tr><th>学号</th><th>姓名</th><th>专业</th><th>平时成绩</th><th>期末成绩</th><th>总评</th><th>操作</th></tr>
            </thead>
            <tbody>
              <tr v-for="s in students" :key="s.studentId">
                <td>{{ s.studentNo }}</td>
                <td>{{ s.studentName }}</td>
                <td>{{ s.studentMajor }}</td>
                <td>
                  <el-input v-if="editingId === s.studentId" v-model.number="editRegular" size="small" style="width:80px" type="number" min="0" max="100" />
                  <span v-else>{{ fmt(s.regularScore) }}</span>
                </td>
                <td>
                  <el-input v-if="editingId === s.studentId" v-model.number="editFinal" size="small" style="width:80px" type="number" min="0" max="100" />
                  <span v-else>{{ fmt(s.finalScore) }}</span>
                </td>
                <td><b>{{ fmt(s.totalScore) }}</b></td>
                <td>
                  <template v-if="editingId === s.gradeId">
                    <button class="btn btn-success btn-sm" @click="saveGrade(s)">保存</button>
                    <button class="btn btn-ghost btn-sm" @click="editingId = null">取消</button>
                  </template>
                  <button v-else class="btn btn-outline btn-sm" @click="startEdit(s)">批改</button>
                </td>
              </tr>
              <tr v-if="!students.length"><td colspan="7" style="text-align:center;color:var(--text-light);padding:24px">该课程暂无学生</td></tr>
            </tbody>
          </table>
        </div>
      </div>
      <div v-else class="empty-state">
        <div class="empty-icon" v-html="svgIcon('edit')" />
        <div class="empty-title">请选择一门课程</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '@/api'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()
const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`

const courses = ref<any[]>([])
const students = ref<any[]>([])
const selectedCourse = ref<any>(null)
const editingId = ref<number | null>(null)
const editRegular = ref<number | null>(null)
const editFinal = ref<number | null>(null)

function fmt(v: any) { return v != null ? v : '—' }
const gradedCount = computed(() => students.value.filter(s => s.totalScore != null).length)
const pendingCount = computed(() => students.value.filter(s => s.totalScore == null).length)

async function loadCourses() {
  try {
    const r: any = await http.get(`/courses/teacher/${auth.user?.id}`)
    courses.value = r?.data || []
    if (courses.value.length && !selectedCourse.value) {
      // 支持从"选课学生"页带 courseId 跳转过来直接定位课程
      const q = Number(route.query.courseId)
      const target = courses.value.find(c => c.id === q)
      selectedCourse.value = target || courses.value[0]
      await loadStudents()
    }
  } catch {}
}
async function loadStudents() {
  if (!selectedCourse.value) return
  try {
    const r: any = await http.get(`/grades/course/${selectedCourse.value.id}`)
    students.value = r?.data || []
  } catch {}
}
function selectCourse(c: any) { selectedCourse.value = c; editingId.value = null; loadStudents() }
function startEdit(s: any) {
  editingId.value = s.studentId
  editRegular.value = s.regularScore ?? null
  editFinal.value = s.finalScore ?? null
}
async function saveGrade(s: any) {
  try {
    await http.post('/grades', {
      studentId: s.studentId,
      courseId: selectedCourse.value.id,
      regularScore: editRegular.value,
      finalScore: editFinal.value,
    })
    ElMessage.success('保存成功')
    editingId.value = null
    await loadStudents()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  }
}

onMounted(loadCourses)
</script>

<style scoped>
.page-wrap { padding: 4px 0; }
.tab-group { display: flex; gap: 6px; flex-wrap: wrap; }
.tab-btn { padding: 6px 14px; border-radius: 999px; border: 1px solid var(--border); background: transparent; color: var(--text-light); font-size: 13px; cursor: pointer; transition: all .15s; }
.tab-btn:hover { color: var(--text); }
.tab-btn.active { background: var(--primary); color: #fff; border-color: var(--primary); }
.empty-state { text-align: center; padding: 60px 20px; color: var(--text-light); }
.empty-icon { font-size: 48px; opacity: 0.3; margin-bottom: 10px; }
</style>
