# 生产渠道配置、运行验证与备份证明

此手册配合[生产部署说明](../deploy/production/README.md)，提供已实现的配置与验证命令。命令使用独立候选环境及其受保护凭据；云资源由平台准备。支付宝、短信、COS、故障切换及容量按真实环境结果验收。

## 从空环境接入

先完成生产数据库初始化、向前迁移和首个管理员创建，再配置渠道；管理员需完成首次改密和 MFA。保留生产配置、MySQL 迁移账号文件和一次性回执的权限要求。

将 `deploy/production/integrations.example.json` 复制到受保护的配置目录并修改：

| 字段 | 来源与要求 |
| --- | --- |
| `namespace` | 与生产 settings、Kubernetes namespace 一致 |
| `operator_id` | 已创建且启用的管理员 ID；不能使用演示账号或任意数字 |
| `alipay.name/priority/icon` | 门户显示名称、排序及站内图标地址；代码固定使用真实 `aliPay` 实现 |
| `sms.sign_name` | 阿里云审核通过的短信签名 |
| `sms.template_code` | 审核通过的 `SMS_...` 模板编号 |
| `sms.content` | 与供应商模板一致的预览文本，验证码参数只能是一个 `${code}` |

凭据继续通过生产 Secret 提供：支付宝 app-id、seller-id、商户私钥、支付宝公钥，以及阿里云 AccessKey。映射文件不保存密钥。阿里云身份需允许 `dysms:SendSms` 和 `dysms:QuerySendDetails`；验证码为每个接收人保存受理与送达状态，`ACCEPTED` 表示供应商受理，`DELIVERED` 表示回执确认送达。腾讯和 uCloud 的旧占位实现不属于可配置的真实短信渠道。

在仓库根目录执行，`E:/secure` 为操作员已创建并收紧权限的目录，结果路径必须未使用：

```powershell
python deploy/production/integrations.py check --spec E:/secure/integrations.json --result E:/secure/mappings-check.json
python deploy/production/integrations.py apply --spec E:/secure/integrations.json --defaults-extra-file E:/secure/tianji-migration.cnf --expected-host <托管MySQL主机名> --result E:/secure/mappings-apply.json
```

该命令在同一事务内初始化支付宝渠道、阿里云平台、`VERIFY_CODE` 站内模板及供应商模板关联，使用现有数据库部署锁。内容完全相同则返回 `UNCHANGED`；业务编码重复或已有配置冲突时停止且不覆盖。修改现有映射应先按实际供应商配置核对，再由有权限的管理员调整；不要通过删除真实业务表重新初始化。

## 配置检查和连通性

```powershell
python deploy/production/check_integrations.py --settings E:/secure/production.yml --spec E:/secure/integrations.json --package E:/secure/rendered --result E:/secure/integration-preflight.json
python deploy/production/check_integrations.py --settings E:/secure/production.yml --spec E:/secure/integrations.json --package E:/secure/rendered --connectivity --result E:/secure/integration-connectivity.json
```

静态检查核对配置、映射、生产 Secret 键契约与准确候选清单；不访问集群或供应商。`--connectivity` 仅执行公开入口、供应商和支持直接 TLS 握手的托管端点的 DNS/TCP/TLS 检查，证书和主机名必须可信。MySQL 仍由 `database.py` 的实际 MySQL 协议连接验证；权限、短信送达、对象上传和支付结果由后续业务流程分别验证。私有 CA 通过受保护的系统信任配置或 `SSL_CERT_FILE` 提供，不关闭验证。

集群实际 Secret 键、sidecar、agent 和跨区副本继续使用 `preflight.py` 检查。COS 私有 ACL、CORS、隔离／READY 前缀及独立 worker IAM 仍按生产部署手册准备，并用真实上传、扫描、播放和撤权流程核对。

### 搜索索引

生产不调用绑定本机 Compose 的 `prepare_search.py`。创建一个受保护的 JSON 凭据文件，字段为 `endpoint`（实际 HTTPS origin）、`username`、`password`，私有 CA 可加绝对路径 `ca_file`。使用单独的索引维护身份；不得把该文件挂载给应用。

```powershell
python deploy/production/search.py init --credentials E:/secure/search-bootstrap.json --expected-host <托管搜索主机名> --result E:/secure/search-init.json
python deploy/production/search.py check --credentials E:/secure/search-bootstrap.json --expected-host <托管搜索主机名> --result E:/secure/search-check.json
```

