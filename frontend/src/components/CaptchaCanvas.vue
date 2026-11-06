<template>
  <canvas ref="cvRef" width="112" height="44" title="点击刷新验证码" @click="draw"></canvas>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'

const cvRef = ref<HTMLCanvasElement | null>(null)
let code = ''

function draw() {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
  code = ''
  for (let i = 0; i < 4; i++) code += chars.charAt(Math.floor(Math.random() * chars.length))
  const cv = cvRef.value
  if (!cv) return
  const ctx = cv.getContext('2d')!
  ctx.clearRect(0, 0, cv.width, cv.height)
  ctx.fillStyle = 'rgba(15,23,42,0.85)'
  ctx.fillRect(0, 0, cv.width, cv.height)
  const palette = ['#4ade80', '#22d3ee', '#7dd3fc', '#fbbf24']
  for (let i = 0; i < 4; i++) {
    ctx.strokeStyle = `rgba(74,222,128,${0.12 + Math.random() * 0.18})`
    ctx.beginPath()
    ctx.moveTo(Math.random() * cv.width, Math.random() * cv.height)
    ctx.lineTo(Math.random() * cv.width, Math.random() * cv.height)
    ctx.stroke()
  }
  for (let i = 0; i < code.length; i++) {
    ctx.save()
    ctx.font = '700 22px Georgia, "Times New Roman", serif'
    ctx.fillStyle = palette[i % palette.length]
    ctx.shadowColor = 'rgba(0,0,0,0.35)'
    ctx.shadowBlur = 2
    ctx.translate(18 + i * 24, 28)
    ctx.rotate((Math.random() - 0.5) * 0.5)
    ctx.fillText(code[i], -8, 6)
    ctx.restore()
  }
  for (let i = 0; i < 24; i++) {
    ctx.fillStyle = 'rgba(148,163,184,0.5)'
    ctx.fillRect(Math.random() * cv.width, Math.random() * cv.height, 1.6, 1.6)
  }
}

function getCode() { return code }
defineExpose({ getCode, refresh: draw })

onMounted(() => draw())
</script>

<style scoped>
canvas {
  width: 112px; height: 44px; flex-shrink: 0;
  border-radius: 10px; cursor: pointer;
}
</style>
