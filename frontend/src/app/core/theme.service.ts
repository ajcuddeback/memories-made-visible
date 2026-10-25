import { DOCUMENT } from '@angular/common';
import { Injectable, computed, effect, inject, signal } from '@angular/core';

export type Theme = 'dark' | 'light';
const KEY = 'mmv-theme';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly doc = inject(DOCUMENT);
  readonly theme = signal<Theme>(this.read());
  readonly isDark = computed(() => this.theme() === 'dark');

  constructor() {
    effect(() => {
      const t = this.theme();
      this.doc.documentElement.setAttribute('data-theme', t);
      this.doc.querySelector('meta[name="theme-color"]')?.setAttribute('content', t === 'dark' ? '#0f1012' : '#fafafa');
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
    try {
      const t = localStorage.getItem(KEY);
      if (t === 'light' || t === 'dark') return t;
    } catch {
      /* ignore */
    }
    return 'dark';
  }
}
