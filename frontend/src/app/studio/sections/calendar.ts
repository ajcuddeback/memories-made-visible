import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MONTHS, WEEKDAYS, fmtLongDate, fmtRange, fmtTime, fromISO, toISO, today } from '../../core/dates';
import { AvailabilityHours, BookingStatus, MoveReason } from '../../core/models';
import { ViewportService } from '../../core/viewport.service';
import { Icon } from '../../shared/icons';
import { DateField, TimeField } from '../../shared/pickers';
import { AdminStore } from '../admin-store.service';
import { AuthService } from '../auth.service';

type Kind = 'confirmed' | 'pending' | 'declined' | 'popup' | 'block';

interface CalItem {
  key: string;
  kind: Kind;
  /** "HH:mm" for sorting; '' = all day (sorts first) */
  sort: string;
  time: string;
  title: string;
  sub: string;
  bookingId?: number;
  blockId?: number;
}
interface CalDay {
  label: string;
  iso: string;
  isToday: boolean;
  isPast: boolean;
  closed: boolean;
  items: CalItem[];
}

/** Inline "block time" form. */
interface BlockForm { startDate: string; endDate: string; allDay: boolean; startTime: string; endTime: string; note: string; staffName: string; busy: boolean; error: string | null; }
/** Inline "move session" form: the new slot, why, and the confirmation email the customer gets. */
interface MoveForm {
  bookingId: number; to: string; date: string; time: string; reason: MoveReason;
  sendEmail: boolean; subject: string; body: string; loading: boolean; busy: boolean; error: string | null;
}

export const MOVE_REASONS: { key: MoveReason; label: string }[] = [
  { key: 'CLIENT_REQUEST', label: 'Client asked to reschedule' },
  { key: 'WEATHER', label: 'Weather' },
  { key: 'STUDIO', label: 'Conflict on our end' },
  { key: 'OTHER', label: 'Other' },
];

const KIND_LABEL: Record<Kind, string> = { confirmed: 'Confirmed', pending: 'Pending', declined: 'Declined', popup: 'Pop-up', block: 'Unavailable' };
const STATUS_KIND: Record<BookingStatus, Kind> = { CONFIRMED: 'confirmed', PENDING: 'pending', DECLINED: 'declined' };

