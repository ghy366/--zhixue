<template>
  <div ref="wrap" class="atlas-scene" :class="{ 'is-ready': ready, 'is-fallback': fallback }" aria-hidden="true">
    <canvas ref="cv" class="atlas-cv"></canvas>
  </div>
</template>

<script setup lang="ts">
/**
 * 智学选课 · 学域星图（Atlas）— Three.js 实时 3D 主视觉
 *
 * 主题依据：系统名「智学选课」→ 每一门课是一颗星，选课就是把自己的星图连起来。
 * 场景构成：知核（呼吸 Shader）+ 课程星座（单 draw call 点精灵）+ 连线 +
 *          三条选课轨道与流动光点 + 星尘 + 偶发流星 + 交互涟漪 + Bloom。
 *
 * 遵循 three.js r186 skill 规范（E:\buyyd数据缓存\skills\threejs-skills）：
 *   - 自定义 ShaderMaterial / Points attribute、EffectComposer + OutputPass
 *   - 按需动态 import、ResizeObserver、DPR 上限、完整 dispose
 *   - reduced-motion 停帧、窄屏/高 DPI 关闭 Bloom
 */
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import type * as ThreeNS from 'three'

const props = defineProps<{ success?: boolean }>()

const wrap = ref<HTMLDivElement | null>(null)
const cv = ref<HTMLCanvasElement | null>(null)
const ready = ref(false)
const fallback = ref(false)

const JADE = '#74e4c4'
const GOLD = '#e7c58c'
const PAPER = '#eaf4f1'
const NIGHT = 0x0a1621

let renderer: ThreeNS.WebGLRenderer | null = null
let composer: { render: () => void; setSize: (w: number, h: number) => void; dispose: () => void } | null = null
let bloom: { strength: number } | null = null
let raf = 0
let ro: ResizeObserver | null = null
let disposed = false
let energy = 0
let burst = 0
let burstAt = -10
let pendingBurst = false
const disposables: Array<{ dispose: () => void }> = []
const pointer = { x: 0, y: 0, tx: 0, ty: 0 }
let onSuccessHook: (() => void) | null = null
let rippleCleanup: (() => void) | null = null

watch(() => props.success, (v) => {
  if (v) {
    pendingBurst = true
    onSuccessHook?.()
  }
})

function onPointerMove(e: PointerEvent) {
  pointer.tx = (e.clientX / window.innerWidth - 0.5) * 2
  pointer.ty = (e.clientY / window.innerHeight - 0.5) * 2
}
function onFocusIn(e: FocusEvent) {
  const tag = (e.target as HTMLElement | null)?.tagName || ''
  if (/^(INPUT|SELECT|TEXTAREA|BUTTON)$/.test(tag)) energy = Math.max(energy, 0.72)
}
function onPointerDown() { energy = Math.max(energy, 0.9) }