`init` 仅在 `course` 索引不存在时创建，默认 1 个主分片、1 个副本；集群需提供可分配副本的节点。已存在的索引按真实查询所需字段、类型、分析器和时间格式校验，不删除、不覆盖、不自动改映射。课程发布沿用已有可靠事件更新索引；迁移已有课程时，使用管理端搜索投影重放，并核对课程状态。完成后核对可搜索课程及其状态，容量按独立负载场景测量。

## 候选环境运行检查

`runtime_checks.py` 执行 HTTPS 请求、精确 HTTP 状态和 JSON Pointer 字段断言，支持保存响应中的 ID 供后续请求引用，以及有界 GET 轮询；不执行 shell、SQL 片段或动态代码。服务器 origin 必须出现在候选 VirtualService 中，禁止携带凭据重定向。报告只保留响应哈希、HTTP 状态、断言结果及实际时间，不保存令牌、手机号、验证码、请求体或响应原文。

复制 `runtime-notifications.example.json`，填写真实候选域名、合成测试账号、明确测试手机号及已受理的短信任务编号。独立受保护的凭据 JSON 使用 token 别名，例如 `{"admin":"<当前管理员访问令牌>"}`。各请求的 `auth` 引用相应别名。根据实际 JSON 包装使用指针；当前接口默认包装的 `data` 字段不能在配置中省略。运行前需替换样例占位值。

其他功能均提供现有接口的可配置样例：`runtime-payments.example.json`、`runtime-privacy.example.json`、`runtime-media-revocation.example.json` 和 `runtime-identity-mfa.example.json`；不需要编写测试代码。把相应样例和 `runtime-credentials.example.json` 复制到受保护目录，只填写本次场景使用的字段。账号、实时 TOTP、ID 和金额必须对应同一独立测试环境；订单、支付和退款流水使用 JSON 数字而非精度不受控的手工舍入值。

- 支付样例实际调用预下单、下单及异步完成查询，另核对同一测试学生通过门户完成的真实支付宝已支付订单、已成功退款的单课程订单及撤权。供应商侧二维码付款由测试付款人完成；样例不会尝试代替付款人扫码。`paid_*` 对应状态为2的支付宝已支付订单；`refunded_*` 与退款编号必须对应同一笔退款，金额从该订单的首个条目读取并与退款详情核对。这里的供应商观测来自应用已校验并持久化的支付／退款流水，供应商原始回执仍需随真实验收保存。
- 隐私样例使用三个不同学生身份：导出所有者、验证越权的其他学生，以及可注销的独立清理账号。清理账号会被禁用，并按当前已审阅规则处理数据；不得填写真实学员。管理员审批前的样例检查实际规则版本和保留清单，完成后核对保留结果。更换规则或测试数据后同步核对样例断言。
- 媒体撤权样例先获取新票据并读取真实 Range 字节，再对可退款的合成付费订单申请管理员退款，观察供应商退款完成，最后使用同一张尚未过期的票据确认拒绝。`section_id`、`order_detail_id` 必须属于同一课程和测试学生；课程不应开启试看。`media_range_sha256` 为已处理视频前1024字节的实际 SHA-256，可将此前正常 Range 响应保存到受保护文件后用 `Get-FileHash -Algorithm SHA256 <文件>` 获取。票据到期时场景失败，需重新取得票据后重跑撤权流程。
- MFA 样例要求测试管理员已登记验证器：缺少 TOTP 被拒绝，当前未使用的 TOTP 登录成功，同码重放被拒绝，退出后原会话被拒绝。它会登出此次测试登录，不应使用正在值班的真实管理员账号。

```powershell
python deploy/production/runtime_checks.py --package E:/secure/rendered --scenario E:/secure/notifications.json --credentials E:/secure/test-tokens.json --output E:/secure/evidence/notifications.measurement.json
```

仅有 GET/HEAD 的场景默认可执行。有 POST/PUT/DELETE 的场景必须增加 `--allow-writes`，并显式配置独立测试身份；短信场景还必须写明 `test_recipient`。真实短信和商户支付可能收费，先选供应商允许的测试环境；本轮没有运行这类请求。验证码、TOTP 等实时值写入受保护场景后执行，不能放进 Git、命令参数或公开附件。

