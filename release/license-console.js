/* Injected inside the shipped console bundle, where Vue=e and HaloApiClient=D. */
function useAosteLicense() {
  const key = e.ref("");
  const busy = e.ref(false);
  const status = e.ref(null);
  const error = e.ref("");
  let alive = true;
  e.onUnmounted(() => { alive = false; key.value = ""; });
  async function load() {
    busy.value = true;
    error.value = "";
    try {
      const response = await D.axiosInstance.get("/shop/api/admin/license");
      if (alive) status.value = response.data;
    } catch (cause) {
      if (alive) error.value = "无法读取授权状态，请确认插件已升级、已启用且当前账号具有超级管理员权限。";
    } finally { if (alive) busy.value = false; }
  }
  async function save() {
    if (busy.value) return;
    if (!key.value.trim()) { error.value = "请填写授权码。"; return; }
    busy.value = true;
    error.value = "";
    try {
      const response = await D.axiosInstance.put("/shop/api/admin/license", { key: key.value.trim() });
      if (alive) { status.value = response.data; key.value = ""; }
    } catch (cause) {
      // A rejected replacement must not overwrite the existing server credential/status.
      if (alive) error.value = cause.response?.data?.message || "授权未保存，请检查授权码、管理权限或授权服务网络后重试。";
    } finally { if (alive) busy.value = false; }
  }
  async function clear() {
    if (busy.value || !window.confirm("移除后主题和商城将停止对外服务，已有订单处理保留。确认移除授权？")) return;
    busy.value = true;
    error.value = "";
    try {
      const response = await D.axiosInstance.delete("/shop/api/admin/license");
      if (alive) { status.value = response.data; key.value = ""; }
    } catch { if (alive) error.value = "授权移除失败，请稍后重试。"; }
    finally { if (alive) busy.value = false; }
  }
  e.onMounted(load);
  return function renderAosteLicense() {
    const h = e.h;
    return h("section", { class: "aoste-license", "aria-label": "主题与插件授权", "aria-busy": busy.value }, [
      h("h3", null, "主题与插件授权"),
      h("p", null, "填写一次授权码，AOSTE 主题与 Aoste主题插件同步生效。未授权时主题与商城停止对外服务。"),
      h("label", { for: "aoste-license-key" }, "授权码"),
      h("div", { class: "aoste-license-input" }, [
        h("input", { id: "aoste-license-key", type: "password", autocomplete: "off", spellcheck: false,
          maxlength: 128, value: key.value, placeholder: status.value?.valid ? "已保存授权码，填写可更换" : "请输入授权码",
          disabled: busy.value, onInput: event => { key.value = event.target.value; } }),
        h("button", { type: "button", class: "primary", disabled: busy.value, onClick: save }, busy.value ? "处理中…" : "保存并验证"),
      ]),
      h("p", { class: "aoste-license-help" }, ["如无授权码请", h("a", { href: "https://hfz.pw/shop/2678.html", target: "_blank", rel: "noopener noreferrer" }, "前往获取"), "（免费）"]),
      h("p", { role: "status", "aria-live": "polite" }, status.value?.message || "正在读取授权状态…"),
      status.value?.expiresAt ? h("p", null, "授权到期：" + new Date(status.value.expiresAt).toLocaleString("zh-CN")) : null,
      error.value ? h("p", { role: "alert", class: "aoste-license-error" }, error.value) : null,
      h("div", { class: "aoste-license-actions" }, [
        h("button", { type: "button", disabled: busy.value, onClick: load }, "重新读取状态"),
        h("button", { type: "button", disabled: busy.value || !status.value || status.value.state === "missing", onClick: clear }, "移除授权"),
      ]),
    ]);
  };
}

const AosteLicensePanel = e.defineComponent({ name: "AosteLicensePanel", setup: useAosteLicense });
