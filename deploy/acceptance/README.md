# 独立验收环境

此目录使用 Compose 项目 `vocational-acceptance`，所有发布端口绑定 127.0.0.1。现有 `tianji-desktop` 容器、数据卷和配置不由这里的脚本修改。数据库克隆和故障注入只用于验收，不可直接对生产执行。

## 准备和启动

需要 Docker Desktop、Java 21、Maven、Python（requirements.txt，账户种子另外需要 bcrypt）、Node 22。服务启动工具目前适用于 Windows；CI 的数据库/消息/算法测试适用于 Linux。第一次执行：

```powershell
python -m pip install -r deploy/acceptance/requirements.txt
python -m pip install bcrypt
python deploy/acceptance/prepare.py --clone
python deploy/acceptance/migrate.py
mvn -B -DskipTests install
docker compose -p vocational-acceptance -f deploy/acceptance/compose.yaml -f deploy/acceptance/compose.extras.yaml --env-file deploy/acceptance/.env --profile search up -d --wait elasticsearch
python deploy/acceptance/prepare_search.py
python deploy/acceptance/seed_accounts.py
python deploy/acceptance/restart_all.py
python deploy/acceptance/rebuild_search.py
python deploy/acceptance/prepare_observability.py
```

`--clone` 只读取现有 tianji-desktop MySQL 的 12 个数据库，拒绝覆盖已有验收库。没有该部署时，CI 使用 `initialize_test_schemas.py` 初始化四个专用测试库，仅验证后端集成测试，不等同完整业务环境。迁移发现历史重复记录会停止，必须先人工核对；不得删除记录来绕过唯一约束。

本地随机凭据、克隆备份、对象、运行配置、PID 和报告保存在被忽略的 `.env`/`.local`。不要提交、展示或分享这些文件。启动器从完成构建的可执行 JAR 创建 SHA-256 校验的固定副本，运行中的 JVM 不读取 Maven target/classes；先完成构建再启动。当前 Windows Java 路径见 `start_service.py`，迁移到其他电脑前调整路径。

```powershell
npm --prefix frontend ci
npm --prefix frontend run dev --workspace @school/student
npm --prefix frontend run dev --workspace @school/admin
```

默认开发端口见 frontend 的 Vite 配置；浏览器验收专用端口为学生端 23500、管理端 23501，网关为 23310。账户在 `.local/accounts.json`，只在本机查看。显式本地模式支持持久化模拟支付、短信和真实文件/视频字节；生产环境不得启用模拟标志。

## 回归及证据

按顺序执行，浏览器、重启、状态投影修改、压测不能互相重叠：

```powershell
python deploy/acceptance/run_tests.py --modules tj-remark,tj-user,tj-promotion,tj-learning,tj-exam,tj-pay/tj-pay-service,tj-auth/tj-auth-service,tj-search,tj-gateway,tj-trade --tests PageQueryTest,ReliabilityDatabaseTest,ResponseConverterTest,CookieBuilderTest,AcceptanceFaultsTest,LearningReliabilityTest,DelayTaskTest,ExamReliabilityTest,ObjectiveScoringTest,DiscountServiceTest,ProviderSettlementTest,CourseRepositoryBulkTest,FeignBulkheadTest,ReliabilityMetricsTest,AsyncSmsClientReliabilityTest,VerificationCodeReliabilityTest,LikeConcurrencyReliabilityTest
python deploy/acceptance/api_smoke.py
python deploy/acceptance/coupon_concurrency.py
python deploy/acceptance/financial_smoke.py
python deploy/acceptance/auxiliary_smoke.py
python deploy/acceptance/browser_fixture.py
npm --prefix frontend run test:e2e
python deploy/acceptance/concurrency_edges.py
python deploy/acceptance/projection_security_smoke.py
python deploy/acceptance/recovery_smoke.py
python deploy/acceptance/browser_fixture.py
python deploy/acceptance/runtime_faults.py
python deploy/acceptance/migration_audit.py
python deploy/acceptance/verify_observability.py
```

