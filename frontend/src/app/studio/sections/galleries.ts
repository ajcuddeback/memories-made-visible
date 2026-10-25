import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { fmtDate, fromISO, today } from '../../core/dates';
import { AdminStore } from '../admin-store.service';

const STEPS = ['Shot', 'Editing', 'Proofing', 'Delivered'];

@Component({
  selector: 'app-galleries',
  templateUrl: './galleries.html',
  styleUrl: './galleries.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GalleriesSection {
  readonly store = inject(AdminStore);
  private readonly today = today();

  readonly newGal = signal<number | null>(null);

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
          steps: STEPS.map((label, i) => ({ label, done: i <= g.stage, current: i === g.stage, delivered: g.stage === 3 })),
          backOff: g.stage === 0,
          nextOff: g.stage === 3,
          nextLabel: g.stage === 3 ? 'Delivered' : 'Mark ' + STEPS[g.stage + 1].toLowerCase(),
        };
      })
      .sort((a, b) => Number(a.nextOff) - Number(b.nextOff));
  });

  /** Confirmed sessions that have already happened and don't have a gallery yet. */
  readonly options = computed(() => {
    const has = new Set(this.store.galleries().map((g) => g.bookingId));
    return this.store.confirmed()
      .filter((b) => fromISO(b.date) <= this.today && !has.has(b.id))
      .map((b) => ({ id: b.id, label: `${b.name} · ${fmtDate(b.date)}` }));
  });

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
  next(id: number, stage: number): void { this.store.setStage(id, Math.min(3, stage + 1)); }
}
