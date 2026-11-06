<template>
  <el-dialog
    v-model="visible"
    :title="dialogTitle"
    width="460px"
    class="ipd-dialog"
    append-to-body
  >
    <div v-if="course" class="ipd-body">
      <div class="ipd-course">
        <span class="ipd-name">{{ course.name || course.courseName }}</span>
        <span class="course-cat" :class="'cat-' + course.category">{{ course.category }}</span>
      </div>

      <!-- 换课保底（Swap）：有在修选修课时报名/候补均可挂保底，选不上自动保留原课 -->
      <div v-if="isEnroll && swapCandidates.length" class="ipd-swap">
        <label class="ipd-swap-head">
          <el-checkbox v-model="swapOn" />
          <span class="ipd-swap-title">{{ t('🔄 换课保底') }}</span>
          <span class="ipd-swap-sub">{{ t('选不上新课？原课自动保留') }}</span>
        </label>
        <div v-if="swapOn" class="ipd-swap-body">
          <el-select
            v-model="swapFrom"
            :placeholder="t('选择要换掉的已选课程')"
            size="small"
            class="ipd-swap-select"
          >
            <el-option
              v-for="s in swapCandidates"
              :key="s.courseId"
              :value="s.courseId"
              :label="`《${s.courseName}》· ${t('已投')} ${s.points ?? 0} ${t('分')}`"
            />
          </el-select>
          <div class="ipd-swap-tip" v-if="round >= 3">
            {{ t('确认后立即退掉《A》并录取《B》（需 B 有空位）；任一校验不过则整单不动，原课绝不会先丢。') }}
          </div>
          <div class="ipd-swap-tip" v-else>
            {{ t('本轮结算时若《B》录取，自动退掉《A》完成互换；若未录取，《A》原样保留、投分全额退还。投分上限 = 剩余积分 + 《A》已投积分。') }}
          </div>
        </div>
      </div>

      <template v-if="round >= 3">
        <div class="ipd-tip ipd-fcfc">
          {{ t('本轮回合为') }} <b>{{ t('先到先得补选') }}</b>{{ t('：仅剩前两轮剩余的空位，按报名顺序录取，') }}<b>{{ t('无需投入积分') }}</b>{{ t('。点「{btn}」即报名，名额满即止。', { btn: primaryText }) }}
        </div>
      </template>

      <template v-else>
        <div class="ipd-stat">
          <span v-html="t('本轮剩余 <b>{n}</b> 分', { n: remaining })"></span>
          <span v-html="t('可投入区间 <b>0 – {n}</b> 分', { n: swapOn ? swapMax : maxPoints })"></span>
        </div>

        <div class="ipd-slider">
          <el-slider
            v-model="points"
            :min="0"
            :max="maxPts"
            :step="1"
            show-input
            :show-input-controls="false"
          />
        </div>

        <div class="ipd-quick">
          <button
            v-for="q in quickList"
            :key="q"
            type="button"
            class="ipd-chip"
            :class="{ active: points === q }"
            @click="points = q"
          >{{ t('{n} 分', { n: q }) }}</button>
        </div>

        <div class="ipd-tip">
          <template v-if="isEnroll">
            {{ t('点「{btn}」即正式参与本轮选课，投入的积分当场扣入本课程。', { btn: t('确认报名') }) }}<br>
          </template>
          {{ t('投入积分越多，本轮结算时录取优先级越高；') }}<b>{{ t('0 分') }}</b>{{ t('表示暂不投入积分。') }}<br>
          {{ t('窗口期内任意时间提交都一样，') }}<b>{{ t('不用卡点抢') }}</b>{{ t('；截止后系统按积分从高到低录取，同分随机抽签。') }}
        </div>

        <div v-if="overLimit" class="ipd-warn">
          {{ swapOn
            ? t('超过换课投分上限 {n} 分（剩余 {r} + 保底课已投 {a}），请调整后再提交。', { n: swapMax, r: remaining, a: aPoints })
            : t('超过本轮剩余积分 {n} 分，请调整后再提交。', { n: remaining }) }}
        </div>

        <!-- 选课策略模拟器：按当前报名快照估算录取概率 -->
        <div v-if="isEnroll && sim" class="ipd-sim">
          <div class="ipd-sim-head">
            {{ t('🎯 录取模拟') }}
            <span>{{ t('按当前报名快照估算 · 同分随机抽签') }}</span>
          </div>
          <div class="ipd-sim-main">
            <div class="ipd-sim-chance" :class="sim.chance >= 60 ? 'ok' : sim.chance >= 30 ? 'mid' : 'bad'">
              <b>{{ sim.chance }}%</b><span>{{ t('预计录取概率') }}</span>
            </div>
            <div class="ipd-sim-facts">
              <div>{{ t('你的积分超过当前') }} <b>{{ sim.beatPct }}%</b> {{ t('的报名者') }}</div>
              <div>{{ t('排位') }} <b>{{ t('第 {a} / {b} 位', { a: sim.myRank, b: sim.pending + 1 }) }}</b> · {{ t('剩余名额') }} <b>{{ sim.seatsLeft }}</b></div>
            </div>
          </div>
          <div class="ipd-sim-bar"><i :style="{ width: sim.chance + '%' }" :class="sim.chance >= 60 ? 'ok' : sim.chance >= 30 ? 'mid' : 'bad'" /></div>
          <div class="ipd-sim-note">{{ t('拖动上方积分滑块实时重新模拟；后续报名者可能改变结果，以截止时数据为准') }}</div>
        </div>
      </template>
    </div>

    <template #footer>
      <div class="ipd-foot">
        <el-button v-if="!isEnroll && isEdit" text type="danger" @click="onRemove">{{ t('移出意向') }}</el-button>
        <span class="ipd-spacer" />
        <el-button @click="visible = false">{{ t('取消') }}</el-button>
        <el-button
          type="primary"
          :loading="submitting"
          :disabled="overLimit"
          @click="onSubmit"
        >{{ primaryText }}</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, watch, ref } from 'vue'
