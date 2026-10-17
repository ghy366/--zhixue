<template>
  <div>
    <!-- 列表模式 -->
    <template v-if="!selected">
      <div class="section">
        <div class="section-header"><div class="section-title">待审批课程（{{ needApprove.length }}）</div></div>
        <div v-if="needApprove.length" class="data-table">
          <table>
            <thead><tr><th>课程</th><th>容量</th><th>报名人数</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="c in needApprove" :key="c.id">
                <td><b>{{ c.name }}</b></td><td>{{ c.capacity }}</td><td>{{ c.stat?.total || 0 }} 人</td>
                <td><button class="btn btn-primary btn-sm" @click="openApprove(c.id)">去审批</button></td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else class="empty-state"><div class="empty-icon"><Icon name="check-circle" /></div><p>暂无需要审批的课程</p></div>
      </div>

      <div class="section">
        <div class="section-header"><div class="section-title">我的全部课程</div></div>
        <div class="data-table">
          <table>
            <thead><tr><th>课程</th><th>容量</th><th>报名</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="c in courses" :key="c.id">
                <td><b>{{ c.name }}</b></td><td>{{ c.capacity }}</td><td>{{ c.stat?.total || 0 }} 人</td>
                <td><button class="btn btn-outline btn-sm" @click="openApprove(c.id)">查看报名</button></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </template>

    <!-- 审批详情模式 -->
    <div v-else class="section">
      <div class="section-header">
        <div class="section-title">审批「{{ selected.name }}」选课</div>
        <button class="btn btn-ghost btn-sm" @click="back">返回</button>
      </div>
      <div class="data-table">
        <div class="approve-toolbar">
          <div class="approve-count">已选 <b>{{ chosen.size }}</b> / {{ selected.capacity }} 人（报名共 {{ enrollments.length }} 人）</div>
          <div style="display:flex;gap:8px">
            <button class="btn btn-outline btn-sm" @click="autoByPoints">按积分自动录取前{{ selected.capacity }}人</button>
            <button class="btn btn-success btn-sm" @click="submit">提交审批结果</button>
          </div>
        </div>
        <label v-for="e in enrollments" :key="e.studentId" class="approve-student-row" :class="{ selected: chosen.has(e.studentId) }">
          <input type="checkbox" :checked="chosen.has(e.studentId)" @change="toggle(e.studentId)">
          <div class="stu-info">
            <div class="stu-name">{{ e.studentName }}</div>
            <div class="stu-meta">{{ e.studentNo }} · {{ e.studentMajor || '' }} · 状态：
              <span class="status-tag" :class="'status-' + (e.status === 'approved' ? 'approved' : e.status === 'pending' ? 'pending' : 'rejected')">
                {{ e.status === 'approved' ? '已录取' : e.status === 'pending' ? '待审批' : '未录取' }}
              </span>
            </div>
          </div>
          <span class="stu-points">{{ e.points }} 分</span>
        </label>
      </div>

      <div class="section" style="margin-top:20px">
        <div class="section-header"><div class="section-title">候补队列</div></div>
        <div v-if="waitlist.length" class="data-table">
          <table>
            <thead><tr><th>学号</th><th>姓名</th><th>专业</th><th>状态</th><th>位置</th></tr></thead>
            <tbody>
              <tr v-for="w in waitlist" :key="w.studentId">
                <td>{{ w.studentNo }}</td><td>{{ w.studentName }}</td><td>{{ w.studentMajor || '-' }}</td>
                <td>{{ w.status === 'confirming' ? '待确认' : '候补中' }}</td><td>第 {{ w.position }} 位</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else class="empty-state"><div class="empty-icon"><Icon name="hourglass" /></div><p>暂无候补学生</p></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '@/components/Icon.vue'
import { api, getUser } from '@/api/client'

const route = useRoute()
const router = useRouter()
const user = getUser()
const courses = ref([])
const selected = ref(null)
const enrollments = ref([])
const waitlist = ref([])
const chosen = ref(new Set())

const needApprove = computed(() => courses.value.filter(c => c.oversubscribed))

async function load() {
  const r = await api('GET', `/courses/teacher/${user.id}`)
  courses.value = r.success ? r.data : []
}
async function openApprove(id) {
  const [courseR, enrR, wlR] = await Promise.all([
    api('GET', '/courses?category=全部'),
    api('GET', `/enrollments/course/${id}`),
    api('GET', `/waitlist/course/${id}`),
  ])
  selected.value = courseR.data.find(c => c.id == id)
  enrollments.value = enrR.success ? enrR.data : []
  waitlist.value = wlR.success ? wlR.data : []
  chosen.value = new Set(enrollments.value.filter(e => e.status === 'approved').map(e => e.studentId))
}
function back() { selected.value = null; router.replace('/approve') }
function toggle(sid) {
  const s = new Set(chosen.value)
  s.has(sid) ? s.delete(sid) : s.add(sid)
  chosen.value = s
}
function autoByPoints() {
  const rows = [...enrollments.value].sort((a, b) => (b.points || 0) - (a.points || 0))
  const s = new Set(enrollments.value.filter(e => e.status === 'approved').map(e => e.studentId))
  let need = selected.value.capacity - s.size
  for (const e of rows) {
    if (need <= 0) break
    if (!s.has(e.studentId)) { s.add(e.studentId); need-- }
  }
  chosen.value = s
}
async function submit() {
  const ids = [...chosen.value]
  if (ids.length > selected.value.capacity) { alert(`录取人数不能超过容量 ${selected.value.capacity}`); return }
  if (!confirm(`确认录取 ${ids.length} 名学生？`)) return
  const r = await api('POST', '/enrollments/approve', { courseId: selected.value.id, studentIds: ids })
  alert(r.message)
  if (r.success) { selected.value = null; router.replace('/approve'); await load() }
}
onMounted(() => { load(); if (route.query.course) openApprove(route.query.course) })
watch(() => route.query.course, (c) => { if (c) openApprove(c); else selected.value = null })
</script>
