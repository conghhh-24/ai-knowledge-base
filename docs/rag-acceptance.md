# RAG 验收记录

## 验收日期

2026-10-28

## 验收目标

确认文档能够从上传开始，经过分块、Embedding、向量检索和 LLM 问答，最终返回答案及来源。

## 验收链路

```text
Java 上传文档
→ document_chunk
→ FastAPI /vectors/upsert
→ Chroma 向量索引
→ FastAPI /chat 检索 TopK
→ LLM 根据上下文生成答案
→ Java 返回 answer + sources
```

## 验收步骤

1. 在 Apifox 登录，获取 JWT。
2. 上传包含以下内容的 TXT 文档：

   ```text
   Redis 常用于缓存热点数据，减少重复访问数据库。
   ```

3. 记录上传响应中的 `data.id` 作为 `document_id`。
4. 调用 `POST /api/ai/chat`：

   ```json
   {
     "question": "Redis 有什么作用？",
     "document_id": 1,
     "top_k": 3
   }
   ```

5. 检查答案和 `sources` 是否来自上传文档。

## 实际结果

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "answer": "根据文档片段，Redis 常用于缓存热点数据，减少重复访问数据库。",
    "sources": [
      {
        "text": "Redis 常用于缓存热点数据，减少重复访问数据库。",
        "distance": 0.376674085855484,
        "chunk_index": 0
      }
    ]
  }
}
```

## 验收结论

已通过。当前 RAG 能够完成：

- 上传后自动保存 `document_chunk`；
- 自动调用 FastAPI 建立向量索引；
- 根据 `document_id` 检索相关 chunk；
- 将检索结果交给 LLM；
- 返回 `answer`、`text`、`chunk_index` 和 `distance`；
- Java 侧校验当前用户对文档的访问权限。

## 异常验证

- 未配置 `LLM_API_KEY` 时返回 Mock 答案，配置后恢复真实 LLM 回答。
- 文档内容为 `erfvdsc`、问题为“Redis 有什么作用？”时，返回的 source 也是 `erfvdsc`，说明系统会暴露检索结果与问题不相关的情况，没有伪造文档来源。

## 当前未完成

- Redis RAG 答案缓存的真实环境验收及缓存失效验证；
- 20–30 个问题的系统化 RAG 评测；
- Docker 完整编排和自动化集成测试。
