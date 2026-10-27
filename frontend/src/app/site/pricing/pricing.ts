import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SiteDataService } from '../../core/site-data.service';

@Component({
  selector: 'app-pricing',
  imports: [RouterLink],
  templateUrl: './pricing.html',
  styleUrl: './pricing.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PricingPage {
  private readonly data = inject(SiteDataService);

  readonly error = this.data.pricingError;
  /** Which packages have their "what's included" list open, keyed "category:index". */
  private readonly open = signal<Set<string>>(new Set());

  isOpen(key: string, i: number): boolean { return this.open().has(`${key}:${i}`); }
  toggle(key: string, i: number): void {
    this.open.update((s) => { const n = new Set(s); const k = `${key}:${i}`; n.has(k) ? n.delete(k) : n.add(k); return n; });
  }
  readonly skeletons = [0, 1, 2, 3, 4, 5];
  readonly services = computed(() =>
    this.data.sessionTypes().map((s, i) => ({ ...s, tint: i % 2 === 0 ? 'var(--color-accent-200)' : 'var(--color-accent-2-200)' })),
  );

  constructor() {
    this.data.loadPricing();
  }
}
