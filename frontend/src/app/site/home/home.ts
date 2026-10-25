import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SiteDataService } from '../../core/site-data.service';
import { PhotoSlot } from '../../shared/photo-slot';

@Component({
  selector: 'app-home',
  imports: [RouterLink, PhotoSlot],
  templateUrl: './home.html',
  styleUrl: './home.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomePage {
  private readonly data = inject(SiteDataService);

  readonly services = computed(() =>
    this.data.sessionTypes().map((s, i) => ({ ...s, dot: i % 2 === 0 ? 'var(--color-accent)' : 'var(--color-accent-2)' })),
  );

  readonly steps = [
    { n: '1', title: 'Request a date', desc: 'Choose your session type, a day and a time that works for you.' },
    { n: '2', title: 'Plan together', desc: 'We confirm availability and talk through location, timing and what to wear.' },
    { n: '3', title: 'Receive your gallery', desc: 'Your edited photos arrive in a private online gallery, ready to download and share.' },
  ];

  constructor() {
    this.data.loadPricing();
  }
}
