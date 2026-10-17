<template>
  <div>
    <div class="page-hero">
      <div class="page-hero-icon"><Icon name="users" /></div>
      <div class="page-hero-text">
        <div class="page-hero-title">选课学生</div>
        <div class="page-hero-sub">选择课程查看报名学生名单，可直接录入成绩</div>
      </div>
      <div class="page-hero-count">{{ courses.length }}<span>门课程</span></div>
    </div>

    <div class="filter-bar select-bar">
      <div class="search-box select-box">
        <Icon name="book" />
        <select v-model="selCourse" @change="loadList">
          <option value="">请选择课程…</option>
          <option v-for="c in courses" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>
      </div>
    </div>

    <div v-if="!selCourse" class="empty-state">
      <div class="empty-icon"><Icon name="users" /></div>
      <p>在上方选择一门课程，即可查看选课学生名单</p>
    </div>
    <div v-else-if="!list.length" class="empty-state">
      <div class="empty-icon"><Icon name="users" /></div><p>暂无选课学生</p>
    </div>
    <div v-else class="data-table">
      <table>
        <thead><tr><th>学号</th><th>姓名</th><th>专业</th><th>年级</th><th>班级</th><th>联系电话</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="s in list" :key="s.studentId">
            <td>{{ s.studentNo }}</td><td>{{ s.studentName }}</td><td>{{ s.studentMajor || '' }}</td>
            <td>{{ s.grade || '-' }}</td><td>{{ s.studentClass || '-' }}</td><td>{{ s.studentPhone || '-' }}</td>
            <td>{{ s.status }}</td>
            <td><button class="btn btn-primary btn-sm" @click="openGrade(s)">录入成绩</button></td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 成绩录入弹窗 -->
    <div v-if="gradeStu" class="modal-mask" @click.self="gradeStu = null">
      <div class="modal">
        <h3>录入成绩</h3>
        <div class="modal-sub">学号：{{ gradeStu.studentNo }}　姓名：{{ gradeStu.studentName }}</div>
        <div class="form-group"><label>平时成绩</label><input type="number" min="0" max="100" v-model="gRegular"></div>
        <div class="form-group"><label>期末成绩</label><input type="number" min="0" max="100" v-model="gFinal"></div>
        <div class="form-group"><label>总评成绩（自动计算）</label><input readonly :value="gTotal"></div>
        <div class="modal-actions">
          <button class="btn btn-ghost" @click="gradeStu = null">取消</button>
          <button class="btn btn-primary" @click="saveGrade">确定</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import Icon from '@/components/Icon.vue'
import { api, getUser } from '@/api/client'

const user = getUser()
const courses = ref([])
const selCourse = ref('')
const list = ref([])
const gradeStu = ref(null)
const gRegular = ref('')
const gFinal = ref('')

const gTotal = computed(() => {
  const r = parseFloat(gRegular.value) || 0
  const f = parseFloat(gFinal.value) || 0
  return (r * 0.4 + f * 0.6).toFixed(1)
})
async function load() {
  const r = await api('GET', `/courses/teacher/${user.id}`)
  courses.value = r.success ? r.data : []
}
async function loadList() {
  if (!selCourse.value) { list.value = []; return }
  const r = await api('GET', `/enrollments/course/${selCourse.value}`)
  list.value = r.success ? r.data.filter(s => s.status === 'approved' || s.status === 'pending') : []
}
function openGrade(s) { gradeStu.value = s; gRegular.value = ''; gFinal.value = '' }
async function saveGrade() {
  const r = await api('POST', '/grades', {
    studentId: gradeStu.value.studentId, courseId: gradeStu.value.courseId || selCourse.value,
    regularScore: parseFloat(gRegular.value) || null, finalScore: parseFloat(gFinal.value) || null,
  })
  alert(r.message)
  if (r.success) { gradeStu.value = null; await loadList() }
}
onMounted(load)
</script>
