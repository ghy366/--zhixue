<template>
  <div class="gm-page stats-page liquid analysis-page">
    <header class="an-head">
      <div>
        <h1>毕业管理<span class="an-eyebrow an-eyebrow-inline">🎓 毕设过程管理</span></h1>
        <p>全院毕业设计进度漏斗与评阅指派——对齐维普 / 超星毕设系统的教务视角。</p>
      </div>
      <div class="an-head-side">
        <span class="an-head-mini">{{ t('数据更新于 {time}', { time: updatedAt }) }}</span>
        <button class="gm-refresh" @click="load">⟳ {{ t('刷新') }}</button>
      </div>
    </header>

    <!-- ===== 进度漏斗 ===== -->
    <div class="gm-funnel">
      <div v-for="(f, i) in funnel" :key="f.label" class="gm-fstep" :class="{ dim: f.value === 0 }">
        <div class="gmf-bar-wrap">
          <i class="gmf-bar" :style="{ width: f.pct + '%' }" />
        </div>
        <div class="gmf-num"><b>{{ f.value }}</b><span>{{ f.label }}</span></div>
        <div v-if="i < funnel.length - 1" class="gmf-rate">{{ f.rate }}</div>
      </div>
    </div>

    <!-- ===== KPI ===== -->
    <div class="gm-kpis">
      <div class="gm-kpi"><b>{{ ov.confirmed }}</b><span>双选确认</span></div>
      <div class="gm-kpi"><b>{{ ov.withTopic }}</b><span>已确定课题</span></div>
      <div class="gm-kpi warn"><b>{{ unassigned }}</b><span>评阅待指派</span></div>
      <div class="gm-kpi ok"><b>{{ qualifiedCount }}</b><span>具备答辩资格</span></div>
      <div class="gm-kpi ok"><b>{{ ov.defenseDone }}</b><span>答辩完成</span></div>
    </div>

    <!-- ===== 明细表 ===== -->
    <div class="gm-table-card">
      <div class="gmt-head">
        <b>双选对明细</b>
        <span class="gmt-note">中期通过后须指派评阅教师（三方评分链：导师 40% + 评阅 20% + 答辩 40%）</span>
      </div>
      <div class="gm-table-scroll">
        <table class="gm-table">
          <thead>
            <tr>
              <th>学生</th><th>专业</th><th>导师</th><th>课题</th><th>当前阶段</th>
              <th>开题</th><th>中期</th><th>终稿</th><th>查重</th><th>答辩</th>
              <th>指导</th><th>答辩资格</th><th>评阅教师</th><th>总评</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="p in ov.pairs" :key="p.advisorId">
              <td><b>{{ p.studentName }}</b><small>{{ p.studentNo }}</small></td>
              <td class="gm-muted">{{ p.studentMajor }}</td>
              <td>{{ p.advisorName }}</td>
              <td class="gm-topic" :title="p.topic">{{ p.topic }}</td>
              <td><span class="gm-stage">{{ p.stage }}</span></td>
              <td><span class="gm-st" :class="stCls(p.opening)">{{ p.opening }}</span></td>
              <td><span class="gm-st" :class="stCls(p.midterm)">{{ p.midterm }}</span></td>
              <td><span class="gm-st" :class="stCls(p.final)">{{ p.final }}</span></td>
              <td><span class="gm-st plag">{{ p.plagiarism }}<template v-if="p.repeatRate != null"> {{ p.repeatRate }}%</template></span></td>
              <td><span class="gm-st" :class="stCls(p.defense)">{{ p.defense }}</span></td>
              <td><span class="gm-guide" :class="{ low: p.guidanceCount < 3 }">{{ p.guidanceCount }} 次</span></td>
              <td><span class="gm-qual" :class="p.qualified ? 'ok' : 'no'">{{ p.qualified ? '✓ 具备' : '未具备' }}</span></td>
              <td>
                <template v-if="p.reviewerName">{{ p.reviewerName }}<span v-if="p.reviewerScore != null" class="gm-rscore">{{ p.reviewerScore }} 分</span><span v-else class="gm-rscore pend">待评分</span></template>
                <template v-else-if="p.midterm === '已通过'">
                  <div class="gm-assign">
                    <select v-model="p._reviewerId" class="gm-select">
                      <option value="" disabled>选择评阅教师</option>
                      <option v-for="t in teachers" :key="t.id" :value="t.id">{{ t.name }}</option>
                    </select>
                    <button class="gm-btn" :disabled="!p._reviewerId || assigning === p.advisorId" @click="assign(p)">指派</button>
                  </div>
                </template>
                <span v-else class="gm-muted">—</span>
              </td>
              <td><b v-if="p.totalScore != null" class="gm-total">{{ p.totalScore }}</b><span v-else class="gm-muted">—</span></td>
            </tr>
          </tbody>
        </table>
        <div v-if="!ov.pairs?.length" class="gm-empty">尚无已确认的双选关系——学生完成导师双选后，这里会出现进度明细。</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import http from '@/api'
