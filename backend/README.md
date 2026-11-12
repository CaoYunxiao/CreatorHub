# CreatorHub 后端：阶段 1

## 环境与构建

- JDK 25：`E:\JDK`
- Maven 3.10.0：`E:\Maven\apache-maven-3.10.0`
- Spring Boot 4.1.2
- Maven 仓库：`E:\Maven\Repository`
- 构建产物：本目录下 `target/`

在 PowerShell 中进入 `E:\CreatorHub\backend`，先执行离线测试：

```powershell
& E:\Maven\apache-maven-3.10.0\bin\mvn.cmd -o '-Dmaven.repo.local=E:\Maven\Repository' test
```

已获准下载依赖时，去掉 `-o`。打包使用相同命令，将 `test` 换为 `package`。
不要直接运行 Wrapper：其 Maven 分发包缓存位置与依赖仓库不同。
IDEA 中也需选择 E 盘 JDK、Maven 与本地仓库。

## 数据库安全配置

通过运行进程环境提供以下变量，不将真实值提交到 Git：

| 变量 | 含义 |
| --- | --- |
| CREATORHUB_DB_URL | 如 `jdbc:mysql://localhost:3306/creatorhub`，禁止在 URL 中放密码 |
| CREATORHUB_DB_USERNAME | 优先使用已有只读账户 |
| CREATORHUB_DB_PASSWORD | 本机安全输入的密码，不放入命令参数、文档或聊天 |

可在 IDE 的本地、非共享运行配置中提供；不要截图或打印含密码的配置。
Spring Boot 不会自动读取普通 `.env` 文件。
未设置凭据时不应启动应用。配置明确关闭 SQL 初始化；不要导入 `database/creatorhub.sql`，其中含 DROP TABLE。
创建账户或授予权限需单独批准。

## 测试与运行

普通 `test` 验证无数据库的 Spring 上下文，数据库测试默认跳过。
本机安全设置三个数据库变量后，显式开启只读验收：

```powershell
$env:CREATORHUB_DB_VERIFY = 'true'
& E:\Maven\apache-maven-3.10.0\bin\mvn.cmd '-Dmaven.repo.local=E:\Maven\Repository' '-Dtest=DatabaseConnectionTests' test
Remove-Item Env:CREATORHUB_DB_VERIFY
```

测试只执行 SELECT：验证连接、当前数据库名称和六张表的列名，不读取业务记录。
该测试直接使用 JDBC，不能单独证明 Spring 数据源配置或 Web 服务启动成功。
六张表的类型、索引、外键与实际权限仍需后续按批准范围检查。

启动应用（先完成数据库环境配置）：

```powershell
& E:\Maven\apache-maven-3.10.0\bin\mvn.cmd '-Dmaven.repo.local=E:\Maven\Repository' spring-boot:run
```

启动成功也不等于连接成功：连接池可能延迟获取连接，需另行通过只读测试验收。
连接失败时检查服务状态、主机端口、数据库名、账户权限；不要把完整配置或异常日志中的秘密发送到聊天。

## 后续分层（本阶段不实现业务）

`controller → service → repository → MySQL`。
`entity` 表示数据库记录，`dto` 表示接口输入输出；密码哈希不得进入响应 DTO。
采用 Spring JDBC：Repository 写参数化 SQL，Spring 管理连接和资源。
本阶段不修改数据库、不实现注册登录、不提交或推送 Git。

## 阶段 2：只读作品列表

新增 `GET /api/works?page=1&size=20`。page 从 1 开始，size 范围 1～100。
固定按 `created_at DESC, id DESC` 排序。超出数据范围返回空数组，不提供总条数。
空表响应：

```json
{"page":1,"size":20,"items":[]}
```

每条记录包含 id、authorId、title、intro、createdAt、updatedAt；不加载正文，
不查询 users 表或密码哈希。数据库 DATETIME 以无时区本地时间返回，不擅自添加 UTC 标记。

