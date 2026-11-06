<template>
  <div class="dc" role="timer" :aria-label="ariaText">
    <div class="dc-time">
      <template v-for="(part, pi) in parts" :key="pi">
        <!-- 分隔符：青白渐变冒号，与数字同辉光 -->
        <span v-if="part.sep" class="dc-colon" aria-hidden="true">:</span>
        <span v-else class="dc-grp" :class="{ 'is-sec': part.sec }">
          <span v-for="(ch, ci) in part.v" :key="ci" class="dc-cell">
            <Transition name="dc-roll">
              <b :key="ch">{{ ch }}</b>
            </Transition>
          </span>
        </span>
      </template>
    </div>

    <!-- 时间下方一条细长光带：中间一个亮点，光点循环掠过 -->
    <span class="dc-beam" aria-hidden="true"><i></i></span>

    <div class="dc-date">
      <svg class="dc-ic" viewBox="0 0 24 24" aria-hidden="true">
        <rect x="3" y="5" width="18" height="16" rx="2.5" />
        <path d="M3 10h18M8 3v4M16 3v4" />
      </svg>
      <span>{{ dateText }}</span>
    </div>

    <!-- 读屏用完整时间（可见区是逐位渲染，语义不连续） -->
    <span class="dc-sr" aria-live="off">{{ timeText }}</span>
  </div>
</template>

<script setup lang="ts">
/**
 * 科技感数字时钟
 * - 真实系统时间，对齐到整秒触发，长期运行不漂移
 * - 每一位单独成格、定宽 + 等宽数字，数字变化时整行不抖动
 * - 时分浅青白、秒高亮青绿；逐位纵向滚动切换
 */
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'

const WEEK = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY']
const pad = (n: number) => (n < 10 ? '0' + n : '' + n)

const hh = ref('--')
const mm = ref('--')
const ss = ref('--')
const dateText = ref('----.--.-- · ---')

const parts = computed(() => [
  { sep: false, v: hh.value, sec: false },
  { sep: true },
  { sep: false, v: mm.value, sec: false },
  { sep: true },
  { sep: false, v: ss.value, sec: true },
])
const timeText = computed(() => `${hh.value}:${mm.value}:${ss.value}`)
const ariaText = computed(() => `${timeText.value} ${dateText.value}`)

function tick() {
  const d = new Date()
  hh.value = pad(d.getHours())
  mm.value = pad(d.getMinutes())
  ss.value = pad(d.getSeconds())
  dateText.value = `${d.getFullYear()}.${pad(d.getMonth() + 1)}.${pad(d.getDate())} · ${WEEK[d.getDay()]}`
}

/* 每次都在「整秒刚过」时安排下一次，避免 setInterval 累积漂移 */
let timer: number | null = null
let stopped = false
function schedule() {
  if (stopped) return
  timer = window.setTimeout(() => { tick(); schedule() }, 1000 - (Date.now() % 1000))
}
/* 从后台切回来立刻补一次，再重新对齐 */
function onVis() {
  if (!document.hidden) { tick(); if (timer) clearTimeout(timer); schedule() }
}

onMounted(() => {
  tick()
  schedule()
  document.addEventListener('visibilitychange', onVis)
})
onBeforeUnmount(() => {
  stopped = true
  if (timer) clearTimeout(timer)
  document.removeEventListener('visibilitychange', onVis)
})
</script>

