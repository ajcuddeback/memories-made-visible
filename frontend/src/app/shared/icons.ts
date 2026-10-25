import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** The handful of stroke icons the mock-ups use, inlined so there's no icon dependency. */
@Component({
  selector: 'app-icon',
  template: `
    <svg [attr.width]="size()" [attr.height]="size()" viewBox="0 0 24 24" fill="none" stroke="currentColor"
         stroke-width="2.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
      @switch (name()) {
        @case ('sun') {
          <circle cx="12" cy="12" r="4" />
          <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41" />
        }
        @case ('moon') { <path d="M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z" /> }
        @case ('menu') { <path d="M4 6h16M4 12h16M4 18h16" /> }
        @case ('close') { <path d="M18 6 6 18M6 6l12 12" /> }
        @case ('prev') { <path d="m15 18-6-6 6-6" /> }
        @case ('next') { <path d="m9 18 6-6-6-6" /> }
        @case ('check') { <path d="M20 6 9 17l-5-5" /> }
        @case ('upload') { <path d="M12 16V4m0 0 4 4m-4-4-4 4M4 16v3a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-3" /> }
      }
    </svg>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Icon {
  readonly name = input.required<'sun' | 'moon' | 'menu' | 'close' | 'prev' | 'next' | 'check' | 'upload'>();
  readonly size = input(20);
}
