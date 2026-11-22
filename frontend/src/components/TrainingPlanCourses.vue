<template>
  <div class="tpc-plan">
    <!-- 页头 -->
    <div class="tpc-hero">
      <div class="tpc-hero-main">
        <h2><span class="tpc-ico" v-html="icon('book-open')" />{{ t('培养方案 · 修读课程') }}</h2>
        <p>{{ t('按「专业 + 入学年级 + 学期」排定学生应修课程。教务系统据此在登录/打开课表时自动把必修课排入学生课表，学生无需也不能在选课中选择。') }}</p>
      </div>
      <div class="tpc-hero-stats">
        <div class="tpc-stat"><b>{{ stats.majors || 0 }}</b><span>{{ t('覆盖专业') }}</span></div>
        <div class="tpc-stat"><b>{{ stats.courses || 0 }}</b><span>{{ t('方案课程') }}</span></div>
        <div class="tpc-stat"><b>{{ stats.students || 0 }}</b><span>{{ t('覆盖学生') }}</span></div>
      </div>
    </div>

    <!-- 工具栏：专业/年级选择 + 批量排定 -->
    <div class="tpc-toolbar">
      <label class="tpc-field">{{ t('专业') }}
        <el-select v-model="major" filterable placeholder="选择专业" style="width:230px" @change="onMajorChange">
          <el-option v-for="m in majors" :key="m" :label="m" :value="m" />
        </el-select>
      </label>
      <label class="tpc-field">{{ t('入学年级') }}
        <el-select v-model="grade" style="width:120px" @change="loadItems">
          <el-option :label="t('全年级通用')" :value="null" />
          <el-option v-for="g in gradeOpts" :key="g" :label="g + ' 级'" :value="g" />
        </el-select>
      </label>
      <span class="tpc-sep"></span>
      <div class="tpc-btns">
        <button class="btn btn-outline btn-sm" @click="openPreview">{{ t('预览影响面') }}</button>
        <button class="btn btn-primary btn-sm" :disabled="batching" @click="batchEnsure">
          {{ batching ? t('排定中…') : t('一键批量排定') }}
        </button>
        <button class="btn btn-success btn-sm" :disabled="!major" @click="openAdd">+ {{ t('添加课程') }}</button>
      </div>
    </div>

    <!-- 学期分组课程清单 -->
    <div v-if="major" class="tpc-sem-list">
      <div v-for="sem in activeSemesters" :key="sem" class="tpc-sem-card">
        <div class="tpc-sem-head">
          <span class="tpc-sem-badge">{{ sem }}</span>
          <b>{{ semTitle(sem) }}</b><span class="tpc-sem-alt">{{ t('第 {n} 学期', { n: sem }) }}</span>
          <span class="tpc-sem-meta">{{ groupItems[sem]?.length || 0 }} {{ t('门') }} · {{ semCredit(sem) }} {{ t('学分') }}</span>
        </div>
        <div class="tpc-course-list" v-if="groupItems[sem]?.length">
          <div v-for="row in groupItems[sem]" :key="row.id" class="tpc-course">
            <span class="tpc-cat-tag" :class="catCls(row.category)">{{ row.category }}</span>
            <span class="tpc-cname">{{ row.courseName }}</span>
            <span class="tpc-ccode">{{ row.courseCode }}</span>
            <span class="tpc-credit">{{ row.credit ?? '—' }} {{ t('分') }}</span>
            <button class="tpc-del" :title="t('移除')" @click="removeItem(row)">×</button>
          </div>
        </div>
        <div v-else class="tpc-sem-empty">{{ t('该学期暂无课程') }}</div>
      </div>
    </div>
    <div v-else class="tpc-placeholder">
      <div class="tpc-ph-ico" v-html="icon('graduation-cap')" />
      <b>{{ t('请先在上方选择一个专业') }}</b>
      <p>{{ t('选择专业后可查看与编辑该专业各学期的必修课程清单。') }}</p>
    </div>

    <!-- 添加课程抽屉 -->
    <Teleport to="body">
      <div v-if="addOpen" class="tpc-mask" @click="addOpen=false">
        <div class="tpc-drawer" @click.stop>
          <div class="tpc-drawer-h">
            <div><b>{{ t('添加方案课程') }}</b><span>{{ major }} · {{ grade ? grade + ' 级' : t('全年级通用') }}</span></div>
            <button class="tpc-close" @click="addOpen=false">×</button>
          </div>
          <div class="tpc-drawer-body">
            <label class="tpc-field full">{{ t('搜索并选择课程') }}
              <el-select v-model="addCourseId" filterable remote :remote-method="searchCourses" :loading="searching"
                         placeholder="输入课程名/编码搜索课程库（仅必修与专业选修）" style="width:100%">
                <el-option v-for="c in addCourseOpts" :key="c.id" :label="c.name + '（' + c.category + '）'" :value="c.id" />
              </el-select>
            </label>
            <label class="tpc-field full">{{ t('开课学期') }}
              <el-select v-model="addSem" style="width:100%">
                <el-option v-for="s in activeSemesters" :key="s" :label="semTitle(s) + '（第 ' + s + ' 学期）'" :value="s" />
              </el-select>
            </label>
            <div class="tpc-tip">{{ t('提示：培养方案只容纳「必修 / 专业必修 / 公共必修 / 专业选修」类课程；公共选修属于学生自由选课，不在此列。') }}</div>
          </div>
          <div class="tpc-drawer-f">
            <button class="btn btn-primary btn-sm" :disabled="!addCourseId" @click="confirmAdd">{{ t('加入方案') }}</button>
            <button class="btn btn-outline btn-sm" @click="addOpen=false">{{ t('取消') }}</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 影响面预览抽屉 -->
    <Teleport to="body">
      <div v-if="previewOpen" class="tpc-mask" @click="previewOpen=false">
        <div class="tpc-drawer wide" @click.stop>
          <div class="tpc-drawer-h">
            <div><b>{{ t('批量排定影响面预览') }}</b><span>{{ t('按专业+年级统计，不写库') }}</span></div>
            <button class="tpc-close" @click="previewOpen=false">×</button>
          </div>
          <div class="tpc-drawer-body">
            <div class="tpc-preview-tip">{{ t('点「一键批量排定」后，系统会对下列每组学生按培养方案幂等补齐必修课（已存在的记录不重复插入）。') }}</div>
            <div class="data-table">
              <table>
                <thead><tr><th>{{ t('专业') }}</th><th>{{ t('年级') }}</th><th>{{ t('学生数') }}</th><th>{{ t('应修门数') }}</th><th>{{ t('应修学分') }}</th></tr></thead>
                <tbody>
                  <tr v-for="p in previewRows" :key="p.major + '-' + p.grade">
                    <td><b>{{ p.major }}</b></td>
                    <td>{{ p.grade ? p.grade + ' 级' : '全年级' }}</td>
                    <td>{{ p.students }}</td>
                    <td>{{ p.courses }}</td>
                    <td>{{ p.credits }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div v-if="!previewRows.length" class="tpc-sem-empty">{{ t('暂无可排定的方案') }}</div>
          </div>
          <div class="tpc-drawer-f">
            <button class="btn btn-primary btn-sm" :disabled="batching" @click="batchEnsure">
              {{ batching ? t('排定中…') : t('确认批量排定') }}
            </button>
            <button class="btn btn-outline btn-sm" @click="previewOpen=false">{{ t('关闭') }}</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api'
import { t } from '@/i18n'
import { semesterTitle } from '@/utils/semester'
const icon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`
// 当前选中专业是否专升本；专升本两年制 1~4 学期映射到大三上~大四下
const isZsb = computed(() => String(major.value || '').includes('专升本'))
const semTitle = (s: number) => semesterTitle(s, isZsb.value)
// 专升本两年制只显示 4 个学期；四年制显示 8 个
const activeSemesters = computed(() => isZsb.value ? semesters.slice(0, 4) : semesters)

const semesters = [1,2,3,4,5,6,7,8]
const gradeOpts = [2021,2022,2023,2024,2025,2026]

const major = ref<string>('')
const grade = ref<number | null>(null)
const majors = ref<string[]>([])
const items = ref<any[]>([])
const stats = ref<any>({})
const batching = ref(false)

const addOpen = ref(false)
const addCourseId = ref<number | null>(null)
const addSem = ref<number>(1)
const addCourseOpts = ref<any[]>([])
const searching = ref(false)
const previewOpen = ref(false)
const previewRows = ref<any[]>([])

const groupItems = computed(() => {
  const g: Record<number, any[]> = {}
  for (const s of semesters) g[s] = []
  for (const it of items.value) {
    const s = Number(it.semesterNo) || 1
    if (!g[s]) g[s] = []
    g[s].push(it)
  }
  for (const s of semesters) g[s].sort((a,b) => String(a.courseCode).localeCompare(String(b.courseCode)))
  return g
})
function semCredit(sem: number): number {
  return (groupItems.value[sem] || []).reduce((a: number, r: any) => a + (Number(r.credit) || 0), 0)
}
function catCls(c: string): string {
  if ((c || '').includes('公共必修')) return 'cat-ge'
  if ((c || '').includes('专业必修')) return 'cat-mj'
  if ((c || '').includes('专业选修')) return 'cat-el'
  return 'cat-ge'
}

async function loadStats() {
  try {
    const r: any = await http.get('/training-plan/stats')
    stats.value = r?.data || {}
  } catch (e: any) { /* 静默 */ }
}

async function loadMajors() {
  try {
    // 从方案接口拿全部专业（去重），不足则从学生列表补
    const r: any = await http.get('/training-plan/items')
    const all = r?.data || []
    const set = new Set<string>(all.map((x: any) => x.major).filter(Boolean))
    majors.value = Array.from(set).sort()
    if (majors.value.length && !major.value) major.value = majors.value[0]
  } catch (e: any) { /* 静默 */ }
}

async function loadItems() {
  if (!major.value) { items.value = []; return }
  try {
    const r: any = await http.get('/training-plan/items', { params: { major: major.value } })
    const all = r?.data || []
    // 前端按年级过滤：显示"通用" + 当前选中年级专属
    items.value = grade.value == null
      ? all.filter((x: any) => x.grade == null)
      : all.filter((x: any) => x.grade == null || x.grade === grade.value)
  } catch (e: any) { ElMessage.error(e?.message || t('加载方案失败')) }
}
function onMajorChange() { grade.value = null; loadItems() }

async function searchCourses(q: string) {
  searching.value = true
  try {
    const params: any = { page: 1, size: 30 }
    if (q) params.keyword = q
    const r: any = await http.get('/courses', { params })
    const list = r?.data?.items || r?.data?.list || r?.data || []
    addCourseOpts.value = list
      .filter((c: any) => (c.category || '').includes('必修') || (c.category || '').includes('专业选修'))
      .slice(0, 30)
  } catch (e: any) { /* 静默 */ }
  finally { searching.value = false }
}

async function openAdd() {
  addCourseId.value = null
  // 默认学期 = 第一个还没有安排课程的学期，符合"按学期往下排"的习惯
  const empty = activeSemesters.value.find((s: number) => !(groupItems.value[s]?.length))
  addSem.value = empty ?? 1
  addCourseOpts.value = []
  addOpen.value = true
  // 预载课程库前 30 门必修/专业选修，打开抽屉即可直接浏览挑选
  await searchCourses('')
}

async function confirmAdd() {
  if (!addCourseId.value) return
  try {
    const r: any = await http.post('/training-plan/items', {
      major: major.value, grade: grade.value, semesterNo: addSem.value, courseId: addCourseId.value,
    })
    if (r?.success === false) { ElMessage.warning(r?.message || t('添加失败')); return }
    ElMessage.success(t('已加入培养方案'))
    addOpen.value = false
    await Promise.all([loadItems(), loadStats(), loadMajors()])
  } catch (e: any) { ElMessage.error(e?.message || t('添加失败')) }
}

async function removeItem(row: any) {
  try {
    await ElMessageBox.confirm(t('确定从培养方案移除「{n}」？', { n: row.courseName }), t('移除课程'),
      { type: 'warning', confirmButtonText: t('移除'), cancelButtonText: t('取消') })
  } catch { return }
  try {
    await http.delete('/training-plan/items/' + row.id)
    ElMessage.success(t('已移除'))
    await Promise.all([loadItems(), loadStats()])
  } catch (e: any) { ElMessage.error(e?.message || t('移除失败')) }
}

async function openPreview() {
  try {
    const r: any = await http.get('/training-plan/preview')
    previewRows.value = r?.data || []
    previewOpen.value = true
  } catch (e: any) { ElMessage.error(e?.message || t('预览失败')) }
}

async function batchEnsure() {
  try {
    await ElMessageBox.confirm(
      t('将对全校学生按各自培养方案幂等补齐必修课，已在课表中的记录不会重复插入。确定继续？'),
      t('一键批量排定'), { type: 'warning', confirmButtonText: t('确定排定'), cancelButtonText: t('取消') })
  } catch { return }
  batching.value = true
  try {
    const r: any = await http.post('/training-plan/batch-ensure')
    ElMessage.success(r?.message || t('批量排定完成'))
    previewOpen.value = false
    await loadStats()
  } catch (e: any) { ElMessage.error(e?.message || t('批量排定失败')) }
  finally { batching.value = false }
}

onMounted(async () => { await Promise.all([loadStats(), loadMajors()]); await loadItems() })
</script>

<style scoped>
.tpc-plan { display: flex; flex-direction: column; gap: 16px; }
.tpc-hero { display: flex; justify-content: space-between; align-items: flex-end; gap: 20px; flex-wrap: wrap; padding: 24px 28px;
  border-radius: 18px; border: 1px solid rgba(43,177,135,.25);
  background: radial-gradient(500px 240px at 8% 0%, rgba(43,177,135,.12), transparent 60%),
              radial-gradient(460px 260px at 100% 100%, rgba(56,189,248,.10), transparent 60%), var(--card-bg); }
.tpc-hero-main h2 { margin: 0 0 8px; font-size: 21px; font-weight: 800; color: var(--text); display: flex; align-items: center; gap: 9px; }
.tpc-ico { width: 22px; height: 22px; color: var(--primary); display: inline-flex; }
.tpc-ico :deep(.icon) { width: 22px; height: 22px; }
.tpc-hero-main p { margin: 0; font-size: 13px; line-height: 1.7; color: var(--text-light); max-width: 680px; }
.tpc-hero-stats { display: flex; gap: 24px; padding: 14px 22px; border-radius: 14px; background: color-mix(in srgb, var(--card-bg) 72%, transparent); border: 1px solid var(--border); }
.tpc-stat { display: flex; flex-direction: column; align-items: center; gap: 3px; min-width: 64px; }
.tpc-stat b { font-size: 21px; font-weight: 800; color: var(--text); font-variant-numeric: tabular-nums; }
.tpc-stat span { font-size: 11.5px; color: var(--text-light); }

.tpc-toolbar { display: flex; align-items: flex-end; gap: 12px; flex-wrap: wrap; }
.tpc-field { display: flex; flex-direction: column; gap: 5px; font-size: 12px; font-weight: 600; color: var(--text-light); }
.tpc-field.full { width: 100%; }
.tpc-flex { flex: 1; }
.tpc-sep { width: 1px; align-self: stretch; background: var(--border); margin: 0 4px; }
/* 三个操作按钮成组右对齐：窄屏换行时整组一起掉到下一行右侧，不会散落到最左 */
.tpc-btns { display: flex; align-items: center; gap: 10px; margin-left: auto; flex-wrap: wrap; justify-content: flex-end; }

.tpc-sem-list { display: flex; flex-direction: column; gap: 12px; }
.tpc-sem-card { border: 1px solid var(--border); border-radius: 14px; background: var(--card-bg); overflow: hidden; }
.tpc-sem-head { display: flex; align-items: center; gap: 10px; padding: 12px 16px; background: color-mix(in srgb, var(--primary) 7%, transparent); border-bottom: 1px solid var(--border); }
.tpc-sem-badge { width: 30px; height: 30px; border-radius: 9px; display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #2bb187, #0d9488); color: #fff; font-size: 14px; font-weight: 800; }
.tpc-sem-head b { font-size: 14px; color: var(--text); }
.tpc-sem-alt { font-size: 11px; color: var(--text-lighter); margin-left: -4px; }
.tpc-sem-meta { margin-left: auto; font-size: 12px; color: var(--text-light); }
.tpc-course-list { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 8px; padding: 12px 16px; }
.tpc-course { display: flex; align-items: center; gap: 8px; padding: 9px 11px; border-radius: 10px; border: 1px solid var(--border);
  background: color-mix(in srgb, var(--text-light) 4%, transparent); }
.tpc-cat-tag { flex-shrink: 0; font-size: 10.5px; font-weight: 700; padding: 2px 7px; border-radius: 6px; white-space: nowrap; }
.cat-ge { color: #0f6f52; background: rgba(43,177,135,.14); }
.cat-mj { color: #0284c7; background: rgba(2,132,199,.14); }
.cat-el { color: #b45309; background: rgba(180,83,9,.14); }
html.dark .cat-ge { color: #5eead4; } html.dark .cat-mj { color: #7dd3fc; } html.dark .cat-el { color: #fcd34d; }
.tpc-cname { font-size: 12.5px; font-weight: 600; color: var(--text); flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tpc-ccode { font-size: 10.5px; color: var(--text-lighter); font-family: monospace; }
.tpc-credit { font-size: 11px; color: var(--text-light); white-space: nowrap; }
.tpc-del { flex-shrink: 0; width: 20px; height: 20px; border: none; border-radius: 6px; background: transparent; color: var(--text-light); cursor: pointer; font-size: 16px; line-height: 1; }
.tpc-del:hover { background: rgba(239,68,68,.14); color: #ef4444; }
.tpc-sem-empty { padding: 16px; font-size: 12px; color: var(--text-lighter); text-align: center; }
.tpc-placeholder { text-align: center; padding: 50px 20px; border-radius: 16px; border: 1.5px dashed var(--border); display: flex; flex-direction: column; align-items: center; gap: 8px; }
.tpc-ph-ico { font-size: 44px; opacity: .3; color: var(--text-light); }
.tpc-ph-ico :deep(.icon) { width: 44px; height: 44px; }
.tpc-placeholder b { font-size: 15px; color: var(--text); }
.tpc-placeholder p { margin: 0; font-size: 12.5px; color: var(--text-light); }

/* 抽屉 */
/* z-index 必须低于 Element Plus 弹出层(2000+)，否则抽屉里 select 的下拉选项被遮罩压住、无法点选 */
.tpc-mask { position: fixed; inset: 0; background: rgba(0,0,0,.4); z-index: 1999; display: flex; justify-content: flex-end; }
.tpc-drawer { width: 420px; max-width: 92vw; height: 100%; background: var(--card-bg); display: flex; flex-direction: column;
  box-shadow: -8px 0 32px rgba(0,0,0,.2); }
.tpc-drawer.wide { width: 620px; }
.tpc-drawer-h { display: flex; align-items: flex-start; justify-content: space-between; padding: 20px 22px; border-bottom: 1px solid var(--border); }
.tpc-drawer-h > div { display: flex; flex-direction: column; gap: 4px; }
.tpc-drawer-h b { font-size: 16px; color: var(--text); }
.tpc-drawer-h span { font-size: 12px; color: var(--text-light); }
.tpc-close { width: 28px; height: 28px; border: none; border-radius: 8px; background: color-mix(in srgb, var(--text-light) 12%, transparent); color: var(--text); cursor: pointer; font-size: 18px; line-height: 1; }
.tpc-drawer-body { flex: 1; overflow: auto; padding: 20px 22px; display: flex; flex-direction: column; gap: 16px; }
.tpc-drawer-f { padding: 16px 22px; border-top: 1px solid var(--border); display: flex; gap: 10px; justify-content: flex-end; }
.tpc-tip, .tpc-preview-tip { font-size: 12px; line-height: 1.6; color: var(--text-light); padding: 10px 12px; border-radius: 10px;
  background: rgba(56,189,248,.07); border: 1px solid rgba(56,189,248,.2); }
.tpc-drawer-body .data-table table { width: 100%; border-collapse: collapse; font-size: 12.5px; }
.tpc-drawer-body .data-table th, .tpc-drawer-body .data-table td { padding: 8px 10px; border-bottom: 1px solid var(--border); text-align: left; color: var(--text); }
.tpc-drawer-body .data-table th { color: var(--text-light); font-weight: 600; }
</style>