故障测试会停止本验收 RabbitMQ 或学习/交易 JVM，必须在专用验收窗口执行。测试中的业务 fixture 使用新 ID，不能证明所有管理页面的 CRUD 都已通过浏览器验收。支付成功模拟器调用本项目支付服务，真实支付宝/微信渠道需独立凭据和环境验证。

空闲窗口运行 `load_claims.py`、`sustained_claims.py --seconds 60`。五档并发依次为 1、10、50、100、200；指标包含客户端轮询耗时，因此不能当作纯接口 QPS，也不能由一分钟窗口推断长期生产容量。压测会写入验收优惠券与合成用户 ID，不会创建真实用户或访问原部署。

```powershell
python deploy/acceptance/export_openapi.py learning exam promotion trade course user auth media remark search message pay data
node frontend/scripts/generate-types.mjs
npm --prefix frontend run typecheck
npm --prefix frontend run build
```

公开的 v2 OpenAPI 保存到 frontend/openapi，共享生成类型在 frontend/packages/shared/src/generated。真实文件响应不包装为 JSON；Long 按字符串发布。当前界面还有人工维护字段类型，不能把类型生成当作所有 API 调用都已静态校验。最终门禁通过后可运行 export_evidence.py 导出固定白名单中的脱敏统计报告；原始日志、账户、运行配置和数据库备份继续留在 .local。

## 切换和回退

先阅读 ../../docs/productionization-status.md 和 ../../docs/reliability-runbook.md。迁移工具是独立验收工具，发布到真实环境应由部署流程引用同一批已校验 SQL 和校验和，替换数据库连接，独立演练备份恢复。不要把验收环境账号、模拟渠道或密钥复制到生产。


## Linux 镜像与两端 Nginx 验收

先完成后端构建、宿主机启动和两端构建，再创建固定产物的镜像。私有运行配置由已有验收配置转换，原部署不参与：

```powershell
python deploy/acceptance/container_apps.py prepare
python deploy/acceptance/container_apps.py build
python deploy/acceptance/container_smoke.py
```

容器检查按服务逐个启动，非 root 用户、384 MiB 堆、768 MiB 容器上限，校验 Sentinel 日志目录可写、无 OOM、优雅停机，并访问真实 readiness 和 OpenAPI/网关接口；跨服务依赖使用同一验收库和宿主机验收服务。它验证 Linux 镜像启动，不能替代完整容器栈同时运行或高可用验收。

`compose.apps.yaml` 提供 14 个应用和两个 Nginx 的完整容器配置，依赖 search profile；全栈启动前先停止占用相同端口的验收 JVM，检查 WSL 可用内存，避免与旧部署同时启动造成内存不足。该同时运行模式仍需专用资源窗口验证：

```powershell
docker compose -p vocational-acceptance -f deploy/acceptance/compose.yaml -f deploy/acceptance/compose.extras.yaml -f deploy/acceptance/compose.apps.yaml --env-file deploy/acceptance/.env --profile search --profile apps config --quiet
```

前端容器回归由 `container_browser.py` 创建两个有界 Nginx 容器，代理至宿主机验收网关，再执行同一套完整业务浏览器测试，完成后只清理新建容器：

```powershell
python deploy/acceptance/container_browser.py
```

镜像基于本机已有 Temurin 21 和 Nginx 标签用于隔离验收；真实发布需要锁定经过安全审查的镜像 digest、配置及资源。不要直接将本地模拟配置作为生产配置。


## 释放本次临时资源

```powershell
python deploy/acceptance/stop_acceptance.py
```

只停止命令行与 PID 身份匹配的验收进程，以及 Compose 项目标签和服务名匹配的验收容器；不删除任何数据卷，不停止原 `tianji-desktop` 或其他项目。只有进程身份发生变化时跳过；启动工具还会检查端口占用。若后续只想停止验收 JVM、继续保留基础服务，使用 `--keep-infra`。不要结束 VmmemWSL 或执行全局 WSL 关闭来保留原容器访问。
