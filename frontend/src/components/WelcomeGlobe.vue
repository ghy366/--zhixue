<template>
  <div ref="wrap" class="lg-wrap" aria-hidden="true">
    <canvas
      ref="cv" class="lwg-canvas" :class="{ ready, dragging }"
      @pointerdown="onDown" @pointermove="onMoveP" @pointerup="onUp" @pointercancel="onUp"
    ></canvas>
    <!-- 目标浮签：下钻到集美区时浮现，跟随球面投影 -->
    <transition name="lg-fade">
      <div v-show="level >= 4 && labelVisible" class="lg-pin" :style="labelStyle">
        <i class="lg-pin-ring"></i><i class="lg-pin-dot"></i>
      </div>
    </transition>
    <transition name="lg-fade">
      <div v-show="level >= 4 && labelVisible" class="lg-tag" :class="{ 'is-left': tagSide === 'l' }" :style="labelStyle">
        <i class="lg-tag-line"></i>
        <span class="lg-tag-text">集美区 · 厦门华厦学院</span>
      </div>
    </transition>
    <!-- 点击反馈涟漪 -->
    <span v-for="r in ripples" :key="r.id" class="lg-rip" :style="{ left: r.x + 'px', top: r.y + 'px' }"></span>
  </div>
</template>

<script setup lang="ts">
/**
 * 登录页 3D 真实地球（卫星贴图版）：
 * - NASA Blue Marble + 夜景灯光自发光 + 地形凹凸 + 海洋高光 + 云层 + 大气辉光
 * - 逐级点击下钻：全球 → 全国（中国轮廓）→ 福建省 → 厦门市 → 集美区 → 落地校园
 *   每一级在球面绘制该级行政区边界（DataV.GeoAtlas 抽稀，构建期打包，零运行时外网依赖）
 * - 拖动可自由旋转；点击（位移 < 8px）推进一级
 * - 贴图随构建打包，动态 import('three') 与 CampusMap3D 共享 chunk
 */
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import earthDayUrl from './globe/earth-day.jpg'
import earthNightUrl from './globe/earth-night.jpg'
import earthTopoUrl from './globe/earth-topology.png'
import earthWaterUrl from './globe/earth-water.png'
/* 云层原本存成 .jpg 但实际是带透明通道的 PNG，扩展名错了会导致解码失败、云层永远不显示 */
import earthCloudsUrl from './globe/earth-clouds.png'

const wrap = ref<HTMLDivElement | null>(null)
const cv = ref<HTMLCanvasElement | null>(null)
const ready = ref(false)
const dragging = ref(false)
const level = ref(0)
const labelStyle = ref<any>({})
const labelVisible = ref(false)
const ripples = ref<Array<{ id: number; x: number; y: number }>>([])
const tagSide = ref<'l' | 'r'>('r')   // 浮签朝点位的空侧展开，避免顶出画面

/** 把当前下钻层级抛给外层（欢迎层底部层级条 / 提示文案用） */
const emit = defineEmits<{ (e: 'level', n: number): void; (e: 'done'): void }>()
watch(level, (v) => emit('level', v))

/**
 * 下钻序列：每一级给出「镜头正对该级中心」的经纬度、相机到球面的距离 d 与贴图不透明度。
 * d 从 23.6 一路收到 0.13（贴地级几乎切平面），按球体缩放同步换算成实际 z。
 * tex 随层级淡出：贴地时卫星图被放大几万倍只会糊成色块，改用深色底盘 + 轮廓线。
 */
const LEVELS = [
  { name: '全球',   lat: 22.0,  lng: 108.0,  d: 23.60, tex: 1.00, y: -1.35 },
  { name: '全国',   lat: 34.6,  lng: 104.5,  d: 8.600, tex: 0.92, y: -1.00 },
  { name: '福建省', lat: 25.9,  lng: 118.2,  d: 1.650, tex: 0.52, y: -0.55 },
  { name: '厦门市', lat: 24.62, lng: 118.10, d: 0.170, tex: 0.34, y: -0.14 },
  { name: '集美区', lat: 24.643, lng: 118.045, d: 0.130, tex: 0.22, y: -0.03 },
]
/* d 是「相机到球面的距离」，按 0.64 球体缩放标定；实际 z 会随屏宽缩放同步换算，
   这样窄屏球变小后视野角不变，市级/区级轮廓不会缩成一个小点。 */

