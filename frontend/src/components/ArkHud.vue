<template>
  <div ref="host" class="ark-hud" :class="{ 'is-ready': ready, 'is-fallback': failed }" aria-hidden="true">
    <div v-if="failed" class="ark-hud-fallback"><i /><i /><i /></div>
    <div class="ark-hud-ring" />
    <div class="ark-hud-cross"><span /><span /></div>
    <div class="ark-hud-tag">ATLAS / <b>01</b></div>
  </div>
</template>

<script setup lang="ts">
/**
 * 三端主页共用的轻量 three.js 星图核心（HUD）
 * - 纯程序化几何，不加载外部模型，three 动态 import（与登录页共用同一 chunk 缓存）
 * - 无 WebGL / 加载失败 → CSS 圆环降级；reduced-motion → 只绘一帧
 * - 组件卸载时完整释放 GPU 资源、定时器与监听
 */
import { ref, onMounted, onBeforeUnmount } from 'vue'

const host = ref<HTMLElement | null>(null)
const ready = ref(false)
const failed = ref(false)

let THREE: typeof import('three') | null = null
let renderer: import('three').WebGLRenderer | null = null
let scene: import('three').Scene | null = null
let camera: import('three').PerspectiveCamera | null = null
let raf = 0
let ro: ResizeObserver | null = null
let alive = true
const disposables: Array<{ dispose: () => void }> = []
const keep = <T extends { dispose: () => void }>(o: T): T => { disposables.push(o); return o }

function cleanup() {
  alive = false
  if (raf) cancelAnimationFrame(raf)
  raf = 0
  ro?.disconnect(); ro = null
  host.value?.removeEventListener('pointermove', onPointer)
  disposables.forEach(d => { try { d.dispose() } catch {} })
  disposables.length = 0
  try { renderer?.dispose(); renderer?.forceContextLoss?.() } catch {}
  try { renderer?.domElement.remove() } catch {}
  renderer = null; scene = null; camera = null
}

let pointer = { x: 0, y: 0 }
function onPointer(e: PointerEvent) {
  const r = host.value?.getBoundingClientRect()
  if (!r) return
  pointer.x = ((e.clientX - r.left) / r.width - .5) * 2
  pointer.y = ((e.clientY - r.top) / r.height - .5) * 2
}

onMounted(async () => {
  const el = host.value
  if (!el) return
  el.addEventListener('pointermove', onPointer, { passive: true })
  try {
    THREE = await import('three')
    if (!alive || !el.isConnected) return
    build(el)
  } catch {
    failed.value = true
  }
})

