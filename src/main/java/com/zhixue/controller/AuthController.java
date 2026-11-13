package com.zhixue.controller;

import com.zhixue.dto.ApiResponse;
import com.zhixue.entity.User;
import com.zhixue.service.AuthService;
import com.zhixue.service.EnrollmentService;
import com.zhixue.util.SessionStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired private AuthService authService;
    @Autowired private EnrollmentService enrollmentService;

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, String> body,
                                                  HttpServletRequest request) {
        String no = body.get("no");
        String password = com.zhixue.util.RsaCipher.decryptIfEncrypted(body.get("password"));
        String ip = clientIp(request);
        /* 撞库防护：同 IP + 同账号 1 分钟内失败 5 次 → 锁定 5 分钟（登录成功即清零） */
        if (com.zhixue.util.LoginRateLimiter.isLocked(ip, no)) {
            return ApiResponse.fail("登录失败次数过多，该账号已被临时锁定，请 5 分钟后再试或联系管理员重置密码");
        }
        User u = authService.login(no, password);
        if (u == null) {
            boolean lockedNow = com.zhixue.util.LoginRateLimiter.recordFailure(ip, no);
            return ApiResponse.fail(lockedNow
                    ? "连续登录失败次数过多，该账号已被临时锁定 5 分钟"
                    : "账号或密码错误");
        }
        com.zhixue.util.LoginRateLimiter.reset(ip, no);
        /* 角色入口校验：登录页按所选角色登录，选错入口给出明确提示。
         * 密码已验证正确（本人操作），不记入限流失败计数；role 未传时不校验（兼容旧客户端）。 */
        String wantRole = body.get("role");
        if (wantRole != null && !wantRole.isBlank() && !wantRole.equals(u.getRole())) {
            String cn = "student".equals(u.getRole()) ? "学生" : "teacher".equals(u.getRole()) ? "教师" : "管理员";
            return ApiResponse.fail("该账号是「" + cn + "」账号，请切换到「" + cn + "」入口登录");
        }
        // 学生登录时自动安排必修课（幂等）
        if ("student".equals(u.getRole())) {
            try { enrollmentService.ensureRequiredEnrollments(u.getId()); } catch (Exception ignored) {}
        }
        String token = SessionStore.createToken(u.getId(), u.getRole());
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("user", safeUser(u));
        return ApiResponse.ok("登录成功", data);
    }

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        if (body.containsKey("password")) body.put("password", com.zhixue.util.RsaCipher.decryptIfEncrypted(body.get("password")));
        User u = authService.registerStudent(body);
        if (u == null) return ApiResponse.fail("该学号已注册");
        return ApiResponse.ok("注册成功，请登录", null);
    }

    /**
     * 专业字典：学院 → [{专业, 班级简称, 每年级班级数}]。
     * 注册下拉 = 该字典(按年级生成标准班名) ∪ 学生档案聚合，保证新学院/新专业也可选。
     */
    private static final Map<String, List<Object[]>> MAJOR_DICT = new java.util.LinkedHashMap<>();
    static {
        // {专业, 班级简称, 每年级班级数}
        MAJOR_DICT.put("计算机学院", List.of(
                new Object[]{"计算机科学与技术", "计算机", 6},
                new Object[]{"软件工程", "软工", 6},
                new Object[]{"通信工程", "通信", 4},
                new Object[]{"网络工程", "网工", 3},
                new Object[]{"物联网工程", "物联网", 3},
                new Object[]{"数据科学与大数据技术", "数据科学", 3},
                new Object[]{"人工智能", "智能", 3}));
        MAJOR_DICT.put("信息学院", List.of(
                new Object[]{"信息安全", "信安", 3},
                new Object[]{"电子信息工程", "电信", 3},
                new Object[]{"信息管理与信息系统", "信管", 2}));
        MAJOR_DICT.put("数学学院", List.of(
                new Object[]{"数学与应用数学", "数学", 3},
                new Object[]{"信息与计算科学", "信计", 2},
                new Object[]{"统计学", "统计", 2}));
        MAJOR_DICT.put("外国语学院", List.of(
                new Object[]{"英语", "英语", 4},
                new Object[]{"商务英语", "商英", 2},
                new Object[]{"日语", "日语", 2},
                new Object[]{"翻译", "翻译", 1}));
        MAJOR_DICT.put("机械工程学院", List.of(
                new Object[]{"机械设计制造及其自动化", "机械", 4},
                new Object[]{"机械电子工程", "机电", 3},
                new Object[]{"车辆工程", "车辆", 2},
                new Object[]{"工业设计", "工设", 2}));
        MAJOR_DICT.put("经济管理学院", List.of(
                new Object[]{"工商管理", "工商", 4},
                new Object[]{"会计学", "会计", 5},
                new Object[]{"市场营销", "营销", 3},
                new Object[]{"电子商务", "电商", 3},
                new Object[]{"财务管理", "财管", 3}));
        MAJOR_DICT.put("艺术设计学院", List.of(
                new Object[]{"视觉传达设计", "视传", 4},
                new Object[]{"环境设计", "环设", 3},
                new Object[]{"数字媒体艺术", "数媒", 3},
                new Object[]{"产品设计", "产品", 2}));
        MAJOR_DICT.put("数字学院", List.of(
                new Object[]{"数字媒体技术", "数媒技", 2},
                new Object[]{"虚拟现实技术", "虚拟现实", 2}));
    }

    /** 字典班级:普通班(简称+年+两位班号+班,各年级 perGrade 个) + 专升本方向(专业名加后缀,各年级 1 个班)。
     *  年份覆盖注册页全部可选年级(2021~2026),保证选任何年级都有对应班级,不再出现「2022级配2023班」的矛盾。 */
    private static void addDictClasses(Map<String, Set<String>> classesByCollegeMajor,
                                       String college, String major, String abbr, int perGrade) {
        Set<String> cls = classesByCollegeMajor.computeIfAbsent(
                college + "|" + major, k -> new java.util.LinkedHashSet<>());
        Set<String> zsb = classesByCollegeMajor.computeIfAbsent(
                college + "|" + major + "(专升本)", k -> new java.util.LinkedHashSet<>());
        for (String year : new String[]{"2021", "2022", "2023", "2024", "2025", "2026"}) {
            for (int i = 1; i <= perGrade; i++) {
                cls.add(abbr + year + (i < 10 ? "0" : "") + i + "班");
            }
            // 专升本通常 40~80 人/专业/年 → 每年级 2 个班
            for (int i = 1; i <= 2; i++) {
                zsb.add(abbr + "专升本" + year + (i < 10 ? "0" : "") + i + "班");
            }
        }
    }

    /**
     * 注册表单下拉选项：专业字典 ∪ 学生档案聚合，得到真实存在的 学院 → 专业 → 班级 三级树，
     * 前端注册页只能选已有值，避免自由文本造成档案口径混乱。
     */
    @GetMapping("/register-options")
    public ApiResponse<Map<String, Object>> registerOptions() {
        Set<String> colleges = new java.util.LinkedHashSet<>();
        Map<String, Set<String>> majorsByCollege = new java.util.LinkedHashMap<>();
        Map<String, Set<String>> classesByCollegeMajor = new java.util.LinkedHashMap<>();
        for (User u : userRepo.findByRole("student")) {
            String college = u.getCollege(), major = u.getMajor(), cls = u.getClassNo();
            if (college == null || college.isBlank()) continue;
            colleges.add(college);
            if (major == null || major.isBlank()) continue;
            majorsByCollege.computeIfAbsent(college, k -> new java.util.LinkedHashSet<>()).add(major);
            if (cls == null || cls.isBlank()) continue;
            classesByCollegeMajor.computeIfAbsent(college + "|" + major, k -> new java.util.LinkedHashSet<>()).add(cls);
        }
        for (Map.Entry<String, List<Object[]>> en : MAJOR_DICT.entrySet()) {
            colleges.add(en.getKey());
            for (Object[] def : en.getValue()) {
                String major = (String) def[0], abbr = (String) def[1];
                int perGrade = (Integer) def[2];
                // 同名专业单列专升本方向(学制/班名不同,档案口径要分开)
                majorsByCollege.computeIfAbsent(en.getKey(), k -> new java.util.LinkedHashSet<>()).add(major);
                majorsByCollege.computeIfAbsent(en.getKey(), k -> new java.util.LinkedHashSet<>()).add(major + "(专升本)");
                addDictClasses(classesByCollegeMajor, en.getKey(), major, abbr, perGrade);
            }
        }
        Map<String, Object> data = new HashMap<>();
        data.put("colleges", colleges.stream().sorted().toList());
        // 可选年级:学生档案中真实存在的年级 ∪ 注册页年级范围(2021~2026)
        Set<Integer> grades = new java.util.TreeSet<>();
        for (User u : userRepo.findByRole("student")) {
            if (u.getGrade() != null) grades.add(u.getGrade());
        }
        for (int g = 2021; g <= 2026; g++) grades.add(g);
        data.put("grades", new java.util.ArrayList<>(grades));
        data.put("majors", majorsByCollege.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey, e -> e.getValue().stream().sorted().toList(),
                (a, b) -> a, java.util.LinkedHashMap::new)));
        data.put("classes", classesByCollegeMajor.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey, e -> e.getValue().stream().sorted().toList(),
                (a, b) -> a, java.util.LinkedHashMap::new)));
        return ApiResponse.ok(data);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(value = "Authorization", required = false) String token) {
        if (token != null) SessionStore.remove(token.replace("Bearer ", ""));
        return ApiResponse.ok("已退出", null);
    }
    @GetMapping("/me")
    public ApiResponse<java.util.Map<String, Object>> me(
            @RequestHeader(value = "Authorization", required = false) String token) {
        if (token != null && token.startsWith("Bearer ")) token = token.substring(7);
        Long uid = com.zhixue.util.SessionStore.getUserId(token);
        if (uid == null) return ApiResponse.fail("未登录");
        User u = userRepo.findById(uid).orElse(null);
        if (u == null) return ApiResponse.fail("用户不存在");
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("id", u.getId());
        map.put("no", u.getNo());
        map.put("name", u.getName());
        map.put("role", u.getRole());
        map.put("avatar", u.getAvatar());
        return ApiResponse.ok("ok", map);
    }


    @Autowired private com.zhixue.repository.UserRepository userRepo;

    @PutMapping("/profile")
    public ApiResponse<Map<String, Object>> updateProfile(@RequestHeader(value = "Authorization", required = false) String token,
                                                          @RequestBody Map<String, String> body) {
        if (token == null) return ApiResponse.fail("未登录");
        Long uid = com.zhixue.util.SessionStore.getUserId(token.replace("Bearer ", ""));
        if (uid == null) return ApiResponse.fail("登录已失效，请重新登录");
        User u = userRepo.findById(uid).orElse(null);
        if (u == null) return ApiResponse.fail("用户不存在");
        if (body.containsKey("name")) u.setName(body.get("name"));
        if (body.containsKey("college")) u.setCollege(body.get("college"));
        if (body.containsKey("major")) u.setMajor(body.get("major"));
        if (body.containsKey("classNo")) u.setClassNo(body.get("classNo"));
        if (body.containsKey("phone")) u.setPhone(body.get("phone"));
        if (body.containsKey("avatar")) u.setAvatar(body.get("avatar"));
        userRepo.save(u);
        return ApiResponse.ok("更新成功", safeUser(u));
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@RequestHeader(value = "Authorization", required = false) String token,
                                            @RequestBody Map<String, String> body) {
        if (token == null) return ApiResponse.fail("未登录");
        Long uid = com.zhixue.util.SessionStore.getUserId(token.replace("Bearer ", ""));
        if (uid == null) return ApiResponse.fail("登录已失效，请重新登录");
        User u = userRepo.findById(uid).orElse(null);
        if (u == null) return ApiResponse.fail("用户不存在");
        String oldPwd = com.zhixue.util.RsaCipher.decryptIfEncrypted(body.get("oldPassword"));
        String newPwd = com.zhixue.util.RsaCipher.decryptIfEncrypted(body.get("newPassword"));
        if (oldPwd == null || !authService.matches(oldPwd, u.getPassword())) return ApiResponse.fail("原密码错误");
        if (newPwd == null || newPwd.length() < 4) return ApiResponse.fail("新密码至少4位");
        u.setPassword(authService.encode(newPwd));
        userRepo.save(u);
        return ApiResponse.ok("密码修改成功", null);
    }

    public static Map<String, Object> safeUser(User u) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", u.getId());
        m.put("no", u.getNo());
        m.put("name", u.getName());
        m.put("role", u.getRole());
        m.put("college", u.getCollege());
        m.put("major", u.getMajor());
        m.put("grade", u.getGrade());
        m.put("classNo", u.getClassNo());
        m.put("phone", u.getPhone());
        m.put("avatar", u.getAvatar());
        return m;
    }
    /** 客户端 IP：优先取反向代理透传的 X-Forwarded-For 首段 */
    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return request.getRemoteAddr();
    }

}