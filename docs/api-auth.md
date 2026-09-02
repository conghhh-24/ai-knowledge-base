# API Auth

## 1. 目标

这一部分负责用户认证相关能力：

- 用户注册
- 用户登录
- 获取当前登录用户信息
- 后续扩展退出登录

## 2. 接口清单

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/auth/me`
- `POST /api/auth/logout`（预留）

## 3. 注册接口

### 3.1 请求参数

- `username`
- `password`

### 3.2 返回结果

- `id`
- `username`
- `status`

### 3.3 失败情况

- 用户名已存在
- 参数非法
- 密码为空或过短

## 4. 登录接口

### 4.1 请求参数

- `username`
- `password`

### 4.2 返回结果

- `token`
- `userInfo`

### 4.3 失败情况

- 用户名不存在
- 密码错误
- 账号被禁用

## 5. 当前用户接口

### 5.1 返回结果

- `id`
- `username`
- `status`

## 6. 数据对象约定

### 6.1 Request

- `RegisterRequest`
  - username
  - password

- `LoginRequest`
  - username
  - password
### 6.2 VO

- `LoginVO`
  - token
  - userInfo

- `UserVO`
  - id
  - username
  - status

## 7. 字段说明

- `password` 只在请求中传明文
- 数据库保存的是 `password_hash`
- `status` 表示账号状态，默认可用
- `token` 后续由 JWT 生成

## 8. 当前数据库基础

`docs/sql/init.sql` 里已经有 `user` 表，当前字段足够支撑第一版认证流程：

- `id`
- `username`
- `password_hash`
- `status`
- `create_time`
- `update_time`

