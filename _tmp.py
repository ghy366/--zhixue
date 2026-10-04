import os
os.chdir(r'g:\AI\智学选课')

# 1. User.java: avatar -> TEXT
with open(r'src\main\java\com\zhixue\entity\User.java', 'r', encoding='utf-8') as f:
    c = f.read()
old = '    private String avatar;  // emoji or icon char, frontend renders'
new = '    @Column(columnDefinition = "TEXT")\n    private String avatar;  // emoji 或 base64 图片'
c = c.replace(old, new)
with open(r'src\main\java\com\zhixue\entity\User.java', 'w', encoding='utf-8') as f:
    f.write(c)
print('1. User.java OK')

# 2. index.html
with open(r'src\main\resources\static\index.html', 'r', encoding='utf-8') as f:
    c = f.read()

old_u = '选择 Emoji 头像</div>'
new_u = '''📷 上传自定义头像</div>
        <div style="font-size:12px;color:var(--text-light);margin-top:4px">支持 JPG/PNG，自动压缩至 200x200</div>
        <input type="file" id="avatar-file" accept="image/*" style="display:none" onchange="uploadAvatar(this.files[0]); this.value=''">
      </div>
      <div style="font-weight:600;margin-bottom:8px">或选择 Emoji 头像</div>'''
c = c.replace(old_u, new_u)

old_p = '''<div id="profile-form">
        <div class="avatar-preview" id="profile-avatar-preview" style="width:80px;height:80px;font-size:36px;margin:0 auto 16px;border-radius:50%;display:flex;align-items:center;justify-content:center;background:linear-gradient(135deg,#4f46e5,#7c3aed);color:#fff">👤</div>
        <div class="form-group"><label>姓名</label>'''
new_p = '''<div id="profile-form">
        <div style="margin-bottom:16px;text-align:center">
          <div id="profile-avatar-preview" style="width:80px;height:80px;font-size:36px;margin:0 auto 8px;border-radius:50%;display:flex;align-items:center;justify-content:center;background:linear-gradient(135deg,#4f46e5,#7c3aed);color:#fff;overflow:hidden">👤</div>
          <button type="button" class="btn btn-ghost btn-sm" onclick="closeModal('profile-mask');openAvatarModal()">🎨 更换头像</button>
        </div>
        <div class="form-group"><label>姓名</label>'''
c = c.replace(old_p, new_p)

with open(r'src\main\resources\static\index.html', 'w', encoding='utf-8') as f:
    f.write(c)
print('2. index.html OK')