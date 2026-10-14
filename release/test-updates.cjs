// Exercise the actual shipped updater functions, including cache and asset validation.
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const source = fs.readFileSync(process.argv[2], 'utf8');
const readme = fs.readFileSync('README.md', 'utf8');
const published = JSON.parse(readme.match(/<!-- aoste-release-source\n([\s\S]*?)\n-->/)[1]);
const calls = [];
let now = 1800000000000;
let current = published;
const context = vm.createContext({
  Date: {now: () => now}, AbortSignal,
  fetch: async (url, options) => {
    calls.push({url, options});
    assert.equal(options.cache, 'no-store');
    assert.equal(options.credentials, 'omit');
    assert.match(url, /README\.md\?check=\d+$/);
    return {ok:true, text:async()=>`<!-- aoste-release-source\n${JSON.stringify(current)}\n-->`};
  },
});
vm.runInContext('const Y="https://github.com/cnmbdb/halo/releases"; const z="```aoste-update\\n";'+source.slice(source.indexOf('function U('),source.indexOf('async function zt(')),context);
(async()=>{
  const first = await vm.runInContext('W()',context);
  const count = calls.length;
  assert.equal((await vm.runInContext('W()',context)).tag, first.tag);
  assert.equal(calls.length,count,'automatic checks reuse short-lived cache');
  await vm.runInContext('W(true)',context);
  assert.equal(calls.length,count+1,'manual checks bypass cached result');
  now+=10;
  await vm.runInContext('W(true)',context);
  assert.notEqual(calls.at(-1).url,calls.at(-2).url,'manual checks use fresh CDN URL');
  now+=61000;
  const before=calls.length;
  await Promise.all([vm.runInContext('W()',context),vm.runInContext('W()',context)]);
  assert.equal(calls.length,before+1,'concurrent checks are coalesced');
  assert.equal(vm.runInContext('U("1.19.11","1.19.9")',context),1);
  current={...published,assets:published.assets.map(a=>({...a,digest:'sha256:'+'0'.repeat(64)}))};
  await assert.rejects(vm.runInContext('W(true)',context),/校验值不匹配/);
  assert(!source.includes('GITHUB RELEASES'));
  assert(source.includes('d.value=await W(Boolean(event))'));
  console.log('PASS: manual refresh, CDN busting, automatic cache, concurrency, numeric versions and checksum protection');
})().catch(error=>{console.error(error);process.exitCode=1});
