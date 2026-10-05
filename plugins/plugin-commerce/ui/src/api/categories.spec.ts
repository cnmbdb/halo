import { beforeEach, expect, it, vi } from 'vitest'
import { listCategories, ensureDefaultCategory } from './categories'
const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))
vi.mock('@halo-dev/api-client', () => ({ axiosInstance: { get, post } }))
beforeEach(() => { get.mockReset(); post.mockReset() })
it('collects every page and sorts categories by priority', async () => {
  const item = (name: string, priority: number) => ({ metadata: { name }, spec: { priority } })
  get.mockResolvedValueOnce({ data: { items: [item('later', 10)], total: 2 } })
    .mockResolvedValueOnce({ data: { items: [item('first', -1)], total: 2 } })
  expect((await listCategories()).map(item => item.metadata.name)).toEqual(['first', 'later'])
  expect(get.mock.calls[1]?.[1]).toEqual({ params: { page: 2, size: 100 } })
})
it('stops if a category is removed while paging', async () => {
  get.mockResolvedValueOnce({ data: { items: [], total: 10 } })
  expect(await listCategories()).toEqual([])
  expect(get).toHaveBeenCalledTimes(1)
})
it('propagates failed requests rather than silently returning partial categories', async () => {
  get.mockRejectedValueOnce(new Error('denied'))
  await expect(listCategories()).rejects.toThrow('denied')
})

it('preserves an existing default category without overwriting it', async () => {
  get.mockResolvedValueOnce({ data: { spec: { title: '自定义默认分类' } } })
  await ensureDefaultCategory()
  expect(post).not.toHaveBeenCalled()
})
it('creates the missing persistent default category', async () => {
  get.mockRejectedValueOnce({ response: { status: 404 } })
  post.mockResolvedValueOnce({ data: {} })
  await ensureDefaultCategory()
  expect(post.mock.calls[0]?.[1]).toMatchObject({ metadata: { name: 'default' }, spec: { title: '默认分类', slug: 'default' } })
})
it('accepts a concurrent creation only after reading the existing category', async () => {
  get.mockRejectedValueOnce({ response: { status: 404 } }).mockResolvedValueOnce({ data: {} })
  post.mockRejectedValueOnce({ response: { status: 409 } })
  await ensureDefaultCategory()
  expect(get).toHaveBeenCalledTimes(2)
})
it('does not turn authorization or network failures into category writes', async () => {
  get.mockRejectedValueOnce(new Error('denied'))
  await expect(ensureDefaultCategory()).rejects.toThrow('denied')
  expect(post).not.toHaveBeenCalled()
})
