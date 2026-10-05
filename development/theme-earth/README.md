# AOSTE

AOSTE 是基于 Halo Earth 扩展开发的商城主题，支持实物与数字商品，配合本仓库的 Commerce 插件使用。

[项目源码](https://github.com/cnmbdb/halo/tree/aotes) · [问题反馈](https://github.com/cnmbdb/halo/issues)

## 主题信息

| 项目 | 内容 |
| --- | --- |
| 显示名称 | AOSTE |
| 主题 ID | `theme-aoset` |
| 当前版本 | `1.18.0` |
| Halo 版本要求 | `>=2.22.0`；本地开发使用官方 `halohub/halo:2.26` |
| 设置标识 | `theme-aoset-setting` |
| 配置存储 | `theme-earth-configMap`，保留此标识以兼容已有配置 |
| 源码目录 | `development/theme-earth/` |
| 本地运行目录 | `themes/theme-aoset/` |

源码目录沿用历史名称，安装后的主题名称和 ID 已改为 AOSTE / `theme-aoset`。

## 功能

- 商城首页支持左侧大图轮播、右侧两张展示图，并可设置图片、跳转链接和布局比例。
- 分类管理整合在「商城首页」的商品分类区块中；每个分类可配置名称、排序和正方形图片，支持 GIF、PNG、JPEG、WebP 等浏览器图片格式。
- 商品卡片采用左图右文的紧凑布局，分类按钮采用半透明毛玻璃样式，支持纯黑暗色模式。
- 商品管理支持分类关联、多张商品图片、SKU、价格、库存和数字兑换码；详情支持 HTML 富文本及源码编辑，Markdown 编辑暂未接入。
- 每个商品可勾选「允许未登录游客购买」，实际下单接口会校验该许可；默认需要登录。游客订单通过当前浏览器会话识别，更换浏览器后不能直接访问原订单。
- 订单支持库存预留、取消及超时释放、付款后扣减库存、数字商品交付和实物发货管理。
- 易支付支持微信、支付宝和 USDT 渠道。官方支付、虎皮椒、epusdt、tokenpay 的选项尚未完成接口接入。

商品、订单和支付功能由 Commerce 插件提供；仅安装主题不能完成交易。

## 构建与安装

从仓库根目录运行：

```sh
pnpm -C development/theme-earth install --frozen-lockfile
pnpm -C development/theme-earth build
```

构建生成 `development/theme-earth/dist/theme-aoset-1.18.0.zip`。在 Halo 后台「外观 → 主题」上传安装包并启用 AOSTE，同时安装并启用 `plugins/plugin-commerce/` 中的 Commerce 插件。

完整的插件构建和 Docker 开发启动步骤见[仓库 README](../../README.md)。旧 `shop/` 目录是早期实现，开发当前主题请使用 `development/theme-earth/` 和 `plugins/plugin-commerce/`。

## 后台配置

在「外观 → 主题」中统一进入商品管理、订单管理、支付管理和商城首页配置，使用页面底部的保存按钮提交修改。

支付管理的易支付配置区块默认折叠，展开后填写接口地址、商户 ID 和商户密钥，再开启对应支付方式并选择易支付。密钥由插件保存到 Halo Secret，留空时保持已有密钥，不应写入主题源码。

本地测试可自动使用本机通知和返回地址；通过公网域名接收支付结果时，需要配置可访问的通知地址和返回地址。只有支付平台通知校验通过后，订单才会确认付款并执行交付。

商城地址为 `/shop`。如需显示在网站顶部导航，在 Halo「菜单」的主菜单中添加指向 `/shop` 的商城菜单项。

## 本地主题开发

开发容器使用官方 Halo 镜像和内置 H2 数据库。主题源码编译后，需要同步生成的模板到运行目录：

```sh
pnpm -C development/theme-earth build
mkdir -p themes/theme-aoset
cp development/theme-earth/theme.yaml development/theme-earth/settings.yaml themes/theme-aoset/
rsync -a development/theme-earth/templates/ themes/theme-aoset/templates/
rsync -a development/theme-earth/i18n/ themes/theme-aoset/i18n/
docker compose -f compose.dev.yaml up -d halo
```

开发配置已关闭 Thymeleaf 模板缓存，模板同步后刷新页面即可检查效果。修改插件代码时还需要重新构建插件并重启 Halo。

数据库、运行配置、商户密钥和 Cloudflare Tunnel Token 不属于主题源码，不应提交到 Git。

## 开源来源

本主题基于 [Halo 官方 Earth 主题](https://github.com/halo-dev/theme-earth) 扩展，保留原作者版权及 GPL-3.0 许可证。许可证见 [LICENSE](./LICENSE)。
