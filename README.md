# 天机学堂 · 职业教育在线学习平台

天机学堂是基于 Java 21 / Spring Boot 4 和 Vue 3 的在线学习平台，提供学生门户、教师与管理工作台，串联 **选课 → 购买 → 学习 → 考试 → 错题复习 → 积分激励**。

适合本地体验在线教育业务、学习 Java 多模块架构与前后端协作。默认部署使用合成演示数据，支付与短信为本地模拟，不会真实扣款或发送短信。

本项目按**个人可维护的单机演示平台**交付。真实用户运营、真实支付／短信、云视频、培训学时认证、多节点高可用及生产容量不属于当前验收范围；不能将角色功能测试通过解释为支持 200 并发或生产 SLA。

视频学习由服务端播放会话累计计时，折算达到视频时长的 **90%** 后完成小节并按原规则发放奖励；支持 0.5～2 倍速。暂停、缓冲、隐藏页面和拖动不会直接增加计时，断线超过 30 秒的区间不补计。同一学生仅一个页面计时，历史已完成记录保留。记录表示播放会话事实，不能证明本人实际观看。

本地视频上传由服务端使用 [ffprobe](https://ffmpeg.org/ffprobe.html) 解析实际时长并检查容器及视频编码，管理员无需手填时长；支持 H.264 MP4、VP8／VP9 WebM，解析失败或其他视频编码会拒绝登记，不进行转码。音轨编码未做兼容性检查。媒体所在的 `support` 镜像包含该工具。新播放会话按媒资毫秒时长计算完成门槛，目录仍按整数秒显示，已有会话快照和历史完成记录保留。播放地址默认有效 10 分钟，学生页面会在到期前续签，并保留播放位置、倍速与暂停状态；续签仍重新检查课程权益。签名地址属于短期访问凭据，不能当作 DRM 或防分享保证。

### 本机真实课程素材

“Java微服务实战”用于演示从选课到播放真实教学录屏的过程。素材来自用户本机提供的《天机学堂1.0》项目开发视频，按 day01～day12 组织成 12 章、161 个视频小节，原文件合计约 11.05 GiB、34.71 小时。它们讲授平台项目开发，与原有演示课程分别管理；不会替换原课程、学习历史或已发奖励。

课程与视频是本机数据，**不随 GitHub 源码分发，也不会在全新初始化时自动出现**。使用自己的本地素材时，先按[本机部署手册](deploy/compact/README.md)检查受控导入流程。公开上传仍限制为 200 MiB；受控离线导入单文件最多 512 MiB，只在显式启用时读取指定的只读目录，结束后关闭导入入口。支付、短信仍为模拟环境，课程素材的教学内容不代表平台已具备真实用户运营或培训认证能力。

旧媒资的人工时长不会自动回填，新规则仅保证新上传／导入素材的探测结果。约 11.05 GiB 的课程对象另需同量备份空间；原视频、对象副本、备份和镜像包分别占用磁盘。完整备份会短暂停写，不能当作无中断服务或异地容灾。

真实课程、长视频续签、时长解析及相关新课程缺陷修复的功能证据与执行边界见[真实本地课程交付记录](deploy/compact/reports/real-local-course-20261026.md)。

2026-10-26 较早批次完善了临时凭据与首次改密、服务端学习计时、考试并发和资源隔离；该批四角色体验、恢复升级及数据保留结果见[早期交付记录](deploy/compact/reports/personal-demo-20261026.md)。这些历史结果不替代本次重新验证，也不证明容量、真实渠道或容灾能力。

[快速部署](#快速部署) · [首次使用](#首次使用) · [界面预览](#界面预览) · [项目结构](docs/project-structure.md) · [更多文档](#更多文档)

## 功能与解决的问题

| 场景 | 提供的功能 | 解决的问题 |
| --- | --- | --- |
| 选课与购买 | 游客课程检索、课程介绍与目录、购物车、优惠券、模拟支付、退款 | 将选课、交易和课程学习权益串联起来 |
| 日常学习 | 周学习计划、进度续学、时间点私人笔记、课程问答与教师答疑 | 帮助学生安排进度、记录疑问并继续学习 |
| 考试与复习 | 答题草稿、提交考试、教师评分、成绩与反馈、错题本 | 将考试结果与后续复习衔接起来 |
| 学习激励 | 签到日历、学习积分、月度榜单、成就、可配置优惠券奖励 | 集中展示学习记录，为持续学习提供反馈 |
| 教学与运营 | 课程与章节管理、媒资上传、题库试卷、授课学情、角色权限、操作审计 | 为教师和管理员提供统一工作入口 |

退款或课程权益到期后，学生仍可查看本人的学习、考试历史；收费视频和新的学习、考试操作由后端校验权限。赛季优惠券奖励默认关闭，可由管理员配置。

## 界面预览

以下为 2026-10-16 本地演示部署截图，使用合成数据；门户中的功能片段标注为“界面示例”。

**学生门户 · 桌面**

![学生门户桌面效果](deploy/compact/reports/assets/20261016/portal-1440.png)

**管理工作台 · 赛季与奖励**

![管理端赛季与奖励](deploy/compact/reports/assets/20261016/season-admin-1440.png)

<details>
<summary>查看移动端：课程门户与积分签到</summary>

<p>
  <img src="deploy/compact/reports/assets/20261016/portal-390.png" alt="移动端课程门户" width="300">
  <img src="deploy/compact/reports/assets/20261016/points-calendar-390.png" alt="移动端积分与签到日历" width="300">
</p>

</details>

## 快速部署

### 1. 准备环境并获取代码

默认方式为**本机源码构建 + Docker Compose 部署**。准备以下工具并加入 `PATH`：

- Git、JDK 21（含 `javac` 和 `keytool`）、Maven。
- Node.js 22 / npm、Python 3.12+。
- Docker Engine 与 Compose v2；Windows 可使用 Docker Desktop 的 Linux 容器模式。
- 能下载 Maven/npm 依赖及 Docker 镜像的网络。Docker 内存预算建议预留 12 GB（按当前容器内存上限估算，并非实测最低配置）。

以下命令以 **Windows PowerShell** 为例，后续均在仓库根目录执行：

```powershell
git clone --branch dev https://github.com/W205614/Vocational-school.git
cd Vocational-school
python -m pip install -r deploy/compact/requirements.txt
npm --prefix frontend ci
```

### 2. 初始化、构建并启动

**以下步骤用于全新部署。已有部署直接看[日常启停与常见问题](#日常启停与常见问题)，升级请按[部署手册](deploy/compact/README.md)操作。**

按顺序逐条执行；某一步报错时先处理错误，再继续：

```powershell
# 生成本地配置、签名密钥与中间件密码
python deploy/compact/setup.py prepare

# 启动基础服务，初始化数据库、演示数据与登录账号
python deploy/compact/setup.py init

# 从源码构建后端、前端及应用镜像；首次执行需下载依赖
python deploy/compact/setup.py build

# 启动应用与网页，等待健康检查
python deploy/compact/setup.py up

# 创建课程搜索索引，并同步课程数据
python deploy/compact/prepare_search.py
python deploy/compact/rebuild_search.py
```

不需要私有历史 JAR、旧数据库备份或真实支付密钥。构建脚本要求生产源码已提交；首次体验直接使用克隆后的代码即可。默认 Compose 项目名为 `tianji-compact`，包含 11 个业务／基础设施容器和 3 个本机监控容器。

### 3. 打开页面并获取账号

| 入口 | 地址 | 登录角色 |
| --- | --- | --- |
| 学生门户 | [http://127.0.0.1:24500/](http://127.0.0.1:24500/) | 游客可浏览；学生登录后购买、学习 |
| 教师与管理工作台 | [http://127.0.0.1:24501/](http://127.0.0.1:24501/) | 教师、管理员 |
| API 网关 | [http://127.0.0.1:24310/api/v2/](http://127.0.0.1:24310/api/v2/) | 接口入口，非网页首页 |
| 监控面板 | [http://127.0.0.1:24592/](http://127.0.0.1:24592/) | Grafana 用户 `tianji`，密码在本机 `deploy/compact/.env` 的 `ACCEPTANCE_GRAFANA_PASSWORD` |
| 本机告警 | [http://127.0.0.1:24591/](http://127.0.0.1:24591/) | Alertmanager，无外部通知 |

初始化后，在本机查看登录信息：

```powershell
Get-Content deploy/compact/.local/accounts.json
```

分别使用 `student`、`teacher`、`admin` 下的 `username` 和 `password` 登录对应端。密码随机生成，账号文件仅保存在本地，不要提交或公开。

管理员新建教师／员工或重置密码时，页面仅当次展示可复制的随机临时凭据，有效期 24 小时。使用者必须先设置自己的密码，再重新登录；临时密码不能访问业务功能。关闭回执后无法重新查看，丢失或过期需再次重置。管理员修改自己的密码使用个人设置，正常自定义密码保持不变。历史旧库导入前仍需核查旧默认口令治理，compact 启动审计的生命周期边界见[本次记录](deploy/compact/reports/real-local-course-20261026.md)。

## 首次使用

1. **游客选课**：打开学生门户，搜索课程并查看详情、章节目录，无需先登录。
2. **学生体验**：使用学生账号登录，将课程加入购物车并完成模拟支付；进入「我的学习」设置周目标、继续学习、记录笔记，再查看考试、错题本和积分签到。
3. **教师体验**：在管理工作台使用教师账号登录，进入「授课学情与答疑」或「考试」查看授权课程、答疑和待评分答卷。没有提交的答卷时，待评分列表为空。
4. **管理员体验**：使用管理员账号登录管理工作台，维护课程、章节、媒资、题库和试卷；在「赛季与奖励」查看积分来源并配置未来赛季奖励。

优惠券抵扣全部金额时，订单直接按「零元成交」完成，不进入付款页；优惠券确认与学习权益仍由持久事件处理。零元退课沿用原审批流程，不发起渠道退款。管理员可在「可靠性与补偿」核对渠道异常并记录处理依据。

教师授课权限与评分权限分别配置。详细页面说明、计分和奖励规则见[学习与教学使用说明](docs/learning-experience.md)。

## 日常启停与常见问题

```powershell
# 查看容器状态
docker compose -p tianji-compact --project-directory deploy/compact -f deploy/compact/.local/compose.runtime.yaml --env-file deploy/compact/.env ps

# 暂停（保留数据）
docker compose -p tianji-compact --project-directory deploy/compact -f deploy/compact/.local/compose.runtime.yaml --env-file deploy/compact/.env stop

# 恢复启动
python deploy/compact/setup.py up
```

| 遇到的问题 | 处理方式 |
| --- | --- |
| 命令找不到或构建失败 | 确认工具已加入 `PATH`、Docker 已启动、依赖与镜像下载可用；脚本诊断日志位于 `deploy/compact/.local/`，例如 `maven.log`、`frontend.log`、`infra.log`、`up-None.log` |
| 页面打不开或启动失败 | 用上面的 `ps` 查看状态；检查本机端口是否被占用，实际端口与挂载见本机 `deploy/compact/.local/compose.runtime.yaml`；用同一 Compose 命令将 `ps` 换成 `logs --tail 100` 查看日志 |
| 门户搜索不到课程 | 确认启动成功，并已执行两个搜索初始化与同步命令 |
| 不知道登录账号 | 查看 `deploy/compact/.local/accounts.json`，学生与教师／管理员使用不同网页入口 |
| 已有环境需要升级 | 先备份，再按[部署手册](deploy/compact/README.md)完成迁移、配置补全和重新构建；不要重复执行首次准备步骤 |

数据库等数据保存在 Docker 命名卷，上传文件在 `deploy/compact/.local/objects`。保留 `.env`、`.local` 和数据卷；**不要用 `docker compose down -v` 清理有数据的部署**。所有默认发布端口仅绑定本机回环地址，其他设备不能直接访问。

### 本机自动备份

Windows 下可注册每日北京时间 **03:30** 的完整备份任务：

```powershell
powershell -NoProfile -File deploy/compact/register_backup_task.ps1
```

备份只保存在本项目 `deploy/compact/.local/backup/`。手动和自动完整备份统一保留最新一份本脚本管理的有效备份；新数据和当前镜像恢复包均校验成功后，旧的受管备份（含升级前回退点）移入 Windows 回收站。当前镜像离线包固定保存在 `.local/current-runtime-backup/`，镜像未变化时复用，不再归档过时镜像。失败材料与无归属标记的历史文件不自动处置。完整备份需要短暂停写，完成后恢复原服务状态。

**Docker Desktop 未运行时跳过，不自动启动它。** Docker 可用但业务容器已停止时，只临时启动备份必需的 MySQL、Redis、RabbitMQ，备份后恢复停止。任务每 15 分钟在后台无窗口检查是否欠缺备份，成功后不重复备份；需要 Python 目录含 `pythonw.exe`、主机及当前用户会话可用，不保存账户密码。03:30 是计划时间，条件不满足时会延后补执行。

跳过状态覆盖同一小文件，不持续追加日志；错误日志总量最多 3 MB。真正备份失败后暂停自动重试，处理原因后运行 `python deploy/compact/maintenance.py retry` 恢复。详细操作、告警及回退约束见[稳定性与运维说明](docs/stability-operations.md)。

## 技术与代码导航

后端使用 Java 21、Spring Boot 4、Spring Cloud、MyBatis-Plus；前端使用 Vue 3、TypeScript、Vite、Element Plus、Pinia。数据与消息使用 MySQL、Redis、RabbitMQ，课程搜索使用 Elasticsearch。

默认部署将原业务模块组合为 `identity`、`commerce`、`education`、`support` 四个应用，经网关为双端提供接口；业务模块仍保留各自的数据库与事务边界。

```mermaid
flowchart LR
  Student[学生门户] --> Gateway[API 网关 / 身份与权限]
  Admin[教师与管理工作台] --> Gateway
  Gateway --> Identity[identity：认证 / 用户]
  Gateway --> Commerce[commerce：交易 / 支付 / 营销]
  Gateway --> Education[education：课程 / 学习 / 考试]
  Gateway --> Support[support：媒资 / 搜索 / 消息]
  Commerce --> DB[(MySQL：模块独立事务)]
  Education --> DB
  DB --> Outbox[事务 Outbox]
  Outbox --> MQ[RabbitMQ]
  MQ --> Inbox[Inbox 去重 / 有界重试 / 人工补偿]
  Inbox --> Education
  Inbox --> Support
```

| 设计选择 | 原因与边界 | 可演示的证据 |
| --- | --- | --- |
| 四个组合应用，保留模块事务边界 | 降低本机进程成本；跨模块调用仍可能失败，不依赖一个全局事务 | 部署配置、模块启动入口与独立连接池 |
| 事务 Outbox、消费者去重、持久补偿 | 交易事实与待发事件同事务保存；消息允许重复，业务避免重复生效 | 失败分页、重放前后状态、支付退款与权益链路 |
| 服务端授权与不可变考试快照 | 学习权益、答卷归属和评分授权由后端判断；发布后题目变化不改历史答卷 | 未授权请求拒绝、教师评分与学生历史记录 |
| 请求上下文隔离与独立进度队列 | 慢响应不能覆盖新课程，页面交互不能阻断进度尾写 | 延迟响应测试、保存失败重试、切节续学 |
| 私有运行配置、不可变镜像绑定、完整备份 | 公共模板可复现，升级保留本机卷与 RabbitMQ 身份 | 源码 SHA、镜像 ID、配置等价检查及隔离恢复 |

面试演示可按三条路径展开：学生购课到教师评分的业务闭环；模拟接口失败后的局部重试和进度补写；管理员查询旧失败、定位模块并重放。项目既有业务与本次改造分别以 Git 记录和[批次验收报告](deploy/compact/reports/README.md)核对；不将仓库全部功能表述为个人原创贡献。

2026-10-21 最终完善包含学习进度单写队列、课程请求隔离、教师分区重试、管理端故障分页与告警、公共模板／私有运行配置分离。隔离环境四角色相关浏览器 13 项通过；已将验收镜像更新至日常部署，14 个容器健康。源码、失败复现、旧备份恢复及数据保留口径见[本次交付报告](deploy/compact/reports/final-school-recruitment-20261021.md)。

| 目录 | 内容 |
| --- | --- |
| `frontend/` | 学生端、管理端及共享客户端 |
| `tj-compact/` | 四个组合应用的启动入口 |
| `tj-user/`、`tj-course/`、`tj-learning/`、`tj-exam/` 等 | 用户、课程、学习、考试等业务模块 |
| `tj-common/`、`tj-api/`、`tj-auth/`、`tj-gateway/` | 公共组件、调用契约、认证与网关 |
| `deploy/compact/` | 当前默认部署、备份恢复与运维工具 |
| `docs/` | 使用说明、运行手册与历史交付记录 |

当前定位是个人演示与本机验证；真实支付／短信、正式容量及多节点高可用不属于已完成验收范围。

## 更多文档

- [目录职责、源码阅读顺序与本地文件边界](docs/project-structure.md)
- [文档导航](docs/README.md) · [部署与运维工具索引](deploy/compact/TOOLS.md)
- [学习、考试、赛季与教学使用说明](docs/learning-experience.md)
- [部署、升级与备份恢复手册](deploy/compact/README.md)
- [可靠性运行手册](docs/reliability-runbook.md)
- [历史验收、修复与交付证据](deploy/compact/reports/README.md)
