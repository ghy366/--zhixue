<template>
  <div class="page-wrap stats-page liquid">
    <div class="stats-grid">
      <div class="stat-card"><div class="stat-icon amber" v-html="svgIcon('hourglass')" /><div><div class="stat-value">{{ waiting.length }}</div><div class="stat-label">候补中</div></div></div>
      <div class="stat-card"><div class="stat-icon green" v-html="svgIcon('party')" /><div><div class="stat-value">{{ confirming.length }}</div><div class="stat-label">待确认录取</div></div></div>
      <div class="stat-card"><div class="stat-icon blue" v-html="svgIcon('clipboard')" /><div><div class="stat-value">{{ list.length }}</div><div class="stat-label">候补课程</div></div></div>
    </div>

    <div class="section">
      <div class="section-header">
        <div class="section-title" v-html="svgIcon('hourglass') + ' 我的候补队列'" />
        <router-link class="section-link" to="/courses">去选课 →</router-link>
      </div>
      <div v-if="list.length" class="course-grid">
        <div v-for="w in list" :key="w.waitlistId" class="wl-card" :class="{ 'wl-confirm-card': w.status === 'confirming' }">
          <div class="wl-card-top">
            <div class="wl-course">{{ w.courseName }}</div>
            <span class="status-tag" :class="w.status === 'confirming' ? 'status-pending' : 'status-approved'">
              {{ w.status === 'confirming' ? '待确认录取' : '候补中' }}
            </span>
          </div>
          <div class="wl-meta" v-html="svgIcon('user') + ' ' + w.teacherName + ' · ' + w.credit + ' 学分'" />
          <div class="wl-pos">第 <b>{{ w.position }}</b> / {{ w.queueTotal }} 位</div>
          <div v-if="w.status === 'confirming'" class="wl-meta">请在 <span class="wl-countdown" :data-deadline="w.deadline">{{ fmtCountdown(w.deadline) }}</span> 前确认</div>
          <div v-else class="wl-meta" v-html="svgIcon('clock') + ' 预计递补：' + (w.estimatedWait || '待定')" />
          <div class="wl-acts">
            <button v-if="w.status === 'confirming'" class="btn btn-success btn-sm" @click="confirm(w)">确认录取</button>
            <button v-if="w.status === 'confirming'" class="btn btn-ghost btn-sm" @click="cancel(w)">放弃名额</button>
            <button v-else class="btn btn-danger btn-sm" @click="cancel(w)">退出候补</button>
          </div>
        </div>
      </div>
      <div v-else class="empty-state">
        <div class="empty-icon" v-html="svgIcon('hourglass')" />
        <div class="empty-title">暂无候补课程</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api'
const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`
const list = ref<any[]>([])
const waiting = computed(() => list.value.filter(w => w.status === 'waiting'))
const confirming = computed(() => list.value.filter(w => w.status === 'confirming'))
function fmtCountdown(deadline: any) {
  if (!deadline) return '--:--'
  const diff = new Date(deadline).getTime() - Date.now()
  if (diff <= 0) return '已过期'
  const h = Math.floor(diff / 3600000), m = Math.floor((diff % 3600000) / 60000)
  return `${h}h ${m}m`
}
async function load() { try { const r: any = await http.get('/waitlist/mine'); list.value = r?.data || [] } catch {} }
async function confirm(w: any) {
  try { await http.post(`/waitlist/confirm/${w.waitlistId}`); ElMessage.success('确认成功'); load() }
  catch (e: any) { ElMessage.error(e?.message || '确认失败') }
}
async function cancel(w: any) {
  try { await ElMessageBox.confirm('确定取消？', '提示', { type: 'warning' }) } catch { return }
  try { await http.post('/waitlist/cancel', { courseId: w.courseId }); ElMessage.success('已取消'); load() }
  catch (e: any) { ElMessage.error(e?.message || '操作失败') }
}
onMounted(load)
</script>

<style scoped>
.page-wrap { padding: 4px 0; }
.wl-card { background: var(--card-bg); border: 1px solid var(--border); border-radius: 12px; padding: 14px 16px; }
.wl-confirm-card { border-color: rgba(245,158,11,0.5); box-shadow: 0 0 20px rgba(245,158,11,0.15); }
.wl-card-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.wl-course { font-size: 14px; font-weight: 600; }
.wl-meta { font-size: 12px; color: var(--text-light); margin-top: 4px; display: flex; gap: 6px; align-items: center; }
.wl-pos { font-size: 13px; color: var(--text-light); margin-top: 6px; }
.wl-pos b { color: var(--primary); }
.wl-acts { display: flex; gap: 8px; margin-top: 10px; }
.empty-state { text-align: center; padding: 60px 20px; color: var(--text-light); }
.empty-icon { font-size: 48px; opacity: 0.3; margin-bottom: 10px; }
</style>
