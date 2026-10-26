# 项目结构与源码阅读路径

当前默认入口是根目录 [README](../README.md) 中的 `deploy/compact/setup.py`。compact 将业务模块装配成四个应用；各业务模块仍是实际源码和事务边界，不能作为重复目录删除。

## 目录职责

```text
Vocational-school/
├── frontend/              双端网页、共享客户端、接口契约与浏览器测试
├── tj-compact/            四个组合应用与公共装配运行时
├── tj-gateway/            API 网关
├── tj-common/             公共组件
├── tj-api/                跨模块调用契约
├── tj-auth/               认证服务与 SDK
├── tj-*/                  用户、课程、交易、学习等业务实现
├── deploy/
│   ├── compact/           默认部署、升级、备份恢复及报告
│   ├── acceptance/        验收基础设施、迁移、测试与容器模板
│   ├── production/        独立商业部署渲染、安全扫描和证据验收工具
│   ├── operations/        当前与兼容部署共用的快照捕获和校验库
│   └── final/             历史部署及仍被复用的备份恢复工具
├── docs/                  当前使用说明与历史交付证据
├── .github/workflows/     GitHub CI
└── pom.xml                Maven 聚合与依赖管理
```

| 源码位置 | 职责 |
| --- | --- |
| `frontend/apps/student/` | 学生门户 |
| `frontend/apps/admin/` | 教师与管理工作台 |
| `frontend/packages/shared/` | 双端共享客户端与组件 |
| `frontend/openapi/` | 接口契约 |
| `frontend/e2e/` | 浏览器验收 |
| `tj-compact/runtime/` | 组合应用装配与运行隔离 |
| `tj-compact/identity/` | 组合认证、用户模块 |
| `tj-compact/commerce/` | 组合交易、支付、促销模块 |
| `tj-compact/education/` | 组合课程、学习、考试、互动评价模块 |
| `tj-compact/support/` | 组合媒资、搜索、消息、数据模块 |
| `tj-user/`、`tj-course/`、`tj-learning/`、`tj-exam/` | 用户、课程、学习、考试业务实现 |
| `tj-trade/`、`tj-pay/`、`tj-promotion/` | 订单、支付与退款、优惠券和促销 |
| `tj-media/`、`tj-search/`、`tj-message/`、`tj-data/`、`tj-remark/` | 媒资、课程搜索、消息、数据与互动评价 |

### 部署目录之间的依赖

- `deploy/compact/` 是默认部署。命令用途见[工具索引](../deploy/compact/TOOLS.md)。
- `deploy/production/` 是独立生产候选部署包，要求外部集群和托管服务；其模板、静态检查或 CI 通过均不构成高可用、供应商或容量验收。四组应用保持独立模块上下文，启动任务执行完成后才就绪；生产 RPC 另受静态调用清单限制。
- `deploy/acceptance/` 仍被当前 CI 使用；compact 也复用其模块清单、迁移、测试入口及容器模板。
- `deploy/operations/` 提供共享快照与 Redis 工具，compact 显式传入当前运行配置；`deploy/final/` 中对应文件保留为兼容入口，不再作为 compact 的配置来源。
- `deploy/compact/reports/` 与 `docs/evidence/` 保存按批次记录的历史证据，不属于临时缓存。

部分本机保留 `deploy/docker/`，这是旧 VM 向 Docker Desktop 迁移的私有材料，依赖原备份和配置，不是当前默认部署，也不应直接加入 Git。旧根目录 Jenkins `startup.sh` 及配套 `Dockerfile` 已移除；当前构建通过 `setup.py build` 完成。

## 推荐阅读顺序

1. [README](../README.md)：先启动并体验学生、教师、管理员入口。
2. [使用说明](learning-experience.md)：理解选课、购买、学习、考试及退款后的权限变化。
3. `frontend/apps/student/src/` 或 `frontend/apps/admin/src/`：从目标页面找到路由、请求及共享客户端。
4. `tj-gateway/`：确认外部接口与认证入口。
5. 目标业务模块的 `controller` → `service` → `mapper`，结合 `tj-api/` 理解跨模块调用。
6. `tj-compact/` 的应用入口与配置：理解独立业务模块如何组合运行。
7. [部署手册](../deploy/compact/README.md)、[可靠性运行手册](reliability-runbook.md)：最后阅读运维和故障恢复。

## 本地文件的保留与清理

`.local` 是本机工作目录，并非统一的缓存目录。尤其 `deploy/compact/.local/` 中含运行数据，不能整体删除。

`.local/compose.runtime.yaml` 是当前部署的生效配置，保留本机镜像身份、搜索卷和 RabbitMQ hostname；公共 `compose.yaml` 仅为可复现模板。备份、恢复、升级和配置指纹均读取统一运行配置入口，旧恢复包中的 `compose.yaml` 文件名继续兼容。

| 类别 | 处理原则 |
| --- | --- |
| `.env`、账号 JSON、签名密钥、运行配置 | 保留并保持 Git 忽略 |
| `objects/`、`configs/`、`observability/`、`ops-status/` | 可能被容器挂载；先核对挂载，保留当前运行依赖 |
| 备份、镜像归档、恢复演练与回滚材料 | 保留；不能仅凭日期旧、无运行容器引用就删除 |
| 根 `.local/` 中旧源码副本、构建副本、截图、临时诊断 | 核对进程、挂载和引用；需保留证据时先归档并校验，再清理副本 |
| 过时日志、`__pycache__/`、旧浏览器输出 | 确认无进程使用后按明确名单清理；不执行全目录通配删除 |
| `node_modules/`、`target/`、网页 `dist/` | 可生成，但可能被本机构建、测试或回退流程使用，不在日常结构整理时一并清空 |
| `.idea/`、`.taste-skill/` | 本机 IDE 与设计工具资料；不混入提交 |

清理前记录名单与哈希，检查目录链接、进程和 Docker 挂载；归档后验证内容完整，再操作名单中的路径。不得用全局 Docker prune 或 `docker compose down -v` 代替文件整理。

所有命令须从项目根目录执行。对历史材料的整理不改变当前部署版本，也不构成对未修改业务的重新验收。
