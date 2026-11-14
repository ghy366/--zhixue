<template>
  <div class="page-wrap stats-page liquid">
    <div class="section">
      <div class="section-header">
        <div class="section-title" v-html="svgIcon('users') + ' ' + t('用户管理（共 {n} 人）', { n: counts.all ?? total })"></div>
        <div style="display:flex;gap:8px;flex-wrap:wrap;justify-content:flex-end">
          <el-input v-model="kw" :placeholder="t('搜索学号/姓名/班级/手机号')" size="default" clearable style="width:210px" />
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

      <div class="um-tabbar">
        <div class="um-tabs">
          <button v-for="tb in roleTabs" :key="tb.label" class="um-tab" :class="{ on: roleFilter === tb.value }" @click="roleFilter = tb.value">
            {{ tb.label }}<span class="um-tab-num">{{ tb.count }}</span>
          </button>
        </div>
        <div class="um-tools-right">
          <span class="um-per-label">{{ t('每页') }}</span>
          <select v-model.number="pageSize" class="um-per">
            <option v-for="n in [10, 20, 50, 100]" :key="n" :value="n">{{ n }}</option>
          </select>
          <span class="um-per-label">{{ t('条') }}</span>
        </div>
      </div>

      <div v-if="activeChips.length" class="um-chips">
        <span class="um-chip-label">{{ t('已选条件') }}</span>
        <button v-for="c in activeChips" :key="c.key" class="um-chip" @click="c.clear">{{ c.label }}<span class="um-x">×</span></button>
        <button class="um-clear" @click="clearFilters">{{ t('清空筛选') }}</button>
      </div>

      <div class="um-split">
        <aside class="um-tree">
          <div class="um-tree-h"><b>{{ t('组织结构') }}</b><span>{{ classOpts.length }} {{ t('个班') }}</span></div>
          <div class="um-tree-search">
            <el-input v-model="treeKw" :placeholder="t('搜索学院 / 专业 / 班级')" size="small" clearable />
          </div>
          <div class="um-tree-body">
            <button class="um-root" :class="{ on: !classFilter }" @click="selectClass(null)">
              {{ t('全部用户') }}<span class="um-n">{{ counts.all ?? total }}</span>
            </button>
            <div v-for="col in treeCols" :key="col.name" class="um-col">
              <button class="um-node" @click="toggleNode('c:' + col.name)">
                <span class="um-caret">{{ isOpen('c:' + col.name) ? '▾' : '▸' }}</span>{{ col.name }}<span class="um-n">{{ col.count }}</span>
              </button>
              <div v-if="isOpen('c:' + col.name)">
                <div v-for="maj in col.majors" :key="maj.name" class="um-maj">
                  <button class="um-node sub" @click="toggleNode('m:' + col.name + '/' + maj.name)">
                    <span class="um-caret">{{ isOpen('m:' + col.name + '/' + maj.name) ? '▾' : '▸' }}</span>{{ maj.name }}<span class="um-n">{{ maj.count }}</span>
                  </button>
                  <div v-if="isOpen('m:' + col.name + '/' + maj.name)">
                    <button v-for="cl in visibleClasses(maj, 'm:' + col.name + '/' + maj.name)" :key="cl.value"
                            class="um-leaf" :class="{ on: classFilter === cl.value }" @click="selectClass(cl.value)">
                      {{ cl.name }}<span class="um-n">{{ cl.count }}</span>
                    </button>
                    <button v-if="moreClasses(maj, 'm:' + col.name + '/' + maj.name)" class="um-more"
                            @click="expandMaj('m:' + col.name + '/' + maj.name)">
                      ＋ {{ t('展开其余 {n} 个班', { n: maj.classes.length - 6 }) }}
                    </button>
                  </div>
                </div>
              </div>
            </div>
            <div v-if="!treeCols.length" class="um-tree-empty">{{ t('没有匹配的组织节点') }}</div>
          </div>
        </aside>

        <section class="um-main">
          <div class="um-list-h">
            <div class="um-list-title"><span class="um-dot"></span><b>{{ scopeLabel }}</b><span class="um-scope-n">{{ total }} {{ t('人') }}</span></div>
            <div class="um-list-actions">
              <button class="btn btn-outline btn-sm" @click="downloadTemplate('students')">{{ t('模板') }}</button>
              <button class="btn btn-outline btn-sm" @click="pickAndImport('students', load)">{{ t('导入 Excel') }}</button>
              <button class="btn btn-outline btn-sm" @click="exportCurrent">{{ t('导出') }}</button>
              <button class="btn btn-sm" :class="batchMode ? 'btn-primary' : 'btn-outline'" @click="batchMode = !batchMode">{{ t('批量操作') }}</button>
            </div>
          </div>
          <div class="um-hint">{{ t('点击任意一行查看完整档案，可进行编辑 / 重置密码 / 删除') }}</div>

          <div class="data-table">
            <table>
              <thead><tr>
                <th v-if="batchMode" class="um-check-col"><input type="checkbox" :checked="allChecked" @change="toggleAll" /></th>
                <th>{{ t('学号/工号') }}</th><th>{{ t('姓名') }}</th><th>{{ t('角色') }}</th><th>{{ t('年级') }}</th><th>{{ t('学院') }}</th><th>{{ t('专业') }}</th><th>{{ t('操作') }}</th>
              </tr></thead>
              <tbody>
                <tr v-if="loading && !paged.length"><td :colspan="batchMode ? 8 : 7" style="text-align:center;padding:28px;color:var(--text-light)">{{ t('加载中…') }}</td></tr>
                <tr v-else-if="loadError && !paged.length"><td :colspan="batchMode ? 8 : 7" style="text-align:center;padding:28px;color:var(--danger)">{{ loadError }} · <button class="btn btn-outline btn-sm" @click="load">{{ t('重试') }}</button></td></tr>
                <tr v-for="u in paged" :key="u.id" class="um-row" @click="detail = u">
                  <td v-if="batchMode" class="um-check-col" @click.stop><input type="checkbox" :checked="selectedIds.includes(u.id)" @change="toggleSel(u.id)" /></td>
                  <td><b>{{ u.no }}</b></td>
                  <td>{{ u.name }}</td>
                  <td><span class="status-tag" :class="roleTag(u.role)">{{ roleLabel(u.role) }}</span></td>
                  <td><span v-if="u.role === 'student' && u.grade" class="grade-tag">{{ t('{n} 级', { n: u.grade }) }}</span><span v-else>—</span></td>
                  <td>{{ u.college || '—' }}</td>
                  <td>{{ u.major || '—' }}</td>
                  <td><button class="um-detail" @click.stop="detail = u">{{ t('详情') }} ›</button></td>
                </tr>
                <tr v-if="!loading && !loadError && !paged.length"><td :colspan="batchMode ? 8 : 7" style="text-align:center;padding:28px;color:var(--text-light)">{{ t('没有符合条件的用户') }}</td></tr>
              </tbody>
            </table>
          </div>

          <div v-if="batchMode && selectedIds.length" class="um-batch">
            <b>{{ t('已选 {n} 人', { n: selectedIds.length }) }}</b>
            <button class="btn btn-outline btn-sm" @click="batchReset">{{ t('批量重置密码') }}</button>
            <button class="btn btn-ghost-danger btn-sm" @click="batchDelete">{{ t('批量删除') }}</button>
            <button class="um-clear" @click="selectedIds = []">{{ t('取消选择') }}</button>
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
              <span v-if="totalPages > 7" class="um-jump">{{ t('跳至') }}
                <input v-model.number="jumpTo" type="number" min="1" :max="totalPages" @keyup.enter="doJump" />
                {{ t('页') }}<button class="pg-btn" @click="doJump">GO</button>
              </span>
            </div>
          </div>
        </section>
      </div>

      <Teleport to="body">
        <div v-if="detail" class="um-mask" @click="detail = null">
          <div class="um-drawer" @click.stop>
            <div class="um-drawer-h">
              <div><b>{{ detail.name }}</b><span>{{ detail.no }}</span></div>
              <button class="um-close" @click="detail = null">×</button>
            </div>
            <div class="um-drawer-body">
              <div v-for="f in detailFields" :key="f.label" class="um-field"><span>{{ f.label }}</span><b>{{ f.value || '—' }}</b></div>
            </div>
            <div class="um-drawer-f">
              <button class="btn btn-primary btn-sm" @click="openDialog(detail)">{{ t('编辑') }}</button>
              <button class="btn btn-outline btn-sm" @click="resetPwd(detail)">{{ t('重置密码') }}</button>
              <button class="btn btn-ghost-danger btn-sm" @click="del(detail)">{{ t('删除') }}</button>
            </div>
          </div>
        </div>
      </Teleport>
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
import { downloadTemplate, pickAndImport } from '@/utils/xlsxImport'
import { COLLEGES } from '@/utils/colleges'
import { t } from '@/i18n'
const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`
const users = ref<any[]>([])
const total = ref(0)
const kw = ref('')
const roleFilter = ref<string | null>(null)
const dlg = ref(false)
// —— 角色 Tab 人数 / 班级下拉：由 /admin/users 一次性返回，不受筛选影响 ——
const counts = ref<Record<string, number>>({})
const classOpts = ref<string[]>([])
const classCounts = ref<Record<string, number>>({})
const classFilter = ref<string | null>(null)
const jumpTo = ref<number | null>(null)
// —— 左侧组织树 / 详情抽屉 / 批量操作 ——
const treeData = ref<any[]>([])
const treeKw = ref('')
const openNodes = ref<Set<string>>(new Set())
const expandedMaj = ref<Set<string>>(new Set())
const detail = ref<any>(null)
const batchMode = ref(false)
const selectedIds = ref<number[]>([])
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
// —— 服务端分页：筛选（角色/班级/关键词）传后端，当前页数据 + 总数由接口返回 ——
const pageSize = ref(20)
const page = ref(1)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
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
watch([majorFilter, gradeFilter, classFilter], () => { page.value === 1 ? load() : (page.value = 1) })
watch(pageSize, () => { page.value === 1 ? load() : (page.value = 1) })
watch(page, load)
watch(totalPages, tp => { if (page.value > tp) page.value = tp })
const roleLabel = (r: string) => ({ student: t('学生'), teacher: t('教师'), admin: t('管理员') } as any)[r] || r
const roleTag = (r: string) => ({ student: 'status-approved', teacher: 'status-pending', admin: 'status-warning' } as any)[r] || 'status-approved'

// —— 角色 Tab：人数来自后端全库聚合，不随筛选变化 ——
const roleTabs = computed(() => [
  { value: null, label: t('全部'), count: counts.value.all ?? total.value },
  { value: 'student', label: t('学生'), count: counts.value.student ?? 0 },
  { value: 'teacher', label: t('教师'), count: counts.value.teacher ?? 0 },
  { value: 'admin', label: t('管理员'), count: counts.value.admin ?? 0 },
])

// —— 已选条件 chips，点单个 chip 即清除该条件 ——
const activeChips = computed(() => {
  const out: { key: string; label: string; clear: () => void }[] = []
  if (kw.value.trim()) out.push({ key: 'kw', label: `${t('关键词')}：${kw.value.trim()}`, clear: () => { kw.value = '' } })
  const rv = roleFilter.value
  if (rv) out.push({ key: 'role', label: `${t('角色')}：${roleLabel(rv as string)}`, clear: () => { roleFilter.value = null } })
  if (collegeFilter.value) out.push({ key: 'college', label: `${t('学院')}：${collegeFilter.value}`, clear: () => { collegeFilter.value = null } })
  if (majorFilter.value) out.push({ key: 'major', label: `${t('专业')}：${majorFilter.value}`, clear: () => { majorFilter.value = null } })
  if (gradeFilter.value) out.push({ key: 'grade', label: `${t('年级')}：${gradeFilter.value}`, clear: () => { gradeFilter.value = null } })
  if (classFilter.value) out.push({ key: 'class', label: `${t('班级')}：${classFilter.value === '__none__' ? t('未分班 / 教职工') : classFilter.value}`, clear: () => { classFilter.value = null } })
  return out
})
function clearFilters() {
  kw.value = ''; roleFilter.value = null; collegeFilter.value = null
  majorFilter.value = null; gradeFilter.value = null; classFilter.value = null
  page.value === 1 ? load() : (page.value = 1)
}

// —— 左侧组织树：学院 → 专业 → 班级 ——
function toggleNode(k: string) {
  const s = new Set(openNodes.value)
  if (s.has(k)) s.delete(k); else s.add(k)
  openNodes.value = s
}
function isOpen(k: string) {
  return treeKw.value.trim() ? true : openNodes.value.has(k)
}
function expandMaj(k: string) {
  const s = new Set(expandedMaj.value); s.add(k); expandedMaj.value = s
}
function visibleClasses(maj: any, k: string) {
  const list = maj.classes || []
  return expandedMaj.value.has(k) || treeKw.value.trim() ? list : list.slice(0, 6)
}
function moreClasses(maj: any, k: string) {
  return (maj.classes || []).length > 6 && !expandedMaj.value.has(k) && !treeKw.value.trim()
}
const treeCols = computed(() => {
  const q = treeKw.value.trim().toLowerCase()
  return treeData.value.map((col: any) => {
    const majors = (col.majors || []).map((maj: any) => ({
      ...maj,
      classes: (maj.classes || []).filter((cl: any) =>
        !q || String(col.name).toLowerCase().includes(q)
        || String(maj.name).toLowerCase().includes(q)
        || String(cl.name).toLowerCase().includes(q)),
    })).filter((m: any) => m.classes.length)
    return { ...col, majors }
  }).filter((c: any) => c.majors.length)
})
function selectClass(v: string | null) {
  classFilter.value = v
  selectedIds.value = []
  page.value === 1 ? load() : (page.value = 1)
}
const scopeLabel = computed(() => {
  if (classFilter.value === '__none__') return t('未分班 / 教职工')
  if (classFilter.value) return classFilter.value
  return t('全部用户')
})

// —— 行详情抽屉 ——
const detailFields = computed(() => !detail.value ? [] : [
  { label: t('学号/工号'), value: detail.value.no },
  { label: t('姓名'), value: detail.value.name },
  { label: t('角色'), value: roleLabel(detail.value.role) },
  { label: t('年级'), value: detail.value.grade ? `${detail.value.grade} 级` : '' },
  { label: t('学院'), value: detail.value.college },
  { label: t('专业'), value: detail.value.major },
  { label: t('班级'), value: detail.value.classNo },
  { label: t('手机号'), value: detail.value.phone },
])

// —— 批量操作与导出当前范围 ——
const allChecked = computed(() => users.value.length > 0 && users.value.every((u: any) => selectedIds.value.includes(u.id)))
function toggleAll(e: any) {
  selectedIds.value = e.target.checked ? users.value.map((u: any) => u.id) : []
}
function toggleSel(id: number) {
  selectedIds.value = selectedIds.value.includes(id)
    ? selectedIds.value.filter(x => x !== id)
    : [...selectedIds.value, id]
}
function currentParams(): any {
  const p: any = { page: 1, size: 10000 }
  if (roleFilter.value) p.role = roleFilter.value
  if (collegeFilter.value) p.college = collegeFilter.value
  if (majorFilter.value) p.major = majorFilter.value
  if (gradeFilter.value) p.grade = gradeFilter.value
  if (classFilter.value) p.classNo = classFilter.value
  if (kw.value.trim()) p.keyword = kw.value.trim()
  return p
}
async function exportCurrent() {
  try {
    const r: any = await http.get('/admin/users', { params: currentParams() })
    const d = r?.data
    const list: any[] = Array.isArray(d) ? d : (d?.items || [])
    const esc = (v: any) => '"' + String(v ?? '').replace(/"/g, '""') + '"'
    const head = [t('学号/工号'), t('姓名'), t('角色'), t('年级'), t('学院'), t('专业'), t('班级'), t('手机号')]
    const lines = [head.map(esc).join(','), ...list.map((u: any) =>
      [u.no, u.name, roleLabel(u.role), u.grade ?? '', u.college ?? '', u.major ?? '', u.classNo ?? '', u.phone ?? ''].map(esc).join(','))]
    const blob = new Blob(['\ufeff' + lines.join('\r\n')], { type: 'text/csv;charset=utf-8' })
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = `用户-${scopeLabel.value}.csv`
    a.click(); URL.revokeObjectURL(a.href)
    ElMessage.success(t('已导出 {n} 条', { n: list.length }))
  } catch (e: any) {
    ElMessage.error(e?.message || t('导出失败'))
  }
}
async function batchReset() {
  if (!selectedIds.value.length) return
  try { await ElMessageBox.confirm(t('将选中的 {n} 个账号密码重置为 123456？', { n: selectedIds.value.length }), t('提示'), { type: 'warning' }) } catch { return }
  try {
    const pwd = await encryptPassword('123456')
    for (const id of selectedIds.value) await http.put(`/admin/users/${id}/reset-password`, { newPassword: pwd })
    ElMessage.success(t('已重置')); selectedIds.value = []; load()
  } catch (e: any) { ElMessage.error(e?.message || t('操作失败')) }
}
async function batchDelete() {
  if (!selectedIds.value.length) return
  try { await ElMessageBox.confirm(t('确定删除选中的 {n} 个账号？此操作不可撤销。', { n: selectedIds.value.length }), t('提示'), { type: 'warning' }) } catch { return }
  try {
    for (const id of selectedIds.value) await http.delete(`/admin/users/${id}`)
    ElMessage.success(t('已删除')); selectedIds.value = []; detail.value = null; load()
  } catch (e: any) { ElMessage.error(e?.message || t('操作失败')) }
}

function doJump() {
  const n = Number(jumpTo.value)
  if (!Number.isFinite(n) || n < 1) return
  page.value = Math.min(Math.max(1, Math.round(n)), totalPages.value)
  jumpTo.value = null
}

async function load() {
  const seq = ++reqSeq
  loading.value = true
  loadError.value = ''
  try {
    const params: any = { page: page.value, size: pageSize.value }
    if (roleFilter.value) params.role = roleFilter.value
    if (collegeFilter.value) params.college = collegeFilter.value
    if (majorFilter.value) params.major = majorFilter.value
    if (gradeFilter.value) params.grade = gradeFilter.value
    if (classFilter.value) params.classNo = classFilter.value
    if (kw.value.trim()) params.keyword = kw.value.trim()
    const r: any = await http.get('/admin/users', { params })
    if (seq !== reqSeq) return   // 丢弃过期响应，避免旧请求覆盖新结果
    const d = r?.data
    if (Array.isArray(d)) {
      // 旧后端（未重启）：不支持 keyword/role 参数 → 本地过滤 + 切片分页兼容
      let list: any[] = d
      if (roleFilter.value) list = list.filter(u => u.role === roleFilter.value)
      if (classFilter.value) list = list.filter(u => u.classNo === classFilter.value)
      if (kw.value.trim()) {
        const k = kw.value.trim().toLowerCase()
        list = list.filter(u => ((u.no || '') + (u.name || '') + (u.classNo || '') + (u.phone || '')).toLowerCase().includes(k))
      }
      users.value = list.slice((page.value - 1) * pageSize.value, page.value * pageSize.value)
      total.value = list.length
      counts.value = { all: list.length }
      classOpts.value = [...new Set(list.map(u => u.classNo).filter(Boolean))].sort() as string[]
      const cc: Record<string, number> = {}
      list.forEach(u => { if (u.classNo) cc[u.classNo] = (cc[u.classNo] || 0) + 1 })
      classCounts.value = cc
    } else {
      users.value = d?.items || []
      total.value = d?.total || 0
      if (d?.counts) counts.value = d.counts
      if (d?.classes) classOpts.value = d.classes
      if (d?.classCounts) classCounts.value = d.classCounts
      if (d?.tree) {
        treeData.value = d.tree
        if (!openNodes.value.size && d.tree.length) {
          // 首次进入展开第一个学院 + 第一个专业，避免左侧只有一层空树
          const c0 = d.tree[0], m0 = c0.majors?.[0]
          const s = new Set<string>()
          s.add('c:' + c0.name)
          if (m0) s.add('m:' + c0.name + '/' + m0.name)
          openNodes.value = s
        }
      }
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

/* 角色分层 Tab */
.um-tabbar { display: flex; align-items: center; gap: 10px; flex-wrap: nowrap; padding: 2px 0 10px; }
.um-tabs { display: flex; gap: 6px; flex-wrap: nowrap; flex: 1 1 auto; min-width: 0; overflow-x: auto; }
.um-tab { display: inline-flex; align-items: center; gap: 6px; flex: 0 0 auto; white-space: nowrap; padding: 6px 12px; border: 1px solid var(--border); border-radius: 999px; background: var(--card); color: var(--text-light); font-family: inherit; font-size: 12.5px; cursor: pointer; transition: all .2s; }
.um-tab:hover { border-color: var(--primary); color: var(--primary); }
.um-tab.on { background: var(--primary-bg, rgba(43,177,135,.12)); border-color: var(--primary); color: var(--primary); font-weight: 600; }
.um-tab-num { font-size: 11px; padding: 1px 6px; border-radius: 999px; background: rgba(0,0,0,.05); color: var(--text-lighter); }
.um-tab.on .um-tab-num { background: rgba(43,177,135,.18); color: var(--primary); }
.um-tools-right { display: flex; align-items: center; gap: 6px; margin-left: auto; flex: 0 0 auto; flex-wrap: nowrap; }
.um-per-label { font-size: 12px; color: var(--text-lighter); }
.um-per { padding: 5px 8px; border: 1px solid var(--border); border-radius: 8px; background: var(--card); color: var(--text); font-family: inherit; font-size: 12.5px; }

/* 已选条件 chips */
.um-chips { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; padding: 0 0 10px; }
.um-chip-label { font-size: 12px; color: var(--text-lighter); }
.um-chip { display: inline-flex; align-items: center; gap: 5px; padding: 4px 9px; border: 1px solid var(--border); border-radius: 999px; background: var(--card); color: var(--text-light); font-family: inherit; font-size: 12px; cursor: pointer; }
.um-chip:hover { border-color: var(--primary); }
.um-x { color: var(--text-lighter); font-size: 13px; line-height: 1; }
.um-clear { border: none; background: none; color: var(--primary); font-family: inherit; font-size: 12px; cursor: pointer; padding: 4px 6px; }

.um-dot { display: inline-block; width: 6px; height: 6px; border-radius: 50%; background: var(--primary); flex: 0 0 auto; }

/* 左侧组织树 + 右侧列表 */
.um-split { display: grid; grid-template-columns: 232px minmax(0, 1fr); gap: 12px; align-items: start; }
.um-tree { border: 0.5px solid var(--border); border-radius: 12px; background: var(--card); overflow: hidden; }
.um-tree-h { display: flex; align-items: center; justify-content: space-between; padding: 10px 12px; border-bottom: 0.5px solid var(--border); font-size: 12.5px; color: var(--text); }
.um-tree-h span { font-size: 11px; color: var(--text-lighter); }
.um-tree-search { padding: 8px 10px; border-bottom: 0.5px solid var(--border); }
.um-tree-body { max-height: 560px; overflow-y: auto; padding: 6px; }
.um-root, .um-node, .um-leaf, .um-more { display: flex; align-items: center; gap: 6px; width: 100%; text-align: left; border: none; background: none; font-family: inherit; font-size: 12.5px; color: var(--text-light); cursor: pointer; border-radius: 8px; padding: 7px 8px; }
.um-root:hover, .um-node:hover, .um-leaf:hover, .um-more:hover { background: var(--primary-bg, rgba(43,177,135,.1)); color: var(--primary); }
.um-root.on, .um-leaf.on { background: var(--primary, #2bb187); color: #fff; }
.um-root.on .um-n, .um-leaf.on .um-n { color: rgba(255,255,255,.9); background: rgba(255,255,255,.18); }
.um-n { margin-left: auto; font-size: 11px; color: var(--text-lighter); background: rgba(0,0,0,.05); border-radius: 999px; padding: 1px 6px; flex: 0 0 auto; }
.um-caret { width: 10px; color: var(--text-lighter); font-size: 11px; flex: 0 0 auto; }
.um-node { font-weight: 600; color: var(--text); }
.um-node.sub { font-weight: 400; padding-left: 14px; color: var(--text-light); }
.um-leaf { padding-left: 30px; font-size: 12px; }
.um-more { padding-left: 30px; color: var(--primary); font-size: 11.5px; }
.um-tree-empty { padding: 16px; text-align: center; font-size: 12px; color: var(--text-lighter); }
.um-main { min-width: 0; }
.um-list-h { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; padding: 10px 12px; border: 0.5px solid var(--border); border-radius: 12px; background: var(--primary-bg, rgba(43,177,135,.09)); margin-bottom: 6px; }
.um-list-title { display: flex; align-items: center; gap: 8px; font-size: 13px; color: var(--primary); }
.um-scope-n { font-size: 11.5px; color: var(--text-light); background: rgba(0,0,0,.05); padding: 2px 8px; border-radius: 999px; }
.um-list-actions { margin-left: auto; display: flex; gap: 6px; flex-wrap: wrap; }
.um-hint { padding: 0 4px 8px; font-size: 11.5px; color: var(--text-lighter); }
.um-row { cursor: pointer; }
.um-row:hover { background: var(--primary-bg, rgba(43,177,135,.06)); }
.um-detail { border: none; background: none; color: var(--primary); font-family: inherit; font-size: 12px; cursor: pointer; padding: 4px 6px; white-space: nowrap; }
.um-check-col { width: 36px; text-align: center; }
.um-batch { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; padding: 8px 12px; margin-top: 8px; border: 0.5px solid var(--border); border-radius: 10px; background: var(--card); font-size: 12px; color: var(--text); }

/* 行详情抽屉 */
.um-mask { position: fixed !important; inset: 0; background: rgba(0,0,0,.38); z-index: 3000 !important; display: flex; justify-content: flex-end; }
.um-drawer { width: 360px; max-width: 90vw; height: 100%; background: var(--card); border-left: 0.5px solid var(--border); display: flex; flex-direction: column; }
.um-drawer-h { display: flex; align-items: center; padding: 16px 18px; border-bottom: 0.5px solid var(--border); }
.um-drawer-h b { font-size: 15px; color: var(--text); }
.um-drawer-h span { display: block; font-size: 12px; color: var(--text-lighter); margin-top: 2px; }
.um-close { margin-left: auto; border: none; background: none; font-size: 22px; color: var(--text-light); cursor: pointer; line-height: 1; }
.um-drawer-body { flex: 1; overflow-y: auto; padding: 12px 18px; }
.um-field { display: flex; justify-content: space-between; gap: 12px; padding: 10px 0; border-bottom: 0.5px solid var(--border); font-size: 12.5px; }
.um-field span { color: var(--text-lighter); flex: 0 0 auto; }
.um-field b { color: var(--text); font-weight: 500; text-align: right; word-break: break-all; }
.um-drawer-f { display: flex; gap: 8px; flex-wrap: wrap; padding: 14px 18px; border-top: 0.5px solid var(--border); }

@media (max-width: 900px) {
  .um-split { grid-template-columns: 1fr; }
  .um-tree-body { max-height: 220px; }
}

/* 跳页 */
.um-jump { display: inline-flex; align-items: center; gap: 5px; margin-left: 8px; font-size: 12px; color: var(--text-light); }
.um-jump input { width: 52px; padding: 4px 6px; border: 1px solid var(--border); border-radius: 6px; background: var(--card); color: var(--text); font-family: inherit; font-size: 12px; }
</style>
