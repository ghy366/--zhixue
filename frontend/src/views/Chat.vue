<template>
  <div class="chat-page stats-page liquid">
    <div class="chat-header">
      <div class="chat-title">
        <div class="chat-title-icon">✨</div>
        <div class="chat-title-text">AI 助手 · {{ roleLabel }}</div>
        <span class="chat-status">在线</span>
      </div>
      <button class="chat-clear-btn" @click="clear" :disabled="!messages.length">清空对话</button>
    </div>

    <div class="chat-messages" ref="messagesRef">
      <div v-if="!messages.length" class="chat-welcome">
        <div class="chat-welcome-icon">🎓</div>
        <div class="chat-welcome-title">智学选课 AI 助手</div>
        <div class="chat-welcome-sub">基于真实选课数据，帮你制定计划、解读规则</div>
        <div class="quick-chips">
          <button v-for="q in quickQuestions" :key="q" class="chip" @click="send(q)">{{ q }}</button>
        </div>
      </div>

      <template v-for="(m, i) in messages" :key="i">
        <div v-if="m.role === 'day'" class="chat-day-sep">{{ m.content }}</div>
        <div v-else class="chat-msg" :class="m.role">
          <div class="chat-avatar">{{ m.role === 'user' ? avatarText : '智' }}</div>
          <div class="chat-bubble-wrap">
            <div class="chat-sender">{{ m.role === 'user' ? '我' : 'AI 助手' }}</div>
            <div class="chat-bubble" :class="m.role">
              <pre v-if="m.loading" class="chat-typing"><span/><span/><span/></pre>
              <div v-else class="chat-md" v-html="renderMd(m.content)" />
            </div>
            <div class="chat-time">{{ m.time }}</div>
          </div>
        </div>
      </template>

      <div v-if="error" class="chat-error">
        ⚠️ {{ error }}
        <button class="chat-retry" @click="retry">重试</button>
      </div>
    </div>

    <div class="chat-input-area">
      <div class="chat-input-wrap">
        <textarea ref="taRef" v-model="input" :disabled="loading"
                  placeholder="输入你的问题... Enter 发送"
                  @keydown.enter.exact.prevent="send()"
                  :rows="1" @input="autoResize" />
      </div>
      <button class="chat-send" :disabled="loading || !input.trim()" @click="send">
        <svg class="send-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M22 2L11 13"/><path d="M22 2L15 22L11 13L2 9L22 2Z"/>
        </svg>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted } from 'vue'
import http from '@/api'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const roleLabel = computed(() =>
  ({ student: '学生模式', teacher: '教师模式', admin: '管理员模式' } as any)[auth.user?.role] || '通用'
)
const avatarText = computed(() => auth.user?.name?.charAt(0) || 'U')

const quickQuestions = computed(() => {
  if (auth.user?.role === 'student') return ['帮我制定本学期选课计划', '这门课需要哪些先修？', '如何准备毕业设计开题？', '当前选课状态如何？']
  if (auth.user?.role === 'teacher') return ['如何写一份好的课程大纲？', '批改作业有什么技巧？', '如何指导学生做毕设？']
  return ['系统维护最佳实践', '如何设计合理的选课轮次？', '数据报表优化建议']
})

interface Msg { role: 'user' | 'assistant' | 'day'; content: string; time?: string; loading?: boolean; retryable?: boolean }

const messages = ref<Msg[]>([])
const history = ref<{ role: string; content: string }[]>([])
const input = ref('')
const loading = ref(false)
const error = ref('')
const messagesRef = ref<HTMLElement | null>(null)
const taRef = ref<HTMLTextAreaElement | null>(null)

function fmtTime() {
  return new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false })
}

