# 受控本地课程视频导入

`import_local_course.py` 将已获授权使用的本机 MP4 视频导入**独立免费课程**，正常调用课程草稿、目录、媒资登记、教师、上架和搜索 API。它不执行业务 SQL，不删除／下架旧课程，不修改或移动源视频，不向外部服务上传内容。

默认源目录为调用者显式指定的 `--source-root`，应含 `day01` 至 `day12` 的 161 个 MP4。名称按自然数字排序；课程名默认“Java微服务实战”，每个 day 为一章。小节标题受现有 30 个 Java UTF-16 字符限制，保留顺序编号并截短显示；完整文件名保存在私有清单中。课程默认免费、0 元、学习有效期永久，演示报名截止为 2099-12-31，可显式指定。

## 前置条件

1. 先取得现有完整备份，并在隔离副本完成迁移、功能与恢复检查。
2. 媒资服务安装 `ffprobe`，本地存储及 `local-simulator` profile 已启用。公开视频上传仍为 200 MiB；离线导入专用上限为 512 MiB，不允许通过公网路径绕过普通上传限制。
3. 只给 `app-support` 增加源根目录的临时**只读**挂载，例如 `/run/offline-import:ro`，并在 media 配置中显式启用 `tj.local-import.enabled=true`、设置 `tj.local-import.directory=/run/offline-import`。默认不启用；只在本次导入窗口启用。
4. 内部 `POST /internal/v2/media/local-import` 必须保持网关不可达。工具通过现有 loopback support 端口调用，使用本机服务凭证和经正常管理员登录核实的身份；不在命令参数、清单或日志中保存凭证。
5. 目标必须存在可用的三级 Java 分类和教师。工具默认选择已启用 Java 分类的首个三级叶子及本机 accounts.json 的 teacher；也可用 `--category-id`、`--teacher-id` 明确指定。没有匹配项会停止，不临时造分类或角色。
6. 存储空间应覆盖新增对象字节、升级后的完整备份和当前镜像包。所有 URL 必须来自目标部署的 IPv4 loopback 映射，工具不读取 HTTP 代理环境。

## 先计划，再在隔离环境做三文件演练

以下命令在仓库根目录运行，`<source>` 是原始视频根目录，`<isolated-home>` 是已恢复且运行中的隔离部署目录。

```powershell
# 默认只读源文件并生成私有清单，不登录、不调用业务 API。
python deploy/compact/import_local_course.py --home <isolated-home> --source-root "<source>"

# 精确选择三文件，应包括一个超过200MiB的源和两个普通源。
# --include 必须使用源目录相对路径、正斜杠和原文件名。
python deploy/compact/import_local_course.py --home <isolated-home> --source-root "<source>" --course-name "Java微服务实战-隔离验证" --include "day01/<file1>.mp4" --include "day02/<file2>.mp4" --include "day03/<large-file>.mp4" --apply
```

`--limit 1` 至 `--limit 3` 也可用于取自然顺序最前几个文件；与 `--include` 互斥。任何部分选择都拒绝指向日常 `tianji-compact` 项目，不能把三文件结果当作全量交付。

重复运行完全相同的三文件命令，应复用同一课程、目录和媒资，不重复发布。随后实际体验游客发现课程、学生免费报名／播放／计时，以及教师学情。大文件应确认真实 Range 字节可读。

## 全量与复验

隔离演练通过后，由交付流程在日常部署执行：

```powershell
# 先保持默认dry-run，确认12章、161文件、字节数和4个大文件。
python deploy/compact/import_local_course.py --source-root "<source>"

# 使用同一组参数明确应用；仅本命令写入正常业务数据。
python deploy/compact/import_local_course.py --source-root "<source>" --apply

# 复验不改课程或媒资；正常身份登录会生成登录记录。
python deploy/compact/import_local_course.py --source-root "<source>" --verify
```

每次 apply／verify 重新核对源文件大小、时间戳及哈希；每个媒资采用后端 `ffprobe` 的真实时长，课程小节时长取 `CEIL(duration)`。完成后核对全部新对象字节 SHA-256、正式目录、免费课程状态、旧可见课程 ID 集合及搜索可发现性。该集合检查不替代整体数据库历史、备份和恢复验收。

## 中断、安全及保留

- 清单自动放在目标 `.local/course-import/<fingerprint>/manifest.json`，可用 `--manifest` 显式指定该目标 `.local` 下路径。它含私有源路径、文件哈希和业务 ID，不能提交 Git。
- 课程介绍包含导入所有权标识。同名而无匹配标识的课程、变更的目录或跨部署清单都停止处理，绝不覆盖已有课程。
- 媒资使用稳定幂等键；429 按服务端重试提示等待并继续使用原键。断网或未决任务停止后，修复原因并重跑同命令继续，不删除已登记资源。
- 导入与备份／发布竞争同一维护锁，避免导入中途被停写备份打断。
- 发布响应丢失后，通过正常 API 识别已上架的所属课程，只复验和补索引，不下架重建。
- 最终禁用离线导入配置并移除只读源挂载，再完成包含新增对象的完整备份。播放使用已持久化对象，不依赖源目录挂载。
- 本工具不会自动删除失败草稿、媒资、历史数据或恢复材料。需要清理时使用项目既有归属和可恢复性清单流程。

## 定向工具验证

```powershell
python -m unittest discover -s deploy/compact -p test_import_local_course.py
```

工具单测覆盖源清单、截短命名、默认不写业务、同名保护、目标绑定、重入不重复、CEIL时长及429原键重试。真实视频可解码性、专用入口的权限／大小约束及三文件／全量运行结果必须来自实际后端与浏览器，不能用工具契约替身结果代替。
