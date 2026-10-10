# 多用户与 ACL 权限管理设计
实施版 v7，2026-10-04。已按用户最终修订实现并重新部署。

## 1. 最终规则与闭环结论
采用用户具体权限、权限集合比较、实时权限查询，不存角色、上级或后代关系。
- 配置单账号为唯一超管，具备 read:*、update:*、delete:*。
- 管理员、普通用户只是前端模板；数据库只存具体权限。
- 创建时拟授权限必须 <= 操作者权限，包含相等。
- 管理现有账号要求目标全部权限严格小于操作者，任一权限超出即整个账号不可管理。
- 创建者降权、禁用、删除不影响曾创建的用户。
- 本人改用户名、密码、头像为账户能力，不需要业务 UPDATE；不支持本人改权限。
- 普通模板 read:default_path 在创建时展开配置为具体路径，落表快照。
- 无 ACL 注解默认通过权限检查；原有登录门禁继续保留。
- 会话永久有效；禁用、删除用户时删除其全部会话，立即失效。改密不影响已有会话。
- 改权限保留会话，每次鉴权实时查询当前权限，下一次请求立即生效。

本稿将“小于”解释为严格集合包含，以防相等权限管理员互相删除；若需要相等也能管理，可将该规则调整为非严格包含。创建相等权限用户后，操作者将不能继续管理它，页面需提示。超管不受此范围限制，仍不能删除、禁用、修改超管权限。

最新决策：会话不具备权限快照。权限变更下一次请求立即生效；已经通过请求开始鉴权的请求继续执行，不重查权限。授权创建时落表的独立配置仍是快照，不是会话快照。

## 2. 项目接入
现有 Java 8 / Spring Boot 2.6.7 / MyBatis Plus，主数据源与 TableInitConfig 使用 MySQL；本稿 DDL 按 MySQL InnoDB。
DashboardAuthProperties 实际读取 dashboard.auth.user-id/password，并非源码写死。DashboardAuthTokenService 仅支持配置账号，永久 AES-GCM Token 不具备多用户撤销机制；SecurityFilter 保留登录门禁，增加会话查询。
前端为本地 Vue / Vue Router / Element Plus，新增页面接入 dashboard/app.js，无独立构建。
ArtistGalleryController 的媒体根限制、真实路径校验保留；增加 ACL 不代替文件安全检查。

## 3. 用户与超管
字段：userId、username、passwordHash、enabled、permissions、avatarBase64、isSuperAdmin、createdAt、updatedAt、deletedAt。
userId 为唯一不可改登录 ID，1～64 位 ASCII 字母数字及 . _ -，统一小写。username 可重复，trim 后为空后端填 userId，最大64字符。
密码仅请求传明文，入库带盐哈希，永不返回。删除采用软删除，ID不复用。

初始化超管资料行：isSuperAdmin=true，passwordHash=NULL，继续由现有配置认证，支持名称头像。超管不能网页改密，修改配置重启后新登录使用新密码，已有超管会话保持有效。配置ID与已初始化超管不同则要求显式迁移，不能自动生成第二个超管或劫持普通账号。

模板：
| 模板 | 默认内容 |
| --- | --- |
| 管理员 | read:*、update:*、delete:* |
| 普通用户 | read:default_path 展开结果 |
| 手动 | 具体 type:point 列表 |
不保存role、template、parentId。有限操作者选择全权限管理员模板时子集校验失败。

## 4. 权限集合和管理规则
P(u)=用户数据库当前配置的权限集合，每次鉴权实时查询。各type独立，无隐含关系，不读取会话权限或创建者权限。

创建或调整目标权限：proposed ⊆ P(actor)。
管理已有账号需全部满足：
1. 操作者当前落表权限具有对应op:user.*操作点。
2. 不是本人，也不是超管。
3. P(target)是P(actor)的严格子集；任一type越界即整个账号不可管理。
4. 改权限时拟授集合仍为操作者当前权限的子集。

不比较目标会话权限，因为会话中没有权限。允许将目标升到与自己相等，但保存后操作者失去管理能力，提交前提示。没有上下级继承或级联变更。
请求开始时读取操作者和目标当前权限，完成操作点、目标范围及拟授权限检查。通过后按该次检查结果执行，不加权限锁、不在事务内重查权限，也不因请求途中权限变化而中止。validate只供前端预检查，正式保存请求开始时仍独立鉴权。用户和权限写入使用普通事务保证数据一致，不做版本控制；并发编辑按最后一次成功提交结果保存。

