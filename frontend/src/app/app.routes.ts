import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: 'studio',
    // The admin is deliberately not linked from the site; it lives behind this URL only.
    loadChildren: () => import('./studio/studio.routes').then((m) => m.STUDIO_ROUTES),
  },
  {
    path: '',
    loadComponent: () => import('./site/site-shell').then((m) => m.SiteShell),
    children: [
      { path: '', loadComponent: () => import('./site/home/home').then((m) => m.HomePage), title: 'Memories Made Visible Photography' },
      { path: 'portfolio', loadComponent: () => import('./site/portfolio/portfolio').then((m) => m.PortfolioPage), title: 'Portfolio · Memories Made Visible' },
      { path: 'events', loadComponent: () => import('./site/events/events').then((m) => m.EventsPage), title: 'Pop-up events · Memories Made Visible' },
      { path: 'pricing', loadComponent: () => import('./site/pricing/pricing').then((m) => m.PricingPage), title: 'Pricing · Memories Made Visible' },
      { path: 'book', loadComponent: () => import('./site/booking/booking').then((m) => m.BookingPage), title: 'Book a session · Memories Made Visible' },
      { path: 'about', loadComponent: () => import('./site/about/about').then((m) => m.AboutPage), title: 'About · Memories Made Visible' },
    ],
  },
  { path: '**', redirectTo: '' },
];
