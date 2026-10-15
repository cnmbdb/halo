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

首页移动端布局优化：
- 手机搜索改为图标按钮，减少导航挤占。
- 自定义卡片默认双列、原图比例，避免固定高度裁切和长屏堆叠。
- 后台：首页布局 → 自定义卡片区块，新增手机端列数、媒体比例、卡片间距。
- 手机端支持单列/双列、原图/16:10/1:1/沿用卡片高度、8/12/18px 间距；桌面列数和尺寸独立保留。
- 收紧首页区块、分类、文章卡片间距，调整移动端圆角与文字尺寸。
- 幻灯片紧凑/舒展高度适配手机，保留全高度和自适应模式。
- 已在 Docker 沙盒实际验证后台切换保存，测试 320/390/430/768/1280px，无横向溢出。
- 插件保持 0.2.8，本次无需升级插件。

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
