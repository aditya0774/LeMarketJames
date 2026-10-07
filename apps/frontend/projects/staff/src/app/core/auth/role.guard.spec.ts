import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRouteSnapshot, CanActivateFn, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { Auth, Role } from './auth';
import { requiresRole } from './role.guard';

describe('requiresRole', () => {
  beforeEach(() => TestBed.configureTestingModule({
    providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
  }));

  /** What the guard decides: true, or the address it sends the visitor to. */
  const decide = (guard: CanActivateFn): true | string => {
    const result = TestBed.runInInjectionContext(
      () => guard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot));
    return result === true ? true : TestBed.inject(Router).serializeUrl(result as UrlTree);
  };

  const signInAs = (...roles: Role[]) => {
    const auth = TestBed.inject(Auth);
    auth.currentUser.set('someone');
    auth.roles.set(roles);
  };

  it('sends a signed-out visitor to the staff login', () => {
    expect(decide(requiresRole('TRADING_OPS'))).toBe('/login');
  });

  it('lets the required role in', () => {
    signInAs('ANALYST');
    expect(decide(requiresRole('ANALYST'))).toBe(true);
  });

  for (const role of ['CLIENT', 'ANALYST', 'COMPLIANCE'] as Role[]) {
    it(`refuses ${role} on a TRADING_OPS page`, () => {
      signInAs(role);
      expect(decide(requiresRole('TRADING_OPS'))).toBe('/access-denied');
    });
  }

  it('lets in any one of several roles, and refuses the rest', () => {
    const guard = requiresRole('TRADING_OPS', 'COMPLIANCE');

    signInAs('COMPLIANCE');
    expect(decide(guard)).toBe(true);
    signInAs('TRADING_OPS');
    expect(decide(guard)).toBe(true);
    signInAs('ANALYST');
    expect(decide(guard)).toBe('/access-denied');
  });
});
