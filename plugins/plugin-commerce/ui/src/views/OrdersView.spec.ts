// @vitest-environment jsdom
import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import OrdersView from './OrdersView.vue'
const { get } = vi.hoisted(() => ({ get: vi.fn() }))
vi.mock('@halo-dev/api-client', () => ({ axiosInstance: { get } }))
beforeEach(() => get.mockReset())
it('shows an empty state only after a successful order request', async () => {
  get.mockResolvedValueOnce({ data: { items: [], total: 0 } })
  const wrapper = mount(OrdersView)
  await flushPromises()
  expect(wrapper.text()).toContain('暂无订单')
  expect(wrapper.find('[role="alert"]').exists()).toBe(false)
})
it('separates forbidden requests from an empty list and allows retry', async () => {
  get.mockRejectedValueOnce({ response: { status: 403 } })
  const wrapper = mount(OrdersView)
  await flushPromises()
  expect(wrapper.get('[role="alert"]').text()).toContain('当前账号没有订单管理权限')
  expect(wrapper.text()).not.toContain('暂无订单')
  get.mockResolvedValueOnce({ data: { items: [], total: 0 } })
  await wrapper.get('button').trigger('click')
  await flushPromises()
  expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  expect(wrapper.text()).toContain('暂无订单')
})
