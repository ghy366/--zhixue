package com.zhixue.util;

/**
 * 课程类别 → 毕业学分大类的统一映射。
 * <p>
 * 选课结算、选课入口和毕业审核必须使用同一套映射，
 * 否则结算时“允许报的名”到毕业审核时会被判定为“多修”。
 */
public final class CourseGroup {

    /** 毕业学分大类的固定顺序 */
    public static final String[] KEYS = {"必修", "选修", "通识", "实践"};

    /** 选修 / 通识 / 实践三类上限对应的配置字段（必修类由教学计划自动保证） */
    private CourseGroup() {}

    public static String groupOf(String category) {
        if (category == null) return "选修";
        if (category.contains("必修")) return "必修";
        if (category.contains("通识")) return "通识";
        if (category.contains("实践")) return "实践";
        return "选修";
    }

    /**
     * 必修类判断（公共必修 / 专业必修）。
     * 门数上限（maxCoursesPerSemester）只统计非必修课，所有入口统一走这里，勿另写判断。
     */
    public static boolean isRequired(String category) {
        return "必修".equals(groupOf(category));
    }
}
