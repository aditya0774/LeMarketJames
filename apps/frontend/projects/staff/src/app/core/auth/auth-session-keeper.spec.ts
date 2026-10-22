import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Auth } from './auth';
import { AuthSessionKeeper } from './auth-session-keeper';

describe('AuthSessionKeeper', () => {
  let auth: { sessionLease: ReturnType<typeof vi.fn>; refresh: ReturnType<typeof vi.fn>; logout: ReturnType<typeof vi.fn>; clearSession: ReturnType<typeof vi.fn> };
  let keeper: AuthSessionKeeper;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    auth = {
      sessionLease: vi.fn(() => ({ expiresInSeconds: 42, inactivityTimeoutSeconds: 60 })),
      refresh: vi.fn().mockResolvedValue({ expiresInSeconds: 60, inactivityTimeoutSeconds: 60 }),
      logout: vi.fn().mockResolvedValue(undefined),
      clearSession: vi.fn(),
    };
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: Auth, useValue: auth }] });
    keeper = TestBed.inject(AuthSessionKeeper);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  it('reads the session timing from Auth and extends it through refresh', async () => {
    expect(keeper.current()).toEqual({ expiresInSeconds: 42, inactivityTimeoutSeconds: 60 });
    expect(await keeper.extend()).toEqual({ expiresInSeconds: 60, inactivityTimeoutSeconds: 60 });
  });

  it('signs out and shows the login page with the reason when the session ends', async () => {
    await keeper.end('timeout');

    expect(auth.logout).toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { reason: 'timeout' }, replaceUrl: true });
  });

  it('still forgets the session and shows the login page when sign-out fails', async () => {
    auth.logout.mockRejectedValue(new Error('offline'));

    await keeper.end('expired');

    expect(auth.clearSession).toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { reason: 'expired' }, replaceUrl: true });
  });
});
