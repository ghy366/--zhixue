<template>
  <div class="page">
    <div class="page-head"><h1>管理后台</h1><p>全局选课运行监控与系统配置。</p></div>
    <div class="stats-grid">
      <div class="stat-card"><div class="stat-value">{{ users.length }}</div><div class="stat-label">系统用户</div></div>
      <div class="stat-card"><div class="stat-value">{{ courses.length }}</div><div class="stat-label">课程总数</div></div>
      <div class="stat-card"><div class="stat-value">{{ sys?.currentRound ?? '-' }}</div><div class="stat-label">当前轮次</div></div>
      <div class="stat-card"><div class="stat-value">{{ sys?.roundOpen ? '进行中' : '已关闭' }}</div><div class="stat-label">选课状态</div></div>
    </div>
    <div class="card">
      <div class="card-header"><h3>管理模块</h3></div>
      <div class="quick-grid">
        <button class="quick-tile" v-for="m in modules" :key="m.page" @click="go(m.page)"><Icon :name="m.icon" /><span>{{ m.label }}</span></button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/Icon.vue'
import { api } from '@/api/client'

const router = useRouter()
const users = ref([])
const courses = ref([])
const sys = ref(null)
const modules = [
  { page: 'rounds', icon: 'refresh', label: '选课轮次' },
  { page: 'course-mgr', icon: 'book', label: '课程管理' },
  { page: 'user-mgr', icon: 'users', label: '用户管理' },
  { page: 'announce', icon: 'megaphone', label: '公告管理' },
  { page: 'stats', icon: 'bar-chart', label: '统计报表' },
  { page: 'logs', icon: 'file-text', label: '系统日志' },
]
function go(p) { router.push('/' + p) }

onMounted(async () => {
  const [uR, cR, sR] = await Promise.all([
    api('GET', '/admin/users'),
    api('GET', '/courses?category=全部'),
    api('GET', '/system/config'),
  ])
  if (uR.success) users.value = uR.data || []
  if (cR.success) courses.value = cR.data || []
  if (sR.success) sys.value = sR.data
})
</script>
