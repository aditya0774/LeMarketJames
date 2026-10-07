# Staff gateway

The staff app on port 4201 calls this gateway on port 8090. It forwards only
POST login/logout and GET session, trade-search and report requests. Registration,
order placement and internal service endpoints have no route. Route definitions
and upstream environment overrides live in `src/main/resources/application.yml`.

The gateway forwards the JWT cookie and response cookies unchanged. Auth-service
authenticates users; buy-sell-service requires Trading Operations for search;
reporting-service requires Analyst for reports. Client authentication does not
grant access to either staff API. The gateway has no database connection.

CORS is checked at the gateway, then Origin is removed before proxying to avoid
applying upstream trading-browser CORS settings to staff traffic.

Run `mvn -B -pl services/staff-gateway-service spring-boot:run` from the repository
root, or use the Windows start-all script / Docker Compose. Health is available
at `http://localhost:8090/actuator/health`.

Run `mvn -B -pl services/staff-gateway-service -am test` for HTTP routing, cookie,
query-string, CORS and excluded-path tests with a local upstream stub.
