<template>
  <div>
    <!-- ===== 原版 ws-header-card ===== -->
    <div class="ws-header-card">
      <div class="ws-avatar">{{ partner.name?.charAt(0) }}</div>
      <div class="ws-info">
        <div class="ws-name">{{ role === 'student' ? '指导教师：' : '学生：' }}{{ partner.name }}</div>
        <div class="ws-sub">{{ partner.college || '' }} · {{ partner.major || '' }}<template v-if="partner.no"> · {{ role === 'student' ? '工号' : '学号' }} {{ partner.no }}</template></div>
        <div v-if="ws.topic" class="ws-sub" style="color:var(--primary);margin-top:4px">🎯 论文方向：{{ ws.topic }}</div>
      </div>
      <div style="text-align:right;display:flex;flex-direction:column;align-items:flex-end;gap:8px">
        <!-- 原版 quickChat：打开真·私聊 Modal（不是 AI 助手！） -->
        <button class="ws-chat-float-btn" @click="openQuickChat">💬 私聊{{ role === 'student' ? '老师' : '学生' }}</button>
        <div style="font-size:12px;color:var(--text-light)">已确认<br>{{ formatDate(ws.approvedAt) }}</div>
      </div>
    </div>

    <!-- ===== 原版 ws-tabs（加 Tab 4: 聊天）===== -->
    <div class="ws-tabs">
      <div class="ws-tab" :class="{ active: activeTab === 'req' }" @click="activeTab = 'req'">📋 {{ role === 'teacher' ? '发布要求' : '查看要求' }}</div>
      <div class="ws-tab" :class="{ active: activeTab === 'paper' }" @click="activeTab = 'paper'">
        📄 论文{{ role === 'teacher' ? '管理' : '上传' }}
        <span v-if="ws.papers?.length" class="ws-badge approved">{{ ws.papers.length }}</span>
      </div>
      <div class="ws-tab" :class="{ active: activeTab === 'anno' }" @click="activeTab = 'anno'">📢 公告</div>
      <div class="ws-tab" :class="{ active: activeTab === 'chat' }" @click="switchChatTab">💬 聊天</div>
    </div>

    <!-- ===== Tab 1: 要求 & 推荐题目 ===== -->
    <div v-if="activeTab === 'req'">
      <template v-if="role === 'student'">
        <div v-if="!ws.latestRequirement" class="empty-card">教师还未发布毕业设计要求，耐心等待~</div>
        <template v-else>
          <div class="ws-requirement-card">
            <h4>📋 毕业设计要求
              <span v-if="ws.latestRequirement.deadline" class="ws-badge pending">截止 {{ formatDate(ws.latestRequirement.deadline) }}</span>
            </h4>
            <div style="white-space:pre-wrap;font-size:14px;line-height:1.7;color:var(--text)">{{ ws.latestRequirement.content || '（未填写）' }}</div>
            <div style="margin-top:14px;font-size:11px;color:var(--text-light)">发布时间：{{ formatDateTime(ws.latestRequirement.createdAt) }}</div>
          </div>
          <div v-if="ws.latestRequirement.topics?.length" class="ws-requirement-card">
            <h4>💡 推荐题目（{{ ws.latestRequirement.topics.length }}）</h4>
            <div v-for="(t, i) in ws.latestRequirement.topics" :key="i" class="ws-topic-item">
              <span style="color:var(--text-light);font-size:12px;margin-right:8px">#{{ i + 1 }}</span>
              <span class="ws-topic-title">{{ t.title }}</span>
              <div v-if="t.desc" class="ws-topic-desc">{{ t.desc }}</div>
            </div>
          </div>
        </template>
      </template>
      <template v-else>
        <div class="ws-requirement-card">
          <h4>✏️ 发布毕业设计要求
            <span v-if="ws.latestRequirement" style="font-size:11px;color:var(--text-light);font-weight:400;margin-left:8px">上次发布：{{ formatDateTime(ws.latestRequirement.createdAt) }}</span>
          </h4>
          <div class="form-group"><label>要求正文</label>
            <textarea v-model="reqContent" class="form-input" rows="6" placeholder="例如：选题范围、字数要求、格式规范、关键时间节点..."></textarea></div>
          <div class="form-group"><label>截止日期（可选）</label>
            <input v-model="reqDeadline" class="form-input" type="datetime-local"></div>
          <div style="margin-top:10px"><strong style="font-size:13px">推荐题目（可选，每行一条，格式：标题|描述）</strong></div>
          <textarea v-model="reqTopics" class="form-input" rows="4" placeholder="题目一|可选描述&#10;题目二|可选描述"></textarea>
          <div style="margin-top:14px;display:flex;gap:10px">
            <button class="btn btn-primary" :disabled="submitting" @click="submitReq">
              {{ submitting ? '提交中...' : (ws.latestRequirement ? '更新要求' : '发布要求') }}
            </button>
          </div>
        </div>
      </template>
    </div>

    <!-- ===== Tab 2: 论文 ===== -->
    <div v-if="activeTab === 'paper'">
      <template v-if="role === 'student'">
        <div class="ws-requirement-card">
          <h4>📤 上传新版本论文</h4>
          <div class="ws-dropzone" @click="triggerFile" @dragover.prevent @drop.prevent="onDrop">
            <div style="font-size:32px">📄</div>
            <div style="font-weight:500;margin-top:6px">点击或拖拽文件到此处上传</div>
            <div class="ws-hint">支持 PDF / DOC / DOCX / TXT / ZIP / RAR，最大 100MB · 每次上传自动成为新版本</div>
            <input ref="fileInput" type="file" class="ws-file-input" accept=".pdf,.doc,.docx,.txt,.zip,.rar" @change="onFileChange">
          </div>
          <div class="form-group" style="margin-top:12px"><label>论文标题（可选）</label>
            <input v-model="paperTitle" class="form-input" placeholder="留空则使用文件名"></div>
          <div class="form-group"><label>备注（可选）</label>
            <textarea v-model="paperNote" class="form-input" rows="2" placeholder="给老师看的说明，比如：这版主要改了XX..."></textarea></div>
        </div>
      </template>
      <template v-else>
        <div class="ws-requirement-card">
          <h4>📎 教师上传批注/参考材料（可选）</h4>
          <input type="file" accept=".pdf,.doc,.docx,.txt,.zip,.rar" style="margin-top:8px" @change="onTeacherFile">
        </div>
      </template>
      <div v-if="papers.length">
        <div v-for="p in papers" :key="p.id" class="ws-paper-row" :class="{ 'teacher-upload': p.uploadedBy === 'teacher' }">
          <div class="ws-paper-ver">v{{ p.versionNo }}</div>
          <div class="ws-paper-info">
            <div class="ws-paper-title">{{ p.fileName }} <span v-if="p.uploadedBy === 'teacher'" style="font-size:11px;color:#d97706">[教师上传]</span></div>
            <div class="ws-paper-meta">
              <template v-if="p.title">📝 {{ p.title }}  ·  </template>
              {{ (p.fileSizeBytes / 1024).toFixed(1) }} KB · 上传于 {{ formatDateTime(p.createdAt) }}{{ p.uploadedBy === 'teacher' ? '' : ' · 来自学生' }}
            </div>
            <div v-if="p.note" class="ws-paper-note">"{{ p.note }}"</div>
            <div v-if="p.teacherComment" style="font-size:12px;color:#7c3aed;margin-top:4px">👤 教师批注：{{ p.teacherComment }}</div>
            <div v-if="p.grade" style="font-size:12px;color:#16a34a;margin-top:2px">⭐ 评分：{{ p.grade }}/100</div>
            <template v-if="role === 'teacher' && p.uploadedBy !== 'teacher'">
              <button class="btn btn-outline btn-sm" style="margin-top:8px" @click="toggleReview(p)">批阅</button>
              <div v-if="showReviewId === p.id" style="margin-top:10px;padding:12px;background:var(--bg);border-radius:8px;border-left:3px solid var(--primary)">
                <div class="form-group"><label>教师评语</label>
                  <textarea v-model="reviewComment" class="form-input" rows="2" placeholder="批注、修改建议..."></textarea></div>
                <div style="display:flex;gap:8px;align-items:center">
                  <select v-model="reviewStatus" class="form-input" style="width:auto">
                    <option value="pending">待批阅</option><option value="revised">需修改</option><option value="approved">已通过</option>
                  </select>
                  <input v-model.number="reviewGrade" class="form-input" style="width:80px" type="number" min="0" max="100" placeholder="评分">
                  <button class="btn btn-primary btn-sm" :disabled="submitting" @click="submitReview(p)">保存批阅</button>
                  <button class="btn btn-outline btn-sm" @click="showReviewId = null">取消</button>
                </div>
              </div>
            </template>
          </div>
          <div style="text-align:right">
            <span v-if="p.uploadedBy !== 'teacher'" class="ws-paper-grade" :class="p.reviewStatus || 'pending'">{{ paperStatusLabel(p.reviewStatus) }}</span>
            <div style="margin-top:8px"><button class="btn btn-outline btn-sm" @click="downloadPaper(p)">⬇️ 下载</button></div>
          </div>
        </div>
      </div>
      <div v-else class="empty-card" style="margin-top:14px">暂无论文版本</div>
    </div>

    <!-- ===== Tab 3: 公告 ===== -->
    <div v-if="activeTab === 'anno'">
      <div class="ws-anno-page">
        <div class="ws-anno-toolbar">
          <div class="ws-anno-title">📢 导师公告</div>
          <button v-if="role === 'teacher'" class="ws-chat-float-btn" @click="openAnnoModal">➕ 发布公告</button>
          <span v-else class="ws-anno-tip">查看你导师发布的全部通知</span>
        </div>
        <div v-if="announcements.length">
          <div v-for="a in announcements" :key="a.id" class="ws-anno-card">
            <div class="ws-anno-badge">公告</div>
            <div class="ws-anno-title-line"><div class="ws-anno-title-text">{{ a.title }}</div></div>
            <div v-if="a.content" class="ws-anno-content">{{ a.content }}</div>
            <div v-if="a.fileName" class="ws-anno-file">📎 <a :href="annoFileUrl(a.id)" target="_blank">{{ a.fileName }}</a>
              <a :href="annoFileUrl(a.id)" target="_blank" class="ws-anno-download">⬇ 下载</a>
            </div>
            <div class="ws-anno-meta">👤 {{ a.teacherName }} · 🕐 {{ formatDateTime(a.createdAt) }}</div>
          </div>
        </div>
        <div v-else class="empty-card">📭 暂无公告</div>
      </div>
    </div>

    <!-- ===== Tab 4: 真·学生-老师私聊（原版 renderWsChatTab）===== -->
    <!-- ===== Tab 4: 真·私聊（样式 = AI Chat 同款）===== -->
    <div v-if="activeTab === 'chat'">
      <div class="chat-page chat-page-inline">
        <div class="chat-header">
          <div class="chat-title">
            <div class="chat-title-icon">💬</div>
            <div class="chat-title-text">私信对话 · {{ partner.name }}</div>
            <span class="chat-status">在线</span>
          </div>
          <div class="chat-header-right">
            <span class="chat-hint-sm">仅限双方可见</span>
          </div>
        </div>
        <div class="chat-messages" ref="chatBoxRef">
          <div v-if="!chatMsgs.length" class="chat-welcome">
            <div class="chat-welcome-icon">💬</div>
            <div class="chat-welcome-title">还没开始对话</div>
            <div class="chat-welcome-sub">发条消息打个招呼吧 ~</div>
          </div>
          <template v-else>
            <template v-for="(group, gi) in chatGroups" :key="gi">
              <div v-if="group.dayLabel" class="chat-day-sep">{{ group.dayLabel }}</div>
              <div v-for="m in group.msgs" :key="m.id" class="chat-msg" :class="String(m.senderId) === String(myId) ? 'user' : ''">
                <div class="chat-avatar">{{ (m.senderName || '').charAt(0) }}</div>
                <div class="chat-bubble-wrap">
                  <div class="chat-sender">{{ m.senderName || (m.senderRole === 'student' ? '同学' : '老师') }}</div>
                  <div class="chat-bubble" :class="String(m.senderId) === String(myId) ? 'user' : ''">{{ m.content }}</div>
                  <div class="chat-time">{{ msgTime(m.sentAt) }}</div>
                </div>
              </div>
            </template>
          </template>
        </div>
        <div class="chat-input-area">
          <div class="chat-input-wrap">
            <textarea ref="chatTaRef" v-model="chatInput" :disabled="chatSending" rows="1"
                      placeholder="输入消息，Enter 发送，Shift+Enter 换行..."
                      @keydown.enter.exact.prevent="sendChatMsg"
                      @input="autoResizeChat"></textarea>
          </div>
          <button class="chat-send" :disabled="chatSending || !chatInput.trim()" @click="sendChatMsg">
            <svg class="send-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M22 2L11 13"/><path d="M22 2L15 22L11 13L2 9L22 2Z"/>
            </svg>
          </button>
        </div>
      </div>
    </div>

    <!-- ===== quickChat Modal（AI 助手同款深色面板）===== -->
    <div v-if="showQuickChat" class="ws-chat-modal-mask" @click.self="showQuickChat = false">
      <div class="ws-chat-modal chat-modal-body">
        <div class="chat-header">
          <div class="chat-title">
            <div class="chat-title-icon">💬</div>
            <div class="chat-title-text">{{ partner.name }}</div>
            <span class="chat-status">在线</span>
          </div>
          <button class="chat-clear-btn" @click="showQuickChat = false">关闭</button>
        </div>
        <div class="chat-messages ws-chat-modal-body" ref="modalBoxRef">
          <div v-if="!modalMsgs.length" class="chat-welcome">
            <div class="chat-welcome-icon">💬</div>
            <div class="chat-welcome-title">私信对话 · {{ role === 'student' ? '老师' : '学生' }} {{ partner.name }}</div>
            <div class="chat-welcome-sub">发条消息打个招呼吧 ~</div>
          </div>
          <template v-else>
            <template v-for="(group, gi) in modalGroups" :key="gi">
              <div v-if="group.dayLabel" class="chat-day-sep">{{ group.dayLabel }}</div>
              <div v-for="m in group.msgs" :key="m.id" class="chat-msg" :class="String(m.senderId) === String(myId) ? 'user' : ''">
                <div class="chat-avatar">{{ (m.senderName || '').charAt(0) }}</div>
                <div class="chat-bubble-wrap">
                  <div class="chat-sender">{{ m.senderName || (m.senderRole === 'student' ? '同学' : '老师') }}</div>
                  <div class="chat-bubble" :class="String(m.senderId) === String(myId) ? 'user' : ''">{{ m.content }}</div>
                  <div class="chat-time">{{ msgTime(m.sentAt) }}</div>
                </div>
              </div>
            </template>
          </template>
        </div>
        <div class="chat-input-area ws-chat-modal-input">
          <div class="chat-input-wrap">
            <textarea v-model="modalInput" :disabled="modalSending" rows="1"
                      placeholder="输入消息，Enter 发送"
                      @keydown.enter.exact.prevent="sendModalMsg"
                      @input="autoResizeModal"></textarea>
          </div>
          <button class="chat-send" :disabled="modalSending || !modalInput.trim()" @click="sendModalMsg">
            <svg class="send-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M22 2L11 13"/><path d="M22 2L15 22L11 13L2 9L22 2Z"/>
            </svg>
          </button>
        </div>
      </div>
    </div>

    <!-- ===== 公告发布弹窗 ===== -->
    <div v-if="showAnnoModal" class="ws-chat-modal-mask" @click.self="showAnnoModal = false">
      <div class="ws-chat-modal" style="width:560px;height:auto;max-height:80vh">
        <div class="ws-chat-modal-header">
          <div class="ws-chat-header-left">
            <div class="ws-chat-header-avatar" style="background:rgba(255,255,255,.22);font-size:16px">📢</div>
            <div class="ws-chat-header-title">
              <div class="ws-chat-header-name">发布公告</div>
              <div class="ws-chat-header-status">你的所有学生都能收到</div>
            </div>
          </div>
          <button class="ws-chat-modal-close" @click="showAnnoModal = false">✕</button>
        </div>
        <div style="padding:20px 22px;display:flex;flex-direction:column;gap:14px">
          <div>
            <label style="font-size:12px;color:var(--text-light);margin-bottom:6px;display:block;font-weight:500">标题<span style="color:#ff6b8a">*</span></label>
            <input v-model="annoTitle" maxlength="120" placeholder="如：学校关于毕设格式的新要求"
                   style="width:100%;padding:10px 14px;border:1.5px solid var(--border);border-radius:10px;font-size:14px;outline:none;font-family:inherit">
          </div>
          <div>
            <label style="font-size:12px;color:var(--text-light);margin-bottom:6px;display:block;font-weight:500">内容（可选）</label>
            <textarea v-model="annoContent" rows="5" placeholder="写点什么给同学们..."
                      style="width:100%;padding:10px 14px;border:1.5px solid var(--border);border-radius:10px;font-size:14px;outline:none;font-family:inherit;resize:vertical;line-height:1.55"></textarea>
          </div>
          <div style="display:flex;justify-content:flex-end;gap:10px;margin-top:4px">
            <button class="btn" style="background:var(--border);color:var(--text);border:none;border-radius:10px;padding:9px 18px;cursor:pointer" @click="showAnnoModal = false">取消</button>
            <button class="ws-chat-send-btn" :disabled="submitting" @click="submitAnno">{{ submitting ? '发布中...' : '发布公告' }}</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import http from '@/api'

