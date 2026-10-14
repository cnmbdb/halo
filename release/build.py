"""Rebuild the published theme from its base package and reviewed source overlay."""
import hashlib
import io
import json
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
from patch_plugin import patch_plugin

ROOT = Path(__file__).resolve().parent.parent
version = json.loads((ROOT / "release/version.json").read_text())
theme, base, plugin = (version[key] for key in ("theme", "baseTheme", "plugin"))
out = ROOT / "dist"
out.mkdir(exist_ok=True)
with ZipFile(ROOT / f"AOSTE-{base}-with-commerce-plugin.zip") as bundle:
    files = {}
    with ZipFile(io.BytesIO(bundle.read(f"theme-aoset-{base}.zip"))) as original:
        files = {name: original.read(name) for name in original.namelist() if not name.endswith("/")}
    base_plugin = version.get("basePlugin", plugin)
    plugin_data = bundle.read(f"plugin-commerce-{base_plugin}.jar")
    if plugin != base_plugin:
        plugin_data = patch_plugin(plugin_data, base_plugin, plugin)
    readme = bundle.read("README.md").decode().replace(base, theme).replace(base_plugin, plugin)
for path in (ROOT / "release/theme-overlay").rglob("*"):
    if path.is_file():
        files[path.relative_to(ROOT / "release/theme-overlay").as_posix()] = path.read_bytes()
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

独立更新修复（需要升级商城插件）：
- 手动点击“检查更新”绕过内存、浏览器及 CDN 的旧清单缓存。
- 保留并发请求合并、自动检查的一分钟缓存，以及版本、来源、文件大小和 SHA-256 校验。
- 移除更新页顶部 GITHUB RELEASES 英文标记。
- 版本卡片改为无边框、半透明毛玻璃背景，适配窄屏。

首页布局 → 幻灯片高度新增：
- 全高度：占满视口扣除顶部导航与间距后的剩余高度，手机端同步适配。
- 自适应：按当前图片原始比例完整展示，不裁切；切换图片时高度随当前图片调整。

保留紧凑、舒展和已有配置，默认仍为舒展。
已检查桌面 1280×720 与手机 390×844 的尺寸、原有模式兼容性及图片比例。

建议先手动升级插件至 {plugin}，刷新后台后再检查并升级主题。
旧版检查不到新版本时，可从本发布页下载插件 JAR，通过 Halo 自带的插件升级上传一次。
后台手动升级：主题 → 独立更新 → 检查更新 → 升级主题。
也可下载 theme-aoset-{theme}.zip，通过 Halo 自带的主题升级上传安装。
升级前可导出主题配置；主题文件会被新版覆盖。

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