import http from '@/api'
import { t } from '@/i18n'

const props = defineProps<{
  modelValue: boolean
  course: any
  currentPoints?: number | null
  remaining?: number
  submitting?: boolean
  round?: number
  maxPoints?: number
  /** enroll = 选课开放中，提交即正式报名；intent = 选课未开始，只收藏意向 */
  mode?: 'enroll' | 'intent'
  /** 换课保底候选：本人当前进行中的选修报名（pending/approved） */
  swapCandidates?: any[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'submit', points: number, swapFromCourseId?: number | null): void
  (e: 'remove'): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v)
})

const isEdit = computed(() => props.currentPoints !== null && props.currentPoints !== undefined)
const isEnroll = computed(() => props.mode === 'enroll')

// ===== 换课保底（Swap） =====
const swapCandidates = computed(() => props.swapCandidates || [])
const swapOn = ref(false)
const swapFrom = ref<number | null>(null)
const aPoints = computed(() => {
  const a = swapCandidates.value.find(s => s.courseId === swapFrom.value)
  return a ? (a.points ?? 0) : 0
})
/** 换课投分上限 = 剩余积分 + 保底课已投积分（换课成功即释放原课积分） */
const swapMax = computed(() => (props.remaining ?? 0) + aPoints.value)

const dialogTitle = computed(() =>
  isEnroll.value ? (swapOn.value ? t('换课保底报名') : t('投入积分报名')) : (isEdit.value ? t('调整投入积分') : t('加入意向'))
)
const primaryText = computed(() => {
  if (isEnroll.value) return swapOn.value ? t('确认换课') : t('确认报名')
  return isEdit.value ? t('保存') : t('确认加入')
})
const quickList = [0, 5, 10, 20, 30, 50]
const points = ref(10)
const maxPts = computed(() => (swapOn.value ? Math.min(props.maxPoints ?? 50, swapMax.value) : (props.maxPoints ?? 50)))
const overLimit = computed(() => (props.round ?? 1) < 3 && points.value > (swapOn.value ? swapMax.value : (props.remaining ?? 0)))

// 每次打开/切换课程时，回填当前积分（新意向默认 10 分）并重置换课选择
watch(
  () => [props.modelValue, props.course, props.currentPoints] as const,
  () => {
    if (!props.modelValue) return
    swapOn.value = false
    swapFrom.value = swapCandidates.value.length ? swapCandidates.value[0].courseId : null
    // 第三轮先到先得，无需积分：默认 0
    if (props.round >= 3) { points.value = 0; return }
    const cur = props.currentPoints
    points.value = cur !== null && cur !== undefined ? cur : 10
  },
  { immediate: true }
)

// 切换保底课/开关时，把投分夹回允许区间
watch([swapOn, swapFrom], () => {
  if (points.value > maxPts.value) points.value = Math.max(0, maxPts.value)
})

function onSubmit() { emit('submit', points.value, isEnroll.value && swapOn.value ? swapFrom.value : null) }
function onRemove() { emit('remove') }

// ===== 选课策略模拟（enroll 模式下，随积分滑块实时模拟）=====
const sim = ref<any>(null)
const courseId = computed(() => props.course?.id ?? props.course?.courseId ?? null)
let simTimer: any = null
watch([points, courseId, isEnroll], () => {
  if (!isEnroll.value || !courseId.value || (props.round ?? 1) >= 3) { sim.value = null; return }
  clearTimeout(simTimer)
  simTimer = setTimeout(async () => {
    try {
      const r: any = await http.post('/enrollments/simulate', { courseId: courseId.value, points: points.value })
      sim.value = r?.data || null
    } catch { sim.value = null }
  }, 220)
}, { immediate: true })
</script>

