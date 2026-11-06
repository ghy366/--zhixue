<template>
  <div ref="host" class="es" :class="{ 'is-fallback': fallback }">
    <!-- 球体 + 星环全部由 WebGL 绘制；canvas 只作为画布，无内容时下方光晕兜底 -->
    <canvas ref="cv" class="es-cv" aria-hidden="true"></canvas>
    <div class="es-aura" aria-hidden="true"></div>
    <!-- WebGL 不可用 / 初始化失败时的纯 CSS 降级球（不白屏、不影响登录） -->
    <div v-if="fallback" class="es-fb" aria-hidden="true"></div>
  </div>
</template>

<script setup lang="ts">
/**
 * 深空地球场景（Three.js）
 * ------------------------------------------------------------------
 * - SphereGeometry 球体 + 4096x2048 卫星贴图 + 夜灯自发光 + 海洋高光
 * - 独立云层球（转速略快于地球本体）
 * - 大气层：背面球 + Fresnel 着色器，形成青蓝色边缘光
 * - 两条不同倾角 / 不同速度的椭圆轨道（真 3D 圆环倾斜投影，前后穿插由深度测试自动完成）
 * - 每条轨道一颗发光粒子 + 拖尾
 * - 三层远近不同的星场（冷暖色点缀）+ 独立闪烁亮星层 + 三团背景星云
 * - 组件卸载时完整释放 geometry / material / texture / renderer
 * 说明：three 采用动态 import，首屏先出 CSS 光晕，地球随后淡入，避免拖慢登录首屏。
 */
import { onMounted, onBeforeUnmount, ref } from 'vue'
import earthDayUrl from '../globe/earth-day.jpg'
import earthNightUrl from '../globe/earth-night.jpg'
import earthCloudUrl from '../globe/earth-clouds.png'
import earthTopoUrl from '../globe/earth-topology.png'
import earthWaterUrl from '../globe/earth-water.png'

const host = ref<HTMLDivElement | null>(null)
const cv = ref<HTMLCanvasElement | null>(null)
const fallback = ref(false)

type Any = any
let THREE: Any = null
let renderer: Any = null
let scene: Any = null
let camera: Any = null
let raf = 0
let frame: Any = null          // 渲染循环（resize 可能早于它定义时被调用）
let visible = true
let disposed = false
let ready = false

/* 需要在卸载时释放的资源（geometry / material / texture） */
const bin: Any[] = []
const track = <T,>(x: T): T => { bin.push(x); return x }

/* ---------- 场景对象 ---------- */
let globe: Any = null          // 总容器（倾斜 + 视差）
let earthSpin: Any = null      // 地球自转轴（贴在球面上的一切都挂这里）
let clouds: Any = null
let cloudMat: Any = null
let atmo: Any = null
let atmoMat: Any = null
let starLayers: Array<{ pts: Any; base: number; z: number }> = []
let twinkleMat: Any = null
let nebulae: Array<{ sp: Any; m: Any; base: number; ph: number }> = []
let issGroup: Any = null
let issGlow: Any = null
let issTimer: Any = 0
let issFail = 0
let dragGroup: Any = null
let dragging = false
let dragPX = 0, dragPY = 0, dragVX = 0, dragVY = 0
let dashRing: { group: Any; speed: number } | null = null
const rings: Array<{
  group: Any; head: Any; trail: Any; trailGeo: Any; trailColors: Any
  a: number; speed: number; dir: number; rotZ: number; wobble: number
  alpha: number
}> = []

/* 交互（只在左栏内轻微视差，不影响登录操作） */
let px = 0, py = 0, tx = 0, ty = 0
let hovered = false

const reduced = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches

/* ---------- 工具：圆形光点贴图（避免方块粒子） ---------- */
function dotTexture(THREE_: Any, soft = 0.5) {
  const s = 128
  const c = document.createElement('canvas')
  c.width = c.height = s
  const g = c.getContext('2d')!
  const grd = g.createRadialGradient(s / 2, s / 2, 0, s / 2, s / 2, s / 2)
  grd.addColorStop(0, 'rgba(255,255,255,1)')
  grd.addColorStop(soft, 'rgba(255,255,255,.55)')
  grd.addColorStop(1, 'rgba(255,255,255,0)')
  g.fillStyle = grd
  g.fillRect(0, 0, s, s)
  const tex = new THREE_.CanvasTexture(c)
  tex.colorSpace = THREE_.SRGBColorSpace
  return track(tex)
}

/* ---------- 工具：程序化星云贴图（多重柔光叠加，供背景星云精灵用） ---------- */
function nebulaTexture(THREE_: Any, seeds: Array<{ x: number; y: number; r: number; c: string }>) {
  const s = 512
  const c = document.createElement('canvas')
  c.width = c.height = s
  const g = c.getContext('2d')!
  g.globalCompositeOperation = 'lighter'
  for (const sp of seeds) {
    const grd = g.createRadialGradient(sp.x * s, sp.y * s, 0, sp.x * s, sp.y * s, sp.r * s)
    grd.addColorStop(0, sp.c)
    grd.addColorStop(1, 'rgba(0,0,0,0)')
    g.fillStyle = grd
    g.fillRect(0, 0, s, s)
  }
  const tex = new THREE_.CanvasTexture(c)
  tex.colorSpace = THREE_.SRGBColorSpace
  return track(tex)
}

