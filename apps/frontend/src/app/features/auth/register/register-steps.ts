import { RegisterFormData } from './register.schema';

/** One page of the sign-up flow and the form fields it collects, in on-screen order. */
export interface RegisterStep {
  readonly label: string;
  readonly fields: readonly (keyof RegisterFormData)[];
}

/**
 * The four steps of the redesign's "Create account" flow. Every schema field belongs to exactly
 * one step, so a step can be checked on its own before moving on and a server error can send the
 * user back to the step that holds the field.
 */
export const REGISTER_STEPS: readonly RegisterStep[] = [
  { label: 'About you', fields: ['firstName', 'middleName', 'lastName', 'dateOfBirth', 'phoneNumber', 'email'] },
  { label: 'Home address', fields: ['streetAddress', 'apartment', 'city', 'state', 'zipCode'] },
  { label: 'Identity & funding', fields: ['ssn', 'initialDeposit', 'investmentExperience', 'employmentStatus'] },
  { label: 'Sign-in details', fields: ['username', 'password', 'confirmPassword'] },
];

/** The step (1-based) that collects a field, or null for a field no step shows. */
export function stepOfField(field: string): number | null {
  const index = REGISTER_STEPS.findIndex((step) => (step.fields as readonly string[]).includes(field));
  return index < 0 ? null : index + 1;
}

/**
 * The password rules from register.schema.ts, one line each, so the form can tick them off as the
 * user types. The schema stays the source of truth on submit.
 */
export const PASSWORD_RULES: readonly { readonly text: string; readonly test: (password: string) => boolean }[] = [
  { text: 'At least 8 characters', test: (p) => p.length >= 8 },
  { text: 'One uppercase letter', test: (p) => /[A-Z]/.test(p) },
  { text: 'One lowercase letter', test: (p) => /[a-z]/.test(p) },
  { text: 'One special character, like ! or #', test: (p) => /[^A-Za-z0-9]/.test(p) },
];

export const US_STATES: readonly string[] = [
  'Alabama', 'Alaska', 'Arizona', 'Arkansas', 'California', 'Colorado', 'Connecticut', 'Delaware', 'Florida',
  'Georgia', 'Hawaii', 'Idaho', 'Illinois', 'Indiana', 'Iowa', 'Kansas', 'Kentucky', 'Louisiana', 'Maine',
  'Maryland', 'Massachusetts', 'Michigan', 'Minnesota', 'Mississippi', 'Missouri', 'Montana', 'Nebraska',
  'Nevada', 'New Hampshire', 'New Jersey', 'New Mexico', 'New York', 'North Carolina', 'North Dakota', 'Ohio',
  'Oklahoma', 'Oregon', 'Pennsylvania', 'Rhode Island', 'South Carolina', 'South Dakota', 'Tennessee', 'Texas',
  'Utah', 'Vermont', 'Virginia', 'Washington', 'West Virginia', 'Wisconsin', 'Wyoming',
];
