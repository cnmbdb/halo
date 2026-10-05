# 本机开发运行

Halo 2.26 开发容器在 http://localhost:8090。首次启动前先完成下方构建步骤（至少完成插件 Gradle build 和主题模板同步），再运行 `docker compose -f compose.dev.yaml up -d`。Earth 模板目录挂载到 Halo，商城插件工程挂载到 `/workspace/plugins/plugin-commerce`。开发容器以 Halo 插件开发模式加载该工程；`development/plugin-commerce-enabled.yaml` 只覆盖容器内的开发描述文件，让本机实例启动时自动启用商城，不会改变打包 JAR 中的默认关闭状态。

本机重新构建后运行 `docker compose -f compose.dev.yaml up -d --force-recreate halo`，Halo 会重载插件工程。确认 `docker logs halo-halo-1` 出现 `Plugin commerce started with state STARTED`，然后访问 http://localhost:8090/shop 和 `/shop/api/products`。

## 修改后重新构建

在仓库根目录运行：

```sh
pnpm -C plugins/plugin-commerce/ui install --frozen-lockfile
pnpm -C plugins/plugin-commerce/ui build
docker run --rm -v "$PWD:/workspace" -v "$HOME/.gradle:/root/.gradle" \
  -w /workspace/plugins/plugin-commerce eclipse-temurin:21-jdk ./gradlew build
cd development/theme-earth
npx --yes pnpm@12.4.2 install --frozen-lockfile
npx --yes pnpm@12.4.2 build
cd ../..
python3 -c 'from pathlib import Path; import shutil; shutil.copytree("development/theme-earth/templates", "themes/theme-aoset/templates", dirs_exist_ok=True)'
```

本机开发时不需要上传 JAR。正式部署时使用构建出的 JAR 上传并启用插件，然后在「外观 → 主题」重新加载 AOSTE 以读取更新后的设置定义。

## 备份本机数据与源码

商城商品、分类、订单和 Halo 设置保存在 `halo_theme-dev-data` 数据卷；主题模板和插件源码来自工作区目录。升级插件或改动数据结构前，先停服并同时备份数据卷与源码。下面命令会在退出时重新启动 Halo：

```sh
mkdir -p backups
archive="halo-theme-dev-$(date +%Y%m%d-%H%M%S).tgz"
docker compose -f compose.dev.yaml stop halo
trap 'docker compose -f compose.dev.yaml start halo' EXIT
docker run --rm -v halo_theme-dev-data:/data:ro -v "$PWD/backups:/backup" \
  eclipse-temurin:21-jdk tar -czf "/backup/$archive" -C /data .
tar --exclude='*/node_modules' --exclude='*/build' --exclude='*/.gradle' \
  --exclude='.pnpm-store' -czf "backups/source-$archive" \
  compose.dev.yaml development plugins/plugin-commerce themes/theme-earth
```

1. 在 Halo Console 的「插件」页面上传 plugins/plugin-commerce/build/libs/plugin-commerce-0.1.0-SNAPSHOT.jar，安装后启用「商城」。
2. 在「外观 → 主题」重新加载 AOSTE 并应用主题。打开主题设置，确认「商城装修」和「商品详情」两组配置可见，修改并保存后重新打开核对持久化值。
3. 打开「商城 → 商品分类」新增分类，再到「商品管理」创建实物或数字商品。数字 SKU 每行录入一个兑换码；已上架数字 SKU 的兑换码数量须不少于库存。
4. 访问 /shop、商品详情、购物车和结账页检查布局。登录买家账号创建订单；管理账号在「商城 → 订单管理」登记承运商和单号，再确认送达；买家订单页应显示物流状态。只有本人可读取自己的订单和数字商品兑换码。

未配置易支付环境变量时，不会产生真实支付。联调需要在本地未跟踪的 .env 中设置 COMMERCE_EPAY_SUBMIT_URL、COMMERCE_EPAY_MERCHANT_ID、COMMERCE_EPAY_MERCHANT_KEY、COMMERCE_EPAY_NOTIFY_URL 和 COMMERCE_EPAY_RETURN_URL；网关与回调使用 HTTPS 公网地址，商户密钥仅传入 Halo 容器环境。已取消或过期订单迟到付款会标为 REFUND_REQUIRED，需要商家人工退款。

禁用插件会停止商城路由并注销模型，不会主动删除商品、分类或订单数据。已在 Halo 2.26 开发容器验证插件启停、完整 `/shop` 页面渲染、主题 CSS 和公开商品 API。构建测试覆盖权限、买家隔离、支付回调、数字码分配、实物发货和混合订单状态。主题设置保存/读回、录入商品后的完整浏览器结账流程仍需通过已登录的 Halo Console 验证。数字文件下载尚未实现。
