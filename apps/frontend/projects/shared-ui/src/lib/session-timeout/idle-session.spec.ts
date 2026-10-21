import { TestBed } from '@angular/core/testing';
import { IdleSession, SESSION_EXPIRY_STORAGE_KEY } from './idle-session';
import { SESSION_KEEPER, SessionLease } from './session-keeper';

const FULL: SessionLease = { expiresInSeconds: 60, inactivityTimeoutSeconds: 60 };

describe('IdleSession', () => {
  let keeper: { current: ReturnType<typeof vi.fn>; extend: ReturnType<typeof vi.fn>; end: ReturnType<typeof vi.fn> };
  let idle: IdleSession;

  // The clock starts at 0 when the session starts, so the times below read as "seconds after sign-in".
  function startWith(extend: ReturnType<typeof vi.fn> = vi.fn().mockResolvedValue(FULL)): void {
    keeper = { current: vi.fn(() => FULL), extend, end: vi.fn().mockResolvedValue(undefined) };
    TestBed.configureTestingModule({ providers: [IdleSession, { provide: SESSION_KEEPER, useValue: keeper }] });
    idle = TestBed.inject(IdleSession);
    idle.start();
  }

  const at = async (seconds: number) => vi.advanceTimersByTimeAsync(seconds * 1000 - Date.now());
  const use = (type = 'mousemove') => document.dispatchEvent(new Event(type));

  beforeEach(() => {
    vi.useFakeTimers({ now: 0 });
    localStorage.clear();
  });

  afterEach(() => {
    idle.stop();
    vi.useRealTimers();
    localStorage.clear();
  });

  it('extends the session as it opens: signing in or loading a page is activity', async () => {
    startWith();
    await at(1);

    expect(keeper.extend).toHaveBeenCalledTimes(1);
    expect(idle.secondsLeft()).toBeNull();
  });

  it('warns 30 seconds before an idle session ends, then ends it as timed out', async () => {
    startWith();

    await at(29);
    expect(idle.secondsLeft()).toBeNull();
    await at(30);
    expect(idle.secondsLeft()).toBe(30);
    await at(45);
    expect(idle.secondsLeft()).toBe(15);
    expect(keeper.end).not.toHaveBeenCalled();

    await at(60);
    expect(keeper.end).toHaveBeenCalledExactlyOnceWith('timeout');
    expect(idle.secondsLeft()).toBeNull();
    // Nobody used it, so it was never extended after opening.
    expect(keeper.extend).toHaveBeenCalledTimes(1);
  });

  it('extends at most every 20 seconds however much the user does', async () => {
    startWith();
    for (let second = 1; second <= 5; second++) {
      await at(second);
      use();
      use('keydown');
    }

    await at(19);
    expect(keeper.extend).toHaveBeenCalledTimes(1);
    await at(20);
    expect(keeper.extend).toHaveBeenCalledTimes(2);
  });

  it('never warns or signs out a user who keeps using the app', async () => {
    startWith();
    for (let second = 10; second <= 180; second += 10) {
      await at(second);
      expect(idle.secondsLeft()).toBeNull();
      use(second % 20 ? 'touchstart' : 'wheel');
    }

    expect(keeper.end).not.toHaveBeenCalled();
  });

  it('needs an answer once the warning shows: activity alone no longer extends', async () => {
    startWith();
    await at(31);
    use();
    await at(40);
    expect(keeper.extend).toHaveBeenCalledTimes(1);
    expect(idle.secondsLeft()).toBe(20);

    await idle.staySignedIn();

    expect(keeper.extend).toHaveBeenCalledTimes(2);
    expect(idle.secondsLeft()).toBeNull();
    await at(99);
    expect(keeper.end).not.toHaveBeenCalled();
  });

  it('signs out at once from the warning', async () => {
    startWith();
    await at(35);

    await idle.signOutNow();

    expect(keeper.end).toHaveBeenCalledExactlyOnceWith('timeout');
    await at(70);
    expect(keeper.end).toHaveBeenCalledTimes(1);
  });

  it('says the session expired when it hit its time limit rather than sat idle', async () => {
    // An extension that lasts less than a full timeout means the session cap was reached.
    startWith(vi.fn().mockResolvedValue({ expiresInSeconds: 20, inactivityTimeoutSeconds: 60 }));

    await at(21);

    expect(keeper.end).toHaveBeenCalledExactlyOnceWith('expired');
  });

  it('keeps an idle tab signed in while another tab of the app is in use', async () => {
    startWith();
    await at(25);
    // Another tab extended the session: it ends 60 seconds from now instead.
    localStorage.setItem(SESSION_EXPIRY_STORAGE_KEY, JSON.stringify({ expiresAt: Date.now() + 60_000, capped: false }));

    await at(54);
    expect(idle.secondsLeft()).toBeNull();
    await at(61);
    expect(keeper.end).not.toHaveBeenCalled();
    await at(85);
    expect(keeper.end).toHaveBeenCalledExactlyOnceWith('timeout');
  });

  it('shares its own end time with the other tabs', async () => {
    startWith();
    await at(1);

    expect(JSON.parse(localStorage.getItem(SESSION_EXPIRY_STORAGE_KEY)!)).toEqual({ expiresAt: 60_000, capped: false });
  });

  it('keeps the old end time when an extension fails', async () => {
    startWith(vi.fn().mockResolvedValueOnce(FULL).mockRejectedValue(new Error('auth-service down')));
    await at(5);
    use();

    await at(20);
    expect(keeper.extend).toHaveBeenCalledTimes(2);
    await at(60);

    expect(keeper.end).toHaveBeenCalledExactlyOnceWith('timeout');
  });

  it('checks the end time as soon as the tab is visible again', async () => {
    startWith();
    await at(1);
    // A hidden tab's timers barely run: the end passes with no tick.
    vi.setSystemTime(61_000);
    Object.defineProperty(document, 'visibilityState', { value: 'visible', configurable: true });

    document.dispatchEvent(new Event('visibilitychange'));

    expect(keeper.end).toHaveBeenCalledExactlyOnceWith('timeout');
    delete (document as { visibilityState?: string }).visibilityState;
  });

  it('does nothing once stopped', async () => {
    startWith();
    idle.stop();
    use();

    await at(120);

    expect(keeper.end).not.toHaveBeenCalled();
    expect(keeper.extend).toHaveBeenCalledTimes(1);
  });
});
