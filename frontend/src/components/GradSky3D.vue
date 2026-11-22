<template>
  <div ref="wrap" class="gsky-wrap">
    <!-- 左上：标题 + 类别图例 -->
    <div class="gsky-panel gsky-legend">
      <div class="gsky-title"><i class="gsky-title-mark" />毕业星系 <span>GRADUATION ATLAS</span></div>
      <div v-for="c in cats" :key="c.key" class="gsky-lg-row">
        <i class="gsky-lg-dot" :style="{ background: cssColor(c.key), boxShadow: '0 0 9px ' + cssColor(c.key) }" />
        <b>{{ c.key }}</b>
        <span class="gsky-lg-num">{{ Math.min(c.earned || 0, c.required || 0) }}/{{ c.required }}</span>
      </div>
    </div>

    <!-- 右上：状态图例 -->
    <div class="gsky-panel gsky-state">
      <span><i style="background:#34d399;box-shadow:0 0 8px #34d399" />已通过</span>
      <span><i style="background:#f43f5e;box-shadow:0 0 8px #f43f5e" />未通过</span>
      <span><i style="background:#fbbf24;box-shadow:0 0 8px #fbbf24" />重修/在修</span>
      <span><i style="background:#64748b" />未修</span>
    </div>

    <!-- 底部进度坞：不遮挡中心 -->
    <div class="gsky-dock">
      <div class="dock-num">{{ percent }}<small>%</small></div>
      <div class="dock-side">
        <div class="dock-label">毕业学分进度 <b>已获 {{ earned }} / {{ required }} 学分</b></div>
        <div class="dock-bars">
          <div v-for="c in cats" :key="c.key" class="dock-bar-row">
            <span>{{ c.key }}</span>
            <div class="dock-bar"><i :style="{ width: barPct(c) + '%', background: cssColor(c.key) }" /></div>
            <b>{{ barPct(c) }}%</b>
          </div>
        </div>
      </div>
      <div class="dock-gap" :class="{ ok: gap <= 0 }">
        <template v-if="gap > 0">还差 <b>{{ gap }}</b> 学分</template>
        <template v-else>全部达标</template>
      </div>
    </div>

    <!-- 操作提示 -->
    <div class="gsky-tip">拖拽旋转 · 滚轮缩放 · 悬停查看课程</div>

    <!-- 悬停提示 -->
    <div v-if="hover" class="gsky-tooltip" :style="{ left: hover.x + 'px', top: hover.y + 'px' }">
      <b>{{ hover.star.name }}</b>
      <em>{{ hover.star.group }} · {{ hover.star.credit }} 学分 · {{ semesterTitle(hover.star.semesterNo) }}</em>
      <span :class="'st-' + hover.star.status">{{ statusText(hover.star) }}</span>
    </div>
    <div v-if="!ready" class="gsky-loading">星系生成中…</div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import type * as ThreeNS from 'three'

type Star = { name: string; group: string; credit: number; semesterNo: number | null; status: string; score: number | null }
type Cat = { key: string; earned: number; required: number; done?: boolean }

const props = defineProps<{
  stars: Star[]
  cats: Cat[]
  percent: number
  earned: number
  required: number
  gap: number
}>()

const wrap = ref<HTMLElement | null>(null)
const ready = ref(false)
const hover = ref<{ x: number; y: number; star: Star } | null>(null)

const HEX: Record<string, string> = { '必修': '#2dd4bf', '公共选修': '#fbbf24', '专业选修': '#60a5fa' }
const NUM: Record<string, number> = { '必修': 0x2dd4bf, '公共选修': 0xfbbf24, '专业选修': 0x60a5fa }
const STATUS_NUM: Record<string, number> = {
  passed: 0x34d399, failed: 0xf43f5e, retaking: 0xfbbf24, pending: 0x38bdf8, planned: 0x64748b,
}
const cssColor = (k: string) => HEX[k] || '#2dd4bf'
const barPct = (c: Cat) => c.required > 0 ? Math.round(Math.min(100, (c.earned || 0) / c.required * 100)) : 0
function statusText(s: Star) {
  if (s.status === 'passed') return `已通过 · ${s.score} 分`
  if (s.status === 'failed') return `未通过 · ${s.score} 分`
  if (s.status === 'retaking') return '重修中'
  if (s.status === 'pending') return '在修 · 待出成绩'
  return '未修 · 培养方案计划课程'
}

