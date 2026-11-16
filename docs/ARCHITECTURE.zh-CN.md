# 员工管理 —— 微服务参考系统

三个可独立部署的 Spring Boot 服务，最初是一个单体 REST API，
通过**渐进式**拆分演进而来：一个员工 API、一个入职（onboarding）消费者，
以及位于两者之前的 Spring Cloud Gateway，并配有 Micrometer/Zipkin 链路追踪，
可以把一次请求跨越 HTTP 与 AMQP 边界串起来。

本项目演示真实后端服务中真正重要的实践：
**事件驱动的服务边界**、**API 网关**、**分布式链路追踪**、
清晰的分层、**全局异常处理**、**严格的请求校验**、
正确的 **HTTP 状态码**、**无状态 JWT 安全**、
**带重试与幂等性的第三方集成韧性**、**分页动态搜索**、
**带显式失效的 Redis 读缓存**、**通过 RabbitMQ 实现的事件驱动解耦**、
**自描述 OpenAPI 文档**、**correlation-id 追踪与指标**、
**有界异步执行**、**自动化测试**、三个**容器镜像**，
以及针对经典 **JPA N+1 查询问题**的、经过度量的修复。

> 作者：**wangqi**

---

## 技术栈

| 层次             | 技术                                                 |
| ---------------- | ---------------------------------------------------- |
| 语言             | Java 17                                              |
| 框架             | Spring Boot 3.3.5, Spring Web (MVC)                  |
| 网关             | Spring Cloud Gateway 4.1.x, Spring Cloud 2023.0.3    |
| 服务拆分         | 4 个 Maven 模块：`contracts`、`employee-management-api`、`onboarding-service`、`api-gateway` |
| 持久化           | Spring Data JPA / Hibernate 6.5                      |
| 数据库           | H2（内存数据库，每个服务一个实例）                   |
| 缓存             | Redis 7 + Spring Cache（`@Cacheable`、`@CacheEvict`）|
| 消息             | RabbitMQ 3.13 + Spring AMQP，publisher confirms、死信队列、一个消费者服务 |
| 链路追踪         | Micrometer Tracing 1.3（Brave bridge）、Zipkin 3     |
| 校验             | Jakarta Bean Validation（Hibernate Validator）       |
| 安全             | Spring Security 6、JWT（JJWT 0.12）、BCrypt          |
| 集成             | 基于 JDK `HttpClient` 的 Spring `RestClient`，带指数退避的有界重试 |
| API 文档         | springdoc-openapi 2.6（Swagger UI）                  |
| 可观测性         | Spring Boot Actuator、Micrometer、Prometheus         |
| 异步             | Spring `@Async` + 有界 `ThreadPoolTaskExecutor`      |
| 打包             | Docker（多阶段构建）、Docker Compose（8 个服务）    |
| 样板代码         | Lombok                                               |
| 测试             | JUnit 5、Mockito、MockMvc、AssertJ                   |
| 构建             | Maven 3.9（根 reactor）                              |

---

## 核心特性

- **渐进式微服务拆分** —— 入职消费者从员工 API 中抽取为独立服务，
  而写入路径没有改变：生产者仍然向同一个 exchange 发布同一个事件。
  只共享消息契约，且通过独立的 `contracts` 模块共享，其类名通过 AMQP
  `__TypeId__` 头传递。拓扑归属随消费者一起迁移（队列、绑定、DLQ），
  因此生产者不再需要知道谁在监听。
- **API 网关** —— `:8000` 上的 Spring Cloud Gateway 是唯一的公开入口。
  它把 `/api/auth`、`/api/employees` 和 `/api/departments` 路由到员工 API，
  把 `/api/onboarding` 路由到入职服务。认证保持在下游：网关无法扩大调用者的权限。
- **分布式链路追踪** —— 每个请求在网关处生成一个 trace；trace 上下文通过 HTTP
  以及 AMQP 转发（`spring.rabbitmq.template.observation-enabled` /
  `listener.simple.observation-enabled`），因此网关、API 和消费者记录的是
  **同一个** `traceId`。Span 导出到 `:9411` 上的 Zipkin。
- **全局异常处理** —— 单个 `@RestControllerAdvice` 把领域异常翻译成有意义的
  HTTP 响应，并把非法 JSON、类型不匹配、缺失参数、不支持的请求方法与媒体类型
  映射为对应的 `4xx`。未预期的错误只记录堆栈，对外返回仅带关联 id 的通用 `500`，
  内部细节绝不外泄。
- **统一响应信封** —— 无论成功还是失败，所有响应都是 `Result<T>`：
  `{code, message, data, path, requestId, fieldErrors, timestamp}`。
  前端只需一个解析器和一个 Axios 拦截器，同时 `code` 与真实 HTTP 状态码一致，
  缓存、代理和监控不受影响。
- **严格的请求校验** —— DTO 上标注 `@NotBlank`、`@Email`、`@DecimalMin`、`@Size`，
  并通过 `@Valid` 校验；实体上保留同样的约束作为最后一道防线。校验失败时，
  API 返回结构化的 `400 Bad Request`，明确列出**哪些字段出错以及原因**。
  跨字段规则会拒绝下限高于上限的薪资区间。
- **标准 HTTP 状态码** —— 创建返回 `201 Created`（带 `Location` 头），
  读取成功返回 `200 OK`，删除返回 `200 OK`（`data` 为空），
  未知 id 返回 `404 Not Found`，重复邮箱返回 `409 Conflict`。
  响应信封只重复状态码，不替代它。
- **自动化测试** —— 服务层用 Mockito 做单元测试，Web 层用 `@WebMvcTest` + `MockMvc` 测试。
- **JPA 性能（消除 N+1）** —— 查询部门及其员工时，使用单个 `@EntityGraph`
  抓取，而不是每个部门额外发一次查询。**差异由测试断言，而不是口头声明**：
  修复前 4 条语句，修复后 1 条。
- **无状态 JWT 安全** —— 登录签发 HS256 token，请求过滤器负责校验，
  基于角色的规则对**缺少凭证返回 `401`**、对**角色不足返回 `403`**，
  并与其他所有失败共用同一套 `Result<T>` 响应信封。
- **韧性集成** —— 对第三方 HR 系统的出站调用在超时和 `5xx` 响应时按指数退避重试，
  但绝不对 `4xx` 重试；重试预算耗尽时返回 **`502 Bad Gateway`**，而不是原始的连接器异常。
- **幂等写入** —— `POST /api/employees` 接受 `Idempotency-Key` 头。
  相同 key + 相同 payload 会重放原始响应（`Idempotent-Replay: true`）
  且不会创建重复记录；相同 key + 不同 payload 返回 **`409 Conflict`**。
- **分页动态搜索** —— `GET /api/employees` 通过 JPA `Specification` 按姓名、邮箱、
  职位、部门和薪资区间过滤，返回稳定的 `PageResponse` 封装，限制页大小，
  并白名单化可排序字段，使错误输入变成干净的 `400` 而不是持久化异常。
- **Redis 读缓存** —— `GET /api/employees/{id}` 和 `GET /api/departments`
  由 Redis 提供，带按缓存划分的 TTL（10m / 5m）、命名空间化 key 和可读的 JSON 值，
  任何会改变缓存视图的写入都会使其失效。**经过度量，而非口头声明**：
  测试断言第二次读取发送 **零条** SQL；缓存命中/未命中计数器通过 Actuator 暴露。
- **自描述 API** —— springdoc 生成 OpenAPI 3 描述，`/swagger-ui.html` 上的
  Swagger UI 允许客户端用 JWT 认证并直接调用每个端点，无需写任何代码。
- **可观测性** —— 每个请求获得一个 correlation id（`X-Request-Id`），
  传播到 SLF4J MDC 和错误载荷中，并通过 Actuator 和 Prometheus 抓取端点暴露。
- **事件驱动解耦** —— 已提交的员工会向 RabbitMQ topic exchange 发送
  `employee.created` 消息；入职流程由 **onboarding 服务**启动，而不是请求线程。
  发布者不知道谁在监听，消费者是**幂等的**（已证明重复投递不会创建第二个任务），
  被拒绝的消息会落到**死信队列**，而不是被无限重投。
- **有界异步执行** —— 新员工通知运行在专用线程池上，显式设置了
  core/max/queue 限制、CallerRuns 拒绝策略和 MDC 传播，
  因此后台任务既不会饿死请求，也不会丢失 trace 上下文。