function build(el: HTMLElement) {
  if (!THREE) return
  const T = THREE
  const w = Math.max(120, el.clientWidth || 190)
  const h = Math.max(120, el.clientHeight || 190)

  scene = new T.Scene()
  camera = new T.PerspectiveCamera(38, w / h, .1, 60)
  camera.position.set(0, .18, 5.6)

  renderer = new T.WebGLRenderer({ alpha: true, antialias: true, powerPreference: 'low-power' })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.75))
  renderer.setSize(w, h, false)
  renderer.setClearColor(0x000000, 0)
  renderer.domElement.className = 'ark-hud-canvas'
  el.prepend(renderer.domElement)

  const root = new T.Group()
  root.rotation.set(.22, .4, .1)
  scene.add(root)

  const Y = 0x74e4c4
  const mat = {
    wire: keep(new T.LineBasicMaterial({ color: Y, transparent: true, opacity: .7, depthWrite: false })),
    wireDim: keep(new T.LineBasicMaterial({ color: 0x8fa6b4, transparent: true, opacity: .26, depthWrite: false })),
    shell: keep(new T.MeshStandardMaterial({
      color: 0x121b24, metalness: .9, roughness: .34, flatShading: true,
      emissive: 0x0a1118, emissiveIntensity: .6,
    })),
    core: keep(new T.MeshBasicMaterial({ color: Y, transparent: true, opacity: .92, blending: T.AdditiveBlending, depthWrite: false })),
    halo: keep(new T.MeshBasicMaterial({ color: Y, transparent: true, opacity: .1, blending: T.AdditiveBlending, depthWrite: false })),
    arc: keep(new T.MeshBasicMaterial({ color: 0xffe04d, transparent: true, opacity: .95, blending: T.AdditiveBlending, depthWrite: false, side: T.DoubleSide })),
    dot: keep(new T.PointsMaterial({ color: 0x9fc4d6, size: .022, transparent: true, opacity: .62, depthWrite: false })),
  }

  // 1) 外壳线框 + 暗金属实心核
  const outerGeo = keep(new T.IcosahedronGeometry(1.28, 1))
  root.add(new T.LineSegments(keep(new T.EdgesGeometry(outerGeo, 18)), mat.wire))
  const shellGeo = keep(new T.IcosahedronGeometry(.96, 1))
  const shell = new T.Mesh(shellGeo, mat.shell)
  root.add(shell)
  root.add(new T.LineSegments(keep(new T.EdgesGeometry(shellGeo, 26)), mat.wireDim))

  // 2) 发光核心 + 外层辉光球
  const core = new T.Mesh(keep(new T.SphereGeometry(.24, 20, 14)), mat.core)
  root.add(core)
  const halo = new T.Mesh(keep(new T.SphereGeometry(.42, 18, 12)), mat.halo)
  root.add(halo)

  // 3) 三条异倾角能量环 + 环上流动弧光
  const rings: import('three').Group[] = []
  const arcs: import('three').Mesh[] = []
  for (let i = 0; i < 3; i++) {
    const g = new T.Group()
    g.rotation.set(.55 + i * .72, i * .8, i * .42)
    const rg = keep(new T.TorusGeometry(1.52 + i * .14, .006, 3, 96))
    g.add(new T.Mesh(rg, i === 1 ? mat.wireDim : mat.wire))
    const arcGeo = keep(new T.TorusGeometry(1.52 + i * .14, .022, 3, 22, .72))
    const arc = new T.Mesh(arcGeo, mat.arc)
    g.add(arc)
    arcs.push(arc)
    root.add(g)
    rings.push(g)
  }

  // 4) 径向刻度（48 根短线，构成仪表感）
  const tickPos: number[] = []
  for (let i = 0; i < 48; i++) {
    const a = (i / 48) * Math.PI * 2
    const r1 = i % 4 === 0 ? 1.82 : 1.88
    const r2 = 1.96
    tickPos.push(Math.cos(a) * r1, Math.sin(a) * r1, 0, Math.cos(a) * r2, Math.sin(a) * r2, 0)
  }
  const tickGeo = keep(new T.BufferGeometry())
  tickGeo.setAttribute('position', new T.Float32BufferAttribute(tickPos, 3))
  const ticks = new T.LineSegments(tickGeo, keep(new T.LineBasicMaterial({ color: Y, transparent: true, opacity: .3, depthWrite: false })))
  ticks.rotation.set(1.15, .25, .35)
  root.add(ticks)

  // 5) 悬浮数据点
  const p: number[] = []
  for (let i = 0; i < 70; i++) {
    const a = Math.random() * Math.PI * 2
    const r = 1.5 + Math.random() * 1.3
    p.push(Math.cos(a) * r, (Math.random() - .5) * 3, Math.sin(a) * r)
  }
  const dustGeo = keep(new T.BufferGeometry())
  dustGeo.setAttribute('position', new T.Float32BufferAttribute(p, 3))
  const dust = new T.Points(dustGeo, mat.dot)
  root.add(dust)

  // 6) 灯光
  scene.add(new T.AmbientLight(0x9fb6c4, 1.15))
  const key = new T.PointLight(Y, 14, 12)
  key.position.set(1.6, 1.8, 2.4)
  scene.add(key)
  const rim = new T.DirectionalLight(0x8fd0f5, 1.6)
  rim.position.set(-2, -1, -1.5)
  scene.add(rim)

  // 7) 渲染循环
  const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false
  const clock = new T.Clock()
  let camX = 0, camY = 0
  const frame = () => {
    const r = renderer, s = scene, c = camera
    if (!r || !s || !c) return
    const t = clock.getElapsedTime()
    if (!reduced) {
      root.rotation.y = .4 + t * .16
      root.rotation.x = .22 + Math.sin(t * .38) * .09
      shell.rotation.y = -t * .1
      rings.forEach((g, i) => { g.rotation.z = i * .42 + t * (i % 2 ? -.24 : .2) })
      arcs.forEach((a, i) => { a.rotation.z = t * (i % 2 ? -.9 : .85) })
      ticks.rotation.z = .35 + t * .06
      dust.rotation.y = -t * .05
      const pulse = 1 + Math.sin(t * 2.1) * .07
      core.scale.setScalar(pulse)
      halo.scale.setScalar(1 + Math.sin(t * 1.4) * .12)
      mat.arc.opacity = .7 + Math.sin(t * 3) * .25
      // 指针视差：相机平滑跟随
      camX += (pointer.x * .42 - camX) * .05
      camY += (-pointer.y * .3 - camY) * .05
      c.position.x = camX
      c.position.y = .18 + camY
      c.lookAt(0, 0, 0)
    }
    r.render(s, c)
    if (reduced) return
    raf = requestAnimationFrame(frame)
  }
  frame()
  ready.value = true

  ro = new ResizeObserver(() => {
    const r = renderer, c = camera, s = scene
    if (!r || !c || !s || !el.isConnected) return
    const nw = Math.max(120, el.clientWidth || 190)
    const nh = Math.max(120, el.clientHeight || 190)
    c.aspect = nw / nh
    c.updateProjectionMatrix()
    r.setSize(nw, nh, false)
    if (reduced) r.render(s, c)
  })
  ro.observe(el)
}

