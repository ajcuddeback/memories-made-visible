import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { AnalyticsReport } from '../../core/models';
import { fromISO } from '../../core/dates';
import { AdminStore } from '../admin-store.service';

const PAGE_NAMES: Record<string, string> = {
  '/': 'Home', '/portfolio': 'Portfolio', '/events': 'Events', '/pricing': 'Pricing', '/book': 'Book a session', '/about': 'About',
};

@Component({
  selector: 'app-insights',
  templateUrl: './insights.html',
  styleUrl: './insights.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InsightsSection {
  private readonly store = inject(AdminStore);
  readonly days = signal(30);
  readonly ranges = [{ d: 7, label: '7 days' }, { d: 30, label: '30 days' }, { d: 90, label: '90 days' }, { d: 365, label: 'Year' }];
  readonly report = signal<AnalyticsReport | null>(null);
  readonly loading = signal(true);
  readonly error = signal(false);

  constructor() {
    effect(() => { const d = this.days(); untracked(() => this.load(d)); });
  }

  private load(days: number): void {
    this.loading.set(true); this.error.set(false);
    this.store.analytics(days).subscribe({
      next: (r) => { this.report.set(r); this.loading.set(false); },
      error: () => { this.loading.set(false); this.error.set(true); },
    });
  }

  readonly tiles = computed(() => {
    const r = this.report();
    if (!r) return [];
    const t = r.totals;
    const conv = r.funnel[0]?.visits ? Math.round((r.bookingsSubmitted / r.funnel[0].visits) * 100) : 0;
    return [
      { label: 'Unique visitors', value: String(t.visitors), sub: `${t.visits} visit${t.visits === 1 ? '' : 's'} · ${t.pageviews} page views`, accent: true },
      { label: 'Average visit', value: fmtSeconds(t.avgVisitSeconds), sub: `${Math.round(t.bounceRate * 100)}% left after one page`, accent: false },
      { label: 'Booking requests', value: String(r.bookingsSubmitted), sub: `${conv}% of people who opened the booking page`, accent: false },
      { label: 'Mobile share', value: pct(r.devices, 'mobile'), sub: 'of visits from a phone', accent: false },
    ];
  });

  /** Daily unique visitors, scaled for the bar chart. Long ranges are bucketed by week so the chart stays readable. */
  readonly series = computed(() => {
    const r = this.report();
    if (!r) return [];
    const byWeek = r.days > 45;
    const buckets: { key: string; label: string; visitors: number; visits: number }[] = [];
    for (const d of r.daily) {
      const dt = fromISO(d.date);
      const key = byWeek ? weekKey(dt) : d.date;
      let b = buckets.find((x) => x.key === key);
      if (!b) { b = { key, label: byWeek ? 'wk ' + dt.toLocaleDateString('en-US', { month: 'short', day: 'numeric' }) : dt.toLocaleDateString('en-US', { month: 'short', day: 'numeric' }), visitors: 0, visits: 0 }; buckets.push(b); }
      b.visitors += d.visitors; b.visits += d.visits;
    }
    const max = Math.max(1, ...buckets.map((b) => b.visitors));
    return buckets.map((b) => ({ ...b, h: Math.round((b.visitors / max) * 100) }));
  });

  /** Each funnel step with its share of the first step and the drop from the previous one. */
  readonly funnel = computed(() => {
    const r = this.report();
    if (!r) return [];
    const first = r.funnel[0]?.visits ?? 0;
    return r.funnel.map((s, i) => {
      const prev = i === 0 ? s.visits : r.funnel[i - 1].visits;
      return {
        ...s,
        pct: first ? Math.round((s.visits / first) * 100) : 0,
        drop: i === 0 || !prev ? null : prev - s.visits,
      };
    });
  });

  readonly pages = computed(() => (this.report()?.pages ?? []).map((p) => ({ ...p, name: PAGE_NAMES[p.label] ?? p.label })));
  readonly maxPage = computed(() => Math.max(1, ...this.pages().map((p) => p.count)));
  readonly referrers = computed(() => this.report()?.referrers ?? []);
  readonly maxRef = computed(() => Math.max(1, ...this.referrers().map((p) => p.count)));
}

function fmtSeconds(s: number): string {
  if (s < 60) return `${s}s`;
  const m = Math.floor(s / 60), r = s % 60;
  return r ? `${m}m ${r}s` : `${m}m`;
}
function pct(rows: { label: string; count: number }[], label: string): string {
  const total = rows.reduce((a, r) => a + r.count, 0);
  const n = rows.find((r) => r.label === label)?.count ?? 0;
  return total ? `${Math.round((n / total) * 100)}%` : '—';
}
function weekKey(d: Date): string {
  const monday = new Date(d); monday.setDate(d.getDate() - ((d.getDay() + 6) % 7));
  return monday.toISOString().slice(0, 10);
}
