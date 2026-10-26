# 独立商业部署包

此目录与本机 `deploy/compact` 分开。当前交付为可渲染的配置、应用安全约束、生产镜像配方、验收工具和安全门禁，**没有创建云资源，也没有完成供应商、容量、容灾或 SLA 验收**。示例输入故意无法部署；缺失信息会阻止渲染。

## 前置资源

使用至少两个可用区、支持拓扑标签和 NetworkPolicy 的 Kubernetes 集群（`minDomains` 需要 Kubernetes 1.30+）；已安装使用 CNI 模式的 Istio、OpenTelemetry Operator，入口前配置云 WAF 与负载均衡。restricted Pod 安全策略不允许依靠具备 NET_ADMIN 的 istio-init 容器配置网络，因此必须先配置 Istio CNI。必须核对实际注入的 sidecar/agent，不能仅以命名空间标签当作加密或追踪证据。入口 TLS Secret 位于 Istio ingress gateway 所在命名空间，其证书覆盖学生、管理员两个域名。

入口必须根据实际 WAF／负载均衡可信跳数净化客户端转发信息，向应用只提供单个真实客户端 IP 和 `https` 协议；`trusted_proxy_addresses` 应指向能解析实际 ingress pod IP 的 headless peer DNS／明确 IP，普通 Service 的 VIP 不等于连接来源。上线验收用两个独立客户端和伪造转发头检查 IP 限额隔离、协议及 Secure Cookie，不能靠一个布尔值证明入口正确。

外部 MySQL 使用托管高可用 DNS、独立库 DML 账号及 `sslMode=VERIFY_IDENTITY`；Redis 使用具有 TLS、ACL 和自动主节点切换能力的托管代理端点；本轮不依赖 Redis Cluster 跨槽语义。RabbitMQ 使用新的生产 vhost（名称等于应用 namespace），**在首次声明前设置 default queue type 为 quorum**，配置跨可用区多数派副本及各应用最小权限。不能在原 classic 队列上修改类型，也不能把 broker 集群数量当作消息复制证明。搜索使用 HTTPS 和独立账号，允许辅助搜索降级。

服务端 CA 必须被应用镜像 JDK 信任；私有 CA 需在受审镜像中导入专用 truststore，或通过只读 Secret 挂载并设置 JVM truststore。不得关闭证书或主机名验证。没有选定实际云厂商端点、IAM 和 CA 时不要部署。

COS 桶与对象默认私有，API 和 worker 使用不同 IAM 身份。API 仅允许所需隔离上传、元数据读取和已处理对象读取，以及 `covers/<sha256>.png|jpg` 的私有写入、读取和元数据查询；worker 仅允许隔离输入读取、已处理输出写入、受控清理，不授予 covers 权限。两者需要读取桶 ACL/policy 供启动检查；配置桶 CORS 仅允许两个门户的 HTTPS origin、必要的 PUT/HEAD 及已签名头。封面通过既有站内 course-covers 路径代理，视频 `CLOUD_COS` 每次媒体请求复核权益，不直接返回公开 COS/VOD URL。云 VOD CDN/PSIGN 无法保证即时撤权，因此本轮不启用 VOD 分发。

worker 使用 `tj-media/docker/Dockerfile.worker`，挂载维护中的 ClamAV signatures PVC 为只读；该卷必须能跨可用区读取，文件及祖先目录必须实际允许 UID/GID10001 读取／遍历（不能仅依赖 CSI 是否实现 fsGroup）。镜像中的扫描工具不代表存在新鲜病毒库，需独立签名更新服务和过期告警；没有签名或扫描失败不得进入 READY。工作盘为有界 `emptyDir`，不挂载本机媒资目录。IAM 的 HEAD／GET／PUT／DELETE 需限制隔离与 READY 前缀，若私有 ACL 请求需要 PutObjectACL 权限应按最小范围单独授权，不能为解决403开放公有 ACL。

## 渲染和发布

### 托管 MySQL 初始化、升级与首个管理员

数据库操作使用独立迁移账号，不能使用应用 DML 账号、`root` 或通用 `admin`。该账号需具备十二个 `tj_*` 库的建库、DDL、触发器及迁移所需 DML 权限；启动检查要求每个库都有直接 CREATE／ALTER／TRIGGER 授权，不凭角色名称推断权限。发布后不得把迁移凭据挂载给应用。准备可信 MySQL 8 客户端和仅当前操作员／SYSTEM／Administrators 可读的配置文件；Linux 权限为 `0600`。Windows 可用 `database.protect()` 为新建的专用文件收紧 ACL。先运行 `python -m pip install -r deploy/production/requirements.txt` 安装声明的依赖，其中 `bcrypt` 用于首个管理员密码哈希，Windows 的 `pywin32` 用于权限检查；安装依赖不接触云环境。配置示意如下，所有值由生产环境提供，密码仅存于该受保护文件：

```ini
[client]
host=<实际托管MySQL的DNS主机名>
port=3306
user=ddl_tianji
password=<独立迁移凭据>
ssl-ca=E:/secure/mysql-provider-ca.pem
ssl-mode=VERIFY_IDENTITY
```

