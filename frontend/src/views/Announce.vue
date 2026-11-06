<template>
  <div class="page-wrap stats-page liquid">
    <div class="section">
      <div class="section-header">
        <div class="section-title" v-html="svgIcon('megaphone') + ' ' + t('公告管理（{n} 条）', { n: list.length })"></div>
        <button class="btn btn-primary btn-sm" @click="openDialog(null)">{{ t('+ 发布公告') }}</button>
      </div>
      <div class="announce-list">
        <div v-for="a in list" :key="a.id" class="announce-item">
          <div class="ai-head">
            <span class="ai-title">{{ a.title }}</span>
            <span class="ai-meta">{{ format(a.createdAt) }} · {{ a.pinned ? t('置顶') : t('普通') }}</span>
          </div>
          <div class="ai-content">{{ a.content }}</div>
          <div class="ai-read">
            <div class="ai-read-bar"><i :class="{ good: readRate(a) >= 60 }" :style="{ width: readRate(a) + '%' }" /></div>
            <span class="ai-read-num">{{ t('已读 {n} / {m} 人（{r}%）', { n: a.readCount || 0, m: studentCount, r: readRate(a) }) }}</span>
          </div>
          <div class="ai-acts">
            <button class="btn btn-outline btn-sm" @click="openDialog(a)">{{ t('编辑') }}</button>
            <button class="btn btn-danger btn-sm" @click="del(a)">{{ t('删除') }}</button>
          </div>
        </div>
        <div v-if="!list.length" class="empty-state">
          <div class="empty-icon" v-html="svgIcon('megaphone')" /><div class="empty-title">{{ t('暂无公告') }}</div>
        </div>
      </div>
    </div>
    <el-dialog v-model="dlg" :title="form.id ? t('编辑公告') : t('发布公告')" width="560px">
      <el-form :model="form" label-position="top">
        <el-form-item :label="t('标题')"><el-input v-model="form.title" /></el-form-item>
        <el-form-item :label="t('正文')"><el-input v-model="form.content" type="textarea" :rows="6" /></el-form-item>
        <el-form-item><el-checkbox v-model="form.pinned">{{ t('置顶') }}</el-checkbox></el-form-item>
      </el-form>
      <template #footer>
        <button class="btn btn-ghost btn-sm" @click="dlg = false">{{ t('取消') }}</button>
        <button class="btn btn-primary btn-sm" @click="save">{{ t('发布') }}</button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api'
import { t } from '@/i18n'
const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`
const list = ref<any[]>([])
const studentCount = ref(0)
const dlg = ref(false)
const form = reactive<any>({ id: null, title: '', content: '', pinned: false })
function format(t: any) { if (!t) return ''; const d = new Date(t); const p = (n: number) => String(n).padStart(2, '0'); return `${d.getFullYear()}-${p(d.getMonth()+1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}` }
function readRate(a: any) {
  return studentCount.value <= 0 ? 0 : Math.round((a.readCount || 0) * 100 / studentCount.value)
}
async function load() {
  try {
    const [r, s] = await Promise.all([
      http.get('/announcements?all=1'),
      http.get('/announcements/read-stats').catch(() => null),
    ])
    list.value = r?.data || []
    studentCount.value = s?.data?.studentCount ?? 0
  } catch {}
}
function openDialog(a: any) { Object.assign(form, a || { id: null, title: '', content: '', pinned: false }); dlg.value = true }
async function save() {
  try { if (form.id) await http.put(`/announcements/${form.id}`, form); else await http.post('/announcements', form); ElMessage.success(t('发布成功')); dlg.value = false; load() }
  catch (e: any) { ElMessage.error(e?.message || t('发布失败')) }
}
async function del(a: any) {
  try { await ElMessageBox.confirm(t('确定删除「{n}」？', { n: a.title }), t('提示'), { type: 'warning', confirmButtonText: t('确定'), cancelButtonText: t('取消') }) } catch { return }
  try { await http.delete(`/announcements/${a.id}`); ElMessage.success(t('已删除')); load() } catch (e: any) { ElMessage.error(e?.message || t('删除失败')) }
}
onMounted(load)
</script>

<style scoped>
.page-wrap { padding: 4px 0; }
.announce-list { display: flex; flex-direction: column; gap: 10px; }
.announce-item { background: var(--card-bg); border: 1px solid var(--border); border-radius: 10px; padding: 14px 16px; }
.ai-head { display: flex; justify-content: space-between; margin-bottom: 6px; }
.ai-title { font-weight: 600; font-size: 14px; }
.ai-meta { font-size: 12px; color: var(--text-light); }
.ai-content { font-size: 13px; color: var(--text-light); line-height: 1.6; margin-bottom: 10px; white-space: pre-wrap; }
.ai-acts { display: flex; gap: 6px; }
.ai-read { display: flex; align-items: center; gap: 10px; margin: 10px 0 4px; }
.ai-read-bar { flex: 0 0 180px; height: 8px; border-radius: 99px; background: rgba(148,163,184,.14); overflow: hidden; }
.ai-read-bar i { display: block; height: 100%; border-radius: 99px; background: linear-gradient(90deg, #2bb187, #38bdf8); transition: width .6s cubic-bezier(.22,.9,.36,1); }
.ai-read-bar i.good { background: linear-gradient(90deg, #10b981, #34d399); }
.ai-read-num { font-size: 12px; color: var(--text-light); font-variant-numeric: tabular-nums; }
.empty-state { text-align: center; padding: 60px 20px; color: var(--text-light); }
.empty-icon { font-size: 48px; opacity: 0.3; margin-bottom: 10px; }
</style>
