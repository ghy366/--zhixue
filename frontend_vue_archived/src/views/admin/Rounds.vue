<template>
  <div>
    <!-- 时间设置 -->
    <div class="admin-card">
      <h3>选课时间设置 <span class="rs-tag">按时间自动开放 / 自动结束</span></h3>
      <p class="rs-hint">为每一轮设置<b>开始时间</b>与<b>结束时间</b>。到开始时间系统自动开放该轮选课，到结束时间自动关闭，无需人工值守切换；学生端登录页倒计时与「当前轮次」会同步更新。</p>
      <label class="rs-switch">
        <input type="checkbox" v-model="auto">
        <span class="rs-slider"></span>
        <span class="rs-switch-label">启用自动模式（关闭后回到手动切换）</span>
      </label>
      <div class="rs-rows">
        <div class="rs-row" v-for="n in [1,2,3]" :key="n">
          <span class="rs-round">第 {{ n }} 轮</span>
          <label class="rs-field"><span>开始</span><input type="datetime-local" v-model="schedules[n].openAt"></label>
          <label class="rs-field"><span>结束</span><input type="datetime-local" v-model="schedules[n].closeAt"></label>
          <span class="rs-state" :class="stateOf(n).cls">{{ stateOf(n).t }}</span>
        </div>
      </div>
      <div class="rs-actions">
        <button class="btn btn-primary" @click="saveSchedule">保存时间设置</button>
        <button class="btn" @click="clearSchedule">清空全部时间</button>
        <span class="rs-statusline">{{ statusLine }}</span>
      </div>
    </div>

    <!-- 手动控制 -->
    <div class="admin-card">
      <h3>手动控制 <span v-if="auto" class="rs-tag rs-tag-warn">自动模式开启中，手动操作会关闭自动模式</span></h3>
      <p style="font-size:13px;color:var(--text-light);margin-bottom:16px">切换当前选课轮次。每轮学生拥有 {{ pointsPerRound }} 积分。{{ auto ? '当前已启用自动模式，手动点击将立即退出自动模式。' : '' }}</p>
      <div class="round-controls">
        <button v-for="n in [1,2,3]" :key="n" class="round-btn" :class="{ active: cfg.currentRound === n }" @click="changeRound(n)">第 {{ n }} 轮</button>
        <button class="btn" :class="cfg.roundOpen ? 'btn-danger' : 'btn-success'" @click="toggleOpen">{{ cfg.roundOpen ? '关闭本轮选课' : '开放本轮选课' }}</button>
      </div>
    </div>

    <!-- 候补队列设置 -->
    <div class="admin-card">
      <h3>候补队列设置</h3>
      <p style="font-size:13px;color:var(--text-light);margin-bottom:12px">课程满员后学生可排队候补；有人退课自动递补，递补成功的学生需在限时内确认，超时自动释放名额给下一位。</p>
      <div style="display:flex;align-items:center;gap:10px;flex-wrap:wrap">
        <label style="font-size:14px">限时确认（分钟）</label>
        <input type="number" min="1" max="1440" v-model="confirmMinutes" style="width:110px;padding:8px 12px;border:1.5px solid var(--border);border-radius:8px">
        <button class="btn btn-primary" @click="saveConfirm">保存设置</button>
      </div>
    </div>

    <!-- 毕业学分要求 -->
    <div class="admin-card">
      <h3>毕业学分要求</h3>
      <p style="font-size:13px;color:var(--text-light);margin-bottom:12px">设置学生毕业所需学分（必修/选修/通识/实践），学生端「毕业进度」页据此展示完成情况并推荐课程。</p>
      <div style="display:flex;align-items:center;gap:10px;flex-wrap:wrap">
        <label style="font-size:14px">必修</label><input type="number" min="0" v-model="gRequired" style="width:90px;padding:8px 10px;border:1.5px solid var(--border);border-radius:8px">
        <label style="font-size:14px">选修</label><input type="number" min="0" v-model="gElective" style="width:90px;padding:8px 10px;border:1.5px solid var(--border);border-radius:8px">
        <label style="font-size:14px">通识</label><input type="number" min="0" v-model="gGeneral" style="width:90px;padding:8px 10px;border:1.5px solid var(--border);border-radius:8px">
        <label style="font-size:14px">实践</label><input type="number" min="0" v-model="gPractice" style="width:90px;padding:8px 10px;border:1.5px solid var(--border);border-radius:8px">
        <button class="btn btn-primary" @click="saveGrad">保存设置</button>
      </div>
    </div>

    <!-- 结算 -->
    <div class="admin-card">
      <h3>本轮结算</h3>
      <p style="font-size:13px;color:var(--text-light);margin-bottom:12px">选课期间学生只登记报名（积分冻结）。点下方按钮后，系统按每门课的投入积分<b>从高到低</b>统一录取，同分先报名者优先；超出容量的记为未录取并自动退还积分。</p>
      <button class="btn btn-primary" @click="settle">结算本轮选课</button>
    </div>

    <div class="admin-card"><h3>轮次说明</h3>
      <ul style="font-size:14px;color:var(--text-light);line-height:2">
        <li>每轮学生有 <b>{{ pointsPerRound }} 积分</b>，报名时投入并<b>冻结</b>，未录取自动退还。</li>
        <li>选课期间<b>只登记报名</b>，不即时录取；课程超额后依然可以继续报名。</li>
        <li>本轮结束后点「<b>结算本轮选课</b>」，按投入积分<b>从高到低</b>录取（同分先报名优先）。</li>
        <li>超出容量的记为未录取，可加入候补队列等待递补。</li>
      </ul>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, reactive, onMounted } from 'vue'
