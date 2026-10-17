import { getToken } from '@/api/client'

export async function downloadFile(path, name) {
  const token = getToken()
  try {
    const res = await fetch(import.meta.env.VITE_API_BASE + path, { headers: { Authorization: 'Bearer ' + token } })
    const blob = await res.blob()
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob); a.download = name
    document.body.appendChild(a); a.click(); a.remove()
    URL.revokeObjectURL(a.href)
  } catch (e) { alert('下载失败：' + e.message) }
}

export async function importCsv(type, file) {
  const token = getToken()
  const fd = new FormData(); fd.append('file', file)
  const res = await fetch(import.meta.env.VITE_API_BASE + '/api/data/import/' + type, {
    method: 'POST', headers: { Authorization: 'Bearer ' + token }, body: fd,
  })
  return await res.json()
}
