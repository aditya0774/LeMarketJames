// Jenkins declarative pipeline for deploying mission-service directly
// to the Linux server on which Jenkins is running.
//
// Assumptions:
//   - Jenkins is running on this Linux host on port 8080.
//   - Docker is installed and the Jenkins user can run Docker commands.
//   - The application container listens on port 8080 internally.
//   - The application is exposed on host port 8081 to avoid conflicting
//     with Jenkins on host port 8080.
//   - mission-ui exists in the repository if the Acceptance Tests stage
//     is required.

pipeline {
    agent any

    environment {
        DEPLOY_ENV = 'test'
        SPRING_PROFILES_ACTIVE = 'test'

        // Example service configuration
        DB_HOST = 'localhost'
        DB_PORT = '5432'
        DB_NAME = 'helloworld_wealth'
        DB_USER = 'postgres'
        DB_PASSWORD = 'admin'

        //TWELVE Data API key
        TWELVE_DATA_API_KEY = credentials('twelve-data-api-key')
        
        // Docker image tag
        IMAGE_TAG = "${BUILD_NUMBER}"

        DEPLOYED_HW_URL='http://localhost:4200'
        DEPLOYED_HWR_URL='http://localhost:5200'
        DEPLOYED_HWA_URL='http://localhost:3000'
    }

    tools {
        nodejs 'NodeJS'
    }
    
    stages {
        stage('Checkout') {
            steps {
                checkout scm

                sh '''
                    echo "Current directory:"
                    pwd

                    echo "Workspace contents:"
                    ls -la

                    echo "==========DB CONNECTION =========="
                    echo "$DB_NAME"
                    echo "$DB_USER"
                    
                '''
            }
        }

        stage('Config Pipeline') {
            steps {
                
               script {
                    env.IMAGE_TAG = sh(
                        script: 'git rev-parse --short HEAD',
                        returnStdout: true
                    ).trim()

                    echo "Building hello-world:${env.IMAGE_TAG}"
               }
            }
        }

        stage('Start Database and Initialize') {
            steps {
                sh '''
                    docker compose up -d postgres

                    echo "Waiting for PostgreSQL..."
                    until docker compose exec -T postgres \
                        pg_isready -U "$DB_USER" -d "$DB_NAME"
                    do
                        sleep 2
                    done
                    docker compose exec -T postgres \
                        psql -U "$DB_USER" -d "$DB_NAME" \
                        -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"

                    docker compose exec -T postgres \
                        psql -U "$DB_USER" -d "$DB_NAME" \
                        < ./test-data/enterprise-schema.sql
                '''
            }
        }

        stage('Validate tables and data') {
            steps {
                sh '''
                    docker compose exec -T postgres \
                      psql -U "$DB_USER" -d "$DB_NAME" \
                      -c "\\dt"

                    docker compose exec -T postgres \
                      psql -U "$DB_USER" -d "$DB_NAME" \
                      -c "SELECT COUNT(*) FROM clients;"
                '''
            }
        }
        
        stage('Build Docker Images') {
            steps {
                sh '''
                    docker compose build hello-world
                    docker compose build hello-world-rpt
                    docker compose build hello-world-auth
                    docker compose build market-service
                    
                    echo "$IMAGE_TAG" > image-tag.txt
                '''
            }
        }

        stage('Deploy') {
            steps {
                sh '''
                   set -eu

                   IMAGE_TAG=$(cat image-tag.txt)
                   export IMAGE_TAG

                   echo "Deploying hello-world:$IMAGE_TAG"
                   docker compose up -d --no-build hello-world

                   echo "Deploying hello-world-rpt:$IMAGE_TAG"
                   docker compose up -d --no-build hello-world-rpt

                   echo "Deploying hello-world-auth:$IMAGE_TAG"
                   docker compose up -d --no-build hello-world-auth

                   echo "Deploying market-service:$IMAGE_TAG"
                   docker compose up -d --no-build market-service

                   echo "Application container started:"

                   docker compose ps 

                   echo "Show any containers that may have started but exited"
                   docker compose ps -a
                   
                '''
            }
        }

        stage('Verify Deployment') {
            steps {
                sh '''
                   set -eu

                   IMAGE_TAG=$(cat image-tag.txt)
                   export IMAGE_TAG

                   echo "Waiting for applications to start..."
                   sleep 10

                   echo "Checking container status..."
                   docker compose ps

                   echo "Checking Hello World..."
                   curl --fail http://localhost:4200

                   echo "Checking Hello World Reporting..."
                   curl --fail http://localhost:5200

                   echo "Checking Hello World Authorization..."
                   curl --fail http://localhost:3000/api

                   echo "Checking Market Service..."
                   curl --fail http://localhost:8000/health


                   echo "All applications are responding."
                 '''
            }
        }

        // Reuses the existing Playwright acceptance tests against the
        // application deployed on this Linux server.
        stage('Acceptance Tests') {
            steps {
                sh '''
                    set -eu

                    echo "========== TESTING STOCK QUOTE SVC =========="
                    curl --fail "http://localhost:8000/api/market/quotes?symbols=AAPL"
              
                    echo "========== TESTING HELLO WORLD AUTH =========="
                    docker exec hello-world-auth npm test -- --runInBand
                    
                    echo "========== TESTING HELLO WORLD =========="
                    cd hello-world
                    npm ci
                    
                    npx playwright install chromium
                    PLAYWRIGHT_TEST_BASE_URL="$DEPLOYED_HW_URL" npx playwright test

                    echo "========== TESTING HELLO WORLD REPORTING =========="
                    cd ../hello-world-rpt
                    npm ci
                    
                    npx playwright install chromium
                    PLAYWRIGHT_TEST_BASE_URL="$DEPLOYED_HWR_URL" npx playwright test
                '''
            }
        }
    }

    post {
        always {
            sh '''
                echo "Stopping Docker containers..."
                docker compose down
            '''
        }

        success {
            echo 'HELLO WORLD Pipeline succeeded.'
        }
        
        failure {
               sh '''
                  echo "HELLO WORLD Pipeline failed."

                  IMAGE_TAG=$(cat image-tag.txt 2>/dev/null || true)
                  export IMAGE_TAG

                  echo "========== CONTAINER STATUS =========="
                  docker compose ps -a || true

                  echo "========== HELLO WORLD LOGS =========="
                  docker compose logs --tail=100 hello-world || true

                  echo "========== HELLO WORLD REPORTING LOGS =========="
                  docker compose logs --tail=100 hello-world-rpt || true

                  echo "========== HELLO WORLD AUTH LOGS =========="
                  docker compose logs --tail=100 hello-world-auth || true

                  echo "========== HELLO WORLD MARKET SERVICE =========="
                  docker compose logs --tail=100 market-service || true

                  echo "========== TEARDOWN =========="
                  docker compose down || true
               '''
        }
    }
}