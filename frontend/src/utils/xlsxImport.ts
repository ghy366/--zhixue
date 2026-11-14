import http from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

/** 管理员专用：下载 .xlsx 导入模板 */
export async function downloadTemplate(kind: 'students' | 'courses') {
  try {
    const r: any = await http.get(`/data/template/${kind}`, { responseType: 'blob' })
    const blob = r instanceof Blob
      ? r
      : new Blob([r], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${kind}-template.xlsx`
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
  } catch (e: any) {
    ElMessage.error(e?.message || '模板下载失败')
  }
}

/** 选择 .xlsx 并导入；成功后回调刷新列表 */
export function pickAndImport(kind: 'students' | 'courses', onDone?: () => void) {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.xlsx'
  input.onchange = async () => {
    const file = input.files?.[0]
    if (!file) return
    if (!file.name.toLowerCase().endsWith('.xlsx')) {
      ElMessage.error('只支持 .xlsx 文件，请先下载模板填写')
      return
    }
    const fd = new FormData()
    fd.append('file', file)
    try {
      const r: any = await http.post(`/data/import/${kind}`, fd)
      const d = r?.data || r || {}
      const errs: string[] = d.errors || []
      await ElMessageBox.alert(
        `新增 ${d.created ?? 0} · 更新 ${d.updated ?? 0} · 跳过 ${d.skipped ?? 0}`
        + (errs.length ? `\n\n前 5 条问题：\n${errs.slice(0, 5).join('\n')}` : ''),
        '导入结果',
        { type: errs.length ? 'warning' : 'success' }
      )
      onDone?.()
    } catch (e: any) {
      ElMessage.error(e?.message || '导入失败')
    }
  }
  input.click()
}
