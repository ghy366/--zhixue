package com.zhixue.service;

import com.zhixue.entity.Course;
import com.zhixue.entity.Enrollment;
import com.zhixue.entity.GraduateAdvisor;
import com.zhixue.entity.SystemConfig;
import com.zhixue.entity.User;
import com.zhixue.repository.CourseRepository;
import com.zhixue.repository.EnrollmentRepository;
import com.zhixue.repository.GraduateAdvisorRepository;
import com.zhixue.repository.SystemConfigRepository;
import com.zhixue.repository.UserRepository;
import com.zhixue.repository.WaitlistRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 助手的「识别层」：把用户的自然语言问题解析成结构化意图 + 实体，
 * 再按需装配权威数据块（而不是把整个课程表硬塞进提示词）。
 *
 * 解决的问题：
 *  1. 旧实现只把课程表前 40 门写进提示词 → 第 41 门起（含《大学物理》《线性代数》
 *     《程序设计基础》等公共必修）AI 完全查不到，只能瞎编。
 *  2. 课程简介、容量余量、同学期同类课程等字段没有进入上下文 → 「这门课讲什么」
 *     「还剩几个名额」类问题识别失败。
 *  3. 三轮积分制、候补递补、毕业口径等业务规则没进提示词 → 规则类问题答非所问。
 *  4. 用户口语与系统术语不一致（投分/前置/轮次/候补）→ 意图识别不到。
 *  5. 时间冲突没有真实计算，AI 只能猜。
 *
 * 产出：Recognition（意图 + 命中课程 + 引用数据块）与 system 提示词。
 */
@Service
public class AiContextService {

    /** 识别出的意图 */
    public enum Intent {
        GRADUATION,   // 毕业 / 还差多少学分
        GRADES,       // 成绩 / 绩点
        MY_ENROLL,    // 我的选课 / 我的课表
        MY_INTENT,    // 意向单 / 投分策略
        WAITLIST,     // 候补 / 递补
        COURSE_INFO,  // 某门课的信息
        CONFLICT,     // 时间冲突
        RECOMMEND,    // 推荐课程 / 该选什么
        ROUND_RULE,   // 选课规则 / 轮次 / 怎么投分
        TEACHER,      // 教师视角
        ADMIN,        // 管理员视角
        ADVISOR,      // 毕业设计导师（学生双选确认的导师，非课程授课教师）
        GENERAL       // 兜底
    }

    /** 识别结果 */
    public static class Recognition {
        public String question = "";
        public Intent intent = Intent.GENERAL;
        public List<Course> courses = new ArrayList<>();
        public List<String> sources = new ArrayList<>();   // 引用了哪些数据块（前端展示）
        public String note = "";                            // 识别说明（调试/展示）
        public Map<String, Object> facts = new HashMap<>(); // 结构化事实（如冲突结论、推荐清单）

        public String intentCn() {
            switch (intent) {
                case GRADUATION: return "毕业学分";
                case GRADES: return "成绩绩点";
                case MY_ENROLL: return "我的选课";
                case MY_INTENT: return "意向投分";
                case WAITLIST: return "候补递补";
                case COURSE_INFO: return "课程信息";
                case CONFLICT: return "时间冲突";
                case RECOMMEND: return "选课推荐";
                case ROUND_RULE: return "选课规则";
                case TEACHER: return "教师视角";
                case ADMIN: return "管理员视角";
                case ADVISOR: return "毕业设计导师";
                default: return "综合问答";
            }
        }
    }

    @Autowired private CourseRepository courseRepo;
    @Autowired private EnrollmentRepository enrollmentRepo;
    @Autowired private SystemConfigRepository configRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private GraduateAdvisorRepository advisorRepo;
    @Autowired private WaitlistRepository waitlistRepo;
    @Autowired private WaitlistService waitlistService;
    @Autowired private GradeService gradeService;
    @Autowired private CourseService courseService;
    @Autowired private IntentService intentService;
    @Autowired private GraduationService graduationService;

    // ================= 业务规则文本（唯一事实源，AI 只能引用这里的规则） =================

    /** 平台业务规则：三轮积分制 / 候补 / 毕业口径 */
    public String businessRules(SystemConfig cfg) {
        StringBuilder sb = new StringBuilder();
        sb.append("【选课规则（系统真实规则，必须严格遵守）】\n");
        sb.append("1. 三轮制：第 1、2 轮为「积分竞价轮」，第 3 轮为「先到先得」。\n");
        sb.append("2. 积分池：每轮每名学生最多可投入 50 积分；单门课程可投 0-50 任意整数，"
                + "0 分表示「不投入积分、只占报名名额」。\n");
        sb.append("3. 结算规则：第 1、2 轮截止后统一结算——按积分从高到低依次录取，"
                + "同分者随机抽签（先随机打乱再稳定按积分降序），因此同分无法保证先后。\n");
        sb.append("4. 第 3 轮不消耗积分，先到先得，额满即止。\n");
        sb.append("5. 容量与冲突：报名时校验课程容量、时间冲突、学期学分上限、毕业大类封顶；"
                + "第 1、2 轮报名阶段只登记不拦截，最终由结算统一裁决。\n");
        sb.append("6. 候补：课程满员后可加入候补队列，排在队列前面的人退课或候补超时会自动递补；"
                + "确认候补时需要系统提示你在确认时限内操作，超时顺延给下一位。\n");
        sb.append("7. 必修课（公共必修 / 专业必修）不参与选课，由系统按教学计划自动排入课表，不占积分、不用投分。\n");
        sb.append("8. 退课：退课会释放名额并触发候补递补；已投入积分在退课时按系统规则处理。\n");
        sb.append("9. 毕业口径：毕业学分 = 必修 + 选修 + 通识 + 实践 四大类分别达标，"
                + "某一类修满不能抵扣其他类的缺口；必修需逐门修满教学计划。\n");
        if (cfg != null) {
            sb.append("10. 当前每学期学分上限：").append(cfg.getMaxCreditPerSemester()).append(" 分。\n");
            sb.append("11. 当前毕业学分要求：必修≥").append(cfg.getGradReqRequired())
              .append("、选修≥").append(cfg.getGradReqElective())
              .append("、通识≥").append(cfg.getGradReqGeneral())
              .append("、实践≥").append(cfg.getGradReqPractice()).append("。\n");
            sb.append("12. 候补确认时限：被递补者须在 ").append(cfg.getConfirmMinutes())
              .append(" 分钟内确认，超时名额自动顺延给下一位（此为系统真实配置值）。\n");
        }
        return sb.toString();
    }

    /** 术语对照表：用户口语 → 系统术语，消除识别歧义 */
    public String glossary() {
        return "【术语对照（用户说法 → 系统口径）】\n"
                + "· 投分 / 加码 / 报分 / 填分 / 竞价 = 意向积分（在「我的意向」里给每门课设 0-50 分）\n"
                + "· 意向 / 意向单 = course_intents（想选的课程及投入积分）\n"
                + "· 轮 / 轮次 / 第几轮 = 三轮选课（1、2 轮积分竞价，3 轮先到先得）\n"
                + "· 补选 / 候补 / 排队 / 等名额 = waitlist 候补队列\n"
                + "· 前置 / 先修 / 基础课 = 课程的前置要求（本系统未建模先修关系，"
                + "应按课程简介与学分层次说明，并建议查看培养方案）\n"
                + "· 绩点 / GPA / 分数 / 考得怎么样 = 成绩（regularScore 平时分 / finalScore 期末分 / totalScore 总评）\n"
                + "· 落选 / 没中 / 被刷 = 结算未录取\n"
                + "· 容量 / 名额 / 几个人 = 课程容量与已报人数\n"
                + "· 冲突 / 撞课 = 同一时段时间冲突\n"
                + "· 毕业 / 学位 / 能不能毕业 = 毕业进度（四大类学分分别达标）\n";
    }

    /** 校情资料：学校自身的基础事实（名称/地址/地标），AI 回答校情类问题的权威来源 */
    /** 系统简介：回答「系统是什么 / 架构 / 技术栈」类介绍问题的事实来源 */
    public String systemIntro() {
        return "【系统简介（回答系统自身介绍类问题的事实来源）】\n"
                + "· 系统定位：高校学分制选课平台，面向学生、教师、教务管理员三类角色\n"
                + "· 架构：B/S 前后端分离——前端 Vue 3 + TypeScript + Element Plus + ECharts；后端 Spring Boot 3 + Spring Data JPA；数据库 MySQL 8；AI 助手经大模型兼容接口接入\n"
                + "· 核心机制：三轮积分选课（第 1、2 轮积分竞价统一结算，第 3 轮先到先得）、候补队列自动递补、年级错峰开放、时间冲突检测与智能排课、毕业学分四类封顶\n"
                + "· 主要模块：课程浏览 / 意向投分 / 选课 / 候补 / 课表 / 成绩 / 毕业进度 / 毕设工作台 / AI 助手，以及教务端的轮次、课程、用户、统计、日志等管理模块\n"
                + "· AI 助手能力：已接入联网搜索，涉及时效性/外部信息的问题会自动检索公开网页并在回答中标注 [来源n]；课程、成绩、选课等业务事实一律以系统数据库为准\n";
    }

    public String schoolProfile() {
        return "【校情资料（学校真实信息，回答校情类问题必须以此为准）】\n"
                + "· 学校全称：厦门华厦学院\n"
                + "· 所在区：厦门市集美区\n"
                + "· 校址：福建省厦门市集美区天马路 288 号（集美文教区）\n"
                + "· 校园地标：教学楼群（七行双楼与玻璃连廊）、俊秀图书馆、钟楼、湖心亭、喷泉广场、操场、校门\n"
                + "· 二级学院：含信息与智能机电学院等\n"
                + "· 上课地点：选课系统内每门课程均标注「地点」字段，以课表/课程详情为准\n";
    }