let dispose: (() => void) | null = null
let raf = 0

/** 径向光斑贴图：核心亮点 + 柔和外晕 */
function glowTexture(T: typeof import('three'), hex: number) {
  const cv = document.createElement('canvas')
  cv.width = cv.height = 128
  const ctx = cv.getContext('2d')!
  const c = '#' + hex.toString(16).padStart(6, '0')
  const g = ctx.createRadialGradient(64, 64, 0, 64, 64, 64)
  g.addColorStop(0, 'rgba(255,255,255,1)')
  g.addColorStop(0.12, c)
  g.addColorStop(0.40, c + '66')
  g.addColorStop(1, 'rgba(0,0,0,0)')
  ctx.fillStyle = g
  ctx.fillRect(0, 0, 128, 128)
  const tex = new T.CanvasTexture(cv)
  tex.needsUpdate = true
  return tex
}

onMounted(async () => {
  const T: typeof import('three') = await import('three')
  const el = wrap.value!
  const W = () => el.clientWidth || 1
  const H = () => el.clientHeight || 1

  const scene = new T.Scene()
  const camera = new T.PerspectiveCamera(46, W() / H(), 0.1, 300)
  camera.position.set(0, 8.0, 16)
  camera.lookAt(0, 0.6, 0)

  const renderer = new T.WebGLRenderer({ antialias: true, alpha: true, powerPreference: 'high-performance' })
  renderer.setPixelRatio(Math.min(2, window.devicePixelRatio))
  renderer.setSize(W(), H())
  el.appendChild(renderer.domElement)

  const system = new T.Group()
  scene.add(system)

  // ---------- 远景星野（不随星系旋转，提供纵深参照） ----------
  {
    const N = 900
    const pos = new Float32Array(N * 3)
    const col = new Float32Array(N * 3)
    const palette = [0x9fc7e8, 0xd7e9ff, 0x7fa8cc, 0xbcd6f2]
    for (let i = 0; i < N; i++) {
      const r = 48 + Math.random() * 70
      const th = Math.random() * Math.PI * 2
      const ph = Math.acos(2 * Math.random() - 1)
      pos[i * 3] = r * Math.sin(ph) * Math.cos(th)
      pos[i * 3 + 1] = r * Math.cos(ph) * 0.7
      pos[i * 3 + 2] = r * Math.sin(ph) * Math.sin(th)
      const c = new T.Color(palette[(Math.random() * palette.length) | 0])
      col[i * 3] = c.r; col[i * 3 + 1] = c.g; col[i * 3 + 2] = c.b
    }
    const geo = new T.BufferGeometry()
    geo.setAttribute('position', new T.BufferAttribute(pos, 3))
    geo.setAttribute('color', new T.BufferAttribute(col, 3))
    scene.add(new T.Points(geo, new T.PointsMaterial({
      size: 0.22, vertexColors: true, transparent: true, opacity: 0.65,
      sizeAttenuation: true, depthWrite: false,
    })))
  }

  // ---------- 星云薄雾 ----------
  const nebulaTex = glowTexture(T, 0x1d6f8f)
  for (const [x, y, z, s, o] of [[-16, -3, -22, 30, 0.10], [14, 4, -26, 36, 0.08], [0, -6, -34, 44, 0.06]]) {
    const sp = new T.Sprite(new T.SpriteMaterial({ map: nebulaTex, transparent: true, opacity: o, depthWrite: false, blending: T.AdditiveBlending }))
    sp.position.set(x, y, z); sp.scale.setScalar(s)
    scene.add(sp)
  }

  // ---------- 中心：毕业星球 ----------
  const coreGroup = new T.Group()
  coreGroup.position.y = 0.6
  system.add(coreGroup)

  const core = new T.Mesh(
    new T.IcosahedronGeometry(1.15, 3),
    new T.MeshStandardMaterial({
      color: 0x0f5d63, emissive: 0x1b8f86, emissiveIntensity: 0.85,
      metalness: 0.65, roughness: 0.28, flatShading: true,
    })
  )
  coreGroup.add(core)

  // 大气层（边缘发光）
  const atmosphere = new T.Mesh(
    new T.SphereGeometry(1.36, 32, 24),
    new T.MeshBasicMaterial({ color: 0x4ade9f, transparent: true, opacity: 0.10, side: T.BackSide, blending: T.AdditiveBlending, depthWrite: false })
  )
  coreGroup.add(atmosphere)

  // 赤道光环
  const eqRing = new T.Mesh(
    new T.TorusGeometry(1.85, 0.018, 6, 160),
    new T.MeshBasicMaterial({ color: 0x5eead4, transparent: true, opacity: 0.55, blending: T.AdditiveBlending })
  )
  eqRing.rotation.set(Math.PI / 2.25, 0.35, 0)
  coreGroup.add(eqRing)

  const coreGlow = new T.Sprite(new T.SpriteMaterial({
    map: glowTexture(T, 0x2dd4bf), transparent: true, opacity: 0.5,
    depthWrite: false, blending: T.AdditiveBlending,
  }))
  coreGlow.scale.setScalar(4.8)
  coreGroup.add(coreGlow)

  scene.add(new T.AmbientLight(0x8fd8ff, 1.1))
  const keyLight = new T.PointLight(0x7fffd4, 2.4, 40)
  keyLight.position.set(6, 9, 8)
  scene.add(keyLight)
  const rimLight = new T.PointLight(0x4f8cff, 1.4, 40)
  rimLight.position.set(-9, -3, -7)
  scene.add(rimLight)

  // ---------- 三条轨道 ----------
  const keys = props.cats.map(c => c.key)
  const groupOf = (g: string) => (keys.includes(g) ? g : keys[0] || '必修')
  const RADII = [2.6, 3.9, 5.2]
  const tracks = new Map<string, T.Group>()
  const starMeshes: T.Mesh[] = []
  const starRecs: Array<{ mesh: T.Mesh; glow: T.Sprite; base: number; phase: number; passed: boolean }> = []
  const meta = new Map<T.Mesh, Star>()

  const byGroup: Record<string, Star[]> = {}
  for (const s of props.stars) (byGroup[groupOf(s.group)] ||= []).push(s)

  const boxGeo = new T.OctahedronGeometry(0.34, 0)

  keys.forEach((g, gi) => {
    const R = RADII[gi % RADII.length] || 6.5
    const color = NUM[g] || 0x2dd4bf
    const track = new T.Group()
    track.rotation.x = 0
    system.add(track)
    tracks.set(g, track)

    // 轨道环：LineLoop 画圆（LineBasicMaterial 无背面剔除，从任意角度都可见）
    const circlePts = (radius: number) => {
      const pts: T.Vector3[] = []
      for (let k = 0; k < 180; k++) {
        const a = (k / 180) * Math.PI * 2
        pts.push(new T.Vector3(Math.cos(a) * radius, 0, Math.sin(a) * radius))
      }
      return pts
    }
    // 用 TubeGeometry 保证有实际厚度（WebGL 的线宽恒为 1px，太细看不见）
    const ringCurve = new T.CatmullRomCurve3(circlePts(R), true)
    track.add(new T.Mesh(
      new T.TubeGeometry(ringCurve, 180, 0.035, 6, true),
      new T.MeshBasicMaterial({ color, transparent: false, opacity: 1 })
    ))
    const ringCurve2 = new T.CatmullRomCurve3(circlePts(R + 0.22), true)
    track.add(new T.Mesh(
      new T.TubeGeometry(ringCurve2, 180, 0.015, 5, true),
      new T.MeshBasicMaterial({ color, transparent: false, opacity: 1 })
    ))

    // 课程晶体星：沿轨道均匀分布，高度轻微起伏
    const list = byGroup[g] || []
    list.forEach((s, i) => {
      const a = (i / Math.max(1, list.length)) * Math.PI * 2 + gi * 0.7
      const r = R + Math.sin(i * 2.7) * 0.18
      const passed = s.status === 'passed'
      const stColor = STATUS_NUM[s.status] ?? color
      const scale = (0.8 + Math.min(0.7, (s.credit || 1) * 0.18)) * (passed ? 1.3 : 0.95)

      const mesh = new T.Mesh(
        boxGeo,
        new T.MeshBasicMaterial({
          color: stColor, transparent: true,
          opacity: passed ? 1 : s.status === 'planned' ? 0.72 : 0.9,
          toneMapped: false,
        })
      )
      mesh.position.set(Math.cos(a) * r, Math.sin(a * 3 + i) * 0.42, Math.sin(a) * r)
      mesh.scale.setScalar(scale)
      mesh.rotation.set(a, a * 1.4, 0)
      track.add(mesh)
      starMeshes.push(mesh)
      meta.set(mesh, s)
      // 必见核心光斑：Sprite 不受线宽/面朝向影响
      const coreSp = new T.Sprite(new T.SpriteMaterial({
        map: glowTexture(T, stColor), transparent: true, opacity: passed ? 0.85 : 0.5,
        depthWrite: false, depthTest: false,
      }))
      coreSp.position.copy(mesh.position)
      coreSp.scale.setScalar(scale * 1.8)
      track.add(coreSp)

      // 所有星都带光晕：暗星微弱、亮星强烈，保证 0 进度时星系仍清晰
      const glow = new T.Sprite(new T.SpriteMaterial({
        map: glowTexture(T, stColor), transparent: true,
        opacity: passed ? 0.55 : s.status === 'planned' ? 0.14 : 0.3,
        depthWrite: false, blending: T.AdditiveBlending,
      }))
      glow.position.copy(mesh.position)
      glow.scale.setScalar(scale * (passed ? 2.8 : 2.2))
      track.add(glow)
      starRecs.push({ mesh, glow: glow as T.Sprite, base: scale, phase: Math.random() * 6.28, passed })

      // 细连线到轨道平面，增强立体层次
      if (passed) {
        const lg = new T.BufferGeometry().setFromPoints([
          mesh.position.clone(),
          new T.Vector3(mesh.position.x, 0, mesh.position.z),
        ])
        track.add(new T.Line(lg, new T.LineBasicMaterial({ color: stColor, transparent: true, opacity: 0.28 })))
      }
    })
  })

  // ---------- 交互 ----------
  let dragging = false, pointerInside = false
  let lastX = 0, lastY = 0, velY = 0, targetZ = 16
  const raycaster = new T.Raycaster()
  const ndc = new T.Vector2()

  function onDown(e: PointerEvent) {
    dragging = true; lastX = e.clientX; lastY = e.clientY
    el.setPointerCapture?.(e.pointerId)
  }
  function onMove(e: PointerEvent) {
    if (dragging) {
      const dx = e.clientX - lastX, dy = e.clientY - lastY
      lastX = e.clientX; lastY = e.clientY
      system.rotation.y += dx * 0.005
      system.rotation.x = Math.max(-0.5, Math.min(0.5, system.rotation.x + dy * 0.003))
      velY = dx * 0.005
      hover.value = null
      return
    }
    const rect = renderer.domElement.getBoundingClientRect()
    ndc.x = ((e.clientX - rect.left) / rect.width) * 2 - 1
    ndc.y = -((e.clientY - rect.top) / rect.height) * 2 + 1
    raycaster.setFromCamera(ndc, camera)
    const hits = raycaster.intersectObjects(starMeshes, false)
    if (hits.length) {
      const m = meta.get(hits[0].object as T.Mesh)
      if (m) hover.value = { x: e.clientX - rect.left + 14, y: e.clientY - rect.top + 12, star: m }
    } else hover.value = null
  }
  function onUp(e: PointerEvent) { dragging = false; el.releasePointerCapture?.(e.pointerId) }
  function onWheel(e: WheelEvent) { e.preventDefault(); targetZ = Math.max(9, Math.min(30, targetZ + e.deltaY * 0.018)) }
  function onLeave() { hover.value = null; pointerInside = false }
  function onEnter() { pointerInside = true }

  renderer.domElement.addEventListener('pointerdown', onDown)
  renderer.domElement.addEventListener('pointermove', onMove)
  renderer.domElement.addEventListener('pointerup', onUp)
  renderer.domElement.addEventListener('pointerenter', onEnter)
  renderer.domElement.addEventListener('pointerleave', onLeave)
  renderer.domElement.addEventListener('wheel', onWheel, { passive: false })

  const ro = new ResizeObserver(() => {
    if (!W() || !H()) return
    camera.aspect = W() / H()
    camera.updateProjectionMatrix()
    renderer.setSize(W(), H())
  })
  ro.observe(el)

  // ---------- 动画 ----------
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  let t = 0
  function tick() {
    t += 0.016
    if (!dragging) {
      system.rotation.y += (reduceMotion || pointerInside) ? 0 : 0.0018 + Math.abs(velY)
      velY *= 0.94
      system.rotation.x += (0 - system.rotation.x) * 0.012
    }
    camera.position.z += (targetZ - camera.position.z) * 0.08

    core.rotation.y += reduceMotion ? 0 : 0.0035
    core.rotation.x = Math.sin(t * 0.25) * 0.12
    eqRing.rotation.z += reduceMotion ? 0 : 0.0015
    coreGlow.material.opacity = 0.42 + Math.sin(t * 1.2) * 0.08

    for (const r of starRecs) {
      const tw = r.passed ? 0.9 + Math.sin(t * 2 + r.phase) * 0.1 : 0.96 + Math.sin(t * 1.1 + r.phase) * 0.04
      r.mesh.scale.setScalar(r.base * tw)
      r.mesh.rotation.y += 0.004
      if (r.glow) r.glow.material.opacity = (r.passed ? 0.72 : 0.32) + Math.sin(t * 1.6 + r.phase) * 0.12
    }
    // tracks 不自转，保持轨道稳定朝向

    renderer.render(scene, camera)
    raf = requestAnimationFrame(tick)
  }
  raf = requestAnimationFrame(tick)
  ready.value = true

  dispose = () => {
    cancelAnimationFrame(raf)
    ro.disconnect()
    renderer.domElement.removeEventListener('pointerdown', onDown)
    renderer.domElement.removeEventListener('pointermove', onMove)
    renderer.domElement.removeEventListener('pointerup', onUp)
    renderer.domElement.removeEventListener('pointerenter', onEnter)
    renderer.domElement.removeEventListener('pointerleave', onLeave)
    renderer.domElement.removeEventListener('wheel', onWheel)
    scene.traverse((o: any) => {
      o.geometry?.dispose?.()
      if (Array.isArray(o.material)) o.material.forEach((m: any) => { m.map?.dispose?.(); m.dispose?.() })
      else o.material?.dispose?.()
    })
    nebulaTex.dispose()
    coreGlow.material.map?.dispose()
    for (const r of starRecs) r.glow?.material?.map?.dispose()
    renderer.dispose()
    renderer.domElement.remove()
  }
})

