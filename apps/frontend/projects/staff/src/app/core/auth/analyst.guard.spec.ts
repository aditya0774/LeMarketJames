import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { signal } from '@angular/core';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { analystGuard } from './analyst.guard';
import { Auth } from '../../../../src/app/core/auth/auth';

describe('analystGuard', () => {
  let router: Router;
  let mockAuth: Partial<Auth>;

  beforeEach(() => {
    mockAuth = {
      currentUser: signal(null),
      hasRole: vi.fn(() => false),
    };
    TestBed.configureTestingModule({
      providers: [
        { provide: Auth, useValue: mockAuth },
        { provide: Router, useValue: { createUrlTree: vi.fn() } },
      ]
    });
    router = TestBed.inject(Router);
  });

  it('should allow access for authenticated ANALYST user', () => {
    (mockAuth.currentUser as any).set({ id: '123', username: 'analyst' });
    (mockAuth.hasRole as any).mockReturnValue(true);

    const result = TestBed.runInInjectionContext(() => analystGuard({} as any, {} as any));
    expect(result).toBe(true);
  });

  it('should redirect to login for unauthenticated user', () => {
    (mockAuth.currentUser as any).set(null);

    const urlTree = { path: 'login' } as any;
    (router.createUrlTree as any).mockReturnValue(urlTree);

    const result = TestBed.runInInjectionContext(() => analystGuard({} as any, {} as any));
    expect(router.createUrlTree).toHaveBeenCalledWith(['/login']);
    expect(result).toBe(urlTree);
  });

  it('should redirect to access-denied for authenticated non-ANALYST user', () => {
    (mockAuth.currentUser as any).set({ id: '123', username: 'client' });
    (mockAuth.hasRole as any).mockReturnValue(false);

    const urlTree = { path: 'access-denied' } as any;
    (router.createUrlTree as any).mockReturnValue(urlTree);

    const result = TestBed.runInInjectionContext(() => analystGuard({} as any, {} as any));
    expect(router.createUrlTree).toHaveBeenCalledWith(['/access-denied']);
    expect(result).toBe(urlTree);
  });
});
