# 天机学堂 · 职业教育在线学习平台

[![CI](https://github.com/W205614/Vocational-school/actions/workflows/reliability.yml/badge.svg?branch=dev)](https://github.com/W205614/Vocational-school/actions/workflows/reliability.yml)

基于 Java 21 / Spring Boot 4 与 Vue 3 的在线学习平台，提供学生门户和管理／教师工作台，覆盖 **游客选课 → 购买与学习计划 → 学习积分 → 考试评分 → 错题复习 → 月度激励**，并保留退款与历史读取流程。

本项目用于个人展示与本机功能、故障恢复验证。默认使用合成演示数据，支付和短信采用显式模拟服务，不会真实扣款或发送短信。

## 功能

- **课程与交易**：分类检索、课程详情、购物车、优惠方案、订单、模拟支付、取消及退款；管理端支持课程内容、封面、章节、优惠券和退款审核。
- **游客与选课**：未登录可搜索已上架课程并查看公开目录，购买和学习时登录并返回原课程；收费媒体、考试与个人数据仍需授权。
- **学习与互动**：周小节目标、最近进度续学、可搜索的时间点私人笔记、收藏、问答分页与教师答疑；工作台显示真实待考试、待评分和未读互动提醒，提醒失败显示未知及重试。
- **积分与激励**：首次完成小节、签到和教师采纳回答计分；自然月赛季、历史榜、签到日历、个人成就、积分明细和可配置的赛季优惠券奖励。
- **考试与评分**：题库、不可变试卷版本、草稿自动保存、多设备版本冲突检测、提交与授权教师评分；学生可跨浏览器查看完成评分后的成绩、发布快照答案和教师反馈，并在错题本复习、标记掌握；复习不改原成绩。
- **管理与教师工作台**：业务看板、媒资上传、用户与角色权限、订单查询、操作审计；教师待评分列表支持分页及课程、学生筛选，授课学情和答疑按课程授权，授课与评分权限分别校验。
- **权益与历史**：课程权益随购买、退款和到期重新聚合；失效后禁止收费视频播放及学习、考试写入，保留本人已有的学习和考试历史。

## 界面预览

以下 4 张截图来自 2026-10-16 实际 8dc 主部署，逐张视觉检查隐私，公开文件与原始捕图的 SHA-256 一致。截图不含身份、账号、手机号、答卷答案或敏感日志；课程、日历和运营数据是合成演示，门户功能片段明确标为“界面示例”，不代表学员规模或教学效果。

**游客门户 · 桌面**

![游客门户桌面](deploy/compact/reports/assets/20261016/portal-1440.png)

**游客门户 · 手机**

![游客门户手机](deploy/compact/reports/assets/20261016/portal-390.png)

**积分与签到 · 手机**

![签到日历与本人赛季摘要](deploy/compact/reports/assets/20261016/points-calendar-390.png)

**管理员赛季运营**

![赛季状态、积分来源与奖励管理](deploy/compact/reports/assets/20261016/season-admin-1440.png)

## 技术栈

| 层次 | 技术与用途 |
| --- | --- |
| 后端 | Java 21、Spring Boot 4、Spring Cloud、MyBatis-Plus；业务接口、事务及持久任务 |
| 前端 | Vue 3、TypeScript、Vite、Element Plus、Pinia；学生与管理双端、共享客户端 |
| 数据与消息 | MySQL、Redis、RabbitMQ；业务事实、缓存、异步事件与补偿 |
| 搜索 | Elasticsearch 9.5.5、Java API Client / Rest5Client；从 MySQL 重建课程投影 |
| 验证 | JUnit、真实数据库与 HTTP 回归、Vitest、Playwright、Python 验收工具 |
| 部署 | Docker Compose、源码镜像构建、健康检查、停写备份与独立恢复 |

## 架构

默认运行组为 `tianji-compact`，共 **11 个容器**：4 个业务应用、1 个网关、2 个网页，以及 MySQL、Redis、RabbitMQ、Elasticsearch。合并的是部署单位，原业务模块及 12 个数据库继续保留。

```text
学生门户 / 管理与教师工作台
             │
       Gateway /api/v2
             │
    ┌────────┼────────┬─────────┐
 identity  commerce education support
    └────────┴────────┴─────────┘
             │
 MySQL / Redis / RabbitMQ / Elasticsearch
```

| 应用 | 包含模块 | 默认本机端口 |
| --- | --- | --- |
| identity | auth、user | 24001 |
| commerce | trade、pay、promotion | 24002 |
| education | course、learning、exam、remark | 24003 |
| support | media、search、message、data | 24004 |
| gateway | 入口鉴权、路由 | 24310 |

各业务模块拥有独立 Bean 容器、Mapper、连接池与事务管理器。模块间使用标准 Feign HTTP，同宿主指向回环地址，跨宿主指向服务地址；内部路径为 `/_modules/{alias}/...`，要求服务凭证。调用保留身份、请求编号和深度限制，各模块独立执行事务及补偿。Nacos 集成与原独立启动入口保留，本机关闭远程 Nacos 与 XXL-Job，持久后台任务由应用执行。

## 项目结构

```text
frontend/                    学生端、管理端、共享客户端与浏览器测试
tj-compact/                  四个业务启动模块与模块隔离
tj-common/ tj-api/ tj-auth/   公共基础、调用契约、会话与安全 SDK
tj-user/ tj-course/ tj-exam/ tj-learning/ tj-remark/  身份与教学业务
tj-trade/ tj-pay/ tj-promotion/                    交易业务
tj-media/ tj-search/ tj-message/ tj-data/          支撑业务
tj-gateway/                  网关与显式接口权限清单
deploy/compact/              启动、验收、备份恢复及性能工具
deploy/acceptance/           结构基线、迁移、合成夹具与隔离回归
deploy/final/                旧部署及可复用备份组件
docs/                       运行手册与历史验收证据
```

## 快速启动

### 首次从源码启动

准备 JDK 21、Maven、Node.js 22/npm、Python 3.12+、Docker Compose 及可用镜像源，确认命令已加入 PATH。在仓库根目录执行：

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

初始化使用仓库内的结构与合成数据，不依赖私有历史 JAR 或旧库；发现未经初始化器管理的已有业务库时拒绝覆盖。构建记录 Git 提交和产物哈希，拒绝未提交的生产源码改动，修改代码后应先提交再构建。`-Pcompact` 构建合并应用，默认 Maven 配置仍支持独立模块。

| 入口 | 本机地址 |
| --- | --- |
| 学生门户 | <http://127.0.0.1:24500/> |
| 管理与教师工作台 | <http://127.0.0.1:24501/> |
| API 网关 | <http://127.0.0.1:24310/api/v2/> |

主要页面入口：

| 使用者 | 页面 |
| --- | --- |
| 游客 | `/` 课程检索、`/courses/:id` 公开介绍与目录 |
| 学生 | `/learning` 学习工作台、`/points` 积分签到与月榜、`/review` 错题复习、`/notes` 私人笔记、`/exams` 考试、`/cart` 结算、`/messages` 通知 |
| 教师 | 管理端 `/teaching` 授课学情与答疑、`/exams` 授权评分 |
| 管理员 | 管理端 `/seasons` 赛季奖励运营，以及课程、题库、优惠券、通知和审计页面 |

所有发布端口绑定回环地址。随机生成的学生、管理员、教师账号保存在 `deploy/compact/.local/accounts.json`；该文件、私有配置、签名密钥及备份不提交到 GitHub。

### 已有部署

```powershell
# 查看状态
docker compose -p tianji-compact -f deploy/compact/compose.yaml --env-file deploy/compact/.env ps
# 暂停
docker compose -p tianji-compact -f deploy/compact/compose.yaml --env-file deploy/compact/.env stop
# 恢复启动
python deploy/compact/setup.py up
```

代码更新需重新构建镜像；若本地 Compose 已固定镜像 ID，还需核对其引用与新构建清单一致再重建容器。运行数据在命名卷，上传字节在 `deploy/compact/.local/objects`。保留数据与配置，禁止用 `down -v` 或全局 Docker prune 清理部署。操作细节见 [部署手册](deploy/compact/README.md)。

已有部署升级时，在完成停写备份、提交源码及迁移后，启动新版应用前运行：

```powershell
python deploy/compact/client_contexts.py
```

它根据当前源码补入缺失的 Feign context，从已有对应服务配置复制 URL 和超时等值，不覆盖已存在的 context／自定义配置，不重建 Compose。工具先验证全部待更新模块，原始配置保存在私有 `deploy/compact/.local/client-context-sync/<UTC批次>/`；该目录不提交 GitHub。不要用重新运行 `setup.py prepare` 替代这一既有部署配置补全步骤。

## 使用流程与业务规则

1. 管理员维护课程、章节、媒资、题库和试卷，并分别配置授课与评分权限。
2. 游客搜索课程、查看公开介绍和目录；购买、学习或查看个人记录时登录，随后返回原操作位置。完结课程保留介绍但停止报名，草稿、下架和删除课程不公开。
3. 学生加入购物车并完成模拟支付，在「我的学习」设置 1–50 小节／周的目标，从最近小节继续学习，记录可搜索的私人笔记或参加公开问答。
4. 首次完成小节后查看到账积分、当月榜和签到日历；积分由服务端完成事实产生，重复播放、重考及消息重放不重复计分。
5. 学生保存答题草稿并提交考试；授权教师评分，整份答卷完成评分后学生查看发布时的答案、解析及反馈，在错题本复习和标记掌握。复习不改变原考试成绩。
6. 教师按所授课程查看学情并答疑；管理员查看未来赛季规则和发奖结果。退款或权益到期后保留本人历史，收费视频和学习／考试写入继续受后端权益限制。

待完成考试按当前有效权益课程中“每个考试小节的最新已发布试卷”计数：尚未通过，且没有待评分答卷；包括尚未开始、作答中和未通过可重考。待评分数量单列。未读互动通知统计本人未读、已发布且未过期的互动消息，包含课程回复、教师采纳和公共互动，按完整数据计数并避免公共通知重复计入。任一提醒依赖不可用时，该项显示未知及原因，可独立重试；不能用 0 代替失败，也不影响已有课程和学习计划展示。

| 积分行为 | 规则 |
| --- | --- |
| 首次完成视频或考试小节 | 每小节 10 分，每日上限 50 分 |
| 每日签到 | 基础 1 分；连续 7／14／28 天另奖 10／20／40 分 |
| 学生回答被授课教师采纳 | 每个有效回答 5 分，每日上限 20 分；同一回答仅奖励一次，不能通过采纳自己的回答获奖 |
| 普通发言、私人笔记、评价 | 不计分 |

赛季按 `Asia/Shanghai` 的自然月划分，未来月份显示“未开始”。名次按积分降序、达到该积分的时间升序、用户 ID 升序确定；无积分时显示“暂未上榜”。次月 2 日零点后核对事件和消费回执，未完成事件或依赖异常会阻断结算；最终榜冻结后，迟到积分进入核对流程，不自动改写已发奖名次。新月份使用统一积分事实与快照，不动态创建业务表；上线前旧赛季只读，不补造积分或历史奖励。

成就包含首次结课、首次通过考试、连续七天完成学习及赛季荣誉。优惠券奖励默认关闭，只可配置未开始的赛季，启用的名次区间不得重叠，开季后规则锁定；发放校验库存、个人限领、发放期和有效期，失败保留记录并由管理员重试，重复请求不重复发券。

结算可从全部有效用户券中选择最多 6 张计算方案，显示券名称、规则及实际到期时间，也可以明确不使用优惠券。持券超过 6 张不会阻断无券购买；优惠计算超时或不可用时给出提示，不宣称得到了最优方案。

本人赛季奖励按全部月份分页查询，奖励标签、页码和榜单月份保存在 URL，刷新可恢复；榜单月份不冒充奖励列表的月份过滤。管理员配置奖励优惠券时，可按名称进行服务端查询并逐页加载，不限于本地前 100 张；已有选券保留名称，失败时禁用陈旧选项及保存，重试继续原查询。

详细规则与数据边界见 [学习体验说明](docs/learning-experience.md)。

## 核心机制与安全

- **交易与异步可靠性**：支付、退款和关闭订单删除共用订单锁；事务 Outbox、消费去重、失败重放与补偿处理重复、迟到事件。本人只能删除已关闭订单，管理查询区分课程名称快照与学员姓名。
- **权益与考试一致性**：权益从有效订单明细重新聚合，永久权益优先，退款可缩短期限；考试保留题目快照和完整答案集合，提交后只读，重复提交或事件不重复累加进度。
- **会话与权限**：刷新令牌轮换、独立会话撤销；改密、禁用和角色变化更新安全版本，网关缓存最长 5 秒。登录按账号及实际来源 IP 限流，网关默认拒绝未声明接口并清除伪造身份头，业务侧核对数据归属。
- **资源与数据边界**：运行账号只拥有所属数据库的 DML 权限，迁移单独执行；有界执行器、内部请求及上传并发限制、图片大小与像素限制。库存和限领以数据库为准，缓存与搜索投影可重建。
- **可观测性**：容器健康检查、受凭证保护的 Actuator/Prometheus 指标、失败及补偿任务查询、持久操作审计。当前运行组没有单独部署 Prometheus/Grafana。

## 本轮交付与验证

运行镜像绑定生产源码 **`8dcf8e7d44c6048318ad1e306e1425c8fd40fb70`**。其 [CI 38857524064](https://github.com/W205614/Vocational-school/actions/runs/38857524064) 的 `backend`、`frontend`、`compact-browser` 三个作业均成功。主部署核验时 11 个容器健康，7 个应用／网页镜像、5 个实际 JAR 及全部网页资源与源码绑定一致；原数据卷、挂载、Compose 非镜像配置和其他项目保留。

| 范围 | 已核实结果 |
| --- | --- |
| 后端 | 195 项通过，0 失败／错误／跳过，包含 9 项真实套接字检查。 |
| 前端与普通浏览器 | 源码 CI 的类型、单元及双端构建通过；主部署普通浏览器 37 通过／0 失败／35 条件跳过，跳过不计为通过。 |
| 安全与数据 | 安全 26 项、持久审计 3 项、Redis 5 条记录／9 项检查通过；18 张表的 1,696 个预存 ID 与 51 个原媒资保留。停写备份已校验，包含更新后的 53 个媒资。 |
| 实际界面 | 三角色 8 路由各 1440／768／390 像素，共 24 状态通过；有限可访问性检查 8 项通过，4 张公开截图已逐张审查并核对原始哈希。 |
| 恢复与奖励连续链路 | 原始恢复 183 表、51 媒资、Redis 4 条记录与 4 项增量迁移通过；隔离浏览器 42 通过／0 失败／30 角色跳过。真实互动验证 5 次教师采纳的 20 分日上限；受控 Clock 结算、库存发券、回执及真实 HTTP 本机模拟支付用券完成。同券同单只读续验核对冻结快照、权益与 broker 回执，补充 6 项 UI／6 项 HTTP 检查通过，功能门禁 11 项通过。原 FAILED 记录保留，续验未重复发券或付款；支付仅为 local simulator。 |
| 搜索兼容与回退 | ES9→7→9 三阶段浏览器各 37 通过／0 失败／35 条件跳过，各阶段投影与原记录保留均通过；ES7 原 2 个测试方法／27 个断言通过。support 适配 `189f31e` 源自 8dc，其余 6 组保持 8dc。[复现及源码归属](deploy/compact/reports/search7-8dcf8e7-reproduction.md)。 |
| 最终清理与回滚保留 | 已移除 59 个临时容器及退役后无任何容器引用的 9 个网络；累计网络清理 12＋1＋9＝22 个。主组 11 容器健康，27 个演练卷全部保留，清理前后全局 252 卷集合一致；未使用 `-v`，未删除镜像。8dc／912／旧运行字节派生回滚镜像、2 份已校验备份及其他项目网络保留。252 为本次全局卷数，更新前 224 个原卷基线另列报告，不合并计数。 |

主 UI 只读检查时，待考／待评分／未读互动为 1／0／0，两个提醒错误字段均为 `null`；奖励记录实际为 0，管理员对实际 10 张券按 `name` 查询、选择后取消成功。大于 20 条的分页使用独立受控界面契约证据，不能代替真实发奖结果。

键盘焦点与导航、赛季下拉键盘操作、减少动态效果、移动目标间距及表格内部滚动已检查。抽查的 24 个目标中，4 个导航文字高度约 22.4px 并依赖周围间距，14 个小于 44px 舒适目标；这不是完整 WCAG 2.2 认证。门户技能检查为源码 0 FAIL／0 WARN、页面 0 FAIL／2 WARN，剩余为重复课程格和缺少公开宣传视频。

“实际用户测试”指代理使用游客、学生、教师和管理员进行操作验收，不是招募真人的满意度研究。912 等阶段结果、2cf 未部署的修复过程、失败诊断和测试替身边界集中保留在 [交付报告](deploy/compact/reports/README.md)，不合并为当前版本的通过数。

运行镜像源码与后续文档提交分别记录。本次文档提交的 CI 可从 [dev 当前提交](https://github.com/W205614/Vocational-school/commit/dev) 的 Checks 查看；最终交付回复提供准确的文档提交 SHA 与对应 CI 链接。

常用检查入口：

```powershell
python deploy/compact/check_policy.py
python deploy/compact/run_backend_tests.py
npm --prefix frontend run typecheck
npm --prefix frontend run test:unit
npm --prefix frontend run build
# 首次浏览器检查需安装 Chromium
Push-Location frontend
npx playwright install chromium
Pop-Location
python deploy/compact/run_browser.py
```

后端使用 `acceptance_*` 专用库；普通浏览器回归创建课程、订单等合成数据。高级激励回归仅在显式隔离副本或临时 CI 启用，验证错题、历史榜展示和真实学习／奖励消息链路，不向保留部署补造历史赛季；可控时钟测试另外验证月度结算，主机时间不变。完整回归与核心回归清单由本地和 CI 共用，独立入口支持 `--suite regression/core`。环境准备、安全与审计检查顺序见 [部署手册](deploy/compact/README.md#回归)。CI 包括 `backend`、`frontend`、`compact-browser` 三个作业，状态以 [对应提交的 Actions 记录](https://github.com/W205614/Vocational-school/actions) 为准。

## 备份与恢复

完整备份停写后保存 SQL／触发器、私有配置和签名密钥、媒资字节、Redis 数据与到期时间、RabbitMQ 数据目录及内容校验和。校验后保存为独立恢复点，结束时恢复原服务并等待健康。

```powershell
python deploy/compact/backup.py
python deploy/final/final_backup.py --verify <完整备份目录>
python deploy/compact/rehearse_recovery.py <完整备份目录> --pause-source
```

恢复使用新的空目录、独立项目、卷与端口，先核对表内容、对象哈希及原业务历史，再运行新增业务验证。备份记录镜像标识但不包含镜像字节，换机需另行取得对应镜像。本轮另行保留经字节核验的旧运行容器 rootfs 派生回滚镜像及归档；它们不等于恢复了旧 OCI 镜像身份，也不证明升级后的数据库可直接降级运行旧版。具体材料和验证范围见本轮报告。搜索回退从切换时的 MySQL 重建投影，禁止用旧 SQL 覆盖新增业务数据。详见 [恢复操作](deploy/compact/README.md#备份与独立恢复) 和 [可靠性手册](docs/reliability-runbook.md)。

恢复、搜索切换及容器退役的本轮状态统一记录在上面的交付表；具体清单、失败记录和回滚材料见交付报告。

## 已知边界

- 本轮保留当前compact既有数据，18张业务表的1696个预存ID及51媒资已核对通过。外部另一套旧生产环境的完整导入及正式割接尚未验收；这不表示本轮清空或丢失当前compact数据。真实支付／短信及多节点高可用未验收。
- 优惠方案最多计算选定的 6 张用户券；持券更多时先选券，也可以不用券继续购买。积分基于系统记录的完成状态，不宣称具备完整防作弊能力；赛季优惠券默认关闭。
- 性能工具与旧部署能力保留，正式性能采样和容量指标未完成验证，不能用容器数量推导性能结论；具体协议与状态见交付报告。

## 文档

- [学习、赛季与教学使用说明](docs/learning-experience.md)
- [部署与验收手册](deploy/compact/README.md)
- [交付报告、修复与清理记录](deploy/compact/reports/README.md)
- [可靠性运行手册](docs/reliability-runbook.md)
- [旧部署记录](deploy/final/README.md)
- [历史交付与压测状态](docs/productionization-status.md)
