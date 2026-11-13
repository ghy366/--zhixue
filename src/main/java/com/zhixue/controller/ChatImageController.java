package com.zhixue.controller;

import com.zhixue.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

/**
 * AI 助手聊天图片上传：落盘到 zhixue.chat.image-path（E:/ai文件 图片缓存/chat-images），
 * 消息里只保存 /files/chat/xxx 直链 URL，避免 base64 撑爆 MySQL。
 * 访问鉴权由 WebConfig 的 /files/chat/** 静态映射承担（文件名不可猜测）。
 */
@RestController
@RequestMapping("/api/ai")
public class ChatImageController {

    @Value("${zhixue.chat.image-path:E:/ai文件 图片缓存/chat-images}")
    private String imageDir;

    /** 单图上限：前端已压缩（最长边 1024 / JPEG 0.82），正常远小于此 */
    private static final long MAX_SIZE = 5 * 1024 * 1024;

    @PostMapping("/images")
    public ApiResponse<?> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) return ApiResponse.fail("请选择图片");
        String type = file.getContentType();
        if (type == null || !type.startsWith("image/")) return ApiResponse.fail("仅支持图片文件");
        if (file.getSize() > MAX_SIZE) return ApiResponse.fail("图片不能超过 5MB");
        try {
            String ext = switch (type) {
                case "image/png" -> ".png";
                case "image/webp" -> ".webp";
                case "image/gif" -> ".gif";
                default -> ".jpg";
            };
            String name = "img_" + System.currentTimeMillis() + "_"
                    + UUID.randomUUID().toString().substring(0, 8) + ext;
            Path dir = Paths.get(imageDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            // transferTo 相对路径会落到上传临时目录，这里确保绝对路径
            file.transferTo(dir.resolve(name).toFile());
            return ApiResponse.ok(Map.of("url", "/files/chat/" + name));
        } catch (Exception e) {
            return ApiResponse.fail("图片保存失败：" + e.getMessage());
        }
    }
}
