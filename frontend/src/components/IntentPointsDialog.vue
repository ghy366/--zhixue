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

      <template v-if="round >= 3">
        <div class="ipd-tip ipd-fcfc">
          本轮回合为 <b>先到先得补选</b>：仅剩前两轮剩余的空位，按报名顺序录取，<b>无需投入积分</b>。点「确认加入」即报名，名额满即止。
        </div>
      </template>

      <template v-else>
        <div class="ipd-stat">
          <span>本轮剩余 <b>{{ remaining }}</b> 分</span>
          <span>可投入区间 <b>0 – {{ maxPoints }}</b> 分</span>
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
          >{{ q }} 分</button>
        </div>

        <div class="ipd-tip">
          <template v-if="isEnroll">
            点「确认报名」即正式参与本轮选课，投入的积分当场扣入本课程。<br>
          </template>
          投入积分越多，本轮结算时录取优先级越高；<b>0 分</b>表示暂不投入积分。<br>
          窗口期内任意时间提交都一样，<b>不用卡点抢</b>；截止后系统按积分从高到低录取，同分随机抽签。
        </div>

        <div v-if="points > remaining" class="ipd-warn">
          超过本轮剩余积分 {{ remaining }} 分，请调整后再提交。
        </div>
      </template>
    </div>

    <template #footer>
      <div class="ipd-foot">
        <el-button v-if="!isEnroll && isEdit" text type="danger" @click="onRemove">移出意向</el-button>
        <span class="ipd-spacer" />
        <el-button @click="visible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="submitting"
          :disabled="round < 3 && points > remaining"
          @click="onSubmit"
        >{{ primaryText }}</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, watch, ref } from 'vue'

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
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'submit', points: number): void
  (e: 'remove'): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v)
})

const isEdit = computed(() => props.currentPoints !== null && props.currentPoints !== undefined)
const isEnroll = computed(() => props.mode === 'enroll')
const dialogTitle = computed(() =>
  isEnroll.value ? '投入积分报名' : (isEdit.value ? '调整投入积分' : '加入意向')
)
const primaryText = computed(() => {
  if (isEnroll.value) return '确认报名'
  return isEdit.value ? '保存' : '确认加入'
})
const quickList = [0, 5, 10, 20, 30, 50]
const points = ref(10)
const maxPts = computed(() => props.maxPoints ?? 50)

// 每次打开/切换课程时，回填当前积分（新意向默认 10 分）
watch(
  () => [props.modelValue, props.course, props.currentPoints] as const,
  () => {
    if (!props.modelValue) return
    // 第三轮先到先得，无需积分：默认 0
    if (props.round >= 3) { points.value = 0; return }
    const cur = props.currentPoints
    points.value = cur !== null && cur !== undefined ? cur : 10
  },
  { immediate: true }
)

function onSubmit() { emit('submit', points.value) }
function onRemove() { emit('remove') }
</script>

<style scoped>
.ipd-body { display: flex; flex-direction: column; gap: 14px; }
.ipd-course { display: flex; align-items: center; gap: 10px; }
.ipd-name { font-size: 15px; font-weight: 600; color: var(--text); }
.ipd-stat {
  display: flex; justify-content: space-between; font-size: 13px; color: var(--text-light);
  background: var(--card-bg, rgba(148,163,184,.08)); border: 1px solid var(--border);
  border-radius: 8px; padding: 8px 12px;
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
.ipd-fcfc { background: rgba(245,158,11,.08); border: 1px dashed rgba(245,158,11,.4); border-radius: 8px; padding: 10px 12px; color: var(--text-light); }
.ipd-fcfc b { color: #d97706; }
.ipd-warn { font-size: 12px; color: #ef4444; }
.ipd-foot { display: flex; align-items: center; gap: 8px; }
.ipd-spacer { flex: 1; }
</style>
