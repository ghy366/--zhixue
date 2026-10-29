# 智学选课平台

智学选课是一个面向高校场景的全栈选课演示系统：后端以 Spring Boot 提供 REST API，前端为 Vue 3 单页应用，构建产物由 Spring Boot 直接托管，开发时通常只需启动一个服务，默认访问 `http://localhost:8080`。

系统围绕"三轮录取机制"构建：第 1、2 轮为积分选课（截止后统一按积分排序录取），第 3 轮为先到先得补选，并配套候补队列、按年级错峰开放、学分封顶、毕业进度跟踪、毕设导师双向选择与工作台、AI 选课助手等完整教学场景功能。

## 技术栈

- **后端**：Java 17、Spring Boot 3.3.5、Spring Web、Spring Data JPA、Bean Validation、Maven
- **数据库**：MySQL 8（`application.yml` 默认启用 `mysql` profile）；仓库同时包含 H2 与 MySQL JDBC 驱动
- **前端**：Vue 3、TypeScript、Vite、Pinia、Vue Router、Element Plus（源码位于 `frontend/`）
- **其他**：Apache POI（Excel 导入导出）、大模型兼容接口（AI 助手）

> `src/main/resources/static/` 保留的是旧版原生 HTML/CSS/JS 前端，仅作对照；当前默认服务的是 Vue 构建产物 `src/main/resources/static-vue/`（由 `application.yml` 的 `spring.web.resources.static-locations` 指定）。

## 项目结构

```text
frontend/                            # Vue 3 前端源码（npm run build 输出 static-vue）
├── src/views/                       # 32 个业务页面（学生/教师/管理员）
├── src/components/                  # 布局、键帽侧边栏、透明顶栏等
├── src/stores/、src/router/、src/api/
└── src/styles/original.css          # 全局样式（新拟物键帽菜单、透明顶栏等）

src/main/java/com/zhixue/
├── config/      # Spring MVC、请求日志、异常处理、启动数据初始化、自动结算任务
├── controller/  # REST API 控制器
├── dto/         # 统一响应结构 ApiResponse
├── entity/      # JPA 实体（含错峰窗口 StaggerWindow）
├── repository/  # Spring Data JPA 数据访问接口
├── service/     # 业务逻辑（选课结算、候补、错峰、毕业要求等）
└── util/        # 会话管理、毕业大类映射 CourseGroup 等

src/test/java/                       # 单元测试（结算规则）
src/main/resources/
├── application.yml                  # 数据库、AI 助手、静态资源配置
├── static-vue/                      # Vue 构建产物（由构建生成，不进版本库）
└── static/                          # 旧版原生前端（对照保留）

init-mysql.sql    # MySQL 演示数据脚本（含 DROP TABLE，使用前务必检查）
pom.xml           # Java 版本、依赖与 Maven 构建配置
deploy.sh         # Linux/Cloud Studio 演示部署脚本
```

## 环境要求

- JDK 17、MySQL 8（使用默认 `mysql` profile 时）
- Node.js 18+（仅构建前端时需要）
- Maven 3.6+；仓库中也提供 Maven 3.9.6 可执行文件

## 构建与启动

前端产物已在 `static-vue/` 时可跳过第 1 步。在仓库根目录执行：

```powershell
# 1) 构建前端（输出到 src/main/resources/static-vue）
npm --prefix frontend install          # 首次需要
npm --prefix frontend run build

# 2) 打包后端（前端产物一并打进 jar）
.\apache-maven-3.9.6\bin\mvn.cmd clean package

# 3) 启动
java -jar .\target\zhixue-selection-1.0.0.jar
```

也可以在 IDE 中直接运行 `com.zhixue.ZhixueApplication`。注意 IDE 运行读取的是 `target/classes/`：修改前端后需要重新执行 `npm --prefix frontend run build`，并把 `static-vue` 同步到 `target/classes/`（或重新执行一次 `mvn compile`），刷新浏览器（Ctrl+F5）即可生效。

本地运行 AI 助手时，可通过环境变量设置兼容接口参数；未设置时使用 `application.yml` 中的默认值：

```powershell
$env:AI_BASE_URL = "https://your-compatible-api.example/v1"
$env:AI_API_KEY  = "your-api-key"
$env:AI_MODEL    = "your-model"
```

密钥仅用于本地环境配置，不要粘贴到文档、提交记录或公开日志中。

【关键设计 — advisorId 文件隔离】
所有论文/消息/公告都带 graduate_advisor 表的主键 advisorId
下载接口双重校验：ga.studentId == 当前用户ID 或 ga.teacherId == 当前用户ID
不匹配 → 403 拒绝，跨导师/跨学生数据完全不可见

应用启动时 `config/DataInitializer.java` 会在对应表为空时初始化演示数据：

| 角色 | 账号 | 密码 |
|---|---|---|
| 管理员 | `admin` | `admin` |
| 教师 | `T001` ~ `T004` | `123456` |
| 学生 | `2023010101` ~ `2023010105` | `123456` |

