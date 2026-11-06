<template>
  <div class="page-wrap stats-page liquid">
    <!-- ===== 页面级 Tab：导师双选 / 自选课题 / 各学院课题库 ===== -->
    <div class="aa-tabs">
      <template v-if="!isTeacher">
        <div class="aa-tab" :class="{ active: pageTab === 'select' }" @click="pageTab = 'select'">
          🤝 {{ t('导师双选') }}
        </div>
        <div class="aa-tab" :class="{ active: pageTab === 'proposal' }" @click="pageTab = 'proposal'">
          ✏️ {{ t('自选课题') }}
        </div>
      </template>
      <div v-for="c in topicColleges" :key="c" class="aa-tab" :class="{ active: pageTab === c }" @click="openTopicTab(c)">
        📚 {{ c }}{{ t('课题库') }}
      </div>
      <div v-if="!topicColleges.length && isTeacher" class="aa-tab active">📚 {{ t('课题库') }}</div>
    </div>

    <template v-if="pageTab === 'select'">
    <!-- ===== 顶部统计卡（统一高度/图标/字号 + 真实口径） ===== -->
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-icon blue" v-html="svgIcon('graduation-cap')" />
        <div>
          <div class="stat-value">{{ statusLabel }}</div>
          <div class="stat-label">{{ t('双选进度 · 志愿 {n}/3', { n: pendingCount }) }}</div>
          <div class="stat-sub">{{ statusHint }}</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon purple" v-html="svgIcon('award')" />
        <div>
          <div class="stat-value">{{ advisors.length }}</div>
          <div class="stat-label">{{ t('可选导师') }}</div>
          <div class="stat-sub">{{ t('有余位 {a} 位 · 已满 {b} 位', { a: availableCount, b: fullCount }) }}</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon amber" v-html="svgIcon('hourglass')" />
        <div>
          <div class="stat-value">{{ totalRemain }}</div>
          <div class="stat-label">{{ t('剩余名额合计') }}</div>
          <div class="stat-sub">{{ t('全部容量') }} {{ totalCapacity }} · {{ t('多志愿一经确认其余自动失效') }}</div>
        </div>
      </div>
    </div>

    <!-- ===== 导师选择已关闭提示 ===== -->
    <div v-if="cfg.open === false" class="aa-closed-tip">
      <span v-html="svgIcon('hourglass')" /> {{ t('导师选择当前已关闭，请等待管理员开放后再提交申请') }}
    </div>

    <!-- ===== 我的志愿卡（多志愿双选：最多同时 3 个待审志愿） ===== -->
    <div v-if="activeAdvisors.length" class="section aa-mine">
      <div class="section-header">
        <div class="section-title">
          <span v-html="svgIcon('check-circle')" />
          {{ approvedAdvise ? t('我的导师已确认') : t('我的双选志愿') }}
        </div>
        <span class="aa-status-badge" :class="approvedAdvise ? 'approved' : 'pending'">
          {{ approvedAdvise ? t('双选完成') : t('待审 {n}/3', { n: pendingCount }) }}
        </span>
      </div>
      <div class="aa-choice-grid" :class="{ 'is-done': !!approvedAdvise }">
        <div v-for="(g, i) in activeAdvisors" :key="g.id"
             class="aa-choice-card" :class="g.status">
          <div class="aa-choice-head">
            <span class="aa-choice-pri" :class="g.status">{{ t('第{n}志愿', { n: g.priority || i + 1 }) }}</span>
            <span class="aa-status-badge sm" :class="g.status">{{ statusText(g.status) }}</span>
          </div>
          <div class="aa-choice-name">
            <div class="advisor-avatar sm">{{ g.teacherName?.charAt(0) }}</div>
            <div>
              <div class="aa-choice-teacher">{{ g.teacherName || t('暂无') }}</div>
              <div class="aa-choice-no">{{ g.teacherCollege || '' }} {{ g.teacherNo ? '· ' + g.teacherNo : '' }}</div>
            </div>
          </div>
          <div class="aa-choice-field" v-if="g.preferenceText"><label>{{ t('意向说明') }}</label><span>{{ g.preferenceText }}</span></div>
          <div class="aa-choice-field" v-if="g.teacherReply"><label>{{ t('导师回复') }}</label><span class="aa-reply">{{ g.teacherReply }}</span></div>
          <div class="aa-choice-foot">
            <span class="aa-choice-time">{{ fmtTime(g.createdAt) }}</span>
            <button v-if="g.status === 'pending'" class="btn btn-outline btn-sm aa-cancel"
                    :disabled="cancelling" @click="cancelApply(g)">
              {{ cancelling ? t('撤销中…') : t('撤销') }}
            </button>
          </div>
        </div>
        <!-- 双选完成：右侧双选流程面板（填充确认后的空旷区域） -->
        <div v-if="approvedAdvise" class="aa-flow-card">
          <div class="aa-flow-head">
            <span class="aa-flow-title">{{ t('双选流程') }}</span>
            <span class="aa-flow-next" v-if="nextStepHint">{{ t('下一步') }}：{{ nextStepHint }}</span>
          </div>
          <div class="aa-flow-steps">
            <div v-for="(s, i) in flowSteps" :key="i" class="aa-flow-step" :class="s.state">
              <div class="aa-flow-rail">
                <i class="aa-flow-dot">{{ s.state === 'done' ? '✓' : '' }}</i>
                <i v-if="i < flowSteps.length - 1" class="aa-flow-line" :class="{ filled: s.state === 'done' }" />
              </div>
              <div class="aa-flow-main">
                <b>{{ s.label }}</b>
                <span v-if="s.detail" class="aa-flow-detail" :class="{ 'is-topic': s.isTopic }">{{ s.detail }}</span>
              </div>
            </div>
          </div>
        </div>
        <!-- 空槽位提示 -->
        <div v-if="!approvedAdvise && pendingCount < 3" class="aa-choice-card empty-slot">
          <div class="aa-slot-plus">＋</div>
          <div class="aa-slot-text">{{ t('还可提交 {n} 个志愿', { n: 3 - pendingCount }) }}</div>
          <div class="aa-slot-hint">{{ t('在下方导师列表点「设为志愿」') }}</div>
        </div>
      </div>
    </div>

    <!-- ===== 导师列表 ===== -->
    <div class="section" v-if="advisors.length">
      <div class="section-header">
        <div class="section-title" v-html="svgIcon('graduation-cap') + ' ' + t('导师列表')"></div>
        <span class="aa-summary">{{ t('总余位') }} <b>{{ totalRemain }}</b> · {{ t('满额导师') }} <b>{{ fullCount }}</b></span>
      </div>

      <!-- 搜索与筛选 -->
      <div class="aa-filter">
        <input v-model="fKw" class="aa-input aa-search" type="text" :placeholder="t('搜索导师姓名 / 工号')">
        <!-- 学院同步：后端已按学生本院强制隔离，这里展示锁定徽章而非筛选 -->
        <div v-if="lockedCollege" class="at-own-badge static aa-college-lock">🏛 {{ lockedCollege }} · {{ t('本院导师') }}</div>
        <select v-else v-model="fCollege" class="aa-input">
          <option value="">{{ t('全部学院') }}</option>
          <option v-for="c in collegeOptions" :key="c" :value="c">{{ c }}</option>
        </select>
        <select v-model="fMajor" class="aa-input">
          <option value="">{{ t('全部研究方向') }}</option>
          <option v-for="m in majorOptions" :key="m" :value="m">{{ m }}</option>
        </select>
        <select v-model="fCap" class="aa-input aa-cap-sel">
          <option value="all">{{ t('名额：全部') }}</option>
          <option value="open">{{ t('仅看有余位') }}</option>
          <option value="full">{{ t('仅看已满') }}</option>
        </select>
        <button v-if="hasFilter" class="aa-clear" @click="clearFilter">{{ t('清空') }}</button>
      </div>
      <div class="aa-filter-result">{{ t('共 {a} 位导师 · 筛选后 {b} 位', { a: advisors.length, b: filteredAdvisors.length }) }}</div>

      <div class="advisor-grid">
        <div v-for="a in filteredAdvisors" :key="a.teacherId"
             class="advisor-card" :class="{ full: !a.available, mine: myStatusOf(a) }">
          <div class="aa-card-top">
            <div class="advisor-avatar">{{ a.teacherName?.charAt(0) }}</div>
            <div class="aa-id-box">
              <div class="advisor-name" :title="a.teacherName">{{ a.teacherName }}</div>
              <div class="advisor-no">{{ t('工号') }} {{ a.teacherNo || t('暂无') }}</div>
            </div>
            <span v-if="myStatusOf(a)" class="aa-status-badge sm" :class="myStatusOf(a)">{{ statusText(myStatusOf(a)) }}</span>
            <span v-else-if="!a.available" class="aa-status-badge sm full-tag">{{ t('已满') }}</span>
          </div>
          <div class="aa-meta-row" :title="a.college"><span class="aa-meta-label">{{ t('学院') }}</span><span class="aa-meta-val">{{ a.college || t('暂无') }}</span></div>
          <div class="aa-meta-row" :title="a.major"><span class="aa-meta-label">{{ t('方向') }}</span><span class="aa-meta-val">{{ a.major || t('暂无') }}</span></div>
          <div class="aa-cap-bar"><i :class="{ full: !a.available }" :style="{ width: capPct(a) + '%' }" /></div>
          <div class="advisor-capacity">
            {{ t('已录') }} <b>{{ a.approved || 0 }}</b> / {{ a.capacity }}
            <span class="cap-detail">· {{ t('待审 {n}', { n: a.pending || 0 }) }} · {{ t('余 {n}', { n: remainOf(a) }) }}</span>
          </div>
          <div class="advisor-actions">
            <button class="btn aa-apply-btn"
              :class="canApply(a) ? 'btn-primary' : 'btn-outline'"
              :disabled="!canApply(a)"
              @click="apply(a)">
              {{ btnText(a) }}
            </button>
          </div>
        </div>
      </div>
      <div v-if="!filteredAdvisors.length" class="empty-state aa-no-match">
        <div class="empty-icon" v-html="svgIcon('graduation-cap')" />
        <div class="empty-title">{{ t('没有符合筛选条件的导师') }}</div>
      </div>
    </div>
    <div v-else class="empty-state">
      <div class="empty-icon" v-html="svgIcon('graduation-cap')" />
      <div class="empty-title">{{ t('暂无可选导师') }}</div>
    </div>
    </template>

    <!-- ===== 自选课题面板：学生自拟 → 毕业导师审批 ===== -->
    <template v-else-if="pageTab === 'proposal'">
      <!-- 未完成双选：先去导师双选 -->
      <div v-if="!approvedAdvise" class="section aa-mine">
        <div class="section-header">
          <div class="section-title">✏️ {{ t('自选课题') }}</div>
        </div>
        <div class="empty-state aa-no-match">
          <div class="empty-icon" v-html="svgIcon('graduation-cap')" />
          <div class="empty-title">{{ t('完成导师双选后，可在这里自拟课题提交毕业导师审批') }}</div>
          <div class="aa-slot-hint">{{ t('先到「导师双选」Tab 向导师提交志愿') }}</div>
        </div>
      </div>

      <template v-else>
        <!-- 当前课题状态卡：已确定课题时置顶展示 -->
        <div v-if="currentTopic" class="aa-current-topic">
          <div class="aa-ct-main">
            <span class="aa-ct-label">✓ {{ t('当前已确定课题') }}</span>
            <b class="aa-ct-title">{{ currentTopic }}</b>
            <span class="aa-ct-meta" v-if="approvedAdvise.updatedAt">{{ t('确认时间') }}：{{ fmtTime(approvedAdvise.updatedAt) }}</span>
          </div>
          <span v-if="!pendingProposal" class="aa-ct-hint">{{ t('如需更换课题，可在下方提交新的自选课题申请') }}</span>
        </div>

        <!-- 提交 / 修改重发表单 -->
        <div class="section aa-topic-pub">
          <div class="section-header">
            <div class="section-title">✏️ {{ proposalFormTitle }}</div>
            <span class="aa-summary">{{ proposalFormHint }}</span>
          </div>
          <div v-if="pendingProposal" class="aa-pending-tip">
            ⏳ {{ pendingTipText }}
          </div>
          <div v-else class="at-pub-grid">
            <div class="at-pub-field at-span2">
              <label>{{ t('课题名称') }} <i>*</i><span class="at-count">{{ propForm.title.length }}/120</span></label>
              <input v-model="propForm.title" class="form-input" maxlength="120"
                     :placeholder="t('如：基于 SpringBoot 的选课系统的设计与实现（与本专业相关）')">
            </div>
            <div class="at-pub-field">
              <label>{{ t('研究方向') }}<span class="at-count">{{ propForm.direction.length }}/30</span></label>
              <input v-model="propForm.direction" class="form-input" maxlength="30" :placeholder="t('如：后端开发 / 人工智能')">
            </div>
            <div class="at-pub-field">
              <label>{{ t('面向专业') }}</label>
              <div class="at-own-badge static">🎓 {{ auth.user?.major || auth.user?.college || t('本专业') }}</div>
            </div>
            <div class="at-pub-field at-span2">
              <label>{{ t('选题说明（研究内容 / 与本专业的关联 / 可行性）') }}<span class="at-count">{{ propForm.description.length }}/500</span></label>
              <textarea v-model="propForm.description" class="form-input" rows="4" maxlength="500"
                        :placeholder="t('说明课题背景、要解决的问题、使用的技术方法、预期成果…')"></textarea>
            </div>
            <div v-if="editingReply" class="at-pub-field at-span2">
              <div class="aa-reject-reason">📥 {{ t('导师打回理由：') }}{{ editingReply }}</div>
            </div>
          </div>
          <div class="at-pub-foot" v-if="!pendingProposal">
            <button class="btn btn-primary at-pub-btn" :disabled="propSubmitting || !propForm.title.trim()"
                    @click="submitProposal">
              {{ propSubmitting ? t('提交中…') : submitBtnText }}
            </button>
            <button v-if="editingId" class="btn btn-outline at-pub-btn" @click="cancelEdit">{{ t('取消修改') }}</button>
            <span class="ff-hint">{{ submitHint }}</span>
          </div>
        </div>

        <!-- 我的自选记录 -->
        <div class="section" v-if="proposals.length">
          <div class="section-header">
            <div class="section-title">{{ t('我的自选课题记录') }}</div>
            <span class="aa-summary">{{ proposals.length }} {{ t('条') }}</span>
          </div>
          <div class="aa-choice-grid">
            <div v-for="p in proposals" :key="p.id" class="aa-choice-card" :class="p.status">
              <div class="aa-choice-head">
                <span class="aa-choice-pri" :class="p.status">
                  {{ proposalStatusText(p.status) }}
                  <i v-if="p.status === 'approved' && p.title === currentTopic" class="aa-cur-tag">{{ t('当前课题') }}</i>
                </span>
                <span class="aa-choice-time">{{ fmtTime(p.createdAt) }}</span>
              </div>
              <div class="at-title">{{ p.title }}</div>
              <div class="aa-choice-field" v-if="p.direction"><label>{{ t('方向') }}</label><span>{{ p.direction }}</span></div>
              <div class="aa-choice-field" v-if="p.description"><label>{{ t('说明') }}</label><span>{{ p.description }}</span></div>
              <div class="aa-choice-field" v-if="p.teacherReply">
                <label>{{ p.status === 'rejected' ? t('打回理由') : t('导师备注') }}</label>
                <span :class="p.status === 'rejected' ? 'aa-reject-text' : 'aa-reply'">{{ p.teacherReply }}</span>
              </div>
              <div class="aa-choice-foot">
                <span class="aa-choice-time">{{ t('更新于') }} {{ fmtTime(p.updatedAt) }}</span>
                <span class="aa-choice-actions">
                  <button v-if="p.status === 'rejected'" class="btn btn-outline btn-sm aa-cancel"
                          @click="startEdit(p)">{{ t('修改重提') }}</button>
                  <button v-if="p.status === 'pending'" class="btn btn-outline btn-sm aa-cancel"
                          :disabled="propSubmitting" @click="withdrawProposal(p)">{{ t('撤回') }}</button>
                </span>
              </div>
            </div>
          </div>
        </div>
      </template>
    </template>

    <!-- ===== 学院课题库面板 ===== -->
    <template v-else-if="pageTab !== 'proposal'">
      <!-- 教师发布课题（仅本院教师可见本院课题库） -->
      <div v-if="isTeacher" class="section aa-topic-pub">
        <div class="section-header">
          <div class="section-title" v-html="svgIcon('book-open') + ' ' + t('发布新课题')"></div>
          <span class="aa-summary">{{ t('学生申领后进入双选流程，可随时下架或删除') }}</span>
        </div>
        <div class="at-pub-grid">
          <div class="at-pub-field at-span2">
            <label>{{ t('课题名称') }} <i>*</i><span class="at-count">{{ pubForm.title.length }}/120</span></label>
            <input v-model="pubForm.title" class="form-input" maxlength="120" :placeholder="t('如：基于SpringBoot的校园二手交易平台设计与实现')">
          </div>
          <div class="at-pub-field">
            <label>{{ t('研究方向') }}<span class="at-count">{{ pubForm.direction.length }}/30</span></label>
            <input v-model="pubForm.direction" class="form-input" maxlength="30" :placeholder="t('如：后端开发 / 人工智能')">
          </div>
          <div class="at-pub-field">
            <label>{{ t('面向学生') }}</label>
            <div class="at-own-badge static">🏛 {{ auth.user?.college || t('未设置学院') }} · {{ t('本院课题库') }}</div>
          </div>
          <div class="at-pub-field at-span2">
            <label>{{ t('课题描述与要求') }}<span class="at-count">{{ pubForm.description.length }}/500</span></label>
            <textarea v-model="pubForm.description" class="form-input" rows="4" maxlength="500" :placeholder="t('研究内容、技术栈、工作量预期、对学生的要求…')"></textarea>
          </div>
        </div>
        <div class="at-pub-foot">
          <button class="btn btn-primary at-pub-btn" :disabled="topicSubmitting || !pubForm.title.trim()" @click="publishTopic">
            {{ topicSubmitting ? t('发布中…') : '✨ ' + t('发布到本院课题库') }}
          </button>
          <span class="ff-hint">{{ t('发布后立即出现在「{c}课题库」，学生可一键申领', { c: auth.user?.college || '' }) }}</span>
        </div>
      </div>

      <!-- 课题网格 -->
      <div class="section">
        <div class="section-header">
          <div class="section-title" v-html="svgIcon('folder') + ' ' + (pageTab && topicColleges.includes(pageTab) ? pageTab + t('课题库') : t('课题库'))"></div>
          <div class="aa-topic-head-right">
            <!-- 双选完成：默认只显示自己导师的毕设题目，可切换查看全院 -->
            <label v-if="approvedAdvise" class="aa-only-mine">
              <input type="checkbox" v-model="onlyMyAdvisorTopics" />
              {{ t('只看我的导师（{n}）', { n: approvedAdvise.teacherName || '' }) }}
            </label>
            <span class="aa-summary">{{ t('开放课题') }} <b>{{ visibleTopics.length }}</b> {{ t('个') }} · {{ t('被选定后自动标记') }}</span>
          </div>
        </div>
        <div class="aa-topic-grid">
          <div v-for="tp in visibleTopics" :key="tp.id" class="aa-topic-card" :class="{ mine: isMyTopic(tp), taken: tp.taken, closed: tp.status !== 'open' }">
            <div class="at-head">
              <span v-if="tp.direction" class="at-dir">{{ tp.direction }}</span>
              <span class="at-status" :class="{ taken: tp.taken }">{{ tp.taken ? t('已选定') : tp.status === 'open' ? t('可申领') : t('已下架') }}</span>
            </div>
            <div class="at-title" :title="tp.title">{{ tp.title }}</div>
            <div v-if="tp.description" class="at-desc">{{ tp.description }}</div>
            <div class="at-teacher">
              <span class="at-avatar">{{ tp.teacherName?.charAt(0) }}</span>
              <span>{{ tp.teacherName }} · {{ tp.teacherNo }}</span>
            </div>
            <div class="at-foot">
              <!-- 学生：申这个题 -->
              <button v-if="!isTeacher" class="btn btn-sm at-apply"
                :class="topicBtnCls(tp)" :disabled="tp.taken || tp.status !== 'open' || !topicCanApply(tp) || applyingTopic"
                @click="applyTopic(tp)">
                {{ topicBtnText(tp) }}
              </button>
              <!-- 教师：管理自己的课题 -->
              <template v-else-if="isMyTopic(tp)">
                <button class="btn btn-outline btn-sm" :disabled="topicSubmitting" @click="toggleTopic(tp)">
                  {{ tp.status === 'open' ? t('下架') : t('重新上架') }}
                </button>
                <button class="btn btn-outline btn-sm at-del" :disabled="topicSubmitting" @click="removeTopic(tp)">🗑</button>
              </template>
              <span v-else class="at-readonly">{{ t('{n} 的课题', { n: tp.teacherName }) }}</span>
            </div>
          </div>
        </div>
        <div v-if="!visibleTopics.length" class="empty-state">
          <div class="empty-icon" v-html="svgIcon('book-open')" />
          <div class="empty-title">{{ isTeacher ? t('本学院还没有开放课题，发布第一个吧') : t('该学院暂无开放课题') }}</div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api'
