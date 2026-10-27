import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { ThemeService } from '../core/theme.service';
import { SiteDataService } from '../core/site-data.service';
import { Icon } from '../shared/icons';

interface NavItem {
  path: string;
  label: string;
}

@Component({
  selector: 'app-site-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Icon],
  templateUrl: './site-shell.html',
  styleUrl: './site-shell.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SiteShell {
  readonly theme = inject(ThemeService);
  private readonly router = inject(Router);
  private readonly data = inject(SiteDataService);

  readonly nav: NavItem[] = [
    { path: '/portfolio', label: 'Portfolio' },
    { path: '/events', label: 'Events' },
    { path: '/pricing', label: 'Pricing' },
    { path: '/about', label: 'About' },
  ];
  readonly mobileNav: NavItem[] = [{ path: '/', label: 'Home' }, ...this.nav];
  readonly footerLinks: NavItem[] = [{ path: '/', label: 'Home' }, ...this.nav, { path: '/book', label: 'Book' }];

  readonly menuOpen = signal(false);
  readonly year = new Date().getFullYear();
  readonly contact = this.data.contact;

  constructor() {
    // Warm the caches every page shares so navigation feels instant.
    this.data.loadPhotos();
    this.data.loadPricing();
    this.data.loadContact();
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe(() => this.menuOpen.set(false));
  }

  toggleMenu(): void {
    this.menuOpen.update((v) => !v);
  }
}
