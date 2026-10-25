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

  approve(id: number): void { this.store.approve(id); }
  decline(id: number): void { this.store.decline(id); }
  restore(id: number): void { this.store.restore(id); }
}
