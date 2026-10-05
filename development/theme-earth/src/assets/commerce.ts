type CartItem = { product: string; sku: string; title: string; skuTitle: string; quantity: number };
const storageKey = "earth-commerce-cart-v1";
function readCart(): CartItem[] {
  const value: unknown = JSON.parse(localStorage.getItem(storageKey) || "[]");
  if (!Array.isArray(value) || value.length > 100) throw new Error("购物车数据无效，请清理浏览器中的购物车数据。");
  return value.map((item: unknown) => {
    const entry = item as Partial<CartItem> | null;
    if (!entry || typeof entry.product !== "string" || !entry.product || typeof entry.sku !== "string" || !entry.sku ||
      typeof entry.title !== "string" || typeof entry.skuTitle !== "string" || !Number.isInteger(entry.quantity) ||
      (entry.quantity ?? 0) < 1 || (entry.quantity ?? 0) > 10000) throw new Error("购物车数据无效。");
    return entry as CartItem;
  });
}
function saveCart(items: CartItem[]) { localStorage.setItem(storageKey, JSON.stringify(items)); }
function parameters(items: CartItem[]) {
  const query = new URLSearchParams();
  for (const item of items) {
    query.append("product", item.product); query.append("sku", item.sku); query.append("quantity", String(item.quantity));
  }
  return query;
}
for (const button of document.querySelectorAll<HTMLButtonElement>("[data-add-to-cart]")) {
  button.addEventListener("click", () => {
    const form = button.closest("form");
    const message = form?.querySelector<HTMLElement>("[data-cart-message]");
    if (!form || !message || !form.reportValidity()) return;
    try {
      const data = new FormData(form);
      const item: CartItem = { product: String(data.get("product")), sku: String(data.get("sku")),
        title: String(data.get("productTitle")), skuTitle: String(data.get("skuTitle")), quantity: Number(data.get("quantity")) };
      const cart = readCart();
      const existing = cart.find(entry => entry.product === item.product && entry.sku === item.sku);
      if (existing) {
        if (existing.quantity + item.quantity > 10000) throw new Error("单个规格最多选择 10000 件。");
        existing.quantity += item.quantity;
      } else {
        if (cart.length >= 100) throw new Error("购物车最多包含 100 个规格。");
        cart.push(item);
      }
      saveCart(cart); message.textContent = "已加入购物车";
    } catch (error) { message.textContent = error instanceof Error ? error.message : "无法保存购物车。"; }
  });
}
const purchaseOptions = document.querySelector<HTMLElement>("[data-purchase-options]");
const mobilePurchaseBar = document.querySelector<HTMLElement>("[data-mobile-purchase-bar]");
mobilePurchaseBar?.querySelector<HTMLButtonElement>("[data-scroll-purchase]")?.addEventListener("click", () => {
  purchaseOptions?.scrollIntoView({ behavior: "smooth", block: "start" });
});
if (purchaseOptions && mobilePurchaseBar && typeof IntersectionObserver !== "undefined") {
  const purchaseObserver = new IntersectionObserver(([entry]) => {
    if (entry) mobilePurchaseBar.hidden = entry.isIntersecting;
  }, { rootMargin: "0px 0px -80px 0px" });
  purchaseObserver.observe(purchaseOptions);
}
for (const gallery of document.querySelectorAll<HTMLElement>("[data-product-gallery]")) {
  const images = [...gallery.querySelectorAll<HTMLImageElement>("[data-gallery-image]")];
  const thumbnails = [...gallery.querySelectorAll<HTMLButtonElement>("[data-gallery-thumbnail]")];
  thumbnails.forEach((thumbnail, selectedIndex) => {
    thumbnail.addEventListener("click", () => {
      images.forEach((image, imageIndex) => image.classList.toggle("hidden", imageIndex !== selectedIndex));
      thumbnails.forEach((item, imageIndex) => {
        item.setAttribute("aria-pressed", String(imageIndex === selectedIndex));
        item.classList.toggle("border-blue-600", imageIndex === selectedIndex);
        item.classList.toggle("border-transparent", imageIndex !== selectedIndex);
      });
    });
  });
}
const root = document.querySelector<HTMLElement>("[data-commerce-cart]");
if (root) {
  const container = root.querySelector<HTMLElement>("[data-cart-items]")!;
  const status = root.querySelector<HTMLElement>("[data-cart-status]")!;
  const total = root.querySelector<HTMLElement>("[data-cart-total]")!;
  const checkout = root.querySelector<HTMLButtonElement>("[data-cart-checkout]")!;
  let cart: CartItem[] = [];
  let request: AbortController | undefined;
  let revision = 0;
  async function refreshQuote() {
    const current = ++revision;
    request?.abort(); checkout.disabled = true; total.textContent = "";
    if (!cart.length) { status.textContent = "购物车为空，请先选购商品。"; return; }
    const controller = new AbortController(); request = controller;
    const timer = setTimeout(() => controller.abort(), 10000);
    status.textContent = "正在核对价格与库存…";
    try {
      const response = await fetch(`/shop/api/quote?${parameters(cart)}`, { signal: controller.signal });
      if (!response.ok) throw new Error("部分商品已下架、规格失效或库存不足，请调整购物车后重试。");
      const quote = await response.json() as { totalMinor: number };
      if (current !== revision) return;
      if (!Number.isSafeInteger(quote.totalMinor) || quote.totalMinor < 0) throw new Error("报价格式无效，请重试。");
      total.textContent = `合计 ¥${(quote.totalMinor / 100).toFixed(2)}`;
      status.textContent = "已按当前商品价格核对。"; checkout.disabled = false;
    } catch (error) {
      if (current === revision) status.textContent = controller.signal.aborted ? "报价请求超时，请刷新重试。" :
        error instanceof Error ? error.message : "报价失败，请重试。";
    } finally { clearTimeout(timer); }
  }
  function render() {
    container.replaceChildren();
    cart.forEach((item, index) => {
      const row = document.createElement("div"); row.className = "flex flex-wrap items-center gap-4 border-b border-gray-200 pb-4";
      const label = document.createElement("p"); label.className = "grow dark:text-neutral-100";
      label.textContent = `${item.title} · ${item.skuTitle}`;
      const input = document.createElement("input"); input.type = "number"; input.min = "1"; input.max = "10000";
      input.value = String(item.quantity); input.setAttribute("aria-label", `${item.title}数量`);
      input.className = "w-24 rounded border border-gray-300 px-3 py-2 dark:bg-neutral-950";
      input.addEventListener("change", () => {
        const quantity = Number(input.value);
        if (!Number.isInteger(quantity) || quantity < 1 || quantity > 10000) { input.value = String(item.quantity); return; }
        const previous = item.quantity; item.quantity = quantity;
        try { saveCart(cart); void refreshQuote(); }
        catch { item.quantity = previous; input.value = String(previous); status.textContent = "无法保存购物车修改。"; }
      });
      const remove = document.createElement("button"); remove.type = "button"; remove.textContent = "移除";
      remove.className = "text-red-600";
      remove.addEventListener("click", () => {
        const updated = cart.filter((_, current) => current !== index);
        try { saveCart(updated); cart = updated; render(); void refreshQuote(); }
        catch { status.textContent = "无法保存购物车修改。"; }
      });
      row.append(label, input, remove); container.append(row);
    });
  }
  checkout.addEventListener("click", () => { if (!checkout.disabled) window.location.assign(`/shop/checkout?${parameters(cart)}`); });
  try { cart = readCart(); render(); void refreshQuote(); }
  catch (error) { status.textContent = error instanceof Error ? error.message : "无法读取购物车。"; }
}

