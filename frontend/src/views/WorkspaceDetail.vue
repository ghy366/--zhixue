<template>
  <div>
    <!-- ===== 原版 ws-header-card ===== -->
    <div class="ws-header-card">
      <div class="ws-avatar">{{ partner.name?.charAt(0) }}</div>
      <div class="ws-info">
        <div class="ws-name">
          {{ role === 'student' ? t('指导教师：') : t('学生：') }}{{ partner.name }}
          <span class="ws-confirmed-badge"><svg viewBox="0 0 16 16" class="wc-check"><path d="M3 8.5l3.2 3.2L13 5" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/></svg>{{ t('已确认') }}</span>
        </div>
        <div class="ws-sub">{{ partner.college || t('学院暂无') }}<template v-if="partner.major"> · {{ partner.major }}</template><template v-if="partner.no"> · {{ role === 'student' ? t('工号') : t('学号') }} {{ partner.no }}</template></div>
        <div class="ws-sub ws-topic-line" v-if="ws.topic">🎯 {{ t('论文方向：') }}<b>{{ ws.topic }}</b></div>
        <div class="ws-sub" v-else>🎯 {{ t('论文方向：暂未确定（与导师沟通后确定）') }}</div>
      </div>
      <div style="text-align:right;display:flex;flex-direction:column;align-items:flex-end;gap:8px">
        <!-- 原版 quickChat：打开真·私聊 Modal（不是 AI 助手！） -->
        <button class="ws-chat-float-btn" @click="openQuickChat">💬 {{ t('私聊{who}', { who: role === 'student' ? t('老师') : t('学生') }) }}</button>
        <div style="font-size:12px;color:var(--text-light)">{{ t('确认于') }} {{ formatDate(ws.approvedAt) || t('暂无') }}</div>
      </div>
    </div>

    <!-- ===== 原版 ws-tabs（加 Tab: 流程 / 聊天）===== -->
    <div class="ws-tabs">
      <div class="ws-tab" :class="{ active: activeTab === 'flow' }" @click="activeTab = 'flow'">
        🎓 {{ t('流程') }}
        <span v-if="flowPendingReview" class="ws-tab-dot warn">{{ flowPendingReview }}</span>
      </div>
      <div class="ws-tab" :class="{ active: activeTab === 'req' }" @click="activeTab = 'req'">
        📋 {{ role === 'teacher' ? t('发布要求') : t('查看要求') }}
        <span v-if="role === 'student' && reqDeadlineTag" class="ws-tab-dot" :class="reqDeadlineTag.cls">{{ reqDeadlineTag.short }}</span>
      </div>
      <div class="ws-tab" :class="{ active: activeTab === 'paper' }" @click="activeTab = 'paper'">
        📄 {{ t('论文') }}{{ role === 'teacher' ? t('管理') : t('上传') }}
        <span v-if="ws.papers?.length" class="ws-badge" :class="paperBadgeCls">{{ ws.papers.length }}</span>
      </div>
      <div class="ws-tab" :class="{ active: activeTab === 'anno' }" @click="activeTab = 'anno'">
        📢 {{ t('公告') }}
        <span v-if="announcements.length" class="ws-badge approved">{{ announcements.length }}</span>
      </div>
      <div class="ws-tab" :class="{ active: activeTab === 'chat' }" @click="switchChatTab">💬 {{ t('聊天') }}</div>
    </div>

    <!-- ===== Tab 0: 流程里程碑（开题 → 中期 → 论文 → 答辩）===== -->
    <div v-if="activeTab === 'flow'" class="flow-wrap">
      <!-- 顶部进度总览 -->
      <div class="flow-progress">
        <template v-for="(m, i) in milestones" :key="'p' + m.id">
          <div class="flow-step" :class="m.status">
            <span class="flow-step-ico">{{ statusIcon(m.status) }}</span>
            <span class="flow-step-name">{{ m.stageName }}</span>
          </div>
          <div v-if="i < milestones.length - 1" class="flow-arrow" :class="{ done: m.status === 'passed' }">→</div>
        </template>
        <div v-if="defenseScore" class="flow-total">
          {{ t('毕设总评') }} <b>{{ defenseScore }}</b>
        </div>
      </div>

      <!-- 时间线节点 -->
      <div class="flow-list">
        <div v-for="m in milestones" :key="m.id" class="flow-node" :class="m.status">
          <div class="flow-dot" v-html="statusIconSvg(m.status)" />
          <div class="flow-body">
            <div class="flow-head">
              <b class="flow-stage">{{ m.stageName }}</b>
              <span class="flow-badge" :class="m.status">{{ flowStatusText(m) }}</span>
              <span class="flow-times">
                {{ m.submittedAt ? t('提交') + ' ' + fmtTime(m.submittedAt) : '' }}
                {{ m.reviewedAt ? t('· 审核') + ' ' + fmtTime(m.reviewedAt) : '' }}
              </span>
            </div>

            <!-- 学生已提交的内容 -->
            <div v-if="m.title || m.content" class="flow-detail">
              <div v-if="m.title" class="flow-title">📌 {{ m.title }}</div>
              <div v-if="m.content" class="flow-content">{{ m.content }}</div>
            </div>
            <div v-if="m.fileName" class="flow-file">
              📎 <a @click.prevent="downloadMilestone(m)" href="#">{{ m.fileName }}</a>
              <span class="flow-size">{{ fmtSize(m.fileSizeBytes) }}</span>
            </div>

            <!-- 导师评语 -->
            <div v-if="m.teacherComment" class="flow-comment" :class="m.status">
              <span class="fc-label">{{ t('导师评语') }}</span>{{ m.teacherComment }}
            </div>

            <!-- 答辩成绩 -->
            <div v-if="m.stage === 'defense' && m.totalScore != null" class="flow-score">
              <span>{{ t('导师评分') }} <b>{{ m.advisorScore }}</b> × 40%</span>
              <span>＋</span>
              <span>{{ t('答辩组评分') }} <b>{{ m.defenseScore }}</b> × 60%</span>
              <span class="fs-eq">=</span>
              <span class="fs-total">{{ t('总评') }} <b>{{ m.totalScore }}</b></span>
            </div>

            <!-- 学生提交表单 -->
            <div v-if="m.canSubmit" class="flow-form">
              <div class="ff-title">{{ m.status === 'returned' ? t('已退回，请修改后重新提交') : t('提交{n}材料', { n: m.stageName }) }}</div>
              <input v-model="form.title" class="form-input" maxlength="120" :placeholder="t('材料标题，如：{n}（第一版）', { n: m.stageName })">
              <textarea v-model="form.content" class="form-input" rows="3" :placeholder="m.stage === 'opening' ? t('研究背景、目标、技术路线概述…') : m.stage === 'midterm' ? t('当前进展、已完成工作、遇到的问题…') : m.stage === 'defense' ? t('答辩演示说明…') : t('说明（可选）')"></textarea>
              <div class="ff-row">
                <input type="file" class="ff-file" @change="(e: any) => form.file = e.target.files[0]">
                <button class="btn btn-primary btn-sm" :disabled="flowSubmitting || (!form.title && !form.content && !form.file)" @click="submitMilestone(m)">
                  {{ flowSubmitting ? t('提交中…') : t('提交') }}
                </button>
              </div>
              <div class="ff-hint">{{ t('支持 pdf/doc/docx/txt/zip/rar/ppt/pptx · 100MB 以内 · 文件可选') }}</div>
            </div>

            <!-- 教师审核表单 -->
            <div v-if="m.canReview" class="flow-form review">
              <textarea v-model="reviewForm.comment" class="form-input" rows="2" :placeholder="t('评语（通过寄语或退回原因）')"></textarea>
              <div v-if="m.stage === 'defense'" class="ff-scores">
                <label>{{ t('导师评分(40%)') }}<input type="number" v-model.number="reviewForm.advisorScore" min="0" max="100" class="form-input score"></label>
                <label>{{ t('答辩组评分(60%)') }}<input type="number" v-model.number="reviewForm.defenseScore" min="0" max="100" class="form-input score"></label>
                <span class="fs-preview" v-if="validScores">{{ t('合成总评 {n}', { n: computedTotal }) }}</span>
              </div>
              <div class="ff-row">
                <button class="btn btn-primary btn-sm" :disabled="flowSubmitting || (m.stage === 'defense' && !validScores)" @click="reviewMilestone(m, 'passed')">✅ {{ t('通过') }}</button>
                <button class="btn btn-outline btn-sm ff-return" :disabled="flowSubmitting" @click="reviewMilestone(m, 'returned')">↩️ {{ t('退回修改') }}</button>
              </div>
            </div>

            <!-- 锁定提示 -->
            <div v-if="!m.unlocked" class="flow-locked">🔒 {{ t('完成前一道环节后解锁') }}</div>
          </div>
        </div>
      </div>
      <div v-if="!milestones.length" class="empty-card">{{ t('暂无流程数据') }}</div>
    </div>

    <!-- ===== Tab 1: 要求 & 推荐题目 ===== -->
    <div v-if="activeTab === 'req'">
      <template v-if="role === 'student'">
        <!-- 当前课题：已确定则优先展示 -->
        <div v-if="ws.topic" class="ws-requirement-card ws-current-topic">
          <h4>🎯 {{ t('当前课题') }}</h4>
          <div class="wct-title">{{ ws.topic }}</div>
          <div class="wct-sub">{{ t('课题已确定 · 如需调整请通过私聊与导师沟通') }}</div>
          <button class="btn btn-outline btn-sm wct-chat-btn" @click="openQuickChat">💬 {{ t('与导师沟通') }}</button>
        </div>

        <!-- 确定课题：未确定时始终可用（不依赖导师是否已发布要求） -->
        <div v-if="!ws.topic" class="ws-requirement-card">
          <h4>🎯 {{ t('确定毕业设计课题') }}
            <span class="ws-badge pending">{{ t('尚未确定课题') }}</span>
          </h4>
          <template v-if="latestTopics.length">
            <div class="wst-group-title">{{ t('导师推荐题目（点「选定」即可确认）') }}</div>
            <div v-for="(tp, i) in latestTopics" :key="i" class="ws-topic-item">
              <div class="wst-head">
                <span class="wst-idx">#{{ i + 1 }}</span>
                <span class="ws-topic-title">{{ tp.title }}</span>
                <button class="wst-pick" :disabled="topicSubmitting" @click="confirmTopic(tp.title)">🎯 {{ t('选定该题目') }}</button>
              </div>
              <div v-if="tp.desc" class="ws-topic-desc" :class="{ collapsed: !expandedTopic(i) && needCollapseTopic(tp) }">{{ tp.desc }}</div>
              <button v-if="needCollapseTopic(tp)" class="wst-more" @click="toggleTopic(i)">{{ expandedTopics.has(i) ? t('收起') : t('查看详情') }}</button>
            </div>
          </template>
          <div class="wst-custom">
            <input v-model="customTopic" class="form-input" maxlength="120" :placeholder="t('或输入自定义课题名称')">
            <button class="btn btn-primary btn-sm" :disabled="topicSubmitting || !customTopic.trim()" @click="confirmTopic(customTopic)">✅ {{ t('确认课题') }}</button>
          </div>
          <div class="wst-custom-hint">{{ t('建议先与导师私聊沟通课题方向，确认后导师可在工作台看到') }}</div>
        </div>

        <div v-if="!ws.latestRequirement" class="empty-card">{{ t('教师还未发布毕业设计要求，耐心等待~') }}</div>
        <template v-else>
          <div class="ws-requirement-card">
            <h4>📋 {{ t('毕业设计要求') }}
              <span v-if="reqDeadlineTag" class="ws-badge" :class="reqDeadlineTag.cls">{{ reqDeadlineTag.text }}</span>
            </h4>
            <div class="ws-req-content" :class="{ collapsed: !reqExpanded && reqNeedCollapse }">{{ ws.latestRequirement.content || t('（未填写）') }}</div>
            <button v-if="reqNeedCollapse" class="ws-req-toggle" @click="reqExpanded = !reqExpanded">
              {{ reqExpanded ? t('收起 ▲') : t('查看完整要求 ▼') }}
            </button>
            <div class="ws-req-meta">🕐 {{ t('发布时间：') }}{{ formatDateTime(ws.latestRequirement.createdAt) || t('暂无') }}<template v-if="ws.latestRequirement.deadline"> · ⏰ {{ t('截止：') }}{{ formatDateTime(ws.latestRequirement.deadline) }}</template></div>
          </div>
          <!-- 课题已确定时：推荐题目只读浏览 -->
          <div v-if="latestTopics.length && ws.topic" class="ws-requirement-card">
            <h4>💡 {{ t('推荐题目（{n}）', { n: latestTopics.length }) }}</h4>
            <div v-for="(tp, i) in latestTopics" :key="i" class="ws-topic-item">
              <div class="wst-head">
                <span class="wst-idx">#{{ i + 1 }}</span>
                <span class="ws-topic-title">{{ tp.title }}</span>
              </div>
              <div v-if="tp.desc" class="ws-topic-desc" :class="{ collapsed: !expandedTopic(i) && needCollapseTopic(tp) }">{{ tp.desc }}</div>
              <button v-if="needCollapseTopic(tp)" class="wst-more" @click="toggleTopic(i)">{{ expandedTopics.has(i) ? t('收起') : t('查看详情') }}</button>
            </div>
          </div>
        </template>
      </template>
      <template v-else>
        <div class="ws-requirement-card">
          <h4>✏️ {{ t('发布毕业设计要求') }}
            <span v-if="ws.latestRequirement" style="font-size: 12px;color:var(--text-light);font-weight:400;margin-left:8px">{{ t('上次发布：') }}{{ formatDateTime(ws.latestRequirement.createdAt) }}</span>
          </h4>
          <div class="form-group"><label>{{ t('要求正文') }}</label>
            <textarea v-model="reqContent" class="form-input" rows="6" :placeholder="t('例如：选题范围、字数要求、格式规范、关键时间节点...')"></textarea></div>
          <div class="form-group"><label>{{ t('截止日期（可选）') }}</label>
            <input v-model="reqDeadline" class="form-input" type="datetime-local"></div>
          <div style="margin-top:10px"><strong style="font-size:13px">{{ t('推荐题目（可选，每行一条，格式：标题|描述）') }}</strong></div>
          <textarea v-model="reqTopics" class="form-input" rows="4" :placeholder="t('题目一|可选描述\n题目二|可选描述')"></textarea>
          <div style="margin-top:14px;display:flex;gap:10px">
            <button class="btn btn-primary" :disabled="submitting" @click="submitReq">
              {{ submitting ? t('提交中...') : (ws.latestRequirement ? t('更新要求') : t('发布要求')) }}
            </button>
          </div>
        </div>
      </template>
    </div>

    <!-- ===== Tab 2: 论文 ===== -->
    <div v-if="activeTab === 'paper'">
      <template v-if="role === 'student'">
        <div class="ws-requirement-card">
          <h4>📤 {{ t('上传新版本论文') }}
            <span v-if="deadlineInfo" class="ws-cc-deadline" :class="deadlineInfo.cls">{{ deadlineInfo.text }}</span>
          </h4>
          <div class="ws-dropzone" :class="{ uploading }" @click="!uploading && triggerFile()" @dragover.prevent @drop.prevent="onDrop">
            <template v-if="uploading">
              <div style="font-size:32px">⏳</div>
              <div style="font-weight:600;margin-top:6px">{{ t('正在上传 {n}%', { n: uploadPct }) }}</div>
              <div class="ws-up-progress"><i :style="{ width: uploadPct + '%' }" /></div>
              <div class="ws-hint">{{ t('大文件上传请不要关闭页面') }}</div>
            </template>
            <template v-else>
              <div style="font-size:32px">📄</div>
              <div style="font-weight:500;margin-top:6px">{{ t('点击或拖拽文件到此处上传') }}</div>
              <div class="ws-hint">{{ t('支持 PDF / DOC / DOCX / TXT / ZIP / RAR，最大 100MB · 每次上传自动成为新版本') }}</div>
            </template>
            <input ref="fileInput" type="file" class="ws-file-input" accept=".pdf,.doc,.docx,.txt,.zip,.rar" @change="onFileChange">
          </div>
          <div class="form-group" style="margin-top:12px"><label>{{ t('论文标题（可选）') }}</label>
            <input v-model="paperTitle" class="form-input" :placeholder="t('留空则使用文件名')"></div>
          <div class="form-group"><label>{{ t('备注（可选）') }}</label>
            <textarea v-model="paperNote" class="form-input" rows="2" :placeholder="t('给老师看的说明，比如：这版主要改了XX...')"></textarea></div>
        </div>
      </template>
      <template v-else>
        <!-- 教师端：文件收集状态 -->
        <div class="ws-collect-card" v-if="stuPapers.length">
          <div class="ws-cc-head">
            <b>📥 {{ t('学生提交状态') }}</b>
            <span v-if="deadlineInfo" class="ws-cc-deadline" :class="deadlineInfo.cls">{{ deadlineInfo.text }}</span>
          </div>
          <div class="ws-cc-grid">
            <div class="ws-cc-item"><b>v{{ stuPapers.length }}</b><span>{{ t('累计版本') }}</span></div>
            <div class="ws-cc-item"><b>{{ humanSize(latestStuPaper?.fileSizeBytes) }}</b><span>{{ t('最新文件') }}</span></div>
            <div class="ws-cc-item"><b>{{ paperStatusLabel(latestStuPaper?.reviewStatus) }}</b><span>{{ t('稿件状态') }}</span></div>
            <div class="ws-cc-item"><b>{{ formatDate(latestStuPaper?.createdAt) || '—' }}</b><span>{{ t('最近提交') }}</span></div>
          </div>
        </div>
        <div class="ws-collect-card none" v-else>
          <b>📥 {{ t('学生提交状态') }}</b><span>{{ t('学生还没有提交过论文') }}</span>
        </div>
        <div class="ws-requirement-card">
          <h4>📎 {{ t('教师上传批注/参考材料（可选）') }}</h4>
          <div v-if="uploading" class="ws-up-progress big"><i :style="{ width: uploadPct + '%' }" /><em>{{ t('上传中 {n}%', { n: uploadPct }) }}</em></div>
          <input v-else type="file" accept=".pdf,.doc,.docx,.txt,.zip,.rar" style="margin-top:8px" @change="onTeacherFile">
        </div>
      </template>
      <!-- 版本轨迹 -->
      <div v-if="papers.length" class="ws-ver-line-wrap">
        <span class="ws-ver-label">{{ t('版本轨迹') }}</span>
        <div class="ws-ver-line">
          <div
            v-for="p in versionChips" :key="p.id"
            class="ws-ver-chip"
            :class="{ mine: p.uploadedBy !== 'teacher' }"
            :title="`${p.title || p.fileName} · ${formatDateTime(p.createdAt)}${p.uploadedBy === 'teacher' ? ' · ' + t('教师上传') : ''}`"
          >
            <b>v{{ p.versionNo }}</b>
            <i v-if="p.uploadedBy !== 'teacher'" class="ws-ver-dot" :class="p.reviewStatus || 'pending'" />
          </div>
        </div>
      </div>
      <div v-if="papers.length">
        <div v-for="p in papers" :key="p.id" class="ws-paper-row" :class="{ 'teacher-upload': p.uploadedBy === 'teacher' }">
          <div class="ws-paper-ver">v{{ p.versionNo }}</div>
          <div class="ws-paper-info">
            <div class="ws-paper-title">{{ p.fileName }} <span v-if="p.uploadedBy === 'teacher'" style="font-size: 12px;color:#d97706">[{{ t('教师上传') }}]</span></div>
            <div class="ws-paper-meta">
              <template v-if="p.title">📝 {{ p.title }}  ·  </template>
              {{ humanSize(p.fileSizeBytes) }} · {{ t('上传于') }} {{ formatDateTime(p.createdAt) }}{{ p.uploadedBy === 'teacher' ? '' : ' · ' + t('来自学生') }}
            </div>
            <div v-if="p.note" class="ws-paper-note">"{{ p.note }}"</div>
            <div v-if="p.teacherComment" style="font-size:12px;color:#0ea5e9;margin-top:4px">👤 {{ t('教师批注：') }}{{ p.teacherComment }}</div>
            <div v-if="p.grade" style="font-size:12px;color:#16a34a;margin-top:2px">⭐ {{ t('评分：') }}{{ p.grade }}/100</div>
            <template v-if="role === 'teacher' && p.uploadedBy !== 'teacher'">
              <button class="btn btn-outline btn-sm" style="margin-top:8px" @click="toggleReview(p)">{{ t('批阅') }}</button>
              <div v-if="showReviewId === p.id" style="margin-top:10px;padding:12px;background:var(--bg);border-radius:8px;border-left:3px solid var(--primary)">
                <div class="form-group"><label>{{ t('教师评语') }}</label>
                  <textarea v-model="reviewComment" class="form-input" rows="2" :placeholder="t('批注、修改建议...')"></textarea></div>
                <div style="display:flex;gap:8px;align-items:center">
                  <select v-model="reviewStatus" class="form-input" style="width:auto">
                    <option value="pending">{{ t('待批阅') }}</option><option value="revised">{{ t('需修改') }}</option><option value="approved">{{ t('已通过') }}</option>
                  </select>
                  <input v-model.number="reviewGrade" class="form-input" style="width:80px" type="number" min="0" max="100" :placeholder="t('评分')">
                  <button class="btn btn-primary btn-sm" :disabled="submitting" @click="submitReview(p)">{{ t('保存批阅') }}</button>
                  <button class="btn btn-outline btn-sm" @click="showReviewId = null">{{ t('取消') }}</button>
                </div>
              </div>
            </template>
          </div>
          <div style="text-align:right">
            <span v-if="p.uploadedBy !== 'teacher'" class="ws-paper-grade" :class="p.reviewStatus || 'pending'">{{ paperStatusLabel(p.reviewStatus) }}</span>
            <div style="margin-top:8px"><button class="btn btn-outline btn-sm" @click="downloadPaper(p)">⬇️ {{ t('下载') }}</button></div>
          </div>
        </div>
      </div>
      <div v-else class="empty-card" style="margin-top:14px">{{ t('暂无论文版本') }}</div>
    </div>

    <!-- ===== Tab 3: 公告 ===== -->
    <div v-if="activeTab === 'anno'">
      <div class="ws-anno-page">
        <div class="ws-anno-toolbar">
          <div class="ws-anno-title">📢 {{ t('导师公告') }}</div>
          <button v-if="role === 'teacher'" class="ws-chat-float-btn" @click="openAnnoModal">➕ {{ t('发布公告') }}</button>
          <span v-else class="ws-anno-tip">{{ t('查看你导师发布的全部通知') }}</span>
        </div>
        <div v-if="announcements.length">
          <div v-for="a in announcements" :key="a.id" class="ws-anno-card">
            <div class="ws-anno-badge">{{ t('公告') }}</div>
            <div class="ws-anno-title-line"><div class="ws-anno-title-text">{{ a.title }}</div></div>
            <div v-if="a.content" class="ws-anno-content">{{ a.content }}</div>
            <div v-if="a.fileName" class="ws-anno-file">📎 <a :href="annoFileUrl(a.id)" target="_blank">{{ a.fileName }}</a>
              <a :href="annoFileUrl(a.id)" target="_blank" class="ws-anno-download">⬇ {{ t('下载') }}</a>
            </div>
            <div class="ws-anno-meta">👤 {{ a.teacherName }} · 🕐 {{ formatDateTime(a.createdAt) }}</div>
          </div>
        </div>
        <div v-else class="empty-card">📭 {{ t('暂无公告') }}</div>
      </div>
    </div>

    <!-- ===== Tab 4: 真·学生-老师私聊（原版 renderWsChatTab）===== -->
    <!-- ===== Tab 4: 真·私聊（样式 = AI Chat 同款）===== -->
    <div v-if="activeTab === 'chat'">
      <div class="chat-page chat-page-inline">
        <div class="chat-header">
          <div class="chat-title">
            <div class="chat-title-icon">💬</div>
            <div class="chat-title-text">{{ t('私信对话') }} · {{ partner.name }}</div>
            <span class="chat-status">{{ t('在线') }}</span>
          </div>
          <div class="chat-header-right">
            <span class="chat-hint-sm">{{ t('仅限双方可见') }}</span>
          </div>
        </div>
        <div class="chat-messages" ref="chatBoxRef">
          <div v-if="!chatMsgs.length" class="chat-welcome">
            <div class="chat-welcome-icon">💬</div>
            <div class="chat-welcome-title">{{ t('还没开始对话') }}</div>
            <div class="chat-welcome-sub">{{ t('发条消息打个招呼吧 ~') }}</div>
          </div>
          <template v-else>
            <template v-for="(group, gi) in chatGroups" :key="gi">
              <div v-if="group.dayLabel" class="chat-day-sep">{{ group.dayLabel }}</div>
              <div v-for="m in group.msgs" :key="m.id" class="chat-msg" :class="String(m.senderId) === String(myId) ? 'user' : ''">
                <div class="chat-avatar">{{ (m.senderName || '').charAt(0) }}</div>
                <div class="chat-bubble-wrap">
                  <div class="chat-sender">{{ m.senderName || (m.senderRole === 'student' ? t('同学') : t('老师')) }}</div>
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
                      :placeholder="t('输入消息，Enter 发送，Shift+Enter 换行...')"
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
            <span class="chat-status">{{ t('在线') }}</span>
          </div>
          <button class="chat-clear-btn" @click="showQuickChat = false">{{ t('关闭') }}</button>
        </div>
        <div class="chat-messages ws-chat-modal-body" ref="modalBoxRef">
          <div v-if="!modalMsgs.length" class="chat-welcome">
            <div class="chat-welcome-icon">💬</div>
            <div class="chat-welcome-title">{{ t('私信对话') }} · {{ role === 'student' ? t('老师') : t('学生') }} {{ partner.name }}</div>
            <div class="chat-welcome-sub">{{ t('发条消息打个招呼吧 ~') }}</div>
          </div>
          <template v-else>
            <template v-for="(group, gi) in modalGroups" :key="gi">
              <div v-if="group.dayLabel" class="chat-day-sep">{{ group.dayLabel }}</div>
              <div v-for="m in group.msgs" :key="m.id" class="chat-msg" :class="String(m.senderId) === String(myId) ? 'user' : ''">
                <div class="chat-avatar">{{ (m.senderName || '').charAt(0) }}</div>
                <div class="chat-bubble-wrap">
                  <div class="chat-sender">{{ m.senderName || (m.senderRole === 'student' ? t('同学') : t('老师')) }}</div>
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
                      :placeholder="t('输入消息，Enter 发送')"
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
              <div class="ws-chat-header-name">{{ t('发布公告') }}</div>
              <div class="ws-chat-header-status">{{ t('你的所有学生都能收到') }}</div>
            </div>
          </div>
          <button class="ws-chat-modal-close" @click="showAnnoModal = false">✕</button>
        </div>
        <div style="padding:20px 22px;display:flex;flex-direction:column;gap:14px">
          <div>
            <label style="font-size:12px;color:var(--text-light);margin-bottom:6px;display:block;font-weight:500">{{ t('标题') }}<span style="color:#ff6b8a">*</span></label>
            <input v-model="annoTitle" maxlength="120" :placeholder="t('如：学校关于毕设格式的新要求')"
                   style="width:100%;padding:10px 14px;border:1.5px solid var(--border);border-radius:10px;font-size:14px;outline:none;font-family:inherit">
          </div>
          <div>
            <label style="font-size:12px;color:var(--text-light);margin-bottom:6px;display:block;font-weight:500">{{ t('内容（可选）') }}</label>
            <textarea v-model="annoContent" rows="5" :placeholder="t('写点什么给同学们...')"
                      style="width:100%;padding:10px 14px;border:1.5px solid var(--border);border-radius:10px;font-size:14px;outline:none;font-family:inherit;resize:vertical;line-height:1.55"></textarea>
          </div>
          <div style="display:flex;justify-content:flex-end;gap:10px;margin-top:4px">
            <button class="btn" style="background:var(--border);color:var(--text);border:none;border-radius:10px;padding:9px 18px;cursor:pointer" @click="showAnnoModal = false">{{ t('取消') }}</button>
            <button class="ws-chat-send-btn" :disabled="submitting" @click="submitAnno">{{ submitting ? t('发布中...') : t('发布公告') }}</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import http from '@/api'
