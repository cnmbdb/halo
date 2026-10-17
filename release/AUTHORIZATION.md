# AOSTE 统一授权

主题 1.19.16 与插件 0.3.1 共用一个授权码。主题后台的更新页、插件设置里的独立更新页、`/console/aoste/updates` 使用同一个表单与服务端状态。

Halo 服务端通过 ETS Pages 项目别名 `https://ets-web-admin.pages.dev/api/authorization/aoste` 校验授权，避免自定义域名的 Bot Fight Mode 挑战后端请求。Docker Cloud Sandbox 使用 deny-all 出口策略时，需单独允许 `ets-web-admin.pages.dev:443`。

## 部署顺序

1. 先部署本机 `ETS-admin` 的 `app/src/app/api/authorization/aoste/route.ts`。
2. ETS 中启用 `plugin_slug=aoste` 的“Aoste主题”产品。当前只读检查确认此记录存在且为免费产品；本次未修改数据库。
3. 先安装/升级 `plugin-commerce-0.3.1.jar`，再升级 `theme-aoset-1.19.16.zip`。
4. 超级管理员打开主题后台更新页，填入 ETS 用户授权码（`ets_ak_...`），保存并验证。
5. 确认主题首页、商城浏览与下单恢复；退出授权页、刷新或换浏览器后状态保持。

获取链接：https://hfz.pw/shop/2678.html 。表单文案为“如无授权码请前往获取（免费）”，仅“前往获取”可点击并在新窗口打开。

升级会改变现有未授权站点的可用性，请先在测试站完成授权；生产站升级前备份。

## ETS 校验规则

`POST /api/authorization/aoste`，JSON 请求 `{ "key": "ets_ak_..." }`。在 Halo 服务端调用，不需要浏览器跨域。

- 复用现有 ETS 账户授权码校验：码必须启用，用户必须为 ACTIVE。撤销/轮换后旧码不再有效。
- 产品必须存在并启用。默认匹配 `plugin_slug=aoste`；可通过 ETS 服务端 `AOSTE_EXTENSION_SLUG` 调整，产品语义始终是 AOSTE。
- 免费产品按 ETS 现有规则授予有效用户使用权限。付费模式必须有启用、未过期的 `extension_users` 记录。
- 成功返回 `valid=true`、`product=aoste`、两个组件标识和到期时间，不返回用户 ID、授权码或数据库错误。
- 不新增数据库表或写入产品数据；授权码使用时间沿用原校验接口记录。

## Halo 保存与限制

- 由超级管理员访问 `GET/PUT/DELETE /shop/api/admin/license`。其他账号返回 401/403。
- 授权码保存至 Halo `Secret/aoste-authorization`，不写入浏览器 localStorage，不在状态接口回显。
- 服务端通过固定的 HTTPS ETS 地址校验，8 秒超时，16 KiB 响应上限；不接受客户端传入校验地址或“已授权”标志。
- 有效结果最多缓存 60 秒，失败最多缓存 10 秒；每次请求仍检查到期时间，重启会重新校验。网络异常且无有效缓存时停止服务。
- 未授权时，服务端拦截 AOSTE 的主题页面、商城商品浏览、新下单和支付发起。返回 HTML 提示或 JSON 403；授权服务异常返回 503。
- 后台、登录、授权/更新接口、已有订单查询、取消、数字交付、订单管理、支付回调保留。已有支付回调继续走原签名验证和交付处理。
- 其他主题页面不受限制。商城独立使用时仍需共用授权。
- 主题包内有服务端模板保护：插件缺失、停用或版本低于 0.2.9 时，24 个主题页面显示授权提示页。
- 更换授权码须先验证成功才覆盖旧码；移除授权即时清除本实例的缓存。多实例共享数据库时，其他实例在下一次读取 Secret 后同步。

站点所有者能够修改主题/插件文件；这属于软件内的授权控制，不提供不可篡改的 DRM，也未增加域名绑定或设备数量限制。

## 构建与验证

安装 JDK 21、Maven 3.9+、Python 3、Node.js。设置 `JAVA_HOME`，Maven 不在 PATH 时设置 `MVN` 为其可执行文件路径。

```sh
python3 release/build.py
python3 release/test-package.py
# 从安装包提取 console/main.js 至 dist/plugin-main.js 后：
node --check dist/plugin-main.js
node release/test-updates.cjs dist/plugin-main.js
node release/test-license-console.cjs dist/plugin-main.js
mvn -q -f release/license-backend/pom.xml dependency:build-classpath -Dmdep.outputFile=/tmp/aoste-license-cp
java -cp "$(cat /tmp/aoste-license-cp)" release/VerifyLicenseTemplates.java dist/theme-aoset-1.19.16.zip
```

构建自动执行授权后端的 15 项测试，包含状态共享、过期、撤销、失败关闭、Secret 保存、无效替换、权限检查、实际请求拦截与恢复入口。原插件后端 class 文件保持字节一致，新增授权扩展通过 Halo 的插件组件索引注册。

ETS 验证命令：在 `ETS-admin/app` 执行 `node --test tests/aoste-authorization.test.cjs` 和 `node node_modules/typescript/bin/tsc --noEmit`。
