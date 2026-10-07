import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Auth } from './auth';

describe('Auth', () => {
  let auth: Auth;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    auth = TestBed.inject(Auth);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('signs in on this app\'s own origin and keeps who signed in and their role', async () => {
    const signedIn = auth.login('ops@seed.lemarket.com', 'Pass123!');

    // A relative URL: the staff app's proxy or Nginx forwards it to the staff gateway.
    const request = http.expectOne('/api/auth/login');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ username: 'ops@seed.lemarket.com', password: 'Pass123!' });
    request.flush({ username: 'ops', roles: ['TRADING_OPS'], message: 'Login successful' });
    await signedIn;

    expect(auth.currentUser()).toBe('ops');
    expect(auth.roles()).toEqual(['TRADING_OPS']);
    expect(auth.hasRole('TRADING_OPS')).toBe(true);
    expect(auth.hasRole('ANALYST')).toBe(false);
  });

  it('stays signed out when sign-in is refused', async () => {
    const signedIn = auth.login('ops@seed.lemarket.com', 'wrong');
    http.expectOne('/api/auth/login')
      .flush({ message: 'Invalid email or password' }, { status: 400, statusText: 'Bad Request' });

    await expect(signedIn).rejects.toBeTruthy();
    expect(auth.currentUser()).toBeNull();
    expect(auth.roles()).toEqual([]);
  });

  it('restores the session from the cookie after a reload', async () => {
    const restored = auth.restoreSession();
    const request = http.expectOne('/api/auth/me');
    expect(request.request.method).toBe('GET');
    request.flush({ username: 'analyst', roles: ['ANALYST'] });
    await restored;

    expect(auth.currentUser()).toBe('analyst');
    expect(auth.roles()).toEqual(['ANALYST']);
  });

  it('is signed out when there is no session to restore', async () => {
    auth.currentUser.set('analyst');
    auth.roles.set(['ANALYST']);

    const restored = auth.restoreSession();
    http.expectOne('/api/auth/me').flush({}, { status: 401, statusText: 'Unauthorized' });
    await restored;

    expect(auth.currentUser()).toBeNull();
    expect(auth.roles()).toEqual([]);
  });

  it('signing out clears the session', async () => {
    auth.currentUser.set('ops');
    auth.roles.set(['TRADING_OPS']);

    const signedOut = auth.logout();
    const request = http.expectOne('/api/auth/logout');
    expect(request.request.method).toBe('POST');
    request.flush({ message: 'Logged out' });
    await signedOut;

    expect(auth.currentUser()).toBeNull();
    expect(auth.roles()).toEqual([]);
  });
});
