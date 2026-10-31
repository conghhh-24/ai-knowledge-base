# 文档状态与失败清理

## 分块配置

`rag.chunk-size` 默认500，`rag.chunk-overlap` 默认50，可通过 `RAG_CHUNK_SIZE`、`RAG_CHUNK_OVERLAP` 环境变量覆盖，修改后重启后端。要求大小大于0、重叠大于等于0且小于大小，否则启动失败。只影响新上传文档；重建索引仍使用已有分块。

## 上传

先独立提交 UPLOADED 记录，再保存文件、解析、写向量。成功后在另一个事务内一起保存分块和 PARSED 状态。失败时独立提交 FAILED，清理分块、文件和可能部分写入的向量；保留文档记录便于用户在列表看到失败。参数校验失败或数据库最初无法创建记录时，不能保留 FAILED 记录。

`DocumentUploadState` 是单独的 Spring 服务，其 REQUIRES_NEW 表示单独提交一笔数据库事务，避免上传异常把失败记录一起回滚。

## 删除和重建

新增内部接口 `DELETE /vectors/{document_id}`，只删除对应文档向量，重复删除成功返回204，删除不需要 Embedding 密钥。Python 服务仍是内部可信服务，不应直接暴露到公网，Java 负责用户归属校验。

删除先校验归属，独立提交 DELETING，阻止继续问答；再删除向量和文件，最后事务删除分块及文档。失败保留 DELETING，可重复调用原删除接口；数据库提交后清答案缓存。

重建先清缓存，标记 FAILED 以阻止问答，再清除旧向量并按已有分块重新写入；写入失败再次尝试清向量并清缓存。全部成功后恢复 PARSED。失败重建保留数据库分块，可再次调用重建接口；上传失败没有分块时需要重新上传。

问答在读答案缓存前检查 PARSED，其他状态返回 DOCUMENT_NOT_READY（HTTP 409）。

## 边界与验证

这不是跨 MySQL、磁盘、Chroma 的原子事务。外部服务不可用、数据库不可用、进程崩溃或超时请求在服务端延迟完成，都可能需要人工重试清理；失败清理异常附加到原异常并记录日志。尚未加入后台自动重试、并发版本控制或崩溃恢复。

Java 测试覆盖配置、解析失败、部分向量失败、数据库完成失败、清理异常、删除失败与权限。Python 使用隔离的真实 Chroma 内存集合验证删除范围、重复删除和 FastAPI 返回204，不调用付费模型。

MySQL事务验证已通过真实数据库，失败上传状态和分块回滚均已验证。可重复运行：

```powershell
mvn -f backend/pom.xml '-Djwt.secret=document-state-test-only-secret-0123456789abcdef' '-Ddocument.mysql-test=true' test
```

Python 测试在 ai-service 目录执行 `python -m unittest discover -s tests -v`。

2026-10-31 端到端测试还验证了上传状态为 `PARSED`、重建索引后问答成功、删除后文档查询返回404。