import { t } from '@/i18n'

const props = defineProps<{ ws: any; role: 'student' | 'teacher'; advisorId: number; startTab?: 'flow' | 'req' | 'paper' | 'anno' | 'chat' | null }>()
const emit = defineEmits<{ (e: 'refresh'): void }>()
const auth = useAuthStore()

const partner = computed(() => props.role === 'student' ? props.ws.teacher : props.ws.student)

const activeTab = ref<'flow' | 'req' | 'paper' | 'anno' | 'chat'>('flow')

// 待办跳转：Workspace.vue 通过 startTab 传入目标标签
watch(() => props.startTab, (t) => {
  if (!t) return
  if (t === 'chat') switchChatTab()
  else activeTab.value = t
}, { immediate: true })
const submitting = ref(false)

// ===== 流程里程碑：开题 → 中期 → 论文终稿 → 答辩 =====
const milestones = ref<any[]>([])
const flowSubmitting = ref(false)
const form = ref<any>({ title: '', content: '', file: null })
const reviewForm = ref<any>({ comment: '', advisorScore: null, defenseScore: null })

const flowPendingReview = computed(() =>
  props.role === 'teacher' ? milestones.value.filter((m: any) => m.canReview).length : 0)
const defenseScore = computed(() => {
  const d = milestones.value.find((m: any) => m.stage === 'defense')
  return d && d.totalScore != null ? d.totalScore : null
})
const validScores = computed(() => {
  const s = reviewForm.value
  return Number.isFinite(s.advisorScore) && Number.isFinite(s.defenseScore)
    && s.advisorScore >= 0 && s.advisorScore <= 100 && s.defenseScore >= 0 && s.defenseScore <= 100
})
const computedTotal = computed(() =>
  Math.round(reviewForm.value.advisorScore * 0.4 + reviewForm.value.defenseScore * 0.6))

