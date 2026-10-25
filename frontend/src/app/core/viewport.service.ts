import { Injectable, signal } from '@angular/core';

/** The mock-ups switch to their compact layout below 820px. */
const NARROW_QUERY = '(max-width: 819.98px)';

@Injectable({ providedIn: 'root' })
export class ViewportService {
  readonly narrow = signal(false);

  constructor() {
    if (typeof window === 'undefined' || !window.matchMedia) return;
    const mq = window.matchMedia(NARROW_QUERY);
    this.narrow.set(mq.matches);
    mq.addEventListener('change', (e) => this.narrow.set(e.matches));
  }
}