function mutationHeaders(json = false) {
  const headers: Record<string, string> = json ? { "Content-Type": "application/json" } : {};
  const token = document.querySelector<HTMLMetaElement>('meta[name="commerce-csrf-token"]')?.content;
  const header = document.querySelector<HTMLMetaElement>('meta[name="commerce-csrf-header"]')?.content;
  if (token && header) headers[header] = token;
  return headers;
}
const createOrderRoot = document.querySelector<HTMLElement>("[data-commerce-checkout]");
if (createOrderRoot) {
  const form = createOrderRoot.querySelector<HTMLFormElement>("[data-order-create]")!;
  const submit = form.querySelector<HTMLButtonElement>("[data-create-order]")!;
  const message = form.querySelector<HTMLElement>("[data-order-message]")!;
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!form.reportValidity()) return;
    const query = new URLSearchParams(window.location.search);
    const products = query.getAll("product"); const skus = query.getAll("sku"); const quantities = query.getAll("quantity");
    if (!products.length || products.length > 100 || products.length !== skus.length || products.length !== quantities.length) {
      message.textContent = "购物车信息有误，请返回购物车重试。"; return;
    }
    const selections = products.map((product, index) => ({ productName: product, skuId: skus[index]!, quantity: Number(quantities[index]) }));
    const shipping = form.querySelector<HTMLFieldSetElement>("[data-shipping-fields]");
    const data = new FormData(form);
    const body = { selections, ...(shipping ? { shippingAddress: {
      recipient: String(data.get("recipient")), phone: String(data.get("phone")), address: String(data.get("address")),
    } } : {}) };
    submit.disabled = true; message.textContent = "正在创建订单并预留库存…";
    try {
      const response = await fetch("/shop/api/orders", { method: "POST", headers: mutationHeaders(true),
        body: JSON.stringify(body), signal: AbortSignal.timeout(15000) });
      if (response.status === 401) { message.textContent = "请先登录后再创建订单。"; return; }
      if (response.status === 403) { message.textContent = "页面验证已失效，请刷新后重新下单。"; return; }
      if (!response.ok) {
        const result = await response.json().catch(() => ({})) as { message?: string };
        throw new Error(result.message || "订单创建失败，请刷新后重试。");
      }
      const order = await response.json() as { name: string };
      try {
        const cart = readCart();
        const remaining = cart.filter(item => !selections.some(selection => selection.productName === item.product && selection.skuId === item.sku));
        saveCart(remaining);
      } catch { /* The server order is already durable; preserve navigation even if local storage is unavailable. */ }
      window.location.assign(`/shop/orders?created=${encodeURIComponent(order.name)}`);
    } catch (error) { message.textContent = error instanceof Error ? error.message : "订单创建失败，请重试。"; }
    finally { submit.disabled = false; }
  });
}
for (const button of document.querySelectorAll<HTMLButtonElement>("[data-cancel-order]")) {
  button.addEventListener("click", async () => {
    const name = button.dataset.order;
    const message = document.querySelector<HTMLElement>("[data-order-message]");
    if (!name || !message) return;
    button.disabled = true;
    try {
      const response = await fetch(`/shop/api/orders/${encodeURIComponent(name)}/cancel`, { method: "POST", headers: mutationHeaders(), signal: AbortSignal.timeout(10000) });
      if (!response.ok) throw new Error(response.status === 401 ? "请登录后管理订单。" : "取消失败，请刷新订单后重试。");
      window.location.reload();
    } catch (error) {
      message.textContent = error instanceof Error ? error.message : "取消失败，请重试。";
      button.disabled = false;
    }
  });
}
for (const button of document.querySelectorAll<HTMLButtonElement>("[data-digital-delivery]")) {
  button.addEventListener("click", async () => {
    const name = button.dataset.order;
    const result = button.parentElement?.querySelector<HTMLElement>("[data-digital-result]");
    if (!name || !result) return;
    button.disabled = true; result.textContent = "正在安全读取交付内容…";
    try {
      const response = await fetch(`/shop/api/orders/${encodeURIComponent(name)}/digital-delivery`, {
        signal: AbortSignal.timeout(10000),
      });
      if (!response.ok) throw new Error(response.status === 401 ? "登录后可查看已付款的数字商品。" :
        response.status === 409 ? "订单尚未付款或交付暂不可用。" : "读取失败，请刷新订单后重试。");
      const deliveries = await response.json() as Array<{ productTitle: string; skuTitle: string; quantity: number; codes: string[] }>;
      result.replaceChildren();
      if (!deliveries.length) { result.textContent = "此订单没有数字商品。"; return; }
      for (const delivery of deliveries) {
        const section = document.createElement("section"); section.className = "rounded-lg bg-gray-50 p-4 dark:bg-neutral-700";
        const title = document.createElement("h3"); title.className = "font-medium";
        title.textContent = `${delivery.productTitle} · ${delivery.skuTitle} × ${delivery.quantity}`;
        const list = document.createElement("ul"); list.className = "mt-2 space-y-1 font-mono break-all";
        for (const code of delivery.codes) { const item = document.createElement("li"); item.textContent = code; list.append(item); }
        section.append(title, list); result.append(section);
      }
    } catch (error) {
      result.textContent = error instanceof Error ? error.message : "读取失败，请重试。";
    } finally { button.disabled = false; }
  });
}