// ---------- 拖动/点击交互（模板事件绑定，须在 setup 作用域） ----------
let mx = 0, my = 0
let isDown = false
let moved = 0
let lastX = 0, lastY = 0
let dragOverride = false   // 用户拖过后暂停自动对准，直到下次点击
function onMove(e: MouseEvent) {
  mx = (e.clientX / window.innerWidth) - 0.5
  my = (e.clientY / window.innerHeight) - 0.5
}
function onDown(e: PointerEvent) {
  isDown = true; moved = 0
  lastX = e.clientX; lastY = e.clientY
  dragging.value = true
}
function onUp(e: PointerEvent) {
  const wasDown = isDown
  isDown = false
  dragging.value = false
  if (wasDown && moved < 8) {
    addRipple(e.clientX, e.clientY)
    advance()
  }
}
function onMoveP(e: PointerEvent) {
  onMove(e)
  if (!isDown || !spin || !tilt) return
  const dx = e.clientX - lastX
  const dy = e.clientY - lastY
  moved += Math.abs(dx) + Math.abs(dy)
  lastX = e.clientX; lastY = e.clientY
  spin.rotation.y += dx * 0.005
  tilt.rotation.x = Math.max(-1.1, Math.min(1.1, tilt.rotation.x + dy * 0.004))
}

// ---------- 层级推进 / 闲置兜底 ----------
let idleT: number | null = null
let doneSent = false
function advance() {
  if (doneSent) return
  if (level.value >= LEVELS.length - 1) { finish(); return }
  level.value++
  dragOverride = false
  resetIdle(level.value >= LEVELS.length - 1 ? 2400 : 3800)
}
function finish() {
  if (doneSent) return
  doneSent = true
  if (idleT) { clearTimeout(idleT); idleT = null }
  emit('done')
}
function resetIdle(ms: number) {
  if (idleT) clearTimeout(idleT)
  idleT = window.setTimeout(advance, ms)
}

// 场景节点提升为模块作用域：拖动事件（onMoveP）会在 mount 之外访问
let globe: any
let tilt: any
let spin: any

let disposed = false
let raf = 0
let cleanupFns: Array<() => void> = []
let rippleSeq = 0
function addRipple(x: number, y: number) {
  const id = ++rippleSeq
  ripples.value.push({ id, x, y })
  window.setTimeout(() => {
    ripples.value = ripples.value.filter(r => r.id !== id)
  }, 900)
}

