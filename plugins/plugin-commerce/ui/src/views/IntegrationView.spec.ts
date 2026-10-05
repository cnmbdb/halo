// @vitest-environment jsdom
import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import IntegrationView from './IntegrationView.vue'
const { get } = vi.hoisted(() => ({ get: vi.fn() }))
vi.mock('@halo-dev/api-client', () => ({ axiosInstance: { get } }))
beforeEach(() => get.mockReset())
it('shows the current phase and version, then refreshes them', async () => {
  get.mockResolvedValueOnce({ data: { spec: { version: '1.0' }, status: { phase: 'STARTED' } } })
  const wrapper = mount(IntegrationView)
  await flushPromises()
  expect(wrapper.get('[role="status"]').text()).toBe('运行中')
  expect(wrapper.text()).toContain('1.0')
  get.mockResolvedValueOnce({ data: { spec: { version: '1.1' }, status: { phase: 'STOPPED' } } })
  await wrapper.get('button').trigger('click')
  await flushPromises()
  expect(wrapper.get('[role="status"]').text()).toBe('已停止')
  expect(wrapper.text()).toContain('1.1')
})
it('reports unavailable status without claiming the plugin is running', async () => {
  get.mockRejectedValueOnce(new Error('denied'))
  const wrapper = mount(IntegrationView)
  await flushPromises()
  expect(wrapper.get('[role="status"]').text()).toBe('状态不可用')
  expect(wrapper.get('[role="alert"]').text()).toContain('插件详情')
  expect(wrapper.find('a[href="/console/plugins/commerce"]').exists()).toBe(true)
})
