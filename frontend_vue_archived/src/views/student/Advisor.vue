<template>
  <div class="page">
    <div class="page-head"><h1>毕业设计导师</h1><p>选择毕业设计导师并提交申请。</p></div>
    <div class="card">
      <div v-if="loading" class="empty">加载中…</div>
      <div v-else>
        <div v-if="mine" class="alert alert-ok">
          当前导师：<b>{{ mine.teacherName || mine.teacher }}</b>（{{ statusLabel }}）
          <button class="btn btn-outline btn-sm" style="margin-left:12px" @click="cancel">取消申请</button>
        </div>
        <div v-else>
          <div class="card-header"><h3>可选导师</h3></div>
          <div v-if="!teachers.length" class="empty">暂无可申请导师。</div>
          <div class="course-grid" v-else>
            <div class="course-card" v-for="t in teachers" :key="t.teacherId || t.id">
              <div class="course-name">{{ t.teacherName || t.name }}</div>
              <div class="course-meta">{{ t.college || t.major || '—' }}</div>
              <div class="course-meta">已带 {{ t.approved || 0 }}/{{ t.capacity || '—' }} 人</div>
              <div class="course-actions">
                <button class="btn btn-primary btn-sm" :disabled="!configOpen || t.available === false" @click="apply(t)">申请</button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { api } from '@/api/client'
import { useAuthStore } from '@/store/auth'
import { toast } from '@/utils/dom'

const auth = useAuthStore()
const teachers = ref([])
const mine = ref(null)
const configOpen = ref(false)
const loading = ref(false)

const statusLabel = computed(() => ({ approved: '已通过', pending: '审核中', rejected: '已驳回', cancelled: '已取消' }[mine.value?.status] || '审核中'))

async function load() {
  loading.value = true
  const [cR, mR, tR] = await Promise.all([
    api('GET', '/advisor/config'),
    api('GET', '/advisor/mine'),
    api('GET', '/advisor/teachers'),
  ])
  loading.value = false
  configOpen.value = !!(cR.data && cR.data.open)
  if (mR.success) mine.value = (mR.data || [])[0] || null
  if (tR.success) teachers.value = tR.data || []
}
async function apply(t) {
  const r = await api('POST', '/advisor/apply', { teacherId: t.teacherId || t.id })
  toast(r.message || (r.success ? '已申请' : '失败'), r.success ? 'success' : 'error')
  if (r.success) load()
}
async function cancel() {
  if (!confirm('确定取消当前导师申请？')) return
  const r = await api('POST', '/advisor/cancel/' + (mine.value?.id || ''))
  toast(r.message || (r.success ? '已取消' : '失败'), r.success ? 'success' : 'error')
  if (r.success) load()
}
onMounted(load)
</script>
