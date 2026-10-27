import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { AuthService } from './auth.service';

/** Set a new password. `required` is the first-sign-in gate (temporary password must be replaced before anything else). */
@Component({
  selector: 'app-password-panel',
  template: `
    <div class="pw" [class.pw--gate]="required()">
      <form class="pw-card" (submit)="$event.preventDefault(); save()">
        <h2 class="pw-title">{{ required() ? 'Choose your password' : 'Change password' }}</h2>
        <p class="pw-sub">{{ required() ? 'You signed in with a temporary password. Pick your own to continue — at least 8 characters.' : 'At least 8 characters. You stay signed in.' }}</p>
        <label class="field field--sm">{{ required() ? 'Temporary password' : 'Current password' }}<input class="input" type="password" autocomplete="current-password" [value]="current()" (input)="current.set(val($event))" /></label>
        <label class="field field--sm">New password<input class="input" type="password" autocomplete="new-password" [value]="next()" (input)="next.set(val($event))" /></label>
        <label class="field field--sm">New password again<input class="input" type="password" autocomplete="new-password" [value]="again()" (input)="again.set(val($event))" /></label>
        @if (error()) { <p class="pw-error" role="alert">{{ error() }}</p> }
        <div class="pw-actions">
          <button type="submit" class="btn btn-primary sm" [disabled]="busy()">{{ busy() ? 'Saving…' : 'Save password' }}</button>
          @if (!required()) { <button type="button" class="btn btn-secondary sm" (click)="closed.emit()">Cancel</button> }
          @else { <button type="button" class="btn btn-ghost sm" (click)="auth.logout()">Sign out</button> }
        </div>
      </form>
    </div>
  `,
  styles: `
    .pw { margin-bottom: 18px; }
    .pw--gate { min-height: 100vh; display: grid; place-items: center; padding: 24px; background: var(--color-bg); }
    .pw-card { width: min(440px, 100%); background: var(--color-surface); border-radius: 28px; padding: 28px; display: grid; gap: 12px; box-shadow: var(--shadow-md); }
    .pw-title { font-size: 24px; margin: 0; }
    .pw-sub { font-size: 14px; line-height: 1.5; color: var(--ink-70); margin: 0 0 4px; }
    .pw-error { font-size: 14px; font-weight: 600; color: var(--color-accent-700); margin: 0; }
    .pw-actions { display: flex; gap: 8px; flex-wrap: wrap; }
    .btn.sm { font-size: 13px; padding: 8px 14px; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PasswordPanel {
  readonly auth = inject(AuthService);
  readonly required = input(false);
  readonly closed = output<void>();
  readonly current = signal('');
  readonly next = signal('');
  readonly again = signal('');
  readonly error = signal('');
  readonly busy = signal(false);
  val(e: Event): string { return (e.target as HTMLInputElement).value; }

  save(): void {
    if (this.busy()) return;
    if (this.next().length < 8) { this.error.set('Use at least 8 characters.'); return; }
    if (this.next() !== this.again()) { this.error.set('The two new passwords don’t match.'); return; }
    this.busy.set(true); this.error.set('');
    this.auth.changePassword(this.current(), this.next()).subscribe({
      next: () => { this.busy.set(false); this.closed.emit(); },
      error: (e) => { this.busy.set(false); this.error.set(e?.error?.message ?? 'Couldn’t change the password.'); },
    });
  }
}
