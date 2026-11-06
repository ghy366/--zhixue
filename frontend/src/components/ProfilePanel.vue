<template>
  <div class="profile-panel">
    <!-- 个人资料卡：头像 + 身份 + 预设头像 -->
    <div class="pp-card pp-profile-card">
      <div class="pp-avatar-row">
        <div class="avatar-circle" :class="{ 'is-custom': !!avatarUrl }" :title="t('点击或使用下方按钮上传')">
          <img v-if="avatarUrl" :src="avatarUrl" alt="avatar" />
          <span v-else class="avatar-emoji">{{ initials }}</span>
          <div class="avatar-overlay">{{ t('📷 换头像') }}</div>
        </div>
        <div class="pp-id">
          <div class="pp-name">{{ form.name || t('未命名') }}</div>
          <div class="pp-meta">
            <el-tag :type="roleTag" effect="plain" size="small" round>{{ roleText }}</el-tag>
            <span class="pp-no">{{ form.no }}</span>
          </div>
          <button class="pp-upload-btn" type="button" @click="triggerUpload">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><path d="m17 8-5-5-5 5"/><path d="M12 3v12"/></svg>
            {{ t('上传头像') }}
          </button>
          <div class="pp-hint">{{ t('支持 JPG/PNG · 不超过 2MB') }}</div>
        </div>
      </div>
      <input ref="fileInput" type="file" accept="image/jpeg,image/png" style="display:none" @change="onFileChange" />
      <div class="pp-emojis-label">{{ t('或选择预设头像') }}</div>
      <div class="pp-emojis">
        <button v-for="em in EMOJI_AVATARS" :key="em" type="button" class="emoji-btn"
          :class="{ selected: form.avatar === em }" :title="form.avatar === em ? t('当前头像') : em" @click="useEmoji(em)">
          {{ em }}
          <i v-if="form.avatar === em" class="emoji-check">✓</i>
        </button>
      </div>
    </div>

    <!-- 基本信息：桌面两列，窄容器自动单列 -->
    <div class="pp-card">
      <h4>{{ t('基本信息') }}</h4>
      <el-form :model="form" label-position="top" size="default" @submit.prevent>
        <div class="pp-grid">
          <el-form-item :label="t('姓名')"><el-input v-model="form.name" :placeholder="t('请输入姓名')" /></el-form-item>
          <el-form-item :label="t('学号/工号')">
            <el-input v-model="form.no" disabled><template #suffix><el-icon><Lock /></el-icon></template></el-input>
          </el-form-item>
          <el-form-item :label="t('学院')"><el-input v-model="form.college" :placeholder="t('如：计算机学院')" /></el-form-item>
          <el-form-item :label="t('专业/年级')"><el-input v-model="form.major" :placeholder="t('如：软件工程 2023')" /></el-form-item>
          <el-form-item :label="t('手机号')"><el-input v-model="form.phone" :placeholder="t('选填')" maxlength="11" /></el-form-item>
        </div>
      </el-form>
    </div>

    <!-- 账号安全：可折叠 -->
    <div class="pp-card pp-security">
      <button type="button" class="pp-sec-head" :aria-expanded="String(secOpen)" @click="secOpen = !secOpen">
        <span class="pp-sec-title">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect width="18" height="11" x="3" y="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
          {{ t('账号安全') }}
        </span>
        <span class="pp-sec-sub">{{ t('修改登录密码') }}</span>
        <svg class="pp-sec-chev" :class="{ open: secOpen }" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m18 15-6-6-6 6"/></svg>
      </button>
      <transition name="pp-fold">
        <div v-show="secOpen" class="pp-sec-body">
          <el-form :model="pwd" label-position="top" @submit.prevent>
            <el-form-item :label="t('原密码')">
              <el-input v-model="pwd.old" type="password" show-password :placeholder="t('请输入原密码')" />
            </el-form-item>
            <el-form-item :label="t('新密码')">
              <el-input v-model="pwd.new" type="password" show-password :placeholder="t('至少 4 位')" />
            </el-form-item>
            <el-form-item :label="t('确认新密码')" :class="{ 'pp-err': pwd.confirm && pwd.new !== pwd.confirm }">
              <el-input v-model="pwd.confirm" type="password" show-password :placeholder="t('再次输入新密码')" />
              <span v-if="pwd.confirm && pwd.new !== pwd.confirm" class="pp-err-text">{{ t('两次输入的新密码不一致') }}</span>
            </el-form-item>
            <el-button type="warning" plain style="width:100%" @click="changePwd" :loading="pwdLoading">{{ t('修改密码') }}</el-button>
          </el-form>
        </div>
      </transition>
    </div>

    <!-- 底部操作栏 -->
    <div class="pp-footer">
      <el-button plain @click="confirmLogout">{{ t('退出登录') }}</el-button>
      <el-button type="primary" :disabled="!dirty" :loading="saving" @click="save">{{ t('保存修改') }}</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Lock } from '@element-plus/icons-vue'
