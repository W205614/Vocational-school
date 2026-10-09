# 天机学堂 · 职业教育在线学习平台

Java 21 / Spring Boot 4 与 Vue 3 双端应用，覆盖课程、购买退款、学习、笔记问答、考试评分、积分优惠券及后台管理。面向个人展示、故障验证和本机压测，支付与短信采用显式模拟服务，不会真实扣款或发送短信。

最终部署为 **`tianji-compact`，共 11 个容器**：4 个业务应用、1 个网关、2 个网页和 MySQL / Redis / RabbitMQ / Elasticsearch。保留原业务模块及 12 个数据库，合并部署单位。模块各自的事务、Outbox、消费者去重和补偿协议继续保留。

旧的 `tianji-final` 和恢复演练容器已退役；旧数据卷、私有配置、媒资和校验备份保留在本机。累计 162 个不同的多余项目镜像在验收通过后归档校验并删除，另移除 3 个冗余标签；回退材料保留在本机。**当前演示环境使用合成数据，原离线历史业务数据没有迁入新环境。** 最终运行环境已有的购买、学习与考试数据在更新中保留，其他项目不在清理范围内。

2026-10-08 按“先清理，再测试”的要求移除 102 个旧测量、诊断和恢复容器，保留最终部署和数据卷。2026-10-09 前次交付的运行源码 `7aec0a2` 通过后端、完整浏览器、安全、审计、Redis、独立恢复、当前数据搜索 9→7→9 回退及历史保留验证，八类功能证据门禁通过。清理后本项目仅 11 个健康最终容器，168 个原数据卷及 35 个其他项目容器标识保留。该次结果和复现方式见 [交付报告](deploy/compact/reports/functional-20261009.md)，失败复现及修复过程见 [优化记录](deploy/compact/OPTIMIZATION.md)。

同日功能复查修复了领券成功后“我的优惠券”显示空列表的问题：未传 `status` 时查询本人全部状态，显式传入时继续按状态筛选。新增实际浏览器回归在旧应用上复现失败，修复后验证领取、默认列表、刷新保留、状态筛选及不同账号的数据隔离；完整浏览器 32 项通过、0 失败，20 个角色不适用执行槽跳过，后端 134 项、安全 26 项、3 类审计及签到、优惠券、通知与媒资补测通过。更新前快照中 18 张表的 1,617 个记录标识及 47 个媒资文件哈希保留，详见 [本次修复结果](deploy/compact/reports/coupon-query-fix-20261009.json)。本次未重复独立恢复、搜索版本回退或性能测量。

本机按保留现有容器的要求，仅停止交易应用、替换 `/app/app.jar` 后启动；原 JAR 已备份，11 个容器的 ID、镜像引用、挂载和端口未改，全部健康。修复保存在交易容器的可写层，原镜像本身不包含补丁；以后若重建容器，须先从修复后的源码构建镜像。JAR 校验和、失败日志及更新记录留在 `deploy/compact/.local/coupon-fix-20261009/`，不提交私有配置、账号或浏览器 trace。

## 访问入口

| 入口 | 本机地址 |
| --- | --- |
| 学生门户 | <http://127.0.0.1:24500/> |
| 管理与教师工作台 | <http://127.0.0.1:24501/> |
| API 网关 | <http://127.0.0.1:24310/api/v2/> |

所有发布端口绑定本机回环地址。随机生成的学生、管理员和教师演示账号保存在 `deploy/compact/.local/accounts.json`，该文件及签名密钥、配置、备份、测试诊断不提交到 GitHub。

## 功能与安全

