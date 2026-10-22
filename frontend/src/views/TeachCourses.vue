<template>
  <div class="page-wrap stats-page liquid">
    <div v-if="!courses.length && !loading" class="empty-state">
      <div class="empty-icon" v-html="svgIcon('book-open')" />
      <div class="empty-title">暂无教授课程</div>
    </div>
    <div v-else class="data-table">
      <table>
        <thead><tr><th>课程</th><th>类别</th><th>学分</th><th>容量</th><th>报名</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="c in courses" :key="c.id">
            <td><b>{{ c.name }}</b></td>
            <td><span class="course-cat" :class="'cat-' + c.category">{{ c.category }}</span></td>
            <td>{{ c.credit }}</td>
            <td>{{ c.capacity }}</td>
            <td>{{ c.stat?.total || 0 }} 人（已录取 {{ c.stat?.approved || 0 }}）</td>
            <td>
              <span class="status-tag" :class="c.oversubscribed ? 'status-pending' : 'status-approved'">
                {{ c.oversubscribed ? '超容量待审批' : '正常' }}
              </span>
            </td>
            <td>
              <button v-if="c.oversubscribed" class="btn btn-primary btn-sm" @click="$router.push('/approve')">审批选课</button>
              <button class="btn btn-outline btn-sm" @click="$router.push('/student-list')">查看学生</button>
              <button class="btn btn-outline btn-sm" @click="$router.push('/grade-mgr')">成绩管理</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import http from '@/api'
import { useAuthStore } from '@/stores/auth'
const auth = useAuthStore()
const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`
const loading = ref(false)
const courses = ref<any[]>([])
async function load() {
  loading.value = true
  try { const r: any = await http.get(`/courses/teacher/${auth.user?.id}`); courses.value = r?.data || [] } catch {}
  loading.value = false
}
onMounted(load)
</script>

<style scoped>
.page-wrap { padding: 4px 0; }
.empty-state { text-align: center; padding: 60px 20px; color: var(--text-light); }
.empty-icon { font-size: 48px; opacity: 0.3; margin-bottom: 10px; }
</style>
