# AOSTE 商城主题与 Commerce 插件

当前开发源码：`development/theme-earth/`（主题 AOSTE，ID `theme-aoset`）和 `plugins/plugin-commerce/`。`shop/` 保留分支原有早期实现，当前运行环境使用 Commerce 插件，请勿同时安装旧插件。

支持实物及数字商品、分类图片、商品富文本 HTML、库存预留与取消释放、订单管理、易支付微信/支付宝/USDT、按商品允许游客购买。游客订单绑定浏览器会话；未勾选时仍需登录。Markdown 解析尚未启用，官方支付及非易支付渠道尚未接通。

## 本地构建

需要 Node.js、pnpm、Docker（或 JDK 21）。先构建插件控制台，再构建插件：

```sh
pnpm -C plugins/plugin-commerce/ui install --frozen-lockfile
pnpm -C plugins/plugin-commerce/ui build
docker run --rm -v "$PWD:/workspace" -v halo-gradle-cache:/root/.gradle -w /workspace/plugins/plugin-commerce eclipse-temurin:21-jdk ./gradlew build
pnpm -C development/theme-earth install --frozen-lockfile
pnpm -C development/theme-earth build
mkdir -p themes/theme-aoset
cp development/theme-earth/theme.yaml development/theme-earth/settings.yaml themes/theme-aoset/
cp -R development/theme-earth/templates development/theme-earth/i18n themes/theme-aoset/
cp .env.cftun.example .env.cftun.local
docker compose -f compose.dev.yaml up -d halo
```

首次进入 http://localhost:8090/system/setup 初始化 Halo。后台安装主题构建生成的 ZIP 并启用 AOSTE。商品管理、订单和支付配置在主题设置内。主题配置沿用 `theme-earth-configMap` 兼容已有数据。

## Cloudflare Tunnel

把令牌填写到本地 `.env.cftun.local` 的 `TUNNEL_TOKEN`，该文件不会提交。Cloudflare 服务源地址配置为 `http://localhost:8090`，隧道容器共享 Halo 网络。

```sh
docker compose -f compose.dev.yaml up -d cftun
```

重启或重建 Halo 后，运行 `docker compose -f compose.dev.yaml up -d --force-recreate cftun` 重新连接网络。H2 数据在 Docker 命名卷中；此源码提交不包含本地数据库、商品数据、图片附件或商户密钥。

## 验证

本次源码通过插件 Gradle build、前端类型检查及 25 项前端测试；实测游客下单、会话隔离与取消释放库存。真实付款与交付仍需完整支付配置后验证。公网通知地址、返回地址在主题支付设置中填写；本地回调地址只用于跳转测试。
