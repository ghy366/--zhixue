<template>
  <div class="auth-page">
    <!-- 左侧 60%：深色科技品牌区 -->
    <div class="auth-left">
      <!-- 漂浮光晕球：纯氛围装饰，reduced-motion 时静止 -->
      <div class="auth-orbs" aria-hidden="true"><span></span><span></span><span></span></div>
      <div class="auth-brand">
        <div class="logo-icon">智</div>
        <span class="auth-brand-name">智学选课</span>
      </div>
      <div class="auth-gauge">
        <svg class="gauge-svg" id="gauge-svg-inner" viewBox="0 0 220 220" aria-hidden="true">
          <circle cx="110" cy="110" r="95" fill="none" stroke="rgba(71,85,105,0.25)" stroke-width="0.8"/>
          <line x1="110.0" y1="10.0" x2="110.0" y2="21.0" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="118.7" y1="10.4" x2="117.8" y2="21.3" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="127.4" y1="11.5" x2="125.5" y2="22.4" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="135.9" y1="13.4" x2="133.0" y2="24.0" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="144.2" y1="16.0" x2="140.4" y2="26.4" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="152.3" y1="19.4" x2="147.6" y2="29.3" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="160.0" y1="23.4" x2="154.5" y2="32.9" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="167.4" y1="28.1" x2="161.0" y2="37.1" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="174.3" y1="33.4" x2="167.2" y2="41.8" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="180.7" y1="39.3" x2="172.9" y2="47.1" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="186.6" y1="45.7" x2="178.2" y2="52.8" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="191.9" y1="52.6" x2="182.9" y2="59.0" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="196.6" y1="60.0" x2="187.1" y2="65.5" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="200.6" y1="67.7" x2="190.7" y2="72.4" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="204.0" y1="75.8" x2="193.6" y2="79.6" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="206.6" y1="84.1" x2="196.0" y2="87.0" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="208.5" y1="92.6" x2="197.6" y2="94.5" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="209.6" y1="101.3" x2="198.7" y2="102.2" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="210.0" y1="110.0" x2="199.0" y2="110.0" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="209.6" y1="118.7" x2="198.7" y2="117.8" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="208.5" y1="127.4" x2="197.6" y2="125.5" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="206.6" y1="135.9" x2="196.0" y2="133.0" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="204.0" y1="144.2" x2="193.6" y2="140.4" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="200.6" y1="152.3" x2="190.7" y2="147.6" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="196.6" y1="160.0" x2="187.1" y2="154.5" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="191.9" y1="167.4" x2="182.9" y2="161.0" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="186.6" y1="174.3" x2="178.2" y2="167.2" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="180.7" y1="180.7" x2="172.9" y2="172.9" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="174.3" y1="186.6" x2="167.2" y2="178.2" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="167.4" y1="191.9" x2="161.0" y2="182.9" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="160.0" y1="196.6" x2="154.5" y2="187.1" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="152.3" y1="200.6" x2="147.6" y2="190.7" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="144.2" y1="204.0" x2="140.4" y2="193.6" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="135.9" y1="206.6" x2="133.0" y2="196.0" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="127.4" y1="208.5" x2="125.5" y2="197.6" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="118.7" y1="209.6" x2="117.8" y2="198.7" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="110.0" y1="210.0" x2="110.0" y2="199.0" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="101.3" y1="209.6" x2="102.2" y2="198.7" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="92.6" y1="208.5" x2="94.5" y2="197.6" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="84.1" y1="206.6" x2="87.0" y2="196.0" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="75.8" y1="204.0" x2="79.6" y2="193.6" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="67.7" y1="200.6" x2="72.4" y2="190.7" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="60.0" y1="196.6" x2="65.5" y2="187.1" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="52.6" y1="191.9" x2="59.0" y2="182.9" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="45.7" y1="186.6" x2="52.8" y2="178.2" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="39.3" y1="180.7" x2="47.1" y2="172.9" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="33.4" y1="174.3" x2="41.8" y2="167.2" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="28.1" y1="167.4" x2="37.1" y2="161.0" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="23.4" y1="160.0" x2="32.9" y2="154.5" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="19.4" y1="152.3" x2="29.3" y2="147.6" stroke="#4ade80" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="16.0" y1="144.2" x2="26.4" y2="140.4" stroke="rgba(71,85,105,0.55)" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="13.4" y1="135.9" x2="24.0" y2="133.0" stroke="rgba(71,85,105,0.55)" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="11.5" y1="127.4" x2="22.4" y2="125.5" stroke="rgba(71,85,105,0.55)" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="10.4" y1="118.7" x2="21.3" y2="117.8" stroke="rgba(71,85,105,0.55)" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="10.0" y1="110.0" x2="21.0" y2="110.0" stroke="rgba(71,85,105,0.55)" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="10.4" y1="101.3" x2="21.3" y2="102.2" stroke="rgba(71,85,105,0.55)" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="11.5" y1="92.6" x2="22.4" y2="94.5" stroke="rgba(71,85,105,0.55)" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="13.4" y1="84.1" x2="24.0" y2="87.0" stroke="rgba(71,85,105,0.55)" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="16.0" y1="75.8" x2="26.4" y2="79.6" stroke="rgba(71,85,105,0.55)" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="19.4" y1="67.7" x2="29.3" y2="72.4" stroke="rgba(71,85,105,0.55)" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="23.4" y1="60.0" x2="32.9" y2="65.5" stroke="rgba(71,85,105,0.55)" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="28.1" y1="52.6" x2="37.1" y2="59.0" stroke="rgba(71,85,105,0.55)" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="33.4" y1="45.7" x2="41.8" y2="52.8" stroke="rgba(71,85,105,0.55)" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="39.3" y1="39.3" x2="47.1" y2="47.1" stroke="rgba(71,85,105,0.55)" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="45.7" y1="33.4" x2="52.8" y2="41.8" stroke="rgba(71,85,105,0.55)" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="52.6" y1="28.1" x2="59.0" y2="37.1" stroke="rgba(71,85,105,0.55)" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="60.0" y1="23.4" x2="65.5" y2="32.9" stroke="rgba(71,85,105,0.55)" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="67.7" y1="19.4" x2="72.4" y2="29.3" stroke="rgba(71,85,105,0.55)" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="75.8" y1="16.0" x2="79.6" y2="26.4" stroke="rgba(71,85,105,0.55)" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="84.1" y1="13.4" x2="87.0" y2="24.0" stroke="rgba(71,85,105,0.55)" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="92.6" y1="11.5" x2="94.5" y2="22.4" stroke="rgba(71,85,105,0.55)" stroke-width="1.4" stroke-linecap="round"/>
          <line x1="101.3" y1="10.4" x2="102.2" y2="21.3" stroke="rgba(71,85,105,0.55)" stroke-width="1.4" stroke-linecap="round"/>
        </svg>
        <div class="gauge-center">
          <div class="gauge-time" id="gauge-time">--:--:--</div>
          <div class="gauge-date" id="gauge-date">----.--.-- - ---</div>
        </div>
      </div>

      <!-- 原版左侧：选课倒计时横幅 -->
      <div id="login-round-banner" class="login-round-banner lrb-left" v-if="roundData.show">
        <div class="lrb-glow"></div>
        <div class="lrb-head">
          <span class="lrb-badge">第 {{ roundData.round || 1 }} 轮</span>
          <span class="lrb-pulse"></span>
          <span class="lrb-status">{{ roundData.status || '选课进行中' }}</span>
          <span class="lrb-label">距本轮结束</span>
        </div>
        <div class="lrb-count">
          <div class="lrb-cell"><b>{{ pad(roundData.days) }}</b><span>天</span></div>
          <i>:</i>
          <div class="lrb-cell"><b>{{ pad(roundData.hours) }}</b><span>时</span></div>
          <i>:</i>
          <div class="lrb-cell"><b>{{ pad(roundData.mins) }}</b><span>分</span></div>
          <i>:</i>
          <div class="lrb-cell"><b>{{ pad(roundData.secs) }}</b><span>秒</span></div>
        </div>
        <div class="lrb-progress">
          <div class="lrb-progress-track"><div class="lrb-progress-fill" :style="{ width: roundData.progress + '%' }"></div></div>
          <span class="lrb-progress-label">进度 {{ roundData.progress }}%</span>
        </div>
      </div>

      <!-- 功能亮点胶囊 -->
      <div class="auth-features" aria-hidden="true">
        <span class="feat"><i></i>智能排课</span>
        <span class="feat"><i></i>积分选课</span>
        <span class="feat"><i></i>实时看板</span>
      </div>

      <div class="auth-left-foot">智学选课 · 大学生课程管理平台</div>
    </div>

    <!-- 右侧 40% -->
    <div class="auth-right">
      <div class="auth-card">
        <div class="auth-card-head">
          <h2>欢迎登录</h2>
          <p>请使用您的账号登录系统</p>
        </div>
        <div class="auth-tabs">
          <button class="auth-tab" :class="{ active: tab==='login' }" @click="tab='login'">登录</button>
          <button class="auth-tab" :class="{ active: tab==='register' }" @click="tab='register'">注册</button>
        </div>

        <form v-if="tab==='login'" class="auth-form" @submit.prevent="doLogin">
          <div class="form-group input-icon">
            <svg class="icon input-left-icon"><use href="#i-user"/></svg>
            <input type="text" v-model="form.no" placeholder="学号 / 工号 / 管理员账号" required>
          </div>
          <div class="form-group input-icon">
            <svg class="icon input-left-icon"><use href="#i-lock"/></svg>
            <input :type="pwdVisible?'text':'password'" v-model="form.password" placeholder="请输入密码" required>
            <button type="button" class="pwd-eye" @click="pwdVisible=!pwdVisible">
              <svg class="icon"><use :href="pwdVisible?'#i-eye':'#i-eye-off'"/></svg>
            </button>
          </div>
          <div class="form-group captcha-row">
            <div class="input-icon captcha-input">
              <svg class="icon input-left-icon"><use href="#i-check-circle"/></svg>
              <input type="text" v-model="form.captcha" placeholder="验证码" maxlength="4" autocomplete="off" required>
            </div>
            <canvas ref="captchaRef" width="112" height="44" title="点击刷新验证码" @click="drawCaptcha"></canvas>
          </div>
          <div class="remember-row">
            <label class="remember-label"><input type="checkbox" v-model="remember"> 记住登录状态</label>
          </div>
          <button type="submit" id="login-btn" class="btn btn-primary btn-block" :class="{ 'btn-globing': globing }">
            <span class="btn-text">登 录</span>
            <span class="btn-particles" aria-hidden="true"><i></i><i></i><i></i><i></i><i></i><i></i><i></i></span>
            <svg class="btn-check" viewBox="0 0 52 52" aria-hidden="true"><path class="btn-check-circle" fill="none" d="M26 0 C11.6 0 0 11.6 0 26 s11.6 26 26 26 26-11.6 26-26 S40.4 0 26 0 z"/><path class="btn-check-tick" fill="none" d="M14.1 27.2 l7.1 7.2 15.4-15.4"/></svg>
          </button>
          <div class="quick-login">
            <span class="quick-login-label">快捷登录：</span>
            <button type="button" class="btn btn-outline btn-sm quick-btn" @click="quickLogin('student')">学生</button>
            <button type="button" class="btn btn-outline btn-sm quick-btn" @click="quickLogin('teacher')">老师</button>
            <button type="button" class="btn btn-outline btn-sm quick-btn" @click="quickLogin('admin')">管理员</button>
          </div>
          <div class="auth-tip">密码均为 <b>123456</b></div>
        </form>

        <form v-else class="auth-form" @submit.prevent="doRegister">
          <div class="form-group"><label>姓名</label><input type="text" v-model="reg.name" required></div>
          <div class="form-group"><label>学号</label><input type="text" v-model="reg.no" required></div>
          <div class="form-group"><label>密码</label><input type="password" v-model="reg.password" required></div>
          <div class="form-row">
            <div class="form-group"><label>学院</label>
              <select v-model="reg.college">
                <option>计算机学院</option><option>信息学院</option><option>经管学院</option>
                <option>外语学院</option><option>数学学院</option>
              </select>
            </div>
            <div class="form-group"><label>年级</label>
              <select v-model="reg.grade">
                <option>2021</option><option>2022</option><option>2023</option>
                <option>2024</option><option>2025</option><option>2026</option>
              </select>
            </div>
          </div>
          <div class="form-group"><label>专业</label><input type="text" v-model="reg.major" placeholder="如：软件工程"></div>
          <div class="form-group"><label>班级</label><input type="text" v-model="reg.classNo" placeholder="如：软工2301班"></div>
          <div class="form-group"><label>联系电话</label><input type="text" v-model="reg.phone" placeholder="请输入手机号"></div>
          <button type="submit" class="btn btn-primary btn-block">注 册</button>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, getCurrentInstance } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import api from '../api'

