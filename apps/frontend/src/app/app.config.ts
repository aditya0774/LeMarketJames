import { ApplicationConfig, provideBrowserGlobalErrorListeners, provideAppInitializer, inject } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';

import { Auth } from './core/auth/auth';
import { SESSION_KEEPER, Theme } from '@lemarket/shared-ui';
import { AuthSessionKeeper } from './core/auth/auth-session-keeper';
import { routes } from './app.routes';
import { withCredentialsInterceptor } from './core/interceptors/with-credentials.interceptor';
import { accountInactiveInterceptor } from './core/interceptors/account-inactive.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideAppInitializer(() => inject(Auth).restoreSession()),
    // Created up front so the saved theme is applied before the first page renders.
    provideAppInitializer(() => {
      inject(Theme);
    }),
    provideRouter(routes),
    // What the signed-in layout's inactivity timeout (BR-03) calls to extend or end the session.
    { provide: SESSION_KEEPER, useExisting: AuthSessionKeeper },
    provideHttpClient(withInterceptors([withCredentialsInterceptor, accountInactiveInterceptor]))
  ]
};
