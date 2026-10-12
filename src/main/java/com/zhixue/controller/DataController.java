package com.zhixue.controller;

import com.zhixue.dto.ApiResponse;
import com.zhixue.service.DataService;
import com.zhixue.service.ExcelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** CSV 批量导入 / 导出 */
@RestController
@RequestMapping("/api/data")
public class DataController {

    @Autowired private DataService dataService;
    @Autowired private ExcelService excelService;

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String XLSX_EXT = ".xlsx";

    private ResponseEntity<byte[]> xlsx(byte[] bytes, String filename) {
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + XLSX_EXT + "\"");
        h.set(HttpHeaders.CONTENT_TYPE, XLSX);
        return new ResponseEntity<>(bytes, h, org.springframework.http.HttpStatus.OK);
    }

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

    // ---- 真正的 Excel(.xlsx) 导出 ----
    @GetMapping("/export/students/xlsx")
    public ResponseEntity<byte[]> exportStudentsXlsx() {
        return xlsx(excelService.toXlsx("学生名单", dataService.studentsRows()), "students");
    }

    @GetMapping("/export/courses/xlsx")
    public ResponseEntity<byte[]> exportCoursesXlsx() {
        return xlsx(excelService.toXlsx("课程列表", dataService.coursesRows()), "courses");
    }

    @GetMapping("/export/grades/xlsx")
    public ResponseEntity<byte[]> exportGradesXlsx() {
        return xlsx(excelService.toXlsx("成绩单", dataService.gradesRows()), "grades");
    }

    @GetMapping("/export/course/{courseId}/enrollments/xlsx")
    public ResponseEntity<byte[]> exportCourseEnrollmentsXlsx(@PathVariable Long courseId) {
        return xlsx(excelService.toXlsx("选课名单", dataService.courseEnrollmentsRows(courseId)), "course-" + courseId + "-enrollments");
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
