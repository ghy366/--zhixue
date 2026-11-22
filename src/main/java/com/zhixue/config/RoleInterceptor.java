package com.zhixue.config;

import com.zhixue.entity.User;
import com.zhixue.repository.UserRepository;
import com.zhixue.util.SessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(RoleInterceptor.class);

    @Autowired private UserRepository userRepo;

    private static final Set<String> ADMIN = Set.of("admin");
    private static final Set<String> TEACHER_ADMIN = Set.of("teacher", "admin");
    private static final Set<String> STUDENT = Set.of("student");
    private static final Set<String> STUDENT_TEACHER = Set.of("student", "teacher");
    /** 课题库浏览：学生选课题 / 教师管本院 / 管理员总览 */
    private static final Set<String> TOPIC_VIEW = Set.of("student", "teacher", "admin");
    /** 与角色无关的个人操作（如公告标记已读）：任意已登录角色 */
    private static final Set<String> ALL_ROLES = Set.of("student", "teacher", "admin");

    /**
     * 一条鉴权规则：HTTP 方法（* 任意）+ API 路径 + 允许的角色（null = 公开）+ 是否精确匹配。
     * 默认前缀匹配；exact=true 时要求路径完全相等，用于 /api/courses 这类会被 /api/courses/xxx
     * 前缀吞并的公开接口，避免把教师专属 /api/courses/teacher/ 等规则架空。
     */
    private record Rule(String method, String prefix, Set<String> roles, boolean exact) {
        Rule(String method, String prefix, Set<String> roles) { this(method, prefix, roles, false); }
    }

    private static final List<Rule> RULES = List.of(
        // ===== 公开接口：登录、登录页倒计时、公开课程目录与公告 =====
        new Rule("*",    "/api/auth/login", null),
        new Rule("GET",  "/api/system/config", null),
        new Rule("GET",  "/api/system/round-schedule", null),   // 登录页轮次横幅（未登录也要拉三轮排期）
        new Rule("GET",  "/api/system/current-semester", null), // 全校当前学期口径（教师端/教务端都要读）
        new Rule("GET",  "/api/courses/hot", null),
        new Rule("GET",  "/api/courses/teachers", null),
        // 精确匹配：否则会吞掉下方 /api/courses/teacher/ 教师专属规则（前缀匹配首条命中即生效）
        new Rule("GET",  "/api/courses", null, true),
        new Rule("GET",  "/api/announcements", null),
        new Rule("GET",  "/api/enrollments/recent", null),   // 登录页报考动态滚动条（脱敏，未登录可拉）

        // ===== 补考 / 重修：具体路径在前，避免被下方的管理端通配规则吞掉 =====
        new Rule("GET",  "/api/makeup/mine", STUDENT),
        new Rule("POST", "/api/makeup/generate", ADMIN),
        new Rule("POST", "/api/makeup/", STUDENT),             // /{id}/register
        new Rule("GET",  "/api/makeup/export", ADMIN),
        new Rule("PUT",  "/api/makeup/", ADMIN),
        new Rule("GET",  "/api/makeup", ADMIN),

        new Rule("GET",  "/api/retake/mine", STUDENT),
        new Rule("GET",  "/api/retake/eligible", STUDENT),
        new Rule("GET",  "/api/retake/classes", ALL_ROLES),   // 学生选班 / 教务指定跟班班级
        new Rule("POST", "/api/retake/", STUDENT),             // /{id}/apply、/{id}/choose-class
        new Rule("GET",  "/api/retake", ADMIN),
        new Rule("PUT",  "/api/retake/", ADMIN),

        // 教学班（组班重修）：开班、排课、停开、名单均属教务操作
        new Rule("GET",  "/api/teaching-classes", TEACHER_ADMIN),
        new Rule("POST", "/api/teaching-classes/", ADMIN),
        new Rule("PUT",  "/api/teaching-classes/", ADMIN),

        // ===== 管理员专属 =====
        // 公告「标记已读 / 全部已读」是个人阅读状态，所有登录角色都可调用。
        // 必须排在下面 POST /api/announcements（管理员发布公告）之前：前缀匹配会把
        // /api/announcements/{id}/read、/api/announcements/read-all 一并判成管理员专属 → 学生 403。
        new Rule("POST", "/api/announcements/", ALL_ROLES),
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
        new Rule("POST", "/api/system/release-capacity", ADMIN),
        new Rule("POST", "/api/system/round-schedule", ADMIN),
        new Rule("POST", "/api/system/confirm-minutes", ADMIN),
        new Rule("POST", "/api/system/max-credit", ADMIN),
        new Rule("POST", "/api/system/max-courses", ADMIN),
        new Rule("POST", "/api/system/teacher-max-courses", ADMIN),   // 教师每学期授课上限（勿依赖 max-courses 旧名）
        new Rule("POST", "/api/system/semester-advance", ADMIN),      // 推进学期偏移+释放名额，破坏性操作
        new Rule("POST", "/api/system/graduation-reqs", ADMIN),
        new Rule("POST", "/api/system/grade-reqs", ADMIN),
        new Rule("DELETE", "/api/system/grade-reqs", ADMIN),
        new Rule("GET",  "/api/system/grade-reqs", ADMIN, true),      // GET 变体同样仅管理员可读
        new Rule("GET",  "/api/system/phase", ADMIN, true),           // 阶段时间窗配置
        // AI 通道动态配置（含密钥，仅管理员可读写）
        new Rule("*",    "/api/ai/channels", ADMIN),
        new Rule("POST", "/api/system/phase", ADMIN),
        new Rule("POST", "/api/system/stagger", ADMIN),
        new Rule("GET",  "/api/system/grade-stats", ADMIN),
        new Rule("GET",  "/api/system/stagger", ADMIN),
        // 补考/重修参数（封顶分、次数上限、组班门槛、报名窗口）
        new Rule("POST", "/api/system/makeup-config", ADMIN),
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
        // 轮次结束后的退课申请：学生查自己的/撤回自己的 / 教师管理员审批（具体规则必须排在通配之前）
        new Rule("GET",  "/api/drop-requests/mine", STUDENT),
        new Rule("POST", "/api/drop-requests/withdraw", STUDENT),
        new Rule("*",    "/api/drop-requests", TEACHER_ADMIN),

        // ===== 教师 + 管理员 =====
        new Rule("POST", "/api/grades", TEACHER_ADMIN),
        new Rule("GET",  "/api/grades/course/", TEACHER_ADMIN),
        new Rule("GET",  "/api/grades/student/", TEACHER_ADMIN),
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
        new Rule("GET",  "/api/enrollments/my-semester", STUDENT),
        new Rule("GET",  "/api/enrollments/my-semesters", STUDENT),
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
        new Rule("GET",  "/api/system/my-window", STUDENT),
        new Rule("GET",  "/api/grades/mine", STUDENT)
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // CORS 预检直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;

        String path = request.getRequestURI().substring(request.getContextPath().length());
        String method = request.getMethod().toUpperCase();

        Rule matched = null;
        for (Rule r : RULES) {
            boolean hit = r.exact() ? path.equals(r.prefix()) : path.startsWith(r.prefix());
            if (!hit) continue;
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
        // 未命中规则 → 任意已登录角色放行，但打 WARN 以便发现新接口漏登记鉴权规则
        if (matched == null) {
            log.warn("未登记鉴权规则的路径（默认放行任意登录角色，请确认是否应限定角色）：{} {} role={}",
                     method, path, role);
            return true;
        }
        if (!matched.roles().contains(role)) {
            return deny(response, 403, "权限不足：该操作需要" + roleLabel(matched.roles()) + "权限");
        }
        return true;
    }

    private String roleLabel(Set<String> roles) {
        if (roles.equals(ADMIN)) return "管理员";
        if (roles.equals(TEACHER_ADMIN)) return "教师";
        if (roles.equals(ALL_ROLES)) return "登录用户";
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