- **容器化** —— 多阶段 Dockerfile 使用完整 JDK 构建，交付带健康检查的
  非 root JRE 镜像；`docker compose up` 会同时启动网关、两个服务、MySQL、Redis、
  RabbitMQ 和 Zipkin，外加一个 Nginx 容器：它托管构建好的 SPA，并把 `/api` 转发给网关。

> **本项目证明了什么：** 我能在 SQL 层面找到性能问题，并用查询计数断言证明修复；
> 能添加无状态 JWT 认证并正确处理 `401`/`403` 语义；
> 能把有界指数退避与数据库幂等性结合，让第三方集成可以安全重试；
> 能把过滤下推到 SQL，并置于白名单化的排序接口之后；
> 能用 Redis 缓存把数据库移出热点读路径，并用测试断言其失效逻辑；
> 能用事件流解耦写入路径，供其他服务订阅；
> **在不改动生产者的前提下，把消费者抽取为独立可部署服务**；
> 把两个服务放到网关之后，并**在 gateway → HTTP → 队列 → consumer 之间保持同一个 trace id**；
> 同时仍能通过 correlation id、指标、有界异步池、带健康检查的容器和不变的错误契约来运维。

| 声明 | 本仓库中的证据 |
| ---- | -------------- |
| N+1 查询确实消失了 | `DepartmentQueryCountTest` —— 修复前 **4** 条语句，修复后 **1** 条 |
| 缓存确实减少了数据库工作 | `CacheBehaviorIntegrationTest` —— 首次读取 **1** 条语句，第二次 **0** 条；创建/删除时断言失效 |
| 重试不会产生重复 | `IdempotencyIntegrationTest` —— 重放带 `Idempotent-Replay: true`，key 复用返回 `409` |
| 安全能区分未认证与未授权 | `SecurityIntegrationTest` —— 无 token 为 `401`，角色错误为 `403` |
| 每种失败都是结构化契约 | `EmployeeControllerTest` —— 字段级 `400` 响应体；correlation id 在 `CorrelationIdFilterTest` 中断言 |
| 拆分后的契约确实共享 | `EmployeeCreatedMessageTest` —— JSON 往返以及作为 `__TypeId__` 发送的稳定 FQCN |
| 消费者确实拥有其拓扑 | `RabbitMqTopologyTest` —— 持久队列、DLQ 参数、绑定、JSON 往返 |
| 抽取后的服务确实能验证签发者的 token | `OnboardingApiTest` —— 匿名 `401`，用员工 API secret 签发的 HS256 token 为 `200`；`JwtServiceTest` 拒绝被篡改/过期的 token |
| 网关确实暴露了两个服务 | `GatewayRoutingTest` —— 路由 id、路径断言和目标 URI |
| 异步任务不会饿死或丢失 trace | `AsyncConfigTest` —— 线程池边界加 MDC 传播 |
| 重投的消息不会重复处理 | `OnboardingInitializerTest` —— 第二次投递返回 `false`，表中一行 |
| broker 拓扑与 README 描述一致 | `MessagingConfigTest` —— 生产者 exchange 持久化；队列/DLQ 从消费者侧断言 |
| 以上全部通过 | **68 个测试**，在仓库根目录运行 `mvn test` |

---

## 架构：把单体演进为服务

原始 API 已经会向 RabbitMQ 发布 `employee.created`，但消费者生活在同一进程内。
这让抽取变得**在设计上**非常廉价：边界本来就是事件，而不是方法调用。

```
                         :8000  api-gateway (Spring Cloud Gateway)
                                   │
          ┌────────────────────────┴─────────────────────────┐
          ▼                                                  ▼
 :8080 employee-management-api                     :8081 onboarding-service
 employees / departments / auth                     GET /api/onboarding/tasks
 Redis cache, JWT issue, External HR, JPA           own H2, JWT validate, consumer
          │                                                  ▲
          │ publish employee.created (after commit)          │ consume
          └──────────► RabbitMQ exchange employee.events ─────┘
                          queue employee.created.onboarding (+ DLQ)

 All three services ──► Zipkin :9411 (spans); traceId is also in every log line
```

| 边界决策 | 原因 |
| -------- | ---- |
| 按**事件**拆分，而非按实体拆分 | 消费者只需要事件已携带的快照（`employeeId`、姓名、邮箱）；无需同步回调 API。 |
| 共享**契约**，而非代码 | 生产者和消费者依赖 `contracts`。`Jackson2JsonMessageConverter` 把类名放入 `__TypeId__`；若在不同包下各有一份拷贝，反序列化会失败。 |
| 拓扑属于**消费者** | onboarding 服务拥有队列、绑定和 DLQ。生产者只声明 exchange。因此抽取消费者**没有改变任何生产者逻辑**——只是重新指向了共享 DTO 的 import。 |
| 每个服务有**自己的数据库** | onboarding 服务从不读取员工表。事件就是数据传输边界。 |
| 在**下游**认证，而非在网关 | 两个服务校验同一个 HS256 token。一个只信任自己的网关不是安全边界。 |

**抽取实际上改变了生产者里的什么：** 留下的四个消息类
（`EmployeeEventRelay`、`EmployeeEventPublisher`、`AmqpEmployeeEventPublisher`、
`LocalEmployeeEventPublisher`）只是把 `EmployeeCreatedMessage` 的 import 重新指向。
消费者、其实体、仓库和幂等逻辑原样迁移到了 `onboarding-service`。

**有意为之的取舍，明说而非掩盖：**

- HS256 密钥通过 `JWT_SECRET` 共享。生产环境会使用非对称密钥和 JWKS 端点，
  只有签发方持有私钥。
- H2 仍是内存数据库，且每个服务一份。真实部署需要每服务独立数据库；
  onboarding 在事件中持有一份员工**快照**。
- 没有服务发现：Compose 服务名（或 localhost）是显式配置的。
  不引入 Eureka/Consul，是因为它只会增加基础设施，却无法展示新的后端技能。
- 若 API 在消费者队列绑定前发布消息，RabbitMQ 会丢弃该消息
  （exchange 存在，但绑定不存在）。Compose 会先启动消费者；生产环境应预声明拓扑。
- commit→publish 之间的窗口仍然只靠一份文档化的 outbox 提案来收口，尚未实现。

---

## JPA 性能：修复 N+1 问题

`Department` → `Employee` 是懒加载的一对多关联。序列化部门列表的朴素做法是：
先加载部门，然后逐个访问 `getEmployees()`：

```java
List<Department> departments = departmentRepository.findAll();   // 1 次查询
departments.forEach(d -> d.getEmployees().size());               // 每个部门 +1 次查询
```

3 个部门、每个 5 名员工时，Hibernate 会向数据库发送 **4 条语句**。
增加到 50 个部门就是 51 条语句——经典的 N+1。

仓库暴露了一个实体图查询，在一次往返中抓取整个聚合：

```java
@EntityGraph(attributePaths = "employees")
@Query("select distinct d from Department d")
List<Department> findAllWithEmployees();
```

| 访问路径                                     | 执行语句数 | 结果         |
| -------------------------------------------- | ---------- | ------------ |
| `findAll()` + 懒加载 `getEmployees()`        | **4**      | N+1 问题     |
| `findAllWithEmployees()` 配合 `@EntityGraph` | **1**      | 已修复       |

两个数字都由 `DepartmentQueryCountTest` 度量，它注册了 Hibernate
`StatementInspector` 并断言确切的语句数。若有人移除实体图或重新引入懒加载遍历，
测试就会失败。

---

## 搜索：分页、动态过滤与安全排序

```
GET /api/employees?jobTitle=engineer&minSalary=5000&page=0&size=20&sort=salary,desc
```

| 参数           | 默认值  | 规则                                                        |
| -------------- | ------- | ----------------------------------------------------------- |
| `firstName`    | —       | 不区分大小写的包含匹配                                      |
| `lastName`     | —       | 不区分大小写的包含匹配                                      |
| `email`        | —       | 不区分大小写的包含匹配                                      |
| `jobTitle`     | —       | 不区分大小写的包含匹配                                      |
| `departmentId` | —       | 精确匹配                                                    |
| `minSalary`    | —       | 包含下界                                                    |
| `maxSalary`    | —       | 包含上界                                                    |
| `page`         | `0`     | 从 0 开始；负数 → `400`                                     |
| `size`         | `20`    | `1..100`；超过上限 → `400`                                  |
| `sort`         | `id,asc`| `field` 或 `field,asc\|desc`，可重复                         |

