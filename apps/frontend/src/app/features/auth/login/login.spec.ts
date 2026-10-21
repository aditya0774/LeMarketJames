import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Auth } from '../../../core/auth/auth';
import { ACCOUNT_INACTIVE_MESSAGE, Login } from './login';
import { SESSION_END_MESSAGES } from '@lemarket/shared-ui';

describe('Login', () => {
  let component: Login;
  let fixture: ComponentFixture<Login>;
  let authMock: { hasRole: (role: string) => boolean; login: (request: { username: string; password: string }) => Promise<{ username: string; message: string }>; logout: () => Promise<void> };
  let loginCalls: Array<{ username: string; password: string }>;
  let logoutCalls = 0;
  let router: Router;
  let navigateCalls = 0;
  let navigateArgs: unknown[] = [];

  beforeEach(async () => {
    loginCalls = [];
    logoutCalls = 0;
    authMock = {
      hasRole: () => false,
      login: async (request) => {
        loginCalls.push(request);
        return { username: request.username, message: 'Login successful' };
      },
      logout: async () => {
        logoutCalls += 1;
      },
    };

    TestBed.resetTestingModule();

    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: Auth, useValue: authMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Login);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    navigateCalls = 0;
    navigateArgs = [];
    router.navigate = async (...commands: unknown[]) => {
      navigateCalls += 1;
      navigateArgs = commands;
      return true;
    };
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should not submit when form is invalid', async () => {
    await component.submit();

    expect(loginCalls.length).toBe(0);
    expect((component as any).form.get('username')?.touched).toBeTruthy();
    expect((component as any).form.get('password')?.touched).toBeTruthy();
  });

  it('should authenticate registered user and navigate to dashboard', async () => {
    (component as any).form.setValue({ username: 'alice@example.com', password: 'Pass123!' });
    await component.submit();

    expect(loginCalls).toEqual([{ username: 'alice@example.com', password: 'Pass123!' }]);
    expect(navigateCalls).toBe(1);
    expect(navigateArgs).toEqual([['/dashboard']]);
    expect((component as any).errorMessage()).toBeNull();
  });

  // The trading app is customer-only (LMKT-145): trade search moved to the staff app.
  it('signs Operations out again and points them to the staff app', async () => {
    authMock.hasRole = role => role === 'TRADING_OPS';
    (component as any).form.setValue({ username: 'ops@seed.lemarket.com', password: 'Pass123!' });
    await component.submit();
    expect(logoutCalls).toBe(1);
    expect(navigateCalls).toBe(0);
    expect((component as any).errorMessage()).toBe('Please use the staff app for operations.');
    fixture.detectChanges();
    const alert: HTMLElement = fixture.nativeElement.querySelector('[role="alert"]');
    expect(alert.querySelector('b')!.textContent).toBe('Staff account');
    expect(alert.classList).toContain('info');
  });

  it('should show error message for invalid credentials', async () => {
    authMock.login = async () => {
      throw new HttpErrorResponse({
        status: 400,
        error: { message: 'Invalid email or password' },
      });
    };

    (component as any).form.setValue({ username: 'alice@example.com', password: 'WrongPass!' });
    await component.submit();

    expect((component as any).errorMessage()).toBe('Invalid email or password');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"] b').textContent).toBe('Login failed');
  });

  it('should show the lockout duration from the server', async () => {
    authMock.login = async () => {
      throw new HttpErrorResponse({
        status: 400,
        error: {
          message: 'Account locked for 15 minutes.',
        },
      });
    };

    (component as any).form.setValue({ username: 'alice@example.com', password: 'Pass123!' });
    await component.submit();

    expect((component as any).errorMessage()).toBe('Account locked for 15 minutes.');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent)
      .toContain('Account locked for 15 minutes.');
    // A lockout is a warning, not a failure: the account is fine, it just has to wait.
    expect(fixture.nativeElement.querySelector('[role="alert"] b').textContent).toBe('Account locked');
    expect(fixture.nativeElement.querySelector('[role="alert"]').classList).toContain('warn');
  });

  it('treats the during-lockout refusal as a lockout too', async () => {
    authMock.login = async () => {
      throw new HttpErrorResponse({
        status: 400,
        error: { message: 'Account temporarily locked. Try again in 14 minutes.' },
      });
    };

    (component as any).form.setValue({ username: 'alice@example.com', password: 'Pass123!' });
    await component.submit();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="alert"] b').textContent).toBe('Account locked');
  });

  // LMKT-56: a closed or suspended account gets a fixed message, chosen by the code, not the text.
  it('shows the fixed message when the account is no longer active', async () => {
    authMock.login = async () => {
      throw new HttpErrorResponse({
        status: 403,
        error: { success: false, code: 'ACCOUNT_INACTIVE', error: 'server text', message: 'server text' },
      });
    };

    (component as any).form.setValue({ username: 'closed@example.com', password: 'Pass123!' });
    await component.submit();
    fixture.detectChanges();

    const alert: HTMLElement = fixture.nativeElement.querySelector('[role="alert"]');
    expect(alert.querySelector('b')!.textContent).toBe('Account not active');
    expect(alert.textContent).toContain(ACCOUNT_INACTIVE_MESSAGE);
    expect(alert.textContent).not.toContain('server text');
    expect(navigateCalls).toBe(0);
  });

  it('goes back to "Login failed" when a later attempt fails for another reason', async () => {
    authMock.login = async () => {
      throw new HttpErrorResponse({ status: 403, error: { code: 'ACCOUNT_INACTIVE', message: 'x' } });
    };
    (component as any).form.setValue({ username: 'alice@example.com', password: 'Pass123!' });
    await component.submit();

    authMock.login = async () => {
      throw new HttpErrorResponse({ status: 400, error: { message: 'Invalid email or password' } });
    };
    await component.submit();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="alert"] b').textContent).toBe('Login failed');
    expect((component as any).errorMessage()).toBe('Invalid email or password');
  });
});

// LMKT-56: accountInactiveInterceptor sends a cut-off session here with ?reason=inactive.
describe('Login opened after the session was cut off', () => {
  async function open(url: string): Promise<HTMLElement> {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'login', component: Login }]),
        { provide: Auth, useValue: { hasRole: () => false } },
      ],
    });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url, Login);
    return harness.routeNativeElement!;
  }

  it('shows the fixed heading and message on load', async () => {
    const alert = (await open('/login?reason=inactive')).querySelector('[role="alert"]')!;

    expect(alert.querySelector('b')!.textContent).toBe('Account not active');
    expect(alert.textContent).toContain(ACCOUNT_INACTIVE_MESSAGE);
  });

  it('shows no message on a plain visit', async () => {
    expect((await open('/login')).querySelector('[role="alert"]')).toBeNull();
  });

  // BR-03: the inactivity timeout signs an idle session out to ?reason=timeout, and one that hit
  // its time limit to ?reason=expired.
  for (const reason of ['timeout', 'expired'] as const) {
    it('says why the session ended for ?reason=' + reason, async () => {
      const alert = (await open('/login?reason=' + reason)).querySelector('[role="alert"]')!;

      expect(alert.classList).toContain('info');
      expect(alert.querySelector('b')!.textContent).toBe(SESSION_END_MESSAGES[reason].heading);
      expect(alert.textContent).toContain(SESSION_END_MESSAGES[reason].message);
    });
  }
});
