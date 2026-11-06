<template>
  <div class="tp-page stats-page liquid">
    <!-- ===== 页头 ===== -->
    <div class="tp-hero">
      <div class="tp-hero-main">
        <h2><span class="tp-hero-ico" v-html="svgIcon('graduation-cap')" />{{ t('差异化培养方案') }}</h2>
        <p>{{ t('参照真实高校培养方案模式：为不同年级（或年级+专业）配置专属的毕业学分与课程门数要求；未覆盖的学生自动使用全校默认要求。') }}</p>
      </div>
      <div class="tp-hero-stats">
        <div class="tp-hstat"><b>{{ items.length }}</b><span>{{ t('方案总数') }}</span></div>
        <div class="tp-hstat"><b>{{ coveredGrades }}</b><span>{{ t('覆盖年级') }}</span></div>
        <div class="tp-hstat"><b>{{ customMajors }}</b><span>{{ t('定制专业') }}</span></div>
        <div class="tp-hstat"><b>{{ maxTotal }}</b><span>{{ t('最高总学分') }}</span></div>
      </div>
    </div>

    <!-- ===== 工具栏 ===== -->
    <div class="tp-toolbar">
      <span class="tp-count">{{ t('共 {n} 个方案', { n: items.length }) }}</span>
      <span class="tp-dirty" v-if="dirty">{{ t('有未保存的修改') }}</span>
      <span class="tp-flex"></span>
      <button class="btn btn-outline btn-sm" @click="addPlan" :disabled="editingIdx !== null">+ {{ t('新增方案') }}</button>
      <button class="btn btn-primary btn-sm" :disabled="!dirty || editingIdx !== null" @click="saveAll">
        {{ saving ? t('保存中…') : t('保存全部修改') }}
      </button>
    </div>

    <!-- ===== 方案卡片网格 ===== -->
    <div class="tp-grid" v-if="items.length">
      <div v-for="(g, i) in items" :key="i" class="tp-card" :class="{ editing: editingIdx === i, generic: !g.major, custom: g.major }">
        <!-- 编辑态 -->
        <template v-if="editingIdx === i">
          <div class="tp-edit-head">
            <span class="tp-grade-badge edit">{{ g.grade ? g.grade + ' 级' : '——' }}</span>
            <el-input-number v-model="g.grade" :min="2000" :max="2100" size="small" controls-position="right" style="width:110px" />
          </div>
          <div class="tp-edit-grid">
            <label>{{ t('适用专业') }}<el-input v-model="g.major" size="small" :placeholder="t('留空 = 全年级通用')" /></label>
            <label>{{ t('方案名称') }}<el-input v-model="g.label" size="small" :placeholder="t('如 普通本科 / 专升本')" /></label>
          </div>
          <div class="tp-edit-grid four">
            <label>{{ t('必修') }}<el-input-number v-model="g.gradReqRequired" :min="0" :max="200" size="small" controls-position="right" style="width:100%" /></label>
            <label>{{ t('选修') }}<el-input-number v-model="g.gradReqElective" :min="0" :max="200" size="small" controls-position="right" style="width:100%" /></label>
            <label>{{ t('通识') }}<el-input-number v-model="g.gradReqGeneral" :min="0" :max="200" size="small" controls-position="right" style="width:100%" /></label>
            <label>{{ t('实践') }}<el-input-number v-model="g.gradReqPractice" :min="0" :max="200" size="small" controls-position="right" style="width:100%" /></label>
          </div>
          <div class="tp-edit-grid">
            <label>{{ t('最少课程数') }}<el-input-number v-model="g.gradReqCourses" :min="0" :max="200" size="small" controls-position="right" style="width:100%" /></label>
            <span class="tp-total-edit">{{ t('合计 {n} 学分', { n: totalOf(g) }) }}</span>
          </div>
          <div class="tp-actions">
            <button class="btn btn-success btn-sm" @click="confirmEdit(i)">{{ t('完成') }}</button>
            <button class="btn btn-danger btn-sm" @click="removePlan(i)">{{ t('删除') }}</button>
          </div>
        </template>

        <!-- 查看态 -->
        <template v-else>
          <div class="tp-card-head">
            <span class="tp-grade-badge">{{ g.grade }} 级</span>
            <div class="tp-card-title">
              <b>{{ g.label || t('定制培养方案') }}</b>
              <span class="tp-major">{{ g.major ? t('限 {m} 专业', { m: g.major }) : t('全年级通用') }}</span>
            </div>
            <span class="tp-total" :class="{ max: totalOf(g) === maxTotal }">{{ totalOf(g) }}<small>{{ t('学分') }}</small></span>
          </div>
          <div class="tp-cats">
            <div class="tp-cat" v-for="c in catDefs" :key="c.key">
              <b :style="{ color: c.color }">{{ g[c.key] ?? 0 }}</b>
              <span>{{ t(c.name) }}</span>
            </div>
            <div class="tp-cat">
              <b>{{ g.gradReqCourses ?? 0 }}</b>
              <span>{{ t('课程数') }}</span>
            </div>
          </div>
          <div class="tp-actions view">
            <button class="btn btn-outline btn-sm" @click="startEdit(i)">{{ t('编辑') }}</button>
            <button class="btn btn-ghost-danger btn-sm" @click="removePlan(i)">{{ t('删除') }}</button>
          </div>
        </template>
      </div>

      <!-- 新增占位卡 -->
      <button class="tp-card tp-add" @click="addPlan" v-if="editingIdx === null">
        <span class="tp-add-ico">＋</span>
        <span>{{ t('新增培养方案') }}</span>
        <small>{{ t('为某个年级（或年级+专业）定制毕业要求') }}</small>
      </button>
    </div>

    <div v-else class="tp-empty">
      <div class="tp-empty-ico" v-html="svgIcon('graduation-cap')" />
      <b>{{ t('还没有配置任何差异化方案') }}</b>
      <p>{{ t('所有学生当前使用全校默认要求。点击下方按钮为特定年级（如专升本）定制专属方案。') }}</p>
      <button class="btn btn-primary" @click="addPlan">+ {{ t('新增第一个方案') }}</button>
    </div>

    <!-- ===== 全校默认要求提示 ===== -->
    <div class="tp-default-hint">
      <span class="tp-hint-ico" v-html="svgIcon('alert-triangle')" />
      <span>{{ props.embedded
        ? t('未覆盖的学生按本页「规则与参数」Tab 中「毕业要求学分」的全校默认要求计算毕业进度。')
        : t('未覆盖的学生按「选课轮次 → 规则与参数 → 毕业要求学分」的全校默认要求计算毕业进度。') }}</span>
      <button v-if="!props.embedded" class="btn btn-ghost btn-sm" @click="$router.push('/rounds')">{{ t('前往配置默认要求 →') }}</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api'
