<template>
  <div>
    <!-- 头部：对方信息 -->
    <div class="ws-header-card">
      <div class="ws-avatar">{{ partner?.name?.charAt(0) }}</div>
      <div class="ws-info">
        <div class="ws-name">{{ role === 'student' ? '指导教师：' : '学生：' }}{{ partner?.name }}</div>
        <div class="ws-sub">{{ partner?.college || '' }} · {{ partner?.major || '' }}{{ partner?.no ? ' · ' + partner.no : '' }}</div>
        <div v-if="ws.topic" class="ws-sub" style="color:var(--primary);margin-top:4px">🎯 论文方向：{{ ws.topic }}</div>
      </div>
      <div style="text-align:right;display:flex;flex-direction:column;align-items:flex-end;gap:8px">
        <button class="ws-chat-float-btn" @click="emit('chat', advisorId, partner?.name)">💬 私聊{{ role === 'student' ? '老师' : '学生' }}</button>
        <div style="font-size:12px;color:var(--text-light)">已确认<br>{{ ws.approvedAt ? new Date(ws.approvedAt).toLocaleDateString('zh-CN') : '' }}</div>
      </div>
    </div>

    <!-- Tabs -->
    <div class="ws-tabs">
      <div class="ws-tab" :class="{ active: tab === 'req' }" @click="tab = 'req'">📋 {{ role === 'teacher' ? '发布要求' : '查看要求' }}</div>
      <div class="ws-tab" :class="{ active: tab === 'paper' }" @click="tab = 'paper'">📄 论文{{ role === 'teacher' ? '管理' : '上传' }} <span v-if="papers.length" class="ws-badge approved">{{ papers.length }}</span></div>
      <div class="ws-tab" :class="{ active: tab === 'anno' }" @click="tab = 'anno'">📢 公告</div>
    </div>

    <div class="ws-tab-body">
      <!-- 要求 -->
      <div v-if="tab === 'req'">
        <div v-if="role === 'student'">
          <div v-if="!ws.latestRequirement" class="empty-card">教师还未发布毕业设计要求，耐心等待~</div>
          <div v-else>
            <div class="ws-requirement-card">
              <h4>📋 毕业设计要求 <span v-if="ws.latestRequirement.deadline" class="ws-badge pending">截止 {{ new Date(ws.latestRequirement.deadline).toLocaleDateString('zh-CN') }}</span></h4>
              <div style="white-space:pre-wrap;font-size:14px;line-height:1.7">{{ ws.latestRequirement.content || '（未填写）' }}</div>
              <div style="margin-top:14px;font-size:11px;color:var(--text-light)">发布时间：{{ new Date(ws.latestRequirement.createdAt).toLocaleString('zh-CN') }}</div>
            </div>
            <div v-if="ws.latestRequirement.topics?.length" class="ws-requirement-card">
              <h4>💡 推荐题目（{{ ws.latestRequirement.topics.length }}）</h4>
              <div v-for="(t, i) in ws.latestRequirement.topics" :key="i" class="ws-topic-item">
                <span style="color:var(--text-light);font-size:12px;margin-right:8px">#{{ i + 1 }}</span>
                <span class="ws-topic-title">{{ t.title }}</span>
                <div v-if="t.desc" class="ws-topic-desc">{{ t.desc }}</div>
              </div>
            </div>
          </div>
        </div>
        <div v-else>
          <div class="ws-requirement-card">
            <h4>✏️ 发布毕业设计要求 <span v-if="ws.latestRequirement" style="font-size:11px;color:var(--text-light);font-weight:400">上次发布：{{ new Date(ws.latestRequirement.createdAt).toLocaleString('zh-CN') }}</span></h4>
            <div class="form-group"><label>要求正文</label>
              <textarea v-model="reqContent" class="form-input" rows="6" placeholder="例如：选题范围、字数要求、格式规范、关键时间节点..."></textarea></div>
            <div class="form-group"><label>截止日期（可选）</label>
              <input v-model="reqDeadline" class="form-input" type="datetime-local"></div>
            <div style="margin-top:10px"><strong style="font-size:13px">推荐题目（可选，每行一条，格式：标题|描述）</strong></div>
            <textarea v-model="reqTopics" class="form-input" rows="4" placeholder="题目一|可选描述&#10;题目二|可选描述"></textarea>
            <div style="margin-top:14px"><button class="btn btn-primary" @click="submitReq">{{ ws.latestRequirement ? '更新要求' : '发布要求' }}</button></div>
          </div>
        </div>
      </div>

      <!-- 论文 -->
      <div v-else-if="tab === 'paper'">
        <div v-if="role === 'student'" class="ws-requirement-card">
          <h4>📤 上传新版本论文</h4>
          <input type="file" accept=".pdf,.doc,.docx,.txt,.zip,.rar" @change="onStudentFile">
          <div class="form-group" style="margin-top:12px"><label>论文标题（可选）</label><input v-model="paperTitle" class="form-input" placeholder="留空则使用文件名"></div>
          <div class="form-group"><label>备注（可选）</label><textarea v-model="paperNote" class="form-input" rows="2" placeholder="给老师看的说明"></textarea></div>
        </div>
        <div v-else class="ws-requirement-card">
          <h4>📎 教师上传批注/参考材料（可选）</h4>
          <input type="file" accept=".pdf,.doc,.docx,.txt,.zip,.rar" @change="onTeacherFile">
        </div>

        <div v-if="!papers.length && !role" class="empty-card">暂无论文版本</div>
        <div v-for="p in papers" :key="p.id" class="ws-paper-row" :class="{ 'teacher-upload': p.uploadedBy === 'teacher' }">
          <div class="ws-paper-ver">v{{ p.versionNo }}</div>
          <div class="ws-paper-info">
            <div class="ws-paper-title">{{ p.fileName }}<span v-if="p.uploadedBy === 'teacher'" style="font-size:11px;color:#d97706"> [教师上传]</span></div>
            <div class="ws-paper-meta">{{ p.title ? '📝 ' + p.title + ' · ' : '' }}{{ (p.fileSizeBytes / 1024).toFixed(1) }} KB · 上传于 {{ new Date(p.createdAt).toLocaleString('zh-CN') }}</div>
            <div v-if="p.note" class="ws-paper-note">"{{ p.note }}"</div>
            <div v-if="p.teacherComment" style="font-size:12px;color:#7c3aed;margin-top:4px">👤 教师批注：{{ p.teacherComment }}</div>
            <div v-if="p.grade" style="font-size:12px;color:#16a34a;margin-top:2px">⭐ 评分：{{ p.grade }}/100</div>
            <div v-if="role === 'teacher' && p.uploadedBy !== 'teacher'" style="margin-top:10px;padding:12px;background:var(--bg);border-radius:8px;border-left:3px solid var(--primary)">
              <div class="form-group"><label>教师评语</label><textarea v-model="p._comment" class="form-input" rows="2" :placeholder="p.teacherComment || '批注、修改建议...'"></textarea></div>
              <div style="display:flex;gap:8px;align-items:center">
                <select v-model="p._status" class="form-input" style="width:auto">
                  <option value="pending">待批阅</option><option value="revised">需修改</option><option value="approved">已通过</option>
                </select>
                <input v-model="p._grade" class="form-input" style="width:80px" type="number" min="0" max="100" placeholder="评分">
                <button class="btn btn-primary btn-sm" @click="submitReview(p)">保存批阅</button>
              </div>
            </div>
          </div>
          <div style="text-align:right">
            <span v-if="p.uploadedBy !== 'teacher'" class="ws-paper-grade" :class="p.reviewStatus || 'pending'">{{ { pending: '待批阅', revised: '需修改', approved: '已通过' }[p.reviewStatus] || p.reviewStatus }}</span>
            <div style="margin-top:8px"><button class="btn btn-outline btn-sm" @click="downloadPaper(p.id)">⬇️ 下载</button></div>
          </div>
        </div>
      </div>

      <!-- 公告 -->
      <div v-else-if="tab === 'anno'">
        <div class="ws-anno-page">
          <div class="ws-anno-toolbar">
            <div class="ws-anno-title">📢 导师公告</div>
            <button v-if="role === 'teacher'" class="ws-chat-float-btn" @click="showAnnoForm = !showAnnoForm">➕ 发布公告</button>
            <span v-else class="ws-anno-tip">查看你导师发布的全部通知</span>
          </div>
          <div v-if="role === 'teacher' && showAnnoForm" class="ws-requirement-card">
            <h4>发布公告</h4>
            <div class="form-group"><label>标题 *</label><input v-model="annoTitle" class="form-input" maxlength="120"></div>
            <div class="form-group"><label>内容（可选）</label><textarea v-model="annoContent" class="form-input" rows="4"></textarea></div>
            <button class="btn btn-primary" @click="publishAnno">发布</button>
          </div>
          <div v-if="!annos.length" class="ws-chat-empty"><div class="ws-chat-empty-icon">📭</div>暂无公告</div>
          <div v-for="a in annos" :key="a.id" class="ws-anno-card">
            <div class="ws-anno-badge">公告</div>
            <div class="ws-anno-title-text">{{ a.title }}</div>
            <div v-if="a.content" class="ws-anno-content" style="white-space:pre-wrap">{{ a.content }}</div>
            <div class="ws-anno-meta">👤 {{ a.teacherName }} · 🕐 {{ new Date(a.createdAt).toLocaleString('zh-CN', { hour12: false }) }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import Icon from '@/components/Icon.vue'
import { api, getToken } from '@/api/client'

const props = defineProps({ ws: Object, role: String, advisorId: [Number, String], me: Object })
const emit = defineEmits(['reload', 'chat'])

const tab = ref('req')
const papers = ref([])
const annos = ref([])
const reqContent = ref('')
const reqDeadline = ref('')
const reqTopics = ref('')
const paperTitle = ref('')
const paperNote = ref('')
const showAnnoForm = ref(false)
const annoTitle = ref('')
const annoContent = ref('')

const partner = computed(() => props.role === 'student' ? props.ws.teacher : props.ws.student)

watch(() => props.ws, (w) => {
  if (w) {
    papers.value = w.papers || []
    reqContent.value = w.latestRequirement?.content || ''
    reqDeadline.value = w.latestRequirement?.deadline ? new Date(w.latestRequirement.deadline).toISOString().slice(0, 16) : ''
    reqTopics.value = w.latestRequirement?.topics ? w.latestRequirement.topics.map(t => t.title + '|' + (t.desc || '')).join('\n') : ''
  }
}, { immediate: true })

watch(tab, (t) => { if (t === 'anno') loadAnnos() })

async function loadAnnos() {
  const r = await api('GET', '/workspace/announcements')
  annos.value = r.success ? (r.data || []) : []
}

async function submitReq() {
  if (!reqContent.value.trim()) { alert('请填写要求内容'); return }
  const topics = reqTopics.value.trim() ? reqTopics.value.trim().split('\n').map(l => {
    const [title, desc] = l.split('|').map(s => (s || '').trim()); return { title, desc }
  }).filter(t => t.title) : []
  const r = await api('POST', '/workspace/requirements', {
    advisorId: props.advisorId, content: reqContent.value.trim(), topics,
    deadline: reqDeadline.value ? reqDeadline.value.replace('T', 'T') + ':00' : null,
  })
  alert(r.message)
  if (r.success) emit('reload')
}

async function onStudentFile(e) { await uploadPaper(e.target.files[0], 'student'); e.target.value = '' }
async function onTeacherFile(e) { await uploadPaper(e.target.files[0], 'teacher'); e.target.value = '' }

async function uploadPaper(file, role) {
  if (!file) return
  const fd = new FormData()
  fd.append('advisorId', props.advisorId)
  fd.append('file', file)
  if (role === 'student') {
    if (paperTitle.value) fd.append('title', paperTitle.value)
    if (paperNote.value) fd.append('note', paperNote.value)
  }
  const token = getToken()
  const resp = await fetch(import.meta.env.VITE_API_BASE + '/api/workspace/papers/upload', {
    method: 'POST', headers: { Authorization: 'Bearer ' + token }, body: fd,
  })
  const data = await resp.json()
  alert(data.message)
  if (data.success) { paperTitle.value = ''; paperNote.value = ''; emit('reload') }
}

function downloadPaper(id) {
  const token = getToken()
  fetch(import.meta.env.VITE_API_BASE + '/api/workspace/papers/' + id + '/download', { headers: { Authorization: 'Bearer ' + token } })
    .then(r => r.blob()).then(b => {
      const url = URL.createObjectURL(b); const a = document.createElement('a')
      a.href = url; a.download = 'paper_v' + id; document.body.appendChild(a); a.click(); a.remove()
      URL.revokeObjectURL(url)
    }).catch(() => alert('下载失败'))
}

async function submitReview(p) {
  const r = await api('POST', '/workspace/papers/' + p.id + '/review', {
    comment: p._comment || '', status: p._status || 'pending', grade: p._grade ? parseInt(p._grade) : null,
  })
  alert(r.message)
  if (r.success) emit('reload')
}

async function publishAnno() {
  if (!annoTitle.value.trim()) { alert('请填写公告标题'); return }
  const r = await api('POST', '/workspace/announce', { title: annoTitle.value.trim(), content: annoContent.value.trim() })
  alert(r.message)
  if (r.success) { showAnnoForm.value = false; annoTitle.value = ''; annoContent.value = ''; await loadAnnos() }
}

onMounted(() => { if (tab.value === 'anno') loadAnnos() })
</script>