import http from '@/api'
import { encryptPassword } from '@/utils/rsa'
import { useAuthStore } from '@/stores/auth'
import { t } from '@/i18n'

const emit = defineEmits<{ (e: 'logout'): void }>()

const auth = useAuthStore()
const form = reactive({ name: '', no: '', college: '', major: '', phone: '', avatar: '' })
const pwd = reactive({ old: '', new: '', confirm: '' })
const saving = ref(false)
const pwdLoading = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)
const secOpen = ref(false)

const EMOJI_AVATARS = ['👤', '👨‍🎓', '👩‍🎓', '👨‍🏫', '👩‍🏫', '👑', '🦊', '🐱', '🐶', '🐼', '🦄', '🌟']

const avatarUrl = computed(() => {
  const a = form.avatar || auth.user?.avatar
  if (!a) return ''
  return a.startsWith('data:') ? a : ''
})
const initials = computed(() => {
  const a = form.avatar || auth.user?.avatar || ''
  if (a && !a.startsWith('data:')) return a
  return form.name?.charAt(0) || t('用')
})

const roleText = computed(() => ({ student: t('学生'), teacher: t('教师'), admin: t('管理员') } as any)[auth.user?.role] || t('用户'))
const roleTag = computed(() => ({ student: 'primary', teacher: 'success', admin: 'warning' } as any)[auth.user?.role] || 'info')

/* 表单脏检查：与最近一次加载/保存的快照比较，未修改时禁用保存按钮 */
let snap = { name: '', college: '', major: '', phone: '' }
const dirty = computed(() =>
  form.name !== snap.name || form.college !== snap.college ||
  form.major !== snap.major || form.phone !== snap.phone)

function takeSnap() {
  snap = { name: form.name, college: form.college, major: form.major, phone: form.phone }
}

function triggerUpload() { fileInput.value?.click() }

async function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (!/^image\/(jpeg|png)$/.test(file.type)) { ElMessage.error(t('仅支持 JPG/PNG 格式')); input.value = ''; return }
  if (file.size > 2 * 1024 * 1024) { ElMessage.error(t('图片不能超过 2MB')); input.value = ''; return }
  const reader = new FileReader()
  reader.onload = async (ev) => {
    try {
      const img = new Image()
      img.onload = async () => {
        const canvas = document.createElement('canvas')
        const SZ = 200
        canvas.width = SZ; canvas.height = SZ
        const ctx = canvas.getContext('2d')!
        const ratio = img.width / img.height
        let sx = 0, sy = 0, sw = img.width, sh = img.height
        if (ratio > 1) { sw = img.height; sx = (img.width - sw) / 2 }
        else { sh = img.width; sy = (img.height - sh) / 2 }
        ctx.drawImage(img, sx, sy, sw, sh, 0, 0, SZ, SZ)
        const base64 = canvas.toDataURL('image/jpeg', 0.85)
        form.avatar = base64
        await http.put('/auth/profile', { avatar: base64 })
        auth.setUser({ ...(auth.user as any), avatar: base64 })
        ElMessage.success(t('头像上传成功'))
      }
      img.src = ev.target?.result as string
    } catch (err: any) { ElMessage.error(t('上传失败: {m}', { m: err.message })) }
  }
  reader.readAsDataURL(file)
  input.value = ''
}

async function useEmoji(emoji: string) {
  if (form.avatar === emoji) return
  form.avatar = emoji
  try {
    await http.put('/auth/profile', { avatar: emoji })
    auth.setUser({ ...(auth.user as any), avatar: emoji })
    ElMessage.success(t('头像已更换'))
  } catch (e: any) { ElMessage.error(e?.message || t('更换失败')) }
}

