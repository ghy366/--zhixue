<template>
  <div ref="wrap" class="bot3d" aria-hidden="true"></div>
</template>

<script setup lang="ts">
/**
 * AI 助手 · 云白机器人（Bot3D v3 · Light）
 *
 * 视觉：白瓷机身 + 冰蓝玻璃面屏 + 青蓝能量核 + 暖黄小点缀，
 *   圆润大眼 / 微笑弧 / 淡粉腮红，整体高调明亮，去掉暗金属与黄黑警示纹。
 *
 * 语义：state 由 Chat.vue 的 loading / streamingNow / error 驱动，
 *   idle 呼吸 → thinking 扫视+环加速 → streaming 稳定输出 → error 一次红闪。
 *
 * 工程：three 按需动态 import；无 WebGL 降级 CSS；reduced-motion 停帧；
 *   DPR 上限；ResizeObserver；用 performance.now 计时；卸载时完整 dispose。
 */
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import type * as ThreeNS from 'three'

type BotState = 'idle' | 'thinking' | 'streaming' | 'error'
const props = withDefaults(defineProps<{ state?: BotState; compact?: boolean }>(), {
  state: 'idle',
  compact: false,
})

const wrap = ref<HTMLDivElement | null>(null)

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
  else if (s === 'thinking' || s === 'streaming') pulse = Math.max(pulse, 0.6)
  if (reduced) drawFn?.()
})

/** 面屏渐变：上白下冰蓝，带一层柔光，程序化生成不引入贴图文件 */
function faceTexture(T: typeof import('three')) {
  const c = document.createElement('canvas')
  c.width = 256; c.height = 160
  const g = c.getContext('2d')
  if (g) {
    const lg = g.createLinearGradient(0, 0, 0, 160)
    lg.addColorStop(0, '#eaf8ff')
    lg.addColorStop(0.5, '#c7e9fa')
    lg.addColorStop(1, '#a7d5f2')
    g.fillStyle = lg
    g.fillRect(0, 0, 256, 160)
    const rg = g.createRadialGradient(128, 8, 4, 128, 8, 170)
    rg.addColorStop(0, 'rgba(255,255,255,.9)')
    rg.addColorStop(1, 'rgba(255,255,255,0)')
    g.fillStyle = rg
    g.fillRect(0, 0, 256, 160)
  }
  const tex = new T.CanvasTexture(c)
  tex.colorSpace = T.SRGBColorSpace
  return tex
}

/** 径向柔光：眼/核/天线/底环共用，白心青边 */
function glowTexture(T: typeof import('three')) {
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
  return tex
}

