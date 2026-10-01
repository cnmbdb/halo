# Halo Shop 开发版

面向 Halo 2.26 社区版的独立商城插件与配套主题。插件使用 Halo Extension 存储商品和订单，与 Halo 运行在同一个容器中，不修改 Halo 核心代码。

## 第一阶段目标

- 实物商品：标题、介绍、图片、人民币分价、库存和上架状态。
- 游客可查看已上架商品；购物车存放在当前浏览器的 `localStorage`。
- 拥有“商城下单”权限的 Halo 用户可提交收货资料和订单。服务端重新读取商品价格并保存订单快照，状态为 `PENDING_CONFIRMATION`。
- 店主通过 Halo 的 Extension API 管理商品、查看订单与人工确认。

当前版本**没有在线支付、库存预留、订单后台界面和游客结算**。订单提交仅表示购买意向；上线收款前需补齐库存并发控制、支付回调、反滥用、订单权限和隐私保护。

## 代码位置

- `src/main/java/run/halo/shop/extension/`：商品与订单数据类型。
- `src/main/java/run/halo/shop/`：商品查询与登录后下单接口。
- `theme/`：可独立安装的 Halo 主题，提供首页、文章页、普通页面和商城页面模板。

## 构建与安装

要求完整 JDK 21。执行 `./gradlew build`，插件包在 `build/libs/`。将 jar 安装到 Halo 插件目录并启用；将 `theme/` 作为主题安装并启用。插件提供 `/shop` 路由，主题提供对应的 `shop.html` 模板。

商品的 Halo Extension 资源为 `shop.cnmbdb.github.io/v1alpha1` 下的 `products`。价格以人民币**分**存储，例如 `1299` 代表 ¥12.99。请只授予可信管理员商品和订单 Extension 的写入权限。

接口：

- `GET /apis/api.shop.cnmbdb.github.io/v1alpha1/products`：公开商品列表。
- `GET /apis/api.shop.cnmbdb.github.io/v1alpha1/products/{name}`：公开商品详情。
- `POST /apis/uc.api.shop.cnmbdb.github.io/v1alpha1/orders`：拥有“商城下单”权限的用户提交订单。

## 下一阶段

完善订单管理界面和订单状态流转；实现原子库存预留和取消释放；接入微信支付或支付宝并校验服务端回调；增加游客购买所需的验证码、限流与订单查询凭证；最后再做生产数据库、备份和公网部署。
