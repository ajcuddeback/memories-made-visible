import { Routes } from '@angular/router';

export const routes: Routes = [
	{
		path: '',
		loadComponent: () => import('./pages/home/home.page').then(m => m.HomePage),
	},
	{
		path: 'portfolio',
		loadComponent: () => import('./pages/portfolio/portfolio.page').then(m => m.PortfolioPage),
	},
	{
		path: 'services',
		loadComponent: () => import('./pages/services/services.page').then(m => m.ServicesPage),
	},
	{
		path: 'contact',
		loadComponent: () => import('./pages/contact/contact.page').then(m => m.ContactPage),
	},
	// fallback to home
	{ path: '**', redirectTo: '' },
];
