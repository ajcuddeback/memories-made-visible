import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { MONTHS, fmtLongDate, toISO, today } from '../../core/dates';
import { SiteDataService } from '../../core/site-data.service';
import { Icon } from '../../shared/icons';

const TIMES = ['9:00 AM', '11:00 AM', '2:00 PM', '4:00 PM', 'Golden hour'];
const WEEKDAYS_SHORT = ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'];

interface Day {
  label: string;
  date: Date | null;
  past: boolean;
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
  private readonly route = inject(ActivatedRoute);
  private readonly today = today();

  readonly weekdays = WEEKDAYS_SHORT;
  readonly times = TIMES;
  readonly types = this.data.sessionTypes;

  // Form state
  readonly type = signal<string | null>(null);
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
    const out: Day[] = [];
    for (let i = 0; i < first; i++) out.push({ label: '', date: null, past: true });
    for (let d = 1; d <= count; d++) {
      const dt = new Date(y, m, d);
      out.push({ label: String(d), date: dt, past: dt <= this.today });
    }
    return out;
  });

  readonly typeName = computed(() => this.data.typeByKey().get(this.type() ?? '')?.name ?? null);
  readonly dateLabel = computed(() => (this.date() ? fmtLongDate(this.date()!) : null));

  readonly missing = computed(() => {
    const m: string[] = [];
    if (!this.type()) m.push('a session type');
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
    const preset = this.route.snapshot.queryParamMap.get('type');
    if (preset) this.type.set(preset);
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

  submit(): void {
    if (this.missing().length || this.submitting()) return;
    this.submitting.set(true);
    this.error.set(null);
    this.data.submitBooking({
      sessionType: this.type()!,
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
        this.submitting.set(false);
        this.submitted.set(true);
        window.scrollTo(0, 0);
      },
      error: (err) => {
        this.submitting.set(false);
        this.error.set(err?.error?.message ?? 'We couldn’t send your request just now. Please try again in a moment.');
      },
    });
  }

  reset(): void {
    this.type.set(null); this.date.set(null); this.time.set(null);
    this.name.set(''); this.email.set(''); this.phone.set(''); this.location.set(''); this.notes.set('');
    this.submitted.set(false);
    this.error.set(null);
  }
}
