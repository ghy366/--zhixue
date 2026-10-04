import re

with open(r'src\main\resources\static\app.js', 'r', encoding='utf-8') as f:
    c = f.read()

# 1. 在 backTop 绑定后 } 前插入新事件绑定
back_top_end = """    backTop.addEventListener('click', () => {
      window.scrollTo({ top: 0, behavior: 'smooth' });
    });
  }
}"""

new_bindings = """    backTop.addEventListener('click', () => {
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
  }
}"""

c = c.replace(back_top_end, new_bindings)

# 2. 把 avatar 显示逻辑改掉（原来用 name.charAt(0)）
# 找 user-avatar 赋值
old_avatar = "$('#user-avatar').textContent = user.name.charAt(0);"
new_avatar = """  const avatarEmoji = user.avatar || ({student:'🎓', teacher:'👨‍🏫', admin:'👑'}[user.role] || '👤');
  const avatarEl = $('#user-avatar');
  if (avatarEmoji.length === 1 && emojiChar(avatarEmoji)) {
    avatarEl.textContent = avatarEmoji;
    avatarEl.style.fontSize = '26px';
    avatarEl.style.background = '';
  } else {
    avatarEl.textContent = user.name.charAt(0);
  }"""
c = c.replace(old_avatar, new_avatar)

# 3. 在 initApp(); 之前插入所有新函数
insert_point = "\n// ===== 启动应用 =====\ninitApp();"

