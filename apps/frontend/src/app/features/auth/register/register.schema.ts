import { z } from 'zod';

/**
 * Registration Form Schema
 * 
 * This file defines all validation rules for the registration form using Zod schema validation.
 * Zod provides a declarative approach to data validation, allowing us to define both the shape
 * of the data and all validation rules in one place.
 * 
 * Benefits of this approach:
 * - Single source of truth for all validation rules
 * - Type-safe validation (RegisterFormData type is automatically inferred)
 * - Detailed error messages for each validation rule
 * - Composable validation logic (useful for cross-field rules)
 * - Easy to maintain and update validation rules
 * 
 * Name Field Structure:
 * - firstName: Required field for the user's given name
 * - middleName: Optional field for middle name(s)
 * - lastName: Required field for the user's family name
 * 
 * This split allows for:
 * - Better international name support (many cultures have complex name structures)
 * - More accurate data storage and retrieval
 * - Flexible display formatting (can combine as needed)
 * - Optional middle name for users who don't have or want to provide one
 */

// Reused character patterns -------------------------------------------------

// Letters, spaces, hyphens, and apostrophes only (e.g. "Mary-Jane O'Neil").
// Used for fields where a name is expected, so digits and symbols can't be typed in.
// Allows 2-60 characters to accommodate a wide variety of name formats.
// Works for firstName, middleName, lastName, city, and state fields.
const namePattern = /^[A-Za-z' -]{2,60}$/;

// Letters, numbers, spaces, and . , # - only.
// Used for street address / apartment, since those legitimately need numbers (e.g. "123 Main St #4B").
const addressPattern = /^[A-Za-z0-9\s.,#-]{2,100}$/;

// Letters, numbers, underscores, and hyphens only, 3-20 characters.
const usernamePattern = /^[A-Za-z0-9_-]{3,20}$/;

// (XXX) XXX-XXXX — the format produced by formatPhoneNumber() in register.ts as the user types.
const phonePattern = /^\(\d{3}\) \d{3}-\d{4}$/;

// XXX-XX-XXXX — the format produced by formatSsn() in register.ts as the user types.
const ssnPattern = /^\d{3}-\d{2}-\d{4}$/;

// Exactly 5 digits — the format produced by formatZipCode() in register.ts as the user types.
const zipPattern = /^\d{5}$/;

// One @ symbol, no spaces, and must end in .com.
const emailPattern = /^[^\s@]+@[^\s@]+\.com$/i;

// This helper computes age in whole years from a yyyy-mm-dd date string.
function calculateAge(dateOfBirth: string): number {
  const birthDate = new Date(dateOfBirth);
  const today = new Date();
  let age = today.getFullYear() - birthDate.getFullYear();

  const hasHadBirthdayThisYear =
    today.getMonth() > birthDate.getMonth() ||
    (today.getMonth() === birthDate.getMonth() && today.getDate() >= birthDate.getDate());

  if (!hasHadBirthdayThisYear) {
    age--;
  }

  return age;
}

// The schema itself ----------------------------------------------------------
// Each field lists its rules top to bottom; Zod stops at (and reports) the first rule that fails.
//
// Name Fields Validation:
// - firstName: Required string, 2-60 characters, letters/spaces/hyphens/apostrophes only
// - middleName: Optional string, same character restrictions as firstName
// - lastName: Required string, 2-60 characters, letters/spaces/hyphens/apostrophes only
//
// Why split names?
// 1. Many cultures have first name(s) and family name(s) that need to be separate
// 2. Provides more accurate data structure for international users
// 3. Allows flexible formatting and personalization (e.g., addressing users by first name)
// 4. Makes API integrations and reporting easier with structured data
// 5. Optional middle name accommodates users from cultures/regions where it's not common
export const registerSchema = z
  .object({
    // Name fields: split into first, middle (optional), and last name
    // First name and last name are required; middle name is optional
    firstName: z
      .string()
      .trim()
      .min(1, 'First name is required.')
      .regex(namePattern, 'First name may only contain letters, spaces, hyphens, and apostrophes.'),

    middleName: z
      .string()
      .trim()
      .refine((value) => value === '' || namePattern.test(value), {
        message: 'Middle name may only contain letters, spaces, hyphens, and apostrophes.',
      }),

    lastName: z
      .string()
      .trim()
      .min(1, 'Last name is required.')
      .regex(namePattern, 'Last name may only contain letters, spaces, hyphens, and apostrophes.'),

    username: z
      .string()
      .trim()
      .min(1, 'Username is required.')
      .min(3, 'Username must be between 3 and 20 characters.')
      .max(20, 'Username must be between 3 and 20 characters.')
      .regex(usernamePattern, 'Username may only contain letters, numbers, underscores, and hyphens.'),

    streetAddress: z
      .string()
      .trim()
      .min(1, 'Street address is required.')
      .regex(addressPattern, 'Street address may only contain letters, numbers, spaces, and . , # -.'),

    // Apartment is optional, so an empty string is always allowed.
    apartment: z
      .string()
      .trim()
      .refine((value) => value === '' || addressPattern.test(value), {
        message: 'Apartment may only contain letters, numbers, spaces, and . , # -.',
      }),

    city: z
      .string()
      .trim()
      .min(1, 'City is required.')
      .regex(namePattern, 'City may only contain letters, spaces, hyphens, and apostrophes.'),

    state: z
      .string()
      .trim()
      .min(1, 'State is required.')
      .regex(namePattern, 'State may only contain letters, spaces, hyphens, and apostrophes.'),

    zipCode: z
      .string()
      .trim()
      .min(1, 'ZIP code is required.')
      .regex(zipPattern, 'ZIP code must be exactly 5 digits.'),

    ssn: z
      .string()
      .trim()
      .min(1, 'SSN is required.')
      .regex(ssnPattern, 'SSN must be 9 digits in the format xxx-xx-xxxx.'),

    // Coerced to a string because the <input type="number"> in the template makes
    // Angular's ngModel write back an actual JS number, not a string, on this field.
    initialDeposit: z
      .coerce
      .string()
      .trim()
      .min(1, 'Initial deposit amount is required.')
      .refine((value) => !isNaN(Number(value)), { message: 'Initial deposit amount must be a number.' })
      .refine((value) => Number(value) >= 5000, { message: 'Initial deposit must be at least $5,000.' }),

    investmentExperience: z.enum(['beginner', 'experienced']),

    // Employment status is required: users must select either 'employed' or 'notEmployed'
    employmentStatus: z.enum(['employed', 'notEmployed'], {
      message: 'Employment status is required.',
    }),

    dateOfBirth: z
      .string()
      .trim()
      .min(1, 'Date of birth is required.')
      .refine((value) => !isNaN(new Date(value).getTime()), { message: 'Date of birth is invalid.' })
      .refine((value) => calculateAge(value) > 18, { message: 'You must be older than 18 to register.' }),

    email: z
      .string()
      .trim()
      .min(1, 'Email is required.')
      .regex(emailPattern, 'Email must contain @ and end in .com.'),

    phoneNumber: z
      .string()
      .trim()
      .min(1, 'Phone is required.')
      .regex(phonePattern, 'Phone number must be 10 digits.'),

    // Individual .regex() calls (rather than one combined check) so Zod can point out
    // exactly which requirement failed, using the same combined message as before.
    password: z
      .string()
      .min(1, 'Password is required.')
      .min(8, 'Password must be at least 8 characters and include an uppercase letter, a lowercase letter, and a special character.')
      .regex(/[A-Z]/, 'Password must be at least 8 characters and include an uppercase letter, a lowercase letter, and a special character.')
      .regex(/[a-z]/, 'Password must be at least 8 characters and include an uppercase letter, a lowercase letter, and a special character.')
      .regex(
        /[^A-Za-z0-9]/,
        'Password must be at least 8 characters and include an uppercase letter, a lowercase letter, and a special character.'
      ),

    confirmPassword: z.string().min(1, 'Confirm password is required.'),
  })
  // Cross-field validation rules that depend on multiple properties
  // These rules must go in .superRefine() since individual field rules can't access other fields
  .superRefine((data, ctx) => {
    // Rule 1: Password confirmation must match
    // Ensures users entered their password correctly before submission
    if (data.confirmPassword !== data.password) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['confirmPassword'],
        message: 'Passwords do not match.',
      });
    }

    // Rule 2: 'Experienced' requires minimum deposit
    // Business rule: Users must have at least $100,000 initial deposit to select 'experienced'
    // This prevents over-claiming expertise while having low financial commitment
    if (data.investmentExperience === 'experienced' && Number(data.initialDeposit) < 100000) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['investmentExperience'],
        message: 'Experienced requires an initial deposit of $100,000 or more.',
      });
    }
  });

/**
 * RegisterFormData Type
 * 
 * Automatically inferred from the Zod schema above using z.infer<typeof registerSchema>.
 * 
 * This means:
 * - Any changes to validation rules immediately update the type
 * - The component's registerData object is always type-safe
 * - TypeScript catches mismatches between form fields and validation rules at compile time
 * 
 * Structure:
 * - Name: firstName (required), middleName (optional), lastName (required)
 * - Address: streetAddress, apartment (optional), city, state, zipCode
 * - Identity: ssn, dateOfBirth
 * - Financial: initialDeposit, investmentExperience ('beginner' | 'experienced'), employmentStatus ('employed' | 'notEmployed')
 * - Contact: email, phoneNumber
 * - Security: password, confirmPassword
 */
// Zod infers the exact TypeScript type of valid data straight from the schema above,
// so the shape of RegisterFormData and the validation rules can never drift apart.
export type RegisterFormData = z.infer<typeof registerSchema>;
