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

      <!-- 左侧：选课倒计时横幅（常驻显示，按时间窗切换状态，不会再整块消失） -->
      <div id="login-round-banner" class="login-round-banner lrb-left" :class="'lrb-' + roundData.state">
        <div class="lrb-glow"></div>
        <div class="lrb-head">
          <span class="lrb-badge">第 {{ roundData.round || 1 }} 轮</span>
          <span class="lrb-pulse" v-if="roundData.state === 'active'"></span>
          <span class="lrb-status">{{ roundData.status }}</span>
          <span class="lrb-label">{{ roundData.label }}</span>
        </div>
        <div class="lrb-count" v-if="roundData.showCount">
          <div class="lrb-cell"><b>{{ pad(roundData.days) }}</b><span>天</span></div>
          <i>:</i>
          <div class="lrb-cell"><b>{{ pad(roundData.hours) }}</b><span>时</span></div>
          <i>:</i>
          <div class="lrb-cell"><b>{{ pad(roundData.mins) }}</b><span>分</span></div>
          <i>:</i>
          <div class="lrb-cell"><b>{{ pad(roundData.secs) }}</b><span>秒</span></div>
        </div>
        <div class="lrb-hint" v-else>{{ roundData.hint }}</div>
        <div class="lrb-progress">
          <div class="lrb-progress-track"><div class="lrb-progress-fill" :style="{ width: roundData.progress + '%' }"></div></div>
          <span class="lrb-progress-label">{{ roundData.progressText }}</span>
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
            <canvas ref="captchaRef" class="captcha-canvas" width="112" height="44" title="点击刷新验证码" @click="drawCaptcha"></canvas>
          </div>
          <div class="remember-row">
            <label class="remember-label"><input type="checkbox" v-model="remember"> 记住登录状态</label>
          </div>
          <button type="submit" id="login-btn" class="btn btn-primary btn-block" :class="{ 'btn-globing': globing, 'btn-check-ok': loginOk }">
            <span class="btn-text">登 录</span>
            <span class="orb" aria-hidden="true">
              <i class="orb-wave"></i>
              <i class="orb-wave orb-wave-2"></i>
              <span class="orb-ring"></span>
              <span class="orb-core">
                <svg class="orb-check" viewBox="0 0 24 24" fill="none">
                  <path d="M5 12.6l4.4 4.4L19 7.4" />
                </svg>
              </span>
            </span>
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

    <!-- 登录成功欢迎层 -->
    <transition name="lw-fade">
      <div v-if="welcome" class="login-welcome">
        <div class="lw-inner">
          <h2 class="lw-title">欢迎进入智学选课系统</h2>
          <p class="lw-sub">{{ welcomeSub }}</p>
          <div class="lw-dots"><i></i><i></i><i></i></div>
        </div>
      </div>
    </transition>
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
const loginOk = ref(false)
const welcome = ref(false)
const welcomeSub = ref('正在为你加载课程信息…')
const remember = ref(true)
const captchaRef = ref<HTMLCanvasElement | null>(null)
const captchaCode = ref('')

// === 左侧 RoundBanner 轮次倒计时 ===
// state: active(进行中) / upcoming(即将开始) / ended(已结束) / idle(未开放)
const roundData = reactive({
  state: 'idle' as 'active' | 'upcoming' | 'ended' | 'idle',
  round: 1, status: '选课未开放', label: '等待开放',
  days: 0, hours: 0, mins: 0, secs: 0,
  progress: 0, progressText: '暂未开放',
  showCount: false, hint: '本轮选课尚未开放，开放后在此显示倒计时'
})
let roundTimer: number | null = null
function parseISO(s: string | null | undefined) {
  if (!s) return null
  const t = new Date(s); return isNaN(t.getTime()) ? null : t
}