- Controller：将 HTTP 参数交给 Service。
- Service：校验分页、计算偏移，将查询对象转换成响应 DTO。
- Repository：通过 JdbcTemplate 执行唯一的 SELECT，参数用占位符绑定。
- entity/WorkSummary：表记录的列表投影，并非完整 works 实体或 JPA 实体。
- DTO：对外响应契约，独立于数据库模型。
- GlobalExceptionHandler：参数错误返回 400，数据库错误返回 503；不回显内部异常。
  Spring MVC 的 404、405 等状态及协议头保留；其他异常返回通用 500。

此阶段无需额外依赖。分页校验在 Service 中显式完成，整数类型转换由 Spring MVC 完成。
构造器注入让 Spring 装配各层，也使测试能替换数据库访问对象。

自动测试继续使用前述 Maven test/package 命令。普通测试不访问真实数据库：
HTTP 测试替换 Repository，Repository 测试替换 JdbcTemplate；这些测试不能证明实际 SQL 已在 MySQL 执行成功。

真实联调需要本机临时配置既有只读账户及三个环境变量，使用新打包 JAR 启动，
然后在另一窗口执行（不写数据库、不插入演示数据）：

```powershell
Invoke-RestMethod 'http://127.0.0.1:8080/api/works?page=1&size=20' | ConvertTo-Json -Depth 5
```

预期 HTTP 200，即使库中没有作品也应返回 items 空数组。
`page=0`、`size=101`、`page=text` 应返回 400。
当前接口没有认证及作品发布状态筛选，仅用于本机课程项目阶段验收；公开部署前必须确定作品可见性规则。
大页码虽不会计算溢出，但 OFFSET 查询可能变慢，数据规模增长后再优化。
本阶段未实现注册、登录或任何写入接口，未改变表结构或账户权限。

## 阶段 3：注册、Session 登录与退出

本节是最新功能说明；前面的“本阶段”描述仅指阶段 1、2。
新增 Spring Security 及其测试依赖，版本由 Spring Boot 管理。

| 方法与路径 | 输入/行为 | 成功状态 |
| --- | --- | --- |
| GET /api/auth/csrf | 返回 headerName、token，同时建立匿名 Session | 200 |
| POST /api/auth/register | JSON：username、password；需要 CSRF header | 201，返回 id、username |
| POST /api/auth/login | application/x-www-form-urlencoded：username、password；需要 CSRF header | 200，authenticated=true |
| GET /api/auth/me | 携带登录 Session Cookie | 200，返回 id、username |
| POST /api/auth/logout | 携带 Cookie 及当前 CSRF header | 204 |

用户名规则：3～50 位英文字母、数字、下划线，注册及登录统一转为小写。
密码规则：至少 8 个 Java UTF-16 单元，UTF-8 编码不超过 72 字节，不裁剪密码中的空格。
BCrypt 使用随机盐及工作因子 12，数据库仅保存哈希；不能解密还原密码。
新注册用户不会自动登录；用户名重复返回 409，非法输入返回 400。
登录失败统一返回 401，不区分用户不存在与密码错误。
认证过滤器中的登录失败（包括认证服务异常）也使用通用 401；不回显底层数据库原因。

采用 Spring Security 内置表单认证过滤器，登录输入与注册 JSON 格式不同，
不自行拼装认证上下文。登录成功更换 Session ID，清理旧 CSRF token；退出销毁 Session。
Session 空闲 30 分钟失效，Cookie 为 HttpOnly、SameSite=Lax。
本机 HTTP 验收未设置 Secure Cookie；正式 HTTPS 部署时需配置 secure=true。
Session 保存在进程内存，重启会退出登录；尚未实现多实例共享 Session。