import { t } from '@/i18n'
const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`

// 嵌入模式（选课轮次页 Tab 内）：隐藏跳转提示按钮，文案指向本页「规则与参数」Tab
const props = defineProps<{ embedded?: boolean }>()

interface Plan {
  grade: number | null
  major: string | null
  label: string | null
  gradReqRequired: number
  gradReqElective: number
  gradReqGeneral: number
  gradReqPractice: number
  gradReqCourses: number
}

const catDefs = [
  { key: 'gradReqRequired', name: '必修', color: '#0f6f52' },
  { key: 'gradReqElective', name: '选修', color: '#0284c7' },
  { key: 'gradReqGeneral', name: '通识', color: '#0369a1' },
  { key: 'gradReqPractice', name: '实践', color: '#b45309' },
]

const items = ref<Plan[]>([])
const editingIdx = ref<number | null>(null)
const dirty = ref(false)
const saving = ref(false)

const coveredGrades = computed(() => new Set(items.value.map(g => g.grade)).size)
const customMajors = computed(() => items.value.filter(g => g.major && g.major.trim()).length)
const maxTotal = computed(() => Math.max(0, ...items.value.map(totalOf)))
function totalOf(g: Plan): number {
  return (Number(g.gradReqRequired) || 0) + (Number(g.gradReqElective) || 0)
    + (Number(g.gradReqGeneral) || 0) + (Number(g.gradReqPractice) || 0)
}

async function load() {
  try {
    const r: any = await http.get('/system/grade-reqs')
    items.value = (r?.data || r || []).map((x: any) => ({ ...x }))
    dirty.value = false
  } catch (e: any) { ElMessage.error(e?.message || t('加载失败')) }
}

function addPlan() {
  items.value.push({
    grade: new Date().getFullYear(),
    major: '',
    label: '',
    gradReqRequired: 110, gradReqElective: 8, gradReqGeneral: 4, gradReqPractice: 2, gradReqCourses: 50,
  })
  editingIdx.value = items.value.length - 1
  dirty.value = true
}

function startEdit(i: number) { editingIdx.value = i }

function confirmEdit(i: number) {
  const g = items.value[i]
  if (!g.grade || g.grade < 2000) { ElMessage.warning(t('请填写年级（入学年份）')); return }
  if (g.major) g.major = g.major.trim()
  // 同「年级+专业」去重
  const dup = items.value.some((x, xi) => xi !== i && x.grade === g.grade && (x.major || '') === (g.major || ''))
  if (dup) { ElMessage.warning(t('该年级下已存在相同专业的方案')); return }
  editingIdx.value = null
  dirty.value = true
}

function removePlan(i: number) {
  const g = items.value[i]
  ElMessageBox.confirm(
    t('删除 {grade} 级{major}的培养方案？删除后该{scope}回退为全校默认要求。',
      { grade: g.grade, major: g.major ? '「' + g.major + '」专业的' : '', scope: g.major ? '专业' : '年级' }),
    t('删除培养方案'),
    { type: 'warning', confirmButtonText: t('删除'), cancelButtonText: t('取消') }
  ).then(() => {
    if (editingIdx.value === i) editingIdx.value = null
    items.value.splice(i, 1)
    dirty.value = true
  }).catch(() => {})
}

async function saveAll() {
  for (const g of items.value) {
    if (!g.grade || g.grade < 2000) { ElMessage.warning(t('存在未填写年级的方案')); return }
  }
  saving.value = true
  try {
    const r: any = await http.post('/system/grade-reqs', {
      items: items.value.map(g => ({
        grade: g.grade, major: g.major || '', label: g.label || '',
        gradReqRequired: g.gradReqRequired, gradReqElective: g.gradReqElective,
        gradReqGeneral: g.gradReqGeneral, gradReqPractice: g.gradReqPractice,
        gradReqCourses: g.gradReqCourses,
      })),
    })
    ElMessage.success(r?.message || t('培养方案已保存'))
    await load()
  } catch (e: any) { ElMessage.error(e?.message || t('保存失败')) }
  finally { saving.value = false }
}

onMounted(load)
</script>

<style scoped>
.tp-page { display: flex; flex-direction: column; gap: 16px; padding: 4px 0; }

/* ===== 页头 ===== */
.tp-hero {
  display: flex; justify-content: space-between; align-items: flex-end; gap: 20px; flex-wrap: wrap;
  padding: 24px 28px; border-radius: 18px;
  border: 1px solid rgba(43,177,135,.25);
  background:
    radial-gradient(500px 240px at 8% 0%, rgba(43,177,135,.12), transparent 60%),
    radial-gradient(460px 260px at 100% 100%, rgba(56,189,248,.10), transparent 60%),
    var(--card-bg);
  position: relative; overflow: hidden;
}
.tp-hero-main h2 { margin: 0 0 8px; font-size: 21px; font-weight: 800; color: var(--text); display: flex; align-items: center; gap: 9px; }
.tp-hero-ico { width: 22px; height: 22px; color: var(--primary); display: inline-flex; }
.tp-hero-ico :deep(.icon) { width: 22px; height: 22px; }
.tp-hero-main p { margin: 0; font-size: 13px; line-height: 1.7; color: var(--text-light); max-width: 660px; }
.tp-hero-stats { display: flex; gap: 24px; padding: 14px 22px; border-radius: 14px;
  background: color-mix(in srgb, var(--card-bg) 72%, transparent); border: 1px solid var(--border); }
.tp-hstat { display: flex; flex-direction: column; align-items: center; gap: 3px; min-width: 64px; }
.tp-hstat b { font-size: 21px; font-weight: 800; color: var(--text); font-variant-numeric: tabular-nums; }
.tp-hstat span { font-size: 11.5px; color: var(--text-light); }

/* ===== 工具栏 ===== */
.tp-toolbar { display: flex; align-items: center; gap: 12px; }
.tp-count { font-size: 13px; color: var(--text-light); }
.tp-dirty { font-size: 12px; font-weight: 700; color: #d97706; background: rgba(245,158,11,.1); border: 1px solid rgba(245,158,11,.35); padding: 3px 10px; border-radius: 999px; }
html.dark .tp-dirty { color: #fbbf24; }
.tp-flex { flex: 1; }

/* ===== 卡片网格 ===== */
.tp-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(340px, 1fr)); gap: 14px; }
.tp-card {
  position: relative; padding: 18px 20px; border-radius: 16px;
  border: 1px solid var(--border); background: var(--card-bg);
  display: flex; flex-direction: column; gap: 14px;
  transition: transform .25s cubic-bezier(.2,.7,.3,1), border-color .25s, box-shadow .25s;
}
.tp-card:hover { transform: translateY(-3px); border-color: rgba(43,177,135,.4); box-shadow: 0 12px 28px -12px rgba(43,177,135,.3); }
.tp-card.generic { border-top: 3px solid rgba(43,177,135,.55); }
.tp-card.custom { border-top: 3px solid rgba(56,189,248,.55); }
.tp-card.editing { border-color: var(--primary); box-shadow: 0 0 0 3px rgba(43,177,135,.12); }

/* 卡头 */
.tp-card-head { display: flex; align-items: flex-start; gap: 12px; }
.tp-grade-badge {
  flex-shrink: 0; width: 52px; height: 52px; border-radius: 14px;
  display: flex; align-items: center; justify-content: center;
  font-size: 13px; font-weight: 800; color: #fff;
  background: linear-gradient(135deg, #2bb187, #0d9488); box-shadow: 0 4px 12px rgba(43,177,135,.3);
}
.tp-grade-badge.edit { width: auto; height: auto; padding: 4px 10px; border-radius: 8px; background: rgba(43,177,135,.14); color: var(--primary); box-shadow: none; }
.tp-card-title { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
.tp-card-title b { font-size: 15px; font-weight: 800; color: var(--text); }
.tp-major { font-size: 12px; color: var(--text-light); }
.tp-total { margin-left: auto; font-size: 26px; font-weight: 800; color: var(--text); font-variant-numeric: tabular-nums; line-height: 1; }
.tp-total small { font-size: 11px; font-weight: 600; color: var(--text-light); margin-left: 3px; }
.tp-total.max { color: var(--primary); }

/* 学分四宫格 */
.tp-cats { display: grid; grid-template-columns: repeat(5, 1fr); gap: 8px; }
.tp-cat { text-align: center; padding: 9px 4px; border-radius: 10px; background: color-mix(in srgb, var(--text-light) 7%, transparent); }
.tp-cat b { display: block; font-size: 17px; font-weight: 800; font-variant-numeric: tabular-nums; color: var(--text); }
.tp-cat span { font-size: 11px; color: var(--text-light); }

/* 操作 */
.tp-actions { display: flex; justify-content: flex-end; gap: 8px; }
.tp-actions.view { border-top: 1px dashed var(--border); padding-top: 12px; }

/* 编辑态 */
.tp-edit-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.tp-edit-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.tp-edit-grid.four { grid-template-columns: repeat(4, 1fr); }
.tp-edit-grid label { display: flex; flex-direction: column; gap: 5px; font-size: 12px; font-weight: 600; color: var(--text-light); }
.tp-total-edit { align-self: center; font-size: 13px; font-weight: 800; color: var(--primary); font-variant-numeric: tabular-nums; }

/* 新增占位卡 */
.tp-add {
  border: 1.5px dashed var(--border); background: transparent; cursor: pointer;
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 6px;
  min-height: 200px; font-family: inherit; transition: all .25s;
}
.tp-add:hover { border-color: var(--primary); background: rgba(43,177,135,.05); transform: translateY(-3px); }
.tp-add-ico { font-size: 30px; font-weight: 300; color: var(--primary); line-height: 1; }
.tp-add span:nth-child(2) { font-size: 14px; font-weight: 700; color: var(--text); }
.tp-add small { font-size: 12px; color: var(--text-light); }

/* 空状态 */
.tp-empty { text-align: center; padding: 50px 20px; border-radius: 16px; border: 1.5px dashed var(--border);
  display: flex; flex-direction: column; align-items: center; gap: 8px; }
.tp-empty-ico { font-size: 44px; opacity: .3; color: var(--text-light); }
.tp-empty-ico :deep(.icon) { width: 44px; height: 44px; }
.tp-empty b { font-size: 15px; color: var(--text); }
.tp-empty p { margin: 0; font-size: 12.5px; color: var(--text-light); max-width: 420px; }

/* 默认要求提示 */
.tp-default-hint {
  display: flex; align-items: center; gap: 10px; flex-wrap: wrap;
  padding: 12px 16px; border-radius: 12px;
  background: rgba(245,158,11,.06); border: 1px dashed rgba(245,158,11,.35);
  font-size: 12.5px; color: var(--text-light);
}
.tp-hint-ico { width: 15px; height: 15px; color: #d97706; display: inline-flex; flex-shrink: 0; }
.tp-hint-ico :deep(.icon) { width: 15px; height: 15px; }
html.dark .tp-hint-ico { color: #fbbf24; }

@media (max-width: 900px) { .tp-grid { grid-template-columns: 1fr; } .tp-hero-stats { width: 100%; justify-content: space-around; } }
</style>
