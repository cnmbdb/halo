// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { expect, it } from 'vitest'
import PaymentProviderSelect from './PaymentProviderSelect.vue'
it('supports keyboard selection, selected indication, Escape and outside close', async () => {
  const wrapper = mount(PaymentProviderSelect, { attachTo: document.body, props: { modelValue: 'epay', label: '微信支付渠道', options: [{ value: 'official', label: '官方' }, { value: 'epay', label: '易支付' }, { value: 'xunhupay', label: '虎皮椒' }] } })
  await wrapper.find('[role=combobox]').trigger('keydown', { key: 'ArrowDown' })
  expect(wrapper.find('[role=option][aria-selected=true]').text()).toBe('易支付')
  expect(document.activeElement?.textContent).toBe('易支付')
  await wrapper.find('[role=listbox]').trigger('keydown', { key: 'ArrowDown' })
  expect(document.activeElement?.textContent).toBe('虎皮椒')
  await wrapper.findAll('[role=option]')[2].trigger('click')
  expect(wrapper.emitted('update:modelValue')).toEqual([['xunhupay']])
  expect(wrapper.find('[role=listbox]').exists()).toBe(false)
  await wrapper.find('[role=combobox]').trigger('click')
  await wrapper.find('[role=listbox]').trigger('keydown', { key: 'Escape' })
  expect(document.activeElement).toBe(wrapper.find('[role=combobox]').element)
  await wrapper.find('[role=combobox]').trigger('click')
  document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }))
  await wrapper.vm.$nextTick()
  expect(wrapper.find('[role=listbox]').exists()).toBe(false)
  wrapper.unmount()
})
