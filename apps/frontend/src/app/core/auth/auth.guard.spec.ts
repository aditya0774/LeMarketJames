import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { Auth } from './auth';
import { authGuard } from './auth.guard';

describe('authGuard', () => {
  const currentUser = signal<string | null>(null);

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: Auth, useValue: { currentUser } }],
    });
  });

  const run = () =>
    TestBed.runInInjectionContext(() => authGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot));

  it('redirects signed-out visitors to /login', () => {
    currentUser.set(null);
    const result = run();
    expect(result instanceof UrlTree).toBe(true);
    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/login');
  });

  it('lets signed-in users through', () => {
    currentUser.set('lebron');
    expect(run()).toBe(true);
  });
});
