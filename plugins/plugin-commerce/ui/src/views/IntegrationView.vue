<script setup lang="ts">
import { axiosInstance } from '@halo-dev/api-client'
import { computed, onMounted, ref } from 'vue'

type PluginInfo = { spec: { version: string }; status?: { phase?: string } }
const plugin = ref<PluginInfo | null>(null)
const loading = ref(false)
const error = ref('')
const running = computed(() => plugin.value?.status?.phase === 'STARTED')
const status = computed(() => {
  if (loading.value) return '读取中'
  if (error.value) return '状态不可用'
  const phase = plugin.value?.status?.phase
  return ({ STARTED: '运行中', STOPPED: '已停止', DISABLED: '已禁用', FAILED: '启动失败', RESOLVED: '待启动' } as Record<string, string>)[phase ?? ''] ?? '待确认'
})
const links = [
  { title: '商品管理', description: '上架商品、维护规格与库存', href: '/console/theme/settings/commerce_products' },
  { title: '订单管理', description: '查看订单、物流与数字交付', href: '/console/theme/settings/commerce_orders' },
  { title: '支付管理', description: '配置易支付接口与商户密钥', href: '/console/theme/settings/commerce_payment' },
  { title: '商城首页', description: '设置轮播、分类与商品展示', href: '/console/theme/settings/commerce_home' },
]
async function load() {
  loading.value = true; error.value = ''
  try { plugin.value = (await axiosInstance.get<PluginInfo>('/apis/plugin.halo.run/v1alpha1/plugins/commerce')).data }
  catch { error.value = '暂时无法读取插件状态，可在插件详情中查看。' }
  finally { loading.value = false }
}
onMounted(load)
</script>

<template>
  <section class="integration" aria-label="商城插件集成">
    <div class="plugin-card">
      <header>
        <div class="identity"><span class="plugin-mark" aria-hidden="true">S</span><div><h2>商城核心插件</h2><p>2333 Store 的配套服务</p></div></div>
        <span class="status" :class="{ running }" role="status"><span aria-hidden="true" />{{ status }}</span>
      </header>
      <p class="description">管理实物与数字商品，处理库存、订单、商品交付和支付回调。页面展示与商城配置统一在当前主题中管理。</p>
      <div class="metadata"><span>插件标识 <code>commerce</code></span><span>版本 <strong>{{ plugin?.spec.version || '—' }}</strong></span><button type="button" :disabled="loading" @click="load">刷新状态</button></div>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <footer><a class="primary" href="/shop" target="_blank" rel="noopener">访问商城 <span aria-hidden="true">↗</span></a><a class="secondary" href="/console/plugins/commerce">插件详情</a></footer>
    </div>
    <div class="management"><h3>商城管理</h3><p>从主题配置直接进入对应功能。</p><div class="link-grid"><a v-for="link in links" :key="link.href" :href="link.href"><div><h4>{{ link.title }}</h4><p>{{ link.description }}</p></div><span aria-hidden="true">→</span></a></div></div>
  </section>
</template>

<style scoped>
.integration { color: #253247; margin-bottom: 28px; }
.plugin-card { border: 1px solid #e3e7ee; border-radius: 12px; padding: 24px; background: #fff; }
header, .identity, .metadata, footer, .link-grid a { display: flex; align-items: center; }
header { justify-content: space-between; gap: 16px; }
.identity { gap: 14px; }
.plugin-mark { display: grid; place-items: center; width: 48px; height: 48px; border-radius: 12px; background: #171e2c; color: white; font-size: 24px; font-weight: 700; }
h2 { font-size: 19px; font-weight: 650; margin: 0 0 4px; } p { margin: 0; line-height: 1.6; color: #6a7688; font-size: 13px; }
.status { display: inline-flex; align-items: center; gap: 6px; padding: 5px 10px; border-radius: 20px; background: #f1f3f6; color: #647084; font-size: 12px; white-space: nowrap; }
.status > span { width: 6px; height: 6px; background: currentColor; border-radius: 50%; }.status.running { background: #edf7f0; color: #27824a; }
.description { margin: 22px 0 16px; max-width: 700px; font-size: 14px; }.metadata { gap: 20px; flex-wrap: wrap; color: #7a8595; font-size: 12px; }code, strong { color: #425168; font-weight: 500; margin-left: 6px; }
button { color: #53657d; background: none; border: none; cursor: pointer; padding: 0; }button:disabled { opacity: .5; }.error { margin-top: 14px; color: #a34135; }
footer { gap: 10px; margin-top: 24px; padding-top: 20px; border-top: 1px solid #eef0f4; }footer a { display: inline-flex; align-items: center; gap: 14px; padding: 9px 15px; border-radius: 6px; font-size: 13px; text-decoration: none; }.primary { background: #171e2c; color: #fff; }.secondary { border: 1px solid #dbe0e8; color: #425168; }
.management { margin-top: 26px; }h3 { font-size: 16px; font-weight: 600; margin: 0 0 4px; }.link-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 12px; margin-top: 16px; }.link-grid a { justify-content: space-between; gap: 12px; border: 1px solid #e3e7ee; border-radius: 8px; padding: 16px; text-decoration: none; color: #344259; transition: border-color .15s; }.link-grid a:hover { border-color: #8190a5; }.link-grid h4 { font-size: 14px; font-weight: 600; margin: 0 0 4px; }.link-grid a > span { color: #8a96a8; }
a:focus-visible,button:focus-visible { outline: 2px solid #4f71a8; outline-offset: 3px; }
@media(max-width:640px) { .plugin-card { padding: 18px; } .link-grid { grid-template-columns: 1fr; }.metadata { gap: 12px; } }
</style>