```json
{
  "content": [
    {
      "id": 1,
      "firstName": "Alice",
      "lastName": "Chen",
      "email": "alice@example.com",
      "jobTitle": "Senior Backend Engineer",
      "salary": 9000.00,
      "createdAt": "2024-01-01T10:15:30Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

实现说明：

- 过滤条件通过 `Specification`（`EmployeeSpecifications`）编译进单条 SQL 语句。
  不会把数据加载到内存再过滤，缺失的过滤条件根本不会贡献任何谓词。
- 响应是**稳定的 `PageResponse` 封装**，而不是 Spring 内部的 `Page` 序列化结果——后者
  在不同版本间形状会变，不应作为交给客户端的契约。
- `size` 上限为 100；没有上限时，一个请求就能要求服务器物化整张表。
- 排序字段来自**白名单**（`id`、`firstName`、`lastName`、`email`、
  `jobTitle`、`salary`、`createdAt`）。未知字段或方向会返回 `400`
  并列出允许值，因此不可信输入永远到不了 JPA 属性路径。
- `sort=salary,desc` 从原始请求参数读取——否则 Spring 的逗号感知转换器
  会把它拆成两个独立的值。

由 `EmployeeSearchIntegrationTest`（分页、排序、组合过滤、拒绝排序字段、拒绝页大小）
和针对服务映射的 Mockito 单元测试覆盖。

---

## 缓存：热点读路径上的 Redis

有两个端点在几乎每次页面加载时都会被命中，因此被缓存：

| 缓存          | Key                            | TTL  | 被谁失效 |
| ------------- | ------------------------------ | ---- | -------- |
| `employees`   | 员工 id                        | 10m  | `DELETE /api/employees/{id}` |
| `departments` | 一个条目（`SimpleKey.EMPTY`）  | 5m   | `POST /api/departments`、`POST /api/employees`、`DELETE /api/employees/{id}` |

部门列表内嵌了其员工，因此员工写入也会使其失效——缓存并不了解 join，
假装它了解就是脏数据到达用户的方式。

```java
@Cacheable(cacheNames = CacheConfig.EMPLOYEES, key = "#id")
@Transactional(readOnly = true)
public EmployeeResponse getEmployeeById(Long id) { ... }

@Caching(evict = {
        @CacheEvict(cacheNames = CacheConfig.EMPLOYEES, key = "#id"),
        @CacheEvict(cacheNames = CacheConfig.DEPARTMENTS, allEntries = true)
})
public void deleteEmployee(Long id) { ... }
```

设计决策：

- **按缓存设置 TTL** —— 漏掉的失效会在几分钟内自愈，而不是永久错误。
- **Key 带命名空间**（`employee-api:employees::1`），因此多个服务可以共享一个
  Redis 实例而不冲突，运维人员也能看出某个 key 属于哪个应用。
- **值是 JSON，而不是 JDK 序列化字节** —— `redis-cli GET` 返回可读载荷。
  record 是 `final`，因此序列化器配置了 `DefaultTyping.EVERYTHING`；
  否则缓存的 record 会反序列化成 `LinkedHashMap`，并在**第二次**请求时失败。
  同理，部门列表被物化为 `ArrayList`（`Stream.toList()` 返回 JDK 内部不可变类，
  无法被重建）。
- **`null` 从不缓存**，因此未知 id 会持续返回真实的 `404`，而不是缓存的“未找到”。
- **刻意不缓存** —— 用户查询（权限必须每个请求重新加载，见安全说明）
  和搜索结果（key 空间无界且命中率低）。
- **启用统计** —— 命中/未命中计数器通过
  `GET /actuator/metrics/cache.gets?tag=cache:employees&tag=result:hit` 暴露，
  因此缓存调优基于度量而非直觉。

**验证。** `CacheBehaviorIntegrationTest` 通过 Hibernate `StatementInspector`
统计 SQL 语句，断言**首次读取 1 条、第二次 0 条**，以及在删除和创建时的失效。
已对运行中的 Redis 实例确认：

```
127.0.0.1:6379> TTL employee-api:employees::2
(integer) 599
127.0.0.1:6379> GET employee-api:employees::2
"{\"@class\":\"com.wangqi.employee.dto.EmployeeResponse\",\"id\":[\"java.lang.Long\",2],
 \"firstName\":\"Bob\", ... \"createdAt\":[\"java.time.Instant\",\"2026-10-23T02:43:06.233618Z\"]}"
```

配置（`application.yml`，可被环境变量覆盖）：

| 属性                       | 默认值      | 环境变量             |
| -------------------------- | ----------- | -------------------- |
| `spring.data.redis.host`   | `127.0.0.1` | `REDIS_HOST`         |
| `spring.data.redis.port`   | `6379`      | `REDIS_PORT`         |
| `spring.data.redis.password` | `wangqi`  | `REDIS_PASSWORD`     |
| `app.cache.provider`       | `redis`     | `CACHE_PROVIDER`     |
| `app.cache.employee-ttl`   | `10m`       | —                    |
| `app.cache.department-ttl` | `5m`        | —                    |

测试设置 `app.cache.provider=simple` 和 `spring.cache.type=simple`
（见 `src/test/resources/application.properties`），因此 `mvn test`
无需 Redis 服务器即可运行，同时仍会执行相同的 `@Cacheable` / `@CacheEvict` 注解。

---

## 消息：解耦员工入职流程

`POST /api/employees` 提交数据行后，发出 `employee.created` 事件。
入职流程不由请求线程启动，而且自抽取后也不在同一进程内启动：
`onboarding-service` 拥有该消费者。任何其他服务都可以订阅同一事件，
而 API 无需改动。

```
POST /api/employees                                employee-management-api
        │  transaction commits
        ▼
@TransactionalEventListener(AFTER_COMMIT)          EmployeeEventRelay
        │
        ▼
RabbitTemplate.convertAndSend("employee.events", "employee.created", json)
        │  (+ trace context in the message headers)
        ▼
topic exchange ──► employee.created.onboarding ──► EmployeeCreatedConsumer
   (producer &          (owned by the                    │   onboarding-service
    consumer declare)    onboarding service)              ▼
                                              OnboardingInitializer (idempotent)
                                                          │
                                      failure ──► employee.created.onboarding.dlq
```

| 元素 | 为什么这样设计 |
| ---- | -------------- |
| `AFTER_COMMIT` | 消息绝不能宣告一个事务最终回滚的员工。 |
| Topic exchange | 路由 key 让新的消费者（`employee.created.mail`、`.provisioning`）稍后绑定，而无需改动发布者。 |
| 生产者拥有 exchange，消费者拥有队列 | 生产者只需要一个发布的地方。队列、绑定和 DLQ 随消费者迁移，这也是抽取无需改动生产者逻辑的原因。 |
| 持久队列 | 事件能经受 broker 重启；内存队列会静默丢弃它。 |
| 死信队列 | 被消费者拒绝的消息会被停放以供检查，而不是无限重投（`default-requeue-rejected: false`）。 |
| Publisher confirms | broker 确认收到；nack 会被记录，而不是假定成功。 |
| 幂等消费者 | broker 是**至少一次**投递。`OnboardingInitializer` 在写入前检查 `employee_id`，并用唯一约束在数据库层强制——第二次投递是空操作，而不是第二个入职任务。 |
| 共享契约 | `com.wangqi.contracts.messaging.EmployeeCreatedMessage` 是唯一共享的 Java 类型。其 FQCN 作为 `__TypeId__` 发送，因此拷贝的类会破坏反序列化。 |
| 失败隔离 | 若提交后发布失败，relay 记录日志并返回：员工已存在，`500` 描述的是调用者无法修复的问题。 |

**有意为之的取舍（诚实的局限）：** 发布发生在提交之后但在事务之外，
因此该窗口内崩溃会丢失事件。生产级修复方案是**事务性 outbox**
（在同一事务中写入事件，之后再 relay）或 CDC。这里未实现，
因为它会增加一张表和轮询器，却不会带来关于 broker 的新知识。

**无 broker 的验证** —— 两个服务的测试套件都在
`app.messaging.enabled=false` 下运行：员工 API 换成
`LocalEmployeeEventPublisher`，onboarding 服务跳过其监听容器和拓扑声明。

| 测试（模块） | 证明什么 |
| ------------ | -------- |
| `MessagingConfigTest`（api） | 生产者侧持久 topic exchange 和 JSON `__TypeId__` 头 |
| `EmployeeEventRelayTest`（api） | 领域事件被映射为扁平契约；broker 故障不会使请求失败 |
| `EmployeeCreatedMessageTest`（contracts） | 共享 JSON 形状和稳定的 FQCN |
| `RabbitMqTopologyTest`（onboarding） | 队列持久性、DLQ 参数、绑定和 JSON 往返 |
| `OnboardingInitializerTest`（onboarding） | 首次投递创建任务，重投被忽略，表中只有一行 |

要端到端观察，运行 `docker compose up` 并通过网关创建一名员工。
在 `http://localhost:15672` 的管理 UI 中检查队列计数器；API 日志显示
`Published employee.created for employee 1`，**onboarding** 服务日志显示
`Onboarding initialised for employee 1`，两者带同一个 `traceId`。

