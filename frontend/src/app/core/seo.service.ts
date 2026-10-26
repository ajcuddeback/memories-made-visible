import { DOCUMENT } from '@angular/common';
import { Injectable, inject } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';
import { SITE } from './site';

export interface SeoData {
  title: string;
  description: string;
  /** Route-specific share image; falls back to the site default. */
  image?: string;
  noindex?: boolean;
}

/**
 * Keeps <title>, meta description, canonical and Open Graph/Twitter tags in
 * step with the active route (read from the deepest route's `data.seo`).
 * Runs during prerender too, so the static HTML carries the right tags.
 */
@Injectable({ providedIn: 'root' })
export class SeoService {
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly title = inject(Title);
  private readonly meta = inject(Meta);
  private readonly doc = inject(DOCUMENT);

  init(): void {
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe((e) => {
      let r = this.route;
      while (r.firstChild) r = r.firstChild;
      const seo = r.snapshot.data['seo'] as SeoData | undefined;
      const path = (e as NavigationEnd).urlAfterRedirects.split('?')[0].split('#')[0];
      this.apply(seo ?? { title: SITE.name, description: '' }, path);
    });
  }

  private apply(seo: SeoData, path: string): void {
    const url = SITE.url + (path === '/' ? '/' : path.replace(/\/+$/, ''));
    const image = SITE.url + (seo.image ?? SITE.ogImage);

    this.title.setTitle(seo.title);
    this.tag('name', 'description', seo.description);
    this.tag('name', 'robots', seo.noindex ? 'noindex, nofollow' : 'index, follow');
    this.tag('property', 'og:type', 'website');
    this.tag('property', 'og:site_name', SITE.name);
    this.tag('property', 'og:title', seo.title);
    this.tag('property', 'og:description', seo.description);
    this.tag('property', 'og:url', url);
    this.tag('property', 'og:image', image);
    this.tag('property', 'og:locale', 'en_US');
    this.tag('name', 'twitter:card', 'summary_large_image');
    this.tag('name', 'twitter:title', seo.title);
    this.tag('name', 'twitter:description', seo.description);
    this.tag('name', 'twitter:image', image);
    this.canonical(seo.noindex ? null : url);
  }

  private tag(attr: 'name' | 'property', key: string, content: string): void {
    this.meta.updateTag({ [attr]: key, content }, `${attr}='${key}'`);
  }

  private canonical(url: string | null): void {
    let link = this.doc.head.querySelector<HTMLLinkElement>('link[rel="canonical"]');
    if (!url) {
      link?.remove();
      return;
    }
    if (!link) {
      link = this.doc.createElement('link');
      link.rel = 'canonical';
      this.doc.head.appendChild(link);
    }
    link.href = url;
  }
}
