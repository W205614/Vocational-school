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

## 历史交付与证据

[交付报告索引](../deploy/compact/reports/README.md)按批次汇总修复、验证、运行源码与回滚记录。报告里的日期、测试数量和运行镜像只适用于对应批次；阅读文档不代表重新验证当前部署。

以下文件保留原路径，便于从历史报告追溯：

| 材料 | 用途 |
| --- | --- |
| [2026-10-07 compact 实现](compact-implementation-20261007.md) | 当时的组合部署实现与边界 |
| [2026-10-07 compact 功能验收](compact-final-functional-validation-20261007.md) | 当时的运行与业务验证 |
| [2026-10-07 管理配置验证](admin-configuration-validation-20261007.md) | 当时的管理配置验收 |
| [2026-10-07 前端体验验证](frontend-user-experience-validation-20261007.md) | 当时的双端体验验证 |
| [旧部署验证](final-deployment-validation.md) | 旧 final 部署结果 |
| [历史交付状态](productionization-status.md) | 既有交付与未验证范围 |
| `evidence/` | 上述报告引用的脱敏 JSON 与截图 |

原始日志、账号、备份及本机归档保存在被 Git 忽略的本地目录；不要直接上传。
