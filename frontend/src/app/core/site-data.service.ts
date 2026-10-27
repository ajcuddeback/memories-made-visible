import { HttpClient } from '@angular/common/http';
import { isPlatformBrowser } from '@angular/common';
import { Injectable, PLATFORM_ID, computed, inject, signal } from '@angular/core';
import { catchError, of } from 'rxjs';
import { BookingRequest, Booking, DaySlots, PublicPopupEvent, SessionType } from './models';

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
  readonly photos = signal<Record<string, string>>({});
  readonly typeByKey = computed(() => new Map(this.sessionTypes().map((t) => [t.key, t])));

  private loadedPricing = false;
  private loadedEvents = false;
  private loadedPhotos = false;

  loadPricing(): void {
    if (!this.isBrowser || this.loadedPricing) return;
    this.loadedPricing = true;
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
    this.http.get<PublicPopupEvent[]>('/api/events/upcoming').pipe(catchError(() => of([] as PublicPopupEvent[]))).subscribe((list) => this.upcoming.set(list));
  }

  loadPhotos(): void {
    if (!this.isBrowser || this.loadedPhotos) return;
    this.loadedPhotos = true;
    this.http.get<Record<string, string>>('/api/photos').pipe(catchError(() => of({}))).subscribe((map) => this.photos.set(map));
  }

  /** After a publish: forget the cached lists so the next page visit refetches what's live. */
  invalidate(): void {
    this.loadedPricing = this.loadedEvents = this.loadedPhotos = false;
    this.sessionTypes.set([]);
    this.upcoming.set([]);
    this.photos.set({});
  }

  /** Open start times per day in [from, to] for a session type (not cached: it changes as people book). */
  availability(from: string, to: string, type: string | null, pkg: string | null) {
    const params: Record<string, string> = { from, to };
    if (type) params['type'] = type;
    if (pkg) params['package'] = pkg;
    return this.http.get<DaySlots[]>('/api/availability', { params });
  }

  submitBooking(req: BookingRequest) {
    return this.http.post<Booking>('/api/bookings', req);
  }
}