<style scoped>
.dc {
  display: flex;
  flex-direction: column;
  align-items: center;
  line-height: 1;
  user-select: none;
}
.dc-time {
  display: flex;
  align-items: center;
  justify-content: center;
  /* 等宽数字 + DIN 风字体：Windows 自带 Bahnschrift，其它平台回落等宽 */
  font-family: "Bahnschrift", "DIN Alternate", "SF Pro Display", "Segoe UI Variable Display",
               ui-monospace, "SF Mono", "JetBrains Mono", Consolas, monospace;
  font-variant-numeric: tabular-nums;
  font-feature-settings: "tnum" 1;
  font-size: var(--dc-font, 46px);
  font-weight: 700;
  letter-spacing: 0;
  line-height: 1;
}
/* 每一位定宽：数字切换时整串不位移 */
.dc-cell {
  position: relative;
  display: inline-block;
  width: .62em;
  height: 1em;
  vertical-align: top;
}
.dc-cell b {
  position: absolute;
  inset: 0;
  display: block;
  text-align: center;
  font-weight: 700;
  will-change: transform, opacity;
}
/* 时分：冰蓝白渐变（上白下浅青），秒：青绿渐变 —— 用渐变文字 + 投影做出辉光 */
.dc-grp {
  display: inline-flex;
  background-image: linear-gradient(180deg, #ffffff 0%, #f4fffe 38%, #bfeeec 66%, #86dcd8 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  color: transparent;
  filter:
    drop-shadow(0 0 .03em rgba(210, 255, 252, .55))
    drop-shadow(0 0 .13em rgba(70, 226, 216, .40));
}
.dc-grp.is-sec {
  background-image: linear-gradient(180deg, #f2fffd 0%, #b8fdf3 30%, #52ecda 66%, #14c2b5 100%);
  filter:
    drop-shadow(0 0 .03em rgba(200, 255, 248, .6))
    drop-shadow(0 0 .13em rgba(36, 224, 208, .55))
    drop-shadow(0 0 .30em rgba(24, 190, 190, .30));
}
/* 分隔符：冒号，与数字同色系渐变 + 辉光 */
.dc-colon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: .34em;
  height: 1em;
  font-size: 1em;
  line-height: 1;
  background-image: linear-gradient(180deg, #ffffff 0%, #f4fffe 38%, #bfeeec 66%, #86dcd8 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  color: transparent;
  filter:
    drop-shadow(0 0 .03em rgba(210, 255, 252, .55))
    drop-shadow(0 0 .13em rgba(70, 226, 216, .40));
}

/* 细长动态光线：两端渐隐 + 中心亮点 + 循环掠过的光点 */
.dc-beam {
  position: relative;
  display: block;
  width: min(88%, 460px);
  height: 1px;
  margin-top: .5em;
  background: linear-gradient(90deg,
    rgba(24, 214, 192, 0),
    rgba(120, 240, 228, .34) 14%,
    rgba(190, 255, 249, .78) 42%,
    rgba(230, 255, 253, .95) 50%,
    rgba(190, 255, 249, .78) 58%,
    rgba(120, 240, 228, .34) 86%,
    rgba(24, 214, 192, 0));
  filter: drop-shadow(0 0 6px rgba(48, 226, 214, .55));
  overflow: hidden;
}
.dc-beam::after {
  content: '';
  position: absolute;
  left: 50%; top: 50%;
  width: 5px; height: 5px;
  margin: -2.5px 0 0 -2.5px;
  transform: rotate(45deg);
  background: #dcfffb;
  box-shadow: 0 0 10px rgba(150, 255, 246, .95), 0 0 20px rgba(48, 226, 214, .7);
}
.dc-beam i {
  position: absolute;
  top: 0; left: 0;
  width: 16%;
  height: 1px;
  background: linear-gradient(90deg, rgba(255, 255, 255, 0), #ffffff, rgba(255, 255, 255, 0));
  animation: dcBeam 4.6s cubic-bezier(.5, 0, .5, 1) infinite;
}
@keyframes dcBeam {
  0%   { transform: translateX(-110%); opacity: 0; }
  12%  { opacity: 1; }
  82%  { opacity: 1; }
  100% { transform: translateX(640%); opacity: 0; }
}

/* 逐位滚动：旧数字上移淡出，新数字自下浮入 */
/* 去掉上下位移：秒切换时旧数字上移 + 新数字下浮会在原位叠加出重影，
   改为同位置快速交叉淡化（仅 opacity），切换干脆不跳 */
.dc-roll-enter-active,
.dc-roll-leave-active {
  transition: opacity .12s linear;
}
.dc-roll-enter-from { opacity: 0; }
.dc-roll-leave-to   { opacity: 0; }

.dc-date {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: .95em;
  font-family: "Bahnschrift", "DIN Alternate", "Segoe UI", system-ui, sans-serif;
  font-size: max(12px, calc(var(--dc-font, 46px) * .26));
  font-weight: 600;
  letter-spacing: 2.8px;
  color: #cfe6ef;
  font-variant-numeric: tabular-nums;
  text-shadow: 0 0 16px rgba(24, 214, 192, .35);
}
.dc-ic {
  width: 1.06em;
  height: 1.06em;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.7;
  stroke-linecap: round;
  stroke-linejoin: round;
  opacity: .8;
}
.dc-sr {
  position: absolute;
  width: 1px; height: 1px;
  margin: -1px; padding: 0; border: 0;
  clip: rect(0 0 0 0);
  overflow: hidden;
  white-space: nowrap;
}

@media (prefers-reduced-motion: reduce) {
  .dc-roll-enter-active, .dc-roll-leave-active { transition: none; }
  .dc-beam i { animation: none; opacity: 0; }
}
</style>