    // ================= 意图识别 =================

    private static final Map<Intent, String[]> KEYWORDS = new LinkedHashMap<>();
    static {
        KEYWORDS.put(Intent.GRADUATION, new String[]{
                "毕业", "学位", "还差", "差多少", "缺多少", "学分要求", "毕业要求", "凑学分",
                "够不够学分", "能不能毕业", "修满", "毕业进度", "四类", "大类"});
        KEYWORDS.put(Intent.GRADES, new String[]{
                "成绩", "分数", "绩点", "gpa", "GPA", "总评", "平时分", "期末分", "考得", "考了", "多少分", "挂了", "及格",
                "第几次", "考了几次", "考试类型", "examtype", "attempt", "重修次数", "补考", "成绩明细"});
        KEYWORDS.put(Intent.MY_ENROLL, new String[]{
                "我的选课", "我选了", "已选", "我的课表", "课表", "我上的", "已修", "我的课程", "选了哪些", "学分用了", "选了几门"});
        KEYWORDS.put(Intent.MY_INTENT, new String[]{
                "意向", "投分", "积分", "加码", "投多少", "该不该投", "意向单", "多少分合适", "报分", "填分"});
        KEYWORDS.put(Intent.WAITLIST, new String[]{
                "候补", "补选", "递补", "排队", "排第几", "等名额", "候补位次", "顺延"});
        KEYWORDS.put(Intent.CONFLICT, new String[]{
                "冲突", "撞课", "时间重叠", "撞时间", "能不能一起", "同时上", "时间冲",
                "排除", "排掉", "避开", "冲突的课", "哪些课冲突"});
        KEYWORDS.put(Intent.ROUND_RULE, new String[]{
                "规则", "怎么选", "怎么投分", "怎么投", "怎么报名", "轮次", "几轮", "什么时候结束", "抽签", "先到先得",
                "怎么算", "流程", "学分上限", "怎么操作", "怎么用", "积分规则", "多少分合适", "怎么拿分",
                "同分", "怎么录取", "录取规则", "优先级", "怎么排序", "怎么定",
                "确认时限", "确认分钟", "多少分钟", "分钟内确认", "阶段", "phase", "semesteroffset",
                "学期偏移", "confirmminutes", "上限是多少", "系统配置值"});
        KEYWORDS.put(Intent.RECOMMEND, new String[]{
                "推荐", "选什么", "该选", "帮我选", "补什么", "适合", "值得选", "选哪门", "怎么搭配", "帮我凑", "安排",
                "缺什么", "还缺", "补上", "补哪", "凑够", "帮我补", "选哪些", "还差什么", "可以补", "怎么补"});
        KEYWORDS.put(Intent.TEACHER, new String[]{
                "我的课", "我教", "我带", "授课", "我的学生", "我的教学", "备课", "批改", "我的班级"});
        KEYWORDS.put(Intent.ADMIN, new String[]{
                "统计", "报表", "数据概览", "怎么设置", "系统配置", "管理端", "管理员", "轮次设置", "整体情况", "多少学生"});
        KEYWORDS.put(Intent.ADVISOR, new String[]{
                "毕业设计导师", "毕设导师", "我的导师", "指导教师", "指导老师", "导师是谁", "双选结果", "双选导师", "导师"});
    }

    /** 口语别名 → 课程名（覆盖学生实际用词：简称/缩写/俗称），用于课程实体抽取 */
    private static final Map<String, List<String>> ALIASES = new LinkedHashMap<>();
    static {
        ALIASES.put("高数", List.of("高等数学(上)", "高等数学(下)"));
        ALIASES.put("高数下", List.of("高等数学(下)"));
        ALIASES.put("高数上", List.of("高等数学(上)"));
        ALIASES.put("微积分", List.of("高等数学(上)", "高等数学(下)"));
        ALIASES.put("英语", List.of("大学英语(III)", "商务英语"));
        ALIASES.put("计网", List.of("计算机网络"));
        ALIASES.put("网工", List.of("计算机网络"));
        ALIASES.put("ds", List.of("数据结构"));
        ALIASES.put("ai", List.of("人工智能导论"));
        ALIASES.put("前端", List.of("Web前端开发", "前端工程化"));
        ALIASES.put("web", List.of("Web前端开发"));
        ALIASES.put("数据库", List.of("数据库系统", "数据库优化"));
        ALIASES.put("c语言", List.of("程序设计基础"));
        ALIASES.put("程序设计", List.of("程序设计基础"));
        ALIASES.put("线代", List.of("线性代数"));
        ALIASES.put("大学物理", List.of("大学物理"));
        ALIASES.put("物理", List.of("大学物理"));
        ALIASES.put("ml", List.of("机器学习"));
        ALIASES.put("深度学习", List.of("深度学习实践"));
        ALIASES.put("dl", List.of("深度学习实践"));
        ALIASES.put("心理", List.of("心理学导论"));
        ALIASES.put("书法", List.of("大学书法"));
        ALIASES.put("音乐", List.of("音乐鉴赏"));
        ALIASES.put("摄影", List.of("摄影基础"));
        ALIASES.put("影视", List.of("影视鉴赏"));
        ALIASES.put("茶", List.of("中国茶文化"));
        ALIASES.put("建筑", List.of("世界建筑赏析"));
        ALIASES.put("天文", List.of("天文学入门"));
        ALIASES.put("化学", List.of("生活中的化学"));
        ALIASES.put("日语", List.of("日语入门"));
        ALIASES.put("法语", List.of("法语入门"));
        ALIASES.put("中医", List.of("中医养生"));
        ALIASES.put("autocad", List.of("AutoCAD 制图基础"));
        ALIASES.put("制图", List.of("AutoCAD 制图基础"));
        ALIASES.put("机器学习", List.of("机器学习"));
        ALIASES.put("区块链", List.of("区块链技术"));
        ALIASES.put("量化", List.of("量化金融"));
        ALIASES.put("图像", List.of("数字图像处理"));
        ALIASES.put("嵌入式", List.of("嵌入式系统"));
        ALIASES.put("物联网", List.of("物联网导论"));
        ALIASES.put("算法竞赛", List.of("算法竞赛进阶"));
        ALIASES.put("编译", List.of("编译原理"));
        ALIASES.put("图形学", List.of("计算机图形学"));
        ALIASES.put("自然语言", List.of("自然语言处理"));
        ALIASES.put("测试技术", List.of("软件测试技术"));
        ALIASES.put("工程化", List.of("前端工程化"));
        ALIASES.put("移动应用", List.of("移动应用开发"));
        ALIASES.put("网络安全", List.of("网络安全基础"));
        ALIASES.put("云计算", List.of("云计算与大数据"));
        ALIASES.put("大数据", List.of("云计算与大数据"));
        ALIASES.put("演讲", List.of("演讲与口才"));
        ALIASES.put("口才", List.of("演讲与口才"));
        ALIASES.put("围棋", List.of("围棋入门"));
        ALIASES.put("可持续", List.of("环境与可持续发展"));
        ALIASES.put("环保", List.of("环境与可持续发展"));
        ALIASES.put("创新创业", List.of("创新创业基础", "创新创业实践"));
        ALIASES.put("逻辑", List.of("逻辑学导论"));
        ALIASES.put("艺术史", List.of("西方艺术史"));
        ALIASES.put("志愿", List.of("志愿服务实践"));
        ALIASES.put("电子工艺", List.of("电子工艺实践"));
        ALIASES.put("社会调查", List.of("社会调查与实践", "社会调查方法"));
        ALIASES.put("电影声音", List.of("电影声音赏析"));
        ALIASES.put("金工", List.of("金工实习"));
        ALIASES.put("数据分析", List.of("数据分析实训", "数据可视化"));
        ALIASES.put("数据可视化", List.of("数据可视化"));
        ALIASES.put("人工智能", List.of("人工智能导论"));
        ALIASES.put("运维", List.of("计算机网络"));
        ALIASES.put("商英", List.of("商务英语"));
        ALIASES.put("品酒", List.of("品酒人生"));
        ALIASES.put("写作", List.of("商务英语", "大学英语(III)"));
        ALIASES.put("数字媒体", List.of("数字图像处理"));
        ALIASES.put("运营", List.of("云计算与大数据"));
        ALIASES.put("软工", List.of("软件测试技术"));
        ALIASES.put("软考", List.of("软件测试技术"));
        ALIASES.put("会计", List.of("财务管理"));
        ALIASES.put("微分", List.of("高等数学(上)"));
        ALIASES.put("导数", List.of("高等数学(上)"));
        ALIASES.put("极限", List.of("高等数学(上)"));
        ALIASES.put("矩阵", List.of("线性代数"));
        ALIASES.put("行列式", List.of("线性代数"));
        ALIASES.put("力学", List.of("大学物理"));
        ALIASES.put("电磁", List.of("大学物理"));
    }

