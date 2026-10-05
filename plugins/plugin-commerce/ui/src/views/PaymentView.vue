<script setup lang="ts">
import { consoleApiClient, coreApiClient } from '@halo-dev/api-client'
import PaymentProviderSelect from './PaymentProviderSelect.vue'
import wechatLogo from '../assets/payment/wechat.svg'
import alipayLogo from '../assets/payment/alipay.svg'
import usdtLogo from '../assets/payment/usdt.svg'
import { onMounted, onBeforeUnmount, ref, inject, type Ref } from 'vue'
const selectedTheme = inject<Ref<{ metadata: { name: string } } | undefined>>('selectedTheme', ref())
const themeName = () => selectedTheme.value?.metadata.name ?? 'theme-aoset'
const root = ref<HTMLElement | null>(null)
const loading = ref(true)
const saving = ref(false)
const loaded = ref(false)
const error = ref('')
const success = ref('')
const merchantKey = ref('')
const config = ref<Record<string, unknown>>({})
const channels = [
  { id: 'wxpay', logo: wechatLogo, title: '微信支付', providers: [{ value: 'official', label: '官方' }, { value: 'epay', label: '易支付' }, { value: 'xunhupay', label: '虎皮椒' }] },
  { id: 'alipay', logo: alipayLogo, title: '支付宝支付', providers: [{ value: 'official', label: '官方' }, { value: 'epay', label: '易支付' }, { value: 'xunhupay', label: '虎皮椒' }] },
  { id: 'usdt', logo: usdtLogo, title: 'USDT 支付', providers: [{ value: 'epay', label: '易支付' }, { value: 'epusdt', label: 'epusdt' }, { value: 'tokenpay', label: 'tokenpay' }] },
]
let form: HTMLFormElement | null = null
async function load() {
  try {
    const { data } = await consoleApiClient.theme.theme.fetchThemeJsonConfig({ name: themeName() })
    config.value = { ...((data as Record<string, Record<string, unknown>>).commerce_payment ?? {}) }
    for (const channel of channels) {
      config.value[`${channel.id}_enabled`] ??= channel.id !== 'usdt' && config.value.enabled === true
      config.value[`${channel.id}_provider`] ??= 'epay'
    }
    loaded.value = true
  } catch { error.value = '支付配置加载失败，请刷新重试。' }
  finally { loading.value = false }
}
async function save(event: Event) {
  event.preventDefault(); event.stopImmediatePropagation()
  if (saving.value || loading.value) return
  saving.value = true; error.value = ''; success.value = ''
  try {
    if (merchantKey.value.trim()) {
      const { data } = await coreApiClient.secret.createSecret({ secret: {
        apiVersion: 'v1alpha1', kind: 'Secret', type: 'Opaque',
        metadata: { name: '', generateName: 'commerce-epay-', annotations: { 'halo.run/description': '2333 Store 易支付商户密钥' } },
        stringData: { merchant_key: merchantKey.value.trim() },
      } })
      config.value.merchant_secret = data.metadata.name
      merchantKey.value = ''
    }
    const { data } = await consoleApiClient.theme.theme.fetchThemeJsonConfig({ name: themeName() })
    await consoleApiClient.theme.theme.updateThemeJsonConfig({ name: themeName(), body: { ...data, commerce_payment: { ...config.value } } })
    success.value = '支付配置已保存。'
  } catch { error.value = '保存失败，请检查权限后重试。' }
  finally { saving.value = false }
}
onMounted(() => { form = root.value?.closest('form') ?? null; form?.addEventListener('submit', save, true); void load() })
onBeforeUnmount(() => { form?.removeEventListener('submit', save, true); merchantKey.value = '' })
</script>
<template>
  <section ref="root" class="payment-settings">
    <p v-if="loading">正在加载支付配置…</p>
    <p v-if="error" role="alert" class="error">{{ error }}</p>
    <p v-if="success" role="status">{{ success }}</p>
    <template v-if="loaded">
      <details class="payment-block epay-config">
        <summary class="block-heading"><h2>易支付配置</h2><svg class="collapse-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><path d="m6 9 6 6 6-6" /></svg></summary>
        <div class="fields">
          <label class="switch epay-toggle"><span>启用接口</span><input v-model="config.enabled" type="checkbox" aria-label="启用易支付接口" /></label>
          <label>接口地址<input v-model="config.submit_url" type="url" placeholder="https://支付平台/submit.php" /></label>
          <label>商户 ID<input v-model="config.merchant_id" autocomplete="off" /></label>
          <label>易支付商户密钥<input v-model="merchantKey" type="password" autocomplete="new-password" :placeholder="config.merchant_secret ? '已配置，留空保持原密钥' : '填写易支付平台提供的商户密钥'" /><small>直接填写即可，后台自动保存到 Halo 密钥管理。</small></label>
          <label>商城名称<input v-model="config.site_name" /></label>
          <label>异步通知地址<input v-model="config.notify_url" type="url" placeholder="https://站点域名/shop/payment/notify" /></label>
          <label>支付返回地址<input v-model="config.return_url" type="url" placeholder="https://站点域名/shop/orders" /></label>
        </div>
      </details>
      <section class="payment-block"><header class="block-heading"><div><h2>支付方式</h2><p>开启后选择对应收款渠道。</p></div></header>
        <div v-for="channel in channels" :key="channel.id" class="channel">
          <div class="channel-row"><div class="channel-identity"><span class="channel-icon" :class="channel.id" aria-hidden="true"><img :src="channel.logo" alt="" /></span><div><h3>{{ channel.title }}</h3><small>{{ config[`${channel.id}_enabled`] ? '已开启' : '已关闭' }}</small></div></div><div class="channel-controls"><PaymentProviderSelect v-if="config[`${channel.id}_enabled`]" v-model="config[`${channel.id}_provider`]" :label="`${channel.title}渠道`" :options="channel.providers" /><input v-model="config[`${channel.id}_enabled`]" type="checkbox" :aria-label="channel.title" /></div></div>
          <p v-if="config[`${channel.id}_enabled`] && config[`${channel.id}_provider`] !== 'epay'" class="pending">此渠道待接通，暂不提供收款。</p>
          <p v-else-if="channel.id === 'usdt' && config.usdt_enabled" class="pending">通过易支付收款，USDT 汇率与 TRC20 钱包由易支付平台管理。</p>
        </div>
      </section>
      <p>{{ saving ? '保存中…' : '修改完成后，点击页面底部的保存按钮。' }}</p>
    </template>
  </section>
