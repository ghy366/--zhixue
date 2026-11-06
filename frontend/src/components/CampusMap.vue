<template>
  <div class="campus-map">
    <div class="cm-head">
      <span class="cm-sub">厦门华厦学院 · 发光的楼 = 本学期你有课 · 数字 = 每周课时 · 点击楼查看课程与教室</span>
      <div class="cm-legend">
        <span><i class="lg has" />有课</span>
        <span><i class="lg today" />今天有课</span>
        <span><i class="lg none" />无课</span>
      </div>
    </div>

    <div class="cm-stage">
      <svg :viewBox="VIEW" role="img" aria-label="校园等轴测沙盘地图">
        <defs>
          <radialGradient id="cmGrass" cx="46%" cy="40%" r="72%">
            <stop offset="0%" stop-color="#c4e29b" />
            <stop offset="100%" stop-color="#a6ce79" />
          </radialGradient>
          <linearGradient id="cmWater" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stop-color="#b6e0ef" />
            <stop offset="100%" stop-color="#8cc6dd" />
          </linearGradient>
          <filter id="cmSoft" x="-20%" y="-20%" width="140%" height="140%">
            <feDropShadow dx="0" dy="6" stdDeviation="7" flood-color="#5b6b3d" flood-opacity=".22" />
          </filter>
        </defs>

        <!-- 沙盘底座 -->
        <polygon :points="plinthPts" fill="#e6dcc6" opacity=".9" />
        <!-- 草地 -->
        <polygon :points="groundPts" fill="url(#cmGrass)" filter="url(#cmSoft)" />

        <!-- 道路 -->
        <line
          v-for="(r, i) in roads" :key="'rd' + i"
          :x1="r.x1" :y1="r.y1" :x2="r.x2" :y2="r.y2"
          stroke="#e3b4a6" :stroke-width="r.w" stroke-linecap="round"
        />

        <!-- 教学群中央广场 + 羽毛球场 -->
        <polygon :points="teachPlaza" fill="#f0e7d5" stroke="#e0d3b8" stroke-width="1.2" />
        <g>
          <polygon :points="badminton.pts" fill="#a9cf88" stroke="#84a866" stroke-width="1.1" />
          <line
            v-for="(l, i) in badminton.lines" :key="'bm' + i"
            :x1="l[0]" :y1="l[1]" :x2="l[2]" :y2="l[3]"
            stroke="#f4f9ee" stroke-width="1"
          />
        </g>

        <!-- 湖 -->
        <g>
          <ellipse
            :cx="lake.cx" :cy="lake.cy" :rx="lake.rx * 1.16" :ry="lake.ry * 1.16"
            :transform="`rotate(-29.5 ${lake.cx} ${lake.cy})`" fill="#dbe8c4"
          />
          <ellipse
            :cx="lake.cx" :cy="lake.cy" :rx="lake.rx" :ry="lake.ry"
            :transform="`rotate(-29.5 ${lake.cx} ${lake.cy})`" fill="url(#cmWater)"
          />
        </g>

        <!-- 中央广场 + 喷泉 -->
        <g>
          <ellipse :cx="plaza.cx" :cy="plaza.cy" :rx="plaza.rx" :ry="plaza.ry" :transform="`rotate(-29.5 ${plaza.cx} ${plaza.cy})`" fill="#f0e6d2" stroke="#e0d3b8" stroke-width="1.5" />
          <ellipse :cx="plaza.cx" :cy="plaza.cy" :rx="plaza.rx * 0.4" :ry="plaza.ry * 0.4" :transform="`rotate(-29.5 ${plaza.cx} ${plaza.cy})`" fill="#b9e2f2" stroke="#f6efe0" stroke-width="3" />
        </g>

        <!-- 操场 -->
        <g>
          <ellipse :cx="field.cx" :cy="field.cy" :rx="field.rx" :ry="field.ry" :transform="`rotate(-29.5 ${field.cx} ${field.cy})`" fill="#d9705e" />
          <ellipse :cx="field.cx" :cy="field.cy" :rx="field.rx * 0.72" :ry="field.ry * 0.68" :transform="`rotate(-29.5 ${field.cx} ${field.cy})`" fill="#9dcb6b" stroke="#f2f6ea" stroke-width="2" />
        </g>

        <!-- 篮球场 -->
        <polygon v-for="(c, i) in courts" :key="'ct' + i" :points="c.pts" fill="#cf6152" stroke="#f4efe2" stroke-width="1.4" />

        <!-- 树与楼（按纵深排序） -->
        <g
          v-for="(e, i) in drawList" :key="i"
          :class="{ 'cm-has': e.kind === 'b' && e.has, 'cm-today': e.kind === 'b' && e.today }"
          :opacity="e.kind === 'b' && dimmed(e) ? 0.45 : 1"
          @click="e.kind === 'b' && pick(e.name)"
        >
          <!-- 树 -->
          <template v-if="e.kind === 'tree'">
            <ellipse :cx="e.x" :cy="e.y + 2" :rx="e.r + 3" :ry="(e.r + 3) / 2" fill="#00000014" />
            <circle :cx="e.x" :cy="e.y - e.r * 0.7" :r="e.r" fill="#4f8a44" />
            <circle :cx="e.x - e.r * 0.3" :cy="e.y - e.r * 1" :r="e.r * 0.58" fill="#67a355" />
          </template>

          <!-- 南部斜向生活组团（景观体块，与 3D 沙盘同一组坐标） -->
          <template v-else-if="e.kind === 'dorm'">
            <polygon :points="e.west" fill="#e4d9c6" stroke="#cdbda6" stroke-width="0.8" />
            <polygon :points="e.south" fill="#d6c8b1" stroke="#cdbda6" stroke-width="0.8" />
            <polygon :points="e.top" fill="#e9dfcb" stroke="#c8b79a" stroke-width="1" />
            <line v-for="(s, si) in e.seams" :key="'ds' + si" :x1="s[0]" :y1="s[1]" :x2="s[2]" :y2="s[3]" stroke="#bda98d" stroke-width="0.8" opacity=".75" />
          </template>

          <!-- 双楼连廊：一层实墙 + 二层玻璃廊 -->
          <template v-else-if="e.kind === 'link'">
            <polygon :points="e.westWall" fill="#e9e0cd" stroke="#d6c9b0" stroke-width="0.7" />
            <polygon :points="e.southWall" fill="#ddd2bb" stroke="#d6c9b0" stroke-width="0.7" />
            <polygon :points="e.westGlass" fill="#b9d6e9" stroke="#7ea3bd" stroke-width="1" />
            <polygon :points="e.southGlass" fill="#a2c7df" stroke="#7ea3bd" stroke-width="1" />
            <polygon :points="e.topFace" fill="#f1f3f2" stroke="#9fb6c4" stroke-width="1" opacity=".97" />
          </template>

          <!-- 楼 -->
          <template v-else>
            <ellipse :cx="e.cx" :cy="e.baseY - 3" :rx="e.shadowRx" :ry="e.shadowRx / 2" class="cm-shadow" :fill="e.has ? 'rgba(43,177,135,.28)' : '#00000012'" />

            <!-- 圆柱楼（俊秀图书馆） -->
            <template v-if="e.kindOf === 'round'">
              <path :d="e.roundBody" :fill="e.wallShade" stroke="#d9c9ab" stroke-width="1" />
              <ellipse :cx="e.cx" :cy="e.topY" :rx="e.roundRx" :ry="e.roundRy" :fill="e.roofColor" stroke="#cdbca0" stroke-width="1" />
              <line v-for="(seam, si) in e.roundSeams" :key="'s' + si" :x1="seam[0]" :y1="seam[1]" :x2="seam[2]" :y2="seam[3]" stroke="#cbb9a0" stroke-width="1" opacity=".7" />
            </template>

            <!-- 弧形扇面玻璃楼（齐礼楼） -->
            <template v-else-if="e.kindOf === 'fan'">
              <path :d="e.fanSide" fill="#9fc6de" stroke="#7ea3bd" stroke-width="1" />
              <line v-for="(m, mi) in e.fanMullions" :key="'fm' + mi" :x1="m[0]" :y1="m[1]" :x2="m[2]" :y2="m[3]" stroke="#f5efe2" stroke-width="1.2" opacity=".9" />
              <path :d="e.fanFace" :fill="e.roofColor" stroke="#c8b79a" stroke-width="1" />
            </template>

            <!-- 方楼 -->
            <template v-else>
              <polygon :points="e.topPts" :fill="e.wall" stroke="#d9c9ab" stroke-width="1" />
              <polygon :points="e.leftPts" :fill="e.wallShade" />
              <polygon :points="e.rightPts" :fill="e.wallDark" />
              <!-- 窗 -->
              <path v-for="(w, wi) in e.windows" :key="'w' + wi" :d="w.points" :fill="w.fill" opacity=".82" />
              <!-- 门 -->
              <path v-if="e.door" :d="e.door" fill="#8a6f4d" />
              <!-- 屋顶 -->
              <template v-if="e.roofKind === 'gable'">
                <polygon :points="e.roofA" fill="#d3815f" stroke="#bf6b4d" stroke-width="1" />
                <polygon :points="e.roofB" fill="#b95f43" stroke="#a8543b" stroke-width="1" />
                <polygon :points="e.roofGableA" :fill="e.roofColor" stroke="#c06a4d" stroke-width="1" />
                <polygon :points="e.roofGableB" :fill="e.roofColor" stroke="#c06a4d" stroke-width="1" />
              </template>
              <template v-else-if="e.roofKind === 'hip'">
                <polygon :points="e.roofA" :fill="e.roofColor" stroke="#bf6b4d" stroke-width="1" />
                <polygon :points="e.roofB" fill="#b95f43" stroke="#a8543b" stroke-width="1" />
              </template>
              <template v-else-if="e.roofKind === 'flat'">
                <polygon :points="e.flatRoof" :fill="e.roofColor" stroke="#c8b79a" stroke-width="1" />
              </template>
              <template v-else-if="e.roofKind === 'arch'">
                <path :d="e.archRoof" :fill="e.roofColor" stroke="#bd7a5c" stroke-width="1" />
              </template>
            </template>
          </template>
        </g>

        <!-- 校门 -->
        <g>
          <rect :x="gate.x - 24" :y="gate.y - 30" width="6" height="30" rx="2" fill="#fdf8ef" stroke="#d8c9ab" />
          <rect :x="gate.x + 18" :y="gate.y - 30" width="6" height="30" rx="2" fill="#fdf8ef" stroke="#d8c9ab" />
          <rect :x="gate.x - 28" :y="gate.y - 40" width="56" height="13" rx="3" fill="#fdf8ef" stroke="#d8c9ab" />
          <text :x="gate.x" :y="gate.y - 30.5" text-anchor="middle" class="cm-gate-text">厦门华厦学院</text>
        </g>

        <!-- 楼名与有课徽章：独立顶层绘制，避免被前排楼体遮挡 -->
        <g class="cm-labels" pointer-events="none">
          <g v-for="e in labels" :key="'lb-' + e.name" :opacity="e.dim ? 0.4 : 1">
            <g v-if="e.has" class="cm-badge">
              <rect :x="e.bx - 31" :y="e.by - 26" width="62" height="19" rx="9.5" :fill="e.today ? '#f59e0b' : '#2bb187'" opacity="0.95" />
              <text :x="e.bx" :y="e.by - 13" text-anchor="middle" class="cm-badge-text">
                {{ e.count }} 节/周<template v-if="e.today"> · 今天</template>
              </text>
            </g>
            <text :x="e.lx" :y="e.ly" text-anchor="middle" class="cm-name">{{ e.name }}</text>
          </g>
        </g>
      </svg>

      <!-- 点击楼 → 课程卡 -->
      <div v-if="selected" class="cm-detail">
        <div class="cm-detail-head">
          <b>📍 {{ selected }}</b>
          <button class="cm-close" @click="selected = null">✕</button>
        </div>
        <div class="cm-detail-list">
          <div v-for="(c, i) in selectedCourses" :key="i" class="cm-course">
            <b>{{ c.name }}</b>
            <span>{{ c.teacher || '—' }} · {{ c.classroom }} · 周{{ '日一二三四五六'[c.dayOfWeek] }} {{ periodText(c.period) }}</span>
          </div>
          <div v-if="!selectedCourses.length" class="cm-nodata">这栋楼这学期没有你的课</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  CAMPUS_BUILDINGS, CAMPUS_COURTS, CAMPUS_FIELD, CAMPUS_GATE, CAMPUS_LAKE,
  CAMPUS_BADMINTON, CAMPUS_LINKS, CAMPUS_PLAZA, CAMPUS_ROADS, CAMPUS_TEACH_PLAZA, CAMPUS_TREES,
  CAMPUS_DORM_BLOCKS, GRID_H, GRID_W, groupByBuilding,
} from '@/utils/campusConfig'