    /** 识别意图 + 课程实体 */
    public Recognition recognize(String question, Long userId, String role) {
        Recognition rec = new Recognition();
        String q = question == null ? "" : question.trim();
        String qLower = q.toLowerCase(Locale.ROOT);
        rec.question = q;

        // 1) 课程实体抽取
        rec.courses = matchCourses(q, qLower);

        // 2) 意图打分
        Map<Intent, Integer> score = new LinkedHashMap<>();
        for (Map.Entry<Intent, String[]> e : KEYWORDS.entrySet()) {
            int s = 0;
            for (String kw : e.getValue()) {
                if (q.contains(kw) || qLower.contains(kw.toLowerCase(Locale.ROOT))) s += kw.length() >= 3 ? 2 : 1;
            }
            score.put(e.getKey(), s);
        }
        // 命中课程实体 → 课程信息意图加权（课程名出现本身就是强信号）
        if (!rec.courses.isEmpty()) {
            score.merge(Intent.COURSE_INFO, 3 + rec.courses.size(), Integer::sum);
            // 「XX 和 YY 冲突吗」这类要优先判为冲突（冲突是更强的意图信号）
            if (score.getOrDefault(Intent.CONFLICT, 0) > 0) {
                score.put(Intent.CONFLICT, score.get(Intent.COURSE_INFO) + 2);
            }
        }
        // 「我」+ 毕业类词汇
        if (q.contains("我") && score.getOrDefault(Intent.GRADUATION, 0) > 0) {
            score.merge(Intent.GRADUATION, 1, Integer::sum);
        }
        /* 「我」+ 成绩类词汇：个人数据意图强信号（例：「XX 这门课我考了多少分」
           课程名命中会让课程信息意图 +4，这里给成绩意图 +5 压过它，防止误判） */
        if (q.contains("我") && (q.contains("考了") || q.contains("考了多少") || q.contains("成绩")
                || q.contains("分数") || q.contains("绩点"))) {
            score.merge(Intent.GRADES, 5, Integer::sum);
        }
        if (q.contains("我") && (q.contains("选了") || q.contains("课表") || q.contains("已修"))) {
            score.merge(Intent.MY_ENROLL, 3, Integer::sum);
        }
        // 推荐类强信号：即便问题里带「课表 / 我的选课」，只要是让我挑课，就必须走推荐块
        // （推荐块会排除时间冲突课；误判成「我的选课」时 AI 会凭列表瞎推，实测推到冲突课）
        if (q.contains("推荐") || q.contains("帮我选") || q.contains("选哪门") || q.contains("该选")
                || q.contains("选什么") || q.contains("选几门") || q.contains("能选什么")) {
            score.merge(Intent.RECOMMEND, 4, Integer::sum);
        }
        // 冲突是强意图信号：与个人数据类意图同分时优先判为冲突
        if (score.getOrDefault(Intent.CONFLICT, 0) > 0
                && score.getOrDefault(Intent.CONFLICT, 0) >= score.getOrDefault(Intent.MY_INTENT, 0)) {
            score.merge(Intent.CONFLICT, 1, Integer::sum);
        }

        // 3) 歧义消解：疑问句式偏规则，带「我的」偏个人数据
        boolean askHow = q.contains("怎么") || q.contains("如何") || q.contains("怎样") || q.contains("流程")
                || q.contains("规则") || q.contains("什么意思") || q.contains("为什么");
        boolean aboutMe = q.contains("我的") || q.contains("我还") || q.contains("我该");
        if (askHow && !aboutMe) {
            if (score.getOrDefault(Intent.ROUND_RULE, 0) > 0
                    || q.contains("投分") || q.contains("积分") || q.contains("报名") || q.contains("轮")) {
                score.put(Intent.ROUND_RULE, score.getOrDefault(Intent.ROUND_RULE, 0) + 3);
            }
            if (score.getOrDefault(Intent.MY_INTENT, 0) > 0) {
                score.put(Intent.MY_INTENT, score.get(Intent.MY_INTENT) - 1);
            }
        }
        if (aboutMe && score.getOrDefault(Intent.MY_INTENT, 0) > 0) {
            score.put(Intent.MY_INTENT, score.get(Intent.MY_INTENT) + 2);
        }

        Intent best = Intent.GENERAL;
        int bestScore = 0;
        for (Map.Entry<Intent, Integer> e : score.entrySet()) {
            if (e.getValue() > bestScore) { bestScore = e.getValue(); best = e.getKey(); }
        }
        // 3) 无命中兜底：如果问题里出现课程名但分数为 0，仍然是课程信息
        if (bestScore == 0 && !rec.courses.isEmpty()) best = Intent.COURSE_INFO;
        rec.intent = best;

        // 4) 角色纠正：教师/管理员的专属词覆盖
        if ("teacher".equals(role) && score.getOrDefault(Intent.TEACHER, 0) > 0) rec.intent = Intent.TEACHER;
        if ("admin".equals(role) && score.getOrDefault(Intent.ADMIN, 0) > 0) rec.intent = Intent.ADMIN;

        // 5) 「我的毕业设计导师」是强意图：问题里的「毕业设计」会被去括号后缀匹配
        //    误命中《毕业设计(上)/(下)》两门课，从而被判成 COURSE_INFO。
        //    此时 AI 只会看到课程授课教师，答不出学生真正双选确认的导师，必须强制纠正。
        boolean askAdvisor = q.contains("导师") || q.contains("指导教师") || q.contains("指导老师");
        if (askAdvisor && (q.contains("我") || q.contains("谁") || q.contains("双选"))) {
            rec.intent = Intent.ADVISOR;
        }

        rec.note = "意图=" + rec.intentCn() + "，命中课程=" + rec.courses.size() + " 门";
        return rec;
    }

    /** 课程名匹配：别名表 / 全名 / 去括号后缀 / 英文拆分 / 去后缀简称 / 简介关键词 */
    private List<Course> matchCourses(String q, String qLower) {
        if (q.isBlank()) return new ArrayList<>();
        List<Course> all = courseRepo.findAll();
        String qFlat = qLower.replace(" ", "").replace("　", "");
        Map<Long, Integer> score = new LinkedHashMap<>();

        // 1) 别名表命中（口语简称/缩写，权重最高）
        for (Map.Entry<String, List<String>> e : ALIASES.entrySet()) {
            String alias = e.getKey().toLowerCase(Locale.ROOT);
            if (alias.length() < 2 || !qFlat.contains(alias)) continue;
            for (String target : e.getValue()) {
                for (Course c : all) {
                    if (c.getName() != null && c.getName().equals(target)) {
                        score.merge(c.getId(), 120 + alias.length(), Integer::sum);
                    }
                }
            }
        }

        for (Course c : all) {
            String name = c.getName() == null ? "" : c.getName();
            if (name.isBlank()) continue;
            String nLower = name.toLowerCase(Locale.ROOT);
            String nFlat = nLower.replace(" ", "");
            int s = score.getOrDefault(c.getId(), 0);
            // 完全包含（忽略大小写与空格）
            if (qFlat.contains(nFlat)) s = Math.max(s, 100 + nFlat.length());
            // 去括号后缀匹配：高等数学(下) → 高等数学
            String base = name.replaceAll("[（(].*?[)）]", "").trim();
            if (!base.isEmpty() && qFlat.contains(base.toLowerCase(Locale.ROOT).replace(" ", ""))) {
                s = Math.max(s, 80 + base.length());
            }
            // 通用简称：去掉结尾修饰词后取核心词（心理学导论→心理学、程序设计基础→程序设计）
            String shortName = stripSuffix(name);
            if (shortName != null && shortName.length() >= 3
                    && qFlat.contains(shortName.toLowerCase(Locale.ROOT).replace(" ", ""))) {
                s = Math.max(s, 70 + shortName.length());
            }
            // 英文/混合名拆分：Web前端开发 → web前端 / 前端开发
            String[] parts = nFlat.split("[\\s·/、,，]+");
            for (String p : parts) {
                if (p.length() >= 2 && qFlat.contains(p)) s = Math.max(s, 60 + p.length());
            }
            // 后缀匹配：中文课名去掉前缀限定词（"大学英语(III)"→"英语"）
            for (int cut = 1; cut <= 2 && cut < nFlat.length(); cut++) {
                String tail = nFlat.substring(cut);
                if (tail.length() >= 2 && qFlat.contains(tail) && !satisfied(qFlat, tail)) {
                    s = Math.max(s, 40 + tail.length());
                }
            }
            // 简介关键词命中（弱信号）
            if (c.getIntro() != null && s == 0) {
                for (String kw : qFlat.split("[^\\u4e00-\\u9fa5a-z0-9]+")) {
                    if (kw.length() >= 2 && c.getIntro().toLowerCase(Locale.ROOT).contains(kw.toLowerCase(Locale.ROOT))) {
                        s = Math.max(s, 20 + kw.length());
                        break;
                    }
                }
            }
            if (s > 0) score.put(c.getId(), s);
        }
        if (score.isEmpty()) return new ArrayList<>();

        // 取分数最高的 3 门，同分时按名称长度优先
        List<Map.Entry<Long, Integer>> es = new ArrayList<>(score.entrySet());
        es.sort((a, b) -> b.getValue() - a.getValue());
        List<Course> out = new ArrayList<>();
        int top = es.get(0).getValue();
        for (Map.Entry<Long, Integer> e : es) {
            if (out.size() >= 3) break;
            if (e.getValue() < top - 12) break;   // 只保留与最高分接近的候选
            Course c = courseRepo.findById(e.getKey()).orElse(null);
            if (c != null) out.add(c);
        }
        return out;
    }

    /** 去掉课程名结尾的修饰词，得到核心简称（心理学导论→心理学） */
    private String stripSuffix(String name) {
        String base = name.replaceAll("[（(].*?[)）]", "").trim();
        String[] suffixes = {"导论", "基础", "实训", "实践", "入门", "鉴赏", "赏析", "概论", "方法", "应用", "进阶", "工程化", "基础实践"};
        for (String suf : suffixes) {
            if (base.length() > suf.length() + 1 && base.endsWith(suf)) {
                return base.substring(0, base.length() - suf.length());
            }
        }
        return null;
    }

