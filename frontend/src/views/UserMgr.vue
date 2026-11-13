<template>
  <div class="page-wrap stats-page liquid">
    <div class="section">
      <div class="section-header">
        <div class="section-title" v-html="svgIcon('users') + ' ' + t('用户管理（共 {n} 人）', { n: total })"></div>
        <div style="display:flex;gap:8px;flex-wrap:wrap;justify-content:flex-end">
          <el-input v-model="kw" :placeholder="t('搜索学号/姓名')" size="default" clearable style="width:200px" />
          <el-select v-model="roleFilter" :placeholder="t('全部角色')" clearable size="default" style="width:120px">
            <el-option :label="t('学生')" value="student" /><el-option :label="t('教师')" value="teacher" /><el-option :label="t('管理员')" value="admin" />
          </el-select>
          <el-select v-model="collegeFilter" :placeholder="t('全部学院')" clearable filterable size="default" style="width:150px">
            <el-option v-for="c in collegeOpts" :key="c" :label="c" :value="c" />
          </el-select>
          <el-select v-model="majorFilter" :placeholder="t('全部专业')" clearable filterable size="default" style="width:150px" :disabled="!collegeFilter">
            <el-option v-for="m in majorOpts" :key="m" :label="m" :value="m" />
          </el-select>
          <el-select v-model="gradeFilter" :placeholder="t('全部年级')" clearable size="default" style="width:120px">
            <el-option v-for="g in gradeOpts" :key="g" :label="t('{n} 级', { n: g })" :value="g" />
          </el-select>
          <button class="btn btn-primary btn-sm" @click="openDialog(null)">{{ t('+ 新增用户') }}</button>
        </div>
      </div>
      <div class="data-table">
        <table>
          <thead><tr><th>{{ t('学号/工号') }}</th><th>{{ t('姓名') }}</th><th>{{ t('角色') }}</th><th>{{ t('年级') }}</th><th>{{ t('学院') }}</th><th>{{ t('专业') }}</th><th>{{ t('操作') }}</th></tr></thead>
          <tbody>
            <tr v-if="loading && !paged.length"><td colspan="7" style="text-align:center;padding:28px;color:var(--text-light)">{{ t('加载中…') }}</td></tr>
            <tr v-else-if="loadError && !paged.length"><td colspan="7" style="text-align:center;padding:28px;color:var(--danger)">{{ loadError }} · <button class="btn btn-outline btn-sm" @click="load">{{ t('重试') }}</button></td></tr>
            <tr v-for="u in paged" :key="u.id">
              <td><b>{{ u.no }}</b></td>
              <td>{{ u.name }}</td>
              <td><span class="status-tag" :class="roleTag(u.role)">{{ roleLabel(u.role) }}</span></td>
              <td><span v-if="u.role === 'student' && u.grade" class="grade-tag">{{ t('{n} 级', { n: u.grade }) }}</span><span v-else>—</span></td>
              <td>{{ u.college || '—' }}</td>
              <td>{{ u.major || '—' }}</td>
              <td>
                <button class="btn btn-outline btn-sm" @click="openDialog(u)">{{ t('编辑') }}</button>
                <button class="btn btn-outline btn-sm" @click="resetPwd(u)">{{ t('重置密码') }}</button>
                <button class="btn btn-ghost-danger btn-sm" @click="del(u)">{{ t('删除') }}</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="total > pageSize" class="pg-bar">
        <span class="pg-info">{{ t('共 {n} 条 · 第 {p} / {total} 页', { n: total, p: page, total: totalPages }) }}</span>
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
    <el-dialog v-model="dlg" :title="form.id ? t('编辑用户') : t('新增用户')" width="480px">
      <el-form :model="form" label-position="top">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item :label="t('学号/工号')"><el-input v-model="form.no" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('姓名')"><el-input v-model="form.name" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('角色')">
            <el-select v-model="form.role"><el-option :label="t('学生')" value="student" /><el-option :label="t('教师')" value="teacher" /><el-option :label="t('管理员')" value="admin" /></el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('初始密码')"><el-input v-model="form.password" type="password" show-password :placeholder="t('新增时填写')" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('学院')">
            <!-- 学院是课题库/导师隔离的依据：统一从 COLLEGES 取值，filterable 兼容历史自定义学院 -->
            <el-select v-model="form.college" filterable allow-create default-first-option :placeholder="t('选择或输入学院')">
              <el-option v-for="c in COLLEGES" :key="c" :label="c" :value="c" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('专业')"><el-input v-model="form.major" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('年级（入学年份，仅学生）')">
            <el-input-number v-if="form.role === 'student'" v-model="form.grade" :min="2000" :max="2100" :controls="false" :placeholder="t('如 2023')" style="width:100%" />
            <el-input v-else disabled :placeholder="t('— 非学生无需年级 —')" />
          </el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <button class="btn btn-ghost btn-sm" @click="dlg = false">{{ t('取消') }}</button>
        <button class="btn btn-primary btn-sm" @click="save">{{ t('保存') }}</button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api'
