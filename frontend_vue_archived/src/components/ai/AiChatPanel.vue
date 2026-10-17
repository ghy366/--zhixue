<template>
  <div class="ai-chat-panel" v-show="ui.aiOpen">
    <div class="aicp-head">
      <div class="aicp-title">
        <div class="aicp-avatar-wrap">
          <span class="aicp-logo"><Icon name="sparkles" /></span>
          <span class="aicp-online" title="在线"></span>
        </div>
        <div class="aicp-titles">
          <b>AI 学习助手</b>
          <i>在线 · 通常几秒内回复</i>
        </div>
      </div>
      <button class="topbar-icon-btn" @click="ui.toggleAi()" title="关闭"><Icon name="x" /></button>
    </div>

    <div class="aicp-msgs" ref="msgsRef">
      <div class="aicp-welcome" v-if="!messages.length">
        <div class="aicp-welcome-orb"><Icon name="sparkles" /></div>
        <h4>嗨，我是你的 AI 学习助手 🎓</h4>
        <p>选课流程、课程难点、复习计划……都可以问我。<br />也可以试试下面的问题 👇</p>
      </div>

      <div v-for="(m, i) in messages" :key="i" class="ai-msg" :class="m.role">
        <div class="ai-avatar" :class="{ user: m.role === 'user' }">{{ m.role === 'user' ? '我' : '智' }}</div>
        <div class="ai-bubble-wrap">
          <div class="ai-bubble" v-if="m.role === 'user'">{{ m.text }}</div>
          <div class="ai-bubble" v-else>
            <div class="ai-md" v-html="renderMd(m.text)"></div>
            <button class="ai-copy" @click="copy(m.text, i)" :data-copied="copiedIdx === i">复制</button>
          </div>
          <div class="ai-time">{{ m.time }}</div>
        </div>
      </div>

      <div v-if="sending" class="ai-msg assistant">
        <div class="ai-avatar">智</div>
        <div class="ai-bubble-wrap"><div class="ai-bubble"><span class="ai-typing"><i></i><i></i><i></i></span></div></div>
      </div>
    </div>

    <div class="aicp-suggest" v-if="!messages.length">
      <button class="ai-suggest-btn" v-for="q in questions" :key="q" @click="send(q)">{{ q }}</button>
    </div>

    <div class="aicp-input">
      <textarea ref="inputRef" v-model="draft" rows="1" placeholder="问我任何选课、课程、学习的问题…（Enter 发送）"
        @keydown.enter.exact.prevent="send()"></textarea>
      <button class="aicp-send-btn" :disabled="sending || !draft.trim()" @click="send()" title="发送">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z" /></svg>
      </button>
    </div>
  </div>
</template>

<script setup>
import { nextTick, ref } from 'vue'
import Icon from '@/components/Icon.vue'
import { useUiStore } from '@/store/ui'
import { api } from '@/api/client'
import { aiRenderMarkdown } from '@/utils/markdown'
import { toast } from '@/utils/dom'

const ui = useUiStore()
const draft = ref('')
const sending = ref(false)
const messages = ref([])
const history = ref([])
const copiedIdx = ref(-1)
const msgsRef = ref(null)
const inputRef = ref(null)

const questions = [
  '📋 这学期有哪些课程？',
  '🕐 选课积分怎么用？',
  '🔁 怎么退课和改选？',
  '🎓 毕业学分要求是多少？',
  '📝 帮我制定复习计划',
]

const renderMd = (t) => aiRenderMarkdown(t)

function now() {
  const d = new Date(); const p = (n) => (n < 10 ? '0' + n : '' + n)
  return `${p(d.getHours())}:${p(d.getMinutes())}`
}
async function scrollDown() { await nextTick(); if (msgsRef.value) msgsRef.value.scrollTop = msgsRef.value.scrollHeight }
async function copy(text, i) {
  try {
    await navigator.clipboard.writeText(text)
    copiedIdx.value = i; toast('已复制', 'success')
    setTimeout(() => { if (copiedIdx.value === i) copiedIdx.value = -1 }, 1500)
  } catch (e) {
    const ta = document.createElement('textarea'); ta.value = text; document.body.appendChild(ta); ta.select()
    try { document.execCommand('copy'); copiedIdx.value = i; toast('已复制', 'success') } catch { toast('复制失败', 'error') }
    ta.remove(); setTimeout(() => { if (copiedIdx.value === i) copiedIdx.value = -1 }, 1500)
  }
}

async function send(preset) {
  const text = (preset != null ? preset : draft.value).trim()
  if (!text || sending.value) return
  messages.value.push({ role: 'user', text, time: now() })
  history.value.push({ role: 'user', content: text })
  draft.value = ''; sending.value = true
  await scrollDown()
  const r = await api('POST', '/ai/chat', { message: text, history: history.value.slice(0, -1) })
  sending.value = false
  if (r.success && r.data && r.data.reply) {
    const reply = r.data.reply
    messages.value.push({ role: 'assistant', text: reply, time: now() })
    history.value.push({ role: 'assistant', content: reply })
    if (history.value.length > 16) history.value = history.value.slice(-16)
  } else {
    messages.value.push({ role: 'assistant', text: '⚠️ ' + (r.message || '请求失败，请稍后重试'), time: now() })
  }
  await scrollDown()
}
</script>
