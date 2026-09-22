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
                    sh 'npm ci --legacy-peer-deps && npm test -- --watch=false && npm run build'
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
                    elif command -v docker-compose >/dev/null 2>&1; then
                        docker-compose down -v --remove-orphans
                    fi
                    set -e
                '''
            }
        }

        stage('Test with Maven') {
            steps {
                dir('apps/backend') {
                    sh '''
                        set -eu
                        mvn -B clean test

                        echo "=== BACKEND TESTS COMPLETED: PASS ==="
                        echo "=== BACKEND SUREFIRE SUMMARY ==="

                        if ls target/surefire-reports/TEST-*.xml >/dev/null 2>&1; then
                            grep -h '<testsuite ' target/surefire-reports/TEST-*.xml \
                                | sed -E 's/.*name="([^"]+)".*tests="([0-9]+)".*failures="([0-9]+)".*errors="([0-9]+)".*skipped="([0-9]+)".*/- \1: tests=\2 failures=\3 errors=\4 skipped=\5/'
                        else
                            echo "No surefire XML reports found"
                        fi
                    '''
                }
            }
            post {
                always {
                    junit 'apps/backend/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Run buy-order microservice tests') {
            steps {
                dir('apps/backend') {
                    sh '''
                        set -eu
                        mvn -B "-Dtest=BuyOrderControllerTest,OrderServiceTest" test

                        echo "=== BUY-ORDER MICROSERVICE TEST SUMMARY ==="
                        if ls target/surefire-reports/TEST-*BuyOrderControllerTest.xml >/dev/null 2>&1; then
                            grep -h '<testsuite ' target/surefire-reports/TEST-*BuyOrderControllerTest.xml \
                                | sed -E 's/.*name="([^"]+)".*tests="([0-9]+)".*failures="([0-9]+)".*errors="([0-9]+)".*skipped="([0-9]+)".*/- \1: tests=\2 failures=\3 errors=\4 skipped=\5/'
                        else
                            echo "BuyOrderControllerTest report not found"
                        fi
                    '''
                }
            }
            post {
                always {
                    junit 'apps/backend/target/surefire-reports/TEST-*BuyOrderControllerTest.xml'
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

        stage('Build versioned Docker images') {
            steps {
                sh '''
                    set -eu

                    short_sha=$(git rev-parse --short=8 HEAD)
                    image_tag="${BUILD_NUMBER:-local}-${short_sha}"
                    echo "$image_tag" > .image_tag

                    docker build -t "lemarketjames/backend:$image_tag" ./apps/backend
                    docker build -t "lemarketjames/frontend:$image_tag" ./apps/frontend

                    docker image inspect "lemarketjames/backend:$image_tag" >/dev/null
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
                    else
                        docker-compose exec -T db psql -v ON_ERROR_STOP=1 -U lemarket -d lemarket < database/schema/004_widen_ssn_for_hash.sql
                        docker-compose exec -T db psql -v ON_ERROR_STOP=1 -U lemarket -d lemarket < database/schema/005_set_googl_non_tradable.sql
                        docker-compose exec -T db psql -v ON_ERROR_STOP=1 -U lemarket -d lemarket < database/schema/006_market_simulation.sql
                    fi
                '''
            }
        }

        stage('Verify own-data isolation on PostgreSQL') {
            steps {
                dir('apps/backend') {
                    sh 'mvn -B -Dspring.profiles.active=postgres-test -Dtest=OwnDataIntegrationTest test'
                }
            }
            post {
                always { junit 'apps/backend/target/surefire-reports/TEST-*OwnDataIntegrationTest.xml' }
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
                    compose exec -T backend id

                    echo "Waiting for backend health endpoint"
                    healthy=0
                    for attempt in $(seq 1 60); do
                        status=$(curl --silent --output /dev/null --write-out "%{http_code}" http://localhost:8081/actuator/health || true)
                        if [ "$status" = "200" ]; then
                            healthy=1
                            break
                        fi
                        sleep 1
                    done

                    if [ "$healthy" -ne 1 ]; then
                        echo "Backend did not become healthy in time"
                        compose ps
                        compose logs backend
                        exit 1
                    fi

                    response=$(curl --fail --silent --show-error http://localhost:8081/)
                    echo "Spring Boot response: $response"
                    echo "$response" | grep -F "Hello from LeMarketJames!"

                    echo "Spring Boot container logs:"
                    compose logs backend
                '''
            }
        }

        stage('Run quote API contract smoke test') {
            steps {
                sh '''
                    set -eu

                    base="http://localhost:8081"
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

                    base="http://localhost:8081"
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
                    docker compose logs --tail=100 backend db || true
                elif command -v docker-compose >/dev/null 2>&1; then
                    docker-compose logs --tail=100 backend db || true
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
