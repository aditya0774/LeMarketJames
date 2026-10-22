import { TestBed } from '@angular/core/testing';
import { SESSION_END_MESSAGES } from '@lemarket/shared-ui';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Auth } from '../../../core/auth/auth';
import { ACCOUNT_INACTIVE_MESSAGE, Login } from './login';

describe('Staff login', () => {
  const failureMessages: Record<number, string> = {
    0: 'Staff login is unavailable.',
    500: 'Staff login is unavailable.',
    502: 'Staff login is unavailable.',
    401: 'Invalid email or password.',
    // A refusal that carries no message of its own falls back to the generic text.
    400: 'Unable to log in. Please try again.',
  };
  for (const status of [0, 500, 502, 401, 400]) {
    it('explains login failure for HTTP ' + status, async () => {
      await TestBed.configureTestingModule({
        imports: [Login], providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
      }).compileComponents();
      const fixture = TestBed.createComponent(Login);
      await fixture.whenStable();
      for (const [selector, value] of [['#username', 'ops@seed.lemarket.com'], ['#password', 'Pass123!']]) {
        const input: HTMLInputElement = fixture.nativeElement.querySelector(selector);
        input.value = value; input.dispatchEvent(new Event('input'));
      }
      const submitted = fixture.componentInstance.submit();
      const http = TestBed.inject(HttpTestingController);
      const request = http.expectOne('/api/auth/login');
      if (status === 0) request.error(new ProgressEvent('error'));
      else request.flush('', { status, statusText: 'Login failed' });
      await submitted;
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('.alert').textContent).toContain(failureMessages[status]);
      expect(fixture.nativeElement.querySelector('.alert b').textContent).toBe('Login failed');
      http.verify();
    });
  }
  // Each role lands on its own dashboard; a staff role without a section (COMPLIANCE) stays
  // signed in and lands on Access denied.
  for (const [role, destination] of [['TRADING_OPS', '/trade-search'], ['ANALYST', '/analyst'], ['COMPLIANCE', '/access-denied']]) {
    it('routes ' + role + ' after authenticating on the staff origin', async () => {
      await TestBed.configureTestingModule({
        imports: [Login], providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
      }).compileComponents();
      const fixture = TestBed.createComponent(Login);
      let commands: readonly unknown[] = [];
      TestBed.inject(Router).navigate = async value => { commands = value; return true; };
      await fixture.whenStable();
      for (const [selector, value] of [['#username', 'ops@seed.lemarket.com'], ['#password', 'Pass123!']]) {
        const input: HTMLInputElement = fixture.nativeElement.querySelector(selector);
        input.value = value; input.dispatchEvent(new Event('input'));
      }
      const submitted = fixture.componentInstance.submit();
      const http = TestBed.inject(HttpTestingController);
      const request = http.expectOne('/api/auth/login');
      expect(request.request.body).toEqual({ username: 'ops@seed.lemarket.com', password: 'Pass123!' });
      request.flush({ username: 'staff', roles: [role] });
      await submitted;
      expect(commands).toEqual([destination]);
      http.verify();
    });
  }
  it('signs a CLIENT straight out and says the login is for staff', async () => {
    await TestBed.configureTestingModule({
      imports: [Login], providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    const fixture = TestBed.createComponent(Login);
    let navigated = false;
    TestBed.inject(Router).navigate = async () => { navigated = true; return true; };
    await fixture.whenStable();
    for (const [selector, value] of [['#username', 'seed_active@seed.lemarket.com'], ['#password', 'Pass123!']]) {
      const input: HTMLInputElement = fixture.nativeElement.querySelector(selector);
      input.value = value; input.dispatchEvent(new Event('input'));
    }
    const submitted = fixture.componentInstance.submit();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/auth/login').flush({ username: 'seed_active', roles: ['CLIENT'], accountId: 7 });
    // The sign-out is only sent once the sign-in has been read, a turn of the event loop later.
    await new Promise(resolve => setTimeout(resolve));
    const logout = http.expectOne('/api/auth/logout');
    expect(logout.request.method).toBe('POST');
    logout.flush({ message: 'Logged out' });
    await submitted;
    fixture.detectChanges();
    expect(TestBed.inject(Auth).currentUser()).toBeNull();
    expect(TestBed.inject(Auth).roles()).toEqual([]);
    expect(navigated).toBe(false);
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('This login is for staff only');
    expect(fixture.nativeElement.querySelector('[role="alert"]').classList).toContain('info');
    http.verify();
  });

  it('shows the lockout duration from the server', async () => {
    await TestBed.configureTestingModule({
      imports: [Login], providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    const fixture = TestBed.createComponent(Login);
    await fixture.whenStable();
    for (const [selector, value] of [['#username', 'ops@seed.lemarket.com'], ['#password', 'Pass123!']]) {
      const input: HTMLInputElement = fixture.nativeElement.querySelector(selector);
      input.value = value; input.dispatchEvent(new Event('input'));
    }

    const submitted = fixture.componentInstance.submit();
    const http = TestBed.inject(HttpTestingController);
    const request = http.expectOne('/api/auth/login');
    request.flush({ message: 'Account locked for 15 minutes.' }, { status: 400, statusText: 'Login failed' });
    await submitted;
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent)
      .toContain('Account locked for 15 minutes.');
    // A lockout is a warning, not a failure: the account is fine, it just has to wait.
    expect(fixture.nativeElement.querySelector('[role="alert"] b').textContent).toBe('Account locked');
    expect(fixture.nativeElement.querySelector('[role="alert"]').classList).toContain('warn');
    http.verify();
  });

  // LMKT-56: a deactivated staff login gets a fixed message, chosen by the code, not the text.
  it('shows the fixed message when the account is no longer active', async () => {
    await TestBed.configureTestingModule({
      imports: [Login], providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    const fixture = TestBed.createComponent(Login);
    let navigated = false;
    TestBed.inject(Router).navigate = async () => { navigated = true; return true; };
    await fixture.whenStable();
    for (const [selector, value] of [['#username', 'ops@seed.lemarket.com'], ['#password', 'Pass123!']]) {
      const input: HTMLInputElement = fixture.nativeElement.querySelector(selector);
      input.value = value; input.dispatchEvent(new Event('input'));
    }

    const submitted = fixture.componentInstance.submit();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/auth/login').flush(
      { success: false, code: 'ACCOUNT_INACTIVE', error: 'server text', message: 'server text' },
      { status: 403, statusText: 'Forbidden' },
    );
    await submitted;
    fixture.detectChanges();

    const alert: HTMLElement = fixture.nativeElement.querySelector('[role="alert"]');
    expect(alert.querySelector('b')!.textContent).toBe('Account not active');
    expect(alert.textContent).toContain(ACCOUNT_INACTIVE_MESSAGE);
    expect(alert.textContent).not.toContain('server text');
    expect(navigated).toBe(false);
    http.verify();
  });

  // BR-03: the inactivity timeout signs an idle session out to ?reason=timeout, and one that hit
  // its time limit to ?reason=expired.
  for (const reason of ['timeout', 'expired'] as const) {
    it('says why the session ended for ?reason=' + reason, async () => {
      await TestBed.configureTestingModule({
        providers: [provideRouter([{ path: 'login', component: Login }]), provideHttpClient(), provideHttpClientTesting()],
      }).compileComponents();
      const harness = await RouterTestingHarness.create();
      await harness.navigateByUrl('/login?reason=' + reason, Login);

      const alert = harness.routeNativeElement!.querySelector('[role="alert"]')!;
      expect(alert.querySelector('b')!.textContent).toBe(SESSION_END_MESSAGES[reason].heading);
      expect(alert.textContent).toContain(SESSION_END_MESSAGES[reason].message);
    });
  }

  // LMKT-56: accountInactiveInterceptor sends a cut-off session here with ?reason=inactive.
  for (const [url, shown] of [['/login?reason=inactive', true], ['/login', false]] as const) {
    it((shown ? 'shows' : 'shows no') + ' account-not-active message on load at ' + url, async () => {
      await TestBed.configureTestingModule({
        providers: [provideRouter([{ path: 'login', component: Login }]), provideHttpClient(), provideHttpClientTesting()],
      }).compileComponents();
      const harness = await RouterTestingHarness.create();
      await harness.navigateByUrl(url, Login);

      const alert: HTMLElement | null = harness.routeNativeElement!.querySelector('[role="alert"]');
      if (shown) {
        expect(alert!.querySelector('b')!.textContent).toBe('Account not active');
        expect(alert!.textContent).toContain(ACCOUNT_INACTIVE_MESSAGE);
      } else {
        expect(alert).toBeNull();
      }
      TestBed.inject(HttpTestingController).verify();
    });
  }
});
