# 职业教育在线学习平台 🎓

基于 Spring Cloud Alibaba 微服务架构构建的职业教育在线学习平台，覆盖课程、题库、考试、积分、排行榜、优惠券、订单支付、消息通知等完整业务闭环。后端以微服务方式拆分 16 个模块，支持高并发秒杀式领券、签到积分、兑换码发放、Redis + LUA 原子控制等企业级场景。

## ✨ 功能特点

- 🏗️ **微服务架构**: 基于 Spring Cloud Alibaba，服务注册/配置中心 Nacos，统一网关 Gateway 负责路由转发与鉴权
- 🔐 **统一鉴权体系**: 网关层 JWT 解析 + 权限校验（`AccountAuthFilter`），登录态通过请求头透传给下游微服务，RBAC 权限模型（角色-权限-菜单）
- 🎫 **优惠券秒级领取**: Redis + LUA 脚本保证领券/兑换码高并发下的原子性与防超卖，MQ 异步落库削峰
- 🏆 **积分排行榜**: 按赛季（周榜/总榜）动态建表 + Redis ZSet 排名，签到得积分、查榜单
- 🔁 **兑换码机制**: 基于 Redis 位图（Bitmap）标记兑换状态 + ZSet 按序号区间快速定位优惠券，异步批量生成兑换码
- 🧮 **折扣策略模式**: 满减/折扣等优惠规则通过策略模式统一管理，核销/退款支持批量操作
- 🛒 **完整交易闭环**: 购物车 → 下单 → 支付（对接支付宝/微信）→ 退款，订单与支付分离服务
- 📚 **课程与考试**: 课程目录/分类管理、题库管理、考试出题，学习进度与互动问答（含管理端审核）
- 🔍 **全文搜索**: Elasticsearch 课程检索，搜索历史/兴趣偏好记录，个性化推荐接口
- 🎬 **媒资管理**: 文件上传与视频媒资管理（对接阿里云 OSS / 腾讯云 COS、VOD）
- 📊 **数据中心**: 首页数据看板、今日数据、TOP10 榜单配置，面向运营分析
- 🧱 **工程化**: 公共模块抽离（统一异常/返回体/工具类/自动配置）、xxl-job 分布式定时任务、Redisson 分布式锁、Docker + Jenkins 一键部署

## 🏗️ 技术栈

- **基础框架**: Spring Boot 2.7.2 + Spring Cloud 2021.0.3 + Spring Cloud Alibaba 2021.0.1.0
- **注册/配置中心**: Nacos（服务发现 + 共享配置 `shared-*.yaml`）
- **网关**: Spring Cloud Gateway（路由 + 全局鉴权过滤器 + 跨域 + Swagger 聚合）
- **ORM**: MyBatis-Plus 3.4.3（分页插件、条件构造器）
- **缓存/分布式**: Redis + Redisson 3.13.6（分布式锁、LUA 脚本、位图、ZSet）
- **消息队列**: RabbitMQ（异步削峰、业务解耦）
- **定时任务**: xxl-job 2.3.1 分布式调度
- **分布式事务**: Seata 1.5.1（依赖管理预留）
- **搜索**: Elasticsearch 7.12.1
- **接口文档**: Knife4j / Swagger 3.0.3（网关聚合各服务文档）
- **云厂商 SDK**: 阿里云（OSS/KMS/支付宝）、腾讯云（COS/VOD）
- **工具库**: Lombok、Hutool 5.7.17、MyBatis-Plus 代码生成
- **部署**: Docker（`openjdk:11` 基础镜像 + 时区/内存配置）+ Jenkins 构建脚本（`startup.sh`）

## 🏛️ 架构分层

