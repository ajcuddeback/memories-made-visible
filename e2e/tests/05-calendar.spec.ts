import { expect, test } from '@playwright/test';
import { futureWeekday, goToMonth, pickPublicDate, signIn } from './helpers';

test.describe.configure({ mode: 'serial' });

test.describe('calendar: blocks and booking hours', () => {
  const day = futureWeekday(35);

  test('blocking a morning removes those start times from the booking page', async ({ page }) => {
    await signIn(page);
    await page.goto('/studio/calendar');
    await page.getByRole('button', { name: 'Block time', exact: true }).click();
    // Pick the date in the styled date field
    await page.getByRole('button', { name: 'Block from' }).click();
    await goToMonth(page, page.locator('.pk-cal-title'), page.locator('.pk-cal').getByRole('button', { name: 'Next month' }), day);
    await page.locator('.pk-cal').getByRole('button', { name: String(day.day), exact: true }).click();
    await page.getByLabel('All day').uncheck();
    await page.getByRole('button', { name: 'Block start' }).click();
    await page.getByRole('option', { name: '9:00 AM' }).click();
    await page.getByRole('button', { name: 'Block end' }).click();
    await page.getByRole('option', { name: '11:00 AM' }).click();
    await page.getByPlaceholder('Dentist, vacation, family day…').fill('Dentist');
    await page.getByRole('button', { name: 'Block it' }).click();
    await expect(page.locator('.notice')).toContainText('Time blocked');

    await page.goto('/book');
    await page.getByRole('radio', { name: 'Families & solo portraits' }).click();
    await page.getByRole('radio', { name: /^Solo/ }).click();
    await pickPublicDate(page, day);
    const times = page.getByRole('radiogroup', { name: 'Start time' });
    await expect(times.getByRole('radio', { name: '8:30 AM', exact: true })).toBeVisible();
    await expect(times.getByRole('radio', { name: '9:00 AM', exact: true })).toHaveCount(0);
    await expect(times.getByRole('radio', { name: '10:30 AM', exact: true })).toHaveCount(0);
    await expect(times.getByRole('radio', { name: '11:00 AM', exact: true })).toBeVisible();
  });

  test('the block shows on the day, and can be removed', async ({ page }) => {
    await signIn(page);
    await page.goto('/studio/calendar');
    // Navigate the month grid to the day and select it
    await goToMonth(page, page.locator('.cal-title'), page.getByRole('button', { name: 'Next month' }), day);
    await page.locator('.grid').getByRole('gridcell', { name: new RegExp(`^${day.day}(,|$)`) }).click();
    const item = page.locator('.dayi--block', { hasText: 'Dentist' });
    await expect(item).toContainText('9:00 – 11:00 AM');
    page.once('dialog', (d) => d.accept());
    await item.getByRole('button', { name: 'Remove' }).click();
    await expect(page.locator('.notice')).toContainText('Block removed');
  });

  test('closing a weekday in booking hours empties it on the booking page', async ({ page }) => {
    const target = futureWeekday(42);
    const wd = target.date.getDay();
    const names = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
    await signIn(page);
    await page.goto('/studio/calendar');
    await page.getByRole('button', { name: 'Booking hours' }).click();
    await page.locator('.hour-row', { hasText: names[wd] }).getByRole('checkbox').uncheck();
    await page.getByRole('button', { name: 'Save hours' }).click();
    await expect(page.locator('.panel-msg')).toContainText('Saved');

    await page.goto('/book');
    await page.getByRole('radio', { name: 'Families & solo portraits' }).click();
    await page.getByRole('radio', { name: /^Solo/ }).click();
    await goToMonth(page, page.locator('.cal-label'), page.getByRole('button', { name: 'Next month' }), target);
    await expect(page.locator('.cal-grid').getByRole('button', { name: new RegExp(`^${target.day}, .*unavailable`) })).toBeDisabled();

    // Reopen it
    await page.goto('/studio/calendar');
    await page.getByRole('button', { name: 'Booking hours' }).click();
    await page.locator('.hour-row', { hasText: names[wd] }).getByRole('checkbox').check();
    await page.getByRole('button', { name: 'Save hours' }).click();
    await expect(page.locator('.panel-msg')).toContainText('Saved');
  });
});
