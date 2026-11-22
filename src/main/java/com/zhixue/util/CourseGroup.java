package com.zhixue.util;

/**
 * 课程类别 → 毕业学分大类的统一映射。
 * <p>
 * 选课结算、选课入口和毕业审核必须使用同一套映射，
 * 否则结算时“允许报的名”到毕业审核时会被判定为“多修”。
 */
public final class CourseGroup {

    /** 毕业学分大类：对齐厦门华厦学院——必修 / 公共选修 / 专业选修 三类 */
    public static final String[] KEYS = {"必修", "公共选修", "专业选修"};

    /** 公共选修（华厦硬指标 8 学分）用 gradReqGeneral；专业选修用 gradReqElective */
    private CourseGroup() {}

    /**
     * 课程类别 → 毕业大类。
     * 厦门华厦学院：公共选修（通识选修，修满 8 学分）与专业选修分列，互不抵扣。
     */
    public static String groupOf(String category) {
        if (category == null) return "专业选修";
        if (category.contains("必修")) return "必修";
        if (category.contains("公共选修") || category.contains("通识")) return "公共选修";
        return "专业选修";
    }

    /**
     * 是否系统自动安排（学生不可选/退/换，不占选课门数与积分）。
     * 对齐厦门华厦学院：必修课与专业选修课都由教务按培养方案排入课表，
     * 学生真正自选的只有公共选修课（通识选修）。
     */
    public static boolean isRequired(String category) {
        if (category == null) return false;
        return category.contains("必修") || category.contains("专业选修");
    }

    /**
     * 「历史学期还原课」标记：course_code 以 PE- 开头的课程，是依据华厦真实培养方案
     * 补建的专业选修课（历史已修、带成绩），用于还原各学期课表密度。
     * 它们显示类别为「专业选修」，但不再参与自动排课与毕业学分认定，
     * 避免 120 门历史课被排给全体在校生、或把专业选修学分撑到超额。
     */
    public static boolean isHistorical(String courseCode) {
        return courseCode != null && courseCode.startsWith("PE-");
    }

    /** 是否计入毕业学分认定 / 自动排课：历史还原课不计入 */
    public static boolean countsForGraduation(String courseCode) {
        return !isHistorical(courseCode);
    }
}
