import { ApplicationConfig, provideBrowserGlobalErrorListeners, provideAppInitializer, inject } from '@angular/core';
import { provideRouter, withInMemoryScrolling } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';

import { Auth } from './core/auth/auth';
import { Theme } from '@lemarket/shared-ui';
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
    // anchorScrolling makes a link like /learn#orders scroll to that element.
    provideRouter(routes, withInMemoryScrolling({ anchorScrolling: 'enabled' })),
    provideHttpClient(withInterceptors([withCredentialsInterceptor, accountInactiveInterceptor]))
  ]
};