```
                    ┌──────────────────────────────┐
                    │   Nacos 注册中心 / 配置中心    │
                    └──────────────▲───────────────┘
                                   │ 注册 / 拉取配置
┌──────────────┐   ┌───────────────┴───────────────┐
│   前端 / APP  │──▶│   Gateway 网关 (10010)        │
│              │   │  · 路由转发 (StripPrefix)      │
│              │   │  · 鉴权 (JWT 解析 + 权限校验)   │
│              │   │  · 跨域 / Swagger 聚合          │
└──────────────┘   └───────────────┬───────────────┘
                                   │
         ┌─────────────┬───────────┼───────────┬─────────────┐
         ▼             ▼           ▼           ▼             ▼
   ┌─────────┐   ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐
   │ auth    │   │ user    │  │ course  │  │ learning│  │ trade   │
   │ (8081)  │   │ (8082)  │  │ (8086)  │  │ (8090)  │  │ (8088)  │
   └─────────┘   └─────────┘  └─────────┘  └─────────┘  └─────────┘
   ┌─────────┐   ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐
   │ pay     │   │ search  │  │ media   │  │ message │  │ exam    │
   │ (8087)  │   │ (8083)  │  │ (8084)  │  │ (8085)  │  │ (8089)  │
   └─────────┘   └─────────┘  └─────────┘  └─────────┘  └─────────┘
   ┌─────────┐   ┌─────────┐  ┌─────────┐
   │ remark  │   │promotion│  │ data    │
   │ (8091)  │   │ (8092)  │  │ (8093)  │
   └─────────┘   └─────────┘  └─────────┘

   公共基础设施:  MySQL 8.0 · Redis · RabbitMQ · Elasticsearch · xxl-job
   公共模块:     tj-common (统一返回体/异常/工具/自动配置) · tj-api (Feign DTO/客户端) · tj-auth (SDK)
```

## 📁 项目结构

```
tjxt/                                  # 父工程 (pom 聚合, Spring Boot 2.7.2)
├── tj-common/                         # 公共模块
│   └── com/tianji/common/
│       ├── autoconfigure/             # 自动配置 (mq / mvc / mybatis / redisson / swagger / xxljob)
│       ├── constants/ enums/          # 常量与枚举
│       ├── domain/                    # 统一返回体 R、分页 PageDTO 等
│       ├── exceptions/                # 全局异常体系
│       ├── filters/  utils/  validate/ # 过滤器、工具类、参数校验
├── tj-api/                            # 跨服务 API 层
│   ├── client/                        # 各服务 Feign/Http 客户端 (auth/course/exam/...)
│   └── dto/                           # 跨服务 DTO
├── tj-auth/                           # 认证授权中心
│   ├── tj-auth-service/               # 账号 / 角色 / 权限 / 菜单 / 登录记录服务 (8081)
│   ├── tj-auth-common/                # JWT 常量等公共定义
│   ├── tj-auth-gateway-sdk/           # 网关鉴权 SDK (AuthUtil / JwtSignerHolder)
│   └── tj-auth-resource-sdk/          # 资源服务 SDK (用户上下文解析)
├── tj-gateway/                        # 网关 (10010): 路由 / 鉴权 / 跨域 / Swagger 聚合
├── tj-user/                           # 用户服务 (8082): 学生 / 教师 / 员工
├── tj-search/                         # 搜索服务 (8083): ES 课程检索 / 兴趣 / 推荐
├── tj-media/                          # 媒资服务 (8084): 文件 / 视频媒资
├── tj-message/                        # 消息服务 (8085): 短信等通知
├── tj-course/                         # 课程服务 (8086): 课程 / 分类 / 目录
├── tj-pay/                            # 支付服务 (8087): 对接第三方支付
├── tj-trade/                          # 交易服务 (8088): 购物车 / 订单 / 退款
├── tj-exam/                           # 考试服务 (8089): 题库 / 出题
├── tj-learning/                       # 学习服务 (8090): 我的课表 / 学习记录 / 签到 / 积分 / 排行榜 / 互动问答
├── tj-remark/                         # 点赞服务 (8091): 点赞记录
├── tj-promotion/                      # 营销服务 (8092): 优惠券 / 兑换码 / 折扣策略
├── tj-data/                           # 数据中心 (8093): 看板 / 今日数据 / TOP10
├── Dockerfile                         # 通用镜像 (JDK11, 时区 Asia/Shanghai)
├── startup.sh                         # Jenkins 构建部署脚本 (打镜像 + 起容器)
└── pom.xml                            # 依赖版本统一管理
```

> 说明：服务与网关路由前缀一一对应，例如 `user-service` → `lb://user-service`，前端通过网关 `StripPrefix` 后按 `/us/**`、`/cs/**` 等前缀访问各业务接口。

## 🚀 快速开始

### 前提条件

- JDK 11+
- Maven 3.6+
- MySQL 8.0、Redis、RabbitMQ、Elasticsearch 7.x（按需）
- Nacos 注册中心（服务发现 + 配置中心），提供共享配置 `shared-spring.yaml`、`shared-redis.yaml`、`shared-logs.yaml`
- Docker（可选，用于镜像部署）

### 环境准备

1. 启动 Nacos，导入共享配置（网关及各服务通过 `bootstrap.yml` 从 Nacos 拉取公共配置与日志配置）
2. 修改各服务 `src/main/resources/bootstrap-local.yml` 中的 Nacos 地址与命名空间，选择本地 profile：
   - 各服务 `bootstrap.yml` 默认 `active: dev`，本地联调时切换为 `local`
