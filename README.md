# 天机学堂 · 职业教育在线学习平台

Java 微服务与 Vue 3 双端应用，覆盖课程浏览、购买与退款、学习进度、私人笔记、收藏、问答点赞、考试评分、积分和优惠券。当前本机部署为 **`tianji-final` 一套 Docker Compose 服务组**：14 个后端服务、学生端和管理端两个前端、6 个基础设施服务，共 22 个容器。每个服务使用独立镜像。

当前代码已迁移至 Java 21 / Spring Boot 4。支付、短信和云媒资提供显式本地模拟环境；本地视频上传、数据库记录、消息处理和学习权益使用真实持久化。模拟支付不会真实扣款，本地结果不代表真实第三方服务已验收。

## 访问入口

| 入口 | 本机地址 |
|---|---|
| 学生门户 | <http://localhost/> 或 <http://127.0.0.1:23500/> |
| 管理与教师工作台 | <http://localhost:81/> 或 <http://127.0.0.1:23501/> |
| API 网关 | <http://127.0.0.1:23310/api/v2/> |

专用验收账号保存在本机忽略目录 `deploy/final/.local/accounts.json`，凭据不提交到 Git。管理端页面根据管理员、教师角色显示可用操作；学生接口和私人笔记、考试记录等仍校验归属。

## 当前功能

- 学生端：课程分类、检索与详情，购物车、优惠方案、订单及支付入口，取消与退款确认，课程学习、视频进度、随堂笔记、收藏、讨论点赞、积分、通知及个人设置。
- 管理端：真实业务看板，课程草稿与上/下架、媒资、题库、试卷版本、授权教师评分、用户权限、营销、订单退款和通知管理。
- 课程配置：按名称检索课程、视频、题目和教师；通过目录选择考试小节，已保存的关联刷新后回显。章节未保存时阻止关联写入，切换课程前提示未保存修改。
- 内容修正：封面预览、PNG/JPEG 上传、三级分类选择、以元编辑价格、介绍与详情编辑。报名结束的草稿可保存内容修正并保留截止日期；重新上架需有效报名计划。新课程不会自动生成随机评分。
- 考试：试卷和题目快照，客观题按完整答案集合评分，主观题由授权教师评分；提交后答案不可修改，重复提交不重复发出通过事件。草稿自动持久化，可在同一账号的另一设备继续；并发编辑提示版本冲突，载入最新草稿前保留可查看的本机备份。
- 运维：操作状态、失败事件与补偿任务查询，Actuator 存活/就绪检查，Prometheus/Grafana 指标。

历史课程缺失的真实图片、介绍和报名安排，需要管理员根据原始资料整理。程序提供编辑和失败提示，不自动编造课程资料或延长报名日期。

## 并发与可靠性设计

关键异步写入使用持久操作记录；本地业务事务与 Outbox 一起提交，消费去重与业务更新同事务完成。跨服务使用幂等协议、条件状态更新和持久补偿，不以开启 Seata 替代业务状态设计。事件重发保留原事件标识。

库存、限领、兑换和积分的最终依据在数据库；Redis 用于预校验、缓存和可重建投影。支付事实、订单状态和课程权益分别处理；退款保留学习和笔记历史。学习完成通过条件转换限制重复累加。优惠计算最多接受 6 张有效用户券，超限拒绝，超时不返回“全局最优”。

容器重建可能改变 IP。网关 Reactor Netty、Java 客户端和 Nginx 代理使用有界 DNS 缓存；服务连接失败返回可重试的不可用状态。本机故障恢复验收见下方报告，不能推导多节点基础设施高可用或生产容量。

## 技术栈

以下版本来自当前依赖配置，不表示它们是最新版本。

| 部分 | 当前配置 |
|---|---|
| Java / Spring Boot | Java 21 / 4.0.8 |
| Spring Cloud / Alibaba | 2025.1.3 / 2025.1.0.0 |
| ORM / 缓存 | MyBatis-Plus 3.5.17 / Redis、Redisson 3.52.0 |
| 数据及消息 | MySQL、RabbitMQ、Elasticsearch |
| 前端 | Vue 3、TypeScript、Vite、Vue Router、Pinia、Element Plus |
| 验证及监控 | JUnit、Playwright、Actuator、Prometheus、Grafana |

保留 Nacos 集成能力；当前本地 Compose 使用固定服务地址与挂载配置，关闭远程 Nacos 配置/发现和 XXL-Job。可靠任务由应用持久化机制处理。

## 项目结构

```text
frontend/                    学生端、管理端、共享请求客户端及浏览器测试
tj-common/                   返回体、资源治理、持久操作、Outbox 等公共基础
tj-api/                      跨服务客户端与 DTO
tj-auth/                     认证服务、公共定义、网关和资源服务 SDK
tj-gateway/                  v2 路由、身份头清理、权限与调用隔离
tj-user/ tj-course/ tj-media/ 用户、课程与媒资
tj-trade/ tj-pay/             订单、退款及支付事实
tj-exam/ tj-learning/         题库、考试评分、学习与笔记
tj-promotion/ tj-remark/      优惠券、折扣、点赞
tj-search/ tj-message/ tj-data/ 搜索、通知、数据看板
deploy/acceptance/            独立验收脚本与版本化迁移
deploy/final/                 最终 Compose、镜像构建与恢复验证
docs/                        验收报告、脱敏证据与运行手册
```

