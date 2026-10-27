import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { MON, fromISO } from '../../core/dates';
import { SiteDataService } from '../../core/site-data.service';
import { PhotoSlot } from '../../shared/photo-slot';

@Component({
  selector: 'app-events',
  imports: [PhotoSlot],
  templateUrl: './events.html',
  styleUrl: './events.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EventsPage {
  mapUrl(address: string): string { return 'https://www.google.com/maps/search/?api=1&query=' + encodeURIComponent(address); }
  private readonly data = inject(SiteDataService);
  private readonly router = inject(Router);

  readonly upcoming = computed(() =>
    this.data.upcoming().map((e) => {
      const d = fromISO(e.date);
      return { ...e, mon: MON[d.getMonth()], day: String(d.getDate()) };
    }),
  );

  readonly steps = [
    { n: '1', title: 'Find us at a market', desc: 'Check the schedule below for where we’ll be set up next.' },
    { n: '2', title: 'Step into the set', desc: 'Each pop-up has its own themed backdrop and props. We handle the posing and lighting.' },
    { n: '3', title: 'Take your prints home', desc: 'Pick your favorites and we print them on site while you wait.' },
  ];

  constructor() {
    this.data.loadUpcoming();
  }

  inviteUs(): void {
    this.router.navigate(['/book'], { queryParams: { type: 'events' } });
  }
}
