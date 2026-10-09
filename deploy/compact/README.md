# 本机合并部署与验收

目标为个人展示、故障验证与压测，保留模拟支付及既有业务模块。最终运行组为 `tianji-compact`，共 11 个容器，使用合成演示数据；旧 `tianji-final` 和演练容器已退役，保留其数据卷及校验备份。多余镜像先归档验证后再清理，最终环境已有的业务数据在更新中保留。原离线历史数据未迁入新环境，其他项目不在清理范围。所有入口绑定 `127.0.0.1`。

2026-10-09，运行源码 `7aec0a2` 的八类功能证据门禁通过，清理后 11 个最终容器均健康；累计 162 个不同的多余项目镜像已归档校验并删除，3 个冗余标签已移除。后端、浏览器、独立恢复、当前 MySQL 搜索 9→7→9 回退、原有记录与媒资保留等结果见 [交付报告](reports/functional-20261009.md) 和 [脱敏指纹](reports/functional-20261009.json)。性能采样状态为 `DEFERRED`。

## 结构

| 应用 | 原模块 | 默认端口 |
| --- | --- | --- |
| identity | auth、user | 24001 |
| commerce | trade、pay、promotion | 24002 |
| education | course、learning、exam、remark | 24003 |
| support | media、search、message、data | 24004 |
| gateway | 网关 | 24310 |

学生端为 `http://127.0.0.1:24500`，管理端为 `http://127.0.0.1:24501`。业务仍使用原来的 12 个数据库。每个模块有独立 Spring Bean 容器、Mapper、连接池、事务管理器、任务和消费者，共享宿主的 HTTP 服务器；禁止 Bean 定义覆盖。普通业务 JAR 由四个启动模块组装。默认 Maven 构建仍保留独立应用，`-Pcompact` 构建合并应用。

内部路由为 `/_modules/{alias}/...`，必须提供服务凭证。网关保留 `/api/v2` 路径、响应结构及角色检查。优化分支使用标准 Feign HTTP：同宿主调用回环地址，跨宿主调用服务地址，连接超时 1500ms、读取超时 5000ms。身份、请求 ID 与调用深度随内部请求传递；深度超过四层时拒绝，按深度保留内部处理容量。模块之间仍执行各自事务和幂等、补偿协议，不将远程业务隐式并入同一事务。

## 干净启动

需要 Java 21、Maven、Node.js/npm、Python 3.12+、Docker Compose 和可用镜像源。Windows PowerShell 中请确认这些命令已加入 PATH。

```powershell
python -m pip install -r deploy/compact/requirements.txt
npm --prefix frontend ci
python deploy/compact/setup.py prepare
python deploy/compact/setup.py init
python deploy/compact/setup.py build
python deploy/compact/setup.py up
python deploy/compact/prepare_search.py
python deploy/compact/rebuild_search.py
```

初始化只使用版本库中的结构、权限目录和合成演示数据，不读取本机历史 JAR 或旧用户订单。遇到已有业务库但没有本初始化器日志时直接拒绝覆盖。`.env`、签名密钥、账号、配置、对象和报告保存在被忽略的 `.local` 中；演示登录账号查看 `.local/accounts.json`，不要提交该文件。每个运行账号只有所属库的 DML 权限，DDL 由迁移入口执行。

Linux 构建使用当前非 root 用户的 UID/GID 创建镜像用户，匹配媒资 bind mount 的所有者，避免上传文件和宿主备份互相失去权限；Windows 或 root 执行构建时仍使用非 root UID/GID 10001。恢复到不同 UID 的 Linux 主机时，需要单独核对对象目录与镜像用户的权限。CI 失败诊断只导出已脱敏的状态和日志片段，私有配置不上传。

积分赛季归档表不再由运行账号动态创建；新增赛季后执行 `python deploy/compact/setup.py migrate` 预建归档表。数据库触发器更新账号安全版本；改密、禁用或修改角色后，网关最长缓存 5 秒，旧会话随后失效。刷新必须携带 `audience=student` 或 `audience=admin`，会话可独立撤销。

单批启动可用 `setup.py up --group identity`，随后 commerce、education、support、gateway。所有合并组件都应通过验收后才允许历史入口切换。

## 回归

