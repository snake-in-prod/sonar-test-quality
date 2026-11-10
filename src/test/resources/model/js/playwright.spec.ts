import { test, expect } from '@playwright/test';

test.describe('login', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
  });

  test('signs in', async ({ page }) => {
    await page.getByLabel('User').fill('ann');
    await expect(page).toHaveURL(/\/home$/);
    await expect.soft(page.getByText('Welcome')).toBeVisible();
  });

  test('tagged', { tag: '@smoke' }, async ({ page }) => {
    await expect(page.locator('h1')).toHaveText('Hi');
  });

  test.skip('not ready', async ({ page }) => {
    await page.waitForTimeout(1000);
  });

  test.describe.serial('flow', () => {
    test.fixme('broken step', async () => {});
  });
});

test('outside', async ({ page }) => {
  test.skip(process.env.CI === 'true', 'local only');
  await expect(page.getByRole('heading')).toBeVisible();
});
