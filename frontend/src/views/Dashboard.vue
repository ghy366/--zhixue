<template>
  <div class="stats-page liquid">
    <!-- ===== 原版 home-hero 旋转流光欢迎条（三端通用） ===== -->
    <div class="home-hero">
      <div class="home-hero-glow"></div>
      <div class="home-hero-glow2"></div>
      <div class="home-hero-main">
        <div class="home-hero-hello">欢迎回来，<b>{{ auth.user?.name || '同学' }}</b></div>
        <div class="home-hero-sub">{{ roleLabel }} · 智学选课平台</div>
        <div class="home-hero-tip">{{ roleTip }}</div>
      </div>
      <div class="home-hero-stat">
        <div class="home-hero-stat-icon" v-html="svgIcon(heroIcon)" />
        <div class="home-hero-stat-value">{{ heroValue }}</div>
        <div class="home-hero-stat-label">{{ heroLabel }}</div>
      </div>
    </div>

    <!-- ===== 原版 stats-grid + stat-card ===== -->
    <div class="stats-grid">
      <div v-for="c in statCards" :key="c.label" class="stat-card">
        <div class="stat-icon" :class="c.color" v-html="svgIcon(c.icon)" />
        <div>
          <div class="stat-value">{{ c.value }}</div>
          <div class="stat-label">{{ c.label }}</div>
        </div>
      </div>
    </div>

    <!-- ===== 管理员端：轮次状态 + 时间线 ===== -->
    <template v-if="isAdmin">
      <div style="display:grid;grid-template-columns:1.2fr 1fr;gap:20px">
        <div class="admin-card round-status-card">
          <div class="rsc-head">
            <h3>当前选课状态</h3>
            <span class="status-tag" :class="systemConfig.roundOpen ? 'status-approved' : 'status-rejected'">
              {{ systemConfig.roundOpen ? '进行中' : '已关闭' }}
            </span>
          </div>
          <div class="rsc-steps">
            <template v-for="n in 3" :key="n">
              <div class="rsc-step" :class="{ done: n < +systemConfig.currentRound, active: n === +systemConfig.currentRound }">
                <div class="rsc-dot">{{ n < +systemConfig.currentRound ? '✓' : n }}</div>
                <div class="rsc-step-label">第 {{ n }} 轮</div>
              </div>
              <div class="rsc-line" v-if="n < 3" />
            </template>
          </div>
          <div class="rsc-stats">
            <div class="rsc-stat"><div class="rsc-stat-value">{{ adminOpenCourses }}</div><div class="rsc-stat-label">开放课程</div></div>
            <div class="rsc-stat"><div class="rsc-stat-value">{{ adminTotalEnrolled }}</div><div class="rsc-stat-label">本轮报名</div></div>
            <div class="rsc-stat"><div class="rsc-stat-value">{{ adminStudentCount }}</div><div class="rsc-stat-label">参与学生</div></div>
          </div>
        </div>
        <div class="admin-card">
          <h3>最近动态</h3>
          <div v-if="recentLogs.length" class="timeline">
            <div v-for="l in recentLogs" :key="l.id" class="tl-item">
              <div class="tl-dot" v-html="svgIcon(tlIcon(l.type))" />
              <div class="tl-body">
                <div class="tl-text"><b>{{ l.userName }}</b> {{ l.operation }}</div>
                <div class="tl-time">{{ timeAgo(l.createdAt) }}</div>
              </div>
            </div>
          </div>
          <div v-else class="empty-state">
            <div class="empty-icon" v-html="svgIcon('cpu')" />
            <p>暂无动态</p>
          </div>
        </div>
      </div>
    </template>

    <!-- ===== 学生端待办：待录取课程 ===== -->
    <div v-if="isStudent && studentPending.length" class="section">
      <div class="section-header">
        <div class="section-title"><span v-html="svgIcon('hourglass')" />我的待办 · 待录取课程</div>
        <div class="section-link" @click="$router.push('/my-enrollments')">查看全部 →</div>
      </div>
      <div class="todo-list">
        <div v-for="e in studentPending" :key="e.courseId" class="todo-item">
          <div class="todo-main">
            <b>{{ e.courseName }}</b>
            <span class="todo-sub">{{ e.teacherName || '—' }} · 投入 {{ e.points ?? e.invested ?? 0 }} 积分</span>
          </div>
          <span class="status-tag status-pending">待录取</span>
        </div>
      </div>
    </div>

    <!-- ===== 学生端已录取课程 ===== -->
    <div v-if="isStudent" class="section">
      <div class="section-header">
        <div class="section-title"><span v-html="svgIcon('check-circle')" />我已录取的课程</div>
        <div class="section-link" @click="$router.push('/my-enrollments')">查看全部 →</div>
      </div>
      <div v-if="approvedList.length" class="course-grid">
        <div v-for="c in approvedList" :key="c.courseId" class="course-card">
          <div class="course-head">
            <div class="course-name">{{ c.courseName }}</div>
            <div class="course-cat" :class="'cat-' + (c.category || '其他')">{{ c.category || '其他' }}</div>
          </div>
          <div class="course-meta">
            <span v-html="svgIcon('user')" /> {{ c.teacherName || '—' }} · {{ c.credit || 0 }} 学分
          </div>
          <div class="course-status">
            <span class="status-tag status-approved">已录取</span>
          </div>
        </div>
      </div>
      <div v-else class="empty-state">
        <div class="empty-icon" v-html="svgIcon('inbox')" />
        <p>还没有被录取的课程</p>
      </div>
    </div>

    <!-- ===== 教师端待办：超容量待审批 ===== -->
    <div v-if="isTeacher && pendingCourses.length" class="section">
      <div class="section-header">
        <div class="section-title"><span v-html="svgIcon('hourglass')" />待办 · 超容量待审批</div>
        <div class="section-link" @click="$router.push('/teach-courses')">去处理 →</div>
      </div>
      <div class="todo-list">
        <div v-for="c in pendingCourses" :key="c.id" class="todo-item">
          <div class="todo-main">
            <b>{{ c.name }}</b>
            <span class="todo-sub">报名 {{ c.stat?.total || 0 }} / 容量 {{ c.capacity }}</span>
          </div>
          <span class="status-tag status-warning">超出 {{ (c.stat?.total || 0) - (c.capacity || 0) }} 人</span>
        </div>
      </div>
    </div>

    <!-- ===== 教师端：我教授的课程 ===== -->
    <div v-if="isTeacher" class="section">
      <div class="section-header">
        <div class="section-title"><span v-html="svgIcon('book-open')" />我教授的课程</div>
        <div class="section-link" @click="$router.push('/teach-courses')">管理 →</div>
      </div>
      <div v-if="teacherCourses.length" class="course-grid">
        <div v-for="c in teacherCourses" :key="c.id" class="course-card" :class="{ 'course-req': (c.category || '').includes('必修') }">
          <span class="course-cat" :class="'cat-' + (c.category || '其他')">{{ c.category || '其他' }}</span>
          <div class="course-name">{{ c.name }}</div>
          <div class="course-teacher"><span v-html="svgIcon('user')" /> {{ c.teacherName || '—' }} · {{ c.credit || 0 }} 学分</div>
          <div class="course-meta">
            <span><span v-html="svgIcon('clock')" /> {{ c.time || '时间待安排' }}</span>
            <span><span v-html="svgIcon('map-pin')" /> {{ c.location || '教室待定' }}</span>
          </div>
          <div class="course-desc">{{ c.intro || '暂无课程简介' }}</div>
          <div class="course-footer">
            <div style="flex:1;display:flex;align-items:center;gap:8px;">
              <div class="progress-bar"><div class="progress-fill" :style="{ width: capPercent(c) + '%' }" /></div>
              <span class="enroll-count">{{ c.stat?.total || 0 }}/{{ c.capacity }}</span>
            </div>
            <span v-if="c.oversubscribed" class="status-tag status-pending">超容量待审批</span>
            <span v-else class="status-tag status-approved">正常</span>
          </div>
        </div>
      </div>
      <div v-else class="empty-state">
        <div class="empty-icon" v-html="svgIcon('book-open')" />
        <p>暂无教授课程</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import http from '@/api'

