# Sesame-TK 授权与用户门户设计草案

状态：未来设计，不是当前 Android runtime 契约。

当前客户端使用移动授权库提供的设备绑定 JWS 租约，并通过 LibXposed remote preferences 同步授权状态。Android 端不应恢复用户名/密码 UI、API 或旧的 `SesameCredentialsStore` 方案。

## 目标

- 未完成注册或授权时不启动核心自动化任务。
- 用户可以凭一次性用户密钥进入自助页面查看自己的账号和设备数据。
- 用户身份、设备密钥、支付宝账号匿名标识和运行租约分层保存。
- 管理员可以逐用户管理 Sesame 应用策略、账号状态和设备，并留下审计记录。

## 身份模型

### 用户账号

服务端生成不可预测的 `account_id`，保存身份哈希、必要时的加密身份资料、时间字段和全局状态 `active`、`suspended` 或 `deleted`。首期可以使用 Telegram 作为注册身份，但不改变账号模型。

### 用户密钥

注册成功时生成高熵 `user_key`，服务端只保存哈希，明文只在成功响应中显示一次。登录后换发短期 HttpOnly 会话 Cookie；密钥不放 URL、日志或 Telegram 明文消息中。管理员只能吊销或重新生成，不能读取明文。

### 设备密钥

客户端在 Android Keystore 生成设备密钥对，私钥不离开 Keystore。服务端保存公钥和指纹，激活、续期和撤销使用 DPoP 或等价的请求签名证明设备持有私钥。用户密钥泄露不能直接变成设备私钥泄露。

### 支付宝账号

服务端只保存加密或不可逆匿名标识，不上传原始支付宝 ID、姓名或昵称，除非未来另有明确隐私批准。

## 用户流程

1. 客户端生成设备密钥并创建注册挑战。
2. 用户在浏览器完成身份绑定和频道成员校验。
3. 服务端返回 `account_id`、一次性 `user_key` 和短期注册会话。
4. 客户端加密保存本地授权资料，核心任务只在有效租约下启动。
5. 日常运行通过设备私钥续期短期租约；临时断网使用短期缓存租约，不在支付宝主线程同步联网。
6. 用户页面只展示本人状态、设备、匿名账号数量和本人授权事件。

## 服务端边界

首期需要独立的 Sesame 应用策略，不能用全局账号状态代替应用级加白或加黑。建议的资源包括：

- `account_credentials`：用户密钥哈希、版本和吊销时间；
- `account_app_policies`：`pending/allow/deny`、原因和有效期；
- `user_sessions`：哈希后的会话 ID、账号和有效期；
- `account_activity`：账号、应用、设备、动作元数据和时间。

删除采用软删除：账号不可登录、设备租约立即失效，物理清理作为独立的受限操作并写审计。

建议 API：

```text
POST /v1/sesame/register/challenge
POST /v1/sesame/register/telegram
POST /v1/sesame/session/login
POST /v1/sesame/session/logout
POST /v1/sesame/user-key/rotate
GET  /v1/sesame/me
GET  /v1/sesame/me/devices
GET  /v1/sesame/me/activity
POST /v1/sesame/device/renew
```

管理员接口必须独立命名空间并继续要求管理员会话、CSRF 和审计：用户检索、查看、策略变更、状态变更、删除/恢复、设备撤销和密钥轮换。首期不开放无审计批量封禁或批量删除。

## Android 侧边界

- 认证层独立为授权 repository/state，不放进 Hook 热路径。
- 负责 Keystore、注册挑战、浏览器回流、租约缓存/续期/吊销和可观测错误状态。
- `pending`、`deny`、`suspended`、`deleted` 或过期超过宽限期时，不创建主任务和调度器。
- 本地调试服务始终只监听回环地址；未配置 token 时不启动，且与正式授权服务使用不同凭据和端口。
- 调试 RPC 不得修改认证状态；请求体、频率、方法和异常输出都应受限。

## 验收重点

- 未授权时核心任务不会启动，断网不会阻塞支付宝主线程。
- 用户只能查看本人数据；用户密钥只显示一次，服务端不保存明文。
- 账号策略和设备撤销会阻断后续续期与任务启动。
- 管理员入口受 Cloudflare Access + Passkey 保护，危险操作要求原因并写审计。
- Android 构建、单测、lint 和真机注册/登录/撤销/断网路径均有证据。
- APK 不包含服务端密钥、固定用户密钥或调试 token。

## 暂不做

- 不上传原始支付宝身份信息。
- 不在客户端内置服务端签名密钥。
- 不通过 Telegram 明文传输用户密钥。
- 不引入第二套无审计的 WebUI 或数据库。