async function loadMilestones() {
  try {
    const r: any = await http.get('/workspace/milestones', { params: { advisorId: props.advisorId } })
    milestones.value = r?.data || []
  } catch { milestones.value = [] }
}

function statusIcon(s: string) {
  return ({ none: '⬜', submitted: '⏳', passed: '✅', returned: '↩️' } as Record<string, string>)[s] || '⬜'
}
function statusIconSvg(s: string) {
  return ({ none: '⬜', submitted: '⏳', passed: '✅', returned: '↩️' } as Record<string, string>)[s] || '⬜'
}
function flowStatusText(m: any) {
  const map: Record<string, string> = { none: t('未提交'), submitted: t('待审核'), passed: t('已通过'), returned: t('已退回') }
  return map[m.status] || m.status
}
function fmtSize(n: any) {
  if (!n) return ''
  const v = Number(n)
  return v > 1024 * 1024 ? (v / 1024 / 1024).toFixed(1) + ' MB' : Math.round(v / 1024) + ' KB'
}
function fmtTime(v: any) {
  if (!v) return ''
  try { return new Date(Number(v)).toLocaleString('zh-CN', { hour12: false, month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) } catch { return '' }
}

async function submitMilestone(m: any) {
  if (flowSubmitting.value) return
  flowSubmitting.value = true
  try {
    const fd = new FormData()
    if (form.value.title) fd.append('title', form.value.title)
    if (form.value.content) fd.append('content', form.value.content)
    if (form.value.file) fd.append('file', form.value.file)
    await http.post(`/workspace/milestones/${m.id}/submit`, fd)
    ElMessage.success(t('已提交，等待导师审核'))
    form.value = { title: '', content: '', file: null }
    await loadMilestones()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || t('提交失败'))
  }
  flowSubmitting.value = false
}

