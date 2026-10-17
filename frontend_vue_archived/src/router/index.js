import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuthStore } from '@/store/auth'
import AppLayout from '@/components/AppLayout.vue'
import Login from '@/views/Login.vue'

// 学生端
const studentPages = {
  'student-dashboard': () => import('@/views/student/StudentDashboard.vue'),
  browse: () => import('@/views/student/Browse.vue'),
  intents: () => import('@/views/student/Intents.vue'),
  mycourses: () => import('@/views/student/MyCourses.vue'),
  waitlist: () => import('@/views/student/Waitlist.vue'),
  schedule: () => import('@/views/student/Schedule.vue'),
  scores: () => import('@/views/student/Scores.vue'),
  graduation: () => import('@/views/student/Graduation.vue'),
  advisor: () => import('@/views/student/Advisor.vue'),
}
// 教师端
const teacherPages = {
  'teacher-dashboard': () => import('@/views/teacher/TeacherDashboard.vue'),
  'teach-courses': () => import('@/views/teacher/TeachCourses.vue'),
  approve: () => import('@/views/teacher/Approve.vue'),
  'student-list': () => import('@/views/teacher/StudentList.vue'),
  'grade-mgr': () => import('@/views/teacher/GradeMgr.vue'),
  reviews: () => import('@/views/teacher/Reviews.vue'),
  advise: () => import('@/views/teacher/Advise.vue'),
}
// 管理员端
const adminPages = {
  'admin-dashboard': () => import('@/views/admin/AdminDashboard.vue'),
  rounds: () => import('@/views/admin/Rounds.vue'),
  semesters: () => import('@/views/admin/Semesters.vue'),
  classrooms: () => import('@/views/admin/Classrooms.vue'),
  'course-mgr': () => import('@/views/admin/CourseMgr.vue'),
  'user-mgr': () => import('@/views/admin/UserMgr.vue'),
  announce: () => import('@/views/admin/Announce.vue'),
  stats: () => import('@/views/admin/Stats.vue'),
  'advise-mgr': () => import('@/views/admin/AdviseMgr.vue'),
  logs: () => import('@/views/admin/Logs.vue'),
}
// 共享（学生/教师/管理员）
const sharedPages = {
  workspace: () => import('@/views/Workspace.vue'),
  profile: () => import('@/views/Profile.vue'),
}

function child(path, comp, title) {
  return { path, component: comp, meta: { title } }
}

const routes = [
  { path: '/login', component: Login, meta: { public: true } },
  {
    path: '/',
    component: AppLayout,
    meta: { requiresAuth: true },
    children: [
      ...Object.entries(studentPages).map(([p, c]) => child(p, c, pageTitle(p))),
      ...Object.entries(teacherPages).map(([p, c]) => child(p, c, pageTitle(p))),
      ...Object.entries(adminPages).map(([p, c]) => child(p, c, pageTitle(p))),
      ...Object.entries(sharedPages).map(([p, c]) => child(p, c, pageTitle(p))),
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/login' },
]

function pageTitle(p) {
  const map = {
    'student-dashboard': '首页', browse: '课程浏览', intents: '我的意向',
    mycourses: '我的课程', waitlist: '候补队列', schedule: '我的课表',
    scores: '成绩查询', graduation: '毕业进度', advisor: '毕业设计导师',
    'teacher-dashboard': '首页', 'teach-courses': '我的课程', approve: '选课审批',
    'student-list': '选课学生', 'grade-mgr': '成绩管理', reviews: '课程评价',
    advise: '毕业设计指导', 'admin-dashboard': '首页', rounds: '选课轮次管理',
    semesters: '学期管理', classrooms: '教室管理', 'course-mgr': '课程管理',
    'user-mgr': '用户管理', announce: '公告管理', stats: '统计报表',
    'advise-mgr': '导师管理', logs: '系统日志', workspace: '毕业设计工作台',
    profile: '个人信息',
  }
  return map[p] || '页面'
}

const router = createRouter({
  history: createWebHashHistory(import.meta.env.BASE_URL),
  routes,
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.isLoggedIn) return '/login'
  if (to.path === '/login' && auth.isLoggedIn) return '/'
  if (to.path === '/') {
    if (!auth.isLoggedIn) return '/login'
    const map = { student: '/student-dashboard', teacher: '/teacher-dashboard', admin: '/admin-dashboard' }
    return map[auth.role] || '/student-dashboard'
  }
  return true
})

export default router
