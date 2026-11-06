<template>
  <div ref="wrap" class="lg-wrap" aria-hidden="true">
    <canvas ref="cv" class="lg-canvas" :class="{ ready }"></canvas>
  </div>
</template>

<script setup lang="ts">
/**
 * 登录页 · 玻璃地球（Glass Globe）
 *
 * 历史问题：最初是一颗按 11.5px 铺满圆点的球（约 3100 点、直径 516px，
 * 比 300px 的表盘还大一圈），点均匀铺满 + 边缘透视挤成硬边，
 * 远看是一团发绿的雾 —— 既不像球，也把表盘整个盖住。
 *
 * 这一版（星环版）：
 * 1. **三条倾斜椭圆星环**取代原来的圆形仪表刻度盘。每条都是「三维圆环
 *    → 投影」的参数化（半长轴 a、扁率 flat、屏幕旋转 rot、深度 pz），
 *    不是画个椭圆再用遮罩挖洞；
 * 2. **遮挡是真实的绘制顺序**：先画星环后半段（t∈(π,2π)，pz<0）→ 再画球
 *    → 最后画前半段（t∈(0,π)，pz>0）。球本身是不透明的一整块，后半段在
 *    球内的部分自然被盖住，前半段自然压在球上 —— 环真的从球背后穿过去；
 *    前半段压在球面上的那截再单独降透明度，不糊住大陆；
 * 3. **真正的球面自转**：大陆轮廓、经纬网格、定位点都按「球面点 → 绕地轴
 *    旋转 → 正交/弱透视投影」逐点重算，而不是把一张平面图整体转圈。
 *    因此经线在球缘会自然收拢、大陆越靠边越被压扁，光影始终固定在
 *    光源方向（左上受光）—— 这是和「CSS 转图片」的根本区别；
 * 4. **地轴倾斜 11°**，而星环的倾角取 -11°（与地轴垂直）：环面贴着赤道面，
 *    地球自转时环不动，两者始终是同一个夹角，不会各转各的；
 * 5. 卫星光点的相位直接取系统真实秒数（0 秒在环近端正下方，60 秒一圈），
 *    拖尾沿轨迹回溯 11°，压在球面上时自动减淡；
 * 6. 极淡的经纬网格 + 少量星尘粒子，给球一点「地球仪」的细节层次；
 * 7. 只画朝向相机的半球、线条随朝向衰减，边缘自然化开，不会形成硬亮环；
 * 8. 2D Canvas 手绘，不再 import('three') —— 登录首屏少一个 ~600KB chunk；
 * 9. 30fps 限帧：自转 6°/s 时每帧才 0.2°，肉眼看不出差别，
 *    重绘开销却砍掉一半。
 */
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { LAND_RINGS_B64 } from './globe/land'

const wrap = ref<HTMLDivElement | null>(null)
const cv = ref<HTMLCanvasElement | null>(null)
const ready = ref(false)

let disposed = false
let raf = 0
const cleanups: Array<() => void> = []

