import { total } from './cart';
import Panel from './ui';
import { fmt } from './esm.js';
import { alias } from '@/alias';
const legacy = require('./legacy');

test('total', () => {
  expect(total([1, 2])).toBe(3);
  expect(Panel).toBeDefined();
  expect(fmt(1) + alias + legacy.a()).toBeTruthy();
});
