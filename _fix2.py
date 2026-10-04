# -*- coding: utf-8 -*-
with open(r'src\main\resources\static\app.js', 'r', encoding='utf-8') as f:
    c = f.read()

# Fix avatar logic - simplify
old = """  const avatarEmoji = user.avatar || ({student:'🎓', teacher:'👨‍🏫', admin:'👑'}[user.role] || '👤');
  const avatarEl = $('#user-avatar');
  if (avatarEmoji.length === 1 && emojiChar(avatarEmoji)) {
    avatarEl.textContent = avatarEmoji;
    avatarEl.style.fontSize = '26px';
    avatarEl.style.background = '';
  } else {
    avatarEl.textContent = user.name.charAt(0);
  }"""

new = """  const avatarEmoji = user.avatar || ({student:'🎓', teacher:'👨‍🏫', admin:'👑'}[user.role] || '👤');
  const avatarEl = $('#user-avatar');
  avatarEl.textContent = avatarEmoji;
  avatarEl.style.fontSize = '26px';
  avatarEl.style.background = 'linear-gradient(135deg,#4f46e5,#7c3aed)';
  avatarEl.style.color = '#fff';"""

c = c.replace(old, new)

with open(r'src\main\resources\static\app.js', 'w', encoding='utf-8') as f:
    f.write(c)
print('Fixed avatar logic')