onBeforeUnmount(() => { dispose?.(); dispose = null })
</script>

<style scoped>
.gsky-wrap {
  position: relative; height: 520px; border-radius: 18px; overflow: hidden;
  background: radial-gradient(130% 110% at 50% 34%, #0d2a3c 0%, #071726 48%, #040b15 100%);
  border: 1px solid rgba(94, 234, 212, 0.16); cursor: grab; touch-action: none;
}
.gsky-wrap:active { cursor: grabbing; }
.gsky-wrap::after {
  content: ''; position: absolute; inset: 0; pointer-events: none; z-index: 1;
  background: radial-gradient(90% 75% at 50% 45%, transparent 58%, rgba(2, 8, 16, 0.28) 100%);
}
.gsky-panel {
  position: absolute; z-index: 3; pointer-events: none;
  background: rgba(5, 16, 28, 0.62); backdrop-filter: blur(10px);
  border: 1px solid rgba(125, 211, 252, 0.16);
}
.gsky-legend { top: 16px; left: 16px; padding: 13px 16px; border-radius: 13px; min-width: 158px; }
.gsky-title {
  display: flex; align-items: center; gap: 7px; font-size: 13px; font-weight: 700;
  color: #d8fff2; margin-bottom: 11px; letter-spacing: .4px;
}
.gsky-title span { font-size: 8.5px; font-weight: 600; color: #5c8595; letter-spacing: 1.1px; }
.gsky-title-mark { width: 7px; height: 7px; border-radius: 50%; background: #5eead4; box-shadow: 0 0 10px #5eead4; }
.gsky-lg-row { display: flex; align-items: center; gap: 8px; font-size: 12px; color: #cbe5f2; margin-top: 6px; }
.gsky-lg-dot { width: 8px; height: 8px; border-radius: 50%; flex: none; }
.gsky-lg-num { margin-left: auto; color: #7ea6bb; font-variant-numeric: tabular-nums; }
.gsky-state {
  top: 16px; right: 16px; padding: 9px 13px; border-radius: 11px;
  display: flex; gap: 11px; font-size: 11px; color: #9fc0d2;
}
.gsky-state span { display: inline-flex; align-items: center; gap: 5px; }
.gsky-state i { width: 7px; height: 7px; border-radius: 50%; }

.gsky-dock {
  position: absolute; left: 50%; bottom: 16px; transform: translateX(-50%); z-index: 3;
  display: flex; align-items: center; gap: 20px; min-width: 520px;
  padding: 12px 18px; border-radius: 14px;
  background: rgba(5, 16, 28, 0.74); backdrop-filter: blur(12px);
  border: 1px solid rgba(94, 234, 212, 0.2);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.35); pointer-events: none;
}
.dock-num { font-size: 34px; font-weight: 800; color: #eafff8; line-height: 1; letter-spacing: -1px; }
.dock-num small { font-size: 15px; color: #5eead4; margin-left: 1px; }
.dock-side { flex: 1; }
.dock-label { font-size: 11px; color: #8fb8c9; }
.dock-label b { color: #d5f5ea; font-weight: 600; margin-left: 6px; }
.dock-bars { display: flex; gap: 12px; margin-top: 7px; }
.dock-bar-row { flex: 1; display: flex; align-items: center; gap: 6px; font-size: 10px; color: #9fc0d2; }
.dock-bar { flex: 1; height: 4px; border-radius: 3px; background: rgba(148, 180, 200, 0.18); overflow: hidden; }
.dock-bar i { display: block; height: 100%; border-radius: 3px; transition: width .5s ease; }
.dock-bar-row b { color: #cbe5f2; font-variant-numeric: tabular-nums; min-width: 26px; text-align: right; }
.dock-gap {
  font-size: 11px; color: #fbbf24; white-space: nowrap;
  padding: 6px 10px; border-radius: 8px; background: rgba(251, 191, 36, 0.10);
}
.dock-gap b { font-size: 14px; }
.dock-gap.ok { color: #34d399; background: rgba(52, 211, 153, 0.10); }

.gsky-tip {
  position: absolute; bottom: 18px; left: 18px; z-index: 3;
  font-size: 10.5px; color: #6f97aa; pointer-events: none;
}
.gsky-tooltip {
  position: absolute; z-index: 6; pointer-events: none;
  padding: 9px 12px; border-radius: 10px; max-width: 240px;
  background: rgba(6, 20, 34, 0.94); border: 1px solid rgba(94, 234, 212, 0.32);
  box-shadow: 0 10px 28px rgba(0, 0, 0, 0.5);
  display: flex; flex-direction: column; gap: 3px;
}
.gsky-tooltip b { font-size: 13px; color: #ecfffa; }
.gsky-tooltip em { font-style: normal; font-size: 11px; color: #96b8c9; }
.gsky-tooltip span { font-size: 11.5px; font-weight: 700; }
.gsky-tooltip span.st-passed { color: #34d399; }
.gsky-tooltip span.st-failed { color: #f43f5e; }
.gsky-tooltip span.st-retaking, .gsky-tooltip span.st-pending { color: #fbbf24; }
.gsky-tooltip span.st-planned { color: #94a3b8; }
.gsky-loading {
  position: absolute; inset: 0; display: flex; align-items: center; justify-content: center;
  color: #7fb3c5; font-size: 13px; z-index: 4;
}
@media (max-width: 1200px) {
  .gsky-dock { min-width: unset; width: calc(100% - 32px); gap: 12px; }
  .gsky-state { display: none; }
}
</style>
