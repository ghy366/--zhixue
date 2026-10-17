<template>
  <div class="auth-page">
    <!-- 左侧品牌区 -->
    <div class="auth-left">
      <div class="auth-brand">
        <div class="logo-icon">智</div>
        <span class="auth-brand-name">智学选课</span>
      </div>

      <div class="auth-gauge">
        <svg class="gauge-svg" viewBox="0 0 220 220" aria-hidden="true">
          <circle cx="110" cy="110" r="95" fill="none" stroke="rgba(71,85,105,0.25)" stroke-width="0.8" />
          <circle cx="110" cy="110" r="95" fill="none" stroke="#4ade80" stroke-width="3"
            stroke-linecap="round" :stroke-dasharray="596" :stroke-dashoffset="596 - 596 * clockPct"
            transform="rotate(-90 110 110)" />
        </svg>
        <div class="gauge-center">
          <div class="gauge-time">{{ clockTime }}</div>
          <div class="gauge-date">{{ clockDate }}</div>
        </div>
      </div>

      <div class="auth-left-foot">智学选课 · 大学生课程管理平台</div>
    </div>

    <!-- 右侧登录区 -->
    <div class="auth-right">
      <button class="topbar-icon-btn auth-theme-btn" @click="ui.toggleTheme()" :title="ui.theme === 'dark' ? '切换为浅色' : '切换为深色'">
        <Icon :name="ui.theme === 'dark' ? 'sun' : 'moon'" />
      </button>

      <div class="auth-card">
        <div class="auth-card-head">
          <h2>欢迎登录</h2>
          <p>请使用您的账号登录系统</p>
        </div>

        <div class="auth-tabs">
          <button class="auth-tab" :class="{ active: tab === 'login' }" @click="tab = 'login'">登录</button>
          <button class="auth-tab" :class="{ active: tab === 'register' }" @click="tab = 'register'">注册</button>
        </div>

        <!-- 登录表单 -->
        <form v-show="tab === 'login'" class="auth-form" @submit.prevent="onLogin">
          <div class="form-group input-icon">
            <Icon name="user" cls="input-left-icon" />
            <input type="text" v-model="login.no" placeholder="学号 / 工号 / 管理员账号" required />
          </div>
          <div class="form-group input-icon">
            <Icon name="lock" cls="input-left-icon" />
            <input :type="pwdType" v-model="login.password" placeholder="请输入密码" required />
            <button type="button" class="pwd-eye" @click="togglePwd">
              <Icon :name="pwdType === 'password' ? 'eye' : 'eye-off'" />
            </button>
          </div>
          <div class="form-group captcha-row">
            <div class="input-icon captcha-input">
              <Icon name="check-circle" cls="input-left-icon" />
              <input type="text" v-model="login.captcha" placeholder="验证码" maxlength="4" autocomplete="off" required />
            </div>
            <canvas ref="captchaRef" width="112" height="44" class="captcha-canvas" @click="drawCaptcha" title="点击刷新验证码"></canvas>
          </div>
          <div class="remember-row">
            <label class="remember-label"><input type="checkbox" v-model="login.remember" /> 记住登录状态</label>
          </div>
          <button type="submit" class="btn btn-primary btn-block" :disabled="logging">
            {{ logging ? '登录中…' : '登 录' }}
          </button>
          <div class="quick-login">
            <span class="quick-login-label">快捷登录：</span>
            <button type="button" class="btn btn-outline btn-sm" @click="quick('student')">学生</button>
            <button type="button" class="btn btn-outline btn-sm" @click="quick('teacher')">老师</button>
            <button type="button" class="btn btn-outline btn-sm" @click="quick('admin')">管理员</button>
          </div>
          <div class="auth-tip">密码均为 <b>123456</b></div>
        </form>

        <!-- 注册表单（仅学生） -->
        <form v-show="tab === 'register'" class="auth-form" @submit.prevent="onRegister">
          <div class="form-group"><label>姓名</label><input type="text" v-model="reg.name" placeholder="请输入姓名" required /></div>
          <div class="form-group"><label>学号</label><input type="text" v-model="reg.no" placeholder="请输入学号" required /></div>
          <div class="form-group"><label>密码</label>
            <input :type="pwdType" v-model="reg.password" placeholder="请输入密码" required />
          </div>
          <div class="form-row">
            <div class="form-group"><label>学院</label>
              <select v-model="reg.college">
                <option>计算机学院</option><option>信息学院</option><option>经管学院</option><option>外语学院</option><option>数学学院</option>
              </select>
            </div>
            <div class="form-group"><label>年级</label>
              <select v-model="reg.grade">
                <option>2021</option><option>2022</option><option>2023</option><option>2024</option><option>2025</option><option>2026</option>
              </select>
            </div>
          </div>
          <div class="form-group"><label>专业</label><input type="text" v-model="reg.major" placeholder="如：软件工程" required /></div>
          <div class="form-group"><label>班级</label><input type="text" v-model="reg.className" placeholder="如：软工2301班" /></div>
          <div class="form-group"><label>联系电话</label><input type="text" v-model="reg.phone" placeholder="请输入手机号" /></div>
          <button type="submit" class="btn btn-primary btn-block">注 册</button>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/Icon.vue'
