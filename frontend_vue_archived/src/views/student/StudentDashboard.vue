<template>
  <div class="page">
    <div class="page-head">
      <h1>你好，{{ auth.user?.name }}</h1>
      <p>当前第 {{ sys?.currentRound || '-' }} 轮选课{{ sys?.roundOpen ? '进行中' : '已关闭' }}，合理规划你的积分与课程。</p>
    </div>

    <div class="stats-grid">
      <div class="stat-card"><div class="stat-value">{{ points?.remaining ?? '—' }}</div><div class="stat-label">剩余积分</div></div>
      <div class="stat-card"><div class="stat-value">{{ mine.length }}</div><div class="stat-label">已选课程</div></div>
      <div class="stat-card"><div class="stat-value">{{ credits }}</div><div class="stat-label">已选学分</div></div>
      <div class="stat-card"><div class="stat-value">{{ sys?.maxCreditPerSemester ?? '—' }}</div><div class="stat-label">学分上限</div></div>
    </div>

    <div class="card">
      <div class="card-header"><h3>快捷入口</h3></div>
      <div class="quick-grid">
        <button class="quick-tile" @click="go('browse')"><Icon name="grid" /><span>课程浏览</span></button>
        <button class="quick-tile" @click="go('mycourses')"><Icon name="clipboard" /><span>我的课程</span></button>
        <button class="quick-tile" @click="go('schedule')"><Icon name="calendar" /><span>我的课表</span></button>
        <button class="quick-tile" @click="go('scores')"><Icon name="award" /><span>成绩查询</span></button>
        <button class="quick-tile" @click="go('graduation')"><Icon name="target" /><span>毕业进度</span></button>
        <button class="quick-tile" @click="go('advisor')"><Icon name="graduation-cap" /><span>毕业设计</span></button>
      </div>
    </div>

    <div class="card">
      <div class="card-header"><h3>已选课程</h3><button class="btn btn-outline btn-sm" @click="go('mycourses')">查看全部</button></div>
      <div v-if="!mine.length" class="empty">还没有选课，去「课程浏览」挑选吧。</div>
      <div class="course-grid" v-else>
        <div class="course-card" v-for="c in mine.slice(0, 6)" :key="c.courseId || c.id">
          <div class="course-name">{{ c.courseName || c.name }}</div>
          <div class="course-meta">{{ c.teacherName || c.teacher || '—' }} · {{ c.credit }} 学分</div>
          <div class="course-meta">{{ c.time || c.courseTime || '—' }}</div>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="card-header">
        <h3>最新公告</h3>
        <a class="section-link" @click="openAnns">查看全部 →</a>
      </div>
      <div v-if="!anns.length" class="empty">暂无公告</div>
      <div v-else>
        <div v-for="a in anns" :key="a.id" class="announce-card" style="margin-bottom:10px">
          <div class="announce-head"><b>{{ a.title }}</b><span class="status-tag status-approved" style="margin-left:8px">{{ a.type || '系统公告' }}</span></div>
          <div class="announce-body">{{ a.content }}</div>
        </div>
      </div>
    </div>

    <div class="modal-mask" v-if="annModal" @click.self="annModal = false">
      <div class="modal" style="max-width:640px;text-align:left">
        <div class="modal-header"><h3>系统公告</h3><button class="modal-close" @click="annModal = false">✕</button></div>
        <div class="modal-body" style="max-height:60vh;overflow:auto">
          <div v-if="!allAnns.length" style="color:var(--text-light);font-size:13px">暂无公告</div>
          <div v-for="a in allAnns" :key="a.id" class="announce-card" style="margin-bottom:10px">
            <div class="announce-head"><b>{{ a.title }}</b><span class="status-tag status-approved" style="margin-left:8px">{{ a.type || '系统公告' }}</span></div>
            <div class="announce-body">{{ a.content }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/Icon.vue'
import { api } from '@/api/client'
import { useAuthStore } from '@/store/auth'

const router = useRouter()
const auth = useAuthStore()
const points = ref(null)
const mine = ref([])
const sys = ref(null)
const anns = ref([])
const allAnns = ref([])
const annModal = ref(false)

const credits = computed(() => mine.value.reduce((s, c) => s + (Number(c.credit) || 0), 0))
function go(p) { router.push('/' + p) }

async function openAnns() {
  const r = await api('GET', '/announcements')
  allAnns.value = r.success ? r.data || [] : []
  annModal.value = true
  await api('POST', '/announcements/read-all')
}

onMounted(async () => {
  const [pR, mR, sR, aR] = await Promise.all([
    api('GET', '/enrollments/points'),
    api('GET', '/enrollments/mine'),
    api('GET', '/system/config'),
    api('GET', '/announcements?limit=3'),
  ])
  if (pR.success) points.value = pR.data
  if (mR.success) mine.value = mR.data || []
  if (sR.success) sys.value = sR.data
  if (aR.success) anns.value = aR.data || []
})
</script>
