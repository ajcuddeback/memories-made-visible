import { Routes } from '@angular/router';
import { SeoData } from './core/seo.service';
import { HomePage } from './site/home/home';
import { SiteShell } from './site/site-shell';

const seo = (title: string, description: string, image?: string): { seo: SeoData } => ({ seo: { title, description, image } });

export const routes: Routes = [
  {
    path: 'studio',
    // The admin is deliberately not linked from the site; it lives behind this URL only.
    loadChildren: () => import('./studio/studio.routes').then((m) => m.STUDIO_ROUTES),
  },
  {
    path: '',
    // The shell and the landing page ship in the main bundle: two fewer round trips before the home hero can render.
    component: SiteShell,
    children: [
      {
        path: '',
        component: HomePage,
        data: seo(
          'Memories Made Visible | Photographer in Winter Haven & Polk County, FL',
          'Family-run photography studio in Winter Haven, Florida. Wedding, family, couples, maternity, newborn, senior and event photography across Polk County. Book a session online.',
        ),
      },
      {
        path: 'portfolio',
        loadComponent: () => import('./site/portfolio/portfolio').then((m) => m.PortfolioPage),
        data: seo(
          'Portfolio | Memories Made Visible Photography, Winter Haven FL',
          'Recent wedding, family, couples, maternity, newborn and senior sessions photographed around Winter Haven, Lakeland and the rest of Polk County, Florida.',
        ),
      },
      {
        path: 'events',
        loadComponent: () => import('./site/events/events').then((m) => m.EventsPage),
        data: seo(
          'Pop-Up Photo Events at Polk County Markets | Memories Made Visible',
          'Themed photo sets with on-site printing at markets and community events around Winter Haven, Lakeland and Polk County. See upcoming pop-ups or invite us to yours.',
        ),
      },
      {
        path: 'pricing',
        loadComponent: () => import('./site/pricing/pricing').then((m) => m.PricingPage),
        data: seo(
          'Photography Pricing & Packages | Memories Made Visible, Winter Haven FL',
          'Straightforward photography packages for weddings, families, couples, maternity, newborns, seniors and events. Every session includes a private online gallery and travel within Polk County.',
        ),
      },
      {
        path: 'book',
        loadComponent: () => import('./site/booking/booking').then((m) => m.BookingPage),
        data: seo(
          'Book a Photo Session in Winter Haven, FL | Memories Made Visible',
          'Request a photography session with Memories Made Visible. Pick a session type, date and time — we confirm availability personally. Serving Winter Haven and all of Polk County.',
        ),
      },
      {
        path: 'about',
        loadComponent: () => import('./site/about/about').then((m) => m.AboutPage),
        data: seo(
          'About Erica & Austin Cuddeback | Memories Made Visible Photography',
          'Meet the husband-and-wife team behind Memories Made Visible, a family photography studio based in Winter Haven, Florida and serving Polk County.',
        ),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