new_funcs = """

// ===== 个人中心 / 账号设置 / 头像 =====

const AVATAR_EMOJIS = ['👤','👨','👩','🧑','👨‍🎓','👩‍🎓','🧑‍🎓','👨‍🏫','👩‍🏫','🧑‍🏫','👑','🥷','🧙','🧝','🧚','🦸','🦹','💂','🕵️','👷','🥗','🦊','🐱','🐶','🐼','🐨','🐯','🦁','🐸','🐙','🦄','🐳','🦈','🌞','🌙','⭐','🔥','💎','🎨','🎮','🎯','🚀','🎯','🏆','⚽','🎸','💻','📚','💡','🔮','🍀','🌈','🌸','🌺','🍎','🍕','🎂','☕','🍜','🎵','📱','💼','🎒','🧸'];

function emojiChar(s) {
  return /[\\u{1F000}-\\u{1FFFF}]/u.test(s);
}

function closeModal(id) {
  const el = document.getElementById(id);
  if (el) el.style.display = 'none';
}

// 点遮罩关闭
document.addEventListener('click', (e) => {
  if (e.target.classList && e.target.classList.contains('modal-mask')) {
    e.target.style.display = 'none';
  }
});

function openProfileModal() {
  const user = getUser();
  $('#pf-name').value = user.name || '';
  $('#pf-no').value = user.no || '';
  $('#pf-role').value = ROLES[user.role] || user.role;
  $('#pf-college').value = user.college || '';
  $('#pf-major').value = user.major || '';
  $('#pf-phone').value = user.phone || '';
  closeModal('user-dropdown');
  document.getElementById('profile-avatar-preview').textContent = user.avatar || '👤';
  document.getElementById('profile-mask').style.display = 'flex';
}

async function saveProfile() {
  const user = getUser();
  const body = {
    name: $('#pf-name').value,
    college: $('#pf-college').value,
    major: $('#pf-major').value,
    phone: $('#pf-phone').value,
  };
  const r = await api('PUT', '/auth/profile', body);
  if (r.success) {
    toast('个人信息已更新', 'success');
    // 更新本地 user 和 UI
    Object.assign(user, r.data);
    localStorage.setItem('zhixue_user', JSON.stringify(user));
    $('#user-name').textContent = user.name;
    closeModal('profile-mask');
  } else toast(r.message, 'error');
}

function openSettingsModal() {
  $('#pwd-old').value = ''; $('#pwd-new').value = ''; $('#pwd-new2').value = '';
  closeModal('user-dropdown');
  document.getElementById('settings-mask').style.display = 'flex';
}

async function changePassword() {
  const oldP = $('#pwd-old').value;
  const newP = $('#pwd-new').value;
  const newP2 = $('#pwd-new2').value;
  if (!oldP || !newP) { toast('请填写完整', 'error'); return; }
  if (newP.length < 4) { toast('新密码至少 4 位', 'error'); return; }
  if (newP !== newP2) { toast('两次新密码不一致', 'error'); return; }
  const r = await api('PUT', '/auth/password', { oldPassword: oldP, newPassword: newP });
  if (r.success) { toast('密码修改成功', 'success'); closeModal('settings-mask'); }
  else toast(r.message, 'error');
}

function openAvatarModal() {
  const user = getUser();
  const current = user.avatar || '👤';
  const prev = document.getElementById('avatar-current');
  prev.textContent = current;
  closeModal('user-dropdown');
  // 渲染 emoji 网格
  const grid = document.getElementById('emoji-grid');
  grid.innerHTML = AVATAR_EMOJIS.map(em =>
    `<div class="avatar-cell" onclick="chooseAvatar('${em}')" style="cursor:pointer;width:40px;height:40px;display:flex;align-items:center;justify-content:center;border-radius:8px;font-size:22px;border:2px solid ${em === current ? 'var(--primary)' : 'transparent'};transition:all .15s" onmouseover="this.style.background='rgba(79,70,229,0.1)'" onmouseout="this.style.background=''">${em}</div>`
  ).join('');
  document.getElementById('avatar-mask').style.display = 'flex';
}

async function chooseAvatar(emoji) {
  const user = getUser();
  const r = await api('PUT', '/auth/profile', { avatar: emoji });
  if (r.success) {
    toast('头像已更换', 'success');
    Object.assign(user, r.data);
    localStorage.setItem('zhixue_user', JSON.stringify(user));
    // 更新顶栏头像
    const avatarEl = $('#user-avatar');
    avatarEl.textContent = emoji;
    avatarEl.style.fontSize = '26px';
    avatarEl.style.background = '';
    closeModal('avatar-mask');
  } else toast(r.message, 'error');
}

// 管理员 - 新增教师/用户
function openAdminUserModal(role) {
  $('#nu-role').value = role || 'teacher';
  $('#nu-name').value = ''; $('#nu-no').value = '';
  $('#nu-password').value = '123456';
  $('#nu-college').value = ''; $('#nu-phone').value = '';
  document.getElementById('admin-user-title').textContent =
    role === 'teacher' ? '➕ 新增教师账号' : role === 'admin' ? '➕ 新增管理员' : '➕ 新增学生';
  document.getElementById('admin-user-mask').style.display = 'flex';
}

async function adminCreateUser() {
  const body = {
    role: $('#nu-role').value,
    name: $('#nu-name').value,
    no: $('#nu-no').value,
    password: $('#nu-password').value,
    college: $('#nu-college').value,
    phone: $('#nu-phone').value,
    avatar: ({student:'🎓', teacher:'👨‍🏫', admin:'👑'})[$('#nu-role').value] || '👤',
  };
  if (!body.name || !body.no) { toast('姓名和工号不能为空', 'error'); return; }
  const r = await api('POST', '/admin/users', body);
  if (r.success) {
    toast('用户创建成功', 'success');
    closeModal('admin-user-mask');
    await renderUserMgr();  // 刷新列表
  } else toast(r.message, 'error');
}

// ===== 启动应用 =====
initApp();"""

c = c.replace(insert_point, new_funcs)

# 4. 更新 renderUserMgr 加"新增用户"按钮、角色筛选、删除操作
old_usermgr = """async function renderUserMgr() {
  const r = await api('GET', '/admin/users');
  const users = r.success ? r.data : [];
  $('#content').innerHTML = `<div class="data-table"><table><thead><tr><th>姓名</th><th>账号</th><th>角色</th><th>学院</th><th>专业/年级</th></tr></thead><tbody>${users.map(u => `<tr>
    <td><b>${u.name}</b></td><td>${u.no}</td>
    <td><span class="status-tag ${u.role==='student'?'status-approved':u.role==='teacher'?'status-pending':''}">${ROLES[u.role]}</span></td>
    <td>${u.college || '-'}</td><td>${u.major || '-'}${u.grade ? ' · ' + u.grade : ''}</td>
  </tr>`).join('')}</tbody></table></div>`;
}"""

