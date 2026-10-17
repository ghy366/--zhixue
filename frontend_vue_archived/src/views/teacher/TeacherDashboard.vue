<template>
  <div class="page">
    <div class="page-head"><h1>教师工作台</h1><p>管理你开设的课程、审批选课与成绩。</p></div>
    <div class="stats-grid">
      <div class="stat-card"><div class="stat-value">{{ myCourses.length }}</div><div class="stat-label">我开设的课程</div></div>
      <div class="stat-card"><div class="stat-value">{{ totalStudents }}</div><div class="stat-label">已录取学生</div></div>
      <div class="stat-card"><div class="stat-value">{{ pendingCount }}</div><div class="stat-label">待审批</div></div>
      <div class="stat-card"><div class="stat-value">{{ sys?.currentRound ?? '-' }}</div><div class="stat-label">当前轮次</div></div>
    </div>
    <div class="card">
      <div class="card-header"><h3>我的课程</h3><button class="btn btn-outline btn-sm" @click="go('teach-courses')">管理</button></div>
      <div v-if="!myCourses.length" class="empty">暂无开设课程。</div>
      <div class="course-grid" v-else>
        <div class="course-card" v-for="c in myCourses.slice(0, 6)" :key="c.id">
          <div class="course-name">{{ c.name }}</div>
          <div class="course-meta">{{ c.category }} · {{ c.credit }} 学分</div>
          <div class="course-meta">{{ c.stat?.total ?? 0 }} 人报名 · 已录取 {{ c.stat?.approved ?? 0 }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '@/api/client'
import { useAuthStore } from '@/store/auth'

const router = useRouter()
const auth = useAuthStore()
const myCourses = ref([])
const sys = ref(null)
const totalStudents = computed(() => myCourses.value.reduce((s, c) => s + (Number(c.stat?.approved) || 0), 0))
const pendingCount = computed(() => myCourses.value.reduce((s, c) => s + (Number(c.stat?.pending) || 0), 0))
function go(p) { router.push('/' + p) }

onMounted(async () => {
  const [cR, sR] = await Promise.all([
    api('GET', '/courses/teacher/' + auth.user.id),
    api('GET', '/system/config'),
  ])
  if (cR.success) myCourses.value = cR.data || []
  if (sR.success) sys.value = sR.data
})
</script>