const router = useRouter()
const auth = useAuthStore()
const { proxy } = getCurrentInstance()!

const tab = ref<'login'|'register'>('login')
const pwdVisible = ref(false)
const globing = ref(false)
const remember = ref(true)
const captchaRef = ref<HTMLCanvasElement | null>(null)
const captchaCode = ref('')

// === 原版 RoundBanner 轮次倒计时 ===
const roundData = reactive({
  show: false, round: 0, status: '选课进行中',
  days: 0, hours: 0, mins: 0, secs: 0, progress: 0
})
let roundTimer: number | null = null
function parseISO(s: string | null | undefined) {
  if (!s) return null
  const t = new Date(s); return isNaN(t.getTime()) ? null : t
}
function loadLoginRoundBanner() {
  api.get('/system/config').then((r: any) => {
    const c = r?.success ? (r.data || {}) : {}
    const open = !!c.roundOpen
    roundData.round = c.currentRound || 1
    const oa = parseISO(c.roundOpenAt)
    const ca = parseISO(c.roundCloseAt)
    if (roundTimer) { clearInterval(roundTimer); roundTimer = null }
    const tick = () => {
      const now = Date.now()
      let ms = 0
      if (open && ca) {
        // 进行中 → 距结束
        roundData.status = '选课进行中'
        ms = ca.getTime() - now
      } else if (!open && oa) {
        // 未开始 → 距开始
        roundData.status = '距开始'
        ms = oa.getTime() - now
      } else {
        roundData.status = open ? '选课进行中' : '选课已关闭'
        roundData.show = false
        return
      }
      roundData.show = true
      if (ms <= 0) {
        roundData.days = roundData.hours = roundData.mins = roundData.secs = 0
      } else {
        roundData.days = Math.floor(ms / 86400000)
        roundData.hours = Math.floor((ms % 86400000) / 3600000)
        roundData.mins = Math.floor((ms % 3600000) / 60000)
        roundData.secs = Math.floor((ms % 60000) / 1000)
      }
      // 进度
      if (oa && ca) {
        const total = ca.getTime() - oa.getTime()
        const done = now - oa.getTime()
        roundData.progress = Math.max(0, Math.min(100, Math.round(done / total * 100)))
      }
    }
    tick()
    roundTimer = window.setInterval(tick, 1000)
  }).catch(() => { roundData.show = false })
}

