# 天机学堂学习闭环、月度激励与双端改版交付报告

生产源码 `8dcf8e7d44c6048318ad1e306e1425c8fd40fb70` 已推送并激活主部署，[源码CI 38857524064](https://github.com/W205614/Vocational-school/actions/runs/38857524064) 三项成功。主环境195项后端、37项普通浏览器、安全、审计、Redis和历史门禁均通过，实际主界面复核通过。隔离旧备份恢复、四迁移、高级42项浏览器、真实互动及生产Clock结算已完成；结算后的原观察SQL发生1267并保留FAILED；同一订单第三轮正式只读续验现已PASSED，真实奖励用券及权益已核验。搜索9→7→9已通过，独立只读奖励UI和绑定恢复／搜索及本轮功能交付11项门禁现均已通过，主环境11容器健康；最终清理已通过，结果在下文单独记录；文档提交的Checks按本提交查询。

## 功能与入口

默认学生端为 `http://127.0.0.1:24500`，教师／管理端为 `http://127.0.0.1:24501`，网关为 `http://127.0.0.1:24310`。上述入口已在8dc主部署UI窗口实际读取；恢复／切换／清理后已复核11容器健康，运行镜像、JAR及全部网页资源符合冻结来源。

| 入口 | 本轮行为 |
| --- | --- |
| 学生 `/`、`/courses/:id` | 游客搜索和查看公开课程、价格及目录；购买时登录并返回原课程。草稿、下架、删除课程不公开；完结课程保留介绍但不开放报名；公开数据不返回收费媒体标识。 |
| `/learning` | 继续最近小节、查看待学习课程；按有效权益统计每小节最新试卷的待完成／待评分数，统计本人未读互动通知；失败独立显示不可用及重试，不伪装为零。周计划统一为“小节／周”，范围 1–50，显示本周完成量；分页课程以实际 weekFreq 初始化。 |
| 课程学习、`/notes` | 续学定位小节与进度，私人笔记支持课程／关键词筛选和时间点回看；权益失效后保留历史，收费播放及进度写入受后端限制。 |
| `/exams`、`/review` | 整份本人答卷完成评分后显示发布时的标准答案、解析及教师反馈；失分题可筛选、复习作答、标记掌握、重新加入复习，跨设备持久，原考试成绩不变；主观题复习不自动判分。 |
| `/points` | 月度签到日历、连续签到、积分来源与到账、个人排名和差距、历史赛季、成就及奖励记录。首次完成小节每次 10 分、每日最多 50 分；教师采纳回答每次 5 分、每日最多 20 分；重复事实及消息不重复奖励。签到保留原连续奖励。 |
| 月榜与奖励 | 按 Asia/Shanghai 自然月划分，积分降序、达到积分时间升序、用户 ID 升序产生唯一名次；次月 2 日起核对事件及消费回执后冻结快照。缺失回执阻断结算，冻结后的迟到积分进入核对，不自动改写名次；旧赛季只读，不补造旧积分或旧奖励。 |
| 成就与优惠券 | 首次结课、首次通过考试、连续完成学习 7 天及赛季荣誉；奖励默认关闭，管理员只配置未开始赛季，启用名次区间不得重叠，开季锁定；发放有持久请求、库存及限领校验、回执与失败重试，重复消息不重复发券。 |
| 课程问答、`/messages` | 讨论分页、我的提问和回复状态；教师答疑与采纳回答产生业务通知；私人笔记与公开讨论保持不同归属。 |
| 管理端 `/teaching`、`/exams` | 按实际授课关系选择课程、查看学生学习进度／完成情况及答疑；试卷评分按独立 grader 授权查看待评分和考试统计，授课权与评分权互不替代；删除的授课关系不再授权。 |
| `/seasons`、管理资源页 | 赛季状态、积分来源、未来奖励规则、发放结果及失败重试；课程、题库、通知、优惠券等表单使用明确名称和状态。 |
| `/orders` 结算 | 持有超过 6 张有效券时要求选择最多 6 张计算方案，允许显式无券；计算超时给出提示，不阻断无券购买。 |

双端统一导航、字号、间距、表单、状态反馈及窄屏布局；门户按 editorial-report 方向使用课程与目录组织内容，保留蓝色品牌，不虚构评价、学员数量或效果数据。新增接口沿用 `/api/v2`、持久异步操作和幂等请求；后端校验归属及角色，公开读取走明确白名单。运行账号维持所属库 DML 权限，迁移独立执行，不为新增月份动态建表。

## 当前已核验结果

主环境证据绑定batch `b7598ddf6154457ca6a808d7e5c4a922`、同一8dc源码、镜像与配置；不以旧912阶段计数替代。

| 范围 | 实际结果与证据 |
| --- | --- |
| 主后端 | 195项，0失败／错误／跳过，含9项真实socket；该批backend-result.json。 |
| 主普通浏览器 | 37通过／0失败／35角色或条件不适用跳过；跳过不计通过。 |
| 安全／审计／Redis | 安全26、持久审计3通过；Redis5条非空记录／9检查通过，含二进制／类型、绝对过期及非空目标拒绝。 |
| 主历史保留 | 18张关键表、1696个预存ID、51个原有媒资核对通过，缺失ID和变化媒资为零。这不是全库总表数／行数。 |
| 实际部署绑定 | 7镜像、5JAR与全部网页assets核对通过，11容器健康；35个其他项目容器、224个原有卷、6284个原有Docker文件，以及基础设施身份、挂载与Compose非镜像配置保留。deployment-verification.json。 |
| 最新停写备份 | 8dc备份 `20261016T055632.071775Z` verified／quiesced=true，含12应用数据库和53媒资；旧912的035549／51媒资备份保留为阶段材料。 |
| 隔离原始恢复与升级 | `final-upgrade-1016-061134`：183表内容校验一致、51媒资、4条Redis值／过期、240个broker归档成员及字节校验通过；四项正式增量迁移通过，镜像绑定／搜索重建／业务smoke通过。恢复工具状态PASSED；旧应用未在raw阶段启动。 |
| 隔离高级浏览器 | 同一8dc恢复副本42通过／0失败／30角色不适用跳过；独立于主37项。 |
| 真实互动业务 | live-interaction-result.json PASSED：5次真实教师采纳积分5→10→15→20→20，4份积分事实、同回答重复无新增；broker采纳／回复通知各5。overview真实内部调用无降级，未读+5／真实标读-1、待考0→1→1→0→1→0；考试消费后10学习积分、首次结课徽章1，version2重考不重复。此为HTTP／DB／broker探针，新增Playwright数0。 |
| 生产Clock结算阶段 | 真实生产award→settle生成最终snapshot、reward_request及outbox；真实HTTP readiness／receipt核对（exam HTTP2次，pending0／eventCount0／ready=true；user HTTP1次）、6项calendar（闰月／跨年／UTC16点上海切日／非法月份／grace）和2项冻结榜分页通过；冻结后迟到核对、重复settle亦验证。不是只mock依赖的单元结论。 |
| 故障／发奖与付款观测 | 已真实暂停隔离commerce，验证SENT／持久queue／request PENDING／未扣库存券0，finally恢复；随后真实snapshot→request→broker发券→ACK→本地模拟付款→消费奖励券→权益已完成。原观察SQL1267保留FAILED；`live-settlement-final-result.json` 为同一订单PASSED：库存仅扣1、券使用1、付款／3份broker回执／权益／学生GET均精确核对。正式续验无业务表写入、无业务命令重放。只读snapshot断言jdbcReadOnly=true／sessionTransactionReadOnly=1／snapshotUnchanged=true。 |

### 本轮连续与总门禁

恢复bound与搜索bound均为PASSED，完整功能交付门禁11项全部true；原183表恢复→四迁移→42高级浏览器→真实互动→生产Clock结算／真实发奖付款→同订单只读续验→6UI／6HTTP只读奖励界面形成连续证据。搜索9→7→9各阶段另有37／0／35浏览器、projection及原记录保留。主环境已恢复11容器健康；这些门禁不扩大为真实支付供应商、正式容量或外部旧生产割接证明。

## 界面与代理角色实测

使用design-taste-frontend的editorial-report方向组织门户，真实课程／目录与功能片段替代抽象装饰。8dc主环境三角色8路由×1440／768／390共24状态通过，无文档横向溢出、页面异常或API错误；另8项有限可访问性检查通过。实际提醒1／0／0、两错误字段null；奖励实际0保持空态，优惠券实际10张，按真实名称筛选／选择／取消通过。

门户source lint为0FAIL／0WARN，实际inspector为0FAIL／2WARN；8张inspector图及1张减少动效图逐张查看，重复课程网格等设计限制和主观7.0评分保留。24个触控目标中4个约22.4px文字导航依赖周围间距、14个低于44px舒适目标；这不是完整WCAG认证。多角色“用户实际测试”指代理操作，不代表真人满意度、教学效果或用户研究。

4张公开截图均从8dc实际主部署新捕获，逐张视觉与隐私检查，复制哈希一致；没有改像素或数值。旧912原图私有保留，不作为当前来源。合成课程／运营数据及界面示例不暗示真实学员规模。

| 公开截图 | SHA256 |
| --- | --- |
| [portal-1440.png](assets/20261016/portal-1440.png) | `e920cd23303eb62ee416fda5fedfda8d2ac52c7e308b122e9d56a3cb15a40b8b` |
| [portal-390.png](assets/20261016/portal-390.png) | `4fdecb1429540e26ee06ec412149bc2b4b0ab63e7074bd2a65286c8c4632e363` |
| [points-calendar-390.png](assets/20261016/points-calendar-390.png) | `24b77aae38298c89bf324d6582b80b015da77987fc08d878d169c4825e860a9c` |
| [season-admin-1440.png](assets/20261016/season-admin-1440.png) | `61a9ad59a87bfc21b3f9bad634422c3576c2a1e27b03f7e877b9858f8f26480a` |

## 关键失败、修复及保留证据

| 阶段 | 根因与处理 |
| --- | --- |
| 9043 CI38752703349 | 高级fixture的CONVERT(0x… USING utf8mb4)与列同为coercibility2，跨collation比较1267；改为真正_utf8mb4字面量并补工具契约。 |
| c8 CI38758143466 | 40／1／29：mastered被序列化为字符串“0”，UI truthiness误判；后端统一boolean、前端兼容旧格式并补真实DB契约。 |
| 742 CI38761299122、c4 CI38765112138 | 前者在组合有效券谓词失败，原日志不能唯一定位某字段；另已证实DATETIME(0)对.900秒进位边界，发奖起效时间按秒截断。新增回归曾用Timestamp造成UTC偏8小时，测试改直接LocalDateTime；9项真实DB分别UTC／上海通过，强断言保留。 |
| edcf／9a8远端全绿但保留数据实测失败 | 自动配置组合使分页插件退让，超过100条课程引发RPC400；保证分页／动态表／填充共同安装，选项真实分页取全，不扩大RPC100限制。9a8另有到期考试课程选项缺失，修复完整选项与初始路由竞态，未放宽原断言。16项定向、47工具、前端8与初始登录1项阶段证据保留。 |
| 912 fresh恢复 | 42项浏览器之后LEARNING_ACCEPT异步调用交互式/users/{id}反复401。2cf改用可信内部sessionIdentity并传权威userType；不以roleId代替类型，不放宽私有接口。新5项真实socket／Feign／MVC鉴权／异步DB及原5项定向通过；8dc真实互动现已通过。原FAILED保留。 |
| 2cf CI38856417921 | frontend／compact-browser成功，backend默认package时Boot主JAR嵌套导致跨模块测试编译失败。8dc仅两POM增加process-classes的contract-classes测试附件；保留可执行主JAR及testCompile。默认打包、主／薄／用户运行JAR结构强断言及原10项compact通过，8dc最终源码CI三项成功。2cf从未部署。 |
| 8dc恢复观察器 | 首次观察SQL1267，后续UTF-8／GBK和Spring额外SET READONLY技术失败均保留；最终同订单只读续验PASSED，不重复结算、发奖或付款。原live-settlement-result.json FAILED不覆盖，最终证明单独保存。 |
| ES7适配器509b | fresh ES7无increment_sold存储脚本，bulk断言真实失败；不临时seed脚本。仅本地适配副本改为与8dc等价的INLINE／sold null→0逻辑，保留所有断言，base仍8dc；新适配源码 `189f31eba6e71bf9f374fa15d68e0832e1c93e47`（父509b，基线8dc），support已重建为 `sha256:e5cae90507428e1655fed32fed1678a705a6d607c68d2bd479732c3c5e5f56af`，保留testCompile、全部断言；完整patch含两个适配提交。新适配ES7两方法、原11＋16共27断言已真实通过；9→7→9每阶段37／0／35、projection pending0、retainedHistory=true，最终wrapper退出0／主环境已恢复。静态归属与复现见[ES7说明](search7-8dcf8e7-reproduction.md)。 |

失败日志／原始状态均保留；未靠盲目重跑、跳过编译、放宽断言、forceclick或刷新掩盖。阶段166／190后端与912界面记录仍可追溯，但最终主环境计数采用195。

### 只读续验观察器的两次技术失败

原结算观察SQL的1267之外，续验观察器又分别遇到UTF-8配置／GBK读取兼容问题，以及Spring额外SET READONLY被已处于只读事务的JDBC连接拒绝；两次技术FAILED及日志均保留，没有重复业务。最终观察器采用显式propagates配置＋事务及三重只读断言；真实Connector/J9.4.0短SELECT通过后，第三轮正式same-order续验已PASSED。`live-settlement-final-result.json` 绑定原request／order／源码及原失败哈希，readOnly=true、businessDataWrites=false、businessCommandsExecuted=false；认证HTTP读取创建会话如实记录，不将其声称为完全无任何系统写入。只读重启持久性验收与原先42浏览器分别记录，不把重启后的浏览器新鲜度放宽用于业务重放。

### 只读奖励UI的preflight技术失败与最终通过

正式同单观察通过后，readonly UI helper曾错误要求web容器内部端口80，而本轮实际配置为8080，preflight即拒绝；该次未启动浏览器、没有业务写入，原失败保留。前端代理已按实际8080修复helper，原隔离／来源／只读守卫保留，发布manifest同步10文件；不是改变业务断言。新的UI-only wrapper（session91283，final-recovery-ui-8dc-wrapper.log）只启动独立副本并验证UI，不再调用observer。新UI-only实际结果已单独PASSED：6项UI＋6项HTTP、businessRecordsWritten=false，核7镜像并保持原只读守卫；主环境随后11容器健康。未将main24状态或同单观察PASS替代此项。

## 测试边界与数据来源

| 证据层 | 覆盖与限制 |
| --- | --- |
| 浏览器与真实接口 | 游客边界、购买学习、笔记／讨论、计划签到、评分复习、跨设备与退款历史；支付为本地模拟，媒体为合成文件。不能撤回已下载字节，不验证真实观看或完整防作弊。 |
| 高级合成fixture | 只在明确隔离副本或临时CI启用，专用Synthetic课程、权益、错题快照与只读历史榜；奖励fixture预置PENDING请求／Outbox，真实应用扣库存发券／ACK后才写成功manifest。没有SQL写成功user_coupon、成就或FULFILLED。该fixture本身不等于自然结算。 |
| 真实互动／Clock探针 | 仅合成身份／课程／奖励规则等基础数据，未seed成功积分／小节完成／成就／最终snapshot/request/ACK。宿主时间不变，直接调用生产Clock服务，跨服务走真实HTTP／broker。六calendar、冻结榜分页及实际settle已通过；实际exam readiness走pending0／eventCount0／ready=true，不宣称真实未消费exam积压阻断已实测。同订单最终用券只读观测、只读奖励UI及绑定恢复总证明均通过。 |
| 真实DB专项 | LearningMotivationReliabilityTest调用真实settle与事务验证50分封顶、同分、迟到、restart补空月、七天等；该类readiness／昵称客户端有替身，不能冒称全栈。真实Clock探针补充实际跨服务readiness。 |
| 发券与时间专项 | SeasonRewardDeliveryReliabilityTest的9项使用InnoDB／锁／事务与同连接JDBC mapper桥接，含库存竞争、重放、补偿、ACK故障回滚与秒精度；不是9次真实broker故障。实际consumer故障段由隔离探针补充。 |
| 多券与超时 | 真实HTTP10券→要求选择、6券成功、7券400、显式无券成功；穷举oracle保留1–6券。超时用executor不执行注入，不能宣称真实供应商超时实测；本次10券专项未下单。奖励专属券实际订单已由正式同单只读续验核对付款、消费及权益；不扩大为真实支付供应商实测。 |
| 权限与答案 | owner、FINISHED快照、未授权教师403／deleted授课排除、grader分离、到期退款历史均有实际浏览器或DB／HTTP检查；局部DB专项CourseClient／权益元数据为有界替身。新5项异步认证用真实HTTP Feign＋MVC拦截器及DB，授课元数据仍为替身；不是完整identity容器启动。 |

真实支付、短信、正式容量、长期稳定性及多节点HA不属于本轮已验证能力；默认奖励关闭。私密账号、SQL、配置、完整日志和恢复材料不发布。

## 备份、原有数据、回滚与清理

最终备份使用8dc的055632／53媒资；隔离升级故意恢复更早c3d6971原始备份的183表／51媒资／Redis4／broker240，再执行learning V008/V009、promotion V004、exam V004。两者用途不同，不能将旧恢复51误写成当前备份53中的文件丢失。当前compact既有订单／学习／答卷和媒资保留；外部另一套旧生产数据完整导入与正式割接未验收，不能解释为本轮丢弃当前数据。

原旧OCI描述符不可取得；从仍存旧运行容器导出rootfs制作7个verified-container-rootfs-export派生回滚镜像，5JAR对照原c3d6971既有哈希、网页与归档另存哈希。升级前派生旧应用实际健康，并用原账号检查9个身份／业务GET（学生资料、订单、课程、笔记、答卷，教师评分，管理员交易／考试审计及失败操作）。证明选定旧运行字节可启动，不代表恢复原OCI身份或验证新库直接降级。

仅删除本项目已验证无引用资源：两批77个旧search7 Maven target／3604文件／1,539,282,833字节，第二批source HEAD／tracked状态前后不变，0唯一测试报告；保留所有源码、.git、target外JAR副本、日志、备份、镜像及数据。三轮网络12＋1＋9累计22删除；首轮231卷、追加当轮235卷及最终全局252卷均全部保留，其他项目网络未改变。224是更新前原卷保留基线，235是另一时点全卷清单，不能相减当作删除量。本轮全部镜像保留，不删除镜像。最终容器清理结果见下方。

原Compose本地变更和deploy/docker/保留，不混入功能提交。仅提交本轮审查后源码、文档、公开截图与安全探针源码；探针私密输出固定ignored路径。

## 最终清理

最终清理实际PASSED：删除9个本项目演练project的59个临时容器，主项目仅保留最终11容器且全部healthy；另外删除9个零容器引用网络，连同前两轮12和1累计22个。27个owned演练卷全部保留；全局252个卷的前后集合完全一致，未使用-v、未删卷，也没有删除任何镜像。224是升级前原有卷保留基线、27是owned演练子集、252是最终时点全局集合，三者不可相减或相加当作删除量。

四个Redis镜像自动声明/data产生的匿名卷，曾被prefix守卫拒绝；最终仅按精确4个volume ID、容器配置hash、唯一自身引用及镜像VOLUME声明建立严格归属例外。该例外用于确认可移除所属临时容器，**不是删卷例外**，四卷仍全部保留。两个备份验证通过，全部备份、派生回滚镜像、其他镜像、源码和日志／恢复材料保留。

| 已退役的演练project | 类型 |
| --- | --- |
| `tianji-opt-learning-search-1016-062611` | 搜索兼容副本 |
| `tianji-opt-learning-search-1016-064825` | 搜索兼容副本 |
| `tianji-recovery-final-upgrade-1016-051004` | 独立恢复／升级演练副本 |
| `tianji-recovery-final-upgrade-1016-061134` | 独立恢复／升级演练副本 |
| `tianji-recovery-upgrade-20261015b` | 独立恢复／升级演练副本 |
| `tianji-redis-proof-20261016040428-source` | Redis源／目标恢复证明副本 |
| `tianji-redis-proof-20261016040428-target` | Redis源／目标恢复证明副本 |
| `tianji-redis-proof-20261016060443-source` | Redis源／目标恢复证明副本 |
| `tianji-redis-proof-20261016060443-target` | Redis源／目标恢复证明副本 |

真实清理结果为retirement-result.json与final-cleanup-inventory.json，网络累计按三份带时间戳network-retirement结果求和；不是把最新单轮9写成总数。清理后的最终部署产物／配置／原文件核验由发布pipeline再执行并汇总至脱敏摘要与最终回复，文档不提前声称最终stage隐私核验已经完成。

## 镜像与产物归属

以下七组均属于生产源码 `8dcf8e7d44c6048318ad1e306e1425c8fd40fb70`，部署实字节核验包含5个JAR与双端全部网页assets。独立ES7的support189f31e不混作生产support；其归属见[ES7复现与实际结果](search7-8dcf8e7-reproduction.md)。

| 生产组 | 实际镜像ID | JAR SHA256／网页归属 |
| --- | --- | --- |
| identity | `sha256:b7de3cf8ccd98be37b641427cd2f1636e21da6c5938426087e7538785ec7a984` | `dd038da07b00b70ce1d0f9fd3f6d002857cc63809c971271e82c650da38f743f` |
| commerce | `sha256:507933532e8a9b047f1545205663e79554a46642d5f9bc8c2685feaa962354a9` | `e4738414a327789ae127ad3a7d03d2b3199866ad13bf83dd9c0902ed02fa5cd1` |
| education | `sha256:a09a053c53b796a356eafb48c6440f1423342f22fb82f96b614ed17c6431b875` | `aedf665fc486eadbf40b8a9dc1e678ac01295d1d60b459bdf725c7996232779c` |
| support | `sha256:12692d93023ab85da517aa1634cd4a6bc7fb64e010002073ab04f319be1908e7` | `d888da9f354a94fe538971311dd05f222d201f1810a3927e1ca41a50c6108dfe` |
| gateway | `sha256:5f94aaa28704c43c2e5981d5d1993eb35bd1fa444dd08520e18f4d87456f4d4b` | `20d2dc2d1df0718a928e252c28379b1a40a6d014da6a6ca4e60090b92ff5d7d8` |
| student | `sha256:c47fa45084f024cdb1298eb52237e04a745191474f650ccfbfe31d26a13d6fc5` | 8dc全部网页assets逐文件核验；不含JAR |
| admin | `sha256:4e074ff7d99ca306ee5fe10cdc77ffae3d25546aef314f1f69fff9de09d84745` | 8dc全部网页assets逐文件核验；不含JAR |

## 公开材料及CI

生产源码[8dc对应CI 38857524064](https://github.com/W205614/Vocational-school/actions/runs/38857524064) 的backend／frontend／compact-browser全部success。文档提交不冒充已编译Java的来源；最终文档状态请查看**本提交的Actions Checks**，最终交付回复列出准确远端SHA与对应CI链接，避免文档自引用自身尚未生成的提交SHA。

- [脱敏验收摘要](2026-10-16-learning-motivation-summary.json)：仅公开来源、布尔、计数与哈希，完整私有配置／SQL／账号／日志保留在ignored材料。
- [安全探针说明](probes/README.md)、[只读奖励UI说明](probes/LIVE_REWARD_UI.md)及[源码哈希清单](probes/learning-source-manifest.json)。十份源码／说明分别为：[互动探针](probes/live-interaction-probe.py)、[结算runner](probes/run_live_settlement.py)、[结算Java](probes/LiveSettlementProbe.java)、[guard测试](probes/test_live_settlement_guards.py)、[分页核验](probes/verify_live_learning_pagination.py)、[DTO契约探针](probes/FixtureDtoContractProbe.java)、[奖励UI runner](probes/run_live_reward_ui.py)、[奖励UI脚本](probes/live_reward_ui.mjs)及前述两份说明。所有生成身份、SQL、日志和结果固定写ignored目录，不随公开源码目录生成。
- [portal桌面](assets/20261016/portal-1440.png)、[portal手机](assets/20261016/portal-390.png)、[签到日历](assets/20261016/points-calendar-390.png)、[管理赛季](assets/20261016/season-admin-1440.png)：上文记录本次8dc实际截图与哈希。
- [双提交ES7 patch](search7-8dcf8e7-adapter.patch)及[复现说明](search7-8dcf8e7-reproduction.md)：本地适配commit未推送远端，新建commit／镜像必须重新生成自己的manifest。

公开提交前核对暂存文件白名单、私有凭据字面值、十份探针源码／说明的原始字节哈希，以及四张公开截图的哈希；检查结果随本机交付记录保留。准备阶段安全guard曾拒绝不满足归属／字节／只读约定的状态，原拒绝保留，按实际契约精确调整；未放宽最终门禁断言。

私有证据包括同批evidence/b7598ddf6154457ca6a808d7e5c4a922、source-8dc-ci、deployment-verification、final-backup、live-interaction／live-settlement原FAILED及final-result、readonlyUI最终汇总与search证明，以及两批文件／网络清理材料；公开文件不复制私密原文。当前compact既有数据保留与未验证的外部旧生产正式割接有明确区别。
