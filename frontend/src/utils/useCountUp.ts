import { ref, watch, onUnmounted, type Ref } from 'vue'

export interface CountUpOptions {
  /** 小数位数（默认 0） */
  decimals?: number
  /** 动画时长 ms（默认 900） */
  duration?: number
  /** 自定义格式化（默认按 decimals 取值；千分位场景传 (v) => v.toLocaleString()） */
  format?: (v: number) => string
}

/**
 * 数字滚动：目标值变化时从当前显示值 easeOutCubic 缓动到新值。
 * source 为数字 ref 或 getter；返回展示字符串 ref，首次与每次变化都滚动。
 * prefers-reduced-motion 用户跳过动画直接显示目标值。
 */
export function useCountUp(source: Ref<number> | (() => number), options: CountUpOptions = {}) {
  const decimals = options.decimals ?? 0
  const duration = options.duration ?? 900
  const fmt = options.format
    ?? ((v: number) => (decimals ? Number(v).toFixed(decimals) : String(Math.round(v))))
  const reduced = typeof window !== 'undefined'
    && !!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches

  const display = ref(fmt(0))
  let raf = 0
  let current = 0

  const targetOf = () => {
    const v = typeof source === 'function' ? (source as () => number)() : (source as Ref<number>).value
    const n = Number(v)
    return Number.isFinite(n) ? n : 0
  }

  function animate() {
    cancelAnimationFrame(raf)
    const to = targetOf()
    // 后台标签页里浏览器会暂停 requestAnimationFrame：若仍走动画路径，
    // 展示值会永久冻结在旧值（典型症状：后台打开首页 → 切回来数字全是 0）。
    // 页面不可见时直接落定目标值；切回前台时由 visibilitychange 补一次滚动。
    if (reduced || (typeof document !== 'undefined' && document.hidden)) {
      current = to
      display.value = fmt(to)
      return
    }
    const from = current
    if (from === to) { display.value = fmt(to); return }
    const t0 = performance.now()
    const step = (now: number) => {
      const p = Math.min(1, (now - t0) / duration)
      const e = 1 - Math.pow(1 - p, 3) // easeOutCubic，与毕业进度环/Stats 原滚动一致
      current = from + (to - from) * e
      display.value = fmt(current)
      if (p < 1) raf = requestAnimationFrame(step)
    }
    raf = requestAnimationFrame(step)
  }

  watch(targetOf, animate, { immediate: true })
  // 半程冻结恢复：动画中途切后台 → rAF 暂停 → 值停在半路；切回前台时补齐
  const onVisChange = () => { if (document.visibilityState === 'visible') animate() }
  document.addEventListener('visibilitychange', onVisChange)
  onUnmounted(() => {
    document.removeEventListener('visibilitychange', onVisChange)
    cancelAnimationFrame(raf)
  })

  return display
}
