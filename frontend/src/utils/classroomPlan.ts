/**
 * 教室目录：以学生端 3D 沙盘 CAMPUS_BUILDINGS 的规范楼名与层数为唯一依据。
 *
 * 真实高校排课口径：
 * - 房号 = 楼层 × 100 + 该层序号（101/102…、201/202…），楼层不得超过沙盘建筑层数
 * - 教室名称统一「楼栋-房号」，location / building 存规范楼名，供 3D 沙盘 buildingOf() 直接命中
 * - 按楼栋功能分配教室类型：教学楼=普通/多媒体/阶梯，工科楼=机房/实验室，外语楼=语音室，
 *   培训中心=实训室，报告厅/体育馆/图书馆单独归类
 * - 容量与设备由类型决定，不再出现「40 人阶梯教室」「普通教室配电脑」这类组合
 * - 少量教室标记维护中，符合真实教务状态
 */
import { CAMPUS_BUILDINGS } from './campusConfig'

export type RoomType =
  | '普通教室' | '多媒体教室' | '阶梯教室' | '计算机实验室' | '语音室'
  | '实验室' | '实训室' | '报告厅' | '体育场馆' | '研讨室'

export interface RoomTypeSpec {
  label: RoomType
  /** 合理容量档位 */
  capacities: number[]
  equipment: string
  /** 卡片标签色调 */
  tone: 'gray' | 'green' | 'blue' | 'purple' | 'amber'
}

export const ROOM_TYPES: Record<RoomType, RoomTypeSpec> = {
  '普通教室':     { label: '普通教室',     capacities: [40, 60],       equipment: '白板、空调',                       tone: 'gray' },
  '多媒体教室':   { label: '多媒体教室',   capacities: [80, 120],      equipment: '多媒体、投影仪、扩音器、空调',     tone: 'green' },
  '阶梯教室':     { label: '阶梯教室',     capacities: [160, 200],     equipment: '多媒体、投影仪、扩音器、录播、空调', tone: 'purple' },
  '计算机实验室': { label: '计算机实验室', capacities: [40, 60],       equipment: '电脑、投影仪、多媒体、空调',       tone: 'blue' },
  '语音室':       { label: '语音室',       capacities: [40, 50],       equipment: '语音设备、投影仪、空调',           tone: 'blue' },
  '实验室':       { label: '实验室',       capacities: [30, 40],       equipment: '实验台、白板、空调',               tone: 'blue' },
  '实训室':       { label: '实训室',       capacities: [40, 60],       equipment: '实训设备、白板、空调',             tone: 'amber' },
  '报告厅':       { label: '报告厅',       capacities: [200, 300],     equipment: '多媒体、投影仪、扩音器、录播、空调', tone: 'purple' },
  '体育场馆':     { label: '体育场馆',     capacities: [120, 200],     equipment: '体育器材、扩音器',                 tone: 'amber' },
  '研讨室':       { label: '研讨室',       capacities: [20, 30],       equipment: '白板、空调',                       tone: 'gray' },
}

export const ROOM_TYPE_LIST = Object.keys(ROOM_TYPES) as RoomType[]
export const ROOM_STATUS = ['正常', '维护中'] as const
export type RoomStatus = typeof ROOM_STATUS[number]

export interface BuildingSpec {
  name: string
  zone: string
  role: string
  /** 实际投入排课的楼层数（≤ 沙盘建筑层数） */
  floorsUsed: number
  perFloor: number
  /** 每层按序号循环的类型 */
  cycle: RoomType[]
  /** 一层首间改作大教室（合班课 / 报告厅） */
  large?: RoomType
}