onMounted(async () => {
  const host = wrap.value
  const canvas = cv.value
  if (!host || !canvas) return
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches

  let THREE: any
  try {
    THREE = await import('three')
  } catch { return }
  if (disposed) return

  // ---------- 基础 ----------
  const renderer = new THREE.WebGLRenderer({ canvas, alpha: true, antialias: true })
  renderer.setPixelRatio(Math.min(1.75, window.devicePixelRatio || 1))
  const scene = new THREE.Scene()
  const camera = new THREE.PerspectiveCamera(42, 1, 0.004, 900)
  camera.position.set(0, 0, 30)
  scene.add(camera)

  const R = 10
  globe = new THREE.Group()      // 定位/视差
  tilt = new THREE.Group()       // 纬度俯仰
  spin = new THREE.Group()       // 自转/拖动
  spin.rotation.y = -3.385       // 开场正对中国（lng 104°），点击后下钻更连贯
  tilt.add(spin)
  globe.add(tilt)
  globe.rotation.z = -0.12
  scene.add(globe)

  const disposables: Array<{ dispose: () => void }> = []
  const track = <T extends { dispose: () => void }>(x: T): T => { disposables.push(x); return x }

  const maxAniso = renderer.capabilities.getMaxAnisotropy()
  const loader = new THREE.TextureLoader()
  const loadTex = (url: string, srgb = false) => new Promise<any>((resolve, reject) => {
    loader.load(url, tex => {
      if (srgb) tex.colorSpace = THREE.SRGBColorSpace
      tex.anisotropy = maxAniso
      resolve(tex)
    }, undefined, reject)
  })

  // ---------- 贴图（构建期打包，运行时零外网依赖） ----------
  let dayTex: any = null, nightTex: any = null
  try {
    ;[dayTex, nightTex] = await Promise.all([
      loadTex(earthDayUrl, true),
      loadTex(earthNightUrl, true),
    ])
  } catch { /* 贴图缺失时退化为深蓝球体，功能不受影响 */ }
  if (disposed) return

  // ---------- 灯光：暖调阳光，可见面受光、夜面浮出城市灯光 ----------
  const sun = new THREE.DirectionalLight(0xfff1dc, 3.1)
  sun.position.set(9, 5, 11)
  camera.add(sun)
  scene.add(new THREE.AmbientLight(0x62789a, 0.5))

  // ---------- 深色底盘：贴图淡出后透出（避免看到球体空腔与穿帮） ----------
  const baseMat = track(new THREE.MeshBasicMaterial({ color: 0x06101c }))
  const baseBall = new THREE.Mesh(track(new THREE.SphereGeometry(R * 0.996, 64, 64)), baseMat)
  spin.add(baseBall)

  // ---------- 真实地球：白天贴图 + 夜灯自发光 + 地形凹凸 + 海洋高光 ----------
  const earthMat = track(new THREE.MeshPhongMaterial({
    color: dayTex ? 0xffffff : 0x0a1a30,
    map: dayTex || null,
    emissive: 0xffce7a,
    emissiveMap: nightTex || null,
    emissiveIntensity: nightTex ? 0.9 : 0,
    bumpScale: 0.45,
    specular: new THREE.Color(0x35506e),
    shininess: 15,
    transparent: true,
    opacity: 1,
  }))
  const earth = new THREE.Mesh(track(new THREE.SphereGeometry(R, 96, 96)), earthMat)
  spin.add(earth)

  loadTex(earthTopoUrl).then(tex => { earthMat.bumpMap = tex; earthMat.needsUpdate = true }).catch(() => {})
  loadTex(earthWaterUrl).then(tex => { earthMat.specularMap = tex; earthMat.needsUpdate = true }).catch(() => {})

  // ---------- 云层（独立慢速漂移） ----------
  let cloudsMat: any = null
  loadTex(earthCloudsUrl, true).then(tex => {
    cloudsMat = track(new THREE.MeshLambertMaterial({
      map: tex, transparent: true, opacity: 0.5, depthWrite: false,
    }))
    const clouds = new THREE.Mesh(track(new THREE.SphereGeometry(R * 1.012, 64, 64)), cloudsMat)
    clouds.userData.isClouds = true
    spin.add(clouds)
  }).catch(() => {})

  // ---------- 大气：极薄的边缘散射，不做发光环 ----------
  const atmoMat = track(new THREE.ShaderMaterial({
    transparent: true, side: THREE.BackSide, depthWrite: false, blending: THREE.AdditiveBlending,
    uniforms: { glow: { value: new THREE.Color('#5b93c4') }, fade: { value: 1 } },
    vertexShader: `varying vec3 vN;
      void main() {
        vN = normalize(normalMatrix * normal);
        gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
      }`,
    fragmentShader: `varying vec3 vN; uniform vec3 glow; uniform float fade;
      void main() {
        float intensity = pow(0.58 - dot(vN, vec3(0.0, 0.0, 1.0)), 6.0);
        gl_FragColor = vec4(glow, 1.0) * intensity * 0.55 * fade;
      }`,
  }))
  const atmo = new THREE.Mesh(track(new THREE.SphereGeometry(R * 1.18, 64, 64)), atmoMat)
  globe.add(atmo)

  // ---------- 地理坐标工具 ----------
  const latLng = (lat: number, lng: number, radius = R) => {
    const phi = (90 - lat) * Math.PI / 180
    const theta = (lng + 180) * Math.PI / 180
    return new THREE.Vector3(-radius * Math.sin(phi) * Math.cos(theta), radius * Math.cos(phi), radius * Math.sin(phi) * Math.sin(theta))
  }

  // ---------- 逐级行政区轮廓（球面贴合的等距细分线段 + 面填充） ----------
  type Node = { obj: any; mat: any; glow: any; glowMat: any; fill: any; fillMat: any; lv: number }
  const outlineNodes: Node[] = []
  let materialCtors: any = null
  try {
    const bm: any = await import('./globe/bounds')
    try {
      const [m2, mg, ml] = await Promise.all([
        import('three/examples/jsm/lines/LineSegments2.js'),
        import('three/examples/jsm/lines/LineSegmentsGeometry.js'),
        import('three/examples/jsm/lines/LineMaterial.js'),
      ])
      materialCtors = { L2: (m2 as any).LineSegments2, G: (mg as any).LineSegmentsGeometry, M: (ml as any).LineMaterial }
    } catch { materialCtors = null }

    const keys = ['nation', 'fujian', 'xiamen', 'jimei']
    const step = 0.42 * Math.PI / 180
    const va = new THREE.Vector3(), vb = new THREE.Vector3()
    const vp = new THREE.Vector3(), vPrev = new THREE.Vector3()

    for (let k = 0; k < keys.length; k++) {
      const set = bm.BOUNDS[keys[k]]
      if (!set) continue
      const radius = R * (1.004 + k * 0.0016)
      const seg: number[] = []
      for (const flat of set.rings) {
        const n = flat.length >> 1
        if (n < 3) continue
        va.copy(latLng(flat[1], flat[0], radius))
        vPrev.copy(va)
        for (let i = 1; i <= n; i++) {
          const j = i % n
          vb.copy(latLng(flat[j * 2 + 1], flat[j * 2], radius))
          const ang = va.angleTo(vb)
          const steps = Math.max(1, Math.ceil(ang / step))
          for (let s = 1; s <= steps; s++) {
            vp.copy(va).lerp(vb, s / steps).normalize().multiplyScalar(radius)
            seg.push(vPrev.x, vPrev.y, vPrev.z, vp.x, vp.y, vp.z)
            vPrev.copy(vp)
          }
          va.copy(vb)
        }
      }
      if (!seg.length) continue

      // 面填充：经纬度平面三角化 → 顶点投回球面。只保留主陆域（点数多的 ring），
      // 小岛/礁盘只描边，避免成千上万个碎三角。
      const fills: number[] = []
      const v2: any[] = []
      for (const flat of set.rings) {
        const n = flat.length >> 1
        if (n < 8) continue
        v2.length = 0
        for (let i = 0; i < n; i++) v2.push(new THREE.Vector2(flat[i * 2], flat[i * 2 + 1]))
        if (v2[0].x === v2[n - 1].x && v2[0].y === v2[n - 1].y) v2.pop()
        if (v2.length < 3) continue
        let faces: number[][] = []
        try { faces = THREE.ShapeUtils.triangulateShape(v2, []) } catch { continue }
        for (const f of faces) {
          for (const idx of f) {
            const p = latLng(v2[idx].y, v2[idx].x, radius * 0.9995)
            fills.push(p.x, p.y, p.z)
          }
        }
      }
      let fill: any = null, fillMat: any = null
      if (fills.length) {
        const fg = track(new THREE.BufferGeometry())
        fg.setAttribute('position', new THREE.Float32BufferAttribute(fills, 3))
        fillMat = track(new THREE.MeshBasicMaterial({
          color: 0x63d3ff, transparent: true, opacity: 0, side: THREE.DoubleSide,
          depthWrite: false, depthTest: false,
        }))
        fill = new THREE.Mesh(fg, fillMat)
        fill.renderOrder = 3
        fill.visible = false
        spin.add(fill)
      }

      let obj: any = null, mat: any = null, glow: any = null, glowMat: any = null
      if (materialCtors) {
        const g = new materialCtors.G()
        g.setPositions(seg)
        glowMat = new materialCtors.M({
          color: 0x64a9cc, linewidth: 6, transparent: true, opacity: 0,
          depthWrite: false, depthTest: false, worldUnits: false,
        })
        glowMat.resolution.set(host.clientWidth || window.innerWidth, host.clientHeight || window.innerHeight)
        glow = new materialCtors.L2(g, glowMat)
        glow.renderOrder = 4
        glow.visible = false

        mat = new materialCtors.M({
          color: 0xe6faff, linewidth: 1.55, transparent: true, opacity: 0,
          depthWrite: false, depthTest: false, worldUnits: false,
        })
        mat.resolution.set(host.clientWidth || window.innerWidth, host.clientHeight || window.innerHeight)
        obj = new materialCtors.L2(g, mat)
        obj.renderOrder = 5
      } else {
        const g = new THREE.BufferGeometry()
        g.setAttribute('position', new THREE.Float32BufferAttribute(seg, 3))
        mat = new THREE.LineBasicMaterial({ color: 0xe6faff, transparent: true, opacity: 0, depthWrite: false, depthTest: false })
        obj = new THREE.LineSegments(g, mat)
        obj.renderOrder = 5
        glowMat = null
      }
      obj.visible = false
      spin.add(obj)
      if (glow) spin.add(glow)
      outlineNodes.push({ obj, mat, glow, glowMat, fill, fillMat, lv: k + 1 })
    }
  } catch { /* 轮廓数据缺失时退化为纯地球，不影响下钻 */ }

  // ---------- 目标点（厦门华厦学院）：定位环改用 HTML 叠层，见 .lg-pin ----------
  const TARGET_LL = [24.6165, 118.1045] as const
  const targetPos = latLng(TARGET_LL[0], TARGET_LL[1])

  // ---------- 星野 ----------
  const SN = 760
  const starPos = new Float32Array(SN * 3)
  for (let i = 0; i < SN; i++) {
    const v = new THREE.Vector3().randomDirection().multiplyScalar(55 + Math.random() * 45)
    starPos[i * 3] = v.x; starPos[i * 3 + 1] = v.y; starPos[i * 3 + 2] = v.z
  }
  const starGeo = track(new THREE.BufferGeometry())
  starGeo.setAttribute('position', new THREE.BufferAttribute(starPos, 3))
  const starMat = track(new THREE.PointsMaterial({
    size: 0.62, color: 0xdfe8f5, transparent: true, opacity: 0.72,
    depthWrite: false, sizeAttenuation: true,
  }))
  const stars = new THREE.Points(starGeo, starMat)
  scene.add(stars)

  // ---------- 尺寸与定位 ----------
  let halfWRef = 10
  let baseScale = 0.64
  const resize = () => {
    const w = host.clientWidth || window.innerWidth
    const h = host.clientHeight || window.innerHeight
    renderer.setSize(w, h, false)
    camera.aspect = w / h
    camera.updateProjectionMatrix()
    const halfH = Math.tan(THREE.MathUtils.degToRad(21)) * 30
    halfWRef = halfH * camera.aspect
    const wide = w >= 900
    baseScale = wide ? 0.64 : Math.min(0.64, w / 900) * 0.92
    // 初值直接对齐 tick 里的稳态位置，避免过场开场时地球从别处滑入
    globe.position.x = wide ? -halfWRef * 0.04 : 0
    globe.position.y = -1.35 * baseScale / 0.64
    globe.scale.setScalar(baseScale)
    outlineNodes.forEach(o => { o.mat.resolution?.set(w, h); o.glowMat?.resolution?.set(w, h) })
  }
  resize()

  // ---------- 渲染循环 ----------
  const clock = new THREE.Clock()
  const invSpin = new THREE.Matrix4()
  const vGc = new THREE.Vector3(), vDir = new THREE.Vector3(), vPt = new THREE.Vector3()
  const tick = () => {
    if (disposed) return
    const dt = Math.min(0.05, clock.getDelta())
    const lv = LEVELS[level.value]
    const k = 1 - Math.pow(0.00002, dt)

    // 距离按球体缩放同步换算，保证不同屏宽下的视野角一致；
    // 竖屏再补一点（越往下钻补得越多，否则市级轮廓会横向顶出画面）
    const aspK = camera.aspect < 1 ? Math.pow(1 / camera.aspect, 0.18 + 0.12 * level.value) : 1
    const zGoal = (R + lv.d / 0.64 * aspK) * baseScale
    camera.position.z += (zGoal - camera.position.z) * k
    // 全球态：地球偏下方给问候文字留位；下钻后回中，保证目标点落在画面正中
    const tgtX = level.value === 0 ? -halfWRef * 0.04 : 0
    const tgtY = lv.y * baseScale / 0.64
    globe.position.x += (tgtX - globe.position.x) * k
    globe.position.y += (tgtY - globe.position.y) * k
    globe.scale.setScalar(baseScale)

    // 自转：全球态缓旋；下钻态（未被拖动接管时）始终把当前目标点转向镜头正面
    const autoSpeed = level.value === 0 && !dragOverride ? 0.028 : 0
    spin.userData.speed = (spin.userData.speed ?? 0.028) + (autoSpeed - (spin.userData.speed ?? 0.028)) * (level.value === 0 ? 0.05 : 0.3)
    spin.rotation.y += dt * spin.userData.speed
    if (level.value >= 1 && !dragOverride) {
      // 闭环对准：反算「球面正对相机的那一点」的经纬度，再按与目标的残差修正 yaw / pitch。
      // 比解析式更稳——自动吸收 globe.rotation.z 滚转与相机相对球心的偏移。
      const gc = globe.getWorldPosition(vGc)
      const dirW = vDir.copy(camera.position).sub(gc).normalize()
      invSpin.copy(spin.matrixWorld).invert()
      const local = vPt.copy(gc).addScaledVector(dirW, R * globe.scale.x).applyMatrix4(invSpin)
      const rr = local.length() || 1
      const latA = 90 - Math.acos(Math.max(-1, Math.min(1, local.y / rr))) * 180 / Math.PI
      const lngA = Math.atan2(local.z, -local.x) * 180 / Math.PI - 180
      let dLng = lv.lng - lngA
      if (dLng > 180) dLng -= 360
      if (dLng < -180) dLng += 360
      const RAD = Math.PI / 180
      spin.rotation.y -= dLng * RAD * k
      tilt.rotation.x += (lv.lat - latA) * RAD * k
    } else if (level.value === 0 && !dragOverride) {
      tilt.rotation.x += ((0.36 + my * 0.14) - tilt.rotation.x) * 0.04
    }
    // 下钻后视差归零，避免残留偏转把目标点挤出画面中心
    globe.rotation.y += (((level.value === 0 ? mx * 0.14 : 0)) - globe.rotation.y) * 0.04

    // 云层相对地表慢速漂移
    spin.children.forEach(ch => { if (ch.userData.isClouds) ch.rotation.y += dt * 0.01 })

    // 贴图/云层随层级淡出：贴地级只剩深色底盘 + 轮廓线，避免放大的卫星图糊成色块
    earthMat.opacity += (lv.tex - earthMat.opacity) * k
    // 贴地级把贴图整体压暗：放大几十倍的卫星图只剩色块，压暗后轮廓才立得住
    const dimT = level.value >= 3 ? 0.5 : level.value === 2 ? 0.68 : level.value === 1 ? 0.92 : 1
    earthMat.color.setScalar(earthMat.color.r + (dimT - earthMat.color.r) * k)
    if (cloudsMat) cloudsMat.opacity += (0.5 * Math.max(0, (lv.tex - 0.45) / 0.55) - cloudsMat.opacity) * k
    // 相机进入大气壳内部就收掉整壳，否则 BackSide 会把整屏刷成蓝白
    const atmoR = R * 1.18 * baseScale   // 相机进入大气壳内部时必须收掉，否则整屏泛蓝
    const atmoFade = Math.max(0, Math.min(1, (camera.position.z - atmoR) / (1.6 * baseScale / 0.64)))
    atmoMat.uniforms.fade.value = atmoFade
    atmo.visible = atmoFade > 0.01

    // 行政轮廓：只保留当前级（上级淡化会让透视拉伸的碎片线变成噪点）
    for (const o of outlineNodes) {
      const isCur = o.lv === level.value
      const tgt = isCur ? 0.92 : 0
      o.mat.opacity += (tgt - o.mat.opacity) * k
      o.obj.visible = o.mat.opacity > 0.008
      if (o.glowMat) {
        o.glowMat.opacity += ((isCur ? 0.16 : 0) - o.glowMat.opacity) * k
        o.glow.visible = o.glowMat.opacity > 0.006
        o.glowMat.linewidth += ((isCur ? 6.5 : 4) - o.glowMat.linewidth) * (1 - Math.pow(0.02, dt))
      }
      if (o.fillMat) {
        o.fillMat.opacity += ((isCur ? 0.13 : 0) - o.fillMat.opacity) * k
        o.fill.visible = o.fillMat.opacity > 0.004
      }
      const wTarget = isCur ? 1.65 : 0.95
      o.mat.linewidth += (wTarget - o.mat.linewidth) * (1 - Math.pow(0.02, dt))
    }

    // 目标浮签 + 定位环：标记点世界坐标投影到屏幕（HTML 叠层，尺寸不随缩放变化）
    if (level.value >= 4 && labelAnchor) {
      globe.updateMatrixWorld()
      const gc = globe.getWorldPosition(vGc)
      const world = labelAnchor.clone().applyMatrix4(spin.matrixWorld)
      const camDir = vDir.copy(camera.position).sub(gc).normalize()
      const normal = world.clone().sub(gc).normalize()
      const front = normal.dot(camDir) > 0.2
      const v = world.clone().project(camera)
      if (front && v.z < 1) {
        labelVisible.value = true
        tagSide.value = v.x > 0.12 ? 'l' : 'r'
        labelStyle.value = {
          left: ((v.x * 0.5 + 0.5) * (host.clientWidth || window.innerWidth)) + 'px',
          top: ((-v.y * 0.5 + 0.5) * (host.clientHeight || window.innerHeight)) + 'px',
        }
      } else {
        labelVisible.value = false
      }
    } else {
      labelVisible.value = false
    }

    renderer.render(scene, camera)
    raf = requestAnimationFrame(tick)
  }

  // ---------- 事件 ----------
  const onResize = () => resize()
  const onVis = () => {
    cancelAnimationFrame(raf)
    if (!disposed && !document.hidden) raf = requestAnimationFrame(tick)
  }
  window.addEventListener('resize', onResize)
  window.addEventListener('mousemove', onMove, { passive: true })
  document.addEventListener('visibilitychange', onVis)
  cleanupFns = [
    () => window.removeEventListener('resize', onResize),
    () => window.removeEventListener('mousemove', onMove),
    () => document.removeEventListener('visibilitychange', onVis),
    () => {
      cancelAnimationFrame(raf)
      if (idleT) clearTimeout(idleT)
      disposables.forEach(d => d.dispose())
      renderer.dispose()
    },
  ]

  // 浮签锚点：厦门华厦学院（集美文教区）实际坐标，与下钻终点一致
  labelAnchor = latLng(24.6165, 118.1045).multiplyScalar(1.004)
  resize()
  ready.value = true

  // 开发自检：反算「镜头正中心对应的经纬度」，供自动化校验下钻落点是否等于目标坐标
  if (import.meta.env.DEV) {
    const inv = new THREE.Matrix4()
    const probeTimer = window.setInterval(() => {
      if (disposed) return
      scene.updateMatrixWorld(true)
      const gc = globe.getWorldPosition(new THREE.Vector3())
      const dir = camera.position.clone().sub(gc).normalize()
      const p = gc.clone().addScaledVector(dir, R * globe.scale.x)
      inv.copy(spin.matrixWorld).invert()
      const local = p.applyMatrix4(inv)
      const r = local.length() || 1
      const lat = 90 - Math.acos(Math.max(-1, Math.min(1, local.y / r))) * 180 / Math.PI
      const lngRaw = Math.atan2(local.z, -local.x) * 180 / Math.PI - 180
      const lng = ((lngRaw + 540) % 360) - 180
      ;(window as any).__globeProbe = { lat: +lat.toFixed(3), lng: +lng.toFixed(3), level: level.value, z: +camera.position.z.toFixed(2) }
    }, 300)
    cleanupFns.push(() => clearInterval(probeTimer))

    // 机位调试：__globeDbg.go(2) 直接跳级，__globeDbg.pose({z: 8}) 改当前级镜头
    ;(window as any).__globeDbg = {
      go: (n: number) => { level.value = Math.max(0, Math.min(LEVELS.length - 1, n)); dragOverride = false; resetIdle(60000) },
      pose: (p: any) => Object.assign(LEVELS[level.value], p),
      get levels() { return LEVELS.map(l => ({ ...l })) },
      get nodes() {
        return outlineNodes.map(o => ({
          lv: o.lv, vis: o.obj.visible, op: +o.mat.opacity.toFixed(3),
          fillOp: o.fillMat ? +o.fillMat.opacity.toFixed(3) : null,
          camZ: +camera.position.z.toFixed(4),
          atmo: +atmoMat.uniforms.fade.value.toFixed(2),
        }))
      },
    }
    cleanupFns.push(() => { delete (window as any).__globeDbg })
  }

  if (reduced) {
    // 关闭动效时跳过整个下钻过场，直接落到校园层
    level.value = LEVELS.length - 1
    renderer.render(scene, camera)
    window.setTimeout(finish, 600)
  } else {
    raf = requestAnimationFrame(tick)
    resetIdle(4400)   // 首屏留足看清地球的时间，之后每级闲置兜底自动推进
  }
})