配置：

| 属性 | 默认值 | 环境变量 |
| ---- | ------ | -------- |
| `spring.rabbitmq.host` | `127.0.0.1` | `RABBITMQ_HOST` |
| `spring.rabbitmq.port` | `5672` | `RABBITMQ_PORT` |
| `spring.rabbitmq.username` / `password` | `wangqi` / `wangqi` | `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` |
| `app.messaging.enabled` | `true` | `MESSAGING_ENABLED` |
| `app.messaging.exchange` / `routing-key` | `employee.events` / `employee.created` | — |
| `app.messaging.queue` / `dead-letter-queue` | `employee.created.onboarding` / `employee.created.onboarding.dlq` | 仅 onboarding 服务 |

---

## 安全：无状态 JWT 认证

`POST /api/auth/login` 用凭证换取签名的 HS256 token。后续每个请求以
`Authorization: Bearer <token>` 发送；不涉及 session 或 cookie，
因此服务可以水平扩展而不需要共享状态。

网关原样转发该头。员工 API 依据用户表校验 token；onboarding 服务校验同一签名
并读取 `role` claim，**没有用户表，也没有数据库调用**，因此抽取后的服务可以独立作答。

| 情形 | 响应 |
| ---- | ---- |
| 无 token、格式错误或过期 token | `401 Unauthorized` |
| token 有效但角色不足 | `403 Forbidden` |
| 登录时用户名或密码错误 | `401 Unauthorized`（通用消息） |

访问规则：

| 操作 | 所需角色 |
| ---- | -------- |
| `POST /api/auth/login` | 公开 |
| `GET /api/employees/{id}` | 任意已认证用户 |
| `GET /api/departments` | 任意已认证用户 |
| `POST /api/employees` | `ADMIN` |
| `POST /api/departments` | `ADMIN` |
| `DELETE /api/employees/{id}` | `ADMIN` |

实现说明：

- 密码以 **BCrypt** 哈希存储。登录失败消息刻意保持通用，以免泄露用户名是否存在。
- 权限**每个请求都从数据库重新加载**，因此禁用或降级账号会立即生效，
  即使其 token 尚未过期。
- 格式错误或被篡改的 token 被视为未认证并返回 `401`，绝不能是 `500`。
- `401` 和 `403` 复用共享的 `Result<T>` 信封，因此安全错误看起来
  与校验错误、领域错误完全一致。
- 签名密钥来自 `JWT_SECRET`；`application.yml` 中的值仅是开发默认值。
  员工 API 和 onboarding 服务必须使用相同的值（Compose 已如此处理），
  否则抽取后的服务会拒绝所有 token。生产环境会用非对称密钥和 JWKS 端点
  替换共享 HS256 secret。

演示账号（启动时写入；用 `app.demo-users.enabled=false` 禁用）：

| 用户名   | 密码        | 角色    |
| -------- | ----------- | ------- |
| `admin`  | `admin123`  | `ADMIN` |
| `viewer` | `viewer123` | `USER`  |

---

## 集成：重试、超时与幂等性

`POST /api/employees/{id}/sync` 把员工推送到外部 HR 系统
（`app.external-hr.base-url`）。第三方系统会以调用者无法控制的方式失败，
因此该客户端被设计为能挺过瞬时故障，并让重试变得安全。

### 重试策略

| 上游结果                            | 行为                                                    |
| ----------------------------------- | ------------------------------------------------------- |
| `4xx`                               | **不重试** —— 请求本身有错；快速失败并返回 `502`        |
| `5xx`                               | 按指数退避重试                                          |
| 连接被拒 / 读取超时                 | 按指数退避重试                                          |
| 重试耗尽                            | `502 Bad Gateway`，单个 `Result` 响应体                 |

每次尝试都由连接和读取超时（默认 2s）约束，因此挂起的上游无法永久占住 Tomcat worker。
退避从 200 ms 开始，每次尝试翻倍（默认 `max-attempts: 3`），
避免对正在恢复的依赖反复冲击：

```
attempt 1 --fail--> wait 200ms
attempt 2 --fail--> wait 400ms
attempt 3 --fail--> 502 Bad Gateway
```

该循环是在 Spring `RestClient` 之上的几行普通 Java——没有 Spring Retry，
没有 Resilience4j，没有额外运行时依赖。实际尝试次数会返回给调用者：

```json
{ "employeeId": 1, "attempts": 2 }
```

### 幂等写入

网络重试可能把同一个 `POST` 投递两次。没有保护时，第二次到达会创建第二名员工。
因此 `POST /api/employees` 接受可选的 `Idempotency-Key` 头：

| 场景                                        | 响应                                                    |
| ------------------------------------------- | ------------------------------------------------------- |
| 首次请求，使用新 key                        | `201 Created`，记录已存储                               |
| 相同 key + 相同 payload                     | `201 Created`，**重放原始响应体**，`Idempotent-Replay: true` |
| 相同 key + 不同 payload                     | `409 Conflict`                                          |
| 无 key                                      | 正常行为，不记录任何内容                                |
| 相同 key 的两个并发请求                     | 一个获胜；失败方得到 `409 Conflict`，可重试             |

工作原理：

- key 是 `idempotency_records` 表的**主键**，因此唯一性由数据库强制，
  而不是应用层锁。
- 同时存储规范化请求 JSON 的 **SHA-256 指纹**。因此 key 复用但 payload 不同
  可以被检测并拒绝，而不是静默返回别人的结果。
- 记录与员工**在同一事务内**写入，因此不存在员工已存在但没有幂等记录的窗口
  （反之亦然）。
- 重放与原始响应逐字节一致，因此客户端可以安全重试超时和连接重置。

由 `ExternalHrClientTest`（重试直到成功、`4xx` 不重试、预算耗尽后放弃）
和 `IdempotencyIntegrationTest`（重放、payload 不匹配时 `409`、无重复行）覆盖。

---

## 可观测性：Correlation ID、分布式追踪、健康检查与指标

每个请求都携带 correlation id。如果调用者提供了安全的 `X-Request-Id`
（1–64 个 `A-Z a-z 0-9 . _ -` 字符），则复用它，这样 API 网关的日志能与我们的对齐；
否则生成 UUID。该 id 会：

- 回显在 `X-Request-Id` 响应头中，
- 放入 SLF4J MDC 的 `requestId`，由日志 pattern 渲染，
- 作为 `requestId` 包含在每个响应体中，
- 复制到异步工作线程（见下一节），
- 在 `finally` 块中移除，使池化线程永远不会泄漏另一个请求的 id。

### 分布式链路追踪

correlation id 把一个服务的日志行串起来；**trace id** 则把整个请求跨服务串起来。
三个应用都包含 Micrometer Tracing（Brave bridge）和 Zipkin reporter：

| 跳转 | 机制 |
| ---- | ---- |
| client → gateway | 网关启动一个 span 并接受传入的 `traceparent` |
| gateway → employee API / onboarding service | 由追踪 HTTP 埋点注入的 W3C `traceparent` 头 |
| employee API → RabbitMQ → onboarding consumer | AMQP observation：生产者把 trace 上下文注入消息头，`spring.rabbitmq.listener.simple.observation-enabled` 在消费者侧继续它 |
| everywhere → Zipkin | span 发送到 `${ZIPKIN_ENDPOINT}`（默认 `http://localhost:9411/api/v2/spans`） |

每一行日志都渲染两个 id，因此一次 grep 就能证明传播：

```
2026-10-23 10:14:02.118 INFO  [trace=7f3a9c1e5d2b4a17 request=3b0f...] c.w.e.messaging.AmqpEmployeeEventPublisher - Published employee.created for employee 42 ...
2026-10-23 10:14:02.341 INFO  [trace=7f3a9c1e5d2b4a17] c.w.o.messaging.EmployeeCreatedConsumer - Onboarding initialised for employee 42 <alice@example.com>
```