import { encryptPassword } from '@/utils/rsa'
import { COLLEGES } from '@/utils/colleges'
import { t } from '@/i18n'
const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`
const users = ref<any[]>([])
const total = ref(0)
const kw = ref('')
const roleFilter = ref<string | null>(null)
const dlg = ref(false)
// —— 学院/专业/年级筛选:选项来自 register-options(专业字典 ∪ 学生档案),学院→专业级联 ——
const collegeFilter = ref<string | null>(null)
const majorFilter = ref<string | null>(null)
const gradeFilter = ref<number | null>(null)
const optLoaded = ref(false)
const optColleges = ref<string[]>([])
const optMajorsByCollege = ref<Record<string, string[]>>({})
const optGrades = ref<number[]>([])
const collegeOpts = computed(() => (optLoaded.value && optColleges.value.length ? optColleges.value : COLLEGES))
const majorOpts = computed(() => {
  if (collegeFilter.value) return optMajorsByCollege.value[collegeFilter.value] || []
  // 未选学院:聚合全部学院的专业并去重排序
  const all = new Set<string>()
  for (const list of Object.values(optMajorsByCollege.value)) list.forEach(m => all.add(m))
  return [...all].sort((a, b) => a.localeCompare(b, 'zh'))
})
const gradeOpts = computed(() => (optGrades.value.length ? optGrades.value : Array.from({ length: 6 }, (_, i) => 2021 + i)))
async function loadFilterOptions() {
  try {
    const r: any = await http.get('/auth/register-options')
    if (r?.success && r.data) {
      optColleges.value = r.data.colleges || []
      optMajorsByCollege.value = r.data.majors || {}
      optGrades.value = r.data.grades || []
      optLoaded.value = true
    }
  } catch { /* 选项加载失败时用兜底常量,不影响主列表 */ }
}
const loading = ref(false)
const loadError = ref('')
let reqSeq = 0
const form = reactive<any>({ id: null, no: '', name: '', role: 'student', password: '123456', college: '', major: '', grade: null })
// —— 服务端分页：筛选（角色/关键词）传后端，当前页数据 + 总数由接口返回 ——
const pageSize = 10
const page = ref(1)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
const paged = computed(() => users.value)
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
let kwTimer: any = null
watch(kw, () => {
  clearTimeout(kwTimer)
  kwTimer = setTimeout(() => { page.value === 1 ? load() : (page.value = 1) }, 300)
})
watch(roleFilter, () => { page.value === 1 ? load() : (page.value = 1) })
watch(collegeFilter, () => { if (majorFilter.value) majorFilter.value = null; else { page.value === 1 ? load() : (page.value = 1) } })
watch([majorFilter, gradeFilter], () => { page.value === 1 ? load() : (page.value = 1) })
watch(page, load)
watch(totalPages, tp => { if (page.value > tp) page.value = tp })
const roleLabel = (r: string) => ({ student: t('学生'), teacher: t('教师'), admin: t('管理员') } as any)[r] || r
const roleTag = (r: string) => ({ student: 'status-approved', teacher: 'status-pending', admin: 'status-warning' } as any)[r] || 'status-approved'
async function load() {
  const seq = ++reqSeq
  loading.value = true
  loadError.value = ''
  try {
    const params: any = { page: page.value, size: pageSize }
    if (roleFilter.value) params.role = roleFilter.value
    if (collegeFilter.value) params.college = collegeFilter.value
    if (majorFilter.value) params.major = majorFilter.value
    if (gradeFilter.value) params.grade = gradeFilter.value
    if (kw.value.trim()) params.keyword = kw.value.trim()
    const r: any = await http.get('/admin/users', { params })
    if (seq !== reqSeq) return   // 丢弃过期响应，避免旧请求覆盖新结果
    const d = r?.data
    if (Array.isArray(d)) {
      // 旧后端（未重启）：不支持 keyword/role 参数 → 本地过滤 + 切片分页兼容
      let list: any[] = d
      if (roleFilter.value) list = list.filter(u => u.role === roleFilter.value)
      if (kw.value.trim()) {
        const k = kw.value.trim().toLowerCase()
        list = list.filter(u => ((u.no || '') + (u.name || '')).toLowerCase().includes(k))
      }
      users.value = list.slice((page.value - 1) * pageSize, page.value * pageSize)
      total.value = list.length
    } else {
      users.value = d?.items || []
      total.value = d?.total || 0
    }
  } catch (e: any) {
    if (seq !== reqSeq) return
    loadError.value = e?.message || t('加载失败')
    ElMessage.error(loadError.value)
  } finally {
    if (seq === reqSeq) loading.value = false
  }
}
function openDialog(u: any) { Object.assign(form, u || { id: null, no: '', name: '', role: 'student', password: '123456', college: '', major: '', grade: null }); dlg.value = true }
async function save() {
  try {
    const payload = { ...form, password: form.password ? await encryptPassword(form.password) : '' }
    if (form.id) await http.put(`/admin/users/${form.id}`, payload); else await http.post('/admin/users', payload)
    ElMessage.success(t('保存成功')); dlg.value = false; load()
  }
  catch (e: any) { ElMessage.error(e?.message || t('保存失败')) }
}
async function del(u: any) {
  try { await ElMessageBox.confirm(t('确定删除用户「{name}」？', { name: u.name }), t('提示'), { type: 'warning', confirmButtonText: t('确定'), cancelButtonText: t('取消') }) } catch { return }
  try { await http.delete(`/admin/users/${u.id}`); ElMessage.success(t('已删除')); load() } catch (e: any) { ElMessage.error(e?.message || t('删除失败')) }
}
async function resetPwd(u: any) {
  try { await ElMessageBox.confirm(t('确定重置「{name}」的密码为 123456？', { name: u.name }), t('提示'), { type: 'warning', confirmButtonText: t('确定'), cancelButtonText: t('取消') }) } catch { return }
  try { await http.put(`/admin/users/${u.id}/reset-password`, { newPassword: await encryptPassword('123456') }); ElMessage.success(t('已重置')) } catch (e: any) { ElMessage.error(e?.message || t('重置失败')) }
}
onMounted(() => { load(); loadFilterOptions() })
</script>

<style scoped>
.page-wrap { padding: 4px 0; }
.grade-tag { display: inline-block; padding: 2px 10px; border-radius: 999px; font-size: 12px; font-weight: 600; background: var(--primary-bg); color: var(--primary); white-space: nowrap; }
</style>