async function reviewMilestone(m: any, decision: string) {
  if (flowSubmitting.value) return
  if (decision === 'passed' && m.stage === 'defense' && !validScores.value) {
    ElMessage.warning(t('请先填写 0-100 的导师评分与答辩组评分'))
    return
  }
  flowSubmitting.value = true
  try {
    const body: any = { decision, comment: reviewForm.value.comment }
    if (m.stage === 'defense' && decision === 'passed') {
      body.advisorScore = reviewForm.value.advisorScore
      body.defenseScore = reviewForm.value.defenseScore
    }
    await http.post(`/workspace/milestones/${m.id}/review`, body)
    ElMessage.success(decision === 'passed' ? t('已通过') : t('已退回'))
    reviewForm.value = { comment: '', advisorScore: null, defenseScore: null }
    await loadMilestones()
    emit('refresh')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || t('操作失败'))
  }
  flowSubmitting.value = false
}

async function downloadMilestone(m: any) {
  try {
    const r: any = await http.get(`/workspace/milestones/${m.id}/download`, { responseType: 'blob' })
    const url = window.URL.createObjectURL(new Blob([r.data]))
    const a = document.createElement('a')
    a.href = url
    a.download = m.fileName || 'download'
    a.click()
    window.URL.revokeObjectURL(url)
  } catch { ElMessage.error(t('下载失败')) }
}

