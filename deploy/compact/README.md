# 本机合并部署与验收

目标为个人展示、故障验证与压测，保留模拟支付及既有业务模块。最终运行组为 `tianji-compact`，共 11 个容器，使用合成演示数据；旧 `tianji-final` 和演练容器已退役，保留其数据卷、镜像及校验备份。历史数据未迁入新环境，其他项目不在清理范围。所有入口绑定 `127.0.0.1`。

## 结构

| 应用 | 原模块 | 默认端口 |
| --- | --- | --- |
| identity | auth、user | 24001 |
| commerce | trade、pay、promotion | 24002 |
| education | course、learning、exam、remark | 24003 |
| support | media、search、message、data | 24004 |
| gateway | 网关 | 24310 |

学生端为 `http://127.0.0.1:24500`，管理端为 `http://127.0.0.1:24501`。业务仍使用原来的 12 个数据库。每个模块有独立 Spring Bean 容器、Mapper、连接池、事务管理器、任务和消费者，共享宿主的 HTTP 服务器；禁止 Bean 定义覆盖。普通业务 JAR 由四个启动模块组装。默认 Maven 构建仍保留独立应用，`-Pcompact` 构建合并应用。

内部路由为 `/_modules/{alias}/...`，必须提供服务凭证。网关保留 `/api/v2` 路径、响应结构及角色检查。同宿主的 Feign 契约由内存 MVC 适配器执行，跨应用继续走 Feign HTTP。模块之间仍执行各自事务和幂等、补偿协议，不将远程业务隐式并入同一事务。

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
python deploy/compact/run_transport_tests.py
npm --prefix frontend run typecheck
python deploy/compact/security_smoke.py
# 安全测试会短暂限制生成的学生账号，等待 32 秒再运行浏览器。
Start-Sleep -Seconds 32
cd frontend
npx playwright install chromium
cd ..
python deploy/compact/run_browser.py
python deploy/compact/audit_smoke.py
```

数据库测试仅初始化 `acceptance_*` 专用库。浏览器每次新建明确标识的合成夹具并重建搜索投影；验证购买、媒资、学习、笔记问答、考试草稿冲突、评分、退款和退款后的考试历史。管理端新增按模块查询的操作审计；教师待评分列表有分页及课程、学生筛选。审计的异步操作与操作记录关联，`ACCEPTED` 不等于业务成功。

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

默认备份暂停该项目的应用和网页，完成后恢复之前运行的服务。SQL 含表、数据、触发器；包内还包括私有配置、签名密钥、实际媒资字节、Redis DUMP 及绝对到期时间、停写后的 RabbitMQ 数据目录、镜像标识、表行数和 SHA-256。校验通过后才将临时目录发布为不可变 UTC 时间戳目录，保留多个恢复点。失败目录保留供排错，不自动删除旧备份。

`--online` 只提供部分在线备份，不能作为完整跨资源恢复证据。恢复程序要求新的空目录和从未拥有资源的 recovery 项目，禁止镜像向消息队列恢复卷自动复制初始化内容，拒绝非空 Redis、错误卷归属和镜像标签漂移；首先核对每张表的精确行数及每个媒资文件哈希，再重建搜索投影、读取原业务历史并在恢复环境运行新的浏览器完整流程。恢复环境会占用额外资源，可以先暂停自己的 compact 项目；不得操作其他项目。

不要使用 `docker compose down -v` 清理有数据的部署。恢复失败时保留恢复目录和卷；重新演练使用另一个新的目录、项目名和未占用端口。

## 性能验证

```powershell
python deploy/compact/load_fixture.py
python deploy/compact/load_test.py --users 50 --seconds 60
python deploy/compact/load_test.py --formal
```

使用 200 个独立合成学生，250ms 思考时间、90% 常规查询与 10% 异步笔记写入。分别统计查询、写入接受、异步完成、准入拒绝、业务拒绝、系统错误及资源、连接池等待、队列积压和 Redis 内存告警。短测只验证链路与采集，不能推断正式容量；这组负载覆盖查询和异步笔记，不覆盖领券热点、考试高峰或完整交易链路的容量。正式协议为 10/50/100/200 并发，每档 30 分钟、三次：一个部署配置需至少 6 小时；合并前后完整对照需至少 12 小时，另加初始化、预热与登录时间。

正式对照须使用同机器、相同业务代码、同份数据快照和基础设施镜像；先记录预热后的稳态 Java 内存、空闲 CPU、连接数及启动时间，再跑完整链路。原历史部署和新合成部署的内存快照不能证明降低 20%。50 用户的待验证目标为查询 P95 ≤500ms、机器处理异步完成 P95 ≤3s。发现真实慢查询后再执行 EXPLAIN 和修改索引；不要通过取消库存校验或无限增加线程提高表面吞吐。

## 切换与回退约束

`python deploy/compact/release_gate.py --historical` 检查功能、安全、恢复、正式性能和历史数据一致性证据；缺少任意项返回非零，不自动切换原入口。性能与历史证据不得手填“成功”绕过测量。

历史切换必须先在独立副本验证迁移和接口一致性，再安排停写窗口：停止旧入口的写请求、旧后台任务及消费者，检查数据库操作/Outbox 和 RabbitMQ 积压，取得一致备份，恢复到目标，完成行数、媒资和关键业务校验后切换路由。保留原镜像及配置。新实例发生写入后，回退必须让旧代码兼容读取当前数据或先做增量回迁；禁止用切换前备份覆盖新订单、评分、退款等数据。新旧消费者不能同时消费同一业务队列。

当前入口为最终个人演示部署；原入口已停用，原历史数据保留离线。容器退役不等于历史数据迁移，历史切换门禁仍有效。Elasticsearch 7.17.29 仅限本机隔离运行；支持版本迁移、真实支付、多节点高可用、Kubernetes、服务网格、多租户均为独立后续范围。

## 容器退役记录

实际页面测试通过后，通过 `retire_old.py` 显式指定旧部署和演练项目；先校验两份停写备份及最终 11 个容器健康，再核对容器标签并停止、移除目标。命令不删除卷或镜像，完整容器配置与卷清单只存入忽略目录。新增历史/演练环境不能用全局 prune 清理。当前清理与退役后回归见 [最终功能验收](../../docs/compact-final-functional-validation-20261007.md)。
