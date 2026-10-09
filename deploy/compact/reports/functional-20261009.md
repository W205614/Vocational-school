# 2026-10-09 功能与恢复交付

本轮采用用户确认的功能交付范围。运行源码为 `7aec0a216ce603e806ee93f14bca195bd8065b00`，批次为 `e4db216bd9cc460e9cb34ffa4754d7f5`。发布提交只增加文档和脱敏报告，门禁检查它与运行源码的关系；报告中的运行提交不改写为文档提交。

## 实际结果

| 检查 | 结果 |
| --- | --- |
| 后端与真实 HTTP | 134 通过，0 失败／错误／跳过；含 9 项套接字检查 |
| 最终容器浏览器 | 31 通过，0 失败，19 个角色条件执行槽跳过 |
| 前端与工具 | 类型检查、双端生产构建通过；高亮 3 项、部署与证据工具 29 项通过 |
| 安全与审计 | 26 项安全断言，含 60 个并发请求身份隔离；3 个审计范围通过 |
| 非空 Redis | 5 个键、9 项检查，含二进制、类型、绝对到期时间与拒绝覆盖非空目标 |
| 独立恢复 | 183 表内容校验和、45 个媒资哈希一致；消息目录恢复、历史读取、新业务浏览器 31 项通过 |
| 当前数据搜索回退 | 9 → 7 → 9；每阶段完整浏览器 31 项通过，阶段间既有及新增业务记录保留 |
| 既有记录 | 18 张关键表的原有 1,080 个记录标识及 21 个媒资字节哈希保留 |
| 容器与镜像 | 本项目仅 11 个健康最终容器；102 个原临时容器及后续演练容器已退役；162 个多余项目镜像归档校验后删除 |

恢复点为 `20261009T043539.412759Z`。其 Redis 记录为 1，非空 Redis 能力由上表的独立检查证明。主键标识保留不等于可变业务状态完全相等。168 个原数据卷、35 个其他项目容器标识保留；清理未使用全局 prune、force 或删除卷。

镜像数量按不同根摘要去重统计；同一搜索 7 镜像为新批次加载后再次退役，不重复计入不同镜像数量。

## 修复与用户体验

权益按订单明细重新聚合，退款回缩期限并禁止失效后的写入，保留本人学习和考试历史。本人只能删除已关闭订单，支付、退款与删除共用订单锁。模块调用改用标准 HTTP，保持有界准入、内部容量、身份隔离及超时后的幂等处理。搜索升级到 9.5.5，兼容源码 `628fb93f95676835b399273809487f202a1083ef` 仅替换搜索实现，回退从当前 MySQL 重建新卷；切换财务事实指纹一致，未用旧业务备份覆盖新增数据。

手工复查发现并修复管理订单把学员姓名标成课程的问题，增加不可变课程快照字段和学员列。题库刷新期间曾出现选中另一题的失败，受控真实响应在旧镜像复现确认按钮仍可操作；修复以记录 ID 固定行，并在加载时禁止选择、分页和确认，回归校验两题均正确选中。新学生会话可以找到退款后的旧答卷，成绩、答案与教师反馈正确且只读。学生历史、管理订单及教师页面在 390 像素视口复查，表格提供滑动提示；教师导航没有管理权限入口。备份命令恢复之前运行的服务并等待健康，避免结束后立即登录时服务尚未就绪。

镜像清理后再次在新浏览器会话登录教师工作台，核对实际网页脚本与最终容器一致，390 像素视口下待评分页面、筛选与角色导航正常，没有整页水平溢出。

Linux CI 复现响应体测试在读取响应头时提前超时。测试改为先发送并刷新一个字节，再以同步信号阻塞剩余响应体，断言实际读取超时、关闭响应和后续调用恢复。修复前 9 项有 1 错误，修复后独立 Linux 9 项全部通过；详见脱敏结果的 `ciHarnessFix`。本批次重新构建并执行功能、恢复和搜索回退，未放宽证据门禁。

## 复现与证据

