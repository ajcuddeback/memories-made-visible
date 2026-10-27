import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { forkJoin, tap } from 'rxjs';
import { AvailabilityHours, Booking, DeliverRequest, EmailAction, TimeBlock, TimeBlockCreate, EmailDraft, EmailRequest, Gallery, PhotoGroup, PopupEvent, PopupEventUpsert, PublishStatus, RescheduleRequest, MoveReason, SessionType, SessionTypeCreate } from '../core/models';
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
  readonly hours = signal<AvailabilityHours[]>([]);
  readonly blocks = signal<TimeBlock[]>([]);
  readonly loaded = signal(false);
  /** Draft vs. live. Pricing, pop-ups and photos are a draft until Publish; bookings and galleries are not staged. */
  readonly publishStatus = signal<PublishStatus | null>(null);
  readonly publishing = signal(false);
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
      pricing: this.http.get<SessionType[]>('/api/admin/pricing'),
      publish: this.http.get<PublishStatus>('/api/admin/publish'),
      hours: this.http.get<AvailabilityHours[]>('/api/admin/availability/hours'),
      blocks: this.http.get<TimeBlock[]>('/api/admin/availability/blocks'),
      photos: this.http.get<PhotoGroup[]>('/api/admin/photos'),
    }).subscribe({
      next: (r) => {
        this.bookings.set(r.bookings);
        this.events.set(r.events);
        this.galleries.set(r.galleries);
        this.pricing.set(r.pricing);
        this.photoGroups.set(r.photos);
        this.publishStatus.set(r.publish);
        this.hours.set(r.hours);
        this.blocks.set(r.blocks);
        this.loaded.set(true);
      },
      error: () => this.loadError.set('Couldn’t load the studio data. Check that the API is running.'),
    });
  }

  reset(): void {
    this.bookings.set([]); this.events.set([]); this.galleries.set([]); this.photoGroups.set([]);
    this.publishStatus.set(null);
    this.hours.set([]); this.blocks.set([]);
    this.loaded.set(false);
  }

  // ── availability ──
  saveHours(hours: AvailabilityHours[]) {
    return this.http.put<AvailabilityHours[]>('/api/admin/availability/hours', hours).pipe(tap((h) => this.hours.set(h)));
  }
  addBlock(body: TimeBlockCreate) {
    return this.http.post<TimeBlock>('/api/admin/availability/blocks', body).pipe(
      tap((b) => this.blocks.update((list) => [...list, b].sort((x, y) => x.startDate.localeCompare(y.startDate) || (x.startTime ?? '').localeCompare(y.startTime ?? '')))),
    );
  }
  removeBlock(id: number) {
    return this.http.delete<void>(`/api/admin/availability/blocks/${id}`).pipe(tap(() => this.blocks.update((list) => list.filter((b) => b.id !== id))));
  }
  /** The "please confirm the new time" email for a move, pre-filled from the reason's template. */
  rescheduleDraft(id: number, reason: MoveReason, date: string, time: string) {
    return this.http.get<EmailDraft>(`/api/admin/bookings/${id}/reschedule-draft`, { params: { reason, date, time } });
  }
  reschedule(id: number, body: RescheduleRequest) {
    return this.http.post<Booking>(`/api/admin/bookings/${id}/reschedule`, body).pipe(tap((b) => this.replaceBooking(b)));
  }

  // ── publish ──
  refreshPublishStatus(): void {
    this.http.get<PublishStatus>('/api/admin/publish').subscribe((s) => this.publishStatus.set(s));
  }
  publish() {
    this.publishing.set(true);
    return this.http.post<PublishStatus>('/api/admin/publish', {}).pipe(
      tap({
        next: (s) => { this.publishStatus.set(s); this.publishing.set(false); this.site.invalidate(); },
        error: () => this.publishing.set(false),
      }),
    );
  }
  /** Throw away the draft and reload the admin from what's live. */
  discard() {
    this.publishing.set(true);
    return this.http.post<PublishStatus>('/api/admin/publish/discard', {}).pipe(
      tap({
        next: (s) => { this.publishStatus.set(s); this.publishing.set(false); this.load(); },
        error: () => this.publishing.set(false),
      }),
    );
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
      this.refreshPublishStatus();
    });
  }
  setPrints(id: number, patch: { prints?: number; printPrice?: number }): void {
    this.http.patch<PopupEvent>(`/api/admin/events/${id}/prints`, patch).subscribe((e) =>
      this.events.update((list) => list.map((x) => (x.id === e.id ? e : x))),
    );
  }
  removeEvent(id: number): void {
    this.http.delete(`/api/admin/events/${id}`).subscribe(() => {
      this.events.update((list) => list.filter((x) => x.id !== id));
      this.refreshPublishStatus();
    });
  }

  // ── galleries ──
  addGallery(bookingId: number): void {
    this.http.post<Gallery>('/api/admin/galleries', { bookingId }).subscribe((g) => this.galleries.update((list) => [...list, g]));
  }
  removeGallery(id: number): void {
    this.http.delete(`/api/admin/galleries/${id}`).subscribe(() => this.galleries.update((list) => list.filter((g) => g.id !== id)));
  }
  setStage(id: number, stage: number): void {
    this.http.patch<Gallery>(`/api/admin/galleries/${id}/stage`, { stage }).subscribe((g) =>
      this.galleries.update((list) => list.map((x) => (x.id === g.id ? g : x))),
    );
  }
  /** The "your photos are ready" email, pre-filled. */
  galleryDraft(id: number) {
    return this.http.get<EmailDraft>(`/api/admin/galleries/${id}/draft`);
  }
  /** Mark delivered with the link and final price; the booking comes back with its updated quote (revenue follows). */
  deliverGallery(id: number, body: DeliverRequest) {
    return this.http.post<{ gallery: Gallery; booking: Booking }>(`/api/admin/galleries/${id}/deliver`, body).pipe(
      tap((r) => {
        this.galleries.update((list) => list.map((x) => (x.id === r.gallery.id ? r.gallery : x)));
        this.replaceBooking(r.booking);
      }),
    );
  }

  // ── pricing ──
  savePricing(updates: SessionType[]) {
    return this.http.put<SessionType[]>('/api/admin/pricing', updates);
  }
  createCategory(body: SessionTypeCreate) {
    return this.http.post<SessionType[]>('/api/admin/pricing', body);
  }
  deleteCategory(key: string) {
    return this.http.delete<SessionType[]>(`/api/admin/pricing/${key}`);
  }
  resetPricing() {
    return this.http.post<SessionType[]>('/api/admin/pricing/reset', {});
  }
  applyPricing(list: SessionType[]): void {
    this.pricing.set(list);
    // Portfolio photo slots follow the categories; refresh the admin's slot grid too.
    this.http.get<PhotoGroup[]>('/api/admin/photos').subscribe((g) => this.photoGroups.set(g));
    this.refreshPublishStatus();
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
    this.refreshPublishStatus();
  }
}
