import { HttpInterceptorFn } from '@angular/common/http';

// Ensures the httpOnly auth cookie is sent/received on cross-origin requests to the backend.
export const withCredentialsInterceptor: HttpInterceptorFn = (req, next) => {
  return next(req.clone({ withCredentials: true }));
};
