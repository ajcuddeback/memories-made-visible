import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { SiteDataService } from '../core/site-data.service';

/**
 * A named photo slot on the public site. Shows the photo the admin uploaded
 * for this slot, else a bundled fallback, else a quiet placeholder — so the
 * layout never jumps while the API loads.
 */
@Component({
  selector: 'app-photo-slot',
  template: `
    @if (src(); as url) {
      <img [src]="url" [alt]="alt()" loading="lazy" decoding="async" />
    } @else {
      <div class="placeholder" aria-hidden="true"><span>{{ placeholder() }}</span></div>
    }
  `,
  styles: `
    :host { display: block; width: 100%; height: 100%; }
    img { width: 100%; height: 100%; object-fit: cover; }
    .placeholder {
      width: 100%; height: 100%; display: grid; place-items: center; padding: 16px; text-align: center;
      font-size: 13px; font-weight: 600; letter-spacing: 0.02em; color: var(--ink-55);
      background:
        radial-gradient(circle at 30% 30%, color-mix(in srgb, var(--color-accent) 14%, transparent), transparent 55%),
        var(--color-neutral-200);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PhotoSlot {
  private readonly data = inject(SiteDataService);

  readonly slot = input.required<string>();
  readonly placeholder = input('Photo');
  readonly fallback = input<string | null>(null);
  readonly alt = input('');

  readonly src = computed(() => this.data.photos()[this.slot()] ?? this.fallback());

  constructor() {
    this.data.loadPhotos();
  }
}
