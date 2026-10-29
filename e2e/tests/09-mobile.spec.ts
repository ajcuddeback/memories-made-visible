import { devices, expect, test } from '@playwright/test';
import { signIn } from './helpers';

const iphone = devices['iPhone 13'];
test.use({ viewport: iphone.viewport, userAgent: iphone.userAgent, deviceScaleFactor: iphone.deviceScaleFactor, isMobile: true, hasTouch: true });

async function noHorizontalScroll(page: import('@playwright/test').Page) {
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow, 'horizontal overflow px').toBeLessThanOrEqual(1);
}

test.describe('phone layout', () => {
  test('public pages fit the screen', async ({ page }) => {
    for (const path of ['/', '/pricing', '/book', '/portfolio', '/events', '/about']) {
      await page.goto(path);
      await page.waitForLoadState('networkidle');
      await noHorizontalScroll(page);
    }
    await page.goto('/book');
    await page.getByRole('radio', { name: 'Families & solo portraits' }).click();
    await page.getByRole('radio', { name: /^Family/ }).click();
    await noHorizontalScroll(page);
  });

  test('studio pages fit the screen and the top nav works', async ({ page }) => {
    await signIn(page);
    for (const path of ['/studio', '/studio/sessions', '/studio/calendar', '/studio/popups', '/studio/pricing', '/studio/team', '/studio/insights']) {
      await page.goto(path);
      await page.waitForLoadState('networkidle');
      await noHorizontalScroll(page);
    }
    await page.goto('/studio/calendar');
    await expect(page.locator('.dots').first()).toBeAttached(); // phone grid uses dots, not chips
    await page.getByRole('button', { name: 'Account menu' }).click();
    await expect(page.getByRole('button', { name: 'Log out' })).toBeVisible();
  });
});
