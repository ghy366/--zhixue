<template>
  <div class="page">
    <div class="page-head"><h1>成绩查询</h1><p>查看已出成绩课程的得分与绩点。</p></div>
    <div class="card">
      <div v-if="loading" class="empty">加载中…</div>
      <div v-else-if="!scores.length" class="empty">暂无成绩记录。</div>
      <table class="table" v-else>
        <thead><tr><th>课程</th><th>类别</th><th>学分</th><th>平时</th><th>期末</th><th>总评</th></tr></thead>
        <tbody>
          <tr v-for="s in scores" :key="s.courseId || s.id">
            <td>{{ s.courseName || s.name }}</td>
            <td>{{ s.category || '—' }}</td>
            <td>{{ s.credit }}</td>
            <td>{{ s.regularScore ?? '—' }}</td>
            <td>{{ s.finalScore ?? '—' }}</td>
            <td><b>{{ s.totalScore ?? '—' }}</b></td>
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
const scores = ref([])
const loading = ref(false)
onMounted(async () => {
  loading.value = true
  const r = await api('GET', '/enrollments/scores')
  loading.value = false
  if (r.success) scores.value = r.data || []
  else toast(r.message || '加载失败', 'error')
})
</script>