/** 楼栋功能规划，顺序与沙盘主路西侧 → 教学群 → 公共服务 → 南区一致 */
export const BUILDING_SPECS: BuildingSpec[] = [
  { name: '培训中心二期', zone: '主路西侧', role: '创新创业 · 实训基地', floorsUsed: 3, perFloor: 2, cycle: ['实训室', '研讨室'] },
  { name: '俊秀图书馆',   zone: '主路西侧', role: '图书馆研讨间',       floorsUsed: 2, perFloor: 2, cycle: ['研讨室', '研讨室'] },
  { name: '培训中心一期', zone: '主路西侧', role: '工程训练中心',       floorsUsed: 3, perFloor: 2, cycle: ['实训室', '实验室'] },

  { name: '大象楼', zone: '教学群', role: '公共教学楼',           floorsUsed: 3, perFloor: 3, cycle: ['多媒体教室', '普通教室', '多媒体教室'], large: '阶梯教室' },
  { name: '大音楼', zone: '教学群', role: '艺术与语言教学楼',     floorsUsed: 3, perFloor: 3, cycle: ['普通教室', '语音室', '多媒体教室'],     large: '阶梯教室' },
  { name: '大器楼', zone: '教学群', role: '工科实验楼',           floorsUsed: 3, perFloor: 3, cycle: ['计算机实验室', '实验室', '多媒体教室'], large: '阶梯教室' },
  { name: '恒思楼', zone: '教学群', role: '理学实验楼',           floorsUsed: 3, perFloor: 2, cycle: ['实验室', '多媒体教室'] },
  { name: '大方楼', zone: '教学群', role: '文科教学楼',           floorsUsed: 3, perFloor: 2, cycle: ['普通教室', '多媒体教室'] },
  { name: '望星楼', zone: '教学群', role: '文科教学楼',           floorsUsed: 3, perFloor: 2, cycle: ['多媒体教室', '普通教室'],   large: '阶梯教室' },
  { name: '洪涛楼', zone: '教学群', role: '公共教学楼',           floorsUsed: 3, perFloor: 2, cycle: ['多媒体教室', '普通教室'] },
  { name: '求实楼', zone: '教学群', role: '第一教学楼（原教1）',  floorsUsed: 3, perFloor: 3, cycle: ['多媒体教室', '普通教室', '多媒体教室'], large: '阶梯教室' },
  { name: '知新楼', zone: '教学群', role: '第二教学楼',           floorsUsed: 3, perFloor: 2, cycle: ['计算机实验室', '普通教室'] },
  { name: '闻道楼', zone: '教学群', role: '外语楼 · 语音室',       floorsUsed: 3, perFloor: 3, cycle: ['语音室', '普通教室', '多媒体教室'],     large: '阶梯教室' },
  { name: '温故楼', zone: '教学群', role: '公共教学楼',           floorsUsed: 3, perFloor: 2, cycle: ['多媒体教室', '普通教室'] },
  { name: '学思楼', zone: '教学群', role: '公共教学楼',           floorsUsed: 3, perFloor: 2, cycle: ['普通教室', '多媒体教室'] },

  { name: '齐礼楼',   zone: '公共服务', role: '报告厅 · 合班教室',   floorsUsed: 2, perFloor: 1, cycle: ['阶梯教室'],     large: '报告厅' },
  { name: '明德厅',   zone: '公共服务', role: '学术报告厅',         floorsUsed: 1, perFloor: 1, cycle: ['报告厅'] },
  { name: '至善厅',   zone: '公共服务', role: '学术报告厅',         floorsUsed: 1, perFloor: 1, cycle: ['报告厅'] },
  { name: '体育馆',   zone: '公共服务', role: '体育教学场馆',       floorsUsed: 2, perFloor: 2, cycle: ['体育场馆', '体育场馆'] },
  { name: '半月楼',   zone: '公共服务', role: '继续教育 · 研讨',     floorsUsed: 3, perFloor: 2, cycle: ['研讨室', '多媒体教室'] },

  { name: '明志楼', zone: '南区', role: '信息楼 · 机房',     floorsUsed: 4, perFloor: 3, cycle: ['计算机实验室', '多媒体教室', '普通教室'], large: '阶梯教室' },
  { name: '致远楼', zone: '南区', role: '公共教学楼',       floorsUsed: 3, perFloor: 2, cycle: ['普通教室', '多媒体教室'] },
]

export interface ClassroomPlanItem {
  roomNo: string
  name: string
  building: string
  zone: string
  role: string
  floor: number
  seq: number
  type: RoomType
  capacity: number
  equipment: string
  location: string
  status: RoomStatus
}