    /** 判断短词是否已被更长词覆盖（避免"中国茶文化"里的"茶"到处误命中） */
    private boolean satisfied(String qFlat, String tail) {
        for (String other : new String[]{"中国茶文化"}) {
            if (other.contains(tail) && qFlat.contains(other)) return true;
        }
        return false;
    }

    // ================= 上下文装配 =================

    /** 构造 system 提示词：角色 + 术语 + 规则 + 权威数据块 + 输出要求 */
    public String buildSystemPrompt(Recognition rec, String question, Long userId, String role) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是「智学选课」大学生选课平台的 AI 助手，面向")
          .append(roleCn(role)).append("。请用简体中文、简洁专业、先结论后细节地回答。\n\n");
        sb.append(glossary()).append("\n");
        sb.append(schoolProfile()).append("\n");
        sb.append(systemIntro()).append("\n");
        sb.append(businessRules(configRepo.findById(1L).orElse(null))).append("\n");

        sb.append("【回答纪律】\n");
        sb.append("1. 数字、课程名、学分、老师、时间、教室、名额等事实，只能来自下方【权威数据】；")
          .append("数据中未出现的，必须说明「系统未提供该信息」，严禁编造或用常识推测。\n");
        sb.append("2. 学生问「我的」时，以【我的…】数据块为准；涉及尚未公布的录取结果，")
          .append("必须说明取决于积分高低与同分抽签，不能承诺一定录取。\n");
        sb.append("3. 回答控制在 400 字以内，多用短列表；先给一句结论，再给具体明细。\n");
        sb.append("4. 若问题超出本平台业务范围（课程内容泛谈、编程语法等），可以正常作答，但不要虚构平台数据。\n");
        sb.append("5. 信息按类别处理：\n")
          .append("   ① 用户个人数据与具体业务数字（我的成绩 / 选课 / 学分 / 录取结果、课程容量与已报人数等）：只能来自【权威数据】，没有就明确回答「系统未提供该信息」，严禁编造；引用其中的具体数字时，在句末标注「（系统数据）」作为来源角标；\n")
          .append("   ② 通用世界常识（地理、历史、科学、文化等公知事实）：直接用你自己的知识正常回答，不要以「系统未提供」拒绝；\n")
          .append("   ③ 关于本系统自身的介绍类问题（功能、模块、选课规则、使用方法、技术架构）：基于【系统简介】【选课规则】【术语对照】正常介绍，不要回答「未提供」；\n")
          .append("   ④ 判断信息是否存在时要细读上下文——例如【校情资料】写明「集美区天马路 288 号」，问「在哪个区」就应回答集美区；\n")
          .append("   任何情况下严禁编造「某路」「某处」这类模糊占位表述；真正不掌握的信息要坦诚说明。\n");
        sb.append("6. 「毕业设计导师」与课程的「授课教师」是两个不同概念：学生问「我的导师 / 谁指导我」时，")
          .append("必须回答【我的毕业设计导师】数据块里由导师双选确认的姓名，")
          .append("严禁用《毕业设计(上)》《毕业设计(下)》等课程表上的授课教师代替；")
          .append("只有明确问「这门课谁上 / 授课教师 / 任课老师」时才回答授课教师。\n");
        sb.append("7. 联网能力：本平台已接入联网搜索。问题涉及最新消息、报名时间、考试时间、放假安排、天气等时效性/外部信息时，")
          .append("系统会自动检索公开网页，并把结果放在【联网搜索结果】数据块中；引用时须标注 [来源n] 与抓取时间，")
          .append("与【权威数据】冲突时一律以业务数据为准。\n")
          .append("   被问到「你能联网搜索吗 / 能不能上网查 / 支持联网吗」时，如实回答「可以」，")
          .append("说明触发方式（系统按问题类型自动检索，也可以说「帮我联网查一下…」），")
          .append("严禁回答「我无法联网」「我是离线模型」「无法实时搜索」；")
          .append("若本轮没有【联网搜索结果】数据块，说明本次问题未触发检索即可，不要虚构搜索结果或声称刚搜过。\n");
        sb.append("8. 推荐或列举课程时必须核对时间冲突：数据块里带「⚠与…时间冲突」标记的课程，")
          .append("一律不能推荐成「可以选/建议选」，要说明「与《某课》上课时间重叠，需先退课或另选时段」；")
          .append("推荐清单优先给时间不冲突的课，并说明已自动排除了哪些冲突课程；")
          .append("学生问「哪些课和我冲突/帮我避开冲突」时，按【时间冲突核对】数据块逐门回答，不要凭记忆推测时段。\n\n");

