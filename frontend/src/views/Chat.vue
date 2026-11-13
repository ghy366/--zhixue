<template>
  <div class="chat-page stats-page liquid" :class="{ 'side-open': !sideHidden }">
    <!-- ===== 左侧导航栏 ===== -->
    <aside class="ai-side" :class="{ collapsed: sideHidden }">
      <div class="ai-side-logo">
        <div class="ai-logo-icon">智</div>
        <span class="ai-logo-text">{{ t('智学选课') }}</span>
      </div>

      <button class="ai-new-chat" @click="newChat" :title="t('新建对话')">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path d="M12 5v14M5 12h14"/></svg>
        <span>{{ t('新建对话') }}</span>
      </button>

      <nav class="ai-side-nav">
        <button class="ai-nav-item" :class="{ active: activeNav === 'chat' }" @click="activeNav = 'chat'" :title="t('智能对话')">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
          <span>{{ t('智能对话') }}</span>
        </button>
        <button class="ai-nav-item" :class="{ active: activeNav === 'kb' }" @click="activeNav = 'kb'" :title="t('课程中心')">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
          <span>{{ t('课程中心') }}</span>
        </button>
        <button class="ai-nav-item" :class="{ active: activeNav === 'tools' }" @click="activeNav = 'tools'" :title="t('工具箱')">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="7" width="18" height="13" rx="2"/><path d="M8 7V5a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/><path d="M12 11v5M9.5 13.5h5"/></svg>
          <span>{{ t('工具箱') }}</span>
        </button>
        <button class="ai-nav-item" :class="{ active: activeNav === 'history' }" @click="activeNav = 'history'" :title="t('历史记录')">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg>
          <span>{{ t('历史记录') }}</span>
        </button>
      </nav>

      <!-- 最近对话（真实会话） -->
      <div class="ai-recent" v-if="sessionList.length">
        <div class="ai-recent-title">{{ t('最近对话') }}</div>
        <button v-for="s in recentSessions" :key="s.id" class="ai-recent-item"
                :class="{ current: s.id === currentId }" @click="switchSession(s.id)" :title="s.title">
          <span class="ai-recent-name">{{ s.title }}</span>
          <span class="ai-recent-time">{{ fmtRecent(s.updatedAt) }}</span>
        </button>
      </div>

      <div class="ai-side-foot">
        <div class="ai-side-user">
          <div class="ai-user-avatar">{{ auth.user?.name?.charAt(0) || 'U' }}</div>
          <div class="ai-user-meta">
            <div class="ai-user-name">{{ auth.user?.name || t('未登录') }}</div>
            <div class="ai-user-role">{{ roleLabel }}</div>
          </div>
        </div>
      </div>
    </aside>

    <!-- ===== 右侧主工作区 ===== -->
    <section class="ai-main">
      <div class="chat-header">
        <div class="chat-title">
          <button class="ai-side-toggle" @click="toggleSide" :title="sideHidden ? t('展开导航') : t('收起导航')">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M3 6h18M3 12h18M3 18h18"/></svg>
          </button>
          <div class="chat-title-icon">◈</div>
          <div class="chat-title-text">{{ headerTitle }}</div>
          <span class="chat-status">{{ t('在线') }}</span>
        </div>
        <div class="chat-header-actions">
          <div v-if="messages.length" class="bot-mini" :title="botStateText">
            <Bot3D compact :state="botState" />
          </div>
          <button class="chat-clear-btn" @click="clear" :disabled="!messages.length">{{ t('清空当前会话') }}</button>
        </div>
      </div>

      <!-- 智能对话视图 -->
      <template v-if="activeNav === 'chat'">
        <div class="chat-messages" ref="messagesRef" @scroll="onMsgScroll">
          <!-- 欢迎首页：无消息时 -->
          <div v-if="!messages.length" class="ai-home">
            <div class="ai-home-glow g1" />
            <div class="ai-home-glow g2" />
            <div class="ai-home-glow g3" />
            <div class="ai-home-hero">
              <div class="ai-home-greet">
                <div class="ai-home-hi">
                  {{ auth.user?.name ? t('你好，{name}', { name: auth.user.name }) : t('你好') }}<span class="ai-home-wave">👋</span>
                </div>
                <div class="ai-home-hi2">{{ t('有什么我可以帮你的吗？') }}</div>
                <div class="ai-home-brand-line"><span class="ai-home-brand">{{ t('智学选课 · 智能导航') }}</span></div>
                <div class="ai-home-sub">{{ t('结合你的选课记录、课表和成绩，帮助你制定选课计划、分析课程冲突、规划学分。') }}</div>
              </div>

              <div class="ai-mascot" aria-hidden="true">
                <span class="mascot-corner tl" />
                <span class="mascot-corner br" />
                <div class="bot-state-tag" :class="'is-' + botState"><i />{{ botStateText }}</div>
                <Bot3D :state="botState" />
              </div>
            </div>

            <div class="ai-cards">
              <button v-for="f in features" :key="f.title" class="ai-card" @click="fillQuestion(f.q)">
                <div class="ai-card-icon" :style="{ background: f.bg, color: f.color }">{{ f.icon }}</div>
                <div class="ai-card-title">{{ f.title }}</div>
                <div class="ai-card-desc">{{ f.desc }}</div>
                <div class="ai-card-arrow">→</div>
              </button>
            </div>

            <div class="ai-quick-block">
              <div class="ai-quick-label">{{ t('猜你想问') }}</div>
              <div class="quick-chips">
                <button v-for="q in quickQuestions" :key="q" class="chip" @click="send(q)">{{ q }}</button>
              </div>
            </div>
          </div>

          <template v-for="(m, i) in messages" :key="i">
            <div v-if="m.role === 'day'" class="chat-day-sep">{{ m.content }}</div>
            <div v-else class="chat-msg" :class="m.role">
              <div class="chat-avatar">{{ m.role === 'user' ? avatarText : '智' }}</div>
              <div class="chat-bubble-wrap">
                <div class="chat-sender">{{ m.role === 'user' ? t('我') : t('智能导航') }}</div>
                <div class="chat-bubble" :class="m.role">
                  <pre v-if="m.loading" class="chat-typing"><span/><span/><span/></pre>
                  <template v-else>
                    <div v-if="m.reasoning" class="chat-reasoning" :class="{ open: m.reasoningOpen }">
                      <button class="cr-toggle" @click="m.reasoningOpen = !m.reasoningOpen">
                        {{ m.reasoningOpen ? '▾ 收起思考' : '▸ 思考过程' }}
                      </button>
                      <pre v-show="m.reasoningOpen" class="cr-body">{{ m.reasoning }}</pre>
                    </div>
                    <div v-if="m.images && m.images.length" class="chat-bubble-imgs">
                      <img v-for="(src, ii) in m.images" :key="ii" :src="src" alt="uploaded" />
                    </div>
                    <div v-if="m.files && m.files.length" class="chat-bubble-files">
                      <a v-for="f in m.files" :key="f.url" class="chat-file-link" :href="f.url" :download="f.name" target="_blank">
                        <span class="cf-ico">📄</span><span class="cf-name">{{ f.name }}</span>
                        <span class="cf-size">{{ fmtSize(f.size) }}</span>
                      </a>
                    </div>
                    <div class="chat-md" v-html="renderMd(m.content)" />
                    <span v-if="m.streaming" class="chat-caret" />
                  </template>
                </div>
                <div class="chat-time">{{ m.time }}<template v-if="m.personal"> · {{ t('已结合你的选课数据') }}</template></div>
                <div v-if="m.meta && (m.meta.modelLabel || (m.meta.sources && m.meta.sources.length))" class="chat-meta">
                  <span class="cm-tag">{{ m.meta.intentCn || t('智能识别') }}</span>
                  <span v-if="m.meta.modelLabel" class="cm-tag model">⚙ {{ m.meta.modelLabel }}</span>
                  <span v-if="m.meta.webSearch" class="cm-tag web">🌐 已联网搜索</span>
                  <span v-if="m.meta.visitedPages && m.meta.visitedPages.length" class="cm-tag pages">🖥 {{ t('已查阅 {n} 个页面', { n: m.meta.visitedPages.length }) }}</span>
                  <span class="cm-src">{{ t('参考：{list}', { list: m.meta.sources.join('、') }) }}</span>
                  <span v-if="m.meta.courses && m.meta.courses.length" class="cm-src">{{ t('命中：{list}', { list: m.meta.courses.join('、') }) }}</span>
                </div>
                <div v-if="m.meta && m.meta.pages && m.meta.pages.length" class="chat-pages">
                  <span class="cp-label">{{ t('本次入口') }}</span>
                  <button v-for="p in m.meta.pages" :key="p.id" class="cp-chip" :title="t('前往 /{p}', { p: p.path })"
                          @click="router.push('/' + p.path)">{{ p.name }}<span class="cp-arrow">↗</span></button>
                </div>
                <!-- 最后一条 AI 回复：复制 / 重新生成 -->
                <div v-if="m.role === 'assistant' && i === lastAssistantIdx && !m.loading && !m.streaming && m.content"
                     class="chat-acts">
                  <button class="chat-act" @click="copyMsg(m)">{{ copied ? t('✓ 已复制') : t('复制') }}</button>
                  <button class="chat-act" @click="regenerate" :disabled="loading">{{ t('重新生成') }}</button>
                </div>
              </div>
            </div>
          </template>

          <div v-if="error" class="chat-error">
            ⚠️ {{ error }}
            <button class="chat-retry" @click="retry">{{ t('重试') }}</button>
          </div>
        </div>
      </template>

      <!-- 历史记录视图 -->
      <div v-else-if="activeNav === 'history'" class="ai-panel">
        <div class="ai-panel-head">
          <div class="ai-panel-title">{{ t('历史记录') }}</div>
          <div class="ai-panel-sub">{{ t('共 {n} 个会话，点击可继续聊天', { n: sessionList.length }) }}</div>
        </div>
        <div v-if="!sessionList.length" class="ai-empty">{{ t('暂无历史会话，点击「新建对话」开始提问') }}</div>
        <div v-else class="ai-history-list">
          <div v-for="s in sessionList" :key="s.id" class="ai-history-item" :class="{ current: s.id === currentId }">
            <div class="ai-history-main" @click="switchSession(s.id)">
              <div class="ai-history-title">{{ s.title }}</div>
              <div class="ai-history-meta">{{ t('{n} 条消息', { n: s.messages.length }) }} · {{ fmtRecent(s.updatedAt) }}</div>
            </div>
            <button class="ai-history-del" @click="deleteSession(s.id)" :title="t('删除会话')">{{ t('删除') }}</button>
          </div>
        </div>
      </div>

      <!-- 课程中心：课程信息与选课功能汇总（全部来自真实接口） -->
      <div v-else-if="activeNav === 'kb'" class="ai-panel">
        <div class="ai-panel-head">
          <div class="ai-panel-title">{{ t('课程中心') }}</div>
          <div class="ai-panel-sub">{{ t('课程信息、选课状态与系统机制说明 · 数据来自系统真实接口') }}</div>
        </div>

        <div v-if="kbErr" class="ai-empty">
          {{ t('课程中心数据加载失败：{msg}', { msg: kbErr }) }}
          <div style="margin-top:14px"><button class="chat-retry" @click="loadKb">{{ t('重新加载') }}</button></div>
        </div>

        <template v-else>
          <!-- 选课状态（/system/my-window + /system/config） -->
          <div class="kb-status" v-if="kbStatus">
            <div class="kb-status-item">
              <span class="kb-k">{{ t('当前轮次') }}</span>
              <b>{{ t('第 {n} 轮', { n: kbStatus.currentRound }) }}</b>
            </div>
            <div class="kb-status-item">
              <span class="kb-k">{{ t('开放状态') }}</span>
              <b :class="kbStatus.roundOpen && kbStatus.myOpen !== false ? 'kb-on' : 'kb-off'">
                {{ kbStatus.roundOpen ? (kbStatus.myOpen === false ? t('对我未开放（错峰）') : t('选课开放中')) : t('未开放') }}
              </b>
            </div>
            <div class="kb-status-item" v-if="kbStatus.roundCloseAt">
              <span class="kb-k">{{ t('本轮截止') }}</span><b>{{ fmtKbTime(kbStatus.roundCloseAt) }}</b>
            </div>
            <div class="kb-status-item" v-if="kbStatus.staggered && kbStatus.myOpenAt">
              <span class="kb-k">{{ t('我的开放时间') }}</span><b>{{ fmtKbTime(kbStatus.myOpenAt) }}</b>
            </div>
            <div class="kb-status-item" v-if="kbCfg && kbCfg.maxCoursesPerSemester">
              <span class="kb-k">{{ t('每轮选课上限') }}</span><b>{{ t('{n} 门', { n: kbCfg.maxCoursesPerSemester }) }}</b>
            </div>
            <div class="kb-status-item" v-if="kbCfg && kbCfg.maxCredit">
              <span class="kb-k">{{ t('学分上限') }}</span><b>{{ t('{n} 分', { n: kbCfg.maxCredit }) }}</b>
            </div>
          </div>

          <!-- 课程查询（GET /courses） -->
          <div class="kb-sec-title">
            {{ t('课程查询') }}
            <button v-if="isStudent" class="kb-jump" @click="router.push('/courses')">{{ t('前往选课 →') }}</button>
          </div>
          <div class="kb-toolbar">
            <input class="kb-search" v-model="kbQuery" :placeholder="t('搜索课程名称 / 教师 / 课程编号')" />
            <div class="kb-cats">
              <button :class="{ active: !kbCat }" @click="kbCat = ''">{{ t('全部') }}</button>
              <button v-for="c in kbCats" :key="c" :class="{ active: kbCat === c }" @click="kbCat = c">{{ c }}</button>
            </div>
          </div>

          <div v-if="!kbCourses.length && !kbLoading" class="ai-empty">{{ t('暂无课程数据') }}</div>
          <div class="kb-list">
            <div v-for="c in kbFiltered" :key="c.id" class="kb-course" :class="{ open: kbOpenId === c.id }"
                 @click="kbOpenId = kbOpenId === c.id ? null : c.id">
              <div class="kb-row">
                <div class="kb-name">
                  {{ c.name }}
                  <span v-if="(c.category || '').includes('必修')" class="kb-tag req">{{ t('必修 · 系统自动排课') }}</span>
                </div>
                <div class="kb-meta">{{ c.category || t('未分类') }} · {{ t('{n} 学分', { n: c.credit }) }} · {{ c.teacherName || t('教师待定') }}</div>
                <div class="kb-cap" v-if="c.capacity > 0">
                  <span class="kb-cap-num">{{ Math.max(0, c.capacity - (c.stat?.total || 0)) }}</span> / {{ c.capacity }} {{ t('剩余') }}
                </div>
                <div class="kb-cap" v-else>{{ t('容量未设') }}</div>
              </div>
              <div v-if="kbOpenId === c.id" class="kb-detail" @click.stop>
                <div class="kb-detail-grid">
                  <div><span class="kb-k">{{ t('课程编号') }}</span>{{ c.courseCode || '—' }}</div>
                  <div><span class="kb-k">{{ t('上课时间') }}</span>{{ c.time || t('时间待定') }}</div>
                  <div><span class="kb-k">{{ t('上课地点') }}</span>{{ c.location || '—' }}</div>
                  <div><span class="kb-k">{{ t('校区') }}</span>{{ c.campus || '—' }}</div>
                  <div><span class="kb-k">{{ t('课程类别') }}</span>{{ c.category || '—' }}</div>
                  <div><span class="kb-k">{{ t('已报名') }}</span>{{ t('{n} 人', { n: c.stat?.total || 0 }) }}</div>
                </div>
                <div class="kb-intro" v-if="c.intro">{{ c.intro }}</div>
                <div class="kb-intro kb-dim" v-else>{{ t('暂无课程简介') }}</div>
              </div>
            </div>
          </div>

          <!-- 选课机制与常见问题（依据系统真实实现与当前配置） -->
          <div class="kb-sec-title">{{ t('选课机制说明') }}</div>
          <div class="kb-faq">
            <div class="kb-qa"><b>{{ t('三轮选课制') }}</b><p>{{ t('第 1、2 轮为积分报名：每轮积分池 50 分，单门课程可投 0-50 分（0 分表示不投入），轮次截止后按投分从高到低录取，同分随机抽签。第 3 轮为先到先得，不使用积分，即时录取。') }}</p></div>
            <div class="kb-qa" v-if="kbCfg && kbCfg.maxCoursesPerSemester"><b>{{ t('门数与学分上限') }}</b><p>{{ t('每轮最多选中 {n} 门非必修课程{credit}。公共必修 / 专业必修由系统自动排课，不占用选课额度。', { n: kbCfg.maxCoursesPerSemester, credit: kbCfg.maxCredit ? t('，总学分不超过 {n} 分', { n: kbCfg.maxCredit }) : '' }) }}</p></div>
            <div class="kb-qa"><b>{{ t('候补与递补') }}</b><p>{{ t('课程报满后可进入候补队列；有人退选或轮次结算后系统按候补顺序自动递补，全程无需手动抢课。') }}</p></div>
            <div class="kb-qa" v-if="kbCfg && kbCfg.confirmMinutes"><b>{{ t('结算与确认') }}</b><p>{{ t('每轮截止后系统统一结算录取结果，并在 {n} 分钟确认时限内保留名额，请及时查看「我的课程」确认录取情况。', { n: kbCfg.confirmMinutes }) }}</p></div>
          </div>

          <div class="kb-sec-title">{{ t('常见问题') }}</div>
          <div class="kb-faq">
            <div class="kb-qa"><b>{{ t('为什么列表里看不到某门必修课？') }}</b><p>{{ t('公共必修与专业必修由系统自动排入课表，不参与选课流程，可在「我的课程」中查看。') }}</p></div>
            <div class="kb-qa"><b>{{ t('没被录取怎么办？') }}</b><p>{{ t('可以进入课程候补队列等待递补，也可以在下一轮重新投分，或在第 3 轮先到先得阶段直接报名。') }}</p></div>
            <div class="kb-qa"><b>{{ t('退课后积分会退回吗？') }}</b><p>{{ t('轮次结算前退课，投入的积分会退回积分池；结算完成后退课需遵守系统当轮退课规则，请以「我的课程」页面的实际提示为准。') }}</p></div>
            <div class="kb-qa"><b>{{ t('什么时候可以选课？') }}</b><p>{{ t('以页面顶部「选课状态」显示的当前轮次与截止时间为准；如设置了年级错峰开放，请留意你的开放时间。') }}</p></div>
          </div>
        </template>
      </div>

      <!-- 工具箱（规划中，不伪装） -->
      <div v-else-if="activeNav === 'tools'" class="ai-panel">
        <div class="ai-panel-head">
          <div class="ai-panel-title">{{ t('工具箱') }}</div>
          <div class="ai-panel-sub">{{ t('选课决策 · 学业规划工具（数据来自系统真实接口）') }}</div>
        </div>

        <!-- 工具选择 -->
        <div v-if="!activeTool" class="tool-grid">
          <button class="tool-card" @click="activeTool = 'bids'">
            <div class="tool-icon" style="background: rgba(43,177,135,0.14)">📊</div>
            <div class="tool-name">{{ t('投分策略计算器') }}</div>
            <div class="tool-desc">{{ t('50 分积分池怎么分：按竞争线自动分配，实时算出每门课的录取概率') }}</div>
          </button>
          <button class="tool-card" @click="activeTool = 'plan'">
            <div class="tool-icon" style="background: rgba(47,159,224,0.14)">🧩</div>
            <div class="tool-name">{{ t('选课方案预演') }}</div>
            <div class="tool-desc">{{ t('勾选一组课程，一次校验时间冲突、学分门数上限、先修课与名额，并预览周课表') }}</div>
          </button>
          <button class="tool-card" @click="activeTool = 'gradplan'">
            <div class="tool-icon" style="background: rgba(167,139,250,0.14)">🎓</div>
            <div class="tool-name">{{ t('毕业缺口排期') }}</div>
            <div class="tool-desc">{{ t('按剩余学期把四大类学分缺口拆成每学期目标，并排出推荐课程') }}</div>
          </button>
          <button class="tool-card" @click="activeTool = 'gpa'">
            <div class="tool-icon" style="background: rgba(64,150,255,0.12)">🎯</div>
            <div class="tool-name">{{ t('GPA 计算器') }}</div>
            <div class="tool-desc">{{ t('输入课程、学分与分数，实时计算加权平均分与绩点，支持按目标绩点反推') }}</div>
          </button>
          <button class="tool-card" @click="activeTool = 'countdown'">
            <div class="tool-icon" style="background: rgba(47,159,224,0.12)">⏳</div>
            <div class="tool-name">{{ t('倒计时板') }}</div>
            <div class="tool-desc">{{ t('期末考试、四六级、考研…… 重要日期倒计时，本地保存自动刷新') }}</div>
          </button>
          <button class="tool-card" @click="activeTool = 'focus'">
            <div class="tool-icon" style="background: rgba(167,139,250,0.14)">🍅</div>
            <div class="tool-name">{{ t('专注番茄钟') }}</div>
            <div class="tool-desc">{{ t('专注与休息循环，圆环进度实时显示，完成时提示音提醒') }}</div>
          </button>
        </div>

        <!-- GPA 计算器 -->
        <div v-else-if="activeTool === 'gpa'" class="tool-body">
          <button class="tool-back" @click="activeTool = ''">← {{ t('工具箱') }}</button>
          <table class="gpa-table">
            <thead><tr><th>{{ t('课程') }}</th><th class="num">{{ t('学分') }}</th><th class="num">{{ t('百分制成绩') }}</th><th class="num">{{ t('绩点') }}</th><th></th></tr></thead>
            <tbody>
              <tr v-for="(r, i) in gpaRows" :key="i">
                <td><input v-model.trim="r.name" :placeholder="t('课程名（选填）')" /></td>
                <td class="num"><input v-model.number="r.credit" type="number" min="0.5" step="0.5" /></td>
                <td class="num"><input v-model.number="r.score" type="number" min="0" max="100" :placeholder="t('0-100')" /></td>
                <td class="num gpa-pt">{{ rowPoint(r) }}</td>
                <td><button class="gpa-del" @click="gpaRows.length > 1 && gpaRows.splice(i, 1)" :disabled="gpaRows.length <= 1">✕</button></td>
              </tr>
            </tbody>
          </table>
          <div class="gpa-actions">
            <button class="gpa-add" @click="gpaRows.push({ name: '', credit: 3, score: null })">+ {{ t('添加课程') }}</button>
            <button class="gpa-clear" @click="gpaReset">{{ t('清空') }}</button>
          </div>
          <div v-if="gpaSummary" class="gpa-result">
            <div class="gpa-kpi"><div class="kpi-v">{{ gpaSummary.credits }}</div><div class="kpi-k">{{ t('总学分') }}</div></div>
            <div class="gpa-kpi"><div class="kpi-v">{{ gpaSummary.avg }}</div><div class="kpi-k">{{ t('加权平均分') }}</div></div>
            <div class="gpa-kpi hl"><div class="kpi-v">{{ gpaSummary.gpa }}</div><div class="kpi-k">{{ t('GPA（4.0 制）') }}</div></div>
          </div>
          <div v-if="gpaSummary" class="gpa-plan">
            <div class="plan-row">
              <span>{{ t('目标绩点') }}</span>
              <input v-model.number="planTarget" type="number" min="0" max="4" step="0.1" />
              <span>{{ t('再修学分') }}</span>
              <input v-model.number="planCredit" type="number" min="0" step="1" />
              <span class="plan-out">{{ planNeed }}</span>
            </div>
            <div class="plan-tip">{{ t('按当前成绩与目标绩点反推：再修这些学分时平均绩点需要达到多少。') }}</div>
          </div>
        </div>

        <!-- 倒计时板 -->
        <div v-else-if="activeTool === 'countdown'" class="tool-body">
          <button class="tool-back" @click="activeTool = ''">← {{ t('工具箱') }}</button>
          <div class="cd-add">
            <input v-model.trim="cdName" :placeholder="t('事项名，如：期末考试')" maxlength="16" />
            <input v-model="cdDate" type="date" />
            <button class="gpa-add" @click="cdAdd" :disabled="!cdName || !cdDate">+ {{ t('添加') }}</button>
          </div>
          <div class="cd-list">
            <div v-for="it in cdSorted" :key="it.id" class="cd-item" :class="{ past: cdDays(it.date) < 0, today: cdDays(it.date) === 0 }">
              <div class="cd-main">
                <div class="cd-name">{{ it.name }}</div>
                <div class="cd-date">{{ it.date }}</div>
              </div>
              <div class="cd-num" v-if="cdDays(it.date) > 0"><b>{{ cdDays(it.date) }}</b><span>{{ t('天') }}</span></div>
              <div class="cd-num" v-else-if="cdDays(it.date) === 0"><b class="today-v">{{ t('今天') }}</b></div>
              <div class="cd-num past-v" v-else><b>{{ -cdDays(it.date) }}</b><span>{{ t('天前') }}</span></div>
              <button class="gpa-del" @click="cdDel(it.id)">✕</button>
            </div>
            <div v-if="!cdItems.length" class="cd-empty">{{ t('还没有倒计时，添加一个重要日期吧。') }}</div>
          </div>
        </div>

        <!-- 专注番茄钟 -->
        <div v-else-if="activeTool === 'focus'" class="tool-body">
          <button class="tool-back" @click="activeTool = ''">← {{ t('工具箱') }}</button>
          <div class="focus-wrap">
            <svg class="focus-ring" viewBox="0 0 200 200">
              <circle class="ring-bg" cx="100" cy="100" r="88" />
              <circle class="ring-fg" cx="100" cy="100" r="88"
                      :class="{ break: focusMode === 'break' }"
                      :style="{ strokeDashoffset: ringOffset }" />
            </svg>
            <div class="focus-center">
              <div class="focus-time">{{ focusFmt }}</div>
              <div class="focus-mode">{{ focusMode === 'focus' ? t('专注中') : t('休息中') }}</div>
            </div>
          </div>
          <div class="focus-acts">
            <button class="focus-btn main" @click="focusToggle">{{ focusRun ? t('暂停') : t('开始') }}</button>
            <button class="focus-btn" @click="focusReset">{{ t('重置') }}</button>
            <select v-model.number="focusMin" class="focus-min" :disabled="focusRun" @change="focusReset">
              <option :value="15">15 {{ t('分钟') }}</option>
              <option :value="25">25 {{ t('分钟') }}</option>
              <option :value="40">40 {{ t('分钟') }}</option>
              <option :value="60">60 {{ t('分钟') }}</option>
            </select>
          </div>
          <div class="focus-stat">{{ t('本次已完成') }} <b>{{ focusDone }}</b> {{ t('个番茄') }} 🍅</div>
        </div>

        <!-- 选课决策工具（独立组件，共用真实业务数据） -->
        <BidStrategy v-else-if="activeTool === 'bids'" @back="activeTool = ''" />
        <PlanRehearsal v-else-if="activeTool === 'plan'" @back="activeTool = ''" />
        <GradPlan v-else-if="activeTool === 'gradplan'" @back="activeTool = ''" />
      </div>

      <!-- 输入区（对话视图固定底部）：图片预览 + 文本 + 识图按钮 + 发送 -->
      <div v-if="activeNav === 'chat'" class="chat-input-area">
        <div class="chat-input-col">
          <div v-if="pendingImages.length" class="chat-img-strip">
            <div v-for="(img, ii) in pendingImages" :key="ii" class="chat-img-thumb">
              <img :src="img" alt="pending" />
              <button class="chat-img-remove" @click="removeImage(ii)" :title="t('移除图片')">×</button>
            </div>
          </div>
          <div v-if="pendingFiles.length" class="chat-file-strip">
            <div v-for="(f, fi) in pendingFiles" :key="fi" class="chat-file-chip">
              <span class="cf-ico">📄</span>
              <span class="cf-name" :title="f.name">{{ f.name }}</span>
              <span class="cf-size">{{ fmtSize(f.size) }}</span>
              <button class="chat-img-remove" @click="removeFile(fi)" :title="t('移除文件')">×</button>
            </div>
          </div>
          <div class="chat-input-row">
            <button class="chat-img-btn chat-add-btn" :disabled="loading" :title="t('添加文件')" @click="pickFile">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round">
                <path d="M12 5v14M5 12h14"/>
              </svg>
            </button>
            <div class="chat-input-wrap">
              <textarea ref="taRef" v-model="input" :disabled="loading"
                        :placeholder="t('输入你的问题... Enter 发送，Shift+Enter 换行，可粘贴截图或文件')"
                        @keydown.enter.exact.prevent="send()"
                        @paste="onPaste"
                        :rows="1" @input="autoResize" />
              <select v-model="selectedKey" class="ai-model-select" :title="t('选择通道与模型')" @change="onModelChange">
                <option value="auto">{{ t('自动 · 智能级联') }}</option>
                <optgroup v-for="ch in modelChannels" :key="ch.id" :label="ch.name">
                  <option v-for="m in ch.models" :key="ch.id + '|' + m.id" :value="ch.id + '|' + m.id">
                    {{ m.label }}{{ m.primary ? ' ★' : '' }}{{ m.vision ? ' 🖼' : '' }}
                  </option>
                </optgroup>
              </select>
            </div>
            <button class="chat-img-btn" :disabled="!visionAllowed" :title="visionTip" @click="pickImage">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <rect x="3" y="3" width="18" height="18" rx="3"/><circle cx="8.5" cy="8.5" r="1.5"/><path d="m21 15-5-5L5 21"/>
              </svg>
            </button>
            <button v-if="loading && streamingNow" class="chat-send chat-stop" @click="stop" :title="t('停止生成')">
              <svg class="send-icon" viewBox="0 0 24 24" fill="currentColor"><rect x="7" y="7" width="10" height="10" rx="2"/></svg>
            </button>
            <button v-else class="chat-send" :disabled="loading || (!input.trim() && !pendingImages.length && !pendingFiles.length)" @click="send">
              <svg class="send-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M22 2L11 13"/><path d="M22 2L15 22L11 13L2 9L22 2Z"/>
              </svg>
            </button>
          </div>
        </div>
        <input ref="imgInputRef" type="file" accept="image/*" multiple class="chat-img-input" @change="onImagePicked" />
        <input ref="fileInputRef" type="file" multiple class="chat-img-input" @change="onFilePicked" />
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted, onBeforeUnmount, reactive, watch } from 'vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import http from '@/api'
import { t } from '@/i18n'
import { useAuthStore } from '@/stores/auth'
import Bot3D from '@/components/Bot3D.vue'
import BidStrategy from '@/components/tools/BidStrategy.vue'
import PlanRehearsal from '@/components/tools/PlanRehearsal.vue'
import GradPlan from '@/components/tools/GradPlan.vue'