/* ---------- 工具：四角星芒贴图（轨道光点用，比圆点更像参考图） ---------- */
function flareTexture(THREE_: Any) {
  const s = 256
  const c = document.createElement('canvas')
  c.width = c.height = s
  const g = c.getContext('2d')!
  const h = s / 2
  /* 核心柔光 */
  const core = g.createRadialGradient(h, h, 0, h, h, h * 0.30)
  core.addColorStop(0, 'rgba(255,255,255,1)')
  core.addColorStop(0.35, 'rgba(255,255,255,.62)')
  core.addColorStop(1, 'rgba(255,255,255,0)')
  g.fillStyle = core
  g.fillRect(0, 0, s, s)
  /* 横向光条 */
  const hz = g.createLinearGradient(0, h, s, h)
  hz.addColorStop(0, 'rgba(255,255,255,0)')
  hz.addColorStop(0.5, 'rgba(255,255,255,.9)')
  hz.addColorStop(1, 'rgba(255,255,255,0)')
  g.globalAlpha = 0.75
  g.fillStyle = hz
  g.fillRect(0, h - 1.5, s, 3)
  /* 纵向光条 */
  const vt = g.createLinearGradient(h, 0, h, s)
  vt.addColorStop(0, 'rgba(255,255,255,0)')
  vt.addColorStop(0.5, 'rgba(255,255,255,.8)')
  vt.addColorStop(1, 'rgba(255,255,255,0)')
  g.fillStyle = vt
  g.fillRect(h - 1.5, 0, 3, s)
  g.globalAlpha = 1
  const tex = new THREE_.CanvasTexture(c)
  tex.colorSpace = THREE_.SRGBColorSpace
  return track(tex)
}

/* ---------- 加载贴图（失败不阻塞场景） ---------- */
function loadTex(url: string, srgb = true): Promise<Any> {
  return new Promise((resolve, reject) => {
    const tex = new THREE.TextureLoader().load(
      url,
      (t: Any) => { if (srgb) t.colorSpace = THREE.SRGBColorSpace; resolve(t) },
      undefined,
      () => reject(new Error('texture failed: ' + url)),
    )
    bin.push(tex)
  })
}

