// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
const api = vi.hoisted(() => ({ fetch: vi.fn(), update: vi.fn(), create: vi.fn() }))
vi.mock('@halo-dev/api-client', () => ({ consoleApiClient: { theme: { theme: { fetchThemeJsonConfig: api.fetch, updateThemeJsonConfig: api.update } } }, coreApiClient: { secret: { createSecret: api.create } } }))
import PaymentView from './PaymentView.vue'
beforeEach(() => {
  vi.clearAllMocks()
  api.fetch.mockResolvedValue({ data: { unrelated: { keep: true }, commerce_payment: { enabled: true, merchant_secret: 'existing-secret' } } })
  api.update.mockResolvedValue({})
  api.create.mockResolvedValue({ data: { metadata: { name: 'new-secret' } } })
})
afterEach(() => { document.body.innerHTML = '' })
async function open() {
  const form = document.createElement('form'); document.body.append(form)
  const wrapper = mount(PaymentView, { attachTo: form }); await flushPromises()
  return { wrapper, form }
}
it('saves independent channel switches and retains the key reference on blank input', async () => {
  const { wrapper, form } = await open()
  expect(wrapper.findAll('[role=combobox]')).toHaveLength(2)
  await wrapper.findAll('.channel input')[2].setValue(true)
  await wrapper.findAll('[role=combobox]')[2].trigger('click')
  await wrapper.findAll('[role=option]').find(option => option.text() === 'tokenpay')!.trigger('click')
  form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); await flushPromises()
  expect(api.create).not.toHaveBeenCalled()
  expect(api.update.mock.calls[0][0].body).toMatchObject({ unrelated: { keep: true }, commerce_payment: { merchant_secret: 'existing-secret', usdt_enabled: true, usdt_provider: 'tokenpay' } })
  wrapper.unmount()
})
it('stores a typed key in Secret and saves only the reference in theme config', async () => {
  const { wrapper, form } = await open()
  await wrapper.find('input[type=password]').setValue('test-only-key')
  form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); await flushPromises()
  expect(api.create.mock.calls[0][0].secret.stringData).toEqual({ merchant_key: 'test-only-key' })
  expect(api.update.mock.calls[0][0].body.commerce_payment.merchant_secret).toBe('new-secret')
  expect(JSON.stringify(api.update.mock.calls)).not.toContain('test-only-key')
  expect((wrapper.find('input[type=password]').element as HTMLInputElement).value).toBe('')
  wrapper.unmount()
})