const router = useRouter()

const auth = useAuthStore()
const isStudent = computed(() => auth.user?.role === 'student')

const roleLabel = computed(() =>
  ({ student: t('学生模式'), teacher: t('教师模式'), admin: t('管理员模式') } as any)[auth.user?.role] || t('通用')
)
const avatarText = computed(() => auth.user?.name?.charAt(0) || 'U')

// ===== 自选通道 / 模型（列表由后端下发，不含密钥） =====
const modelChannels = ref<any[]>([])
const selectedKey = ref(localStorage.getItem('zhixue_ai_model') || 'auto')
function onModelChange() { localStorage.setItem('zhixue_ai_model', selectedKey.value) }
function selection() {
  const parts = selectedKey.value.split('|')
  return parts.length === 2 ? { channel: parts[0], model: parts[1] } : { channel: '', model: '' }
}
async function loadAiOptions() {
  try {
    const r: any = await http.get('/ai/options')
    modelChannels.value = r?.data?.channels || r?.channels || []
    // 记住的选择已不在清单里（通道被删/改名）→ 退回自动级联
    if (selectedKey.value !== 'auto') {
      const valid = modelChannels.value.some((c: any) =>
        (c.models || []).some((m: any) => `${c.id}|${m.id}` === selectedKey.value))
      if (!valid) { selectedKey.value = 'auto'; onModelChange() }
    }
  } catch { /* 旧后端或接口不可用时只保留「自动」 */ }
}

