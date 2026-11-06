<template>
  <div class="zx-page stats-page liquid">
    <!-- ===== 顶部栏 ===== -->
    <div class="page-head">
      <div class="page-title">
        {{ t('我的课表') }}
        <span class="page-sub">{{ t('已录取课程的上课安排 · 选课结果确定后按此课表上课') }}</span>
      </div>
      <el-radio-group v-model="period" size="default">
        <el-radio-button label="week">{{ t('周视图') }}</el-radio-button>
        <el-radio-button label="day">{{ t('日视图') }}</el-radio-button>
      </el-radio-group>
    </div>

    <!-- ===== 学期/周次 + 导出 ===== -->
    <div class="week-nav">
      <button class="wn-btn" @click="changeWeek(-1)" :disabled="week<=1">‹</button>
      <button ref="weekBtn" class="wn-center" @click="openWeekPicker">
        <span class="wn-sem">{{ viewSemLabel || semInfo?.name || t('当前学期') }}</span>
        <span class="wn-sep">{{ t('第') }}</span>
        <span class="wn-week">{{ week }}</span>
        <span class="wn-sep">{{ t('周') }}</span>
        <span class="wn-chev">›</span>
      </button>
      <button class="wn-btn" @click="changeWeek(1)" :disabled="week>=18">›</button>
      <button class="wn-reset" @click="week = curWeek">🔄 {{ t('重置') }}</button>
      <button class="wn-export" @click="exportIcs">📅 {{ t('导出日历') }}</button>
      <button class="wn-export" @click="exportPng">🖼️ {{ t('导出图片') }}</button>
    </div>

    <!-- ===== #1 拖拽试排工具条（默认折叠，点开才显示候选） ===== -->
    <div v-if="period === 'week' && cands.length" class="drag-strip" :class="{ open: dragOpen }">
      <div class="ds-head">
        <button class="ds-toggle" @click="dragOpen = !dragOpen">
          <span class="ds-toggle-arrow">{{ dragOpen ? '▾' : '▸' }}</span>
          {{ t('拖拽试排') }}
          <small>{{ t('把候选课拖到课表格子上，先试试时间冲不冲突') }}</small>
        </button>
        <span v-if="drafts.length" class="ds-count">{{ t('已试排 {n} 门', { n: drafts.length }) }}</span>
        <button v-if="drafts.length" class="ds-clear" @click="drafts = []">🗑 {{ t('清空') }}</button>
      </div>
      <div v-if="dragOpen" class="ds-chips">
        <span
          v-for="c in dragCandidates" :key="c.id"
          class="ds-chip" :class="{ used: usedDraftIds.has(c.id) }"
          draggable="true"
          @dragstart="onDragStart($event, c)"
        >{{ c.name }}<small>{{ c.time }}</small></span>
      </div>
    </div>

    <!-- ===== 周视图 ===== -->
    <div v-if="period === 'week'" class="schedule-grid" ref="gridRef">
      <div class="sg-header">
        <div class="sg-cell time-col">
          <div class="sg-month">{{ monthLabel }}</div>
        </div>
        <div v-for="d in weekDays" :key="d.key" class="sg-cell">
          <div class="sg-name">{{ d.name }}</div>
          <div class="sg-num" :class="{ today: d.isToday }">{{ d.num }}</div>
        </div>
      </div>
      <div v-for="(p, ti) in periods" :key="ti" class="sg-row">
        <div class="sg-cell time-col">
          <div class="ti-label">{{ t(['一二节','三四节','五六节','七八节','九十节'][ti]) }}</div>
          <div class="ti-time">{{ p.from }}</div>
        </div>
        <div
          v-for="d in weekDays" :key="d.key"
          class="sg-cell day-col"
          :class="{ 'drop-ok': dragOver?.day === d.key && dragOver?.period === p.key }"
          @dragover.prevent="dragOver = { day: d.key, period: p.key }"
          @dragleave="dragOver = null"
          @drop.prevent="onDrop($event, d.key, p.key)"
        >
          <div
            v-for="(c, idx) in cellClasses(d.key, p.key)"
            :key="idx"
            class="class-block"
            :class="[
              conflictLevel(c) ? (conflictLevel(c) === 'room' ? 'cf-room' : 'cf-teacher') : '',
              { 'cf-glow': isConflictsWithActive(c) },
            ]"
            :style="{ background: c.bg }"
            @click="viewClass(c)"
            @mouseenter="activeKey = blockKey(c)"
            @mouseleave="activeKey = ''"
          >
            <div class="cb-name">{{ c.name }}</div>
            <div class="cb-meta">{{ c.classroom }}</div>
            <div v-if="conflictLevel(c)" class="cf-badge" :class="conflictLevel(c)">{{ conflictLevel(c) === 'room' ? t('冲突') : t('教师撞') }}</div>
          </div>
          <!-- #1 试排预览块 -->
          <div
            v-for="df in draftAt(d.key, p.key)"
            :key="'df' + df.key"
            class="draft-block"
            :class="{ 'df-conflict': df.conflicts.length }"
            :title="df.conflicts.length ? t('⚠ 与以下课程时间冲突：{n}', { n: df.conflicts.map((x: any) => x.name).join('、') }) : t('试排预览 · 点击移除')"
            @click="removeDraft(df.key)"
          >
            <div class="cb-name">{{ df.name }}</div>
            <div class="cb-meta">{{ df.conflicts.length ? t('⚠ {n} 处冲突', { n: df.conflicts.length }) : t('试排预览') }}</div>
          </div>
        </div>
      </div>
    </div>
    <div v-if="period === 'week' && draftConflicts.length" class="section conflict-section">
      <div class="section-header">
        <div class="section-title">{{ t('试排冲突（{n} 处）', { n: draftConflicts.length }) }}</div>
        <span class="section-sub">{{ t('试排课程与已排课程/其他试排块的时间撞车，点击课表中的试排块可移除') }}</span>
      </div>
      <div class="conf-list">
        <div v-for="(c, i) in draftConflicts" :key="'dc' + i" class="conf-item t-time">
          <span class="conf-badge">{{ t('试排冲突') }}</span>
          <div class="conf-body">
            <div class="conf-pair"><b>{{ c.a }}</b> ⇄ <b>{{ c.b }}</b></div>
            <span>{{ t('周') }}{{ t(['日','一','二','三','四','五','六'][c.day]) }} · {{ t(PERIOD_LABELS[c.period] || c.period) }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- ===== 冲突检测 ===== -->
    <div v-if="conflicts.length" class="section conflict-section">
      <div class="section-header">
        <div class="section-title">{{ t('⚠️ 冲突检测（{n} 处）', { n: conflicts.length }) }}</div>
        <span class="section-sub">{{ t('🔴 教室/时间冲突 · 🟠 同一教师同一时间 · 悬停课表卡片可高亮冲突对方') }}</span>
      </div>
      <div class="conf-list">
        <div v-for="(c, i) in conflicts" :key="i" class="conf-item" :class="'t-' + c.type">
          <span class="conf-badge">{{ typeLabel(c.type) }}</span>
          <div class="conf-body">
            <div class="conf-pair"><b>{{ c.a.name }}</b><template v-if="c.a.pending"> <i class="conf-pend-tag">{{ t('待录取') }}</i></template> ⇄ <b>{{ c.b.name }}</b><template v-if="c.b.pending"> <i class="conf-pend-tag">{{ t('待录取') }}</i></template></div>
            <span>{{ t('周') }}{{ t(['日','一','二','三','四','五','六'][c.dayOfWeek]) }} · {{ t(PERIOD_LABELS[c.period] || c.period) }}<template v-if="c.type === 'room'"> · {{ t('教室') }} {{ c.a.classroom }}</template><template v-if="c.type === 'teacher'"> · {{ t('教师') }} {{ c.a.teacher }}</template><template v-if="c.a.pending || c.b.pending"> · {{ t('含待录取课程') }}</template></span>
          </div>
          <div class="conf-act">
            <template v-if="droppableSide(c)">
              <button class="conf-fix-btn" :disabled="dropping" @click="resolveConflict(c)">
                {{ dropping ? t('处理中…') : (droppableSide(c)!.pending ? t('撤销「{name}」', { name: droppableSide(c)!.name }) : t('退掉「{name}」', { name: droppableSide(c)!.name })) }}
              </button>
              <span class="conf-act-hint">{{ confHint(c) }}</span>
            </template>
            <span v-else class="conf-act-hint">{{ confHint(c) }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- ===== 日视图 ===== -->
    <div v-else class="day-view">
      <div class="dv-date">{{ dayLabel }}</div>
      <div v-if="todayClasses.length" class="dv-list">
        <div v-for="(c, idx) in todayClasses" :key="idx" class="dv-card">
          <div class="dv-head">
            <div class="dv-name">{{ c.name }}</div>
            <div class="dv-time">{{ c.timeLabel }}</div>
          </div>
          <div class="dv-meta">
            {{ c.teacher }} · {{ c.classroom }}
            <button class="dv-loc-btn" :disabled="!buildingFromLocation(c.classroom)" @click="locateOnMap(c.classroom)" :title="t('在 3D 校园地图中定位教学楼')">{{ t('地图定位') }}</button>
          </div>
        </div>
      </div>
      <el-empty v-else :description="t('今日无课')" />
    </div>

    <!-- ===== 校园地图（3D / 平面）===== -->
    <div class="section campus-section">
      <div class="section-header">
        <div class="section-title">{{ t('🗺️ 我的校园地图') }}</div>
        <div style="display:flex;gap:8px;align-items:center">
          <el-radio-group v-if="showMap" v-model="mapMode" size="small">
            <el-radio-button label="3d">{{ t('🏢 3D 模型') }}</el-radio-button>
            <el-radio-button label="2d">{{ t('🗺️ 平面地图') }}</el-radio-button>
          </el-radio-group>
          <button class="btn btn-ghost btn-sm" @click="showMap = !showMap">{{ showMap ? t('收起地图') : t('展开地图') }}</button>
        </div>
      </div>
      <CampusMap3D v-if="showMap && mapMode === '3d'" ref="map3dRef" :items="items" />
      <CampusMap v-else-if="showMap" ref="map2dRef" :items="items" />
    </div>

    <!-- ===== 智能选课推荐 ===== -->
    <div class="section gen-section">
      <!-- 标题栏：左 标题+说明 / 右 实时统计（全部来自当前勾选状态的真实计算） -->
      <div class="gen-head">
        <div class="gen-head-l">
          <div class="section-title">{{ t('🧠 智能选课推荐') }}</div>
          <span class="section-sub">{{ t('结合上课时间、学分与竞争度，在浏览器本地实时计算互不冲突的选课组合；🔒 锁定课程优先入选') }}</span>
        </div>
        <div class="gen-head-r">
          <div class="gen-stat"><b>{{ checkedIds.length }}</b><small>{{ t('已选课程') }}</small></div>
          <div class="gen-stat"><b>{{ checkedCredits }}</b><small>{{ t('已选总学分') }}</small></div>
          <div class="gen-stat" :class="{ bad: conflictCourseCount > 0 }"><b>{{ conflictCourseCount }}</b><small>{{ t('时间冲突') }}</small></div>
          <button class="btn btn-primary btn-sm" :disabled="generating || !checkedIds.length" @click="generate" :title="t('基于当前勾选与锁定课程重新计算推荐组合')">
            {{ generating ? t('⏳ 计算中…') : t('✨ 重新推荐') }}
          </button>
        </div>
      </div>

      <div v-if="cands.length" class="cand-list">
        <div v-for="c in cands" :key="c.id" class="cand-row" :class="{ locked: locks.has(c.id), conflicted: rowConflict(c).length > 0, checked: checkedIds.includes(c.id) }">
          <!-- 第一行：勾选 + 课程名 + 类型 + 推荐标签（真实判断依据） -->
          <div class="cr-top">
            <label class="cand-label">
              <input type="checkbox" class="cand-check" v-model="checkedIds" :value="c.id" />
              <span class="cand-name" :title="c.name">{{ c.name }}</span>
            </label>
            <span v-if="c.category" class="cand-cat">{{ c.category }}</span>
            <span class="cr-rec" :class="recTag(c).cls" :title="recTag(c).tip">{{ recTag(c).label }}</span>
            <button class="cand-loc" :disabled="!buildingFromLocation(c.location)" @click="locateOnMap(c.location)" :title="t('在 3D 校园地图中定位教学楼')">{{ t('定位') }}</button>
            <button class="cand-lock" :class="{ locked: locks.has(c.id) }" @click="toggleLock(c.id)" :title="locks.has(c.id) ? t('已锁定：重新推荐时优先保留') : t('锁定：生成方案时优先占位')">
              {{ locks.has(c.id) ? '🔒' : '🔓' }}
            </button>
          </div>
          <!-- 第二行：教师 / 学分 / 时间 / 教室 / 校区 -->
          <div class="cr-meta">
            <span>👨‍🏫 {{ (isThesisCourse(c) && thesisAdvisorName) ? thesisAdvisorName : (c.teacherName || c.teacher || t('教师待定')) }}</span>
            <span>🎓 {{ c.credit || 0 }} {{ t('学分') }}</span>
            <span>📅 {{ c.time || t('时间待定') }}</span>
            <span>🏛️ {{ c.location || t('教室待定') }}</span>
            <span>🗺️ {{ c.campus || t('主校区') }}</span>
          </div>
          <!-- 第三行：推荐理由 + 时间冲突提示 -->
          <div class="cr-reason">
            <span class="cr-why">{{ recReason(c) }}</span>
            <span v-if="rowConflict(c).length" class="cr-conflict" :title="t('与 {n} 上课时间重叠', { n: rowConflict(c).map(x => x.name).join('、') })">
              ⚠ {{ t('与 {n} 时间冲突', { n: rowConflict(c).map(x => x.name).join('、') }) }}
            </span>
          </div>
        </div>

        <div class="cand-actions">
          <button class="btn btn-ghost btn-sm" @click="resetGen" :title="t('清空勾选与全部锁定')">🔄 {{ t('重置') }}</button>
          <button class="btn btn-primary btn-sm" :disabled="generating || !checkedIds.length" @click="generate" :title="t('基于当前勾选与锁定重新计算推荐组合')">
            {{ generating ? t('⏳ 生成中...') : t('✨ 生成推荐方案') }}
          </button>
        </div>
      </div>
      <div v-else class="cand-empty">{{ t('没有更多可选的候选课程 🎉') }}</div>

      <!-- 推荐组合概览：方案编号 / 推荐原因 / 规模 / 冲突检查 / 锁定保护（均为前端真实计算结果） -->
      <div v-if="plans.length && activePlan" class="ov-strip">
        <div class="ov-item">
          <small>{{ t('当前方案') }}</small>
          <b>{{ t('方案 {n}', { n: planIdx + 1 }) }} · {{ planTag(activePlan).label }}</b>
        </div>
        <div class="ov-item ov-wide">
          <small>{{ t('推荐原因') }}</small>
          <span>{{ planReason(activePlan) }}</span>
        </div>
        <div class="ov-item">
          <small>{{ t('组合规模') }}</small>
          <b>{{ activePlan.courses.length }} {{ t('门') }} / {{ activePlan.credits }} {{ t('学分') }}</b>
        </div>
        <div class="ov-item">
          <small>{{ t('冲突检查') }}</small>
          <b :class="activePlan.conflicts.length ? 'bad' : 'ok'">{{ activePlan.conflicts.length ? t('{n} 处冲突', { n: activePlan.conflicts.length }) : t('全部互不冲突 ✓') }}</b>
        </div>
        <div class="ov-item">
          <small>{{ t('锁定保护') }}</small>
          <b :class="{ ok: lockedCheckedCount > 0 }">{{ lockedCheckedCount ? t('已优先保留 {n} 门锁定课程', { n: lockedCheckedCount }) : t('未使用锁定') }}</b>
        </div>
      </div>

      <div v-if="plans.length" class="rec-panel">
        <div class="rec-head">
          ✨ {{ t('已生成 {n} 套互不冲突方案', { n: plans.length }) }} · {{ t('点击卡片切换预览') }}
          <span v-if="plans[0]?.excluded.length" class="rec-rejected-tip">
            · {{ t('勾选中 {n} 门因冲突未能进入任何方案', { n: plans[0]?.excluded.length }) }}
          </span>
        </div>
        <!-- 方案对比卡片 -->
        <div class="plan-cards">
          <div
            v-for="(pl, pi) in plans" :key="pi"
            class="plan-card" :class="{ active: planIdx === pi, conflict: !!pl.conflicts.length }"
            @click="planIdx = pi"
          >
            <div class="plan-head">
              <b>{{ t('方案 {n}', { n: pi + 1 }) }}</b>
              <span class="plan-tag" :class="planTag(pl).cls">{{ planTag(pl).label }}</span>
            </div>
            <div class="plan-stats">
              <span><b>{{ pl.courses.length }}</b> {{ t('门') }}</span>
              <span><b>{{ pl.credits }}</b> {{ t('学分') }}</span>
              <span>{{ t('空闲') }} <b>{{ pl.freeSlots }}</b> {{ t('格') }}</span>
            </div>
            <div class="plan-chips">
              <span v-for="c in pl.courses" :key="c.courseId" class="plan-chip">{{ c.name }}</span>
            </div>
          </div>
        </div>
        <!-- 当前方案明细 -->
        <div class="data-table">
          <table>
            <thead><tr><th>{{ t('课程') }}</th><th>{{ t('上课时间') }}</th><th>{{ t('学分') }}</th><th>{{ t('报名/容量') }}</th><th>{{ t('结果') }}</th></tr></thead>
            <tbody>
              <tr v-for="c in activePlan?.courses || []" :key="'ok-'+c.courseId">
                <td><b>{{ c.name }}</b></td>
                <td style="font-size:13px;color:var(--text-light)">{{ c.time || '' }}</td>
                <td>{{ c.credit || 0 }}</td>
                <td style="font-size:12px;color:var(--text-light)">{{ statOf(c.courseId) }}</td>
                <td><span class="status-tag status-approved">{{ t('方案 {n} 选入', { n: planIdx + 1 }) }}</span></td>
              </tr>
              <tr v-for="id in activePlan?.excluded || []" :key="'no-'+id" class="rec-rejected">
                <td>{{ candMap.get(id)?.name || t('课程#{n}', { n: id }) }}</td>
                <td style="font-size:13px;color:var(--text-light)">{{ candMap.get(id)?.time || '' }}</td>
                <td>{{ candMap.get(id)?.credit || '' }}</td>
                <td style="font-size:12px;color:var(--text-light)">{{ statOf(id) }}</td>
                <td><span class="status-tag status-rejected">{{ t('未入选') }}</span></td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="rec-actions">
          <button v-if="activePlan?.courses.length" class="btn btn-primary btn-sm" @click="addIntents">🛒 {{ t('把方案 {n} 加入意向单', { n: planIdx + 1 }) }}</button>
        </div>
        <div class="rec-tip">{{ t('推荐组合由') }}<strong>{{ t('浏览器本地规则算法') }}</strong>{{ t('（三种贪心策略：学分优先 / 竞争小优先 / 随机扰动）基于课程真实上课时间计算生成，非后端 AI 推荐；冲突判定按「周几 + 节次时段」粒度。「报名/容量」可帮你判断竞争激烈程度。点「加入意向单」后，可在意向单页面调整投入积分并一键提交。') }}</div>
      </div>
    </div>

    <!-- ===== 周次选择器（teleport到body避免被page容器截断） ===== -->
    <Teleport to="body">
    <div v-if="showWeekPicker" class="wk-overlay" @click.self="showWeekPicker = false">
      <div class="wk-sheet">
        <div class="wk-sheet-head">
          <button class="wk-btn-text" @click="showWeekPicker = false">{{ t('取消') }}</button>
          <span class="wk-sheet-title">{{ t('选择上课周次') }}</span>
          <span class="wk-head-spacer"></span>
        </div>
        <div class="wk-body">
          <div class="wk-col">
            <div class="wk-col-title">{{ t('选择学期') }}</div>
            <div class="wk-sems">
              <div
                v-for="o in semOpts" :key="o.no"
                class="wk-sem-item"
                :class="{ active: o.no === semSel, cur: o.no === currentSemesterNo }"
                @click="semSel = o.no"
              >
                <span class="wk-sem-check" v-if="o.no === semSel">✓</span>
                <b class="wk-sem-year">{{ o.year }}</b>
                <span class="wk-sem-term">{{ o.term }}</span>
              </div>
            </div>
          </div>
          <div class="wk-col">
            <div class="wk-col-title">{{ t('选择周次') }}</div>
            <div class="wk-weeks">
              <div
                v-for="i in 18" :key="i"
                class="wk-opt"
                :class="{ active: i === weekSel, cur: i === curWeek }"
                :data-now="t('本周')"
                @click="weekSel = i"
              >
                {{ t('第{n}周', { n: i }) }}<span v-if="i === curWeek" class="wk-now">{{ t('本周') }}</span>
              </div>
            </div>
          </div>
        </div>
        <div class="wk-sheet-foot">
          <span class="wk-picked">{{ t('已选') }}：<b>第 {{ weekSel }} 周</b><template v-if="semSelLabel"> · {{ semSelLabel }}</template></span>
          <button class="wk-confirm" @click="confirmWeekPicker">{{ t('确认选择') }}</button>
        </div>
      </div>
    </div>
    </Teleport>

    <!-- ===== 课程详情弹窗 ===== -->
    <el-dialog v-model="showClass" :title="currentClass?.name" width="420px">
      <div v-if="currentClass" class="detail-body">
        <!-- 毕业设计课的教师即学生所选毕设导师（学生维度覆盖显示，不落库） -->
        <div><b>{{ t('教师：') }}</b>{{ isThesisCourse(currentClass) && thesisAdvisorName ? thesisAdvisorName : currentClass.teacher }}</div>
        <div><b>{{ t('教室：') }}</b>{{ currentClass.classroom }}</div>
        <div><b>{{ t('时间：') }}</b>{{ currentClass.dayLabel }} {{ t('第 {n} 节', { n: currentClass.period }) }}</div>
        <button class="btn btn-primary btn-sm detail-loc" @click="locateFromDialog">{{ t('地图定位教室') }}</button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import http from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import CampusMap from '@/components/CampusMap.vue'
import CampusMap3D from '@/components/CampusMap3D.vue'
import { buildingOf, CAMPUS_BUILDINGS } from '@/utils/campusConfig'
import { t } from '@/i18n'

const showMap = ref(true)
const mapMode = ref<'3d' | '2d'>('2d')

/* 毕业设计导师：与毕设工作台同源（/workspace/my 会触发「课程教师→毕设导师」同步） */
const thesisAdvisorName = ref('')
async function loadThesisAdvisor() {
  try {
    const r: any = await http.get('/workspace/my')
    const list = r?.success ? (r.data || []) : []
    const w = list.find((x: any) => x.teacher?.name) || list[0]
    thesisAdvisorName.value = w?.teacher?.name || ''
  } catch { thesisAdvisorName.value = '' }
}
const isThesisCourse = (c: any) => !!c && (c.name || '').startsWith('毕业设计')

/* ===== 课表 × 地图联动：教室 → 楼栋定位 ===== */
const map3dRef = ref<any>(null)
const map2dRef = ref<any>(null)
function buildingFromLocation(loc?: string): string | null {
  if (!loc) return null
  const name = buildingOf(String(loc))
  return CAMPUS_BUILDINGS.some(b => b.name === name) ? name : null
}
async function locateOnMap(loc?: string) {
  const b = buildingFromLocation(loc)
  if (!b) { ElMessage.info(t('该课程没有可定位的教室信息')); return }
  const wasHidden = !showMap.value
  showMap.value = true
  mapMode.value = '3d'
  await nextTick()
  // 等 three.js 场景完成初始化（动态 import + 建 mesh）
  await new Promise(r => setTimeout(r, 500))
  map3dRef.value?.focusBuilding(b)
  if (wasHidden) document.querySelector('.campus-section')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
function locateFromDialog() {
  showClass.value = false
  locateOnMap(currentClass.value?.classroom)
}

interface ScheduleItem { courseId: number; name: string; teacher: string; classroom: string; dayOfWeek: number; period: string; time?: string; category?: string; pending?: boolean }
interface Course { id: number; name: string; time?: string; location?: string; credit?: number; campus?: string; teacher?: string; teacherName?: string; category?: string; capacity?: number; stat?: { total?: number } }
interface SemInfo { name?: string; start?: string }

const items = ref<ScheduleItem[]>([])
const period = ref<'week' | 'day'>('week')
const showClass = ref(false)
const currentClass = ref<ScheduleItem | null>(null)
const semInfo = ref<SemInfo | null>(null)
/* ===== 学期切换：可选学期 = 有修读记录的学期（从成绩聚合），确认后重载课表 ===== */
interface SemOptItem { no: number; label: string; year?: string; term?: string }
const semOpts = ref<SemOptItem[]>([])
const semSel = ref<number | null>(null)
const viewSem = ref<number | null>(null)
const semSelLabel = computed(() => {
  const hit = semOpts.value.find(o => o.no === semSel.value)
  return hit ? hit.label : ''
})
const viewSemLabel = computed(() => {
  const hit = semOpts.value.find(o => o.no === viewSem.value)
  return hit ? hit.label : ''
})
const currentSemesterNo = ref<number | null>(null)

function myGrade(): number {
  let g = 0
  try { g = Number(JSON.parse(localStorage.getItem('zhixue_user') || '{}').grade) || 0 } catch {}
  return g
}
function mySemesterNo(): number {
  // 与后端 GraduationService.currentSemesterNo 同公式（从本地登录信息取年级）
  const g = myGrade()
  const t0 = new Date()
  const y = t0.getFullYear(), m = t0.getMonth() + 1
  if (g < 2000) return 1
  return Math.max(1, m >= 8 ? (y - g) * 2 + 1 : (m === 1 ? (y - g) * 2 - 1 : (y - g) * 2))
}
/** 学期序 → 学年学期名（与后端 semesterLabel 同公式） */
function semesterLabelOf(no: number): string {
  const g = myGrade()
  if (g < 2000) return `第 ${no} 学期`
  const startYear = g + Math.floor((no - 1) / 2)
  return `${startYear}-${startYear + 1}学年${no % 2 === 1 ? '第一' : '第二'}学期`
}

async function loadSemOptions() {
  currentSemesterNo.value = mySemesterNo()
  try {
    const r: any = await http.get('/enrollments/scores')
    const set = new Map<number, string>()
    for (const g of (r?.data || [])) {
      if (g.semesterNo != null && !set.has(g.semesterNo)) {
        set.set(g.semesterNo, g.semesterLabel || semesterLabelOf(g.semesterNo))
      }
    }
    const list: SemOptItem[] = [...set.entries()].sort((a, b) => b[0] - a[0]).map(([no, label]) => {
      const m = label.match(/(\d{4}-\d{4})学年(第一|第二)学期/)
      return { no, label, year: m ? m[1] : '', term: m ? m[2] : '' }
    })
    const cur = currentSemesterNo.value
    if (cur != null && !list.some(o => o.no === cur)) {
      list.unshift({ no: cur, label: semesterLabelOf(cur), year: '', term: '' })
    }
    semOpts.value = list
    semSel.value = cur
    viewSem.value = cur
  } catch (e: any) {
    // 非学生角色（教师/管理员）无修读记录：scores 403 时降级为仅当前学期
    semOpts.value = []
  } finally {
    const cur = currentSemesterNo.value
    if (cur != null && !semOpts.value.some(o => o.no === cur)) {
      semOpts.value.unshift({ no: cur, label: semesterLabelOf(cur) })
    }
    semSel.value = cur
    viewSem.value = cur
  }
}
const curWeek = ref(1)
const week = ref(1)
const weekSel = ref(1)
const showWeekPicker = ref(false)
const weekBtn = ref<HTMLElement | null>(null)
const sheetPos = ref({ top: 0, left: 0 })

const cands = ref<Course[]>([])
const checkedIds = ref<number[]>([])
const locks = ref<Set<number>>(new Set())
const generating = ref(false)
const candMap = new Map<number, Course>()

const WEEK0_DEFAULT = new Date(2026, 8, 7) // 2026-09-07
const palette = ['#15803d', '#2563eb', '#0369a1', '#b45309', '#be185d', '#0f766e', '#dc2626', '#0f6f52']
const DAYS = ['日', '一', '二', '三', '四', '五', '六']
const periods = [
  { key: '1-2', from: '08:00', to: '09:40' },
  { key: '3-4', from: '10:00', to: '11:40' },
  { key: '5-6', from: '14:00', to: '15:40' },
  { key: '7-8', from: '16:00', to: '17:40' },
  { key: '9-10', from: '19:00', to: '20:40' },
]

function bgOf(name: string) {
  let h = 0
  for (let i = 0; i < name.length; i++) h = (h * 31 + name.charCodeAt(i)) >>> 0
  return palette[h % palette.length] + 'cc'
}
function cellClasses(day: number, periodKey: string) {
  return items.value.filter(c => c.dayOfWeek === day && c.period === periodKey)
    .map(c => ({ ...c, bg: bgOf(c.name) }))
}

/* ===== 冲突检测：教室（红）/ 同一教师（橙）/ 时间（红） ===== */
interface Conflict { type: 'room' | 'teacher' | 'time'; day: number; period: string; a: ScheduleItem; b: ScheduleItem }
const conflicts = computed<Conflict[]>(() => {
  const out: Conflict[] = []
  const arr = items.value
  for (let i = 0; i < arr.length; i++) {
    for (let j = i + 1; j < arr.length; j++) {
      const a = arr[i], b = arr[j]
      if (a.courseId === b.courseId) continue
      if (a.dayOfWeek !== b.dayOfWeek || a.period !== b.period) continue
      let type: Conflict['type'] = 'time'
      if (a.classroom && a.classroom === b.classroom) type = 'room'
      else if (a.teacher && a.teacher === b.teacher) type = 'teacher'
      out.push({ type, day: a.dayOfWeek, period: a.period, a, b })
    }
  }
  return out
})
function conflictLevel(item: ScheduleItem): 'room' | 'teacher' | null {
  let level: 'room' | 'teacher' | null = null
  for (const c of conflicts.value) {
    if (c.a !== item && c.b !== item) continue
    if (c.type === 'room') return 'room'
    if (c.type !== 'time') level = 'teacher'
  }
  return level
}
const activeKey = ref('')
function blockKey(c: ScheduleItem) { return `${c.courseId}-${c.dayOfWeek}-${c.period}` }
function isConflictsWithActive(c: ScheduleItem): boolean {
  if (!activeKey.value) return false
  return conflicts.value.some(cft => {
    const ka = blockKey(cft.a), kb = blockKey(cft.b)
    return (ka === activeKey.value && kb === blockKey(c)) || (kb === activeKey.value && ka === blockKey(c))
  })
}
const PERIOD_LABELS: Record<string, string> = { '1-2': '一二节', '3-4': '三四节', '5-6': '五六节', '7-8': '七八节', '9-10': '九十节' }
function typeLabel(type: string) { return type === 'room' ? t('教室冲突') : type === 'teacher' ? t('教师冲突') : t('时间冲突') }

/* ===== 冲突解决：优先撤销待录取方；其次退掉非必修的已录取方；两门必修则只能找教务 ===== */
const dropping = ref(false)
function isRequiredItem(it: ScheduleItem) { return (it.category || '').includes('必修') }
function droppableSide(c: Conflict): ScheduleItem | null {
  if (c.a.pending) return c.a
  if (c.b.pending) return c.b
  const aReq = isRequiredItem(c.a), bReq = isRequiredItem(c.b)
  if (aReq && bReq) return null
  return aReq ? c.b : c.a
}
function confHint(c: Conflict): string {
  const t2 = droppableSide(c)
  if (!t2) return t('两门均为必修课（系统自动安排），如需调整请联系教务处')
  return t2.pending ? t('撤销后积分将退回，冲突自动解除') : t('退掉这门选修课即可解除冲突，积分将退还')
}
async function resolveConflict(c: Conflict) {
  const target = droppableSide(c)
  if (!target || dropping.value) return
  const confirmed = await ElMessageBox.confirm(
    target.pending ? t('撤销「{name}」的待录取报名？积分将全额退回。', { name: target.name })
                   : t('确定退掉「{name}」？退课后积分将退还。', { name: target.name }),
    t('解决时间冲突'),
    { confirmButtonText: t('确定'), cancelButtonText: t('取消'), type: 'warning' },
  ).then(() => true).catch(() => false)
  if (!confirmed) return
  dropping.value = true
  try {
    await http.post('/enrollments/drop', { courseId: target.courseId })
    ElMessage.success(t('冲突已解决，课表已更新'))
    await loadSchedule()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || t('操作失败，请重试'))
  }
  dropping.value = false
}
const week0 = computed(() => {
  const s = semInfo.value
  if (s?.start) {
    const d = new Date(s.start)
    if (!isNaN(d.getTime())) return d
  }
  return WEEK0_DEFAULT
})
const mondayOfWeek = computed(() => {
  const m = new Date(week0.value)
  m.setDate(m.getDate() + (week.value - 1) * 7)
  return m
})
const monthLabel = computed(() => {
  const m = mondayOfWeek.value
  const next = new Date(m); next.setDate(next.getDate() + 6)
  if (m.getMonth() === next.getMonth()) return t('{n}月', { n: m.getMonth() + 1 })
  return t('{a}月-{b}月', { a: m.getMonth() + 1, b: next.getMonth() + 1 })
})
const weekDays = computed(() => {
  const today = new Date()
  const curDow = today.getDay() || 7
  const arr = []
  for (let i = 0; i < 7; i++) {
    const dt = new Date(mondayOfWeek.value)
    dt.setDate(dt.getDate() + i)
    arr.push({
      key: i + 1,
      name: t('周') + t(DAYS[(i + 1) % 7]),
      num: dt.getDate(),
      isToday: dt.toDateString() === today.toDateString(),
    })
  }
  return arr
})
const todayClasses = computed(() => {
  const curDow = new Date().getDay() || 7
  return items.value.filter(c => c.dayOfWeek === curDow).map(c => ({
    ...c,
    timeLabel: (() => {
      const p = periods.find(pp => pp.key === c.period)
      return p ? `${p.from}-${p.to}` : ''
    })(),
  }))
})
const dayLabel = computed(() => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')} ${t('周')}${t(DAYS[d.getDay()])}`
})

function openWeekPicker() {
  if (weekBtn.value) {
    const r = weekBtn.value.getBoundingClientRect()
    sheetPos.value = { top: r.bottom + 4, left: r.left }
  }
  weekSel.value = week.value
  semSel.value = viewSem.value
  showWeekPicker.value = true
}
function changeWeek(d: number) {
  const n = Math.min(18, Math.max(1, week.value + d))
  if (n !== week.value) week.value = n
}
async function confirmWeekPicker() {
  week.value = weekSel.value
  const semChanged = semSel.value != null && semSel.value !== viewSem.value
  viewSem.value = semSel.value
  showWeekPicker.value = false
  if (semChanged) await loadSchedule()
}
function viewClass(c: any) { currentClass.value = c; showClass.value = true }
function statOf(id: number): string {
  const c = candMap.get(id)
  if (!c) return '—'
  return `${(c.stat?.total || 0)}/${(c.capacity || 0)}`
}
function toggleLock(id: number) {
  const s = locks.value
  if (s.has(id)) s.delete(id); else s.add(id)
  locks.value = new Set(s)
}

/* ===== 实时推荐分析：勾选变化即刻重算（全部基于真实课程数据，前端计算） ===== */
/** 已勾选课程 id 集合（避免模板/计算属性里反复 includes） */
const checkedSet = computed(() => new Set(checkedIds.value))
/** 已勾选课程总学分 */
const checkedCredits = computed(() =>
  cands.value.filter(c => checkedSet.value.has(c.id)).reduce((s, c) => s + (c.credit || 0), 0)
)
/** 已勾选且锁定的课程数（重新生成时后端优先保留，见 generate() 锁定占位逻辑） */
const lockedCheckedCount = computed(() => checkedIds.value.filter(id => locks.value.has(id)).length)

/**
 * 勾选课程间的实时时间冲突：按 parseSchedule 解析出的「周几 + 节次时段」真实重叠判定。
 * 同时检测与已录取课表（items）的冲突。冲突课程不会被自动取消，仅在行内与概览中提示。
 */
const checkedConflicts = computed<Map<number, { name: string; enrolled: boolean }[]>>(() => {
  const m = new Map<number, { name: string; enrolled: boolean }[]>()
  const push = (id: number, name: string, enrolled: boolean) => {
    const arr = m.get(id) || []
    if (!arr.some(x => x.name === name)) arr.push({ name, enrolled })
    m.set(id, arr)
  }
  const arr = cands.value.filter(c => checkedSet.value.has(c.id))
  const slotKeys = new Map<number, string[]>(arr.map(c => [c.id, slotsKeyOf(c)]))
  // 勾选课程两两判冲突
  for (let i = 0; i < arr.length; i++) {
    for (let j = i + 1; j < arr.length; j++) {
      const ka = new Set(slotKeys.get(arr[i].id)!)
      if ((slotKeys.get(arr[j].id)!).some(k => ka.has(k))) {
        push(arr[i].id, arr[j].name, false)
        push(arr[j].id, arr[i].name, false)
      }
    }
  }
  // 与已录取课表判冲突
  const enrolled = items.value.map(it => ({ key: it.dayOfWeek + '-' + it.period, name: it.name }))
  for (const c of arr) {
    const ks = new Set(slotKeys.get(c.id) || [])
    for (const it of enrolled) if (ks.has(it.key)) push(c.id, it.name, true)
  }
  return m
})
/** 时间冲突的课程数（用于标题栏统计） */
const conflictCourseCount = computed(() => checkedConflicts.value.size)
/** 某课程当前的冲突列表（无冲突返回空数组） */
function rowConflict(c: Course): { name: string; enrolled: boolean }[] {
  return checkedConflicts.value.get(c.id) || []
}
function slotsKeyOf(c: Course): string[] {
  return courseSlots(c).map(s => s.day + '-' + s.period)
}

/** 推荐标签：有真实判断依据（锁定态 > 冲突态 > 学分×竞争度），无数据不虚构 */
function recTag(c: Course): { label: string; cls: string; tip: string } {
  if (locks.value.has(c.id)) return { label: t('🔒 已锁定'), cls: 'lock', tip: t('生成方案时优先占位保留') }
  if (rowConflict(c).length) return { label: t('⚠ 时间冲突'), cls: 'conf', tip: t('与勾选课程或已录取课表时间重叠') }
  const ratio = (c.stat?.total || 0) / Math.max(1, c.capacity || 1)
  const credit = c.credit || 0
  if (credit >= 3 && ratio <= 0.6) return { label: t('⭐ 优先推荐'), cls: 'best', tip: t('学分较高且竞争较小') }
  if (ratio <= 0.8) return { label: t('👍 推荐'), cls: 'good', tip: t('竞争相对适中') }
  return { label: t('可选'), cls: 'ok', tip: t('常规可选课程') }
}

/** 推荐理由：逐条来自真实数据（时间解析 / 冲突 / 学分 / 竞争度 / 锁定） */
function recReason(c: Course): string {
  const parts: string[] = []
  const slots = courseSlots(c)
  if (!slots.length) {
    parts.push(t('上课时间无法解析，暂不参与冲突检测'))
  } else {
    const cf = rowConflict(c)
    parts.push(cf.length ? t('与 {n} 门课程时间重叠', { n: cf.length }) : t('与已选课程时间不冲突'))
  }
  const credit = c.credit || 0
  if (credit >= 3) parts.push(t('{n} 学分，有助于凑满总学分', { n: credit }))
  const cap = c.capacity || 0
  if (cap > 0) {
    const ratio = (c.stat?.total || 0) / cap
    if (ratio <= 0.6) parts.push(t('竞争较小（{a}/{b}）', { a: c.stat?.total || 0, b: cap }))
    else if (ratio > 1) parts.push(t('报名已超容量（{a}/{b}）', { a: c.stat?.total || 0, b: cap }))
  }
  if (locks.value.has(c.id)) parts.push(t('已锁定，重新推荐时优先保留'))
  return parts.join(' · ')
}

/** 组合概览的推荐原因：来自方案真实策略，不虚构后端算法 */
function planReason(pl: Plan): string {
  if (pl.strategy.includes('学分')) return t('优先保留学分高的课程，在互不冲突前提下组合总学分最大')
  if (pl.strategy.includes('竞争')) return t('优先选择报名/容量比低的课程，降低落选风险')
  return t('在可行组合中做随机均衡采样，供对比不同取舍')
}

/* ===== #8 多方案生成：三种贪心策略，全部互不冲突，可对比 ===== */
interface Plan {
  courses: (Course & { courseId: number })[]
  credits: number
  freeSlots: number
  excluded: number[]
  conflicts: string[]
  strategy: string
}
const plans = ref<Plan[]>([])
const planIdx = ref(0)
const activePlan = computed(() => plans.value[planIdx.value] || null)

/** 课程占用的 (day, period) 集合；locks 中的课程优先占位 */
function occupyOf(c: Course): { day: number; period: string }[] {
  return courseSlots(c)
}
function planTag(pl: Plan): { label: string; cls: string } {
  if (!pl.courses.length) return { label: t('无可行组合'), cls: 'bad' }
  if (pl.strategy.includes('学分')) return { label: t('学分最多'), cls: 'credit' }
  if (pl.strategy.includes('竞争')) return { label: t('竞争最小'), cls: 'easy' }
  return { label: t('均衡组合'), cls: 'mix' }
}

function generate() {
  if (!checkedIds.value.length) { ElMessage.warning(t('请至少勾选一门候选课程')); return }
  generating.value = true
  try {
    const chosen = cands.value.filter(c => checkedIds.value.includes(c.id))
    const locked = chosen.filter(c => locks.value.has(c.id))
    // 5×7 网格共 35 格
    const totalSlots = periods.length * 7
    const strategies: { name: string; sortFn: (a: Course, b: Course) => number; jitter: boolean }[] = [
      { name: '学分优先', sortFn: (a, b) => (b.credit || 0) - (a.credit || 0), jitter: false },
      { name: '竞争最小', sortFn: (a, b) => ((a.stat?.total || 0) / Math.max(1, a.capacity || 1)) - ((b.stat?.total || 0) / Math.max(1, b.capacity || 1)), jitter: false },
      { name: '均衡扰动', sortFn: () => Math.random() - 0.5, jitter: true },
    ]
    const out: Plan[] = []
    const coveredByAny = new Set<number>()
    for (const st of strategies) {
      const occupied = new Map<string, string>()   // "day-period" -> name
      // 已录取课表先占格（含待录取）：推荐方案不得与现有课表撞车
      for (const it of items.value) {
        const key = it.dayOfWeek + '-' + it.period
        if (!occupied.has(key)) occupied.set(key, it.name)
      }
      // 🔒 锁定课程再占格（与已录取课表或彼此冲突则记入方案问题）
      const conflicts: string[] = []
      for (const c of locked) {
        for (const s of occupyOf(c)) {
          const key = s.day + '-' + s.period
          if (occupied.has(key)) conflicts.push(`${c.name} 与 ${occupied.get(key)} 在 ${key} 冲突`)
          else occupied.set(key, c.name)
        }
      }
      const rest = chosen.filter(c => !locks.value.has(c.id)).sort(st.sortFn)
      const picked: (Course & { courseId: number })[] = []
      const excluded: number[] = []
      for (const c of rest) {
        const slots = occupyOf(c)
        if (!slots.length) { excluded.push(c.id); continue }
        const clash = slots.some(s => occupied.has(s.day + '-' + s.period))
        if (clash) { excluded.push(c.id); continue }
        for (const s of slots) occupied.set(s.day + '-' + s.period, c.name)
        picked.push({ ...c, courseId: c.id })
      }
      for (const p of picked) coveredByAny.add(p.courseId)
      // 方案内课程多时段课程占用可能重复占同一格（同一课两节在同一格），freeSlots 按去重格子算
      const credits = picked.reduce((s, c) => s + (c.credit || 0), 0)
      out.push({ courses: picked, credits, freeSlots: totalSlots - occupied.size, excluded, conflicts, strategy: st.name })
    }
    // 排除列表 = 从未进入任何方案的课
    for (const pl of out) pl.excluded = pl.excluded.filter(id => !coveredByAny.has(id) && !locks.value.has(id))
    out.sort((a, b) => b.credits - a.credits)
    plans.value = out
    planIdx.value = 0
    const best = out[0]
    if (!best || !best.courses.length) ElMessage.error(t('勾选的课程全部互相冲突，没有可行方案'))
    else ElMessage.success(t('已生成 {n} 套方案，最优方案 {a} 门 / {b} 学分', { n: out.length, a: best.courses.length, b: best.credits }))
  } finally { generating.value = false }
}
function resetGen() {
  checkedIds.value = []
  locks.value = new Set()
  plans.value = []
  planIdx.value = 0
}
async function addIntents() {
  const list = activePlan.value?.courses || []
  if (!list.length) return
  const token = localStorage.getItem('zhixue_token')
  let ok = 0
  for (const c of list) {
    try {
      await fetch('/api/intents', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
        body: JSON.stringify({ courseId: c.courseId }),
      })
      ok++
    } catch {}
  }
  ElMessage.success(ok ? t('已把方案 {a} 的 {b} 门课程加入意向单', { a: planIdx.value + 1, b: ok }) : t('加入失败'))
}

