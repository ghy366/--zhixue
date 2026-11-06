package com.zhixue.config;

import com.zhixue.entity.User;
import com.zhixue.repository.UserRepository;
import com.zhixue.util.SessionStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * 统一鉴权拦截器：在后端校验角色权限，不依赖前端路由隔离。
 * <p>
 * 规则按注册顺序匹配（具体规则在前、通配在后），第一条命中即生效：
 * <ol>
 *   <li>命中 PUBLIC → 放行（未登录也可访问）</li>
 *   <li>未命中任何规则 → 仅需登录（任意角色）</li>
 *   <li>命中角色规则 → 登录且角色必须匹配，否则 403</li>
 * </ol>
 * 未登录访问非公开接口返回 401（前端拦截器统一跳登录页）。
 * 角色来源：登录时写入 SessionStore；旧 token 无角色记录时兜底查库一次。
 */
@Component
public class RoleInterceptor implements HandlerInterceptor {

    @Autowired private UserRepository userRepo;

    private static final Set<String> ADMIN = Set.of("admin");
    private static final Set<String> TEACHER_ADMIN = Set.of("teacher", "admin");
    private static final Set<String> STUDENT = Set.of("student");
    private static final Set<String> STUDENT_TEACHER = Set.of("student", "teacher");
    /** 课题库浏览：学生选课题 / 教师管本院 / 管理员总览 */
    private static final Set<String> TOPIC_VIEW = Set.of("student", "teacher", "admin");

    /** 一条鉴权规则：HTTP 方法（* 任意）+ API 前缀 + 允许的角色（null = 公开） */
    private record Rule(String method, String prefix, Set<String> roles) {}

