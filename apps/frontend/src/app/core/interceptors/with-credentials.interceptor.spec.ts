import { HttpRequest, HttpResponse } from '@angular/common/http';
import { firstValueFrom, of } from 'rxjs';
import { withCredentialsInterceptor } from './with-credentials.interceptor';

describe('withCredentialsInterceptor', () => {
  it('should send credentials with auth-related HTTP requests', async () => {
    const request = new HttpRequest('GET', '/api/auth/me');
    let forwardedWithCredentials: boolean | undefined;

    await firstValueFrom(withCredentialsInterceptor(request, (nextRequest) => {
      forwardedWithCredentials = nextRequest.withCredentials;
      return of(new HttpResponse({ status: 200 }));
    }));

    expect(forwardedWithCredentials).toBe(true);
  });
});
