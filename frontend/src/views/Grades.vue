<template>
  <div class="zx-page stats-page liquid">
    <div class="page-head">
      <h2>{{ t('📊 成绩查询') }}</h2>
      <button class="btn btn-outline btn-sm" @click="printTranscript">{{ t('🖨 导出成绩单 PDF') }}</button>
    </div>

    <!-- 学期筛选行（胶囊式 + 口径说明） -->
    <div class="gr-filter">
      <span class="gf-label">{{ t('学期') }}</span>
      <el-select class="sem-pill" v-model="selectedSemester" style="width:280px">
        <template #prefix><span class="sem-cal" v-html="svgIcon('calendar')" /></template>
        <el-option v-for="s in semesterOptions" :key="String(s.id)" :label="s.label" :value="s.id" />
      </el-select>
      <small class="gf-note">{{ scopeText }}</small>
    </div>

    <!-- 接口失败：错误提示 + 重试 -->
    <div v-if="loadError" class="gr-error">
      ⚠ {{ loadError }}
      <button class="btn btn-ghost btn-sm" @click="load">{{ t('重试') }}</button>
    </div>

    <!-- 加载骨架 -->
    <div v-if="loading && !grades.length" class="gr-loading" v-loading="true" :element-loading-text="t('成绩加载中…')" />

    <!-- 空状态 -->
    <el-empty v-else-if="!loading && !grades.length && !loadError" :description="t('暂无已录取课程的成绩记录')" />

    <template v-if="grades.length">
      <!-- ===== 成绩概览统计（口径分明：GPA=全部学期累计，其余=当前选中学期） ===== -->
      <div class="gr-stats-row">
        <div class="gr-stat gpa">
          <b>{{ gpa.value == null ? '—' : animGpa }}</b>
          <span>{{ t('累计 GPA') }}</span>
          <small>{{ t('全部学期 · 学分加权 · 4.0 制') }}</small>
        </div>
        <div class="gr-stat">
          <b :class="avgCls">{{ scoredRows.length ? animAvg : '—' }}</b>
          <span>{{ t('平均分') }}</span>
          <small>{{ selectedSemesterName }} · {{ t('{n} 门已录入', { n: scoredCount }) }}</small>
        </div>
        <div class="gr-stat">
          <b class="good">{{ scoredRows.length ? animMax : '—' }}</b>
          <span>{{ t('最高分') }}</span>
          <small>{{ selectedSemesterName }} · {{ t('仅已录入') }}</small>
        </div>
        <div class="gr-stat">
          <b :class="minCls">{{ scoredRows.length ? animMin : '—' }}</b>
          <span>{{ t('最低分') }}</span>
          <small>{{ selectedSemesterName }} · {{ t('未录入不计入') }}</small>
        </div>
        <div class="gr-stat">
          <b>{{ animScored }} <i class="gr-of">/ {{ grades.length }}</i></b>
          <span>{{ t('已录入 / 总课程') }}</span>
          <small>{{ t('已获学分 {n}（成绩≥60）', { n: animEarned }) }}</small>
        </div>
        <div class="gr-stat gpa ranking">
          <b>{{ rankText }}</b>
          <span>{{ t('专业排名') }}</span>
          <small>{{ rankSub }}</small>
        </div>
      </div>

      <!-- ===== 类别均分 + 趋势/单轮概览 ===== -->
      <div class="gr-head">
        <div class="gr-card">
          <div class="gr-title">{{ t('类别均分') }} <small>{{ t('按当前筛选范围 · 虚线：及格 60 / 优秀 90') }}</small></div>
          <div class="gr-cats">
            <div v-for="g in catRows" :key="g.key" class="gr-cat-row">
              <span class="gr-cat-label">{{ g.key }}</span>
              <div class="gr-cat-track">
                <i :class="g.cls" :style="{ width: Math.min(100, g.avg) + '%' }" />
                <em class="tick" style="left: 60%" />
                <em class="tick" style="left: 90%" />
              </div>
              <span class="gr-cat-val">{{ g.avg }}<small class="gr-cat-n">{{ g.n }} {{ t('门') }}</small></span>
            </div>
            <div v-if="!catRows.length" class="gr-nodata">{{ t('暂无有效成绩') }}</div>
          </div>
        </div>
        <div class="gr-card">
          <div class="gr-title">{{ t('学期成绩概览') }} <small>{{ selectedSemesterName }}</small></div>
          <div class="gr-single">
            <div class="gs-item"><b :class="avgCls">{{ scoredRows.length ? animAvg : '—' }}</b><span>{{ t('学期平均分') }}</span></div>
            <div class="gs-item"><b>{{ semesterGpaText === '—' ? '—' : animSemGpa }}</b><span>{{ t('学期 GPA') }}</span></div>
            <div class="gs-item"><b>{{ animScored }}</b><span>{{ t('已录入课程') }}</span></div>
          </div>
          <div class="t-note">{{ t('完成更多学期后，这里将展示各学期均分与 GPA 的变化趋势') }}</div>
        </div>
      </div>

      <!-- ===== 成绩明细 ===== -->
      <el-table v-if="filtered.length" :data="filtered" v-loading="loading" style="width:100%" row-key="courseId">
        <el-table-column prop="courseName" :label="t('课程名')" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.courseName }}
            <el-tag v-if="row.examType === 'makeup'" size="small" type="warning" effect="plain" class="gr-exam-tag">
              {{ t('补考') }}<template v-if="row.attempt > 1"> · {{ row.attempt }}</template>
            </el-tag>
            <el-tag v-else-if="row.examType === 'retake'" size="small" type="danger" effect="plain" class="gr-exam-tag">
              {{ t('重修') }}<template v-if="row.attempt > 1"> · {{ row.attempt }}</template>
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="teacherName" :label="t('教师')" width="110" />
        <el-table-column prop="category" :label="t('类型')" width="95" align="center" />
        <el-table-column prop="credit" :label="t('学分')" width="65" align="center" />
        <el-table-column :label="t('成绩')" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="isEntered(row)" :type="scoreTag(row.totalScore)" size="small">{{ row.totalScore }}</el-tag>
            <span v-else class="muted">{{ t('未录入') }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('状态')" width="118" align="center">
          <template #default="{ row }">
            <template v-if="isEntered(row)">
              <span class="st-text" :class="'st-' + scoreTag(row.totalScore)">{{ statusText(row.totalScore) }}</span>
              <span v-if="Number(row.totalScore) < 60" class="rt-flag" :class="row.retaking ? 'now' : 'todo'">
                {{ row.retaking ? t('重修中') : t('待补考') }}
              </span>
            </template>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('成绩定位')" width="130">
          <template #default="{ row }">
            <div v-if="isEntered(row)" class="gr-pos">
              <div class="gr-pos-track">
                <i :style="{ left: Math.min(100, Math.max(0, Number(row.totalScore))) + '%' }" />
                <em class="tick" style="left: 60%" /><em class="tick" style="left: 90%" />
              </div>
            </div>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('绩点')" width="75" align="center">
          <template #default="{ row }">{{ isEntered(row) ? scoreToGpa(Number(row.totalScore), row.examType).toFixed(2) : '—' }}</template>
        </el-table-column>
        <el-table-column :label="t('明细')" min-width="150">
          <template #default="{ row }">
            <span v-if="row.regularScore != null || row.finalScore != null" class="muted">
              {{ t('平时') }} {{ row.regularScore ?? '—' }} · {{ t('期末') }} {{ row.finalScore ?? '—' }}
            </span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="grades.length && !filtered.length" :description="t('该学期暂无成绩记录（成绩数据归属于当前学期）')" />
      <div class="gr-footnote">{{ t('口径：平均分/最高/最低仅统计已录入成绩（未录入不计入、不当 0 分）；GPA = Σ(绩点×课程学分) ÷ Σ学分，绩点换算与后端一致（90→4.0 · 85→3.7 · 80→3.3 · 75→3.0 · 70→2.7 · 65→2.3 · 60→2.0 · <60→0），补考通过按 60 分档封顶 2.0 计绩点、重修按实际分数；总评 = 平时×40% + 期末×60%；定位条 60 及格 / 90 优秀。挂科课程可补考、补考不过可重修，重修通过即获得学分。') }}</div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api'
import { useCountUp } from '@/utils/useCountUp'
import { t } from '@/i18n'

interface Grade {
  courseId: number; courseName: string; teacherName: string; category: string
  credit: number; regularScore: number | null; finalScore: number | null; totalScore: number | null
  round?: number
  examType?: string | null; attempt?: number | null
  failed?: boolean; retaking?: boolean
  semesterNo?: number | null; semesterLabel?: string | null
}

const loading = ref(false)
const loadError = ref('')
const grades = ref<Grade[]>([])

/* ===== 学期筛选：成绩归属修读学期（真实高校口径）——每学期成绩在该学期查询 ===== */
interface SemOpt { id: number; label: string }
const semesterOptions = ref<SemOpt[]>([])
const currentSemesterNo = ref<number | null>(null)
const selectedSemester = ref<number | null>(null)

const selectedSemesterName = computed(() =>
  semesterOptions.value.find(s => s.id === selectedSemester.value)?.label || t('全部学期'))
const filtered = computed(() =>
  selectedSemester.value == null || selectedSemester.value === 'all'
    ? grades.value
    : grades.value.filter(g => (g.semesterNo ?? -1) === selectedSemester.value))
const scopeText = computed(() =>
  selectedSemester.value === currentSemesterNo.value
    ? t('当前学期 · 累计 GPA 统计全部学期')
    : t('历史学期 · 累计 GPA 统计全部学期'))

/** 已录入 = totalScore 有效，且不是「成绩行存在但平时/期末均未填」（后端此时会算出 0 分，非真实 0 分） */
function isEntered(g: Grade): boolean {
  if (g.totalScore == null || g.totalScore === '' || isNaN(Number(g.totalScore))) return false
  if (Number(g.totalScore) === 0 && g.regularScore == null && g.finalScore == null) return false
  return true
}

/* ===== 统计（只统计已录入成绩；0 分若平时/期末齐全视为真实 0 分） ===== */
const scoredRows = computed(() => filtered.value.filter(isEntered))
const allScored = computed(() => grades.value.filter(isEntered))
const scoredCount = computed(() => scoredRows.value.length)
const avgScore = computed(() => {
  const g = scoredRows.value
  return g.length ? (g.reduce((s, x) => s + Number(x.totalScore), 0) / g.length).toFixed(1) : '—'
})
const maxScore = computed(() => {
  const g = scoredRows.value
  return g.length ? Math.max(...g.map(x => Number(x.totalScore))) : '—'
})
const minScore = computed(() => {
  const g = scoredRows.value
  return g.length ? Math.min(...g.map(x => Number(x.totalScore))) : '—'
})
const avgCls = computed(() => gradeCls(Number(avgScore.value)))
const minCls = computed(() => (scoredRows.value.length && Number(minScore.value) < 60 ? 'bad' : ''))
function gradeCls(n: number) {
  if (isNaN(n)) return ''
  if (n >= 90) return 'gold'
  if (n >= 80) return 'good'
  if (n >= 60) return 'mid'
  return 'bad'
}
/** 已获学分：已录入且总评 ≥60 的课程学分合计（学分来自真实课程数据） */
const earnedCredits = computed(() =>
  allScored.value.filter(g => Number(g.totalScore) >= 60).reduce((s, g) => s + (g.credit || 0), 0)
)

/** 与后端 GradeService.scoreToGpa 一致的 4.0 制换算（90→4 85→3.7 80→3.3 75→3 70→2.7 65→2.3 60→2 <60→0）。
    补考（makeup）通过一律按 60 分档封顶 2.0 计绩点——真实高校「补考通过不刷绩点」规则；重修按实际分数 */
function scoreToGpa(s: number, examType?: string | null): number {
  const p = s >= 90 ? 4.0 : s >= 85 ? 3.7 : s >= 80 ? 3.3 : s >= 75 ? 3.0
    : s >= 70 ? 2.7 : s >= 65 ? 2.3 : s >= 60 ? 2.0 : 0.0
  return examType === 'makeup' ? Math.min(2.0, p) : p
}

/** 累计 GPA：全部轮次已录入成绩、按真实课程学分加权（后端 gpaOfStudent 硬编码学分 3 不可靠，前端按真实学分计算） */
const gpa = computed<number | null>(() => {
  const scored = allScored.value.filter(g => g.credit > 0)
  if (!scored.length) return null
  const weighted = scored.reduce((s, g) => s + scoreToGpa(Number(g.totalScore), g.examType) * g.credit, 0)
  const credits = scored.reduce((s, g) => s + g.credit, 0)
  return credits > 0 ? Math.round((weighted / credits) * 100) / 100 : null
})
const gpaText = computed(() => gpa.value != null ? gpa.value.toFixed(2) : '—')

/* ===== 类别均分：按课程类别聚合，展示有效成绩数量 ===== */
const CAT_KEYS = ['必修', '选修', '通识', '实践']
const CAT_CLS: Record<string, string> = { '必修': 'c-bx', '选修': 'c-xx', '通识': 'c-ts', '实践': 'c-sj' }
function catOf(c: string) {
  const s = c || ''
  if (s.includes('通识')) return '通识'
  if (s.includes('实践')) return '实践'
  if (s.includes('选修')) return '选修'
  return '必修'
}
const catRows = computed(() => {
  const map: Record<string, number[]> = {}
  for (const g of scoredRows.value) {
    const k = catOf(g.category)
    ;(map[k] = map[k] || []).push(Number(g.totalScore))
  }
  return CAT_KEYS
    .filter(k => map[k]?.length)
    .map(k => ({
      key: k,
      cls: CAT_CLS[k],
      n: map[k].length,
      avg: (map[k].reduce((s, v) => s + v, 0) / map[k].length).toFixed(1),
    }))
})

/* ===== 学期 GPA：当前选中学期的学分加权 GPA ===== */
const semesterGpaText = computed(() => {
  const scored = scoredRows.value.filter(g => g.credit > 0)
  if (!scored.length) return '—'
  const weighted = scored.reduce((s, g) => s + scoreToGpa(Number(g.totalScore), g.examType) * g.credit, 0)
  const credits = scored.reduce((s, g) => s + g.credit, 0)
  return credits > 0 ? (weighted / credits).toFixed(2) : '—'
})

/* ===== 统计卡数字滚动（GPA 2 位小数、均分 1 位，'—' 时保持占位） ===== */
const animGpa = useCountUp(() => gpa.value ?? 0, { decimals: 2 })
const animAvg = useCountUp(() => (scoredRows.value.length ? Number(avgScore.value) : 0), { decimals: 1 })
const animMax = useCountUp(() => (scoredRows.value.length ? Number(maxScore.value) : 0))
const animMin = useCountUp(() => (scoredRows.value.length ? Number(minScore.value) : 0))
const animScored = useCountUp(() => scoredCount.value)
const animEarned = useCountUp(() => earnedCredits.value)
const animSemGpa = useCountUp(() => (semesterGpaText.value === '—' ? 0 : Number(semesterGpaText.value)), { decimals: 2 })

function scoreTag(s: number) {
  if (s >= 90) return 'success'
  if (s >= 60) return 'primary'
  return 'danger'
}
function statusText(s: number) {
  if (s >= 90) return t('优秀')
  if (s >= 80) return t('良好')
  if (s >= 70) return t('中等')
  if (s >= 60) return t('及格')
  return t('不及格')
}

/* ===== 专业排名（同专业学生按学分加权 GPA） ===== */
const rank = ref<number | null>(null)
const rankTotal = ref(0)
const rankPct = ref<number | null>(null)
const rankMajor = ref('')
const rankText = computed(() => (rank.value != null && rankTotal.value > 0) ? rank.value : '—')
const rankSub = computed(() => {
  if (rank.value == null || rankTotal.value <= 0) return rankMajor.value ? t('{m} · 暂无可比数据', { m: rankMajor.value }) : t('暂无同专业数据')
  const pct = rankPct.value != null ? ' · ' + t('前 {p}%', { p: rankPct.value }) : ''
  return t('{m} · 共 {n} 人', { m: rankMajor.value, n: rankTotal.value }) + pct
})
async function loadRanking() {
  try {
    const r: any = await http.get('/graduation/ranking')
    rank.value = r?.data?.rank ?? null
    rankTotal.value = r?.data?.total ?? 0
    rankPct.value = r?.data?.percentile ?? null
    rankMajor.value = r?.data?.major || ''
  } catch {}
}

/** 导出成绩单 PDF：打印窗口单开全部学期成绩（浏览器「另存为 PDF」） */
async function printTranscript() {
  const rows = grades.value.filter(isEntered)
  if (!rows.length) { ElMessage.warning(t('暂无成绩可导出')); return }
  const stu = JSON.parse(localStorage.getItem('zhixue_user') || '{}')
  const bySem = new Map()
  for (const g of rows) {
    const no = g.semesterNo ?? 0
    if (!bySem.has(no)) bySem.set(no, { label: g.semesterLabel || ('第 ' + no + ' 学期'), list: [] })
    bySem.get(no).list.push(g)
  }
  const gpaTxt = gpa.value != null ? gpa.value.toFixed(2) : '—'
  const earned = earnedCredits.value
  const w = window.open('', '_blank')
  if (!w) { ElMessage.error(t('浏览器拦截了弹窗，请允许弹出窗口')); return }
  const esc = (v: any) => String(v ?? '')
  const semHtml = [...bySem.entries()].sort((a, b) => b[0] - a[0]).map(([no, grp]) => `
    <h3>${esc(grp.label)}</h3>
    <table>
      <thead><tr><th>课程</th><th>类型</th><th>学分</th><th>平时</th><th>期末</th><th>总评</th><th>绩点</th></tr></thead>
      <tbody>${grp.list.map(g => `
        <tr>
          <td>${esc(g.courseName)}</td><td>${esc(g.category)}</td><td>${g.credit ?? ''}</td>
          <td>${g.regularScore ?? '—'}</td><td>${g.finalScore ?? '—'}</td><td>${g.totalScore ?? '—'}</td>
          <td>${scoreToGpa(Number(g.totalScore), g.examType).toFixed(2)}</td>
        </tr>`).join('')}</tbody>
    </table>`).join('')
  w.document.write(`<!DOCTYPE html><html lang="zh"><head><meta charset="utf-8"><title>成绩单</title>
    <style>
      body { font-family: 'Microsoft YaHei', sans-serif; padding: 32px; color: #1f2937; }
      h1 { text-align: center; font-size: 22px; margin: 0 0 4px; }
      .meta { text-align: center; color: #6b7280; font-size: 13px; margin-bottom: 20px; }
      h3 { font-size: 15px; margin: 18px 0 8px; color: #0f766e; }
      table { width: 100%; border-collapse: collapse; font-size: 12.5px; margin-bottom: 8px; }
      th, td { border: 1px solid #d1d5db; padding: 6px 10px; text-align: left; }
      th { background: #f0faf7; }
      .sum { margin-top: 16px; font-size: 14px; }
      .sum b { color: #0f766e; }
      @media print { body { padding: 0; } }
    </style></head><body>
    <h1>智学选课 · 学生成绩单</h1>
    <div class="meta">姓名：${esc(stu.name)}　学号：${esc(stu.no)}　专业：${esc(stu.major)}　打印时间：${new Date().toLocaleString('zh-CN')}</div>
    ${semHtml}
    <div class="sum">累计 GPA：<b>${gpaTxt}</b>　已获学分：<b>${earned}</b>　已通过课程：<b>${rows.filter(g => Number(g.totalScore) >= 60).length} 门</b></div>
    <script>window.onload = () => setTimeout(() => window.print(), 300)<\/script>
    </body></html>`)
  w.document.close()
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const [r, me] = await Promise.all([
      http.get('/enrollments/scores'),
      http.get('/auth/me').catch(() => null),
    ])
    // 学期序单一事实来源：后端（含学期滚动 offset）；失败时回退本地推算
    try {
      const ms: any = await http.get('/enrollments/my-semester')
      currentSemesterNo.value = ms?.data?.semesterNo ?? null
    } catch {
      let myGrade = 0
      try { myGrade = Number(JSON.parse(localStorage.getItem('zhixue_user') || '{}').grade) || 0 } catch {}
      if (myGrade < 2000) {
        const mu = me?.data?.data || me?.data || {}
        myGrade = Number(mu.grade) || 0
      }
      const today = new Date()
      const y = today.getFullYear(), m = today.getMonth() + 1
      let sem = 1
      if (myGrade >= 2000) {
        sem = Math.max(1, m >= 8 ? (y - myGrade) * 2 + 1 : (m === 1 ? (y - myGrade) * 2 - 1 : (y - myGrade) * 2))
      }
      currentSemesterNo.value = sem
    }
    grades.value = (r?.data?.list || r?.data || []) as Grade[]
    // 学期选项：从成绩的修读学期聚合（学期序降序），当前学期标注「进行中」
    const semSet = new Map<number, string>()
    for (const g of grades.value) {
      if (g.semesterNo != null && !semSet.has(g.semesterNo)) {
        semSet.set(g.semesterNo, g.semesterLabel || `第 ${g.semesterNo} 学期`)
      }
    }
    const list: SemOpt[] = [...semSet.entries()]
      .sort((a, b) => b[0] - a[0])
      .map(([no, label]) => ({
        id: no,
        label: no === currentSemesterNo.value ? `${label}（${t('进行中')}）` : label,
      }))
    // 当前学期（尚无成绩也保留选项）：学年学期名与后端 semesterLabel 同公式
    const cur = currentSemesterNo.value
    let myGrade = 0
    try { myGrade = Number(JSON.parse(localStorage.getItem('zhixue_user') || '{}').grade) || 0 } catch {}
    if (myGrade < 2000) {
      const mu = me?.data?.data || me?.data || {}
      myGrade = Number(mu.grade) || 0
    }
    if (cur != null && myGrade >= 2000 && !list.some(o => o.id === cur)) {
      const startYear = myGrade + Math.floor((cur - 1) / 2)
      const label = `${startYear}-${startYear + 1}学年${cur % 2 === 1 ? '第一' : '第二'}学期（${t('进行中')}）`
      list.unshift({ id: cur, label })
    }
    semesterOptions.value = [{ id: 'all', label: t('全部学期总览') }, ...list]
    // 默认选中当前学期；当前学期尚无成绩时选最近一个有成绩的学期
    selectedSemester.value = list.some(o => o.id === currentSemesterNo.value)
      ? currentSemesterNo.value
      : 'all'
  } catch {
    loadError.value = t('成绩数据加载失败（服务可能刚重启），请重试')
  }
  loading.value = false
  loadRanking()
}
onMounted(load)
</script>

