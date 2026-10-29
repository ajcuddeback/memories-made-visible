import { HttpClient } from '@angular/common/http';
import { isPlatformBrowser } from '@angular/common';
import { Injectable, PLATFORM_ID, computed, inject, signal } from '@angular/core';
import { catchError, of } from 'rxjs';
import { BookingRequest, Booking, ContactInfo, DaySlots, Photographer, PhotoSrc, PublicPopupEvent, SessionType } from './models';

/**
 * Public-site data: price list, upcoming pop-ups and the slot → photo URL map.
 * Everything comes from the API (which the studio admin edits); each list is
 * fetched once per page session and cached in a signal. Fetches only happen
 * in the browser — the prerendered HTML is the static shell, data hydrates in.
 */
@Injectable({ providedIn: 'root' })
export class SiteDataService {
  private readonly http = inject(HttpClient);
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));

  readonly sessionTypes = signal<SessionType[]>([]);
  readonly pricingError = signal(false);
  readonly upcoming = signal<PublicPopupEvent[]>([]);
  readonly photos = signal<Record<string, PhotoSrc>>({});
  /** Until the API answers, the prerendered page shows the studio's known address. */
  readonly contact = signal<ContactInfo>({ email: 'hello@memoriesmadevisible.com', phone: null, instagram: null, location: 'Winter Haven, FL · Serving all of Polk County' });
  private loadedContact = false;
  readonly typeByKey = computed(() => new Map(this.sessionTypes().map((t) => [t.key, t])));

  private loadedPricing = false;
  private loadedEvents = false;
  private loadedPhotos = false;

  /**
   * index.html starts the page's API requests before the app bundle has even downloaded; each loader takes
   * that response if it is there (once) rather than asking again.
   */
  private early<T>(name: 'pricing' | 'contact' | 'photos' | 'upcoming'): Promise<T> | null {
    const w = window as unknown as { __mmvEarly?: Record<string, Promise<T> | undefined> };
    const p = w.__mmvEarly?.[name];
    if (p && w.__mmvEarly) delete w.__mmvEarly[name];
    return p ?? null;
  }

  loadPricing(): void {
    if (!this.isBrowser || this.loadedPricing) return;
    this.loadedPricing = true;
    const early = this.early<SessionType[] | null>('pricing');
    if (early) {
      early.then((list) => { if (list) this.sessionTypes.set(list); else this.fetchPricing(); }, () => this.fetchPricing());
      return;
    }
    this.fetchPricing();
  }

  private fetchPricing(): void {
    this.http.get<SessionType[]>('/api/pricing').subscribe({
      next: (list) => this.sessionTypes.set(list),
      error: () => {
        this.pricingError.set(true);
        this.loadedPricing = false; // let the next page visit retry
      },
    });
  }

  loadUpcoming(): void {
    if (!this.isBrowser || this.loadedEvents) return;
    this.loadedEvents = true;
    const early = this.early<PublicPopupEvent[]>('upcoming');
    if (early) { early.then((l) => this.upcoming.set(l ?? []), () => this.upcoming.set([])); return; }
    this.http.get<PublicPopupEvent[]>('/api/events/upcoming').pipe(catchError(() => of([] as PublicPopupEvent[]))).subscribe((list) => this.upcoming.set(list));
  }

  loadContact(): void {
    if (!this.isBrowser || this.loadedContact) return;
    this.loadedContact = true;
    const early = this.early<ContactInfo | null>('contact');
    if (early) { early.then((c) => { if (c) this.contact.set(c); }, () => undefined); return; }
    this.http.get<ContactInfo>('/api/site/contact').pipe(catchError(() => of(null))).subscribe((c) => { if (c) this.contact.set(c); });
  }

  loadPhotos(): void {
    if (!this.isBrowser || this.loadedPhotos) return;
    this.loadedPhotos = true;
    const early = this.early<Record<string, PhotoSrc>>('photos');
    if (early) { early.then((map) => this.photos.set(map ?? {}), () => this.fetchPhotos()); return; }
    this.fetchPhotos();
  }

  private fetchPhotos(): void {
    this.http.get<Record<string, PhotoSrc>>('/api/photos').pipe(catchError(() => of({}))).subscribe((map) => this.photos.set(map));
  }

  /** After a publish: forget the cached lists so the next page visit refetches what's live. */
  invalidate(): void {
    this.loadedPricing = this.loadedEvents = this.loadedPhotos = false;
    this.sessionTypes.set([]);
    this.upcoming.set([]);
    this.photos.set({});
  }

  /** Open start times per day in [from, to] for a session type (not cached: it changes as people book). */
  readonly photographers = signal<Photographer[]>([]);
  private loadedPhotographers = false;
  loadPhotographers(): void {
    if (!this.isBrowser || this.loadedPhotographers) return;
    this.loadedPhotographers = true;
    this.http.get<Photographer[]>('/api/photographers').pipe(catchError(() => of([] as Photographer[]))).subscribe((l) => this.photographers.set(l));
  }

  availability(from: string, to: string, type: string | null, pkg: string | null, hours: number | null, photographer: string | null = null) {
    const params: Record<string, string> = { from, to };
    if (type) params['type'] = type;
    if (pkg) params['package'] = pkg;
    if (hours) params['hours'] = String(hours);
    if (photographer) params['photographer'] = photographer;
    return this.http.get<DaySlots[]>('/api/availability', { params });
  }

  submitBooking(req: BookingRequest) {
    return this.http.post<Booking>('/api/bookings', req);
  }
}