3. 准备 MySQL 数据库并导入各服务建表脚本（表结构以各服务 `domain/po` 实体为准）

### 编译与启动

```bash
# 1. 编译安装公共模块
mvn install -pl tj-common -am -DskipTests
mvn install -pl tj-api -am -DskipTests
mvn install -pl tj-auth/tj-auth-common -am -DskipTests

# 2. 依次启动各服务 (推荐启动顺序)
mvn -pl tj-auth/tj-auth-service spring-boot:run   # 认证中心 8081
mvn -pl tj-gateway spring-boot:run                # 网关 10010
mvn -pl tj-user spring-boot:run                   # 用户 8082
# ...其余业务服务按需启动
```

各服务端口一览：

| 服务 | 端口 | 服务 | 端口 |
|---|---|---|---|
| gateway-service | 10010 | trade-service | 8088 |
| auth-service | 8081 | exam-service | 8089 |
| user-service | 8082 | learning-service | 8090 |
| search-service | 8083 | remark-service | 8091 |
| media-service | 8084 | promotion-service | 8092 |
| message-service | 8085 | data-service | 8093 |
| course-service | 8086 | pay-service | 8087 |

### 访问接口文档

网关已聚合 Swagger，启动网关后访问：

```
http://localhost:10010/doc.html      # Knife4j 聚合文档
```

## 🔧 核心实现

### 1. 网关统一鉴权（JWT + 权限校验）

所有请求经网关 `AccountAuthFilter`（`GlobalFilter`，order=1000）：

- 白名单路径（如登录）直接放行，其余需校验 `Authorization` 请求头中的 JWT
- 解析出用户信息后，通过 `USER_HEADER` 请求头把 `userId` 透传给下游微服务
- 结合 `authUtil.checkAuth` 做接口权限校验，权限不足直接拦截返回

```java
// 获取请求，判断是否为无需登录的路径
if (isExcludePath(antPath)) {
    return chain.filter(exchange);
}
// 解析 token
R<LoginUserDTO> r = authUtil.parseToken(token);
// 登录态透传
exchange.mutate().request(builder -> builder.header(USER_HEADER, r.getData().getUserId().toString())).build();
// 校验权限
authUtil.checkAuth(antPath, r);
```

下游服务通过 `UserContext` 获取当前登录用户 id，实现无状态鉴权。

### 2. 优惠券高并发领取（Redis LUA + MQ 异步落库）

领券/兑换码属于高并发写场景，采用 **LUA 脚本原子校验 + MQ 异步落库** 两级削峰：

1. **LUA 脚本**（`lua/receive_coupon.lua`、`lua/exchange_coupon.lua`）在 Redis 内原子完成：校验券状态、每人限领、库存扣减、标记已领取，避免并发超卖与重复领取
2. 脚本执行成功后仅发送 MQ 消息（`PROMOTION_EXCHANGE`），由消费者 `checkAndCreateUserCoupon` 异步写库、累加发放数量
3. 兑换码使用 **Redis 位图**（`COUPON_CODE_MAP_KEY`）标记序列号是否已被兑换，ZSet（`COUPON_RANGE_KEY`）记录各批次序列号区间，兑换时 `rangeByScore` 秒级定位目标券

```java
@Lock  // Redisson 分布式锁兜底
public void receiveCoupon(Long couponId) {
    // 1. 执行 LUA 脚本原子校验，结果非 0 抛业务异常
    Long r = redisTemplate.execute(RECEIVE_COUPON_SCRIPT, List.of(key1, key2), userId.toString());
    // 2. 发送 MQ 消息，异步落库
    mqHelper.send(MqConstants.Exchange.PROMOTION_EXCHANGE, MqConstants.Key.COUPON_RECEIVE, uc);
}
```

### 3. 折扣策略模式

优惠券的满减/折扣规则通过策略模式组织（`strategy/discount/DiscountStrategy`）：

- 不同优惠类型对应不同 `DiscountStrategy` 实现，统一通过 `DiscountStrategy.getDiscount(discountType)` 获取
- 核销（`writeOffCoupon`）与退款（`refundCoupon`）支持批量处理，并在事务内维护优惠券已发放/已使用数量

### 4. 积分签到与排行榜

