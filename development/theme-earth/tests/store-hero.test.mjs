import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import test from 'node:test';
import assert from 'node:assert/strict';
const template = readFileSync(new URL('../src/shop.html', import.meta.url), 'utf8');
const source = template.match(/x-data="(\{ active: 0[^\"]+)"/)[1].replaceAll('&amp;', '&');
function carousel(count, seconds) {
  let tick, cleared;
  const controller = runInNewContext(`(${source})`, {
    setInterval(fn, delay) { tick = fn; assert.equal(delay, seconds * 1000); return 12; },
    clearInterval(id) { cleared = id; }, document: { activeElement: {} },
  });
  controller.$el = { dataset: { interval: seconds }, querySelectorAll: () => Array(count), matches: () => false, contains: () => false };
  controller.init();
  return { controller, tick: () => tick?.(), scheduled: () => !!tick, cleared: () => cleared };
}
test('cycles slides, wraps, pauses during hover and focus, and clears timer', () => {
  const c = carousel(2, 5); c.tick(); assert.equal(c.controller.active, 1);
  c.tick(); assert.equal(c.controller.active, 0);
  c.controller.$el.matches = () => true; c.tick(); assert.equal(c.controller.active, 0);
  c.controller.$el.matches = () => false; c.controller.$el.contains = () => true;
  c.tick(); assert.equal(c.controller.active, 0);
  c.controller.destroy(); assert.equal(c.cleared(), 12);
});
test('zero interval and single slide do not start automatic switching', () => {
  assert.equal(carousel(2, 0).scheduled(), false);
  assert.equal(carousel(1, 5).scheduled(), false);
});
