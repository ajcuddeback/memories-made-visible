import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AdminUser } from '../core/models';
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

  readonly users = signal<AdminUser[]>([]);
  readonly loginAs = signal('');
  readonly pw = signal('');
  readonly error = signal('');
  readonly busy = signal(false);

  constructor() {
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