let labelAnchor: any = null

onBeforeUnmount(() => {
  disposed = true
  if (idleT) { clearTimeout(idleT); idleT = null }
  cleanupFns.forEach(f => { try { f() } catch {} })
  cleanupFns = []
})
</script>

<style scoped>
.lg-wrap {
  position: absolute;
  inset: 0;
  z-index: 0;
  overflow: hidden;
}
.lwg-canvas {
  width: 100%;
  height: 100%;
  display: block;
  opacity: 0;
  transition: opacity 1.2s ease;
  cursor: grab;
  touch-action: none;
}
.lwg-canvas.dragging { cursor: grabbing; }
.lwg-canvas.ready { opacity: 1; }

/* 点击涟漪 */
.lg-rip {
  position: absolute;
  width: 10px; height: 10px;
  margin: -5px 0 0 -5px;
  border-radius: 50%;
  border: 1px solid rgba(190, 246, 255, .85);
  pointer-events: none;
  animation: lgRip .85s cubic-bezier(.2, .7, .3, 1) forwards;
}
@keyframes lgRip {
  0% { transform: scale(.4); opacity: .95; }
  100% { transform: scale(7); opacity: 0; }
}

/* 目标定位环：HTML 叠层，任意缩放下尺寸恒定，不受球面放大影响 */
.lg-pin {
  position: absolute;
  width: 46px; height: 46px;
  transform: translate(-50%, -50%);
  pointer-events: none;
  z-index: 1;
}
.lg-pin-ring {
  position: absolute; inset: 0;
  border-radius: 50%;
  border: 1.5px solid rgba(232, 250, 255, .85);
  box-shadow: 0 0 16px rgba(130, 214, 245, .55), inset 0 0 14px rgba(130, 214, 245, .35);
  animation: lgPinPulse 2.4s ease-out infinite;
}
.lg-pin-dot {
  position: absolute; left: 50%; top: 50%;
  width: 7px; height: 7px; margin: -3.5px 0 0 -3.5px;
  border-radius: 50%;
  background: #ffffff;
  box-shadow: 0 0 12px rgba(200, 244, 255, .95), 0 0 26px rgba(120, 210, 240, .7);
}
@keyframes lgPinPulse {
  0% { transform: scale(.82); opacity: .95; }
  70% { transform: scale(1.16); opacity: .35; }
  100% { transform: scale(.82); opacity: .95; }
}

