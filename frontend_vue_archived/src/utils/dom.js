// 轻量 DOM 工具 + 全局 toast（复用 legacy.css 的 .toast 视觉）
export function toast(msg, type = 'info') {
  let box = document.getElementById('toast')
  if (!box) {
    box = document.createElement('div')
    box.id = 'toast'
    box.className = 'toast'
    document.body.appendChild(box)
  }
  const el = document.createElement('div')
  el.className = 'toast-item toast-' + type
  el.textContent = msg
  box.appendChild(el)
  requestAnimationFrame(() => el.classList.add('toast-show'))
  setTimeout(() => {
    el.classList.remove('toast-show')
    setTimeout(() => el.remove(), 300)
  }, 2600)
}

export function formatDateTime(s) {
  if (!s) return ''
  const d = new Date(s)
  if (isNaN(d)) return s
  const p = (n) => (n < 10 ? '0' + n : '' + n)
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

export function weekdayCN(d) {
  const w = ['日', '一', '二', '三', '四', '五', '六']
  return '星期' + w[new Date(d).getDay()]
}
