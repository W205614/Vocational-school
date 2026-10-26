# 部署与运维工具索引

以下路径均相对于仓库根目录。首次部署按[项目 README](../../README.md)顺序执行；已有部署按[部署手册](README.md)操作。工具可能修改数据库或创建演练环境，先阅读对应文档与参数，避免直接批量运行。

## 默认部署与日常维护

| 工具 | 用途 |
| --- | --- |
| `setup.py prepare/init/build/up` | 生成本机配置、初始化全新环境、源码构建、启动 |
| `prepare_search.py`、`rebuild_search.py` | 搜索索引准备与数据重建 |
| `upgrade_existing.py` | 已有部署的受控增量升级 |
| `backup.py`、`restore.py` | 完整备份与隔离恢复；只保留最新受管有效包 |
| `backup_images.py`、`backup_retention.py` | 当前镜像离线包复用／更新、旧受管备份回收站轮换 |
| `maintenance.py` | 自动维护与备份状态管理；详见[稳定性与运维说明](../../docs/stability-operations.md) |
| `register_backup_task.ps1`、`scheduled_backup.ps1` | Windows 定时任务注册与调用 |
| `recovery_smoke.py`、`recovery_upgrade.py`、`rehearse_recovery.py` | 恢复校验、恢复后升级及演练 |
| `search_transition.py` | 受控搜索版本迁移与回退 |
| `retire_old.py`、`retire_images.py` | 带保护与归档校验的旧环境／镜像退役 |
| `retire_test_volumes.py` | 按明确清单及恢复证据退役可重新创建的测试卷；先生成计划，再显式应用 |

## 变更验证与交付

| 工具 | 用途 |
| --- | --- |
| `run_backend_tests.py`、`run_core_tests.py` | 后端测试入口 |
| `run_transport_tests.py` | 请求上下文与传输相关验证 |
| `run_browser.py` | 浏览器验收；使用专用夹具并写入测试数据 |
| `security_smoke.py`、`audit_smoke.py` | 安全边界与审计验证 |
| `redis_roundtrip.py` | Redis 恢复验证 |
| `check_policy.py`、`check_packaging.py` | 策略与打包检查 |
| `build_provenance.py`、`release_gate.py` | 构建来源与交付证据约束 |
| `demo_release_gate.py` | 单机演示交付门禁，核对本轮角色、迁移、历史与镜像证据，并明确容量未验收 |
| `diagnose_ci.py` | CI 脱敏诊断 |
| `test_*.py` | 工具自身的定向回归；按修改范围选择 |

前端命令见 [frontend/package.json](../../frontend/package.json)。GitHub 的 `Reliability and frontend gates` 工作流分别运行后端、前端和 compact 浏览器检查，定义见[工作流文件](../../.github/workflows/reliability.yml)。本地仅验证改动直接影响的部分，最终以所推送提交的 CI 为准。

## 性能实验与当前证据

`benchmark_*.py`、`load_*.py`、`mixed_*.py`、`performance_acceptance.py`、`summarize_performance.py` 等用于专门的性能或隔离实验，协议与边界见 [OPTIMIZATION.md](OPTIMIZATION.md)，不属于首次使用所需命令。

[reports/](reports/README.md) 只列当前保留的部署与专项交付记录。旧提交专用探针、兼容补丁和过时结果已移出工作目录；当前测试、性能和恢复工具继续保留。`deploy/acceptance/`、`deploy/final/` 内仍有当前工具导入的实现和兼容入口，应一起保留。
