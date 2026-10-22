<template>
  <div class="page-wrap">
    <div class="section">
      <div class="section-header">
        <div class="section-title" v-html="svgIcon('building') + ' 教室管理（' + list.length + ' 个）'"></div>
        <button class="btn btn-primary btn-sm" @click="openDialog(null)">+ 新增教室</button>
      </div>
      <div class="classroom-grid">
        <div v-for="c in list" :key="c.id" class="classroom-card">
          <div class="cc-head">
            <div class="cc-name">{{ c.name }} <span class="cc-building">{{ c.building }}</span></div>
            <span class="status-tag status-approved">{{ c.type || '普通教室' }}</span>
          </div>
          <div class="cc-meta">容纳 <b>{{ c.capacity }}</b> 人 · {{ c.equipment || '—' }}</div>
          <div class="cc-acts">
            <button class="btn btn-outline btn-sm" @click="openDialog(c)">编辑</button>
            <button class="btn btn-danger btn-sm" @click="del(c)">删除</button>
          </div>
        </div>
      </div>
    </div>
    <el-dialog v-model="dlg" :title="form.id ? '编辑教室' : '新增教室'" width="560px" class="cr-dialog" append-to-body align-center :close-on-click-modal="false">
      <el-form :model="form" label-position="top" class="cr-form" @submit.prevent="save">
        <el-form-item label="教室名称" required>
          <el-input v-model="form.name" placeholder="如 教学楼A-301" clearable @input="autoBuilding" />
        </el-form-item>
        <div class="cr-row">
          <el-form-item label="楼栋">
            <el-input v-model="form.building" placeholder="如 教学楼A" />
          </el-form-item>
          <el-form-item label="类型">
            <el-select v-model="form.type" style="width:100%">
              <el-option label="普通教室" value="普通教室" />
              <el-option label="多媒体教室" value="多媒体教室" />
              <el-option label="计算机实验室" value="计算机实验室" />
              <el-option label="阶梯教室" value="阶梯教室" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="容纳人数" required>
          <div class="cr-cap">
            <el-input-number v-model.number="form.capacity" :min="10" :max="500" :step="10" style="width:160px" />
            <div class="cr-presets">
              <button v-for="p in capPresets" :key="p" type="button" class="cr-chip" :class="{ on: form.capacity === p }" @click="form.capacity = p">{{ p }}</button>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="设备">
          <div class="cr-eq">
            <div class="cr-chips">
              <button v-for="eq in eqChips" :key="eq" type="button" class="cr-chip" :class="{ on: hasEq(eq) }" @click="toggleEq(eq)">{{ eq }}</button>
            </div>
            <el-input v-model="form.equipment" placeholder="或直接填写，如 投影仪、空调" clearable />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <button class="btn btn-ghost btn-sm" @click="dlg = false">取消</button>
        <button class="btn btn-primary btn-sm" :disabled="saving" @click="save">{{ saving ? '保存中…' : (form.id ? '保存修改' : '创建教室') }}</button>
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
const saving = ref(false)
const capPresets = [40, 60, 120, 200, 300]
const eqChips = ['投影仪', '多媒体', '扩音器', '空调', '录播', '白板', '电脑']
const form = reactive<any>({ id: null, name: '', building: '', capacity: 60, type: '普通教室', equipment: '' })
async function load() { try { const r: any = await http.get('/classrooms'); list.value = r?.data || [] } catch {} }
function openDialog(s: any) { Object.assign(form, s || { id: null, name: '', building: '', capacity: 60, type: '普通教室', equipment: '' }); dlg.value = true }
/** 从名称自动识别楼栋（如 教学楼A-301 → 教学楼A），楼栋已有值时不覆盖 */
function autoBuilding() {
  const m = /^(.+?)[-－—]\d+/.exec((form.name || '').trim())
  if (m && !form.building) form.building = m[1]
}
function hasEq(eq: string) { return (form.equipment || '').split(/[、,，;；\s]+/).includes(eq) }
function toggleEq(eq: string) {
  const parts = (form.equipment || '').split(/[、,，;；\s]+/).filter(Boolean)
  if (hasEq(eq)) { const i = parts.indexOf(eq); if (i > -1) parts.splice(i, 1) } else { parts.push(eq) }
  form.equipment = parts.join('、')
}
async function save() {
  if (!form.name || !form.name.trim()) { ElMessage.warning('请填写教室名称'); return }
  if (!form.capacity || form.capacity < 10) { ElMessage.warning('容纳人数至少 10 人'); return }
  saving.value = true
  try { if (form.id) await http.put(`/classrooms/${form.id}`, form); else await http.post('/classrooms', form); ElMessage.success(form.id ? '修改已保存' : '教室创建成功'); dlg.value = false; load() }
  catch (e: any) { ElMessage.error(e?.message || '保存失败') }
  finally { saving.value = false }
}
async function del(c: any) {
  try { await ElMessageBox.confirm(`确定删除「${c.name}」？`, '提示', { type: 'warning' }) } catch { return }
  try { await http.delete(`/classrooms/${c.id}`); ElMessage.success('已删除'); load() } catch (e: any) { ElMessage.error(e?.message || '删除失败') }
}
onMounted(load)
</script>

<style scoped>
.page-wrap { padding: 4px 0; }
.classroom-grid { display: grid; gap: 12px; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); }
.classroom-card { background: var(--card-bg); border: 1px solid var(--border); border-radius: 10px; padding: 14px 16px; }
.cc-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.cc-name { font-size: 14px; font-weight: 600; }
.cc-building { font-size: 11px; color: var(--text-light); margin-left: 4px; }
.cc-meta { font-size: 12px; color: var(--text-light); margin: 6px 0 10px; }
.cc-meta b { color: var(--primary); }
.cc-acts { display: flex; gap: 6px; }
</style>
