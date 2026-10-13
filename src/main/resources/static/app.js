// ===== 应用主逻辑 v3 (Spring Boot 后端) =====
const $ = (sel) => document.querySelector(sel);
const $$ = (sel) => document.querySelectorAll(sel);
let notifyES = null;  // SSE 长连接（必须声明在 initApp() 调用之前，避免暂时性死区）

const API = '/api';
const TOKEN_KEY = 'zhixue_token';
const USER_KEY = 'zhixue_user';

let state = {
  currentPage: 'dashboard',
  filterCategory: '全部',
  searchKeyword: '',
};

const CATEGORIES = ['全部', '公共必修', '专业必修', '专业选修', '通识选修', '实践课程'];
const ROLES = { student: '学生', teacher: '老师', admin: '管理员' };
const POINTS_PER_ROUND = 60;

// ===== API 工具 =====
function getToken() { return localStorage.getItem(TOKEN_KEY); }
function setToken(t) { localStorage.setItem(TOKEN_KEY, t); }
function getUser() { return JSON.parse(localStorage.getItem(USER_KEY) || 'null'); }
function setUser(u) { localStorage.setItem(USER_KEY, JSON.stringify(u)); }

async function api(method, path, body) {
  const opts = {
    method, headers: { 'Content-Type': 'application/json' },
  };
  const token = getToken();
  if (token) opts.headers['Authorization'] = 'Bearer ' + token;
  if (body) opts.body = JSON.stringify(body);
  const res = await fetch(API + path, opts);
  let data;
  try {
    data = await res.json();
  } catch (e) {
    data = { success: false, message: '服务器错误 (' + res.status + ')' };
  }
  // 会话失效：清除本地登录态并回到登录页
  const msg = data && data.message ? data.message : '';
  if (msg.indexOf('未登录') >= 0 || msg.indexOf('登录已失效') >= 0) {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    toast('登录已过期，请重新登录', 'error');
    setTimeout(() => location.reload(), 1200);
  }
  return data;
}

// ===== 启动（在文件末尾调用 initApp，确保所有常量已初始化）=====

async function initApp() {
  initTheme();
  bindEvents();
  // 启动后始终先进入登录页（即使本地存有登录态，也不自动进入主页）
  showAuth();
}

// ===== 主题（暗黑模式） =====
function initTheme() {
  applyTheme('light');
}
function applyTheme(theme) {
  document.documentElement.setAttribute('data-theme', theme);
  localStorage.setItem('zhixue_theme', theme);
  const themeIcon = theme === 'dark' ? 'sun' : 'moon';
  const t1 = document.getElementById('theme-toggle');
  const t2 = document.getElementById('auth-theme-toggle');
  if (t1) t1.innerHTML = svgIcon(themeIcon);
  if (t2) t2.innerHTML = svgIcon(themeIcon);
}
function toggleTheme() {
  const cur = document.documentElement.getAttribute('data-theme') || 'light';
  applyTheme(cur === 'dark' ? 'light' : 'dark');
}

function showAuth() {
  $('#auth-page').style.display = 'flex';
  $('#app').style.display = 'none';
  disconnectNotify();
  // 重置登录按钮（退出登录后二次登录）
  var btn = document.getElementById('login-btn');
  if (btn) {
    btn.disabled = false;
    btn.classList.remove('btn-globing', 'btn-check-ok', 'btn-shake');
  }
  loadLoginRoundBanner();
}

// ===== 登录页：选课倒计时横幅 =====
let loginCountdownTimer = null;
function loadLoginRoundBanner() {
  const banner = document.getElementById('login-round-banner');
  if (!banner) return;
  if (loginCountdownTimer) { clearInterval(loginCountdownTimer); loginCountdownTimer = null; }
  api('GET', '/system/config').then(r => {
    const c = r && r.success ? (r.data || {}) : {};
    banner.style.display = 'flex';
    const open = !!c.roundOpen;
    const roundBadge = document.getElementById('lrb-round');
    if (roundBadge && c.currentRound) roundBadge.textContent = '第 ' + c.currentRound + ' 轮';
    // 宽松解析 ISO 字符串
    const parseISO = (s) => {
      if (!s) return null;
      try {
        const t = new Date(s);
        return isNaN(t.getTime()) ? null : t;
      } catch (e) { return null; }
    };
    const oa = parseISO(c.roundOpenAt);
    const ca = parseISO(c.roundCloseAt);
    const p = (n) => String(n).padStart(2, '0');
    const els = {
      d: document.getElementById('lrb-d'),
      h: document.getElementById('lrb-h'),
      m: document.getElementById('lrb-m'),
      s: document.getElementById('lrb-s'),
    };
    const status = document.getElementById('lrb-status');
    const title = document.getElementById('lrb-title');
    const fillEl = document.getElementById('lrb-progress-fill');
    const progLabel = document.getElementById('lrb-progress-label');
    // 数字翻牌：仅内容变化时触发翻转动画
    const flipNum = (el, val) => {
      if (!el || el.textContent === val) return;
      el.textContent = val;
      const num = el.getAttribute('data-num');
      if (num === val) return;
      el.setAttribute('data-num', val);
      el.classList.remove('lrb-flip');
      void el.offsetWidth;   // 强制回流以重启动画
      el.classList.add('lrb-flip');
    };
    const setNums = (ms) => {
      if (!els.d || !els.h || !els.m || !els.s) return;
      if (ms <= 0) { flipNum(els.d,'00'); flipNum(els.h,'00'); flipNum(els.m,'00'); flipNum(els.s,'00'); return; }
      const d = Math.floor(ms / 86400000);
      const h = Math.floor((ms % 86400000) / 3600000);
      const m = Math.floor((ms % 3600000) / 60000);
      const s = Math.floor((ms % 60000) / 1000);
      flipNum(els.d, p(d));
      flipNum(els.h, p(h));
      flipNum(els.m, p(m));
      flipNum(els.s, p(s));
    };
    const dim = (label) => {
      setNums(0);
      if (status) status.textContent = label;
      if (title) title.textContent = '';
      banner.classList.add('lrb-idle');
      if (fillEl) { fillEl.style.width = '0%'; }
      if (progLabel) progLabel.textContent = '';
    };
    // 已过去的时间比例（用于进度条）
    const progressOf = (start, end, now) => {
      const total = end.getTime() - start.getTime();
      if (total <= 0) return 100;
      const elapsed = now.getTime() - start.getTime();
      return Math.max(0, Math.min(100, Math.round(elapsed * 100 / total)));
    };
    let refreshPending = false;
    const tick = () => {
      const n = new Date();
      // 倒计时归零 = 刚跨过「自动开放」或「自动结束」节点，重新拉取一次状态
      const hasFuture = (ca && ca.getTime() > n.getTime()) || (oa && oa.getTime() > n.getTime());
      if (hasFuture) {
        refreshPending = false;
      } else if (!refreshPending) {
        refreshPending = true;
        setTimeout(() => loadLoginRoundBanner(), 1500);
      }
      if (ca && ca.getTime() > n.getTime()) {
        setNums(ca.getTime() - n.getTime());
        if (status) status.textContent = open ? '选课进行中' : '本轮已关闭';
        if (title) title.textContent = '距本轮结束';
        banner.classList.remove('lrb-idle');
        if (oa && fillEl && progLabel) {
          const pct = progressOf(oa, ca, n);
          fillEl.style.width = pct + '%';
          progLabel.textContent = '本轮进度 ' + pct + '%';
        } else if (fillEl) {
          fillEl.style.width = '100%';
        }
      } else if (oa && oa.getTime() > n.getTime()) {
        setNums(oa.getTime() - n.getTime());
        if (status) status.textContent = '即将开放';
        if (title) title.textContent = '距选课开放';
        banner.classList.remove('lrb-idle');
        if (fillEl) fillEl.style.width = '0%';
        if (progLabel) progLabel.textContent = '';
      } else {
        dim(open ? '选课进行中' : '本轮已关闭');
      }
    };
    tick();
    loginCountdownTimer = setInterval(tick, 1000);
  }).catch(() => { banner.style.display = 'none'; });
}

function showApp(user) {
  $('#auth-page').style.display = 'none';
  $('#app').style.display = 'flex';
  $('#user-name').textContent = user.name;
  updateTopbarAvatar(user);
  $('#user-meta').textContent = `${ROLES[user.role]} · ${user.no}`;
  renderSidebar(user.role);
  setTimeout(refreshWorkspaceNav, 600);
  renderRoundInfo();
  navigate('dashboard');
  loadNotificationsToNotif();
  // 学生：首次检查候补递补状态；后续通知走 SSE 实时推送（不再轮询）
  if (user.role === 'student') checkWaitlistNotify();
  connectNotify();
}

// ===== 图标（内联 SVG sprite） =====
function svgIcon(name, cls) {
  return '<svg class="icon' + (cls ? ' ' + cls : '') + '" aria-hidden="true"><use href="#i-' + name + '"></use></svg>';
}
function togglePwd(id, btn) {
  const p = document.getElementById(id);
  p.type = p.type === 'password' ? 'text' : 'password';
  btn.innerHTML = svgIcon(p.type === 'password' ? 'eye' : 'eye-off');
}

// ===== 侧边栏 =====
const NAV = {
  student: [
    { page: 'dashboard', icon: 'home', label: '首页' },
    { page: 'browse', icon: 'grid', label: '课程浏览' },
    { page: 'intents', icon: 'cart', label: '我的意向' },
    { page: 'mycourses', icon: 'clipboard', label: '我的课程' },
    { page: 'waitlist', icon: 'hourglass', label: '候补队列' },
    { page: 'schedule', icon: 'calendar', label: '我的课表' },
    { page: 'scores', icon: 'award', label: '成绩查询' },
    { page: 'graduation', icon: 'target', label: '毕业进度' },
    { page: 'advisor', icon: 'graduation-cap', label: '毕业设计导师' },
    { page: 'workspace', icon: 'folder', label: '毕业设计工作台', conditional: true },
  ],
  teacher: [
    { page: 'dashboard', icon: 'home', label: '首页' },
    { page: 'teach-courses', icon: 'book-open', label: '我的课程' },
    { page: 'approve', icon: 'check-circle', label: '选课审批' },
    { page: 'student-list', icon: 'users', label: '选课学生' },
    { page: 'grade-mgr', icon: 'edit', label: '成绩管理' },
    { page: 'reviews', icon: 'message-square', label: '课程评价' },
    { page: 'advise', icon: 'users', label: '毕业设计指导' },
    { page: 'workspace', icon: 'folder', label: '毕业设计工作台', conditional: true },
  ],
  admin: [
    { page: 'dashboard', icon: 'home', label: '首页' },
    { page: 'rounds', icon: 'refresh', label: '选课轮次' },
    { page: 'semesters', icon: 'calendar', label: '学期管理' },
    { page: 'classrooms', icon: 'building', label: '教室管理' },
    { page: 'course-mgr', icon: 'book', label: '课程管理' },
    { page: 'user-mgr', icon: 'users', label: '用户管理' },
    { page: 'announce', icon: 'megaphone', label: '公告管理' },
    { page: 'stats', icon: 'bar-chart', label: '统计报表' },
    { page: 'advise-mgr', icon: 'graduation-cap', label: '导师管理' },
    { page: 'workspace', icon: 'folder', label: '工作台总览', conditional: true },
    { page: 'logs', icon: 'file-text', label: '系统日志' },
  ],
};

function renderSidebar(role) {
  const nav = (NAV[role] || []).filter(n => !n.conditional);
  $('#sidebar-nav').innerHTML = nav.map(n =>
    `<a class="nav-item" data-page="${n.page}"><span class="nav-icon">${svgIcon(n.icon)}</span><span>${n.label}</span></a>`
  ).join('');
  $$('.nav-item').forEach(item => item.addEventListener('click', () => navigate(item.dataset.page)));
}

let roundInfoTimer = null;
async function renderRoundInfo() {
  const r = await api('GET', '/system/config');
  if (!r.success) return;
  const c = r.data;
  $('#round-info').innerHTML = `
    <div>当前选课</div>
    <div class="round-num">第 ${c.currentRound} 轮</div>
    <span class="round-status ${c.roundOpen ? 'round-open' : 'round-closed'}">${c.roundOpen ? '进行中' : '已关闭'}</span>
    <div class="round-left" id="round-left"></div>
  `;
  if (roundInfoTimer) { clearInterval(roundInfoTimer); roundInfoTimer = null; }
  const closeAt = c.roundCloseAt ? new Date(c.roundCloseAt) : null;
  const openAt = c.roundOpenAt ? new Date(c.roundOpenAt) : null;
  const now0 = Date.now();
  let target = null, label = '';
  if (closeAt && closeAt.getTime() > now0) { target = closeAt; label = '距本轮结束'; }
  else if (openAt && openAt.getTime() > now0) { target = openAt; label = '距开放'; }
  if (!target) {
    const el = document.getElementById('round-left');
    if (el) el.textContent = c.roundOpen ? '未设结束时间' : '';
    return;
  }
  const p = n => String(n).padStart(2, '0');
  let didRefresh = false;
  const tick = () => {
    const el = document.getElementById('round-left');
    if (!el) { clearInterval(roundInfoTimer); roundInfoTimer = null; return; }
    const ms = target.getTime() - Date.now();
    if (ms <= 0) {
      el.textContent = '';
      if (!didRefresh) { didRefresh = true; setTimeout(() => renderRoundInfo(), 1500); }
      return;
    }
    const d = Math.floor(ms / 86400000);
    const h = Math.floor((ms % 86400000) / 3600000);
    const m = Math.floor((ms % 3600000) / 60000);
    const s = Math.floor((ms % 60000) / 1000);
    el.textContent = label + ' ' + (d > 0 ? d + '天 ' : '') + p(h) + ':' + p(m) + ':' + p(s);
  };
  tick();
  roundInfoTimer = setInterval(tick, 1000);
}

// ===== 事件绑定 =====
function bindEvents() {
  $$('.auth-tab').forEach(tab => {
    tab.addEventListener('click', () => {
      $$('.auth-tab').forEach(t => t.classList.remove('active'));
      tab.classList.add('active');
      const target = tab.dataset.tab;
      $('#login-form').style.display = target === 'login' ? 'block' : 'none';
      $('#register-form').style.display = target === 'register' ? 'block' : 'none';
    });
  });
  // 载入记住的密码
  (function() {
    try {
      const c = JSON.parse(localStorage.getItem('zhixue_remember') || 'null');
      if (c) {
        document.getElementById('login-no').value = c.no || '';
        document.getElementById('login-password').value = c.password || '';
        document.getElementById('remember-me').checked = true;
      } else {
        document.getElementById('remember-me').checked = true;
      }
    } catch(e) {
      document.getElementById('remember-me').checked = true;
    }
  })();

  $('#login-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const loginBtn = document.getElementById('login-btn');
    // 验证码校验（前端 canvas 验证码）
    const capEl = document.getElementById('login-captcha');
    if (capEl && window.__zxCaptcha) {
      if ((capEl.value || '').trim().toUpperCase() !== window.__zxCaptcha.get()) {
        toast('验证码错误，请重新输入', 'error');
        capEl.value = '';
        window.__zxCaptcha.refresh();
        return;
      }
    }
    const no = $('#login-no').value.trim();
    const pwd = $('#login-password').value;
    loginBtn.disabled = true;
    loginBtn.classList.remove('btn-shake');
    loginBtn.classList.add('btn-globing');
    let r;
    try {
      r = await api('POST', '/auth/login', { no, password: pwd });
    } catch (err) {
      r = { success: false, message: '网络异常，请稍后再试' };
    }
    if (r.success) {
      loginBtn.classList.add('btn-check-ok');
      const remember = $('#remember-me').checked;
      if (remember) {
        localStorage.setItem('zhixue_remember', JSON.stringify({ no, password: pwd }));
      } else {
        localStorage.removeItem('zhixue_remember');
      }
      setToken(r.data.token);
      setUser(r.data.user);
      toast('登录成功', 'success');
      // 等对勾动画播完再切换页面
      setTimeout(() => showApp(r.data.user), 800);
    } else {
      loginBtn.classList.remove('btn-globing');
      loginBtn.classList.add('btn-shake');
      setTimeout(() => loginBtn.classList.remove('btn-shake'), 500);
      loginBtn.disabled = false;
      toast(r.message || '登录失败', 'error');
    }
  });

  // 快捷登录按钮 - 自动填充账号密码
  const quickAccounts = {
    student: { no: '2023010101', password: '123456' },
    teacher: { no: 'T001', password: '123456' },
admin:   { no: 'admin',   password: 'admin' },
  };
  document.querySelectorAll('.quick-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      const acc = quickAccounts[btn.dataset.role];
      $('#login-no').value = acc.no;
      $('#login-password').value = acc.password;
      toast('已填充' + btn.textContent + '账号，请点击登录', 'success');
    });
  });

  $('#register-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const data = {
      name: $('#reg-name').value.trim(),
      no: $('#reg-no').value.trim(),
      password: $('#reg-password').value,
      college: $('#reg-college').value,
      grade: $('#reg-grade').value,
      major: $('#reg-major').value.trim(),
    };
    const r = await api('POST', '/auth/register', data);
    if (r.success) {
      toast('注册成功，请登录', 'success');
      $('.auth-tab[data-tab="login"]').click();
      $('#login-no').value = data.no;
    } else toast(r.message, 'error');
  });

  $('#logout-btn').addEventListener('click', () => {
    api('POST', '/auth/logout').catch(() => {});
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    toast('已退出登录', 'success');
    showAuth();
  });

  $('#reset-btn').addEventListener('click', () => {
    showConfirm('重置演示数据', '重置数据需要重启服务端，确定继续？', 'danger', () => { toast('请联系管理员重置数据库', 'success'); });
  });

  // 主题切换（顶栏）
  const themeBtn = document.getElementById('theme-toggle');
  if (themeBtn) themeBtn.addEventListener('click', toggleTheme);
  const authThemeBtn = document.getElementById('auth-theme-toggle');
  if (authThemeBtn) authThemeBtn.addEventListener('click', toggleTheme);

  // 顶栏退出按钮
  const lb2 = document.getElementById('logout-btn2');
  if (lb2) lb2.addEventListener('click', () => {
    api('POST', '/auth/logout').catch(() => {});
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    toast('已退出登录', 'success');
    showAuth();
  });

  // 侧边栏折叠
  const sidebarToggle = document.getElementById('sidebar-toggle');
  if (sidebarToggle) sidebarToggle.addEventListener('click', () => {
    document.querySelector('.sidebar').classList.toggle('collapsed');
  });

  // 通知面板
  const notifBtn = document.getElementById('notif-btn');
  const notifPanel = document.getElementById('notif-panel');
  if (notifBtn) notifBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    notifPanel.classList.toggle('show');
    if (notifPanel.classList.contains('show')) loadNotificationsToNotif();
    const ud = document.getElementById('user-dropdown');
    if (ud) ud.classList.remove('show');
  });
  const notifClear = document.getElementById('notif-clear');
  if (notifClear) notifClear.addEventListener('click', () => markAllNotifRead());

  // 用户下拉菜单
  const userMenuBtn = document.getElementById('user-menu-btn');
  const userDropdown = document.getElementById('user-dropdown');
  if (userMenuBtn) userMenuBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    userDropdown.classList.toggle('show');
    if (notifPanel) notifPanel.classList.remove('show');
  });

  // 点击外部关闭下拉
  document.addEventListener('click', () => {
    if (userDropdown) userDropdown.classList.remove('show');
    if (notifPanel) notifPanel.classList.remove('show');
  });

  // 回到顶部
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
  });
}

// ===== 路由 =====
async function navigate(page) {
  state.currentPage = page;
  $$('.nav-item').forEach(n => n.classList.toggle('active', n.dataset.page === page));
  const titles = {
    dashboard: '首页', browse: '课程浏览', mycourses: '我的课程',
    schedule: '我的课表', scores: '成绩查询', graduation: '毕业进度',
    'teach-courses': '我的课程', approve: '选课审批',
    'student-list': '选课学生', 'grade-mgr': '成绩管理',
    rounds: '选课轮次管理', semesters: '学期管理', classrooms: '教室管理',
    'course-mgr': '课程管理', 'user-mgr': '用户管理', logs: '系统日志',
    intents: '我的意向', reviews: '课程评价', announce: '公告管理',
    waitlist: '候补队列', stats: '统计报表',
    advisor: '毕业设计导师', advise: '毕业设计指导', 'advise-mgr': '导师管理',
    workspace: '毕业设计工作台',
  };
  $('#page-title').textContent = titles[page] || '首页';
  $("#content").innerHTML = '<div class="stats-grid"><div class="skeleton skeleton-card"></div><div class="skeleton skeleton-card"></div><div class="skeleton skeleton-card"></div><div class="skeleton skeleton-card"></div></div>';

  const user = getUser();
  const role = user.role;

  if (role === 'student') {
    if (page === 'dashboard') await renderStudentDashboard();
    else if (page === 'browse') renderBrowse();
    else if (page === 'mycourses') await renderMyCourses();
    else if (page === 'schedule') await renderSchedule();
    else if (page === 'scores') await renderScores();
    else if (page === 'graduation') await renderGraduation();
    else if (page === 'advisor') await renderAdvisor();
    else if (page === 'workspace') await renderWorkspace();
    else if (page === 'intents') await renderIntents();
    else if (page === 'waitlist') await renderWaitlist();
  } else if (role === 'teacher') {
    if (page === 'dashboard') await renderTeacherDashboard();
    else if (page === 'teach-courses') await renderTeachCourses();
    else if (page === 'approve') await renderApproveList();
    else if (page === 'student-list') await renderStudentList();
    else if (page === 'grade-mgr') await renderGradeMgr();
    else if (page === 'reviews') await renderTeacherReviews();
    else if (page === 'advise') await renderAdvise();
    else if (page === 'workspace') await renderWorkspace();
  } else if (role === 'admin') {
    if (page === 'dashboard') await renderAdminDashboard();
    else if (page === 'rounds') await renderRoundMgr();
    else if (page === 'semesters') await renderSemesterMgr();
    else if (page === 'classrooms') await renderClassroomMgr();
    else if (page === 'course-mgr') await renderCourseMgr();
    else if (page === 'user-mgr') await renderUserMgr();
    else if (page === 'announce') await renderAnnounceMgr();
    else if (page === 'stats') await renderStats();
    else if (page === 'advise-mgr') await renderAdviseMgr();
    else if (page === 'workspace') await renderWorkspace();
    else if (page === 'logs') await renderSystemLogs();
  }
  await renderRoundInfo();
  setTimeout(() => {
    document.querySelectorAll('.stat-value').forEach(el => {
      const num = parseInt(el.textContent);
      if (!isNaN(num)) animateCounter(el, num);
    });
  }, 80);
}

// ===== Toast =====
let toastTimer;
function toast(msg, type = '') {
  const el = $('#toast');
  el.textContent = msg;
  el.className = 'toast show ' + type;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { el.className = 'toast ' + type; }, 2200);
}

// ===== 自定义确认弹窗 =====
function showConfirm(title, message, type, onConfirm) {
  const mask = document.getElementById('confirm-mask');
  const confirmIcon = type === 'danger' ? 'alert-triangle' : 'help-circle';
  mask.innerHTML = '<div class="confirm-mask" id="confirm-inner">' +
    '<div class="confirm-box">' +
    '<div class="confirm-icon ' + type + '">' + svgIcon(confirmIcon) + '</div>' +
    '<div class="confirm-title">' + title + '</div>' +
    '<div class="confirm-msg">' + message + '</div>' +
    '<div class="confirm-actions">' +
    '<button class="btn btn-ghost" id="confirm-cancel">取消</button>' +
    '<button class="btn ' + (type === 'danger' ? 'btn-danger' : 'btn-primary') + '" id="confirm-ok">确定</button>' +
    '</div></div></div>';
  mask.style.display = 'block';
  const inner = document.getElementById('confirm-inner');
  document.getElementById('confirm-cancel').onclick = () => { mask.style.display = 'none'; mask.innerHTML = ''; };
  document.getElementById('confirm-ok').onclick = () => {
    mask.style.display = 'none'; mask.innerHTML = '';
    if (onConfirm) onConfirm();
  };
  inner.onclick = (e) => { if (e.target === inner) { mask.style.display = 'none'; mask.innerHTML = ''; } };
}

// Promise 版本确认框，可直接 await
function confirmAsync(title, message, type) {
  return new Promise((resolve) => {
    showConfirm(title, message, type, () => resolve(true));
    setTimeout(() => {
      const mask = document.getElementById('confirm-mask');
      const inner = document.getElementById('confirm-inner');
      if (!inner) return;
      const cancelBtn = document.getElementById('confirm-cancel');
      if (cancelBtn) cancelBtn.onclick = () => { mask.style.display = 'none'; mask.innerHTML = ''; resolve(false); };
      inner.onclick = (e) => { if (e.target === inner) { mask.style.display = 'none'; mask.innerHTML = ''; resolve(false); } };
    }, 0);
  });
}

// ===== 数字滚动动画 =====
function animateCounter(el, target, duration = 800) {
  const start = 0;
  const startTime = performance.now();
  function update(now) {
    const elapsed = now - startTime;
    const progress = Math.min(elapsed / duration, 1);
    const eased = 1 - Math.pow(1 - progress, 3);
    el.textContent = Math.round(start + (target - start) * eased);
    if (progress < 1) requestAnimationFrame(update);
  }
  requestAnimationFrame(update);
}

// ============================================================
// 首页呼吸感欢迎横幅（三端通用）
// ============================================================
function homeHeroHtml(name, roleLabel, statValue, statLabel, icon) {
  return `<div class="home-hero">
    <div class="home-hero-glow"></div>
    <div class="home-hero-glow2"></div>
    <div class="home-hero-main">
      <div class="home-hero-hello">欢迎回来，<b>${name || '同学'}</b></div>
      <div class="home-hero-sub">${roleLabel} · 智学选课平台</div>
      <div class="home-hero-tip">${roleLabel === '同学' ? '愿你今天选到心仪的好课 ✨' : '愿你今天工作顺利 ✨'}</div>
    </div>
    <div class="home-hero-stat">
      <div class="home-hero-stat-icon">${svgIcon(icon)}</div>
      <div class="home-hero-stat-value">${statValue}</div>
      <div class="home-hero-stat-label">${statLabel}</div>
    </div>
  </div>`;
}

// ============================================================
// 数据可视化小组件：走势线 / 徽标 / 环形图 / 时间线
// ============================================================

function sparkSvg(values, color) {
  var v = (values && values.length >= 2) ? values : [3,5,4,6,5,7];
  var w = 120, h = 36, n = v.length;
  var max = Math.max.apply(null, v), min = Math.min.apply(null, v);
  var range = (max - min) || 1;
  var pts = v.map(function(x, i) {
    return [ (i/(n-1))*w, h - 4 - ((x - min)/range)*(h - 12) ];
  });
  var line = pts.map(function(p){ return p[0].toFixed(1)+','+p[1].toFixed(1); }).join(' ');
  var area = line + ' ' + w + ',' + h + ' 0,' + h;
  return '<svg class="stat-spark" viewBox="0 0 '+w+' '+h+'" preserveAspectRatio="none">' +
    '<polygon points="'+area+'" fill="'+color+'" opacity="0.14"/>' +
    '<polyline points="'+line+'" fill="none" stroke="'+color+'" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>' +
    '<circle cx="'+pts[n-1][0].toFixed(1)+'" cy="'+pts[n-1][1].toFixed(1)+'" r="2.8" fill="'+color+'"/></svg>';
}

// 带 sparkline + 涨跌徽标的统计卡（opts: icon,color,value,label,trend,deltaText,deltaDir）
function statCardPro(opts) {
  var colorMap = { blue:'#667eea', green:'#10b981', red:'#ef4444', amber:'#f59e0b', purple:'#8b5cf6' };
  var c = colorMap[opts.color] || colorMap.blue;
  return '<div class="stat-card stat-pro">' +
    '<div class="stat-pro-top"><div class="stat-icon '+opts.color+'">'+svgIcon(opts.icon)+'</div>' +
    (opts.deltaText ? '<span class="stat-delta '+(opts.deltaDir||'up')+'">'+opts.deltaText+'</span>' : '') + '</div>' +
    '<div class="stat-value">'+opts.value+'</div>' +
    '<div class="stat-label">'+opts.label+'</div>' +
    (opts.trend ? sparkSvg(opts.trend, c) : '') +
    '</div>';
}

// 每小时操作量走势（取最近 buckets 小时）
function hourlyTrend(logs, buckets) {
  var arr = new Array(buckets).fill(0);
  var now = Date.now(), HOUR = 3600000;
  (logs || []).forEach(function(l) {
    var t = new Date(String(l.createdAt).replace(' ', 'T')).getTime();
    if (isNaN(t)) return;
    var idx = buckets - 1 - Math.floor((now - t)/HOUR);
    if (idx >= 0 && idx < buckets) arr[idx]++;
  });
  return arr;
}

function timeAgo(s) {
  var t = new Date(String(s).replace(' ', 'T')).getTime();
  if (isNaN(t)) return String(s);
  var m = Math.floor((Date.now()-t)/60000);
  if (m < 1) return '刚刚';
  if (m < 60) return m + ' 分钟前';
  var h = Math.floor(m/60);
  if (h < 24) return h + ' 小时前';
  return Math.floor(h/24) + ' 天前';
}

