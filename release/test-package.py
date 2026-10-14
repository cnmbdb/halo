"""Check classpath resource discovery, not only file presence inside the JAR."""
import io
import json
import re
from pathlib import Path
from zipfile import ZipFile

root = Path(__file__).resolve().parent.parent
v = json.loads((root / 'release/version.json').read_text())
with ZipFile(root / f'AOSTE-{v["baseTheme"]}-with-commerce-plugin.zip') as bundle:
    original = ZipFile(io.BytesIO(bundle.read(f'plugin-commerce-{v.get("basePlugin",v["plugin"])}.jar')))
with ZipFile(root / f'dist/plugin-commerce-{v["plugin"]}.jar') as jar:
    assert jar.testzip() is None
    for entry in original.infolist():
        assert entry.filename in jar.namelist(), f'Missing classpath entry: {entry.filename}'
        if entry.filename.endswith('.class'):
            assert jar.read(entry.filename) == original.read(entry.filename)
    assert jar.getinfo('extensions/').is_dir()
    spec = jar.read('plugin.yaml').decode()
    name = re.search(r'settingName:\s*"?([^"\s]+)', spec)[1]
    settings = [jar.read(n).decode() for n in jar.namelist()
                if n.startswith('extensions/') and n.endswith(('.yaml','.yml'))]
    assert any('kind: Setting' in s and re.search(r'name:\s*'+re.escape(name)+r'\s',s)
               for s in settings), f'Setting {name} must be discoverable'
    assert f'Implementation-Version: {v["plugin"]}' in jar.read('META-INF/MANIFEST.MF').decode()
    assert f'version: "{v["plugin"]}"' in spec
    js = jar.read('console/main.js').decode()
    assert '"theme:list:tabs:create"' in js
    assert 'id:"commerce:aoste-release-updates"' in js
    assert 'label:"AOSTE 更新",component:e.markRaw(sl),priority:0' in js
    assert 'f.value)==null?void 0:d.metadata.name)==="theme-aoset"' in js
print('PASS: classpath directory discovery, matching Setting, versions, unchanged backend classes and theme update tab')
