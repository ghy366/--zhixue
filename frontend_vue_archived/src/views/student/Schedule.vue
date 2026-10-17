<template>
  <div class="page">
    <div class="page-head"><h1>我的课表</h1><p>已录取课程的时间安排与冲突检测。</p></div>

    <div class="card">
      <div class="card-header">
        <h3>本周课表</h3>
        <button class="btn btn-primary btn-sm" :disabled="loading" @click="generate">智能生成课表</button>
      </div>
      <div v-if="conflicts.length" class="alert alert-warn">
        ⚠️ 检测到 {{ conflicts.length }} 处时间冲突，请调整选课。
      </div>
      <div v-if="loading" class="empty">加载中…</div>
      <div v-else-if="!approved.length" class="empty">暂无已录取课程。</div>
      <table class="table" v-else>
        <thead><tr><th>课程</th><th>教师</th><th>时间</th><th>教室</th></tr></thead>
        <tbody>
          <tr v-for="c in approved" :key="c.courseId || c.id">
            <td>{{ c.name }}</td>
            <td>{{ c.teacherName || c.teacher }}</td>
            <td>{{ c.time || c.courseTime || '—' }}</td>
            <td>{{ c.location || c.classroom || '—' }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { api } from '@/api/client'
import { toast } from '@/utils/dom'

const approved = ref([])
const conflicts = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  const [aR, cR] = await Promise.all([api('GET', '/enrollments/approved'), api('GET', '/schedule/conflicts')])
  loading.value = false
  if (aR.success) approved.value = aR.data || []
  const cf = cR.success ? cR.data : null
  conflicts.value = (cf && (cf.conflicts || Array.isArray(cf))) ? (cf.conflicts || cf) : []
}
async function generate() {
  const r = await api('POST', '/schedule/generate', { candidates: [], locks: [] })
  toast(r.message || (r.success ? '课表已生成' : '失败'), r.success ? 'success' : 'error')
  if (r.success) load()
}
onMounted(load)
</script>
