# 西邮点评家（speak-to-me）

西邮校园美食点评微信小程序后端工程。Java 21 + Spring Boot 3.2 + MyBatis-Plus + MySQL 8 + Redis 7。
前端为微信小程序原生 + Vant Weapp（第二阶段引入，目录为 `miniprogram/`）。

## 环境要求

- JDK 21（LTS）
- Maven 3.9+
- MySQL 8.0（utf8mb4）
- Redis 7.x
- 微信小程序 AppID / AppSecret（可先空跑登录以外的接口，登录接口需真实凭证）
- 微信公众平台后台配置：request 合法域名指向后端域名（如 https://api.xiyou-review.com）

## 工程结构（后端 Maven 多模块）

```
speakToMe/
├─ pom.xml                        # 父 POM：版本与依赖统一管理
├─ speakToMe-common/              # 公共模块：Result、错误码、AES/SHA 工具（零依赖）
├─ speakToMe-framework/           # 框架模块：全局异常、登录拦截器、限流切面、配置
├─ speakToMe-user/                # 用户体系：微信登录鉴权、Redis Session、校区资料
├─ speakToMe-content/             # 内容安全：敏感词 DFA、微信审核封装、举报
├─ speakToMe-canteen/             # 美食点评（一期核心）：地点/档口/菜品、点评、排行
├─ speakToMe-admin/               # 管理端：对象维护、举报处理、复审、敏感词管理
└─ speakToMe-app/                 # 启动模块：主应用 + application.yml
```

依赖方向（严格单向）：`app -> admin -> canteen/user/content -> framework -> common`。
二期新增 trade 等模块只需依赖 framework + common 即可接入，用户/审核体系天然复用。

## 初始化数据库

```bash
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS speak_to_me DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -uroot -p speak_to_me < sql/schema.sql
```

schema.sql 可重复执行（DROP + CREATE + 种子数据），种子数据含两校区、5 个美食地点（含"东区校外""雁塔校外"）、档口与菜品示例。

## 配置

编辑 `speakToMe-app/src/main/resources/application.yml`，或直接以环境变量注入（推荐，避免密钥入库）：

| 环境变量 | 说明 |
|---|---|
| MYSQL_HOST / MYSQL_PORT / MYSQL_USER / MYSQL_PASSWORD | 数据库连接 |
| REDIS_HOST / REDIS_PORT / REDIS_PASSWORD | Redis 连接 |
| WX_APPID / WX_SECRET | 微信小程序凭证 |
| AES_KEY | openid/手机号加密密钥（32 字节随机值 Base64，`openssl rand -base64 32` 生成） |

## 启动

```bash
# 方式一：直接运行（自动编译依赖模块）
mvn -pl speakToMe-app -am spring-boot:run

# 方式二：打包部署
mvn clean package -DskipTests
java -jar speakToMe-app/target/speak-to-me.jar
```

启动后访问 `http://localhost:8080/api/v1/campuses` 验证服务是否就绪（该接口无需登录）。

## 小程序端

小程序工程在第二阶段创建于 `miniprogram/`。配置 `project.config.json` 中的 AppID，
`utils/request.js` 中 baseURL 指向后端地址。日间/夜间主题通过 CSS 变量与 theme.json 实现（详见第二阶段说明）。

## 安全约定

- 全站 HTTPS + HSTS；小程序合法域名白名单校验
- 敏感信息（openid/手机号）AES-256-GCM 加密 + SHA-256 摘要索引
- 评论接口双层限流（用户 5 条/分钟、IP 20 次/分钟，Redis Lua 原子实现）
- 本地敏感词 DFA 预检 + 微信 security.msgSecCheck 双保险，审核日志保留 >=60 天
- Nginx 跨域与 XSS 防护模板：`deploy/nginx.conf`（第三阶段提供）
