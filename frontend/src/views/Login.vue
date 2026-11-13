<template>
  <div class="atlas-page" :class="{ 'is-leaving': rolling, 'is-en': lang === 'en' }">
    <!-- ============ 学域星图：three.js 实时 3D 主视觉 ============ -->
    <AtlasScene :success="loginOk" />
    <div class="atlas-aurora" aria-hidden="true"></div>
    <div class="atlas-grain" aria-hidden="true"></div>

    <!-- ============ 页眉 ============ -->
    <header class="masthead">
      <div class="brand">
        <div class="brand-sigil" aria-hidden="true"><i></i><i></i><i></i></div>
        <div class="brand-text">
          <div class="brand-cn">智学选课</div>
          <div class="brand-en">ZHIXUE · COURSE ATLAS</div>
        </div>
      </div>
      <div class="mast-right">
        <span class="live"><i></i>{{ t('星图同步中') }}</span>
        <span class="clock">{{ clockText }}</span>
        <nav class="lang-sw" :aria-label="t('语言')">
          <button type="button" :class="{ on: lang === 'zh' }" @click="switchLang('zh')">中</button>
          <i aria-hidden="true">/</i>
          <button type="button" :class="{ on: lang === 'en' }" @click="switchLang('en')">EN</button>
        </nav>
      </div>
    </header>

    <!-- ============ 主区 ============ -->
    <main class="atlas-main" :class="{ 'is-reg': tab === 'register' }">

      <!-- 左：系统识别 + 星图读数 -->
      <section class="intro">
        <p class="intro-season">{{ t('本学期选课通道已开放') }}</p>
        <h1 class="intro-title">智学<span>选课</span></h1>
        <p class="intro-lead">{{ t('把每一门课，连成你的星图') }}</p>
        <p class="intro-desc">{{ t('大学生课程管理平台') }}<br><span>{{ t('统一身份认证 · 实时选课数据 · 全终端同步') }}</span></p>

        <div class="star-rule" aria-hidden="true"><i></i><i></i><i></i><i></i><i></i></div>

        <div class="metrics" :aria-label="t('平台实时数据')" :title="statsState === 'err' ? t('统计暂不可用') : undefined">
          <div class="metric" v-for="d in dataBand" :key="d.key">
            <b v-if="statsState === 'ok'" class="metric-n">{{ d.value }}</b>
            <i v-else-if="statsState === 'loading'" class="metric-skeleton" aria-hidden="true"></i>
            <b v-else class="metric-n is-na">—</b>
            <span class="metric-l">{{ t(d.label) }}</span>
          </div>
        </div>

        <p class="intro-note">{{ t('课程即星辰，选择即连线。每一次选课，都在更新属于你的学域。') }}</p>
      </section>

      <!-- 右：身份认证玻璃卡 -->
      <section class="auth-wrap">
        <section class="auth-card" :class="{ 'is-ok': loginOk, 'is-err': !!loginErr, 'is-busy': globing }">
          <div class="card-sheen" aria-hidden="true"></div>
          <div class="card-busy" aria-hidden="true"><i></i></div>

          <!-- ===== 登录 ===== -->
          <template v-if="tab === 'login'">
            <div class="card-head">
              <div class="card-kicker">{{ t('统一身份认证') }}</div>
              <h2 class="card-title">{{ t('欢迎回来') }}</h2>
              <p class="card-sub">{{ t('登录后进入你的选课工作台') }}</p>
            </div>

            <form class="auth-form" @submit.prevent="doLogin">
              <div class="seg" role="radiogroup" :aria-label="t('登录角色')">
                <i class="seg-slider" aria-hidden="true" :style="{ transform: `translateX(${roleIndex * 100}%)` }"></i>
                <button type="button" class="seg-btn" :class="{ on: form.role === 'student' }" @click="form.role = 'student'">{{ t('学生') }}</button>
                <button type="button" class="seg-btn" :class="{ on: form.role === 'teacher' }" @click="form.role = 'teacher'">{{ t('教师') }}</button>
                <button type="button" class="seg-btn" :class="{ on: form.role === 'admin' }" @click="form.role = 'admin'">{{ t('管理员') }}</button>
              </div>

              <label class="lbl" for="login-no">{{ t('账号') }}<em>{{ t('学号 / 工号 / 管理员账号') }}</em></label>
              <div class="field">
                <i class="field-dot" aria-hidden="true"></i>
                <input id="login-no" type="text" v-model="form.no" :placeholder="t('请输入学号或工号')" autocomplete="username" required @input="loginErr = ''">
              </div>

              <label class="lbl" for="login-pwd">{{ t('密码') }}<em>PASSCODE</em></label>
              <div class="field">
                <i class="field-dot" aria-hidden="true"></i>
                <input id="login-pwd" :type="pwdVisible ? 'text' : 'password'" v-model="form.password"
                       :placeholder="t('请输入密码')" autocomplete="current-password" required
                       @keyup="capsCheck" @keydown="capsCheck" @blur="capsOn = false" @input="loginErr = ''">
                <button type="button" class="eye-btn" :aria-label="pwdVisible ? t('隐藏密码') : t('显示密码')" @click="pwdVisible = !pwdVisible">
                  <svg class="glyph" aria-hidden="true"><use :href="pwdVisible ? '#i-eye' : '#i-eye-off'"/></svg>
                </button>
              </div>
              <p v-if="capsOn" class="caps-tip">{{ t('大写锁定已开启') }}</p>

              <label class="lbl" for="login-captcha">{{ t('验证码') }}<em>{{ t('点击图片可刷新') }}</em></label>
              <div class="captcha-row">
                <div class="field" :class="{ 'is-err': errField === 'captcha' }">
                  <i class="field-dot" aria-hidden="true"></i>
                  <input id="login-captcha" type="text" v-model="form.captcha" :placeholder="t('请输入验证码')"
                         maxlength="4" autocomplete="off" required @input="loginErr = ''; errField = ''">
                </div>
                <canvas ref="captchaRef" class="captcha-canvas" width="112" height="44"
                        :title="t('点击刷新验证码')" @click="drawCaptcha"></canvas>
                <button type="button" class="captcha-refresh" :title="t('换一张')" :aria-label="t('换一张')" @click="drawCaptcha">
                  <svg class="glyph" aria-hidden="true"><use href="#i-refresh"/></svg>
                </button>
              </div>

              <div class="remember-row">
                <label class="remember-label"><input type="checkbox" v-model="remember"> {{ t('记住登录状态') }}</label>
                <a class="forgot-link" tabindex="0" @click="showRecovery = true" @keydown.enter.prevent="showRecovery = true">{{ t('忘记密码？') }}</a>
              </div>

              <p v-if="loginErr" class="form-err" role="alert"><i></i>{{ loginErr }}</p>

              <button type="submit" id="login-btn" class="btn-enter" :class="{ 'is-ok': loginOk }" :disabled="globing">
                <span class="btn-txt" v-if="loginOk">{{ t('欢迎回来') }}</span>
                <span class="btn-txt" v-else-if="globing">{{ t('核验中…') }}</span>
                <span class="btn-txt" v-else>{{ t('登录') }}</span>
                <svg v-if="loginOk" class="btn-check" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M5 12.6l4.4 4.4L19 7.4" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/></svg>
                <span v-else class="btn-arrow" aria-hidden="true">→</span>
              </button>

              <div class="dev-quick" v-if="isDev">
                <span class="dev-quick-label">{{ t('快捷登录：') }}</span>
                <button type="button" class="lc-quick" @click="quickLogin?.('student')">{{ t('学生') }}</button>
                <button type="button" class="lc-quick" @click="quickLogin?.('teacher')">{{ t('老师') }}</button>
                <button type="button" class="lc-quick" @click="quickLogin?.('admin')">{{ t('管理员') }}</button>
              </div>

              <div class="auth-alt">
                <span>{{ t('首次使用？') }}</span>
                <button type="button" class="alt-link" @click="tab = 'register'">{{ t('注册账号') }}<span class="alt-arrow">→</span></button>
              </div>
            </form>
          </template>

          <!-- ===== 注册 ===== -->
          <form v-else class="auth-form reg-form" @submit.prevent="doRegister">
            <div class="card-head">
              <div class="card-kicker">{{ t('建立选课档案') }}</div>
              <h2 class="card-title">{{ t('注册账号') }}</h2>
              <p class="card-sub">{{ t('录入你的信息，建立选课档案') }}</p>
            </div>

            <div class="reg-grid">
              <div class="reg-item"><label>{{ t('姓名') }}</label><div class="field slim"><input type="text" v-model="reg.name" required></div></div>
              <div class="reg-item"><label>{{ t('学号') }}</label><div class="field slim"><input type="text" v-model="reg.no" :placeholder="t('请输入学号')" required></div></div>
              <div class="reg-item span2"><label>{{ t('密码') }}</label>
                <div class="field slim">
                  <input :type="regPwdVisible ? 'text' : 'password'" v-model="reg.password" required>
                  <button type="button" class="eye-btn" :aria-label="regPwdVisible ? t('隐藏密码') : t('显示密码')" @click="regPwdVisible = !regPwdVisible">
                    <svg class="glyph" aria-hidden="true"><use :href="regPwdVisible ? '#i-eye' : '#i-eye-off'"/></svg>
                  </button>
                </div>
              </div>
              <div class="reg-item"><label>{{ t('学院') }}</label>
                <div class="field slim"><select v-model="reg.college"><option v-for="c in collegeOptions" :key="c" :value="c">{{ c }}</option></select></div>
              </div>
              <div class="reg-item"><label>{{ t('年级') }}</label>
                <div class="field slim"><select v-model="reg.grade"><option v-for="g in GRADES" :key="g" :value="g">{{ g }}</option></select></div>
              </div>
              <div class="reg-item"><label>{{ t('专业') }}</label><div class="field slim">
                <select v-if="optsLoaded" v-model="reg.major" :disabled="!majorOptions.length">
                  <option value="" disabled>{{ majorOptions.length ? t('请选择专业') : t('该学院暂无可选专业') }}</option>
                  <option v-for="m in majorOptions" :key="m" :value="m">{{ m }}</option>
                </select>
                <input v-else type="text" v-model="reg.major" :placeholder="t('如：软件工程')">
              </div></div>
              <div class="reg-item"><label>{{ t('班级') }}</label><div class="field slim">
                <select v-if="optsLoaded" v-model="reg.classNo" :disabled="!classOptions.length">
                  <option value="" disabled>{{ classOptions.length ? t('请选择班级') : t('请先选择专业') }}</option>
                  <option v-for="c in classOptions" :key="c" :value="c">{{ c }}</option>
                </select>
                <input v-else type="text" v-model="reg.classNo" :placeholder="t('如：软工2301班')">
              </div></div>
              <div class="reg-item span2"><label>{{ t('联系电话') }}</label><div class="field slim"><input type="text" v-model="reg.phone" :placeholder="t('请输入手机号')"></div></div>
            </div>

            <button type="submit" class="btn-enter"><span class="btn-txt">{{ t('提交注册') }}</span><span class="btn-arrow">→</span></button>
            <div class="auth-alt">
              <span>{{ t('已有账号？') }}</span>
              <button type="button" class="alt-link" @click="tab = 'login'">{{ t('返回登录') }}<span class="alt-arrow">→</span></button>
            </div>
          </form>

          <div class="card-foot"><span>{{ t('智学选课 · 学域星图') }}</span><span>{{ t('安全连接') }}</span></div>
        </section>
      </section>
    </main>

    <!-- ============ 页脚 ============ -->
    <footer class="foot">
      <span class="foot-l">© 2026 {{ t('智学选课') }} · {{ t('大学生课程管理平台') }}</span>
      <span class="foot-mid">{{ t('课程星图 / COURSE ATLAS') }}</span>
      <span class="foot-r">{{ t('选课数据实时同步') }}<i></i></span>
    </footer>

    <!-- ============ 找回密码 ============ -->
    <transition name="notice">
      <div v-if="showRecovery" class="notice-mask" @click.self="showRecovery = false">
        <div class="notice-card" role="dialog" :aria-label="t('找回密码')">
          <button type="button" class="notice-close" :aria-label="t('关闭')" @click="showRecovery = false">×</button>
          <div class="notice-kicker">{{ t('账号恢复') }}</div>
          <h3 class="notice-title">{{ t('找回密码') }}</h3>
          <p class="notice-sub">{{ t('本系统不存储明文密码，找回需由管理员重置。两分钟搞定：') }}</p>
          <div class="notice-step">
            <i class="notice-n">1</i>
            <span>{{ t('联系你的') }}<b>{{ t('辅导员或系统管理员') }}</b>{{ t('（班级群 / 教务处均可），提供你的') }}<b>{{ t('学号与姓名') }}</b>。</span>
          </div>
          <div class="notice-step">
            <i class="notice-n">2</i>
            <span>{{ t('管理员在') }}<b>「{{ t('管理端 → 用户管理') }}」</b>{{ t('中找到你的账号，点击') }}<b>「{{ t('重置密码') }}」</b>{{ t('——初始密码为') }}<b> 123456</b>{{ t('（管理员也可指定其他密码）。') }}</span>
          </div>
          <div class="notice-step">
            <i class="notice-n">3</i>
            <span>{{ t('用初始密码登录本系统，前往个人中心立即修改为新密码。重置不会影响你的选课数据与成绩。') }}</span>
          </div>
          <p class="notice-acct" v-if="form.no">{{ t('你的账号：') }}<b>{{ form.no }}</b>{{ t('（联系管理员时提供这一位即可）') }}</p>
          <button type="button" class="btn-enter notice-back" @click="showRecovery = false"><span class="btn-txt">{{ t('我知道了，返回登录') }}</span><span class="btn-arrow">→</span></button>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted, onUnmounted, getCurrentInstance } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import api from '../api'