onMounted(async () => {
  const el = host.value
  const canvas = cv.value
  if (!el || !canvas) return

  try {
    THREE = await import('three')
  } catch {
    fallback.value = true
    return
  }
  if (disposed) return

  /* ---------- WebGL 能力检测：拿不到上下文就降级，绝不让页面白屏 ----------
     先在临时 canvas 上探测，避免 three 内部再打印一条 context 创建失败的错误 */
  let supported = false
  try {
    const probe = document.createElement('canvas')
    const ctx = probe.getContext('webgl2') || probe.getContext('webgl') || probe.getContext('experimental-webgl')
    supported = !!ctx
    ;(ctx as Any)?.getExtension?.('WEBGL_lose_context')?.loseContext?.()
  } catch { supported = false }
  if (!supported) { fallback.value = true; return }

  try {
    renderer = new THREE.WebGLRenderer({
      canvas, alpha: true, antialias: true, powerPreference: 'high-performance',
    })
  } catch {
    fallback.value = true
    return
  }
  if (!renderer) { fallback.value = true; return }

  const w = el.clientWidth || 560
  const h = el.clientHeight || 420
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2))
  renderer.setSize(w, h, false)
  renderer.outputColorSpace = THREE.SRGBColorSpace
  renderer.toneMapping = THREE.LinearToneMapping
  renderer.toneMappingExposure = 1.18
  renderer.setClearAlpha(0)

  scene = new THREE.Scene()
  camera = new THREE.PerspectiveCamera(40, w / h, 0.1, 120)

  /* ---------- 灯光：正面平光把海洋打出通透的青蓝，左前补光勾出参考图那圈亮边 ----------
     three 新版按物理光照计（漫反射除以 π），环境光要给到 1.7 左右才够亮 */
  scene.add(new THREE.AmbientLight(0xcdeaf4, 2.15))
  const key = new THREE.DirectionalLight(0xffffff, 2.08)
  key.position.set(1.4, 1.2, 4.4)
  scene.add(key)
  /* 左前补光给足，否则贴图的深海蓝会在暗面压成一片黑 */
  const fill = new THREE.DirectionalLight(0x9fe8ff, 2.85)
  fill.position.set(-4.2, 1.2, 2.4)
  scene.add(fill)
  /* 左后青绿轮廓光：形成参考图左缘那一道亮边 */
  const rim = new THREE.DirectionalLight(0x2ff0dc, 1.88)
  rim.position.set(-4.0, -0.3, -1.6)
  scene.add(rim)

  /* ---------- 地球总容器：拖拽层 → 地轴倾斜 + 视差 ---------- */
  dragGroup = new THREE.Group()
  scene.add(dragGroup)
  globe = new THREE.Group()
  globe.rotation.z = -0.19          // 地轴倾斜 ≈ 11°
  dragGroup.add(globe)

  earthSpin = new THREE.Group()
  globe.add(earthSpin)

  /* 球心底色：贴图未到位时先是一颗深青球，不会出现黑洞 */
  const shellMat = track(new THREE.MeshBasicMaterial({ color: 0x0a2b3d }))
  const shell = new THREE.Mesh(track(new THREE.SphereGeometry(0.995, 64, 64)), shellMat)
  earthSpin.add(shell)

  const small = Math.min(w, h) < 420
  const maxAniso = Math.min(8, renderer.capabilities.getMaxAnisotropy())

  try {
    const dayTex = await loadTex(earthDayUrl)
    dayTex.anisotropy = maxAniso
    if (disposed) return

    const earthMat = track(new THREE.MeshPhongMaterial({
      map: dayTex,
      color: 0xffffff,
      specular: new THREE.Color(0x24505e),
      shininess: 40,
      emissive: new THREE.Color(0x0a2a3a),
      emissiveIntensity: 0.26,
    }))
    /* 深海蓝贴图偏暗：着色器内 gamma 提亮 + 轻微加青，得到参考图的通透青蓝 */
    earthMat.onBeforeCompile = (shader: Any) => {
      shader.fragmentShader = shader.fragmentShader.replace(
        '#include <map_fragment>',
        `#include <map_fragment>
         diffuseColor.rgb = pow(diffuseColor.rgb, vec3(0.74));
         diffuseColor.rgb *= vec3(1.02, 1.10, 1.20);`,
      )
    }
    const earth = new THREE.Mesh(track(new THREE.SphereGeometry(1, 96, 96)), earthMat)
    earthSpin.add(earth)
    shellMat.visible = false

    /* 夜景灯光：只做极轻的自发光，暗面有城市感但不发脏 */
    if (!small) {
      loadTex(earthNightUrl).then(tex => {
        if (disposed) return
        tex.anisotropy = maxAniso
        /* 改用 day 贴图做整体自发光提亮（见 earthMat），night 不再覆盖 emissive */
        earthMat.needsUpdate = true
      }).catch(() => {})
      /* 地形凹凸 + 海洋高光：远看提升质感，近看有起伏 */
      loadTex(earthTopoUrl, false).then(tex => {
        if (disposed) return
        earthMat.bumpMap = tex
        earthMat.bumpScale = 0.018
        earthMat.needsUpdate = true
      }).catch(() => {})
      loadTex(earthWaterUrl, false).then(tex => {
        if (disposed) return
        earthMat.specularMap = tex
        earthMat.needsUpdate = true
      }).catch(() => {})
    }
  } catch {
    /* 贴图挂了也保留深青球体，不算失败 */
  }

  /* ---------- 云层：独立球体，比地球略快 ---------- */
  try {
    const cloudTex = await loadTex(earthCloudUrl)
    cloudTex.anisotropy = maxAniso
    if (disposed) return
    cloudMat = track(new THREE.MeshLambertMaterial({
      map: cloudTex,
      transparent: true,
      opacity: 0.62,
      depthWrite: false,
      blending: THREE.NormalBlending,
    }))
    clouds = new THREE.Mesh(track(new THREE.SphereGeometry(1.012, 96, 96)), cloudMat)
    clouds.renderOrder = 1
    earthSpin.add(clouds)
  } catch { /* 无云层不影响 */ }

  /* ---------- 大气层：Fresnel 边缘散射（青蓝），并按视角左右做偏置 ----------
     参考图的亮边集中在左下缘，右上是暗的，所以不能用一圈均匀的辉光 */
  atmoMat = track(new THREE.ShaderMaterial({
    transparent: true,
    side: THREE.BackSide,
    depthWrite: false,
    blending: THREE.AdditiveBlending,
    uniforms: {
      uColor: { value: new THREE.Color(0x55dff2) },
      uColor2: { value: new THREE.Color(0x2a92dc) },
      uPower: { value: 1.95 },
      uOpacity: { value: 1.0 },
    },
    vertexShader: `
      varying vec3 vN;
      void main() {
        vN = normalize(normalMatrix * normal);
        gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
      }`,
    fragmentShader: `
      varying vec3 vN;
      uniform vec3 uColor; uniform vec3 uColor2;
      uniform float uPower; uniform float uOpacity;
      void main() {
        float d = dot(vN, vec3(0.0, 0.0, 1.0));
        float rim = pow(clamp(0.92 - d, 0.0, 1.0), uPower);
        /* 左下方偏亮：vN.x 越小（越靠左）越亮，但不要做成硬边 */
        float side = clamp(0.60 - vN.x * 0.44 - vN.y * 0.16, 0.34, 1.0);
        vec3 col = mix(uColor2, uColor, clamp(rim * 2.0, 0.0, 1.0));
        gl_FragColor = vec4(col, 1.0) * rim * side * uOpacity * 0.95;
      }`,
  }))
  atmo = new THREE.Mesh(track(new THREE.SphereGeometry(1.13, 64, 64)), atmoMat)
  atmo.renderOrder = 0
  scene.add(atmo)

  /* 紧贴球面的一道亮边：参考图球缘那根 1px 的青白高光 */
  const edgeMat = track(new THREE.ShaderMaterial({
    transparent: true,
    side: THREE.BackSide,
    depthWrite: false,
    blending: THREE.AdditiveBlending,
    uniforms: { uColor: { value: new THREE.Color(0xbafff8) } },
    vertexShader: `
      varying vec3 vN;
      void main() {
        vN = normalize(normalMatrix * normal);
        gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
      }`,
    fragmentShader: `
      varying vec3 vN; uniform vec3 uColor;
      void main() {
        float d = dot(vN, vec3(0.0, 0.0, 1.0));
        float rim = pow(clamp(0.995 - d, 0.0, 1.0), 9.0);
        float side = clamp(0.75 - vN.x * 0.45, 0.25, 1.0);
        gl_FragColor = vec4(uColor, 1.0) * rim * side * 0.95;
      }`,
  }))
  const edge = new THREE.Mesh(track(new THREE.SphereGeometry(1.012, 64, 64)), edgeMat)
  edge.renderOrder = 1
  scene.add(edge)

  /* 外层极淡的一圈散射，把地球"焊"进星空 */
  const atmo2Mat = track(new THREE.ShaderMaterial({
    transparent: true,
    side: THREE.BackSide,
    depthWrite: false,
    blending: THREE.AdditiveBlending,
    uniforms: { uColor: { value: new THREE.Color(0x1fa8cc) }, uPower: { value: 1.55 } },
    vertexShader: `
      varying vec3 vN;
      void main() {
        vN = normalize(normalMatrix * normal);
        gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
      }`,
    fragmentShader: `
      varying vec3 vN; uniform vec3 uColor; uniform float uPower;
      void main() {
        float d = dot(vN, vec3(0.0, 0.0, 1.0));
        float rim = pow(clamp(0.94 - d, 0.0, 1.0), uPower);
        float side = clamp(0.44 - vN.x * 0.55, 0.16, 1.0);
        gl_FragColor = vec4(uColor, 1.0) * rim * side * 0.32;
      }`,
  }))
  const atmo2 = new THREE.Mesh(track(new THREE.SphereGeometry(1.36, 48, 48)), atmo2Mat)
  atmo2.renderOrder = 0
  scene.add(atmo2)

  /* ---------- 星场：三层不同远近（密度加密 + 冷暖色点缀） ---------- */
  const starTex = dotTexture(THREE, 0.28)
  const starSpec = [
    { n: 520, r0: 7.5, r1: 11, size: 0.028, op: 0.92, z: -6 },
    { n: 350, r0: 12, r1: 17, size: 0.044, op: 0.62, z: -12 },
    { n: 240, r0: 18, r1: 26, size: 0.06, op: 0.40, z: -20 },
  ]
  /* 冷暖分布：绝大多数冰青白，点缀暖金与淡蓝，更接近真实星场 */
  const starTints: Array<[Any, number]> = [
    [new THREE.Color(0xcdeef7), 0.82],
    [new THREE.Color(0xffe2b8), 0.10],
    [new THREE.Color(0xa8e8ff), 0.08],
  ]
  const pickTint = () => {
    const r = Math.random()
    let acc = 0
    for (const [c, w] of starTints) { acc += w; if (r <= acc) return c }
    return starTints[0][0]
  }
  for (const s of starSpec) {
    const pos = new Float32Array(s.n * 3)
    const col = new Float32Array(s.n * 3)
    for (let i = 0; i < s.n; i++) {
      const v = new THREE.Vector3().randomDirection().multiplyScalar(s.r0 + Math.random() * (s.r1 - s.r0))
      pos[i * 3] = v.x; pos[i * 3 + 1] = v.y; pos[i * 3 + 2] = v.z + s.z
      const tint = pickTint()
      const k = 0.72 + Math.random() * 0.28
      col[i * 3] = tint.r * k; col[i * 3 + 1] = tint.g * k; col[i * 3 + 2] = tint.b * k
    }
    const g = track(new THREE.BufferGeometry())
    g.setAttribute('position', new THREE.BufferAttribute(pos, 3))
    g.setAttribute('color', new THREE.BufferAttribute(col, 3))
    const m = track(new THREE.PointsMaterial({
      size: s.size, map: starTex, color: 0xffffff, vertexColors: true, transparent: true,
      opacity: s.op, depthWrite: false, sizeAttenuation: true,
      blending: THREE.AdditiveBlending,
    }))
    const pts = new THREE.Points(g, m)
    scene.add(pts)
    starLayers.push({ pts, base: s.op, z: s.z })
  }

  /* ---------- 闪烁亮星层：独立着色器，逐星相位错开，缓慢明灭 ---------- */
  const TW_VS = `
    attribute float aSize;
    attribute float aPhase;
    uniform float uTime;
    uniform float uAmp;
    varying float vA;
    void main() {
      vec4 mv = modelViewMatrix * vec4(position, 1.0);
      float tw = 0.62 + uAmp * 0.38 * sin(uTime * (0.9 + aPhase * 1.7) + aPhase * 6.2831);
      vA = tw;
      gl_PointSize = aSize * (240.0 / -mv.z) * (0.72 + 0.55 * tw);
      gl_Position = projectionMatrix * mv;
    }`
  const TW_FS = `
    uniform vec3 uColor;
    varying float vA;
    void main() {
      float d = length(gl_PointCoord - vec2(0.5));
      float a = smoothstep(0.5, 0.06, d) * vA;
      gl_FragColor = vec4(uColor, a);
    }`
  const twGeo = track(new THREE.BufferGeometry())
  {
    const n = 160
    const pos = new Float32Array(n * 3)
    const size = new Float32Array(n)
    const phase = new Float32Array(n)
    for (let i = 0; i < n; i++) {
      const v = new THREE.Vector3().randomDirection().multiplyScalar(8.5 + Math.random() * 9)
      pos[i * 3] = v.x; pos[i * 3 + 1] = v.y; pos[i * 3 + 2] = v.z - 10
      size[i] = 0.05 + Math.random() * 0.1
      phase[i] = Math.random()
    }
    twGeo.setAttribute('position', new THREE.BufferAttribute(pos, 3))
    twGeo.setAttribute('aSize', new THREE.BufferAttribute(size, 1))
    twGeo.setAttribute('aPhase', new THREE.BufferAttribute(phase, 1))
  }
  twinkleMat = track(new THREE.ShaderMaterial({
    vertexShader: TW_VS,
    fragmentShader: TW_FS,
    uniforms: {
      uTime: { value: 0 },
      uAmp: { value: reduced() ? 0.25 : 1.0 },
      uColor: { value: new THREE.Color(0xdff6ff) },
    },
    transparent: true,
    depthWrite: false,
    blending: THREE.AdditiveBlending,
  }))
  const twPts = new THREE.Points(twGeo, twinkleMat)
  scene.add(twPts)

  /* ---------- 背景星云：三团程序化柔光精灵，垫在地球与星场之后 ---------- */
  const nebTexA = nebulaTexture(THREE, [
    { x: 0.42, y: 0.45, r: 0.42, c: 'rgba(31, 214, 200, .52)' },
    { x: 0.68, y: 0.36, r: 0.30, c: 'rgba(58, 170, 226, .38)' },
    { x: 0.28, y: 0.62, r: 0.26, c: 'rgba(122, 108, 240, .30)' },
  ])
  const nebTexB = nebulaTexture(THREE, [
    { x: 0.5, y: 0.52, r: 0.46, c: 'rgba(63, 127, 232, .44)' },
    { x: 0.32, y: 0.4, r: 0.28, c: 'rgba(31, 214, 200, .30)' },
  ])
  const nebTexC = nebulaTexture(THREE, [
    { x: 0.55, y: 0.5, r: 0.4, c: 'rgba(122, 108, 240, .40)' },
    { x: 0.36, y: 0.6, r: 0.24, c: 'rgba(31, 214, 200, .26)' },
  ])
  const nebSpec = [
    { x: -7.2, y: 3.6, z: -20, s: 15, op: 0.20, tex: nebTexA },
    { x: 6.8, y: -3.2, z: -23, s: 19, op: 0.15, tex: nebTexB },
    { x: 2.5, y: 5.2, z: -17, s: 11, op: 0.12, tex: nebTexC },
  ]
  for (const n of nebSpec) {
    const m = track(new THREE.SpriteMaterial({
      map: n.tex, transparent: true, opacity: n.op,
      blending: THREE.AdditiveBlending, depthWrite: false,
      rotation: Math.random() * Math.PI,
    }))
    const sp = new THREE.Sprite(m)
    sp.position.set(n.x, n.y, n.z)
    sp.scale.set(n.s, n.s * 0.72, 1)
    scene.add(sp)
    nebulae.push({ sp, m, base: n.op, ph: Math.random() * 6.28 })
  }

  /* ---------- 国际空间站：真实位置（open-notify JSONP，失败自动隐藏） ---------- */
  issGroup = new THREE.Group()
  const issDotTex = dotTexture(THREE, 0.3)
  const issCore = new THREE.Sprite(track(new THREE.SpriteMaterial({
    map: issDotTex, color: 0xbff4ff, transparent: true,
    blending: THREE.AdditiveBlending, depthWrite: false,
  })))
  issCore.scale.set(0.085, 0.085, 1)
  issGlow = new THREE.Sprite(track(new THREE.SpriteMaterial({
    map: issDotTex, color: 0x2fd8e8, transparent: true, opacity: 0.55,
    blending: THREE.AdditiveBlending, depthWrite: false,
  })))
  issGlow.scale.set(0.2, 0.2, 1)
  issGroup.add(issCore)
  issGroup.add(issGlow)
  issGroup.visible = false
  dragGroup.add(issGroup)

  function placeISS(lat: number, lon: number) {
    const la = (lat * Math.PI) / 180, lo = (lon * Math.PI) / 180
    const r = 1.58
    issGroup.position.set(r * Math.cos(la) * Math.sin(lo), r * Math.sin(la), r * Math.cos(la) * Math.cos(lo))
    issGroup.visible = true
  }
  function pollISS() {
    if (disposed || reduced()) return
    const cb = "issCb" + Math.floor(Math.random() * 1e6)
    const sc = document.createElement("script")
    const timer = window.setTimeout(() => { cleanup(); if (++issFail >= 3) { issGroup.visible = false; if (issTimer) { clearInterval(issTimer); issTimer = 0 } } }, 8000)
    function cleanup() {
      window.clearTimeout(timer)
      /* 超时后响应可能迟到：把回调降级为空函数而不是删除，避免 Uncaught ReferenceError */
      ;(window as Any)[cb] = function () {}
      sc.remove()
    }
    ;(window as Any)[cb] = (d: Any) => {
      cleanup()
      issFail = 0
      const p = d && d.iss_position ? { lat: +d.iss_position.latitude, lon: +d.iss_position.longitude } : null
      if (p && !Number.isNaN(p.lat) && !Number.isNaN(p.lon)) placeISS(p.lat, p.lon)
    }
    sc.src = "http://api.open-notify.org/iss-now.json?callback=" + cb
    sc.onerror = () => { cleanup(); if (++issFail >= 3) { issGroup.visible = false; if (issTimer) { clearInterval(issTimer); issTimer = 0 } } }
    document.head.appendChild(sc)
  }
  issTimer = window.setInterval(pollISS, 8000)
  pollISS()

  /* ---------- 星环：真 3D 圆环绕 X 轴倾斜 → 投影成椭圆 ----------
     参考图里只有一条主环是「细白实线」，其余是极淡的细环与点状虚线环；
     近端亮 / 远端压暗靠顶点色实现，球体遮挡交给深度测试自动完成。 */
  const ringGradient = (geo: Any, a: number, base: number, near: number, far: number) => {
    const pos = geo.attributes.position
    const col = new Float32Array(pos.count * 3)
    const c = new THREE.Color(base)
    for (let i = 0; i < pos.count; i++) {
      const t = Math.max(-1, Math.min(1, pos.getZ(i) / a))
      let k = far + (near - far) * (t * 0.5 + 0.5)
      /* 椭圆左右两端两条弧线会挤在一起，做一段平滑收尾渐隐，别像被画布切断 */
      const xr = Math.min(1, Math.abs(pos.getX(i) / a))
      k *= 1 - 0.72 * Math.pow(Math.max(0, (xr - 0.66) / 0.34), 1.3)
      col[i * 3] = c.r * k; col[i * 3 + 1] = c.g * k; col[i * 3 + 2] = c.b * k
    }
    geo.setAttribute('color', new THREE.BufferAttribute(col, 3))
  }

  const RING_SPEC = [
    /* 主环：细、亮、白中带青，正面弧形从地球下半部斜穿到右上（参考图最醒目的一条） */
    { a: 1.54, tilt: 0.37, rotZ: 0.15, tube: 0.0050, glow: 0.018, glowA: 0.17, alpha: 0.92, color: 0xeafffc, period: 29, dir: 1, particle: true, head: 0.26 },
    /* 交叉环：反向倾角，形成参考图那种两头相交的环带，压得很淡 */
    { a: 1.62, tilt: 0.46, rotZ: -0.34, tube: 0.0024, glow: 0.013, glowA: 0.16, alpha: 0.36, color: 0xbff6f1, period: 44, dir: -1, particle: true, head: 0.17 },
    /* 紧贴本体的第二条亮线：和主环同组倾角，做出「双线」观感 */
    { a: 1.66, tilt: 0.22, rotZ: 0.62, tube: 0.0014, glow: 0.007, glowA: 0.10, alpha: 0.38, color: 0xa8f0ea, period: 58, dir: -1, particle: true, head: 0.14 },
  ]

  const flareTex = flareTexture(THREE)

  for (const s of RING_SPEC) {
    /* 嵌套 group：先绕 X 轴倒向屏幕外，再在屏幕平面内旋转 → 得到任意倾角的椭圆 */
    const tiltG = new THREE.Group()
    tiltG.rotation.x = Math.asin(s.tilt)
    tiltG.rotation.z = s.rotZ
    globe.add(tiltG)

    /* 圆环本体：torus 默认在 XY 平面，转 90° 躺进 XZ 平面 */
    const torusG = track(new THREE.TorusGeometry(s.a, s.tube, 6, 420))
    torusG.rotateX(Math.PI / 2)
    ringGradient(torusG, s.a, s.color, 1.85, 0.34)
    const coreMat = track(new THREE.MeshBasicMaterial({
      color: 0xffffff, vertexColors: true, transparent: true, opacity: s.alpha,
      depthWrite: false, blending: THREE.AdditiveBlending, toneMapped: false,
    }))
    const core = new THREE.Mesh(torusG, coreMat)
    core.renderOrder = 3
    tiltG.add(core)

    /* 环外的柔光：比本体粗一点点的淡管，模拟空气感，别做太厚否则像塑料绳 */
    const glowG = track(new THREE.TorusGeometry(s.a, s.glow, 6, 420))
    glowG.rotateX(Math.PI / 2)
    ringGradient(glowG, s.a, s.color, 1.4, 0.30)
    const glowMat = track(new THREE.MeshBasicMaterial({
      color: 0xffffff, vertexColors: true, transparent: true, opacity: s.glowA,
      depthWrite: false, blending: THREE.AdditiveBlending, toneMapped: false,
    }))
    const glow = new THREE.Mesh(glowG, glowMat)
    glow.renderOrder = 2
    tiltG.add(glow)

    if (!s.particle) continue

    /* 轨道光点：四角星芒 + 柔光晕 + 拖尾，和参考图上的星形闪光一致 */
    const headMat = track(new THREE.SpriteMaterial({
      map: flareTex, color: 0xeafffd, transparent: true, opacity: 1,
      depthWrite: false, blending: THREE.AdditiveBlending, toneMapped: false,
    }))
    const head = new THREE.Sprite(headMat)
    head.scale.setScalar(s.head)
    head.renderOrder = 6
    tiltG.add(head)

    const haloMat2 = track(new THREE.SpriteMaterial({
      map: flareTex, color: 0x5ff0e2, transparent: true, opacity: 0.52,
      depthWrite: false, blending: THREE.AdditiveBlending, toneMapped: false,
    }))
    const halo = new THREE.Sprite(haloMat2)
    halo.scale.setScalar(s.head * 3.6)
    halo.renderOrder = 5
    tiltG.add(halo)
    ;(head as Any).userData.halo = halo

    const TRAIL = 30
    const trailGeo = track(new THREE.BufferGeometry())
    const tPos = new Float32Array(TRAIL * 3)
    const tCol = new Float32Array(TRAIL * 3)
    trailGeo.setAttribute('position', new THREE.BufferAttribute(tPos, 3))
    trailGeo.setAttribute('color', new THREE.BufferAttribute(tCol, 3))
    const trailMat = track(new THREE.PointsMaterial({
      size: 0.042, map: starTex, vertexColors: true, transparent: true,
      opacity: 0.55, depthWrite: false, sizeAttenuation: true,
      blending: THREE.AdditiveBlending, toneMapped: false,
    }))
    const trail = new THREE.Points(trailGeo, trailMat)
    trail.renderOrder = 4
    tiltG.add(trail)

    rings.push({
      group: tiltG, head, trail, trailGeo, trailColors: tCol,
      a: s.a, speed: (Math.PI * 2) / s.period, dir: s.dir,
      rotZ: s.rotZ, wobble: s.rotZ,
      alpha: s.alpha,
    })
  }

  /* ---------- 虚线环：参考图里那圈点状轨道，用点阵伪造虚线 ---------- */
  {
    const dA = 1.78
    const dTilt = 0.26
    const dRotZ = -0.58
    const g = new THREE.Group()
    g.rotation.x = Math.asin(dTilt)
    g.rotation.z = dRotZ
    globe.add(g)

    const N = 150
    const pos = new Float32Array(N * 3)
    for (let i = 0; i < N; i++) {
      const a = (i / N) * Math.PI * 2
      pos[i * 3] = dA * Math.cos(a)
      pos[i * 3 + 1] = 0
      pos[i * 3 + 2] = dA * Math.sin(a)
    }
    const dGeo = track(new THREE.BufferGeometry())
    dGeo.setAttribute('position', new THREE.BufferAttribute(pos, 3))
    const dMat = track(new THREE.PointsMaterial({
      size: 0.015, map: starTex, color: 0xcaf8f4, transparent: true,
      opacity: 0.42, depthWrite: false, sizeAttenuation: true,
      blending: THREE.AdditiveBlending, toneMapped: false,
    }))
    const dots = new THREE.Points(dGeo, dMat)
    dots.renderOrder = 3
    g.add(dots)
    dashRing = { group: g, speed: 0.028 }
  }

  /* ---------- 尺寸：按容器自适应，环永远不出框 ---------- */
  const still = reduced()          // 减少动效：静止首帧后不再空转
  let stillDirty = true
  let last = performance.now()
  const resize = () => {
    if (!renderer || !camera) return
    const W = el.clientWidth || 560
    const H = el.clientHeight || 420
    renderer.setSize(W, H, false)
    camera.aspect = W / H
    /* 最外圈虚线环横向撑到 2.03R、球体加大气纵向到 1.18R；
       留足边距，保证整个地球 + 三条星环都完整落在容器内不出框 */
    const halfW = 2.35
    const halfH = 1.16
    const tan = Math.tan(THREE.MathUtils.degToRad(20))
    const dist = Math.max(halfW / (tan * camera.aspect), halfH / tan) * 1.02
    camera.position.set(0, 0, dist)
    camera.updateProjectionMatrix()
    stillDirty = true
    if (still && !raf && !disposed && frame) { last = performance.now(); raf = requestAnimationFrame(frame) }
  }
  resize()

  const ro = new ResizeObserver(resize)
  ro.observe(el)
  ;(el as Any).__ro = ro

  /* ---------- 交互：鼠标在左栏移动 → 极轻视差 ---------- */
  const onMove = (e: PointerEvent) => {
    const r = el.getBoundingClientRect()
    tx = ((e.clientX - r.left) / r.width - 0.5) * 2
    ty = ((e.clientY - r.top) / r.height - 0.5) * 2
    hovered = true
  }
  const onLeave = () => { hovered = false; tx = 0; ty = 0 }
  el.addEventListener('pointermove', onMove, { passive: true })
  el.addEventListener('pointerleave', onLeave)
  ;(el as Any).__off = () => {
    el.removeEventListener('pointermove', onMove)
    el.removeEventListener('pointerleave', onLeave)
  }

  /* ---------- 交互：按住拖拽旋转地球（左右转经度，上下调倾角，松手带惯性） ---------- */
  const onDragDown = (e: PointerEvent) => {
    dragging = true
    dragPX = e.clientX; dragPY = e.clientY
    dragVX = 0; dragVY = 0
    try { (el as Any).setPointerCapture(e.pointerId) } catch { /* noop */ }
    el.classList.add('is-drag')
  }
  const onDragMove = (e: PointerEvent) => {
    if (!dragging || !dragGroup) return
    const dx = (e.clientX - dragPX) / (el.clientWidth || 1)
    const dy = (e.clientY - dragPY) / (el.clientHeight || 1)
    dragPX = e.clientX; dragPY = e.clientY
    const ry = dx * 3.4, rx = dy * 2.1
    dragGroup.rotation.y += ry
    dragGroup.rotation.x = Math.max(-0.55, Math.min(0.55, dragGroup.rotation.x + rx))
    dragVX = ry; dragVY = rx
    stillDirty = true
  }
  const onDragUp = (e: PointerEvent) => {
    if (!dragging) return
    dragging = false
    el.classList.remove('is-drag')
    try { (el as Any).releasePointerCapture(e.pointerId) } catch { /* noop */ }
  }
  el.addEventListener('pointerdown', onDragDown)
  el.addEventListener('pointermove', onDragMove)
  el.addEventListener('pointerup', onDragUp)
  el.addEventListener('pointercancel', onDragUp)
  ;(el as Any).__dragOff = () => {
    el.removeEventListener('pointerdown', onDragDown)
    el.removeEventListener('pointermove', onDragMove)
    el.removeEventListener('pointerup', onDragUp)
    el.removeEventListener('pointercancel', onDragUp)
  }

  /* ---------- 动画 ---------- */
  let tAcc = 0
  const ang = [0.6, 2.1]

  /* 星环光点与拖尾：抽成函数，静止模式也要先摆一次位置，
     否则光点会全部堆在球心（等于消失） */
  const stepRings = (dt: number, animate: boolean) => {
    for (let i = 0; i < rings.length; i++) {
      const r = rings[i]
      if (animate) {
        r.group.rotation.z = r.wobble + Math.sin(tAcc * 0.22 + i * 1.7) * 0.03
        ang[i] += dt * r.speed * r.dir
      }
      const c = Math.cos(ang[i]), s2 = Math.sin(ang[i])
      r.head.position.set(r.a * c, 0, r.a * s2)
      const halo = r.head.userData?.halo
      if (halo) halo.position.copy(r.head.position)

      /* 拖尾：回溯一小段角度，越往后越暗 */
      const pos = r.trailGeo.attributes.position.array as Float32Array
      const col = r.trailColors
      const N = pos.length / 3
      const back = 0.30 * r.dir
      for (let k = 0; k < N; k++) {
        const a2 = ang[i] - back * (k / (N - 1))
        pos[k * 3] = r.a * Math.cos(a2)
        pos[k * 3 + 1] = 0
        pos[k * 3 + 2] = r.a * Math.sin(a2)
        const f = Math.pow(1 - k / N, 2.1)
        col[k * 3] = f * 0.55
        col[k * 3 + 1] = f * 0.95
        col[k * 3 + 2] = f * 0.92
      }
      r.trailGeo.attributes.position.needsUpdate = true
      r.trailGeo.attributes.color.needsUpdate = true
    }
  }
  stepRings(0, false)

  frame = (now: number) => {
    raf = 0
    if (disposed || !renderer || !frame) return
    if (still) {
      /* 减少动效：只画一次，之后仅在尺寸变化时重绘，不做无谓的 60fps 空转 */
      if (stillDirty) { stillDirty = false; renderer.render(scene, camera) }
      if (!ready) { ready = true; el.classList.add('is-ready') }
      return
    }
    const dt = Math.min((now - last) / 1000, 0.05)
    last = now
    if (visible) {
      tAcc += dt
      /* 地球自转：约 90s 一圈；云层再快 35% */
      earthSpin.rotation.y += dt * (Math.PI * 2 / 90)
      if (clouds) clouds.rotation.y += dt * (Math.PI * 2 / 90) * 0.35

      /* 视差：缓动跟随，不抢注意力 */
      px += ((hovered ? tx : 0) - px) * Math.min(1, dt * 3)
      py += ((hovered ? ty : 0) - py) * Math.min(1, dt * 3)
      globe.rotation.y = px * 0.10
      globe.rotation.x = py * 0.055

      /* 拖拽惯性：松手后角速度衰减，慢慢停住 */
      if (!dragging && dragGroup && (Math.abs(dragVX) > 1e-4 || Math.abs(dragVY) > 1e-4)) {
        dragGroup.rotation.y += dragVX
        dragGroup.rotation.x = Math.max(-0.55, Math.min(0.55, dragGroup.rotation.x + dragVY))
        dragVX *= 0.93; dragVY *= 0.93
      }

      stepRings(dt, true)
      if (dashRing) dashRing.group.rotation.z += dt * dashRing.speed

      /* 星场：只做极慢的整体旋转，制造纵深 */
      for (const s of starLayers) s.pts.rotation.y = tAcc * 0.004 + s.z * 0.001
      if (issGlow && issGroup && issGroup.visible) {
        issGlow.material.opacity = 0.4 + 0.2 * Math.sin(tAcc * 2.2)
      }
      if (twinkleMat) twinkleMat.uniforms.uTime.value = tAcc
      for (const n of nebulae) {
        n.sp.material.rotation += dt * 0.012
        n.sp.material.opacity = n.base * (0.85 + 0.15 * Math.sin(tAcc * 0.35 + n.ph))
      }
      atmoMat.uniforms.uOpacity.value = 0.94 + Math.sin(tAcc * 0.9) * 0.06
    }
    renderer.render(scene, camera)
    /* 第一帧真正画完再淡入，避免出现「空一帧」的闪白 */
    if (!ready) { ready = true; el.classList.add('is-ready') }
    raf = requestAnimationFrame(frame)
  }
  raf = requestAnimationFrame(frame)

  /* 开发环境探针：供自动化测试读取自转角度 / 光点位置（生产构建静态剔除） */
  if (import.meta.env.DEV) {
    ;(window as Any).__esProbe = {
      spin: () => earthSpin.rotation.y,
      dots: () => rings.map((r: Any) => +r.head.position.x.toFixed(4)),
      fallback: () => fallback.value,
    }
  }

  /* 页面切到后台就停帧，省电 */
  const onVis = () => {
    visible = !document.hidden
    if (visible && !raf && !disposed) { last = performance.now(); raf = requestAnimationFrame(frame) }
  }
  document.addEventListener('visibilitychange', onVis)
  ;(el as Any).__vis = onVis
})

