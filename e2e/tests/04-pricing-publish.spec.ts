import { expect, test } from '@playwright/test';
import { signIn } from './helpers';

test.describe.configure({ mode: 'serial' });

test.describe('pricing: save is a draft, publish makes it live', () => {
  test('change a package length, save, publish, and the booking page follows', async ({ page }) => {
    await signIn(page);
    await page.goto('/studio/pricing');
    const families = page.locator('.plan', { hasText: 'Families & solo portraits' });
    await expect(families).toContainText('Family');
    await families.getByRole('button', { name: 'Edit' }).click();
    const familyRow = families.locator('.tier', { hasText: 'Family' }).filter({ hasNot: page.locator('.tier', { hasText: 'Solo' }) }).first();
    await familyRow.getByRole('button', { name: 'Edit' }).click();
    await familyRow.getByLabel('Session length').selectOption('90');
    await familyRow.getByRole('textbox', { name: /^Price/ }).fill('$245');
    await page.getByRole('button', { name: 'Save changes' }).click();
    await expect(page.locator('.msg')).toContainText('Saved to the draft');

    // Not live yet
    await expect(page.locator('.publish-bar')).toContainText('Unpublished changes: pricing');
    const pub = await page.request.get('http://localhost:8080/api/pricing');
    const live = (await pub.json()).find((t: { key: string }) => t.key === 'families').tiers.find((x: { name: string }) => x.name === 'Family');
    expect(live.price).toBe('$225');
    expect(live.durationMinutes).toBe(60);

    await page.getByRole('button', { name: 'Publish' }).click();
    await expect(page.locator('.publish-bar')).toContainText(/Everything is live|up to date|matches the live site/);

    const pub2 = await page.request.get('http://localhost:8080/api/pricing');
    const live2 = (await pub2.json()).find((t: { key: string }) => t.key === 'families').tiers.find((x: { name: string }) => x.name === 'Family');
    expect(live2.price).toBe('$245');
    expect(live2.durationMinutes).toBe(90);

    await page.goto('/book');
    await page.getByRole('radio', { name: 'Families & solo portraits' }).click();
    await expect(page.getByRole('radio', { name: /^Family/ })).toContainText('1.5 hrs');
    await expect(page.getByRole('radio', { name: /^Family/ })).toContainText('$245');
  });

  test('discard throws the draft away', async ({ page }) => {
    await signIn(page);
    await page.goto('/studio/pricing');
    const families = page.locator('.plan', { hasText: 'Families & solo portraits' });
    await families.getByRole('button', { name: 'Edit' }).click();
    await families.getByLabel('From').fill('$999');
    await page.getByRole('button', { name: 'Save changes' }).click();
    await expect(page.locator('.publish-bar')).toContainText('Unpublished changes: pricing');
    page.once('dialog', (d) => d.accept());
    await page.getByRole('button', { name: 'Discard' }).click();
    await expect(page.locator('.publish-bar')).toContainText(/Everything is live|up to date|matches the live site/);
    await expect(page.locator('.plan', { hasText: 'Families & solo portraits' })).toContainText('from $150');
  });
});
