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

首页布局、文章卡片与阅读排行榜：
- 首页第一区块改为左侧一张大图、右侧上下两张图片，后台配置同步对应布局。
- 首页文章支持四列网格，封面铺满卡片顶部；常规字重标题最多两行并预留高度。
- 移除文章卡片分类标签和发布日期，以及对应的后台开关。
- 新增独立阅读排行榜设置，默认展示 10 篇，支持阅读量/最新排序、TOP1 大图及移动端设置。
- 分类按钮按独立图片、共用图片、默认图案依次回退，未单独配置图片的按钮会继承共用图片。
- 首页上一页、页码和下一页控件使用胶囊边框。
- 商城插件维持 {plugin}，保留现有统一授权与商城卡片功能。

升级前请备份站点和主题自定义文件。可下载 theme-aoset-{theme}.zip，
通过 Halo 主题升级上传安装，或使用后台独立更新入口。
每页文章数量由 Halo「设置 → 文章设置」管理；需要四列六行时将相应列表页显示条数设为 24。
站点内容、分类图片选择和每页 24 篇的站点配置不包含在主题包内。

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
