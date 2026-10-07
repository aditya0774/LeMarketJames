// Same-origin requests, as in the trading app: the browser calls /api on the staff app's own
// origin, and the Angular development proxy (proxy.conf.json) or Nginx in Docker (nginx.conf)
// forwards them to the staff gateway on 8090, never to the trading gateway. The session cookie
// the browser holds for this app is staff_jwt (contracts/C7-roles.md).
export const environment = { apiBaseUrl: '' };