const props = defineProps<{ items: any[] }>()

/* ===== 等轴测投影 ===== */
const TW = 19
const TH = 17
const OX = 350
const OY = 100
const VIEW = '-8 74 824 752'
const FLOOR_PX = 7.2 // 一层楼在 SVG 里的像素高度
function iso(x: number, y: number): [number, number] {
  return [OX + (x - y) * TW, OY + (x + y) * TH]
}

/* ===== 课表 → 楼宇使用统计 ===== */
const todayDow = new Date().getDay() || 7
const usage = computed(() => groupByBuilding(props.items, todayDow))

const selected = ref<string | null>(null)
const selectedCourses = computed(() => (selected.value ? usage.value[selected.value]?.courses || [] : []))
function pick(name: string) {
  selected.value = selected.value === name ? null : name
}
/** 供课表联动：直接选中某栋楼（不切换取消） */
function focusBuilding(name: string) {
  selected.value = name
}
defineExpose({ focusBuilding })
function periodText(p: string) {
  return ({ '1-2': '一二节', '3-4': '三四节', '5-6': '五六节', '7-8': '七八节', '9-10': '九十节' } as Record<string, string>)[p] || p
}
function dimmed(e: any) {
  return selected.value != null && e.name !== selected.value
}

/* ===== 地面 / 湖 / 广场 / 操场 ===== */
const groundPts = computed(() =>
  [iso(0, 0), iso(GRID_W, 0), iso(GRID_W, GRID_H), iso(0, GRID_H)].map((p) => `${p[0]},${p[1]}`).join(' '),
)
const plinthPts = computed(() =>
  [iso(-0.5, -0.5), iso(GRID_W + 0.5, -0.5), iso(GRID_W + 0.5, GRID_H + 0.5), iso(-0.5, GRID_H + 0.5)]
    .map((p) => `${p[0]},${p[1]}`).join(' '),
)
const lake = computed(() => {
  const [cx, cy] = iso(CAMPUS_LAKE.cx, CAMPUS_LAKE.cy)
  return { cx, cy, rx: CAMPUS_LAKE.rx * TW * 0.86, ry: CAMPUS_LAKE.rz * TH * 2.2 }
})
const plaza = computed(() => {
  const [cx, cy] = iso(CAMPUS_PLAZA.cx, CAMPUS_PLAZA.cy)
  return { cx, cy, rx: CAMPUS_PLAZA.r * TW * 0.86, ry: CAMPUS_PLAZA.r * TH * 2.2 }
})
const field = computed(() => {
  const [cx, cy] = iso(CAMPUS_FIELD.cx, CAMPUS_FIELD.cy)
  return { cx, cy, rx: CAMPUS_FIELD.rx * TW * 0.86, ry: CAMPUS_FIELD.rz * TH * 2.2 }
})
const roads = computed(() =>
  CAMPUS_ROADS.map((r) => {
    const [x1, y1] = iso(r.x1, r.y1)
    const [x2, y2] = iso(r.x2, r.y2)
    return { x1, y1, x2, y2, w: (r.w || 1) * TW * 0.72 }
  }),
)
const courts = computed(() =>
  CAMPUS_COURTS.map((c) => ({
    pts: [iso(c.gx, c.gy), iso(c.gx + c.w, c.gy), iso(c.gx + c.w, c.gy + c.d), iso(c.gx, c.gy + c.d)]
      .map((p) => `${p[0]},${p[1]}`).join(' '),
  })),
)
const teachPlaza = computed(() => {
  const p = CAMPUS_TEACH_PLAZA
  return [iso(p.gx, p.gy), iso(p.gx + p.w, p.gy), iso(p.gx + p.w, p.gy + p.d), iso(p.gx, p.gy + p.d)]
    .map((q) => `${q[0].toFixed(1)},${q[1].toFixed(1)}`)
    .join(' ')
})
const badminton = computed(() => {
  const b = CAMPUS_BADMINTON
  const at = (u: number, v: number) => iso(b.gx + b.w * u, b.gy + b.d * v)
  const line = (u1: number, v1: number, u2: number, v2: number) => {
    const a = at(u1, v1)
    const c = at(u2, v2)
    return [a[0], a[1], c[0], c[1]]
  }
  return {
    pts: [at(0, 0), at(1, 0), at(1, 1), at(0, 1)].map((q) => `${q[0].toFixed(1)},${q[1].toFixed(1)}`).join(' '),
    lines: [
      line(0.04, 0.5, 0.96, 0.5),     // 中线
      line(0.04, 0.16, 0.96, 0.16),   // 前发球线
      line(0.04, 0.84, 0.96, 0.84),   // 后发球线
      line(0.5, 0.04, 0.5, 0.96),     // 单打边线（长向）
    ],
  }
})
const gate = computed(() => {
  const [x, y] = iso(CAMPUS_GATE.cx, CAMPUS_GATE.cy)
  return { x, y }
})

