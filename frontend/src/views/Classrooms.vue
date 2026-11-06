<template>
  <div class="page-wrap">
    <div class="section">
      <div class="section-header">
        <div class="section-title" v-html="svgIcon('building') + ' ' + t('教室管理（{n} 个）', { n: list.length })"></div>
        <button class="btn btn-primary btn-sm" @click="openDialog(null)">{{ t('+ 新增教室') }}</button>
      </div>

      <!-- 概览：与 3D 沙盘楼栋口径一致 -->
      <div class="cr-summary">
        <div class="cr-sum"><b>{{ list.length }}</b><span>{{ t('教室总数') }}</span></div>
        <div class="cr-sum"><b>{{ buildingCount }}</b><span>{{ t('沙盘楼栋') }}</span></div>
        <div class="cr-sum"><b>{{ totalSeats }}</b><span>{{ t('总座位') }}</span></div>
        <div class="cr-sum"><b>{{ maintenanceCount }}</b><span>{{ t('维护中') }}</span></div>
        <div class="cr-sum-note">{{ t('教室编号规则：楼栋-楼层×100+序号，例如 明志楼-301；楼栋与层数取自学生端 3D 沙盘') }}</div>
      </div>

      <div class="cr-toolbar">
        <el-input v-model="kw" :placeholder="t('搜索教室 / 设备')" clearable style="width:190px" />
        <el-select v-model="buildingFilter" clearable filterable :placeholder="t('全部楼栋')" style="width:170px">
          <el-option v-for="s in BUILDING_SPECS" :key="s.name" :label="s.name" :value="s.name" />
        </el-select>
        <el-select v-model="typeFilter" clearable :placeholder="t('全部类型')" style="width:160px">
          <el-option v-for="ty in ROOM_TYPE_LIST" :key="ty" :label="t(ty)" :value="ty" />
        </el-select>
        <el-select v-model="statusFilter" clearable :placeholder="t('全部状态')" style="width:130px">
          <el-option v-for="s in ROOM_STATUS" :key="s" :label="t(s)" :value="s" />
        </el-select>
        <span class="cr-count">{{ t('显示 {n} / {m}', { n: filtered.length, m: list.length }) }}</span>
      </div>

      <div v-for="g in grouped" :key="g.name" class="cr-bld">
        <div class="cr-bld-head">
          <span class="cr-zone">{{ t(g.zone) }}</span>
          <b class="cr-bld-name">{{ g.name }}</b>
          <span class="cr-role">{{ g.role }}</span>
          <span class="cr-bld-stat">{{ t('{r} 间 · {s} 座 · 最高 {f} 层', { r: g.rooms.length, s: g.seats, f: g.maxFloor }) }}</span>
        </div>
        <div class="classroom-grid">
          <div v-for="c in g.rooms" :key="c.id" class="classroom-card" :class="{ 'is-maint': c.status === '维护中' }">
            <div class="cc-head">
              <div class="cc-name">{{ c.name }}</div>
              <span class="cc-type" :class="'tone-' + toneOf(c.type)">{{ c.type ? t(c.type) : t('普通教室') }}</span>
            </div>
            <div class="cc-meta">{{ t('第 {n} 层', { n: c.floorNo || floorOf(c.name) }) }} · {{ t('容纳') }} <b>{{ c.capacity }}</b> {{ t('人') }}</div>
            <div class="cc-eq" :title="c.equipment || ''">{{ c.equipment || '—' }}</div>
            <div class="cc-acts">
              <span v-if="c.status === '维护中'" class="cc-maint">{{ t('维护中') }}</span>
              <button class="btn btn-outline btn-sm" @click="openDialog(c)">{{ t('编辑') }}</button>
              <button class="btn btn-ghost-danger btn-sm" @click="del(c)">{{ t('删除') }}</button>
            </div>
          </div>
        </div>
      </div>
      <div v-if="!grouped.length" class="cr-empty">{{ t('没有符合条件的教室') }}</div>
    </div>

    <el-dialog v-model="dlg" :title="form.id ? t('编辑教室') : t('新增教室')" width="600px" class="cr-dialog"
               append-to-body align-center :close-on-click-modal="false">
      <el-form :model="form" label-position="top" class="cr-form" @submit.prevent="save">
        <div class="cr-row">
          <el-form-item :label="t('楼栋（3D 沙盘）')" required>
            <el-select v-model="form.building" filterable :placeholder="t('选择沙盘中的教学楼')" style="width:100%" @change="onBuildingChange">
              <el-option v-for="s in BUILDING_SPECS" :key="s.name" :label="s.name + ' · ' + s.role" :value="s.name" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('所在楼层')" required>
            <el-select v-model="form.floorNo" style="width:100%">
              <el-option v-for="f in buildingFloors" :key="f" :label="t('第 {n} 层', { n: f })" :value="f" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('本层序号')" required>
            <el-input-number v-model.number="form.seq" :min="1" :max="20" style="width:100%" />
          </el-form-item>
        </div>
        <el-form-item :label="t('教室名称')" required>
          <el-input v-model="form.name" :placeholder="t('如 明志楼-301')" clearable @input="manualName = true" />
          <div class="cr-tip">{{ autoName ? t('按楼栋 / 楼层 / 序号自动编号') : t('已手动修改，保存时不自动改写') }}</div>
        </el-form-item>
        <div class="cr-row">
          <el-form-item :label="t('类型')">
            <el-select v-model="form.type" style="width:100%" @change="onTypeChange">
              <el-option v-for="ty in ROOM_TYPE_LIST" :key="ty" :label="t(ty)" :value="ty" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('状态')">
            <el-select v-model="form.status" style="width:100%">
              <el-option v-for="s in ROOM_STATUS" :key="s" :label="t(s)" :value="s" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item :label="t('容纳人数')" required>
          <div class="cr-cap">
            <el-input-number v-model.number="form.capacity" :min="10" :max="500" :step="10" style="width:160px" />
            <div class="cr-presets">
              <button v-for="p in capPresets" :key="p" type="button" class="cr-chip" :class="{ on: form.capacity === p }" @click="form.capacity = p">{{ p }}</button>
            </div>
          </div>
          <div class="cr-tip">{{ t('{ty} 建议容量 {a}–{b} 人', { ty: t(form.type), a: typeSpec.capacities[0], b: typeSpec.capacities[typeSpec.capacities.length - 1] }) }}</div>
        </el-form-item>
        <el-form-item :label="t('设备')">
          <div class="cr-eq">
            <div class="cr-chips">
              <button v-for="eq in eqChips" :key="eq" type="button" class="cr-chip" :class="{ on: hasEq(eq) }" @click="toggleEq(eq)">{{ t(eq) }}</button>
            </div>
            <el-input v-model="form.equipment" :placeholder="t('或直接填写，如 投影仪、空调')" clearable />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <button class="btn btn-ghost btn-sm" @click="dlg = false">{{ t('取消') }}</button>
        <button class="btn btn-primary btn-sm" :disabled="saving" @click="save">{{ saving ? t('保存中…') : (form.id ? t('保存修改') : t('创建教室')) }}</button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api'
