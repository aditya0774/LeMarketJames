import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, provideRouter, UrlTree } from '@angular/router';
import { Auth, Role } from './auth';
import { clientGuard } from './client.guard';

describe('Client page access', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [provideHttpClient(), provideRouter([])] }));
  const run = () => TestBed.runInInjectionContext(() => clientGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot));
  const redirect = () => TestBed.inject(Router).serializeUrl(run() as UrlTree);

  it('redirects signed-out visitors to login', () => {
    expect(redirect()).toBe('/login');
  });

  it('lets a CLIENT in', () => {
    const auth = TestBed.inject(Auth);
    auth.currentUser.set('user'); auth.roles.set(['CLIENT']);
    expect(run()).toBe(true);
  });

  for (const role of ['ANALYST', 'TRADING_OPS'] as Role[]) {
    it('sends ' + role + ' to access denied', () => {
      const auth = TestBed.inject(Auth);
      auth.currentUser.set('user'); auth.roles.set([role]);
      expect(redirect()).toBe('/access-denied');
    });
  }
});
