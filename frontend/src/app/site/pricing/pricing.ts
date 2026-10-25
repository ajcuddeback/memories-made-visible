import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
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
  readonly services = computed(() =>
    this.data.sessionTypes().map((s, i) => ({ ...s, tint: i % 2 === 0 ? 'var(--color-accent-200)' : 'var(--color-accent-2-200)' })),
  );

  constructor() {
    this.data.loadPricing();
  }
}
