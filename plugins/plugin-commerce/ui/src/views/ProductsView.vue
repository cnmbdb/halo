<script setup lang="ts">
import { axiosInstance } from '@halo-dev/api-client'
import { computed, onMounted, onBeforeUnmount, ref, toRaw } from 'vue'
import ProductDescriptionEditor from './ProductDescriptionEditor.vue'
import { listCategories } from '../api/categories'

type Sku = { id: string; title: string; priceMinor: number; stock: number; digitalCodes: string[]; digitalCodesText?: string }
type Product = {
  apiVersion: string; kind: string;
  metadata: { name: string; version?: number; annotations?: Record<string, string> };
  spec: { title: string; slug: string; type: 'PHYSICAL' | 'DIGITAL'; state: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
    description: string; images: string[]; categories: string[]; skus: Sku[]; featured: boolean }
}
const endpoint = '/apis/commerce.halo.run/v1alpha1/products'
const products = ref<Product[]>([])
const page = ref(1)
const total = ref(0)
const pageSize = 20
const categories = ref<{ metadata: { name: string }; spec: { title: string } }[]>([])
const editing = ref<Product | null>(null)
const error = ref('')
const busy = ref(false)
const isNew = ref(false)
const root = ref<HTMLElement | null>(null)
let themeForm: HTMLFormElement | null = null
function submitProduct(event: Event) {
  if (!editing.value) return
  event.preventDefault()
  event.stopImmediatePropagation()
  if (!busy.value) void save()
}
const imageLines = ref('')
const descriptionFormat = ref('text')
const galleryImages = computed({
  get: () => imageLines.value.split('\n').map(s => s.trim()).filter(Boolean),
  set: (images: string[]) => { imageLines.value = images.join('\n') },
})
function moveImage(index: number, delta: number) {
  const images = [...galleryImages.value]
  const target = index + delta
  if (target < 0 || target >= images.length) return
  ;[images[index], images[target]] = [images[target], images[index]]
  galleryImages.value = images
}
function removeImage(index: number) { galleryImages.value = galleryImages.value.filter((_, i) => i !== index) }
function plainClone<T>(value: T): T {
  const raw = toRaw(value)
  if (Array.isArray(raw)) return raw.map(item => plainClone(item)) as T
  if (raw && typeof raw === 'object') {
    const result: Record<string, unknown> = {}
    for (const [key, child] of Object.entries(raw)) result[key] = plainClone(child)
    return result as T
  }
  return raw
}
async function load() {
  busy.value = true; error.value = ''
  try {
    const { data } = await axiosInstance.get<{ items: Product[]; total: number }>(endpoint, { params: { page: page.value, size: pageSize } })
    products.value = data.items
    total.value = data.total
  } catch { error.value = '加载失败，请确认商城插件已启用且你拥有商品管理权限。' }
  finally { busy.value = false }
}
const allowGuestPurchase = ref(false)
function edit(product?: Product) {
  isNew.value = !product
  editing.value = product ? structuredClone(toRaw(product)) : {
    apiVersion: 'commerce.halo.run/v1alpha1', kind: 'Product',
    metadata: { name: `product-${crypto.randomUUID()}` },
    spec: { title: '', slug: '', type: 'PHYSICAL', state: 'DRAFT', description: '',
      images: [], categories: [], skus: [{ id: 'default', title: '标准款', priceMinor: 0, stock: 0, digitalCodes: [] }], featured: false },
  }
  editing.value.spec.skus = editing.value.spec.skus.map(sku => ({ ...sku,
    digitalCodes: sku.digitalCodes ?? [], digitalCodesText: (sku.digitalCodes ?? []).join('\n') }))
  allowGuestPurchase.value = editing.value.metadata.annotations?.['commerce.halo.run/allow-guest-purchase'] === 'true'
  imageLines.value = editing.value.spec.images.join('\n')
  descriptionFormat.value = editing.value.metadata.annotations?.['commerce.halo.run/description-format'] || 'text'
  error.value = ''
}
async function save() {
  if (!editing.value) return
  const product = editing.value
  if (!product.spec.title.trim() || !/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(product.spec.slug)) {
    error.value = '请填写商品名称和由小写字母、数字及连字符组成的商品路径。'; return
  }
  if (!product.spec.skus.length || product.spec.skus.some(s => !s.id || !s.title || !Number.isSafeInteger(s.priceMinor) || s.priceMinor < 0 || !Number.isSafeInteger(s.stock) || s.stock < 0)) {
    error.value = 'SKU 必须包含标识、名称，以及非负整数价格（分）和库存。'; return
  }
  if (new Set(product.spec.skus.map(s => s.id)).size !== product.spec.skus.length) {
    error.value = 'SKU 标识不能重复。'; return
  }
  const codes = product.spec.skus.flatMap(sku => (sku.digitalCodesText ?? '').split('\n').map(code => code.trim()).filter(Boolean))
  if (new Set(codes).size !== codes.length) { error.value = '数字兑换码不能重复。'; return }
  if (product.spec.type === 'DIGITAL' && product.spec.state === 'PUBLISHED' &&
    product.spec.skus.some(sku => (sku.digitalCodesText ?? '').split('\n').filter(code => code.trim()).length < sku.stock)) {
    error.value = '数字商品上架前，每个 SKU 的兑换码数量必须不少于库存。'; return
  }
  product.metadata.annotations = { ...product.metadata.annotations, 'commerce.halo.run/description-format': descriptionFormat.value, 'commerce.halo.run/allow-guest-purchase': String(allowGuestPurchase.value) }
  product.spec.images = imageLines.value.split('\n').map(s => s.trim()).filter(Boolean)
  const payload = plainClone(product)
  payload.spec.skus = payload.spec.skus.map(({ digitalCodesText, ...sku }) => ({
    ...sku, digitalCodes: (digitalCodesText ?? '').split('\n').map(code => code.trim()).filter(Boolean),
  }))
  busy.value = true; error.value = ''
  try {
    if (isNew.value) await axiosInstance.post(endpoint, payload)
    else await axiosInstance.put(`${endpoint}/${encodeURIComponent(product.metadata.name)}`, payload)
    editing.value = null
    await load()
  } catch { error.value = '保存失败。如有版本冲突，请关闭编辑窗口并刷新后重试。' }
  finally { busy.value = false }
}
onMounted(async () => {
  themeForm = root.value?.closest('form') ?? null
  themeForm?.addEventListener('submit', submitProduct, true)
  await load()
  try {
    categories.value = await listCategories()
  } catch { error.value = '分类加载失败，请刷新重试。' }
})
onBeforeUnmount(() => themeForm?.removeEventListener('submit', submitProduct, true))
</script>

