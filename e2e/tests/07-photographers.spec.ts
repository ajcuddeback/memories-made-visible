import { expect, test } from '@playwright/test';
import { ADMIN, OWNER, api, futureWeekday, pickPublicDate, signIn } from './helpers';

test.describe.configure({ mode: 'serial' });

test.describe('choosing a photographer', () => {
  const day = futureWeekday(49);

  test('with nobody bookable there is no photographer step', async ({ page }) => {
    await page.goto('/book');
    await page.getByRole('radio', { name: 'Families & solo portraits' }).click();
    await expect(page.getByRole('radiogroup', { name: 'Photographer' })).toHaveCount(0);
  });

  test('marking people bookable adds the step; a personal block only affects that person', async ({ page }) => {
    await signIn(page, OWNER);
    await page.goto('/studio/team');
    await page.locator('.member', { hasText: 'Erica' }).getByLabel('Bookable by customers').check();
    await expect(page.locator('.notice')).toContainText('Erica is now offered');
    await page.locator('.member', { hasText: 'Austin' }).getByLabel('Bookable by customers').check();
    // Erica blocks her own morning
    await api(page.request, 'post', '/api/admin/availability/blocks', { startDate: day.iso, startTime: '09:00', endTime: '12:00', note: 'Erica dentist', staffName: 'Erica' }, ADMIN);

    await page.goto('/book');
    await page.getByRole('radio', { name: 'Families & solo portraits' }).click();
    await page.getByRole('radio', { name: /^Solo/ }).click();
    const who = page.getByRole('radiogroup', { name: 'Photographer' });
    await expect(who.getByRole('radio', { name: /No preference/ })).toBeVisible();
    await expect(who.getByRole('radio', { name: /Erica/ })).toContainText('Lead photographer');
    await who.getByRole('radio', { name: /Erica/ }).click();
    await pickPublicDate(page, day);
    const times = page.getByRole('radiogroup', { name: 'Start time' });
    await expect(times.getByRole('radio', { name: '8:30 AM' })).toBeVisible();
    await expect(times.getByRole('radio', { name: '9:00 AM' })).toHaveCount(0);
    await who.getByRole('radio', { name: /Austin/ }).click();
    await expect(times.getByRole('radio', { name: '9:00 AM' })).toBeVisible();
    await who.getByRole('radio', { name: /No preference/ }).click();
    await expect(times.getByRole('radio', { name: '9:00 AM' })).toBeVisible();
    await expect(page.locator('.summary')).toContainText('No preference');

    // Book Austin at 9:00; the request is assigned to him
    await who.getByRole('radio', { name: /Austin/ }).click();
    await times.getByRole('radio', { name: '9:00 AM' }).click();
    await page.getByLabel('Full name').fill('Pat Lee');
    await page.getByLabel('Email').fill('pat@example.com');
    await page.getByRole('button', { name: 'Send request' }).click();
    await expect(page.getByRole('heading', { name: 'Request sent' })).toBeVisible();
    const list = await (await api(page.request, 'get', '/api/admin/bookings')).json();
    const pat = list.find((b: { name: string }) => b.name === 'Pat Lee');
    expect(pat.assignees).toEqual(['Austin']);
  });

  test('handing the session to someone who is busy is refused; the studio books as one again when unflagged', async ({ page }) => {
    await signIn(page, OWNER);
    const list = await (await api(page.request, 'get', '/api/admin/bookings')).json();
    const pat = list.find((b: { name: string }) => b.name === 'Pat Lee');
    // Erica is blocked 9–12 that day → can't take Pat's 9:00
    const res = await api(page.request, 'put', `/api/admin/bookings/${pat.id}/assignees`, { names: ['Erica'] });
    expect(res.status()).toBe(409);
    expect((await res.json()).message).toContain("Erica isn't free then");

    await page.goto('/studio/team');
    await page.locator('.member', { hasText: 'Erica' }).getByLabel('Bookable by customers').uncheck();
    await page.locator('.member', { hasText: 'Austin' }).getByLabel('Bookable by customers').uncheck();
    await page.goto('/book');
    await page.getByRole('radio', { name: 'Families & solo portraits' }).click();
    await expect(page.getByRole('radiogroup', { name: 'Photographer' })).toHaveCount(0);
  });
});
