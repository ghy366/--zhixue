<template>
  <div>
    <div class="stats-grid">
      <div class="stat-card"><div class="stat-icon amber"><Icon name="star" /></div><div><div class="stat-value">{{ d.totalAvg || 0 }}</div><div class="stat-label">平均评分</div></div></div>
      <div class="stat-card"><div class="stat-icon blue"><Icon name="message-square" /></div><div><div class="stat-value">{{ d.totalCount || 0 }}</div><div class="stat-label">收到评价</div></div></div>
      <div class="stat-card"><div class="stat-icon purple"><Icon name="book-open" /></div><div><div class="stat-value">{{ courses.length }}</div><div class="stat-label">我的课程</div></div></div>
    </div>

    <div class="section">
      <div class="section-header"><div class="section-title">各课程评分</div></div>
      <div v-if="!courses.length" class="empty-state"><div class="empty-icon"><Icon name="message-square" /></div><p>暂无教授课程</p></div>
      <div v-else class="data-table">
        <table>
          <thead><tr><th>课程</th><th>综合</th><th>教学</th><th>内容</th><th>作业</th><th>评价数</th><th>最新评价</th></tr></thead>
          <tbody>
            <tr v-for="c in courses" :key="c.courseId">
              <td><b>{{ c.courseName }}</b></td>
              <td v-if="c.count">{{ miniStars(c.avg) }} <b>{{ c.avg }}</b></td>
              <td v-else><span style="color:var(--text-light)">暂无</span></td>
              <td>{{ c.count ? c.teachingAvg : '—' }}</td>
              <td>{{ c.count ? c.contentAvg : '—' }}</td>
              <td>{{ c.count ? c.homeworkAvg : '—' }}</td>
              <td>{{ c.count }}</td>
              <td style="font-size:12px;color:var(--text-light)">{{ c.latest && c.latest.content ? (c.latest.content.length > 28 ? c.latest.content.slice(0, 28) + '…' : c.latest.content) : '—' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div style="margin-top:10px;font-size:12px;color:var(--text-light)">学生的评价默认以「姓氏 + 同学」形式展示；学生可勾选完全匿名（显示为「匿名同学」）。</div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import Icon from '@/components/Icon.vue'
import { api, getUser } from '@/api/client'

const user = getUser()
const d = ref({})
const courses = ref([])
function miniStars(v) {
  const n = Math.round(v || 0)
  return '★★★★★'.slice(0, n) + '☆☆☆☆☆'.slice(0, 5 - n)
}
async function load() {
  const r = await api('GET', `/reviews/teacher/${user.id}`)
  const data = (r && r.success) ? r.data : { courses: [], totalCount: 0, totalAvg: 0 }
  d.value = data
  courses.value = data.courses || []
}
onMounted(load)
</script>
