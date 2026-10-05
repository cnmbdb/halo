// @vitest-environment jsdom
import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import CategoriesView from './CategoriesView.vue'
const { put, listCategories, ensureDefaultCategory } = vi.hoisted(() => ({ put: vi.fn(), listCategories: vi.fn(), ensureDefaultCategory: vi.fn() }))
vi.mock('@halo-dev/api-client', () => ({ axiosInstance: { put } }))
vi.mock('../api/categories', () => ({ categoryEndpoint: '/categories', listCategories, ensureDefaultCategory }))
const key = 'commerce.halo.run/category-image'
function mountView() { return mount(CategoriesView, { global: { stubs: { FormKit: true } } }) }
beforeEach(() => {
  vi.clearAllMocks()
  ensureDefaultCategory.mockResolvedValue(undefined)
  listCategories.mockResolvedValue([{ metadata: { name: 'default', annotations: { other: 'preserved', [key]: '/old.gif' } }, spec: { title: '默认分类', slug: 'default', description: '', priority: 0 } }])
  put.mockResolvedValue({})
})
it('saves a GIF with its category while preserving other annotations', async () => {
  const w = mountView(); await flushPromises(); await w.get('tbody button').trigger('click')
  expect((w.get('input[placeholder]').element as HTMLInputElement).value).toBe('/old.gif')
  await w.get('input[placeholder]').setValue('/new.gif'); await w.get('form').trigger('submit'); await flushPromises()
  expect(put.mock.calls[0][1].metadata.annotations).toEqual({ other: 'preserved', [key]: '/new.gif' })
})
it('clears only the category image when requested', async () => {
  const w = mountView(); await flushPromises(); await w.get('tbody button').trigger('click')
  await w.get('input[placeholder]').setValue(''); await w.get('form').trigger('submit'); await flushPromises()
  expect(put.mock.calls[0][1].metadata.annotations).toEqual({ other: 'preserved' })
})
