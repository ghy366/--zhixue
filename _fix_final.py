import re

with open(r'src\main\resources\static\app.js', 'r', encoding='utf-8') as f:
    c = f.read()

# ===== 修复 1: 把菜单事件绑定从 backTop if 块里挪出来 =====
old_block = """  // 回到顶部
  const backTop = document.getElementById('back-to-top');
  if (backTop) {
    window.addEventListener('scroll', () => {
      if (window.scrollY > 300) backTop.classList.add('show');
      else backTop.classList.remove('show');
    });
    backTop.addEventListener('click', () => {
      window.scrollTo({ top: 0, behavior: 'smooth' });
    });

    // 个人信息菜单
    const menuProfile = document.getElementById('menu-profile');
    if (menuProfile) menuProfile.addEventListener('click', (e) => { e.stopPropagation(); openProfileModal(); });

    // 账号设置菜单
    const menuSettings = document.getElementById('menu-settings');
    if (menuSettings) menuSettings.addEventListener('click', (e) => { e.stopPropagation(); openSettingsModal(); });

    // 更换头像菜单
    const menuAvatar = document.getElementById('menu-avatar');
    if (menuAvatar) menuAvatar.addEventListener('click', (e) => { e.stopPropagation(); openAvatarModal(); });
  }"""

new_block = """  // 回到顶部
  const backTop = document.getElementById('back-to-top');
  if (backTop) {
    window.addEventListener('scroll', () => {
      if (window.scrollY > 300) backTop.classList.add('show');
      else backTop.classList.remove('show');
    });
    backTop.addEventListener('click', () => {
      window.scrollTo({ top: 0, behavior: 'smooth' });
    });
  }

  // 个人信息菜单（用事件委托，更可靠）
  document.getElementById('menu-profile')?.addEventListener('click', (e) => {
    e.stopPropagation(); closeAllDropdowns(); openProfileModal();
  });
  document.getElementById('menu-settings')?.addEventListener('click', (e) => {
    e.stopPropagation(); closeAllDropdowns(); openSettingsModal();
  });
  document.getElementById('menu-avatar')?.addEventListener('click', (e) => {
    e.stopPropagation(); closeAllDropdowns(); openAvatarModal();
  });"""

c = c.replace(old_block, new_block)

# ===== 修复 2: 把 openProfileModal / openAvatarModal / chooseAvatar 整体替换 =====
old_funcs = r"""function openProfileModal\(\).*?closeModal\('profile-mask'\);\n  document.getElementById\('profile-mask'\)\.style\.display = 'flex';\n}\n\nasync function saveProfile.*?closeModal\('profile-mask'\);\n  } else toast\(r\.message, 'error'\);\n}\n\nfunction openSettingsModal\(\).*?closeModal\('settings-mask'\);\n}\n\nasync function changePassword.*?toast\(r\.message, 'error'\);\n}\n\nfunction openAvatarModal\(\).*?document.getElementById\('avatar-mask'\)\.style\.display = 'flex';\n}\n\nasync function chooseAvatar\(emoji\).*?closeModal\('avatar-mask'\);\n  } else toast\(r\.message, 'error'\);\n}"""