const props = defineProps<{ ws: any; role: 'student' | 'teacher'; advisorId: number }>()
const auth = useAuthStore()

const partner = computed(() => props.role === 'student' ? props.ws.teacher : props.ws.student)

const activeTab = ref<'req' | 'paper' | 'anno' | 'chat'>('req')
const submitting = ref(false)

// ===== 推荐题目 =====
const latestTopics = computed(() => props.ws?.latestRequirement?.topics || [])
const papers = computed(() => props.ws?.papers || [])
const fileInput = ref<HTMLInputElement | null>(null)
const paperTitle = ref('')
const paperNote = ref('')
const showReviewId = ref<number | null>(null)
const reviewComment = ref('')
const reviewStatus = ref('pending')
const reviewGrade = ref<number | null>(null)
const announcements = ref<any[]>([])
const showAnnoModal = ref(false)
const annoTitle = ref('')
const annoContent = ref('')
const reqContent = ref('')
const reqDeadline = ref('')
const reqTopics = ref('')

// ========== 真·私聊系统（原版 /workspace/chat API）==========
const myId = computed(() => auth.user?.id)

// Tab 4 消息
const chatMsgs = ref<any[]>([])
const chatInput = ref('')
const chatSending = ref(false)
const chatBoxRef = ref<HTMLElement | null>(null)
const chatTaRef = ref<HTMLTextAreaElement | null>(null)

