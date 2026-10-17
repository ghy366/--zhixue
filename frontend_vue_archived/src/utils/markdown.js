// 轻量 Markdown 渲染（复刻原 app.js 的 aiRenderMarkdown），无外部依赖、防 XSS
function esc(t) {
  return t.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}
function inline(t) {
  let s = esc(t)
  s = s.replace(/`([^`]+)`/g, '<code class="ai-md-code">$1</code>')
  s = s.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
  s = s.replace(/(^|[^*])\*([^*]+)\*/g, '$1<em>$2</em>')
  s = s.replace(/\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g, '<a href="$2" target="_blank" rel="noopener">$1</a>')
  return s
}
export function aiRenderMarkdown(text) {
  if (!text) return ''
  const lines = String(text).split('\n')
  const blocks = []
  let i = 0
  while (i < lines.length) {
    const line = lines[i]
    const cb = line.match(/^```(\w*)\s*$/)
    if (cb) {
      const buf = []
      i++
      while (i < lines.length && !/^```\s*$/.test(lines[i])) { buf.push(lines[i]); i++ }
      i++
      blocks.push('<pre class="ai-md-pre"><code>' + esc(buf.join('\n')) + '</code></pre>')
      continue
    }
    const h = line.match(/^(#{1,4})\s+(.*)$/)
    if (h) {
      const lv = h[1].length
      blocks.push(`<h${lv} class="ai-md-h">${inline(h[2])}</h${lv}>`)
      i++; continue
    }
    const q = line.match(/^>\s?(.*)$/)
    if (q) { blocks.push(`<blockquote class="ai-md-quote">${inline(q[1])}</blockquote>`); i++; continue }
    const ol = line.match(/^(\d+)\.\s+(.*)$/)
    if (ol) {
      const items = []
      while (i < lines.length) {
        const m = lines[i].match(/^(\d+)\.\s+(.*)$/)
        if (!m) break
        items.push(`<li>${inline(m[2])}</li>`); i++
      }
      blocks.push('<ol class="ai-md-ol">' + items.join('') + '</ol>')
      continue
    }
    const ul = line.match(/^[-*]\s+(.*)$/)
    if (ul) {
      const items = []
      while (i < lines.length) {
        const m = lines[i].match(/^[-*]\s+(.*)$/)
        if (!m) break
        items.push(`<li>${inline(m[1])}</li>`); i++
      }
      blocks.push('<ul class="ai-md-ul">' + items.join('') + '</ul>')
      continue
    }
    if (line.trim() === '') { i++; continue }
    const para = [line]
    i++
    while (i < lines.length && lines[i].trim() !== '' &&
      !/^(#{1,4}\s|>\s|\d+\.\s|[-*]\s|```)/.test(lines[i])) {
      para.push(lines[i]); i++
    }
    blocks.push(`<p class="ai-md-p">${inline(para.join(' '))}</p>`)
  }
  return blocks.join('')
}
