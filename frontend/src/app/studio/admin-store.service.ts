import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { forkJoin, tap } from 'rxjs';
import { Booking, EmailAction, EmailDraft, EmailRequest, Gallery, PhotoGroup, PopupEvent, PopupEventUpsert, SessionType } from '../core/models';
import { SiteDataService } from '../core/site-data.service';

/**
 * Everything the studio admin shows is small enough to hold in memory, so this
 * store loads it once after sign-in and keeps the signals in step with each
 * mutation. The overview's stats are computed from the same signals.
 */
@Injectable({ providedIn: 'root' })
export class AdminStore {
  private readonly http = inject(HttpClient);
  private readonly site = inject(SiteDataService);

  readonly bookings = signal<Booking[]>([]);
  readonly events = signal<PopupEvent[]>([]);
  readonly galleries = signal<Gallery[]>([]);
  readonly pricing = signal<SessionType[]>([]);
  readonly photoGroups = signal<PhotoGroup[]>([]);
  readonly loaded = signal(false);
  readonly loadError = signal<string | null>(null);

  readonly pending = computed(() => this.bookings().filter((b) => b.status === 'PENDING'));
  readonly confirmed = computed(() => this.bookings().filter((b) => b.status === 'CONFIRMED'));
  readonly bookingById = computed(() => new Map(this.bookings().map((b) => [b.id, b])));
  readonly typeName = computed(() => new Map(this.pricing().map((t) => [t.key, t.name])));

  load(): void {
    this.loadError.set(null);
    forkJoin({
      bookings: this.http.get<Booking[]>('/api/admin/bookings'),
      events: this.http.get<PopupEvent[]>('/api/admin/events'),
      galleries: this.http.get<Gallery[]>('/api/admin/galleries'),
      pricing: this.http.get<SessionType[]>('/api/pricing'),
      photos: this.http.get<PhotoGroup[]>('/api/admin/photos'),
    }).subscribe({
      next: (r) => {
        this.bookings.set(r.bookings);
        this.events.set(r.events);
        this.galleries.set(r.galleries);
        this.pricing.set(r.pricing);
        this.photoGroups.set(r.photos);
        this.loaded.set(true);
      },
      error: () => this.loadError.set('Couldn’t load the studio data. Check that the API is running.'),
    });
  }

  reset(): void {
    this.bookings.set([]); this.events.set([]); this.galleries.set([]); this.photoGroups.set([]);
    this.loaded.set(false);
  }

  // ── bookings ──
  setQuote(id: number, quote: number): void {
    this.http.patch<Booking>(`/api/admin/bookings/${id}/quote`, { quote }).subscribe((b) => this.replaceBooking(b));
  }
  /** The email the studio would send for an action, pre-filled for the admin to edit. */
  draft(id: number, action: EmailAction) {
    return this.http.get<EmailDraft>(`/api/admin/bookings/${id}/draft`, { params: { action } });
  }
  approve(id: number, email?: EmailRequest) { return this.bookingAction(id, 'approve', email); }
  decline(id: number, email?: EmailRequest) { return this.bookingAction(id, 'decline', email); }
  restore(id: number) { return this.bookingAction(id, 'restore'); }
  message(id: number, email: EmailRequest) {
    return this.http.post<void>(`/api/admin/bookings/${id}/message`, email);
  }
  private bookingAction(id: number, action: string, email?: EmailRequest) {
    return this.http.post<Booking>(`/api/admin/bookings/${id}/${action}`, email ?? {}).pipe(tap((b) => this.replaceBooking(b)));
  }
  private replaceBooking(b: Booking): void {
    this.bookings.update((list) => list.map((x) => (x.id === b.id ? b : x)));
  }

  // ── pop-ups ──
  addEvent(body: PopupEventUpsert): void {
    this.http.post<PopupEvent>('/api/admin/events', body).subscribe((e) => {
      this.events.update((list) => [...list, e].sort((a, b) => a.date.localeCompare(b.date)));
      this.site.upcoming.set([]); // public cache is stale now; it reloads on next visit
    });
  }
  setPrints(id: number, patch: { prints?: number; printPrice?: number }): void {
    this.http.patch<PopupEvent>(`/api/admin/events/${id}/prints`, patch).subscribe((e) =>
      this.events.update((list) => list.map((x) => (x.id === e.id ? e : x))),
    );
  }
  removeEvent(id: number): void {
    this.http.delete(`/api/admin/events/${id}`).subscribe(() => this.events.update((list) => list.filter((x) => x.id !== id)));
  }

  // ── galleries ──
  addGallery(bookingId: number): void {
    this.http.post<Gallery>('/api/admin/galleries', { bookingId }).subscribe((g) => this.galleries.update((list) => [...list, g]));
  }
  setStage(id: number, stage: number): void {
    this.http.patch<Gallery>(`/api/admin/galleries/${id}/stage`, { stage }).subscribe((g) =>
      this.galleries.update((list) => list.map((x) => (x.id === g.id ? g : x))),
    );
  }

  // ── pricing ──
  savePricing(updates: { key: string; from: string; tiers: { name: string; detail: string; price: string }[] }[]) {
    return this.http.put<SessionType[]>('/api/admin/pricing', updates);
  }
  resetPricing() {
    return this.http.post<SessionType[]>('/api/admin/pricing/reset', {});
  }
  applyPricing(list: SessionType[]): void {
    this.pricing.set(list);
    this.site.sessionTypes.set(list);
  }

  // ── photos ──
  uploadPhoto(slotId: string, file: File) {
    const form = new FormData();
    form.append('file', file, file.name);
    return this.http.post<{ slotId: string; url: string }>(`/api/admin/photos/${slotId}`, form);
  }
  clearPhoto(slotId: string) {
    return this.http.delete(`/api/admin/photos/${slotId}`);
  }
  setPhotoUrl(slotId: string, url: string | null): void {
    this.photoGroups.update((groups) =>
      groups.map((g) => ({ ...g, slots: g.slots.map((s) => (s.id === slotId ? { ...s, url } : s)) })),
    );
    this.site.photos.update((map) => {
      const next = { ...map };
      if (url) next[slotId] = url; else delete next[slotId];
      return next;
    });
  }
}
