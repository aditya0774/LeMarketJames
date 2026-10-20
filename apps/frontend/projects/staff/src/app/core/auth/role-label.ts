import { Role } from './auth';

/**
 * How each staff role is named to people: in the header, the account menu and the Employee
 * information page. Listed once so every place names a role the same way.
 */
const ROLE_LABELS: Partial<Record<Role, string>> = {
  TRADING_OPS: 'Trading Operations',
  ANALYST: 'Analyst',
};

/** The name of the first staff role among these, or "Staff" when none has a name here. */
export function roleLabel(roles: readonly Role[]): string {
  return roles.map((role) => ROLE_LABELS[role]).find((label) => !!label) ?? 'Staff';
}
