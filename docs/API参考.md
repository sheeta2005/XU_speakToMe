# API 参考

核对日期：2026-10-24。本文对应当前 Controller、Service 和 DTO，不把产品方案中的接口当成已实现接口。源码入口为各业务模块的 `controller` 目录。

## 请求和响应

本地地址为 `http://127.0.0.1:8080`，以下路径均含 `/api/v1`。有请求体时使用 `Content-Type: application/json`。除登录和校区列表外，业务接口均需 `Authorization: Bearer <token>`，包括地点、菜品和榜单查询。管理接口还要求当前数据库用户 role=1、status=1。

统一结果为 `{"code":0,"msg":"ok","data":...}`。无返回数据的操作通常省略 data。分页 data 为 `{"list":[],"total":0,"page":1,"size":10}`。业务失败通常仍为 HTTP 200，调用方必须检查 code；网络、反向代理及健康端点可能使用其他 HTTP 状态。

| code | 含义 |
|---|---|
| 0 | 成功 |
| 10001 / 10002 | 未登录 / 会话失效 |
| 10003 | 当前账号无权限 |
| 20001 | 参数或业务规则错误 |
| 20002 | 点评正文长度不符 |
| 20003 | 内容被拦截 |
| 20004 | 目标不存在或不可用 |
| 20005 | 无权操作该资源 |
| 30001 | 操作过于频繁 |
| 50001 | 系统异常 |

以下为服务显式校验规则；不保证所有格式错误都返回相同错误码。ID 为数值，时间通常为不带时区的本地日期时间字符串；数据库与部署应保持一致的时区。分页 page 从 1 开始，size 最大 50，超限返回参数错误。

## 登录和资料

| 方法 | 路径 | 参数 | data |
|---|---|---|---|
| POST | `/api/v1/auth/login` | 必填 code：微信登录临时凭证 | LoginVO |
| GET | `/api/v1/auth/me` | 无 | UserVO |
| PUT | `/api/v1/auth/campus` | 必填 campusId：正整数 | UserVO |
| PUT | `/api/v1/auth/profile` | nickname：去首尾空白后 1—20 字符；avatarUrl：可选，最长 512 字符 | UserVO |
| GET | `/api/v1/campuses` | 无；无需登录，仅启用校区 | CampusVO 数组 |

LoginVO 包含 token、needCampus、user（UserVO）。UserVO 包含 id、nickname、avatarUrl、campusId、role、createdAt，不返回 openid 密文或摘要。CampusVO 只有 id、name。

登录每 IP 每 60 秒最多 5 次。会话保存在 Redis，默认有效期 7 天，读取不会续期。当前没有退出登录 HTTP 接口，也没有完成批量撤销会话；再次登录不会自动使全部旧会话失效。校区写入目前只检查 ID 为正数，调用方应先从启用校区列表选择；尚缺服务端存在性和启用状态校验。资料接口尚未接入昵称内容审核，不提供头像文件上传。

本地演示用法见[快速开始](快速开始.md)，不要向真实环境发送演示身份凭证。

## 美食浏览和榜单

| 方法 | 路径 | 查询参数 | data |
|---|---|---|---|
| GET | `/api/v1/sites` | campusId 必填且为正数 | SiteVO 数组 |
| GET | `/api/v1/sites/{siteId}/stalls` | 无 | StallVO 数组 |
| GET | `/api/v1/stalls/{stallId}/dishes` | 无，仅启用且在售菜品 | DishVO 数组 |
| GET | `/api/v1/dishes/{dishId}` | 无，菜品须启用 | DishVO，含评分分布 |
| GET | `/api/v1/dishes/{dishId}/reviews` | page=1、size=10、sort=latest；sort 支持 latest/high/low，其他值按 latest | ReviewVO 分页 |
| GET | `/api/v1/rankings/hot-dishes` | days=7，支持 7/30，其他值回退 7；limit=20，范围 1—50，越界回退 20 | HotDishVO 数组 |

| 类型 | 字段 |
|---|---|
| SiteVO | id、campusId、name、siteType、location、openTime、avgRating、ratingCount |
| StallVO | id、siteId、name、floor、category |
| DishVO | id、stallId、name、description、price、avgRating、ratingCount、distribution |
| ReviewVO | id、nickname、avatarUrl、rating、content、auditStatus、createdAt、deletable |
| HotDishVO | rank、dishId、dishName、siteName、stallName、avgRating、recentReviewCount、hotScore |