import { api } from '@/api/client'

const pointsPerRound = 60
const cfg = reactive({ currentRound: 1, roundOpen: false, roundOpenAt: null, roundCloseAt: null, confirmMinutes: 30, gradReqRequired: 12, gradReqElective: 10, gradReqGeneral: 6, gradReqPractice: 4 })
const auto = ref(false)
const schedules = reactive({ 1: { roundNo: 1, openAt: '', closeAt: '' }, 2: { roundNo: 2, openAt: '', closeAt: '' }, 3: { roundNo: 3, openAt: '', closeAt: '' } })
const confirmMinutes = ref(30)
const gRequired = ref(12); const gElective = ref(10); const gGeneral = ref(6); const gPractice = ref(4)

const fmtDT = (v) => v ? String(v).replace('T', ' ').slice(0, 16) : '—'
const now = () => new Date()
function stateOf(n) {
  const s = schedules[n]
  if (!s || (!s.openAt && !s.closeAt)) return { t: '未配置', cls: 'rs-off' }
  const oa = s.openAt ? new Date(s.openAt) : null
  const ca = s.closeAt ? new Date(s.closeAt) : null
  if (oa && now() < oa) return { t: '待开放', cls: 'rs-wait' }
  if (ca && now() >= ca) return { t: '已结束', cls: 'rs-done' }
  return { t: '进行中', cls: 'rs-live' }
}
const statusLine = computed(() => {
  if (auto.value) {
    return `当前：第 ${cfg.currentRound || '—'} 轮 · ${cfg.roundOpen ? '选课进行中' : (cfg.roundOpenAt ? '尚未开放' : '当前无开放轮次')}`
      + (cfg.roundCloseAt ? ` · 本轮将于 ${fmtDT(cfg.roundCloseAt)} 自动结束` : (cfg.roundOpenAt ? ` · 将于 ${fmtDT(cfg.roundOpenAt)} 自动开放` : ''))
  }
  return `当前：第 ${cfg.currentRound || '—'} 轮 · ${cfg.roundOpen ? '进行中' : '已关闭'}（手动模式）`
})

async function load() {
  const [r, sr] = await Promise.all([api('GET', '/system/config'), api('GET', '/system/round-schedule')])
  if (r.success) Object.assign(cfg, r.data)
  confirmMinutes.value = cfg.confirmMinutes || 30
  gRequired.value = cfg.gradReqRequired ?? 12; gElective.value = cfg.gradReqElective ?? 10
  gGeneral.value = cfg.gradReqGeneral ?? 6; gPractice.value = cfg.gradReqPractice ?? 4
  const sd = (sr && sr.success) ? sr.data : { autoSchedule: false, schedules: [] }
  auto.value = !!sd.autoSchedule
  for (const n of [1, 2, 3]) {
    const s = (sd.schedules || []).find(x => x.roundNo === n) || { roundNo: n }
    schedules[n] = { roundNo: n, openAt: s.openAt ? String(s.openAt).slice(0, 16) : '', closeAt: s.closeAt ? String(s.closeAt).slice(0, 16) : '' }
  }
}
function clearSchedule() { for (const n of [1,2,3]) { schedules[n].openAt = ''; schedules[n].closeAt = '' } }
async function saveSchedule() {
  const list = [1, 2, 3].map(n => ({ roundNo: n, openAt: schedules[n].openAt || null, closeAt: schedules[n].closeAt || null }))
  for (const s of list) {
    if ((s.openAt && !s.closeAt) || (!s.openAt && s.closeAt)) { alert(`第 ${s.roundNo} 轮需要同时填写开始与结束时间`); return }
    if (s.openAt && s.closeAt && s.openAt >= s.closeAt) { alert(`第 ${s.roundNo} 轮的结束时间必须晚于开始时间`); return }
  }
  if (auto.value && !list.some(s => s.openAt && s.closeAt)) { alert('启用自动模式前，请至少为一轮填写完整的时间窗口'); return }
  const r = await api('POST', '/system/round-schedule', { autoSchedule: auto.value, schedules: list })
  alert(r.message || '已保存'); if (r.success) load()
}
async function changeRound(n) {
  const r = await api('POST', '/system/round', { currentRound: n }); alert(r.message); if (r.success) load()
}
async function toggleOpen() {
  const r = await api('POST', '/system/round-open', { roundOpen: !cfg.roundOpen }); alert(r.message); if (r.success) load()
}
async function saveConfirm() {
  const v = parseInt(confirmMinutes.value); if (!v || v < 1) { alert('请输入有效的分钟数'); return }
  const r = await api('POST', '/system/confirm-minutes', { confirmMinutes: v }); alert(r.message); if (r.success) load()
}
async function saveGrad() {
  const r = await api('POST', '/system/graduation-reqs', {
    gradReqRequired: parseInt(gRequired.value) || 0, gradReqElective: parseInt(gElective.value) || 0,
    gradReqGeneral: parseInt(gGeneral.value) || 0, gradReqPractice: parseInt(gPractice.value) || 0,
  }); alert(r.message); if (r.success) load()
}
async function settle() {
  if (!confirm('将按每门课的投入积分从高到低统一录取，同分先报名者优先；超额部分记为未录取并退还积分。确定继续？')) return
  const r = await api('POST', '/enrollments/settle', {})
  if (r.success) { alert('第 ' + (r.data?.round || '') + ' 轮结算完成：' + r.message); load() } else alert(r.message)
}
onMounted(load)
</script>