new_funcs = r"""
function closeAllDropdowns() {
  document.getElementById('user-dropdown')?.classList.remove('show');
  document.getElementById('notif-panel')?.classList.remove('show');
}

function openProfileModal() {
  const user = getUser();
  document.getElementById('pf-name').value = user.name || '';
  document.getElementById('pf-no').value = user.no || '';
  document.getElementById('pf-role').value = ROLES[user.role] || user.role;
  document.getElementById('pf-college').value = user.college || '';
  document.getElementById('pf-major').value = user.major || '';
  document.getElementById('pf-phone').value = user.phone || '';
  const prev = document.getElementById('profile-avatar-preview');
  const av = user.avatar || '';
  if (av.startsWith('data:')) { prev.innerHTML = '<img src="'+av+'" style="width:100%;height:100%;object-fit:cover;border-radius:50%">'; prev.style.background=''; }
  else { prev.textContent = av || '👤'; prev.style.background='linear-gradient(135deg,#4f46e5,#7c3aed)'; }
  document.getElementById('profile-mask').style.display = 'flex';
}

async function saveProfile() {
  const user = getUser();
  const body = {
    name: document.getElementById('pf-name').value,
    college: document.getElementById('pf-college').value,
    major: document.getElementById('pf-major').value,
    phone: document.getElementById('pf-phone').value,
  };
  const r = await api('PUT', '/auth/profile', body);
  if (r.success) {
    toast('个人信息已更新', 'success');
    Object.assign(user, r.data);
    localStorage.setItem('zhixue_user', JSON.stringify(user));
    $('#user-name').textContent = user.name;
    closeModal('profile-mask');
  } else toast(r.message, 'error');
}

function openSettingsModal() {
  document.getElementById('pwd-old').value = '';
  document.getElementById('pwd-new').value = '';
  document.getElementById('pwd-new2').value = '';
  document.getElementById('settings-mask').style.display = 'flex';
}

async function changePassword() {
  const oldP = document.getElementById('pwd-old').value;
  const newP = document.getElementById('pwd-new').value;
  const newP2 = document.getElementById('pwd-new2').value;
  if (!oldP || !newP) return toast('请填写完整', 'error');
  if (newP.length < 4) return toast('新密码至少 4 位', 'error');
  if (newP !== newP2) return toast('两次新密码不一致', 'error');
  const r = await api('PUT', '/auth/password', { oldPassword: oldP, newPassword: newP });
  if (r.success) { toast('密码修改成功', 'success'); closeModal('settings-mask'); }
  else toast(r.message, 'error');
}

function openAvatarModal() {
  const user = getUser();
  const cur = user.avatar || '👤';
  const prev = document.getElementById('avatar-current');
  if (cur.startsWith('data:')) { prev.innerHTML = '<img src="'+cur+'" style="width:100%;height:100%;object-fit:cover;border-radius:50%">'; prev.style.background=''; }
  else { prev.textContent = cur; prev.style.background='linear-gradient(135deg,#4f46e5,#7c3aed)'; }
  // emoji 网格
  const grid = document.getElementById('emoji-grid');
  const emojis = ['👤','👨','👩','🧑','👨‍🎓','👩‍🎓','👨‍🏫','👩‍🏫','👑','🦊','🐱','🐶','🐼','🐨','🐯','🦁','🐸','🐙','🦄','🔥','💎','🎨','🎮','🎯','🚀','🏆','⚽','🎸','💻','📚','💡','🍀','🌈','🌸','🍎','🍕','🎂','☕','🎵','📱','💼','🎒'];
  grid.innerHTML = emojis.map(em =>
    '<div class="avatar-cell" onclick="chooseAvatar(\''+em+'\')" style="cursor:pointer;width:40px;height:40px;display:flex;align-items:center;justify-content:center;border-radius:8px;font-size:22px;border:2px solid '+(em===cur?'var(--primary)':'transparent')+';transition:all .15s" onmouseover="this.style.background=\'rgba(79,70,229,0.1)\'" onmouseout="this.style.background=\'\'">'+em+'</div>'
  ).join('');
  document.getElementById('avatar-mask').style.display = 'flex';
}

async function chooseAvatar(emoji) {
  const r = await api('PUT', '/auth/profile', { avatar: emoji });
  if (r.success) {
    toast('头像已更换', 'success');
    const user = getUser(); Object.assign(user, r.data);
    localStorage.setItem('zhixue_user', JSON.stringify(user));
    updateTopbarAvatar(user);
    closeModal('avatar-mask');
  } else toast(r.message, 'error');
}

function updateTopbarAvatar(user) {
  const el = document.getElementById('user-avatar');
  if (!el) return;
  const av = user.avatar || '';
  if (av.startsWith('data:')) {
    el.innerHTML = '<img src="'+av+'" style="width:100%;height:100%;object-fit:cover;border-radius:50%">';
    el.style.background = '';
  } else {
    el.textContent = av || ({student:'🎓',teacher:'👨‍🏫',admin:'👑'}[user.role]||'👤');
    el.style.fontSize = '26px';
    el.style.background = 'linear-gradient(135deg,#4f46e5,#7c3aed)';
    el.style.color = '#fff';
  }
}

async function uploadAvatar(file) {
  if (!file) return;
  if (file.size > 2*1024*1024) return toast('图片不能超过 2MB', 'error');
  const reader = new FileReader();
  reader.onload = async (e) => {
    // 压缩图片到 200x200
    const img = new Image();
    img.onload = async () => {
      const canvas = document.createElement('canvas');
      canvas.width = 200; canvas.height = 200;
      const ctx = canvas.getContext('2d');
      ctx.drawImage(img, 0, 0, 200, 200);
      const base64 = canvas.toDataURL('image/jpeg', 0.8);
      const r = await api('PUT', '/auth/profile', { avatar: base64 });
      if (r.success) {
        toast('头像上传成功', 'success');
        const user = getUser(); Object.assign(user, r.data);
        localStorage.setItem('zhixue_user', JSON.stringify(user));
        updateTopbarAvatar(user);
        // 更新头像模态框预览
        const prev = document.getElementById('avatar-current');
        prev.innerHTML = '<img src="'+base64+'" style="width:100%;height:100%;object-fit:cover;border-radius:50%">';
        prev.style.background = '';
      } else toast(r.message, 'error');
    };
    img.src = e.target.result;
  };
  reader.readAsDataURL(file);
}
"""

c = re.sub(old_funcs, new_funcs, c, flags=re.DOTALL)

# ===== 修复 3: showApp 里的头像改成调用 updateTopbarAvatar =====
old_showapp_avatar = """    const avatarEmoji = user.avatar || ({student:'🎓', teacher:'👨‍🏫', admin:'👑'}[user.role] || '👤');
  const avatarEl = $('#user-avatar');
  avatarEl.textContent = avatarEmoji;
  avatarEl.style.fontSize = '26px';
  avatarEl.style.background = 'linear-gradient(135deg,#4f46e5,#7c3aed)';
  avatarEl.style.color = '#fff';"""
new_showapp_avatar = "  updateTopbarAvatar(user);"
c = c.replace(old_showapp_avatar, new_showapp_avatar)

# ===== 修复 4: 删掉旧的 emojiChar 函数（不再需要）=====
c = c.replace("\nfunction emojiChar(s) {\n  return /[\\u{1F000}-\\u{1FFFF}]/u.test(s);\n}", "")

with open(r'src\main\resources\static\app.js', 'w', encoding='utf-8') as f:
    f.write(c)
print('app.js FIXED')
