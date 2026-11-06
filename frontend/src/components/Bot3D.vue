<template>
  <div ref="wrap" class="bot3d" aria-hidden="true"></div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import * as THREE from 'three'
import { RoundedBoxGeometry } from 'three/examples/jsm/geometries/RoundedBoxGeometry.js'

/**
 * AI 助手吉祥物 · Three.js 3D 机器人（欢迎首页）
 * - 白色圆角头 + 黑色脸屏 + 青色发光眼（周期眨眼）+ 天线脉冲灯 + 胸口能量钮
 * - 整体上下悬浮 + 头部跟随鼠标转动（互动）
 * - 底部青色发光环脉冲；透明背景，可叠在渐变光晕上
 */
const wrap = ref<HTMLDivElement | null>(null)

let renderer: THREE.WebGLRenderer | null = null
let scene: THREE.Scene | null = null
let camera: THREE.PerspectiveCamera | null = null
let raf = 0
let clock: THREE.Clock | null = null
let bot: THREE.Group | null = null
let head: THREE.Group | null = null
let eyeL: THREE.Mesh | null = null
let eyeR: THREE.Mesh | null = null
let tipMat: THREE.MeshStandardMaterial | null = null
let ringMat: THREE.MeshBasicMaterial | null = null
let ro: ResizeObserver | null = null

const mouse = { x: 0, y: 0 }
const headAim = { x: 0, y: 0 }

