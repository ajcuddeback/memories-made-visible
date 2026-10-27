import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { fmtDate, fromISO, money, today } from '../../core/dates';
import { AdminStore } from '../admin-store.service';

const STEPS = ['Shot', 'Editing', 'Proofing', 'Delivered'];

/** The delivery panel: link + final price + the reviewed email. */
interface Deliver {
  galleryId: number;
  client: string;
  to: string;
  link: string;
  finalPrice: string;
  sendEmail: boolean;
  subject: string;
  body: string;
  loading: boolean;
  busy: boolean;
  error: string | null;
}

@Component({
  selector: 'app-galleries',
  imports: [DatePipe],
  templateUrl: './galleries.html',
  styleUrl: './galleries.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GalleriesSection {
  readonly store = inject(AdminStore);
  private readonly today = today();

  readonly newGal = signal<number | null>(null);
  readonly deliver = signal<Deliver | null>(null);
  readonly notice = signal<string | null>(null);
  private noticeTimer: ReturnType<typeof setTimeout> | null = null;

  readonly rows = computed(() => {
    const byId = this.store.bookingById();
    const names = this.store.typeName();
    return this.store.galleries()
      .filter((g) => byId.has(g.bookingId))
      .map((g) => {
        const b = byId.get(g.bookingId)!;
        return {
          id: g.id,
          stage: g.stage,
          client: b.name,
          session: names.get(b.sessionType) ?? b.sessionTypeName,
          date: fmtDate(b.date),
          price: b.quote ? money(b.quote) : null,
          link: g.link,
          deliveredAt: g.deliveredAt,
          steps: STEPS.map((label, i) => ({ label, done: i <= g.stage, current: i === g.stage, delivered: g.stage === 3 })),
          backOff: g.stage === 0,
          nextOff: g.stage === 3,
          nextLabel: g.stage === 3 ? 'Delivered' : g.stage === 2 ? 'Deliver & email link' : 'Mark ' + STEPS[g.stage + 1].toLowerCase(),
        };
      })
      .sort((a, b) => Number(a.nextOff) - Number(b.nextOff));
  });

  /** Every confirmed session without a gallery yet: shot ones first, upcoming ones after (you can prep early). */
  readonly options = computed(() => {
    const has = new Set(this.store.galleries().map((g) => g.bookingId));
    const names = this.store.typeName();
    return this.store.confirmed()
      .filter((b) => !has.has(b.id))
      .map((b) => ({ id: b.id, upcoming: fromISO(b.date) > this.today, label: `${b.name} · ${names.get(b.sessionType) ?? b.sessionTypeName} · ${fmtDate(b.date)}` }))
      .sort((a, b) => Number(a.upcoming) - Number(b.upcoming));
  });
  readonly readyOptions = computed(() => this.options().filter((o) => !o.upcoming));
  readonly upcomingOptions = computed(() => this.options().filter((o) => o.upcoming));

  pick(e: Event): void {
    const v = (e.target as HTMLSelectElement).value;
    this.newGal.set(v ? Number(v) : null);
  }

  add(): void {
    const id = this.newGal();
    if (id == null) return;
    this.store.addGallery(id);
    this.newGal.set(null);
  }

  back(id: number, stage: number): void { this.store.setStage(id, Math.max(0, stage - 1)); }

  /** Shot → Editing → Proofing move straight through; the last step opens the delivery panel. */
  next(id: number, stage: number): void {
    if (stage >= 2) { this.openDeliver(id); return; }
    this.store.setStage(id, stage + 1);
  }

  openDeliver(id: number): void {
    const g = this.store.galleries().find((x) => x.id === id);
    const b = g && this.store.bookingById().get(g.bookingId);
    if (!g || !b) return;
    this.deliver.set({
      galleryId: id, client: b.name, to: b.email, link: g.link ?? '', finalPrice: b.quote != null ? String(b.quote) : '',
      sendEmail: true, subject: '', body: '', loading: true, busy: false, error: null,
    });
    this.store.galleryDraft(id).subscribe({
      next: (d) => this.deliver.update((c) => (c && c.galleryId === id ? { ...c, subject: d.subject, body: d.body, to: d.to, loading: false } : c)),
      error: () => this.deliver.update((c) => (c ? { ...c, loading: false, error: 'Couldn’t load the draft. You can still write the email yourself.' } : c)),
    });
  }

  closeDeliver(): void { this.deliver.set(null); }

  set(field: 'link' | 'finalPrice' | 'subject' | 'body', e: Event): void {
    const v = (e.target as HTMLInputElement | HTMLTextAreaElement).value;
    this.deliver.update((c) => (c ? { ...c, [field]: v } : c));
  }
  setSend(e: Event): void {
    const v = (e.target as HTMLInputElement).checked;
    this.deliver.update((c) => (c ? { ...c, sendEmail: v } : c));
  }

  submitDeliver(): void {
    const c = this.deliver();
    if (!c || c.busy) return;
    const link = c.link.trim();
    if (!/^https?:\/\/\S+$/i.test(link)) { this.fail('Paste the full gallery link, starting with https://'); return; }
    const price = c.finalPrice.trim() === '' ? null : Number(c.finalPrice.replace(/[^0-9.]/g, ''));
    if (price != null && (Number.isNaN(price) || price < 0)) { this.fail('The final price needs to be a number.'); return; }
    if (c.sendEmail && (!c.subject.trim() || !c.body.trim())) { this.fail('Add a subject and a message, or untick "Email the client".'); return; }
    this.deliver.update((x) => (x ? { ...x, busy: true, error: null } : x));
    this.store.deliverGallery(c.galleryId, { link, finalPrice: price, sendEmail: c.sendEmail, subject: c.subject.trim(), body: c.body.trim() }).subscribe({
      next: () => {
        this.closeDeliver();
        this.say(`${c.client}'s gallery is delivered${c.sendEmail ? ` — link emailed to ${c.to}` : ' — no email sent'}.${price != null ? ' Revenue updated.' : ''}`);
      },
      error: (e) => this.fail(e?.error?.message),
    });
  }

  private fail(msg?: string): void {
    this.deliver.update((x) => (x ? { ...x, busy: false, error: msg ?? 'That didn’t go through. Please try again.' } : x));
  }

  private say(text: string): void {
    this.notice.set(text);
    if (this.noticeTimer) clearTimeout(this.noticeTimer);
    this.noticeTimer = setTimeout(() => this.notice.set(null), 7000);
  }
}
