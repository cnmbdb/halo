# AOSTE 主题与商城

AOSTE 是一款用于 Halo 的网站主题，适合展示文章，也可以搭配随包提供的商城插件使用。首页内容可以在 Halo 后台按自己的需要调整，不必修改代码。

在线体验：[https://halo-deve.txsw.top](https://halo-deve.txsw.top)

## 下载包里有什么

下载 `AOSTE-1.19.14-with-commerce-plugin.zip` 后，请先在电脑上解压。里面有两个需要分别安装的文件：

- `theme-aoset-1.19.14.zip`：AOSTE 网站主题。
- `plugin-commerce-0.2.9.jar`：商城插件。

外层压缩包只是把主题和插件放在一起，不能直接作为一个主题上传。

## 安装前

请使用 Halo 2.26 或更高版本。商城插件需要 Halo 2.26 及以上版本；本组合包按这个版本要求准备。

## 安装步骤

1. 下载组合包并解压。
2. 在 Halo 后台打开「插件」，上传 `plugin-commerce-0.2.9.jar`，安装并启用商城插件。
3. 打开「外观 → 主题」，上传 `theme-aoset-1.19.14.zip`，然后启用 AOSTE。
4. 超级管理员进入主题后台的「更新」页，填写 ETS 授权码并「保存并验证」；也可打开 `/console/aoste/updates`。
5. 进入主题的「首页布局」，配置首页内容并保存。

主题和插件是两个独立的安装项，但从主题 1.19.14、插件 0.2.9 起共用授权。请安装并启用配套插件，在主题后台更新页填写一次授权码；未授权时主题页面、商品浏览和新下单停止服务。后台授权入口、已有订单查询与交付、支付回调保留。

## 主题与插件共用授权

如无授权码请[前往获取](https://hfz.pw/shop/2678.html)（免费）。主题更新页与插件设置使用同一个授权入口和服务端状态，保存一次即可同步生效。

授权码保存在 Halo Secret 中，不在状态接口回显。授权被撤销、过期或授权服务无法验证时，停止对外服务；有效校验最多缓存一分钟。升级前请备份站点，建议先升级插件、完成授权，再升级主题。后台更新仍需你手动确认。

详细规则、部署顺序和测试见 [统一授权说明](release/AUTHORIZATION.md)。

## 独立更新

插件列表会自动检查 GitHub Releases。有新版本时，Aoste主题插件旁会显示「有新版本」「查看详情」和「立即更新」。点击「立即更新」后确认，即可下载、校验并升级插件。检查失败会明确显示原因入口。

安装并启用商城插件后，在「主题」页面点右上角「主题管理」，再从 AOSTE 详情的「升级」打开 GitHub Releases 更新页。该页会显示主题和插件的当前版本、最新版本、Halo 版本要求与更新说明；有新版本时可分别升级。也可以打开「插件 → Aoste主题插件 → 设置 → 独立更新」，或直接打开 `/console/aoste/updates`。

更新来自 [GitHub Releases](https://github.com/cnmbdb/halo/releases)，不需要在 Halo 官方应用市场上架。后台从仓库的静态更新清单读取 AOSTE 的正式发布版本，不消耗 GitHub API 的未认证请求配额。检查结果缓存一分钟，同一页面的并发检查会合并。后台只读取 AOSTE 的正式发布版本，不会把 Halo 主程序的版本误认为主题更新。下载的安装包通过大小与 SHA-256 校验后，才会交给 Halo 自带的升级接口。

主题升级保留主题配置，但会覆盖自行修改过的主题文件。插件升级时商城服务会短暂重启，商品、订单和已交付卡密数据保留。升级前可在更新页导出主题配置；需要完整恢复时，请同时使用 Halo 的站点备份功能。

检查更新时浏览器需要能连接 GitHub，下载安装包时 Halo 服务端也需要能连接 GitHub。升级操作需要超级管理员权限。检查或下载失败时会显示原因，校验失败的包不会被安装。旧版本首次接入时，请先从发布页下载新主题包和插件包，分别使用 Halo 自带的「升级」功能安装；后续即可在「独立更新」页操作。

## 首页可以这样设置

首页由四个区块组成，可在「首页布局」里分别设置是否显示：

- **幻灯片**：添加图片、设置展示高度、自动播放和图片点击后的跳转地址。
- **自定义卡片**：添加图片、GIF 或视频，设置卡片尺寸、显示数量和点击后的链接。
- **文章分类**：选择要展示的分类并设置胶囊按钮图片。访客点击分类后，首页下方的文章列表会随之筛选；「全部」按钮也可以单独设置图片。
- **文章卡片列表**：展示 Halo 中的文章。文章布局和每页数量沿用 Halo 的文章相关设置。

首页分类按钮负责筛选首页文章，不会把访客带离首页。

## 页面预览

**首页分类筛选与文章卡片**

![AOSTE 首页分类筛选与文章卡片](./images/home-categories-and-articles.png)

**商城商品与分类**

![AOSTE 商城商品与分类](./images/shop-products-and-categories.png)

**商城订单与支付方式**

![AOSTE 商城订单与支付方式](./images/shop-orders-and-payment.png)

## 关于商城和支付

商城页面由配套插件提供。启用插件后，可按站点需要配置商品和订单。在线支付还需要你自己准备并填写支付网关信息；组合包不包含商户账号或支付密钥。正式开放前，建议先在自己的站点检查商品展示、下单和支付配置。

## 商城插件 0.1.1 修复

修复了新订单预占库存时清空历史卡密交付记录的问题。已付款订单的卡密会在后续下单、取消订单和重复支付回调中保留；对应的已交付预占记录也会保留，以支持迟到的重复回调。

已通过 91 项自动化测试，覆盖连续下单、取消订单、重复与迟到的支付回调、并发支付及库存预占重试。旧版本已经清空的卡密记录，需要从丢失前的备份恢复。

## 主题 1.19.10 / Aoste主题插件 0.2.7

插件名称统一为「Aoste主题插件」，使用主题同款图标，并在插件设置和 Halo 自带的主题升级弹窗中提供独立更新入口。主题的网站地址改为 https://hfz.pw/archives/2675。更新通过 Halo 插件扩展点接入，不修改 Halo 核心；大于 256 KB 的安装包也可正常下载校验。

## 反馈与源码

如果遇到问题或想查看项目源码，可以前往 [AOSTE 项目与问题反馈页](https://github.com/cnmbdb/halo)。

<!-- aoste-release-source
{
  "tag_name": "aoste-v1.19.14",
  "html_url": "https://github.com/cnmbdb/halo/releases/tag/aoste-v1.19.14",
  "draft": false,
  "prerelease": false,
  "body": "AOSTE 主题 1.19.14 · 商城插件 0.2.9\n\n主题与插件共用 ETS 授权入口：\n- 后台主题更新页与插件设置共用一份授权码，保存并验证后同时生效。\n- 如无授权码请前往获取（免费）：https://hfz.pw/shop/2678.html\n- 未授权时由服务端禁止主题页面、商品浏览与新下单；后台授权/更新入口、已有订单查询、交付与支付回调保留。\n- 授权码保存在 Halo Secret 中；授权失效、撤销或到期后停止对外服务。服务端最多缓存一分钟；授权服务故障时校验失败。\n- 主题依赖 Aoste主题插件 >=0.2.9，插件停用或缺失时显示授权提示页。\n\n部署前先发布 ETS-admin 的 /api/authorization/aoste，再升级插件与主题并填写授权码。\n先在测试站验证授权，再应用生产站点；未授权的站点升级后将停止主题与商城服务。\n升级前请备份站点和主题自定义文件。\n\n```aoste-update\n{\n  \"schemaVersion\": 1,\n  \"theme\": {\n    \"id\": \"theme-aoset\",\n    \"version\": \"1.19.14\",\n    \"asset\": \"theme-aoset-1.19.14.zip\",\n    \"requires\": \">=2.26.0\",\n    \"sha256\": \"42926f8cbcd0cc6a733797125f0ca4a956df10ad480b5da62d1a7c90579167a3\"\n  },\n  \"plugin\": {\n    \"id\": \"commerce\",\n    \"version\": \"0.2.9\",\n    \"asset\": \"plugin-commerce-0.2.9.jar\",\n    \"requires\": \">=2.26.0\",\n    \"sha256\": \"bff3ec64de055744c765dd4cc98b03a0b18c858f65eb52c498fecaf8df0e68f8\"\n  }\n}\n```\n",
  "assets": [
    {
      "url": "https://api.github.com/repos/cnmbdb/halo/releases/assets/651476494",
      "id": 651476494,
      "node_id": "RA_kwDOU14Bjc4m1L4O",
      "name": "AOSTE-1.19.14-with-commerce-plugin.zip",
      "label": "",
      "uploader": {
        "login": "github-actions[bot]",
        "id": 41898282,
        "node_id": "MDM6Qm90NDE4OTgyODI=",
        "avatar_url": "https://avatars.githubusercontent.com/in/15368?v=4",
        "gravatar_id": "",
        "url": "https://api.github.com/users/github-actions%5Bbot%5D",
        "html_url": "https://github.com/apps/github-actions",
        "followers_url": "https://api.github.com/users/github-actions%5Bbot%5D/followers",
        "following_url": "https://api.github.com/users/github-actions%5Bbot%5D/following{/other_user}",
        "gists_url": "https://api.github.com/users/github-actions%5Bbot%5D/gists{/gist_id}",
        "starred_url": "https://api.github.com/users/github-actions%5Bbot%5D/starred{/owner}{/repo}",
        "subscriptions_url": "https://api.github.com/users/github-actions%5Bbot%5D/subscriptions",
        "organizations_url": "https://api.github.com/users/github-actions%5Bbot%5D/orgs",
        "repos_url": "https://api.github.com/users/github-actions%5Bbot%5D/repos",
        "events_url": "https://api.github.com/users/github-actions%5Bbot%5D/events{/privacy}",
        "received_events_url": "https://api.github.com/users/github-actions%5Bbot%5D/received_events",
        "type": "Bot",
        "user_view_type": "public",
        "site_admin": false
      },
      "content_type": "application/zip",
      "state": "uploaded",
      "size": 6697763,
      "digest": "sha256:35895d6b1c19c2eda1db9ac3c1f5ee4908a3b3764f91121ee61ff107a5b5efe2",
      "download_count": 0,
      "created_at": "2026-10-17T20:09:32Z",
      "updated_at": "2026-10-17T20:09:33Z",
      "browser_download_url": "https://github.com/cnmbdb/halo/releases/download/aoste-v1.19.14/AOSTE-1.19.14-with-commerce-plugin.zip"
    },
    {
      "url": "https://api.github.com/repos/cnmbdb/halo/releases/assets/651476490",
      "id": 651476490,
      "node_id": "RA_kwDOU14Bjc4m1L4K",
      "name": "plugin-commerce-0.2.9.jar",
      "label": "",
      "uploader": {
        "login": "github-actions[bot]",
        "id": 41898282,
        "node_id": "MDM6Qm90NDE4OTgyODI=",
        "avatar_url": "https://avatars.githubusercontent.com/in/15368?v=4",
        "gravatar_id": "",
        "url": "https://api.github.com/users/github-actions%5Bbot%5D",
        "html_url": "https://github.com/apps/github-actions",
        "followers_url": "https://api.github.com/users/github-actions%5Bbot%5D/followers",
        "following_url": "https://api.github.com/users/github-actions%5Bbot%5D/following{/other_user}",
        "gists_url": "https://api.github.com/users/github-actions%5Bbot%5D/gists{/gist_id}",
        "starred_url": "https://api.github.com/users/github-actions%5Bbot%5D/starred{/owner}{/repo}",
        "subscriptions_url": "https://api.github.com/users/github-actions%5Bbot%5D/subscriptions",
        "organizations_url": "https://api.github.com/users/github-actions%5Bbot%5D/orgs",
        "repos_url": "https://api.github.com/users/github-actions%5Bbot%5D/repos",
        "events_url": "https://api.github.com/users/github-actions%5Bbot%5D/events{/privacy}",
        "received_events_url": "https://api.github.com/users/github-actions%5Bbot%5D/received_events",
        "type": "Bot",
        "user_view_type": "public",
        "site_admin": false
      },
      "content_type": "application/java-archive",
      "state": "uploaded",
      "size": 1924695,
      "digest": "sha256:bff3ec64de055744c765dd4cc98b03a0b18c858f65eb52c498fecaf8df0e68f8",
      "download_count": 0,
      "created_at": "2026-10-17T20:09:32Z",
      "updated_at": "2026-10-17T20:09:33Z",
      "browser_download_url": "https://github.com/cnmbdb/halo/releases/download/aoste-v1.19.14/plugin-commerce-0.2.9.jar"
    },
    {
      "url": "https://api.github.com/repos/cnmbdb/halo/releases/assets/651476491",
      "id": 651476491,
      "node_id": "RA_kwDOU14Bjc4m1L4L",
      "name": "theme-aoset-1.19.14.zip",
      "label": "",
      "uploader": {
        "login": "github-actions[bot]",
        "id": 41898282,
        "node_id": "MDM6Qm90NDE4OTgyODI=",
        "avatar_url": "https://avatars.githubusercontent.com/in/15368?v=4",
        "gravatar_id": "",
        "url": "https://api.github.com/users/github-actions%5Bbot%5D",
        "html_url": "https://github.com/apps/github-actions",
        "followers_url": "https://api.github.com/users/github-actions%5Bbot%5D/followers",
        "following_url": "https://api.github.com/users/github-actions%5Bbot%5D/following{/other_user}",
        "gists_url": "https://api.github.com/users/github-actions%5Bbot%5D/gists{/gist_id}",
        "starred_url": "https://api.github.com/users/github-actions%5Bbot%5D/starred{/owner}{/repo}",
        "subscriptions_url": "https://api.github.com/users/github-actions%5Bbot%5D/subscriptions",
        "organizations_url": "https://api.github.com/users/github-actions%5Bbot%5D/orgs",
        "repos_url": "https://api.github.com/users/github-actions%5Bbot%5D/repos",
        "events_url": "https://api.github.com/users/github-actions%5Bbot%5D/events{/privacy}",
        "received_events_url": "https://api.github.com/users/github-actions%5Bbot%5D/received_events",
        "type": "Bot",
        "user_view_type": "public",
        "site_admin": false
      },
      "content_type": "application/zip",
      "state": "uploaded",
      "size": 4787298,
      "digest": "sha256:42926f8cbcd0cc6a733797125f0ca4a956df10ad480b5da62d1a7c90579167a3",
      "download_count": 0,
      "created_at": "2026-10-17T20:09:32Z",
      "updated_at": "2026-10-17T20:09:33Z",
      "browser_download_url": "https://github.com/cnmbdb/halo/releases/download/aoste-v1.19.14/theme-aoset-1.19.14.zip"
    },
    {
      "url": "https://api.github.com/repos/cnmbdb/halo/releases/assets/651476493",
      "id": 651476493,
      "node_id": "RA_kwDOU14Bjc4m1L4N",
      "name": "updates.json",
      "label": "",
      "uploader": {
        "login": "github-actions[bot]",
        "id": 41898282,
        "node_id": "MDM6Qm90NDE4OTgyODI=",
        "avatar_url": "https://avatars.githubusercontent.com/in/15368?v=4",
        "gravatar_id": "",
        "url": "https://api.github.com/users/github-actions%5Bbot%5D",
        "html_url": "https://github.com/apps/github-actions",
        "followers_url": "https://api.github.com/users/github-actions%5Bbot%5D/followers",
        "following_url": "https://api.github.com/users/github-actions%5Bbot%5D/following{/other_user}",
        "gists_url": "https://api.github.com/users/github-actions%5Bbot%5D/gists{/gist_id}",
        "starred_url": "https://api.github.com/users/github-actions%5Bbot%5D/starred{/owner}{/repo}",
        "subscriptions_url": "https://api.github.com/users/github-actions%5Bbot%5D/subscriptions",
        "organizations_url": "https://api.github.com/users/github-actions%5Bbot%5D/orgs",
        "repos_url": "https://api.github.com/users/github-actions%5Bbot%5D/repos",
        "events_url": "https://api.github.com/users/github-actions%5Bbot%5D/events{/privacy}",
        "received_events_url": "https://api.github.com/users/github-actions%5Bbot%5D/received_events",
        "type": "Bot",
        "user_view_type": "public",
        "site_admin": false
      },
      "content_type": "application/json",
      "state": "uploaded",
      "size": 459,
      "digest": "sha256:d00b837725683a4d05bab2845e098e9dba50d17ff6bee71ce4aad844cc89edd1",
      "download_count": 0,
      "created_at": "2026-10-17T20:09:32Z",
      "updated_at": "2026-10-17T20:09:33Z",
      "browser_download_url": "https://github.com/cnmbdb/halo/releases/download/aoste-v1.19.14/updates.json"
    }
  ]
}
-->
