import { ChangeDetectionStrategy, Component, effect, inject, signal } from '@angular/core';
import { PricingTier, SessionType } from '../../core/models';
import { AdminStore } from '../admin-store.service';
import { Icon } from '../../shared/icons';

interface Draft {
  key: string;
  name: string;
  from: string;
  tiers: PricingTier[];
}

@Component({
  selector: 'app-pricing-admin',
  imports: [Icon],
  templateUrl: './pricing-admin.html',
  styleUrl: './pricing-admin.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PricingAdminSection {
  readonly store = inject(AdminStore);
  readonly draft = signal<Draft[]>([]);
  readonly msg = signal('');
  readonly busy = signal(false);
  private dirty = false;

  constructor() {
    // Seed the draft from the store once, and again after a reset — never over unsaved edits.
    effect(() => {
      const list = this.store.pricing();
      if (!this.dirty && list.length) this.draft.set(this.toDraft(list));
    });
  }

  private toDraft(list: SessionType[]): Draft[] {
    return list.map((s) => ({ key: s.key, name: s.name, from: s.from, tiers: s.tiers.map((t) => ({ ...t })) }));
  }

  private edit(fn: (d: Draft[]) => void): void {
    this.draft.update((d) => {
      const copy = this.toDraft(d as unknown as SessionType[]).map((x, i) => ({ ...x, name: d[i].name }));
      fn(copy);
      return copy;
    });
    this.dirty = true;
    this.msg.set('Unsaved changes');
  }

  val(e: Event): string {
    return (e.target as HTMLInputElement).value;
  }

  setFrom(i: number, e: Event): void { const v = this.val(e); this.edit((d) => { d[i].from = v; }); }
  setTier(i: number, ti: number, field: keyof PricingTier, e: Event): void {
    const v = this.val(e);
    this.edit((d) => { d[i].tiers[ti][field] = v; });
  }
  addTier(i: number): void { this.edit((d) => { d[i].tiers.push({ name: 'New package', detail: '', price: '$' }); }); }
  removeTier(i: number, ti: number): void { this.edit((d) => { d[i].tiers.splice(ti, 1); }); }

  save(): void {
    if (this.busy()) return;
    const bad = this.draft().find((d) => !d.from.trim() || d.tiers.some((t) => !t.name.trim() || !t.price.trim()));
    if (bad) {
      this.msg.set(`Every package in "${bad.name}" needs a name and a price.`);
      return;
    }
    this.busy.set(true);
    this.store.savePricing(this.draft().map((d) => ({ key: d.key, from: d.from, tiers: d.tiers }))).subscribe({
      next: (list) => {
        this.busy.set(false);
        this.dirty = false;
        this.store.applyPricing(list);
        this.msg.set('Saved — the Pricing page is updated.');
      },
      error: (err) => {
        this.busy.set(false);
        this.msg.set(err?.error?.message ?? 'Couldn’t save. Please try again.');
      },
    });
  }

  reset(): void {
    if (this.busy() || !confirm('Reset every price and package to the original launch list?')) return;
    this.busy.set(true);
    this.store.resetPricing().subscribe({
      next: (list) => {
        this.busy.set(false);
        this.dirty = false;
        this.store.applyPricing(list);
        this.msg.set('Reset to the original prices.');
      },
      error: () => {
        this.busy.set(false);
        this.msg.set('Couldn’t reset. Please try again.');
      },
    });
  }
}
