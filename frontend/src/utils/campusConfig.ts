/**
 * 校园沙盘配置：等轴测平面地图（CampusMap.vue）与 3D 场景（CampusMap3D.vue）共用。
 *
 * 布局依据：厦门华厦学院「我在华厦」官方手绘地图 + 招生办「航拍校园」实景照片。
 * 手绘图按上北下南（y 递增 = 向南），校园整体为方正地块，主入口在南面正中：
 *   ┌ 北部：培训中心二期（西北）、钟楼、右上整片 7 行横向教学群
 *   ├ 中上：俊秀图书馆（西侧），湖紧贴图书馆东侧
 *   ├ 中部：圆形凉亭广场在校园正中偏东、湖的东南；明德厅 / 至善厅 / LeHAS 餐厅 / 半月楼在东侧一排
 *   ├ 中下：齐礼楼（西侧弧形楼，校门内左侧）、致远楼、明志楼
 *   ├ 东南：体育馆 + 斜向椭圆跑道；西南：红色宿舍组团、培训中心一期
 *   └ 南部正中：校门牌楼，一条粉色主路笔直向北贯穿到中部横路
 *
 * 真实建筑特征（据航拍 + 手绘）：平顶女儿墙 + 米白墙 / 橙红竖向构件，非红瓦坡顶；
 * 校园内大王椰子（棕榈）成列，圆广场为同心圆水池 + 中央景石喷泉。
 *
 * 等轴测投影：screenX = OX + (x - y) * TW，screenY = OY + (x + y) * TH
 * 所以 x+y 越小越靠画面顶部，x-y 越大越靠右。
 *
 * 注意：CAMPUS_BUILDINGS 的楼名集合是课程地点 / 教室管理 / i18n 的主键，重排时只能改坐标，不能增删或改名。
 */

export const GRID_UNIT = 3.0
export const GRID_W = 23
export const GRID_H = 18

/** 南北主路中心（手绘图：校门在南面正中，主路笔直向北） */
export const CAMPUS_MAIN_ROAD_X = 9.4

/** 屋顶样式 */
export type RoofKind = 'gable' | 'hip' | 'flat' | 'arch' | 'round'

/** 墙色语义色板（据航拍实景：米白墙 + 橙红构件） */
export const WALL = '#f7f3ea'   // 米白（教学楼）
export const WALL_B = '#eef4f8' // 冷白（培训中心/玻璃楼）
export const PINK = '#e3a08e'   // 粉墙（半月楼）
export const ORANGE = '#d97b52' // 橙红（体育馆/竖向构件）
export const TERRACOTTA = '#c96a45'

export interface CampusBld {
  /** 正式楼名（课程 location 优先按 name 匹配） */
  name: string
  /** 历史数据里的旧楼名别名（"信息楼-401" → 明志楼） */
  aka?: string[]
  gx: number
  gy: number
  w: number
  d: number
  floors: number
  roof?: RoofKind
  wall?: string
  roofColor?: string
  /** 标签额外位移（网格单位） */
  lx?: number
  ly?: number
  /** 标签放到屋顶上方（密集的教学群用） */
  labelTop?: boolean
  /** 立面窗墙风格（3D 贴图）：ribbon 横向带窗 / classic 双联窗 / curtain 玻璃幕墙 / brick 红砖 */
  wallStyle?: 'ribbon' | 'classic' | 'curtain' | 'brick'
  /** 立面强调色（竖向构件 / 腰线），默认橙红 */
  accent?: string
  /** 特殊楼体造型：fan = 弧形扇面玻璃楼（齐礼楼） */
  shape?: 'fan'
}

/** 教学群单栋：面宽（沿 x，长边）/ 进深（沿 y，短边） */
const TW = 3.0
const TD = 0.66
/** 一行里左右两栋之间的主脊净宽（连廊跨度） */
const SPINE_W = 1.4
/** 通宽楼的面宽 = 左楼 + 主脊 + 右楼 */
const FULL_W = TW * 2 + SPINE_W

/** 教学群原点：手绘图里整片横条集中在北部偏右 */
const T_X0 = 11.8
const T_Y0 = 0.7
/** 行距（含前后连廊的长度） */
const ROW_GAP = 0.16
/** 中央广场进深（广场放在第 4 行与第 5 行之间） */
const PLAZA_D = 0.8

