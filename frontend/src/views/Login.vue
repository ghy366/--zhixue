<template>
  <div class="auth-page">
    <!-- ===== 深空背景层：星云 / 星点 / 底部星球地平线（纯装饰，pointer-events 全关） ===== -->
    <div class="sp-bg" aria-hidden="true">
      <i class="sp-neb sp-neb-a"></i>
      <i class="sp-neb sp-neb-b"></i>
      <i class="sp-neb sp-neb-c"></i>
      <i class="sp-neb sp-neb-d"></i>
      <i class="sp-neb sp-neb-e"></i>
      <i class="sp-neb sp-neb-f"></i>
      <i class="sp-neb sp-neb-g"></i>
      <i class="sp-streak"></i>
      <div class="sp-stars">
        <i v-for="(s, i) in spStars" :key="i" :class="{ 'sp-flare': s.f }"
           :style="{ left: s.x + '%', top: s.y + '%', width: s.r * 2 + 'px', height: s.r * 2 + 'px', opacity: s.o, animationDelay: s.d + 's', animationDuration: s.dur + 's', background: s.c }"></i>
      </div>
      <div class="sp-meteors" aria-hidden="true">
        <i class="sp-meteor" style="left: 63%; top: 7%; animation-delay: 0s;"></i>
        <i class="sp-meteor" style="left: 90%; top: 4%; animation-delay: 3.4s;"></i>
        <i class="sp-meteor" style="left: 34%; top: 11%; animation-delay: 6.8s;"></i>
        <i class="sp-meteor" style="left: 78%; top: 15%; animation-delay: 10.2s;"></i>
        <i class="sp-meteor" style="left: 12%; top: 18%; animation-delay: 1.8s; animation-duration: 15s;"></i>
        <i class="sp-meteor" style="left: 52%; top: 3%; animation-delay: 8.6s; animation-duration: 11.4s;"></i>
        <i class="sp-meteor" style="left: 96%; top: 21%; animation-delay: 5.1s; animation-duration: 14.2s;"></i>
      </div>
      <div class="sp-daytint" :style="dayTintStyle" aria-hidden="true"></div>
      <svg class="sp-con sp-con-a" viewBox="0 0 220 150" aria-hidden="true">
        <g class="con-lines">
          <line x1="18" y1="122" x2="66" y2="84"/>
          <line x1="66" y1="84" x2="124" y2="98"/>
          <line x1="124" y1="98" x2="170" y2="50"/>
          <line x1="170" y1="50" x2="206" y2="72"/>
          <line x1="124" y1="98" x2="148" y2="134"/>
        </g>
        <g class="con-stars">
          <circle cx="18" cy="122" r="2.4"/>
          <circle cx="66" cy="84" r="2"/>
          <circle cx="124" cy="98" r="2.6"/>
          <circle cx="170" cy="50" r="2.2"/>
          <circle cx="206" cy="72" r="1.8"/>
          <circle cx="148" cy="134" r="1.8"/>
        </g>
      </svg>
      <svg class="sp-con sp-con-b" viewBox="0 0 200 130" aria-hidden="true">
        <g class="con-lines">
          <line x1="22" y1="36" x2="78" y2="22"/>
          <line x1="78" y1="22" x2="132" y2="40"/>
          <line x1="132" y1="40" x2="178" y2="26"/>
          <line x1="78" y1="22" x2="96" y2="78"/>
        </g>
        <g class="con-stars">
          <circle cx="22" cy="36" r="2.2"/>
          <circle cx="78" cy="22" r="2.6"/>
          <circle cx="132" cy="40" r="2"/>
          <circle cx="178" cy="26" r="2.2"/>
          <circle cx="96" cy="78" r="1.8"/>
        </g>
      </svg>
      <div class="sp-horizon"></div>
      <div class="sp-haze"></div>
      <div class="sp-vig"></div>
    </div>

    <div class="auth-shell">
      <!-- ==================== 左侧：地球 + 时钟 + 平台数据 ==================== -->
      <div class="auth-left">
        <div class="auth-brand">
          <div class="logo-icon">智</div>
          <span class="auth-brand-name">学选课</span>
        </div>

        <header class="lc-head">
          <h1 class="lc-title">
            <i class="lc-tline" aria-hidden="true"></i>
            <span>{{ t('智慧选课 · 时空感知') }}</span>
            <i class="lc-tline" aria-hidden="true"></i>
          </h1>
          <p class="lc-sub">{{ t('实时状态 · 选课动态 · 数据同步') }}</p>
        </header>

        <!-- 3D 地球 + 双星环（Three.js，全部由 EarthScene 负责） -->
        <div class="lc-globe">
          <EarthScene />
        </div>

        <!-- 科技数字时钟（真实系统时间，逐位滚动） -->
        <DigitalClock class="lc-clock" />

        <!-- 平台实时数据（公开课程接口聚合，禁止造假） -->
        <div class="lc-stats">
          <div class="lc-stat">
            <span class="lc-sic" aria-hidden="true">
              <svg viewBox="0 0 24 24"><path d="M11.15 5.05C9.55 3.85 7.1 3.35 4.5 3.35c-.75 0-1.15.42-1.15 1.15v12.3c0 .73.4 1.15 1.15 1.15 2.6 0 5.05.5 6.65 1.7z"/><path d="M12.85 5.05c1.6-1.2 4.05-1.7 6.65-1.7.75 0 1.15.42 1.15 1.15v12.3c0 .73-.4 1.15-1.15 1.15-2.6 0-5.05.5-6.65 1.7z"/></svg>
            </span>
            <span class="lc-sbody">
              <b>{{ platStats.courses }}</b>
              <em><i>+</i>{{ t('在开课程') }}</em>
            </span>
          </div>
          <div class="lc-stat">
            <span class="lc-sic" aria-hidden="true">
              <svg viewBox="0 0 24 24"><circle cx="9.1" cy="8" r="3.35"/><path d="M2.9 19.3c0-3.05 2.8-5.15 6.2-5.15s6.2 2.1 6.2 5.15c0 .66-.44 1.05-1.14 1.05H4.04c-.7 0-1.14-.39-1.14-1.05z"/><circle cx="17.5" cy="8.75" r="2.6"/><path d="M16.5 14.4c2.75.12 4.85 1.98 4.85 4.4 0 .55-.34.9-.9.9h-2.9c.14-2.05-.38-3.95-1.05-5.3z"/></svg>
            </span>
            <span class="lc-sbody">
              <b>{{ platStats.applied }}</b>
              <em><i>+</i>{{ t('累计报名') }}</em>
            </span>
          </div>
          <div class="lc-stat">
            <span class="lc-sic" aria-hidden="true">
              <svg viewBox="0 0 24 24"><circle cx="12" cy="7.5" r="3.55"/><path d="M4.55 19.55c0-3.4 3.33-5.8 7.45-5.8s7.45 2.4 7.45 5.8c0 .75-.5 1.15-1.25 1.15H5.8c-.75 0-1.25-.4-1.25-1.15z"/></svg>
            </span>
            <span class="lc-sbody">
              <b>{{ platStats.teachers }}</b>
              <em><i>+</i>{{ t('开课教师') }}</em>
            </span>
          </div>
        </div>
      </div>

      <!-- ==================== 右侧：玻璃登录卡 ==================== -->
      <div class="auth-right">
        <div class="auth-card">
          <div class="auth-card-head">
            <h2>
              <svg class="lc-spark" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M12 2.2l1.85 5.15L19 9.2l-5.15 1.85L12 16.2l-1.85-5.15L5 9.2l5.15-1.85z"/>
                <path d="M18.9 15.2l.8 2.1 2.1.8-2.1.8-.8 2.1-.8-2.1-2.1-.8 2.1-.8z"/>
              </svg>
              {{ t('欢迎登录') }}
            </h2>
            <p><i aria-hidden="true"></i><span>{{ t('请使用您的账号登录系统') }}</span><i aria-hidden="true"></i></p>
          </div>

          <div class="auth-tabs">
            <button type="button" class="auth-tab" :class="{ active: tab === 'login' }" @click="tab = 'login'">
              <svg class="at-ic" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="8" r="3.6"/><path d="M4.8 20.4c0-3.9 3.2-6.6 7.2-6.6s7.2 2.7 7.2 6.6"/></svg>
              {{ t('登录') }}
            </button>
            <button type="button" class="auth-tab" :class="{ active: tab === 'register' }" @click="tab = 'register'">
              <svg class="at-ic" viewBox="0 0 24 24" aria-hidden="true"><path d="M4 20l3.6-.8L19 7.8a2.1 2.1 0 0 0-3-3L4.8 16.2z"/><path d="M14.6 6.2l3.2 3.2"/></svg>
              {{ t('注册') }}
            </button>
          </div>

          <form v-if="tab === 'login'" class="auth-form" @submit.prevent="doLogin">
            <div class="form-group input-icon">
              <svg class="icon input-left-icon"><use href="#i-user"/></svg>
              <input type="text" v-model="form.no" :placeholder="t('学号 / 工号 / 管理员账号')" autocomplete="username" required>
            </div>

            <div class="form-group input-icon">
              <svg class="icon input-left-icon"><use href="#i-lock"/></svg>
              <input :type="pwdVisible ? 'text' : 'password'" v-model="form.password"
                     :placeholder="t('请输入密码')" autocomplete="current-password" required
                     @keyup="capsCheck" @keydown="capsCheck" @blur="capsOn = false">
              <button type="button" class="pwd-eye" :aria-label="pwdVisible ? t('隐藏密码') : t('显示密码')" @click="pwdVisible = !pwdVisible">
                <svg class="icon"><use :href="pwdVisible ? '#i-eye' : '#i-eye-off'"/></svg>
              </button>
              <span v-if="capsOn" class="caps-tip">⚠ 大写锁定已开启</span>
            </div>

            <div class="form-group captcha-row">
              <div class="input-icon captcha-input">
                <svg class="icon input-left-icon"><use href="#i-check-circle"/></svg>
                <input id="login-captcha" type="text" v-model="form.captcha" :placeholder="t('请输入验证码')"
                       maxlength="4" autocomplete="off" required>
              </div>
              <canvas ref="captchaRef" class="captcha-canvas" width="112" height="44"
                      :title="t('点击刷新验证码')" @click="drawCaptcha"></canvas>
              <button type="button" class="captcha-refresh" :title="t('换一张')" :aria-label="t('换一张')" @click="drawCaptcha">
                <svg class="icon"><use href="#i-refresh"/></svg>
              </button>
            </div>

            <div class="remember-row">
              <label class="remember-label"><input type="checkbox" v-model="remember"> {{ t('记住登录状态') }}</label>
              <a class="forgot-link" tabindex="0" @click="showRecovery = true" @keydown.enter.prevent="showRecovery = true">{{ t('忘记密码？') }}</a>
            </div>

            <button type="submit" id="login-btn" class="lc-submit" :class="{ 'is-busy': globing, 'is-ok': loginOk }">
              <span class="lc-sbtn-text">
                <svg class="lc-arrow" viewBox="0 0 24 24" aria-hidden="true">
                  <circle cx="12" cy="12" r="9.6" fill="#ecfffd" stroke="none"/>
                  <path d="M7.8 12h7.1M12.3 9.2l2.9 2.8-2.9 2.8" stroke="#0d7d74" stroke-width="1.9" fill="none" stroke-linecap="round" stroke-linejoin="round"/>
                </svg>
                {{ t('登 录') }}
              </span>
              <span class="orb" aria-hidden="true">
                <i class="orb-wave"></i>
                <i class="orb-wave orb-wave-2"></i>
                <span class="orb-ring"></span>
                <span class="orb-core">
                  <svg class="orb-check" viewBox="0 0 24 24" fill="none"><path d="M5 12.6l4.4 4.4L19 7.4"/></svg>
                </span>
              </span>
            </button>

            <div class="quick-login" v-if="isDev">
              <span class="quick-login-label">{{ t('快捷登录：') }}</span>
              <button type="button" class="lc-quick" @click="quickLogin?.('student')">{{ t('学生') }}</button>
              <button type="button" class="lc-quick" @click="quickLogin?.('teacher')">{{ t('老师') }}</button>
              <button type="button" class="lc-quick" @click="quickLogin?.('admin')">{{ t('管理员') }}</button>
            </div>
            <div class="auth-tip" v-if="isDev">{{ t('开发环境：快捷登录已启用') }}</div>
          </form>

          <form v-else class="auth-form reg-form" @submit.prevent="doRegister">
            <div class="form-row">
              <div class="form-group"><label>{{ t('姓名') }}</label><input type="text" v-model="reg.name" required></div>
              <div class="form-group"><label>{{ t('学号') }}</label><input type="text" v-model="reg.no" required></div>
            </div>
            <div class="form-group"><label>{{ t('密码') }}</label><input type="password" v-model="reg.password" required></div>
            <div class="form-row">
              <div class="form-group"><label>{{ t('学院') }}</label>
                <select v-model="reg.college">
                  <option v-for="c in COLLEGES" :key="c" :value="c">{{ c }}</option>
                </select>
              </div>
              <div class="form-group"><label>{{ t('年级') }}</label>
                <select v-model="reg.grade">
                  <option>2021</option><option>2022</option><option>2023</option>
                  <option>2024</option><option>2025</option><option>2026</option>
                </select>
              </div>
            </div>
            <div class="form-row">
              <div class="form-group"><label>{{ t('专业') }}</label><input type="text" v-model="reg.major" :placeholder="t('如：软件工程')"></div>
              <div class="form-group"><label>{{ t('班级') }}</label><input type="text" v-model="reg.classNo" :placeholder="t('如：软工2301班')"></div>
            </div>
            <div class="form-group"><label>{{ t('联系电话') }}</label><input type="text" v-model="reg.phone" :placeholder="t('请输入手机号')"></div>
            <button type="submit" class="lc-submit">
              <span class="lc-sbtn-text">{{ t('注 册') }}</span>
            </button>
          </form>
        </div>
      </div>
    </div>

    <!-- 页面底部平台署名 -->
    <div class="auth-foot">
      <i aria-hidden="true"></i>
      <span>{{ t('智学选课 · 大学生课程管理平台') }}</span>
      <i aria-hidden="true"></i>
    </div>

    <!-- ==================== 登录成功欢迎层（保持原逻辑不变） ==================== -->
    <transition name="lw-fade">
      <div v-if="welcome" class="login-welcome" :class="{ 'is-campus': phase === 'campus' }">
        <div class="lw-campus" :class="{ show: campusVisible }">
          <CampusMap3D v-if="campusMounted" intro :items="[]" @ready="campusReady = true" />
          <div class="lw-vig"></div>
        </div>
        <div v-if="globeMounted" class="lw-globe" :class="{ hide: phase === 'campus' }">
          <WelcomeGlobe @level="lwLevel = $event" @done="landCampus" />
        </div>
        <div class="lw-inner" :class="{ zoom: lwLevel >= 1 }">
          <div class="lw-greet">{{ welcomeGreet }}</div>
          <h2 class="lw-title"><span v-for="(w, i) in lwWords" :key="i" class="lw-w" :style="{ animationDelay: (i * 0.06) + 's' }">{{ w }}</span></h2>
          <p v-if="campusVisible" class="lw-loc"><i></i>{{ t('已定位 · 厦门华厦学院') }}</p>
          <p v-else class="lw-hint"><i class="lw-hint-dot"></i>{{ lwHintText }}</p>
        </div>
        <ol class="lw-path" aria-hidden="true">
          <li v-for="(p, i) in LW_PATH" :key="p" class="lw-path-i" :class="{ on: lwLevel >= i + 1, cur: lwLevel === i + 1 }">
            <i class="lw-path-dot"></i><span>{{ t(p) }}</span>
          </li>
        </ol>
      </div>
    </transition>

    <!-- 找回密码引导弹窗 -->
    <transition name="modal-fade">
      <div v-if="showRecovery" class="reco-mask" @click.self="showRecovery = false">
        <div class="reco-modal">
          <button type="button" class="reco-close" :aria-label="t('关闭')" @click="showRecovery = false">×</button>
          <h3 class="reco-title">{{ t('找回密码') }}</h3>
          <p class="reco-sub">{{ t('本系统不存储明文密码，找回需由管理员重置。两分钟搞定：') }}</p>
          <div class="reco-step">
            <i class="reco-n">1</i>
            <span>{{ t('联系你的') }}<b>{{ t('辅导员或系统管理员') }}</b>{{ t('（班级群 / 教务处均可），提供你的') }}<b>{{ t('学号与姓名') }}</b>。</span>
          </div>
          <div class="reco-step">
            <i class="reco-n">2</i>
            <span>{{ t('管理员在') }}<b>「{{ t('管理端 → 用户管理') }}」</b>{{ t('中找到你的账号，点击') }}<b>「{{ t('重置密码') }}」</b>{{ t('——初始密码为') }}<b> 123456</b>{{ t('（管理员也可指定其他密码）。') }}</span>
          </div>
          <div class="reco-step">
            <i class="reco-n">3</i>
            <span>{{ t('用初始密码登录本系统，前往') }}<b>「{{ t('个人中心') }}」</b>{{ t('立即修改为新密码。重置不会影响你的选课数据与成绩。') }}</span>
          </div>
          <p class="reco-acct" v-if="form.no">{{ t('你的账号：') }}<b>{{ form.no }}</b>{{ t('（联系管理员时提供这一位即可）') }}</p>
          <button type="button" class="reco-back" @click="showRecovery = false">{{ t('我知道了，返回登录') }}</button>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, getCurrentInstance, defineAsyncComponent } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import api from '../api'
