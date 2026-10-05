// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn() }))
vi.mock('@halo-dev/api-client', () => ({ axiosInstance: api }))
vi.mock('../api/categories', () => ({ listCategories: vi.fn().mockResolvedValue([
  { metadata: { name: 'default' }, spec: { title: '默认分类' } },
  { metadata: { name: 'accessories' }, spec: { title: '配件' } },
]) }))

import ProductsView from './ProductsView.vue'

function hostForm() {
  const form = document.createElement('form')
  document.body.append(form)
  return form
}
async function submitThemeForm(wrapper: ReturnType<typeof mount>) {
  wrapper.element.closest('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
  await flushPromises()
}

function findLabel(wrapper: ReturnType<typeof mount>, labelText: string) {
  const label = wrapper.findAll('label').find(item => item.text().startsWith(labelText))
  if (!label) throw new Error(`Missing field label: ${labelText}`)
  return label
}

async function openNewProduct() {
  api.get.mockResolvedValue({ data: { items: [], total: 0 } })
  const wrapper = mount(ProductsView, { attachTo: hostForm(), global: { stubs: { FormKit: true } } })
  await flushPromises()
  await wrapper.findAll('button').find(button => button.text() === '新增商品')!.trigger('click')
  await flushPromises()
  return wrapper
}

describe('ProductsView', () => {
  afterEach(() => { document.body.innerHTML = '' })
  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('crypto', { randomUUID: () => 'new-product-id' })
  })

  it('creates a physical product with a server resource payload', async () => {
    const wrapper = await openNewProduct()
    expect(wrapper.find('form').exists()).toBe(false)
    expect(wrapper.findAll('button').some(button => button.text() === '保存商品')).toBe(false)
    await findLabel(wrapper, '商品名称').find('input').setValue('桌面摆件')
    await findLabel(wrapper, '商品路径').find('input').setValue('desk-figure')
    await findLabel(wrapper, '价格（分）').find('input').setValue('2590')
    await findLabel(wrapper, '库存').find('input').setValue('6')
    await findLabel(wrapper, '默认分类').find('input').setValue(true)
    await findLabel(wrapper, '配件').find('input').setValue(true)
    await submitThemeForm(wrapper)
    await flushPromises()

    expect(api.post).toHaveBeenCalledOnce()
    const [url, payload] = api.post.mock.calls[0]
    expect(url).toBe('/apis/commerce.halo.run/v1alpha1/products')
    expect(payload).toMatchObject({
      apiVersion: 'commerce.halo.run/v1alpha1',
      kind: 'Product',
      metadata: { name: 'product-new-product-id' },
      spec: {
        title: '桌面摆件', slug: 'desk-figure', type: 'PHYSICAL', state: 'DRAFT',
        categories: ['default', 'accessories'],
        skus: [{ id: 'default', title: '标准款', priceMinor: 2590, stock: 6, digitalCodes: [] }],
      },
    })
  })

  it('blocks publishing digital stock without enough one-time codes', async () => {
    const wrapper = await openNewProduct()
    await findLabel(wrapper, '商品名称').find('input').setValue('数字兑换卡')
    await findLabel(wrapper, '商品路径').find('input').setValue('digital-card')
    await findLabel(wrapper, '类型').find('select').setValue('DIGITAL')
    await findLabel(wrapper, '发布状态').find('select').setValue('PUBLISHED')
    await findLabel(wrapper, '库存').find('input').setValue('2')
    await findLabel(wrapper, '数字兑换码').find('textarea').setValue('CODE-ONE')
    await submitThemeForm(wrapper)
    await flushPromises()

    expect(wrapper.find('[role="alert"]').text()).toContain('兑换码数量必须不少于库存')
    expect(api.post).not.toHaveBeenCalled()
  })

  it('publishes digital stock with one unique code per unit and removes editor-only fields', async () => {
    const wrapper = await openNewProduct()
    await findLabel(wrapper, '商品名称').find('input').setValue('数字兑换卡')
    await findLabel(wrapper, '商品路径').find('input').setValue('digital-card')
    await findLabel(wrapper, '类型').find('select').setValue('DIGITAL')
    await findLabel(wrapper, '发布状态').find('select').setValue('PUBLISHED')
    await findLabel(wrapper, '库存').find('input').setValue('2')
    await findLabel(wrapper, '数字兑换码').find('textarea').setValue('CODE-ONE\nCODE-TWO')
    await submitThemeForm(wrapper)
    await flushPromises()

    expect(api.post).toHaveBeenCalledOnce()
    const payload = api.post.mock.calls[0][1]
    expect(payload.spec.state).toBe('PUBLISHED')
    expect(payload.spec.skus[0].digitalCodes).toEqual(['CODE-ONE', 'CODE-TWO'])
    expect(payload.spec.skus[0]).not.toHaveProperty('digitalCodesText')
  })

  it('updates the existing versioned product instead of creating a duplicate', async () => {
    api.get.mockResolvedValue({ data: { items: [{
      apiVersion: 'commerce.halo.run/v1alpha1', kind: 'Product',
      metadata: { name: 'desk-figure', version: 7 },
      spec: { title: '桌面摆件', slug: 'desk-figure', type: 'PHYSICAL', state: 'PUBLISHED',
        description: '', images: [], categories: ['default'],
        skus: [{ id: 'default', title: '标准款', priceMinor: 2590, stock: 6 }], featured: false },
    }], total: 1 } })
    const wrapper = mount(ProductsView, { attachTo: hostForm(), global: { stubs: { FormKit: true } } })
    await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === '编辑')!.trigger('click')
    expect(findLabel(wrapper, '默认分类').find('input').element.checked).toBe(true)
    await findLabel(wrapper, '默认分类').find('input').setValue(false)
    await findLabel(wrapper, '配件').find('input').setValue(true)
    await findLabel(wrapper, '商品名称').find('input').setValue('桌面摆件（新版）')
    await submitThemeForm(wrapper)
    await flushPromises()

    expect(api.post).not.toHaveBeenCalled()
    expect(api.put).toHaveBeenCalledOnce()
    expect(api.put.mock.calls[0][0]).toBe('/apis/commerce.halo.run/v1alpha1/products/desk-figure')
    expect(api.put.mock.calls[0][1].metadata.version).toBe(7)
    expect(api.put.mock.calls[0][1].spec.title).toBe('桌面摆件（新版）')
    expect(api.put.mock.calls[0][1].spec.categories).toEqual(['accessories'])
  })
})
