<template>
  <div class="page-wrap stats-page liquid">
    <div class="section">
      <div class="section-header">
        <div class="section-title" v-html="svgIcon('book') + ' ' + t('课程管理（{n} 门）', { n: list.length })"></div>
        <div style="display:flex;gap:8px">
          <el-input v-model="kw" :placeholder="t('搜索课程名/教师')" size="default" clearable style="width:220px" @input="load" />
          <button class="btn btn-primary btn-sm" @click="openDialog(null)">{{ t('+ 新增课程') }}</button>
          <button class="btn btn-outline btn-sm" @click="prereqVisible = true">🔗 {{ t('先修链') }}</button>
        </div>
      </div>
      <div class="data-table">
        <table>
          <thead><tr><th>{{ t('课程名') }}</th><th>{{ t('类别') }}</th><th>{{ t('学分') }}</th><th>{{ t('教师') }}</th><th>{{ t('容量/已报') }}</th><th>{{ t('状态') }}</th><th>{{ t('操作') }}</th></tr></thead>
          <tbody>
            <tr v-for="c in paged" :key="c.id">
              <td><b>{{ c.name }}</b></td>
              <td><span class="course-cat" :class="'cat-' + c.category">{{ c.category }}</span></td>
              <td>{{ c.credit }}</td>
              <td>{{ c.teacherName || c.teacher }}</td>
              <td>{{ c.capacity }} / {{ c.stat?.total || 0 }}</td>
              <td><span class="status-tag" :class="c.oversubscribed ? 'status-pending' : 'status-approved'">{{ c.oversubscribed ? t('报名超额') : (c.full ? t('已满') : t('正常')) }}</span></td>
              <td>
                <button class="btn btn-outline btn-sm" @click="openDialog(c)">{{ t('编辑') }}</button>
                <button class="btn btn-ghost-danger btn-sm" @click="del(c)">{{ t('删除') }}</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="filtered.length > pageSize" class="pg-bar">
        <span class="pg-info">{{ t('共 {n} 条 · 第 {p} / {total} 页', { n: filtered.length, p: page, total: totalPages }) }}</span>
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
    <!-- ===== 先修链依赖图 ===== -->
    <el-dialog v-model="prereqVisible" :title="t('课程先修链')" width="640px">
      <div v-if="prereqPairs.length" class="pr-graph">
        <svg viewBox="0 0 560 200" class="pr-svg">
          <defs>
            <marker id="pr-arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto">
              <path d="M0,0 L8,4 L0,8 Z" fill="#2bb187" />
            </marker>
          </defs>
          <g v-for="(n, i) in prereqNodes" :key="'l' + i">
            <rect :x="30" :y="n.ly - 16" width="180" height="32" rx="8" class="pr-node" />
            <text :x="120" :y="n.ly + 5" text-anchor="middle" class="pr-label">{{ n.preName }}</text>
          </g>
          <g v-for="(n, i) in prereqNodes" :key="'r' + i">
            <rect :x="350" :y="n.ry - 16" width="180" height="32" rx="8" class="pr-node dep" />
            <text :x="440" :y="n.ry + 5" text-anchor="middle" class="pr-label">{{ n.depName }}</text>
            <line :x1="212" :y1="n.ly" :x2="346" :y2="n.ry" class="pr-edge" marker-end="url(#pr-arrow)" />
          </g>
        </svg>
        <div class="pr-tip">{{ t('箭头方向 = 先修 → 后续；未通过先修课的学生无法选修箭头指向的课程') }}</div>
      </div>
      <div v-else class="pr-empty">{{ t('尚未配置任何先修关系——在课程编辑中为课程设置「先修课程」即可建立依赖链') }}</div>
    </el-dialog>

    <el-dialog v-model="dlg" :title="form.id ? t('编辑课程') : t('新增课程')" width="520px">
      <el-form :model="form" label-position="top">
        <el-row :gutter="12">
          <el-col :span="24"><el-form-item :label="t('课程名称')"><el-input v-model="form.name" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('类别')">
            <el-select v-model="form.category"><el-option :label="t('必修')" value="必修" /><el-option :label="t('选修')" value="选修" /><el-option :label="t('公选')" value="公选" /></el-select>
          </el-form-item></el-col>
          <el-col :span="12" v-if="(form.category || '').includes('必修')"><el-form-item :label="t('开课学期（培养方案）')">
            <el-select v-model="form.semesterNo" clearable :placeholder="t('第几学期开课')" style="width:100%">
              <el-option v-for="n in 8" :key="n" :label="t('第 {n} 学期', { n })" :value="n" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('学分')"><el-input-number v-model.number="form.credit" :min="1" :max="6" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('授课教师')">
            <el-select v-model="form.teacherId" filterable :placeholder="t('选择教师')">
              <el-option v-for="t in teachers" :key="t.id"
              :label="t.name + ' (' + t.no + ') · ' + (teacherLoad[t.id] || 0) + '/' + teacherMax + ' 门' + ((teacherLoad[t.id] || 0) >= teacherMax && form.teacherId !== t.id ? ' · 已满' : '')"
              :disabled="(teacherLoad[t.id] || 0) >= teacherMax && form.teacherId !== t.id"
              :value="t.id" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('容量')"><el-input-number v-model.number="form.capacity" :min="5" :max="500" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('先修课程')">
            <el-select v-model="form.prereqId" clearable filterable :placeholder="t('无（未通过先修不可选本课）')" style="width:100%">
              <el-option v-for="c2 in list.filter(x => x.id !== form.id)" :key="c2.id" :label="c2.name" :value="c2.id" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('上课时间')">
            <el-select v-model="form.time" filterable allow-create default-first-option clearable
                       popper-class="course-time-popper"
                       :placeholder="t('选择标准排课时段（第1-10节，用于冲突检测与课表）')" style="width:100%">
              <el-option-group v-for="g in timeOptionGroups" :key="g.label" :label="t(g.label)">
                <el-option v-for="opt in g.options" :key="opt.value" :label="opt.label" :value="opt.value">
                  <div class="to-row">
                    <span class="to-main">{{ opt.value }}</span>
                    <span class="to-meta">{{ opt.meta }}</span>
                  </div>
                </el-option>
              </el-option-group>
            </el-select>
            <div v-if="scheduleHint" class="time-hint" :class="{ warn: scheduleWarn }" :title="form.time || ''">
              <div>{{ scheduleHint }}</div>
              <div v-if="form.time" class="time-hint-value">{{ form.time }}</div>
            </div>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('教室')">
            <el-select v-model="form.location" filterable clearable
                       :placeholder="t('从教室管理中选择教室')" style="width:100%">
              <el-option v-for="r in roomOptions" :key="r" :label="r" :value="r" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="24"><el-form-item :label="t('课程描述')"><el-input v-model="form.intro" type="textarea" :rows="2" /></el-form-item></el-col>
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
import { t } from '@/i18n'
import { buildTimeOptionGroups, describeSchedule } from '@/utils/classSchedule'
const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`
const list = ref<any[]>([])
const teachers = ref<any[]>([])
/* 教师课时负载：每师本学期授课门数（上限 teacher_max_courses，默认 3） */
const prereqVisible = ref(false)
/* 先修链：从课程数据提取（先修 → 依赖）配对 */
const prereqPairs = computed(() =>
  list.value
    .filter(c => c.prereqId)
    .map(c => ({
      preId: c.prereqId,
      preName: (list.value.find(x => x.id === c.prereqId) || {}).name || '先修课程',
      depName: c.name,
    }))
)
const prereqNodes = computed(() => {
  const n = prereqPairs.value.length
  if (!n) return []
  return prereqPairs.value.map((p, i) => {
    const step = Math.min(150, 170 / Math.max(1, n - 1))
    const y = n === 1 ? 100 : 30 + i * step
    return { ...p, ly: y, ry: y }
  })
})

const teacherLoad = ref<Record<number, number>>({})
const teacherMax = ref(3)
function teacherLabelOf(t: any): string {
  return ''
}
const kw = ref('')
const dlg = ref(false)
const form = reactive<any>({ id: null, name: '', category: '必修', credit: 3, teacherId: null, capacity: 60, time: '', location: '', intro: '', semesterNo: null, prereqId: null })
// 上课时间选项：按高校标准排课（节次 × 周学时模式）生成，与课表网格 / 后端冲突检测同口径
const BUILTIN_TIME_GROUPS = buildTimeOptionGroups()
const BUILTIN_TIME_VALUES = new Set(BUILTIN_TIME_GROUPS.flatMap(g => g.options.map(o => o.value)))
const timeOptionGroups = computed(() => {
  const groups = BUILTIN_TIME_GROUPS.map(g => ({ ...g }))
  // 编辑旧课程时，若数据库里存的是非标准时间串，仍保留为可选项，避免下拉显示空白
  const legacy = (form.time || '').trim()
  if (legacy && !BUILTIN_TIME_VALUES.has(legacy)) {
    groups.push({ label: '已有课程时间（自定义）', options: [{ value: legacy, label: legacy, meta: '沿用原有排课' }] })
  }
  return groups
})
// 选中后排课校验提示：周学时、学分对照、非标准节次 / 同天重复 / 晚间周末
const scheduleInfo = computed(() => describeSchedule(form.time || ''))
const scheduleWarn = computed(() => {
  const s = scheduleInfo.value
  if (!s) return false
  const raw = (form.time || '').trim()
  return s.nonStandard || s.sameDay ||
    (!!raw && !/^周[一二三四五六日]\s*\d{2}:\d{2}-\d{2}:\d{2}( \/ 周[一二三四五六日]\s*\d{2}:\d{2}-\d{2}:\d{2})*$/.test(raw))
})
const scheduleHint = computed(() => {
  const raw = (form.time || '').trim()
  if (!raw) return ''
  const s = scheduleInfo.value
  if (!s) return t('格式无法识别，应为「周一 08:00-09:40」，多次课用 / 分隔')
  let msg = t('每周 {h} 学时 · {n} 次连堂 · 当前学分 {c}', { h: s.hours, n: s.count, c: form.credit ?? '-' })
  if (s.nonStandard) msg += t(' · 非标准节次，课表无法定位，请从下拉选择')
  if (s.sameDay) msg += t(' · 同一天排了两次课，请检查')
  if (s.nightOrWeekend && !s.nonStandard) msg += t(' · 晚间/周末一般安排公选、重修或辅修课')
  return msg
})
// 教室选项：来自教室管理（同步课程真实使用的楼栋教室）
const rooms = ref<string[]>([])
const roomOptions = computed(() => rooms.value)
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
    const [c, t, cfg, cr] = await Promise.all([
      http.get('/courses'), http.get('/courses/teachers'),
      http.get('/system/config').catch(() => null),
      http.get('/classrooms').catch(() => null),
    ])
    teacherMax.value = Number(cfg?.data?.teacherMaxCourses) || 3
    const loadMap: Record<number, number> = {}
    for (const course of (c?.data || [])) {
      if (course.teacherId) loadMap[course.teacherId] = (loadMap[course.teacherId] || 0) + 1
    }
    teacherLoad.value = loadMap
    list.value = c?.data || []
    teachers.value = t?.data || []
    rooms.value = (cr?.data || []).map((x: any) => x.name).filter(Boolean)
  } catch {}
}
function openDialog(c: any) { Object.assign(form, c || { id: null, name: '', category: '必修', credit: 3, teacherId: null, capacity: 60, time: '', location: '', intro: '', semesterNo: null, prereqId: null }); dlg.value = true }
async function save() {
  try { if (form.id) await http.put(`/courses/${form.id}`, form); else await http.post('/courses', form); ElMessage.success(t('保存成功')); dlg.value = false; load() }
  catch (e: any) { ElMessage.error(e?.message || t('保存失败')) }
}
async function del(c: any) {
  try { await ElMessageBox.confirm(t('确定删除课程「{name}」？', { name: c.name }), t('提示'), { type: 'warning', confirmButtonText: t('确定'), cancelButtonText: t('取消') }) } catch { return }
  try { await http.delete(`/courses/${c.id}`); ElMessage.success(t('已删除')); load() } catch (e: any) { ElMessage.error(e?.message || t('删除失败')) }
}
onMounted(load)
</script>

<style scoped>.page-wrap { padding: 4px 0; }/* ===== 先修链依赖图 ===== */
.pr-svg { width: 100%; height: auto; }
.pr-node { fill: rgba(43,177,135,.1); stroke: rgba(43,177,135,.5); }
.pr-node.dep { fill: rgba(56,189,248,.1); stroke: rgba(56,189,248,.5); }
.pr-label { font-size: 12.5px; fill: var(--text); font-weight: 600; }
.pr-edge { stroke: #2bb187; stroke-width: 1.6; stroke-dasharray: 5 3; fill: none; }
.pr-tip { margin-top: 8px; font-size: 12px; color: var(--text-light); text-align: center; }
.pr-empty { text-align: center; padding: 30px; color: var(--text-light); font-size: 13px; }
.time-hint { margin-top: 5px; font-size: 11px; line-height: 1.5; color: var(--text-light); }
.time-hint.warn { color: #e0872b; }
.time-hint-value { margin-top: 1px; font-variant-numeric: tabular-nums; word-break: break-all; opacity: .82; }
</style>

<!-- 上课时间下拉由 Element Plus 传送到 body，需全局样式加宽并排版节次说明 -->
<style>
.course-time-popper.el-select-dropdown,
.el-select-dropdown.course-time-popper { max-width: 660px !important; }
.course-time-popper .el-select-group__title { font-size: 12px; font-weight: 700; color: var(--primary, #2bb187); }
.course-time-popper .el-select-dropdown__item { height: auto; min-height: 34px; line-height: 1.5; padding: 6px 12px; }
.course-time-popper .to-row { display: flex; align-items: baseline; justify-content: space-between; gap: 14px; }
.course-time-popper .to-main { white-space: nowrap; font-variant-numeric: tabular-nums; }
.course-time-popper .to-meta { flex-shrink: 0; font-size: 11px; color: var(--text-light, #94a3b8); white-space: nowrap; }
@media (max-width: 700px) {
  .course-time-popper .to-row { flex-direction: column; gap: 0; }
  .course-time-popper .to-meta { text-align: left; }
}
</style>