distribution 为按星级计数的对象，如 `{"1":0,"2":0,"3":1,"4":2,"5":3}`，菜品列表不加载该统计。菜品详情仅检查 status，isAvailable=0 的菜品仍可能读取详情。父级停用没有统一级联屏蔽规则。

点评列表展示审核通过的点评及当前作者自己的待复审点评，排除逻辑删除记录。只支持 food/dish 口径。公开 VO 不暴露作者 userId，deletable 表示当前请求者能否删除。

热门榜单跨校区，按窗口内审核通过且未删除的点评计算热度：`ROUND(条数 × 2 + 窗口平均分 × 10, 2)`。仅菜品自身启用且在售，不额外筛选父级状态；返回的 avgRating 是菜品汇总平均分，和热度使用的窗口平均分不是同一个口径。缓存每十分钟重建，刚处理的点评不保证立即改变榜单顺序。

## 点评和举报

| 方法 | 路径 | 参数 | data |
|---|---|---|---|
| POST | `/api/v1/reviews` | body：见下表 | ReviewVO |
| GET | `/api/v1/reviews/mine` | page=1、size=10 | MyReviewVO 分页 |
| DELETE | `/api/v1/reviews/{reviewId}` | 只能删除本人未删除点评 | 无 |
| POST | `/api/v1/reports` | reviewId 正整数、reasonType=1—6；reasonDetail 可选，去首尾空白后最长 200 字符 | 举报 ID |

| 点评字段 | 规则 |
|---|---|
| module | 默认 food；调用方使用 food。当前服务未对白名单外非空值完全限制，但查询和评分固定 food/dish，其他值不构成可用模块 |
| targetType | 默认 dish，必须为 dish |
| targetId | 存在、启用且在售的菜品 ID |
| rating | 整数 1—5 |
| content | 去首尾空白后 10—200 字符；不接受图片上传 |

提交和举报分别每用户每 60 秒最多 5 次。同一用户对同一点评的举报另有 24 小时 Redis 去重；仅公开、未删除的 food/dish 点评可举报。前端隐藏本人举报入口，服务端尚缺显式自举报拒绝。reasonType：1 垃圾广告、2 色情、3 辱骂、4 政治敏感、5 学术不端、6 其他。

MyReviewVO 包含 id、dishId、dishName、rating、content、auditStatus、createdAt。我的点评包含本人全部审核状态、未删除的 food/dish 点评，按创建时间及 ID 倒序；菜品缺失时名称为“菜品已移除”。本人删除为逻辑删除，并按需更新菜品评分。

审核状态为 0 待审核、1 通过、2 拦截、3 人工复审。demo 提交进入 3；真实审核异常也进入 3。本地直接拦截不创建点评，微信拦截可留下状态 2 的记录并返回 20003。提交成功返回的 ReviewVO 不额外加载 nickname/avatarUrl，页面需要时重新查询。

## 管理：复审与举报

本节所有接口均需管理员，默认 page=1、size=20。返回实体使用 camelCase 字段，字段含义及完整集合见[数据字典](数据字典.md)，不要将管理响应直接当作公开用户响应。

| 方法 | 路径 | 参数及行为 | data |
|---|---|---|---|
| GET | `/api/v1/admin/reviews` | auditStatus=3，允许 1—3；未删除 food/dish 点评，时间及 ID 正序 | Review 实体分页 |
| GET | `/api/v1/admin/reviews/{reviewId}` | 可读到已逻辑删除点评，供核对原文 | Review 实体 |
| POST | `/api/v1/admin/audit/{reviewId}/confirm` | action=pass/block；通过或拦截，也用于下架和恢复；已删除不可恢复 | 无 |
| GET | `/api/v1/admin/reports` | status=0，允许 0 待处理、1 已处理、2 驳回 | Report 实体分页 |
| POST | `/api/v1/admin/reports/{id}/handle` | action=block/dismiss；handleResult 可选，去首尾空白后最长 200 字符 | 无 |

