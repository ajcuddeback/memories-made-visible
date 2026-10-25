import { ChangeDetectionStrategy, Component, computed, signal } from '@angular/core';
import { CATEGORY_SHORT } from '../../core/site-data.service';
import { PhotoSlot } from '../../shared/photo-slot';

interface PortfolioItem {
  id: string;
  key: string;
  cat: string;
  ratio: string;
}

const CATEGORIES = ['weddings', 'families', 'couples', 'maternity', 'seniors', 'events'];
const RATIOS = ['3 / 4', '4 / 5', '1 / 1', '3 / 2', '4 / 5', '2 / 3'];

// Three slots per category; ratios vary so the masonry columns stagger nicely.
const ALL: PortfolioItem[] = [];
CATEGORIES.forEach((c, ci) => {
  for (let i = 0; i < 3; i++) {
    ALL.push({ id: `${c}-${i + 1}`, key: c, cat: CATEGORY_SHORT[c], ratio: RATIOS[(ci + i * 2) % RATIOS.length] });
  }
});
// "All" interleaves categories instead of listing them in blocks.
const ORDERED: PortfolioItem[] = [];
for (let i = 0; i < 3; i++) ALL.filter((_, j) => j % 3 === i).forEach((p) => ORDERED.push(p));

@Component({
  selector: 'app-portfolio',
  imports: [PhotoSlot],
  templateUrl: './portfolio.html',
  styleUrl: './portfolio.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PortfolioPage {
  readonly filter = signal('all');
  readonly filters = [{ key: 'all', label: 'All' }, ...CATEGORIES.map((k) => ({ key: k, label: CATEGORY_SHORT[k] }))];
  readonly items = computed(() => (this.filter() === 'all' ? ORDERED : ALL.filter((p) => p.key === this.filter())));
}
