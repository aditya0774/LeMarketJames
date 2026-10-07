import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
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
  for (const [role, destination] of [['TRADING_OPS', '/trade-search'], ['ANALYST', '/analyst'], ['CLIENT', '/access-denied']]) {
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
});
