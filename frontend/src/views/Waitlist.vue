<template>
  <div class="page-wrap stats-page liquid">
    <div class="stats-grid">
      <div class="stat-card"><div class="stat-icon amber" v-html="svgIcon('hourglass')" /><div><div class="stat-value">{{ animWaiting }}</div><div class="stat-label">{{ t('候补中') }}</div></div></div>
      <div class="stat-card"><div class="stat-icon green" v-html="svgIcon('party')" /><div><div class="stat-value">{{ animConfirming }}</div><div class="stat-label">{{ t('待确认录取') }}</div></div></div>
      <div class="stat-card"><div class="stat-icon blue" v-html="svgIcon('clipboard')" /><div><div class="stat-value">{{ animList }}</div><div class="stat-label">{{ t('候补课程') }}</div></div></div>
    </div>

    <!-- ===== 候补水位：每门候补课我的位置 / 队列长度（递补概率一目了然） ===== -->
    <div class="section" v-if="list.length">
      <div class="section-header">
        <div class="section-title">{{ t('📊 候补水位') }}</div>
        <span class="wl-water-tip">{{ t('条越长 = 队列越长；高亮段 = 你的位置 · 结合课程剩余名额判断递补希望') }}</span>
      </div>
      <div class="wl-water">
        <div v-for="w in list" :key="w.waitlistId" class="wl-water-row">
          <span class="wl-water-name" :title="w.courseName">{{ w.courseName }}</span>
          <div class="wl-water-track">
            <i class="wl-water-queue" :style="{ width: Math.min(100, (Number(w.queueTotal) || 0) * 100 / Math.max(Number(w.queueTotal) || 1, Number(w.position) || 1, 10)) + '%' }" />
            <i class="wl-water-mine" :style="{ left: 'calc(' + Math.min(100, ((Number(w.position) || 1) * 100) / Math.max(Number(w.queueTotal) || 1, Number(w.position) || 1, 10)) + '% - 6px)' }" />
          </div>
          <span class="wl-water-num">{{ t('第 {a} / {b} 位', { a: w.position, b: w.queueTotal }) }}</span>
        </div>
      </div>
    </div>

    <div class="section">
      <div class="section-header">
        <div class="section-title" v-html="svgIcon('hourglass') + ' ' + t('我的候补队列')" />
        <router-link class="section-link" to="/courses">{{ t('去选课 →') }}</router-link>
      </div>
      <div v-if="list.length" class="course-grid">
        <div v-for="w in list" :key="w.waitlistId" class="wl-card" :class="{ 'wl-confirm-card': w.status === 'confirming' }">
          <div class="wl-card-top">
            <div class="wl-course">
              {{ w.courseName }}
              <span v-if="w.swapFromName" class="wl-swap-badge" :title="t('确认后自动退掉《{n}》完成换课', { n: w.swapFromName })">
                🔄 {{ t('换课保底') }}
              </span>
            </div>
            <span class="status-tag" :class="w.status === 'confirming' ? 'status-pending' : 'status-approved'">
              {{ w.status === 'confirming' ? t('待确认录取') : t('候补中') }}
            </span>
          </div>
          <div v-if="w.swapFromName" class="wl-meta">🔄 {{ t('换课保底：确认后自动退掉《{n}》', { n: w.swapFromName }) }}</div>
          <div class="wl-meta" v-html="svgIcon('user') + ' ' + w.teacherName + ' · ' + w.credit + ' ' + t('学分')" />
          <div class="wl-pos-row">
            <div class="wl-pos-bar">
              <i :style="{ width: posPct(w) + '%' }" :class="{ deep: posPct(w) > 60 }" />
              <em class="wl-pos-marker" :style="{ left: posPct(w) + '%' }" />
            </div>
            <div class="wl-pos-text">{{ t('第') }} <b>{{ w.position }}</b> / {{ w.queueTotal }} {{ t('位 · 前方') }} {{ Math.max(0, w.position - 1) }} {{ t('人') }}</div>
          </div>
          <div class="wl-cap-row" v-if="capOf(w.courseId)">
            <span>{{ t('课程容量 {c} · 已录取 {a}', { c: capOf(w.courseId).capacity, a: capOf(w.courseId).approved }) }}</span>
            <span class="wl-hope" :class="hopeOf(w).cls">{{ hopeOf(w).text }}</span>
          </div>
          <div v-if="w.status === 'confirming'" class="wl-meta">{{ t('请在') }} <span class="wl-countdown" :data-deadline="w.deadline">{{ fmtCountdown(w.deadline) }}</span> {{ t('前确认') }}</div>
          <div v-else class="wl-meta" v-html="svgIcon('clock') + ' ' + t('预计递补：') + (w.estimatedWait ? fmtWait(w.estimatedWait) : t('暂无历史数据，待定'))" />
          <div class="wl-acts">
            <button v-if="w.status === 'confirming'" class="btn btn-success btn-sm" @click="confirm(w)">
              {{ w.swapFromName ? t('确认换课') : t('锁定课程坐标') }}
            </button>
            <button v-if="w.status === 'confirming'" class="btn btn-ghost btn-sm" @click="cancel(w)">{{ t('放弃名额') }}</button>
            <button v-else class="btn btn-danger btn-sm" @click="cancel(w)">{{ t('退出候补') }}</button>
          </div>
        </div>
      </div>
      <div v-else class="empty-state">
        <div class="empty-icon" v-html="svgIcon('hourglass')" />
        <div class="empty-title">{{ t('暂无候补课程') }}</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api'
