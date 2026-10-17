package com.zhixue.controller;

import com.zhixue.dto.ApiResponse;
import com.zhixue.entity.Course;
import com.zhixue.entity.SystemConfig;
import com.zhixue.repository.CourseRepository;
import com.zhixue.repository.SystemConfigRepository;
import com.zhixue.service.AiService;
import com.zhixue.service.CourseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** AI 智能体对话：免登录公开功能，代理转发到大模型（API Key 只存在后端）。
 *  后端在调用模型前动态注入「业务数据上下文」（选课状态 + 课程目录），
 *  使 AI 能基于真实数据解答课程与选课规则问题，无需接入新 API。 */
@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    @Autowired private AiService aiService;
    @Autowired private CourseRepository courseRepo;
    @Autowired private SystemConfigRepository configRepo;
    @Autowired private CourseService courseService;

    private static final String BASE_PROMPT = ""
            + "你是「智学选课」大学生选课平台的内置 AI 助手。请用简体中文、友好简洁的口吻回答。"
            + "你可以帮助用户：解答选课流程与规则、介绍平台课程、推荐学习计划、解释课程难点、"
            + "提供期末复习与时间管理建议、解答毕业进度相关问题。"
            + "注意：平台内的具体个人数据（如某学生已选课程、成绩、选课结果）请引导用户到对应功能页面查看，"
            + "你本身无法直接查询个人账号数据；但平台公开的课程目录与选课规则以下方【业务数据】为准。";

    // AI 助手为免费公开功能，无需登录即可使用
    @PostMapping("/chat")
    public ApiResponse<Map<String, Object>> chat(@RequestBody Map<String, Object> body) {
        String message = body.get("message") == null ? "" : body.get("message").toString().trim();
        if (message.isEmpty()) return ApiResponse.fail("消息不能为空");
        if (message.length() > 2000) return ApiResponse.fail("消息过长，请分条发送");

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", buildSystemPrompt()));

        // 前端带来的历史对话（最多保留最近 16 条，控制 token 消耗）
        Object historyObj = body.get("history");
        if (historyObj instanceof List) {
            for (Object o : (List<?>) historyObj) {
                if (!(o instanceof Map)) continue;
                String role = String.valueOf(((Map<?, ?>) o).get("role"));
                String content = String.valueOf(((Map<?, ?>) o).get("content"));
                if (("user".equals(role) || "assistant".equals(role))
                        && content != null && !content.isBlank() && !"null".equals(content)) {
                    messages.add(Map.of(role, content));
                }
            }
            while (messages.size() > 17) { // system + 16 条历史
                messages.remove(1);
            }
        }
        messages.add(Map.of("role", "user", "content", message));

        try {
            String reply = aiService.chat(messages);
            Map<String, Object> data = new HashMap<>();
            data.put("reply", reply);
            return ApiResponse.ok(data);
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    /** 动态拼接「业务数据上下文」：选课状态 + 课程目录，让 AI 能基于真实数据答疑 */
    private String buildSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append(BASE_PROMPT);

        SystemConfig cfg = configRepo.findById(1L).orElse(null);
        if (cfg != null) {
            sb.append("\n\n【当前选课状态】\n");
            sb.append("- 当前第 ").append(cfg.getCurrentRound()).append(" 轮，")
              .append(Boolean.TRUE.equals(cfg.getRoundOpen()) ? "选课进行中" : "本轮暂未开放")
              .append("；阶段：").append(phaseName(cfg.getCurrentPhase())).append("。\n");
            sb.append("- 每学期学分上限 ").append(nullTo(cfg.getMaxCreditPerSemester(), "未限制")).append(" 分。\n");
            sb.append("- 毕业学分要求：必修≥").append(cfg.getGradReqRequired())
              .append("、选修≥").append(cfg.getGradReqElective())
              .append("、公共≥").append(cfg.getGradReqGeneral())
              .append("、实践≥").append(cfg.getGradReqPractice()).append("。\n");
            if (Boolean.TRUE.equals(cfg.getAutoSchedule())) {
                sb.append("- 已开启按时间自动开放 / 结束选课。\n");
            }
        }

        List<Course> courses = courseRepo.findAll();
        if (!courses.isEmpty()) {
            sb.append("\n【课程目录（共 ").append(courses.size()).append(" 门，列示前 40 门）】\n");
            int n = 0;
            for (Course c : courses) {
                if (n >= 40) { sb.append("…（更多课程请在「选课」页面查看）\n"); break; }
                n++;
                sb.append(n).append(". ").append(nullTo(c.getName(), "未命名"))
                  .append(" ｜ ").append(nullTo(c.getCategory(), "未分类"))
                  .append(" ｜ ").append(nullTo(c.getCredit(), "0")).append("学分")
                  .append(" ｜ 教师：").append(courseService.teacherName(c.getTeacherId()))
                  .append(" ｜ 时间：").append(nullTo(c.getTime(), "待定"))
                  .append(" ｜ 校区：").append(nullTo(c.getCampus(), "主校区")).append("\n");
            }
        }
        sb.append("\n请优先基于以上【业务数据】回答学生关于课程、选课规则、轮次与毕业要求的问题；")
          .append("若涉及某学生个人已选 / 成绩等隐私数据，请引导其到对应页面查看。")
          .append("回答控制在 300 字以内，多用列表。");
        return sb.toString();
    }

    private static String phaseName(String p) {
        if (p == null) return "正常选课阶段";
        return switch (p) {
            case "batch" -> "补选阶段";
            case "reselect" -> "退改选阶段";
            default -> "正常选课阶段";
        };
    }

    private static String nullTo(Object v, String dft) {
        return v == null ? dft : String.valueOf(v);
    }
}
