<template>
  <div ref="wrap" class="bot3d" :class="{ 'bot3d--fade': !compact }" aria-hidden="true"></div>
</template>

<script setup lang="ts">
/**
 * AI 助手 · 时空机甲（v15 · 第二轮精修）
 *
 * 结构（自下而上）：
 *   三层全息底座（光池 / 刻度盘 / 旋转能量带 / 光柱）
 *   → 三层时空轨道（近身细轨 · 中景全息带 · 远景空间轨，不同倾角与转速）
 *   → 上半身：腰腹甲 → 胸甲 + 玻璃能源核 → 肩甲 + 上臂 → 颈环 → 头部
 *   → 头部：白瓷球壳 + 弧形黑玻璃面罩 + 冰蓝发光眼 + 耳舱
 *   → 空间光晕 / 星点粒子 / 定位刻线
 *
 * 比例：头径 0.70、肩宽 0.95、总高 1.65 —— 头略大但不失控（约 2.4 头身），Q 版比例。
 *
 * 五个状态由 STATE_FX 表驱动（轨道速度 / 核心增益 / 光晕 / 眼睛缩放 / 呼吸 / 扫视）：
 *   idle 轻微浮动 + 偶发眨眼；listening 核心增亮、呼吸加快；
 *   thinking 环与粒子加速、眼睛微眯、左右扫视；responding 核心脉冲加快；
 *   success 一次 0.9s 点头 + 光效峰值。旧值 streaming/error 自动归一。
 *
 * 关键几何处理：
 *   - 面罩 / 眼片都是"细分网格逐顶点投影到球面"，平面片会被球面吞掉内侧（见 visorGeometry / eyePatch）
 *   - 躯干用 LatheGeometry 由侧面轮廓旋转成型，首尾各补一个近零半径点把上下口封死
 *   - 躯干与其分模线放在同一个 scale(1.05,1,0.8) 的组里，避免椭圆环单独被拉伸
 *   - 三层轨道都"前低后高"倾斜：前半圈落在胸腹以下，绝不横穿面罩与眼睛
 *   - 底座挂在 scene 上而不是机器人组，身体浮动时底座保持静止（才有"悬浮"关系）
 *   - 底座混合模式随主题切换：浅色页面用普通混合，深色页面用加色混合
 *
 * 性能：粒子数按 hardwareConcurrency 降级，compact 模式只留头肩、不生成粒子与远景轨；
 *   prefers-reduced-motion 停帧；组件销毁时统一 dispose 几何体/材质/贴图。
 */
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import type * as ThreeNS from 'three'

type BotState =
  | 'idle' | 'listening' | 'thinking' | 'responding' | 'success'
  // 兼容旧值：Chat.vue 早期只传这四个
  | 'streaming' | 'error'

/** 把旧状态名归一到五态 */
function normalizeState(v: BotState): string {
  if (v === 'streaming') return 'responding'
  return v
}
const props = withDefaults(defineProps<{ state?: BotState; compact?: boolean }>(), {
  state: 'idle',
  compact: false,
})

const wrap = ref<HTMLDivElement | null>(null)

/* ==========================================================================
 * 主题化灯光 / 材质配置
 * ======================================================================== */
type ThemeKey = 'dark' | 'light'

interface BotThemeCfg {
  exposure: number
  backdrop: { color: number; opacity: number }
  ambient: { color: number; intensity: number }
  hemi: { sky: number; ground: number; intensity: number }
  key: { color: number; intensity: number; pos: [number, number, number] }
  rim: { color: number; intensity: number; pos: [number, number, number] }
  fill: { color: number; intensity: number; pos: [number, number, number] }
  shell: { color: number; roughness: number; metalness: number; clearcoat: number; clearcoatRoughness: number; env: number }
  structure: { color: number; roughness: number; metalness: number; env: number }
  metal: { color: number; metalness: number; roughness: number; env: number }
  metalDark: { color: number; metalness: number; roughness: number; env: number }
  oled: { color: number; roughness: number; env: number; emissive: number; emissiveIntensity: number; clearcoat: number; clearcoatRoughness: number }
  accent: { color: number; emissive: number; emissiveIntensity: number; env: number }
  core: { emissive: number; intensity: number }
  eye: { color: number; hdr: number }
  tip: { hdr: number }
  shadow: { opacity: number }
  fx: { ring: number; halo: number; dust: number; base: number; orbit: number; hud: number; glow: number }
}

/** 每个状态的外观系数：轨道速度 / 核心增益 / 光晕 / 眼睛缩放 / 呼吸幅度 / 扫视幅度 */
const STATE_FX: Record<string, {
  orbit: number; core: number; glow: number; eye: number; breath: number; scan: number
}> = {
  idle:       { orbit: 0.30, core: 1.00, glow: 1.00, eye: 1.00, breath: 1.00, scan: 0.00 },
  listening:  { orbit: 0.46, core: 1.55, glow: 1.15, eye: 1.10, breath: 1.35, scan: 0.00 },
  thinking:   { orbit: 1.85, core: 1.25, glow: 1.05, eye: 0.90, breath: 0.70, scan: 1.00 },
  responding: { orbit: 0.95, core: 1.45, glow: 1.30, eye: 1.05, breath: 0.85, scan: 0.18 },
  success:    { orbit: 0.55, core: 1.70, glow: 1.45, eye: 1.15, breath: 0.60, scan: 0.00 },
  error:      { orbit: 0.40, core: 1.00, glow: 1.00, eye: 0.85, breath: 0.90, scan: 0.00 },
}

const THEME: Record<ThemeKey, BotThemeCfg> = {
  dark: {
    exposure: 0.88,
    backdrop: { color: 0x2f6f9c, opacity: 0 },
    ambient: { color: 0x9fc8ff, intensity: 0.1 },
    hemi: { sky: 0xbcdcff, ground: 0x0a121a, intensity: 0.36 },
    key: { color: 0xfff1e0, intensity: 0.42, pos: [4.2, 6.2, 5.4] },
    rim: { color: 0x8cccff, intensity: 1.7, pos: [-5.2, 3.4, -4.6] },
    fill: { color: 0xdfe9ff, intensity: 0.16, pos: [-2.6, 0.6, 4.6] },
    shell: { color: 0xdfe4ea, roughness: 0.42, metalness: 0.08, clearcoat: 0.40, clearcoatRoughness: 0.34, env: 0.38 },
    structure: { color: 0x4b555f, roughness: 0.5, metalness: 0.6, env: 0.45 },
    metal: { color: 0xd6dce4, metalness: 0.60, roughness: 0.36, env: 0.44 },
    metalDark: { color: 0x99a1ac, metalness: 0.9, roughness: 0.3, env: 0.45 },
    oled: { color: 0x03060b, roughness: 0.38, env: 0.03, emissive: 0x081420, emissiveIntensity: 0.6, clearcoat: 0.16, clearcoatRoughness: 0.55 },
    accent: { color: 0x4ec5f1, emissive: 0x1a86c8, emissiveIntensity: 0.45, env: 0.4 },
    core: { emissive: 0x3fc9ff, intensity: 0.62 },
    eye: { color: 0x8fe0ff, hdr: 1.9 },
    tip: { hdr: 1.4 },
    shadow: { opacity: 0 },
    fx: { ring: 0.55, halo: 0.07, dust: 0.42, base: 1.10, orbit: 1.00, hud: 0.40, glow: 0.18 },
  },
  light: {
    exposure: 0.78,
    // 浅色页面：用一层淡蓝灰衬底把白瓷机器人从白背景里"托"出来
    backdrop: { color: 0x7c9bb5, opacity: 0.28 },
    ambient: { color: 0xdfe9f5, intensity: 0.04 },
    hemi: { sky: 0xe8f2ff, ground: 0x8593a3, intensity: 0.14 },
    key: { color: 0xfff6e8, intensity: 1.05, pos: [-7.0, 4.0, 2.2] },
    // rim 从 2.3 降到 0.9：原来白边光把白壳整个洗亮，轮廓反而没了
    rim: { color: 0x9fc8e8, intensity: 0.9, pos: [4.6, 2.6, -5.4] },
    fill: { color: 0xc4d6e8, intensity: 0.05, pos: [3.0, 1.0, 4.2] },
    // 壳色偏冷灰、略降反射，和白背景拉开明度差
    shell: { color: 0xcbd5e0, roughness: 0.46, metalness: 0.10, clearcoat: 0.34, clearcoatRoughness: 0.36, env: 0.32 },
    structure: { color: 0x2c3642, roughness: 0.52, metalness: 0.55, env: 0.42 },
    metal: { color: 0xb8c3d0, metalness: 0.58, roughness: 0.40, env: 0.42 },
    metalDark: { color: 0x77828f, metalness: 0.9, roughness: 0.34, env: 0.46 },
    oled: { color: 0x03060b, roughness: 0.36, env: 0.03, emissive: 0x0a1622, emissiveIntensity: 0.6, clearcoat: 0.16, clearcoatRoughness: 0.55 },
    accent: { color: 0x4ec5f1, emissive: 0x1a86c8, emissiveIntensity: 0.45, env: 0.45 },
    core: { emissive: 0x3fc9ff, intensity: 0.58 },
    eye: { color: 0x6fd4ff, hdr: 1.6 },
    tip: { hdr: 1.1 },
    shadow: { opacity: 0.4 },
    fx: { ring: 0.16, halo: 0.05, dust: 0.26, base: 0.74, orbit: 0.86, hud: 0.44, glow: 0.13 },
  },
}

let renderer: ThreeNS.WebGLRenderer | null = null
let scene: ThreeNS.Scene | null = null
let camera: ThreeNS.PerspectiveCamera | null = null
let raf = 0
let ro: ResizeObserver | null = null
let disposed = false
let reduced = false
let drawFn: (() => void) | null = null
const disposables: Array<{ dispose: () => void }> = []
const cleanups: Array<() => void> = []

const mouse = { x: 0, y: 0, tx: 0, ty: 0 }
let curState: BotState = 'idle'
let errorFlash = 0
let pulse = 0

watch(() => props.state, (s) => {
  curState = s
  if (s === 'error') errorFlash = 1
  else if (s === 'thinking' || s === 'streaming' || s === 'responding' || s === 'success' || s === 'listening') {
    pulse = Math.max(pulse, s === 'success' ? 0.9 : 0.6)
  }
  if (reduced) drawFn?.()
}, { immediate: true })

/** 贴图统一采样：mipmap + 各向异性 */
function prepTex<T extends { generateMipmaps: boolean; minFilter: number; magFilter: number; anisotropy: number; needsUpdate: boolean }>(
  T3: typeof import('three'), tex: T, aniso: number,
): T {
  tex.generateMipmaps = true
  tex.minFilter = T3.LinearMipmapLinearFilter
  tex.magFilter = T3.LinearFilter
  tex.anisotropy = aniso
  tex.needsUpdate = true
  return tex
}

