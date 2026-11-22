package com.zhixue.controller;

import com.zhixue.dto.ApiResponse;
import com.zhixue.entity.User;
import com.zhixue.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired private UserRepository userRepo;
    @Autowired private com.zhixue.util.PasswordUtil passwordEncoder;
    @Autowired private com.zhixue.service.ExcelService excelService;

    /**
     * 用户列表（全校账号，服务端分页）。传 page/size 时返回
     * {items,total,page,size,counts,classes}，未传时保持旧的全量数组（旧调用方零破坏）。
     * 过滤：role 角色；classNo 班级；keyword 模糊匹配 学号/姓名/学院/专业/班级/手机号；
     * college/major/grade 精确匹配。结果按班级分组排序，便于前端插入分组行。
     */
    @GetMapping("/users")
    public ApiResponse<Object> users(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String college,
            @RequestParam(required = false) String major,
            @RequestParam(required = false) Integer grade,
            @RequestParam(required = false) String classNo,
            @RequestParam(required = false) Boolean zsbOnly) {
        List<User> allUsers = userRepo.findAll();

        // 聚合数据基于全库，不随筛选变化：角色 Tab 人数 + 班级下拉选项
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("all", (long) allUsers.size());
        counts.put("student", allUsers.stream().filter(u -> "student".equals(u.getRole())).count());
        counts.put("teacher", allUsers.stream().filter(u -> "teacher".equals(u.getRole())).count());
        counts.put("admin", allUsers.stream().filter(u -> "admin".equals(u.getRole())).count());
        List<String> classes = allUsers.stream().map(User::getClassNo)
                .filter(c -> c != null && !c.isBlank()).distinct().sorted().collect(Collectors.toList());
        // 每个班级的全库总人数：分组行不能因为分页被切断而显示偏小
        Map<String, Long> classCounts = allUsers.stream()
                .filter(u -> u.getClassNo() != null && !u.getClassNo().isBlank())
                .collect(Collectors.groupingBy(User::getClassNo, LinkedHashMap::new, Collectors.counting()));

        // 左侧组织树：只统计学生（学院 → 专业 → 班级）。教师/管理员不在此树中，
        // 否则他们没有专业和班级，会生成「未填写专业 / 未分班」这类噪音节点。
        Map<String, Map<String, Map<String, Long>>> grouped = new LinkedHashMap<>();
        for (User u : allUsers) {
            if (!"student".equals(u.getRole())) continue;
            String c = str(u.getCollege()).isEmpty() ? "未填写学院" : u.getCollege();
            String m = str(u.getMajor()).isEmpty() ? "未填写专业" : u.getMajor();
            String k = str(u.getClassNo()).isEmpty() ? "__none__" : u.getClassNo();
            grouped.computeIfAbsent(c, x -> new LinkedHashMap<>())
                    .computeIfAbsent(m, x -> new LinkedHashMap<>())
                    .merge(k, 1L, Long::sum);
        }
        List<Map<String, Object>> tree = new java.util.ArrayList<>();
        for (var ce : grouped.entrySet()) {
            long collegeCount = 0;
            List<Map<String, Object>> majors = new java.util.ArrayList<>();
            for (var me : ce.getValue().entrySet()) {
                long majorCount = 0;
                List<Map<String, Object>> cls = new java.util.ArrayList<>();
                for (var le : me.getValue().entrySet()) {
                    majorCount += le.getValue();
                    Map<String, Object> lm = new LinkedHashMap<>();
                    lm.put("name", "__none__".equals(le.getKey()) ? "未分班" : le.getKey());
                    lm.put("value", le.getKey());
                    lm.put("count", le.getValue());
                    cls.add(lm);
                }
                cls.sort(java.util.Comparator.comparing(x -> (String) x.get("name")));
                collegeCount += majorCount;
                Map<String, Object> mm = new LinkedHashMap<>();
                mm.put("name", me.getKey());
                mm.put("count", majorCount);
                mm.put("classes", cls);
                majors.add(mm);
            }
            majors.sort(java.util.Comparator.comparing(x -> (String) x.get("name")));
            Map<String, Object> cm = new LinkedHashMap<>();
            cm.put("name", ce.getKey());
            cm.put("count", collegeCount);
            cm.put("majors", majors);
            tree.add(cm);
        }
        tree.sort(java.util.Comparator.comparing(x -> (String) x.get("name")));

        List<User> all = allUsers;
        if (role != null && !role.isBlank()) {
            all = all.stream().filter(u -> role.equals(u.getRole())).toList();
        }
        if (college != null && !college.isBlank()) {
            all = all.stream().filter(u -> college.equals(u.getCollege())).toList();
        }
        if (major != null && !major.isBlank()) {
            all = all.stream().filter(u -> major.equals(u.getMajor())).toList();
        }
        if (grade != null) {
            all = all.stream().filter(u -> grade.equals(u.getGrade())).toList();
        }
        if (classNo != null && !classNo.isBlank()) {
            if ("__none__".equals(classNo)) {
                all = all.stream().filter(u -> str(u.getClassNo()).isEmpty()).toList();
            } else {
                all = all.stream().filter(u -> classNo.equals(u.getClassNo())).toList();
            }
        }
        // 专升本标签筛选：true=仅专升本学生，false=排除专升本（null=不筛选）
        if (zsbOnly != null) {
            all = all.stream().filter(u -> zsbOnly == str(u.getMajor()).contains("专升本")).toList();
        }
        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.toLowerCase();
            all = all.stream().filter(u -> (str(u.getNo()) + " " + str(u.getName()) + " "
                    + str(u.getCollege()) + " " + str(u.getMajor()) + " "
                    + str(u.getClassNo()) + " " + str(u.getPhone())).toLowerCase().contains(k)).toList();
        }
        // 有班级的按班级聚合在前，同班内按学号；无班级的教职工排在最后
        all = all.stream().sorted(java.util.Comparator
                .comparing((User u) -> str(u.getClassNo()).isEmpty() ? 1 : 0)
                .thenComparing((User u) -> str(u.getClassNo()))
                .thenComparing((User u) -> str(u.getNo())))
                .collect(Collectors.toList());

        List<Map<String, Object>> list = all.stream().map(u -> {
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("id", u.getId());
            m.put("no", u.getNo());
            m.put("name", u.getName());
            m.put("role", u.getRole());
            m.put("college", u.getCollege());
            m.put("major", u.getMajor());
            m.put("grade", u.getGrade());
            m.put("classNo", u.getClassNo());
            m.put("gender", u.getGender());
            m.put("phone", u.getPhone());
            m.put("avatar", u.getAvatar());
            return m;
        }).collect(Collectors.toList());
        if (page == null || size == null || page < 1 || size < 1) {
            return ApiResponse.ok(list);   // 兼容模式
        }
        int from = Math.min(list.size(), (page - 1) * size);
        int to = Math.min(list.size(), from + size);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", list.subList(from, to));
        data.put("total", list.size());
        data.put("page", page);
        data.put("size", size);
        data.put("counts", counts);
        data.put("classes", classes);
        data.put("classCounts", classCounts);
        data.put("tree", tree);
        return ApiResponse.ok(data);
    }

    /**
     * 学院 → 专业 → 年级 聚合（学生档案真实值），供用户管理的筛选级联使用。
     * 原先复用注册下拉的 /auth/register-options；自助注册按真实高校口径移除后，
     * 该聚合成为管理端独立接口，只聚合学生档案里真实存在的值（不再合并注册字典）。
     */
    @GetMapping("/major-dict")
    public ApiResponse<Map<String, Object>> majorDict() {
        java.util.Set<String> colleges = new java.util.LinkedHashSet<>();
        Map<String, java.util.Set<String>> majorsByCollege = new java.util.LinkedHashMap<>();
        java.util.Set<Integer> grades = new java.util.TreeSet<>();
        for (User u : userRepo.findByRole("student")) {
            String college = u.getCollege(), major = u.getMajor();
            if (college == null || college.isBlank()) continue;
            colleges.add(college);
            if (major == null || major.isBlank()) continue;
            majorsByCollege.computeIfAbsent(college, k -> new java.util.LinkedHashSet<>()).add(major);
            if (u.getGrade() != null) grades.add(u.getGrade());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("colleges", colleges.stream().sorted().toList());
        data.put("majors", majorsByCollege.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey, e -> e.getValue().stream().sorted().toList(),
                (a, b) -> a, java.util.LinkedHashMap::new)));
        data.put("grades", new java.util.ArrayList<>(grades));
        return ApiResponse.ok(data);
    }

    /**
     * 用户导出为真正的 .xlsx（Excel）。筛选参数与 /admin/users 完全一致，
     * 导出的是当前筛选范围的全量结果（不受分页影响）。
     */
    @GetMapping("/users/export")
    public org.springframework.http.ResponseEntity<byte[]> exportUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String college,
            @RequestParam(required = false) String major,
            @RequestParam(required = false) Integer grade,
            @RequestParam(required = false) String classNo,
            @RequestParam(required = false) Boolean zsbOnly) {
        List<User> all = userRepo.findAll();
        if (role != null && !role.isBlank()) all = all.stream().filter(u -> role.equals(u.getRole())).toList();
        if (college != null && !college.isBlank()) all = all.stream().filter(u -> college.equals(u.getCollege())).toList();
        if (major != null && !major.isBlank()) all = all.stream().filter(u -> major.equals(u.getMajor())).toList();
        if (grade != null) all = all.stream().filter(u -> grade.equals(u.getGrade())).toList();
        if (classNo != null && !classNo.isBlank()) {
            all = "__none__".equals(classNo)
                    ? all.stream().filter(u -> str(u.getClassNo()).isEmpty()).toList()
                    : all.stream().filter(u -> classNo.equals(u.getClassNo())).toList();
        }
        if (zsbOnly != null) all = all.stream().filter(u -> zsbOnly == str(u.getMajor()).contains("专升本")).toList();
        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.toLowerCase();
            all = all.stream().filter(u -> (str(u.getNo()) + " " + str(u.getName()) + " "
                    + str(u.getCollege()) + " " + str(u.getMajor()) + " "
                    + str(u.getClassNo()) + " " + str(u.getPhone())).toLowerCase().contains(k)).toList();
        }
        all = all.stream().sorted(java.util.Comparator
                .comparing((User u) -> str(u.getClassNo()).isEmpty() ? 1 : 0)
                .thenComparing((User u) -> str(u.getClassNo()))
                .thenComparing((User u) -> str(u.getNo())))
                .collect(Collectors.toList());

        java.util.List<java.util.List<String>> rows = new java.util.ArrayList<>();
        rows.add(java.util.List.of("学号/工号", "姓名", "角色", "性别", "年级", "学院", "专业", "班级", "手机号"));
        for (User u : all) {
            String roleCn = "student".equals(u.getRole()) ? "学生" : "teacher".equals(u.getRole()) ? "教师" : "管理员";
            rows.add(java.util.List.of(str(u.getNo()), str(u.getName()), roleCn, str(u.getGender()),
                    u.getGrade() == null ? "" : String.valueOf(u.getGrade()),
                    str(u.getCollege()), str(u.getMajor()), str(u.getClassNo()), str(u.getPhone())));
        }
        byte[] bytes = excelService.toXlsx("用户列表", rows);
        org.springframework.http.HttpHeaders h = new org.springframework.http.HttpHeaders();
        h.set(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"users-export.xlsx\"");
        h.set(org.springframework.http.HttpHeaders.CONTENT_TYPE,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        return new org.springframework.http.ResponseEntity<>(bytes, h, org.springframework.http.HttpStatus.OK);
    }

    private String str(Object v) { return v == null ? "" : v.toString(); }

    // 管理员新增任意角色用户（教师/管理员/学生）
    @PostMapping("/users")
    public ApiResponse<User> createUser(@RequestBody Map<String, String> body) {
        String no = body.get("no");
        if (userRepo.findByNo(no).isPresent()) return ApiResponse.fail("该账号已存在");
        User u = new User();
        u.setNo(no);
        u.setPassword(passwordEncoder.encode(com.zhixue.util.RsaCipher.decryptIfEncrypted(body.getOrDefault("password", "123456"))));
        u.setName(body.get("name"));
        u.setRole(body.getOrDefault("role", "teacher"));
        u.setCollege(body.get("college"));
        u.setMajor(body.get("major"));
        u.setGender(body.get("gender"));
        String g = body.get("grade");
        if (g != null && !g.isEmpty()) u.setGrade(Integer.parseInt(g));
        u.setClassNo(body.get("classNo"));
        u.setPhone(body.get("phone"));
        u.setAvatar(body.getOrDefault("avatar", "👤"));
        return ApiResponse.ok("创建成功", userRepo.save(u));
    }

    @PutMapping("/users/{id}")
    public ApiResponse<User> updateUser(@PathVariable Long id, @RequestBody Map<String, String> body) {
        User u = userRepo.findById(id).orElse(null);
        if (u == null) return ApiResponse.fail("用户不存在");
        if (body.containsKey("name")) u.setName(body.get("name"));
        if (body.containsKey("college")) u.setCollege(body.get("college"));
        if (body.containsKey("major")) u.setMajor(body.get("major"));
        if (body.containsKey("gender")) u.setGender(body.get("gender"));
        if (body.containsKey("classNo")) u.setClassNo(body.get("classNo"));
        if (body.containsKey("phone")) u.setPhone(body.get("phone"));
        if (body.containsKey("avatar")) u.setAvatar(body.get("avatar"));
        if (body.containsKey("grade")) {
            String g = body.get("grade");
            if (g == null || g.isEmpty()) {
                u.setGrade(null);            // 显式传空：允许清掉配错的年级
            } else {
                try { u.setGrade(Integer.parseInt(g)); } catch (NumberFormatException ignored) {}
            }
        }
        if (body.containsKey("password") && body.get("password") != null && !body.get("password").isEmpty()) {
            u.setPassword(passwordEncoder.encode(com.zhixue.util.RsaCipher.decryptIfEncrypted(body.get("password"))));
        }
        return ApiResponse.ok("更新成功", userRepo.save(u));
    }

    @DeleteMapping("/users/{id}")
    public ApiResponse<Void> deleteUser(@PathVariable Long id) {
        userRepo.deleteById(id);
        return ApiResponse.ok("已删除", null);
    }

    /** 管理员一键重置密码 */
    @PutMapping("/users/{id}/reset-password")
    public ApiResponse<Map<String, Object>> resetPassword(@PathVariable Long id,
                                                          @RequestBody(required = false) Map<String, String> body) {
        User u = userRepo.findById(id).orElse(null);
        if (u == null) return ApiResponse.fail("用户不存在");
        String newPwd = (body != null && body.get("newPassword") != null && !body.get("newPassword").isEmpty())
                ? com.zhixue.util.RsaCipher.decryptIfEncrypted(body.get("newPassword"))
                : "123456";
        u.setPassword(passwordEncoder.encode(newPwd));
        userRepo.save(u);
        Map<String, Object> r = new java.util.HashMap<>();
        r.put("tempPassword", newPwd);
        return ApiResponse.ok("密码已重置为 " + newPwd, r);
    }
}