`management.tracing.sampling.probability=1.0` 在本演示中追踪每个请求；
生产服务会采样很小比例。若 Zipkin 未运行，reporter 会安静失败，
而 trace id 仍在日志中，因此追踪可以离线演示。

| 端点                       | 用途                                           |
| -------------------------- | ---------------------------------------------- |
| `/actuator/health`         | 带组件详情的存活/就绪检查                      |
| `/actuator/metrics`        | Micrometer 指标名称和值                        |
| `/actuator/metrics/http.server.requests` | 按 URI、方法、状态统计的延迟和次数 |
| `/actuator/prometheus`     | Prometheus 抓取格式                            |
| `/actuator/info`           | 构建/应用元数据                                |
| `http://localhost:9411`    | Zipkin UI：按 trace id 搜索、查看 span         |

`http.server.requests` 由 Micrometer 自动收集，因此没有可能与现实脱节的手写计时代码。
在本演示中这些端点对评审者开放；真实部署中它们应位于网络策略或需认证的抓取之后。

---

## 异步：有界线程池而非默认线程池

创建员工会在事务内发布 `EmployeeCreatedEvent`。有两个监听器响应它：
一个在工作线程池上发送欢迎通知，另一个是 `EmployeeEventRelay`，把事件发布到 RabbitMQ
（见消息一节）。通知监听器是：

```java
@Async(AsyncConfig.NOTIFICATION_EXECUTOR)
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onEmployeeCreated(EmployeeCreatedEvent event) { ... }
```

两个监听器都在提交后运行，原因相同。

- **`AFTER_COMMIT`，而非 `@EventListener`。** 普通监听器在事务提交前触发，
  因此一个最终回滚的创建仍会为一个从未存在过的员工发送欢迎通知。
- **专用、有界的执行器。** 默认 `@Async` 执行器是无界的：
  负载下它会静默地为每个任务增长线程。通知池是显式的：

| 设置              | 值                  | 原因                                     |
| ----------------- | ------------------- | ---------------------------------------- |
| core / max 线程数 | 2 / 4               | 可预测的内存和 CPU                       |
| 队列容量          | 50                  | 有界积压                                 |
| 拒绝策略          | `CallerRunsPolicy`  | 用背压代替静默丢弃任务                   |
| 任务装饰器        | 复制 MDC map        | 异步日志保留来源 `requestId`             |
| 关闭              | 最多等待 10s        | 部署时进行中的通知能完成                 |

因此，慢通知路径无法饿死 HTTP 请求线程或任何其他异步任务。
`AsyncConfigTest` 断言线程池边界，并证明 correlation id 能挺过线程交接。

---

## Docker：一条命令，六个服务

```bash
docker compose up --build
# Gateway:   http://localhost:8000   <-- the only public entry point
# Swagger:   http://localhost:8000/swagger-ui.html
# Employee:  http://localhost:8080/actuator/health
# Onboard.:  http://localhost:8081/actuator/health
# Zipkin:    http://localhost:9411
# Redis:     localhost:6379 (password from REDIS_PASSWORD, default `wangqi`)
# RabbitMQ:  amqp://localhost:5672, UI at http://localhost:15672 (wangqi/wangqi)
```

三个应用镜像加三个基础设施容器：

| 服务                | 镜像 / Dockerfile                    | 端口 | 依赖（健康后启动）        |
| ------------------- | ------------------------------------ | ---- | ------------------------- |
| `api-gateway`       | `api-gateway/Dockerfile`             | 8000 | employee-api, onboarding  |
| `employee-api`      | `employee-management-api/Dockerfile` | 8080 | redis, rabbitmq           |
| `onboarding-service`| `onboarding-service/Dockerfile`      | 8081 | rabbitmq                  |
| `redis`             | `redis:7-alpine`                     | 6379 | —                         |
| `rabbitmq`          | `rabbitmq:3.13-management-alpine`    | 5672 / 15672 | —                |
| `zipkin`            | `openzipkin/zipkin:3`                | 9411 | —                         |

- **每模块多阶段构建** —— 构建阶段用 Maven + 完整 JDK，运行阶段仅用 JRE 的 Alpine。
  构建上下文是仓库根目录，以便模块解析同级模块；执行
  `mvn -pl <module> -am package` 前只复制 POM，使依赖层保持可缓存。
- **非 root** —— 每个应用容器都以非特权 `app` 用户运行。
- **健康检查** —— 每个镜像和 Compose 都探测 `/actuator/health`，
  因此编排看到的是“启动中”与“健康”，而不是猜。网关等待两个服务，
  服务等待其依赖，这消除了朴素 compose 文件隐藏的启动竞态。
- **通过环境变量配置** —— `JWT_SECRET` 由 API（签发方）、onboarding 服务（验证方）
  和网关测试共享；`EMPLOYEE_API_URI` 和 `ONBOARDING_SERVICE_URI` 是网关的路由目标；
  `ZIPKIN_ENDPOINT` 让每个服务指向 tracer；`EXTERNAL_HR_URL`、`REDIS_*` 和 `RABBITMQ_*`
  可在不重建镜像的情况下覆盖。
- **Redis 服务** —— 带密码保护和 AOF 文件的缓存，在 API 启动前就健康，
  因此首次缓存读取不会与缓存服务器竞态。
- **RabbitMQ 服务** —— 启用管理插件的 broker，带持久卷和
  `rabbitmq-diagnostics ping` 健康检查；`http://localhost:15672` 的 UI
  展示 exchange、onboarding 队列及其 DLQ。
- **Zipkin 服务** —— 上述共享 trace id 背后的 trace 存储；打开
  `http://localhost:9411`，按服务名搜索即可看到
  `gateway → employee-api → rabbit → onboarding-service` 的 span 树。

---

## API 端点

以下所有路径都通过 `http://localhost:8000` 的网关公开；所属服务的端口见最后一列。

| 方法     | 路径                  | 所属             | 请求体                                                   | 成功                | 错误                         |
| -------- | --------------------- | ---------------- | -------------------------------------------------------- | ------------------- | ---------------------------- |
| `POST`   | `/api/auth/login`     | employee-api     | JSON（`username`、`password`）                           | `200 OK`            | `400 Bad Request`、`401 Unauthorized` |
| `GET`    | `/api/employees/{id}` | employee-api     | —                                                        | `200 OK`            | `401 Unauthorized`、`404 Not Found` |
| `GET`    | `/api/employees`      | employee-api     | 查询参数（`firstName`、`lastName`、`email`、`jobTitle`、`departmentId`、`minSalary`、`maxSalary`、`page`、`size`、`sort`） | `200 OK` | `400 Bad Request`、`401 Unauthorized` |
| `POST`   | `/api/employees`      | employee-api     | JSON（`firstName`、`lastName`、`email`、`jobTitle`、`salary`，可选 `departmentId`）；可选 `Idempotency-Key` 头 | `201 Created`  | `400 Bad Request`、`401 Unauthorized`、`403 Forbidden`、`404 Not Found`、`409 Conflict` |
| `PUT`    | `/api/employees/{id}` | employee-api     | JSON（`firstName`、`lastName`、`email`、`jobTitle`、`salary`，可选 `departmentId`） | `200 OK` | `400 Bad Request`、`401 Unauthorized`、`403 Forbidden`、`404 Not Found`、`409 Conflict` |
| `POST`   | `/api/employees/{id}/sync` | employee-api | —（把员工推送到外部 HR 系统）                            | `200 OK`            | `401 Unauthorized`、`404 Not Found`、`502 Bad Gateway` |
| `DELETE` | `/api/employees/{id}` | employee-api     | —                                                        | `200 OK`（空信封）  | `401 Unauthorized`、`403 Forbidden`、`404 Not Found` |
| `POST`   | `/api/departments`    | employee-api     | JSON（`name`）                                           | `201 Created`       | `400 Bad Request`、`401 Unauthorized`、`403 Forbidden`、`409 Conflict` |
| `GET`    | `/api/departments`    | employee-api     | —                                                        | `200 OK`            | `401 Unauthorized`           |
| `GET`    | `/api/onboarding/tasks` | onboarding-service | —                                                     | `200 OK`（最新在前） | `401 Unauthorized`       |

除 `/api/auth/**` 外，`/api/**` 下的所有端点都需要 Bearer token。
onboarding 服务用共享 secret 验证**同一个** token；它从 claims 读取
`sub` 和 `role`，不触碰员工数据库，这就是 `employee-api` 签发的 token
可以原样通过网关工作的原因。

