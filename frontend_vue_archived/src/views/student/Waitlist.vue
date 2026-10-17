<template>
  <div class="page">
    <div class="page-head"><h1>候补队列</h1><p>满员课程可加入候补，有空位时按序递补。</p></div>
    <div class="card">
      <div v-if="loading" class="empty">加载中…</div>
      <div v-else-if="!list.length" class="empty">你不在任何课程的候补队列中。</div>
      <table class="table" v-else>
        <thead><tr><th>课程</th><th>教师</th><th>我的排位</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="w in list" :key="w.waitlistId || w.courseId">
            <td>{{ w.courseName || w.name }}</td>
            <td>{{ w.teacherName || w.teacher }}</td>
            <td>{{ w.position ? '第 ' + w.position + ' 位' : '—' }}</td>
            <td><span class="badge" :class="isDone(w) ? 'badge-ok' : 'badge-warn'">{{ statusLabel(w) }}</span></td>
            <td>
              <button v-if="isConfirming(w)" class="btn btn-primary btn-sm" @click="confirm(w)">确认录取</button>
              <button v-if="!isDone(w)" class="btn btn-outline btn-sm" @click="cancel(w)">退出</button>
            </td>
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

const list = ref([])
const loading = ref(false)
async function load() {
  loading.value = true
  const r = await api('GET', '/waitlist/mine')
  loading.value = false
  if (r.success) list.value = r.data || []
  else toast(r.message || '加载失败', 'error')
}
function isConfirming(w) { return w.status === 'confirming' }
function isDone(w) { return w.status === 'confirmed' || w.status === 'approved' }
function statusLabel(w) {
  return { confirming: '待确认录取', confirmed: '已录取', waiting: '候补中', timeout: '已超时' }[w.status] || '候补中'
}
async function confirm(w) {
  const r = await api('POST', '/waitlist/confirm/' + (w.waitlistId || w.id))
  toast(r.message || (r.success ? '已确认' : '失败'), r.success ? 'success' : 'error')
  if (r.success) load()
}
async function cancel(w) {
  if (!confirm('确定退出该候补？')) return
  const r = await api('POST', '/waitlist/cancel', { courseId: w.courseId || w.id })
  toast(r.message || (r.success ? '已退出' : '失败'), r.success ? 'success' : 'error')
  if (r.success) load()
}
onMounted(load)
</script>
