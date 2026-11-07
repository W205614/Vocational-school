# 商业化运行与验收契约

生产部署包提供应用配置、发布门禁与验收工具，目标云环境待部署和验收，入口见[生产部署包](../deploy/production/README.md)。单平台、同地域单节点／单可用区容错的建设目标为月可用性 99.9%、RPO≤300秒、RTO≤1800秒；地域整体中断需另定恢复方案。副本及资源限制为初始配置，准入容量按测量结果制定。

## 发布门禁

生产发布依次核对准确源码 commit、不可变镜像 digest、安全报告、配置指纹、向前迁移、托管备份策略、供应商测试环境证据、候选环境真实角色流程和故障／恢复演练。原本机环境及私有迁移材料不参与发布，也不从旧数据库覆盖正在使用的业务数据。没有云账号或供应商配置时，状态保留为未部署／待验收。

CI 的 `production-security` 生成已解析 Maven CycloneDX SBOM，并扫描 Java、npm、Python 依赖、仓库密钥及独立 worker 镜像；原浏览器流水线扫描实际构建的七个应用／网页镜像。Trivy 工具固定版本，新增 Actions 固定 commit。HIGH／CRITICAL 漏洞、密钥命中、扫描失败或数据库不可用均阻止门禁，不默认忽略未修复漏洞。公开附件只包含漏洞元数据和密钥规则／路径／行号，不包含匹配值或代码片段。扫描结果按执行时间和候选版本保存，后续依赖更新继续扫描。

## 隔离候选环境的演练协议

使用生产同构、独立 namespace／数据库／bucket、合成账号及供应商测试环境。每次记录服务版本、云资源身份、开始时间、故障时间、恢复时间、流量、错误、积压和测试断言。只对明确候选资源执行故障，不对日常环境使用删除、停机或旧快照覆盖。

| 场景             | 操作与必须保留的证据                                                                                                                                                        |
| ---------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 应用实例故障     | 持续运行学习心跳、考试提交和幂等请求，退出一个候选实例；保存请求时间线和业务结果，检查没有重复发课、积分、退款或答案丢失                                                    |
| 数据库主节点切换 | 使用托管控制面的测试切换，保存控制面事件及真实写入确认点；新主就绪后核对已确认交易与恢复窗口                                                                                |
| 消息节点故障     | 在带新生产 vhost 的托管测试集群执行节点切换；保存 quorum 配置、publisher confirm、消费者记录及 Outbox／Inbox 收敛结果                                                       |
| 可用区故障       | 候选节点按单一区域隔离，保持负载与观测运行；核对余下可用区持续服务、调度、重试、连接排空及端到端恢复窗口                                                                    |
| 在线备份恢复     | 在全新目标恢复 MySQL 时间点与关联私有对象版本，核对业务不变量、媒资哈希和支付／权益状态；再重建搜索与缓存，不覆盖原候选                                                     |
| 媒体撤权         | 真实私有对象经过扫描与转码后播放；退款后测试已有票据的下一次媒体／Range请求、续签和跨用户访问全部按权益拒绝                                                                 |
| MFA／账户        | 未验证的短信、重复验证、过期验证码、管理员缺少／错误 TOTP、旧会话撤销、找回后会话失效和隐私操作归属分别验证                                                                 |
| 通知与告警       | 保存供应商受理及送达回执，以及外部接收端的故障与恢复通知                                                                                    |
| 容量             | 固定场景比例、思考时间、媒体码率和请求重试；覆盖购课、学习心跳、视频与集中交卷，分别记录活跃用户、RPS、并发流、P95/P99、错误与429、资源和积压；准入容量不得超过实测稳定范围 |

现有 compact 负载工具用于本机 Compose、合成夹具和模拟支付。生产容量使用目标环境的工作负载测量；故障操作由平台控制面执行，验收门禁核对实际报告。首次对外部署前完成上述协议，真实支付使用指定测试订单验收。

## 可机读的验收证据

