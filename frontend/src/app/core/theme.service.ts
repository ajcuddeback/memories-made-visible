import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { Injectable, PLATFORM_ID, computed, effect, inject, signal } from '@angular/core';

export type Theme = 'dark' | 'light';
const KEY = 'mmv-theme';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly doc = inject(DOCUMENT);
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  readonly theme = signal<Theme>(this.read());
  readonly isDark = computed(() => this.theme() === 'dark');

  constructor() {
    effect(() => {
      const t = this.theme();
      this.doc.documentElement.setAttribute('data-theme', t);
      this.doc.querySelector('meta[name="theme-color"]')?.setAttribute('content', t === 'dark' ? '#0f1012' : '#fafafa');
      if (!this.isBrowser) return;
      try {
        localStorage.setItem(KEY, t);
      } catch {
        /* private mode — theme just won't persist */
      }
    });
  }

  toggle(): void {
    this.theme.update((t) => (t === 'dark' ? 'light' : 'dark'));
  }

  private read(): Theme {
    if (!this.isBrowser) return 'dark'; // prerendered HTML is dark; the inline <head> script fixes it up pre-paint
    try {
      const t = localStorage.getItem(KEY);
      if (t === 'light' || t === 'dark') return t;
    } catch {
      /* ignore */
    }
    return 'dark';
  }
}
