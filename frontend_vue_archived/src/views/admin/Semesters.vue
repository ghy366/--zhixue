<template>
  <div>
    <div class="filter-bar"><button class="btn btn-primary btn-sm" @click="showAdd = true">+ 新增学期</button></div>
    <div v-if="!list.length" class="empty-state"><div class="empty-icon"><Icon name="calendar" /></div><p>暂无学期数据</p></div>
    <div v-else class="data-table">
      <table>
        <thead><tr><th>ID</th><th>学期名称</th><th>开始日期</th><th>选课开始</th><th>选课结束</th><th>状态</th><th>当前学期</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="s in list" :key="s.id">
            <td>{{ s.id }}</td><td><b>{{ s.name }}</b></td><td>{{ s.startDate || '-' }}</td>
            <td>{{ s.selectionStart || '-' }}</td><td>{{ s.selectionEnd || '-' }}</td><td>{{ s.status }}</td>
            <td>{{ s.isCurrent ? '✓' : '' }}</td>
            <td><button class="btn btn-danger btn-sm" @click="del(s.id)">删除</button></td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="showAdd" class="modal-mask" @click.self="showAdd = false">
      <div class="modal">
        <h3>新增学期</h3>
        <div class="form-group"><label>学期名称</label><input v-model="f.name"></div>
        <div class="form-group"><label>开始日期</label><input type="date" v-model="f.startDate"></div>
        <div class="form-group"><label>选课开始</label><input type="date" v-model="f.selectionStart"></div>
        <div class="form-group"><label>选课结束</label><input type="date" v-model="f.selectionEnd"></div>
        <div class="form-group"><label><input type="checkbox" v-model="f.isCurrent"> 设为当前学期</label></div>
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
const f = ref({ name: '', startDate: '', selectionStart: '', selectionEnd: '', isCurrent: false })

async function load() { const r = await api('GET', '/semesters'); list.value = r.success ? r.data : [] }
async function save() {
  if (!f.value.name.trim()) { alert('请输入学期名称'); return }
  const r = await api('POST', '/semesters', { ...f.value, status: '未开始' })
  alert(r.message); if (r.success) { showAdd.value = false; f.value = { name: '', startDate: '', selectionStart: '', selectionEnd: '', isCurrent: false }; load() }
}
async function del(id) { if (!confirm('确定删除该学期？')) return; const r = await api('DELETE', '/semesters/' + id); alert(r.message); if (r.success) load() }
onMounted(load)
</script>
