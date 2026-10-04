package com.zhixue.controller;

import com.zhixue.dto.ApiResponse;
import com.zhixue.service.DataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** CSV 批量导入 / 导出 */
@RestController
@RequestMapping("/api/data")
public class DataController {

    @Autowired private DataService dataService;

    private ResponseEntity<byte[]> csv(String content, String filename) {
        byte[] bytes = ("\uFEFF" + content).getBytes(StandardCharsets.UTF_8);   // BOM 便于 Excel 识别 UTF-8
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        h.set(HttpHeaders.CONTENT_TYPE, "text/csv;charset=UTF-8");
        return new ResponseEntity<>(bytes, h, org.springframework.http.HttpStatus.OK);
    }

    @GetMapping("/export/students")
    public ResponseEntity<byte[]> exportStudents() {
        return csv(dataService.exportStudentsCsv(), "students.csv");
    }

    @GetMapping("/export/courses")
    public ResponseEntity<byte[]> exportCourses() {
        return csv(dataService.exportCoursesCsv(), "courses.csv");
    }

    @GetMapping("/export/grades")
    public ResponseEntity<byte[]> exportGrades() {
        return csv(dataService.exportGradesCsv(), "grades.csv");
    }

    @GetMapping("/export/course/{courseId}/enrollments")
    public ResponseEntity<byte[]> exportCourseEnrollments(@PathVariable Long courseId) {
        return csv(dataService.exportCourseEnrollmentsCsv(courseId), "course-" + courseId + "-enrollments.csv");
    }

    @GetMapping("/template/students")
    public ResponseEntity<byte[]> templateStudents() {
        return csv(dataService.studentTemplateCsv(), "students-template.csv");
    }

    @GetMapping("/template/courses")
    public ResponseEntity<byte[]> templateCourses() {
        return csv(dataService.courseTemplateCsv(), "courses-template.csv");
    }

    @PostMapping("/import/students")
    public ApiResponse<Map<String, Object>> importStudents(@RequestParam("file") MultipartFile file) throws IOException {
        String text = new String(file.getBytes(), StandardCharsets.UTF_8);
        Map<String, Object> r = dataService.importStudentsCsv(text);
        return ApiResponse.ok((String) r.get("msg"), r);
    }

    @PostMapping("/import/courses")
    public ApiResponse<Map<String, Object>> importCourses(@RequestParam("file") MultipartFile file) throws IOException {
        String text = new String(file.getBytes(), StandardCharsets.UTF_8);
        Map<String, Object> r = dataService.importCoursesCsv(text);
        return ApiResponse.ok((String) r.get("msg"), r);
    }
}
