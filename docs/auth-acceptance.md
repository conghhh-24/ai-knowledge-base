# 鉴权接口验收记录

## 环境

- 后端地址：`http://localhost:8080`
- MySQL：`localhost:3307/ai_knowledge_base`
- Redis：`localhost:6379`
- `JWT_SECRET`：已设置，且长度满足 HS256 密钥要求
- 验收日期：

## 场景记录

| 场景 | 请求 | 预期 | 实际状态 | 结果 |
|---|---|---|---|---|
| 注册成功 | `POST /api/auth/register` | HTTP 200，`code=0`，返回用户信息 | | |
| 重复用户名 | 同上，使用已存在用户名 | HTTP 409，返回用户名已存在 | | |
| 正确登录 | `POST /api/auth/login` | HTTP 200，`code=0`，返回 token | | |
| 错误密码 | 同上，密码错误 | HTTP 401，返回用户名或密码错误 | | |
| 无 Token 访问当前用户 | `GET /api/auth/me` | HTTP 401，返回 Token 缺失 | | |
| 合法 Token 访问当前用户 | 带 `Authorization: Bearer <token>` | HTTP 200，返回当前用户 | | |
| 伪造 Token | 带非法 Token | HTTP 401，返回 Token 无效 | | |
| 过期 Token | 带已过期 Token | HTTP 401，返回 Token 已过期 | | |

## 数据库检查

注册后执行：

```sql
SELECT username, password_hash, status
FROM user
WHERE username = '<测试用户名>';
```

验收标准：`password_hash` 不等于注册时提交的明文密码，并且以 BCrypt 哈希格式保存。

## 结论

- Sprint 1 是否通过：
- 未通过项及原因：
