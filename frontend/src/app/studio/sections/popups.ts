import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MON, fromISO, money, num, today } from '../../core/dates';
import { AdminStore } from '../admin-store.service';

@Component({
  selector: 'app-popups',
  templateUrl: './popups.html',
  styleUrl: './popups.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PopupsSection {
  readonly store = inject(AdminStore);
  private readonly today = today();

  readonly ne = signal({ date: '', title: '', theme: '', venue: '', time: '' });
  readonly cantAdd = computed(() => !this.ne().date || !this.ne().title.trim());

  readonly popups = computed(() =>
    [...this.store.events()].sort((a, b) => a.date.localeCompare(b.date)).map((e) => {
      const d = fromISO(e.date);
      const past = d < this.today;
      return {
        ...e,
        mon: MON[d.getMonth()],
        day: String(d.getDate()),
        past,
        status: past ? 'Past' : 'Upcoming',
        revenue: money(e.prints * num(e.printPrice)),
      };
    }),
  );

  set(key: 'date' | 'title' | 'theme' | 'venue' | 'time', e: Event): void {
    const v = (e.target as HTMLInputElement).value;
    this.ne.update((s) => ({ ...s, [key]: v }));
  }

  add(): void {
    if (this.cantAdd()) return;
    const n = this.ne();
    this.store.addEvent({ date: n.date, title: n.title.trim(), theme: n.theme.trim(), venue: n.venue.trim(), time: n.time.trim() });
    this.ne.set({ date: '', title: '', theme: '', venue: '', time: '' });
  }

  inc(id: number, prints: number): void { this.store.setPrints(id, { prints: prints + 1 }); }
  dec(id: number, prints: number): void { this.store.setPrints(id, { prints: Math.max(0, prints - 1) }); }
  price(id: number, e: Event): void { this.store.setPrints(id, { printPrice: num((e.target as HTMLInputElement).value) }); }
  remove(id: number, title: string): void {
    if (confirm(`Remove "${title}"? Its print sales will be gone too.`)) this.store.removeEvent(id);
  }
}
