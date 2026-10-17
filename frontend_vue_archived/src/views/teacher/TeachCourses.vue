<template>
  <div>
    <div v-if="!courses.length" class="empty-state">
      <div class="empty-icon"><Icon name="book-open" /></div>
      <p>暂无教授课程</p>
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
            <td>{{ (c.stat?.total || 0) }} 人（已录取 {{ c.stat?.approved || 0 }}）</td>
            <td>
              <span v-if="c.oversubscribed" class="status-tag status-pending">超容量待审批</span>
              <span v-else class="status-tag status-approved">正常</span>
            </td>
            <td>
              <button class="btn btn-sm" :class="c.oversubscribed ? 'btn-primary' : 'btn-ghost'" @click="openApprove(c.id)">{{ c.oversubscribed ? '审批选课' : '查看报名' }}</button>
              <button class="btn btn-outline btn-sm" @click="go('student-list')">查看学生</button>
              <button class="btn btn-outline btn-sm" @click="go('grade-mgr')">成绩管理</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/Icon.vue'
import { api, getUser } from '@/api/client'

const router = useRouter()
const user = getUser()
const courses = ref([])

async function load() {
  const r = await api('GET', `/courses/teacher/${user.id}`)
  courses.value = r.success ? r.data : []
}
function openApprove(id) { router.push('/approve?course=' + id) }
function go(p) { router.push('/' + p) }
onMounted(load)
</script>
