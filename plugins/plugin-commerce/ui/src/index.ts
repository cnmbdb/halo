import { definePlugin } from '@halo-dev/console-shared'
import PaymentView from './views/PaymentView.vue'
import ProductsView from './views/ProductsView.vue'
import CategoriesView from './views/CategoriesView.vue'
import OrdersView from './views/OrdersView.vue'
import IntegrationView from './views/IntegrationView.vue'

export default definePlugin({
  components: {
    'commerce-products': ProductsView,
    'commerce-payment': PaymentView,
    'commerce-categories': CategoriesView,
    'commerce-orders': OrdersView,
    'commerce-integration': IntegrationView,
  },
  routes: [
    { parentName: 'Root', route: {
      path: '/commerce/products', name: 'CommerceProducts', component: ProductsView,
      meta: { title: '商品管理', searchable: true, permissions: ['commerce:products:manage'] },
    } },
    { parentName: 'Root', route: {
      path: '/commerce/orders', name: 'CommerceOrders', component: OrdersView,
      meta: { title: '订单管理', searchable: true, permissions: ['commerce:orders:manage'] },
    } },
  ],
})
