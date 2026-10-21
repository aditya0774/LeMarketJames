// What the trading and staff apps import from '@lemarket/shared-ui': UI building blocks both apps
// show the same way. Features, routes and services that call the back end stay in each app.
export { THEME_STORAGE_KEY, Theme } from './lib/theme/theme';
export type { ThemeName } from './lib/theme/theme';
export { AccountMenu } from './lib/account-menu/account-menu';
export { SessionTimeout } from './lib/session-timeout/session-timeout';
export { SESSION_EXPIRY_STORAGE_KEY } from './lib/session-timeout/idle-session';
export {
  SESSION_KEEPER,
  SESSION_EXPIRED_REASON,
  SESSION_TIMEOUT_REASON,
  SESSION_END_MESSAGES,
  sessionEndMessage,
} from './lib/session-timeout/session-keeper';
export type { SessionEndReason, SessionKeeper, SessionLease } from './lib/session-timeout/session-keeper';
// For specs only; unused in the apps' builds, so it is left out of them.
export { provideSessionKeeperStub } from './lib/session-timeout/session-keeper-stub';
