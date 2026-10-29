import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { BookingStatus, EmailAction, Gallery, TeamMember } from '../../core/models';
import { DatePipe } from '@angular/common';
import { fmtDate, fmtRange, fromISO, money, num, today } from '../../core/dates';
import { AdminStore } from '../admin-store.service';
import { AuthService } from '../auth.service';

/** One session's whole life: request → booked → shot/edited/proofed → delivered (or declined). */
type Tab = 'REQUESTS' | 'BOOKED' | 'DELIVERED' | 'DECLINED';
const TABS: { key: Tab; label: string; hint: string }[] = [
  { key: 'REQUESTS', label: 'Requests', hint: 'New requests from the website. Set a quote, then approve or decline — the customer gets an email either way.' },
  { key: 'BOOKED', label: 'Booked', hint: 'Approved sessions, upcoming or being worked on. Track each from shot to delivered.' },
  { key: 'DELIVERED', label: 'Delivered', hint: 'Finished sessions — the client has their gallery link.' },
  { key: 'DECLINED', label: 'Declined', hint: 'Declined or cancelled. They can be moved back to Requests.' },
];

const STEPS = ['Shot', 'Editing', 'Proofing', 'Delivered'];

/** The delivery panel: link + final price + the reviewed email. */
interface Deliver {
  galleryId: number; client: string; to: string; link: string; finalPrice: string;
  sendEmail: boolean; subject: string; body: string; loading: boolean; busy: boolean; error: string | null;
}

/** The email the admin is reviewing before an action goes through. */
interface Compose {
  bookingId: number;
  action: EmailAction;
  to: string;
  subject: string;
  body: string;
  sendEmail: boolean;
  loading: boolean;
  busy: boolean;
  error: string | null;
}

const ACTION_LABEL: Record<EmailAction, { title: string; button: string }> = {
  approve: { title: 'Approve & email confirmation', button: 'Approve & send' },
  decline: { title: 'Decline & email customer', button: 'Decline & send' },
  cancel: { title: 'Cancel session & email customer', button: 'Cancel session & send' },
  message: { title: 'Email the customer', button: 'Send email' },
};

