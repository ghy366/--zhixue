<template>
  <div class="profile-panel">
    <!-- 头像区 -->
    <div class="pp-avatar-box">
      <div class="avatar-circle" @click="triggerUpload">
        <img v-if="avatarUrl" :src="avatarUrl" alt="avatar" />
        <span v-else>{{ initials }}</span>
        <div class="avatar-overlay">📷 换头像</div>
      </div>
      <input ref="fileInput" type="file" accept="image/*" style="display:none" @change="onFileChange" />
      <div class="pp-name">{{ form.name || '未命名' }}</div>
      <div class="pp-meta">
        <el-tag :type="roleTag" effect="plain" size="small">{{ roleText }}</el-tag>
        <span class="pp-no">{{ form.no }}</span>
      </div>
      <div class="pp-hint">点击头像上传 · JPG/PNG · ≤2MB</div>
      <div class="pp-emojis">
        <button v-for="em in EMOJI_AVATARS" :key="em" class="emoji-btn" @click="useEmoji(em)">{{ em }}</button>
      </div>
    </div>

    <!-- 基本信息 -->
    <div class="pp-card">
      <h4>基本信息</h4>
      <el-form :model="form" label-position="top" size="default">
        <el-form-item label="姓名"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="学号/工号"><el-input v-model="form.no" disabled /></el-form-item>
        <el-form-item label="学院"><el-input v-model="form.college" placeholder="如：计算机学院" /></el-form-item>
        <el-form-item label="专业/年级"><el-input v-model="form.major" placeholder="如：软件工程 2023" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.phone" placeholder="选填" /></el-form-item>
      </el-form>
    </div>

    <!-- 修改密码 -->
    <div class="pp-card">
      <h4>修改密码</h4>
      <el-form :model="pwd" label-position="top">
        <el-form-item label="原密码"><el-input v-model="pwd.old" type="password" show-password /></el-form-item>
        <el-form-item label="新密码"><el-input v-model="pwd.new" type="password" show-password /></el-form-item>
        <el-button type="warning" plain style="width:100%" @click="changePwd" :loading="pwdLoading">修改密码</el-button>
      </el-form>
    </div>

    <!-- 底部操作 -->
    <div class="pp-footer">
      <el-button @click="$emit('logout')" plain>退出登录</el-button>
      <el-button type="primary" @click="save" :loading="saving">保存修改</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api'
import { useAuthStore } from '@/stores/auth'

defineEmits<{ (e: 'logout'): void }>()

const auth = useAuthStore()
const form = reactive({ name: '', no: '', college: '', major: '', phone: '', avatar: '' })
const pwd = reactive({ old: '', new: '' })
const saving = ref(false)
const pwdLoading = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

const EMOJI_AVATARS = ['👤', '👨‍🎓', '👩‍🎓', '👨‍🏫', '👩‍🏫', '👑', '🦊', '🐱', '🐶', '🐼', '🦄', '🌟']

const avatarUrl = computed(() => {
  const a = form.avatar || auth.user?.avatar
  if (!a) return ''
  return a.startsWith('data:') ? a : ''
})
const initials = computed(() => {
  const a = form.avatar || auth.user?.avatar || ''
  if (a && !a.startsWith('data:')) return a
  return form.name?.charAt(0) || '用'
})

const roleText = computed(() => ({ student: '学生', teacher: '教师', admin: '管理员' } as any)[auth.user?.role] || '用户')
const roleTag = computed(() => ({ student: 'primary', teacher: 'success', admin: 'warning' } as any)[auth.user?.role] || 'info')

function triggerUpload() { fileInput.value?.click() }

async function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (file.size > 2 * 1024 * 1024) { ElMessage.error('图片不能超过 2MB'); return }
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
        ElMessage.success('头像上传成功')
      }
      img.src = ev.target?.result as string
    } catch (err: any) { ElMessage.error('上传失败: ' + err.message) }
  }
  reader.readAsDataURL(file)
  input.value = ''
}