import { t } from '@/i18n'
import { ElMessage } from 'element-plus'

const ov = ref<any>({ pairs: [] })
const teachers = ref<any[]>([])
const assigning = ref<number | null>(null)
const updatedAt = ref('—')

const funnel = computed(() => {
  const o = ov.value
  const steps = [
    { label: '在校学生', value: o.studentTotal || 0 },
    { label: '双选确认', value: o.confirmed || 0 },
    { label: '已确定课题', value: o.withTopic || 0 },
    { label: '开题通过', value: o.openingPassed || 0 },
    { label: '中期通过', value: o.midtermPassed || 0 },
    { label: '终稿通过', value: o.finalPassed || 0 },
    { label: '查重达标', value: o.plagPassed || 0 },
    { label: '答辩完成', value: o.defenseDone || 0 },
  ]
  const top = Math.max(1, steps[0].value)
  let prev = top
  return steps.map(s => {
    const rate = s.value > 0 && prev > 0 ? Math.round(s.value * 100 / prev) + '%' : '—'
    prev = s.value
    return { ...s, pct: Math.max(3, Math.round(s.value * 100 / top)), rate }
  })
})

const unassigned = computed(() => (ov.value.pairs || []).filter((p: any) => p.midterm === '已通过' && !p.reviewerName).length)
const qualifiedCount = computed(() => (ov.value.pairs || []).filter((p: any) => p.qualified).length)

function stCls(s: string) {
  return s === '已通过' ? 'ok' : s === '待审核' ? 'pend' : s === '已退回' ? 'bad' : ''
}

async function load() {
  try {
    const r: any = await http.get('/workspace/admin-overview')
    const data = r?.data || {}
    data.pairs = (data.pairs || []).map((p: any) => ({ ...p, _reviewerId: '' }))
    ov.value = data
    updatedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false, hour: '2-digit', minute: '2-digit' })
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  }
  try {
    const r2: any = await http.get('/admin/users', { params: { role: 'teacher', page: 1, size: 500 } })
    const d = r2?.data
    teachers.value = Array.isArray(d) ? d : (d?.items || [])
  } catch { teachers.value = [] }
}

async function assign(p: any) {
  assigning.value = p.advisorId
  try {
    const r: any = await http.post('/workspace/assign-reviewer', { advisorId: p.advisorId, reviewerId: p._reviewerId })
    ElMessage.success(r?.message || r?.data?.msg || '已指派')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '指派失败')
  }
  assigning.value = null
}

onMounted(load)
</script>

<style scoped>
.gm-page { display: flex; flex-direction: column; gap: 16px; padding: 4px 0 20px; }
.gm-refresh { padding: 6px 14px; border-radius: 9px; border: 1px solid var(--border); background: var(--card-bg); color: var(--text); font-family: inherit; font-size: 12.5px; font-weight: 700; cursor: pointer; }

