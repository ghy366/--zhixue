<template>
  <div class="page-wrap stats-page liquid">
    <div class="section">
      <div class="section-header">
        <div class="section-title" v-html="svgIcon('users') + ' 用户管理（共 ' + users.length + ' 人）'"></div>
        <div style="display:flex;gap:8px">
          <el-input v-model="kw" placeholder="搜索学号/姓名" size="default" clearable style="width:200px" />
          <el-select v-model="roleFilter" placeholder="全部角色" clearable size="default" style="width:130px">
            <el-option label="学生" value="student" /><el-option label="教师" value="teacher" /><el-option label="管理员" value="admin" />
          </el-select>
          <button class="btn btn-primary btn-sm" @click="openDialog(null)">+ 新增用户</button>
        </div>
      </div>
      <div class="data-table">
        <table>
          <thead><tr><th>学号/工号</th><th>姓名</th><th>角色</th><th>年级</th><th>学院</th><th>专业</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="u in paged" :key="u.id">
              <td><b>{{ u.no }}</b></td>
              <td>{{ u.name }}</td>
              <td><span class="status-tag" :class="roleTag(u.role)">{{ roleLabel(u.role) }}</span></td>
              <td><span v-if="u.role === 'student' && u.grade" class="grade-tag">{{ u.grade }} 级</span><span v-else>—</span></td>
              <td>{{ u.college || '—' }}</td>
              <td>{{ u.major || '—' }}</td>
              <td>
                <button class="btn btn-outline btn-sm" @click="openDialog(u)">编辑</button>
                <button class="btn btn-outline btn-sm" @click="resetPwd(u)">重置密码</button>
                <button class="btn btn-danger btn-sm" @click="del(u)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="filtered.length > pageSize" class="pg-bar">
        <span class="pg-info">共 {{ filtered.length }} 条 · 第 {{ page }} / {{ totalPages }} 页</span>
        <div class="pg-btns">
          <button class="pg-btn" :disabled="page <= 1" @click="page--">‹</button>
          <template v-for="(p, i) in pageItems" :key="i">
            <span v-if="p === '…'" class="pg-dots">…</span>
            <button v-else class="pg-btn" :class="{ active: p === page }" @click="page = p">{{ p }}</button>
          </template>
          <button class="pg-btn" :disabled="page >= totalPages" @click="page++">›</button>
        </div>
      </div>
    </div>
    <el-dialog v-model="dlg" :title="form.id ? '编辑用户' : '新增用户'" width="480px">
      <el-form :model="form" label-position="top">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="学号/工号"><el-input v-model="form.no" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="姓名"><el-input v-model="form.name" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="角色">
            <el-select v-model="form.role"><el-option label="学生" value="student" /><el-option label="教师" value="teacher" /><el-option label="管理员" value="admin" /></el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="初始密码"><el-input v-model="form.password" type="password" show-password placeholder="新增时填写" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="学院"><el-input v-model="form.college" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="专业"><el-input v-model="form.major" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="年级（入学年份，仅学生）">
            <el-input-number v-if="form.role === 'student'" v-model="form.grade" :min="2000" :max="2100" :controls="false" placeholder="如 2023" style="width:100%" />
            <el-input v-else disabled placeholder="— 非学生无需年级 —" />
          </el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <button class="btn btn-ghost btn-sm" @click="dlg = false">取消</button>
        <button class="btn btn-primary btn-sm" @click="save">保存</button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api'
const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`
const users = ref<any[]>([])
const kw = ref('')
const roleFilter = ref<string | null>(null)
const dlg = ref(false)
const form = reactive<any>({ id: null, no: '', name: '', role: 'student', password: '123456', college: '', major: '', grade: null })
// —— 分页 ——
const pageSize = 10
const page = ref(1)
const filtered = computed(() => {
  let list = users.value
  if (roleFilter.value) list = list.filter(u => u.role === roleFilter.value)
  if (kw.value) { const k = kw.value.toLowerCase(); list = list.filter(u => (u.no + u.name).toLowerCase().includes(k)) }
  return list
})
const totalPages = computed(() => Math.max(1, Math.ceil(filtered.value.length / pageSize)))
const paged = computed(() => filtered.value.slice((page.value - 1) * pageSize, page.value * pageSize))
const pageItems = computed<(number | '…')[]>(() => {
  const t = totalPages.value, c = page.value
  if (t <= 7) return Array.from({ length: t }, (_, i) => i + 1)
  const set = new Set([1, 2, t - 1, t, c - 1, c, c + 1].filter(p => p >= 1 && p <= t))
  const arr = [...set].sort((a, b) => a - b)
  const out: (number | '…')[] = []
  let prev = 0
  for (const p of arr) { if (p - prev > 1) out.push('…'); out.push(p); prev = p }
  return out
})
watch([kw, roleFilter], () => { page.value = 1 })
watch(totalPages, t => { if (page.value > t) page.value = t })
const roleLabel = (r: string) => ({ student: '学生', teacher: '教师', admin: '管理员' } as any)[r] || r
const roleTag = (r: string) => ({ student: 'status-approved', teacher: 'status-pending', admin: 'status-warning' } as any)[r] || 'status-approved'
async function load() { try { const r: any = await http.get('/admin/users'); users.value = r?.data || [] } catch {} }
function openDialog(u: any) { Object.assign(form, u || { id: null, no: '', name: '', role: 'student', password: '123456', college: '', major: '', grade: null }); dlg.value = true }
async function save() {
  try { if (form.id) await http.put(`/admin/users/${form.id}`, form); else await http.post('/admin/users', form); ElMessage.success('保存成功'); dlg.value = false; load() }
  catch (e: any) { ElMessage.error(e?.message || '保存失败') }
}
async function del(u: any) {
  try { await ElMessageBox.confirm(`确定删除用户「${u.name}」？`, '提示', { type: 'warning' }) } catch { return }
  try { await http.delete(`/admin/users/${u.id}`); ElMessage.success('已删除'); load() } catch (e: any) { ElMessage.error(e?.message || '删除失败') }
}
async function resetPwd(u: any) {
  try { await ElMessageBox.confirm(`确定重置「${u.name}」的密码为 123456？`, '提示', { type: 'warning' }) } catch { return }
  try { await http.put(`/admin/users/${u.id}/reset-password`, { password: '123456' }); ElMessage.success('已重置') } catch (e: any) { ElMessage.error(e?.message || '重置失败') }
}
onMounted(load)
</script>

<style scoped>
.page-wrap { padding: 4px 0; }
.grade-tag { display: inline-block; padding: 2px 10px; border-radius: 999px; font-size: 12px; font-weight: 600; background: var(--primary-bg); color: var(--primary); white-space: nowrap; }
</style>