## 5. point 与通配符
type Java枚举 READ/UPDATE/DELETE，JSON与文本为read/update/delete。新增、上传、启停、触发任务归UPDATE，删除/清理归DELETE。
同type多规则OR；多个ACL检查AND。无权限仍可登录与使用本人能力、未加ACL接口。

操作点使用 op:user.list、op:artist.status 等稳定命名；文件路径使用 D:/media/test/*。默认注解点 op:全限定类名.方法名。*代表所有命名空间；op:*仅业务操作。
read:*确实包括用户管理读点，普通模板不填它。

仅支持 *：匹配零或多个字符，包含/，整串锚定；其余字符字面量。不支持?、正则、特殊**。
D:/test/*包含后代但不包含D:/test，目录树授权生成目录本身与子树两行；D:/test*也包含D:/test2，需提示。
文本只按第一个冒号切分，因此read:D:/test/*合法。
权限point保存和查询保留原文（大小写、斜杠方向及末尾斜杠）。仅匹配与通配符集合包含校验时将Windows路径统一为/和小写；op点仍大小写敏感。point最多512字符、每用户200行、每行最多16个*；拒绝空值、控制字符、路径..和非法路径语法。

子集判断采用受限glob自动机，逐type判断子模式并集是否包含于父模式并集；检查L(child)与L(parent)补集的交集是否为空。严格小于另证明父集合存在子集合未包含的合法point。
不能用match(parentPattern,childPattern)替代：D:/*可授D:/test/*，不能授C:/test。
只在规范合法point域比较；限定状态数/执行时间，无法证明则拒绝，返回行号，绝不降级放行。前端不执行复杂子集证明，使用后端validate。

## 6. default_path 配置
read:default_path 是模板引用，不是运行时权限别名。
配置示例：
```properties
dashboard.acl.default-paths[0]=D:/media/fanbox/ahrstudio
dashboard.acl.default-paths[1]=D:/media/fanbox/ahrstudio/*
```
GET /api/acl/defaults返回模板文本及resolvedPermissions。创建接口permissionText允许read:default_path，后端展开后检查子集再保存具体点；permissions数组必须是已展开规则。
配置修改仅影响以后创建或主动应用模板，不改变已有授权。默认目录越出操作者权限则403，不静默截断。
普通模板没有隐式op读点：作者及自由画廊登记列表对登录用户可见，不按目录权限隐藏条目；无权限画廊入口置灰，内容接口强制校验路径；若某接口显式要求READ操作点，模板必须额外配置它。

## 7. 注解
```java
public enum AclType { READ, UPDATE, DELETE }
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ACL {
    AclType type();
    String point() default "";
}
// 示例
@ACL(type = AclType.READ, point = "op:user.list")
@ACL(type = AclType.UPDATE, point = "$arg1.field1")
@ACL(type = AclType.DELETE)
```
Java注解用等号。$arg1从1开始指第一个业务参数，忽略请求/响应框架参数；支持$argN或$argN.field1.field2。仅白名单DTO属性读取，禁止任意SpEL、方法、Bean、构造器调用；null/解析失败拒绝，不能回退默认点。
文件引用经ResourcePointResolver规范化、解析真实资源。按ID修改时以数据库资源路径为准，不能相信请求旧路径。
重复注解用@ACLs；类级和方法级约束累加AND。

SecurityFilter认证与上下文；AclAspect检查存在的注解，无注解默认通过ACL；Service完成对象校验、逐项过滤、路径解析、用户管理集合判断。关键校验直接调用统一Evaluator，避免AOP自调用漏校验。
默认通过不代表匿名开放，原登录白名单不扩大。生成敏感接口覆盖清单供审查，但不因无注解启动失败。

## 8. 媒体链路与数据过滤
目录入口READ真实目录，返回项目逐项READ。原图、缩略图、视频封面、Range均READ真实源文件，不仅folderPath。缓存路径不是授权点。
ArtistImageController按fileName返回图片需绑定作者/源媒体身份再查权限；无法归属文件不对受限用户开放。
公共缓存只保存原始资源，每个请求开始时鉴权，用户过滤结果不能写回全局缓存。
作者列表和自由画廊登记列表不按目录权限过滤，分页及总数包含所有匹配条目。前端根据当前权限置灰无权限的画廊入口和操作按钮；直接访问无权限画廊内容接口返回403。全局爬取历史没有可靠目录归属，仅具备全局READ及历史入口权限的用户可查询；受限用户首页返回空统计及空作者列表，避免泄漏全局数据。第一版不缓存用户授权后的资源结果，按请求开始时读取的权限过滤。
修改/删除要求对应op点与真实路径UPDATE/DELETE，目录迁移查旧新目录；全局任务查操作点及全部目标范围，请求开始时授权通过即可提交任务，任务沿用本次授权范围，不在执行时重新鉴权。
DTO返回canonical point、canOpenGallery/canUpdate/canDelete。未授权记录默认不返回；可见作者但媒体不可读时画廊按钮置灰。
先解码、normalize、toRealPath、验证媒体根再授权；拒绝跨盘、UNC、ADS、双重编码、链接/junction逃逸。不存在的目标用真实父目录解析，打开前复核文件身份。超管也不能越出媒体根。
响应采用private/no-store，切号清前端数据。不能回收已下载文件；会话撤销保证新请求拒绝，不承诺中断既有传输。

## 9. 会话、盐和即时生效
每次登录生成随机sessionId、32字节独立salt及CSRF Token，不存任何权限快照。
Token=v2.sessionId.signature；signature=HMAC-SHA256(serverSecret, tokenFormat+sessionId+salt)，字段按固定长度/长度前缀编码。
盐不是签名密钥；serverSecret独立配置，不使用用户密码。表中不存原始Token。
请求按sessionId查表，用该行盐验签，查询用户enabled/deletedAt，然后在请求开始时查询当前权限，本次请求内的ACL和列表过滤共用该规则组。会话仅负责识别用户，不携带权限。
删除会话即失效；v1永久Token不兼容，上线全部重新登录。

| 事件 | 会话及权限行为 |
| --- | --- |
| 注销 | 删除当前会话 |
| 禁用/删除 | 删除目标全部会话 |
| 本人改密/管理员重置密码 | 保留已有会话，新登录使用新密码 |
| 改权限 | 保留会话，下一次请求实时使用新权限 |
| 改名称/头像 | 保留会话，刷新资料 |
| 创建者任何变更 | 不影响其他账号 |

会话没有过期机制、最后访问时间或后台过期清理。用户未禁用、未删除且未主动注销时，会话长期有效。禁用会删除全部会话，重新启用后需重新登录，旧Token不会恢复。会话服务端不因改密或改权限失效；主动退出当前/全部会话仍可删除相应记录。Cookie配置长期有效，不根据用户活动续期。
第一版不使用跨请求权限缓存，不能把权限放入session或长期CurrentUserContext。每个请求开始时一次读出完整规则组，保存在仅本次请求有效的CurrentUserContext中；请求结束后丢弃。请求进行期间不重新查询，不处理期间发生的权限修改。

Cookie保留名称，HttpOnly、SameSite=Lax，HTTPS用Secure；写接口检查X-CSRF-Token，不比较Origin、协议、主机、端口或Sec-Fetch-Site。登录错误统一，按IP限制5分钟内20次失败尝试。
普通密码实际采用Java 8内置PBKDF2-HMAC-SHA256、600000次迭代、随机16字节盐、256位输出；完整编码包含算法/参数/盐，无新增运行时密码库依赖。密码12～128字符。

## 10. 表结构
MySQL InnoDB；服务校验type/唯一超管，不依赖CHECK版本行为。权限直接保存在用户表permissions字段，内容为JSON字符串数组，例如["read:D:/test","read:D:/test/*"]。使用MEDIUMTEXT兼容现有MySQL，后端解析为List<String>，不使用独立权限表或权限摘要。
```sql
CREATE TABLE t_dashboard_user (
 user_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 username VARCHAR(64) NOT NULL,
 password_hash VARCHAR(255) CHARACTER SET ascii NULL,
 enabled TINYINT NOT NULL DEFAULT 1,
 permissions MEDIUMTEXT NOT NULL,
 avatar_base64 MEDIUMTEXT CHARACTER SET ascii NULL,
 is_super_admin TINYINT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL,
 updated_at DATETIME(3) NOT NULL,
 deleted_at DATETIME(3) NULL,
 PRIMARY KEY(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


CREATE TABLE t_dashboard_user_session (
 session_id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 user_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 token_salt VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 csrf_token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 created_at DATETIME(3) NOT NULL,
 PRIMARY KEY(session_id),
 KEY idx_session_user(user_id),
 CONSTRAINT fk_session_user FOREIGN KEY(user_id)
 REFERENCES t_dashboard_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

```
permissions默认由后端填[]，仅接受字符串数组；按第一个冒号解析type和point，验证格式、统一type并按原文去重后整体保存；point不改写。权限更新只更新用户行的permissions字段。超管可保存三条全权限，但系统超管能力仍由受保护的is_super_admin确定。会话表只记录会话ID、用户ID、随机盐、CSRF摘要与创建时间；创建时间仅用于记录，不参与过期判断。不记录过期时间或最后访问/登录时间，也不保存权限或权限版本。仅保留用户、会话两张表，不建立权限审计表。
软删除清密码/头像与全部会话，ID保留；删除时将permissions置为[]。普通日志不记录密码、Token、盐、完整头像。

## 11. 接口
统一/api与ResultEntity(result,message,data)，建议新增code。
400参数错误、401会话失效、403权限不足、404目标不可见、409重复ID、413上传超限。

### 11.1 本人
| 方法路径 | 请求/返回 |
| --- | --- |
| POST /api/auth/login | userId,password；me,csrfToken与Cookie |
| POST /api/auth/logout | 删除当前会话 |
| GET /api/auth/me | 本人资料、当前全部permissions |
| GET /api/auth/csrf | 刷新CSRF |
| PATCH /api/auth/me | username |
| PUT /api/auth/me/password | oldPassword,newPassword；保留已有会话 |
| POST /api/auth/me/avatar | multipart file |
| POST /api/auth/me/logout-all | 退出本人全部会话 |
本人接口只需登录/本人校验/CSRF，不需要业务UPDATE；超管改密接口不开放。
me不含头像；me.permissions为["read:D:/test","read:D:/test/*"]这样的字符串数组，返回数据库当前全部授权，不返回任何权限版本。前端据此渲染，后端按每个请求开始时的当前权限鉴权。

### 11.2 管理
| 方法路径 | 内容 | ACL |
| --- | --- | --- |
| GET /api/users | 分页/筛选/可管理目标 | READ op:user.list |
| GET /api/users/{id} | 资料、当前权限、capabilities | READ op:user.list |
| POST /api/users | 新增 | UPDATE op:user.create |
| PATCH /api/users/{id} | username | UPDATE op:user.update |
| PUT /api/users/{id}/enabled | enabled | UPDATE op:user.status |
| PUT /api/users/{id}/permissions | permissions | UPDATE op:user.permissions |
| PUT /api/users/{id}/password | newPassword | UPDATE op:user.password |
| DELETE /api/users/{id} | 无请求体 | DELETE op:user.delete |
| POST /api/users/{id}/logout-all | 撤销全部会话 | UPDATE op:user.sessions |
| GET /api/acl/catalog | 注册点说明 | 登录、过滤范围 |
| GET /api/acl/defaults | 模板与展开结果 | 登录 |
| POST /api/acl/validate | permissions或permissionText、可选目标 | 登录、目标范围 |

管理接口除注解外执行第4节全部规则。列表非超管仅本人及可管理账号；额外返回脱敏超管摘要置顶，不占普通分页total、不开放详情。超管查看所有。本人管理按钮禁用。普通用户没有user.list则不展示管理入口。
任何用户列表、详情、当前用户查询及登录返回的用户DTO都不携带头像内容，不返回avatarBase64或其他头像数据。查询使用明确列选择，避免读取头像大字段；密码哈希、会话盐和Token同样不返回。头像仅由专门图片接口提供。

创建示例：
```json
{"userId":"reader01","username":"","password":"example-only-change-me",
 "enabled":true,"permissionText":"read:default_path"}
```
permissionText与permissions互斥，不传采用普通模板，显式[]表示空权限。拒绝role、parentId、isSuperAdmin等输入。创建201返回不含头像的用户DTO。permissions请求与响应统一为["type:point"]字符串数组，不采用type/point对象数组。
validate返回valid、normalizedPermissions、errors[{line,code,message}]、warnings、willLoseManagement。
管理和头像保存均不传版本参数。
错误码建议PERMISSION_NOT_SUBSET、TARGET_NOT_STRICT_SUBSET、SUPER_ADMIN_PROTECTED等。

## 12. 头像和前端
选择PNG/JPEG，Canvas支持移动缩放、1:1裁剪，导出256×256PNG上传。后端实际解码、缩放、重新编码，剥离元数据；输入<=2MiB、边长<=4096、总像素<=16M，输出<=256KiB。拒绝SVG/动画/伪图片；WebP解码兼容验证后开放。
标准data:image/png;base64,...写用户avatar_base64，产物约<=350KiB。不能把任意客户端base64直接入表。仅本人上传，管理PATCH不接受头像。上传成功仅返回成功状态，不返回头像内容，前端重新请求专门头像接口。
### 专门头像与网站icon接口
| 方法路径 | 行为 | 访问规则 |
| --- | --- | --- |
| GET /api/users/avatar?userId={id} | 查询指定用户头像，解码base64并直接返回image/png二进制；未配置头像返回默认图片 | 需要登录；本人或用户管理列表中可见目标（含置顶超管摘要）；不可见目标404 |
| GET /favicon.ico | 先识别当前会话身份，再返回当前用户头像；未登录、会话无效、用户已禁用/删除或未设置头像时返回默认图片 | 匿名可访问，不重定向登录 |

两个接口不返回用户JSON。头像与icon使用Cache-Control: private, no-store，防止不同账号复用图像；默认头像由服务器本地资源提供。
/favicon.ico在现有SecurityFilter公开白名单中保留，但接口内部需主动尝试解析登录身份，不能因为是公开路径就跳过身份识别。身份识别失败正常返回默认图片，不返回401。
前端页头和用户列表使用专门头像接口；link rel=icon始终指向/favicon.ico。上传、清除、退出或切换账号后重新加载图片与icon，可附带时间戳刷新浏览器显示；该时间戳不是版本控制。默认图标尺寸与上传头像保持适配。

新增/manage/users与我的账户。编辑器一行type:point，模板仅填文本；显示默认路径展开预览、子集错误与权限立即生效提示。权限修改不删除会话。
统一can(type,canonicalPoint)使用/me返回的当前权限匹配，业务行结合capabilities置灰；用户管理按钮必须用后端集合比较结果。
401清用户/头像/敏感页面数据去登录；不使用权限版本响应头；保存权限成功、遇到403或窗口聚焦时刷新/me并重新渲染，无需重新登录。路由也核对业务入口能力。

## 13. 实施与验收
实施顺序：建表超管初始化；实时权限查询与多账号会话认证；ACL与集合算法；敏感业务媒体全链路；用户页头像；测试打包。部署已按AGENTS.md通过生产run.bat执行。

验收覆盖：
- 无角色/上下级字段，创建者变更不影响其他账号。
- 只有用户与会话两张表；permissions保存JSON字符串数组，创建/编辑/查询格式一致。
- 列表、详情、me、登录返回均不含头像；按ID头像接口与匿名favicon默认图行为正确。
- D:/*正确包含D:/test/*、拒绝C:/test；任一type越界整个目标不可管理。
- 相等权限不可互管，创建相等有提示；目标会话数量和历史权限不影响管理资格。
- 权限改动后同一会话下一次请求使用新权限；禁用/删除立即撤销全部会话；改密保留会话。
- 默认无ACL通过但保留登录门禁；所有敏感出口覆盖。
- default_path创建展开，配置变更不回写。
- 列表/分页/统计过滤，媒体Range/缩略图/缓存/封面无绕过。
- 路径穿越、大小写、链接、双重编码、请求开始后权限变化不影响已通过鉴权请求的测试。
- 本人只读可改头像/名称/密码，不能自改权限。
- 前后端glob一致，集合包含、严格包含、复杂度上限正确。
- CSRF、永久会话、禁用后重新启用旧Token仍无效、切号、头像伪文件/超限正确。

实施时选择测试类，避免legacy手工爬取/录音测试；画廊变更按AGENTS.md执行指定Maven测试与Node检查。本次代码已测试、打包与部署。

## 14. 参考与取舍
[OWASP Authorization](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html)推荐默认拒绝与逐请求授权；本稿按明确需求采用默认通过，因此敏感接口覆盖清单和Service强制检查必须落实。
[OWASP Password Storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)支持密码哈希参数选择。
[OWASP File Upload](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html)支持图片实际解码、限制与重编码。

## 15. 实施验证记录（2026-10-04）
- 新增两张表：t_dashboard_user、t_dashboard_user_session。
- 指定Java测试39项通过，包含账号、会话、ACL注解、通配符集合、头像及画廊回归；Node画廊生命周期、滚动恢复和user-acl检查通过。
- Headless Edge实际验证用户页、快捷模板、我的账户、图片裁剪上传与按钮置灰。
- 生产通过run.bat启动，端口18080；认证后dashboard、玩家资源、用户管理资源、真实ahrstudio画廊均正常。
- 生产验证创建/登录只读账号、阻止业务写入、头像与icon一致、同会话实时降权、禁用再启用旧Token仍失效；临时验证账号已软删除。
- 生产JAR与构建JAR、已部署播放器和用户页面资源哈希一致。
- 旧JAR备份：D:/DazoServer/DazoServer-1.0-SNAPSHOT.jar.backup-20261004-001702。
- 原配置账号现在为超管；旧格式Token失效，需要重新登录。普通用户默认目录读权限取自配置，默认使用fanbox.save.path及其子树，外部可通过dashboard.acl.default-paths[0]等覆盖。

### 域名反向代理兼容修正
已去除Origin及Sec-Fetch-Site来源校验，避免https://cboard.dazo66.com通过反向代理访问时被后端HTTP协议/端口判定错误。登录与ACL不受影响，登录后的写请求仍验证会话CSRF Token。

### 作者与自由画廊列表显示修正
作者及自由画廊登记列表对所有登录用户显示匹配条目，不以目录权限筛除。桌面和手机的无权限画廊入口置灰；直接访问内容仍返回403并显示错误。目录授权按通配符匹配，read:D:/OneDrive只匹配目录本身，read:D:/OneDrive/*匹配其后代路径。
本次24项Java测试及Node检查通过；线上使用与zlzz相同权限的临时账号验证列表完整、入口置灰、直达画廊403，超管真实画廊200。验证账号已删除。生产通过run.bat重新部署且JAR哈希一致，备份为D:/DazoServer/DazoServer-1.0-SNAPSHOT.jar.backup-20261004-004641。

### 账户入口及逐行权限编辑
移除“我的账户”导航项；头像、用户名与退出图标位于菜单底部，点击头像或用户名进入账户设置。用户权限编辑以每行type下拉框及point文本框呈现，支持添加和删除行，快捷模板填入各行，保存继续传递type:point并落表JSON数组。移除指定说明文案及恢复默认头像按钮，删除DELETE /api/auth/me/avatar接口；未设置头像时仍显示默认头像。
本次27项Java测试及Node检查通过；浏览器验证权限行增删、类型切换、新增和编辑的保存内容、头像上传，以及320/390/820/1280宽度的账户入口。生产经run.bat部署，域名页面交互验证通过，实际ahrstudio画廊HTTP 200，构建及部署JAR哈希一致。

### 移动端单行导航与账户退出
移动端和平板顶部合并为单行：头像及用户名在最前，菜单横向排列并支持滑动；桌面端保留侧栏账户入口。两端的退出登录按钮均移入账户设置页面。Node检查及指定Java测试通过，线上320/390/820/1280宽度验证正常，账户退出返回登录页，真实ahrstudio画廊HTTP 200，JAR哈希一致。

### 账户名称与头像交互简化
移除保存名称按钮，用户名修改并离开输入框时自动保存；相同名称不重复提交。头像是可通过鼠标和键盘操作的选图按钮，原生文件输入隐藏，点击头像选择图片后保留裁剪及上传流程。Node、指定Java测试、浏览器自动保存和选图裁剪上传验证通过；经run.bat部署，线上选图入口及真实画廊访问正常，生产与构建JAR哈希一致。

### 账户身份区紧凑排版
账户页头像缩小为48像素，与用户名输入框放在同一行，用户ID置于名称输入框下方，取消头像独占整行的布局。名称自动保存及点击头像选图裁剪验证通过；部署后320/390/1280宽度布局正常，实际画廊HTTP 200，生产JAR哈希一致。

### 权限点原文存储
point在保存、详情、列表和会话权限读取时保留原写法，不再转换大小写、斜杠方向或移除末尾斜杠。前端逐行编辑的保存同样保留point原文。匹配与集合包含判断继续使用canonical归一化，Windows路径忽略大小写和斜杠方向，op点大小写敏感。精确重复行仍去重，权限格式校验保留；以前被改写的值无法还原其原始写法，需重新输入保存。
相关Java及Node测试通过；生产临时账号验证大写与反斜杠权限点原样返回且可读取实际画廊，账号已删除。已通过run.bat部署，生产与构建JAR哈希一致。

### 画廊上传与独立排序偏好
每个画廊标题栏右侧显示上传文件按钮。仅具备当前目录UPDATE权限的用户可用，后端还校验目标文件UPDATE权限、目录归属、文件名与路径，单文件最大500MiB，不覆盖已有同名文件。上传中按钮禁用并显示上传中xx%，旁边圆环显示累计进度，成功后仅刷新后端索引，不自动重新查询前端画廊列表。
接口POST /api/fanbox/gallery/upload接收folderPath与multipart file；大于512KiB的文件由前端分片调用POST /api/fanbox/gallery/upload-chunk，附filename、totalSize、partIndex、partCount和服务端返回的uploadId。每个分片最大512KiB，总大小不超过500MiB，严格校验顺序、用户和目标，所有分片完成后合并文件并调用rebuildAuthorized强制刷新索引。临时文件只存缓存目录，不落表；15分钟无活动的上传自动清理。中途失败或页面关闭可重新选图上传，不提供断点续传。这样避免域名Nginx对整体大请求返回413，无需修改Docker代理。非媒体文件通过文件接口以attachment返回。
localStorage key为dazo-gallery-sort-v1:加文件夹绝对路径（Windows大小写及斜杠归一），value为{field,order}。排序切换立即保存，首次请求优先采用对应目录的保存值，切换目录时分别恢复；存储不可用或内容无效时使用默认值，不影响浏览。
31项指定Java测试以及Node、浏览器进度/置灰/排序测试通过。生产通过run.bat部署，域名实际2MiB分片上传成功，合并文件大小与SHA256一致、索引立即出现新文件；只读用户接口403且按钮禁用，实际ahrstudio画廊200，生产及构建JAR哈希一致。临时验证账号、自由画廊登记和文件已清理。

### 文件修改时间与上传标题栏适配
文件详情通过原文件HEAD响应的Last-Modified读取文件系统修改时间，按浏览器本地时间显示；图片、视频均适用，保留文件大小和分辨率等详情。上传按钮及圆环独立放在标题右侧，移动端保持同一行，排序和返回放到下一行。列表刷新保留已有缩略图的加载状态，避免上传后缩略图停在加载中。后端HEAD时间测试、Node检查、浏览器及域名320/390/1280布局与实际文件修改时间验证通过，真实画廊200，生产JAR哈希一致。

### 随机排序
画廊排序新增random，每次GET列表在权限校验及过滤后重新打乱完整返回列表，不改变缓存索引的原始顺序。随机模式忽略正序倒序，前端方向控件禁用；选择仍按文件夹localStorage偏好保存并恢复。后端随机排列完整性及缓存排序回归、Node、浏览器验证通过。经run.bat部署，真实ahrstudio画廊1272条记录连续10次随机查询返回10种顺序且均不遗漏条目，随机偏好重进恢复，构建与生产JAR哈希一致。

### 上传完成后保留当前列表
取消上传成功后的loadGallery调用，上传提示和进度恢复正常，后端索引仍强制重建。当前列表、随机顺序和浏览位置保持不变，新文件在后续主动查询时显示。浏览器及线上实际2MiB上传验证没有额外列表请求，索引含新文件，生产JAR哈希一致。
