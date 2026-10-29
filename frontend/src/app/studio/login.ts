import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from './auth.service';

@Component({
  selector: 'app-studio-login',
  imports: [RouterLink],
  templateUrl: './login.html',
  styleUrl: './login.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StudioLogin {
  private readonly auth = inject(AuthService);

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  readonly users = signal<{ name: string; role: string; initial: string }[]>([]);
  /** 'signin' | 'forgot' | 'reset' (reset comes from the emailed link: /studio?reset=<token>) */
  readonly mode = signal<'signin' | 'forgot' | 'reset'>('signin');
  readonly email = signal('');
  readonly resetToken = signal('');
  readonly pw2 = signal('');
  readonly sent = signal(false);
  readonly loginAs = signal('');
  readonly pw = signal('');
  readonly error = signal('');
  readonly busy = signal(false);

  constructor() {
    const token = this.route.snapshot.queryParamMap.get('reset');
    if (token) { this.resetToken.set(token); this.mode.set('reset'); }
    this.auth.users().subscribe({
      next: (list) => {
        this.users.set(list);
        if (list.length && !this.loginAs()) this.loginAs.set(list[0].name);
      },
      error: () => this.error.set('The studio API isn’t reachable right now.'),
    });
  }

  onPw(e: Event): void {
    this.pw.set((e.target as HTMLInputElement).value);
    this.error.set('');
  }

  val(e: Event): string { return (e.target as HTMLInputElement).value; }

  sendReset(): void {
    if (this.busy()) return;
    if (!/\S+@\S+\.\S+/.test(this.email())) { this.error.set('Enter the email on your studio account.'); return; }
    this.busy.set(true); this.error.set('');
    this.auth.forgot(this.email().trim()).subscribe({
      next: () => { this.busy.set(false); this.sent.set(true); },
      error: () => { this.busy.set(false); this.sent.set(true); }, // same answer either way; the API never says who has an account
    });
  }

  finishReset(): void {
    if (this.busy()) return;
    if (this.pw().length < 8) { this.error.set('Use at least 8 characters.'); return; }
    if (this.pw() !== this.pw2()) { this.error.set('The two passwords don’t match.'); return; }
    this.busy.set(true); this.error.set('');
    this.auth.reset(this.resetToken(), this.pw()).subscribe({
      next: () => { this.busy.set(false); this.router.navigateByUrl('/studio'); },
      error: (e) => { this.busy.set(false); this.error.set(e?.error?.message ?? 'That link didn’t work. Ask for a new one.'); },
    });
  }

  signIn(): void {
    if (this.busy()) return;
    if (!this.pw().trim()) {
      this.error.set('Enter your password to continue.');
      return;
    }
    this.busy.set(true);
    this.auth.login(this.loginAs(), this.pw()).subscribe({
      next: () => {
        this.busy.set(false);
        this.pw.set('');
      },
      error: (err) => {
        this.busy.set(false);
        this.error.set(err?.error?.message ?? 'Sign-in failed. Please try again.');
      },
    });
  }
}
