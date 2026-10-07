import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { analystGuard } from './analyst.guard';
import { Auth } from '../../../../src/app/core/auth/auth';

describe('analystGuard', () => {
  let router: Router;
  let mockAuth: jasmine.SpyObj<Auth>;

  beforeEach(() => {
    mockAuth = jasmine.createSpyObj('Auth', ['currentUser', 'hasRole']);
    TestBed.configureTestingModule({
      providers: [
        { provide: Auth, useValue: mockAuth },
        { provide: Router, useValue: jasmine.createSpyObj('Router', ['createUrlTree']) },
      ]
    });
    router = TestBed.inject(Router);
  });

  it('should allow access for authenticated ANALYST user', () => {
    mockAuth.currentUser.and.returnValue({ id: '123', username: 'analyst' });
    mockAuth.hasRole.and.returnValue(true);

    const result = TestBed.runInInjectionContext(() => analystGuard());
    expect(result).toBe(true);
  });

  it('should redirect to login for unauthenticated user', () => {
    mockAuth.currentUser.and.returnValue(null);

    const urlTree = { path: 'login' } as any;
    (router.createUrlTree as jasmine.Spy).and.returnValue(urlTree);

    const result = TestBed.runInInjectionContext(() => analystGuard());
    expect(router.createUrlTree).toHaveBeenCalledWith(['/login']);
    expect(result).toBe(urlTree);
  });

  it('should redirect to access-denied for authenticated non-ANALYST user', () => {
    mockAuth.currentUser.and.returnValue({ id: '123', username: 'client' });
    mockAuth.hasRole.and.returnValue(false);

    const urlTree = { path: 'access-denied' } as any;
    (router.createUrlTree as jasmine.Spy).and.returnValue(urlTree);

    const result = TestBed.runInInjectionContext(() => analystGuard());
    expect(router.createUrlTree).toHaveBeenCalledWith(['/access-denied']);
    expect(result).toBe(urlTree);
  });
});
