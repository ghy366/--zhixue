package com.zhixue.controller;

import com.zhixue.dto.ApiResponse;
import com.zhixue.service.AiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** AI 智能体对话：需登录后使用，代理转发到大模型（API Key 只存在后端） */
@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    @Autowired private AiService aiService;

    private static final String SYSTEM_PROMPT = ""
            + "你是「智学选课」大学生选课平台的内置 AI 助手。请用简体中文、友好简洁的口吻回答。"
            + "你可以帮助用户：解答选课流程与规则、推荐学习计划、解释课程内容难点、"
            + "提供期末复习与时间管理建议、解答毕业进度相关问题。"
            + "注意：平台内的具体数据（如课程余量、成绩、选课结果）请引导用户到对应功能页面查看，"
            + "你本身无法直接查询数据库。回答控制在 300 字以内，适当使用列表让内容清晰。";

    // AI 助手为免费公开功能，无需登录即可使用，避免会话过期（服务重启）导致无法对话
    @PostMapping("/chat")
    public ApiResponse<Map<String, Object>> chat(@RequestBody Map<String, Object> body) {
        String message = body.get("message") == null ? "" : body.get("message").toString().trim();
        if (message.isEmpty()) return ApiResponse.fail("消息不能为空");
        if (message.length() > 2000) return ApiResponse.fail("消息过长，请分条发送");

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));

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
}