/* ===== 面片工具 ===== */
function faceWin(c1: [number, number], c2: [number, number], h: number, u: number, v: number, du: number, dv: number): string {
  const px = (uu: number, vv: number) => `${(c1[0] + (c2[0] - c1[0]) * uu).toFixed(1)},${(c1[1] + (c2[1] - c1[1]) * uu - h * vv).toFixed(1)}`
  return `M${px(u, v)} L${px(u + du, v)} L${px(u + du, v + dv)} L${px(u, v + dv)} Z`
}

/* ===== 绘制列表（楼 + 树，按纵深排序）===== */
const drawList = computed(() => {
  const list: any[] = []
  const used = usage.value

  for (const b of CAMPUS_BUILDINGS) {
    const p1 = iso(b.gx, b.gy)
    const p2 = iso(b.gx + b.w, b.gy)
    const p3 = iso(b.gx + b.w, b.gy + b.d)
    const p4 = iso(b.gx, b.gy + b.d)
    const h = b.floors * FLOOR_PX
    const wall = b.wall || '#fbf5ea'
    const roofColor = b.roofColor || '#c4694f'
    const roofKind = b.roof || 'gable'

    const top = `${p1[0]},${p1[1] - h} ${p2[0]},${p2[1] - h} ${p3[0]},${p3[1] - h} ${p4[0]},${p4[1] - h}`
    const left = `${p1[0]},${p1[1] - h} ${p4[0]},${p4[1] - h} ${p4[0]},${p4[1]} ${p1[0]},${p1[1]}`
    const right = `${p4[0]},${p4[1] - h} ${p3[0]},${p3[1] - h} ${p3[0]},${p3[1]} ${p4[0]},${p4[1]}`

    // 窗（每层两列 + 对面一列）
    const windows: Array<{ points: string; fill: string }> = []
    const floors = Math.max(1, b.floors)
    for (let f = 0; f < floors; f++) {
      const v0 = (f + 0.2) / floors
      const dv = 0.5 / floors
      windows.push({ points: faceWin(p1, p4, h, 0.14, v0, 0.26, dv), fill: '#7fa8c4' })
      windows.push({ points: faceWin(p1, p4, h, 0.56, v0, 0.26, dv), fill: '#7fa8c4' })
      windows.push({ points: faceWin(p4, p3, h, 0.12, v0, 0.2, dv), fill: '#6d95b2' })
      windows.push({ points: faceWin(p4, p3, h, 0.4, v0, 0.2, dv), fill: '#6d95b2' })
      windows.push({ points: faceWin(p4, p3, h, 0.68, v0, 0.2, dv), fill: '#6d95b2' })
    }
    const door = faceWin(p4, p3, h, 0.58, 0, 0.2, 0.44)

    // 弧形扇面楼（齐礼楼）：等轴测下用折线近似外弧 / 内弧
    let fanSide = '', fanFace = ''
    const fanMullions: Array<[number, number, number, number]> = []
    if (b.shape === 'fan') {
      const fcx = b.gx + b.w / 2, fcy = b.gy + b.d / 2
      const R = Math.min(b.w, b.d) * 0.72
      const r = R * 0.55
      const a0 = Math.PI * 0.1, a1 = Math.PI * 0.9
      const outTop: string[] = [], inTop: string[] = [], outBot: string[] = [], inBot: string[] = []
      for (let k = 0; k <= 12; k++) {
        const a = a0 + ((a1 - a0) * k) / 12
        const [ox, oy] = iso(fcx + Math.cos(a) * R * 1.3, fcy + Math.sin(a) * R)
        const [ix, iy] = iso(fcx + Math.cos(a) * r * 1.3, fcy + Math.sin(a) * r)
        outTop.push(`${ox.toFixed(1)},${(oy - h).toFixed(1)}`)
        inTop.push(`${ix.toFixed(1)},${(iy - h).toFixed(1)}`)
        outBot.push(`${ox.toFixed(1)},${oy.toFixed(1)}`)
        inBot.push(`${ix.toFixed(1)},${iy.toFixed(1)}`)
        if (k % 2 === 0 && k > 0 && k < 12) fanMullions.push([ox, oy - h, ox, oy])
      }
      fanFace = `M${outTop.join(' L')} L${inTop.slice().reverse().join(' L')} Z`
      // 外弧 + 内弧两道玻璃立面，中间留出扇面屋顶
      fanSide = `M${outTop.join(' L')} L${outBot.slice().reverse().join(' L')} Z`
        + ` M${inTop.join(' L')} L${inBot.slice().reverse().join(' L')} Z`
    }

    const u = used[b.name]
    const has = !!u?.count
    const cx = (p1[0] + p3[0]) / 2
    const baseY = p3[1] + 3
    const roofBaseY = (p1[1] + p3[1]) / 2 - h

    // 屋顶几何
    const o = b.labelTop ? 1.5 : 2.6 // 出檐（教学群收窄，避免压住后排）
    const e1: [number, number] = [p1[0], p1[1] - h - o]
    const e2: [number, number] = [p2[0] + o, p2[1] - h - o * 0.5]
    const e3: [number, number] = [p3[0], p3[1] - h + o]
    const e4: [number, number] = [p4[0] - o, p4[1] - h - o * 0.5]
    const ry = Math.min(b.w, b.d) * 6 + 4
    const m1: [number, number] = [iso(b.gx, b.gy + b.d / 2)[0], iso(b.gx, b.gy + b.d / 2)[1] - h - o - ry]
    const m2: [number, number] = [iso(b.gx + b.w, b.gy + b.d / 2)[0], iso(b.gx + b.w, b.gy + b.d / 2)[1] - h - o - ry]
    const ridge: [number, number] = [(e1[0] + e2[0] + e3[0] + e4[0]) / 4, (e1[1] + e2[1] + e3[1] + e4[1]) / 4 - ry]

    const pt = (p: [number, number]) => `${p[0].toFixed(1)},${p[1].toFixed(1)}`
    const roofA = `${pt(e1)} ${pt(e2)} ${pt(m2)} ${pt(m1)}`
    const roofB = `${pt(e4)} ${pt(e3)} ${pt(m2)} ${pt(m1)}`
    const roofGableA = `${pt(e1)} ${pt(m1)} ${pt(e4)}`
    const roofGableB = `${pt(e2)} ${pt(e3)} ${pt(m2)}`
    const hipA = `${pt(e1)} ${pt(e2)} ${pt(e3)} ${pt(ridge)}`
    const hipB = `${pt(e1)} ${pt(e3)} ${pt(e4)} ${pt(ridge)}`
    const flatRoof = `${pt(e1)} ${pt(e2)} ${pt(e3)} ${pt(e4)}`

    // 拱形屋顶：沿 w 方向的半拱
    const archH = Math.min(b.w, b.d) * 6 + 6
    const archRoof = `M${e1[0]},${e1[1]} Q${(e1[0] + e3[0]) / 2},${(e1[1] + e3[1]) / 2 - archH * 2} ${e3[0]},${e3[1]} L${e4[0]},${e4[1]} Q${(e2[0] + e4[0]) / 2},${(e2[1] + e4[1]) / 2 - archH * 2} ${e2[0]},${e2[1]} Z`

    // 圆柱楼（图书馆）
    const rrx = (b.w * TW) / 2
    const rry = (b.w * TW) / 4 + 4
    const roundBody = `M${cx - rrx},${baseY} L${cx - rrx},${baseY - h} A${rrx},${rry} 0 0 0 ${cx + rrx},${baseY - h} L${cx + rrx},${baseY} A${rrx},${rry} 0 0 1 ${cx - rrx},${baseY} Z`
    const roundSeams: Array<[number, number, number, number]> = []
    for (let k = 1; k <= 2; k++) {
      const yy = baseY - (h * k) / 3
      roundSeams.push([cx - rrx * 0.94, yy, cx + rrx * 0.94, yy])
    }

    list.push({
      kind: 'b', kindOf: b.shape === 'fan' ? 'fan' : roofKind, name: b.name, sort: b.gx + b.gy + b.w + b.d,
      wall, wallShade: shade(wall, -8), wallDark: shade(wall, -16),
      topPts: top, leftPts: left, rightPts: right,
      windows, door, cx, baseY,
      fanSide, fanFace, fanMullions,
      shadowRx: Math.max(b.w, b.d) * TW * 0.86,
      has, today: !!u?.today, count: u?.count || 0,
      roofKind, roofColor,
      roofA, roofB, roofGableA, roofGableB, hipA, hipB, flatRoof, archRoof,
      roundBody, roundRx: rrx, roundRy: rry, roundSeams, topY: baseY - h,
      bx: cx, by: roofBaseY - 12,
      lx: cx + (b.lx || 0) * TW, ly: (b.labelTop ? roofBaseY - ry - 9 : baseY + 13) + (b.ly || 0) * TH,
    })
    // hip 用 hipA/hipB 覆盖 roofA/roofB
    if (roofKind === 'hip') {
      list[list.length - 1].roofA = hipA
      list[list.length - 1].roofB = hipB
    }
  }

  // 双楼连廊：教学群每组两栋之间的两层连廊（一层实墙 + 二层玻璃）
  const linkBase = 1.05 * FLOOR_PX + 1   // 连廊二层楼面
  const linkTop = linkBase + 9.5         // 连廊顶板
  for (const lk of CAMPUS_LINKS) {
    const q1 = iso(lk.gx, lk.gy)
    const q2 = iso(lk.gx + lk.w, lk.gy)
    const q3 = iso(lk.gx + lk.w, lk.gy + lk.d)
    const q4 = iso(lk.gx, lk.gy + lk.d)
    const up = (p: [number, number], hh: number) => `${p[0].toFixed(1)},${(p[1] - hh).toFixed(1)}`

    list.push({
      kind: 'link', sort: lk.gx + lk.gy + lk.w + lk.d + 0.1,
      // 二层玻璃廊
      westGlass: `${up(q1, linkTop)} ${up(q4, linkTop)} ${up(q4, linkBase)} ${up(q1, linkBase)}`,
      southGlass: `${up(q4, linkTop)} ${up(q3, linkTop)} ${up(q3, linkBase)} ${up(q4, linkBase)}`,
      topFace: `${up(q1, linkTop)} ${up(q2, linkTop)} ${up(q3, linkTop)} ${up(q4, linkTop)}`,
      // 一层实墙
      westWall: `${up(q1, linkBase)} ${up(q4, linkBase)} ${q4[0].toFixed(1)},${q4[1].toFixed(1)} ${q1[0].toFixed(1)},${q1[1].toFixed(1)}`,
      southWall: `${up(q4, linkBase)} ${up(q3, linkBase)} ${q3[0].toFixed(1)},${q3[1].toFixed(1)} ${q4[0].toFixed(1)},${q4[1].toFixed(1)}`,
    })
  }

  // 西南斜向生活组团：先在网格里按 angle 旋转四角，再做等轴测投影
  for (const d of CAMPUS_DORM_BLOCKS) {
    const cxg = d.gx + d.w / 2, cyg = d.gy + d.d / 2
    const co = Math.cos(d.angle), si = Math.sin(d.angle)
    const corner = (ux: number, uy: number) => iso(cxg + ux * co - uy * si, cyg + ux * si + uy * co)
    const q = [
      corner(-d.w / 2, -d.d / 2), corner(d.w / 2, -d.d / 2),
      corner(d.w / 2, d.d / 2), corner(-d.w / 2, d.d / 2),
    ]
    const h = d.floors * FLOOR_PX
    const pt = (p2: [number, number], hh: number) => `${p2[0].toFixed(1)},${(p2[1] - hh).toFixed(1)}`
    const seams: Array<[number, number, number, number]> = []
    for (let k = 1; k < d.floors; k++) {
      const yy = (h * k) / d.floors
      seams.push([q[0][0], q[0][1] - yy, q[3][0], q[3][1] - yy])
      seams.push([q[3][0], q[3][1] - yy, q[2][0], q[2][1] - yy])
    }
    list.push({
      kind: 'dorm', sort: cxg + cyg,
      top: q.map((p2) => pt(p2, h)).join(' '),
      west: `${pt(q[0], h)} ${pt(q[3], h)} ${pt(q[3], 0)} ${pt(q[0], 0)}`,
      south: `${pt(q[3], h)} ${pt(q[2], h)} ${pt(q[2], 0)} ${pt(q[3], 0)}`,
      seams,
    })
  }

  // 树
  for (const [tx, ty, tr] of CAMPUS_TREES) {
    const [x, y] = iso(tx, ty)
    list.push({ kind: 'tree', sort: tx + ty, x, y, r: Math.max(5, tr * TW * 1.15) })
  }

  return list.sort((a, b) => a.sort - b.sort)
})