/** 径向柔光（眼/核/天线/光环共用） */
function glowTexture(T: typeof import('three'), aniso: number) {
  const c = document.createElement('canvas')
  c.width = c.height = 128
  const g = c.getContext('2d')
  if (g) {
    const rg = g.createRadialGradient(64, 64, 2, 64, 64, 62)
    rg.addColorStop(0, 'rgba(255,255,255,1)')
    rg.addColorStop(0.3, 'rgba(120,214,255,.5)')
    rg.addColorStop(1, 'rgba(120,214,255,0)')
    g.fillStyle = rg
    g.fillRect(0, 0, 128, 128)
  }
  const tex = new T.CanvasTexture(c)
  tex.colorSpace = T.SRGBColorSpace
  return prepTex(T, tex, aniso)
}


/** 屏幕：斜向白→浅蓝渐变 + 顶部玻璃高光 + 边缘内阴影（对齐参考图） */
function screenTexture(T: typeof import('three'), aniso: number) {
  const c = document.createElement('canvas')
  c.width = c.height = 256
  const g = c.getContext('2d')
  if (g) {
    const lg = g.createLinearGradient(0, 0, 256, 256)
    lg.addColorStop(0, '#ffffff')
    lg.addColorStop(0.42, '#f2fbff')
    lg.addColorStop(1, '#bfe6ff')
    g.fillStyle = lg
    g.fillRect(0, 0, 256, 256)
    // 顶部一条斜向高光，模拟玻璃反光
    const hl = g.createLinearGradient(0, 0, 0, 96)
    hl.addColorStop(0, 'rgba(255,255,255,0.95)')
    hl.addColorStop(1, 'rgba(255,255,255,0)')
    g.fillStyle = hl
    g.fillRect(0, 0, 256, 96)
    // 四周轻微内阴影，让屏幕看起来是凹进去的
    const edge = g.createLinearGradient(0, 0, 0, 18)
    edge.addColorStop(0, 'rgba(120,170,210,0.22)')
    edge.addColorStop(1, 'rgba(120,170,210,0)')
    g.fillStyle = edge
    g.fillRect(0, 0, 256, 18)
  }
  const tex = new T.CanvasTexture(c)
  tex.colorSpace = T.SRGBColorSpace
  return prepTex(T, tex, aniso)
}

/** 背景衬底（径向 alpha，四周衰减到 0，画布不出现硬边方框） */
/**
 * 弧形镜片：细分网格投影到球面。
 * 踩过的坑：ShapeGeometry/ExtrudeGeometry 只在轮廓上有顶点，三角面是"弦"，
 * 投影后弦会深深切进球体内部 —— 结果只剩边缘一圈可见。
 * 所以必须用带网格密度的平面，逐顶点投影；轮廓的圆角交给 alphaMap 遮罩。
 */
function visorGeometry(
  T: typeof import('three'),
  R: number, width: number, height: number, segX = 56, segY = 34,
) {
  const geo = new T.PlaneGeometry(width, height, segX, segY)
  const pos = geo.attributes.position as ThreeNS.BufferAttribute
  for (let i = 0; i < pos.count; i++) {
    const x = pos.getX(i)
    const y = pos.getY(i)
    pos.setZ(i, Math.sqrt(Math.max(0, R * R - x * x - y * y)))
  }
  pos.needsUpdate = true
  geo.computeVertexNormals()
  return geo
}

/** 圆角矩形遮罩：作为镜片的 alphaMap，给出真正的圆角轮廓（几何投影做不到圆角） */
function visorMask(T: typeof import('three'), aniso: number, w: number, h: number, r: number) {
  const H = 512
  const W = Math.round(H * (w / h))
  const c = document.createElement('canvas')
  c.width = W; c.height = H
  const g = c.getContext('2d')
  if (g) {
    g.fillStyle = '#000'
    g.fillRect(0, 0, W, H)
    g.fillStyle = '#fff'
    const rr = r * (H / h)
    g.beginPath()
    g.roundRect(1, 1, W - 2, H - 2, rr)
    g.fill()
  }
  const tex = new T.CanvasTexture(c)
  tex.colorSpace = T.NoColorSpace
  return prepTex(T, tex, aniso)
}


/**
 * 眼片：把圆形网格逐顶点投影到球面。
 * 踩过的坑：用平面 CircleGeometry 贴在球面上，眼睛内侧（靠近球心轴）的球面
 * 比平面更靠前，会把眼片内侧"吞掉"，形成月牙形缺口。
 */
function eyePatch(
  T: typeof import('three'), R: number,
  ex: number, ey: number, radius: number, seg = 36, lift = 0.006,
) {
  const z0 = Math.sqrt(Math.max(0.0001, R * R - ex * ex - ey * ey))
  const geo = new T.CircleGeometry(radius, seg)
  const pos = geo.attributes.position as ThreeNS.BufferAttribute
  for (let i = 0; i < pos.count; i++) {
    // 顶点以眼睛中心为基准做球面投影（相对 z），组再整体摆到 (ex, ey, z0)
    const x = pos.getX(i) + ex
    const y = pos.getY(i) + ey
    pos.setZ(i, Math.sqrt(Math.max(0.0001, R * R - x * x - y * y)) - z0 + lift)
  }
  pos.needsUpdate = true
  geo.computeVertexNormals()
  return geo
}

/**
 * 全息轨道带：在方形 UV 里画一个带刻度的圆环。
 * RingGeometry 的 uv 是「以几何外径为半径做归一化」的方图，所以圆环要画在画布正中、
 * 外缘贴着画布内切圆 —— 这样任意内/外径的 Ring 都能复用同一张图。
 */
function orbitBandTexture(T: typeof import('three'), aniso: number, dash = 180, majorEvery = 15) {
  const S = 512
  const c = document.createElement('canvas')
  c.width = c.height = S
  const g = c.getContext('2d')
  if (g) {
    const cx = S / 2
    // 柔和的环带底
    const band = g.createRadialGradient(cx, cx, S * 0.34, cx, cx, S * 0.5)
    band.addColorStop(0, 'rgba(255,255,255,0)')
    band.addColorStop(0.66, 'rgba(255,255,255,0.10)')
    band.addColorStop(0.90, 'rgba(255,255,255,0.26)')
    band.addColorStop(1, 'rgba(255,255,255,0)')
    g.fillStyle = band
    g.beginPath()
    g.arc(cx, cx, S * 0.499, 0, Math.PI * 2)
    g.fill()
    // 外缘实线：给环一条干净锐利的边界
    g.strokeStyle = 'rgba(255,255,255,0.92)'
    g.lineWidth = 2.4
    g.beginPath()
    g.arc(cx, cx, S * 0.494, 0, Math.PI * 2)
    g.stroke()
    // 刻度：长的做定位标记，短的做速度感
    g.save()
    g.translate(cx, cx)
    for (let i = 0; i < dash; i++) {
      const a = (i / dash) * Math.PI * 2
      const major = i % majorEvery === 0
      const len = major ? S * 0.058 : S * 0.022
      const r1 = S * 0.490
      g.strokeStyle = major ? 'rgba(255,255,255,0.96)' : 'rgba(255,255,255,0.44)'
      g.lineWidth = major ? 3.6 : 1.3
      g.beginPath()
      g.moveTo(Math.cos(a) * (r1 - len), Math.sin(a) * (r1 - len))
      g.lineTo(Math.cos(a) * r1, Math.sin(a) * r1)
      g.stroke()
    }
    g.restore()
  }
  const tex = new T.CanvasTexture(c)
  tex.colorSpace = T.SRGBColorSpace
  return prepTex(T, tex, aniso)
}

function backdropTexture(T: typeof import('three'), aniso: number) {
  const c = document.createElement('canvas')
  c.width = c.height = 256
  const g = c.getContext('2d')
  if (g) {
    const rg = g.createRadialGradient(128, 128, 0, 128, 128, 128)
    rg.addColorStop(0, 'rgba(255,255,255,0.95)')
    rg.addColorStop(0.18, 'rgba(255,255,255,0.62)')
    rg.addColorStop(0.32, 'rgba(255,255,255,0.26)')
    rg.addColorStop(0.44, 'rgba(255,255,255,0.07)')
    rg.addColorStop(0.50, 'rgba(255,255,255,0)')
    g.fillStyle = rg
    g.fillRect(0, 0, 256, 256)
  }
  const tex = new T.CanvasTexture(c)
  tex.colorSpace = T.SRGBColorSpace
  return prepTex(T, tex, aniso)
}

/** 接触阴影（径向渐变椭圆） */
function contactShadowTexture(T: typeof import('three'), aniso: number) {
  const c = document.createElement('canvas')
  c.width = c.height = 256
  const g = c.getContext('2d')
  if (g) {
    const rg = g.createRadialGradient(128, 128, 4, 128, 128, 126)
    rg.addColorStop(0, 'rgba(10,16,24,0.66)')
    rg.addColorStop(0.34, 'rgba(10,16,24,0.30)')
    rg.addColorStop(0.66, 'rgba(10,16,24,0.07)')
    rg.addColorStop(1, 'rgba(10,16,24,0)')
    g.fillStyle = rg
    g.fillRect(0, 0, 256, 256)
  }
  const tex = new T.CanvasTexture(c)
  tex.colorSpace = T.SRGBColorSpace
  return prepTex(T, tex, aniso)
}

/** 微表面噪声：roughnessMap + bumpMap，去掉"均匀塑料"光泽 */
function noiseTexture(T: typeof import('three'), aniso: number, lo: number, hi: number, size = 192) {
  const c = document.createElement('canvas')
  c.width = c.height = size
  const g = c.getContext('2d')
  if (g) {
    const img = g.createImageData(size, size)
    const grid = 8
    const cells: number[][] = []
    for (let i = 0; i < grid * grid; i++) cells.push([Math.random(), Math.random(), Math.random()])
    const sample = (u: number, v: number) => {
      const gx = u * (grid - 1), gy = v * (grid - 1)
      const x0 = Math.floor(gx), y0 = Math.floor(gy)
      const fx = gx - x0, fy = gy - y0
      const at = (x: number, y: number) => cells[Math.min(grid - 1, y) * grid + Math.min(grid - 1, x)]
      const a = at(x0, y0), b = at(x0 + 1, y0), d = at(x0, y0 + 1), e = at(x0 + 1, y0 + 1)
      const top = a[0] * (1 - fx) + b[0] * fx
      const bot = d[1] * (1 - fx) + e[1] * fx
      return top * (1 - fy) + bot * fy
    }
    for (let y = 0; y < size; y++) {
      for (let x = 0; x < size; x++) {
        const n = sample(x / size, y / size) * 0.6 + Math.random() * 0.4
        const v = Math.round(255 * (lo + (hi - lo) * n))
        const i = (y * size + x) * 4
        img.data[i] = img.data[i + 1] = img.data[i + 2] = v
        img.data[i + 3] = 255
      }
    }
    g.putImageData(img, 0, 0)
  }
  const tex = new T.CanvasTexture(c)
  tex.wrapS = tex.wrapT = T.RepeatWrapping
  tex.repeat.set(2, 2)
  tex.colorSpace = T.NoColorSpace
  return prepTex(T, tex, aniso)
}

