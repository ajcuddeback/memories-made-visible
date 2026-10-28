import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { tap } from 'rxjs';
import { AdminUser, LoginResponse } from '../core/models';

const KEY = 'mmv-admin-session';

interface StoredSession {
  token: string;
  user: AdminUser;
  expiresAt: number;
}

/** Admin sign-in state. The token lives in sessionStorage so closing the tab signs you out. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly session = signal<StoredSession | null>(this.read());

  readonly user = computed(() => this.session()?.user ?? null);
  readonly token = computed(() => this.session()?.token ?? null);
  readonly isLoggedIn = computed(() => !!this.session());
  /** Owner or admin: may change prices, photos, publish, see revenue and insights, manage the team. */
  readonly canManage = computed(() => { const a = this.user()?.access; return a === 'OWNER' || a === 'ADMIN'; });
  readonly isOwner = computed(() => this.user()?.access === 'OWNER');
  readonly mustChangePassword = computed(() => !!this.user()?.mustChangePassword);

  users() {
    return this.http.get<{ name: string; role: string; initial: string }[]>('/api/admin/auth/users');
  }

  login(name: string, password: string) {
    return this.http.post<LoginResponse>('/api/admin/auth/login', { name, password }).pipe(
      tap((res) => {
        const s: StoredSession = { token: res.token, user: res.user, expiresAt: Date.now() + res.expiresInSeconds * 1000 };
        this.session.set(s);
        try {
          sessionStorage.setItem(KEY, JSON.stringify(s));
        } catch {
          /* ignore */
        }
      }),
    );
  }

  private store(res: LoginResponse): void {
    const s: StoredSession = { token: res.token, user: res.user, expiresAt: Date.now() + res.expiresInSeconds * 1000 };
    this.session.set(s);
    try { sessionStorage.setItem(KEY, JSON.stringify(s)); } catch { /* ignore */ }
  }

  /** Changing the password revokes every token issued before it; the API hands back a fresh one for this session. */
  changePassword(current: string, next: string) {
    return this.http.post<LoginResponse>('/api/admin/auth/password', { current, next }).pipe(tap((res) => this.store(res)));
  }
  forgot(email: string) { return this.http.post<void>('/api/admin/auth/forgot', { email }); }
  /** Finish a reset from the emailed link; signs the person in. */
  reset(token: string, password: string) {
    return this.http.post<LoginResponse>('/api/admin/auth/reset', { token, password }).pipe(tap((res) => this.store(res)));
  }

  logout(): void {
    this.session.set(null);
    try {
      sessionStorage.removeItem(KEY);
    } catch {
      /* ignore */
    }
  }

  private read(): StoredSession | null {
    try {
      const raw = sessionStorage.getItem(KEY);
      if (!raw) return null;
      const s = JSON.parse(raw) as StoredSession;
      return s.expiresAt > Date.now() ? s : null;
    } catch {
      return null;
    }
  }
}
