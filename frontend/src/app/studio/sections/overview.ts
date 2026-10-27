import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MON, MONTHS, fromISO, money, num, sameMonth, today } from '../../core/dates';
import { AdminStore } from '../admin-store.service';

@Component({
  selector: 'app-overview',
  imports: [RouterLink],
  templateUrl: './overview.html',
  styleUrl: './overview.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OverviewSection {
  readonly store = inject(AdminStore);
  private readonly today = today();

  /** Confirmed-session quotes plus pop-up print sales for a given month. */
  private monthRevenue(y: number, m: number): number {
    const sessions = this.store.confirmed().filter((b) => sameMonth(b.date, y, m)).reduce((a, b) => a + num(b.quote), 0);
    const prints = this.store.events().filter((e) => sameMonth(e.date, y, m)).reduce((a, e) => a + e.prints * num(e.printPrice), 0);
    return sessions + prints;
  }

  /** Six-month window: three back, this month, two ahead. */
  readonly months = computed(() => {
    const range: [number, number][] = [];
    for (let i = 5; i >= 0; i--) {
      const d = new Date(this.today.getFullYear(), this.today.getMonth() - i + 2, 1);
      range.push([d.getFullYear(), d.getMonth()]);
    }
    const vals = range.map(([y, m]) => this.monthRevenue(y, m));
    const max = Math.max(1, ...vals);
    return range.map(([y, m], i) => {
      const isNow = y === this.today.getFullYear() && m === this.today.getMonth();
      const future = new Date(y, m, 1) > this.today;
      return {
        mon: MON[m],
        label: vals[i] ? money(vals[i]) : '—',
        h: Math.round((vals[i] / max) * 72),
        bg: isNow ? 'var(--color-accent)' : future ? 'var(--color-accent-300)' : 'var(--color-accent-2)',
      };
    });
  });

  readonly stats = computed(() => {
    const y = this.today.getFullYear();
    let ytd = 0;
    for (let m = 0; m < 12; m++) ytd += this.monthRevenue(y, m);
    const pending = this.store.pending();
    const prints = this.store.events().reduce((a, e) => a + e.prints, 0);
    const printRev = this.store.events().reduce((a, e) => a + e.prints * num(e.printPrice), 0);
    return [
      { label: 'Revenue · ' + MONTHS[this.today.getMonth()], value: money(this.monthRevenue(y, this.today.getMonth())), sub: 'Confirmed sessions (final price once delivered) + prints', accent: true },
      { label: 'Year to date', value: money(ytd), sub: y + ' booked revenue', accent: false },
      { label: 'Pending requests', value: String(pending.length), sub: money(pending.reduce((a, b) => a + num(b.quote), 0)) + ' in quotes', accent: false },
      { label: 'Pop-up prints sold', value: String(prints), sub: money(printRev) + ' from pop-ups', accent: false },
    ];
  });

  readonly nextUp = computed(() => {
    const names = this.store.typeName();
    const items = [
      ...this.store.confirmed().filter((b) => fromISO(b.date) >= this.today)
        .map((b) => ({ date: b.date, title: b.name, sub: `${names.get(b.sessionType) ?? b.sessionTypeName} · ${b.time}`, tint: 'var(--color-accent-100)' })),
      ...this.store.events().filter((e) => fromISO(e.date) >= this.today)
        .map((e) => ({ date: e.date, title: e.title, sub: 'Pop-up · ' + e.venue, tint: 'var(--color-accent-2-200)' })),
    ];
    return items.sort((a, b) => a.date.localeCompare(b.date)).slice(0, 5).map((x) => {
      const d = fromISO(x.date);
      return { ...x, mon: MON[d.getMonth()], day: String(d.getDate()) };
    });
  });

  readonly pendingLabel = computed(() => {
    const n = this.store.pending().length;
    return `Review ${n} pending request${n === 1 ? '' : 's'}`;
  });
  readonly galleriesLabel = computed(() => {
    const ids = this.store.bookingById();
    const n = this.store.galleries().filter((g) => g.stage < 3 && ids.has(g.bookingId)).length;
    return `${n} session${n === 1 ? '' : 's'} being edited`;
  });
}