// ===== 识图：图片选择 / 压缩 / 预览 =====
const pendingImages = ref<string[]>([])
const imgInputRef = ref<HTMLInputElement | null>(null)

/** 自动模式带图会由后端切到识图模型；手动选择时只有标了 🖼 的模型能发图 */
const visionAllowed = computed(() => {
  if (selectedKey.value === 'auto') return true
  const [cid, mid] = selectedKey.value.split('|')
  const ch = modelChannels.value.find((c: any) => c.id === cid)
  const m = ch?.models?.find((x: any) => x.id === mid)
  return !!m?.vision
})
const visionTip = computed(() => visionAllowed.value
  ? t('添加图片') : t('当前模型不支持识图，请切换带 🖼 的模型'))

function pickImage() { imgInputRef.value?.click() }
function removeImage(i: number) { pendingImages.value.splice(i, 1) }

/** 前端先压缩（最长边 1024 / JPEG 0.82），控制上传体积 */
function compressToBlob(file: File): Promise<Blob> {
  return new Promise((resolve, reject) => {
    const fr = new FileReader()
    fr.onerror = () => reject(new Error('read'))
    fr.onload = () => {
      const img = new Image()
      img.onerror = () => reject(new Error('decode'))
      img.onload = () => {
        const scale = Math.min(1, 1024 / Math.max(img.width, img.height))
        const w = Math.max(1, Math.round(img.width * scale))
        const h = Math.max(1, Math.round(img.height * scale))
        const cv = document.createElement('canvas')
        cv.width = w; cv.height = h
        const ctx = cv.getContext('2d')
        if (!ctx) { reject(new Error('canvas')); return }
        ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, w, h)
        ctx.drawImage(img, 0, 0, w, h)
        cv.toBlob(b => b ? resolve(b) : reject(new Error('encode')), 'image/jpeg', 0.82)
      }
      img.src = String(fr.result)
    }
    fr.readAsDataURL(file)
  })
}

/** 上传落盘（后端存到 E:/ai文件 图片缓存/chat-images），消息里只留直链 URL，避免 base64 撑爆数据库 */
async function uploadImage(file: File): Promise<string> {
  const blob = await compressToBlob(file)
  const fd = new FormData()
  fd.append('file', new File([blob], 'chat.jpg', { type: 'image/jpeg' }))
  const r: any = await http.post('/ai/images', fd)
  if (!r?.success || !r?.data?.url) throw new Error(r?.message || 'upload failed')
  return r.data.url
}

/** 选择器与粘贴共用的入列管线：压缩 → 上传 → 预览，上限 3 张 */
async function addImageFiles(files: File[]) {
  const room = Math.max(0, 3 - pendingImages.value.length)
  for (const f of files.slice(0, room)) {
    if (!f.type.startsWith('image/')) continue
    try { pendingImages.value.push(await uploadImage(f)) }
    catch { ElMessage.error(t('图片上传失败')) }
  }
  if (files.length > room || pendingImages.value.length >= 3) ElMessage.info(t('最多上传 3 张图片'))
}

async function onImagePicked(e: Event) {
  const el = e.target as HTMLInputElement
  const files = Array.from(el.files || [])
  el.value = ''
  await addImageFiles(files)
}

/** 支持在输入框直接粘贴截图（Ctrl+V / 右键粘贴），走与选择器相同的压缩预览管线 */
function onPaste(e: ClipboardEvent) {
  const items = e.clipboardData?.items
  if (!items) return
  const imgs: File[] = [], docs: File[] = []
  for (let i = 0; i < items.length; i++) {
    if (items[i].kind !== 'file') continue
    const f = items[i].getAsFile()
    if (!f) continue
    if (f.type.startsWith('image/')) imgs.push(f)
    else if (f.size > 0) docs.push(f) // 其余文件走附件管线；size=0 多为文件夹等伪文件
  }
  if (!imgs.length && !docs.length) return
  e.preventDefault() // 阻止二进制以乱码文本落入输入框
  if (imgs.length) addImageFiles(imgs)
  if (docs.length) addFiles(docs)
}

// ===== 通用附件（Word/txt/代码等）：+ 按钮或粘贴上传，落盘后消息里只留 URL =====
interface FileAtt { name: string; url: string; size: number; excerpt?: string }
const pendingFiles = ref<FileAtt[]>([])
const fileInputRef = ref<HTMLInputElement | null>(null)

function pickFile() { fileInputRef.value?.click() }
function removeFile(i: number) { pendingFiles.value.splice(i, 1) }

function fmtSize(n: number) {
  return n >= 1024 * 1024 ? (n / 1048576).toFixed(1) + 'MB' : Math.max(1, Math.round(n / 1024)) + 'KB'
}

/** 通用附件入列：上传后展示为文件条，txt/代码/docx 正文片段由后端抽取注入 AI */
async function addFiles(files: File[]) {
  const room = Math.max(0, 3 - pendingFiles.value.length)
  for (const f of files.slice(0, room)) {
    try {
      const fd = new FormData()
      fd.append('file', f)
      const r: any = await http.post('/ai/files', fd)
      if (!r?.success || !r?.data?.url) throw new Error(r?.message || 'upload failed')
      pendingFiles.value.push({ name: r.data.name, url: r.data.url, size: r.data.size, excerpt: r.data.excerpt })
    } catch { ElMessage.error(t('文件上传失败')) }
  }
  if (files.length > room || pendingFiles.value.length >= 3) ElMessage.info(t('最多上传 3 个文件'))
}

function onFilePicked(e: Event) {
  const el = e.target as HTMLInputElement
  const files = Array.from(el.files || [])
  el.value = ''
  addFiles(files)
}

// ===== 工具箱（纯前端本地计算，不依赖接口） =====
const activeTool = ref<'' | 'gpa' | 'countdown' | 'focus' | 'bids' | 'plan' | 'gradplan'>('')

function loadJson<T>(k: string, fb: T): T {
  try { const s = localStorage.getItem(k); return s ? JSON.parse(s) as T : fb } catch { return fb }
}

// --- GPA 计算器（国内常用 4.0 制换算） ---
interface GpaRow { name: string; credit: number; score: number | null }
const gpaRows = ref<GpaRow[]>(loadJson('zhixue_tool_gpa', [
  { name: '', credit: 3, score: null }, { name: '', credit: 3, score: null }, { name: '', credit: 3, score: null },
]))
watch(gpaRows, v => localStorage.setItem('zhixue_tool_gpa', JSON.stringify(v)), { deep: true })

function gpPoint(s: number): number {
  if (s >= 90) return 4.0
  if (s >= 85) return 3.7
  if (s >= 82) return 3.3
  if (s >= 78) return 3.0
  if (s >= 75) return 2.7
  if (s >= 72) return 2.3
  if (s >= 68) return 2.0
  if (s >= 64) return 1.5
  if (s >= 60) return 1.0
  return 0
}
const rowPoint = (r: GpaRow) =>
  (r.score != null && String(r.score) !== '' && !isNaN(Number(r.score)) && Number(r.score) >= 0 && Number(r.score) <= 100)
    ? gpPoint(Number(r.score)) : '—'

const gpaSummary = computed(() => {
  let c = 0, q = 0, ws = 0
  for (const r of gpaRows.value) {
    if (!r.credit || r.credit <= 0) continue
    if (r.score == null || String(r.score) === '' || isNaN(Number(r.score)) || Number(r.score) < 0 || Number(r.score) > 100) continue
    c += r.credit; q += r.credit * gpPoint(Number(r.score)); ws += r.credit * Number(r.score)
  }
  if (!c) return null
  return { credits: Math.round(c * 10) / 10, avg: (ws / c).toFixed(1), gpa: (q / c).toFixed(2) }
})

const planTarget = ref<number | null>(3.5)
const planCredit = ref<number | null>(10)
const planNeed = computed(() => {
  const s = gpaSummary.value
  if (!s) return t('先填写成绩')
  const R = Number(planCredit.value) || 0
  const G = Number(planTarget.value) || 0
  if (R <= 0 || G <= 0) return t('填写目标与学分')
  const need = (G * (s.credits + R) - Number(s.gpa) * s.credits) / R
  if (need > 4) return `需 ${need.toFixed(2)} · ${t('超出 4.0，目标偏难')}`
  if (need <= 0) return t('已达目标 ✓')
  return `${t('平均绩点')} ${need.toFixed(2)}`
})

const gpaReset = () => {
  gpaRows.value = [{ name: '', credit: 3, score: null }, { name: '', credit: 3, score: null }, { name: '', credit: 3, score: null }]
  planTarget.value = 3.5; planCredit.value = 10
}

// --- 倒计时板 ---
interface CdItem { id: number; name: string; date: string }
const cdItems = ref<CdItem[]>(loadJson('zhixue_tool_countdown', []))
watch(cdItems, v => localStorage.setItem('zhixue_tool_countdown', JSON.stringify(v)), { deep: true })
const cdName = ref(''); const cdDate = ref('')
const cdSorted = computed(() => [...cdItems.value].sort((a, b) => a.date.localeCompare(b.date)))
const cdDays = (d: string) => {
  const t = new Date(d + 'T00:00:00').getTime()
  const n = new Date()
  return Math.round((t - new Date(n.getFullYear(), n.getMonth(), n.getDate()).getTime()) / 86400000)
}
const cdAdd = () => {
  if (!cdName.value || !cdDate.value) return
  cdItems.value.push({ id: Date.now(), name: cdName.value, date: cdDate.value })
  cdName.value = ''; cdDate.value = ''
}
const cdDel = (id: number) => { cdItems.value = cdItems.value.filter(x => x.id !== id) }

// --- 专注番茄钟 ---
const focusMin = ref(25)
const breakMin = 5
const focusLeft = ref(25 * 60)
const focusRun = ref(false)
const focusMode = ref<'focus' | 'break'>('focus')
const focusDone = ref(0)
let focusTimer: number | undefined

const focusFmt = computed(() => {
  const m = Math.floor(focusLeft.value / 60), s = focusLeft.value % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
})
const ringOffset = computed(() => {
  const C = 2 * Math.PI * 88   // r=88 圆周
  const total = (focusMode.value === 'focus' ? focusMin.value : breakMin) * 60
  return String(C * (1 - focusLeft.value / Math.max(total, 1)))
})

const beep = () => {
  try {
    const AC = (window as any).AudioContext || (window as any).webkitAudioContext
    if (!AC) return
    const ac = new AC()
    const o = ac.createOscillator(); const g = ac.createGain()
    o.connect(g); g.connect(ac.destination)
    o.type = 'sine'; o.frequency.value = 880
    g.gain.setValueAtTime(0.25, ac.currentTime)
    g.gain.exponentialRampToValueAtTime(0.001, ac.currentTime + 1.2)
    o.start(); o.stop(ac.currentTime + 1.2)
  } catch { /* 无声环境忽略 */ }
}

const focusTick = () => {
  if (!focusRun.value) return
  focusLeft.value--
  if (focusLeft.value <= 0) {
    beep()
    if (focusMode.value === 'focus') { focusDone.value++; focusMode.value = 'break'; focusLeft.value = breakMin * 60 }
    else { focusMode.value = 'focus'; focusLeft.value = focusMin.value * 60 }
  }
}
const focusToggle = () => {
  focusRun.value = !focusRun.value
  if (focusRun.value && focusTimer == null) focusTimer = window.setInterval(focusTick, 1000)
  if (!focusRun.value && focusTimer != null) { clearInterval(focusTimer); focusTimer = undefined }
}
const focusReset = () => {
  focusRun.value = false
  if (focusTimer != null) { clearInterval(focusTimer); focusTimer = undefined }
  focusMode.value = 'focus'; focusLeft.value = focusMin.value * 60
}
onBeforeUnmount(() => { if (focusTimer != null) clearInterval(focusTimer) })

// ===== 快捷提问（每条均经 /api/ai/probe 验证命中真实意图与数据块） =====
const quickQuestions = computed(() => {
  if (auth.user?.role === 'student' && auth.user) return [
    '我还差多少学分毕业？', '帮我分析我的意向单和投分策略。', '我的意向课程和已录取课程有时间冲突吗？', '我的课表有哪些课程？',
    '我还缺什么课程可以补修？', '学校的选课规则是什么？', '帮我分析我的成绩和 GPA。', '根据我的毕业进度和学分缺口，帮我制定选课计划。',
  ]
  if (auth.user?.role === 'teacher') return [
    '我教哪些课？', '当前选课状态如何？', '选课规则是怎样的？', '我的课程选了多少人？',
  ]
  return ['当前选课状态如何？', '平台的整体选课统计情况如何？', '课程容量和报名情况怎样？', '选课规则是怎样的？']
})

