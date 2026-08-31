# AI Knowledge Base Roadmap

## 1. Goal

求职目标：
- 2027 届 Java 后端
- 后端开发
- AI 应用后端 / 大模型应用开发

项目目标：
完成一个能运行、能演示、能复现、能写进简历、能在面试中讲清楚的 AI 知识库系统 V1.0。

当前状态：
- V0.1 已完成
- Spring Boot 项目骨架已有
- MySQL / Redis 基础环境已有
- GET /health 已有
- 初始表结构和 README 已有
- 不重新学习第 1 周，只做必要验证和收尾

主线优先级：

Java 后端基本盘
> 完整业务闭环
> AI 服务工程化
> 最小 RAG + 可验证效果
> Redis / Docker
> 简历 / 面试 / 投递

暂缓：
- Agent
- MCP
- LangGraph
- K8s
- 微服务
- 复杂 PDF / OCR
- 漂亮前端
- 多模型路由
- 流式输出

---

## 2. Current Task Rule

Codex 不要自行根据完整 Roadmap 连续实现多个阶段。

处理任务时：
1. 先确认当前版本。
2. 每次只完成一个小功能或一个问题。
3. 当前任务完成并验证后，再进入下一项。
4. 不提前实现后续阶段功能。

---

## 3. V0.1 Final Check

目标：确认已有基础真正可用。

必须确认：
- Spring Boot 能启动
- GET /health 正常
- Java 能真实访问 MySQL
- Redis 可连接
- Git 工作区正常
- README / 项目目录正常

如果某项失败：
只修复该问题，不重学 Java 基础，不扩展新功能。

完成后进入 V0.2。

---

## 4. Sprint 1 — V0.2 User Authentication

目标：
完成最基础 Java 后端业务闭环。

必须完成：
- RegisterRequest
- LoginRequest
- LoginResponse
- UserVO
- 用户注册
- 参数校验
- 用户名重复检查
- BCrypt 密码加密
- 登录
- JWT 生成
- JWT 校验
- GET /api/user/me
- Filter 或 Interceptor 鉴权
- Result<T>
- ErrorCode
- BusinessException
- GlobalExceptionHandler

验收：
- 注册成功
- 数据库不保存明文密码
- 重复用户名有明确错误
- 正确密码登录返回 token
- 错误密码返回统一错误
- 无 token 无法访问 /api/user/me
- 合法 token 能获得当前用户
- 伪造 / 过期 token 有统一错误

不要做：
- 复杂 Spring Security
- 验证码
- 第三方登录

---

## 5. Sprint 2 — V0.4 Document + AI Service

### Document

必须完成：
- TXT / Markdown 上传
- document 表
- 文档状态
- 保存 storage/{userId}/
- 文件大小 / 后缀 / 空文件校验
- 列表
- 详情
- 逻辑删除
- 用户资源归属校验

验收：
用户 A 不能读取或删除用户 B 的文档。

### FastAPI

必须完成：
- Python FastAPI 服务
- GET /health
- POST /chat
- schemas / services / clients 基础分层
- API Key 使用环境变量
- Java AiClient
- AI_SERVICE_URL
- timeout
- Java → Python → LLM
- chat_message 入库
- AI 服务异常映射

验收：
Java → Python → LLM → DB 完整跑通。

不要做：
- PDF
- OSS
- 前端页面
- 流式输出
- 多模型路由

---

## 6. Sprint 3 — V0.5 RAG

目标：
完成最小但完整 RAG 闭环。

流程：

文档
→ 解析
→ chunk
→ Embedding
→ 向量索引
→ TopK
→ Prompt
→ LLM
→ answer + sources

必须完成：
- document_chunk
- chunk_index
- vector_id
- TXT / Markdown 解析
- chunk_size 可配置
- chunk_overlap 可配置
- FAISS 或 Chroma
- Embedding
- 本地向量索引
- 上传后触发索引
- UPLOADED / PARSED / FAILED
- POST /rag/chat
- document_id + question
- TopK
- answer
- sources
- Java RAG API
- 权限校验
- 历史保存

评测 v0：
- 20–30 个问题
- 至少比较一组 chunk_size / TopK
- 记录是否命中正确来源
- docs/rag-evaluation.md

重点：
不是只证明“用了向量库”，而是能解释：
- 为什么这样切分
- TopK 为什么这样选
- 哪些问题失败
- 如何验证回答来自文档

---

## 7. Sprint 4 — V0.6 Redis + Engineering

优先级：

缓存
> 历史
> 超时 / 异常
> 状态一致性
> 限流

### Redis

设计：
rag:answer:{userId}:{documentId}:{questionHash}

必须明确：
- 缓存什么
- TTL
- cache hit / miss
- 什么情况下失效

