<template>
  <div class="page-wrap stats-page liquid">
    <div class="section">
      <div class="section-header">
        <div class="section-title" v-html="svgIcon('book') + ' 课程管理（' + list.length + ' 门）'"></div>
        <div style="display:flex;gap:8px">
          <el-input v-model="kw" placeholder="搜索课程名/教师" size="default" clearable style="width:220px" @input="load" />
          <button class="btn btn-primary btn-sm" @click="openDialog(null)">+ 新增课程</button>
        </div>
      </div>
      <div class="data-table">
        <table>
          <thead><tr><th>课程名</th><th>类别</th><th>学分</th><th>教师</th><th>容量/已报</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="c in paged" :key="c.id">
              <td><b>{{ c.name }}</b></td>
              <td><span class="course-cat" :class="'cat-' + c.category">{{ c.category }}</span></td>
              <td>{{ c.credit }}</td>
              <td>{{ c.teacherName || c.teacher }}</td>
              <td>{{ c.capacity }} / {{ c.enrolled || 0 }}</td>
              <td><span class="status-tag" :class="c.active === false ? 'status-pending' : 'status-approved'">{{ c.active === false ? '已停开' : '正常' }}</span></td>
              <td>
                <button class="btn btn-outline btn-sm" @click="openDialog(c)">编辑</button>
                <button class="btn btn-danger btn-sm" @click="del(c)">删除</button>
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
    <el-dialog v-model="dlg" :title="form.id ? '编辑课程' : '新增课程'" width="520px">
      <el-form :model="form" label-position="top">
        <el-row :gutter="12">
          <el-col :span="24"><el-form-item label="课程名称"><el-input v-model="form.name" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="类别">
            <el-select v-model="form.category"><el-option label="必修" value="必修" /><el-option label="选修" value="选修" /><el-option label="公选" value="公选" /></el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="学分"><el-input-number v-model.number="form.credit" :min="1" :max="6" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="授课教师">
            <el-select v-model="form.teacherId" filterable placeholder="选择教师">
              <el-option v-for="t in teachers" :key="t.id" :label="t.name + ' (' + t.no + ')'" :value="t.id" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="容量"><el-input-number v-model.number="form.capacity" :min="5" :max="500" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="上课时间"><el-input v-model="form.schedule" placeholder="如 周一 1-2 节" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="教室"><el-input v-model="form.classroom" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="课程描述"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item></el-col>
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
const list = ref<any[]>([])
const teachers = ref<any[]>([])
const kw = ref('')
const dlg = ref(false)
const form = reactive<any>({ id: null, name: '', category: '必修', credit: 3, teacherId: null, capacity: 60, schedule: '', classroom: '', description: '' })
// —— 分页 ——
const pageSize = 10
const page = ref(1)
const filtered = computed(() => {
  if (!kw.value) return list.value
  const k = kw.value.toLowerCase()
  return list.value.filter(c => c.name.toLowerCase().includes(k) || (c.teacherName || c.teacher || '').toLowerCase().includes(k))
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
watch(kw, () => { page.value = 1 })
watch(totalPages, t => { if (page.value > t) page.value = t })
async function load() {
  try {
    const [c, t] = await Promise.all([http.get('/courses'), http.get('/courses/teachers')])
    list.value = c?.data || []
    teachers.value = t?.data || []
  } catch {}
}
function openDialog(c: any) { Object.assign(form, c || { id: null, name: '', category: '必修', credit: 3, teacherId: null, capacity: 60, schedule: '', classroom: '', description: '' }); dlg.value = true }
async function save() {
  try { if (form.id) await http.put(`/courses/${form.id}`, form); else await http.post('/courses', form); ElMessage.success('保存成功'); dlg.value = false; load() }
  catch (e: any) { ElMessage.error(e?.message || '保存失败') }
}
async function del(c: any) {
  try { await ElMessageBox.confirm(`确定删除课程「${c.name}」？`, '提示', { type: 'warning' }) } catch { return }
  try { await http.delete(`/courses/${c.id}`); ElMessage.success('已删除'); load() } catch (e: any) { ElMessage.error(e?.message || '删除失败') }
}
onMounted(load)
</script>

<style scoped>.page-wrap { padding: 4px 0; }</style>
