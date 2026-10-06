import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, provideRouter, UrlTree } from '@angular/router';
import { Auth, Role } from './auth';
import { tradingOpsGuard } from './trading-ops.guard';
import { AppShell } from '../../shared/layout/app-shell/app-shell';

describe('Trading Operations access', () => {
  beforeEach(() => TestBed.configureTestingModule({ imports: [AppShell], providers: [provideHttpClient(), provideRouter([])] }));
  const run = () => TestBed.runInInjectionContext(() => tradingOpsGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot));
  it('redirects signed-out visitors to login', () => {
    expect(TestBed.inject(Router).serializeUrl(run() as UrlTree)).toBe('/login');
  });
  for (const role of ['CLIENT', 'ANALYST', 'COMPLIANCE', 'TRADING_OPS'] as Role[]) {
    it('checks direct access and navigation for ' + role, () => {
      const auth = TestBed.inject(Auth);
      auth.currentUser.set('user'); auth.roles.set([role]);
      if (role === 'TRADING_OPS') expect(run()).toBe(true);
      else expect(TestBed.inject(Router).serializeUrl(run() as UrlTree)).toBe('/access-denied');
      const fixture = TestBed.createComponent(AppShell);
      fixture.detectChanges();
      expect(!!fixture.nativeElement.querySelector('nav a')).toBe(role === 'TRADING_OPS');
    });
  }
});
