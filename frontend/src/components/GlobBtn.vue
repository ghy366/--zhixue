<template>
  <button
    ref="btnRef"
    type="submit"
    class="auth-login-btn"
    :class="{ 'btn-globing': globing, 'btn-check-ok': checkOk, 'btn-shake': shake }"
    :disabled="disabled"
    @click="$emit('click')"
  >
    <span class="btn-text" :style="{ opacity: globing ? 0 : 1 }">{{ text }}</span>
    <span v-show="globing" class="btn-particles" aria-hidden="true">
      <i></i><i></i><i></i><i></i><i></i><i></i><i></i>
    </span>
    <svg v-show="globing && checkOk" class="btn-check" viewBox="0 0 52 52" aria-hidden="true">
      <path class="btn-check-circle" fill="none" d="M26 0 C11.6 0 0 11.6 0 26 s11.6 26 26 26 26-11.6 26-26 S40.4 0 26 0 z" />
      <path class="btn-check-tick" fill="none" d="M14.1 27.2 l7.1 7.2 15.4-15.4" />
    </svg>
  </button>
</template>

<script setup lang="ts">
import { ref, defineExpose } from 'vue'

const props = withDefaults(defineProps<{ text?: string; disabled?: boolean }>(), {
  text: '登 录',
  disabled: false
})
defineEmits<{ (e: 'click'): void }>()

const btnRef = ref<HTMLButtonElement | null>(null)
const globing = ref(false)
const checkOk = ref(false)
const shake = ref(false)

function startGlobing() {
  globing.value = true
  checkOk.value = false
  shake.value = false
}
function success(delay = 700) {
  checkOk.value = true
  return new Promise<void>(r => setTimeout(r, delay))
}
function fail() {
  shake.value = true
  setTimeout(() => {
    globing.value = false
    checkOk.value = false
    shake.value = false
  }, 500)
}
function reset() {
  globing.value = false
  checkOk.value = false
  shake.value = false
}

defineExpose({ startGlobing, success, fail, reset })
</script>

<style scoped>
.auth-login-btn {
  width: 100%;
  padding: 12px;
  border: none;
  border-radius: 10px;
  height: 44px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 4px;
  color: #fff;
  cursor: pointer;
  background: linear-gradient(135deg, #22c55e, #16a34a);
  box-shadow: 0 6px 18px rgba(34, 197, 94, 0.35);
  transition: width 0.55s cubic-bezier(0.4, 0, 0.2, 1),
              height 0.55s cubic-bezier(0.4, 0, 0.2, 1),
              border-radius 0.55s cubic-bezier(0.4, 0, 0.2, 1),
              padding 0.55s cubic-bezier(0.4, 0, 0.2, 1),
              box-shadow 0.55s ease,
              background 0.2s ease,
              transform 0.15s ease;
  position: relative;
  overflow: hidden;
}
.auth-login-btn:hover:not(:disabled):not(.btn-globing) {
  background: linear-gradient(135deg, #16a34a, #15803d);
  box-shadow: 0 8px 24px rgba(34, 197, 94, 0.42);
  transform: translateY(-1px);
}
.auth-login-btn:disabled { cursor: wait; opacity: 0.9; }

/* 光球形态 */
.auth-login-btn.btn-globing {
  width: 44px; height: 44px; padding: 0;
  border-radius: 50% !important;
  margin: 0 auto;
  display: block;
  max-width: none !important;
  letter-spacing: 0;
  background: radial-gradient(circle at 35% 35%, #4ade80, #16a34a 55%, #15803d);
  box-shadow:
    0 0 0 2px rgba(34, 197, 94, 0.25),
    0 0 28px rgba(34, 197, 94, 0.55),
    0 0 60px rgba(34, 197, 94, 0.25),
    inset 0 0 14px rgba(220, 252, 231, 0.35);
  animation: globPulse 1.6s ease-in-out infinite;
}
@keyframes globPulse {
  0%, 100% { box-shadow: 0 0 0 2px rgba(34, 197, 94, 0.25), 0 0 28px rgba(34, 197, 94, 0.55), 0 0 60px rgba(34, 197, 94, 0.25), inset 0 0 14px rgba(220, 252, 231, 0.35); }
  50%      { box-shadow: 0 0 0 2px rgba(34, 197, 94, 0.40), 0 0 40px var(--border), 0 0 80px rgba(34, 197, 94, 0.35), inset 0 0 20px rgba(220, 252, 231, 0.55); }
}

/* 7 个粒子旋转 */
.btn-particles {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  animation: particleSpin 1.1s linear infinite;
}
.btn-particles i {
  width: 4px; height: 4px; border-radius: 50%;
  background: #dcfce7;
  box-shadow: 0 0 6px rgba(220, 252, 231, 0.9);
  animation: particleBlink 1.1s linear infinite;
}
.btn-particles i:nth-child(2) { animation-delay: 0.1s; }
.btn-particles i:nth-child(3) { animation-delay: 0.2s; }
.btn-particles i:nth-child(4) { animation-delay: 0.3s; }
.btn-particles i:nth-child(5) { animation-delay: 0.4s; }
.btn-particles i:nth-child(6) { animation-delay: 0.5s; }
.btn-particles i:nth-child(7) { animation-delay: 0.6s; }
@keyframes particleSpin { to { transform: rotate(360deg); } }
@keyframes particleBlink {
  0%, 100% { opacity: 0.3; transform: scale(0.7); }
  50%      { opacity: 1;   transform: scale(1.2); }
}

/* 对勾 */
.auth-login-btn.btn-check-ok { background: radial-gradient(circle at 35% 35%, #22c55e, #16a34a 55%, #14532d); }
.btn-check {
  position: absolute; inset: 50%; transform: translate(-50%, -50%);
  width: 26px; height: 26px;
}
.btn-check-circle { stroke: #dcfce7; stroke-width: 3; stroke-linecap: round;
  stroke-dasharray: 166; stroke-dashoffset: 166; fill: none; }
.btn-check-tick   { stroke: #dcfce7; stroke-width: 4; stroke-linecap: round; stroke-linejoin: round;
  stroke-dasharray: 50; stroke-dashoffset: 50; fill: none; }
.btn-check-ok .btn-check-circle { animation: checkCircle 0.45s ease 0.05s forwards; }
.btn-check-ok .btn-check-tick   { animation: checkTick   0.3s ease  0.35s forwards; }
@keyframes checkCircle { to { stroke-dashoffset: 0; } }
@keyframes checkTick   { to { stroke-dashoffset: 0; } }

/* 失败抖动 */
.auth-login-btn.btn-shake { animation: btnShake 0.45s ease; }
@keyframes btnShake {
  0%, 100% { transform: translateX(0); }
  20%, 60% { transform: translateX(-6px); }
  40%, 80% { transform: translateX(6px); }
}
</style>