onMounted(() => {
  const host = wrap.value
  const canvas = cv.value
  if (!host || !canvas) return
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  const TAU = Math.PI * 2
  const D2R = Math.PI / 180

  // ---------- 解码大陆轮廓（int16，0.01°） ----------
  const bin = atob(LAND_RINGS_B64)
  const bytes = new Uint8Array(bin.length)
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i)
  const i16 = new Int16Array(bytes.buffer)

  const ringStart: number[] = []
  const ringLen: number[] = []
  const lngs: number[] = []
  const lats: number[] = []
  let p = 0
  while (p < i16.length) {
    const n = i16[p++]
    if (!n || p + n * 2 > i16.length) break
    ringStart.push(lngs.length)
    ringLen.push(n)
    for (let k = 0; k < n; k++) {
      lngs.push(i16[p] / 100)
      lats.push(i16[p + 1] / 100)
      p += 2
    }
  }
  const NP = lngs.length
  const NX = new Float32Array(NP)
  const NY = new Float32Array(NP)
  const NZ = new Float32Array(NP)
  for (let i = 0; i < NP; i++) {
    const la = lats[i] * D2R
    const lo = lngs[i] * D2R
    const cl = Math.cos(la)
    NX[i] = cl * Math.sin(lo)
    NY[i] = Math.sin(la)
    NZ[i] = cl * Math.cos(lo)
  }

  // ---------- 经纬网格（极淡的底纹，让球有「地球仪」的细节） ----------
  const GRID: Float32Array[] = []
  {
    const push = (arr: number[]) => GRID.push(new Float32Array(arr))
    for (let lng = 0; lng < 360; lng += 15) {
      const lo = lng * D2R
      const sl = Math.sin(lo)
      const cl = Math.cos(lo)
      const arr: number[] = []
      for (let lat = -90; lat <= 90; lat += 15) {
        const la = lat * D2R
        const cf = Math.cos(la)
        arr.push(cf * sl, Math.sin(la), cf * cl)
      }
      push(arr)
    }
    for (let lat = -75; lat <= 75; lat += 15) {
      const la = lat * D2R
      const cf = Math.cos(la)
      const sf = Math.sin(la)
      const arr: number[] = []
      for (let lng = 0; lng <= 360; lng += 15) {
        const lo = lng * D2R
        arr.push(cf * Math.sin(lo), sf, cf * Math.cos(lo))
      }
      push(arr)
    }
  }

  // ---------- 尺寸 ----------
  let W = 0, H = 0, DPR = 1
  let cx = 0, cy = 0, R = 130
  let visible = false
  let mx = 0, my = 0

  // 表盘位置会被上方文字/入场动画带着走（尺寸不变，ResizeObserver 抓不到），
  // 所以几何按 0.35s 轮询一次，保证球心始终跟着表盘走
  let gaugeEl: HTMLElement | null = null
  const syncGeom = () => {
    if (!gaugeEl || !gaugeEl.isConnected) gaugeEl = document.querySelector('.auth-gauge') as HTMLElement | null
    const g = gaugeEl?.getBoundingClientRect()
    // 窄屏左栏整体隐藏，球在卡片背后空转没意义 —— 直接不画
    visible = !!g && g.width > 2 && W >= 900
    host.style.visibility = visible ? '' : 'hidden'
    if (!visible || !g) return

    const hr = host.getBoundingClientRect()
    cx = g.left - hr.left + g.width / 2
    // 球心 = 星环中心：读数在环外下方，球不需要上移，避免视觉偏轴
    cy = g.top - hr.top + g.height / 2
    // 球径收到容器半径的 0.59：星环要伸出球外一大截（土星环的观感），
    // 球再大就只剩「环贴边」了
    R = (g.width / 220) * 64.5
  }

  const resize = () => {
    W = host.clientWidth || window.innerWidth
    H = host.clientHeight || window.innerHeight
    DPR = Math.min(2, window.devicePixelRatio || 1)
    canvas.width = Math.max(1, Math.round(W * DPR))
    canvas.height = Math.max(1, Math.round(H * DPR))
    canvas.style.width = W + 'px'
    canvas.style.height = H + 'px'
    ctx.setTransform(DPR, 0, 0, DPR, 0, 0)
    syncGeom()
  }

  // ---------- 绘制 ----------
  // 中国在东经 118°，开场把它转到正对镜头，定位点一眼可见
  const YAW0 = -2.061
  const tl = 24.6165 * D2R
  const tg = 118.1045 * D2R
  const TX = Math.cos(tl) * Math.sin(tg)
  const TY = Math.sin(tl)
  const TZ = Math.cos(tl) * Math.cos(tg)

  const D = 3.2                              // 透视距离（单位球）

  // 自转：60s 一圈（6°/s），自西向东 —— 经度增大的方向，和真实地球一致。
  // 与外部轨道光点同周期：球转一圈 = 光点绕一圈 = 秒针走满 60 格。
  const SPIN = TAU / 60
  // 地轴在画面内倾斜 11°（北极偏左上）：大陆轨迹是斜的，不是垂直上下滑
  const TILT = 0.19

  const PX = new Float32Array(NP)
  const PY = new Float32Array(NP)
  const PZ = new Float32Array(NP)
  // 线段按朝向分档，每档一条 path 一次 stroke
  const BUCK = 7
  const buckets: number[][] = []
  for (let i = 0; i < BUCK; i++) buckets.push([])

  // ---------- 星环 ----------
  // 三维圆环参数化后投影到屏幕：
  //   lx = a·cos t              环平面内的横轴
  //   ly = a·sin t·flat         投影到屏幕上的纵轴（flat = 短轴/长轴）
  //   pz = a·sin t·dep          朝向相机的深度（dep = √(1−flat²)）
  // t ∈ (0, π) 时 pz > 0 → 这段落在屏幕下方，在球的**前面**；
  // t ∈ (π, 2π) 时 pz < 0 → 落在屏幕上方，在球的**后面**。
  interface RingDef {
    aRel: number; flat: number; dep: number; cs: number; sn: number
    w: number; cols: [string, string, string]
  }
  const mkRing = (aRel: number, flat: number, rotDeg: number, w: number,
                  cols: [string, string, string]): RingDef => {
    const rot = rotDeg * D2R
    return {
      aRel, flat, dep: Math.sqrt(1 - flat * flat), w,
      cs: Math.cos(rot), sn: Math.sin(rot), cols,
    }
  }
  // 主环与外侧光带用 -17°：比「与地轴垂直」的 -11° 再斜一点，轨道感更强；
  // 中间那条交叉环用 +31°，与主环形成明显的异面交叉，是「多条轨道」的纵深来源
  const RINGS: RingDef[] = [
    // 主环：薄荷 → 青绿 → 薄荷，环的两端亮、中段深，像一段有高光的实体轨道
    mkRing(1.52, 0.600, -17, 3.1,
      ['rgba(166,230,212,0.95)', 'rgba(18,128,102,1)', 'rgba(128,210,188,0.95)']),
    // 交叉环：更细更淡，不同倾角，从地球另一侧绕过去
    mkRing(1.40, 0.560, 31, 1.15,
      ['rgba(168,230,214,0.62)', 'rgba(52,166,140,0.74)', 'rgba(168,230,214,0.58)']),
    // 第三层：主环外侧一圈极淡的轨道光，只做纵深，不抢戏
    mkRing(1.63, 0.460, -17, 0.8,
      ['rgba(176,232,220,0.17)', 'rgba(96,196,174,0.23)', 'rgba(176,232,220,0.14)']),
  ]

  // 复用的投影结果，避免每帧产生垃圾对象
  const RP = [0, 0, 0]
  const ringPt = (rg: RingDef, t: number, a: number) => {
    const ca = Math.cos(t), sa = Math.sin(t)
    const lx = a * ca
    const ly = a * sa * rg.flat
    const pz = a * sa * rg.dep
    // 轻微透视：远端收 5%、近端放 5%，看起来是「绕着」而不是「贴着」
    const k = 1 + 0.05 * (pz / R)
    RP[0] = (lx * rg.cs - ly * rg.sn) * k
    RP[1] = (lx * rg.sn + ly * rg.cs) * k
    RP[2] = pz
  }

  const RSEG = 132
  const strokeRing = (rg: RingDef, a: number, from: number, to: number,
                      wMul: number, alpha: number) => {
    if (alpha <= 0.004) return
    // 沿环的长轴做线性渐变：左端薄荷、中段青绿、右端薄荷回亮
    const gx = rg.cs * a, gy = rg.sn * a
    const grd = ctx.createLinearGradient(-gx, -gy, gx, gy)
    grd.addColorStop(0, rg.cols[0])
    grd.addColorStop(0.5, rg.cols[1])
    grd.addColorStop(1, rg.cols[2])
    // 端点区域整体压暗一档（见下方端点淡出）
    ctx.beginPath()
    for (let i = 0; i <= RSEG; i++) {
      const t = from + (to - from) * (i / RSEG)
      ringPt(rg, t, a)
      if (i === 0) ctx.moveTo(RP[0], RP[1])
      else ctx.lineTo(RP[0], RP[1])
    }
    ctx.strokeStyle = grd
    ctx.lineWidth = rg.w * wMul
    ctx.globalAlpha = alpha
    ctx.lineJoin = 'round'
    ctx.lineCap = 'round'
    ctx.stroke()
    ctx.globalAlpha = 1
  }

  // 画一整段（前半 / 后半）的所有环：先铺一层柔光晕，再压主线
  const ringPass = (front: boolean, aMul: number, wMul: number) => {
    const from = front ? 0 : Math.PI
    const to = front ? Math.PI : TAU
    for (let i = 0; i < RINGS.length; i++) {
      const rg = RINGS[i]
      const a = R * rg.aRel
      strokeRing(rg, a, from, to, wMul * 2.7, aMul * 0.30)
      strokeRing(rg, a, from, to, wMul, aMul)
    }
  }

  // 星尘粒子：极少量、极低透明度，一多半藏在球后（先画、被球盖掉），
  // 少量在前（后画），靠疏密差拉开纵深
  const DUST: Array<{ ang: number; rad: number; sz: number; al: number; sp: number; ph: number }> = []
  {
    let sd = 20261102
    const rnd = () => { sd = (sd * 1103515245 + 12345) & 0x7fffffff; return sd / 0x7fffffff }
    for (let i = 0; i < 18; i++) {
      DUST.push({
        ang: rnd() * TAU,
        rad: 1.05 + rnd() * 0.52,          // × R，最远 1.57R 仍在容器内
        sz: 0.5 + rnd() * 0.85,
        al: 0.10 + rnd() * 0.15,
        sp: (rnd() < 0.5 ? -1 : 1) * (0.012 + rnd() * 0.028),  // 2~8 分钟绕一圈
        ph: rnd() * TAU,
      })
    }
  }
  const drawDust = (t: number, near: boolean) => {
    for (let i = 0; i < DUST.length; i++) {
      // 每 3 个里有 1 个放到球前，其余在球后
      if ((i % 3 === 0) !== near) continue
      const p = DUST[i]
      const ang = p.ang + t * p.sp
      const rr = R * p.rad + Math.sin(t * 0.55 + p.ph) * 1.6
      const x = Math.cos(ang) * rr
      const y = Math.sin(ang) * rr * 0.93
      const tw = 0.55 + 0.45 * Math.sin(t * 0.8 + p.ph)
      ctx.beginPath()
      ctx.arc(x, y, p.sz * (near ? 0.82 : 1), 0, TAU)
      ctx.fillStyle = `rgba(74,182,160,${(p.al * tw * (near ? 0.75 : 1)).toFixed(3)})`
      ctx.fill()
    }
  }

  // 卫星光点 + 短拖尾。相位取系统真实秒数：0 秒在环的近端正下方，
  // 顺时针 60 秒绕满一圈 —— 与地球自转同周期，但时间本身仍来自系统时钟
  const drawComet = (t: number, front: boolean) => {
    const rg = RINGS[0]
    const a = R * rg.aRel
    const now = new Date()
    const sec = now.getSeconds() + now.getMilliseconds() / 1000
    const t0 = Math.PI / 2 + (sec / 60) * TAU

    // 拖尾：沿轨迹回溯 18 小段（合计约 11°），越远越淡越细
    const TAIL = 18
    const STEP = 0.0108
    for (let i = TAIL; i >= 1; i--) {
      ringPt(rg, t0 - STEP * i, a)
      const x1 = RP[0], y1 = RP[1], z1 = RP[2]
      ringPt(rg, t0 - STEP * (i - 1), a)
      const x2 = RP[0], y2 = RP[1], z2 = RP[2]
      // 跨过球的前后分界就断开：尾巴被球挡住的那一段不该露出来
      if ((z1 + z2 < 0) === front) continue
      const f = 1 - i / TAIL
      const din = Math.hypot((x1 + x2) * 0.5, (y1 + y2) * 0.5) < R
      const k = din ? 0.42 : 1
      ctx.beginPath()
      ctx.moveTo(x1, y1)
      ctx.lineTo(x2, y2)
      ctx.strokeStyle = `rgba(32,190,152,${(0.42 * f * f * k).toFixed(3)})`
      ctx.lineWidth = 0.45 + 1.55 * f
      ctx.lineCap = 'round'
      ctx.stroke()
    }

    ringPt(rg, t0, a)
    // 只在自己的那一层画：光点被调用两次（球前/球后各一次），
    // 不过滤就会重复描一遍，边缘出现一层淡淡的重影
    if ((RP[2] > 0) !== front) return
    const px = RP[0], py = RP[1]
    const k = Math.hypot(px, py) < R ? 0.5 : 1
    const pulse = 0.5 + 0.5 * Math.sin(t * 1.9)

    const gl = ctx.createRadialGradient(px, py, 0, px, py, 13)
    gl.addColorStop(0, `rgba(64,214,178,${(0.34 * k).toFixed(3)})`)
    gl.addColorStop(0.40, `rgba(52,200,168,${(0.13 * k).toFixed(3)})`)
    gl.addColorStop(1, 'rgba(52,200,168,0)')
    ctx.beginPath()
    ctx.arc(px, py, 13, 0, TAU)
    ctx.fillStyle = gl
    ctx.fill()

    // 呼吸外环：像一颗有信号的小卫星
    ctx.beginPath()
    ctx.arc(px, py, 4.4 + pulse * 3.1, 0, TAU)
    ctx.strokeStyle = `rgba(26,178,144,${(0.34 * (1 - pulse * 0.55) * k).toFixed(3)})`
    ctx.lineWidth = 1.1
    ctx.stroke()

    // 亮核 + 一点白芯
    ctx.beginPath()
    ctx.arc(px, py, 2.6, 0, TAU)
    ctx.fillStyle = `rgba(20,196,154,${(0.98 * k).toFixed(3)})`
    ctx.fill()
    ctx.beginPath()
    ctx.arc(px - 0.7, py - 0.75, 1.0, 0, TAU)
    ctx.fillStyle = `rgba(244,255,251,${(0.95 * k).toFixed(3)})`
    ctx.fill()
  }

  // 交叉环上的一颗小卫星节点：纯装饰，走得很慢，方向与光点相反
  const drawMiniSat = (t: number, front: boolean) => {
    const rg = RINGS[1]
    const a = R * rg.aRel
    const tt = Math.PI / 2 - (t / 96) * TAU
    ringPt(rg, tt, a)
    if ((RP[2] > 0) !== front) return
    const px = RP[0], py = RP[1]
    if (Math.hypot(px, py) < R * 0.99) return       // 压在球面上就不画了
    ctx.beginPath()
    ctx.arc(px, py, 3.4, 0, TAU)
    ctx.fillStyle = 'rgba(78,196,172,0.09)'
    ctx.fill()
    ctx.beginPath()
    ctx.arc(px, py, 1.15, 0, TAU)
    ctx.fillStyle = 'rgba(36,168,140,0.55)'
    ctx.fill()
  }

  let lastSync = -1
  const draw = (t: number) => {
    // 入场动画（0.5s 位移）期间逐帧跟位置，稳定后改为 0.35s 轮询
    if (t < 1.6 || t - lastSync > 0.35 || lastSync < 0) { lastSync = t; syncGeom() }
    ctx.clearRect(0, 0, W, H)
    if (!visible) return

    ctx.save()
    ctx.translate(cx, cy)

    const yaw = YAW0 + t * SPIN + mx * 0.06
    const pitch = 0.24 + my * 0.035
    const sy = Math.sin(yaw), cyw = Math.cos(yaw)
    const sp = Math.sin(pitch), cp = Math.cos(pitch)
    const stl = Math.sin(TILT), ctl = Math.cos(TILT)

    // 球外一圈极淡的薄荷青光晕（唯一的一层光，不再叠加白色柔光片）
    const glow = ctx.createRadialGradient(0, 0, R * 0.96, 0, 0, R * 1.5)
    glow.addColorStop(0, 'rgba(94,190,172,0.16)')
    glow.addColorStop(0.5, 'rgba(110,198,182,0.07)')
    glow.addColorStop(1, 'rgba(140,210,196,0)')
    ctx.beginPath()
    ctx.arc(0, 0, R * 1.5, 0, TAU)
    ctx.fillStyle = glow
    ctx.fill()

    // —— 球后层：星尘 + 星环后半段。先画，随后被不透明的球体自然遮住，
    //    这就是「环从球背后穿过去」的全部实现 —— 不需要任何 mask ——
    drawDust(t, false)
    ringPass(false, 0.82, 0.94)
    drawComet(t, false)
    drawMiniSat(t, false)

    ctx.save()
    ctx.beginPath()
    ctx.arc(0, 0, R, 0, TAU)
    ctx.clip()

    // —— 球体：左上受光、右下压暗的浅色玻璃 ——
    // 光源在左上、渐变半径要盖住整个球面（球底距光源 1.4R，给到 1.55R），
    // 否则球底直接吃到末端色，整颗球会出现一圈「沉底」的青灰
    const fill = ctx.createRadialGradient(-R * 0.30, -R * 0.34, R * 0.04, 0, 0, R * 1.55)
    // 球体必须不透明：星环的后半段画在它下面，只要透一点点，
    // 「环被球挡住」就会变成「环贴在球面上」，空间关系立刻垮掉
    // 球面整体提白、只留边缘一层薄荷：环是深青绿的，两者明度拉开，
    // 环才「跳」得出来；如果球和环都压在中绿区，远看会糊成一团
    fill.addColorStop(0, 'rgba(255,255,255,1)')
    fill.addColorStop(0.26, 'rgba(250,255,253,1)')
    fill.addColorStop(0.54, 'rgba(219,245,237,1)')
    fill.addColorStop(0.76, 'rgba(186,227,219,1)')
    fill.addColorStop(0.90, 'rgba(162,209,202,1)')
    fill.addColorStop(1, 'rgba(140,197,190,1)')
    ctx.fillStyle = fill
    ctx.fillRect(-R * 1.3, -R * 1.3, R * 2.6, R * 2.6)

    const shade = ctx.createRadialGradient(R * 0.36, R * 0.42, R * 0.04, R * 0.36, R * 0.42, R * 0.96)
    shade.addColorStop(0, 'rgba(11,84,86,0.17)')
    shade.addColorStop(0.52, 'rgba(11,84,86,0.06)')
    shade.addColorStop(1, 'rgba(11,84,86,0)')
    ctx.fillStyle = shade
    ctx.fillRect(-R * 1.3, -R * 1.3, R * 2.6, R * 2.6)

    // —— 投影所有轮廓点：自转 → 俯仰 → 地轴倾斜（绕视线方向）——
    for (let i = 0; i < NP; i++) {
      const x = NX[i], y = NY[i], z = NZ[i]
      const x1 = x * cyw + z * sy
      const z1 = -x * sy + z * cyw
      const y2 = y * cp - z1 * sp
      const z2 = y * sp + z1 * cp
      const x3 = x1 * ctl - y2 * stl
      const y3 = x1 * stl + y2 * ctl
      PZ[i] = z2
      const k2 = D / (D - z2)
      PX[i] = x3 * k2 * R
      PY[i] = -y3 * k2 * R
    }

    // —— 地轴：一条极淡的斜线贯穿球心，让「倾斜」这件事看得见 ——
    // 北极方向 = 世界 y 轴经自转/俯仰/倾斜后的屏幕投影，忽略透视即为 (-sinT, -cosT)
    const ax = -stl, ay = -ctl
    ctx.beginPath()
    ctx.moveTo(ax * R, ay * R)
    ctx.lineTo(-ax * R, -ay * R)
    ctx.strokeStyle = 'rgba(18,84,100,0.038)'
    ctx.lineWidth = 0.9
    ctx.stroke()

    // —— 经纬网格：极淡的底纹，给球一点「地球仪」的细节 ——
    ctx.beginPath()
    for (let li = 0; li < GRID.length; li++) {
      const line = GRID[li]
      const n = line.length / 3
      let started = false
      for (let k = 0; k < n; k++) {
        const x = line[k * 3], y = line[k * 3 + 1], z = line[k * 3 + 2]
        const x1 = x * cyw + z * sy
        const z1 = -x * sy + z * cyw
        const y2 = y * cp - z1 * sp
        const z2 = y * sp + z1 * cp
        if (z2 <= 0.02) { started = false; continue }
        const x3 = x1 * ctl - y2 * stl
        const y3 = x1 * stl + y2 * ctl
        const k2 = D / (D - z2)
        const px = x3 * k2 * R
        const py = -y3 * k2 * R
        if (!started) { ctx.moveTo(px, py); started = true }
        else ctx.lineTo(px, py)
      }
    }
    ctx.strokeStyle = 'rgba(26,86,100,0.095)'
    ctx.lineWidth = 0.55
    ctx.stroke()

    // —— 线段按朝向分档 ——
    for (let b = 0; b < BUCK; b++) buckets[b].length = 0
    for (let r = 0; r < ringStart.length; r++) {
      const s0 = ringStart[r]
      const n = ringLen[r]
      for (let k = 0; k < n; k++) {
        const i1 = s0 + k
        const i2 = s0 + ((k + 1) % n)
        const za = PZ[i1], zb = PZ[i2]
        if (za <= 0.015 || zb <= 0.015) continue   // 背面不画
        const z = (za + zb) * 0.5
        let b = Math.round((z - 0.015) / (1 - 0.015) * (BUCK - 1))
        if (b < 0) b = 0
        else if (b > BUCK - 1) b = BUCK - 1
        const arr = buckets[b]
        arr.push(PX[i1], PY[i1], PX[i2], PY[i2])
      }
    }

    // 先铺一层粗而淡的"水彩晕"，再压细线 —— 大陆才有厚度，又不是死描边
    for (let pass = 0; pass < 2; pass++) {
      for (let b = 0; b < BUCK; b++) {
        const arr = buckets[b]
        if (!arr.length) continue
        const a = b / (BUCK - 1)
        ctx.beginPath()
        for (let i = 0; i < arr.length; i += 4) {
          ctx.moveTo(arr[i], arr[i + 1])
          ctx.lineTo(arr[i + 2], arr[i + 3])
        }
        if (pass === 0) {
          ctx.strokeStyle = `rgba(24,100,112,${(0.035 + 0.10 * a).toFixed(3)})`
          ctx.lineWidth = 2.0
        } else {
          ctx.strokeStyle = `rgba(12,54,70,${(0.20 + 0.54 * Math.pow(a, 0.95)).toFixed(3)})`
          ctx.lineWidth = 0.58 + 0.36 * a
        }
        ctx.lineJoin = 'round'
        ctx.stroke()
      }
    }

    // —— 厦门定位点：跟着球转，转到背面自动淡出 ——
    const tx1 = TX * cyw + TZ * sy
    const tz1 = -TX * sy + TZ * cyw
    const ty2 = TY * cp - tz1 * sp
    const tz2 = TY * sp + tz1 * cp
    if (tz2 > 0.1) {
      const tX3 = tx1 * ctl - ty2 * stl
      const tY3 = tx1 * stl + ty2 * ctl
      const k2 = D / (D - tz2)
      const px = tX3 * k2 * R
      const py = -tY3 * k2 * R
      const fade = Math.min(1, (tz2 - 0.1) / 0.34)
      const pulse = 0.5 + 0.5 * Math.sin(t * 1.4)

      ctx.beginPath()
      ctx.arc(px, py, 5.5 + pulse * 5, 0, TAU)
      ctx.strokeStyle = `rgba(16,150,118,${(0.32 * fade * (1 - pulse * 0.6)).toFixed(3)})`
      ctx.lineWidth = 1
      ctx.stroke()

      ctx.beginPath()
      ctx.arc(px, py, 3, 0, TAU)
      ctx.fillStyle = `rgba(15,138,110,${(0.95 * fade).toFixed(3)})`
      ctx.fill()

      ctx.beginPath()
      ctx.arc(px - 0.6, py - 0.6, 1, 0, TAU)
      ctx.fillStyle = `rgba(255,255,255,${(0.9 * fade).toFixed(3)})`
      ctx.fill()
    }

    // —— 晨昏线：左上柔光、右下压暗，球才有明确的明暗关系 ——
    const term = ctx.createLinearGradient(-R * 0.66, -R * 0.7, R * 0.66, R * 0.7)
    term.addColorStop(0, 'rgba(255,255,255,0.24)')
    term.addColorStop(0.46, 'rgba(0,0,0,0)')
    term.addColorStop(1, 'rgba(12,78,86,0.16)')
    ctx.fillStyle = term
    ctx.fillRect(-R * 1.3, -R * 1.3, R * 2.6, R * 2.6)

    // —— 环的反光：星环就在球的下方，球底会吃到一层青绿的反射光。
    // 没有这层，球和环就是两个各自独立的图形；有了它，两者才在同一个空间里 ——
    const refl = ctx.createLinearGradient(0, R * 0.42, 0, R)
    refl.addColorStop(0, 'rgba(40,176,150,0)')
    refl.addColorStop(0.62, 'rgba(40,176,150,0.07)')
    refl.addColorStop(1, 'rgba(34,168,142,0.17)')
    ctx.fillStyle = refl
    ctx.fillRect(-R * 1.3, -R * 1.3, R * 2.6, R * 2.6)

    // —— 边缘大气：球缘内侧一圈淡淡的青绿辉光，星球感全靠它 ——
    const atmo = ctx.createRadialGradient(0, 0, R * 0.74, 0, 0, R)
    atmo.addColorStop(0, 'rgba(120,220,204,0)')
    atmo.addColorStop(0.62, 'rgba(150,235,220,0.055)')
    atmo.addColorStop(1, 'rgba(198,248,238,0.20)')
    ctx.fillStyle = atmo
    ctx.fillRect(-R * 1.3, -R * 1.3, R * 2.6, R * 2.6)

    // —— 左上高光：一层很柔的白，像玻璃的反光 ——
    const spec = ctx.createRadialGradient(-R * 0.38, -R * 0.44, 0, -R * 0.38, -R * 0.44, R * 0.95)
    spec.addColorStop(0, 'rgba(240,255,251,0.62)')
    spec.addColorStop(0.30, 'rgba(236,255,249,0.24)')
    spec.addColorStop(0.62, 'rgba(230,255,247,0.07)')
    spec.addColorStop(1, 'rgba(214,255,248,0)')
    ctx.fillStyle = spec
    ctx.fillRect(-R * 1.3, -R * 1.3, R * 2.6, R * 2.6)

    ctx.restore()

    // —— 球缘：左上亮（受光），右下深（背光），把轮廓钉住 ——
    const rim = ctx.createLinearGradient(-R * 0.72, -R * 0.72, R * 0.72, R * 0.72)
    rim.addColorStop(0, 'rgba(255,255,255,0.95)')
    rim.addColorStop(0.24, 'rgba(255,255,255,0.34)')
    rim.addColorStop(0.40, 'rgba(80,168,162,0.15)')
    rim.addColorStop(0.72, 'rgba(64,158,152,0.24)')
    rim.addColorStop(1, 'rgba(46,138,134,0.38)')
    ctx.beginPath()
    ctx.arc(0, 0, R - 0.4, 0, TAU)
    ctx.strokeStyle = rim
    ctx.lineWidth = 1.35
    ctx.stroke()

    // —— 星环·前半段：从球前方掠过。球外段全亮 ——
    ctx.save()
    ctx.beginPath()
    ctx.rect(-W * 2, -H * 2, W * 4, H * 4)
    ctx.arc(0, 0, R, 0, TAU)
    ctx.clip('evenodd')
    ringPass(true, 1, 1)
    ctx.restore()

    // 压在球面上的那一段：降透明度 + 减细，环从球前过但不糊住大陆
    ctx.save()
    ctx.beginPath()
    ctx.arc(0, 0, R, 0, TAU)
    ctx.clip()
    ringPass(true, 0.92, 0.86)
    ctx.restore()

    // —— 球前层：卫星光点、交叉环小节点、少量星尘 ——
    drawComet(t, true)
    drawMiniSat(t, true)
    drawDust(t, true)

    ctx.restore()
  }

  // ---------- 交互 / 循环 ----------
  const onMove = (e: MouseEvent) => {
    mx = e.clientX / window.innerWidth - 0.5
    my = e.clientY / window.innerHeight - 0.5
  }

  const clock = { t: 0, last: 0 }
  // 自转只有 1.26°/s，30fps 已经足够顺滑，比 60fps 省一半重绘开销
  const FRAME = 1000 / 30
  let nextFrame = 0
  const tick = (now: number) => {
    if (disposed) return
    if (!clock.last) clock.last = now
    const dt = Math.min(0.05, (now - clock.last) / 1000)
    clock.last = now
    clock.t += dt
    if (now >= nextFrame) {
      nextFrame = now + FRAME
      draw(clock.t)
    }
    raf = requestAnimationFrame(tick)
  }

  const onVis = () => {
    cancelAnimationFrame(raf)
    clock.last = 0
    if (!disposed && !document.hidden && !reduced) raf = requestAnimationFrame(tick)
  }

  window.addEventListener('resize', resize)
  window.addEventListener('mousemove', onMove, { passive: true })
  document.addEventListener('visibilitychange', onVis)

  cleanups.push(
    () => window.removeEventListener('resize', resize),
    () => window.removeEventListener('mousemove', onMove),
    () => document.removeEventListener('visibilitychange', onVis),
    () => cancelAnimationFrame(raf),
  )

  resize()
  ready.value = true
  if (reduced) draw(0)
  else raf = requestAnimationFrame(tick)
})

onBeforeUnmount(() => {
  disposed = true
  cleanups.forEach(f => { try { f() } catch {} })
})
</script>

<style scoped>
.lg-wrap {
  position: absolute;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
}
.lg-canvas {
  width: 100%;
  height: 100%;
  display: block;
  opacity: 0;
  transition: opacity 1.1s ease;
}
.lg-canvas.ready { opacity: 1; }
@media (prefers-reduced-motion: reduce) {
  .lg-canvas { transition: none; }
}
</style>
