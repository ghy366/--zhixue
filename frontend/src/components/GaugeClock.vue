<template>
  <div class="auth-gauge">
    <!-- 原版 72 根刻度线 SVG（全部硬编码 stroke="#4ade80" / "rgba(71,85,105,0.55)"） -->
    <svg class="gauge-svg" viewBox="0 0 220 220" aria-hidden="true">
      <circle cx="110" cy="110" r="95" fill="none" stroke="rgba(71,85,105,0.25)" stroke-width="0.8"/>
      <!-- 36根绿色刻度（每10°） -->
      <line x1="110.0" y1="10.0" x2="110.0" y2="21.0" class="g-tick" />
      <line x1="118.7" y1="10.4" x2="117.8" y2="21.3" class="g-tick" />
      <line x1="127.4" y1="11.5" x2="125.5" y2="22.4" class="g-tick" />
      <line x1="135.9" y1="13.4" x2="133.0" y2="24.0" class="g-tick" />
      <line x1="144.2" y1="16.0" x2="140.4" y2="26.4" class="g-tick" />
      <line x1="152.3" y1="19.4" x2="147.6" y2="29.3" class="g-tick" />
      <line x1="160.0" y1="23.4" x2="154.5" y2="32.9" class="g-tick" />
      <line x1="167.4" y1="28.1" x2="161.0" y2="37.1" class="g-tick" />
      <line x1="174.3" y1="33.4" x2="167.2" y2="41.8" class="g-tick" />
      <line x1="180.7" y1="39.3" x2="172.9" y2="47.1" class="g-tick" />
      <line x1="186.6" y1="45.7" x2="178.2" y2="52.8" class="g-tick" />
      <line x1="191.9" y1="52.6" x2="182.9" y2="59.0" class="g-tick" />
      <line x1="196.6" y1="60.0" x2="187.1" y2="65.5" class="g-tick" />
      <line x1="200.6" y1="67.7" x2="190.7" y2="72.4" class="g-tick" />
      <line x1="204.0" y1="75.8" x2="193.6" y2="79.6" class="g-tick" />
      <line x1="206.6" y1="84.1" x2="196.0" y2="87.0" class="g-tick" />
      <line x1="208.5" y1="92.6" x2="197.6" y2="94.5" class="g-tick" />
      <line x1="209.6" y1="101.3" x2="198.7" y2="102.2" class="g-tick" />
      <line x1="210.0" y1="110.0" x2="199.0" y2="110.0" class="g-tick" />
      <line x1="209.6" y1="118.7" x2="198.7" y2="117.8" class="g-tick" />
      <line x1="208.5" y1="127.4" x2="197.6" y2="125.5" class="g-tick" />
      <line x1="206.6" y1="135.9" x2="196.0" y2="133.0" class="g-tick" />
      <line x1="204.0" y1="144.2" x2="193.6" y2="140.4" class="g-tick" />
      <line x1="200.6" y1="152.3" x2="190.7" y2="147.6" class="g-tick" />
      <line x1="196.6" y1="160.0" x2="187.1" y2="154.5" class="g-tick" />
      <line x1="191.9" y1="167.4" x2="182.9" y2="161.0" class="g-tick" />
      <line x1="186.6" y1="174.3" x2="178.2" y2="167.2" class="g-tick" />
      <line x1="180.7" y1="180.7" x2="172.9" y2="172.9" class="g-tick" />
      <line x1="174.3" y1="186.6" x2="167.2" y2="178.2" class="g-tick" />
      <line x1="167.4" y1="191.9" x2="161.0" y2="182.9" class="g-tick" />
      <line x1="160.0" y1="196.6" x2="154.5" y2="187.1" class="g-tick" />
      <line x1="152.3" y1="200.6" x2="147.6" y2="190.7" class="g-tick" />
      <line x1="144.2" y1="204.0" x2="140.4" y2="193.6" class="g-tick" />
      <line x1="135.9" y1="206.6" x2="133.0" y2="196.0" class="g-tick" />
      <line x1="127.4" y1="208.5" x2="125.5" y2="197.6" class="g-tick" />
      <line x1="118.7" y1="209.6" x2="117.8" y2="198.7" class="g-tick" />
    </svg>
    <div class="gauge-center">
      <div class="gauge-time">{{ time }}</div>
      <div class="gauge-date">{{ date }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'

const time = ref('--:--:--')
const date = ref('----.--.-- - ---')
let timer: number | null = null

function pad(n: number) { return String(n).padStart(2, '0') }

function tick() {
  const WEEK = ['SUN','MON','TUE','WED','THU','FRI','SAT']
  const now = new Date()
  time.value = `${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
  date.value = `${now.getFullYear()}.${pad(now.getMonth()+1)}.${pad(now.getDate())} - ${WEEK[now.getDay()]}`
  // 更新刻度：按当天秒数比例染红/灰
  const totalSec = now.getHours()*3600 + now.getMinutes()*60 + now.getSeconds()
  const greenCount = Math.min(36, Math.round(36 * totalSec / 86400))
  const svg = document.querySelector('.gauge-svg')
  if (svg) {
    const ticks = svg.querySelectorAll('.g-tick') as NodeListOf<SVGLineElement>
    ticks.forEach((t, i) => {
      t.setAttribute('stroke', i < greenCount ? '#4ade80' : 'rgba(71,85,105,0.55)')
      t.setAttribute('stroke-width', '1.4')
      t.setAttribute('stroke-linecap', 'round')
    })
  }
}

onMounted(() => { tick(); timer = setInterval(tick, 1000) as any })
onBeforeUnmount(() => { if (timer) clearInterval(timer) })
</script>

<style scoped>
.auth-gauge {
  position: relative; z-index: 1;
  width: 260px; height: 260px;
  flex-shrink: 0;
}
.gauge-svg { width: 100%; height: 100%; overflow: visible; }
.gauge-center {
  position: absolute; top: 50%; left: 50%;
  transform: translate(-50%, -50%);
  text-align: center; pointer-events: none;
}
.gauge-time {
  font-family: "JetBrains Mono", "Consolas", "SF Mono", Menlo, monospace;
  font-size: 28px; font-weight: 600; letter-spacing: 2px;
  color: #e2e8f0; line-height: 1.1; font-variant-numeric: tabular-nums;
}
.gauge-date {
  margin-top: 8px; font-size: 11px; letter-spacing: 1.5px;
  color: rgba(148,163,184,0.85); text-transform: uppercase;
  font-family: "JetBrains Mono", "Consolas", monospace;
}
.g-tick { stroke: #4ade80; stroke-width: 1.4; stroke-linecap: round; }
</style>
