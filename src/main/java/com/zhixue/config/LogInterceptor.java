package com.zhixue.config;

import com.zhixue.entity.User;
import com.zhixue.repository.UserRepository;
import com.zhixue.service.LogService;
import com.zhixue.util.SessionStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class LogInterceptor implements HandlerInterceptor {

    @Autowired private LogService logService;
    @Autowired private UserRepository userRepo;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/")) return;

        String method = request.getMethod();
        String token = request.getHeader("Authorization");
        Long userId = null;
        String userName = "匿名";

        if (token != null && token.startsWith("Bearer ")) {
            try {
                userId = SessionStore.getUserId(token.substring(7));
                if (userId != null) {
                    User u = userRepo.findById(userId).orElse(null);
                    if (u != null) userName = u.getName();
                }
            } catch (Exception ignored) {}
        }

        // 解析操作类型
        String type = "其他";
        if (uri.contains("/auth/")) type = "用户管理";
        else if (uri.contains("/courses")) type = "课程管理";
        else if (uri.contains("/enrollments")) type = "选课管理";
        else if (uri.contains("/grades")) type = "成绩管理";
        else if (uri.contains("/semesters")) type = "学期管理";
        else if (uri.contains("/classrooms")) type = "资源管理";
        else if (uri.contains("/admin")) type = "用户管理";
        else if (uri.contains("/system")) type = "系统管理";
        else if (uri.contains("/logs")) type = "系统管理";

        String operation = method + " " + uri;
        String status = ex == null && response.getStatus() < 400 ? "成功" : "失败";
        String ip = request.getRemoteAddr();

        try {
            logService.log(userId, userName, operation, type, status, ip);
        } catch (Exception ignored) {}
    }
}