async function init() {
  const el = wrap.value
  if (!el) return

  let T: typeof import('three')
  let RoundedBoxGeometry: typeof import('three/examples/jsm/geometries/RoundedBoxGeometry.js').RoundedBoxGeometry
  let RGBELoader: typeof import('three/examples/jsm/loaders/RGBELoader.js').RGBELoader
  let RoomEnvironment: typeof import('three/examples/jsm/environments/RoomEnvironment.js').RoomEnvironment
  let EffectComposer: typeof import('three/examples/jsm/postprocessing/EffectComposer.js').EffectComposer
  let RenderPass: typeof import('three/examples/jsm/postprocessing/RenderPass.js').RenderPass
  let UnrealBloomPass: typeof import('three/examples/jsm/postprocessing/UnrealBloomPass.js').UnrealBloomPass
  let OutputPass: typeof import('three/examples/jsm/postprocessing/OutputPass.js').OutputPass
  try {
    const [three, rbg, rgbe, envMod, ec, rp, bp, op] = await Promise.all([
      import('three'),
      import('three/examples/jsm/geometries/RoundedBoxGeometry.js'),
      import('three/examples/jsm/loaders/RGBELoader.js'),
      import('three/examples/jsm/environments/RoomEnvironment.js'),
      import('three/examples/jsm/postprocessing/EffectComposer.js'),
      import('three/examples/jsm/postprocessing/RenderPass.js'),
      import('three/examples/jsm/postprocessing/UnrealBloomPass.js'),
      import('three/examples/jsm/postprocessing/OutputPass.js'),
    ])
    T = three
    RoundedBoxGeometry = rbg.RoundedBoxGeometry
    RGBELoader = rgbe.RGBELoader
    RoomEnvironment = envMod.RoomEnvironment
    EffectComposer = ec.EffectComposer
    RenderPass = rp.RenderPass
    UnrealBloomPass = bp.UnrealBloomPass
    OutputPass = op.OutputPass
  } catch {
    el.classList.add('is-fallback')
    return
  }
  if (disposed) return

  const keep = <X extends { dispose: () => void }>(o: X): X => { disposables.push(o); return o }

  let glr: ThreeNS.WebGLRenderer
  try {
    glr = new T.WebGLRenderer({ antialias: true, alpha: true, powerPreference: 'high-performance' })
  } catch {
    el.classList.add('is-fallback')
    return
  }
  renderer = glr
  glr.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.6))
  glr.outputColorSpace = T.SRGBColorSpace
  glr.toneMapping = T.ACESFilmicToneMapping
  glr.shadowMap.enabled = !props.compact && !matchMedia('(prefers-reduced-motion: reduce)').matches
  glr.shadowMap.type = T.PCFSoftShadowMap
  el.appendChild(glr.domElement)
  const maxAniso = Math.min(8, glr.capabilities.getMaxAnisotropy())

  let themeKey: ThemeKey = document.documentElement.getAttribute('data-theme') === 'dark' ? 'dark' : 'light'
  const cfg = () => THEME[themeKey]
  glr.setClearColor(0x000000, 0)
  glr.toneMappingExposure = cfg().exposure

  const themeOb = new MutationObserver(() => {
    themeKey = document.documentElement.getAttribute('data-theme') === 'dark' ? 'dark' : 'light'
    applyTheme()
  })
  themeOb.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] })
  cleanups.push(() => themeOb.disconnect())

  scene = new T.Scene()
  camera = new T.PerspectiveCamera(34, 1, 0.1, 60)
  // 构图：模型纵向 -0.74(底座) … +0.90(头顶) 共 1.64；可见高 1.85 → 机器人占画布高约 88%
  // compact 只做头肩特写（顶栏 44px 头像），否则整体缩成一个小点
  const lookY = props.compact ? 0.56 : 0.07
  if (props.compact) camera.position.set(0, 0.56, 1.30)
  else camera.position.set(0, 0.23, 2.80)
  camera.lookAt(0, lookY, 0)

  // ---------- 环境反射：HDRI + 程序化兜底 ----------
  const pmrem = new T.PMREMGenerator(glr)
  try {
    const envScene = new RoomEnvironment()
    const fallback = pmrem.fromScene(envScene, 0.04).texture
    scene.environment = fallback
    keep({ dispose: () => fallback.dispose() })
  } catch { /* 无环境贴图仅损失反射 */ }
  void (async () => {
    try {
      const hdr = await new RGBELoader().loadAsync('/hdri/studio_small_08_1k.hdr')
      if (disposed || !scene) { hdr.dispose(); return }
      hdr.mapping = T.EquirectangularReflectionMapping
      const rt = pmrem.fromEquirectangular(hdr)
      hdr.dispose()
      scene.environment = rt.texture
      keep({ dispose: () => rt.texture.dispose() })
    } catch { /* 离线：保留兜底环境 */ }
    finally { pmrem.dispose() }
  })()

  // ---------- 布光 ----------
  const key = new T.DirectionalLight(cfg().key.color, cfg().key.intensity)
  key.position.set(...cfg().key.pos)
  scene.add(key)
  const rim = new T.DirectionalLight(cfg().rim.color, cfg().rim.intensity)
  rim.position.set(...cfg().rim.pos)
  scene.add(rim)
  const fill = new T.DirectionalLight(cfg().fill.color, cfg().fill.intensity)
  fill.position.set(...cfg().fill.pos)
  scene.add(fill)
  const ambient = new T.AmbientLight(cfg().ambient.color, cfg().ambient.intensity)
  scene.add(ambient)
  const hemi = new T.HemisphereLight(cfg().hemi.sky, cfg().hemi.ground, cfg().hemi.intensity)
  scene.add(hemi)

  // ---------- 材质 ----------
  const shellNoise = keep(noiseTexture(T, maxAniso, 0.78, 1.0))
  const metalNoise = keep(noiseTexture(T, maxAniso, 0.55, 0.95, 128))

  const shell = keep(new T.MeshPhysicalMaterial({
    color: cfg().shell.color,
    roughness: cfg().shell.roughness,
    metalness: cfg().shell.metalness,
    clearcoat: cfg().shell.clearcoat,
    clearcoatRoughness: cfg().shell.clearcoatRoughness,
    envMapIntensity: cfg().shell.env,
    roughnessMap: shellNoise,
    bumpMap: shellNoise,
    bumpScale: 0.0014,
  }))
  const structure = keep(new T.MeshStandardMaterial({
    color: cfg().structure.color,
    roughness: cfg().structure.roughness,
    metalness: cfg().structure.metalness,
    envMapIntensity: cfg().structure.env,
    roughnessMap: metalNoise,
  }))
  const metal = keep(new T.MeshStandardMaterial({
    color: cfg().metal.color,
    metalness: cfg().metal.metalness,
    roughness: cfg().metal.roughness,
    envMapIntensity: cfg().metal.env,
    roughnessMap: metalNoise,
    bumpMap: metalNoise,
    bumpScale: 0.001,
  }))
  const metalDark = keep(new T.MeshStandardMaterial({
    color: cfg().metalDark.color,
    metalness: cfg().metalDark.metalness,
    roughness: cfg().metalDark.roughness,
    envMapIntensity: cfg().metalDark.env,
    roughnessMap: metalNoise,
  }))
  const oled = keep(new T.MeshPhysicalMaterial({
    color: cfg().oled.color,
    metalness: 0,
    roughness: cfg().oled.roughness,
    specularIntensity: 0.16,
    clearcoat: cfg().oled.clearcoat,
    clearcoatRoughness: cfg().oled.clearcoatRoughness,
    envMapIntensity: cfg().oled.env,
    emissive: cfg().oled.emissive,
    emissiveIntensity: cfg().oled.emissiveIntensity,
    alphaMap: keep(visorMask(T, maxAniso, 0.470, 0.286, 0.092)),
    transparent: true,
    alphaTest: 0.5,
    polygonOffset: true, polygonOffsetFactor: -2, polygonOffsetUnits: -2,
  }))
  // 胸甲 / 肩甲：比头壳更亮、更硬的"上釉瓷" —— 同一套白，靠粗糙度分层，才有陶瓷+金属的混合感
  const shell2 = keep(new T.MeshPhysicalMaterial({
    color: cfg().shell.color,
    roughness: 0.22,
    metalness: 0.16,
    clearcoat: 0.70,
    clearcoatRoughness: 0.16,
    envMapIntensity: cfg().shell.env + 0.16,
    roughnessMap: shellNoise,
    bumpMap: shellNoise,
    bumpScale: 0.001,
  }))
  const accent = keep(new T.MeshStandardMaterial({
    color: cfg().accent.color,
    metalness: 0.25,
    roughness: 0.3,
    emissive: cfg().accent.emissive,
    emissiveIntensity: cfg().accent.emissiveIntensity,
    envMapIntensity: cfg().accent.env,
  }))
  // 能量核：用基础材质直接给 HDR 色 —— Standard 材质会被白光照成"白球"，失去自发光感
  const coreMat = keep(new T.MeshBasicMaterial({ color: 0xffffff }))
  coreMat.color.set(cfg().core.emissive).multiplyScalar(1.45)
  const eyeMat = keep(new T.MeshBasicMaterial({ color: 0xffffff }))
  eyeMat.color.setHex(cfg().eye.color).multiplyScalar(cfg().eye.hdr)
  const eyeHiMat = keep(new T.MeshBasicMaterial({ color: 0xffffff }))
  const tipMat = keep(new T.MeshBasicMaterial({ color: 0xffc75e }))
  const glowTex = keep(glowTexture(T, maxAniso))
  const bandTex = keep(orbitBandTexture(T, maxAniso))

  // ---------- 背景衬底 / 接触阴影 ----------
  const backdropMat = keep(new T.MeshBasicMaterial({
    map: backdropTexture(T, maxAniso), color: cfg().backdrop.color, transparent: true,
    opacity: cfg().backdrop.opacity, depthWrite: false, toneMapped: false,
  }))
  const backdropMesh = new T.Mesh(keep(new T.PlaneGeometry(12, 12)), backdropMat)
  backdropMesh.scale.setScalar(props.compact ? 0.55 : 1)
  backdropMesh.position.set(0, 1.0, -4)
  scene.add(backdropMesh)

  // ================= 模型：白瓷机甲 · 上半身 =================
  // 头径 0.70 / 肩宽 0.95 / 总高 1.65 —— 约 2.4 头身，圆润但不失控。
  const isCompact = !!props.compact
  const bot = new T.Group()
  scene.add(bot)

  // 刚性体：上半身整体（悬浮 / 侧摆作用在它身上）
  const rig = new T.Group()
  bot.add(rig)

  // ===== 真实 3D 模型（AI 生成 PBR 机器人）=====
  // 加载成功后隐藏程序化身体，用 GLB 作主体；光效/轨道/底座/五态驱动照常运作。
  let glbRoot: ThreeNS.Group | null = null
  let glbBaseSize = 1
  let glbBaseY = 0   // 模型居中基准 y（循环里浮动叠加在它之上）
  let glbFaceY = 0
  let glbEyes: ThreeNS.Mesh[] = []
  let glbEyeGroup: ThreeNS.Group | null = null
  let glbEyeGlows: ThreeNS.Sprite[] = []
  let glbScanline: ThreeNS.Mesh | null = null
  // 加载期间先隐藏程序化身体，避免刷新时先闪一下旧模型再切 GLB
  rig.visible = false
  ;(async () => {
    try {
      const { GLTFLoader } = await import('three/examples/jsm/loaders/GLTFLoader.js')
      // 固定新文件名破缓存；换模型时改文件名 + 这里的路径即可
      const gltf = await new GLTFLoader().loadAsync('/models/ai-mascot-v12.glb')
      if (disposed || !scene) return
      glbRoot = gltf.scene
      const box = new T.Box3().setFromObject(glbRoot)
      const size = box.getSize(new T.Vector3())
      const center = box.getCenter(new T.Vector3())
      // 按高度缩放（模型含宽底座，用 max 维度会把整体压得太小）
      glbBaseSize = size.y || 1
      const k = (isCompact ? 0.78 : 1.08) / glbBaseSize
      glbRoot.scale.setScalar(k)
      // 居中到原点；相机 lookAt 原点，底座/身体都在视野内
      glbBaseY = -center.y * k
      glbRoot.position.set(-center.x * k, glbBaseY, -center.z * k)
      glbFaceY = 0
      glbRoot.rotation.y = glbFaceY
      bot.add(glbRoot)
      rig.visible = false   // 隐藏程序化身体；底座/轨道仍在 bot 下照常显示
      // GLB 是完整带底座模型，重新定相机：看模型中心，距离随模式调整
      camera.lookAt(0, 0.24, 0)
      camera.position.set(0, 0.30, isCompact ? 2.5 : 3.2)
      glbRoot.traverse((o: any) => {
        if (o.isMesh) {
          o.castShadow = true
          if (o.material?.emissive) glbEyes.push(o as ThreeNS.Mesh)
        }
      })

      // ===== 参考图特征补全：微笑嘴 + 头顶天线 + 胸口发光圆点 =====
      // 模型自带双眼，这里只补它缺的三样；全部按模型自身尺寸比例定位。
      const bMaxY = box.max.y
      const faceZ = center.z + size.z * 0.5 + size.z * 0.03   // 略推到模型表面之外
      const mouthY = center.y + size.y * 0.10      // 面罩下部、眼睛下方（相对模型中心）
      const mouthW = size.x * 0.10
      // 微笑嘴：柔和的小 ∪ 弧（两端略高、中间略低），细且暗，不抢眼睛
      const smilePts: THREE.Vector3[] = []
      for (let i = 0; i <= 12; i++) {
        const u = i / 12
        const px = center.x + (u - 0.5) * mouthW * 2
        const py = mouthY - Math.sin(u * Math.PI) * size.y * 0.022
        smilePts.push(new T.Vector3(px, py, faceZ))
      }
      const smileCurve = new T.CatmullRomCurve3(smilePts)
      const smile = new T.Mesh(
        keep(new T.TubeGeometry(smileCurve, 20, size.y * 0.008, 6, false)),
        keep(new T.MeshBasicMaterial({ color: 0x7fdcf0, transparent: true, opacity: 0.9, toneMapped: false, depthTest: false }))
      )
      smile.renderOrder = 900
      glbRoot.add(smile)

      // 头顶天线：细杆 + 顶端小圆球
      const antenna = new T.Group()
      const postH = size.y * 0.09
      const post = new T.Mesh(
        keep(new T.CylinderGeometry(size.y * 0.008, size.y * 0.008, postH, 8)),
        keep(new T.MeshStandardMaterial({ color: 0xcdd8e4, roughness: 0.4, metalness: 0.5 }))
      )
      post.position.y = postH / 2
      antenna.add(post)
      const tip = new T.Mesh(
        keep(new T.SphereGeometry(size.y * 0.055, 16, 12)),
        keep(new T.MeshStandardMaterial({ color: 0x5ec8ff, emissive: 0x2a9fd8, emissiveIntensity: 0.8, roughness: 0.3 }))
      )
      tip.position.y = postH
      antenna.add(tip)
      antenna.position.set(center.x, bMaxY - size.y * 0.005, center.z)
      glbRoot.add(antenna)

      // 胸口发光圆点：贴在机身正面中部
      const chest = new T.Mesh(
        keep(new T.CircleGeometry(size.y * 0.075, 24)),
        keep(new T.MeshBasicMaterial({ color: 0x6fe0ff, toneMapped: false, depthTest: false }))
      )
      ;(chest.material as any).color.multiplyScalar(1.5)
      chest.position.set(center.x, center.y - size.y * 0.08, faceZ)
      chest.renderOrder = 900
      glbRoot.add(chest)
      const chestGlow = new T.Sprite(new T.SpriteMaterial({
        map: glowTex, color: 0x5ec8ff, transparent: true, opacity: 0.5,
        blending: T.AdditiveBlending, depthWrite: false, toneMapped: false,
      }))
      chestGlow.position.copy(chest.position); chestGlow.position.z += 0.002
      chestGlow.scale.setScalar(size.y * 0.34)
      glbRoot.add(chestGlow)
      drawFn?.()
    } catch (e) {
      // 模型缺失/加载失败：回退到程序化机器人
      rig.visible = true
      console.warn('[Bot3D] GLB load failed, keeping procedural model', e)
    }
  })()

  /* ---------------- 头部 ---------------- */
  const HEAD_R = 0.305
  const HEAD_Y = 0.592
  const headGroup = new T.Group()
  headGroup.position.y = HEAD_Y
  headGroup.scale.setScalar(0.94)   // 头部缩小约 6%，头身比更修长
  rig.add(headGroup)

  headGroup.add(new T.Mesh(keep(new T.SphereGeometry(HEAD_R, 64, 48)), shell))

  // 面罩：弧形玻璃（细分投影 + 圆角遮罩），宽高按头径等比收到 0.54 × 0.33
  const visorR = HEAD_R * 1.006
  const visor = new T.Mesh(keep(visorGeometry(T, visorR, 0.470, 0.286, 44, 28)), oled)
  headGroup.add(visor)

  // 玻璃反光带：一道斜向高光，让面罩读成"玻璃"而不是"黑塑料"
  const glossMat = keep(new T.SpriteMaterial({
    map: glowTex, color: 0xffffff, transparent: true, opacity: 0.07,
    blending: T.AdditiveBlending, depthWrite: false,
  }))
  const gloss = new T.Sprite(glossMat)
  gloss.scale.set(0.26, 0.115, 1)
  gloss.position.set(-0.134, 0.096, visorR + 0.008)
  headGroup.add(gloss)

  // 眼：发光圆片（球面投影）+ 高光点 + 柔光晕
  const eyes: ThreeNS.Object3D[] = []
  const eyeGlows: ThreeNS.Sprite[] = []
  const eyeGlowMats: ThreeNS.SpriteMaterial[] = []
  for (const sx of [-1, 1]) {
    const ex = sx * 0.098
    const ey = 0.014
    const er = 0.048
    const ez = Math.sqrt(Math.max(0.0001, visorR * visorR - ex * ex - ey * ey))
    const g = new T.Group()
    g.position.set(ex, ey, ez)
    headGroup.add(g)
    g.add(new T.Mesh(keep(eyePatch(T, visorR, ex, ey, er)), eyeMat))
    // 上内侧的一小片高光，眼睛才有"湿润"的玻璃感
    g.add(new T.Mesh(keep(eyePatch(T, visorR, ex - 0.015, ey + 0.017, 0.015, 18, 0.010)), eyeHiMat))
    eyes.push(g)

    const gm = keep(new T.SpriteMaterial({
      map: glowTex, color: 0x9fe6ff, transparent: true, opacity: 0.1,
      blending: T.AdditiveBlending, depthWrite: false,
    }))
    const sp = new T.Sprite(gm)
    sp.scale.set(0.21, 0.21, 1)
    sp.position.set(ex, ey, ez + 0.02)
    headGroup.add(sp)
    eyeGlows.push(sp)
    eyeGlowMats.push(gm)
  }

  // 耳舱：两侧的小圆柱 + 瓷面盖 + 青点，头壳因此读成"头盔"而不是"球"
  for (const sx of [-1, 1]) {
    const pod = new T.Mesh(keep(new T.CylinderGeometry(0.060, 0.066, 0.056, 28)), metal)
    pod.rotation.z = Math.PI / 2
    pod.position.set(sx * 0.291, 0, 0)
    headGroup.add(pod)
    const cap = new T.Mesh(keep(new T.CylinderGeometry(0.046, 0.046, 0.016, 24)), shell2)
    cap.rotation.z = Math.PI / 2
    cap.position.set(sx * 0.318, 0, 0)
    headGroup.add(cap)
    const dot = new T.Mesh(keep(new T.SphereGeometry(0.009, 12, 10)), accent)
    dot.position.set(sx * 0.327, 0, 0)
    headGroup.add(dot)
  }

  /* ---------------- 颈部 ---------------- */
  // 颈：结构件（深色）+ 金属颈环，头与身体之间必须有实体连接，不能悬空
  const neck = new T.Mesh(keep(new T.CylinderGeometry(0.082, 0.098, 0.14, 28)), metalDark)
  neck.position.y = 0.272
  rig.add(neck)
  const collar = new T.Mesh(keep(new T.TorusGeometry(0.112, 0.023, 14, 56)), metal)
  collar.rotation.x = Math.PI / 2
  collar.position.y = 0.278
  collar.scale.set(1, 0.94, 1)
  rig.add(collar)
  // 领口装甲：胸口向上收出的那一圈，头和身体因此有"接口"
  const yoke = new T.Mesh(keep(new T.TorusGeometry(0.150, 0.030, 16, 64)), shell2)
  yoke.rotation.x = Math.PI / 2
  yoke.position.y = 0.246
  yoke.scale.set(1, 0.82, 1)
  rig.add(yoke)

  /* ---------------- 躯干 ---------------- */
  // 侧面轮廓车削成型的胸腹甲：腰部收、胸腔鼓、颈部收口。
  // 首尾各补一个近零半径点，把上下开口封死（否则仰视会看进壳体内部）。
  const torsoGroup = new T.Group()
  torsoGroup.scale.set(1.06, 1, 0.72)   // 压扁前后方向：机甲胸甲是扁的，不是坛子
  rig.add(torsoGroup)

  const torsoProfile: Array<[number, number]> = [
    [0.002, -0.356], [0.196, -0.354], [0.214, -0.332], [0.224, -0.293],
    [0.236, -0.231], [0.258, -0.157], [0.294, -0.056], [0.322, 0.042],
    [0.338, 0.112], [0.334, 0.162], [0.300, 0.208], [0.240, 0.238],
    [0.170, 0.252], [0.105, 0.258], [0.002, 0.262],
  ]
  const torso = new T.Mesh(
    keep(new T.LatheGeometry(torsoProfile.map(([r, y]) => new T.Vector2(r, y)), 72)),
    shell,
  )
  torsoGroup.add(torso)

  // 腰部分模线 + 下缘金属包边（"断口"要有一圈结构件收边，才不会像被切断）
  // 腰线：一道金属细缝 + 深色机械腰，壳体到此收边（否则下缘看起来像花瓶口）
  const waistSeam = new T.Mesh(keep(new T.TorusGeometry(0.224, 0.0038, 6, 120)), metalDark)
  waistSeam.rotation.x = Math.PI / 2
  waistSeam.position.y = -0.293
  torsoGroup.add(waistSeam)

  const waistJoint = new T.Mesh(keep(new T.CylinderGeometry(0.206, 0.150, 0.072, 48)), metalDark)
  waistJoint.position.y = -0.384
  waistJoint.scale.set(1.04, 1, 0.86)
  rig.add(waistJoint)
  const waistRing = new T.Mesh(keep(new T.TorusGeometry(0.207, 0.0075, 8, 64)), metal)
  waistRing.rotation.x = Math.PI / 2
  waistRing.position.y = -0.354
  waistRing.scale.set(1.02, 0.86, 1)
  rig.add(waistRing)
  const waistGlowMat = keep(new T.MeshBasicMaterial({
    color: 0x7fe0ff, transparent: true, opacity: 0.55,
    blending: T.AdditiveBlending, depthWrite: false, toneMapped: false,
  }))
  const waistGlow = new T.Mesh(keep(new T.TorusGeometry(0.160, 0.0042, 8, 64)), waistGlowMat)
  waistGlow.rotation.x = Math.PI / 2
  waistGlow.position.y = -0.416
  waistGlow.scale.set(1.02, 0.86, 1)
  rig.add(waistGlow)

  /* ---------------- 肩甲 / 上臂 ---------------- */
  const arms: ThreeNS.Group[] = []
  for (const sx of [-1, 1]) {
    // 肩甲：略压扁的球，坐在胸甲上缘
    // 护肩：贴着肩线的一小片弧甲（压扁球），不是独立的"球"
    const pad = new T.Mesh(keep(new T.SphereGeometry(0.132, 36, 24)), shell2)
    pad.scale.set(1.10, 0.66, 1.08)
    pad.position.set(sx * 0.300, 0.158, 0)
    pad.rotation.z = sx * -0.30
    rig.add(pad)

    // 肩关节：深色球窝，护肩与上臂之间的"机械骨"
    const joint = new T.Mesh(keep(new T.SphereGeometry(0.070, 24, 18)), structure)
    joint.position.set(sx * 0.328, 0.136, 0)
    rig.add(joint)

    // 上臂：胶囊 → 腕环 → 白瓷圆头收尾（不用深色球，避免像门把手/烧焦的火柴）
    const ag = new T.Group()
    ag.position.set(sx * 0.336, 0.126, 0)
    ag.rotation.z = sx * 0.10
    rig.add(ag)
    const upper = new T.Mesh(keep(new T.CapsuleGeometry(0.073, 0.198, 8, 24)), shell)
    upper.position.y = -0.140
    ag.add(upper)
    const wrist = new T.Mesh(keep(new T.TorusGeometry(0.0745, 0.0062, 8, 36)), metal)
    wrist.rotation.x = Math.PI / 2
    wrist.position.y = -0.250
    ag.add(wrist)
    // 手掌：白瓷圆掌 + 内侧拇指凸起 + 一圈分模线，读得出"手"而不是胶囊末端
    const cuff = new T.Mesh(keep(new T.SphereGeometry(0.076, 24, 18)), shell2)
    cuff.scale.set(1, 0.86, 0.92)
    cuff.position.y = -0.300
    ag.add(cuff)
    const thumb = new T.Mesh(keep(new T.SphereGeometry(0.026, 16, 12)), shell2)
    thumb.scale.set(0.72, 1, 0.9)
    thumb.position.set(-sx * 0.062, -0.306, 0.030)
    thumb.rotation.z = sx * 0.35
    ag.add(thumb)
    const cuffSeam = new T.Mesh(keep(new T.TorusGeometry(0.062, 0.0025, 6, 40)), metalDark)
    cuffSeam.rotation.x = Math.PI / 2
    cuffSeam.position.y = -0.328
    ag.add(cuffSeam)
    const cuffDot = new T.Mesh(keep(new T.SphereGeometry(0.010, 12, 10)), accent)
    cuffDot.position.set(0, -0.362, 0.040)
    ag.add(cuffDot)
    arms.push(ag)
  }

  /* ---------------- 胸口能源核心 ---------------- */
  // 胸腔在 y=0.02 处的表面 z ≈ 0.315 × 0.80 = 0.252
  const coreY = 0.040
  const coreZ = 0.230

  // 凹座：深色金属内壁，核心是"嵌进"胸甲而不是贴上去
  const coreSocket = new T.Mesh(keep(new T.CylinderGeometry(0.076, 0.090, 0.11, 40)), structure)
  coreSocket.rotation.x = Math.PI / 2
  coreSocket.position.set(0, coreY, coreZ - 0.048)
  rig.add(coreSocket)

  // 外圈金属环（凸出胸甲 6mm，给出厚度）
  const coreRing = new T.Mesh(keep(new T.TorusGeometry(0.084, 0.012, 12, 60)), metal)
  coreRing.position.set(0, coreY, coreZ - 0.006)
  rig.add(coreRing)

  // 内芯：基础材质给 HDR 青，避免被白光照成白球
  const core = new T.Mesh(keep(new T.SphereGeometry(0.056, 32, 24)), coreMat)
  core.position.set(0, coreY, 0.200)
  rig.add(core)

  // 内外双层金属环：外环已在上文（coreRing），这里补一圈更细的内环压在玻璃罩根部。
  // 用金属而非发光材质，避免和青核的光叠加成白圈。
  const coreInnerRing = new T.Mesh(keep(new T.TorusGeometry(0.061, 0.0075, 12, 56)), metalDark)
  coreInnerRing.position.set(0, coreY, 0.238)
  rig.add(coreInnerRing)

  // 透明玻璃外罩：用 FrontSide + 低不透明度的物理玻璃，只做"罩"的体积与边缘反光。
  // 踩过的坑：整颗 DoubleSide / transmission 玻璃在浅色透明画布下会采样错误背景把青核染成品红。
  // 这里用 FrontSide + 普通混合 + 低 opacity + 清漆，只提亮边缘、不改核心色；两种主题都验证过。
  const coreGlassMat = keep(new T.MeshPhysicalMaterial({
    color: 0xdff4ff, metalness: 0, roughness: 0.08,
    clearcoat: 0.92, clearcoatRoughness: 0.06,
    envMapIntensity: 0.5,
    transparent: true, opacity: 0.20,
    side: T.FrontSide, depthWrite: false,
  }))
  const coreGlass = new T.Mesh(keep(new T.SphereGeometry(0.066, 32, 24)), coreGlassMat)
  coreGlass.position.set(0, coreY, 0.208)
  coreGlass.renderOrder = 3
  rig.add(coreGlass)

  // 柔和能量脉冲：一圈从核心向外扩散并淡出的青色环（loop 里驱动 scale/opacity）。
  const corePulseMat = keep(new T.MeshBasicMaterial({
    color: 0x8fe0ff, transparent: true, opacity: 0.0,
    blending: T.AdditiveBlending, depthWrite: false, toneMapped: false,
  }))
  const corePulse = new T.Mesh(keep(new T.TorusGeometry(0.078, 0.004, 8, 64)), corePulseMat)
  corePulse.position.set(0, coreY, 0.250)
  rig.add(corePulse)

  const coreRimMat = keep(new T.MeshBasicMaterial({
    color: 0xd8f4ff, transparent: true, opacity: 0.5,
    blending: T.AdditiveBlending, depthWrite: false, toneMapped: false,
  }))
  const coreRim = new T.Mesh(keep(new T.TorusGeometry(0.064, 0.0032, 8, 64)), coreRimMat)
  coreRim.position.set(0, coreY, 0.232)
  rig.add(coreRim)
  const coreRim2 = new T.Mesh(keep(new T.TorusGeometry(0.050, 0.0022, 8, 56)), coreRimMat)
  coreRim2.position.set(0, coreY, 0.243)
  rig.add(coreRim2)

  const coreGlossMat = keep(new T.SpriteMaterial({
    map: glowTex, color: 0xffffff, transparent: true, opacity: 0.35,
    blending: T.AdditiveBlending, depthWrite: false,
  }))
  const coreGloss = new T.Sprite(coreGlossMat)
  coreGloss.scale.set(0.038, 0.038, 1)
  coreGloss.position.set(-0.020, coreY + 0.024, 0.252)
  rig.add(coreGloss)

  // 内部溢光 + 打在胸甲上的一圈淡光
  const coreGlowMat = keep(new T.SpriteMaterial({
    map: glowTex, color: 0x8fe0ff, transparent: true, opacity: 0.34,
    blending: T.AdditiveBlending, depthWrite: false,
  }))
  const coreGlow = new T.Sprite(coreGlowMat)
  coreGlow.scale.set(0.30, 0.30, 1)
  coreGlow.position.set(0, coreY, 0.260)
  rig.add(coreGlow)

  const coreHaloMat = keep(new T.SpriteMaterial({
    map: glowTex, color: 0x8fe0ff, transparent: true, opacity: 0.08,
    blending: T.AdditiveBlending, depthWrite: false,
  }))
  const coreHalo = new T.Sprite(coreHaloMat)
  coreHalo.scale.set(0.48, 0.48, 1)
  coreHalo.position.set(0, coreY, 0.236)
  rig.add(coreHalo)

  // 核心下方的状态光条（很细一条青线，撑住"面板"的机械感）
  const chestBar = new T.Mesh(keep(new T.BoxGeometry(0.148, 0.010, 0.014)), accent)
  chestBar.position.set(0, -0.112, 0.196)
  rig.add(chestBar)

  /* ---------------- 头部状态灯（藏在后脑，正面看不到"痣"） ---------------- */
  const lamp = new T.Mesh(keep(new T.SphereGeometry(0.016, 14, 12)), tipMat)
  lamp.position.set(0, 0.296, -0.170)
  headGroup.add(lamp)
  const tipGlowMat = keep(new T.SpriteMaterial({
    map: glowTex, color: 0xffd98a, transparent: true, opacity: 0.18,
    blending: T.AdditiveBlending, depthWrite: false,
  }))
  const tipGlow = new T.Sprite(tipGlowMat)
  tipGlow.scale.set(0.13, 0.13, 1)
  tipGlow.position.copy(lamp.position)
  headGroup.add(tipGlow)

  /* ================= 三层时空轨道 ================= */
  // 三层统一"前低后高"：前半圈压在胸腹以下，后半圈绕到脑后 —— 任何一圈都不会横穿面罩。
  // 1) 近身细轨：贴着腰腹的极细亮线
  const orbitMatA = keep(new T.MeshBasicMaterial({
    color: 0x9fe4ff, transparent: true, opacity: 0.5,
    blending: T.AdditiveBlending, depthWrite: false, toneMapped: false,
  }))
  const orbitGroupA = new T.Group()
  orbitGroupA.position.y = -0.128
  orbitGroupA.rotation.set(Math.PI / 2 + 0.55, 0, 0.10)
  bot.add(orbitGroupA)
  orbitGroupA.add(new T.Mesh(keep(new T.TorusGeometry(0.440, 0.0042, 8, 180)), orbitMatA))
  const dotMatA = keep(new T.MeshBasicMaterial({ color: 0xe8fbff, toneMapped: false }))
  const orbitDotA = new T.Mesh(keep(new T.SphereGeometry(0.016, 12, 10)), dotMatA)
  orbitDotA.position.set(0.440, 0, 0)
  orbitGroupA.add(orbitDotA)

  // 2) 中景全息带：带刻度的半透明环带，转速最快
  const bandMat = keep(new T.MeshBasicMaterial({
    map: bandTex, color: 0x6fd0f0, transparent: true, opacity: 0.42,
    blending: T.AdditiveBlending, depthWrite: false, side: T.DoubleSide, toneMapped: false,
  }))
  const bandGroup = new T.Group()
  bandGroup.position.y = -0.100
  bandGroup.rotation.set(Math.PI / 2 + 0.38, 0, -0.35)
  bot.add(bandGroup)
  bandGroup.add(new T.Mesh(keep(new T.RingGeometry(0.632, 0.702, 160, 1)), bandMat))

  // 3) 远景空间轨：最淡最外，缓速反向，负责空间纵深
  const orbitMatC = keep(new T.MeshBasicMaterial({
    color: 0x8fd4ff, transparent: true, opacity: 0.16,
    blending: T.AdditiveBlending, depthWrite: false, toneMapped: false,
  }))
  const orbitGroupC = new T.Group()
  orbitGroupC.position.y = 0.020
  orbitGroupC.rotation.set(Math.PI / 2 + 0.55, 0, 0.30)
  bot.add(orbitGroupC)
  orbitGroupC.add(new T.Mesh(keep(new T.TorusGeometry(0.720, 0.0028, 6, 200)), orbitMatC))
  const dotMatC = keep(new T.MeshBasicMaterial({ color: 0x9fd8ff, toneMapped: false }))
  const orbitDotC = new T.Mesh(keep(new T.SphereGeometry(0.010, 10, 8)), dotMatC)
  orbitDotC.position.set(0.720, 0, 0)
  orbitGroupC.add(orbitDotC)

  // 空间光晕（身体背后的柔光，撑层次）
  const ambientGlowMat = keep(new T.SpriteMaterial({
    map: glowTex, color: 0x8fd8ff, transparent: true, opacity: 0.12,
    blending: T.AdditiveBlending, depthWrite: false,
  }))
  const ambientGlow = new T.Sprite(ambientGlowMat)
  ambientGlow.scale.set(2.1, 2.1, 1)
  ambientGlow.position.set(0, 0.10, -0.9)
  scene.add(ambientGlow)

  /* ================= 三层全息悬浮底座 ================= */
  // 挂 scene 而不是 bot：身体上下浮动时底座纹丝不动，才读得出"悬浮"关系。
  const BASE_Y = -0.620
  const baseGroup = new T.Group()
  baseGroup.position.y = BASE_Y
  scene.add(baseGroup)

  // 底层：径向光池（最外、最柔）
  const baseMat = keep(new T.MeshBasicMaterial({
    map: glowTex, color: 0x5fc8f0, transparent: true, opacity: 0.46,
    depthWrite: false, side: T.DoubleSide, toneMapped: false,
  }))
  const baseDisc = new T.Mesh(keep(new T.CircleGeometry(0.56, 96)), baseMat)
  baseDisc.rotation.x = -Math.PI / 2
  baseGroup.add(baseDisc)

  // 中层：实心台面（极淡的一层膜，让光池有"面"）
  const basePlateMat = keep(new T.MeshBasicMaterial({
    color: 0x49bce8, transparent: true, opacity: 0.10,
    depthWrite: false, side: T.DoubleSide, toneMapped: false,
  }))
  const basePlate = new T.Mesh(keep(new T.CircleGeometry(0.425, 80)), basePlateMat)
  basePlate.rotation.x = -Math.PI / 2
  basePlate.position.y = 0.001
  baseGroup.add(basePlate)

  // 顶层：两道边界环（外圈定尺寸，内圈托住身体）
  const baseRingMat = keep(new T.MeshBasicMaterial({
    color: 0x4fc0e8, transparent: true, opacity: 0.42,
    depthWrite: false, side: T.DoubleSide, toneMapped: false,
  }))
  const baseRing = new T.Mesh(keep(new T.RingGeometry(0.408, 0.442, 96)), baseRingMat)
  baseRing.rotation.x = -Math.PI / 2
  baseRing.position.y = 0.002
  baseGroup.add(baseRing)

  const baseInnerMat = keep(new T.MeshBasicMaterial({
    color: 0x6fd6f5, transparent: true, opacity: 0.30,
    depthWrite: false, side: T.DoubleSide, toneMapped: false,
  }))
  const baseInner = new T.Mesh(keep(new T.RingGeometry(0.272, 0.286, 80)), baseInnerMat)
  baseInner.rotation.x = -Math.PI / 2
  baseInner.position.y = 0.002
  baseGroup.add(baseInner)

  // 刻度盘（长短刻度交替，像定位刻度）
  const tickPts: number[] = []
  const TICK = 48
  for (let i = 0; i < TICK; i++) {
    const a = (i / TICK) * Math.PI * 2
    const major = i % 6 === 0
    const r0 = major ? 0.448 : 0.482
    const r1 = 0.528
    tickPts.push(Math.cos(a) * r0, 0.001, Math.sin(a) * r0,
                 Math.cos(a) * r1, 0.001, Math.sin(a) * r1)
  }
  const tickGeo = keep(new T.BufferGeometry())
  tickGeo.setAttribute('position', new T.Float32BufferAttribute(tickPts, 3))
  const tickMat = keep(new T.LineBasicMaterial({
    color: 0x7fd8ff, transparent: true, opacity: 0.3, depthWrite: false,
  }))
  baseGroup.add(new T.LineSegments(tickGeo, tickMat))

  // 旋转能量环：带刻度纹理的环带，缓慢自转（素材转动才看得见"在转"）
  const dashMat = keep(new T.MeshBasicMaterial({
    map: bandTex, color: 0x7fd8ff, transparent: true, opacity: 0.55,
    blending: T.AdditiveBlending, depthWrite: false, side: T.DoubleSide, toneMapped: false,
  }))
  const dashRing = new T.Mesh(keep(new T.RingGeometry(0.300, 0.376, 128, 1)), dashMat)
  dashRing.rotation.x = -Math.PI / 2
  dashRing.position.y = 0.003
  baseGroup.add(dashRing)

  // 向上的柔光柱：把底座和身体连成一口气
  const beamMat = keep(new T.MeshBasicMaterial({
    map: glowTex, color: 0x7fd8ff, transparent: true, opacity: 0.10,
    blending: T.AdditiveBlending, depthWrite: false, side: T.DoubleSide,
    toneMapped: false,
  }))
  const beam = new T.Mesh(keep(new T.CylinderGeometry(0.124, 0.376, 0.34, 40, 1, true)), beamMat)
  beam.position.y = 0.17
  baseGroup.add(beam)

  // 定位刻线（四条短刻线 + 一条地平线，保持克制）
  const hudPts: number[] = []
  for (let i = 0; i < 4; i++) {
    const a = (i / 4) * Math.PI * 2 + Math.PI / 4
    const r0 = 0.588, r1 = 0.700
    hudPts.push(Math.cos(a) * r0, BASE_Y + 0.003, Math.sin(a) * r0,
                Math.cos(a) * r1, BASE_Y + 0.003, Math.sin(a) * r1)
  }
  hudPts.push(-0.90, BASE_Y + 0.003, 0, -0.61, BASE_Y + 0.003, 0)
  hudPts.push(0.61, BASE_Y + 0.003, 0, 0.90, BASE_Y + 0.003, 0)
  const hudGeo = keep(new T.BufferGeometry())
  hudGeo.setAttribute('position', new T.Float32BufferAttribute(hudPts, 3))
  const hudMat = keep(new T.LineBasicMaterial({
    color: 0x86dcff, transparent: true, opacity: 0.26, depthWrite: false,
  }))
  scene.add(new T.LineSegments(hudGeo, hudMat))

  // 底座上的柔和投影（身体挡住了上方的光）
  const shadowMat = keep(new T.SpriteMaterial({
    map: contactShadowTexture(T, maxAniso), color: 0x1d5a75,
    transparent: true, depthWrite: false, opacity: 0.07,
  }))
  const contactShadow = new T.Sprite(shadowMat)
  contactShadow.scale.set(0.80, 0.108, 1)
  contactShadow.position.set(0, BASE_Y + 0.006, 0.03)
  scene.add(contactShadow)

  // 实时投影：ShadowMaterial 只接收阴影、不画自身，叠在全息底座上
  if (!isCompact) {
    const shadowPlane = new T.Mesh(
      keep(new T.PlaneGeometry(1.6, 1.6)),
      keep(new T.ShadowMaterial({ color: 0x0a2233, opacity: 0.28 })),
    )
    shadowPlane.rotation.x = -Math.PI / 2
    shadowPlane.position.y = BASE_Y + 0.004
    shadowPlane.receiveShadow = true
    scene.add(shadowPlane)
    // 让机器人所有网格投射阴影（Sprite 粒子/光晕不投影）
    bot.traverse((o: any) => { if (o.isMesh) o.castShadow = true })
    key.castShadow = true
    key.shadow.mapSize.set(1024, 1024)
    const sc = key.shadow.camera as any
    sc.left = -1.2; sc.right = 1.2; sc.top = 1.4; sc.bottom = -1.2
    sc.near = 0.1; sc.far = 8
    sc.updateProjectionMatrix()
    key.shadow.bias = -0.0008
    key.shadow.normalBias = 0.012
    key.shadow.radius = 3
  }

  // ---------- 星点粒子（低速上浮，数量按性能裁）----------
  const perfLow = (navigator.hardwareConcurrency || 4) <= 4
  const particleN = isCompact ? 0 : (perfLow ? 14 : 28)
  const particleData: Array<{ a: number; r: number; y: number; vy: number; va: number }> = []
  const particlePos = new Float32Array(Math.max(1, particleN) * 3)
  for (let i = 0; i < particleN; i++) {
    const a = Math.random() * Math.PI * 2
    const r = 0.34 + Math.random() * 0.30
    const y = -0.62 + Math.random() * 0.92
    particleData.push({ a, r, y, vy: 0.05 + Math.random() * 0.06, va: 0.10 + Math.random() * 0.18 })
    particlePos[i * 3] = Math.cos(a) * r
    particlePos[i * 3 + 1] = y
    particlePos[i * 3 + 2] = Math.sin(a) * r
  }
  const particleGeo = keep(new T.BufferGeometry())
  particleGeo.setAttribute('position', new T.BufferAttribute(particlePos, 3))
  const particleMat = keep(new T.PointsMaterial({
    map: glowTex, color: 0xbfe9ff, size: 0.050, transparent: true, opacity: 0.5,
    sizeAttenuation: true, depthWrite: false, blending: T.AdditiveBlending,
  }))
  const particles = new T.Points(particleGeo, particleMat)
  particles.visible = particleN > 0
  scene.add(particles)

  // 泛光后处理：让眼睛 / 能量核真正发光，而非只靠 Sprite 假光晕
  let composer: any = null
  if (!isCompact && EffectComposer) {
    try {
      composer = new EffectComposer(glr)
      composer.addPass(new RenderPass(scene, camera))
      const bloom = new UnrealBloomPass(
        new T.Vector2(el.clientWidth || 300, el.clientHeight || 300),
        // 浅色页面泛光会糊掉白瓷壳，只留很弱的一层；深色页面才是发光主场
        themeKey === 'light' ? 0.16 : 0.20,
        0.35,  // radius
        1.05,  // threshold：超过 1.0 的 HDR 亮部才发光；白瓷壳(LDR)不参与，避免过曝洗白
      )
      composer.addPass(bloom)
      // 主题切换时同步泛光强度
      cleanups.push(() => { /* bloom 随组件销毁释放 */ })
      ;(scene as any).__botBloom = bloom
      composer.addPass(new OutputPass())
      cleanups.push(() => { composer?.dispose?.() })
    } catch { composer = null }
  }

  drawFn = () => {
    if (!renderer || !scene || !camera) return
    if (composer) composer.render()
    else renderer.render(scene, camera)
  }

  /** 主题套用 */
  function applyTheme() {
    const c = cfg()
    glr.toneMappingExposure = c.exposure
    const bl = (scene as any)?.__botBloom
    if (bl) bl.strength = themeKey === 'light' ? 0.16 : 0.20
    key.color.set(c.key.color); key.intensity = c.key.intensity; key.position.set(...c.key.pos)
    rim.color.set(c.rim.color); rim.intensity = c.rim.intensity; rim.position.set(...c.rim.pos)
    fill.color.set(c.fill.color); fill.intensity = c.fill.intensity; fill.position.set(...c.fill.pos)
    ambient.color.set(c.ambient.color); ambient.intensity = c.ambient.intensity
    hemi.color.set(c.hemi.sky); hemi.groundColor.set(c.hemi.ground); hemi.intensity = c.hemi.intensity

    backdropMat.color.set(c.backdrop.color)
    backdropMat.opacity = c.backdrop.opacity

    shell.color.set(c.shell.color)
    shell.roughness = c.shell.roughness; shell.metalness = c.shell.metalness
    shell.clearcoat = c.shell.clearcoat; shell.clearcoatRoughness = c.shell.clearcoatRoughness
    shell.envMapIntensity = c.shell.env

    // 胸甲/肩甲：同色系但更硬更亮，主题切换时只调反射强度，颜色跟随主壳
    shell2.color.set(c.shell.color)
    shell2.envMapIntensity = c.shell.env + 0.16

    structure.color.set(c.structure.color)
    structure.roughness = c.structure.roughness
    structure.metalness = c.structure.metalness
    structure.envMapIntensity = c.structure.env

    metal.color.set(c.metal.color); metal.metalness = c.metal.metalness
    metal.roughness = c.metal.roughness; metal.envMapIntensity = c.metal.env
    metalDark.color.set(c.metalDark.color); metalDark.metalness = c.metalDark.metalness
    metalDark.roughness = c.metalDark.roughness; metalDark.envMapIntensity = c.metalDark.env

    oled.color.set(c.oled.color); oled.roughness = c.oled.roughness
    oled.emissive.set(c.oled.emissive); oled.emissiveIntensity = c.oled.emissiveIntensity
    oled.envMapIntensity = c.oled.env

    accent.color.set(c.accent.color); accent.emissive.set(c.accent.emissive)
    accent.emissiveIntensity = c.accent.emissiveIntensity; accent.envMapIntensity = c.accent.env

    coreMat.color.set(c.core.emissive).multiplyScalar(1.15)

    eyeMat.color.set(c.eye.color).multiplyScalar(c.eye.hdr)

    shadowMat.opacity = c.shadow.opacity

    // 底座 / 轨道混合模式随主题切换：浅色页面加色会烧成白线，深色页面普通混合会变成黑洞
    const isDark = document.documentElement.getAttribute('data-theme') === 'dark'
    const blend = isDark ? T.AdditiveBlending : T.NormalBlending
    for (const m of [
      baseMat, baseRingMat, baseInnerMat, basePlateMat, bandMat, dashMat,
      orbitMatA, orbitMatC, dotMatA, dotMatC,
    ]) {
      if (m.blending !== blend) { m.blending = blend; m.needsUpdate = true }
    }
  }
  applyTheme()

  // ---------- 尺寸 / 交互 ----------
  const resize = () => {
    if (!renderer || !camera || !el) return
    const w = el.clientWidth || 210
    const h = el.clientHeight || 224
    camera.aspect = w / h
    camera.updateProjectionMatrix()
    renderer.setSize(w, h, false)
    if (composer) composer.setSize(w, h)
  }
  ro = new ResizeObserver(resize)
  ro.observe(el)
  resize()

  const onMove = (e: PointerEvent) => {
    const r = el.getBoundingClientRect()
    mouse.tx = Math.max(-1, Math.min(1, ((e.clientX - r.left) / r.width) * 2 - 1))
    mouse.ty = Math.max(-1, Math.min(1, ((e.clientY - r.top) / r.height) * 2 - 1))
  }
  const onDown = (e: PointerEvent) => {
    const r = el.getBoundingClientRect()
    if (e.clientX >= r.left && e.clientX <= r.right && e.clientY >= r.top && e.clientY <= r.bottom) pulse = 1
  }
  window.addEventListener('pointermove', onMove, { passive: true })
  window.addEventListener('pointerdown', onDown, { passive: true })
  cleanups.push(() => window.removeEventListener('pointermove', onMove))
  cleanups.push(() => window.removeEventListener('pointerdown', onDown))

  reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false

  // ---------- 帧循环 ----------
  let last = performance.now()
  let elapsed = 0
  let nextBlink = 2.4
  let blinkT = -1
  let successT = -1
  const colBlue = new T.Color(0x49c8ff)
  const colRed = new T.Color(0xff5a6e)
  const colMix = new T.Color()
  const coreGlowCol = new T.Color(0x9fe6ff)

  const loop = (now: number) => {
    if (disposed || !renderer || !scene || !camera) return
    const c = cfg()
    const st = normalizeState(curState)
    const fx = STATE_FX[st] ?? STATE_FX.idle
    const dt = Math.min((now - last) / 1000, 0.05)
    last = now
    elapsed += dt
    const t = elapsed
    pulse = Math.max(0, pulse - dt * 1.05)
    errorFlash = Math.max(0, errorFlash - dt * 1.4)
    const err = errorFlash

    // SUCCESS 的一次性点头计时
    if (st === 'success') {
      if (successT < 0) successT = 0
      successT += dt
    } else {
      successT = -1
    }

    // ---------- 悬浮：整体上下浮动；忙碌时幅度收小（像屏息专注）----------
    const bob = Math.sin(t * 1.15) * 0.016 * fx.breath
    bot.position.y = bob
    // 底座不动，所以用"身体离底的高度"反向补偿一个极小的呼吸压缩，避免像贴纸平移
    rig.position.y = Math.sin(t * 1.15 + 0.6) * 0.004 * fx.breath

    // ---------- 注视 / 扫视 / 点头（只转头部，身体保持稳定）----------
    mouse.x += (mouse.tx - mouse.x) * 0.06
    mouse.y += (mouse.ty - mouse.y) * 0.06
    const scan = st === 'thinking' ? Math.sin(t * 2.2) * 0.16 * fx.scan : 0
    // 点头：0.9s 正弦半波，抬起再落下
    const nod = (st === 'success' && successT >= 0 && successT < 0.9)
      ? -Math.sin((successT / 0.9) * Math.PI) * 0.17
      : 0
    headGroup.rotation.y += (mouse.x * 0.26 + scan - headGroup.rotation.y) * 0.07
    headGroup.rotation.x += (-mouse.y * 0.11 + nod - headGroup.rotation.x) * (nod !== 0 ? 0.20 : 0.07)
    // 头部自身还有一点极慢的偏摆，静止时也不"僵住"
    headGroup.rotation.z = Math.sin(t * 0.62) * 0.012 * fx.breath
    // 身体：跟一点点鼠标方向 + 极慢侧摆 + 重心左右转移（像站着呼吸的人）
    rig.rotation.y += (mouse.x * 0.07 - rig.rotation.y) * 0.05
    rig.rotation.z = Math.sin(t * 0.74) * 0.012 * fx.breath
    rig.position.x = Math.sin(t * 0.53) * 0.006 * fx.breath
    // 呼吸：胸腔轻微起伏（纵向缩放），肩臂联动一张一合
    const breathing = Math.sin(t * 1.15) * 0.012 * fx.breath
    rig.scale.set(1 - breathing * 0.35, 1 + breathing, 1 - breathing * 0.35)
    arms.forEach((a, i) => {
      const sx = i === 0 ? -1 : 1
      a.rotation.z = sx * (0.14 + breathing * 0.9)
    })

    // ---------- 眨眼 ----------
    if (blinkT < 0 && t >= nextBlink) blinkT = 0
    let squint = 1
    if (blinkT >= 0) {
      blinkT += dt
      const dur = st === 'thinking' ? 0.18 : 0.14
      const p = Math.min(1, blinkT / dur)
      squint = p < 0.5 ? 1 - p * 2 * 0.9 : 0.1 + (p - 0.5) * 2 * 0.9
      if (p >= 1) {
        blinkT = -1
        nextBlink = t + (st === 'idle' ? 2.4 + Math.random() * 2.4 : 1.4 + Math.random() * 1.4)
      }
    }

    // ---------- 表情：各状态不同的眼型（在眨眼前庭基础上叠加）----------
    // thinking 微眯 + 眼神左右扫；listening 睁大；success 笑眼（压扁上扬）；responding 常规
    const expressionY = st === 'thinking' ? 0.68
      : st === 'listening' ? 1.14
      : st === 'success' ? Math.min(1.08, 0.82 + Math.sin(t * 5) * 0.05)
      : st === 'responding' ? 0.94
      : 1.0
    const expressionX = st === 'listening' ? 1.07 : st === 'success' ? 1.10 : 1.0
    // 眼睛：状态缩放 + 呼吸光晕
    eyes.forEach((e, i) => {
      e.scale.y += (squint * fx.eye * expressionY - e.scale.y) * 0.4
      e.scale.x += (fx.eye * expressionX - e.scale.x) * 0.25
      // 笑眼时轻微上移，像弯起来的眼睛
      e.position.y = 0.014 + (st === 'success' ? 0.006 : 0)
      eyeGlows[i].scale.setScalar((0.19 + pulse * 0.05) * fx.eye * (st === 'listening' ? 1.2 : 1))
      eyeGlowMats[i].opacity = (0.09 + pulse * 0.11) * fx.glow
    })

    // ---------- 颜色（错误红闪）----------
    colMix.copy(colBlue).lerp(colRed, err)
    coreMat.color.copy(colMix).multiplyScalar(1.45)
    tipMat.color.copy(colMix).multiplyScalar(c.tip.hdr)
    coreGlowMat.color.copy(colMix)
    coreRimMat.color.copy(colMix).lerp(coreGlowCol, 0.55)
    corePulseMat.color.copy(colMix).lerp(coreGlowCol, 0.4)
    tipGlowMat.color.copy(colMix)
    eyeGlowMats.forEach(m => m.color.copy(colMix))

    // ---------- 能量核：每个状态一条专属节奏 ----------
    const beatRate = st === 'thinking' ? 7.5
      : st === 'responding' ? 4.2
      : st === 'listening' ? 3.0
      : st === 'success' ? 3.6
      : 2.0
    const beat = 0.7 + Math.sin(t * beatRate) * 0.22 + pulse * 0.30 + err * 0.30
    coreGlowMat.opacity = Math.min(0.42, beat * 0.30 * fx.glow)
    coreGlow.scale.setScalar(0.30 * (0.92 + fx.core * 0.12))
    coreHalo.scale.setScalar(0.54 * (0.90 + fx.core * 0.22))
    coreHaloMat.opacity = (0.05 + beat * 0.055) * fx.glow
    coreRimMat.opacity = (0.30 + beat * 0.25) * fx.glow
    coreGlossMat.opacity = 0.22 + beat * 0.16
    core.scale.setScalar(0.94 + beat * 0.13)
    coreGlass.scale.setScalar(0.98 + beat * 0.045)
    coreGlassMat.opacity = (0.16 + beat * 0.06) * (st === 'success' ? 1.25 : 1)
    // 脉冲：每 1.6s 一圈，向外扩散并淡出；状态越忙扩散越快
    const pulseT = (t * (0.55 + fx.core * 0.28)) % 1
    corePulse.scale.setScalar(0.7 + pulseT * 0.85)
    corePulseMat.opacity = (1 - pulseT) * (1 - pulseT) * 0.30 * fx.glow
    // 内外金属环保持极慢反向转动，高光才会"活"
    coreRim.rotation.z += dt * 0.6 * fx.orbit
    coreInnerRing.rotation.z -= dt * 0.4 * fx.orbit

    // ---------- 三层时空轨道：倾角不同、转速不同、透明度不同 ----------
    orbitGroupA.rotation.z += dt * 0.62 * fx.orbit   // 近身细轨：中速顺转
    bandGroup.rotation.z -= dt * 0.95 * fx.orbit     // 中景全息带：最快、反向
    orbitGroupC.rotation.z += dt * 0.26 * fx.orbit   // 远景空间轨：最慢（越远越慢才合理）
    orbitMatA.opacity = c.fx.orbit * (0.70 + Math.sin(t * 1.8) * 0.14 + pulse * 0.24)
    bandMat.opacity = c.fx.orbit * (0.72 + Math.sin(t * 2.4) * 0.12 + pulse * 0.22)
    orbitMatC.opacity = c.fx.orbit * (0.26 + Math.sin(t * 1.1) * 0.06 + pulse * 0.10)

    // ---------- 三层全息底座 / 光晕 ----------
    baseMat.opacity = c.fx.base * (0.50 + Math.sin(t * 1.2) * 0.07 + pulse * 0.24)
    basePlateMat.opacity = c.fx.base * (0.20 + Math.sin(t * 1.2 + 1.1) * 0.03)
    baseRingMat.opacity = c.fx.base * (0.62 + Math.sin(t * 2.2) * 0.14 + pulse * 0.3)
    baseInnerMat.opacity = c.fx.base * (0.46 + Math.sin(t * 2.2 + 0.8) * 0.10 + pulse * 0.2)
    dashMat.opacity = c.fx.base * (0.55 + Math.sin(t * 1.5) * 0.10 + pulse * 0.22)
    dashRing.rotation.z -= dt * (0.30 + fx.orbit * 0.55) * (0.55 + fx.orbit * 0.7)   // 能量环：忙时加速
    tickMat.opacity = c.fx.hud * (0.80 + Math.sin(t * 0.9) * 0.20)
    hudMat.opacity = c.fx.hud * (0.62 + Math.sin(t * 0.7) * 0.18 + pulse * 0.20)
    beamMat.opacity = c.fx.base * (0.16 + Math.sin(t * 1.6) * 0.04 + pulse * 0.10)
    ambientGlowMat.opacity = c.fx.glow * (0.85 + Math.sin(t * 0.8) * 0.15 + pulse * 0.30)
    shadowMat.opacity = c.shadow.opacity * 0.75

    // ---------- 星点粒子 ----------
    if (particleN > 0) {
      const posAttr = particleGeo.getAttribute('position') as ThreeNS.BufferAttribute
      const drift = 0.7 + fx.orbit * 0.8
      for (let i = 0; i < particleN; i++) {
        const d = particleData[i]
        d.y += d.vy * dt * drift
        d.a += d.va * dt * drift
        if (d.y > 0.30) {
          d.y = -0.62
          d.r = 0.34 + Math.random() * 0.30
        }
        particlePos[i * 3] = Math.cos(d.a) * d.r
        particlePos[i * 3 + 1] = d.y
        particlePos[i * 3 + 2] = Math.sin(d.a) * d.r
      }
      posAttr.needsUpdate = true
      // 整体缓慢公转 + 忙碌时加速
      particles.rotation.y += dt * (0.12 + fx.orbit * 0.35)
      particleMat.opacity = c.fx.dust * (0.85 + pulse * 0.35)
    }

    // ---------- 顶部指示灯 ----------
    lamp.scale.setScalar(0.9 + Math.sin(t * 2.4) * 0.1 + pulse * 0.2)

    // 相机极小视差：保留空间感，但主体始终居中
    camera.position.x += (mouse.x * 0.16 - camera.position.x) * 0.04
    camera.lookAt(0, glbRoot ? 0.24 : lookY, 0)

    // ---------- GLB 主体：状态驱动的呼吸/摇摆/视线/发光 ----------
    if (glbRoot) {
      // 呼吸：纵向起伏更明显，带一点横向"吸气扩张"
      const breath = Math.sin(t * 1.35) * fx.breath
      const baseScale = (isCompact ? 0.78 : 1.08) / glbBaseSize
      glbRoot.scale.set(
        baseScale * (1 + breath * 0.008),
        baseScale * (1 + breath * 0.016),
        baseScale * (1 + breath * 0.008)
      )
      // 悬浮：主浮动 + 状态叠加（聆听轻快上浮，思考下沉）
      const hoverBias = st === 'listening' ? 0.02 : st === 'thinking' ? -0.015 : 0
      const successHop = (st === 'success' && successT >= 0 && successT < 0.9)
        ? Math.sin((successT / 0.9) * Math.PI) * 0.09 : 0
      glbRoot.position.y = glbBaseY + Math.sin(t * 1.35) * 0.022 * fx.breath + hoverBias + successHop
      // 左右重心摇摆：待机慢摆，思考时轻微晃头
      const sway = Math.sin(t * 0.72) * 0.03 * fx.breath
      // 状态专属倾斜：思考歪头、聆听前倾、生成轻微后仰
      const tiltZ = st === 'thinking' ? 0.09 : st === 'listening' ? -0.02 : 0
      const tiltX = st === 'listening' ? 0.06 : st === 'responding' ? -0.025 : 0
      const faceY = glbFaceY + mouse.x * 0.32 + scan
      glbRoot.rotation.y += (faceY - glbRoot.rotation.y) * 0.07
      glbRoot.rotation.z += (sway + tiltZ + nod * 0.5 - glbRoot.rotation.z) * 0.12
      glbRoot.rotation.x += (tiltX + (-mouse.y * 0.05) - glbRoot.rotation.x) * 0.08
      // 发光部件随状态增亮：聆听/生成时更亮
      const glowBoost = st === 'listening' || st === 'responding' || st === 'success' ? 1.35 : 1
      for (const m of glbEyes) {
        const mat = m.material as any
        if (mat?.emissive && mat.emissiveIntensity != null) {
          const base = (mat.userData.__base ??= Math.min(mat.emissiveIntensity, 1.1))
          // 压住发光上限，避免深色主题下眼睛被 bloom 烧成白球
          mat.emissiveIntensity = Math.min(1.4, base * glowBoost * (0.85 + pulse * 0.2))
        }
      }
      // 发光眼：眨眼（squint）+ 状态表情 + 注视微移
      if (glbEyeGroup) {
        const exprY = st === 'thinking' ? 0.62 : st === 'listening' ? 1.15 : st === 'success' ? 0.78 : 1
        glbEyeGroup.scale.y = squint * exprY
        glbEyeGroup.position.x = mouse.x * 0.006
        glbEyeGlows.forEach(sp => {
          const m = sp.material as T.SpriteMaterial
          m.opacity = (0.42 + pulse * 0.18) * glowBoost * squint
        })
      }
      // 扫描线：面罩上下往复
      if (glbScanline) {
        const sy = (glbBaseSize * 0.12)
        glbScanline.position.y = sy + Math.sin(t * 1.4) * glbBaseSize * 0.055
        ;(glbScanline.material as T.MeshBasicMaterial).opacity = (0.25 + 0.25 * Math.sin(t * 2.2)) * fx.glow
      }
    }

    drawFn?.()
    raf = requestAnimationFrame(loop)
  }

  if (reduced) drawFn?.()
  else raf = requestAnimationFrame(loop)
}