私有 `acceptance.json` 使用如下顶层字段；所有 `artifacts.path` 相对证据目录，附件必须实际存在并按 SHA-256 校验。缺少环境或观测时保留 `BLOCKED`，执行后由工具汇总实际结果。

```json
{
  "schema": "tianji-production-acceptance-v1",
  "commit": "<准确40位提交>",
  "manifest_sha256": "<resources.yaml哈希>",
  "environment": "isolated-production-candidate",
  "observed_at": "<带时区的实际执行时间>",
  "suites": {
    "application_failover": {
      "status": "BLOCKED",
      "required_skips": 0,
      "recovery_seconds": null,
      "data_loss_seconds": null,
      "duplicate_business_effects": null,
      "invariant_checks_passed": false,
      "artifacts": []
    }
  }
}
```

必需 suite 为 `application_failover`、`zone_failover`、`database_failover`、`broker_failover`、`restore`、`media_revocation`、`identity_mfa`、`notifications`、`external_alert`、`telemetry_redaction`、`capacity`、`payments`、`privacy`。支付和隐私套件还需满足运行工具声明的逐项业务断言。故障和恢复场景需提供恢复秒数、丢失时间窗口、零重复副作用和业务不变量检查；恢复另需 `objects_verified=true`，外部告警需 `delivered_to_external_receiver=true`。遥测脱敏需在真实入口、sidecar、应用和最终追踪后端检索合成签名 canary，证明没有把 bearer URL 查询传入日志／外部追踪。

容量需提供 `active_users`、`duration_seconds`、`p95_ms`、`p99_ms`、`error_ratio`、`admitted_active_users`、`workload_model_sha256`、`budget_passed` 和 `workflow_counts`（`purchase`、`learning_heartbeat`、`video`、`exam_submit` 均须实际执行）。附件保存约定预算、观测数据、未通过场景和退出码。门禁检查七天内证据与候选 SHA／配置的一致性，操作员复核来源；历史报告按明确的内容一致性规则核对复用条件。

每个 suite 至少一个 JSON 附件必须是结构化的 `tianji-production-measurement-v1`：包含同名 `suite`、准确 `commit`／`manifest_sha256`，`execution` 内记录 `kind=runtime-experiment`、隔离环境、实际带时区起止时间、`exit_code=0`、`required_skips=0`，`measurements` 与声明指标一致；`observations` 保存实际 HTTP、业务查询、供应商回执、故障事件、负载采样或遥测检索的原始观测。工具校验结构、完整性及候选一致性，操作员负责复核运行记录和附件来源。

月度 SLO 根据真实网关业务请求定义分母和错误预算，持续保留用户侧可用性、变更、供应商故障及维护记录。

## 密钥维护边界

服务间有向边令牌支持接收方先行的新旧密钥重叠。JWT 已支持 kid、公钥集合周期刷新和显式证书重叠；MFA 使用版本化密文与受控重加密维护。具体配置、先后顺序和旧键退役条件见[认证密钥维护](key-maintenance.md)。云环境需演练滚动切换并记录会话影响；MFA 批量维护暂停相关写入，维护影响计入可用性记录。媒资签名密钥更换仍需明确存量票据失效及恢复策略，短时 bearer 票据不提供 DRM。

## 官方依据

- [Kubernetes 拓扑分布约束](https://kubernetes.io/docs/concepts/scheduling-eviction/topology-spread-constraints/)
- [Istio STRICT 双向 TLS](https://istio.io/latest/docs/reference/config/security/peer_authentication/)
- [OpenTelemetry Java 自动注入](https://opentelemetry.io/docs/platforms/kubernetes/operator/automatic/)
- [RabbitMQ Quorum Queues](https://www.rabbitmq.com/docs/quorum-queues)
- [CycloneDX Maven 插件](https://github.com/CycloneDX/cyclonedx-maven-plugin)
- [Trivy SBOM 扫描](https://trivy.dev/docs/latest/docs/target/sbom/)
