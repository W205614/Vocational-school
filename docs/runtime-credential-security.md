# 运行凭据与 HTTPS

这些工程修改影响新构建的应用，不会自动升级已有容器、迁移已有凭据或启用备份。

刷新接口改为 `POST /api/v2/auth/accounts/refresh`，播放票据改为 POST，收件箱同步改为 `POST /api/v2/services/message/inboxes/query`；旧 GET 不再执行对应写入。刷新 JWT 经 AES-GCM 加密后写入 HttpOnly、SameSite=Lax、Secure Cookie，网关拒绝跨来源刷新。升级后旧格式刷新 Cookie 失效，需要重新登录。Cookie 加密与 JWT 签名轮换独立；默认使用既有私有 `ZXT_INTERNAL_TOKEN`，通过独立 HMAC 上下文派生密钥，也可设置至少 32 字符的 `zxt.auth.cookie.encryption-key`。

## HTTPS

Secure Cookie 要求 HTTPS。Chromium 和 Firefox 对 HTTP 回环地址存在特殊处理，WebKit 不会在该地址发送 Secure Cookie；需要三种浏览器的完整刷新流程时必须配置 HTTPS。不要关闭证书验证、取消 Secure 或跳过 WebKit。

准备覆盖 `localhost` 的证书及匹配私钥，并让客户端通过受控 CA 信任该证书。安装 OpenSSL 后，在首次 `prepare` 之后、`up` 之前执行：

```powershell
python deploy/compact/enable_tls.py --home deploy/compact --certificate C:/private/localhost.crt --private-key C:/private/localhost.key
```

工具为两个现有网页服务添加 TLS 配置与只读证书挂载，保留宿主端口，网页变为 `https://localhost:24500/` 和 `https://localhost:24501/`。内部健康检查继续使用 HTTP，不发送刷新凭据。证书由操作者保留和续期；工具不安装主机信任根。已有实例先核对配置原件、服务中断与回退前提，不能重复首次安装流程。

CI 仅在 GitHub 托管临时 Linux runner 中生成短期 CA，并安装到 runner 系统和浏览器信任库。Linux Firefox 按 [Mozilla 的 NSS 配置方法](https://support.mozilla.org/en-US/kb/setting-certificate-authorities-firefox)使用 `certutil` 准备证书库，再在启动器创建的每个临时 profile 中预置该库；浏览器配置相互隔离，不依赖系统根证书选项或异步策略导入。三个浏览器、Requests、Node 保留证书校验；`ci_tls.py` 拒绝在个人主机或自托管 runner 安装根证书。

独立 API 冒烟使用 `ZXT_ACCEPTANCE_ORIGIN` 选择已配置 HTTPS 的入口。compact 浏览器、课程导入和负载工具根据本部署 `.local/web-tls.json` 选择 HTTPS。Python 客户端可通过 `REQUESTS_CA_BUNDLE` 指定可信 CA 文件，不关闭验证。

## 新建环境的宿主凭据

新基础设施凭据和数据库应用密码分别加密保存于 `.local/environment.enc`、`.local/database-accounts.enc`。`.env` 只保留说明，Spring 配置及 `app.env` 使用环境变量占位符。工具在内存解密后注入子进程。Prometheus 从环境接收令牌，在容器 tmpfs 中建立仅自身可读的文件，不再生成宿主 `observability/internal-token`。

- Windows 默认用当前账号的 DPAPI 包装 Fernet 密钥，包装文件为 `.local/secrets.key.dpapi`。需保留该文件及 Windows 账号恢复能力；复制到其他主机或账号不代表能解密。
- Linux 必须由外部凭据管理系统注入稳定的 `ZXT_SECRET_KEY`（Fernet 的 URL-safe Base64 编码 32 字节密钥）。每次使用同一部署需提供同一密钥；工具不在部署目录保存明文密钥，也不自动生成替代密钥。CI 临时作业环境中的随机密钥不作为长期恢复方案。
- 现有 `.env`、`database-accounts.json` 只读兼容，不自动改写或删除，也不声称已经加密。日常实例迁移是单独的维护操作。

本机受控交互会话可调用 `deploy/compact/secret_store.py` 的 `environment(home)` 读取所需单项凭据；不要把返回字典写入日志、文档或 CI 产物。独立验收环境使用 `python deploy/acceptance/compose.py ...` 包装器解密后调用 Compose，不能仅给原生 Compose 传说明用途的 `.env`。

宿主凭据文件加密不等于 Docker 守护进程元数据、数据库、演示账号清单、备份或整个磁盘加密。Docker 管理权限仍能访问运行环境，演示账号仅用于隔离或本机演示。恢复需保留对应密钥、配置、数据与已验证制品，这些文件不是完整恢复集；既有备份策略保持不变。

## 依赖与构建

JVM 候选改用固定摘要的 Temurin 21 Alpine，保留时区与真实 FFmpeg；worker 保留 ClamAV、curl。所有应用及 worker 继续使用 UNKNOWN/LOW/MEDIUM/HIGH/CRITICAL 全级别门禁，不新增忽略项。

XXL-Job 依赖更新到 3.4.2；外部 XXL 管理端对接不在本机模拟验收中，启用时需核对配套协议和真实调度流程。

MyBatis-Plus 保持兼容的 MyBatis 3.5 API，从校验 SHA-256 的官方源码重建本地限定版本 `3.5.19-zxt.1` 并替换内嵌 OGNL。来源、许可和撤销条件见[补丁构建说明](../third-party/mybatis/SOURCE.md)。这不是上游官方版本，不发布到 Maven Registry，需从根 reactor 构建。