async function load() {
  try {
    const r: any = await http.get('/auth/me')
    const u = r?.data || auth.user
    if (u) Object.assign(form, {
      name: u.name || '', no: u.no || '',
      college: u.college || '', major: u.major || '',
      phone: u.phone || '', avatar: u.avatar || ''
    })
  } catch {
    if (auth.user) Object.assign(form, { name: auth.user.name || '', no: auth.user.no || '', avatar: auth.user.avatar || '' })
  }
  takeSnap()
}

async function save() {
  saving.value = true
  try {
    const payload: any = { name: form.name, college: form.college, major: form.major, phone: form.phone }
    await http.put('/auth/profile', payload)
    auth.setUser({ ...(auth.user as any), name: form.name })
    takeSnap()
    ElMessage.success(t('保存成功'))
  } catch (e: any) { ElMessage.error(e?.message || t('保存失败')) }
  saving.value = false
}

function changePwd() {
  if (!pwd.old || !pwd.new || !pwd.confirm) { ElMessage.warning(t('请填写所有密码项')); return }
  if (pwd.new.length < 4) { ElMessage.warning(t('新密码至少 4 位')); return }
  if (pwd.new !== pwd.confirm) { ElMessage.warning(t('两次输入的新密码不一致')); return }
  doChangePwd()
}

async function doChangePwd() {
  pwdLoading.value = true
  try {
    await http.put('/auth/password', { oldPassword: await encryptPassword(pwd.old), newPassword: await encryptPassword(pwd.new) })
    ElMessage.success(t('密码修改成功'))
    pwd.old = pwd.new = pwd.confirm = ''
    secOpen.value = false
  } catch (e: any) { ElMessage.error(e?.message || t('密码修改失败')) }
  pwdLoading.value = false
}

function confirmLogout() {
  ElMessageBox.confirm(t('退出后将返回登录页'), t('确定退出登录？'), {
    confirmButtonText: t('退出登录'),
    cancelButtonText: t('取消'),
    type: 'warning',
  }).then(() => emit('logout')).catch(() => {})
}

onMounted(load)
</script>

<style scoped>
.profile-panel { display: flex; flex-direction: column; gap: 14px; padding-bottom: 72px; }

/* 卡片基础：统一 18px 圆角 */
.pp-card {
  background: var(--card); border: 1px solid var(--border);
  border-radius: 18px; padding: 18px 20px;
  box-shadow: var(--shadow);
}

/* 个人资料卡 */
.pp-avatar-row { display: flex; align-items: center; gap: 16px; }
.avatar-circle {
  width: 104px; height: 104px; border-radius: 50%; overflow: hidden; flex: 0 0 auto;
  background: linear-gradient(135deg, #2bb187, #38bdf8);
  display: flex; align-items: center; justify-content: center;
  font-size: 40px; font-weight: 700; color: #fff;
  cursor: pointer; position: relative; transition: transform .2s ease-out, box-shadow .2s ease-out;
  box-shadow: 0 0 0 3px rgba(43,177,135,.16);
}
.avatar-circle:hover { transform: scale(1.03); box-shadow: 0 0 0 4px rgba(43,177,135,.28); }
.avatar-circle img { width: 100%; height: 100%; object-fit: cover; border-radius: 50%; }
.avatar-overlay {
  position: absolute; inset: 0; background: rgba(15,60,45,0.52); color: #fff;
  font-size: 12px; display: flex; align-items: center; justify-content: center;
  opacity: 0; transition: opacity .2s ease-out;
}
.avatar-circle:hover .avatar-overlay { opacity: 1; }
.pp-id { display: flex; flex-direction: column; gap: 6px; min-width: 0; }
.pp-name { font-size: 18px; font-weight: 700; color: var(--text); line-height: 1.2; }
.pp-meta { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.pp-no { font-size: 12.5px; color: var(--text-light); font-variant-numeric: tabular-nums; }
.pp-upload-btn {
  display: inline-flex; align-items: center; gap: 6px; align-self: flex-start;
  border: 1px solid rgba(31,166,122,.4); border-radius: 999px; cursor: pointer;
  background: var(--primary-bg); color: var(--primary);
  font-size: 12.5px; font-weight: 600; padding: 5px 14px;
  transition: background .2s ease-out, border-color .2s ease-out, transform .18s ease-out;
}
.pp-upload-btn svg { width: 13px; height: 13px; }
.pp-upload-btn:hover { background: rgba(31,166,122,.16); border-color: rgba(31,166,122,.6); transform: translateY(-1px); }
.pp-upload-btn:active { transform: translateY(0) scale(.97); }
.pp-upload-btn:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; }
.pp-hint { font-size: 11.5px; color: var(--text-light); }

/* 预设头像网格 */
.pp-emojis-label { font-size: 12px; color: var(--text-light); margin: 14px 0 8px; }
.pp-emojis { display: grid; grid-template-columns: repeat(auto-fill, minmax(52px, 1fr)); gap: 8px; }
.emoji-btn {
  position: relative; height: 52px; border-radius: 12px; border: 1.5px solid var(--border);
  background: var(--card-bg-2); cursor: pointer; font-size: 24px; line-height: 1;
  display: flex; align-items: center; justify-content: center;
  transition: border-color .2s ease-out, background .2s ease-out, transform .18s ease-out;
}
.emoji-btn:hover { border-color: rgba(31,166,122,.55); background: var(--primary-bg); transform: translateY(-2px); }
.emoji-btn:active { transform: translateY(0) scale(.96); }
.emoji-btn:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; }
.emoji-btn.selected { border-color: var(--primary); background: var(--primary-bg); box-shadow: 0 0 0 3px rgba(31,166,122,.14); }
.emoji-check {
  position: absolute; right: -5px; top: -5px; width: 17px; height: 17px;
  border-radius: 50%; background: var(--primary); color: #fff;
  font-size: 10px; font-style: normal; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 1px 4px rgba(15,111,82,.4);
}

