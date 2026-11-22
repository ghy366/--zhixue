/**
 * 学期序号 → 通俗学期名（四年制：大一上/大一下…大四下；专升本两年制：大三上…大四下）。
 * 统一口径：课表 / 培养方案 / 成绩档案 / 我的课程 / 星图 都用它，避免各处"第 N 学期"不一致。
 */

const YEAR_CN = ['一', '二', '三', '四', '五']
const TERM_CN = ['上', '下']

/** 是否专升本（从 localStorage 用户资料读，兼容  academic major 含"(专升本)"） */
export function isZsbUser(): boolean {
  try {
    const u = JSON.parse(localStorage.getItem('zhixue_user') || '{}')
    return String(u.major || '').includes('专升本')
  } catch { return false }
}

/**
 * 学期序号 → "大一上"等通俗名。
 * @param no 学期序（1~8）
 * @param zsb 是否专升本（两年制，从第 1 学期映射到大三上）
 */
export function semesterTitle(no: number | null | undefined, zsb = false): string {
  if (no == null || no < 1) return '—'
  // 专升本两年制：1→大三上, 2→大三下, 3→大四上, 4→大四下
  const seq = zsb ? no + 4 : no
  const yi = Math.floor((seq - 1) / 2)
  const term = (seq - 1) % 2
  if (yi >= YEAR_CN.length) return `第 ${no} 学期`
  return `大${YEAR_CN[yi]}${TERM_CN[term]}`
}

/** 带学年前缀的完整标签，如 "2025-2026学年 · 大一上" */
export function semesterLabel(no: number | null | undefined, grade?: number | null, zsb = false): string {
  const title = semesterTitle(no, zsb)
  if (!grade || grade < 2000 || no == null) return title
  const seq = zsb ? no + 4 : no
  const startYear = grade + Math.floor((seq - 1) / 2)
  return `${startYear}-${startYear + 1}学年 · ${title}`
}
