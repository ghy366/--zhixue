<template>
  <div class="page-wrap stats-page liquid">
    <div class="stats-grid">
      <div class="stat-card"><div class="stat-icon purple" v-html="svgIcon('cart')" /><div><div class="stat-value">{{ list.length }}</div><div class="stat-label">意向课程</div></div></div>
      <div class="stat-card"><div class="stat-icon blue" v-html="svgIcon('coins')" /><div><div class="stat-value">{{ totalPoints }}</div><div class="stat-label">预计投入积分</div></div></div>
      <div class="stat-card"><div class="stat-icon amber" v-html="svgIcon('coins')" /><div><div class="stat-value">{{ remaining }}</div><div class="stat-label">本轮剩余积分</div></div></div>
    </div>

    <div class="section">
      <div class="section-header">
        <div class="section-title" v-html="svgIcon('cart') + ' 意向单（' + list.length + ' 门）'" />
        <div style="display:flex;gap:8px">
          <button class="btn btn-ghost btn-sm" :disabled="!list.length" @click="clearAll">
            <span class="icon" v-html="svgIcon('trash')" /> 清空
          </button>
          <button class="btn btn-primary btn-sm" :disabled="!list.length" @click="submit">
            <span class="icon" v-html="svgIcon('check-circle')" /> 一键转正式选课
          </button>
        </div>
      </div>
      <div v-if="list.length" class="data-table">
        <table>
          <thead><tr><th>课程</th><th>类别</th><th>教师</th><th>学分</th><th>时间/地点</th><th>投入积分</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="it in list" :key="it.intentId">
              <td><b>{{ it.courseName }}</b></td>
              <td><span class="course-cat">{{ it.category }}</span></td>
              <td>{{ it.teacherName }}</td>
              <td>{{ it.credit }}</td>
              <td>{{ it.time || '—' }} @ {{ it.location || '—' }}</td>
              <td>
                <el-input-number :model-value="it.desiredPoints ?? 0" :min="0" :max="pointPool" size="small" style="width:96px" @change="(v:any) => updatePoints(it, v)" />
              </td>
              <td><button class="btn btn-danger btn-sm" @click="remove(it)">移除</button></td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="empty-state">
        <div class="empty-icon" v-html="svgIcon('cart')" />
        <div class="empty-title">还没有添加任何意向课程</div>
        <router-link to="/courses" class="btn btn-primary btn-sm" style="margin-top:12px">去课程浏览</router-link>
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
const remaining = ref(0)
// 当前轮次与本轮积分池（来自 /enrollments/points）
const currentRound = ref(1)
const pointPool = ref(50)
const totalPoints = computed(() => list.value.reduce((s, it) => s + (it.desiredPoints || 0), 0))
async function load() {
  try {
    const [r, p] = await Promise.all([
      http.get('/intents/mine'),
      http.get('/enrollments/points').catch(() => null)
    ])
    list.value = r?.data || []
    remaining.value = p?.data?.remaining ?? 0
    currentRound.value = p?.data?.round ?? 1
    pointPool.value = p?.data?.total ?? 50
  } catch {}
}
async function updatePoints(it: any, v: number) {
  if (v === null || v === undefined) return
  const old = it.desiredPoints
  it.desiredPoints = v
  try {
    // 后端 /intents/add 是 upsert：同课程再次提交即覆盖积分
    await http.post('/intents/add', { courseId: it.courseId, points: v })
    ElMessage.success({ message: `「${it.courseName}」已设为 ${v} 分`, duration: 1500 })
  } catch (e: any) {
    it.desiredPoints = old
    ElMessage.error(e?.message || '保存失败')
  }
}
async function remove(it: any) {
  try {
    // 后端是 POST /intents/remove {courseId}
    await http.post('/intents/remove', { courseId: it.courseId })
    ElMessage.success('已移除'); load()
  } catch (e: any) { ElMessage.error(e?.message || '移除失败') }
}
async function clearAll() {
  try { await ElMessageBox.confirm('确定清空全部意向？', '提示', { type: 'warning' }) } catch { return }
  try {
    // 后端是 POST /intents/clear（无 body）
    await http.post('/intents/clear', {})
    ElMessage.success('已清空'); load()
  } catch (e: any) { ElMessage.error(e?.message || '清空失败') }
}
async function submit() {
  try { await ElMessageBox.confirm('一键转为正式选课？', '提示', { type: 'warning' }) } catch { return }
  try { await http.post('/intents/submit', {}); ElMessage.success('意向单已提交'); load() }
  catch (e: any) { ElMessage.error(e?.message || '提交失败') }
}
onMounted(load)
</script>

<style scoped>
.page-wrap { padding: 4px 0; }
.empty-state { text-align: center; padding: 60px 20px; color: var(--text-light); }
.empty-icon { font-size: 48px; opacity: 0.3; margin-bottom: 10px; }
.course-cat { font-size: 11px; padding: 2px 8px; border-radius: 999px; background: rgba(59,130,246,0.15); color: #60a5fa; }
</style>