仅待处理工单可处理。block 将点评审核状态改为 2 并刷新评分，不将 isDeleted 设为 1；dismiss 不改点评。处理记录含管理员 ID 和处理时间。源码历史注释中的“24 小时处理”是业务目标，没有自动响应时限保障。

## 管理：对象维护

以下列表默认 page=1、size=20，包含停用对象。没有 DELETE 对象接口，停用使用 PUT 且 status=0。列表不提供按父级过滤参数。

| 方法 | 路径 | data |
|---|---|---|
| GET | `/api/v1/admin/campuses` | Campus 实体分页 |
| POST | `/api/v1/admin/campuses` | 新建校区 ID |
| PUT | `/api/v1/admin/campuses/{id}` | 无 |
| GET | `/api/v1/admin/sites` | Site 实体分页 |
| POST | `/api/v1/admin/sites` | 新建地点 ID |
| PUT | `/api/v1/admin/sites/{id}` | 无 |
| GET | `/api/v1/admin/stalls` | Stall 实体分页 |
| POST | `/api/v1/admin/stalls` | 新建档口 ID |
| PUT | `/api/v1/admin/stalls/{id}` | 无 |
| GET | `/api/v1/admin/dishes` | Dish 实体分页 |
| POST | `/api/v1/admin/dishes` | 新建菜品 ID |
| PUT | `/api/v1/admin/dishes/{id}` | 无 |
| GET | `/api/v1/admin/object-options` | campuses/sites/stalls 三组归属选项 |

| 对象 | POST/PUT 业务字段 |
|---|---|
| Campus | name 必填，去首尾空白后 1—32 字符；sort、status |
| Site | campusId 必填且存在；name 必填，1—64 字符；siteType=1 校内食堂/2 校外区域/3 其他；location≤255、openTime≤64；sort、status |
| Stall | siteId 必填且存在；name 必填，1—64 字符；floor≤16、category≤32；sort、status |
| Dish | stallId 必填且存在；name 必填，1—64 字符；description≤255；price 可空，0—999999.99，最多两位小数；isAvailable=0/1；sort、status |

新增默认 sort=0、status=1、siteType=1、isAvailable=1。sort 允许 0—255，status 为 0/1。可选文本缺失按空字符串处理。归属校验只要求父级存在，停用父级仍可能被选中。

PUT 按完整业务表单提交，名称及归属仍为必填；省略可选文字会清为空字符串，菜品 price 省略或 null 会清除旧价格。评分、人数、创建/更新时间等由服务维护，不能通过这些请求改写；当前接口也不维护封面图。object-options 的有效字段为各对象 id/name/status，以及 site 的 campusId、stall 的 siteId；响应复用实体，未选择字段可能为空。

## 管理：敏感词

| 方法 | 路径 | 参数 | data |
|---|---|---|---|
| GET | `/api/v1/admin/sensitive-words` | page=1、size=20；包含停用词 | SensitiveWord 实体分页 |
| POST | `/api/v1/admin/sensitive-words` | word、category、level；新增固定启用 | 词条 ID |
| PUT | `/api/v1/admin/sensitive-words/{id}` | word、category、level；status 可选，0/1 | 无 |
| DELETE | `/api/v1/admin/sensitive-words/{id}` | 软停用，保留词条 | 无 |

word 去首尾空白后 1—64 字符，归一化后须包含有效文字或数字，重复词（含停用词）不可新增。category：1 政治、2 色情、3 辱骂、4 广告、5 学术不端、6 其他；**与举报原因编号不同**。level 默认 1：直接拦截；2：转人工复审。PUT 省略 level 也会按 1 处理，不是保留旧等级。修改后只重载当前实例词库，不具备多实例同步机制。

## 本地请求示例

公开请求，无需 token：

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/v1/campuses
```

登录后，用自己的 token 查询当前身份。不要把真实 token 贴进 Issue、截图或代码仓库：

```powershell
$requestHeaders = @{ Authorization = 'Bearer 替换为自己的token' }
Invoke-RestMethod http://127.0.0.1:8080/api/v1/auth/me -Headers $requestHeaders
```

健康端点不属于公开 API：生产仅在容器内部 `127.0.0.1:9001` 提供 liveness/readiness，访问方式见[部署与运维](部署与运维.md)。
