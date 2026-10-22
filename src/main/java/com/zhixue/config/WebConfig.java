package com.zhixue.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired private LogInterceptor logInterceptor;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
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
                "/approve", "/approve/**",
                "/student-list", "/student-list/**",
                "/grade-mgr", "/grade-mgr/**",
                "/reviews", "/reviews/**",
                "/advise", "/advise/**",
                "/rounds", "/rounds/**",
                "/semesters", "/semesters/**",
                "/classrooms", "/classrooms/**",
                "/course-mgr", "/course-mgr/**",
                "/user-mgr", "/user-mgr/**",
                "/announce", "/announce/**",
                "/stats", "/stats/**",
                "/advise-mgr", "/advise-mgr/**",
                "/logs", "/logs/**",
                "/profile", "/profile/**",
        };
        for (String p : spaPaths) {
            registry.addViewController(p).setViewName("forward:/index.html");
        }
    }
}