function renderMd(text: string) {
  if (!text) return ''
  let s = text
  s = s.replace(/```([\s\S]*?)```/g, (_, code) => `<pre class="md-code">${code.trim()}</pre>`)
  s = s.replace(/`([^`]+)`/g, (_, c) => `<code class="md-inline">${c}</code>`)
  s = s.replace(/\*\*([^*]+)\*\*/g, '<b>$1</b>')
  s = s.replace(/\*([^*]+)\*/g, '<i>$1</i>')
  s = s.replace(/(?:^|\n)(\d+)\.\s+(.+)/g, '\n<li>$2</li>')
  s = s.replace(/(?:^|\n)[-\u2022*]\s+(.+)/g, '\n<li>$1</li>')
  if (/<li>/.test(s)) s = s.replace(/((?:<li>.*<\/li>\n?)+)/g, '<ul class="md-list">$1</ul>')
  s = s.replace(/\n/g, '<br>')
  return s
}

function autoResize() {
  if (!taRef.value) return
  taRef.value.style.height = 'auto'
  taRef.value.style.height = Math.min(taRef.value.scrollHeight, 140) + 'px'
}

async function send(text?: any) {
  const raw = typeof text === 'string' ? text : ''
  const content = (raw || input.value || '').trim()
  if (!content || loading.value) return
  input.value = ''; error.value = ''; autoResize()
  messages.value.push({ role: 'user', content, time: fmtTime() })
  history.value.push({ role: 'user', content })
  if (history.value.length > 16) history.value = history.value.slice(-16)

  const tail: Msg = { role: 'assistant', content: '', loading: true }
  messages.value.push(tail)
  await nextTick(); scroll()

  loading.value = true
  try {
    const r: any = await http.post('/ai/chat', {
      message: content,
      history: history.value.slice(0, -1),
      role: auth.user?.role
    })
    tail.content = r?.data?.reply || r?.reply || '（AI 返回为空）'
  } catch (e: any) {
    tail.content = '⚠️ AI 服务暂不可用：' + (e?.message || '未知错误')
    error.value = tail.content
    tail.retryable = true
    if (history.value.length) history.value.pop()
  } finally {
    tail.loading = false; loading.value = false; scroll()
  }
  if (!tail.retryable && tail.content && !tail.content.startsWith('⚠️')) {
    history.value.push({ role: 'assistant', content: tail.content })
  }
}

function retry() {
  error.value = ''
  const idx = [...messages.value].reverse().findIndex(m => m.retryable)
  if (idx >= 0) {
    const realIdx = messages.value.length - 1 - idx
    const prev = messages.value[realIdx - 1]
    messages.value.splice(realIdx, 1)
    if (prev?.role === 'user') send(prev.content)
  }
}

function clear() { messages.value = []; history.value = []; error.value = '' }
function scroll() { if (messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight }

onMounted(() => { auth.loadFromStorage(); setTimeout(() => taRef.value?.focus(), 100) })
</script>

<style scoped>
.chat-page { height: calc(100vh - 110px); display: flex; flex-direction: column; background: var(--card); border: 1px solid var(--border); border-radius: 14px; overflow: hidden; }
.chat-header { display: flex; align-items: center; justify-content: space-between; padding: 12px 18px; border-bottom: 1px solid var(--border); background: linear-gradient(135deg, rgba(99,102,241,0.06), rgba(168,85,247,0.06)); }
.chat-title { display: flex; align-items: center; gap: 10px; font-size: 15px; font-weight: 600; }
.chat-title-icon { font-size: 20px; }
.chat-title-text { color: var(--text); }
.chat-status { font-size: 10px; padding: 2px 8px; border-radius: 10px; background: rgba(16,185,129,0.12); color: #10b981; font-weight: 500; display: flex; align-items: center; gap: 4px; }
.chat-status::before { content: ''; width: 5px; height: 5px; border-radius: 50%; background: #10b981; animation: pulse 2s infinite; }
@keyframes pulse { 0%,100%{opacity:1} 50%{opacity:.4} }
.chat-clear-btn { font-size: 12px; padding: 5px 12px; border-radius: 20px; background: var(--bg); border: 1px solid var(--border); color: var(--text-light); cursor: pointer; transition: all .2s; }
.chat-clear-btn:hover:not(:disabled) { border-color: var(--primary); color: var(--primary); }
.chat-clear-btn:disabled { opacity: .4; cursor: not-allowed; }
.chat-messages { flex: 1; overflow-y: auto; padding: 20px 24px; display: flex; flex-direction: column; gap: 16px; background: var(--card); }
.chat-welcome { text-align: center; padding: 30px 10px; color: var(--text-light); }
.chat-welcome-icon { font-size: 48px; margin-bottom: 10px; }
.chat-welcome-title { font-size: 16px; font-weight: 600; color: var(--text); }
.chat-welcome-sub { margin-top: 6px; font-size: 13px; }
.quick-chips { display: flex; gap: 8px; justify-content: center; flex-wrap: wrap; margin-top: 16px; }
.chip { padding: 7px 16px; border: 1px solid var(--border); border-radius: 999px; background: transparent; color: var(--text-light); cursor: pointer; font-size: 13px; transition: all .2s; }
.chip:hover { border-color: var(--primary); color: var(--primary); background: var(--primary-bg); }
.chat-msg { display: flex; gap: 12px; align-items: flex-start; animation: msgIn .25s ease; }
.chat-msg.user { flex-direction: row-reverse; }
@keyframes msgIn { from { opacity: 0; transform: translateY(6px) } to { opacity: 1; transform: none } }
.chat-avatar { width: 36px; height: 36px; border-radius: 10px; flex-shrink: 0; background: linear-gradient(135deg, #16a34a, #22c55e); display: flex; align-items: center; justify-content: center; color: #fff; font-weight: 600; font-size: 14px; }
.chat-msg.user .chat-avatar { background: linear-gradient(135deg, #6366f1, #8b5cf6); }
.chat-bubble-wrap { max-width: 72%; display: flex; flex-direction: column; gap: 4px; }
.chat-msg.user .chat-bubble-wrap { align-items: flex-end; }
.chat-sender { font-size: 11px; color: var(--text-lighter); padding: 0 4px; }
.chat-bubble { padding: 12px 16px; border-radius: 14px; font-size: 14px; line-height: 1.65; background: var(--bg); border: 1px solid var(--border); color: var(--text); word-break: break-word; white-space: pre-wrap; border-top-left-radius: 4px; }
.chat-bubble.user { background: linear-gradient(135deg, #6366f1, #8b5cf6); border-color: transparent; color: #fff; border-top-left-radius: 14px; border-top-right-radius: 4px; }
.chat-time { font-size: 10px; color: var(--text-lighter); padding: 0 4px; }
.chat-md :deep(b) { font-weight: 700; }
.chat-md :deep(i) { font-style: italic; }
.chat-md :deep(.md-code) { background: rgba(0,0,0,0.05); border-radius: 8px; padding: 10px 14px; margin: 8px 0; overflow-x: auto; font-family: 'Menlo','Consolas',monospace; font-size: 13px; }
.chat-md :deep(.md-inline) { background: rgba(0,0,0,0.06); padding: 1px 6px; border-radius: 4px; font-family: 'Menlo','Consolas',monospace; font-size: 13px; }
.chat-md :deep(.md-list) { padding-left: 18px; margin: 6px 0; }
.chat-md :deep(.md-list li) { margin: 2px 0; }
html[data-theme="dark"] .chat-md :deep(.md-code) { background: rgba(255,255,255,0.08); }
html[data-theme="dark"] .chat-md :deep(.md-inline) { background: rgba(255,255,255,0.1); }
.chat-typing { display: flex; gap: 4px; padding: 2px 0; margin: 0; }
.chat-typing span { width: 7px; height: 7px; border-radius: 50%; background: var(--text-light); animation: dotBounce 1.2s infinite; }
.chat-typing span:nth-child(2) { animation-delay: 0.15s }
.chat-typing span:nth-child(3) { animation-delay: 0.3s }
@keyframes dotBounce { 0%,60%,100%{transform:translateY(0);opacity:.4} 30%{transform:translateY(-5px);opacity:1} }
.chat-day-sep { text-align: center; margin: 18px 0 14px; font-size: 11px; color: var(--text-lighter); display: flex; align-items: center; gap: 12px; }
.chat-day-sep::before, .chat-day-sep::after { content: ''; flex: 1; height: 1px; background: linear-gradient(90deg, transparent, var(--border), transparent); }
.chat-error { margin: 8px auto; padding: 10px 16px; max-width: 480px; background: rgba(239,68,68,0.08); border: 1px solid rgba(239,68,68,0.2); border-radius: 10px; color: var(--danger); font-size: 13px; text-align: center; display: flex; gap: 10px; align-items: center; justify-content: center; }
.chat-retry { padding: 4px 12px; border-radius: 6px; border: 1px solid var(--danger); background: transparent; color: var(--danger); cursor: pointer; font-size: 12px; }
.chat-retry:hover { background: var(--danger); color: #fff; }
.chat-input-area { display: flex; gap: 10px; padding: 14px 18px; border-top: 1px solid var(--border); align-items: flex-end; background: var(--card); }
.chat-input-wrap { flex: 1; border: 1.5px solid var(--border); border-radius: 12px; padding: 6px 12px; background: var(--bg); transition: border-color .2s, box-shadow .2s; }
.chat-input-wrap:focus-within { border-color: var(--primary); box-shadow: 0 0 0 3px rgba(99,102,241,0.12); }
.chat-input-wrap textarea { width: 100%; border: none; outline: none; resize: none; font-size: 14px; background: transparent; color: var(--text); font-family: inherit; line-height: 1.55; max-height: 140px; }
.chat-send { width: 44px; height: 44px; border-radius: 12px; border: none; cursor: pointer; background: linear-gradient(135deg, #6366f1, #8b5cf6); color: #fff; font-weight: 600; display: flex; align-items: center; justify-content: center; transition: transform .15s, box-shadow .2s, opacity .2s; flex-shrink: 0; }
.chat-send:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 4px 14px rgba(99,102,241,.35); }
.chat-send:disabled { opacity: .5; cursor: not-allowed; }
.send-icon { width: 20px; height: 20px; }
.chat-messages::-webkit-scrollbar { width: 6px; }
.chat-messages::-webkit-scrollbar-thumb { background: var(--border); border-radius: 3px; }
</style>
