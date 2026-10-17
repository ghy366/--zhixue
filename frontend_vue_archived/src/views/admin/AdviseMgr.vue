<template>
  <div>
    <div class="section-header"><div class="section-title">导师管理</div></div>
    <div class="stats-grid" style="margin-bottom:16px">
      <div class="stat-card"><div class="stat-icon blue"><Icon name="users" /></div><div><div class="stat-value">{{ ov.teacherCount || 0 }}</div><div class="stat-label">导师总数</div></div></div>
      <div class="stat-card"><div class="stat-icon green"><Icon name="check-circle" /></div><div><div class="stat-value">{{ ov.totalApproved || 0 }}</div><div class="stat-label">已确认配对</div></div></div>
      <div class="stat-card"><div class="stat-icon amber"><Icon name="hourglass" /></div><div><div class="stat-value">{{ ov.totalPending || 0 }}</div><div class="stat-label">待审批申请</div></div></div>
      <div class="stat-card"><div class="stat-icon purple"><Icon name="star" /></div><div><div class="stat-value">{{ ov.teachersWithSpace || 0 }}</div><div class="stat-label">有空位导师</div></div></div>
    </div>

    <div class="section"><div class="section-header"><div class="section-title">全局配置</div></div>
      <div class="form-row-inline">
        <label><input type="checkbox" v-model="cfgOpen"> 开启导师选择</label>
        <label>默认容量 <input class="form-input" style="width:80px" type="number" min="1" v-model="cfgCap"></label>
        <button class="btn btn-primary btn-sm" @click="saveCfg">保存</button>
      </div>
    </div>

    <div class="section" style="margin-top:16px"><div class="section-header"><div class="section-title">导师容量设置</div></div>
      <div class="data-table">
        <table>
          <thead><tr><th>导师</th><th>工号</th><th>容量上限</th><th>已确认</th><th>待审批</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="c in caps" :key="c.teacherId">
              <td><strong>{{ c.teacherName }}</strong></td><td>{{ c.teacherNo }}</td>
              <td><input class="form-input" style="width:80px" type="number" min="1" v-model="c._cap"></td>
              <td>{{ c.approved }}</td><td>{{ c.pending }}</td>
              <td><button class="btn btn-primary btn-sm" @click="saveCap(c)">保存</button></td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div class="section" style="margin-top:16px"><div class="section-header"><div class="section-title">全部申请（{{ list.length }}）</div></div>
      <div v-if="!list.length" class="empty-card">暂无申请记录</div>
      <div v-else class="data-table">
        <table>
          <thead><tr><th>学生</th><th>导师</th><th>论文方向</th><th>状态</th><th>时间</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="r in list" :key="r.id">
              <td>{{ r.studentName }} <span style="font-size:12px;color:var(--text-light)">({{ r.studentNo }})</span></td>
              <td>{{ r.teacherName }}</td><td>{{ r.topic || '-' }}</td>
              <td>{{ r.status }}</td>
              <td style="font-size:12px">{{ r.createdAt ? new Date(r.createdAt).toLocaleString('zh-CN') : '' }}</td>
              <td><button class="btn btn-outline btn-sm" @click="del(r)">删除</button></td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import Icon from '@/components/Icon.vue'
import { api } from '@/api/client'

const ov = ref({})
const cfgOpen = ref(false)
const cfgCap = ref(15)
const caps = ref([])
const list = ref([])

async function load() {
  const [oR, lR, cR] = await Promise.all([
    api('GET', '/advisor/admin/overview'), api('GET', '/advisor/admin/list'), api('GET', '/advisor/admin/capacities'),
  ])
  ov.value = oR.data || {}
  cfgOpen.value = ov.value.config?.open || false
  cfgCap.value = ov.value.config?.defaultCapacity || 15
  list.value = lR.data || []
  caps.value = (cR.data || []).map(c => ({ ...c, _cap: c.capacity }))
}
async function saveCfg() {
  const r = await api('POST', '/advisor/admin/cfg', { open: cfgOpen.value, defaultCapacity: parseInt(cfgCap.value) || 15 })
  alert(r.message); if (r.success) load()
}
async function saveCap(c) {
  const r = await api('POST', '/advisor/admin/cfg', { teacherId: c.teacherId, teacherCapacity: parseInt(c._cap) || 1 })
  alert(r.message); if (r.success) load()
}
async function del(r) { if (!confirm('确定删除该申请？')) return; const x = await api('DELETE', '/advisor/admin/' + r.id); alert(x.message); if (x.success) load() }
onMounted(load)
</script>
