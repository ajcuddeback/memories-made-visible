import { Routes } from '@angular/router';

export const STUDIO_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./studio-shell').then((m) => m.StudioShell),
    title: 'Studio admin · Memories Made Visible',
    data: { seo: { title: 'Studio admin · Memories Made Visible', description: '', noindex: true } },
    children: [
      { path: '', loadComponent: () => import('./sections/overview').then((m) => m.OverviewSection) },
      { path: 'sessions', loadComponent: () => import('./sections/sessions').then((m) => m.SessionsSection) },
      { path: 'requests', redirectTo: 'sessions' },
      { path: 'galleries', redirectTo: 'sessions' },
      { path: 'calendar', loadComponent: () => import('./sections/calendar').then((m) => m.CalendarSection) },
      { path: 'popups', loadComponent: () => import('./sections/popups').then((m) => m.PopupsSection) },
      { path: 'pricing', loadComponent: () => import('./sections/pricing-admin').then((m) => m.PricingAdminSection) },
      { path: 'photos', loadComponent: () => import('./sections/photos').then((m) => m.PhotosSection) },
      { path: 'insights', loadComponent: () => import('./sections/insights').then((m) => m.InsightsSection) },
      { path: 'team', loadComponent: () => import('./sections/team').then((m) => m.TeamSection) },
      { path: '**', redirectTo: '' },
    ],
  },
];
