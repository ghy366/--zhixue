/**
 * 高校标准排课时段目录
 *
 * 口径（与课表网格、后端冲突检测保持一致）：
 * - 一次课 = 2 小节连堂 = 2 学时；全天 5 个大节：1-2 / 3-4 / 5-6 / 7-8 / 9-10
 * - 时间串固定为「周X HH:MM-HH:MM」，每周多次用 " / " 连接
 *   后端 ScheduleService / EnrollmentService / AiContextService 均按此正则解析
 * - 起始时刻必须是 08:00 / 10:00 / 14:00 / 16:00 / 19:00，
 *   否则前端课表 parseSchedule 无法映射到节次格子，冲突检测会漏判
 *
 * 排课惯例：
 * - 一门课每周多次应隔天安排（周一+周三、周二+周四…），优先同一节次（教室固定、便于记忆）
 * - 同一门课不在同一天排两次
 * - 晚间第 9-10 节、周六日一般只排公选 / 重修 / 辅修 / 继续教育，不排必修
 */

export interface TeachingPeriod {
  key: string      // '1-2'
  label: string    // '第1-2节'
  from: string
  to: string
  part: string     // 上午 / 下午 / 晚上
}

export const TEACHING_PERIODS: TeachingPeriod[] = [
  { key: '1-2',  label: '第1-2节',  from: '08:00', to: '09:40', part: '上午' },
  { key: '3-4',  label: '第3-4节',  from: '10:00', to: '11:40', part: '上午' },
  { key: '5-6',  label: '第5-6节',  from: '14:00', to: '15:40', part: '下午' },
  { key: '7-8',  label: '第7-8节',  from: '16:00', to: '17:40', part: '下午' },
  { key: '9-10', label: '第9-10节', from: '19:00', to: '20:40', part: '晚上' },
]

/** 标准起始时刻 → 对应结束时刻，用于校验自定义时间 */
export const STANDARD_RANGES: Record<string, string> = {
  '08:00': '09:40', '10:00': '11:40', '14:00': '15:40',
  '16:00': '17:40', '19:00': '20:40',
}

export const WEEKDAYS = ['周一', '周二', '周三', '周四', '周五']
export const WEEKENDS = ['周六', '周日']

/** 每周两次的隔天组合：避开连续两天，且避开周五下午集中排课 */
const TWO_DAY_PAIRS: [string, string][] = [
  ['周一', '周三'], ['周一', '周四'], ['周二', '周四'],
  ['周二', '周五'], ['周三', '周五'],
]
/** 每周三次：隔天三次，高学时课程（如高等数学）的典型排法 */
const THREE_DAY_GROUP = ['周一', '周三', '周五']

export interface TimeOption {
  value: string   // 存入数据库的规范时间串
  label: string   // 下拉过滤用的文本（=value）
  meta: string    // 右侧说明：节次 / 排课规则
}
export interface TimeOptionGroup {
  label: string
  options: TimeOption[]
}

export function slotText(day: string, p: TeachingPeriod): string {
  return `${day} ${p.from}-${p.to}`
}

function opt(value: string, meta: string): TimeOption {
  return { value, label: value, meta }
}

/** 生成内置标准排课选项（按周学时模式分组，供新增/编辑课程下拉使用） */
export function buildTimeOptionGroups(): TimeOptionGroup[] {
  const day = TEACHING_PERIODS.filter(p => p.key !== '9-10')
  const night = TEACHING_PERIODS.find(p => p.key === '9-10')!
  const byKey = Object.fromEntries(TEACHING_PERIODS.map(p => [p.key, p])) as Record<string, TeachingPeriod>

  // ① 每周 1 次：2 学时 / 周，多为 2 学分课程
  const once: TimeOption[] = []
  for (const d of WEEKDAYS) {
    for (const p of day) {
      once.push(opt(slotText(d, p), `${p.label} · ${p.part} · 2 学时/周`))
    }
  }

  // ② 每周 2 次：4 学时 / 周，多为 4 学分课程
  const twice: TimeOption[] = []
  for (const [a, b] of TWO_DAY_PAIRS) {
    for (const p of day) {
      twice.push(opt(
        `${slotText(a, p)} / ${slotText(b, p)}`,
        `隔天 · ${p.label} 同一节次 · 教室固定 · 4 学时/周`,
      ))
    }
  }
  // 上下午错节次：真实排课中用于避开实验室/合班课，仍保持隔天
  for (const [a, b] of TWO_DAY_PAIRS) {
    for (const [ka, kb] of [['1-2', '5-6'], ['3-4', '7-8']] as [string, string][]) {
      twice.push(opt(
        `${slotText(a, byKey[ka])} / ${slotText(b, byKey[kb])}`,
        `隔天 · 上午+下午错节次 · 4 学时/周`,
      ))
    }
  }

  // ③ 每周 3 次：6 学时 / 周，高学时公共基础课
  const thrice: TimeOption[] = day.map(p => opt(
    THREE_DAY_GROUP.map(d => slotText(d, p)).join(' / '),
    `隔天三次 · ${p.label} 同一节次 · 6 学时/周`,
  ))

  // ④ 晚间 / 周末：公选、重修、辅修专用时段
  const extra: TimeOption[] = []
  for (const d of WEEKDAYS) {
    extra.push(opt(slotText(d, night), `${night.label} · 晚间 · 公选/重修`))
  }
  for (const d of WEEKENDS) {
    for (const p of day) {
      extra.push(opt(slotText(d, p), `${p.label} · 周末 · 辅修/重修`))
    }
  }

  return [
    { label: '每周 1 次 · 2 学时（2 学分课程）', options: once },
    { label: '每周 2 次 · 4 学时（4 学分课程）', options: twice },
    { label: '每周 3 次 · 6 学时（6 学分课程）', options: thrice },
    { label: '晚间 / 周末（公选 · 重修 · 辅修）', options: extra },
  ]
}

/** 解析时间串，返回每周学时数、连堂次数与排课异常提示 */
export function describeSchedule(raw: string): {
  count: number
  hours: number
  nonStandard: boolean
  sameDay: boolean
  nightOrWeekend: boolean
} | null {
  if (!raw || !raw.trim()) return null
  const re = /(周[一二三四五六日])\s*(\d{2}:\d{2})-(\d{2}:\d{2})/g
  const found = [...raw.matchAll(re)]
  if (!found.length) return null
  const days = found.map(m => m[1])
  return {
    count: found.length,
    hours: found.length * 2,
    nonStandard: found.some(m => STANDARD_RANGES[m[2]] !== m[3]),
    sameDay: new Set(days).size !== days.length,
    nightOrWeekend: found.some(m => m[2] >= '19:00' || m[1] === '周六' || m[1] === '周日'),
  }
}