// 固定种子：每次进入登录页星图构图一致，不随机跳变
function mulberry32(seed: number) {
  return () => {
    seed |= 0; seed = (seed + 0x6d2b79f5) | 0
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

onMounted(async () => {
  const host = wrap.value
  const canvas = cv.value
  if (!host || !canvas) return

  let THREE: typeof import('three')
  try {
    THREE = await import('three')
  } catch {
    fallback.value = true
    return
  }
  if (disposed) return

  let gl: ThreeNS.WebGLRenderer
  try {
    gl = new THREE.WebGLRenderer({ canvas, alpha: true, antialias: true, powerPreference: 'high-performance' })
  } catch {
    fallback.value = true
    return
  }
  renderer = gl
  gl.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.75))
  gl.setClearColor(NIGHT, 0)
  gl.outputColorSpace = THREE.SRGBColorSpace
  gl.toneMapping = THREE.ACESFilmicToneMapping
  gl.toneMappingExposure = 1.0

  const scene = new THREE.Scene()
  scene.fog = new THREE.FogExp2(NIGHT, 0.021)
  const camera = new THREE.PerspectiveCamera(46, 1, 0.1, 120)
  camera.position.set(0, 0.1, 14)

  const keep = <T extends { dispose: () => void }>(o: T): T => { disposables.push(o); return o }
  const rand = mulberry32(20261111)

  // ---------- 灯光 ----------
  scene.add(new THREE.AmbientLight(0x8fb8c4, 0.75))
  const key = new THREE.PointLight(JADE, 13, 26)
  key.position.set(-5.2, 3.4, 6)
  scene.add(key)
  const warm = new THREE.PointLight(GOLD, 8, 24)
  warm.position.set(5.4, -2.6, 4)
  scene.add(warm)

  // ---------- 主组：宽屏偏左，窄屏居中 ----------
  const root = new THREE.Group()
  // 主体落在左右两栏之间的空隙：不压标题、不压读数、不压登录卡
  root.position.set(-1.7, -0.28, -0.6)
  scene.add(root)

  const uTime = { value: 0 }
  const uEnergy = { value: 0 }
  const uJade = { value: new THREE.Color(JADE) }
  const uGold = { value: new THREE.Color(GOLD) }
  const uWhite = { value: new THREE.Color(PAPER) }
  const uPointer = { value: new THREE.Vector2(0, 0) }
  const uBurst = { value: 0 }

  // ---------- 知核：菲涅尔 + 顶点呼吸形变 ----------
  const coreMat = keep(new THREE.ShaderMaterial({
    uniforms: { uTime, uEnergy, uBurst, uPointer, uJade, uGold, uWhite },
    transparent: true,
    depthWrite: false,
    side: THREE.FrontSide,
    vertexShader: /* glsl */`
      uniform float uTime;
      uniform float uEnergy;
      varying vec3 vNormalV;
      varying vec3 vPos;
      varying float vWave;
      void main() {
        vec3 p = position;
        float w = sin(p.x * 2.6 + uTime * 0.9)
                * sin(p.y * 2.2 - uTime * 0.7)
                * sin(p.z * 2.4 + uTime * 0.5);
        p += normal * w * (0.052 + uEnergy * 0.085);
        vWave = w;
        vNormalV = normalize(normalMatrix * normal);
        vPos = p;
        gl_Position = projectionMatrix * modelViewMatrix * vec4(p, 1.0);
      }
    `,
    fragmentShader: /* glsl */`
      uniform float uTime;
      uniform float uEnergy;
      uniform float uBurst;
      uniform vec2 uPointer;
      uniform vec3 uJade;
      uniform vec3 uGold;
      uniform vec3 uWhite;
      varying vec3 vNormalV;
      varying vec3 vPos;
      varying float vWave;
      void main() {
        vec3 n = normalize(vNormalV);
        // 中心近乎通透、边缘强菲涅尔：玻璃质感的「知核」，而不是实心泥球
        float fres = pow(1.0 - abs(dot(n, vec3(0.0, 0.0, 1.0))), 2.5);

        // 球面经纬细纹：随球自转的「知识网格」，比外部线框更贴体
        vec3 sp = normalize(vPos);
        float lat = asin(clamp(sp.y, -1.0, 1.0));
        float lon = atan(sp.z, sp.x);
        float gridA = smoothstep(0.052, 0.0, abs(sin(lat * 9.0)));
        float gridB = smoothstep(0.042, 0.0, abs(sin(lon * 9.0)));
        // 网纹在正面收敛、边缘增强：玻璃球而不是密密麻麻的线框球
        float grid = max(gridA, gridB) * 0.1 * (0.3 + 0.7 * fres);

        // 斜向流动能量带 + 上下循环的扫描光带（动态感的主要来源之一）
        float flow = sin(vPos.y * 3.1 + vPos.x * 1.6 - uTime * 0.62);
        float band = smoothstep(0.84, 1.0, flow * 0.5 + 0.5) * 0.2;
        float scanY = sin(uTime * 0.55) * 1.16;
        float scan = exp(-pow((vPos.y - scanY) * 3.1, 2.0)) * 0.46;

        // 随鼠标移动的方向性高光：球面像玻璃一样「跟着人反光」
        vec3 lightDir = normalize(vec3(uPointer.x * 0.8, -uPointer.y * 0.8, 1.0));
        float glint = pow(max(0.0, dot(n, lightDir)), 7.0) * 0.52;

        vec3 col = mix(uJade * 0.07, uJade * 1.35, fres);
        col += uWhite * (pow(fres, 3.4) * 0.5 + glint + scan * 0.5);
        col += uJade * (grid + band + (vWave * 0.5 + 0.5) * 0.05 + scan * 0.32);
        col = mix(col, uGold, uEnergy * 0.16 + uBurst * 0.32);
        col += uWhite * uBurst * 0.2;
        float alpha = 0.085 + fres * 0.7 + grid + scan * 0.16 + uEnergy * 0.07;
        gl_FragColor = vec4(col, clamp(alpha, 0.0, 0.94));
      }
    `,
  }))
  const coreGeo = keep(new THREE.IcosahedronGeometry(1.34, 4))
  const core = new THREE.Mesh(coreGeo, coreMat)
  root.add(core)

  // 天球经纬网：比多面体边线更贴合「学域星图」语义，也更精致
  const gridPts: number[] = []
  const GRID_R = 1.62
  for (let latDeg = -60; latDeg <= 60; latDeg += 30) {
    const lat = latDeg * Math.PI / 180
    const r = GRID_R * Math.cos(lat)
    const y = GRID_R * Math.sin(lat)
    for (let i = 0; i < 64; i++) {
      const a = (i / 64) * Math.PI * 2
      const b = ((i + 1) / 64) * Math.PI * 2
      gridPts.push(Math.cos(a) * r, y, Math.sin(a) * r, Math.cos(b) * r, y, Math.sin(b) * r)
    }
  }
  for (let lonDeg = 0; lonDeg < 360; lonDeg += 30) {
    const lon = lonDeg * Math.PI / 180
    for (let i = 0; i < 32; i++) {
      const p1 = -Math.PI / 2 + (i / 32) * Math.PI
      const p2 = -Math.PI / 2 + ((i + 1) / 32) * Math.PI
      gridPts.push(
        GRID_R * Math.cos(p1) * Math.cos(lon), GRID_R * Math.sin(p1), GRID_R * Math.cos(p1) * Math.sin(lon),
        GRID_R * Math.cos(p2) * Math.cos(lon), GRID_R * Math.sin(p2), GRID_R * Math.cos(p2) * Math.sin(lon),
      )
    }
  }
  const wireGeo = keep(new THREE.BufferGeometry())
  wireGeo.setAttribute('position', new THREE.Float32BufferAttribute(gridPts, 3))
  const wire = new THREE.LineSegments(
    wireGeo,
    keep(new THREE.LineBasicMaterial({ color: JADE, transparent: true, opacity: 0.1, depthWrite: false })),
  )
  root.add(wire)

  const nucleus = new THREE.Mesh(
    keep(new THREE.SphereGeometry(0.085, 20, 14)),
    keep(new THREE.MeshBasicMaterial({ color: PAPER, transparent: true, opacity: 0.95, blending: THREE.AdditiveBlending, depthWrite: false })),
  )
  root.add(nucleus)
  // 光晕必须极淡：球心一旦出现可见的圆斑，玻璃球立刻退化成「实心泥球」
  const halo = new THREE.Mesh(
    keep(new THREE.SphereGeometry(0.44, 24, 16)),
    keep(new THREE.MeshBasicMaterial({ color: PAPER, transparent: true, opacity: 0.035, blending: THREE.AdditiveBlending, depthWrite: false, depthTest: false })),
  )
  root.add(halo)

  // ---------- 大气层：BackSide 菲涅尔壳，给球体一圈柔和体积光 ----------
  const atmoMat = keep(new THREE.ShaderMaterial({
    uniforms: { uTime, uEnergy, uBurst, uJade, uWhite },
    transparent: true,
    depthWrite: false,
    side: THREE.BackSide,
    blending: THREE.AdditiveBlending,
    vertexShader: /* glsl */`
      varying vec3 vN;
      void main() {
        vN = normalize(normalMatrix * normal);
        gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
      }
    `,
    fragmentShader: /* glsl */`
      uniform float uTime;
      uniform float uEnergy;
      uniform float uBurst;
      uniform vec3 uJade;
      uniform vec3 uWhite;
      varying vec3 vN;
      void main() {
        float rim = pow(1.0 - abs(dot(normalize(vN), vec3(0.0, 0.0, 1.0))), 2.1);
        float breathe = 0.86 + 0.14 * sin(uTime * 0.85);
        float a = rim * (0.25 + uEnergy * 0.16 + uBurst * 0.42) * breathe;
        gl_FragColor = vec4(mix(uJade, uWhite, rim * 0.55), a);
      }
    `,
  }))
  const atmo = new THREE.Mesh(keep(new THREE.SphereGeometry(1.92, 48, 32)), atmoMat)
  root.add(atmo)

  // ---------- 内核知识粒子云：让球是「有内容」的，而不是空壳 ----------
  // 独立随机源，避免消耗星座用的种子而打乱已验证过的构图
  const innerRand = mulberry32(771103)
  const INNER_N = 190
  const innerPos = new Float32Array(INNER_N * 3)
  const innerPhase = new Float32Array(INNER_N)
  const innerSize = new Float32Array(INNER_N)
  for (let i = 0; i < INNER_N; i++) {
    const u = innerRand() * 2 - 1
    const th = innerRand() * Math.PI * 2
    const r = 0.2 + Math.cbrt(innerRand()) * 0.96
    const s = Math.sqrt(Math.max(0, 1 - u * u))
    innerPos[i * 3] = Math.cos(th) * s * r
    innerPos[i * 3 + 1] = u * r
    innerPos[i * 3 + 2] = Math.sin(th) * s * r
    innerPhase[i] = innerRand()
    innerSize[i] = 0.5 + innerRand() * 1.1
  }
  const innerGeo = keep(new THREE.BufferGeometry())
  innerGeo.setAttribute('position', new THREE.BufferAttribute(innerPos, 3))
  innerGeo.setAttribute('aPhase', new THREE.BufferAttribute(innerPhase, 1))
  innerGeo.setAttribute('aSize', new THREE.BufferAttribute(innerSize, 1))
  const innerMat = keep(new THREE.ShaderMaterial({
    uniforms: { uTime, uEnergy, uJade, uWhite, uPixelRatio: { value: Math.min(window.devicePixelRatio || 1, 1.75) } },
    transparent: true, depthWrite: false, blending: THREE.AdditiveBlending,
    vertexShader: /* glsl */`
      attribute float aPhase;
      attribute float aSize;
      uniform float uTime;
      uniform float uEnergy;
      uniform float uPixelRatio;
      varying float vA;
      void main() {
        float pulse = 0.55 + 0.45 * sin(uTime * 1.3 + aPhase * 6.2831853);
        vA = pulse * (0.42 + uEnergy * 0.5);
        // 差速旋转：不同纬度的粒子转速不同，像缓慢流动的星云而不是刚体自转
        float ang = uTime * 0.11 + position.y * 0.55;
        float ca = cos(ang), sa = sin(ang);
        vec3 p = vec3(position.x * ca - position.z * sa, position.y, position.x * sa + position.z * ca);
        vec4 mv = modelViewMatrix * vec4(p, 1.0);
        gl_PointSize = aSize * uPixelRatio * (26.0 / max(-mv.z, 0.1));
        gl_Position = projectionMatrix * mv;
      }
    `,
    fragmentShader: /* glsl */`
      uniform vec3 uJade;
      uniform vec3 uWhite;
      varying float vA;
      void main() {
        float d = length(gl_PointCoord - 0.5);
        float c = smoothstep(0.5, 0.05, d);
        gl_FragColor = vec4(mix(uJade, uWhite, 0.45), c * vA * 0.62);
      }
    `,
  }))
  const innerCloud = new THREE.Points(innerGeo, innerMat)
  root.add(innerCloud)

  // ---------- 课程星座：一组球面簇 + 连线，全部一次 draw call ----------
  const constellations = new THREE.Group()
  root.add(constellations)

  const nodePos: number[] = []
  const nodeSize: number[] = []
  const nodePhase: number[] = []
  const nodeTint: number[] = []
  const linePts: number[] = []
  const lineT: number[] = []
  const brightPts: number[] = []
  const brightT: number[] = []
  const paths: ThreeNS.Vector3[][] = []

  // 球面上取一个方向，在其切平面内散布一簇「课程星」
  function clusterAt(theta: number, phi: number, count: number, spread: number, tintBase: number) {
    const dir = new THREE.Vector3(
      Math.sin(phi) * Math.cos(theta),
      Math.cos(phi),
      Math.sin(phi) * Math.sin(theta),
    )
    const up = Math.abs(dir.y) > 0.9 ? new THREE.Vector3(1, 0, 0) : new THREE.Vector3(0, 1, 0)
    const u = new THREE.Vector3().crossVectors(up, dir).normalize()
    const v = new THREE.Vector3().crossVectors(dir, u).normalize()
    const chain: ThreeNS.Vector3[] = []
    for (let i = 0; i < count; i++) {
      const a = (i / count) * Math.PI * 2 + rand() * 0.7
      const rr = spread * (0.4 + rand() * 0.75)
      const p = dir.clone().multiplyScalar(3.35 + rand() * 1.05)
        .addScaledVector(u, Math.cos(a) * rr)
        .addScaledVector(v, Math.sin(a) * rr * 0.82)
      chain.push(p)
      nodePos.push(p.x, p.y, p.z)
      nodeSize.push(0.7 + rand() * 1.5 + (i % 3 === 0 ? 0.9 : 0))
      nodePhase.push(rand())
      nodeTint.push(Math.min(1, Math.max(0, tintBase + (rand() - 0.5) * 0.5)))
    }
    // 收尾连回起点，形成「星座」而不是折线；同时累计弧长，供连线 shader 做数据流
    let acc = 0
    for (let i = 0; i < chain.length; i++) {
      const a = chain[i], b = chain[(i + 1) % chain.length]
      const len = a.distanceTo(b)
      const t0 = acc, t1 = acc + len
      acc += len
      linePts.push(a.x, a.y, a.z, b.x, b.y, b.z)
      lineT.push(t0, t1)
      if (i % 2 === 0) {
        brightPts.push(a.x, a.y, a.z, b.x, b.y, b.z)
        brightT.push(t0, t1)
      }
    }
    paths.push(chain)
  }

  const clusterSpec = [
    [0.35, 1.02, 6, 0.72, 0.15], [1.42, 0.72, 5, 0.62, 0.55], [2.45, 1.32, 7, 0.8, 0.9],
    [3.55, 0.85, 5, 0.6, 0.25], [4.55, 1.18, 6, 0.74, 0.7], [5.55, 0.62, 5, 0.58, 0.4],
    [0.9, 2.05, 6, 0.7, 0.82], [2.95, 2.1, 5, 0.66, 0.2], [4.9, 2.0, 6, 0.72, 0.6],
  ] as const
  clusterSpec.forEach(s => clusterAt(s[0], s[1], s[2], s[3], s[4]))

  const starMat = keep(new THREE.ShaderMaterial({
    uniforms: {
      uTime, uEnergy, uJade, uGold, uWhite,
      uPixelRatio: { value: Math.min(window.devicePixelRatio || 1, 1.75) },
    },
    transparent: true,
    depthWrite: false,
    blending: THREE.AdditiveBlending,
    vertexShader: /* glsl */`
      attribute float aSize;
      attribute float aPhase;
      attribute float aTint;
      uniform float uTime;
      uniform float uEnergy;
      uniform float uPixelRatio;
      varying float vTint;
      varying float vAlpha;
      void main() {
        float pulse = 0.62 + 0.38 * sin(uTime * 1.65 + aPhase * 6.2831853);
        vAlpha = pulse * (0.52 + uEnergy * 0.48);
        vTint = aTint;
        vec4 mv = modelViewMatrix * vec4(position, 1.0);
        gl_PointSize = aSize * uPixelRatio * (30.0 / max(-mv.z, 0.1)) * (0.72 + pulse * 0.62);
        gl_Position = projectionMatrix * mv;
      }
    `,
    fragmentShader: /* glsl */`
      uniform vec3 uJade;
      uniform vec3 uWhite;
      uniform vec3 uGold;
      varying float vTint;
      varying float vAlpha;
      void main() {
        float d = length(gl_PointCoord - 0.5);
        float core = smoothstep(0.5, 0.015, d);
        float halo = smoothstep(0.5, 0.14, d) * 0.42;
        vec3 col = mix(uJade, uWhite, smoothstep(0.22, 0.78, vTint));
        col = mix(col, uGold, smoothstep(0.8, 1.0, vTint));
        gl_FragColor = vec4(col, (core + halo) * vAlpha);
      }
    `,
  }))
  const starGeo = keep(new THREE.BufferGeometry())
  starGeo.setAttribute('position', new THREE.Float32BufferAttribute(nodePos, 3))
  starGeo.setAttribute('aSize', new THREE.Float32BufferAttribute(nodeSize, 1))
  starGeo.setAttribute('aPhase', new THREE.Float32BufferAttribute(nodePhase, 1))
  starGeo.setAttribute('aTint', new THREE.Float32BufferAttribute(nodeTint, 1))
  const stars = new THREE.Points(starGeo, starMat)
  constellations.add(stars)

  // 连线不再静态：用累计弧长做 varying，让「选课数据流」沿每颗星座的链路传播
  function linesFrom(pts: number[], ts: number[], color: string, opacity: number, additive = false) {
    const g = keep(new THREE.BufferGeometry())
    g.setAttribute('position', new THREE.Float32BufferAttribute(pts, 3))
    g.setAttribute('aT', new THREE.Float32BufferAttribute(ts, 1))
    return new THREE.LineSegments(g, keep(new THREE.ShaderMaterial({
      uniforms: {
        uTime, uEnergy, uWhite,
        uColor: { value: new THREE.Color(color) },
        uOpacity: { value: opacity },
      },
      transparent: true,
      depthWrite: false,
      blending: additive ? THREE.AdditiveBlending : THREE.NormalBlending,
      vertexShader: /* glsl */`
        attribute float aT;
        varying float vT;
        void main() {
          vT = aT;
          gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
        }
      `,
      fragmentShader: /* glsl */`
        uniform float uTime;
        uniform float uEnergy;
        uniform float uOpacity;
        uniform vec3 uColor;
        uniform vec3 uWhite;
        varying float vT;
        void main() {
          float w = sin(vT * 2.3 - uTime * 2.5);
          float pulse = pow(max(0.0, w), 9.0);
          float a = uOpacity * (0.72 + pulse * 2.1) * (0.8 + uEnergy * 0.6);
          gl_FragColor = vec4(mix(uColor, uWhite, pulse * 0.8), clamp(a, 0.0, 1.0));
        }
      `,
    })))
  }
  constellations.add(linesFrom(linePts, lineT, '#79b9c4', 0.11))
  constellations.add(linesFrom(brightPts, brightT, JADE, 0.24, true))

  // ---------- 选课轨道：三层环 + 流动光点 + 能量弧 ----------
  const orbitGroup = new THREE.Group()
  root.add(orbitGroup)
  const orbits: Array<{ ring: ThreeNS.Object3D; arc: ThreeNS.Object3D; sparks: Array<{ m: ThreeNS.Mesh; r: number; a: number; speed: number }> }> = []
  const sparkGeo = keep(new THREE.SphereGeometry(0.055, 10, 8))
  const sparkMat = keep(new THREE.MeshBasicMaterial({ color: JADE, transparent: true, opacity: 0.95, blending: THREE.AdditiveBlending, depthWrite: false }))
  const sparkGoldMat = keep(new THREE.MeshBasicMaterial({ color: GOLD, transparent: true, opacity: 0.9, blending: THREE.AdditiveBlending, depthWrite: false }))
  // x 倾角远离 90°：侧对相机的圆环会被投影成一条横贯画面的「孤立长直线」
  const ringSpec = [
    { r: 2.18, tilt: [0.86, 0.22, 0.34] as const, color: JADE, op: 0.34 },
    { r: 2.62, tilt: [0.46, 0.88, -0.46] as const, color: '#a9d8dc', op: 0.2 },
    { r: 3.02, tilt: [0.98, -0.36, 0.72] as const, color: GOLD, op: 0.22 },
  ]
  ringSpec.forEach((s, i) => {
    const g = keep(new THREE.TorusGeometry(s.r, 0.0075, 5, 180))
    const ring = new THREE.Mesh(g, keep(new THREE.MeshBasicMaterial({
      color: s.color, transparent: true, opacity: s.op, blending: THREE.AdditiveBlending, depthWrite: false,
    })))
    ring.rotation.set(s.tilt[0], s.tilt[1], s.tilt[2])
    orbitGroup.add(ring)

    // 能量弧与环同粗细、只做局部提亮：比环粗会被看成横在画面里的孤立脏线
    const ag = keep(new THREE.TorusGeometry(s.r, 0.0075, 5, 64, 1.5))
    const arc = new THREE.Mesh(ag, keep(new THREE.MeshBasicMaterial({
      color: s.color, transparent: true, opacity: 0.5, blending: THREE.AdditiveBlending, depthWrite: false,
    })))
    ring.add(arc)
    const sparks: Array<{ m: ThreeNS.Mesh; r: number; a: number; speed: number }> = []
    const n = i === 1 ? 3 : 2
    for (let k = 0; k < n; k++) {
      const m = new THREE.Mesh(sparkGeo, i === 2 && k === 0 ? sparkGoldMat : sparkMat)
      ring.add(m)
      sparks.push({ m, r: s.r, a: (k / n) * Math.PI * 2 + i, speed: 0.32 + i * 0.12 + k * 0.08 })
    }
    orbits.push({ ring, arc, sparks })
  })

  // ---------- 轨道火花拖尾：一个 Points + 动态 attribute，静止截图也能看见「在跑」 ----------
  const TRAIL_N = 9
  const trailCount = orbits.reduce((n, o) => n + o.sparks.length, 0) * TRAIL_N
  const trailPos = new Float32Array(trailCount * 3)
  const trailAlpha = new Float32Array(trailCount)
  const trailSize = new Float32Array(trailCount)
  const trailGeo = keep(new THREE.BufferGeometry())
  const trailPosAttr = new THREE.BufferAttribute(trailPos, 3)
  trailPosAttr.setUsage(THREE.DynamicDrawUsage)
  trailGeo.setAttribute('position', trailPosAttr)
  const trailAlphaAttr = new THREE.BufferAttribute(trailAlpha, 1)
  trailAlphaAttr.setUsage(THREE.DynamicDrawUsage)
  trailGeo.setAttribute('aAlpha', trailAlphaAttr)
  const trailSizeAttr = new THREE.BufferAttribute(trailSize, 1)
  trailSizeAttr.setUsage(THREE.DynamicDrawUsage)
  trailGeo.setAttribute('aSize', trailSizeAttr)
  const trailMat = keep(new THREE.ShaderMaterial({
    uniforms: { uJade, uWhite, uPixelRatio: { value: Math.min(window.devicePixelRatio || 1, 1.75) } },
    transparent: true, depthWrite: false, blending: THREE.AdditiveBlending,
    vertexShader: /* glsl */`
      attribute float aAlpha;
      attribute float aSize;
      uniform float uPixelRatio;
      varying float vA;
      void main() {
        vA = aAlpha;
        vec4 mv = modelViewMatrix * vec4(position, 1.0);
        gl_PointSize = aSize * uPixelRatio * (30.0 / max(-mv.z, 0.1));
        gl_Position = projectionMatrix * mv;
      }
    `,
    fragmentShader: /* glsl */`
      uniform vec3 uJade;
      uniform vec3 uWhite;
      varying float vA;
      void main() {
        float d = length(gl_PointCoord - 0.5);
        float c = smoothstep(0.5, 0.04, d);
        gl_FragColor = vec4(mix(uJade, uWhite, 0.35), c * vA * 0.85);
      }
    `,
  }))
  const trails = new THREE.Points(trailGeo, trailMat)
  trails.frustumCulled = false // 位置每帧变，包围盒不可靠
  scene.add(trails)

  const trailPoint = new THREE.Vector3()
  function syncTrails() {
    let ti = 0
    orbits.forEach(o => {
      o.sparks.forEach(s => {
        const parent = s.m.parent
        if (!parent) return
        for (let k = 0; k < TRAIL_N; k++) {
          const a = s.a - k * 0.062
          trailPoint.set(Math.cos(a) * s.r, Math.sin(a) * s.r, 0)
          parent.localToWorld(trailPoint)
          trailPos[ti * 3] = trailPoint.x
          trailPos[ti * 3 + 1] = trailPoint.y
          trailPos[ti * 3 + 2] = trailPoint.z
          const f = 1 - k / TRAIL_N
          trailAlpha[ti] = Math.pow(f, 1.6) * (0.68 + uEnergy.value * 0.4)
          trailSize[ti] = f * 1.5 + 0.3
          ti++
        }
      })
    })
    trailPosAttr.needsUpdate = true
    trailAlphaAttr.needsUpdate = true
    trailSizeAttr.needsUpdate = true
  }
  syncTrails()

  // ---------- 选课路径上的「正在被选择」的光点 ----------
  const travelers: Array<{ m: ThreeNS.Mesh; path: ThreeNS.Vector3[]; t: number; speed: number }> = []
  ;[paths[0], paths[2], paths[6]].forEach((p, i) => {
    if (!p) return
    const m = new THREE.Mesh(keep(new THREE.SphereGeometry(0.075, 12, 10)), i === 1 ? sparkGoldMat : sparkMat)
    constellations.add(m)
    travelers.push({ m, path: p, t: i * 0.33, speed: 0.11 + i * 0.035 })
  })

  // 说明：不再放置超大远景圆环。半径 >6 的环投影后会在画面里形成横贯/竖贯的
  // 「孤立长直线」，压在标题与登录卡上很脏；纵深改由两层星尘 + 雾承担。

  // ---------- 星尘：两层，慢速漂移 + 视差 ----------
  function dustLayer(n: number, spread: number, size: number, opacity: number, color: string) {
    const pos = new Float32Array(n * 3)
    for (let i = 0; i < n; i++) {
      pos[i * 3] = (rand() - 0.5) * spread * 2
      pos[i * 3 + 1] = (rand() - 0.5) * spread * 0.9
      pos[i * 3 + 2] = (rand() - 0.5) * spread - 3
    }
    const g = keep(new THREE.BufferGeometry())
    g.setAttribute('position', new THREE.BufferAttribute(pos, 3))
    return new THREE.Points(g, keep(new THREE.PointsMaterial({
      color, size, transparent: true, opacity, sizeAttenuation: true, depthWrite: false,
    })))
  }
  const dustFar = dustLayer(420, 17, 0.022, 0.3, '#9fc6cf')
  const dustNear = dustLayer(140, 12, 0.038, 0.24, PAPER)
  scene.add(dustFar, dustNear)

  // ---------- 流星：带尾迹的 shader 光带，周期性划过 ----------
  const meteorMat = keep(new THREE.ShaderMaterial({
    uniforms: { uAlpha: { value: 0 }, uJade, uWhite },
    transparent: true, depthWrite: false, blending: THREE.AdditiveBlending, side: THREE.DoubleSide,
    vertexShader: /* glsl */`
      varying vec2 vUv;
      void main() { vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }
    `,
    fragmentShader: /* glsl */`
      uniform float uAlpha;
      uniform vec3 uJade;
      uniform vec3 uWhite;
      varying vec2 vUv;
      void main() {
        float head = smoothstep(0.0, 0.06, vUv.x);
        float tail = pow(1.0 - vUv.x, 2.4);
        float thin = smoothstep(0.5, 0.0, abs(vUv.y - 0.5) * 2.0);
        vec3 col = mix(uJade, uWhite, head);
        gl_FragColor = vec4(col, head * tail * thin * uAlpha);
      }
    `,
  }))
  const meteor = new THREE.Mesh(keep(new THREE.PlaneGeometry(3.4, 0.075)), meteorMat)
  // 轨迹限制在两栏之间的上半部：不要斜穿标题与数据读数
  meteor.rotation.z = -0.5
  meteor.position.set(4.5, 6, -1)
  meteor.visible = false
  scene.add(meteor)
  const meteorAt = 3.2

  // ---------- 点击涟漪：预分配 4 个，复用不产生 GC 抖动 ----------
  const ripples: Array<{ m: ThreeNS.Mesh; mat: ThreeNS.MeshBasicMaterial; age: number }> = []
  for (let i = 0; i < 4; i++) {
    const mat = keep(new THREE.MeshBasicMaterial({ color: JADE, transparent: true, opacity: 0, blending: THREE.AdditiveBlending, depthWrite: false, side: THREE.DoubleSide }))
    const m = new THREE.Mesh(keep(new THREE.RingGeometry(0.86, 0.9, 64)), mat)
    m.visible = false
    scene.add(m)
    ripples.push({ m, mat, age: -1 })
  }
  let rippleCursor = 0
  const raycaster = new THREE.Raycaster()
  const plane = new THREE.Plane(new THREE.Vector3(0, 0, 1), 0)
  const hit = new THREE.Vector3()
  const ndc = new THREE.Vector2()
  function spawnRipple(e: PointerEvent) {
    const r = ripples[rippleCursor]
    rippleCursor = (rippleCursor + 1) % ripples.length
    ndc.set((e.clientX / window.innerWidth) * 2 - 1, -(e.clientY / window.innerHeight) * 2 + 1)
    raycaster.setFromCamera(ndc, camera)
    if (!raycaster.ray.intersectPlane(plane, hit)) return
    r.m.position.copy(hit)
    r.m.quaternion.copy(camera.quaternion)
    r.m.visible = true
    r.age = 0
  }
  rippleCleanup = () => window.removeEventListener('pointerdown', spawnRipple)

  // ---------- 尺寸 / 视差 ----------
  const resize = () => {
    if (!renderer || !host) return
    const w = host.clientWidth || window.innerWidth
    const h = host.clientHeight || window.innerHeight
    gl.setSize(w, h, false)
    composer?.setSize(w, h)
    camera.aspect = w / h
    camera.updateProjectionMatrix()
    const narrow = w < 1080
    // 窄屏：星图上移到标题背后当光晕，登录卡区域保持干净
    root.position.set(narrow ? 0 : -1.7, narrow ? 3.3 : -0.28, narrow ? -2.4 : -0.6)
    root.scale.setScalar(narrow ? 0.56 : 1)
  }
  ro = new ResizeObserver(resize)
  ro.observe(host)
  resize()

  const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false

  // ---------- Bloom：桌面 + 常规 DPI 才开 ----------
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
      const bp = new UnrealBloomPass(new THREE.Vector2(host.clientWidth || 1600, host.clientHeight || 900), 0.52, 0.5, 0.74)
      comp.addPass(bp)
      comp.addPass(new OutputPass())
      composer = comp
      bloom = bp
      // composer 输出不带 alpha：底色与页面保持一致，再由 CSS 叠一层渐变
      gl.setClearColor(NIGHT, 1)
      disposables.push({ dispose: () => comp.dispose() })
      resize()
    } catch {
      composer = null
      bloom = null
    }
  }

  const draw = () => { if (composer) composer.render(); else gl.render(scene, camera) }

  onSuccessHook = () => { energy = 1.3; uEnergy.value = 0.9; if (reduced) draw() }

  // ---------- 帧循环 ----------
  // r186 起 Clock 已废弃：用核心 Timer，不 connect(document) 以免隐藏标签页冻结动画
  const timer = new THREE.Timer()
  const tick = () => {
    timer.update()
    const dt = Math.min(timer.getDelta(), 0.05)
    const t = timer.getElapsed()
    if (pendingBurst) { pendingBurst = false; burst = 1; burstAt = t; energy = 1.3 }
    energy = Math.max(0, energy - dt * 0.9)
    burst = Math.max(0, burst - dt * 0.42)
    uTime.value = t
    uEnergy.value = Math.min(1.4, energy + burst * 0.8)
    uBurst.value = burst

    core.rotation.y += dt * 0.14
    core.rotation.x += dt * 0.05
    wire.rotation.y -= dt * 0.09
    wire.rotation.z += dt * 0.035
    innerCloud.rotation.x = Math.sin(t * 0.25) * 0.12
    atmo.scale.setScalar(1 + Math.sin(t * 0.7) * 0.012 + burst * 0.08)
    nucleus.scale.setScalar(1 + Math.sin(t * 2.1) * 0.09 + uEnergy.value * 0.35)
    halo.scale.setScalar(1 + Math.sin(t * 1.5) * 0.13 + uEnergy.value * 0.5)
    key.intensity = 13 + uEnergy.value * 10
    warm.intensity = 8 + uEnergy.value * 6
    if (bloom) bloom.strength = 0.52 + uEnergy.value * 0.3

    constellations.rotation.y += dt * 0.035
    constellations.rotation.x = Math.sin(t * 0.18) * 0.06
    orbitGroup.rotation.y -= dt * 0.03

    orbits.forEach((o, i) => {
      o.ring.rotation.z += dt * (i % 2 ? -0.07 : 0.09)
      o.arc.rotation.z += dt * (i % 2 ? -0.5 : 0.62)
      o.sparks.forEach(s => {
        s.a += dt * s.speed
        s.m.position.set(Math.cos(s.a) * s.r, Math.sin(s.a) * s.r, 0)
        s.m.scale.setScalar(0.8 + Math.sin(t * 3.2 + s.a) * 0.3 + uEnergy.value * 0.7)
      })
    })
    syncTrails()

    travelers.forEach((tr, i) => {
      tr.t = (tr.t + dt * tr.speed) % 1
      const f = tr.t * tr.path.length
      const a = tr.path[Math.floor(f) % tr.path.length]
      const b = tr.path[(Math.floor(f) + 1) % tr.path.length]
      tr.m.position.lerpVectors(a, b, f % 1)
      tr.m.scale.setScalar(0.85 + Math.sin(t * 4 + i) * 0.22 + uEnergy.value * 0.6)
    })

    dustFar.rotation.y += dt * 0.006
    dustNear.rotation.y -= dt * 0.011
    dustNear.position.y = Math.sin(t * 0.22) * 0.35

    // 流星：每约 9s 划一次，尾迹渐隐
    const mAge = (t - meteorAt) % 9.2
    if (mAge >= 0 && mAge < 1.35) {
      const p = mAge / 1.35
      meteor.position.set(4.5 - p * 7, 6 - p * 3.6, -1)
      meteor.visible = true
      meteorMat.uniforms.uAlpha.value = Math.sin(p * Math.PI) * 0.85
    } else {
      meteor.visible = false
    }

    ripples.forEach(r => {
      if (r.age < 0) return
      r.age += dt
      const p = r.age / 1.15
      if (p >= 1) { r.m.visible = false; r.age = -1; r.mat.opacity = 0; return }
      r.m.scale.setScalar(0.3 + p * 3.2)
      r.mat.opacity = (1 - p) * 0.5
    })

    // 登录成功：星图短暂收束 + 相机推近，形成一次编排好的收尾
    const sinceBurst = t - burstAt
    if (burst > 0 && sinceBurst >= 0 && sinceBurst < 2.2) {
      const k = Math.sin(Math.min(1, sinceBurst / 2.2) * Math.PI)
      root.scale.setScalar((window.innerWidth < 1080 ? 0.56 : 1) * (1 - k * 0.09))
      camera.position.z = 14 - k * 1.1
    } else {
      camera.position.z += (14 - camera.position.z) * 0.05
    }

    pointer.x += (pointer.tx - pointer.x) * 0.035
    pointer.y += (pointer.ty - pointer.y) * 0.035
    uPointer.value.set(pointer.x, pointer.y)
    camera.position.x += (pointer.x * 0.62 - camera.position.x) * 0.05
    camera.position.y += (0.1 - pointer.y * 0.42 - camera.position.y) * 0.05
    camera.lookAt(0, 0, 0)
  }

  if (reduced) {
    draw()
  } else {
    window.addEventListener('pointermove', onPointerMove, { passive: true })
    window.addEventListener('focusin', onFocusIn, { passive: true })
    window.addEventListener('pointerdown', onPointerDown, { passive: true })
    window.addEventListener('pointerdown', spawnRipple, { passive: true })
    const loop = () => {
      if (disposed) return
      tick()
      draw()
      raf = requestAnimationFrame(loop)
    }
    raf = requestAnimationFrame(loop)
  }
  ready.value = true
})

onBeforeUnmount(() => {
  disposed = true
  if (raf) cancelAnimationFrame(raf)
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('focusin', onFocusIn)
  window.removeEventListener('pointerdown', onPointerDown)
  rippleCleanup?.()
  rippleCleanup = null
  ro?.disconnect()
  disposables.forEach(d => { try { d.dispose() } catch { /* 已释放 */ } })
  disposables.length = 0
  composer = null
  bloom = null
  renderer?.dispose()
  renderer = null
  onSuccessHook = null
})
</script>

<style scoped>
.atlas-scene { position: absolute; inset: 0; z-index: 0; pointer-events: none; overflow: hidden; opacity: 0; transition: opacity 1.1s ease; }
.atlas-scene.is-ready { opacity: 1; }
.atlas-cv { display: block; width: 100%; height: 100%; }
.atlas-scene.is-fallback {
  opacity: 1;
  background:
    radial-gradient(circle at 30% 46%, rgba(116, 228, 196, .13), transparent 44%),
    radial-gradient(circle at 34% 50%, rgba(231, 197, 140, .07), transparent 58%);
}
@media (prefers-reduced-motion: reduce) {
  .atlas-scene { transition: none; }
}
</style>
