<template>
  <div>
    <!-- 学生：未确认导师 -->
    <div v-if="role === 'student' && !studentWs.length" class="empty-card">
      暂未确认导师，先去「毕业设计导师」页面选择吧 👆
    </div>

    <!-- 教师：左侧学生列表 -->
    <div v-if="role === 'teacher' && studentWs.length" class="ws-teacher-layout">
      <div class="ws-teacher-list">
        <div class="section-title">我的学生（{{ studentWs.length }}）</div>
        <div
          v-for="s in studentWs" :key="s.advisorId"
          class="ws-teacher-stu-card" :class="{ active: selectedAid === s.advisorId }"
          @click="selectAdvisor(s.advisorId)"
        >
          <div class="ws-stu-avatar">{{ (s.student?.name || '?').charAt(0) }}</div>
          <div class="ws-stu-meta">
            <div class="ws-stu-name">{{ s.student?.name }}</div>
            <div class="ws-stu-sub">{{ s.student?.college || '' }} · {{ s.student?.major || '' }}</div>
            <div v-if="s.topic" class="ws-stu-topic">🎯 {{ s.topic }}</div>
          </div>
        </div>
      </div>
      <div class="ws-detail-col">
        <Detail v-if="ws" :ws="ws" :role="role" :advisorId="selectedAid" :me="me" @reload="selectAdvisor(selectedAid)" @chat="openChat" />
        <div v-else class="empty-state"><div class="empty-icon"><Icon name="folder" /></div><p>请选择左侧学生</p></div>
      </div>
    </div>

    <!-- 学生：单工作台 -->
    <div v-if="role === 'student' && studentWs.length" class="ws-detail-col">
      <Detail :ws="studentWs[0]" :role="role" :advisorId="studentWs[0].advisorId" :me="me" @reload="loadStudent" @chat="openChat" />
    </div>

    <!-- 私聊弹窗 -->
    <div v-if="chatOpen" class="ws-chat-modal-mask" @click.self="chatOpen = false">
      <div class="ws-chat-modal">
        <div class="ws-chat-modal-header">
          <div class="ws-chat-header-left">
            <div class="ws-chat-header-avatar">{{ chatOther.charAt(0) }}</div>
            <div class="ws-chat-header-title">
              <div class="ws-chat-header-name">{{ chatOther }}</div>
              <div class="ws-chat-header-status">{{ role === 'student' ? '导师' : '学生' }} · 在线</div>
            </div>
          </div>
          <button class="ws-chat-modal-close" @click="chatOpen = false">✕</button>
        </div>
        <div class="ws-chat-modal-body">
          <div v-if="!chatMsgs.length" class="ws-chat-empty"><div class="ws-chat-empty-icon">💬</div>还没开始对话<br>发条消息打个招呼吧 ~</div>
          <div v-for="m in chatMsgs" :key="m.id || m.sentAt" class="ws-chat-msg" :class="{ mine: String(m.senderId) === String(me.id) }">
            <div class="ws-chat-bubble">{{ m.content }}</div>
            <div class="ws-chat-time">{{ fmtTime(m.sentAt) }}</div>
          </div>
        </div>
        <div class="ws-chat-modal-input">
          <textarea v-model="chatText" rows="1" placeholder="输入消息，Enter 发送，Shift+Enter 换行" @keydown="onChatKey"></textarea>
          <button class="ws-chat-send-btn" @click="sendChat">发送</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import Icon from '@/components/Icon.vue'
import Detail from '@/components/workspace/WsDetail.vue'
import { api, getUser, getToken } from '@/api/client'

const me = getUser()
const role = me?.role
const studentWs = ref([])
const selectedAid = ref(null)
const ws = ref(null)
const chatOpen = ref(false)
const chatOther = ref('对方')
const chatMsgs = ref([])
const chatText = ref('')

async function loadStudent() {
  const r = await api('GET', '/workspace/my')
  if (r.success) studentWs.value = r.data || []
}
async function loadTeacher() {
  const r = await api('GET', '/workspace/teacher/students')
  if (r.success) {
    studentWs.value = r.data || []
    if (studentWs.value.length && !selectedAid.value) selectedAid.value = studentWs.value[0].advisorId
    if (selectedAid.value) await selectAdvisor(selectedAid.value)
  }
}
async function selectAdvisor(aid) {
  selectedAid.value = aid
  const r = await api('GET', '/workspace/advisor/' + aid)
  if (r.success) ws.value = r.data
}
function openChat(aid, name) {
  chatOther.value = name || '对方'
  chatOpen.value = true
  loadChat(aid)
}
async function loadChat(aid) {
  const r = await api('GET', '/workspace/chat?advisorId=' + aid)
  chatMsgs.value = r.success ? (r.data || []) : []
}
async function sendChat() {
  const text = chatText.value.trim()
  if (!text) return
  const r = await api('POST', '/workspace/chat', { advisorId: selectedAid.value || ws.value?.advisorId, content: text })
  if (r.success) { chatText.value = ''; await loadChat(selectedAid.value || ws.value?.advisorId) }
}
function onChatKey(e) { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendChat() } }
function fmtTime(t) { return new Date(t).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false }) }

onMounted(() => { if (role === 'student') loadStudent(); else if (role === 'teacher') loadTeacher() })
</script>
