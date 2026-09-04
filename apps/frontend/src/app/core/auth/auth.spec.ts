import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { Auth } from './auth';
import { environment } from '../../../environments/environment';

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
  });

  it('should create authenticated session state on successful login', async () => {
    const loginPromise = auth.login({ username: 'alice', password: 'Pass123!' });

    const request = httpMock.expectOne(`${baseUrl}/login`);
    expect(request.request.method).toBe('POST');
    request.flush({ username: 'alice', message: 'Login successful' });

    await loginPromise;
    expect(auth.currentUser()).toBe('alice');
  });

  it('should support secure return visits by restoring session from backend cookie check', async () => {
    const restorePromise = auth.restoreSession();

    const request = httpMock.expectOne(`${baseUrl}/me`);
    expect(request.request.method).toBe('GET');
    request.flush({ username: 'alice' });

    await restorePromise;
    expect(auth.currentUser()).toBe('alice');
  });

  it('should clear session state when no valid return-visit session exists', async () => {
    const restorePromise = auth.restoreSession();

    const request = httpMock.expectOne(`${baseUrl}/me`);
    expect(request.request.method).toBe('GET');
    request.flush({}, { status: 401, statusText: 'Unauthorized' });

    await restorePromise;
    expect(auth.currentUser()).toBeNull();
  });
});