交互式文档位于 `http://localhost:8000/swagger-ui.html`（代理到员工 API）；
原始 OpenAPI 3 文档位于 `/v3/api-docs`。使用 **Authorize** 按钮并填入
`POST /api/auth/login` 得到的 token，即可直接在浏览器中调用受保护的端点。

### 响应信封

无论成功还是失败（包括由安全过滤器链直接返回的 `401`/`403`），
所有响应都是 `Result<T>`：

```json
{
  "code": 200,
  "message": "OK",
  "data": { },
  "path": "/api/employees",
  "requestId": "3b0f9c4e-8f7a-4c2b-9a1e-1f0b2c3d4e5f",
  "fieldErrors": { },
  "timestamp": "2024-01-01T10:15:30.123Z"
}
```

| 字段 | 出现场景 | 含义 |
| ---- | -------- | ---- |
| `code` | 始终 | 与 HTTP 状态码一致；状态行仍然是权威来源 |
| `message` | 始终 | 可直接展示给最终用户 |
| `data` | 有载荷的成功响应 | 业务响应体本身 |
| `path` | 错误响应 | 出错请求的 URI |
| `requestId` | 过滤器已运行时 | 与 `X-Request-Id` 响应头一致 |
| `fieldErrors` | 仅 `400` | 字段名 → 校验消息 |
| `timestamp` | 始终 | 服务端时间，ISO-8601 UTC |

空字段会被省略，因此成功响应体保持精简。删除接口返回 `200 OK` 且 `data` 缺省，
而不是 `204 No Content`：`204` 按定义没有响应体，前端不该为此多写一条分支。

### 登录 —— 请求与响应

```json
{
  "username": "admin",
  "password": "admin123"
}
```

```json
{
  "code": 200,
  "message": "Login successful.",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresInSeconds": 3600,
    "username": "admin"
  },
  "requestId": "3b0f9c4e-8f7a-4c2b-9a1e-1f0b2c3d4e5f",
  "timestamp": "2024-01-01T10:15:30.123Z"
}
```

### 创建员工 —— 请求

```json
{
  "firstName": "Qi",
  "lastName": "Wang",
  "email": "wangqi@example.com",
  "jobTitle": "Backend Engineer",
  "salary": 5000.00
}
```

### 创建员工 —— 成功响应（`201 Created`）

```json
{
  "code": 201,
  "message": "Created",
  "data": {
    "id": 1,
    "firstName": "Qi",
    "lastName": "Wang",
    "email": "wangqi@example.com",
    "jobTitle": "Backend Engineer",
    "salary": 5000.00,
    "createdAt": "2024-01-01T10:15:30Z"
  },
  "requestId": "3b0f9c4e-8f7a-4c2b-9a1e-1f0b2c3d4e5f",
  "timestamp": "2024-01-01T10:15:30.123Z"
}
```

`Location` 头指向 `/api/employees/1`。

### 校验失败响应（`400 Bad Request`）

```json
{
  "code": 400,
  "message": "Validation failed for one or more fields.",
  "path": "/api/employees",
  "requestId": "3b0f9c4e-8f7a-4c2b-9a1e-1f0b2c3d4e5f",
  "fieldErrors": {
    "email": "Email must be a well-formed email address",
    "firstName": "First name is required",
    "lastName": "Last name is required",
    "salary": "Salary is required"
  },
  "timestamp": "2024-01-01T10:15:30.123Z"
}
```

### 未找到响应（`404 Not Found`）

```json
{
  "code": 404,
  "message": "Employee not found with id: 99",
  "path": "/api/employees/99",
  "requestId": "3b0f9c4e-8f7a-4c2b-9a1e-1f0b2c3d4e5f",
  "timestamp": "2024-01-01T10:15:30.123Z"
}
```

### 其他已映射的失败场景

| 场景 | 状态码 | 信封内容 |
| ---- | ------ | -------- |
| 请求体不是合法 JSON，或字段类型错误 | `400` | `message` 说明违反的 schema |
| 路径变量类型错误（`/api/employees/abc`） | `400` | `message` 指出参数名 |
| 缺少必填查询参数 | `400` | `message` 指出参数名 |
| `size` 超过上限，或 `minSalary > maxSalary` | `400` | `fieldErrors` |
| 已存在的路径不支持该 HTTP 方法 | `405` | `message` 指出方法名 |
| `Content-Type` 缺失或不是 JSON | `415` | `message` 指出媒体类型 |
| 其他未预期异常 | `500` | 通用消息 + `requestId`；堆栈只留在日志 |

---

## 快速开始

### 前置条件

- JDK 17 或更高版本
- Maven 3.8+
- 本地运行 Redis 6+（默认密码 `wangqi`；用 `REDIS_PASSWORD` 覆盖）。
  若想完全跳过 Redis，用 `CACHE_PROVIDER=simple` 运行，即使用与测试相同的内存缓存。
- 本地运行 RabbitMQ 3.x（用户名/密码默认 `wangqi`；用
  `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` 覆盖）。若想不依赖 broker 运行，
  在两个服务上设置 `app.messaging.enabled=false`，事件会留在员工 API 进程内。
- Docker Desktop（可选，用于六容器工作流；它会帮你启动一切，包括 Zipkin）
- Zipkin（本地可选；没有它时 trace 仍会创建并记录，只是不导出）

### 构建 reactor

该仓库是四模块 Maven reactor。从根目录构建和测试：

```bash
mvn -DskipTests package   # builds contracts, employee-management-api,
                          # onboarding-service and api-gateway
mvn test                  # runs every module's tests (no external servers needed)
```

### 运行服务

每个服务都是独立的 Spring Boot 应用。在三个终端中：

```bash
# 1. Employee API (owner of /api/auth, /api/employees, /api/departments)
mvn -pl employee-management-api spring-boot:run            # http://localhost:8080

# 2. Onboarding service (owner of /api/onboarding, consumer of employee.created)
mvn -pl onboarding-service spring-boot:run                # http://localhost:8081

# 3. Gateway (the only entry point a client should use)
mvn -pl api-gateway spring-boot:run                       # http://localhost:8000
```

一切调用都走 `http://localhost:8000`，而不是服务端口——网关负责把
`/api/employees` 路由到 `:8080`、把 `/api/onboarding` 路由到 `:8081`。

在不带 broker 运行（`app.messaging.enabled=false`）时，Rabbit 健康指示器会随消息一起禁用，
因此 `/actuator/health` 仍报告 `UP`。在 Compose 中该指示器开启，
这就是容器只有在 RabbitMQ 响应后才变为健康的原因。

重试与超时行为可在员工 API 的 `application.yml` 中配置
（`app.external-hr.max-attempts`、`initial-backoff`、`timeout`、`base-url`）。
`JWT_SECRET` 由两个服务共享；`EXTERNAL_HR_URL`、`REDIS_HOST`、`REDIS_PORT`、
`REDIS_PASSWORD`、`RABBITMQ_HOST`、`RABBITMQ_PORT`、`RABBITMQ_USERNAME`、
`RABBITMQ_PASSWORD`、`ZIPKIN_ENDPOINT`、`EMPLOYEE_API_URI` 和
`ONBOARDING_SERVICE_URI` 可通过环境变量覆盖。

### 改用 MySQL 而不是 H2

默认数据源是内存 H2，每次重启都从空库开始。启用 `mysql` profile 即切换到真实数据库，
首次启动自动建表，重启后数据保留：

```bash
MYSQL_USERNAME=root MYSQL_PASSWORD=secret \
  mvn -pl employee-management-api spring-boot:run -Dspring-boot.run.profiles=mysql
```

`MYSQL_HOST`、`MYSQL_PORT`、`MYSQL_USERNAME`、`MYSQL_PASSWORD` 和
`MYSQL_DATABASE`（默认 `employee_management`）可覆盖默认值。若 3306 端口空闲，
可用一个临时容器：

```bash
docker run -d --name employee-mysql -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=secret -e MYSQL_DATABASE=employee_management mysql:8.0
```

### 用容器跑起全部服务

```bash
docker compose up --build -d
```

一条命令构建并启动 8 个容器，只对外暴露两个端口：