interface RowSpec {
  kind: 'single' | 'pair'
  names: string[]
  floors: number[]
  /** 进深覆盖（通宽楼略深） */
  depth?: number
  aka?: Array<string[] | undefined>
}

const TEACH_ROWS: RowSpec[] = [
  { kind: 'single', names: ['大象楼'], floors: [6], depth: 0.8 },
  { kind: 'single', names: ['大音楼'], floors: [6], depth: 0.8 },
  { kind: 'pair', names: ['大器楼', '恒思楼'], floors: [6, 5], aka: [['工科楼'], ['子思楼', '理学楼', '慎思楼']] },
  { kind: 'pair', names: ['大方楼', '望星楼'], floors: [4, 4], aka: [['天音楼', '教学楼A'], ['至善楼', '文科楼']] },
  // ↓ 广场
  { kind: 'pair', names: ['洪涛楼', '求实楼'], floors: [5, 5], aka: [['演洞楼'], ['人力楼', '教1', '求索楼']] },
  { kind: 'pair', names: ['知新楼', '闻道楼'], floors: [5, 5], aka: [['知返楼', '教学楼B', '知心楼'], ['问道楼', '外语楼']] },
  { kind: 'pair', names: ['温故楼', '学思楼'], floors: [5, 4], aka: [undefined, ['泗思楼']] },
]

type TeachingRow = { spec: RowSpec; gy: number; d: number }

const TEACHING_ROWS: TeachingRow[] = []
{
  let y = T_Y0
  TEACH_ROWS.forEach((spec, i) => {
    const d = spec.depth ?? TD
    TEACHING_ROWS.push({ spec, gy: y, d })
    y += d + ROW_GAP
    if (i === 3) y += PLAZA_D          // 第 4 行之后插入中央广场
  })
}

// 航拍实景：教学群是平顶、米白墙 + 橙红构件；按行给墙面/屋顶轻微冷暖差，避免 13 栋完全同色
const ROOF_TINTS = ['#e2d8c6', '#d3c5ae', '#e6ddcd', '#d9cdb8', '#e2d8c6', '#d3c5ae', '#e6ddcd']
const WALL_TINTS = ['#f7f3ea', '#f2eee5', '#f9f5ec', '#f0ece3', '#f7f3ea', '#f2eee5', '#f9f5ec']

const teaching: CampusBld[] = []
TEACHING_ROWS.forEach(({ spec, gy, d }, i) => {
  const tint = ROOF_TINTS[i % ROOF_TINTS.length]
  const wt = WALL_TINTS[i % WALL_TINTS.length]
  const wt2 = WALL_TINTS[(i + 2) % WALL_TINTS.length]
  if (spec.kind === 'single') {
    teaching.push({
      name: spec.names[0], gx: T_X0, gy, w: FULL_W, d,
      floors: spec.floors[0], wall: wt, roof: 'flat', roofColor: tint,
      wallStyle: 'ribbon', aka: spec.aka?.[0], labelTop: true,
    })
  } else {
    teaching.push(
      { name: spec.names[0], gx: T_X0, gy, w: TW, d, floors: spec.floors[0], wall: wt, roof: 'flat', roofColor: tint, wallStyle: 'ribbon', aka: spec.aka?.[0], labelTop: true },
      { name: spec.names[1], gx: T_X0 + TW + SPINE_W, gy, w: TW, d, floors: spec.floors[1], wall: wt2, roof: 'flat', roofColor: tint, wallStyle: 'ribbon', aka: spec.aka?.[1], labelTop: true },
    )
  }
})

