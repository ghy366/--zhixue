/**
 * 玻璃卡片鼠标跟随液态高光（全局事件委托）
 * - pointermove 时把光标位置写成卡片上的 --gx/--gy 百分比变量
 * - 卡片 ::after/::before 高光通过 radial-gradient at var(--gx) var(--gy) 跟随
 * - 只写 CSS 变量 + rAF 节流，不触发布局；未悬停的卡片保持默认位置
 * - 遵循 prefers-reduced-motion 降级
 */
const SELECTOR = [
  '.stat-card', '.section', '.course-card', '.credit-chip', '.dv-card', '.empty-card',
  '.ws-teacher-stu-card', '.an-panel', '.an-kpi', '.ws-header-card', '.ws-requirement-card',
  '.ws-paper-row', '.tc-card', '.wl-card', '.advisor-card',
].join(',')

let raf = 0
let el: HTMLElement | null = null
let ev: PointerEvent | null = null

function apply() {
  raf = 0
  if (!el || !ev) return
  const rect = el.getBoundingClientRect()
  const px = ((ev.clientX - rect.left) / rect.width) * 100
  const py = ((ev.clientY - rect.top) / rect.height) * 100
  el.style.setProperty('--gx', px.toFixed(2) + '%')
  el.style.setProperty('--gy', py.toFixed(2) + '%')
}

export function setupGlassGlow() {
  if (typeof window === 'undefined') return
  if (!window.matchMedia('(hover: hover) and (pointer: fine)').matches) return
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return

  document.addEventListener('pointermove', (e) => {
    const t = (e.target as HTMLElement)?.closest?.(SELECTOR) as HTMLElement | null
    if (!t) return
    el = t
    ev = e
    if (!raf) raf = requestAnimationFrame(apply)
  }, { passive: true })
}
