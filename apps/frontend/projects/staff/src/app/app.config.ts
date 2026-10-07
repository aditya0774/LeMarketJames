import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';

import { routes } from './app.routes';
import { withCredentialsInterceptor } from '../../../../src/app/core/interceptors/with-credentials.interceptor';

// HttpClient with credentials interceptor: ensures httpOnly auth cookie is sent to backend.
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([withCredentialsInterceptor]))
  ]
};
