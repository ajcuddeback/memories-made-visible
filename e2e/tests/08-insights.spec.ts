import { expect, test } from '@playwright/test';
import { signIn } from './helpers';

test.describe('insights', () => {
  test('public browsing shows up as visitors, pages and a booking funnel', async ({ page, context }) => {
    // A visitor browses: home → pricing → booking page, picks a type (funnel step), then leaves.
    const visitor = await context.newPage();
    await visitor.goto('/');
    await visitor.getByRole('link', { name: 'Pricing' }).first().click();
    await visitor.waitForURL(/\/pricing/);
    await visitor.waitForTimeout(3000); // let the tracker's batch timer send the page view before a full navigation
    await visitor.goto('/book');
    await visitor.getByRole('radio', { name: 'Families & solo portraits' }).click();
    await visitor.waitForTimeout(3500); // the tracker batches every 2.5s
    await visitor.close();

    await signIn(page);
    await page.goto('/studio/insights');
    await expect(page.locator('.stat', { hasText: 'Unique visitors' })).not.toContainText(/^0/);
    const visitors = Number((await page.locator('.stat', { hasText: 'Unique visitors' }).locator('.stat-value').textContent())?.trim());
    expect(visitors).toBeGreaterThanOrEqual(1);
    await expect(page.locator('.row', { hasText: 'Pricing' })).toBeVisible();
    const first = page.locator('.fstep').first();
    await expect(first).toContainText('Opened the booking page');
    const opened = Number((await first.locator('.fstep-n').textContent())?.trim().split(' ')[0]);
    expect(opened).toBeGreaterThanOrEqual(1);
    await page.getByRole('button', { name: '7 days' }).click();
    await expect(page.locator('.panel-sub').first()).toContainText('per day');
  });

  test('employees cannot open insights', async ({ page }) => {
    const res = await page.request.get('http://localhost:8080/api/admin/analytics');
    expect(res.status()).toBe(403);
  });
});
