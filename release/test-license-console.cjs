const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const source = fs.readFileSync(process.argv[2], 'utf8');
const start = source.indexOf('function useAosteLicense()');
const end = source.indexOf('const q=T(', start);
assert(start > 0 && end > start, 'shared license module is in the shipped console bundle');
const callbacks = [];
const calls = [];
let saved = false;
let invalid = false;
const csrfToken = 'csrf-test-token';
const context = vm.createContext({ Date, window: { confirm: () => true }, e: {
  ref: value => ({value}), onMounted: fn => callbacks.push(fn), onUnmounted() {},
  defineComponent: value => value,
  h: (tag, props, children) => ({tag, props: props || {}, children}),
}, D: { axiosInstance: {
  async get(url) { assert.equal(url, '/shop/api/admin/license'); return {data: {valid:saved, state:saved?'active':'missing', message:saved?'已授权':'尚未授权', csrfToken}}; },
  async put(url, body, config) { calls.push({url, body, config}); if(invalid) throw {response:{data:{message:'授权码无效'}}}; saved=true;return this.get(url); },
  async delete(url, config) { calls.push({url, config}); saved=false;return this.get(url); },
} } });
vm.runInContext(source.slice(start, end) + '\nthis.render=useAosteLicense();', context);
function nodes(node) { return node && typeof node === 'object' ? [node,...(Array.isArray(node.children)?node.children.flatMap(nodes):[])] : []; }
const find = predicate => nodes(context.render()).find(predicate);
(async()=>{
  await callbacks[0]();
  let input=find(n=>n.tag==='input'); assert.equal(nodes(context.render()).filter(n=>n.tag==='input').length,1); assert.equal(input.props.type,'password'); assert.equal(input.props.autocomplete,'off');
  const link=find(n=>n.tag==='a'); assert.equal(link.props.href,'https://hfz.pw/shop/2678.html');assert.equal(link.props.target,'_blank');assert.equal(link.props.rel,'noopener noreferrer');
  const save=()=>find(n=>n.tag==='button'&&n.children==='保存并验证').props.onClick();
  await save();assert.equal(calls.length,0);assert.match(find(n=>n.props.role==='alert').children,/请填写/);
  const key='ets_ak_'+'A'.repeat(43); input.props.onInput({target:{value:'  '+key+'  '}});
  await save(); assert.equal(calls[0].body.key,key); assert.equal(calls[0].config.headers['X-XSRF-TOKEN'],csrfToken); assert.equal(find(n=>n.tag==='input').props.value,''); assert.equal(find(n=>n.props.role==='status').children,'已授权');
  invalid=true;find(n=>n.tag==='input').props.onInput({target:{value:'invalid'}});await save();assert.equal(find(n=>n.props.role==='status').children,'已授权');assert.equal(find(n=>n.props.role==='alert').children,'授权码无效');
  await find(n=>n.tag==='button'&&n.children==='移除授权').props.onClick(); assert.equal(find(n=>n.props.role==='status').children,'尚未授权'); assert.equal(calls.at(-1).config.headers['X-XSRF-TOKEN'],csrfToken);
  assert(!source.slice(start,end).includes('localStorage'));
  console.log('PASS: shared shipped license form, masked key, required field, normalized save, no browser persistence, rejected replacement and removal');
})().catch(e=>{console.error(e);process.exitCode=1});
