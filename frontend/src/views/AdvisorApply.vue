<template>
  <div class="page-wrap stats-page liquid">
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-icon blue" v-html="svgIcon('graduation-cap')" />
        <div>
          <div class="stat-value">{{ myAdvise ? (myAdvise.status === 'approved' ? '已确认' : myAdvise.status) : '未申请' }}</div>
          <div class="stat-label">当前状态</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon purple" v-html="svgIcon('award')" />
        <div><div class="stat-value">{{ advisors.length }}</div><div class="stat-label">可选导师</div></div>
      </div>
      <div class="stat-card">
        <div class="stat-icon amber" v-html="svgIcon('hourglass')" />
        <div><div class="stat-value">{{ myAdvise?.rank || '—' }}</div><div class="stat-label">申请排名</div></div>
      </div>
    </div>

    <div v-if="myAdvise && myAdvise.status === 'approved'" class="section">
      <div class="section-header">
        <div class="section-title" v-html="svgIcon('check-circle') + ' 已确认导师：' + myAdvise.teacherName" />
      </div>
      <div class="info-row">
        <div><b>毕设题目：</b>{{ myAdvise.topic || '待定' }}</div>
        <div><b>申请说明：</b>{{ myAdvise.preferenceText || '—' }}</div>
        <div v-if="myAdvise.teacherReply"><b>导师回复：</b>{{ myAdvise.teacherReply }}</div>
      </div>
    </div>

    <div class="section" v-if="advisors.length">
      <div class="section-header">
        <div class="section-title" v-html="svgIcon('graduation-cap') + ' 导师列表'"></div>
      </div>
      <div class="advisor-grid">
        <div v-for="a in advisors" :key="a.teacherId" class="advisor-card">
          <div class="advisor-avatar">{{ a.teacherName?.charAt(0) }}</div>
          <div class="advisor-info">
            <div class="advisor-name">{{ a.teacherName }} <span class="advisor-no">{{ a.teacherNo }}</span></div>
            <div class="advisor-college">{{ a.college }}</div>
            <div class="advisor-capacity">
              名额 <b>{{ a.capacity - (a.approved || 0) - (a.pending || 0) }}</b> / {{ a.capacity }}
              <span class="cap-detail">（已录 {{ a.approved || 0 }}，待审 {{ a.pending || 0 }}）</span>
            </div>
          </div>
          <div class="advisor-actions">
            <button class="btn btn-outline btn-sm"
              :disabled="!a.available || !!myAdvise"
              @click="apply(a)">
              {{ !a.available ? '满额' : (myAdvise ? '已申请' : '申请') }}
            </button>
          </div>
        </div>
      </div>
    </div>
    <div v-else class="empty-state">
      <div class="empty-icon" v-html="svgIcon('graduation-cap')" />
      <div class="empty-title">暂无可选导师</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api'
const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`
const advisors = ref<any[]>([])
const myAdvise = ref<any>(null)
async function load() {
  try {
    const [a, m] = await Promise.all([
      http.get('/advisor/teachers'),
      http.get('/advisor/mine').catch(() => null)
    ])
    advisors.value = a?.data || []
    // /advisor/mine 返回数组，取最新一条
    const mineList = m?.data || []
    if (Array.isArray(mineList) && mineList.length) {
      // 找状态最优先的（approved > pending > rejected）
      const priority = { approved: 3, pending: 2, rejected: 1, cancelled: 0 } as any
      mineList.sort((x, y) => (priority[y.status] || 0) - (priority[x.status] || 0))
      myAdvise.value = mineList[0]
    }
  } catch {}
}
async function apply(a: any) {
  try { await ElMessageBox.confirm(`确定申请 ${a.teacherName} 为毕业设计导师？`, '提示', { type: 'warning' }) } catch { return }
  try { await http.post('/advisor/apply', { teacherId: a.teacherId }); ElMessage.success('申请已提交'); load() }
  catch (e: any) { ElMessage.error(e?.message || '申请失败') }
}
onMounted(load)
</script>

<style scoped>
.page-wrap { padding: 4px 0; }
.advisor-grid { display: grid; gap: 12px; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); }
.advisor-card { background: var(--card-bg); border: 1px solid var(--border); border-radius: 12px; padding: 14px 16px; display: flex; gap: 12px; align-items: center; }
.advisor-avatar { width: 44px; height: 44px; border-radius: 50%; background: linear-gradient(135deg, #16a34a, #22c55e); display: flex; align-items: center; justify-content: center; font-size: 18px; font-weight: 700; color: #fff; flex-shrink: 0; }
.advisor-info { flex: 1; }
.advisor-name { font-size: 14px; font-weight: 600; }
.advisor-no { font-size: 11px; color: var(--text-light); margin-left: 6px; font-weight: 400; }
.advisor-college { font-size: 12px; color: var(--text-light); margin-top: 2px; }
.advisor-capacity { font-size: 12px; margin-top: 4px; }
.advisor-capacity b { color: var(--primary); }
.cap-detail { color: var(--text-light); font-size: 11px; }
.empty-state { text-align: center; padding: 60px 20px; color: var(--text-light); }
.empty-icon { font-size: 48px; opacity: 0.3; margin-bottom: 10px; }
.info-row { display: flex; flex-direction: column; gap: 6px; padding: 12px 16px; background: var(--card-bg); border: 1px solid var(--border); border-radius: 8px; font-size: 14px; }
.info-row b { color: var(--text-light); margin-right: 6px; }
</style>