import { useAuthStore } from '@/store/auth'
import { useUiStore } from '@/store/ui'
import { toast } from '@/utils/dom'

const router = useRouter()
const auth = useAuthStore()
const ui = useUiStore()

const tab = ref('login')
const pwdType = ref('password')
const logging = ref(false)
const login = reactive({ no: '', password: '', captcha: '', remember: true })
const reg = reactive({ name: '', no: '', password: '', college: '计算机学院', grade: '2023', major: '', className: '', phone: '' })

const captchaRef = ref(null)
let captchaCode = ''
const palette = ['#4ade80', '#22d3ee', '#a78bfa', '#fbbf24']
function drawCaptcha() {
  const cv = captchaRef.value
  if (!cv) return
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
  captchaCode = ''
  for (let i = 0; i < 4; i++) captchaCode += chars.charAt(Math.floor(Math.random() * chars.length))
  const ctx = cv.getContext('2d')
  ctx.clearRect(0, 0, cv.width, cv.height)
  ctx.fillStyle = 'rgba(15,23,42,0.85)'
  ctx.fillRect(0, 0, cv.width, cv.height)
  for (let i = 0; i < 4; i++) {
    ctx.strokeStyle = 'rgba(74,222,128,' + (0.12 + Math.random() * 0.18) + ')'
    ctx.beginPath(); ctx.moveTo(Math.random() * cv.width, Math.random() * cv.height); ctx.lineTo(Math.random() * cv.width, Math.random() * cv.height); ctx.stroke()
  }
  for (let i = 0; i < captchaCode.length; i++) {
    ctx.save()
    ctx.font = '700 22px Georgia, serif'
    ctx.fillStyle = palette[i % palette.length]
    ctx.shadowColor = 'rgba(74,222,128,0.3)'; ctx.shadowBlur = 2
    ctx.translate(18 + i * 24, 28); ctx.rotate((Math.random() - 0.5) * 0.5)
    ctx.fillText(captchaCode[i], -8, 6); ctx.restore()
  }
  for (let i = 0; i < 24; i++) { ctx.fillStyle = 'rgba(74,222,128,0.4)'; ctx.fillRect(Math.random() * cv.width, Math.random() * cv.height, 1.6, 1.6) }
}

const clockTime = ref(''); const clockDate = ref(''); const clockPct = ref(0)
let clockTimer = null
function tickClock() {
  const d = new Date(); const p = (n) => (n < 10 ? '0' + n : '' + n)
  clockTime.value = `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
  clockDate.value = `${d.getFullYear()}.${p(d.getMonth() + 1)}.${p(d.getDate())} - ${['SUN','MON','TUE','WED','THU','FRI','SAT'][d.getDay()]}`
  const sec = d.getHours() * 3600 + d.getMinutes() * 60 + d.getSeconds()
  clockPct.value = sec / 864
}

function togglePwd() { pwdType.value = pwdType.value === 'password' ? 'text' : 'password' }
function quick(role) {
  const acc = { student: { no: '2023010101', password: '123456' }, teacher: { no: 'T001', password: '123456' }, admin: { no: 'admin', password: 'admin' } }[role]
  login.no = acc.no; login.password = acc.password
  toast('已填充' + ({ student: '学生', teacher: '老师', admin: '管理员' }[role]) + '账号，请点击登录', 'success')
}

async function onLogin() {
  if ((login.captcha || '').trim().toUpperCase() !== captchaCode) { toast('验证码错误，请重新输入', 'error'); drawCaptcha(); return }
  logging.value = true
  const r = await auth.login(login.no.trim(), login.password)
  logging.value = false
  if (r.success) {
    if (login.remember) localStorage.setItem('zhixue_remember', JSON.stringify({ no: login.no, password: login.password }))
    else localStorage.removeItem('zhixue_remember')
    toast('登录成功', 'success')
    setTimeout(() => router.push('/'), 500)
  } else {
    toast(r.message || '登录失败', 'error')
  }
}

async function onRegister() {
  const r = await auth.register({
    name: reg.name.trim(), no: reg.no.trim(), password: reg.password,
    college: reg.college, grade: reg.grade, major: reg.major.trim(),
    className: reg.className.trim(), phone: reg.phone.trim(),
  })
  if (r.success) { toast('注册成功，请登录', 'success'); tab.value = 'login'; login.no = reg.no }
  else toast(r.message || '注册失败', 'error')
}

onMounted(() => {
  tickClock(); clockTimer = setInterval(tickClock, 1000)
  drawCaptcha()
  try {
    const c = JSON.parse(localStorage.getItem('zhixue_remember') || 'null')
    if (c) { login.no = c.no; login.password = c.password; login.remember = true }
  } catch (e) {}
})
onUnmounted(() => clearInterval(clockTimer))
</script>
