import { expect, test } from '@playwright/test';
import { clearMail, futureWeekday, pickPublicDate, waitForMail } from './helpers';

test.describe.configure({ mode: 'serial' });

test.describe('booking a session', () => {
  const when = futureWeekday(21);

  test('fixed package: type → package → date → time → details → request sent, with both emails', async ({ page, request }) => {
    await clearMail(request);
    await page.goto('/book');
    await page.getByRole('radio', { name: 'Families & solo portraits' }).click();
    // Package step appears; the summary shows package and starting quote
    await page.getByRole('radio', { name: /^Family/ }).click();
    await expect(page.locator('.summary')).toContainText('Family');
    await expect(page.locator('.summary')).toContainText('$225');

    // Past days are disabled; pick a weekday three weeks out
    await pickPublicDate(page, when);
    const times = page.getByRole('radiogroup', { name: 'Start time' });
    await expect(times.getByRole('radio').first()).toBeVisible();
    await expect(times.getByRole('radio', { name: '7:00 AM' })).toBeVisible();
    await expect(times.getByRole('radio', { name: '5:00 PM' })).toBeVisible();   // last 1-hour start inside 7–6
    await expect(times.getByRole('radio', { name: '5:30 PM' })).toHaveCount(0);
    await times.getByRole('radio', { name: '10:00 AM' }).click();

    await page.getByLabel('Full name').fill('Taylor Reyes');
    await page.getByLabel('Email').fill('taylor@example.com');
    await page.getByLabel('Phone').fill('(863) 555-0199');
    await page.getByLabel('Preferred location').fill('Lake Mirror, Lakeland');
    await page.getByLabel('Tell us about your session').fill('Family of four, two kids.');
    await expect(page.locator('.summary')).toContainText('10:00 AM');
    await page.getByRole('button', { name: 'Send request' }).click();

    await expect(page.getByRole('heading', { name: 'Request sent' })).toBeVisible();
    await expect(page.locator('.done')).toContainText('Taylor Reyes');
    await expect(page.locator('.done')).toContainText('10:00 AM');

    const studio = await waitForMail(request, 'New session request', 'bookings@e2e.local');
    expect(studio.text).toContain('Taylor Reyes');
    expect(studio.text).toContain('Family');
    expect(studio.html).toContain('Open in studio admin');
    const ack = await waitForMail(request, 'We received your session request', 'taylor@example.com');
    expect(ack.text).toContain('Hi Taylor,');
  });

  test('the taken slot disappears for the next visitor', async ({ page }) => {
    await page.goto('/book');
    await page.getByRole('radio', { name: 'Families & solo portraits' }).click();
    await page.getByRole('radio', { name: /^Family/ }).click();
    await pickPublicDate(page, when);
    const times = page.getByRole('radiogroup', { name: 'Start time' });
    await expect(times.getByRole('radio', { name: '11:00 AM' })).toBeVisible();
    await expect(times.getByRole('radio', { name: '10:00 AM' })).toHaveCount(0);
    await expect(times.getByRole('radio', { name: '9:30 AM' })).toHaveCount(0);   // would overlap 10–11
    await expect(times.getByRole('radio', { name: '9:00 AM' })).toBeVisible();
  });

  test('hourly package: hours and add-ons drive the quote and the slot length', async ({ page, request }) => {
    const day = futureWeekday(28);
    await page.goto('/book');
    await page.getByRole('radio', { name: 'Events' }).click();
    await page.getByRole('radio', { name: /^Event coverage/ }).click();
    await expect(page.getByLabel('How many hours?')).toBeVisible();
    await page.getByLabel('How many hours?').selectOption('3');
    await page.getByLabel('Second photographer').check();
    await expect(page.locator('.summary')).toContainText('3 hrs');
    await expect(page.locator('.summary')).toContainText('$675');   // 3×150 + 3×75
    await pickPublicDate(page, day);
    const times = page.getByRole('radiogroup', { name: 'Start time' });
    await expect(times.getByRole('radio', { name: '3:00 PM' })).toBeVisible();   // 3h ending 6pm
    await expect(times.getByRole('radio', { name: '3:30 PM' })).toHaveCount(0);
    await times.getByRole('radio', { name: '2:00 PM' }).click();
    await page.getByLabel('Full name').fill('Party Host');
    await page.getByLabel('Email').fill('host@example.com');
    await page.getByRole('button', { name: 'Send request' }).click();
    await expect(page.getByRole('heading', { name: 'Request sent' })).toBeVisible();
    const studio = await waitForMail(request, 'New session request: Events', 'bookings@e2e.local');
    expect(studio.text).toContain('Event coverage · 3 hrs + Second photographer');
    expect(studio.text).toContain('$675.00');
  });

  test('honest form validation: nothing sends until everything needed is there', async ({ page }) => {
    await page.goto('/book');
    await expect(page.getByRole('button', { name: 'Send request' })).toBeDisabled();
    await expect(page.locator('.summary .hint')).toContainText('a session type');
  });
});