/** 课表周视图导出为 PNG 图片（html2canvas 按需加载） */
const gridRef = ref<HTMLElement | null>(null)
async function exportPng() {
  const el = gridRef.value
  if (!el) { ElMessage.warning(t('请切到周视图后再导出图片')); return }
  try {
    const html2canvas = (await import('html2canvas')).default
    const page = document.querySelector('.page-wrap') || document.body
    const bg = getComputedStyle(page as Element).backgroundColor
    const canvas = await html2canvas(el, {
      backgroundColor: bg && bg !== 'transparent' ? bg : '#0f172a',
      scale: 2, useCORS: true, logging: false,
    })
    const a = document.createElement('a')
    a.href = canvas.toDataURL('image/png')
    a.download = `${t('课表_第{n}周', { n: week.value })}.png`
    document.body.appendChild(a); a.click(); a.remove()
    ElMessage.success(t('课表图片已导出'))
  } catch (e: any) {
    ElMessage.error(e?.message || t('导出失败，请重试'))
  }
}

function exportIcs() {
  if (!items.value.length) { ElMessage.error(t('暂无可导出的课表')); return }
  const pad = (n: number) => String(n).padStart(2, '0')
  const dtUTC = (d: Date, t: string) => {
    const [hh, mm] = (t || '00:00').split(':').map(Number)
    const dt = new Date(d); dt.setHours(hh, mm, 0, 0)
    return dt.toISOString().replace(/[-:]/g, '').slice(0, 15) + 'Z'
  }
  const lines = ['BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//Zhixue//Schedule//CN', 'CALSCALE:GREGORIAN']
  const seen = new Set<string>()
  items.value.forEach((c, idx) => {
    const key = `${c.courseId}-${c.dayOfWeek}-${c.period}`
    if (seen.has(key)) return; seen.add(key)
    const ti = periods.findIndex(p => p.key === c.period)
    if (ti < 0) return
    const start = periods[ti].from
    const end = periods[ti].to
    const day = new Date(mondayOfWeek.value)
    day.setDate(day.getDate() + c.dayOfWeek - 1)
    lines.push('BEGIN:VEVENT')
    lines.push('UID:zhixue-' + (c.courseId || idx) + '-' + c.dayOfWeek + '-' + c.period + '@zhixue.local')
    lines.push('DTSTAMP:' + new Date().toISOString().replace(/[-:]/g, '').slice(0, 15) + 'Z')
    lines.push('DTSTART:' + dtUTC(day, start))
    lines.push('DTEND:' + dtUTC(day, end))
    lines.push('SUMMARY:' + (c.name || t('课程')))
    lines.push('LOCATION:' + (c.classroom || ''))
    lines.push('DESCRIPTION:' + (c.teacher ? t('教师:') + c.teacher : '') + t('每周重复，共16周'))
    lines.push('RRULE:FREQ=WEEKLY;COUNT=16')
    lines.push('END:VEVENT')
  })
  lines.push('END:VCALENDAR')
  const blob = new Blob([lines.join('\r\n')], { type: 'text/calendar;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = 'zhixue-schedule.ics'
  document.body.appendChild(a); a.click(); a.remove()
  setTimeout(() => URL.revokeObjectURL(a.href), 2000)
  ElMessage.success(t('已导出 .ics 日历（16周重复），可用手机日历 / Outlook / Google 日历导入'))
}

onMounted(async () => {
  // 0) 学期选项（供周次弹窗切换）
  await loadSemOptions()

  // 1) 学期信息
  try {
    const semR: any = await http.get('/semesters/current')
    semInfo.value = semR?.data?.name ? { name: semR.data.name, start: semR.data.startDate } : null
  } catch { semInfo.value = null }
  loadThesisAdvisor()   // 毕业设计导师（触发后端「课程教师→导师」同步）

  // 2) 当前周次
  const today = new Date()
  const w0 = semInfo.value?.start ? new Date(semInfo.value.start) : WEEK0_DEFAULT
  curWeek.value = Math.min(18, Math.max(1, Math.floor((today.getTime() - w0.getTime()) / 86400000 / 7) + 1))
  week.value = curWeek.value
  weekSel.value = curWeek.value

  // 3) 我的课表（含待录取：冲突检测需要看到全部占用时段）
  await loadSchedule()
})

async function loadSchedule() {
  try {
    const q = viewSem.value != null ? `?semester=${viewSem.value}` : ''
    const mineR: any = await http.get('/enrollments/mine' + q)
    const mine = mineR?.data || []
    // 课表只显示本学期修读的课（mine 已按学期归属过滤）；历史学期课程在成绩单与毕业进度中查看
    const courseIds = [...new Set(mine.map((e: any) => e.courseId))]
    // 每门课的报名状态：approved 优先于 pending
    const statusOf = new Map<number, string>()
    for (const e of mine) {
      if (e.status === 'approved' || !statusOf.has(e.courseId)) statusOf.set(e.courseId, e.status)
    }
    for (const e of mine) {
      if (e.status === 'approved') statusOf.set(e.courseId, 'approved')
    }
    const allR: any = await http.get('/courses')
    const allCourses = allR?.data || []
    items.value = allCourses
      .filter((c: any) => courseIds.includes(c.id))
      .flatMap((c: any) => parseSchedule(c).map((it: ScheduleItem) => ({
        ...it, category: c.category, pending: statusOf.get(c.id) !== 'approved',
      })))

    // 4) 智能推荐候选（必修课已自动排入课表，不再作为候选）
    const curIds = new Set(courseIds)
    cands.value = allCourses.filter((c: any) =>
      !curIds.has(c.id) && !(c.category || '').includes('必修')
    )
    cands.value.forEach(c => candMap.set(c.id, c))
    checkedIds.value = cands.value.map(c => c.id)
  } catch {
    ElMessage.error(t('课表数据加载失败（服务可能刚重启），请刷新页面重试'))
  }
}

/* ===== #1 拖拽试排：把候选课拖上周课表，本地预览 + 冲突高亮 ===== */
interface DraftBlock { key: string; courseId: number; name: string; day: number; period: string; conflicts: { name: string }[] }
const drafts = ref<DraftBlock[]>([])
const dragOpen = ref(false)
const dragOver = ref<{ day: number; period: string } | null>(null)
let dragging: Course | null = null
const usedDraftIds = computed(() => new Set(drafts.value.map(d => d.courseId)))
/** 工具条候选：未放入试排的排前面 */
const dragCandidates = computed(() => {
  const used = usedDraftIds.value
  return [...cands.value.filter(c => !used.has(c.id)), ...cands.value.filter(c => used.has(c.id))]
})
function onDragStart(e: DragEvent, c: Course) {
  dragging = c
  e.dataTransfer?.setData('text/plain', String(c.id))
  if (e.dataTransfer) e.dataTransfer.effectAllowed = 'copy'
}
function courseSlots(course: Course): { day: number; period: string }[] {
  return parseSchedule({ ...course, teacherName: '', classroom: '' }).map(s => ({ day: s.dayOfWeek, period: s.period }))
}
function onDrop(_e: DragEvent, day: number, period: string) {
  dragOver.value = null
  const c = dragging
  dragging = null
  if (!c) return
  const slots = courseSlots(c)
  if (!slots.length) { ElMessage.warning(t('{n} 没有可解析的上课时间', { n: c.name })); return }
  // 该课的所有时段都必须落到这个格子的 day+period 才算放对了位置；多时段课按首个时段对齐提示
  if (!slots.some(s => s.day === day && s.period === period)) {
    ElMessage.warning(t('{n} 的上课时间是「{t}」，请拖到对应格子', { n: c.name, t: c.time }))
    return
  }
  if (drafts.value.some(d => d.courseId === c.id)) { ElMessage.info(t('该课已在试排中')); return }
  // 冲突检测：与已录取课表 + 其他试排块
  const conflicts: { name: string }[] = []
  for (const it of items.value) {
    if (it.dayOfWeek === day && it.period === period && it.courseId !== c.id) conflicts.push({ name: it.name })
  }
  for (const d of drafts.value) {
    if (d.courseId === c.id) continue
    if (d.day === day && d.period === period) conflicts.push({ name: d.name + t('（试排）') })
  }
  drafts.value.push({ key: `${c.id}-${day}-${period}-${Date.now()}`, courseId: c.id, name: c.name, day, period, conflicts })
  if (conflicts.length) ElMessage.warning(t('{n} 试排存在 {m} 处时间冲突', { n: c.name, m: conflicts.length }))
  else ElMessage.success(t('{n} 试排成功，无冲突', { n: c.name }))
}
function draftAt(day: number, period: string) {
  return drafts.value.filter(d => d.day === day && d.period === period)
}
function removeDraft(key: string) {
  drafts.value = drafts.value.filter(d => d.key !== key)
}
/** 试排块之间的冲突对（用于冲突列表） */
const draftConflicts = computed(() => {
  const out: { a: string; b: string; day: number; period: string }[] = []
  const arr = drafts.value
  for (let i = 0; i < arr.length; i++) {
    for (let j = i + 1; j < arr.length; j++) {
      if (arr[i].day === arr[j].day && arr[i].period === arr[j].period && arr[i].courseId !== arr[j].courseId) {
        out.push({ a: arr[i].name, b: arr[j].name, day: arr[i].day, period: arr[i].period })
      }
    }
  }
  return out
})

function parseSchedule(course: any): ScheduleItem[] {  const WEEK_MAP: Record<string, number> = {
    '周一': 1, '周二': 2, '周三': 3, '周四': 4, '周五': 5, '周六': 6, '周日': 7,
  }
  function timeToPeriod(raw: string): string | null {
    const hh = raw.split(':')[0]
    const h = parseInt(hh, 10)
    if (h === 8)  return '1-2'
    if (h === 10) return '3-4'
    if (h === 14) return '5-6'
    if (h === 16) return '7-8'
    if (h === 19) return '9-10'
    return null
  }
  const raw: string = course.time || course.schedule || ''
  if (!raw) return []
  const out: ScheduleItem[] = []
  const re = /(周[一二三四五六日天])\s*(\d{1,2}:\d{2})\s*-\s*(\d{1,2}:\d{2})/g
  let m
  while ((m = re.exec(raw))) {
    const dayKey = m[1]
    const from = m[2]
    const day = WEEK_MAP[dayKey]
    const period = timeToPeriod(from)
    if (day && period) {
      out.push({
        courseId: course.id,
        name: course.name,
        teacher: course.teacherName || course.teacher,
        classroom: course.classroom || course.location,
        dayOfWeek: day,
        period,
        time: raw,
      })
    }
  }
  return out
}
</script>

<style scoped>
.zx-page { display: flex; flex-direction: column; gap: 14px; }
.page-head { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 10px; }
.page-title { font-size: 20px; font-weight: 700; color: var(--text); }
.page-sub { display: block; font-size: 12px; color: var(--text-light); font-weight: 400; margin-top: 2px; }

/* ===== 周次导航 ===== */
.week-nav {
  display: flex; align-items: center; gap: 8px;
  background: var(--card); border: 1px solid var(--border);
  border-radius: 10px; padding: 8px 12px; box-shadow: var(--shadow);
}
.wn-btn {
  width: 32px; height: 32px; border-radius: 8px; border: 1px solid var(--border);
  background: var(--bg); color: var(--text); font-size: 18px; line-height: 1;
  cursor: pointer; display: flex; align-items: center; justify-content: center;
  transition: all .15s;
}
.wn-btn:hover:not(:disabled) { border-color: var(--primary); color: var(--primary); }
.wn-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.wn-center {
  display: flex; align-items: center; gap: 4px;
  padding: 6px 18px; border-radius: 8px;
  background: linear-gradient(135deg, rgba(43,177,135,0.15), rgba(56,189,248,0.15));
  border: 1px solid rgba(43,177,135,0.3);
  cursor: pointer; transition: all .15s; color: var(--text);
  font-size: 14px;
}
.wn-center:hover { border-color: var(--primary); box-shadow: 0 2px 8px rgba(0,0,0,.16); }
.wn-sem { font-weight: 600; color: var(--primary); }
.wn-sep { color: var(--text-light); }
.wn-week { font-weight: 700; color: var(--primary); font-size: 16px; }
.wn-chev { color: var(--text-light); margin-left: 4px; }
.wn-reset {
  padding: 6px 14px; border-radius: 8px; border: 1px solid var(--border);
  background: var(--bg); color: var(--text-light); font-size: 13px; cursor: pointer;
  transition: all .15s;
}
.wn-reset:hover { border-color: var(--primary); color: var(--primary); }
.wn-export {
  padding: 6px 14px; border-radius: 8px; border: none;
  background: var(--primary-deep);
  color: #fff; font-size: 13px; font-weight: 500; cursor: pointer;
  transition: all .15s; margin-left: auto;
}
.wn-export:hover { transform: translateY(-1px); box-shadow: 0 4px 12px rgba(43,177,135,0.4); }

/* ===== 课表网格 ===== */
.schedule-grid { background: var(--card); border: 1px solid var(--grid-line); border-radius: 12px; overflow: hidden; }
.sg-header { display: grid; grid-template-columns: 80px repeat(7, 1fr); border-bottom: 1px solid var(--grid-line); }
.sg-row { display: grid; grid-template-columns: 80px repeat(7, 1fr); border-top: 1px solid var(--grid-line); }
.sg-cell { border-right: 1px solid var(--grid-line); padding: 6px; min-height: 64px; display: flex; flex-direction: column; align-items: center; justify-content: flex-start; gap: 4px; }
.sg-cell.time-col { justify-content: center; background: var(--bg); }
.sg-cell:last-child { border-right: none; }
.sg-name { font-size: 12px; color: var(--text-light); }
.sg-num { font-size: 14px; font-weight: 700; color: var(--text); }
.sg-num.today { color: #fff; background: var(--primary-deep); border-radius: 50%; width: 24px; height: 24px; display: flex; align-items: center; justify-content: center; }
.sg-month { font-size: 12px; color: var(--green-text); font-weight: 600; }
.ti-label { font-size: 12px; font-weight: 600; color: var(--green-text); }
.ti-time { font-size: 12px; color: var(--text-light); }
.class-block { width: 100%; border-radius: 6px; padding: 5px 7px; cursor: pointer; color: #fff; text-align: left; transition: transform .1s; margin-top: 2px; }
.class-block:hover { transform: scale(1.02); }
.cb-name { font-size: 12px; font-weight: 600; line-height: 1.2; }
.cb-meta { font-size: 12px; opacity: 0.9; margin-top: 1px; }

/* ===== 冲突标记 ===== */
.class-block.cf-room { outline: 2px solid #ef4444; outline-offset: 1px; }
.class-block.cf-teacher { outline: 2px solid #f59e0b; outline-offset: 1px; }
.cf-badge {
  font-size: 8.5px; font-weight: 700; padding: 0 5px; border-radius: 5px;
  margin-top: 2px; display: inline-block; color: #fff;
}
.cf-badge.room { background: #ef4444; }
.cf-badge.teacher { background: #f59e0b; }
.class-block.cf-glow { animation: cfGlow 1s ease-in-out infinite; }
@keyframes cfGlow { 0%, 100% { box-shadow: 0 0 0 0 rgba(43,177,135,.0); } 50% { box-shadow: 0 0 0 3px rgba(43,177,135,.65); } }

/* 冲突列表 */
.conflict-section .section-sub { font-size: 12px; color: var(--text-light); }
.conf-list { display: flex; flex-direction: column; gap: 8px; }
.conf-item {
  display: flex; align-items: center; gap: 12px; padding: 10px 14px;
  border: 1px solid var(--border); border-radius: 10px; background: var(--card);
}
.conf-item.t-room { border-left: 4px solid #ef4444; }
.conf-item.t-teacher { border-left: 4px solid #f59e0b; }
.conf-item.t-time { border-left: 4px solid #ef4444; }
.conf-badge { font-size: 12px; font-weight: 700; padding: 2px 10px; border-radius: 999px; color: #fff; flex-shrink: 0; }
.conf-item.t-room .conf-badge { background: #ef4444; }
.conf-item.t-teacher .conf-badge { background: #f59e0b; }
.conf-item.t-time .conf-badge { background: #ef4444; }
.conf-body { font-size: 13px; color: var(--text); }
.conf-pair b { color: var(--primary); }
.conf-body span { display: block; font-size: 12px; color: var(--text-light); margin-top: 2px; }
/* 修复：液态玻璃页给 .section-header 设了 sticky(top:64px, z-10)，滚动时冲突标题悬浮盖住列表。
   课表页标题不需要吸附，恢复普通文档流（优先级需压过 original.css 的 .stats-page.liquid 链） */
.stats-page.liquid .conflict-section > .section-header,
.stats-page.liquid .gen-section > .section-header,
.stats-page.liquid .campus-section > .section-header { position: static !important; }
.conf-item { flex-wrap: wrap; }
/* 解决动作：撤销待录取 / 退掉冲突选修 */
.conf-act { margin-left: auto; display: flex; flex-direction: column; align-items: flex-end; gap: 4px; flex-shrink: 0; }
.conf-fix-btn {
  border: 1px solid rgba(220,38,38,.45); background: #fef2f2; color: #dc2626;
  font-size: 12.5px; font-weight: 600; padding: 6px 14px; border-radius: 999px; cursor: pointer;
  transition: background .2s ease-out, color .2s ease-out, transform .18s ease-out, box-shadow .2s ease-out;
}
.conf-fix-btn:hover { background: #dc2626; color: #fff; box-shadow: 0 4px 12px rgba(220,38,38,.3); }
.conf-fix-btn:active { transform: scale(.97); }
.conf-fix-btn:focus-visible { outline: 2px solid #dc2626; outline-offset: 2px; }
.conf-fix-btn:disabled { opacity: .6; cursor: not-allowed; }
.conf-act-hint { font-size: 11.5px; color: var(--text-light); max-width: 280px; text-align: right; }
.conf-pend-tag { font-style: normal; font-size: 11px; color: #b45309; background: rgba(217,119,6,.12); padding: 1px 7px; border-radius: 999px; }
html[data-theme="dark"] .conf-fix-btn { background: rgba(220,38,38,.14); }
html[data-theme="dark"] .conf-fix-btn:hover { background: #dc2626; color: #fff; }
html[data-theme="dark"] .conf-pend-tag { background: rgba(217,119,6,.22); }

/* ===== 日视图 ===== */
.day-view { display: flex; flex-direction: column; gap: 10px; }
.dv-date { font-size: 16px; color: var(--green-text); font-weight: 600; }
.dv-list { display: flex; flex-direction: column; gap: 10px; }
.dv-card { background: var(--card); border: 1px solid var(--border); border-radius: 8px; padding: 12px 16px; }
.dv-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.dv-name { font-size: 15px; color: var(--text); font-weight: 600; }
.dv-time { font-size: 13px; color: var(--green-text); }
.dv-meta { font-size: 12px; color: var(--text-light); display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.dv-loc-btn { font-size: 12px; padding: 4px 11px; border-radius: 999px; border: 1px solid var(--border); background: rgba(43,177,135,.1); color: #4cc9a3; cursor: pointer; transition: all .15s; }
.dv-loc-btn:hover { background: rgba(43,177,135,.2); border-color: #4cc9a3; }
.dv-loc-btn:disabled { opacity: .4; cursor: not-allowed; }

/* ===== 周次选择器 ===== */
.wk-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.35);
  z-index: 2000;
  display: flex; align-items: center; justify-content: center;
}
.wk-sheet {
  position: relative;
  top: auto !important; left: auto !important;
  max-height: 86vh; overflow-y: auto;
}

/* ===== 方案一：左右分栏式（学期+周次分区精美版） ===== */
.wk-sheet {
  width: 640px;
  max-width: 92vw;
  border-radius: 16px;
  padding: 0;
  overflow: hidden;
}
.wk-sheet-head {
  padding: 14px 18px;
  border-bottom: 1px solid var(--border);
  background: color-mix(in srgb, var(--card-bg) 60%, transparent);
}
.wk-sheet-title { font-size: 15px; font-weight: 800; color: var(--text); }
.wk-body { padding: 18px; gap: 24px; align-items: stretch; }
.wk-col { flex: 1; min-width: 0; }
.wk-col-title { font-size: 12.5px; font-weight: 700; color: var(--text-light); margin-bottom: 10px; }
.wk-sems { display: flex; flex-direction: column; gap: 8px; max-height: 320px; overflow-y: auto; padding-right: 4px; }
.wk-sem-item {
  position: relative;
  padding: 10px 14px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: transparent;
  cursor: pointer;
  transition: all .18s;
}
.wk-sem-item:hover { border-color: rgba(43,177,135,.5); background: rgba(43,177,135,.06); }
.wk-sem-item.active {
  border-color: transparent;
  background: linear-gradient(135deg, rgba(43,177,135,.16), rgba(43,177,135,.08));
}
.wk-sem-item.active::after {
  content: '✓';
  position: absolute; right: 12px; top: 50%;
  transform: translateY(-50%);
  width: 20px; height: 20px; border-radius: 50%;
  background: var(--primary); color: #fff;
  font-size: 12px; font-weight: 800;
  display: flex; align-items: center; justify-content: center;
}
.wk-sem-year { display: block; font-size: 14px; font-weight: 800; color: var(--text); }
.wk-sem-item.active .wk-sem-year { color: var(--primary); }
.wk-sem-term { display: block; font-size: 12px; color: var(--text-light); margin-top: 2px; }
.wk-sem-item.cur .wk-sem-term::after { content: '·本学期'; color: var(--primary); font-weight: 700; }
.wk-weeks { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; max-height: 320px; overflow-y: auto; padding-right: 4px; }
.wk-opt {
  text-align: center;
  padding: 10px 0;
  border-radius: 10px;
  border: 1px solid var(--border);
  background: transparent;
  cursor: pointer;
  font-size: 13px; font-weight: 600; color: var(--text);
  transition: all .18s;
  position: relative;
}
.wk-opt:hover { border-color: rgba(43,177,135,.5); background: rgba(43,177,135,.06); }
.wk-opt.active {
  border-color: transparent;
  background: var(--primary); color: #fff;
  box-shadow: 0 4px 12px rgba(43,177,135,.35);
}
.wk-opt.cur:not(.active) { background: rgba(43,177,135,.10); border-color: transparent; }
.wk-now {
  position: absolute; top: -7px; left: 50%; transform: translateX(-50%);
  font-size: 10px; font-weight: 700; color: #fff;
  background: #f59e0b; padding: 1px 6px; border-radius: 999px;
}
.wk-sheet-foot {
  display: flex; align-items: center; justify-content: flex-end; gap: 12px;
  padding: 12px 18px; border-top: 1px solid var(--border);
  background: color-mix(in srgb, var(--card-bg) 60%, transparent);
}
.wk-picked { font-size: 12.5px; color: var(--text-light); margin-right: auto; }
.wk-picked b { color: var(--primary); font-weight: 800; }
.wk-confirm {
  border: none; cursor: pointer; font-family: inherit;
  font-size: 13px; font-weight: 800; color: #fff;
  background: linear-gradient(135deg, #2bb187, #0d9488);
  padding: 9px 24px; border-radius: 999px;
  box-shadow: 0 4px 12px rgba(43,177,135,.35);
  transition: transform .15s, box-shadow .2s;
}
.wk-confirm:hover { transform: translateY(-1px); box-shadow: 0 6px 16px rgba(43,177,135,.45); }
.wk-head-spacer { width: 40px; flex-shrink: 0; }

/* ===== 智能选课推荐板块样式系统（补齐缺失定义） ===== */
/* -- 标题栏 -- */
.gen-head { display: flex; justify-content: space-between; align-items: flex-end; gap: 16px; flex-wrap: wrap; margin-bottom: 14px; }
.gen-head-l { display: flex; flex-direction: column; gap: 4px; }
.gen-head-l .section-sub { font-size: 12.5px; color: var(--text-light); }
.gen-head-r { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
.gen-stat { text-align: center; padding: 6px 14px; border-radius: 12px; background: color-mix(in srgb, var(--card-bg) 65%, transparent); border: 1px solid var(--border); min-width: 74px; }
.gen-stat b { display: block; font-size: 19px; font-weight: 800; color: var(--text); font-variant-numeric: tabular-nums; line-height: 1.2; }
.gen-stat small { font-size: 11px; color: var(--text-light); }
.gen-stat.bad b { color: #e11d48; }

/* -- 候选课程行卡 -- */
.cand-list { display: flex; flex-direction: column; gap: 8px; margin-bottom: 14px; }
.cand-row {
  padding: 11px 14px; border-radius: 12px;
  border: 1px solid var(--border); background: var(--card-bg);
  transition: border-color .2s, box-shadow .2s, background .2s;
}
.cand-row:hover { border-color: rgba(43,177,135,.45); box-shadow: 0 4px 14px -6px rgba(43,177,135,.25); }
.cand-row.checked { border-color: rgba(43,177,135,.5); background: rgba(43,177,135,.05); }
.cand-row.locked { border-left: 3px solid #f59e0b; }
.cand-row.conflicted { border-left: 3px solid rgba(244,63,94,.55); }
.cr-top { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.cand-label { display: inline-flex; align-items: center; gap: 8px; cursor: pointer; min-width: 0; }
.cand-check { width: 15px; height: 15px; accent-color: var(--primary); cursor: pointer; flex-shrink: 0; }
.cand-name { font-size: 13.5px; font-weight: 700; color: var(--text); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 220px; }
.cand-cat { font-size: 11px; font-weight: 600; color: var(--text-light); background: color-mix(in srgb, var(--text-light) 10%, transparent); padding: 2px 8px; border-radius: 999px; flex-shrink: 0; }
.cr-rec { font-size: 11px; font-weight: 700; padding: 2px 9px; border-radius: 999px; flex-shrink: 0; }
.cr-rec.rec-green { color: #059669; background: rgba(16,185,129,.1); }
.cr-rec.rec-blue { color: #0284c7; background: rgba(2,132,199,.1); }
.cr-rec.rec-amber { color: #d97706; background: rgba(245,158,11,.1); }
.cr-rec.rec-red { color: #e11d48; background: rgba(244,63,94,.1); }
html.dark .cr-rec.rec-green { color: #34d399; }
html.dark .cr-rec.rec-blue { color: #38bdf8; }
html.dark .cr-rec.rec-amber { color: #fbbf24; }
html.dark .cr-rec.rec-red { color: #fb7185; }
.cand-loc, .cand-lock {
  margin-left: auto; border: none; background: transparent; cursor: pointer;
  font-size: 13px; padding: 3px 6px; border-radius: 7px; color: var(--text-light);
  transition: background .15s, color .15s; flex-shrink: 0;
}
.cand-loc { margin-left: 8px; }
.cand-loc:hover:not(:disabled) { background: rgba(43,177,135,.1); color: var(--primary); }
.cand-loc:disabled { opacity: .35; cursor: not-allowed; }
.cand-lock:hover { background: rgba(245,158,11,.1); }
.cand-lock.locked { color: #f59e0b; }
.cr-meta { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; margin-top: 7px; }
.cr-meta span {
  font-size: 11.5px; color: var(--text-light);
  background: color-mix(in srgb, var(--text-light) 8%, transparent);
  padding: 2px 9px; border-radius: 999px; white-space: nowrap;
}
.cr-reason { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-top: 7px; }
.cr-why { font-size: 11.5px; color: var(--text-lighter); }
.cr-conflict { font-size: 11.5px; font-weight: 600; color: #e11d48; }
html.dark .cr-conflict { color: #fb7185; }
.cand-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 4px; }
.cand-empty { text-align: center; padding: 26px; color: var(--text-light); font-size: 13px;
  border: 1.5px dashed var(--border); border-radius: 12px; }

/* -- 概览统计条 -- */
.ov-strip { display: flex; align-items: stretch; gap: 10px; flex-wrap: wrap; margin-bottom: 14px; }
.ov-item {
  flex: 1; min-width: 150px; padding: 10px 14px; border-radius: 12px;
  background: color-mix(in srgb, var(--card-bg) 65%, transparent); border: 1px solid var(--border);
  display: flex; flex-direction: column; gap: 3px;
}
.ov-item small { font-size: 11px; color: var(--text-light); }
.ov-item b { font-size: 13.5px; font-weight: 800; color: var(--text); }
.ov-item b.ok { color: #059669; }
.ov-item b.bad { color: #e11d48; }
html.dark .ov-item b.ok { color: #34d399; }
html.dark .ov-item b.bad { color: #fb7185; }
.ov-wide { flex: 2.2; min-width: 220px; }
.ov-wide span { font-size: 12px; color: var(--text-light); line-height: 1.5; }

/* -- 方案面板 -- */
.rec-panel {
  padding: 16px 18px; border-radius: 14px;
  border: 1px solid rgba(43,177,135,.3);
  background: linear-gradient(160deg, rgba(43,177,135,.05), rgba(56,189,248,.03)), var(--card-bg);
}
.rec-head { font-size: 13.5px; font-weight: 700; color: var(--text); margin-bottom: 14px; }
.rec-rejected-tip { font-size: 12px; font-weight: 600; color: #d97706; }
html.dark .rec-rejected-tip { color: #fbbf24; }

/* -- 方案对比卡片 -- */
.plan-cards { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
@media (max-width: 900px) { .plan-cards { grid-template-columns: 1fr; } }
.plan-card {
  padding: 14px 16px; border-radius: 13px; cursor: pointer;
  border: 1.5px solid var(--border); background: var(--card-bg);
  transition: all .22s cubic-bezier(.2,.7,.3,1);
}
.plan-card:hover { transform: translateY(-2px); border-color: rgba(43,177,135,.45); box-shadow: 0 8px 20px -8px rgba(43,177,135,.3); }
.plan-card.active {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(43,177,135,.14), 0 8px 20px -8px rgba(43,177,135,.3);
  background: linear-gradient(160deg, rgba(43,177,135,.06), transparent), var(--card-bg);
}
.plan-card.conflict { border-color: rgba(245,158,11,.4); }
.plan-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }
.plan-head b { font-size: 13.5px; font-weight: 800; color: var(--text); }
.plan-tag { font-size: 11px; font-weight: 700; padding: 2px 10px; border-radius: 999px; }
.plan-tag.credit { color: #059669; background: rgba(16,185,129,.1); }
html.dark .plan-tag.credit { color: #34d399; }
.plan-tag.easy { color: #0284c7; background: rgba(2,132,199,.1); }
html.dark .plan-tag.easy { color: #38bdf8; }
.plan-tag.mix { color: #7c3aed; background: rgba(124,58,237,.1); }
html.dark .plan-tag.mix { color: #a78bfa; }
.plan-tag.bad { color: #e11d48; background: rgba(244,63,94,.1); }
.plan-stats { display: flex; align-items: baseline; gap: 12px; margin-bottom: 10px; font-size: 12px; color: var(--text-light); }
.plan-stats b { font-size: 16px; font-weight: 800; color: var(--text); font-variant-numeric: tabular-nums; }
.plan-chips { display: flex; flex-wrap: wrap; gap: 5px; }
.plan-chip {
  font-size: 11.5px; font-weight: 600; color: var(--text);
  background: color-mix(in srgb, var(--primary) 10%, transparent);
  border: 1px solid color-mix(in srgb, var(--primary) 22%, transparent);
  padding: 3px 10px; border-radius: 999px;
}

/* -- 方案明细表 -- */
.rec-panel .data-table { margin-top: 12px; }
</style>