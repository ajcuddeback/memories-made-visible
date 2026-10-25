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

  users() {
    return this.http.get<AdminUser[]>('/api/admin/auth/users');
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
