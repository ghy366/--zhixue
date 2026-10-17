<template>
  <div class="page">
    <div class="page-head">
      <h1>个人信息</h1>
      <p>查看并维护你的账号资料、头像与登录密码。</p>
    </div>

    <div class="profile-grid">
      <!-- 左：头像 -->
      <div class="card">
        <div class="card-header"><h3>我的头像</h3></div>
        <div class="profile-avatar-big">
          <img v-if="isImg" :src="form.avatar" alt="" />
          <template v-else>{{ form.avatar || '👤' }}</template>
        </div>
        <div class="avatar-grid">
          <div
            v-for="em in emojis"
            :key="em"
            class="avatar-cell"
            :class="{ active: em === form.avatar }"
            @click="chooseAvatar(em)"
          >{{ em }}</div>
        </div>
        <div style="margin-top:12px">
          <label class="btn btn-outline btn-sm" style="cursor:pointer">
            上传图片
            <input type="file" accept="image/*" style="display:none" @change="onUpload" />
          </label>
        </div>
      </div>

      <!-- 右：资料 -->
      <div class="card">
        <div class="card-header"><h3>账号资料</h3></div>
        <div class="form-grid">
          <label class="field"><span>学号 / 工号</span><input :value="user.no" disabled /></label>
          <label class="field"><span>角色</span><input :value="roleLabel" disabled /></label>
          <label class="field"><span>姓名</span><input v-model="form.name" /></label>
          <label class="field"><span>学院</span><input v-model="form.college" placeholder="如：计算机学院" /></label>
          <label class="field"><span>专业</span><input v-model="form.major" placeholder="如：软件工程" /></label>
          <label class="field"><span>联系电话</span><input v-model="form.phone" placeholder="选填" /></label>
        </div>
        <div style="margin-top:16px;display:flex;gap:10px">
          <button class="btn btn-primary" @click="saveProfile">保存资料</button>
          <button class="btn btn-ghost" @click="reload">重置</button>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="card-header"><h3>修改密码</h3></div>
      <div class="form-grid">
        <label class="field"><span>当前密码</span><input type="password" v-model="pwd.old" placeholder="请输入当前密码" /></label>
        <label class="field"><span>新密码</span><input type="password" v-model="pwd.n1" placeholder="至少 4 位" /></label>
        <label class="field"><span>确认新密码</span><input type="password" v-model="pwd.n2" placeholder="再次输入新密码" /></label>
      </div>
      <div style="margin-top:16px">
        <button class="btn btn-primary" @click="changePwd">确认修改</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive } from 'vue'
import { api } from '@/api/client'
import { useAuthStore } from '@/store/auth'
import { ROLES } from '@/config/nav'
import { toast } from '@/utils/dom'

const auth = useAuthStore()
const user = computed(() => auth.user || {})
const roleLabel = computed(() => ROLES[auth.role] || '')
const isImg = computed(() => String(form.avatar || '').startsWith('data:'))

const emojis = ['👤','👨','👩','🧑','👨‍🎓','👩‍🎓','🧑‍🎓','👨‍🏫','👩‍🏫','🧑‍🏫','👑','🥷','🧙','🧝','🧚','🦸','🦹','💂','🕵️','👷','🦊','🐱','🐶','🐼','🐨','🐯','🦁','🐸','🐙','🦄','🐳','🦈','🌞','🌙','⭐','🔥','💎','🎨','🎮','🎯','🚀','🏆','⚽','🎸','💻','📚','💡','🔮','🍀','🌈','🌸','🎵','📱','💼','🎒','🧸']

const form = reactive({ name: '', college: '', major: '', phone: '', avatar: '' })
const pwd = reactive({ old: '', n1: '', n2: '' })

function reload() {
  const u = user.value
  form.name = u.name || ''
  form.college = u.college || ''
  form.major = u.major || ''
  form.phone = u.phone || ''
  form.avatar = u.avatar || '👤'
}
onMounted(reload)

async function saveProfile() {
  const r = await api('PUT', '/auth/profile', {
    name: form.name, college: form.college, major: form.major, phone: form.phone,
  })
  if (r.success) {
    auth.updateUser(r.data || { name: form.name, college: form.college, major: form.major, phone: form.phone })
    toast('个人信息已更新', 'success')
  } else toast(r.message || '保存失败', 'error')
}

async function chooseAvatar(em) {
  form.avatar = em
  const r = await api('PUT', '/auth/profile', { avatar: em })
  if (r.success) { auth.updateUser(r.data || { avatar: em }); toast('头像已更换', 'success') }
  else toast(r.message || '更换失败', 'error')
}

function onUpload(e) {
  const file = e.target.files && e.target.files[0]
  if (!file) return
  if (file.size > 512 * 1024) { toast('图片请小于 512KB', 'error'); return }
  const reader = new FileReader()
  reader.onload = async () => {
    form.avatar = reader.result
    const r = await api('PUT', '/auth/profile', { avatar: reader.result })
    if (r.success) { auth.updateUser(r.data || { avatar: reader.result }); toast('头像已上传', 'success') }
    else toast(r.message || '上传失败', 'error')
  }
  reader.readAsDataURL(file)
}

async function changePwd() {
  if (!pwd.old || !pwd.n1) return toast('请填写完整', 'error')
  if (pwd.n1.length < 4) return toast('新密码至少 4 位', 'error')
  if (pwd.n1 !== pwd.n2) return toast('两次新密码不一致', 'error')
  const r = await api('PUT', '/auth/password', { oldPassword: pwd.old, newPassword: pwd.n1 })
  if (r.success) { toast('密码修改成功', 'success'); pwd.old = pwd.n1 = pwd.n2 = '' }
  else toast(r.message || '修改失败', 'error')
}
</script>
