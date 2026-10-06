// Same-origin requests, as in the trading app: the browser calls /api on the staff app's own
// origin, and the Angular development proxy (proxy.conf.json) or Nginx in Docker (nginx.conf)
// forwards them to the staff gateway on 8090 (LMKT-143), never to the trading gateway.
export const environment = { apiBaseUrl: '' };
