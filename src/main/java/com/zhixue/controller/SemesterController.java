package com.zhixue.controller;

import com.zhixue.dto.ApiResponse;
import com.zhixue.entity.Semester;
import com.zhixue.service.SemesterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/semesters")
public class SemesterController {

    @Autowired private SemesterService semesterService;

    @GetMapping
    public ApiResponse<List<Semester>> list() {
        List<Semester> list = semesterService.findAll();
        list.forEach(s -> s.setStatus(semesterService.computeStatus(s)));
        return ApiResponse.ok(list);
    }
    @GetMapping("/current")
    public ApiResponse<Semester> current() {
        Semester cur = semesterService.findCurrent();
        if (cur == null) return ApiResponse.fail("没有设置学期");
        cur.setStatus(semesterService.computeStatus(cur));
        return ApiResponse.ok("ok", cur);
    }


    @PostMapping
    public ApiResponse<Semester> save(@RequestBody Semester s) {
        return ApiResponse.ok("保存成功", semesterService.save(s));
    }

    /** 编辑学期（此前缺失该接口，管理端「学期管理 → 编辑」一直保存失败） */
    @PutMapping("/{id}")
    public ApiResponse<Semester> update(@PathVariable Long id, @RequestBody Semester s) {
        if (!semesterService.findAll().stream().anyMatch(x -> x.getId().equals(id))) {
            return ApiResponse.fail("学期不存在");
        }
        s.setId(id);
        return ApiResponse.ok("更新成功", semesterService.save(s));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        semesterService.delete(id);
        return ApiResponse.ok("删除成功", null);
    }
}
