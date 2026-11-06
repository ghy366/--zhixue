package com.zhixue.config;

import com.zhixue.entity.GraduateTopic;
import com.zhixue.entity.User;
import com.zhixue.repository.GraduateTopicRepository;
import com.zhixue.repository.UserRepository;
import com.zhixue.util.PasswordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 毕设双选种子数据（幂等补齐，每次启动执行）：
 * <ul>
 *   <li>每个学院补足 10 名导师（工号 T2xxx，方向按学院学科池分配）；</li>
 *   <li>每位导师补足 10~15 个毕设课题——已有 ≥10 个的导师不动，不足的补到目标数
 *       （目标 = 10 + 导师序号 mod 6，即 10-15 区间）；</li>
 *   <li>课题标题按学院学科生成（基础题 × 技术前缀组合），全局唯一，与库内已有课题不冲突。</li>
 * </ul>
 * 学院名单与前端 utils/colleges.ts 保持一致。
 */
@Component
public class GraduateAdvisorSeedRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(GraduateAdvisorSeedRunner.class);

    @Autowired private UserRepository userRepo;
    @Autowired private GraduateTopicRepository topicRepo;

    private static final int TEACHERS_PER_COLLEGE = 10;
    private static final int MIN_TOPICS = 10, MAX_TOPICS = 15;

    private record CollegeSeed(String college, List<String> directions,
                               List<String> names, List<String> baseTitles, List<String> prefixes) {}

    private static final List<CollegeSeed> SEEDS = List.of(
        new CollegeSeed("计算机学院",
            List.of("人工智能", "系统与安全", "大数据", "图形与可视化"),
            List.of("陈慕青", "周子墨", "许明轩", "何静姝", "罗文昊", "高沐宸", "孙一诺", "唐嘉树", "冯若曦", "蒋明远", "韩雨泽", "崔梦琪"),
            List.of("校园二手交易平台的设计与实现", "基于协同过滤的课程推荐系统", "智能问答机器人的设计与实现",
                    "在线考试系统的设计与实现", "宿舍安全监测与预警系统", "图书馆座位预约系统", "校园失物招领平台",
                    "程序设计自动评测系统", "基于知识图谱的学科问答", "代码缺陷检测工具", "短视频推荐算法研究",
                    "校园能耗监控平台", "基于YOLOv8的口罩佩戴检测", "分布式文件同步系统", "智能排课算法研究与实现", "软件缺陷跟踪平台"),
            List.of("基于 Spring Boot 的", "基于微服务架构的", "基于深度学习的", "基于区块链的", "基于云原生的",
                    "基于大数据分析的", "面向移动端的", "基于边缘计算的", "基于大语言模型的")),
        new CollegeSeed("软件学院",
            List.of("软件工程", "前端工程", "测试与质量", "DevOps"),
            List.of("苏念安", "孟子川", "叶知秋", "程亦凡", "丁诗涵", "任浩然", "卢晓萌", "贾云帆", "潘俊驰", "倪若冰", "邹婉如", "钱思远"),
            List.of("低代码表单引擎的设计与实现", "组件库可视化搭建平台", "自动化接口测试平台", "持续集成流水线监控平台",
                    "前端性能分析与优化工具", "API 网关的设计与实现", "代码评审辅助工具", "项目进度管理平台",
                    "单元测试用例自动生成工具", "微服务链路追踪系统", "UI 自动化回归测试平台", "需求变更影响分析工具",
                    "团队协作看板系统", "日志聚合与分析平台", "软件项目工作量估算模型", "灰度发布管控平台",
                    "需求知识库检索系统", "特性开关管理平台", "接口契约测试工具", "研发效能度量平台"),
            List.of("基于 Vue3 的", "基于 React 的", "基于微服务的", "基于 Docker 的", "基于 Kubernetes 的",
                    "面向敏捷开发的", "基于静态分析的", "基于大模型的", "面向 DevOps 的",
                    "基于服务网格的", "基于事件驱动的", "基于领域驱动的", "基于低代码的", "基于混沌工程的", "面向云原生研发的")),
        new CollegeSeed("信息学院",
            List.of("网络安全", "物联网", "通信技术", "嵌入式"),
            List.of("赵启铭", "钱泽楷", "孙浩宇", "李慕涵", "周远航", "吴思彤", "郑柏舟", "王梓睿", "冯天翊", "陈曦月", "褚一鸣", "卫嘉澍"),
            List.of("校园网络流量分析与异常检测", "智能家居远程控制系统", "基于RFID的实验室门禁系统", "无线传感网数据采集平台",
                    "网站漏洞扫描器的设计与实现", "基于MQTT的环境监测系统", "恶意代码行为分析平台", "智能小车自主避障系统",
                    "隐私数据脱敏工具", "基于STM32的健康手环", "室内WiFi定位系统", "工控协议模糊测试工具",
                    "无人机图传链路优化", "区块链电子存证系统", "5G网络切片仿真", "入侵检测规则自动生成"),
            List.of("基于深度学习的", "基于区块链的", "基于MQTT的", "面向工业互联网的", "基于LoRa的",
                    "基于联邦学习的", "基于边缘计算的", "面向车联网的", "基于SDN的")),
        new CollegeSeed("数学学院",
            List.of("应用数学", "数据科学", "计算方法", "金融数学"),
            List.of("华清扬", "方启文", "吕明哲", "穆婉清", "尹鸿飞", "米书瑶", "祁沐阳", "季文彦", "伍思睿", "阎雨桐", "余泓静", "关云舟"),
            List.of("高校考试成绩分析与预测模型", "城市交通流量预测模型", "股票价格时间序列分析", "数学建模竞赛培训平台",
                    "蒙特卡洛方法在风险估值中的应用", "基于神经网络的房价预测", "排队论在食堂服务优化中的应用",
                    "图书借阅数据的关联分析", "传染病传播模型仿真", "最短路径算法在物流配送中的应用", "图像去噪的数值方法研究",
                    "大学生学习行为分析", "体育赛事数据可视化分析", "基于优化算法的课程表编排", "保险精算模型比较研究", "气象数据的统计建模与预测"),
            List.of("基于机器学习的", "基于深度学习的", "基于蒙特卡洛的", "基于遗传算法的", "基于贝叶斯方法的",
                    "面向金融数据的", "基于时间序列的", "基于群智能优化的", "面向大数据的")),
        new CollegeSeed("数字学院",
            List.of("数字媒体", "虚拟现实", "游戏开发", "数字孪生"),
            List.of("白皓宇", "莫雨薇", "温子谦", "贺志远", "庄文清", "舒亦辰", "晏紫苏", "盛望舒", "简知行", "连雨欣", "纪云舟", "凌慕晴"),
            List.of("校园三维地图漫游系统", "虚拟展厅的设计与实现", "增强现实名片识别应用", "校史馆VR导览系统",
                    "基于Unity的休闲游戏开发", "数字孪生校园建模", "全景视频直播平台", "手势交互体感游戏",
                    "虚拟仿真实验平台", "三维模型轻量化工具", "数字人播报系统", "AR图书馆导航应用",
                    "粒子特效编辑器", "影视后期自动化工具", "虚拟演唱会舞台设计", "体感交互装置创作"),
            List.of("基于Unity的", "基于Unreal的", "基于WebXR的", "基于三维重建的", "基于动作捕捉的",
                    "面向元宇宙的", "基于AIGC的", "基于全景视频的", "基于数字孪生的")),
        new CollegeSeed("外国语学院",
            List.of("翻译学", "应用语言学", "跨文化交际", "外语教育"),
            List.of("林晚晴", "欧子涵", "简梦瑶", "文静怡", "方语桐", "穆清扬", "雷雨欣", "齐悦然", "佟安歌", "陶知夏", "黎晓岚", "沈诗雨"),
            List.of("机器翻译译后编辑质量研究", "中国文化外宣翻译策略研究", "科幻小说汉译实践与报告", "英语口语智能测评应用设计",
                    "双语平行语料库建设", "影视剧字幕翻译规范性研究", "跨文化视角下的品牌出海研究", "学术英语写作错误分析",
                    "大学生外语学习焦虑研究", "术语一致性管理工具设计", "英语绘本与儿童语言习得研究", "旅游文本英译策略研究",
                    "日语敬语教学设计研究", "语料库驱动的词汇研究", "AI同声传译可用性研究", "商务谈判口译实践报告"),
            List.of("基于语料库的", "基于大语言模型的", "面向文旅融合的", "基于眼动追踪的", "基于机器翻译的",
                    "面向学术论文的", "基于语音识别的", "面向非遗外译的", "基于混合方法研究的")),
        new CollegeSeed("经济管理学院",
            List.of("金融科技", "市场营销", "供应链管理", "人力资源管理"),
            List.of("施雅雯", "张若楠", "黄启航", "穆雨欣", "贾思齐", "路云舒", "傅明轩", "桑郁宁", "解雨欣", "应采薇", "邹冰洁", "常思远"),
            List.of("大学生消费行为分析", "社区团购运营模式研究", "中小企业融资难问题研究", "直播电商营销策略研究",
                    "校园快递末端配送优化", "供应链金融风险评价", "网约车定价策略研究", "新能源汽车市场渗透预测",
                    "跨境电商选品策略研究", "员工流失预警模型", "移动支付使用意愿研究", "城市夜经济消费调研",
                    "ESG表现与企业价值关系研究", "生鲜农产品冷链物流优化", "短视频平台用户留存分析", "共享经济平台用工模式研究"),
            List.of("基于问卷调查的", "基于面板数据的", "基于结构方程模型的", "面向Z世代的", "基于机器学习的",
                    "基于案例研究的", "面向中小城市的", "基于博弈论的", "基于文本挖掘的")),
        new CollegeSeed("艺术设计学院",
            List.of("视觉传达", "环境设计", "产品设计", "交互设计"),
            List.of("顾清池", "花沐言", "方雨眠", "薛暮橙", "柏一帆", "桂雨桐", "蓝沐晨", "荆一诺", "池晚吟", "涂雅雯", "易书棋", "季蓝湾"),
            List.of("校园文创产品设计", "非遗主题视觉设计", "城市形象海报设计", "老旧社区公共空间改造设计",
                    "适老化App界面设计", "品牌全案设计", "儿童绘本插画创作", "文旅IP形象设计",
                    "可变化包装设计", "民宿空间设计", "信息可视化设计", "动态海报设计",
                    "博物馆展陈设计", "残障人士辅助产品设计", "潮玩产品设计", "校园导视系统设计"),
            List.of("面向地域文化的", "基于可持续理念的", "面向Z世代的", "基于参数化设计的", "面向乡村振兴的",
                    "基于用户旅程的", "基于服务设计的", "面向城市更新的", "基于AIGC辅助的")),
        new CollegeSeed("机械工程学院",
            List.of("智能制造", "机器人", "车辆工程", "机械设计"),
            List.of("常凯风", "侯振宇", "白宇轩", "龙浩然", "闵振鹏", "卿云帆", "臧天佑", "井雨泽", "伏子昂", "岑致远", "詹慕辰", "雷正阳"),
            List.of("智能分拣机械臂设计", "AGV小车路径规划", "3D打印结构件优化", "自行车变速器改进设计",
                    "农产品分选装置设计", "机械故障声纹诊断", "爬壁机器人结构设计", "新能源底盘布置仿真",
                    "数控加工工艺优化", "管道检测机器人", "差速器设计与仿真", "智能温室控制机构",
                    "快速换模装置设计", "收获机械割台优化", "外骨骼助力结构设计", "小型播种机具设计"),
            List.of("基于SolidWorks的", "基于MATLAB仿真的", "基于机器视觉的", "面向农业场景的", "基于有限元分析的",
                    "基于PLC控制的", "面向柔性制造的", "基于数字孪生的", "面向绿色制造的")),
        new CollegeSeed("通识教育中心",
            List.of("美育通识", "科学素养", "劳动教育", "心理健康"),
            List.of("关雅澜", "龚雨荷", "贺清越", "万晓慧", "闻嘉禾", "房文静", "缪书言", "盛清欢", "屠维桢", "易安晴", "凌婉婷", "覃思源"),
            List.of("大学生美育课程体系设计", "校园音乐节策划与实践研究", "通识课程学习动机研究", "大学生心理健康测评平台设计",
                    "劳动教育实践基地设计", "科学阅读推广模式研究", "通识选修课满意度分析", "校园文化艺术节运营研究",
                    "朋辈心理辅导模式研究", "通识写作课程改革研究", "博雅教育国际比较研究", "美育浸润行动个案研究",
                    "大学生时间管理训练方案", "剧场式教学在通识课的应用", "通识课程评教数据分析", "校园公众科学项目设计"),
            List.of("基于行动研究的", "基于问卷调查的", "面向新生群体的", "基于混合式教学的", "面向五育融合的",
                    "基于个案研究的", "基于叙事研究的", "面向应用型本科的", "基于大数据分析的")),
        new CollegeSeed("艺术学院",
            List.of("音乐表演", "美术学", "舞蹈", "艺术理论"),
            List.of("云若溪", "迟暮雨", "池沐橙", "阮清弦", "柳映月", "柯梦琪", "童欣然", "屠静姝", "饶曼妮", "戚雨眠", "栗丹妮", "颜书韵"),
            List.of("地方民歌的采集与研究", "合唱指挥艺术实践报告", "数字插画创作实践", "油画本土化语言研究",
                    "舞台灯光设计实践", "钢琴作品演奏分析", "民乐重编配实践", "陶瓷艺术创作报告",
                    "舞蹈编导作品实践", "艺术概论课程教改研究", "水彩风景写生与创作", "音乐治疗在校园的应用",
                    "传统壁画临摹与研究", "独唱音乐会设计与实践", "公共艺术装置创作", "短视频艺术表达研究"),
            List.of("面向地方非遗的", "基于跨媒介的", "基于田野调查的", "面向校园美育的", "基于数字技术的",
                    "基于比较研究的", "面向乡村振兴的", "基于个案分析的", "基于多学科交叉的")),
        new CollegeSeed("商学院",
            List.of("工商管理", "电子商务", "会计学", "旅游管理"),
            List.of("喻之航", "傅诗淇", "饶曼晴", "岑怡萱", "麦乐瑶", "简亦航", "缪可言", "佟梦圆", "龙芷晴", "边书瑶", "税雨晨", "池向阳"),
            List.of("校园咖啡店运营方案设计", "民宿经营模式研究", "小微企业内控案例研究", "直播带货财务分析",
                    "景区游客满意度研究", "跨境电商物流模式比较", "连锁便利店选址分析", "预制菜市场竞争分析",
                    "企业并购案例财务分析", "研学旅行产品设计", "社区生鲜店运营诊断", "酒店收益管理研究",
                    "大学生创业项目路演分析", "电子发票应用调研", "会展经济带动效应研究", "县域电商发展路径研究"),
            List.of("基于财务报表的", "基于田野调查的", "面向县域经济的", "基于SWOT分析的", "基于平衡计分卡的",
                    "基于SPSS实证的", "面向银发经济的", "基于商业画布的", "基于对标研究的"))
    );

    @Override
    public void run(String... args) {
        try {
            // 清理早期组合器产生的「双重前缀」标题（如：基于大语言模型的基于YOLOv8的…），
            // 删除后由下方补足逻辑以规范标题重建（幂等：第二次启动无匹配项）
            List<GraduateTopic> bad = topicRepo.findAll().stream()
                    .filter(t -> t.getTitle().matches("^(基于|面向)[^的]+的(基于|面向).+"))
                    .toList();
            if (!bad.isEmpty()) {
                topicRepo.deleteAll(bad);
                log.info("清理双重前缀课题 {} 个", bad.size());
            }

            List<User> teachers = userRepo.findByRole("teacher");
            Set<String> usedNos = userRepo.findAll().stream().map(User::getNo).collect(Collectors.toSet());
            Set<String> usedNames = teachers.stream().map(User::getName).collect(Collectors.toSet());
            Set<String> existingTitles = topicRepo.findAll().stream()
                    .map(t -> t.getTitle().trim()).collect(Collectors.toSet());
            Map<Long, Long> topicCount = topicRepo.findAll().stream()
                    .collect(Collectors.groupingBy(GraduateTopic::getTeacherId, Collectors.counting()));

            int addedTeachers = 0, addedTopics = 0;
            for (CollegeSeed seed : SEEDS) {
                List<User> inCollege = teachers.stream()
                        .filter(t -> seed.college().equals(t.getCollege()))
                        .sorted(Comparator.comparing(User::getId))
                        .collect(Collectors.toList());

                // ---- 补足导师数量 ----
                int nextName = 0, seq = 201;
                while (inCollege.size() < TEACHERS_PER_COLLEGE && nextName < seed.names().size()) {
                    String name = seed.names().get(nextName++);
                    if (usedNames.contains(name)) continue;
                    String no = "T" + (seq++);
                    while (usedNos.contains(no)) no = "T" + (seq++);
                    User t = new User();
                    t.setNo(no);
                    t.setPassword(new PasswordUtil().encode("123456"));
                    t.setName(name);
                    t.setRole("teacher");
                    t.setCollege(seed.college());
                    t.setMajor(seed.directions().get(inCollege.size() % seed.directions().size()));
                    t.setAvatar("👨‍🏫");
                    try {
                        userRepo.save(t);
                        usedNos.add(no); usedNames.add(name);
                        inCollege.add(t);
                        addedTeachers++;
                    } catch (DataIntegrityViolationException ignore) { }
                }

                // ---- 每位导师补足课题（10-15 个） ----
                List<String> combos = new ArrayList<>(seed.baseTitles());
                for (String p : seed.prefixes()) {
                    for (String b : seed.baseTitles()) {
                        // 基础题自带「基于/面向」前缀时剥离，避免组合出双重前缀标题
                        String bare = b.replaceFirst("^(基于|面向)[^的]+的", "");
                        combos.add(p + bare);
                    }
                }
                Set<String> usedTitles = new HashSet<>(existingTitles);

                for (int i = 0; i < inCollege.size(); i++) {
                    User t = inCollege.get(i);
                    long existing = topicCount.getOrDefault(t.getId(), 0L);
                    if (existing >= MIN_TOPICS) continue;
                    int target = Math.min(MAX_TOPICS, MIN_TOPICS + (i % (MAX_TOPICS - MIN_TOPICS + 1)));
                    int add = (int) Math.max(0, target - existing);
                    int created = 0, probe = 0, k = 0;
                    while (created < add && probe < combos.size() * 3) {
                        String title = combos.get((i * 17 + k * 13) % combos.size());
                        k++; probe++;
                        if (!usedTitles.add(title)) continue;
                        GraduateTopic tp = new GraduateTopic();
                        tp.setTeacherId(t.getId());
                        tp.setTitle(title);
                        tp.setDirection(seed.directions().get((i + k) % seed.directions().size()));
                        tp.setDescription("要求完成文献调研、需求分析、方案设计与实现（或研究论证），工作量饱满，"
                                + "按学院规范提交毕业论文与全部成果材料，适合" + tp.getDirection() + "方向学生选做。");
                        tp.setStatus("open");
                        tp.setCreatedAt(LocalDateTime.now().minusDays((i * 3L + k) % 45));
                        try {
                            topicRepo.save(tp);
                            created++;
                        } catch (DataIntegrityViolationException e) {
                            usedTitles.remove(title);
                        }
                    }
                    addedTopics += created;
                    topicCount.put(t.getId(), existing + created);
                }
            }
            if (addedTeachers > 0 || addedTopics > 0) {
                log.info("毕设双选种子数据：新增导师 {} 名、新增课题 {} 个", addedTeachers, addedTopics);
            }
        } catch (Exception e) {
            log.warn("毕设双选种子数据生成失败（不影响系统启动）：{}", e.getMessage(), e);
        }
    }
}
