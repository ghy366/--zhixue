# 智学选课平台

面向高校场景的全栈选课系统：后端以 Spring Boot 提供 REST API，前端为 Vue 3 单页应用，构建产物由 Spring Boot 直接托管，正常运行只需启动一个服务，默认访问 `http://localhost:8080`。

系统围绕"三轮录取机制"构建，并覆盖选课全链路与配套教学管理：

| 层次 | 已实现功能 |
|---|---|
| 选课核心 | 课程检索/筛选/排序、三轮录取（积分竞价 + 先到先得补选）、统一结算与同分抽签、退选/改选/原子换课、容量与时间冲突校验、学分与门数封顶、先修课与重修规则、候补队列与限时递补、错峰开放、录取线/概率模拟 |
| 教学配套 | 学生/教师/管理员三角色权限、学期与课程/教室/用户管理、培养方案、智能排课与冲突检测、教师成绩录入（含补考/重修）、学生成绩与绩点、毕业要求与毕业审核、课程评价、选课意愿征集、公告、站内消息推送、操作日志、统计看板、CSV/XLSX 导入导出 |
| 扩展与创新 | AI 选课助手（联网检索 + 业务数据问答 + 流式输出）、协同过滤课程推荐、2D 等轴测与 3D 校园沙盘课表、毕设导师双向选择与论文工作台（按导师隔离文件） |

## 技术栈

- **后端**：Java 17（源码级别，JDK 17 及以上均可编译运行）、Spring Boot 3.3.5、Spring Web、Spring Data JPA、Bean Validation、Maven
- **数据库**：MySQL 8（`application.yml` 默认激活 `mysql` profile；另含 `cloudstudio` profile，仓库同时保留 H2 驱动）
- **前端**：Vue 3、TypeScript、Vite、Pinia、Vue Router、Element Plus、Three.js（3D 校园沙盘 / 登录页地球）、ECharts（统计图表）
- **其他**：Apache POI（Excel 导入导出）、SSE 站内推送、BCrypt 口令哈希 + RSA 传输加密、OpenAI 兼容的大模型接口

> `src/main/resources/static/` 保留的是旧版原生 HTML/CSS/JS 前端，仅作对照；当前服务的是 Vue 构建产物 `static-vue/`，由 `spring.web.resources.static-locations` 指定。

## 项目结构

```text
frontend/                            # Vue 3 前端源码（npm run build 输出 static-vue）
├── src/views/                       # 30 余个业务页面（学生/教师/管理员）
├── src/components/                  # 布局、3D 沙盘、图表、登录页地球等
├── src/stores/、src/router/、src/api/、src/i18n/
└── src/styles/                      # 全局样式（键帽菜单、透明顶栏等）

src/main/java/com/zhixue/
├── config/      # MVC 与统一鉴权、请求日志、异常处理、启动数据初始化、自动结算任务、口令迁移
├── controller/  # 23 个 REST 控制器
├── dto/         # 统一响应结构 ApiResponse
├── entity/、repository/             # JPA 实体与数据访问
├── service/     # 选课结算、候补、错峰、毕业要求、AI 助手、统计等业务逻辑
└── util/        # 会话管理、口令工具、毕业大类映射 CourseGroup 等

src/test/java/                       # 5 个测试类（结算规则、并发、门数上限、毕业分类、AI 意图识别）
src/main/resources/
├── application.yml                  # 端口、数据库、AI 助手、静态资源、外部配置导入
├── static-vue/                      # Vue 构建产物（由构建生成）
└── static/                          # 旧版原生前端（对照保留）

init-mysql.sql    # MySQL 演示数据脚本（含 DROP TABLE，使用前务必检查）
pom.xml           # Java 版本、依赖与 Maven 构建配置
application-local.yml                # 参考副本（非生效配置，说明见"配置说明"）
```

## 环境要求

- JDK 17+、MySQL 8（使用默认 `mysql` profile 时）、Maven 3.6+
- Node.js 18+（仅构建前端时需要）

本机环境中 Maven 与 JDK 未加入 PATH，可直接使用 `E:\buildtools\apache-maven-3.9.9\bin\mvn.cmd` 与 `F:\JDK\JDK23\bin\java.exe`。

## 构建与启动

前端产物已在 `static-vue/` 时可跳过第 1 步。在仓库根目录执行：

```powershell
# 1) 构建前端（输出到 src/main/resources/static-vue）
npm --prefix frontend install          # 首次需要
npm --prefix frontend run build

# 2) 打包后端（前端产物一并打进 jar）
mvn clean package -DskipTests

# 3) 启动
java -jar .\target\zhixue-selection-1.0.0.jar
```

也可以在 IDE 中直接运行 `com.zhixue.ZhixueApplication`。IDE 运行读取的是 `target/classes/`：修改前端后需重新 `npm --prefix frontend run build`，并把 `static-vue` 同步到 `target/classes/`（或重新执行一次 `mvn compile`），刷新浏览器（Ctrl+F5）即可生效。