onBeforeUnmount(cleanup)
</script>

<style scoped>
.ark-hud {
  position: relative; width: 100%; height: 100%; min-height: 168px;
  overflow: hidden; isolation: isolate;
}
/* canvas 由 JS 动态创建、不带 scoped 属性，必须用 :deep */
:deep(.ark-hud-canvas) {
  position: absolute; inset: 0; width: 100%; height: 100%;
  opacity: 0; transition: opacity .8s ease;
}
.ark-hud.is-ready :deep(.ark-hud-canvas) { opacity: 1; }

/* HUD 外圈刻度环（CSS，WebGL 失败时仍然成立） */
.ark-hud-ring {
  position: absolute; inset: 12%; border: 1px solid rgba(116, 228, 196, .16);
  border-radius: 50%; pointer-events: none;
  box-shadow: 0 0 0 8px rgba(116, 228, 196, .025), inset 0 0 28px rgba(116, 228, 196, .05);
}
.ark-hud-ring::before,
.ark-hud-ring::after {
  content: ''; position: absolute; border: 1px dashed rgba(116, 228, 196, .3); border-radius: 50%;
}
.ark-hud-ring::before { inset: -9px; animation: hudSpin 28s linear infinite; }
.ark-hud-ring::after { inset: 14px; border-style: solid; border-color: rgba(116, 228, 196, .14); }
@keyframes hudSpin { to { transform: rotate(360deg); } }

/* 十字准星 */
.ark-hud-cross { position: absolute; inset: 0; pointer-events: none; }
.ark-hud-cross span { position: absolute; background: rgba(116, 228, 196, .22); }
.ark-hud-cross span:first-child { left: 0; right: 0; top: 50%; height: 1px; }
.ark-hud-cross span:last-child { top: 0; bottom: 0; left: 50%; width: 1px; }

.ark-hud-tag {
  position: absolute; right: 4px; bottom: 2px;
  font-family: var(--ark-mono); font-size: 9px; letter-spacing: .14em;
  color: rgba(139, 152, 164, .8); pointer-events: none;
}
.ark-hud-tag b { color: var(--ark-y, #74e4c4); font-weight: 600; }

/* 无 WebGL 降级 */
.ark-hud-fallback { position: absolute; inset: 22%; }
.ark-hud-fallback i {
  position: absolute; inset: 0; border: 1px solid rgba(116, 228, 196, .55);
  clip-path: polygon(50% 0, 100% 50%, 50% 100%, 0 50%);
  animation: hudSpin 12s linear infinite;
}
.ark-hud-fallback i:nth-child(2) { inset: 14%; animation-duration: 9s; animation-direction: reverse; opacity: .6; }
.ark-hud-fallback i:nth-child(3) { inset: 28%; background: rgba(116, 228, 196, .5); border: 0; animation: none; }

@media (prefers-reduced-motion: reduce) {
  .ark-hud-ring::before, .ark-hud-fallback i { animation: none; }
  :deep(.ark-hud-canvas) { transition: none; }
}
</style>