- 学生端：分类检索、课程详情、购物车、优惠方案、订单与模拟支付、取消退款、实际本地视频播放和学习进度、私人笔记、收藏、讨论点赞、考试、积分签到、通知及个人设置。
- 管理端：实际业务看板、课程内容与封面、章节关联、媒资上传、题库与不可变试卷版本、授权教师评分、用户和权限、优惠券、退款审核及通知。教师待评分列表支持分页、课程和学生筛选；管理审计可按模块、操作者、结果和请求编号查询。
- 考试：完整答案集合评分、题目快照、草稿自动持久化、多设备版本冲突与本机答案备份。提交后答案不可修改；重复提交和重复事件不会重复累加学习进度。考试历史按本人分页，换浏览器后仍能查看成绩、答案和教师反馈。退款或权益到期后保留历史读取，禁止继续考试、写草稿或播放收费视频；页面解释原因并提供历史入口。
- 课程权益：以订单明细的有效权益重新聚合期限，永久权益优先；退款可缩短期限，全部撤销后禁止继续学习写入，保留已产生的学习历史。发放、撤销及重复和乱序事件共用权限校验与用户／课程锁。
- 订单：本人只能删除已关闭订单，重复删除幂等，其他状态返回冲突；支付和退款使用同一订单锁，保留迟到回调需要的订单事实。管理列表分别显示下单时的课程名称快照和学员姓名。
- 会话：独立会话与刷新令牌轮换、撤销、刷新时重新核对账号。改密、禁用和角色变化更新安全版本；网关缓存最长 5 秒。按账号与实际来源 IP 限制登录尝试，短暂限制后可以重试。
- 权限：网关默认拒绝未声明接口，角色规则显式列出；清除伪造身份头，内部接口要求服务凭证。业务侧校验数据归属，运行数据库账号只拥有所属库的 DML 权限，迁移使用单独入口。
- 可靠性与资源：持久操作、事务 Outbox、消费去重、失败重放与补偿；有界执行器、内部请求和上传并发限制、图片大小及像素限制。库存和限领以数据库为准，缓存和搜索可重建。

当前保留健康检查和受凭证保护的 Actuator/Prometheus 指标端点，管理页面可以查看失败与补偿任务。最终 11 容器组没有单独运行 Prometheus/Grafana；原监控部署文件作为历史配置保留。

## 部署结构

| 应用 | 包含模块 | 本机端口 |
| --- | --- | --- |
| identity | auth、user | 24001 |
| commerce | trade、pay、promotion | 24002 |
| education | course、learning、exam、remark | 24003 |
| support | media、search、message、data | 24004 |
| gateway | 入口鉴权、路由 | 24310 |

```text
frontend/                    双端应用、共享客户端与浏览器测试
tj-compact/                  四个启动模块、模块隔离与标准 HTTP 调用
tj-common/ tj-api/ tj-auth/   公共基础、调用契约、会话与安全 SDK
tj-user/ tj-course/ tj-exam/ tj-learning/ tj-remark/  身份与教学业务
tj-trade/ tj-pay/ tj-promotion/                    交易业务
tj-media/ tj-search/ tj-message/ tj-data/          支撑业务
tj-gateway/                  网关与显式接口权限清单
deploy/compact/              源码启动、验收、备份恢复与性能脚本
deploy/acceptance/           结构基线、版本化迁移、合成夹具与隔离测试
deploy/final/                旧部署与可复用的备份组件
docs/                       验收报告、脱敏证据、运行手册
```

业务模块有独立 Bean 容器、Mapper、连接池和事务管理器，禁止通过 Bean 覆盖隐藏冲突。模块调用使用标准 Feign HTTP；同宿主指向回环地址，跨宿主指向服务地址。内部 URL 为 `/_modules/{alias}/...`，网关保持 `/api/v2`。连接超时为 1500ms，读取超时为 5000ms，内部调用按深度保留处理容量。保留 Nacos 集成代码，本机关闭远程 Nacos 与 XXL-Job；持久后台任务由应用执行。

## 从源码启动

需要 JDK 21、Maven、Node.js 22/npm、Python 3.12+ 和 Docker Compose，命令须在 PATH 中。首次启动从仓库结构和合成数据初始化，不依赖私有历史 JAR 或旧库；初始化器拒绝覆盖未经它管理的已有业务库。

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

已有部署检查和暂停：

```powershell
docker compose -p tianji-compact -f deploy/compact/compose.yaml --env-file deploy/compact/.env ps
docker compose -p tianji-compact -f deploy/compact/compose.yaml --env-file deploy/compact/.env stop
# 恢复启动：
python deploy/compact/setup.py up
```

修改代码后重新运行 `setup.py build` 和 `setup.py up`。`-Pcompact` 构建合并应用；默认 Maven 配置仍支持原独立模块，用于隔离开发。运行数据在命名卷，上传字节在 `deploy/compact/.local/objects`；**不要使用 `down -v` 或 Docker 全局清理删除数据卷。** 详细操作见 [合并部署手册](deploy/compact/README.md)。

## 实际页面验收