@Component({
  selector: 'app-calendar',
  imports: [Icon, DateField, TimeField],
  templateUrl: './calendar.html',
  styleUrl: './calendar.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CalendarSection {
  readonly store = inject(AdminStore);
  readonly auth = inject(AuthService);
  readonly canManage = this.auth.canManage;
  /** Staff who can own a block (owner/admin pick anyone; employees only themselves). */
  readonly staff = signal<{ name: string; role: string }[]>([]);
  readonly viewport = inject(ViewportService);
  readonly weekdays = WEEKDAYS;
  readonly kindLabel = KIND_LABEL;
  readonly moveReasons = MOVE_REASONS;
  private readonly today = today();
  readonly todayIso = toISO(this.today);

  readonly cy = signal(this.today.getFullYear());
  readonly cm = signal(this.today.getMonth());
  readonly label = computed(() => `${MONTHS[this.cm()]} ${this.cy()}`);
  readonly selected = signal<string | null>(this.todayIso);
  readonly showDeclined = signal(false);
  readonly notice = signal('');

  readonly block = signal<BlockForm | null>(null);
  readonly move = signal<MoveForm | null>(null);

  // Booking-hours editor (a draft of the store's hours until saved)
  readonly hoursOpen = signal(false);
  /** Whose week the editor shows: '' = the studio's. Employees can only see their own. */
  readonly hoursFor = signal('');
  readonly hoursCustom = signal(false);
  readonly hoursLoading = signal(false);
  readonly hoursDraft = signal<AvailabilityHours[]>([]);
  readonly hoursMsg = signal('');
  readonly hoursBusy = signal(false);

  /** Every calendar item for the visible month, keyed by day. */
  private readonly itemsByDay = computed(() => {
    const names = this.store.typeName();
    const map = new Map<string, CalItem[]>();
    const push = (iso: string, it: CalItem) => { const l = map.get(iso) ?? []; l.push(it); map.set(iso, l); };
    for (const b of this.store.bookings()) {
      if (b.status === 'DECLINED' && !this.showDeclined()) continue;
      push(b.date, {
        key: 'b' + b.id, kind: STATUS_KIND[b.status], sort: b.startTime ?? '99', time: b.startTime ? fmtRange(b.startTime, b.endTime) : b.time,
        title: b.name, sub: names.get(b.sessionType) ?? b.sessionTypeName, bookingId: b.id,
      });
    }
    for (const e of this.store.events()) {
      push(e.date, { key: 'e' + e.id, kind: 'popup', sort: '98', time: e.time, title: e.title, sub: e.venue });
    }
    for (const bl of this.store.blocks()) {
      const allDay = !bl.startTime;
      for (let d = fromISO(bl.startDate); toISO(d) <= bl.endDate; d.setDate(d.getDate() + 1)) {
        push(toISO(d), {
          key: 'k' + bl.id + toISO(d), kind: 'block', sort: allDay ? '' : bl.startTime!, time: allDay ? 'All day' : fmtRange(bl.startTime, bl.endTime),
          title: (bl.staffName ? bl.staffName + ' · ' : '') + (bl.note || 'Unavailable'),
          sub: [bl.staffName ? '' : 'Whole studio', bl.startDate === bl.endDate ? '' : `${fmtDateShort(bl.startDate)} – ${fmtDateShort(bl.endDate)}`].filter(Boolean).join(' · '), blockId: bl.id,
        });
      }
    }
    for (const l of map.values()) l.sort((a, b) => a.sort.localeCompare(b.sort));
    return map;
  });

  readonly days = computed<CalDay[]>(() => {
    const y = this.cy(), m = this.cm();
    const first = new Date(y, m, 1).getDay();
    const count = new Date(y, m + 1, 0).getDate();
    const items = this.itemsByDay();
    const closedDays = new Set(this.store.hours().filter((h) => !h.open).map((h) => h.weekday));
    const out: CalDay[] = [];
    for (let i = 0; i < first; i++) out.push({ label: '', iso: '', isToday: false, isPast: true, closed: false, items: [] });
    for (let d = 1; d <= count; d++) {
      const dt = new Date(y, m, d);
      const iso = toISO(dt);
      out.push({ label: String(d), iso, isToday: iso === this.todayIso, isPast: dt < this.today, closed: closedDays.has(dt.getDay()), items: items.get(iso) ?? [] });
    }
    return out;
  });

  readonly selectedDay = computed(() => {
    const iso = this.selected();
    if (!iso) return null;
    const dt = fromISO(iso);
    const h = this.store.hours().find((x) => x.weekday === dt.getDay());
    return {
      iso, title: fmtLongDate(dt), items: this.itemsByDay().get(iso) ?? [],
      hours: h ? (h.open ? `Booking hours ${fmtRange(h.start, h.end)}` : 'Closed for bookings') : '',
    };
  });

  constructor() {
    if (this.canManage()) this.store.team().subscribe({ next: (m) => this.staff.set(m.filter((x) => x.active).map((x) => ({ name: x.name, role: x.role }))), error: () => undefined });
  }

  // ── navigation ──
  prev(): void { if (this.cm() === 0) { this.cm.set(11); this.cy.update((y) => y - 1); } else this.cm.update((m) => m - 1); }
  next(): void { if (this.cm() === 11) { this.cm.set(0); this.cy.update((y) => y + 1); } else this.cm.update((m) => m + 1); }
  goToday(): void { this.cy.set(this.today.getFullYear()); this.cm.set(this.today.getMonth()); this.selected.set(this.todayIso); }
  select(iso: string): void {
    if (!iso) return;
    this.selected.set(iso);
    this.move.set(null);
    const b = this.block();
    if (b) this.block.set({ ...b, startDate: iso, endDate: b.endDate && b.endDate < iso ? iso : b.endDate });
  }

  // ── block time ──
  openBlock(iso?: string): void {
    const d = iso ?? this.selected() ?? this.todayIso;
    this.block.set({ startDate: d, endDate: '', allDay: true, startTime: '12:00', endTime: '13:00', note: '', staffName: this.canManage() ? '' : (this.auth.user()?.name ?? ''), busy: false, error: null });
    this.move.set(null);
  }
  closeBlock(): void { this.block.set(null); }
  setBlock<K extends keyof BlockForm>(field: K, value: BlockForm[K]): void { this.block.update((b) => (b ? { ...b, [field]: value } : b)); }
  val(e: Event): string { return (e.target as HTMLInputElement).value; }
  checked(e: Event): boolean { return (e.target as HTMLInputElement).checked; }

  saveBlock(): void {
    const b = this.block();
    if (!b || b.busy) return;
    if (!b.startDate) { this.setBlock('error', 'Pick a start date.'); return; }
    if (!b.allDay && (!b.startTime || !b.endTime || b.startTime >= b.endTime)) { this.setBlock('error', 'The end time needs to be after the start time.'); return; }
    this.setBlock('busy', true); this.setBlock('error', null);
    this.store.addBlock({
      startDate: b.startDate, endDate: b.endDate || null,
      startTime: b.allDay ? null : b.startTime, endTime: b.allDay ? null : b.endTime, note: b.note.trim(), staffName: b.staffName || null,
    }).subscribe({
      next: () => { this.block.set(null); this.say('Time blocked. Customers can no longer book it.'); },
      error: (e) => { this.setBlock('busy', false); this.setBlock('error', e?.error?.message ?? 'Couldn’t save that. Please try again.'); },
    });
  }

  removeBlock(id: number): void {
    if (!confirm('Remove this block and open the time up again?')) return;
    this.store.removeBlock(id).subscribe({ next: () => this.say('Block removed.'), error: () => this.say('Couldn’t remove that block.') });
  }

  // ── move a session ──
  openMove(bookingId: number): void {
    const b = this.store.bookingById().get(bookingId);
    if (!b) return;
    this.move.set({
      bookingId, to: b.email, date: b.date, time: b.startTime ?? '09:00', reason: 'CLIENT_REQUEST',
      sendEmail: true, subject: '', body: '', loading: false, busy: false, error: null,
    });
    this.block.set(null);
    this.loadMoveDraft();
  }
  closeMove(): void { this.move.set(null); }
  setMove<K extends keyof MoveForm>(field: K, value: MoveForm[K]): void { this.move.update((m) => (m ? { ...m, [field]: value } : m)); }
  /** Date, time or reason changed → refresh the template (unless the admin has started editing it). */
  setMoveSlot(field: 'date' | 'time' | 'reason', value: string): void {
    this.setMove(field, value as never);
    this.loadMoveDraft();
  }
  private draftEdited = false;
  private loadMoveDraft(): void {
    const m = this.move();
    if (!m || !m.date || !m.time) return;
    if (this.draftEdited && (m.subject || m.body)) return;
    this.draftEdited = false;
    this.setMove('loading', true);
    const id = m.bookingId;
    this.store.rescheduleDraft(id, m.reason, m.date, m.time).subscribe({
      next: (d) => this.move.update((x) => (x && x.bookingId === id ? { ...x, subject: d.subject, body: d.body, to: d.to, loading: false } : x)),
      error: () => this.setMove('loading', false),
    });
  }
  editMoveText(field: 'subject' | 'body', value: string): void { this.draftEdited = true; this.setMove(field, value); }
  saveMove(): void {
    const m = this.move();
    if (!m || m.busy) return;
    if (!m.date || !m.time) { this.setMove('error', 'Pick a date and a start time.'); return; }
    if (m.sendEmail && (!m.subject.trim() || !m.body.trim())) { this.setMove('error', 'Add a subject and a message, or untick the email.'); return; }
    this.setMove('busy', true); this.setMove('error', null);
    this.store.reschedule(m.bookingId, { date: m.date, time: m.time, reason: m.reason, sendEmail: m.sendEmail, subject: m.subject.trim(), body: m.body.trim() }).subscribe({
      next: (b) => {
        this.move.set(null); this.draftEdited = false; this.selected.set(b.date);
        this.say(`${b.name} moved to ${fmtLongDate(fromISO(b.date))} at ${fmtTime(b.startTime)}${m.sendEmail ? ` — confirmation request emailed to ${m.to}` : ' — no email sent'}.`);
      },
      error: (e) => { this.setMove('busy', false); this.setMove('error', e?.error?.message ?? 'Couldn’t move that session.'); },
    });
  }

  // ── booking hours ──
  toggleHours(): void {
    this.hoursMsg.set('');
    if (!this.hoursOpen()) {
      // Owner/admin start on the studio's week; employees only ever see their own.
      this.hoursFor.set(this.canManage() ? '' : (this.auth.user()?.name ?? ''));
      this.loadWeek();
    }
    this.hoursOpen.update((v) => !v);
  }
  pickHoursFor(e: Event): void { this.hoursFor.set((e.target as HTMLSelectElement).value); this.hoursMsg.set(''); this.loadWeek(); }
  private loadWeek(): void {
    this.hoursLoading.set(true);
    this.store.week(this.hoursFor() || null).subscribe({
      next: (w) => { this.hoursDraft.set(w.hours.map((h) => ({ ...h }))); this.hoursCustom.set(w.custom); this.hoursLoading.set(false); },
      error: () => { this.hoursLoading.set(false); this.hoursMsg.set('Couldn’t load those hours.'); },
    });
  }
  /** Back to the studio's week for the selected person. */
  useStudioHours(): void {
    const who = this.hoursFor();
    if (!who || !confirm(`Drop ${who}'s own hours and use the studio's?`)) return;
    this.hoursBusy.set(true);
    this.store.clearWeek(who).subscribe({
      next: (w) => { this.hoursBusy.set(false); this.hoursDraft.set(w.hours.map((h) => ({ ...h }))); this.hoursCustom.set(false); this.hoursMsg.set(`${who} now follows the studio's hours.`); },
      error: (e) => { this.hoursBusy.set(false); this.hoursMsg.set(e?.error?.message ?? 'Couldn’t change that.'); },
    });
  }
  setHour(weekday: number, field: 'open' | 'start' | 'end', value: boolean | string): void {
    this.hoursDraft.update((list) => list.map((h) => (h.weekday === weekday ? { ...h, [field]: value } : h)));
  }
  saveHours(): void {
    if (this.hoursBusy()) return;
    const bad = this.hoursDraft().find((h) => h.open && h.start >= h.end);
    if (bad) { this.hoursMsg.set(`${WEEKDAYS[bad.weekday]}: the end time needs to be after the start time.`); return; }
    this.hoursBusy.set(true);
    const who = this.hoursFor();
    const done = () => {
      this.hoursBusy.set(false);
      if (who) { this.hoursCustom.set(true); this.hoursMsg.set(`Saved — ${who} now has their own hours; the booking page offers times inside them.`); }
      else this.hoursMsg.set('Saved. The booking page offers times inside these hours (for anyone without their own).');
    };
    const fail = (e: { error?: { message?: string } }) => { this.hoursBusy.set(false); this.hoursMsg.set(e?.error?.message ?? 'Couldn’t save the hours.'); };
    if (who) this.store.saveWeek(who, this.hoursDraft()).subscribe({ next: done, error: fail });
    else this.store.saveHours(this.hoursDraft()).subscribe({ next: done, error: fail });
  }
  hoursSummary(h: AvailabilityHours): string { return h.open ? fmtRange(h.start, h.end) : 'Closed'; }

  private say(text: string): void {
    this.notice.set(text);
    setTimeout(() => this.notice.set(''), 6000);
  }
}

function fmtDateShort(iso: string): string {
  return fromISO(iso).toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
}
