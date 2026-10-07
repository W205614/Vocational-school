# 天机学堂最终本地部署

Docker Desktop 中的 **tianji-final** 是一套 Compose 部署分组，可以展开查看每个容器。每个后端、前端或基础服务使用自己的镜像和容器；同组启动、停止，不再运行旧版和临时环境。

| 服务 | 数量 | 用途 |
|---|---:|---|
| app-data/pay/promotion/learning/trade/exam/course/user/auth/media/remark/search/message/gateway | 14 | 数据、支付、营销、学习、交易、考试、课程、用户、鉴权、媒资、点赞、搜索、消息和网关 |
| web-student / web-admin | 2 | 学生端、管理端 |
| mysql / redis / rabbitmq / elasticsearch | 4 | 数据库、缓存、消息队列、搜索索引 |
| prometheus / grafana | 2 | 指标采集和监控 |

学生端：http://localhost/ 。管理端：http://localhost:81/ 。验证账号保存在本机 `.local/accounts.json`，包含学生、管理员和教师；原业务账号的数据也已迁入。所有发布端口绑定本机回环地址。

此部署使用明确标识的本地支付、短信和文件/视频模拟环境，支付不会真实扣款，短信不会真实发送。媒资上传保存实际文件，视频读取实际字节。Nacos 远程配置和 XXL-Job 在这个本地环境中关闭，配置、服务地址和持久后台任务由新版应用处理；它们不是缺失的运行依赖。

从仓库根目录执行：

```powershell
docker compose -p tianji-final -f deploy/final/compose.yaml --env-file deploy/final/.env up -d --wait --wait-timeout 420
docker compose -p tianji-final -f deploy/final/compose.yaml --env-file deploy/final/.env ps
docker compose -p tianji-final -f deploy/final/compose.yaml --env-file deploy/final/.env logs --tail 100 app-trade
docker compose -p tianji-final -f deploy/final/compose.yaml --env-file deploy/final/.env stop
```

`up -d --wait` 检查所有 22 个服务的健康状态。应用 JVM 使用 384 MiB 堆上限、768 MiB 容器上限，数据库连接池每服务最多 8 个连接。MySQL 缓冲池为 256 MiB，Redis 为 96 MiB 且禁止静默淘汰；这只是当前本地环境配置，不代表生产容量。

私有配置 `.env`、`.local/configs`、签名密钥、账号、SQL 备份与测试诊断均被 Git 忽略。数据库等运行数据在命名卷中；新上传文件在 `.local/objects`。运行目录与备份目录作用不同。**不要运行 down -v，也不要清理这些数据卷。**

重新构建页面先运行 `npm --prefix frontend run typecheck` 和 `npm --prefix frontend run build`，再运行 `python deploy/final/build_web.py`，最后执行上面的启动命令。前端按当前 Compose 镜像标签分别构建；构建目录不包含运行凭据。首次准备或重新生成完整部署使用 `python deploy/final/generate.py`，它会核验已验收的后端镜像 ID，并保留 `.local/backend-images.json` 中的后端修复版本。必须保留本机已验证镜像和私有验收工件；仅复制 Compose 文件无法重建完整运行环境。

支付模块修复可运行 `python deploy/final/verify_pay.py`：它在独立的 `acceptance_pay` 数据库执行可靠性测试并打包模块，不调用真实支付商。随后把 JDK 的 bin 目录加入 PATH，运行 `python deploy/final/build_backend.py --only pay --tag ux-20261007`。该脚本记录镜像 ID 和 JAR 校验值；执行生成器、启动命令部署此版本。`WxPayTest`、`AliPayTest` 仅在显式设置 `TJ_REAL_PAYMENT_TESTS=true` 时启用，且需要独立配置真实支付环境。

接口回归入口为 `python deploy/final/verify.py`。浏览器验收先运行 `python deploy/final/runtime.py browser_fixture` 和 `python deploy/final/runtime.py rebuild_search` 创建独立课程并同步搜索；每次重跑完整购买、考试及退款流程均需新建夹具。设置 `ACCEPTANCE_CONTAINER_UI=1`、`TJ_RUNTIME_HOME`（deploy/final 绝对路径）、`TJ_RUNTIME_PROJECT=tianji-final` 和 `TJ_UI_RUNTIME_HOME`（deploy/final/.local 绝对路径）后，从 frontend 目录运行 `npm test` 或 `npx playwright test`。测试覆盖 1440、845、390 像素布局、登录恢复、免费报名、购买学习考试及退款，并写入专用验收账号的数据。

测试完成运行 `python deploy/final/archive_browser_fixtures.py`，通过课程正常下架流程移出专用测试课程，不删除历史。最新前端体验与浏览器结果见 [2026-10-07 验收报告](../../docs/frontend-user-experience-validation-20261007.md)。

课程配置和考试草稿的后续改进见 [课程配置与恢复验收](../../docs/admin-configuration-validation-20261007.md)。管理端通过分页检索选择课程、视频、题目和教师；编辑课程资料时可修正本地 PNG/JPEG 封面、介绍与详情，报名结束的草稿保留原截止日期，上架仍需有效报名计划。本地封面单独保存在 `.local/objects/course-covers`，公开读取仅匹配内容哈希图片地址；上传仍要求管理员，私人视频继续使用原有授权与签名。

考试草稿通过版本化迁移 `tj_exam/V003__exam_drafts.sql` 持久化。部署此代码前运行 `python deploy/final/runtime.py migrate`，然后重建、启动考试服务及前端。草稿自动同步，另一个设备使用同一账号可继续；冲突时保留本机未同步答案，提交检查草稿版本，已提交答卷拒绝后续写入。`python deploy/final/verify_exam.py` 在独立 `acceptance_exam` 数据库执行评分、并发创建/提交、教师评分、草稿冲突和归属测试并打包考试模块。

容器重建后的服务发现使用 Docker DNS。生成器为所有 JVM 挂载追加的 `dns.security`（成功解析缓存 10 秒、失败 2 秒）；网关 Reactor Netty 和两端 Nginx 同样限制解析缓存。修改此配置后应执行生成器和完整 Compose 启动命令，使所有应用获得新挂载。没有关闭证书校验或替换系统其他安全配置。`python deploy/final/verify_dns_recovery.py` 会停止/重建考试服务、网关和媒资服务，验证两端代理恢复、服务不可用时的 503 和持久封面字节；仅在本地验收期间执行。`python deploy/final/verify_history.py` 可只读核对已完成考试、小节及私人笔记，须在当前夹具完成完整业务测试后执行。

旧版数据卷和 `.local/backup` 中的 SQL、缓存和消息队列备份保留作恢复入口。历史错误队列消息已归档，没有自动重放。回退需要先停止新版、核对后续新增数据，再使用旧版部署配置；不能把旧备份直接覆盖到有新增业务的新版数据库上。

`python deploy/final/final_backup.py` 保存 SQL、私有配置（包含 DNS 安全属性）和媒资校验清单。校验清单不是文件字节备份；恢复新上传视频及封面仍需保留 `.local/objects` 或另行复制这些文件。配置和备份均留在本机忽略目录，不随 GitHub 源代码提交。
