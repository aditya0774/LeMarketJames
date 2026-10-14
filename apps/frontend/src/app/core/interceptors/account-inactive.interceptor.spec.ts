import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { Auth } from '../auth/auth';
import { accountInactiveInterceptor } from './account-inactive.interceptor';

describe('accountInactiveInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let auth: Auth;
  let router: Router;
  let navigations: unknown[][];

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'login', children: [] }, { path: '**', children: [] }]),
        provideHttpClient(withInterceptors([accountInactiveInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(Auth);
    router = TestBed.inject(Router);

    // A signed-in client in the middle of using the app.
    await router.navigateByUrl('/portfolio');
    const signedIn = auth.login({ username: 'alice', password: 'Pass123!' });
    httpMock.expectOne('/api/auth/login').flush({ username: 'alice', roles: ['CLIENT'], accountId: 7 });
    await signedIn;

    navigations = [];
    const navigate = router.navigate.bind(router);
    router.navigate = (...args: Parameters<Router['navigate']>) => {
      navigations.push(args);
      return navigate(...args);
    };
  });

  afterEach(() => httpMock.verify());

  /** Sends a GET and answers it with the given error; resolves to the error the caller sees. */
  function fail(url: string, body: unknown, status: number): Promise<unknown> {
    const result = firstValueFrom(http.get(url)).then(() => null, error => error);
    httpMock.expectOne(url).flush(body, { status, statusText: 'Error' });
    return result;
  }

  it('clears the session and redirects to the login page once for a burst of 401 ACCOUNT_INACTIVE', async () => {
    const inactive = { success: false, code: 'ACCOUNT_INACTIVE', error: 'gone' };
    const errors = await Promise.all([
      fail('/api/v1/portfolio', inactive, 401),
      fail('/api/v1/holdings', inactive, 401),
      fail('/api/v1/orders', inactive, 401),
    ]);
    await new Promise(resolve => setTimeout(resolve));

    expect(errors.every(error => (error as { status: number }).status === 401)).toBe(true);
    expect(auth.currentUser()).toBeNull();
    expect(auth.currentAccountId()).toBeNull();
    expect(auth.roles()).toEqual([]);
    expect(navigations).toEqual([[['/login'], { queryParams: { reason: 'inactive' } }]]);
    expect(router.url).toBe('/login?reason=inactive');

    // A request still in flight when the redirect lands doesn't send the user there again.
    await fail('/api/v1/trades', inactive, 401);
    expect(navigations.length).toBe(1);
  });

  for (const [label, body, status] of [
    ['a plain 401', { success: false, error: 'Unauthorized' }, 401],
    ['a 403 without the code', { success: false, error: 'Forbidden' }, 403],
    ['a 403 ACCOUNT_INACTIVE from sign-in', { success: false, code: 'ACCOUNT_INACTIVE', error: 'gone' }, 403],
  ] as const) {
    it('passes ' + label + ' through untouched', async () => {
      const error = await fail('/api/v1/portfolio', body, status);

      expect((error as { status: number }).status).toBe(status);
      expect(auth.currentUser()).toBe('alice');
      expect(auth.roles()).toEqual(['CLIENT']);
      expect(navigations).toEqual([]);
      expect(router.url).toBe('/portfolio');
    });
  }
});
