import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, provideRouter, UrlTree } from '@angular/router';
import { Auth, Role } from './auth';
import { tradingOpsGuard } from './trading-ops.guard';
import { StaffShell } from '../../shared/layout/staff-shell/staff-shell';

describe('Staff trade-search access', () => {
  beforeEach(() => TestBed.configureTestingModule({
    imports: [StaffShell], providers: [provideHttpClient(), provideRouter([])],
  }));
  const run = () => TestBed.runInInjectionContext(() =>
    tradingOpsGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot));
  it('redirects anonymous visitors to login', () => {
    expect(TestBed.inject(Router).serializeUrl(run() as UrlTree)).toBe('/login');
  });
  for (const role of ['CLIENT', 'ANALYST', 'COMPLIANCE', 'TRADING_OPS'] as Role[]) {
    it('enforces access and navigation for ' + role, () => {
      const auth = TestBed.inject(Auth);
      auth.currentUser.set('staff'); auth.roles.set([role]);
      if (role === 'TRADING_OPS') expect(run()).toBe(true);
      else expect(TestBed.inject(Router).serializeUrl(run() as UrlTree)).toBe('/access-denied');
      const fixture = TestBed.createComponent(StaffShell); fixture.detectChanges();
      expect(!!fixture.nativeElement.querySelector('a[href="/trade-search"]')).toBe(role === 'TRADING_OPS');
    });
  }
});