```powershell
python deploy/compact/check_policy.py
python deploy/compact/run_backend_tests.py
npm --prefix frontend run typecheck
npm --prefix frontend run test:unit
python deploy/compact/security_smoke.py
# 安全测试会短暂限制生成的学生账号，等待 32 秒再运行浏览器。
Start-Sleep -Seconds 32
cd frontend
npx playwright install chromium
cd ..
python deploy/compact/run_browser.py
python deploy/compact/audit_smoke.py
```

数据库测试仅初始化 `acceptance_*` 专用库；完整后端入口还串行执行真实套接字与模块准入测试，要求指定用例全部执行且没有跳过，失败日志按次保留。浏览器每次新建明确标识的合成夹具并重建搜索投影；验证购买、媒资、学习、笔记问答、考试草稿冲突、评分、退款和退款后的考试历史。失效权益保留本人历史读取，页面解释原因；开始考试、保存草稿及收费视频继续受权益限制。管理订单分别显示课程快照和学员，回归同时核对接口字段与表格。管理端新增按模块查询的操作审计；教师待评分列表有分页及课程、学生筛选。审计的异步操作与操作记录关联，`ACCEPTED` 不等于业务成功。

CI 增加权限清单、会话与登录分类测试、草稿/封面/课程配置测试、合并启动及浏览器主流程。修改后的远程 CI 是否成功须以本次提交的运行结果为准。

## 备份与独立恢复

```powershell
python deploy/compact/backup.py
python deploy/final/final_backup.py --verify <完整备份目录>
python deploy/compact/restore.py <完整备份目录> --home <新的空目录> --project tianji-recovery-demo --offset 1000
# 自动演练、保存验收结果，并恢复原来运行的服务：
python deploy/compact/rehearse_recovery.py <完整备份目录> --pause-source
# 单独验证非空 Redis 的二进制、多种类型、TTL 及覆盖拒绝：
python deploy/compact/redis_roundtrip.py
```

默认备份暂停该项目的应用和网页，完成后恢复之前运行的服务，并等待健康检查通过；恢复启动失败时命令返回非零。SQL 含表、数据、触发器；包内还包括私有配置、签名密钥、实际媒资字节、Redis DUMP 及绝对到期时间、停写后的 RabbitMQ 数据目录、镜像标识、表行数和 SHA-256。校验通过后才将临时目录发布为不可变 UTC 时间戳目录，保留多个恢复点。失败目录保留供排错，不自动删除旧备份。

`--online` 只提供部分在线备份，不能作为完整跨资源恢复证据。恢复程序要求新的空目录和从未拥有资源的 recovery 项目，禁止镜像向消息队列恢复卷自动复制初始化内容，拒绝非空 Redis、错误卷归属和镜像标签漂移；首先核对每张表的精确行数、内容校验和及每个媒资文件哈希，再重建搜索投影、读取原业务历史并在恢复环境运行新的浏览器完整流程。恢复环境会占用额外资源，可以先暂停自己的 compact 项目；不得操作其他项目。

不要使用 `docker compose down -v` 清理有数据的部署。恢复失败时保留恢复目录和卷；重新演练使用另一个新的目录、项目名和未占用端口。

## 性能验证

2026-10-08 用户确认本轮按功能、安全与恢复检查交付，不再等待正式性能采样，也不要求固定延迟阈值。以下为保留的性能协议与工具，**不属于本轮交付的通过证明**；200 并发容量和 RSS 降低 20% 均未完成当前版本验证。

```powershell
python deploy/compact/load_fixture.py
python deploy/compact/load_test.py --users 50 --seconds 60
```

上面的旧负载入口只覆盖查询与笔记，作为链路诊断保留，不作为本轮正式验收。当前正式协议为 `perf-3h-v1`，入口是 `mixed_load.py`，由 `run_baseline.py` 和 `run_post_measurements.py` 串行编排冻结的测量环境。每种配置按 10/50/100 并发各 300 秒、200 并发 600 秒三轮执行，合计 45 分钟；修复前独立、修复前合并、修复后独立、修复后合并共 180 分钟正式采样。初始化、快照恢复、串行登录、每轮 300 秒查询预热、调参诊断和失败重测另计。

总并发包含全部业务用户，固定随机种子 20261007、250ms 思考时间；查询/笔记/进度/考试评分/购买支付退款/热点领券比例为 50/10/15/10/10/5，低并发档每类至少一人。教师、管理员动作在所属流程内串行执行，不增加独立业务用户。每轮恢复基础快照，使用足量合法夹具，测量期间不得运行构建、浏览器、恢复演练或镜像下载。

