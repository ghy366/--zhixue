// 导航配置：与原 app.js 的 NAV 保持一致（去掉了条件性的「工作台」项，后续可加回）
export const ROLES = { student: '学生', teacher: '老师', admin: '管理员' }

export const NAV = {
  student: [
    { page: 'student-dashboard', icon: 'home', label: '首页' },
    { page: 'browse', icon: 'grid', label: '课程浏览' },
    { page: 'intents', icon: 'cart', label: '我的意向' },
    { page: 'mycourses', icon: 'clipboard', label: '我的课程' },
    { page: 'waitlist', icon: 'hourglass', label: '候补队列' },
    { page: 'schedule', icon: 'calendar', label: '我的课表' },
    { page: 'scores', icon: 'award', label: '成绩查询' },
    { page: 'graduation', icon: 'target', label: '毕业进度' },
    { page: 'advisor', icon: 'graduation-cap', label: '毕业设计导师' },
    { page: 'workspace', icon: 'folder', label: '毕业设计工作台' },
  ],
  teacher: [
    { page: 'teacher-dashboard', icon: 'home', label: '首页' },
    { page: 'teach-courses', icon: 'book-open', label: '我的课程' },
    { page: 'approve', icon: 'check-circle', label: '选课审批' },
    { page: 'student-list', icon: 'users', label: '选课学生' },
    { page: 'grade-mgr', icon: 'edit', label: '成绩管理' },
    { page: 'reviews', icon: 'message-square', label: '课程评价' },
    { page: 'advise', icon: 'users', label: '毕业设计指导' },
    { page: 'workspace', icon: 'folder', label: '毕业设计工作台' },
  ],
  admin: [
    { page: 'admin-dashboard', icon: 'home', label: '首页' },
    { page: 'rounds', icon: 'refresh', label: '选课轮次' },
    { page: 'semesters', icon: 'calendar', label: '学期管理' },
    { page: 'classrooms', icon: 'building', label: '教室管理' },
    { page: 'course-mgr', icon: 'book', label: '课程管理' },
    { page: 'user-mgr', icon: 'users', label: '用户管理' },
    { page: 'announce', icon: 'megaphone', label: '公告管理' },
    { page: 'stats', icon: 'bar-chart', label: '统计报表' },
    { page: 'advise-mgr', icon: 'graduation-cap', label: '导师管理' },
    { page: 'logs', icon: 'file-text', label: '系统日志' },
  ],
}

export const PAGE_TITLES = {
  'student-dashboard': '首页', browse: '课程浏览', intents: '我的意向',
  mycourses: '我的课程', waitlist: '候补队列', schedule: '我的课表',
  scores: '成绩查询', graduation: '毕业进度', advisor: '毕业设计导师',
  'teacher-dashboard': '首页', 'teach-courses': '我的课程', approve: '选课审批',
  'student-list': '选课学生', 'grade-mgr': '成绩管理', reviews: '课程评价',
  advise: '毕业设计指导', 'admin-dashboard': '首页', rounds: '选课轮次管理',
  semesters: '学期管理', classrooms: '教室管理', 'course-mgr': '课程管理',
  'user-mgr': '用户管理', announce: '公告管理', stats: '统计报表',
  'advise-mgr': '导师管理', logs: '系统日志',
}
