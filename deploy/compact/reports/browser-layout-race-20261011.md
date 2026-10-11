# 2026-10-11 浏览器布局断言时序修复

本次修复管理课程配置浏览器用例的偶发手机布局失败，修改测试同步与受控回归，不修改应用样式、业务接口或依赖。原有本地 Compose 与迁移材料保留。

## 失败与根因

基线提交 `552db38` 的 [CI 首次运行](https://github.com/W205614/Vocational-school/actions/runs/38128366384/attempts/1) 为浏览器 31 通过、1 失败、20 个角色不适用执行槽跳过。失败发生在管理课程配置用例：选择视频小节后立即切换到 390px 视口，断言整页没有横向溢出。重跑成功没有解释或修复这个时序问题。

选择步骤原先只等待选项点击完成。Element Plus 的下拉菜单挂在 `body`，关闭时还会执行离场动画；菜单尚未隐藏时仍可能保留桌面位置。此时切换视口会把这个过渡元素的右边界计入页面宽度，测试读取的不是菜单关闭后的页面。

在本机现有部署使用 Chromium 按 CI 顺序选择考试、视频小节，再从 1280px 切到 390px。24 次逐帧采样中有 1 次复现：

| 状态 | 视口宽度 | 页面 scrollWidth | 溢出元素 |
| --- | --- | --- | --- |
| 切换后的前两帧 | 390 | 579 | `.el-select__popper`，仍有 `el-zoom-in-top-leave-active` / `leave-to` 类 |
| 第三帧起 | 390 | 390 | 菜单位置更新／关闭，不再造成整页溢出 |

失败帧的菜单左边界为 273px、宽度为 306px、右边界为 579px，`display:block`。未发现该场景下持续存在的表单布局溢出。此前“可能是布局尚未稳定”的推断由这些 DOM、样式和逐帧几何证据进一步定位为下拉菜单关闭与视口切换的竞争。

## 修改

`frontend/e2e/admin-config.spec.ts` 中的普通 `option` 方法在点击后，等待对应菜单的实际 CSS `display:none`，再返回给后续步骤。定位选项时显式包含隐藏元素，避免 `aria-hidden` 提前改变后定位器失去目标，误把“找不到菜单”当成关闭动画已结束。

手机页和教师选择弹窗仍立即检查 `document.documentElement.scrollWidth > innerWidth + 1`，原 1px 容差不变。没有对布局断言增加重试或轮询，没有固定 sleep、关闭动画、设置整页 `overflow:hidden` 或提高测试重跑次数；Playwright 配置仍为 `retries:0`。等待的是操作完成状态，菜单不能关闭时用例会失败。

原业务用例补充受控情形：从真实视频选择框取得桌面位置和宽度，测试中将关闭动画延长到 1 秒，并让离场菜单保持该桌面几何，模拟位置更新晚于视口切换。动画仍实际执行，选择方法必须等它关闭；随后继续原手机页、教师弹窗和业务持久化断言。临时 CSS 只存在于测试页面，检查后移除。

## 本机验证

| 检查 | 结果 |
| --- | --- |
| 原始逐帧复现 | 24 次切换中 1 次读到 579px；恢复后为 390px |
| 受控关闭，临时移除等待 | 原手机溢出断言失败，证明用例能稳定捕获过早切换 |
| 恢复等待，受控用例重复执行 | 3 次全部通过，0 重试 |
| 持续溢出负向检查 | 临时给章节表单设置 720px 最小宽度，原断言仍失败；负向注入不提交 |
| 完整管理端浏览器 | 15 通过、0 失败，11 个学生角色执行槽跳过 |
| 前端类型检查、差异格式 | 通过 |

修复前自然复现、两项负向检查、修复后重复检查及完整管理端日志保存在本机忽略目录。自然复现 JSON 的 SHA-256 为 `748964d5b9a1ef754d1c4991e39b896582afe28839dcdd2dd3e8b2bc50cb1fa2`；受控失败日志为 `6d960dbec267f20b2c9a6e5bf1400c34dce308d2ff43a07316f3c7e920b2440a`；修复后重复检查日志为 `16b55c952bc99ab0a3832c3a9ae572653b7191bf301ad2c5dd4685128f5fecc3`；持续溢出负向日志为 `40316c6268be3a578f55eb4676449d16110ba8c95660d113e0e0721ab08df354`。账号、浏览器 trace 和私有配置不上传。

本机应用仍运行既有镜像，未重建容器。本次没有重新执行本机完整后端、学生端购买或恢复／性能门禁；远程 CI 按最终推送提交执行 `backend`、`frontend`、`compact-browser`，其结果以 [Actions](https://github.com/W205614/Vocational-school/actions/workflows/reliability.yml) 为准。

## 复现入口

先按 [部署手册](../README.md#回归) 准备合成账号及浏览器夹具，在仓库根目录执行：

```powershell
$env:ACCEPTANCE_CONTAINER_UI='1'
$env:TJ_UI_RUNTIME_HOME=(Resolve-Path deploy/compact/.local).Path
$env:TJ_ADMIN_URL='http://127.0.0.1:24501'
$env:TJ_STUDENT_URL='http://127.0.0.1:24500'
$env:PLAYWRIGHT_CHANNEL='chromium'
npx --prefix frontend playwright test --config frontend/playwright.config.ts --project admin --grep 'managed draft' --repeat-each 3
npx --prefix frontend playwright test --config frontend/playwright.config.ts --project admin
npm --prefix frontend run typecheck
```

首次使用需安装 Chromium。重复执行用于验证时序修复，不是失败后重跑判定通过；用例和 CI 都不自动重试。浏览器检查会新增明确标识的合成课程，保留已有数据。