// ===== 学生确认课题（推荐题目选其一，或自定义）=====
const topicSubmitting = ref(false)
const customTopic = ref('')
async function confirmTopic(title: string) {
  const name = (title || '').trim()
  if (!name || topicSubmitting.value) return
  try {
    await ElMessageBox.confirm(t('确定将「{n}」作为毕业设计课题？确认后导师可见，如需更改请先与导师私聊沟通。', { n: name }), t('确认课题'), {
      confirmButtonText: t('确定'), cancelButtonText: t('取消'), type: 'warning',
    })
  } catch { return }
  topicSubmitting.value = true
  try {
    const r: any = await http.post('/workspace/topic', { advisorId: props.advisorId, title: name })
    ElMessage.success(r?.data?.msg || t('课题已确认，导师已可见'))
    customTopic.value = ''
    emit('refresh')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || t('操作失败'))
  }
  topicSubmitting.value = false
}

// ===== 上传进度 =====
const uploading = ref(false)
const uploadPct = ref(0)

// ===== 论文收集状态（教师视角） =====
const stuPapers = computed(() => (props.ws?.papers || []).filter((p: any) => p.uploadedBy !== 'teacher'))
const latestStuPaper = computed(() => stuPapers.value[0] || null)
function humanSize(bytes: any) {
  const n = Number(bytes) || 0
  if (n >= 1024 * 1024) return (n / 1024 / 1024).toFixed(1) + ' MB'
  return (n / 1024).toFixed(1) + ' KB'
}
/** 要求截止倒计时 */
const deadlineInfo = computed(() => {
  const d = props.ws?.latestRequirement?.deadline
  if (!d) return null
  const end = new Date(d).getTime()
  const now = Date.now()
  const days = Math.ceil((end - now) / 86400000)
  const latest = latestStuPaper.value
  const submittedAfter = latest && latest.createdAt && new Date(latest.createdAt).getTime() > end
  if (now > end) return { text: submittedAfter ? t('已截止 · 截止前有提交') : t('已截止 · 未收到提交'), cls: submittedAfter ? 'ok' : 'bad' }
  if (days <= 3) return { text: t('距截止仅 {n} 天', { n: days }), cls: 'bad' }
  return { text: t('距截止 {n} 天', { n: days }), cls: 'ok' }
})

// ===== 推荐题目 =====
const latestTopics = computed(() => props.ws?.latestRequirement?.topics || [])
const papers = computed(() => props.ws?.papers || [])

/* ===== 要求截止状态（真实时间计算，不硬编码） ===== */
const reqDeadlineTag = computed(() => {
  const d = props.ws?.latestRequirement?.deadline
  if (!d) return null
  const end = new Date(d).getTime()
  const now = Date.now()
  if (now > end) {
    const days = Math.floor((now - end) / 86400000)
    return { text: days >= 1 ? t('已截止 {n} 天', { n: days }) : t('已截止'), short: t('已截止'), cls: 'bad' }
  }
  const days = Math.ceil((end - now) / 86400000)
  if (days <= 3) return { text: t('距截止仅 {n} 天', { n: days }), short: t('{n}天', { n: days }), cls: 'warn' }
  return { text: t('距截止 {n} 天', { n: days }), short: '', cls: 'ok' }
})

/* ===== 要求正文折叠 ===== */
const reqExpanded = ref(false)
const reqNeedCollapse = computed(() => ((props.ws?.latestRequirement?.content || '') as string).length > 220)

/* ===== 推荐题目描述折叠 ===== */
const expandedTopics = ref<Set<number>>(new Set())
const expandedTopic = (i: number) => expandedTopics.value.has(i)
const needCollapseTopic = (t: any) => ((t?.desc || '') as string).length > 80
function toggleTopic(i: number) {
  const s = new Set(expandedTopics.value)
  if (s.has(i)) s.delete(i); else s.add(i)
  expandedTopics.value = s
}

