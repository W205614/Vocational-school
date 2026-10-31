# 认证密钥维护

本文描述已有认证机制的密钥更换与验证，不创建云资源，不改动日常配置或自动备份。本机备份继续停用；源码中的轮换支持不代表已经对本机或云环境执行密钥切换。

## JWT：先分发验签公钥，再切换签名键

JWT 使用 RSA 签名。新令牌的 `kid` 是公钥 DER 编码的 SHA-256 base64url 值；网关与刷新接口仅使用显式信任的公钥验签，不读取令牌中的公钥或远程地址。新旧公钥使用 [JWK Set](https://www.rfc-editor.org/rfc/rfc7517) 发布，RSA 参数采用 [RFC 7518](https://www.rfc-editor.org/rfc/rfc7518#section-6.3.1) 的 `n`、`e` 编码。

现有 `GET /jwks` 默认仍返回当前公钥的 base64，供尚未升级的 SDK 使用；新 SDK 调用相同路径的 `?format=jwks` 获取公钥集合。该请求沿用服务身份和已有 RPC 授权，没有新增公开业务入口。网关每10秒读取一次整个集合并原子替换；请求失败或格式错误不替换有效集合。默认超过60秒未成功刷新即拒绝鉴权并退出就绪状态，返回服务暂不可用；`tj.auth.jwk-max-age-seconds` 可在10至300秒内显式设置，但必须大于 `tj.auth.jwk-retry-ms` 指定的刷新间隔加3秒 HTTP 超时预算，否则启动失败。缺失、未知的 `kid` 不会触发无界网络查询。

先在隔离候选环境验证以下步骤，再考虑实际维护：

1. 所有身份服务和网关先升级到支持公钥集合的同一版本，期间保留现有签名键。不要在旧 SDK 仍运行时改变签名键。
2. 在受保护的 `signing.jks` 中加入下一把密钥和证书，保留当前签名别名。通过 `tj.auth.jwt.verification-aliases=next` 只加入需要重叠的证书别名，最多七个，不重复当前签名别名。所有身份副本先使用这个集合；逐个确认网关已经成功加载新旧公钥，不能只等待固定秒数后假定成功。
3. 将 `encrypt.key-store.alias` 改为下一把密钥的别名，同时把旧证书加入 `verification-aliases`，再滚动切换身份服务。此时新旧副本都能验证两代访问和刷新令牌。当前 JKS 的新私钥密码必须与配置对应，不能只替换一个不匹配的文件。
4. 保留旧公钥直至最后一个旧签名副本停止签发后，最长刷新令牌有效期结束（当前记住登录最长七天），或明确撤销相关会话并接受重新登录。移除旧证书后，网关在成功刷新集合时停止接受旧签名；旧格式没有 `kid` 的令牌也仅能使用仍被显式信任的公钥。
5. 退役前回退保留双公钥集合，仅恢复签名别名；退役后若要恢复旧签名，必须先恢复接收方对旧公钥的信任，再启动签发。不要把签名键轮换与数据库回滚混为一体。

独立生产渲染输入使用：

```yaml
signing:
  active_alias: tianji
  verification_aliases: [next]
```

这里仅填写证书别名。私钥和 JKS 密码仍由原有受保护 Secret 提供。渲染不会修改 Secret 或发布集群。实际多副本维护的可用性与缓存更新时延仍需在目标环境观察；本地公钥和 HTTP 回归不能替代该演练。

## MFA：保留解密窗口并重加密存量

MFA 的当前写入键仍为 `tj.auth.mfa.encryption-key` / `TJ_MFA_ENCRYPTION_KEY`。密钥必须是32字节随机值的 base64，不能放入命令行、公共配置或报告。轮换使用显式旧键集合读取存量密文；不通过重新登记验证器替代数据迁移，也不直接覆盖当前键后尝试登录。

密文使用 `v1.<key-id>.<base64载荷>`，key-id 是解码后 AES 密钥的 SHA-256 小写十六进制值。AAD 同时绑定版本、键ID和账户ID。当前键只负责写入，最多两把历史键通过 `tj.auth.mfa.decryption-keys` / `TJ_AUTH_MFA_DECRYPTION_KEYS` 的 JSON 对象（`{"旧key-id":"旧键base64"}`）用于读取；不得重复当前键。既有无版本密文以 `tj.auth.mfa.legacy-key-id` / `TJ_AUTH_MFA_LEGACY_KEY_ID` 明确选择解密键，未设置时使用当前键。

首次从不识别 v1 的旧二进制升级时，先暂停 MFA 登记、确认等写入，让所有实例都升级后再开放；不可让旧二进制读取新格式。后续轮换先让所有实例能读取两代密文，再切换当前写入键。批量重加密期间暂停所有 MFA 写入，核对维护回执与新密文覆盖后，确保全部实例使用新写入键，最后退役旧解密键。退役时同步移除旧 `legacy-key-id` 或改为当前键ID。

一次性工具不启动应用，也不调用云供应商。它仅维护受保护输入明确指定的合成测试库或隔离候选库；所有 MFA 行在同一事务内加锁重加密，失败整体回滚，保留启用状态、待确认期限、已消费验证码时间步及原修改时间。已属于当前键的密文仍需解密验证，重复执行不再改写。

准备仅操作员可读的 JSON 文件；Windows 复用 `deploy/production/database.py` 的 `protect(path)` 收紧专用文件 ACL，其他系统使用0600权限。字段契约：

```json
{
  "schema": "tianji-mfa-maintenance-v1",
  "environment": "isolated-production-candidate",
  "host": "<实际候选数据库DNS>",
  "port": 3306,
  "database": "tj_auth",
  "username": "<独立维护账号>",
  "password": "<维护凭据>",
  "activeKey": "<当前写入键base64>",
  "decryptionKeys": {"<旧key-id>": "<旧键base64>"},
  "legacyKeyId": "<无版本密文对应的旧key-id>"
}
```

候选连接固定使用 `VERIFY_IDENTITY`，拒绝通用 root/admin 身份；私有CA需要受审的 `trustStoreUrl`（file URL）及必要的 `trustStorePassword`。本地合成验证使用 `environment=isolated-test`，仅接受回环地址和 `acceptance_*` 库，不能改成日常业务库。凭据和 AES 键均只存于受保护文件，不传入命令行。

先用普通非 compact profile 构建当前认证可执行 JAR，再显式调用：

```powershell
python deploy/production/mfa_reencrypt.py --settings E:/secure/mfa-candidate.json --jar tj-auth/tj-auth-service/target/tj-auth-service.jar --expected-host <候选DNS> --expected-database tj_auth --receipt E:/secure/mfa-receipt-new.json
```

回执要求新路径，只记录目标、JAR哈希、键ID和处理计数；`PREPARED_NOT_APPLIED` 或 `FAILED_OR_UNCONFIRMED` 均须人工核对数据库后再决定下一步，不能当作已经回滚或已经完成。只有 `APPLIED` 表示该次工具观察到事务成功；它不证明生产切换、旧键销毁或备份存在。此工具不会启用备份，也不能恢复已经丢失的旧解密键。
