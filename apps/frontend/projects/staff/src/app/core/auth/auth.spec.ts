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

    // A relative URL: the staff app's proxy or Nginx forwards it to the services.
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

  // LMKT-144: staff sessions carry the staff member's own email and full name.
  it('keeps the staff email and full name from sign-in and from a restored session', async () => {
    const signedIn = auth.login('ops@seed.lemarket.com', 'Pass123!');
    http.expectOne('/api/auth/login').flush({
      username: 'seed_ops', roles: ['TRADING_OPS'], email: 'ops@seed.lemarket.com', fullName: 'Olivia Ops',
      message: 'Login successful',
    });
    await signedIn;
    expect(auth.email()).toBe('ops@seed.lemarket.com');
    expect(auth.fullName()).toBe('Olivia Ops');

    const restored = auth.restoreSession();
    http.expectOne('/api/auth/me').flush({
      username: 'seed_analyst', roles: ['ANALYST'], email: 'analyst@seed.lemarket.com', fullName: 'Andy Analyst',
    });
    await restored;
    expect(auth.email()).toBe('analyst@seed.lemarket.com');
    expect(auth.fullName()).toBe('Andy Analyst');
  });

  it('forgets the staff email and full name on sign-out, and leaves them empty when not sent', async () => {
    auth.email.set('ops@seed.lemarket.com');
    auth.fullName.set('Olivia Ops');
    const signedOut = auth.logout();
    http.expectOne('/api/auth/logout').flush({ message: 'Logged out' });
    await signedOut;
    expect(auth.email()).toBeNull();
    expect(auth.fullName()).toBeNull();

    const restored = auth.restoreSession();
    http.expectOne('/api/auth/me').flush({ username: 'seed_ops', roles: ['TRADING_OPS'] });
    await restored;
    expect(auth.email()).toBeNull();
    expect(auth.fullName()).toBeNull();
  });

  // LMKT-56: the server already ended the session, so nothing is sent (verify() checks it).
  it('clears the session without calling the server', () => {
    auth.currentUser.set('ops');
    auth.roles.set(['TRADING_OPS']);

    auth.clearSession();

    expect(auth.currentUser()).toBeNull();
    expect(auth.roles()).toEqual([]);
  });

  // BR-03: the inactivity timeout starts from what sign-in or /me says the session has left.
  it('keeps the session timing from sign-in and extends it through this app\'s origin', async () => {
    const signedIn = auth.login('ops@seed.lemarket.com', 'Pass123!');
    http.expectOne('/api/auth/login').flush({
      username: 'ops', roles: ['TRADING_OPS'], message: 'Login successful', expiresInSeconds: 60, inactivityTimeoutSeconds: 60,
    });
    await signedIn;
    expect(auth.sessionLease()).toEqual({ expiresInSeconds: 60, inactivityTimeoutSeconds: 60 });

    const refreshed = auth.refresh();
    const request = http.expectOne('/api/auth/refresh');
    expect(request.request.method).toBe('POST');
    request.flush({ expiresInSeconds: 60, inactivityTimeoutSeconds: 60 });
    expect(await refreshed).toEqual({ expiresInSeconds: 60, inactivityTimeoutSeconds: 60 });

    auth.clearSession();
    expect(auth.sessionLease()).toBeNull();
  });

});
