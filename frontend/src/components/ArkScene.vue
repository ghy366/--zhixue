<template>
  <div ref="wrap" class="ark-scene" :class="{ 'is-ready': ready }" aria-hidden="true">
    <canvas ref="cv" class="ark-scene-cv"></canvas>
  </div>
</template>

<script setup lang="ts">
/**
 * 登录页 · 方舟核心（Ark Core）— Three.js 实时背景 · v2
 *
 * 视觉意图：不是「放一颗球」，而是一台悬浮的、正在自检的学术终端核心。
 * 观看距离 = 全屏背景；主体刻意压低亮度，绝不与右侧登录卡抢注意力。
 *
 * v2 升级（3D 插件产出 + 3dviz-pro-max 工作流第 6/7/8 步）：
 *   1. 接入 3D 生成插件产出的 GLB 模型作为「实体内核」，统一重绘为暗金属 + 黄边线，
 *      风格与程序化外壳一致；模型缺失/加载失败时静默退回原多面体内核，不留空白。
 *   2. EffectComposer + UnrealBloomPass 真实辉光，让黄色能量件有光晕而非纯色块。
 *   3. 三条能量环上增加流动弧光与端点光点，动态不再只有整体自转。
 *   4. 交互脉冲：表单聚焦 / 点击时核心增亮、辉光增强，随后自然衰减。
 *   5. three 与所有 addons 改为按需动态 import，首屏不再同步背 736KB。
 *
 * 行为归属：自转、公转、扫描只改 transform / uniform，不伪装成物理仿真。
 * reduced-motion 停帧；窄屏/高 DPI 自动关闭 Bloom；卸载时完整 dispose。
 */
import { ref, onMounted, onBeforeUnmount } from 'vue'
import type * as ThreeNS from 'three'

const wrap = ref<HTMLDivElement | null>(null)
const cv = ref<HTMLCanvasElement | null>(null)
const ready = ref(false)

const MODEL_URL = '/models/ark-core.glb'

let renderer: ThreeNS.WebGLRenderer | null = null
let composer: { render: () => void; setSize: (w: number, h: number) => void; dispose: () => void } | null = null
let bloom: { strength: number } | null = null
let raf = 0
let ro: ResizeObserver | null = null
let disposed = false
const disposables: Array<{ dispose: () => void }> = []
const pointer = { x: 0, y: 0, tx: 0, ty: 0 }
let energy = 0

function onPointerMove(e: PointerEvent) {
  pointer.tx = (e.clientX / window.innerWidth - 0.5) * 2
  pointer.ty = (e.clientY / window.innerHeight - 0.5) * 2
}
function onFocusIn(e: FocusEvent) {
  const tag = (e.target as HTMLElement | null)?.tagName || ''
  if (/^(INPUT|SELECT|TEXTAREA|BUTTON)$/.test(tag)) energy = 1
}
function onPointerDown() { energy = Math.max(energy, 0.8) }

