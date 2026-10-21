/**
 * The shared token every call to a service's /internal/** endpoints must carry (BR-02, contract C5).
 * The default is the development value that docker-compose.yml and both services' application.properties
 * use when INTERNAL_API_TOKEN is not set; set INTERNAL_API_TOKEN when the stack uses another.
 */
export const INTERNAL_HEADERS = {
  'X-Internal-Token': process.env.INTERNAL_API_TOKEN ?? 'change-this-development-only-internal-token',
};
