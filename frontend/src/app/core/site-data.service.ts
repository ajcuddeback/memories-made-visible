import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { catchError, of } from 'rxjs';
import { BookingRequest, Booking, PublicPopupEvent, SessionType } from './models';

/** Fallback copy of the price list so the site renders even if the API is briefly unreachable. */
export const DEFAULT_SESSION_TYPES: SessionType[] = [
  { key: 'families', name: 'Families & solo portraits', description: 'Relaxed sessions at home, at the park or by the lake, for the whole family or just you.', from: '$150',
    tiers: [{ name: 'Solo', detail: '30 minutes · 15 edited photos', price: '$150' }, { name: 'Family', detail: '1 hour · up to 6 people · 30 edited photos', price: '$225' }] },
  { key: 'couples', name: 'Couples & engagements', description: 'Engagements, anniversaries and the everyday moments in between.', from: '$150',
    tiers: [{ name: 'Mini', detail: '30 minutes · 20 edited photos', price: '$150' }, { name: 'Full session', detail: '1 hour · 2 locations · 40 edited photos', price: '$225' }] },
  { key: 'weddings', name: 'Weddings', description: 'Wedding-day coverage with a lead and a second photographer.', from: '$800',
    tiers: [{ name: 'Intimate', detail: '2 hours · lead photographer', price: '$800' }, { name: 'Classic', detail: '6 hours · two photographers', price: '$1,800' }, { name: 'Full day', detail: '8 hours · two photographers', price: '$2,400' }] },
  { key: 'maternity', name: 'Maternity & newborn', description: 'Calm, unhurried sessions for the newest chapter of your family.', from: '$200',
    tiers: [{ name: 'Maternity', detail: '1 hour · 25 edited photos', price: '$200' }, { name: 'Newborn', detail: 'Up to 2 hours at home · 25 edited photos', price: '$250' }, { name: 'Bump to baby', detail: 'Both sessions', price: '$400' }] },
  { key: 'seniors', name: 'Seniors & graduation', description: 'Senior portraits and graduation milestones, styled to the student.', from: '$150',
    tiers: [{ name: 'Mini', detail: '30 minutes · 1 outfit · 15 edited photos', price: '$150' }, { name: 'Classic', detail: '1 hour · 3 outfits · 2 locations · 35 edited photos', price: '$250' }] },
  { key: 'events', name: 'Events', description: 'Birthdays, showers, parties and community events across Polk County.', from: '$150/hr',
    tiers: [{ name: 'Event coverage', detail: '2-hour minimum', price: '$150/hr' }, { name: 'Second photographer', detail: 'Added coverage', price: '+$75/hr' }] },
];

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
 * Each is loaded once and cached in a signal for the session.
 */
@Injectable({ providedIn: 'root' })
export class SiteDataService {
  private readonly http = inject(HttpClient);

  readonly sessionTypes = signal<SessionType[]>(DEFAULT_SESSION_TYPES);
  readonly upcoming = signal<PublicPopupEvent[]>([]);
  readonly photos = signal<Record<string, string>>({});
  readonly typeByKey = computed(() => new Map(this.sessionTypes().map((t) => [t.key, t])));

  private loadedPricing = false;
  private loadedEvents = false;
  private loadedPhotos = false;

  loadPricing(): void {
    if (this.loadedPricing) return;
    this.loadedPricing = true;
    this.http.get<SessionType[]>('/api/pricing').pipe(catchError(() => of(null))).subscribe((list) => {
      if (list && list.length) this.sessionTypes.set(list);
    });
  }

  loadUpcoming(): void {
    if (this.loadedEvents) return;
    this.loadedEvents = true;
    this.http.get<PublicPopupEvent[]>('/api/events/upcoming').pipe(catchError(() => of([] as PublicPopupEvent[]))).subscribe((list) => this.upcoming.set(list));
  }

  loadPhotos(): void {
    if (this.loadedPhotos) return;
    this.loadedPhotos = true;
    this.http.get<Record<string, string>>('/api/photos').pipe(catchError(() => of({}))).subscribe((map) => this.photos.set(map));
  }

  submitBooking(req: BookingRequest) {
    return this.http.post<Booking>('/api/bookings', req);
  }
}
