<template>
  <div class="page">
    <div class="page-head"><h1>我的意向</h1><p>把心仪课程加入意向单，集中提交参与积分竞标。</p></div>
    <div class="card">
      <div class="card-header">
        <h3>意向课程</h3>
        <div>
          <button class="btn btn-outline btn-sm" @click="clearAll">清空</button>
          <button class="btn btn-primary btn-sm" @click="submitAll">提交选课</button>
        </div>
      </div>
      <div v-if="loading" class="empty">加载中…</div>
      <div v-else-if="!intents.length" class="empty">意向单为空。在「课程浏览」中可加入意向。</div>
      <table class="table" v-else>
        <thead><tr><th>课程</th><th>投入积分</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="it in intents" :key="it.courseId">
            <td>{{ it.courseName || it.name }}</td>
            <td>{{ it.desiredPoints ?? it.points }}</td>
            <td>
              <button class="btn btn-primary btn-sm" @click="convert(it)">转选课</button>
              <button class="btn btn-danger btn-sm" @click="remove(it)">移除</button>
            </td>
          </tr>
        </tbody>
      </table>
      <div class="intent-add">
        <input v-model="addId" placeholder="课程 ID" class="search-input" style="max-width:140px" />
        <input v-model="addPts" placeholder="积分" class="search-input" style="max-width:90px" />
        <button class="btn btn-outline btn-sm" @click="add">加入意向</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { api } from '@/api/client'
import { toast } from '@/utils/dom'

const intents = ref([])
const loading = ref(false)
const addId = ref('')
const addPts = ref('10')

async function load() {
  loading.value = true
  const [iR, pR] = await Promise.all([api('GET', '/intents/mine'), api('GET', '/enrollments/points')])
  loading.value = false
  if (iR.success) intents.value = iR.data || []
  else toast(iR.message || '加载失败', 'error')
}
async function remove(it) {
  const r = await api('POST', '/intents/remove', { courseId: it.courseId })
  toast(r.message || (r.success ? '已移除' : '失败'), r.success ? 'success' : 'error')
  if (r.success) load()
}
async function convert(it) {
  if (!confirm('将《' + (it.courseName || it.name) + '》提交为正式选课（待审批）？')) return
  const r = await api('POST', '/intents/convert', { courseId: it.courseId })
  toast(r.message || (r.success ? '已提交为正式选课' : '失败'), r.success ? 'success' : 'error')
  if (r.success) load()
}
async function clearAll() {
  if (!confirm('确定清空意向单？')) return
  const r = await api('POST', '/intents/clear', {})
  toast(r.message || (r.success ? '已清空' : '失败'), r.success ? 'success' : 'error')
  if (r.success) load()
}
async function submitAll() {
  const r = await api('POST', '/intents/submit', {})
  toast(r.message || (r.success ? '已提交' : '失败'), r.success ? 'success' : 'error')
  if (r.success) load()
}
async function add() {
  const id = parseInt(addId.value)
  if (!id) return toast('请输入课程 ID', 'error')
  const r = await api('POST', '/intents/add', { courseId: id, points: parseInt(addPts.value) || 10 })
  toast(r.message || (r.success ? '已加入' : '失败'), r.success ? 'success' : 'error')
  if (r.success) { addId.value = ''; load() }
}
onMounted(load)
</script>