<style scoped>
.zx-page { display: flex; flex-direction: column; gap: 14px; }
.page-head { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 10px; }
.page-head h2 { margin: 0; color: var(--text); font-size: 20px; }
.gr-filter { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.gf-label { font-size: 12px; color: var(--text-light); }
.gf-note { font-size: 12px; color: var(--text-lighter); }

.gr-error {
  display: flex; align-items: center; gap: 12px;
  background: rgba(239,68,68,.08); border: 1px solid rgba(239,68,68,.3);
  border-radius: 10px; padding: 10px 16px; color: #f87171; font-size: 13px;
}
.gr-loading { min-height: 160px; background: var(--card); border: 1px solid var(--border); border-radius: 13px; }

:deep(.el-table) {
  --el-table-tr-bg-color: var(--card);
  --el-table-header-bg-color: var(--border);
  --el-table-border-color: var(--border);
  --el-table-text-color: var(--text);
  --el-table-header-text-color: var(--primary);
  background: var(--card); border-radius: 8px; overflow: hidden;
}
.muted { color: var(--text-lighter); font-size: 12px; }
.st-text { font-size: 12px; font-weight: 600; }
.st-text.st-success { color: #059669; }
.st-text.st-primary { color: #3b82f6; }
.st-text.st-danger { color: #e11d48; }

/* 补考/重修标记 */
.gr-exam-tag { margin-left: 5px; }
.rt-flag { display: inline-block; margin-left: 6px; font-size: 11px; font-weight: 700; padding: 1px 8px; border-radius: 999px; vertical-align: 1px; }
.rt-flag.now { color: #b45309; background: rgba(217,119,6,.12); border: 1px solid rgba(217,119,6,.35); }
.rt-flag.todo { color: #be123c; background: rgba(190,18,60,.08); border: 1px dashed rgba(190,18,60,.4); }
html.dark .rt-flag.now { color: #fbbf24; }
html.dark .rt-flag.todo { color: #fb7185; }

/* ===== 概览统计行：统一高度/圆角/字号 ===== */
.gr-stats-row { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 12px; }
@media (max-width: 1280px) { .gr-stats-row { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 720px)  { .gr-stats-row { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
.gr-stat {
  background: var(--card); border: 1px solid var(--border); border-radius: 12px;
  padding: 13px 16px; display: flex; flex-direction: column; gap: 2px; min-height: 92px;
}
.gr-stat b { font-size: 24px; color: var(--text); font-variant-numeric: tabular-nums; line-height: 1.15; }
.gr-stat b.gold { color: #d97706; }
.gr-stat b.good { color: #059669; }
.gr-stat b.mid { color: #2563eb; }
.gr-stat b.bad { color: #e11d48; }
.gr-stat .gr-of { font-style: normal; font-size: 14px; color: var(--text-lighter); }
.gr-stat span { font-size: 12.5px; font-weight: 600; color: var(--text-light); }
.gr-stat small { font-size: 12px; color: var(--text-lighter); }
.gr-stat.gpa {
  background: linear-gradient(135deg, rgba(245,158,11,.14), rgba(56,189,248,.08));
  border-color: rgba(245,158,11,.35);
}
.gr-stat.gpa b { color: #fbbf24; font-family: 'JetBrains Mono', Consolas, monospace; }

/* ===== 类别均分 + 趋势卡 ===== */
.gr-head { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1.35fr); gap: 14px; align-items: stretch; }
@media (max-width: 1000px) { .gr-head { grid-template-columns: minmax(0, 1fr); } }
.gr-card { background: var(--card); border: 1px solid var(--border); border-radius: 13px; padding: 14px 16px; }
.gr-title { font-size: 13px; font-weight: 700; color: var(--text); margin-bottom: 12px; display: flex; align-items: center; flex-wrap: wrap; gap: 6px; }
.gr-title small { font-weight: 500; font-size: 12px; color: var(--text-light); }
.gr-cats { display: flex; flex-direction: column; gap: 12px; }
.gr-cat-row { display: grid; grid-template-columns: 44px minmax(0, 1fr) 84px; align-items: center; gap: 10px; }
.gr-cat-label { font-size: 12px; color: var(--text-light); }
.gr-cat-track { position: relative; height: 11px; border-radius: 99px; background: rgba(148,163,184,.13); }
.gr-cat-track i { position: absolute; left: 0; top: 0; height: 100%; border-radius: 99px; transition: width .6s cubic-bezier(.22,.9,.36,1); }
.gr-cat-track i.c-bx { background: linear-gradient(90deg, #2bb187, #4cc9a3); }
.gr-cat-track i.c-xx { background: linear-gradient(90deg, #0ea5e9, #38bdf8); }
.gr-cat-track i.c-ts { background: linear-gradient(90deg, #38bdf8, #7dd3fc); }
.gr-cat-track i.c-sj { background: linear-gradient(90deg, #f59e0b, #fbbf24); }
.gr-cat-val { display: flex; align-items: baseline; justify-content: flex-end; gap: 6px; font-size: 13px; font-weight: 800; color: var(--text); font-variant-numeric: tabular-nums; }
.gr-cat-n { font-size: 12px; font-weight: 500; color: var(--text-lighter); }
.gr-nodata { font-size: 12px; color: var(--text-light); opacity: .75; padding: 8px 0; }

/* ===== 趋势折线（限宽限高：桌面端 ≤300px 高） ===== */
.trend-wrap { max-width: 840px; margin: 0 auto; }
.trend-svg { width: 100%; height: auto; display: block; }
.t-legend { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; font-weight: 500; color: var(--text-light); margin-left: auto; }
.t-legend .lg { width: 14px; height: 3px; border-radius: 2px; display: inline-block; }
.t-legend .lg.avg { background: #38bdf8; }
.t-legend .lg.gpa { background: #7dd3fc; }
.t-note { margin-top: 8px; font-size: 12px; color: var(--text-lighter); text-align: center; }
.t-axis { font-size: 12px; fill: var(--text-light); font-weight: 600; }
.t-right { font-size: 12px; fill: var(--text-lighter); }
.t-grid line { stroke: rgba(148,163,184,.18); stroke-dasharray: 3 4; }
.t-grid text { font-size: 12px; fill: var(--text-light); }
.t-line { fill: none; stroke-width: 2.5; stroke-linecap: round; stroke-linejoin: round; }
.t-line.avg { stroke: #38bdf8; filter: drop-shadow(0 2px 6px rgba(56,189,248,.35)); }
.t-line.gpa { stroke: #7dd3fc; filter: drop-shadow(0 2px 6px rgba(125,211,252,.35)); }
.t-dot { stroke: var(--card); stroke-width: 2; }
.t-dot.avg { fill: #38bdf8; }
.t-dot.gpa { fill: #7dd3fc; }
.t-val { font-size: 12px; font-weight: 700; font-variant-numeric: tabular-nums; }
.t-val.avg-t { fill: #7dd3fc; }
.t-val.gpa-t { fill: #c4b5fd; }
.t-x { font-size: 12px; fill: var(--text-light); }

/* ===== 单轮概览 ===== */
.gr-single { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
.gs-item { text-align: center; padding: 12px 8px; border: 1px solid var(--border); border-radius: 10px; background: var(--bg); }
.gs-item b { display: block; font-size: 22px; color: var(--text); font-variant-numeric: tabular-nums; }
.gs-item span { font-size: 12px; color: var(--text-light); }

/* 成绩定位条（紧凑版） */
.gr-pos-track { position: relative; height: 8px; border-radius: 99px; background: rgba(148,163,184,.13); }
.gr-pos-track i {
  position: absolute; top: -3.5px; width: 14px; height: 14px; border-radius: 50%;
  transform: translateX(-50%); background: var(--primary); border: 2.5px solid #fff;
  box-shadow: 0 1px 4px rgba(0,0,0,.3);
}
.gr-pos-track .tick, .gr-cat-track .tick {
  position: absolute; top: -3px; width: 2px; height: 17px; border-radius: 1px;
  background: rgba(148,163,184,.5);
}
.gr-pos-track .tick { height: 13px; top: -2px; }
.gr-footnote { font-size: 12px; color: var(--text-lighter); line-height: 1.7; opacity: .85; }

/* ===== 学期胶囊选择器（顶部胶囊式） ===== */
:deep(.sem-pill .el-select__wrapper) {
  background: rgba(43, 177, 135, .10);
  border-radius: 999px;
  box-shadow: 0 0 0 1px rgba(43, 177, 135, .28) inset;
  min-height: 38px;
  padding: 4px 16px;
  transition: box-shadow .2s, background .2s;
}
:deep(.sem-pill .el-select__wrapper.is-hovering) {
  background: rgba(43, 177, 135, .16);
  box-shadow: 0 0 0 1px rgba(43, 177, 135, .55) inset;
}
:deep(.sem-pill .el-select__placeholder),
:deep(.sem-pill .el-select__selected-item) {
  color: var(--primary);
  font-weight: 700;
}
:deep(.sem-pill .el-select__caret) { color: var(--primary); }
:deep(.sem-pill .sem-cal) { width: 15px; height: 15px; color: var(--primary); display: block; }
.sem-cal :deep(svg) { width: 15px; height: 15px; display: block; }
</style>