import { encryptPassword } from '../utils/rsa'
import { COLLEGES } from '../utils/colleges'
import EarthScene from '../components/login/EarthScene.vue'
import DigitalClock from '../components/login/DigitalClock.vue'
import WelcomeGlobe from '../components/WelcomeGlobe.vue'
/* 校园 3D 沙盘只在登录过场用一次，异步加载（与 three 共享 chunk，不拖慢登录首屏） */
const CampusMap3D = defineAsyncComponent(() => import('../components/CampusMap3D.vue'))
import { t } from '@/i18n'

const router = useRouter()
const auth = useAuthStore()
const { proxy } = getCurrentInstance()!

const tab = ref<'login'|'register'>('login')
const showRecovery = ref(false)
const pwdVisible = ref(false)
const globing = ref(false)
const loginOk = ref(false)
const welcome = ref(false)
const welcomeGreet = ref('')
/* B11 逐字点亮：标题按字符拆分，入读顺序依次激活 */
const lwWords = computed(() => t('欢迎进入智学选课系统').split(''))
/* 过场地球下钻路径（与 WelcomeGlobe 的 LEVELS 一一对应，第 5 格为落地校名） */
const LW_PATH = ['全国', '福建省', '厦门市', '集美区', '华厦学院'] as const
/* 点击提示文案：索引 = 当前地球层级 */
const LW_HINT = ['轻点地球 · 进入全国', '轻点 · 进入福建省', '轻点 · 进入厦门市', '轻点 · 进入集美区', '正在落地校园…'] as const
const lwLevel = ref(0)
const lwHintText = computed(() => t(LW_HINT[Math.min(lwLevel.value, LW_HINT.length - 1)]))
/* 过场两段：地球下钻 → 落地校园 */
const phase = ref<'globe' | 'campus'>('globe')
const campusMounted = ref(false)
const campusReady = ref(false)
const globeMounted = ref(false)
const campusVisible = computed(() => phase.value === 'campus' && campusReady.value)
const lwTimers: number[] = []
let lwWait = 0
/** 地球下钻到位（末级再点或闲置兜底）→ 落地校园。沙盘未就绪时顺延，最多重试 10 次后强制落地 */
function landCampus() {
  if (phase.value === 'campus') return
  if (!campusReady.value && lwWait++ < 10) {
    lwTimers.push(window.setTimeout(landCampus, 350))
    return
  }
  phase.value = 'campus'
  lwLevel.value = 5
  lwTimers.push(
    window.setTimeout(() => { globeMounted.value = false }, 1000),
    window.setTimeout(() => { router.push('/dashboard') }, 2600),
  )
}
const remember = ref(true)
const captchaRef = ref<HTMLCanvasElement | null>(null)
const captchaCode = ref('')

// === 星点（确定性伪随机，每次刷新布局一致，避免闪跳） ===
const spStars = (() => {
  let s = 20261102
  const rnd = () => { s = (s * 1103515245 + 12345) % 2147483648; return s / 2147483648 }
  /* 背景图自带星空，动态星尘做大面积加密补充：三层基础星 + 大号十字亮星 */
  const layers = [
    { n: 96, r: [0.3, 0.6], o: [0.05, 0.22] },
    { n: 46, r: [0.7, 1.1], o: [0.20, 0.42] },
    { n: 20, r: [1.3, 1.9], o: [0.45, 0.80] },
  ]
  /* 冷暖点缀：绝大多数冰青白，少量暖金与淡青，层次更接近真实星场 */
  const palette = ['#dffbff', '#dffbff', '#dffbff', '#9fefff', '#ffe3ba']
  const out: Array<{ x: number; y: number; r: number; o: number; d: number; c: string; dur: number; f: boolean }> = []
  for (const L of layers) {
    for (let i = 0; i < L.n; i++) {
      out.push({
        x: +(rnd() * 100).toFixed(2),
        y: +(rnd() * 100).toFixed(2),
        r: +(L.r[0] + rnd() * (L.r[1] - L.r[0])).toFixed(2),
        o: +(L.o[0] + rnd() * (L.o[1] - L.o[0])).toFixed(2),
        d: +(rnd() * 7).toFixed(2),
        c: palette[Math.floor(rnd() * palette.length)],
        dur: +(3.6 + rnd() * 4.6).toFixed(2),
        f: false,
      })
    }
  }
  /* 大号十字星芒：只放在上半屏，避免压住登录表单与地球主体 */
  for (let i = 0; i < 7; i++) {
    out.push({
      x: +(4 + rnd() * 92).toFixed(2),
      y: +(2 + rnd() * 44).toFixed(2),
      r: +(2.4 + rnd() * 1.3).toFixed(2),
      o: +(0.55 + rnd() * 0.35).toFixed(2),
      d: +(rnd() * 7).toFixed(2),
      c: i % 3 === 0 ? '#ffe9c9' : '#e6fbff',
      dur: +(2.8 + rnd() * 3).toFixed(2),
      f: true,
    })
  }
  return out
})()


// === 随时段的整页色调（真实时钟：清晨暖金 / 白天微亮 / 黄昏橙紫 / 深夜冷蓝）===
const dayTintStyle = (() => {
  const h = new Date().getHours()
  let tint = 'rgba(40, 90, 160, 0.18)'
  if (h >= 5 && h < 8) tint = 'rgba(255, 176, 92, 0.17)'
  else if (h >= 8 && h < 16) tint = 'rgba(140, 200, 255, 0.06)'
  else if (h >= 16 && h < 20) tint = 'rgba(255, 122, 69, 0.16)'
  return { background: `radial-gradient(120% 90% at 50% 0%, ${tint}, transparent 72%)` }
})()

// === Caps Lock 检测 ===
const capsOn = ref(false)
function capsCheck(e: KeyboardEvent) {
  if (e.getModifierState) capsOn.value = e.getModifierState('CapsLock')
}

