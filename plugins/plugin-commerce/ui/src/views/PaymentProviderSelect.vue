<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
const props = defineProps<{ modelValue: unknown; label: string; options: { value: string; label: string }[] }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const root = ref<HTMLElement | null>(null)
const trigger = ref<HTMLButtonElement | null>(null)
const open = ref(false)
const active = ref(0)
const selected = computed(() => props.options.find(option => option.value === props.modelValue)?.label ?? '请选择')
async function show() { open.value = true; active.value = Math.max(0, props.options.findIndex(option => option.value === props.modelValue)); await nextTick(); focusOption() }
function focusOption() { root.value?.querySelectorAll<HTMLButtonElement>('[role=option]')[active.value]?.focus() }
function close() { open.value = false; trigger.value?.focus() }
function choose(value: string) { emit('update:modelValue', value); close() }
function key(event: KeyboardEvent) {
  if (event.key === 'Escape') { event.preventDefault(); close() }
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') { event.preventDefault(); active.value = (active.value + (event.key === 'ArrowDown' ? 1 : -1) + props.options.length) % props.options.length; focusOption() }
  if (event.key === 'Home' || event.key === 'End') { event.preventDefault(); active.value = event.key === 'Home' ? 0 : props.options.length - 1; focusOption() }
}
function outside(event: PointerEvent) { if (!root.value?.contains(event.target as Node)) open.value = false }
function blur(event: FocusEvent) { if (!root.value?.contains(event.relatedTarget as Node)) open.value = false }
onMounted(() => document.addEventListener('pointerdown', outside))
onBeforeUnmount(() => document.removeEventListener('pointerdown', outside))
</script>
<template>
  <div ref="root" class="provider-select" @focusout="blur">
    <button ref="trigger" class="select-trigger" type="button" role="combobox" :aria-label="label" :aria-expanded="open" aria-haspopup="listbox" @click="open ? close() : show()" @keydown.down.prevent="show" @keydown.up.prevent="show">
      <span>{{ selected }}</span><svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><path d="m6 9 6 6 6-6" /></svg>
    </button>
    <div v-if="open" class="select-menu" role="listbox" :aria-label="label" @keydown="key">
      <button v-for="(option, index) in options" :key="option.value" type="button" role="option" :aria-selected="option.value === modelValue" :tabindex="index === active ? 0 : -1" @click="choose(option.value)">
        <span>{{ option.label }}</span><svg v-if="option.value === modelValue" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="m5 12 4 4L19 6" /></svg>
      </button>
    </div>
  </div>
</template>
<style scoped>
.provider-select { position:relative; width:132px; flex:none; font-size:12px; color:#18181b; }
.select-trigger { display:flex; align-items:center; justify-content:space-between; gap:12px; width:100%; height:34px; padding:0 10px; border:1px solid #e4e4e7; border-radius:6px; background:#fff; box-shadow:0 1px 2px #00000006; cursor:pointer; }
.select-trigger svg { flex:none; color:#71717a; }.select-trigger:focus-visible { outline:2px solid #a1a1aa; outline-offset:2px; }
.select-menu { position:absolute; z-index:30; top:calc(100% + 5px); right:0; min-width:100%; padding:4px; border:1px solid #e4e4e7; border-radius:7px; background:#fff; box-shadow:0 4px 16px #00000014,0 1px 3px #0000000a; }
.select-menu button { display:flex; align-items:center; justify-content:space-between; gap:16px; width:100%; height:30px; padding:0 8px; border:0; border-radius:4px; background:transparent; color:inherit; text-align:left; white-space:nowrap; cursor:pointer; outline:none; }.select-menu button[aria-selected=true] { font-weight:500; }.select-menu button:focus-visible { background:#f4f4f5; }
@media(hover:hover) { .select-menu button:hover { background:#f4f4f5; }.select-trigger:hover { border-color:#c4c4c8; } }
@media(max-width:600px) { .provider-select { width:100px; } }
</style>