import { t } from '@/i18n'
import { CAMPUS_BUILDINGS } from '@/utils/campusConfig'
import {
  BUILDING_SPECS, ROOM_TYPES, ROOM_TYPE_LIST, ROOM_STATUS,
  generateClassroomPlan, type RoomType,
} from '@/utils/classroomPlan'

const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`
const list = ref<any[]>([])
const dlg = ref(false)
const saving = ref(false)
const kw = ref('')
const buildingFilter = ref('')
const typeFilter = ref('')
const statusFilter = ref('')
const manualName = ref(false)
const eqChips = ['投影仪', '多媒体', '扩音器', '空调', '录播', '白板', '电脑', '语音设备', '实验台']
const form = reactive<any>({
  id: null, name: '', building: '', location: '', roomNo: '', floorNo: 1, seq: 1,
  capacity: 60, type: '多媒体教室', equipment: '', status: '正常',
})

const specMap = new Map(BUILDING_SPECS.map(s => [s.name, s]))
const specOrder = new Map(BUILDING_SPECS.map((s, i) => [s.name, i]))
const floorMap = new Map(CAMPUS_BUILDINGS.map(b => [b.name, b.floors]))
/** 目录兜底：后端尚未返回 building/type/floorNo 时，按规范教室名补全 */
const planByName = new Map(generateClassroomPlan().map(r => [r.name, r]))

const buildingCount = computed(() => new Set(list.value.map(c => c.building || c.location || '')).size)
const totalSeats = computed(() => list.value.reduce((s, c) => s + (Number(c.capacity) || 0), 0))
const maintenanceCount = computed(() => list.value.filter(c => c.status === '维护中').length)
const buildingFloors = computed(() => Array.from({ length: Math.max(1, floorMap.get(form.building) || 6) }, (_, i) => i + 1))
const autoName = computed(() => !manualName.value)
const typeSpec = computed(() => ROOM_TYPES[form.type as RoomType] || ROOM_TYPES['普通教室'])
const capPresets = computed(() => [...new Set([...typeSpec.value.capacities, 40, 60, 80, 120, 200])].sort((a, b) => a - b))

const filtered = computed(() => {
  const k = kw.value.trim().toLowerCase()
  return list.value.filter(c => {
    if (buildingFilter.value && (c.building || c.location) !== buildingFilter.value) return false
    if (typeFilter.value && (c.type || '普通教室') !== typeFilter.value) return false
    if (statusFilter.value && (c.status || '正常') !== statusFilter.value) return false
    if (k && !(`${c.name || ''} ${c.equipment || ''} ${c.building || ''}`.toLowerCase().includes(k))) return false
    return true
  })
})

/** 按沙盘楼栋分组，顺序与 3D 沙盘一致；自定义楼栋排在最后 */
const grouped = computed(() => {
  const m = new Map<string, any[]>()
  for (const c of filtered.value) {
    const b = c.building || c.location || String(c.name || '').split('-')[0] || t('自定义楼栋')
    if (!m.has(b)) m.set(b, [])
    m.get(b)!.push(c)
  }
  return [...m.entries()].map(([name, rooms]) => {
    const spec = specMap.get(name)
    return {
      name,
      zone: spec?.zone || t('其他'),
      role: spec?.role || t('自定义楼栋'),
      rooms,
      seats: rooms.reduce((s, c) => s + (Number(c.capacity) || 0), 0),
      maxFloor: Math.max(1, ...rooms.map(c => Number(c.floorNo) || floorOf(c.name))),
    }
  }).sort((a, b) => (specOrder.get(a.name) ?? 999) - (specOrder.get(b.name) ?? 999) || a.name.localeCompare(b.name, 'zh'))
})

function toneOf(type: string) { return (ROOM_TYPES[type as RoomType] || ROOM_TYPES['普通教室']).tone }
function floorOf(name: string) {
  const m = /-(\d{3})/.exec(String(name || ''))
  return m ? Number(m[1][0]) : 1
}
function composeName() {
  if (!form.building || !form.floorNo || !form.seq) return
  form.name = `${form.building}-${form.floorNo}${String(form.seq).padStart(2, '0')}`
}
watch([() => form.building, () => form.floorNo, () => form.seq], () => { if (!manualName.value) composeName() })
function onBuildingChange() {
  const max = floorMap.get(form.building) || 6
  if (form.floorNo > max) form.floorNo = max
  if (!manualName.value) composeName()
}
function onTypeChange() {
  form.capacity = typeSpec.value.capacities[0]
  form.equipment = typeSpec.value.equipment
}

async function load() {
  try {
    const r: any = await http.get('/classrooms')
    list.value = (r?.data || []).map((c: any) => {
      const p = planByName.get(c.name)
      return {
        ...c,
        building: c.building || c.location || p?.building || '',
        type: c.type || p?.type || '普通教室',
        floorNo: c.floorNo ?? p?.floor ?? null,
        status: c.status || p?.status || '正常',
        equipment: c.equipment || p?.equipment || '',
      }
    })
  } catch {}
}
function openDialog(c: any) {
  const base = { id: null, name: '', building: BUILDING_SPECS[0].name, location: '', roomNo: '', floorNo: 1, seq: 1, capacity: 80, type: '多媒体教室', equipment: ROOM_TYPES['多媒体教室'].equipment, status: '正常' }
  Object.assign(form, c || base)
  if (c) {
    form.building = c.building || c.location || String(c.name || '').split('-')[0] || BUILDING_SPECS[0].name
    form.floorNo = Number(c.floorNo) || floorOf(c.name)
    const m = /-(\d{3})$/.exec(String(c.name || ''))
    form.seq = m ? Number(m[1].slice(1)) : 1
    form.type = c.type || '普通教室'
    form.status = c.status || '正常'
  }
  manualName.value = !!c
  if (!c) composeName()
  dlg.value = true
}
function hasEq(eq: string) { return (form.equipment || '').split(/[、,，;；\s]+/).includes(eq) }
function toggleEq(eq: string) {
  const parts = (form.equipment || '').split(/[、,，;；\s]+/).filter(Boolean)
  if (hasEq(eq)) { const i = parts.indexOf(eq); if (i > -1) parts.splice(i, 1) } else { parts.push(eq) }
  form.equipment = parts.join('、')
}
async function save() {
  const name = String(form.name || '').trim()
  if (!name) { ElMessage.warning(t('请填写教室名称')); return }
  if (!form.building) { ElMessage.warning(t('请选择楼栋')); return }
  const maxFloor = floorMap.get(form.building) || 6
  const floor = Number(form.floorNo)
  if (floor < 1 || floor > maxFloor) { ElMessage.warning(t('楼层不能超过该楼栋的 {n} 层', { n: maxFloor })); return }
  if (!/^.+-\d{3}$/.test(name)) { ElMessage.warning(t('教室编号须为「楼栋-楼层+序号」，如 明志楼-301')); return }
  if (!form.capacity || form.capacity < 10) { ElMessage.warning(t('容纳人数至少 10 人')); return }
  if (list.value.some(c => c.name === name && c.id !== form.id)) { ElMessage.warning(t('教室名称已存在')); return }
  const payload = {
    id: form.id, roomNo: name, name,
    building: form.building, location: form.building,
    floorNo: floor, type: form.type, capacity: Number(form.capacity),
    equipment: form.equipment, status: form.status,
  }
  saving.value = true
  try {
    if (form.id) await http.put(`/classrooms/${form.id}`, payload)
    else await http.post('/classrooms', payload)
    ElMessage.success(form.id ? t('修改已保存') : t('教室创建成功'))
    dlg.value = false; load()
  } catch (e: any) { ElMessage.error(e?.message || t('保存失败')) }
  finally { saving.value = false }
}
async function del(c: any) {
  try { await ElMessageBox.confirm(t('确定删除「{n}」？', { n: c.name }), t('提示'), { type: 'warning', confirmButtonText: t('确定'), cancelButtonText: t('取消') }) } catch { return }
  try { await http.delete(`/classrooms/${c.id}`); ElMessage.success(t('已删除')); load() } catch (e: any) { ElMessage.error(e?.message || t('删除失败')) }
}
onMounted(load)
</script>

<style scoped>
.page-wrap { padding: 4px 0; }
.cr-summary { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; margin: 4px 0 14px; }
.cr-sum { display: flex; flex-direction: column; min-width: 92px; padding: 8px 14px; background: var(--card-bg); border: 1px solid var(--border); border-radius: 10px; }
.cr-sum b { font-size: 20px; color: var(--primary); line-height: 1.2; }
.cr-sum span { font-size: 11px; color: var(--text-light); margin-top: 2px; }
.cr-sum-note { flex: 1; min-width: 260px; font-size: 11px; line-height: 1.6; color: var(--text-light); }
.cr-toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin-bottom: 14px; padding: 10px 12px; background: var(--card-bg); border: 1px solid var(--border); border-radius: 10px; }
.cr-count { margin-left: auto; font-size: 12px; color: var(--text-light); }
.cr-bld { margin-bottom: 18px; }
.cr-bld-head { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; padding: 0 2px 8px; border-bottom: 1px solid var(--border); margin-bottom: 10px; }
.cr-zone { font-size: 11px; font-weight: 700; color: var(--primary); background: rgba(43,177,135,.12); border-radius: 4px; padding: 2px 7px; }
.cr-bld-name { font-size: 15px; }
.cr-role { font-size: 12px; color: var(--text-light); }
.cr-bld-stat { margin-left: auto; font-size: 11px; color: var(--text-light); }
.classroom-grid { display: grid; gap: 10px; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); }
.classroom-card { background: var(--card-bg); border: 1px solid var(--border); border-radius: 10px; padding: 12px 14px; }
.classroom-card.is-maint { border-style: dashed; opacity: .8; }
.cc-head { display: flex; justify-content: space-between; align-items: center; gap: 6px; }
.cc-name { font-size: 13.5px; font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.cc-type { flex-shrink: 0; font-size: 10.5px; font-weight: 600; border-radius: 4px; padding: 2px 6px; }
.tone-gray { color: #64748b; background: rgba(100,116,139,.14); }
.tone-green { color: #15803d; background: rgba(43,177,135,.15); }
.tone-blue { color: #0369a1; background: rgba(56,189,248,.16); }
.tone-purple { color: #7c3aed; background: rgba(139,92,246,.15); }
.tone-amber { color: #b45309; background: rgba(245,158,11,.16); }
.cc-meta { font-size: 12px; color: var(--text-light); margin: 6px 0 2px; }
.cc-meta b { color: var(--primary); }
.cc-eq { font-size: 11px; color: var(--text-light); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.cc-acts { display: flex; align-items: center; gap: 6px; margin-top: 10px; }
.cc-maint { font-size: 11px; color: #b45309; margin-right: auto; }
.cr-empty { padding: 40px; text-align: center; color: var(--text-light); font-size: 13px; }
.cr-form :deep(.el-form-item) { margin-bottom: 12px; }
.cr-row { display: grid; grid-template-columns: 1.6fr 1fr 1fr; gap: 10px; }
.cr-cap { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.cr-presets, .cr-chips { display: flex; flex-wrap: wrap; gap: 5px; }
.cr-chip { font-size: 11px; padding: 3px 8px; border: 1px solid var(--border); border-radius: 5px; background: var(--card-bg); color: var(--text); cursor: pointer; }
.cr-chip.on { border-color: var(--primary); color: var(--primary); background: rgba(43,177,135,.1); }
.cr-eq { display: flex; flex-direction: column; gap: 8px; width: 100%; }
.cr-tip { font-size: 11px; color: var(--text-light); margin-top: 4px; line-height: 1.5; }
@media (max-width: 700px) { .cr-row { grid-template-columns: 1fr; } }
</style>
