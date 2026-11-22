<template>
  <div class="tp-page stats-page liquid">
    <!-- ===== 两个 Tab ===== -->
    <div class="tp-tabs">
      <button class="tp-tab" :class="{ on: activeTab === 'courses' }" @click="activeTab = 'courses'">
        <span class="tp-tab-ico" v-html="svgIcon('book-open')" />{{ t('修读课程方案') }}
        <small>{{ t('按专业+学期排定必修') }}</small>
      </button>
      <button class="tp-tab" :class="{ on: activeTab === 'diff' }" @click="activeTab = 'diff'">
        <span class="tp-tab-ico" v-html="svgIcon('graduation-cap')" />{{ t('差异化培养') }}
        <small>{{ t('毕业学分要求') }}</small>
      </button>
    </div>

    <!-- ===== Tab 1：修读课程方案（教务系统批量排定）===== -->
    <div v-show="activeTab === 'courses'">
      <TrainingPlanCourses />
    </div>

    <!-- ===== Tab 2：毕业条件（= 培养方案，学制两套，按专业逐行展开）===== -->
    <div v-show="activeTab === 'diff'">
    <div class="tp-hero">
      <div class="tp-hero-main">
        <h2><span class="tp-hero-ico" v-html="svgIcon('graduation-cap')" />{{ t('毕业条件 · 即培养方案') }}</h2>
        <p>{{ t('每个专业的毕业条件 = 修完其培养方案排定的全部课程（必修 + 专业选修）并自选修满公共选修 6 学分。下表按专业逐行给出精确学分；条件随培养方案自动更新。') }}</p>
      </div>
      <div class="tp-hero-stats">
        <div class="tp-hstat"><b>{{ items.length }}</b><span>{{ t('学制套数') }}</span></div>
        <div class="tp-hstat"><b>{{ gxSupply ?? '—' }}</b><span>{{ t('全校公选池学分') }}</span></div>
      </div>
    </div>

    <!-- ===== 学制区块：每专业一行的毕业条件表 ===== -->
    <div class="tp-trackblock" v-for="g in items" :key="g.track">
      <div class="tp-track-head">
        <span class="tp-track-badge" :style="{ background: g.track === 'zsb' ? '#0284c7' : '#2bb187' }">{{ g.label }}</span>
        <b>{{ g.track === 'zsb' ? t('专升本毕业条件') : t('普通本科毕业条件') }}</b>
        <span class="tp-track-note">{{ t('覆盖 {n} 个专业 · 公共选修自选修满 6 学分（全校公选池 {m} 学分）', { n: g.coveredMajorCount, m: g.gxSupply ?? '—' }) }}</span>
      </div>
      <table class="tp-mtable">
        <thead>
          <tr>
            <th>{{ t('专业') }}</th>
            <th>{{ t('必修（含公共必修）') }}</th>
            <th>{{ t('专业选修') }}</th>
            <th>{{ t('公共选修（自选）') }}</th>
            <th>{{ t('毕业合计') }}</th>
            <th>{{ t('课程门数') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="mj in g.majors" :key="mj.major">
            <td class="tp-mname">{{ mj.major }}</td>
            <td class="tp-mnum">{{ mj.bx }}</td>
            <td class="tp-mnum">{{ mj.zx }}</td>
            <td class="tp-mnum">6</td>
            <td class="tp-mtotal">{{ mj.total }}</td>
            <td class="tp-mnum">{{ mj.courses }}</td>
          </tr>
        </tbody>
      </table>
      <div class="tp-tracknote">{{ t('修完对应专业培养方案的全部课程（含公共选修自选 6 学分）即满足该专业毕业条件；课程随培养方案调整自动同步。') }}</div>
    </div>

    <!-- 专业增减说明（红字） -->
    <div class="tp-rednote">
      <b>关于专业的新增与撤销：</b>
      真实高校的新增 / 撤销本科专业，走教育部「普通高等学校本科专业设置与服务平台」的年度集中备案审批
      （院系申报 → 教学委员会论证 → 校长办公会 → 报教育部备案），不在教务 / 选课系统里操作——真实华厦亦如此
      （2015 年建校首批 5 个本科专业，现已增至 20 余个）。
      本系统内的「加专业」= 在「修读课程方案」为该专业排定课程并导入该专业学生账号：
      毕业条件（上表）、自动排课、毕业进度即全部自动联动，本表会自动新增该专业一行。
    </div>
    </div><!-- /Tab 2 毕业条件 -->
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api'
import { t } from '@/i18n'
import TrainingPlanCourses from '@/components/TrainingPlanCourses.vue'
const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`

// 两个 Tab：修读课程方案 / 毕业条件（= 培养方案，学制两套）
const activeTab = ref<'courses' | 'diff'>('courses')

// 嵌入模式（选课轮次页 Tab 内）：隐藏跳转提示按钮，文案指向本页「规则与参数」Tab
const props = defineProps<{ embedded?: boolean }>()

interface MajorCond {
  major: string
  bx: number
  zx: number
  courses: number
  total: number
}

interface Plan {
  track: 'normal' | 'zsb'
  label: string
  gxSupply?: number | null
  coveredMajorCount?: number
  majors?: MajorCond[]
}

const items = ref<Plan[]>([])
const gxSupply = computed(() => items.value[0]?.gxSupply ?? null)

async function load() {
  try {
    const r: any = await http.get('/system/grade-reqs')
    const rows: Plan[] = (r?.data || r || []).map((x: any) => ({ ...x }))
    // 防御性兜底：两套学制必须齐全（后端已合成，这里兜底历史空库）
    if (!rows.some(x => x.track === 'normal')) rows.push({ track: 'normal', label: '普通本科', majors: [] })
    if (!rows.some(x => x.track === 'zsb')) rows.push({ track: 'zsb', label: '专升本', majors: [] })
    items.value = rows
  } catch (e: any) { ElMessage.error(e?.message || t('加载失败')) }
}

onMounted(load)
</script>

<style scoped>
.tp-page { display: flex; flex-direction: column; gap: 16px; padding: 4px 0; }

/* ===== 两个 Tab ===== */
.tp-tabs { display: flex; gap: 6px; padding: 5px; border-radius: 14px; background: color-mix(in srgb, var(--text-light) 6%, transparent); border: 1px solid var(--border); }
.tp-tab { flex: 1; display: flex; align-items: center; justify-content: center; gap: 8px; flex-wrap: wrap;
  padding: 10px 14px; border: none; border-radius: 10px; background: transparent; cursor: pointer;
  font-family: inherit; font-size: 14px; font-weight: 700; color: var(--text-light);
  transition: background .2s, color .2s, box-shadow .2s; }
.tp-tab small { flex-basis: 100%; font-size: 11px; font-weight: 400; color: var(--text-lighter); margin-top: -4px; }
.tp-tab:hover { color: var(--text); background: color-mix(in srgb, var(--primary) 8%, transparent); }
.tp-tab.on { color: var(--primary); background: var(--card-bg); box-shadow: 0 2px 10px -4px rgba(0,0,0,.3), inset 0 0 0 1px color-mix(in srgb, var(--primary) 30%, transparent); }
.tp-tab.on small { color: var(--text-light); }
.tp-tab-ico { display: inline-flex; }
.tp-tab-ico :deep(.icon) { width: 18px; height: 18px; }

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
/* 供给核对（培养方案可提供学分 vs 配置要求） */
.tp-checks { display: flex; flex-direction: column; gap: 5px; margin-top: 10px; padding-top: 10px; border-top: 1px dashed var(--border); }
.tp-check { font-size: 11.5px; line-height: 1.5; }
.tp-check.bx { color: var(--text-light, #64748b); }
.tp-check.warn { color: #e11d48; font-weight: 600; }
.tp-check.ok { color: #059669; }

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

/* ===== 毕业条件表（按专业逐行）===== */
.tp-trackblock { background: var(--card-bg); border: 1px solid var(--border); border-radius: 16px; padding: 18px 20px; }
.tp-trackblock + .tp-trackblock { margin-top: 14px; }
.tp-track-head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-bottom: 12px; }
.tp-track-badge { color: #fff; font-size: 12px; font-weight: 800; padding: 4px 12px; border-radius: 999px; flex-shrink: 0; }
.tp-track-head b { font-size: 15px; color: var(--text); }
.tp-track-note { font-size: 12px; color: var(--text-light); margin-left: auto; }
.tp-mtable { width: 100%; border-collapse: collapse; font-size: 13px; }
.tp-mtable th { text-align: center; padding: 8px 10px; color: var(--text-light); font-size: 12px; font-weight: 600; border-bottom: 1px solid var(--border); white-space: nowrap; }
.tp-mtable th:first-child, .tp-mtable td:first-child { text-align: left; }
.tp-mtable td { padding: 8px 10px; border-bottom: 1px dashed var(--border); color: var(--text); text-align: center; }
.tp-mname { font-weight: 700; text-align: left !important; }
.tp-mnum { font-variant-numeric: tabular-nums; }
.tp-mtotal { font-weight: 800; color: var(--primary, #2bb187); }
.tp-tracknote { margin-top: 10px; font-size: 12px; color: var(--text-light); }


/* 专业增减说明（红字） */
.tp-rednote {
  margin-top: 14px;
  padding: 12px 16px;
  border-radius: 12px;
  border: 1px solid rgba(225, 29, 72, 0.35);
  background: rgba(244, 63, 94, 0.06);
  color: #e11d48;
  font-size: 12.5px;
  line-height: 1.7;
}
.tp-rednote b { font-weight: 800; }
html.dark .tp-rednote { border-color: rgba(244, 63, 94, 0.4); background: rgba(244, 63, 94, 0.08); color: #fb7185; }

</style>
