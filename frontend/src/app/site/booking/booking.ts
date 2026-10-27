import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { MONTHS, fmtDuration, fmtLongDate, fmtTime, money, num, toISO, today } from '../../core/dates';
import { Slot } from '../../core/models';
import { SiteDataService } from '../../core/site-data.service';
import { AnalyticsService } from '../../core/analytics.service';
import { Icon } from '../../shared/icons';

const WEEKDAYS_SHORT = ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'];

interface Day {
  label: string;
  date: Date | null;
  iso: string;
  /** Not bookable: in the past, or no open slots for this session type. */
  off: boolean;
}

@Component({
  selector: 'app-booking',
  imports: [Icon],
  templateUrl: './booking.html',
  styleUrl: './booking.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BookingPage {
  private readonly data = inject(SiteDataService);
  private readonly analytics = inject(AnalyticsService);
  private trackedDetails = false;
  private readonly route = inject(ActivatedRoute);
  private readonly today = today();

  readonly weekdays = WEEKDAYS_SHORT;
  readonly skeletons = [0, 1, 2, 3, 4, 5];
  /** Open slots per day for the visible month and chosen session type, from the API. */
  readonly openByDay = signal<Map<string, Slot[]>>(new Map());
  readonly availLoading = signal(false);
  readonly availError = signal(false);
  readonly types = this.data.sessionTypes;
  readonly contact = this.data.contact;
  /** Bookable photographers; when there are none the step is hidden and the studio books as one. */
  readonly photographers = this.data.photographers;
  readonly photographer = signal<string | null>(null);
  readonly hasPhotographers = computed(() => this.photographers().length > 0);
  readonly photographerLabel = computed(() => this.photographer() ?? (this.hasPhotographers() ? 'No preference' : null));
  pickPhotographer(name: string | null): void { this.photographer.set(name); this.time.set(null); }
  /** Step numbers shift with the optional package and photographer steps. */
  stepNo(step: 'package' | 'photographer' | 'when' | 'details'): number {
    let n = 1;
    if (step === 'package') return n + 1;
    if (this.needsPackage()) n++;
    if (step === 'photographer') return n + 1;
    if (this.hasPhotographers()) n++;
    if (step === 'when') return n + 1;
    return n + 2;
  }
  readonly typesError = this.data.pricingError;

  // Form state
  readonly type = signal<string | null>(null);
  readonly pkg = signal<string | null>(null);
  readonly hours = signal<number | null>(null);
  readonly addons = signal<Set<string>>(new Set());
  readonly date = signal<Date | null>(null);
  readonly time = signal<string | null>(null);
  readonly name = signal('');
  readonly email = signal('');
  readonly phone = signal('');
  readonly location = signal('');
  readonly notes = signal('');
  readonly website = signal(''); // honeypot

  readonly submitting = signal(false);
  readonly submitted = signal(false);
  readonly error = signal<string | null>(null);

  // Calendar state
  readonly vy = signal(this.today.getFullYear());
  readonly vm = signal(this.today.getMonth());
  readonly monthLabel = computed(() => `${MONTHS[this.vm()]} ${this.vy()}`);
  readonly prevDisabled = computed(() => this.vy() === this.today.getFullYear() && this.vm() === this.today.getMonth());

  readonly days = computed<Day[]>(() => {
    const y = this.vy(), m = this.vm();
    const first = new Date(y, m, 1).getDay();
    const count = new Date(y, m + 1, 0).getDate();
    const open = this.openByDay();
    const out: Day[] = [];
    for (let i = 0; i < first; i++) out.push({ label: '', date: null, iso: '', off: true });
    for (let d = 1; d <= count; d++) {
      const dt = new Date(y, m, d);
      const iso = toISO(dt);
      out.push({ label: String(d), date: dt, iso, off: dt < this.today || !(open.get(iso)?.length) });
    }
    return out;
  });

  /** Start times open on the chosen day. */
  readonly times = computed<Slot[]>(() => (this.date() ? this.openByDay().get(toISO(this.date()!)) ?? [] : []));
  readonly monthHasSlots = computed(() => this.days().some((d) => d.date && !d.off));

  readonly typeName = computed(() => this.data.typeByKey().get(this.type() ?? '')?.name ?? null);
  /** Packages of the chosen session type, with a length label. */
  readonly packages = computed(() => (this.data.typeByKey().get(this.type() ?? '')?.tiers ?? []).map((t) => ({
    ...t,
    addons: t.addons ?? [],
    minHours: t.minHours || 1,
    maxHours: t.maxHours || 12,
    hourly: t.pricingModel === 'HOURLY',
    length: t.pricingModel === 'HOURLY' ? `${t.minHours}–${t.maxHours} hrs` : fmtDuration(t.durationMinutes || 60),
    priceLabel: t.pricingModel === 'HOURLY' ? `${money(num(t.price))}/hr` : t.price,
  })));
  readonly needsPackage = computed(() => this.packages().length > 0);
  readonly chosenPackage = computed(() => this.packages().find((p) => p.name === this.pkg()) ?? null);
  readonly hourOptions = computed(() => {
    const p = this.chosenPackage();
    if (!p?.hourly) return [] as number[];
    return Array.from({ length: p.maxHours - p.minHours + 1 }, (_, i) => p.minHours + i);
  });
  /** Hours the session runs (chosen for hourly, fixed for flat). */
  private readonly sessionHours = computed(() => {
    const p = this.chosenPackage();
    if (!p) return 1;
    return p.hourly ? (this.hours() ?? p.minHours) : Math.ceil((p.durationMinutes || 60) / 60);
  });
  readonly duration = computed(() => {
    const p = this.chosenPackage();
    if (p?.hourly) return this.hours() ? fmtDuration(this.hours()! * 60) : `${p.minHours}–${p.maxHours} hrs`;
    return p?.length ?? fmtDuration(this.data.typeByKey().get(this.type() ?? '')?.durationMinutes ?? 60);
  });
  /** Starting quote from what's chosen; the studio confirms the final figure. */
  readonly estimate = computed(() => {
    const p = this.chosenPackage();
    if (!p) return null;
    const h = this.sessionHours();
    let total = p.hourly ? num(p.price) * h : num(p.price);
    for (const a of p.addons) if (this.addons().has(a.name)) total += a.perHour ? a.price * h : a.price;
    return money(total);
  });
  readonly money = money;
  readonly num = num;
  addonLabel(a: { price: number; perHour: boolean }): string { return `+${money(a.price)}${a.perHour ? '/hr' : ''}`; }
  toggleAddon(name: string): void {
    this.addons.update((s) => { const n = new Set(s); n.has(name) ? n.delete(name) : n.add(name); return n; });
  }
  pickPackage(name: string): void {
    this.pkg.set(name);
    this.time.set(null);
    this.addons.set(new Set());
    const p = this.packages().find((x) => x.name === name);
    this.hours.set(p?.hourly ? p.minHours : null);
  }
  pickHours(e: Event): void { this.hours.set(Number((e.target as HTMLSelectElement).value)); this.time.set(null); }
  readonly dateLabel = computed(() => (this.date() ? fmtLongDate(this.date()!) : null));
  readonly timeLabel = computed(() => (this.time() ? fmtTime(this.time()) : null));

  readonly missing = computed(() => {
    const m: string[] = [];
    if (!this.type()) m.push('a session type');
    else if (this.needsPackage() && !this.chosenPackage()) m.push('a package');
    else if (this.chosenPackage()?.hourly && !this.hours()) m.push('how many hours');
    if (!this.date()) m.push('a date');
    if (!this.time()) m.push('a time');
    if (!this.name().trim()) m.push('your name');
    if (!/\S+@\S+\.\S+/.test(this.email())) m.push('a valid email');
    return m;
  });
  readonly hint = computed(() =>
    this.missing().length ? `Still needed: ${this.missing().join(', ')}.` : 'No payment is taken now. We’ll confirm availability first.',
  );

  constructor() {
    this.data.loadPricing();
    this.data.loadPhotographers();
    const preset = this.route.snapshot.queryParamMap.get('type');
    if (preset) this.type.set(preset);
    this.analytics.track('book_view');
    const presetPkg = this.route.snapshot.queryParamMap.get('package');
    if (presetPkg) this.pkg.set(presetPkg); // hours/add-ons are chosen on the page
    // Refetch what's open whenever the visible month or the session type (its length) changes.
    effect(() => {
      const y = this.vy(), m = this.vm(), type = this.type(), pkg = this.pkg(), hours = this.hours(), who = this.photographer();
      this.photographers(); // refetch once the list arrives too
      untracked(() => this.fetchAvailability(y, m, type, pkg, hours, who));
    });
  }

  pickType(key: string): void {
    if (this.type() === key) return;
    this.analytics.track('book_type');
    this.type.set(key);
    const tiers = this.data.typeByKey().get(key)?.tiers ?? [];
    this.pkg.set(null); this.hours.set(null); this.addons.set(new Set()); this.time.set(null);
    if (tiers.length === 1) this.pickPackage(tiers[0].name);
  }

  private fetchAvailability(y: number, m: number, type: string | null, pkg: string | null, hours: number | null, who: string | null = null): void {
    const from = toISO(new Date(y, m, 1));
    const to = toISO(new Date(y, m + 1, 0));
    this.availLoading.set(true);
    this.availError.set(false);
    this.data.availability(from, to, type, pkg, hours, who).subscribe({
      next: (days) => {
        this.openByDay.set(new Map(days.map((d) => [d.date, d.slots])));
        this.availLoading.set(false);
        // The chosen time may no longer be open (someone booked it, or the type got longer).
        const t = this.time();
        if (t && !this.times().some((s) => s.start === t)) this.time.set(null);
      },
      error: () => { this.availLoading.set(false); this.availError.set(true); },
    });
  }

  pickDate(d: Date): void {
    this.date.set(d);
    this.time.set(null);
  }

  isSelected(d: Day): boolean {
    return !!d.date && !!this.date() && d.date.getTime() === this.date()!.getTime();
  }

  prevMonth(): void {
    if (this.vm() === 0) { this.vm.set(11); this.vy.update((y) => y - 1); } else this.vm.update((m) => m - 1);
  }

  nextMonth(): void {
    if (this.vm() === 11) { this.vm.set(0); this.vy.update((y) => y + 1); } else this.vm.update((m) => m + 1);
  }

  value(e: Event): string {
    return (e.target as HTMLInputElement | HTMLTextAreaElement).value;
  }

  contactClick(): void { this.analytics.track('contact_click'); }
  /** Funnel: a start time was chosen. */
  pickTime(hm: string): void {
    this.time.set(hm);
    this.analytics.track('book_slot');
  }
  /** Funnel: name and a valid email are in (fires once). */
  detailsTyped(): void {
    if (this.trackedDetails || !this.name().trim() || !/\S+@\S+\.\S+/.test(this.email())) return;
    this.trackedDetails = true;
    this.analytics.track('book_details');
  }

  submit(): void {
    if (this.missing().length || this.submitting()) return;
    this.submitting.set(true);
    this.error.set(null);
    this.data.submitBooking({
      sessionType: this.type()!,
      packageName: this.chosenPackage()?.name ?? null,
      hours: this.chosenPackage()?.hourly ? this.hours() : null,
      addons: [...this.addons()],
      photographer: this.photographer(),
      name: this.name().trim(),
      email: this.email().trim(),
      phone: this.phone().trim(),
      location: this.location().trim(),
      notes: this.notes().trim(),
      date: toISO(this.date()!),
      time: this.time()!,
      website: this.website(),
    }).subscribe({
      next: () => {
        this.analytics.track('book_submit');
        this.submitting.set(false);
        this.submitted.set(true);
        window.scrollTo(0, 0);
      },
      error: (err) => {
        this.submitting.set(false);
        this.error.set(err?.error?.message ?? 'We couldn’t send your request just now. Please try again in a moment.');
        if (err?.status === 409) { this.time.set(null); this.fetchAvailability(this.vy(), this.vm(), this.type(), this.pkg(), this.hours(), this.photographer()); }
      },
    });
  }

  reset(): void {
    this.type.set(null); this.pkg.set(null); this.hours.set(null); this.addons.set(new Set()); this.photographer.set(null); this.date.set(null); this.time.set(null);
    this.name.set(''); this.email.set(''); this.phone.set(''); this.location.set(''); this.notes.set('');
    this.submitted.set(false);
    this.error.set(null);
  }
}