// === 左侧选课轮次倒计时（沿用原有接口推导逻辑） ===
const roundData = reactive({
  state: 'idle' as 'active' | 'upcoming' | 'ended' | 'idle',
  round: 1, status: '选课未开放', label: '等待开放',
  days: 0, hours: 0, mins: 0, secs: 0,
  progress: 0, progressText: t('暂未开放'),
  showCount: false, hint: t('本轮选课尚未开放，开放后在此显示倒计时')
})
let roundTimer: number | null = null
function parseISO(s: string | null | undefined) {
  if (!s) return null
  const t = new Date(s); return isNaN(t.getTime()) ? null : t
}

const pad = (n: number) => n < 10 ? '0' + n : '' + n

// === 轮次时间轴：节点状态按各轮时间窗推导（数据全部来自现有接口） ===
const roundTimeline = ref<Array<{ round: number; state: 'done' | 'active' | 'todo' }>>([])
let tlSchedules: any[] = []
let tlConfig: any = {}
function buildTimeline() {
  const now = Date.now()
  const cur = Number(tlConfig?.currentRound || 1)
  const src = (tlSchedules || []).slice().sort((a: any, b: any) => Number(a.roundNo) - Number(b.roundNo))
  const list: Array<{ round: number; state: 'done' | 'active' | 'todo' }> = []
  if (!src.length) {
    list.push({ round: cur, state: tlConfig?.roundOpen ? 'active' : 'todo' })
  } else {
    for (const s of src) {
      const r = Number(s.roundNo)
      const o = parseISO(s.openAt)?.getTime() ?? null
      const cl = parseISO(s.closeAt)?.getTime() ?? null
      let st: 'done' | 'active' | 'todo'
      if (o !== null && cl !== null) st = now > cl ? 'done' : (now >= o ? 'active' : 'todo')
      else if (o !== null) st = now >= o ? 'active' : 'todo'
      else st = r < cur ? 'done' : (r === cur ? (tlConfig?.roundOpen ? 'active' : 'todo') : 'todo')
      if (r === cur && tlConfig?.roundOpen && st === 'done') st = 'active'
      if (r === cur && !tlConfig?.roundOpen && st === 'active') st = 'todo'
      list.push({ round: r, state: st })
    }
  }
  roundTimeline.value = list
}
const timelineStatusText = computed(() => {
  const nodes = roundTimeline.value
  if (!nodes.length) return t('选课尚未开放')
  const active = nodes.find(n => n.state === 'active')
  if (active) {
    return roundData.label === '不限时'
      ? t('第 {n} 轮选课进行中', { n: active.round })
      : t('第 {n} 轮选课进行中', { n: active.round })
  }
  const todo = nodes.find(n => n.state === 'todo')
  const lastDone = [...nodes].reverse().find(n => n.state === 'done')
  if (todo) {
    return lastDone
      ? t('第 {a} 轮已结束，等待第 {b} 轮开放', { a: lastDone.round, b: todo.round })
      : t('第 {n} 轮尚未开始', { n: todo.round })
  }
  return nodes.length > 1 ? t('共 {n} 轮选课均已结束', { n: nodes.length }) : t('本轮选课已结束')
})

/** 依据「时间窗 + 开关」推导横幅状态；无时间窗时回落到手动开关 */
function applyRoundBanner(c: any, schedules: any[]) {
  if (roundTimer) { clearInterval(roundTimer); roundTimer = null }
  const roundOpen = !!c?.roundOpen
  const round = Number(c?.currentRound || 1)
  roundData.round = round || 1
  tlConfig = c || {}
  tlSchedules = schedules || []

  const hit = (schedules || []).find((s: any) => Number(s?.roundNo) === roundData.round)
  const oa = parseISO(c?.roundOpenAt) || (hit ? parseISO(hit.openAt) : null)
  const ca = parseISO(c?.roundCloseAt) || (hit ? parseISO(hit.closeAt) : null)
  const openT = oa ? oa.getTime() : null
  const closeT = ca ? ca.getTime() : null

  const tick = () => {
    buildTimeline()
    const now = Date.now()
    let ms = 0
    if (openT !== null && closeT !== null && closeT > openT) {
      if (now < openT) {
        roundData.state = 'upcoming'; roundData.status = '即将开始'; roundData.label = '距本轮开放'
        roundData.progress = 0; roundData.progressText = t('尚未开始')
        roundData.hint = t('本轮选课尚未开始，开放后在此显示倒计时')
        ms = openT - now
      } else if (now <= closeT && roundOpen) {
        roundData.state = 'active'; roundData.status = '选课进行中'; roundData.label = '距本轮结束'
        roundData.progress = Math.min(100, Math.max(0, Math.round((now - openT) / (closeT - openT) * 100)))
        roundData.progressText = t('进度 {p}%', { p: roundData.progress })
        ms = closeT - now
      } else if (now <= closeT) {
        roundData.state = 'idle'; roundData.status = '选课未开放'; roundData.label = '等待开放'
        roundData.progress = 0; roundData.progressText = t('暂未开放')
        roundData.hint = t('选课时间窗内，但管理员暂未开放本轮选课')
        ms = 0
      } else {
        roundData.state = 'ended'; roundData.status = '本轮已结束'; roundData.label = '等待下一轮'
        roundData.progress = 100; roundData.progressText = t('已完成 100%')
        roundData.hint = t('本轮选课已结束，请等待下一轮开放')
        ms = 0
      }
    } else if (roundOpen) {
      roundData.state = 'active'; roundData.status = '选课进行中'; roundData.label = '不限时'
      roundData.progress = 0; roundData.progressText = t('进行中')
      roundData.hint = t('本轮选课进行中，管理员未设置截止时间')
      ms = 0
    } else {
      roundData.state = 'ended'; roundData.status = '本轮已结束'; roundData.label = '等待下一轮'
      roundData.progress = 100; roundData.progressText = t('已完成 100%')
      roundData.hint = t('本轮选课已结束，请等待下一轮开放')
      ms = 0
    }
    roundData.showCount = roundData.state === 'active' ? closeT !== null : roundData.state === 'upcoming'

    if (ms <= 0) {
      roundData.days = roundData.hours = roundData.mins = roundData.secs = 0
    } else {
      roundData.days = Math.floor(ms / 86400000)
      roundData.hours = Math.floor((ms % 86400000) / 3600000)
      roundData.mins = Math.floor((ms % 3600000) / 60000)
      roundData.secs = Math.floor((ms % 60000) / 1000)
    }
  }

  tick()
  roundTimer = window.setInterval(tick, 1000)
}

function loadLoginRoundBanner() {
  api.get('/system/config').then(async (r: any) => {
    const c = r?.success ? (r.data || {}) : {}
    let schedules: any[] = []
    try {
      const rs: any = await api.get('/system/round-schedule')
      const d = rs?.success ? (rs.data || {}) : (rs || {})
      schedules = Array.isArray(d?.schedules) ? d.schedules : []
    } catch { schedules = [] }
    applyRoundBanner(c, schedules)
  }).catch(() => {
    applyRoundBanner({}, [])
  })
}

const form = reactive({ no: '', password: '', captcha: '' })
const reg = reactive({ name: '', no: '', password: '', college: '计算机学院', grade: '2026', major: '', classNo: '', phone: '' })

// === 验证码 Canvas（4 位字符 + 干扰线 + 噪点，纯前端校验） ===
function drawCaptcha() {
  if (!captchaRef.value) return
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
  captchaCode.value = ''
  for (let i = 0; i < 4; i++) captchaCode.value += chars.charAt(Math.floor(Math.random() * chars.length))
  const cv = captchaRef.value
  const ctx = cv.getContext('2d')!
  ctx.clearRect(0, 0, cv.width, cv.height)

  // 浅米青底 + 深青字：深色卡片里保持可读，和参考图一致
  /* 与深色玻璃卡同步：深蓝底 + 青色字，避免白色块突兀 */
  const bg = ctx.createLinearGradient(0, 0, cv.width, cv.height)
  bg.addColorStop(0, '#0a2436')
  bg.addColorStop(1, '#071a28')
  ctx.fillStyle = bg
  ctx.fillRect(0, 0, cv.width, cv.height)

  for (let i = 0; i < 3; i++) {
    ctx.strokeStyle = 'rgba(95, 233, 221,' + (0.12 + Math.random() * 0.14) + ')'
    ctx.lineWidth = 1
    ctx.beginPath()
    ctx.moveTo(Math.random() * cv.width, Math.random() * cv.height)
    ctx.lineTo(Math.random() * cv.width, Math.random() * cv.height)
    ctx.stroke()
  }

  const palette = ['#7ff6e8', '#5fe9dd', '#a8f0ea', '#4fd8c8']
  for (let i = 0; i < captchaCode.value.length; i++) {
    ctx.save()
    ctx.font = '700 25px "Times New Roman", Georgia, serif'
    ctx.fillStyle = palette[i % palette.length]
    ctx.translate(16 + i * 23, 29)
    ctx.rotate((Math.random() - 0.5) * 0.36)
    ctx.fillText(captchaCode.value[i], -7, 6)
    ctx.restore()
  }

  for (let i = 0; i < 22; i++) {
    ctx.fillStyle = 'rgba(95, 233, 221, 0.22)'
    ctx.fillRect(Math.random() * cv.width, Math.random() * cv.height, 1.4, 1.4)
  }
  // 开发环境暴露验证码便于自动化测试（生产构建静态剥离）
  if (import.meta.env.DEV) (window as any).__cap = captchaCode.value
}

// === 登录 ===
async function doLogin() {
  if (!form.no || !form.password || !form.captcha) return
  if (form.captcha.toUpperCase() !== captchaCode.value.toUpperCase()) {
    ;(proxy as any).$message?.error(t('验证码错误'))
    drawCaptcha()
    return
  }
  globing.value = true
  loginOk.value = false
  try {
    const r = await api.post('/auth/login', { no: form.no, password: await encryptPassword(form.password) })
    if (r.success) {
      auth.setToken(r.data.token)
      auth.setUser(r.data.user)
      if (remember.value) localStorage.setItem('zhixue_remember', form.no)
      const role = r.data?.user?.role
      const h = new Date().getHours()
      const greetKey = h >= 5 && h < 11 ? '早上好' : h >= 11 && h < 14 ? '中午好' : h >= 14 && h < 18 ? '下午好' : '晚上好'
      welcomeGreet.value = `${t(greetKey)}，${t(role === 'admin' ? '管理员' : role === 'teacher' ? '老师' : '同学')}`
      loginOk.value = true
      setTimeout(() => {
        globing.value = false
        loginOk.value = false
        lwLevel.value = 0
        phase.value = 'globe'
        campusMounted.value = true
        campusReady.value = false
        globeMounted.value = true
        welcome.value = true
        lwWait = 0
        lwTimers.push(window.setTimeout(landCampus, 26000))
      }, 900)
    } else {
      globing.value = false
      loginOk.value = false
      ;(proxy as any).$message?.error(r.message || t('登录失败'))
      drawCaptcha()
    }
  } catch (e: any) {
    globing.value = false
    loginOk.value = false
    ;(proxy as any).$message?.error(e?.response?.data?.message || t('登录失败'))
    drawCaptcha()
  }
}