/** 沙盘建筑名 → 层数（楼层上限的唯一来源） */
const FLOORS = new Map(CAMPUS_BUILDINGS.map(b => [b.name, b.floors]))

/** 生成完整教室目录；无副作用、可重复调用，供 UI 与数据迁移共用 */
export function generateClassroomPlan(): ClassroomPlanItem[] {
  const out: ClassroomPlanItem[] = []
  let n = 0
  for (const spec of BUILDING_SPECS) {
    const maxFloor = FLOORS.get(spec.name)
    if (!maxFloor) throw new Error(`沙盘中不存在楼栋：${spec.name}`)
    const floors = Math.min(spec.floorsUsed, maxFloor)
    for (let floor = 1; floor <= floors; floor++) {
      for (let seq = 1; seq <= spec.perFloor; seq++) {
        const type: RoomType = (floor === 1 && seq === 1 && spec.large)
          ? spec.large
          : spec.cycle[(floor + seq - 2) % spec.cycle.length]
        const spec2 = ROOM_TYPES[type]
        const room = `${floor}${String(seq).padStart(2, '0')}`
        const name = `${spec.name}-${room}`
        out.push({
          roomNo: name,
          name,
          building: spec.name,
          zone: spec.zone,
          role: spec.role,
          floor,
          seq,
          type,
          capacity: spec2.capacities[(floor + seq) % spec2.capacities.length],
          equipment: spec2.equipment,
          location: spec.name,
          status: n % 17 === 16 ? '维护中' : '正常',
        })
        n++
      }
    }
  }
  return out
}

/* ------------------------------------------------------------------
 * 历史楼名 → 沙盘规范楼名（课程 location 迁移用）
 * ------------------------------------------------------------------ */
const norm = (s: string) => String(s || '').replace(/[\s\-_·]/g, '').toLowerCase()
const ALIAS = new Map<string, string>()
for (const b of CAMPUS_BUILDINGS) {
  ALIAS.set(norm(b.name), b.name)
  for (const a of b.aka || []) ALIAS.set(norm(a), b.name)
}
// 沙盘 aka 未覆盖、但历史数据里出现过的别名
for (const [alias, canonical] of [
  ['物理楼', '恒思楼'], ['理科楼', '恒思楼'], ['物理实验楼', '恒思楼'],
  ['信息楼', '明志楼'], ['计算机楼', '明志楼'],
  ['教1', '求实楼'], ['教一', '求实楼'], ['求索楼', '求实楼'],
  ['外语楼', '闻道楼'], ['培训中心二附', '培训中心二期'],
] as [string, string][]) ALIAS.set(norm(alias), canonical)

/** 旧教室串 → 规范楼名（"信息楼-401" → "明志楼"；"大方楼101" → "大方楼"） */
export function resolveBuilding(raw: string): string {
  const s = String(raw || '').trim()
  const aliasKeys = [...ALIAS.keys()].sort((a, b) => b.length - a.length)
  const key = aliasKeys.find(k => norm(s).startsWith(k))
  if (key) return ALIAS.get(key)!
  const head = s.split(/[-－—\d]/)[0].trim()
  return ALIAS.get(norm(head)) || head || '求实楼'
}

/**
 * 把旧课程地点映射到新目录中的真实教室。
 * 同一楼栋内按序轮询分配；同一教室被多门课复用是真实排课常态（不同时段），因此允许复用。
 */
export function buildLocationMigration(oldLocations: string[], plan = generateClassroomPlan()): Record<string, string> {
  const byBuilding = new Map<string, ClassroomPlanItem[]>()
  for (const r of plan) {
    if (!byBuilding.has(r.building)) byBuilding.set(r.building, [])
    byBuilding.get(r.building)!.push(r)
  }
  const all = plan
  const cursor = new Map<string, number>()
  const map: Record<string, string> = {}
  for (const raw of oldLocations) {
    const key = String(raw || '').trim()
    if (!key) continue
    const b = resolveBuilding(key)
    const pool = byBuilding.get(b)?.length ? byBuilding.get(b)! : all
    const i = cursor.get(b) || 0
    cursor.set(b, i + 1)
    map[key] = pool[i % pool.length].name
  }
  return map
}
