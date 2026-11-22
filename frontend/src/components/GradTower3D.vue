<template>
  <div ref="wrap" class="gtw-wrap">
    <!-- 左上：标题 + 类别图例 -->
    <div class="gtw-panel gtw-legend">
      <div class="gtw-title"><i class="gtw-title-mark" />学分塔 <span>CREDIT TOWER</span></div>
      <div v-for="c in cats" :key="c.key" class="gtw-lg-row">
        <i class="gtw-lg-dot" :style="{ background: cssColor(c.key), boxShadow: '0 0 9px ' + cssColor(c.key) }" />
        <b>{{ c.key }}</b>
        <span class="gtw-lg-num">{{ Math.min(c.earned || 0, c.required || 0) }}/{{ c.required }}</span>
      </div>
    </div>

    <!-- 右上：状态图例 -->
    <div class="gtw-panel gtw-state">
      <span><i style="background:#34d399;box-shadow:0 0 8px #34d399" />已通过</span>
      <span><i style="background:#f43f5e;box-shadow:0 0 8px #f43f5e" />未通过</span>
      <span><i style="background:#fbbf24;box-shadow:0 0 8px #fbbf24" />在修</span>
      <span><i style="background:#475569" />未修</span>
    </div>

    <!-- 底部进度坞 -->
    <div class="gtw-dock">
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
        <template v-else>全部达标 · 可提交毕业申请</template>
      </div>
    </div>

    <!-- 操作提示 -->
    <div class="gtw-tip">拖拽旋转 · 滚轮缩放 · 点击砖块查看课程</div>

    <!-- 点击砖块：课程详情卡 -->
    <transition name="gtw-pop">
      <div v-if="selected" class="gtw-detail">
        <button class="gtw-detail-close" @click.stop="selected = null">×</button>
        <b>{{ selected.name }}</b>
        <em>{{ selected.group }} · {{ selected.credit }} 学分 · {{ semesterTitle(selected.semesterNo) }}</em>
        <div class="gtw-detail-grid">
          <span>状态</span><b :class="'st-' + selected.status">{{ statusText(selected) }}</b>
          <span>成绩</span><b>{{ selected.score != null ? selected.score + ' 分' : '—' }}</b>
        </div>
      </div>
    </transition>

    <!-- 悬停提示 -->
    <div v-if="hover" class="gtw-tooltip" :style="{ left: hover.x + 'px', top: hover.y + 'px' }">
      <b>{{ hover.star.name }}</b>
      <em>{{ hover.star.group }} · {{ hover.star.credit }} 学分 · {{ semesterTitle(hover.star.semesterNo) }}</em>
      <span :class="'st-' + hover.star.status">{{ statusText(hover.star) }}</span>
    </div>
    <div v-if="!ready" class="gtw-loading">学分塔生成中…</div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import type * as ThreeNS from 'three'
import { semesterTitle } from '@/utils/semester'

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
const selected = ref<Star | null>(null)

const HEX: Record<string, string> = { '必修': '#2dd4bf', '公共选修': '#fbbf24', '专业选修': '#60a5fa' }
const TINT: Record<string, number> = { '必修': 0x14b8a6, '公共选修': 0xf59e0b, '专业选修': 0x38bdf8 }
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

function glowTexture(T: typeof import('three'), hex: number) {
  const cv = document.createElement('canvas')
  cv.width = cv.height = 128
  const ctx = cv.getContext('2d')!
  const c = '#' + hex.toString(16).padStart(6, '0')
  const g = ctx.createRadialGradient(64, 64, 0, 64, 64, 64)
  g.addColorStop(0, 'rgba(255,255,255,1)')
  g.addColorStop(0.12, c)
  g.addColorStop(0.40, c + '55')
  g.addColorStop(1, 'rgba(0,0,0,0)')
  ctx.fillStyle = g
  ctx.fillRect(0, 0, 128, 128)
  const tex = new T.CanvasTexture(cv)
  tex.needsUpdate = true
  return tex
}

