import { Injectable } from '@angular/core';
import manifest from '../../assets/images/manifest/portfolio.json';

export interface PhotoEntry {
  id: string;
  title?: string;
  alt?: string;
  category?: string;
  url: string;
  thumbUrl?: string;
}

@Injectable({ providedIn: 'root' })
export class ImageService {
  // Normalize manifest paths so they're absolute (start with '/') which
  // ensures browser resolves them from the app root instead of the
  // current route (e.g. /portfolio/assets/... vs /assets/...).
  private readonly photos: PhotoEntry[] = (manifest as PhotoEntry[]).map((p) => {
    const url = p.url && (p.url.startsWith('/') || p.url.startsWith('http')) ? p.url : '/' + p.url;
    let thumbUrl: string | undefined = undefined;
    if (p.thumbUrl) {
      thumbUrl = p.thumbUrl.startsWith('/') || p.thumbUrl.startsWith('http') ? p.thumbUrl : '/' + p.thumbUrl;
    }
    return { ...p, url, thumbUrl };
  });

  getAll() {
    return this.photos;
  }

  getFeatured(limit = 6) {
    return this.photos.slice(0, limit);
  }

  // helper for debugging — returns the internal normalized list
  debug() {
    return this.photos;
  }
}
