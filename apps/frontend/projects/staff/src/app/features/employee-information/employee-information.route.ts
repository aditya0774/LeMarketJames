/**
 * Where the Employee information page lives and what it is called (LMKT-144). The route and the
 * account-menu link both read it, and it is kept apart from the component so the header can link
 * to the page without loading it.
 */
export const EMPLOYEE_INFORMATION = {
  path: '/employee-information',
  title: 'Employee information',
} as const;
