<template>
  <div class="page-wrap stats-page liquid">
    <!-- ===== 学期滚动：推进到下一学期 ===== -->
    <div class="sm-advance">
      <div class="sm-adv-main">
        <b>{{ t('学期滚动') }}</b>
        <span>{{ t('推进到下一学期：旧学期成绩与课表自动归档，新学期必修课按培养方案自动排入（挂科必修自动重修）。') }}</span>
      </div>
      <button class="btn btn-primary" :disabled="advancing" @click="advance">{{ advancing ? t('推进中…') : t('推进到下一学期') }}</button>
    </div>

<div class="section">
      <div class="section-header">
        <div class="section-title" v-html="svgIcon('calendar') + ' ' + t('学期管理（{n} 个）', { n: list.length })"></div>
        <button class="btn btn-primary btn-sm" @click="openDialog(null)">{{ t('+ 新建学期') }}</button>
      </div>
      <div class="data-table">
        <table>
          <thead><tr><th>{{ t('学期名称') }}</th><th>{{ t('学年') }}</th><th>{{ t('开始日期') }}</th><th>{{ t('结束日期') }}</th><th>{{ t('状态') }}</th><th>{{ t('操作') }}</th></tr></thead>
          <tbody>
            <tr v-for="s in list" :key="s.id">
              <td><b>{{ s.name }}</b></td>
              <td>{{ s.year }}</td>
              <td>{{ s.startDate }}</td>
              <td>{{ s.endDate }}</td>
              <td><span class="status-tag" :class="s.active ? 'status-approved' : 'status-pending'">{{ s.active ? t('进行中') : t('已结束') }}</span></td>
              <td>
                <button class="btn btn-outline btn-sm" @click="openDialog(s)">{{ t('编辑') }}</button>
                <button class="btn btn-danger btn-sm" @click="del(s)">{{ t('删除') }}</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
    <el-dialog v-model="dlg" :title="form.id ? t('编辑学期') : t('新建学期')" width="450px">
      <el-form :model="form" label-position="top">
        <el-form-item :label="t('学期名称')"><el-input v-model="form.name" :placeholder="t('2024-2025 第一学期')" /></el-form-item>
        <el-form-item :label="t('学年')"><el-input v-model.number="form.year" type="number" /></el-form-item>
        <el-form-item :label="t('开始日期')"><el-date-picker v-model="form.startDate" type="date" /></el-form-item>
        <el-form-item :label="t('结束日期')"><el-date-picker v-model="form.endDate" type="date" /></el-form-item>
        <el-form-item :label="t('是否当前学期')"><el-switch v-model="form.active" /></el-form-item>
      </el-form>
      <template #footer>
        <button class="btn btn-ghost btn-sm" @click="dlg = false">{{ t('取消') }}</button>
        <button class="btn btn-primary btn-sm" @click="save">{{ t('保存') }}</button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api'
import { t } from '@/i18n'
const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`
const list = ref<any[]>([])
const dlg = ref(false)
const form = reactive<any>({ id: null, name: '', year: new Date().getFullYear(), startDate: null, endDate: null, active: false })
const advancing = ref(false)
async function advance() {
  try {
    await ElMessageBox.confirm(t('确认推进到下一学期？此操作影响全体学生的学期序与必修排课。'), t('学期滚动'), {
      type: 'warning', confirmButtonText: t('确认推进'), cancelButtonText: t('取消')
    })
  } catch { return }
  advancing.value = true
  try {
    const r: any = await http.post('/system/semester-advance')
    ElMessage.success(r?.message || t('已推进'))
    load()
  } catch (e: any) { ElMessage.error(e?.message || t('推进失败')) }
  finally { advancing.value = false }
}

async function load() { try { const r: any = await http.get('/semesters'); list.value = r?.data || [] } catch {} }
function openDialog(s: any) {
  Object.assign(form, s || { id: null, name: '', year: new Date().getFullYear(), startDate: null, endDate: null, active: false })
  dlg.value = true
}
async function save() {
  try {
    if (form.id) await http.put(`/semesters/${form.id}`, form)
    else await http.post('/semesters', form)
    ElMessage.success(t('保存成功')); dlg.value = false; load()
  } catch (e: any) { ElMessage.error(e?.message || t('保存失败')) }
}
async function del(s: any) {
  try { await ElMessageBox.confirm(t('确定删除学期「{n}」？', { n: s.name }), t('提示'), { type: 'warning', confirmButtonText: t('确定'), cancelButtonText: t('取消') }) } catch { return }
  try { await http.delete(`/semesters/${s.id}`); ElMessage.success(t('已删除')); load() } catch (e: any) { ElMessage.error(e?.message || t('删除失败')) }
}
onMounted(load)
</script>

<style scoped>.page-wrap { padding: 4px 0; }/* ===== 学期滚动 ===== */
.sm-advance { display: flex; align-items: center; justify-content: space-between; gap: 16px; flex-wrap: wrap;
  padding: 16px 20px; border-radius: 14px; border: 1px solid rgba(43,177,135,.3);
  background: linear-gradient(135deg, rgba(43,177,135,.08), rgba(56,189,248,.06)); }
.sm-adv-main { display: flex; flex-direction: column; gap: 4px; }
.sm-adv-main b { font-size: 14px; color: var(--text); }
.sm-adv-main span { font-size: 12.5px; color: var(--text-light); line-height: 1.6; max-width: 720px; }
</style>