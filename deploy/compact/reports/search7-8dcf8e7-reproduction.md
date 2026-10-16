# 8dc基线的独立ES7适配复现

本页记录已完成的构建归属与隔离ES9→7→9实际验收；完整交付及恢复业务链路见[最终主报告](learning-motivation-20261016.md)。生产主部署仍使用8dc与ES9；下面的support适配镜像用于独立兼容环境。

## 源码及构建归属

| 项目 | 本次真实值 |
| --- | --- |
| 生产基线 | `8dcf8e7d44c6048318ad1e306e1425c8fd40fb70` |
| 第一适配提交 | `509b1363e4595a239732404b1c0058c2633f7a55` |
| 修复后的本地适配提交 | `189f31eba6e71bf9f374fa15d68e0832e1c93e47`，父提交为上行509b |
| 其余六组 | identity、commerce、education、gateway、student、admin均保持8dc基线；只有support对应189f31e |
| 独立support镜像 | `tianji-opt/compact-support:learning-search7-8dcf8e7` |
| 本次镜像ID | `sha256:e5cae90507428e1655fed32fed1678a705a6d607c68d2bd479732c3c5e5f56af` |
| 本次support JAR SHA256 | `6d3c3f4de0cf2c8d35cce94746420f87854d3a9ff3e4e94135796e1752257c50` |
| 双提交patch SHA256 | `4e3b9a99f7d72878dbeba3f3c1609cbfaf24a64497177bca56a954eb160105b1` |

适配提交只存在本地，未推送到远端dev；本仓库发布[双提交patch](search7-8dcf8e7-adapter.patch)供复现，不要求远端能checkout到189f31e。patch包含从8dc到509b、再到189f31e的两项提交。八个适配路径限定为客户端依赖、连接／repository／projection／search service与两个测试的传输API翻译；其余生产功能沿用8dc。28项基线文件逐字节核对保留，六组生产镜像保持原ID，support OCI revision和base-commit标签均已核验。

## 缺陷与修复

第一适配版在真实ES7端点执行CourseRepositoryBulkTest时失败：incrementSold仍引用STORED类型的`increment_sold`，fresh ES7卷没有该存储脚本；初始化器只创建mapping。SearchCompatibilityTest只走搜索／metadata路径，因此它通过不能覆盖销量增量行为。

修复沿用8dc的INLINE语义：sold为null时先置0，然后按params.count增加。保留原参数映射，不临时seed存储脚本，不修改断言或跳过失败路径。CourseRepositoryBulkTest的11项、SearchCompatibilityTest的16项，共27项原断言完整保留；两测试在此次189f31e修复中内容未变。

本次已完成独立support的compact clean package，保留测试编译；构建时使用`-DskipTests`，因此不能把构建成功写成端点测试通过。主Boot／compact部署合同未因本地适配改变。原509b失败日志与归属材料保留。

## 复现源码与构建

在单独目录执行，勿覆盖已有部署或向其数据库／ES端点运行兼容测试。先下载本页patch，记住它的绝对路径，并具备Java21、Maven和正常的Git提交者配置。

```powershell
$patchPath = 'C:\path\search7-8dcf8e7-adapter.patch'
git clone --no-checkout https://github.com/W205614/Vocational-school.git tianji-es7-reproduction
cd tianji-es7-reproduction
git checkout --detach 8dcf8e7d44c6048318ad1e306e1425c8fd40fb70
git am "$patchPath"
mvn -B -Pcompact -pl tj-compact/support -am -DskipTests clean package
```

先把`$patchPath`设为下载patch的实际绝对路径。生成的JAR位于`tj-compact/support/target/tj-support-app.jar`。`git am`会使用本地提交者与时间，通常产生新的适配commit；JAR、镜像及其ID也应按实际重建产物重新记录，不能冒用本页189f31e／image／JAR哈希或本次manifest。新manifest应将自己的support commit、镜像ID、JAR哈希、OCI标签与8dc基线绑定，其余六组也必须逐项核对。

本页给出的是可复现源码和真实历史构建身份，不提供一键切换生产环境的授权或步骤。需要验证端点时，另建明确归属的ES7.17.29隔离环境并执行原两类测试，随后核对每阶段projection、浏览器及保留数据；禁止通过seed脚本、删断言或盲目重跑改写原失败。

## 实际隔离验收结果

`search-rollback-acceptance.json`及最终wrapper记录为PASSED，退出码0；生产基线8dc、ES7适配189f31e，主环境已恢复。本轮未删除镜像。

| 阶段 | 浏览器 | projection及保留数据 |
| --- | --- | --- |
| ES9／8dc | 37通过、0失败、35条件跳过 | 93门公开课程；pending0，名称／销量一致，retainedHistory=true |
| ES7.17.29／189f31e support＋其余六组8dc | 37通过、0失败、35条件跳过 | 96门公开课程；pending0，名称／销量一致，retainedHistory=true |
| 返回ES9／8dc | 37通过、0失败、35条件跳过 | 99门公开课程；pending0，名称／销量一致，retainedHistory=true |

各阶段浏览器使用新合成学生／课程，课程数随实际操作增长，不能把93→96→99当作同一静止数据集。每阶段均另核验原有记录ID保留；跳过不计通过。

新适配的CourseRepositoryBulkTest和SearchCompatibilityTest均实际执行，各1项用例，失败／错误／跳过为0；原11＋16共27项断言保留并通过。原509b的stored-script失败继续保留，不以本次成功覆盖原记录。本结果来自真实ES7端点，不再仅是静态编译；不能扩大为生产容量、HA或新库直接降级证明。
