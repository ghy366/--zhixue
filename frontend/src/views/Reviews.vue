<template>
  <div class="page-wrap stats-page liquid">
    <!-- 教师视角：我教课程的评价汇总 -->
    <template v-if="isTeacher">
      <div class="stats-grid">
        <div class="stat-card"><div class="stat-icon amber" v-html="svgIcon('star')" /><div><div class="stat-value">{{ data.totalAvg?.toFixed(1) || '—' }}</div><div class="stat-label">课程平均分</div></div></div>
        <div class="stat-card"><div class="stat-icon blue" v-html="svgIcon('message-square')" /><div><div class="stat-value">{{ data.totalCount || 0 }}</div><div class="stat-label">总评价数</div></div></div>
        <div class="stat-card"><div class="stat-icon green" v-html="svgIcon('book')" /><div><div class="stat-value">{{ (data.courses || []).length }}</div><div class="stat-label">有评价课程</div></div></div>
      </div>

      <div class="section">
        <div class="section-header">
          <div class="section-title">课程评价汇总</div>
        </div>
        <div v-if="(data.courses || []).length" class="data-table">
          <table>
            <thead>
              <tr><th>课程</th><th>评价数</th><th>综合分</th><th>教学</th><th>内容</th><th>作业</th><th>最新评价</th></tr>
            </thead>
            <tbody>
              <tr v-for="c in data.courses" :key="c.courseId">
                <td><b>{{ c.courseName }}</b></td>
                <td>{{ c.count }}</td>
                <td><span class="rating" :class="ratingCls(c.avg)">{{ fmt(c.avg) }}</span></td>
                <td>{{ fmt(c.teachingAvg) }}</td>
                <td>{{ fmt(c.contentAvg) }}</td>
                <td>{{ fmt(c.homeworkAvg) }}</td>
                <td class="review-preview" v-if="c.latest">{{ formatLatest(c.latest) }}</td>
                <td v-else class="review-preview">—</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else class="empty-state">
          <div class="empty-icon" v-html="svgIcon('star')" />
          <div class="empty-title">暂无课程评价</div>
        </div>
      </div>
    </template>

    <!-- 学生视角：待评价 + 我的评价 -->
    <template v-else>
      <div class="stats-grid">
        <div class="stat-card"><div class="stat-icon amber" v-html="svgIcon('edit')" /><div><div class="stat-value">{{ pending.length }}</div><div class="stat-label">待评价课程</div></div></div>
        <div class="stat-card"><div class="stat-icon green" v-html="svgIcon('check-circle')" /><div><div class="stat-value">{{ mine.length }}</div><div class="stat-label">我已评价</div></div></div>
        <div class="stat-card"><div class="stat-icon blue" v-html="svgIcon('star')" /><div><div class="stat-value">{{ myAvg }}</div><div class="stat-label">我的平均评分</div></div></div>
      </div>

      <div class="section">
        <div class="section-header"><div class="section-title">待评价课程</div></div>
        <div v-if="pending.length" class="data-table">
          <table>
            <thead><tr><th>课程</th><th>教师</th><th>类型</th><th>学分</th><th style="width:120px">操作</th></tr></thead>
            <tbody>
              <tr v-for="c in pending" :key="c.courseId">
                <td><b>{{ c.courseName }}</b></td>
                <td>{{ c.teacherName }}</td>
                <td>{{ c.category }}</td>
                <td>{{ c.credit }}</td>
                <td><el-button type="primary" size="small" @click="openForm(c)">去评价</el-button></td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else class="empty-state">
          <div class="empty-icon" v-html="svgIcon('check-circle')" />
          <div class="empty-title">暂无待评价课程</div>
          <div class="empty-sub">修读结束后即可对课程进行评价</div>
        </div>
      </div>

      <div class="section" v-if="mine.length">
        <div class="section-header"><div class="section-title">我的评价</div></div>
        <div class="data-table">
          <table>
            <thead><tr><th>课程</th><th>教师</th><th>综合</th><th>教学</th><th>内容</th><th>作业</th><th>评语</th><th style="width:120px">操作</th></tr></thead>
            <tbody>
              <tr v-for="m in mine" :key="m.reviewId">
                <td><b>{{ m.courseName }}</b></td>
                <td>{{ m.teacherName }}</td>
                <td><span class="rating" :class="ratingCls(m.rating)">{{ m.rating }}</span></td>
                <td>{{ m.teaching }}</td>
                <td>{{ m.contentScore }}</td>
                <td>{{ m.homework }}</td>
                <td class="review-preview">{{ m.content || '—' }} <span v-if="m.anonymous" class="review-anon">匿名</span></td>
                <td><el-button size="small" @click="openEdit(m)">修改</el-button></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </template>

    <!-- 评价弹窗（学生） -->
    <el-dialog v-model="dialogVisible" :title="editTarget ? '修改评价' : '课程评价'" width="480px" append-to-body align-center>
      <div class="review-form" v-if="formCourse">
        <div class="rf-course">
          <b>{{ formCourse.courseName }}</b>
          <span class="muted">{{ formCourse.teacherName }}</span>
        </div>
        <div class="rf-row"><span class="rf-label">综合评分</span><el-rate v-model="form.rating" /></div>
        <div class="rf-row"><span class="rf-label">教学质量</span><el-rate v-model="form.teaching" /></div>
        <div class="rf-row"><span class="rf-label">课程内容</span><el-rate v-model="form.contentScore" /></div>
        <div class="rf-row"><span class="rf-label">作业负担</span><el-rate v-model="form.homework" /></div>
        <el-input v-model="form.content" type="textarea" :rows="3" maxlength="200" show-word-limit placeholder="写点上课感受吧（选填）" />
        <el-checkbox v-model="form.anonymous">匿名评价</el-checkbox>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">提交评价</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`
