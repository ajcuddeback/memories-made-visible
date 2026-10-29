import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { SiteDataService } from '../../core/site-data.service';
import { PhotoSlot } from '../../shared/photo-slot';

interface PortfolioItem {
  id: string;
  key: string;
  cat: string;
  ratio: string;
}

/** Before any photos are uploaded, a category shows this many placeholders so the page isn't empty. */
const PLACEHOLDERS = 3;
const RATIOS = ['3 / 4', '4 / 5', '1 / 1', '3 / 2', '4 / 5', '2 / 3'];
const SLOT = /^pf-(.+)-(\d+)$/;

@Component({
  selector: 'app-portfolio',
  imports: [PhotoSlot],
  templateUrl: './portfolio.html',
  styleUrl: './portfolio.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PortfolioPage {
  private readonly data = inject(SiteDataService);
  readonly filter = signal('all');

  private readonly shown = computed(() => this.data.sessionTypes().filter((t) => t.showInPortfolio));

  readonly filters = computed(() => [
    { key: 'all', label: 'All' },
    ...this.shown().map((t) => ({ key: t.key, label: t.shortName })),
  ]);

  /** Every uploaded portfolio photo, grouped by category in slot order; categories with none get placeholders. */
  private readonly byCategory = computed<PortfolioItem[][]>(() => {
    const uploaded = new Map<string, number[]>();
    for (const id of Object.keys(this.data.photos())) {
      const m = SLOT.exec(id);
      if (m) uploaded.set(m[1], [...(uploaded.get(m[1]) ?? []), Number(m[2])]);
    }
    return this.shown().map((t, ci) => {
      const ns = (uploaded.get(t.key) ?? []).sort((a, b) => a - b);
      const list = ns.length ? ns : Array.from({ length: PLACEHOLDERS }, (_, i) => i + 1);
      return list.map((n, i) => ({ id: `${t.key}-${n}`, key: t.key, cat: t.shortName, ratio: RATIOS[(ci + i * 2) % RATIOS.length] }));
    });
  });

  /** "All" interleaves categories round-robin instead of listing them in blocks. */
  readonly items = computed(() => {
    const groups = this.byCategory();
    if (this.filter() !== 'all') return groups.flat().filter((p) => p.key === this.filter());
    const out: PortfolioItem[] = [];
    const longest = Math.max(0, ...groups.map((g) => g.length));
    for (let i = 0; i < longest; i++) for (const g of groups) if (g[i]) out.push(g[i]);
    return out;
  });

  constructor() {
    this.data.loadPricing();
    this.data.loadPhotos();
  }
}
