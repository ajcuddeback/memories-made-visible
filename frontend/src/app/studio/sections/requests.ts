import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { BookingStatus } from '../../core/models';
import { fmtDate, money, num } from '../../core/dates';
import { AdminStore } from '../admin-store.service';

const TABS: { key: BookingStatus; label: string }[] = [
  { key: 'PENDING', label: 'Pending' },
  { key: 'CONFIRMED', label: 'Confirmed' },
  { key: 'DECLINED', label: 'Declined' },
];

@Component({
  selector: 'app-requests',
  templateUrl: './requests.html',
  styleUrl: './requests.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RequestsSection {
  readonly store = inject(AdminStore);
  readonly tab = signal<BookingStatus>('PENDING');

  readonly tabs = computed(() =>
    TABS.map((t) => ({ ...t, count: this.store.bookings().filter((b) => b.status === t.key).length })),
  );

  readonly requests = computed(() =>
    this.store.bookings()
      .filter((b) => b.status === this.tab())
      .sort((a, b) => a.date.localeCompare(b.date))
      .map((b) => ({
        ...b,
        when: `${fmtDate(b.date)} · ${b.time}`,
        contact: [b.email, b.phone].filter(Boolean).join(' · '),
        priceLabel: money(b.quote),
      })),
  );

  quoteChanged(id: number, e: Event): void {
    const v = num((e.target as HTMLInputElement).value);
    this.store.setQuote(id, v);
  }

  readonly notice = signal<string | null>(null);
  private noticeTimer: ReturnType<typeof setTimeout> | null = null;

  approve(id: number): void {
    this.store.approve(id).subscribe({
      next: (b) => this.say(`Approved ${b.name} — confirmation emailed to ${b.email}.`),
      error: () => this.say('Couldn’t approve that request. Please try again.'),
    });
  }
  decline(id: number): void {
    const wasConfirmed = this.store.bookingById().get(id)?.status === 'CONFIRMED';
    this.store.decline(id).subscribe({
      next: (b) => this.say(`${wasConfirmed ? 'Cancelled' : 'Declined'} ${b.name} — ${b.email} has been emailed.`),
      error: () => this.say('Couldn’t update that request. Please try again.'),
    });
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