onMounted(async () => {
  const host = wrap.value
  const canvas = cv.value
  if (!host || !canvas) return

  // ---------- 按需加载：首屏只付 Vue 的体积，3D 延后一帧 ----------
  let THREE: typeof import('three')
  try {
    THREE = await import('three')
  } catch {
    host.classList.add('is-fallback')
    return
  }
  if (disposed) return

  let gl: ThreeNS.WebGLRenderer
  try {
    gl = new THREE.WebGLRenderer({ canvas, alpha: true, antialias: true, powerPreference: 'high-performance' })
  } catch {
    host.classList.add('is-fallback')   // 无 WebGL：交给 CSS 背景，不空白
    return
  }
  renderer = gl
  gl.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.75))
  gl.setClearColor(0x000000, 0)
  gl.outputColorSpace = THREE.SRGBColorSpace

  const scene = new THREE.Scene()
  scene.fog = new THREE.FogExp2(0x070a0e, 0.026)

  const camera = new THREE.PerspectiveCamera(46, 1, 0.1, 120)
  camera.position.set(0, 0.15, 12)

  const keep = <T extends { dispose: () => void }>(o: T): T => { disposables.push(o); return o }
  const M = {
    lineWhite: keep(new THREE.LineBasicMaterial({ color: 0xdfe8ef, transparent: true, opacity: 0.28, depthWrite: false })),
    lineDim: keep(new THREE.LineBasicMaterial({ color: 0x8a97a4, transparent: true, opacity: 0.12, depthWrite: false })),
    lineYellow: keep(new THREE.LineBasicMaterial({ color: 0xffd100, transparent: true, opacity: 0.55, depthWrite: false })),
    glowYellow: keep(new THREE.MeshBasicMaterial({ color: 0xffd100, transparent: true, opacity: 0.09, blending: THREE.AdditiveBlending, depthWrite: false, depthTest: false })),
    ringYellow: keep(new THREE.MeshBasicMaterial({ color: 0xffd100, transparent: true, opacity: 0.65, blending: THREE.AdditiveBlending, depthWrite: false })),
    ringWhite: keep(new THREE.MeshBasicMaterial({ color: 0xbfe0f2, transparent: true, opacity: 0.35, blending: THREE.AdditiveBlending, depthWrite: false })),
    arcYellow: keep(new THREE.MeshBasicMaterial({ color: 0xffda3d, transparent: true, opacity: 0.78, blending: THREE.AdditiveBlending, depthWrite: false })),
    spark: keep(new THREE.MeshBasicMaterial({ color: 0xffe066, transparent: true, opacity: 0.95, blending: THREE.AdditiveBlending, depthWrite: false })),
    core: keep(new THREE.MeshStandardMaterial({
      color: 0x111923, metalness: 0.85, roughness: 0.34, flatShading: true,
      emissive: 0x0b1a24, emissiveIntensity: 0.55,
    })),
    modelEdge: keep(new THREE.LineBasicMaterial({ color: 0xffd100, transparent: true, opacity: 0.22, depthWrite: false })),
    shard: keep(new THREE.MeshStandardMaterial({
      color: 0x151d26, metalness: 0.7, roughness: 0.5, flatShading: true,
      transparent: true, opacity: 0.9, side: THREE.DoubleSide,
    })),
    dust: keep(new THREE.PointsMaterial({ color: 0xcfe0ea, size: 0.028, transparent: true, opacity: 0.5, sizeAttenuation: true, depthWrite: false })),
    scan: keep(new THREE.MeshBasicMaterial({ color: 0xffd100, transparent: true, opacity: 0.055, blending: THREE.AdditiveBlending, depthWrite: false, side: THREE.DoubleSide })),
  }

  // ---------- 灯光：黄为主光（右上），冷白为补光（左下），弱环境托底 ----------
  scene.add(new THREE.AmbientLight(0x5b6a7a, 0.85))
  const key = new THREE.PointLight(0xffd100, 26, 24)
  key.position.set(-4.5, 3.2, 5.5)
  scene.add(key)
  const fill = new THREE.PointLight(0x86cfff, 14, 22)
  fill.position.set(5, -2.4, 3)
  scene.add(fill)

  // ---------- 主体：方舟核心 ----------
  const root = new THREE.Group()
  root.position.set(-2.9, 0.25, 0)
  scene.add(root)

  const shellGeo = keep(new THREE.IcosahedronGeometry(2.08, 1))
  const shellEdge = keep(new THREE.EdgesGeometry(shellGeo, 12))
  const shell = new THREE.LineSegments(shellEdge, M.lineWhite)
  root.add(shell)

  // 程序化内核（同时也是 GLB 加载失败时的兜底）
  const innerGeo = keep(new THREE.IcosahedronGeometry(1.38, 0))
  const inner = new THREE.Mesh(innerGeo, M.core)
  root.add(inner)
  const innerWire = new THREE.LineSegments(keep(new THREE.EdgesGeometry(innerGeo)), M.lineDim)
  root.add(innerWire)

  const nucleusGeo = keep(new THREE.SphereGeometry(0.3, 20, 14))
  const nucleus = new THREE.Mesh(nucleusGeo, keep(new THREE.MeshBasicMaterial({ color: 0xffd83b })))
  root.add(nucleus)
  const halo = new THREE.Mesh(keep(new THREE.SphereGeometry(0.72, 20, 14)), M.glowYellow)
  root.add(halo)

  // 三条不同倾角的能量环
  const rings: ThreeNS.Mesh[] = []
  const arcs: ThreeNS.Mesh[] = []
  const sparks: Array<{ m: ThreeNS.Mesh; r: number; a: number; speed: number }> = []
  const ringSpec = [
    { r: 2.62, tilt: [1.18, 0.24, 0.35], mat: M.ringYellow },
    { r: 2.98, tilt: [0.42, 0.9, -0.5], mat: M.ringWhite },
    { r: 3.34, tilt: [1.52, -0.4, 0.8], mat: M.ringYellow },
  ]
  const sparkGeo = keep(new THREE.SphereGeometry(0.05, 8, 6))
  ringSpec.forEach((s, i) => {
    const g = keep(new THREE.TorusGeometry(s.r, 0.009, 6, 180))
    const m = new THREE.Mesh(g, s.mat)
    m.rotation.set(s.tilt[0], s.tilt[1], s.tilt[2])
    m.userData.spin = (i % 2 ? -1 : 1) * (0.09 + i * 0.035)
    root.add(m)
    rings.push(m)

    // 沿环奔跑的能量弧：一段发光圆弧 + 端点光点，属于「仪表正在读数」的行为
    const arcLen = [0.55, 0.38, 0.48][i]
    const ag = keep(new THREE.TorusGeometry(s.r, 0.024, 6, 30, arcLen))
    const arc = new THREE.Mesh(ag, M.arcYellow)
    arc.userData.spin = (i % 2 ? -1 : 1) * (0.42 + i * 0.14)
    m.add(arc)
    arcs.push(arc)

    const n = i === 0 ? 3 : 2
    for (let k = 0; k < n; k++) {
      const sp = new THREE.Mesh(sparkGeo, M.spark)
      m.add(sp)
      sparks.push({ m: sp, r: s.r, a: (k / n) * Math.PI * 2, speed: 0.34 + i * 0.13 + k * 0.07 })
    }
  })

  // 刻度环：72 根短划线，每 6 根加长 —— 纯 BufferGeometry，一次 draw call
  const tickPts: number[] = []
  const R = 3.78
  for (let i = 0; i < 72; i++) {
    const a = (i / 72) * Math.PI * 2
    const long = i % 6 === 0
    const r0 = long ? R - 0.22 : R - 0.09
    tickPts.push(Math.cos(a) * r0, Math.sin(a) * r0, 0, Math.cos(a) * R, Math.sin(a) * R, 0)
  }
  const tickGeo = keep(new THREE.BufferGeometry())
  tickGeo.setAttribute('position', new THREE.Float32BufferAttribute(tickPts, 3))
  const ticks = new THREE.LineSegments(tickGeo, keep(new THREE.LineBasicMaterial({ color: 0xffd100, transparent: true, opacity: 0.3, depthWrite: false })))
  ticks.rotation.set(1.2, 0.2, 0.3)
  root.add(ticks)

  // 悬浮碎片：共用一份几何体，各自保存初相位，行为独立
  const shardGeo = keep(new THREE.BoxGeometry(0.46, 0.035, 0.2))
  const shardEdge = keep(new THREE.EdgesGeometry(shardGeo))
  const shards: Array<{ obj: ThreeNS.Object3D; phase: number; y: number; spin: number }> = []
  for (let i = 0; i < 22; i++) {
    const a = (i / 22) * Math.PI * 2 + Math.random() * 0.4
    const rad = 4.0 + Math.random() * 2.2
    const g = new THREE.Group()
    g.position.set(Math.cos(a) * rad, (Math.random() - 0.5) * 4.2, Math.sin(a) * rad - 1)
    g.rotation.set(Math.random() * 2, Math.random() * 3, Math.random() * 2)
    g.add(new THREE.Mesh(shardGeo, M.shard))
    g.add(new THREE.LineSegments(shardEdge, M.lineDim))
    root.add(g)
    shards.push({ obj: g, phase: Math.random() * Math.PI * 2, y: g.position.y, spin: (Math.random() - 0.5) * 0.24 })
  }

  // ---------- 纵深：远处两层大圆环，克制不抢镜 ----------
  ;[7.4, 10.2].forEach((r, i) => {
    const g = keep(new THREE.TorusGeometry(r, 0.006, 4, 140))
    const m = new THREE.Mesh(g, M.ringWhite)
    m.material = keep(new THREE.MeshBasicMaterial({ color: 0x8fb6cc, transparent: true, opacity: i ? 0.07 : 0.1, blending: THREE.AdditiveBlending, depthWrite: false }))
    m.position.set(-2.9, 0.25, -9 - i * 4)
    m.rotation.set(0.24, 0.1, i * 0.4)
    scene.add(m)
  })

  // ---------- 地面栅格 + 扫描光带 ----------
  const gridPts: number[] = []
  const G = 22, step = 1.5
  for (let i = -G; i <= G; i++) {
    gridPts.push(i * step, -3.45, -G * step, i * step, -3.45, G * step)
    gridPts.push(-G * step, -3.45, i * step, G * step, -3.45, i * step)
  }
  const gridGeo = keep(new THREE.BufferGeometry())
  gridGeo.setAttribute('position', new THREE.Float32BufferAttribute(gridPts, 3))
  const grid = new THREE.LineSegments(gridGeo, M.lineDim)
  grid.material = keep(new THREE.LineBasicMaterial({ color: 0x6f8291, transparent: true, opacity: 0.085, depthWrite: false }))
  scene.add(grid)

  const scan = new THREE.Mesh(keep(new THREE.PlaneGeometry(60, 0.5)), M.scan)
  scan.rotation.x = -Math.PI / 2
  scan.position.set(0, -3.4, 0)
  scene.add(scan)

  // ---------- 尘埃 ----------
  const dustN = 420
  const dustPos = new Float32Array(dustN * 3)
  for (let i = 0; i < dustN; i++) {
    dustPos[i * 3] = (Math.random() - 0.5) * 30
    dustPos[i * 3 + 1] = (Math.random() - 0.5) * 14
    dustPos[i * 3 + 2] = (Math.random() - 0.5) * 20 - 3
  }
  const dustGeo = keep(new THREE.BufferGeometry())
  dustGeo.setAttribute('position', new THREE.BufferAttribute(dustPos, 3))
  const dust = new THREE.Points(dustGeo, M.dust)
  scene.add(dust)

  // ---------- 尺寸 / 视差 ----------
  const resize = () => {
    if (!renderer || !host) return
    const w = host.clientWidth || window.innerWidth
    const h = host.clientHeight || window.innerHeight
    renderer.setSize(w, h, false)
    composer?.setSize(w, h)
    camera.aspect = w / h
    camera.updateProjectionMatrix()
    // 窄屏把核心收回画面中央并缩小，避免被登录卡压住
    const narrow = w < 1080
    root.position.x = narrow ? 0 : -2.9
    root.scale.setScalar(narrow ? 0.78 : 1)
  }
  ro = new ResizeObserver(resize)
  ro.observe(host)
  resize()

  const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false

  // ---------- Bloom 真实辉光：桌面 + 常规 DPI 才开，低性能设备直接跳过 ----------
  if (!reduced && window.innerWidth >= 900 && (window.devicePixelRatio || 1) <= 2) {
    try {
      const [{ EffectComposer }, { RenderPass }, { UnrealBloomPass }, { OutputPass }] = await Promise.all([
        import('three/examples/jsm/postprocessing/EffectComposer.js'),
        import('three/examples/jsm/postprocessing/RenderPass.js'),
        import('three/examples/jsm/postprocessing/UnrealBloomPass.js'),
        import('three/examples/jsm/postprocessing/OutputPass.js'),
      ])
      if (disposed) return
      const comp = new EffectComposer(gl)
      comp.addPass(new RenderPass(scene, camera))
      const bp = new UnrealBloomPass(new THREE.Vector2(host.clientWidth || 1600, host.clientHeight || 900), 0.62, 0.5, 0.58)
      comp.addPass(bp)
      comp.addPass(new OutputPass())
      composer = comp
      bloom = bp
      // composer 的最终输出不保留 alpha，直接给一个与页面一致的深色底，避免透出黑块
      gl.setClearColor(0x070a0e, 1)
      disposables.push({ dispose: () => comp.dispose() })
      resize()
    } catch {
      composer = null
      bloom = null
    }
  }

  const draw = () => {
    if (composer) composer.render()
    else gl.render(scene, camera)
  }

  // ---------- 3D 插件产出的 GLB：替换内核，统一重绘为方舟线框语言 ----------
  let modelCore: ThreeNS.Group | null = null
  const loadModel = async () => {
    try {
      // 先探测，避免 public 下无文件时被 SPA fallback 返回 index.html 触发解析报错
      const probe = await fetch(MODEL_URL, { method: 'HEAD' })
      const ct = probe.headers.get('content-type') || ''
      const len = Number(probe.headers.get('content-length') || 0)
      if (!probe.ok || ct.includes('text/html') || (len > 0 && len < 2000)) return
      const { GLTFLoader } = await import('three/examples/jsm/loaders/GLTFLoader.js')
      const gltf = await new GLTFLoader().loadAsync(MODEL_URL)
      if (disposed) return

      const box = new THREE.Box3().setFromObject(gltf.scene)
      const size = box.getSize(new THREE.Vector3())
      const center = box.getCenter(new THREE.Vector3())
      const maxDim = Math.max(size.x, size.y, size.z) || 1
      const k = 3.24 / maxDim
      gltf.scene.scale.setScalar(k)
      gltf.scene.position.copy(center.multiplyScalar(-k))

      // 保留 3D 插件生成的贴图与发光缝：只压暗装甲，并把贴图复用为 emissiveMap，
      // 让橙黄能量缝真正发光、被 Bloom 捕捉；再叠一层黄色硬边线呼应程序化外壳。
      const touched = new Set<ThreeNS.Material>()
      gltf.scene.traverse(o => {
        const mesh = o as ThreeNS.Mesh
        if (!mesh.isMesh) return
        const geo = mesh.geometry as ThreeNS.BufferGeometry
        const mats = Array.isArray(mesh.material) ? mesh.material : [mesh.material]
        mats.forEach(raw => {
          const mat = raw as ThreeNS.MeshStandardMaterial
          if (!mat || touched.has(mat)) return
          touched.add(mat)
          mat.color?.multiplyScalar(0.72)
          if (mat.map && 'emissive' in mat) {
            mat.emissiveMap = mat.map
            mat.emissive.setRGB(0.92, 0.56, 0.14)
            mat.emissiveIntensity = 0.5
          }
          if ('metalness' in mat) mat.metalness = Math.max(Number(mat.metalness ?? 0), 0.55)
          if ('roughness' in mat) mat.roughness = Math.min(Number(mat.roughness ?? 1), 0.68)
          mat.needsUpdate = true
          keep(mat)
        })
        keep(geo)
        const edge = keep(new THREE.EdgesGeometry(geo, 38))
        mesh.add(new THREE.LineSegments(edge, M.modelEdge))
      })

      const holder = new THREE.Group()
      holder.add(gltf.scene)
      root.add(holder)
      inner.visible = false
      innerWire.visible = false
      nucleus.visible = false
      modelCore = holder
      ready.value = true
      if (reduced) draw()   // 减少动效模式停帧，模型到位后需手动重绘一次
    } catch (e) {
      // 模型缺失 / 解析失败：静默保留程序化内核，页面不报错、不空白
      console.warn('[ark] core model skipped:', e)
    }
  }

  // ---------- 帧循环 ----------
  const clock = new THREE.Clock()
  const tick = () => {
    const dt = Math.min(clock.getDelta(), 0.05)
    const t = clock.elapsedTime
    energy = Math.max(0, energy - dt * 0.85)

    root.rotation.y += dt * 0.12
    inner.rotation.x += dt * 0.09
    inner.rotation.y -= dt * 0.14
    innerWire.rotation.copy(inner.rotation)
    if (modelCore) {
      modelCore.rotation.y -= dt * 0.17
      modelCore.rotation.x += dt * 0.045
    }
    halo.scale.setScalar(1 + Math.sin(t * 1.6) * 0.12 + energy * 0.45)
    nucleus.scale.setScalar(1 + Math.sin(t * 2.3) * 0.06 + energy * 0.3)
    key.intensity = 26 + energy * 16
    if (bloom) bloom.strength = 0.62 + energy * 0.42

    rings.forEach((r, i) => { r.rotation.z += dt * (r.userData.spin as number); r.rotation.y += dt * 0.02 * (i + 1) })
    arcs.forEach(a => { a.rotation.z += dt * (a.userData.spin as number) })
    sparks.forEach((s, i) => {
      s.a += dt * s.speed
      s.m.position.set(Math.cos(s.a) * s.r, Math.sin(s.a) * s.r, 0)
      s.m.scale.setScalar(0.75 + Math.sin(t * 3 + i) * 0.25 + energy * 0.8)
    })
    ticks.rotation.z -= dt * 0.035
    shards.forEach(s => {
      s.obj.position.y = s.y + Math.sin(t * 0.5 + s.phase) * 0.28
      s.obj.rotation.y += dt * s.spin
    })
    scan.position.y = -3.4 + ((t * 0.42) % 1) * 6.8
    dust.rotation.y += dt * 0.008
    dust.position.y = Math.sin(t * 0.18) * 0.35

    // 相机视差：只改 transform，不重建场景
    pointer.x += (pointer.tx - pointer.x) * 0.035
    pointer.y += (pointer.ty - pointer.y) * 0.035
    camera.position.x += (pointer.x * 0.55 - camera.position.x) * 0.05
    camera.position.y += (0.15 - pointer.y * 0.4 - camera.position.y) * 0.05
    camera.lookAt(0, 0, 0)
  }

  if (reduced) {
    draw()
  } else {
    window.addEventListener('pointermove', onPointerMove, { passive: true })
    window.addEventListener('focusin', onFocusIn, { passive: true })
    window.addEventListener('pointerdown', onPointerDown, { passive: true })
    const loop = () => {
      if (disposed) return
      tick()
      draw()
      raf = requestAnimationFrame(loop)
    }
    raf = requestAnimationFrame(loop)
  }
  ready.value = true
  void loadModel()
})

onBeforeUnmount(() => {
  disposed = true
  if (raf) cancelAnimationFrame(raf)
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('focusin', onFocusIn)
  window.removeEventListener('pointerdown', onPointerDown)
  ro?.disconnect()
  disposables.forEach(d => { try { d.dispose() } catch { /* 已释放 */ } })
  disposables.length = 0
  composer = null
  bloom = null
  renderer?.dispose()
  renderer = null
})
</script>

<style scoped>
.ark-scene { position: absolute; inset: 0; z-index: 0; pointer-events: none; overflow: hidden; opacity: 0; transition: opacity .7s ease; }
.ark-scene.is-ready { opacity: 1; }
.ark-scene-cv { display: block; width: 100%; height: 100%; }
.ark-scene.is-fallback {
  opacity: 1;
  background:
    radial-gradient(circle at 28% 48%, rgba(255, 209, 0, .10), transparent 42%),
    radial-gradient(circle at 30% 50%, rgba(120, 180, 220, .08), transparent 55%);
}
/* 窄屏由 Login.vue 的 :deep(.ark-scene) 统一压到 .62，此处不再覆盖 */
@media (prefers-reduced-motion: reduce) {
  .ark-scene { transition: none; }
}
</style>