import { encryptPassword } from '../utils/rsa'
import { COLLEGES } from '../utils/colleges'
import { t, getLocale, setLocale } from '@/i18n'
import { homePath } from '@/utils/home'
import AtlasScene from '@/components/AtlasScene.vue'

const router = useRouter()
const auth = useAuthStore()
const { proxy } = getCurrentInstance()!

const tab = ref<'login' | 'register'>('login')
const showRecovery = ref(false)
const pwdVisible = ref(false)
const regPwdVisible = ref(false)
const globing = ref(false)
const loginOk = ref(false)
const loginErr = ref('')
const errField = ref<'' | 'captcha'>('')
const rolling = ref(false)
const remember = ref(true)

/* 中 / EN 切换；切换时清掉旧语言生成的错误文案 */
const lang = ref<'zh' | 'en'>(getLocale())
function switchLang(l: 'zh' | 'en') { lang.value = l; setLocale(l); loginErr.value = ''; errField.value = '' }

const form = reactive({ no: '', password: '', captcha: '', role: 'student' })
const reg = reactive({ name: '', no: '', password: '', college: '计算机学院', grade: '2026', major: '', classNo: '', phone: '' })
const GRADES = ['2021', '2022', '2023', '2024', '2025', '2026']

// 注册下拉：学院 → 专业 → 班级 三级联动，选项聚合自数据库学生档案 + 专业字典（只能选已有值）
const optsLoaded = ref(false)
const regOpts = ref<{ colleges: string[]; majors: Record<string, string[]>; classes: Record<string, string[]> }>({ colleges: [], majors: {}, classes: {} })
const collegeOptions = computed(() => (optsLoaded.value && regOpts.value.colleges.length ? regOpts.value.colleges : COLLEGES))
const majorOptions = computed(() => regOpts.value.majors[reg.college] || [])
const classOptions = computed(() => {
  const all = regOpts.value.classes[`${reg.college}|${reg.major}`] || []
  // 班名自带入学年份(如 软工202601班),按所选年级过滤;该年级暂无班名时回退全量
  const inGrade = all.filter(c => c.includes(reg.grade))
  return inGrade.length ? inGrade : all
})
watch(() => reg.college, () => { reg.major = ''; reg.classNo = '' })
watch(() => reg.major, () => { reg.classNo = '' })
watch(() => reg.grade, () => { reg.classNo = '' })