    private static final List<Rule> RULES = List.of(
        // ===== 公开接口：登录/注册、登录页倒计时、公开课程目录与公告 =====
        new Rule("*",    "/api/auth/login", null),
        new Rule("*",    "/api/auth/register", null),
        new Rule("GET",  "/api/system/config", null),
        new Rule("GET",  "/api/system/round-schedule", null),   // 登录页轮次横幅（未登录也要拉三轮排期）
        new Rule("GET",  "/api/courses/hot", null),
        new Rule("GET",  "/api/courses/teachers", null),
        new Rule("GET",  "/api/courses", null),
        new Rule("GET",  "/api/announcements", null),
        new Rule("GET",  "/api/enrollments/recent", null),   // 登录页报考动态滚动条（脱敏，未登录可拉）

        // ===== 管理员专属 =====
        new Rule("*",    "/api/admin", ADMIN),
        new Rule("*",    "/api/logs", ADMIN),
        new Rule("*",    "/api/data", ADMIN),
        new Rule("*",    "/api/stats", ADMIN),
        new Rule("POST", "/api/enrollments/settle", ADMIN),
        new Rule("POST", "/api/courses", ADMIN),
        new Rule("PUT",  "/api/courses", ADMIN),
        new Rule("DELETE", "/api/courses", ADMIN),
        new Rule("POST", "/api/classrooms", ADMIN),
        new Rule("PUT",  "/api/classrooms", ADMIN),
        new Rule("DELETE", "/api/classrooms", ADMIN),
        new Rule("POST", "/api/semesters", ADMIN),
        new Rule("PUT",  "/api/semesters", ADMIN),
        new Rule("DELETE", "/api/semesters", ADMIN),
        new Rule("POST", "/api/announcements", ADMIN),
        new Rule("PUT",  "/api/announcements", ADMIN),
        new Rule("DELETE", "/api/announcements", ADMIN),
        // 系统配置写操作：轮次/开关/上限/毕业要求/阶段/错峰
        new Rule("POST", "/api/system/round", ADMIN),
        new Rule("POST", "/api/system/round-open", ADMIN),
        new Rule("POST", "/api/system/round-schedule", ADMIN),
        new Rule("POST", "/api/system/confirm-minutes", ADMIN),
        new Rule("POST", "/api/system/max-credit", ADMIN),
        new Rule("POST", "/api/system/max-courses", ADMIN),
        new Rule("POST", "/api/system/graduation-reqs", ADMIN),
        new Rule("POST", "/api/system/grade-reqs", ADMIN),
        new Rule("DELETE", "/api/system/grade-reqs", ADMIN),
        new Rule("POST", "/api/system/phase", ADMIN),
        new Rule("POST", "/api/system/stagger", ADMIN),
        new Rule("GET",  "/api/system/grade-stats", ADMIN),
        new Rule("GET",  "/api/system/stagger", ADMIN),
        // 导师管理端
        new Rule("*",    "/api/advisor/admin", ADMIN),
        // 建议处理/删除（GET /api/suggestions/mine 学生规则在下方，前缀更具体需排在其前）
        new Rule("POST", "/api/suggestions/", ADMIN),
        new Rule("DELETE", "/api/suggestions/", ADMIN),
        new Rule("GET",  "/api/suggestions/mine", STUDENT_TEACHER),
        new Rule("GET",  "/api/suggestions", ADMIN),
        // 毕业申请审核（须排在下方 /api/graduation 学生规则之前）
        new Rule("GET",  "/api/graduation/applications", ADMIN),
        new Rule("POST", "/api/graduation/applications/", ADMIN),
        // 课程报名学生名单（含学号/电话，教师与管理员可看）
        new Rule("GET",  "/api/enrollments/course/", TEACHER_ADMIN),
        // 智能排课
        new Rule("POST", "/api/schedule/generate", ADMIN),
        new Rule("GET",  "/api/schedule/conflicts", ADMIN),

        // 课题库 / 双选配置：所有登录角色可读（须排在下方 /api/advisor/ 学生通配规则之前）
        new Rule("GET",  "/api/advisor/config", TOPIC_VIEW),
        new Rule("GET",  "/api/advisor/topics", TOPIC_VIEW),
        new Rule("POST", "/api/advisor/topics", TEACHER_ADMIN),
        new Rule("PUT",  "/api/advisor/topics", TEACHER_ADMIN),
        new Rule("DELETE", "/api/advisor/topics", TEACHER_ADMIN),
        // ===== 教师 + 管理员 =====
        new Rule("POST", "/api/grades", TEACHER_ADMIN),
        new Rule("GET",  "/api/grades/course/", TEACHER_ADMIN),
        new Rule("GET",  "/api/courses/teacher/", TEACHER_ADMIN),
        new Rule("*",    "/api/students/", TEACHER_ADMIN),
        new Rule("*",    "/api/advisor/teacher/", TEACHER_ADMIN),
        new Rule("GET",  "/api/waitlist/course/", TEACHER_ADMIN),

        // ===== 学生专属 =====
        new Rule("*",    "/api/intents", STUDENT),
        new Rule("POST", "/api/enrollments/enroll", STUDENT),
        new Rule("POST", "/api/enrollments/drop", STUDENT),
        new Rule("POST", "/api/enrollments/reselect", STUDENT),
        new Rule("POST", "/api/enrollments/simulate", STUDENT),
        new Rule("GET",  "/api/enrollments/mine", STUDENT),
        new Rule("GET",  "/api/enrollments/points", STUDENT),
        new Rule("GET",  "/api/enrollments/scores", STUDENT),
        new Rule("GET",  "/api/enrollments/approved", STUDENT),
        new Rule("POST", "/api/waitlist/join", STUDENT),
        new Rule("POST", "/api/waitlist/cancel", STUDENT),
        new Rule("POST", "/api/waitlist/confirm/", STUDENT),
        new Rule("GET",  "/api/waitlist/mine", STUDENT),
        new Rule("*",    "/api/graduation", STUDENT),
        new Rule("POST", "/api/reviews", STUDENT),
        new Rule("GET",  "/api/reviews/mine", STUDENT),
        new Rule("GET",  "/api/reviews/pending", STUDENT),
        new Rule("POST", "/api/suggestions", STUDENT_TEACHER),
        new Rule("*",    "/api/advisor/", STUDENT),
        new Rule("GET",  "/api/system/my-window", STUDENT)
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // CORS 预检直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;

        String path = request.getRequestURI().substring(request.getContextPath().length());
        String method = request.getMethod().toUpperCase();

        Rule matched = null;
        for (Rule r : RULES) {
            if (!path.startsWith(r.prefix())) continue;
            if (!"*".equals(r.method()) && !r.method().equals(method)) continue;
            matched = r;
            break;
        }
        // 未命中规则 → 仅需登录（任意角色）
        if (matched != null && matched.roles() == null) return true;

        String token = extractToken(request);
        Long userId = SessionStore.getUserId(token);
        if (userId == null) return deny(response, 401, "登录已过期，请重新登录");

        String role = SessionStore.getRole(token);
        if (role == null) {
            // 旧 token（重启前的会话）没有角色记录：兜底查库一次并回填
            User u = userRepo.findById(userId).orElse(null);
            role = u != null ? u.getRole() : null;
            if (role == null) return deny(response, 401, "登录状态无效，请重新登录");
        }
        // 未命中规则或命中无角色要求 → 任意已登录角色放行
        if (matched == null) return true;
        if (!matched.roles().contains(role)) {
            return deny(response, 403, "权限不足：该操作需要" + roleLabel(matched.roles()) + "权限");
        }
        return true;
    }

    private String roleLabel(Set<String> roles) {
        if (roles.equals(ADMIN)) return "管理员";
        if (roles.equals(TEACHER_ADMIN)) return "教师";
        if (roles.equals(STUDENT_TEACHER)) return "登录";
        return "学生";
    }

    /**
     * token 提取：优先 Authorization 头；回退 query 参数 token——
     * EventSource（SSE 站内通知）无法自定义请求头，前端以 ?token= 传递。
     */
    private String extractToken(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) return token.substring(7);
        if (token != null && !token.isBlank()) return token;
        return request.getParameter("token");
    }

    /** 统一 JSON 错误响应（与 ApiResponse 结构一致，前端拦截器按 message 处理） */
    private boolean deny(HttpServletResponse response, int status, String msg) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getOutputStream().write(
                ("{\"success\":false,\"message\":\"" + msg + "\"}").getBytes(StandardCharsets.UTF_8));
        return false;
    }
}
