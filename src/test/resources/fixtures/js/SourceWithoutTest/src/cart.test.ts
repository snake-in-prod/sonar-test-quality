import { total } from './cart';
import Panel from './ui';
import { fmt } from './esm.js';
import { alias } from '@/alias';
const legacy = require('./legacy');
import { area } from '@acme/shapes';
import { side } from '@acme/shapes/square';
import { upper } from 'text-utils';
import { lower } from 'text-utilsx';

test('total', () => {
  expect(total([1, 2])).toBe(3);
  expect(Panel).toBeDefined();
  expect(fmt(1) + alias + legacy.a()).toBeTruthy();
  expect(area(1) + side(4) + upper('a') + lower('B')).toBeTruthy();
});