// quickChat Modal 消息
const showQuickChat = ref(false)
const modalMsgs = ref<any[]>([])
const modalInput = ref('')
const modalSending = ref(false)
const modalBoxRef = ref<HTMLElement | null>(null)

// 日期分组（原版 renderWsChatMsgs 逻辑）
function groupByDay(msgs: any[]) {
  if (!msgs.length) return [] as any[]
  const today = new Date().toLocaleDateString('zh-CN')
  const yesterday = new Date(Date.now() - 86400000).toLocaleDateString('zh-CN')
  const groups: any[] = []
  let lastDay = ''
  for (const m of msgs) {
    const d = new Date(m.sentAt)
    const dayStr = d.toLocaleDateString('zh-CN')
    if (dayStr !== lastDay) {
      const label = dayStr === today ? '今天' : dayStr === yesterday ? '昨天' : dayStr
      groups.push({ dayLabel: label, msgs: [m] })
      lastDay = dayStr
    } else {
      groups[groups.length - 1].msgs.push(m)
    }
  }
  return groups
}
const chatGroups = computed(() => groupByDay(chatMsgs.value))
const modalGroups = computed(() => groupByDay(modalMsgs.value))

function msgTime(v: any) {
  if (!v) return ''
  try { return new Date(v).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false }) } catch { return '' }
}
function escapeHtml(s: string) {
  const d = document.createElement('div'); d.textContent = s; return d.innerHTML
}

