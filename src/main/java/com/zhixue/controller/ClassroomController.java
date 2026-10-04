package com.zhixue.controller;

import com.zhixue.dto.ApiResponse;
import com.zhixue.entity.Classroom;
import com.zhixue.service.ClassroomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/classrooms")
public class ClassroomController {

    @Autowired private ClassroomService classroomService;

    @GetMapping
    public ApiResponse<List<Classroom>> list() {
        return ApiResponse.ok(classroomService.findAll());
    }

    @PostMapping
    public ApiResponse<Classroom> save(@RequestBody Classroom c) {
        return ApiResponse.ok("保存成功", classroomService.save(c));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        classroomService.delete(id);
        return ApiResponse.ok("删除成功", null);
    }
}
