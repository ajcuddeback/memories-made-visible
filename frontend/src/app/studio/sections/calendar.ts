import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MONTHS, WEEKDAYS, toISO, today } from '../../core/dates';
import { ViewportService } from '../../core/viewport.service';
import { Icon } from '../../shared/icons';
import { AdminStore } from '../admin-store.service';

interface CalItem {
  text: string;
  full: string;
  kind: 'session' | 'popup';
}
interface CalDay {
  label: string;
  isToday: boolean;
  items: CalItem[];
}

@Component({
  selector: 'app-calendar',
  imports: [Icon],
  templateUrl: './calendar.html',
  styleUrl: './calendar.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CalendarSection {
  readonly store = inject(AdminStore);
  readonly viewport = inject(ViewportService);
  readonly weekdays = WEEKDAYS;
  private readonly today = today();

  readonly cy = signal(this.today.getFullYear());
  readonly cm = signal(this.today.getMonth());
  readonly label = computed(() => `${MONTHS[this.cm()]} ${this.cy()}`);

  readonly days = computed<CalDay[]>(() => {
    const y = this.cy(), m = this.cm();
    const first = new Date(y, m, 1).getDay();
    const count = new Date(y, m + 1, 0).getDate();
    const names = this.store.typeName();
    const todayIso = toISO(this.today);
    const out: CalDay[] = [];
    for (let i = 0; i < first; i++) out.push({ label: '', isToday: false, items: [] });
    for (let d = 1; d <= count; d++) {
      const key = toISO(new Date(y, m, d));
      const items: CalItem[] = [
        ...this.store.confirmed().filter((b) => b.date === key)
          .map((b) => ({ text: b.name, full: `${b.name} · ${names.get(b.sessionType) ?? b.sessionTypeName} · ${b.time}`, kind: 'session' as const })),
        ...this.store.events().filter((e) => e.date === key)
          .map((e) => ({ text: e.title, full: `${e.title} · ${e.venue}`, kind: 'popup' as const })),
      ];
      out.push({ label: String(d), isToday: key === todayIso, items });
    }
    return out;
  });

  readonly agenda = computed(() =>
    this.days()
      .filter((d) => d.items.length)
      .map((d) => ({ ...d, wd: WEEKDAYS[new Date(this.cy(), this.cm(), Number(d.label)).getDay()] })),
  );

  prev(): void {
    if (this.cm() === 0) { this.cm.set(11); this.cy.update((y) => y - 1); } else this.cm.update((m) => m - 1);
  }
  next(): void {
    if (this.cm() === 11) { this.cm.set(0); this.cy.update((y) => y + 1); } else this.cm.update((m) => m + 1);
  }
}