/* ===== 论文 Tab 徽标颜色：按最新一篇学生稿状态 ===== */
const paperBadgeCls = computed(() => {
  const latest = stuPapers.value[0]
  if (!latest) return 'approved'
  return latest.reviewStatus === 'approved' ? 'approved' : latest.reviewStatus === 'revised' ? 'revised' : 'pending'
})
/** 版本轨迹：从 v1 到最新，最新的在最右 */
const versionChips = computed(() => [...papers.value].slice().reverse())
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
      const label = dayStr === today ? t('今天') : dayStr === yesterday ? t('昨天') : dayStr
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
    alert(t('发送失败：') + (e?.message || ''))
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
    alert(t('发送失败：') + (e?.message || ''))
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
  const m = ({ pending: '待批阅', revised: '需修改', approved: '已通过' } as Record<string, string>)[s]
  return m ? t(m) : (s || t('待批阅'))
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
  if (t === 'flow' && milestones.value.length === 0) loadMilestones()
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
    // 后端 LocalDateTime.parse 需要本地时间格式（不带时区后缀）
    const deadline = reqDeadline.value ? (reqDeadline.value.length === 16 ? reqDeadline.value + ':00' : reqDeadline.value) : null
    const body: any = { advisorId: props.advisorId, content: reqContent.value, topics, deadline }
    await http.post('/workspace/requirements', body)
    ElMessage.success(t('要求已发布/更新'))
    emit('refresh')
  } catch (e: any) { ElMessage.error(e?.message || t('发布失败')) } finally { submitting.value = false }
}

function triggerFile() { fileInput.value?.click() }
function onFileChange(e: Event) { const f = (e.target as HTMLInputElement).files?.[0]; if (f) uploadPaper(f); (e.target as HTMLInputElement).value = '' }
function onDrop(e: DragEvent) { const f = e.dataTransfer?.files?.[0]; if (f) uploadPaper(f) }
async function uploadPaper(file: File) {
  if (file.size > 100 * 1024 * 1024) { ElMessage.warning(t('文件不能超过 100MB')); return }
  uploading.value = true
  uploadPct.value = 0
  try {
    const fd = new FormData()
    fd.append('advisorId', String(props.advisorId))
    fd.append('file', file)
    fd.append('title', paperTitle.value)
    fd.append('note', paperNote.value)
    await http.post('/workspace/papers/upload', fd, {
      onUploadProgress: (e: any) => { if (e.total) uploadPct.value = Math.round((e.loaded / e.total) * 100) },
    })
    ElMessage.success(t('论文上传成功，已生成新版本'))
    paperTitle.value = ''
    paperNote.value = ''
    emit('refresh')
  } catch (e: any) { ElMessage.error(e?.message || t('上传失败')) } finally { uploading.value = false }
}
async function onTeacherFile(e: Event) {
  const f = (e.target as HTMLInputElement).files?.[0]; if (!f) return
  if (f.size > 100 * 1024 * 1024) { ElMessage.warning(t('文件不能超过 100MB')); return }
  uploading.value = true
  uploadPct.value = 0
  try {
    const fd = new FormData()
    fd.append('advisorId', String(props.advisorId))
    fd.append('file', f)
    await http.post('/workspace/papers/upload', fd, {
      onUploadProgress: (e: any) => { if (e.total) uploadPct.value = Math.round((e.loaded / e.total) * 100) },
    })
    ElMessage.success(t('材料上传成功'))
    emit('refresh')
  } catch (e: any) { ElMessage.error(e?.message || t('上传失败')) } finally { uploading.value = false }
}
function downloadPaper(p: any) {
  const base = (http as any).defaults?.baseURL || ''
  const token = localStorage.getItem('zhixue_token')
  fetch(base + '/workspace/papers/' + p.id + '/download', { headers: token ? { Authorization: 'Bearer ' + token } : {} })
    .then(async res => { if (!res.ok) throw new Error(t('下载失败')); const blob = await res.blob(); const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = p.fileName; a.click(); URL.revokeObjectURL(url) })
    .catch(e => ElMessage.error(e.message))
}
function toggleReview(p: any) {
  if (showReviewId.value === p.id) { showReviewId.value = null; return }
  showReviewId.value = p.id; reviewComment.value = p.teacherComment || ''; reviewStatus.value = p.reviewStatus || 'pending'; reviewGrade.value = p.grade ?? null
}
async function submitReview(p: any) {
  submitting.value = true
  try {
    await http.post('/workspace/papers/' + p.id + '/review', { comment: reviewComment.value, status: reviewStatus.value, grade: reviewGrade.value })
    ElMessage.success(t('批阅已保存'))
    showReviewId.value = null
    emit('refresh')
  } catch (e: any) { ElMessage.error(e?.message || t('保存失败')) } finally { submitting.value = false }
}
function openAnnoModal() { annoTitle.value = ''; annoContent.value = ''; showAnnoModal.value = true }
async function submitAnno() {
  if (!annoTitle.value.trim()) { ElMessage.warning(t('标题不能为空')); return }
  submitting.value = true
  try { await http.post('/workspace/announce', { title: annoTitle.value, content: annoContent.value }); showAnnoModal.value = false; ElMessage.success(t('公告已发布')); loadAnnouncements() }
  catch (e: any) { ElMessage.error(e?.message || t('发布失败')) } finally { submitting.value = false }
}