| 服务 | 端口 | 作用 |
| ---- | ---- | ---- |
| `web` | 80 | Nginx 托管构建后的 SPA，并把 `/api` 转发给网关 |
| `api-gateway` | 8000 | 唯一公开的 API 入口 |
| `employee-api` | 8080 | 员工、部门、认证（数据落在 MySQL） |
| `onboarding-service` | 8081 | 消费 `employee.created` |
| `mysql` | 3306 | 员工 API 的持久化存储 |
| `redis` | 6379 | 读缓存 |
| `rabbitmq` | 5672 / 15672 | 事件 broker 与管理界面 |
| `zipkin` | 9411 | 链路追踪收集与界面 |

打开 <http://localhost>，用 `admin` / `admin123` 登录。

不改文件也能覆盖默认值：

```bash
JWT_SECRET=... MYSQL_ROOT_PASSWORD=... docker compose up -d
```

只改了某个镜像时，单独重建即可：

```bash
docker compose up -d --build web          # 前端
docker compose up -d --build employee-api # 后端
```

### 运行测试

```bash
mvn test                                   # all modules
mvn -pl onboarding-service test            # one module
mvn -pl api-gateway test                   # route definitions only
```

### 动手试一试

```bash
# 1. Log in as the seeded admin through the gateway -> 200 OK, capture the token
TOKEN=$(curl -s -X POST http://localhost:8000/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r .data.accessToken)

# 2. Unauthenticated request -> 401 Unauthorized (JSON), produced downstream
curl http://localhost:8000/api/departments

# 3. Create a department -> 201 Created (routed to employee-api)
curl -X POST http://localhost:8000/api/departments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Engineering"}'

# 4. Create an employee -> 201 Created
curl -X POST http://localhost:8000/api/employees \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Qi","lastName":"Wang","email":"wangqi@example.com","jobTitle":"Backend Engineer","salary":5000,"departmentId":1}'

# 5. List departments with employees -> 200 OK, one SQL statement
curl http://localhost:8000/api/departments -H "Authorization: Bearer $TOKEN"

# 6. Invalid payload -> 400 Bad Request with field errors
curl -X POST http://localhost:8000/api/employees \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"firstName":"","lastName":"","email":"not-an-email"}'

# 7. Send the same create request twice with one Idempotency-Key -> 201 both times,
#    the second carries "Idempotent-Replay: true" and no second employee is created
curl -i -X POST http://localhost:8000/api/employees \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: 9b2f0c1e-4a7d-4f2a-9c31-8d5b6e0a1f22" \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Qi","lastName":"Wang","email":"idem@example.com","jobTitle":"Backend Engineer","salary":5000}'

# 8. Push the employee to the external HR system -> 502 after 3 attempts
#    (the default base URL http://localhost:1 is intentionally unreachable)
curl -i -X POST http://localhost:8000/api/employees/1/sync -H "Authorization: Bearer $TOKEN"

# 9. Paginated, filtered search -> 200 OK with a PageResponse envelope
curl -s "http://localhost:8000/api/employees?jobTitle=engineer&minSalary=5000&page=0&size=10&sort=salary,desc" \
  -H "Authorization: Bearer $TOKEN"

# 10. Unsupported sort field -> 400 Bad Request, listing the allowed fields
curl -s "http://localhost:8000/api/employees?sort=password,asc" -H "Authorization: Bearer $TOKEN"

# 11. Read the same employee twice, then inspect the cache
curl -s http://localhost:8000/api/employees/1 -H "Authorization: Bearer $TOKEN"
curl -s http://localhost:8000/api/employees/1 -H "Authorization: Bearer $TOKEN"
#    -> 1 miss, 1 hit; the second call sends no SQL at all
curl -s "http://localhost:8080/actuator/metrics/cache.gets?tag=cache:employees&tag=result:hit" \
  -H "Authorization: Bearer $TOKEN"
#    -> the key lives in Redis as readable JSON with a TTL
redis-cli -a wangqi TTL employee-api:employees::1

# 12. Create an employee and watch the event leave the API
#     employee-api log: "Published employee.created for employee N to exchange 'employee.events'"
#     onboarding-service log: "Onboarding initialised for employee N <...>"
#     Both lines carry the same trace=... as the gateway log for that request.
#     RabbitMQ UI (http://localhost:15672): messages delivered on
#     employee.created.onboarding, no messages resting in the DLQ.

# 13. Read the onboarding tasks created by the other service
curl -s http://localhost:8000/api/onboarding/tasks -H "Authorization: Bearer $TOKEN"
#     -> the same token is accepted by onboarding-service (shared secret, no DB lookup)

# 14. Inspect the trace end to end
open http://localhost:9411    # search by serviceName=api-gateway
```

用 `viewer` / `viewer123` 登录并重复第 3 步，可看到已认证但不具备 `ADMIN` 角色时
返回的 **403 Forbidden** 响应。

每个服务都有其内存 H2 控制台：
`http://localhost:8080/h2-console`（`jdbc:h2:mem:employeedb`，用户 `sa`，密码为空）
和 `http://localhost:8081/h2-console`（`jdbc:h2:mem:onboardingdb`，同样的凭证）。
服务 Actuator 可直接通过各自端口访问；网关只路由 `/api/**` 和 Swagger 路径，
从而把运维面留在公开入口之外。

---

## 前端：Vue 3 单页应用

`employee-management-web/` 是同一套 API 的 Vite + Vue 3 + TypeScript + Element Plus 客户端。
它通过 `/api` 前缀访问员工服务，并在一处统一拆解 `Result<T>` 信封。

- **认证：** 登录页调用 `POST /api/auth/login`，把 JWT 存入 `localStorage`；
  请求拦截器在每次调用时附带 Bearer token。收到 `401` 会清除会话并跳转
  `/login`，同时保留原始路径，登录后回到原页面。
- **布局：** 可折叠侧边栏、面包屑、账户菜单与退出确认。
- **员工管理：** 服务端过滤（名、姓、邮箱、部门）、分页与排序，外加新增/编辑
  弹窗和删除确认；前端校验规则与后端约束保持一致。
- **数据面板：** 员工总数等指标卡片，以及基于 ECharts 的部门分布环形图，
  数据来自 `GET /api/employees`（`totalElements`）与 `GET /api/departments`。
- **错误处理：** 单个响应拦截器负责拆信封、展示字段级错误，并把
  `401` / `403` / `500` 统一为一条可读提示，页面无需再判断状态码。
- **界面级角色控制：** 登录响应会带回已授予的角色，于是路由守卫（`meta.roles`）、
  基于路由表的菜单过滤和 `v-permission` 指令可以隐藏当前账号无权使用的页面与写操作。
  `viewer` 看到的是同一份员工目录，但没有新增、编辑、删除入口。
  服务端仍然对每次调用做鉴权：隐藏按钮只是体验，`403` 才是契约。
- **CORS：** `SecurityConfig` 只对 `app.cors.allowed-origins`（`CORS_ALLOWED_ORIGINS`）
  列出的来源应答预检请求。开发与生产都由代理转发 `/api`，浏览器始终同源，
  该列表只对“浏览器直连 API”的部署方式有意义。

```bash
cd employee-management-web
npm install
npm run dev      # http://localhost:5173，/api 代理到 http://localhost:8080
npm run build    # 先执行 vue-tsc 类型检查，再输出静态资源到 dist/
```

请先启动员工 API：开发服务器把 `/api` 代理到 8080 端口，浏览器始终同源，
因此无需配置 CORS。演示账号：`admin / admin123`（ADMIN）与
`viewer / viewer123`（USER，只读）。

> 第二阶段同时补充了 `PUT /api/employees/{id}`：编辑弹窗需要它，
> 而此前 API 只提供创建、读取、搜索和删除。

---

## 项目结构