// conic-gradient 环形图（entries: [[name,count],...]）
function donutHtml(entries, centerValue, centerLabel) {
  var colors = ['#667eea','#f59e0b','#10b981','#ef4444','#8b5cf6','#ec4899'];
  var total = entries.reduce(function(s,kv){ return s + kv[1]; }, 0) || 1;
  // 只保留前 5 类，其余合并为「其他」（参考 Traffic Source 卡片的精简图例）
  var sorted = entries.slice().sort(function(a,b){ return b[1] - a[1]; });
  var top = sorted.slice(0, 5);
  var restSum = sorted.slice(5).reduce(function(s,kv){ return s + kv[1]; }, 0);
  if (restSum > 0) top.push(['其他', restSum]);
  var acc = 0, segs = [], glowColor = '#667eea', maxShare = 0;
  top.forEach(function(kv, i) {
    var from = acc/total*360; acc += kv[1];
    var to = acc/total*360;
    if (kv[1]/total > maxShare) { maxShare = kv[1]/total; glowColor = colors[i % colors.length]; }
    segs.push(colors[i % colors.length] + ' ' + from.toFixed(1) + 'deg ' + to.toFixed(1) + 'deg');
  });
  return '<div class="donut-wrap" style="--donut-glow:' + glowColor + '">' +
    '<div class="donut" style="background:conic-gradient(' + segs.join(',') + ')">' +
    '<div class="donut-hole"><b>' + centerValue + '</b><span>' + centerLabel + '</span></div></div>' +
    '<div class="donut-legend">' + top.map(function(kv, i) {
      return '<div class="pie-item"><span class="pie-dot" style="background:' + colors[i % colors.length] + ';box-shadow:0 0 6px ' + colors[i % colors.length] + '99"></span>' +
        '<span class="pie-label">' + kv[0] + '</span>' +
        '<span class="pie-pct">' + (kv[1]/total*100).toFixed(1) + '%</span>' +
        '<span class="pie-cnt">' + kv[1] + '</span></div>';
    }).join('') + '</div></div>';
}

// ============================================================
// 学生端
// ============================================================

async function renderStudentDashboard() {
  const user = getUser();
  const [pointsR, mineR] = await Promise.all([
    api('GET', '/enrollments/points'),
    api('GET', '/enrollments/mine'),
  ]);
  const remaining = pointsR.success ? pointsR.data.remaining : 60;
  const approved = mineR.success ? mineR.data.filter(e => e.status === 'approved') : [];
  const pending = mineR.success ? mineR.data.filter(e => e.status === 'pending') : [];
  const totalCredits = approved.reduce((s, e) => s + (e.credit || 0), 0);

  $('#content').innerHTML = `
    ${homeHeroHtml(user.name, '同学', remaining, '本轮剩余积分', 'coins')}
    <div class="stats-grid">
      <div class="stat-card"><div class="stat-icon amber">${svgIcon('coins')}</div><div><div class="stat-value">${remaining}</div><div class="stat-label">本轮剩余积分 / ${POINTS_PER_ROUND}</div></div></div>
      <div class="stat-card"><div class="stat-icon blue">${svgIcon('book')}</div><div><div class="stat-value">${approved.length}</div><div class="stat-label">已录取课程</div></div></div>
      <div class="stat-card"><div class="stat-icon amber">${svgIcon('hourglass')}</div><div><div class="stat-value">${pending.length}</div><div class="stat-label">待录取课程</div></div></div>
      <div class="stat-card"><div class="stat-icon green">${svgIcon('star')}</div><div><div class="stat-value">${totalCredits}</div><div class="stat-label">已获学分</div></div></div>
    </div>
    <div id="announce-slot"></div>
    <div class="section"><div class="section-header"><div class="section-title">我已录取的课程</div><a class="section-link" onclick="navigate('mycourses')">查看全部 →</a></div>
      ${approved.length ? approvedListHtml(approved, true) : emptyHtml('inbox', '还没有被录取的课程')}
    </div>
    <div class="section"><div class="section-header"><div class="section-title">推荐课程</div><a class="section-link" onclick="navigate('browse')">浏览全部 →</a></div>
          <div id="rec-courses"><div class="skeleton skeleton-card"></div><div class="skeleton skeleton-card"></div><div class="skeleton skeleton-card"></div></div>
    </div>
  `;
  bindCourseActions();
  await loadAnnouncementCards('#announce-slot');
  // 加载推荐课程
  const r = await api('GET', '/courses?category=全部');
  if (r.success) {
    const myIds = new Set(mineR.data.map(e => e.courseId));
    const recs = r.data.filter(c => !myIds.has(c.id)).slice(0, 4);
    $('#rec-courses').innerHTML = recs.length ? courseGridHtml(recs, false) : emptyHtml('inbox', '暂无');
    bindCourseActions();
  }
}

function approvedListHtml(list, showDrop) {
  return `<div class="course-grid">${list.map(e => `
    <div class="course-card">
      <span class="course-cat cat-${e.category}">${e.category}</span>
      <div class="course-name">${e.courseName}</div>
      <div class="course-teacher">${svgIcon('user')} ${e.teacherName} · ${e.credit} 学分</div>
      <div class="course-footer">
        <span class="stu-points">${e.points} 分</span>
        ${(e.category || '').indexOf('必修') >= 0
          ? '<span style="font-size:12px;color:var(--text-light)">必修 · 自动安排</span>'
          : (showDrop ? `<button class="btn btn-danger btn-sm" data-action="drop" data-id="${e.courseId}">退课</button>` : '')}
      </div>
    </div>`).join('')}</div>`;
}

// ===== 课程浏览 =====
function renderBrowse() {
  $('#content').innerHTML = `
    <div class="filter-bar">
      <div class="search-box"><input type="text" id="search-input" placeholder="搜索课程名或教师..." value="${state.searchKeyword}"></div>
      <div class="category-tabs">
        ${CATEGORIES.filter(c => c.indexOf('必修') < 0).map(c => `<span class="cat-tab ${state.filterCategory === c ? 'active' : ''}" data-cat="${c}">${c}</span>`).join('')}
      </div>
    </div>
        <div id="course-list"><div class="skeleton skeleton-card"></div><div class="skeleton skeleton-card"></div><div class="skeleton skeleton-card"></div><div class="skeleton skeleton-card"></div></div>
  `;
  loadCourseList();
  $('#search-input').addEventListener('input', (e) => { state.searchKeyword = e.target.value.trim(); loadCourseList(); });
  $$('.cat-tab').forEach(tab => tab.addEventListener('click', () => {
    state.filterCategory = tab.dataset.cat;
    $$('.cat-tab').forEach(t => t.classList.toggle('active', t === tab));
    loadCourseList();
  }));
}

async function loadCourseList() {
  const r = await api('GET', `/courses?category=${encodeURIComponent(state.filterCategory)}&keyword=${encodeURIComponent(state.searchKeyword)}`);
  if (r.success) {
    const selectable = (r.data || []).filter(c => (c.category || '').indexOf('必修') < 0);
  $('#course-list').innerHTML = selectable.length ? courseGridHtml(selectable, false) : emptyHtml('search', '没有可选修的课程');
    bindCourseActions();
  }
}

function courseGridHtml(courses, showDrop) {
  return `<div class="course-grid">${courses.map(c => {
    const stat = c.stat || { total: 0, approved: 0 };
    const over = c.oversubscribed;
    const isStudent = (getUser() || {}).role === 'student';
    let btnHtml = '';
    const st = c.myStatus;
    const wst = c.myWaitStatus;
    const isReqCourse = (c.category || '').indexOf('必修') >= 0;
    if (isReqCourse) btnHtml = `<button class="btn btn-ghost btn-sm" disabled>必修·自动安排</button>`;
    else if (st === 'approved') {
      btnHtml = showDrop ? `<button class="btn btn-danger btn-sm" data-action="drop" data-id="${c.id}">退课</button>` : `<button class="btn btn-ghost btn-sm" disabled>已录取</button>`;
    } else if (st === 'pending') btnHtml = `<button class="btn btn-ghost btn-sm" disabled>${svgIcon('hourglass')} 待录取</button>`;
    else if (st === 'rejected') btnHtml = `<button class="btn btn-outline btn-sm" data-action="waitlist" data-id="${c.id}">未录取·加入候补</button>`;
    else if (isStudent && wst === 'confirming') btnHtml = `<button class="btn btn-success btn-sm" data-action="wait-confirm" data-id="${c.id}" data-wid="${c.myWaitId}">${svgIcon('party')} 待确认录取</button>`;
    else if (isStudent && wst === 'waiting') btnHtml = `<button class="btn btn-ghost btn-sm" disabled>候补中·第${c.myWaitPosition}位</button>`;
    else btnHtml = `<button class="btn btn-primary btn-sm" data-action="enroll" data-id="${c.id}">投入积分选课</button>`;
    const progress = Math.round((stat.total / c.capacity) * 100);
    return `
    <div class="course-card${isReqCourse ? ' course-req' : ''}">
      <span class="course-cat cat-${c.category}">${c.category}</span>
      ${isStudent && !isReqCourse ? `<button class="intent-btn${c.myIntent ? ' active' : ''}" data-intent="${c.id}" title="${c.myIntent ? '从意向单移除' : '加入意向单'}">${svgIcon('bookmark')}</button>` : ''}
      <div class="course-name">${c.name}${isStudent && over && !isReqCourse ? ' <span class="tag-full">已超额</span>' : ''}</div>
      <div class="course-teacher">${svgIcon('user')} ${c.teacherName} · ${c.credit} 学分</div>
      <div class="course-meta"><span>${svgIcon('clock')} ${(c.time||'').split('/')[0].trim()}</span><span>${svgIcon('map-pin')} ${c.location}</span>${c.rating ? `<span class="course-rating">${svgIcon('star')} ${c.rating}</span>` : ''}</div>
      <div class="course-desc">${c.intro}</div>
      <div class="course-footer">
        <div style="flex:1;display:flex;align-items:center;gap:8px;">
          <div class="progress-bar"><div class="progress-fill" style="width:${Math.min(progress,100)}%"></div></div>
          <span class="enroll-count">${stat.total}/${c.capacity}${over ? ' ' + svgIcon('alert-triangle') : ''}</span>
        </div>
        ${btnHtml}
      </div>
    </div>`;
  }).join('')}</div>`;
}

function bindCourseActions() {
  $$('[data-action="enroll"]').forEach(btn => btn.addEventListener('click', () => openEnrollModal(btn.dataset.id)));
  $$('[data-action="drop"]').forEach(btn => btn.addEventListener('click', async () => {
    if (!await confirmAsync('退选课程', '确定退选该课程？积分将退还。', 'danger')) return;
    const r = await api('POST', '/enrollments/drop', { courseId: parseInt(btn.dataset.id) });
    toast(r.message, r.success ? 'success' : 'error');
    if (r.success) navigate(state.currentPage);
  }));
  // 满员课程：加入候补
  $$('[data-action="waitlist"]').forEach(btn => btn.addEventListener('click', async () => {
    if (!await confirmAsync('加入候补', '该课程已满员。加入候补后，有人退课将按顺序自动递补，并通知你限时确认。确定加入？', 'info')) return;
    const r = await api('POST', '/waitlist/join', { courseId: parseInt(btn.dataset.id) });
    toast(r.message, r.success ? 'success' : 'error');
    if (r.success) navigate(state.currentPage);
  }));
  // 递补待确认 / 退出候补
  $$('[data-action="wait-confirm"]').forEach(btn => btn.addEventListener('click', () => openWaitConfirmModal(btn.dataset.id, btn.dataset.wid)));
  $$('[data-action="wait-cancel"]').forEach(btn => btn.addEventListener('click', async () => {
    if (!await confirmAsync('退出候补', '确定退出该课程的候补队列？', 'danger')) return;
    const r = await api('POST', '/waitlist/cancel', { courseId: parseInt(btn.dataset.id) });
    toast(r.message, r.success ? 'success' : 'error');
    if (r.success) navigate(state.currentPage);
  }));
  // 意向单：加入 / 移出
  $$('[data-intent]').forEach(btn => btn.addEventListener('click', async (e) => {
    e.stopPropagation();
    const on = btn.classList.contains('active');
    const r = await api('POST', on ? '/intents/remove' : '/intents/add', { courseId: parseInt(btn.dataset.intent), points: 10 });
    toast(r.message, r.success ? 'success' : 'error');
    if (r.success) {
      btn.classList.toggle('active', !on);
      btn.title = on ? '加入意向单' : '从意向单移除';
    }
  }));
  // 课程评价
  $$('[data-review]').forEach(btn => btn.addEventListener('click', () => openReviewModal(btn.dataset.review, btn.dataset.name)));
}

async function openEnrollModal(courseId) {
  const [courseR, pointsR] = await Promise.all([
    api('GET', `/courses?category=全部`),
    api('GET', '/enrollments/points'),
  ]);
  const course = courseR.data.find(c => c.id == courseId);
  const remaining = pointsR.data.remaining;
  const over = course.oversubscribed;

  const modal = document.createElement('div');
  modal.className = 'modal-mask';
  modal.innerHTML = `
    <div class="modal">
      <h3>报名「${course.name}」</h3>
      <div class="modal-sub">授课教师：${course.teacherName} · 容量 ${course.capacity} 人</div>
      <div class="modal-points-info">
        本轮剩余积分：<b>${remaining} / ${POINTS_PER_ROUND}</b>
        ${over ? '<br><span style="color:var(--danger)">' + svgIcon('alert-triangle') + ' 该课程已超额，本次靠积分竞争：本轮结束后按投入积分从高到低录取，积分越高越有利</span>' : '<br><span style="color:var(--success)">' + svgIcon('check-circle') + ' 报名后积分会被冻结，本轮结束后统一录取；未录取将自动退还积分</span>'}
      </div>
      <div style="font-size:13px;color:var(--text-light);text-align:center">投入积分（1-${remaining}）</div>
      <div class="points-display" id="pts-display">${Math.min(10, remaining)}</div>
      <input type="range" class="points-slider" id="pts-slider" min="1" max="${remaining}" value="${Math.min(10, remaining)}">
      <div class="modal-actions">
        <button class="btn btn-ghost" id="modal-cancel">取消</button>
        <button class="btn btn-primary" id="modal-confirm">确认报名</button>
      </div>
    </div>`;
  document.body.appendChild(modal);
  const slider = $('#pts-slider'), display = $('#pts-display');
  slider.addEventListener('input', () => display.textContent = slider.value);
  $('#modal-cancel').onclick = () => modal.remove();
  modal.onclick = (e) => { if (e.target === modal) modal.remove(); };
  $('#modal-confirm').onclick = async () => {
    const r = await api('POST', '/enrollments/enroll', { courseId: parseInt(courseId), points: parseInt(slider.value) });
    toast(r.message, r.success ? 'success' : 'error');
    modal.remove();
    if (r.success) navigate(state.currentPage);
  };
}

// ===== 我的课程 =====
async function renderMyCourses() {
  const [r, pointsR, wlR] = await Promise.all([api('GET', '/enrollments/mine'), api('GET', '/enrollments/points'), api('GET', '/waitlist/mine')]);
  const list = r.success ? r.data : [];
  const waitlist = wlR.success ? wlR.data : [];
  const approved = list.filter(e => e.status === 'approved');
  const pending = list.filter(e => e.status === 'pending');
  const row = (e) => {
    const sm = { approved: ['已录取', 'status-approved'], pending: ['已报名·待录取', 'status-pending'], rejected: ['未录取', 'status-rejected'] };
    const [stxt, scls] = sm[e.status] || ['', ''];
    const isReq = (e.category || '').indexOf('必修') >= 0;
    return `<tr>
      <td><b>${e.courseName || '（课程已删除）'}</b></td>
      <td>${e.category ? '<span class="course-cat cat-' + e.category + '">' + e.category + '</span>' : '-'}</td>
      <td>${e.teacherName || '-'}</td>
      <td>${e.credit}</td>
      <td><span class="stu-points">${e.points} 分</span></td>
      <td><span class="status-tag ${scls}">${stxt}</span></td>
      <td>${isReq ? '<span style="font-size:12px;color:var(--text-light)">必修 · 自动安排</span>'
        : e.status === 'approved' ? `<button class="btn btn-ghost btn-sm" data-review="${e.courseId}" data-name="${e.courseName}">评价</button> <button class="btn btn-danger btn-sm" data-action="drop" data-id="${e.courseId}">退课</button>`
        : e.status === 'pending' ? `<button class="btn btn-outline btn-sm" data-action="drop" data-id="${e.courseId}">撤销报名</button>`
        : e.status === 'rejected' ? `<button class="btn btn-outline btn-sm" data-action="waitlist" data-id="${e.courseId}">加入候补</button>`
        : '-'}</td>
    </tr>`;
  };
  const wlRow = (w) => {
    if (w.status === 'confirming') {
      return `<tr>
        <td><b>${w.courseName}</b></td>
        <td>${w.teacherName}</td>
        <td><span class="status-tag status-pending">${svgIcon('party')} 待确认录取</span></td>
        <td><span class="wl-countdown" data-deadline="${w.deadline}">--:--</span></td>
        <td><button class="btn btn-success btn-sm" data-action="wait-confirm" data-id="${w.courseId}" data-wid="${w.waitlistId}">确认录取</button></td>
      </tr>`;
    }
    return `<tr>
      <td><b>${w.courseName}</b></td>
      <td>${w.teacherName}</td>
      <td><span class="status-tag status-pending">候补中</span></td>
      <td>第 ${w.position} / ${w.queueTotal} 位${w.estimatedWait ? '<div style="font-size:12px;color:var(--text-light)">预计递补：' + fmtEstWait(w.estimatedWait) + '</div>' : ''}</td>
      <td><button class="btn btn-danger btn-sm" data-action="wait-cancel" data-id="${w.courseId}">退出候补</button></td>
    </tr>`;
  };
  $('#content').innerHTML = `
    <div class="stats-grid">
      <div class="stat-card"><div class="stat-icon green">${svgIcon('check-circle')}</div><div><div class="stat-value">${approved.length}</div><div class="stat-label">已录取</div></div></div>
      <div class="stat-card"><div class="stat-icon amber">${svgIcon('hourglass')}</div><div><div class="stat-value">${pending.length}</div><div class="stat-label">待录取</div></div></div>
      <div class="stat-card"><div class="stat-icon purple">${svgIcon('coins')}</div><div><div class="stat-value">${pointsR.data.remaining}</div><div class="stat-label">剩余积分</div></div></div>
    </div>
    ${list.length ? `<div class="data-table"><table><thead><tr><th>课程</th><th>类别</th><th>教师</th><th>学分</th><th>投入积分</th><th>状态</th><th>操作</th></tr></thead><tbody>${list.map(row).join('')}</tbody></table></div>` : emptyHtml('inbox', '本轮还没有报名任何课程')}
    ${waitlist.length ? `<div class="section" style="margin-top:20px"><div class="section-header"><div class="section-title">${svgIcon('hourglass')} 我的候补队列（${waitlist.length}）</div></div>
      <div class="data-table"><table><thead><tr><th>课程</th><th>教师</th><th>状态</th><th>排队位置</th><th>操作</th></tr></thead><tbody>${waitlist.map(wlRow).join('')}</tbody></table></div></div>` : ''}
  `;
  bindCourseActions();
  startWaitlistCountdowns();
}

// ===== 课表 =====
const DAYS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];
const TIMES = [
  { label: '08:00-09:40', start: '08:00' },
  { label: '10:00-11:40', start: '10:00' },
  { label: '14:00-15:40', start: '14:00' },
  { label: '16:00-17:40', start: '16:00' },
  { label: '19:00-20:40', start: '19:00' },
];

async function renderSchedule() {
  const [r, cfR] = await Promise.all([api('GET', '/enrollments/approved'), api('GET', '/schedule/conflicts')]);
  const courses = r.success ? r.data : [];
  const conflicts = cfR.success ? (cfR.data.conflicts || []) : [];
  const sys = await api('GET', '/system/config');
  await loadCurrentSemester();
  const extra = window._schedExtra || [];
  const allCourses = courses.concat(extra);

  if (!allCourses.length) { document.getElementById('content').innerHTML = emptyHtml('calendar', '你还没有被录取的课程'); return; }

  function dayIdxOf(dayName) {
    const m = { '一':0,'二':1,'三':2,'四':3,'五':4,'六':5,'日':6 };
    const n = dayName.replace(/周|星期/g,'');
    return m[n] != null ? m[n] : -1;
  }
  function timeIdxOf(startStr) { return TIMES.findIndex(t => t.start === startStr); }
  function parseSlots(timeStr) {
    if (!timeStr) return [];
    return timeStr.split('/').map(s => s.trim()).map(s => {
      const dayMatch = s.match(/(周[一二三四五六日])/);
      const timeMatch = s.match(/(\d{2}:\d{2})\s*-\s*(\d{2}:\d{2})/);
      if (!dayMatch || !timeMatch) return null;
      return { day: dayIdxOf(dayMatch[1]), start: timeMatch[1], end: timeMatch[2] };
    }).filter(Boolean);
  }

  const occupied = {};
  for (let di = 0; di < 7; di++) occupied[di] = {};
  const slotsMap = {};

  for (const course of allCourses) {
    const slots = parseSlots(course.time);
    const byDay = {};
    for (const s of slots) (byDay[s.day] = byDay[s.day] || []).push(s);
    for (const di in byDay) {
      const daySlots = byDay[+di].sort((a,b) => a.start.localeCompare(b.start));
      const groups = [];
      let curGroup = [daySlots[0]];
      for (let i = 1; i < daySlots.length; i++) {
        const prevEnd = curGroup[curGroup.length-1].end;
        if (daySlots[i].start === prevEnd) curGroup.push(daySlots[i]);
        else { groups.push(curGroup); curGroup = [daySlots[i]]; }
      }
      groups.push(curGroup);
      for (const g of groups) {
        const firstTi = timeIdxOf(g[0].start);
        if (firstTi < 0) continue;
        const lastTi = timeIdxOf(g[g.length-1].start);
        const span = (lastTi < 0 ? firstTi : lastTi - firstTi) + 1;
        for (let ti = firstTi; ti < firstTi + span; ti++) {
          if (ti === firstTi) slotsMap[di+','+ti] = { course, span };
          else occupied[di][ti] = true;
        }
      }
    }
  }

  const today = new Date();
  const WEEK0 = semWeek0();
  const curWeek = semCurWeek();
  const week = window._schedWeek || curWeek;
  const monday = new Date(WEEK0);
  monday.setDate(monday.getDate() + (week - 1) * 7);
  const dates = DAYS.map((d,i) => {
    const dt = new Date(monday);
    dt.setDate(monday.getDate() + i);
    return { name: d, num: dt.getDate(), month: dt.getMonth() + 1 };
  });

  function esc(t) { return (t || '').replace(/[<>&]/g, ''); }

  // 冲突课程集合 + 新增课程集合
  const confIds = new Set();
  conflicts.forEach(c => { confIds.add(c.courseAId); confIds.add(c.courseBId); });
  const extraIds = new Set(extra.map(c => c.courseId));

  let html = '<div class="section">' +
    '<div class="section-header">' +
      '<div><div class="section-title">' + svgIcon('calendar') + ' 我的课表</div>' +
        '<div style="font-size:12px;color:var(--text-light);margin-top:2px">已录取课程的上课安排 · 选课结果确定后按此课表上课</div></div>' +
      '<div style="display:flex;gap:8px;align-items:center">' +
        '<button class="week-selector" onclick="openWeekPicker()">' +
          '<span class="ws-chev">‹</span>' +
          '<span class="ws-main">' + semName() + ' 第' + week + '周</span>' +
          '<span class="ws-chev">›</span>' +
        '</button>' +
        '<button class="btn btn-ghost btn-sm" onclick="resetScheduleGen()">' + svgIcon('refresh') + ' 重置</button>' +
        '<button class="btn btn-primary btn-sm" onclick="exportScheduleIcs()">' + svgIcon('download') + ' 导出日历</button>' +
      '</div>' +
    '</div>' +
    '<div class="schedule-wrap"><table class="schedule-table">' +
      '<thead><tr><th class="time-col"><div class="sd-month">' + schedMonthLabel(dates) + '</div></th>' +
        dates.map(d => '<th><div class="sd-name">' + d.name + '</div><div class="sd-num">' + d.num + '</div></th>').join('') +
      '</tr></thead><tbody>';

  for (let ti = 0; ti < TIMES.length; ti++) {
    html += '<tr>';
    html += '<td class="time-col"><div class="ti-label">' + ['一二节','三四节','五六节','七八节','九十节'][ti] + '</div><div class="ti-time">' + TIMES[ti].start + '</div></td>';
    for (let di = 0; di < 7; di++) {
      if (occupied[di][ti]) continue;
      const key = di + ',' + ti;
      const slot = slotsMap[key];
      if (slot) {
        const c = slot.course;
        const ci = ((c.courseId || ti * 7 + di) % 5) + 1;
        const rs = slot.span;
        const h = rs * 36 + (rs - 1) * 4 - 8;
        const isExtra = extraIds.has(c.courseId);
        const isConflict = confIds.has(c.courseId);
        const isReq = (c.category || '').indexOf('必修') >= 0;
        const cls = isReq ? 'schedule-item c-req' : ('schedule-item c' + ci + (isExtra ? ' c-new' : '') + (isConflict ? ' c-conflict' : ''));
        html += '<td rowspan="' + rs + '" class="course-td"><div class="' + cls + '" style="min-height:' + h + 'px">' +
          (isReq ? '<div class="si-tag req">必修</div>' : isExtra ? '<div class="si-tag">' + svgIcon('sparkles') + ' 新增</div>' : '<div class="si-tag el">选修</div>') +
          '<div class="si-name">' + esc(c.name) + '</div>' +
          '<div class="si-info">' + esc(c.teacher) + '</div>' +
          '<div class="si-loc">' + svgIcon('map-pin') + ' ' + esc(c.location) + (c.campus ? ' · ' + esc(c.campus) : '') + '</div>' +
        '</div></td>';
      } else {
        html += '<td class="empty-td"></td>';
      }
    }
    html += '</tr>';
  }
  html += '</tbody></table></div></div>';

  // 冲突报告
  if (conflicts.length) {
    const typeMeta = { time: ['clock','时间冲突','#ef4444'], campus: ['map-pin','校区冲突','#f97316'], exam: ['file-text','考试冲突','#8b5cf6'] };
    html += '<div class="conflict-section">' +
      '<div class="conflict-header"><span>' + svgIcon('alert-triangle') + ' 冲突检测报告（' + conflicts.length + ' 处）</span><span style="font-size:12px;color:var(--text-light)">红色卡片为冲突课程，建议调整</span></div>' +
      conflicts.map(c => {
        const tm = typeMeta[c.type] || [c.type, c.type, '#6b7280'];
        return '<div class="conflict-item"><span class="conflict-badge" style="background:' + tm[2] + '1a;color:' + tm[2] + '">' + svgIcon(tm[0]) + ' ' + tm[1] + '</span><span class="conflict-detail">' + esc(c.detail) + '</span></div>';
      }).join('') +
    '</div>';
  } else {
    html += '<div class="conflict-ok">' + svgIcon('check-circle') + ' 恭喜，当前课表无任何时间 / 校区 / 考试冲突</div>';
  }

  // 无课表课程
  const scheduledIds = new Set();
  for (const k in slotsMap) scheduledIds.add(slotsMap[k].course.courseId);
  const unscheduled = allCourses.filter(c => !scheduledIds.has(c.courseId));
  if (unscheduled.length) {
    html += '<div class="unscheduled-section">' +
      '<div class="us-header"><span>' + svgIcon('file-text') + ' 无课表课程</span><span class="us-count">' + unscheduled.length + '门</span></div>' +
      '<div class="us-list">' +
      unscheduled.map(c => '<div class="us-item">' + esc(c.name) + ' · ' + esc(c.teacher) + ' · ' + esc(c.location) + '</div>').join('') +
      '</div></div>';
  }

  // 智能课表生成面板（异步获取候选课程）
  html += await scheduleGenPanelHtml(allCourses);

  // 记录导出用完整课表（已录取 + 新增）
  window._schedAll = allCourses;

  document.getElementById('content').innerHTML = html;
  bindCandEvents();
}

// ---- 周次选择（图片样式：底部弹窗） ----
function schedMonthLabel(dates) {
  const set = [...new Set(dates.map(d => d.month))];
  return set.map(m => m + '月').join('/');
}
function schedCurWeek() { return semCurWeek(); }
let _wkSel = 0;
function openWeekPicker() {
  const curWeek = schedCurWeek();
  const sel = window._schedWeek || curWeek;
  _wkSel = sel;
  let weeks = '';
  for (let i = 1; i <= 18; i++) {
    weeks += '<div class="wk-opt' + (i === sel ? ' active' : '') + (i === curWeek ? ' cur' : '') + '" data-w="' + i + '" onclick="pickWeek(' + i + ')">第' + i + '周' + (i === curWeek ? '<span class="wk-now">本周</span>' : '') + '</div>';
  }
  const oldEl = document.getElementById('week-picker');
  if (oldEl) oldEl.remove();
  document.body.insertAdjacentHTML('beforeend',
    '<div class="wk-overlay" id="week-picker" onclick="if(event.target===this)closeWeekPicker()">' +
      '<div class="wk-sheet">' +
        '<div class="wk-sheet-head"><button class="wk-btn-text" onclick="closeWeekPicker()">取消</button><span class="wk-sheet-title">选择时间及模式</span><button class="wk-btn-text primary" onclick="confirmWeekPicker()">确认</button></div>' +
        '<div class="wk-tabs"><span class="wk-tab active">选择时间</span><span class="wk-tab">选择时间模式</span></div>' +
        '<div class="wk-body">' +
          '<div class="wk-col"><div class="wk-col-title">学期</div><div class="wk-opt active">' + semName() + '</div></div>' +
          '<div class="wk-col"><div class="wk-col-title">周次</div><div class="wk-weeks">' + weeks + '</div></div>' +
        '</div>' +
      '</div>' +
    '</div>');
}
function pickWeek(n) {
  _wkSel = n;
  const el = document.getElementById('week-picker');
  if (el) el.querySelectorAll('.wk-opt[data-w]').forEach(o => o.classList.toggle('active', parseInt(o.dataset.w) === n));
}
function confirmWeekPicker() {
  if (_wkSel) { window._schedWeek = _wkSel; renderSchedule(); }
  closeWeekPicker();
}
function closeWeekPicker() {
  const el = document.getElementById('week-picker');
  if (el) el.remove();
}