function destroy() {
  disposed = true
  if (raf) cancelAnimationFrame(raf)
  ro?.disconnect()
  cleanups.forEach(f => f())
  cleanups.length = 0
  if (scene) {
    scene.traverse(o => {
      const m = o as ThreeNS.Mesh
      m.geometry?.dispose?.()
      const mat = m.material as ThreeNS.Material | ThreeNS.Material[] | undefined
      if (Array.isArray(mat)) mat.forEach(x => x?.dispose?.())
      else mat?.dispose?.()
    })
    scene.environment?.dispose?.()
    scene.clear()
  }
  disposables.forEach(d => { try { d.dispose() } catch { /* 已释放 */ } })
  disposables.length = 0
  renderer?.dispose()
  renderer?.domElement?.remove()
  renderer = null; scene = null; camera = null; drawFn = null
}

onMounted(init)
onBeforeUnmount(destroy)
</script>

<style scoped>
.bot3d { position: relative; width: 100%; height: 100%; }
/* 胸像底部裁在画布边上会像"被切断"；用 mask 渐隐回页面背景（44px 头像不需要） */
.bot3d--fade {
  -webkit-mask-image: linear-gradient(to bottom, #000 92%, rgba(0, 0, 0, .4) 97%, transparent 100%);
  mask-image: linear-gradient(to bottom, #000 92%, rgba(0, 0, 0, .4) 97%, transparent 100%);
}
/* canvas 是 JS append 的，必须用 :deep() 才能命中 scoped 样式 */
.bot3d :deep(canvas) { display: block; width: 100% !important; height: 100% !important; }

/* 无 WebGL：CSS 兜底 */
.bot3d.is-fallback::before {
  content: "";
  position: absolute; left: 50%; top: 46%;
  width: 118px; height: 92px; transform: translate(-50%, -50%);
  border: 2px solid #8fd0f2;
  border-radius: 26px;
  background: linear-gradient(160deg, #f6fbff, #c9e8f8);
  box-shadow: 0 8px 26px rgba(70, 160, 215, .18), inset 0 -8px 18px rgba(70, 160, 215, .1);
}
.bot3d.is-fallback::after {
  content: "AI";
  position: absolute; left: 50%; top: 46%; transform: translate(-50%, -50%);
  color: #2a7fb8;
  font: 700 15px var(--ark-mono, monospace);
  letter-spacing: 2px;
}
</style>
