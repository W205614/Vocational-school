# 账户验证、管理员 MFA 与隐私申请

本批提供可构建的账户与隐私工作流。公开注册与找回默认关闭，未配置通知渠道时拒绝验证码申请；没有执行真实短信发送、云账号部署或支付验收。

旧 `PUT /students/password` 及其网关别名已退役；登录用户通过个人设置验证原密码后改密，找回统一使用下述受控流程。登录凭据验证携带同一用户记录的身份版本，创建会话前发现版本或角色变化即拒绝本次登录。凭据与 MFA 验证通过后先持久记录认证受理，再签发会话／令牌；该记录不等同于客户端已收到登录响应，持久记录失败时不签发凭据。

## 公开学生账户

网关只开放以下固定入口，内部用户服务仍要求服务身份：

| 接口 | 输入与行为 |
| --- | --- |
| `GET /api/v2/public/accounts/privacy` | 当前规则版本、运营方政策地址、自助服务开关和通知模式 |
| `POST /api/v2/public/accounts/verification-codes` | `{phone,purpose}`，purpose 为 `REGISTER` 或 `RECOVER`；202 表示持久通知已受理，不能解释为短信已送达 |
| `POST /api/v2/public/accounts/registration` | `{phone,code,password,policyVersion,consent}`；验证码单次有效，必须接受当前规则版本 |
| `POST /api/v2/public/accounts/password-recovery` | `{phone,code,newPassword}`；只更新可用学生账户，不恢复被禁用或注销的账户，也不签发登录会话 |

配置位于 user 模块：

```yaml
tj:
  accounts:
    public:
      enabled: false
      sms-delivery-mode: unconfigured # 明确配置后选 provider；simulated 仅用于显式演示
    privacy:
      policy-version: '2026-10-26'
      policy-url: /privacy
      retention-review-days: 30
```

短信沿用现有持久 Outbox 和供应商适配。仅填 `provider` 不代表完成供应商配置或送达验证；生产部署门禁还需检查实际短信适配。验证码由安全随机数生成，6 位、5 分钟，按手机号和用途绑定，消费通过 Redis Lua 原子执行，最多尝试5次；同一手机号60秒冷却，每小时最多10次，同一可信客户端IP每小时最多20次。Redis 仅保存带随机盐的摘要；通知业务持久队列内需包含待发送验证码，须限制其读取权限和保留期限。

## 管理员验证器

