import { RenderMode, ServerRoute } from '@angular/ssr';

/**
 * Public pages are prerendered to static HTML at build time (full content for
 * crawlers, instant first paint). The studio admin is client-only: it needs a
 * signed-in session and must never be in the crawlable output.
 */
export const serverRoutes: ServerRoute[] = [
  { path: 'studio', renderMode: RenderMode.Client },
  { path: 'studio/**', renderMode: RenderMode.Client },
  { path: '**', renderMode: RenderMode.Prerender },
];
