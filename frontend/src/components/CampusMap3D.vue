<template>
  <div ref="wrap" class="c3d-wrap" :class="{ 'is-intro': intro }">
    <div v-if="!intro" class="c3d-tip">🖱️ 拖拽旋转 · 右键平移 · 滚轮缩放 · 点楼栋或楼名看课程</div>
    <button v-if="!intro" class="c3d-night" :class="{ on: night }" type="button" @click="toggleNight">
      {{ night ? '☀️ 日间' : '🌙 夜景' }}
    </button>
    <div v-if="!ready" class="c3d-loading">3D 场景生成中…</div>

    <!-- 楼名标签（HTML 层，投影跟随相机） -->
    <div v-if="!intro" class="c3d-labels">
      <button
        v-for="l in labelList"
        :key="l.name"
        :ref="(el) => setLabelRef(l.name, el as HTMLElement | null)"
        class="c3d-lbl"
        :class="{ has: l.count > 0, today: l.today > 0, sel: selected === l.name }"
        @click="select(l.name)"
      >
        <b>{{ l.name }}</b>
        <em v-if="l.count">{{ l.count }} 节<template v-if="l.today"> · 今天</template></em>
      </button>
    </div>

    <!-- 点击楼栋 → 课程卡 -->
    <div v-if="!intro && selected" class="c3d-detail">
      <div class="c3d-detail-head">
        <b>📍 {{ selected }}</b>
        <button class="c3d-close" @click="select(null)">✕</button>
      </div>
      <div class="c3d-detail-list">
        <div v-for="(c, i) in selectedCourses" :key="i" class="c3d-course">
          <b>{{ c.name }}</b>
          <span>{{ c.teacher || '—' }} · {{ c.classroom }} · 周{{ '日一二三四五六'[c.dayOfWeek] }} {{ periodText(c.period) }}</span>
        </div>
        <div v-if="!selectedCourses.length" class="c3d-nodata">这栋楼这学期没有你的课</div>
      </div>
      <!-- 导航：从当前楼到目标楼 -->
      <div class="c3d-nav">
        <span>🧭 从「{{ selected }}」到</span>
        <select v-model="navTarget" @change="drawNav">
          <option value="">选择目的地…</option>
          <option v-for="b in navChoices" :key="b" :value="b">{{ b }}</option>
        </select>
        <button v-if="navTarget" @click="clearNav">清除</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  CAMPUS_BUILDINGS, CAMPUS_COURTS, CAMPUS_FIELD, CAMPUS_GATE, CAMPUS_LAKE,
  CAMPUS_BADMINTON, CAMPUS_LINKS, CAMPUS_PLAZA, CAMPUS_ROADS, CAMPUS_TEACH_PLAZA, CAMPUS_TREES,
  CAMPUS_PALMS, CAMPUS_DORM_BLOCKS,
  GRID_H, GRID_UNIT, GRID_W, ORANGE,
  bldHeight, groupByBuilding, type CampusBld,
} from '@/utils/campusConfig'

/**
 * items：课表（楼栋角标与点击课程卡）
 * intro：登录过场「落地校园」纯展示模式——隐藏交互 UI、禁用轨道控制、镜头从高空缓缓推近
 */
const props = defineProps<{ items: any[]; intro?: boolean }>()
const emit = defineEmits<{ (e: 'select', name: string | null): void; (e: 'ready'): void }>()

const wrap = ref<HTMLDivElement | null>(null)
const ready = ref(false)
/** 夜景开关：默认日间，只在交互模式下可切换；不影响登录过场 */
const night = ref(false)
let applyNight: ((on: boolean) => void) | null = null
function toggleNight() {
  night.value = !night.value
  applyNight?.(night.value)
}
const todayDow = new Date().getDay() || 7

const usage = computed(() => groupByBuilding(props.items, todayDow))
const selected = ref<string | null>(null)
const selectedCourses = computed(() => (selected.value ? usage.value[selected.value]?.courses || [] : []))

/** 楼名标签数据（3D 位置由 render 时投影同步） */
const labelList = computed(() =>
  CAMPUS_BUILDINGS.map((b) => {
    const u = usage.value[b.name]
    return { name: b.name, count: u?.count || 0, today: u?.today || 0 }
  }),
)

const navTarget = ref('')
const navChoices = CAMPUS_BUILDINGS.map(b => b.name).sort()
let drawNavFn: ((to: string) => void) | null = null
let clearNavFn: (() => void) | null = null
function drawNav() { if (navTarget.value) drawNavFn?.(navTarget.value) }
function clearNav() { navTarget.value = ''; clearNavFn?.() }

function select(name: string | null) {
  selected.value = name
  if (!name) { navTarget.value = ''; clearNavFn?.() }
  emit('select', name)
  paintSelection()
}
/** 供课表联动：定位并高亮某栋楼（paintSelection 为空时由场景初始化后兜底重绘） */
function focusBuilding(name: string) {
  selected.value = name
  paintSelection()
}
defineExpose({ focusBuilding })
function periodText(p: string) {
  return ({ '1-2': '一二节', '3-4': '三四节', '5-6': '五六节', '7-8': '七八节', '9-10': '九十节' } as Record<string, string>)[p] || p
}

/* ===== 标签 DOM refs ===== */
const labelEls = new Map<string, HTMLElement>()
function setLabelRef(name: string, el: HTMLElement | null) {
  if (el) labelEls.set(name, el)
  else labelEls.delete(name)
}

/* ===== three.js 场景（动态 import，进本页才加载）===== */
let dispose: (() => void) | null = null
let setSelectedMaterial: ((name: string | null) => void) | null = null
let paintSelection: () => void = () => {}

