<template>
  <div>
    <div class="admin-card">
      <h3>添加课程</h3>
      <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:12px;margin-bottom:12px">
        <input v-model="f.name" placeholder="课程名称" style="padding:10px;border:1.5px solid var(--border);border-radius:8px">
        <select v-model="f.teacherId" style="padding:10px;border:1.5px solid var(--border);border-radius:8px">
          <option v-for="t in teachers" :key="t.id" :value="t.id">{{ t.name }}</option>
        </select>
        <select v-model="f.category" style="padding:10px;border:1.5px solid var(--border);border-radius:8px">
          <option v-for="c in cats" :key="c" :value="c">{{ c }}</option>
        </select>
        <input v-model="f.credit" type="number" min="1" max="10" placeholder="学分" style="padding:10px;border:1.5px solid var(--border);border-radius:8px">
        <input v-model="f.capacity" type="number" min="1" placeholder="容量" style="padding:10px;border:1.5px solid var(--border);border-radius:8px">
        <input v-model="f.time" placeholder="时间 如 周一 08:00-09:40" style="padding:10px;border:1.5px solid var(--border);border-radius:8px">
        <input v-model="f.location" placeholder="地点" style="padding:10px;border:1.5px solid var(--border);border-radius:8px">
        <select v-model="f.campus" style="padding:10px;border:1.5px solid var(--border);border-radius:8px"><option>主校区</option><option>东校区</option><option>西校区</option></select>
        <input v-model="f.examTime" placeholder="考试时间(留空=按上课时间)" style="padding:10px;border:1.5px solid var(--border);border-radius:8px">
      </div>
      <button class="btn btn-primary" @click="add">+ 添加课程</button>
    </div>

    <div style="display:flex;gap:10px;margin-bottom:14px;flex-wrap:wrap">
      <button class="btn btn-outline" @click="downloadFile('/api/data/export/courses', 'courses.csv')">导出课程</button>
      <button class="btn btn-outline" @click="pickImport('courses')">导入课程</button>
      <button class="btn btn-ghost btn-sm" @click="downloadFile('/api/data/template/courses', 'courses_template.csv')">下载模板</button>
    </div>

    <div v-if="!courses.length" class="empty-state"><div class="empty-icon"><Icon name="book" /></div><p>暂无课程</p></div>
    <div v-else class="data-table">
      <table>
        <thead><tr><th>课程</th><th>教师</th><th>类别</th><th>学分</th><th>容量</th><th>校区</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="c in courses" :key="c.id">
            <td><b>{{ c.name }}</b></td><td>{{ c.teacherName }}</td>
            <td><span class="course-cat" :class="'cat-' + c.category">{{ c.category }}</span></td>
            <td>{{ c.credit }}</td><td>{{ c.capacity }}</td><td>{{ c.campus || '主校区' }}</td>
            <td><button class="btn btn-danger btn-sm" @click="del(c.id)">删除</button></td>
          </tr>
        </tbody>
      </table>
    </div>
    <input ref="importInput" type="file" accept=".csv,text/csv" style="display:none" @change="onImport">
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import Icon from '@/components/Icon.vue'
import { api } from '@/api/client'
import { downloadFile, importCsv } from '@/utils/file'

const cats = ['公共必修', '专业必修', '专业选修', '通识选修', '实践课程']
const courses = ref([])
const teachers = ref([])
const importInput = ref(null)
const importType = ref('')
const f = ref({ name: '', teacherId: '', category: '公共必修', credit: 3, capacity: 60, time: '周一 08:00-09:40', location: '待定', campus: '主校区', examTime: '' })

async function load() {
  const [cR, tR] = await Promise.all([api('GET', '/courses?category=全部'), api('GET', '/courses/teachers')])
  courses.value = cR.success ? cR.data : []
  teachers.value = tR.success ? tR.data : []
  if (teachers.value.length && !f.value.teacherId) f.value.teacherId = teachers.value[0].id
}
async function add() {
  if (!f.value.name.trim()) { alert('请输入课程名'); return }
  const r = await api('POST', '/courses', {
    name: f.value.name, teacherId: parseInt(f.value.teacherId), category: f.value.category,
    credit: parseInt(f.value.credit) || 3, capacity: parseInt(f.value.capacity) || 60,
    time: f.value.time, location: f.value.location, campus: f.value.campus,
    examTime: f.value.examTime || null, intro: '管理员添加的课程。',
  })
  alert(r.message); if (r.success) load()
}
async function del(id) { if (!confirm('确定删除该课程？')) return; const r = await api('DELETE', '/courses/' + id); alert(r.message); if (r.success) load() }
function pickImport(type) { importType.value = type; importInput.value.click() }
async function onImport(e) {
  const file = e.target.files[0]; if (!file) return
  const r = await importCsv(importType.value, file)
  const d = r.data || {}
  alert((r.success ? '导入完成：' : '导入失败：') + (r.message || '') + `\n新增 ${d.created || 0} · 更新 ${d.updated || 0} · 跳过 ${d.skipped || 0}`)
  if (r.success) load()
  e.target.value = ''
}
onMounted(load)
</script>
