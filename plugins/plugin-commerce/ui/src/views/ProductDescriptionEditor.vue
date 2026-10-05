<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
const props = defineProps<{ modelValue: string; format: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const editor = ref<HTMLElement>()
const source = ref(false)
const resourceUrl = ref('')
function safeHtml(html: string) {
  const doc = new DOMParser().parseFromString(html, 'text/html')
  doc.querySelectorAll('script,style,iframe,object,embed,form,input,button,svg,math').forEach(el => el.remove())
  doc.body.querySelectorAll('*').forEach(el => {
    Array.from(el.attributes).forEach(attr => {
      if (attr.name.startsWith('on') || attr.name === 'srcdoc' || attr.name === 'style' ||
          (['href', 'src'].includes(attr.name) && !/^(https?:\/\/|\/(?!\/)|#|mailto:)/i.test(attr.value))) el.removeAttribute(attr.name)
    })
  })
  return doc.body.innerHTML
}
async function sync() {
  await nextTick()
  if (editor.value && editor.value.innerHTML !== safeHtml(props.modelValue || '')) editor.value.innerHTML = safeHtml(props.modelValue || '')
}
function update() { emit('update:modelValue', editor.value?.innerHTML || '') }
function command(name: string, value?: string) {
  editor.value?.focus()
  document.execCommand(name, false, value)
  update()
}
function insertResource(kind: 'createLink' | 'insertImage') {
  if (!/^(https?:\/\/|\/(?!\/))/.test(resourceUrl.value.trim())) return
  command(kind, resourceUrl.value.trim()); resourceUrl.value = ''
}
watch(() => [props.format, source.value], sync)
watch(() => props.modelValue, () => { if (document.activeElement !== editor.value) sync() })
onMounted(sync)
</script>
<template>
  <section class="description-editor">
    <div v-if="format === 'html'" class="toolbar">
      <button type="button" @click="source = !source">{{ source ? '可视化编辑' : 'HTML 源码' }}</button>
      <template v-if="!source">
        <button type="button" @mousedown.prevent @click="command('bold')">粗体</button>
        <button type="button" @mousedown.prevent @click="command('italic')">斜体</button>
        <button type="button" @mousedown.prevent @click="command('formatBlock', 'h2')">标题</button>
        <button type="button" @mousedown.prevent @click="command('formatBlock', 'p')">正文</button>
        <button type="button" @mousedown.prevent @click="command('insertUnorderedList')">列表</button>
        <button type="button" @mousedown.prevent @click="command('insertOrderedList')">编号列表</button>
        <button type="button" @mousedown.prevent @click="command('formatBlock', 'blockquote')">引用</button>
        <button type="button" @mousedown.prevent @click="command('removeFormat')">清除格式</button>
      </template>
    </div>
    <div v-if="format === 'html' && !source" ref="editor" class="editable" contenteditable="true" role="textbox" aria-label="商品说明富文本编辑器" aria-multiline="true" @input="update" @blur="sync" />
    <textarea v-else :value="modelValue" :aria-label="format === 'markdown' ? '商品说明 Markdown' : '商品说明源码'" rows="10" @input="emit('update:modelValue', ($event.target as HTMLTextAreaElement).value)" />
    <div v-if="format === 'html' && !source" class="resources">
      <input v-model="resourceUrl" aria-label="说明链接或图片地址" placeholder="链接或图片地址" />
      <button type="button" @mousedown.prevent @click="insertResource('createLink')">插入链接</button>
      <button type="button" @mousedown.prevent @click="insertResource('insertImage')">插入图片</button>
    </div>
    <p v-if="format === 'markdown'">支持 Markdown 标题、列表、表格、链接、图片与代码块。</p>
  </section>
</template>
<style scoped>
.description-editor { display: grid; gap: 10px; } .toolbar,.resources { display:flex; flex-wrap:wrap; gap:6px; } button { padding:6px 10px; border:1px solid #d5dce6; border-radius:6px; background:#fff; } .editable,textarea { min-height:220px; padding:16px; border:1px solid #d5dce6; border-radius:8px; background:white; width:100%; } .editable :deep(h2) { font-size:1.5em; font-weight:600; } .editable :deep(ul) { list-style:disc; padding-left:24px; } .editable :deep(ol) { list-style:decimal; padding-left:24px; } .editable :deep(img) { max-width:100%; } .editable :deep(blockquote) { border-left:3px solid #cbd5e1; padding-left:12px; } input { padding:8px; border:1px solid #d5dce6; border-radius:6px; flex:1; } p { font-size:13px; color:#64748b; }
</style>