/* 基本信息两列布局：宽容器两列，抽屉/窄屏自动单列 */
.pp-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 0 16px; }
.pp-card h4 { margin: 0 0 14px; font-size: 14px; font-weight: 700; color: var(--green-text, #15803d); }
:deep(.el-form-item) { margin-bottom: 14px; }
:deep(.el-form-item__label) { color: var(--text-light); font-size: 12px; padding-bottom: 4px; }
/* 输入框：10px 圆角 + 主题绿焦点光晕 */
:deep(.el-input__wrapper) {
  border-radius: 10px;
  transition: box-shadow .2s ease-out;
}
:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--primary) inset, 0 0 0 3px rgba(31,166,122,.14) !important;
}
:deep(.el-input.is-disabled .el-input__wrapper) { border-radius: 10px; }

/* 账号安全（可折叠） */
.pp-sec-head {
  width: 100%; display: flex; align-items: center; gap: 10px;
  background: none; border: none; cursor: pointer; padding: 0; font: inherit; text-align: left;
  border-radius: 10px;
}
.pp-sec-head:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; }
.pp-sec-title { display: inline-flex; align-items: center; gap: 8px; font-size: 14px; font-weight: 700; color: var(--green-text, #15803d); }
.pp-sec-title svg { width: 15px; height: 15px; }
.pp-sec-sub { font-size: 12px; color: var(--text-light); flex: 1; }
.pp-sec-chev { width: 15px; height: 15px; color: var(--text-light); flex: 0 0 auto; transition: transform .22s ease-out; }
.pp-sec-chev.open { transform: rotate(180deg); color: var(--primary); }
.pp-sec-body { padding-top: 14px; }
.pp-fold-enter-active, .pp-fold-leave-active { transition: opacity .22s ease-out, max-height .22s ease-out; overflow: hidden; }
.pp-fold-enter-from, .pp-fold-leave-to { opacity: 0; max-height: 0; }
.pp-fold-enter-to, .pp-fold-leave-from { opacity: 1; max-height: 420px; }
.pp-err :deep(.el-input__wrapper) { box-shadow: 0 0 0 1px #ef4444 inset !important; }
.pp-err-text { font-size: 11.5px; color: #ef4444; margin-top: 2px; }

/* 底部操作栏：固定沉底 + 背景遮蔽 + 顶边框 + 轻阴影 */
.pp-footer {
  position: sticky; bottom: 0; z-index: 5;
  display: flex; gap: 10px; padding: 12px 4px;
  background: linear-gradient(to top, var(--bg) 78%, transparent);
  border-top: 1px solid var(--border);
  box-shadow: 0 -4px 14px rgba(15,60,45,.06);
}
[data-theme="dark"] .pp-footer { box-shadow: 0 -4px 14px rgba(0,0,0,.4); }
.pp-footer .el-button { flex: 1; margin: 0; border-radius: 10px; }
</style>
