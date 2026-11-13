import { createRouter, createWebHistory } from 'vue-router'
import { homePath, storedRole } from '@/utils/home'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue')
  },
  {
    path: '/',
    component: () => import('@/components/MainLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '', redirect: '/dashboard' },
      { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/Dashboard.vue') },
      // 角色分端首页：同一看板组件，URL 体现角色端口
      { path: 'student/dashboard', name: 'StudentDashboard', component: () => import('@/views/Dashboard.vue') },
      { path: 'teacher/dashboard', name: 'TeacherDashboard', component: () => import('@/views/Dashboard.vue') },
      { path: 'admin/dashboard', name: 'AdminDashboard', component: () => import('@/views/Dashboard.vue') },

      // 学生
      { path: 'courses', name: 'Courses', component: () => import('@/views/CourseList.vue') },
      { path: 'intents', name: 'Intents', component: () => import('@/views/Intents.vue') },
      { path: 'my-enrollments', name: 'MyEnrollments', component: () => import('@/views/MyEnrollments.vue') },
      { path: 'waitlist', name: 'Waitlist', component: () => import('@/views/Waitlist.vue') },
      { path: 'schedule', name: 'Schedule', component: () => import('@/views/Schedule.vue') },
      { path: 'grades', name: 'Grades', component: () => import('@/views/Grades.vue') },
      { path: 'graduation', name: 'Graduation', component: () => import('@/views/Graduation.vue') },
      { path: 'advisor-apply', name: 'AdvisorApply', component: () => import('@/views/AdvisorApply.vue') },
      { path: 'workspace', name: 'Workspace', component: () => import('@/views/Workspace.vue') },
      { path: 'chat', name: 'Chat', component: () => import('@/views/Chat.vue') },

      // 教师
      { path: 'teach-courses', name: 'TeachCourses', component: () => import('@/views/TeachCourses.vue') },
      { path: 'student-list', name: 'StudentList', component: () => import('@/views/StudentList.vue') },
      { path: 'drop-requests', name: 'DropRequests', component: () => import('@/views/DropRequests.vue') },
      { path: 'grade-mgr', name: 'GradeMgr', component: () => import('@/views/GradeMgr.vue') },
      { path: 'reviews', name: 'Reviews', component: () => import('@/views/Reviews.vue') },
      { path: 'advise', name: 'Advise', component: () => import('@/views/Advise.vue') },

      // 管理员
      { path: 'rounds', name: 'Rounds', component: () => import('@/views/Rounds.vue') },
      { path: 'semesters', name: 'Semesters', component: () => import('@/views/Semesters.vue') },
      { path: 'classrooms', name: 'Classrooms', component: () => import('@/views/Classrooms.vue') },
      { path: 'course-mgr', name: 'CourseMgr', component: () => import('@/views/CourseMgr.vue') },
      { path: 'user-mgr', name: 'UserMgr', component: () => import('@/views/UserMgr.vue') },
      { path: 'announce', name: 'Announce', component: () => import('@/views/Announce.vue') },
      { path: 'stats', name: 'Stats', component: () => import('@/views/Stats.vue') },
      { path: 'advise-mgr', name: 'AdviseMgr', component: () => import('@/views/AdviseMgr.vue') },
      { path: 'logs', name: 'Logs', component: () => import('@/views/Logs.vue') },
      { path: 'graduation-audit', name: 'GraduationAudit', component: () => import('@/views/GraduationAudit.vue') },
      { path: 'training-plans', name: 'TrainingPlans', component: () => import('@/views/TrainingPlans.vue') },
      { path: 'ai-channels', name: 'AiChannels', component: () => import('@/views/AiChannels.vue') },

      // 通用
      { path: 'profile', name: 'Profile', component: () => import('@/views/Profile.vue') },
      { path: 'announcements', name: 'Announcements', component: () => import('@/views/Announcements.vue') },
      { path: 'suggestions', name: 'Suggestions', component: () => import('@/views/Suggestions.vue') },
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/NotFound.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('zhixue_token')
  if (to.meta.requiresAuth && !token) {
    next('/login')
  } else if (to.path === '/login' && token) {
    next(homePath(storedRole()))
  } else {
    next()
  }
})

export default router
