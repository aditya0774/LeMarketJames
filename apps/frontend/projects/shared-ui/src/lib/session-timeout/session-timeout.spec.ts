import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SessionTimeout } from './session-timeout';
import { SESSION_KEEPER, SessionLease } from './session-keeper';

const FULL: SessionLease = { expiresInSeconds: 60, inactivityTimeoutSeconds: 60 };

describe('SessionTimeout', () => {
  let keeper: { current: ReturnType<typeof vi.fn>; extend: ReturnType<typeof vi.fn>; end: ReturnType<typeof vi.fn> };
  let fixture: ComponentFixture<SessionTimeout>;

  const page = () => fixture.nativeElement as HTMLElement;
  const dialog = () => page().querySelector<HTMLElement>('[role="alertdialog"]');
  const button = (name: string) =>
    Array.from(page().querySelectorAll('button')).find((b) => b.textContent?.trim() === name)!;

  async function at(seconds: number): Promise<void> {
    await vi.advanceTimersByTimeAsync(seconds * 1000 - Date.now());
    fixture.detectChanges();
  }

  beforeEach(() => {
    vi.useFakeTimers({ now: 0 });
    localStorage.clear();
    keeper = {
      current: vi.fn(() => FULL),
      extend: vi.fn().mockResolvedValue(FULL),
      end: vi.fn().mockResolvedValue(undefined),
    };
    TestBed.configureTestingModule({
      imports: [SessionTimeout],
      providers: [{ provide: SESSION_KEEPER, useValue: keeper }],
    });
    fixture = TestBed.createComponent(SessionTimeout);
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.destroy();
    vi.useRealTimers();
    localStorage.clear();
  });

  it('shows nothing while the session has time left', async () => {
    await at(29);

    expect(dialog()).toBeNull();
  });

  it('asks whether the user is still there 30 seconds before sign-out, with focus on staying', async () => {
    await at(30);

    expect(dialog()).not.toBeNull();
    expect(dialog()!.getAttribute('aria-modal')).toBe('true');
    expect(page().querySelector('#session-timeout-title')!.textContent).toBe('Are you still there?');
    expect(page().querySelector('.session-timeout-count')!.textContent).toBe('30 seconds');
    expect(document.activeElement).toBe(button('Stay signed in'));

    await at(59);
    expect(page().querySelector('.session-timeout-count')!.textContent).toBe('1 second');
  });

  it('extends the session and closes the warning on "Stay signed in"', async () => {
    await at(40);

    button('Stay signed in').click();
    await at(41);

    expect(keeper.extend).toHaveBeenCalledTimes(2);
    expect(dialog()).toBeNull();
  });

  it('ends the session on "Sign out"', async () => {
    await at(40);
    button('Sign out').click();
    await at(41);
    expect(keeper.end).toHaveBeenCalledExactlyOnceWith('timeout');
  });

  it('ends the session when nobody answers', async () => {
    await at(60);

    expect(keeper.end).toHaveBeenCalledExactlyOnceWith('timeout');
    expect(dialog()).toBeNull();
  });

  it('stops watching when the signed-in layout goes', async () => {
    fixture.destroy();

    await vi.advanceTimersByTimeAsync(120_000);

    expect(keeper.end).not.toHaveBeenCalled();
  });
});