// 学号自动推断年级:前 4 位为完整年份(2023010101 → 2023),否则前 2 位为年级尾数(250391209 → 2025)
watch(() => reg.no, (no) => {
  const digits = (no || '').replace(/\D/g, '')
  if (digits.length < 2) return
  let g: string | null = null
  if (digits.length >= 4 && GRADES.includes(digits.slice(0, 4))) g = digits.slice(0, 4)
  if (!g) {
    const y2 = '20' + digits.slice(0, 2)
    if (GRADES.includes(y2)) g = y2
  }
  if (g && g !== reg.grade) reg.grade = g
})

const roleIndex = computed(() => ({ student: 0, teacher: 1, admin: 2 }[form.role as 'student' | 'teacher' | 'admin'] ?? 0))

// === 页眉时钟 ===
const clockText = ref('')
let clockTimer = 0
function tickClock() {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  clockText.value = `${d.getFullYear()}.${p(d.getMonth() + 1)}.${p(d.getDate())}  ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

// === Caps Lock 检测 ===
const capsOn = ref(false)
function capsCheck(e: KeyboardEvent) {
  if (e.getModifierState) capsOn.value = e.getModifierState('CapsLock')
}

// === 验证码 Canvas：深墨底 + 青玉/纸白/暖金字 ===
const captchaRef = ref<HTMLCanvasElement | null>(null)
const captchaCode = ref('')
function drawCaptcha() {
  if (!captchaRef.value) return
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
  captchaCode.value = ''
  // 演示/联调辅助:DEV 构建且 URL 带 ?demo=1 时验证码固定为 8888(生产构建不受影响)
  if (import.meta.env.DEV && window.location.search.includes('demo=1')) {
    captchaCode.value = '8888'
  } else {
    for (let i = 0; i < 4; i++) captchaCode.value += chars.charAt(Math.floor(Math.random() * chars.length))
  }
  const cv = captchaRef.value
  const ctx = cv.getContext('2d')!
  ctx.clearRect(0, 0, cv.width, cv.height)

  const bg = ctx.createLinearGradient(0, 0, cv.width, cv.height)
  bg.addColorStop(0, '#0d2330')
  bg.addColorStop(1, '#0a1926')
  ctx.fillStyle = bg
  ctx.fillRect(0, 0, cv.width, cv.height)

  ctx.strokeStyle = 'rgba(174, 214, 214, .08)'
  ctx.lineWidth = 1
  for (let x = 0; x < cv.width; x += 14) { ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, cv.height); ctx.stroke() }
  for (let y = 0; y < cv.height; y += 14) { ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(cv.width, y); ctx.stroke() }
  for (let i = 0; i < 3; i++) {
    ctx.strokeStyle = 'rgba(116, 228, 196,' + (0.08 + Math.random() * 0.14) + ')'
    ctx.beginPath()
    ctx.moveTo(Math.random() * cv.width, Math.random() * cv.height)
    ctx.lineTo(Math.random() * cv.width, Math.random() * cv.height)
    ctx.stroke()
  }

  const palette = ['#74e4c4', '#eaf4f1', '#e7c58c', '#b8dcd6']
  for (let i = 0; i < captchaCode.value.length; i++) {
    ctx.save()
    ctx.font = '700 25px Consolas, "Courier New", monospace'
    ctx.fillStyle = palette[i % palette.length]
    ctx.translate(16 + i * 23, 29)
    ctx.rotate((Math.random() - 0.5) * 0.3)
    ctx.fillText(captchaCode.value[i], -7, 6)
    ctx.restore()
  }
  for (let i = 0; i < 22; i++) {
    ctx.fillStyle = 'rgba(116, 228, 196, 0.2)'
    ctx.fillRect(Math.random() * cv.width, Math.random() * cv.height, 1.4, 1.4)
  }
  if (import.meta.env.DEV) (window as any).__cap = captchaCode.value
}

// === 登录 ===
function prefersReduced() {
  return window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false
}
/* 验证码防爆破：连续错 5 次 → 锁定 60 秒（验证码是前端 canvas 自画的,后端限流器看不到它） */
const CAPTCHA_FAIL_MAX = 5
const CAPTCHA_LOCK_SECONDS = 60
const captchaFails = ref(0)
const captchaLockLeft = ref(0)
let captchaLockTimer = 0

function startCaptchaLock() {
  captchaLockLeft.value = CAPTCHA_LOCK_SECONDS
  loginErr.value = t('验证码错误次数过多，已锁定 {n} 秒', { n: captchaLockLeft.value })
  errField.value = 'captcha'
  if (captchaLockTimer) window.clearInterval(captchaLockTimer)
  captchaLockTimer = window.setInterval(() => {
    captchaLockLeft.value--
    if (captchaLockLeft.value <= 0) {
      window.clearInterval(captchaLockTimer)
      captchaLockLeft.value = 0
      captchaFails.value = 0
      loginErr.value = ''
      errField.value = ''
    } else {
      loginErr.value = t('验证码错误次数过多，已锁定 {n} 秒', { n: captchaLockLeft.value })
    }
  }, 1000)
}

async function doLogin() {
  if (!form.no || !form.password || !form.captcha) return
  if (captchaLockLeft.value > 0) {
    loginErr.value = t('验证码错误次数过多，已锁定 {n} 秒', { n: captchaLockLeft.value })
    errField.value = 'captcha'
    return
  }
  if (form.captcha.toUpperCase() !== captchaCode.value.toUpperCase()) {
    captchaFails.value++
    errField.value = 'captcha'
    drawCaptcha()
    if (captchaFails.value >= CAPTCHA_FAIL_MAX) {
      captchaFails.value = 0
      startCaptchaLock()
    } else {
      loginErr.value = t('验证码错误')
    }
    return
  }
  captchaFails.value = 0
  loginErr.value = ''
  errField.value = ''
  globing.value = true
  loginOk.value = false
  try {
    const r = await api.post('/auth/login', { no: form.no, password: await encryptPassword(form.password), role: form.role })
    if (r.success) {
      auth.setToken(r.data.token)
      auth.setUser(r.data.user)
      if (remember.value) localStorage.setItem('zhixue_remember', form.no)
      /* 成功：卡片转亮 → 星图收束 → 按角色跳转 */
      loginOk.value = true
      if (prefersReduced()) { router.push(homePath(auth.user?.role)); return }
      flowTimers.push(window.setTimeout(() => {
        rolling.value = true
        flowTimers.push(window.setTimeout(() => { router.push(homePath(auth.user?.role)) }, 620))
      }, 760))
    } else {
      globing.value = false
      loginOk.value = false
      loginErr.value = r.message || t('登录失败')
      drawCaptcha()
    }
  } catch (e: any) {
    globing.value = false
    loginOk.value = false
    loginErr.value = e?.response?.data?.message || e?.message || t('登录失败')
    drawCaptcha()
  }
}
const flowTimers: number[] = []

const isDev = !!import.meta.env.DEV
const quickLogin: ((role: 'student' | 'teacher' | 'admin') => void) | null = import.meta.env.DEV
  ? (role) => {
      const map = { student: '2023010101', teacher: 'T001', admin: 'admin' }
      form.no = map[role]
      form.password = role === 'admin' ? 'admin' : '123456'
      form.captcha = ''
      form.role = role
      loginErr.value = ''
      errField.value = ''
      ;(document.querySelector('#login-captcha') as HTMLInputElement)?.focus()
    }
  : null

async function doRegister() {
  try {
    const payload = { ...reg, password: await encryptPassword(reg.password) }
    const r = await api.post('/auth/register', payload)
    if (r.success) {
      ;(proxy as any).$message?.success(t('注册成功，请登录'))
      tab.value = 'login'
      form.no = reg.no
      form.password = ''
    } else {
      ;(proxy as any).$message?.error(r.message || t('注册失败'))
    }
  } catch (e: any) {
    ;(proxy as any).$message?.error(e?.response?.data?.message || t('注册失败'))
  }
}

// === 数据带：真实接口聚合（/courses 匿名可访问） ===
const statsState = ref<'loading' | 'ok' | 'err'>('loading')
const platStats = reactive({ courses: 0, applied: 0, teachers: 0 })
const dataBand = computed(() => [
  { key: 'courses', label: '在开课程', value: platStats.courses },
  { key: 'applied', label: '累计报名', value: platStats.applied },
  { key: 'teachers', label: '开课教师', value: platStats.teachers },
])
function countUp(target: number, apply: (v: number) => void, dur = 700) {
  if (prefersReduced() || dur <= 0) { apply(target); return }
  const t0 = performance.now()
  const step = (now: number) => {
    const p = Math.min(1, (now - t0) / dur)
    apply(Math.round(target * (1 - Math.pow(1 - p, 3))))
    if (p < 1) requestAnimationFrame(step)
  }
  requestAnimationFrame(step)
}
function loadPlatformStats() {
  statsState.value = 'loading'
  api.get('/courses').then((r: any) => {
    if (!r?.success) { statsState.value = 'err'; return }
    const list: any[] = r.data || []
    const teachers = new Set(list.map((c: any) => c.teacherId).filter(Boolean)).size
    const applied = list.reduce((s: number, c: any) => s + (c?.stat?.total || 0), 0)
    statsState.value = 'ok'
    countUp(list.length, v => { platStats.courses = v }, 700)
    window.setTimeout(() => countUp(applied, v => { platStats.applied = v }, 700), 140)
    window.setTimeout(() => countUp(teachers, v => { platStats.teachers = v }, 700), 280)
  }).catch(() => { statsState.value = 'err' })
}

let prevTheme: string | null = null
onMounted(() => {
  prevTheme = document.documentElement.getAttribute('data-theme')
  document.documentElement.setAttribute('data-theme', 'dark')
  document.body.classList.add('atlas-login')
  drawCaptcha()
  loadPlatformStats()
  tickClock()
  /* 注册下拉选项:学院→专业→班级聚合自学生档案+专业字典;失败时降级回常量/文本输入 */
  api.get('/auth/register-options').then((r: any) => {
    if (r?.success && r.data) {
      regOpts.value = { colleges: r.data.colleges || [], majors: r.data.majors || {}, classes: r.data.classes || {} }
      optsLoaded.value = true
    }
  }).catch(() => {})
  clockTimer = window.setInterval(tickClock, 1000)
  const rem = localStorage.getItem('zhixue_remember')
  if (rem) { form.no = rem; remember.value = true }
})
onUnmounted(() => {
  document.body.classList.remove('atlas-login')
  if (prevTheme === null) document.documentElement.removeAttribute('data-theme')
  else document.documentElement.setAttribute('data-theme', prevTheme)
  clearInterval(clockTimer)
  if (captchaLockTimer) clearInterval(captchaLockTimer)
  flowTimers.forEach((id) => clearTimeout(id))
  flowTimers.length = 0
})
</script>

<style scoped>
/* ==========================================================================
   智学选课 · 学域星图主题（Atlas）
   色板：墨夜 #0A1621 / 深靛 #0F2430 / 青玉 #74E4C4 / 暖砂金 #E7C58C
         纸白 #EAF4F1 / 雾灰 #93AAB2
   字体：标题衬线（书卷气）、正文黑体、数字等宽
   结构：不对称左右分栏；3D 星图是唯一的记忆点，其余保持克制
   ========================================================================== */
* { box-sizing: border-box; }

.atlas-page {
  --night: #0a1621;
  --ink: #0f2430;
  --jade: #74e4c4;
  --jade-deep: #2f9c8c;
  --gold: #e7c58c;
  --paper: #eaf4f1;
  --mist: #93aab2;
  --mist-2: #67838d;
  --line: rgba(174, 214, 214, .16);
  --line-2: rgba(174, 214, 214, .3);
  --red: #ff8a7a;
  --serif: "Noto Serif SC", "Source Han Serif SC", "Songti SC", "STSong", "SimSun", serif;
  --sans: "PingFang SC", "Microsoft YaHei", "Source Han Sans CN", -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  --mono: "DIN Alternate", "Bahnschrift", "Consolas", ui-monospace, "Courier New", monospace;
  position: relative;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--night);
  color: var(--paper);
  color-scheme: dark;
  font-family: var(--sans);
  overflow: hidden;
  isolation: isolate;
}
.atlas-page :where(button, a, input, select, b[tabindex]):focus-visible { outline: 1px solid var(--jade); outline-offset: 3px; }
.atlas-page :where(input, select):focus-visible { outline: none; }
.glyph { width: 17px; height: 17px; fill: none; stroke: currentColor; stroke-width: 1.9; stroke-linecap: round; stroke-linejoin: round; }

/* ============ 背景叠层：canvas 之上补一层极淡的极光与颗粒 ============ */
.atlas-aurora {
  position: absolute; inset: 0; z-index: 1; pointer-events: none;
  background:
    /* 压住 bloom 在画面上半部泛出的灰雾，保留深墨蓝的通透 */
    linear-gradient(180deg, rgba(10, 22, 33, .52) 0%, rgba(10, 22, 33, .18) 34%, transparent 56%),
    radial-gradient(ellipse 58% 62% at 30% 52%, rgba(116, 228, 196, .035), transparent 72%),
    radial-gradient(ellipse 44% 50% at 82% 18%, rgba(231, 197, 140, .035), transparent 76%),
    linear-gradient(90deg, rgba(10, 22, 33, .12) 0%, transparent 36%, rgba(10, 22, 33, .42) 76%, rgba(10, 22, 33, .68) 100%);
}
.atlas-grain {
  position: absolute; inset: 0; z-index: 1; pointer-events: none; opacity: .35;
  background-image: radial-gradient(rgba(234, 244, 241, .07) .6px, transparent .6px);
  background-size: 5px 5px;
}

/* ============ 页眉 ============ */
.masthead {
  position: relative; z-index: 4; flex: none;
  height: 84px; padding: 0 clamp(24px, 4vw, 68px);
  display: flex; align-items: center; justify-content: space-between;
  border-bottom: 1px solid var(--line);
  animation: mast-in .8s cubic-bezier(.2,.7,.2,1) both;
}
@keyframes mast-in { from { opacity: 0; transform: translateY(-12px); } to { opacity: 1; transform: none; } }
.brand { display: flex; align-items: center; gap: 14px; }
.brand-sigil { position: relative; width: 34px; height: 34px; flex: none; }
.brand-sigil i { position: absolute; display: block; }
.brand-sigil i:nth-child(1) { inset: 0; border: 1px solid rgba(116, 228, 196, .55); border-radius: 50%; }
.brand-sigil i:nth-child(2) { inset: 7px; border: 1px solid rgba(231, 197, 140, .7); border-radius: 50%; transform: rotate(-24deg) scaleX(.62); }
.brand-sigil i:nth-child(3) { left: 14px; top: 14px; width: 6px; height: 6px; border-radius: 50%; background: var(--jade); box-shadow: 0 0 12px var(--jade); }
.brand-text { display: flex; flex-direction: column; gap: 4px; }
.brand-cn { font-family: var(--serif); font-size: 19px; font-weight: 600; letter-spacing: .18em; line-height: 1; }
.brand-en { font-family: var(--mono); font-size: 9.5px; letter-spacing: .22em; color: var(--mist-2); }
.mast-right { display: flex; align-items: center; gap: clamp(16px, 2.4vw, 34px); font-family: var(--mono); font-size: 11px; letter-spacing: .1em; color: var(--mist); }
.live { display: inline-flex; align-items: center; gap: 8px; color: var(--jade); font-family: var(--sans); font-size: 11px; letter-spacing: .08em; }
.live i { width: 5px; height: 5px; border-radius: 50%; background: var(--jade); box-shadow: 0 0 10px var(--jade); animation: breathe 2.4s ease-in-out infinite; }
@keyframes breathe { 0%, 100% { opacity: 1; } 50% { opacity: .3; } }
.clock { font-variant-numeric: tabular-nums; }
.lang-sw { display: flex; align-items: center; gap: 7px; padding-left: 20px; border-left: 1px solid var(--line); }
.lang-sw i { font-style: normal; color: var(--mist-2); }
.lang-sw button { border: 0; background: none; padding: 4px 2px; cursor: pointer; font: inherit; color: var(--mist); border-bottom: 1px solid transparent; }
.lang-sw button.on { color: var(--jade); border-bottom-color: var(--jade); }

/* ============ 主区 ============ */
.atlas-main {
  position: relative; z-index: 3; flex: 1; min-height: 0;
  display: grid; grid-template-columns: 1.06fr minmax(390px, 468px);
  align-items: center; gap: clamp(28px, 5vw, 96px);
  padding: 30px clamp(30px, 5.4vw, 100px) 30px clamp(34px, 6.4vw, 118px);
}
.atlas-main.is-reg { grid-template-columns: .7fr minmax(480px, 660px); }
/* 注册卡更宽，背后星图容易透出来糊成一片：把玻璃压得更实 */
.atlas-main.is-reg .auth-card { background: linear-gradient(155deg, rgba(16, 36, 48, .94), rgba(9, 21, 31, .97)); }

/* ---- 左：系统识别 ---- */
.intro { position: relative; isolation: isolate; min-width: 0; text-shadow: 0 2px 18px rgba(3, 12, 18, .82); animation: intro-in .95s cubic-bezier(.2,.7,.2,1) .12s both; }
/* 文字区局部压暗 3D 星图，保证标题与读数始终可读 */
.intro::before {
  content: ""; position: absolute; inset: -14% -10% -14% -14%; z-index: -1; pointer-events: none;
  background: radial-gradient(ellipse 78% 72% at 36% 44%, rgba(10, 22, 33, .68), rgba(10, 22, 33, .24) 58%, transparent 100%);
}
@keyframes intro-in { from { opacity: 0; transform: translateY(20px); } to { opacity: 1; transform: none; } }
.intro-season { margin: 0 0 22px; font-size: 12px; letter-spacing: .16em; color: var(--gold); }
.intro-season::before { content: ""; display: inline-block; width: 22px; height: 1px; margin-right: 10px; vertical-align: middle; background: var(--gold); opacity: .7; }
.intro-title {
  margin: 0; font-family: var(--serif); font-size: clamp(58px, 7vw, 112px);
  line-height: 1.06; font-weight: 600; letter-spacing: .08em; color: var(--paper);
  text-shadow: 0 6px 48px rgba(116, 228, 196, .13);
}
.intro-title span { color: rgba(116, 228, 196, .12); -webkit-text-stroke: 1.1px rgba(203, 246, 231, .92); text-shadow: 0 0 30px rgba(116, 228, 196, .28); }
.intro-lead { margin: 18px 0 0; font-family: var(--serif); font-size: clamp(17px, 1.8vw, 25px); letter-spacing: .1em; color: var(--jade); }
.intro-desc { margin: 20px 0 0; font-size: 13.5px; line-height: 2; letter-spacing: .06em; color: var(--mist); }
.intro-desc span { font-size: 12px; color: #8daab2; letter-spacing: .1em; }
.star-rule { display: flex; align-items: center; gap: 7px; margin: 30px 0 26px; width: min(300px, 70%); }
.star-rule i { height: 1px; flex: 1; background: linear-gradient(90deg, rgba(116, 228, 196, .45), rgba(116, 228, 196, .04)); }
.star-rule i:nth-child(3) { flex: none; width: 5px; height: 5px; border-radius: 50%; background: var(--gold); box-shadow: 0 0 10px rgba(231, 197, 140, .7); }

.metrics { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 0; max-width: 500px; }
.metric { position: relative; padding: 4px 22px 4px 0; display: flex; flex-direction: column; gap: 7px; }
.metric + .metric { padding-left: 24px; border-left: 1px solid var(--line); }
.metric-n { font-family: var(--serif); font-size: clamp(30px, 3vw, 44px); font-weight: 600; line-height: 1; color: var(--paper); font-variant-numeric: tabular-nums; }
.metric-n.is-na { color: var(--mist-2); }
.metric-l { font-size: 11.5px; letter-spacing: .14em; color: var(--mist); }
.metric-skeleton { height: 32px; width: 62%; background: var(--line); border-radius: 2px; animation: breathe 1.6s ease-in-out infinite; }
.intro-note { margin: 34px 0 0; max-width: 460px; font-family: var(--serif); font-size: 13px; line-height: 2; letter-spacing: .08em; color: #8aa5ad; }

/* ---- 右：玻璃身份卡 ---- */
.auth-wrap { display: flex; justify-content: flex-end; min-width: 0; animation: card-in .95s cubic-bezier(.2,.7,.2,1) .24s both; }
@keyframes card-in { from { opacity: 0; transform: translateY(24px); } to { opacity: 1; transform: none; } }
.auth-card {
  position: relative; width: 100%; overflow: hidden;
  padding: 30px clamp(24px, 2.6vw, 36px) 20px;
  border: 1px solid var(--line-2);
  border-radius: 20px;
  background: linear-gradient(155deg, rgba(20, 44, 57, .78), rgba(10, 24, 35, .88));
  backdrop-filter: blur(22px) saturate(130%);
  -webkit-backdrop-filter: blur(22px) saturate(130%);
  box-shadow: 0 30px 90px rgba(2, 10, 16, .6), inset 0 1px 0 rgba(234, 244, 241, .08);
  transition: border-color .35s ease, box-shadow .35s ease, transform .35s ease;
}
.auth-card.is-err { border-color: rgba(255, 138, 122, .6); }
.auth-card.is-ok { border-color: rgba(116, 228, 196, .75); box-shadow: 0 30px 90px rgba(2, 10, 16, .6), 0 0 60px rgba(116, 228, 196, .16); }
.card-sheen {
  position: absolute; left: -30%; top: -60%; width: 70%; height: 160%; pointer-events: none;
  background: linear-gradient(90deg, transparent, rgba(116, 228, 196, .05), transparent);
  transform: rotate(18deg);
}
.card-busy { position: absolute; left: 0; right: 0; bottom: 0; height: 2px; overflow: hidden; opacity: 0; transition: opacity .2s; }
.auth-card.is-busy .card-busy { opacity: 1; }
.card-busy i { display: block; height: 100%; width: 45%; background: linear-gradient(90deg, transparent, var(--jade), transparent); animation: sweep 1.25s linear infinite; }
@keyframes sweep { from { transform: translateX(-110%); } to { transform: translateX(320%); } }

.card-head { margin-bottom: 24px; }
.card-kicker { font-size: 11px; letter-spacing: .2em; color: var(--jade); opacity: .85; }
.card-title { margin: 10px 0 0; font-family: var(--serif); font-size: 30px; font-weight: 600; letter-spacing: .1em; line-height: 1.25; }
.card-sub { margin: 8px 0 0; font-size: 12px; letter-spacing: .06em; color: var(--mist); }

/* ---- 表单 ---- */
.auth-form { display: block; }
.seg { position: relative; display: grid; grid-template-columns: repeat(3, 1fr); gap: 2px; padding: 4px; margin-bottom: 22px; border: 1px solid var(--line); border-radius: 12px; background: rgba(4, 14, 22, .4); }
.seg-slider { position: absolute; top: 4px; bottom: 4px; left: 4px; width: calc((100% - 8px) / 3); border-radius: 9px; background: linear-gradient(135deg, rgba(116, 228, 196, .22), rgba(116, 228, 196, .1)); border: 1px solid rgba(116, 228, 196, .38); box-shadow: 0 0 22px rgba(116, 228, 196, .1); transition: transform .28s cubic-bezier(.2,.8,.2,1); will-change: transform; }
.seg-btn { position: relative; z-index: 1; height: 34px; border: 0; background: none; cursor: pointer; font: inherit; font-size: 13px; letter-spacing: .12em; color: var(--mist); transition: color .2s; }
.seg-btn.on { color: var(--jade); font-weight: 700; }

.lbl { display: flex; align-items: baseline; justify-content: space-between; gap: 10px; margin: 0 0 8px; font-size: 12px; letter-spacing: .1em; color: var(--paper); }
.lbl em { font-family: var(--mono); font-style: normal; font-size: 9.5px; letter-spacing: .12em; color: var(--mist-2); }
.field {
  position: relative; display: flex; align-items: center; gap: 9px;
  height: 46px; padding: 0 12px 0 15px; margin-bottom: 16px;
  background: rgba(4, 16, 25, .42);
  border: 1px solid var(--line);
  border-radius: 11px;
  transition: border-color .22s, background-color .22s, box-shadow .22s;
}
.field:hover { border-color: var(--line-2); }
.field:focus-within { border-color: rgba(116, 228, 196, .72); background: rgba(116, 228, 196, .055); box-shadow: 0 0 0 3px rgba(116, 228, 196, .09); }
.field-dot { flex: none; width: 4px; height: 4px; border-radius: 50%; background: var(--mist-2); transition: background .2s, box-shadow .2s; }
.field:focus-within .field-dot { background: var(--jade); box-shadow: 0 0 8px var(--jade); }
.field input, .field select {
  flex: 1; min-width: 0; height: 100%; border: 0; outline: 0; background: transparent;
  font: inherit; font-size: 13.5px; color: var(--paper); letter-spacing: .04em;
  font-family: var(--mono);
}
.field input::placeholder { color: var(--mist-2); font-family: var(--sans); letter-spacing: .02em; }
.field select { cursor: pointer; }
.field select option { background: var(--ink); color: var(--paper); }
.field.is-err { border-color: var(--red); box-shadow: 0 0 0 3px rgba(255, 138, 122, .09); }
.field.slim { height: 42px; margin-bottom: 0; }
.eye-btn { flex: none; width: 30px; height: 30px; display: grid; place-items: center; border: 0; background: none; color: var(--mist); cursor: pointer; }
.eye-btn:hover { color: var(--jade); }
.caps-tip { margin: -8px 0 12px; font-size: 11px; letter-spacing: .08em; color: var(--gold); }
.captcha-row { display: grid; grid-template-columns: 1fr auto auto; gap: 8px; margin-bottom: 16px; }
.captcha-row .field { margin-bottom: 0; min-width: 0; }
.captcha-canvas { display: block; width: 112px; height: 44px; border: 1px solid var(--line); border-radius: 10px; cursor: pointer; background: #0d2330; }
.captcha-refresh { width: 44px; height: 44px; display: grid; place-items: center; border: 1px solid var(--line); border-radius: 10px; background: rgba(4, 16, 25, .4); color: var(--mist); cursor: pointer; transition: border-color .2s, color .2s; }
.captcha-refresh:hover { border-color: var(--jade); color: var(--jade); }
.remember-row { display: flex; align-items: center; justify-content: space-between; margin: 2px 0 18px; font-size: 12px; color: var(--mist); }
.remember-label { display: flex; align-items: center; gap: 8px; cursor: pointer; user-select: none; }
.remember-label input { width: 14px; height: 14px; accent-color: var(--jade); cursor: pointer; }
.forgot-link { color: var(--jade); cursor: pointer; text-decoration: none; border-bottom: 1px solid transparent; }
.forgot-link:hover { border-bottom-color: var(--jade); }
.form-err { display: flex; align-items: center; gap: 8px; margin: -6px 0 14px; font-size: 12px; color: var(--red); }
.form-err i { flex: none; width: 5px; height: 5px; border-radius: 50%; background: var(--red); }

/* ---- 主按钮：青玉渐变，圆角，流光 ---- */
.btn-enter {
  position: relative; width: 100%; height: 50px; overflow: hidden;
  display: inline-flex; align-items: center; justify-content: center; gap: 12px;
  background: linear-gradient(110deg, #4fc5aa, #7fe9cb 52%, #a9f0d8);
  color: #08201d;
  border: 0; border-radius: 12px; cursor: pointer;
  font: inherit; font-size: 15px; font-weight: 700; letter-spacing: .22em; text-indent: .22em;
  box-shadow: 0 10px 30px rgba(79, 197, 170, .22);
  transition: transform .15s, box-shadow .25s, filter .25s;
}
.btn-enter::before {
  content: ""; position: absolute; inset: 0; pointer-events: none;
  background: linear-gradient(100deg, transparent 30%, rgba(255, 255, 255, .38) 50%, transparent 70%);
  transform: translateX(-120%);
}
.btn-enter:hover::before { transform: translateX(120%); transition: transform .75s ease; }
.btn-enter:hover { filter: brightness(1.06); box-shadow: 0 14px 38px rgba(79, 197, 170, .34); }
.btn-enter:active { transform: translateY(1px); }
.btn-enter:disabled { cursor: default; }
.btn-enter.is-ok { background: linear-gradient(110deg, #a9f0d8, #e7c58c); }
.btn-txt, .btn-arrow, .btn-check { position: relative; z-index: 1; }
.btn-arrow { font-size: 17px; font-weight: 400; letter-spacing: 0; }
.btn-check { width: 20px; height: 20px; }

.dev-quick { display: flex; align-items: center; gap: 7px; margin-top: 10px; font-size: 11px; color: var(--mist-2); flex-wrap: wrap; }
.dev-quick-label { flex: none; }
.lc-quick { height: 24px; padding: 0 10px; border: 1px solid var(--line); border-radius: 7px; background: none; color: var(--mist); font: inherit; font-size: 11px; cursor: pointer; }
.lc-quick:hover { border-color: var(--jade); color: var(--jade); }
.auth-tip { margin-top: 8px; font-size: 10px; letter-spacing: .08em; color: var(--mist-2); }
.auth-alt { display: flex; align-items: center; justify-content: center; flex-wrap: wrap; gap: 8px; margin-top: 14px; font-size: 12px; color: var(--mist-2); }
.alt-link { display: inline-flex; align-items: center; gap: 6px; min-height: 30px; padding: 4px 8px; font-family: inherit; font-size: 12.5px; font-weight: 600; line-height: 1; letter-spacing: .04em; color: var(--jade); cursor: pointer; background: none; border: none; border-radius: 6px; transition: color .2s, background .2s; }
.alt-link:hover { color: #a5f0da; background: rgba(116, 228, 196, .08); }
.alt-link:focus-visible { outline: 2px solid var(--jade); outline-offset: 2px; }
.alt-link .alt-arrow { font-size: 13px; transition: transform .2s; }
.alt-link:hover .alt-arrow { transform: translateX(3px); }
.card-foot { display: flex; justify-content: space-between; margin-top: 16px; padding-top: 12px; border-top: 1px solid var(--line); font-size: 10px; letter-spacing: .14em; color: #8aa5ad; }

/* ---- 注册双列 ---- */
.reg-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px 16px; margin-bottom: 20px; }
.reg-item { display: flex; flex-direction: column; gap: 7px; min-width: 0; }
.reg-item label { font-size: 11px; letter-spacing: .1em; color: var(--mist); }
.span2 { grid-column: span 2; }

/* ============ 页脚 ============ */
.foot {
  position: relative; z-index: 4; flex: none; height: 46px;
  display: flex; align-items: center; justify-content: space-between; gap: 20px;
  padding: 0 clamp(24px, 4vw, 68px);
  border-top: 1px solid var(--line);
  font-size: 10px; letter-spacing: .12em; color: var(--mist-2);
}
.foot-mid { font-family: var(--serif); letter-spacing: .18em; color: var(--mist); }
.foot-r { display: inline-flex; align-items: center; gap: 8px; }
.foot-r i { width: 4px; height: 4px; border-radius: 50%; background: var(--jade); box-shadow: 0 0 8px var(--jade); }

/* ============ 找回密码弹窗 ============ */
.notice-mask { position: fixed; inset: 0; z-index: 60; display: grid; place-items: center; padding: 20px; background: rgba(4, 12, 19, .76); backdrop-filter: blur(6px); }
.notice-card { position: relative; width: min(540px, 100%); max-height: 86vh; overflow: auto; padding: 32px 34px 30px; border: 1px solid var(--line-2); border-radius: 18px; background: linear-gradient(155deg, rgba(20, 44, 57, .97), rgba(10, 24, 35, .98)); box-shadow: 0 34px 100px rgba(0, 0, 0, .68); }
.notice-close { position: absolute; right: 20px; top: 16px; border: 0; background: none; font-size: 22px; line-height: 1; color: var(--mist); cursor: pointer; }
.notice-close:hover { color: var(--jade); }
.notice-kicker { font-size: 11px; letter-spacing: .2em; color: var(--jade); }
.notice-title { margin: 10px 0 0; font-family: var(--serif); font-size: 24px; font-weight: 600; letter-spacing: .1em; }
.notice-sub { margin: 10px 0 6px; font-size: 12.5px; color: var(--mist); line-height: 1.9; }
.notice-step { display: flex; gap: 13px; margin-top: 15px; font-size: 13px; line-height: 1.9; color: var(--paper); }
.notice-step b { color: var(--jade); font-weight: 600; }
.notice-n { flex: none; width: 23px; height: 23px; margin-top: 3px; display: grid; place-items: center; border: 1px solid rgba(116, 228, 196, .5); border-radius: 50%; color: var(--jade); font-family: var(--mono); font-size: 11px; font-style: normal; }
.notice-acct { margin: 18px 0 0; font-size: 12px; color: var(--mist); }
.notice-acct b { color: var(--paper); font-family: var(--mono); }
.notice-back { margin-top: 22px; height: 44px; font-size: 13px; }

.notice-enter-active { transition: opacity .24s ease; }
.notice-enter-active .notice-card { transition: transform .24s cubic-bezier(.16,1,.3,1); }
.notice-enter-from { opacity: 0; }
.notice-enter-from .notice-card { transform: translateY(12px) scale(.99); }
.notice-leave-active { transition: opacity .16s ease-in; }
.notice-leave-to { opacity: 0; }

/* ============ 登录成功：整页上卷退场 ============ */
.is-leaving .masthead, .is-leaving .atlas-main, .is-leaving .foot { animation: none; transition: transform .6s cubic-bezier(.5,.05,.35,1), opacity .6s ease; transform: translateY(-105%); opacity: 0; }
.is-leaving .atlas-main { transition-delay: .07s; }
.is-leaving .foot { transition-delay: .13s; }

/* ============ 响应式 ============ */
@media (max-height: 900px) {
  .masthead { height: 62px; }
  .atlas-main { padding-top: 8px; padding-bottom: 8px; }
  .intro-season { margin-bottom: 10px; }
  .intro-title { font-size: clamp(48px, 5.8vw, 90px); }
  .intro-lead { margin-top: 10px; }
  .intro-desc { margin-top: 12px; }
  .star-rule { margin: 16px 0 14px; }
  .intro-note { margin-top: 16px; }
  .auth-card { padding: 20px clamp(22px, 2.4vw, 32px) 14px; }
  .card-head { margin-bottom: 10px; }
  .card-title { font-size: 26px; }
  .card-sub { margin-top: 5px; }
  .seg { margin-bottom: 10px; }
  .seg-btn { height: 30px; }
  .lbl { margin-bottom: 5px; }
  .field { height: 40px; margin-bottom: 9px; }
  .captcha-row, .field.slim { margin-bottom: 9px; }
  .captcha-canvas, .captcha-refresh { height: 40px; }
  .captcha-canvas { width: 104px; }
  .captcha-refresh { width: 40px; }
  .remember-row { margin: 0 0 10px; }
  .btn-enter { height: 44px; }
  .dev-quick { margin-top: 8px; }
  .auth-alt { margin-top: 12px; }
  .card-foot { margin-top: 10px; padding-top: 8px; }
  .foot { height: 38px; }
}
@media (max-height: 700px) { .atlas-page { min-height: 0; overflow: auto; } .intro-note { display: none; } }
@media (max-width: 1180px) {
  .atlas-page { overflow: auto; }
  /* 窄屏首屏必须能看到主按钮：压缩标题区，而不是让用户先滚一屏 */
  .atlas-main, .atlas-main.is-reg { grid-template-columns: 1fr; justify-items: center; gap: 16px; padding: 14px clamp(20px, 6vw, 60px) 24px; }
  .intro { width: min(680px, 100%); }
  .intro-season { margin-bottom: 8px; font-size: 11px; }
  .intro-title { font-size: clamp(38px, 6.6vw, 58px); }
  .intro-lead { margin-top: 8px; font-size: clamp(16px, 2.2vw, 19px); }
  .intro-desc { margin-top: 8px; font-size: 12.5px; line-height: 1.65; }
  .intro-desc span { font-size: 11px; }
  .star-rule { margin: 12px 0 10px; }
  .metric { gap: 4px; }
  .metric-n { font-size: 30px; }
  .intro-note { display: none; }
  .auth-wrap, .atlas-main.is-reg .auth-wrap { width: min(520px, 100%); justify-content: center; }
  .auth-card { padding: 22px clamp(22px, 3vw, 32px) 16px; }
  .card-head { margin-bottom: 16px; }
  .dev-quick { display: none; }
  .card-foot { margin-top: 12px; padding-top: 10px; }
  .atlas-aurora { background: linear-gradient(180deg, rgba(10, 22, 33, .28), rgba(10, 22, 33, .8)); }
  .atlas-page :deep(.atlas-scene) { opacity: .58; }
}
@media (max-width: 719px) {
  .mast-right .live, .clock { display: none; }
  .brand-cn { font-size: 16px; }
  .brand-en { display: none; }
  .reg-grid { grid-template-columns: 1fr; }
  .span2 { grid-column: auto; }
  .captcha-row { grid-template-columns: 1fr auto; }
  .captcha-refresh { display: none; }
  .foot-l, .foot-mid { display: none; }
  .foot { justify-content: flex-end; }
  .metrics { max-width: 100%; }
  .metric + .metric { padding-left: 16px; }
}

/* 单列 + 矮视口（如 1024×768）：进一步压缩，保证主按钮留在首屏 */
@media (max-width: 1180px) and (max-height: 820px) {
  .atlas-main, .atlas-main.is-reg { gap: 10px; padding-top: 8px; padding-bottom: 12px; }
  .intro-season, .intro-desc, .card-sub, .dev-quick { display: none; }
  .intro-title { font-size: clamp(34px, 5vw, 46px); }
  .intro-lead { margin-top: 4px; font-size: 15px; }
  .star-rule { margin: 8px 0 6px; }
  .metric-n { font-size: 25px; }
  .metric-l { font-size: 10px; }
  .metric { gap: 3px; }
  .auth-card { padding: 16px 24px 12px; }
  .card-head { margin-bottom: 10px; }
  .card-title { font-size: 24px; }
  .seg { margin-bottom: 10px; }
  .seg-btn { height: 30px; }
  .lbl { margin-bottom: 5px; }
  .field { height: 40px; margin-bottom: 8px; }
  .captcha-row { margin-bottom: 8px; }
  .captcha-canvas, .captcha-refresh { height: 38px; }
  .captcha-canvas { width: 100px; }
  .captcha-refresh { width: 38px; }
  .remember-row { margin: 0 0 8px; }
  .btn-enter { height: 42px; }
  .auth-alt { margin-top: 10px; }
  .card-foot { margin-top: 8px; padding-top: 8px; }
  .foot { height: 32px; }
}

/* ============ 减少动态效果 ============ */
@media (prefers-reduced-motion: reduce) {
  .masthead, .intro, .auth-wrap { animation: none; }
  .live i, .metric-skeleton, .card-busy i { animation: none; }
  .btn-enter::before { display: none; }
  .seg-slider, .field, .btn-enter, .auth-card { transition: none; }
}
</style>

<style>
/* 登录页专属：统一底色，压掉 original.css 的浅色渐变 */
body.atlas-login { background: #0a1621 !important; }
body.atlas-login::before { opacity: 0 !important; }
body.atlas-login::after { opacity: 0 !important; }
</style>