// ===== 功能卡片（仅展示后端真实具备的能力，点击填入输入框可修改后发送） =====
// 依据：AiContextService.recognize 的意图识别 + 各意图真实数据块
// （毕业进度/成绩绩点/我的选课/意向单/候补/课程详情/时间冲突计算/缺口推荐/选课规则）
const features = computed(() => {
  if (auth.user?.role === 'teacher') return [
    { icon: '👨‍🏫', color: '#0284c7', bg: 'rgba(2,132,199,0.10)', title: t('我的课程'), desc: t('查询我讲授的课程与时间地点'), q: '我教哪些课？' },
    { icon: '👥', color: '#2f9fe0', bg: 'rgba(47,159,224,0.12)', title: t('报名情况'), desc: t('查看我课程的本轮报名人数'), q: '我的课程选了多少人？' },
    { icon: '📋', color: '#2f9fe0', bg: 'rgba(47,159,224,0.12)', title: t('选课规则'), desc: t('三轮积分制、候补与录取规则'), q: '选课规则是怎样的？' },
    { icon: '⏰', color: '#d97706', bg: 'rgba(217,119,6,0.10)', title: t('选课状态'), desc: t('当前轮次与开放状态'), q: '当前选课状态如何？' },
  ]
  if (auth.user?.role === 'admin') return [
    { icon: '📈', color: '#2f9fe0', bg: 'rgba(47,159,224,0.12)', title: t('系统概览'), desc: t('学生/教师/课程整体统计'), q: '平台的整体选课统计情况如何？' },
    { icon: '⏰', color: '#d97706', bg: 'rgba(217,119,6,0.10)', title: t('选课状态'), desc: t('当前轮次、阶段与开放状态'), q: '当前选课状态如何？' },
    { icon: '📋', color: '#0284c7', bg: 'rgba(2,132,199,0.10)', title: t('选课规则'), desc: t('三轮积分制与毕业学分要求'), q: '选课规则是怎样的？' },
    { icon: '📚', color: '#0d9488', bg: 'rgba(13,148,136,0.10)', title: t('课程目录'), desc: t('全部课程的容量与报名情况'), q: '课程容量和报名情况怎样？' },
  ]
  return [
    { icon: '🎓', color: '#2f9fe0', bg: 'rgba(47,159,224,0.12)', title: t('毕业进度'), desc: t('四大类学分已修/要求/缺口'), q: '我还差多少学分毕业？' },
    { icon: '📋', color: '#0284c7', bg: 'rgba(2,132,199,0.10)', title: t('我的选课'), desc: t('已录取课程、时间与课表'), q: '我的已选课程和课表是什么？' },
    { icon: '🎯', color: '#d97706', bg: 'rgba(217,119,6,0.10)', title: t('意向投分'), desc: t('分析意向单与积分投入策略'), q: '帮我分析我的意向单和投分策略。' },
    { icon: '⚔️', color: '#dc2626', bg: 'rgba(220,38,38,0.10)', title: t('时间冲突'), desc: t('意向课程与已录取课真实核对'), q: '我的意向课程和已录取课程有时间冲突吗？' },
    { icon: '🧭', color: '#0d9488', bg: 'rgba(13,148,136,0.10)', title: t('选课推荐'), desc: t('按学分缺口推荐可补修课程'), q: '根据我的学分缺口，推荐我可以补修的课程。' },
    { icon: '📊', color: '#2f9fe0', bg: 'rgba(47,159,224,0.12)', title: t('成绩绩点'), desc: t('总评、学分加权均分与 GPA'), q: '帮我分析我的成绩和 GPA。' },
    { icon: '💬', color: '#0284c7', bg: 'rgba(2,132,199,0.10)', title: t('智能问答'), desc: t('三轮积分制、候补等选课规则'), q: '学校的选课规则是什么？三轮怎么投分？' },
    { icon: '🗺️', color: '#7c3aed', bg: 'rgba(124,58,237,0.10)', title: t('修读路线规划'), desc: t('按剩余学期规划每学期修什么、修多少'), q: '请结合我的年级、毕业要求、按学期的必修进度和已修课程成绩，帮我规划剩余每个学期应该修读哪些课程（含挂科必修的重修安排与选修补缺），直到满足毕业要求。' },
  ]
})

// ===== 课程中心（activeNav === 'kb'）：全部来自真实接口 =====
// 注意：state 声明放这里，watch 在 activeNav 声明之后统一挂载（见下方 activeNav 定义处）
const kbLoading = ref(false)
const kbLoaded = ref(false)
const kbErr = ref('')
const kbCourses = ref<any[]>([])
const kbCats = ref<string[]>([])
const kbQuery = ref('')
const kbCat = ref('')
const kbOpenId = ref<number | null>(null)
const kbStatus = ref<any>(null)
const kbCfg = ref<any>(null)

/** 首次进入课程中心时加载：课程列表 + 我的选课窗口 + 系统配置 */
async function loadKb() {
  if (kbLoading.value) return
  kbLoading.value = true
  kbErr.value = ''
  try {
    const [c, w, cfg] = await Promise.all([
      http.get('/courses'),
      // 我的选课窗口是学生专属数据，教师/管理员不请求，避免必然 403
      isStudent.value ? http.get('/system/my-window').catch(() => null) : Promise.resolve(null),
      http.get('/system/config').catch(() => null),
    ])
    kbCourses.value = c?.data || []
    kbCats.value = [...new Set(kbCourses.value.map((x: any) => x.category).filter(Boolean))]
    kbStatus.value = w?.data || null
    kbCfg.value = cfg?.data || null
    kbLoaded.value = true
  } catch (e: any) {
    kbErr.value = e?.message || t('网络异常')
  } finally {
    kbLoading.value = false
  }
}

const kbFiltered = computed(() => {
  const k = kbQuery.value.trim().toLowerCase()
  let list = kbCourses.value
  if (k) list = list.filter(c =>
    (c.name || '').toLowerCase().includes(k) ||
    (c.teacherName || '').toLowerCase().includes(k) ||
    (c.courseCode || '').toLowerCase().includes(k))
  if (kbCat.value) list = list.filter(c => c.category === kbCat.value)
  return list
})

function fmtKbTime(t: any) {
  if (!t) return '—'
  return String(t).slice(0, 16).replace('T', ' ')
}

interface Msg { role: 'user' | 'assistant' | 'day'; content: string; images?: string[]; files?: { name: string; url: string; size: number }[]; time?: string; loading?: boolean; streaming?: boolean; retryable?: boolean; personal?: boolean; meta?: any; reasoning?: string; reasoningOpen?: boolean }
interface ChatSession { id: string; title: string; updatedAt: number; messages: Msg[]; history: { role: string; content: string }[] }

// ===== 多会话管理：登录用户存 MySQL，未登录降级 localStorage =====
const sessionList = ref<ChatSession[]>([])
const currentId = ref('')
const messages = ref<Msg[]>([])
const history = ref<{ role: string; content: string }[]>([])
const activeNav = ref<'chat' | 'kb' | 'tools' | 'history'>('chat')
watch(activeNav, v => { if (v === 'kb' && !kbLoaded.value) loadKb() })
const sideHidden = ref(false)

const SESS_KEY = computed(() => 'zhixue_ai_sessions_' + (auth.user?.id || 'guest'))
const dbHistory = computed(() => !!((auth as any).token || localStorage.getItem('zhixue_token')))

function loadLocalSessions() {
  try {
    const raw = localStorage.getItem(SESS_KEY.value)
    const list = raw ? JSON.parse(raw) : []
    sessionList.value = Array.isArray(list) ? list : []
  } catch { sessionList.value = [] }
}

/** 先拉 MySQL；首次登录且库为空时，把浏览器旧会话一次性迁移过去 */
async function loadSessions() {
  loadLocalSessions()
  if (!dbHistory.value) return
  try {
    let r: any = await http.get('/ai/history/sessions')
    let list = (r?.data || []) as any[]
    if (!list.length && sessionList.value.length) {
      await http.post('/ai/history/sessions', {
        sessions: sessionList.value.slice(0, 30).map(s => ({
          id: s.id, title: s.title, updatedAt: s.updatedAt, messages: s.messages
        }))
      })
      r = await http.get('/ai/history/sessions')
      list = (r?.data || []) as any[]
    }
    sessionList.value = list.map((x: any) => ({
      id: String(x.id), title: x.title || t('新对话'),
      updatedAt: Number(x.updatedAt) || Date.now(), messages: [], history: []
    }))
  } catch { /* 后端不可用时保持本地会话 */ }
}

function saveSessions() {
  try { localStorage.setItem(SESS_KEY.value, JSON.stringify(sessionList.value.slice(0, 30))) } catch {}
}

/** 从消息重建发给模型的历史（DB 不单独存 history 字段） */
function rebuildHistory(msgs: Msg[]) {
  return msgs.filter(m => (m.role === 'user' || m.role === 'assistant') && m.content)
    .slice(-16).map(m => ({ role: m.role, content: m.content }))
}

/** 当前会话落库：本地缓存 + MySQL（fire-and-forget，失败静默降级） */
function persistCurrent() {
  const clean = messages.value
    .filter(m => m.role !== 'day' && !m.loading && !m.streaming && !m.retryable
      && (m.content || (m.images && m.images.length)))
  if (!clean.length) return
  const snapMsgs = clean.slice(-40).map(m => ({ ...m }))
  const snapHist = history.value.slice(-16).map(h => ({ ...h }))
  if (!currentId.value) {
    const first = clean.find(m => m.role === 'user')
    currentId.value = 's' + Date.now() + Math.random().toString(36).slice(2, 6)
    sessionList.value.unshift({
      id: currentId.value,
      title: (first?.content || '[图片]').slice(0, 24),
      updatedAt: Date.now(), messages: snapMsgs, history: snapHist,
    })
  } else {
    const s = sessionList.value.find(x => x.id === currentId.value)
    if (s) { s.messages = snapMsgs; s.history = snapHist; s.updatedAt = Date.now() }
    else sessionList.value.unshift({
      id: currentId.value, title: (snapMsgs[0]?.content || '[图片]').slice(0, 24),
      updatedAt: Date.now(), messages: snapMsgs, history: snapHist,
    })
  }
  saveSessions()
  if (dbHistory.value && currentId.value) {
    const s = sessionList.value.find(x => x.id === currentId.value)
    if (s) http.put(`/ai/history/sessions/${encodeURIComponent(s.id)}`,
      { title: s.title, messages: s.messages }).catch(() => null)
  }
}

const recentSessions = computed(() => sessionList.value.slice(0, 6))
const headerTitle = computed(() => {
  if (activeNav.value === 'history') return t('历史记录')
  if (activeNav.value === 'kb') return t('课程中心')
  if (activeNav.value === 'tools') return t('工具箱')
  if (messages.value.length) {
    const s = sessionList.value.find(x => x.id === currentId.value)
    if (s) return s.title
  }
  return t('新对话')
})

function newChat() {
  persistCurrent()
  currentId.value = ''
  messages.value = []
  history.value = []
  error.value = ''
  activeNav.value = 'chat'
  nextTick(() => taRef.value?.focus())
}

async function switchSession(id: string) {
  if (id === currentId.value) { activeNav.value = 'chat'; return }
  persistCurrent()
  if (dbHistory.value) {
    try {
      const r: any = await http.get(`/ai/history/sessions/${encodeURIComponent(id)}`)
      const d = r?.data
      if (d && Array.isArray(d.messages)) {
        currentId.value = id
        messages.value = d.messages.map((m: any) => ({ ...m }))
        history.value = rebuildHistory(messages.value)
        error.value = ''
        activeNav.value = 'chat'
        nextTick(() => scroll(true))
        return
      }
    } catch { /* 降级本地缓存 */ }
  }
  const s = sessionList.value.find(x => x.id === id)
  if (!s) return
  currentId.value = id
  messages.value = s.messages.map(m => ({ ...m }))
  history.value = s.history.length ? s.history.map(h => ({ ...h })) : rebuildHistory(messages.value)
  error.value = ''
  activeNav.value = 'chat'
  nextTick(() => scroll(true))
}

async function deleteSession(id: string) {
  try { await ElMessageBox.confirm(t('删除后该会话记录无法恢复，确定删除？'), t('删除会话'), { type: 'warning', confirmButtonText: t('确定'), cancelButtonText: t('取消') }) } catch { return }
  sessionList.value = sessionList.value.filter(x => x.id !== id)
  if (currentId.value === id) { currentId.value = ''; messages.value = []; history.value = [] }
  saveSessions()
  if (dbHistory.value) http.delete(`/ai/history/sessions/${encodeURIComponent(id)}`).catch(() => null)
}

function clear() {
  if (!messages.value.length) return
  messages.value = []; history.value = []; error.value = ''
  if (currentId.value) {
    const id = currentId.value
    sessionList.value = sessionList.value.filter(x => x.id !== id)
    currentId.value = ''
    if (dbHistory.value) http.delete(`/ai/history/sessions/${encodeURIComponent(id)}`).catch(() => null)
  }
  saveSessions()
}

function fmtRecent(t: number) {
  const d = new Date(t), now = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  if (d.toDateString() === now.toDateString()) return `${p(d.getHours())}:${p(d.getMinutes())}`
  if (d.getFullYear() === now.getFullYear()) return `${p(d.getMonth() + 1)}-${p(d.getDate())}`
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

function toggleSide() {
  sideHidden.value = !sideHidden.value
  localStorage.setItem('zhixue_ai_side', sideHidden.value ? '1' : '0')
}

function fillQuestion(q: string) {
  input.value = q
  activeNav.value = 'chat'
  nextTick(() => { autoResize(); taRef.value?.focus() })
}

const lastAssistantIdx = computed(() => {
  for (let i = messages.value.length - 1; i >= 0; i--) {
    if (messages.value[i].role === 'assistant') return i
  }
  return -1
})

const copied = ref(false)
async function copyMsg(m: Msg) {
  try {
    await navigator.clipboard.writeText(m.content)
    copied.value = true
    setTimeout(() => (copied.value = false), 1500)
  } catch {
    // clipboard 不可用时的降级
    const ta = document.createElement('textarea')
    ta.value = m.content; document.body.appendChild(ta); ta.select()
    try { document.execCommand('copy'); copied.value = true; setTimeout(() => (copied.value = false), 1500) } catch {}
    document.body.removeChild(ta)
  }
}

function regenerate() {
  if (loading.value) return
  // 移除最后一条 AI 回复，重发其对应的用户提问
  const la = lastAssistantIdx.value
  if (la < 0) return
  const prevUser = [...messages.value.slice(0, la)].reverse().find(m => m.role === 'user')
  if (!prevUser) return
  messages.value.splice(la, 1)
  history.value = history.value.filter((h, idx) => !(h.role === 'assistant' && idx === history.value.length - 1))
  send(prevUser.content)
}

function onMsgScroll() { /* 预留：滚动跟随由 scroll() 内部判断 */ }

const input = ref('')
const loading = ref(false)
const streamingNow = ref(false)
const error = ref('')

/** 3D 机器人状态：把 AI 运行阶段映射为可视反馈，而非纯装饰 */
const botState = computed(() => {
  if (error.value) return 'error' as const
  if (streamingNow.value) return 'streaming' as const
  if (loading.value) return 'thinking' as const
  return 'idle' as const
})
const botStateText = computed(() => ({
  idle: 'CORE / 待机',
  thinking: 'CORE / 分析中',
  streaming: 'CORE / 生成中',
  error: 'CORE / 连接异常',
})[botState.value])

const messagesRef = ref<HTMLElement | null>(null)
const taRef = ref<HTMLTextAreaElement | null>(null)
let abortCtrl: AbortController | null = null

function fmtTime() {
  return new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false })
}

