<template>
  <div class="cr-page">
    <!-- ================= 顶部 sticky 导航栏 ================= -->
    <header class="cr-topbar">
      <div class="cr-brand">
        <div class="cr-brand-icon">
          <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M3 21h18M5 21V7l7-4 7 4v14M9 21v-5h6v5M9 9h.01M15 9h.01M9 13h.01M15 13h.01"/>
          </svg>
        </div>
        <div class="cr-brand-text">
          <div class="cr-title">{{ t('教室管理') }}</div>
          <div class="cr-subtitle">{{ t('共 {n} 间 · {b} 栋教学楼', { n: list.length, b: buildingCount }) }}</div>
        </div>
      </div>
      <button class="cr-search-trigger" @click="openCommand" :aria-label="t('打开搜索')">
        <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>
        <span class="cr-search-ph">{{ filters.keyword || t('搜索教室 / 设备 / 楼栋，支持拼音首字母…') }}</span>
        <kbd class="cr-kbd">{{ isMac ? '⌘' : 'Ctrl+' }} K</kbd>
      </button>
      <div class="cr-top-actions">
        <button class="cr-btn cr-btn-solid" @click="openDialog(null)">
          <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path d="M12 5v14M5 12h14"/></svg>
          <span>{{ t('新增教室') }}</span>
        </button>
      </div>
    </header>

    <!-- 概览：与 3D 沙盘楼栋口径一致（保留原样，不动 3D 沙盘） -->
    <div class="cr-summary">
      <div class="cr-sum"><b>{{ list.length }}</b><span>{{ t('教室总数') }}</span></div>
      <div class="cr-sum"><b>{{ buildingCount }}</b><span>{{ t('沙盘楼栋') }}</span></div>
      <div class="cr-sum"><b>{{ totalSeats }}</b><span>{{ t('总座位') }}</span></div>
      <div class="cr-sum"><b>{{ maintenanceCount }}</b><span>{{ t('维护中') }}</span></div>
      <div class="cr-sum-note">{{ t('教室编号规则：楼栋-楼层×100+序号，例如 明志楼-301；楼栋与层数取自学生端 3D 沙盘') }}</div>
    </div>

    <div class="cr-layout">
      <!-- ================= 左侧楼栋树（240px） ================= -->
      <aside class="cr-tree">
        <div class="cr-tree-h"><b>{{ t('沙盘楼栋') }}</b><span>{{ buildingCount }} {{ t('栋') }}</span></div>
        <div class="cr-tree-search">
          <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>
          <input v-model="treeKw" :placeholder="t('过滤楼栋 / 区域')" />
        </div>
        <div class="cr-tree-body">
          <button class="cr-node cr-root" :class="{ on: !filters.building }" @click="filters.building = ''">
            <span class="cr-node-label">{{ t('全部教学楼') }}</span><span class="cr-n">{{ list.length }}</span>
          </button>
          <div v-for="g in treeBuildings" :key="g.name" class="cr-cat">
            <button class="cr-node" :class="{ on: filters.building === g.name }" @click="filters.building = filters.building === g.name ? '' : g.name">
              <span class="cr-zone-dot" :style="{ background: zoneColor(g.zone) }"></span>
              <span class="cr-node-label">{{ g.name }}</span><span class="cr-n">{{ g.count }}</span>
            </button>
          </div>
          <div v-if="!treeBuildings.length" class="cr-tree-empty">{{ t('没有匹配的楼栋') }}</div>
        </div>
      </aside>

      <!-- ================= 右侧主区 ================= -->
      <main class="cr-main">
        <!-- 筛选轨道 chips -->
        <section class="cr-filtercard">
          <div class="cr-chip-row">
            <div v-for="chip in chipDefs" :key="chip.key" class="cr-chip-wrap">
              <button class="cr-chip" :class="{ on: chip.active }" @click="toggleChip(chip.key)">
                <span class="cr-chip-label">{{ chip.label }}</span>
                <span v-if="chip.value" class="cr-chip-value">{{ chip.value }}</span>
                <svg class="ic cr-chip-arrow" :class="{ open: openChip === chip.key }" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="m6 9 6 6 6-6"/></svg>
              </button>
              <div v-if="openChip === chip.key" class="cr-dropdown" @click.stop>
                <button class="cr-opt" :class="{ on: !chip.current }" @click="chip.clear(); openChip = null">
                  <span>{{ t('全部') }}{{ chip.label }}</span><span v-if="!chip.current" class="cr-opt-check">✓</span>
                </button>
                <button v-for="o in chip.options" :key="String(o.value)" class="cr-opt" :class="{ on: chip.current === o.value }" @click="chip.set(o.value); openChip = null">
                  <span>{{ o.label }}<span v-if="o.count !== undefined" class="cr-opt-n">{{ o.count }}</span></span>
                  <span v-if="chip.current === o.value" class="cr-opt-check">✓</span>
                </button>
              </div>
            </div>
            <button v-if="hasFilter" class="cr-reset" @click="clearFilters">{{ t('清空') }}</button>
            <span class="cr-count">{{ t('显示 {n} / {m}', { n: filtered.length, m: list.length }) }}</span>
          </div>
        </section>

        <!-- 楼栋分组卡片（保留原展示逻辑，按沙盘楼栋分组） -->
        <section class="cr-listcard">
          <div v-for="g in grouped" :key="g.name" class="cr-bld">
            <div class="cr-bld-head">
              <span class="cr-zone">{{ t(g.zone) }}</span>
              <b class="cr-bld-name">{{ g.name }}</b>
              <span class="cr-role">{{ g.role }}</span>
              <span class="cr-bld-stat">{{ t('{r} 间 · {s} 座 · 最高 {f} 层', { r: g.rooms.length, s: g.seats, f: g.maxFloor }) }}</span>
            </div>
            <div class="classroom-grid">
              <div v-for="c in g.rooms" :key="c.id" class="classroom-card" :class="{ 'is-maint': c.status === '维护中' }" @click="openDetail(c)">
                <div class="cc-head">
                  <div class="cc-name">{{ c.name }}</div>
                  <span class="cc-type" :class="'tone-' + toneOf(c.type)">{{ c.type ? t(c.type) : t('普通教室') }}</span>
                </div>
                <div class="cc-meta">{{ t('第 {n} 层', { n: c.floorNo || floorOf(c.name) }) }} · {{ t('容纳') }} <b>{{ c.capacity }}</b> {{ t('人') }}</div>
                <div class="cc-eq" :title="c.equipment || ''">{{ c.equipment || '—' }}</div>
                <div class="cc-acts">
                  <span v-if="c.status === '维护中'" class="cc-maint">{{ t('维护中') }}</span>
                  <button class="btn btn-outline btn-sm" @click.stop="openDialog(c)">{{ t('编辑') }}</button>
                  <button class="btn btn-ghost-danger btn-sm" @click.stop="del(c)">{{ t('删除') }}</button>
                </div>
              </div>
            </div>
          </div>

          <!-- 空结果 Dice 纠错 -->
          <div v-if="!grouped.length" class="cr-empty">
            <div class="cr-empty-icon">
              <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 4.5 4.5"/><path d="M8 10.5h5"/></svg>
            </div>
            <div class="cr-empty-title">{{ t('没有符合条件的教室') }}</div>
            <div class="cr-empty-sub">{{ t('试试换个关键词，或看看下面的相似结果') }}</div>
            <div v-if="suggestChips.length" class="cr-suggest">
              <span class="cr-suggest-label">{{ t('是不是想找') }}</span>
              <button v-for="s in suggestChips" :key="s" class="cr-suggest-chip" @click="filters.keyword = s">{{ s }}</button>
            </div>
            <button class="cr-empty-reset" @click="clearFilters">{{ t('清空筛选') }}</button>
          </div>
        </section>
      </main>
    </div>

    <!-- ================= 命令面板 ================= -->
    <Teleport to="body">
      <div v-if="cmdOpen" class="cmd-overlay" @click="closeCommand(false)">
        <div class="cmd-panel" @click.stop>
          <div class="cmd-input-row">
            <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>
            <input ref="cmdInput" v-model="cmdTerm" :placeholder="t('搜索教室 / 设备 / 楼栋，支持拼音首字母…')" />
            <kbd class="cr-kbd">esc</kbd>
          </div>
          <div class="cmd-results">
            <template v-if="cmdItems.length">
              <div v-if="cmdRooms.length" class="cmd-group">
                <div class="cmd-group-h">{{ t('教室') }} <span>{{ cmdRooms.length }}</span></div>
                <button v-for="(u, i) in cmdRooms" :key="'u' + u.id" class="cmd-item" :class="{ active: cmdCursor === i }" @mouseenter="cmdCursor = i" @click="selectCmd(cmdItems[i])">
                  <span class="cmd-item-ico user">
                    <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M3 21h18M5 21V7l7-4 7 4v14"/></svg>
                  </span>
                  <span class="cmd-item-main"><b>{{ u.name }}</b><small>{{ u.building || '' }} · {{ t('第 {n} 层', { n: u.floorNo || floorOf(u.name) }) }} · {{ u.capacity }} {{ t('座') }}</small></span>
                  <span class="cmd-enter" v-if="cmdCursor === i">↵</span>
                </button>
              </div>
              <div v-if="cmdBuildings.length" class="cmd-group">
                <div class="cmd-group-h">{{ t('楼栋') }} <span>{{ cmdBuildings.length }}</span></div>
                <button v-for="(o, j) in cmdBuildings" :key="'o' + o.name" class="cmd-item" :class="{ active: cmdCursor === cmdRooms.length + j }" @mouseenter="cmdCursor = cmdRooms.length + j" @click="selectCmd(cmdItems[cmdRooms.length + j])">
                  <span class="cmd-item-ico org">
                    <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M3 21h18M5 21V7l7-4 7 4v14M9 21v-6h6v6"/></svg>
                  </span>
                  <span class="cmd-item-main"><b>{{ o.name }}</b><small>{{ t(o.zone) }} · {{ o.count }} {{ t('间教室') }}</small></span>
                  <span class="cmd-enter" v-if="cmdCursor === cmdRooms.length + j">↵</span>
                </button>
              </div>
            </template>
            <div v-else class="cmd-empty">{{ t('没有联想结果，试试教室名 / 设备 / 楼栋') }}</div>
          </div>
          <div class="cmd-footer">
            <span><kbd class="cr-kbd">↑</kbd><kbd class="cr-kbd">↓</kbd> {{ t('导航') }}</span>
            <span><kbd class="cr-kbd">↵</kbd> {{ t('选中') }}</span>
            <span class="cmd-footer-tip">{{ t('选中教室看档案 · 选中楼栋即筛选') }}</span>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- ================= 教室详情抽屉 ================= -->
    <Teleport to="body">
      <div v-if="crDetail" class="cr-mask" @click="crDetail = null">
        <div class="cr-drawer" @click.stop>
          <div class="cr-drawer-h">
            <div><b>{{ crDetail.name }}</b><span>{{ t(crDetail.type) }} · {{ t('第 {n} 层', { n: crDetail.floorNo || floorOf(crDetail.name) }) }}</span></div>
            <button class="cr-close" @click="crDetail = null">×</button>
          </div>
          <div class="cr-drawer-body">
            <div v-for="f in detailFields" :key="f.label" class="cr-field"><span>{{ f.label }}</span><b>{{ f.value || '—' }}</b></div>
          </div>
          <div class="cr-drawer-f">
            <button class="cr-btn cr-btn-solid" @click="editFromDetail(crDetail)">{{ t('编辑') }}</button>
            <button class="cr-btn cr-btn-danger" @click="del(crDetail)">{{ t('删除') }}</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- ================= 新增 / 编辑教室弹窗（保留原样） ================= -->
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
import { ref, reactive, toRefs, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api'
import { t } from '@/i18n'
import { CAMPUS_BUILDINGS } from '@/utils/campusConfig'
import {
  BUILDING_SPECS, ROOM_TYPES, ROOM_TYPE_LIST, ROOM_STATUS,
  generateClassroomPlan, type RoomType,
} from '@/utils/classroomPlan'
import { diceCoefficient } from '@/utils/similarity'
import { initialsOf } from '@/utils/pinyin'

const isMac = typeof navigator !== 'undefined' && /Mac|iPhone|iPad/.test(navigator.platform)

/* 单一状态源：楼栋树 / chips / 命令面板 / 搜索框读写同一 filters */
const filters = reactive({
  keyword: '' as string,
  building: '' as string,
  type: '' as string,
  status: '' as string,
})
const { keyword: kw, building: buildingFilter, type: typeFilter, status: statusFilter } = toRefs(filters)

const list = ref<any[]>([])
const dlg = ref(false)
const saving = ref(false)
const manualName = ref(false)
const treeKw = ref('')
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

/** 按沙盘楼栋分组，顺序与 3D 沙盘一致；自定义楼栋排在最后（保留原逻辑） */
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

/* —— 左侧楼栋树（计数基于全库） —— */
const treeBuildings = computed(() => {
  const q = treeKw.value.trim().toLowerCase()
  const groups = grouped.value
  return BUILDING_SPECS
    .filter(s => !q || s.name.toLowerCase().includes(q) || String(s.zone).toLowerCase().includes(q))
    .map(s => ({ name: s.name, zone: s.zone, count: groups.find(g => g.name === s.name)?.rooms.length || 0 }))
})
function zoneColor(zone: string) {
  const z = String(zone || '')
  if (z.includes('教学')) return '#10b981'
  if (z.includes('实验')) return '#3b82f6'
  if (z.includes('行政')) return '#8b5cf6'
  return '#f59e0b'
}

/* —— 筛选轨道 chips —— */
type ChipKey = 'building' | 'type' | 'status'
const openChip = ref<ChipKey | null>(null)
function toggleChip(k: ChipKey) { openChip.value = openChip.value === k ? null : k }
const chipDefs = computed(() => [
  {
    key: 'building' as ChipKey, label: t('楼栋'), active: !!buildingFilter.value, value: buildingFilter.value || '', current: buildingFilter.value,
    options: [...new Set([...BUILDING_SPECS.map(s => s.name), ...list.value.map(c => c.building || c.location).filter(Boolean)])]
      .filter(Boolean).sort((a: any, b: any) => String(a).localeCompare(String(b), 'zh'))
      .map((b: any) => ({ value: b, label: b, count: list.value.filter(c => (c.building || c.location) === b).length })),
    clear: () => { buildingFilter.value = '' }, set: (v: any) => { buildingFilter.value = v as string },
  },
  {
    key: 'type' as ChipKey, label: t('类型'), active: !!typeFilter.value, value: typeFilter.value || '', current: typeFilter.value,
    options: ROOM_TYPE_LIST.map(ty => ({ value: ty, label: t(ty), count: list.value.filter(c => (c.type || '普通教室') === ty).length })),
    clear: () => { typeFilter.value = '' }, set: (v: any) => { typeFilter.value = v as string },
  },
  {
    key: 'status' as ChipKey, label: t('状态'), active: !!statusFilter.value, value: statusFilter.value || '', current: statusFilter.value,
    options: ROOM_STATUS.map(s => ({ value: s, label: t(s), count: list.value.filter(c => (c.status || '正常') === s).length })),
    clear: () => { statusFilter.value = '' }, set: (v: any) => { statusFilter.value = v as string },
  },
])
const hasFilter = computed(() => !!(kw.value || filters.building || filters.type || filters.status))
function clearFilters() {
  kw.value = ''; filters.building = ''; filters.type = ''; filters.status = ''
}

/* —— 详情抽屉 —— */
const crDetail = ref<any>(null)
const detailFields = computed(() => !crDetail.value ? [] : [
  { label: t('教室名称'), value: crDetail.value.name },
  { label: t('楼栋'), value: crDetail.value.building || crDetail.value.location },
  { label: t('区域'), value: t(specMap.get(crDetail.value.building)?.zone || '其他') },
  { label: t('楼层'), value: `${crDetail.value.floorNo || floorOf(crDetail.value.name)} ${t('层')}` },
  { label: t('类型'), value: t(crDetail.value.type || '普通教室') },
  { label: t('容纳人数'), value: `${crDetail.value.capacity} ${t('人')}` },
  { label: t('设备'), value: crDetail.value.equipment },
  { label: t('状态'), value: t(crDetail.value.status || '正常') },
])
function openDetail(c: any) { crDetail.value = c }
function editFromDetail(c: any) { crDetail.value = null; openDialog(c) }

/* —— 空结果 Dice 纠错（bigram + 单字混合） —— */
function charDice(a: string, b: string): number {
  const A = new Set(Array.from(a.toLowerCase().replace(/\s+/g, '')))
  const B = new Set(Array.from(b.toLowerCase().replace(/\s+/g, '')))
  if (!A.size || !B.size) return 0
  let inter = 0
  for (const c of A) if (B.has(c)) inter++
  return (2 * inter) / (A.size + B.size)
}
const suggestChips = computed(() => {
  const k = kw.value.trim()
  if (!k) return []
  const names = new Set<string>()
  for (const c of list.value) { names.add(c.name); if (c.equipment) names.add(c.equipment) }
  for (const s of BUILDING_SPECS) names.add(s.name)
  const scored = new Map<string, number>()
  for (const n of names) {
    const score = Math.max(diceCoefficient(k, n), charDice(k, n))
    if (score >= 0.34) scored.set(n, score)
  }
  return [...scored.entries()].sort((a, b) => b[1] - a[1]).slice(0, 5).map(([n]) => n)
})

/* —— 命令面板（本地全量数据，教室 + 楼栋分组） —— */
const cmdOpen = ref(false)
const cmdTerm = ref('')
const cmdCursor = ref(0)
const cmdInput = ref<HTMLInputElement | null>(null)
function pyOf(c: any) {
  return initialsOf(`${c.name || ''} ${c.equipment || ''} ${c.building || ''}`)
}
function matchText(haystack: string, py: string, q: string): boolean {
  const ql = q.toLowerCase().replace(/\s+/g, '')
  if (!ql) return true
  if ((haystack || '').toLowerCase().replace(/\s+/g, '').includes(ql)) return true
  return !!py && py.includes(ql)
}
function openCommand() {
  cmdTerm.value = kw.value
  cmdCursor.value = 0
  cmdOpen.value = true
  nextTick(() => cmdInput.value?.focus())
}
function closeCommand(clear: boolean) {
  cmdOpen.value = false
  if (clear) { cmdTerm.value = ''; kw.value = '' }
  else { cmdTerm.value = kw.value }
}
watch(cmdTerm, v => { if (cmdOpen.value) kw.value = v })
const cmdRooms = computed(() => {
  const q = cmdTerm.value.trim()
  const arr = !q ? list.value.slice(0, 8)
    : list.value.filter(c => matchText(`${c.name || ''} ${c.equipment || ''} ${c.building || ''}`, pyOf(c), q)).slice(0, 8)
  return arr
})
const cmdBuildings = computed(() => {
  const q = cmdTerm.value.trim()
  if (!q) return []
  const groups = grouped.value
  return BUILDING_SPECS
    .filter(s => matchText(s.name, initialsOf(s.name), q))
    .map(s => ({ ...s, count: groups.find(g => g.name === s.name)?.rooms.length || 0 }))
    .slice(0, 6)
})
type CmdItem = { kind: 'room' | 'building'; raw: any }
const cmdItems = computed<CmdItem[]>(() => [
  ...cmdRooms.value.map(c => ({ kind: 'room' as const, raw: c })),
  ...cmdBuildings.value.map(b => ({ kind: 'building' as const, raw: b })),
])
watch(cmdItems, () => { if (cmdCursor.value >= cmdItems.value.length) cmdCursor.value = 0 })
function selectCmd(item: CmdItem | undefined) {
  if (!item) return
  if (item.kind === 'room') { crDetail.value = item.raw; cmdOpen.value = false; cmdTerm.value = ''; kw.value = '' }
  else { filters.building = item.raw.name; cmdOpen.value = false; cmdTerm.value = ''; kw.value = ''; ElMessage.success(t('已按楼栋筛选：{n}', { n: item.raw.name })) }
}
function onGlobalKey(e: KeyboardEvent) {
  const meta = e.metaKey || e.ctrlKey
  if (meta && e.key.toLowerCase() === 'k') {
    e.preventDefault()
    cmdOpen.value ? closeCommand(false) : openCommand()
    return
  }
  if (!cmdOpen.value) return
  if (e.key === 'Escape') { e.preventDefault(); closeCommand(false) }
  else if (e.key === 'ArrowDown') { e.preventDefault(); if (cmdItems.value.length) cmdCursor.value = (cmdCursor.value + 1) % cmdItems.value.length }
  else if (e.key === 'ArrowUp') { e.preventDefault(); if (cmdItems.value.length) cmdCursor.value = (cmdCursor.value - 1 + cmdItems.value.length) % cmdItems.value.length }
  else if (e.key === 'Enter') { e.preventDefault(); selectCmd(cmdItems.value[cmdCursor.value]) }
}

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
  try { await http.delete(`/classrooms/${c.id}`); ElMessage.success(t('已删除')); crDetail.value = null; load() } catch (e: any) { ElMessage.error(e?.message || t('删除失败')) }
}
onMounted(() => { load(); window.addEventListener('keydown', onGlobalKey) })
onBeforeUnmount(() => { window.removeEventListener('keydown', onGlobalKey) })
</script>

