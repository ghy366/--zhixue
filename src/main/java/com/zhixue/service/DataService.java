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
            sb.append(esc(u.getNo())).append(',').append(esc(u.getName())).append(',').append(esc(u.getPassword())).append(',')
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

    public String studentTemplateCsv() {
        return "学号,姓名,密码,学院,专业,年级,班级,手机号\n" +
               "2023090101,张三,123456,计算机学院,软件工程,2023,软工2301班,13800000000\n";
    }

    public String courseTemplateCsv() {
        return "课程代码,课程名称,教师工号,学分,学时,容量,类别,上课时间,上课地点\n" +
               "CS501,分布式系统,T002,3,48,60,专业选修,周三 08:00-09:40,信息楼-701\n";
    }

    // ================= 导入 =================
    @Transactional
    public Map<String, Object> importStudentsCsv(String text) {
        int created = 0, updated = 0, skipped = 0, lineNo = 0;
        List<String> errors = new ArrayList<>();
        for (String raw : splitLines(text)) {
            lineNo++;
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (lineNo == 1 && isHeader(line)) continue;
            List<String> f = parseLine(line);
            String no = s(f, 0), name = s(f, 1);
            if (no.isEmpty() || name.isEmpty()) { skipped++; errors.add("第 " + lineNo + " 行：学号或姓名为空，已跳过"); continue; }
            String pwd = s(f, 2).isEmpty() ? "123456" : s(f, 2);
            User u = userRepo.findByNo(no).orElse(null);
            if (u != null && !"student".equals(u.getRole())) {
                skipped++; errors.add("第 " + lineNo + " 行：" + no + " 已存在且不是学生，已跳过"); continue;
            }
            if (u == null) {
                u = new User();
                u.setNo(no);
                u.setRole("student");
                u.setPassword(pwd);
                u.setAvatar("🎓");
                created++;
            } else {
                updated++;
            }
            u.setName(name);
            if (!s(f, 3).isEmpty()) u.setCollege(s(f, 3));
            if (!s(f, 4).isEmpty()) u.setMajor(s(f, 4));
            if (!s(f, 5).isEmpty()) {
                try { u.setGrade(Integer.parseInt(s(f, 5))); } catch (NumberFormatException ignored) {}
            }
            if (!s(f, 6).isEmpty()) u.setClassNo(s(f, 6));
            if (!s(f, 7).isEmpty()) u.setPhone(s(f, 7));
            userRepo.save(u);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("created", created);
        r.put("updated", updated);
        r.put("skipped", skipped);
        r.put("errors", errors);
        r.put("msg", "导入完成：新增 " + created + " 人，更新 " + updated + " 人，跳过 " + skipped + " 行");
        return r;
    }

    @Transactional
    public Map<String, Object> importCoursesCsv(String text) {
        int created = 0, updated = 0, skipped = 0, lineNo = 0;
        List<String> errors = new ArrayList<>();
        for (String raw : splitLines(text)) {
            lineNo++;
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (lineNo == 1 && isHeader(line)) continue;
            List<String> f = parseLine(line);
            String code = s(f, 0), name = s(f, 1);
            if (code.isEmpty() || name.isEmpty()) { skipped++; errors.add("第 " + lineNo + " 行：课程代码或名称为空，已跳过"); continue; }

            Course c = courseRepo.findAll().stream()
                    .filter(x -> code.equals(x.getCourseCode())).findFirst().orElse(null);
            if (c == null) { c = new Course(); c.setCourseCode(code); created++; }
            else updated++;

            c.setName(name);
            String tNo = s(f, 2);
            if (!tNo.isEmpty()) {
                User t = userRepo.findByNo(tNo).orElse(null);
                if (t == null) errors.add("第 " + lineNo + " 行：教师工号 " + tNo + " 不存在，该课程未指定教师");
                else c.setTeacherId(t.getId());
            }
            if (!s(f, 3).isEmpty()) { try { c.setCredit(Integer.parseInt(s(f, 3))); } catch (NumberFormatException ignored) {} }
            if (!s(f, 4).isEmpty()) { try { c.setHours(Integer.parseInt(s(f, 4))); } catch (NumberFormatException ignored) {} }
            if (!s(f, 5).isEmpty()) { try { c.setCapacity(Integer.parseInt(s(f, 5))); } catch (NumberFormatException ignored) {} }
            if (!s(f, 6).isEmpty()) c.setCategory(s(f, 6));
            if (!s(f, 7).isEmpty()) c.setTime(s(f, 7));
            if (!s(f, 8).isEmpty()) c.setLocation(s(f, 8));
            courseRepo.save(c);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("created", created);
        r.put("updated", updated);
        r.put("skipped", skipped);
        r.put("errors", errors);
        r.put("msg", "导入完成：新增 " + created + " 门，更新 " + updated + " 门，跳过 " + skipped + " 行");
        return r;
    }
}