async function loadChatMsgs() {
  try {
    const r: any = await http.get('/workspace/chat', { params: { advisorId: props.advisorId } })
    chatMsgs.value = r?.data || []
    await nextTick()
    if (chatBoxRef.value) chatBoxRef.value.scrollTop = chatBoxRef.value.scrollHeight
  } catch {}
}
async function loadModalMsgs() {
  try {
    const r: any = await http.get('/workspace/chat', { params: { advisorId: props.advisorId } })
    modalMsgs.value = r?.data || []
    await nextTick()
    if (modalBoxRef.value) modalBoxRef.value.scrollTop = modalBoxRef.value.scrollHeight
  } catch {}
}

function switchChatTab() {
  activeTab.value = 'chat'
  loadChatMsgs()
}

async function sendChatMsg() {
  const text = chatInput.value.trim()
  if (!text || chatSending.value) return
  chatSending.value = true
  try {
    await http.post('/workspace/chat', { advisorId: props.advisorId, content: text })
    chatInput.value = ''
    await loadChatMsgs()
  } catch (e: any) {
    alert('发送失败：' + (e?.message || ''))
  } finally {
    chatSending.value = false
  }
}

function openQuickChat() {
  showQuickChat.value = true
  loadModalMsgs()
}

function autoResizeChat() {
  const ta = chatTaRef.value
  if (ta) { ta.style.height = 'auto'; ta.style.height = Math.min(ta.scrollHeight, 140) + 'px' }
}
function autoResizeModal() {
  // modal textarea 没有 ref，用 DOM 查找
  const ta = document.querySelector('.ws-chat-modal-input textarea') as HTMLTextAreaElement
  if (ta) { ta.style.height = 'auto'; ta.style.height = Math.min(ta.scrollHeight, 120) + 'px' }
}

