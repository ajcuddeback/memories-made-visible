import { ChangeDetectionStrategy, Component, ElementRef, HostListener, computed, inject, input, output, signal } from '@angular/core';
import { MONTHS, fmtTime, fromISO, toISO, today } from '../core/dates';

const WD = ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'];

/**
 * A date field in the studio's own style: a button showing the chosen date that opens a month
 * grid. Emits ISO yyyy-mm-dd ('' when cleared). Keyboard: Escape closes, the grid is buttons.
 */
@Component({
  selector: 'app-date-field',
  template: `
    <button type="button" class="pk-btn" [class.is-open]="open()" [class.is-empty]="!value()" [disabled]="disabled()"
            (click)="toggle()" [attr.aria-expanded]="open()" [attr.aria-label]="label()">
      <svg class="pk-ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" aria-hidden="true"><rect x="3" y="5" width="18" height="16" rx="3"/><path d="M3 10h18M8 3v4M16 3v4"/></svg>
      <span class="pk-text">{{ display() }}</span>
      @if (value() && clearable()) { <span class="pk-clear" role="button" aria-label="Clear" (click)="clear($event)">×</span> }
    </button>
    @if (open()) {
      <div class="pk-pop pk-cal" role="dialog" aria-label="Choose a date">
        <div class="pk-cal-head">
          <button type="button" class="pk-nav" (click)="shift(-1)" aria-label="Previous month">‹</button>
          <span class="pk-cal-title">{{ monthLabel() }}</span>
          <button type="button" class="pk-nav" (click)="shift(1)" aria-label="Next month">›</button>
        </div>
        <div class="pk-grid">
          @for (w of wd; track w) { <span class="pk-wd">{{ w }}</span> }
          @for (d of days(); track $index) {
            @if (d.iso) {
              <button type="button" class="pk-day" [class.is-today]="d.today" [class.is-selected]="d.iso === value()" [class.is-off]="d.off"
                      [disabled]="d.off" (click)="pick(d.iso)">{{ d.n }}</button>
            } @else { <span></span> }
          }
        </div>
        <div class="pk-foot">
          <button type="button" class="pk-link" (click)="pick(todayIso)">Today</button>
        </div>
      </div>
    }
  `,
  styleUrl: './pickers.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DateField {
  readonly value = input<string>('');
  readonly min = input<string>('');
  readonly label = input<string>('Date');
  readonly placeholder = input<string>('Pick a date');
  readonly disabled = input<boolean>(false);
  readonly clearable = input<boolean>(false);
  readonly valueChange = output<string>();

  readonly wd = WD;
  readonly open = signal(false);
  readonly todayIso = toISO(today());
  private readonly view = signal(this.viewFor(''));
  private readonly host = inject(ElementRef<HTMLElement>);

  readonly display = computed(() => (this.value() ? fromISO(this.value()).toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' }) : this.placeholder()));
  readonly monthLabel = computed(() => `${MONTHS[this.view().m]} ${this.view().y}`);
  readonly days = computed(() => {
    const { y, m } = this.view();
    const first = new Date(y, m, 1).getDay();
    const count = new Date(y, m + 1, 0).getDate();
    const out: { iso: string; n: string; today: boolean; off: boolean }[] = [];
    for (let i = 0; i < first; i++) out.push({ iso: '', n: '', today: false, off: true });
    for (let d = 1; d <= count; d++) {
      const iso = toISO(new Date(y, m, d));
      out.push({ iso, n: String(d), today: iso === this.todayIso, off: !!this.min() && iso < this.min() });
    }
    return out;
  });

  private viewFor(iso: string) { const d = iso ? fromISO(iso) : today(); return { y: d.getFullYear(), m: d.getMonth() }; }
  toggle(): void { if (!this.open()) this.view.set(this.viewFor(this.value())); this.open.update((v) => !v); }
  shift(by: number): void { this.view.update(({ y, m }) => { const d = new Date(y, m + by, 1); return { y: d.getFullYear(), m: d.getMonth() }; }); }
  pick(iso: string): void { this.valueChange.emit(iso); this.open.set(false); }
  clear(e: Event): void { e.stopPropagation(); this.valueChange.emit(''); }

  @HostListener('document:click', ['$event'])
  onDocClick(e: Event): void { if (this.open() && !this.host.nativeElement.contains(e.target as Node)) this.open.set(false); }
  @HostListener('document:keydown.escape')
  onEsc(): void { this.open.set(false); }
}

/**
 * A time field: a button showing "9:00 AM" that opens a scrollable list of times at `step`
 * minutes between `from` and `to`. Emits "HH:mm".
 */
@Component({
  selector: 'app-time-field',
  template: `
    <button type="button" class="pk-btn" [class.is-open]="open()" [class.is-empty]="!value()" [disabled]="disabled()"
            (click)="toggle()" [attr.aria-expanded]="open()" [attr.aria-label]="label()">
      <svg class="pk-ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg>
      <span class="pk-text">{{ display() }}</span>
    </button>
    @if (open()) {
      <div class="pk-pop pk-times" role="listbox" aria-label="Choose a time">
        @for (t of options(); track t.hm) {
          <button type="button" class="pk-time" [class.is-selected]="t.hm === value()" role="option" [attr.aria-selected]="t.hm === value()" (click)="pick(t.hm)">{{ t.label }}</button>
        }
      </div>
    }
  `,
  styleUrl: './pickers.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TimeField {
  readonly value = input<string>('');
  readonly label = input<string>('Time');
  readonly placeholder = input<string>('Pick a time');
  readonly disabled = input<boolean>(false);
  /** Minutes between options. */
  readonly step = input<number>(30);
  readonly from = input<string>('05:00');
  readonly to = input<string>('23:00');
  readonly valueChange = output<string>();

  readonly open = signal(false);
  private readonly host = inject(ElementRef<HTMLElement>);
  readonly display = computed(() => (this.value() ? fmtTime(this.value()) : this.placeholder()));
  readonly options = computed(() => {
    const out: { hm: string; label: string }[] = [];
    const [fh, fm] = this.from().split(':').map(Number);
    const [th, tm] = this.to().split(':').map(Number);
    for (let min = fh * 60 + fm; min <= th * 60 + tm; min += this.step()) {
      const hm = `${String(Math.floor(min / 60)).padStart(2, '0')}:${String(min % 60).padStart(2, '0')}`;
      out.push({ hm, label: fmtTime(hm) });
    }
    return out;
  });

  toggle(): void {
    this.open.update((v) => !v);
    if (this.open()) setTimeout(() => this.host.nativeElement.querySelector('.pk-time.is-selected')?.scrollIntoView({ block: 'center' }), 0);
  }
  pick(hm: string): void { this.valueChange.emit(hm); this.open.set(false); }

  @HostListener('document:click', ['$event'])
  onDocClick(e: Event): void { if (this.open() && !this.host.nativeElement.contains(e.target as Node)) this.open.set(false); }
  @HostListener('document:keydown.escape')
  onEsc(): void { this.open.set(false); }
}
