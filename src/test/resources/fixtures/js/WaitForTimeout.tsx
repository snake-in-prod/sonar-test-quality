import { test, expect } from '@playwright/test';
import { setTimeout } from 'timers/promises';

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

function pause(ms) {
  return new Promise(r => setTimeout(r, ms));
}

test.describe('checkout', () => {
  test('pays', async ({ page }) => {
    await page.click('#pay');
    await page.waitForTimeout(3000); // Noncompliant {{"page.waitForTimeout" waits for a fixed time}}
    await expect(page.getByText('Paid')).toBeVisible();
  });

  test('pays through the page object', async () => {
    await this.page.waitForTimeout(delayMs); // Noncompliant
  });

  test('waits on a promise', async () => {
    await new Promise(r => setTimeout(r, 500)); // Noncompliant
    await new Promise<void>((resolve) => {
      setTimeout(resolve, 1_000); // Noncompliant
    });
    await setTimeout(250); // Noncompliant
    await sleep(2000); // Noncompliant
    await pause(100); // Noncompliant
  });

  test('cypress', () => {
    cy.get('button').click();
    cy.wait(2000); // Noncompliant {{cy.wait}}
  });

  test('waits for what it needs', async ({ page }) => {
    await page.waitForSelector('#done');
    await page.waitForLoadState('networkidle');
    await page.waitForResponse('**/api/pay');
    cy.wait('@getUser');
    cy.wait(['@a', '@b']);
  });

  test('flushes promises', async () => {
    await new Promise(r => setTimeout(r, 0));
    await new Promise(setImmediate);
    await new Promise(r => setTimeout(r, delay));
    await sleep(ms);
    await setTimeout(0);
  });

  test('schedules without waiting', async () => {
    setTimeout(() => emit('tick'), 1000);
    const timer = new Promise(r => setTimeout(r, 1000));
    await waitFor(() => expect(spy).toHaveBeenCalled(), { timeout: 3000 });
    await delayHelper(1000);
  });

  test('uses fake timers', async () => {
    jest.useFakeTimers();
    await new Promise(r => setTimeout(r, 1000));
    await sleep(1000);
  });
});