/* ===== 目标浮签：跟随球面投影 ===== */
.lg-tag {
  position: absolute;
  transform: translate(14px, -50%);
  display: flex;
  align-items: center;
  pointer-events: none;
  z-index: 1;
}
.lg-tag-line {
  width: 34px; height: 1px;
  background: linear-gradient(90deg, rgba(190, 246, 255, .95), rgba(190, 246, 255, .2));
  margin-right: 8px;
}
.lg-tag.is-left {
  transform: translate(calc(-100% - 14px), -50%);
  flex-direction: row-reverse;
}
.lg-tag.is-left .lg-tag-line {
  margin-right: 0; margin-left: 8px;
  background: linear-gradient(270deg, rgba(190, 246, 255, .95), rgba(190, 246, 255, .2));
}
.lg-tag-text {
  font-size: 15px; font-weight: 700; letter-spacing: 2px;
  color: #f2fbff;
  text-shadow: 0 0 14px rgba(120, 210, 240, .65);
  white-space: nowrap;
  padding: 5px 12px;
  border-radius: 999px;
  border: 1px solid rgba(200, 244, 255, .32);
  background: rgba(8, 24, 38, .58);
  backdrop-filter: blur(6px);
}
.lg-fade-enter-active, .lg-fade-leave-active { transition: opacity .5s ease; }
.lg-fade-enter-from, .lg-fade-leave-to { opacity: 0; }

@media (max-width: 640px) {
  .lg-pin { width: 38px; height: 38px; }
  .lg-tag-text { font-size: 13px; letter-spacing: 1px; padding: 4px 10px; }
  .lg-tag-line { width: 22px; }
}

@media (prefers-reduced-motion: reduce) {
  .lwg-canvas { transition: none; }
  .lg-rip { display: none; }
}
</style>