async function init() {
  const el = wrap.value
  if (!el) return

  let T: typeof import('three')
  let RoundedBoxGeometry: typeof import('three/examples/jsm/geometries/RoundedBoxGeometry.js').RoundedBoxGeometry
  try {
    const [three, rbg] = await Promise.all([
      import('three'),
      import('three/examples/jsm/geometries/RoundedBoxGeometry.js'),
    ])
    T = three
    RoundedBoxGeometry = rbg.RoundedBoxGeometry
  } catch {
    el.classList.add('is-fallback')
    return
  }
  if (disposed) return

  const keep = <X extends { dispose: () => void }>(o: X): X => { disposables.push(o); return o }

  let gl: ThreeNS.WebGLRenderer
  try {
    gl = new T.WebGLRenderer({ antialias: true, alpha: true, powerPreference: 'high-performance' })
  } catch {
    el.classList.add('is-fallback')
    return
  }
  renderer = gl
  gl.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.75))
  gl.setClearColor(0x000000, 0)
  gl.outputColorSpace = T.SRGBColorSpace
  el.appendChild(gl.domElement)

  scene = new T.Scene()
  camera = new T.PerspectiveCamera(34, 1, 0.1, 60)
  const lookY = props.compact ? 1.72 : 0.8
  if (props.compact) camera.position.set(0, 1.95, 3.6)
  else camera.position.set(0, 1.15, 8.4)
  camera.lookAt(0, lookY, 0)

  drawFn = () => { if (renderer && scene && camera) renderer.render(scene, camera) }

  // ---------- 灯光：高调布光，白主光 + 冷蓝轮廓 + 暖补光 ----------
  scene.add(new T.AmbientLight(0xffffff, 2.0))
  const key = new T.DirectionalLight(0xffffff, 2.4)
  key.position.set(3.5, 6, 5)
  scene.add(key)
  const rim = new T.DirectionalLight(0x8fd8ff, 1.4)
  rim.position.set(-4, 2.5, -4)
  scene.add(rim)
  const fill = new T.DirectionalLight(0xffd9a8, 0.6)
  fill.position.set(0, -2, 4)
  scene.add(fill)
  const faceLight = new T.PointLight(0x7fd4f5, 6, 9)
  faceLight.position.set(0, 1.7, 3)
  scene.add(faceLight)

  // ---------- 材质 ----------
  const shell = keep(new T.MeshPhysicalMaterial({
    color: 0xf6fbff, metalness: 0.04, roughness: 0.36,
    clearcoat: 0.7, clearcoatRoughness: 0.22,
  }))
  const shellShade = keep(new T.MeshStandardMaterial({ color: 0xd9e9f5, metalness: 0.1, roughness: 0.48 }))
  const shellDeep = keep(new T.MeshStandardMaterial({ color: 0x9fc6e0, metalness: 0.18, roughness: 0.42 }))
  const faceTex = keep(faceTexture(T))
  const glass = keep(new T.MeshPhysicalMaterial({
    color: 0xffffff, map: faceTex,
    metalness: 0.05, roughness: 0.16, clearcoat: 1, clearcoatRoughness: 0.1,
    emissive: 0x2a7fb8, emissiveIntensity: 0.12,
  }))
  const accent = keep(new T.MeshStandardMaterial({
    color: 0x4ec5f1, metalness: 0.25, roughness: 0.3,
    emissive: 0x1a86c8, emissiveIntensity: 0.45,
  }))
  const eyeMat = keep(new T.MeshBasicMaterial({ color: 0x1d4f76 }))
  const eyeHiMat = keep(new T.MeshBasicMaterial({ color: 0xffffff }))
  const smileMat = keep(new T.MeshBasicMaterial({ color: 0x2a6a92 }))
  const blushMat = keep(new T.MeshBasicMaterial({ color: 0xffb3c1, transparent: true, opacity: 0.5, depthWrite: false }))
  const seamMat = keep(new T.LineBasicMaterial({ color: 0x8fb9d6, transparent: true, opacity: 0.32, depthWrite: false }))
  const glowTex = keep(glowTexture(T))

  // 背后淡青柔光衬底：让白瓷机身在浅色页面上有轮廓，不至于糊成一片
  const backGlowMat = keep(new T.SpriteMaterial({
    map: glowTex, color: 0x8fd0f2, transparent: true, opacity: 0.42,
    depthWrite: false,
  }))
  const backGlow = new T.Sprite(backGlowMat)
  backGlow.scale.set(5.4, 5.4, 1)
  backGlow.position.set(0, 0.8, -1.6)
  scene.add(backGlow)

  const bot = new T.Group()
  scene.add(bot)

  // ---------- 头：白瓷壳 + 冰蓝面屏 + 圆眼微笑 ----------
  const head = new T.Group()
  head.position.y = 1.72
  bot.add(head)

  const skullGeo = keep(new RoundedBoxGeometry(2.2, 1.62, 1.5, 5, 0.42))
  head.add(new T.Mesh(skullGeo, shell))

  // 头顶浅青装饰带（替代原黄黑警示纹）
  const bandGeo = keep(new RoundedBoxGeometry(1.86, 0.16, 1.3, 3, 0.07))
  const band = new T.Mesh(bandGeo, shellShade)
  band.position.set(0, 0.84, 0)
  band.rotation.x = -0.06
  head.add(band)
  const bandLine = new T.Mesh(keep(new T.BoxGeometry(1.5, 0.025, 0.02)), accent)
  bandLine.position.set(0, 0.85, 0.62)
  head.add(bandLine)

  const faceGeo = keep(new RoundedBoxGeometry(1.86, 1.14, 0.1, 4, 0.3))
  const face = new T.Mesh(faceGeo, glass)
  face.position.set(0, -0.02, 0.74)
  head.add(face)
  const faceRim = new T.Mesh(
    keep(new RoundedBoxGeometry(1.94, 1.22, 0.06, 4, 0.32)),
    shellShade,
  )
  faceRim.position.set(0, -0.02, 0.7)
  head.add(faceRim)

  // 面屏柔光扫描带（thinking 时加快）
  const hudGeo = keep(new T.PlaneGeometry(1.6, 0.09))
  const hudMat = keep(new T.MeshBasicMaterial({
    color: 0xffffff, transparent: true, opacity: 0.28,
    blending: T.AdditiveBlending, depthWrite: false,
  }))
  const hud = new T.Mesh(hudGeo, hudMat)
  hud.position.set(0, 0, 0.8)
  head.add(hud)

  // 眼睛：深蓝圆眼 + 白色高光点 + 柔光晕
  const eyeGeo = keep(new T.SphereGeometry(0.125, 20, 16))
  const hiGeo = keep(new T.SphereGeometry(0.042, 12, 10))
  const eyes: ThreeNS.Mesh[] = []
  const eyeGlows: ThreeNS.Sprite[] = []
  const eyeGlowMats: ThreeNS.SpriteMaterial[] = []
  for (const sx of [-1, 1]) {
    const e = new T.Mesh(eyeGeo, eyeMat)
    e.position.set(sx * 0.43, 0.12, 0.83)
    head.add(e)
    eyes.push(e)
    const hi = new T.Mesh(hiGeo, eyeHiMat)
    hi.position.set(sx * 0.43 - 0.038, 0.16, 0.94)
    head.add(hi)
    const gm = keep(new T.SpriteMaterial({
      map: glowTex, color: 0x8fe0ff, transparent: true, opacity: 0.42,
      blending: T.AdditiveBlending, depthWrite: false,
    }))
    const sp = new T.Sprite(gm)
    sp.scale.set(0.52, 0.52, 1)
    sp.position.set(sx * 0.43, 0.12, 0.86)
    head.add(sp)
    eyeGlows.push(sp)
    eyeGlowMats.push(gm)
  }

  // 微笑弧（下半圆）
  const smileGeo = keep(new T.TorusGeometry(0.16, 0.022, 8, 24, Math.PI))
  const smile = new T.Mesh(smileGeo, smileMat)
  smile.position.set(0, -0.16, 0.82)
  smile.rotation.z = Math.PI
  head.add(smile)

  // 腮红
  const blushGeo = keep(new T.CircleGeometry(0.085, 20))
  for (const sx of [-1, 1]) {
    const b = new T.Mesh(blushGeo, blushMat)
    b.position.set(sx * 0.72, -0.22, 0.81)
    head.add(b)
  }

  // 耳罩：浅蓝圆盘 + 青蓝内圈
  const earGeo = keep(new T.CylinderGeometry(0.2, 0.2, 0.24, 20))
  const earInnerGeo = keep(new T.CylinderGeometry(0.13, 0.13, 0.26, 20))
  for (const sx of [-1, 1]) {
    const ear = new T.Mesh(earGeo, shellShade)
    ear.rotation.z = Math.PI / 2
    ear.position.set(sx * 1.14, 0, 0)
    head.add(ear)
    const inner = new T.Mesh(earInnerGeo, accent)
    inner.rotation.z = Math.PI / 2
    inner.position.set(sx * 1.27, 0, 0)
    head.add(inner)
  }

  // 天线：细杆 + 暖黄小球 + 光晕
  const stemGeo = keep(new T.CylinderGeometry(0.032, 0.042, 0.46, 10))
  const stem = new T.Mesh(stemGeo, shellShade)
  stem.position.set(0, 1.02, 0)
  head.add(stem)
  const tipGeo = keep(new T.SphereGeometry(0.115, 18, 14))
  const tipMat = keep(new T.MeshBasicMaterial({ color: 0xffc75e }))
  const tip = new T.Mesh(tipGeo, tipMat)
  tip.position.set(0, 1.32, 0)
  head.add(tip)
  const tipGlowMat = keep(new T.SpriteMaterial({
    map: glowTex, color: 0xffd98a, transparent: true, opacity: 0.6,
    blending: T.AdditiveBlending, depthWrite: false,
  }))
  const tipGlow = new T.Sprite(tipGlowMat)
  tipGlow.scale.set(0.52, 0.52, 1)
  tipGlow.position.copy(tip.position)
  head.add(tipGlow)

  // ---------- 颈 / 身体 / 手臂 ----------
  const neckGeo = keep(new T.CylinderGeometry(0.24, 0.3, 0.3, 16))
  const neck = new T.Mesh(neckGeo, shellDeep)
  neck.position.y = 0.82
  bot.add(neck)
  const neckRing = new T.Mesh(keep(new T.TorusGeometry(0.31, 0.022, 6, 24)), accent)
  neckRing.rotation.x = Math.PI / 2
  neckRing.position.y = 0.82
  bot.add(neckRing)

  const torsoGeo = keep(new RoundedBoxGeometry(1.5, 1.18, 1.08, 5, 0.36))
  const torso = new T.Mesh(torsoGeo, shell)
  torso.position.y = 0.14
  bot.add(torso)

  const chestGeo = keep(new RoundedBoxGeometry(1.06, 0.64, 0.09, 3, 0.14))
  const chest = new T.Mesh(chestGeo, shellShade)
  chest.position.set(0, 0.2, 0.55)
  bot.add(chest)
  const chestSeam = new T.LineSegments(keep(new T.EdgesGeometry(chestGeo, 40)), seamMat)
  chestSeam.position.copy(chest.position)
  bot.add(chestSeam)

  // 胸口能量核
  const coreGeo = keep(new T.SphereGeometry(0.18, 20, 14))
  const coreMat = keep(new T.MeshBasicMaterial({ color: 0x4ec5f1 }))
  const core = new T.Mesh(coreGeo, coreMat)
  core.position.set(0, 0.22, 0.65)
  bot.add(core)
  const coreRing = new T.Mesh(keep(new T.TorusGeometry(0.28, 0.022, 6, 32)), accent)
  coreRing.position.set(0, 0.22, 0.63)
  bot.add(coreRing)
  const coreGlowMat = keep(new T.SpriteMaterial({
    map: glowTex, color: 0x8fe0ff, transparent: true, opacity: 0.7,
    blending: T.AdditiveBlending, depthWrite: false,
  }))
  const coreGlow = new T.Sprite(coreGlowMat)
  coreGlow.scale.set(0.95, 0.95, 1)
  coreGlow.position.set(0, 0.22, 0.7)
  bot.add(coreGlow)

  // 腹部浅青色带
  const beltGeo = keep(new RoundedBoxGeometry(0.78, 0.1, 0.06, 2, 0.04))
  const belt = new T.Mesh(beltGeo, accent)
  belt.position.set(0, -0.28, 0.55)
  bot.add(belt)

  const shoulderGeo = keep(new T.SphereGeometry(0.3, 18, 14))
  const upperGeo = keep(new T.CapsuleGeometry(0.13, 0.34, 5, 12))
  const foreGeo = keep(new T.CapsuleGeometry(0.11, 0.28, 5, 12))
  const jointGeo = keep(new T.SphereGeometry(0.14, 12, 10))
  const arms: ThreeNS.Group[] = []
  for (const sx of [-1, 1]) {
    const shoulder = new T.Mesh(shoulderGeo, shellShade)
    shoulder.position.set(sx * 0.92, 0.5, 0)
    shoulder.scale.set(1, 0.82, 0.9)
    bot.add(shoulder)

    const arm = new T.Group()
    arm.position.set(sx * 1.0, 0.28, 0)
    const upper = new T.Mesh(upperGeo, shell)
    upper.position.set(0, -0.22, 0)
    arm.add(upper)
    const joint = new T.Mesh(jointGeo, shellDeep)
    joint.position.set(0, -0.5, 0)
    arm.add(joint)
    const fore = new T.Mesh(foreGeo, shell)
    fore.position.set(0, -0.74, 0)
    arm.add(fore)
    bot.add(arm)
    arms.push(arm)
  }

  // ---------- 底部悬浮光环 + 刻度 + 气泡粒子 ----------
  const ringGroup = new T.Group()
  ringGroup.position.y = -0.82
  bot.add(ringGroup)
  const ringMat = keep(new T.MeshBasicMaterial({
    color: 0x74d3f5, transparent: true, opacity: 0.6,
    blending: T.AdditiveBlending, depthWrite: false,
  }))
  const ring = new T.Mesh(keep(new T.TorusGeometry(1.34, 0.024, 8, 64)), ringMat)
  ring.rotation.x = Math.PI / 2
  ringGroup.add(ring)
  const haloMat = keep(new T.MeshBasicMaterial({
    color: 0x9fe0f8, transparent: true, opacity: 0.14, side: T.DoubleSide,
    blending: T.AdditiveBlending, depthWrite: false,
  }))
  const halo = new T.Mesh(keep(new T.RingGeometry(1.0, 1.6, 64)), haloMat)
  halo.rotation.x = -Math.PI / 2
  halo.position.y = -0.01
  ringGroup.add(halo)

  const tickPts: number[] = []
  for (let i = 0; i < 48; i++) {
    const a = (i / 48) * Math.PI * 2
    const r0 = i % 6 === 0 ? 1.48 : 1.55
    tickPts.push(Math.cos(a) * r0, 0, Math.sin(a) * r0, Math.cos(a) * 1.62, 0, Math.sin(a) * 1.62)
  }
  const tickGeo = keep(new T.BufferGeometry())
  tickGeo.setAttribute('position', new T.Float32BufferAttribute(tickPts, 3))
  ringGroup.add(new T.LineSegments(tickGeo, seamMat))

  const dustN = 56
  const dustPos = new Float32Array(dustN * 3)
  for (let i = 0; i < dustN; i++) {
    const a = Math.random() * Math.PI * 2
    const rad = 1.3 + Math.random() * 1.8
    dustPos[i * 3] = Math.cos(a) * rad
    dustPos[i * 3 + 1] = (Math.random() - 0.5) * 3.2 + 0.8
    dustPos[i * 3 + 2] = Math.sin(a) * rad - 0.4
  }
  const dustGeo = keep(new T.BufferGeometry())
  dustGeo.setAttribute('position', new T.BufferAttribute(dustPos, 3))
  const dustMat = keep(new T.PointsMaterial({
    color: 0xffffff, size: 0.045, transparent: true, opacity: 0.65,
    sizeAttenuation: true, depthWrite: false,
  }))
  const dust = new T.Points(dustGeo, dustMat)
  scene.add(dust)

  // ---------- 尺寸 / 交互 ----------
  const resize = () => {
    if (!renderer || !camera || !el) return
    const w = el.clientWidth || 210
    const h = el.clientHeight || 224
    camera.aspect = w / h
    camera.updateProjectionMatrix()
    renderer.setSize(w, h, false)
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

  // ---------- 帧循环：状态驱动 ----------
  let last = performance.now()
  let elapsed = 0
  const colBlue = new T.Color(0x4ec5f1)
  const colRed = new T.Color(0xff5b6e)
  const colMix = new T.Color()
  const loop = (now: number) => {
    if (disposed || !renderer || !scene || !camera) return
    const dt = Math.min((now - last) / 1000, 0.05)
    last = now
    elapsed += dt
    const t = elapsed
    pulse = Math.max(0, pulse - dt * 1.1)
    errorFlash = Math.max(0, errorFlash - dt * 1.4)

    const busy = curState === 'thinking' || curState === 'streaming'
    const speed = curState === 'thinking' ? 1.7 : curState === 'streaming' ? 1.15 : 0.42
    const err = errorFlash

    bot.position.y = Math.sin(t * 1.4) * 0.11
    bot.rotation.z = Math.sin(t * 0.75) * 0.022

    mouse.x += (mouse.tx - mouse.x) * 0.06
    mouse.y += (mouse.ty - mouse.y) * 0.06
    const scan = curState === 'thinking' ? Math.sin(t * 2.4) * 0.2 : 0
    head.rotation.y += (mouse.x * 0.35 + scan - head.rotation.y) * 0.07
    head.rotation.x += (-mouse.y * 0.18 - head.rotation.x) * 0.07

    // 眨眼：idle 每 4.6s 一次；忙时不眨
    const bp = t % 4.6
    const squint = !busy && bp > 0.05 && bp < 0.18 ? 0.14 : 1
    eyes.forEach((e, i) => {
      e.scale.y += (squint - e.scale.y) * 0.45
      eyeGlows[i].scale.setScalar(0.5 + pulse * 0.22 + (busy ? 0.1 : 0))
      eyeGlowMats[i].opacity = 0.36 + pulse * 0.3 + (busy ? 0.14 : 0)
    })

    // 颜色：正常青蓝，错误时一次红闪后回落
    colMix.copy(colBlue).lerp(colRed, err)
    coreMat.color.copy(colMix)
    tipMat.color.copy(colMix)
    coreGlowMat.color.copy(colMix)
    tipGlowMat.color.copy(colMix)
    eyeGlowMats.forEach(m => m.color.copy(colMix))

    const beat = 0.68 + Math.sin(t * (busy ? 6.5 : 2.2)) * (busy ? 0.22 : 0.1) + pulse * 0.35 + err * 0.4
    coreGlowMat.opacity = Math.min(1, beat)
    core.scale.setScalar(0.9 + beat * 0.16)
    coreRing.rotation.z += dt * (0.5 + speed)
    faceLight.intensity = 5 + beat * 4

    // 面屏柔光带
    hud.position.y = Math.sin(t * (busy ? 3.2 : 0.9)) * 0.4
    hudMat.opacity = 0.18 + (busy ? 0.18 : 0.08) + pulse * 0.12

    // 微笑：thinking 略收，streaming 舒展
    smile.scale.x = curState === 'thinking' ? 0.8 + Math.sin(t * 5) * 0.08 : curState === 'streaming' ? 1.18 : 1
    ringGroup.rotation.y += dt * speed
    ringMat.opacity = 0.5 + Math.sin(t * 1.6) * 0.14 + pulse * 0.2
    haloMat.opacity = 0.12 + Math.sin(t * 1.6) * 0.03 + pulse * 0.08
    dust.rotation.y += dt * 0.06 * speed
    dustMat.opacity = 0.5 + pulse * 0.25 + (busy ? 0.1 : 0)

    arms.forEach((a, i) => { a.rotation.z = Math.sin(t * 1.1 + i * 2) * (busy ? 0.09 : 0.035) })
    tip.scale.setScalar(0.9 + Math.sin(t * 2.6) * 0.12 + pulse * 0.25)

    camera.position.x += (mouse.x * 0.25 - camera.position.x) * 0.04
    camera.lookAt(0, lookY, 0)
    drawFn?.()
    raf = requestAnimationFrame(loop)
  }

  if (reduced) {
    drawFn?.()
  } else {
    raf = requestAnimationFrame(loop)
  }
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
.bot3d canvas { display: block; }

/* 无 WebGL / three 加载失败：CSS 画一个简版云白小机器人 */
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
