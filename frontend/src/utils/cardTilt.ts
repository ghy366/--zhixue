/**
 * 课程卡片悬浮 3D 倾斜（全局事件委托，无需改页面模板）
 * - 仅在支持 hover 的设备生效
 * - 遵循 prefers-reduced-motion 降级
 */
export function setupCardTilt() {
  if (typeof window === 'undefined') return
  if (!window.matchMedia('(hover: hover) and (pointer: fine)').matches) return
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return

  const SELECTOR = '.course-card, .stat-card, .tc-card, .wl-card, .advisor-card'
  const MAX_TILT = 6      // 最大倾斜角度（度）
  const SCALE = 1.015     // 悬浮轻微放大
  let raf = 0

  function apply(el: HTMLElement, e: MouseEvent) {
    const rect = el.getBoundingClientRect()
    const px = (e.clientX - rect.left) / rect.width   // 0~1
    const py = (e.clientY - rect.top) / rect.height
    const rx = (0.5 - py) * MAX_TILT * 2              // 上抬为负角度
    const ry = (px - 0.5) * MAX_TILT * 2
    cancelAnimationFrame(raf)
    raf = requestAnimationFrame(() => {
      el.style.transform = `perspective(800px) rotateX(${rx.toFixed(2)}deg) rotateY(${ry.toFixed(2)}deg) scale(${SCALE})`
      el.style.transition = 'transform .08s linear, box-shadow .25s ease'
      el.style.willChange = 'transform'
      el.style.zIndex = '2'
    })
  }

  function reset(el: HTMLElement) {
    cancelAnimationFrame(raf)
    el.style.transition = 'transform .35s cubic-bezier(.22,1,.36,1), box-shadow .25s ease'
    el.style.transform = ''
    el.style.willChange = ''
    el.style.zIndex = ''
  }

  document.addEventListener('mouseover', (e) => {
    const t = e.target as HTMLElement
    if (!t || !t.closest) return
    const card = t.closest(SELECTOR) as HTMLElement | null
    if (card && !card.contains(e.relatedTarget as Node)) apply(card, e)
  })

  document.addEventListener('mouseout', (e) => {
    const t = e.target as HTMLElement
    if (!t || !t.closest) return
    const card = t.closest(SELECTOR) as HTMLElement | null
    if (card && !card.contains(e.relatedTarget as Node)) reset(card)
  })
}
