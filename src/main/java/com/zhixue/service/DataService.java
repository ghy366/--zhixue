package com.zhixue.service;

import com.zhixue.entity.Course;
import com.zhixue.entity.Enrollment;
import com.zhixue.entity.Grade;
import com.zhixue.entity.User;
import com.zhixue.repository.CourseRepository;
import com.zhixue.repository.EnrollmentRepository;
import com.zhixue.repository.GradeRepository;
import com.zhixue.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** CSV 批量导入 / 导出 */
@Service
public class DataService {

    @Autowired private UserRepository userRepo;
    @Autowired private CourseRepository courseRepo;
    @Autowired private GradeRepository gradeRepo;
    @Autowired private EnrollmentRepository enrollmentRepo;
    @Autowired private com.zhixue.util.PasswordUtil passwordUtil;

    // ================= CSV 工具 =================
    private static String esc(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static List<String> parseLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQuote) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; }
                    else inQuote = false;
                } else cur.append(ch);
            } else {
                if (ch == '"') inQuote = true;
                else if (ch == ',') { out.add(cur.toString()); cur.setLength(0); }
                else cur.append(ch);
            }
        }
        out.add(cur.toString());
        return out;
    }

    private static List<String> splitLines(String text) {
        String t = text == null ? "" : text.replace("\uFEFF", "");   // 去掉 Excel/WPS 导出可能带上的 BOM
        List<String> lines = new ArrayList<>();
        for (String s : t.replace("\r\n", "\n").replace("\r", "\n").split("\n")) lines.add(s);
        return lines;
    }

    private static boolean isHeader(String line) {
        String l = line.trim();
        return l.startsWith("学号") || l.startsWith("姓名") || l.startsWith("课程代码") || l.startsWith("课程名称")
                || l.toLowerCase().startsWith("no,") || l.toLowerCase().startsWith("code,");
    }

    private static String s(List<String> f, int i) {
        return i < f.size() ? f.get(i).trim() : "";
    }

    // ================= 导出 =================
    public String exportStudentsCsv() {
        StringBuilder sb = new StringBuilder("学号,姓名,密码,学院,专业,年级,班级,手机号\n");
        List<User> users = new ArrayList<>(userRepo.findAll());
        users.sort(Comparator.comparing(u -> u.getNo() == null ? "" : u.getNo()));
        for (User u : users) {
            if (!"student".equals(u.getRole())) continue;
            sb.append(esc(u.getNo())).append(',').append(esc(u.getName())).append(',').append("") // 密码已哈希，导出置空保护
              .append(',')
              .append(esc(u.getCollege())).append(',').append(esc(u.getMajor())).append(',')
              .append(u.getGrade() == null ? "" : u.getGrade()).append(',')
              .append(esc(u.getClassNo())).append(',').append(esc(u.getPhone())).append('\n');
        }
        return sb.toString();
    }

    public String exportCoursesCsv() {
        Map<Long, User> users = new HashMap<>();
        for (User u : userRepo.findAll()) users.put(u.getId(), u);
        StringBuilder sb = new StringBuilder("课程代码,课程名称,教师工号,教师姓名,学分,学时,容量,类别,上课时间,上课地点,校区,考试时间\n");
        for (Course c : courseRepo.findAll()) {
            User t = c.getTeacherId() == null ? null : users.get(c.getTeacherId());
            sb.append(esc(c.getCourseCode())).append(',').append(esc(c.getName())).append(',')
              .append(esc(t != null ? t.getNo() : "")).append(',').append(esc(t != null ? t.getName() : "")).append(',')
              .append(c.getCredit() == null ? "" : c.getCredit()).append(',')
              .append(c.getHours() == null ? "" : c.getHours()).append(',')
              .append(c.getCapacity() == null ? "" : c.getCapacity()).append(',')
              .append(esc(c.getCategory())).append(',').append(esc(c.getTime())).append(',')
              .append(esc(c.getLocation())).append(',').append(esc(c.getCampus())).append(',')
              .append(esc(c.getExamTime())).append('\n');
        }
        return sb.toString();
    }

    public String exportGradesCsv() {
        Map<Long, User> users = new HashMap<>();
        for (User u : userRepo.findAll()) users.put(u.getId(), u);
        Map<Long, Course> courses = new HashMap<>();
        for (Course c : courseRepo.findAll()) courses.put(c.getId(), c);
        StringBuilder sb = new StringBuilder("学号,姓名,课程代码,课程名称,平时分,考试分,总分\n");
        for (Grade g : gradeRepo.findAll()) {
            User u = users.get(g.getStudentId());
            Course c = courses.get(g.getCourseId());
            sb.append(esc(u != null ? u.getNo() : "")).append(',').append(esc(u != null ? u.getName() : "")).append(',')
              .append(esc(c != null ? c.getCourseCode() : "")).append(',').append(esc(c != null ? c.getName() : "")).append(',')
              .append(g.getRegularScore() == null ? "" : g.getRegularScore()).append(',')
              .append(g.getFinalScore() == null ? "" : g.getFinalScore()).append(',')
              .append(g.getTotalScore() == null ? "" : g.getTotalScore()).append('\n');
        }
        return sb.toString();
    }

    public String exportCourseEnrollmentsCsv(Long courseId) {
        Course course = courseRepo.findById(courseId).orElse(null);
        String title = course != null ? course.getName() : ("课程" + courseId);
        Map<Long, User> users = new HashMap<>();
        for (User u : userRepo.findAll()) users.put(u.getId(), u);
        StringBuilder sb = new StringBuilder("课程：" + title + "\n");
        sb.append("学号,姓名,专业,班级,手机号,轮次,投入积分,状态\n");
        List<Enrollment> list = enrollmentRepo.findAll().stream()
                .filter(e -> courseId.equals(e.getCourseId()))
                .sorted(Comparator.comparing(Enrollment::getPoints).reversed())
                .toList();
        for (Enrollment e : list) {
            User u = users.get(e.getStudentId());
            sb.append(esc(u != null ? u.getNo() : "")).append(',').append(esc(u != null ? u.getName() : "")).append(',')
              .append(esc(u != null ? u.getMajor() : "")).append(',').append(esc(u != null ? u.getClassNo() : "")).append(',')
              .append(esc(u != null ? u.getPhone() : "")).append(',')
              .append(e.getRound() == null ? "" : e.getRound()).append(',')
              .append(e.getPoints() == null ? "" : e.getPoints()).append(',')
              .append(esc(e.getStatus())).append('\n');
        }
        return sb.toString();
    }

    // ================= Excel(.xlsx) 行构造（供 ExcelService 生成真实 xlsx） =================
    private static String str(Object o) { return o == null ? "" : String.valueOf(o); }

    public List<List<String>> studentsRows() {
        List<List<String>> rows = new ArrayList<>();
        rows.add(Arrays.asList("学号", "姓名", "密码", "学院", "专业", "年级", "班级", "手机号"));
        List<User> users = new ArrayList<>(userRepo.findAll());
        users.sort(Comparator.comparing(u -> u.getNo() == null ? "" : u.getNo()));
        for (User u : users) {
            if (!"student".equals(u.getRole())) continue;
            rows.add(Arrays.asList(esc(u.getNo()), esc(u.getName()), "", esc(u.getCollege()), // 密码已哈希，导出置空保护
                    esc(u.getMajor()), u.getGrade() == null ? "" : str(u.getGrade()), esc(u.getClassNo()), esc(u.getPhone())));
        }
        return rows;
    }

    public List<List<String>> coursesRows() {
        Map<Long, User> users = new HashMap<>();
        for (User u : userRepo.findAll()) users.put(u.getId(), u);
        List<List<String>> rows = new ArrayList<>();
        rows.add(Arrays.asList("课程代码", "课程名称", "教师工号", "教师姓名", "学分", "学时", "容量", "类别", "上课时间", "上课地点", "校区", "考试时间"));
        for (Course c : courseRepo.findAll()) {
            User t = c.getTeacherId() == null ? null : users.get(c.getTeacherId());
            rows.add(Arrays.asList(esc(c.getCourseCode()), esc(c.getName()), esc(t != null ? t.getNo() : ""),
                    esc(t != null ? t.getName() : ""), str(c.getCredit()), str(c.getHours()), str(c.getCapacity()),
                    esc(c.getCategory()), esc(c.getTime()), esc(c.getLocation()), esc(c.getCampus()), esc(c.getExamTime())));
        }
        return rows;
    }

    public List<List<String>> gradesRows() {
        Map<Long, User> users = new HashMap<>();
        for (User u : userRepo.findAll()) users.put(u.getId(), u);
        Map<Long, Course> courses = new HashMap<>();
        for (Course c : courseRepo.findAll()) courses.put(c.getId(), c);
        List<List<String>> rows = new ArrayList<>();
        rows.add(Arrays.asList("学号", "姓名", "课程代码", "课程名称", "平时分", "考试分", "总分"));
        for (Grade g : gradeRepo.findAll()) {
            User u = users.get(g.getStudentId());
            Course c = courses.get(g.getCourseId());
            rows.add(Arrays.asList(esc(u != null ? u.getNo() : ""), esc(u != null ? u.getName() : ""),
                    esc(c != null ? c.getCourseCode() : ""), esc(c != null ? c.getName() : ""),
                    str(g.getRegularScore()), str(g.getFinalScore()), str(g.getTotalScore())));
        }
        return rows;
    }

    public List<List<String>> courseEnrollmentsRows(Long courseId) {
        Course course = courseRepo.findById(courseId).orElse(null);
        String title = course != null ? course.getName() : ("课程" + courseId);
        Map<Long, User> users = new HashMap<>();
        for (User u : userRepo.findAll()) users.put(u.getId(), u);
        List<List<String>> rows = new ArrayList<>();
        rows.add(Arrays.asList("课程：" + title));
        rows.add(Arrays.asList("学号", "姓名", "专业", "班级", "手机号", "轮次", "投入积分", "状态"));
        List<Enrollment> list = enrollmentRepo.findAll().stream()
                .filter(e -> courseId.equals(e.getCourseId()))
                .sorted(Comparator.comparing(Enrollment::getPoints).reversed())
                .toList();
        for (Enrollment e : list) {
            User u = users.get(e.getStudentId());
            rows.add(Arrays.asList(esc(u != null ? u.getNo() : ""), esc(u != null ? u.getName() : ""),
                    esc(u != null ? u.getMajor() : ""), esc(u != null ? u.getClassNo() : ""), esc(u != null ? u.getPhone() : ""),
                    str(e.getRound()), str(e.getPoints()), esc(e.getStatus())));
        }
        return rows;
    }

    /** Excel 导入模板：第一行表头（列名用于自动映射字段），第二行示例 */
    public List<List<String>> studentTemplateRows() {
        return List.of(
            Arrays.asList("学号", "姓名", "密码", "学院", "专业", "年级", "班级", "手机号"),
            Arrays.asList("2023090101", "张三", "123456", "计算机学院", "软件工程", "2023", "软工2301班", "13800000000")
        );
    }

    public List<List<String>> courseTemplateRows() {
        return List.of(
            Arrays.asList("课程代码", "课程名称", "教师工号", "学分", "学时", "容量", "类别", "上课时间", "上课地点"),
            Arrays.asList("CS501", "分布式系统", "T002", "3", "48", "60", "专业选修", "周三 08:00-09:40", "明志楼-403")
        );
    }

    // ================= Excel(.xlsx) 导入 =================
    private static final String[] STUDENT_HEADERS = {"学号", "姓名", "密码", "学院", "专业", "年级", "班级", "手机号"};
    private static final String[] COURSE_HEADERS = {"课程代码", "课程名称", "教师工号", "学分", "学时", "容量", "类别", "上课时间", "上课地点"};

    /** 首行是否表头；按列名建立映射，支持用户调整列顺序或加多余列 */
    private static int[] headerMap(List<String> head, String[] names) {
        int[] idx = new int[names.length];
        Arrays.fill(idx, -1);
        for (int i = 0; i < head.size(); i++) {
            String h = head.get(i) == null ? "" : head.get(i).trim();
            for (int j = 0; j < names.length; j++) if (h.equals(names[j])) idx[j] = i;
        }
        return idx;
    }

    private static boolean isHeaderRow(List<String> f, String[] names) {
        if (f.isEmpty() || f.get(0) == null) return false;
        String first = f.get(0).trim();
        for (String n : names) if (first.equals(n)) return true;
        return false;
    }

    /** 取第 j 个字段：有表头映射按列名取，否则按固定列序 */
    private static String cell(List<String> f, int[] map, int j) {
        return s(f, map != null && map[j] >= 0 ? map[j] : j).trim();
    }

    @Transactional
    public Map<String, Object> importStudentsRows(List<List<String>> rows) {
        int created = 0, updated = 0, skipped = 0, lineNo = 0;
        int[] map = null;
        List<String> errors = new ArrayList<>();
        for (List<String> f : rows) {
            lineNo++;
            if (f == null || f.isEmpty()) continue;
            if (lineNo == 1 && isHeaderRow(f, STUDENT_HEADERS)) { map = headerMap(f, STUDENT_HEADERS); continue; }
            String no = cell(f, map, 0), name = cell(f, map, 1);
            if (no.isEmpty() || name.isEmpty()) { skipped++; errors.add("第 " + lineNo + " 行：学号或姓名为空，已跳过"); continue; }
            String pwd = cell(f, map, 2).isEmpty() ? "123456" : cell(f, map, 2);
            User u = userRepo.findByNo(no).orElse(null);
            if (u != null && !"student".equals(u.getRole())) {
                skipped++; errors.add("第 " + lineNo + " 行：" + no + " 已存在且不是学生，已跳过"); continue;
            }
            if (u == null) {
                u = new User();
                u.setNo(no);
                u.setRole("student");
                u.setPassword(passwordUtil.encode(pwd));
                u.setAvatar("🎓");
                created++;
            } else updated++;
            u.setName(name);
            String college = cell(f, map, 3); if (!college.isEmpty()) u.setCollege(college);
            String major = cell(f, map, 4); if (!major.isEmpty()) u.setMajor(major);
            String grade = cell(f, map, 5);
            if (!grade.isEmpty()) { try { u.setGrade(Integer.parseInt(grade)); } catch (NumberFormatException ignored) {} }
            String classNo = cell(f, map, 6); if (!classNo.isEmpty()) u.setClassNo(classNo);
            String phone = cell(f, map, 7); if (!phone.isEmpty()) u.setPhone(phone);
            userRepo.save(u);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("created", created); r.put("updated", updated); r.put("skipped", skipped); r.put("errors", errors);
        r.put("msg", "导入完成：新增 " + created + " 人，更新 " + updated + " 人，跳过 " + skipped + " 行");
        return r;
    }

    @Transactional
    public Map<String, Object> importCoursesRows(List<List<String>> rows) {
        int created = 0, updated = 0, skipped = 0, lineNo = 0;
        int[] map = null;
        List<String> errors = new ArrayList<>();
        for (List<String> f : rows) {
            lineNo++;
            if (f == null || f.isEmpty()) continue;
            if (lineNo == 1 && isHeaderRow(f, COURSE_HEADERS)) { map = headerMap(f, COURSE_HEADERS); continue; }
            String code = cell(f, map, 0), name = cell(f, map, 1);
            if (code.isEmpty() || name.isEmpty()) { skipped++; errors.add("第 " + lineNo + " 行：课程代码或名称为空，已跳过"); continue; }

            Course c = courseRepo.findAll().stream()
                    .filter(x -> code.equals(x.getCourseCode())).findFirst().orElse(null);
            if (c == null) { c = new Course(); c.setCourseCode(code); created++; }
            else updated++;

            c.setName(name);
            String tNo = cell(f, map, 2);
            if (!tNo.isEmpty()) {
                User t = userRepo.findByNo(tNo).orElse(null);
                if (t == null) errors.add("第 " + lineNo + " 行：教师工号 " + tNo + " 不存在，该课程未指定教师");
                else c.setTeacherId(t.getId());
            }
            for (int[] pair : new int[][]{{3, 0}, {4, 1}, {5, 2}}) {
                String v = cell(f, map, pair[0]);
                if (v.isEmpty()) continue;
                try {
                    int n = Integer.parseInt(v);
                    if (pair[1] == 0) c.setCredit(n);
                    else if (pair[1] == 1) c.setHours(n);
                    else c.setCapacity(n);
                } catch (NumberFormatException ignored) {}
            }
            String cat = cell(f, map, 6); if (!cat.isEmpty()) c.setCategory(cat);
            String time = cell(f, map, 7); if (!time.isEmpty()) c.setTime(time);
            String loc = cell(f, map, 8); if (!loc.isEmpty()) c.setLocation(loc);
            courseRepo.save(c);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("created", created); r.put("updated", updated); r.put("skipped", skipped); r.put("errors", errors);
        r.put("msg", "导入完成：新增 " + created + " 门，更新 " + updated + " 门，跳过 " + skipped + " 行");
        return r;
    }
}
