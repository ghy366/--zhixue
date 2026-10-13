# 智学选课平台

智学选课是一个以 Spring Boot 提供 REST API、以原生 HTML/CSS/JavaScript 提供前端页面的全栈选课演示项目。后端同时负责提供 `src/main/resources/static/` 下的页面资源，开发时通常只需启动一个服务，默认访问 `http://localhost:8080`。

## 技术栈

- Java 17、Spring Boot 3.3.5、Maven
- Spring Web、Spring Data JPA、Bean Validation
- MySQL（当前默认启用的配置）；项目还包含 H2 和 MySQL JDBC 驱动
- 原生 HTML、CSS、JavaScript
- Apache POI：Excel 文件导入与导出

## 项目结构

```text
src/main/java/com/zhixue/
├── config/       # Spring MVC、请求日志、异常处理、启动数据初始化
├── controller/   # REST API 控制器，负责接收请求和组织响应
├── dto/          # API 数据传输对象和统一响应结构
├── entity/       # JPA 实体及数据库映射
├── repository/   # Spring Data JPA 数据访问接口
├── service/      # 业务逻辑
├── util/         # 会话等通用工具
└── ZhixueApplication.java

src/main/resources/
├── application.yml  # Spring Boot、数据库和 AI 助手配置
└── static/
    ├── index.html   # 单页应用入口及页面结构
    ├── app.js       # 页面交互、状态和 /api 请求
    ├── styles.css   # 页面样式
    └── bg-effects.js

init-mysql.sql       # MySQL 演示数据脚本（含删除表语句，使用前务必检查）
pom.xml              # Java 版本、依赖和 Maven 构建配置
deploy.sh            # Linux/Cloud Studio 演示部署脚本
```

## 本地开发

### 环境要求

- JDK 17
- MySQL 8（使用当前默认配置时）
- Maven 3.6+；仓库目录中也提供了 Maven 3.9.6 可执行文件

### 配置数据库

当前 `application.yml` 默认激活 `mysql` profile，连接本机 `zhixue` 数据库，端口为 `3306`。启动前请确认 MySQL 正在运行，并检查该 profile 中的连接地址、用户名和密码是否适合你的本地环境。不要把个人或生产环境凭据提交到版本库。

`pom.xml` 中虽然包含 H2 依赖，但当前配置没有启用 H2 profile；不要仅因依赖存在就假定应用默认使用 H2。

### 构建和启动

在 Windows PowerShell 中，从仓库根目录执行：

```powershell
# 如果已安装 Maven
mvn clean package

# 或使用仓库中提供的 Maven
.\apache-maven-3.9.6\bin\mvn.cmd clean package

# 启动应用
java -jar .\target\zhixue-selection-1.0.0.jar
```

也可以在 IDE 中运行 `com.zhixue.ZhixueApplication`。启动后打开 `http://localhost:8080`。

本地运行 AI 助手时，可通过环境变量设置兼容接口参数；未设置时使用 `application.yml` 中的默认值：

```powershell
$env:AI_BASE_URL = "https://your-compatible-api.example/v1"
$env:AI_API_KEY = "your-api-key"
$env:AI_MODEL = "your-model"
```

密钥仅用于本地环境配置，不要粘贴到文档、提交记录或公开日志中。

## 数据初始化与重置

应用启动时，`config/DataInitializer.java` 会在相应数据表为空时创建演示数据（用户、课程、选课记录、学期、教室和公告等）。有数据的表不会由这段初始化逻辑清空。

`init-mysql.sql` 是独立的 MySQL 演示初始化脚本，其中包含 `DROP TABLE`。运行它可能删除同名表及其中的数据；对已有数据库执行前，请先备份并检查脚本内容。数据库结构也由 JPA 实体映射，开发配置使用 Hibernate `ddl-auto: update`。

## 前后端协作约定

- 前端静态资源由 Spring Boot 从 `src/main/resources/static/` 提供。
- API 路径以 `/api` 开头；前端请求封装位于 `static/app.js`。
- API 通常返回统一结构：`success` 表示是否成功，`message` 提供说明，`data` 携带结果；结构定义在 `dto/ApiResponse.java`。
- 登录后端使用会话 token；前端将 token 和用户信息保存在浏览器 `localStorage`，请求时通过 `Authorization: Bearer <token>` 发送 token。
- 新增或调整业务时，优先保持 `Controller → Service → Repository → Entity` 的职责分层；请求/响应结构较复杂时，在 `dto/` 中定义类型，不要把业务规则堆进控制器或前端。

## 功能模块与 API 入口

控制器集中在 `controller/`，主要 API 前缀如下：

| 模块 | API 前缀 | 主要职责 |
|---|---|---|
| 登录与个人资料 | `/api/auth` | 登录、注册、退出、资料和密码管理 |
| 课程与选课 | `/api/courses`、`/api/enrollments`、`/api/intents` | 课程浏览、选课、退课、意向课程和积分 |
| 等候与排课 | `/api/waitlist`、`/api/schedule` | 候补队列、课表和冲突检查 |
| 教学与成绩 | `/api/grades`、`/api/reviews` | 成绩、课程评价及相关审批 |
| 教务管理 | `/api/admin`、`/api/system`、`/api/semesters` | 用户、选课轮次、系统配置和学期管理 |
| 通知与公告 | `/api/notify`、`/api/announcements` | 消息推送、通知和公告 |
| 毕业与研究生工作区 | `/api/graduation`、`/api/advisor`、`/api/workspace` | 毕业信息、导师和研究生工作区 |
| 数据与统计 | `/api/data`、`/api/stats`、`/api/logs` | 数据导入导出、统计报表和操作日志 |
| AI 助手 | `/api/ai` | AI 对话 |

服务端业务规则放在对应 `service/` 类中；数据库查询放在 `repository/` 接口中；持久化字段和关系定义在 `entity/` 中。增加实体或字段时，应同步检查初始化逻辑、前端展示以及相关导入导出流程。

## 测试与校验

```powershell
mvn test
mvn clean package
```

目前仓库的 `src/` 下未发现测试文件。新增功能时，请同时补充对应的服务层或 API 测试，并使用 `mvn test` 验证。

## 配置与部署注意事项

- 服务默认监听 `8080`；数据库连接、Hibernate 和 AI 助手配置位于 `src/main/resources/application.yml`。
- `deploy.sh` 面向 Linux/Cloud Studio，包含环境安装、MySQL 容器和应用启动步骤；不是 Windows 部署脚本。使用前请审查其中的账号、口令、端口映射及 Docker 操作，并替换为安全配置。
- 仓库中的演示数据和默认配置仅用于本地演示。不要将演示账号、默认口令或内置密钥用于生产环境；生产部署前应改用外部密钥管理，并关闭或替换演示初始化数据。
