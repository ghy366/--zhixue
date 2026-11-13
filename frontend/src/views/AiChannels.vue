<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api'
import { t } from '@/i18n'

const rows = ref<any[]>([])
const loading = ref(false)
const dialog = ref(false)
const editing = ref(false)
const saving = ref(false)
const syncing = ref(false)
const testingId = ref<number | null>(null)

const empty = () => ({ id: null, name: '', baseUrl: '', apiKey: '', models: '', visionModels: '', enabled: true, sortNo: 0, remark: '' })
const form = reactive<any>(empty())

async function load() {
  loading.value = true
  try {
    const r: any = await http.get('/ai/channels')
    rows.value = r?.data || []
  } catch (e: any) { ElMessage.error(e?.message || '读取通道失败') }
  finally { loading.value = false }
}

function openCreate() {
  Object.assign(form, empty())
  editing.value = false
  dialog.value = true
}

function openEdit(row: any) {
  Object.assign(form, {
    id: row.id, name: row.name, baseUrl: row.baseUrl, apiKey: '', models: row.models || '',
    visionModels: row.visionModels || '', enabled: row.enabled, sortNo: row.sortNo || 0, remark: row.remark || '',
  })
  editing.value = true
  dialog.value = true
}

async function save() {
  if (!form.name.trim() || !form.baseUrl.trim() || !form.models.trim()) {
    ElMessage.warning('请填写通道名称、Base URL 和模型名'); return
  }
  saving.value = true
  try {
    const body = { ...form }
    const r: any = editing.value
      ? await http.put('/ai/channels/' + form.id, body)
      : await http.post('/ai/channels', body)
    ElMessage.success(r?.message || '已保存')
    dialog.value = false
    load()
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') }
  finally { saving.value = false }
}

async function toggle(row: any) {
  try {
    const body = {
      name: row.name, baseUrl: row.baseUrl, apiKey: '', models: row.models,
      visionModels: row.visionModels, enabled: row.enabled, sortNo: row.sortNo, remark: row.remark,
    }
    await http.put('/ai/channels/' + row.id, body)
    ElMessage.success(row.enabled ? '已启用，模型下拉已更新' : '已停用')
  } catch (e: any) { ElMessage.error(e?.message || '操作失败'); row.enabled = !row.enabled; }
}

async function test(row: any) {
  testingId.value = row.id
  try {
    const r: any = await http.post('/ai/channels/' + row.id + '/test', {})
    const d = r?.data || {}
    if (d.ok) ElMessageBox.alert(`通道「${d.channel}」可用\n模型：${d.model}\n耗时：${d.ms} ms\n返回：${d.preview || '(空)'}`, '测试成功', { type: 'success' })
    else ElMessageBox.alert(`通道「${row.name}」调用失败\n耗时：${d.ms} ms\n原因：${d.error || '未知'}`, '测试失败', { type: 'error' })
  } catch (e: any) { ElMessage.error(e?.message || '测试失败') }
  finally { testingId.value = null }
}

async function del(row: any) {
  // 配置文件 / 主通道：库里删掉还会被配置重新带回来（主通道更是自动级联的起点），改为停用
  if (row.origin !== 'ADMIN') {
    const from = row.origin === 'PRIMARY' ? 'application.yml 的主通道配置' : 'application-local.yml'
    try {
      await ElMessageBox.confirm(
        `「${row.name}」来自 ${from}，直接删除后仍会被配置重新导入。\n建议停用：模型下拉立刻隐藏，页面记录保留。`,
        row.origin === 'PRIMARY' ? '这是系统主通道' : '该通道来自配置文件',
        { type: 'warning', confirmButtonText: '停用', cancelButtonText: '取消' })
    } catch { return }
    row.enabled = false
    await toggle(row)
    return
  }
  try {
    await ElMessageBox.confirm(`确定删除通道「${row.name}」？删除后该通道的模型会立即从下拉中消失。`, '删除通道', { type: 'warning' })
  } catch { return }
  try { await http.delete('/ai/channels/' + row.id); ElMessage.success('已删除'); load() }
  catch (e: any) { ElMessage.error(e?.message || '删除失败') }
}

async function syncConfig(overwrite = false) {
  syncing.value = true
  try {
    const r: any = await http.post('/ai/channels/sync?overwrite=' + overwrite, {})
    ElMessage.success(r?.message || '已同步')
    load()
  } catch (e: any) { ElMessage.error(e?.message || '同步失败') }
  finally { syncing.value = false }
}

async function confirmSync() {
  try {
    await ElMessageBox.confirm(
      '将配置文件同步到本页：\n· 主通道（ai.base-url）：地址 / 密钥 / 主模型 + 备用模型\n· 备用通道（ai.extra-channels）：配置里有、本页没有的会新增；来源为「配置文件」的用配置值覆盖\n· 本页后台新增的通道不受影响',
      '同步配置文件通道', { type: 'info', confirmButtonText: '开始同步' })
  } catch { return }
  syncConfig(true)
}

onMounted(load)
</script>

<template>
  <div class="page-wrap stats-page liquid ai-ch">
    <div class="ai-ch-head">
      <div>
        <h1>{{ t('AI 通道配置') }}</h1>
        <p>{{ t('新增 / 修改后即时生效，无需重启后端。密钥只存服务端，列表仅显示掩码。') }}</p>
      </div>
      <div class="ai-ch-acts">
        <button class="btn" :disabled="syncing" @click="confirmSync">
          {{ syncing ? t('同步中…') : t('同步配置文件通道') }}
        </button>
        <button class="btn btn-primary" @click="openCreate">+ {{ t('新增通道') }}</button>
      </div>
    </div>

    <div class="ai-ch-note">
      {{ t('配置文件里的主通道（阿里百炼等）与备用通道已自动导入本页，可直接编辑、测试、停用；主通道的修改会作用于「自动 · 智能级联」。后台新增 / 修改即时生效，无需重启。安全约束：Base URL 仅允许公网 http(s)，模型 id 用逗号分隔。') }}
    </div>

    <div class="ai-ch-card">
      <el-table :data="rows" v-loading="loading" stripe style="width:100%">
        <el-table-column prop="name" :label="t('通道名称')" min-width="130" />
        <el-table-column :label="t('来源')" width="90">
          <template #default="{ row }">
            <span :class="['src', row.origin === 'PRIMARY' ? 'pri' : (row.origin === 'CONFIG' ? 'cfg' : 'adm')]">
              {{ row.origin === 'PRIMARY' ? t('主通道') : (row.origin === 'CONFIG' ? t('配置文件') : t('后台')) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="baseUrl" :label="t('Base URL')" min-width="230" show-overflow-tooltip />
        <el-table-column :label="t('密钥')" width="110">
          <template #default="{ row }"><span :class="row.hasKey ? 'ok' : 'muted'">{{ row.apiKey }}</span></template>
        </el-table-column>
        <el-table-column :label="t('模型')" min-width="200">
          <template #default="{ row }">
            <span v-for="m in row.modelList" :key="m" class="tag">{{ m }}</span>
            <span v-if="!row.modelList?.length" class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('启用')" width="80">
          <template #default="{ row }"><el-switch v-model="row.enabled" @change="toggle(row)" /></template>
        </el-table-column>
        <el-table-column :label="t('排序')" width="70">
          <template #default="{ row }">{{ row.origin === 'PRIMARY' ? '—' : row.sortNo }}</template>
        </el-table-column>
        <el-table-column :label="t('操作')" width="210" fixed="right">
          <template #default="{ row }">
            <button class="lnk" @click="openEdit(row)">{{ t('编辑') }}</button>
            <button class="lnk" :disabled="testingId === row.id" @click="test(row)">{{ testingId === row.id ? t('测试中…') : t('测试') }}</button>
            <button class="lnk danger" @click="del(row)">{{ row.origin === 'ADMIN' ? t('删除') : t('停用') }}</button>
          </template>
        </el-table-column>
        <template #empty>{{ t('还没有通道，点「新增通道」添加，或从配置文件同步') }}</template>
      </el-table>
    </div>

    <el-dialog v-model="dialog" :title="editing ? t('编辑通道') : t('新增通道')" width="620px">
      <div class="form-grid">
        <label>{{ t('通道名称') }}<input v-model="form.name" :placeholder="t('如：我的 OpenAI 代理')" /></label>
        <label>{{ t('Base URL') }}<input v-model="form.baseUrl" placeholder="https://api.example.com/v1" /></label>
        <label>{{ t('API Key') }}<input v-model="form.apiKey" type="password" :placeholder="editing ? t('留空表示不修改') : t('无密钥网关可留空')" /></label>
        <label>{{ t('排序号') }}<input v-model.number="form.sortNo" type="number" min="0" /></label>
        <label class="full">{{ t('模型 id（逗号分隔）') }}<textarea v-model="form.models" rows="2" placeholder="gpt-4o-mini, deepseek-chat" /></label>
        <label class="full">{{ t('识图模型 id（逗号分隔，可留空）') }}<textarea v-model="form.visionModels" rows="2" placeholder="gpt-4o-mini" /></label>
        <label class="full">{{ t('备注') }}<input v-model="form.remark" :placeholder="t('选填')" /></label>
        <label class="check"><input type="checkbox" v-model="form.enabled" /> {{ t('启用（停用后下拉里不再显示）') }}</label>
      </div>
      <template #footer>
        <button class="btn" @click="dialog = false">{{ t('取消') }}</button>
        <button class="btn btn-primary" :disabled="saving" @click="save">{{ saving ? t('保存中…') : t('保存') }}</button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.ai-ch { padding: 24px; }
.ai-ch-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 20px; margin-bottom: 14px; }
.ai-ch-head h1 { font-size: 22px; font-weight: 700; color: var(--text); margin: 0 0 6px; }
.ai-ch-head p { font-size: 13px; color: var(--text-lighter); margin: 0; }
.ai-ch-acts { display: flex; gap: 10px; flex-shrink: 0; }
.ai-ch-note { padding: 10px 14px; border-radius: 10px; background: rgba(47,159,224,.10); color: var(--text-light); font-size: 12.5px; line-height: 1.7; margin-bottom: 14px; }
.ai-ch-card { padding: 12px; border: 1px solid var(--border); border-radius: 14px; background: var(--card); }
.tag { display: inline-block; margin: 0 5px 4px 0; padding: 2px 8px; border-radius: 6px; background: rgba(43,177,135,.12); color: var(--primary, #2bb187); font-size: 11.5px; }
.src { display: inline-block; padding: 1px 8px; border-radius: 6px; font-size: 11.5px; white-space: nowrap; }
.src.cfg { background: rgba(47,159,224,.14); color: #2f9fe0; }
.src.pri { background: rgba(139,92,246,.14); color: #8b5cf6; }
.src.adm { background: rgba(43,177,135,.12); color: var(--primary, #2bb187); }
.ok { color: #1fa877; font-size: 12px; }
.muted { color: var(--text-lighter); font-size: 12px; }
.lnk { padding: 3px 7px; border: none; background: transparent; color: var(--primary, #2bb187); font-size: 12.5px; cursor: pointer; font-family: inherit; }
.lnk:hover { text-decoration: underline; }
.lnk:disabled { opacity: .5; cursor: wait; }
.lnk.danger { color: #e05a5a; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.form-grid label { display: flex; flex-direction: column; gap: 6px; font-size: 12.5px; color: var(--text-light); }
.form-grid .full { grid-column: 1 / -1; }
.form-grid .check { display: block; grid-column: 1 / -1; }
.form-grid .check input { width: auto; margin-right: 8px; vertical-align: middle; }
.form-grid input, .form-grid textarea { width: 100%; padding: 8px 10px; border: 1px solid var(--border); border-radius: 8px; background: var(--card); color: var(--text); font-family: inherit; font-size: 13px; box-sizing: border-box; }
.form-grid textarea { resize: vertical; }
/* 弹窗不透明，避免玻璃拟态背景下透出后面的表格文字 */
.ai-ch :deep(.el-dialog) { background: var(--card); }
.ai-ch :deep(.el-dialog__body) { padding-top: 10px; }
@media (max-width: 760px) { .form-grid { grid-template-columns: 1fr; } }
</style>
