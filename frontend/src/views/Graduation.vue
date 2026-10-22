<template>
  <div class="page-wrap stats-page liquid">
    <div class="stats-grid" v-if="summary">
      <div class="stat-card">
        <div class="stat-icon purple" v-html="svgIcon('target')" />
        <div><div class="stat-value">{{ summary.total?.percent || 0 }}%</div><div class="stat-label">毕业进度完成度</div></div>
      </div>
      <div class="stat-card">
        <div class="stat-icon blue" v-html="svgIcon('star')" />
        <div><div class="stat-value">{{ summary.total?.earned || 0 }}<span class="sub"> / {{ summary.total?.required || 0 }}</span></div><div class="stat-label">已获学分</div></div>
      </div>
      <div class="stat-card">
        <div class="stat-icon amber" v-html="svgIcon('pin')" />
        <div><div class="stat-value">{{ summary.total?.gap || 0 }}</div><div class="stat-label">还差总学分</div></div>
      </div>
    </div>

    <div class="section" v-if="summary">
      <div class="section-header"><div class="section-title">各模块学分完成情况</div></div>
      <div class="stats-grid">
        <div v-for="c in summary.categories" :key="c.key" class="stat-card">
          <div class="stat-icon" :class="c.done ? 'green' : 'amber'" v-html="svgIcon(icons[c.key] || 'star')" />
          <div>
            <div class="stat-value">{{ c.earned }}<span class="sub"> / {{ c.required }}</span></div>
            <div class="stat-label">
              {{ c.key }}学分
              <span v-html="c.done ? svgIcon('check-circle') + ' 已完成' : '· 还差 ' + c.gap" />
            </div>
          </div>
        </div>
      </div>
      <div class="grad-progress">
        <div class="grad-progress-label">
          <span>毕业总进度</span>
          <span><b>{{ summary.total?.earned }}</b> / {{ summary.total?.required }} 学分（{{ summary.total?.percent }}%）</span>
        </div>
        <div class="progress-bar">
          <div class="progress-fill" :style="{ width: Math.min(100, summary.total?.percent || 0) + '%' }" />
        </div>
      </div>
    </div>

    <div class="section" v-if="summary?.recommend?.length">
      <div class="section-header">
        <div class="section-title" v-html="svgIcon('pin') + ' 推荐可修课程（补齐差额）'" />
      </div>
      <div class="course-grid">
        <div v-for="c in summary.recommend" :key="c.courseId" class="course-card">
          <span class="course-cat" :class="'cat-' + c.category">{{ c.category }}</span>
          <div class="course-name">{{ c.name }}</div>
          <div class="course-teacher">
            <span v-html="svgIcon('user')" /> {{ c.teacherName || c.teacher }} · {{ c.credit }} 学分
          </div>
          <div class="course-foot">
            <span class="stu-points">补齐 +{{ c.credit }} 学分</span>
            <button class="btn btn-primary btn-sm" :disabled="enrollingId === c.courseId" @click="openPoints(c)">
              {{ enrollingId === c.courseId ? '加入中…' : '选课' }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <IntentPointsDialog
      v-model="pointsVisible"
      :course="pendingCourse"
      :current-points="null"
      :remaining="remaining"
      :submitting="!!enrollingId"
      :round="currentRound"
      :max-points="pointPool"
      @submit="submitIntent"
    />

    <div class="empty-state" v-if="!summary && !loading">
      <div class="empty-icon" v-html="svgIcon('target')" />
      <div class="empty-title">暂无毕业进度数据</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api'
import IntentPointsDialog from '@/components/IntentPointsDialog.vue'
const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`
const icons: Record<string, string> = { '必修': 'book', '选修': 'book-open', '通识': 'layers', '实践': 'flask' }
const loading = ref(false)
const summary = ref<any>(null)
const enrollingId = ref<number | null>(null)
// 投入积分弹窗
const pointsVisible = ref(false)
const pendingCourse = ref<any>(null)
const remaining = ref(0)
// 当前轮次与本轮积分池（来自 /enrollments/points）
const currentRound = ref(1)
const pointPool = ref(50)
async function load() {
  loading.value = true
  try {
    const [r, p]: any[] = await Promise.all([
      http.get('/graduation/summary'),
      http.get('/enrollments/points').catch(() => null)
    ])
    summary.value = r?.data
    remaining.value = p?.data?.remaining ?? 0
    currentRound.value = p?.data?.round ?? 1
    pointPool.value = p?.data?.total ?? 50
  } catch {
    ElMessage.error('毕业进度加载失败（服务可能刚重启），请刷新页面重试')
  }
  loading.value = false
}
function openPoints(c: any) {
  pendingCourse.value = c
  pointsVisible.value = true
}
// 后端意向接口是 POST /api/intents/add，body: { courseId, points }，积分 0-69 自由投入
async function submitIntent(points: number) {
  const c = pendingCourse.value
  if (!c || enrollingId.value) return
  enrollingId.value = c.courseId
  try {
    await http.post('/intents/add', { courseId: c.courseId, points })
    ElMessage.success(`已加入意向：${c.name}（投入 ${points} 分）`)
    pointsVisible.value = false
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    enrollingId.value = null
  }
}
onMounted(load)
</script>

<style scoped>
.page-wrap { padding: 4px 0; }
.sub { font-size: 13px; color: var(--text-light); margin-left: 2px; }
.grad-progress { margin-top: 12px; padding: 16px; border: 1px solid var(--border); border-radius: 12px; background: var(--card-bg); }
.grad-progress-label { display: flex; justify-content: space-between; font-size: 14px; margin-bottom: 8px; }
.grad-progress-label b { color: var(--primary); }
.empty-state { text-align: center; padding: 60px 20px; color: var(--text-light); }
.empty-icon { font-size: 48px; opacity: 0.3; margin-bottom: 10px; }
.course-grid { display: grid; gap: 14px; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); }
.course-card { background: var(--card-bg); border: 1px solid var(--border); border-radius: 12px; padding: 14px 16px; display: flex; flex-direction: column; gap: 8px; }
.course-name { font-size: 14px; font-weight: 600; }
.course-teacher { font-size: 12px; color: var(--text-light); display: flex; align-items: center; gap: 4px; }
.course-foot { display: flex; justify-content: space-between; align-items: center; margin-top: 4px; }
.stu-points { font-size: 12px; font-weight: 700; color: #fff; background: linear-gradient(135deg, #8b5cf6, #6366f1); padding: 4px 12px; border-radius: 999px; box-shadow: 0 2px 8px rgba(139, 92, 246, 0.35); }
.course-cat { font-size: 11px; padding: 2px 8px; border-radius: 999px; background: rgba(59,130,246,0.15); color: #60a5fa; align-self: flex-start; }
</style>
