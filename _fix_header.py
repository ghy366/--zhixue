# -*- coding: utf-8 -*-
with open(r'src\main\resources\static\index.html', 'r', encoding='utf-8') as f:
    lines = f.readlines()

start_idx = -1; end_idx = -1
for i, line in enumerate(lines):
    if 'id="user-menu-btn"' in line and start_idx < 0:
        start_idx = i
    if '</header>' in line and start_idx > 0 and end_idx < 0:
        end_idx = i

print(f'Replacing L{start_idx+1} ~ L{end_idx+1}')

replacement = [
'        <div class="user-info" style="position:relative;cursor:pointer" id="user-menu-btn">\n',
'          <div class="avatar" id="user-avatar">👤</div>\n',
'          <div class="user-detail">\n',
'            <span class="user-name" id="user-name">同学</span>\n',
'            <span class="user-meta" id="user-meta">学号</span>\n',
'          </div>\n',
'          <div class="user-dropdown" id="user-dropdown">\n',
'            <div class="user-dropdown-item" id="menu-profile">👤 个人信息</div>\n',
'            <div class="user-dropdown-item" id="menu-settings">⚙️ 账号设置</div>\n',
'            <div class="user-dropdown-item" id="menu-avatar">🎨 更换头像</div>\n',
'            <div class="user-dropdown-divider"></div>\n',
'            <div class="user-dropdown-item" id="logout-btn" style="color:var(--danger)">🚪 退出登录</div>\n',
'          </div>\n',
'        </div>\n',
'        <button id="logout-btn2" class="btn btn-ghost btn-sm">退出</button>\n',
'      </div>\n',
'    </header>\n',
]

result = lines[:start_idx] + replacement + lines[end_idx+1:]
with open(r'src\main\resources\static\index.html', 'w', encoding='utf-8') as f:
    f.writelines(result)
print(f'Fixed! {len(result)} lines total')