## 本机部署与开发

已有部署可从仓库根目录运行：

```powershell
docker compose -p tianji-final -f deploy/final/compose.yaml --env-file deploy/final/.env up -d --wait --wait-timeout 420
docker compose -p tianji-final -f deploy/final/compose.yaml --env-file deploy/final/.env ps
```

`deploy/final/.env`、签名密钥、已迁移的基础数据、镜像清单和私有配置均在本机保留。**全新克隆不能只凭 Compose 文件自动恢复这套环境**：还需自行准备这些私有配置、基础数据库与镜像，或使用经过核验的本机恢复工件。镜像和数据库备份没有上传到 GitHub。具体步骤及保护范围见 [最终部署说明](deploy/final/README.md)。

运行数据库、缓存和消息数据在命名卷中，新媒资在 `deploy/final/.local/objects`。备份与运行卷作用不同；不要对当前部署执行 `down -v` 或删除数据卷。

开发构建需要 JDK 21、Maven，以及满足前端依赖要求的 Node.js：

```powershell
mvn -B -DskipTests package
npm --prefix frontend ci
npm --prefix frontend run typecheck
npm --prefix frontend run build
```

这组命令构建源代码；`-DskipTests` 不代表测试通过。部署前端改动运行 `python deploy/final/build_web.py`，再执行 Compose 启动命令。后端打包后通过 `build_backend.py --only 服务名 --tag 版本标签` 生成独立镜像，运行 `generate.py` 并启动服务组；Java 21 的 `java`、`javac` 必须位于 PATH。Python 脚本依赖以 [验收依赖文件](deploy/acceptance/requirements.txt) 为准。

## 功能性测试与证据

最新课程配置、持久考试草稿、容器恢复和浏览器回归结果见 [本轮验收报告](docs/admin-configuration-validation-20261007.md)。之前的门户与工作台恢复、完整交易学习路径见 [前端体验验收](docs/frontend-user-experience-validation-20261007.md)。历史压测、迁移和故障测试分别保留各自环境与范围，不能合并为生产性能结论。

2026-10-07 最终回归：浏览器 **25 项通过、0 失败**（13 个执行槽按两端适用范围跳过），相关后端 **15 项通过、0 失败、0 跳过**，类型检查与双端生产构建成功。考试、网关、媒资实际地址变化后恢复访问，学习历史及封面保留；最终 **22 个服务健康**。详见 [脱敏证据](docs/evidence/2026-10-07/admin-ux/validation.json)，这些结果限于当前本机环境。

测试前创建新的专用课程夹具，并同步搜索：

```powershell
python deploy/final/runtime.py migrate
python deploy/final/runtime.py browser_fixture
python deploy/final/runtime.py rebuild_search
$env:ACCEPTANCE_CONTAINER_UI = '1'
$env:TJ_RUNTIME_HOME = (Resolve-Path deploy/final).Path
$env:TJ_RUNTIME_PROJECT = 'tianji-final'
$env:TJ_UI_RUNTIME_HOME = (Resolve-Path deploy/final/.local).Path
npm --prefix frontend test
```

浏览器测试会使用专用学生、管理员和教师账号写入验收数据，覆盖两端页面、1440/845/390 像素布局、登录恢复、课程配置，以及购买 → 视频学习 → 笔记与问答 → 考试与教师评分 → 退款。

- `python deploy/final/verify_exam.py`：在独立 `acceptance_exam` 数据库运行考试与草稿并发测试，并打包考试服务。
- `python deploy/final/verify_pay.py`：在独立 `acceptance_pay` 数据库测试支付可靠性；不调用真实支付商。
- `python deploy/final/verify_dns_recovery.py`：会停止/重建考试服务、网关和媒资服务，验证依赖方访问恢复；应在专用本机验收期间运行，不能当作生产只读健康检查。
- `python deploy/final/verify_history.py`：只读核对当前夹具的完成记录、考试和笔记。
- `python deploy/final/archive_browser_fixtures.py`：正常下架专用 Browser course/free 课程，保留订单、考试、笔记和草稿。
- `python deploy/final/final_backup.py`：保存本机 SQL、私有配置及媒资校验清单。

## 运行边界与文档

本轮功能回归不替代持续压测、真实第三方支付/短信验收、多节点故障恢复或原版全部页面逐项对照。真实环境不会自动回退为模拟成功。

- [可靠性运行手册](docs/reliability-runbook.md)
- [全链路交付状态与验证边界](docs/productionization-status.md)
- [最终部署验证](docs/final-deployment-validation.md)
