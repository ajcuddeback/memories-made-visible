import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/** Attaches the admin token to /api/admin calls and signs out on a 401 (expired token). */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.token();
  const isAdminCall = req.url.startsWith('/api/admin/') && !req.url.startsWith('/api/admin/auth/login');
  const authed = token && isAdminCall ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
  return next(authed).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse && err.status === 401 && isAdminCall) {
        auth.logout();
      }
      return throwError(() => err);
    }),
  );
};
