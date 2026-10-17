<template>
  <div>
    <div class="section-header">
      <div class="section-title">毕业设计指导 <span v-if="!cfg.open" class="tag warn">系统已关闭</span></div>
    </div>

    <div class="stats-grid" style="margin-bottom:16px">
      <div class="stat-card"><div class="stat-icon purple"><Icon name="graduation-cap" /></div><div><div class="stat-value">{{ stats.capacity || 0 }}</div><div class="stat-label">可带上限</div></div></div>
      <div class="stat-card"><div class="stat-icon green"><Icon name="check-circle" /></div><div><div class="stat-value">{{ stats.approved || 0 }}</div><div class="stat-label">已确认学生</div></div></div>
      <div class="stat-card"><div class="stat-icon amber"><Icon name="hourglass" /></div><div><div class="stat-value">{{ stats.pending || 0 }}</div><div class="stat-label">待审批</div></div></div>
      <div class="stat-card"><div class="stat-icon danger"><Icon name="x-circle" /></div><div><div class="stat-value">{{ stats.rejected || 0 }}</div><div class="stat-label">已拒绝</div></div></div>
    </div>

    <div class="adv-tabs">
      <button v-for="t in tabs" :key="t.k" class="adv-tab" :class="{ active: tab === t.k }" @click="tab = t.k">{{ t.l }}</button>
    </div>

    <div class="data-table">
      <table>
        <thead><tr><th>学生</th><th>论文方向</th><th>备注</th><th>状态</th><th>回复</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="r in filtered" :key="r.id">
            <td><strong>{{ r.studentName }}</strong><div style="font-size:12px;color:var(--text-light)">{{ r.studentNo || '' }} · {{ r.studentClass || '' }}</div></td>
            <td>{{ r.topic || '-' }}</td>
            <td style="max-width:260px">{{ r.preferenceText || '-' }}</td>
            <td>{{ fmtStatus(r.status) }}</td>
            <td style="font-size:12px">{{ r.teacherReply || '-' }}</td>
            <td>
              <template v-if="r.status === 'pending'">
                <button class="btn btn-success btn-sm" @click="openDecide(r, 'approve')">批准</button>
                <button class="btn btn-danger btn-sm" @click="openDecide(r, 'reject')">拒绝</button>
              </template>
              <span v-else>-</span>
            </td>
          </tr>
          <tr v-if="!filtered.length"><td colspan="6" class="empty-td">暂无数据</td></tr>
        </tbody>
      </table>
    </div>

    <div v-if="decideRow" class="modal-mask" @click.self="decideRow = null">
      <div class="modal">
        <h3>{{ decideAct === 'approve' ? '批准申请' : '拒绝申请' }}</h3>
        <div class="form-group"><label>回复（可选）</label><textarea v-model="reply" rows="3" class="form-input"></textarea></div>
        <div class="modal-actions">
          <button class="btn btn-outline" @click="decideRow = null">取消</button>
          <button class="btn" :class="decideAct === 'approve' ? 'btn-success' : 'btn-danger'" @click="submitDecide">确认</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import Icon from '@/components/Icon.vue'
import { api } from '@/api/client'

const cfg = ref({})
const stats = ref({})
const all = ref([])
const tab = ref('pending')
const decideRow = ref(null)
const decideAct = ref('')
const reply = ref('')

const tabs = computed(() => [
  ['all', '全部 (' + all.value.length + ')'],
  ['pending', '待审批 (' + all.value.filter(x => x.status === 'pending').length + ')'],
  ['approved', '已确认 (' + all.value.filter(x => x.status === 'approved').length + ')'],
  ['rejected', '已拒绝 (' + all.value.filter(x => x.status === 'rejected').length + ')'],
].map(([k, l]) => ({ k, l })))

const filtered = computed(() => tab.value === 'all' ? all.value : all.value.filter(x => x.status === tab.value))

function fmtStatus(s) {
  return { pending: '待审批', approved: '已确认', rejected: '已拒绝' }[s] || s
}
function openDecide(r, act) { decideRow.value = r; decideAct.value = act; reply.value = '' }
async function submitDecide() {
  const r = await api('POST', '/advisor/teacher/decide', { id: decideRow.value.id, decision: decideAct.value, reply: reply.value.trim() })
  alert(r.message)
  if (r.success) { decideRow.value = null; await load() }
}
async function load() {
  cfg.value = (await api('GET', '/advisor/config')).data || {}
  stats.value = (await api('GET', '/advisor/teacher/stats')).data || {}
  all.value = (await api('GET', '/advisor/teacher/list')).data || []
}
onMounted(load)
</script>