## 数据初始化与重置

- `DataInitializer` 只在数据表为空时写入演示数据，不会清空已有数据。
- `init-mysql.sql` 是独立的 MySQL 演示初始化脚本，其中包含 `DROP TABLE`；对已有数据库执行前请先备份并检查脚本内容。
- 数据库结构由 JPA 实体映射，开发配置使用 `ddl-auto: update`；`enrollments` 的 `(student_id, course_id, round)` 唯一约束也依赖该机制自动创建。

## 前后端协作约定

- API 路径以 `/api` 开头；前端请求封装位于 `frontend/src/api/`，登录后 token 与用户信息保存在 `localStorage`，请求通过 `Authorization: Bearer <token>` 传递。
- API 返回统一结构：`success` 表示是否成功，`message` 提供说明，`data` 携带结果；结构定义在 `dto/ApiResponse.java`。
- 业务逻辑遵循 `Controller → Service → Repository → Entity` 分层；不要把业务规则堆进控制器或前端。
- 前端菜单按角色渲染，仅是体验层隔离；权限判断需以后端为准（见"已知简化"）。

## 核心业务规则

### 三轮录取机制

- 第 1、2 轮为积分选课：报名阶段只记录 `pending` 与积分投入，窗口期内任意时间提交都有效；截止后系统按积分全局降序统一录取（同分随机抽签）。
- 第 3 轮为先到先得：确认加入即录取，不使用积分。
- 候补队列：课程满员时学生可手动加入；退课或改选释放名额后按加入时间顺序递补，递补者需在限时内确认（默认 30 分钟，确认时复检时间冲突），超时则顺延给下一位。
- 支持改选：改选成功后才释放旧课的候补位（`EnrollmentService` 在旧课释放后触发 `promoteIfNeeded` 递补）。

### 选课并发与结算约束

- 第 1、2 轮报名阶段只记录 `pending`，结算时按投入积分全局降序录取（同分随机），并在录取前重新校验时间冲突和学期学分上限，避免"报名时都合法、结算后互相冲突"。
- 第 3 轮先到先得在课程行锁内统计已录取人数再插入，并发抢最后一个名额不会超卖。
- `enrollments` 对 `(student_id, course_id, round)` 建立唯一约束；选课、退课、改选会锁定学生行和课程行（按课程 ID 升序加锁），避免重复报名、积分超扣和交叉死锁。
- 手动结算与定时结算通过 `system_config` 行锁互斥；自动结算必须在该轮结束时间之后执行。
- 使用非 `ddl-auto: update` 的部署方式时，需要手动创建上述唯一约束。

### 学分封顶

- 每学期总学分受系统配置的学期上限约束（默认 24 分）。
- 毕业学分按毕业要求分类封顶：必修/选修/通识/实践分别按 `grad_req_*` 配置封顶，分类口径由 `CourseGroup` 统一维护（选课入口与结算使用同一口径）。
- 第 3 轮在报名入口即时校验封顶；第 1、2 轮允许超报，由结算统一裁决。

### 错峰开放

- 管理员可为第 1、2 轮配置"年级 → 开放时间"窗口；学生的年级为入学年份（如 2023 表示 2023 级），可在用户管理中维护，注册时优先取显式值、否则从学号前 4 位推导。
- 错峰只影响提交时间，不影响截止后的统一排序结果；未配置窗口或未设置年级的学生跟随全局开放时间。

## 测试与校验

```powershell
.\apache-maven-3.9.6\bin\mvn.cmd test
```

`src/test/java/com/zhixue/service/EnrollmentSettlementTest.java` 覆盖结算的积分排序、容量限制、时间冲突、学分分类封顶、重复报名等规则（共 7 个用例）。新增业务规则时请同步补充测试。

## 配置与部署注意事项

- 服务默认监听 `8080`；数据库连接、Hibernate 和 AI 助手配置位于 `src/main/resources/application.yml`。
- `deploy.sh` 面向 Linux/Cloud Studio，包含环境安装、MySQL 容器和应用启动步骤；不是 Windows 部署脚本，使用前请审查其中的账号、口令、端口映射及 Docker 操作。
- 仓库中的演示数据和默认配置仅用于本地演示，不要用于生产环境。

## 已知简化（演示取舍）

以下实现是为单机演示做的简化，答辩或扩展时可作为改进方向：

- **会话**：token 存于内存 `ConcurrentHashMap`，服务重启后登录态失效、无过期时间；生产应改用 JWT + Redis。
- **口令**：用户口令为明文存储比对；生产应使用 BCrypt 等哈希加盐。
- **权限**：后端暂未统一拦截校验角色，接口主要依赖前端菜单隔离；生产应在拦截器/网关统一校验 token 与角色。
- **通知**：通知中心为前端本地数据，SSE 推送未接入。
- **分页**：部分列表接口为全量返回，数据量大时应加分页。