// ---- 智能课表生成面板 ----
async function scheduleGenPanelHtml(curCourses) {
  const r = await api('GET', '/courses?category=全部');
  const all = r.success ? r.data : [];
  const curIds = new Set(curCourses.map(c => c.courseId || c.id));
  const cands = all.filter(c => !curIds.has(c.id));
  const lockSet = window._schedLocks || (window._schedLocks = new Set());
  const map = new Map();
  cands.forEach(c => map.set(c.id, c));
  window._schedCandMap = map;
  function esc(t) { return (t || '').replace(/[<>&]/g, ''); }

  return '<div class="section" style="margin-top:20px">' +
    '<div class="section-header"><div class="section-title">' + svgIcon('cpu') + ' 智能选课推荐</div>' +
    '<span style="font-size:12px;color:var(--text-light)">勾选想学的课程 → 系统推荐互不冲突的选课组合；' + svgIcon('unlock') + '/' + svgIcon('lock') + ' 锁定课程优先入选</span></div>' +
    (cands.length
      ? '<div class="cand-list">' + cands.map(c => {
          const locked = lockSet.has(c.id);
          return '<div class="cand-row">' +
            '<label style="display:flex;align-items:center;gap:8px;flex:1;min-width:0;cursor:pointer">' +
              '<input type="checkbox" class="cand-check" data-id="' + c.id + '" checked>' +
              '<span class="cand-name">' + esc(c.name) + '</span>' +
            '</label>' +
            '<span class="cand-meta">' + esc(c.time || '') + ' · ' + esc(c.location || '') + ' · ' + (c.credit||0) + ' 学分</span>' +
            '<span class="cand-campus">' + esc(c.campus || '主校区') + '</span>' +
            '<button class="cand-lock' + (locked ? ' locked' : '') + '" data-id="' + c.id + '" title="锁定：优先加入课表">' + svgIcon(locked ? 'lock' : 'unlock') + '</button>' +
          '</div>';
        }).join('') +
        '<div class="cand-actions">' +
          '<button class="btn btn-ghost btn-sm" onclick="resetScheduleGen()">' + svgIcon('refresh') + ' 重置</button>' +
          '<button class="btn btn-primary btn-sm" id="gen-schedule-btn" onclick="generateSchedule()">' + svgIcon('sparkles') + ' 生成推荐方案</button>' +
        '</div>' +
        '<div id="rec-result"></div>' +
      '</div>'
      : '<div class="cand-empty">没有更多可选的候选课程 ' + svgIcon('party') + '</div>') +
  '</div>';
}

function bindCandEvents() {
  $$('.cand-lock').forEach(btn => {
    btn.onclick = (e) => {
      e.preventDefault();
      const id = parseInt(btn.dataset.id);
      const lockSet = window._schedLocks || (window._schedLocks = new Set());
      if (lockSet.has(id)) lockSet.delete(id); else lockSet.add(id);
      btn.classList.toggle('locked');
      btn.innerHTML = svgIcon(lockSet.has(id) ? 'lock' : 'unlock');
    };
  });
}

async function generateSchedule() {
  const checked = [...querySelectorAll('.cand-check:checked')].map(cb => parseInt(cb.dataset.id));
  if (!checked.length) { toast('请至少勾选一门候选课程', 'error'); return; }
  const locks = [...(window._schedLocks || [])].filter(id => checked.includes(id));
  const btn = $('#gen-schedule-btn');
  if (btn) { btn.disabled = true; btn.innerHTML = svgIcon('hourglass') + ' 生成中...'; }
  try {
    const r = await api('POST', '/schedule/generate', { candidates: checked, locks });
    if (!r.success) { toast(r.message, 'error'); return; }
    const schedule = r.data.schedule || [];
    const addedIds = new Set(r.data.added || []);
    const recommended = schedule.filter(c => addedIds.has(c.courseId));
    const rejected = r.data.rejected || [];
    window._recResult = { recommended: recommended, rejected: rejected };
    renderRecommendResult(recommended, rejected);
    toast(rejected.length
        ? ('推荐方案已生成：' + recommended.length + ' 门可加入，' + rejected.length + ' 门存在时间冲突')
        : ('推荐方案已生成：' + recommended.length + ' 门互不冲突'),
      rejected.length ? 'warning' : 'success');
  } catch (err) {
    toast('生成失败：' + err.message, 'error');
  } finally {
    if (btn) { btn.disabled = false; btn.innerHTML = svgIcon('sparkles') + ' 生成无冲突课表'; }
  }
}

function resetScheduleGen() {
  window._schedExtra = [];
  window._schedLocks = new Set();
  window._recResult = null;
  renderSchedule();
}

