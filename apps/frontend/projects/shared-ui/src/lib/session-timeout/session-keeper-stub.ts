import { Provider } from '@angular/core';
import { SESSION_KEEPER, SessionKeeper } from './session-keeper';

/**
 * For specs of pages that contain the signed-in layout: a SESSION_KEEPER that never extends or
 * ends anything, so the inactivity timeout stays quiet and sends no requests. Specs of the
 * timeout itself use their own keeper.
 */
export function provideSessionKeeperStub(): Provider {
  const keeper: SessionKeeper = {
    current: () => null,
    extend: () => new Promise(() => undefined),
    end: () => Promise.resolve(),
  };
  return { provide: SESSION_KEEPER, useValue: keeper };
}
