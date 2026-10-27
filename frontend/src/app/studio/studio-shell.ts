import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { ThemeService } from '../core/theme.service';
import { ViewportService } from '../core/viewport.service';
import { AdminStore } from './admin-store.service';
import { AuthService } from './auth.service';
import { StudioLogin } from './login';

interface NavItem {
  path: string;
  label: string;
  title: string;
  exact: boolean;
}

@Component({
  selector: 'app-studio-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, StudioLogin, DatePipe],
  templateUrl: './studio-shell.html',
  styleUrl: './studio-shell.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StudioShell {
  readonly auth = inject(AuthService);
  readonly store = inject(AdminStore);
  readonly theme = inject(ThemeService);
  readonly viewport = inject(ViewportService);
  private readonly router = inject(Router);

  readonly nav: NavItem[] = [
    { path: '/studio', label: 'Overview', title: 'Overview', exact: true },
    { path: '/studio/sessions', label: 'Sessions', title: 'Sessions', exact: false },
    { path: '/studio/calendar', label: 'Calendar', title: 'Calendar', exact: false },
    { path: '/studio/popups', label: 'Pop-ups', title: 'Pop-up events', exact: false },
    { path: '/studio/pricing', label: 'Pricing', title: 'Pricing', exact: false },
    { path: '/studio/photos', label: 'Photos', title: 'Website photos', exact: false },
    { path: '/studio/insights', label: 'Insights', title: 'Site insights', exact: false },
  ];

  readonly menuOpen = signal(false);
  readonly url = signal(this.router.url);
  readonly sectionTitle = computed(() => {
    const u = this.url().split('?')[0];
    return this.nav.find((n) => (n.exact ? u === n.path : u.startsWith(n.path)))?.title ?? 'Overview';
  });
  readonly pendingCount = computed(() => this.store.pending().length);
  readonly publishMsg = signal('');
  readonly unpublished = computed(() => {
    const s = this.store.publishStatus();
    if (!s?.dirty) return [] as string[];
    const out: string[] = [];
    if (s.changes.pricing) out.push('pricing');
    if (s.changes.events) out.push('pop-ups');
    if (s.changes.photos) out.push('photos');
    return out;
  });
  readonly unpublishedLabel = computed(() => {
    const u = this.unpublished();
    return u.length ? `Unpublished changes: ${u.join(', ')}` : '';
  });
  readonly greeting = computed(() => {
    const hr = new Date().getHours();
    return (hr < 12 ? 'Good morning, ' : hr < 18 ? 'Good afternoon, ' : 'Good evening, ') + (this.auth.user()?.name ?? '');
  });

  constructor() {
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe((e) => {
      this.url.set((e as NavigationEnd).urlAfterRedirects);
      this.menuOpen.set(false);
    });
    // Fetch fresh studio data every time the admin opens (sign-in or return from the site);
    // the store is only a cache of API responses, never a source of data.
    effect(() => {
      if (this.auth.isLoggedIn()) {
        this.store.load();
      } else {
        this.store.reset();
      }
    });
  }

  publish(): void {
    if (this.store.publishing()) return;
    this.publishMsg.set('');
    this.store.publish().subscribe({
      next: () => this.publishMsg.set('Published — the site is up to date.'),
      error: () => this.publishMsg.set('Publishing failed. Please try again.'),
    });
  }

  discard(): void {
    if (this.store.publishing()) return;
    const u = this.unpublished();
    if (!confirm(`Throw away the unpublished ${u.join(', ')} changes and go back to what's live? This can't be undone.`)) return;
    this.publishMsg.set('');
    this.store.discard().subscribe({
      next: () => this.publishMsg.set('Draft discarded — the studio matches the live site again.'),
      error: () => this.publishMsg.set('Couldn’t discard the draft. Please try again.'),
    });
  }

  logOut(): void {
    this.auth.logout();
    this.menuOpen.set(false);
    this.router.navigateByUrl('/studio');
  }
}
