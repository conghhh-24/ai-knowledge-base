# RAG 功能完善

## 升级

新数据库使用 docs/sql/init.sql。已有数据库只执行一次 docs/sql/migrate-rag-improvements.sql，新增 document.index_version（初始1）、chat_message.sources（JSON）和文档列表索引；然后重启 Java 后端和 FastAPI。不需要删除已有数据。旧历史无法补回当时未保存的来源，查询返回 sources: []。

## 空检索与历史

指定 document_id，但检索结果为空，直接返回 answer="文档中没有相关信息"、sources=[]，不调用 LLM。不指定文档的普通聊天仍正常调用模型。此规则只处理空检索，不代表非空片段必然相关。

新问答把返回的 sources 原样保存到历史，包含 text、chunk_index、distance；缓存命中也保存来源。删除、重建文档不会改写既有历史引用。历史始终按当前登录用户过滤。

## 索引版本与并发

文档 index_version 初始为1，每次开始重建原子加1并设置 INDEXING，失败保留新版本且状态为 FAILED，成功变为 PARSED。重建使用已有分块，不重新按新参数切分。

答案缓存键：rag:answer:v2:{userId}:{documentId}:{indexVersion}:{questionHash}。问答开始记录版本，返回前再次检查归属、PARSED 和版本。重建期间的旧请求即使在最后检查后才写缓存，也只会写入旧版本键，后续新版本请求不会命中；旧键由清理或 TTL 回收。升级前 v1 缓存不再读取，等待原 TTL 到期。

重建和删除共用 MySQL GET_LOCK 会话锁，等待时间为0，适用于连接同一 MySQL 的多个 Java 实例。相同文档操作冲突返回 HTTP409 / DOCUMENT_BUSY；其他文档可操作。删除持锁到数据库事务完成，异常也释放；没有固定锁租期导致长任务中途自动解锁的问题。锁使用独立数据库连接，小型项目支持，连接池耗尽仍可能等待。上传中 UPLOADED 文档拒绝删除；崩溃留下的 INDEXING 在无持锁任务后可删除，但不能直接问答。

这不是跨数据库和 Python 的事务。进程崩溃、数据库断连、客户端超时但 Python 仍继续执行，仍可能需要人工清理；尚未加入后台恢复和 Python 端的版本隔离。当前保证正常运行期间的 Java 操作互斥及缓存版本隔离。

## 超时和支持范围

| 环境变量 | 默认 | 含义 |
|---|---|---|
| AI_CONNECT_TIMEOUT | 3s | Java 连接 FastAPI 的超时 |
| AI_CHAT_TIMEOUT | 30s | Java 等待一次问答响应 |
| AI_INDEX_TIMEOUT | 60s | Java 等待一次索引批次或向量删除 |
| LLM_TIMEOUT_SECONDS | 10 | Python 调用模型的超时秒数 |
| EMBEDDING_TIMEOUT_SECONDS | 45 | Python 调用向量模型的超时秒数 |

Java 配置必须为正数。Python 的模型及向量客户端关闭 SDK 自动重试，避免隐藏重试放大等待。Java 检测到传输超时返回 HTTP504 / AI_SERVICE_TIMEOUT，连接不可用返回503，下游处理失败返回502。Python 先返回错误时仍按下游错误处理，不保证所有下游模型超时都表现为504。

当前支持 UTF-8 TXT/Markdown，最大50MB，上传和重建同步完成，每批20个分块。60秒是单批等待时间，不是整个上传的总时限；50MB只是文件大小上限，不保证接近上限的文档都能及时完成。演示优先使用较小文档。上传失败保留 FAILED，清理文件和分块，需要重新上传；重建失败保留分块，可以再次重建。没有后台任务接口或上传进度查询。

## 缓存观察

后端 INFO 日志包含 RAG cache=HIT / MISS / BYPASS，以及 userId、documentId、indexVersion，不记录问题、答案或 token。HIT 表示跳过本次 AI 调用；MISS 也包括 Redis 故障回退，不能等同于模型成功次数。BYPASS 表示未指定文档，不使用答案缓存。可按日志统计命中率 HIT/(HIT+MISS)，估算缓存跳过的调用数量。

## 文档列表分页

GET /api/documents?page=1&pageSize=20，需要登录。page>=1，pageSize 为1–100，过大偏移返回参数错误。响应 data 从数组改成：

```json
{"total": 1, "page": 1, "pageSize": 20, "items": [{"id": 1, "fileName": "demo.txt", "status": "PARSED", "indexVersion": 1}]}
```

按 id 倒序，仅查询当前用户。列表和数量是两个查询，并发上传/删除时可能短暂不一致，不提供跨页快照。详情同样返回 indexVersion。

## 验证

Java：`mvn -f backend/pom.xml '-Djwt.secret=integration-test-secret-0123456789abcdef' '-Ddocument.mysql-test=true' '-Drag.cache.redis-test=true' test`。先升级数据库并启动 MySQL、Redis。

Python：在 ai-service 目录执行 `python -m unittest discover -s tests -v`。测试不调用付费模型。

新增验证涵盖空检索不调用模型、缓存版本隔离、生成中重建阻止历史及缓存写入、独立问答与索引超时、真实 MySQL 锁互斥/释放、来源 JSON 往返及文档分页归属。

2026-10-31 验证结果：Java 52 项全部通过（含真实 MySQL、Redis，0 跳过）；Python 3 项全部通过。已对本机现有数据库执行本次升级 SQL，不应重复执行。当前运行服务需要重启加载新代码；本次未重新调用付费模型。