async function sendModalMsg() {
  const text = modalInput.value.trim()
  if (!text || modalSending.value) return
  modalSending.value = true
  try {
    await http.post('/workspace/chat', { advisorId: props.advisorId, content: text })
    modalInput.value = ''
    await loadModalMsgs()
    // 如果聊天 Tab 也开着，同步刷新
    if (activeTab.value === 'chat') loadChatMsgs()
  } catch (e: any) {
    alert('发送失败：' + (e?.message || ''))
  } finally {
    modalSending.value = false
  }
}

// ========== 原有助手/论文/公告代码（保留）==========
function formatDate(v: any) {
  if (!v) return ''
  try { return new Date(v).toLocaleDateString('zh-CN') } catch { return '' }
}
function formatDateTime(v: any) {
  if (!v) return ''
  try { return new Date(v).toLocaleString('zh-CN', { hour12: false }) } catch { return '' }
}
function paperStatusLabel(s: string) {
  return ({ pending: '待批阅', revised: '需修改', approved: '已通过' } as Record<string, string>)[s] || s || '待批阅'
}
function annoFileUrl(id: number) {
  const base = (http as any).defaults?.baseURL || ''
  return base + '/workspace/announce/file/' + id
}

async function loadAnnouncements() {
  try {
    const r: any = await http.get('/workspace/announcements')
    announcements.value = r?.data || []
  } catch {}
}

