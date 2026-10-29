import { APIRequestContext, Page, expect } from '@playwright/test';

export const API = 'http://localhost:8080';
export const MAIL = 'http://127.0.0.1:2526';

export const OWNER = { name: 'Austin', password: 'austin-e2e' };
export const ADMIN = { name: 'Erica', password: 'erica-e2e' };

export interface Mail { to: string[]; from: string; subject: string; text: string; html: string; at: number }

/** All captured emails, newest last. */
export async function mails(request: APIRequestContext): Promise<Mail[]> {
  return (await request.get(MAIL + '/messages')).json();
}
export async function clearMail(request: APIRequestContext): Promise<void> {
  await request.delete(MAIL + '/messages');
}
/** Waits for an email whose subject contains the fragment (async sends land a moment after the HTTP response). */
export async function waitForMail(request: APIRequestContext, subjectFragment: string, to?: string): Promise<Mail> {
  let found: Mail | undefined;
  await expect.poll(async () => {
    const all = await mails(request);
    found = all.reverse().find((m) => m.subject.includes(subjectFragment) && (!to || m.to.some((a) => a.toLowerCase() === to.toLowerCase())));
    return !!found;
  }, { timeout: 15_000, message: `email "${subjectFragment}"${to ? ' to ' + to : ''}` }).toBe(true);
  return found!;
}

/** A bearer token straight from the API, for test setup that isn't the thing under test. */
export async function token(request: APIRequestContext, who = OWNER): Promise<string> {
  const res = await request.post(API + '/api/admin/auth/login', { data: who });
  expect(res.ok(), 'login ' + who.name).toBeTruthy();
  return 'Bearer ' + (await res.json()).token;
}
export async function api(request: APIRequestContext, method: 'get' | 'post' | 'put' | 'patch' | 'delete', path: string, data?: unknown, who = OWNER) {
  const auth = await token(request, who);
  const res = await request[method](API + path, { data, headers: { Authorization: auth } });
  return res;
}

/** Sign in through the real login card. */
export async function signIn(page: Page, who = OWNER): Promise<void> {
  await page.goto('/studio');
  await page.getByRole('radio', { name: who.name }).click();
  await page.getByLabel('Password').fill(who.password);
  await page.getByRole('button', { name: 'Sign in as ' + who.name }).click();
  await expect(page.getByRole('heading', { name: 'Overview' })).toBeVisible();
}
export async function signOut(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Log out' }).first().click();
  await expect(page.getByRole('heading', { name: 'Studio admin' })).toBeVisible();
}

/** A weekday at least `daysAhead` out, as yyyy-mm-dd plus parts for picking it in the public calendar. */
export function futureWeekday(daysAhead: number): { iso: string; day: number; month: number; year: number; date: Date } {
  const d = new Date(); d.setHours(0, 0, 0, 0); d.setDate(d.getDate() + daysAhead);
  while (d.getDay() === 0 || d.getDay() === 6) d.setDate(d.getDate() + 1);
  const pad = (n: number) => String(n).padStart(2, '0');
  return { iso: `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`, day: d.getDate(), month: d.getMonth(), year: d.getFullYear(), date: d };
}

/** Navigate the public booking calendar to a month and click a day. */
export async function pickPublicDate(page: Page, target: { day: number; month: number; year: number }): Promise<void> {
  await goToMonth(page, page.locator('.cal-label'), page.getByRole('button', { name: 'Next month' }), target);
  await page.locator('.cal-grid').getByRole('button', { name: new RegExp(`^${target.day}, `) }).click();
}

/** Click "next" until a month label reads the target month, waiting for each change to render. */
export async function goToMonth(page: Page, label: import('@playwright/test').Locator, next: import('@playwright/test').Locator,
                                target: { month: number; year: number }): Promise<void> {
  const want = new Date(target.year, target.month, 1).toLocaleDateString('en-US', { month: 'long', year: 'numeric' });
  await expect(label).not.toBeEmpty();
  for (let i = 0; i < 30; i++) {
    const current = (await label.textContent())?.trim();
    if (current === want) break;
    await next.click();
    await expect(label).not.toHaveText(current ?? '');
  }
  await expect(label).toHaveText(want);
}
