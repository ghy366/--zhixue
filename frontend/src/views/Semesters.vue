<template>
  <div class="page-wrap stats-page liquid">
    <div class="section">
      <div class="section-header">
        <div class="section-title" v-html="svgIcon('calendar') + ' 学期管理（' + list.length + ' 个）'"></div>
        <button class="btn btn-primary btn-sm" @click="openDialog(null)">+ 新建学期</button>
      </div>
      <div class="data-table">
        <table>
          <thead><tr><th>学期名称</th><th>学年</th><th>开始日期</th><th>结束日期</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="s in list" :key="s.id">
              <td><b>{{ s.name }}</b></td>
              <td>{{ s.year }}</td>
              <td>{{ s.startDate }}</td>
              <td>{{ s.endDate }}</td>
              <td><span class="status-tag" :class="s.active ? 'status-approved' : 'status-pending'">{{ s.active ? '进行中' : '已结束' }}</span></td>
              <td>
                <button class="btn btn-outline btn-sm" @click="openDialog(s)">编辑</button>
                <button class="btn btn-danger btn-sm" @click="del(s)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
    <el-dialog v-model="dlg" :title="form.id ? '编辑学期' : '新建学期'" width="450px">
      <el-form :model="form" label-position="top">
        <el-form-item label="学期名称"><el-input v-model="form.name" placeholder="2024-2025 第一学期" /></el-form-item>
        <el-form-item label="学年"><el-input v-model.number="form.year" type="number" /></el-form-item>
        <el-form-item label="开始日期"><el-date-picker v-model="form.startDate" type="date" /></el-form-item>
        <el-form-item label="结束日期"><el-date-picker v-model="form.endDate" type="date" /></el-form-item>
        <el-form-item label="是否当前学期"><el-switch v-model="form.active" /></el-form-item>
      </el-form>
      <template #footer>
        <button class="btn btn-ghost btn-sm" @click="dlg = false">取消</button>
        <button class="btn btn-primary btn-sm" @click="save">保存</button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api'
const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`
const list = ref<any[]>([])
const dlg = ref(false)
const form = reactive<any>({ id: null, name: '', year: new Date().getFullYear(), startDate: null, endDate: null, active: false })
async function load() { try { const r: any = await http.get('/semesters'); list.value = r?.data || [] } catch {} }
function openDialog(s: any) {
  Object.assign(form, s || { id: null, name: '', year: new Date().getFullYear(), startDate: null, endDate: null, active: false })
  dlg.value = true
}
async function save() {
  try {
    if (form.id) await http.put(`/semesters/${form.id}`, form)
    else await http.post('/semesters', form)
    ElMessage.success('保存成功'); dlg.value = false; load()
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') }
}
async function del(s: any) {
  try { await ElMessageBox.confirm(`确定删除学期「${s.name}」？`, '提示', { type: 'warning' }) } catch { return }
  try { await http.delete(`/semesters/${s.id}`); ElMessage.success('已删除'); load() } catch (e: any) { ElMessage.error(e?.message || '删除失败') }
}
onMounted(load)
</script>

<style scoped>.page-wrap { padding: 4px 0; }</style>