/* ---------- 卸载：停循环 → 摘监听 → 释放 GPU 资源 ---------- */
onBeforeUnmount(() => {
  disposed = true
  if (raf) { cancelAnimationFrame(raf); raf = 0 }
  const el = host.value as Any
  if (el) {
    try { el.__ro?.disconnect() } catch { /* noop */ }
    try { el.__off?.() } catch { /* noop */ }
    try { el.__dragOff?.() } catch { /* noop */ }
    if (issTimer) { clearInterval(issTimer); issTimer = 0 }
    if (el.__vis) document.removeEventListener('visibilitychange', el.__vis)
  }
  for (const o of bin) {
    try { o?.dispose?.() } catch { /* noop */ }
  }
  bin.length = 0
  try {
    renderer?.dispose?.()
    renderer?.forceContextLoss?.()
  } catch { /* noop */ }
  renderer = null
  scene = null
  camera = null
  globe = null
  earthSpin = null
  clouds = null
  cloudMat = null
  atmo = null
  atmoMat = null
  starLayers = []
  rings.length = 0
})
</script>

<style scoped>
.es {
  position: relative;
  width: 100%;
  height: 100%;
  display: grid;
  place-items: center;
  cursor: grab;              /* 提示可拖拽旋转 */
  touch-action: pan-y;       /* 横向拖地球，纵向留给页面滚动（移动端） */
}
.es.is-drag { cursor: grabbing; }
.es.is-drag .es-aura { filter: blur(16px) brightness(1.18); }
/* 球后光晕：WebGL 首帧前先撑住画面，也作为地球的柔光底 —— 贴住球体大小，别散成一团 */
.es-aura {
  position: absolute;
  left: 50%; top: 50%;
  height: 94%;
  aspect-ratio: 1;
  transform: translate(-50%, -50%);
  border-radius: 50%;
  background:
    radial-gradient(closest-side, rgba(24, 214, 192, .34), rgba(67, 185, 223, .14) 54%, transparent 74%);
  filter: blur(16px);
  pointer-events: none;
}
.es-cv {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  display: block;
  opacity: 0;
  transition: opacity .9s ease;
}
.es.is-ready .es-cv { opacity: 1; }