客户端流程：先 GET csrf，保存响应 Cookie 和 token；POST 时携带同一 Cookie，
并将 token 放进返回的 headerName 指定的请求头。登录成功后再次 GET csrf，
再使用新的 token 进行退出或后续写入。CSRF 不是登录凭据，而是防止其他网站冒用浏览器 Cookie 发起写入。
匿名仍可 GET /api/works；当前用户接口未登录返回 401；缺失/无效 CSRF 返回 403。
不要将密码、Session Cookie、CSRF token 放进 URL、代码、日志、截图或聊天。
不要对真实凭据使用 MockMvc 打印、HTTP 请求调试或命令行明文参数。

新增测试使用模拟 UserRepository 和虚构凭据，不执行真实 INSERT。
覆盖 BCrypt 保存、Session 持续登录和 ID 更换、CSRF 刷新、退出、错误密码、
用户名重复/并发重复、超长密码、匿名作品列表及 JDBC 参数绑定。
现有只读 creatorhub_verify 账户继续保持不变；它不能完成真实注册。
在独立批准数据库写入及应用账户权限方案之前，不执行真实注册验收，
不使用 root 运行应用，不扩大只读账户权限，不插入或删除演示用户。
此阶段尚无登录限流、找回密码、邮箱验证等功能；公开部署前另行设计。

参考：[Spring Security Session 管理](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html)。

## 阶段 4：作品文字管理

| 接口 | 权限 | 成功响应 |
| --- | --- | --- |
| GET /api/works/{id} | 公开 | 200，包含正文和作者 ID |
| POST /api/works | 登录及 CSRF | 201，Location 为新作品的详情地址 |
| PUT /api/works/{id} | 作者本人及 CSRF | 204，随后可重新获取详情 |

创建和编辑接收 JSON，包含必填的 title、content 和可选的 intro。
PUT 替换全部文字字段，省略或传 null 的 intro 会清空简介。标题去除首尾空白后最多
200 字符，简介最多 500 字符，正文非空且 UTF-8 编码最多 65535 字节。
正文是纯文本，客户端应按文本显示。现有列表仍使用原有分页格式且不加载正文。

作者 ID 来自服务端登录身份。更新语句同时匹配作品 ID 与作者 ID。
不存在的作品返回 404，编辑他人的作品返回 403，参数无效返回 400。
写请求继续使用阶段 3 的 Cookie 和 CSRF header。

自动测试使用模拟 Repository 和 JDBC，不访问真实 MySQL。现有应用账户要联调此功能，
还需单独批准 works 表的正文读取和列级 INSERT、UPDATE 权限，以及测试数据写入。
本阶段没有删除接口，也无需修改表结构。

## 阶段 5：上传和展示作品图片

| 接口 | 行为 |
| --- | --- |
| POST /api/works/{id}/images | 作者登录后用 multipart/form-data 的 file 字段上传一张图片，携带当前 CSRF 请求头 |
| GET /api/works/{id}/images | 公开返回图片 ID、imageUrl 和 sortOrder，按 sortOrder、ID 升序 |
| GET /api/works/{id}/images/{fileName} | 公开读取已关联到该作品的图片 |

上传成功返回 201，Location 是可读取的图片地址。新图片的 sortOrder 默认是 0。
上传内容须为能解码的 JPEG 或 PNG，文件最多 5 MiB，宽高各不超过 4096 像素。
文件名由后端生成。应用从 backend 目录启动时，默认把文件保存在 backend/uploads。
可通过 CREATORHUB_UPLOAD_DIRECTORY 设置其他存储目录。

MySQL 记录图片与作品的关联，磁盘保存图片本身。数据库写入失败时会尝试清理本次上传文件。
进程在保存文件后、写入数据库前异常退出，可能留下未关联文件。图片数据恢复需要同时恢复
上传目录和对应数据库记录。

自动测试使用临时目录和模拟数据库。真实 MySQL 联调需要额外批准 work_images 表的
SELECT（id、work_id、image_url、sort_order）、INSERT（work_id、image_url、sort_order）
权限及测试图片关联写入。