- **签到**: 按用户月维度使用 Redis 位图记录签到，连续签到发放积分
- **积分记录**: `PointsRecord` 记录积分流水，来源涵盖签到、学习等行为
- **排行榜**: 按赛季（周/总榜）动态建表，`PointsBoardSeason` 维护赛季信息，Redis ZSet 维护实时排名，定时任务结算落库，提供榜单查询与我的排名

### 5. 分布式定时任务（xxl-job）

- 通过 `tj-common` 的 xxl-job 自动配置接入调度中心
- 典型场景：优惠券到期/过期处理、排行榜赛季结算、兑换码批次管理

## 🧰 运维与部署

### Docker 镜像

根目录 `Dockerfile` 为通用微服务镜像（`openjdk:11.0-jre`，时区 `Asia/Shanghai`，支持 `JAVA_OPTS` 注入）：

```bash
docker build -t <service>:latest .
docker run -d --name <service> \
  -p "<port>:<port>" \
  -e JAVA_OPTS="-Xms300m -Xmx300m" \
  --network heima-net <service>:latest
```

### Jenkins 自动部署

`startup.sh` 已封装「拷贝 jar → 打镜像 → 起容器」全流程：

```bash
# 参数: -c 容器名  -n 项目名(产物jar名)  -d 工作区相对路径  -p 端口  -o JAVA_OPTS  -a 调试端口(可选)
sh startup.sh -c user-service -n user-service -d tj-user -p 8082
# 开启远程调试 (5005)
sh startup.sh -c user-service -n user-service -d tj-user -p 8082 -a 5005
```

## 📚 模块职责速览

| 模块 | 职责 | 核心接口（Controller） |
|---|---|---|
| tj-auth | 账号/角色/权限/菜单、登录记录、JWT | AccountController、RoleController、MenuController、PrivilegeController |
| tj-user | 学生/教师/员工管理 | StudentController、TeacherController、StaffController |
| tj-course | 课程/分类/目录管理 | CourseController、CategoryController、CatalogueController |
| tj-learning | 课表、学习记录、签到、积分、排行榜、互动问答 | LearningLessonController、SignRecordController、PointsRecordController、PointsBoardController、InteractionQuestionController |
| tj-search | ES 课程检索、兴趣、推荐 | CourseController、InterestsController、RecommendController |
| tj-media | 文件上传、视频媒资 | FileController、MediaController |
| tj-message | 短信通知 | message-service |
| tj-pay | 支付对接 | PayController |
| tj-trade | 购物车/订单/退款 | CartController、OrderController、RefundApplyController |
| tj-exam | 题库、出题 | QuestionController、QuestionBizController |
| tj-remark | 点赞 | LikedRecordController |
| tj-promotion | 优惠券、兑换码、折扣策略 | CouponController、CouponScopeController、ExchangeCodeController、UserCouponController |
| tj-data | 首页看板、今日数据、TOP10 | BoardController、TodayDataController、Top10Controller |

## ⚠️ 已知局限与后续优化方向

> 以下为本项目当前的设计边界，供后续维护者据此优化。

### 1. 本地联调依赖 Nacos 与共享配置 🟡

- **现状**：各服务 `bootstrap.yml` 从 Nacos 拉取共享配置（`shared-spring/redis/logs.yaml`），本地启动前需自行准备 Nacos 与中间件环境
- **优化方向**：补充完整的本地环境搭建文档 / docker-compose 一键拉起中间件

### 2. 数据库脚本分散在各服务 🟡

- **现状**：建表结构散落在各服务 `domain/po`，无统一 SQL 管理目录，初始化成本偏高
- **优化方向**：收集整理统一 SQL 脚本并纳入版本管理

### 3. 事务一致性依赖 Seata 配置 🟡

- **现状**：依赖管理已引入 Seata 1.5.1，但具体业务场景（如订单-支付-营销联动）需按需开启全局事务
- **优化方向**：梳理跨服务写链路，明确 Seata AT/TCC 接入范围

## 🤝 贡献指南

欢迎提交 Pull Request 或 Issue！

## 🙏 文档和资源

- [Spring Cloud Alibaba](https://github.com/alibaba/spring-cloud-alibaba) - 微服务框架
- [Nacos](https://nacos.io/) - 注册/配置中心
- [MyBatis-Plus](https://github.com/baomidou/mybatis-plus) - ORM 框架
- [Knife4j](https://doc.xiaominfo.com/) - 接口文档聚合
- [xxl-job](https://github.com/xuxueli/xxl-job) - 分布式任务调度
- [Redisson](https://github.com/redisson/redisson) - Redis 客户端与分布式锁
- [Hutool](https://hutool.cn/) - Java 工具库
