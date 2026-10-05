<script setup lang="ts">
import { axiosInstance } from '@halo-dev/api-client'
import { onMounted, ref, toRaw } from 'vue'

import { categoryEndpoint as endpoint, listCategories, ensureDefaultCategory, type Category } from '../api/categories'
const items = ref<Category[]>([])
const editing = ref<Category | null>(null)
const busy = ref(false)
const error = ref('')
const isNew = ref(false)
const image = ref('')
const imageAnnotation = 'commerce.halo.run/category-image'
async function load() {
  busy.value = true; error.value = ''
  try {
    await ensureDefaultCategory()
    items.value = await listCategories()
  } catch { error.value = '加载分类失败，请确认你拥有商品管理权限。' }
  finally { busy.value = false }
}
function edit(item?: Category) {
  isNew.value = !item
  editing.value = item ? structuredClone(toRaw(item)) : {
    apiVersion: 'commerce.halo.run/v1alpha1', kind: 'ProductCategory',
    metadata: { name: `category-${crypto.randomUUID()}` },
    spec: { title: '', slug: '', description: '', priority: 0 },
  }
  image.value = editing.value.metadata.annotations?.[imageAnnotation] || ''
}
async function save() {
  if (!editing.value) return
  const item = editing.value
  const imageUrl = image.value.trim()
  if (imageUrl && !/^(https?:\/\/|\/(?!\/))/.test(imageUrl)) {
    error.value = '图片请使用 HTTP(S) 地址或站内附件路径。'; return
  }
  item.metadata.annotations = { ...item.metadata.annotations }
  if (imageUrl) item.metadata.annotations[imageAnnotation] = imageUrl
  else delete item.metadata.annotations[imageAnnotation]
  if (!item.spec.title.trim() || !/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(item.spec.slug) || !Number.isSafeInteger(item.spec.priority)) {
    error.value = '请填写分类名称、合法的小写分类路径和整数排序值。'; return
  }
  busy.value = true; error.value = ''
  try {
    if (isNew.value) await axiosInstance.post(endpoint, item)
    else await axiosInstance.put(`${endpoint}/${encodeURIComponent(item.metadata.name)}`, item)
    editing.value = null
    await load()
  } catch { error.value = '保存失败，如有版本冲突请重新加载分类。' }
  finally { busy.value = false }
}
onMounted(load)
</script>

<template>
  <section class="categories">
    <header><h1>商品分类</h1><div><button type="button" :disabled="busy" @click="load">刷新</button><button type="button" :disabled="busy" @click="edit()">新增分类</button></div></header>
    <p>每个分类可同时设置名称、方形图片及排序。图片支持 GIF、PNG、JPEG、WebP 等浏览器格式。</p>
    <p v-if="error" role="alert">{{ error }}</p>
    <table v-if="!editing"><thead><tr><th>名称</th><th>分类标识</th><th>排序</th><th>操作</th></tr></thead>
      <tbody><tr v-for="item in items" :key="item.metadata.name"><td><span class="category-name"><img v-if="item.metadata.annotations?.[imageAnnotation]" :src="item.metadata.annotations[imageAnnotation]" alt="" class="category-image" />{{ item.spec.title }}</span></td><td><code>{{ item.metadata.name }}</code></td><td>{{ item.spec.priority }}</td><td><button type="button" :disabled="busy" @click="edit(item)">编辑</button></td></tr></tbody>
    </table>
    <p v-if="!editing && !items.length && !busy">暂无分类。</p>
    <form v-if="editing" @submit.prevent.stop="save">
      <label>名称<input v-model="editing.spec.title" required /></label>
      <FormKit v-model="image" type="attachment" label="分类图片（支持 GIF）" :ignore="true" />
      <label>图片地址<input v-model="image" placeholder="可选择附件或填写图片地址" /></label>
      <img v-if="image" :src="image" alt="分类图片预览" class="category-image preview" />
      <p class="image-help">图片显示在分类名称前，按正方形裁剪；GIF 保留动画。不填图片只显示文字。</p>
      <label>分类路径<input v-model="editing.spec.slug" required pattern="[a-z0-9]+(-[a-z0-9]+)*" /></label>
      <label>说明<textarea v-model="editing.spec.description" /></label>
      <label>排序<input v-model.number="editing.spec.priority" type="number" step="1" required /></label>
      <footer><button type="button" :disabled="busy" @click="editing = null">取消</button><button type="submit" :disabled="busy">保存分类</button></footer>
    </form>
  </section>
</template>

<style scoped>
.categories { padding: 24px; color: #273449; } header { display: flex; justify-content: space-between; align-items: center; } h1 { font-size: 24px; font-weight: 600; } p { margin: 16px 0; } button { padding: 8px 14px; margin-right: 8px; background: white; border: 1px solid #d5dce6; border-radius: 6px; } button:disabled { opacity: .5; } table { width: 100%; background: white; border-collapse: collapse; } td, th { padding: 14px; text-align: left; border-bottom: 1px solid #e5e9f0; } form { max-width: 640px; display: grid; gap: 18px; } label { display: grid; gap: 6px; } input, textarea { padding: 10px; background: white; border: 1px solid #d5dce6; border-radius: 6px; }
.category-name { display: inline-flex; align-items: center; gap: 10px; } .category-image { width: 36px; height: 36px; object-fit: cover; border-radius: 5px; } .category-image.preview { width: 64px; height: 64px; } .image-help { margin: 0; font-size: 13px; color: #64748b; }
</style>