<style scoped>
/* 顶部/主区沿用课程页翠绿体系；教室卡片展示与编辑弹窗样式保持原样 */
.cr-page { --g: #10b981; --g-dark: #059669; --g-soft: rgba(16,185,129,.10); --g-soft2: rgba(16,185,129,.16); display: flex; flex-direction: column; gap: 14px; padding: 4px 0; }
.ic { display: block; }
.cr-topbar { position: sticky; top: 0; z-index: 40; display: flex; align-items: center; gap: 16px; flex-wrap: wrap; padding: 12px 16px; background: color-mix(in srgb, var(--card, #fff) 86%, transparent); backdrop-filter: saturate(1.4) blur(14px); -webkit-backdrop-filter: saturate(1.4) blur(14px); border: 0.5px solid var(--border, #e5e7eb); border-radius: 16px; box-shadow: 0 1px 2px rgba(16,24,40,.03), 0 8px 24px rgba(16,24,40,.04); }
.cr-brand { display: flex; align-items: center; gap: 12px; flex: 0 0 auto; }
.cr-brand-icon { width: 40px; height: 40px; border-radius: 12px; display: grid; place-items: center; color: #fff; background: linear-gradient(135deg, #34d399, var(--g-dark)); box-shadow: 0 4px 12px rgba(16,185,129,.32); }
.cr-brand-icon .ic { width: 21px; height: 21px; }
.cr-title { font-size: 17px; font-weight: 700; color: var(--text, #0f172a); line-height: 1.2; }
.cr-subtitle { font-size: 12px; color: var(--text-lighter, #94a3b8); margin-top: 2px; }
.cr-search-trigger { flex: 1 1 320px; max-width: 520px; min-width: 180px; height: 38px; display: flex; align-items: center; gap: 9px; padding: 0 10px 0 14px; border: 1px solid var(--border, #e5e7eb); border-radius: 999px; background: var(--card, #fff); color: var(--text-lighter, #94a3b8); cursor: pointer; transition: all .18s; font-family: inherit; }
.cr-search-trigger:hover { border-color: var(--g); box-shadow: 0 0 0 3px var(--g-soft); }
.cr-search-trigger .ic { width: 17px; height: 17px; flex: 0 0 auto; }
.cr-search-ph { flex: 1; text-align: left; font-size: 13px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.cr-kbd { font-family: inherit; font-size: 11px; line-height: 1; white-space: nowrap; padding: 4px 7px; border-radius: 6px; border: 1px solid var(--border, #e5e7eb); background: color-mix(in srgb, var(--card, #fff) 92%, #000 8%); color: var(--text-light, #64748b); flex: 0 0 auto; }
.cr-top-actions { display: flex; align-items: center; gap: 8px; flex: 0 0 auto; margin-left: auto; }
.cr-btn { display: inline-flex; align-items: center; justify-content: center; gap: 6px; height: 36px; padding: 0 14px; border-radius: 10px; font-family: inherit; font-size: 13px; font-weight: 600; cursor: pointer; transition: all .16s; white-space: nowrap; border: 1px solid transparent; }
.cr-btn .ic { width: 15px; height: 15px; }
.cr-btn-solid { background: var(--g); color: #fff; box-shadow: 0 2px 8px rgba(16,185,129,.26); }
.cr-btn-solid:hover { background: var(--g-dark); transform: translateY(-1px); }
.cr-btn-danger { background: transparent; color: #ef4444; border-color: rgba(239,68,68,.35); }
.cr-btn-danger:hover { background: rgba(239,68,68,.08); }
.cr-summary { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; }
.cr-sum { display: flex; flex-direction: column; min-width: 92px; padding: 8px 14px; background: var(--card-bg); border: 1px solid var(--border); border-radius: 10px; }
.cr-sum b { font-size: 20px; color: var(--g); line-height: 1.2; }
.cr-sum span { font-size: 11px; color: var(--text-light); margin-top: 2px; }
.cr-sum-note { flex: 1; min-width: 260px; font-size: 11px; line-height: 1.6; color: var(--text-light); }
.cr-layout { display: grid; grid-template-columns: 240px minmax(0, 1fr); gap: 16px; align-items: start; }
.cr-tree { border: 0.5px solid var(--border, #e5e7eb); border-radius: 14px; background: var(--card, #fff); overflow: hidden; position: sticky; top: 82px; }
.cr-tree-h { display: flex; align-items: center; justify-content: space-between; padding: 12px 14px 8px; font-size: 13px; color: var(--text); }
.cr-tree-h b { font-weight: 700; }
.cr-tree-h span { font-size: 11px; color: var(--text-lighter); }
.cr-tree-search { display: flex; align-items: center; gap: 7px; margin: 2px 10px 10px; padding: 0 10px; height: 32px; border: 1px solid var(--border); border-radius: 9px; background: color-mix(in srgb, var(--card) 94%, #000 6%); }
.cr-tree-search .ic { width: 14px; height: 14px; color: var(--text-lighter); flex: 0 0 auto; }
.cr-tree-search input { flex: 1; min-width: 0; border: none; outline: none; background: transparent; color: var(--text); font-family: inherit; font-size: 12px; }
.cr-tree-search input::placeholder { color: var(--text-lighter); }
.cr-tree-body { max-height: calc(100vh - 260px); overflow-y: auto; padding: 2px 8px 10px; }
.cr-node { display: flex; align-items: center; gap: 7px; width: 100%; text-align: left; border: none; background: none; font-family: inherit; font-size: 12.5px; color: var(--text-light); cursor: pointer; border-radius: 9px; padding: 8px 9px; transition: all .14s; }
.cr-node:hover { background: var(--g-soft); color: var(--g); }
.cr-node.on { background: var(--g); color: #fff; font-weight: 600; }
.cr-node.on .cr-n { color: rgba(255,255,255,.95); background: rgba(255,255,255,.22); }
.cr-node-label { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cr-n { flex: 0 0 auto; font-size: 10.5px; color: var(--text-lighter); background: color-mix(in srgb, var(--card) 90%, #000 10%); border-radius: 999px; padding: 1px 7px; font-variant-numeric: tabular-nums; }
.cr-root { font-weight: 600; color: var(--text); }
.cr-zone-dot { width: 7px; height: 7px; border-radius: 50%; flex: 0 0 auto; }
.cr-tree-empty { padding: 18px 8px; text-align: center; font-size: 12px; color: var(--text-lighter); }
.cr-main { min-width: 0; display: flex; flex-direction: column; gap: 14px; }
.cr-filtercard, .cr-listcard { border: 0.5px solid var(--border, #e5e7eb); border-radius: 14px; background: var(--card, #fff); box-shadow: 0 1px 2px rgba(16,24,40,.03); }
.cr-filtercard { padding: 12px 14px; }
.cr-chip-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.cr-chip-wrap { position: relative; }
.cr-chip { display: inline-flex; align-items: center; gap: 6px; height: 32px; padding: 0 11px; border: 1px solid var(--border); border-radius: 999px; background: var(--card); color: var(--text-light); font-family: inherit; font-size: 12.5px; cursor: pointer; transition: all .16s; }
.cr-chip:hover { border-color: var(--g); color: var(--g); }
.cr-chip.on { background: var(--g-soft); border-color: var(--g); color: var(--g-dark); font-weight: 600; }
.cr-chip-label { white-space: nowrap; }
.cr-chip-value { max-width: 140px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--g-dark); }
.cr-chip-arrow { width: 13px; height: 13px; transition: transform .16s; }
.cr-chip-arrow.open { transform: rotate(180deg); }
.cr-reset { border: none; background: none; color: var(--text-lighter); font-family: inherit; font-size: 12.5px; cursor: pointer; padding: 6px 8px; }
.cr-reset:hover { color: #ef4444; }
.cr-count { margin-left: auto; font-size: 12px; color: var(--text-light); }
.cr-dropdown { position: absolute; top: calc(100% + 6px); left: 0; z-index: 30; min-width: 190px; max-height: 320px; overflow-y: auto; padding: 6px; border: 0.5px solid var(--border); border-radius: 12px; background: var(--card); box-shadow: 0 12px 32px rgba(16,24,40,.14); }
.cr-opt { display: flex; align-items: center; justify-content: space-between; gap: 12px; width: 100%; border: none; background: none; font-family: inherit; font-size: 12.5px; color: var(--text-light); cursor: pointer; padding: 8px 10px; border-radius: 8px; text-align: left; }
.cr-opt:hover { background: var(--g-soft); color: var(--g); }
.cr-opt.on { background: var(--g-soft); color: var(--g-dark); font-weight: 600; }
.cr-opt-n { margin-left: 8px; font-size: 11px; color: var(--text-lighter); font-variant-numeric: tabular-nums; }
.cr-opt-check { color: var(--g); font-weight: 700; }
.cr-listcard { padding: 14px; }
.cr-bld { margin-bottom: 18px; }
.cr-bld:last-child { margin-bottom: 0; }
.cr-bld-head { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; padding: 0 2px 8px; border-bottom: 1px solid var(--border); margin-bottom: 10px; }
.cr-zone { font-size: 11px; font-weight: 700; color: var(--g); background: var(--g-soft); border-radius: 4px; padding: 2px 7px; }
.cr-bld-name { font-size: 15px; }
.cr-role { font-size: 12px; color: var(--text-light); }
.cr-bld-stat { margin-left: auto; font-size: 11px; color: var(--text-light); }
.classroom-grid { display: grid; gap: 10px; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); }
.classroom-card { background: var(--card-bg); border: 1px solid var(--border); border-radius: 10px; padding: 12px 14px; cursor: pointer; transition: all .16s; }
.classroom-card:hover { border-color: var(--g); box-shadow: 0 4px 14px rgba(16,185,129,.10); }
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
.cc-meta b { color: var(--g); }
.cc-eq { font-size: 11px; color: var(--text-light); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.cc-acts { display: flex; align-items: center; gap: 6px; margin-top: 10px; }
.cc-maint { font-size: 11px; color: #b45309; margin-right: auto; }
.cr-empty { display: flex; flex-direction: column; align-items: center; text-align: center; padding: 46px 20px 50px; }
.cr-empty-icon { width: 56px; height: 56px; border-radius: 16px; display: grid; place-items: center; background: var(--g-soft); color: var(--g); margin-bottom: 14px; }
.cr-empty-icon .ic { width: 28px; height: 28px; }
.cr-empty-title { font-size: 15px; font-weight: 700; color: var(--text); }
.cr-empty-sub { font-size: 12.5px; color: var(--text-lighter); margin-top: 5px; }
.cr-suggest { display: flex; align-items: center; gap: 7px; flex-wrap: wrap; justify-content: center; margin-top: 18px; }
.cr-suggest-label { font-size: 12px; color: var(--text-light); }
.cr-suggest-chip { border: 1px solid var(--border); background: var(--card); color: var(--g-dark); font-family: inherit; font-size: 12.5px; padding: 6px 12px; border-radius: 999px; cursor: pointer; transition: all .15s; }
.cr-suggest-chip:hover { border-color: var(--g); background: var(--g-soft); }
.cr-empty-reset { margin-top: 18px; border: none; background: none; color: var(--g); font-family: inherit; font-size: 12.5px; cursor: pointer; text-decoration: underline; text-underline-offset: 3px; }
.cmd-overlay { position: fixed; inset: 0; z-index: 4000; display: flex; justify-content: center; align-items: flex-start; padding: 14vh 16px 16px; background: rgba(15,23,42,.42); backdrop-filter: blur(8px); -webkit-backdrop-filter: blur(8px); animation: cmdFade .16s ease; }
.cmd-panel { width: 100%; max-width: 600px; max-height: 68vh; display: flex; flex-direction: column; border: 0.5px solid var(--border); border-radius: 16px; overflow: hidden; background: var(--card); box-shadow: 0 24px 70px rgba(15,23,42,.34); animation: cmdUp .2s cubic-bezier(.16,1,.3,1); }
.cmd-input-row { display: flex; align-items: center; gap: 11px; padding: 14px 16px; border-bottom: 0.5px solid var(--border); }
.cmd-input-row .ic { width: 19px; height: 19px; color: var(--g); flex: 0 0 auto; }
.cmd-input-row input { flex: 1; min-width: 0; border: none; outline: none; background: transparent; color: var(--text); font-family: inherit; font-size: 15px; }
.cmd-input-row input::placeholder { color: var(--text-lighter); }
.cmd-results { flex: 1; overflow-y: auto; padding: 8px; }
.cmd-group + .cmd-group { margin-top: 6px; }
.cmd-group-h { display: flex; align-items: center; gap: 7px; padding: 6px 10px; font-size: 11px; font-weight: 700; letter-spacing: .04em; color: var(--text-lighter); text-transform: uppercase; }
.cmd-group-h span { font-weight: 500; }
.cmd-item { display: flex; align-items: center; gap: 11px; width: 100%; text-align: left; border: none; background: none; font-family: inherit; cursor: pointer; padding: 9px 10px; border-radius: 10px; color: var(--text); }
.cmd-item.active { background: var(--g-soft); }
.cmd-item-ico { width: 32px; height: 32px; border-radius: 9px; display: grid; place-items: center; flex: 0 0 auto; }
.cmd-item-ico .ic { width: 17px; height: 17px; }
.cmd-item-ico.user { background: rgba(16,185,129,.13); color: var(--g); }
.cmd-item-ico.org { background: rgba(59,130,246,.13); color: #3b82f6; }
.cmd-item-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.cmd-item-main b { font-size: 13px; font-weight: 600; color: var(--text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cmd-item-main small { font-size: 11px; color: var(--text-lighter); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cmd-enter { color: var(--g); font-size: 14px; flex: 0 0 auto; }
.cmd-empty { padding: 36px 16px; text-align: center; color: var(--text-lighter); font-size: 13px; }
.cmd-footer { display: flex; align-items: center; gap: 16px; padding: 10px 16px; border-top: 0.5px solid var(--border); background: color-mix(in srgb, var(--card) 94%, #000 6%); font-size: 11px; color: var(--text-lighter); }
.cmd-footer span { display: inline-flex; align-items: center; gap: 4px; }
.cmd-footer .cr-kbd { padding: 2px 6px; }
.cmd-footer-tip { margin-left: auto; color: var(--g); }
@keyframes cmdFade { from { opacity: 0 } to { opacity: 1 } }
@keyframes cmdUp { from { opacity: 0; transform: translateY(-10px) scale(.985) } to { opacity: 1; transform: none } }
.cr-mask { position: fixed !important; inset: 0; background: rgba(0,0,0,.42); z-index: 3500 !important; display: flex; justify-content: flex-end; animation: cmdFade .16s ease; }
.cr-drawer { width: 400px; max-width: 92vw; height: 100%; background: var(--card); border-left: 0.5px solid var(--border); display: flex; flex-direction: column; animation: drawerIn .22s cubic-bezier(.16,1,.3,1); }
@keyframes drawerIn { from { transform: translateX(24px); opacity: .6 } to { transform: none; opacity: 1 } }
.cr-drawer-h { display: flex; align-items: center; padding: 18px 20px; border-bottom: 0.5px solid var(--border); }
.cr-drawer-h b { font-size: 16px; color: var(--text); }
.cr-drawer-h span { display: block; font-size: 12px; color: var(--text-lighter); margin-top: 3px; }
.cr-close { margin-left: auto; border: none; background: none; font-size: 24px; color: var(--text-light); cursor: pointer; line-height: 1; }
.cr-drawer-body { flex: 1; overflow-y: auto; padding: 12px 20px; }
.cr-field { display: flex; justify-content: space-between; gap: 12px; padding: 11px 0; border-bottom: 0.5px solid var(--border); font-size: 12.5px; }
.cr-field span { color: var(--text-lighter); flex: 0 0 auto; }
.cr-field b { color: var(--text); font-weight: 500; text-align: right; word-break: break-all; }
.cr-drawer-f { display: flex; gap: 8px; flex-wrap: wrap; padding: 14px 20px; border-top: 0.5px solid var(--border); }
.cr-form :deep(.el-form-item) { margin-bottom: 12px; }
.cr-row { display: grid; grid-template-columns: 1.6fr 1fr 1fr; gap: 10px; }
.cr-cap { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.cr-presets, .cr-chips { display: flex; flex-wrap: wrap; gap: 5px; }
.cr-chip { font-size: 11px; padding: 3px 8px; border: 1px solid var(--border); border-radius: 5px; background: var(--card-bg); color: var(--text); cursor: pointer; }
.cr-chip.on { border-color: var(--primary); color: var(--primary); background: rgba(43,177,135,.1); }
.cr-eq { display: flex; flex-direction: column; gap: 8px; width: 100%; }
.cr-tip { font-size: 11px; color: var(--text-light); margin-top: 4px; line-height: 1.5; }
@media (max-width: 1100px) { .cr-search-trigger { order: 3; flex-basis: 100%; max-width: none; } .cr-top-actions { order: 2; } }
@media (max-width: 860px) { .cr-layout { grid-template-columns: 1fr; } .cr-tree { display: none; } }
@media (max-width: 700px) { .cr-row { grid-template-columns: 1fr; } }
@media (max-width: 560px) { .cr-topbar { padding: 12px; } .cr-kbd { display: none; } .cr-count { margin-left: 0; } }
</style>