onMounted(async () => {
  const wrapEl = wrap.value
  if (!wrapEl) return
  try {
    const THREE = await import('three')
    const { OrbitControls } = await import('three/addons/controls/OrbitControls.js')
    const { RoomEnvironment } = await import('three/addons/environments/RoomEnvironment.js')

    const G = GRID_UNIT
    const W = () => wrapEl.clientWidth || 600
    const H = () => wrapEl.clientHeight || 520
    const isDark = () => document.documentElement.getAttribute('data-theme') === 'dark'
    /** 过场模式固定用白天光照/天空，深色主题下也能得到明媚的校园日照 */
    const darkMode = () => (props.intro ? false : isDark())

    /* ---------- 渲染器 ---------- */
    const scene = new THREE.Scene()
    const camera = new THREE.PerspectiveCamera(48, W() / H(), 0.5, 1200)

    const renderer = new THREE.WebGLRenderer({ antialias: true, powerPreference: 'high-performance', preserveDrawingBuffer: true })
    const glInfo = renderer.getContext()
    const dbgExt = glInfo.getExtension('WEBGL_debug_renderer_info')
    const gpuName = String(dbgExt ? glInfo.getParameter(dbgExt.UNMASKED_RENDERER_WEBGL) : '')
    const lite = /swiftshader|software|basic render/i.test(gpuName) // 无 GPU 环境降一档

    renderer.setPixelRatio(lite ? 1 : Math.min(2, window.devicePixelRatio))
    renderer.setSize(W(), H())
    renderer.shadowMap.enabled = true
    renderer.shadowMap.type = THREE.PCFSoftShadowMap
    renderer.toneMapping = (THREE as any).NeutralToneMapping || THREE.ACESFilmicToneMapping
    renderer.toneMappingExposure = 1.06
    wrapEl.appendChild(renderer.domElement)

    /* ---------- 环境光照（PBR 反射，让玻璃/屋顶有质感）---------- */
    try {
      const pmrem = new THREE.PMREMGenerator(renderer)
      const envScene = new RoomEnvironment()
      const envRT = pmrem.fromScene(envScene as any, 0.04)
      scene.environment = envRT.texture
      ;(scene as any).environmentIntensity = 0.38
      pmrem.dispose()
    } catch { /* 环境贴图失败不影响主流程 */ }

    /* ---------- 天空渐变 ---------- */
    function skyTexture(dark: boolean, night = false, equirect = true) {
      const W2 = 1024, H2 = 512
      const cv = document.createElement('canvas')
      cv.width = W2; cv.height = H2
      const c = cv.getContext('2d')!
      const g = c.createLinearGradient(0, 0, 0, H2)
      if (night) {
        g.addColorStop(0, '#070d1e'); g.addColorStop(0.48, '#111e3c')
        g.addColorStop(0.86, '#1d3052'); g.addColorStop(1, '#3c4a63')
      } else if (dark) {
        g.addColorStop(0, '#141d30'); g.addColorStop(0.46, '#22304a'); g.addColorStop(1, '#3a4762')
      } else {
        g.addColorStop(0, '#7db4e8'); g.addColorStop(0.38, '#c8e3f7'); g.addColorStop(0.72, '#e8f2fa'); g.addColorStop(1, '#f6f0dc')
      }
      c.fillStyle = g; c.fillRect(0, 0, W2, H2)

      if (night) {
        // 星点 + 月亮：集中在画面上半部到中部（相机俯视时可见的天区）
        for (let i = 0; i < 520; i++) {
          const x = Math.random() * W2, y = Math.random() * H2 * 0.5
          const r = Math.random() * 1.2 + 0.3
          c.fillStyle = `rgba(235,245,255,${0.35 + Math.random() * 0.6})`
          c.beginPath(); c.arc(x, y, r, 0, 7); c.fill()
        }
        // 几颗亮星带十字光芒
        for (const [bx, by] of [[0.22, 0.18], [0.52, 0.12], [0.84, 0.26]] as Array<[number, number]>) {
          const x = W2 * bx, y = H2 * by
          c.strokeStyle = 'rgba(255,255,255,.7)'; c.lineWidth = 1
          c.beginPath(); c.moveTo(x - 6, y); c.lineTo(x + 6, y); c.moveTo(x, y - 6); c.lineTo(x, y + 6); c.stroke()
          c.fillStyle = '#ffffff'; c.beginPath(); c.arc(x, y, 2, 0, 7); c.fill()
        }
        const mx = W2 * 0.72, my = H2 * 0.3
        const mg = c.createRadialGradient(mx, my, 0, mx, my, 62)
        mg.addColorStop(0, 'rgba(255,250,225,.95)'); mg.addColorStop(0.15, 'rgba(255,248,220,.8)')
        mg.addColorStop(1, 'rgba(255,245,210,0)')
        c.fillStyle = mg; c.beginPath(); c.arc(mx, my, 62, 0, 7); c.fill()
        c.fillStyle = '#fffbe8'; c.beginPath(); c.arc(mx, my, 16, 0, 7); c.fill()
      } else {
        // 云层：多层椭圆晕染，覆盖整幅贴图（俯视机位只看到天区一条，需全高分布）
        const cloudBands = dark ? 30 : 52
        for (let i = 0; i < cloudBands; i++) {
          const cx = Math.random() * W2
          const cy = 30 + Math.random() * H2 * 0.72
          const rx = 70 + Math.random() * 150
          const ry = 14 + Math.random() * 26
          const alpha = dark ? 0.1 : 0.26 + Math.random() * 0.22
          c.save(); c.translate(cx, cy); c.scale(1, ry / rx)
          const cg = c.createRadialGradient(0, 0, 0, 0, 0, rx)
          cg.addColorStop(0, `rgba(255,255,255,${alpha})`)
          cg.addColorStop(0.6, `rgba(255,255,255,${alpha * 0.55})`)
          cg.addColorStop(1, 'rgba(255,255,255,0)')
          c.fillStyle = cg; c.beginPath(); c.arc(0, 0, rx, 0, 7); c.fill(); c.restore()
        }
        // 太阳光晕
        const sx = W2 * 0.18, sy = H2 * 0.2
        const sg = c.createRadialGradient(sx, sy, 0, sx, sy, 90)
        sg.addColorStop(0, 'rgba(255,248,214,.75)'); sg.addColorStop(1, 'rgba(255,245,200,0)')
        c.fillStyle = sg; c.beginPath(); c.arc(sx, sy, 90, 0, 7); c.fill()
      }
      const t = new THREE.CanvasTexture(cv)
      t.colorSpace = THREE.SRGBColorSpace
      if (equirect) t.mapping = THREE.EquirectangularReflectionMapping
      return t
    }
    let sky = skyTexture(darkMode())
    scene.background = sky
    let domeTex = skyTexture(darkMode(), false, false)

    // 天空穹顶：俯视机位下 scene.background 只采样到贴图窄窄一段，
    // 云/星月几乎不可见。改用独立球体（BackSide + 普通 UV）。
    const skyDomeMat = new THREE.MeshBasicMaterial({
      map: domeTex, side: THREE.BackSide, depthWrite: false, fog: false,
    })
    const skyDome = new THREE.Mesh(new THREE.SphereGeometry(500, 40, 24), skyDomeMat)
    skyDome.renderOrder = -1000
    scene.add(skyDome)

    /* ---------- 灯光 ---------- */
    const hemi = new THREE.HemisphereLight(darkMode() ? 0x9fb6dd : 0xdcefff, darkMode() ? 0x2a3346 : 0x7a9c58, darkMode() ? 0.42 : 0.38)
    scene.add(hemi)

    // 主光：暖色斜射，拉长建筑投影，得到明显的日照方向感
    const sun = new THREE.DirectionalLight(darkMode() ? 0xbcd0ff : 0xffedcc, darkMode() ? 2.0 : 2.9)
    sun.position.set((GRID_W * G) / 2 + 92, 48, (GRID_H * G) / 2 + 62)
    sun.target.position.set((GRID_W * G) / 2, 0, (GRID_H * G) / 2)
    sun.castShadow = true
    const SS = lite ? 1024 : 3072
    sun.shadow.mapSize.set(SS, SS)
    const sc = sun.shadow.camera as any
    sc.left = -78; sc.right = 78; sc.top = 78; sc.bottom = -78
    sc.near = 1; sc.far = 300
    sc.updateProjectionMatrix()
    sun.shadow.bias = -0.0005
    sun.shadow.normalBias = 0.018
    sun.shadow.radius = 2.2   // 柔和半影，避免硬边阴影
    scene.add(sun)
    scene.add(sun.target)

    // 冷色轮廓光：勾出建筑背光面的边缘
    const fill = new THREE.DirectionalLight(darkMode() ? 0x6f8dcc : 0xa9d4ff, darkMode() ? 0.3 : 0.32)
    fill.position.set(-52, 26, -44)
    scene.add(fill)

    /* ---------- 材质工具 ---------- */
    const std = (color: number, opt: any = {}) =>
      new THREE.MeshStandardMaterial({ color, roughness: 0.88, metalness: 0.03, ...opt })

    const glassMat = new THREE.MeshStandardMaterial({
      color: 0xa9cbe3, roughness: 0.06, metalness: 0.55, envMapIntensity: 2.1,
    })

    /**
     * 一层楼一格的墙面贴图。四种立面风格让 22 栋楼不再共用同一张双联窗：
     *   ribbon  教学群：通长横向玻璃带 + 白色竖向遮阳板（航拍里的现代教学楼）
     *   classic 公共楼：双联窗 + 窗间壁柱 + 窗下墙分格
     *   curtain 图书馆 / 培训中心：玻璃幕墙 + 细竖梃
     *   brick   钟楼：红砖错缝 + 拱窗
     * 贴图一格 = 世界 2.4 × 1.05 单位（宽高比 2.29:1），保证窗型不被拉伸。
     */
    type WallStyle = 'ribbon' | 'classic' | 'curtain' | 'brick'
    const wallTexCache = new Map<string, any>()
    function wallTexture(color: string, style: WallStyle = 'classic', night = false) {
      const key = `${color}|${style}|${night ? 'n' : 'd'}`
      const hit = wallTexCache.get(key)
      if (hit) return hit
      const W2 = 256, H2 = 112
      const cv = document.createElement('canvas')
      cv.width = W2; cv.height = H2
      const c = cv.getContext('2d')!
      c.fillStyle = color
      c.fillRect(0, 0, W2, H2)

      // 墙面微颗粒
      for (let i = 0; i < 560; i++) {
        c.fillStyle = `rgba(${Math.random() > 0.5 ? '255,255,255' : '120,104,82'},.04)`
        c.fillRect(Math.random() * W2, Math.random() * H2, 2, 2)
      }

      /** 一块玻璃：窗洞阴影 + 渐变玻璃 + 斜向高光 + 亮色窗框 */
      const glass = (x: number, y: number, w: number, h: number, deep = false) => {
        c.fillStyle = 'rgba(88,76,60,.22)'
        c.fillRect(x - 2, y - 2, w + 4, h + 4)
        const g = c.createLinearGradient(x, y, x + w * 0.4, y + h)
        g.addColorStop(0, deep ? '#c6e6f7' : '#e0f0fb')
        g.addColorStop(0.32, deep ? '#82b6d4' : '#aad0e6')
        g.addColorStop(0.64, deep ? '#4c7d9f' : '#7ba9c5')
        g.addColorStop(1, deep ? '#2c5573' : '#527c99')
        c.fillStyle = g
        c.fillRect(x, y, w, h)
        c.save()
        c.beginPath(); c.rect(x, y, w, h); c.clip()
        c.fillStyle = 'rgba(255,255,255,.26)'
        c.beginPath()
        c.moveTo(x + w * 0.02, y + h); c.lineTo(x + w * 0.38, y)
        c.lineTo(x + w * 0.56, y); c.lineTo(x + w * 0.20, y + h)
        c.closePath(); c.fill()
        c.restore()
        c.strokeStyle = '#faf6ed'; c.lineWidth = 3
        c.strokeRect(x + 1.5, y + 1.5, w - 3, h - 3)
      }

      if (style === 'ribbon') {
        // 通长横向玻璃带（上下留实墙），白色竖向遮阳板打断长窗
        c.fillStyle = 'rgba(255,255,255,.55)'; c.fillRect(0, 8, W2, 5)
        c.fillStyle = 'rgba(150,132,108,.15)'; c.fillRect(0, 13, W2, 13)
        glass(8, 26, W2 - 16, 58)
        for (let i = 1; i < 5; i++) {
          const x = 8 + (W2 - 16) * i / 5
          c.fillStyle = '#f7f2e6'; c.fillRect(x - 3, 24, 6, 62)
          c.fillStyle = 'rgba(90,76,58,.14)'; c.fillRect(x + 3, 24, 2, 62)
        }
        c.fillStyle = 'rgba(255,255,255,.48)'; c.fillRect(0, 86, W2, 4)
        c.fillStyle = 'rgba(140,122,98,.12)'; c.fillRect(0, 91, W2, 9)
        c.fillStyle = 'rgba(118,102,80,.20)'; c.fillRect(0, 103, W2, 7)
        c.fillStyle = 'rgba(255,255,255,.5)'; c.fillRect(0, 100, W2, 2.5)
      } else if (style === 'curtain') {
        // 玻璃幕墙：细竖梃 + 层间白带
        c.fillStyle = 'rgba(126,106,84,.18)'; c.fillRect(0, 0, W2, 8)
        glass(4, 10, W2 - 8, 84, true)
        for (let i = 1; i < 8; i++) {
          const x = 4 + (W2 - 8) * i / 8
          c.fillStyle = 'rgba(250,246,237,.92)'
          c.fillRect(x - 1.6, 10, 3.2, 84)
        }
        c.fillStyle = 'rgba(250,246,237,.85)'; c.fillRect(0, 48, W2, 4)
        c.fillStyle = 'rgba(255,255,255,.6)'; c.fillRect(0, 6, W2, 3)
        c.fillStyle = 'rgba(118,102,80,.22)'; c.fillRect(0, 102, W2, 8)
      } else if (style === 'brick') {
        // 红砖错缝 + 双联拱窗
        for (let r = 0; r < 7; r++) {
          const y = r * 16
          c.fillStyle = 'rgba(120,60,42,.14)'
          c.fillRect(0, y + 15, W2, 1.4)
          const off = r % 2 ? 16 : 0
          for (let x = -32 + off; x < W2; x += 32) c.fillRect(x, y, 1.2, 15)
        }
        c.fillStyle = '#fbf6ea'
        c.fillRect(12, 18, 104, 74); c.fillRect(140, 18, 104, 74)
        glass(16, 22, 96, 66)
        glass(144, 22, 96, 66)
        c.fillStyle = '#faf6ed'
        c.fillRect(62, 22, 3, 66); c.fillRect(190, 22, 3, 66)
        c.fillStyle = 'rgba(118,102,80,.22)'; c.fillRect(0, 103, W2, 7)
      } else {
        // classic：双联窗 + 窗间壁柱 + 上亮子 + 窗下墙分格
        c.fillStyle = 'rgba(255,255,255,.5)'; c.fillRect(0, 9, W2, 4)
        c.fillStyle = 'rgba(150,132,108,.13)'; c.fillRect(0, 13, W2, 5)
        glass(14, 20, 100, 66)
        glass(142, 20, 100, 66)
        c.fillStyle = '#f8f3e9'
        c.fillRect(62, 20, 3, 66); c.fillRect(190, 20, 3, 66)
        c.fillRect(14, 44, 100, 2.5); c.fillRect(142, 44, 100, 2.5)
        c.fillStyle = 'rgba(255,255,255,.7)'
        c.fillRect(12, 88, 104, 3.5); c.fillRect(140, 88, 104, 3.5)
        c.fillStyle = 'rgba(255,255,255,.42)'
        c.fillRect(0, 16, 9, 74); c.fillRect(124, 16, 9, 74); c.fillRect(247, 16, 9, 74)
        c.fillStyle = 'rgba(140,122,98,.12)'; c.fillRect(0, 93, W2, 3)
        c.fillStyle = 'rgba(118,102,80,.19)'; c.fillRect(0, 104, W2, 8)
        c.fillStyle = 'rgba(255,255,255,.5)'; c.fillRect(0, 101, W2, 2.5)
      }

      if (night) {
        // 夜景版：压暗墙体，按窗格点亮一部分暖黄窗光（同色同风格共用一张贴图）
        c.fillStyle = 'rgba(9,17,32,.68)'
        c.fillRect(0, 0, W2, H2)
        const lit = (x: number, y: number, w: number, h: number, on: boolean) => {
          if (!on) {
            c.fillStyle = 'rgba(38,58,82,.5)'
            c.fillRect(x, y, w, h)
            return
          }
          c.fillStyle = 'rgba(255,196,104,.22)'
          c.fillRect(x - 4, y - 4, w + 8, h + 8)
          const g = c.createLinearGradient(x, y, x, y + h)
          g.addColorStop(0, '#ffe6b0'); g.addColorStop(0.45, '#ffc46d'); g.addColorStop(1, '#e39a43')
          c.fillStyle = g
          c.fillRect(x, y, w, h)
          c.strokeStyle = 'rgba(255,246,222,.65)'; c.lineWidth = 2
          c.strokeRect(x + 1, y + 1, w - 2, h - 2)
        }
        if (style === 'ribbon') {
          const seg = (W2 - 16) / 5
          for (let i = 0; i < 5; i++) lit(8 + i * seg + 3, 28, seg - 6, 54, i % 3 !== 1)
        } else if (style === 'curtain') {
          const seg = (W2 - 8) / 8
          for (let i = 0; i < 8; i++) lit(4 + i * seg + 2, 12, seg - 4, 80, i % 4 !== 2)
        } else if (style === 'brick') {
          lit(16, 22, 96, 66, true); lit(144, 22, 96, 66, false)
        } else {
          lit(14, 20, 100, 66, true); lit(142, 20, 100, 66, false)
        }
        c.fillStyle = 'rgba(170,190,220,.16)'
        c.fillRect(0, 101, W2, 2)
      }

      const t = new THREE.CanvasTexture(cv)
      t.wrapS = t.wrapT = THREE.RepeatWrapping
      t.colorSpace = THREE.SRGBColorSpace
      t.anisotropy = 8
      wallTexCache.set(key, t)
      return t
    }

    /** 草地贴图 */
    function grassTexture() {
      const S = 1024
      const cv = document.createElement('canvas')
      cv.width = cv.height = S
      const c = cv.getContext('2d')!
      // 底层：柔和的绿渐变，模拟阳光下的草坪色差
      const base = c.createLinearGradient(0, 0, S, S)
      base.addColorStop(0, '#8fb95f'); base.addColorStop(0.5, '#9cc76b'); base.addColorStop(1, '#86b158')
      c.fillStyle = base; c.fillRect(0, 0, S, S)
      // 大尺度色斑：修剪痕迹 + 土壤湿度差异
      for (let i = 0; i < 60; i++) {
        const x = Math.random() * S, y = Math.random() * S, r = 40 + Math.random() * 130
        const tone = Math.random() > 0.5 ? '186,222,124' : '112,158,72'
        const g = c.createRadialGradient(x, y, 0, x, y, r)
        g.addColorStop(0, `rgba(${tone},.24)`); g.addColorStop(1, `rgba(${tone},0)`)
        c.fillStyle = g; c.beginPath(); c.arc(x, y, r, 0, 7); c.fill()
      }
      // 细密草丝：短线段，比方块更像真实草
      for (let i = 0; i < 26000; i++) {
        const x = Math.random() * S, y = Math.random() * S
        const len = 2 + Math.random() * 3
        c.strokeStyle = Math.random() > 0.5 ? 'rgba(206,232,158,.30)' : 'rgba(94,142,60,.26)'
        c.lineWidth = 0.8
        c.beginPath(); c.moveTo(x, y); c.lineTo(x + (Math.random() - 0.5), y - len); c.stroke()
      }
      const t = new THREE.CanvasTexture(cv)
      t.wrapS = t.wrapT = THREE.RepeatWrapping
      t.repeat.set(5, 4)
      t.colorSpace = THREE.SRGBColorSpace
      t.anisotropy = 8
      return t
    }
    /** 草地法线贴图：让阳光在草面上有细微起伏的明暗，而非死平面 */
    function grassNormalTexture() {
      const S = 256
      const cv = document.createElement('canvas'); cv.width = cv.height = S
      const c = cv.getContext('2d')!
      c.fillStyle = '#8080ff'; c.fillRect(0, 0, S, S)  // 平法线 (128,128,255)
      for (let i = 0; i < 5000; i++) {
        const x = Math.random() * S, y = Math.random() * S
        const nx = 128 + (Math.random() - 0.5) * 46
        const ny = 128 + (Math.random() - 0.5) * 46
        c.fillStyle = `rgb(${nx|0},${ny|0},255)`
        c.fillRect(x, y, 1, 1)
      }
      const t = new THREE.CanvasTexture(cv)
      t.wrapS = t.wrapT = THREE.RepeatWrapping
      t.repeat.set(30, 24)
      return t
    }

    /* ---------- 沙盘底座 ---------- */
    const groundW = GRID_W * G + 26
    const groundD = GRID_H * G + 24
    const CX = (GRID_W * G) / 2
    const CZ = (GRID_H * G) / 2

    const groundMat = std(0xffffff, {
      map: grassTexture(), normalMap: grassNormalTexture(),
      normalScale: new THREE.Vector2(0.5, 0.5), roughness: 0.96, metalness: 0,
    })
    const ground = new THREE.Mesh(
      new THREE.BoxGeometry(groundW, 2, groundD),
      groundMat,
    )
    ground.position.set(CX, -1, CZ)
    ground.receiveShadow = true
    scene.add(ground)

    // 底座侧壁（土层）+ 台面
    const soil = new THREE.Mesh(new THREE.BoxGeometry(groundW + 2.4, 3.4, groundD + 2.4), std(0xcbb894))
    soil.position.set(CX, -3.6, CZ)
    soil.castShadow = true
    scene.add(soil)
    const plinth = new THREE.Mesh(new THREE.BoxGeometry(groundW + 5, 0.9, groundD + 5), std(0xf4efe0))
    plinth.position.set(CX, -5.7, CZ)
    plinth.receiveShadow = true
    scene.add(plinth)

    /* ---------- 道路 ---------- */
    const roadMat = std(0xe6c3b2, { roughness: 0.95 })
    const walkMat = std(0xf2e6d6, { roughness: 0.95 })
    const dashMat = std(0xfdfaf2, { roughness: 0.9 })
    for (const r of CAMPUS_ROADS) {
      const dx = r.x2 - r.x1, dz = r.y2 - r.y1
      const len = Math.hypot(dx, dz)
      if (len < 0.01) continue
      const rw = r.w || 1
      const px = ((r.x1 + r.x2) / 2) * G
      const pz = ((r.y1 + r.y2) / 2) * G
      // 折线/斜路：按线段方向旋转，接头处略微加长避免露缝
      const angle = -Math.atan2(dz, dx)
      const ux = dx / len, uz = dz / len
      const span = len * G + rw * G * 0.5
      const walk = new THREE.Mesh(new THREE.BoxGeometry(span, 0.1, (rw + 0.42) * G), walkMat)
      walk.position.set(px, 0.05, pz)
      walk.rotation.y = angle
      walk.receiveShadow = true
      scene.add(walk)
      const m = new THREE.Mesh(new THREE.BoxGeometry(span, 0.16, rw * G), roadMat)
      m.position.set(px, 0.1, pz)
      m.rotation.y = angle
      m.receiveShadow = true
      scene.add(m)
      // 主干道中线虚线（沿线段方向排布）
      if (rw >= 0.85) {
        const seg = 1.6, gap = 1.5, n = Math.floor((len * G) / (seg + gap))
        for (let i = 0; i < n; i++) {
          const d = -len * G / 2 + 0.6 + i * (seg + gap) + seg / 2
          const dm = new THREE.Mesh(new THREE.BoxGeometry(seg, 0.02, 0.16), dashMat)
          dm.position.set(px + ux * d, 0.19, pz + uz * d)
          dm.rotation.y = angle
          scene.add(dm)
        }
      }
    }

    /* ---------- 动态车流 / 行人：沿主路往返移动，给沙盘注入生活感 ---------- */
    const movers: Array<{
      obj: THREE.Object3D; ax: number; az: number; bx: number; bz: number;
      t: number; speed: number; dir: number; facing: number;
    }> = []
    {
      const carColors = [0xe05a4f, 0x3f7fc4, 0xf0c04a, 0xe8e8e4, 0x4b5563, 0x7c5cc4]
      const carBodyGeo = new THREE.BoxGeometry(1.5, 0.5, 0.85)
      const carTopGeo = new THREE.BoxGeometry(0.8, 0.36, 0.78)
      const carTopMat = std(0x2a3b4c, { roughness: 0.2, metalness: 0.5 })
      // 主路 + 中部横路 + 南侧横路放车，支路放行人
      CAMPUS_ROADS.forEach((r, ri) => {
        const len = Math.hypot(r.x2 - r.x1, r.y2 - r.y1)
        const ax = r.x1 * G, az = r.y1 * G, bx = r.x2 * G, bz = r.y2 * G
        const isMain = (r.w || 1) >= 0.85
        if (isMain && len > 4) {
          const n = ri === 0 ? 3 : 2
          for (let k = 0; k < n; k++) {
            const g = new THREE.Group()
            const body = new THREE.Mesh(carBodyGeo, std(carColors[(ri + k) % carColors.length], { roughness: 0.35, metalness: 0.45 }))
            body.position.y = 0.48
            g.add(body)
            const top = new THREE.Mesh(carTopGeo, carTopMat)
            top.position.set(-0.1, 0.86, 0)
            g.add(top)
            g.castShadow = true
            scene.add(g)
            movers.push({ obj: g, ax, az, bx, bz, t: (k / n + ri * 0.13) % 1, speed: 0.0009 + (k % 3) * 0.00022, dir: k % 2 ? -1 : 1, facing: 0 })
          }
        } else if (len > 3) {
          // 行人：小圆柱身体 + 球头，颜色随机
          const n = 2
          for (let k = 0; k < n; k++) {
            const g = new THREE.Group()
            const shirt = [0xef6b6b, 0x4a90d9, 0x5cb85c, 0xf0ad4e][(ri + k) % 4]
            const body = new THREE.Mesh(new THREE.CylinderGeometry(0.16, 0.2, 0.72, 8), std(shirt))
            body.position.y = 0.56
            g.add(body)
            const head = new THREE.Mesh(new THREE.SphereGeometry(0.17, 10, 8), std(0xf0c9a0))
            head.position.y = 1.06
            g.add(head)
            scene.add(g)
            movers.push({ obj: g, ax, az, bx, bz, t: (k / n + ri * 0.21) % 1, speed: 0.0004 + (k % 2) * 0.0001, dir: k % 2 ? -1 : 1, facing: 0 })
          }
        }
      })
    }

    /* ---------- 湖（地图：教学群东侧的不规则南北向水面 + 小岛 + 亲水栈桥） ---------- */
    {
      const { cx, cy, rx, rz } = CAMPUS_LAKE
      const X = cx * G, Z = cy * G
      const outline: Array<[number, number]> = [
        [-0.42, -1.0], [0.34, -0.9], [0.66, -0.5], [0.46, -0.08],
        [0.9, 0.3], [0.56, 0.8], [0.02, 1.0], [-0.56, 0.8],
        [-0.8, 0.28], [-0.5, -0.2], [-0.86, -0.62],
      ]
      const shape = (scale: number) => {
        const s = new THREE.Shape()
        outline.forEach(([a, b], i) => {
          const x = a * rx * G * scale, y = b * rz * G * scale
          if (i === 0) s.moveTo(x, y); else s.lineTo(x, y)
        })
        s.closePath()
        return s
      }
      const flat = (sc: number, mat: any, y: number) => {
        const geo = new THREE.ShapeGeometry(shape(sc), 20)
        geo.rotateX(-Math.PI / 2)
        const mesh = new THREE.Mesh(geo, mat)
        mesh.position.set(X, y, Z)
        mesh.receiveShadow = true
        scene.add(mesh)
      }
      flat(1.16, std(0xd9e6c0, { roughness: 1 }), 0.22)   // 草岸
      // 水面：低粗糙度 + 高环境反射，模拟倒影
      const waterMat = new THREE.MeshStandardMaterial({
        color: 0x7ec4e4, roughness: 0.025, metalness: 0.72, envMapIntensity: 2.4,
        transparent: true, opacity: 0.94,
      })
      flat(1.0, waterMat, 0.42)
      // 倒影色块：几片半透明亮斑模拟天空/建筑在水面的反射
      for (let i = 0; i < 5; i++) {
        const refl = new THREE.Mesh(
          new THREE.CircleGeometry(0.3 + Math.random() * 0.5, 16),
          new THREE.MeshBasicMaterial({ color: i % 2 ? 0xe8f4ff : 0xcfe0c0, transparent: true, opacity: 0.12 + Math.random() * 0.1, depthWrite: false })
        )
        refl.rotation.x = -Math.PI / 2
        refl.position.set(X + (Math.random() - 0.5) * rx * G * 1.2, 0.44, Z + (Math.random() - 0.5) * rz * G * 1.2)
        refl.scale.y = 0.5
        scene.add(refl)
      }
      // 湖心小岛 + 树
      const ix = X - rx * G * 0.24, iz = Z + rz * G * 0.18
      const isle = new THREE.Mesh(new THREE.CylinderGeometry(rx * G * 0.3, rx * G * 0.34, 0.5, 20), std(0xc4d99f))
      isle.position.set(ix, 0.44, iz)
      isle.castShadow = true
      scene.add(isle)
      const isleTree = new THREE.Mesh(new THREE.IcosahedronGeometry(0.95, 0), std(0x4f8a44, { flatShading: true }))
      isleTree.position.set(ix, 1.5, iz)
      isleTree.castShadow = true
      scene.add(isleTree)
      // 亲水木栈桥（航拍里的水面平台）
      const deck = new THREE.Mesh(new THREE.BoxGeometry(rx * G * 0.95, 0.14, 0.9), std(0xc7a678, { roughness: 0.9 }))
      deck.position.set(X + rx * G * 0.78, 0.52, Z + rz * G * 0.5)
      deck.castShadow = true
      scene.add(deck)
      for (let i = 0; i < 4; i++) {
        const post = new THREE.Mesh(new THREE.CylinderGeometry(0.07, 0.07, 0.75, 6), std(0x9d7d55))
        post.position.set(X + rx * G * (0.38 + i * 0.26), 0.85, Z + rz * G * 0.5 + 0.42)
        scene.add(post)
      }
    }

    /* ---------- 圆形喷泉广场（航拍：同心圆水池 + 中央景石 + 环形喷柱） ---------- */
    {
      const { cx, cy, r } = CAMPUS_PLAZA
      const X = cx * G, Z = cy * G, R = r * G
      const disc = (rad: number, h: number, y: number, mat: any) => {
        const m = new THREE.Mesh(new THREE.CylinderGeometry(rad, rad, h, 48), mat)
        m.position.set(X, y, Z)
        m.receiveShadow = true
        scene.add(m)
      }
      disc(R, 0.2, 0.11, std(0xf3e9d5))
      disc(R * 0.86, 0.26, 0.14, std(0xe6d8be))
      disc(R * 0.72, 0.3, 0.17, std(0xd9cbb2))
      disc(R * 0.66, 0.42, 0.28, std(0xf8f2e6, { roughness: 0.7 }))       // 池壁
      disc(R * 0.6, 0.2, 0.42, new THREE.MeshStandardMaterial({             // 水面
        color: 0x9fd8ee, roughness: 0.08, metalness: 0.5, envMapIntensity: 1.6,
      }))
      // 中央景石
      const rock = new THREE.Mesh(new THREE.IcosahedronGeometry(R * 0.2, 0), std(0x9d9484, { flatShading: true, roughness: 1 }))
      rock.scale.set(1, 1.35, 0.9)
      rock.position.set(X, 0.42 + R * 0.22, Z)
      rock.castShadow = true
      scene.add(rock)
      // 环形喷柱 + 中心主喷
      const jetMat = new THREE.MeshStandardMaterial({ color: 0xdaf1fb, transparent: true, opacity: 0.6, roughness: 0.1 })
      for (let i = 0; i < 8; i++) {
        const a = (i / 8) * Math.PI * 2
        const jet = new THREE.Mesh(new THREE.CylinderGeometry(0.05, 0.11, 0.85, 10), jetMat)
        jet.position.set(X + Math.cos(a) * R * 0.48, 0.85, Z + Math.sin(a) * R * 0.48)
        scene.add(jet)
      }
      const main = new THREE.Mesh(new THREE.CylinderGeometry(0.08, 0.2, 1.6, 14), jetMat)
      main.position.set(X, 1.35, Z)
      scene.add(main)
    }

    /* ---------- 教学群中央广场 + 羽毛球场 ---------- */
    {
      const P = CAMPUS_TEACH_PLAZA
      const B = CAMPUS_BADMINTON
      const plate = new THREE.Mesh(
        new THREE.BoxGeometry(P.w * G, 0.18, P.d * G),
        std(0xf1e7d3, { roughness: 0.95 }),
      )
      plate.position.set((P.gx + P.w / 2) * G, 0.09, (P.gy + P.d / 2) * G)
      plate.receiveShadow = true
      scene.add(plate)

      const court = new THREE.Mesh(
        new THREE.BoxGeometry(B.w * G, 0.2, B.d * G),
        std(0x9fc57c, { roughness: 0.95 }),
      )
      court.position.set((B.gx + B.w / 2) * G, 0.19, (B.gy + B.d / 2) * G)
      court.receiveShadow = true
      scene.add(court)

      // 场地线
      const lineMat = std(0xf6fbf0, { roughness: 0.8 })
      const x0 = B.gx * G, x1 = (B.gx + B.w) * G
      const z0 = B.gy * G, z1 = (B.gy + B.d) * G
      const bar = (cx: number, cz: number, lx: number, lz: number) => {
        const m = new THREE.Mesh(new THREE.BoxGeometry(lx, 0.03, lz), lineMat)
        m.position.set(cx, 0.3, cz)
        scene.add(m)
      }
      const lw = 0.09
      bar((x0 + x1) / 2, z0 + lw, x1 - x0, lw)
      bar((x0 + x1) / 2, z1 - lw, x1 - x0, lw)
      bar(x0 + lw, (z0 + z1) / 2, lw, z1 - z0)
      bar(x1 - lw, (z0 + z1) / 2, lw, z1 - z0)
      bar((x0 + x1) / 2, (z0 + z1) / 2, x1 - x0, lw)
      bar((x0 + x1) / 2, z0 + (z1 - z0) * 0.28, x1 - x0, lw * 0.8)
      bar((x0 + x1) / 2, z0 + (z1 - z0) * 0.72, x1 - x0, lw * 0.8)
    }

    /* ---------- 操场 ---------- */
    {
      const { cx, cy, rx, rz } = CAMPUS_FIELD
      const track = new THREE.Mesh(new THREE.CylinderGeometry(1, 1, 0.24, 56), std(0xcf6a56))
      track.scale.set(rx * G, 1, rz * G)
      track.position.set(cx * G, 0.13, cy * G)
      track.receiveShadow = true
      scene.add(track)
      const line = new THREE.Mesh(new THREE.CylinderGeometry(1, 1, 0.26, 56), std(0xf4f8ec, { roughness: 0.7 }))
      line.scale.set(rx * G * 0.9, 1, rz * G * 0.86)
      line.position.set(cx * G, 0.15, cy * G)
      scene.add(line)
      const turf = new THREE.Mesh(new THREE.CylinderGeometry(1, 1, 0.3, 56), std(0x93c566))
      turf.scale.set(rx * G * 0.82, 1, rz * G * 0.74)
      turf.position.set(cx * G, 0.17, cy * G)
      turf.receiveShadow = true
      scene.add(turf)
      // 跑道分道线（椭圆环，地图里跑道是竖向长轴）
      const laneMat = std(0xf7f4e8, { roughness: 0.8 })
      for (const f of [0.94, 0.88, 0.83]) {
        const lane = new THREE.Mesh(new THREE.TorusGeometry(1, 0.012, 4, 72), laneMat)
        lane.rotation.x = -Math.PI / 2
        lane.scale.set(rx * G * f, rz * G * f, 1)
        lane.position.set(cx * G, 0.3, cy * G)
        scene.add(lane)
      }
      const mid = new THREE.Mesh(new THREE.BoxGeometry(rx * G * 1.45, 0.02, 0.12), laneMat)
      mid.position.set(cx * G, 0.34, cy * G)
      scene.add(mid)
      const circle = new THREE.Mesh(new THREE.TorusGeometry(0.8, 0.03, 4, 32), laneMat)
      circle.rotation.x = -Math.PI / 2
      circle.position.set(cx * G, 0.34, cy * G)
      scene.add(circle)
    }

    /* ---------- 篮球场 ---------- */
    for (const c of CAMPUS_COURTS) {
      const bw = c.w * G, bd = c.d * G
      const court = new THREE.Mesh(new THREE.BoxGeometry(bw, 0.22, bd), std(0xc25a4b))
      court.position.set((c.gx + c.w / 2) * G, 0.12, (c.gy + c.d / 2) * G)
      court.receiveShadow = true
      scene.add(court)
      const mid = new THREE.Mesh(new THREE.BoxGeometry(bw * 0.94, 0.26, 0.14), std(0xf6f1e4))
      mid.position.set((c.gx + c.w / 2) * G, 0.14, (c.gy + c.d / 2) * G)
      scene.add(mid)
      const hoop = new THREE.Mesh(new THREE.BoxGeometry(0.12, 0.12, bd * 0.42), std(0xf6f1e4))
      hoop.position.set((c.gx + c.w / 2) * G, 0.14, (c.gy + c.d / 2) * G)
      scene.add(hoop)
    }

    /* ---------- 校门 ---------- */
    {
      const x = CAMPUS_GATE.cx * G, z = CAMPUS_GATE.cy * G
      const wallMat = std(0xfdf8ef, { roughness: 0.8 })
      for (const dx of [-2.3, 2.3]) {
        const p = new THREE.Mesh(new THREE.BoxGeometry(0.44, 2.6, 0.44), wallMat)
        p.position.set(x + dx, 1.3, z)
        p.castShadow = true
        scene.add(p)
        for (const fy of [0.8, 1.85]) {
          const band = new THREE.Mesh(new THREE.BoxGeometry(0.48, 0.05, 0.48), std(0xd8a184))
          band.position.set(x + dx, fy, z)
          scene.add(band)
        }
        const cap = new THREE.Mesh(new THREE.BoxGeometry(0.7, 0.18, 0.7), std(0xdcb9a4))
        cap.position.set(x + dx, 2.72, z)
        cap.castShadow = true
        scene.add(cap)
      }
      const beam = new THREE.Mesh(new THREE.BoxGeometry(5.3, 0.66, 0.5), wallMat)
      beam.position.set(x, 2.88, z)
      beam.castShadow = true
      scene.add(beam)
      const capBeam = new THREE.Mesh(new THREE.BoxGeometry(5.6, 0.16, 0.72), std(0xf4ece0))
      capBeam.position.set(x, 3.3, z)
      capBeam.castShadow = true
      scene.add(capBeam)

      const cv = document.createElement('canvas')
      cv.width = 640; cv.height = 128
      const c = cv.getContext('2d')!
      const bg = c.createLinearGradient(0, 0, 0, 128)
      bg.addColorStop(0, '#a13a35'); bg.addColorStop(1, '#7d2823')
      c.fillStyle = bg
      c.fillRect(0, 0, 640, 128)
      c.strokeStyle = 'rgba(247,233,201,.5)'
      c.lineWidth = 3
      c.strokeRect(8, 8, 624, 112)
      c.fillStyle = '#f7e9c9'
      c.font = 'bold 66px "Microsoft YaHei", system-ui, sans-serif'
      c.textAlign = 'center'; c.textBaseline = 'middle'
      c.fillText('厦门华厦学院', 320, 68)
      const tex = new THREE.CanvasTexture(cv)
      tex.colorSpace = THREE.SRGBColorSpace
      const plaque = new THREE.Mesh(new THREE.PlaneGeometry(3.7, 0.74), new THREE.MeshBasicMaterial({ map: tex }))
      plaque.position.set(x, 2.88, z + 0.26)
      scene.add(plaque)
    }

    /* ---------- 树（阔叶 + 针叶混植）---------- */
    {
      const trunkMat = std(0x8b6b46, { roughness: 1 })
      const leafMats = [
        std(0x4e8a44, { flatShading: true }),
        std(0x62a054, { flatShading: true }),
        std(0x3d7a3c, { flatShading: true }),
        std(0x6cae5c, { flatShading: true }),
      ]
      const coneMat = std(0x356b3a, { flatShading: true })
      CAMPUS_TREES.forEach(([tx, tz, tr], i) => {
        const r = tr * G
        const trunk = new THREE.Mesh(new THREE.CylinderGeometry(0.11, 0.17, r * 1.05, 6), trunkMat)
        trunk.position.set(tx * G, r * 0.52, tz * G)
        trunk.castShadow = true
        scene.add(trunk)
        if (i % 5 === 0) {
          // 针叶树
          const c1 = new THREE.Mesh(new THREE.ConeGeometry(r * 0.72, r * 1.5, 7), coneMat)
          c1.position.set(tx * G, r * 1.5, tz * G)
          c1.castShadow = true
          scene.add(c1)
          const c2 = new THREE.Mesh(new THREE.ConeGeometry(r * 0.54, r * 1.2, 7), coneMat)
          c2.position.set(tx * G, r * 2.2, tz * G)
          c2.castShadow = true
          scene.add(c2)
        } else {
          const crown = new THREE.Mesh(new THREE.IcosahedronGeometry(r, 0), leafMats[i % 4])
          crown.scale.set(1, 0.9, 1)
          crown.position.set(tx * G, r * 1.15, tz * G)
          crown.castShadow = true
          scene.add(crown)
          if (i % 3 === 0) {
            const sub = new THREE.Mesh(new THREE.IcosahedronGeometry(r * 0.62, 0), leafMats[(i + 2) % 4])
            sub.position.set(tx * G + r * 0.5, r * 1.5, tz * G - r * 0.3)
            sub.castShadow = true
            scene.add(sub)
          }
        }
      })
    }

    /* ---------- 大王椰子（航拍里成列的棕榈） ---------- */
    {
      const palmTrunk = std(0x9a8b6a, { roughness: 1 })
      const leafMat = std(0x4e8f4a, { flatShading: true, side: THREE.DoubleSide })
      for (const [tx, tz, tr] of CAMPUS_PALMS) {
        const h = tr * G * 2.6
        const trunk = new THREE.Mesh(new THREE.CylinderGeometry(0.09, 0.16, h, 6), palmTrunk)
        trunk.position.set(tx * G, h / 2, tz * G)
        trunk.castShadow = true
        scene.add(trunk)
        for (let k = 0; k < 7; k++) {
          const a = (k / 7) * Math.PI * 2
          const leaf = new THREE.Mesh(new THREE.ConeGeometry(0.4, h * 0.6, 4), leafMat)
          leaf.scale.set(1, 1, 0.22)
          leaf.rotation.set(Math.PI / 2.7, a, 0)
          leaf.position.set(tx * G + Math.cos(a) * h * 0.14, h * 0.96, tz * G + Math.sin(a) * h * 0.14)
          scene.add(leaf)
        }
      }
    }

    /* ---------- 路灯 ---------- */
    let streetBulbMat: any = null
    const glowSprites: any[] = []
    {
      const poleMat = std(0x3f4756, { metalness: 0.5, roughness: 0.5 })
      const bulbMat = new THREE.MeshStandardMaterial({ color: 0xfff0c4, emissive: 0xffd88a, emissiveIntensity: 0.85, roughness: 0.4 })
      streetBulbMat = bulbMat
      // 夜景光晕（加法混合的径向渐变 Sprite，白天隐藏）
      const glowCv = document.createElement('canvas')
      glowCv.width = glowCv.height = 64
      const gc = glowCv.getContext('2d')!
      const gg = gc.createRadialGradient(32, 32, 0, 32, 32, 32)
      gg.addColorStop(0, 'rgba(255,232,175,.95)')
      gg.addColorStop(0.22, 'rgba(255,206,124,.5)')
      gg.addColorStop(1, 'rgba(255,190,100,0)')
      gc.fillStyle = gg
      gc.fillRect(0, 0, 64, 64)
      const glowTex = new THREE.CanvasTexture(glowCv)
      glowTex.colorSpace = THREE.SRGBColorSpace
      const spots: Array<[number, number]> = [
        // 南中主路沿线
        [9.4, 8.6], [9.4, 10.6], [9.4, 12.6], [9.4, 14.6], [9.4, 16.6],
        // 中部横路
        [4.0, 7.9], [7.0, 7.9], [11.0, 7.9], [14.0, 7.9], [17.0, 7.9], [20.0, 7.9],
        // 南部横路 / 运动区支路
        [3.5, 15.7], [6.5, 15.7], [11.2, 15.7], [13.8, 14.5],
        // 西侧沿边界路 / 东侧纵路
        [2.0, 3.0], [2.0, 6.0], [2.0, 9.0], [2.0, 12.0],
        [22.4, 5.0], [22.4, 10.5], [22.4, 15.0],
      ]
      for (const [px, pz] of spots) {
        const pole = new THREE.Mesh(new THREE.CylinderGeometry(0.055, 0.075, 2.5, 8), poleMat)
        pole.position.set(px * G, 1.25, pz * G)
        pole.castShadow = true
        scene.add(pole)
        const head = new THREE.Mesh(new THREE.SphereGeometry(0.2, 10, 8), bulbMat)
        head.position.set(px * G, 2.6, pz * G)
        scene.add(head)
        const glow = new THREE.Sprite(new THREE.SpriteMaterial({
          map: glowTex, transparent: true, blending: THREE.AdditiveBlending,
          depthWrite: false, opacity: 0.9,
        }))
        glow.position.set(px * G, 2.6, pz * G)
        glow.scale.set(2.8, 2.8, 1)
        glow.visible = false
        scene.add(glow)
        glowSprites.push(glow)
      }
    }

    /* ---------- 楼群 ---------- */
    const meshes: Array<{ name: string; body: any; baseEmissive: number }> = []
    const pickables: any[] = []
    const labelAnchors = new Map<string, any>()
    /** 所有窗墙材质：夜景切换时整批替换 map，不额外增加 mesh */
    const wallMats: Array<{ mat: any; day: any; night: any }> = []
    /** 弧形扇面楼的玻璃幕墙（无 map，夜景只调颜色） */
    const fanGlassMats: any[] = []

    const EDGE_COLOR = () => new THREE.LineBasicMaterial({ color: 0x9c8b6e, transparent: true, opacity: 0.28 })
    const EDGE_COLOR_HL = () => new THREE.LineBasicMaterial({ color: 0x6d5fbe, transparent: true, opacity: 0.55 })

    function addRoof(group: any, b: CampusBld, bw: number, bd: number, bh: number, has: boolean) {
      const kind = b.roof || 'gable'
      const color = new THREE.Color(b.roofColor || '#c4694f').getHex()
      const roofMat = std(color, { roughness: 0.78, flatShading: true })
      const trimMat = std(0xf7f2e7, { roughness: 0.85 })

      if (kind === 'gable') {
        const rh = Math.min(bw, 7) * 0.24
        const shape = new THREE.Shape()
        shape.moveTo(-bw / 2 - 0.24, 0)
        shape.lineTo(bw / 2 + 0.24, 0)
        shape.lineTo(0, rh)
        shape.closePath()
        const geo = new THREE.ExtrudeGeometry(shape, { depth: bd + 0.5, bevelEnabled: false })
        geo.translate(0, 0, -(bd + 0.5) / 2)
        const mesh = new THREE.Mesh(geo, roofMat)
        mesh.position.set(0, bh, 0)
        mesh.castShadow = true
        mesh.receiveShadow = true
        group.add(mesh)
        // 屋脊压顶
        const ridge = new THREE.Mesh(new THREE.BoxGeometry(0.26, 0.16, bd + 0.6), trimMat)
        ridge.position.set(0, bh + rh - 0.02, 0)
        ridge.castShadow = true
        group.add(ridge)
        // 封檐板
        for (const s of [-1, 1]) {
          const fascia = new THREE.Mesh(new THREE.BoxGeometry(bw + 0.5, 0.2, 0.16), trimMat)
          fascia.position.set(0, bh + 0.02, s * (bd / 2 + 0.2))
          group.add(fascia)
        }
      } else if (kind === 'hip') {
        const rh = Math.max(bw, bd) * 0.78
        const geo = new THREE.CylinderGeometry(0.02, Math.max(bw, bd) * 0.78, rh, 4)
        geo.rotateY(Math.PI / 4)
        const mesh = new THREE.Mesh(geo, roofMat)
        mesh.position.set(0, bh + rh / 2, 0)
        mesh.castShadow = true
        group.add(mesh)
        const finial = new THREE.Mesh(new THREE.SphereGeometry(0.16, 12, 10), std(0xd8b25f, { metalness: 0.6, roughness: 0.35 }))
        finial.position.set(0, bh + rh + 0.1, 0)
        group.add(finial)
      } else if (kind === 'flat') {
        const parapet = new THREE.Mesh(new THREE.BoxGeometry(bw + 0.5, 0.5, bd + 0.5), roofMat)
        parapet.position.set(0, bh + 0.25, 0)
        parapet.castShadow = true
        parapet.receiveShadow = true
        group.add(parapet)
        const inner = new THREE.Mesh(new THREE.BoxGeometry(bw + 0.2, 0.54, bd + 0.2), std(0xcfc6b4))
        inner.position.set(0, bh + 0.27, 0)
        group.add(inner)

        // 女儿墙压顶：4 条亮色边，俯视时勾出屋顶轮廓
        for (const s of [-1, 1]) {
          const capZ = new THREE.Mesh(new THREE.BoxGeometry(bw + 0.62, 0.12, 0.14), trimMat)
          capZ.position.set(0, bh + 0.54, s * (bd / 2 + 0.22))
          group.add(capZ)
          const capX = new THREE.Mesh(new THREE.BoxGeometry(0.14, 0.12, bd + 0.62), trimMat)
          capX.position.set(s * (bw / 2 + 0.22), bh + 0.54, 0)
          group.add(capX)
        }
        // 屋面铺装分格
        for (let i = 1; i < 3; i++) {
          const seam = new THREE.Mesh(new THREE.BoxGeometry(0.035, 0.02, bd - 0.2), std(0xe6ddca))
          seam.position.set(-bw / 2 + (bw * i) / 3, bh + 0.55, 0)
          group.add(seam)
        }
        // 顶层退台：大面宽教学楼按行交替收进一层玻璃体块，让天际线有高低起伏
        const row = Math.floor(b.gy / 0.85)
        const withSetback = b.wallStyle === 'ribbon' && bw >= 6 && row % 2 === 0
        if (withSetback) {
          const pw = bw * 0.34, pd = bd * 0.52, ph = 1.5
          const glassBox = new THREE.Mesh(
            new THREE.BoxGeometry(pw, ph, pd),
            new THREE.MeshStandardMaterial({ color: 0x8fbcd8, roughness: 0.2, metalness: 0.35, envMapIntensity: 1.7 }),
          )
          glassBox.position.set(bw * 0.28, bh + 0.55 + ph / 2, -bd * 0.12)
          glassBox.castShadow = true
          group.add(glassBox)
          const cap = new THREE.Mesh(new THREE.BoxGeometry(pw + 0.36, 0.16, pd + 0.36), trimMat)
          cap.position.set(bw * 0.28, bh + 0.55 + ph + 0.08, -bd * 0.12)
          cap.castShadow = true
          group.add(cap)
          for (let i = 1; i < 4; i++) {
            const f = new THREE.Mesh(new THREE.BoxGeometry(0.09, ph, 0.1), trimMat)
            f.position.set(bw * 0.28 - pw / 2 + (pw * i) / 4, bh + 0.55 + ph / 2, -bd * 0.12 + pd / 2 + 0.05)
            group.add(f)
          }
        } else {
          // 楼梯间 / 电梯机房：给平顶一个竖向层次
          const shaftW = Math.min(2.3, bw * 0.22), shaftD = Math.min(1.8, bd * 0.42)
          const shaft = new THREE.Mesh(new THREE.BoxGeometry(shaftW, 1.15, shaftD), std(0xeee7d7, { roughness: 0.85 }))
          shaft.position.set(bw * 0.28, bh + 1.1, -bd * 0.16)
          shaft.castShadow = true
          group.add(shaft)
          const shaftCap = new THREE.Mesh(new THREE.BoxGeometry(shaftW + 0.24, 0.14, shaftD + 0.24), std(0xc9bda6))
          shaftCap.position.set(bw * 0.28, bh + 1.72, -bd * 0.16)
          group.add(shaftCap)
        }

        // 屋顶主题：按楼所在「行」成规律分配，同一排楼顶同一种处理，俯视形成节奏而非随机补丁
        const theme = row % 3
        if (theme === 0) {
          // 光伏板：银色设备底框 + 深蓝黑反光面 + 分格线
          const pvMat = new THREE.MeshStandardMaterial({
            color: 0x2b3d50, roughness: 0.24, metalness: 0.5, envMapIntensity: 1.8,
          })
          const pvFrame = std(0xc9d3d8, { metalness: 0.45, roughness: 0.4 })
          const pvW = Math.min(bw * 0.44, 8), pvD = Math.min(bd * 0.56, 3.2)
          const pvBase = new THREE.Mesh(new THREE.BoxGeometry(pvW + 0.2, 0.07, pvD + 0.2), pvFrame)
          pvBase.position.set(-bw * 0.16, bh + 0.58, bd * 0.06)
          group.add(pvBase)
          const pv = new THREE.Mesh(new THREE.BoxGeometry(pvW, 0.09, pvD), pvMat)
          pv.position.set(-bw * 0.16, bh + 0.66, bd * 0.06)
          pv.castShadow = true
          group.add(pv)
          for (let i = 1; i < 4; i++) {
            const line = new THREE.Mesh(new THREE.BoxGeometry(0.045, 0.03, pvD - 0.08), pvFrame)
            line.position.set(-bw * 0.16 - pvW / 2 + (pvW * i) / 4, bh + 0.73, bd * 0.06)
            group.add(line)
          }
          const lineH = new THREE.Mesh(new THREE.BoxGeometry(pvW - 0.08, 0.03, 0.045), pvFrame)
          lineH.position.set(-bw * 0.16, bh + 0.73, bd * 0.06)
          group.add(lineH)
        } else if (theme === 1) {
          // 屋顶花园：米白花坛围边 + 低饱和灰绿草皮 + 小灌木，避免像贴了绿纸
          const gw = Math.min(bw * 0.32, 5), gd = Math.min(bd * 0.42, 2.4)
          const bed = new THREE.Mesh(new THREE.BoxGeometry(gw + 0.26, 0.13, gd + 0.26), std(0xe9e0cd))
          bed.position.set(-bw * 0.16, bh + 0.6, 0)
          group.add(bed)
          const green = new THREE.Mesh(new THREE.BoxGeometry(gw, 0.08, gd), std(0x9bb583, { roughness: 1 }))
          green.position.set(-bw * 0.16, bh + 0.7, 0)
          group.add(green)
          const bushMat = std(0x6f9a56, { roughness: 1 })
          for (let i = 0; i < 2; i++) {
            const bush = new THREE.Mesh(new THREE.SphereGeometry(0.22, 8, 6), bushMat)
            bush.position.set(-bw * 0.16 - gw * 0.22 + i * gw * 0.44, bh + 0.88, gd * 0.12)
            group.add(bush)
          }
        } else {
          // 设备区：机组 + 排气筒
          const u1 = new THREE.Mesh(new THREE.BoxGeometry(bw * 0.24, 0.62, bd * 0.28), std(0xece6d8, { roughness: 0.8 }))
          u1.position.set(-bw * 0.16, bh + 0.85, bd * 0.1)
          u1.castShadow = true
          group.add(u1)
          const u2 = new THREE.Mesh(new THREE.CylinderGeometry(0.2, 0.2, 0.9, 10), std(0xc9c3b4))
          u2.position.set(-bw * 0.02, bh + 1.0, -bd * 0.16)
          group.add(u2)
        }
      } else if (kind === 'arch') {
        const r = bw / 2
        const shape = new THREE.Shape()
        shape.moveTo(-r, 0)
        shape.absarc(0, 0, r, Math.PI, 0, true)
        shape.lineTo(-r, 0)
        const geo = new THREE.ExtrudeGeometry(shape, { depth: bd + 0.4, bevelEnabled: false })
        geo.translate(0, 0, -(bd + 0.4) / 2)
        const mesh = new THREE.Mesh(geo, roofMat)
        mesh.scale.y = 0.6
        mesh.position.set(0, bh, 0)
        mesh.castShadow = true
        group.add(mesh)
        // 拱顶肋
        for (let i = -1; i <= 1; i++) {
          const rib = new THREE.Mesh(new THREE.TorusGeometry(bw / 2, 0.08, 6, 18, Math.PI), trimMat)
          rib.position.set(0, bh, (i * bd) / 3)
          rib.scale.y = 0.6
          group.add(rib)
        }
      } else if (kind === 'round') {
        const drum = new THREE.Mesh(new THREE.CylinderGeometry(bw * 0.54, bw * 0.56, 0.5, 36), trimMat)
        drum.position.set(0, bh + 0.25, 0)
        group.add(drum)
        const dome = new THREE.Mesh(new THREE.SphereGeometry(bw * 0.52, 32, 18, 0, Math.PI * 2, 0, Math.PI / 2), glassMat)
        dome.position.set(0, bh + 0.5, 0)
        dome.scale.y = 0.72
        dome.castShadow = true
        group.add(dome)
        const cap = new THREE.Mesh(new THREE.SphereGeometry(0.24, 12, 10), std(0xd8b25f, { metalness: 0.6, roughness: 0.3 }))
        cap.position.set(0, bh + 1.28, 0)
        group.add(cap)
      }
      void has
    }

    /** 门厅：玻璃幕墙 + 门廊柱 + 雨棚 + 台阶 */
    function addEntrance(group: any, bw: number, bd: number, accentHex: number) {
      const w = Math.min(bw * 0.34, 4.6)
      const h = 2.0
      const hall = new THREE.Mesh(new THREE.BoxGeometry(w, h, 0.9), glassMat)
      hall.position.set(0, h / 2 + 0.4, bd / 2 + 0.42)
      hall.castShadow = true
      group.add(hall)
      // 门框 + 玻璃竖梃
      const frameMat = std(0xf2ece0, { roughness: 0.85 })
      for (const s of [-1, 1]) {
        const post = new THREE.Mesh(new THREE.BoxGeometry(0.14, h, 1.0), frameMat)
        post.position.set((s * w) / 2, h / 2 + 0.4, bd / 2 + 0.42)
        group.add(post)
      }
      for (let i = 1; i < 3; i++) {
        const mullion = new THREE.Mesh(new THREE.BoxGeometry(0.09, h, 0.96), frameMat)
        mullion.position.set(-w / 2 + (w * i) / 3, h / 2 + 0.4, bd / 2 + 0.42)
        group.add(mullion)
      }
      const lintel = new THREE.Mesh(new THREE.BoxGeometry(w + 0.3, 0.16, 1.0), frameMat)
      lintel.position.set(0, h + 0.4, bd / 2 + 0.42)
      group.add(lintel)
      // 雨棚
      const canopy = new THREE.Mesh(new THREE.BoxGeometry(w + 1.0, 0.16, 1.5), std(0xe8ddc8, { roughness: 0.85 }))
      canopy.position.set(0, h + 0.66, bd / 2 + 0.85)
      canopy.castShadow = true
      group.add(canopy)
      // 雨棚前沿强调线（呼应立面橙红构件）
      const edge = new THREE.Mesh(new THREE.BoxGeometry(w + 1.0, 0.1, 0.09), std(accentHex, { roughness: 0.8 }))
      edge.position.set(0, h + 0.6, bd / 2 + 1.58)
      group.add(edge)
      // 门廊柱
      for (const s of [-1, 1]) {
        const col = new THREE.Mesh(new THREE.CylinderGeometry(0.11, 0.13, h + 0.72, 10), frameMat)
        col.position.set(s * (w / 2 + 0.28), (h + 0.72) / 2 + 0.02, bd / 2 + 1.35)
        col.castShadow = true
        group.add(col)
      }
      // 台阶
      for (let i = 0; i < 2; i++) {
        const step = new THREE.Mesh(new THREE.BoxGeometry(w + 1.4 - i * 0.3, 0.2, 1.9 - i * 0.5), std(0xe4dbc7))
        step.position.set(0, 0.3 - i * 0.2, bd / 2 + 0.95 - i * 0.3)
        step.receiveShadow = true
        group.add(step)
      }
    }

    /**
     * 齐礼楼：手绘图里的弧形扇面玻璃楼。
     * 用「外弧 + 内弧」闭合 Shape 拉伸成一段弧形墙带，再补基座、放射状竖框与弧形压顶。
     */
    function fanShape(rOut: number, rIn: number, a0: number, a1: number) {
      const s = new THREE.Shape()
      s.absarc(0, 0, rOut, a0, a1, false)
      s.absarc(0, 0, rIn, a1, a0, true)
      s.closePath()
      return s
    }
    function addFanBuilding(b: CampusBld, group: any, bw: number, bd: number, bh: number, has: boolean) {
      const rOut = Math.max(bw, bd) * 0.56
      const rIn = rOut - Math.min(2.6, bd * 0.82)
      const a0 = Math.PI * 0.12, a1 = Math.PI * 0.88
      const curve = { depth: bh, bevelEnabled: false, curveSegments: 30 } as const

      const bodyGeo = new THREE.ExtrudeGeometry(fanShape(rOut, rIn, a0, a1), { ...curve })
      bodyGeo.rotateX(-Math.PI / 2)   // XY 弧面 → XZ 平面，depth 变成楼高
      const glassWall = new THREE.MeshStandardMaterial({
        color: 0x9dc3dc, roughness: 0.16, metalness: 0.32, envMapIntensity: 1.75,
        emissive: new THREE.Color(has ? 0x4b3f92 : 0x000000),
        emissiveIntensity: has ? 0.12 : 0,
      })
      fanGlassMats.push(glassWall)
      const body = new THREE.Mesh(bodyGeo, [std(0xf1e9d8, { roughness: 0.9 }), glassWall])
      body.position.y = 0.24
      body.castShadow = true
      body.receiveShadow = true
      body.userData.buildingName = b.name
      group.add(body)
      meshes.push({ name: b.name, body, baseEmissive: has ? 0x4b3f92 : 0x000000 })
      pickables.push(body)

      // 浅色台基
      const baseGeo = new THREE.ExtrudeGeometry(fanShape(rOut + 0.18, rIn - 0.18, a0 - 0.02, a1 + 0.02), {
        depth: 0.26, bevelEnabled: false, curveSegments: 30,
      })
      baseGeo.rotateX(-Math.PI / 2)
      const base = new THREE.Mesh(baseGeo, std(0xd9cbb2, { roughness: 0.95 }))
      base.position.y = 0.02
      base.receiveShadow = true
      group.add(base)

      // 弧形女儿墙压顶
      const capGeo = new THREE.ExtrudeGeometry(fanShape(rOut + 0.14, rIn - 0.14, a0 - 0.01, a1 + 0.01), {
        depth: 0.28, bevelEnabled: false, curveSegments: 30,
      })
      capGeo.rotateX(-Math.PI / 2)
      const cap = new THREE.Mesh(capGeo, std(0xf7f2e7, { roughness: 0.85 }))
      cap.position.y = bh + 0.24
      cap.castShadow = true
      group.add(cap)

      // 外弧放射状白色竖框（= 幕墙分格）
      const mullionMat = std(0xf5efe2, { roughness: 0.8 })
      for (let i = 0; i <= 10; i++) {
        const a = a0 + ((a1 - a0) * i) / 10
        const m = new THREE.Mesh(new THREE.CylinderGeometry(0.075, 0.075, bh + 0.1, 8), mullionMat)
        m.position.set(Math.cos(a) * (rOut + 0.02), bh / 2 + 0.24, -Math.sin(a) * (rOut + 0.02))
        m.castShadow = true
        group.add(m)
      }
      // 内弧顶部一条橙红点缀
      const trimMat = std(new THREE.Color(b.accent || ORANGE).getHex(), { roughness: 0.85 })
      for (let i = 0; i <= 8; i++) {
        const a = a0 + ((a1 - a0) * i) / 8
        const t = new THREE.Mesh(new THREE.CylinderGeometry(0.05, 0.05, 0.24, 6), trimMat)
        t.position.set(Math.cos(a) * (rIn + 0.06), bh + 0.42, -Math.sin(a) * (rIn + 0.06))
        group.add(t)
      }
    }

    for (const b of CAMPUS_BUILDINGS) {
      const u = usage.value[b.name]
      const has = !!u?.count
      const bw = b.w * G, bd = b.d * G
      const bh = bldHeight(b.floors)
      const cx = (b.gx + b.w / 2) * G
      const cz = (b.gy + b.d / 2) * G
      const wall = b.wall || '#fbf5ea'
      const isRound = b.roof === 'round'
      const wallStyle: WallStyle = (b.wallStyle || 'classic') as WallStyle
      const accent = new THREE.Color(b.accent || ORANGE).getHex()

      const group = new THREE.Group()
      group.position.set(cx, 0, cz)
      group.userData.buildingName = b.name
      scene.add(group)

      // 弧形扇面楼走独立造型，跳过下面所有方楼专属逻辑
      if (b.shape === 'fan') {
        addFanBuilding(b, group, bw, bd, bh, has)
        labelAnchors.set(b.name, new THREE.Vector3(cx, bh + 2.2, cz + bd / 2))
        continue
      }

      // 勒脚
      const plinthH = 0.42
      if (!isRound) {
        const base = new THREE.Mesh(new THREE.BoxGeometry(bw + 0.34, plinthH, bd + 0.34), std(0xd9cbb2, { roughness: 0.95 }))
        base.position.set(0, plinthH / 2, 0)
        base.castShadow = true
        base.receiveShadow = true
        group.add(base)
      }

      // 主体
      const dayTex = wallTexture(wall, wallStyle, false)
      const nightTex = wallTexture(wall, wallStyle, true)
      const isCurtain = wallStyle === 'curtain'
      const mkSide = (span: number) => {
        const t = dayTex.clone()
        t.needsUpdate = true
        t.repeat.set(Math.max(1, Math.round(span / 2.4)), Math.max(1, b.floors))
        const nt = nightTex.clone()
        nt.needsUpdate = true
        nt.repeat.copy(t.repeat)
        const mat = new THREE.MeshStandardMaterial({
          map: t, color: 0xffffff,
          // 幕墙给一点真实光泽，实墙保持哑光
          roughness: isCurtain ? 0.58 : 0.8,
          metalness: isCurtain ? 0.16 : 0.04,
          envMapIntensity: isCurtain ? 1.35 : 0.9,
          emissive: new THREE.Color(has ? 0x4b3f92 : 0x000000),
          emissiveIntensity: has ? 0.12 : 0,
        })
        wallMats.push({ mat, day: t, night: nt })
        return mat
      }
      // ±x 面朝 z 向，宽度是 bd；±z 面朝 x 向，宽度是 bw
      const sideMatZ = mkSide(bw)
      const sideMatX = mkSide(bd)
      const sideMat = sideMatZ
      const topMat = std(new THREE.Color(wall).getHex(), { roughness: 0.9 })
      const bodyH = bh - (isRound ? 0 : plinthH)
      const geo = isRound
        ? new THREE.CylinderGeometry(Math.min(bw, bd) / 2, Math.min(bw, bd) / 2, bh, 36)
        : new THREE.BoxGeometry(bw, bodyH, bd)
      const body = new THREE.Mesh(geo, isRound ? sideMat : [sideMatX, sideMatX, topMat, topMat, sideMatZ, sideMatZ])
      body.position.set(0, isRound ? bh / 2 : plinthH + bodyH / 2, 0)
      body.castShadow = true
      body.receiveShadow = true
      body.userData.buildingName = b.name
      group.add(body)
      meshes.push({ name: b.name, body, baseEmissive: has ? 0x4b3f92 : 0x000000 })
      pickables.push(body)

      // 檐口
      if (!isRound) {
        const eave = new THREE.Mesh(new THREE.BoxGeometry(bw + 0.42, 0.26, bd + 0.42), std(0xf6f1e6, { roughness: 0.88 }))
        eave.position.set(0, bh - 0.13, 0)
        eave.castShadow = true
        group.add(eave)
      }

      // 描边
      const edges = new THREE.LineSegments(
        isRound
          ? new THREE.EdgesGeometry(new THREE.CylinderGeometry(Math.min(bw, bd) / 2, Math.min(bw, bd) / 2, bh, 18))
          : new THREE.EdgesGeometry(new THREE.BoxGeometry(bw, bodyH, bd)),
        has ? EDGE_COLOR_HL() : EDGE_COLOR(),
      )
      edges.position.copy(body.position)
      group.add(edges)

      // 门厅（非圆楼、非钟楼这种小体量）
      if (!isRound && b.w >= 1.2) addEntrance(group, bw, bd, accent)

      // 教学群 / 高层办公楼：正立面两侧通高玻璃竖带，打破整片墙的单调
      if (!isRound && wallStyle === 'ribbon' && bw >= 6) {
        const sw = Math.min(1.15, bw * 0.09)
        for (const s of [-1, 1]) {
          const stripe = new THREE.Mesh(new THREE.BoxGeometry(sw, bodyH * 0.86, 0.1), glassMat)
          stripe.position.set(s * bw * 0.3, plinthH + bodyH / 2, bd / 2 + 0.07)
          group.add(stripe)
          const capLine = new THREE.Mesh(new THREE.BoxGeometry(sw + 0.16, 0.12, 0.13), std(0xf6f1e6))
          capLine.position.set(s * bw * 0.3, plinthH + bodyH * 0.93, bd / 2 + 0.08)
          group.add(capLine)
        }
      }

      // 航拍实景：米白立面上的橙红竖向构件
      if (!isRound && b.w >= 2.5 && b.floors >= 4) {
        const bandMat = std(accent, { roughness: 0.85 })
        const n = Math.max(2, Math.round(bw / 5))
        for (let i = 0; i < n; i++) {
          const bx = -bw / 2 + (i + 0.5) * bw / n
          for (const s of [-1, 1]) {
            const band = new THREE.Mesh(new THREE.BoxGeometry(0.38, bodyH * 0.9, 0.1), bandMat)
            band.position.set(bx, plinthH + bodyH / 2, s * (bd / 2 + 0.06))
            group.add(band)
          }
        }
      }

      addRoof(group, b, bw, bd, bh, has)

      // 钟楼钟面
      if (b.name === '钟楼') {
        const cv = document.createElement('canvas')
        cv.width = 160; cv.height = 160
        const c = cv.getContext('2d')!
        c.fillStyle = '#fdfaf2'
        c.beginPath(); c.arc(80, 80, 72, 0, Math.PI * 2); c.fill()
        c.strokeStyle = '#b8563f'; c.lineWidth = 7; c.stroke()
        c.strokeStyle = '#6b5540'; c.lineWidth = 3.5
        for (let i = 0; i < 12; i++) {
          const a = (i / 12) * Math.PI * 2
          c.beginPath()
          c.moveTo(80 + Math.cos(a) * 56, 80 + Math.sin(a) * 56)
          c.lineTo(80 + Math.cos(a) * 64, 80 + Math.sin(a) * 64)
          c.stroke()
        }
        c.lineWidth = 6; c.strokeStyle = '#3f3227'
        c.beginPath(); c.moveTo(80, 80); c.lineTo(80, 36); c.stroke()
        c.beginPath(); c.moveTo(80, 80); c.lineTo(110, 92); c.stroke()
        c.beginPath(); c.arc(80, 80, 5, 0, Math.PI * 2); c.fillStyle = '#3f3227'; c.fill()
        const t = new THREE.CanvasTexture(cv)
        t.colorSpace = THREE.SRGBColorSpace
        const face = new THREE.Mesh(
          new THREE.CircleGeometry(Math.min(bw, bd) * 0.34, 28),
          new THREE.MeshBasicMaterial({ map: t, transparent: true }),
        )
        face.position.set(0, bh * 0.76, bd / 2 + 0.03)
        group.add(face)
      }

      // 标签锚点（屋顶上方）
      // 有顶层退台的楼标签再抬高一截，避免压住退台体块
      const tall = b.wallStyle === 'ribbon' && bw >= 6 && Math.floor(b.gy / 0.85) % 2 === 0
      labelAnchors.set(b.name, new THREE.Vector3(cx, bh + (b.roof === 'hip' ? 4.2 : tall ? 2.8 : 2.0), cz + bd / 2))
    }

    /* ---------- 南部斜向生活组团 + 右上角独立公寓区（纯景观体块，不参与课程数据） ----------
       与主楼共用窗墙贴图 + 平顶女儿墙 + 橙红竖向构件，避免从 3/4 视角看成一片白板。 */
    {
      const dormWall = '#f2ebe0'
      const dormRoof = std(0xc9b79c, { roughness: 0.85 })
      const bandMat = std(new THREE.Color(ORANGE).getHex(), { roughness: 0.85 })
      const panelMat = std(0x3d6074, { roughness: 0.35, metalness: 0.28 })
      /** 按面宽 / 层数重复「一层一格」的窗墙贴图；日/夜两套供切换 */
      const dormSide = (span: number, floors: number) => {
        const t = wallTexture(dormWall, 'classic', false).clone()
        t.needsUpdate = true
        t.repeat.set(Math.max(1, Math.round(span / 2.4)), floors)
        const nt = wallTexture(dormWall, 'classic', true).clone()
        nt.needsUpdate = true
        nt.repeat.copy(t.repeat)
        const mat = new THREE.MeshStandardMaterial({ map: t, roughness: 0.88, metalness: 0.02 })
        wallMats.push({ mat, day: t, night: nt })
        return mat
      }
      for (const d of CAMPUS_DORM_BLOCKS) {
        const w = d.w * G, dp = d.d * G, h = bldHeight(d.floors)
        const g = new THREE.Group()
        g.position.set((d.gx + d.w / 2) * G, 0, (d.gy + d.d / 2) * G)
        g.rotation.y = d.angle
        const sideZ = dormSide(w, d.floors)
        const sideX = dormSide(dp, d.floors)
        const body = new THREE.Mesh(
          new THREE.BoxGeometry(w, h, dp),
          [sideX, sideX, dormRoof, dormRoof, sideZ, sideZ],
        )
        body.position.y = h / 2
        body.castShadow = true
        body.receiveShadow = true
        g.add(body)
        // 女儿墙 + 屋顶设备（与教学群平顶同一套语汇）
        const parapet = new THREE.Mesh(new THREE.BoxGeometry(w + 0.28, 0.34, dp + 0.28), dormRoof)
        parapet.position.y = h + 0.17
        parapet.castShadow = true
        g.add(parapet)
        const unit = new THREE.Mesh(new THREE.BoxGeometry(w * 0.24, 0.44, dp * 0.4), std(0xeae2d2))
        unit.position.set(-w * 0.16, h + 0.54, 0)
        unit.castShadow = true
        g.add(unit)
        // 屋顶光伏板：高机位俯视时把大片平顶打散，不会看成一条空白长板
        for (let i = 0; i < 4; i++) {
          const panel = new THREE.Mesh(new THREE.BoxGeometry(w * 0.17, 0.06, dp * 0.46), panelMat)
          panel.position.set(-w * 0.34 + i * w * 0.21, h + 0.38, -dp * 0.14)
          panel.castShadow = true
          g.add(panel)
        }
        // 长立面上的橙红竖向构件
        for (let i = 0; i < 3; i++) {
          const bx = -w / 2 + (i + 0.5) * w / 3
          for (const s of [-1, 1]) {
            const band = new THREE.Mesh(new THREE.BoxGeometry(0.22, h * 0.86, 0.08), bandMat)
            band.position.set(bx, h / 2, s * (dp / 2 + 0.05))
            g.add(band)
          }
        }
        scene.add(g)
      }
      // 手绘地图里没有右上角独立公寓区，故不再绘制远景平台
    }

    /* ---------- 双楼连廊：教学群每组两栋之间的玻璃连廊 ---------- */
    {
      const FLOOR_H = 1.05
      const glassPane = () => new THREE.MeshStandardMaterial({
        color: 0x9cc6e2, roughness: 0.05, metalness: 0.3, envMapIntensity: 1.8,
        transparent: true, opacity: 0.82,
      })
      const slabMat = std(0xdde9f0, { roughness: 0.82 })
      const frameMat = std(0x7f9db4, { roughness: 0.5, metalness: 0.35 })

      for (const lk of CAMPUS_LINKS) {
        const bw = lk.w * G
        const bd = lk.d * G
        const y0 = lk.floor * FLOOR_H
        const bh = 1.0

        const g = new THREE.Group()
        g.position.set((lk.gx + lk.w / 2) * G, 0, (lk.gy + lk.d / 2) * G)
        g.userData.buildingName = lk.a
        scene.add(g)

        // 楼板 + 顶板（略微伸进两侧楼体，视觉上「连上了」）
        const slab = new THREE.Mesh(new THREE.BoxGeometry(bw + 0.34, 0.2, bd + 0.34), slabMat)
        slab.position.set(0, y0, 0)
        slab.castShadow = true; slab.receiveShadow = true
        g.add(slab)
        const roof = new THREE.Mesh(new THREE.BoxGeometry(bw + 0.56, 0.16, bd + 0.56), slabMat)
        roof.position.set(0, y0 + bh, 0)
        roof.castShadow = true
        g.add(roof)

        // 玻璃幕墙（南北两面）+ 竖梃
        const nBay = Math.max(2, Math.round(bw / 1.15))
        for (const s of [-1, 1]) {
          const pane = new THREE.Mesh(new THREE.BoxGeometry(bw, bh - 0.24, 0.06), glassPane())
          pane.position.set(0, y0 + bh / 2, s * (bd / 2 + 0.01))
          pane.castShadow = true
          g.add(pane)
          for (let i = 0; i <= nBay; i++) {
            const mull = new THREE.Mesh(new THREE.BoxGeometry(0.08, bh - 0.22, 0.1), frameMat)
            mull.position.set(-bw / 2 + (bw / nBay) * i, y0 + bh / 2, s * (bd / 2 + 0.01))
            g.add(mull)
          }
          // 扶手
          const rail = new THREE.Mesh(new THREE.BoxGeometry(bw + 0.06, 0.07, 0.13), frameMat)
          rail.position.set(0, y0 + 0.16, s * (bd / 2 + 0.01))
          g.add(rail)
        }
        // 一层实墙（连廊做成两层：一层砖墙 + 二层玻璃）
        const base = new THREE.Mesh(new THREE.BoxGeometry(bw + 0.12, y0, bd + 0.12), std(0xf7f1e4, { roughness: 0.9 }))
        base.position.set(0, y0 / 2, 0)
        base.castShadow = true; base.receiveShadow = true
        g.add(base)
        // 勒脚
        const plinth = new THREE.Mesh(new THREE.BoxGeometry(bw + 0.26, 0.34, bd + 0.26), std(0xd9cbb2, { roughness: 0.95 }))
        plinth.position.set(0, 0.17, 0)
        plinth.receiveShadow = true
        g.add(plinth)
        // 一层窗洞（南北两面各两个）
        for (const s of [-1, 1]) {
          for (const k of [-1, 1]) {
            const win = new THREE.Mesh(new THREE.BoxGeometry(bw * 0.26, 0.62, 0.06), glassMat)
            win.position.set(k * bw * 0.24, 0.62, s * (bd / 2 + 0.07))
            g.add(win)
          }
        }
      }
    }

    /* ===== 相机控制 ===== */
    const controls = new OrbitControls(camera, renderer.domElement)
    // 交互模式抬高机位：北部椭圆跑道才不会被 6 层教学群整个挡住。
    // 登录过场沿用原机位——intro 的俯冲 / 环绕脚本是按这个起点算的，改动会破坏过场构图。
    if (props.intro) camera.position.set(64, 48, 67)
    else camera.position.set(72, 72, 62)
    controls.target.set(CX, 1.0, CZ + (props.intro ? 2 : 0))
    controls.enableDamping = true
    controls.dampingFactor = 0.08
    controls.minDistance = 24
    controls.maxDistance = 210
    controls.maxPolarAngle = Math.PI / 2.06
    controls.update()
    if (props.intro) controls.enabled = false   // 过场模式：禁用手势，镜头交给脚本

    /* ===== 标签投影 ===== */
    const tmpV = new THREE.Vector3()
    function syncLabels() {
      const w = W(), h = H()
      const list: Array<{ el: HTMLElement; x: number; y: number; behind: boolean }> = []
      for (const [name, anchor] of labelAnchors) {
        const el = labelEls.get(name)
        if (!el) continue
        tmpV.copy(anchor).project(camera)
        list.push({
          el,
          x: (tmpV.x * 0.5 + 0.5) * w,
          y: (-tmpV.y * 0.5 + 0.5) * h,
          behind: tmpV.z > 1,
        })
      }
      // 由远及近逐排避让，密集的教学群才读得清
      list.sort((a, b) => a.y - b.y || a.x - b.x)
      const placed: Array<{ x: number; y: number; w: number; h: number }> = []
      for (const it of list) {
        if (it.behind) {
          it.el.style.opacity = '0'
          it.el.style.pointerEvents = 'none'
          continue
        }
        const lw = it.el.offsetWidth || 62
        const lh = it.el.offsetHeight || 18
        const hitAt = (yy: number) =>
          placed.find((q) => Math.abs(q.x - it.x) < (lw + q.w) / 2 && Math.abs(q.y - yy) < (lh + q.h) / 2)
        let y = it.y
        // 教学群 7 行标签很密，多退几步逐排避让；仍冲突时再做一次左右错位
        for (let step = 0; step < 5; step++) {
          if (!hitAt(y) || y - (lh + 1) < 16) break
          y -= lh + 1
        }
        let x = it.x
        if (placed.find((q) => Math.abs(q.x - x) < (lw + q.w) / 2 && Math.abs(q.y - y) < (lh + q.h) / 2)) {
          const shift = lw * 0.62 + 4
          for (const sign of [-1, 1]) {
            if (!placed.find((q) => Math.abs(q.x - (x + sign * shift)) < (lw + q.w) / 2 && Math.abs(q.y - y) < (lh + q.h) / 2)) {
              x += sign * shift
              break
            }
          }
        }
        placed.push({ x, y, w: lw, h: lh })
        it.el.style.transform = `translate3d(${x.toFixed(1)}px, ${y.toFixed(1)}px, 0) translate(-50%, -100%)`
        it.el.style.opacity = '1'
        it.el.style.pointerEvents = 'auto'
      }
    }

    // 导航箭头（由 drawNavFn 填充）
    const navArrows: Array<{ mesh: THREE.Mesh; t: number; pts: THREE.Vector3[]; curve: any }> = []

    // 车流/行人常驻动画（轻量，只在组件挂载期间运行）
    let moverRaf = 0
    let moverT = 0
    let animateMovers = () => {
      moverT += 1
      for (const m of movers) {
        m.t += m.speed * m.dir
        if (m.t > 1) m.t -= 1
        if (m.t < 0) m.t += 1
        const x = m.ax + (m.bx - m.ax) * m.t
        const z = m.az + (m.bz - m.az) * m.t
        m.obj.position.set(x, 0, z)
        // 朝向行进方向
        const dx = (m.bx - m.ax) * m.dir, dz = (m.bz - m.az) * m.dir
        m.obj.rotation.y = Math.atan2(-dz, dx)
      }
      // 导航箭头沿路线流动
      for (const a of navArrows) {
        a.t = (a.t + 0.004) % 1
        a.mesh.position.copy(a.curve.getPoint(a.t))
        const tan = a.curve.getTangent(a.t)
        a.mesh.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), tan.normalize())
      }
      // 必须每帧重绘：只更新 Object3D 位置不会自动刷新画面。
      // intro 过场由 fly() 自行驱镜渲染，此处跳过避免抢占。
      if (!props.intro) renderer.render(scene, camera)
      moverRaf = requestAnimationFrame(animateMovers)
    }
    moverRaf = requestAnimationFrame(animateMovers)

    let labelRaf = 0
    let loopRaf = 0
    let loopOn = false
    function render() {
      renderer.render(scene, camera)
      cancelAnimationFrame(labelRaf)
      labelRaf = requestAnimationFrame(syncLabels)
    }

    /** 阻尼惯性：交互开始/结束后连续渲染直至相机收敛 */
    function loop() {
      if (loopOn) return
      loopOn = true
      const tick = () => {
        if (!loopOn) return
        const moved = controls.update()
        if (moved) {
          renderer.render(scene, camera)
          syncLabels()
          loopRaf = requestAnimationFrame(tick)
        } else {
          loopOn = false
        }
      }
      loopRaf = requestAnimationFrame(tick)
    }
    controls.addEventListener('start', loop)
    controls.addEventListener('end', loop)

    /* ===== 点击拾取 ===== */
    const ray = new THREE.Raycaster()
    const ptr = new THREE.Vector2()
    let downAt: { x: number; y: number } | null = null
    function nameOf(obj: any): string | null {
      let o = obj
      while (o) {
        if (o.userData?.buildingName) return o.userData.buildingName as string
        o = o.parent
      }
      return null
    }
    function onDown(e: PointerEvent) { downAt = { x: e.clientX, y: e.clientY } }
    function onUp(e: PointerEvent) {
      if (!downAt) return
      const moved = Math.hypot(e.clientX - downAt.x, e.clientY - downAt.y)
      downAt = null
      if (moved > 5) return
      const rect = renderer.domElement.getBoundingClientRect()
      ptr.x = ((e.clientX - rect.left) / rect.width) * 2 - 1
      ptr.y = -((e.clientY - rect.top) / rect.height) * 2 + 1
      ray.setFromCamera(ptr, camera)
      const hit = ray.intersectObjects(pickables, false)[0]
      select(hit ? nameOf(hit.object) : null)
    }

    paintSelection = () => {
      for (const m of meshes) {
        const mm = m.body.material as any
        const isSel = selected.value === m.name
        const target = isSel ? 0x6366f1 : m.baseEmissive
        const inten = isSel ? 0.5 : (m.baseEmissive ? 0.12 : 0)
        if (Array.isArray(mm)) {
          // 方楼是 6 面材质，弧形扇面楼只有 [顶盖, 幕墙] 两项；逐项安全处理
          for (const mat of mm) {
            if (!mat?.emissive) continue
            mat.emissive.set(target)
            mat.emissiveIntensity = inten
          }
        } else if (mm?.emissive) {
          mm.emissive.set(target)
          mm.emissiveIntensity = inten
        }
      }
      renderer.render(scene, camera)
    }
    setSelectedMaterial = paintSelection
    // 场景初始化完成时补一次绘制，覆盖初始化期间通过 focusBuilding 预设的选中
    paintSelection()

    renderer.domElement.addEventListener('pointerdown', onDown)
    renderer.domElement.addEventListener('pointerup', onUp)

    /* ===== 尺寸自适应 ===== */
    const ro = new ResizeObserver(() => {
      camera.aspect = W() / H()
      camera.updateProjectionMatrix()
      renderer.setSize(W(), H())
      render()
    })
    ro.observe(wrapEl)

    /* ===== 日/夜 + 深浅主题：统一光照与窗墙贴图切换 ===== */
    function applyLighting(dark: boolean, on: boolean) {
      sky.dispose()
      sky = skyTexture(dark, on)
      scene.background = sky
      domeTex.dispose()
      domeTex = skyTexture(dark, on, false)
      skyDomeMat.map = domeTex
      skyDomeMat.needsUpdate = true
      hemi.color.set(on ? 0x546da8 : dark ? 0x9fb6dd : 0xdfeeff)
      hemi.groundColor.set(on ? 0x1b2a44 : dark ? 0x2a3346 : 0x8fac6a)
      hemi.intensity = on ? 0.26 : (dark ? 0.42 : 0.38)
      sun.color.set(on ? 0x93aee0 : dark ? 0xbcd0ff : 0xffedcc)
      sun.intensity = on ? 0.42 : dark ? 2.0 : 2.9
      fill.intensity = on ? 0.16 : (dark ? 0.3 : 0.32)
      renderer.toneMappingExposure = on ? 0.92 : 1.04
      ;(scene as any).environmentIntensity = on ? 0.08 : 0.38
      // 草地单独压暗（贴图 × 颜色），夜景下不再是一整块亮绿
      groundMat.color.set(on ? 0x607a52 : 0xffffff)
      for (const w of wallMats) {
        w.mat.map = on ? w.night : w.day
        w.mat.needsUpdate = true
      }
      for (const m of fanGlassMats) m.color.set(on ? 0x5d84a8 : 0x9dc3dc)
      for (const s of glowSprites) s.visible = on
      if (streetBulbMat) streetBulbMat.emissiveIntensity = on ? 2.4 : 0.85
      render()
    }
    applyNight = (on: boolean) => applyLighting(darkMode(), on)
    // 组件重建（2D/3D 切换）后沿用当前开关；微任务避开初始化阶段的变量 TDZ
    queueMicrotask(() => { if (night.value) applyNight?.(true) })

    const mo = new MutationObserver(() => applyLighting(darkMode(), night.value))
    mo.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] })

    /* ---------- 建筑导航路线：选中楼 → 目标楼，沿主路折线 + 流动箭头 ---------- */
    const navGroup = new THREE.Group()
    scene.add(navGroup)
    function clearNavScene() {
      navGroup.traverse((o: any) => { o.geometry?.dispose?.(); o.material?.dispose?.() })
      navGroup.clear()
      navArrows.length = 0
    }
    drawNavFn = (toName: string) => {
      clearNavScene()
      if (!selected.value) return
      const from = CAMPUS_BUILDINGS.find(b => b.name === selected.value)
      const to = CAMPUS_BUILDINGS.find(b => b.name === toName)
      if (!from || !to) return
      const fx = (from.gx + from.w / 2) * G, fz = (from.gy + from.d / 2) * G
      const tx = (to.gx + to.w / 2) * G, tz = (to.gy + to.d / 2) * G
      // 折线：起点 → 主路入口(中部横路 z=7.9G) → 主路交点(x=9.4G) → 终点
      const midZ = 7.9 * G, mainX = 9.4 * G
      const pts = [
        new THREE.Vector3(fx, 0.34, fz),
        new THREE.Vector3(fx, 0.34, midZ),
        new THREE.Vector3(mainX, 0.34, midZ),
        new THREE.Vector3(mainX, 0.34, tz),
        new THREE.Vector3(tx, 0.34, tz),
      ]
      const curve: any = new THREE.CatmullRomCurve3(pts, false, 'catmullrom', 0.1)
      const tube = new THREE.Mesh(
        new THREE.TubeGeometry(curve, 80, 0.11, 6, false),
        new THREE.MeshBasicMaterial({ color: 0x22d3ee, transparent: true, opacity: 0.9 })
      )
      navGroup.add(tube)
      // 流动箭头
      for (let i = 0; i < 6; i++) {
        const a = new THREE.Mesh(
          new THREE.ConeGeometry(0.24, 0.6, 6),
          new THREE.MeshBasicMaterial({ color: 0xecfeff })
        )
        a.rotation.x = Math.PI / 2
        navGroup.add(a)
        navArrows.push({ mesh: a, t: i / 6, pts, curve })
      }
      render()
    }
    clearNavFn = clearNavScene
    ready.value = true
    emit('ready')
    await nextTick()

    if (props.intro) {
      /* ===== intro：高空俯冲推近 → 环绕转向正南（正面平视校园）→ 定格 =====
         两段都是脚本驱镜，不叠加 CSS 变焦。第二段绕 Y 轴做球面弧线，
         相机始终看向校园中心，最后停在正门外的低机位，得到「站在校门口」的正面构图。 */
      const ease = (x: number) => (x < 0.5 ? 2 * x * x : 1 - Math.pow(-2 * x + 2, 2) / 2)

      // A 段终点：3/4 俯侧机位
      const tgtA = controls.target.clone()
      const to = camera.position.clone().multiplyScalar(1.02)
      to.y *= 0.61   // 压低机位：更贴地、楼群有高度感，而不是纯俯瞰沙盘
      const from = to.clone().multiplyScalar(1.95)
      from.y += 30

      // B 段终点：正南面（z 越大越靠南）低机位，横构图正好装下整片校园
      const tgtB = new THREE.Vector3(CX, 4.0, CZ - 2)
      const front = new THREE.Vector3(CX, 17, CZ + 68)

      const DUR_A = 2200
      const GAP = 700        // 落位后短暂停顿，让眼睛先看清 3/4 全景再开始转
      const DUR_B = 3400

      camera.position.copy(from)
      camera.lookAt(tgtA)
      renderer.render(scene, camera)

      const relA = to.clone().sub(tgtA)
      const relB = front.clone().sub(tgtB)
      const rA = Math.hypot(relA.x, relA.z)
      const rB = Math.hypot(relB.x, relB.z)
      let thA = Math.atan2(relA.z, relA.x)
      const thB = Math.atan2(relB.z, relB.x)
      let dth = (thB - thA) % (Math.PI * 2)
      if (dth > Math.PI) dth -= Math.PI * 2
      if (dth < -Math.PI) dth += Math.PI * 2

      const look = new THREE.Vector3()
      const t0 = performance.now()
      const fly = () => {
        if (!wrapEl.isConnected) return
        const el = performance.now() - t0
        if (el <= DUR_A) {
          const k = ease(el / DUR_A)
          camera.position.lerpVectors(from, to, k)
          look.copy(tgtA)
        } else if (el <= DUR_A + GAP) {
          camera.position.copy(to)
          look.copy(tgtA)
        } else {
          const k = ease(Math.min(1, (el - DUR_A - GAP) / DUR_B))
          // 半径 / 高度 / 方位角同步收敛：绕着校园转过去，而不是直线插值穿过楼群
          const r = rA + (rB - rA) * k
          const th = thA + dth * k
          const y = to.y + (front.y - to.y) * k
          camera.position.set(tgtB.x + Math.cos(th) * r, y, tgtB.z + Math.sin(th) * r)
          look.lerpVectors(tgtA, tgtB, k)
        }
        camera.lookAt(look)
        renderer.render(scene, camera)
        if (el < DUR_A + GAP + DUR_B) requestAnimationFrame(fly)
      }
      requestAnimationFrame(fly)
    }

    /* ===== 入场：canvas 由近及远缓出（CSS 过渡，不依赖 rAF）===== */
    if (!props.intro) {
      const cv = renderer.domElement
      cv.style.transformOrigin = '50% 62%'
      cv.style.transition = 'transform 1150ms cubic-bezier(.2,.85,.25,1), opacity 900ms ease'
      cv.style.transform = 'scale(1.2)'
      cv.style.opacity = '0.25'
      const labels = wrapEl.querySelector('.c3d-labels') as HTMLElement | null
      if (labels) {
        labels.style.transition = 'opacity 700ms ease 260ms'
        labels.style.opacity = '0'
      }
      requestAnimationFrame(() => {
        cv.style.transform = 'scale(1)'
        cv.style.opacity = '1'
        if (labels) labels.style.opacity = '1'
        window.setTimeout(() => {
          cv.style.transition = ''
          cv.style.transform = ''
          cv.style.opacity = ''
          if (labels) { labels.style.transition = ''; labels.style.opacity = '' }
        }, 1500)
      })
    }

    /* 调试钩子（无头截图/自检用） */
    ;(window as any).__c3dDbg = {
      tris: () => renderer.info.render.triangles,
      calls: () => renderer.info.render.calls,
      cam: () => camera.position.toArray().map((n: number) => +n.toFixed(1)),
      target: () => controls.target.toArray().map((n: number) => +n.toFixed(1)),
      objects: () => scene.children.length,
      shadow: () => ({ enabled: renderer.shadowMap.enabled, cast: sun.castShadow, map: !!sun.shadow.map }),
      reRender: () => render(),
      /** 调试：一次性摆好机位（x,y,z 相机 / tx,ty,tz 注视点） */
      pose: (x: number, y: number, z: number, tx: number, ty: number, tz: number) => {
        camera.position.set(x, y, z)
        controls.target.set(tx, ty, tz)
        camera.lookAt(tx, ty, tz)
        renderer.render(scene, camera)
        return camera.position.toArray().map((n) => +n.toFixed(1))
      },
      bounds: () => {
        const b = new THREE.Box3().setFromObject(scene)
        return [b.min.toArray().map((n) => +n.toFixed(1)), b.max.toArray().map((n) => +n.toFixed(1))]
      },
    }

    /* ===== 清理 ===== */
    dispose = () => {
      loopOn = false
      applyNight = null
      cancelAnimationFrame(labelRaf)
      cancelAnimationFrame(loopRaf)
      cancelAnimationFrame(moverRaf)
      ro.disconnect()
      mo.disconnect()
      renderer.domElement.removeEventListener('pointerdown', onDown)
      renderer.domElement.removeEventListener('pointerup', onUp)
      controls.dispose()
      scene.traverse((o: any) => {
        o.geometry?.dispose?.()
        if (Array.isArray(o.material)) o.material.forEach((mm: any) => { mm.map?.dispose?.(); mm.dispose?.() })
        else if (o.material) { o.material.map?.dispose?.(); o.material.dispose?.() }
      })
      sky.dispose()
      renderer.dispose()
      renderer.domElement.remove()
    }
  } catch (err: any) {
    ;(window as any).__c3dErr = String(err?.stack || err?.message || err)
    console.error('[CampusMap3D] init failed:', err)
  }
})

