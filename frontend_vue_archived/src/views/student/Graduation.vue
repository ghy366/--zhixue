<template>
  <div class="page">
    <div class="page-head"><h1>毕业进度</h1><p>对照毕业学分要求，查看各模块完成情况。</p></div>
    <div class="card">
      <div v-if="loading" class="empty">加载中…</div>
      <div v-else>
        <div class="stats-grid">
          <div class="stat-card"><div class="stat-value">{{ summary?.total?.earned ?? '—' }}</div><div class="stat-label">已修总学分</div></div>
          <div class="stat-card"><div class="stat-value">{{ summary?.total?.required ?? '—' }}</div><div class="stat-label">毕业要求学分</div></div>
          <div class="stat-card"><div class="stat-value">{{ progress }}%</div><div class="stat-label">完成度</div></div>
        </div>
        <div v-if="cats.length" class="req-list">
          <div class="req-item" v-for="it in cats" :key="it.key">
            <div class="req-head"><span>{{ it.key }}<span v-if="it.done" class="tag" style="margin-left:8px">已达标</span></span><span>{{ it.earned }}/{{ it.required }}</span></div>
            <div class="req-bar"><div class="req-fill" :style="{ width: Math.min(100, it.percent || 0) + '%' }"></div></div>
          </div>
        </div>

        <template v-if="(summary?.recommend || []).length">
          <div class="card-header" style="margin-top:22px"><h3>推荐课程</h3></div>
          <div class="course-grid">
            <div class="course-card" v-for="c in summary.recommend" :key="c.courseId">
              <div class="course-name">{{ c.name }}</div>
              <div class="course-meta">{{ c.category }} · {{ c.credit }} 学分</div>
              <div class="course-meta" v-if="c.teacherName">{{ c.teacherName }}</div>
            </div>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { api } from '@/api/client'
import { toast } from '@/utils/dom'

const summary = ref(null)
const loading = ref(false)
const cats = computed(() => summary.value?.categories || [])
const progress = computed(() => {
  const p = summary.value?.total?.percent
  return p == null ? 0 : Math.min(100, Math.round(p))
})

onMounted(async () => {
  loading.value = true
  const r = await api('GET', '/graduation/summary')
  loading.value = false
  if (r.success) summary.value = r.data
  else toast(r.message || '加载失败', 'error')
})
</script>
