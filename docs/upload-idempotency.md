# 上传幂等：相同操作只执行一次

## 调用方式

POST /api/documents/upload 仍使用 multipart/form-data 的 file 字段，新增必填请求头 Idempotency-Key。用户 ID 只从登录凭证取得，不接受客户端指定。

Key 支持1–128位字母、数字、下划线、连字符，推荐 UUID。同一次操作重试必须复用 Key 和原文件；新上传操作生成新 Key。同内容使用不同 Key 仍视为不同上传，这不是文件内容去重。

项目目前没有前端应用，提供可直接接入页面的 [浏览器调用示例](examples/upload-client.js)：

```javascript
const operation = createUploadOperation(file); // 用户开始一次新上传时创建
await operation.send(token);                   // 首次上传
// 网络断开后用户点击重试，保留 operation，不要重新创建
await operation.send(token);
// 用户选择另一次新上传时，再调用 createUploadOperation(newFile)
```

示例依赖浏览器安全上下文提供的 crypto.randomUUID()。不要把 Key 的生成放在每次 send 或重试函数里；页面重载后如需恢复重试，需另外保留 Key 并让用户重选相同文件。本次没有新增页面或前端框架。

PowerShell 调用：

```powershell
$uploadKey = [guid]::NewGuid().ToString()
$headers = @{ Authorization = "Bearer $token"; "Idempotency-Key" = $uploadKey }
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/documents/upload" -Headers $headers -Form @{ file = Get-Item ".\demo.txt" }
# 重试时复用 $headers，不要重新生成 $uploadKey。
```

## 数据与事务

新增 upload_request 表，UNIQUE(user_id, idempotency_key)，Key 区分大小写。记录 request_hash、document_id、status、result_json 和 error_code。

1. 校验 Key、文件和登录用户，计算文件名及文件内容的 SHA-256 指纹。
2. 独立事务内先插入 PROCESSING 幂等记录，再创建 UPLOADED 文档并关联 document_id，统一提交。
3. 提交后才写文件、解析和调用 Python；成功后在另一个事务内统一保存分块、PARSED 文档状态和 SUCCEEDED 原始结果快照。
4. 失败时独立事务内保存文档 FAILED、清分块和幂等 FAILED / 错误码，并尝试清文件及向量。不自动重新执行。
5. 并发冲突由数据库唯一约束裁决；捕获 DuplicateKeyException 的位置在创建事务之外，失败事务回滚后再查询胜出者记录，避免在已失败事务里继续处理。

文档状态沿用 UPLOADED/PARSED/FAILED；上传操作状态使用 PROCESSING/SUCCEEDED/FAILED，两者职责不同。

## 重复请求响应

| 情况 | 响应 | 是否再写文件/调用 Python |
|---|---|---|
| SUCCEEDED | HTTP200，返回原始 DocumentVO，包含原文档 ID | 否 |
| PROCESSING | HTTP409，1014 UPLOAD_PROCESSING | 否 |
| FAILED | 返回保存的业务错误码及对应错误信息；内部异常统一保存为1015 UPLOAD_FAILED | 否 |
| 同 Key、不同文件名或内容 | HTTP409，1016 IDEMPOTENCY_KEY_CONFLICT | 否 |
| Key 缺失或格式非法 | HTTP400，1017 IDEMPOTENCY_KEY_INVALID | 否 |

FAILED 重复响应使用已保存的错误码，不向客户端暴露异常栈、数据库信息或本地文件路径。原始请求的异常仍按现有异常处理方式返回。

## 边界

- 幂等成功回放是原上传结果快照。后来文档重建或删除，原 Key 仍返回原上传结果，不表示文档当前仍存在，也不会重新创建。当前状态应查询文档详情。
- 幂等记录不随文档删除而删除，也没有自动过期。否则同 Key 可能被当作新请求再次执行。
- 未通过参数校验、文件读取失败、初始事务未成功提交时，不会保留幂等记录；事务回滚后原 Key 可再次提交。
- 进程崩溃或数据库不可用，可能留下 PROCESSING，重复请求只提示处理中，不自动抢占。需人工核对数据库、文件和向量后恢复。
- 幂等保证同用户同 Key 的请求不重复启动外部工作，不是跨 MySQL、文件和 Python 的原子事务；超时后 Python 仍可能继续执行，仍需已有失败清理机制。
- FAILED 不自动重新执行；确认旧数据清理完毕后，如决定再次上传，应创建新操作和新 Key。

## 升级和验证

新库用 docs/sql/init.sql。已有库执行一次 [升级 SQL](sql/migrate-upload-idempotency.sql)，再重启 Java 后端。2026-10-31 已为本机数据库执行升级，请勿重复建表。

```powershell
mvn -f backend/pom.xml '-Djwt.secret=integration-test-secret-0123456789abcdef' '-Ddocument.mysql-test=true' '-Drag.cache.redis-test=true' test
```

2026-10-31：Java 61 项通过，0失败、0错误、0跳过。新增真实 MySQL 测试验证：并发只创建一个文档、处理中不重复调用 AI、成功回放、失败不重试、用户隔离、不同 Key 独立、文件指纹冲突，以及创建/完成事务的整体回滚。Python 调用在幂等集成测试中使用替身，不调用付费模型；数据库、文件、事务和上传服务使用真实实现。