<template>
  <section ref="root" class="commerce-admin">
    <header><div><h1>商品管理</h1><p>实物与数字商品统一管理，价格单位为分。</p></div>
      <div><button type="button" :disabled="busy" @click="load">刷新</button><button type="button" :disabled="busy" @click="edit()">新增商品</button></div></header>
    <p v-if="error" role="alert" class="error">{{ error }}</p>
    <table v-if="!editing"><thead><tr><th>商品</th><th>类型</th><th>状态</th><th>SKU</th><th>操作</th></tr></thead>
      <tbody><tr v-for="product in products" :key="product.metadata.name">
        <td>{{ product.spec.title }}</td><td>{{ product.spec.type === 'PHYSICAL' ? '实物' : '数字' }}</td>
        <td>{{ { DRAFT: '草稿', PUBLISHED: '已上架', ARCHIVED: '已下架' }[product.spec.state] }}</td>
        <td>{{ product.spec.skus.length }}</td><td><button type="button" :disabled="busy" @click="edit(product)">编辑</button></td>
      </tr></tbody></table>
    <nav v-if="!editing && total > pageSize" aria-label="商品分页"><button type="button" :disabled="busy || page === 1" @click="page--; load()">上一页</button><span>第 {{ page }} 页，共 {{ total }} 件</span><button type="button" :disabled="busy || page * pageSize >= total" @click="page++; load()">下一页</button></nav>
    <p v-if="!editing && !products.length && !busy">暂无商品，点击新增商品开始。</p>
    <div v-if="editing" class="product-editor">
      <label>商品名称<input v-model="editing.spec.title" required /></label>
      <label>商品路径<input v-model="editing.spec.slug" required pattern="[a-z0-9]+(-[a-z0-9]+)*" /></label>
      <label>类型<select v-model="editing.spec.type"><option value="PHYSICAL">实物商品</option><option value="DIGITAL">数字商品</option></select></label>
      <label>发布状态<select v-model="editing.spec.state"><option value="DRAFT">草稿</option><option value="PUBLISHED">上架</option><option value="ARCHIVED">下架</option></select></label>
      <label>商品说明格式<select v-model="descriptionFormat"><option value="text">纯文本</option><option value="html">富文本 / HTML</option><option value="markdown" disabled>Markdown（待启用）</option></select></label>
      <ProductDescriptionEditor v-model="editing.spec.description" :format="descriptionFormat" />
      <FormKit v-model="galleryImages" type="attachment" label="商品图片" :multiple="true" :ignore="true" />
      <p>可上传或从附件库选择多张图片，第一张作为商品封面，支持 GIF。</p>
      <div class="image-gallery"><div v-for="(image, index) in galleryImages" :key="index" class="image-item"><img :src="image" alt="商品图片预览" /><span>{{ index === 0 ? '封面' : `图片 ${index + 1}` }}</span><div><button type="button" :disabled="index === 0" @click="moveImage(index, -1)">前移</button><button type="button" :disabled="index === galleryImages.length - 1" @click="moveImage(index, 1)">后移</button><button type="button" @click="removeImage(index)">移除图片</button></div></div></div>
      <label>图片链接（每行一个）<textarea v-model="imageLines" rows="3" /></label>
      <fieldset class="category-picker"><legend>关联商品分类</legend>
        <p>可选择多个分类，保存商品后生效。</p>
        <label v-for="category in categories" :key="category.metadata.name" class="check">
          <input v-model="editing.spec.categories" type="checkbox" :value="category.metadata.name" />{{ category.spec.title }}
        </label>
        <p v-if="!categories.length">暂无可选分类，请先在主题 → 商城首页 → 商品分类区块新增分类。</p>
      </fieldset>
      <label class="check"><input v-model="allowGuestPurchase" type="checkbox" />允许未登录游客购买</label>
      <label class="check"><input v-model="editing.spec.featured" type="checkbox" />精选商品</label>
      <fieldset><legend>SKU</legend>
        <div v-for="(sku, index) in editing.spec.skus" :key="index" class="sku">
          <label>标识<input v-model="sku.id" required /></label><label>名称<input v-model="sku.title" required /></label>
          <label>价格（分）<input v-model.number="sku.priceMinor" type="number" min="0" step="1" required /></label>
          <label>库存<input v-model.number="sku.stock" type="number" min="0" step="1" required /></label>
          <label v-if="editing.spec.type === 'DIGITAL'" class="codes">数字兑换码（每行一个，仅管理端可见）<textarea v-model="sku.digitalCodesText" rows="4" autocomplete="off" /></label>
          <button type="button" :disabled="editing.spec.skus.length === 1" @click="editing.spec.skus.splice(index, 1)">移除</button>
        </div>
        <button type="button" @click="editing.spec.skus.push({ id: '', title: '', priceMinor: 0, stock: 0, digitalCodes: [], digitalCodesText: '' })">添加 SKU</button>
      </fieldset>
      <footer><button type="button" :disabled="busy" @click="editing = null">取消编辑</button><span>{{ busy ? '保存中…' : '编辑完成后，点击页面底部的保存按钮。' }}</span></footer>
    </div>
  </section>
