package com.zhixue.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AI 助手的「页面入口目录」：把当前角色能看到的页面（路径 + 取数接口 + 能查什么）登记下来。
 * AI 不再只依赖人工整理的权威数据块——它会按问题选页面，由 {@link AiPageBrowserService}
 * 带着当前用户的 token 真正请求该页面的接口，把页面实况数据放进提示词后再思考回答。
 *
 * 权限双保险：目录先按角色过滤，实际请求仍走 RoleInterceptor 鉴权，越权页面取不到数据。
 */
@Service
public class AiPageCatalogService {

    public record Page(String id, String name, String path, String api, String desc,
                       String keywords, List<String> roles) {
        public boolean allows(String role) {
            return roles.contains(role == null ? "student" : role);
        }
    }

    private static final List<Page> PAGES = new ArrayList<>();
    static {
        // ===== 通用 / 学生 =====
        add("dashboard", "学习总览", "dashboard", "/api/system/config",
                "当前轮次、选课开关、学分上限等系统状态", "首页|总览|仪表盘|系统状态|配置", "student,teacher,admin");
        add("courses", "课程目录", "courses", "/api/courses",
                "全部课程、容量、上课时间、教师、本轮报名人数", "课程|选课|推荐|目录|有什么课|课程列表|容量", "student,teacher,admin");
        add("intents", "选课意向", "intents", "/api/intents/mine",
                "我的意向单、每门课投入的积分", "意向|投分|积分|加码|报分|意向单", "student");
        add("my-enrollments", "我的课程", "my-enrollments", "/api/enrollments/mine",
                "我的选课记录、录取/落选/退课状态、投入积分", "我的课|已选|选课记录|录取|退课|我选了", "student");
        add("waitlist", "候补队列", "waitlist", "/api/waitlist/mine",
                "我的候补位次、确认时限与状态", "候补|排队|递补|等名额", "student");
        add("schedule", "课表日程", "schedule", "/api/enrollments/mine",
                "本学期课表、上课时段与地点", "课表|日程|上课时间|周几|冲突", "student");
        add("grades", "成绩档案", "grades", "/api/grades/mine",
                "我的成绩、总评、GPA、挂科记录", "成绩|分数|绩点|GPA|挂科|总评", "student");
        add("reviews", "课程评价", "reviews", "/api/reviews/mine",
                "我提交的课程评分与评价", "评价|评分|口碑|评课|我的评价", "student");
        add("reviews-teacher", "课程评价", "reviews", "/api/reviews/teacher/{uid}",
                "我任教课程收到的学生评价", "评价|评分|口碑|评课|学生评价", "teacher");
        add("graduation", "毕业进度", "graduation", "/api/graduation/summary",
                "四大学分缺口、完成度、推荐补修课程", "毕业|学分|缺口|毕业进度|培养方案|达标", "student");
        add("suggestions", "意见反馈", "suggestions", "/api/suggestions/mine",
                "我提交的反馈与管理员回复", "反馈|建议|意见|投诉|建议箱|我的反馈", "student");
        add("suggestions-admin", "意见反馈", "suggestions", "/api/suggestions",
                "全部学生反馈与处理状态", "反馈|建议|意见|投诉|建议箱|处理反馈|反馈处理", "admin");
        add("advisor-apply", "导师匹配", "advisor-apply", "/api/advisor/topics",
                "毕业设计课题库与双选状态", "导师|课题|双选|毕业设计|毕设", "student,teacher");
        add("workspace", "课题工作台", "workspace", "/api/workspace/my",
                "我的毕设里程碑、论文与进度", "工作台|里程碑|论文|毕设|进度", "student");
        add("workspace-teacher", "课题工作台", "workspace", "/api/workspace/teacher/students",
                "我指导学生的毕设进度与里程碑", "工作台|里程碑|论文|毕设|进度|指导学生", "teacher");
        add("announcements", "公告列表", "announcements", "/api/announcements",
                "我可见的公告与通知", "公告|通知|消息|未读", "student,teacher,admin");
        add("profile", "个人档案", "profile", "/api/auth/me",
                "我的账号、姓名、学号、年级、专业", "个人|档案|资料|我的信息|账号", "student,teacher,admin");
        // ===== 教师 =====
        add("teach-courses", "授课课程", "teach-courses", "/api/courses/teacher/{uid}",
                "我讲授的课程与报名情况", "授课|我教的课|我的课程|任课", "teacher");
        add("student-list", "学生名册", "student-list", "/api/workspace/teacher/students",
                "我指导/授课的学生名单", "学生名册|名单|选课学生|我的学生", "teacher");
        add("drop-requests", "退课申请", "drop-requests", "/api/drop-requests",
                "待我审批的退课申请与处理状态", "退课申请|审批|退课审批|驳回", "teacher");
        add("grade-mgr", "成绩录入", "grade-mgr", "/api/courses/teacher/{uid}",
                "我任教课程（进入后选择课程录成绩）", "成绩录入|录分|录成绩|成绩管理", "teacher");
        add("advise", "课题指导", "advise", "/api/advisor/teacher/list",
                "我指导的学生与课题申请", "课题指导|指导学生|课题申请|指导", "teacher");
        // ===== 管理员 =====
        add("rounds", "选课轮次", "rounds", "/api/system/round-schedule",
                "三轮时间窗口、自动排班与当前开关", "轮次|开放|关闭|排期|时间窗|选课开关", "admin");
        add("semesters", "学期设置", "semesters", "/api/semesters",
                "全部学期、当前学期与状态", "学期|学期设置|学期推进|当前学期", "admin");
        add("classrooms", "教室管理", "classrooms", "/api/classrooms",
                "教室、容量与校区", "教室|场地|教室容量|楼", "admin");
        add("course-mgr", "课程总库", "course-mgr", "/api/courses",
                "全部课程配置（容量、学分、教师、时间）", "课程库|课程管理|课程总库|开课", "admin");
        add("user-mgr", "用户管理", "user-mgr", "/api/admin/users",
                "全部用户账号、角色与年级", "用户|账号|角色|学生名单|教师名单|用户管理", "admin");
        add("announce", "公告管理", "announce", "/api/announcements?all=1",
                "全部公告、已读统计", "公告管理|发公告|通知管理|已读统计", "admin");
        add("makeup-mgr", "补考管理", "makeup-mgr", "/api/makeup",
                "补考册生成、排考、弃考与名单导出", "补考|补考册|排考|补考名单|补考管理", "admin");
        add("retake-mgr", "重修管理", "retake-mgr", "/api/retake/summary",
                "重修报名汇总、定形式与跟班指定", "重修|重修报名|组班|跟班|重修管理", "admin");
        add("stats", "统计报表", "stats", "/api/stats/report",
                "全校选课、容量、成绩等统计", "统计|报表|数据|概览|分析|全校", "admin");
        add("graduation-audit", "毕业审核", "graduation-audit", "/api/graduation/applications",
                "学生毕业申请与审核状态", "毕业审核|毕业申请|审核", "admin");
        add("advise-mgr", "导师管理", "advise-mgr", "/api/advisor/admin/overview",
                "导师双选总览与容量配置", "导师管理|双选总览|导师容量", "admin");
        add("logs", "操作日志", "logs", "/api/logs?page=1&size=20",
                "最新操作日志与日志统计", "日志|操作记录|审计|活跃", "admin");
        add("training-plans", "培养方案", "training-plans", "/api/training-plan/items",
                "按专业+年级+学期排定必修课程，一键批量排定学生课表；含毕业学分要求配置", "培养方案|必修课|批量排定|排课|毕业要求|学分要求|年级方案", "admin");
        add("ai-channels", "AI 通道", "ai-channels", "/api/ai/channels",
                "自定义 AI 通道、Base URL 与模型清单（密钥仅掩码）", "AI通道|通道配置|模型通道|自定义通道|API通道", "admin");
        // AI 助手自身：只作跳转入口，不取数
        add("chat", "AI 学业助手", "chat", "", "AI 对话页面", "AI助手|聊天|对话", "student,teacher,admin");
    }