/** Markdown 渲染：代码块/行内码/粗斜体/列表/表格/标题/链接（流式安全：先转义 HTML） */
function escHtml(s: string) {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

/** 行级解析表格：|表头| |---|---| | 行1 | 行2 | */
function renderTables(s: string): string {
  const lines = s.split('\n')
  const out: string[] = []
  for (let i = 0; i < lines.length; i++) {
    const cur = lines[i]
    const nxt = lines[i + 1] || ''
    const isSep = /^\s*\|?[\s\-:|]*-[\s\-:|]*\|[\s\-:|]*$/.test(nxt) && nxt.includes('|') && nxt.includes('-')
    if (cur.includes('|') && isSep) {
      const head = cur.trim().replace(/^\||\|$/g, '').split('|').map(c => c.trim())
      const rows: string[][] = []
      let j = i + 2
      while (j < lines.length && lines[j].includes('|') && lines[j].trim() !== '') {
        rows.push(lines[j].trim().replace(/^\||\|$/g, '').split('|').map(c => c.trim()))
        j++
      }
      if (rows.length) {
        out.push('<table class="md-table"><thead><tr>' +
          head.map(c => `<th>${c}</th>`).join('') + '</tr></thead><tbody>' +
          rows.map(r => '<tr>' + r.map(c => `<td>${c}</td>`).join('') + '</tr>').join('') +
          '</tbody></table>')
        i = j - 1
        continue
      }
    }
    out.push(cur)
  }
  return out.join('\n')
}

function renderMd(text: string) {
  if (!text) return ''
  let s = escHtml(text)
  s = renderTables(s)
  s = s.replace(/```([\s\S]*?)```/g, (_, code) => `<pre class="md-code">${code.trim()}</pre>`)
  s = s.replace(/`([^`\n]+)`/g, (_, c) => `<code class="md-inline">${c}</code>`)
  s = s.replace(/^#{1,4}\s+(.+)$/gm, '<div class="md-h">$1</div>')
  s = s.replace(/\*\*([^*\n]+)\*\*/g, '<b>$1</b>')
  s = s.replace(/\*([^*\n]+)\*/g, '<i>$1</i>')
  s = s.replace(/\[([^\]]+)\]\((https?:[^)\s]+)\)/g, '<a href="$2" target="_blank" rel="noopener">$1</a>')
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

/** 滚动跟随：仅当用户接近底部时自动滚，翻历史时不打扰 */
function scroll(force = false) {
  const el = messagesRef.value
  if (!el) return
  const nearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 120
  if (force || nearBottom) el.scrollTop = el.scrollHeight
}

async function send(text?: any) {
  const raw = typeof text === 'string' ? text : ''
  const content = (raw || input.value || '').trim()
  if ((!content && !pendingImages.value.length && !pendingFiles.value.length) || loading.value) return
  const imgs = pendingImages.value.slice()
  pendingImages.value = []
  const atts = pendingFiles.value.slice()
  pendingFiles.value = []
  activeNav.value = 'chat'
  input.value = ''; error.value = ''; autoResize()
  // 附件正文注入 AI 上下文（气泡展示保持干净，文件链接走 files 字段）
  let aiContent = content
  for (const f of atts) {
    aiContent += `\n\n[附件 ${f.name}（${fmtSize(f.size)}）]`
    if (f.excerpt) aiContent += '\n' + f.excerpt
  }
  messages.value.push({ role: 'user', content, time: fmtTime(), images: imgs.length ? imgs : undefined, files: atts.length ? atts : undefined })
  history.value.push({ role: 'user', content: aiContent || (imgs.length ? '[图片]' : '') })
  if (history.value.length > 16) history.value = history.value.slice(-16)

  // 必须用 reactive：push 进 ref 数组后若仍持有普通对象，改字段不会触发渲染
  const tail = reactive<Msg>({ role: 'assistant', content: '', loading: true, personal: isStudent.value && !!auth.user })
  messages.value.push(tail)
  loading.value = true
  await nextTick(); scroll(true)

  // 流式请求（失败自动回退非流式 /chat）
  const sel = selection()
  abortCtrl = new AbortController()
  let streamed = false
  try {
    const token = (auth as any).token || localStorage.getItem('zhixue_token') || ''
    const resp = await fetch('/api/ai/chat/stream', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
      body: JSON.stringify({ message: aiContent, history: history.value.slice(0, -1), role: auth.user?.role,
                             channel: sel.channel, model: sel.model, images: imgs }),
      signal: abortCtrl.signal
    })
    if (!resp.ok || !resp.body) throw new Error('HTTP ' + resp.status)
    tail.loading = false; tail.streaming = true; streamingNow.value = true

    const reader = resp.body.getReader()
    const dec = new TextDecoder()
    let buf = ''
    let finished = false
    while (!finished) {
      const { done, value } = await reader.read()
      if (done) break
      buf += dec.decode(value, { stream: true })
      // SSE 事件按空行分隔
      const events = buf.split('\n\n')
      buf = events.pop() || ''
      for (const ev of events) {
        const lines = ev.split('\n')
        // 事件名：Spring SseEmitter 输出 "event:delta"（冒号后无空格）
        const evName = (lines.find(l => l.startsWith('event:')) || '').slice(6).trim()
        // data 只去掉 "data:" 前缀与其后一个可选空格，保留内容里的空格
        const data = lines.filter(l => l.startsWith('data:')).map(l => l.slice(5).replace(/^ /, '')).join('\n')
        if (!evName && !data) continue
        if (evName === 'meta') {
          try { tail.meta = JSON.parse(data) } catch { tail.meta = null }
          continue
        }
        // 思考过程（deepseek 思考模型的 reasoning_content）：流式期间默认展开，可手动收起
        if (evName === 'reasoning') {
          tail.reasoning = (tail.reasoning || '') + data
          if (tail.reasoningOpen !== false) tail.reasoningOpen = true
          scroll()
          continue
        }
        if (evName === 'error') { throw new Error(data || t('AI 服务异常')) }
        if (evName === 'done') { tail.reasoningOpen = false; finished = true; break }
        // delta 事件（无 event: 名的裸 data 也按 delta 处理）
        if (evName === 'delta' || (!evName && data)) {
          streamed = true
          tail.content += data
          scroll()
        }
      }
    }
    if (!streamed) throw new Error(t('AI 返回为空'))
    tail.streaming = false
    history.value.push({ role: 'assistant', content: tail.content })
  } catch (e: any) {
    tail.streaming = false
    if (e?.name === 'AbortError') {
      // 用户主动停止：保留已生成的部分
      if (streamed && tail.content) history.value.push({ role: 'assistant', content: tail.content })
    } else if (!streamed) {
      // 流式不可用 → 回退非流式接口
      try {
        const r: any = await http.post('/ai/chat', {
          message: content, history: history.value.slice(0, -1), role: auth.user?.role,
          channel: sel.channel, model: sel.model, images: imgs
        })
        tail.content = r?.data?.reply || r?.reply || t('（AI 返回为空）')
        const d: any = r?.data?.data ?? r?.data ?? r ?? {}
        tail.meta = {
          intentCn: d.intentCn || t('智能识别'), modelLabel: d.modelLabel || '',
          sources: d.sources || [], courses: d.courses || [],
          pages: d.pages || [], visitedPages: d.visitedPages || [], webSearch: !!d.webSearch
        }
        history.value.push({ role: 'assistant', content: tail.content })
      } catch (e2: any) {
        tail.content = t('⚠️ AI 服务暂不可用：{msg}', { msg: e2?.message || t('未知错误') })
        error.value = tail.content
        tail.retryable = true
        if (history.value.length) history.value.pop()
      }
    } else {
      // 流式中断但已有内容：保留
      if (tail.content) history.value.push({ role: 'assistant', content: tail.content })
    }
  } finally {
    tail.loading = false; tail.streaming = false; loading.value = false; streamingNow.value = false
    abortCtrl = null
    scroll(); persistCurrent()
  }
}

function stop() { abortCtrl?.abort() }

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

function onResize() { /* 侧栏折叠态由 CSS 媒体查询控制 */ }

onMounted(() => {
  auth.loadFromStorage()
  loadSessions()
  loadAiOptions()
  sideHidden.value = window.innerWidth < 900 || localStorage.getItem('zhixue_ai_side') === '1'
  window.addEventListener('resize', onResize)
  setTimeout(() => taRef.value?.focus(), 100)
})
onBeforeUnmount(() => {
  abortCtrl?.abort()
  window.removeEventListener('resize', onResize)
})
</script>

<style scoped>
.chat-page { height: calc(100vh - 110px); display: flex; flex-direction: row; background: var(--card); border: 1px solid var(--border); border-radius: 14px; overflow: hidden; position: relative; }

/* ===== 左侧导航 ===== */
.ai-side { width: 240px; flex-shrink: 0; display: flex; flex-direction: column; border-right: 1px solid var(--border); background: linear-gradient(180deg, rgba(43,177,135,0.06), rgba(56,189,248,0.05) 60%, transparent), var(--card); transition: width .22s ease; overflow: hidden; white-space: nowrap; }
.ai-side.collapsed { width: 0; border-right-width: 0; }
.ai-side-logo { display: flex; align-items: center; gap: 10px; padding: 16px 16px 10px; }
.ai-logo-icon { width: 34px; height: 34px; border-radius: 10px; flex-shrink: 0; background: linear-gradient(135deg, #16a34a, #38bdf8); color: #fff; font-weight: 700; font-size: 15px; display: flex; align-items: center; justify-content: center; }
.ai-logo-text { font-size: 15px; font-weight: 700; color: var(--text); }
.ai-new-chat { margin: 6px 12px 10px; padding: 10px 14px; display: flex; align-items: center; gap: 8px; border: none; border-radius: 10px; background: linear-gradient(135deg, #2bb187, #38bdf8); color: #fff; font-size: 14px; font-weight: 600; cursor: pointer; transition: transform .15s, box-shadow .2s, filter .2s; }
.ai-new-chat:hover { transform: translateY(-1px); box-shadow: 0 6px 16px rgba(43,177,135,.32); filter: brightness(1.04); }
.ai-new-chat svg { width: 16px; height: 16px; flex-shrink: 0; }
.ai-side-nav { display: flex; flex-direction: column; gap: 2px; padding: 0 8px; }
.ai-nav-item { display: flex; align-items: center; gap: 10px; padding: 9px 12px; border: none; border-radius: 9px; background: transparent; color: var(--text-light); font-size: 13.5px; cursor: pointer; text-align: left; transition: background .15s, color .15s; font-family: inherit; }
.ai-nav-item svg { width: 17px; height: 17px; flex-shrink: 0; }
.ai-nav-item:hover { background: var(--bg); color: var(--text); }
.ai-nav-item.active { background: rgba(43,177,135,0.12); color: var(--primary, #2bb187); font-weight: 600; }
/* 最近对话 */
.ai-recent { flex: 1; overflow-y: auto; padding: 10px 8px 4px; border-top: 1px solid var(--border); margin-top: 8px; min-height: 0; }
.ai-recent-title { font-size: 12px; color: var(--text-lighter); padding: 2px 10px 6px; }
.ai-recent-item { width: 100%; display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 8px 10px; border: none; border-radius: 8px; background: transparent; cursor: pointer; font-family: inherit; transition: background .15s; }
.ai-recent-item:hover { background: var(--bg); }
.ai-recent-item.current { background: rgba(56,189,248,0.10); }
.ai-recent-name { flex: 1; font-size: 13px; color: var(--text); text-align: left; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ai-recent-time { font-size: 11.5px; color: var(--text-lighter); flex-shrink: 0; }
.ai-side-foot { border-top: 1px solid var(--border); padding: 10px 12px; }
.ai-side-user { display: flex; align-items: center; gap: 9px; }
.ai-user-avatar { width: 30px; height: 30px; border-radius: 50%; flex-shrink: 0; background: var(--primary-deep); color: #0a0e12; font-size: 13px; font-weight: 700; display: flex; align-items: center; justify-content: center; }
.ai-user-name { font-size: 13px; font-weight: 600; color: var(--text); }
.ai-user-role { font-size: 11.5px; color: var(--text-lighter); }
.ai-recent::-webkit-scrollbar { width: 5px; }
.ai-recent::-webkit-scrollbar-thumb { background: var(--border); border-radius: 3px; }

/* 侧栏开关按钮 */
.ai-side-toggle { width: 30px; height: 30px; border: 1px solid var(--border); border-radius: 8px; background: transparent; color: var(--text-light); cursor: pointer; display: flex; align-items: center; justify-content: center; transition: all .15s; flex-shrink: 0; }
.ai-side-toggle:hover { color: var(--primary, #74e4c4); border-color: var(--primary, #74e4c4); }
.ai-side-toggle svg { width: 15px; height: 15px; }
.chat-header-actions { display: flex; align-items: center; gap: 8px; }

/* ===== 主工作区 ===== */
.ai-main { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.chat-header { display: flex; align-items: center; justify-content: space-between; padding: 8px 18px; border-bottom: 1px solid var(--border); background: linear-gradient(90deg, rgba(116,228,196,.07), rgba(116,228,196,.03) 60%, transparent); }
.chat-title { display: flex; align-items: center; gap: 10px; font-size: 15px; font-weight: 600; min-width: 0; }
.chat-title-icon { font-size: 17px; color: var(--ark-y, #74e4c4); font-family: var(--ark-mono, monospace); }
.chat-title-text { color: var(--text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.chat-status { font-size: 12px; padding: 2px 8px; border-radius: 2px; background: rgba(116,228,196,.1); color: var(--ark-y, #74e4c4); font-weight: 500; display: flex; align-items: center; gap: 5px; flex-shrink: 0; font-family: var(--ark-mono, monospace); }
.chat-status::before { content: ''; width: 5px; height: 5px; border-radius: 50%; background: var(--ark-y, #74e4c4); animation: pulse 2.2s ease-in-out infinite; }
@keyframes pulse { 0%,100%{opacity:1} 50%{opacity:.4} }
.chat-clear-btn { font-size: 12px; padding: 5px 12px; border-radius: 20px; background: var(--bg); border: 1px solid var(--border); color: var(--text-light); cursor: pointer; transition: all .2s; white-space: nowrap; }
.chat-clear-btn:hover:not(:disabled) { border-color: var(--primary); color: var(--primary); }

/* 自选通道 / 模型下拉：嵌在输入框右端、发送按钮左侧 */
.ai-model-select {
  flex: 0 0 auto; align-self: center;
  max-width: 150px; height: 30px; padding: 3px 8px; border-radius: 8px;
  background: rgba(43,177,135,0.08); border: 1px solid var(--border); color: var(--text);
  font: inherit; font-size: 12px; cursor: pointer; outline: none; white-space: nowrap;
  transition: border-color .2s, color .2s;
}
.ai-model-select:hover, .ai-model-select:focus { border-color: var(--primary); color: var(--primary); }
.ai-model-select optgroup, .ai-model-select option { background: var(--card); color: var(--text); }
@media (max-width: 720px) { .ai-model-select { max-width: 96px; font-size: 11px; padding: 2px 4px; } }
.chat-clear-btn:disabled { opacity: .4; cursor: not-allowed; }
.chat-messages { flex: 1; overflow-y: auto; padding: 20px 24px; display: flex; flex-direction: column; gap: 16px; background: var(--card); min-height: 0; }

/* ===== 欢迎首页 ===== */
.ai-home { margin: auto; width: 100%; max-width: 860px; padding: 10px 4px; position: relative; }
/* 背景光晕：青玉 + 暖金，与学域星图主题一致 */
.ai-home-glow { position: absolute; border-radius: 50%; filter: blur(60px); pointer-events: none; z-index: 0; }
.ai-home-glow.g1 { top: -50px; right: -70px; width: 400px; height: 400px; background: radial-gradient(circle, rgba(116,228,196,.16), transparent 65%); }
.ai-home-glow.g2 { top: 110px; left: -90px; width: 340px; height: 340px; background: radial-gradient(circle, rgba(116,228,196,.09), transparent 65%); }
.ai-home-glow.g3 { bottom: -40px; right: 130px; width: 300px; height: 300px; background: radial-gradient(circle, rgba(231,197,140,.10), transparent 65%); }
/* 左问候 + 右机器人 */
.ai-home-hero { position: relative; z-index: 1; display: flex; align-items: center; justify-content: space-between; gap: 24px; margin-bottom: 28px; }
.ai-home-greet { text-align: left; flex: 1; min-width: 0; }
.ai-home-hi { font-size: 26px; font-weight: 700; color: var(--text); letter-spacing: .3px; }
.ai-home-wave { display: inline-block; margin-left: 8px; transform-origin: 70% 70%; animation: ai-wave 2.4s ease-in-out infinite; }
@keyframes ai-wave { 0%,60%,100% { transform: rotate(0); } 10% { transform: rotate(16deg); } 20% { transform: rotate(-8deg); } 30% { transform: rotate(12deg); } 40% { transform: rotate(-4deg); } 50% { transform: rotate(8deg); } }
.ai-home-hi2 { margin-top: 8px; font-size: 30px; font-weight: 800; color: var(--text); letter-spacing: .5px; }
.ai-home-brand-line { margin-top: 14px; }
.ai-home-brand { display: inline-block; font-size: 12px; font-weight: 600; color: var(--ark-y, #74e4c4); background: rgba(116,228,196,.10); padding: 3px 11px; border-radius: 2px; border-left: 2px solid var(--ark-y, #74e4c4); font-family: var(--ark-mono, monospace); letter-spacing: .5px; }
.ai-home-sub { margin-top: 10px; font-size: 13.5px; color: var(--text-light); line-height: 1.6; max-width: 420px; }
/* ===== 3D 机器人：学域星图核心（Three.js 组件 Bot3D.vue）===== */
.ai-mascot { position: relative; width: 210px; height: 224px; flex-shrink: 0; }
.mascot-corner { position: absolute; width: 18px; height: 18px; border-color: rgba(116,228,196,.45); border-style: solid; z-index: 2; pointer-events: none; }
.mascot-corner.tl { top: 0; left: 0; border-width: 1px 0 0 1px; }
.mascot-corner.br { bottom: 0; right: 0; border-width: 0 1px 1px 0; }
.bot-state-tag { position: absolute; left: 50%; bottom: 4px; transform: translateX(-50%); z-index: 2; display: flex; align-items: center; gap: 6px; padding: 3px 9px; white-space: nowrap; font: 600 10px var(--ark-mono, monospace); letter-spacing: 1px; color: var(--ark-y, #74e4c4); background: rgba(7,10,14,.86); border: 1px solid rgba(116,228,196,.35); }
.bot-state-tag i { width: 5px; height: 5px; border-radius: 50%; background: var(--ark-y, #74e4c4); box-shadow: 0 0 8px var(--ark-y, #74e4c4); animation: pulse 1.8s ease-in-out infinite; }
.bot-state-tag.is-thinking { color: #9df0d7; border-color: rgba(255,224,77,.6); }
.bot-state-tag.is-thinking i { animation-duration: .55s; }
.bot-state-tag.is-streaming i { animation-duration: 1s; }
.bot-state-tag.is-error { color: var(--ark-red, #ff4a3d); border-color: rgba(255,74,61,.55); }
.bot-state-tag.is-error i { background: var(--ark-red, #ff4a3d); box-shadow: 0 0 8px var(--ark-red, #ff4a3d); }
/* 对话中：顶栏小号 3D 状态头像 */
.bot-mini { position: relative; width: 44px; height: 44px; flex-shrink: 0; border: 1px solid rgba(116,228,196,.3); background: radial-gradient(circle at 50% 40%, rgba(116,228,196,.10), transparent 70%); overflow: hidden; }
.bot-mini::after { content: ''; position: absolute; left: 0; right: 0; top: 0; height: 1px; background: linear-gradient(90deg, transparent, var(--ark-y, #74e4c4), transparent); }
/* 主题适配：暗色光晕略收、浅色整体压淡 */
html[data-theme="dark"] .ai-home-glow { opacity: .8; }
html:not([data-theme="dark"]) .ai-home-glow { opacity: .5; }
/* 功能卡片 */
.ai-cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 12px; }
.ai-card { position: relative; display: flex; flex-direction: column; align-items: flex-start; gap: 6px; padding: 16px 16px 14px; border: 1px solid var(--border); border-radius: 14px; background: var(--card); cursor: pointer; font-family: inherit; text-align: left; transition: transform .18s ease, box-shadow .18s ease, border-color .18s ease; overflow: hidden; }
.ai-card:hover { transform: translateY(-3px); border-color: rgba(116,228,196,.45); box-shadow: 0 10px 24px rgba(116,228,196,.10); }
.ai-card:hover .ai-card-arrow { opacity: 1; transform: translateX(0); }
.ai-card-icon { width: 38px; height: 38px; border-radius: 10px; display: flex; align-items: center; justify-content: center; font-size: 19px; margin-bottom: 2px; }
.ai-card-title { font-size: 14.5px; font-weight: 700; color: var(--text); }
.ai-card-desc { font-size: 12.5px; color: var(--text-light); line-height: 1.5; }
.ai-card-arrow { position: absolute; right: 14px; bottom: 12px; font-size: 15px; color: var(--primary, #74e4c4); opacity: 0; transform: translateX(-6px); transition: all .18s ease; }
/* 快捷提问 */
.ai-quick-block { margin-top: 24px; }
.ai-quick-label { font-size: 12.5px; color: var(--text-lighter); margin-bottom: 10px; padding-left: 4px; }
.quick-chips { display: flex; gap: 8px; flex-wrap: wrap; }
.chip { padding: 7px 15px; border: 1px solid var(--border); border-radius: 999px; background: transparent; color: var(--text-light); cursor: pointer; font-size: 13px; transition: all .2s; font-family: inherit; }
.chip:hover { border-color: var(--primary); color: var(--primary); background: rgba(43,177,135,0.08); }

/* ===== 面板视图（历史/知识库/工具箱） ===== */
.ai-panel { flex: 1; overflow-y: auto; padding: 22px 26px; min-height: 0; }
.ai-panel-head { margin-bottom: 16px; }
.ai-panel-title { font-size: 17px; font-weight: 700; color: var(--text); }
.ai-panel-sub { font-size: 12.5px; color: var(--text-lighter); margin-top: 4px; }
.ai-empty { padding: 46px 20px; text-align: center; font-size: 13.5px; color: var(--text-light); line-height: 1.8; border: 1px dashed var(--border); border-radius: 12px; background: var(--bg); }

/* ===== 课程中心 ===== */
.kb-status { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 18px; }
.kb-status-item { display: flex; align-items: baseline; gap: 6px; padding: 7px 13px; border-radius: 10px;
  background: var(--card); border: 1px solid var(--border); font-size: 12.5px; }
.kb-k { color: var(--text-lighter, var(--text-light)); }
.kb-status-item b { font-weight: 700; color: var(--text); font-variant-numeric: tabular-nums; }
.kb-status-item b.kb-on { color: #16a34a; }
.kb-status-item b.kb-off { color: #d97706; }
.kb-sec-title { display: flex; align-items: center; justify-content: space-between; gap: 10px;
  font-size: 14px; font-weight: 700; color: var(--text); margin: 16px 0 10px; }
.kb-jump { border: none; cursor: pointer; font-family: inherit; font-size: 12.5px; font-weight: 600;
  color: var(--primary, #2bb187); background: rgba(43,177,135,0.10); padding: 5px 12px; border-radius: 999px; transition: background .18s; }
.kb-jump:hover { background: rgba(43,177,135,0.2); }
.kb-toolbar { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; margin-bottom: 12px; }
.kb-search { flex: 1; min-width: 200px; padding: 8px 13px; border-radius: 10px; border: 1px solid var(--border);
  background: var(--card); color: var(--text); font-family: inherit; font-size: 13px; outline: none; transition: border-color .18s; }
.kb-search:focus { border-color: rgba(43,177,135,0.55); }
.kb-cats { display: flex; gap: 6px; flex-wrap: wrap; }
.kb-cats button { border: 1px solid var(--border); background: var(--card); color: var(--text-light);
  font-family: inherit; font-size: 12px; padding: 5px 11px; border-radius: 999px; cursor: pointer; transition: all .18s; }
.kb-cats button.active { color: #fff; background: var(--primary, #2bb187); border-color: var(--primary, #2bb187); font-weight: 600; }
.kb-list { display: flex; flex-direction: column; gap: 8px; }
.kb-course { border: 1px solid var(--border); border-radius: 12px; background: var(--card); cursor: pointer; transition: border-color .18s, box-shadow .18s; }
.kb-course:hover { border-color: rgba(43,177,135,0.45); box-shadow: 0 4px 14px rgba(43,177,135,0.08); }
.kb-course.open { border-color: rgba(43,177,135,0.55); }
.kb-row { display: flex; align-items: center; gap: 12px; padding: 11px 14px; flex-wrap: wrap; }
.kb-name { flex: 1 1 220px; min-width: 0; font-size: 13.5px; font-weight: 700; color: var(--text);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.kb-tag { display: inline-block; font-size: 11px; font-weight: 600; padding: 1px 8px; border-radius: 999px;
  background: rgba(2,132,199,0.10); color: #0284c7; margin-left: 6px; vertical-align: 1px; }
.kb-meta { flex: 1 1 200px; min-width: 0; font-size: 12px; color: var(--text-light);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.kb-cap { flex: 0 0 auto; font-size: 12px; color: var(--text-light); font-variant-numeric: tabular-nums; }
.kb-cap-num { font-size: 14px; font-weight: 800; color: var(--primary, #2bb187); }
.kb-detail { border-top: 1px dashed var(--border); padding: 10px 14px 12px; }
.kb-detail-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 6px 14px; font-size: 12.5px; color: var(--text); }
.kb-detail-grid .kb-k { display: block; font-size: 11.5px; color: var(--text-lighter, var(--text-light)); }
.kb-intro { margin-top: 8px; font-size: 12.5px; line-height: 1.7; color: var(--text-light); }
.kb-intro.kb-dim { color: var(--text-lighter, var(--text-light)); font-style: italic; }
.kb-faq { display: flex; flex-direction: column; gap: 8px; margin-bottom: 10px; }
.kb-qa { border: 1px solid var(--border); border-radius: 12px; background: var(--card); padding: 11px 14px; }
.kb-qa b { display: block; font-size: 13px; color: var(--text); margin-bottom: 4px; }
.kb-qa p { margin: 0; font-size: 12.5px; line-height: 1.7; color: var(--text-light); }
.ai-history-list { display: flex; flex-direction: column; gap: 8px; }
.ai-history-item { display: flex; align-items: center; gap: 10px; padding: 12px 16px; border: 1px solid var(--border); border-radius: 12px; background: var(--card); transition: border-color .15s, background .15s; }
.ai-history-item:hover { border-color: rgba(43,177,135,0.4); }
.ai-history-item.current { background: rgba(43,177,135,0.07); border-color: rgba(43,177,135,0.35); }
.ai-history-main { flex: 1; min-width: 0; cursor: pointer; }
.ai-history-title { font-size: 14px; font-weight: 600; color: var(--text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ai-history-meta { font-size: 12px; color: var(--text-lighter); margin-top: 3px; }
.ai-history-del { flex-shrink: 0; padding: 4px 12px; border-radius: 8px; border: 1px solid var(--border); background: transparent; color: var(--text-light); font-size: 12px; cursor: pointer; transition: all .15s; font-family: inherit; }
.ai-history-del:hover { border-color: #ef4444; color: #ef4444; background: rgba(239,68,68,0.06); }

/* ===== 消息 ===== */
.chat-msg { display: flex; gap: 12px; align-items: flex-start; animation: msgIn .25s ease; }
.chat-msg.user { flex-direction: row-reverse; }
@keyframes msgIn { from { opacity: 0; transform: translateY(6px) } to { opacity: 1; transform: none } }
.chat-avatar { width: 36px; height: 36px; border-radius: 10px; flex-shrink: 0; background: linear-gradient(135deg, #16a34a, #22c55e); display: flex; align-items: center; justify-content: center; color: #fff; font-weight: 600; font-size: 14px; }
.chat-msg.user .chat-avatar { background: linear-gradient(135deg, #2bb187, #38bdf8); }
.chat-bubble-wrap { max-width: 72%; display: flex; flex-direction: column; gap: 4px; min-width: 0; }
.chat-msg.user .chat-bubble-wrap { align-items: flex-end; }
.chat-sender { font-size: 12px; color: var(--text-lighter); padding: 0 4px; }
.chat-bubble { padding: 12px 16px; border-radius: 14px; font-size: 14px; line-height: 1.65; background: var(--bg); border: 1px solid var(--border); color: var(--text); word-break: break-word; white-space: pre-wrap; border-top-left-radius: 4px; }
.chat-bubble.user { background: linear-gradient(135deg, #2bb187, #38bdf8); border-color: transparent; color: #fff; border-top-left-radius: 14px; border-top-right-radius: 4px; }
.chat-time { font-size: 12px; color: var(--text-lighter); padding: 0 4px; }
/* 消息操作（复制/重新生成） */
.chat-acts { display: flex; gap: 6px; padding: 0 4px; }
.chat-act { padding: 3px 10px; border-radius: 7px; border: 1px solid var(--border); background: transparent; color: var(--text-light); font-size: 12px; cursor: pointer; transition: all .15s; font-family: inherit; }
.chat-act:hover:not(:disabled) { border-color: var(--primary, #2bb187); color: var(--primary, #2bb187); }
.chat-act:disabled { opacity: .45; cursor: not-allowed; }
/* 识别结果条：让用户看到 AI 理解成了什么、参考了哪些数据 */
.chat-meta { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; padding: 0 4px; }
.cm-tag { font-size: 12px; font-weight: 600; padding: 2px 8px; border-radius: 999px; background: rgba(43,177,135,0.12); color: var(--primary, #2bb187); }
.cm-tag.web { background: rgba(64,150,255,0.12); color: #4096ff; }
.cm-tag.model { background: rgba(148,120,230,0.14); color: #8b6fd4; font-weight: 500; }
/* 思考过程折叠块 */
.chat-reasoning { margin: 0 0 8px; border-left: 3px solid rgba(43,177,135,0.35); background: rgba(43,177,135,0.06); border-radius: 8px; overflow: hidden; }
.chat-reasoning .cr-toggle { all: unset; cursor: pointer; display: block; box-sizing: border-box; width: 100%; padding: 6px 10px; font-size: 12px; font-weight: 600; color: var(--primary, #2bb187); user-select: none; }
.chat-reasoning .cr-body { margin: 0; padding: 2px 10px 8px; font-family: inherit; font-size: 12px; line-height: 1.6; color: var(--text-soft, #6b7a70); white-space: pre-wrap; max-height: 260px; overflow-y: auto; }
.cm-src { font-size: 12px; color: var(--text-lighter); }
.cm-tag.pages { background: rgba(96,165,250,.14); color: #5b9bd5; }
.chat-pages { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; padding: 5px 4px 0; }
.cp-label { font-size: 12px; color: var(--text-lighter); }
.cp-chip { display: inline-flex; align-items: center; gap: 4px; font-size: 12px; line-height: 1.5;
  padding: 2px 10px; border-radius: 999px; cursor: pointer;
  border: 1px solid rgba(43,177,135,.32); background: rgba(43,177,135,.10);
  color: var(--primary, #2bb187); transition: background .18s, transform .18s; }
.cp-chip:hover { background: rgba(43,177,135,.22); transform: translateY(-1px); }
.cp-arrow { font-size: 11px; opacity: .75; }
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
.chat-day-sep { text-align: center; margin: 18px 0 14px; font-size: 12px; color: var(--text-lighter); display: flex; align-items: center; gap: 12px; }
.chat-day-sep::before, .chat-day-sep::after { content: ''; flex: 1; height: 1px; background: linear-gradient(90deg, transparent, var(--border), transparent); }
.chat-error { margin: 8px auto; padding: 10px 16px; max-width: 480px; background: rgba(239,68,68,0.08); border: 1px solid rgba(239,68,68,0.2); border-radius: 10px; color: var(--danger); font-size: 13px; text-align: center; display: flex; gap: 10px; align-items: center; justify-content: center; flex-wrap: wrap; }
.chat-retry { padding: 4px 12px; border-radius: 6px; border: 1px solid var(--danger); background: transparent; color: var(--danger); cursor: pointer; font-size: 12px; font-family: inherit; }
.chat-retry:hover { background: var(--danger); color: #fff; }

/* ===== 输入区 ===== */
.chat-input-area { display: flex; gap: 10px; padding: 14px 18px; border-top: 1px solid var(--border); align-items: flex-end; background: var(--card); }
.chat-input-wrap { flex: 1; display: flex; align-items: center; gap: 8px; border: 1.5px solid var(--border); border-radius: 12px; padding: 6px 12px; background: var(--bg); transition: border-color .2s, box-shadow .2s; }
.chat-input-wrap:focus-within { border-color: var(--primary); box-shadow: 0 0 0 3px rgba(43,177,135,0.12); }
.chat-input-wrap textarea { flex: 1; min-width: 0; width: 100%; border: none; outline: none; resize: none; font-size: 14px; background: transparent; color: var(--text); font-family: inherit; line-height: 1.55; max-height: 140px; }
.chat-send { width: 44px; height: 44px; border-radius: 12px; border: none; cursor: pointer; background: linear-gradient(135deg, #2bb187, #38bdf8); color: #fff; font-weight: 600; display: flex; align-items: center; justify-content: center; transition: transform .15s, box-shadow .2s, opacity .2s; flex-shrink: 0; }
.chat-send:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 4px 14px rgba(43,177,135,.35); }
.chat-send:disabled { opacity: .5; cursor: not-allowed; }

/* 识图：图片按钮 / 待发预览 / 气泡内图片 */
.chat-input-col { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 8px; }
.chat-input-row { display: flex; align-items: flex-end; gap: 8px; }
.chat-img-input { display: none; }
.chat-img-btn {
  width: 44px; height: 44px; flex: 0 0 auto; border-radius: 12px; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  background: var(--bg); border: 1px solid var(--border); color: var(--text-light);
  transition: border-color .2s, color .2s;
}
.chat-img-btn svg { width: 20px; height: 20px; }
.chat-img-btn:hover:not(:disabled) { border-color: var(--primary); color: var(--primary); }
.chat-img-btn:disabled { opacity: .45; cursor: not-allowed; }
.chat-img-strip { display: flex; gap: 8px; flex-wrap: wrap; }
.chat-img-thumb { position: relative; width: 64px; height: 64px; border-radius: 10px; overflow: hidden; border: 1px solid var(--border); }
.chat-img-thumb img { width: 100%; height: 100%; object-fit: cover; }
.chat-img-remove {
  position: absolute; top: 2px; right: 2px; width: 18px; height: 18px; padding: 0;
  border: none; border-radius: 50%; cursor: pointer; background: rgba(0,0,0,.6);
  color: #fff; font-size: 13px; line-height: 18px; text-align: center;
}
.chat-bubble-imgs { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 6px; }
.chat-bubble-imgs img { max-height: 160px; max-width: 100%; border-radius: 8px; border: 1px solid var(--border); }
/* 通用附件：待发文件条 + 气泡内下载链接 */
.chat-file-strip { display: flex; gap: 8px; flex-wrap: wrap; }
.chat-file-chip {
  display: inline-flex; align-items: center; gap: 6px; max-width: 240px;
  padding: 5px 8px; border-radius: 9px; font-size: 12px;
  background: var(--bg); border: 1px solid var(--border);
}
.chat-file-chip .cf-ico { font-size: 13px; }
.chat-file-chip .cf-name, .chat-bubble-files .cf-name {
  max-width: 150px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--text);
}
.chat-file-chip .cf-size, .chat-bubble-files .cf-size { flex: 0 0 auto; color: var(--text-light); font-size: 11px; }
.chat-bubble-files { display: flex; flex-direction: column; gap: 4px; margin-bottom: 6px; }
.chat-file-link {
  display: inline-flex; align-items: center; gap: 6px; max-width: 100%;
  padding: 5px 9px; border-radius: 9px; font-size: 12px; text-decoration: none;
  background: var(--bg); border: 1px solid var(--border); color: var(--text);
  transition: border-color .15s;
}
.chat-file-link:hover { border-color: var(--primary); }
@media (max-width: 480px) {
  .chat-img-btn { width: 38px; height: 38px; }
  .chat-img-thumb { width: 52px; height: 52px; }
}
.send-icon { width: 20px; height: 20px; }
.chat-messages::-webkit-scrollbar { width: 6px; }
.chat-messages::-webkit-scrollbar-thumb { background: var(--border); border-radius: 3px; }
/* 流式打字光标 */
.chat-caret { display: inline-block; width: 7px; height: 15px; margin-left: 2px; vertical-align: -2px; background: var(--primary, #2bb187); border-radius: 2px; animation: caretBlink .9s steps(1) infinite; }
@keyframes caretBlink { 0%,100%{opacity:1} 50%{opacity:0} }
/* 停止生成按钮 */
.chat-stop { background: linear-gradient(135deg, #ef4444, #f97316); }
.chat-stop:hover { box-shadow: 0 4px 14px rgba(239,68,68,.35); }
/* markdown 增强 */
.chat-md :deep(.md-h) { font-size: 14.5px; font-weight: 700; color: var(--text); margin: 8px 0 2px; }
.chat-md :deep(.md-table) { border-collapse: collapse; margin: 8px 0; font-size: 12.5px; width: 100%; }
.chat-md :deep(.md-table th) { background: rgba(43,177,135,0.10); font-weight: 600; text-align: left; }
.chat-md :deep(.md-table th), .chat-md :deep(.md-table td) { border: 1px solid var(--border); padding: 5px 9px; }
.chat-md :deep(.md-table tr:nth-child(even) td) { background: rgba(0,0,0,0.02); }
.chat-md :deep(a) { color: var(--primary, #2bb187); text-decoration: underline; }
html[data-theme="dark"] .chat-md :deep(.md-table tr:nth-child(even) td) { background: rgba(255,255,255,0.03); }

/* ===== 工具箱 ===== */
.tool-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(210px, 1fr)); gap: 12px; }
.tool-card { display: flex; flex-direction: column; align-items: flex-start; gap: 8px; padding: 18px 16px; border: 1px solid var(--border); border-radius: 14px; background: var(--card); cursor: pointer; font-family: inherit; text-align: left; transition: transform .18s ease, box-shadow .18s ease, border-color .18s ease; }
.tool-card:hover { transform: translateY(-3px); border-color: rgba(43,177,135,0.4); box-shadow: 0 10px 24px rgba(43,177,135,0.12); }
.tool-icon { width: 42px; height: 42px; border-radius: 11px; display: flex; align-items: center; justify-content: center; font-size: 21px; }
.tool-name { font-size: 15px; font-weight: 700; color: var(--text); }
.tool-desc { font-size: 12.5px; color: var(--text-light); line-height: 1.55; }
.tool-body { animation: tool-fade .25s ease; }
@keyframes tool-fade { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: none; } }
.tool-back { padding: 6px 12px; border-radius: 9px; border: 1px solid var(--border); background: var(--card); color: var(--text-light); cursor: pointer; font-size: 12.5px; font-family: inherit; margin-bottom: 14px; }
.tool-back:hover { color: var(--primary, #2bb187); border-color: rgba(43,177,135,0.4); }
/* GPA 计算器 */
.gpa-table { width: 100%; border-collapse: collapse; }
.gpa-table th { font-size: 12px; color: var(--text-lighter); font-weight: 600; text-align: left; padding: 4px 8px; }
.gpa-table th.num { text-align: center; }
.gpa-table td { padding: 2px 8px; border-bottom: 1px solid var(--border); }
.gpa-table td.num { text-align: center; }
.gpa-table input { width: 100%; border: none; background: transparent; color: var(--text); font: inherit; font-size: 13.5px; padding: 7px 0; outline: none; }
.gpa-table input::placeholder { color: var(--text-lighter); }
.gpa-pt { color: var(--primary, #2bb187); font-weight: 700; font-size: 13.5px; }
.gpa-del { width: 26px; height: 26px; border-radius: 8px; border: none; background: transparent; color: var(--text-lighter); cursor: pointer; font-size: 13px; }
.gpa-del:hover:not(:disabled) { color: #e5484d; background: rgba(229,72,77,0.08); }
.gpa-del:disabled { opacity: .3; cursor: not-allowed; }
.gpa-actions { display: flex; gap: 8px; margin-top: 12px; }
.gpa-add { padding: 7px 14px; border-radius: 9px; border: 1px dashed rgba(43,177,135,0.5); background: rgba(43,177,135,0.06); color: var(--primary, #2bb187); font-weight: 600; cursor: pointer; font-size: 13px; font-family: inherit; }
.gpa-add:disabled { opacity: .4; cursor: not-allowed; }
.gpa-clear { padding: 7px 14px; border-radius: 9px; border: 1px solid var(--border); background: transparent; color: var(--text-light); cursor: pointer; font-size: 13px; font-family: inherit; }
.gpa-result { display: flex; gap: 12px; margin-top: 16px; }
.gpa-kpi { flex: 1; padding: 14px; border-radius: 14px; background: var(--card); border: 1px solid var(--border); text-align: center; }
.gpa-kpi.hl { background: rgba(43,177,135,0.08); border-color: rgba(43,177,135,0.35); }
.kpi-v { font-size: 24px; font-weight: 800; color: var(--text); font-variant-numeric: tabular-nums; }
.gpa-kpi.hl .kpi-v { color: var(--primary, #2bb187); }
.kpi-k { font-size: 12px; color: var(--text-light); margin-top: 3px; }
.gpa-plan { margin-top: 14px; padding: 12px 14px; border: 1px dashed var(--border); border-radius: 12px; }
.plan-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; font-size: 13px; color: var(--text); }
.plan-row input { width: 72px; padding: 5px 8px; border: 1px solid var(--border); border-radius: 8px; background: transparent; color: var(--text); font: inherit; }
.plan-out { font-weight: 800; color: var(--primary, #2bb187); }
.plan-tip { margin-top: 7px; font-size: 12px; color: var(--text-lighter); }
/* 倒计时板 */
.cd-add { display: flex; gap: 8px; margin: 2px 0 14px; flex-wrap: wrap; }
.cd-add input { flex: 1; min-width: 150px; padding: 9px 12px; border: 1px solid var(--border); border-radius: 10px; background: var(--card); color: var(--text); font: inherit; }
.cd-list { display: flex; flex-direction: column; gap: 8px; }
.cd-item { display: flex; align-items: center; gap: 12px; padding: 12px 14px; border: 1px solid var(--border); border-radius: 12px; background: var(--card); }
.cd-item.today { border-color: rgba(43,177,135,0.5); background: rgba(43,177,135,0.07); }
.cd-item.past { opacity: .55; }
.cd-main { flex: 1; min-width: 0; }
.cd-name { font-size: 14px; font-weight: 700; color: var(--text); }
.cd-date { font-size: 12px; color: var(--text-lighter); margin-top: 2px; }
.cd-num b { font-size: 22px; font-weight: 800; color: var(--primary, #2bb187); font-variant-numeric: tabular-nums; }
.cd-num span { font-size: 12px; color: var(--text-light); margin-left: 3px; }
.cd-num .today-v { font-size: 16px; }
.cd-num.past-v b { color: var(--text-lighter); }
.cd-empty { text-align: center; color: var(--text-lighter); font-size: 13px; padding: 20px 0; }
/* 番茄钟 */
.focus-wrap { position: relative; width: 240px; margin: 6px auto 12px; }
.focus-ring { width: 100%; display: block; transform: rotate(-90deg); }
.ring-bg { fill: none; stroke: rgba(127,140,155,0.15); stroke-width: 12; }
.ring-fg { fill: none; stroke: var(--primary, #2bb187); stroke-width: 12; stroke-linecap: round; stroke-dasharray: 553; transition: stroke-dashoffset .6s linear; }
.ring-fg.break { stroke: #4096ff; }
.focus-center { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; }
.focus-time { font-size: 36px; font-weight: 800; color: var(--text); font-variant-numeric: tabular-nums; }
.focus-mode { font-size: 12.5px; color: var(--text-light); margin-top: 2px; }
.focus-acts { display: flex; justify-content: center; align-items: center; gap: 10px; margin-top: 4px; flex-wrap: wrap; }
.focus-btn { padding: 9px 18px; border-radius: 10px; border: 1px solid var(--border); background: var(--card); color: var(--text); font-weight: 600; cursor: pointer; font-size: 13.5px; font-family: inherit; }
.focus-btn.main { background: var(--primary, #2bb187); border-color: var(--primary, #2bb187); color: #fff; }
.focus-min { padding: 9px 10px; border-radius: 10px; border: 1px solid var(--border); background: var(--card); color: var(--text); font: inherit; }
.focus-stat { text-align: center; margin-top: 14px; font-size: 13px; color: var(--text-light); }
.focus-stat b { color: var(--primary, #2bb187); }

/* ===== 响应式 ===== */
@media (max-width: 1024px) {
  .chat-bubble-wrap { max-width: 82%; }
}
@media (max-width: 900px) {
  .ai-side { position: absolute; left: 0; top: 0; bottom: 0; z-index: 30; box-shadow: 0 12px 40px rgba(0,0,0,0.25); transform: translateX(-105%); transition: transform .25s ease; width: 250px; }
  .ai-side.collapsed { width: 250px; border-right-width: 1px; }
  .chat-page.side-open .ai-side { transform: translateX(0); }
  .chat-messages { padding: 16px 14px; }
  .ai-home { padding: 4px 0; }
  .ai-home-hi { font-size: 19px; }
  .ai-home-hi2 { font-size: 22px; }
  .ai-home-hero { flex-direction: column-reverse; gap: 8px; }
  .ai-home-greet { text-align: center; }
  .ai-home-sub { max-width: none; }
  .ai-mascot { width: 178px; height: 200px; transform: scale(.92); }
  .gpa-result { flex-direction: column; }
}
@media (max-width: 640px) {
  .chat-clear-btn { padding: 4px 8px; }
}

/* ===== 云白冰蓝配色收口：AI 助手页独立于全站主题，保持明亮 ===== */
.ai-side { background: linear-gradient(180deg, rgba(47,159,224,.07), rgba(140,205,245,.03) 60%, transparent), #ffffff; }
.ai-logo-icon { background: linear-gradient(135deg, #4ec5f1, #2f7fd0); color: #fff; border-radius: 10px; }
.ai-new-chat { background: linear-gradient(135deg, #4ec5f1, #2f8fd8); color: #fff; border-radius: 10px; }
.ai-new-chat:hover { box-shadow: 0 6px 16px rgba(47,159,224,.28); }
.ai-nav-item.active { background: rgba(47,159,224,.12); color: #1c7fc0; }
.ai-recent-item.current { background: rgba(47,159,224,.10); }
.chat-avatar { background: linear-gradient(135deg, #eaf7ff, #cbe8f8); color: #1c7fc0; border: 1px solid rgba(47,159,224,.28); border-radius: 10px; }
.chat-msg.user .chat-avatar { background: linear-gradient(135deg, #4ec5f1, #2f8fd8); color: #fff; border-color: transparent; }
.chat-bubble.user { background: linear-gradient(135deg, #4ec5f1, #2f8fd8); color: #fff; }
.chat-send { background: linear-gradient(135deg, #4ec5f1, #2f8fd8); color: #fff; border-radius: 12px; }
.chat-send:hover:not(:disabled) { box-shadow: 0 4px 14px rgba(47,159,224,.32); }
.chat-input-wrap:focus-within { border-color: #4ec5f1; box-shadow: 0 0 0 3px rgba(47,159,224,.14); }
.chip:hover { border-color: #4ec5f1; color: #1c7fc0; background: rgba(47,159,224,.08); }
.chat-reasoning { border-left-color: rgba(47,159,224,.45); background: rgba(47,159,224,.06); }
.chat-reasoning .cr-toggle, .cm-tag, .chat-caret, .chat-md :deep(a), .gpa-pt, .kb-cap-num { color: #1c7fc0; }
.cm-tag { background: rgba(47,159,224,.12); }
.chat-md :deep(.md-table th) { background: rgba(47,159,224,.09); }
.chat-act:hover:not(:disabled), .tool-back:hover { color: #1c7fc0; border-color: rgba(47,159,224,.45); }
.kb-jump, .kb-course:hover, .kb-course.open, .ai-history-item:hover, .tool-card:hover { border-color: rgba(47,159,224,.45); }
.kb-jump, .gpa-add { color: #1c7fc0; background: rgba(47,159,224,.08); }
.kb-jump:hover, .ai-history-item.current { background: rgba(47,159,224,.12); }
.kb-search:focus { border-color: rgba(47,159,224,.55); }
.kb-cats button.active { color: #fff; background: #2f9fe0; border-color: #2f9fe0; }
.tool-card:hover { box-shadow: 0 10px 24px rgba(47,159,224,.14); }

/* 窄屏：三栏挤压时让机器人让位，避免与问候文字重叠 */
@media (max-width: 1180px) {
  .ai-home-hero { flex-direction: column; align-items: flex-start; gap: 10px; }
  .ai-home-greet { width: 100%; }
  .ai-home-sub { max-width: 100%; }
  .ai-mascot { align-self: center; width: 180px; height: 192px; }
}
@media (max-width: 760px) {
  .ai-mascot { display: none; }
}

/* ===== 明亮主题：AI 助手页局部浅色变量（独立于全站主题） ===== */
.chat-page {
  --card: #ffffff;
  --card-bg: #ffffff;
  --card-bg-2: #f3f9fd;
  --bg: #f2f8fd;
  --text: #1e3446;
  --text-light: #5c7a91;
  --text-lighter: #8ba7bb;
  --border: rgba(38,104,150,.14);
  --primary: #2f9fe0;
  --primary-light: #6fd3f7;
  --primary-dark: #1c7fc0;
  --primary-deep: #1870ad;
  --primary-bg: rgba(47,159,224,.10);
  --danger: #e5484d;
  --ark-y: #2f9fe0;
  --ark-red: #ef5b6b;
  background: #ffffff;
  color: #1e3446;
}
/* 暗色主题：深蓝夜色 + 冰蓝点缀，白瓷机器人保持不变、在深底上更立体 */
html[data-theme="dark"] .chat-page {
  --card: #12202e;
  --card-bg: #12202e;
  --card-bg-2: #182c3e;
  --bg: #0e1a27;
  --text: #e8f3fb;
  --text-light: #bcd6e8;
  --text-lighter: #9fbcd2;
  --border: rgba(150,200,230,.16);
  --primary: #5ec8f2;
  --primary-light: #8fe0ff;
  --primary-dark: #2f9fe0;
  --primary-deep: #1c7fc0;
  --primary-bg: rgba(94,200,242,.14);
  --danger: #ff6b7a;
  --ark-y: #5ec8f2;
  --ark-red: #ff6b7a;
  background: #12202e;
  color: #e8f3fb;
}
.chat-page .chat-header { background: linear-gradient(90deg, rgba(47,159,224,.08), rgba(140,205,245,.02) 60%, transparent); }
.chat-page .chat-status { background: rgba(47,159,224,.12); color: #1c7fc0; border-radius: 999px; }
.chat-page .chat-status::before { background: #2f9fe0; }
.chat-page .chat-messages { background: linear-gradient(180deg, #ffffff, #fbfdff); }
.chat-page .ai-home-glow.g1 { background: radial-gradient(circle, rgba(96,196,245,.20), transparent 65%); }
.chat-page .ai-home-glow.g2 { background: radial-gradient(circle, rgba(164,148,240,.14), transparent 65%); }
.chat-page .ai-home-glow.g3 { background: radial-gradient(circle, rgba(120,220,205,.16), transparent 65%); }
html[data-theme="dark"] .ai-home-glow,
html:not([data-theme="dark"]) .ai-home-glow { opacity: 1; }
.chat-page .ai-home-brand { color: #1c7fc0; background: rgba(47,159,224,.10); border-left-color: #2f9fe0; border-radius: 999px; }
.chat-page .mascot-corner { border-color: rgba(47,159,224,.4); }
.chat-page .bot-state-tag { color: #1c7fc0; background: rgba(255,255,255,.94); border: 1px solid rgba(47,159,224,.35); border-radius: 999px; box-shadow: 0 4px 14px rgba(47,159,224,.14); }
.chat-page .bot-state-tag i { background: #2f9fe0; box-shadow: 0 0 8px rgba(47,159,224,.7); }
.chat-page .bot-state-tag.is-thinking { color: #1870ad; border-color: rgba(47,159,224,.6); }
.chat-page .bot-state-tag.is-error { color: #ef5b6b; border-color: rgba(239,91,107,.5); }
.chat-page .bot-state-tag.is-error i { background: #ef5b6b; box-shadow: 0 0 8px rgba(239,91,107,.6); }
.chat-page .bot-mini { border: 1px solid rgba(47,159,224,.3); border-radius: 50%; background: radial-gradient(circle at 50% 40%, rgba(140,215,250,.28), rgba(255,255,255,.7) 72%); }
.chat-page .bot-mini::after { background: linear-gradient(90deg, transparent, #4ec5f1, transparent); }
.chat-page .ai-card { box-shadow: 0 2px 10px rgba(38,104,150,.04); }
.chat-page .ai-card:hover { border-color: rgba(47,159,224,.5); box-shadow: 0 10px 24px rgba(47,159,224,.14); }
.chat-page .ai-card-arrow { color: #2f9fe0; }
.chat-page .ai-side-toggle:hover { color: #1c7fc0; border-color: #4ec5f1; }
.chat-page .chat-clear-btn:hover:not(:disabled) { border-color: #4ec5f1; color: #1c7fc0; }
.chat-page .ai-user-avatar { color: #fff; }
.chat-page .chat-md :deep(.md-code) { background: rgba(38,104,150,.06); }
.chat-page .chat-md :deep(.md-inline) { background: rgba(38,104,150,.09); }
.chat-page .chat-md :deep(.md-table tr:nth-child(even) td) { background: rgba(38,104,150,.03); }

/* ===== 暗色主题适配：硬编码浅色全部换成深蓝夜色，随主题切换联动 ===== */
html[data-theme="dark"] .chat-page .ai-side { background: linear-gradient(180deg, rgba(94,200,242,.09), rgba(94,200,242,.02) 60%, transparent), #101d2b; }
html[data-theme="dark"] .chat-page .chat-header { background: linear-gradient(90deg, rgba(94,200,242,.12), rgba(94,200,242,.02) 60%, transparent); }
html[data-theme="dark"] .chat-page .chat-messages { background: linear-gradient(180deg, #12202e, #0f1c29); }
html[data-theme="dark"] .chat-page .chat-status { color: #9fe0ff; background: rgba(94,200,242,.16); }
html[data-theme="dark"] .chat-page .chat-status::before { background: #5ec8f2; }
html[data-theme="dark"] .chat-page .ai-home-glow.g1 { background: radial-gradient(circle, rgba(70,170,235,.28), transparent 65%); }
html[data-theme="dark"] .chat-page .ai-home-glow.g2 { background: radial-gradient(circle, rgba(120,110,220,.20), transparent 65%); }
html[data-theme="dark"] .chat-page .ai-home-glow.g3 { background: radial-gradient(circle, rgba(60,190,180,.18), transparent 65%); }
html[data-theme="dark"] .chat-page .ai-home-brand { color: #9fe0ff; background: rgba(94,200,242,.14); border-left-color: #5ec8f2; }
html[data-theme="dark"] .chat-page .mascot-corner { border-color: rgba(94,200,242,.5); }
html[data-theme="dark"] .chat-page .ai-mascot { background: radial-gradient(circle at 50% 45%, rgba(94,200,242,.13), transparent 68%); border-radius: 50%; }
html[data-theme="dark"] .chat-page .bot-state-tag { color: #9fe0ff; background: rgba(14,28,41,.92); border-color: rgba(94,200,242,.4); box-shadow: 0 4px 16px rgba(0,0,0,.3); }
html[data-theme="dark"] .chat-page .bot-state-tag.is-thinking { color: #bff0ff; }
html[data-theme="dark"] .chat-page .bot-state-tag.is-error { color: #ff8b98; }
html[data-theme="dark"] .chat-page .bot-mini { background: radial-gradient(circle at 50% 40%, rgba(94,200,242,.22), rgba(14,28,41,.8) 74%); border-color: rgba(94,200,242,.4); }
html[data-theme="dark"] .chat-page .ai-card { box-shadow: 0 2px 12px rgba(0,0,0,.16); }
html[data-theme="dark"] .chat-page .ai-card:hover { border-color: rgba(94,200,242,.55); box-shadow: 0 10px 26px rgba(0,0,0,.28); }
html[data-theme="dark"] .chat-page .ai-card-arrow,
html[data-theme="dark"] .chat-page .ai-side-toggle:hover,
html[data-theme="dark"] .chat-page .chat-clear-btn:hover:not(:disabled),
html[data-theme="dark"] .chat-page .ai-nav-item.active,
html[data-theme="dark"] .chat-page .chip:hover,
html[data-theme="dark"] .chat-page .chat-reasoning .cr-toggle,
html[data-theme="dark"] .chat-page .cm-tag,
html[data-theme="dark"] .chat-page .chat-caret,
html[data-theme="dark"] .chat-page .chat-md :deep(a),
html[data-theme="dark"] .chat-page .gpa-pt,
html[data-theme="dark"] .chat-page .kb-cap-num,
html[data-theme="dark"] .chat-page .kb-jump,
html[data-theme="dark"] .chat-page .gpa-add,
html[data-theme="dark"] .chat-page .chat-act:hover:not(:disabled),
html[data-theme="dark"] .chat-page .tool-back:hover { color: #8fe0ff; }
html[data-theme="dark"] .chat-page .chat-avatar { background: rgba(94,200,242,.16); color: #9fe0ff; border-color: rgba(94,200,242,.35); }
html[data-theme="dark"] .chat-page .cm-tag { background: rgba(94,200,242,.16); }
html[data-theme="dark"] .chat-page .chat-md :deep(.md-code) { background: rgba(255,255,255,.08); }
html[data-theme="dark"] .chat-page .chat-md :deep(.md-inline) { background: rgba(255,255,255,.10); }
html[data-theme="dark"] .chat-page .chat-md :deep(.md-table tr:nth-child(even) td) { background: rgba(255,255,255,.03); }
/* 页面容器底色：用更高特异性压过全局 .stats-page.liquid 的透传，保证容器本身也随主题变 */
.chat-page.stats-page.liquid { background: #ffffff; }
html[data-theme="dark"] .chat-page.stats-page.liquid { background: #12202e; }
</style>