**端口与开发代理**：默认监听 8080。若 8080 被其他应用占用，可用 `--server.port=8081` 启动后端；前端独立开发时设 `VITE_API_TARGET=http://localhost:8081` 让 Vite 代理指向该端口。Windows 下重新打包前需先停止正在运行的 `java -jar` 进程，否则 jar 文件被占用会导致打包失败。

## 配置说明

- **私有配置**：`application.yml` 通过 `spring.config.import: optional:file:E:/buyyd数据缓存/application-local.yml` 导入外部本地配置，**API Key 只放这一份外部文件**，不写入仓库源码。项目根目录的 `application-local.yml` 只是参考副本，不生效。
- **AI 助手**：主通道为 OpenAI 兼容接口（默认阿里云百炼 Qwen，模型 `qwen-flash`），支持同网关备用模型级联、其他网关备用通道与全部失败后的兜底通道；联网检索使用独立的 Search API 配置。可用环境变量覆盖：

```powershell
$env:AI_BASE_URL = "https://your-compatible-api.example/v1"
$env:AI_MODEL    = "your-model"
$env:AI_FALLBACK_MODELS = "your-backup-model"
$env:DASHSCOPE_API_KEY = "your-api-key"   # 也可写在外部 application-local.yml
```

- `fallback-models` 与主通道共用 `base-url` / `api-key`，只能填同一网关内的模型；不同网关通过 `extra-channels`（JSON 数组）配置。
- 密钥仅用于本地环境配置，不要粘贴到文档、提交记录或公开日志中。

## 演示账号

应用启动时 `config/DataInitializer.java` 会在对应表为空时初始化演示数据：

| 角色 | 账号 | 密码 |
|---|---|---|
| 管理员 | `admin` | `admin` |
| 教师 | `T001` ~ `T004` | `123456` |
| 学生 | `2023010101` ~ `2023010105` | `123456` |

口令以 BCrypt 哈希存储，登录时前端用公钥加密传输、后端私钥解密后比对；历史上存在的明文口令会在启动时由 `PasswordMigrationRunner` 自动升级。

## 数据初始化与重置

- `DataInitializer` 只在数据表为空时写入演示数据，不会清空已有数据。
- `init-mysql.sql` 是独立的 MySQL 演示初始化脚本，其中包含 `DROP TABLE`；对已有数据库执行前请先备份并检查脚本内容。
- 数据库结构由 JPA 实体映射，开发配置使用 `ddl-auto: update`；`enrollments` 的 `(student_id, course_id, round)` 唯一约束也依赖该机制自动创建。

## 前后端协作约定

- API 路径以 `/api` 开头；前端请求封装位于 `frontend/src/api/`，登录后 token 与用户信息保存在 `localStorage`，请求通过 `Authorization: Bearer <token>` 传递。
- API 返回统一结构：`success` 表示是否成功，`message` 提供说明，`data` 携带结果；结构定义在 `dto/ApiResponse.java`。
- 业务逻辑遵循 `Controller → Service → Repository → Entity` 分层；不要把业务规则堆进控制器或前端。
- **权限以后端为准**：`config/RoleInterceptor.java` 统一拦截 `/api/**`，按「HTTP 方法 + 路径前缀 + 角色白名单」匹配，未登录返回 401、越权返回 403；前端菜单按角色渲染只是体验层。EventSource 无法携带请求头，SSE 订阅通过 `?token=` 传参。

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
mvn test
```

`src/test/java/com/zhixue/service/` 下 5 个测试类共 38 个 `@Test` 用例，覆盖结算的积分排序、容量限制、时间冲突、学分分类封顶、重复报名、并发抢课、课程门数上限、毕业分类判定与 AI 意图识别。新增业务规则时请同步补充测试。

## 已知简化（演示取舍）

以下是为单机演示做的简化，答辩或扩展时可作为改进方向。注意：口令哈希、传输加密、后端角色鉴权、站内消息推送均已实现，**不属于**简化项。

- **会话**：token 存于内存 `ConcurrentHashMap`，服务重启后登录态失效、无过期时间；生产应改用 JWT + Redis。
- **前端路由**：`router/index.ts` 只校验是否登录，未做前端角色守卫；越权访问会由后端返回 403，但直接输入跨角色 URL 时可能看到空页面。
- **开课流程**：课程由管理员统一维护，未实现"教师提交开课申请 → 教务审核发布"链路；这是本系统的功能边界取舍。
- **分页**：课程列表已支持服务端分页，部分统计与名单接口仍全量返回，数据量大时应补充分页。
- **并发范围**：并发控制基于数据库行锁，适用于单机部署；多实例部署需引入分布式锁或消息队列。
- **配置与数据**：数据库连接口令写在 profile 配置中，演示账号与演示数据仅用于本地环境，不要用于生产。