在仓库根目录按 [部署手册](../README.md) 从源码初始化独立合成环境。以下检查会写入明确标识的验收数据：

```powershell
python -m unittest discover -s deploy/compact -p "test_*.py"
python deploy/compact/check_policy.py
python deploy/compact/run_backend_tests.py
npm --prefix frontend run typecheck
npm --prefix frontend run test:unit
python deploy/compact/security_smoke.py
Start-Sleep -Seconds 32
python deploy/compact/run_browser.py
python deploy/compact/audit_smoke.py
python deploy/compact/redis_roundtrip.py
python deploy/compact/backup.py
python deploy/final/final_backup.py --verify <本轮完整备份目录>
python deploy/compact/rehearse_recovery.py <本轮完整备份目录> --pause-source
```

恢复必须使用新目录、项目和独立卷，先核对表内容、媒资与历史，再执行新增业务。搜索回退在本轮数据副本中分三阶段执行 `run_browser.py`；切换入口为 `search_transition.py --major 7/9 --images <对应版本images.json> --source-manifest <对应版本build-source.json>`。7 兼容源码、冻结清单、完整 Git bundle 与已校验镜像归档留存在本机恢复材料中；加载归档后按其清单恢复必要镜像标签。每次切换核对课程名称、销售数、投影积压、财务事实及前阶段主键记录，失败时保留写入暂停状态和报告。

公开 [搜索 7 兼容补丁](search7-compat-628fb93.patch) 与已验证兼容源码的差异一致，仅修改根依赖版本和 `tj-search`。从运行提交创建独立 worktree，再应用补丁并创建本地提交，即可得到相同业务代码与兼容搜索实现；本地新提交哈希会不同，不能冒用本报告的兼容源码哈希。构建与初始化必须设置独立的 `TJ_COMPACT_HOME`、`TJ_COMPACT_PROJECT=tianji-opt-...` 和不冲突的端口偏移，严禁在最终运行目录上应用兼容补丁或初始化。公开补丁不包含私有业务快照和配置。

```powershell
$taskRepoRoot = (Get-Location).Path
$taskCompatibilityRoot = Join-Path (Split-Path $taskRepoRoot -Parent) 'Vocational-school-search7-recovery'
$taskPatch = Join-Path $taskRepoRoot 'deploy/compact/reports/search7-compat-628fb93.patch'
git worktree add -b codex/search7-recovery "$taskCompatibilityRoot" 7aec0a216ce603e806ee93f14bca195bd8065b00
git -C "$taskCompatibilityRoot" apply --unidiff-zero "$taskPatch"
git -C "$taskCompatibilityRoot" add pom.xml tj-search
git -C "$taskCompatibilityRoot" commit -m "build: prepare isolated search7 recovery source"
```

`evidence.py start --snapshot <基础SQL快照>` 建立批次，`run_bound_check.py <检查名> python <对应检查入口>` 保存原始结果哈希、时间与提交／镜像／配置／快照绑定。功能门禁须具备同批次的后端、浏览器、安全、审计、Redis、独立恢复、搜索回退与既有记录保留八类证明；新检出仓库没有私有证据，不能直接引用此公开摘要让门禁通过。

```powershell
python deploy/compact/release_gate.py --scope functional
```

各证明时间、指纹、原始报告哈希、镜像归档 SHA-256 和平台范围见 [脱敏结果](functional-20261009.json)。完整 SQL、配置、账号、密钥、日志、媒资、截图和恢复材料保留在本机 `.local`，不上传 GitHub。

## 范围

`acceptanceScope=functional`，`performanceValidation=DEFERRED`。180 分钟正式采样、200 并发容量、延迟门槛和修复后 Java RSS 降低 20% 没有完成验证。默认完整性能门禁继续保留，不能用容器数量、历史短测或修复前内存结果替代。当前验收为本机合成数据、模拟支付／短信与单机恢复；原离线历史数据未迁入，真实支付、正式历史切换和多节点高可用是独立范围。
