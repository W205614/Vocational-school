# 文档导航

首次运行从[项目 README](../README.md)开始；需要理解源码时阅读[项目结构](project-structure.md)。

## 当前使用与维护

| 任务 | 文档 |
| --- | --- |
| 学生学习、考试、教师评分及赛季奖励 | [学习与教学使用说明](learning-experience.md) |
| 首次部署、升级、备份与恢复 | [默认部署手册](../deploy/compact/README.md) |
| 查找具体脚本及其用途 | [部署与运维工具索引](../deploy/compact/TOOLS.md) |
| 自动备份、监控、稳定性维护 | [稳定性与运维说明](stability-operations.md) |
| 消息、支付与补偿排障 | [可靠性运行手册](reliability-runbook.md) |
| 账户、MFA 与隐私申请 | [账户安全说明](account-security.md) |
| 独立商业部署与目标环境验收 | [生产部署说明](../deploy/production/README.md) · [商业化运行手册](commercial-ha.md) |

## 当前交付记录

| 记录 | 适用范围 |
| --- | --- |
| [本机部署记录](../deploy/compact/reports/current-deployment-20261026.md) | 当前七镜像来源、增量迁移、数据保留与停止状态 |
| [商业化交付记录](commercial-implementation-20261026.md) | 商业部署代码与工具的本地测试，目标云环境仍待验收 |
| [真实课程专项记录](../deploy/compact/reports/real-local-course-20261026.md) | 真实本地素材、时长探测与长视频续签的专项历史测试来源 |

[交付索引](../deploy/compact/reports/README.md)区分当前部署核对与专项测试来源。过时批次报告和旧结果已移出工作目录；保留的记录不会因源码更新自动变成新版本的通过证明。

原始日志、账号、备份及当前必要的本机证据保存在被 Git 忽略的目录；不要直接上传。
