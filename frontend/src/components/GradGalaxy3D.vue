<template>
  <div ref="wrap" class="ggx-wrap">
    <!-- 左上：总进度 HUD -->
    <div class="ggx-hud">
      <div class="ggx-hud-title"><i class="ggx-mark" />毕业星图 <span>GRADUATION ATLAS</span></div>
      <div class="ggx-hud-rows">
        <div class="ggx-hud-row"><span>总进度</span><b>{{ percent }}<small>%</small></b></div>
        <div class="ggx-hud-row"><span>已获学分</span><b>{{ earned }}<small> / {{ required }}</small></b></div>
        <div class="ggx-hud-row" :class="{ ok: gap <= 0 }"><span>还差</span><b>{{ gap }}<small> 学分</small></b></div>
      </div>
      <div class="ggx-hud-foot">
        <template v-if="filter === 'all'">{{ allStars.length }} 门课程 · {{ semesters.length }} 个学期</template>
        <template v-else>筛选：{{ filterLabel }} <b>{{ filteredCount }}</b> 门 · 再点「全部」还原</template>
      </div>
    </div>

    <!-- 状态筛选：一键只显示某类课程星 -->
    <div class="ggx-filter">
      <button v-for="f in filters" :key="f.key" :class="{ on: filter === f.key }" @click="setFilter(f.key)">
        <i :style="{ background: f.color }" />{{ f.label }}
      </button>
    </div>

    <!-- 右上：课程状态统计（一眼看清有多少门过/挂/在修） -->
    <div class="ggx-stats">
      <div class="ggx-stats-head">课程状态 <span>共 {{ allStars.length }} 门</span></div>
      <div class="ggx-stat-row" :class="{ on: filter === 'passed' }" @click="setFilter('passed')"><i style="background:#34d399" /><span>已通过</span><b>{{ stat.passed }}</b></div>
      <div class="ggx-stat-row" :class="{ on: filter === 'failed' }" @click="setFilter('failed')"><i style="background:#fb7185" /><span>未通过</span><b>{{ stat.failed }}</b></div>
      <div class="ggx-stat-row" :class="{ on: filter === 'retaking' }" @click="setFilter('retaking')"><i style="background:#fb923c" /><span>重修中</span><b>{{ stat.retaking }}</b></div>
      <div class="ggx-stat-row" :class="{ on: filter === 'pending' }" @click="setFilter('pending')"><i style="background:#fde68a" /><span>在修</span><b>{{ stat.pending }}</b></div>
      <div class="ggx-stat-row" :class="{ on: filter === 'planned' }" @click="setFilter('planned')"><i style="background:#64748b" /><span>未修</span><b>{{ stat.planned }}</b></div>
    </div>

    <!-- 底部：读图说明 -->
    <div class="ggx-legend">
      <span><i style="background:#34d399" />绿=已过</span>
      <span><i style="background:#fb7185" />红=未过(闪烁)</span>
      <span><i style="background:#fde68a" />黄=在修</span>
      <span><i style="background:#64748b" />灰=未修</span>
      <em>点学期星球看课程清单 · 点课程光点看详情 · 中心弧=总进度</em>
    </div>

    <!-- 悬停提示（详情卡打开时不显示，避免重叠） -->
    <div v-if="hover && !selected" class="ggx-tooltip" :style="tipStyle">
      <b>{{ hover.star.name }}</b>
      <em>{{ hover.star.group }} · {{ hover.star.credit }} 学分 · {{ semesterTitle(hover.star.semesterNo) }}</em>
      <span :class="'st-' + hover.star.status">{{ statusText(hover.star) }}</span>
    </div>

    <!-- 聚焦学期：下钻到该学期的课程清单 -->
    <transition name="ggx-pop">
      <div v-if="focusSem != null" class="ggx-focus">
        <div class="ggx-focus-head">
          <b>{{ focusSemName }}</b>
          <span>{{ focusSemEarned }}/{{ focusSemCredits }} 学分 · {{ focusCourses.length }} 门</span>
          <button @click="exitFocus">返回全景 ×</button>
        </div>
        <div class="ggx-focus-list">
          <div v-for="c in focusCourses" :key="c.name" class="ggx-focus-item" @click="selected = c">
            <i :style="{ background: statusColor(c.status) }" />
            <span class="gfi-name">{{ c.name }}</span>
            <em>{{ c.credit }}分</em>
            <b :class="'st-' + c.status">{{ statusShort(c.status) }}</b>
          </div>
        </div>
      </div>
    </transition>

    <!-- 点击详情卡 -->
    <transition name="ggx-pop">
      <div v-if="selected" class="ggx-detail">
        <button class="ggx-detail-close" @click.stop="selected = null">×</button>
        <b>{{ selected.name }}</b>
        <em>{{ selected.group }} · {{ selected.credit }} 学分 · {{ semesterTitle(selected.semesterNo) }}</em>
        <div class="ggx-detail-grid">
          <span>状态</span><b :class="'st-' + selected.status">{{ statusText(selected) }}</b>
          <span>成绩</span><b>{{ selected.score != null ? selected.score + ' 分' : '—' }}</b>
        </div>
        <div v-if="selected.score != null" class="ggx-score-bar"><i :style="{ width: Math.min(100, selected.score) + '%', background: selected.score >= 60 ? '#34d399' : '#fb7185' }" /></div>
      </div>
    </transition>

    <div v-if="!ready" class="ggx-loading">{{ failed ? '当前环境不支持 3D 星图' : '星图生成中…' }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { semesterTitle } from '@/utils/semester'

type SemPlanet = {
  semesterNo: number
  label: string
  credits: number
  earned: number
  status: 'passed' | 'failed' | 'retaking' | 'pending' | 'future'
  courses: Array<{ name: string; credit: number; category: string; score: number | null; status: string }>
}
type Star = { name: string; group: string; credit: number; semesterNo: number | null; status: string; score: number | null }

const props = defineProps<{
  semesters: SemPlanet[]
  stars?: Star[]
  percent: number
  earned: number
  required: number
  gap: number
  currentSemesterNo: number
}>()

/** 课程星数据：优先用父组件传入的成绩档案 ∪ 培养方案；未传时从学期行星降级组装 */
const allStars = computed<Star[]>(() => {
  if (props.stars?.length) return props.stars
  const out: Star[] = []
  for (const s of props.semesters || []) {
    for (const c of s.courses || []) {
      out.push({
        name: c.name,
        group: (c as any).group || (String(c.category || '').includes('选修') ? '公共选修' : '必修'),
        credit: Number(c.credit) || 1,
        semesterNo: s.semesterNo,
        status: c.status,
        score: c.score,
      })
    }
  }
  return out
})

const wrap = ref<HTMLElement | null>(null)
const ready = ref(false)
const failed = ref(false)
const hover = ref<{ x: number; y: number; star: Star } | null>(null)
const selected = ref<Star | null>(null)
const boxSize = ref({ w: 900, h: 520 })
const focusSem = ref<number | null>(null)
const filter = ref<string>('all')
const filters = [
  { key: 'all', label: '全部', color: '#5eead4' },
  { key: 'passed', label: '已过', color: '#34d399' },
  { key: 'failed', label: '未过', color: '#fb7185' },
  { key: 'pending', label: '在修', color: '#fde68a' },
  { key: 'planned', label: '未修', color: '#64748b' },
]
let applyFilterFn: ((f: string) => void) | null = null
function setFilter(f: string) { filter.value = f; applyFilterFn?.(f) }

const filterLabel = computed(() => filters.find(f => f.key === filter.value)?.label || '')
const filteredCount = computed(() => {
  if (filter.value === 'all') return allStars.value.length
  return allStars.value.filter(s => {
    const n = s.status === 'planned' || s.status === 'future' ? 'planned' : s.status
    return n === filter.value
  }).length
})

/** 聚焦学期的课程清单 */
const focusCourses = computed<Star[]>(() => focusSem.value == null ? [] : allStars.value.filter(s => Number(s.semesterNo) === focusSem.value))
const focusSemName = computed(() => semesterTitle(focusSem.value))
const focusSemCredits = computed(() => props.semesters.find(s => s.semesterNo === focusSem.value)?.credits || 0)
const focusSemEarned = computed(() => props.semesters.find(s => s.semesterNo === focusSem.value)?.earned || 0)

function statusColor(s: string) {
  return ({ passed: '#34d399', failed: '#fb7185', retaking: '#fb923c', pending: '#fde68a' } as Record<string, string>)[s] || '#64748b'
}
function statusShort(s: string) {
  return ({ passed: '已通过', failed: '未通过', retaking: '重修中', pending: '在修', planned: '未修' } as Record<string, string>)[s] || '未修'
}
let exitFocusFn: (() => void) | null = null
function exitFocus() { exitFocusFn?.(); focusSem.value = null; selected.value = null }

/** 课程状态统计：让数字直接可读，不必数星星 */
const stat = computed(() => {
  const s = { passed: 0, failed: 0, retaking: 0, pending: 0, planned: 0 }
  for (const x of allStars.value) {
    if (x.status === 'passed') s.passed++
    else if (x.status === 'failed') s.failed++
    else if (x.status === 'retaking') s.retaking++
    else if (x.status === 'pending') s.pending++
    else s.planned++
  }
  return s
})

/** tooltip 靠近右/下边缘时自动翻转，不溢出画布 */
const tipStyle = computed(() => {
  if (!hover.value) return {}
  const { w, h } = boxSize.value
  const x = hover.value.x + 16 + 250 > w ? hover.value.x - 266 : hover.value.x + 16
  const y = hover.value.y + 16 + 90 > h ? hover.value.y - 98 : hover.value.y + 16
  return { left: Math.max(8, x) + 'px', top: Math.max(8, y) + 'px' }
})

function statusText(s: Star) {
  if (s.status === 'passed') return `已通过 · ${s.score} 分`
  if (s.status === 'failed') return `未通过 · ${s.score} 分`
  if (s.status === 'retaking') return '重修中'
  if (s.status === 'pending') return '在修 · 待出成绩'
  return '未修 · 培养方案计划课程'
}

let dispose: (() => void) | null = null
let raf = 0

/** 径向光斑贴图：星体辉光 */
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

/** 值噪声 + 分形叠加，用于程序化行星表面 */
function noise2(x: number, y: number, seed: number) {
  const n = Math.sin(x * 127.1 + y * 311.7 + seed * 74.7) * 43758.5453
  return n - Math.floor(n)
}
function smoothNoise(x: number, y: number, seed: number) {
  const ix = Math.floor(x), iy = Math.floor(y)
  const fx = x - ix, fy = y - iy
  const ux = fx * fx * (3 - 2 * fx), uy = fy * fy * (3 - 2 * fy)
  const a = noise2(ix, iy, seed), b = noise2(ix + 1, iy, seed)
  const c = noise2(ix, iy + 1, seed), d = noise2(ix + 1, iy + 1, seed)
  return (a * (1 - ux) + b * ux) * (1 - uy) + (c * (1 - ux) + d * ux) * uy
}
function fbm(x: number, y: number, seed: number) {
  let v = 0, amp = 0.5, f = 1
  for (let i = 0; i < 5; i++) { v += smoothNoise(x * f, y * f, seed + i) * amp; f *= 2; amp *= 0.5 }
  return v
}

/** 程序化行星表面贴图：陆地/海洋/极冠，按学期用不同色系与种子 */
function planetTexture(T: typeof import('three'), seed: number, base: [number, number, number]) {
  const W = 256, H = 128
  const cv = document.createElement('canvas'); cv.width = W; cv.height = H
  const ctx = cv.getContext('2d')!
  const img = ctx.createImageData(W, H)
  for (let y = 0; y < H; y++) {
    for (let x = 0; x < W; x++) {
      const nx = x / W * 6, ny = y / H * 3
      let h = fbm(nx, ny, seed)
      // 纬度：两极偏向亮色（极冠）
      const lat = Math.abs(y / H - 0.5) * 2
      h = h * (1 - lat * 0.35) + lat * 0.22
      const i = (y * W + x) * 4
      const land = h > 0.5
      const shade = land ? 0.75 + (h - 0.5) * 1.4 : 0.45 + h * 0.5
      img.data[i] = Math.min(255, base[0] * shade * (land ? 1 : 0.55))
      img.data[i + 1] = Math.min(255, base[1] * shade * (land ? 1 : 0.62))
      img.data[i + 2] = Math.min(255, base[2] * shade * (land ? 0.85 : 1.05))
      img.data[i + 3] = 255
    }
  }
  ctx.putImageData(img, 0, 0)
  const tex = new T.CanvasTexture(cv)
  tex.colorSpace = T.SRGBColorSpace
  tex.wrapS = T.RepeatWrapping
  tex.needsUpdate = true
  return tex
}

/** 星空背景贴图：星云带 + 细密星点 */
function nebulaTexture(T: typeof import('three')) {
  const W = 1024, H = 512
  const cv = document.createElement('canvas'); cv.width = W; cv.height = H
  const ctx = cv.getContext('2d')!
  ctx.fillStyle = '#050b14'; ctx.fillRect(0, 0, W, H)
  // 两条斜向星云带
  for (const [cx, cy, rx, ry, col] of [
    [280, 210, 360, 90, 'rgba(45,120,150,.20)'],
    [740, 330, 420, 110, 'rgba(90,60,140,.18)'],
    [520, 150, 260, 70, 'rgba(20,100,90,.14)'],
  ] as Array<[number, number, number, number, string]>) {
    const g = ctx.createRadialGradient(cx, cy, 0, cx, cy, rx)
    g.addColorStop(0, col); g.addColorStop(1, 'rgba(0,0,0,0)')
    ctx.save(); ctx.translate(cx, cy); ctx.scale(1, ry / rx); ctx.translate(-cx, -cy)
    ctx.fillStyle = g; ctx.fillRect(cx - rx, cy - rx, rx * 2, rx * 2); ctx.restore()
  }
  // 星点：小而密，避免放大后像雪花
  for (let i = 0; i < 2600; i++) {
    const x = Math.random() * W, y = Math.random() * H
    const r = Math.random() * 0.45 + 0.12
    const a = 0.2 + Math.random() * 0.6
    ctx.fillStyle = `rgba(220,240,255,${a})`
    ctx.fillRect(x, y, r, r)
  }
  const tex = new T.CanvasTexture(cv)
  tex.colorSpace = T.SRGBColorSpace
  tex.mapping = T.EquirectangularReflectionMapping
  tex.needsUpdate = true
  return tex
}

onMounted(async () => {
  try {
    const [T, OC, EC, RP, BP, OP, C2, ENV] = await Promise.all([
      import('three'),
      import('three/examples/jsm/controls/OrbitControls.js'),
      import('three/examples/jsm/postprocessing/EffectComposer.js'),
      import('three/examples/jsm/postprocessing/RenderPass.js'),
      import('three/examples/jsm/postprocessing/UnrealBloomPass.js'),
      import('three/examples/jsm/postprocessing/OutputPass.js'),
      import('three/examples/jsm/renderers/CSS2DRenderer.js'),
      import('three/examples/jsm/environments/RoomEnvironment.js'),
    ])
    const el = wrap.value!
    const W = () => el.clientWidth || 1
    const H = () => el.clientHeight || 1

    const scene = new T.Scene()
    const nebula = nebulaTexture(T)
    scene.background = nebula
    scene.fog = new T.FogExp2(0x08131f, 0.006)

    const camera = new T.PerspectiveCamera(46, W() / H(), 0.1, 300)
    const DIST = 24.5
    camera.position.set(DIST * 0.70, DIST * 0.62, DIST * 0.70)
    camera.lookAt(0, 0, 0)

    const renderer = new T.WebGLRenderer({ antialias: true, alpha: true, powerPreference: 'high-performance' })
    renderer.setPixelRatio(Math.min(1.75, window.devicePixelRatio))
    renderer.setSize(W(), H())
    renderer.outputColorSpace = T.SRGBColorSpace
    renderer.toneMapping = T.ACESFilmicToneMapping
    renderer.toneMappingExposure = 1.05
    el.appendChild(renderer.domElement)

    // PMREM 环境反射：让金属/晶体材质有真实的光照层次，而非纯自发光
    const pmrem = new T.PMREMGenerator(renderer)
    const envRT = pmrem.fromScene(new ENV.RoomEnvironment(), 0.04)
    scene.environment = envRT.texture
    scene.environmentIntensity = 0.6

    // CSS2D 标签层（学期名跟随 3D 位置，始终正对相机）
    const labels = new C2.CSS2DRenderer()
    labels.setSize(W(), H())
    labels.domElement.style.position = 'absolute'
    labels.domElement.style.inset = '0'
    labels.domElement.style.pointerEvents = 'none'
    labels.domElement.style.zIndex = '2'
    el.appendChild(labels.domElement)

    const controls = new OC.OrbitControls(camera, renderer.domElement)
    controls.enableDamping = true
    controls.dampingFactor = 0.07
    controls.minDistance = 8
    controls.maxDistance = 52
    controls.minPolarAngle = 0.22
    controls.maxPolarAngle = Math.PI - 0.22
    controls.target.set(0, 0, 0)
    const homeTarget = new T.Vector3(0, 0, 0)
    const homeCam = camera.position.clone()
    let focusGoal: T.Vector3 | null = null
    let focusCamGoal: T.Vector3 | null = null
    // 拖拽/缩放时暂停自动公转，避免"拖不动"的错觉
    let interacting = false
    controls.addEventListener('start', () => { interacting = true })
    controls.addEventListener('end', () => { interacting = false })

    // 泛光后处理：让星体真正"发光"
    const composer = new EC.EffectComposer(renderer)
    composer.addPass(new RP.RenderPass(scene, camera))
    const bloom = new BP.UnrealBloomPass(new T.Vector2(W(), H()), 0.42, 0.4, 0.55)
    composer.addPass(bloom)
    composer.addPass(new OP.OutputPass())

    scene.add(new T.AmbientLight(0xb9d8ff, 0.9))
    const key = new T.DirectionalLight(0xfff4e0, 2.2)   // 主恒星暖光
    key.position.set(8, 14, 10)
    scene.add(key)
    const rim = new T.DirectionalLight(0x4dd0e1, 1.1)   // 冷色轮廓光
    rim.position.set(-10, -6, -8)
    scene.add(rim)

    const disk = new T.Group()
    scene.add(disk)

    // ===== 中心：学位核心（自转多面体 + 双光环 + 光晕） =====
    const core = new T.Group()
    disk.add(core)
    const coreGem = new T.Mesh(
      new T.IcosahedronGeometry(1.02, 2),
      new T.MeshPhysicalMaterial({
        color: 0xffd98a, emissive: 0xc8861a, emissiveIntensity: 0.42,
        roughness: 0.18, metalness: 0.85, clearcoat: 1, clearcoatRoughness: 0.12,
        flatShading: true,
      })
    )
    core.add(coreGem)
    // Fresnel 大气层：边缘散射辉光，比 sprite 光斑更真实
    const atmoMat = new T.ShaderMaterial({
      uniforms: { uColor: { value: new T.Color(0xffc860) } },
      vertexShader: `varying vec3 vN; varying vec3 vP;
        void main(){ vN=normalize(normalMatrix*normal);
        vec4 mv=modelViewMatrix*vec4(position,1.0); vP=mv.xyz;
        gl_Position=projectionMatrix*mv; }`,
      fragmentShader: `uniform vec3 uColor; varying vec3 vN; varying vec3 vP;
        void main(){ float f=pow(1.0-abs(dot(normalize(vN),normalize(-vP))),3.0);
        gl_FragColor=vec4(uColor,f*0.85); }`,
      transparent: true, blending: T.AdditiveBlending, side: T.BackSide, depthWrite: false,
    })
    const coreAtmo = new T.Mesh(new T.SphereGeometry(1.35, 32, 24), atmoMat)
    core.add(coreAtmo)
    const coreGlow = new T.Sprite(new T.SpriteMaterial({
      map: glowTexture(T, 0xffcf70), transparent: true, opacity: 0.72,
      depthWrite: false, blending: T.AdditiveBlending,
    }))
    coreGlow.scale.setScalar(3.0)
    core.add(coreGlow)

    // 进度仪表：正对相机的环形进度条，弧长 = 毕业进度百分比
    // 挂在相机上并置于右下，作为常驻 HUD；不随星系公转、不遮挡中心天体
    const gauge = new T.Group()
    const GAUGE_X = 5.9, GAUGE_Y = -2.9, GAUGE_Z = -12
    gauge.position.set(GAUGE_X, GAUGE_Y, GAUGE_Z)
    camera.add(gauge)
    scene.add(camera)
    const ringR = 1.55
    const progTrack = new T.Mesh(
      new T.TorusGeometry(ringR, 0.05, 10, 120),
      new T.MeshBasicMaterial({ color: 0x173b47, transparent: true, opacity: 0.9 })
    )
    gauge.add(progTrack)
    const pctArc = Math.max(0.001, Math.min(1, props.percent / 100)) * Math.PI * 2
    const progArc = new T.Mesh(
      new T.TorusGeometry(ringR, 0.11, 12, 120, pctArc),
      new T.MeshBasicMaterial({ color: 0x5eead4 })
    )
    progArc.rotation.z = -Math.PI / 2
    gauge.add(progArc)
    // 环端点的光点，强调"当前进度位置"
    const head = new T.Mesh(
      new T.SphereGeometry(0.1, 14, 10),
      new T.MeshBasicMaterial({ color: 0xffffff })
    )
    head.position.set(Math.sin(pctArc) * ringR, -Math.cos(pctArc) * ringR, 0)
    gauge.add(head)

    const ringA = new T.Mesh(
      new T.TorusGeometry(1.62, 0.018, 8, 96),
      new T.MeshBasicMaterial({ color: 0x7fe3c4, transparent: true, opacity: 0.72, side: T.DoubleSide })
    )
    ringA.rotation.x = Math.PI / 2.35
    core.add(ringA)
    const ringB = new T.Mesh(
      new T.TorusGeometry(2.05, 0.012, 8, 96),
      new T.MeshBasicMaterial({ color: 0xf2c879, transparent: true, opacity: 0.42, side: T.DoubleSide })
    )
    ringB.rotation.x = Math.PI / 1.85
    core.add(ringB)
    // 进度数字放在仪表中心，正对相机
    const coreLabel = new C2.CSS2DObject(document.createElement('div'))
    coreLabel.element.className = 'ggx-core-label'
    const coreNum = document.createElement('b')
    coreNum.textContent = `${props.percent}%`
    const coreTxt = document.createElement('small')
    coreTxt.textContent = '毕业进度'
    coreLabel.element.appendChild(coreNum)
    coreLabel.element.appendChild(coreTxt)
    coreLabel.position.set(0, 0, 0.2)
    gauge.add(coreLabel)

    // ===== 学期轨道 + 学期主星 + 课程星 =====
    const SEM_COLOR: Record<string, number> = {
      passed: 0x34d399, failed: 0xfb7185, retaking: 0xfb923c,
      pending: 0xfde68a, future: 0x64748b,
    }
    const STAR_COLOR: Record<string, number> = {
      passed: 0x34d399, failed: 0xfb7185, retaking: 0xfb923c,
      pending: 0xfde68a, planned: 0x64748b, future: 0x64748b,
    }
    // 类别决定星体所在高度层：必修居中、公共选修偏上、专业选修偏下
    const GROUP_Y: Record<string, number> = { '必修': 0, '公共选修': 0.72, '专业选修': -0.72 }
    const ORBIT0 = 4.0, ORBIT_STEP = 1.05
    const semCount = Math.max(1, props.semesters.length)
    const courseMeshes: T.Mesh[] = []
    const semTags: Array<{ no: number; el: HTMLElement }> = []
    const semMeshes: T.Mesh[] = []
    const semData = new Map<T.Mesh, number>()
    const courseData = new Map<T.Mesh, Star>()
    const orbiting: Array<{ mesh: T.Mesh; cx: number; cy: number; cz: number; r: number; angle: number; speed: number }> = []
    const pulsing: Array<{ mesh: T.Mesh; phase: number }> = []   // 未通过/重修中的星体脉冲警示
    const flows: Array<{ mesh: T.Mesh; R: number; tilt: number; angle: number; speed: number }> = [] // 轨道流光

    props.semesters.forEach((sem, si) => {
      const R = ORBIT0 + si * ORBIT_STEP
      const tilt = (si % 2 ? 1 : -1) * 0.018
      const pts: T.Vector3[] = []
      for (let k = 0; k < 160; k++) {
        const a = (k / 160) * Math.PI * 2
        pts.push(new T.Vector3(Math.cos(a) * R, tilt * Math.sin(a) * R, Math.sin(a) * R))
      }
      const isCur = sem.semesterNo === props.currentSemesterNo
      disk.add(new T.LineLoop(
        new T.BufferGeometry().setFromPoints(pts),
        new T.LineBasicMaterial({
          color: isCur ? 0x5eead4 : (sem.status === 'future' ? 0x334155 : 0x456b8c),
          transparent: true, opacity: isCur ? 0.62 : 0.34,
        })
      ))

      // 轨道亮弧：画出「已获学分 / 总学分」的那一段，完成度直接可见
      const ratio = sem.credits > 0 ? Math.max(0, Math.min(1, sem.earned / sem.credits)) : 0
      if (ratio > 0.01) {
        const arcPts: T.Vector3[] = []
        const n = Math.max(8, Math.round(120 * ratio))
        const start = si * 2.39996 + 0.4
        for (let k = 0; k <= n; k++) {
          const a = start + (k / n) * Math.PI * 2 * ratio
          arcPts.push(new T.Vector3(Math.cos(a) * R, tilt * Math.sin(a) * R, Math.sin(a) * R))
        }
        const curve = new T.CatmullRomCurve3(arcPts)
        disk.add(new T.Mesh(
          new T.TubeGeometry(curve, n, 0.045, 6, false),
          new T.MeshBasicMaterial({ color: 0x34d399, transparent: true, opacity: 0.95 })
        ))
      }

      // 轨道流光：沿轨道跑的光点，暗示"修读进程"在推进
      const flow = new T.Mesh(
        new T.SphereGeometry(0.075, 10, 8),
        new T.MeshBasicMaterial({ color: isCur ? 0x7fffe0 : 0x93c5fd, transparent: true, opacity: 0.9 })
      )
      disk.add(flow)
      flows.push({ mesh: flow, R, tilt, angle: si * 0.7, speed: 0.0009 + si * 0.00006 })

      const pColor = SEM_COLOR[sem.status] ?? 0x64748b
      // 按状态取行星基色：通过=蓝绿、挂科=暗红、在修=金黄、未来=灰蓝
      const palettes: Record<string, [number, number, number]> = {
        passed: [70, 200, 165], failed: [210, 80, 100], retaking: [230, 140, 70],
        pending: [235, 200, 110], future: [110, 140, 170],
      }
      const pal = palettes[sem.status] ?? palettes.future!
      const planetTex = planetTexture(T, si + 1, pal)
      const planet = new T.Mesh(
        new T.SphereGeometry(0.34, 40, 28),
        new T.MeshStandardMaterial({
          map: planetTex, color: 0xffffff,
          emissive: pColor, emissiveIntensity: sem.status === 'future' ? 0.05 : 0.16,
          roughness: 0.62, metalness: 0.18,
        })
      )
      // 行星大气层
      const pAtmo = new T.Mesh(
        new T.SphereGeometry(0.40, 28, 20),
        new T.ShaderMaterial({
          uniforms: { uColor: { value: new T.Color(pColor) } },
          vertexShader: `varying vec3 vN; varying vec3 vP;
            void main(){ vN=normalize(normalMatrix*normal); vec4 mv=modelViewMatrix*vec4(position,1.0); vP=mv.xyz; gl_Position=projectionMatrix*mv; }`,
          fragmentShader: `uniform vec3 uColor; varying vec3 vN; varying vec3 vP;
            void main(){ float f=pow(1.0-abs(dot(normalize(vN),normalize(-vP))),2.6); gl_FragColor=vec4(uColor,f*0.7); }`,
          transparent: true, blending: T.AdditiveBlending, side: T.BackSide, depthWrite: false,
        })
      )
      planet.add(pAtmo)
      // 黄金角分布：8 颗学期主星均匀铺满整圈，避免挤在同一象限
      const baseAngle = si * 2.39996 + 0.4
      planet.position.set(Math.cos(baseAngle) * R, tilt * Math.sin(baseAngle) * R, Math.sin(baseAngle) * R)
      disk.add(planet)
      // 透明大球作为点击热区（主星本身太小不好点）
      const hit = new T.Mesh(
        new T.SphereGeometry(0.62, 8, 6),
        new T.MeshBasicMaterial({ visible: false })
      )
      hit.position.copy(planet.position)
      disk.add(hit)
      semMeshes.push(hit)
      semData.set(hit, sem.semesterNo)
      const pGlow = new T.Sprite(new T.SpriteMaterial({
        map: glowTexture(T, pColor), transparent: true, opacity: isCur ? 0.85 : 0.45,
        depthWrite: false, blending: T.AdditiveBlending,
      }))
      pGlow.scale.setScalar(1.5)
      planet.add(pGlow)
      if (isCur) {
        const curRing = new T.Mesh(
          new T.TorusGeometry(0.58, 0.016, 8, 48),
          new T.MeshBasicMaterial({ color: 0xffffff, transparent: true, opacity: 0.9, side: T.DoubleSide })
        )
        curRing.rotation.x = Math.PI / 2.5
        planet.add(curRing)
      }
      const tag = new C2.CSS2DObject(Object.assign(document.createElement('div'), {
        className: 'ggx-sem-label' + (isCur ? ' cur' : ''),
      }))
      const tagMain = document.createElement('b')
      tagMain.textContent = semesterTitle(sem.semesterNo)
      const sub = document.createElement('small')
      const listN = allStars.value.filter((s) => Number(s.semesterNo) === sem.semesterNo).length
      sub.textContent = `${sem.earned}/${sem.credits} 学分 · ${listN} 门`
      tag.element.appendChild(tagMain)
      tag.element.appendChild(sub)
      tag.position.set(0, 0.66, 0)
      planet.add(tag)
      semTags.push({ no: sem.semesterNo, el: tag.element })

      // 课程星贴在所属学期主星周围成簇分布（黄金角小半径螺旋），归属关系一眼可见
      const list = allStars.value.filter((s) => Number(s.semesterNo) === sem.semesterNo)
      const px = planet.position.x, py = planet.position.y, pz = planet.position.z
      list.forEach((star, ci) => {
        const color = STAR_COLOR[star.status] ?? 0x94a3b8
        // 成绩热力：有分数时按分数增亮（60~100 → 0.85~1.9），无分数用状态基准
        const score = star.score != null ? Number(star.score) : null
        const heat = score != null ? 0.85 + (Math.max(0, Math.min(100, score)) / 100) * 1.05 : 1.0
        const size = 0.12 + Math.sqrt(Math.max(1, Number(star.credit) || 1)) * 0.04 + (score != null ? (score / 100) * 0.05 : 0)
        const baseGlow = star.status === 'planned' ? 0.32 : 0.9 * heat
        const mesh = new T.Mesh(
          new T.SphereGeometry(size, 14, 10),
          new T.MeshStandardMaterial({
            color, emissive: color, emissiveIntensity: baseGlow,
            roughness: 0.32, metalness: 0.35, transparent: true, opacity: 1,
          })
        )
        mesh.userData.baseGlow = baseGlow
        const spread = 0.62 + Math.sqrt(ci) * 0.17
        const a = ci * 2.39996 + si
        const x = px + Math.cos(a) * spread
        const z = pz + Math.sin(a) * spread
        const y = py + (GROUP_Y[star.group] ?? 0)
        mesh.position.set(x, y, z)
        mesh.userData.star = star
        disk.add(mesh)
        courseMeshes.push(mesh)
        courseData.set(mesh, star)
        if (star.status === 'failed' || star.status === 'retaking') pulsing.push({ mesh, phase: ci * 0.9 + si })
        // 相对主星的小轨道公转
        orbiting.push({ mesh, cx: px, cy: y, cz: pz, r: spread, angle: a, speed: 0.00028 + (ci % 5) * 0.00004 })
      })
    })

    // 背景星尘
    const dustGeo = new T.BufferGeometry()
    const dust = new Float32Array(700 * 3)
    for (let i = 0; i < 700; i++) {
      const r = 28 + Math.random() * 55
      const th = Math.random() * Math.PI * 2
      const ph = Math.acos(2 * Math.random() - 1)
      dust[i * 3] = r * Math.sin(ph) * Math.cos(th)
      dust[i * 3 + 1] = r * Math.cos(ph)
      dust[i * 3 + 2] = r * Math.sin(ph) * Math.sin(th)
    }
    dustGeo.setAttribute('position', new T.BufferAttribute(dust, 3))
    scene.add(new T.Points(dustGeo, new T.PointsMaterial({ color: 0xbdd7f0, size: 0.045, transparent: true, opacity: 0.5 })))

    // ===== 交互：射线拾取课程星 + 学期主星 =====
    const raycaster = new T.Raycaster()
    const pointer = new T.Vector2()
    let hovered: T.Mesh | null = null
    function setPointer(e: PointerEvent) {
      const r = renderer.domElement.getBoundingClientRect()
      pointer.x = ((e.clientX - r.left) / r.width) * 2 - 1
      pointer.y = -((e.clientY - r.top) / r.height) * 2 + 1
      raycaster.setFromCamera(pointer, camera)
    }
    function pickCourse(e: PointerEvent) {
      setPointer(e)
      const hits = raycaster.intersectObjects(courseMeshes, false)
      // 跳过被筛选淡出的星体
      const hit = hits.find(h => ((h.object as T.Mesh).material as T.MeshStandardMaterial).opacity > 0.5)
      return hit?.object as T.Mesh | undefined
    }
    function pickSem(e: PointerEvent) {
      setPointer(e)
      return raycaster.intersectObjects(semMeshes, false)[0]?.object as T.Mesh | undefined
    }
    function onMove(e: PointerEvent) {
      const hit = pickCourse(e)
      if (hovered && hovered !== hit) { hovered.scale.setScalar(1); hovered = null }
      if (hit) {
        hit.scale.setScalar(1.85)
        hovered = hit
        const r = renderer.domElement.getBoundingClientRect()
        hover.value = { x: e.clientX - r.left, y: e.clientY - r.top, star: courseData.get(hit)! }
        renderer.domElement.style.cursor = 'pointer'
      } else {
        hover.value = null
        const semHit = pickSem(e)
        renderer.domElement.style.cursor = semHit ? 'pointer' : 'grab'
      }
    }
    function onLeave() {
      if (hovered) hovered.scale.setScalar(1)
      hovered = null
      hover.value = null
    }
    function onClick(e: PointerEvent) {
      const hit = pickCourse(e)
      if (hit) {
        selected.value = courseData.get(hit) || null
        return
      }
      const semHit = pickSem(e)
      if (semHit) {
        selected.value = null
        const no = semData.get(semHit)!
        focusSem.value = no
        // 镜头聚焦该学期主星（世界坐标），平滑推进
        const target = new T.Vector3()
        semHit.getWorldPosition(target)
        focusGoal = target.clone()
        // 相机沿当前视线方向推进到距目标约 11 单位处
        const dir = camera.position.clone().sub(target).normalize()
        focusCamGoal = target.clone().add(dir.multiplyScalar(11.5))
        exitFocusFn = () => { focusGoal = homeTarget.clone(); focusCamGoal = homeCam.clone() }
      } else if (focusSem.value != null) {
        // 点空白退出聚焦
        exitFocus()
      }
    }
    renderer.domElement.addEventListener('pointermove', onMove)
    renderer.domElement.addEventListener('pointerleave', onLeave)
    renderer.domElement.addEventListener('pointerdown', onClick)

    boxSize.value = { w: W(), h: H() }
    const ro = new ResizeObserver(() => {
      camera.aspect = W() / H()
      camera.updateProjectionMatrix()
      renderer.setSize(W(), H())
      composer.setSize(W(), H())
      labels.setSize(W(), H())
      boxSize.value = { w: W(), h: H() }
    })
    ro.observe(el)

    let t = 0
    function tick() {
      // 聚焦动画：平滑推进镜头到目标学期
      if (focusGoal) {
        controls.target.lerp(focusGoal, 0.07)
        if (focusCamGoal) camera.position.lerp(focusCamGoal, 0.07)
      }
      controls.update()
      t += 1
      if (!interacting && focusSem.value == null) {
        disk.rotation.y += 0.00045
        for (const o of orbiting) {
          o.angle += o.speed
          o.mesh.position.set(o.cx + Math.cos(o.angle) * o.r, o.cy, o.cz + Math.sin(o.angle) * o.r)
        }
        for (const f of flows) {
          f.angle += f.speed
          f.mesh.position.set(Math.cos(f.angle) * f.R, f.tilt * Math.sin(f.angle) * f.R, Math.sin(f.angle) * f.R)
        }
      }
      coreGem.rotation.y += 0.0045
      coreGem.rotation.x += 0.0016
      ringA.rotation.z += 0.0032
      ringB.rotation.z -= 0.0021
      // 进度仪表是相机子对象，随相机朝向，无需额外 billboard
      // 未通过 / 重修中的星体脉冲警示；hover 放大的星体跳过，避免抢 scale
      for (const p of pulsing) {
        if (p.mesh === hovered) continue
        const s = 1 + 0.25 * (0.5 + 0.5 * Math.sin(t * 0.055 + p.phase))
        p.mesh.scale.setScalar(s)
      }
      composer.render()
      labels.render(scene, camera)
      raf = requestAnimationFrame(tick)
    }
    raf = requestAnimationFrame(tick)
    ready.value = true

    // 状态筛选：非匹配课程星淡出（保留位置，仍能看出整体分布）
    applyFilterFn = (f) => {
      for (const mesh of courseMeshes) {
        const st = (mesh.userData.star as Star).status
        const norm = st === 'planned' || st === 'future' ? 'planned' : st
        const match = f === 'all' || norm === f
        const mat = mesh.material as T.MeshStandardMaterial
        mat.opacity = match ? 1 : 0.1
        mat.emissiveIntensity = match ? (mesh.userData.baseGlow as number) : 0.05
      }
    }

    // 聚焦时只保留该学期标签，其余淡出，避免镜头推进后标签拥挤
    watch(focusSem, (no) => {
      for (const t of semTags) t.el.style.opacity = (no == null || t.no === no) ? '1' : '0.12'
    })

    dispose = () => {
      cancelAnimationFrame(raf)
      ro.disconnect()
      renderer.domElement.removeEventListener('pointermove', onMove)
      renderer.domElement.removeEventListener('pointerleave', onLeave)
      renderer.domElement.removeEventListener('pointerdown', onClick)
      controls.dispose()
      composer.dispose?.()
      bloom.dispose?.()
      scene.traverse((o: any) => {
        o.geometry?.dispose?.()
        if (Array.isArray(o.material)) o.material.forEach((m: any) => { m.map?.dispose?.(); m.dispose?.() })
        else o.material?.dispose?.()
      })
      nebula.dispose()
      envRT.dispose()
      pmrem.dispose()
      scene.environment = null
      renderer.dispose()
      renderer.domElement.remove()
      labels.domElement.remove()
    }
  } catch (e) {
    console.error('[GradGalaxy3D] init failed', e)
    failed.value = true
  }
})

onBeforeUnmount(() => { dispose?.(); dispose = null })
</script>

<style scoped>
.ggx-wrap {
  position: relative; height: 520px; border-radius: 18px; overflow: hidden;
  background: #08131f;
  border: 1px solid rgba(94, 234, 212, 0.16); touch-action: none;
}
.ggx-wrap :deep(canvas) { display: block; }
.ggx-wrap :deep(.ggx-core-label) {
  display: flex; flex-direction: column; align-items: center; gap: 0;
  padding: 5px 15px; border-radius: 12px;
  background: rgba(12, 26, 40, .82); border: 1px solid rgba(250, 204, 21, .55);
  white-space: nowrap; box-shadow: 0 0 22px rgba(250, 204, 21, .25);
}
.ggx-wrap :deep(.ggx-core-label b) { font-size: 26px; font-weight: 800; line-height: 1.05; color: #ffdf94; font-variant-numeric: tabular-nums; }
.ggx-wrap :deep(.ggx-core-label small) { font-size: 10px; font-weight: 700; color: #d4b476; letter-spacing: 1px; }
.ggx-wrap :deep(.ggx-sem-label) {
  display: flex; flex-direction: column; align-items: center; gap: 1px;
  padding: 4px 9px; border-radius: 8px;
  color: #c9e6f5; background: rgba(9, 23, 36, .82); border: 1px solid rgba(94, 234, 212, .26);
  white-space: nowrap; pointer-events: none;
}
.ggx-wrap :deep(.ggx-sem-label b) { font-size: 11px; font-weight: 700; }
.ggx-wrap :deep(.ggx-sem-label.cur) {
  color: #ecfeff; border-color: rgba(94, 234, 212, .8);
  box-shadow: 0 0 14px rgba(45, 212, 191, .35);
}
.ggx-wrap :deep(.ggx-sem-label small) { font-size: 9.5px; font-weight: 600; color: #87a9bd; }

.ggx-hud {
  position: absolute; top: 16px; left: 16px; z-index: 5; pointer-events: none;
  padding: 13px 16px; border-radius: 13px; min-width: 196px;
  background: rgba(5, 16, 28, 0.72); backdrop-filter: blur(10px);
  border: 1px solid rgba(125, 211, 252, 0.18);
}
.ggx-hud-title {
  display: flex; align-items: center; gap: 7px; font-size: 13px; font-weight: 700;
  color: #d8fff2; margin-bottom: 10px; letter-spacing: .4px;
}
.ggx-hud-title span { font-size: 8.5px; font-weight: 600; color: #5c8595; letter-spacing: 1.1px; }
.ggx-mark { width: 7px; height: 7px; border-radius: 50%; background: #ffd98a; box-shadow: 0 0 10px #ffd98a; }
.ggx-hud-row { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; font-size: 12px; color: #9fc0d2; margin-top: 6px; }
.ggx-hud-row b { color: #eafff8; font-size: 15px; font-variant-numeric: tabular-nums; }
.ggx-hud-row b small { font-size: 11px; color: #8fb8c9; font-weight: 600; }
.ggx-hud-row.ok b { color: #34d399; }
.ggx-hud-foot { margin-top: 9px; padding-top: 8px; border-top: 1px solid rgba(125,211,252,.14); font-size: 10.5px; color: #6f97aa; }

/* 状态筛选条：HUD 下方 */
.ggx-filter {
  position: absolute; top: 184px; left: 16px; z-index: 6;
  display: flex; flex-direction: column; gap: 5px; align-items: flex-start;
}
.ggx-filter button {
  display: inline-flex; align-items: center; gap: 6px; min-width: 76px;
  padding: 5px 10px; border-radius: 7px; cursor: pointer;
  border: 1px solid rgba(125,211,252,.18); background: rgba(5,16,28,.7);
  color: #9fbccb; font-size: 10.5px; font-weight: 600; font-family: inherit;
  transition: all .18s;
}
.ggx-filter button:hover { border-color: rgba(94,234,212,.5); color: #d8fff2; }
.ggx-filter button.on { border-color: rgba(94,234,212,.75); background: rgba(94,234,212,.15); color: #ecfeff; }
.ggx-filter button i { width: 7px; height: 7px; border-radius: 50%; }

.ggx-stats {
  /* 下移避开右上的「图表 / 星图」切换按钮 */
  position: absolute; top: 58px; right: 16px; z-index: 5; pointer-events: auto;
  min-width: 132px; padding: 10px 13px; border-radius: 11px;
  background: rgba(5, 16, 28, 0.76); border: 1px solid rgba(125, 211, 252, 0.16);
}
.ggx-stats-head { font-size: 11.5px; font-weight: 700; color: #d8fff2; margin-bottom: 7px; padding-bottom: 6px; border-bottom: 1px solid rgba(125,211,252,.14); }
.ggx-stats-head span { float: right; font-size: 9.5px; font-weight: 600; color: #6f97aa; }
.ggx-stat-row { display: flex; align-items: center; gap: 7px; font-size: 10.5px; color: #a8c6d6; margin-top: 5px; padding: 2px 4px; border-radius: 5px; cursor: pointer; transition: background .15s; }
.ggx-stat-row:hover { background: rgba(94,234,212,.1); }
.ggx-stat-row.on { background: rgba(94,234,212,.18); color: #ecfeff; }
.ggx-stat-row i { width: 7px; height: 7px; border-radius: 50%; flex-shrink: 0; }
.ggx-stat-row b { margin-left: auto; font-size: 12px; color: #eafff8; font-variant-numeric: tabular-nums; }

.ggx-legend {
  position: absolute; bottom: 14px; left: 16px; right: 16px; z-index: 5; pointer-events: none;
  display: flex; align-items: center; gap: 13px; flex-wrap: wrap;
  font-size: 10.5px;
}
.ggx-legend span { display: inline-flex; align-items: center; gap: 5px; color: #a8c6d6; white-space: nowrap; }
.ggx-legend i { width: 7px; height: 7px; border-radius: 50%; display: inline-block; }
.ggx-legend em { margin-left: auto; font-style: normal; color: #7fa8bb; }

.ggx-tooltip {
  position: absolute; z-index: 8; pointer-events: none; max-width: 250px;
  padding: 9px 12px; border-radius: 10px;
  background: rgba(6, 19, 31, .94); border: 1px solid rgba(94, 234, 212, .38);
  box-shadow: 0 10px 26px rgba(0,0,0,.45);
  display: flex; flex-direction: column; gap: 3px;
}
.ggx-tooltip b { font-size: 12.5px; color: #edfaff; }
.ggx-tooltip em { font-size: 10.5px; font-style: normal; color: #8fb1c5; }
.ggx-tooltip span { font-size: 11px; font-weight: 700; }
.st-passed { color: #34d399 !important; }
.st-failed { color: #fb7185 !important; }
.st-retaking { color: #fb923c !important; }
.st-pending { color: #fde68a !important; }
.st-planned, .st-future { color: #94a3b8 !important; }

.ggx-detail {
  position: absolute; left: 16px; bottom: 48px; z-index: 9; width: 250px;
  padding: 14px 16px; border-radius: 12px;
  background: rgba(6, 19, 31, .95); border: 1px solid rgba(94, 234, 212, .42);
  box-shadow: 0 14px 34px rgba(0,0,0,.5);
  display: flex; flex-direction: column; gap: 6px;
}
.ggx-detail-close {
  position: absolute; top: 6px; right: 8px; border: 0; background: transparent;
  color: #7fa3b6; font-size: 18px; cursor: pointer; line-height: 1;
}
.ggx-detail > b { font-size: 14px; color: #edfaff; padding-right: 16px; }
.ggx-detail > em { font-size: 10.5px; font-style: normal; color: #8fb1c5; }
.ggx-detail-grid { display: grid; grid-template-columns: 44px 1fr; gap: 5px 8px; margin-top: 6px; padding-top: 8px; border-top: 1px solid rgba(125,211,252,.14); font-size: 11.5px; }
.ggx-detail-grid span { color: #7fa3b6; }
.ggx-detail-grid b { color: #dff3fb; font-weight: 700; }
.ggx-score-bar { height: 5px; border-radius: 99px; background: rgba(148,163,184,.18); overflow: hidden; margin-top: 4px; }
.ggx-score-bar i { display: block; height: 100%; border-radius: 99px; }
.ggx-pop-enter-active, .ggx-pop-leave-active { transition: opacity .2s, transform .2s; }
.ggx-pop-enter-from, .ggx-pop-leave-to { opacity: 0; transform: translateY(8px); }

/* 聚焦学期面板 */
.ggx-focus {
  position: absolute; right: 16px; top: 212px; bottom: 44px; z-index: 9; width: 268px;
  display: flex; flex-direction: column; border-radius: 12px; overflow: hidden;
  background: rgba(6, 19, 31, .95); border: 1px solid rgba(94, 234, 212, .42);
  box-shadow: 0 14px 34px rgba(0,0,0,.5);
}
.ggx-focus-head { padding: 11px 13px; border-bottom: 1px solid rgba(125,211,252,.16); display: flex; flex-direction: column; gap: 3px; position: relative; }
.ggx-focus-head b { font-size: 14px; color: #edfaff; }
.ggx-focus-head span { font-size: 10.5px; color: #8fb1c5; }
.ggx-focus-head button { position: absolute; top: 10px; right: 10px; border: 1px solid rgba(94,234,212,.35); background: rgba(94,234,212,.1); color: #8ee8d6; border-radius: 6px; padding: 3px 8px; font-size: 10.5px; cursor: pointer; }
.ggx-focus-list { flex: 1; overflow-y: auto; padding: 6px; }
.ggx-focus-list::-webkit-scrollbar { width: 5px; }
.ggx-focus-list::-webkit-scrollbar-thumb { background: rgba(94,234,212,.3); border-radius: 4px; }
.ggx-focus-item { display: flex; align-items: center; gap: 7px; padding: 7px 8px; border-radius: 7px; cursor: pointer; transition: background .15s; }
.ggx-focus-item:hover { background: rgba(94,234,212,.1); }
.ggx-focus-item > i { width: 7px; height: 7px; border-radius: 50%; flex-shrink: 0; }
.gfi-name { flex: 1; min-width: 0; font-size: 11.5px; color: #dff3fb; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ggx-focus-item em { font-size: 10px; color: #6f97aa; font-style: normal; flex-shrink: 0; }
.ggx-focus-item > b { font-size: 10px; flex-shrink: 0; }

.ggx-loading {
  position: absolute; inset: 0; display: flex; align-items: center; justify-content: center;
  color: #7fb3c5; font-size: 13px; z-index: 4;
}
</style>
