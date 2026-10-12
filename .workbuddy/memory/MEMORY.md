# 智学选课 · 项目长期备忘

## 用户偏好与教训
- 登录页不要随意大改版：2026-10-12 左右分栏改版后用户遇到登录受阻，直接要求回退到未改动版本。改 UI 前先确认用户接受度；登录链路加阻断性校验（如验证码）必须同步兼容快捷登录按钮。
- 回退改动时勿用 `git checkout`：工作区长期存在大量用户未提交改动，只精确回退本次会话动过的文件/片段。
- AI 助手已接入 WorkBuddy 自带免密钥大模型：前端用官方 @tencent-ai/workbuddy-cloud-sdk 直连流式调用（应用 wbapp_u84krQjk8ztBGh52assLFx，域 zhixue-xuanke.app.workbuddy.host）。云端鉴权失败(auth_，本地 localhost)时回退后端 Pollinations 免费模型。注意：WorkBuddy 模型仅在该应用 workbuddy.host 域名下才生效，本地开发走兜底。
