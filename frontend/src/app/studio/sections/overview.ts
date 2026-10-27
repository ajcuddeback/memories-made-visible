import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
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

  /** Confirmed-session quotes and pop-up print sales for a given month, separately. */
  private monthParts(y: number, m: number): { sessions: number; popups: number } {
    const sessions = this.store.confirmed().filter((b) => sameMonth(b.date, y, m)).reduce((a, b) => a + num(b.quote), 0);
    const popups = this.store.events().filter((e) => sameMonth(e.date, y, m)).reduce((a, e) => a + e.prints * num(e.printPrice), 0);
    return { sessions, popups };
  }
  private monthRevenue(y: number, m: number): number {
    const p = this.monthParts(y, m);
    return p.sessions + p.popups;
  }

  /** Chart window. */
  readonly range = signal<'6m' | '12m' | 'year'>('6m');
  readonly ranges: { key: '6m' | '12m' | 'year'; label: string }[] = [
    { key: '6m', label: '6 months' }, { key: '12m', label: 'Past year' }, { key: 'year', label: String(this.today.getFullYear()) },
  ];

  /** Months in the chosen window: 6m = three back, now, two ahead; 12m = the past twelve; year = Jan–Dec. */
  readonly months = computed(() => {
    const y0 = this.today.getFullYear(), m0 = this.today.getMonth();
    const range: [number, number][] = [];
    const push = (offset: number) => { const d = new Date(y0, m0 + offset, 1); range.push([d.getFullYear(), d.getMonth()]); };
    switch (this.range()) {
      case '6m': for (let i = -3; i <= 2; i++) push(i); break;
      case '12m': for (let i = -11; i <= 0; i++) push(i); break;
      case 'year': for (let m = 0; m < 12; m++) range.push([y0, m]); break;
    }
    const parts = range.map(([y, m]) => this.monthParts(y, m));
    const max = Math.max(1, ...parts.map((p) => Math.max(p.sessions, p.popups)));
    const showYear = range.some(([y]) => y !== y0);
    return range.map(([y, m], i) => {
      const isNow = y === y0 && m === m0;
      const future = new Date(y, m, 1) > this.today;
      const total = parts[i].sessions + parts[i].popups;
      return {
        key: `${y}-${m}`,
        mon: MON[m] + (showYear && (m === 0 || i === 0) ? ` ’${String(y).slice(2)}` : ''),
        label: total ? money(total) : '—',
        total,
        sessionsH: Math.round((parts[i].sessions / max) * 72),
        popupsH: Math.round((parts[i].popups / max) * 72),
        isNow, future,
        name: `${MONTHS[m]} ${y}`,
        sessions: money(parts[i].sessions),
        popups: money(parts[i].popups),
      };
    });
  });
  readonly rangeTotal = computed(() => money(this.months().reduce((a, m) => a + m.total, 0)));
  readonly rangeAria = computed(() => ({ '6m': 'Monthly revenue, three months back through two ahead', '12m': 'Monthly revenue, past twelve months', year: `Monthly revenue, ${this.today.getFullYear()}` })[this.range()]);

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
