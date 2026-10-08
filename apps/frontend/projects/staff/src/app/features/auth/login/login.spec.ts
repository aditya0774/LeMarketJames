import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { Auth } from '../../../core/auth/auth';
import { Login } from './login';

describe('Staff login', () => {
  for (const status of [0, 500, 502, 401]) {
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
      expect(fixture.nativeElement.querySelector('.alert').textContent).toContain(
        status === 401 ? 'Invalid email or password.' : 'Staff login is unavailable.',
      );
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
    http.verify();
  });
});