function textTexture(T: typeof import('three'), text: string, sub: string) {
  const cv = document.createElement('canvas')
  cv.width = 512; cv.height = 128
  const ctx = cv.getContext('2d')!
  ctx.fillStyle = 'rgba(8,20,32,0.78)'
  ctx.fillRect(0, 30, 512, 66)
  ctx.strokeStyle = 'rgba(251,191,36,0.55)'
  ctx.lineWidth = 2.5
  ctx.strokeRect(0, 30, 512, 66)
  ctx.fillStyle = '#ffe9b8'
  ctx.font = '700 34px system-ui, sans-serif'
  ctx.textAlign = 'center'; ctx.textBaseline = 'middle'
  ctx.fillText(text, 256, 62)
  if (sub) {
    ctx.fillStyle = '#c9d8e8'
    ctx.font = '600 21px system-ui, sans-serif'
    ctx.fillText(sub, 256, 100)
  }
  const tex = new T.CanvasTexture(cv)
  tex.needsUpdate = true
  return tex
}

onMounted(async () => {
  const [T, rbg]: any[] = await Promise.all([
    import('three'),
    import('three/examples/jsm/geometries/RoundedBoxGeometry.js'),
  ])
  const RoundedBoxGeometry = rbg.RoundedBoxGeometry
  const el = wrap.value!
  const W = () => el.clientWidth || 1
  const H = () => el.clientHeight || 1

  const scene = new T.Scene()
  const camera = new T.PerspectiveCamera(42, W() / H(), 0.1, 400)

  const renderer = new T.WebGLRenderer({ antialias: true, alpha: true, powerPreference: 'high-performance' })
  renderer.setPixelRatio(Math.min(2, window.devicePixelRatio))
  renderer.setSize(W(), H())
  renderer.setClearColor(0x000000, 0)
  renderer.outputColorSpace = (T as any).SRGBColorSpace
  renderer.toneMapping = (T as any).ACESFilmicToneMapping
  renderer.toneMappingExposure = 1.7
  el.appendChild(renderer.domElement)

  // 环境反射（水晶质感来源）
  try {
    const envMod: any = await import('three/examples/jsm/environments/RoomEnvironment.js')
    const pmrem = new T.PMREMGenerator(renderer)
    scene.environment = pmrem.fromScene(new envMod.RoomEnvironment(), 0.04).texture
    pmrem.dispose()
  } catch { /* 失败仅损失反射 */ }

  // ---------- 灯光：低强度 HDR + 高曝光（Bot3D 校准口径） ----------
  const key = new T.DirectionalLight(0xfff2e2, 0.55)
  key.position.set(6, 10, 7)
  scene.add(key)
  const rim = new T.DirectionalLight(0x7fc4ff, 1.0)
  rim.position.set(-7, 5, -6)
  scene.add(rim)
  const fill = new T.DirectionalLight(0xdfe9ff, 0.2)
  fill.position.set(-3, 2, 6)
  scene.add(fill)
  scene.add(new T.AmbientLight(0xffffff, 0.1))
  const spot = new T.PointLight(0x5eead4, 1.5, 40)
  spot.position.set(0, 8, 3)
  scene.add(spot)

  // ---------- 远景星野（夜空纵深） ----------
  {
    const N = 700
    const pos = new Float32Array(N * 3)
    const col = new Float32Array(N * 3)
    const palette = [0x9fc7e8, 0xd7e9ff, 0x7fa8cc, 0xbcd6f2]
    for (let i = 0; i < N; i++) {
      const r = 40 + Math.random() * 60
      const th = Math.random() * Math.PI * 2
      const ph = Math.acos(2 * Math.random() - 1)
      pos[i * 3] = r * Math.sin(ph) * Math.cos(th)
      pos[i * 3 + 1] = r * Math.cos(ph) * 0.7 + 4
      pos[i * 3 + 2] = r * Math.sin(ph) * Math.sin(th)
      const c = new T.Color(palette[(Math.random() * palette.length) | 0])
      col[i * 3] = c.r; col[i * 3 + 1] = c.g; col[i * 3 + 2] = c.b
    }
    const geo = new T.BufferGeometry()
    geo.setAttribute('position', new T.BufferAttribute(pos, 3))
    geo.setAttribute('color', new T.BufferAttribute(col, 3))
    scene.add(new T.Points(geo, new T.PointsMaterial({
      size: 0.3, vertexColors: true, transparent: true, opacity: 0.7,
      sizeAttenuation: true, depthWrite: false,
    })))
  }

  // ---------- 塔体参数：总高 = 毕业要求学分，砖高 = 课程学分 ----------
  const sorted = [...props.stars].sort((a, b) => {
    const sa = a.semesterNo == null ? -1 : a.semesterNo
    const sb = b.semesterNo == null ? -1 : b.semesterNo
    if (sa !== sb) return sa - sb
    const ga = a.group === '必修' ? 0 : 1
    const gb = b.group === '必修' ? 0 : 1
    if (ga !== gb) return ga - gb
    return a.name.localeCompare(b.name, 'zh')
  })
  const totalCredit = sorted.reduce((s, x) => s + (x.credit || 1), 0) || 1
  const REQ_H = (props.required || totalCredit) * 0.125   // 毕业线高度（= 毕业要求学分）
  const UNIT = REQ_H / Math.max(1, props.required || totalCredit)  // 每学分实际高度
  const WATER_Y = (props.earned || 0) * UNIT              // 水位线（已获学分）

  const tower = new T.Group()
  scene.add(tower)

  // ---------- 全息基座 ----------
  const baseGroup = new T.Group()
  baseGroup.position.y = -0.12
  tower.add(baseGroup)
  const baseDisc = new T.Mesh(
    new T.CircleGeometry(2.5, 80),
    new T.MeshBasicMaterial({ color: 0x0c2233, transparent: true, opacity: 0.72, side: (T as any).DoubleSide, depthWrite: false })
  )
  baseDisc.rotation.x = -Math.PI / 2
  baseGroup.add(baseDisc)
  for (const [r, o] of [[2.5, 0.55], [2.1, 0.28], [1.5, 0.16]]) {
    const ring = new T.Mesh(
      new T.TorusGeometry(r, 0.02, 8, 110),
      new T.MeshBasicMaterial({ color: 0x5eead4, transparent: true, opacity: o, blending: (T as any).AdditiveBlending, depthWrite: false })
    )
    ring.rotation.x = Math.PI / 2
    ring.position.y = 0.015 * (r === 2.5 ? 1 : 0)
    baseGroup.add(ring)
  }

  // ---------- 砖块：每门课一块厚实晶体砖 ----------
  const bricks = sorted.map(s => {
    const h = Math.max(0.3, (s.credit || 1) * UNIT)
    return { ...s, h }
  })
  let cursor = 0.1
  for (const b of bricks) { b.y0 = cursor; b.yc = cursor + b.h / 2; cursor += b.h + 0.06 }
  const TOWER_TOP = cursor
  const totalH = cursor

  // 分层收分：越往上越窄（金字塔感）
  const taperAt = (yc: number) => 1 - 0.34 * Math.min(1, Math.max(0, yc) / Math.max(0.001, REQ_H))

  const brickMeshes: T.Mesh[] = []
  const meta = new Map<T.Mesh, Star>()
  const glowRecs: Array<{ glow: T.Sprite; passed: boolean; failed: boolean; phase: number; baseOp: number }> = []
  const glassGeoCache = new Map<string, any>()

  const TINTS: Record<string, number> = {
    '必修': 0x14b8a6, '公共选修': 0xf59e0b, '专业选修': 0x38bdf8,
  }

  for (const b of bricks) {
    const passed = b.status === 'passed'
    const failed = b.status === 'failed'
    const lit = passed || failed || b.status === 'retaking' || b.status === 'pending'
    const w0 = b.group === '必修' ? 3.3 : b.group === '公共选修' ? 2.5 : 2.9
    const w = Math.max(2.0, w0 * taperAt(b.yc))
    const d = w * 0.62
    const geoKey = `${w.toFixed(3)}_${b.h.toFixed(3)}_${d.toFixed(3)}`
    let geo: any = glassGeoCache.get(geoKey)
    if (!geo) {
      geo = new RoundedBoxGeometry(w, b.h, d, 3, Math.min(0.16, b.h * 0.4))
      glassGeoCache.set(geoKey, geo)
    }
    const baseTint = TINTS[b.group] || 0x14b8a6
    const tint = failed ? 0xf43f5e : baseTint

    if (lit) {
      // 已修/在修：实体发光晶体（点亮并实体化）
      const emis = new (T as any).Color(tint).multiplyScalar(failed ? 0.42 : 0.6)
      const mat = new (T as any).MeshPhysicalMaterial({
        color: new (T as any).Color(tint).multiplyScalar(0.62),
        metalness: 0.12, roughness: 0.24,
        clearcoat: 0.8, clearcoatRoughness: 0.18,
        emissive: emis, emissiveIntensity: failed ? 0.85 : passed ? 0.72 : 0.6,
        envMapIntensity: 0.5,
      })
      const mesh = new T.Mesh(geo, mat)
      mesh.position.set(0, b.yc, 0)
      tower.add(mesh)
      brickMeshes.push(mesh)
      meta.set(mesh, b)
      // 亮边描线
      const edges = new T.LineSegments(
        new (T as any).EdgesGeometry(geo),
        new T.LineBasicMaterial({ color: new (T as any).Color(tint).multiplyScalar(1.5), transparent: true, opacity: 0.55 })
      )
      edges.position.copy(mesh.position)
      tower.add(edges)
      // 顶部柔光
      const glow = new (T as any).Sprite(new (T as any).SpriteMaterial({
        map: glowTexture(T, tint), transparent: true,
        opacity: (passed ? 0.34 : failed ? 0.4 : 0.3),
        depthWrite: false, blending: (T as any).AdditiveBlending,
      }))
      glow.position.set(0, b.yc, 0)
      glow.scale.set(w * 1.25, b.h * 2.6, 1)
      tower.add(glow)
      glowRecs.push({ glow, passed, failed, phase: Math.random() * 6.28, baseOp: (passed ? 0.34 : failed ? 0.4 : 0.3) })
    } else {
      // 未修：暗色线框轮廓（半透明暗壳 + 亮描边）
      const shell = new T.Mesh(geo, new (T as any).MeshStandardMaterial({
        color: 0x0b1320, metalness: 0.35, roughness: 0.5,
        transparent: true, opacity: 0.4, depthWrite: false,
      }))
      shell.position.set(0, b.yc, 0)
      tower.add(shell)
      const edges = new T.LineSegments(
        new (T as any).EdgesGeometry(geo),
        new T.LineBasicMaterial({ color: 0x54697d, transparent: true, opacity: 0.75 })
      )
      edges.position.copy(shell.position)
      tower.add(edges)
      brickMeshes.push(shell)
      meta.set(shell, b)
    }
  }

  // ---------- 水位线：已获学分高度（金色环 + 标签） ----------
  const waterGroup = new T.Group()
  waterGroup.position.y = WATER_Y + 0.04
  tower.add(waterGroup)
  const waterRing = new T.Mesh(
    new T.TorusGeometry(2.62, 0.028, 8, 110),
    new T.MeshBasicMaterial({ color: 0xfbbf24, transparent: true, opacity: 0.9, blending: (T as any).AdditiveBlending, depthWrite: false })
  )
  waterRing.rotation.x = Math.PI / 2
  waterGroup.add(waterRing)
  const waterLabel = new T.Sprite(new (T as any).SpriteMaterial({
    map: textTexture(T, `${props.earned} 学分`, '当前水位 · 已获'), transparent: true, depthWrite: false,
  }))
  waterLabel.scale.set(2.5, 0.62, 1)
  waterLabel.position.set(0, 0.42, 0)
  waterGroup.add(waterLabel)

  // ---------- 毕业线：金色细环（塔的终点高度） ----------
  const gradRing = new T.Mesh(
    new T.TorusGeometry(2.75, 0.02, 8, 110),
    new T.MeshBasicMaterial({ color: 0xfbbf24, transparent: true, opacity: 0.5, blending: (T as any).AdditiveBlending, depthWrite: false })
  )
  gradRing.rotation.x = Math.PI / 2
  gradRing.position.y = REQ_H + 0.3
  tower.add(gradRing)

  // ---------- 塔顶学士帽（终点） ----------
  const capGroup = new T.Group()
  capGroup.position.y = REQ_H + 0.85
  tower.add(capGroup)
  const board = new T.Mesh(
    new T.BoxGeometry(1.05, 0.06, 1.05),
    new T.MeshStandardMaterial({ color: 0x14141c, metalness: 0.65, roughness: 0.28 })
  )
  board.rotation.y = Math.PI / 4
  capGroup.add(board)
  const dome = new T.Mesh(
    new T.CylinderGeometry(0.3, 0.34, 0.24, 20),
    new T.MeshStandardMaterial({ color: 0x1c1c26, metalness: 0.6, roughness: 0.3 })
  )
  dome.position.y = 0.15
  capGroup.add(dome)
  const knob = new T.Mesh(
    new T.SphereGeometry(0.05, 10, 8),
    new T.MeshBasicMaterial({ color: 0xffd98a })
  )
  knob.position.set(0, 0.32, 0)
  capGroup.add(knob)
  const tassel = new T.Mesh(
    new T.CylinderGeometry(0.009, 0.009, 0.46, 6),
    new T.MeshBasicMaterial({ color: 0xffc75e })
  )
  tassel.position.set(0.55, -0.06, 0)
  capGroup.add(tassel)
  const tasselBall = new T.Mesh(
    new T.SphereGeometry(0.055, 10, 8),
    new T.MeshBasicMaterial({ color: 0xffc75e })
  )
  tasselBall.position.set(0.55, -0.32, 0)
  capGroup.add(tasselBall)
  const capGlow = new T.Sprite(new T.SpriteMaterial({
    map: glowTexture(T, 0xffc75e), transparent: true, opacity: 0.6,
    depthWrite: false, blending: T.AdditiveBlending,
  }))
  capGlow.scale.setScalar(1.7)
  capGroup.add(capGlow)

  // ---------- 悬浮微尘（星空氛围） ----------
  {
    const N = 260
    const pos = new Float32Array(N * 3)
    for (let i = 0; i < N; i++) {
      const r = 2.4 + Math.random() * 4.5
      const th = Math.random() * Math.PI * 2
      pos[i * 3] = Math.cos(th) * r
      pos[i * 3 + 1] = -0.3 + Math.random() * (REQ_H + 2)
      pos[i * 3 + 2] = Math.sin(th) * r
    }
    const geo = new T.BufferGeometry()
    geo.setAttribute('position', new T.BufferAttribute(pos, 3))
    scene.add(new T.Points(geo, new T.PointsMaterial({
      color: 0x9fc7e8, size: 0.04, transparent: true, opacity: 0.62,
      sizeAttenuation: true, depthWrite: false,
    })))
  }

  // ---------- 相机：缓慢环绕 + 拖拽 + 滚轮 ----------
  let angle = 0.55
  let dist = 24
  let camH = REQ_H * 0.48
  let dragging = false
  let moved = 0
  let lastX = 0, lastY = 0
  const raycaster = new T.Raycaster()
  const ndc = new T.Vector2()

  function applyCamera() {
    camera.position.set(Math.sin(angle) * dist, camH, Math.cos(angle) * dist)
    camera.lookAt(0, REQ_H * 0.42, 0)
  }
  applyCamera()

  function onDown(e: PointerEvent) {
    dragging = true; moved = 0; lastX = e.clientX; lastY = e.clientY
    el.setPointerCapture?.(e.pointerId)
  }
  function onMove(e: PointerEvent) {
    if (dragging) {
      const dx = e.clientX - lastX, dy = e.clientY - lastY
      lastX = e.clientX; lastY = e.clientY
      moved += Math.abs(dx) + Math.abs(dy)
      angle += dx * 0.006
      camH = Math.max(0.4, Math.min(REQ_H * 0.75, camH + dy * 0.015))
      applyCamera()
      hover.value = null
      return
    }
    const rect = renderer.domElement.getBoundingClientRect()
    ndc.x = ((e.clientX - rect.left) / rect.width) * 2 - 1
    ndc.y = -((e.clientY - rect.top) / rect.height) * 2 + 1
    raycaster.setFromCamera(ndc, camera)
    const hits = raycaster.intersectObjects(brickMeshes, false)
    const first = hits.find(h => h.object.visible)
    if (first) {
      const m = meta.get(first.object as T.Mesh)
      if (m) hover.value = { x: e.clientX - rect.left + 14, y: e.clientY - rect.top + 12, star: m }
    } else hover.value = null
  }
  function onUp(e: PointerEvent) {
    dragging = false
    el.releasePointerCapture?.(e.pointerId)
    if (moved < 6) {
      const rect = renderer.domElement.getBoundingClientRect()
      ndc.x = ((e.clientX - rect.left) / rect.width) * 2 - 1
      ndc.y = -((e.clientY - rect.top) / rect.height) * 2 + 1
      raycaster.setFromCamera(ndc, camera)
      const hits = raycaster.intersectObjects(brickMeshes, false)
      if (hits.length) {
        const m = meta.get(hits[0].object as T.Mesh)
        if (m) { selected.value = m; return }
      }
      selected.value = null
    }
  }
  function onWheel(e: WheelEvent) { e.preventDefault(); dist = Math.max(6, Math.min(26, dist + e.deltaY * 0.014)); applyCamera() }

  renderer.domElement.addEventListener('pointerdown', onDown)
  renderer.domElement.addEventListener('pointermove', onMove)
  renderer.domElement.addEventListener('pointerup', onUp)
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
    if (!dragging && !reduceMotion) angle += 0.0026
    applyCamera()
    waterRing.material.opacity = 0.62 + Math.sin(t * 2.1) * 0.28
    if (!reduceMotion) {
      capGroup.position.y = REQ_H + 0.85 + Math.sin(t * 1.25) * 0.08
      capGroup.rotation.y = Math.sin(t * 0.55) * 0.22
      tassel.rotation.x = Math.sin(t * 1.7) * 0.15
    }
    for (const g of glowRecs) {
      g.glow.material.opacity = g.baseOp + Math.sin(t * 1.9 + g.phase) * (g.failed ? 0.14 : 0.08)
    }
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
    renderer.domElement.removeEventListener('wheel', onWheel)
    scene.traverse((o: any) => {
      o.geometry?.dispose?.()
      if (Array.isArray(o.material)) o.material.forEach((m: any) => { m.map?.dispose?.(); m.dispose?.() })
      else { o.material?.map?.dispose?.(); o.material?.dispose?.() }
    })
    scene.environment?.dispose?.()
    renderer.dispose()
    renderer.domElement.remove()
  }
})