每一步包含 `name`、`check`、`method`、`path`、`expected_status`，成功业务请求还需 `assertions:[{"path":"/data/status","equals":"..."}]`。`save:{"order.id":"/data/orderId"}` 可供后续 `${order.id}` 引用；写请求可以设置稳定的 `idempotency_key`，视频校验可以配置 `headers:{"Range":"bytes=0-1023"}`。GET/HEAD 可用 `attempts:1..300`，默认只执行一次，每次轮询间隔两秒，写请求不自动重试。跨13域的隐私处理、供应商回执和退款样例采用120次，预留最多238秒轮询等待，HTTP请求本身耗时另计；这是轮询等待上限，实际完成时间由业务与供应商处理决定。失败时停止后续步骤，保留各检查的执行状态。执行前保留新的 `RUNNING` 结果文件，路径已存在则不会发起请求；进程中断时文件保留 RUNNING，供排查和重新组织验证。

请求体中的 `${credentials.NAME}` 从受保护凭据文件读取，密码和 TOTP 不进入报告；`auth:"${auth.token}"` 可以使用前一步登录后绑定的令牌。二进制下载使用 `expected_sha256`（准确摘要或此前响应绑定），按流计算真实响应哈希，不把 ZIP／视频内容载入报告；响应字节和总读取时间有界。`require_unexpired` 接收播放票据的 epoch 毫秒到期时间或响应绑定，每次轮询前后都会核对，避免把过期票据的403误报为撤权成功。

| suite | 必须实际观测的 check | 使用方式 |
| --- | --- | --- |
| `payments` | `order_created`、`payment_confirmed`、`refund_confirmed`、`amount_consistent`、`entitlement_revoked` | 覆盖现有下单、异步操作查询、支付、退款与撤权流程；包含实际业务查询和供应商结果，不能用模拟订单 |
| `notifications` | `supplier_delivered` | 查询 `/api/v2/admin/deliveries/sms?taskId=<id>`，接收人 `state` 必须为 `DELIVERED`；受理、模拟或未知均不通过 |
| `privacy` | `export_completed`、`download_authorized`、`cleanup_approved`、`retention_preserved` | 使用隐私任务 API，检查分域完成、下载鉴权、审批结果和实际保留清单 |
| `media_revocation` | `revoked` | 退款或撤权后对原媒体／Range 请求确认拒绝，并保留此前成功播放的独立运行观测 |
| `identity_mfa` | `mfa_rejected`、`mfa_accepted`、`session_revoked` | 实际运行缺少／错误 TOTP、正确登录及旧会话失效路径 |

下单与退款是异步操作，202 仅表示持久受理；必须轮询相应 operation 到 `SUCCEEDED` 再检查业务结果。支付宝二维码支付仍需测试付款人完成供应商侧交互；超时后保留同一订单和幂等键再核对，不重复创造付款事实。验收时同时保留场景配置与供应商实际回执。

供应商回执步骤设置 `observation:"provider_receipt"`，业务查询设置 `"business_query"`，其他请求默认 `"http"`。支付验收必须同时包含这三类实际观测，隐私验收包含 HTTP 与业务查询。审核时核对实际端点、断言和对应的业务事实。

将各 suite 的真实报告命名为 `<suite>.measurement.json`，保持在同一受保护证据目录。故障切换、恢复、负载和遥测检索依照商业化手册在平台侧实际执行后，用现有结构化报告契约提交观测；收集器汇总这些实际测量结果。

```powershell
python deploy/production/collect_evidence.py --package E:/secure/rendered --reports E:/secure/evidence --output E:/secure/evidence/acceptance.json
python deploy/production/acceptance.py --package E:/secure/rendered --evidence E:/secure/evidence/acceptance.json
```

收集器将实际测量、SHA-256 附件与候选绑定；缺少、失败、跳过、过期或版本不符的 suite 会让总验收保持 `BLOCKED`。新增 `payments`、`privacy` 为必需项，每次验收按候选版本重新核对报告绑定。

## 备份证明与外部告警

生产应用启用 `http.server.requests` 延迟直方图，显式桶为100ms、500ms、1s、3s、10s。网关 recording rules 按候选 namespace 记录请求速率、P95 和五分钟成功比例，排除健康探针；成功比例分母为2xx、3xx、5xx和429，分子为2xx／3xx，其他明确客户端拒绝不计入该比例。全失败时结果为0，无业务请求时无样本。月度 SLO 另按持续运行数据评估，异步业务完成通过任务与端到端流程观察。