const form = reactive({ no: '', password: '', captcha: '' })
const reg = reactive({ name: '', no: '', password: '', college: '计算机学院', grade: '2026', major: '', classNo: '', phone: '' })

// === Gauge 仪表盘时钟（原版 IIFE 改成 Vue） ===
let tickLines: SVGLineElement[] = []
let tickTimer: number | null = null
const WEEK = ['SUN','MON','TUE','WED','THU','FRI','SAT']
const pad = (n: number) => n < 10 ? '0' + n : '' + n
const GREEN = '#4ade80'
const GRAY = 'rgba(71,85,105,0.55)'

function initTicks() {
  const svg = document.getElementById('gauge-svg-inner') as SVGSVGElement | null
  if (!svg) return
  const lines = svg.querySelectorAll('line')
  tickLines = []
  for (let i = 0; i < lines.length; i++) {
    const x1 = parseFloat(lines[i].getAttribute('x1') || '')
    if (!isNaN(x1)) tickLines.push(lines[i])
  }
}
function updateTicks(percent: number) {
  const total = tickLines.length
  if (total === 0) return
  const greenCount = Math.min(total, Math.round(total * percent / 100))
  for (let i = 0; i < total; i++) {
    tickLines[i].setAttribute('stroke', i < greenCount ? GREEN : GRAY)
  }
}
function gaugeTick() {
  const t = document.getElementById('gauge-time')
  const d = document.getElementById('gauge-date')
  if (!t || !d) return
  const now = new Date()
  t.textContent = pad(now.getHours()) + ':' + pad(now.getMinutes()) + ':' + pad(now.getSeconds())
  d.textContent = now.getFullYear() + '.' + pad(now.getMonth()+1) + '.' + pad(now.getDate()) + ' - ' + WEEK[now.getDay()]
  const sec = now.getHours() * 3600 + now.getMinutes() * 60 + now.getSeconds()
  updateTicks(sec / 864)
}