watch(activeTab, (t) => {
  if (t === 'anno' && announcements.value.length === 0) loadAnnouncements()
  if (t === 'req' && props.role === 'teacher') {
    const req = props.ws?.latestRequirement
    reqContent.value = req?.content || ''
    reqDeadline.value = req?.deadline ? new Date(req.deadline).toISOString().slice(0, 16) : ''
    reqTopics.value = req?.topics ? req.topics.map((x: any) => (x.title || '') + '|' + (x.desc || '')).join('\n') : ''
  }
})

async function submitReq() {
  submitting.value = true
  try {
    const topics = reqTopics.value.split('\n').filter(Boolean).map(line => {
      const [title, desc] = line.split('|')
      return { title: title?.trim() || '', desc: desc?.trim() || '' }
    })
    const body: any = { content: reqContent.value, topics }
    if (reqDeadline.value) body.deadline = new Date(reqDeadline.value).toISOString()
    await http.post('/workspace/requirement/' + props.advisorId, body)
    alert('✅ 要求已发布/更新！')
    location.reload()
  } catch (e: any) { alert('发布失败：' + (e?.message || '')) } finally { submitting.value = false }
}

function triggerFile() { fileInput.value?.click() }
function onFileChange(e: Event) { const f = (e.target as HTMLInputElement).files?.[0]; if (f) uploadPaper(f); (e.target as HTMLInputElement).value = '' }
function onDrop(e: DragEvent) { const f = e.dataTransfer?.files?.[0]; if (f) uploadPaper(f) }
async function uploadPaper(file: File) {
  if (file.size > 100 * 1024 * 1024) { alert('文件不能超过 100MB'); return }
  submitting.value = true
  try {
    const fd = new FormData(); fd.append('file', file); fd.append('title', paperTitle.value); fd.append('note', paperNote.value); fd.append('uploadedBy', 'student')
    await http.post('/workspace/paper/' + props.advisorId, fd)
    alert('✅ 上传成功！'); location.reload()
  } catch (e: any) { alert('上传失败：' + (e?.message || '')) } finally { submitting.value = false }
}
async function onTeacherFile(e: Event) {
  const f = (e.target as HTMLInputElement).files?.[0]; if (!f) return
  if (f.size > 100 * 1024 * 1024) { alert('文件不能超过 100MB'); return }
  submitting.value = true
  try {
    const fd = new FormData(); fd.append('file', f); fd.append('uploadedBy', 'teacher')
    await http.post('/workspace/paper/' + props.advisorId, fd)
    alert('✅ 上传成功！'); location.reload()
  } catch (e: any) { alert('上传失败：' + (e?.message || '')) } finally { submitting.value = false }
}
function downloadPaper(p: any) {
  const base = (http as any).defaults?.baseURL || ''
  const token = localStorage.getItem('zhixue_token')
  fetch(base + '/workspace/paper/' + p.id + '/download', { headers: token ? { Authorization: 'Bearer ' + token } : {} })
    .then(async res => { if (!res.ok) throw new Error('下载失败'); const blob = await res.blob(); const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = p.fileName; a.click(); URL.revokeObjectURL(url) })
    .catch(e => alert(e.message))
}
function toggleReview(p: any) {
  if (showReviewId.value === p.id) { showReviewId.value = null; return }
  showReviewId.value = p.id; reviewComment.value = p.teacherComment || ''; reviewStatus.value = p.reviewStatus || 'pending'; reviewGrade.value = p.grade ?? null
}
async function submitReview(p: any) {
  submitting.value = true
  try {
    await http.post('/workspace/paper/review/' + p.id, { teacherComment: reviewComment.value, reviewStatus: reviewStatus.value, grade: reviewGrade.value })
    alert('✅ 批阅已保存！'); location.reload()
  } catch (e: any) { alert('保存失败：' + (e?.message || '')) } finally { submitting.value = false }
}
function openAnnoModal() { annoTitle.value = ''; annoContent.value = ''; showAnnoModal.value = true }
async function submitAnno() {
  if (!annoTitle.value.trim()) { alert('标题不能为空'); return }
  submitting.value = true
  try { await http.post('/workspace/announce', { title: annoTitle.value, content: annoContent.value }); showAnnoModal.value = false; alert('✅ 公告已发布！'); loadAnnouncements() }
  catch (e: any) { alert('发布失败：' + (e?.message || '')) } finally { submitting.value = false }
}