const auth = useAuthStore()
const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`

const systemConfig = reactive<Record<string, any>>({})
const role = computed(() => auth.user?.role || 'student')
const isStudent = computed(() => role.value === 'student')
const isTeacher = computed(() => role.value === 'teacher')
const isAdmin = computed(() => role.value === 'admin')

const roleLabel = computed(() => isStudent.value ? '同学' : isTeacher.value ? '老师' : '管理员')
const roleTip = computed(() =>
  isStudent.value ? '愿你今天选到心仪的好课 ✨' :
  isTeacher.value ? '愿你今天工作顺利 ✨' :
  '高效管理选课系统 ✨'
)

// ===== 原版 API 数据 =====
const remaining = ref(0)         // 学生：剩余积分
const POINTS_PER_ROUND = 50

const studentApproved = ref<any[]>([])
const studentPending = ref<any[]>([])
const studentCredits = ref(0)

const teacherCourses = ref<any[]>([])

const adminUsers = ref<any[]>([])
const adminCourses = ref<any[]>([])
const recentLogs = ref<any[]>([])

// ===== 原版 home-hero 数据 =====
const heroIcon = computed(() =>
  isStudent.value ? 'coins' :
  isTeacher.value ? 'book-open' :
  'users'
)
const heroValue = computed(() =>
  isStudent.value ? remaining.value :
  isTeacher.value ? teacherCourses.value.length :
  adminStudentCount.value
)
const heroLabel = computed(() =>
  isStudent.value ? `本轮剩余积分` :
  isTeacher.value ? `我教授的课程` :
  `学生总数`
)

// ===== 原版 stats-grid 4 卡 =====
const statCards = computed(() => {
  if (isStudent.value) {
    return [
      { icon: 'coins',     color: 'amber', value: remaining.value,        label: `本轮剩余积分 / ${POINTS_PER_ROUND}` },
      { icon: 'book',      color: 'blue',  value: studentApproved.value.length, label: '已录取课程' },
      { icon: 'hourglass', color: 'amber', value: studentPending.value.length, label: '待录取课程' },
      { icon: 'star',      color: 'green', value: studentCredits.value,   label: '已获学分' },
    ]
  }
  if (isTeacher.value) {
    const totalEnrolled = teacherCourses.value.reduce((s: number, c: any) => s + (c.stat?.total || 0), 0)
    const pendingCourses = teacherCourses.value.filter((c: any) => c.oversubscribed).length
    return [
      { icon: 'book-open', color: 'blue',  value: teacherCourses.value.length, label: '我教授的课程' },
      { icon: 'users',     color: 'green', value: totalEnrolled,          label: '总报名人数' },
      { icon: 'hourglass', color: 'amber', value: pendingCourses,         label: '待审批课程' },
    ]
  }
  // admin
  return [
    { icon: 'users',  color: 'blue',   value: adminStudentCount.value, label: '学生总数' },
    { icon: 'user',   color: 'green',  value: adminTeacherCount.value, label: '教师总数' },
    { icon: 'book',   color: 'purple', value: adminCourses.value.length, label: '课程总数' },
    { icon: 'edit',   color: 'amber',  value: adminTotalEnrolled.value, label: '选课记录' },
  ]
})

const adminStudentCount = computed(() => adminUsers.value.filter((u: any) => u.role === 'student').length)
const adminTeacherCount = computed(() => adminUsers.value.filter((u: any) => u.role === 'teacher').length)
const adminTotalEnrolled = computed(() => adminCourses.value.reduce((s: number, c: any) => s + (c.stat?.total || 0), 0))
const adminOpenCourses = computed(() => adminCourses.value.length)
const approvedList = computed(() => studentApproved.value)
const pendingCourses = computed(() => teacherCourses.value.filter((c: any) => c.oversubscribed))

// ===== 老师课程卡：容量进度百分比 =====
function capPercent(c: any) {
  const cap = c.capacity || 0
  if (!cap) return 0
  return Math.min(100, Math.round(((c.stat?.total || 0) / cap) * 100))
}

// ===== 原版时间线 icon =====
function tlIcon(type: string) {
  const m: Record<string, string> = { '登录': 'user', '选课': 'book', '退课': 'x-circle', '审批': 'check-circle' }
  return m[type] || 'cpu'
}
function timeAgo(s: any) {
  if (!s) return ''
  const t = typeof s === 'number' ? new Date(s) : new Date(String(s).replace(' ', 'T'))
  if (isNaN(t.getTime())) return String(s)
  const m = Math.floor((Date.now() - t.getTime()) / 60000)
  if (m < 1) return '刚刚'
  if (m < 60) return m + ' 分钟前'
  const h = Math.floor(m / 60)
  if (h < 24) return h + ' 小时前'
  return Math.floor(h / 24) + ' 天前'
}

async function loadData() {
  try {
    const r: any = await http.get('/system/config')
    Object.assign(systemConfig, r?.data || {})
  } catch {}

  if (isStudent.value) {
    try {
      const [pR, mR] = await Promise.all([
        http.get('/enrollments/points').catch(() => null),
        http.get('/enrollments/mine').catch(() => ({ data: [] })),
      ])
      remaining.value = pR?.data?.remaining ?? 60
      const mine: any[] = mR?.data || []
      studentApproved.value = mine.filter(e => e.status === 'approved')
      studentPending.value = mine.filter(e => e.status === 'pending')
      studentCredits.value = studentApproved.value.reduce((s: number, e: any) => s + (e.credit || 0), 0)
    } catch {}
  } else if (isTeacher.value) {
    try {
      const r: any = await http.get(`/courses/teacher/${auth.user?.id}`)
      teacherCourses.value = r?.data || []
    } catch {}
  } else if (isAdmin.value) {
    try {
      const [uR, cR, lR] = await Promise.all([
        http.get('/admin/users').catch(() => ({ data: [] })),
        http.get('/courses?category=全部').catch(() => ({ data: [] })),
        http.get('/logs').catch(() => ({ data: [] })),
      ])
      adminUsers.value = uR?.data || []
      adminCourses.value = cR?.data || []
      recentLogs.value = (lR?.data || []).slice(0, 6)
    } catch {}
  }
}

onMounted(() => {
  auth.loadFromStorage()
  loadData()
})
</script>
