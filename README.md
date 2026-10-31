# AI Knowledge Base

一个面向 Java 后端和 AI 应用后端学习的知识库项目，已完成用户鉴权、文档管理、Java → FastAPI 调用和最小 RAG 闭环。

## 当前状态

已完成并验证：

- Spring Boot 后端骨架和 `GET /health`
- MySQL、Redis 基础运行环境
- 用户注册、登录、BCrypt 密码加密
- JWT 生成、校验和接口鉴权
- 统一响应、参数校验和异常处理
- TXT / Markdown 文档上传
- 文档列表、详情和删除
- 文档按用户隔离，用户只能访问自己的文档
- 文档解析、分块和 `document_chunk` 入库
- 分块参数可配置，失败上传保留 FAILED，上传失败和文档删除时清理向量（见 [文档一致性说明](docs/document-consistency.md)）
- 上传后自动调用 FastAPI 建立 Chroma 向量索引
- RAG 问答和 `answer + sources` 返回
- RAG 答案 Redis 缓存、TTL，以及文档删除 / 重建索引后的缓存失效
- 当前用户问答历史分页查询，按创建时间倒序并按用户隔离

鉴权验收已通过，详细记录见 [docs/auth-acceptance.md](docs/auth-acceptance.md)。
RAG 验收已通过，详细记录见 [docs/rag-acceptance.md](docs/rag-acceptance.md)。

尚未完成：

- Docker 完整编排和端到端系统测试
- 更长文档和更多参数组合的 RAG 评测扩展

## 技术栈

- Java 17
- Spring Boot 3.5
- MySQL 8
- Redis 7
- FastAPI
- Chroma
- Embedding API 和 LLM API
- JWT（jjwt）
- BCrypt
- Maven

## 项目结构

```text
backend/       Spring Boot Java 后端
ai-service/    FastAPI AI 服务
docs/          路线图、接口说明、验收记录和 SQL
storage/       本地上传文件目录，按 userId 分目录保存
docker-compose.yml  MySQL 和 Redis 基础环境
```

## 本地启动

### 1. 启动 MySQL 和 Redis

```bash
docker compose up -d mysql redis
```

MySQL 映射到 `localhost:3307`，Redis 映射到 `localhost:6379`。

### 2. 初始化数据库

执行 [docs/sql/init.sql](docs/sql/init.sql)，创建 `ai_knowledge_base` 数据库及 `user`、`document`、`document_chunk`、`chat_message` 表。

### 3. 配置 JWT 密钥

`backend/src/main/resources/application.properties` 通过环境变量读取 `JWT_SECRET`。启动前设置一个满足 HS256 要求的密钥，例如 PowerShell：

```powershell
$env:JWT_SECRET = "replace-with-a-long-local-secret"
```

### 4. 启动后端

```bash
cd backend
./mvnw spring-boot:run
```

Windows PowerShell 使用：

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

启动后访问 `http://localhost:8080/health`，返回 `ok` 表示后端已启动。

### 5. 启动 FastAPI AI 服务

```powershell
cd ai-service
$env:LLM_API_KEY = "replace-with-your-llm-api-key"
$env:DASHSCOPE_API_KEY = "replace-with-your-embedding-api-key"
uvicorn app.main:app --reload --port 8000
```

FastAPI 默认监听 `http://localhost:8000`。Java 通过 `AI_SERVICE_URL` 配置 AI 服务地址，默认值也是该地址。

## 已实现接口

所有业务接口都使用统一的 `Result<T>` 响应格式。除注册和登录外，接口需要在请求头中携带：

```text
Authorization: Bearer <token>
```

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/health` | 健康检查 |
| POST | `/api/auth/register` | 用户注册 |
| POST | `/api/auth/login` | 用户登录并返回 JWT |
| GET | `/api/auth/me` | 获取当前登录用户 |
| POST | `/api/documents/upload` | 上传 TXT / Markdown 文档，单文件不超过 50 MB |
| GET | `/api/documents` | 获取当前用户的文档列表 |
| GET | `/api/documents/{id}` | 获取当前用户的文档详情 |
| DELETE | `/api/documents/{id}` | 删除当前用户的文档及本地文件 |
| POST | `/api/ai/chat` | 基于指定文档进行 RAG 问答并返回 sources |
| GET | `/api/ai/history?page=1&pageSize=20` | 查询当前用户问答历史，按时间倒序分页 |

认证接口的请求和返回对象说明见 [docs/api-auth.md](docs/api-auth.md)。

## 数据和文件说明

- 用户密码只以 BCrypt 密文保存到 MySQL。
- 上传文件保存到 `storage/{userId}/`。
- 当前只接受 `.txt` 和 `.md` 文件。
- 文档上传后解析、分块并建立向量索引，成功后状态为 `PARSED`。
- RAG 问答返回 `answer` 和 `sources`，source 包含命中的 chunk、`chunk_index` 和距离。
- 不要把真实密码、JWT 密钥或其他敏感配置提交到 Git。

## 学习路线

当前阶段是：

```text
鉴权验收 → 文档资源归属验收 → FastAPI AI 服务 → 最小 RAG → Redis 与工程化 → Docker 与测试
```

完整计划见 [docs/roadmap.md](docs/roadmap.md)。
