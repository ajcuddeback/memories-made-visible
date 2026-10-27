import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { SiteDataService } from '../../core/site-data.service';
import { PhotoSlot } from '../../shared/photo-slot';

interface PortfolioItem {
  id: string;
  key: string;
  cat: string;
  ratio: string;
}

/** Three photo slots per pricing category (pf-<key>-1..3); ratios vary so the masonry staggers. */
const PER_CATEGORY = 3;
const RATIOS = ['3 / 4', '4 / 5', '1 / 1', '3 / 2', '4 / 5', '2 / 3'];

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

  private readonly all = computed<PortfolioItem[]>(() => {
    const out: PortfolioItem[] = [];
    this.shown().forEach((t, ci) => {
      for (let i = 0; i < PER_CATEGORY; i++) {
        out.push({ id: `${t.key}-${i + 1}`, key: t.key, cat: t.shortName, ratio: RATIOS[(ci + i * 2) % RATIOS.length] });
      }
    });
    return out;
  });

  /** "All" interleaves categories instead of listing them in blocks. */
  readonly items = computed(() => {
    const all = this.all();
    if (this.filter() !== 'all') return all.filter((p) => p.key === this.filter());
    const ordered: PortfolioItem[] = [];
    for (let i = 0; i < PER_CATEGORY; i++) all.filter((_, j) => j % PER_CATEGORY === i).forEach((p) => ordered.push(p));
    return ordered;
  });

  constructor() {
    this.data.loadPricing();
  }
}