import { useCountUp } from '@/utils/useCountUp'
import { t } from '@/i18n'
const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`
const list = ref<any[]>([])
const courseMap = ref<Record<number, any>>({})
const waiting = computed(() => list.value.filter(w => w.status === 'waiting'))
const confirming = computed(() => list.value.filter(w => w.status === 'confirming'))

/* 统计卡数字滚动 */
const animWaiting = useCountUp(() => waiting.value.length)
const animConfirming = useCountUp(() => confirming.value.length)
const animList = useCountUp(() => list.value.length)

/* ===== 排位可视化 ===== */
const posPct = (w: any) => {
  const total = Number(w.queueTotal) || 0
  return total <= 0 ? 0 : Math.min(100, Math.max(4, Math.round((w.position || 1) * 100 / total)))
}
const capOf = (courseId: number) => courseMap.value[courseId] || null
/** 递补希望：排位 ≤ 剩余空位 → 希望大 */
function hopeOf(w: any) {
  const cap = capOf(w.courseId)
  if (!cap) return { text: '', cls: '' }
  const remain = Math.max(0, cap.capacity - cap.approved)
  if (remain <= 0) return { text: t('课程已满 · 需有人退课'), cls: 'warn' }
  if ((w.position || 1) <= remain) return { text: t('剩余 {n} 空位 · 递补希望大', { n: remain }), cls: 'good' }
  return { text: t('剩余 {n} 空位 · 需排队等待', { n: remain }), cls: 'mid' }
}
function fmtWait(mins: any) {
  const m = Number(mins)
  if (isNaN(m)) return String(mins)
  if (m >= 60) return t('约 {n} 小时', { n: (m / 60).toFixed(1) })
  return t('约 {n} 分钟', { n: m })
}
function fmtCountdown(deadline: any) {
  if (!deadline) return '--:--'
  const diff = new Date(deadline).getTime() - Date.now()
  if (diff <= 0) return t('已过期')
  const h = Math.floor(diff / 3600000), m = Math.floor((diff % 3600000) / 60000)
  return `${h}h ${m}m`
}
async function load() {
  try {
    const [r, cs] = await Promise.all([
      http.get('/waitlist/mine'),
      http.get('/courses').catch(() => null),
    ])
    list.value = r?.data || []
    const map: Record<number, any> = {}
    for (const c of cs?.data || []) map[c.id] = { capacity: c.capacity, approved: c.stat?.approved || 0 }
    courseMap.value = map
  } catch {}
}
async function confirm(w: any) {
  try {
    await ElMessageBox.confirm(
      w.swapFromName
        ? t('确认后自动退掉《{a}》并录取《{b}》（原子换课）。', { a: w.swapFromName, b: w.courseName })
        : t('确认录取该课程？'),
      w.swapFromName ? t('换课确认') : t('确认录取'),
      { type: 'warning', confirmButtonText: t('确定'), cancelButtonText: t('再想想') }
    )
  } catch { return }
  try { await http.post(`/waitlist/confirm/${w.waitlistId}`); ElMessage.success(t('确认成功')); load() }
  catch (e: any) { ElMessage.error(e?.message || t('确认失败')) }
}
async function cancel(w: any) {
  try { await ElMessageBox.confirm(t('确定取消？'), t('提示'), { type: 'warning', confirmButtonText: t('确定'), cancelButtonText: t('取消') }) } catch { return }
  try { await http.post('/waitlist/cancel', { courseId: w.courseId }); ElMessage.success(t('已取消')); load() }
  catch (e: any) { ElMessage.error(e?.message || t('操作失败')) }
}
onMounted(load)
</script>

<style scoped>
.page-wrap { padding: 4px 0; }
.wl-card { background: var(--card-bg); border: 1px solid var(--border); border-radius: 12px; padding: 14px 16px; }
/* 换课保底徽标 */
.wl-swap-badge {
  display: inline-block; margin-left: 6px; font-size: 11px; font-weight: 700;
  color: #0369a1; background: rgba(56,189,248,.14); border: 1px solid rgba(56,189,248,.35);
  border-radius: 999px; padding: 0 7px; line-height: 18px; vertical-align: middle;
}
html.dark .wl-swap-badge { color: #7dd3fc; }
.wl-confirm-card { border-color: rgba(245,158,11,0.5); box-shadow: 0 0 20px rgba(245,158,11,0.15); }
.wl-card-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.wl-course { font-size: 14px; font-weight: 600; }
.wl-meta { font-size: 12px; color: var(--text-light); margin-top: 4px; display: flex; gap: 6px; align-items: center; }
.wl-pos { font-size: 13px; color: var(--text-light); margin-top: 6px; }
.wl-pos b { color: var(--primary); }
.wl-pos-row { margin-top: 8px; }
.wl-pos-bar { position: relative; height: 8px; border-radius: 99px; background: rgba(148,163,184,.14); overflow: visible; }
.wl-pos-bar i { display: block; height: 100%; border-radius: 99px; background: linear-gradient(90deg, #f59e0b, #f97316); transition: width .6s cubic-bezier(.22,.9,.36,1); }
.wl-pos-bar i.deep { background: linear-gradient(90deg, #ef4444, #f97316); }
.wl-pos-marker {
  position: absolute; top: -3px; width: 14px; height: 14px; border-radius: 50%;
  transform: translateX(-50%); background: #fff; border: 3px solid var(--primary);
  box-shadow: 0 1px 5px rgba(0,0,0,.25);
}
.wl-pos-text { margin-top: 7px; font-size: 12.5px; color: var(--text-light); }
.wl-pos-text b { color: var(--primary); font-size: 14px; }
.wl-cap-row { display: flex; justify-content: space-between; gap: 8px; margin-top: 6px; font-size: 12px; color: var(--text-light); }
.wl-hope { font-weight: 700; }
.wl-hope.good { color: #059669; }
.wl-hope.mid { color: #d97706; }
.wl-hope.warn { color: #e11d48; }
.wl-acts { display: flex; gap: 8px; margin-top: 10px; }
.empty-state { text-align: center; padding: 60px 20px; color: var(--text-light); }
.empty-icon { font-size: 48px; opacity: 0.3; margin-bottom: 10px; }
/* ===== 候补水位图 ===== */
.wl-water-tip { font-size: 12px; color: var(--text-light); }
.wl-water { display: flex; flex-direction: column; gap: 10px; }
.wl-water-row { display: grid; grid-template-columns: minmax(90px, 160px) minmax(0, 1fr) auto; align-items: center; gap: 12px; }
.wl-water-name { font-size: 12.5px; font-weight: 600; color: var(--text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.wl-water-track { position: relative; height: 12px; border-radius: 99px; background: rgba(148,163,184,.12); }
.wl-water-queue { position: absolute; left: 0; top: 0; height: 100%; border-radius: 99px; background: rgba(56,189,248,.28); }
.wl-water-mine { position: absolute; top: -3px; width: 12px; height: 18px; border-radius: 4px; background: var(--primary); border: 2px solid #fff; box-shadow: 0 1px 5px rgba(0,0,0,.3); transform: translateX(-50%); }
.wl-water-num { font-size: 12px; font-weight: 700; color: var(--text-light); font-variant-numeric: tabular-nums; flex-shrink: 0; }
</style>