`database.py` 要求 `--expected-host` 与文件中的 DNS 完全一致，并在命令行强制 TCP、可信 CA 和 `VERIFY_IDENTITY`；不把密码放入参数或环境变量。它检查实际连接身份、DDL 权限和加密状态，在同一连接持有迁移锁，断线后停止，禁止自动重连重放。

```powershell
# 仅新环境：任何 tj_ 库存在都会拒绝；不会覆盖或删除已有库。
python deploy/production/database.py init --defaults-extra-file E:/secure/tianji-migration.cnf --expected-host <实际托管MySQL的DNS主机名> --namespace <部署namespace> --result E:/secure/init-result.json

# 现有环境：先取得可恢复备份，再执行向前迁移。
python deploy/production/database.py migrate --defaults-extra-file E:/secure/tianji-migration.cnf --expected-host <实际托管MySQL的DNS主机名> --namespace <部署namespace> --result E:/secure/migrate-result.json
```

初始化只复用公开 `schema.sql` 与经过白名单检查的 `catalog.sql`，去掉 dump 中的 DROP 和表锁；保留角色、菜单、权限和历史迁移校验记录，不导入学生、教师、演示订单、模拟资金或课程素材。随后调用原有迁移器的重复数据预检与校验和迁移。已有库只能走 `migrate`；发现重复数据、历史文件校验和变化或部分 DDL 失败时停下检查，不回填伪造校验、不自动回滚、不重新导入旧 SQL。托管服务静态加密、备份与跨区能力仍需单独配置及验收。

首个管理员另外显式创建。准备同样受保护的 JSON 文件，字段为 `namespace`、`username`、`phone`、`name`、`role`；`role` 必须为 `admin`，用户名须以 namespace 将连字符改为下划线后的前缀加 `_` 开头，且总长不超过 32 字符。管理员手机号需符合现有 11 位手机号字段。使用真实操作员资料，不使用演示账号。

```powershell
python deploy/production/database.py bootstrap-admin --defaults-extra-file E:/secure/tianji-migration.cnf --expected-host <实际托管MySQL的DNS主机名> --namespace <部署namespace> --admin-spec E:/secure/administrator.json --receipt E:/secure/initial-admin-receipt.json --result E:/secure/bootstrap-result.json
```

工具只创建一个随机临时密码的员工管理员，24 小时有效，强制首次改密。一次性回执先受保护地持久化，再提交用户与角色关联；有活跃管理员、重名账号或已有回执时拒绝执行，不重置已有密码。先通过管理员端完成初始改密，再用自设密码登记／确认 MFA，然后登录业务工作台；此流程不生成绕过 MFA 的业务会话。回执如停留 `PREPARED_NOT_APPLIED`，先核对数据库状态，禁止当作失败后重新重置账号。结果只记录源码指纹、迁移身份边界和查询计数，不记录 SQL 或凭据；所有结果文件要求新路径，不覆盖旧证据。

应用 DML 账号由云数据库的独立账号管理流程创建，`database.py` 不要求 CREATE USER／GRANT 权限。每个应用仅访问自己的库；support 中各媒体请求使用 `app_media`，独立 worker 使用另一个 `app_media_worker` 身份与密码。worker 的 Secret 同样使用 `MEDIA_DB_USERNAME`／`MEDIA_DB_PASSWORD` 两个键，但值必须独立，不能复制 API Secret。已创建并要求 TLS 的 worker 账号只需下列表级权限；`<私有工作负载来源>` 由实际数据库代理／网络来源确定，不使用开放来源通配符。创建账号时通过受保护文件或供应商 Secret 流程传入随机密码，禁止粘贴到命令参数或公共 SQL 历史中。

```sql
GRANT SELECT, UPDATE ON tj_media.cloud_media_upload TO 'app_media_worker'@'<私有工作负载来源>';
GRANT INSERT ON tj_media.media TO 'app_media_worker'@'<私有工作负载来源>';
```

上述 worker 权限与当前查询对应：领取／续租／结束任务、写入已处理媒资；没有 DDL、账号管理、跨库或删除权限。用应用和 worker 各自身份做一次拒绝跨库访问的上线验收。源文件／证书身份验证、真实托管连接和账号权限尚须在目标环境执行，离线工具或本机合成库测试不能替代这些证据。

