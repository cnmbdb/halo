<script setup lang="ts">
import { axiosInstance } from '@halo-dev/api-client'
import { onMounted, ref } from 'vue'

type FulfillmentLine = {
  lineIndex: number; productName: string; skuId: string; type: 'PHYSICAL' | 'DIGITAL'
  state: 'AWAITING_PAYMENT' | 'PROCESSING' | 'DIGITAL_DELIVERED' | 'SHIPPED' | 'DELIVERED'
  carrier?: string; trackingNumber?: string
}
type Order = {
  metadata: { name: string }
  spec: { buyer: string; state: string; totalMinor: number; createdAt: string
    shippingAddress?: { recipient: string; phone: string; address: string }
    lines: Array<{ productTitle: string; skuTitle: string; quantity: number; type: string }>
    fulfillment: FulfillmentLine[] }
}
const orders = ref<Order[]>([])
const page = ref(1)
const total = ref(0)
const busy = ref(false)
const error = ref('')
const selected = ref<Order | null>(null)
const carrier = ref('')
const trackingNumber = ref('')
const delivered = ref(false)

async function load() {
  busy.value = true; error.value = ''
  try {
    const { data } = await axiosInstance.get<{ items: Order[]; total: number }>('/shop/api/admin/orders', { params: { page: page.value } })
    orders.value = data.items; total.value = data.total
  } catch (failure) {
    const status = (failure as { response?: { status: number } }).response?.status
    error.value = status === 401 ? '登录已过期，请重新登录后刷新订单。'
      : status === 403 ? '当前账号没有订单管理权限。'
        : status === 404 ? '订单服务不可用，请检查商城插件是否已启用。'
          : '加载订单失败，请稍后点击刷新重试。'
  }
  finally { busy.value = false }
}
function openShipment(order: Order) {
  selected.value = order
  carrier.value = order.spec.fulfillment.find(line => line.type === 'PHYSICAL' && line.carrier)?.carrier ?? ''
  trackingNumber.value = order.spec.fulfillment.find(line => line.type === 'PHYSICAL' && line.trackingNumber)?.trackingNumber ?? ''
  delivered.value = false
}
async function saveShipment() {
  if (!selected.value) return
  busy.value = true; error.value = ''
  try {
    await axiosInstance.post(`/shop/api/admin/orders/${encodeURIComponent(selected.value.metadata.name)}/shipment`, {
      carrier: carrier.value, trackingNumber: trackingNumber.value, delivered: delivered.value,
    })
    selected.value = null
    await load()
  } catch { error.value = '物流更新失败，请检查订单状态、承运商和单号后重试。' }
  finally { busy.value = false }
}
const stateName: Record<string, string> = {
  AWAITING_PAYMENT: '待付款', PAID: '已付款', FULFILLING: '配送中', COMPLETED: '已完成',
  CANCELLED: '已取消', EXPIRED: '已过期', REFUND_REQUIRED: '待退款', REFUNDED: '已退款', RESERVATION_FAILED: '库存预留失败',
}
onMounted(load)
</script>

<template>
  <section class="commerce-admin">
    <header><div><h1>订单管理</h1><p>查看买家订单、收货信息和逐项履约状态。</p></div><button type="button" :disabled="busy" @click="load">刷新</button></header>
    <p v-if="error" role="alert" class="error">{{ error }}</p>
    <p v-if="!orders.length && !busy && !error">暂无订单</p>
    <article v-for="order in orders" :key="order.metadata.name" class="order">
      <header><strong>{{ order.metadata.name }}</strong><span>{{ order.spec.buyer }}</span>
        <span>{{ stateName[order.spec.state] ?? order.spec.state }}</span>
        <strong>¥{{ (order.spec.totalMinor / 100).toFixed(2) }}</strong></header>
      <p v-for="(line, index) in order.spec.lines" :key="index">
        {{ line.productTitle }} · {{ line.skuTitle }} × {{ line.quantity }}
        <small>履约：{{ stateName[order.spec.fulfillment[index]?.state] ?? order.spec.fulfillment[index]?.state }}</small>
      </p>
      <p v-if="order.spec.shippingAddress" class="address">
        {{ order.spec.shippingAddress.recipient }} · {{ order.spec.shippingAddress.phone }} · {{ order.spec.shippingAddress.address }}
      </p>
      <p v-for="shipment in order.spec.fulfillment.filter(line => line.type === 'PHYSICAL' && line.trackingNumber)" :key="shipment.lineIndex" class="tracking">
        {{ shipment.carrier }} · {{ shipment.trackingNumber }}
      </p>
      <button type="button" v-if="order.spec.state === 'FULFILLING' && order.spec.fulfillment.some(line => line.type === 'PHYSICAL')"
        :disabled="busy" @click="openShipment(order)">更新物流</button>
    </article>
    <nav v-if="total > 20"><button type="button" :disabled="busy || page === 1" @click="page--; load()">上一页</button>
      <span>第 {{ page }} 页，共 {{ total }} 单</span><button type="button" :disabled="busy || page * 20 >= total" @click="page++; load()">下一页</button></nav>
    <form v-if="selected" class="shipment" @submit.prevent.stop="saveShipment">
      <h2>更新物流 · {{ selected.metadata.name }}</h2>
      <label>承运商<input v-model="carrier" maxlength="80" required /></label>
      <label>物流单号<input v-model="trackingNumber" maxlength="100" pattern="[A-Za-z0-9._/-]+" required /></label>
      <label class="check"><input v-model="delivered" type="checkbox"
        :disabled="!selected.spec.fulfillment.filter(line => line.type === 'PHYSICAL').every(line => line.state === 'SHIPPED' || line.state === 'DELIVERED')" />
        确认已送达（先登记物流，再重新打开订单确认送达）</label>
      <footer><button type="button" :disabled="busy" @click="selected = null">关闭</button><button type="submit" :disabled="busy">保存物流</button></footer>
    </form>
  </section>
</template>

<style scoped>
.commerce-admin { padding: 24px; color: #273449; }
header, footer, nav { display: flex; justify-content: space-between; align-items: center; gap: 16px; }
header { margin-bottom: 18px; } h1 { font-size: 24px; font-weight: 600; } p { margin: 8px 0; }
button { padding: 8px 14px; border: 1px solid #d5dce6; border-radius: 6px; background: white; }
button:disabled { opacity: .5; } .order, .shipment { margin: 16px 0; padding: 18px; border: 1px solid #e5e9f0; border-radius: 8px; background: white; }
.order header { flex-wrap: wrap; } .order small { margin-left: 12px; color: #68758a; } .address, .tracking { color: #68758a; }
.shipment { display: grid; gap: 14px; max-width: 640px; } .shipment label { display: grid; gap: 6px; }
.shipment input { padding: 10px; border: 1px solid #d5dce6; border-radius: 6px; } .shipment .check { display: flex; }
.error { padding: 12px; color: #a82020; background: #fff1f1; }
</style>
