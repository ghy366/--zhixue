# 智学选课 · 大学生选课平台

纯前端项目（HTML + CSS + JavaScript），无需后端、无需安装依赖。

## 运行方式

### 方式一：IDE 内置服务器（推荐）
- **VS Code**：安装 `Live Server` 插件 → 右键 `index.html` → `Open with Live Server`
- **IntelliJ IDEA / WebStorm**：右键 `index.html` → `Open in` → `Browser`
- 任何能启动静态服务器的 IDE 均可

### 方式二：命令行启动
```bash
# Python 3
python -m http.server 8080
# 然后访问 http://localhost:8080

# 或 Node.js
npx serve .
```

### 方式三：直接打开
直接双击 `index.html` 也可运行（推荐用服务器方式，以保证 localStorage 正常工作）。

## 演示账号

| 角色 | 账号 | 密码 |
|------|------|------|
| 学生 | 2023010101 | 123456 |
| 老师 | T002 | 123456 |
| 管理员 | admin | admin |

## 功能说明

- **学生**：积分选课（每轮60分）、三轮选课、课表、成绩
- **老师**：查看课程、审批超容量选课（择优录取）
- **管理员**：切换选课轮次、课程管理、用户管理

## 数据存储

所有数据存储在浏览器 `localStorage` 中。如需重置，点击侧边栏底部"重置演示数据"按钮。