准备真实输入和不可变镜像引用，输入文件只填写身份与地址，密钥由受保护的 Secret 管理。Web 镜像使用本目录 `Dockerfile.web` 与 `nginx.conf.template`，不要直接替换为需要 root 的演示镜像。`NGINX_IMAGE` 构建参数必须是经过扫描的固定 digest。应用和 worker 的 JRE 配方固定到官方 Temurin 21 Noble 的不可变镜像索引，避免通用 `21-jre` 标签隐式更换基础发行版；升级须重新扫描实际镜像并验证原生媒体工具。该版本的 [官方 Dockerfile](https://github.com/adoptium/containers/blob/511f9356dc4d50932a0a5f8cfb0f87ed1aef4f07/21/jre/ubuntu/noble/Dockerfile) 使用 Ubuntu 24.04。

```powershell
python -m pip install -r deploy/production/requirements.txt
python deploy/production/render.py --settings <受保护的生产配置.yml> --output <新的输出目录>
kubectl --context <候选集群> apply --dry-run=server -f <输出目录>/resources.yaml
```

渲染器不运行 `kubectl`，输出 `resources.yaml`、必需 Secret 键清单、配置指纹和原生监控配置片段。`RENDERED_NOT_DEPLOYED` 不表示已经部署。实际发布前先创建清单中的 Secret、身份、签名、病毒库和外部服务；完成兼容迁移和备份后再 apply，按准确 commit/digest 灰度引流。迁移只能新增向前版本；应用回退保留已产生的数据，禁止用旧 SQL 覆盖真实交易。

每个应用的 `*-service-identity` Secret 只含该应用的 `service-identities.json`：

```json
{"outbound":{"identity":"<独立边密钥，至少32字符>"},"inbound":{"gateway":["<当前边密钥>","<轮换前边密钥>"]}}
```

调用方向分别分配密钥；同组模块的 loopback 调用使用自己的 self edge。接收方只持有所需 inbound，发送方只持有所需 outbound，不创建全局令牌。worker 没有内部调用时使用空对象。监控调用身份为 `observability`，每个目标应用持有对应 inbound edge；Prometheus 只持有自己的 outbound edge。认证签名 JKS 只挂载到 identity。MFA 加密键是独立的 base64 32 字节密钥，不与服务令牌或媒资签名键复用。

当前支持在线轮换的是**服务边令牌**：先更新接收方的旧＋新密钥窗口，再切换调用方，确认后退出旧密钥。JWT 签名 JKS／网关单公钥缓存与 MFA AES 主密钥尚不支持通用无停机轮换。不要直接滚动替换这些 Secret；其更换属于受控维护，需要密钥和相关密文数据备份、验证过的回退方案及明确的会话影响。后续 JWT 需补充 kid／JWKS 新旧公钥重叠，MFA 需补充密文密钥版本与重新加密迁移，分别验收后才能承诺相应的在线轮换。服务边令牌轮换通过不能替代这些验收。

生产启用真实账户流程、MFA 与 provider 短信模式。支付宝商户、seller-id、签名公钥、通知域名、回调幂等和金额校验必须分别验收；本轮不执行真实支付。短信“任务已接受”不是“供应商已送达”，需要生产模板、回执和供应商凭据。隐私页面需在配置的 URL 实际发布。

## 监控、备份及验证

将 `prometheus-jobs.yml` 合并到现有 Prometheus 安装：使用逐 pod discovery，避免通过一个 Service 轮询丢失副本指标。监控 ServiceAccount 需在应用 namespace 具备 pods 的 get/list/watch 权限、被纳入 mesh，并挂载独立的身份 edge 文件。将 `alertmanager.yml` 合入真实 Alertmanager，挂载接收端 token，真实发送一次故障与恢复通知。OTLP 使用 Java agent 注入，token 由各应用 Secret 提供；仅配置 OTEL 环境变量不算追踪已生效。

播放 URL 的查询签名是 bearer 凭据。门户 Nginx 仅记录 `$uri`，不记录 `$request`、Referer 或查询串；把 `istio-observability-values.yml` 的安全日志格式合入实际 mesh／入口配置，禁止默认 Envoy PATH 字段泄漏查询。`otlp_endpoint` 必须是自有受控的私有中转 Collector，合并 `otel-sanitize.yml` 的处理器，并在每条 trace pipeline 的所有导出器前启用；处理器使用属性白名单，移除完整 URL、查询、请求头、SQL内容和异常文本，禁用原始 debug exporter／处理器调试日志。`collector_configmap` 的 `collector.yaml` 供预检读取完整生效配置；预检检查实际 pipeline 引用和处理器指纹，而不是仅检查片段存在。发布前用合成签名 canary 验证入口、sidecar、应用和最终追踪后端均无签名原文。未部署和验证处理链时不得将 trace 直接送到第三方后端。

`backup-policy.yml` 是要求，不是已应用的云备份策略：PITR 七天、日备份三十天、异地不可变副本三十天、加密及密钥可恢复。由实际托管服务配置并提供导出记录；恢复演练验证 MySQL 业务事实、私有对象版本和事件收敛。缓存和搜索作为派生数据重建。消息恢复优先保证源业务事实与 Outbox/Inbox 对账，不能用新 MySQL 与旧 MQ 快照盲目拼接。

```powershell
python deploy/production/preflight.py --context <候选集群> --package <输出目录> --result <私有验收目录>/preflight.json
python deploy/production/acceptance.py --package <输出目录> --evidence <私有验收目录>/acceptance.json
```

preflight 只读检查实际副本、跨区分布、镜像、Secret 键、sidecar 和 agent 注入；不证明故障切换或容量。外部验收证据格式与演练步骤见 [商业化运行手册](../../docs/commercial-ha.md)。缺失、失败、跳过、过期、SHA/配置不符或附件哈希变化均使验收 BLOCKED。验收工具只能检查提交证据的一致性，不能自动证明附件来源真实。
