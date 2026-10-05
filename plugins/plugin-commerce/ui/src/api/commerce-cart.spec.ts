// @vitest-environment jsdom
import { beforeEach, afterEach, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'
beforeEach(() => { vi.resetModules(); localStorage.clear(); document.body.innerHTML = '' })
afterEach(() => vi.unstubAllGlobals())
const source = () => import('../../../../../development/theme-earth/src/assets/commerce')
it('adds only selected quantities and merges the same SKU', async () => {
  document.body.innerHTML = '<form><input name="product" value="book"><input name="sku" value="sku"><input name="productTitle" value="Book"><input name="skuTitle" value="Standard"><input name="quantity" type="number" value="2"><button type="button" data-add-to-cart>Add</button><p data-cart-message></p></form>'
  await source()
  const button = document.querySelector<HTMLButtonElement>('button')!
  button.click(); button.click()
  const cart = JSON.parse(localStorage.getItem('earth-commerce-cart-v1')!)
  expect(cart).toEqual([{ product: 'book', sku: 'sku', title: 'Book', skuTitle: 'Standard', quantity: 4 }])
})
it('renders titles as text and uses server total rather than browser data', async () => {
  localStorage.setItem('earth-commerce-cart-v1', JSON.stringify([{ product: 'book', sku: 'sku', title: '<img src=x onerror=alert(1)>', skuTitle: 'Standard', quantity: 2, priceMinor: 1 }]))
  document.body.innerHTML = '<main data-commerce-cart><div data-cart-items></div><p data-cart-status></p><p data-cart-total></p><button data-cart-checkout disabled></button></main>'
  const fetch = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ totalMinor: 3998 }) })
  vi.stubGlobal('fetch', fetch)
  await source(); await flushPromises()
  expect(document.querySelector('img')).toBeNull()
  expect(document.querySelector('[data-cart-total]')?.textContent).toBe('合计 ¥39.98')
  expect(fetch.mock.calls[0]?.[0]).toBe('/shop/api/quote?product=book&sku=sku&quantity=2')
  expect(document.querySelector<HTMLButtonElement>('[data-cart-checkout]')?.disabled).toBe(false)
})
it('keeps checkout disabled when stock validation fails', async () => {
  localStorage.setItem('earth-commerce-cart-v1', JSON.stringify([{ product: 'book', sku: 'sku', title: 'Book', skuTitle: 'Standard', quantity: 2 }]))
  document.body.innerHTML = '<main data-commerce-cart><div data-cart-items></div><p data-cart-status></p><p data-cart-total></p><button data-cart-checkout disabled></button></main>'
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false }))
  await source(); await flushPromises()
  expect(document.querySelector<HTMLButtonElement>('[data-cart-checkout]')?.disabled).toBe(true)
  expect(document.querySelector('[data-cart-status]')?.textContent).toContain('库存不足')
})
it('includes the page CSRF header when creating orders and explains expired verification', async () => {
  window.history.replaceState({}, '', '?product=book&sku=sku&quantity=1')
  document.head.innerHTML = '<meta name="commerce-csrf-token" content="test-token"><meta name="commerce-csrf-header" content="X-CSRF-TOKEN">'
  document.body.innerHTML = '<main data-commerce-checkout><form data-order-create><p data-order-message></p><button type="submit" data-create-order>Create</button></form></main>'
  const fetch = vi.fn().mockResolvedValue({ status: 403, ok: false })
  vi.stubGlobal('fetch', fetch)
  await source()
  document.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
  await flushPromises()
  expect(fetch).toHaveBeenCalledWith('/shop/api/orders', expect.objectContaining({ headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': 'test-token' } }))
  expect(document.querySelector('[data-order-message]')!.textContent).toContain('页面验证已失效')
  document.head.innerHTML = ''; window.history.replaceState({}, '', '/')
})
it('sends the page CSRF token when cancelling a held order', async () => {
  document.head.innerHTML = '<meta name="commerce-csrf-token" content="test-token"><meta name="commerce-csrf-header" content="X-CSRF-TOKEN">'
  document.body.innerHTML = '<p data-order-message></p><button data-cancel-order data-order="order-test">Cancel</button>'
  const fetch = vi.fn().mockResolvedValue({ ok: false, status: 403 })
  vi.stubGlobal('fetch', fetch)
  await source(); document.querySelector<HTMLButtonElement>('button')!.click(); await flushPromises()
  expect(fetch).toHaveBeenCalledWith('/shop/api/orders/order-test/cancel', expect.objectContaining({ headers: { 'X-CSRF-TOKEN': 'test-token' } }))
  document.head.innerHTML = ''
})
