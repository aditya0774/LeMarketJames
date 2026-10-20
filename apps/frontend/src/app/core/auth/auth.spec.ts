import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { Auth } from './auth';
import { environment } from '../../../environments/environment';
import { expect, vi } from 'vitest';

describe('Auth', () => {
  let auth: Auth;
  let httpMock: HttpTestingController;
  const baseUrl = `${environment.apiBaseUrl}/api/auth`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    auth = TestBed.inject(Auth);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should reject authentication for invalid credentials', async () => {
    const loginPromise = auth.login({ username: 'alice', password: 'WrongPass!' });

    const request = httpMock.expectOne(`${baseUrl}/login`);
    expect(request.request.method).toBe('POST');
    request.flush({ message: 'Invalid username or password' }, { status: 400, statusText: 'Bad Request' });

    let rejected = false;
    try {
      await loginPromise;
    } catch {
      rejected = true;
    }

    expect(rejected).toBeTruthy();
    expect(auth.currentUser()).toBeNull();
    expect(auth.currentAccountId()).toBeNull();
  });

  it('should create authenticated session state on successful login', async () => {
    const loginPromise = auth.login({ username: 'alice', password: 'Pass123!' });

    const request = httpMock.expectOne(`${baseUrl}/login`);
    expect(request.request.method).toBe('POST');
    request.flush({ username: 'alice', message: 'Login successful', accountId: 7 });

    await loginPromise;
    expect(auth.currentUser()).toBe('alice');
    expect(auth.currentAccountId()).toBe(7);
  });

  it('should support secure return visits by restoring session from backend cookie check', async () => {
    const restorePromise = auth.restoreSession();

    const request = httpMock.expectOne(`${baseUrl}/me`);
    expect(request.request.method).toBe('GET');
    request.flush({ username: 'alice', accountId: 7 });

    await restorePromise;
    expect(auth.currentUser()).toBe('alice');
    expect(auth.currentAccountId()).toBe(7);
  });

  it('should clear session state when no valid return-visit session exists', async () => {
    const restorePromise = auth.restoreSession();

    const request = httpMock.expectOne(`${baseUrl}/me`);
    expect(request.request.method).toBe('GET');
    request.flush({}, { status: 401, statusText: 'Unauthorized' });

    await restorePromise;
    expect(auth.currentUser()).toBeNull();
    expect(auth.currentAccountId()).toBeNull();
  });
  it('clears a previous account when a session response has no account ID', async () => {
    auth.currentAccountId.set(7);
    const restored = auth.restoreSession();
    httpMock.expectOne(`${baseUrl}/me`).flush({ username: 'bob' });
    await restored;
    expect(auth.currentAccountId()).toBeNull();
  });

  it('keeps the session roles, so staff logins (no account) are recognised', async () => {
    const restored = auth.restoreSession();
    httpMock.expectOne(`${baseUrl}/me`).flush({ username: 'olivia_ops', roles: ['TRADING_OPS'] });
    await restored;

    expect(auth.roles()).toEqual(['TRADING_OPS']);
    expect(auth.hasRole('TRADING_OPS')).toBe(true);
    expect(auth.hasRole('CLIENT')).toBe(false);
    expect(auth.currentAccountId()).toBeNull();

    const loggedOut = auth.logout();
    httpMock.expectOne(`${baseUrl}/logout`).flush({});
    await loggedOut;
    expect(auth.roles()).toEqual([]);
  });

  it('clears all session state when logout succeeds', async () => {
    const loggedIn = auth.login({ username: 'alice', password: 'Pass123!' });
    httpMock.expectOne(`${baseUrl}/login`).flush({
      username: 'alice', roles: ['CLIENT'], accountId: 7
    });
    await loggedIn;

    const loggedOut = auth.logout();
    const request = httpMock.expectOne(`${baseUrl}/logout`);
    expect(request.request.method).toBe('POST');
    request.flush({ message: 'Logged out' });
    await loggedOut;

    expect(auth.currentUser()).toBeNull();
    expect(auth.currentAccountId()).toBeNull();
    expect(auth.roles()).toEqual([]);
  });

  it('clears all session state when logout fails', async () => {
    const loggedIn = auth.login({ username: 'alice', password: 'Pass123!' });
    httpMock.expectOne(`${baseUrl}/login`).flush({
      username: 'alice', roles: ['CLIENT'], accountId: 7
    });
    await loggedIn;

    const loggedOut = auth.logout();
    httpMock.expectOne(`${baseUrl}/logout`).flush({}, {
      status: 500, statusText: 'Server Error'
    });

    await expect(loggedOut).rejects.toThrow();
    expect(auth.currentUser()).toBeNull();
    expect(auth.currentAccountId()).toBeNull();
    expect(auth.roles()).toEqual([]);
  });

  // LMKT-56: the server already ended the session, so nothing is sent (verify() checks it).
  it('clears the session without calling the server', async () => {
    const loggedIn = auth.login({ username: 'alice', password: 'Pass123!' });
    httpMock.expectOne(`${baseUrl}/login`).flush({ username: 'alice', roles: ['CLIENT'], accountId: 7 });
    await loggedIn;

    auth.clearSession();

    expect(auth.currentUser()).toBeNull();
    expect(auth.currentAccountId()).toBeNull();
    expect(auth.roles()).toEqual([]);
  });

});