export const CAMPUS_BUILDINGS: CampusBld[] = [
  // ============================================================
  // 西侧：培训中心二期（西北）、俊秀图书馆（西中）、培训中心一期（西南，校门内左侧）
  // ============================================================
  { name: '培训中心二期', gx: 6.4, gy: 0.8, w: 2.9, d: 1.2, floors: 4, roof: 'flat', wall: WALL_B, roofColor: '#d8e2ea', wallStyle: 'curtain', aka: ['培训中心二附', '创新创业基地'] },
  { name: '俊秀图书馆', gx: 3.4, gy: 3.9, w: 2.6, d: 2.6, floors: 3, roof: 'round', wall: WALL_B, roofColor: '#dfeaf3', wallStyle: 'curtain', aka: ['图书馆'] },
  { name: '培训中心一期', gx: 5.4, gy: 16.0, w: 2.9, d: 1.4, floors: 4, roof: 'flat', wall: WALL_B, roofColor: '#d8e2ea', wallStyle: 'curtain', aka: ['工程训练中心'] },

  // ============================================================
  // 北部偏右——教学群 7 行（2 栋通宽单体 + 5 行左右双楼）
  // ============================================================
  ...teaching,

  // ----- 中部东侧：明德厅 / 至善厅 / LeHAS 餐厅 / 半月楼（教学群正南一排） -----
  { name: '明德厅', gx: 14.0, gy: 8.6, w: 1.7, d: 1.05, floors: 2, roof: 'flat', wall: '#f8e7d9', roofColor: '#e0b39c', wallStyle: 'classic', accent: TERRACOTTA, ly: -1.2 },
  { name: '至善厅', gx: 16.0, gy: 8.7, w: 1.7, d: 1.05, floors: 2, roof: 'flat', wall: '#f8e7d9', roofColor: '#e0b39c', wallStyle: 'classic', accent: TERRACOTTA, ly: -1.0 },
  { name: 'LeHAS餐厅', gx: 18.0, gy: 9.4, w: 1.8, d: 1.05, floors: 2, roof: 'flat', wall: '#fbe9d8', roofColor: '#e2b79c', wallStyle: 'curtain', accent: TERRACOTTA, aka: ['食堂'] },
  { name: '半月楼', gx: 20.3, gy: 8.6, w: 1.8, d: 1.5, floors: 5, roof: 'flat', wall: PINK, roofColor: '#dba492', wallStyle: 'classic', accent: TERRACOTTA },

  // ----- 中下：齐礼楼（西侧弧形公共楼）、致远楼、明志楼 -----
  { name: '齐礼楼', gx: 4.6, gy: 9.8, w: 3.2, d: 2.0, floors: 3, roof: 'flat', wall: '#fdf6ea', roofColor: '#e3d3bd', wallStyle: 'curtain', shape: 'fan' },
  { name: '致远楼', gx: 10.0, gy: 11.6, w: 2.0, d: 1.15, floors: 4, roof: 'flat', wall: WALL, roofColor: '#e0b39c', wallStyle: 'ribbon' },
  { name: '明志楼', gx: 14.0, gy: 11.4, w: 2.0, d: 1.15, floors: 4, roof: 'flat', wall: WALL, roofColor: '#e0b39c', wallStyle: 'ribbon', aka: ['信息楼'] },

  // ----- 钟楼：教学群西北端（手绘图里是醒目的红砖尖塔 + 天桥） -----
  { name: '钟楼', gx: 10.7, gy: 0.85, w: 0.95, d: 0.95, floors: 6, roof: 'hip', wall: '#f0d9c8', roofColor: TERRACOTTA, wallStyle: 'brick' },

  // ----- 东南：体育馆紧邻跑道北侧 -----
  { name: '体育馆', gx: 17.2, gy: 11.4, w: 2.8, d: 1.6, floors: 2, roof: 'arch', wall: ORANGE, roofColor: '#e0a181', wallStyle: 'classic', accent: TERRACOTTA },
]

/* ------------------------------------------------------------------
 * 连廊：跨主脊（同一行左右两栋之间）+ 前后（相邻两行之间）
 * ------------------------------------------------------------------ */
export interface CampusLink {
  /** 左楼 / 右楼（跨脊连廊）；前后连廊取上一行楼名 */
  a: string
  b: string
  gx: number
  gy: number
  w: number
  d: number
  /** 连廊层数（1 = 二层，离地一层高） */
  floor: number
  /** cross = 跨主脊；walk = 前后连廊 */
  kind: 'cross' | 'walk'
}

