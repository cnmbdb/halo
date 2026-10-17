"""Rebuild the published theme from its base package and reviewed source overlay."""
import hashlib
import io
import json
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
from patch_plugin import patch_plugin
from build_license import build_license
from theme_license import guard_theme

ROOT = Path(__file__).resolve().parent.parent
version = json.loads((ROOT / "release/version.json").read_text())
theme, base, plugin = (version[key] for key in ("theme", "baseTheme", "plugin"))
out = ROOT / "dist"
out.mkdir(exist_ok=True)
build_license(ROOT)
with ZipFile(ROOT / f"AOSTE-{base}-with-commerce-plugin.zip") as bundle:
    files = {}
    with ZipFile(io.BytesIO(bundle.read(f"theme-aoset-{base}.zip"))) as original:
        files = {name: original.read(name) for name in original.namelist() if not name.endswith("/")}
    base_plugin = version.get("basePlugin", plugin)
    plugin_data = bundle.read(f"plugin-commerce-{base_plugin}.jar")
    if plugin != base_plugin:
        plugin_data = patch_plugin(plugin_data, base_plugin, plugin)
    readme = (ROOT / "README.md").read_text().split("<!-- aoste-release-source", 1)[0].rstrip() + "\n"
for path in (ROOT / "release/theme-overlay").rglob("*"):
    if path.is_file():
        files[path.relative_to(ROOT / "release/theme-overlay").as_posix()] = path.read_bytes()
guard_theme(files)
assert f"version: {theme}" in files["theme.yaml"].decode()
assert all(f"value: {value}" in files["settings.yaml"].decode() for value in ("compact", "comfortable", "full", "auto"))
assert b'slides-height.css' in files["templates/modules/home/slides.html"]
theme_file = out / f"theme-aoset-{theme}.zip"
with ZipFile(theme_file, "w", ZIP_DEFLATED) as archive:
    for name, content in sorted(files.items()):
        archive.writestr(name, content)
plugin_file = out / f"plugin-commerce-{plugin}.jar"
plugin_file.write_bytes(plugin_data)
manifest = {"schemaVersion": 1}
for kind, name, number, path in (("theme", "theme-aoset", theme, theme_file), ("plugin", "commerce", plugin, plugin_file)):
    manifest[kind] = {"id": name, "version": number, "asset": path.name, "requires": ">=2.26.0", "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
(out / "updates.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
notes = f"""AOSTE 主题 {theme} · 商城插件 {plugin}

主题与插件共用 ETS 授权入口：
- 后台主题更新页与插件设置共用一份授权码，保存并验证后同时生效。
- 如无授权码请前往获取（免费）：https://hfz.pw/shop/2678.html
- 未授权时由服务端禁止主题页面、商品浏览与新下单；后台授权/更新入口、已有订单查询、交付与支付回调保留。
- 授权码保存在 Halo Secret 中；授权失效、撤销或到期后停止对外服务。服务端最多缓存一分钟；授权服务故障时校验失败。
- 主题依赖 Aoste主题插件 >=0.2.9，插件停用或缺失时显示授权提示页。

部署前先发布 ETS-admin 的 /api/authorization/aoste，再升级插件与主题并填写授权码。
先在测试站验证授权，再应用生产站点；未授权的站点升级后将停止主题与商城服务。
升级前请备份站点和主题自定义文件。

```aoste-update
{json.dumps(manifest, ensure_ascii=False, indent=2)}
```
"""
(out / "notes.md").write_text(notes)
with ZipFile(out / f"AOSTE-{theme}-with-commerce-plugin.zip", "w", ZIP_DEFLATED) as archive:
    for path in (theme_file, plugin_file):
        archive.write(path, path.name)
    archive.writestr("README.md", readme)
print(json.dumps(manifest, ensure_ascii=False, indent=2))
