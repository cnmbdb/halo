"""Apply the update UI fix to the published plugin; preserve all backend classes."""
import io
from zipfile import ZipFile, ZIP_DEFLATED


def replace_once(source, old, new):
    assert source.count(old) == 1, f"Unexpected upstream code: {old[:80]}"
    return source.replace(old, new, 1)


def patch_plugin(data, old_version, new_version):
    with ZipFile(io.BytesIO(data)) as original:
        entries = original.infolist()
        files = {n: original.read(n) for n in original.namelist() if not n.endswith('/')}
    js = files['console/main.js'].decode()
    js = replace_once(js, 'credentials:"omit",headers:{Accept:f}',
                      'credentials:"omit",cache:"no-store",headers:{Accept:f}')
    js = replace_once(js, 'async function W(){if(L&&Date.now()-L.at<6e4)',
                      'async function W(force=false){if(!force&&L&&Date.now()-L.at<6e4)')
    js = replace_once(js, 'Ft(Ht,"text/plain")',
                      'Ft(Ht+"?check="+(force?Date.now():Math.floor(Date.now()/6e4)),"text/plain")')
    js = replace_once(js, 'async function $(){if(!(p.value||l.value))',
                      'async function $(event){if(!(p.value||l.value))')
    js = replace_once(js, 'd.value=await W(),s.value=new Date()',
                      'd.value=await W(Boolean(event)),s.value=new Date()')
    js = replace_once(js, 'e.createElementVNode("span",{class:"eyebrow"},"GITHUB RELEASES"),', '')
    files['console/main.js'] = js.encode()
    css = files['console/style.css'].decode()
    css = replace_once(css,
        '.update-card[data-v-74d8824b]{border:1px solid #e3e8f0;border-radius:12px;padding:22px;background:#fff}',
        '.update-card[data-v-74d8824b]{border:0;border-radius:16px;padding:22px;background:rgba(235,241,250,.62);-webkit-backdrop-filter:blur(18px) saturate(140%);backdrop-filter:blur(18px) saturate(140%);box-shadow:0 8px 28px rgba(30,50,80,.045)}')
    css += '\n.dark .update-card[data-v-74d8824b]{background:rgba(30,41,59,.68);color:#e2e8f0}\n'
    files['console/style.css'] = css.encode()
    files['plugin.yaml'] = replace_once(files['plugin.yaml'].decode(),
                                       f'version: "{old_version}"', f'version: "{new_version}"').encode()
    files['META-INF/MANIFEST.MF'] = replace_once(files['META-INF/MANIFEST.MF'].decode(),
        f'Implementation-Version: {old_version}', f'Implementation-Version: {new_version}').encode()
    output = io.BytesIO()
    with ZipFile(output, 'w', ZIP_DEFLATED) as archive:
        # Spring resolves classpath:extensions/*.yaml by locating extensions/
        # first. Omitting directory entries makes existing YAML undiscoverable.
        for entry in entries:
            archive.writestr(entry, b'' if entry.is_dir() else files[entry.filename])
    return output.getvalue()
