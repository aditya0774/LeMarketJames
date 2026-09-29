pipeline {
    agent any
    tools {
        jdk 'JDK21'
        nodejs 'NodeJS'
    }
    environment {
        MAVEN_OPTS = '-Dmaven.repo.local=.m2/repository'
        NPM_CONFIG_CACHE = "${WORKSPACE}/.npm"
    }

    stages {
        stage('Preflight checks') {
            steps {
                sh '''
                    set -eu
                    java -version
                    mvn -version
                    node --version
                    npm --version
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

        stage('Collect change scope') {
            steps {
                sh '''
                    set -eu
                    mkdir -p ci/reports

                    base_ref="${CHANGE_TARGET:-main}"
                    if git show-ref --verify --quiet "refs/remotes/origin/${base_ref}"; then
                        git diff --name-only "origin/${base_ref}...HEAD" > ci/reports/changed-files.txt
                    elif git rev-parse --verify "HEAD~1" >/dev/null 2>&1; then
                        git diff --name-only "HEAD~1...HEAD" > ci/reports/changed-files.txt
                    else
                        git ls-files > ci/reports/changed-files.txt
                    fi

                    touches_frontend=false
                    touches_backend=false
                    touches_db_or_contract=false

                    grep -Eq '^apps/frontend/' ci/reports/changed-files.txt && touches_frontend=true || true
                    grep -Eq '^(services/|libs/|pom.xml)' ci/reports/changed-files.txt && touches_backend=true || true
                    grep -Eq '^(database/schema/|contracts/|API-CONTRACTS.md|docker-compose.yml)' ci/reports/changed-files.txt && touches_db_or_contract=true || true

                    cat > ci/reports/change-scope.md <<EOF
# Change Scope

Generated: $(date -u +"%Y-%m-%dT%H:%M:%SZ")

- Base ref: ${base_ref}
- Frontend changed: ${touches_frontend}
- Backend changed: ${touches_backend}
- DB/contract changed: ${touches_db_or_contract}

Changed files:
EOF

                    sed 's/^/- /' ci/reports/changed-files.txt >> ci/reports/change-scope.md
                '''
            }
            post {
                always {
                    archiveArtifacts artifacts: 'ci/reports/changed-files.txt, ci/reports/change-scope.md', allowEmptyArchive: false
                }
            }
        }

        stage('Test and build Angular') {
            steps {
                dir('apps/frontend') {
                    sh 'npm ci --no-audit --no-fund && npm test -- --watch=false --code-coverage && npm run build'
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

        stage('Prepare backend test dependencies') {
            steps {
                // Build shared libraries once to avoid repeating -am work in parallel lanes.
                sh '''
                    set -eu
                    mvn -B -pl libs/common,libs/market-client -am -DskipTests install
                '''
            }
        }

        stage('Test backend units (parallel)') {
            parallel {
                stage('Test libs') {
                    steps {
                        sh '''
                            set -eu
                            mvn -B -pl libs/common,libs/market-client test
                        '''
                    }
                }

                stage('Test core service units') {
                    steps {
                        sh '''
                            set -eu
                            mvn -B -pl services/core-service test
                        '''
                    }
                }

                stage('Test auth service units') {
                    steps {
                        sh '''
                            set -eu
                            mvn -B -pl services/auth-service test
                        '''
                    }
                }

                stage('Test market service units') {
                    steps {
                        sh '''
                            set -eu
                            mvn -B -pl services/market-service test
                        '''
                    }
                }

                stage('Test holdings service units') {
                    steps {
                        sh '''
                            set -eu
                            mvn -B -pl services/holdings-service test
                        '''
                    }
                }

                stage('Test gateway service units') {
                    steps {
                        sh '''
                            set -eu
                            mvn -B -pl services/gateway-service test
                        '''
                    }
                }
            }
            post {
                always {
                    sh '''
                        set +e
                        echo "=== BACKEND UNIT SUREFIRE SUMMARY ==="
                        if ls libs/*/target/surefire-reports/TEST-*.xml services/*/target/surefire-reports/TEST-*.xml >/dev/null 2>&1; then
                            grep -h '<testsuite ' libs/*/target/surefire-reports/TEST-*.xml services/*/target/surefire-reports/TEST-*.xml \
                                | sed -E 's/.*name="([^"]+)".*tests="([0-9]+)".*failures="([0-9]+)".*errors="([0-9]+)".*skipped="([0-9]+)".*/- \1: tests=\2 failures=\3 errors=\4 skipped=\5/'
                        else
                            echo "No surefire XML reports found"
                        fi
                        set -e
                    '''

                    junit 'libs/*/target/surefire-reports/*.xml, services/*/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Run backend integration tests on shared PostgreSQL') {
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

                    # Keep one Maven invocation so integration tests share startup work.
                    mvn -B -pl services/core-service,services/auth-service,services/holdings-service -am \
                        -Dspring.profiles.active=postgres-test \
                        "-Dtest=BuyOrderIntegrationTest,SellOrderIntegrationTest,OwnDataIntegrationTest,AuthPersistenceIntegrationTest,HoldingsOwnDataIntegrationTest" \
                        -Dsurefire.failIfNoSpecifiedTests=false test
                '''
            }
            post {
                always {
                    junit 'services/core-service/target/surefire-reports/TEST-*OrderIntegrationTest.xml, services/core-service/target/surefire-reports/TEST-*OwnDataIntegrationTest.xml, services/auth-service/target/surefire-reports/TEST-*AuthPersistenceIntegrationTest.xml, services/holdings-service/target/surefire-reports/TEST-*HoldingsOwnDataIntegrationTest.xml'
                }
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
                        # Images are built in the previous stage; avoid rebuilding here.
                        IMAGE_TAG="$image_tag" docker compose up -d
                    else
                        IMAGE_TAG="$image_tag" docker-compose up -d
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
                // Every script from 004 on must be idempotent (safe to run twice); 001-003 are not,
                // so they only ever run on an empty volume. New migrations are picked up automatically.
                sh '''
                    set -eu
                    if docker compose version >/dev/null 2>&1; then
                        compose() { docker compose "$@"; }
                    else
                        compose() { docker-compose "$@"; }
                    fi
                    for f in database/schema/0*.sql; do
                        case "$(basename "$f")" in 001_*|002_*|003_*) continue ;; esac
                        echo "Applying $f"
                        compose exec -T db psql -q -v ON_ERROR_STOP=1 -U lemarket -d lemarket < "$f"
                    done
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

                    # Use host-published ports; gateway-service maps host 8089 to container 8080.
                    for port in 8081 8082 8089 8083 8084; do
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

        // Drives the real app in a browser through the nginx frontend on :4200, the same origin a
        // user opens. Runs right after the smoke test so the whole stack is known to be healthy.
        stage('Run Playwright E2E tests') {
            steps {
                sh '''
                    set -eu
                    # The official image ships Chromium and its OS libraries, so the agent needs only
                    # Docker. Its tag must match @playwright/test in apps/e2e/package.json.
                    # --network host lets the browser reach localhost:4200 on the agent; running as
                    # the Jenkins user keeps the report files deletable by the workspace cleanup.
                    docker run --rm --network host --ipc=host \
                        --user "$(id -u):$(id -g)" -e HOME=/tmp -e CI=true \
                        -e E2E_BASE_URL=http://localhost:4200 \
                        -v "$PWD/apps/e2e:/e2e" -w /e2e \
                        mcr.microsoft.com/playwright:v1.63.0-noble \
                        sh -c 'npm ci --no-audit --no-fund && npx playwright test'
                '''
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'apps/e2e/results/junit.xml'
                    // HTML report plus traces, screenshots and videos of any failed test.
                    archiveArtifacts artifacts: 'apps/e2e/playwright-report/**, apps/e2e/test-results/**', allowEmptyArchive: true
                }
            }
        }

        stage('Verify buy order survives restart') {
            steps { sh 'bash scripts/verify-buy-order.sh' }
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

                    # The seed data set's suspended stock (contracts/C3-seed-data.md).
                    non_tradable_id=$(compose exec -T db psql -At -U lemarket -d lemarket \
                        -c "SELECT instrument_id FROM instruments WHERE ticker = 'CAVS' AND NOT tradable;")

                    test -n "$non_tradable_id"

                    order_payload=$(cat <<JSON
{"accountId":$account_id,"instrumentId":$non_tradable_id,"orderType":"BUY","quantity":1}
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

        stage('Generate CI test evidence') {
            steps {
                sh '''
                    set -eu
                    bash scripts/ci/collect-coverage.sh
                    bash scripts/ci/test-inventory.sh
                '''
            }
            post {
                always {
                    archiveArtifacts artifacts: 'ci/reports/**', allowEmptyArchive: false
                }
            }
        }
    }

    post {
        always {
            sh '''
                set -eu
                mkdir -p ci/reports
            '''
            script {
                def durationMs = currentBuild.duration ?: (System.currentTimeMillis() - currentBuild.startTimeInMillis)
                def startedAt = new Date(currentBuild.startTimeInMillis).format("yyyy-MM-dd'T'HH:mm:ssXXX")
                writeFile file: 'ci/reports/pipeline-timing.md', text: """# Pipeline Timing\n\nGenerated: ${new Date().format("yyyy-MM-dd'T'HH:mm:ssXXX")}\n\n- Build: ${env.JOB_NAME} #${env.BUILD_NUMBER}\n- Result: ${currentBuild.currentResult}\n- Started: ${startedAt}\n- Duration ms: ${durationMs}\n- Duration human: ${currentBuild.durationString}\n"""
            }
            archiveArtifacts artifacts: 'ci/reports/pipeline-timing.md', allowEmptyArchive: true
        }
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