TOTP 采用 [RFC 6238](https://www.rfc-editor.org/rfc/rfc6238) 的 SHA-1、6 位、30 秒参数，接受相邻一个时间步；数据库行锁保证多副本不能重复使用已消费的时间步。密钥采用 AES-256-GCM 加密，账户ID作为附加认证数据。通过 `TJ_MFA_ENCRYPTION_KEY` 提供 base64 编码的32字节外部密钥，或者显式设置 `tj.auth.mfa.encryption-key`。密钥不能进入Git、普通日志或公开CI附件。

`tj.auth.mfa.required=true` 时缺少加密密钥会阻止启动，管理员登录必须已启用验证器并提供 `mfaCode`。默认本机部署未配置密钥且未强制MFA时保持既有登录行为。教师不因管理员MFA策略被锁定。

管理员登录表单支持首次设置：先填写已有账号密码，调用 `POST /api/v2/auth/accounts/mfa/enroll`，将当次返回密钥添加到验证器，再调用 `/confirm`，请求均使用 `{type:1,username,password,mfaCode?}`。设置过程不发业务令牌；待设置密钥10分钟到期，已启用密钥不能通过密码单因素重发。确认会撤销该账号既有会话，且当前验证码已被消费，登录需要下一枚验证码。

启用强制策略前应由受控管理员完成设置。即使有旧会话，强制策略下也不能跳过MFA刷新或校验。遗失验证器没有公开的密码单因素重置后门，应执行双人核验、撤销会话、保留审计，再在受控维护窗口重新登记。加密密钥轮换使用版本化密文、显式旧解密键和受控重加密；JWT 则使用公钥集合与先分发后签发的顺序。配置和维护边界见[认证密钥维护](key-maintenance.md)，不能仅覆盖环境变量后宣称完成轮换。

## 隐私权利与责任边界

学生个人设置提供账户资料下载与注销申请，均再次验证当前密码。

- `POST /api/v2/services/user/account-privacy/export` 输入 `{password}`。即时下载明确标注 `ACCOUNT_PROFILE_AND_CONSENT`，不包含密码、令牌或验证码，同时登记 `ALL_DOMAINS / EXPORT / PENDING_REVIEW` 申请，返回需运营处理的交易、学习和安全域。
- `POST /api/v2/services/user/account-privacy/deletion` 输入 `{password,confirmation:"注销账户"}`。在同一本地事务内禁用账户、使原凭据失效并清除公开资料，记录保留状态和 `ALL_DOMAINS / DELETE / PENDING_REVIEW` 申请，返回可下载回执。不会删除订单、支付、考试或审计历史，也不会自动承诺完成跨域永久清理。
- `POST /api/v2/services/user/account-privacy/consent` 输入 `{policyVersion}`，仅接受当前政策版本并幂等记录。
- 管理台“隐私权利申请”使用 `GET /api/v2/admin/user/account-privacy/requests` 查询最多100条待处理记录；`PUT .../requests/{id}` 输入 `{version,status,reason,evidenceReference}`。处理状态可为 `IN_PROGRESS`、`COMPLETED` 或 `REJECTED`，按版本更新；完成须记录导出交付或按保留规则清理的证据引用。

身份服务无法绕过其他模块的权限直接导出或删除其数据库。完整导出交付、保留期限决策和跨域删除仍由运营按发布的规则审核；新页面与记录明确保留这条边界。`retention-review-days` 是审核时间提示，不是法律规定或自动清理期限。生产运营前必须发布联系人、适用保留规则和完整隐私政策，不能把仓库中的通用说明当作已完成合规审查。

新迁移为 `tj_auth/V005` 和 `tj_user/V004`，必须通过现有校验和迁移工具前向应用，不修改历史迁移。

## 服务身份与接口权限

生产的 `service-tokens` 模式在验明调用方令牌后，还通过 `ServiceRpcPolicy` 限制方法与模块路径。非网关工作负载只可调用已审阅的RPC清单：登录及初始改密仅身份组，支付与优惠券状态变更仅交易组，题目关联写入仅教育组。服务令牌本身不能赋予后台接口权限；即使另一个工作负载伪造管理员用户头，也会在进入控制器前被拒绝。

网关使用单独的已审阅外部API目标清单，私有RPC只例外开放认证公钥读取和会话校验。指标调用方仅允许指标抓取，公开健康检查保留给探针。新增Feign方法或路由不会在运行时自动取得权限，须更新并审阅静态清单及负例测试。共享令牌的原本机模式保留既有兼容行为，不作为生产安全隔离证据。

两份静态清单的 `Source-Manifest-SHA256` 注释绑定所依据的Feign接口、直接认证HTTP契约以及网关路由／访问策略源码，计算时只统一换行为LF。`deploy/production/test_rpc_inventory.py` 以只读检查发现漂移：先审阅源码变化、调用方权限和负例，必要时修改静态授权，再手工更新清单头部的指纹；测试不会自动更新指纹或生成新的授权。

认证SDK中两条直接HTTP调用（公钥和会话校验）与Feign一样选择接收方服务凭据，并禁止携带凭据重定向。HTTPS显式使用JDK信任库和主机名校验，覆盖旧Hutool默认信任策略；生产包里的内部HTTP由强制mTLS网格保护。此补偿不等于整个旧Hutool依赖已无漏洞，依赖扫描与后续升级仍需独立维护。

## 私有服务凭据生成与轮换

使用 `deploy/production/service_identities.py` 准备凭据，避免手工把同一全局令牌复制到所有应用。输出必须是显式绝对路径的**新目录**；仓库内只允许根 `.local/` 下的私有目录。工具先设置目录权限再写入密钥（Windows 当前用户与 SYSTEM，其他系统目录0700／文件0600），拒绝覆盖、链接、未知调用方、缺失边、跨边复用及调用方／接收方不匹配。失败的部分输出不自动删除，也不能再次作为输出复用。

```powershell
python deploy/production/service_identities.py generate --output "$PWD\.local\identity-generation-01"
python deploy/production/service_identities.py validate --input "$PWD\.local\identity-generation-01"
python deploy/production/service_identities.py rotate --previous "$PWD\.local\identity-generation-01" --output "$PWD\.local\identity-generation-02"
# 仅在接收方、调用方和监控完成切换并核验后，显式准备退役工件：
python deploy/production/service_identities.py retire --previous "$PWD\.local\identity-generation-02" --output "$PWD\.local\identity-generation-03"
```

每条已审阅的定向服务边使用独立的32字节安全随机密钥；本组RPC使用单独 self edge。当前只读RPC清单确实需要四个业务组互相连接，但仅允许对应方法／路径，绝不因此开放全部后台接口。每个工作负载只接收自己目录中的 `service-identities.json`，对应渲染包的 `<workload>-service-identity` Secret 键；监控仅接收 `observability/prometheus/` 下五个 `<target>-token` 文件，映射到 `/etc/prometheus/tianji/`。worker 当前没有内部RPC与指标抓取边，输出空 keyring。

轮换严格按以下次序发布；脚本只准备本机工件，**不会调用 kubectl、创建云资源或修改运行中的 Secret**：

1. 先发布 `identity-generation-02/receiver-first/<workload>/service-identities.json`：接收方同时接受 `[新密钥,旧密钥]`，所有调用方仍使用旧密钥。核对每个接收方副本已加载。
2. 再发布第二代根目录的各工作负载文件及监控 token 文件：调用方改用新密钥，接收方仍接受新旧两代。验证所有调用方和监控，而非只看健康探针。
3. 保留旧密钥直到滚动切换和观察窗口结束，再显式准备并发布第三代工件，接收方才移除旧密钥。工具拒绝直接对尚保留旧密钥的轮换包再次轮换；不自动丢弃旧入站密钥。

退役前回退应重新发布第二代的 `receiver-first` 文件（旧出站、新旧双入站），不能在部分调用方已使用新密钥时发布第一代的单入站完整 keyring。退役后不能直接恢复旧调用方，须先恢复经审阅的双入站接收方阶段。保留并保护全部代际工件。`ROTATION_PREPARED`／`RETIREMENT_PREPARED` 仅代表工件生成，不能证明线上发布、验证或密钥撤销已完成。已审阅策略拓扑改变时，旧代际不能被隐式轮换到另一张凭据图，需重新审阅迁移方案。
