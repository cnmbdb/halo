# Commerce plugin

Independent Halo 2.26 plugin paired with the Earth commerce theme. Implements product/category models, public catalog and quote routes, and console product/category editors. Earth includes a browser-local cart, multi-item quote, checkout, product detail and order pages. The plugin persists buyer orders and reserves inventory. Epay-compatible payment forms and signed callbacks are implemented when configured. Digital SKUs store one-time codes; verified payment atomically consumes the reserved codes, and the buyer-only delivery route reveals them only for paid orders. Physical shipment management tracks carrier, tracking number and delivery confirmation; mixed orders complete only after all physical lines are delivered.

## Build

1. In ui/, run pnpm install --frozen-lockfile, pnpm build and pnpm lint using the package manager pinned in package.json.
2. From the plugin root, run ./gradlew build with JDK 21. Gradle copies ui/build/dist into the console resources and refuses to package a missing UI bundle.

Version pins live in gradle.properties. The dependency BOM is the released Halo 2.26.0 platform. Upstream scaffold: https://github.com/halo-dev/plugin-starter, GPL-3.0 (LICENSE preserved).

## Catalog and admin API

- GET /shop: theme homepage, optional category filter.
- GET /shop/products/{slug}: published product detail or 404.
- GET /shop/cart: browser-local cart page.
- GET /shop/checkout: server-priced confirmation preview.
- GET /shop/api/quote: JSON quote; repeat product, sku and quantity query parameters in matching order for multiple selections (maximum 100). Duplicate selections are combined before stock validation, and active unexpired reservations are subtracted from sellable stock; missing/unpublished products return 404, malformed carts and insufficient stock return 400. Quote requests do not create orders or reservations.
- POST /shop/api/orders: authenticated buyer order creation from product/SKU/quantity selections; physical goods require recipient, phone and address. Server prices all lines and reserves stock per product. A failed reservation triggers best-effort compensation and marks the order reservation-failed.
- GET /shop/api/orders and GET /shop/api/orders/{name}: buyer-only order history/details. Orders not owned by the caller return 404.
- GET /shop/api/orders/{name}/digital-delivery: returns one-time codes only to the owning buyer after the order is paid; codes are allocated atomically with inventory commit.
- GET /shop/api/admin/orders?page=1: paged order list for users with commerce:orders:manage.
- POST /shop/api/admin/orders/{name}/shipment: manager-only carrier/tracking update; setting delivered=true confirms delivery after a shipment record exists. Mixed orders complete only after all physical lines are delivered.
- POST /shop/api/orders/{name}/cancel: owner-only cancellation while awaiting payment; releases inventory reservations.
- GET /shop/orders: buyer order history page with cancellation and Epay buttons.
- GET /shop/api/orders/{name}/payment?type=alipay|wxpay: buyer-scoped signed form POST to the configured Epay gateway.
- GET /shop/payment/notify: signature- and amount-verified callback; duplicate callbacks are idempotent, late success changes the order to REFUND_REQUIRED and never commits inventory.
- GET /shop/api/products: public projection of published products, optional category filter.
- Halo extension API /apis/commerce.halo.run/v1alpha1/products: protected product management.
- Halo extension API /apis/commerce.halo.run/v1alpha1/productcategories: protected category management.
- Console → 商城 → 订单管理: review buyer, address and line fulfillment, register shipment and confirm delivery. The commerce management role template includes separate product/category and order management UI permissions.

Resource body uses apiVersion=commerce.halo.run/v1alpha1, kind=Product, metadata.name and spec. spec contains title, slug, type (PHYSICAL/DIGITAL), state (DRAFT/PUBLISHED/ARCHIVED), description, images, categories, skus and featured. Each SKU contains id, title, priceMinor and stock; digital SKUs also accept a digitalCodes array. The console editor accepts one code per line. A published digital SKU must have at least one code per unit of stock. Codes are removed from SKU inventory when payment commits and stored against the order for repeat buyer reads. Public catalog responses use a separate SKU projection and never include codes or delivery status. Prices are integer minor currency units. Updates retain metadata.version for optimistic concurrency. Negative prices/stock, invalid slugs, empty or duplicate SKUs are rejected during deserialization.

The console UI manages products with pagination and categories with ordering. Orders store buyer and shipping data; extension permissions grant order reads/writes only to the commerce management role, while public buyer routes enforce ownership. The role template commerce-products-manage grants product/category get/list/create/update and the commerce:products:manage UI permission. No anonymous write access is granted.

See INSTALL.md for local source-development and packaged installation. The Halo 2.26 development container loads the source plugin directly. Current runtime verification confirms plugin start/stop, complete Earth `/shop` rendering and the public product endpoint; authenticated console persistence, product entry and complete buyer/admin order flows remain to be exercised.
