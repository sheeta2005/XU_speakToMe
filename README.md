# 西邮点评家（speak-to-me）

校园美食点评微信小程序，包含地点与菜品浏览、评分点评、热门榜单、个人中心、举报，以及人工复审、对象和敏感词管理。

后端为 Java 21 + Spring Boot 3.2 + MyBatis-Plus + MySQL 8 + Redis 7；前端为原生微信小程序，当前没有 Vant 或其他 npm 依赖。管理页面随小程序提供，没有独立 Web 管理站点。

## 本地快速体验（10/20）

准备 Git、Docker 与 Compose v2+；Windows 使用 Docker Desktop 的 Linux 容器模式。首次构建需联网下载镜像和依赖，无需在宿主机安装 Java、Maven、MySQL 或 Redis。

```powershell
git clone https://github.com/sheeta2005/XU_speakToMe.git
cd XU_speakToMe
# 第四批合并前请切换到交付分支。
git switch codex/quickstart-2026-10-20
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/init-env.ps1
docker compose up -d --build --wait
```

Linux/macOS 将初始化命令换为 `sh scripts/init-env.sh`，需安装 OpenSSL。后续 Compose 命令相同。

访问 `http://127.0.0.1:8080/api/v1/campuses`，应返回 `code: 0` 和示例校区。可运行 `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-demo.ps1` 验证真实数据库、Redis 会话、权限、点评、复审和评分；脚本会创建并删除一条演示点评。

微信开发者工具导入 `miniprogram/`，使用测试号或游客 AppID，本机调试时在工具本地设置中开启“不校验合法域名、TLS 版本以及 HTTPS 证书”。开发版默认连接本机 8080，登录页提供演示同学和管理员；首次登录选择校区即可体验。评论由管理员在“我的 → 管理工作台 → 人工复审”中处理。

演示身份固定且共享，禁止公开部署 demo 环境。演示不调用真实微信服务，评论全部转人工复审。体验版/正式版不显示演示入口，后端禁止 demo 与其他环境混用。

详细配置、真实微信接入、管理员初始化、数据持久化及常见问题见[快速开始](docs/快速开始.md)。

## 环境要求

- JDK 21（LTS）
- Maven 3.9+
- MySQL 8.0（utf8mb4）
- Redis 7.x
- 微信小程序 AppID / AppSecret（真实接入必需，本地 demo 无需）
- 微信公众平台后台配置：request 合法域名指向后端域名（如 https://api.xiyou-review.com）

## 工程结构（后端 Maven 多模块）

```
speakToMe/
├─ pom.xml                        # 父 POM：版本与依赖统一管理
├─ speakToMe-common/              # 公共模块：错误码、异常、AES 工具
├─ speakToMe-framework/           # 框架模块：全局异常、登录拦截器、限流切面、配置
├─ speakToMe-user/                # 用户体系：微信登录鉴权、Redis Session、校区资料
├─ speakToMe-content/             # 内容安全：敏感词 DFA、微信审核封装、举报
├─ speakToMe-canteen/             # 美食点评（一期核心）：地点/档口/菜品、点评、排行
├─ speakToMe-admin/               # 管理端：对象维护、举报处理、复审、敏感词管理
└─ speakToMe-app/                 # 主应用、配置校验、独立 demo 环境
```

依赖方向（严格单向）：`app -> admin -> canteen/user/content -> framework -> common`。
二期新增 trade 等模块只需依赖 framework + common 即可接入，用户/审核体系天然复用。

## 初始化数据库

```bash
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS speak_to_me DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -uroot -p speak_to_me < sql/init/01-schema.sql
mysql -uroot -p speak_to_me < sql/init/02-example-data.sql
```

以上脚本仅用于空数据库；建表和示例数据已拆分。Compose 首次创建 MySQL 数据卷时自动执行，后续启动保留数据。旧 `sql/schema.sql` 含 DROP TABLE，不能作为升级脚本在持久化数据库上重复执行。

## 配置

使用环境变量注入，禁止将真实凭证写进受版本管理的 YAML。Compose 自动读取 `.env`；Java/IDE 不自动读取 `.env`。本机配置可参考 `deploy/application-local.yml.example`，复制到仓库根目录并使用 local Profile。

| 环境变量 | 说明 |
|---|---|
| MYSQL_HOST / MYSQL_PORT / MYSQL_USER / MYSQL_PASSWORD | 数据库连接 |
| REDIS_HOST / REDIS_PORT / REDIS_PASSWORD | Redis 连接 |
| WX_APPID / WX_SECRET | 微信小程序凭证 |
| AES_KEY | openid/手机号加密密钥（32 字节随机值 Base64，`openssl rand -base64 32` 生成） |

默认环境 prod 要求 AES、数据库及微信凭证有效配置；demo 仅免除微信凭证，仍使用真实 MySQL 和 Redis。AES 密钥需与数据配套备份，不能随意替换。小程序环境地址在 `miniprogram/config.js`，体验/正式版的 `api.example.com` 必须替换成自己的 HTTPS 域名。

## 启动

```bash
# 宿主机开发需 JDK 21；Wrapper 自动下载 Maven 3.9.10 并校验 SHA-256。
.\mvnw.cmd -B package
# 先配置环境变量，再运行；不在聚合工程直接执行 -am spring-boot:run。
java -jar speakToMe-app/target/speak-to-me.jar --spring.profiles.active=local
# 前端逻辑测试需 Node.js 22+，无 npm 安装步骤。
node --test tests/miniprogram/*.test.cjs
```

启动后访问 `http://localhost:8080/api/v1/campuses` 验证服务是否就绪（该接口无需登录）。

## 小程序端

小程序工程位于 `miniprogram/`。真实微信接入时设置相同的前后端 AppID，关闭 `config.js` 的开发版 demo 开关，并填写各环境接口地址。日间/夜间主题通过 CSS 变量与 theme.json 实现。当前有 12 个页面；JS 逻辑测试不能代替开发者工具编译和真机验收。

## 生产部署边界

- 正式部署需 HTTPS、合法域名、数据备份和监控；当前 Compose 面向本地演示及联调，端口只绑定回环地址。
- 敏感信息（openid/手机号）AES-256-GCM 加密 + SHA-256 摘要索引
- 点评用户限流使用 Redis Lua；代理层 IP 限流需由部署者配置。
- 真实环境本地敏感词预检和微信内容安全审核并用；日志已有写入和过期删除，删除前独立归档尚待完善。
- `deploy/nginx.conf` 是需要配置域名、证书与 CDN 白名单的历史模板；未配置白名单会拒绝 API 请求，不宜直接照搬。
- `docker compose down` 保留命名卷；`down -v` 会删除数据，不是排错或升级步骤。
- 真实微信联调、真机、CI、数据库迁移、生产运维、许可证及开源规范仍需后续完成，详见[实施计划](docs/分批实施计划.md)与[功能状态](docs/项目功能清单与完成情况.md)。
