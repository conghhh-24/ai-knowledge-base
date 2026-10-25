# AI Knowledge Base

一个面向 Java 后端和 AI 应用后端学习的知识库项目。当前版本先完成用户鉴权和本地文档管理，再逐步接入 FastAPI、RAG、Redis 缓存和 Docker 化部署。

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

鉴权验收已通过，详细记录见 [docs/auth-acceptance.md](docs/auth-acceptance.md)。

尚未完成：

- FastAPI AI 服务和 Java → Python 调用
- 文档解析、分块、Embedding 和向量检索
- RAG 问答、sources 和历史问答
- Redis 缓存、Docker 完整编排和系统测试

## 技术栈

- Java 17
- Spring Boot 3.5
- MySQL 8
- Redis 7
- JWT（jjwt）
- BCrypt
- Maven

## 项目结构

```text
backend/       Spring Boot Java 后端
ai-service/    后续 FastAPI AI 服务
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

执行 [docs/sql/init.sql](docs/sql/init.sql)，创建 `ai_knowledge_base` 数据库及 `user`、`document`、`chat_message` 表。

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

认证接口的请求和返回对象说明见 [docs/api-auth.md](docs/api-auth.md)。

## 数据和文件说明

- 用户密码只以 BCrypt 密文保存到 MySQL。
- 上传文件保存到 `storage/{userId}/`。
- 当前只接受 `.txt` 和 `.md` 文件。
- 文档初始状态为 `UPLOADED`，后续索引流程会扩展状态流转。
- 不要把真实密码、JWT 密钥或其他敏感配置提交到 Git。

## 学习路线

当前阶段是：

```text
鉴权验收 → 文档资源归属验收 → FastAPI AI 服务 → 最小 RAG → Redis 与工程化 → Docker 与测试
```

完整计划见 [docs/roadmap.md](docs/roadmap.md)。
