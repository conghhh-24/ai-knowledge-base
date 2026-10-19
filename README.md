# AI Knowledge Base

## 项目目标

实现一个基于 Spring Boot、FastAPI、MySQL、Redis 的 AI 知识库问答系统。

## 项目结构

- backend：Java 后端
- ai-service：Python AI 服务
- docs：项目文档
- storage：上传文件存储目录

## 当前进度

- 完成开发环境搭建
- 完成 Spring Boot 项目初始化
- 完成 GET /health 接口
- 完成用户注册、登录、JWT 鉴权和当前用户接口
- 完成统一参数错误、业务错误和 Token 错误响应

## 当前验收状态

鉴权主链路已实现，待在本地 MySQL、Redis 和 `JWT_SECRET` 环境下完成接口验收：

- 注册成功、重复用户名
- 正确密码登录、错误密码登录
- 无 Token、合法 Token、伪造或过期 Token 访问 `/api/auth/me`
- 检查数据库中保存的是 BCrypt 密文

验收记录模板见 `docs/auth-acceptance.md`。
