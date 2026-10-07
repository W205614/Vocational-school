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

重新构建页面先运行 `npm --prefix frontend run typecheck` 和 `npm --prefix frontend run build`，再运行 `python deploy/final/generate.py` 和上面的启动命令。生成器核验已验收的后端镜像 ID，并分别重建两个前端镜像；必须保留本机已验证镜像和私有验收工件。仅复制 Compose 文件无法重建完整运行环境。

接口回归入口为 `python deploy/final/verify.py`。浏览器验收设置 `ACCEPTANCE_CONTAINER_UI=1`、`TJ_RUNTIME_HOME`、`TJ_RUNTIME_PROJECT=tianji-final` 和 `TJ_UI_RUNTIME_HOME` 后，从 frontend 目录运行 `npx playwright test`。这些验证会写入专用验收账号的数据。

旧版数据卷和 `.local/backup` 中的 SQL、缓存和消息队列备份保留作恢复入口。历史错误队列消息已归档，没有自动重放。回退需要先停止新版、核对后续新增数据，再使用旧版部署配置；不能把旧备份直接覆盖到有新增业务的新版数据库上。
