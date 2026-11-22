package com.zhixue.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired private LogInterceptor logInterceptor;
    @Autowired private RoleInterceptor roleInterceptor;

    @Value("${zhixue.chat.image-path:E:/ai文件 图片缓存/chat-images}")
    private String chatImageDir;

    @Value("${zhixue.chat.file-path:E:/ai文件 图片缓存/chat-files}")
    private String chatFileDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 聊天图片/附件直链：文件名含时间戳+UUID 不可猜测，且 <img>/<a> 标签无法携带
        // Authorization 头，故放行到 /api 之外免鉴权访问（RoleInterceptor 只拦 /api/**）
        registry.addResourceHandler("/files/chat/**")
                .addResourceLocations(
                        "file:" + Paths.get(chatImageDir).toAbsolutePath().normalize() + "/",
                        "file:" + Paths.get(chatFileDir).toAbsolutePath().normalize() + "/");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 统一鉴权：先于操作日志执行（未授权请求不产生业务日志）
        registry.addInterceptor(roleInterceptor)
                .addPathPatterns("/api/**");
        registry.addInterceptor(logInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/logs/**");
    }

    // Vue SPA history 模式 fallback：所有未命中的路由转到 index.html
    // 注意：/api/** 会被 Controller 处理；静态资源（js/css/png 等）Spring Boot 按扩展名自动匹配
    @Override
    public void addViewControllers(org.springframework.web.servlet.config.annotation.ViewControllerRegistry registry) {
        // 这些 Vue 路由路径（及所有子路径）需要 forward 到 index.html
        // 使用 /** 会和 static 资源冲突，所以只列已知路由前缀
        String[] spaPaths = {
                "/login", "/login/**",
                "/dashboard", "/dashboard/**",
                // 角色分端首页：登录后跳转的 URL，刷新时必须回退到 index.html
                "/student/dashboard", "/teacher/dashboard", "/admin/dashboard",
                "/courses", "/courses/**",
                "/intents", "/intents/**",
                "/my-enrollments", "/my-enrollments/**",
                "/waitlist", "/waitlist/**",
                "/schedule", "/schedule/**",
                "/grades", "/grades/**",
                "/graduation", "/graduation/**",
                "/advisor-apply", "/advisor-apply/**",
                "/workspace", "/workspace/**",
                "/chat", "/chat/**",
                "/teach-courses", "/teach-courses/**",
                "/student-list", "/student-list/**",
                "/drop-requests", "/drop-requests/**",
                "/grade-mgr", "/grade-mgr/**",
                "/reviews", "/reviews/**",
                "/advise", "/advise/**",
                "/rounds", "/rounds/**",
                "/semesters", "/semesters/**",
                "/classrooms", "/classrooms/**",
                "/course-mgr", "/course-mgr/**",
                "/user-mgr", "/user-mgr/**",
                "/announce", "/announce/**",
                "/announcements", "/announcements/**",
                "/stats", "/stats/**",
                "/makeup-mgr", "/makeup-mgr/**",
                "/retake-mgr", "/retake-mgr/**",
                "/advise-mgr", "/advise-mgr/**",
                "/logs", "/logs/**",
                "/profile", "/profile/**",
                "/suggestions", "/suggestions/**",
                "/graduation-audit", "/graduation-audit/**",
                "/training-plans", "/training-plans/**",
                "/ai-channels", "/ai-channels/**",
        };
        for (String p : spaPaths) {
            registry.addViewController(p).setViewName("forward:/index.html");
        }
    }
}