function exportScheduleIcs() {
  const list = window._schedAll || [];
  if (!list.length) { toast('暂无可导出的课表', 'error'); return; }
  // 从所选周次开始排，重复 16 周（第1周周一 = 2026-09-07）
  const weekN = window._schedWeek || 1;
  const base = new Date(2026, 8, 7);
  base.setDate(base.getDate() + (weekN - 1) * 7);
  const m = { '一':0,'二':1,'三':2,'四':3,'五':4,'六':5,'日':6 };
  const pad = n => String(n).padStart(2, '0');
  const dtUTC = (d, t) => {
    const [hh, mm] = (t || '00:00').split(':').map(Number);
    const dt = new Date(d); dt.setHours(hh, mm, 0, 0);
    return dt.toISOString().replace(/[-:]/g, '').slice(0, 15) + 'Z';
  };
  const lines = ['BEGIN:VCALENDAR','VERSION:2.0','PRODID:-//Zhixue//Schedule//CN','CALSCALE:GREGORIAN'];
  list.forEach((c, idx) => {
    const times = (c.time || '').split('/');
    times.forEach((t, j) => {
      const dm = t.match(/(周[一二三四五六日])/);
      const tm = t.match(/(\d{2}:\d{2})\s*-\s*(\d{2}:\d{2})/);
      if (!dm || !tm || m[dm[1].replace(/周/,'')] == null) return;
      const day = new Date(base); day.setDate(base.getDate() + m[dm[1].replace(/周/,'')]);
      lines.push('BEGIN:VEVENT');
      lines.push('UID:zhixue-' + (c.courseId || idx) + '-' + j + '@zhixue.local');
      lines.push('DTSTAMP:' + new Date().toISOString().replace(/[-:]/g, '').slice(0, 15) + 'Z');
      lines.push('DTSTART:' + dtUTC(day, tm[1]));
      lines.push('DTEND:' + dtUTC(day, tm[2]));
      lines.push('SUMMARY:' + (c.name || '课程') + (c.campus && c.campus !== '主校区' ? '(' + c.campus + ')' : ''));
      lines.push('LOCATION:' + (c.location || ''));
      lines.push('DESCRIPTION:' + (c.teacher ? '教师:' + c.teacher : '') + ' 每周重复，共16周');
      lines.push('RRULE:FREQ=WEEKLY;COUNT=16');
      lines.push('END:VEVENT');
    });
  });
  lines.push('END:VCALENDAR');
  const blob = new Blob([lines.join('\r\n')], { type: 'text/calendar;charset=utf-8' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = 'zhixue-schedule.ics';
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(a.href), 2000);
  toast('已导出日历文件（16周重复），可用手机日历 / Outlook / Google 日历导入', 'success');
}

// ===== 成绩 =====
async function renderScores() {
  const r = await api('GET', '/enrollments/scores');
  const scores = r.success ? r.data : [];
  let html = '<div class="stats-grid">';
  if (scores.length) {
    const totalCredits = scores.reduce((s, x) => s + x.credit, 0);
    const scored = scores.filter(x => x.totalScore != null);
    const gpa = scored.length ? scored.reduce((s, x) => s + gpaOf(x.totalScore) * x.credit, 0) / (totalCredits || 1) : 0;
    const avg = scored.length ? scored.reduce((s, x) => s + x.totalScore, 0) / scored.length : 0;
    const passCount = scored.filter(x => x.totalScore >= 60).length;
    html += `
      <div class="stat-card"><div class="stat-icon purple">${svgIcon('graduation-cap')}</div><div><div class="stat-value">${gpa.toFixed(2)}</div><div class="stat-label">GPA</div></div></div>
      <div class="stat-card"><div class="stat-icon blue">${svgIcon('bar-chart')}</div><div><div class="stat-value">${avg.toFixed(1)}</div><div class="stat-label">平均分</div></div></div>
      <div class="stat-card"><div class="stat-icon green">${svgIcon('check-circle')}</div><div><div class="stat-value">${passCount}/${scores.length}</div><div class="stat-label">通过课程</div></div></div>
      <div class="stat-card"><div class="stat-icon amber">${svgIcon('star')}</div><div><div class="stat-value">${totalCredits}</div><div class="stat-label">总学分</div></div></div>`;
  }
  html += '</div>';
  if (scores.length) {
    html += `<div class="data-table"><table><thead><tr><th>课程</th><th>类别</th><th>教师</th><th>学分</th><th>成绩</th><th>绩点</th></tr></thead><tbody>${scores.map(s => {
      const ts = s.totalScore;
      const sc = ts == null ? '' : ts >= 90 ? 'score-excellent' : ts >= 80 ? 'score-good' : ts >= 60 ? 'score-pass' : 'score-fail';
      const scoreCell = ts != null ? `<span class="score-badge ${sc}">${ts}</span>` : '<span style="color:#999">未录入</span>';
      const gpaCell = ts != null ? gpaOf(ts).toFixed(1) : '-';
      return `<tr><td><b>${s.courseName}</b></td><td><span class="course-cat cat-${s.category}">${s.category}</span></td><td>${s.teacherName}</td><td>${s.credit}</td><td>${scoreCell}</td><td>${gpaCell}</td></tr>`;
    }).join('')}</tbody></table></div>`;
  } else html += emptyHtml('graduation-cap', '暂无成绩记录');
  $('#content').innerHTML = html;
}

// ===== 毕业进度可视化 =====
async function renderGraduation() {
  const r = await api('GET', '/graduation/summary');
  if (!r.success) { $('#content').innerHTML = emptyHtml('target', r.message); return; }
  const d = r.data;
  const cat = d.categories || [];
  const tot = d.total || { required: 0, earned: 0, gap: 0, percent: 0 };
  const icons = { '必修': 'book', '选修': 'book-open', '通识': 'layers', '实践': 'flask' };
  const catCards = cat.map(c => `
    <div class="stat-card">
      <div class="stat-icon ${c.done ? 'green' : 'amber'}">${svgIcon(icons[c.key] || 'star')}</div>
      <div><div class="stat-value">${c.earned}<span style="font-size:13px;color:var(--text-light)"> / ${c.required}</span></div>
      <div class="stat-label">${c.key}学分 ${c.done ? svgIcon('check-circle') + ' 已完成' : '· 还差 ' + c.gap}</div></div>
    </div>`).join('');
  $('#content').innerHTML = `
    <div class="stats-grid">
      <div class="stat-card"><div class="stat-icon purple">${svgIcon('target')}</div><div><div class="stat-value">${tot.percent}%</div><div class="stat-label">毕业进度完成度</div></div></div>
      <div class="stat-card"><div class="stat-icon blue">${svgIcon('star')}</div><div><div class="stat-value">${tot.earned}<span style="font-size:13px;color:var(--text-light)"> / ${tot.required}</span></div><div class="stat-label">已获学分</div></div></div>
      <div class="stat-card"><div class="stat-icon amber">${svgIcon('pin')}</div><div><div class="stat-value">${tot.gap}</div><div class="stat-label">还差总学分</div></div></div>
    </div>
    <div class="section"><div class="section-header"><div class="section-title">各模块学分完成情况</div></div>
      <div class="stats-grid">${catCards}</div>
      <div style="margin-top:12px;padding:16px;border:1px solid var(--border);border-radius:12px;background:var(--card-bg)">
        <div style="display:flex;justify-content:space-between;font-size:14px;margin-bottom:6px"><span>毕业总进度</span><span><b>${tot.earned}</b> / ${tot.required} 学分（${tot.percent}%）</span></div>
        <div class="progress-bar" style="height:12px"><div class="progress-fill" style="width:${Math.min(100, tot.percent)}%"></div></div>
      </div>
    </div>
    <div class="section"><div class="section-header"><div class="section-title">${svgIcon('pin')} 推荐可修课程（补齐差额）</div></div>
      <div id="grad-recs">${gradRecHtml(d.recommend)}</div>
    </div>`;
  bindCourseActions();
}

function gradRecHtml(recs) {
  if (!recs || !recs.length) return emptyHtml('inbox', '暂无可推荐的课程（或各模块学分已达标）');
  return `<div class="course-grid">${recs.map(c => `
    <div class="course-card">
      <span class="course-cat cat-${c.category}">${c.category}</span>
      <div class="course-name">${c.name}</div>
      <div class="course-teacher">${svgIcon('user')} ${c.teacherName} · ${c.credit} 学分</div>
      <div class="course-meta"><span>${svgIcon('clock')} ${(c.time||'').split('/')[0].trim()}</span><span>${svgIcon('map-pin')} ${c.location}</span>${c.rating ? `<span class="course-rating">${svgIcon('star')} ${c.rating}</span>` : ''}</div>
      <div class="course-desc">${c.intro}</div>
      <div class="course-footer">
        <span class="stu-points">补齐「${c.group}」还差 ${c.groupGap} 学分</span>
        <button class="btn btn-primary btn-sm" data-action="enroll" data-id="${c.courseId}">投入积分选课</button>
      </div>
    </div>`).join('')}</div>`;
}

function gpaOf(s) {
  if (s >= 90) return 4.0; if (s >= 85) return 3.7; if (s >= 80) return 3.3;
  if (s >= 75) return 3.0; if (s >= 70) return 2.7; if (s >= 65) return 2.3;
  if (s >= 60) return 2.0; return 0;
}

// ============================================================
// 老师端
// ============================================================

async function renderTeacherDashboard() {
  const user = getUser();
  const sys = await api('GET', '/system/config');
  const r = await api('GET', `/courses/teacher/${user.id}`);
  const courses = r.success ? r.data : [];
  const totalEnrolled = courses.reduce((s, c) => s + (c.stat?.total || 0), 0);
  const pendingCourses = courses.filter(c => c.oversubscribed).length;

  $('#content').innerHTML = `
    ${homeHeroHtml(user.name, '老师', courses.length, '我教授的课程', 'book-open')}
    <div class="stats-grid">
      <div class="stat-card"><div class="stat-icon blue">${svgIcon('book-open')}</div><div><div class="stat-value">${courses.length}</div><div class="stat-label">我教授的课程</div></div></div>
      <div class="stat-card"><div class="stat-icon green">${svgIcon('users')}</div><div><div class="stat-value">${totalEnrolled}</div><div class="stat-label">总报名人数</div></div></div>
      <div class="stat-card"><div class="stat-icon amber">${svgIcon('hourglass')}</div><div><div class="stat-value">${pendingCourses}</div><div class="stat-label">待审批课程</div></div></div>
    </div>
    <div id="announce-slot"></div>
    <div class="section"><div class="section-header"><div class="section-title">我教授的课程</div><a class="section-link" onclick="navigate('teach-courses')">管理 →</a></div>
      ${courses.length ? courseGridHtml(courses, false) : emptyHtml('book-open', '暂无教授课程')}
    </div>`;
  await loadAnnouncementCards('#announce-slot');
}

async function renderTeachCourses() {
  const user = getUser();
  const r = await api('GET', `/courses/teacher/${user.id}`);
  const courses = r.success ? r.data : [];
  if (!courses.length) { $('#content').innerHTML = emptyHtml('book-open', '暂无教授课程'); return; }
  $('#content').innerHTML = `<div class="data-table"><table><thead><tr><th>课程</th><th>类别</th><th>学分</th><th>容量</th><th>报名</th><th>状态</th><th>操作</th></tr></thead><tbody>${courses.map(c => {
    const stat = c.stat || { total: 0, approved: 0 };
    const over = c.oversubscribed;
    return `<tr>
      <td><b>${c.name}</b></td>
      <td><span class="course-cat cat-${c.category}">${c.category}</span></td>
      <td>${c.credit}</td>
      <td>${c.capacity}</td>
      <td>${stat.total} 人（已录取 ${stat.approved}）</td>
      <td>${over ? `<span class="status-tag status-pending">超容量待审批</span>` : `<span class="status-tag status-approved">正常</span>`}</td>
      <td><button class="btn btn-${over ? 'primary' : 'ghost'} btn-sm" onclick="openApprove(${c.id})">${over ? '审批选课' : '查看报名'}</button> <button class="btn btn-outline btn-sm" onclick="navigate('student-list')">查看学生</button> <button class="btn btn-outline btn-sm" onclick="navigate('grade-mgr')">成绩管理</button></td>
    </tr>`;
  }).join('')}</tbody></table></div>`;
}

async function renderApproveList() {
  const user = getUser();
  const r = await api('GET', `/courses/teacher/${user.id}`);
  const courses = r.success ? r.data : [];
  const needApprove = courses.filter(c => c.oversubscribed);
  $('#content').innerHTML = `
    <div class="section"><div class="section-header"><div class="section-title">待审批课程（${needApprove.length}）</div></div>
      ${needApprove.length ? `<div class="data-table"><table><thead><tr><th>课程</th><th>容量</th><th>报名人数</th><th>操作</th></tr></thead><tbody>${needApprove.map(c => `<tr><td><b>${c.name}</b></td><td>${c.capacity}</td><td>${c.stat.total} 人</td><td><button class="btn btn-primary btn-sm" onclick="openApprove(${c.id})">去审批</button></td></tr>`).join('')}</tbody></table></div>` : emptyHtml('check-circle', '暂无需要审批的课程')}
    </div>
    <div class="section"><div class="section-header"><div class="section-title">我的全部课程</div></div>
      ${courses.length ? `<div class="data-table"><table><thead><tr><th>课程</th><th>容量</th><th>报名</th><th>操作</th></tr></thead><tbody>${courses.map(c => `<tr><td><b>${c.name}</b></td><td>${c.capacity}</td><td>${c.stat.total} 人</td><td><button class="btn btn-outline btn-sm" onclick="openApprove(${c.id})">查看报名</button></td></tr>`).join('')}</tbody></table></div>` : ''}
    </div>`;
}

async function openApprove(courseId) {
  const [courseR, enrR, wlR] = await Promise.all([
    api('GET', '/courses?category=全部'),
    api('GET', `/enrollments/course/${courseId}`),
    api('GET', `/waitlist/course/${courseId}`),
  ]);
  const course = courseR.data.find(c => c.id == courseId);
  const list = enrR.success ? enrR.data : [];
  const selected = new Set(list.filter(e => e.status === 'approved').map(e => e.studentId));

  $('#content').innerHTML = `
    <div class="section">
      <div class="section-header">
        <div class="section-title">审批「${course.name}」选课</div>
        <button class="btn btn-ghost btn-sm" onclick="navigate('approve')">${svgIcon('arrow-left')} 返回</button>
      </div>
      <div class="data-table">
        <div class="approve-toolbar">
          <div class="approve-count">已选 <b id="sel-count">${selected.size}</b> / ${course.capacity} 人（报名共 ${list.length} 人）</div>
          <div style="display:flex;gap:8px;">
            <button class="btn btn-outline btn-sm" id="auto-by-points">按积分自动录取前${course.capacity}人</button>
            <button class="btn btn-success btn-sm" id="submit-approve">提交审批结果</button>
          </div>
        </div>
        ${list.map(e => `
          <label class="approve-student-row ${selected.has(e.studentId) ? 'selected' : ''}">
            <input type="checkbox" data-sid="${e.studentId}" ${selected.has(e.studentId) ? 'checked' : ''}>
            <div class="stu-info">
              <div class="stu-name">${e.studentName}</div>
              <div class="stu-meta">${e.studentNo} · ${e.studentMajor || ''} · 状态：<span class="status-tag status-${e.status}">${e.status === 'approved' ? '已录取' : e.status === 'pending' ? '待审批' : '未录取'}</span></div>
            </div>
            <span class="stu-points">${e.points} 分</span>
          </label>`).join('')}
      </div>
    </div>
    <div class="section" style="margin-top:20px">
      <div class="section-header"><div class="section-title">${svgIcon('hourglass')} 候补队列</div></div>
      ${wlR.success && wlR.data.length ? `<div class="data-table"><table><thead><tr><th>学号</th><th>姓名</th><th>专业</th><th>状态</th><th>位置</th></tr></thead><tbody>${wlR.data.map(w => `<tr><td>${w.studentNo}</td><td>${w.studentName}</td><td>${w.studentMajor || '-'}</td><td>${w.status === 'confirming' ? '待确认' : '候补中'}</td><td>第 ${w.position} 位</td></tr>`).join('')}</tbody></table></div>` : emptyHtml('hourglass', '暂无候补学生')}
    </div>`;

  const updateCount = () => {
    const n = $$('#content input[type="checkbox"]:checked').length;
    $('#sel-count').textContent = n;
    $$('.approve-student-row').forEach(row => row.classList.toggle('selected', row.querySelector('input').checked));
  };
  $$('#content input[type="checkbox"]').forEach(cb => cb.addEventListener('change', updateCount));

  $('#auto-by-points').onclick = () => {
    // 规则：优先录取投入积分多的学生；已录取的保留；剩余名额按积分降序（列表已排序）自动预选，
    // 投入积分相同的学生由老师手动决定录取哪些
    const rows = [...querySelectorAll('#content .approve-student-row')];
    const was = new Set(rows.filter(r => r.querySelector('input').checked).map(r => +r.querySelector('input').dataset.sid));
    let need = course.capacity;
    rows.forEach(r => {
      const cb = r.querySelector('input');
      if (was.has(+cb.dataset.sid)) { cb.checked = true; need--; } else cb.checked = false;
    });
    rows.forEach(r => {
      const cb = r.querySelector('input');
      if (need <= 0 || cb.checked) return;
      cb.checked = true; need--;
    });
    updateCount();
    toast('已按积分自动预选至容量，积分相同处可手动调整', 'info');
  };

  $('#submit-approve').onclick = async () => {
    const chosen = [...querySelectorAll('#content input[type="checkbox"]:checked')].map(cb => parseInt(cb.dataset.sid));
    if (chosen.length > course.capacity) { toast(`录取人数不能超过容量 ${course.capacity}`, 'error'); return; }
    if (!await confirmAsync('批量录取', "确认录取 ${chosen.length} 名学生？", 'info')) return;
    const r = await api('POST', '/enrollments/approve', { courseId: parseInt(courseId), studentIds: chosen });
    toast(r.message, r.success ? 'success' : 'error');
    if (r.success) navigate('approve');
  };
}

// ============================================================
// 管理员端
// ============================================================

async function renderAdminDashboard() {
  const user = getUser();
  const [usersR, coursesR, enrR, sysR, logsR] = await Promise.all([
    api('GET', '/admin/users'),
    api('GET', '/courses?category=全部'),
    api('GET', '/enrollments/mine'),  // 复用接口拿总数不太合适，这里只展示大概
    api('GET', '/system/config'),
    api('GET', '/logs'),
  ]);
  const users = usersR.success ? usersR.data : [];
  const courses = coursesR.success ? coursesR.data : [];
  const recentLogs = logsR.success ? logsR.data.slice(0, 6) : [];
  const studentCount = users.filter(u => u.role === 'student').length;
  const teacherCount = users.filter(u => u.role === 'teacher').length;

  const tlIcon = { '登录':'user', '选课':'book', '退课':'x-circle', '审批':'check-circle' };
  let tlHtml = recentLogs.map(l =>
    '<div class="tl-item"><div class="tl-dot">' + svgIcon(tlIcon[l.type] || 'cpu') + '</div>' +
    '<div class="tl-body"><div class="tl-text"><b>' + l.userName + '</b> ' + l.operation + '</div>' +
    '<div class="tl-time">' + timeAgo(l.createdAt) + '</div></div></div>'
  ).join('') || emptyHtml('cpu', '暂无动态');

  $('#content').innerHTML = `
    ${homeHeroHtml(user.name, '管理员', studentCount, '学生总数', 'users')}
    <div class="stats-grid">
      <div class="stat-card"><div class="stat-icon blue">${svgIcon('users')}</div><div><div class="stat-value">${studentCount}</div><div class="stat-label">学生总数</div></div></div>
      <div class="stat-card"><div class="stat-icon green">${svgIcon('user')}</div><div><div class="stat-value">${teacherCount}</div><div class="stat-label">教师总数</div></div></div>
      <div class="stat-card"><div class="stat-icon purple">${svgIcon('book')}</div><div><div class="stat-value">${courses.length}</div><div class="stat-label">课程总数</div></div></div>
      <div class="stat-card"><div class="stat-icon amber">${svgIcon('edit')}</div><div><div class="stat-value">${courses.reduce((s,c)=>s+(c.stat?.total||0),0)}</div><div class="stat-label">选课记录</div></div></div>
    </div>
    <div style="display:grid;grid-template-columns:1.2fr 1fr;gap:20px;">
      <div class="admin-card round-status-card">
        <div class="rsc-head">
          <h3>当前选课状态</h3>
          ${sysR.data.roundOpen ? '<span class="status-tag status-approved rsc-pill">进行中</span>' : '<span class="status-tag status-rejected rsc-pill">已关闭</span>'}
        </div>
        <div class="rsc-steps">
          ${[1, 2, 3].map(n => {
            const cls = n < sysR.data.currentRound ? 'done' : n === sysR.data.currentRound ? 'active' : '';
            return '<div class="rsc-step ' + cls + '"><div class="rsc-dot">' + (n < sysR.data.currentRound ? '✓' : n) + '</div><div class="rsc-step-label">第 ' + n + ' 轮</div></div>';
          }).join('<div class="rsc-line"></div>')}
        </div>
        <div class="rsc-stats">
          <div class="rsc-stat"><div class="rsc-stat-value">${courses.length}</div><div class="rsc-stat-label">开放课程</div></div>
          <div class="rsc-stat"><div class="rsc-stat-value">${courses.reduce((s,c)=>s+(c.stat?.total||0),0)}</div><div class="rsc-stat-label">本轮报名</div></div>
          <div class="rsc-stat"><div class="rsc-stat-value">${studentCount}</div><div class="rsc-stat-label">参与学生</div></div>
        </div>
      </div>
      <div class="admin-card"><h3>最近动态</h3><div class="timeline">${tlHtml}</div></div>
    </div>
    <div id="announce-slot"></div>`;
  await loadAnnouncementCards('#announce-slot');
}

async function renderRoundMgr() {
  const [r, sr] = await Promise.all([
    api('GET', '/system/config'),
    api('GET', '/system/round-schedule')
  ]);
  const c = r.data;
  const sd = (sr && sr.success) ? sr.data : { autoSchedule: false, schedules: [], stage: 'closed' };
  const schedules = sd.schedules || [];
  const dtVal = (v) => v ? String(v).slice(0, 16) : '';
  const fmtDT = (v) => v ? String(v).replace('T', ' ').slice(0, 16) : '—';
  const now = new Date();
  const stateOf = (s) => {
    if (!s || (!s.openAt && !s.closeAt)) return { t: '未配置', cls: 'rs-off' };
    const oa = s.openAt ? new Date(s.openAt) : null;
    const ca = s.closeAt ? new Date(s.closeAt) : null;
    if (oa && now < oa) return { t: '待开放', cls: 'rs-wait' };
    if (ca && now >= ca) return { t: '已结束', cls: 'rs-done' };
    return { t: '进行中', cls: 'rs-live' };
  };
  const auto = !!sd.autoSchedule;
  const stageText = c.roundOpen ? '选课进行中'
    : (c.roundOpenAt ? '尚未开放' : (auto ? '当前无开放轮次' : '已关闭'));
  const statusLine = auto
    ? `当前：第 ${c.currentRound || '—'} 轮 · ${stageText}`
      + (c.roundCloseAt ? ` · 本轮将于 ${fmtDT(c.roundCloseAt)} 自动结束`
        : (c.roundOpenAt ? ` · 将于 ${fmtDT(c.roundOpenAt)} 自动开放` : ''))
    : `当前：第 ${c.currentRound || '—'} 轮 · ${c.roundOpen ? '进行中' : '已关闭'}（手动模式）`;

  $('#content').innerHTML = `
    <div class="admin-card">
      <h3>选课时间设置 <span class="rs-tag">按时间自动开放 / 自动结束</span></h3>
      <p class="rs-hint">为每一轮设置<b>开始时间</b>与<b>结束时间</b>。到开始时间系统自动开放该轮选课，到结束时间自动关闭，无需人工值守切换；学生端登录页倒计时与「当前轮次」会同步更新。</p>
      <label class="rs-switch">
        <input type="checkbox" id="rs-auto" ${auto ? 'checked' : ''}>
        <span class="rs-slider"></span>
        <span class="rs-switch-label">启用自动模式（关闭后回到手动切换）</span>
      </label>
      <div class="rs-rows">
        ${[1, 2, 3].map(n => {
          const s = schedules.find(x => x.roundNo === n) || { roundNo: n };
          const st = stateOf(s);
          return `<div class="rs-row">
            <span class="rs-round">第 ${n} 轮</span>
            <label class="rs-field"><span>开始</span><input type="datetime-local" id="rs-open-${n}" value="${dtVal(s.openAt)}"></label>
            <label class="rs-field"><span>结束</span><input type="datetime-local" id="rs-close-${n}" value="${dtVal(s.closeAt)}"></label>
            <span class="rs-state ${st.cls}">${st.t}</span>
          </div>`;
        }).join('')}
      </div>
      <div class="rs-actions">
        <button class="btn btn-primary" onclick="saveRoundSchedule()">保存时间设置</button>
        <button class="btn" onclick="clearRoundSchedule()">清空全部时间</button>
        <span class="rs-statusline">${statusLine}</span>
      </div>
    </div>
    <div class="admin-card">
      <h3>手动控制${auto ? ' <span class="rs-tag rs-tag-warn">自动模式开启中，手动操作会关闭自动模式</span>' : ''}</h3>
      <p style="font-size:13px;color:var(--text-light);margin-bottom:16px">切换当前选课轮次。每轮学生拥有 ${POINTS_PER_ROUND} 积分。${auto ? '当前已启用自动模式，手动点击将立即退出自动模式。' : ''}</p>
      <div class="round-controls">
        ${[1, 2, 3].map(n => `<button class="round-btn ${c.currentRound === n ? 'active' : ''}" onclick="changeRound(${n})">第 ${n} 轮</button>`).join('')}
        <button class="btn ${c.roundOpen ? 'btn-danger' : 'btn-success'}" onclick="toggleRoundOpen()">${c.roundOpen ? '关闭本轮选课' : '开放本轮选课'}</button>
      </div>
    </div>
    <div class="admin-card">
      <h3>候补队列设置</h3>
      <p style="font-size:13px;color:var(--text-light);margin-bottom:12px">课程满员后学生可排队候补；有人退课自动递补，递补成功的学生需在限时内确认，超时自动释放名额给下一位。</p>
      <div style="display:flex;align-items:center;gap:10px;flex-wrap:wrap">
        <label style="font-size:14px">限时确认（分钟）</label>
        <input type="number" id="confirm-minutes" min="1" max="1440" value="${c.confirmMinutes || 30}" style="width:110px;padding:8px 12px;border:1.5px solid var(--border);border-radius:8px">
        <button class="btn btn-primary" onclick="saveConfirmMinutes()">保存设置</button>
      </div>
    </div>
    <div class="admin-card">
      <h3>毕业学分要求</h3>
      <p style="font-size:13px;color:var(--text-light);margin-bottom:12px">设置学生毕业所需学分（必修/选修/通识/实践），学生端「毕业进度」页据此展示完成情况并推荐课程。</p>
      <div style="display:flex;align-items:center;gap:10px;flex-wrap:wrap">
        <label style="font-size:14px">必修</label>
        <input type="number" id="grad-required" min="0" value="${c.gradReqRequired ?? 12}" style="width:90px;padding:8px 10px;border:1.5px solid var(--border);border-radius:8px">
        <label style="font-size:14px">选修</label>
        <input type="number" id="grad-elective" min="0" value="${c.gradReqElective ?? 10}" style="width:90px;padding:8px 10px;border:1.5px solid var(--border);border-radius:8px">
        <label style="font-size:14px">通识</label>
        <input type="number" id="grad-general" min="0" value="${c.gradReqGeneral ?? 6}" style="width:90px;padding:8px 10px;border:1.5px solid var(--border);border-radius:8px">
        <label style="font-size:14px">实践</label>
        <input type="number" id="grad-practice" min="0" value="${c.gradReqPractice ?? 4}" style="width:90px;padding:8px 10px;border:1.5px solid var(--border);border-radius:8px">
        <button class="btn btn-primary" onclick="saveGradReqs()">保存设置</button>
      </div>
    </div>
    <div class="admin-card">
      <h3>本轮结算</h3>
      <p style="font-size:13px;color:var(--text-light);margin-bottom:12px">选课期间学生只登记报名（积分冻结）。点下方按钮后，系统按每门课的投入积分<b>从高到低</b>统一录取，同分先报名者优先；超出容量的记为未录取并自动退还积分。</p>
      <button class="btn btn-primary" onclick="settleRound()">${svgIcon('check-circle')} 结算本轮选课</button>
    </div>
    <div class="admin-card"><h3>轮次说明</h3>
      <ul style="font-size:14px;color:var(--text-light);line-height:2">
        <li>每轮学生有 <b>60 积分</b>，报名时投入并<b>冻结</b>，未录取自动退还。</li>
        <li>选课期间<b>只登记报名</b>，不即时录取；课程超额后依然可以继续报名。</li>
        <li>本轮结束后点「<b>结算本轮选课</b>」，按投入积分<b>从高到低</b>录取（同分先报名优先）。</li>
        <li>超出容量的记为未录取，可加入候补队列等待递补。</li>
      </ul>
    </div>`;
}

async function saveRoundSchedule() {
  const autoEl = document.getElementById('rs-auto');
  const auto = autoEl ? autoEl.checked : false;
  const schedules = [1, 2, 3].map(n => {
    const o = document.getElementById('rs-open-' + n);
    const cl = document.getElementById('rs-close-' + n);
    return { roundNo: n, openAt: (o && o.value) ? o.value : null, closeAt: (cl && cl.value) ? cl.value : null };
  });
  for (const s of schedules) {
    if ((s.openAt && !s.closeAt) || (!s.openAt && s.closeAt)) {
      toast(`第 ${s.roundNo} 轮需要同时填写开始与结束时间`, 'error'); return;
    }
    if (s.openAt && s.closeAt && s.openAt >= s.closeAt) {
      toast(`第 ${s.roundNo} 轮的结束时间必须晚于开始时间`, 'error'); return;
    }
  }
  if (auto && !schedules.some(s => s.openAt && s.closeAt)) {
    toast('启用自动模式前，请至少为一轮填写完整的时间窗口', 'error'); return;
  }
  const r = await api('POST', '/system/round-schedule', { autoSchedule: auto, schedules });
  toast(r.message || '已保存', r.success ? 'success' : 'error');
  if (r.success) navigate('rounds');
}

function clearRoundSchedule() {
  [1, 2, 3].forEach(n => {
    const o = document.getElementById('rs-open-' + n); if (o) o.value = '';
    const c = document.getElementById('rs-close-' + n); if (c) c.value = '';
  });
  toast('已清空输入框，点击「保存时间设置」后生效', '');
}

async function changeRound(r) {
  const res = await api('POST', '/system/round', { currentRound: r });
  toast(res.message, 'success'); navigate('rounds');
}
async function toggleRoundOpen() {
  const cur = await api('GET', '/system/config');
  const closing = cur.data.roundOpen;
  let tip = '';
  if (closing) {
    const cr = await api('GET', '/courses?category=全部');
    const courses = cr.success ? cr.data : [];
    const need = courses.filter(c => c.oversubscribed).length;
    if (need > 0) tip = `，${need} 门课程超容量，请提醒任课老师按积分优先录取（积分相同由老师选择）`;
  }
  if (cur.data.autoSchedule) tip += '；已退出自动模式，改为手动控制';
  const res = await api('POST', '/system/round-open', { roundOpen: !cur.data.roundOpen });
  toast(res.message + tip, 'success'); navigate('rounds');
}

async function saveConfirmMinutes() {
  const v = parseInt($('#confirm-minutes').value);
  if (!v || v < 1) { toast('请输入有效的分钟数', 'error'); return; }
  const r = await api('POST', '/system/confirm-minutes', { confirmMinutes: v });
  toast(r.message, r.success ? 'success' : 'error');
  if (r.success) navigate('rounds');
}

async function saveGradReqs() {
  const v = id => parseInt($(id).value) || 0;
  const r = await api('POST', '/system/graduation-reqs', {
    gradReqRequired: v('#grad-required'), gradReqElective: v('#grad-elective'),
    gradReqGeneral: v('#grad-general'), gradReqPractice: v('#grad-practice'),
  });
  toast(r.message, r.success ? 'success' : 'error');
  if (r.success) navigate('rounds');
}

async function renderCourseMgr() {
  const [coursesR, teachersR] = await Promise.all([api('GET', '/courses?category=全部'), api('GET', '/courses/teachers')]);
  const courses = coursesR.success ? coursesR.data : [];
  const teachers = teachersR.success ? teachersR.data : [];
  $('#content').innerHTML = `
    <div class="admin-card">
      <h3>添加课程</h3>
      <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:12px;margin-bottom:12px">
        <input id="nc-name" placeholder="课程名称" style="padding:10px;border:1.5px solid var(--border);border-radius:8px">
        <select id="nc-teacher" style="padding:10px;border:1.5px solid var(--border);border-radius:8px">${teachers.map(t => `<option value="${t.id}">${t.name}</option>`).join('')}</select>
        <select id="nc-cat" style="padding:10px;border:1.5px solid var(--border);border-radius:8px">${CATEGORIES.filter(c=>c!=='全部').map(c=>`<option>${c}</option>`).join('')}</select>
        <input id="nc-credit" type="number" min="1" max="10" value="3" placeholder="学分" style="padding:10px;border:1.5px solid var(--border);border-radius:8px">
        <input id="nc-cap" type="number" min="1" value="60" placeholder="容量" style="padding:10px;border:1.5px solid var(--border);border-radius:8px">
        <input id="nc-time" placeholder="时间 如 周一 08:00-09:40" style="padding:10px;border:1.5px solid var(--border);border-radius:8px">
        <input id="nc-loc" placeholder="地点" style="padding:10px;border:1.5px solid var(--border);border-radius:8px">
        <select id="nc-campus" style="padding:10px;border:1.5px solid var(--border);border-radius:8px"><option>主校区</option><option>东校区</option><option>西校区</option></select>
        <input id="nc-exam" placeholder="考试时间(留空=按上课时间)" style="padding:10px;border:1.5px solid var(--border);border-radius:8px">
      </div>
      <button class="btn btn-primary" onclick="addNewCourse()">+ 添加课程</button>
    </div>
    <div style="display:flex;gap:10px;margin-bottom:14px;flex-wrap:wrap">
      <button class="btn btn-outline" onclick="downloadCsv('/api/data/export/courses')">${svgIcon('download')} 导出课程</button>
      <button class="btn btn-outline" onclick="importCsvFile('courses')">${svgIcon('upload')} 导入课程</button>
      <button class="btn btn-ghost btn-sm" onclick="downloadCsv('/api/data/template/courses')">下载模板</button>
    </div>
    <div class="data-table"><table><thead><tr><th>课程</th><th>教师</th><th>类别</th><th>学分</th><th>容量</th><th>校区</th><th>操作</th></tr></thead><tbody>
      ${courses.map(c => `<tr><td><b>${c.name}</b></td><td>${c.teacherName}</td><td><span class="course-cat cat-${c.category}">${c.category}</span></td><td>${c.credit}</td><td>${c.capacity}</td><td>${c.campus || '主校区'}</td><td><button class="btn btn-danger btn-sm" onclick="removeCourse(${c.id})">删除</button></td></tr>`).join('')}
    </tbody></table></div>`;
}

async function addNewCourse() {
  const name = $('#nc-name').value.trim();
  if (!name) { toast('请输入课程名', 'error'); return; }
  const r = await api('POST', '/courses', {
    name, teacherId: parseInt($('#nc-teacher').value), category: $('#nc-cat').value,
    credit: parseInt($('#nc-credit').value) || 3, capacity: parseInt($('#nc-cap').value) || 60,
    time: $('#nc-time').value || '周一 08:00-09:40', location: $('#nc-loc').value || '待定',
    campus: $('#nc-campus') ? $('#nc-campus').value : '主校区', examTime: $('#nc-exam') ? $('#nc-exam').value : null, intro: '管理员添加的课程。',
  });
  toast(r.message, r.success ? 'success' : 'error');
  if (r.success) navigate('course-mgr');
}

async function removeCourse(id) {
  if (!await confirmAsync('删除课程', '确定删除该课程？', 'danger')) return;
  const r = await api('DELETE', `/courses/${id}`);
  toast(r.message, 'success'); navigate('course-mgr');
}

async function renderUserMgr() {
  const r = await api('GET', '/admin/users');
  let users = r.success ? r.data : [];
  const user = getUser();
  let html = `
    <div style="display:flex;gap:10px;margin-bottom:14px;flex-wrap:wrap">
      <select id="uf-role" style="padding:8px 12px;border:1.5px solid var(--border);border-radius:8px">
        <option value="">全部角色</option>
        <option value="teacher">教师</option>
        <option value="student">学生</option>
        <option value="admin">管理员</option>
      </select>
      <input id="uf-search" type="text" placeholder="搜索姓名/账号" style="flex:1;min-width:180px;padding:8px 12px;border:1.5px solid var(--border);border-radius:8px">
      <button class="btn btn-primary" onclick="openAdminUserModal('teacher')">${svgIcon('plus')} 新增教师</button>
      <button class="btn btn-ghost" onclick="openAdminUserModal('student')">${svgIcon('plus')} 新增学生</button>
      <button class="btn btn-danger btn-sm" style="padding:8px 14px" onclick="resetAllPwd()">${svgIcon('wrench')} 批量重置密码(123456)</button>
      <span style="flex:1"></span>
      <button class="btn btn-outline" onclick="downloadCsv('/api/data/export/students')">${svgIcon('download')} 导出学生</button>
      <button class="btn btn-outline" onclick="importCsvFile('students')">${svgIcon('upload')} 导入学生</button>
      <button class="btn btn-ghost btn-sm" onclick="downloadCsv('/api/data/template/students')">下载模板</button>
    </div>
    <div class="data-table"><table><thead><tr>
      <th>头像</th><th>姓名</th><th>账号</th><th>角色</th><th>学院</th><th>手机号</th><th>操作</th>
    </tr></thead><tbody>
    ${users.map(u => `
      <tr data-role="${u.role}" data-name="${u.name}" data-no="${u.no}">
        <td>${avatarHtml(u.avatar, u.role)}</td>
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
}

function emptyHtml(name, text) {
  return `<div class="empty-state"><div class="empty-icon">${svgIcon(name)}</div><p>${text}</p></div>`;
}


// ===== 个人中心 / 账号设置 / 头像 =====

const AVATAR_EMOJIS = ['👤','👨','👩','🧑','👨‍🎓','👩‍🎓','🧑‍🎓','👨‍🏫','👩‍🏫','🧑‍🏫','👑','🥷','🧙','🧝','🧚','🦸','🦹','💂','🕵️','👷','🥗','🦊','🐱','🐶','🐼','🐨','🐯','🦁','🐸','🐙','🦄','🐳','🦈','🌞','🌙','⭐','🔥','💎','🎨','🎮','🎯','🚀','🎯','🏆','⚽','🎸','💻','📚','💡','🔮','🍀','🌈','🌸','🌺','🍎','🍕','🎂','☕','🍜','🎵','📱','💼','🎒','🧸'];


function closeModal(id) {
  if (!id) {
    // 无参数：关闭最近显示的 modal-mask
    const masks = document.querySelectorAll('.modal-mask');
    for (let i = masks.length - 1; i >= 0; i--) {
      if (getComputedStyle(masks[i]).display !== 'none') { masks[i].style.display = 'none'; return; }
    }
    return;
  }
  const el = document.getElementById(id);
  if (el) el.style.display = 'none';
}

/** 通用动态弹窗：创建一个新的 modal-mask 追加到 body，返回本次创建的 mask DOM。
 *  后续用 $('.modal-mask:last-of-type .modal-body') 写入表单内容。 */
function showModal(title, opts = {}) {
  const maxW = opts.maxWidth || 460;
  const mask = document.createElement('div');
  mask.className = 'modal-mask';
  mask.style.cssText = 'display:flex;align-items:center;justify-content:center;z-index:' + (10000 + document.querySelectorAll('.modal-mask').length);
  mask.innerHTML = `
    <div class="modal" style="max-width:${maxW}px">
      <div class="modal-header"><h3>${title}</h3><button class="modal-close" onclick="this.closest('.modal-mask').remove()"><svg class="icon"><use href="#i-x"/></svg></button></div>
      <div class="modal-body"></div>
      <div class="modal-footer"></div>
    </div>`;
  // 初始隐藏 footer，有内容显示
  document.body.appendChild(mask);
  mask._dynamic = true;
  return mask;
}

// 点遮罩关闭
document.addEventListener('click', (e) => {
  if (e.target.classList && e.target.classList.contains('modal-mask')) {
    if (e.target._dynamic) e.target.remove();
    else e.target.style.display = 'none';
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
  closeAllDropdowns();
  (function () {
    const pav = user.avatar || '';
    const pel = document.getElementById('profile-avatar-preview');
    if (pav.indexOf('data:') === 0) {
      pel.innerHTML = '<img src="' + pav + '" style="width:100%;height:100%;object-fit:cover;border-radius:50%">';
    } else {
      pel.textContent = pav || '👤';
    }
  })();
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
  closeAllDropdowns();
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
  if (current.startsWith('data:')) {
    prev.innerHTML = '<img src="' + current + '" style="width:100%;height:100%;object-fit:cover;border-radius:50%">';
    prev.style.background = '';
  } else {
    prev.textContent = current;
    prev.style.background = 'linear-gradient(135deg,#4f46e5,#7c3aed)';
  }
  closeAllDropdowns();
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
    updateTopbarAvatar(user);
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
    role === 'teacher' ? '新增教师账号' : role === 'admin' ? '新增管理员' : '新增学生';
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
initApp();

// ============================================================

// ============================================================
// 新增页面：选课学生、成绩管理、学期管理、教室管理、系统日志
// ============================================================

// ---- 老师：选课学生列表（含录入成绩） ----
async function renderStudentList() {
  const user = getUser();
  const courseR = await api('GET', '/courses/teacher/' + user.id);
  const courses = courseR.success ? courseR.data : [];
  let html = '<div class="page-hero">' +
    '<div class="page-hero-icon">' + svgIcon('users') + '</div>' +
    '<div class="page-hero-text"><div class="page-hero-title">选课学生</div>' +
    '<div class="page-hero-sub">选择课程查看报名学生名单，可直接录入成绩</div></div>' +
    '<div class="page-hero-count">' + courses.length + '<span>门课程</span></div></div>' +
    '<div class="filter-bar select-bar"><div class="search-box select-box">' + svgIcon('book') +
    '<select id="sl-course-filter"><option value="">请选择课程…</option>';
  courses.forEach(c => { html += '<option value="' + c.id + '">' + c.name + '</option>'; });
  html += '</select></div></div><div id="sl-list">' + emptyHtml('users', '在上方选择一门课程，即可查看选课学生名单') + '</div>';
  $('#content').innerHTML = html;
  $('#sl-course-filter').addEventListener('change', async (e) => {
    const cid = e.target.value;
    if (!cid) { $('#sl-list').innerHTML = emptyHtml('users', '在上方选择一门课程，即可查看选课学生名单'); return; }
    const r = await api('GET', '/enrollments/course/' + cid);
    const list = r.success ? r.data.filter(s => s.status === 'approved' || s.status === 'pending') : [];
    if (!list.length) { $('#sl-list').innerHTML = emptyHtml('users', '暂无选课学生'); return; }
    let rows = '';
    list.forEach(s => {
      rows += '<tr><td>' + s.studentNo + '</td><td>' + s.studentName + '</td><td>' + (s.studentMajor||'') + '</td><td>' + (s.grade||'-') + '</td><td>' + (s.studentClass||'-') + '</td><td>' + (s.studentPhone||'-') + '</td><td>' + s.status + '</td><td><button class="btn btn-primary btn-sm" onclick="openGradeModal(' + cid + ',' + s.studentId + ',\'' + s.studentName + '\',\'' + s.studentNo + '\')">录入成绩</button></td></tr>';
    });
    $('#sl-list').innerHTML = '<div class="data-table"><table><thead><tr><th>学号</th><th>姓名</th><th>专业</th><th>年级</th><th>班级</th><th>联系电话</th><th>状态</th><th>操作</th></tr></thead><tbody>' + rows + '</tbody></table></div>';
  });
}

function openGradeModal(courseId, studentId, studentName, studentNo) {
  const modal = document.createElement('div');
  modal.className = 'modal-mask';
  modal.innerHTML = '<div class="modal"><h3>录入成绩</h3>' +
    '<div class="modal-sub">学号：' + studentNo + '　姓名：' + studentName + '</div>' +
    '<div class="form-group"><label>平时成绩</label><input type="number" id="g-regular" min="0" max="100" placeholder="0-100"></div>' +
    '<div class="form-group"><label>期末成绩</label><input type="number" id="g-final" min="0" max="100" placeholder="0-100"></div>' +
    '<div class="form-group"><label>总评成绩（自动计算）</label><input type="text" id="g-total" readonly placeholder="平时×40% + 期末×60%"></div>' +
    '<div class="modal-actions"><button class="btn btn-ghost" id="g-cancel">取消</button><button class="btn btn-primary" id="g-save">确定</button></div></div>';
  document.body.appendChild(modal);
  const calc = () => {
    const r = parseFloat($('#g-regular').value) || 0;
    const f = parseFloat($('#g-final').value) || 0;
    $('#g-total').value = (r * 0.4 + f * 0.6).toFixed(1);
  };
  $('#g-regular').addEventListener('input', calc);
  $('#g-final').addEventListener('input', calc);
  $('#g-cancel').onclick = () => modal.remove();
  modal.onclick = (e) => { if (e.target === modal) modal.remove(); };
  $('#g-save').onclick = async () => {
    const r = await api('POST', '/grades', {
      studentId: studentId, courseId: courseId,
      regularScore: parseFloat($('#g-regular').value) || null,
      finalScore: parseFloat($('#g-final').value) || null,
    });
    toast(r.message, r.success ? 'success' : 'error');
    modal.remove();
    if (r.success) navigate(state.currentPage);
  };
}

// ---- 老师：成绩管理 ----
async function renderGradeMgr() {
  const user = getUser();
  const courseR = await api('GET', '/courses/teacher/' + user.id);
  const courses = courseR.success ? courseR.data : [];
  let html = '<div class="page-hero">' +
    '<div class="page-hero-icon amber">' + svgIcon('edit') + '</div>' +
    '<div class="page-hero-text"><div class="page-hero-title">成绩管理</div>' +
    '<div class="page-hero-sub">选择课程查看学生成绩，支持录入与修改</div></div>' +
    '<div class="page-hero-count">' + courses.length + '<span>门课程</span></div></div>' +
    '<div class="filter-bar select-bar"><div class="search-box select-box">' + svgIcon('book') +
    '<select id="gm-course-filter"><option value="">请选择课程…</option>';
  courses.forEach(c => { html += '<option value="' + c.id + '">' + c.name + '</option>'; });
  html += '</select></div></div><div id="gm-list">' + emptyHtml('edit', '在上方选择一门课程，即可查看成绩记录') + '</div>';
  $('#content').innerHTML = html;
  $('#gm-course-filter').addEventListener('change', async (e) => {
    const cid = e.target.value;
    if (!cid) { $('#gm-list').innerHTML = emptyHtml('edit', '在上方选择一门课程，即可查看成绩记录'); return; }
    const r = await api('GET', '/grades/course/' + cid);
    const list = r.success ? r.data : [];
    if (!list.length) { $('#gm-list').innerHTML = emptyHtml('edit', '暂无成绩记录'); return; }
    let rows = '';
    list.forEach(s => {
      rows += '<tr><td>' + s.studentNo + '</td><td>' + s.studentName + '</td><td>' + (s.studentMajor||'') + '</td><td>' + (s.studentClass||'-') + '</td><td>' + (s.regularScore != null ? s.regularScore : '-') + '</td><td>' + (s.finalScore != null ? s.finalScore : '-') + '</td><td>' + (s.totalScore != null ? s.totalScore : '-') + '</td><td><button class="btn btn-primary btn-sm" onclick="openGradeModal(' + cid + ',' + s.studentId + ',\'' + s.studentName + '\',\'' + s.studentNo + '\')">编辑</button></td></tr>';
    });
    $('#gm-list').innerHTML = '<div class="data-table"><table><thead><tr><th>学号</th><th>姓名</th><th>专业</th><th>班级</th><th>平时</th><th>期末</th><th>总评</th><th>操作</th></tr></thead><tbody>' + rows + '</tbody></table></div>';
  });
}

// ---- 管理员：学期管理 ----
async function renderSemesterMgr() {
  const r = await api('GET', '/semesters');
  const list = r.success ? r.data : [];
  let rows = '';
  list.forEach(s => {
    rows += '<tr><td>' + s.id + '</td><td><b>' + s.name + '</b></td><td>' + (s.startDate||'-') + '</td><td>' + (s.selectionStart||'-') + '</td><td>' + (s.selectionEnd||'-') + '</td><td>' + s.status + '</td><td>' + (s.isCurrent ? svgIcon('check-circle') : '') + '</td><td><button class="btn btn-danger btn-sm" onclick="delSemester(' + s.id + ')">删除</button></td></tr>';
  });
  $('#content').innerHTML = '<div class="filter-bar"><button class="btn btn-primary btn-sm" id="add-semester">+ 新增学期</button></div>' +
    (list.length ? '<div class="data-table"><table><thead><tr><th>ID</th><th>学期名称</th><th>开始日期</th><th>选课开始</th><th>选课结束</th><th>状态</th><th>当前学期</th><th>操作</th></tr></thead><tbody>' + rows + '</tbody></table></div>' : emptyHtml('calendar', '暂无学期数据'));
  $('#add-semester').onclick = () => openSemesterModal();
}

function openSemesterModal() {
  const modal = document.createElement('div');
  modal.className = 'modal-mask';
  modal.innerHTML = '<div class="modal"><h3>新增学期</h3>' +
    '<div class="form-group"><label>学期名称</label><input type="text" id="sm-name"></div>' +
    '<div class="form-group"><label>开始日期</label><input type="date" id="sm-start"></div>' +
    '<div class="form-group"><label>选课开始</label><input type="date" id="sm-sel-start"></div>' +
    '<div class="form-group"><label>选课结束</label><input type="date" id="sm-sel-end"></div>' +
    '<div class="form-group"><label><input type="checkbox" id="sm-current"> 设为当前学期</label></div>' +
    '<div class="modal-actions"><button class="btn btn-ghost" id="sm-cancel">取消</button><button class="btn btn-primary" id="sm-save">保存</button></div></div>';
  document.body.appendChild(modal);
  $('#sm-cancel').onclick = () => modal.remove();
  modal.onclick = (e) => { if (e.target === modal) modal.remove(); };
  $('#sm-save').onclick = async () => {
    const data = {
      name: $('#sm-name').value.trim(),
      startDate: $('#sm-start').value || null,
      selectionStart: $('#sm-sel-start').value || null,
      selectionEnd: $('#sm-sel-end').value || null,
      isCurrent: $('#sm-current').checked,
      status: '未开始',
    };
    const r = await api('POST', '/semesters', data);
    toast(r.message, r.success ? 'success' : 'error');
    modal.remove();
    if (r.success) navigate('semesters');
  };
}

async function delSemester(id) {
  if (!await confirmAsync('删除学期', '确定删除该学期？', 'danger')) return;
  const r = await api('DELETE', '/semesters/' + id);
  toast(r.message, r.success ? 'success' : 'error');
  if (r.success) navigate('semesters');
}

// ---- 管理员：教室管理 ----
async function renderClassroomMgr() {
  const r = await api('GET', '/classrooms');
  const list = r.success ? r.data : [];
  let rows = '';
  list.forEach(c => {
    rows += '<tr><td>' + c.roomNo + '</td><td>' + c.name + '</td><td>' + c.capacity + '</td><td>' + (c.location||'') + '</td><td>' + (c.equipment||'') + '</td><td>' + c.status + '</td><td><button class="btn btn-danger btn-sm" onclick="delRoom(' + c.id + ')">删除</button></td></tr>';
  });
  $('#content').innerHTML = '<div class="filter-bar"><button class="btn btn-primary btn-sm" id="add-room">+ 新增教室</button></div>' +
    (list.length ? '<div class="data-table"><table><thead><tr><th>教室编号</th><th>名称</th><th>容量</th><th>位置</th><th>设备</th><th>状态</th><th>操作</th></tr></thead><tbody>' + rows + '</tbody></table></div>' : emptyHtml('building', '暂无教室数据'));
  $('#add-room').onclick = () => openRoomModal();
}

function openRoomModal() {
  const modal = document.createElement('div');
  modal.className = 'modal-mask';
  modal.innerHTML = '<div class="modal"><h3>新增教室</h3>' +
    '<div class="form-group"><label>教室编号</label><input type="text" id="rm-no"></div>' +
    '<div class="form-group"><label>名称</label><input type="text" id="rm-name"></div>' +
    '<div class="form-group"><label>容量</label><input type="number" id="rm-cap"></div>' +
    '<div class="form-group"><label>位置</label><input type="text" id="rm-loc"></div>' +
    '<div class="form-group"><label>设备</label><input type="text" id="rm-equip" placeholder="如：多媒体"></div>' +
    '<div class="form-group"><label>状态</label><select id="rm-status"><option>正常</option><option>维护中</option></select></div>' +
    '<div class="modal-actions"><button class="btn btn-ghost" id="rm-cancel">取消</button><button class="btn btn-primary" id="rm-save">保存</button></div></div>';
  document.body.appendChild(modal);
  $('#rm-cancel').onclick = () => modal.remove();
  modal.onclick = (e) => { if (e.target === modal) modal.remove(); };
  $('#rm-save').onclick = async () => {
    const data = {
      roomNo: $('#rm-no').value.trim(), name: $('#rm-name').value.trim(),
      capacity: parseInt($('#rm-cap').value) || 0, location: $('#rm-loc').value.trim(),
      equipment: $('#rm-equip').value.trim(), status: $('#rm-status').value,
    };
    const r = await api('POST', '/classrooms', data);
    toast(r.message, r.success ? 'success' : 'error');
    modal.remove();
    if (r.success) navigate('classrooms');
  };
}

async function delRoom(id) {
  if (!await confirmAsync('删除教室', '确定删除该教室？', 'danger')) return;
  const r = await api('DELETE', '/classrooms/' + id);
  toast(r.message, r.success ? 'success' : 'error');
  if (r.success) navigate('classrooms');
}

// ---- 管理员：系统日志 ----
async function renderSystemLogs() {
  const [logsR, statsR] = await Promise.all([api('GET', '/logs'), api('GET', '/logs/stats')]);
  const logs = logsR.success ? logsR.data : [];
  const stats = statsR.success ? statsR.data : { byType: {}, byStatus: {}, byUser: [] };

  const typeData = Object.entries(stats.byType || {});
  const total = typeData.reduce((s, kv) => s + kv[1], 0) || 1;

  const trend = hourlyTrend(logs, 12);
  const succ = stats.byStatus['成功'] || 0;
  const fail = stats.byStatus['失败'] || 0;
  const succRate = total ? (succ / (succ + fail || 1) * 100) : 0;
  const recentHalf = trend.slice(6).reduce((a,b)=>a+b,0);
  const prevHalf = trend.slice(0,6).reduce((a,b)=>a+b,0);
  const deltaPct = prevHalf ? ((recentHalf - prevHalf) / prevHalf * 100) : 0;
  const deltaText = (deltaPct >= 0 ? '+' : '') + deltaPct.toFixed(1) + '%';

  const users = (stats.byUser || []).slice(0, 8);
  const maxCnt = users.length ? Math.max(...users.map(u => u.count), 1) : 1;
  const totalOps = users.reduce((s, u) => s + u.count, 0) || 1;
  let barHtml = '';
  users.forEach((u, i) => {
    const pct = Math.max(u.count / maxCnt * 100, 2).toFixed(0);
    const share = (u.count / totalOps * 100).toFixed(1);
    barHtml += '<div class="bar-item" title="' + u.userName + '：' + u.count + ' 次操作（占比 ' + share + '%）">' +
      '<div class="bar-label">' + u.userName + '</div>' +
      '<div class="bar-track"><div class="bar-fill" style="height:' + pct + '%;animation-delay:' + (i * 60) + 'ms"></div></div>' +
      '<div class="bar-value">' + u.count + '</div></div>';
  });

  let logRows = '';
  logs.slice(0, 50).forEach(l => {
    logRows += '<tr><td>' + l.createdAt + '</td><td>' + l.userName + '</td><td>' + l.type + '</td><td>' + l.operation + '</td><td>' + l.status + '</td><td>' + (l.ip||'-') + '</td></tr>';
  });

  $('#content').innerHTML = '<div class="stats-grid">' +
    statCardPro({ icon:'bar-chart', color:'blue', value:logs.length, label:'总操作数', trend:trend, deltaText:deltaText, deltaDir:deltaPct>=0?'up':'down' }) +
    statCardPro({ icon:'check-circle', color:'green', value:succ, label:'成功', trend:hourlyTrend(logs.filter(l=>l.status==='成功'),12), deltaText:succRate.toFixed(1)+'%', deltaDir:'up' }) +
    statCardPro({ icon:'x-circle', color:'red', value:fail, label:'失败', trend:hourlyTrend(logs.filter(l=>l.status==='失败'),12), deltaText:(100-succRate).toFixed(1)+'%', deltaDir:'down' }) +
    '</div>' +
    '<div style="display:grid;grid-template-columns:1fr 1.4fr;gap:20px;margin-top:20px;">' +
    '<div class="card glow-corner"><div class="section-title">操作类型分布</div>' + (typeData.length ? donutHtml(typeData, total, '总操作') : '暂无数据') + '</div>' +
    '<div class="card"><div class="section-title">用户操作排行</div><div class="bar-chart">' + (barHtml || '暂无数据') + '</div></div>' +
    '</div>' +
    '<div class="section" style="margin-top:20px;"><div class="section-header"><div class="section-title">操作日志明细</div></div>' +
    (logs.length ? '<div class="data-table"><table><thead><tr><th>时间</th><th>用户</th><th>类型</th><th>操作</th><th>状态</th><th>IP</th></tr></thead><tbody>' + logRows + '</tbody></table></div>' : emptyHtml('file-text', '暂无日志')) +
    '</div>';
}
// ===== 实时时钟 =====
function updateTimeWidget() {
  var now = new Date();
  var h = String(now.getHours()).padStart(2, "0");
  var m = String(now.getMinutes()).padStart(2, "0");
  var s = String(now.getSeconds()).padStart(2, "0");
  var y = now.getFullYear();
  var mo = String(now.getMonth() + 1).padStart(2, "0");
  var d = String(now.getDate()).padStart(2, "0");
  var weeks = ["日","一","二","三","四","五","六"];
  var w = weeks[now.getDay()];
  var elT = document.getElementById("tw-time");
  if (!elT) return;
  elT.textContent = h + ":" + m + ":" + s;
  document.getElementById("tw-date").textContent = y + "年" + mo + "月" + d + "日";
  document.getElementById("tw-week").textContent = "星期" + w;
}
updateTimeWidget();
setInterval(updateTimeWidget, 1000);

// 通用头像渲染：base64 图片 / emoji 自适应
function avatarHtml(av, role) {
  const fallback = { student: '🎓', teacher: '👨‍🏫', admin: '👑' }[role] || '👤';
  if (av && av.indexOf('data:') === 0) {
    return '<img src="' + av + '" style="width:34px;height:34px;object-fit:cover;border-radius:50%;vertical-align:middle;display:block;margin:0 auto">';
  }
  return '<span style="font-size:20px">' + (av || fallback) + '</span>';
}

function updateTopbarAvatar(user) {
  const el = document.getElementById('user-avatar');
  if (!el) return;
  const av = user.avatar || '';
  if (av.startsWith('data:')) {
    el.innerHTML = '<img src="' + av + '" style="width:100%;height:100%;object-fit:cover;border-radius:50%">';
    el.style.background = '';
    el.style.fontSize = '';
    el.style.color = '';
  } else {
    el.textContent = av || ({student:'🎓',teacher:'👨‍🏫',admin:'👑'}[user.role]||'👤');
    el.style.fontSize = '26px';
    el.style.background = 'linear-gradient(135deg,#4f46e5,#7c3aed)';
    el.style.color = '#fff';
  }
}

async function uploadAvatar(file) {
  if (!file) return;
  console.log('uploadAvatar called, file:', file.name, file.size);
  if (file.size > 2*1024*1024) { toast('图片不能超过 2MB', 'error'); return; }
  try {
    const reader = new FileReader();
    reader.onload = async (e) => {
      try {
        const img = new Image();
        img.onload = async () => {
          try {
            const canvas = document.createElement('canvas');
            canvas.width = 200; canvas.height = 200;
            const ctx = canvas.getContext('2d');
            ctx.drawImage(img, 0, 0, 200, 200);
            const base64 = canvas.toDataURL('image/jpeg', 0.8);
            console.log('base64 length:', base64.length);
            const r = await api('PUT', '/auth/profile', { avatar: base64 });
            if (r.success) {
              toast('头像上传成功', 'success');
              const user = getUser(); Object.assign(user, r.data);
              localStorage.setItem('zhixue_user', JSON.stringify(user));
              updateTopbarAvatar(user);
              const prev = document.getElementById('avatar-current');
              if (prev) { prev.innerHTML = '<img src="' + base64 + '" style="width:100%;height:100%;object-fit:cover;border-radius:50%">'; prev.style.background = ''; }
              closeModal('avatar-mask');
            } else { console.error('API error:', r); toast(r.message || '上传失败', 'error'); }
          } catch(err) { console.error('Canvas/API error:', err); toast('处理图片失败: ' + err.message, 'error'); }
        };
        img.onerror = () => { toast('图片加载失败', 'error'); };
        img.src = e.target.result;
      } catch(err) { console.error('Image load error:', err); toast('图片读取失败', 'error'); }
    };
    reader.onerror = () => { toast('文件读取失败', 'error'); };
    reader.readAsDataURL(file);
  } catch(err) { console.error('uploadAvatar error:', err); toast('上传失败: ' + err.message, 'error'); }
}

function closeAllDropdowns() {
  document.getElementById('user-dropdown')?.classList.remove('show');
  document.getElementById('notif-panel')?.classList.remove('show');
}


// ============================================================
// 候补队列：递补确认弹窗 / 轮询通知 / 倒计时
// ============================================================

const shownWaitConfirm = new Set();

// 轮询：学生递补成功 → 弹窗限时确认（通知）
async function checkWaitlistNotify() {
  const user = getUser();
  if (!user || user.role !== 'student' || !getToken()) return;
  const r = await api('GET', '/waitlist/mine');
  if (!r.success) return;
  const need = r.data.find(w => w.status === 'confirming' && !shownWaitConfirm.has(w.waitlistId));
  if (!need) return;
  shownWaitConfirm.add(need.waitlistId);
  openWaitConfirmModal(need.courseId, need.waitlistId);
}

function refreshCurrentPage() {
  if (state.currentPage) navigate(state.currentPage);
}

// 递补确认弹窗（含倒计时）
async function openWaitConfirmModal(courseId, waitlistId) {
  const mineR = await api('GET', '/waitlist/mine');
  const entry = mineR.success ? mineR.data.find(w => w.waitlistId == waitlistId) : null;
  if (!entry || entry.status !== 'confirming') { toast('候补信息已更新，请刷新页面', 'info'); return; }
  const deadline = entry.deadline;
  if (!deadline) return;
  const modal = document.createElement('div');
  modal.className = 'modal-mask';
  modal.innerHTML = `
    <div class="modal">
      <h3>${svgIcon('party')} 递补成功！</h3>
      <div class="modal-sub">课程「${entry.courseName}」已空出名额，请尽快确认录取</div>
      <div style="text-align:center;margin:16px 0">
        <div style="font-size:40px;font-weight:800;color:var(--primary)" id="wl-cd">--:--</div>
        <div style="font-size:13px;color:var(--text-light)">剩余确认时间，超时将自动释放名额给下一位</div>
      </div>
      <div class="modal-actions">
        <button class="btn btn-ghost" id="wl-decline">放弃名额</button>
        <button class="btn btn-success" id="wl-confirm">${svgIcon('check-circle')} 确认录取</button>
      </div>
    </div>`;
  document.body.appendChild(modal);
  const cd = $('#wl-cd');
  const tick = () => {
    const left = deadline - Date.now();
    if (left <= 0) {
      clearInterval(t);
      modal.remove();
      toast('确认超时，名额已释放', 'error');
      refreshCurrentPage();
      return;
    }
    const s = Math.floor(left / 1000);
    cd.textContent = String(Math.floor(s / 60)).padStart(2, '0') + ':' + String(s % 60).padStart(2, '0');
  };
  tick();
  const t = setInterval(tick, 1000);
  const cleanup = () => { clearInterval(t); modal.remove(); };
  $('#wl-decline').onclick = () => { cleanup(); declineWaitConfirm(courseId); };
  modal.onclick = (e) => { if (e.target === modal) cleanup(); };
  $('#wl-confirm').onclick = async () => {
    const r = await api('POST', '/waitlist/confirm/' + waitlistId);
    toast(r.message, r.success ? 'success' : 'error');
    cleanup();
    if (r.success) navigate(state.currentPage);
    else refreshCurrentPage();
  };
}

// 放弃递补名额（名额给下一位）
async function declineWaitConfirm(courseId) {
  if (!await confirmAsync('放弃名额', '放弃后名额将递补给下一位候补同学，确定放弃？', 'danger')) return;
  const r = await api('POST', '/waitlist/cancel', { courseId: parseInt(courseId) });
  toast(r.message, r.success ? 'success' : 'error');
  if (r.success) navigate(state.currentPage);
}

// 我的课程页倒计时
let wlCountdownTimer = null;
function startWaitlistCountdowns() {
  const els = document.querySelectorAll('.wl-countdown');
  if (!els.length) return;
  if (wlCountdownTimer) clearInterval(wlCountdownTimer);
  const tick = () => {
    let allDone = true;
    els.forEach(el => {
      const dl = parseInt(el.dataset.deadline);
      const left = dl - Date.now();
      if (left <= 0) { el.textContent = '已超时'; el.style.color = 'var(--danger)'; return; }
      allDone = false;
      const s = Math.floor(left / 1000);
      el.textContent = String(Math.floor(s / 60)).padStart(2, '0') + ':' + String(s % 60).padStart(2, '0');
    });
    if (allDone) { clearInterval(wlCountdownTimer); wlCountdownTimer = null; }
  };
  tick();
  wlCountdownTimer = setInterval(tick, 1000);
}


// ============================================================
// 站内消息中心：SSE 实时推送 / 消息面板 / 未读红点
// ============================================================

// 建立 SSE 长连接（实时接收候补递补 / 审批结果 / 新公告）
function connectNotify() {
  disconnectNotify();
  const token = getToken();
  if (!token || !window.EventSource) return;
  try {
    notifyES = new EventSource(API + '/notify/subscribe?token=' + encodeURIComponent(token));
    notifyES.addEventListener('msg', (ev) => {
      let d = {};
      try { d = JSON.parse(ev.data); } catch (e) { return; }
      if (d.type === 'waitlist_confirm') {
        toast(d.title + '：' + d.content, 'success');
        checkWaitlistNotify();
      } else if (d.type === 'enroll') {
        toast((d.title || '选课结果') + '：' + d.content, (d.title || '').indexOf('录取通知') >= 0 ? 'success' : 'info');
      } else if (d.type === 'announcement') {
        toast(d.title, 'info');
      } else {
        toast(d.title || '新消息', 'info');
      }
      loadNotificationsToNotif();
      if (state.currentPage === 'waitlist' || state.currentPage === 'mycourses') refreshCurrentPage();
    });
    // EventSource 断线会自动重连
  } catch (e) { /* 忽略 */ }
}

function disconnectNotify() {
  if (notifyES) { try { notifyES.close(); } catch (e) {} notifyES = null; }
}

// 消息面板：消息列表 + 未读公告入口
async function loadNotificationsToNotif() {
  const box = document.getElementById('notif-list');
  if (!box) return;
  const [mr, ur, ar] = await Promise.all([
    api('GET', '/notify/messages'),
    api('GET', '/notify/unread-count'),
    api('GET', '/announcements?limit=3'),
  ]);
  const msgs = (mr && mr.success) ? mr.data : [];
  const annUnread = (ur && ur.success && ur.data) ? (ur.data.announcement || 0) : 0;
  const anns = (ar && ar.success) ? ar.data : [];
  let html = '';
  if (msgs.length) {
    html += msgs.map(m => '<div class="notif-item' + (m.read ? '' : ' unread') + '" onclick="readNotif(' + m.id + ', this)">' +
      '<div class="notif-item-title">' + (m.title || '') + '</div>' +
      (m.content ? '<div class="notif-item-time">' + m.content + '</div>' : '') +
      '<div class="notif-item-time">' + fmtTime(m.createdAt) + '</div></div>').join('');
  } else {
    html += '<div class="notif-item"><div class="notif-item-title" style="color:var(--text-light)">暂无消息</div></div>';
  }
  if (anns.length) {
    html += '<div class="notif-item notif-ann-link" onclick="openAnnounceList()">' +
      '<div class="notif-item-title">' + svgIcon('megaphone') + ' 系统公告' +
      (annUnread > 0 ? ' · <span style="color:var(--danger)">' + annUnread + ' 条未读</span>' : '') + '</div>' +
      '<div class="notif-item-time">' + anns[0].title + ' · 点击查看全部</div></div>';
  }
  box.innerHTML = html;
  refreshNotifDot();
}

async function readNotif(id, el) {
  await api('POST', '/notify/read/' + id);
  if (el) el.classList.remove('unread');
  refreshNotifDot();
}

async function markAllNotifRead() {
  await api('POST', '/notify/read-all');
  await api('POST', '/announcements/read-all');
  document.querySelectorAll('.notif-item.unread').forEach(el => el.classList.remove('unread'));
  refreshNotifDot();
  if (state.currentPage === 'dashboard' || state.currentPage === 'announce') refreshCurrentPage();
}

// 顶栏铃铛未读红点（消息 + 未读公告）
async function refreshNotifDot() {
  const dot = document.getElementById('notif-dot');
  if (!dot) return;
  const r = await api('GET', '/notify/unread-count');
  let n = 0;
  if (r && r.success && r.data) n = (r.data.count || 0) + (r.data.announcement || 0);
  dot.style.display = n > 0 ? 'block' : 'none';
}

// ============================================================
// 候补队列页：位次 / 预计递补时间 / 限时确认
// ============================================================
async function renderWaitlist() {
  const r = await api('GET', '/waitlist/mine');
  const list = (r && r.success) ? r.data : [];
  const confirming = list.filter(w => w.status === 'confirming');
  const waiting = list.filter(w => w.status === 'waiting');
  const card = (w) => {
    const isConf = w.status === 'confirming';
    return '<div class="wl-card' + (isConf ? ' wl-confirm-card' : '') + '">' +
      '<div class="wl-card-top"><div class="wl-course">' + w.courseName + '</div>' +
      '<span class="status-tag ' + (isConf ? 'status-pending' : 'status-approved') + '">' + (isConf ? '待确认录取' : '候补中') + '</span></div>' +
      '<div class="wl-meta">' + svgIcon('user') + ' ' + w.teacherName + ' · ' + w.credit + ' 学分</div>' +
      '<div class="wl-pos">第 <b>' + w.position + '</b> / ' + w.queueTotal + ' 位</div>' +
      (isConf
        ? '<div class="wl-meta">请在 <span class="wl-countdown" data-deadline="' + w.deadline + '">--:--</span> 前确认，超时名额将释放</div>'
        : '<div class="wl-meta">' + svgIcon('clock') + ' 预计递补：' + fmtEstWait(w.estimatedWait) + '</div>') +
      '<div class="wl-acts">' +
      (isConf
        ? '<button class="btn btn-success btn-sm" data-action="wait-confirm" data-id="' + w.courseId + '" data-wid="' + w.waitlistId + '">确认录取</button>' +
          '<button class="btn btn-ghost btn-sm" data-action="wait-cancel" data-id="' + w.courseId + '">放弃名额</button>'
        : '<button class="btn btn-danger btn-sm" data-action="wait-cancel" data-id="' + w.courseId + '">退出候补</button>') +
      '</div></div>';
  };
  $('#content').innerHTML = `
    <div class="stats-grid">
      <div class="stat-card"><div class="stat-icon amber">${svgIcon('hourglass')}</div><div><div class="stat-value">${waiting.length}</div><div class="stat-label">候补中</div></div></div>
      <div class="stat-card"><div class="stat-icon green">${svgIcon('party')}</div><div><div class="stat-value">${confirming.length}</div><div class="stat-label">待确认录取</div></div></div>
      <div class="stat-card"><div class="stat-icon blue">${svgIcon('clipboard')}</div><div><div class="stat-value">${list.length}</div><div class="stat-label">候补课程</div></div></div>
    </div>
    <div class="section"><div class="section-header"><div class="section-title">${svgIcon('hourglass')} 我的候补队列</div>
      <a class="section-link" onclick="navigate('browse')">去选课 →</a></div>
      ${list.length ? '<div class="wl-grid">' + list.map(card).join('') + '</div>' : emptyHtml('hourglass', '暂无候补课程。课程满员时可加入候补，有人退课将按顺序递补')}
      <div style="margin-top:10px;font-size:12px;color:var(--text-light)">递补成功后将实时通知你（顶栏消息中心），请在限时内确认录取。</div>
    </div>`;
  bindCourseActions();
  startWaitlistCountdowns();
}


// ===== 通用：结果弹窗 / 时间格式化 / 小工具 =====
function closeConfirmMask() {
  const m = document.getElementById('confirm-mask');
  m.style.display = 'none';
  m.innerHTML = '';
}
function showResultModal(title, subtitle, bodyHtml) {
  const mask = document.getElementById('confirm-mask');
  mask.innerHTML = '<div class="confirm-mask" id="confirm-inner"><div class="confirm-box result-box">' +
    '<div class="confirm-title">' + title + '</div>' +
    (subtitle ? '<div class="confirm-msg">' + subtitle + '</div>' : '') +
    '<div class="result-body">' + (bodyHtml || '') + '</div>' +
    '<div class="confirm-actions"><button class="btn btn-primary" id="confirm-ok">知道了</button></div>' +
    '</div></div>';
  mask.style.display = 'block';
  document.getElementById('confirm-ok').onclick = closeConfirmMask;
}
function fmtTime(ts) {
  if (!ts) return '';
  const d = new Date(ts);
  const p = (n) => String(n).padStart(2, '0');
  return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate()) + ' ' + p(d.getHours()) + ':' + p(d.getMinutes());
}
function miniStars(avg) {
  const n = Math.round(avg || 0);
  let h = '';
  for (let i = 0; i < 5; i++) h += '<span class="mini-star' + (i < n ? ' on' : '') + '">' + svgIcon('star') + '</span>';
  return h;
}
function fmtEstWait(ms) {
  if (!ms) return '暂无足够数据';
  const h = ms / 3600000;
  if (h < 1) return '约 ' + Math.max(1, Math.round(ms / 60000)) + ' 分钟';
  if (h < 48) return '约 ' + Math.round(h) + ' 小时';
  return '约 ' + Math.round(h / 24) + ' 天';
}

// ===== 预选意向单 =====
async function renderIntents() {
  const [r, pointsR] = await Promise.all([api('GET', '/intents/mine'), api('GET', '/enrollments/points')]);
  const list = r.success ? r.data : [];
  const remaining = (pointsR && pointsR.success) ? pointsR.data.remaining : 0;
  const total = list.reduce((s, it) => s + (it.desiredPoints || 0), 0);
  $('#content').innerHTML = `
    <div class="stats-grid">
      <div class="stat-card"><div class="stat-icon purple">${svgIcon('cart')}</div><div><div class="stat-value">${list.length}</div><div class="stat-label">意向课程</div></div></div>
      <div class="stat-card"><div class="stat-icon blue">${svgIcon('coins')}</div><div><div class="stat-value">${total}</div><div class="stat-label">预计投入积分</div></div></div>
      <div class="stat-card"><div class="stat-icon amber">${svgIcon('coins')}</div><div><div class="stat-value">${remaining}</div><div class="stat-label">本轮剩余积分</div></div></div>
    </div>
    <div class="section">
      <div class="section-header">
        <div class="section-title">${svgIcon('cart')} 意向单（${list.length} 门）</div>
        <div style="display:flex;gap:8px">
          <button class="btn btn-ghost btn-sm" id="intent-clear" ${list.length ? '' : 'disabled'}>${svgIcon('trash')} 清空</button>
          <button class="btn btn-primary btn-sm" id="intent-submit" ${list.length ? '' : 'disabled'}>${svgIcon('check-circle')} 一键转正式选课</button>
        </div>
      </div>
      ${list.length ? `<div class="data-table"><table><thead><tr><th>课程</th><th>类别</th><th>教师</th><th>学分</th><th>时间 / 地点</th><th>投入积分</th><th>操作</th></tr></thead><tbody>
        ${list.map(it => `<tr>
          <td><b>${it.courseName}</b></td>
          <td><span class="course-cat cat-${it.category}">${it.category}</span></td>
          <td>${it.teacherName}</td>
          <td>${it.credit}</td>
          <td style="font-size:13px;color:var(--text-light)">${(it.time || '').split('/')[0].trim()} · ${it.location || ''}</td>
          <td><input class="intent-points" type="number" min="1" value="${it.desiredPoints || 10}" data-course="${it.courseId}" style="width:74px;padding:5px 8px;border:1.5px solid var(--border);border-radius:6px;background:var(--card);color:var(--text)"></td>
          <td><div style="display:flex;gap:6px"><button class="btn btn-primary btn-sm" data-intent-convert="${it.courseId}">转选课</button><button class="btn btn-danger btn-sm" data-intent-remove="${it.courseId}">移除</button></div></td>
        </tr>`).join('')}
      </tbody></table></div>
      <div style="margin-top:10px;font-size:12px;color:var(--text-light)">「转选课」单门直接提交为待审批；「一键转正式选课」按上表积分依次提交：未满员直接录取，已满员或积分不足会提示失败。</div>`
      : emptyHtml('cart', '意向单是空的，去「课程浏览」把想选的课加入意向单吧')}
    </div>`;

  const submitBtn = document.getElementById('intent-submit');
  if (submitBtn) submitBtn.addEventListener('click', submitIntents);
  const clearBtn = document.getElementById('intent-clear');
  if (clearBtn) clearBtn.addEventListener('click', async () => {
    if (!await confirmAsync('清空意向单', '确定清空全部意向课程？', 'danger')) return;
    const rr = await api('POST', '/intents/clear', {});
    toast(rr.message, rr.success ? 'success' : 'error');
    if (rr.success) navigate('intents');
  });
  $$('[data-intent-convert]').forEach(b => b.addEventListener('click', async () => {
    const courseId = parseInt(b.dataset.intentConvert);
    const item = list.find(x => x.courseId === courseId);
    if (!await confirmAsync('转正式选课', '将「' + (item ? item.courseName : '') + '」提交为正式选课（待审批）？', 'info')) return;
    b.disabled = true;
    const rr = await api('POST', '/intents/convert', { courseId: courseId });
    toast(rr.message, rr.success ? 'success' : 'error');
    if (rr.success) navigate('intents'); else b.disabled = false;
  }));

  $$('[data-intent-remove]').forEach(b => b.addEventListener('click', async () => {
    const rr = await api('POST', '/intents/remove', { courseId: parseInt(b.dataset.intentRemove) });
    toast(rr.message, rr.success ? 'success' : 'error');
    if (rr.success) navigate('intents');
  }));
  $$('.intent-points').forEach(inp => inp.addEventListener('change', async () => {
    const rr = await api('POST', '/intents/add', { courseId: parseInt(inp.dataset.course), points: parseInt(inp.value) || 10 });
    if (rr.success) toast('投入积分已更新为 ' + (parseInt(inp.value) || 10) + ' 分', 'success');
    else toast(rr.message, 'error');
  }));
}

async function submitIntents() {
  if (!await confirmAsync('一键转正式选课', '将按意向单依次提交所有课程为正式选课（待审批），确定继续？', 'info')) return;
  const r = await api('POST', '/intents/submit', {});
  if (!r.success) { toast(r.message, 'error'); return; }
  const d = r.data || {};
  const details = d.details || [];
  const rows = details.map(x => '<tr><td>' + x.courseName + '</td><td>' + x.points + ' 分</td>' +
      '<td><span class="status-tag ' + (x.success ? 'status-approved' : 'status-rejected') + '">' + (x.success ? '成功' : '失败') + '</span></td>' +
      '<td style="font-size:12px;color:var(--text-light)">' + (x.msg || '') + '</td></tr>').join('');
  showResultModal('提交结果', '成功 ' + (d.okCount || 0) + ' 门，失败 ' + (d.failCount || 0) + ' 门',
    '<div class="data-table"><table><thead><tr><th>课程</th><th>积分</th><th>结果</th><th>说明</th></tr></thead><tbody>' +
    (rows || '<tr><td colspan="4">无意向课程</td></tr>') + '</tbody></table></div>');
  renderRoundInfo();
}

// ===== 课程评价 =====
async function openReviewModal(courseId, courseName) {
  const r = await api('GET', '/reviews/course/' + courseId);
  const d = (r && r.success) ? r.data : { list: [], avg: 0, count: 0 };
  const list = d.list || [];
  const mine = list.find(x => x.mine);
  const me = getUser() || {};
  const canReview = me.role === 'student';
  const stars = (n) => { let h = ''; for (let i = 1; i <= 5; i++) h += '<button type="button" class="star-btn' + (i <= n ? ' on' : '') + '" data-star="' + i + '">' + svgIcon('star') + '</button>'; return h; };
  const dimRow = (key, label, init) => '<div class="dim-row"><div class="dim-label">' + label + '</div>' +
      '<div class="star-row dim-stars" id="star-' + key + '" data-rating="' + (init || 0) + '">' + stars(init || 0) + '</div></div>';
  const dimLine = (x) => {
    const parts = [];
    if (x.teaching != null) parts.push('教学 ' + x.teaching);
    if (x.contentScore != null) parts.push('内容 ' + x.contentScore);
    if (x.homework != null) parts.push('作业 ' + x.homework);
    return parts.length ? '<div class="review-dims">' + parts.join(' · ') + '</div>' : '';
  };
  const listHtml = list.length ? list.map(x =>
      '<div class="review-item"><div class="review-head"><span class="review-who">' + x.studentName + '</span>' +
      (x.anonymous ? '<span class="review-anon">匿名</span>' : '') +
      '<span class="review-stars">' + miniStars(x.rating) + '</span>' +
      '<span class="review-time">' + fmtTime(x.createdAt) + '</span></div>' +
      dimLine(x) +
      (x.content ? '<div class="review-text">' + x.content + '</div>' : '') + '</div>').join('')
    : '<div style="color:var(--text-light);font-size:13px;padding:8px 0">还没有人评价这门课</div>';

  const mask = document.getElementById('confirm-mask');
  mask.innerHTML = '<div class="confirm-mask" id="confirm-inner"><div class="confirm-box review-box">' +
    '<div class="confirm-title">' + (canReview ? '评价《' + courseName + '》' : '《' + courseName + '》的评价') + '</div>' +
    '<div class="review-avg">平均 ' + (d.avg || 0) + ' 分（教学 ' + (d.teachingAvg || 0) + ' · 内容 ' + (d.contentAvg || 0) + ' · 作业 ' + (d.homeworkAvg || 0) + '）· 共 ' + (d.count || 0) + ' 条评价</div>' +
    (canReview ? (
      dimRow('overall', '总体评分', mine ? mine.rating : 0) +
      dimRow('teaching', '教学质量', mine ? mine.teaching : 0) +
      dimRow('contentScore', '课程内容', mine ? mine.contentScore : 0) +
      dimRow('homework', '作业适量', mine ? mine.homework : 0) +
      '<label class="anon-label"><input type="checkbox" id="review-anon"' + (mine && mine.anonymous ? ' checked' : '') + '> 匿名评价（其他同学看不到你的姓名）</label>' +
      '<textarea id="review-text" placeholder="说说这门课的上课体验（选填）">' + (mine && mine.content ? mine.content : '') + '</textarea>') : '') +
    '<div class="review-list">' + listHtml + '</div>' +
    '<div class="confirm-actions">' +
    '<button class="btn btn-ghost" id="review-cancel">关闭</button>' +
    (canReview ? '<button class="btn btn-primary" id="review-submit">' + (mine ? '更新评价' : '提交评价') + '</button>' : '') +
    '</div></div></div>';
  mask.style.display = 'block';

  const dimVal = (key) => {
    const row = document.getElementById('star-' + key);
    return row ? (parseInt(row.dataset.rating) || 0) : 0;
  };
  $$('.dim-stars').forEach(starRow => {
    starRow.querySelectorAll('.star-btn').forEach(btn => btn.addEventListener('click', () => {
      const n = parseInt(btn.dataset.star);
      starRow.dataset.rating = n;
      starRow.querySelectorAll('.star-btn').forEach((b, i) => b.classList.toggle('on', i < n));
    }));
  });
  document.getElementById('review-cancel').onclick = closeConfirmMask;
  const submitBtn = document.getElementById('review-submit');
  if (submitBtn) submitBtn.onclick = async () => {
    const rating = dimVal('overall');
    if (!rating) { toast('请先选择总体评分', 'error'); return; }
    const content = document.getElementById('review-text').value.trim();
    const anonEl = document.getElementById('review-anon');
    const rr = await api('POST', '/reviews', {
      courseId: parseInt(courseId), rating: rating, content: content,
      teaching: dimVal('teaching') || null,
      contentScore: dimVal('contentScore') || null,
      homework: dimVal('homework') || null,
      anonymous: anonEl ? anonEl.checked : false,
    });
    toast(rr.message, rr.success ? 'success' : 'error');
    if (rr.success) { closeConfirmMask(); navigate(state.currentPage); }
  };
}

async function renderTeacherReviews() {
  const user = getUser();
  const r = await api('GET', '/reviews/teacher/' + user.id);
  const d = (r && r.success) ? r.data : { courses: [], totalCount: 0, totalAvg: 0 };
  const courses = d.courses || [];
  $('#content').innerHTML = `
    <div class="stats-grid">
      <div class="stat-card"><div class="stat-icon amber">${svgIcon('star')}</div><div><div class="stat-value">${d.totalAvg || 0}</div><div class="stat-label">平均评分</div></div></div>
      <div class="stat-card"><div class="stat-icon blue">${svgIcon('message-square')}</div><div><div class="stat-value">${d.totalCount || 0}</div><div class="stat-label">收到评价</div></div></div>
      <div class="stat-card"><div class="stat-icon purple">${svgIcon('book-open')}</div><div><div class="stat-value">${courses.length}</div><div class="stat-label">我的课程</div></div></div>
    </div>
    <div class="section"><div class="section-header"><div class="section-title">${svgIcon('star')} 各课程评分</div></div>
      ${courses.length ? `<div class="data-table"><table><thead><tr><th>课程</th><th>综合</th><th>教学</th><th>内容</th><th>作业</th><th>评价数</th><th>最新评价</th><th>操作</th></tr></thead><tbody>
        ${courses.map(c => `<tr>
          <td><b>${c.courseName}</b></td>
          <td>${c.count ? miniStars(c.avg) + ' <b>' + c.avg + '</b>' : '<span style="color:var(--text-light)">暂无</span>'}</td>
          <td>${c.count ? c.teachingAvg : '—'}</td>
          <td>${c.count ? c.contentAvg : '—'}</td>
          <td>${c.count ? c.homeworkAvg : '—'}</td>
          <td>${c.count}</td>
          <td style="font-size:12px;color:var(--text-light)">${c.latest && c.latest.content ? (c.latest.content.length > 28 ? c.latest.content.slice(0, 28) + '…' : c.latest.content) : '—'}</td>
          <td><button class="btn btn-outline btn-sm" data-review-view="${c.courseId}" data-name="${c.courseName}">查看</button></td>
        </tr>`).join('')}
      </tbody></table></div>` : emptyHtml('message-square', '暂无教授课程')}
      <div style="margin-top:10px;font-size:12px;color:var(--text-light)">学生的评价默认以「姓氏 + 同学」形式展示；学生可勾选完全匿名（显示为「匿名同学」）。</div>
    </div>`;
  $$('[data-review-view]').forEach(b => b.addEventListener('click', () => openReviewModal(b.dataset.reviewView, b.dataset.name)));
}

// ===== CSV 导入 / 导出 =====
function downloadCsv(path) {
  const a = document.createElement('a');
  a.href = path;
  a.setAttribute('download', '');
  document.body.appendChild(a);
  a.click();
  setTimeout(() => a.remove(), 200);
  toast('已开始下载', 'success');
}

function importCsvFile(type) {
  const input = document.createElement('input');
  input.type = 'file';
  input.accept = '.csv,text/csv';
  input.onchange = async () => {
    const file = input.files && input.files[0];
    if (!file) return;
    const fd = new FormData();
    fd.append('file', file);
    try {
      const res = await fetch('/api/data/import/' + type, {
        method: 'POST',
        headers: { Authorization: 'Bearer ' + (getToken() || '') },
        body: fd,
      });
      const r = await res.json();
      const d = r.data || {};
      const errs = (d.errors || []).slice(0, 12);
      const body = (errs.length
          ? '<div style="font-size:12px;color:var(--danger);line-height:1.7">' + errs.join('<br>') + '</div>'
          : '<div style="font-size:13px;color:var(--text-light)">全部数据行均处理成功</div>')
        + '<div style="margin-top:10px;font-size:13px">新增 <b>' + (d.created || 0) + '</b> · 更新 <b>'
        + (d.updated || 0) + '</b> · 跳过 <b>' + (d.skipped || 0) + '</b></div>';
      showResultModal(r.success ? '导入完成' : '导入失败', r.message || '', body);
      if (r.success) navigate(state.currentPage);
    } catch (e) {
      toast('导入失败：' + e.message, 'error');
    }
  };
  input.click();
}


// ===== 智能选课推荐结果 =====
function renderRecommendResult(recommended, rejected) {
  const box = document.getElementById('rec-result');
  if (!box) return;
  const candMap = window._schedCandMap || new Map();
  const candInfo = (id) => candMap.get(id) || null;
  const statOf = (id) => {
    const c = candInfo(id);
    if (!c || !c.stat) return '—';
    return (c.stat.total || 0) + '/' + (c.capacity || 0);
  };
  const okRows = recommended.map(c => '<tr>' +
      '<td><b>' + c.name + '</b></td>' +
      '<td style="font-size:13px;color:var(--text-light)">' + (c.time || '') + '</td>' +
      '<td>' + (c.credit || 0) + '</td>' +
      '<td style="font-size:12px;color:var(--text-light)">' + statOf(c.courseId) + '</td>' +
      '<td><span class="status-tag status-approved">推荐选入</span></td></tr>').join('');
  const noRows = rejected.map(id => {
    const c = candMap.get(id);
    return '<tr class="rec-rejected">' +
      '<td>' + (c ? c.name : '课程#' + id) + '</td>' +
      '<td style="font-size:13px;color:var(--text-light)">' + (c ? (c.time || '') : '') + '</td>' +
      '<td>' + (c ? (c.credit || 0) : '') + '</td>' +
      '<td style="font-size:12px;color:var(--text-light)">' + statOf(id) + '</td>' +
      '<td><span class="status-tag status-rejected">时间冲突</span></td></tr>';
  }).join('');
  const credits = recommended.reduce((s, c) => s + (c.credit || 0), 0);
  box.innerHTML = '<div class="rec-panel">' +
    '<div class="rec-head">' + svgIcon('sparkles') + ' 推荐方案：可加入 <b>' + recommended.length + '</b> 门 · 合计 <b>' + credits + '</b> 学分' +
    (rejected.length ? ' · <span style="color:var(--danger)">' + rejected.length + ' 门因时间冲突排除</span>' : '') + '</div>' +
    ((okRows || noRows)
      ? '<div class="data-table"><table><thead><tr><th>课程</th><th>上课时间</th><th>学分</th><th>报名/容量</th><th>结果</th></tr></thead><tbody>' +
        okRows + noRows + '</tbody></table></div>'
      : '<div style="color:var(--text-light);font-size:13px;padding:8px 0">没有可推荐的课程</div>') +
    '<div class="rec-actions">' +
      (recommended.length ? '<button class="btn btn-primary btn-sm" onclick="addRecommendToIntents()">' + svgIcon('cart') + ' 把推荐课程加入意向单</button>' : '') +
      '<button class="btn btn-ghost btn-sm" onclick="navigate(\'intents\')">' + svgIcon('cart') + ' 查看我的意向</button>' +
    '</div>' +
    '<div class="rec-tip">推荐结果只是<strong>选课建议</strong>，不会自动选课；「报名/容量」可帮你判断竞争激烈程度。点「加入意向单」后，可在意向单页面调整投入积分并一键提交。</div>' +
    '</div>';
}

async function addRecommendToIntents() {
  const rec = (window._recResult || {}).recommended || [];
  if (!rec.length) { toast('没有可加入的推荐课程', 'error'); return; }
  let ok = 0, fail = 0;
  for (const c of rec) {
    const r = await api('POST', '/intents/add', { courseId: c.courseId, points: 10 });
    if (r && r.success) ok++; else fail++;
  }
  toast('已把 ' + ok + ' 门推荐课程加入意向单' + (fail ? '（' + fail + ' 门失败）' : ''), 'success');
  navigate('intents');
}


// ===== 当前学期（读取后端 semesters，取代硬编码）=====
async function loadCurrentSemester() {
  try {
    const r = await api('GET', '/semesters');
    const list = (r && r.success) ? r.data : [];
    const cur = list.find(s => s.isCurrent) || null;
    window._semInfo = cur ? { name: cur.name, start: cur.startDate } : null;
  } catch (e) {
    window._semInfo = null;
  }
  return window._semInfo;
}
function semWeek0() {
  const s = window._semInfo;
  const d = (s && s.start) ? new Date(s.start) : null;
  if (!d || isNaN(d.getTime())) return new Date(2026, 8, 7);
  return d;
}
function semName() {
  const s = window._semInfo;
  return (s && s.name) ? s.name : '当前学期';
}
function semCurWeek() {
  const today = new Date();
  return Math.min(18, Math.max(1, Math.floor((today.getTime() - semWeek0().getTime()) / 86400000 / 7) + 1));
}


// ===== 公告 =====
function announceTypeColor(t) {
  const s = t || '';
  if (s.indexOf('选课') >= 0) return '#4f46e5';
  if (s.indexOf('考试') >= 0) return '#f59e0b';
  return '#06b6d4';
}

function announceCardHtml(a, admin) {
  const t = a.type || '系统公告';
  const c = announceTypeColor(t);
  const unread = !admin && a.read === false;
  return '<div class="announce-card' + (unread ? ' announce-unread' : '') + '"' +
    (!admin ? ' onclick="markAnnounceRead(' + a.id + ', this)"' : '') + '>' +
    '<div class="announce-top">' +
      '<span class="announce-type" style="background:' + c + '1a;color:' + c + '">' + t + '</span>' +
      (a.pinned ? '<span class="announce-pin">' + svgIcon('pin') + ' 置顶</span>' : '') +
      (a.expired ? '<span class="announce-pin" style="background:#ef44441a;color:#ef4444;border-radius:20px;padding:2px 9px">已过期</span>' : '') +
      (unread ? '<span class="announce-unread-dot"></span>' : '') +
      '<span class="announce-time">' + fmtTime(a.createdAt) + '</span>' +
    '</div>' +
    '<div class="announce-title">' + a.title + '</div>' +
    (a.content ? '<div class="announce-content">' + a.content + '</div>' : '') +
    '<div class="announce-foot">发布人：' + (a.publisherName || '管理员') +
      (a.expireTime ? ' · 有效期至 ' + fmtTime(a.expireTime) : '') +
      (admin ? ' · 已读 ' + (a.readCount || 0) + '/' + (a.totalUsers || 0) : '') + '</div>' +
    (admin ? ('<div class="announce-acts">' +
        '<button class="btn btn-outline btn-sm" onclick="editAnnounce(' + a.id + ')">编辑</button>' +
        '<button class="btn btn-danger btn-sm" onclick="deleteAnnounce(' + a.id + ', \'' + (a.title || '').replace(/'/g, '') + '\')">删除</button>' +
      '</div>') : '') +
    '</div>';
}

// 点击公告卡片标记已读
async function markAnnounceRead(id, el) {
  if (el && !el.classList.contains('announce-unread')) return;
  await api('POST', '/announcements/' + id + '/read');
  if (el) {
    el.classList.remove('announce-unread');
    const dot = el.querySelector('.announce-unread-dot');
    if (dot) dot.remove();
  }
  refreshNotifDot();
}

async function loadAnnouncementCards(selector) {
  const box = document.querySelector(selector);
  if (!box) return;
  const r = await api('GET', '/announcements?limit=3');
  const list = (r && r.success) ? r.data : [];
  if (!list.length) { box.innerHTML = ''; return; }
  box.innerHTML = '<div class="section"><div class="section-header">' +
    '<div class="section-title">' + svgIcon('megaphone') + ' 最新公告</div>' +
    '<a class="section-link" onclick="openAnnounceList()">查看全部 →</a></div>' +
    '<div class="announce-cards">' + list.map(a => announceCardHtml(a, false)).join('') + '</div></div>';
}

async function openAnnounceList() {
  const r = await api('GET', '/announcements');
  const list = (r && r.success) ? r.data : [];
  showResultModal('全部公告', list.length + ' 条', list.length
    ? '<div class="announce-list">' + list.map(a => announceCardHtml(a, false)).join('') + '</div>'
    : '<div style="color:var(--text-light);font-size:13px">暂无公告</div>');
  // 打开全部公告即视为已读
  await api('POST', '/announcements/read-all');
  refreshNotifDot();
}

// ===== 管理员：公告管理 =====
async function renderAnnounceMgr() {
  const r = await api('GET', '/announcements?all=1');
  const list = (r && r.success) ? r.data : [];
  window._annList = list;
  window._editingAnnounce = null;
  $('#content').innerHTML = `
    <div class="admin-card">
      <h3>${svgIcon('megaphone')} 发布公告</h3>
      <div style="display:grid;grid-template-columns:2fr 1fr;gap:12px;margin-bottom:12px">
        <input id="an-title" placeholder="公告标题（如：第一轮选课 9 月 8 日 9:00 开始）" style="padding:10px;border:1.5px solid var(--border);border-radius:8px;background:var(--card);color:var(--text)">
        <select id="an-type" style="padding:10px;border:1.5px solid var(--border);border-radius:8px;background:var(--card);color:var(--text)">
          <option>选课通知</option><option>考试通知</option><option>系统公告</option>
        </select>
      </div>
      <textarea id="an-content" placeholder="公告内容…" style="width:100%;min-height:84px;padding:10px 12px;border:1.5px solid var(--border);border-radius:8px;font-family:inherit;font-size:14px;resize:vertical;background:var(--card);color:var(--text);outline:none;margin-bottom:12px"></textarea>
      <div style="display:flex;align-items:center;gap:16px;flex-wrap:wrap">
        <label style="display:flex;align-items:center;gap:6px;font-size:13px"><input type="checkbox" id="an-pinned"> 置顶显示</label>
        <label style="display:flex;align-items:center;gap:6px;font-size:13px">有效期至 <input type="datetime-local" id="an-expire" style="padding:6px 8px;border:1.5px solid var(--border);border-radius:6px;background:var(--card);color:var(--text)"></label>
        <button class="btn btn-primary" id="an-submit-btn" onclick="submitAnnounce()">${svgIcon('plus')} 发布公告</button>
        <span style="font-size:12px;color:var(--text-light)">公告会出现在学生/教师首页与顶栏通知中心</span>
      </div>
    </div>
    <div class="section">
      <div class="section-header"><div class="section-title">${svgIcon('file-text')} 已发布公告（${list.length}）</div></div>
      ${list.length ? `<div class="announce-cards">${list.map(a => announceCardHtml(a, true)).join('')}</div>` : emptyHtml('megaphone', '还没有发布任何公告')}
    </div>`;
}

async function submitAnnounce() {
  const title = $('#an-title').value.trim();
  if (!title) { toast('请填写公告标题', 'error'); return; }
  const expEl = document.getElementById('an-expire');
  const expVal = expEl ? expEl.value : '';
  const payload = {
    title: title,
    content: $('#an-content').value.trim(),
    type: $('#an-type').value,
    pinned: $('#an-pinned').checked,
    expireTime: expVal ? new Date(expVal).getTime() : '',
  };
  const id = window._editingAnnounce;
  const r = id ? await api('PUT', '/announcements/' + id, payload) : await api('POST', '/announcements', payload);
  toast(r.message, r.success ? 'success' : 'error');
  if (r.success) { window._editingAnnounce = null; navigate('announce'); }
}

function editAnnounce(id) {
  const a = (window._annList || []).find(x => x.id === id);
  if (!a) return;
  $('#an-title').value = a.title || '';
  $('#an-content').value = a.content || '';
  $('#an-type').value = a.type || '系统公告';
  $('#an-pinned').checked = !!a.pinned;
  const expEl = document.getElementById('an-expire');
  if (expEl) {
    if (a.expireTime) {
      const dd = new Date(a.expireTime);
      const pp = (n) => String(n).padStart(2, '0');
      expEl.value = dd.getFullYear() + '-' + pp(dd.getMonth() + 1) + '-' + pp(dd.getDate()) + 'T' + pp(dd.getHours()) + ':' + pp(dd.getMinutes());
    } else expEl.value = '';
  }
  window._editingAnnounce = id;
  const btn = document.getElementById('an-submit-btn');
  if (btn) btn.innerHTML = svgIcon('check-circle') + ' 保存修改';
  window.scrollTo({ top: 0, behavior: 'smooth' });
  toast('已载入公告，修改后点「保存修改」', 'success');
}

async function deleteAnnounce(id, title) {
  if (!await confirmAsync('删除公告', '确定删除「' + title + '」？', 'danger')) return;
  const r = await api('DELETE', '/announcements/' + id);
  toast(r.message, r.success ? 'success' : 'error');
  if (r.success) navigate('announce');
}


// ===== 统计报表（管理员）=====
async function renderStats() {
  const r = await api('GET', '/stats/report');
  if (!r.success) { $('#content').innerHTML = emptyHtml('bar-chart', r.message || '暂无统计数据'); return; }
  const d = r.data || {};
  const ov = d.overview || {};
  const hot = (d.hot || []).filter(x => !x.required).slice(0, 10);
  const hyMax = Math.max(1, ...hot.map(x => x.applied || 0));
  const fill = (d.fill || []).filter(x => !x.required && x.capacity > 0).slice(0, 10);
  const rounds = d.rounds || [];
  const rmMax = Math.max(1, ...rounds.map(x => x.applied || 0));
  const teachers = d.teachers || [];
  $('#content').innerHTML = `
    <div class="stats-grid">
      <div class="stat-card"><div class="stat-icon blue">${svgIcon('clipboard')}</div><div><div class="stat-value">${ov.applied || 0}</div><div class="stat-label">本轮报名（选修）</div></div></div>
      <div class="stat-card"><div class="stat-icon green">${svgIcon('check-circle')}</div><div><div class="stat-value">${ov.approved || 0}</div><div class="stat-label">已录取</div></div></div>
      <div class="stat-card"><div class="stat-icon purple">${svgIcon('target')}</div><div><div class="stat-value">${ov.fillRate || 0}%</div><div class="stat-label">平均满员率</div></div></div>
      <div class="stat-card"><div class="stat-icon amber">${svgIcon('hourglass')}</div><div><div class="stat-value">${ov.fullCount || 0}</div><div class="stat-label">满员课程 · 候补 ${ov.waiting || 0} 人</div></div></div>
    </div>
    <div class="stats-two">
      <div class="section"><div class="section-header"><div class="section-title">${svgIcon('bar-chart')} 课程热度排行（本轮报名）</div></div>
        ${hot.length ? hot.map((x, i) => `<div class="rank-row"><div class="rank-name"><span class="rank-idx${i < 3 ? ' top' : ''}">${i + 1}</span>${x.courseName}<span class="rank-sub">${x.teacherName || ''}</span></div>
          <div class="rank-bar-wrap"><div class="rank-bar" style="width:${Math.round((x.applied || 0) * 100 / hyMax)}%"></div></div>
          <div class="rank-val">${x.applied} 人</div></div>`).join('') : emptyHtml('bar-chart', '暂无报名数据')}
      </div>
      <div class="section"><div class="section-header"><div class="section-title">${svgIcon('target')} 满员率排行</div></div>
        ${fill.length ? fill.map(x => `<div class="rank-row"><div class="rank-name">${x.courseName}<span class="rank-sub">${x.approved}/${x.capacity}</span></div>
          <div class="rank-bar-wrap"><div class="rank-bar fill${x.fillRate >= 100 ? ' full' : ''}" style="width:${Math.min(100, x.fillRate)}%"></div></div>
          <div class="rank-val">${x.fillRate}%</div></div>`).join('') : emptyHtml('target', '暂无数据')}
      </div>
    </div>
    <div class="section"><div class="section-header"><div class="section-title">${svgIcon('refresh')} 轮次报名趋势</div></div>
      ${rounds.length ? rounds.map(x => `<div class="rank-row"><div class="rank-name">第 ${x.round} 轮</div>
        <div class="rank-bar-wrap"><div class="rank-bar round" style="width:${Math.round((x.applied || 0) * 100 / rmMax)}%"></div></div>
        <div class="rank-val">报名 ${x.applied} · 录取 ${x.approved} · 未录取 ${x.rejected} · 退课 ${x.dropped}</div></div>`).join('') : emptyHtml('refresh', '暂无数据')}
    </div>
    <div class="section"><div class="section-header"><div class="section-title">${svgIcon('users')} 教师工作量</div></div>
      ${teachers.length ? `<div class="data-table"><table><thead><tr><th>教师</th><th>学院</th><th>开设课程</th><th>本轮录取学生</th><th>收到评价</th><th>平均评分</th></tr></thead><tbody>
        ${teachers.map(t => `<tr><td><b>${t.teacherName}</b></td><td>${t.college || '—'}</td><td>${t.courseCount}</td><td>${t.studentCount}</td><td>${t.reviewCount}</td><td>${t.reviewCount ? miniStars(t.avgRating) + ' <b>' + t.avgRating + '</b>' : '—'}</td></tr>`).join('')}
      </tbody></table></div>` : emptyHtml('users', '暂无教师数据')}
    </div>`;
}

// ===== 本轮结算（按积分统一录取）=====
async function settleRound() {
  if (!await confirmAsync('结算本轮选课', '将按每门课的投入积分从高到低统一录取，同分先报名者优先；超额部分记为未录取并退还积分。确定继续？', 'info')) return;
  const r = await api('POST', '/enrollments/settle', {});
  if (!r.success) { toast(r.message, 'error'); return; }
  const d = r.data || {};
  const details = d.details || [];
  const rows = details.map(x => '<tr><td><b>' + x.courseName + '</b></td><td>' + x.capacity + '</td><td>' + x.applied + '</td><td>'
      + x.admitted + '</td><td>' + (x.cutline != null ? x.cutline + ' 分' : '全部录取') + '</td></tr>').join('');
  showResultModal('第 ' + (d.round || '') + ' 轮结算完成', r.message,
    details.length
      ? '<div class="data-table"><table><thead><tr><th>课程</th><th>容量</th><th>报名</th><th>录取</th><th>最低录取分</th></tr></thead><tbody>' + rows + '</tbody></table></div>'
      : '<div style="color:var(--text-light);font-size:13px">本轮没有待录取的报名</div>');
  renderRoundInfo();
}


// ===== 毕业设计导师选择 =====
function fmtAdvisorStatus(s) {
  const map = { pending: ['申请中', 'warn'], approved: ['已确认', 'success'], rejected: ['已拒绝', 'danger'], cancelled: ['已撤销', 'text-light'] };
  const [label, cls] = map[s] || [s, 'text-light'];
  return `<span class="tag ${cls}">${label}</span>`;
}

async function renderAdvisor() {
  const cfgR = await api('GET', '/advisor/config');
  const cfg = cfgR.success ? cfgR.data : {};
  const mineR = await api('GET', '/advisor/mine');
  const mine = mineR.success ? mineR.data : [];
  const tR = await api('GET', '/advisor/teachers');
  const teachers = tR.success ? tR.data : [];

  let html = `<div class="section-header"><div class="section-title">${svgIcon('graduation-cap')} 毕业设计导师${cfg.open ? '' : ' <span class="tag danger">已关闭</span>'}</div></div>`;
  if (!cfg.open) { $('#content').innerHTML = html + `<div class="empty-card" style="margin-top:20px">${svgIcon('lock')} 管理员已关闭本轮导师选择</div>`; return; }

  const myApproved = mine.find(x => x.status === 'approved');
  const myPending = mine.filter(x => x.status === 'pending').length;
  html += `<div class="stats-grid" style="margin-bottom:16px">
    <div class="stat-card"><div class="stat-icon purple">${svgIcon('graduation-cap')}</div><div><div class="stat-value">${teachers.length}</div><div class="stat-label">可选导师</div></div></div>
    <div class="stat-card"><div class="stat-icon blue">${svgIcon('check-circle')}</div><div><div class="stat-value">${myApproved ? 1 : 0}</div><div class="stat-label">已确认导师</div></div></div>
    <div class="stat-card"><div class="stat-icon amber">${svgIcon('hourglass')}</div><div><div class="stat-value">${myPending}</div><div class="stat-label">待审批申请</div></div></div>
    <div class="stat-card"><div class="stat-icon green">${svgIcon('users')}</div><div><div class="stat-value">${teachers.filter(t=>t.available).length}</div><div class="stat-label">有空位导师</div></div></div>
  </div>`;

  html += `<div class="section"><div class="section-header"><div class="section-title">📝 我的申请记录</div></div>`;
  if (!mine.length) html += `<div class="empty-card">暂无申请记录，可在下方导师列表中选择意向导师</div>`;
  else {
    html += `<div class="data-table"><table><thead><tr><th>导师</th><th>研究方向</th><th>状态</th><th>时间</th><th>操作</th></tr></thead><tbody>`;
    for (const m of mine) {
      const t = m.createdAt ? new Date(m.createdAt).toLocaleString('zh-CN') : '';
      const canCancel = m.status === 'pending';
      html += `<tr><td><strong>${m.teacherName}</strong><div style="font-size:12px;color:var(--text-light)">${m.teacherCollege || ''}</div></td>
        <td>${m.topic || '-'}</td><td>${fmtAdvisorStatus(m.status)}${m.teacherReply ? '<div style="font-size:12px;color:var(--text-light);margin-top:2px">导师回复：' + m.teacherReply + '</div>' : ''}</td>
        <td style="font-size:12px">${t}</td>
        <td>${canCancel ? `<button class="btn btn-outline btn-sm" data-advisor-cancel="${m.id}">撤销</button>` : ''}</td></tr>`;
    }
    html += `</tbody></table></div>`;
  }
  html += `</div>`;

  html += `<div class="section" style="margin-top:20px"><div class="section-header"><div class="section-title">🔍 浏览导师（${teachers.length}）</div>
    <div class="advisor-search-box" id="advisor-search-box">
      <svg class="icon"><use href="#i-search"/></svg>
      <input id="advisor-search" placeholder="搜索导师姓名 / 学院 / 专业... 支持模糊匹配">
      <button class="adv-clear" id="advisor-search-clear" title="清空" onclick="$('#advisor-search').value=''; $('#advisor-search').dispatchEvent(new Event('input')); $('#advisor-search-box').classList.remove('has-val')"><svg class="icon"><use href="#i-x"/></svg></button>
      <span class="adv-count" id="advisor-search-count"></span>
    </div>
  </div><div id="advisor-teacher-grid" class="stats-grid" style="grid-template-columns:repeat(auto-fill,minmax(280px,1fr));gap:14px"></div></div>`;

  const canApply = !myApproved;
  $('#content').innerHTML = html;

  function renderTeachers(list) {
    const box = $('#advisor-teacher-grid');
    box.innerHTML = list.map(t => {
      const pct = Math.round(t.approved / t.capacity * 100);
      const barCls = pct >= 100 ? 'danger' : pct >= 70 ? 'warn' : 'success';
      const disabled = !t.available || !canApply;
      return `<div class="advisor-card">
        <div class="adv-head">
          <div class="adv-avatar" style="background:linear-gradient(135deg,#6366f1,#a855f7)">${t.teacherName.charAt(0)}</div>
          <div class="adv-info"><div class="adv-name">${t.teacherName}</div><div class="adv-sub">${t.college || '—'} · ${t.major || '—'}</div></div>
          <div class="adv-tag ${t.available ? 'ok' : 'full'}">${t.available ? '空位' : '已满'}</div>
        </div>
        <div class="adv-bar"><div class="adv-bar-fill ${barCls}" style="width:${pct}%"></div></div>
        <div class="adv-foot">
          <span>已带 <strong>${t.approved}</strong> / ${t.capacity}</span>
          <span>${t.pending} 个待审批</span>
          <button class="btn btn-primary btn-sm" ${disabled ? 'disabled title="你已确认导师或导师已满"' : ''} data-advisor-apply="${t.teacherId}">${t.available ? '申请' : '已满'}</button>
        </div>
      </div>`;
    }).join('');
    box.querySelectorAll('[data-advisor-apply]').forEach(b => b.addEventListener('click', () => openApplyModal(parseInt(b.dataset.advisorApply))));
  }
  window._advisorTeachers = teachers;  // 缓存供弹窗/搜索复用
  renderTeachers(teachers);
  const origTeachers = teachers;
  const searchInput = $('#advisor-search');
  const box = $('#advisor-search-box');
  const countEl = $('#advisor-search-count');
  function applyFilter() {
    const q = searchInput.value.trim().toLowerCase();
    box.classList.toggle('has-val', !!q);
    const filtered = q ? origTeachers.filter(t =>
      (t.teacherName || '').toLowerCase().includes(q) ||
      (t.teacherNo || '').toLowerCase().includes(q) ||
      (t.college || '').toLowerCase().includes(q) ||
      (t.major || '').toLowerCase().includes(q)
    ) : origTeachers;
    renderTeachers(filtered);
    countEl.textContent = q ? (filtered.length + ' / ' + origTeachers.length) : (origTeachers.length + ' 位');
  }
  searchInput.addEventListener('input', applyFilter);
  searchInput.addEventListener('keydown', e => { if (e.key === 'Escape') { searchInput.value = ''; applyFilter(); } });
  applyFilter(); // 初始填充 count
  $('#content').querySelectorAll('[data-advisor-cancel]').forEach(b => b.addEventListener('click', async () => {
    if (!confirm('确定撤销该申请？')) return;
    const r = await api('POST', '/advisor/cancel/' + b.dataset.advisorCancel);
    toast(r.message, r.success ? 'success' : 'error');
    if (r.success) refreshCurrentPage();
  }));
}

let _applyOpenFor = null;
function openApplyModal(teacherId) {
  _applyOpenFor = teacherId;
  const t = (window._advisorTeachers || []).find(x => x.teacherId === teacherId);
  const name = t ? t.teacherName : '该导师';
  showModal('提交导师申请', { maxWidth: 480 });
  const college = t?.college || '—';
  const major = t?.major || '—';
  const pct = t ? Math.round(t.approved / t.capacity * 100) : 0;
  const barCls = pct >= 100 ? 'danger' : pct >= 70 ? 'warn' : 'success';
  $('.modal-mask:last-of-type .modal-body').innerHTML = `
    <div class="apply-teacher-card" style="display:flex;gap:14px;padding:14px;background:linear-gradient(135deg,rgba(79,70,229,.08),rgba(168,85,247,.08));border-radius:12px;margin-bottom:18px;align-items:center">
      <div style="width:48px;height:48px;border-radius:50%;background:linear-gradient(135deg,#6366f1,#a855f7);display:flex;align-items:center;justify-content:center;color:#fff;font-size:20px;font-weight:600;flex-shrink:0">${name.charAt(0)}</div>
      <div style="flex:1;min-width:0">
        <div style="font-weight:600;font-size:15px;color:var(--text)">${name}</div>
        <div style="font-size:12px;color:var(--text-light);margin-top:2px">${college} · ${major}</div>
        <div style="display:flex;gap:8px;margin-top:6px;align-items:center">
          <div style="font-size:11px;color:var(--text-light)">已带 ${t?.approved || 0} / ${t?.capacity || 0}</div>
          <div style="flex:1;height:4px;background:var(--border);border-radius:2px;overflow:hidden">
            <div style="height:100%;width:${pct}%;background:${barCls==='success'?'#22c55e':barCls==='warn'?'#f59e0b':'#ef4444'};border-radius:2px"></div>
          </div>
          <span style="font-size:11px;padding:1px 6px;border-radius:8px;background:${t?.available?'#22c55e22':'#ef444422'};color:${t?.available?'#22c55e':'#ef4444'}">${t?.available?'空位':'已满'}</span>
        </div>
      </div>
    </div>
    <div class="form-group"><label>意向论文方向 <span style="color:var(--text-light);font-weight:400;font-size:12px">（可选）</span></label><input id="apply-topic" class="form-input" placeholder="例如：大语言模型在教育场景的应用"></div>
    <div class="form-group"><label>自我介绍 / 备注 <span style="color:var(--text-light);font-weight:400;font-size:12px">（可选）</span></label><textarea id="apply-pref" class="form-input" rows="3" placeholder="简单介绍自己、想做的方向、时间安排..."></textarea></div>`;
  $('.modal-mask:last-of-type .modal-footer').innerHTML = `
    <div style="font-size:12px;color:var(--text-light);flex:1">提交后进入「待审批」，导师批准前可撤销</div>
    <button class="btn btn-outline" onclick="closeModal()">取消</button>
    <button class="btn btn-primary" id="apply-submit-btn" onclick="submitAdvisorApply()">提交申请</button>`;
}
async function submitAdvisorApply() {
  if (!_applyOpenFor) return;
  const btn = $('#apply-submit-btn');
  if (btn) { btn.disabled = true; btn.textContent = '提交中...'; }
  try {
    const r = await api('POST', '/advisor/apply', {
      teacherId: _applyOpenFor,
      topic: $('#apply-topic').value.trim(),
      preferenceText: $('#apply-pref').value.trim()
    });
    toast(r.message, r.success ? 'success' : 'error');
    if (r.success) { closeModal(); refreshCurrentPage(); }
    else { if (btn) { btn.disabled = false; btn.textContent = '提交申请'; } }
  } catch (e) {
    toast('提交失败，请重试', 'error');
    if (btn) { btn.disabled = false; btn.textContent = '提交申请'; }
  }
}

// ===== 教师端 =====
async function renderAdvise() {
  const cfg = (await api('GET', '/advisor/config')).data || {};
  const stats = (await api('GET', '/advisor/teacher/stats')).data || {};
  const all = ((await api('GET', '/advisor/teacher/list')).data) || [];

  let html = `<div class="section-header"><div class="section-title">${svgIcon('users')} 毕业设计指导${cfg.open ? '' : ' <span class="tag warn">系统已关闭</span>'}</div></div>`;
  html += `<div class="stats-grid" style="margin-bottom:16px">
    <div class="stat-card"><div class="stat-icon purple">${svgIcon('graduation-cap')}</div><div><div class="stat-value">${stats.capacity || 0}</div><div class="stat-label">可带上限</div></div></div>
    <div class="stat-card"><div class="stat-icon green">${svgIcon('check-circle')}</div><div><div class="stat-value">${stats.approved || 0}</div><div class="stat-label">已确认学生</div></div></div>
    <div class="stat-card"><div class="stat-icon amber">${svgIcon('hourglass')}</div><div><div class="stat-value">${stats.pending || 0}</div><div class="stat-label">待审批</div></div></div>
    <div class="stat-card"><div class="stat-icon danger">${svgIcon('x-circle')}</div><div><div class="stat-value">${stats.rejected || 0}</div><div class="stat-label">已拒绝</div></div></div>
  </div>`;

  const tabs = [['all', '全部 (' + all.length + ')'], ['pending', '待审批 (' + all.filter(x=>x.status==='pending').length + ')'],
    ['approved', '已确认 (' + all.filter(x=>x.status==='approved').length + ')'], ['rejected', '已拒绝 (' + all.filter(x=>x.status==='rejected').length + ')']];
  html += `<div class="adv-tabs" id="adv-tabs">${tabs.map(([k,l])=>`<button class="adv-tab ${k==='pending'?'active':''}" data-tab="${k}">${l}</button>`).join('')}</div>`;

  function rowHtml(r) {
    return `<tr><td><strong>${r.studentName}</strong><div style="font-size:12px;color:var(--text-light)">${r.studentNo || ''} · ${r.studentClass || ''}</div></td>
      <td>${r.topic || '-'}</td><td style="max-width:260px">${r.preferenceText || '-'}</td>
      <td>${fmtAdvisorStatus(r.status)}</td><td style="font-size:12px">${r.teacherReply || '-'}</td>
      <td>${r.status === 'pending' ? `<button class="btn btn-success btn-sm" data-decide="approve" data-id="${r.id}">批准</button> <button class="btn btn-danger btn-sm" data-decide="reject" data-id="${r.id}">拒绝</button>` : '-'}</td></tr>`;
  }
  html += `<div class="data-table" id="adv-table"><table><thead><tr><th>学生</th><th>论文方向</th><th>备注</th><th>状态</th><th>回复</th><th>操作</th></tr></thead>
    <tbody>${all.filter(x => x.status === 'pending').map(rowHtml).join('') || `<tr><td colspan="6" class="empty-td">暂无数据</td></tr>`}</tbody></table></div>`;
  $('#content').innerHTML = html;

  $('#adv-tabs').querySelectorAll('.adv-tab').forEach(b => b.addEventListener('click', () => {
    $('#adv-tabs').querySelectorAll('.adv-tab').forEach(t => t.classList.remove('active')); b.classList.add('active');
    const list = b.dataset.tab === 'all' ? all : all.filter(x => x.status === b.dataset.tab);
    $('#adv-table').querySelector('tbody').innerHTML = list.map(rowHtml).join('') || `<tr><td colspan="6" class="empty-td">暂无数据</td></tr>`;
    bindDecide();
  }));
  bindDecide();
}
function bindDecide() {
  $('#content').querySelectorAll('[data-decide]').forEach(b => b.addEventListener('click', () => {
    const id = parseInt(b.dataset.id), decision = b.dataset.decide;
    showModal(decision === 'approve' ? '批准申请' : '拒绝申请');
    $('.modal-mask:last-of-type .modal-body').innerHTML = `<div class="form-group"><label>回复（可选）</label><textarea id="adv-reply" class="form-input" rows="3"></textarea></div>`;
    $('.modal-mask:last-of-type .modal-footer').innerHTML = `
      <button class="btn btn-outline" onclick="closeModal()">取消</button>
      <button class="btn ${decision === 'approve' ? 'btn-success' : 'btn-danger'}" onclick="submitAdviseDecide(${id},'${decision}')">确认</button>`;
  }));
}
async function submitAdviseDecide(id, decision) {
  const r = await api('POST', '/advisor/teacher/decide', { id, decision, reply: $('#adv-reply').value.trim() });
  toast(r.message, r.success ? 'success' : 'error');
  if (r.success) { closeModal(); refreshCurrentPage(); }
}

// ===== 管理员端 =====
async function renderAdviseMgr() {
  const ov = ((await api('GET', '/advisor/admin/overview')).data) || {};
  const list = ((await api('GET', '/advisor/admin/list')).data) || [];
  const caps = ((await api('GET', '/advisor/admin/capacities')).data) || [];
  const cfg = ov.config || {};

  let html = `<div class="section-header"><div class="section-title">${svgIcon('graduation-cap')} 导师管理</div></div>`;
  html += `<div class="stats-grid" style="margin-bottom:16px">
    <div class="stat-card"><div class="stat-icon blue">${svgIcon('users')}</div><div><div class="stat-value">${ov.teacherCount || 0}</div><div class="stat-label">导师总数</div></div></div>
    <div class="stat-card"><div class="stat-icon green">${svgIcon('check-circle')}</div><div><div class="stat-value">${ov.totalApproved || 0}</div><div class="stat-label">已确认配对</div></div></div>
    <div class="stat-card"><div class="stat-icon amber">${svgIcon('hourglass')}</div><div><div class="stat-value">${ov.totalPending || 0}</div><div class="stat-label">待审批申请</div></div></div>
    <div class="stat-card"><div class="stat-icon purple">${svgIcon('star')}</div><div><div class="stat-value">${ov.teachersWithSpace || 0}</div><div class="stat-label">有空位导师</div></div></div>
  </div>`;

  html += `<div class="section"><div class="section-header"><div class="section-title">⚙️ 全局配置</div></div>
    <div class="form-row-inline">
      <label><input type="checkbox" id="adv-cfg-open" ${cfg.open ? 'checked' : ''}> 开启导师选择</label>
      <label>默认容量 <input id="adv-cfg-cap" class="form-input" style="width:80px" type="number" value="${cfg.defaultCapacity || 15}" min="1"></label>
      <button class="btn btn-primary btn-sm" id="adv-cfg-save">保存</button>
    </div></div>`;

  html += `<div class="section" style="margin-top:16px"><div class="section-header"><div class="section-title">👨‍🏫 导师容量设置</div></div>
    <div class="data-table"><table><thead><tr><th>导师</th><th>工号</th><th>容量上限</th><th>已确认</th><th>待审批</th><th>操作</th></tr></thead><tbody>`;
  for (const c of caps) {
    html += `<tr><td><strong>${c.teacherName}</strong></td><td>${c.teacherNo}</td>
      <td><input class="form-input" style="width:80px" type="number" min="1" value="${c.capacity}" data-cap="${c.teacherId}"></td>
      <td>${c.approved}</td><td>${c.pending}</td>
      <td><button class="btn btn-primary btn-sm" data-save-cap="${c.teacherId}">保存</button></td></tr>`;
  }
  html += `</tbody></table></div></div>`;

  html += `<div class="section" style="margin-top:16px"><div class="section-header"><div class="section-title">📋 全部申请（${list.length}）</div></div>`;
  if (!list.length) html += `<div class="empty-card">暂无申请记录</div>`;
  else {
    html += `<div class="data-table"><table><thead><tr><th>学生</th><th>导师</th><th>论文方向</th><th>状态</th><th>时间</th><th>操作</th></tr></thead><tbody>`;
    for (const r of list) {
      const t = r.createdAt ? new Date(r.createdAt).toLocaleString('zh-CN') : '';
      html += `<tr><td>${r.studentName} <span style="font-size:12px;color:var(--text-light)">(${r.studentNo})</span></td>
        <td>${r.teacherName}</td><td>${r.topic || '-'}</td><td>${fmtAdvisorStatus(r.status)}</td>
        <td style="font-size:12px">${t}</td><td><button class="btn btn-outline btn-sm" data-del-adv="${r.id}">删除</button></td></tr>`;
    }
    html += `</tbody></table></div>`;
  }
  html += `</div>`;

  $('#content').innerHTML = html;
  $('#adv-cfg-save').addEventListener('click', async () => {
    const r = await api('POST', '/advisor/admin/cfg', { open: $('#adv-cfg-open').checked, defaultCapacity: parseInt($('#adv-cfg-cap').value) || 15 });
    toast(r.message, r.success ? 'success' : 'error');
  });
  $('#content').querySelectorAll('[data-save-cap]').forEach(b => b.addEventListener('click', async () => {
    const tid = parseInt(b.dataset.saveCap), input = document.querySelector(`input[data-cap="${tid}"]`);
    const v = parseInt(input.value); if (!v || v < 1) { toast('请输入有效容量', 'error'); return; }
    const r = await api('POST', '/advisor/admin/cfg', { teacherId: tid, teacherCapacity: v });
    toast(r.message, r.success ? 'success' : 'error');
  }));
  $('#content').querySelectorAll('[data-del-adv]').forEach(b => b.addEventListener('click', async () => {
    if (!confirm('确定删除该申请？')) return;
    const r = await api('DELETE', '/advisor/admin/' + b.dataset.delAdv);
    toast(r.message, r.success ? 'success' : 'error');
    if (r.success) refreshCurrentPage();
  }));
}


// ======== 毕业设计工作台（条件入口：有 approved advisor 才显示）========
let _wsStuList = null, _wsTeaList = null, _wsSelectedAdvisorId = null, _wsRole = null;

async function refreshWorkspaceNav() {
  // 控制侧栏入口显隐 —— 主动往 sidebar-nav 里注入 nav-item
  try {
    const me = getUser(); if (!me) return;
    const nav = $('#sidebar-nav'); if (!nav) return;
    const hasWS = (me.role === 'student')
      ? (await api('GET', '/workspace/my')).success && (await api('GET', '/workspace/my')).data && (await api('GET', '/workspace/my')).data.length > 0
      : (me.role === 'teacher')
        ? (await api('GET', '/workspace/teacher/students')).success && (await api('GET', '/workspace/teacher/students')).data && (await api('GET', '/workspace/teacher/students')).data.length > 0
        : false;
    const id = me.role === 'student' ? 'nav-workspace-stu' : me.role === 'teacher' ? 'nav-workspace-tea' : 'nav-workspace-admin';
    const label = me.role === 'admin' ? '工作台总览' : '毕业设计工作台';
    const exist = $('#' + id);
    if (hasWS && !exist) {
      const a = document.createElement('a');
      a.className = 'nav-item'; a.dataset.page = 'workspace'; a.id = id;
      a.innerHTML = '<span class="nav-icon">' + svgIcon('folder') + '</span><span>' + label + '</span>';
      a.addEventListener('click', () => navigate('workspace'));
      // 插到 advisor 或 advise 后面
      const ref = nav.querySelector('[data-page="advisor"]') || nav.querySelector('[data-page="advise"]') || nav.lastElementChild;
      if (ref && ref.nextElementSibling) nav.insertBefore(a, ref.nextElementSibling);
      else nav.appendChild(a);
    } else if (!hasWS && exist) {
      exist.remove();
    }
  } catch (e) { /* 静默 */ }
}
// 登录后自动检查一次（在 initApp 里 getUser 后调用）
(function() {
  const origInit = window.initApp;
  window.initApp = async function(...args) {
    const r = origInit ? await origInit.apply(this, args) : null;
    setTimeout(refreshWorkspaceNav, 800);
    return r;
  };
  // 如果 initApp 不是 async function，也要保证首次登录后触发
  setTimeout(refreshWorkspaceNav, 1500);
})();

async function renderWorkspace() {
  const me = getUser();
  if (!me) { showAuth(); return; }

  if (me.role === 'student') {
    await renderWorkspaceStudent();
  } else if (me.role === 'teacher') {
    await renderWorkspaceTeacher();
  } else {
    toast('管理员暂未开放工作台', 'error');
    switchPage('dashboard');
  }
}

// ========= 学生工作台 =========
async function renderWorkspaceStudent() {
  $('#page-title').textContent = '毕业设计工作台';
  const r = await api('GET', '/workspace/my');
  if (!r.success) { toast(r.message, 'error'); return; }
  const list = r.data;
  if (!list || list.length === 0) {
    $('#content').innerHTML = `<div class="empty-card">暂未确认导师，先去「毕业设计导师」页面选择吧 👆</div>`;
    return;
  }

  // 第一个工作台
  const ws = list[0];
  $('#content').innerHTML = '<div id="ws-detail"></div>';
  renderWorkspaceDetail(ws, 'student');
}

// ========= 教师工作台：左侧学生列表 + 右侧详情 =========
async function renderWorkspaceTeacher() {
  $('#page-title').textContent = '毕业设计工作台';
  const r = await api('GET', '/workspace/teacher/students');
  if (!r.success) { toast(r.message, 'error'); return; }
  const list = r.data;
  if (!list || list.length === 0) {
    $('#content').innerHTML = `<div class="empty-card">暂无已确认的学生 👀</div>`;
    return;
  }

  let selectedId = _wsSelectedAdvisorId || list[0].advisorId;
  let selected = list.find(x => x.advisorId === selectedId);
  if (!selected) { selected = list[0]; selectedId = selected.advisorId; }

  const html = `
    <div style="display:flex;gap:16px">
      <div style="width:260px;flex-shrink:0">
        <div class="section-title">我的学生（${list.length}）</div>
        <div id="ws-tea-students">
          ${list.map(s => `
            <div class="ws-teacher-stu-card ${s.advisorId === selectedId ? 'active' : ''}" data-aid="${s.advisorId}">
              <div style="flex-shrink:0">
                <div style="width:40px;height:40px;border-radius:50%;background:linear-gradient(135deg,#6366f1,#a855f7);display:flex;align-items:center;justify-content:center;color:#fff;font-weight:600">${s.student.name.charAt(0)}</div>
              </div>
              <div style="flex:1;min-width:0">
                <div style="font-weight:600;font-size:14px">${s.student.name}</div>
                <div style="font-size:11px;color:var(--text-light);margin-top:2px">${s.student.college || ''} · ${s.student.major || ''}</div>
                ${s.topic ? `<div style="font-size:11px;color:var(--primary);margin-top:3px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">🎯 ${s.topic}</div>` : ''}
              </div>
            </div>`).join('')}
        </div>
      </div>
      <div style="flex:1;min-width:0" id="ws-detail">
        <div style="color:var(--text-light);text-align:center;padding:40px">加载中...</div>
      </div>
    </div>`;
  $('#content').innerHTML = html;

  // 绑定学生切换
  $('#ws-tea-students').querySelectorAll('.ws-teacher-stu-card').forEach(card => {
    card.addEventListener('click', () => {
      $('#ws-tea-students').querySelectorAll('.ws-teacher-stu-card').forEach(c => c.classList.remove('active'));
      card.classList.add('active');
      _wsSelectedAdvisorId = parseInt(card.dataset.aid);
      renderWorkspaceDetail(null, 'teacher', _wsSelectedAdvisorId);
    });
  });

  // 渲染选中学生详情
  renderWorkspaceDetail(selected, 'teacher', selectedId);
}

// ========= 详情通用函数 =========
async function renderWorkspaceDetail(wsData, role, advisorId) {
  const aid = advisorId || (wsData ? wsData.advisorId : null);
  $('#ws-detail').innerHTML = '<div style="text-align:center;padding:40px;color:var(--text-light)">加载中...</div>';
  const r = await api('GET', '/workspace/advisor/' + aid);
  if (!r.success) { $('#ws-detail').innerHTML = `<div style="padding:40px;color:var(--danger)">${r.message}</div>`; return; }
  const ws = r.data;

  const partner = role === 'student' ? ws.teacher : ws.student;
  const other = role === 'student' ? ws.student : ws.teacher;

  let html = `
    <!-- 头部：导师/学生信息 -->
    <div class="ws-header-card">
      <div class="ws-avatar">${partner.name.charAt(0)}</div>
      <div class="ws-info">
        <div class="ws-name">${role === 'student' ? '指导教师：' : '学生：'}${partner.name}</div>
        <div class="ws-sub">${partner.college || ''} · ${partner.major || ''}${partner.no ? ' · 学号/工号 ' + partner.no : ''}</div>
        ${ws.topic ? `<div class="ws-sub" style="color:var(--primary);margin-top:4px">🎯 论文方向：${ws.topic}</div>` : ''}
      </div>
      <div style="text-align:right;display:flex;flex-direction:column;align-items:flex-end;gap:8px">
        <button class="ws-chat-float-btn" onclick="quickChat(${aid})">💬 私聊${role === 'student' ? '老师' : '学生'}</button>
        <div style="font-size:12px;color:var(--text-light)">已确认<br>${ws.approvedAt ? new Date(ws.approvedAt).toLocaleDateString('zh-CN') : ''}</div>
      </div>
    </div>

    <!-- Tabs -->
    <div class="ws-tabs">
      <div class="ws-tab active" data-tab="req">📋 ${role === 'teacher' ? '发布要求' : '查看要求'}</div>
      <div class="ws-tab" data-tab="paper">📄 论文${role === 'teacher' ? '管理' : '上传'} &nbsp;${ws.papers && ws.papers.length ? `<span class="ws-badge approved">${ws.papers.length}</span>` : ''}</div>
      <div class="ws-tab" data-tab="anno">📢 公告</div></div>

    <div id="ws-tab-body"></div>
  `;

  $('#ws-detail').innerHTML = html;

  // Tab 切换
  $$('.ws-tab').forEach(tab => {
    tab.addEventListener('click', () => {
      $$('.ws-tab').forEach(t => t.classList.remove('active'));
      tab.classList.add('active');
      renderWsTab(tab.dataset.tab, ws, role, aid);
    });
  });

  // 默认渲染 requirements
  renderWsTab('req', ws, role, aid);
}

function renderWsTab(tab, ws, role, aid) {
  const body = $('#ws-tab-body');
  if (tab === 'req') {
    if (role === 'student') {
      // 学生：只读查看
      if (!ws.latestRequirement) {
        body.innerHTML = `<div class="empty-card">教师还未发布毕业设计要求，耐心等待~</div>`;
      } else {
        const req = ws.latestRequirement;
        body.innerHTML = `
          <div class="ws-requirement-card">
            <h4>📋 毕业设计要求 ${req.deadline ? `<span class="ws-badge pending">截止 ${new Date(req.deadline).toLocaleDateString('zh-CN')}</span>` : ''}</h4>
            <div style="white-space:pre-wrap;font-size:14px;line-height:1.7;color:var(--text)">${req.content || '（未填写）'}</div>
            <div style="margin-top:14px;font-size:11px;color:var(--text-light)">发布时间：${new Date(req.createdAt).toLocaleString('zh-CN')}</div>
          </div>
          ${req.topics && req.topics.length ? `
          <div class="ws-requirement-card">
            <h4>💡 推荐题目（${req.topics.length}）</h4>
            ${req.topics.map((t,i) => `
              <div class="ws-topic-item">
                <span style="color:var(--text-light);font-size:12px;margin-right:8px">#${i+1}</span>
                <span class="ws-topic-title">${t.title || ''}</span>
                ${t.desc ? `<div class="ws-topic-desc">${t.desc}</div>` : ''}
              </div>`).join('')}
          </div>` : ''}
        `;
      }
    } else {
      // 教师：编辑发布要求
      const req = ws.latestRequirement;
      body.innerHTML = `
        <div class="ws-requirement-card">
          <h4>✏️ 发布毕业设计要求 ${req ? `<span style="font-size:11px;color:var(--text-light);font-weight:400">上次发布：${new Date(req.createdAt).toLocaleString('zh-CN')}</span>` : ''}</h4>
          <div class="form-group"><label>要求正文</label>
            <textarea id="ws-req-content" class="form-input" rows="6" placeholder="例如：选题范围、字数要求、格式规范、关键时间节点...">${(req && req.content) || ''}</textarea></div>
          <div class="form-group"><label>截止日期（可选）</label>
            <input id="ws-req-deadline" class="form-input" type="datetime-local" value="${req && req.deadline ? new Date(req.deadline).toISOString().slice(0,16) : ''}"></div>
          <div style="margin-top:10px"><strong style="font-size:13px">推荐题目（可选，每行一条，格式：标题|描述）</strong></div>
          <textarea id="ws-req-topics" class="form-input" rows="4" placeholder="题目一|可选描述&#10;题目二|可选描述">${req && req.topics ? req.topics.map(t => (t.title || '') + '|' + (t.desc || '')).join('\n') : ''}</textarea>
          <div style="margin-top:14px;display:flex;gap:10px">
            <button class="btn btn-primary" onclick="submitWsReq(${aid})">${req ? '更新要求' : '发布要求'}</button>
          </div>
        </div>
      `;
    }
  } else if (tab === 'paper') {
    renderWsPaperTab(ws, role, aid);
  } else if (tab === 'anno') {
    renderWsAnnoTab(ws, role);
  } else if (tab === 'chat') {
    renderWsChatTab(ws, role, aid);
  }
}

function renderWsPaperTab(ws, role, aid) {
  const body = $('#ws-tab-body');
  const papers = ws.papers || [];
  const myPapers = papers.filter(p => p.uploadedBy === 'student');
  const teacherPapers = papers.filter(p => p.uploadedBy === 'teacher');

  // 上传区域
  const uploadHtml = role === 'student' ? `
    <div class="ws-requirement-card">
      <h4>📤 上传新版本论文</h4>
      <div class="ws-dropzone" onclick="document.getElementById('ws-paper-file').click()" ondragover="event.preventDefault()" ondrop="event.preventDefault();handleWsDrop(event,'${aid}')">
        <div style="font-size:32px">📄</div>
        <div style="font-weight:500;margin-top:6px">点击或拖拽文件到此处上传</div>
        <div class="ws-hint">支持 PDF / DOC / DOCX / TXT / ZIP / RAR，最大 100MB · 每次上传自动成为新版本</div>
        <input type="file" id="ws-paper-file" class="ws-file-input" accept=".pdf,.doc,.docx,.txt,.zip,.rar" onchange="handleWsFile(this,'${aid}')">
      </div>
      <div class="form-group" style="margin-top:12px"><label>论文标题（可选）</label>
        <input id="ws-paper-title" class="form-input" placeholder="留空则使用文件名"></div>
      <div class="form-group"><label>备注（可选）</label>
        <textarea id="ws-paper-note" class="form-input" rows="2" placeholder="给老师看的说明，比如：这版主要改了XX..."></textarea></div>
    </div>` : '';

  const teacherUpload = role === 'teacher' ? `
    <div class="ws-requirement-card">
      <h4>📎 教师上传批注/参考材料（可选）</h4>
      <input type="file" id="ws-teacher-file" accept=".pdf,.doc,.docx,.txt,.zip,.rar" style="margin-top:8px" onchange="handleWsTeacherFile(this,'${aid}')">
    </div>` : '';

  function paperRow(p) {
    const isT = p.uploadedBy === 'teacher';
    const badgeCls = p.reviewStatus || 'pending';
    const statusLabel = {'pending':'待批阅','revised':'需修改','approved':'已通过'}[badgeCls] || badgeCls;
    const sizeKB = (p.fileSizeBytes / 1024).toFixed(1);
    const meta1 = role === 'teacher' && !isT ? `
      <button class="btn btn-outline btn-sm" data-review="${p.id}">批阅</button>
      <button class="btn btn-outline btn-sm" onclick="downloadWsPaper(${p.id})">⬇️ 下载</button>`
      : `<button class="btn btn-outline btn-sm" onclick="downloadWsPaper(${p.id})">⬇️ 下载</button>`;
    const commentRow = (role === 'teacher' && !isT) ? `
      <div id="ws-review-${p.id}" style="display:none;margin-top:10px;padding:12px;background:var(--bg);border-radius:8px;border-left:3px solid var(--primary)">
        <div class="form-group"><label>教师评语</label><textarea id="ws-review-comment-${p.id}" class="form-input" rows="2" placeholder="批注、修改建议...">${p.teacherComment || ''}</textarea></div>
        <div style="display:flex;gap:8px;align-items:center">
          <select id="ws-review-status-${p.id}" class="form-input" style="width:auto">
            <option value="pending" ${badgeCls==='pending'?'selected':''}>待批阅</option>
            <option value="revised" ${badgeCls==='revised'?'selected':''}>需修改</option>
            <option value="approved" ${badgeCls==='approved'?'selected':''}>已通过</option>
          </select>
          <input id="ws-review-grade-${p.id}" class="form-input" style="width:80px" type="number" min="0" max="100" placeholder="评分" value="${p.grade || ''}">
          <button class="btn btn-primary btn-sm" onclick="submitWsReview(${p.id})">保存批阅</button>
          <button class="btn btn-outline btn-sm" onclick="document.getElementById('ws-review-${p.id}').style.display='none'">取消</button>
        </div>
      </div>` : '';
    return `
      <div class="ws-paper-row ${isT ? 'teacher-upload' : ''}">
        <div class="ws-paper-ver">v${p.versionNo}</div>
        <div class="ws-paper-info">
          <div class="ws-paper-title">${p.fileName}${isT ? ' <span style="font-size:11px;color:#d97706">[教师上传]</span>' : ''}</div>
          <div class="ws-paper-meta">${p.title ? '📝 ' + p.title + '  ·  ' : ''}${sizeKB} KB · 上传于 ${new Date(p.createdAt).toLocaleString('zh-CN')}${isT ? '' : ' · 来自学生'}</div>
          ${p.note ? `<div class="ws-paper-note">"${p.note}"</div>` : ''}
          ${p.teacherComment ? `<div style="font-size:12px;color:#7c3aed;margin-top:4px">👤 教师批注：${p.teacherComment}</div>` : ''}
          ${p.grade ? `<div style="font-size:12px;color:#16a34a;margin-top:2px">⭐ 评分：${p.grade}/100</div>` : ''}
          ${commentRow}
        </div>
        <div style="text-align:right">
          ${isT ? '' : `<span class="ws-paper-grade ${badgeCls}">${statusLabel}</span>`}
          <div style="margin-top:8px">${meta1}</div>
        </div>
      </div>`;
  }

  body.innerHTML = `
    ${uploadHtml}
    ${teacherUpload}
    ${papers.length === 0 && !uploadHtml ? `<div class="empty-card">暂无论文版本</div>` : ''}
    ${papers.length > 0 ? papers.map(paperRow).join('') : ''}
  `;

  // 教师批阅按钮
  if (role === 'teacher') {
    body.querySelectorAll('[data-review]').forEach(btn => {
      btn.addEventListener('click', () => {
        const panel = $('#ws-review-' + btn.dataset.review);
        if (panel) panel.style.display = panel.style.display === 'none' ? 'block' : 'none';
      });
    });
  }
}

// ========= 公告：渲染 =========
async function renderWsAnnoTab(ws, role) {
  const body = $('#ws-tab-body');
  body.innerHTML = `<div class="ws-anno-page">
    <div class="ws-anno-toolbar">
      <div class="ws-anno-title">📢 导师公告</div>
      ${role === 'teacher' ? `<button class="ws-chat-float-btn" onclick="openAnnoModal()">➕ 发布公告</button>` : '<span class="ws-anno-tip">查看你导师发布的全部通知</span>'}
    </div>
    <div id="ws-anno-list" class="ws-anno-list"><div class="ws-chat-empty">加载中...</div></div>
  </div>`;

  const r = await api('GET', '/workspace/announcements');
  if (!r.success) { toast(r.message, 'error'); return; }
  const annos = r.data || [];
  const list = $('#ws-anno-list');
  if (annos.length === 0) {
    list.innerHTML = `<div class="ws-chat-empty"><div class="ws-chat-empty-icon">📭</div>暂无公告</div>`;
    return;
  }
  list.innerHTML = annos.map(a => {
    const t = new Date(a.createdAt).toLocaleString('zh-CN', { hour12: false });
        const fileHtml = a.fileName ? `<div class="ws-anno-file">📎 <a href="${API}/workspace/announce/file/${a.id}" target="_blank">${escapeHtml(a.fileName)}</a>
        <button onclick="window.open('${API}/workspace/announce/file/${a.id}','_blank')" class="ws-anno-download">⬇ 下载</button>
      </div>` : '';
    const contentHtml = a.content ? `<div class="ws-anno-content">${escapeHtml(a.content).split('\n').join('<br>')}</div>` : '';
    return `<div class="ws-anno-card">
      <div class="ws-anno-badge">公告</div>
      <div class="ws-anno-title-line">
        <div class="ws-anno-title-text">${escapeHtml(a.title)}</div>
      </div>
      ${contentHtml}
      ${fileHtml}
      <div class="ws-anno-meta">👤 ${escapeHtml(a.teacherName)} · 🕐 ${t}</div>
    </div>`;
  }).join('');
}

// ========= 公告：发布弹窗（老师）=========
async function openAnnoModal() {
  const existing = document.getElementById('ws-anno-modal');
  if (existing) { existing.remove(); return; }

  const mask = document.createElement('div');
  mask.id = 'ws-anno-modal';
  mask.className = 'ws-chat-modal-mask';
  mask.innerHTML = `
    <div class="ws-chat-modal" style="width:560px;height:auto;max-height:80vh;">
      <div class="ws-chat-modal-header">
        <div class="ws-chat-header-left">
          <div class="ws-chat-header-avatar" style="background:rgba(255,255,255,.22);font-size:16px;">📢</div>
          <div class="ws-chat-header-title">
            <div class="ws-chat-header-name">发布公告</div>
            <div class="ws-chat-header-status">你的所有学生都能收到</div>
          </div>
        </div>
        <button class="ws-chat-modal-close" onclick="document.getElementById('ws-anno-modal').remove()">✕</button>
      </div>
      <div style="padding:20px 22px;display:flex;flex-direction:column;gap:14px;">
        <div>
          <label style="font-size:12px;color:#8b90a8;margin-bottom:6px;display:block;font-weight:500;">标题<span style="color:#ff6b8a;">*</span></label>
          <input id="ws-anno-title" maxlength="120" placeholder="如：学校关于毕设格式的新要求"
                 style="width:100%;padding:10px 14px;border:1.5px solid #e4e7f0;border-radius:10px;font-size:14px;outline:none;font-family:inherit;">
        </div>
        <div>
          <label style="font-size:12px;color:#8b90a8;margin-bottom:6px;display:block;font-weight:500;">内容（可选）</label>
          <textarea id="ws-anno-content" rows="5" placeholder="写点什么给同学们..."
                    style="width:100%;padding:10px 14px;border:1.5px solid #e4e7f0;border-radius:10px;font-size:14px;outline:none;font-family:inherit;resize:vertical;line-height:1.55;"></textarea>
        </div>
        <div>
          <label style="font-size:12px;color:#8b90a8;margin-bottom:6px;display:block;font-weight:500;">附件（可选 · PDF/DOC/ZIP 等）</label>
          <div id="ws-anno-file-area" style="border:1.5px dashed #d4d8e8;border-radius:10px;padding:14px;text-align:center;cursor:pointer;transition:all .18s;background:#fafbff;" onclick="document.getElementById('ws-anno-file-input').click()"
               ondragover="event.preventDefault();this.style.borderColor='#818cf8';this.style.background='#f0f2ff'"
               ondragleave="this.style.borderColor='#d4d8e8';this.style.background='#fafbff'"
               ondrop="event.preventDefault();this.style.borderColor='#d4d8e8';this.style.background='#fafbff';handleAnnoFile(event.dataTransfer.files[0])">
            <div id="ws-anno-file-placeholder" style="color:#9aa0b5;font-size:13px;">📎 点击选择文件 或 拖拽到这里</div>
            <input id="ws-anno-file-input" type="file" style="display:none" onchange="handleAnnoFile(this.files[0])">
          </div>
        </div>
        <div style="display:flex;justify-content:flex-end;gap:10px;margin-top:4px;">
          <button class="btn" style="background:#f1f3fa;color:#5a6278;border:none;border-radius:10px;padding:9px 18px;cursor:pointer;" onclick="document.getElementById('ws-anno-modal').remove()">取消</button>
          <button class="ws-chat-send-btn" onclick="submitAnno()">发布公告</button>
        </div>
      </div>
    </div>
  `;
  mask.onclick = (e) => { if (e.target === mask) mask.remove(); };
  document.body.appendChild(mask);

  // focus 样式交给 CSS :focus 处理，不加 JS 避免 null 崩溃
}

let _annoFile = null; // {fileName, filePath}

async function handleAnnoFile(file) {
  if (!file) return;
  const MAX = 100 * 1024 * 1024;
  if (file.size > MAX) { toast('文件不能超过 100MB', 'error'); return; }
  const ph = document.getElementById('ws-anno-file-placeholder');
  if (ph) ph.innerHTML = '⏳ 上传中...';
  try {
    const form = new FormData();
    form.append('file', file);
    const res = await fetch(API + '/workspace/announce/upload', {
      headers: { Authorization: 'Bearer ' + getToken() },
      method: 'POST', body: form
    });
    const j = await res.json();
    if (!j.success) throw new Error(j.message);
    _annoFile = j.data;
    if (ph) ph.innerHTML = '✅ 已上传：<b style="color:#6366f1">' + escapeHtml(file.name) + '</b> <span style="color:#9aa0b5;cursor:pointer;text-decoration:underline" onclick="event.stopPropagation();clearAnnoFile()">移除</span>';
  } catch (e) {
    toast('上传失败：' + e.message, 'error');
    if (ph) ph.innerHTML = '📎 点击选择文件 或 拖拽到这里';
  }
}
function clearAnnoFile() {
  _annoFile = null;
  const ph = document.getElementById('ws-anno-file-placeholder');
  if (ph) ph.innerHTML = '📎 点击选择文件 或 拖拽到这里';
}

async function submitAnno() {
  const titleEl = $('#ws-anno-title');
  const contentEl = $('#ws-anno-content');
  const title = titleEl ? titleEl.value.trim() : '';
  const content = contentEl ? contentEl.value.trim() : '';
  if (!title) { toast('标题不能为空', 'warn'); return; }
  const body = { title, content };
  if (_annoFile) { body.fileName = _annoFile.fileName; body.filePath = _annoFile.filePath; }
  const r = await api('POST', '/workspace/announce', body);
  if (!r.success) { toast(r.message, 'error'); return; }
  toast('✅ 公告已发布！');
  _annoFile = null;
  document.getElementById('ws-anno-modal').remove();
  const role = getUser().role;
  renderWsAnnoTab(null, role);
}

function getCurrentAdvisorId() {
  // 从 workspace 顶部卡片推断；当前简化返回第一个 advisor
  const sid = getStudentId && getStudentId() || null;
  // 从 URL hash
  const h = location.hash;
  const m = h.match(/advisorId=(\d+)/);
  return m ? parseInt(m[1]) : null;
}


async function handleWsFile(input, aid) {
  const file = input.files[0]; if (!file) return;
  const title = $('#ws-paper-title').value.trim();
  const note = $('#ws-paper-note').value.trim();
  await uploadWsPaper(aid, file, title, note, 'student');
  input.value = '';
  $('#ws-paper-title').value = '';
  $('#ws-paper-note').value = '';
}

// ========= 聊天：联系老师 =========
async function renderWsChatTab(wsData, role, advisorId) {
  const me = getUser();
  const otherName = role === 'student'
    ? (wsData.teacher ? wsData.teacher.name : '导师')
    : (wsData.student ? wsData.student.name : '学生');
  const chatBoxId = 'ws-chat-box-' + advisorId;

  body.innerHTML = `
    <div class="ws-chat-wrap">
      <div class="ws-chat-header">
        💬 私信对话 · ${otherName}
        <span class="ws-chat-hint">仅限你和${role === 'student' ? '老师' : '学生'}${otherName}之间可见</span>
      </div>
      <div id="${chatBoxId}" class="ws-chat-box">
        <div class="ws-chat-empty">加载中...</div>
      </div>
      <div class="ws-chat-input">
        <textarea id="ws-chat-msg" rows="2" placeholder="输入消息，Enter 发送，Shift+Enter 换行..." onkeydown="if(event.key==='Enter' && !event.shiftKey){event.preventDefault();sendWsChat(${advisorId})}"></textarea>
        <button class="btn btn-primary" onclick="sendWsChat(${advisorId})">发送</button>
      </div>
    </div>
  `;

  const r = await api('GET', '/workspace/chat?advisorId=' + advisorId);
  if (!r.success) { toast(r.message, 'error'); return; }
  renderWsChatMsgs(r.data || [], me.id);

  // 保存最近 chatBoxId 供刷新
  window._wsChatBoxId = chatBoxId;
  window._wsChatAdvisorId = advisorId;
}

// ========= 私聊快速入口 =========
async function quickChat(advisorId) {
  const me = getUser();
  const modalId = 'ws-chat-modal';
  const existing = document.getElementById(modalId);
  if (existing) { existing.remove(); return; }

  // 从 workspace 数据里找对方名字
  const role = me.role;
  let otherName = '对方';
  try {
    const wsUrl = (role === 'teacher') ? '/workspace/teacher/students' : '/workspace/my';
    const r = await api('GET', wsUrl);
    if (r.success && r.data && r.data.length) {
      const item = r.data.find(x => x.advisorId === advisorId);
      if (item) otherName = (role === 'teacher' ? (item.student && item.student.name) : (item.teacher && item.teacher.name)) || otherName;
    }
  } catch(e) {}
  const roleLabel = role === 'student' ? '导师' : '学生';

  const mask = document.createElement('div');
  mask.id = modalId;
  mask.className = 'ws-chat-modal-mask';
  mask.innerHTML = `
    <div class="ws-chat-modal" onclick="event.stopPropagation()">
      <div class="ws-chat-modal-header">
        <div class="ws-chat-header-left">
          <div class="ws-chat-header-avatar">${otherName.charAt(0)}</div>
          <div class="ws-chat-header-title">
            <div class="ws-chat-header-name">${escapeHtml(otherName)}</div>
            <div class="ws-chat-header-status">${roleLabel} · 在线</div>
          </div>
        </div>
        <button class="ws-chat-modal-close" onclick="document.getElementById('${modalId}').remove()" title="关闭">✕</button>
      </div>
      <div class="ws-chat-modal-body" id="ws-chat-modal-box">
        <div class="ws-chat-empty">加载中...</div>
      </div>
      <div class="ws-chat-modal-input">
        <div class="ws-chat-input-wrap">
          <textarea id="ws-chat-msg-modal" rows="1" placeholder="输入消息，Enter 发送，Shift+Enter 换行..." onkeydown="if(event.key==='Enter' && !event.shiftKey){event.preventDefault();sendWsChatModal(${advisorId})}"></textarea>
        </div>
        <button class="ws-chat-send-btn" onclick="sendWsChatModal(${advisorId})">发送</button>
      </div>
    </div>
  `;
  mask.onclick = (e) => { if (e.target === mask) mask.remove(); };
  document.body.appendChild(mask);

  // 拉取对话
  const r = await api('GET', '/workspace/chat?advisorId=' + advisorId);
  if (!r.success) { toast(r.message, 'error'); mask.remove(); return; }
  window._wsModalMyId = me.id;
  window._wsModalAdvisorId = advisorId;
  window._wsModalBoxId = 'ws-chat-modal-box';
  renderWsChatMsgs(r.data || [], me.id);
}

async function sendWsChatModal(advisorId) {
  const ta = document.getElementById('ws-chat-msg-modal');
  const text = ta.value.trim();
  if (!text) return;
  const r = await api('POST', '/workspace/chat', { advisorId, content: text });
  if (!r.success) { toast(r.message, 'error'); return; }
  ta.value = '';
  const r2 = await api('GET', '/workspace/chat?advisorId=' + advisorId);
  if (r2.success) renderWsChatMsgs(r2.data || [], window._wsModalMyId || getUser().id);
}


function renderWsChatMsgs(msgs, myId) {
  const boxId = window._wsModalBoxId || window._wsChatBoxId; const box = $('#' + boxId);
  if (!box) return;
  if (!msgs || msgs.length === 0) {
    box.innerHTML = `<div class="ws-chat-empty"><div class="ws-chat-empty-icon">💬</div>还没开始对话<br>发条消息打个招呼吧 ~</div>`;
    return;
  }

  // 日期分割线辅助
  let lastDay = '';
  const today = new Date().toLocaleDateString('zh-CN');
  const yesterday = new Date(Date.now()-86400000).toLocaleDateString('zh-CN');

  box.innerHTML = msgs.map(m => {
    const d = new Date(m.sentAt);
    const dayStr = d.toLocaleDateString('zh-CN');
    const timeStr = d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false });

    let daySep = '';
    if (dayStr !== lastDay) {
      const label = dayStr === today ? '今天' : dayStr === yesterday ? '昨天' : dayStr;
      daySep = `<div class="ws-chat-day-sep">${label}</div>`;
      lastDay = dayStr;
    }

    const isMine = String(m.senderId) === String(myId);
    const name = m.senderName || (m.senderRole === 'student' ? '同学' : '老师');
    const avatar = name.charAt(0);
    return daySep + `
      <div class="ws-chat-msg ${isMine ? 'mine' : 'theirs'}">
        <div class="ws-chat-msg-avatar">${avatar}</div>
        <div class="ws-chat-bubble-wrap">
          <div class="ws-chat-sender">${escapeHtml(name)}</div>
          <div class="ws-chat-bubble">${escapeHtml(m.content)}</div>
          <div class="ws-chat-time">${timeStr}</div>
        </div>
      </div>`;
  }).join('');

  box.scrollTop = box.scrollHeight;
}

async function sendWsChat(advisorId) {
  const me = getUser();
  const ta = $('#ws-chat-msg');
  const text = ta.value.trim();
  if (!text) { toast('消息不能为空', 'warn'); return; }
  const r = await api('POST', '/workspace/chat', { advisorId, content: text });
  if (!r.success) { toast(r.message, 'error'); return; }
  ta.value = '';
  // 重新拉取对话
  const r2 = await api('GET', '/workspace/chat?advisorId=' + advisorId);
  if (r2.success) renderWsChatMsgs(r2.data || [], me.id);
}

function escapeHtml(s) {
  return (s || '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
}

function handleWsDrop(e, aid) {
  const f = e.dataTransfer.files[0]; if (!f) return;
  e.target.parentNode.querySelector('input[type=file]').files = e.dataTransfer.files;
  handleWsFile(e.target.parentNode.querySelector('input[type=file]'), aid);
}
async function handleWsTeacherFile(input, aid) {
  const file = input.files[0]; if (!file) return;
  await uploadWsPaper(aid, file, '', '', 'teacher');
  input.value = '';
}

async function uploadWsPaper(aid, file, title, note, role) {
  const fd = new FormData();
  fd.append('advisorId', aid);
  fd.append('file', file);
  if (title) fd.append('title', title);
  if (note) fd.append('note', note);
  const me = getUser();
  if (me.role === 'teacher' && role === 'teacher') { /* OK */ }
  if (me.role === 'teacher' && role === 'student') { /* 不会发生 */ }

  toast('上传中...', 'info');
  const token = localStorage.getItem('zhixue_token');
  try {
    const resp = await fetch('/api/workspace/papers/upload', {
      method: 'POST',
      headers: { 'Authorization': 'Bearer ' + token },
      body: fd
    });
    const data = await resp.json();
    toast(data.message, data.success ? 'success' : 'error');
    if (data.success) {
      // 刷新工作台
      const currentTab = $('.ws-tab.active')?.dataset.tab || 'paper';
      const detail = role === 'student' ? null : null;
      await renderWorkspaceDetail(null, me.role, aid);
      // 保持 paper tab
      setTimeout(() => {
        const t = $('.ws-tab[data-tab="paper"]'); if (t) t.click();
      }, 50);
    }
  } catch (e) { toast('上传失败', 'error'); }
}

function downloadWsPaper(paperId) {
  const token = localStorage.getItem('zhixue_token');
  const a = document.createElement('a');
  a.href = '/api/workspace/papers/' + paperId + '/download';
  a.setAttribute('download', '');
  // 更可靠的方式：fetch + blob
  fetch('/api/workspace/papers/' + paperId + '/download', {
    headers: { 'Authorization': 'Bearer ' + token }
  }).then(r => {
    if (!r.ok) throw new Error(r.status);
    return r.blob();
  }).then(b => {
    const url = URL.createObjectURL(b);
    const a2 = document.createElement('a');
    a2.href = url;
    a2.download = 'paper_v' + paperId + '.pdf';
    document.body.appendChild(a2); a2.click(); a2.remove();
    URL.revokeObjectURL(url);
  }).catch(() => toast('下载失败', 'error'));
}

async function submitWsReq(aid) {
  const content = $('#ws-req-content').value.trim();
  if (!content) { toast('请填写要求内容', 'error'); return; }
  const topicsText = $('#ws-req-topics').value.trim();
  const topics = topicsText ? topicsText.split('\n').map(line => {
    const [title, desc] = line.split('|').map(s => (s || '').trim());
    return { title: title, desc: desc };
  }).filter(t => t.title) : [];
  const deadlineRaw = $('#ws-req-deadline').value;
  const deadline = deadlineRaw ? deadlineRaw.replace('T', 'T') + ':00' : null;
  const r = await api('POST', '/workspace/requirements', {
    advisorId: aid, content, topics, deadline
  });
  toast(r.message, r.success ? 'success' : 'error');
  if (r.success) {
    await renderWorkspaceDetail(null, 'teacher', aid);
    setTimeout(() => { const t = $('.ws-tab[data-tab="req"]'); if (t) t.click(); }, 50);
  }
}

async function submitWsReview(paperId) {
  const comment = $('#ws-review-comment-' + paperId).value.trim();
  const status = $('#ws-review-status-' + paperId).value;
  const gradeStr = $('#ws-review-grade-' + paperId).value.trim();
  const grade = gradeStr ? parseInt(gradeStr) : null;
  const r = await api('POST', '/workspace/papers/' + paperId + '/review', { comment, status, grade });
  toast(r.message, r.success ? 'success' : 'error');
  if (r.success) {
    const aid = _wsSelectedAdvisorId;
    await renderWorkspaceDetail(null, 'teacher', aid);
    setTimeout(() => { const t = $('.ws-tab[data-tab="paper"]'); if (t) t.click(); }, 50);
  }
}

// ===== AI 智能体助手（WorkBuddy 免密钥大模型，前端 SDK 直连流式）=====
let aiHistory = [];   // [{role:'user'|'assistant', content}]
let aiSending = false;
let aiCloud = null;
let aiModelId = null;
let aiCloudReady = false;

const AI_SYSTEM_PROMPT = '你是「智学选课」大学生选课平台的内置 AI 助手。请用简体中文、友好简洁的口吻回答，'
  + '帮助用户解答选课流程与规则、推荐学习计划、解释课程难点、提供复习与时间管理建议。'
  + '回答控制在 300 字以内，适当用列表让内容清晰。';

// 懒加载 WorkBuddy 云 SDK（避免 CDN 阻塞页面渲染）
let aiSdkLoading = null;
function aiLoadSdk() {
  if (typeof WorkBuddyCloud !== 'undefined') return Promise.resolve();
  if (aiSdkLoading) return aiSdkLoading;
  aiSdkLoading = new Promise((resolve, reject) => {
    const s = document.createElement('script');
    s.src = (window.WB_PUBLIC_CONFIG && window.WB_PUBLIC_CONFIG.sdkSrc) || '';
    s.onload = resolve;
    s.onerror = () => { aiSdkLoading = null; reject(new Error('AI 组件加载失败，请检查网络后刷新重试')); };
    document.head.appendChild(s);
  });
  return aiSdkLoading;
}

// 懒初始化：创建云客户端并拉取可用模型
async function aiEnsureCloud() {
  if (aiCloudReady) return true;
  await aiLoadSdk();
  if (typeof WorkBuddyCloud === 'undefined') {
    throw new Error('AI 组件未加载完成，请刷新页面重试');
  }
  aiCloud = WorkBuddyCloud.createWorkBuddyCloud({
    endpoint: WB_PUBLIC_CONFIG.endpoint,
    oauthRelayBaseUrl: WB_PUBLIC_CONFIG.oauthRelayBaseUrl,
    publishableKey: WB_PUBLIC_CONFIG.publishableKey,
  });
  const models = await aiCloud.llm.models.list();
  const model = (models.find((m) => m.disabled !== true)) || (models.length ? models[0] : null);
  if (!model) throw new Error('当前应用暂无可用的 AI 模型');
  aiModelId = model.id;
  aiCloudReady = true;
  return true;
}

function aiChatToggle(force) {
  const p = $('#ai-chat-panel');
  if (!p) return;
  const show = force !== undefined ? force : (p.style.display === 'none');
  p.style.display = show ? 'flex' : 'none';
  if (show) {
    // 无真实对话时显示欢迎卡片，否则隐藏（保留上下文）
    const welcome = $('#ai-chat-welcome');
    const hasMsg = $('#ai-chat-msgs').querySelector('.ai-msg');
    if (welcome) welcome.style.display = hasMsg ? 'none' : '';
    const suggest = $('#ai-chat-suggest');
    if (suggest) suggest.classList.toggle('hidden', !!hasMsg);
    setTimeout(() => { const i = $('#ai-chat-input'); if (i) i.focus(); }, 60);
  }
}

function aiMsg(role, text) {
  const box = $('#ai-chat-msgs');
  const div = document.createElement('div');
  div.className = 'ai-msg ' + role;
  const avatar = role === 'user' ? '<div class="ai-avatar user">我</div>' : '<div class="ai-avatar">智</div>';
  div.innerHTML = avatar + '<div class="ai-bubble"></div>';
  div.querySelector('.ai-bubble').textContent = text;
  box.appendChild(div);
  box.scrollTop = box.scrollHeight;
  return div;
}

function aiTyping(on) {
  let t = document.getElementById('ai-chat-typing');
  if (on && !t) {
    t = document.createElement('div');
    t.id = 'ai-chat-typing';
    t.className = 'ai-msg assistant';
    t.innerHTML = '<div class="ai-avatar">智</div><div class="ai-bubble"><span class="ai-dot"></span><span class="ai-dot"></span><span class="ai-dot"></span></div>';
    $('#ai-chat-msgs').appendChild(t);
  } else if (!on && t) {
    t.remove();
  }
  if (on) $('#ai-chat-msgs').scrollTop = $('#ai-chat-msgs').scrollHeight;
}

function aiErrMsg(e) {
  const code = (e && (e.error && e.error.code || e.code)) || '';
  if (code.indexOf('auth_') === 0) {
    return '⚠️ 当前访问域名不在应用授权范围内。请通过 WorkBuddy 发布本应用后，再从应用地址打开即可使用此模型。';
  }
  if (code.indexOf('quota_') === 0) {
    return '⚠️ 调用额度已用尽或触发限流，请稍后再试。';
  }
  if (code.indexOf('gateway_') === 0 || code.indexOf('model_') === 0) {
    return '⚠️ 模型服务暂时不可用，请稍后重试。';
  }
  return '⚠️ ' + (e && e.message ? e.message : '请求失败，请稍后重试');
}

async function aiSend(text) {
  const input = $('#ai-chat-input');
  const msg = (text !== undefined ? String(text) : input.value).trim();
  if (!msg || aiSending) return;
  input.value = '';
  input.style.height = 'auto';
  // 首次真正发送：隐藏欢迎卡片与快捷提问
  const welcome = document.getElementById('ai-chat-welcome');
  if (welcome) welcome.style.display = 'none';
  const suggest = document.getElementById('ai-chat-suggest');
  if (suggest) suggest.classList.add('hidden');
  aiMsg('user', msg);
  aiHistory.push({ role: 'user', content: msg });
  if (aiHistory.length > 16) aiHistory = aiHistory.slice(-16);
  aiSending = true;

  const bubble = aiMsg('assistant', '');
  const bEl = bubble.querySelector('.ai-bubble');
  let acc = '';
  const box = $('#ai-chat-msgs');

  // 本地开发（非 WorkBuddy 发布域名）直接走后端免费模型，干净无外部依赖；
  // 发布到 *.workbuddy.host 后自动改用 WorkBuddy 自带模型
  const isWBHost = /(^|\.)workbuddy\.host$/.test(location.hostname);
  if (!isWBHost) {
    aiSendFallback(msg, bEl);
    return;
  }

  // 1) 优先走 WorkBuddy 免密钥模型（流式直连）
  try {
    await aiEnsureCloud();
    const messages = [
      { role: 'system', content: AI_SYSTEM_PROMPT },
      ...aiHistory.slice(0, -1).map((m) => ({ role: m.role, content: m.content })),
      { role: 'user', content: msg },
    ];
    for await (const chunk of aiCloud.llm.chat.completions.create({
      model: aiModelId,
      messages,
      stream: true,
      stream_options: { include_usage: true },
    })) {
      const delta = chunk.choices && chunk.choices[0] && chunk.choices[0].delta;
      if (delta && delta.content) {
        acc += delta.content;
        bEl.textContent = acc;
        box.scrollTop = box.scrollHeight;
      }
    }
    if (acc) {
      aiHistory.push({ role: 'assistant', content: acc });
      if (aiHistory.length > 16) aiHistory = aiHistory.slice(-16);
    } else {
      bEl.textContent = '⚠️ 未返回内容，请重试';
    }
  } catch (e) {
    const code = (e && (e.error && e.error.code || e.code)) || '';
    // 域名不匹配（多为本地开发 localhost）时，回退到后端免费模型，保证可用
    if (code.indexOf('auth_') === 0) {
      aiSendFallback(msg, bEl);
      return;
    }
    if (acc) {
      bEl.textContent = acc + '\n\n⚠️ 生成被中断，可重新提问';
    } else {
      bEl.textContent = aiErrMsg(e);
    }
  }
  aiSending = false;
  input.focus();
}

// 兜底：云端鉴权不通过（本地开发）时走后端免费模型
async function aiSendFallback(msg, bEl) {
  try {
    const r = await api('POST', '/ai/chat', { message: msg, history: aiHistory.slice(0, -1) });
    if (r.success && r.data && r.data.reply) {
      bEl.textContent = r.data.reply;
      aiHistory.push({ role: 'assistant', content: r.data.reply });
      if (aiHistory.length > 16) aiHistory = aiHistory.slice(-16);
    } else {
      bEl.textContent = '⚠️ ' + (r.message || '请求失败，请稍后重试');
    }
  } catch (e) {
    bEl.textContent = '⚠️ 网络异常，请稍后重试';
  }
  aiSending = false;
  const input = $('#ai-chat-input');
  if (input) input.focus();
}

function bindAiChat() {
  const btn = document.getElementById('ai-chat-btn');
  if (!btn) return;
  btn.addEventListener('click', () => aiChatToggle());
  const closeBtn = document.getElementById('ai-chat-close');
  if (closeBtn) closeBtn.addEventListener('click', () => aiChatToggle(false));
  const sendBtn = document.getElementById('ai-chat-send');
  if (sendBtn) sendBtn.addEventListener('click', () => aiSend());
  const input = document.getElementById('ai-chat-input');
  if (input) {
    input.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); aiSend(); }
    });
    input.addEventListener('input', () => {
      input.style.height = 'auto';
      input.style.height = Math.min(input.scrollHeight, 110) + 'px';
    });
  }
  document.querySelectorAll('.ai-suggest-btn').forEach((b) => {
    b.addEventListener('click', () => aiSend(b.textContent));
  });
  document.addEventListener('keydown', (e) => {
    const p = $('#ai-chat-panel');
    if (e.key === 'Escape' && p && p.style.display !== 'none') aiChatToggle(false);
  });
}

// 关键：面板 DOM 在 app.js <script> 标签之后才解析，必须等 DOM 就绪再绑定事件，
// 否则 getElementById 取到 null 会中断绑定，导致“能打开面板但无法发送消息”
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', bindAiChat);
} else {
  bindAiChat();
}