onBeforeUnmount(() => { dispose?.(); dispose = null })
</script>

<style scoped>
.gtw-wrap {
  position: relative; height: 540px; border-radius: 18px; overflow: hidden;
  background: radial-gradient(120% 100% at 50% 20%, #0e2436 0%, #081726 45%, #030a12 100%);
  border: 1px solid rgba(94, 234, 212, 0.16); cursor: grab; touch-action: none;
}
.gtw-wrap:active { cursor: grabbing; }
.gtw-wrap::after {
  content: ''; position: absolute; inset: 0; pointer-events: none; z-index: 1;
  background: radial-gradient(85% 70% at 50% 42%, transparent 55%, rgba(2, 8, 16, 0.42) 100%);
}
.gtw-panel {
  position: absolute; z-index: 3; pointer-events: none;
  background: rgba(5, 16, 28, 0.62); backdrop-filter: blur(10px);
  border: 1px solid rgba(125, 211, 252, 0.16);
}
.gtw-legend { top: 16px; left: 16px; padding: 13px 16px; border-radius: 13px; min-width: 158px; }
.gtw-title {
  display: flex; align-items: center; gap: 7px; font-size: 13px; font-weight: 700;
  color: #d8fff2; margin-bottom: 11px; letter-spacing: .4px;
}
.gtw-title span { font-size: 8.5px; font-weight: 600; color: #5c8595; letter-spacing: 1.1px; }
.gtw-title-mark { width: 7px; height: 7px; border-radius: 50%; background: #5eead4; box-shadow: 0 0 10px #5eead4; }
.gtw-lg-row { display: flex; align-items: center; gap: 8px; font-size: 12px; color: #cbe5f2; margin-top: 6px; }
.gtw-lg-dot { width: 8px; height: 8px; border-radius: 50%; flex: none; }
.gtw-lg-num { margin-left: auto; color: #7ea6bb; font-variant-numeric: tabular-nums; }
.gtw-state {
  top: 16px; right: 16px; padding: 9px 13px; border-radius: 11px;
  display: flex; gap: 11px; font-size: 11px; color: #9fc0d2;
}
.gtw-state span { display: inline-flex; align-items: center; gap: 5px; }
.gtw-state i { width: 7px; height: 7px; border-radius: 50%; }

.gtw-dock {
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

.gtw-tip {
  position: absolute; bottom: 18px; left: 18px; z-index: 3;
  font-size: 10.5px; color: #6f97aa; pointer-events: none;
}
.gtw-tooltip {
  position: absolute; z-index: 6; pointer-events: none;
  padding: 9px 12px; border-radius: 10px; max-width: 240px;
  background: rgba(6, 20, 34, 0.94); border: 1px solid rgba(94, 234, 212, 0.32);
  box-shadow: 0 10px 28px rgba(0, 0, 0, 0.5);
  display: flex; flex-direction: column; gap: 3px;
}
.gtw-tooltip b { font-size: 13px; color: #ecfffa; }
.gtw-tooltip em { font-style: normal; font-size: 11px; color: #96b8c9; }
.gtw-tooltip span { font-size: 11.5px; font-weight: 700; }
.gtw-tooltip span.st-passed { color: #34d399; }
.gtw-tooltip span.st-failed { color: #f43f5e; }
.gtw-tooltip span.st-retaking, .gtw-tooltip span.st-pending { color: #fbbf24; }
.gtw-tooltip span.st-planned { color: #94a3b8; }
.gtw-loading {
  position: absolute; inset: 0; display: flex; align-items: center; justify-content: center;
  color: #7fb3c5; font-size: 13px; z-index: 4;
}

/* 点击砖块：课程详情卡 */
.gtw-detail {
  position: absolute; right: 16px; top: 64px; z-index: 5;
  width: 250px; padding: 14px 16px; border-radius: 13px;
  background: rgba(6, 20, 34, 0.94); border: 1px solid rgba(94, 234, 212, 0.36);
  box-shadow: 0 14px 34px rgba(0, 0, 0, 0.5);
  display: flex; flex-direction: column; gap: 7px;
}
.gtw-detail b { font-size: 14px; color: #ecfffa; padding-right: 20px; }
.gtw-detail em { font-style: normal; font-size: 11px; color: #96b8c9; }
.gtw-detail-grid { display: grid; grid-template-columns: auto 1fr; gap: 4px 12px; font-size: 12px; }
.gtw-detail-grid span { color: #7fa6bb; }
.gtw-detail-grid b { color: #d5f5ea; font-weight: 600; }
.gtw-detail-grid b.st-passed { color: #34d399; }
.gtw-detail-grid b.st-failed { color: #f43f5e; }
.gtw-detail-grid b.st-retaking, .gtw-detail-grid b.st-pending { color: #fbbf24; }
.gtw-detail-grid b.st-planned { color: #94a3b8; }
.gtw-detail-close {
  position: absolute; top: 8px; right: 10px; width: 24px; height: 24px;
  border: none; border-radius: 7px; background: rgba(148, 180, 200, 0.14);
  color: #cbe5f2; font-size: 15px; cursor: pointer; line-height: 1;
}
.gtw-detail-close:hover { background: rgba(148, 180, 200, 0.26); }
.gtw-pop-enter-active, .gtw-pop-leave-active { transition: opacity .18s ease, transform .18s ease; }
.gtw-pop-enter-from, .gtw-pop-leave-to { opacity: 0; transform: translateY(-6px); }

@media (max-width: 1200px) {
  .gtw-dock { min-width: unset; width: calc(100% - 32px); gap: 12px; }
  .gtw-state { display: none; }
  .gtw-detail { width: calc(100% - 32px); right: 16px; }
}
</style>