// === 验证码 Canvas（原版 IIFE 改成 Vue） ===
function drawCaptcha() {
  if (!captchaRef.value) return
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
  captchaCode.value = ''
  for (let i = 0; i < 4; i++) captchaCode.value += chars.charAt(Math.floor(Math.random() * chars.length))
  const cv = captchaRef.value
  const ctx = cv.getContext('2d')!
  ctx.clearRect(0, 0, cv.width, cv.height)
  ctx.fillStyle = 'rgba(15,23,42,0.85)'
  ctx.fillRect(0, 0, cv.width, cv.height)
  const palette = ['#4ade80', '#22d3ee', '#a78bfa', '#fbbf24']
  for (let i = 0; i < 4; i++) {
    ctx.strokeStyle = 'rgba(74,222,128,' + (0.12 + Math.random() * 0.18) + ')'
    ctx.beginPath()
    ctx.moveTo(Math.random() * cv.width, Math.random() * cv.height)
    ctx.lineTo(Math.random() * cv.width, Math.random() * cv.height)
    ctx.stroke()
  }
  for (let i = 0; i < captchaCode.value.length; i++) {
    ctx.save()
    ctx.font = '700 22px Georgia, "Times New Roman", serif'
    ctx.fillStyle = palette[i % palette.length]
    ctx.shadowColor = 'rgba(74,222,128,0.3)'
    ctx.shadowBlur = 2
    ctx.translate(18 + i * 24, 28)
    ctx.rotate((Math.random() - 0.5) * 0.5)
    ctx.fillText(captchaCode.value[i], -8, 6)
    ctx.restore()
  }
  for (let i = 0; i < 24; i++) {
    ctx.fillStyle = 'rgba(74,222,128,0.4)'
    ctx.fillRect(Math.random() * cv.width, Math.random() * cv.height, 1.6, 1.6)
  }
}

// === 登录 ===
async function doLogin() {
  if (!form.no || !form.password || !form.captcha) return
  if (form.captcha.toUpperCase() !== captchaCode.value.toUpperCase()) {
    ;(proxy as any).$message?.error('验证码错误')
    drawCaptcha()
    return
  }
  globing.value = true
  try {
    const r = await api.post('/auth/login', { no: form.no, password: form.password })
    if (r.success) {
      auth.setToken(r.data.token)
      auth.setUser(r.data.user)
      if (remember.value) localStorage.setItem('zhixue_remember', form.no)
      setTimeout(() => {
        globing.value = false
        router.push('/dashboard')
      }, 800)
    } else {
      globing.value = false
      ;(proxy as any).$message?.error(r.message || '登录失败')
      drawCaptcha()
    }
  } catch (e: any) {
    globing.value = false
    ;(proxy as any).$message?.error(e?.response?.data?.message || '登录失败')
    drawCaptcha()
  }
}

async function quickLogin(role: 'student'|'teacher'|'admin') {
  const map = { student: '2023010101', teacher: 'T001', admin: 'admin' }
  form.no = map[role]
  form.password = '123456'
  form.captcha = ''
  ;(document.querySelector('#login-captcha') as HTMLInputElement)?.focus()
}

async function doRegister() {
  try {
    const r = await api.post('/auth/register', reg)
    if (r.success) {
      ;(proxy as any).$message?.success('注册成功，请登录')
      tab.value = 'login'
      form.no = reg.no
      form.password = ''
    } else {
      ;(proxy as any).$message?.error(r.message || '注册失败')
    }
  } catch (e: any) {
    ;(proxy as any).$message?.error(e?.response?.data?.message || '注册失败')
  }
}

onMounted(() => {
  // ⚠️ 强制暗色主题：Login.vue 是独立深色仪表盘风格，不走 CSS 变量
  document.documentElement.setAttribute('data-theme', 'dark')
  initTicks()
  gaugeTick()
  tickTimer = window.setInterval(gaugeTick, 1000)
  drawCaptcha()
  loadLoginRoundBanner()
  // 回填记住的账号
  const rem = localStorage.getItem('zhixue_remember')
  if (rem) { form.no = rem; remember.value = true }
})
onUnmounted(() => {
  if (tickTimer) clearInterval(tickTimer)
  if (roundTimer) clearInterval(roundTimer)
})
</script>