/* 漏斗 */
.gm-funnel { background: var(--card-bg); border: 1px solid var(--border); border-radius: 16px; padding: 18px 20px; display: flex; gap: 8px; align-items: stretch; overflow-x: auto; }
.gm-fstep { flex: 1; min-width: 96px; text-align: center; position: relative; }
.gmf-bar-wrap { height: 8px; border-radius: 999px; background: color-mix(in srgb, var(--text-light) 10%, transparent); overflow: hidden; margin-bottom: 8px; }
.gmf-bar { display: block; height: 100%; border-radius: 999px; background: linear-gradient(90deg, #2bb187, #38bdf8); transition: width .6s cubic-bezier(.2,.7,.3,1); }
.gmf-num b { display: block; font-size: 22px; font-weight: 800; color: var(--text); font-variant-numeric: tabular-nums; }
.gmf-num span { font-size: 11.5px; color: var(--text-light); }
.gmf-rate { position: absolute; right: -4px; top: 16px; font-size: 10.5px; color: var(--text-lighter); transform: translateX(50%); z-index: 1; }
.gm-fstep.dim .gmf-num b { color: var(--text-lighter); }

/* KPI */
.gm-kpis { display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px; }
.gm-kpi { background: var(--card-bg); border: 1px solid var(--border); border-radius: 14px; padding: 14px 18px; display: flex; flex-direction: column; gap: 2px; }
.gm-kpi b { font-size: 24px; font-weight: 800; color: var(--text); font-variant-numeric: tabular-nums; }
.gm-kpi span { font-size: 12px; color: var(--text-light); }
.gm-kpi.warn b { color: #d97706; }
.gm-kpi.ok b { color: #16a34a; }

/* 表 */
.gm-table-card { background: var(--card-bg); border: 1px solid var(--border); border-radius: 16px; padding: 16px 18px; }
.gmt-head { display: flex; align-items: baseline; gap: 12px; flex-wrap: wrap; margin-bottom: 10px; }
.gmt-head b { font-size: 15px; color: var(--text); }
.gmt-note { font-size: 12px; color: var(--text-light); }
.gm-table-scroll { overflow-x: auto; }
.gm-table { width: 100%; border-collapse: collapse; font-size: 12.5px; min-width: 1050px; }
.gm-table th { text-align: left; padding: 8px 10px; color: var(--text-light); font-weight: 600; font-size: 11.5px; border-bottom: 1px solid var(--border); white-space: nowrap; }
.gm-table td { padding: 9px 10px; border-bottom: 1px dashed var(--border); color: var(--text); vertical-align: middle; }
.gm-table td small { display: block; font-size: 11px; color: var(--text-lighter); }
.gm-topic { max-width: 200px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.gm-muted { color: var(--text-lighter); }
.gm-stage { background: color-mix(in srgb, var(--primary) 10%, transparent); color: var(--primary); padding: 2px 10px; border-radius: 999px; white-space: nowrap; }
.gm-st { white-space: nowrap; font-size: 12px; }
.gm-st.ok { color: #16a34a; }
.gm-st.pend { color: #d97706; }
.gm-st.bad { color: #e11d48; }
.gm-guide { white-space: nowrap; }
.gm-guide.low { color: #d97706; font-weight: 700; }
.gm-rscore { margin-left: 6px; font-weight: 700; color: var(--primary); }
.gm-rscore.pend { color: #d97706; }
.gm-total { color: var(--primary); font-size: 15px; }
.gm-assign { display: flex; gap: 6px; }
.gm-select { max-width: 130px; padding: 4px 6px; border: 1px solid var(--border); border-radius: 8px; background: var(--card-bg); color: var(--text); font-family: inherit; font-size: 12px; }
.gm-btn { padding: 4px 10px; border: none; border-radius: 8px; background: var(--primary); color: #fff; font-family: inherit; font-size: 12px; font-weight: 700; cursor: pointer; white-space: nowrap; }
.gm-btn:disabled { opacity: .45; cursor: not-allowed; }
.gm-empty { text-align: center; padding: 40px 20px; color: var(--text-light); font-size: 13px; }
.gm-st.plag { color: var(--text); }
.gm-qual { white-space: nowrap; font-size: 12px; }
.gm-qual.ok { color: #16a34a; font-weight: 700; }
.gm-qual.no { color: var(--text-lighter); }
</style>