export const CAMPUS_LINKS: CampusLink[] = []
TEACHING_ROWS.forEach(({ spec, gy, d }, i) => {
  if (spec.kind === 'pair') {
    // 跨主脊：x 从右楼东端到左楼西端
    CAMPUS_LINKS.push({
      a: spec.names[0], b: spec.names[1],
      gx: T_X0 + TW, gy: gy + d * 0.12,
      w: SPINE_W, d: d * 0.76, floor: 1, kind: 'cross',
    })
  }
  // 前后连廊：本行底部 → 下一行顶部（广场那一段走通长）
  if (i < TEACHING_ROWS.length - 1) {
    const next = TEACHING_ROWS[i + 1]
    const gap = next.gy - (gy + d)
    const cols = [T_X0 + TW * 0.22, T_X0 + TW + SPINE_W + TW * 0.22]
    for (const cx of cols) {
      CAMPUS_LINKS.push({
        a: spec.names[0], b: next.spec.names[0],
        gx: cx, gy: gy + d,
        w: TW * 0.56, d: gap, floor: 0, kind: 'walk',
      })
    }
  }
})

/** 教学群中央广场（羽毛球场所在） */
export const CAMPUS_TEACH_PLAZA = (() => {
  const y0 = TEACHING_ROWS[3].gy + TEACHING_ROWS[3].d + ROW_GAP
  return { gx: T_X0 - 0.25, gy: y0, w: FULL_W + 0.5, d: PLAZA_D }
})()

/** 羽毛球场（广场正中） */
export const CAMPUS_BADMINTON = (() => {
  const p = CAMPUS_TEACH_PLAZA
  const w = 3.0
  return { gx: p.gx + (p.w - w) / 2, gy: p.gy + 0.1, w, d: p.d - 0.2 }
})()

/** 湖（手绘图：俊秀图书馆东侧、圆凉亭广场西北，带栈桥的不规则水面） */
export const CAMPUS_LAKE = { cx: 7.9, cy: 4.6, rx: 1.55, rz: 1.65 }

/** 圆形喷泉广场（手绘图：校园正中偏东，湖的东南，中央景石小岛） */
export const CAMPUS_PLAZA = { cx: 12.3, cy: 10.0, r: 1.15 }

/** 操场（手绘图：东南角，体育馆南侧，南北长轴的斜向椭圆跑道） */
export const CAMPUS_FIELD = { cx: 16.2, cy: 15.2, rx: 1.25, rz: 2.05 }

/** 球场群（跑道东侧 3 块场地） */
export const CAMPUS_COURTS = [
  { gx: 20.2, gy: 12.5, w: 1.1, d: 1.4 },
  { gx: 20.2, gy: 14.2, w: 1.1, d: 1.4 },
  { gx: 20.2, gy: 15.9, w: 1.1, d: 1.4 },
]

/** 校门（牌楼）——手绘图：南面正中偏左，粉色主路起点 */
export const CAMPUS_GATE = { cx: 9.4, cy: 17.5 }

/**
 * 主路：南中校门笔直向北 → 中部横路；另有沿湖南岸的西北支路、
 * 西侧贴地块边界路、东侧纵路、南部横路与运动区支路。
 */
export const CAMPUS_ROADS: Array<{ x1: number; y1: number; x2: number; y2: number; w?: number }> = [
  { x1: 9.4, y1: 17.4, x2: 9.4, y2: 7.9, w: 1.0 },   // 南中主路（贯穿南北）
  { x1: 3.0, y1: 7.9, x2: 21.5, y2: 7.9, w: 0.85 },  // 中部横路（教学群 / 公共建筑分界）
  { x1: 9.4, y1: 7.1, x2: 6.0, y2: 7.0, w: 0.7 },    // 沿湖南岸西北支路
  { x1: 6.0, y1: 7.0, x2: 2.8, y2: 6.9, w: 0.7 },
  { x1: 2.6, y1: 1.0, x2: 1.8, y2: 6.5, w: 0.75 },   // 西侧贴边界路
  { x1: 1.8, y1: 6.5, x2: 2.2, y2: 15.7, w: 0.75 },
  { x1: 2.4, y1: 15.7, x2: 13.5, y2: 15.7, w: 0.8 }, // 南部横路（宿舍 / 校门分界）
  { x1: 9.4, y1: 13.2, x2: 13.8, y2: 13.2, w: 0.8 }, // 运动区支路
  { x1: 13.8, y1: 13.2, x2: 13.8, y2: 16.6, w: 0.75 },
  { x1: 22.4, y1: 4.0, x2: 22.4, y2: 16.8, w: 0.7 }, // 东侧纵路
]