<!-- ======= 原版 styles.css L3155-3662 登录页 CSS 1:1 照搬 ======= -->
<!-- ⚠️ 必须 scoped：此前全局 .icon / svg.icon 规则会污染登录后的所有页面，
     导致侧边栏图标被 svg.icon{height:100%} 撑成 150px 默认高度、菜单拉伸错乱（刷新才恢复） -->
<style scoped>
/* ============ 基础 ============ */
* { box-sizing: border-box; margin: 0; padding: 0; }
.auth-page {
  width: 100%; height: 100%;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, "PingFang SC", "Microsoft YaHei", sans-serif;
  background: #0a0f16; color: #e2e8f0;
  -webkit-font-smoothing: antialiased;
}
a { color: inherit; text-decoration: none; }
button { cursor: pointer; border: none; background: none; font: inherit; color: inherit; }
input, select { font: inherit; }

/* ============ SVG Icon ============ */
.icon { width: 20px; height: 20px; display: block; }
.icon svg, svg.icon { width: 100%; height: 100%; fill: none; stroke: currentColor; stroke-width: 1.9; stroke-linecap: round; stroke-linejoin: round; }

/* ============ auth-page ============ */
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: stretch;
  justify-content: flex-start;
  position: relative;
  overflow: hidden;
}

/* ============ 左侧 60% ============ */
.auth-left {
  position: relative; z-index: 1;
  width: 60%; min-height: 100vh;
  background: #0a0f16;
  overflow: hidden;
  display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  padding: 48px 40px;
}
.auth-left::before {
  content: ''; position: absolute; inset: 0;
  background-image:
    linear-gradient(rgba(74,222,128,0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(74,222,128,0.05) 1px, transparent 1px);
  background-size: 44px 44px; pointer-events: none;
}
.auth-left::after {
  content: ''; position: absolute; inset: 0;
  background:
    radial-gradient(560px 560px at 50% 52%, rgba(34,197,94,0.20), transparent 65%),
    radial-gradient(900px 520px at 88% 112%, rgba(34,197,94,0.10), transparent 60%),
    radial-gradient(720px 420px at -5% -8%, rgba(59,130,246,0.08), transparent 60%);
  pointer-events: none;
}

/* brand */
.auth-brand {
  position: absolute; top: 34px; left: 42px;
  display: flex; align-items: center; gap: 12px; z-index: 1;
}
.auth-brand .logo-icon {
  width: 40px; height: 40px; font-size: 19px; border-radius: 11px;
  background: linear-gradient(135deg, #16a34a, #22c55e); color: #fff;
  display: flex; align-items: center; justify-content: center; font-weight: 700;
  box-shadow: 0 6px 20px rgba(34,197,94,0.35);
}
.auth-brand-name { font-size: 18px; font-weight: 700; color: #e2e8f0; letter-spacing: 1px; }
.auth-left-foot {
  position: absolute; bottom: 28px; left: 0; right: 0; text-align: center;
  font-size: 12px; letter-spacing: 1.5px; color: rgba(100,116,139,0.75); z-index: 1;
}

/* gauge */
.auth-gauge { position: relative; z-index: 1; width: 300px; height: 300px; }
.gauge-svg { width: 100%; height: 100%; overflow: visible; }
.gauge-center {
  position: absolute; top: 50%; left: 50%;
  transform: translate(-50%, -50%); text-align: center; pointer-events: none;
}
.gauge-time {
  font-family: "JetBrains Mono", "Consolas", "SF Mono", Menlo, monospace;
  font-size: 30px; font-weight: 600; letter-spacing: 2px;
  color: #f1f5f9; line-height: 1.1; font-variant-numeric: tabular-nums;
}
.gauge-date {
  margin-top: 6px; font-size: 11px; letter-spacing: 1.5px;
  color: rgba(148,163,184,0.85); text-transform: uppercase;
  font-family: "JetBrains Mono", "Consolas", monospace;
}

/* ============ 右侧 40% ============ */
.auth-right {
  width: 40%; min-height: 100vh; background: #0a0f16;
  position: relative; display: flex; align-items: center; justify-content: center;
  padding: 40px 28px; overflow-y: auto;
}
.auth-right::before {
  content: ''; position: absolute; inset: 0; pointer-events: none;
  background-image:
    linear-gradient(rgba(74,222,128,0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(74,222,128,0.05) 1px, transparent 1px);
  background-size: 44px 44px;
  mask-image: radial-gradient(ellipse at center, rgba(0,0,0,0.9) 0%, rgba(0,0,0,0) 70%);
  -webkit-mask-image: radial-gradient(ellipse at center, rgba(0,0,0,0.9) 0%, rgba(0,0,0,0) 70%);
}
.auth-right::after {
  content: ''; position: absolute; inset: 0; pointer-events: none;
  background:
    radial-gradient(600px 500px at 80% 10%, rgba(34,197,94,0.12), transparent 60%),
    radial-gradient(500px 500px at 10% 90%, rgba(59,130,246,0.08), transparent 60%);
}

/* auth-card */
.auth-card {
  position: relative; z-index: 1;
  width: 400px; max-width: 100%;
  background: rgba(15,23,42,0.72) !important;
  backdrop-filter: blur(14px); -webkit-backdrop-filter: blur(14px);
  border: 1px solid rgba(74,222,128,0.22) !important;
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.45), 0 0 40px rgba(34,197,94,0.06) !important;
  padding: 32px 32px 26px;
  animation: zxFadeUp 0.45s ease;
  max-height: 94vh; overflow-y: auto;
}
@keyframes zxFadeUp {
  from { opacity: 0; transform: translateY(16px); }
  to { opacity: 1; transform: translateY(0); }
}
.auth-card-head { text-align: center; margin-bottom: 20px; }
.auth-card-head h2 { margin: 0 0 6px; font-size: 22px; font-weight: 700; color: #f1f5f9; }
.auth-card-head p { margin: 0; font-size: 13px; color: #94a3b8; }

/* tabs */
.auth-tabs {
  display: flex; background: rgba(15,23,42,0.5);
  border: 1px solid rgba(74,222,128,0.14); border-radius: 10px; padding: 4px; margin-bottom: 18px;
}
.auth-tab {
  flex: 1; padding: 8px; text-align: center; font-size: 13px; font-weight: 600;
  color: #94a3b8; border-radius: 8px; transition: all .2s;
}
.auth-tab.active { background: rgba(34,197,94,0.14); color: #4ade80; }

/* form */
.auth-form .form-group { margin-bottom: 14px; }
.form-group label { display: block; font-size: 13px; font-weight: 500; color: #cbd5e1; margin-bottom: 6px; }
.form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.auth-form input, .auth-form select {
  width: 100%; height: 44px; border-radius: 10px;
  background: rgba(15,23,42,0.6); border: 1px solid rgba(74,222,128,0.18);
  color: #e2e8f0; padding: 0 14px; font-size: 14px;
  transition: border-color .2s, box-shadow .2s;
  -webkit-text-fill-color: #e2e8f0;
}
.auth-form input::placeholder { color: #64748b; }
.auth-form input:focus, .auth-form select:focus {
  outline: none; border-color: #22c55e; box-shadow: 0 0 0 3px rgba(34,197,94,0.18);
}

/* 带图标输入框 */
.input-icon { position: relative; }
.input-icon .input-left-icon {
  position: absolute; left: 13px; top: 50%; transform: translateY(-50%);
  width: 17px; height: 17px; color: #64748b; pointer-events: none;
}
.input-icon input { padding-left: 40px !important; }
.pwd-eye {
  position: absolute; right: 10px; top: 50%; transform: translateY(-50%);
  color: #64748b; padding: 4px;
}
/* 眼睛按钮无固定尺寸，svg.icon 的 100% 会解析失败退回 300x150 默认值，必须显式定尺寸 */
.pwd-eye .icon { width: 18px; height: 18px; }

/* captcha */
.captcha-row { display: flex; gap: 10px; align-items: stretch; }
.captcha-row .captcha-input { flex: 1; margin-bottom: 0; }
.captcha-row .captcha-input input { height: 44px; }
#captcha-canvas {
  width: 112px; height: 44px; flex-shrink: 0; border-radius: 10px;
  border: 1px solid rgba(74,222,128,0.18); background: rgba(15,23,42,0.6); cursor: pointer;
}

/* remember */
.remember-row { display: flex; align-items: center; margin-bottom: 14px; }
.remember-label { font-size: 13px; color: #94a3b8; display: flex; align-items: center; gap: 6px; cursor: pointer; white-space: nowrap; }
/* 必须显式覆盖：.auth-form input 通用规则会命中复选框，把它撑成 100%x44px 的大白块 */
.remember-label input[type="checkbox"] {
  width: 15px; height: 15px; padding: 0; margin: 0; flex: 0 0 15px;
  border-radius: 4px; accent-color: #22c55e; cursor: pointer;
  -webkit-text-fill-color: initial;
}

/* btn */
.btn-block { width: 100%; }
.btn-primary {
  background: linear-gradient(135deg, #22c55e, #16a34a); border: none;
  border-radius: 10px; height: 44px; font-size: 15px; letter-spacing: 4px; font-weight: 600;
  box-shadow: 0 6px 18px rgba(34,197,94,0.35); color: #fff;
  transition: all .2s; position: relative; overflow: hidden;
}
.btn-primary:hover { background: linear-gradient(135deg, #16a34a, #15803d); transform: translateY(-1px); }
.btn-outline {
  background: rgba(15,23,42,0.5); border: 1px solid rgba(74,222,128,0.22);
  border-radius: 8px; color: #94a3b8; font-size: 12px; font-weight: 500; padding: 7px 14px;
}
.btn-outline:hover { color: #4ade80; border-color: #4ade80; }
.btn-sm { padding: 7px 14px; font-size: 12px; }

/* globing 光球登录动画 */
#login-btn .btn-text, #login-btn .btn-particles, #login-btn .btn-check { transition: opacity .25s; }
#login-btn .btn-particles, #login-btn .btn-check { display: none; }
#login-btn.btn-globing .btn-text { opacity: 0; }
#login-btn.btn-globing .btn-particles { display: flex; opacity: 1; gap: 4px; }
#login-btn.btn-globing.btn-check-ok .btn-particles { display: none; }
#login-btn.btn-globing.btn-check-ok .btn-check { display: block; width: 26px; height: 26px; opacity: 1; }
#login-btn.btn-globing {
  width: 44px; height: 44px; padding: 0; border-radius: 50% !important;
  margin: 0 auto; display: block;
  background: radial-gradient(circle at 35% 35%, #4ade80, #16a34a 55%, #15803d) !important;
  box-shadow: 0 0 0 2px rgba(34,197,94,0.25), 0 0 28px rgba(34,197,94,0.55), 0 0 60px rgba(34,197,94,0.25), inset 0 0 14px rgba(220,252,231,0.35);
  animation: globPulse 1.6s ease-in-out infinite;
}
@keyframes globPulse {
  0%,100% { box-shadow: 0 0 0 2px rgba(34,197,94,0.25), 0 0 28px rgba(34,197,94,0.55), 0 0 60px rgba(34,197,94,0.25), inset 0 0 14px rgba(220,252,231,0.35); }
  50%     { box-shadow: 0 0 0 2px rgba(34,197,94,0.40), 0 0 40px rgba(74,222,128,0.75), 0 0 80px rgba(34,197,94,0.35), inset 0 0 20px rgba(220,252,231,0.55); }
}
#login-btn.btn-globing .btn-particles {
  position: absolute; inset: 0; display: flex; align-items: center; justify-content: center;
  animation: particleSpin 1.1s linear infinite;
}
@keyframes particleSpin { to { transform: rotate(360deg); } }
#login-btn.btn-globing .btn-particles i {
  width: 4px; height: 4px; border-radius: 50%; background: #4ade80; display: block;
  box-shadow: 0 0 6px #4ade80; animation: particleBlink 0.6s ease-in-out infinite alternate;
}
@keyframes particleBlink { from { opacity: 0.3; transform: scale(0.6); } to { opacity: 1; transform: scale(1); } }
.btn-particles i:nth-child(1) { animation-delay: 0.0s; }
.btn-particles i:nth-child(2) { animation-delay: 0.1s; }
.btn-particles i:nth-child(3) { animation-delay: 0.2s; }
.btn-particles i:nth-child(4) { animation-delay: 0.3s; }
.btn-particles i:nth-child(5) { animation-delay: 0.4s; }
.btn-particles i:nth-child(6) { animation-delay: 0.5s; }
.btn-particles i:nth-child(7) { animation-delay: 0.6s; }

/* quick login + tip */
.quick-login {
  display: flex; align-items: center; gap: 8px; margin-top: 14px; flex-wrap: wrap;
}
.quick-login-label { font-size: 12px; color: #64748b; }
.auth-tip { margin-top: 14px; text-align: center; font-size: 12px; color: #64748b; }
.auth-tip b { color: #4ade80; }

/* autocomplete 修复 */
.auth-card input:-webkit-autofill, .auth-card input:-webkit-autofill:hover, .auth-card input:-webkit-autofill:focus {
  -webkit-text-fill-color: #e2e8f0 !important;
  box-shadow: 0 0 0 1000px rgba(15,23,42,0.92) inset !important;
  border-color: #22c55e !important;
  transition: background-color 9999s ease-in-out 0s;
}

/* ============ 原版 RoundBanner 选课倒计时横幅 (styles.css L3155-3320) ============ */
.login-round-banner {
  position: relative; display: flex; flex-direction: column;
  gap: 10px; padding: 14px 16px 16px; border-radius: 16px; margin-top: 22px;
  overflow: hidden;
  background: linear-gradient(135deg, rgba(30,41,59,0.85), rgba(30,27,75,0.6));
  border: 1px solid rgba(255,255,255,0.08);
  animation: zxFadeUp 0.5s ease both; z-index: 1;
  width: 320px; max-width: 100%;
}
.lrb-glow {
  position: absolute; top: -40px; right: -30px;
  width: 140px; height: 140px; border-radius: 50%;
  background: radial-gradient(circle, rgba(139,92,246,0.22), transparent 70%);
  pointer-events: none;
}
.lrb-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.lrb-pulse {
  width: 8px; height: 8px; border-radius: 50%; background: #10b981;
  box-shadow: 0 0 0 0 rgba(16,185,129,0.50);
  animation: lrbPing 1.8s ease-out infinite;
}
@keyframes lrbPing {
  0% { box-shadow: 0 0 0 0 rgba(16,185,129,0.45); }
  70% { box-shadow: 0 0 0 9px rgba(16,185,129,0); }
  100% { box-shadow: 0 0 0 0 rgba(16,185,129,0); }
}
.lrb-badge {
  font-size: 12px; font-weight: 700; padding: 3px 10px; border-radius: 12px;
  background: rgba(79,70,229,0.25); color: #a5b4fc;
  border: 1px solid rgba(139,92,246,0.35);
}
.lrb-status { font-size: 13px; font-weight: 700; color: #e2e8f0; }
.lrb-label { font-size: 11px; color: #94a3b8; margin-left: auto; }
.lrb-count { display: flex; align-items: stretch; gap: 6px; }
.lrb-cell {
  flex: 1; text-align: center; padding: 8px 2px 6px; border-radius: 10px;
  background: rgba(15,23,42,0.55); border: 1px solid rgba(255,255,255,0.06);
}
.lrb-cell b {
  display: block; font-size: 20px; font-weight: 800; line-height: 1.1;
  font-variant-numeric: tabular-nums;
  background: linear-gradient(135deg, #a5b4fc, #c084fc);
  -webkit-background-clip: text; background-clip: text; -webkit-text-fill-color: transparent;
}
.lrb-cell span { display: block; font-size: 10px; color: #94a3b8; margin-top: 2px; }
.lrb-count > i { align-self: center; font-style: normal; font-weight: 800; color: #94a3b8; opacity: 0.7; }
.lrb-progress { display: flex; align-items: center; gap: 10px; }
.lrb-progress-track {
  flex: 1; height: 6px; border-radius: 3px; overflow: hidden;
  background: rgba(15,23,42,0.6); border: 1px solid rgba(255,255,255,0.05);
}
.lrb-progress-fill {
  height: 100%; border-radius: 3px; transition: width .8s ease;
  background: linear-gradient(90deg, #6366f1, #a855f7, #22d3ee);
}
.lrb-progress-label { font-size: 10.5px; color: #94a3b8; white-space: nowrap; }

/* ============ 视觉升级：光晕球 / 光环 / 卡片质感 / 流光 ============ */
/* 左侧漂浮光晕球 */
.auth-orbs { position: absolute; inset: 0; z-index: 0; overflow: hidden; pointer-events: none; }
.auth-orbs span {
  position: absolute; border-radius: 50%; filter: blur(64px); opacity: .55;
  animation: orbFloat 16s ease-in-out infinite;
}
.auth-orbs span:nth-child(1) {
  width: 280px; height: 280px; left: -70px; top: 8%;
  background: radial-gradient(circle, rgba(34,197,94,.55), transparent 70%);
}
.auth-orbs span:nth-child(2) {
  width: 320px; height: 320px; right: -90px; bottom: 4%;
  background: radial-gradient(circle, rgba(59,130,246,.45), transparent 70%);
  animation-delay: -6s; animation-duration: 19s;
}
.auth-orbs span:nth-child(3) {
  width: 220px; height: 220px; left: 32%; bottom: -50px;
  background: radial-gradient(circle, rgba(139,92,246,.38), transparent 70%);
  animation-delay: -11s; animation-duration: 22s;
}
@keyframes orbFloat {
  0%, 100% { transform: translate3d(0, 0, 0); }
  50%      { transform: translate3d(18px, -34px, 0); }
}

/* 仪表盘时钟背后的柔和光环 */
.auth-gauge::before {
  content: ''; position: absolute; inset: -34px; border-radius: 50%;
  background: conic-gradient(from 180deg,
    rgba(34,197,94,.20), rgba(59,130,246,.14), rgba(139,92,246,.16), rgba(34,197,94,.20));
  filter: blur(30px); opacity: .65; z-index: -1;
}

/* 登录卡片顶部发光渐变装饰条 */
.auth-card::before {
  content: ''; position: absolute; top: 0; left: 26px; right: 26px; height: 3px;
  border-radius: 0 0 5px 5px;
  background: linear-gradient(90deg, transparent, #4ade80, #22d3ee, #a78bfa, transparent);
  box-shadow: 0 0 14px rgba(34,197,94,.55);
  opacity: .92;
}

/* 标题渐变文字 */
.auth-card-head h2 {
  background: linear-gradient(135deg, #4ade80, #22d3ee);
  -webkit-background-clip: text; background-clip: text;
  -webkit-text-fill-color: transparent; display: inline-block;
}

/* 登录/注册按钮流光 */
.btn-primary::before {
  content: ''; position: absolute; top: 0; left: -65%; width: 45%; height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255,255,255,.38), transparent);
  transform: skewX(-20deg); pointer-events: none;
}
.btn-primary:hover::before { animation: btnShine .85s ease; }
@keyframes btnShine { to { left: 125%; } }

/* 功能亮点胶囊 */
.auth-features {
  display: flex; gap: 10px; flex-wrap: wrap; justify-content: center;
  margin-top: 26px; z-index: 1; position: relative;
}
.auth-features .feat {
  display: flex; align-items: center; gap: 6px;
  padding: 7px 14px; border-radius: 999px;
  background: rgba(15,23,42,.5); border: 1px solid rgba(74,222,128,.18);
  font-size: 12px; color: #cbd5e1; letter-spacing: .5px;
  backdrop-filter: blur(6px); -webkit-backdrop-filter: blur(6px);
  transition: border-color .2s, color .2s;
}
.auth-features .feat:hover { border-color: rgba(74,222,128,.5); color: #e2e8f0; }
.auth-features .feat i {
  width: 6px; height: 6px; border-radius: 50%;
  background: linear-gradient(135deg, #4ade80, #22d3ee);
  box-shadow: 0 0 8px rgba(34,197,94,.6);
}

/* 减少动效：关闭漂浮与流光，保留静态视觉 */
@media (prefers-reduced-motion: reduce) {
  .auth-orbs span { animation: none; }
  .btn-primary:hover::before { animation: none; }
}

/* 响应式 */
@media (max-width: 960px) {
  .auth-page { flex-direction: column; }
  .auth-left, .auth-right { width: 100%; min-height: auto; }
  .auth-left { padding: 60px 24px 30px; }
  .auth-gauge { width: 180px; height: 180px; }
  .auth-right { padding: 24px; }
  .auth-card { width: 100%; max-width: 400px; padding: 26px 22px; }
}
</style>
