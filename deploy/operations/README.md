# 共享运行维护库

`runtime_backup.py` 和 `runtime_redis_snapshot.py` 为 compact 与旧 final 命令提供同一套快照捕获、校验及 Redis 逻辑。调用捕获时必须显式提供运行配置和源码目录，库本身不选择或启动任何部署。

原 final 文件保留为兼容入口，已有备份格式、保留规则和恢复字节校验不变。compact 不再导入历史部署的配置模块。本库用于本机停写快照；生产在线备份、PITR 和异地恢复由平台配置并演练，要求见[生产部署手册](../production/README.md)。
