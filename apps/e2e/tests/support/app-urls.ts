/**
 * Where the two web apps run. Docker and start-all.ps1 serve them over HTTPS with a self-signed
 * certificate (BR-02, contract C5); set E2E_BASE_URL / E2E_STAFF_BASE_URL to test elsewhere.
 */
export const TRADING_APP_URL = process.env.E2E_BASE_URL ?? 'https://localhost:4200';
export const STAFF_APP_URL = process.env.E2E_STAFF_BASE_URL ?? 'https://localhost:4201';

/**
 * The certificate is self-signed, so every browser and request context accepts it. The configs set
 * it for the fixtures; a context a test makes itself must pass it too, since it inherits nothing.
 */
export const SELF_SIGNED = { ignoreHTTPSErrors: true } as const;