</template>

<style scoped>
.commerce-admin { box-sizing:border-box; width:100%; min-width:0; padding: 24px; color: #273449; }
header, footer { display: flex; justify-content: space-between; align-items: center; gap: 16px; margin-bottom: 24px; }
h1 { font-size: 24px; font-weight: 600; } p { margin-top: 8px; } button { padding: 8px 14px; border: 1px solid #d5dce6; border-radius: 6px; background: white; margin-right: 8px; } button:disabled { opacity: .5; }
table { width: 100%; border-collapse: collapse; background: white; } th, td { text-align: left; padding: 16px; border-bottom: 1px solid #e5e9f0; }
.product-editor { width:100%; max-width: 960px; min-width:0; display: grid; gap: 18px; } label { display: grid; gap: 6px; font-size: 14px; min-width:0; } input, textarea, select { box-sizing:border-box; width:100%; min-width:0; max-width:100%; padding: 10px; border: 1px solid #d5dce6; border-radius: 6px; background: white; } .check { display: flex; align-items:center; } .check input { width:auto; } fieldset { min-width:0; } .sku { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin: 16px 0; } .codes { grid-column: 1 / -1; } .error { padding: 12px; color: #a82020; background: #fff1f1; margin-bottom: 18px; }
@media(max-width: 700px) { .sku { grid-template-columns: 1fr 1fr; } header { align-items: start; flex-direction: column; } }
.image-gallery { display:flex; flex-wrap:wrap; gap:12px; } .image-item { display:grid; gap:8px; padding:10px; border:1px solid #e5e7eb; border-radius:8px; } .image-item img { width:120px; height:120px; object-fit:cover; } .image-item button { font-size:12px; padding:4px 8px; }
</style>
