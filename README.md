# 职学堂 · 职业教育在线学习平台

职学堂是基于 Java 21 / Spring Boot 4 和 Vue 3 的在线学习平台，提供学生门户、教师与管理工作台，串联 **选课 → 购买 → 学习 → 考试 → 错题复习 → 积分激励**。

当前品牌为 **职学堂 / Zhixuetang**，业务模块使用 `zxt-` 前缀和 `com.zhixuetang` 包名。历史交付记录保留当时的名称、素材来源、截图及验证范围。

适合本地体验在线教育业务、学习 Java 多模块架构与前后端协作。默认部署使用合成演示数据，支付与短信为本地模拟。

生产接入提供支付宝、阿里云短信、腾讯 COS 适配，以及安全试看、跨域隐私任务、首次配置和验收工具。真实供应商、云环境、容量与容灾仍待验收；软件交付状态、本地验证范围及接入顺序见[生产就绪说明](docs/production-readiness.md)。

项目按需维护，日常改动合入 `dev`。参与开发见[贡献指南](CONTRIBUTING.md)，安全问题报告见[安全政策](SECURITY.md)。

最新已发布的源码预览为 [v0.1.0-preview.2](https://github.com/W205614/Vocational-school/releases/tag/v0.1.0-preview.2)，改动与历史验证范围见[版本说明](docs/releases/v0.1.0-preview.2.md)。`dev` 另含后续工程修正；它们尚未组成新 Release。`pom.xml` 的 `1.0.0` 是独立的 Maven 构建版本。

[获取方式](#获取方式与发布状态) · [首次部署](#全新环境准备) · [已有环境启动](#已有环境启动) · [源码导航](#源码导航)

## 获取方式与发布状态

| 方式 | 当前状态与用途 |
| --- | --- |
| 源码构建 + Docker Compose | 当前可用的部署入口，按下文从 Git 克隆 `dev`，生成本地配置并构建镜像；需要完整工具链和依赖下载网络 |
| 预构建 Docker/OCI 镜像 | 已确定使用公开 GHCR，**尚未发布、未验证匿名拉取**；目前不能省略源码构建步骤 |
| 历史源码预览 | `preview.1`、`preview.2` 保留原提交和历史说明，均没有附带应用镜像；不代表当前安全告警已清零 |

计划的 GHCR 位置为 `ghcr.io/w205614/zhixuetang-{identity,commerce,education,support,gateway,student,admin}`，平台为 `linux/amd64`。计划版本 `v0.1.0-preview.3` 尚无 Tag、Release 或镜像摘要；许可材料和安全门禁未完成前，不将这些地址作为可用下载入口。最新调查见[安全与发布复核](docs/security-review-20261110.md)。应用交付采用 OCI 镜像，不提供 Maven 应用包安装或发布方式。

源码 ZIP 不含 Git 元数据，而构建工具需要核对提交和源码绑定，请使用下文的 Git 克隆方式。新源码不会自动替换已有日常容器；已有环境先按[升级说明](docs/stability-operations.md#升级已有部署)核对完整构建绑定和恢复前提，不用初始化空库代替升级。

## 快速部署

### 已有环境启动

已完成初始化和构建的环境，在仓库根目录运行：

```powershell
python deploy/compact/setup.py up
```

升级或启动失败时查阅[默认部署手册](deploy/compact/README.md)。保留本机配置、媒资和数据卷。首次部署按以下步骤执行。

### 全新环境准备

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

### 初始化、构建并启动

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

不需要私有历史 JAR、旧数据库备份或真实支付密钥。构建脚本要求生产源码已提交；首次体验直接使用克隆后的代码即可。默认 Compose 项目名为 `zhixuetang-compact`，包含 11 个业务／基础设施容器和 3 个本机监控容器。

`prepare` 生成的 `.env`、`accounts.json`、签名材料和运行配置仅留本机；`objects/` 与 Docker 命名卷存放媒资和业务数据。关闭页面或停止容器不会生成备份。备份、恢复与保留策略见[稳定性与运维](docs/stability-operations.md)，不要将运行卷视为恢复集。

源码调试使用同样的 JDK／Node 工具链；组合应用依赖完整配置、数据库、Redis、RabbitMQ 和搜索服务，不提供一条绕过初始化的 `java -jar` 命令。测试和热更新使用独立环境，入口见[贡献指南](CONTRIBUTING.md)及[隔离验收说明](deploy/acceptance/README.md)，避免写入已有日常库。

## 首次登录与体验

| 入口             | 地址                                                             | 登录角色                                                                                 |
| ---------------- | ---------------------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| 学生门户         | [http://127.0.0.1:24500/](http://127.0.0.1:24500/)               | 游客可浏览；学生登录后购买、学习                                                         |
| 教师与管理工作台 | [http://127.0.0.1:24501/](http://127.0.0.1:24501/)               | 教师、管理员                                                                             |
| API 网关         | [http://127.0.0.1:24310/api/v2/](http://127.0.0.1:24310/api/v2/) | 接口入口，非网页首页                                                                     |
| 监控面板         | [http://127.0.0.1:24592/](http://127.0.0.1:24592/)               | Grafana 用户 `zhixuetang`，密码在本机 `deploy/compact/.env` 的 `ACCEPTANCE_GRAFANA_PASSWORD` |
| 本机告警         | [http://127.0.0.1:24591/](http://127.0.0.1:24591/)               | Alertmanager，无外部通知                                                                 |

初始化后，在本机查看登录信息：

```powershell
Get-Content deploy/compact/.local/accounts.json
```

分别使用 `student`、`teacher`、`admin` 下的 `username` 和 `password` 登录对应端。密码随机生成，账号文件仅保存在本地，不要提交或公开。

1. **游客选课**：打开学生门户，搜索课程并查看详情、章节目录，无需先登录。
2. **学生体验**：使用学生账号登录，将课程加入购物车并完成模拟支付；进入「我的学习」设置周目标、继续学习、记录笔记，再查看考试、错题本和积分签到。
3. **教师体验**：在管理工作台使用教师账号登录，进入「授课学情与答疑」或「考试」查看授权课程、答疑和待评分答卷。没有提交的答卷时，待评分列表为空。
4. **管理员体验**：使用管理员账号登录管理工作台，维护课程、章节、媒资、题库和试卷；在「赛季与奖励」查看积分来源并配置未来赛季奖励。

启动成功后，可以在学生门户搜索到演示课程并打开详情，在管理工作台用对应角色登录。页面打不开或搜索为空时，见[日常启停与排障](deploy/compact/README.md#日常启停与常见问题)。

## 源码导航

默认部署由网关和四个组合应用对外提供服务。在业务模块中修改功能，在组合应用中查看这些模块如何一起启动。

| 想做什么                     | 从哪里开始                                                                                                            |
| ---------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| 改学生页面、教师或管理员页面 | [学生端](frontend/apps/student/)、[管理端](frontend/apps/admin/)；共用请求和组件在[共享包](frontend/packages/shared/) |
| 改学习进度、课程或订单规则   | [学习](zxt-learning/)、[课程](zxt-course/)、[交易](zxt-trade/)                                                           |
| 查请求入口或跨模块调用契约   | [网关](zxt-gateway/)、[调用契约](zxt-api/)                                                                              |
| 查业务模块如何组合运行       | [组合应用](zxt-compact/)；学习模块由 [education](zxt-compact/education/) 装配                                           |
| 部署或排障                   | [本机部署手册](deploy/compact/README.md)、[运维工具索引](deploy/compact/TOOLS.md)                                     |

首次读代码可跟着[视频进度保存路径](docs/project-structure.md#视频进度如何保存)，从学生页面走到数据库和测试。

默认调用关系为双端网页 → API 网关 → 四组组合应用；MySQL 存储业务状态，Redis 提供缓存和任务协调，RabbitMQ 承载可靠消息，Elasticsearch 提供课程搜索。模块装配、Outbox／Inbox 与事务边界见[架构图和源码阅读路径](docs/project-structure.md#默认部署的调用关系)。

## 验证与已知边界

前端类型、单元测试与构建入口，以及后端和真实依赖测试要求见[贡献指南](CONTRIBUTING.md#修改与验证)。本轮本机实际执行了相关 Java 构建与日志回归、compact／生产工具测试和部分扫描；隔离业务 CI、候选镜像扫描及日常环境验收必须分别查看，不能由一个绿灯代替。

2026-11-10 已启用 CodeQL 与 Dependabot，并将漏洞门禁扩展至全部严重级别；安全告警和 JVM 镜像扫描仍有未完成项。默认支付、短信是模拟，真实外部服务、生产容量及备份恢复没有因此得到认证。完整问题与版本边界见[复核记录](docs/security-review-20261110.md)。

## 来源与许可

后端历史包含虎哥、`tjxt` 等原有提交与教学代码署名；当前“职学堂”品牌及后续工程改动不改变这些代码的作者归属。原始资料的完整来源与再分发授权尚未核清，本仓库也没有统一 LICENSE。能够浏览源码不等于已获得任意使用、再许可或镜像再分发的授权；不以 MIT 或唯一作者方式描述项目。课程媒资和私有数据不随源码提供。

## 深入文档

- [文档导航](docs/README.md) · [目录职责与阅读顺序](docs/project-structure.md)
- [贡献指南](CONTRIBUTING.md) · [安全政策与报告渠道](SECURITY.md)
- [生产就绪状态与接入步骤](docs/production-readiness.md) · [供应商配置与验证命令](docs/production-integrations.md)
- [学习与教学使用说明](docs/learning-experience.md) · [账户安全](docs/account-security.md)
- [消息、支付与补偿排障](docs/reliability-runbook.md) · [稳定性与运维](docs/stability-operations.md)
- [商业部署包及目标环境验收要求](deploy/production/README.md) · [历次部署与专项验证范围](deploy/compact/reports/README.md)

## 界面预览

完成本地部署后，可在[学生门户](http://127.0.0.1:24500/)查看选课与学习页面，在[管理工作台](http://127.0.0.1:24501/)查看教学与管理页面。

以下为 2026-11-07 从独立合成环境实际拍摄的匿名页面，候选源码为 `7db0b79`。门户中的功能片段标注为“界面示例”；[截图说明](docs/assets/20261107/README.md)记录实际版本、拍摄时间、检查范围和校验值。

**学生门户 · 桌面**

![职学堂学生门户桌面截图](docs/assets/20261107/portal-1440.png)

**管理端 · 登录入口**

![职学堂管理端桌面登录截图](docs/assets/20261107/admin-login-1440.png)

<details>
<summary>查看移动端：学生门户与管理登录</summary>

<p>
  <img src="docs/assets/20261107/portal-390.png" alt="职学堂学生门户移动端截图" width="300">
  <img src="docs/assets/20261107/admin-login-390.png" alt="职学堂管理端移动登录截图" width="300">
</p>

</details>

[2026-10-16 界面截图](deploy/compact/reports/assets/20261016/)作为历史记录保留，包含当时的品牌与合成演示数据；不代表本轮页面或验证结果。