短信监控区分失败、未知结果及采样时间停滞，分别由 `TianjiSmsFailed`、`TianjiSmsOutcomeUnknown`、`TianjiSmsMetricsStale` 告警；修复供应商或采集后须核对实际恢复通知。`monitoring-rule-tests.yml` 用真实 promtool 验证混合状态流量、全失败、无流量、P95、短信告警触发与恢复。测试仅在显式设置 `TJ_PROMTOOL_IMAGE` 为现有本地不可变 Prometheus image ID 时运行，使用无网络临时容器、只读夹具及有界临时工作盘，不启动日常 Prometheus。

隐私任务使用独立持久队列，因此另采集 `tj_privacy_tasks_pending`、`tj_privacy_tasks_failed`、`tj_privacy_tasks_approval_required`、`tj_privacy_tasks_oldest_seconds` 和 `tj_privacy_metrics_last_success_timestamp_seconds`。`TianjiPrivacyTaskFailed` 提醒管理员处理失败任务；`TianjiPrivacyMetricsStale` 提示采集时间停滞。等待审批是明确的业务状态，仅展示数量，不当作技术故障；修复后在管理台核对影响再继续任务。

`backup-policy.yml` 由平台侧配置实施；`backup_proof.py` 接收备份校验记录并发布监控指标。实际备份和恢复由平台执行，本机备份保持停用。

受保护证明 JSON 字段为：`schema:"tianji-managed-backup-proof-v1"`、`status:"VERIFIED"`、准确 `commit`、`manifest_sha256`、`namespace`、带时区 `completed_at`、实际 `provider`／`resource_id`／`backup_id`、`verified_components:["database","objects","recovery_keys"]`，以及相对证明目录的 `artifacts:[{"path":"...","sha256":"..."}]`。附件包含实际供应商导出和校验记录，由操作员复核来源，工具校验字段、哈希及候选绑定。

```powershell
python deploy/production/backup_proof.py --package E:/secure/rendered --proof E:/secure/evidence/managed-backup.json --textfile E:/secure/textfile/tianji.prom
```

监控环境需安装并采集 Prometheus textfile collector，文件目录由运维创建且只允许指定发布者写入、采集服务读取；将该 exporter 接入现有 Prometheus。工具生成 `tianji_backup_last_verified_timestamp_seconds{namespace="<实际namespace>"}`。仅通过完整校验且在24小时内的证明才更新成功时间；失败保留上次值，既有 missing／expired 告警负责报告缺失或过期。`.pending` 表示上次原子发布未完成，先检查保留文件与目标权限，不用清空证明目录解决。

把渲染出的 Alertmanager 配置合入真实安装，并配置实际支持 Alertmanager Webhook 格式的通知接收器和受保护的 `receiver-token`；需要钉钉、企业微信等格式转换时，使用通知平台已有的 Alertmanager 接入能力。必须实际发送故障通知和恢复通知，并在外部接收端留存回执。验收以外部接收器及值班人员收到故障和恢复通知的回执为准。

生产身份服务的隐私文件另使用独立 COS 身份，读写权限仅限私有 `privacy-exports/` 前缀；另需读取桶 ACL／policy 以执行私有策略核对，私有上传若需 `PutObjectACL` 权限，仅对该前缀授予。它不复用媒资 API、worker 或公开封面的 IAM。按生产清单填入隐私专用凭据；未配置时生产任务明确失败，不能退回本机磁盘或公开链接。

## 本地工具回归

安装生产工具依赖后运行 `python -m unittest discover -s deploy/production -p 'test_*.py' -v`。普通用例覆盖配置与协议，以下两项集成验证需要显式选择隔离资源：

- MySQL：准备仅含合成数据的可丢弃容器，名称为 `tianji-readiness-test-<标识>`；设置 `TJ_READINESS_MYSQL_CONTAINER` 和 `TJ_READINESS_MYSQL_PASSWORD`。测试写入 `acceptance_bootstrap_*` 专用库，验证真实渠道和模板的幂等、冲突及事务回滚。
- Prometheus：设置 `TJ_PROMTOOL_IMAGE` 为本机已有镜像的不可变 `sha256:<64位ID>`。测试在无网络、只读挂载的临时容器内执行 promtool，核对网关记录规则、短信及隐私告警触发和恢复。

未配置的集成项显示为跳过，结果中分别记录通过、失败和跳过数量。测试结束后恢复原进程环境变量，隔离资源按项目归属清单处理。