        sb.append("【权威数据】\n");
        appendSystemState(sb);
        // 学生维度事实：任何问题都可能问到导师，无条件注入，避免被意图识别漏掉
        if ("student".equals(role)) {
            appendMyAdvisor(sb, rec, userId);
            // 课表时段是无条件事实：即使意图被误判成「我的选课」等，
            // 推荐/建议时也必须有时间基准，否则 AI 会推荐与已选课冲突的课
            appendMyTimetable(sb, rec, userId);
        }
        appendIntentData(sb, rec, question, userId, role);
        sb.append("\n请基于以上【权威数据】回答用户问题。\n");
        return sb.toString();
    }

    private void appendSystemState(StringBuilder sb) {
        SystemConfig cfg = configRepo.findById(1L).orElse(null);
        sb.append("\n-- 【系统状态】--\n");
        if (cfg == null) {
            sb.append("配置读取失败\n");
            return;
        }
        sb.append("当前第 ").append(cfg.getCurrentRound()).append(" 轮；")
          .append(Boolean.TRUE.equals(cfg.getRoundOpen()) ? "选课进行中" : "选课已关闭").append("；")
          .append("阶段：").append(phaseName(cfg.getCurrentPhase())).append("。\n");
        sb.append("每学期学分上限 ").append(cfg.getMaxCreditPerSemester()).append(" 分；")
          .append("每轮积分池 50 分。\n");
        sb.append("平台共 ").append(courseRepo.count()).append(" 门课程。\n");
    }

    private void appendIntentData(StringBuilder sb, Recognition rec, String question, Long userId, String role) {
        switch (rec.intent) {
            case GRADUATION -> { appendGraduation(sb, rec, userId); appendRecommend(sb, rec, userId); }
            case GRADES -> appendGrades(sb, rec, userId);
            case MY_ENROLL -> { appendMyEnroll(sb, rec, userId); appendMyIntent(sb, rec, userId); }
            case MY_INTENT -> { appendMyIntent(sb, rec, userId); appendMyEnroll(sb, rec, userId); }
            case WAITLIST -> appendWaitlist(sb, rec, userId);
            case COURSE_INFO -> { appendCourseInfo(sb, rec, userId); appendRelatedCourses(sb, rec, userId); }
            case CONFLICT -> appendConflict(sb, rec, userId);
            case RECOMMEND -> { appendRecommend(sb, rec, userId); appendMyEnroll(sb, rec, userId); }
            case ROUND_RULE -> { appendRoundWindow(sb, rec); appendCourseOverview(sb, rec, userId); }
            case TEACHER -> appendTeacher(sb, rec, userId);
            case ADMIN -> appendAdmin(sb, rec);
            // 导师块已在 buildSystemPrompt 无条件注入，这里补上毕设课程明细即可
            case ADVISOR -> appendMyEnroll(sb, rec, userId);
            default -> { appendCourseOverview(sb, rec, userId); appendMyEnroll(sb, rec, userId); }
        }
    }

    // ---- 各意图的数据块 ----

    private void appendGraduation(StringBuilder sb, Recognition rec, Long userId) {
        if (userId == null) {
            sb.append("\n-- 【毕业进度】--\n未登录，无法读取个人毕业进度。\n");
            return;
        }
        Map<String, Object> g = graduationService.summary(userId);
        if (g == null) { sb.append("\n-- 【毕业进度】--\n读取失败\n"); return; }
        sb.append("\n-- 【我的毕业进度】--\n");
        Object total = g.get("total");
        if (total instanceof Map<?, ?> t) {
            sb.append("总学分：已获 ").append(t.get("earned")).append(" / 要求 ").append(t.get("required"))
              .append("，缺口 ").append(t.get("gap")).append(" 分（完成度 ").append(t.get("percent")).append("%）。\n");
        }
        Object cats = g.get("categories");
        if (cats instanceof List<?> cl) {
            for (Object o : cl) {
                if (!(o instanceof Map<?, ?> c)) continue;
                sb.append("· ").append(c.get("key")).append("：已获 ").append(c.get("earned"))
                  .append(" / 要求 ").append(c.get("required")).append("，缺口 ").append(c.get("gap"))
                  .append(" 分，完成度 ").append(c.get("percent")).append("%")
                  .append(Boolean.TRUE.equals(c.get("done")) ? "（已达标）" : "（未达标）").append("\n");
                Object miss = c.get("missing");
                if (miss instanceof List<?> ml && !ml.isEmpty()) {
                    sb.append("  必修缺修：").append(String.join("、", ml.stream().map(String::valueOf).toList())).append("\n");
                }
            }
        }
        Object rec2 = g.get("recommend");
        if (rec2 instanceof List<?> rl && !rl.isEmpty()) {
            sb.append("系统推荐补修课程（按缺口排序）：\n");
            int i = 0;
            for (Object o : rl) {
                if (!(o instanceof Map<?, ?> c)) continue;
                if (++i > 8) break;
                sb.append("· 《").append(c.get("name")).append("》").append(c.get("credit")).append("学分｜")
                  .append(c.get("category")).append("｜教师：").append(c.get("teacherName"))
                  .append("｜时间：").append(c.get("time")).append("｜地点：").append(c.get("location"))
                  .append("｜全字段:").append(dumpAllFields(c)).append("\n");
            }
        }
        rec.sources.add("我的毕业进度");
    }

    /**
     * 缺口推荐课程 —— 必须排除与「我的已录取课程」时间冲突的课。
     * 冲突的课不再作为推荐项，单独列在「已排除」里说明原因；
     * 缺口推荐被冲突挡光时，再从全库补几门时间不冲突的选修，保证推荐不是空的。
     */
    private void appendRecommend(StringBuilder sb, Recognition rec, Long userId) {
        if (userId == null) return;
        Map<String, Object> g = graduationService.summary(userId);
        if (g == null) return;
        List<Course> mine = myApprovedCourses(userId);

        List<Map<?, ?>> free = new ArrayList<>();
        List<String> blocked = new ArrayList<>();
        Object rec2 = g.get("recommend");
        if (rec2 instanceof List<?> rl) {
            for (Object o : rl) {
                if (!(o instanceof Map<?, ?> c)) continue;
                Course course = courseOf(c);
                if (course == null) continue;
                List<String> hits = conflictWith(course, mine);
                if (hits.isEmpty()) free.add(c);
                else blocked.add("《" + course.getName() + "》（与" + String.join("、", hits) + " 冲突）");
            }
        }

        sb.append("\n-- 【按我的缺口推荐课程（已排除与课表时间冲突的课）】--\n");
        int i = 0;
        for (Map<?, ?> c : free) {
            if (++i > 10) break;
            sb.append("· 《").append(c.get("name")).append("》").append(c.get("credit")).append("学分｜")
              .append(c.get("category")).append("｜缺口类别：").append(c.get("group"))
              .append("（差 ").append(c.get("groupGap")).append(" 分）｜教师：").append(c.get("teacherName"))
              .append("｜时间：").append(c.get("time")).append("｜地点：").append(c.get("location"))
              .append("｜全字段:").append(dumpAllFields(c))
              .append("｜时间不冲突，可直接报名\n");
        }
        if (i == 0) sb.append("（缺口推荐课程全部与课表冲突，见下方「已排除」）\n");

        // 推荐量不足时补「时间不冲突的可选课」，避免只剩一句"都冲突"
        if (free.size() < 3) {
            List<Course> extra = freeElectives(mine, 6 - free.size());
            if (!extra.isEmpty()) {
                sb.append("\n-- 【时间不冲突、仍可报名的选修课（补充候选）】--\n");
                for (Course c : extra) {
                    sb.append("· 《").append(c.getName()).append("》").append(nz(c.getCredit(), "?")).append("学分｜")
                      .append(nz(c.getCategory(), "未分类")).append("｜教师：").append(nz(courseService.teacherName(c.getTeacherId()), "未指定"))
                      .append("｜时间：").append(nz(c.getTime(), "待定")).append("｜地点：").append(nz(c.getLocation(), "待定"))
                      .append("｜容量：").append(nz(c.getCapacity(), "?")).append(" 人")
                      .append("｜全字段:").append(dumpAllFields(c)).append("\n");
                }
                rec.sources.add("可报选修课程");
            }
        }

        if (!blocked.isEmpty()) {
            sb.append("\n已自动排除 ").append(blocked.size()).append(" 门与课表时间冲突的课（不要再推荐它们）：\n");
            for (String b : blocked) sb.append("· ").append(b).append("\n");
        }
        if (!mine.isEmpty()) {
            sb.append("\n我的已录取课程时间（排课基准，推荐必须避开这些时段）：\n");
            for (Course m : mine) {
                sb.append("· 《").append(m.getName()).append("》").append(nz(m.getTime(), "待定")).append("\n");
            }
        }
        rec.sources.add("缺口推荐课程（已排除冲突）");
    }

    /** 从推荐条目里取回课程实体（推荐 Map 里带 courseId） */
    private Course courseOf(Map<?, ?> m) {
        Object cid = m.get("courseId");
        if (cid instanceof Number n) return courseRepo.findById(n.longValue()).orElse(null);
        return null;
    }

    /** 我的本学期已录取课程（跨轮去重；历史学期的课已结课，不占本学期课表，不能当冲突基准） */
    private List<Course> myApprovedCourses(Long userId) {
        if (userId == null) return List.of();
        Integer curSem = currentSemesterNoOf(userId);
        List<Course> out = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        for (Enrollment e : enrollmentRepo.findByStudentId(userId)) {
            if (!"approved".equals(e.getStatus())) continue;
            if (curSem != null && e.getSemesterNo() != null && !e.getSemesterNo().equals(curSem)) continue;
            Course c = courseRepo.findById(e.getCourseId()).orElse(null);
            if (c == null) continue;
            if (seen.add(c.getId())) out.add(c);
        }
        return out;
    }

    /** 本学期课表基准：无条件注入，保证任何意图下推荐课程都有时间参照 */
    private void appendMyTimetable(StringBuilder sb, Recognition rec, Long userId) {
        List<Course> mine = myApprovedCourses(userId);
        if (mine.isEmpty()) return;
        sb.append("\n-- 【我的本学期课表（时间基准，推荐/建议课程必须避开）】--\n");
        for (Course c : mine) {
            sb.append("· 《").append(c.getName()).append("》").append(nz(c.getTime(), "待定"))
              .append("｜").append(nz(c.getLocation(), "地点待定")).append("\n");
        }
        sb.append("说明：以上为本学期已录取课程的固定时段；推荐或列举课程时若与其中任一时段重叠，")
          .append("必须标注「与《某课》时间冲突」并说明需先退课或换时段，不能直接推荐为可选。\n");
        rec.sources.add("我的课表");
    }

    /** 该学生当前学期序（拿不到年级时返回 null = 不做学期过滤） */
    private Integer currentSemesterNoOf(Long userId) {
        if (userId == null) return null;
        User u = userRepo.findById(userId).orElse(null);
        if (u == null || u.getGrade() == null) return null;
        SystemConfig cfg = configRepo.findById(1L).orElse(null);
        int offset = cfg == null || cfg.getSemesterOffset() == null ? 0 : cfg.getSemesterOffset();
        return GraduationService.currentSemesterNo(u.getGrade(), java.time.LocalDate.now(), offset);
    }

    /** 目标课程与我已录取课程的时间冲突明细（同 id 不算冲突） */
    private List<String> conflictWith(Course target, List<Course> mine) {
        List<String> hits = new ArrayList<>();
        if (target == null) return hits;
        for (Course m : mine) {
            if (m.getId().equals(target.getId())) continue;
            if (conflict(m, target)) hits.add("《" + m.getName() + "》");
        }
        return hits;
    }

    /** 全库筛「时间不冲突、未录取、非必修、有名额」的选修课 */
    private List<Course> freeElectives(List<Course> mine, int limit) {
        if (limit <= 0) return List.of();
        Set<Long> taken = new LinkedHashSet<>();
        for (Course c : mine) taken.add(c.getId());
        List<Course> out = new ArrayList<>();
        for (Course c : courseRepo.findAll()) {
            if (out.size() >= limit) break;
            if (c.getCategory() != null && c.getCategory().contains("必修")) continue;
            if (taken.contains(c.getId())) continue;
            if (c.getCapacity() == null || c.getCapacity() <= 0) continue;
            if (!conflictWith(c, mine).isEmpty()) continue;
            out.add(c);
        }
        return out;
    }

    /** 当前学生双选已确认的毕业设计导师（graduate_advisors.status=approved） */
    private GraduateAdvisor myApprovedAdvisor(Long userId) {
        if (userId == null) return null;
        for (GraduateAdvisor a : advisorRepo.findByStudentIdOrderByIdDesc(userId)) {
            if ("approved".equals(a.getStatus())) return a;
        }
        return null;
    }

    private String myAdvisorName(Long userId) {
        GraduateAdvisor ga = myApprovedAdvisor(userId);
        if (ga == null) return null;
        User t = userRepo.findById(ga.getTeacherId()).orElse(null);
        return t == null ? null : t.getName();
    }

    /**
     * 【我的毕业设计导师】——事实源是导师双选 graduate_advisors，不是 courses.teacher_id。
     * 旧实现只注入课程授课教师，导致学生问「我的毕业设计导师是谁」时 AI 回答
     * 「系统里没有毕业设计导师这一字段」，只能列出《毕业设计(上)/(下)》的任课教师。
     */
    private void appendMyAdvisor(StringBuilder sb, Recognition rec, Long userId) {
        if (userId == null) return;
        GraduateAdvisor ga = myApprovedAdvisor(userId);
        sb.append("\n-- 【我的毕业设计导师】--\n");
        if (ga == null) {
            sb.append("尚未确认毕业设计导师（双选未申请，或申请尚未被教师通过）。\n");
        } else {
            User t = userRepo.findById(ga.getTeacherId()).orElse(null);
            sb.append("毕业设计导师：").append(t == null ? "教师信息缺失" : t.getName()).append("；");
            if (t != null && t.getNo() != null) sb.append("工号：").append(t.getNo()).append("；");
            if (t != null && t.getCollege() != null) sb.append("学院：").append(t.getCollege()).append("；");
            if (ga.getTopic() != null && !ga.getTopic().isBlank()) sb.append("毕业设计课题：").append(ga.getTopic()).append("；");
            sb.append("双选状态：已确认（学生本人选定并经教师通过）。")
              .append("全字段:").append(dumpAllFields(ga)).append("\n");
            sb.append("说明：这是学生本人的导师双选结果；《毕业设计(上)》《毕业设计(下)》课程表上的「授课教师」")
              .append("只是课程名义任课教师，与「毕业设计导师」不是同一概念。")
              .append("回答「我的导师 / 谁指导我」必须以本数据块为准。\n");
        }
        rec.sources.add("我的毕业设计导师");
    }

    private void appendMyEnroll(StringBuilder sb, Recognition rec, Long userId) {
        if (userId == null) {
            sb.append("\n-- 【我的选课】--\n未登录，无法读取个人选课记录。\n");
            return;
        }
        String advisor = myAdvisorName(userId);
        Integer curSem = currentSemesterNoOf(userId);
        List<Enrollment> all = enrollmentRepo.findByStudentId(userId);
        // 只列本学期：历史学期已结课，混在一起会让 AI 误当成「本周课表」参与冲突判断
        List<Enrollment> list = all.stream()
                .filter(e -> curSem == null || e.getSemesterNo() == null || curSem.equals(e.getSemesterNo()))
                .toList();
        long historyCourses = all.stream()
                .filter(e -> curSem != null && e.getSemesterNo() != null && !curSem.equals(e.getSemesterNo()))
                .filter(e -> "approved".equals(e.getStatus()))
                .map(Enrollment::getCourseId).distinct().count();
        sb.append("\n-- 【我的选课（本学期）】--\n");
        if (list.isEmpty()) {
            sb.append("本学期尚未选中任何课程。\n");
            if (historyCourses > 0) sb.append("历史学期另已修 ").append(historyCourses).append(" 门课程（成绩见【我的成绩】）。\n");
            rec.sources.add("我的选课");
            return;
        }
        // 同一课程可能跨轮次有多条记录（改选/补报），按课程去重，保留最新一轮
        Map<Long, Enrollment> latest = new LinkedHashMap<>();
        for (Enrollment e : list) {
            Enrollment prev = latest.get(e.getCourseId());
            if (prev == null || (e.getRound() != null && prev.getRound() != null && e.getRound() > prev.getRound())
                    || (e.getRound() != null && prev.getRound() == null)) {
                latest.put(e.getCourseId(), e);
            }
        }
        int credit = 0;
        int count = 0;
        sb.append("已选 ").append(latest.size()).append(" 门：\n");
        List<Course> approvedBase = myApprovedCourses(userId);
        for (Enrollment e : latest.values()) {
            Course c = courseRepo.findById(e.getCourseId()).orElse(null);
            if (c == null) continue;
            count++;
            if ("approved".equals(e.getStatus())) credit += c.getCredit() == null ? 0 : c.getCredit();
            sb.append("· 《").append(c.getName()).append("》").append(nz(c.getCredit(), "?")).append("学分｜")
              .append(nz(c.getCategory(), "未分类")).append("｜教师：").append(nz(courseService.teacherName(c.getTeacherId()), "未指定"))
              .append("｜时间：").append(nz(c.getTime(), "待定")).append("｜地点：").append(nz(c.getLocation(), "待定"))
              .append("｜第").append(nz(e.getRound(), "?")).append("轮｜状态：").append(statusCn(e.getStatus()))
              .append(e.getPoints() != null ? "｜投入 " + e.getPoints() + " 分" : "")
              // 毕设课：同时给出学生本人双选确认的导师，避免把课程授课教师误当成导师
              .append(c.getName() != null && c.getName().contains("毕业设计")
                      ? "｜毕业设计导师：" + nz(advisor, "未确认") : "");
            // 非「已录取」条目（待结算 / 已退课 / 落选）若与课表时段重叠，标注出来，
            // 避免 AI 把一条已退课的记录当成「可选且不冲突」的课推荐给学生
            if (!"approved".equals(e.getStatus())) {
                List<String> hits = conflictWith(c, approvedBase);
                if (!hits.isEmpty()) sb.append("｜⚠与").append(String.join("、", hits)).append(" 时间冲突");
            }
            sb.append("｜全字段:").append(dumpAllFields(e));
            sb.append("\n");
        }
        SystemConfig cfg = configRepo.findById(1L).orElse(null);
        int limit = cfg != null && cfg.getMaxCreditPerSemester() != null ? cfg.getMaxCreditPerSemester() : 24;
        sb.append("本学期已录取课程学分合计 ").append(credit).append(" 分（跨轮次累计；单学期上限 ")
          .append(limit).append(" 分）\n");
        if (historyCourses > 0) {
            sb.append("历史学期另已修 ").append(historyCourses).append(" 门课程（不占本学期课表，不参与时间冲突判断；成绩见【我的成绩】）。\n");
        }
        rec.sources.add("我的选课");
    }

    private void appendMyIntent(StringBuilder sb, Recognition rec, Long userId) {
        if (userId == null) {
            sb.append("\n-- 【我的意向单】--\n未登录，无法读取个人意向单。\n");
            return;
        }
        List<Map<String, Object>> list = intentService.list(userId);
        sb.append("\n-- 【我的意向单】--\n");
        if (list.isEmpty()) { sb.append("意向单为空。\n"); rec.sources.add("我的意向单"); return; }
        int total = 0;
        for (Map<String, Object> it : list) {
            int p = it.get("desiredPoints") == null ? 0 : (Integer) it.get("desiredPoints");
            total += p;
            sb.append("· 《").append(it.get("courseName")).append("》投入 ").append(p).append(" 分｜")
              .append(it.get("category")).append("｜").append(it.get("credit")).append("学分｜")
              .append("容量 ").append(it.get("capacity")).append("｜教师：").append(it.get("teacherName"))
              .append("｜时间：").append(it.get("time"))
              .append("｜全字段:").append(dumpAllFields(it)).append("\n");
        }
        sb.append("合计投入 ").append(total).append(" / 50 分，剩余 ").append(50 - total).append(" 分。\n");
        rec.sources.add("我的意向单");
    }

    private void appendWaitlist(StringBuilder sb, Recognition rec, Long userId) {
        if (userId == null) {
            sb.append("\n-- 【我的候补】--\n未登录，无法读取候补队列。\n");
            return;
        }
        sb.append("\n-- 【我的候补】--\n");
        List<com.zhixue.entity.Waitlist> rows = new ArrayList<>();
        for (int r = 1; r <= 3; r++) {
            rows.addAll(waitlistRepo.findByStudentIdAndRoundAndStatusInOrderByJoinTimeAsc(
                    userId, r, WaitlistService.ACTIVE));
        }
        if (rows.isEmpty()) { sb.append("当前没有候补记录。\n"); rec.sources.add("我的候补"); return; }
        for (com.zhixue.entity.Waitlist w : rows) {
            Course c = courseRepo.findById(w.getCourseId()).orElse(null);
            int pos = waitlistService.position(w);
            int ahead = Math.max(0, pos - 1);
            sb.append("· 《").append(c == null ? "（课程已删除）" : c.getName()).append("》｜第")
              .append(w.getRound()).append("轮｜状态：").append(waitStatusCn(w.getStatus()))
              .append("｜排位：第 ").append(pos).append(" 位｜前方还有 ").append(ahead).append(" 人")
              .append("｜全字段:").append(dumpAllFields(w)).append("\n");
        }
        sb.append("提示：候补递补时会短信/站内提醒，需在确认时限内确认，逾期顺延给下一位。\n");
        rec.sources.add("我的候补");
    }

    private void appendGrades(StringBuilder sb, Recognition rec, Long userId) {
        if (userId == null) {
            sb.append("\n-- 【我的成绩】--\n未登录，无法读取成绩。\n");
            return;
        }
        sb.append("\n-- 【我的成绩】--\n");
        List<com.zhixue.entity.Grade> grades = gradeService.findByStudent(userId);
        if (grades.isEmpty()) { sb.append("暂无已发布成绩。\n"); rec.sources.add("我的成绩"); return; }
        // 学期归属:成绩行自带 semester_no,按「学年-学期」分组输出,支持「某学期成绩」类提问
        Integer entryYear = userRepo.findById(userId).map(User::getGrade).orElse(null);
        Map<Integer, List<String>> rowsBySem = new TreeMap<>();
        Map<Integer, double[]> sumBySem = new TreeMap<>();   // [总分和, 门数, 学分和, 学分加权总分]
        double totalScore = 0; int cnt = 0; double creditSum = 0, creditWeighted = 0;
        for (com.zhixue.entity.Grade g : grades) {
            Course c = courseRepo.findById(g.getCourseId()).orElse(null);
            if (c == null) continue;
            String line = "· 《" + c.getName() + "》｜学分 " + nz(c.getCredit(), "?")
              + "｜平时分 " + fmt(g.getRegularScore())
              + "｜期末分 " + fmt(g.getFinalScore())
              + "｜总评 " + fmt(g.getTotalScore())
              + "｜GPA " + String.format(Locale.ROOT, "%.1f", GradeService.scoreToGpa(
                      g.getTotalScore() == null ? 0 : g.getTotalScore()))
              + "｜全字段:" + dumpAllFields(g);
            int sem = g.getSemesterNo() == null ? -1 : g.getSemesterNo();
            rowsBySem.computeIfAbsent(sem, k -> new ArrayList<>()).add(line);
            if (g.getTotalScore() != null) {
                totalScore += g.getTotalScore(); cnt++;
                int cr = c.getCredit() == null ? 0 : c.getCredit();
                creditSum += cr; creditWeighted += g.getTotalScore() * cr;
                double[] s = sumBySem.computeIfAbsent(sem, k -> new double[4]);
                s[0] += g.getTotalScore(); s[1]++; s[2] += cr; s[3] += g.getTotalScore() * cr;
            }
        }
        for (Map.Entry<Integer, List<String>> en : rowsBySem.entrySet()) {
            sb.append("\n[").append(semLabel(en.getKey(), entryYear)).append("] 共 ")
              .append(en.getValue().size()).append(" 门");
            double[] s = sumBySem.get(en.getKey());
            if (s != null && s[1] > 0) {
                sb.append("｜平均总评 ").append(String.format(Locale.ROOT, "%.1f", s[0] / s[1]));
                if (s[2] > 0) sb.append("｜学分加权均分 ").append(String.format(Locale.ROOT, "%.1f", s[3] / s[2]));
            }
            sb.append("\n");
            for (String line : en.getValue()) sb.append(line).append("\n");
        }
        if (cnt > 0) {
            sb.append("\n全部学期合计：").append(cnt).append(" 门｜平均总评 ")
              .append(String.format(Locale.ROOT, "%.1f", totalScore / cnt)).append(" 分");
            if (creditSum > 0) {
                sb.append("｜学分加权均分：").append(String.format(Locale.ROOT, "%.1f", creditWeighted / creditSum)).append(" 分");
            }
            sb.append("\n");
        }
        sb.append("学期标注格式：「学年-学期序」，如 2025-2026-2 即 2025-2026 学年第二学期；")
          .append("回答「某学期成绩」时引用对应分组的课程与汇总。\n");
        rec.sources.add("我的成绩");
    }

    /** 修读学期序 → 「学年-学期」标注（如 2023 级第 6 学期 = 2025-2026-2）；未知返回占位 */
    private String semLabel(int sem, Integer entryYear) {
        if (sem < 0) return "学期未知";
        if (entryYear == null || entryYear <= 0) return "第" + sem + "学期";
        int ay = entryYear + (sem - 1) / 2;
        int st = (sem - 1) % 2 + 1;
        return ay + "-" + (ay + 1) + "-" + st;
    }

    private String fmt(Double d) {
        return d == null ? "未发布" : String.format(Locale.ROOT, "%.1f", d);
    }

    /** null 安全数值（提示词里绝不能出现 null） */
    private static String nz(Object v, String dft) {
        return v == null ? dft : String.valueOf(v);
    }

    /** 不进提示词的字段：敏感 / 超长 / 无检索价值 */
    private static final Set<String> DENY_FIELDS = Set.of(
            "password", "token", "avatar", "images", "base64", "attachments", "files",
            "intro", "description", "content", "remark", "reason", "note");

    private static final com.fasterxml.jackson.databind.ObjectMapper FIELD_OM =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * 实体 / Map → 全字段紧凑串（k=v | k=v）。
     * 数据块的可读行只挑了关键字段，这里把实体全部字段原样给出 —— 自动跟随字段演进，
     * 保证「每次查询都有迹可循」：AI 引用的任何事实都能落到一个具体字段上。
     */
    private String dumpAllFields(Object entity) {
        try {
            Map<?, ?> m = FIELD_OM.convertValue(entity, Map.class);
            StringBuilder s = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                String k = String.valueOf(e.getKey()).toLowerCase();
                Object v = e.getValue();
                if (DENY_FIELDS.contains(k) || v == null || (v instanceof String s2 && s2.isBlank())) continue;
                String vs = String.valueOf(v);
                if (vs.length() > 60) vs = vs.substring(0, 60) + "…";
                if (!first) s.append(", ");
                s.append(e.getKey()).append("=").append(vs);
                first = false;
            }
            return s.append("}").toString();
        } catch (Exception ex) {
            return "{}";
        }
    }

    private void appendCourseInfo(StringBuilder sb, Recognition rec, Long userId) {
        if (rec.courses.isEmpty()) {
            sb.append("\n-- 【课程详情】--\n未识别到具体课程。\n");
            appendCourseOverview(sb, rec, userId);
            return;
        }
        List<Course> mine = myApprovedCourses(userId);
        sb.append("\n-- 【课程详情】--\n");
        for (Course c : rec.courses) {
            int applied = enrollmentRepo.findByCourseIdAndRound(c.getId(), currentRound()).size();
            sb.append("《").append(c.getName()).append("》\n")
              .append("  类别：").append(nz(c.getCategory(), "未分类")).append("｜学分：").append(nz(c.getCredit(), "?"))
              .append("｜学时：").append(nz(c.getHours(), "?")).append("\n")
              .append("  教师：").append(nz(courseService.teacherName(c.getTeacherId()), "未指定"))
              .append("｜时间：").append(nz(c.getTime(), "待定"))
              .append("｜地点：").append(nz(c.getLocation(), "待定"))
              .append("｜校区：").append(nz(c.getCampus(), "主校区")).append("\n")
              .append("  容量：").append(nz(c.getCapacity(), "?")).append(" 人｜本轮已报：").append(applied).append(" 人\n")
              .append("  课程简介：").append(c.getIntro() == null ? "（暂无）" : c.getIntro()).append("\n")
              .append("  全字段:").append(dumpAllFields(c)).append("\n");
            // 选课可行性：直接给出与「我的课表」的冲突结论，避免 AI 只能罗列时间让学生自己对
            List<String> hits = conflictWith(c, mine);
            if (!hits.isEmpty()) {
                sb.append("  ⚠ 时间冲突：与本学期已录取的 ").append(String.join("、", hits)).append(" 上课时间重叠，当前无法报名。\n");
            } else if (!mine.isEmpty()) {
                sb.append("  ✅ 与本学期已录取课程无时间冲突，可以报名。\n");
            }
        }
        rec.sources.add("课程详情");
    }

    /** 同学期同类别课程：给"还有什么类似的课"用 */
    private void appendRelatedCourses(StringBuilder sb, Recognition rec, Long userId) {
        if (rec.courses.isEmpty()) return;
        Course c0 = rec.courses.get(0);
        List<Course> same = courseRepo.findByCategory(c0.getCategory());
        List<Course> mine = myApprovedCourses(userId);
        sb.append("\n-- 【同类别其他课程】--\n");
        int i = 0;
        for (Course c : same) {
            if (c.getId().equals(c0.getId())) continue;
            if (++i > 8) break;
            sb.append("· 《").append(c.getName()).append("》").append(nz(c.getCredit(), "?")).append("学分｜教师：")
              .append(nz(courseService.teacherName(c.getTeacherId()), "未指定")).append("｜时间：").append(nz(c.getTime(), "待定"))
              .append("｜地点：").append(nz(c.getLocation(), "待定"));
            List<String> hits = conflictWith(c, mine);
            if (!hits.isEmpty()) sb.append("｜⚠与").append(String.join("、", hits)).append(" 时间冲突");
            sb.append("｜全字段:").append(dumpAllFields(c));
            sb.append("\n");
        }
        rec.sources.add("同类别课程");
    }

    /** 冲突判定：后端真实计算，AI 只负责表述 */
    private void appendConflict(StringBuilder sb, Recognition rec, Long userId) {
        sb.append("\n-- 【时间冲突核对】--\n");
        List<Course> mineApproved = myApprovedCourses(userId);

        // A. 点名了课程 → 逐门核对
        if (!rec.courses.isEmpty()) {
            for (Course t : rec.courses) {
                List<String> hits = conflictWith(t, mineApproved);
                rec.facts.put("conflict:" + t.getId(), hits);
                sb.append("《").append(t.getName()).append("》（").append(nz(t.getTime(), "待定")).append("）：");
                if (hits.isEmpty()) {
                    sb.append("与我的已录取课程").append(mineApproved.isEmpty() ? "（我还没有已录取课程）" : "").append("无时间冲突。\n");
                } else {
                    sb.append("与 ").append(String.join("、", hits)).append(" 存在时间冲突。\n");
                }
            }
            appendMineTime(sb, mineApproved);
            rec.sources.add("时间冲突核对");
            return;
        }

        // B. 没点名课程 → 全库反向扫描「哪些课和我冲突」，再补意向单逐门核对
        if (mineApproved.isEmpty()) {
            sb.append("我还没有已录取课程，不存在时间冲突；任何课程都可以报。\n");
            rec.sources.add("时间冲突核对");
            return;
        }
        List<String> clash = new ArrayList<>();
        int total = 0;
        for (Course c : courseRepo.findAll()) {
            if (c.getCategory() != null && c.getCategory().contains("必修")) continue;
            if (mineApproved.stream().anyMatch(m -> m.getId().equals(c.getId()))) continue;
            List<String> hits = conflictWith(c, mineApproved);
            if (hits.isEmpty()) continue;
            total++;
            if (clash.size() < 20) {
                clash.add("《" + c.getName() + "》" + nz(c.getTime(), "") + " → 与" + String.join("、", hits) + " 冲突");
            }
        }
        sb.append("与我已录取课程时间冲突的选修课共 ").append(total).append(" 门")
          .append(total > 20 ? "（列出前 20 门）：\n" : "：\n");
        for (String s : clash) sb.append("· ").append(s).append("\n");
        if (total == 0) sb.append("（当前没有任何冲突课程，可以自由选课）\n");

        // 意向单课程逐门核对（意向课若冲突，提示换课/换时段）
        List<Map<String, Object>> intents = userId == null ? List.of() : intentService.list(userId);
        if (!intents.isEmpty()) {
            sb.append("\n我的意向单课程核对：\n");
            for (Map<String, Object> it : intents) {
                Object cid = it.get("courseId");
                if (!(cid instanceof Number n)) continue;
                Course c = courseRepo.findById(n.longValue()).orElse(null);
                if (c == null) continue;
                List<String> hits = conflictWith(c, mineApproved);
                sb.append("· 《").append(c.getName()).append("》（").append(nz(c.getTime(), "待定")).append("）：")
                  .append(hits.isEmpty() ? "无冲突" : "与 " + String.join("、", hits) + " 冲突").append("\n");
            }
            rec.sources.add("我的意向单");
        }
        appendMineTime(sb, mineApproved);
        rec.sources.add("时间冲突核对");
    }

    /** 「我的已录取课程时间」基准块 */
    private void appendMineTime(StringBuilder sb, List<Course> mine) {
        if (mine.isEmpty()) return;
        sb.append("我的已录取课程时间：\n");
        for (Course m : mine) {
            sb.append("· 《").append(m.getName()).append("》").append(nz(m.getTime(), "待定"))
              .append("｜").append(nz(m.getLocation(), "地点待定")).append("\n");
        }
    }

    private void appendRoundWindow(StringBuilder sb, Recognition rec) {
        sb.append("\n-- 【本轮时间与开放状态】--\n");
        SystemConfig cfg = configRepo.findById(1L).orElse(null);
        if (cfg == null) return;
        sb.append("当前第 ").append(cfg.getCurrentRound()).append(" 轮，")
          .append(Boolean.TRUE.equals(cfg.getRoundOpen()) ? "选课进行中" : "选课已关闭")
          .append("，阶段：").append(phaseName(cfg.getCurrentPhase())).append("。\n");
        sb.append("第 1、2 轮为积分竞价（每轮 50 分池，0-50 分/门，截止后按分降序录取，同分抽签）；")
          .append("第 3 轮先到先得不消耗积分。\n");
        sb.append("系统配置全字段:").append(dumpAllFields(cfg)).append("\n");
        rec.sources.add("选课规则");
    }

    /** 课程概览：按类别分组列出全部课程（保证任何课程都能被问到） */
    private void appendCourseOverview(StringBuilder sb, Recognition rec, Long userId) {
        List<Course> all = courseRepo.findAll();
        List<Course> mine = myApprovedCourses(userId);
        sb.append("\n-- 【课程目录概览（共 ").append(all.size()).append(" 门）】--\n");
        Map<String, List<Course>> byCat = new LinkedHashMap<>();
        for (Course c : all) byCat.computeIfAbsent(c.getCategory() == null ? "其他" : c.getCategory(), k -> new ArrayList<>()).add(c);
        StringBuilder cats = new StringBuilder();
        for (Map.Entry<String, List<Course>> e : byCat.entrySet()) {
            cats.append("【").append(e.getKey()).append(" ").append(e.getValue().size()).append(" 门】");
        }
        sb.append(cats).append("\n");
        /* 问题命中的课程完整列出；未命中时每类列前 2 门作为采样（控制上下文长度） */
        java.util.List<Course> full = new ArrayList<>(rec.courses == null ? java.util.List.of() : rec.courses);
        if (full.isEmpty()) {
            for (List<Course> cl : byCat.values()) {
                for (int i = 0; i < Math.min(2, cl.size()); i++) full.add(cl.get(i));
            }
        }
        for (Course c : full) {
            sb.append("· 《").append(c.getName()).append("》").append(nz(c.getCredit(), "?")).append("学分｜教师：")
              .append(nz(courseService.teacherName(c.getTeacherId()), "未指定")).append("｜时间：").append(nz(c.getTime(), "待定"))
              .append("｜地点：").append(nz(c.getLocation(), "待定")).append("｜容量：").append(nz(c.getCapacity(), "?"))
              .append("｜简介：").append(c.getIntro() == null ? "（暂无）" : c.getIntro());
            List<String> hits = conflictWith(c, mine);
            if (!hits.isEmpty()) sb.append("｜⚠与").append(String.join("、", hits)).append(" 时间冲突");
            sb.append("｜全字段:").append(dumpAllFields(c));
            sb.append("\n");
        }
        if (!mine.isEmpty()) {
            sb.append("我的已录取课程（时间基准）：");
            for (int i = 0; i < mine.size(); i++) {
                Course m = mine.get(i);
                sb.append(i > 0 ? "；" : "").append("《").append(m.getName()).append("》").append(nz(m.getTime(), "待定"));
            }
            sb.append("\n");
        }
        sb.append("（如需其它课程的详情，可直接询问）\n");
        rec.sources.add("课程目录概览");
    }

    private void appendTeacher(StringBuilder sb, Recognition rec, Long userId) {
        if (userId == null) { sb.append("\n-- 【教师视图】--\n未登录。\n"); return; }
        User u = userRepo.findById(userId).orElse(null);
        if (u == null) { sb.append("\n-- 【教师视图】--\n用户不存在。\n"); return; }
        List<Course> mine = courseRepo.findByTeacherId(u.getId());
        sb.append("\n-- 【我讲授的课程】--\n");
        if (mine.isEmpty()) { sb.append("当前没有绑定授课课程。\n"); rec.sources.add("教师视图"); return; }
        for (Course c : mine) {
            int applied = enrollmentRepo.findByCourseIdAndRound(c.getId(), currentRound()).size();
            sb.append("· 《").append(c.getName()).append("》").append(nz(c.getCredit(), "?")).append("学分｜时间：")
              .append(nz(c.getTime(), "待定")).append("｜地点：").append(nz(c.getLocation(), "待定"))
              .append("｜容量：").append(nz(c.getCapacity(), "?")).append("｜本轮报名：").append(applied).append(" 人")
              .append("｜全字段:").append(dumpAllFields(c)).append("\n");
        }
        rec.sources.add("我讲授的课程");
    }

    private void appendAdmin(StringBuilder sb, Recognition rec) {
        sb.append("\n-- 【系统概览】--\n");
        sb.append("课程总数：").append(courseRepo.count()).append(" 门\n");
        sb.append("学生数：").append(userRepo.findByRole("student").size()).append(" 人｜教师数：")
          .append(userRepo.findByRole("teacher").size()).append(" 人\n");
        SystemConfig cfg = configRepo.findById(1L).orElse(null);
        if (cfg != null) {
            sb.append("当前第 ").append(cfg.getCurrentRound()).append(" 轮，")
              .append(Boolean.TRUE.equals(cfg.getRoundOpen()) ? "选课进行中" : "选课已关闭").append("\n");
            sb.append("学分上限 ").append(cfg.getMaxCreditPerSemester())
              .append(" 分｜毕业要求 必修 ").append(cfg.getGradReqRequired())
              .append(" / 选修 ").append(cfg.getGradReqElective())
              .append(" / 通识 ").append(cfg.getGradReqGeneral())
              .append(" / 实践 ").append(cfg.getGradReqPractice()).append("\n");
            sb.append("系统配置全字段:").append(dumpAllFields(cfg)).append("\n");
        }
        rec.sources.add("系统概览");
    }

    // ================= 工具方法 =================

    private int currentRound() {
        SystemConfig cfg = configRepo.findById(1L).orElse(null);
        return cfg != null && cfg.getCurrentRound() != null ? cfg.getCurrentRound() : 1;
    }

    /** 与 EnrollmentService.hasTimeConflict 同口径：同日时段区间重叠即冲突 */
    public boolean conflict(Course a, Course b) {
        for (Slot sa : parseSlots(a.getTime())) {
            for (Slot sb : parseSlots(b.getTime())) {
                if (sa.day.equals(sb.day)) {
                    int aS = toMin(sa.start), aE = toMin(sa.end);
                    int bS = toMin(sb.start), bE = toMin(sb.end);
                    if (aS < bE && bS < aE) return true;
                }
            }
        }
        return false;
    }

    private static class Slot { String day, start, end; }

    private List<Slot> parseSlots(String timeStr) {
        List<Slot> out = new ArrayList<>();
        if (timeStr == null) return out;
        Pattern p = Pattern.compile("(周[一二三四五六日])\\s+(\\d{2}:\\d{2})-(\\d{2}:\\d{2})");
        for (String part : timeStr.split("/")) {
            Matcher m = p.matcher(part.trim());
            if (m.find()) {
                Slot s = new Slot();
                s.day = m.group(1); s.start = m.group(2); s.end = m.group(3);
                out.add(s);
            }
        }
        return out;
    }

    private int toMin(String hhmm) {
        String[] p = hhmm.split(":");
        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
    }

    private String roleCn(String role) {
        return switch (role == null ? "" : role) {
            case "student" -> "学生";
            case "teacher" -> "教师";
            case "admin" -> "教务管理员";
            default -> "访客";
        };
    }

    private String statusCn(String s) {
        return switch (s == null ? "" : s) {
            case "approved" -> "已录取";
            case "pending" -> "待结算";
            case "waitlist" -> "候补中";
            case "dropped" -> "已退课";
            case "rejected" -> "已落选";
            default -> s;
        };
    }

    private String waitStatusCn(String s) {
        return switch (s == null ? "" : s) {
            case "waiting" -> "排队中";
            case "confirming" -> "已递补，待确认";
            case "confirmed" -> "已确认录取";
            case "timeout" -> "已超时";
            case "cancelled" -> "已取消";
            default -> s;
        };
    }

    private String phaseName(String p) {
        if (p == null) return "正常选课阶段";
        return switch (p) {
            case "batch" -> "补选阶段";
            case "reselect" -> "退改选阶段";
            case "regular" -> "正常选课阶段";
            case "closed" -> "已结束";
            default -> "正常选课阶段";
        };
    }

    @Autowired private org.springframework.context.ApplicationContext applicationContext;
}
