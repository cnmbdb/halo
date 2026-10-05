import { axiosInstance } from '@halo-dev/api-client'

export type Category = {
  apiVersion: string; kind: string;
  metadata: { name: string; version?: number; annotations?: Record<string, string> };
  spec: { title: string; slug: string; description: string; priority: number }
}
export const categoryEndpoint = '/apis/commerce.halo.run/v1alpha1/productcategories'
export async function listCategories(): Promise<Category[]> {
  const items: Category[] = []
  let page = 1
  let total = 0
  do {
    const { data } = await axiosInstance.get<{ items: Category[]; total: number }>(categoryEndpoint, { params: { page, size: 100 } })
    items.push(...data.items)
    total = data.total
    if (!data.items.length) break
    page++
  } while (items.length < total)
  return items.sort((a, b) => a.spec.priority - b.spec.priority)
}

/** Persist the initial commerce category; do not confuse it with the blog's category. */
export async function ensureDefaultCategory(): Promise<void> {
  try {
    await axiosInstance.get(`${categoryEndpoint}/default`)
    return
  } catch (error) {
    if ((error as { response?: { status: number } }).response?.status !== 404) throw error
  }
  try {
    await axiosInstance.post(categoryEndpoint, {
      apiVersion: 'commerce.halo.run/v1alpha1', kind: 'ProductCategory',
      metadata: { name: 'default' },
      spec: { title: '默认分类', slug: 'default', description: '商城默认商品分类', priority: 0 },
    })
  } catch (error) {
    if ((error as { response?: { status: number } }).response?.status !== 409) throw error
    await axiosInstance.get(`${categoryEndpoint}/default`)
  }
}
