// 角色分端首页路径：一个入口登录后，按角色落到各自的首页 URL
export function homePath(role?: string | null): string {
  if (role === 'teacher') return '/teacher/dashboard'
  if (role === 'admin') return '/admin/dashboard'
  return '/student/dashboard'
}

/** 从本地缓存的用户信息读角色（路由守卫/404 页等无 store 场景用） */
export function storedRole(): string | null {
  try {
    return JSON.parse(localStorage.getItem('zhixue_user') || 'null')?.role ?? null
  } catch {
    return null
  }
}