/* 降级球：没有 WebGL 时的纯 CSS 星球，仍保留 3D 观感 */
.es-fb {
  position: relative;
  width: min(62%, 320px);
  aspect-ratio: 1;
  border-radius: 50%;
  background:
    radial-gradient(circle at 32% 26%, rgba(255,255,255,.20), transparent 38%),
    radial-gradient(circle at 68% 78%, rgba(24,214,192,.42), transparent 55%),
    radial-gradient(circle at 40% 34%, #16506b, #0b2c40 58%, #061626 100%);
  box-shadow:
    inset 0 -18px 40px rgba(2, 12, 22, .85),
    inset 0 0 0 1px rgba(120, 226, 255, .18),
    0 0 60px rgba(24, 214, 192, .30),
    0 0 140px rgba(24, 214, 192, .16);
  animation: esFbBreath 7s ease-in-out infinite;
}
.es-fb::after {
  content: '';
  position: absolute;
  inset: -12%;
  border-radius: 50%;
  border: 1px solid rgba(24, 214, 192, .28);
  transform: rotate(-18deg) scaleY(.58);
}
@keyframes esFbBreath {
  0%, 100% { filter: brightness(1); }
  50%      { filter: brightness(1.12); }
}
@media (prefers-reduced-motion: reduce) {
  .es-cv { transition: none; }
  .es-fb { animation: none; }
}
</style>
