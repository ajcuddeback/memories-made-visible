import { expect, test } from '@playwright/test';
import { ADMIN, OWNER, api, clearMail, signIn, signOut, waitForMail } from './helpers';

test.describe.configure({ mode: 'serial' });

const SAM = { name: 'Sam', password: 'sams-new-password' };

test.describe('team, roles and passwords', () => {
  test('owner and admin see the full studio; the Team page lists them', async ({ page }) => {
    await signIn(page, ADMIN);
    await expect(page.getByRole('link', { name: 'Pricing' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Team' })).toBeVisible();
    await page.goto('/studio/team');
    await expect(page.locator('.member', { hasText: 'Austin' })).toContainText('Owner');
    await expect(page.locator('.member', { hasText: 'Erica' })).toContainText('Admin');
    await expect(page.locator('.member', { hasText: 'Austin' })).toContainText('Only the owner can change this account');
  });

  test('an admin adds an employee, who signs in with the emailed temporary password and must change it', async ({ page, request }) => {
    await clearMail(request);
    await signIn(page, ADMIN);
    await page.goto('/studio/team');
    await page.getByRole('button', { name: '+ Add a person' }).click();
    await page.getByLabel(/^Name/).fill('Sam');
    await page.getByLabel('Job title').fill('Assistant photographer');
    await page.getByLabel('Email').fill('sam@e2e.local');
    await page.getByLabel('Access').selectOption('EMPLOYEE');
    await page.getByRole('button', { name: 'Add & send invite' }).click();
    await expect(page.locator('.notice')).toContainText('Sam added');
    await expect(page.locator('.member', { hasText: 'Sam' })).toContainText('Hasn’t signed in yet');

    const invite = await waitForMail(request, 'studio team', 'sam@e2e.local');
    const temp = /Temporary password: (\S+)/.exec(invite.text)?.[1];
    expect(temp, 'temporary password in the invite').toBeTruthy();

    await signOut(page);
    await page.getByRole('radio', { name: 'Sam' }).click();
    await page.getByLabel('Password').fill(temp!);
    await page.getByRole('button', { name: 'Sign in as Sam' }).click();
    await expect(page.getByRole('heading', { name: 'Choose your password' })).toBeVisible();
    await page.getByLabel('Temporary password').fill(temp!);
    await page.getByLabel('New password', { exact: true }).fill(SAM.password);
    await page.getByLabel('New password again').fill(SAM.password);
    await page.getByRole('button', { name: 'Save password' }).click();
    await expect(page.getByRole('heading', { name: 'Overview' })).toBeVisible();
  });

  test('an employee sees a reduced studio and no sessions until assigned', async ({ page }) => {
    await signIn(page, SAM);
    for (const hidden of ['Pricing', 'Photos', 'Insights', 'Team']) {
      await expect(page.getByRole('link', { name: hidden })).toHaveCount(0);
    }
    await expect(page.locator('.publish-bar')).toHaveCount(0);
    await expect(page.locator('.stat', { hasText: 'Revenue' })).toHaveCount(0);
    await page.goto('/studio/sessions');
    await expect(page.locator('.tab-hint')).toContainText(/assigned to/);
    await page.getByRole('tab', { name: /Delivered/ }).click();
    await expect(page.locator('.req')).toHaveCount(0);
    // The API refuses admin-only things outright
    const forbidden = await api(page.request, 'get', '/api/admin/pricing', undefined, SAM);
    expect(forbidden.status()).toBe(403);
  });

  test('owner assigns Sam to Taylor’s session; Sam gets the email and sees exactly that one', async ({ page, request }) => {
    await clearMail(request);
    await signIn(page, OWNER);
    await page.goto('/studio/sessions');
    await page.getByRole('tab', { name: /Delivered/ }).click();
    const card = page.locator('.req', { hasText: 'Taylor Reyes' });
    await expect(card).toContainText('Erica (lead)'); // the default photographer led it
    await card.getByRole('button', { name: 'Assign…' }).click();
    await card.getByLabel(/^Lead photographer/).selectOption('Sam');
    await expect(page.locator('.notice')).toContainText("Sam is the lead on Taylor Reyes's session");
    await expect(card).toContainText('Sam (lead)');
    const mail = await waitForMail(request, "You're on Taylor Reyes's", 'sam@e2e.local');
    expect(mail.text).toContain('Austin put you on');

    await signOut(page);
    await signIn(page, SAM);
    await page.goto('/studio/sessions');
    await page.getByRole('tab', { name: /Delivered \(1\)/ }).click();
    await expect(page.locator('.req', { hasText: 'Taylor Reyes' })).toBeVisible();
    // As lead, Sam runs the session: the email action is there (the Delivered card's resend is lead-or-admin)
    await expect(page.getByRole('button', { name: 'Email customer…' })).toBeVisible();
    await expect(page.locator('.tab-hint')).toContainText('As lead you run the session');
    // …and can set their own hours but sees nobody else's
    await page.goto('/studio/calendar');
    await page.getByRole('button', { name: 'My hours' }).click();
    await expect(page.locator('.hours-for')).toHaveCount(0);
    await expect(page.locator('.panel-hint').first()).toContainText('Sam follows the studio');
    await page.locator('.hour-row', { hasText: 'Mon' }).getByRole('checkbox').uncheck();
    await page.getByRole('button', { name: 'Save hours' }).click();
    await expect(page.locator('.panel-msg')).toContainText('Sam now has their own hours');
    page.once('dialog', (d) => d.accept());
    await page.getByRole('button', { name: 'Use studio hours' }).click();
    await expect(page.locator('.panel-msg')).toContainText("follows the studio's hours");
  });

  test('forgot password: the emailed link sets a new one and signs Sam in', async ({ page, request }) => {
    await clearMail(request);
    await page.goto('/studio');
    await page.getByRole('button', { name: 'Forgot your password?' }).click();
    await page.getByLabel('Email on your account').fill('sam@e2e.local');
    await page.getByRole('button', { name: 'Email me a reset link' }).click();
    await expect(page.getByText('a reset link is on its way')).toBeVisible();
    const mail = await waitForMail(request, 'Reset your studio password', 'sam@e2e.local');
    const link = /http:\/\/localhost:4200\/studio\?reset=[0-9a-f]+/.exec(mail.text)?.[0];
    expect(link, 'reset link').toBeTruthy();
    await page.goto(link!);
    await page.getByLabel('New password', { exact: true }).fill('sams-reset-pw-1');
    await page.getByLabel('New password again').fill('sams-reset-pw-1');
    await page.getByRole('button', { name: 'Set password & sign in' }).click();
    await expect(page.getByRole('heading', { name: 'Overview' })).toBeVisible();
    SAM.password = 'sams-reset-pw-1';
  });

  test('owner can deactivate and remove; an admin cannot touch the owner', async ({ page }) => {
    await signIn(page, OWNER);
    await page.goto('/studio/team');
    const sam = page.locator('.member', { hasText: 'Sam' });
    page.once('dialog', (d) => d.accept());
    await sam.getByRole('button', { name: 'Deactivate' }).click();
    await expect(sam).toContainText('Deactivated');
    await sam.getByRole('button', { name: 'Reactivate' }).click();
    await expect(sam).not.toContainText('Deactivated');
    // API-level guard: Erica (admin) may not remove Austin (owner)
    const res = await api(page.request, 'delete', '/api/admin/users/Austin', undefined, ADMIN);
    expect(res.status()).toBe(400);
  });
});
