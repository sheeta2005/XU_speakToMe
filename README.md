# 西邮点评家

一个校园美食点评微信小程序。可以按校区找食堂、档口和菜品，写点评、看评分与热门榜单，也带了举报和管理页面。

后端用 Java 21、Spring Boot、MyBatis-Plus、MySQL 8 和 Redis 7，前端是原生微信小程序，没有 npm 依赖。

## 本地跑起来

准备 Git、Docker 和 Compose v2。Windows 使用 Docker Desktop 的 Linux 容器模式，首次构建需要联网。

```powershell
git clone https://github.com/sheeta2005/XU_speakToMe.git
cd XU_speakToMe
git switch codex/developer-docs-2026-10-23
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/init-env.ps1
docker compose up -d --build --wait
```

Linux/macOS 把初始化命令换成 `sh scripts/init-env.sh`，需安装 OpenSSL。当前内容在上述分支，合并后可使用主分支。

打开 `http://127.0.0.1:8080/api/v1/campuses`，返回 `code: 0` 即可继续。微信开发者工具导入 `miniprogram/`，本地调试开启“不校验合法域名、TLS 版本以及 HTTPS 证书”。登录页可选演示同学或管理员；点评提交后，在“我的 → 管理工作台”中人工复审。

demo 不调用真实微信服务，固定演示身份只用于本地体验。真实接入需要自己的微信凭证、HTTPS 域名和独立数据环境。

## 文档

- [快速开始与配置](docs/快速开始.md)
- [换学校、改名称](docs/品牌与校区替换.md)
- [架构、接口与数据字典](docs/README.md)
- [部署、备份与恢复](docs/部署与运维.md)
- [当前完成情况](docs/项目功能清单与完成情况.md)
- [参与贡献](CONTRIBUTING.md) · [安全反馈](SECURITY.md) · [更新日志](CHANGELOG.md)

美食点评主流程、管理页和自动检查已具备；真实微信、真机验收和运行截图仍待补齐。交易、课程等预留模块尚未实现。

许可证：**待定**，由维护者另行确认；目前没有正式发布版本。
