pipeline {
    agent any
    tools {
        jdk 'JDK21'
        nodejs 'NodeJS'
    }

    stages {
        stage('Test and build Angular') {
            steps {
                dir('apps/frontend') {
                    sh 'rm -rf node_modules && npm install && npm test -- --watch=false && npm run build'
                }
            }
        }

        // Disposable testing database: remove -v here and in post cleanup before keeping real data.
        stage('Clean up stale volumes') {
            steps {
                sh '''
                    set +e
                    if docker compose version >/dev/null 2>&1; then
                        docker compose down -v --remove-orphans
                        docker compose ps -q | xargs -r docker kill 2>/dev/null || true
                    elif command -v docker-compose >/dev/null 2>&1; then
                        docker-compose down -v --remove-orphans
                        docker-compose ps -q | xargs -r docker kill 2>/dev/null || true
                    fi
                    # Force kill any containers still using port 8080
                    docker ps --filter "publish=8080" -q | xargs -r docker kill 2>/dev/null || true
                    set -e
                '''
            }
        }

        stage('Test with Maven') {
            steps {
                // The root parent pom builds and tests every backend module (libs/common + services/*).
                sh '''
                    set -eu
                    mvn -B clean test

                    echo "=== BACKEND TESTS COMPLETED: PASS ==="
                    echo "=== BACKEND SUREFIRE SUMMARY ==="

                    if ls libs/*/target/surefire-reports/TEST-*.xml services/*/target/surefire-reports/TEST-*.xml >/dev/null 2>&1; then
                        grep -h '<testsuite ' libs/*/target/surefire-reports/TEST-*.xml services/*/target/surefire-reports/TEST-*.xml \
                            | sed -E 's/.*name="([^"]+)".*tests="([0-9]+)".*failures="([0-9]+)".*errors="([0-9]+)".*skipped="([0-9]+)".*/- \1: tests=\2 failures=\3 errors=\4 skipped=\5/'
                    else
                        echo "No surefire XML reports found"
                    fi
                '''
            }
            post {
                always {
                    junit 'libs/*/target/surefire-reports/*.xml, services/*/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Run buy-order microservice tests') {
            steps {
                sh '''
                    set -eu
                    mvn -B -pl services/core-service -am "-Dtest=BuyOrderControllerTest,OrderServiceTest" -Dsurefire.failIfNoSpecifiedTests=false test

                    echo "=== BUY-ORDER MICROSERVICE TEST SUMMARY ==="
                    if ls services/core-service/target/surefire-reports/TEST-*BuyOrderControllerTest.xml >/dev/null 2>&1; then
                        grep -h '<testsuite ' services/core-service/target/surefire-reports/TEST-*BuyOrderControllerTest.xml \
                            | sed -E 's/.*name="([^"]+)".*tests="([0-9]+)".*failures="([0-9]+)".*errors="([0-9]+)".*skipped="([0-9]+)".*/- \1: tests=\2 failures=\3 errors=\4 skipped=\5/'
                    else
                        echo "BuyOrderControllerTest report not found"
                    fi
                '''
            }
            post {
                always {
                    junit 'services/core-service/target/surefire-reports/TEST-*BuyOrderControllerTest.xml'
                }
            }
        }

        stage('Run market simulator tests') {
            steps {
                sh '''
                    set -eu
                    mvn -B -pl services/market-service -am "-Dtest=MarketSimulatorTest,GbmModelTest" -Dsurefire.failIfNoSpecifiedTests=false test

                    echo "=== MARKET SIMULATOR TEST SUMMARY ==="
                    if ls services/market-service/target/surefire-reports/TEST-*.xml >/dev/null 2>&1; then
                        grep -h '<testsuite ' services/market-service/target/surefire-reports/TEST-*.xml \
                            | sed -E 's/.*name="([^"]+)".*tests="([0-9]+)".*failures="([0-9]+)".*errors="([0-9]+)".*skipped="([0-9]+)".*/- \1: tests=\2 failures=\3 errors=\4 skipped=\5/'
                    else
                        echo "Market simulator test report not found"
                    fi
                '''
            }
            post {
                always {
                    junit 'services/market-service/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Run holdings service tests') {
            steps {
                sh '''
                    set -eu
                    mvn -B -pl services/holdings-service -am "-Dtest=HoldingsServiceTest,HoldingsControllerTest,HoldingsSettlementServiceTest,ProfileServiceTest,PortfolioServiceTest,TradeServiceTest" -Dsurefire.failIfNoSpecifiedTests=false test

                    echo "=== HOLDINGS SERVICE TEST SUMMARY ==="
                    if ls services/holdings-service/target/surefire-reports/TEST-*.xml >/dev/null 2>&1; then
                        grep -h '<testsuite ' services/holdings-service/target/surefire-reports/TEST-*.xml \
                            | sed -E 's/.*name="([^"]+)".*tests="([0-9]+)".*failures="([0-9]+)".*errors="([0-9]+)".*skipped="([0-9]+)".*/- \1: tests=\2 failures=\3 errors=\4 skipped=\5/'
                    else
                        echo "Holdings service test report not found"
                    fi
                '''
            }
            post {
                always {
                    junit 'services/holdings-service/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Verify Docker Compose') {
            steps {
                sh '''
                    if docker compose version >/dev/null 2>&1; then
                        echo "Using Docker Compose v2"
                    elif command -v docker-compose >/dev/null 2>&1; then
                        echo "Using legacy docker-compose"
                    else
                        echo "Docker Compose is not installed on this Jenkins agent"
                        exit 1
                    fi
                '''
            }
        }

        stage('Verify sell persistence on PostgreSQL') {
            steps {
                sh '''
                    set -eu
                    if docker compose version >/dev/null 2>&1; then
                        compose() { docker compose "$@"; }
                    else
                        compose() { docker-compose "$@"; }
                    fi
                    compose up -d db
                    ready=0
                    for attempt in $(seq 1 60); do
                        if compose exec -T db pg_isready -h 127.0.0.1 -U lemarket -d lemarket >/dev/null 2>&1; then
                            ready=1
                            break
                        fi
                        sleep 1
                    done
                    test "$ready" = 1
                    mvn -B -pl services/core-service -am -Dspring.profiles.active=postgres-test -Dtest=SellOrderIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
                '''
            }
            post {
                always { junit 'services/core-service/target/surefire-reports/TEST-*SellOrderIntegrationTest.xml' }
            }
        }

        stage('Build versioned Docker images') {
            steps {
                sh '''
                    set -eu

                    short_sha=$(git rev-parse --short=8 HEAD)
                    image_tag="${BUILD_NUMBER:-local}-${short_sha}"
                    echo "$image_tag" > .image_tag

                    # Backend images build from the repo root so Maven can see the parent pom and libs/common.
                    for service in core-service auth-service market-service holdings-service gateway-service; do
                        docker build -t "lemarketjames/$service:$image_tag" -f "services/$service/Dockerfile" .
                        docker image inspect "lemarketjames/$service:$image_tag" >/dev/null
                    done
                    docker build -t "lemarketjames/frontend:$image_tag" ./apps/frontend
                    docker image inspect "lemarketjames/frontend:$image_tag" >/dev/null

                    echo "Built versioned images with tag: $image_tag"
                '''
            }
        }

        stage('Start application with Docker Compose') {
            steps {
                sh '''
                    set -eu
                    image_tag=$(cat .image_tag)

                    if docker compose version >/dev/null 2>&1; then
                        IMAGE_TAG="$image_tag" docker compose up -d --build
                    else
                        IMAGE_TAG="$image_tag" docker-compose up -d --build
                    fi
                '''
            }
        }

        stage('Verify PostgreSQL connection') {
            steps {
                sh '''
                    echo "PostgreSQL connection: host=db port=5432 database=lemarket user=lemarket"
                    if docker compose version >/dev/null 2>&1; then
                        compose() { docker compose "$@"; }
                    else
                        compose() { docker-compose "$@"; }
                    fi

                    for attempt in $(seq 1 30); do
                        if compose exec -T db pg_isready -U lemarket -d lemarket >/dev/null 2>&1; then
                            echo "PostgreSQL is accepting connections"
                            exit 0
                        fi
                        sleep 1
                    done

                    echo "PostgreSQL did not become ready"
                    compose logs db
                    exit 1
                '''
            }
        }

        stage('Apply schema updates') {
            steps {
                // Init scripts only run for an empty Postgres volume; CI keeps its volume.
                // Every script applied here must be idempotent (safe to run twice).
                sh '''
                    set -eu
                    if docker compose version >/dev/null 2>&1; then
                        docker compose exec -T db psql -v ON_ERROR_STOP=1 -U lemarket -d lemarket < database/schema/004_widen_ssn_for_hash.sql
                        docker compose exec -T db psql -v ON_ERROR_STOP=1 -U lemarket -d lemarket < database/schema/005_set_googl_non_tradable.sql
                        docker compose exec -T db psql -v ON_ERROR_STOP=1 -U lemarket -d lemarket < database/schema/006_market_simulation.sql
                        docker compose exec -T db psql -v ON_ERROR_STOP=1 -U lemarket -d lemarket < database/schema/007_lebronify_instruments.sql
                        docker compose exec -T db psql -v ON_ERROR_STOP=1 -U lemarket -d lemarket < database/schema/008_holdings_cost_basis.sql
                    else
                        docker-compose exec -T db psql -v ON_ERROR_STOP=1 -U lemarket -d lemarket < database/schema/004_widen_ssn_for_hash.sql
                        docker-compose exec -T db psql -v ON_ERROR_STOP=1 -U lemarket -d lemarket < database/schema/005_set_googl_non_tradable.sql
                        docker-compose exec -T db psql -v ON_ERROR_STOP=1 -U lemarket -d lemarket < database/schema/006_market_simulation.sql
                        docker-compose exec -T db psql -v ON_ERROR_STOP=1 -U lemarket -d lemarket < database/schema/007_lebronify_instruments.sql
                        docker-compose exec -T db psql -v ON_ERROR_STOP=1 -U lemarket -d lemarket < database/schema/008_holdings_cost_basis.sql
                    fi
                '''
            }
        }

        stage('Verify own-data isolation on PostgreSQL') {
            steps {
                sh 'mvn -B -pl services/core-service,services/auth-service,services/holdings-service -am -Dspring.profiles.active=postgres-test "-Dtest=OwnDataIntegrationTest,AuthPersistenceIntegrationTest,HoldingsOwnDataIntegrationTest" -Dsurefire.failIfNoSpecifiedTests=false test'
            }
            post {
                always {
                    junit 'services/core-service/target/surefire-reports/TEST-*OwnDataIntegrationTest.xml, services/auth-service/target/surefire-reports/TEST-*AuthPersistenceIntegrationTest.xml, services/holdings-service/target/surefire-reports/TEST-*HoldingsOwnDataIntegrationTest.xml'
                }
            }
        }

        stage('Wait for services to be running') {
            steps {
                sh '''
                    set -eu
                    
                    if docker compose version >/dev/null 2>&1; then
                        compose() { docker compose "$@"; }
                    else
                        compose() { docker-compose "$@"; }
                    fi

                    echo "Waiting for all containers to be running..."
                    for service in core-service auth-service market-service holdings-service gateway-service; do
                        echo "Checking if $service is running..."
                        max_attempts=30
                        attempt=0
                        while [ $attempt -lt $max_attempts ]; do
                            if compose ps $service 2>/dev/null | grep -q "Up"; then
                                echo "✓ $service is running"
                                break
                            fi
                            attempt=$((attempt + 1))
                            if [ $attempt -eq $max_attempts ]; then
                                echo "✗ $service failed to start"
                                echo "Container logs:"
                                compose logs $service || true
                                exit 1
                            fi
                            sleep 1
                        done
                    done

                    echo "All containers are running"
                '''
            }
        }

        stage('Run smoke test') {
            steps {
                sh '''
                    set -eu

                    if docker compose version >/dev/null 2>&1; then
                        compose() { docker compose "$@"; }
                    else
                        compose() { docker-compose "$@"; }
                    fi

                    echo "Container user and group:"
                    compose exec -T core-service id
                    compose exec -T auth-service id
                    compose exec -T market-service id
                    compose exec -T holdings-service id

                    # core-service :8081, auth-service :8082, gateway-service :8080, market-service :8083, holdings-service :8084
                    for port in 8081 8082 8080 8083 8084; do
                        echo "Waiting for health endpoint on port $port"
                        healthy=0
                        for attempt in $(seq 1 60); do
                            status=$(curl --silent --output /dev/null --write-out "%{http_code}" "http://localhost:$port/actuator/health" || true)
                            if [ "$status" = "200" ]; then
                                healthy=1
                                break
                            fi
                            sleep 1
                        done

                        if [ "$healthy" -ne 1 ]; then
                            echo "Service on port $port did not become healthy in time"
                            compose ps
                            compose logs core-service auth-service market-service holdings-service gateway-service
                            exit 1
                        fi
                    done

                    # Through the gateway, so routing to core-service is exercised too.
                    response=$(curl --fail --silent --show-error http://localhost:8089/)
                    echo "Spring Boot response: $response"
                    echo "$response" | grep -F "Hello from LeMarketJames!"

                    echo "Spring Boot container logs:"
                    compose logs core-service auth-service market-service holdings-service gateway-service
                '''
            }
        }

        stage('Verify sell order survives restart') {
            steps { sh 'bash scripts/verify-sell-order.sh' }
        }

        stage('Run quote API contract smoke test') {
            steps {
                sh '''
                    set -eu

                    # Through the gateway: register/login hit auth-service, the rest hits core-service.
                    base="http://localhost:8089"
                    user="ciuser$(date +%s)"
                    email="$user@example.com"

                    register_payload=$(cat <<JSON
{"username":"$user","password":"Pass123!","email":"$email","fullName":"CI User","streetAddress":"123 Main St","city":"Springfield","state":"IL","zipCode":"62701","country":"US","ssn":"123-45-6789","initialDeposit":500,"investmentExperience":"beginner","employmentStatus":"employed","dateOfBirth":"1990-01-01","phoneNumber":"(555) 123-4567"}
JSON
)

                    login_payload=$(cat <<JSON
{"username":"$email","password":"Pass123!"}
JSON
)

                    cookie_file=$(mktemp)
                    quote_ok_file=$(mktemp)
                    quote_not_found_file=$(mktemp)
                    trap 'rm -f "$cookie_file" "$quote_ok_file" "$quote_not_found_file"' EXIT

                    echo "Verifying unauthenticated quote request is denied"
                    unauth_status=$(curl --silent --output /dev/null --write-out "%{http_code}" "$base/api/quotes/AAPL")
                    test "$unauth_status" = "401"

                    echo "Registering CI user"
                    curl --silent --show-error --fail \
                        --request POST "$base/api/auth/register" \
                        --header "Content-Type: application/json" \
                        --data "$register_payload" \
                        >/dev/null

                    echo "Logging in and capturing auth cookie"
                    curl --silent --show-error --fail \
                        --cookie-jar "$cookie_file" \
                        --request POST "$base/api/auth/login" \
                        --header "Content-Type: application/json" \
                        --data "$login_payload" \
                        >/dev/null

                    echo "Verifying authenticated quote response contract"
                    quote_status=$(curl --silent --output "$quote_ok_file" --write-out "%{http_code}" \
                        --cookie "$cookie_file" \
                        "$base/api/quotes/AAPL")
                    test "$quote_status" = "200"
                    grep -q '"success":true' "$quote_ok_file"
                    grep -q '"symbol":"AAPL"' "$quote_ok_file"
                    grep -q '"price":' "$quote_ok_file"
                    grep -q '"lastUpdate":' "$quote_ok_file"

                    echo "Verifying unknown symbol contract"
                    quote_404_status=$(curl --silent --output "$quote_not_found_file" --write-out "%{http_code}" \
                        --cookie "$cookie_file" \
                        "$base/api/quotes/INVALID")
                    test "$quote_404_status" = "404"
                    grep -q '"success":false' "$quote_not_found_file"
                    grep -q '"error":"Symbol not found"' "$quote_not_found_file"
                '''
            }
        }

        stage('Run tradability API smoke test') {
            steps {
                sh '''
                    set -eu

                    # Through the gateway: register/login hit auth-service, the rest hits core-service.
                    base="http://localhost:8089"
                    user="ciusertrad$(date +%s)"
                    email="$user@example.com"

                    register_payload=$(cat <<JSON
{"username":"$user","password":"Pass123!","email":"$email","fullName":"CI User","streetAddress":"123 Main St","city":"Springfield","state":"IL","zipCode":"62701","country":"US","ssn":"123-45-6789","initialDeposit":500,"investmentExperience":"beginner","employmentStatus":"employed","dateOfBirth":"1990-01-01","phoneNumber":"(555) 123-4567","termsAccepted":true}
JSON
)

                    login_payload=$(cat <<JSON
{"username":"$email","password":"Pass123!"}
JSON
)

                    cookie_file=$(mktemp)
                    order_error_file=$(mktemp)
                    trap 'rm -f "$cookie_file" "$order_error_file"' EXIT

                    curl --silent --show-error --fail \
                        --request POST "$base/api/auth/register" \
                        --header "Content-Type: application/json" \
                        --data "$register_payload" \
                        >/dev/null

                    curl --silent --show-error --fail \
                        --cookie-jar "$cookie_file" \
                        --request POST "$base/api/auth/login" \
                        --header "Content-Type: application/json" \
                        --data "$login_payload" \
                        >/dev/null

                    if docker compose version >/dev/null 2>&1; then
                        compose() { docker compose "$@"; }
                    else
                        compose() { docker-compose "$@"; }
                    fi

                    account_id=$(compose exec -T db psql -At -U lemarket -d lemarket \
                        -c "SELECT a.account_id FROM accounts a JOIN clients c ON c.client_id = a.client_id WHERE c.username = '$user' LIMIT 1;")

                    test -n "$account_id"

                    order_payload=$(cat <<JSON
{"accountId":$account_id,"instrumentId":3,"orderType":"BUY","quantity":1}
JSON
)

                    order_status=$(curl --silent --output "$order_error_file" --write-out "%{http_code}" \
                        --cookie "$cookie_file" \
                        --request POST "$base/api/v1/orders" \
                        --header "Content-Type: application/json" \
                        --data "$order_payload")

                    test "$order_status" = "400"
                    grep -q '"success":false' "$order_error_file"
                    grep -q '"code":"NOT_TRADABLE"' "$order_error_file"
                '''
            }
        }
    }

    post {
        failure {
            // Capture errors from failed smoke requests before containers are removed.
            sh '''
                if docker compose version >/dev/null 2>&1; then
                    docker compose logs --tail=100 gateway-service auth-service core-service market-service holdings-service db || true
                elif command -v docker-compose >/dev/null 2>&1; then
                    docker-compose logs --tail=100 gateway-service auth-service core-service market-service holdings-service db || true
                fi
            '''
        }
        cleanup {
            sh '''
                if docker compose version >/dev/null 2>&1; then
                    docker compose down -v --rmi local --remove-orphans || true
                elif command -v docker-compose >/dev/null 2>&1; then
                    docker-compose down -v --rmi local --remove-orphans || true
                fi
            '''
        }
    }
}