/** 大王椰子（棕榈）成列位置——航拍里教学群、湖岸与广场周边最常见的树种 */
export const CAMPUS_PALMS: Array<[number, number, number]> = [
  [6.3, 3.0, 0.5], [6.3, 5.2, 0.48], [9.9, 3.4, 0.46], [9.9, 5.8, 0.46],
  [11.3, 7.5, 0.46], [13.4, 7.5, 0.46], [15.5, 7.5, 0.46], [17.6, 7.5, 0.46], [19.6, 7.5, 0.46],
  [10.6, 9.1, 0.46], [14.2, 9.2, 0.46],
  [13.4, 12.0, 0.46], [15.0, 12.0, 0.46], [19.6, 13.8, 0.46], [19.6, 16.2, 0.46],
  [3.6, 8.6, 0.46], [3.6, 12.2, 0.46],
]

/**
 * 西南斜向生活组团（纯景观体块，不进 CAMPUS_BUILDINGS，不影响课程/教室数据）。
 * 手绘图：红色宿舍楼群在左下、校门西北侧，两排平行错列。
 */
export const CAMPUS_DORM_BLOCKS: Array<{ gx: number; gy: number; w: number; d: number; floors: number; angle: number }> = [
  { gx: 2.8, gy: 12.9, w: 1.5, d: 0.8, floors: 5, angle: -0.32 },
  { gx: 4.7, gy: 12.9, w: 1.5, d: 0.8, floors: 5, angle: -0.32 },
  { gx: 6.4, gy: 12.9, w: 1.5, d: 0.8, floors: 5, angle: -0.32 },
  { gx: 3.2, gy: 14.3, w: 1.5, d: 0.8, floors: 4, angle: -0.32 },
  { gx: 5.0, gy: 14.3, w: 1.5, d: 0.8, floors: 4, angle: -0.32 },
  { gx: 6.6, gy: 14.3, w: 1.5, d: 0.8, floors: 4, angle: -0.32 },
]

/** 树（网格坐标 + 树冠半径）：沿地块边界与空地，避开楼体和路面 */
export const CAMPUS_TREES: Array<[number, number, number]> = [
  [0.4, 1.5, 0.5], [0.4, 4.2, 0.46], [0.4, 7.2, 0.48], [0.4, 10.2, 0.44],
  [0.4, 13.2, 0.46], [0.4, 16.4, 0.48],
  [3.0, 1.4, 0.44], [6.0, 2.6, 0.42], [10.4, 2.2, 0.44], [20.6, 1.2, 0.46],
  [22.7, 2.6, 0.46], [22.7, 6.0, 0.44], [22.7, 11.0, 0.44], [22.7, 14.0, 0.46], [22.7, 17.2, 0.46],
  [2.9, 8.6, 0.42], [2.9, 11.6, 0.42], [8.4, 10.6, 0.4], [12.7, 13.6, 0.42],
  [19.0, 17.2, 0.46], [4.6, 17.3, 0.42], [11.2, 17.3, 0.42], [13.0, 17.3, 0.42],
]

/* ===== 联动：课程上课地点 → 规范楼名 ===== */

const norm = (s: string) => String(s || '').replace(/[\s\-_·]/g, '').toLowerCase()

const ALT = new Map<string, string>()
for (const b of CAMPUS_BUILDINGS) {
  ALT.set(norm(b.name), b.name)
  for (const a of b.aka || []) ALT.set(norm(a), b.name)
}

/** 教室字符串 → 规范楼名（"信息楼-401" / "明志楼-401" → "明志楼"） */
export function buildingOf(classroom: string | undefined): string {
  const raw = String(classroom || '').split('-')[0].trim()
  if (!raw) return ''
  const hit = ALT.get(norm(raw))
  if (hit) return hit
  const k = [...ALT.keys()].find((a) => norm(raw).startsWith(a))
  return k ? ALT.get(k)! : raw
}

/** 按楼聚合课表：{ 规范楼名: { count, today, courses } } */
export function groupByBuilding(items: any[], todayDow: number) {
  const m: Record<string, { count: number; today: number; courses: any[] }> = {}
  for (const it of items || []) {
    const b = buildingOf(it.classroom)
    if (!b) continue
    m[b] = m[b] || { count: 0, today: 0, courses: [] }
    m[b].count++
    if (it.dayOfWeek === todayDow) m[b].today++
    m[b].courses.push(it)
  }
  return m
}

/** 世界坐标下的楼高（层高 1.05 个世界单位） */
export const bldHeight = (floors: number) => floors * 1.05