</template>
<style scoped>
.payment-settings { width:100%; max-width:880px; margin:0 auto; min-width:0; display:grid; gap:16px; color:#18181b; font-size:13px; }
h2 { font-size:14px; font-weight:600; } h3 { font-size:13px; font-weight:500; } p { font-size:12px; color:#71717a; margin-top:4px; line-height:1.5; } small { font-size:11px; font-weight:400; color:#a1a1aa; }
summary.block-heading { cursor:pointer; list-style:none; border-bottom:0; } summary::-webkit-details-marker { display:none; } summary:focus-visible { outline:2px solid #a1a1aa; outline-offset:2px; } .epay-config[open] summary { border-bottom:1px solid #f0f0f2; } .collapse-icon { flex:none; color:#71717a; } .epay-config[open] .collapse-icon { transform:rotate(180deg); } .epay-toggle { grid-column:1 / -1; justify-content:space-between; }
.payment-block { border:1px solid #e4e4e7; border-radius:8px; background:#fff; min-width:0; box-shadow:0 1px 2px #00000006; }.block-heading { display:flex; justify-content:space-between; align-items:center; gap:16px; padding:14px 18px; border-bottom:1px solid #f0f0f2; }
.fields { align-items:start; display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:14px 16px; padding:18px; } label { display:grid; gap:6px; min-width:0; font-size:12px; font-weight:500; }
input:not([type=checkbox]),select { display:block; height:34px; width:100%; min-width:0; box-sizing:border-box; padding:6px 10px; border:1px solid #e4e4e7; border-radius:6px; background:#fff; color:#18181b; font-size:12px; box-shadow:0 1px 2px #00000006; outline:none; transition:border-color .15s,box-shadow .15s; } input::placeholder { color:#a1a1aa; } input:focus-visible,select:focus-visible { border-color:#a1a1aa; box-shadow:0 0 0 3px #a1a1aa2e; }
.switch { display:flex; align-items:center; gap:10px; color:#71717a; }
input[type=checkbox] { appearance:none; -webkit-appearance:none; position:relative; display:block; flex:none; width:32px; height:18px; margin:0; padding:0; border:0; border-radius:99px; background:#e4e4e7; cursor:pointer; transition:background-color .15s; } input[type=checkbox]::before { content:''; position:absolute; top:2px; left:2px; width:14px; height:14px; border-radius:50%; background:white; box-shadow:0 1px 2px #0002; transition:transform .15s; } input[type=checkbox]:checked { background:#18181b; } input[type=checkbox]:checked::before { transform:translateX(14px); }
.channel { padding:14px 18px; border-bottom:1px solid #f0f0f2; }.channel:last-child { border-bottom:0; }.channel-row,.channel-identity,.channel-controls { display:flex; align-items:center; gap:12px; }.channel-row { justify-content:space-between; }.channel-icon { display:grid; place-items:center; width:32px; height:32px; border-radius:7px; font-size:16px; font-weight:600; }.channel-icon img { width:22px; height:22px; object-fit:contain; }.wxpay { color:#168447; background:#effaf2; }.alipay { color:#1677d9; background:#edf6ff; }.usdt { color:#188679; background:#edf9f7; }.channel-controls select { width:132px; appearance:auto; padding-right:8px; }.badge { border:1px solid #e4e4e7; border-radius:5px; padding:2px 7px; font-size:11px; color:#71717a; }.pending { margin:8px 0 0 44px; color:#945900; font-size:11px; }.error { color:#b42318; } [role=status] { color:#168447; }
@media(max-width:600px) { .fields { grid-template-columns:minmax(0,1fr); padding:14px; }.block-heading,.channel { padding:14px; }.channel-controls { gap:8px; }.channel-controls select { width:100px; } }
@media(prefers-reduced-motion:reduce) { input,select,input::before { transition:none; } }
</style>