2026-10-09，运行源码 `7aec0a2` 的真实浏览器回归为 **31 项通过、0 失败**，19 个执行槽按角色适用范围预期跳过。覆盖 1440/845/390 像素布局、登录恢复、课程内容与封面、草稿冲突、教师筛选和审计查询，以及 **购买 → 实际视频学习 → 笔记问答 → 考试 → 教师评分 → 退款**。断言核对管理订单的课程快照与学员姓名，退款后不能开始考试且能读取本人历史；题库查询等待期间禁止选择和确认，返回后按记录 ID 保持正确选择。

同轮选定后端与真实 HTTP 回归为 **134 项通过、0 失败/错误/跳过**，其中真实套接字检查 9 项；安全回归 26 项断言通过，包括 60 个并发 HTTP 请求的身份隔离。部署与证据工具 29 项、前端安全高亮 3 项、类型检查及双端生产构建通过。另用新浏览器会话核对退款前的考试成绩、答案和教师反馈，答题控件不可修改；学生、管理订单和教师手机页面均复查。上述数字代表实际执行的选定检查，不代表所有业务组合、外部供应商或生产环境均已验收。历史验收见 [2026-10-07 记录](docs/compact-final-functional-validation-20261007.md)。

```powershell
python deploy/compact/check_policy.py
python deploy/compact/run_backend_tests.py
python deploy/compact/run_transport_tests.py
npm --prefix frontend run typecheck
python deploy/compact/security_smoke.py
# 安全脚本会短暂限制合成学生账号：
Start-Sleep -Seconds 32
cd frontend
npx playwright install chromium
cd ..
python deploy/compact/run_browser.py
python deploy/compact/audit_smoke.py
```

后端数据库测试使用 `acceptance_*` 专用库。浏览器会新建课程并写入订单、笔记等合成验收数据。Linux CI 的响应体超时夹具曾在响应头阶段提前超时，现已用首字节刷新和同步信号固定触发条件，独立 Linux 九项检查通过；业务连接和读取超时没有改变。CI 包括后端、前端和独立的合并部署浏览器任务；远程结果以对应提交的 [GitHub Actions](https://github.com/W205614/Vocational-school/actions) 为准。

## 备份、恢复和性能边界

```powershell
python deploy/compact/backup.py
python deploy/final/final_backup.py --verify <完整备份目录>
python deploy/compact/rehearse_recovery.py <完整备份目录> --pause-source
python deploy/compact/redis_roundtrip.py
python deploy/compact/load_fixture.py
python deploy/compact/load_test.py --users 50 --seconds 60
```

完整备份停写后保存 SQL/触发器、私有配置和签名密钥、实际媒资字节、Redis 数据与到期时间、RabbitMQ 数据目录及表行数、内容校验和，校验后发布为 UTC 时间戳恢复点。恢复点 `20261009T043539.412759Z` 已在独立卷和端口恢复 12 库、183 表、45 个媒资文件，表内容和媒资哈希一致，历史业务读取及恢复后的 31 项浏览器回归通过。该备份包含 1 条 Redis 记录；另用 5 个非空键、9 项检查验证二进制、类型、到期时间及拒绝覆盖非空目标。恢复在同一主机完成；备份没有打包 Docker 镜像字节，换机需要取得记录的镜像。备份结束后等待原服务健康再返回。

2026-10-08 用户确认改为**功能、安全与恢复检查通过后交付**，取消延迟数值门槛，本轮不等待完整性能采样。`perf-3h-v1` 工具和原始失败记录继续保留；修复后的 180 分钟正式采样、200 并发容量及 Java RSS 降低 20% **没有完成验证**。历史短测、修复前测量或容器数量不能代替当前性能证据。协议与证据状态见 [优化记录](deploy/compact/OPTIMIZATION.md)。

当前使用 Elasticsearch 9.5.5、Java API Client 9.5.0 与 Rest5Client，新卷从 MySQL 重建投影。回退保留当前核心业务的 7.17.29 兼容版本，并从回退时的 MySQL 重建索引，保留升级后的新增订单、学习及考试数据。旧搜索卷留存，禁止用旧 SQL 备份覆盖新业务数据。本期范围为回环入口、模拟支付与单机验证，真实支付、原离线历史数据正式切换、多节点高可用、Kubernetes、服务网格和多租户不在验收范围内。

- [合并部署手册](deploy/compact/README.md)
- [可靠性运行手册](docs/reliability-runbook.md)
- [旧部署记录](deploy/final/README.md)
- [历史交付与压测记录](docs/productionization-status.md)
