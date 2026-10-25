import { Routes } from '@angular/router';

export const STUDIO_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./studio-shell').then((m) => m.StudioShell),
    title: 'Studio admin · Memories Made Visible',
    children: [
      { path: '', loadComponent: () => import('./sections/overview').then((m) => m.OverviewSection) },
      { path: 'requests', loadComponent: () => import('./sections/requests').then((m) => m.RequestsSection) },
      { path: 'calendar', loadComponent: () => import('./sections/calendar').then((m) => m.CalendarSection) },
      { path: 'popups', loadComponent: () => import('./sections/popups').then((m) => m.PopupsSection) },
      { path: 'galleries', loadComponent: () => import('./sections/galleries').then((m) => m.GalleriesSection) },
      { path: 'pricing', loadComponent: () => import('./sections/pricing-admin').then((m) => m.PricingAdminSection) },
      { path: 'photos', loadComponent: () => import('./sections/photos').then((m) => m.PhotosSection) },
      { path: '**', redirectTo: '' },
    ],
  },
];
