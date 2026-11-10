import { test, expect } from '@playwright/test';
import { fetchUser, removeUser } from '../src/users';

test.describe('users', () => {
  test('resolves without await', async () => {
    expect(fetchUser(1)).resolves.toEqual({ id: 1 }); // Noncompliant {{"expect(...).resolves.toEqual(...)" is neither awaited nor returned}}
  });

  test('rejects with not, no semicolon', async () => {
    const id = 2
    expect(removeUser(id)).rejects.not.toThrow() // Noncompliant
  });

  test('inside an if block', async () => {
    if (process.env.CI) {
      expect(fetchUser(3)).resolves.toBeDefined(); // Noncompliant
    }
  });

  test('web-first matcher', async ({ page }) => {
    await page.goto('/users');
    expect(page.getByText('Ann')).toBeVisible(); // Noncompliant
    expect.soft(page.locator('h1')).toHaveText('Users'); // Noncompliant
  });

  test('poll', async () => {
    expect.poll(() => fetchUser(4)).toBeTruthy(); // Noncompliant
  });

  test('chai as promised', () => {
    expect(fetchUser(5)).to.eventually.deep.equal({ id: 5 }); // Noncompliant
  });

  // compliant

  test('awaited', async ({ page }) => {
    await expect(fetchUser(1)).resolves.toEqual({ id: 1 });
    await expect(page.getByText('Ann')).toBeVisible();
    await expect.poll(() => fetchUser(4)).toBeTruthy();
  });

  test('returned', () => {
    return expect(fetchUser(1)).resolves.toEqual({ id: 1 });
  });

  test('expression body', () => expect(fetchUser(1)).resolves.toEqual({ id: 1 }));

  test('sync matchers stay sync', async ({ page }) => {
    expect(page.url()).toBe('/users');
    expect(await page.title()).toContain('Users');
    expect([1]).toHaveLength(1);
  });

  test('promise kept and awaited later', async () => {
    const check = expect(fetchUser(1)).resolves.toEqual({ id: 1 });
    await check;
  });

  test('promise all', async () => {
    await Promise.all([
      expect(fetchUser(1)).resolves.toBeDefined(),
      expect(fetchUser(2)).resolves.toBeDefined(),
    ]);
  });

  test('handed on to done', (done) => {
    expect(fetchUser(1)).resolves.toBeDefined().then(() => done());
    expect(fetchUser(2)).to.eventually.equal(2).notify(done);
  });

  test('callback returns it', async () => {
    await Promise.all([1, 2].map(id => expect(fetchUser(id)).resolves.toBeDefined()));
  });

  test('resolves without a matcher is another rule', () => {
    expect(fetchUser(1)).resolves;
  });
});