```
employee-management/                      # Maven reactor (packaging: pom)
├── pom.xml                               # Aggregator: contracts, api, onboarding, gateway
├── docker-compose.yml                    # Gateway + 2 services + Redis + RabbitMQ + Zipkin
├── .dockerignore
├── contracts/                            # Shared wire contract (the only shared code)
│   └── com/wangqi/contracts/messaging/
│       └── EmployeeCreatedMessage.java   # FQCN travels in the AMQP __TypeId__ header
├── employee-management-api/              # Service 1: system of record for employees
│   └── src/main/java/com/wangqi/employee
│       ├── EmployeeManagementApplication.java
│       ├── config/
│       │   ├── AsyncConfig.java           # Bounded notification pool + MDC decorator
│       │   ├── CacheConfig.java           # Redis cache manager, TTLs, JSON serializer
│       │   ├── CacheProperties.java
│       │   ├── MessagingProperties.java   # enabled / exchange / routing-key only
│       │   ├── RabbitMqConfig.java        # Exchange + JSON converter (topology owner:
│       │   │                              #   the consumer service, not this one)
│       │   ├── DemoUserInitializer.java
│       │   ├── ExternalHrProperties.java
│       │   ├── RestClientConfig.java
│       │   └── OpenApiConfig.java
│       ├── controller/                    # Auth, Employee, Department REST layers
│       ├── integration/                   # ExternalHrClient (bounded retries) + payload
│       ├── observability/                 # CorrelationIdFilter (X-Request-Id -> MDC)
│       ├── event/                         # EmployeeCreatedEvent + @Async after-commit hook
│       ├── messaging/
│       │   ├── EmployeeEventPublisher.java      # Transport-agnostic interface
│       │   ├── AmqpEmployeeEventPublisher.java  # RabbitMQ publisher
│       │   ├── LocalEmployeeEventPublisher.java # In-process fallback
│       │   └── EmployeeEventRelay.java          # AFTER_COMMIT -> broker
│       ├── service/ + service/impl/       # Business logic, idempotency
│       ├── repository/                    # JPA repositories, Specifications
│       ├── entity/                        # Employee, Department, UserAccount, ...
│       ├── security/                      # Stateless JWT, 401/403 handlers
│       ├── dto/ + exception/              # Payloads, domain exceptions, advice
│       └── resources/application.yml      # Port 8080, tracing, cache, rabbit
├── onboarding-service/                    # Service 2: owns the consumer + its data
│   └── src/main/java/com/wangqi/onboarding
│       ├── OnboardingServiceApplication.java
│       ├── config/
│       │   ├── MessagingProperties.java   # enabled / exchange / routing-key / queue / DLQ
│       │   └── RabbitMqConfig.java        # Exchange, queue + DLQ, binding, converter
│       ├── messaging/EmployeeCreatedConsumer.java   # @RabbitListener
│       ├── service/OnboardingInitializer.java       # @Transactional, idempotent
│       ├── controller/OnboardingTaskController.java # GET /api/onboarding/tasks
│       ├── repository/OnboardingTaskRepository.java # existsByEmployeeId
│       ├── entity/OnboardingTask.java               # unique employee_id
│       ├── security/                      # Verify-only JWT (no user table)
│       ├── dto/ + exception/              # OnboardingTaskResponse, ApiError
│       └── resources/application.yml      # Port 8081, its own H2, listener observation
├── api-gateway/                           # Service 3: the public entry point
│   └── src/main/java/com/wangqi/gateway/ApiGatewayApplication.java
│       └── resources/application.yml      # Port 8000, two routes, traceId logging
│
├── employee-management-web/               # Vue 3 SPA（Vite + TypeScript + Element Plus）
│   ├── src/api/                           # 类型化契约与 Axios 拦截器
│   ├── src/stores/auth.ts                 # 会话状态（JWT 同步到 localStorage）
│   ├── src/router/index.ts                # 路由与登录守卫
│   ├── src/layouts/DefaultLayout.vue      # 侧边栏、顶栏、面包屑
│   ├── src/components/EmployeeFormDialog.vue
│   ├── src/views/                         # Login、Dashboard、Employees、NotFound
│   └── src/styles/main.css                # 设计变量与 Element Plus 覆盖
│
├── employee-management-api/src/test/java/com/wangqi/employee
│   ├── service/EmployeeServiceImplTest.java         # Mockito unit tests
│   ├── service/DepartmentServiceImplTest.java
│   ├── controller/EmployeeControllerTest.java       # MockMvc web tests
│   ├── controller/DepartmentControllerTest.java
│   ├── repository/DepartmentQueryCountTest.java     # 4 statements vs 1
│   ├── security/JwtServiceTest.java                 # Signing, expiry, tampering
│   ├── security/SecurityIntegrationTest.java        # Login, 401 vs 403, roles
│   ├── integration/ExternalHrClientTest.java        # Retry, no retry on 4xx, give up
│   ├── controller/IdempotencyIntegrationTest.java   # Replay, 409, no duplicates
│   ├── controller/EmployeeSearchIntegrationTest.java
│   ├── config/AsyncConfigTest.java                  # Pool bounds + MDC propagation
│   ├── config/CacheConfigTest.java                  # TTLs + JSON round trip
│   ├── config/MessagingConfigTest.java              # Producer exchange + __TypeId__
│   ├── messaging/EmployeeEventRelayTest.java        # Mapping, broker outage
│   ├── observability/CorrelationIdFilterTest.java
│   ├── support/QueryCounter.java                    # Hibernate StatementInspector
│   └── service/CacheBehaviorIntegrationTest.java    # 1 statement then 0; eviction
├── onboarding-service/src/test/java/com/wangqi/onboarding
│   ├── service/OnboardingInitializerTest.java       # Duplicate delivery is a no-op
│   ├── config/RabbitMqTopologyTest.java             # Queue, DLQ args, binding, converter
│   ├── security/JwtServiceTest.java                 # Shared-secret verification
│   └── controller/OnboardingApiTest.java            # 401, and 200 with a shared token
├── contracts/src/test/java/com/wangqi/contracts
│   └── messaging/EmployeeCreatedMessageTest.java    # JSON round trip + FQCN assertion
├── api-gateway/src/test/java/com/wangqi/gateway
│   └── GatewayRoutingTest.java                      # Route URIs and Path predicates
│
└── specs/microservices-evolution/         # spec.md, tasks.md, checklist.md
```

每个模块保留自己的 `Dockerfile`；三者都从仓库根上下文构建，以便 Maven
解析 `contracts` 和同级 POM。

---

## 本项目展示了什么

- 清晰的关注点分离（controller / service / repository / DTO）。
- 防御式 API 设计：在边界处校验，错误契约可预测。
- 正确使用 HTTP 语义，包括 `201 Created` 上的 `Location`。
- 通过构造器注入和接口驱动服务实现可测试代码。
- SQL 层面意识：识别 N+1 查询，用 `@EntityGraph` 修复，
  并用自动化查询计数测试锁定修复。
- 边界安全：BCrypt 存储、无状态 JWT 校验、显式角色规则，
  以及一致的 JSON `401`/`403` 响应。
- 集成韧性：每次尝试都有超时、有界指数退避、客户端错误不重试，
  以及用 `502` 替代泄漏的连接器异常。
- 重试下的正确性：幂等 key 存入数据库、请求指纹检测 key 复用、
  重放原始响应，使重复 `POST` 无法创建重复行。
- 读路径上的查询纪律：过滤和排序下推到 SQL、页大小有上限、
  排序面白名单化，响应是稳定封装而非框架内部类型。
- 在关键处优化性能：热点读端点由 Redis 提供，命中时完全跳过数据库
  （测试中断言为 0 条 SQL 语句），失效逻辑映射到每个改变缓存视图的写入，
  并暴露命中/未命中计数器，使缓存可基于数据调优。
- 在值得的地方使用架构：写入路径通过持久 topic exchange 解耦，
  事件仅在提交后发布，消费者因至少一次投递而保持幂等，
  失败停放在死信队列而不是循环，第二个服务无需改动本仓库代码即可订阅。
  剩余缺口（提交与发布之间的 outbox）被明确指出而非隐藏。
- 有文档的 API：OpenAPI 文档由运行代码生成，因此不会像手写 spec 那样与实现脱节。
- 可运维性：能跨异步边界存活的 correlation id、编排器可据以行动的
  健康与指标面、显式线程池预算，以及以非 root 用户运行且带真实健康探测的镜像。
- 无需重写的微服务演进：服务边界沿着既有事件边绘制，
  因此消费者迁到独立可部署服务，生产者的改动仅限一个 import 和一条 POM 依赖。
  线上契约放在一个小的共享模块中，因为类名是 AMQP 头的一部分；
  拓扑归属转移到消费者，这就是生产者不再声明它不读取的队列的原因。
- 网关纪律：单一公开入口、路由在配置中声明并在测试中断言，
  认证留在下游——那里才真正能强制执行。
- 跨传输协议的分布式追踪：同一个 trace id 通过 HTTP（`traceparent`）
  和 AMQP（observation-enabled template 与 listener）传递，
  因此一个网关请求和数分钟后响应它的消费者仍可在日志和 Zipkin 中关联。
- 务实的拆分：每服务一个数据库、用事件携带快照替代同步查询、
  用共享 secret 的 JWT 避免第二张用户表。取舍（共享 secret 分发、
  无 outbox、内存 H2）被写下来，而不是含糊带过。