<style scoped>
.ipd-body { display: flex; flex-direction: column; gap: 14px; }
.ipd-course { display: flex; align-items: center; gap: 10px; }
.ipd-name { font-size: 15px; font-weight: 600; color: var(--text); }
/* 信息框统一加强对比：浅色主题下淡边框几乎不可见，改用带色底 + 明确描边 */
.ipd-stat {
  display: flex; justify-content: space-between; font-size: 13px; color: var(--text-light);
  background: rgba(43,177,135,.10); border: 1px solid rgba(43,177,135,.42);
  border-radius: 8px; padding: 9px 12px;
}
.ipd-stat b { color: var(--primary); }
.ipd-slider { padding: 0 4px; }
.ipd-quick { display: flex; gap: 8px; flex-wrap: wrap; }
.ipd-chip {
  border: 1px solid var(--border); background: transparent; color: var(--text-light);
  border-radius: 999px; padding: 4px 12px; font-size: 12px; cursor: pointer;
  transition: all .18s;
}
.ipd-chip:hover { border-color: var(--primary); color: var(--primary); }
.ipd-chip.active { background: var(--primary); border-color: var(--primary); color: #fff; }
.ipd-tip { font-size: 12px; color: var(--text-lighter); line-height: 1.7; }
.ipd-tip b { color: var(--text-light); }
.ipd-fcfc { background: rgba(245,158,11,.13); border: 1px dashed rgba(245,158,11,.6); border-radius: 8px; padding: 10px 12px; color: var(--text-light); }
.ipd-fcfc b { color: #d97706; }
.ipd-warn { font-size: 12px; color: #ef4444; }

/* ===== 换课保底（Swap） ===== */
.ipd-swap {
  border: 1px solid rgba(56,189,248,.55); border-radius: 10px;
  background: rgba(56,189,248,.13);
  box-shadow: 0 2px 10px -4px rgba(56,189,248,.35);
  padding: 11px 13px;
}
.ipd-swap-head { display: flex; align-items: center; gap: 8px; cursor: pointer; }
.ipd-swap-title { font-size: 13.5px; font-weight: 700; color: #0369a1; }
.ipd-swap-sub { font-size: 12px; color: var(--text-light); }
.ipd-swap-body { margin-top: 9px; display: flex; flex-direction: column; gap: 8px; }
.ipd-swap-select { width: 100%; }
.ipd-swap-tip { font-size: 12px; color: var(--text-light); line-height: 1.6; }
.ipd-swap :deep(.el-checkbox__inner) { border-radius: 4px; }
html.dark .ipd-swap { background: rgba(56,189,248,.12); border-color: rgba(56,189,248,.5); box-shadow: none; }
html.dark .ipd-swap-title { color: #7dd3fc; }
html.dark .ipd-stat { background: rgba(43,177,135,.13); border-color: rgba(43,177,135,.45); }

/* ===== 选课策略模拟器 ===== */
.ipd-sim {
  border: 1px solid rgba(43,177,135,.5); border-radius: 10px;
  background: color-mix(in srgb, var(--primary) 10%, transparent);
  padding: 11px 13px;
}
.ipd-sim-head { font-size: 12.5px; font-weight: 700; color: var(--text); display: flex; align-items: baseline; gap: 8px; }
.ipd-sim-head span { font-weight: 500; font-size: 12px; color: var(--text-light); }
.ipd-sim-main { display: flex; align-items: center; gap: 14px; margin: 9px 0; }
.ipd-sim-chance { text-align: center; min-width: 74px; }
.ipd-sim-chance b { display: block; font-size: 24px; font-weight: 800; line-height: 1.1; font-variant-numeric: tabular-nums; }
.ipd-sim-chance span { font-size: 12px; color: var(--text-light); }
.ipd-sim-chance.ok b { color: #059669; }
.ipd-sim-chance.mid b { color: #d97706; }
.ipd-sim-chance.bad b { color: #e11d48; }
.ipd-sim-facts { flex: 1; display: flex; flex-direction: column; gap: 4px; font-size: 12px; color: var(--text-light); }
.ipd-sim-facts b { color: var(--text); font-variant-numeric: tabular-nums; }
.ipd-sim-bar { height: 7px; border-radius: 99px; background: rgba(148,163,184,.14); overflow: hidden; }
.ipd-sim-bar i { display: block; height: 100%; border-radius: 99px; transition: width .3s ease; }
.ipd-sim-bar i.ok { background: linear-gradient(90deg, #10b981, #34d399); }
.ipd-sim-bar i.mid { background: linear-gradient(90deg, #f59e0b, #fbbf24); }
.ipd-sim-bar i.bad { background: linear-gradient(90deg, #ef4444, #f97316); }
.ipd-sim-note { margin-top: 8px; font-size: 12px; color: var(--text-lighter); line-height: 1.5; }
.ipd-foot { display: flex; align-items: center; gap: 8px; }
.ipd-spacer { flex: 1; }
</style>
