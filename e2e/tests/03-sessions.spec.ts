import { expect, test } from '@playwright/test';
import { clearMail, signIn, waitForMail } from './helpers';

test.describe.configure({ mode: 'serial' });

test.describe('studio: a session from request to delivered', () => {
  test('owner approves Taylor with the reviewed email', async ({ page, request }) => {
    await clearMail(request);
    await signIn(page);
    await expect(page.getByRole('link', { name: /Sessions/ }).first()).toContainText('2'); // pending badge
    await page.getByRole('link', { name: /Sessions/ }).first().click();
    await expect(page.getByRole('tab', { name: /Requests \(2\)/ })).toBeVisible();

    const card = page.locator('.req', { hasText: 'Taylor Reyes' });
    await expect(card).toContainText('Families & solo portraits · Family');
    await expect(card).toContainText('10:00 – 11:00 AM');
    await card.getByLabel('Quote ($)').fill('250');
    await card.getByLabel('Quote ($)').blur();
    await card.getByRole('button', { name: 'Approve…' }).click();
    await expect(card.getByRole('heading', { name: 'Approve & email confirmation' })).toBeVisible();
    await expect(card.getByLabel('Subject')).toHaveValue(/confirmed/);
    await expect(card.getByLabel('Message')).toHaveValue(/Hi Taylor,/);
    await card.getByLabel('Message').fill('Hi Taylor,\n\nYou’re booked! See you at the lake.\n\nErica & Austin');
    await card.getByRole('button', { name: 'Approve & send' }).click();

    await expect(page.locator('.notice')).toContainText('Approved Taylor Reyes');
    const mail = await waitForMail(request, 'confirmed', 'taylor@example.com');
    expect(mail.text).toContain('See you at the lake');
    expect(mail.text).toContain('$250.00');           // session price row on approval
    expect(mail.text).toContain('Photographer');      // …and who's coming (the default lead)
    expect(mail.text).toContain('Erica');
    await expect(page.getByRole('tab', { name: /Booked \(1\)/ })).toBeVisible();
  });

  test('declining Party Host sends the decline email and frees the slot', async ({ page, request }) => {
    await signIn(page);
    await page.goto('/studio/sessions');
    const card = page.locator('.req', { hasText: 'Party Host' });
    await expect(card).toContainText('Needs a second photographer'); // the add-on brings one
    await card.getByRole('button', { name: 'Decline…' }).click();
    await card.getByRole('button', { name: 'Decline & send' }).click();
    await waitForMail(request, 'About your Events session request', 'host@example.com');
    await page.getByRole('tab', { name: /Declined \(1\)/ }).click();
    await expect(page.locator('.req', { hasText: 'Party Host' })).toBeVisible();
    // A declined session can come back
    await page.getByRole('button', { name: 'Move back to Requests' }).click();
    await expect(page.locator('.notice')).toContainText('back in Requests');
    await page.getByRole('tab', { name: /Requests \(1\)/ }).click();
  });

  test('booked → shot → editing → proofing → delivered with the gallery link and final price', async ({ page, request }) => {
    await signIn(page);
    await page.goto('/studio/sessions');
    await page.getByRole('tab', { name: /Booked/ }).click();
    const card = page.locator('.req', { hasText: 'Taylor Reyes' });
    await expect(card).toContainText('Booked — the session hasn’t happened yet');
    await card.getByRole('button', { name: 'Mark as shot' }).click();
    await expect(card.locator('.step-label.is-current')).toHaveText('Shot');
    await card.getByRole('button', { name: 'Start editing' }).click();
    await expect(card.locator('.step-label.is-current')).toHaveText('Editing');
    await card.getByRole('button', { name: 'Back a step' }).click();
    await expect(card.locator('.step-label.is-current')).toHaveText('Shot');
    await card.getByRole('button', { name: 'Start editing' }).click();
    await card.getByRole('button', { name: 'Send to proofing' }).click();
    await expect(card.locator('.step-label.is-current')).toHaveText('Proofing');

    await card.getByRole('button', { name: 'Deliver & email link…' }).click();
    await expect(card.getByRole('heading', { name: "Deliver Taylor Reyes's photos" })).toBeVisible();
    await expect(card.getByLabel('Subject')).toHaveValue(/photos are ready/);
    await card.getByRole('button', { name: 'Mark delivered & send' }).click(); // link missing → refused
    await expect(card.locator('.compose-error')).toContainText('full gallery link');
    await card.getByLabel('Gallery link').fill('https://photos.example.com/taylor');
    await card.getByLabel('Final price').fill('275');
    await card.getByRole('button', { name: 'Mark delivered & send' }).click();
    await expect(page.locator('.notice')).toContainText('delivered');
    await expect(page.getByRole('tab', { name: /Delivered \(1\)/ })).toBeVisible();
    const delivered = page.locator('.req', { hasText: 'Taylor Reyes' });
    await expect(delivered).toContainText('$275');
    await expect(delivered.getByRole('link', { name: 'Open gallery' })).toHaveAttribute('href', 'https://photos.example.com/taylor');
    const mail = await waitForMail(request, 'photos are ready', 'taylor@example.com');
    expect(mail.html).toContain('https://photos.example.com/taylor');
    expect(mail.html).toContain('View your photos');
  });

  test('overview revenue reflects the final price', async ({ page }) => {
    await signIn(page);
    await expect(page.locator('.stat', { hasText: 'Year to date' })).toContainText('$275');
  });
});