function init() {
  const el = wrap.value
  if (!el) return
  const w = el.clientWidth || 210
  const h = el.clientHeight || 224

  scene = new THREE.Scene()
  camera = new THREE.PerspectiveCamera(34, w / h, 0.1, 50)
  camera.position.set(0, 1.05, 8.2)
  camera.lookAt(0, 0.75, 0)

  renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  renderer.setSize(w, h)
  el.appendChild(renderer.domElement)

  // ---- 灯光：环境 + 主方向光 + 脸前青色点光 ----
  scene.add(new THREE.AmbientLight(0xffffff, 1.15))
  const dir = new THREE.DirectionalLight(0xffffff, 1.6)
  dir.position.set(3, 6, 5)
  scene.add(dir)
  const cyan = new THREE.PointLight(0x4dd8ff, 12, 12)
  cyan.position.set(0, 1.6, 3.2)
  scene.add(cyan)

  bot = new THREE.Group()
  scene.add(bot)

  const white = new THREE.MeshStandardMaterial({ color: 0xffffff, roughness: 0.38, metalness: 0.06 })
  const whiteSoft = new THREE.MeshStandardMaterial({ color: 0xe6edf5, roughness: 0.5, metalness: 0.05 })
  const darkFace = new THREE.MeshStandardMaterial({ color: 0x141c26, roughness: 0.32, metalness: 0.2 })
  const glowCyan = new THREE.MeshStandardMaterial({ color: 0x4dd8ff, emissive: 0x4dd8ff, emissiveIntensity: 1.6, roughness: 0.3 })

  // ---- 头（含脸屏 / 眼 / 嘴 / 耳 / 天线），独立成组以便跟随鼠标 ----
  head = new THREE.Group()
  head.position.y = 1.72
  bot.add(head)

  const skull = new THREE.Mesh(new RoundedBoxGeometry(2.25, 1.62, 1.5, 5, 0.42), white)
  head.add(skull)

  const face = new THREE.Mesh(new RoundedBoxGeometry(1.82, 1.16, 0.14, 4, 0.3), darkFace)
  face.position.set(0, 0, 0.71)
  head.add(face)

  const eyeGeo = new THREE.CapsuleGeometry(0.1, 0.13, 6, 14)
  eyeL = new THREE.Mesh(eyeGeo, glowCyan)
  eyeL.position.set(-0.44, 0.1, 0.8)
  head.add(eyeL)
  eyeR = new THREE.Mesh(eyeGeo, glowCyan)
  eyeR.position.set(0.44, 0.1, 0.8)
  head.add(eyeR)

  const mouth = new THREE.Mesh(new THREE.TorusGeometry(0.17, 0.032, 12, 28, Math.PI), glowCyan)
  mouth.position.set(0, -0.24, 0.8)
  mouth.rotation.z = Math.PI
  head.add(mouth)

  const earGeo = new THREE.CylinderGeometry(0.1, 0.1, 0.24, 20)
  const earL = new THREE.Mesh(earGeo, whiteSoft)
  earL.rotation.z = Math.PI / 2
  earL.position.set(-1.2, 0, 0)
  head.add(earL)
  const earR = new THREE.Mesh(earGeo, whiteSoft)
  earR.rotation.z = Math.PI / 2
  earR.position.set(1.2, 0, 0)
  head.add(earR)

  const stem = new THREE.Mesh(new THREE.CylinderGeometry(0.035, 0.035, 0.5, 10), whiteSoft)
  stem.position.set(0, 1.02, 0)
  head.add(stem)
  const tip = new THREE.Mesh(new THREE.SphereGeometry(0.13, 20, 16), glowCyan.clone())
  tipMat = tip.material as THREE.MeshStandardMaterial
  tip.position.set(0, 1.34, 0)
  head.add(tip)

  // ---- 身体 / 胸口能量钮 / 手臂 ----
  const body = new THREE.Mesh(new RoundedBoxGeometry(1.5, 1.18, 1.12, 5, 0.38), white)
  body.position.y = 0.16
  bot.add(body)

  const core = new THREE.Mesh(new THREE.SphereGeometry(0.18, 22, 18), glowCyan.clone())
  ;(core.material as THREE.MeshStandardMaterial).emissiveIntensity = 1.2
  core.position.set(0, 0.26, 0.56)
  bot.add(core)

  const armGeo = new THREE.CapsuleGeometry(0.16, 0.5, 6, 14)
  const armL = new THREE.Mesh(armGeo, whiteSoft)
  armL.position.set(-0.96, 0.18, 0)
  armL.rotation.z = 0.16
  bot.add(armL)
  const armR = new THREE.Mesh(armGeo, whiteSoft)
  armR.position.set(0.96, 0.18, 0)
  armR.rotation.z = -0.16
  bot.add(armR)

  // ---- 底部发光环（双环：实线 + 淡晕）----
  ringMat = new THREE.MeshBasicMaterial({ color: 0x38d1ff, transparent: true, opacity: 0.6 })
  const ring = new THREE.Mesh(new THREE.TorusGeometry(1.38, 0.028, 10, 64), ringMat)
  ring.rotation.x = Math.PI / 2
  ring.position.y = -0.78
  bot.add(ring)
  const haloMat = new THREE.MeshBasicMaterial({ color: 0x38d1ff, transparent: true, opacity: 0.16, side: THREE.DoubleSide })
  const halo = new THREE.Mesh(new THREE.RingGeometry(1.05, 1.62, 64), haloMat)
  halo.rotation.x = -Math.PI / 2
  halo.position.y = -0.79
  bot.add(halo)

  // ---- 交互 & 尺寸自适应 ----
  const onMove = (e: PointerEvent) => {
    const r = el.getBoundingClientRect()
    mouse.x = Math.max(-1, Math.min(1, ((e.clientX - r.left) / r.width) * 2 - 1))
    mouse.y = Math.max(-1, Math.min(1, ((e.clientY - r.top) / r.height) * 2 - 1))
  }
  window.addEventListener('pointermove', onMove)
  const offMove = () => window.removeEventListener('pointermove', onMove)

  ro = new ResizeObserver(() => {
    if (!el || !renderer || !camera) return
    const nw = el.clientWidth || w
    const nh = el.clientHeight || h
    camera.aspect = nw / nh
    camera.updateProjectionMatrix()
    renderer.setSize(nw, nh)
  })
  ro.observe(el)

  // ---- 动画循环 ----
  clock = new THREE.Clock()
  const tick = () => {
    raf = requestAnimationFrame(tick)
    const t = clock.getElapsedTime()

    // 悬浮 + 轻微摆动
    if (bot) {
      bot.position.y = Math.sin(t * 1.5) * 0.13
      bot.rotation.z = Math.sin(t * 0.8) * 0.025
    }
    // 头部跟随鼠标（平滑追踪）
    if (head) {
      headAim.x += (mouse.y * 0.22 - headAim.x) * 0.06
      headAim.y += (mouse.x * 0.4 - headAim.y) * 0.06
      head.rotation.x = -headAim.x
      head.rotation.y = headAim.y
    }
    // 眨眼：每 4.6s 压扁一次
    const bp = t % 4.6
    const squint = (bp > 0.05 && bp < 0.2) ? 0.1 : 1
    if (eyeL && eyeR) {
      const cur = eyeL.scale.y
      eyeL.scale.y += (squint - cur) * 0.5
      eyeR.scale.y = eyeL.scale.y
    }
    // 天线灯 & 环脉冲
    if (tipMat) tipMat.emissiveIntensity = 1.4 + Math.sin(t * 2.6) * 0.7
    if (ringMat) ringMat.opacity = 0.5 + Math.sin(t * 1.5) * 0.22

    renderer?.render(scene!, camera!)
  }
  tick()

  // ---- 资源释放 ----
  cleanups.push(offMove)
}

const cleanups: Array<() => void> = []

function destroy() {
  cancelAnimationFrame(raf)
  ro?.disconnect()
  cleanups.forEach((f) => f())
  cleanups.length = 0
  if (scene) {
    scene.traverse((o) => {
      const m = o as THREE.Mesh
      if (m.geometry) m.geometry.dispose()
      const mat = m.material as THREE.Material | THREE.Material[] | undefined
      if (Array.isArray(mat)) mat.forEach((x) => x.dispose())
      else mat?.dispose()
    })
    scene.clear()
  }
  renderer?.dispose()
  renderer?.domElement?.remove()
  renderer = null; scene = null; camera = null; bot = null; head = null
}

onMounted(init)
onUnmounted(destroy)
</script>

<style scoped>
.bot3d { width: 100%; height: 100%; }
.bot3d canvas { display: block; }
</style>