new_usermgr = """async function renderUserMgr() {
  const r = await api('GET', '/admin/users');
  let users = r.success ? r.data : [];
  const user = getUser();
  let html = `
    <div style="display:flex;gap:10px;margin-bottom:14px;flex-wrap:wrap">
      <select id="uf-role" style="padding:8px 12px;border:1.5px solid var(--border);border-radius:8px">
        <option value="">全部角色</option>
        <option value="teacher">👨‍🏫 教师</option>
        <option value="student">🎓 学生</option>
        <option value="admin">👑 管理员</option>
      </select>
      <input id="uf-search" type="text" placeholder="🔍 搜索姓名/账号" style="flex:1;min-width:180px;padding:8px 12px;border:1.5px solid var(--border);border-radius:8px">
      <button class="btn btn-primary" onclick="openAdminUserModal('teacher')">➕ 新增教师</button>
      <button class="btn btn-ghost" onclick="openAdminUserModal('student')">➕ 新增学生</button>
      <button class="btn btn-danger btn-sm" style="padding:8px 14px" onclick="resetAllPwd()">🔧 批量重置密码(123456)</button>
    </div>
    <div class="data-table"><table><thead><tr>
      <th>头像</th><th>姓名</th><th>账号</th><th>角色</th><th>学院</th><th>手机号</th><th>操作</th>
    </tr></thead><tbody>
    ${users.map(u => `
      <tr data-role="${u.role}" data-name="${u.name}" data-no="${u.no}">
        <td><span style="font-size:22px">${u.avatar || '👤'}</span></td>
        <td><b>${u.name}</b></td>
        <td>${u.no}</td>
        <td><span class="status-tag ${u.role==='student'?'status-approved':u.role==='teacher'?'status-pending':''}">${ROLES[u.role]}</span></td>
        <td>${u.college || '-'}</td>
        <td>${u.phone || '-'}</td>
        <td>
          <button class="btn btn-primary btn-sm" onclick="resetOnePwd(${u.id},'${u.no}')">重置密码</button>
          ${u.id === user.id ? '<span style="color:#888;font-size:12px">(当前)</span>' :
            `<button class="btn btn-danger btn-sm" onclick="deleteUser(${u.id},'${u.name}')">删除</button>`}
        </td>
      </tr>
    `).join('')}
    </tbody></table></div>
    <div style="margin-top:10px;font-size:12px;color:var(--text-light)">共 ${users.length} 位用户 · 教师 ${users.filter(u=>u.role==='teacher').length} · 学生 ${users.filter(u=>u.role==='student').length} · 管理员 ${users.filter(u=>u.role==='admin').length}</div>
  `;
  $('#content').innerHTML = html;
  // 筛选事件
  $('#uf-role').addEventListener('change', filterUserTable);
  $('#uf-search').addEventListener('input', filterUserTable);
}

function filterUserTable() {
  const role = $('#uf-role').value;
  const kw = $('#uf-search').value.toLowerCase();
  document.querySelectorAll('#content tr[data-role]').forEach(tr => {
    const showRole = !role || tr.dataset.role === role;
    const showKw = !kw || tr.dataset.name.toLowerCase().includes(kw) || tr.dataset.no.toLowerCase().includes(kw);
    tr.style.display = (showRole && showKw) ? '' : 'none';
  });
}

async function deleteUser(id, name) {
  if (!(await confirmAsync(`确定删除用户 "${name}" 吗？此操作不可恢复。`, 'danger'))) return;
  const r = await api('DELETE', `/admin/users/${id}`);
  if (r.success) { toast('已删除', 'success'); await renderUserMgr(); }
  else toast(r.message, 'error');
}

async function resetOnePwd(id, no) {
  if (!(await confirmAsync(`将 "${no}" 的密码重置为 123456？`, 'warning'))) return;
  const r = await api('PUT', `/admin/users/${id}`, { password: '123456' });
  if (r.success) toast('密码已重置为 123456', 'success');
  else toast(r.message, 'error');
}

async function resetAllPwd() {
  if (!(await confirmAsync('将所有学生/教师的密码重置为 123456？', 'warning'))) return;
  const r = await api('GET', '/admin/users');
  if (!r.success) return toast('获取用户失败', 'error');
  let ok = 0, fail = 0;
  for (const u of r.data) {
    if (u.role !== 'admin') {
      const rr = await api('PUT', `/admin/users/${u.id}`, { password: '123456' });
      rr.success ? ok++ : fail++;
    }
  }
  toast(`重置完成：成功 ${ok}，失败 ${fail}`, fail ? 'error' : 'success');
}"""

c = c.replace(old_usermgr, new_usermgr)

with open(r'src\main\resources\static\app.js', 'w', encoding='utf-8') as f:
    f.write(c)
print('app.js updated successfully!')
