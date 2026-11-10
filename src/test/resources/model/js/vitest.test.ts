import { describe, it, expect, vi, test, assert } from 'vitest';
import type { User } from './user';

const parse = <T>(raw: string): T => JSON.parse(raw) as T;

describe('parser', () => {
  const fetcher = vi.fn<(id: number) => Promise<User>>();

  it.concurrent('parses json', async () => {
    const u = parse<User>('{"name":"a"}');
    expect(u.name).toBe('a');
    if (u.age < 3 && u.age > 1) {
      expect.fail('bad');
    }
  });

  test('mocks module', async (): Promise<void> => {
    vi.mock('./api', () => ({ load: vi.fn() }));
    const spy = vi.spyOn(Math, 'random').mockReturnValue(0.5);
    expect(spy).toHaveBeenCalledTimes(0);
    expect.assertions(1);
  });

  test.each([{ a: 1 }, { a: 2 }])('each object $a', ({ a }) => {
    expect(a).toBeGreaterThan(0);
  });

  describe('inner', () => {
    it.only('only one', () => {
      assert.equal(1, 1);
      assert(true);
    });
  });
});
