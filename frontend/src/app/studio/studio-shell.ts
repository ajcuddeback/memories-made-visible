import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
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
  imports: [RouterOutlet, RouterLink, RouterLinkActive, StudioLogin],
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
    { path: '/studio/requests', label: 'Requests', title: 'Booking requests', exact: false },
    { path: '/studio/calendar', label: 'Calendar', title: 'Calendar', exact: false },
    { path: '/studio/popups', label: 'Pop-ups', title: 'Pop-up events', exact: false },
    { path: '/studio/galleries', label: 'Galleries', title: 'Client galleries', exact: false },
    { path: '/studio/pricing', label: 'Pricing', title: 'Pricing', exact: false },
    { path: '/studio/photos', label: 'Photos', title: 'Website photos', exact: false },
  ];

  readonly menuOpen = signal(false);
  readonly url = signal(this.router.url);
  readonly sectionTitle = computed(() => {
    const u = this.url().split('?')[0];
    return this.nav.find((n) => (n.exact ? u === n.path : u.startsWith(n.path)))?.title ?? 'Overview';
  });
  readonly pendingCount = computed(() => this.store.pending().length);
  readonly greeting = computed(() => {
    const hr = new Date().getHours();
    return (hr < 12 ? 'Good morning, ' : hr < 18 ? 'Good afternoon, ' : 'Good evening, ') + (this.auth.user()?.name ?? '');
  });

  constructor() {
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe((e) => {
      this.url.set((e as NavigationEnd).urlAfterRedirects);
      this.menuOpen.set(false);
    });
    // Load the studio data whenever a session becomes active.
    effect(() => {
      if (this.auth.isLoggedIn()) {
        if (!this.store.loaded()) this.store.load();
      } else {
        this.store.reset();
      }
    });
  }

  logOut(): void {
    this.auth.logout();
    this.menuOpen.set(false);
    this.router.navigateByUrl('/studio');
  }
}
