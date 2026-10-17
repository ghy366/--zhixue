<template>
  <div>
    <div class="stats-grid">
      <div class="stat-card"><div class="stat-icon blue"><Icon name="bar-chart" /></div><div><div class="stat-value">{{ logs.length }}</div><div class="stat-label">总操作数</div></div></div>
      <div class="stat-card"><div class="stat-icon green"><Icon name="check-circle" /></div><div><div class="stat-value">{{ byStatus['成功'] || 0 }}</div><div class="stat-label">成功</div></div></div>
      <div class="stat-card"><div class="stat-icon red"><Icon name="x-circle" /></div><div><div class="stat-value">{{ byStatus['失败'] || 0 }}</div><div class="stat-label">失败</div></div></div>
    </div>

    <div style="display:grid;grid-template-columns:1fr 1fr;gap:20px;margin-top:20px">
      <div class="card"><div class="section-title">操作类型分布</div>
        <div v-if="typeEntries.length" class="donut-wrap">
          <div v-for="[k, v] in typeEntries" :key="k" class="donut-item"><span class="donut-dot"></span>{{ k }}：{{ v }}</div>
        </div>
        <div v-else class="empty-state"><div class="empty-icon"><Icon name="file-text" /></div><p>暂无数据</p></div>
      </div>
      <div class="card"><div class="section-title">用户操作排行</div>
        <div v-if="byUser.length" class="bar-chart">
          <div v-for="u in byUser.slice(0, 8)" :key="u.userName" class="bar-item">
            <div class="bar-label">{{ u.userName }}</div>
            <div class="bar-track"><div class="bar-fill" :style="{ height: Math.max(u.count / maxCnt * 100, 2) + '%' }"></div></div>
            <div class="bar-value">{{ u.count }}</div>
          </div>
        </div>
        <div v-else class="empty-state"><div class="empty-icon"><Icon name="users" /></div><p>暂无数据</p></div>
      </div>
    </div>

    <div class="section" style="margin-top:20px"><div class="section-header"><div class="section-title">操作日志明细</div></div>
      <div v-if="!logs.length" class="empty-state"><div class="empty-icon"><Icon name="file-text" /></div><p>暂无日志</p></div>
      <div v-else class="data-table">
        <table>
          <thead><tr><th>时间</th><th>用户</th><th>类型</th><th>操作</th><th>状态</th><th>IP</th></tr></thead>
          <tbody>
            <tr v-for="(l, i) in logs.slice(0, 50)" :key="i">
              <td>{{ l.createdAt }}</td><td>{{ l.userName }}</td><td>{{ l.type }}</td><td>{{ l.operation }}</td><td>{{ l.status }}</td><td>{{ l.ip || '-' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import Icon from '@/components/Icon.vue'
import { api } from '@/api/client'

const logs = ref([])
const stats = ref({ byType: {}, byStatus: {}, byUser: [] })
const byStatus = computed(() => stats.value.byStatus || {})
const typeEntries = computed(() => Object.entries(stats.value.byType || {}))
const byUser = computed(() => stats.value.byUser || [])
const maxCnt = computed(() => Math.max(1, ...byUser.value.map(u => u.count)))

async function load() {
  const [lR, sR] = await Promise.all([api('GET', '/logs'), api('GET', '/logs/stats')])
  logs.value = lR.success ? lR.data : []
  stats.value = sR.success ? sR.data : { byType: {}, byStatus: {}, byUser: [] }
}
onMounted(load)
</script>
