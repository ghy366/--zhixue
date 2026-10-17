<template>
  <div>
    <div v-if="!d.overview" class="empty-state"><div class="empty-icon"><Icon name="bar-chart" /></div><p>{{ msg || '暂无统计数据' }}</p></div>
    <template v-else>
      <div class="stats-grid">
        <div class="stat-card"><div class="stat-icon blue"><Icon name="clipboard" /></div><div><div class="stat-value">{{ ov.applied || 0 }}</div><div class="stat-label">本轮报名（选修）</div></div></div>
        <div class="stat-card"><div class="stat-icon green"><Icon name="check-circle" /></div><div><div class="stat-value">{{ ov.approved || 0 }}</div><div class="stat-label">已录取</div></div></div>
        <div class="stat-card"><div class="stat-icon purple"><Icon name="target" /></div><div><div class="stat-value">{{ ov.fillRate || 0 }}%</div><div class="stat-label">平均满员率</div></div></div>
        <div class="stat-card"><div class="stat-icon amber"><Icon name="hourglass" /></div><div><div class="stat-value">{{ ov.fullCount || 0 }}</div><div class="stat-label">满员课程 · 候补 {{ ov.waiting || 0 }} 人</div></div></div>
      </div>

      <div class="stats-two">
        <div class="section"><div class="section-header"><div class="section-title">课程热度排行（本轮报名）</div></div>
          <div v-if="hot.length" v-for="(x, i) in hot" :key="i" class="rank-row">
            <div class="rank-name"><span class="rank-idx" :class="{ top: i < 3 }">{{ i + 1 }}</span>{{ x.courseName }}<span class="rank-sub">{{ x.teacherName || '' }}</span></div>
            <div class="rank-bar-wrap"><div class="rank-bar" :style="{ width: ((x.applied || 0) * 100 / hyMax) + '%' }"></div></div>
            <div class="rank-val">{{ x.applied }} 人</div>
          </div>
          <div v-else class="empty-state"><div class="empty-icon"><Icon name="bar-chart" /></div><p>暂无报名数据</p></div>
        </div>
        <div class="section"><div class="section-header"><div class="section-title">满员率排行</div></div>
          <div v-if="fill.length" v-for="x in fill" :key="x.courseName" class="rank-row">
            <div class="rank-name">{{ x.courseName }}<span class="rank-sub">{{ x.approved }}/{{ x.capacity }}</span></div>
            <div class="rank-bar-wrap"><div class="rank-bar" :class="{ full: x.fillRate >= 100 }" :style="{ width: Math.min(100, x.fillRate) + '%' }"></div></div>
            <div class="rank-val">{{ x.fillRate }}%</div>
          </div>
          <div v-else class="empty-state"><div class="empty-icon"><Icon name="target" /></div><p>暂无数据</p></div>
        </div>
      </div>

      <div class="section"><div class="section-header"><div class="section-title">轮次报名趋势</div></div>
        <div v-if="rounds.length" v-for="x in rounds" :key="x.round" class="rank-row">
          <div class="rank-name">第 {{ x.round }} 轮</div>
          <div class="rank-bar-wrap"><div class="rank-bar round" :style="{ width: ((x.applied || 0) * 100 / rmMax) + '%' }"></div></div>
          <div class="rank-val">报名 {{ x.applied }} · 录取 {{ x.approved }} · 未录取 {{ x.rejected }} · 退课 {{ x.dropped }}</div>
        </div>
        <div v-else class="empty-state"><div class="empty-icon"><Icon name="refresh" /></div><p>暂无数据</p></div>
      </div>

      <div class="section"><div class="section-header"><div class="section-title">教师工作量</div></div>
        <div v-if="teachers.length" class="data-table">
          <table>
            <thead><tr><th>教师</th><th>学院</th><th>开设课程</th><th>本轮录取学生</th><th>收到评价</th><th>平均评分</th></tr></thead>
            <tbody>
              <tr v-for="t in teachers" :key="t.teacherName">
                <td><b>{{ t.teacherName }}</b></td><td>{{ t.college || '—' }}</td><td>{{ t.courseCount }}</td>
                <td>{{ t.studentCount }}</td><td>{{ t.reviewCount }}</td>
                <td>{{ t.reviewCount ? t.avgRating : '—' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else class="empty-state"><div class="empty-icon"><Icon name="users" /></div><p>暂无教师数据</p></div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import Icon from '@/components/Icon.vue'
import { api } from '@/api/client'

const d = ref({})
const msg = ref('')
const ov = computed(() => d.value.overview || {})
const hot = computed(() => (d.value.hot || []).filter(x => !x.required).slice(0, 10))
const fill = computed(() => (d.value.fill || []).filter(x => !x.required && x.capacity > 0).slice(0, 10))
const rounds = computed(() => d.value.rounds || [])
const teachers = computed(() => d.value.teachers || [])
const hyMax = computed(() => Math.max(1, ...hot.value.map(x => x.applied || 0)))
const rmMax = computed(() => Math.max(1, ...rounds.value.map(x => x.applied || 0)))

async function load() { const r = await api('GET', '/stats/report'); if (r.success) d.value = r.data || {}; else msg.value = r.message || '暂无统计数据' }
onMounted(load)
</script>
