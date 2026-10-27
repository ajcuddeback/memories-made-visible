import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { BookingStatus, EmailAction } from '../../core/models';
import { fmtDate, fmtRange, money, num } from '../../core/dates';
import { AdminStore } from '../admin-store.service';

const TABS: { key: BookingStatus; label: string }[] = [
  { key: 'PENDING', label: 'Pending' },
  { key: 'CONFIRMED', label: 'Confirmed' },
  { key: 'DECLINED', label: 'Declined' },
];

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
  selector: 'app-requests',
  templateUrl: './requests.html',
  styleUrl: './requests.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RequestsSection {
  readonly store = inject(AdminStore);
  readonly tab = signal<BookingStatus>('PENDING');
  readonly compose = signal<Compose | null>(null);
  readonly notice = signal<string | null>(null);
  private noticeTimer: ReturnType<typeof setTimeout> | null = null;

  readonly tabs = computed(() =>
    TABS.map((t) => ({ ...t, count: this.store.bookings().filter((b) => b.status === t.key).length })),
  );

  readonly requests = computed(() =>
    this.store.bookings()
      .filter((b) => b.status === this.tab())
      .sort((a, b) => a.date.localeCompare(b.date))
      .map((b) => ({
        ...b,
        when: `${fmtDate(b.date)} · ${b.startTime ? fmtRange(b.startTime, b.endTime) : b.time}`,
        typeLabel: [this.store.typeName().get(b.sessionType) ?? b.sessionTypeName, b.packageName ? b.packageName + (b.hours ? ` · ${b.hours} hrs` : '') + (b.addons?.length ? ' + ' + b.addons.join(' + ') : '') : null].filter(Boolean).join(' · '),
        priceLabel: money(b.quote),
      })),
  );

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
      next: (b) => this.say(`${b.name} is back in pending. No email was sent.`),
      error: () => this.say('Couldn’t update that request. Please try again.'),
    });
  }

  private say(text: string): void {
    this.notice.set(text);
    if (this.noticeTimer) clearTimeout(this.noticeTimer);
    this.noticeTimer = setTimeout(() => this.notice.set(null), 6000);
  }
}