/* 课表数据变化 → 重算标签内容 + 重绘高亮 */
watch(() => props.items?.length, async () => {
  await nextTick()
  setSelectedMaterial?.()
})

onBeforeUnmount(() => dispose?.())
</script>

<style scoped>
.c3d-wrap {
  position: relative;
  height: 560px;
  border-radius: 12px;
  overflow: hidden;
  background: linear-gradient(180deg, #dfe7f5, #eef2e2);
}
/* 过场模式：撑满父级、去圆角、底色交给天空 */
.c3d-wrap.is-intro :deep(canvas) { filter: saturate(1.08) contrast(1.045); }
.c3d-wrap.is-intro {
  height: 100%;
  border-radius: 0;
  background: transparent;
}
.c3d-tip {
  position: absolute; top: 10px; left: 12px; z-index: 4;
  font-size: 12px; color: var(--text-light);
  background: color-mix(in srgb, var(--card) 82%, transparent);
  border: 1px solid var(--border); border-radius: 999px; padding: 4px 12px;
  pointer-events: none;
}
.c3d-night {
  position: absolute; top: 10px; right: 12px; z-index: 5;
  font-size: 12px; line-height: 1.2; cursor: pointer; font-family: inherit;
  color: var(--text-light); background: color-mix(in srgb, var(--card) 82%, transparent);
  border: 1px solid var(--border); border-radius: 999px; padding: 4px 12px;
  backdrop-filter: blur(6px);
}
.c3d-night:hover { filter: brightness(1.1); }
.c3d-night.on {
  background: rgba(255,196,102,.9); color: #3a2a12;
  border-color: rgba(255,255,255,.45);
}
.c3d-loading {
  position: absolute; inset: 0; display: flex; align-items: center; justify-content: center;
  font-size: 13px; color: var(--text-light); z-index: 2;
}

/* ===== 楼名标签层 ===== */
.c3d-labels {
  position: absolute; inset: 0; z-index: 3; pointer-events: none;
}
.c3d-lbl {
  position: absolute; top: 0; left: 0;
  display: flex; flex-direction: column; align-items: center; gap: 0;
  padding: 2px 6px; border-radius: 6px;
  background: rgba(24, 33, 49, .72);
  border: 1px solid rgba(255, 255, 255, .24);
  box-shadow: 0 3px 10px rgba(10, 18, 30, .28);
  backdrop-filter: blur(6px);
  color: #fff; cursor: pointer; white-space: nowrap;
  font-family: inherit; line-height: 1.25;
  pointer-events: auto;
  transition: background .16s, border-color .16s, box-shadow .16s, opacity .18s;
}
.c3d-lbl b { font-size: 11.5px; font-weight: 600; letter-spacing: .2px; }
.c3d-lbl em { font-style: normal; font-size: 8px; opacity: .82; letter-spacing: .2px; }
.c3d-lbl:hover {
  background: rgba(79, 70, 229, .88);
  border-color: rgba(199, 210, 254, .9);
}
.c3d-lbl.has {
  background: rgba(55, 48, 163, .82);
  border-color: rgba(165, 180, 252, .85);
  box-shadow: 0 4px 14px rgba(79, 70, 229, .38);
}
.c3d-lbl.has em { opacity: .92; }
.c3d-lbl.today {
  background: rgba(180, 83, 9, .86);
  border-color: rgba(253, 230, 138, .9);
  box-shadow: 0 4px 14px rgba(245, 158, 11, .4);
}
.c3d-lbl.sel {
  background: rgba(37, 99, 235, .95);
  border-color: #fff;
  box-shadow: 0 0 0 3px rgba(96, 165, 250, .45), 0 6px 18px rgba(37, 99, 235, .45);
}

.c3d-detail {
  position: absolute; left: 14px; bottom: 14px; width: 270px;
  background: var(--card); border: 1px solid var(--border); border-radius: 12px;
  box-shadow: 0 10px 30px rgba(0,0,0,.18); padding: 12px 14px; z-index: 5;
}
.c3d-detail-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.c3d-detail-head b { font-size: 13.5px; color: var(--text); }
.c3d-close { background: none; border: none; color: var(--text-light); cursor: pointer; font-size: 13px; }
.c3d-detail-list { display: flex; flex-direction: column; gap: 7px; max-height: 210px; overflow-y: auto; }
.c3d-course { padding: 7px 10px; border: 1px solid var(--border); border-radius: 9px; }
.c3d-course b { display: block; font-size: 12.5px; color: var(--text); }
.c3d-course span { font-size: 12px; color: var(--text-light); }
.c3d-nodata { font-size: 12px; color: var(--text-light); opacity: .8; }
.c3d-nav { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; margin-top: 10px; padding-top: 9px; border-top: 1px solid var(--border); }
.c3d-nav span { font-size: 11px; color: var(--text-light); }
.c3d-nav select {
  flex: 1; min-width: 96px; padding: 4px 7px; border-radius: 6px; font-size: 11px;
  border: 1px solid var(--border); background: var(--card-bg); color: var(--text); font-family: inherit;
}
.c3d-nav button { padding: 4px 8px; border-radius: 6px; font-size: 11px; cursor: pointer;
  border: 1px solid rgba(34,211,238,.5); background: rgba(34,211,238,.12); color: #22d3ee; }
</style>
