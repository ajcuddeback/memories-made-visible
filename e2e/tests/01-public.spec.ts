import { expect, test } from '@playwright/test';

test.describe('public site', () => {
  test('home, nav and footer contact', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveTitle(/Memories Made Visible/);
    for (const link of ['Portfolio', 'Events', 'Pricing', 'About']) {
      await expect(page.getByRole('link', { name: link }).first()).toBeVisible();
    }
    await expect(page.locator('footer')).toContainText('hello@e2e.local');
    await expect(page.locator('footer')).toContainText('(863) 555-0100');
  });

  test('pricing page lists categories, packages and what’s included', async ({ page }) => {
    await page.goto('/pricing');
    await expect(page.getByRole('heading', { name: 'Families & solo portraits' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Weddings' })).toBeVisible();
    const families = page.locator('.plan', { hasText: 'Families & solo portraits' });
    await expect(families).toContainText('Family');
    await expect(families).toContainText('$225');
    const more = families.getByRole('button', { name: 'See what’s included' }).first();
    await more.click();
    await expect(families.locator('.tier-includes').first()).toContainText('Private online gallery');
    // Events is hourly with an add-on
    const events = page.locator('.plan', { hasText: 'Events' });
    await expect(events).toContainText('$150/hr');
    await expect(events).toContainText('2-hr min');
    await expect(events).toContainText('Second photographer');
  });

  test('booking page offers direct contact for people who’d rather not use the form', async ({ page }) => {
    await page.goto('/book');
    await expect(page.getByRole('heading', { name: 'Prefer to talk first?' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'hello@e2e.local' }).first()).toHaveAttribute('href', 'mailto:hello@e2e.local');
  });

  test('about and events pages render', async ({ page }) => {
    await page.goto('/about');
    await expect(page.getByRole('heading', { name: /Winter Haven/ })).toBeVisible();
    await page.goto('/events');
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  });
});
