# 参与贡献

项目按需维护，日常改动合入 `dev`。欢迎修复可复现的问题、完善现有业务及文档；较大的功能或依赖调整请先通过 Issue 说明问题、范围和方案。

## 开发准备

准备 JDK 21、Maven、Node.js 22、Python 3.12+ 和 Docker Compose，按[项目 README](README.md)安装依赖。先阅读[目录职责](docs/project-structure.md)和[工具索引](deploy/compact/TOOLS.md)，在业务模块中修改功能，在 `tj-compact` 中维护应用装配。

从最新 `dev` 创建工作分支。已有本机部署按[升级说明](docs/stability-operations.md#升级已有部署)处理，保留私有配置、数据卷和媒资；测试使用独立资源及合成账号。

## 修改与验证

- 保持改动聚焦，遵循现有命名、异常处理和模块边界。新增第三方依赖时说明用途、许可及维护情况。
- 数据库迁移在 `deploy/acceptance/migrations/` 中追加版本，不修改已经应用的迁移。涉及服务调用或网关权限时，同步审阅静态授权清单与负例测试。
- OpenAPI 由真实隔离应用通过 `deploy/acceptance/export_openapi.py` 导出，再运行 `node frontend/scripts/generate-types.mjs` 生成前端类型。修改契约源头后重新生成，避免手工维护生成文件。
- 执行覆盖变更及直接影响范围的测试。后端与数据库验证按[验收工具说明](deploy/acceptance/README.md)和[部署工具索引](deploy/compact/TOOLS.md)选择入口；数据库测试需要专用环境，执行结果注明跳过项和原因。

前端常用检查在仓库根目录执行：

```powershell
npm --prefix frontend run typecheck
npm --prefix frontend run test:unit
npm --prefix frontend run build
```

生产工具的配置测试入口如下；真实 MySQL 和 promtool 用例需要按[接入手册](docs/production-integrations.md#本地工具回归)准备环境：

```powershell
python -m unittest discover -s deploy/production -p 'test_*.py'
```

CI 定义见[业务与前端检查](.github/workflows/reliability.yml)及[生产安全检查](.github/workflows/production-security.yml)。真实支付、短信和云故障演练使用明确指定的候选资源与测试账号，按[生产验收协议](docs/commercial-ha.md)执行。

## 提交说明

提交或 PR 说明应包含问题、最终行为、验证命令与结果，以及尚需外部环境验证的部分。截图使用合成数据，日志去除账号、令牌、验证码和个人资料。

`.env`、`.local`、密钥、导出包及备份仅保存在受保护的本地目录。发现安全问题请按[安全政策](SECURITY.md)联系维护者。
