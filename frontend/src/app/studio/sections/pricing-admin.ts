import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { SessionType } from '../../core/models';
import { AdminStore } from '../admin-store.service';
import { Icon } from '../../shared/icons';
import { fmtDuration } from '../../core/dates';

interface DraftTier {
  name: string;
  detail: string;
  price: string;
  durationMinutes: number;
  /** Edited as one item per line; split on save. */
  includesText: string;
}

interface Draft {
  key: string;
  name: string;
  shortName: string;
  description: string;
  from: string;
  showInPortfolio: boolean;
  durationMinutes: number;
  tiers: DraftTier[];
}

/** 30-minute steps up to 8 hours, for the "session length" picker. */
export const DURATIONS = [15, 30, 45, ...Array.from({ length: 23 }, (_, i) => (i + 2) * 30)];

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
  readonly dirty = signal(false);

  // "Add a category" form
  readonly nc = signal({ name: '', shortName: '', description: '', from: '', showInPortfolio: true, durationMinutes: 60 });
  readonly durations = DURATIONS;
  readonly fmtDuration = fmtDuration;
  readonly cantAdd = computed(() => !this.nc().name.trim() || !this.nc().from.trim());

  /** How many bookings reference each category — shown before a delete so nobody is surprised. */
  readonly bookingsByCategory = computed(() => {
    const m = new Map<string, number>();
    for (const b of this.store.bookings()) m.set(b.sessionType, (m.get(b.sessionType) ?? 0) + 1);
    return m;
  });

  constructor() {
    // Seed the draft from the store once, and again after a save/reset/add — never over unsaved edits.
    effect(() => {
      const list = this.store.pricing();
      if (!this.dirty() && list.length) this.draft.set(this.toDraft(list));
    });
  }

  private toDraft(list: SessionType[]): Draft[] {
    return list.map((s) => ({
      key: s.key, name: s.name, shortName: s.shortName === s.name ? '' : s.shortName, description: s.description, from: s.from,
      showInPortfolio: s.showInPortfolio,
      durationMinutes: s.durationMinutes || 60,
      tiers: s.tiers.map((t) => ({ name: t.name, detail: t.detail, price: t.price, durationMinutes: t.durationMinutes || 60, includesText: (t.includes ?? []).join('\n') })),
    }));
  }

  private toPayload(d: Draft[]): SessionType[] {
    return d.map((c) => ({
      key: c.key, name: c.name, shortName: c.shortName, description: c.description, from: c.from,
      showInPortfolio: c.showInPortfolio,
      durationMinutes: c.durationMinutes,
      tiers: c.tiers.map((t) => ({ name: t.name, detail: t.detail, price: t.price, durationMinutes: t.durationMinutes,
        includes: t.includesText.split(/\r?\n/).map((s) => s.trim()).filter(Boolean) })),
    }));
  }

  private edit(fn: (d: Draft[]) => void): void {
    this.draft.update((d) => { const copy = structuredClone(d); fn(copy); return copy; });
    this.dirty.set(true);
    this.msg.set('Unsaved changes');
  }

  val(e: Event): string { return (e.target as HTMLInputElement | HTMLTextAreaElement).value; }

  // category fields
  setCat(i: number, field: 'name' | 'shortName' | 'description' | 'from', e: Event): void {
    const v = this.val(e); this.edit((d) => { d[i][field] = v; });
  }
  setDuration(i: number, e: Event): void {
    const v = Number((e.target as HTMLSelectElement).value); this.edit((d) => { d[i].durationMinutes = v; });
  }
  setNcDuration(e: Event): void { const v = Number((e.target as HTMLSelectElement).value); this.nc.update((n) => ({ ...n, durationMinutes: v })); }
  setPortfolio(i: number, on: boolean): void { this.edit((d) => { d[i].showInPortfolio = on; }); }
  move(i: number, dir: -1 | 1): void {
    this.edit((d) => { const j = i + dir; if (j < 0 || j >= d.length) return; [d[i], d[j]] = [d[j], d[i]]; });
  }
  // package fields
  setTier(i: number, ti: number, field: 'name' | 'detail' | 'price' | 'includesText', e: Event): void {
    const v = this.val(e); this.edit((d) => { d[i].tiers[ti][field] = v; });
  }
  addTier(i: number): void { this.edit((d) => { d[i].tiers.push({ name: 'New package', detail: '', price: '$', durationMinutes: 60, includesText: '' }); }); }
  setTierDuration(i: number, ti: number, e: Event): void {
    const v = Number((e.target as HTMLSelectElement).value); this.edit((d) => { d[i].tiers[ti].durationMinutes = v; });
  }
  removeTier(i: number, ti: number): void { this.edit((d) => { d[i].tiers.splice(ti, 1); }); }

  setNc(field: 'name' | 'shortName' | 'description' | 'from', e: Event): void {
    const v = this.val(e); this.nc.update((n) => ({ ...n, [field]: v }));
  }
  setNcPortfolio(on: boolean): void { this.nc.update((n) => ({ ...n, showInPortfolio: on })); }

  /** Categories are created server-side (the key is a slug of the name) — unsaved edits are kept. */
  addCategory(): void {
    if (this.cantAdd() || this.busy()) return;
    const n = this.nc();
    this.busy.set(true);
    this.store.createCategory({ name: n.name.trim(), shortName: n.shortName.trim(), description: n.description.trim(), from: n.from.trim(), showInPortfolio: n.showInPortfolio, durationMinutes: n.durationMinutes }).subscribe({
      next: (list) => {
        this.busy.set(false);
        this.nc.set({ name: '', shortName: '', description: '', from: '', showInPortfolio: true, durationMinutes: 60 });
        const added = list.find((s) => !this.draft().some((d) => d.key === s.key));
        if (added) this.draft.update((d) => [...d, ...this.toDraft([added])]);
        this.store.applyPricing(list);
        this.msg.set(`Added "${n.name.trim()}" to the draft${n.showInPortfolio ? ' (three Portfolio photo slots are waiting under Photos)' : ''}. Publish to make it live.`);
      },
      error: (err) => { this.busy.set(false); this.msg.set(err?.error?.message ?? 'Couldn’t add that category.'); },
    });
  }

  removeCategory(i: number): void {
    const c = this.draft()[i];
    const n = this.bookingsByCategory().get(c.key) ?? 0;
    const warn = n ? ` ${n} booking${n === 1 ? '' : 's'} reference it; they keep their records but will show the key "${c.key}".` : '';
    if (!confirm(`Remove "${c.name}" and its packages from the pricing page?${warn}`)) return;
    this.busy.set(true);
    this.store.deleteCategory(c.key).subscribe({
      next: (list) => {
        this.busy.set(false);
        this.draft.update((d) => d.filter((x) => x.key !== c.key));
        this.store.applyPricing(list);
        this.msg.set(`Removed "${c.name}" from the draft. Publish to take it off the site.`);
      },
      error: () => { this.busy.set(false); this.msg.set('Couldn’t remove that category.'); },
    });
  }

  save(): void {
    if (this.busy()) return;
    const bad = this.draft().find((d) => !d.name.trim() || !d.from.trim() || d.tiers.some((t) => !t.name.trim() || !t.price.trim()));
    if (bad) {
      this.msg.set(`"${bad.name || 'A category'}" needs a name, a "from" price, and a name and price on every package.`);
      return;
    }
    this.busy.set(true);
    this.store.savePricing(this.toPayload(this.draft())).subscribe({
      next: (list) => { this.busy.set(false); this.dirty.set(false); this.store.applyPricing(list); this.msg.set('Saved to the draft. Press Publish (top of the page) when you want it live.'); },
      error: (err) => { this.busy.set(false); this.msg.set(err?.error?.message ?? 'Couldn’t save. Please try again.'); },
    });
  }

  reset(): void {
    if (this.busy() || !confirm('Reset to the original launch price list? Any categories you added will be removed.')) return;
    this.busy.set(true);
    this.store.resetPricing().subscribe({
      next: (list) => { this.busy.set(false); this.dirty.set(false); this.store.applyPricing(list); this.msg.set('Draft reset to the original prices. Publish to make it live.'); },
      error: () => { this.busy.set(false); this.msg.set('Couldn’t reset. Please try again.'); },
    });
  }
}
