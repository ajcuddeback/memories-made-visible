import { HttpClient } from '@angular/common/http';
import { isPlatformBrowser } from '@angular/common';
import { Injectable, PLATFORM_ID, computed, inject, signal } from '@angular/core';
import { catchError, of } from 'rxjs';
import { BookingRequest, Booking, PublicPopupEvent, SessionType } from './models';

/** Short labels for the portfolio filter chips and captions (UI copy, not data). */
export const CATEGORY_SHORT: Record<string, string> = {
  families: 'Families & solo',
  couples: 'Couples',
  weddings: 'Weddings',
  maternity: 'Maternity & newborn',
  seniors: 'Seniors',
  events: 'Events',
};

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

  submitBooking(req: BookingRequest) {
    return this.http.post<Booking>('/api/bookings', req);
  }
}