onMounted(() => {
  auth.loadFromStorage()
  if (props.role === 'teacher') {
    const req = props.ws?.latestRequirement
    reqContent.value = req?.content || ''
    reqDeadline.value = req?.deadline ? new Date(req.deadline).toISOString().slice(0, 16) : ''
    reqTopics.value = req?.topics ? req.topics.map((x: any) => (x.title || '') + '|' + (x.desc || '')).join('\n') : ''
  }
})
</script>

<style scoped>
.form-group { margin-bottom: 12px; }
.form-group label { display: block; font-size: 12px; color: var(--text-light); margin-bottom: 6px; font-weight: 500; }
.form-input { width: 100%; padding: 10px 14px; border: 1.5px solid var(--border); border-radius: 10px; font-size: 14px; outline: none; font-family: inherit; background: var(--card); color: var(--text); transition: border-color .2s; box-sizing: border-box; }
.form-input:focus { border-color: var(--primary); }
.empty-card { padding: 40px; text-align: center; color: var(--text-light); background: var(--card); border: 1px solid var(--border); border-radius: 12px; }

/* ===== AI Chat 同款聊天样式（Tab 4 + Modal 共用）===== */
.chat-page {
  display: flex; flex-direction: column;
  background: var(--card); border: 1px solid var(--border);
  border-radius: 14px; overflow: hidden;
}
.chat-page-inline { height: calc(100vh - 280px); min-height: 560px; }
.chat-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px 18px; border-bottom: 1px solid var(--border);
  background: linear-gradient(135deg, rgba(99,102,241,0.06), rgba(168,85,247,0.06));
}
.chat-title { display: flex; align-items: center; gap: 10px; font-size: 15px; font-weight: 600; }
.chat-title-icon { font-size: 18px; }
.chat-title-text { color: var(--text); }
.chat-status { font-size: 10px; padding: 2px 8px; border-radius: 10px; background: rgba(16,185,129,0.12); color: #10b981; font-weight: 500; display: flex; align-items: center; gap: 4px; }
.chat-status::before { content: ''; width: 5px; height: 5px; border-radius: 50%; background: #10b981; animation: pulse 2s infinite; }
@keyframes pulse { 0%,100%{opacity:1} 50%{opacity:.4} }
.chat-header-right { display: flex; gap: 8px; align-items: center; }
.chat-hint-sm { font-size: 11px; color: var(--text-light); }

.chat-messages {
  flex: 1; overflow-y: auto; padding: 20px 24px;
  display: flex; flex-direction: column; gap: 16px;
  background: var(--card);
}

/* 欢迎空状态 */
.chat-welcome { text-align: center; padding: 40px 10px; color: var(--text-light); }
.chat-welcome-icon { font-size: 48px; margin-bottom: 10px; }
.chat-welcome-title { font-size: 16px; font-weight: 600; color: var(--text); }
.chat-welcome-sub { margin-top: 6px; font-size: 13px; }

/* 消息气泡 —— 跟 Chat.vue 同款！ */
.chat-msg { display: flex; gap: 12px; align-items: flex-start; animation: chatMsgIn .25s ease; }
.chat-msg.user { flex-direction: row-reverse; }
@keyframes chatMsgIn { from { opacity: 0; transform: translateY(6px) } to { opacity: 1; transform: none } }

.chat-avatar {
  width: 36px; height: 36px; border-radius: 10px; flex-shrink: 0;
  background: linear-gradient(135deg, #16a34a, #22c55e);
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-weight: 600; font-size: 14px;
}
.chat-msg.user .chat-avatar { background: linear-gradient(135deg, #6366f1, #8b5cf6); }

.chat-bubble-wrap { max-width: 72%; display: flex; flex-direction: column; gap: 4px; }
.chat-msg.user .chat-bubble-wrap { align-items: flex-end; }

.chat-sender { font-size: 11px; color: var(--text-lighter); padding: 0 4px; }

.chat-bubble {
  padding: 12px 16px; border-radius: 14px;
  font-size: 14px; line-height: 1.65;
  background: var(--bg); border: 1px solid var(--border);
  color: var(--text); word-break: break-word; white-space: pre-wrap;
  border-top-left-radius: 4px;
}
.chat-bubble.user {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  border-color: transparent; color: #fff;
  border-top-left-radius: 14px; border-top-right-radius: 4px;
}
.chat-time { font-size: 10px; color: var(--text-lighter); padding: 0 4px; }

/* 日期分割线 */
.chat-day-sep { text-align: center; margin: 18px 0 14px; font-size: 11px; color: var(--text-lighter); display: flex; align-items: center; gap: 12px; }
.chat-day-sep::before, .chat-day-sep::after { content: ''; flex: 1; height: 1px; background: linear-gradient(90deg, transparent, var(--border), transparent); }

/* 输入区 */
.chat-input-area {
  display: flex; gap: 10px; padding: 14px 18px;
  border-top: 1px solid var(--border);
  align-items: flex-end; background: var(--card);
}
.chat-input-wrap {
  flex: 1; border: 1.5px solid var(--border); border-radius: 12px;
  padding: 6px 12px; background: var(--bg);
  transition: border-color .2s, box-shadow .2s;
}
.chat-input-wrap:focus-within { border-color: var(--primary); box-shadow: 0 0 0 3px rgba(99,102,241,0.12); }
.chat-input-wrap textarea {
  width: 100%; border: none; outline: none; resize: none;
  font-size: 14px; background: transparent; color: var(--text);
  font-family: inherit; line-height: 1.55; max-height: 140px;
}
.chat-send {
  width: 44px; height: 44px; border-radius: 12px; border: none; cursor: pointer;
  background: linear-gradient(135deg, #6366f1, #8b5cf6); color: #fff;
  display: flex; align-items: center; justify-content: center;
  transition: transform .15s, box-shadow .2s, opacity .2s; flex-shrink: 0;
}
.chat-send:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 4px 14px rgba(99,102,241,.35); }
.chat-send:disabled { opacity: .5; cursor: not-allowed; }
.send-icon { width: 20px; height: 20px; }

/* Modal 里的聊天适配 —— AI 助手同款深色面板 */
.ws-chat-modal.chat-modal-body {
  display: flex; flex-direction: column;
  border-radius: 14px; overflow: hidden;
}
.ws-chat-modal.chat-modal-body .chat-messages { flex: 1; }
.ws-chat-modal-body {
  flex: 1; overflow-y: auto;
  background: var(--card);
}
.ws-chat-modal-input {
  display: flex; gap: 10px; padding: 12px 16px;
  border-top: 1px solid var(--border); align-items: flex-end; background: var(--card);
}

/* 公告页 */
.ws-anno-page { margin-top: 14px; }
.ws-anno-toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.ws-anno-title { font-size: 15px; font-weight: 600; }
.ws-anno-tip { font-size: 12px; color: var(--text-light); }
.ws-anno-card { background: var(--card); border: 1px solid var(--border); border-radius: 12px; padding: 16px 18px; margin-bottom: 12px; position: relative; }
.ws-anno-badge { position: absolute; top: 12px; right: 14px; font-size: 11px; padding: 2px 10px; border-radius: 10px; background: rgba(99,102,241,.12); color: var(--primary); }
.ws-anno-title-line { padding-right: 60px; }
.ws-anno-title-text { font-size: 15px; font-weight: 600; }
.ws-anno-content { margin-top: 10px; font-size: 13px; line-height: 1.65; color: var(--text); white-space: pre-wrap; }
.ws-anno-file { margin-top: 10px; font-size: 12px; color: var(--primary); }
.ws-anno-download { margin-left: 10px; font-size: 11px; color: var(--text-light); text-decoration: none; }
.ws-anno-meta { margin-top: 10px; font-size: 11px; color: var(--text-lighter); }
</style>