    private static void add(String id, String name, String path, String api, String desc,
                            String keywords, String roles) {
        PAGES.add(new Page(id, name, path, api, desc, keywords,
                Arrays.stream(roles.split(",")).map(String::trim).toList()));
    }

    /** 当前角色可见的全部页面 */
    public List<Page> forRole(String role) {
        return PAGES.stream().filter(p -> p.allows(role)).toList();
    }

    /** 注入提示词的页面目录：让 AI 知道自己有哪些入口、能去哪查 */
    public String catalogText(String role) {
        StringBuilder sb = new StringBuilder("\n-- 【我可进入的页面（当前角色：")
                .append(roleCn(role)).append("）】--\n");
        for (Page p : forRole(role)) {
            sb.append("· [").append(p.id()).append("] ").append(p.name())
              .append("  /").append(p.path()).append(" —— ").append(p.desc()).append("\n");
        }
        sb.append("说明：本轮【页面实况】是已实际打开并取回数据的页面；回答时可以建议用户到对应页面查看详情，")
          .append("路径写成 /xxx，不要编造未列出的页面。\n");
        return sb.toString();
    }

    /**
     * 按问题选本轮要打开的页面：显式点名 > 关键词 > 意图映射，最多 3 个。
     * 用确定性路由而非再调一次大模型，省一次调用与延迟，结果可预测。
     */
    public List<Page> select(String question, String role, String intent) {
        String q = (question == null ? "" : question).toLowerCase(Locale.ROOT);
        List<Page> visible = forRole(role).stream().filter(p -> !"chat".equals(p.id())).toList();
        // 显式「全览」指令：一次打开本角色全部可取数页面，用于「把整个系统看一遍」类问题
        if (q.contains("所有页面") || q.contains("全部页面") || q.contains("每个页面")
                || q.contains("各个页面") || q.contains("整个系统") || q.contains("都看一遍")
                || q.contains("所有入口") || q.contains("遍历")) {
            return visible.stream().filter(p -> !p.api().isBlank()).toList();
        }
        Map<String, Integer> score = new LinkedHashMap<>();
        for (Page p : visible) {
            int s = 0;
            if (q.contains(p.name().toLowerCase(Locale.ROOT)) || q.contains(p.id().toLowerCase(Locale.ROOT))
                    || q.contains("/" + p.path())) s += 10;                 // 显式点名
            for (String kw : p.keywords().split("\\|")) {
                if (kw.length() >= 2 && q.contains(kw.toLowerCase(Locale.ROOT))) s += 2;
            }
            String mapped = intentPage(intent, role);
            if (mapped != null && mapped.equals(p.id())) s += 3;
            if (s > 0) score.put(p.id(), s);
        }
        List<String> top = score.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3).map(Map.Entry::getKey).toList();
        if (top.isEmpty()) {
            String dft = intentPage(intent, role);
            if (dft == null) dft = "dashboard";
            top = List.of(dft);
        }
        final List<String> ids = top;
        List<Page> picked = visible.stream().filter(p -> ids.contains(p.id()))
                .sorted(Comparator.comparingInt(p -> ids.indexOf(p.id()))).toList();
        // 意图默认页不属于当前角色（如学生问管理员专属内容）时，退回本角色总览页，
        // 让 AI 至少能说明「该角色没有这个页面」，而不是完全没有页面数据
        if (picked.isEmpty()) {
            picked = visible.stream().filter(p -> "dashboard".equals(p.id())).toList();
        }
        return picked;
    }

    /** 意图 → 默认页面（找不到时用，保证每轮至少打开一个页面） */
    private String intentPage(String intent, String role) {
        if (intent == null) return "dashboard";
        boolean admin = "admin".equals(role);
        return switch (intent) {
            case "GRADES" -> "admin".equals(role) ? "stats" : ("teacher".equals(role) ? "grade-mgr" : "grades");
            case "GRADUATION" -> "admin".equals(role) ? "graduation-audit"
                    : ("teacher".equals(role) ? "advise" : "graduation");
            case "MY_ENROLL" -> "teacher".equals(role) ? "teach-courses" : "my-enrollments";
            case "MY_INTENT" -> "intents";
            case "WAITLIST" -> "waitlist";
            case "CONFLICT" -> "schedule";
            case "RECOMMEND", "COURSE_INFO" -> "courses";
            case "ROUND_RULE" -> admin ? "rounds" : "dashboard";
            case "ADVISOR" -> "advisor-apply";
            case "TEACHER" -> "teach-courses";
            case "ADMIN" -> "stats";
            default -> admin ? "stats" : "dashboard";
        };
    }

    private String roleCn(String role) {
        return switch (role == null ? "" : role) {
            case "teacher" -> "教师";
            case "admin" -> "教务管理员";
            default -> "学生";
        };
    }
}
