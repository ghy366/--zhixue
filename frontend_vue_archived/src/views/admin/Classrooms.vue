<template>
  <div>
    <div class="filter-bar"><button class="btn btn-primary btn-sm" @click="showAdd = true">+ 新增教室</button></div>
    <div v-if="!list.length" class="empty-state"><div class="empty-icon"><Icon name="building" /></div><p>暂无教室数据</p></div>
    <div v-else class="data-table">
      <table>
        <thead><tr><th>教室编号</th><th>名称</th><th>容量</th><th>位置</th><th>设备</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="c in list" :key="c.id">
            <td>{{ c.roomNo }}</td><td>{{ c.name }}</td><td>{{ c.capacity }}</td><td>{{ c.location || '' }}</td>
            <td>{{ c.equipment || '' }}</td><td>{{ c.status }}</td>
            <td><button class="btn btn-danger btn-sm" @click="del(c.id)">删除</button></td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="showAdd" class="modal-mask" @click.self="showAdd = false">
      <div class="modal">
        <h3>新增教室</h3>
        <div class="form-group"><label>教室编号</label><input v-model="f.roomNo"></div>
        <div class="form-group"><label>名称</label><input v-model="f.name"></div>
        <div class="form-group"><label>容量</label><input type="number" v-model="f.capacity"></div>
        <div class="form-group"><label>位置</label><input v-model="f.location"></div>
        <div class="form-group"><label>设备</label><input v-model="f.equipment" placeholder="如：多媒体"></div>
        <div class="form-group"><label>状态</label>
          <select v-model="f.status"><option>正常</option><option>维护中</option></select></div>
        <div class="modal-actions">
          <button class="btn btn-ghost" @click="showAdd = false">取消</button>
          <button class="btn btn-primary" @click="save">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import Icon from '@/components/Icon.vue'
import { api } from '@/api/client'

const list = ref([])
const showAdd = ref(false)
const f = ref({ roomNo: '', name: '', capacity: 0, location: '', equipment: '', status: '正常' })

async function load() { const r = await api('GET', '/classrooms'); list.value = r.success ? r.data : [] }
async function save() {
  if (!f.value.roomNo.trim()) { alert('请输入教室编号'); return }
  const r = await api('POST', '/classrooms', { ...f.value, capacity: parseInt(f.value.capacity) || 0 })
  alert(r.message); if (r.success) { showAdd.value = false; f.value = { roomNo: '', name: '', capacity: 0, location: '', equipment: '', status: '正常' }; load() }
}
async function del(id) { if (!confirm('确定删除该教室？')) return; const r = await api('DELETE', '/classrooms/' + id); alert(r.message); if (r.success) load() }
onMounted(load)
</script>
