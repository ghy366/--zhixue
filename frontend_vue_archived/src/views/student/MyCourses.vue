<template>
  <div class="page">
    <div class="page-head"><h1>我的课程</h1><p>查看已选课程，可退课或评价课程。</p></div>

    <div class="card">
      <div v-if="loading" class="empty">加载中…</div>
      <div v-else-if="!mine.length" class="empty">你还没有选任何课程。</div>
      <table class="table" v-else>
        <thead><tr><th>课程</th><th>类别</th><th>学分</th><th>教师</th><th>时间</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="c in mine" :key="c.courseId || c.id">
            <td>{{ c.courseName || c.name }}</td>
            <td>{{ c.category }}</td>
            <td>{{ c.credit }}</td>
            <td>{{ c.teacherName || c.teacher }}</td>
            <td>{{ c.time || c.courseTime || '—' }}</td>
            <td><span class="badge" :class="isOk(c) ? 'badge-ok' : 'badge-warn'">{{ isOk(c) ? '已录取' : '待录取' }}</span></td>
            <td>
              <button class="btn btn-outline btn-sm" @click="openReview(c)">评价</button>
              <button class="btn btn-danger btn-sm" @click="drop(c)">退课</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 课程评价弹窗 -->
    <div class="modal-mask" v-if="rv.open" @click.self="rv.open = false">
      <div class="modal" style="max-width:560px;text-align:left">
        <div class="modal-header">
          <h3>{{ rv.courseName }} · 课程评价</h3>
          <button class="modal-close" @click="rv.open = false">✕</button>
        </div>
        <div class="modal-body" style="max-height:64vh;overflow:auto">
          <div v-if="rv.loading" class="empty">加载中…</div>
          <template v-else>
            <div class="review-avg">
              平均 <b>{{ rv.data.avg || 0 }}</b> 分（教学 {{ rv.data.teachingAvg || 0 }} · 内容 {{ rv.data.contentAvg || 0 }} · 作业 {{ rv.data.homeworkAvg || 0 }}）· 共 {{ rv.data.count || 0 }} 条
            </div>

            <div class="dim-row" v-for="d in dims" :key="d.key">
              <div class="dim-label">{{ d.label }}</div>
              <div class="star-row">
                <span
                  v-for="i in 5"
                  :key="i"
                  class="star-btn"
                  :class="{ on: (rv.form[d.key] || 0) >= i }"
                  @click="rv.form[d.key] = i"
                >★</span>
              </div>
            </div>
            <label class="anon-label"><input type="checkbox" v-model="rv.form.anonymous" /> 匿名评价（其他同学看不到你的姓名）</label>
            <textarea v-model="rv.form.content" placeholder="说说这门课的上课体验（选填）" style="width:100%;min-height:80px;margin-top:10px"></textarea>

            <div style="margin-top:12px">
              <button class="btn btn-primary" @click="submitReview">提交评价</button>
            </div>

            <div class="review-list">
              <div v-if="!(rv.data.list || []).length" style="color:var(--text-light);font-size:13px;padding:10px 0">还没有人评价这门课</div>
              <div v-for="(x, i) in rv.data.list" :key="i" class="review-item">
                <div class="review-head">
                  <span class="review-who">{{ x.studentName }}</span>
                  <span class="review-anon" v-if="x.anonymous">匿名</span>
                  <span class="review-stars">{{ '★'.repeat(x.rating || 0) }}</span>
                </div>
                <div class="review-text" v-if="x.content">{{ x.content }}</div>
              </div>
            </div>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { api } from '@/api/client'
import { toast } from '@/utils/dom'

const mine = ref([])
const loading = ref(false)

const rv = reactive({
  open: false, loading: false, courseId: null, courseName: '',
  data: { list: [], avg: 0, count: 0 },
  form: { rating: 0, teaching: 0, contentScore: 0, homework: 0, content: '', anonymous: false },
})
const dims = [
  { key: 'rating', label: '总体评分' },
  { key: 'teaching', label: '教学质量' },
  { key: 'contentScore', label: '课程内容' },
  { key: 'homework', label: '作业适量' },
]

function isOk(c) { return c.approved || c.status === 'approved' }

async function load() {
  loading.value = true
  const r = await api('GET', '/enrollments/mine')
  loading.value = false
  if (r.success) mine.value = r.data || []
  else toast(r.message || '加载失败', 'error')
}
async function drop(c) {
  if (!confirm('确定退选《' + c.name + '》？退课后积分将退回。')) return
  const r = await api('POST', '/enrollments/drop', { courseId: c.courseId || c.id })
  toast(r.message || (r.success ? '已退课' : '失败'), r.success ? 'success' : 'error')
  if (r.success) load()
}

async function openReview(c) {
  rv.open = true; rv.loading = true
  rv.courseId = c.courseId || c.id
  rv.courseName = c.name
  rv.form = { rating: 0, teaching: 0, contentScore: 0, homework: 0, content: '', anonymous: false }
  const r = await api('GET', '/reviews/course/' + rv.courseId)
  rv.loading = false
  rv.data = r.success ? r.data || {} : {}
  const mineRv = (rv.data.list || []).find((x) => x.mine)
  if (mineRv) {
    rv.form.rating = mineRv.rating || 0
    rv.form.teaching = mineRv.teaching || 0
    rv.form.contentScore = mineRv.contentScore || 0
    rv.form.homework = mineRv.homework || 0
    rv.form.content = mineRv.content || ''
    rv.form.anonymous = !!mineRv.anonymous
  }
}
async function submitReview() {
  if (!rv.form.rating) return toast('请先选择总体评分', 'error')
  const r = await api('POST', '/reviews', {
    courseId: rv.courseId,
    rating: rv.form.rating,
    teaching: rv.form.teaching || null,
    contentScore: rv.form.contentScore || null,
    homework: rv.form.homework || null,
    content: rv.form.content,
    anonymous: rv.form.anonymous,
  })
  toast(r.message || (r.success ? '评价已提交' : '失败'), r.success ? 'success' : 'error')
  if (r.success) openReview({ courseId: rv.courseId, name: rv.courseName })
}

onMounted(load)
</script>
