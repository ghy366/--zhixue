<template>
  <div>
    <div class="admin-card">
      <h3>发布公告</h3>
      <div style="display:grid;grid-template-columns:2fr 1fr;gap:12px;margin-bottom:12px">
        <input v-model="f.title" placeholder="公告标题（如：第一轮选课 9 月 8 日 9:00 开始）" style="padding:10px;border:1.5px solid var(--border);border-radius:8px;background:var(--card);color:var(--text)">
        <select v-model="f.type" style="padding:10px;border:1.5px solid var(--border);border-radius:8px;background:var(--card);color:var(--text)">
          <option>选课通知</option><option>考试通知</option><option>系统公告</option>
        </select>
      </div>
      <textarea v-model="f.content" placeholder="公告内容…" style="width:100%;min-height:84px;padding:10px 12px;border:1.5px solid var(--border);border-radius:8px;font-family:inherit;font-size:14px;resize:vertical;background:var(--card);color:var(--text);outline:none;margin-bottom:12px"></textarea>
      <div style="display:flex;align-items:center;gap:16px;flex-wrap:wrap">
        <label style="display:flex;align-items:center;gap:6px;font-size:13px"><input type="checkbox" v-model="f.pinned"> 置顶显示</label>
        <label style="display:flex;align-items:center;gap:6px;font-size:13px">有效期至 <input type="datetime-local" v-model="f.expireTime" style="padding:6px 8px;border:1.5px solid var(--border);border-radius:6px"></label>
        <button class="btn btn-primary" @click="submit">{{ editing ? '保存修改' : '发布公告' }}</button>
        <span style="font-size:12px;color:var(--text-light)">公告会出现在学生/教师首页与顶栏通知中心</span>
      </div>
    </div>

    <div class="section">
      <div class="section-header"><div class="section-title">已发布公告（{{ list.length }}）</div></div>
      <div v-if="!list.length" class="empty-state"><div class="empty-icon"><Icon name="megaphone" /></div><p>还没有发布任何公告</p></div>
      <div v-else class="announce-cards">
        <div v-for="a in list" :key="a.id" class="announce-card">
          <div class="announce-head">
            <div class="announce-title">{{ a.title }}</div>
            <div class="announce-tags"><span class="announce-type">{{ a.type }}</span><span v-if="a.pinned" class="announce-pin">置顶</span></div>
          </div>
          <div class="announce-body">{{ a.content }}</div>
          <div class="announce-foot">
            <span>{{ a.createdAt ? new Date(a.createdAt).toLocaleString('zh-CN') : '' }}</span>
            <span>
              <button class="btn btn-outline btn-sm" @click="edit(a)">编辑</button>
              <button class="btn btn-danger btn-sm" @click="del(a)">删除</button>
            </span>
          </div>
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
const editing = ref(null)
const f = ref({ title: '', content: '', type: '选课通知', pinned: false, expireTime: '' })

async function load() { const r = await api('GET', '/announcements?all=1'); list.value = r.success ? r.data : [] }
function edit(a) {
  editing.value = a.id; f.value = { title: a.title || '', content: a.content || '', type: a.type || '系统公告', pinned: !!a.pinned, expireTime: a.expireTime ? new Date(a.expireTime).toISOString().slice(0, 16) : '' }
  window.scrollTo({ top: 0, behavior: 'smooth' })
}
async function submit() {
  if (!f.value.title.trim()) { alert('请填写公告标题'); return }
  const payload = {
    title: f.value.title, content: f.value.content.trim(), type: f.value.type,
    pinned: f.value.pinned, expireTime: f.value.expireTime ? new Date(f.value.expireTime).getTime() : '',
  }
  const r = editing.value ? await api('PUT', '/announcements/' + editing.value, payload) : await api('POST', '/announcements', payload)
  alert(r.message); if (r.success) { editing.value = null; f.value = { title: '', content: '', type: '选课通知', pinned: false, expireTime: '' }; load() }
}
async function del(a) { if (!confirm('确定删除「' + a.title + '」？')) return; const r = await api('DELETE', '/announcements/' + a.id); alert(r.message); if (r.success) load() }
onMounted(load)
</script>
