<template>
  <div>
    <div style="display:flex;gap:10px;margin-bottom:14px;flex-wrap:wrap">
      <select v-model="filterRole" style="padding:8px 12px;border:1.5px solid var(--border);border-radius:8px">
        <option value="">全部角色</option><option value="teacher">教师</option><option value="student">学生</option><option value="admin">管理员</option>
      </select>
      <input v-model="kw" type="text" placeholder="搜索姓名/账号" style="flex:1;min-width:180px;padding:8px 12px;border:1.5px solid var(--border);border-radius:8px">
      <button class="btn btn-primary" @click="showAdd = true">新增教师</button>
      <button class="btn btn-ghost" @click="showAddStu = true">新增学生</button>
      <button class="btn btn-danger btn-sm" @click="batchReset">批量重置密码(123456)</button>
      <span style="flex:1"></span>
      <button class="btn btn-outline" @click="downloadFile('/api/data/export/students', 'students.csv')">导出学生</button>
      <button class="btn btn-outline" @click="pickImport('students')">导入学生</button>
      <button class="btn btn-ghost btn-sm" @click="downloadFile('/api/data/template/students', 'students_template.csv')">下载模板</button>
    </div>

    <div v-if="!filtered.length" class="empty-state"><div class="empty-icon"><Icon name="users" /></div><p>暂无用户</p></div>
    <div v-else class="data-table">
      <table>
        <thead><tr><th>头像</th><th>姓名</th><th>账号</th><th>角色</th><th>学院</th><th>手机号</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="u in filtered" :key="u.id">
            <td style="font-size:20px">{{ avatarOf(u) }}</td>
            <td><b>{{ u.name }}</b></td><td>{{ u.no }}</td>
            <td><span class="status-tag" :class="u.role === 'student' ? 'status-approved' : u.role === 'teacher' ? 'status-pending' : ''">{{ ROLES[u.role] }}</span></td>
            <td>{{ u.college || '-' }}</td><td>{{ u.phone || '-' }}</td>
            <td>
              <button class="btn btn-primary btn-sm" @click="resetPwd(u)">重置密码</button>
              <button v-if="u.id !== me?.id" class="btn btn-danger btn-sm" @click="del(u)">删除</button>
              <span v-else style="color:#888;font-size:12px">(当前)</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div style="margin-top:10px;font-size:12px;color:var(--text-light)">
      共 {{ users.length }} 位用户 · 教师 {{ users.filter(u=>u.role==='teacher').length }} · 学生 {{ users.filter(u=>u.role==='student').length }} · 管理员 {{ users.filter(u=>u.role==='admin').length }}
    </div>
    <input ref="importInput" type="file" accept=".csv,text/csv" style="display:none" @change="onImport">

    <div v-if="showAdd || showAddStu" class="modal-mask" @click.self="closeAdd">
      <div class="modal">
        <h3>{{ showAddStu ? '新增学生' : '新增教师账号' }}</h3>
        <div class="form-group"><label>角色</label>
          <select v-model="nu.role"><option value="teacher">教师</option><option value="student">学生</option><option value="admin">管理员</option></select></div>
        <div class="form-group"><label>姓名</label><input v-model="nu.name"></div>
        <div class="form-group"><label>账号</label><input v-model="nu.no"></div>
        <div class="form-group"><label>密码</label><input v-model="nu.password" placeholder="默认 123456"></div>
        <div class="form-group"><label>学院</label><input v-model="nu.college"></div>
        <div class="form-group"><label>手机号</label><input v-model="nu.phone"></div>
        <div class="modal-actions">
          <button class="btn btn-ghost" @click="closeAdd">取消</button>
          <button class="btn btn-primary" @click="createUser">创建</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import Icon from '@/components/Icon.vue'
import { api, getUser } from '@/api/client'
import { downloadFile, importCsv } from '@/utils/file'

const ROLES = { student: '学生', teacher: '老师', admin: '管理员' }
const me = getUser()
const users = ref([])
const filterRole = ref('')
const kw = ref('')
const showAdd = ref(false)
const showAddStu = ref(false)
const importInput = ref(null)
const importType = ref('')
const nu = ref({ role: 'teacher', name: '', no: '', password: '123456', college: '', phone: '' })

const filtered = computed(() => users.value.filter(u => {
  const okRole = !filterRole.value || u.role === filterRole.value
  const k = kw.value.toLowerCase()
  const okKw = !k || u.name.toLowerCase().includes(k) || u.no.toLowerCase().includes(k)
  return okRole && okKw
}))
function avatarOf(u) { return u.avatar && !u.avatar.startsWith('data:') ? u.avatar : ({ student: '🎓', teacher: '👨‍🏫', admin: '👑' }[u.role] || '👤') }

async function load() { const r = await api('GET', '/admin/users'); users.value = r.success ? r.data : [] }
function closeAdd() { showAdd.value = false; showAddStu.value = false; nu.value = { role: 'teacher', name: '', no: '', password: '123456', college: '', phone: '' } }
async function createUser() {
  if (!nu.value.name || !nu.value.no) { alert('姓名和账号不能为空'); return }
  const r = await api('POST', '/admin/users', { ...nu.value, avatar: ({ student: '🎓', teacher: '👨‍🏫', admin: '👑' }[nu.value.role] || '👤') })
  alert(r.message); if (r.success) { closeAdd(); load() }
}
async function resetPwd(u) { if (!confirm(`将 "${u.no}" 的密码重置为 123456？`)) return; const r = await api('PUT', '/admin/users/' + u.id, { password: '123456' }); alert(r.success ? '密码已重置为 123456' : r.message) }
async function del(u) { if (!confirm(`确定删除用户 "${u.name}" 吗？此操作不可恢复。`)) return; const r = await api('DELETE', '/admin/users/' + u.id); alert(r.message); if (r.success) load() }
async function batchReset() {
  if (!confirm('将所有学生/教师的密码重置为 123456？')) return
  let ok = 0, fail = 0
  for (const u of users.value) {
    if (u.role !== 'admin') { const r = await api('PUT', '/admin/users/' + u.id, { password: '123456' }); r.success ? ok++ : fail++ }
  }
  alert(`重置完成：成功 ${ok}，失败 ${fail}`)
}
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
