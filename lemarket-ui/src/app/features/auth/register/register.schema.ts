import { z } from 'zod';

// This file defines every validation rule for the registration form in one place.
// Instead of hand-writing a separate "validateX" method for each field (the old approach),
// Zod lets us describe the shape and rules of the data as a single schema, and then
// call `.safeParse(data)` to check a whole object against every rule at once.

// Reused character patterns -------------------------------------------------

// Letters, spaces, hyphens, and apostrophes only (e.g. "Mary-Jane O'Neil").
// Used for fields where a name is expected, so digits and symbols can't be typed in.
const namePattern = /^[A-Za-z' -]{2,60}$/;

// Letters, numbers, spaces, and . , # - only.
// Used for street address / apartment, since those legitimately need numbers (e.g. "123 Main St #4B").
const addressPattern = /^[A-Za-z0-9\s.,#-]{2,100}$/;

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
export const registerSchema = z
  .object({
    fullName: z
      .string()
      .trim()
      .min(1, 'Full name is required.')
      .regex(namePattern, 'Full name may only contain letters, spaces, hyphens, and apostrophes.'),

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
  // Cross-field rules that need more than one property must go in .superRefine(),
  // since a single field's own z.string() rules can't see the rest of the object.
  .superRefine((data, ctx) => {
    // The confirm password field must match the password field exactly.
    if (data.confirmPassword !== data.password) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['confirmPassword'],
        message: 'Passwords do not match.',
      });
    }

    // 'Experienced' may only be selected alongside a $100,000+ initial deposit.
    if (data.investmentExperience === 'experienced' && Number(data.initialDeposit) < 100000) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['investmentExperience'],
        message: 'Experienced requires an initial deposit of $100,000 or more.',
      });
    }
  });

// Zod infers the exact TypeScript type of valid data straight from the schema above,
// so the shape of RegisterFormData and the validation rules can never drift apart.
export type RegisterFormData = z.infer<typeof registerSchema>;
