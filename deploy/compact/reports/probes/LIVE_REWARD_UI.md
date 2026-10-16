# 奖励购买后的只读浏览器补充验收

本目录的 `run_live_reward_ui.py` 和相邻 `live_reward_ui.mjs` 用于核对已完成的实际奖励购买。它们不创建课程、奖励、规则、订单或支付，不改变主机时间或运行容器。认证登录是唯一允许的 POST。脚本固定生产源码 `8dcf8e7d44c6048318ad1e306e1425c8fd40fb70`；源码中有断言不代表运行已经通过，实际结果以该次私有报告为准。

前提是独立 fresh 恢复已经完成原始数据校验、增量迁移和完整 42 通过／0 失败／30 角色跳过的浏览器回归，并通过真实互动、结算、发奖和奖励券购买探针。当前源码不支持把主部署作为目标。

## 运行

在仓库根目录安装 `deploy/compact/requirements.txt`、执行 `npm --prefix frontend ci`，并准备可用的 Playwright 浏览器。本机验收使用 Edge，浏览器通道由 `PLAYWRIGHT_CHANNEL` 指定，默认 `msedge`。脚本通过源码位置向上发现仓库标志，Python 调用相邻 JS，JS 从仓库的 `frontend/node_modules/playwright` 读取依赖；不依赖 `.local/task` 这个源码存放位置。

```powershell
python deploy/compact/reports/probes/run_live_reward_ui.py --home <独立final-upgrade目录> --project <对应tianji-recovery项目名> --offset 1000 --out deploy/compact/.local/learning-upgrade-20261015/live-reward-ui-<唯一批次>
```

默认读取私有任务目录 `deploy/compact/.local/learning-upgrade-20261015/live-settlement-result.json`。如果原执行因观察语句失败而保留 FAILED，随后对同一券、同一订单完成只读续验，应明确传入独立成功证明：

```powershell
python deploy/compact/reports/probes/run_live_reward_ui.py --home <原独立恢复目录> --project <原独立恢复项目名> --offset 1000 --out deploy/compact/.local/learning-upgrade-20261015/live-reward-ui-readonly-<唯一批次> --settlement-proof deploy/compact/.local/learning-upgrade-20261015/live-settlement-final-result.json
```

成功续验不修改原 FAILED 文件，也不代表再次发券或付款。自选证明必须 resolve 后仍位于上述私有任务目录，文件及祖先路径均不能是链接或重解析点。脚本核对选入证明的 SHA-256，浏览器结束时再次校验，防止输入在验收期间被替换。`--out` 必须是 ignored 范围内尚不存在的目录；失败后换新输出目录，保留原现场。

## 守卫

- 证明必须 PASSED，sourceCommit 和 executedSource 都等于固定完整提交，actualRewardPurchased／actualRewardUsed 为 true；单券、一次扣库存、真实 broker 回执和总额减优惠等于实付的前置断言均保留。
- home 只能是仓库 ignored 目录下的 `final-upgrade-*`；project 必须为 `tianji-recovery-` 加该 home 名称，offset 为 1000。拒绝主部署、符号链接和重解析路径。
- 同 home／project 的互动证明、原始恢复和最终源码升级、完整浏览器报告已通过。选入证明新增的只读续验、原失败来源、重启持久化及观察信息是附加元数据，不取消任何成功、身份或金额守卫。
- 仅使用 Docker 的 `compose ps` 和 `inspect`，核对现有服务健康、7 个实际应用／网页镜像与源码 manifest／Compose 一致、独立网络和卷／bind 归属，以及回环 offset1000 端口。不会启动或停止容器。
- 从该恢复 home 读取私有 accounts／fixture。浏览器除确切学生登录外的 API 写入全部阻断；补充 HTTP 客户端只发 GET。原报告、账号、SQL、配置和截图均不在本目录发布。

## 分开记录页面与接口事实

真实页面核对：从 UI 自己的实际分页响应关联同一 reward request，检查对应赛季、名次和“已发放”；在本人订单分页按精确订单号检查“已支付”和折后实付，打开实际详情检查课程与实付；在“我的优惠券”核对对应券名称及持有记录。脚本不 mock 数据、不修改 DOM、不强制点击。

真实 HTTP 核对：FULFILLED 奖励的用户、券及票据对应前置实际购买；精确订单的 totalAmount、discountAmount、realAmount 和课程明细一致。当前用户券接口返回 CouponVO 券定义，不返回票据 ID 或显式票据状态字段；脚本通过 `status=2` 查询含对应 couponId、`status=1` 查询不含，再结合前置成功探针的唯一票据 SQL 证明，单独记录已用核对。

当前订单页面没有独立优惠金额字段，持有券页面也没有已用／未用状态列；“已领取”表示持有记录。报告明确披露这些展示边界，不把 HTTP 成功说成页面展示了不存在的控件。金额错误、奖励不可见、券查询状态矛盾或业务写入尝试都会失败；不得修改断言掩盖失败。

## 输出与发布边界

`aggregate.json` 保存无账号／交易 ID 的汇总；`report.json` 分开保存 uiChecks、httpChecks 和展示限制。私有目录还保存运行时来源、诊断日志，以及奖励、订单详情和持有券页面截图，可能包含合成账号及交易 ID，**不得复制到公开文档**。stdout 只输出通过／失败摘要，不输出账号、密码、token 或敏感 ID。

独立恢复 wrapper 应在真实结算购买或同单只读续验通过后、停止该 clone 前调用；返回非零时保留现场，不能签发整轮连续验收通过。这个补充 UI 检查不替代原始恢复、真实结算／发券／支付、消费去重、搜索回退或最终交付门禁。