onMounted(() => {
  auth.loadFromStorage()
  // 流程里程碑：挂载即加载（首个 Tab）
  loadMilestones()
  // 公告数量徽标：挂载即加载
  loadAnnouncements()
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
.form-input { font-size: 14px; }
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
  background: linear-gradient(135deg, rgba(43,177,135,0.06), rgba(56,189,248,0.06));
}
.chat-title { display: flex; align-items: center; gap: 10px; font-size: 15px; font-weight: 600; }
.chat-title-icon { font-size: 18px; }
.chat-title-text { color: var(--text); }
.chat-status { font-size: 12px; padding: 2px 8px; border-radius: 10px; background: rgba(16,185,129,0.12); color: #10b981; font-weight: 500; display: flex; align-items: center; gap: 4px; }
.chat-status::before { content: ''; width: 5px; height: 5px; border-radius: 50%; background: #10b981; animation: pulse 2s infinite; }
@keyframes pulse { 0%,100%{opacity:1} 50%{opacity:.4} }
.chat-header-right { display: flex; gap: 8px; align-items: center; }
.chat-hint-sm { font-size: 12px; color: var(--text-light); }

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
.chat-msg.user .chat-avatar { background: linear-gradient(135deg, #2bb187, #38bdf8); }

.chat-bubble-wrap { max-width: 72%; display: flex; flex-direction: column; gap: 4px; }
.chat-msg.user .chat-bubble-wrap { align-items: flex-end; }

.chat-sender { font-size: 12px; color: var(--text-lighter); padding: 0 4px; }

.chat-bubble {
  padding: 12px 16px; border-radius: 14px;
  font-size: 14px; line-height: 1.65;
  background: var(--bg); border: 1px solid var(--border);
  color: var(--text); word-break: break-word; white-space: pre-wrap;
  border-top-left-radius: 4px;
}
.chat-bubble.user {
  background: linear-gradient(135deg, #2bb187, #38bdf8);
  border-color: transparent; color: #fff;
  border-top-left-radius: 14px; border-top-right-radius: 4px;
}
.chat-time { font-size: 12px; color: var(--text-lighter); padding: 0 4px; }

/* 日期分割线 */
.chat-day-sep { text-align: center; margin: 18px 0 14px; font-size: 12px; color: var(--text-lighter); display: flex; align-items: center; gap: 12px; }
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
.chat-input-wrap:focus-within { border-color: var(--primary); box-shadow: 0 0 0 3px rgba(43,177,135,0.12); }
.chat-input-wrap textarea {
  width: 100%; border: none; outline: none; resize: none;
  font-size: 14px; background: transparent; color: var(--text);
  font-family: inherit; line-height: 1.55; max-height: 140px;
}
.chat-send {
  width: 44px; height: 44px; border-radius: 12px; border: none; cursor: pointer;
  background: linear-gradient(135deg, #2bb187, #38bdf8); color: #fff;
  display: flex; align-items: center; justify-content: center;
  transition: transform .15s, box-shadow .2s, opacity .2s; flex-shrink: 0;
}
.chat-send:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 4px 14px rgba(43,177,135,.35); }
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
.ws-anno-badge { position: absolute; top: 12px; right: 14px; font-size: 12px; padding: 2px 10px; border-radius: 10px; background: rgba(43,177,135,.12); color: var(--primary); }
.ws-anno-title-line { padding-right: 60px; }
.ws-anno-title-text { font-size: 15px; font-weight: 600; }
.ws-anno-content { margin-top: 10px; font-size: 13px; line-height: 1.65; color: var(--text); white-space: pre-wrap; }
.ws-anno-file { margin-top: 10px; font-size: 12px; color: var(--primary); }
.ws-anno-download { margin-left: 10px; font-size: 12px; color: var(--text-light); text-decoration: none; }
.ws-anno-meta { margin-top: 10px; font-size: 12px; color: var(--text-lighter); }

/* ===== 论文收集状态卡 ===== */
.ws-collect-card {
  padding: 14px 18px; border: 1px solid rgba(43,177,135,.35); border-radius: 12px;
  background: color-mix(in srgb, var(--primary) 6%, var(--card));
  margin-bottom: 14px;
}
.ws-collect-card.none { border-style: dashed; border-color: var(--border); background: transparent; display: flex; gap: 10px; align-items: center; color: var(--text-light); font-size: 13px; }
.ws-cc-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-bottom: 11px; }
.ws-cc-head b { font-size: 14px; color: var(--text); }
.ws-cc-deadline { font-size: 12px; font-weight: 800; padding: 2px 10px; border-radius: 999px; }
.ws-cc-deadline.ok { color: #059669; background: rgba(16,185,129,.13); }
.ws-cc-deadline.bad { color: #e11d48; background: rgba(244,63,94,.13); }
.ws-cc-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 9px; }
@media (max-width: 760px) { .ws-cc-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
.ws-cc-item { padding: 9px 11px; border: 1px solid var(--border); border-radius: 10px; display: flex; flex-direction: column; gap: 2px; }
.ws-cc-item b { font-size: 15px; color: var(--text); font-variant-numeric: tabular-nums; }
.ws-cc-item span { font-size: 12px; color: var(--text-light); }

/* 上传进度 */
.ws-dropzone.uploading { pointer-events: none; border-color: var(--primary); background: color-mix(in srgb, var(--primary) 7%, transparent); }
.ws-up-progress { width: min(320px, 80%); height: 9px; border-radius: 99px; background: rgba(148,163,184,.16); overflow: hidden; margin: 12px auto 0; position: relative; }
.ws-up-progress i { display: block; height: 100%; border-radius: 99px; background: linear-gradient(90deg, #2bb187, #38bdf8); transition: width .25s ease; }
.ws-up-progress.big { width: 100%; margin: 10px 0 0; display: flex; align-items: center; gap: 10px; }
.ws-up-progress.big em { font-style: normal; font-size: 12px; color: var(--text-light); white-space: nowrap; }

/* 版本轨迹 */
.ws-ver-line-wrap { display: flex; align-items: center; gap: 12px; padding: 10px 14px; border: 1px solid var(--border); border-radius: 11px; background: color-mix(in srgb, var(--card-bg) 50%, transparent); margin-bottom: 12px; overflow-x: auto; }
.ws-ver-label { font-size: 12px; font-weight: 700; color: var(--text-light); flex-shrink: 0; }
.ws-ver-line { display: flex; align-items: center; gap: 7px; }
.ws-ver-chip {
  display: inline-flex; align-items: center; gap: 5px; flex-shrink: 0;
  padding: 3px 9px; border-radius: 999px; border: 1px solid var(--border);
  font-size: 12px; color: var(--text-light); background: transparent;
}
.ws-ver-chip.mine { border-color: rgba(43,177,135,.45); color: var(--text); background: rgba(43,177,135,.07); }
.ws-ver-chip b { font-weight: 800; font-variant-numeric: tabular-nums; }
.ws-ver-dot { width: 7px; height: 7px; border-radius: 50%; display: inline-block; }
.ws-ver-dot.pending { background: #f59e0b; }
.ws-ver-dot.revised { background: #3b82f6; }
.ws-ver-dot.approved { background: #10b981; }

/* ===== header 已确认徽标 ===== */
.ws-confirmed-badge {
  display: inline-flex; align-items: center; gap: 4px; vertical-align: 2px;
  margin-left: 10px; font-size: 11.5px; font-weight: 700; padding: 2px 9px; border-radius: 999px;
  color: #059669; background: rgba(16, 185, 129, .12); border: 1px solid rgba(16, 185, 129, .3);
}
.wc-check { width: 10px; height: 10px; }
.ws-topic-line b { color: var(--primary); font-weight: 600; }

/* ===== tabs 徽标扩展 ===== */
.ws-badge.pending { color: #d97706; background: rgba(245, 158, 11, .14); }
.ws-badge.revised { color: #2563eb; background: rgba(59, 130, 246, .14); }
.ws-badge.bad { color: #be123c; background: rgba(244, 63, 94, .13); }
.ws-badge.warn { color: #b45309; background: rgba(245, 158, 11, .14); }
.ws-badge.ok { color: #059669; background: rgba(16, 185, 129, .13); }
.ws-tab-dot {
  font-size: 11px; font-weight: 800; padding: 0 7px; border-radius: 999px; margin-left: 4px;
}
.ws-tab-dot.ok { color: #059669; background: rgba(16, 185, 129, .13); }
.ws-tab-dot.warn { color: #b45309; background: rgba(245, 158, 11, .16); }
.ws-tab-dot.bad { color: #be123c; background: rgba(244, 63, 94, .13); }

/* ===== 当前课题卡 ===== */
.ws-current-topic { border-left: 3px solid var(--primary); }
.wct-title { font-size: 16px; font-weight: 700; color: var(--primary); line-height: 1.5; word-break: break-all; }
.wct-sub { font-size: 12.5px; color: var(--text-light); margin-top: 6px; }
.wct-chat-btn { margin-top: 12px; }

/* ===== 要求正文折叠与元信息 ===== */
.ws-req-content { white-space: pre-wrap; font-size: 14px; line-height: 1.75; color: var(--text); word-break: break-word; }
.ws-req-content.collapsed {
  max-height: 200px; overflow: hidden; position: relative;
}
.ws-req-content.collapsed::after {
  content: ''; position: absolute; left: 0; right: 0; bottom: 0; height: 64px;
  background: linear-gradient(transparent, var(--card));
}
.ws-req-toggle {
  display: inline-block; margin-top: 8px; border: none; background: transparent;
  color: var(--primary); font-size: 12.5px; font-weight: 600; cursor: pointer; padding: 2px 0;
  font-family: inherit;
}
.ws-req-toggle:hover { text-decoration: underline; }
.ws-req-meta { margin-top: 12px; font-size: 12px; color: var(--text-light); border-top: 1px dashed var(--border); padding-top: 10px; }

/* ===== 推荐题目 ===== */
.wst-head { display: flex; align-items: baseline; gap: 8px; }
.wst-idx { color: var(--text-lighter); font-size: 12px; font-weight: 700; flex-shrink: 0; }
.ws-topic-desc { color: var(--text-light); font-size: 12.5px; margin-top: 4px; line-height: 1.6; }
/* 学生确认课题：选定按钮 + 自定义输入 */
.wst-head { display: flex; align-items: center; gap: 8px; }
.wst-pick {
  margin-left: auto; flex-shrink: 0;
  border: 1px solid rgba(31,166,122,.45); background: var(--primary-bg); color: var(--primary);
  font-size: 12px; font-weight: 600; padding: 4px 12px; border-radius: 999px; cursor: pointer;
  transition: background .2s ease-out, color .2s ease-out, transform .18s ease-out;
}
.wst-pick:hover { background: var(--primary); color: #fff; transform: translateY(-1px); }
.wst-pick:active { transform: scale(.97); }
.wst-pick:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; }
.wst-pick:disabled { opacity: .6; cursor: not-allowed; }
.wst-custom { display: flex; gap: 8px; margin-top: 12px; }
.wst-custom .form-input { flex: 1; }
.wst-custom-hint { font-size: 11.5px; color: var(--text-light); margin-top: 8px; }
.wst-group-title { font-size: 12.5px; font-weight: 700; color: var(--text); margin: 4px 0 8px; }
.ws-topic-desc.collapsed { max-height: 52px; overflow: hidden; position: relative; }
.ws-topic-desc.collapsed::after {
  content: ''; position: absolute; left: 0; right: 0; bottom: 0; height: 24px;
  background: linear-gradient(transparent, var(--bg));
}
.wst-more {
  margin-top: 5px; border: none; background: transparent; padding: 0;
  color: var(--primary); font-size: 12px; font-weight: 600; cursor: pointer; font-family: inherit;
}
.wst-more:hover { text-decoration: underline; }

/* ===== 流程里程碑时间线 ===== */
.flow-wrap { display: flex; flex-direction: column; gap: 14px; }
.flow-progress {
  display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
  padding: 12px 16px; border: 1px solid var(--border); border-radius: 13px;
  background: color-mix(in srgb, var(--primary) 4%, var(--card));
}
.flow-step { display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--text-lighter); }
.flow-step.passed { color: var(--primary); font-weight: 700; }
.flow-step.submitted { color: #b45309; font-weight: 700; }
.flow-step.returned { color: #be123c; font-weight: 700; }
.flow-arrow { color: var(--text-lighter); opacity: .55; font-size: 13px; }
.flow-arrow.done { color: var(--primary); opacity: 1; }
.flow-total { margin-left: auto; font-size: 13px; color: var(--text-light); }
.flow-total b { font-size: 20px; color: var(--primary); margin-left: 4px; }

.flow-list { display: flex; flex-direction: column; }
.flow-node { display: flex; gap: 14px; position: relative; padding-bottom: 6px; }
.flow-dot {
  width: 38px; height: 38px; border-radius: 50%; flex-shrink: 0; z-index: 1;
  display: flex; align-items: center; justify-content: center; font-size: 16px;
  background: var(--card); border: 2px solid var(--border); margin-top: 4px;
}
.flow-node.passed .flow-dot { border-color: var(--primary); background: color-mix(in srgb, var(--primary) 10%, var(--card)); }
.flow-node.submitted .flow-dot { border-color: #f59e0b; background: rgba(245, 158, 11, .08); }
.flow-node.returned .flow-dot { border-color: #f43f5e; background: rgba(244, 63, 94, .07); }
.flow-node:not(:last-child)::before {
  content: ''; position: absolute; left: 19px; top: 44px; bottom: 0; width: 2px;
  background: var(--border); border-radius: 2px;
}
.flow-node.passed:not(:last-child)::before { background: color-mix(in srgb, var(--primary) 45%, transparent); }
.flow-body {
  flex: 1; min-width: 0; border: 1px solid var(--border); border-radius: 13px;
  background: var(--card); padding: 13px 16px; margin-bottom: 12px;
  display: flex; flex-direction: column; gap: 9px;
}
.flow-node.locked-body .flow-body { opacity: .72; }
.flow-head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.flow-stage { font-size: 14.5px; color: var(--text); }
.flow-badge { font-size: 11.5px; font-weight: 700; padding: 2px 10px; border-radius: 999px; }
.flow-badge.none { color: #64748b; background: rgba(148, 163, 184, .13); }
.flow-badge.submitted { color: #b45309; background: rgba(245, 158, 11, .13); }
.flow-badge.passed { color: #059669; background: rgba(16, 185, 129, .13); }
.flow-badge.returned { color: #be123c; background: rgba(244, 63, 94, .11); }
.flow-times { font-size: 11.5px; color: var(--text-lighter); margin-left: auto; }
.flow-detail { display: flex; flex-direction: column; gap: 4px; }
.flow-title { font-size: 13px; font-weight: 600; color: var(--text); }
.flow-content { font-size: 12.5px; color: var(--text-light); white-space: pre-wrap; word-break: break-all; }
.flow-file { font-size: 12.5px; }
.flow-file a { color: var(--primary); font-weight: 600; cursor: pointer; }
.flow-file a:hover { text-decoration: underline; }
.flow-size { color: var(--text-lighter); font-size: 11.5px; margin-left: 6px; }
.flow-comment {
  font-size: 12.5px; padding: 9px 12px; border-radius: 10px; line-height: 1.55;
  background: color-mix(in srgb, var(--primary) 6%, transparent); color: var(--text-light);
}
.flow-comment.returned { background: rgba(244, 63, 94, .07); color: #9f1239; }
.fc-label { font-weight: 700; margin-right: 7px; font-size: 11.5px; color: var(--text-lighter); }
.flow-score {
  display: flex; align-items: center; gap: 9px; flex-wrap: wrap;
  padding: 10px 13px; border-radius: 10px; font-size: 12.5px;
  border: 1px dashed color-mix(in srgb, var(--primary) 45%, transparent);
  background: color-mix(in srgb, var(--primary) 5%, transparent); color: var(--text-light);
}
.flow-score b { font-size: 15px; }
.fs-eq { font-weight: 700; }
.fs-total b { color: var(--primary); font-size: 18px; }
.flow-form {
  border: 1.5px dashed color-mix(in srgb, var(--primary) 35%, var(--border));
  border-radius: 11px; padding: 11px 13px; display: flex; flex-direction: column; gap: 8px;
  background: color-mix(in srgb, var(--primary) 3%, transparent);
}
.ff-title { font-size: 12.5px; font-weight: 700; color: var(--primary); }
.flow-form .form-input { font-size: 13px; }
.flow-form textarea.form-input { resize: vertical; }
.ff-row { display: flex; align-items: center; gap: 10px; }
.ff-file { font-size: 12px; color: var(--text-light); flex: 1; min-width: 0; }
.ff-return { color: #be123c; border-color: rgba(244, 63, 94, .4); }
.ff-hint { font-size: 11px; color: var(--text-lighter); }
.flow-form.review { border-color: rgba(245, 158, 11, .45); background: rgba(245, 158, 11, .04); }
.ff-scores { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; font-size: 12px; color: var(--text-light); }
.ff-scores label { display: flex; align-items: center; gap: 6px; }
.ff-scores .score { width: 76px; padding: 5px 9px; }
.fs-preview { color: var(--primary); font-weight: 700; }
.flow-locked { font-size: 12px; color: var(--text-lighter); padding: 4px 0 2px; }
</style>
