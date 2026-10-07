# 天机学堂 · 职业教育在线学习平台

Java 21 / Spring Boot 4 与 Vue 3 双端应用，覆盖课程、购买退款、学习、笔记问答、考试评分、积分优惠券及后台管理。面向个人展示、故障验证和本机压测，支付与短信采用显式模拟服务，不会真实扣款或发送短信。

最终部署为 **`tianji-compact`，共 11 个容器**：4 个业务应用、1 个网关、2 个网页和 MySQL / Redis / RabbitMQ / Elasticsearch。保留原业务模块及 12 个数据库，合并部署单位。模块各自的事务、Outbox、消费者去重和补偿协议继续保留。

旧的 `tianji-final` 和恢复演练容器已退役；旧数据卷、镜像、私有配置、媒资和校验备份保留在本机。**当前演示环境使用合成数据，原历史业务数据没有迁入新环境。** 其他项目的容器不在清理范围内。

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
- 考试：完整答案集合评分、题目快照、草稿自动持久化、多设备版本冲突与本机答案备份。提交后答案不可修改；重复提交和重复事件不会重复累加学习进度。退款后保留已产生的笔记和考试历史，收回收费视频权益。
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
tj-compact/                  四个启动模块、模块隔离与本地调用适配器
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

业务模块有独立 Bean 容器、Mapper、连接池和事务管理器，禁止通过 Bean 覆盖隐藏冲突。同应用内 Feign 契约使用本地适配器，跨应用使用 HTTP。内部 URL 为 `/_modules/{alias}/...`，网关保持 `/api/v2`。保留 Nacos 集成代码，本机关闭远程 Nacos 与 XXL-Job；持久后台任务由应用执行。

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

2026-10-07 的最终本机回归：真实浏览器 **25 项通过、0 失败**，13 个执行槽按两端适用范围预期跳过。覆盖 1440/845/390 像素布局、登录恢复、课程内容与封面、草稿冲突、教师筛选和审计查询，以及 **购买 → 实际视频学习 → 笔记问答 → 考试 → 教师评分 → 退款**。容器退役后再次运行完整路径，检查合并部署没有依赖旧服务。

同轮实现的选定后端回归为 **67 项通过、0 失败/错误/跳过**，覆盖 25 个测试类；本地调用适配器 3 项通过，安全回归 24 项断言通过。类型检查、双端生产构建通过。范围和清理记录见 [最终功能验收](docs/compact-final-functional-validation-20261007.md)及 [脱敏结果](docs/evidence/2026-10-07/compact-final/validation.json)。这些数字代表已执行的选定检查，不代表所有业务组合、外部供应商或生产环境均已验收。

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

后端数据库测试使用 `acceptance_*` 专用库。浏览器会新建课程并写入订单、笔记等合成验收数据。CI 包括后端、前端和独立的合并部署浏览器任务；远程结果以对应提交的 [GitHub Actions](https://github.com/W205614/Vocational-school/actions) 为准。

## 备份、恢复和性能边界

```powershell
python deploy/compact/backup.py
python deploy/final/final_backup.py --verify <完整备份目录>
python deploy/compact/rehearse_recovery.py <完整备份目录> --pause-source
python deploy/compact/redis_roundtrip.py
python deploy/compact/load_fixture.py
python deploy/compact/load_test.py --users 50 --seconds 60
```

完整备份停写后保存 SQL/触发器、私有配置和签名密钥、实际媒资字节、Redis 数据与到期时间、RabbitMQ 数据目录及表行数，校验后发布为 UTC 时间戳恢复点。已在独立卷和端口恢复 12 库、182 表、13 个媒资文件，并通过业务历史读取与浏览器回归；另用 5 个非空 Redis 键验证类型、二进制与 TTL。恢复在同一主机完成；备份没有打包 Docker 镜像字节，换机需要取得记录的镜像。

50 用户的 60 秒短测记录查询 P95 198ms、异步完成 P95 2015ms，只覆盖查询和笔记负载。**合并前后长期容量对照、内存降低 20% 及原历史数据切换尚未验收**；不能用短测或容器数量推导生产容量。正式性能协议及历史切换门禁见 [实现与验证边界](docs/compact-implementation-20261007.md)。

Elasticsearch 当前保留 7.17.29，支持版本迁移另行实施。本期范围为回环入口、模拟支付与单机验证，真实支付、多节点高可用、Kubernetes、服务网格和多租户不在验收范围内。

- [合并部署手册](deploy/compact/README.md)
- [可靠性运行手册](docs/reliability-runbook.md)
- [旧部署记录](deploy/final/README.md)
- [历史交付与压测记录](docs/productionization-status.md)