必须完成：
- RAG 调用前查缓存
- miss 后调用 AI
- 成功后写缓存
- 文档删除 / 重建索引后清缓存

### History

必须完成：
- 当前用户历史分页
- create_time 倒序
- 不能查询其他用户历史

### Engineering Problems

至少真实解决 2 个问题，例如：
- Java → Python timeout
- LLM 调用失败
- 重复索引
- 索引状态不一致
- 文件与数据库状态不一致
- 缓存失效

每个问题记录：

问题
→ 现象
→ 排查
→ 原因
→ 方案
→ 验证
→ 当前不足

限流：
有时间再做 Redis INCR + EXPIRE。
时间不足可只保留设计。

---

## 8. Sprint 5 — V0.8 Docker + Testing + Evaluation

目标：
不再大量新增功能，提升可复现性和项目证据。

必须完成：
- backend Dockerfile
- ai-service Dockerfile
- docker-compose.yml
- MySQL
- Redis
- backend
- ai-service
- 环境变量
- README 启动步骤

如果 Docker 卡住超过半天：
优先保证 README 的本地手动启动可复现。

测试重点：
- 登录
- JWT
- 权限
- 文档
- RAG
- 核心异常路径

不追求测试覆盖率数字。

RAG 评测 v1：
- 扩展到约 30–50 个问题
- 比较 2–3 组 chunk / TopK
- 保存结果
- 保存失败案例

README 至少包括：
- 项目简介
- 技术栈
- 系统架构
- RAG 流程
- 数据库
- 快速启动
- API
- 截图
- RAG 评测
- 工程难点
- 已知不足

---

## 9. Sprint 6 — V1.0

最后一阶段冻结新功能。

只做：
- P0 / P1 bug
- 演示流程稳定
- README
- 2–3 分钟演示视频
- 10 分钟项目讲解
- 项目追问题库
- 简历
- 模拟面试
- 投递

完整演示：

注册
→ 登录
→ 上传
→ 索引
→ RAG 提问
→ sources
→ 历史查询

项目至少准备这些面试主题：
- JWT
- BCrypt
- Spring Boot 分层
- MySQL 索引 / 事务
- Redis / 缓存一致性
- Java → Python 服务调用
- timeout / 异常
- RAG
- Embedding
- chunk
- TopK
- Docker

---

## 10. Learning Priorities

算法：
- 目标 40–60 道高频题
- 工作日约 1 道
- 优先高频题、错题、二刷
- 必须能解释复杂度和边界

八股：
- Java 基础
- Spring Boot
- HTTP / JWT
- MySQL
- Redis
- JVM / 线程池基础
- Docker / Linux
- RAG / Embedding / 向量检索

项目优先于刷课。

忘记知识点时按需查询，不从头重新学习整个课程。

---

## 11. AI Development Rules

AI 可以：
- 解释代码
- 拆分任务
- 审查 git diff
- 定位错误
- 提供测试思路
- 给最小修改建议
- 帮助整理学习笔记

AI 不应：
- 一次完成整周任务
- 未经要求实现后续功能
- 大规模重构
- 替我完成所有核心代码
- 为了“更高级”自行增加技术栈

AI 生成核心代码后必须检查：
- 权限
- 异常
- 事务
- null
- SQL
- 环境变量
- API Key
- Redis TTL / 失效
- 用户资源归属
- 服务 timeout

---

## 12. Scope Reduction Rules

如果鉴权没跑通：
→ 不做 RAG / Docker。

如果上传卡住：
→ 只支持 TXT / Markdown + 本地 storage。

如果 Java → Python 不稳定：
→ 固定一个模型，只处理 timeout / 异常。

如果 RAG 效果一般：
→ 不上 Agent，先保证正确检索和 sources。

如果 Redis 进度不足：
→ 只做缓存 + TTL + 失效。

如果 Docker 卡住：
→ 优先 README 手动启动。

如果算法落后：
→ 不补数量，继续约 1 题 / 天。

遇到新知识：
→ 只补当前高频缺口，不改变整个路线。

---

## 13. Definition of Done

V1.0 完成时，应做到：

1. 能完整演示注册、登录、上传、索引、RAG、sources、历史。
2. 能解释 Java 与 Python 为什么拆分。
3. 能解释服务调用失败如何处理。
4. 能完整解释 RAG。
5. 有 30–50 个问题的小型评测集。
6. 有参数对比和失败案例。
7. 能解释 Redis 缓存及失效。
8. 至少有 2 个真实工程问题及排查记录。
9. README 或 Docker 能让别人复现项目。
10. 能脱稿讲项目 8–10 分钟。
11. 简历中的每个项目亮点都有代码 / 日志 / 测试 / 文档证据。
12. AI 写过的代码，我自己能解释、验证和排错。