每个阶段的独立与合并部署须使用相同业务源码、基础快照和基础设施镜像。200 并发三轮中，每类查询 P95 ≤500ms，每类命令机器处理 P95 ≤3s，成功样本至少 100；预热及正式阶段的非预期拒绝、429、5xx、超时都会导致验收失败。RSS 包含全部业务 Java 服务和网关，取每轮最后六分钟的时间中位数，再取三轮中位数；合并部署须相对同阶段独立部署降低至少 20%。持续增长、采样不足或背景干扰均不能判定通过。

同时保留整套环境资源、业务吞吐、连接等待、SQL/锁等待、队列及 GC 证据。默认候选为连接池 8、操作与调度线程 2、轮询间隔 250ms，仍需正式验收；工具保留 4/8、1/2、750/250ms 的有界候选。12 个业务连接池加 16 个观察连接、16 个控制预留，连接预算为 128，不超过数据库上限 160。观察查询取消冗余逐次 ping，连接等待与所有失败仍计入结果，并单独记录观察耗时。修正测量工具后使用新批次重测四组，拒绝混用旧指纹报告。不得取消业务校验、无限扩容或排除失败样本。该协议只验证本机合成数据的短周期容量，不证明长期无泄漏、无慢性积压、多节点高可用或真实支付。原始私有记录与当前完成状态见 [优化记录](OPTIMIZATION.md)。

## 切换与回退约束

本轮执行 `python deploy/compact/release_gate.py --scope functional`：仍要求同批次的后端、浏览器、安全、审计、非空 Redis、完整独立恢复、搜索回退和既有记录保留证据；提交、镜像、配置或基础快照不匹配，以及缺失、陈旧或失败的证明均返回非零。报告明确记录 `acceptanceScope=functional`、`performanceValidation=DEFERRED`，不会把未测量的指标写成通过。

默认 `python deploy/compact/release_gate.py` 保留完整 `perf-3h-v1` 门槛；`--historical` 另要求原历史数据一致性证据。当前功能交付不代表原离线历史数据可以直接切换，不自动变更原入口。性能与历史证据不得手填“成功”绕过验证。安全检查或高频浏览器登录后应等待登录窗口自然到期再开始下组检查；不要删除限流键或关闭防护。

历史切换必须先在独立副本验证迁移和接口一致性，再安排停写窗口：停止旧入口的写请求、旧后台任务及消费者，检查数据库操作/Outbox 和 RabbitMQ 积压，取得一致备份，恢复到目标，完成行数、媒资和关键业务校验后切换路由。保留原镜像及配置。新实例发生写入后，回退必须让旧代码兼容读取当前数据或先做增量回迁；禁止用切换前备份覆盖新订单、评分、退款等数据。新旧消费者不能同时消费同一业务队列。

当前入口为最终个人演示部署；原入口已停用，原历史数据保留离线。容器退役不等于历史数据迁移，历史切换门禁仍有效。默认搜索为 Elasticsearch 9.5.5，7.17.29 兼容版本仅用于本机隔离回退验证；每次切换暂停本项目写入及相关消费者，创建新卷，从当前 MySQL 重建并核对元数据、销售数和财务事实，再恢复服务。真实支付、多节点高可用、Kubernetes、服务网格、多租户均为独立后续范围。

本机使用模拟支付，真实支付适配器未启用。历史支付 `bootstrap.yml` 保留了已公开的私钥样例，不能作为真实渠道凭证复用；本轮没有核实其有效性。接入真实渠道前须移除样例、改用私有环境配置、更换任何曾公开使用的凭证，并单独验证签名、回调、结算与退款。

## 容器退役记录

常规验收后的清理通过 `retire_old.py` 显式指定旧部署和演练项目，先校验停写备份、容器健康及所有权。2026-10-08 用户改为先清理容器再测试，本次在原部署停写备份和配置、应用字节保留后，逐个核实并移除 102 个旧测量、诊断和恢复容器，只保留最终组的 11 个容器；168 个数据卷和 35 个其他项目容器保持一致。正式测量需要的临时容器按组创建，完成或失败后自动移除，原始证据和卷继续保留。没有使用全局 prune 或删除卷，当前进展见 [优化记录](OPTIMIZATION.md)；此前的独立交付见 [2026-10-07 功能验收](../../docs/compact-final-functional-validation-20261007.md)。
