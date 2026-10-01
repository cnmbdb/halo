const api = "/apis/api.shop.cnmbdb.github.io/v1alpha1/products";
const orderApi = "/apis/uc.api.shop.cnmbdb.github.io/v1alpha1/orders";
const money = cents => new Intl.NumberFormat("zh-CN", {style:"currency",currency:"CNY"}).format(cents / 100);
const productsNode = document.getElementById("products");
const cartNode = document.getElementById("cart-items");
const cartTotal = document.getElementById("cart-total");
const shopStatus = document.getElementById("shop-status");
const checkoutStatus = document.getElementById("checkout-status");
const form = document.getElementById("checkout-form");
let products = [];
let cart = loadCart();

function loadCart() {
  try { const value = JSON.parse(localStorage.getItem("halo-shop-cart") || "{}"); return value && typeof value === "object" && !Array.isArray(value) ? value : {}; }
  catch { return {}; }
}
function saveCart() { localStorage.setItem("halo-shop-cart", JSON.stringify(cart)); renderCart(); }
function item(name) { return products.find(product => product.name === name); }
function renderProducts() {
  productsNode.replaceChildren();
  for (const product of products) {
    const card = document.createElement("article"); card.className = "card";
    if (product.imageUrl) { const image = document.createElement("img"); image.src = product.imageUrl; image.alt = product.title || "商品图片"; image.loading = "lazy"; card.append(image); }
    const title = document.createElement("h3"); title.textContent = product.title || product.name;
    const description = document.createElement("p"); description.textContent = product.description || "";
    const price = document.createElement("p"); price.className = "price"; price.textContent = money(product.priceCents);
    const button = document.createElement("button"); button.className = "button"; button.type = "button"; button.textContent = product.stock > 0 ? "加入购物车" : "暂时缺货"; button.disabled = product.stock < 1;
    button.addEventListener("click", () => { cart[product.name] = Math.min(product.stock, (Number(cart[product.name]) || 0) + 1); saveCart(); });
    card.append(title, description, price, button); productsNode.append(card);
  }
  shopStatus.textContent = products.length ? "" : "暂无已上架商品。";
}
function renderCart() {
  cartNode.replaceChildren(); let total = 0;
  for (const [name, count] of Object.entries(cart)) {
    const product = item(name); if (!product || !Number.isInteger(count) || count < 1) { delete cart[name]; continue; }
    const row = document.createElement("div"); row.className = "cart-row";
    const label = document.createElement("span"); label.textContent = `${product.title} × ${count}`;
    const remove = document.createElement("button"); remove.type = "button"; remove.textContent = "移除"; remove.setAttribute("aria-label", `移除${product.title}`);
    remove.addEventListener("click", () => { delete cart[name]; saveCart(); });
    row.append(label, remove); cartNode.append(row); total += product.priceCents * count;
  }
  if (!cartNode.childElementCount) cartNode.textContent = "购物车是空的。";
  cartTotal.textContent = money(total);
}
async function loadProducts() {
  try { const response = await fetch(api, {credentials:"same-origin"}); if (!response.ok) throw new Error("加载商品失败"); products = await response.json(); renderProducts(); renderCart(); }
  catch { shopStatus.textContent = "商品暂时无法加载，请稍后重试。"; }
}
form.addEventListener("submit", async event => {
  event.preventDefault(); checkoutStatus.textContent = "";
  const items = Object.entries(cart).map(([productName, quantity]) => ({productName, quantity}));
  if (!items.length) { checkoutStatus.textContent = "请先选择商品。"; return; }
  const data = new FormData(form);
  const xsrf = document.cookie.split("; ").find(cookie => cookie.startsWith("XSRF-TOKEN="));
  const headers = {"Content-Type":"application/json"}; if (xsrf) headers["X-XSRF-TOKEN"] = decodeURIComponent(xsrf.split("=")[1]);
  try {
    const response = await fetch(orderApi, {method:"POST",credentials:"same-origin",headers,body:JSON.stringify({customerName:data.get("customerName"),phone:data.get("phone"),address:data.get("address"),items})});
    if (response.status === 401 || response.status === 403 || response.redirected) { checkoutStatus.textContent = "请登录 Halo 账号并确认拥有“商城下单”权限。"; return; }
    if (!response.ok) throw new Error("提交失败，请检查库存后重试。");
    const receipt = await response.json(); checkoutStatus.textContent = `订单 ${receipt.orderName} 已提交，金额 ${money(receipt.totalCents)}，等待店主确认。`;
    cart = {}; saveCart(); form.reset();
  } catch (error) { checkoutStatus.textContent = error.message || "提交失败，请稍后重试。"; }
});
loadProducts();