/** 依据「时间窗 + 开关」推导横幅状态；无时间窗时回落到手动开关，绝不隐藏 */
function applyRoundBanner(c: any, schedules: any[]) {
  if (roundTimer) { clearInterval(roundTimer); roundTimer = null }
  const roundOpen = !!c?.roundOpen
  const round = Number(c?.currentRound || 1)
  roundData.round = round || 1

  // 配置里没时间窗时，回落到三轮排期表里本轮的时间
  const hit = (schedules || []).find((s: any) => Number(s?.roundNo) === roundData.round)
  const oa = parseISO(c?.roundOpenAt) || (hit ? parseISO(hit.openAt) : null)
  const ca = parseISO(c?.roundCloseAt) || (hit ? parseISO(hit.closeAt) : null)
  const openT = oa ? oa.getTime() : null
  const closeT = ca ? ca.getTime() : null

  const tick = () => {
    const now = Date.now()
    let ms = 0
    if (openT !== null && closeT !== null && closeT > openT) {
      // 完整窗口：开放时间 ~ 截止时间
      if (now < openT) {
        roundData.state = 'upcoming'; roundData.status = '即将开始'; roundData.label = '距本轮开放'
        roundData.progress = 0; roundData.progressText = '尚未开始'
        roundData.hint = '本轮选课尚未开始，开放后在此显示倒计时'
        ms = openT - now
      } else if (now <= closeT) {
        roundData.state = 'active'; roundData.status = '选课进行中'; roundData.label = '距本轮结束'
        roundData.progress = Math.min(100, Math.max(0, Math.round((now - openT) / (closeT - openT) * 100)))
        roundData.progressText = `进度 ${roundData.progress}%`
        ms = closeT - now
      } else {
        roundData.state = 'ended'; roundData.status = '本轮已结束'; roundData.label = '等待下一轮'
        roundData.progress = 100; roundData.progressText = '已完成 100%'
        roundData.hint = '本轮选课已结束，请等待下一轮开放'
      }
    } else if (openT !== null && now < openT) {
      roundData.state = 'upcoming'; roundData.status = '即将开始'; roundData.label = '距本轮开放'
      roundData.progress = 0; roundData.progressText = '尚未开始'
      roundData.hint = '本轮选课尚未开始，开放后在此显示倒计时'
      ms = openT - now
    } else if (closeT !== null && now <= closeT) {
      roundData.state = 'active'; roundData.status = '选课进行中'; roundData.label = '距本轮结束'
      roundData.progressText = '进行中'
      ms = closeT - now
    } else if (roundOpen) {
      // 手动开放但没设截止时间
      roundData.state = 'active'; roundData.status = '选课进行中'; roundData.label = '不限时'
      roundData.progress = 0; roundData.progressText = '进行中'
      roundData.hint = '本轮选课进行中，管理员未设置截止时间'
    } else if (closeT !== null) {
      roundData.state = 'ended'; roundData.status = '本轮已结束'; roundData.label = '等待下一轮'
      roundData.progress = 100; roundData.progressText = '已完成 100%'
      roundData.hint = '本轮选课已结束，请等待下一轮开放'
    } else {
      roundData.state = 'idle'; roundData.status = '选课未开放'; roundData.label = '等待开放'
      roundData.progress = 0; roundData.progressText = '暂未开放'
      roundData.hint = '本轮选课尚未开放，开放后在此显示倒计时'
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
    // 配置里缺时间窗时，去三轮排期表补齐（否则横幅没有倒计时目标）
    if (!c.roundOpenAt || !c.roundCloseAt) {
      try {
        const rs: any = await api.get('/system/round-schedule')
        const d = rs?.success ? (rs.data || {}) : (rs || {})
        schedules = Array.isArray(d?.schedules) ? d.schedules : []
      } catch { schedules = [] }
    }
    applyRoundBanner(c, schedules)
  }).catch(() => {
    // 后端不可达时也保留横幅占位，不留下空洞
    applyRoundBanner({}, [])
  })
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
  ctx.fillStyle = 'rgba(8,12,20,0.72)'
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
  loginOk.value = false
  try {
    const r = await api.post('/auth/login', { no: form.no, password: form.password })
    if (r.success) {
      auth.setToken(r.data.token)
      auth.setUser(r.data.user)
      if (remember.value) localStorage.setItem('zhixue_remember', form.no)
      const role = r.data?.user?.role
      welcomeSub.value = role === 'admin' ? '正在载入管理控制台…'
        : role === 'teacher' ? '正在载入教学工作台…'
        : '正在为你加载课程信息…'
      loginOk.value = true   // 光球转成「勾选成功」形态
      setTimeout(() => {
        globing.value = false
        loginOk.value = false
        welcome.value = true
        setTimeout(() => router.push('/dashboard'), 2000)
      }, 900)
    } else {
      globing.value = false
      loginOk.value = false
      ;(proxy as any).$message?.error(r.message || '登录失败')
      drawCaptcha()
    }
  } catch (e: any) {
    globing.value = false
    loginOk.value = false
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
    radial-gradient(380px 380px at 72% 16%, rgba(34,197,94,0.30), transparent 70%),
    radial-gradient(420px 420px at 12% 84%, rgba(34,211,238,0.20), transparent 70%),
    radial-gradient(340px 340px at 90% 78%, rgba(139,92,246,0.24), transparent 70%);
  filter: blur(10px);
  animation: rightAurora 20s ease-in-out infinite alternate;
}
@keyframes rightAurora {
  from { transform: translate3d(0, 0, 0) scale(1); }
  to   { transform: translate3d(-16px, 18px, 0) scale(1.09); }
}

/* auth-card */
.auth-card {
  position: relative; z-index: 1;
  width: 424px; max-width: 100%;
  background: rgba(255,255,255,0.055) !important;
  backdrop-filter: blur(22px) saturate(140%); -webkit-backdrop-filter: blur(22px) saturate(140%);
  border: 1px solid rgba(255,255,255,0.14) !important;
  border-radius: 24px;
  box-shadow:
    0 24px 60px rgba(0,0,0,0.45),
    0 0 70px rgba(34,197,94,0.08),
    inset 0 1px 0 rgba(255,255,255,0.12) !important;
  padding: 36px 32px 30px;
  animation: zxFadeUp 0.55s cubic-bezier(.2,.8,.2,1);
  max-height: 94vh; overflow-y: auto;
}
@keyframes zxFadeUp {
  from { opacity: 0; transform: translateY(16px); }
  to { opacity: 1; transform: translateY(0); }
}
.auth-card-head { text-align: center; margin-bottom: 24px; }
.auth-card-head h2 { margin: 0 0 8px; font-size: 26px; font-weight: 800; color: #f1f5f9; letter-spacing: .5px; }
.auth-card-head p { margin: 0; font-size: 14px; color: #8b98a8; }

/* tabs */
.auth-tabs {
  display: flex; background: rgba(255,255,255,0.05);
  border: 1px solid rgba(255,255,255,0.08); border-radius: 14px; padding: 4px; margin-bottom: 22px;
}
.auth-tab {
  flex: 1; padding: 10px; text-align: center; font-size: 14px; font-weight: 600;
  color: #8b98a8; border-radius: 10px; transition: all .25s;
}
.auth-tab:hover { color: #cbd5e1; }
.auth-tab.active {
  background: rgba(34,197,94,0.16); color: #4ade80;
  box-shadow: 0 4px 14px rgba(0,0,0,.22), inset 0 0 0 1px rgba(74,222,128,.22);
}

/* form */
.auth-form .form-group { margin-bottom: 14px; }
.form-group label { display: block; font-size: 13px; font-weight: 500; color: #cbd5e1; margin-bottom: 6px; }
.form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.auth-form input, .auth-form select {
  width: 100%; height: 50px; border-radius: 14px;
  background: rgba(8,12,20,0.55); border: 1px solid rgba(255,255,255,0.12);
  color: #e6edf3; padding: 0 14px; font-size: 15px;
  transition: border-color .25s, box-shadow .25s, background .25s;
  -webkit-text-fill-color: #e6edf3;
}
.auth-form input::placeholder { color: #5b6878; }
.auth-form input:focus, .auth-form select:focus {
  outline: none; border-color: #22c55e;
  background: rgba(8,12,20,0.75);
  box-shadow: 0 0 0 4px rgba(34,197,94,0.18), 0 0 24px rgba(34,197,94,0.22);
}

/* 带图标输入框 */
.input-icon { position: relative; }
.input-icon .input-left-icon {
  position: absolute; left: 16px; top: 50%; transform: translateY(-50%);
  width: 17px; height: 17px; color: #64748b; pointer-events: none; transition: color .25s;
}
.input-icon:focus-within .input-left-icon { color: #22c55e; }
.input-icon input { padding-left: 46px !important; }
.pwd-eye {
  position: absolute; right: 12px; top: 50%; transform: translateY(-50%);
  color: #64748b; padding: 4px;
}
/* 眼睛按钮无固定尺寸，svg.icon 的 100% 会解析失败退回 300x150 默认值，必须显式定尺寸 */
.pwd-eye .icon { width: 18px; height: 18px; }

/* captcha */
.captcha-row { display: flex; gap: 10px; align-items: center; }
.captcha-row .captcha-input { flex: 1; margin-bottom: 0; }
.captcha-row .captcha-input input { height: 50px; }
.captcha-canvas {
  width: 112px; height: 44px; flex-shrink: 0; border-radius: 14px;
  border: 1px solid rgba(255,255,255,0.12); background: rgba(8,12,20,0.55); cursor: pointer;
  transition: border-color .25s, box-shadow .25s;
}
.captcha-canvas:hover { border-color: rgba(34,197,94,.5); box-shadow: 0 0 18px rgba(34,197,94,.2); }

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
  border-radius: 14px; height: 52px; font-size: 16px; letter-spacing: 6px; font-weight: 700;
  box-shadow: 0 10px 30px rgba(34,197,94,0.35); color: #fff;
  transition: all .2s; position: relative; overflow: hidden;
}
.btn-primary:hover { background: linear-gradient(135deg, #16a34a, #15803d); transform: translateY(-2px); box-shadow: 0 14px 40px rgba(34,197,94,.5); }
.btn-outline {
  background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.12);
  border-radius: 999px; color: #8b98a8; font-size: 13px; font-weight: 500; padding: 8px 16px;
  transition: all .2s;
}
.btn-outline:hover { color: #4ade80; border-color: rgba(34,211,238,.55); box-shadow: 0 0 16px rgba(34,211,238,.22); }
.btn-sm { padding: 8px 16px; font-size: 13px; }

/* ============ 登录光球：玻璃球 + 旋环 + 涟漪 + 勾选 ============ */
#login-btn .btn-text { transition: opacity .22s; }
#login-btn .orb { display: none; }

#login-btn.btn-globing {
  width: 54px; height: 54px; padding: 0; margin: 0 auto;
  border-radius: 50% !important; display: block;
  background: transparent !important;
  box-shadow: none !important;
  overflow: visible !important;
  transform: none !important;
}
#login-btn.btn-globing .btn-text { opacity: 0; }
/* 收起按钮的流光伪元素，否则圆形状态下会露出一道斜光条 */
#login-btn.btn-globing::before { display: none !important; }
#login-btn.btn-globing .orb {
  display: block; position: absolute; inset: 0; border-radius: 50%;
}

/* 球体：多层渐变 + 高光，做出玻璃球质感 */
.orb-core {
  position: absolute; inset: 0; border-radius: 50%;
  background:
    radial-gradient(circle at 30% 20%, rgba(255,255,255,.95) 0%, rgba(236,254,255,.45) 12%, rgba(134,239,172,.12) 26%, rgba(134,239,172,0) 44%),
    radial-gradient(circle at 74% 84%, rgba(34,211,238,.55) 0%, rgba(34,211,238,0) 48%),
    radial-gradient(circle at 40% 32%, #d1fae5 0%, #6ee7b7 16%, #34d399 36%, #10b981 56%, #059669 78%, #064e3b 100%);
  box-shadow:
    inset 0 2px 6px rgba(255,255,255,.55),
    inset 0 -12px 20px rgba(4,80,60,.45),
    inset 0 0 0 1px rgba(209,250,229,.3),
    0 8px 24px rgba(16,185,129,.45),
    0 0 50px rgba(16,185,129,.35);
  animation: orbBreath 2.6s ease-in-out infinite;
  overflow: hidden;
}
/* 锐利镜面高光 */
.orb-core::before {
  content: ''; position: absolute; left: 20%; top: 14%;
  width: 30%; height: 22%; border-radius: 50%;
  background: radial-gradient(circle at 45% 40%, rgba(255,255,255,.98), rgba(255,255,255,.55) 45%, rgba(255,255,255,0) 72%);
  transform: rotate(-24deg); filter: blur(.4px);
}
/* 右下反弹光，让球体通透 */
.orb-core::after {
  content: ''; position: absolute; right: 8%; bottom: 10%;
  width: 34%; height: 24%; border-radius: 50%;
  background: radial-gradient(circle, rgba(125,211,252,.6), rgba(125,211,252,0) 72%);
  filter: blur(2.5px);
}
@keyframes orbBreath {
  0%, 100% { transform: scale(1); box-shadow: inset 0 2px 6px rgba(255,255,255,.55), inset 0 -12px 20px rgba(4,80,60,.45), inset 0 0 0 1px rgba(209,250,229,.3), 0 8px 24px rgba(16,185,129,.45), 0 0 50px rgba(16,185,129,.35); }
  50%      { transform: scale(1.05); box-shadow: inset 0 2px 9px rgba(255,255,255,.7), inset 0 -12px 20px rgba(4,80,60,.4), inset 0 0 0 1px rgba(209,250,229,.45), 0 10px 32px rgba(16,185,129,.6), 0 0 72px rgba(34,211,238,.42); }
}

/* 加载中：球外一圈渐变弧线旋转 */
.orb-ring {
  position: absolute; inset: -7px; border-radius: 50%;
  background: conic-gradient(from 0deg, rgba(74,222,128,0) 0deg, rgba(74,222,128,.15) 90deg, rgba(34,211,238,.95) 260deg, rgba(74,222,128,0) 360deg);
  -webkit-mask: radial-gradient(farthest-side, transparent calc(100% - 3px), #000 calc(100% - 2px));
  mask: radial-gradient(farthest-side, transparent calc(100% - 3px), #000 calc(100% - 2px));
  animation: orbSpin 1.15s linear infinite;
  transition: opacity .3s, transform .3s;
}
@keyframes orbSpin { to { transform: rotate(360deg); } }

/* 涟漪：两道扩散光圈 */
.orb-wave {
  position: absolute; inset: 0; border-radius: 50%;
  border: 1px solid rgba(52,211,153,.55);
  animation: orbWave 2.8s cubic-bezier(.2,.55,.3,1) infinite;
  pointer-events: none;
}
.orb-wave-2 { animation-delay: 1.4s; }
@keyframes orbWave {
  0%   { transform: scale(1); opacity: .7; }
  65%  { opacity: .18; }
  100% { transform: scale(2.1); opacity: 0; }
}

/* 成功勾选：一笔画出来 */
.orb-check {
  position: absolute; left: 26%; top: 26%; width: 48%; height: 48%;
  opacity: 0; transition: opacity .2s .1s;
}
.orb-check path {
  stroke: #f0fdf4; stroke-width: 2.8; stroke-linecap: round; stroke-linejoin: round;
  stroke-dasharray: 26; stroke-dashoffset: 26;
  filter: drop-shadow(0 1px 4px rgba(6,78,59,.7)) drop-shadow(0 0 6px rgba(255,255,255,.45));
}

/* 成功态：旋环收起、球体定格发亮、勾选描边 */
#login-btn.btn-check-ok .orb-ring { opacity: 0; transform: scale(1.35); }
#login-btn.btn-check-ok .orb-core { animation: orbOk .6s cubic-bezier(.2,.9,.25,1) forwards; }
#login-btn.btn-check-ok .orb-wave { animation-duration: 1.9s; border-color: rgba(167,243,208,.8); }
#login-btn.btn-check-ok .orb-check { opacity: 1; }
#login-btn.btn-check-ok .orb-check path { animation: orbTick .42s .08s cubic-bezier(.5,0,.3,1) forwards; }
@keyframes orbTick { to { stroke-dashoffset: 0; } }
@keyframes orbOk {
  0%   { transform: scale(1); filter: brightness(1); }
  45%  { transform: scale(1.13); filter: brightness(1.25) saturate(1.1); }
  100% { transform: scale(1.02); filter: brightness(1.12) saturate(1.05); }
}

/* ============ 登录成功欢迎层 ============ */
.login-welcome {
  position: fixed; inset: 0; z-index: 9999;
  display: grid; place-items: center; padding: 24px;
  background:
    radial-gradient(620px 620px at 50% 44%, rgba(34,197,94,.18), transparent 70%),
    radial-gradient(520px 420px at 84% 84%, rgba(139,92,246,.14), transparent 70%),
    rgba(7,11,16,.74);
  backdrop-filter: blur(12px) saturate(120%);
  -webkit-backdrop-filter: blur(12px) saturate(120%);
}
.lw-inner { text-align: center; transform: translateY(-2vh); }
.lw-title {
  margin: 0; font-size: clamp(26px, 4vw, 46px); font-weight: 800; letter-spacing: 2px;
  background: linear-gradient(90deg, #4ade80, #22d3ee, #a78bfa);
  -webkit-background-clip: text; background-clip: text; color: transparent;
  animation: lwTitleIn .9s .05s cubic-bezier(.2,.8,.2,1) both;
}
.lw-sub {
  margin: 16px 0 0; font-size: 15px; letter-spacing: 1px; color: #8b98a8;
  animation: lwSubIn .9s .4s cubic-bezier(.2,.8,.2,1) both;
}
.lw-dots {
  display: flex; gap: 8px; justify-content: center; margin-top: 26px;
  animation: lwSubIn .6s .7s both;
}
.lw-dots i {
  width: 8px; height: 8px; border-radius: 50%;
  background: linear-gradient(135deg, #4ade80, #22d3ee);
  box-shadow: 0 0 12px rgba(34,197,94,.75);
  animation: lwDot 1.1s ease-in-out infinite;
}
.lw-dots i:nth-child(2) { animation-delay: .16s; }
.lw-dots i:nth-child(3) { animation-delay: .32s; }
@keyframes lwTitleIn {
  from { opacity: 0; transform: translateY(18px) scale(.94); filter: blur(8px); }
  to   { opacity: 1; transform: none; filter: blur(0); }
}
@keyframes lwSubIn { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: none; } }
@keyframes lwDot { 0%, 100% { opacity: .35; transform: scale(.72); } 50% { opacity: 1; transform: scale(1); } }

.lw-fade-enter-active { transition: opacity .45s ease; }
.lw-fade-leave-active { transition: opacity .3s ease; }
.lw-fade-enter-from, .lw-fade-leave-to { opacity: 0; }

/* quick login + tip */
.quick-login {
  display: flex; align-items: center; gap: 8px; margin-top: 18px; flex-wrap: wrap; justify-content: center;
}
.quick-login-label { font-size: 12.5px; color: #5b6878; }
.auth-tip { margin-top: 16px; text-align: center; font-size: 12.5px; color: #5b6878; }
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

/* 无倒计时目标时的占位说明（已结束 / 未开放），保证横幅高度不塌陷 */
.lrb-hint {
  font-size: 12px; line-height: 1.5; color: #94a3b8; text-align: center;
  padding: 11px 12px; border-radius: 10px;
  background: rgba(15,23,42,0.5); border: 1px dashed rgba(148,163,184,0.22);
}
.login-round-banner.lrb-ended .lrb-progress-fill,
.login-round-banner.lrb-idle .lrb-progress-fill {
  background: linear-gradient(90deg, rgba(100,116,139,0.65), rgba(148,163,184,0.55));
}
.login-round-banner.lrb-ended .lrb-badge,
.login-round-banner.lrb-idle .lrb-badge {
  background: rgba(100,116,139,0.18); color: #cbd5e1; border-color: rgba(148,163,184,0.3);
}
.login-round-banner.lrb-ended .lrb-glow,
.login-round-banner.lrb-idle .lrb-glow { opacity: 0.45; }
.login-round-banner.lrb-upcoming .lrb-badge {
  background: rgba(34,211,238,0.16); color: #67e8f9; border-color: rgba(34,211,238,0.35);
}
.login-round-banner.lrb-upcoming .lrb-pulse { background: #22d3ee; }

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
  content: ''; position: absolute; top: -1px; left: 16%; right: 16%; height: 2px;
  border-radius: 2px;
  background: linear-gradient(90deg, transparent, #4ade80, #22d3ee, #a78bfa, transparent);
  box-shadow: 0 0 16px rgba(34,197,94,.6);
  animation: cardShimmer 4s linear infinite;
}
@keyframes cardShimmer { 0%, 100% { opacity: .55; } 50% { opacity: 1; } }

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
  .auth-page { flex-direction: column; height: auto; min-height: 100vh; overflow: visible; }
  .auth-left, .auth-right { width: 100%; min-height: auto; flex: 0 0 auto; }
  .auth-left { padding: 92px 24px 46px; justify-content: flex-start; }
  .auth-gauge { width: 180px; height: 180px; }
  .auth-left-foot { position: static; margin-top: 26px; }
  .auth-features { margin-top: 22px; }
  .auth-right { padding: 24px 20px 44px; }
  .auth-card { width: 100%; max-width: 420px; padding: 26px 22px; max-height: none; }
}
</style>