async function useEmoji(emoji: string) {
  form.avatar = emoji
  try {
    await http.put('/auth/profile', { avatar: emoji })
    auth.setUser({ ...(auth.user as any), avatar: emoji })
    ElMessage.success('头像已更换')
  } catch (e: any) { ElMessage.error(e?.message || '更换失败') }
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
}

async function save() {
  saving.value = true
  try {
    const payload: any = { name: form.name, college: form.college, major: form.major, phone: form.phone }
    await http.put('/auth/profile', payload)
    auth.setUser({ ...(auth.user as any), name: form.name })
    ElMessage.success('保存成功')
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') }
  saving.value = false
}

async function changePwd() {
  if (!pwd.old || !pwd.new) { ElMessage.warning('请填写原密码和新密码'); return }
  pwdLoading.value = true
  try {
    await http.put('/auth/password', { oldPassword: pwd.old, newPassword: pwd.new })
    ElMessage.success('密码修改成功')
    pwd.old = pwd.new = ''
  } catch (e: any) { ElMessage.error(e?.message || '密码修改失败') }
  pwdLoading.value = false
}

onMounted(load)
</script>

<style scoped>
.profile-panel { display: flex; flex-direction: column; gap: 14px; padding-bottom: 8px; }

/* 头像区 */
.pp-avatar-box {
  background: var(--card); border: 1px solid var(--border);
  border-radius: 12px; padding: 22px 18px 16px; text-align: center;
  display: flex; flex-direction: column; align-items: center; gap: 8px;
}
.avatar-circle {
  width: 88px; height: 88px; border-radius: 50%; overflow: hidden;
  background: linear-gradient(135deg, #6366f1, #a855f7);
  display: flex; align-items: center; justify-content: center;
  font-size: 32px; font-weight: 700; color: #fff;
  cursor: pointer; position: relative; transition: transform .2s;
}
.avatar-circle:hover { transform: scale(1.03); }
.avatar-circle img { width: 100%; height: 100%; object-fit: cover; border-radius: 50%; }
.avatar-overlay {
  position: absolute; inset: 0; background: rgba(0,0,0,0.5); color: #fff;
  font-size: 12px; display: none; align-items: center; justify-content: center;
}
.avatar-circle:hover .avatar-overlay { display: flex; }
.pp-name { font-size: 17px; font-weight: 700; color: var(--text); margin-top: 4px; }
.pp-meta { display: flex; align-items: center; gap: 8px; }
.pp-no { font-size: 12px; color: var(--text-light); }
.pp-hint { font-size: 11px; color: var(--text-light); }
.pp-emojis { display: flex; flex-wrap: wrap; gap: 6px; justify-content: center; margin-top: 4px; }
.emoji-btn {
  width: 30px; height: 30px; border-radius: 7px; border: 1px solid var(--border);
  background: var(--card-bg-2); cursor: pointer; font-size: 16px;
  display: flex; align-items: center; justify-content: center; transition: all .15s;
}
.emoji-btn:hover { border-color: var(--primary); transform: scale(1.12); }

/* 卡片 */
.pp-card {
  background: var(--card); border: 1px solid var(--border);
  border-radius: 12px; padding: 16px 18px 4px;
}
.pp-card h4 { margin: 0 0 12px; font-size: 14px; font-weight: 600; color: var(--green-text); }
:deep(.el-form-item) { margin-bottom: 14px; }
:deep(.el-form-item__label) { color: var(--text-light); font-size: 12px; padding-bottom: 4px; }

/* 底部按钮 */
.pp-footer {
  position: sticky; bottom: 0; display: flex; gap: 10px; padding: 12px 0 4px;
  background: linear-gradient(to top, var(--bg) 60%, transparent);
}
.pp-footer .el-button { flex: 1; margin: 0; }
</style>