import { useAuthStore } from '@/stores/auth'
import { t } from '@/i18n'
const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`
const auth = useAuthStore()
const isTeacher = computed(() => auth.user?.role === 'teacher')
const advisors = ref<any[]>([])
const cfg = ref<any>({})
const cancelling = ref(false)

/* ===== 页面级 Tab + 课题库 ===== */
const pageTab = ref<string>('select')
const topicColleges = ref<string[]>([])
const allTopics = ref<any[]>([])
const myTopicList = ref<any[]>([])
const topicSubmitting = ref(false)
const applyingTopic = ref(false)
const pubForm = ref<any>({ title: '', direction: '', description: '' })
/** 双选完成后课题库默认只看自己导师的题目（可切换查看全院） */
const onlyMyAdvisorTopics = ref(true)

const visibleTopics = computed(() => {
  if (isTeacher.value) {
    // 教师：本院开放课题 + 自己的课题（含下架）
    const myCollege = auth.user?.college || ''
    const merged = [...allTopics.value.filter((tp: any) => tp.college === myCollege)]
    for (const mt of myTopicList.value) {
      if (!merged.some((x: any) => x.id === mt.id)) merged.unshift(mt)
    }
    return merged
  }
  // 双选完成：默认只看自己导师的毕设题目（后端已把导师课题并入列表，即使跨学院）
  const adv = approvedAdvise.value
  if (adv && onlyMyAdvisorTopics.value) {
    return allTopics.value.filter((tp: any) => tp.teacherId === adv.teacherId)
  }
  // 学院 Tab：本院课题 + 已确认导师所在学院的课题（切到该 Tab 可浏览）
  return allTopics.value.filter((tp: any) => !pageTab.value || pageTab.value === 'select'
    || tp.college === pageTab.value || (adv && tp.college === adv.teacherCollege))
})
const isMyTopic = (tp: any) => isTeacher.value && tp.teacherId === auth.user?.id

function openTopicTab(c: string) { pageTab.value = c }

async function loadTopics() {
  try {
    const r: any = await http.get('/advisor/topics')
    const d = r?.data || {}
    allTopics.value = d.topics || []
    topicColleges.value = d.colleges || []
  } catch {}
  if (isTeacher.value) {
    try {
      const r: any = await http.get('/advisor/topics/mine')
      myTopicList.value = r?.data || []
    } catch {}
  }
}

/** 学生从课题库直接申领：带着课题标题向该导师提交志愿 */
async function applyTopic(tp: any) {
  try { await ElMessageBox.confirm(
    t('申领「{t}」，将作为志愿提交给 {n} 老师？任一志愿被确认后其余自动失效。', { t: tp.title, n: tp.teacherName }),
    t('申领课题'), { type: 'info', confirmButtonText: t('确定'), cancelButtonText: t('再想想') }) } catch { return }
  applyingTopic.value = true
  try {
    await http.post('/advisor/apply', { teacherId: tp.teacherId, topic: tp.title })
    ElMessage.success(t('已提交为志愿，等待 {n} 老师审批', { n: tp.teacherName }))
    load()
  } catch (e: any) { ElMessage.error(e?.message || t('申领失败')) }
  applyingTopic.value = false
}
function topicCanApply(tp: any) {
  return !approvedAdvise.value && myStatusOf(tp) !== 'pending' && pendingCount.value < 3
}
function topicBtnText(tp: any) {
  if (tp.taken) return t('已选定')
  if (myStatusOf(tp) === 'approved') return t('已确认')
  if (myStatusOf(tp) === 'pending') return t('审核中')
  if (approvedAdvise.value) return t('已有导师')
  if (pendingCount.value >= 3) return t('志愿已满')
  return t('申这个题')
}
function topicBtnCls(tp: any) {
  return (tp.taken || myStatusOf(tp) || approvedAdvise.value || pendingCount.value >= 3) ? 'btn-outline' : 'btn-primary'
}

/* ===== 教师：发布/下架/删除课题 ===== */
async function publishTopic() {
  if (topicSubmitting.value || !pubForm.value.title.trim()) return
  topicSubmitting.value = true
  try {
    await http.post('/advisor/topics', pubForm.value)
    ElMessage.success(t('课题已发布到课题库'))
    pubForm.value = { title: '', direction: '', description: '' }
    await Promise.all([loadTopics(), load()])
  } catch (e: any) { ElMessage.error(e?.message || t('发布失败')) }
  topicSubmitting.value = false
}
async function toggleTopic(tp: any) {
  if (!isMyTopic(tp)) { ElMessage.error(t('仅可管理自己发布的课题')); return }
  const next = tp.status === 'open' ? 'closed' : 'open'
  try {
    await http.put('/advisor/topics/' + tp.id, { status: next })
    ElMessage.success(next === 'open' ? t('已重新上架') : t('已下架'))
    await Promise.all([loadTopics(), load()])
  } catch (e: any) { ElMessage.error(e?.message || t('操作失败')) }
}
async function removeTopic(tp: any) {
  if (!isMyTopic(tp)) { ElMessage.error(t('仅可删除自己发布的课题')); return }
  try { await ElMessageBox.confirm(t('确定删除课题「{t}」？删除后不可恢复。', { t: tp.title }), t('删除课题'),
    { type: 'warning', confirmButtonText: t('删除'), cancelButtonText: t('取消') }) } catch { return }
  try {
    await http.delete('/advisor/topics/' + tp.id)
    ElMessage.success(t('课题已删除'))
    await Promise.all([loadTopics(), load()])
  } catch (e: any) { ElMessage.error(e?.message || t('删除失败')) }
}

/* ===== 自选课题（学生自拟 → 毕业导师审批） ===== */
const proposals = ref<any[]>([])
const propForm = ref<any>({ title: '', direction: '', description: '' })
const editingId = ref<number | null>(null)
const editingReply = ref('')
const propSubmitting = ref(false)
const pendingProposal = computed(() => proposals.value.find((p: any) => p.status === 'pending'))
const rejectedProposal = computed(() => proposals.value.find((p: any) => p.status === 'rejected'))
/** 当前已确定的课题（双选关系上的 topic） */
const currentTopic = computed(() => (approvedAdvise.value?.topic || '').trim())
/** 页面语义：未定题 = 提交自选；已定题 = 换题申请；编辑 = 修改重提 */
const proposalFormTitle = computed(() => {
  if (editingId.value) return t('修改被打回的自选课题')
  if (currentTopic.value) return t('申请更换课题')
  return t('提交自选课题')
})
const proposalFormHint = computed(() => {
  if (editingId.value) return t('修改后将重新提交给毕业导师 {n} 审批', { n: approvedAdvise.value?.teacherName || '' })
  if (pendingProposal.value) return t('自选课题正在审批中')
  if (currentTopic.value) return t('通过后将替换当前课题《{t}》', { t: currentTopic.value })
  return t('课题须与本专业相关，提交给毕业导师 {n} 审批，通过后写入双选进度', { n: approvedAdvise.value?.teacherName || '' })
})
const pendingTipText = computed(() => {
  if (!pendingProposal.value) return ''
  return currentTopic.value
    ? t('换题申请《{t}》待审核，通过后将替换当前课题；撤回后可重新编辑', { t: pendingProposal.value.title })
    : t('已有自选课题《{t}》待审核，导师处理后可再次提交', { t: pendingProposal.value.title })
})
const submitBtnText = computed(() => {
  if (editingId.value) return t('重新提交')
  if (currentTopic.value) return '🔄 ' + t('提交换题申请')
  return '✨ ' + t('提交给导师审批')
})
const submitHint = computed(() => {
  if (currentTopic.value) return t('导师通过后新课题将替换《{t}》并更新双选进度；打回后可修改重提', { t: currentTopic.value })
  return t('导师通过后课题写入双选进度；打回后可在这里修改重新提交')
})

function proposalStatusText(s: string) {
  return ({ pending: t('待审核'), approved: t('已通过'), rejected: t('已打回'), withdrawn: t('已撤回') } as Record<string, string>)[s] || s
}

function startEdit(p: any) {
  editingId.value = p.id
  editingReply.value = p.teacherReply || ''
  propForm.value = { title: p.title || '', direction: p.direction || '', description: p.description || '' }
  window.scrollTo({ top: 0, behavior: 'smooth' })
}
function cancelEdit() {
  editingId.value = null
  editingReply.value = ''
  propForm.value = { title: '', direction: '', description: '' }
}

async function submitProposal() {
  if (propSubmitting.value || !propForm.value.title.trim()) return
  propSubmitting.value = true
  try {
    const r: any = editingId.value
      ? await http.put('/advisor/proposals/' + editingId.value, { ...propForm.value })
      : await http.post('/advisor/proposals', { ...propForm.value })
    ElMessage.success(r?.message || t('已提交'))
    cancelEdit()
    load()
  } catch (e: any) { ElMessage.error(e?.message || t('提交失败')) }
  propSubmitting.value = false
}

async function withdrawProposal(p: any) {
  try { await ElMessageBox.confirm(t('确定撤回自选课题《{t}》？撤回后可重新提交。', { t: p.title }), t('撤回'),
    { type: 'warning', confirmButtonText: t('确定'), cancelButtonText: t('再想想') }) } catch { return }
  propSubmitting.value = true
  try {
    await http.delete('/advisor/proposals/' + p.id)
    ElMessage.success(t('已撤回'))
    load()
  } catch (e: any) { ElMessage.error(e?.message || t('撤回失败')) }
  propSubmitting.value = false
}

/* ===== 筛选状态 ===== */
const fKw = ref('')
const fCollege = ref('')
const fMajor = ref('')
const fCap = ref<'all' | 'open' | 'full'>('all')
const hasFilter = computed(() => !!(fKw.value.trim() || fCollege.value || fMajor.value || fCap.value !== 'all'))
function clearFilter() { fKw.value = ''; fCollege.value = ''; fMajor.value = ''; fCap.value = 'all' }
const collegeOptions = computed(() => [...new Set(advisors.value.map(a => a.college).filter(Boolean))].sort())
const majorOptions = computed(() => [...new Set(advisors.value.map(a => a.major).filter(Boolean))].sort())
/** 学院锁定徽章：学生已设置学院时后端只返回本院导师 */
const lockedCollege = computed(() => (!isTeacher.value ? (auth.user?.college || '') : ''))

/* ===== 余位与容量 ===== */
const remainOf = (a: any) => Math.max(0, (a.capacity || 0) - (a.approved || 0) - (a.pending || 0))
const capPct = (a: any) => {
  const cap = a.capacity || 0
  return cap <= 0 ? 100 : Math.min(100, Math.round(((a.approved || 0) + (a.pending || 0)) * 100 / cap))
}
const totalRemain = computed(() => advisors.value.reduce((s, a) => s + remainOf(a), 0))
const totalCapacity = computed(() => advisors.value.reduce((s, a) => s + (a.capacity || 0), 0))
const availableCount = computed(() => advisors.value.filter(a => a.available).length)
const fullCount = computed(() => advisors.value.filter(a => !a.available).length)

/* ===== 我的志愿（多志愿双选）：数组展示，approved 单独高亮 ===== */
const myAdviseList = ref<any[]>([])
const activeAdvisors = computed(() =>
  myAdviseList.value.filter((g: any) => g.status === 'pending' || g.status === 'approved' || g.status === 'rejected'))
const approvedAdvise = computed(() => activeAdvisors.value.find((g: any) => g.status === 'approved') || null)

/* 毕业工作台里程碑（开题/中期/终稿/答辩）：双选流程时间线与工作台同源同步 */
const flowMs = ref<any[]>([])
async function loadFlowMs() {
  const a = approvedAdvise.value
  if (!a?.id) { flowMs.value = []; return }
  try { const r: any = await http.get('/workspace/milestones', { params: { advisorId: a.id } }); flowMs.value = r?.data || [] }
  catch { flowMs.value = [] }
}
const pendingCount = computed(() => activeAdvisors.value.filter((g: any) => g.status === 'pending').length)
const myStatusOf = (a: any) => {
  const hit = myAdviseList.value.find((g: any) => g.teacherId === a.teacherId && g.status !== 'cancelled')
  return hit ? hit.status : null
}
/** 能否申请：有余位 + 无已确认导师 + 未在向该导师申请中 + 待审志愿未满 3 */
const canApply = (a: any) =>
  !!a.available && !approvedAdvise.value && myStatusOf(a) !== 'pending' && pendingCount.value < 3
function btnText(a: any) {
  if (!a.available) return t('已满')
  if (myStatusOf(a) === 'approved') return t('已确认')
  if (myStatusOf(a) === 'pending') return t('审核中')
  if (approvedAdvise.value) return t('已有导师')
  if (pendingCount.value >= 3) return t('志愿已满')
  return t('设为第{n}志愿', { n: pendingCount.value + 1 })
}

/* ===== 状态文案 ===== */
function statusText(s: string) {
  return ({ pending: t('待审核'), approved: t('已确认'), rejected: t('已拒绝'), cancelled: t('已撤销') } as Record<string, string>)[s] || s
}
const statusLabel = computed(() => {
  if (approvedAdvise.value) return t('已确认')
  if (pendingCount.value > 0) return t('双选中')
  return cfg.value.open === false ? t('未开放') : t('未申请')
})
const statusHint = computed(() => {
  if (approvedAdvise.value) return t('导师 {n} 已确认，其余志愿已自动失效', { n: approvedAdvise.value.teacherName || '' })
  if (pendingCount.value > 0) return t('最多 3 个志愿，任一导师确认后其余自动失效')
  return cfg.value.open === false ? t('导师选择暂未开放') : t('可向最多 3 位导师提交志愿申请')
})

/* ===== 双选完成后的流程面板：志愿 → 确认 → 课题 → 开题中期 → 答辩 ===== */
const flowSteps = computed(() => {
  const a = approvedAdvise.value
  if (!a) return []
  const hasTopic = !!(a.topic && String(a.topic).trim())
  // 「确定课题」步骤：自选审核中（未定题）/ 已定题（可附带换题申请备注）/ 被打回 / 待定
  let topicStep: { label: string; state: string; detail?: string; isTopic?: boolean }
  if (hasTopic) {
    const detail = pendingProposal.value
      ? t('{t}（换题申请《{n}》审核中）', { t: String(a.topic), n: pendingProposal.value.title })
      : String(a.topic)
    topicStep = { label: t('确定课题'), state: 'done', detail, isTopic: true }
  } else if (pendingProposal.value) {
    topicStep = { label: t('确定课题'), state: 'current', detail: t('自选课题审核中：《{t}》', { t: pendingProposal.value.title }) }
  } else if (rejectedProposal.value) {
    topicStep = { label: t('确定课题'), state: 'current',
      detail: t('自选课题已打回：{r}（可到「自选课题」修改后重新提交）', { r: rejectedProposal.value.teacherReply || '' }) }
  } else {
    topicStep = { label: t('确定课题'), state: 'current', detail: t('可在学院课题库申领，或在「自选课题」自拟提交导师审批') }
  }
  // 「开题与中期」「答辩毕业」：由毕业工作台里程碑驱动（同一接口同口径），不再写死
  const msOf = (st: string) => flowMs.value.find((m: any) => m.stage === st)
  const opening = msOf('opening'), midterm = msOf('midterm'), fin = msOf('final'), defense = msOf('defense')
  let s4: { label: any; state: string; detail?: string }
  if (opening?.status === 'passed' && midterm?.status === 'passed') {
    s4 = { label: t('开题与中期'), state: 'done', detail: t('开题报告与中期检查均已通过') }
  } else if (hasTopic && !pendingProposal.value) {
    s4 = { label: t('开题与中期'), state: 'current', detail: t('在「毕业设计工作台」推进进度') }
  } else {
    s4 = { label: t('开题与中期'), state: 'todo' }
  }
  let s5: { label: any; state: string; detail?: string }
  if (defense?.status === 'passed') {
    s5 = { label: t('答辩毕业'), state: 'done',
      detail: defense.totalScore != null ? t('答辩通过 · 毕设总评 {n} 分', { n: defense.totalScore }) : t('答辩已通过') }
  } else if ((fin && fin.status !== 'none') || (defense && defense.status !== 'none')) {
    s5 = { label: t('答辩毕业'), state: 'current', detail: t('论文终稿与答辩推进中') }
  } else {
    s5 = { label: t('答辩毕业'), state: 'todo' }
  }
  return [
    { label: t('提交志愿'), state: 'done', detail: fmtTime(a.createdAt) },
    { label: t('导师确认'), state: 'done', detail: fmtTime(a.updatedAt) },
    topicStep,
    s4, s5,
  ]
})
const nextStepHint = computed(() => {
  if (pendingProposal.value) {
    return currentTopic.value ? t('等待导师审核换题申请') : t('等待导师审核自选课题')
  }
  if (rejectedProposal.value && !currentTopic.value) return t('修改自选课题后重新提交')
  const cur = flowSteps.value.find(s => s.state === 'current')
  if (!cur) return ''
  return cur.label === t('确定课题') ? t('确定毕业设计课题') : cur.label
})

/* ===== 筛选后的列表：余位降序，有余位在前 ===== */
const filteredAdvisors = computed(() => {
  const kw = fKw.value.trim().toLowerCase()
  return [...advisors.value]
    .filter(a => {
      if (kw && !((a.teacherName || '').toLowerCase().includes(kw) || String(a.teacherNo || '').toLowerCase().includes(kw))) return false
      if (fCollege.value && a.college !== fCollege.value) return false
      if (fMajor.value && a.major !== fMajor.value) return false
      if (fCap.value === 'open' && !a.available) return false
      if (fCap.value === 'full' && a.available) return false
      return true
    })
    .sort((a, b) => (Number(b.available) - Number(a.available)) || (remainOf(b) - remainOf(a)))
})

function fmtTime(v: any) {
  if (!v) return ''
  try { return new Date(v).toLocaleString('zh-CN', { hour12: false }) } catch { return '' }
}

async function load() {
  try {
    // 导师名单 / 我的志愿 是学生专属接口，教师端只管课题库，避免发出必然 403 的请求
    const isStu = auth.user?.role === 'student'
    const [a, m, c, p] = await Promise.all([
      isStu ? http.get('/advisor/teachers').catch(() => null) : Promise.resolve(null),
      isStu ? http.get('/advisor/mine').catch(() => null) : Promise.resolve(null),
      http.get('/advisor/config').catch(() => null),
      isStu ? http.get('/advisor/proposals/mine').catch(() => null) : Promise.resolve(null),
    ])
    advisors.value = a?.data || []
    cfg.value = c?.data || {}
    // /advisor/mine 返回全部志愿记录（后端按志愿序升序）
    myAdviseList.value = Array.isArray(m?.data) ? m.data : []
    proposals.value = p?.data || []
    if (isStu) await loadFlowMs()   // 双选流程时间线与毕业工作台里程碑保持同步
  } catch {}
}

async function apply(a: any) {
  const n = pendingCount.value + 1
  try { await ElMessageBox.confirm(t('确定将 {n} 设为第{c}志愿？任一志愿被导师确认后，其余志愿将自动失效。', { n: a.teacherName, c: n }), t('提交志愿'), { type: 'warning', confirmButtonText: t('确定'), cancelButtonText: t('取消') }) } catch { return }
  try { await http.post('/advisor/apply', { teacherId: a.teacherId }); ElMessage.success(t('已提交为第{n}志愿，等待导师审批', { n })); load() }
  catch (e: any) { ElMessage.error(e?.message || t('申请失败')) }
}

async function cancelApply(g: any) {
  if (!g) return
  try { await ElMessageBox.confirm(t('确定撤销对「{n}」的志愿？撤销后志愿序不变，可继续申请新导师。', { n: g.teacherName || '' }), t('撤销志愿'), { type: 'warning', confirmButtonText: t('确定'), cancelButtonText: t('取消') }) } catch { return }
  cancelling.value = true
  try {
    await http.post('/advisor/cancel/' + g.id)
    ElMessage.success(t('志愿已撤销'))
    load()
  } catch (e: any) { ElMessage.error(e?.message || t('撤销失败')) } finally { cancelling.value = false }
}

onMounted(() => {
  auth.loadFromStorage()
  // 教师默认打开本院课题库 Tab；导师列表由后端按学生学院隔离
  loadTopics().then(() => {
    if (isTeacher.value) {
      const c = auth.user?.college
      pageTab.value = (c && topicColleges.value.includes(c)) ? c : (topicColleges.value[0] || 'select')
    }
  })
  load()
})
</script>

<style scoped>
.page-wrap { padding: 4px 0; }
.stat-sub { font-size: 12px; color: var(--text-lighter); margin-top: 2px; }

/* ===== 页面级 Tab ===== */
.aa-tabs {
  display: flex; gap: 8px; flex-wrap: wrap; align-items: center;
  padding: 8px 10px; margin-bottom: 16px;
  background: var(--card-bg); border: 1px solid var(--border); border-radius: 14px;
}
.aa-tab {
  padding: 8px 18px; border-radius: 999px; font-size: 13px; font-weight: 600;
  color: var(--text-light); cursor: pointer; user-select: none;
  transition: all .18s; border: 1.5px solid transparent;
}
.aa-tab:hover { color: var(--primary); background: color-mix(in srgb, var(--primary) 7%, transparent); }
.aa-tab.active {
  color: #fff; background: linear-gradient(135deg, #2bb187, #38bdf8);
  box-shadow: 0 4px 12px -4px rgba(43, 177, 135, .5);
}

/* ===== 课题库 ===== */
.aa-topic-pub { border-left: 3px solid var(--primary); }
.at-pub-grid {
  display: grid; grid-template-columns: 1fr 260px; gap: 14px 16px;
  padding: 14px 16px; border: 1.5px solid var(--border); border-radius: 13px;
  background: color-mix(in srgb, var(--primary) 2.5%, var(--card-bg));
}
.at-pub-field { display: flex; flex-direction: column; gap: 6px; min-width: 0; }
.at-pub-field.at-span2 { grid-column: span 2; }
.at-pub-field label {
  display: flex; align-items: center; justify-content: space-between;
  font-size: 12px; font-weight: 700; color: var(--text-light); letter-spacing: .3px;
}
.at-pub-field label i { color: #f43f5e; font-style: normal; margin-left: 2px; }
.at-count { font-size: 11px; font-weight: 500; color: var(--text-lighter); }
.at-pub-field .form-input { font-size: 13.5px; }
.at-pub-field textarea.form-input { min-height: 96px; }
.at-own-badge {
  align-self: flex-start;
  font-size: 12.5px; font-weight: 700; color: var(--primary);
  background: color-mix(in srgb, var(--primary) 9%, transparent);
  padding: 9px 14px; border-radius: 11px;
  border: 1px dashed color-mix(in srgb, var(--primary) 40%, transparent);
}
.at-own-badge.static { cursor: default; }
.at-pub-foot { display: flex; align-items: center; gap: 14px; margin-top: 13px; flex-wrap: wrap; }
.at-pub-btn {
  border-radius: 12px; padding: 10px 26px; font-size: 13.5px; font-weight: 700;
  background: linear-gradient(135deg, #2bb187, #38bdf8);
  border: none; color: #fff; box-shadow: 0 6px 16px -6px rgba(43, 177, 135, .55);
}
.at-pub-btn:hover { transform: translateY(-1px); box-shadow: 0 9px 20px -6px rgba(43, 177, 135, .6); }
.at-pub-btn:disabled { opacity: .5; transform: none; box-shadow: none; }
.aa-topic-grid { display: grid; gap: 13px; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); }
.aa-topic-card {
  border: 1px solid var(--border); border-radius: 14px; padding: 14px 16px 12px;
  background: var(--card-bg); display: flex; flex-direction: column; gap: 8px;
  transition: transform .2s, border-color .2s, box-shadow .2s;
}
.aa-topic-card:hover { transform: translateY(-3px); border-color: color-mix(in srgb, var(--primary) 40%, var(--border)); box-shadow: 0 10px 24px -12px rgba(43, 177, 135, .4); }
.aa-topic-card.taken { border-style: dashed; opacity: .8; }
.aa-topic-card.mine { border-color: color-mix(in srgb, var(--primary) 50%, var(--border)); }
.at-head { display: flex; align-items: center; justify-content: space-between; }
.at-dir {
  font-size: 11.5px; font-weight: 700; color: #7c3aed;
  background: rgba(124, 58, 237, .1); padding: 2px 10px; border-radius: 999px;
}
.at-status { font-size: 11.5px; font-weight: 700; color: #059669; background: rgba(16, 185, 129, .12); padding: 2px 10px; border-radius: 999px; }
.at-status.taken { color: #64748b; background: rgba(148, 163, 184, .15); }
.at-title { font-size: 14.5px; font-weight: 700; color: var(--text); line-height: 1.45; }
.at-desc {
  font-size: 12.5px; color: var(--text-light); line-height: 1.55;
  display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden;
}
.at-teacher { display: flex; align-items: center; gap: 7px; font-size: 12.5px; color: var(--text-light); }
.at-avatar {
  width: 22px; height: 22px; border-radius: 50%; font-size: 11px; font-weight: 700; color: #fff;
  background: linear-gradient(135deg, #16a34a, #22c55e);
  display: inline-flex; align-items: center; justify-content: center;
}
.at-foot { display: flex; align-items: center; gap: 8px; margin-top: auto; padding-top: 4px; }
.at-apply { flex: 1; justify-content: center; border-radius: 10px; }
.at-apply:disabled { opacity: .55; cursor: not-allowed; }
.at-del { color: #be123c; border-color: rgba(244, 63, 94, .4); }
.at-del:hover { border-color: #f43f5e; background: rgba(244, 63, 94, .06); }
.at-readonly { font-size: 12px; color: var(--text-lighter); }

/* 已关闭提示 */
.aa-closed-tip {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 16px; margin-bottom: 14px;
  border: 1px solid rgba(245, 158, 11, .35); border-radius: 12px;
  background: rgba(245, 158, 11, .08); color: #b45309; font-size: 13px; font-weight: 600;
}
.aa-closed-tip .icon { width: 15px; height: 15px; }

/* ===== 我的志愿卡 ===== */
.aa-mine { border-left: 3px solid var(--primary); }
.aa-status-badge {
  font-size: 12px; font-weight: 700; padding: 3px 12px; border-radius: 999px; flex-shrink: 0;
}
.aa-status-badge.pending { color: #b45309; background: rgba(245, 158, 11, .13); border: 1px solid rgba(245, 158, 11, .3); }
.aa-status-badge.approved { color: #059669; background: rgba(16, 185, 129, .13); border: 1px solid rgba(16, 185, 129, .3); }
.aa-status-badge.rejected { color: #be123c; background: rgba(244, 63, 94, .11); border: 1px solid rgba(244, 63, 94, .3); }
.aa-status-badge.cancelled { color: #64748b; background: rgba(148, 163, 184, .14); border: 1px solid rgba(148, 163, 184, .3); }
.aa-status-badge.sm { padding: 1px 9px; font-size: 11.5px; }
.aa-status-badge.full-tag { color: #64748b; background: rgba(148, 163, 184, .14); border: 1px dashed rgba(148, 163, 184, .45); }
.aa-choice-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 12px; }
.aa-choice-card {
  border: 1px solid var(--border); border-radius: 13px; padding: 13px 15px;
  background: color-mix(in srgb, var(--primary) 3%, var(--card-bg));
  display: flex; flex-direction: column; gap: 9px;
}
.aa-choice-card.approved { border-color: color-mix(in srgb, var(--primary) 55%, var(--border)); background: color-mix(in srgb, var(--primary) 7%, var(--card-bg)); }
.aa-choice-card.rejected { opacity: .75; border-style: dashed; }
.aa-choice-head { display: flex; align-items: center; justify-content: space-between; }
.aa-choice-pri {
  font-size: 12px; font-weight: 800; color: var(--primary);
  background: color-mix(in srgb, var(--primary) 12%, transparent);
  border-radius: 8px; padding: 2px 10px;
}
.aa-choice-pri.approved { background: var(--primary); color: #fff; }
.aa-choice-pri.rejected { color: #be123c; background: rgba(244, 63, 94, .1); }
.aa-choice-head .aa-status-badge.approved { color: #059669; background: rgba(16, 185, 129, .13); }
.aa-choice-head .aa-status-badge.pending { color: #b45309; background: rgba(245, 158, 11, .13); }
.aa-choice-head .aa-status-badge.rejected { color: #be123c; background: rgba(244, 63, 94, .11); }
.aa-choice-name { display: flex; align-items: center; gap: 10px; }
.advisor-avatar.sm { width: 36px; height: 36px; font-size: 15px; }
.aa-choice-teacher { font-size: 14px; font-weight: 700; color: var(--text); }
.aa-choice-no { font-size: 11.5px; color: var(--text-lighter); margin-top: 1px; }
.aa-choice-field { font-size: 12.5px; }
.aa-choice-field label { color: var(--text-lighter); font-size: 11px; margin-right: 6px; }
.aa-choice-field span { color: var(--text-light); word-break: break-all; }
.aa-choice-field .aa-reply { color: var(--primary); font-weight: 600; }
.aa-choice-foot { display: flex; align-items: center; justify-content: space-between; margin-top: auto; }
.aa-choice-time { font-size: 11.5px; color: var(--text-lighter); }
.aa-cancel { color: #be123c; border-color: rgba(244, 63, 94, .4); }
.aa-cancel:hover { border-color: #f43f5e; background: rgba(244, 63, 94, .06); }
.aa-choice-card.empty-slot {
  align-items: center; justify-content: center; text-align: center;
  border-style: dashed; color: var(--text-lighter); background: transparent; gap: 4px;
}
.aa-slot-plus { font-size: 26px; font-weight: 300; color: var(--text-light); line-height: 1; }
.aa-slot-text { font-size: 13px; font-weight: 600; color: var(--text-light); }
.aa-slot-hint { font-size: 11.5px; color: var(--text-lighter); }

/* ===== 双选完成：流程面板（左确认卡 + 右流程，填充空旷区域） ===== */
.aa-choice-grid.is-done { grid-template-columns: minmax(270px, 370px) minmax(0, 1fr); align-items: stretch; }
.aa-flow-card {
  border: 1px solid color-mix(in srgb, var(--primary) 26%, var(--border));
  border-radius: 13px; padding: 14px 18px 12px;
  background: linear-gradient(150deg, color-mix(in srgb, var(--primary) 6%, var(--card-bg)), var(--card-bg));
  display: flex; flex-direction: column; min-width: 0;
}
.aa-flow-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; flex-wrap: wrap; margin-bottom: 10px; }
.aa-flow-title { font-size: 13.5px; font-weight: 800; color: var(--text); }
.aa-flow-next {
  font-size: 12px; font-weight: 600; color: #b45309;
  background: rgba(245, 158, 11, .12); border: 1px solid rgba(245, 158, 11, .35);
  padding: 2px 11px; border-radius: 999px;
}
.aa-flow-steps { display: flex; flex-direction: column; flex: 1; }
.aa-flow-step { display: flex; gap: 12px; min-height: 44px; }
.aa-flow-step:last-child { min-height: 0; }
.aa-flow-rail { display: flex; flex-direction: column; align-items: center; flex-shrink: 0; width: 24px; }
.aa-flow-dot {
  width: 22px; height: 22px; border-radius: 50%; font-size: 11.5px; font-weight: 800;
  display: inline-flex; align-items: center; justify-content: center; font-style: normal;
  background: rgba(148, 163, 184, .16); color: transparent; border: 1.5px solid rgba(148, 163, 184, .4);
  flex-shrink: 0;
}
.aa-flow-step.done .aa-flow-dot {
  background: linear-gradient(135deg, #10b981, #34d399); border-color: transparent; color: #fff;
  box-shadow: 0 3px 9px -3px rgba(16, 185, 129, .6);
}
.aa-flow-step.current .aa-flow-dot {
  background: #fff; border: 2px solid #f59e0b; color: #f59e0b;
  box-shadow: 0 0 0 4px rgba(245, 158, 11, .14);
  animation: aaFlowPulse 2.2s ease-in-out infinite;
}
@keyframes aaFlowPulse {
  0%, 100% { box-shadow: 0 0 0 3px rgba(245, 158, 11, .12); }
  50%      { box-shadow: 0 0 0 6px rgba(245, 158, 11, .2); }
}
.aa-flow-line { flex: 1; width: 2px; min-height: 18px; border-radius: 2px; background: rgba(148, 163, 184, .22); margin: 3px 0; }
.aa-flow-line.filled { background: linear-gradient(180deg, #10b981, rgba(16, 185, 129, .35)); }
.aa-flow-main { padding: 1px 0 10px; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.aa-flow-main b { font-size: 13px; font-weight: 700; color: var(--text); }
.aa-flow-step.todo .aa-flow-main b { color: var(--text-lighter); font-weight: 600; }
.aa-flow-step.current .aa-flow-main b { color: #d97706; }
.aa-flow-detail { font-size: 11.5px; color: var(--text-light); line-height: 1.5; word-break: break-all; }
.aa-flow-detail.is-topic { color: var(--primary); font-weight: 700; }
@media (prefers-reduced-motion: reduce) { .aa-flow-step.current .aa-flow-dot { animation: none; } }

@media (max-width: 1100px) {
  .aa-choice-grid, .aa-choice-grid.is-done { grid-template-columns: 1fr; }
}

/* ===== 筛选栏 ===== */
.aa-filter { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; margin-bottom: 8px; }
.aa-input {
  padding: 7px 12px; border: 1.5px solid var(--border); border-radius: 10px;
  font-size: 13px; background: var(--card); color: var(--text); outline: none;
  transition: border-color .18s, box-shadow .18s; font-family: inherit;
}
.aa-input:focus { border-color: var(--primary); box-shadow: 0 0 0 3px rgba(43, 177, 135, .12); }
.aa-search { flex: 1 1 180px; min-width: 160px; max-width: 280px; }
.aa-cap-sel { flex-shrink: 0; }
.aa-clear {
  border: none; background: transparent; color: var(--text-light); font-size: 12.5px;
  cursor: pointer; padding: 6px 8px; border-radius: 8px;
}
.aa-clear:hover { color: var(--primary); background: rgba(43, 177, 135, .08); }
.aa-filter-result { font-size: 12px; color: var(--text-lighter); margin-bottom: 12px; }
/* 本院导师锁定徽章（与课题库发布表单的学院徽章同款） */
.aa-college-lock { flex-shrink: 0; }

/* ===== 导师卡网格 ===== */
.advisor-grid { display: grid; gap: 12px; grid-template-columns: repeat(auto-fill, minmax(272px, 1fr)); }
.advisor-card {
  background: var(--card-bg); border: 1px solid var(--border); border-radius: 14px;
  padding: 15px 16px 13px; display: flex; flex-direction: column; gap: 7px;
  transition: transform .2s cubic-bezier(.2, .7, .3, 1), border-color .2s, box-shadow .2s;
}
.advisor-card:hover { transform: translateY(-3px); border-color: color-mix(in srgb, var(--primary) 40%, var(--border)); box-shadow: 0 10px 24px -12px rgba(43, 177, 135, .4); }
.advisor-card.full { opacity: .68; border-style: dashed; }
.advisor-card.full:hover { transform: none; box-shadow: none; }
.advisor-card.mine { border-color: color-mix(in srgb, var(--primary) 45%, var(--border)); }
.aa-card-top { display: flex; align-items: center; gap: 11px; }
.advisor-avatar {
  width: 44px; height: 44px; border-radius: 50%; flex-shrink: 0;
  background: linear-gradient(135deg, #16a34a, #22c55e);
  display: flex; align-items: center; justify-content: center;
  font-size: 18px; font-weight: 700; color: #fff;
}
.advisor-card.full .advisor-avatar { background: linear-gradient(135deg, #94a3b8, #cbd5e1); }
.aa-id-box { flex: 1; min-width: 0; }
.advisor-name { font-size: 14.5px; font-weight: 700; color: var(--text); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.advisor-no { font-size: 12px; color: var(--text-light); margin-top: 1px; }
.aa-meta-row { display: flex; align-items: baseline; gap: 7px; font-size: 12.5px; min-width: 0; }
.aa-meta-label { color: var(--text-lighter); flex-shrink: 0; font-size: 11.5px; }
.aa-meta-val { color: var(--text-light); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.aa-cap-bar { height: 7px; border-radius: 99px; background: rgba(148, 163, 184, .14); overflow: hidden; margin-top: 2px; }
.aa-cap-bar i { display: block; height: 100%; border-radius: 99px; background: linear-gradient(90deg, #10b981, #34d399); transition: width .6s cubic-bezier(.22, .9, .36, 1); }
.aa-cap-bar i.full { background: linear-gradient(90deg, #f59e0b, #fbbf24); }
.advisor-capacity { font-size: 12.5px; color: var(--text); }
.advisor-capacity b { color: var(--primary); font-size: 13.5px; }
.cap-detail { color: var(--text-light); font-size: 12px; }
.advisor-actions { margin-top: auto; }
.aa-apply-btn { width: 100%; justify-content: center; border-radius: 10px; }
.aa-apply-btn:disabled { opacity: .55; cursor: not-allowed; transform: none !important; box-shadow: none !important; }
.aa-no-match { padding: 30px 20px; }

.aa-summary { font-size: 12.5px; color: var(--text-light); }
.aa-summary b { color: var(--primary); margin: 0 2px; }
/* 只看我的导师开关 */
.aa-topic-head-right { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.aa-only-mine {
  display: inline-flex; align-items: center; gap: 6px; cursor: pointer;
  font-size: 12.5px; font-weight: 700; color: var(--primary);
  background: color-mix(in srgb, var(--primary) 10%, transparent);
  border: 1px solid color-mix(in srgb, var(--primary) 38%, transparent);
  border-radius: 999px; padding: 4px 12px; user-select: none;
}
.aa-only-mine input { accent-color: var(--primary); margin: 0; }

/* ===== 自选课题 ===== */
.aa-pending-tip {
  display: flex; align-items: center; gap: 8px; margin: 12px 0 2px;
  padding: 11px 15px; border-radius: 12px; font-size: 13px; font-weight: 600; color: #b45309;
  background: rgba(245, 158, 11, .1); border: 1px solid rgba(245, 158, 11, .35);
}
.aa-reject-reason {
  font-size: 12.5px; color: #be123c; line-height: 1.6;
  background: rgba(244, 63, 94, .08); border: 1px dashed rgba(244, 63, 94, .4);
  border-radius: 9px; padding: 9px 12px; word-break: break-all;
}
.aa-choice-card.approved { border-color: color-mix(in srgb, var(--primary) 55%, var(--border)); }
.aa-choice-card.rejected .at-title { color: var(--text-light); }
.aa-reject-text { color: #be123c; font-weight: 600; }
.aa-choice-actions { display: inline-flex; gap: 8px; }
.aa-choice-card.withdrawn { opacity: .65; border-style: dashed; }

/* 当前已确定课题状态卡 */
.aa-current-topic {
  display: flex; align-items: center; justify-content: space-between; gap: 14px; flex-wrap: wrap;
  padding: 13px 17px; margin-bottom: 14px;
  border: 1px solid color-mix(in srgb, var(--primary) 45%, var(--border));
  border-radius: 13px;
  background: linear-gradient(150deg, color-mix(in srgb, var(--primary) 8%, var(--card-bg)), var(--card-bg));
}
.aa-ct-main { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
.aa-ct-label { font-size: 12px; font-weight: 800; color: var(--primary); letter-spacing: .3px; }
.aa-ct-title { font-size: 15px; font-weight: 700; color: var(--text); line-height: 1.4; word-break: break-all; }
.aa-ct-meta { font-size: 11.5px; color: var(--text-lighter); }
.aa-ct-hint { font-size: 12.5px; color: var(--text-light); }
/* 记录卡上的「当前课题」标记 */
.aa-cur-tag {
  font-style: normal; font-size: 10.5px; font-weight: 700; color: var(--primary);
  background: color-mix(in srgb, var(--primary) 14%, transparent);
  border-radius: 999px; padding: 1px 7px; margin-left: 6px; vertical-align: middle;
}
.empty-state { text-align: center; padding: 60px 20px; color: var(--text-light); }
.empty-icon { font-size: 48px; opacity: 0.3; margin-bottom: 10px; }
.empty-title { font-size: 14px; font-weight: 600; }
</style>
