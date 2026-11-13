import { ref } from 'vue'
import http from '@/api'

/** 三个选课决策工具共用的真实业务数据（60 秒缓存，避免切换工具重复请求） */
export interface ToolData {
  courses: any[]
  cfg: any
  grad: any
  points: any
  intents: any[]
  mine: any[]
  isStudent: boolean
}

const cached = ref<ToolData | null>(null)
let cachedAt = 0
let inflight: Promise<ToolData> | null = null

const get = (p: string) => http.get(p).then((r: any) => r?.data ?? null).catch(() => null)

export async function loadToolData(force = false): Promise<ToolData> {
  if (!force && cached.value && Date.now() - cachedAt < 60_000) return cached.value
  if (inflight) return inflight
  inflight = (async () => {
    const [courses, cfg, grad, points, intents, mine] = await Promise.all([
      get('/courses'), get('/system/config'), get('/graduation/summary'),
      get('/enrollments/points'), get('/intents/mine'), get('/enrollments/mine'),
    ])
    const d: ToolData = {
      courses: Array.isArray(courses) ? courses : [],
      cfg: cfg || {},
      grad: grad || null,
      points: points || null,
      intents: Array.isArray(intents) ? intents : [],
      mine: Array.isArray(mine) ? mine : [],
      isStudent: !!points && !!grad,
    }
    cached.value = d
    cachedAt = Date.now()
    inflight = null
    return d
  })()
  return inflight
}

/** 解析「周一 10:00-11:40 / 周三 14:00-15:40」，与后端 hasTimeConflict 同口径 */
export interface Slot { day: string; s: number; e: number; label: string }
export function timeSlots(time: any): Slot[] {
  if (!time || typeof time !== 'string') return []
  const out: Slot[] = []
  const re = /(周[一二三四五六日])\s+(\d{2}:\d{2})-(\d{2}:\d{2})/g
  let m: RegExpExecArray | null
  while ((m = re.exec(time))) {
    const toMin = (t: string) => Number(t.slice(0, 2)) * 60 + Number(t.slice(3, 5))
    out.push({ day: m[1], s: toMin(m[2]), e: toMin(m[3]), label: `${m[2]}-${m[3]}` })
  }
  return out
}

/** 两个时间段是否重叠 */
export function overlap(a: Slot[], b: Slot[]): boolean {
  for (const x of a) for (const y of b) {
    if (x.day === y.day && x.s < y.e && y.s < x.e) return true
  }
  return false
}

/** 课程类别 → 毕业大类（与后端 CourseGroup 一致） */
export function groupOf(category: any): string {
  const c = String(category || '')
  if (c.includes('必修')) return '必修'
  if (c.includes('实践')) return '实践'
  if (c.includes('通识')) return '通识'
  return '选修'
}