/* 楼名 / 徽章单独一层：所有楼体画完再画，才不会被前排楼遮挡 */
const labels = computed(() =>
  drawList.value
    .filter((e) => e.kind === 'b')
    .map((e) => ({
      name: e.name, bx: e.bx, by: e.by, lx: e.lx, ly: e.ly,
      has: e.has, today: e.today, count: e.count,
      dim: selected.value != null && e.name !== selected.value,
    })),
)

/** 十六进制颜色加深/提亮 */
function shade(hex: string, delta: number) {
  const h = hex.replace('#', '')
  const n = parseInt(h.length === 3 ? h.split('').map((c) => c + c).join('') : h, 16)
  const r = Math.min(255, Math.max(0, ((n >> 16) & 255) + delta))
  const g = Math.min(255, Math.max(0, ((n >> 8) & 255) + delta))
  const b = Math.min(255, Math.max(0, (n & 255) + delta))
  return `#${((r << 16) | (g << 8) | b).toString(16).padStart(6, '0')}`
}
</script>

<style scoped>
.campus-map { background: var(--card); border: 1px solid var(--border); border-radius: 13px; padding: 14px 16px; }
.cm-head { display: flex; justify-content: space-between; align-items: center; gap: 10px; flex-wrap: wrap; margin-bottom: 6px; }
.cm-sub { font-size: 12px; color: var(--text-light); }
.cm-legend { display: flex; gap: 12px; font-size: 12px; color: var(--text-light); }
.cm-legend i { display: inline-block; width: 10px; height: 10px; border-radius: 50%; margin-right: 5px; vertical-align: middle; }
.cm-legend .lg.has { background: #2bb187; }
.cm-legend .lg.today { background: #f59e0b; }
.cm-legend .lg.none { background: #d8c8a8; }

.cm-stage { position: relative; }
.campus-map svg { width: 100%; height: auto; display: block; }

.cm-shadow { animation: cmGlowPulse 2.4s ease-in-out infinite; }
@keyframes cmGlowPulse { 0%, 100% { opacity: .35; } 50% { opacity: .8; } }

.campus-map .cm-badge rect { transition: y .2s; }
.campus-map .cm-badge text { fill: #fff; font-size: 12px; font-weight: 700; }
.campus-map .cm-name { fill: #43402f; font-size: 12px; font-weight: 800; paint-order: stroke; stroke: rgba(255,255,255,.92); stroke-width: 3.2px; }
.campus-map .cm-gate-text { fill: #8c2f2f; font-size: 12px; font-weight: 800; letter-spacing: .5px; }
.campus-map g[class*='cm-'] { cursor: pointer; }
.campus-map g.cm-has:hover polygon { filter: brightness(1.04); }

.cm-detail {
  position: absolute; left: 14px; bottom: 14px; width: 260px;
  background: var(--card); border: 1px solid var(--border); border-radius: 12px;
  box-shadow: 0 10px 30px rgba(0,0,0,.18); padding: 12px 14px; z-index: 5;
}
.cm-detail-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.cm-detail-head b { font-size: 13.5px; color: var(--text); }
.cm-close { background: none; border: none; color: var(--text-light); cursor: pointer; font-size: 13px; }
.cm-detail-list { display: flex; flex-direction: column; gap: 7px; max-height: 200px; overflow-y: auto; }
.cm-course { padding: 7px 10px; border: 1px solid var(--border); border-radius: 9px; }
.cm-course b { display: block; font-size: 12.5px; color: var(--text); }
.cm-course span { font-size: 12px; color: var(--text-light); }
.cm-nodata { font-size: 12px; color: var(--text-light); opacity: .8; }
</style>