// 快捷登录仅开发环境可用：import.meta.env.DEV 在生产构建中被静态替换为 false
const isDev = !!import.meta.env.DEV
const quickLogin: ((role: 'student' | 'teacher' | 'admin') => void) | null = import.meta.env.DEV
  ? (role) => {
      const map = { student: '2023010101', teacher: 'T001', admin: 'admin' }
      form.no = map[role]
      form.password = role === 'admin' ? 'admin' : '123456'
      form.captcha = ''
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

const platStats = reactive({ courses: 0, applied: 0, teachers: 0 })
function countUp(target: number, apply: (v: number) => void, dur = 1200) {
  const t0 = performance.now()
  const step = (now: number) => {
    const p = Math.min(1, (now - t0) / dur)
    apply(Math.round(target * (1 - Math.pow(1 - p, 3))))
    if (p < 1) requestAnimationFrame(step)
  }
  requestAnimationFrame(step)
}
function loadPlatformStats() {
  api.get('/courses').then((r: any) => {
    const list = r?.success ? (r.data || []) : []
    const teachers = new Set(list.map((c: any) => c.teacherId).filter(Boolean)).size
    const applied = list.reduce((s: number, c: any) => s + ((c as any).stat?.total) || 0, 0)
    countUp(list.length, v => { platStats.courses = v })
    countUp(applied, v => { platStats.applied = v }, 1600)
    countUp(teachers, v => { platStats.teachers = v })
  }).catch(() => {})
}

onMounted(() => {
  // 深空主题：本页自带完整配色，同时让弹层/滚动条跟随深色
  document.documentElement.setAttribute('data-theme', 'dark')
  drawCaptcha()
  loadPlatformStats()
  const rem = localStorage.getItem('zhixue_remember')
  if (rem) { form.no = rem; remember.value = true }
})
onUnmounted(() => {
  if (roundTimer) clearInterval(roundTimer)
  lwTimers.forEach((id) => clearTimeout(id))
  lwTimers.length = 0
})
</script>

<style scoped>
/* ==========================================================================
   深空科技 · 全球数字孪生 —— 登录页
   色板：底 #061626 / #081C30，主色 #18D6C0，辅色 #43B9DF，标题 #EAFBFF，正文 #A9C5D2
   ========================================================================== */
* { box-sizing: border-box; }

.auth-page {
  --c-bg: #061626;
  --c-bg2: #081c30;
  --c-main: #18d6c0;
  --c-aux: #43b9df;
  --c-title: #eafbff;
  --c-body: #a9c5d2;
  --c-dim: #7b9aa9;
  --c-line: rgba(24, 214, 192, .22);
  --c-line-soft: rgba(24, 214, 192, .12);
  --card-w: 424px;

  position: relative;
  width: 100%;
  min-height: 100vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  color: var(--c-body);
  /* 原生控件（下拉框 / 滚动条）跟随深色，避免白底突兀 */
  color-scheme: dark;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "PingFang SC", "Microsoft YaHei", "Helvetica Neue", Arial, sans-serif;
  background:
    radial-gradient(900px 620px at 18% 8%, #0b2a42 0%, rgba(11, 42, 66, 0) 62%),
    radial-gradient(760px 560px at 86% 84%, #07263a 0%, rgba(7, 38, 58, 0) 64%),
    linear-gradient(168deg, #061626 0%, #071b2c 48%, #051320 100%);
  -webkit-font-smoothing: antialiased;
}
a { color: inherit; text-decoration: none; }
button { cursor: pointer; border: none; background: none; font: inherit; color: inherit; }
input, select { font: inherit; }

/* SVG 图标基线（尺寸在各处显式覆盖） */
.icon { width: 18px; height: 18px; display: block; }

/* ============================ 深空背景 ============================ */
.sp-bg { position: absolute; inset: 0; z-index: 0; pointer-events: none; overflow: hidden; }

/* 星云：四团柔光，缓慢漂移，绝不压到地球主体 */
.sp-neb {
  position: absolute;
  border-radius: 50%;
  filter: blur(70px);
  opacity: .46;
  mix-blend-mode: screen;
  animation: nebDrift 26s ease-in-out infinite alternate, nebPulse 17s ease-in-out infinite;
}
.sp-neb-a {
  width: 46vw; height: 30vw; left: -6vw; top: -6vw;
  background: radial-gradient(closest-side, rgba(24, 214, 192, .30), rgba(24, 214, 192, 0) 72%);
}
.sp-neb-b {
  width: 40vw; height: 26vw; left: 26vw; top: -10vw;
  background: radial-gradient(closest-side, rgba(67, 185, 223, .26), rgba(67, 185, 223, 0) 72%);
  animation-duration: 33s; animation-delay: -8s;
}
.sp-neb-c {
  width: 34vw; height: 34vw; right: -8vw; bottom: 2vw;
  background: radial-gradient(closest-side, rgba(24, 176, 214, .22), rgba(24, 176, 214, 0) 72%);
  animation-duration: 40s; animation-delay: -16s;
}
/* 右上角那团偏紫的星云，给画面一点冷色层次 */
.sp-neb-d {
  width: 30vw; height: 26vw; right: 4vw; top: -8vw;
  background: radial-gradient(closest-side, rgba(96, 150, 210, .18), rgba(96, 150, 210, 0) 72%);
  animation-duration: 46s, 21s; animation-delay: -22s, -6s;
  opacity: .5;
}
/* 补充星云：紫罗兰 / 深蓝 / 玫瑰，错落填空，透明度都压得很低 */
.sp-neb-e {
  width: 26vw; height: 20vw; left: 7vw; bottom: 5vw;
  background: radial-gradient(closest-side, rgba(138, 123, 246, .20), rgba(138, 123, 246, 0) 72%);
  animation-duration: 37s, 19s; animation-delay: -12s, -3s;
  opacity: .48;
}
.sp-neb-f {
  width: 38vw; height: 24vw; right: 15vw; top: 24vh;
  background: radial-gradient(closest-side, rgba(64, 140, 240, .16), rgba(64, 140, 240, 0) 72%);
  animation-duration: 44s, 23s; animation-delay: -5s, -11s;
  opacity: .40;
}
.sp-neb-g {
  width: 22vw; height: 16vw; left: 38vw; bottom: -4vw;
  background: radial-gradient(closest-side, rgba(255, 128, 216, .10), rgba(255, 128, 216, 0) 72%);
  animation-duration: 50s, 26s; animation-delay: -30s, -8s;
  opacity: .38;
}
@keyframes nebPulse {
  0%, 100% { filter: blur(70px) brightness(.9); }
  50%      { filter: blur(70px) brightness(1.22); }
}
@keyframes nebDrift {
  from { transform: translate3d(0, 0, 0) scale(1); }
  to   { transform: translate3d(4.6vw, 2.6vw, 0) scale(1.16); }
}
/* 右上的流星/星云流光 */
.sp-streak {
  position: absolute; right: -14vw; top: -16vh;
  width: 32vw; height: 38vh;
  transform: rotate(-34deg);
  background: linear-gradient(90deg,
    rgba(24, 214, 192, 0) 0%,
    rgba(24, 214, 192, .05) 46%,
    rgba(150, 246, 255, .34) 62%,
    rgba(230, 255, 255, .5) 66%,
    rgba(120, 236, 245, .18) 70%,
    rgba(24, 214, 192, 0) 82%);
  filter: blur(10px);
  opacity: .42;
}
/* 星点：随机位置 + 呼吸闪烁 */
.sp-stars { position: absolute; inset: 0; }
.sp-stars i {
  position: absolute;
  border-radius: 50%;
  background: #dffbff;
  box-shadow: 0 0 6px rgba(190, 245, 255, .9), 0 0 14px rgba(120, 220, 255, .45);
  animation: starBlink 5.2s ease-in-out infinite;
}
@keyframes starBlink {
  0%, 100% { opacity: .18; transform: scale(.8); }
  50%      { opacity: .95; transform: scale(1.15); }
}
/* 大号十字星芒：核心亮圆 + 横竖两条渐变光线，只给最亮的几颗 */
.sp-stars i.sp-flare {
  border-radius: 50%;
  background: radial-gradient(circle, #ffffff 0%, rgba(230, 251, 255, .85) 38%, rgba(160, 235, 255, 0) 74%);
  box-shadow: 0 0 10px rgba(190, 245, 255, .95), 0 0 26px rgba(120, 220, 255, .5);
}
.sp-stars i.sp-flare::before,
.sp-stars i.sp-flare::after {
  content: '';
  position: absolute;
}
.sp-stars i.sp-flare::before {
  left: -340%; right: -340%; top: 50%; height: 1px;
  transform: translateY(-50%);
  background: linear-gradient(90deg, rgba(255, 255, 255, 0), rgba(240, 253, 255, .95), rgba(255, 255, 255, 0));
}
.sp-stars i.sp-flare::after {
  top: -340%; bottom: -340%; left: 50%; width: 1px;
  transform: translateX(-50%);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0), rgba(240, 253, 255, .9), rgba(255, 255, 255, 0));
}
/* 随时段整页色调：真实时钟驱动的软光叠加 */
.sp-daytint { position: absolute; inset: 0; pointer-events: none; mix-blend-mode: soft-light; }
/* 星座连线：纯装饰，无悬停交互 */
.sp-con { position: absolute; pointer-events: none; opacity: .85;
  width: 0;
  animation: conFloat 16s ease-in-out infinite alternate; }
.sp-con-a { left: 2.5%; top: 25%; width: 230px; }
.sp-con-b { right: 20%; top: 2.5%; width: 200px; animation-duration: 21s; animation-delay: -7s; }
.con-lines line { stroke: rgba(190, 235, 255, .25); stroke-width: 1; }
.con-stars circle { fill: #e6f8ff; filter: drop-shadow(0 0 5px rgba(160, 230, 255, .8));
  animation: conTw 4.2s ease-in-out infinite; }
.con-stars circle:nth-child(2n) { animation-delay: -1.6s; }
.con-stars circle:nth-child(3n) { animation-delay: -2.7s; }
@keyframes conTw { 0%, 100% { opacity: .55; } 50% { opacity: 1; } }
@keyframes conFloat { from { transform: translateY(0); } to { transform: translateY(7px); } }
/* Caps Lock 提示 */
.caps-tip { position: absolute; right: 46px; top: 50%; transform: translateY(-50%);
  font-size: 12px; color: #f0b35c; white-space: nowrap; pointer-events: none; }
/* 找回密码引导 */
.reco-mask { position: fixed; inset: 0; z-index: 90; background: rgba(3, 12, 22, .6);
  backdrop-filter: blur(6px); display: flex; align-items: center; justify-content: center; padding: 24px; }
.reco-modal { position: relative; width: min(540px, 92vw); border-radius: 16px;
  border: 1px solid rgba(70, 216, 214, .26);
  background: linear-gradient(168deg, rgba(9, 32, 46, .97), rgba(5, 20, 32, .93));
  box-shadow: 0 30px 80px rgba(0, 0, 0, .55), inset 0 1px 0 rgba(206, 255, 253, .14);
  padding: 24px 26px 20px; }
.reco-close { position: absolute; right: 12px; top: 10px; width: 30px; height: 30px;
  border: none; background: transparent; color: var(--text-light); font-size: 20px;
  cursor: pointer; line-height: 1; }
.reco-close:hover { color: #fff; }
.reco-title { margin: 0 0 4px; font-size: 17px; font-weight: 700; color: var(--text); }
.reco-sub { margin: 0 0 16px; font-size: 12.5px; color: var(--text-light); line-height: 1.6; }
.reco-step { display: flex; gap: 10px; align-items: flex-start; margin: 0 0 13px; }
.reco-step .reco-n { flex: 0 0 auto; width: 21px; height: 21px; border-radius: 50%;
  background: var(--primary-bg); color: var(--primary); font-size: 12px; font-weight: 700;
  display: flex; align-items: center; justify-content: center; font-style: normal; margin-top: 2px; }
.reco-step span { font-size: 12.5px; color: var(--text); line-height: 1.65; }
.reco-step b { font-weight: 600; color: var(--text); }
.reco-acct { font-size: 12px; color: var(--text-light); background: var(--primary-bg);
  border-radius: var(--radius-sm); padding: 8px 12px; margin: 4px 0 14px; }
.reco-acct b { color: var(--primary); }
.reco-back { width: 100%; padding: 10px; border-radius: 10px; border: 1px solid var(--border);
  background: transparent; color: var(--text); font-size: 13px; cursor: pointer;
  transition: border-color .2s, background .2s, color .2s; }
.reco-back:hover { border-color: var(--primary); background: var(--primary-bg); color: var(--primary); }
.modal-fade-enter-active, .modal-fade-leave-active { transition: opacity .22s ease; }
.modal-fade-enter-from, .modal-fade-leave-to { opacity: 0; }
.modal-fade-enter-from .reco-modal { transform: translateY(12px) scale(.98); }
.modal-fade-enter-active .reco-modal { transition: transform .22s ease; }
.reco-lead { font-size: 12.5px; color: var(--text-light); margin: 0 0 16px; line-height: 1.6; }
.reco-step { display: flex; gap: 10px; align-items: flex-start; margin: 0 0 13px; }
.reco-step .reco-n { flex: 0 0 auto; width: 21px; height: 21px; border-radius: 50%;
  background: var(--primary-bg); color: var(--primary); font-size: 12px; font-weight: 700;
  display: flex; align-items: center; justify-content: center; font-style: normal; margin-top: 2px; }
.reco-step span { font-size: 12.5px; color: var(--text); line-height: 1.65; }
.reco-step b { font-weight: 600; color: var(--text); }
.reco-acct { font-size: 12px; color: var(--text-light); background: var(--primary-bg);
  border-radius: var(--radius-sm); padding: 8px 12px; margin: 2px 0 16px; }
.reco-acct b { color: var(--primary); }
.reco-back { width: 100%; padding: 10px; border-radius: 10px; border: 1px solid var(--border);
  background: transparent; color: var(--text); font-size: 13px; cursor: pointer;
  transition: border-color .2s, background .2s, color .2s; }
.reco-back:hover { border-color: var(--primary); background: var(--primary-bg); color: var(--primary); }
.forgot-link { margin-left: auto; font-size: 12px; color: var(--text-light);
  cursor: pointer; text-decoration: none; transition: color .2s; }
.forgot-link:hover { color: var(--primary); }

/* 底部星球地平线：巨大弧面 + 云层纹理，模拟从太空看地球的临边 */
.sp-horizon {
  position: absolute;
  left: 50%; bottom: -72vh;
  width: 240vw; height: 84vh;
  transform: translateX(-50%);
  border-radius: 50%;
  background:
    radial-gradient(140% 100% at 50% 0%, #105c66 0%, #0a3a49 24%, #05202d 60%, #02121c 100%);
  box-shadow:
    0 -1.5px 0 rgba(232, 255, 253, .96),
    0 -4px 16px rgba(140, 255, 248, .62),
    0 -14px 52px rgba(30, 214, 212, .42),
    0 -52px 140px rgba(20, 160, 190, .22);
  overflow: hidden;
}
/* 弧面上的云：直接复用地球云层贴图，横向缓慢漂移 */
.sp-horizon::before {
  content: '';
  position: absolute; inset: 0;
  background-image: url('/assets/space/earth-clouds.png');
  background-repeat: repeat-x;
  background-size: 3200px auto;
  background-position: 0 -80px;
  opacity: .32;
  mix-blend-mode: screen;
  filter: contrast(1.42) brightness(1.16);
  -webkit-mask-image: linear-gradient(180deg, #000 0%, rgba(0, 0, 0, .9) 30%, rgba(0, 0, 0, .35) 58%, rgba(0, 0, 0, 0) 84%);
  mask-image: linear-gradient(180deg, #000 0%, rgba(0, 0, 0, .9) 30%, rgba(0, 0, 0, .35) 58%, rgba(0, 0, 0, 0) 84%);
  animation: horizonDrift 160s linear infinite;
}
@keyframes horizonDrift {
  from { background-position: 0 -80px; }
  to   { background-position: -3200px -80px; }
}
/* 临边上方的一层大气雾 */
.sp-haze {
  position: absolute;
  left: 0; right: 0; bottom: 2vh;
  height: 22vh;
  background: linear-gradient(0deg, rgba(26, 176, 186, .16), rgba(20, 140, 170, .05) 45%, transparent);
  filter: blur(26px);
  pointer-events: none;
}
.sp-vig {
  position: absolute; inset: 0;
  background:
    radial-gradient(120% 90% at 50% 44%, rgba(6, 22, 38, 0) 42%, rgba(4, 14, 24, .5) 100%),
    linear-gradient(180deg, rgba(4, 14, 24, .45), rgba(4, 14, 24, 0) 24%);
}

/* ============================ 布局骨架 ============================ */
.auth-shell {
  position: relative; z-index: 1;
  width: 100%; max-width: 1720px;
  margin: 0 auto;
  flex: 1 1 auto;
  min-height: 100vh;
  display: flex;
  align-items: center;
  gap: clamp(24px, 3vw, 52px);
  padding: 34px clamp(26px, 5.2vw, 92px) 96px;
}
.auth-left {
  position: relative;
  flex: 1 1 57%;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 62px;
}

/* 品牌 */
.auth-brand {
  position: absolute; top: -4px; left: 0;
  display: flex; align-items: center; gap: 12px;
  z-index: 2;
}
.auth-brand .logo-icon {
  width: 42px; height: 42px;
  border-radius: 12px;
  display: grid; place-items: center;
  font-size: 20px; font-weight: 700; color: #04151f;
  background: linear-gradient(150deg, #6cf6e4 0%, #18d6c0 46%, #0f9f9f 100%);
  box-shadow:
    0 6px 20px rgba(24, 214, 192, .38),
    inset 0 1px 0 rgba(255, 255, 255, .65),
    inset 0 -2px 6px rgba(4, 60, 60, .35);
}
.auth-brand-name {
  font-size: 18px; font-weight: 700; letter-spacing: 1.6px; color: var(--c-title);
  text-shadow: 0 0 18px rgba(24, 214, 192, .30);
}

/* 标题 */
.lc-head { text-align: center; margin-bottom: 0; }
.lc-title {
  display: flex; align-items: center; justify-content: center; gap: 18px;
  margin: 0;
  font-size: clamp(22px, 2.3vw, 33px);
  font-weight: 700;
  letter-spacing: 5px;
  line-height: 1.25;
  color: #d8fffa;
  text-shadow:
    0 0 18px rgba(24, 214, 192, .55),
    0 0 46px rgba(24, 214, 192, .28);
}
.lc-tline {
  position: relative;
  display: block;
  width: clamp(36px, 5.4vw, 96px);
  height: 1px;
  background: linear-gradient(90deg, rgba(24, 214, 192, 0), rgba(150, 250, 242, .9));
}
.lc-tline::after {
  content: '';
  position: absolute; right: -2px; top: 50%;
  width: 4px; height: 4px; margin-top: -2px;
  border-radius: 50%;
  background: #c8fff9;
  box-shadow: 0 0 9px rgba(120, 250, 240, .95);
}
.lc-tline:last-child { transform: scaleX(-1); }
.lc-sub {
  margin: 11px 0 0;
  font-size: 13px;
  letter-spacing: 3.6px;
  color: #7fa4b3;
}

/* 3D 地球舞台 */
.lc-globe {
  position: relative;
  width: 100%;
  max-width: 780px;
  height: clamp(244px, 39.6vh, 450px);
  margin: 0;
  z-index: 1;
}

/* 时钟 */
.lc-clock {
  --dc-font: clamp(34px, 6.3vh, 66px);
  margin-top: 8px;
}

/* 平台数据卡 */
.lc-stats {
  position: relative;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  width: 100%;
  max-width: 560px;
  margin-top: 22px;
}
/* 上方一截发光连接线，把数据卡和时钟/地球集群在视觉上串起来 */
.lc-stats::before {
  content: '';
  position: absolute;
  left: 50%; top: -15px;
  width: clamp(150px, 46%, 250px);
  height: 2px;
  transform: translateX(-50%);
  background: linear-gradient(90deg, rgba(24, 214, 192, 0), rgba(140, 246, 240, .85) 50%, rgba(24, 214, 192, 0));
  border-radius: 2px;
  box-shadow: 0 0 12px rgba(24, 214, 192, .5);
}
.lc-stat {
  display: flex; align-items: center; gap: 12px;
  padding: 13px 16px;
  border-radius: 11px;
  border: 1px solid rgba(88, 222, 220, .24);
  background: linear-gradient(180deg, rgba(14, 56, 70, .42), rgba(6, 28, 42, .26));
  backdrop-filter: blur(10px); -webkit-backdrop-filter: blur(10px);
  box-shadow: inset 0 1px 0 rgba(150, 250, 246, .10), 0 10px 28px rgba(0, 0, 0, .22);
  transition: border-color .3s, transform .3s, box-shadow .3s;
  min-width: 0;
}
.lc-stat:hover {
  border-color: rgba(24, 214, 192, .5);
  transform: translateY(-2px);
  box-shadow: 0 14px 34px rgba(0, 0, 0, .3), 0 0 26px rgba(24, 214, 192, .14);
}
.lc-sic {
  flex: none;
  display: grid; place-items: center;
  color: #5fe9dd;
  filter: drop-shadow(0 0 8px rgba(24, 214, 192, .55));
}
.lc-sic svg {
  width: 26px; height: 26px;
  fill: currentColor; stroke: none;
}
.lc-sbody { display: flex; flex-direction: column; min-width: 0; }
.lc-sbody b {
  font-family: "Bahnschrift", "DIN Alternate", ui-monospace, Consolas, monospace;
  font-size: 26px; font-weight: 700; line-height: 1.02;
  color: #f2fffd;
  font-variant-numeric: tabular-nums;
  text-shadow: 0 0 18px rgba(24, 214, 192, .45);
}
.lc-sbody em {
  display: flex; align-items: center; gap: 3px;
  font-style: normal;
  margin-top: 3px;
  font-size: 11.5px;
  letter-spacing: .8px;
  color: #93b4c1;
  white-space: nowrap;
}
.lc-sbody em i {
  font-style: normal;
  font-size: 11px;
  color: rgba(24, 214, 192, .75);
  text-shadow: 0 0 8px rgba(24, 214, 192, .7);
}

/* ============================ 右侧玻璃卡 ============================ */
.auth-right {
  flex: 0 0 40%;
  display: flex; align-items: center; justify-content: center;
  min-width: 0;
}
.auth-card {
  position: relative;
  width: clamp(414px, 30vw, 466px);
  padding: 34px 32px 28px;
  border-radius: 20px;
  background:
    radial-gradient(120% 78% at 18% 0%, rgba(20, 72, 90, .34), rgba(20, 72, 90, 0) 62%),
    linear-gradient(180deg, rgba(10, 38, 54, .74) 0%, rgba(6, 26, 40, .62) 56%, rgba(5, 22, 34, .72) 100%);
  border: 1px solid rgba(122, 242, 237, .44);
  backdrop-filter: blur(24px) saturate(140%);
  -webkit-backdrop-filter: blur(24px) saturate(140%);
  box-shadow:
    0 40px 90px rgba(0, 0, 0, .55),
    0 0 72px rgba(24, 214, 192, .20),
    inset 0 1px 0 rgba(196, 255, 252, .30),
    inset 0 0 70px rgba(20, 140, 150, .10);
  animation: cardIn .6s cubic-bezier(.22, 1, .36, 1) both;
  max-height: 94vh;
  overflow-y: auto;
  scrollbar-width: thin;
}
.auth-card::-webkit-scrollbar { width: 6px; }
.auth-card::-webkit-scrollbar-thumb { background: rgba(24, 214, 192, .25); border-radius: 3px; }
/* 顶部高光线（明亮青白 + 辉光，与目标卡片一致） */
.auth-card::before {
  content: '';
  position: absolute; top: -1px; left: 14%; right: 14%; height: 1.5px;
  background: linear-gradient(90deg, rgba(24, 214, 192, 0), rgba(209, 255, 253, 1), rgba(24, 214, 192, 0));
  border-radius: 2px;
  box-shadow: 0 0 10px rgba(130, 250, 245, .7);
  z-index: 2;
}
@keyframes cardIn {
  from { opacity: 0; transform: translateY(18px) scale(.985); }
  to   { opacity: 1; transform: none; }
}
.auth-card-head { text-align: center; margin-bottom: 24px; }
.auth-card-head h2 {
  display: flex; align-items: center; justify-content: center; gap: 10px;
  margin: 0 0 9px;
  font-size: 27px; font-weight: 700; letter-spacing: 3px;
  color: #ffffff;
  text-shadow: 0 0 26px rgba(24, 214, 192, .42);
}
.lc-spark {
  width: 23px; height: 23px;
  fill: #b6fff8;
  filter: drop-shadow(0 0 8px rgba(24, 214, 192, .95));
}
.auth-card-head p {
  display: flex; align-items: center; justify-content: center; gap: 11px;
  margin: 0;
  font-size: 12.5px; letter-spacing: .8px; color: #7d9dab;
  white-space: nowrap;
}
.auth-card-head p i {
  flex: none;
  width: clamp(18px, 3vw, 42px); height: 1px;
  background: linear-gradient(90deg, rgba(24, 214, 192, 0), rgba(24, 214, 192, .5));
}
.auth-card-head p i:last-child { transform: scaleX(-1); }

/* 登录 / 注册 切换：一个描边外壳，激活项内嵌填充 */
.auth-tabs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0;
  padding: 4px;
  margin-bottom: 22px;
  border-radius: 11px;
  border: 1px solid rgba(70, 216, 214, .22);
  background: rgba(4, 20, 32, .45);
  box-shadow: inset 0 1px 0 rgba(150, 250, 246, .06);
}
.auth-tab {
  position: relative;
  display: flex; align-items: center; justify-content: center; gap: 8px;
  height: 43px;
  border-radius: 8px;
  border: 1px solid transparent;
  background: transparent;
  color: #9dbac8;
  font-size: 15px; font-weight: 600; letter-spacing: 1.5px;
  transition: color .3s, border-color .3s, background .3s, box-shadow .3s, transform .2s;
}
.at-ic {
  width: 17px; height: 17px;
  fill: none; stroke: currentColor; stroke-width: 1.85;
  stroke-linecap: round; stroke-linejoin: round;
}
.auth-tab:hover { color: #d9fbf6; }
.auth-tab:focus-visible { outline: 2px solid var(--c-main); outline-offset: 2px; }
.auth-tab.active {
  color: #ffffff;
  border-color: rgba(160, 255, 248, .35);
  background: linear-gradient(135deg, #34cfb7 0%, #1aa99b 52%, #10887e 100%);
  box-shadow:
    0 8px 22px rgba(24, 214, 192, .30),
    inset 0 1px 0 rgba(255, 255, 255, .40);
}
.auth-tab.active:active { transform: scale(.985); }

/* 表单 */
.auth-form .form-group { margin-bottom: 15px; }
.form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.form-group label {
  display: block; margin-bottom: 7px;
  font-size: 12px; letter-spacing: .4px; color: #7f9fae;
}
.auth-form input:not([type="checkbox"]), .auth-form select {
  width: 100%;
  height: 51px;
  padding: 0 14px;
  border-radius: 8px;
  border: 1px solid rgba(110, 220, 222, .18);
  background: rgba(3, 20, 32, .52);
  color: #eafbff;
  font-size: 14.5px;
  letter-spacing: .3px;
  -webkit-text-fill-color: #eafbff;
  box-shadow: inset 0 1px 0 rgba(150, 250, 246, .05);
  transition: border-color .28s, box-shadow .28s, background .28s;
}
.auth-form input::placeholder { color: #5f8091; -webkit-text-fill-color: #5f8091; }
.auth-form select {
  appearance: none;
  padding-right: 34px;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%236f97a6' stroke-width='2.2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='M6 9l6 6 6-6'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 12px center;
  background-size: 12px 12px;
}
.auth-form select option { background: #082336; color: #eafbff; }
.auth-form input:focus, .auth-form select:focus {
  outline: none;
  border-color: rgba(24, 214, 192, .72);
  background: rgba(6, 26, 40, .85);
  box-shadow: 0 0 0 3px rgba(24, 214, 192, .13), 0 0 20px rgba(24, 214, 192, .16);
}
/* 自动填充：避免白底黑字破坏深空氛围 */
.auth-form input:-webkit-autofill,
.auth-form input:-webkit-autofill:hover,
.auth-form input:-webkit-autofill:focus {
  -webkit-text-fill-color: #eafbff !important;
  -webkit-box-shadow: 0 0 0 1000px #082336 inset !important;
  box-shadow: 0 0 0 1000px #082336 inset !important;
  border-color: rgba(24, 214, 192, .4) !important;
  caret-color: #eafbff;
}

/* 带图标输入 */
.input-icon { position: relative; }
.input-icon .input-left-icon {
  position: absolute; left: 14px; top: 50%; transform: translateY(-50%);
  width: 17px; height: 17px;
  color: #5f8293;
  pointer-events: none;
  transition: color .28s;
}
.input-icon:focus-within .input-left-icon { color: #5ff0dd; }
.input-icon input { padding-left: 42px !important; }
.pwd-eye {
  position: absolute; right: 10px; top: 50%; transform: translateY(-50%);
  width: 30px; height: 30px;
  display: grid; place-items: center;
  border-radius: 8px;
  color: #6b8d9e;
  transition: color .25s, background .25s;
}
.pwd-eye:hover { color: #8ff2e4; background: rgba(24, 214, 192, .08); }
.pwd-eye .icon { width: 18px; height: 18px; }

/* 验证码 */
.captcha-row { display: flex; gap: 10px; align-items: center; }
.captcha-row .captcha-input { flex: 1; margin-bottom: 0; min-width: 0; }
.captcha-canvas {
  width: 104px; height: 44px;
  flex: none;
  border-radius: 8px;
  border: 1px solid rgba(24, 214, 192, .22);
  background: #0a2436;
  cursor: pointer;
  transition: border-color .28s, box-shadow .28s;
}
.captcha-canvas:hover {
  border-color: rgba(24, 214, 192, .7);
  box-shadow: 0 0 16px rgba(24, 214, 192, .3);
}
.captcha-refresh {
  width: 38px; height: 44px;
  flex: none;
  display: grid; place-items: center;
  border-radius: 8px;
  border: 1px solid rgba(24, 214, 192, .18);
  background: rgba(5, 20, 33, .6);
  color: #7fa2b3;
  transition: color .28s, border-color .28s, box-shadow .28s, transform .4s;
}
.captcha-refresh:hover { color: #8ff2e4; border-color: rgba(24, 214, 192, .55); box-shadow: 0 0 14px rgba(24, 214, 192, .22); }
.captcha-refresh:active { transform: rotate(-180deg); }
.captcha-refresh:focus-visible { outline: 2px solid var(--c-main); outline-offset: 2px; }
.captcha-refresh .icon { width: 16px; height: 16px; }

/* 记住登录 */
.remember-row { display: flex; align-items: center; margin: 3px 0 20px; }
.remember-label {
  display: flex; align-items: center; gap: 8px;
  font-size: 12.5px; color: #9db9c7; cursor: pointer; white-space: nowrap;
}
.remember-label input[type="checkbox"] {
  width: 15px; height: 15px; margin: 0; padding: 0; flex: 0 0 15px;
  border-radius: 4px;
  accent-color: #18d6c0;
  cursor: pointer;
  -webkit-text-fill-color: initial;
}

/* 主按钮：青绿渐变胶囊 + 圆形箭头 */
.lc-submit {
  position: relative;
  width: 100%;
  height: 51px;
  border-radius: 999px;
  overflow: hidden;
  display: flex; align-items: center; justify-content: center;
  font-size: 16.5px; font-weight: 700; letter-spacing: 6px;
  color: #052326;
  background: linear-gradient(100deg, #5ceadb 0%, #2ec9b6 32%, #16a99b 68%, #0d8c83 100%);
  box-shadow:
    0 12px 32px rgba(24, 214, 192, .32),
    inset 0 1px 0 rgba(255, 255, 255, .55),
    inset 0 -2px 8px rgba(4, 70, 66, .30);
  transition: transform .3s cubic-bezier(.22, 1, .36, 1), box-shadow .3s, filter .3s;
  animation: none;
}
.lc-submit::before {
  content: '';
  position: absolute; top: 0; left: -60%;
  width: 42%; height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, .55), transparent);
  transform: skewX(-20deg);
  pointer-events: none;
}
.lc-submit:hover {
  transform: translateY(-2px);
  filter: brightness(1.06);
  box-shadow: 0 16px 38px rgba(24, 214, 192, .42), inset 0 1px 0 rgba(255, 255, 255, .7);
}
.lc-submit:hover::before { animation: btnShine .85s ease; }
.lc-submit:active { transform: translateY(0) scale(.99); }
.lc-submit:focus-visible { outline: 2px solid #8ff2e4; outline-offset: 3px; }
@keyframes btnShine { to { left: 125%; } }
.lc-sbtn-text { display: inline-flex; align-items: center; gap: 8px; }
.lc-arrow {
  width: 18px; height: 18px;
  fill: none; stroke: currentColor; stroke-width: 1.7;
  stroke-linecap: round; stroke-linejoin: round;
}

/* 提交中：按钮收成一颗光球（沿用原交互） */
#login-btn .orb { display: none; }
#login-btn.is-busy {
  width: 54px; height: 54px; padding: 0; margin: 0 auto;
  border-radius: 50% !important;
  background: transparent !important;
  box-shadow: none !important;
  overflow: visible !important;
  transform: none !important;
}
#login-btn.is-busy .lc-sbtn-text { opacity: 0; }
#login-btn.is-busy::before { display: none !important; }
#login-btn.is-busy .orb { display: block; position: absolute; inset: 0; border-radius: 50%; }
.orb-core {
  position: absolute; inset: 0; border-radius: 50%;
  background:
    radial-gradient(circle at 30% 20%, rgba(255, 255, 255, .95) 0%, rgba(236, 254, 255, .45) 12%, rgba(134, 239, 212, .12) 26%, rgba(134, 239, 212, 0) 44%),
    radial-gradient(circle at 74% 84%, rgba(67, 185, 223, .55) 0%, rgba(67, 185, 223, 0) 48%),
    radial-gradient(circle at 40% 32%, #d1faf3 0%, #6ef2e0 16%, #18d6c0 36%, #0fae9f 56%, #0b7d76 78%, #063a3c 100%);
  box-shadow:
    inset 0 2px 6px rgba(255, 255, 255, .55),
    inset 0 -12px 20px rgba(4, 60, 58, .45),
    inset 0 0 0 1px rgba(209, 250, 245, .3),
    0 8px 24px rgba(24, 214, 192, .45),
    0 0 50px rgba(24, 214, 192, .35);
  animation: orbBreath 2.6s ease-in-out infinite;
  overflow: hidden;
}
.orb-core::before {
  content: ''; position: absolute; left: 20%; top: 14%;
  width: 30%; height: 22%; border-radius: 50%;
  background: radial-gradient(circle at 45% 40%, rgba(255, 255, 255, .98), rgba(255, 255, 255, .55) 45%, rgba(255, 255, 255, 0) 72%);
  transform: rotate(-24deg); filter: blur(.4px);
}
.orb-core::after {
  content: ''; position: absolute; right: 8%; bottom: 10%;
  width: 34%; height: 24%; border-radius: 50%;
  background: radial-gradient(circle, rgba(125, 211, 252, .6), rgba(125, 211, 252, 0) 72%);
  filter: blur(2.5px);
}
@keyframes orbBreath {
  0%, 100% { transform: scale(1); }
  50%      { transform: scale(1.05); }
}
.orb-ring {
  position: absolute; inset: -7px; border-radius: 50%;
  background: conic-gradient(from 0deg, rgba(24, 214, 192, 0) 0deg, rgba(24, 214, 192, .18) 90deg, rgba(140, 246, 255, .98) 260deg, rgba(24, 214, 192, 0) 360deg);
  -webkit-mask: radial-gradient(farthest-side, transparent calc(100% - 3px), #000 calc(100% - 2px));
  mask: radial-gradient(farthest-side, transparent calc(100% - 3px), #000 calc(100% - 2px));
  animation: orbSpin 1.15s linear infinite;
  transition: opacity .3s, transform .3s;
}
@keyframes orbSpin { to { transform: rotate(360deg); } }
.orb-wave {
  position: absolute; inset: 0; border-radius: 50%;
  border: 1px solid rgba(24, 214, 192, .6);
  animation: orbWave 2.8s cubic-bezier(.2, .55, .3, 1) infinite;
  pointer-events: none;
}
.orb-wave-2 { animation-delay: 1.4s; }
@keyframes orbWave {
  0%   { transform: scale(1); opacity: .7; }
  65%  { opacity: .18; }
  100% { transform: scale(2.1); opacity: 0; }
}
.orb-check {
  position: absolute; left: 26%; top: 26%; width: 48%; height: 48%;
  opacity: 0; transition: opacity .2s .1s;
}
.orb-check path {
  stroke: #f0fffd; stroke-width: 2.8; stroke-linecap: round; stroke-linejoin: round;
  stroke-dasharray: 26; stroke-dashoffset: 26;
  filter: drop-shadow(0 1px 4px rgba(6, 60, 58, .7));
}
#login-btn.is-ok .orb-ring { opacity: 0; transform: scale(1.35); }
#login-btn.is-ok .orb-core { animation: orbOk .6s cubic-bezier(.2, .9, .25, 1) forwards; }
#login-btn.is-ok .orb-check { opacity: 1; }
#login-btn.is-ok .orb-check path { animation: orbTick .42s .08s cubic-bezier(.5, 0, .3, 1) forwards; }
@keyframes orbTick { to { stroke-dashoffset: 0; } }
@keyframes orbOk {
  0%   { transform: scale(1); filter: brightness(1); }
  45%  { transform: scale(1.13); filter: brightness(1.25); }
  100% { transform: scale(1.02); filter: brightness(1.12); }
}

/* 快捷登录 */
.quick-login {
  display: flex; align-items: center; justify-content: center; gap: 8px;
  margin-top: 16px; flex-wrap: wrap;
}
.quick-login-label { font-size: 12px; color: #6d8d9d; }
.lc-quick {
  padding: 5px 14px;
  border-radius: 999px;
  border: 1px solid rgba(24, 214, 192, .22);
  background: rgba(6, 24, 38, .5);
  color: #9db9c7;
  font-size: 12px;
  letter-spacing: .5px;
  transition: color .25s, border-color .25s, background .25s, box-shadow .25s;
}
.lc-quick:hover {
  color: #052326;
  border-color: transparent;
  background: linear-gradient(135deg, #7cf3e6, #18d6c0);
  box-shadow: 0 4px 14px rgba(24, 214, 192, .32);
}
.lc-quick:focus-visible { outline: 2px solid var(--c-main); outline-offset: 2px; }
.auth-tip { margin-top: 12px; text-align: center; font-size: 11.5px; color: #5f7f8f; letter-spacing: .4px; }

/* 注册表单更紧凑 */
.reg-form .form-group { margin-bottom: 12px; }
.reg-form input, .reg-form select { height: 44px; }
.reg-form .lc-submit { margin-top: 6px; }

/* ============================ 底部署名 ============================ */
.auth-foot {
  position: absolute; left: 0; right: 0; bottom: 24px;
  z-index: 2;
  display: flex; align-items: center; justify-content: center; gap: 16px;
  font-size: 12.5px; letter-spacing: 3px;
  color: #eafbfe;
  text-shadow: 0 0 16px rgba(24, 214, 192, .45), 0 1px 10px rgba(2, 20, 30, .9);
  pointer-events: none;
}
.auth-foot i {
  width: clamp(60px, 12vw, 190px); height: 1px;
  background: linear-gradient(90deg, rgba(120, 240, 235, 0), rgba(120, 240, 235, .5));
}
.auth-foot i:last-child { transform: scaleX(-1); }

/* ============================ 登录成功欢迎层（原样式保留） ============================ */
.login-welcome {
  position: fixed; inset: 0; z-index: 9999;
  display: grid; place-items: center; padding: 24px;
  background:
    radial-gradient(900px 720px at 50% 44%, rgba(30, 55, 88, .55), transparent 72%),
    #050a13;
  overflow: hidden;
}
.lw-campus {
  position: absolute; inset: 0; z-index: 0;
  opacity: 0; transform: scale(1.05);
  transition: opacity .9s ease .5s, transform 1.9s cubic-bezier(.22, 1, .36, 1) .4s;
  pointer-events: none;
}
.lw-campus.show { opacity: 1; transform: scale(1); }
.lw-vig {
  position: absolute; inset: 0; pointer-events: none;
  background:
    radial-gradient(126% 96% at 50% 48%, rgba(8, 26, 20, 0) 46%, rgba(8, 26, 20, .20) 100%),
    linear-gradient(180deg, rgba(10, 30, 44, .10), rgba(10, 30, 44, 0) 26%);
}
.lw-globe { position: absolute; inset: 0; z-index: 0; transition: opacity .55s ease; }
.lw-globe.hide { opacity: 0; }
.login-welcome.is-campus::after {
  content: ''; position: absolute; left: 0; right: 0; top: 0; height: 44%; z-index: 1;
  background: linear-gradient(180deg, rgba(255, 255, 255, .34), rgba(255, 255, 255, .08) 46%, transparent);
  pointer-events: none;
  animation: lwFadeIn .9s .55s both;
}
@keyframes lwFadeIn { from { opacity: 0; } to { opacity: 1; } }
.lw-inner {
  position: relative; z-index: 2; text-align: center;
  transform: translateY(-16vh);
  transition: transform 1.1s cubic-bezier(.22, 1, .36, 1) .45s;
}
.lw-inner.zoom { transform: translateY(-39vh); }
.lw-inner.zoom .lw-title { font-size: clamp(17px, 2.15vw, 26px); letter-spacing: 1.5px; }
.lw-inner.zoom .lw-greet { margin-bottom: 8px; font-size: clamp(12px, 1.7vw, 16px); }
.lw-inner.zoom .lw-hint { margin-top: 14px; padding: 6px 14px; font-size: 12.5px; }
.lw-title, .lw-greet, .lw-sub { transition: opacity .45s ease, font-size .8s cubic-bezier(.22, 1, .36, 1); }
.login-welcome.is-campus .lw-inner { transform: translateY(-29vh); }
.login-welcome.is-campus .lw-title {
  font-size: clamp(22px, 3.1vw, 38px);
  color: #0f2a3f;
  text-shadow: 0 2px 22px rgba(255, 255, 255, .85);
}
.lw-loc {
  display: inline-flex; align-items: center; gap: 8px;
  margin: 18px 0 0; padding: 6px 15px;
  font-size: 13.5px; letter-spacing: 1.5px; color: #14513c;
  border: 1px solid rgba(20, 81, 60, .22); border-radius: 999px;
  background: rgba(255, 255, 255, .72);
  box-shadow: 0 4px 18px rgba(20, 60, 40, .12);
  backdrop-filter: blur(6px); -webkit-backdrop-filter: blur(6px);
  animation: lwSubIn .7s .1s cubic-bezier(.22, 1, .36, 1) both;
}
.lw-loc i { width: 6px; height: 6px; border-radius: 50%; background: #52c79e; box-shadow: 0 0 10px rgba(82, 199, 158, .8); }
.lw-greet {
  margin: 0 0 12px; font-size: clamp(14px, 2.2vw, 20px); font-weight: 600;
  letter-spacing: 3px; color: #dbe7f2;
  text-shadow: 0 1px 10px rgba(4, 16, 28, .45), 0 0 18px rgba(120, 160, 200, .35);
  transition: color .35s ease 1.15s, text-shadow .35s ease 1.15s;
  animation: lwTitleIn .9s 0s cubic-bezier(.22, 1, .36, 1) both;
}
.login-welcome.is-campus .lw-greet { color: #2f6349; text-shadow: 0 1px 16px rgba(255, 255, 255, .75); }
.lw-title {
  margin: 0; font-size: clamp(24px, 4vw, 48px); font-weight: 700; letter-spacing: 2px;
  color: #f1f5f9;
  text-shadow: 0 1px 12px rgba(4, 16, 28, .45), 0 2px 30px rgba(34, 211, 238, .30);
  transition: color .35s ease 1.15s, text-shadow .35s ease 1.15s, font-size .7s cubic-bezier(.22, 1, .36, 1) .45s;
}
.lw-w { display: inline-block; opacity: 0; animation: lwWord .6s cubic-bezier(.22, 1, .36, 1) both; }
@keyframes lwWord {
  from { opacity: .2; transform: translateY(10px); }
  to   { opacity: 1; transform: translateY(0); }
}
.lw-hint {
  display: inline-flex; align-items: center; gap: 9px;
  margin: 20px 0 0; padding: 7px 16px;
  font-size: 13.5px; letter-spacing: 2px; color: #d3e6f5;
  border: 1px solid rgba(160, 224, 255, .22); border-radius: 999px;
  background: rgba(10, 24, 38, .42);
  box-shadow: 0 0 26px rgba(80, 170, 220, .14);
  backdrop-filter: blur(8px); -webkit-backdrop-filter: blur(8px);
  text-shadow: 0 1px 10px rgba(0, 0, 0, .5);
  animation: lwSubIn .7s .6s cubic-bezier(.22, 1, .36, 1) both;
}
.lw-hint-dot {
  width: 6px; height: 6px; border-radius: 50%; flex: none;
  background: #bdf0ff; box-shadow: 0 0 12px rgba(150, 230, 255, .95);
  animation: lwHintPulse 1.5s ease-in-out infinite;
}
@keyframes lwHintPulse {
  0%, 100% { transform: scale(1); opacity: .5; }
  50%      { transform: scale(1.8); opacity: 1; }
}
@keyframes lwTitleIn {
  from { opacity: 0; transform: translateY(18px) scale(.94); filter: blur(8px); }
  to   { opacity: 1; transform: none; filter: blur(0); }
}
@keyframes lwSubIn { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: none; } }
.lw-path {
  position: absolute; left: 50%; bottom: 7.5%; transform: translateX(-50%); z-index: 3;
  display: flex; align-items: center; margin: 0; padding: 8px 16px;
  list-style: none; border-radius: 999px;
  background: rgba(8, 20, 34, .42);
  border: 1px solid rgba(103, 232, 249, .16);
  backdrop-filter: blur(8px); -webkit-backdrop-filter: blur(8px);
  animation: lwPathIn .8s .9s both;
}
@keyframes lwPathIn {
  from { opacity: 0; transform: translate(-50%, 12px); }
  to   { opacity: 1; transform: translate(-50%, 0); }
}
.lw-path-i {
  display: flex; align-items: center; gap: 6px;
  font-size: 12.5px; letter-spacing: 1px; white-space: nowrap;
  color: rgba(148, 163, 184, .5);
  transition: color .35s ease;
}
.lw-path-i + .lw-path-i::before {
  content: ''; width: 14px; height: 1px; margin: 0 6px 0 8px;
  background: rgba(103, 232, 249, .18);
  transition: background .35s ease;
}
.lw-path-i.on { color: #a5f3fc; }
.lw-path-i.on + .lw-path-i::before { background: rgba(103, 232, 249, .5); }
.lw-path-i.cur { color: #eafcff; font-weight: 700; text-shadow: 0 0 14px rgba(34, 211, 238, .55); }
.lw-path-dot {
  width: 5px; height: 5px; border-radius: 50%; flex: none;
  background: rgba(148, 163, 184, .45);
  transition: background .35s ease, box-shadow .35s ease;
}
.lw-path-i.on .lw-path-dot { background: #22d3ee; box-shadow: 0 0 10px rgba(34, 211, 238, .8); }
.lw-path-i.cur .lw-path-dot { animation: lwPathPulse 1s ease-in-out infinite; }
@keyframes lwPathPulse {
  0%, 100% { transform: scale(1); opacity: .85; }
  50%      { transform: scale(1.9); opacity: 1; }
}
.lw-fade-enter-active { transition: opacity .45s ease; }
.lw-fade-leave-active { transition: opacity .3s ease; }
.lw-fade-enter-from, .lw-fade-leave-to { opacity: 0; }

/* ============================ 响应式 ============================ */
/* 高度收紧：地球与间距同步缩，保证一屏装下 */
@media (max-height: 880px) {
  .lc-globe { height: 40vh; }
  .auth-left { padding-top: 52px; }
  .auth-shell { padding-top: 26px; padding-bottom: 84px; }
  .lc-stats { margin-top: 15px; }
  .lc-stat { padding: 11px 14px; }
  .lc-sbody b { font-size: 24px; }
}
@media (max-height: 800px) {
  .lc-globe { height: 36.5vh; }
  .auth-left { padding-top: 40px; }
  .lc-clock { --dc-font: clamp(30px, 5.6vh, 48px); }
  .auth-card { padding: 26px 26px 22px; }
  .auth-card-head { margin-bottom: 18px; }
  .auth-tabs { margin-bottom: 16px; }
  .auth-tab { height: 40px; }
  .auth-form .form-group { margin-bottom: 12px; }
  .auth-form input:not([type="checkbox"]) { height: 46px; }
  .lc-submit { height: 46px; }
  .remember-row { margin: 2px 0 15px; }
  .quick-login { margin-top: 13px; }
}
@media (max-height: 720px) {
  .lc-globe { height: 32vh; }
  .lc-sub { display: none; }
  .auth-left { padding-top: 30px; }
  .lc-stats { margin-top: 10px; }
  .auth-card-head p { display: none; }
  .auth-card-head { margin-bottom: 14px; }
  .auth-tip { display: none; }
}

/* 中等宽度：收窄左栏留白 */
@media (max-width: 1180px) {
  .auth-shell { gap: 26px; padding: 30px 28px 88px; }
  .auth-left { flex-basis: 53%; }
  .auth-right { flex-basis: 47%; }
  .lc-stats { max-width: 100%; }
  .lc-title { letter-spacing: 3px; gap: 12px; }
}

/* 窄屏 / 手机：地球缩小在上，登录卡在下 */
@media (max-width: 899px) {
  .auth-page { overflow-y: auto; }
  .auth-shell {
    flex-direction: column;
    gap: 16px;
    padding: 20px 18px 46px;
    min-height: 0;
    justify-content: flex-start;
  }
  .auth-left { flex: none; width: 100%; padding-top: 0; align-items: center; }
  .auth-brand { position: static; align-self: flex-start; margin-bottom: 14px; }
  .lc-title { font-size: 19px; letter-spacing: 2px; gap: 10px; }
  .lc-sub { font-size: 11px; letter-spacing: 2.4px; margin-top: 7px; }
  .lc-globe { max-width: 480px; height: clamp(200px, 48vw, 300px); }
  .lc-clock { --dc-font: clamp(30px, 8.4vw, 44px); margin-top: 0; }
  .lc-stats { margin-top: 14px; gap: 8px; max-width: 460px; }
  .lc-stat { padding: 9px 10px; gap: 8px; }
  .lc-sbody b { font-size: 19px; }
  .lc-sbody em { font-size: 10px; }
  .lc-sic svg { width: 20px; height: 20px; }
  .auth-right { flex: none; width: 100%; }
  .auth-card { width: 100%; max-width: 440px; padding: 26px 22px 22px; max-height: none; }
  .auth-foot { position: static; margin-top: 10px; padding-bottom: 12px; letter-spacing: 1.6px; font-size: 11px; }
  .sp-horizon { bottom: -66vh; height: 72vh; width: 300vw; }
}
@media (max-width: 420px) {
  .lc-globe { height: 192px; }
  .lc-clock { --dc-font: 31px; }
  .lc-stats { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .lc-sic { display: none; }
  .lc-stat { justify-content: center; }
  .captcha-canvas { width: 88px; }
  .form-row { grid-template-columns: 1fr; }
}

/* 页脚压在云层上时保证可读 */
.auth-foot span { text-shadow: 0 1px 12px rgba(2, 10, 16, .95), 0 0 24px rgba(2, 10, 16, .72); }
.auth-foot::before {
  content: '';
  position: absolute; left: 50%; top: 50%;
  width: min(560px, 76%); height: 68px;
  transform: translate(-50%, -50%);
  background: radial-gradient(ellipse 50% 100% at 50% 50%, rgba(2, 12, 20, .66), rgba(2, 12, 20, 0) 72%);
  pointer-events: none;
  z-index: -1;
}

/* 流星：间隔出现，斜向掠过，不持续乱飞 */
.sp-meteors { position: absolute; inset: 0; overflow: hidden; pointer-events: none; }
.sp-meteor {
  position: absolute;
  width: 2px; height: 2px;
  border-radius: 50%;
  background: #eafffe;
  box-shadow: 0 0 6px 1px rgba(190, 250, 255, .9), 0 0 14px 3px rgba(120, 220, 255, .45);
  opacity: 0;
  animation: meteorFly 13s linear infinite;
}
.sp-meteor::after {
  content: '';
  position: absolute;
  top: 50%; left: 0;
  width: 142px; height: 1.6px;
  transform: translate(-100%, -50%) rotate(-27deg);
  transform-origin: right center;
  background: linear-gradient(90deg, rgba(190, 250, 255, 0), rgba(226, 255, 255, .95));
  border-radius: 2px;
}
@keyframes meteorFly {
  0% { transform: translate3d(0, 0, 0); opacity: 0; }
  3% { opacity: 0; }
  5% { opacity: 1; }
  19% { transform: translate3d(-380px, 224px, 0); opacity: 0; }
  100% { transform: translate3d(-380px, 224px, 0); opacity: 0; }
}

.logo-icon svg { width: 24px; height: 24px; fill: currentColor; }
/* ===== 对齐设计稿：整体提亮 + 尺寸微调（覆盖段，勿删） ===== */
.auth-page {
  background:
    url('/assets/space/space-bg.webp') center / cover no-repeat,
    #04121c;
}
/* 背景图已含星云 / 银河 / 底部地球地平线：只保留动态星云层做缓慢漂移，其余自制雾/地平线隐藏 */
.sp-streak, .sp-horizon, .sp-haze { display: none !important; }
.sp-vig { opacity: .35; }
.sp-neb { opacity: .55; }
.sp-vig {
  background:
    radial-gradient(120% 90% at 50% 44%, rgba(6, 22, 38, 0) 46%, rgba(4, 14, 24, .12) 100%),
    linear-gradient(180deg, rgba(4, 14, 24, .10), rgba(4, 14, 24, 0) 22%);
}
/* 底部地球地平线更亮更明显 */
.sp-horizon {
  width: 156vw; height: 62vh; bottom: -50vh;
  opacity: .94;
  filter: contrast(1.06) brightness(1.62) saturate(1.28);
  background:
    radial-gradient(120% 96% at 50% 0%, rgba(10, 52, 66, .08) 0%, rgba(5, 24, 36, .30) 46%, rgba(2, 10, 18, .74) 100%),
    url('/assets/space/earth-horizon.webp') center 52% / cover no-repeat;
  box-shadow:
    0 -1px 0 rgba(226, 255, 253, .9),
    0 -4px 14px rgba(120, 240, 240, .5),
    0 -14px 46px rgba(24, 180, 200, .3);
}
/* 地平线上缓慢流动的青蓝光带 */
.sp-horizon::after {
  content: '';
  position: absolute; left: -18%; right: -18%; top: 0; height: 24%;
  background: linear-gradient(90deg, transparent, rgba(120, 255, 248, .28) 32%, rgba(206, 255, 253, .52) 50%, rgba(120, 255, 248, .28) 68%, transparent);
  filter: blur(16px);
  mix-blend-mode: screen;
  animation: horizonFlow 17s ease-in-out infinite alternate;
}
@keyframes horizonFlow {
  from { transform: translateX(-7%); opacity: .45; }
  to   { transform: translateX(7%); opacity: .85; }
}
.sp-haze { background: linear-gradient(0deg, rgba(34, 204, 214, .30), rgba(22, 156, 186, .08) 45%, transparent); }
/* 地球整体提亮一档（原贴图偏暗） */
.lc-globe { height: clamp(258px, 41.5vh, 470px); }
/* 双栏比例 58 / 38，两侧更平衡 */
.auth-left { flex: 1 1 58%; padding-top: 22px; }
.auth-right { flex: 0 0 38%; }
.auth-shell { padding-bottom: 58px; }
/* 标题放大、两侧装饰线加长 */
.lc-title { font-size: clamp(24px, 2.5vw, 36px); letter-spacing: 6px; }
.lc-tline { width: clamp(44px, 6.4vw, 116px); }
/* 时钟放大 + 冒号拉开间距 */
.lc-clock { --dc-font: clamp(36px, 6.4vh, 68px); margin-top: 40px; }
:deep(.dc-colon) { width: .52em; }
/* 统计卡更宽更亮 */
.lc-stat {
  padding: 15px 18px;
  border-color: rgba(96, 228, 226, .42);
  background: linear-gradient(180deg, rgba(8, 38, 50, .82), rgba(4, 20, 30, .72));
}
.lc-sbody em { color: #b2cfd9; }
.lc-stats { margin-top: 12px; }
.lc-sbody b { font-size: 28px; }
/* 登录卡略加宽、内边距加大、描边更亮 */
.auth-card {
  width: clamp(432px, 31vw, 484px);
  padding: 40px 36px 34px;
  border-color: rgba(160, 252, 246, .62);
  box-shadow:
    0 42px 96px rgba(0, 0, 0, .58),
    0 0 92px rgba(24, 214, 192, .30),
    inset 0 1px 0 rgba(206, 255, 253, .38),
    inset 0 0 80px rgba(20, 150, 160, .14);
}

/* 减少动效：关闭漂移/闪烁/流光，保留静态画面 */
@media (prefers-reduced-motion: reduce) {
  .sp-neb, .sp-stars i, .lc-rdots i.active, .orb-core, .orb-ring, .orb-wave { animation: none !important; }
  .sp-horizon::before { animation: none; }
  .sp-horizon::after { animation: none; }
  .sp-meteor { animation: none; opacity: 0; }
  .lc-submit:hover::before { animation: none; }
  .auth-card { animation-duration: .01s; }
  .auth-tab, .lc-submit, .lc-stat, .captcha-refresh { transition: none; }
}
</style>