@Component({
  selector: 'app-sessions',
  imports: [DatePipe],
  templateUrl: './sessions.html',
  styleUrl: './sessions.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SessionsSection {
  readonly store = inject(AdminStore);
  private readonly auth = inject(AuthService);
  readonly canManage = this.auth.canManage;
  /** Owner/admin, or the session's lead photographer: may quote, approve, decline, email, deliver. */
  canRun(r: { lead: string | null }): boolean {
    return this.canManage() || (!!r.lead && r.lead.toLowerCase() === (this.auth.user()?.name ?? '').toLowerCase());
  }
  /** Active staff who can be put on a session (loaded for owner/admin only). */
  readonly staff = signal<TeamMember[]>([]);
  /** Which card has its assignment picker open. */
  readonly assigning = signal<number | null>(null);
  readonly tab = signal<Tab>('REQUESTS');
  readonly compose = signal<Compose | null>(null);
  readonly deliver = signal<Deliver | null>(null);
  private readonly today = today();
  readonly notice = signal<string | null>(null);
  private noticeTimer: ReturnType<typeof setTimeout> | null = null;

  private readonly galleryByBooking = computed(() => new Map(this.store.galleries().map((g) => [g.bookingId, g])));
  private tabOf(status: BookingStatus, g: Gallery | undefined): Tab {
    if (status === 'PENDING') return 'REQUESTS';
    if (status === 'DECLINED') return 'DECLINED';
    return g && g.stage >= 3 ? 'DELIVERED' : 'BOOKED';
  }
  readonly tabs = computed(() => {
    const gal = this.galleryByBooking();
    return TABS.map((t) => ({ ...t, count: this.store.bookings().filter((b) => this.tabOf(b.status, gal.get(b.id)) === t.key).length }));
  });
  readonly tabHint = computed(() => TABS.find((t) => t.key === this.tab())?.hint ?? '');

  constructor() {
    if (this.canManage()) this.store.team().subscribe({ next: (m) => this.staff.set(m.filter((x) => x.active)), error: () => undefined });
  }

  toggleAssign(id: number): void { this.assigning.update((v) => (v === id ? null : id)); }
  /** Change the lead or the second; the server checks the person is free and emails newcomers. */
  setRole(bookingId: number, role: 'lead' | 'second', e: Event): void {
    const name = (e.target as HTMLSelectElement).value || null;
    const b = this.store.bookingById().get(bookingId);
    if (!b) return;
    const lead = role === 'lead' ? name : b.lead;
    const second = role === 'second' ? name : b.second;
    this.store.assign(bookingId, lead, second).subscribe({
      next: (u) => { if (name) this.say(`${name} is the ${role === 'lead' ? 'lead' : 'second photographer'} on ${u.name}'s session${this.staffEmail(name) ? ' and has been emailed' : ''}.`); },
      error: (err) => { this.say(err?.error?.message ?? 'Couldn’t change who’s on this session.'); this.store.load(this.canManage()); },
    });
  }
  private staffEmail(name: string): string | null { return this.staff().find((s) => s.name === name)?.email ?? null; }

  readonly requests = computed(() => {
    const gal = this.galleryByBooking();
    return this.store.bookings()
      .filter((b) => this.tabOf(b.status, gal.get(b.id)) === this.tab())
      .sort((a, b) => a.date.localeCompare(b.date))
      .map((b) => {
        const g = gal.get(b.id);
        const stage = g ? g.stage : -1; // -1 = booked, not shot yet
        return {
        ...b,
        gallery: g ?? null,
        stage,
        upcoming: fromISO(b.date) > this.today,
        team: [b.lead ? b.lead + ' (lead)' : '', b.second ? b.second + ' (second)' : ''].filter(Boolean).join(' · '),
        missingSecond: !!b.needsSecond && !b.second && b.status !== 'DECLINED',
        steps: STEPS.map((label, i) => ({ label, done: i <= stage, current: i === stage, delivered: stage === 3 })),
        nextLabel: stage < 0 ? 'Mark as shot' : stage === 0 ? 'Start editing' : stage === 1 ? 'Send to proofing' : stage === 2 ? 'Deliver & email link…' : '',
        when: `${fmtDate(b.date)} · ${b.startTime ? fmtRange(b.startTime, b.endTime) : b.time}`,
        typeLabel: [this.store.typeName().get(b.sessionType) ?? b.sessionTypeName, b.packageName ? b.packageName + (b.hours ? ` · ${b.hours} hrs` : '') + (b.addons?.length ? ' + ' + b.addons.join(' + ') : '') : null].filter(Boolean).join(' · '),
        priceLabel: money(b.quote),
        };
      });
  });

  labelFor(action: EmailAction) {
    return ACTION_LABEL[action];
  }

  quoteChanged(id: number, e: Event): void {
    const v = num((e.target as HTMLInputElement).value);
    this.store.setQuote(id, v);
  }

  /** Open the compose panel for an action, pre-filled from the server's draft. */
  open(id: number, action: EmailAction): void {
    const b = this.store.bookingById().get(id);
    if (!b) return;
    this.deliver.set(null);
    this.compose.set({ bookingId: id, action, to: b.email, subject: '', body: '', sendEmail: true, loading: true, busy: false, error: null });
    this.store.draft(id, action).subscribe({
      next: (d) => this.compose.update((c) => (c && c.bookingId === id ? { ...c, subject: d.subject, body: d.body, to: d.to, loading: false } : c)),
      error: () => this.compose.update((c) => (c ? { ...c, loading: false, error: 'Couldn’t load the draft. You can still write the email yourself.' } : c)),
    });
  }

  close(): void {
    this.compose.set(null);
  }

  setSubject(e: Event): void { const v = (e.target as HTMLInputElement).value; this.compose.update((c) => (c ? { ...c, subject: v } : c)); }
  setBody(e: Event): void { const v = (e.target as HTMLTextAreaElement).value; this.compose.update((c) => (c ? { ...c, body: v } : c)); }
  setSend(e: Event): void { const v = (e.target as HTMLInputElement).checked; this.compose.update((c) => (c ? { ...c, sendEmail: v } : c)); }

  /** Approve / decline / cancel with the reviewed email (or without one), or send a plain note. */
  submit(): void {
    const c = this.compose();
    if (!c || c.busy) return;
    const b = this.store.bookingById().get(c.bookingId);
    if (!b) return;
    if (c.sendEmail && (!c.subject.trim() || !c.body.trim())) {
      this.compose.update((x) => (x ? { ...x, error: 'Add a subject and a message, or untick "Email the customer".' } : x));
      return;
    }
    this.compose.update((x) => (x ? { ...x, busy: true, error: null } : x));
    const email = { sendEmail: c.sendEmail, subject: c.subject.trim(), body: c.body.trim() };
    const sent = c.sendEmail ? ` — emailed ${c.to}` : ' — no email sent';

    const done = (text: string) => { this.close(); this.say(text); };
    const fail = (msg?: string) => this.compose.update((x) => (x ? { ...x, busy: false, error: msg ?? 'That didn’t go through. Please try again.' } : x));

    switch (c.action) {
      case 'approve':
        this.store.approve(c.bookingId, email).subscribe({ next: () => done(`Approved ${b.name}${sent}.`), error: (e) => fail(e?.error?.message) });
        break;
      case 'decline':
      case 'cancel':
        this.store.decline(c.bookingId, email).subscribe({
          next: () => done(`${c.action === 'cancel' ? 'Cancelled' : 'Declined'} ${b.name}${sent}.`),
          error: (e) => fail(e?.error?.message),
        });
        break;
      case 'message':
        this.store.message(c.bookingId, email).subscribe({ next: () => done(`Email sent to ${c.to}.`), error: (e) => fail(e?.error?.message) });
        break;
    }
  }

  restore(id: number): void {
    this.store.restore(id).subscribe({
      next: (b) => { this.tab.set('REQUESTS'); this.say(`${b.name} is back in Requests. No email was sent.`); },
      error: () => this.say('Couldn’t update that request. Please try again.'),
    });
  }

  // ── delivery pipeline (booked → shot → editing → proofing → delivered) ──
  advance(bookingId: number, stage: number): void {
    const g = this.galleryByBooking().get(bookingId);
    if (!g) { this.store.addGallery(bookingId); this.say('Marked as shot. Next: start editing when you’re ready.'); return; }
    if (g.stage >= 2) { this.openDeliver(g.id); return; }
    this.store.setStage(g.id, g.stage + 1);
  }
  back(bookingId: number): void {
    const g = this.galleryByBooking().get(bookingId);
    if (!g) return;
    if (g.stage === 0) { if (confirm('Move this session back to "booked, not shot yet"?')) this.store.removeGallery(g.id); return; }
    this.store.setStage(g.id, g.stage - 1);
  }

  openDeliver(galleryId: number): void {
    const g = this.store.galleries().find((x) => x.id === galleryId);
    const b = g && this.store.bookingById().get(g.bookingId);
    if (!g || !b) return;
    this.compose.set(null);
    this.deliver.set({
      galleryId, client: b.name, to: b.email, link: g.link ?? '', finalPrice: b.quote != null ? String(b.quote) : '',
      sendEmail: true, subject: '', body: '', loading: true, busy: false, error: null,
    });
    this.store.galleryDraft(galleryId).subscribe({
      next: (d) => this.deliver.update((c) => (c && c.galleryId === galleryId ? { ...c, subject: d.subject, body: d.body, to: d.to, loading: false } : c)),
      error: () => this.deliver.update((c) => (c ? { ...c, loading: false, error: 'Couldn’t load the draft. You can still write the email yourself.' } : c)),
    });
  }
  closeDeliver(): void { this.deliver.set(null); }
  setDeliver(field: 'link' | 'finalPrice' | 'subject' | 'body', e: Event): void {
    const v = (e.target as HTMLInputElement | HTMLTextAreaElement).value;
    this.deliver.update((c) => (c ? { ...c, [field]: v } : c));
  }
  setDeliverSend(e: Event): void {
    const v = (e.target as HTMLInputElement).checked;
    this.deliver.update((c) => (c ? { ...c, sendEmail: v } : c));
  }
  submitDeliver(): void {
    const c = this.deliver();
    if (!c || c.busy) return;
    const link = c.link.trim();
    const fail = (msg?: string) => this.deliver.update((x) => (x ? { ...x, busy: false, error: msg ?? 'That didn’t go through. Please try again.' } : x));
    if (!/^https?:\/\/\S+$/i.test(link)) { fail('Paste the full gallery link, starting with https://'); return; }
    const price = c.finalPrice.trim() === '' ? null : Number(c.finalPrice.replace(/[^0-9.]/g, ''));
    if (price != null && (Number.isNaN(price) || price < 0)) { fail('The final price needs to be a number.'); return; }
    if (c.sendEmail && (!c.subject.trim() || !c.body.trim())) { fail('Add a subject and a message, or untick "Email the client".'); return; }
    this.deliver.update((x) => (x ? { ...x, busy: true, error: null } : x));
    this.store.deliverGallery(c.galleryId, { link, finalPrice: price, sendEmail: c.sendEmail, subject: c.subject.trim(), body: c.body.trim() }).subscribe({
      next: () => {
        this.closeDeliver();
        this.tab.set('DELIVERED');
        this.say(`${c.client}'s photos are delivered${c.sendEmail ? ` — link emailed to ${c.to}` : ' — no email sent'}.${price != null ? ' Revenue updated.' : ''}`);
      },
      error: (e) => fail(e?.error?.message),
    });
  }

  private say(text: string): void {
    this.notice.set(text);
    if (this.noticeTimer) clearTimeout(this.noticeTimer);
    this.noticeTimer = setTimeout(() => this.notice.set(null), 6000);
  }
}