const isTeacher = computed(() => auth.user?.role === 'teacher')

const data = ref<any>({ courses: [], totalCount: 0, totalAvg: 0 })
const pending = ref<any[]>([])
const mine = ref<any[]>([])

const dialogVisible = ref(false)
const submitting = ref(false)
const formCourse = ref<any>(null)
const editTarget = ref<any>(null)
const form = ref({ rating: 5, teaching: 5, contentScore: 5, homework: 3, anonymous: false, content: '' })

const myAvg = computed(() => {
  if (!mine.value.length) return '—'
  return (mine.value.reduce((s, m) => s + (m.rating || 0), 0) / mine.value.length).toFixed(1)
})

function fmt(v: any) { return v != null && v !== undefined ? Number(v).toFixed(1) : '—' }
function ratingCls(v: any) {
  const n = Number(v) || 0
  if (n >= 4.5) return 'c-green'
  if (n >= 3.5) return 'c-blue'
  if (n > 0) return 'c-amber'
  return ''
}
function formatLatest(l: any) {
  if (typeof l === 'string') return l.slice(0, 30)
  if (l?.content) return l.content.slice(0, 30)
  return '—'
}

function openForm(c: any) {
  formCourse.value = c
  editTarget.value = null
  form.value = { rating: 5, teaching: 5, contentScore: 5, homework: 3, anonymous: false, content: '' }
  dialogVisible.value = true
}

function openEdit(m: any) {
  formCourse.value = { courseId: m.courseId, courseName: m.courseName, teacherName: m.teacherName }
  editTarget.value = m
  form.value = { rating: m.rating || 5, teaching: m.teaching || 5, contentScore: m.contentScore || 5, homework: m.homework || 3, anonymous: !!m.anonymous, content: m.content || '' }
  dialogVisible.value = true
}

async function submit() {
  if (!formCourse.value) return
  if (!form.value.rating) { ElMessage.warning('请先选择综合评分'); return }
  submitting.value = true
  try {
    const r: any = await http.post('/reviews', {
      courseId: formCourse.value.courseId,
      rating: form.value.rating,
      teaching: form.value.teaching,
      contentScore: form.value.contentScore,
      homework: form.value.homework,
      anonymous: form.value.anonymous,
      content: form.value.content || null,
    })
    if (r?.success === false || r?.data?.success === false) {
      ElMessage.error(r?.message || r?.data?.msg || '提交失败')
    } else {
      ElMessage.success(editTarget.value ? '评价已更新' : '评价已提交，感谢反馈')
      dialogVisible.value = false
      await load()
    }
  } catch {
    ElMessage.error('提交失败，请稍后重试')
  }
  submitting.value = false
}

async function load() {
  if (isTeacher.value) {
    try {
      const r: any = await http.get(`/reviews/teacher/${auth.user?.id}`)
      data.value = r?.data || { courses: [], totalCount: 0, totalAvg: 0 }
    } catch {}
    return
  }
  try {
    const [p, m]: any[] = await Promise.all([http.get('/reviews/pending'), http.get('/reviews/mine')])
    pending.value = p?.data || []
    mine.value = m?.data || []
  } catch {}
}

onMounted(load)
</script>

<style scoped>
.page-wrap { padding: 4px 0; }
.empty-state { text-align: center; padding: 60px 20px; color: var(--text-light); }
.empty-icon { font-size: 48px; opacity: 0.3; margin-bottom: 10px; }
.empty-sub { font-size: 12px; color: var(--text-lighter); margin-top: 6px; }
.rating { font-weight: 700; }
.c-green { color: #10b981; }
.c-blue { color: #3b82f6; }
.c-amber { color: #f59e0b; }
.review-preview { max-width: 180px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 12px; color: var(--text-light); }
.review-anon { font-size: 11px; color: var(--text-lighter); border: 1px solid var(--border); border-radius: 10px; padding: 1px 7px; margin-left: 6px; }

.review-form { display: flex; flex-direction: column; gap: 14px; }
.rf-course { display: flex; align-items: baseline; gap: 10px; }
.rf-course .muted { color: var(--text-light); font-size: 13px; }
.rf-row { display: flex; align-items: center; gap: 12px; }
.rf-label { width: 70px; font-size: 14px; color: var(--text); flex-shrink: 0; }
</style>
