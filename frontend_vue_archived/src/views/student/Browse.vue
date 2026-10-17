<template>
  <div class="page">
    <div class="page-head"><h1>课程浏览</h1><p>浏览全部课程，使用积分竞标选课。</p></div>

    <div class="card">
      <div class="filter-row">
        <div class="seg">
          <button v-for="cat in categories" :key="cat" class="seg-btn" :class="{ active: active === cat }" @click="active = cat; load()">
            {{ cat }}
          </button>
        </div>
        <input class="search-input" v-model="kw" placeholder="搜索课程名 / 教师" @input="load" />
      </div>

      <div v-if="loading" class="empty">加载中…</div>
      <div v-else-if="!list.length" class="empty">暂无课程</div>
      <div class="course-grid" v-else>
        <div class="course-card" v-for="c in filtered" :key="c.id">
          <div class="course-top">
            <div class="course-name">{{ c.name }}</div>
            <span class="badge" :class="c.full ? 'badge-warn' : 'badge-ok'">{{ c.full ? '已满' : '可报' }}</span>
          </div>
          <div class="course-meta">{{ c.category }} · {{ c.credit }} 学分</div>
          <div class="course-meta">{{ c.teacherName || c.teacher }} · {{ c.campus || '—' }}</div>
          <div class="course-meta">{{ c.time || c.courseTime || '时间待定' }}</div>
          <div class="course-actions">
            <button class="btn btn-primary btn-sm" :disabled="c.full" @click="enroll(c)">选课</button>
            <button class="btn btn-outline btn-sm" @click="joinWait(c)">候补</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { api } from '@/api/client'
import { toast } from '@/utils/dom'

const categories = ['全部', '公共必修', '专业必修', '专业选修', '通识选修']
const active = ref('全部')
const kw = ref('')
const list = ref([])
const loading = ref(false)

const filtered = computed(() => {
  const k = kw.value.trim()
  if (!k) return list.value
  return list.value.filter((c) => (c.name || '').includes(k) || (c.teacherName || c.teacher || '').includes(k))
})

async function load() {
  loading.value = true
  const cat = active.value === '全部' ? '全部' : active.value
  const r = await api('GET', '/courses?category=' + encodeURIComponent(cat))
  loading.value = false
  if (r.success) list.value = r.data || []
  else toast(r.message || '加载失败', 'error')
}

async function enroll(c) {
  const pts = prompt('为该课程投入多少选课积分？', '10')
  if (pts == null) return
  const r = await api('POST', '/enrollments/enroll', { courseId: c.id, points: parseInt(pts) || 0 })
  toast(r.message || (r.success ? '选课成功' : '失败'), r.success ? 'success' : 'error')
  if (r.success) load()
}
async function joinWait(c) {
  const r = await api('POST', '/waitlist/join', { courseId: c.id })
  toast(r.message || (r.success ? '已加入候补' : '失败'), r.success ? 'success' : 'error')
}

onMounted(load)
</script>
