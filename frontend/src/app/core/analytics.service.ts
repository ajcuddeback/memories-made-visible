import { isPlatformBrowser } from '@angular/common';
import { Injectable, PLATFORM_ID, inject } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';

/**
 * First-party, anonymous analytics for the public site. Sends page views, time on page and
 * booking-funnel steps to our own API in small batches (sendBeacon on page leave). No cookies:
 * a random visitor token lives in localStorage, a visit token in sessionStorage. Nothing is
 * tracked on the studio admin, and "Do Not Track" browsers are left alone.
 */
@Injectable({ providedIn: 'root' })
export class AnalyticsService {
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly router = inject(Router);
  private queue: Record<string, unknown>[] = [];
  private flushTimer: ReturnType<typeof setTimeout> | null = null;
  private currentPath = '';
  private pageStart = 0;
  private visitor = '';
  private session = '';
  private enabled = false;
  private started = false;

  init(): void {
    if (!this.isBrowser || this.started) return;
    this.started = true;
    const dnt = navigator.doNotTrack === '1' || (window as unknown as { doNotTrack?: string }).doNotTrack === '1';
    if (dnt) return;
    try {
      this.visitor = this.token(localStorage, 'mmv.v');
      this.session = this.token(sessionStorage, 'mmv.s');
    } catch {
      return; // storage blocked: don't track at all rather than count every page as a new visitor
    }
    this.enabled = true;
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe((e) => this.pageChanged((e as NavigationEnd).urlAfterRedirects));
    this.pageChanged(this.router.url);
    document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'hidden') this.leave(true); });
    window.addEventListener('pagehide', () => this.leave(true));
  }

  /** A booking-funnel step or other named event on the current page. */
  track(kind: 'book_view' | 'book_type' | 'book_slot' | 'book_details' | 'book_submit' | 'contact_click'): void {
    if (!this.enabled) return;
    this.push({ kind, path: this.currentPath });
  }

  private pageChanged(url: string): void {
    if (this.currentPath) this.leave(false);
    const path = url.split('?')[0].split('#')[0] || '/';
    this.currentPath = path;
    this.pageStart = Date.now();
    if (path.startsWith('/studio')) return;
    this.push({ kind: 'view', path, referrer: document.referrer || null });
  }

  /** Time spent on the page that's being left. On tab hide the clock restarts so it isn't double counted. */
  private leave(beacon: boolean): void {
    if (!this.currentPath || this.currentPath.startsWith('/studio')) return;
    const ms = Date.now() - this.pageStart;
    this.pageStart = Date.now();
    if (ms < 500) return;
    this.push({ kind: 'leave', path: this.currentPath, durationMs: ms });
    if (beacon) this.flush(true);
  }

  private push(e: Record<string, unknown>): void {
    this.queue.push({ visitor: this.visitor, session: this.session, device: window.innerWidth < 820 ? 'mobile' : 'desktop', ...e });
    if (!this.flushTimer) this.flushTimer = setTimeout(() => this.flush(false), 2500);
  }

  private flush(beacon: boolean): void {
    if (this.flushTimer) { clearTimeout(this.flushTimer); this.flushTimer = null; }
    if (!this.queue.length) return;
    const body = JSON.stringify({ events: this.queue.splice(0, 50) });
    try {
      if (beacon && navigator.sendBeacon) {
        navigator.sendBeacon('/api/analytics', new Blob([body], { type: 'text/plain' }));
      } else {
        fetch('/api/analytics', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body, keepalive: true }).catch(() => undefined);
      }
    } catch { /* analytics never breaks the page */ }
  }

  private token(store: Storage, key: string): string {
    let t = store.getItem(key);
    if (!t) {
      t = (crypto.randomUUID ? crypto.randomUUID() : Math.random().toString(36).slice(2) + Date.now().toString(36)).replace(/-/g, '').slice(0, 32);
      store.setItem(key, t);
    }
    return t;
  }
}
