/**
 * The staff app's dev-server proxy (`ng serve staff`): what its Nginx (nginx.conf in this folder)
 * does in Docker. Keep the two in step; contracts/C6-api.md lists the paths and
 * contracts/C7-roles.md the cookie.
 *
 * - Only the listed paths exist. Anything else under /api answers 404; each service still checks
 *   the caller's role itself.
 * - Staff hold their own session cookie, staff_jwt, so a staff and a customer session can share a
 *   browser. The services only know jwt: on the way in the staff cookie is sent as jwt and any
 *   customer jwt is dropped; on the way out the jwt set by sign-in, sign-out and refresh is renamed
 *   to staff_jwt.
 * - Origin is not passed on: the services accept only the trading app's origin and would refuse
 *   every staff POST with 403.
 */

const SERVICE_COOKIE = 'jwt';
const STAFF_COOKIE = 'staff_jwt';

/** The value of one cookie in a Cookie header, or undefined. */
export function cookieValue(cookieHeader, name) {
  for (const part of (cookieHeader ?? '').split(';')) {
    const separator = part.indexOf('=');
    if (separator > 0 && part.slice(0, separator).trim() === name) {
      return part.slice(separator + 1).trim();
    }
  }
  return undefined;
}

/** The Cookie header a service gets: the staff cookie under the services' name, nothing else. */
export function serviceCookie(cookieHeader) {
  const staff = cookieValue(cookieHeader, STAFF_COOKIE);
  return staff === undefined ? undefined : `${SERVICE_COOKIE}=${staff}`;
}

/** Set-Cookie values as the browser gets them: the services' jwt renamed, attributes kept. */
export function staffSetCookies(setCookies) {
  const prefix = `${SERVICE_COOKIE}=`;
  return setCookies.map(value =>
    value.startsWith(prefix) ? `${STAFF_COOKIE}=${value.slice(prefix.length)}` : value);
}

/** One proxied route, translating the session cookie and dropping Origin. */
function staffRoute(target) {
  return {
    target,
    secure: false,
    configure: proxy => {
      proxy.on('proxyReq', (proxyReq, req) => {
        proxyReq.removeHeader('origin');
        const cookie = serviceCookie(req.headers.cookie);
        if (cookie === undefined) {
          proxyReq.removeHeader('cookie');
        } else {
          proxyReq.setHeader('cookie', cookie);
        }
      });
      proxy.on('proxyRes', proxyRes => {
        const setCookies = proxyRes.headers['set-cookie'];
        if (setCookies) {
          proxyRes.headers['set-cookie'] = staffSetCookies(setCookies);
        }
      });
    },
  };
}

/**
 * The proxy configuration for services on the given host. Keys starting with ^ are regular
 * expressions, matched in order against the path and query string.
 */
export function staffProxy(host) {
  const at = port => `http://${host}:${port}`;
  return {
    // Analyst reports and Trading Ops' surveillance alerts: staff-ops-service.
    '^/api/v1/(reports|surveillance)/': staffRoute(at(8086)),
    // Sign-in, sign-out, "who am I" and session refresh (BR-03): auth-service. No registration for staff.
    '^/api/auth/(login|logout|me|refresh)(\\?|$)': staffRoute(at(8082)),
    // The Trading Ops reads: trade search, its client list and an order's timeline: orders-service.
    '^/api/v1/orders/(trades/search|trades/clients|[^/?]+/timeline)(\\?|$)': staffRoute(at(8085)),
    // Any other /api path does not exist for staff; returning false makes the dev server answer 404.
    '^/api/': { target: at(8086), bypass: () => false },
  };
}
