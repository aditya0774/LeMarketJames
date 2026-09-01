pipeline {
    agent any
    tools {
        jdk 'JDK21'
    }

    stages {
        stage('Test with Maven') {
            steps {
                sh 'mvn -B clean test'
            }
        }

        stage('Build image') {
            steps {
                sh 'mvn test'
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

        stage('Start application with Docker Compose') {
            steps {
                sh '''
                    if docker compose version >/dev/null 2>&1; then
                        docker compose up -d --build
                    else
                        docker-compose up -d --build
                    fi
                '''
            }
        }

        stage('Verify PostgreSQL connection') {
            steps {
                sh '''
                    echo "PostgreSQL connection: host=db port=5432 database=paysprint user=paysprint"
                    if docker compose version >/dev/null 2>&1; then
                        compose() { docker compose "$@"; }
                    else
                        compose() { docker-compose "$@"; }
                    fi

                    for attempt in $(seq 1 30); do
                        if compose exec -T db pg_isready -U paysprint -d paysprint >/dev/null 2>&1; then
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

        stage('Run smoke test') {
            steps {
                sh '''
                    echo "Container user and group:"
                    if docker compose version >/dev/null 2>&1; then
                        docker compose exec -T app id
                    else
                        docker-compose exec -T app id
                    fi
                    response=$(curl --fail --silent --show-error --retry 15 --retry-all-errors --retry-delay 1 http://localhost:8081/)
                    echo "Spring Boot response: $response"
                    echo "$response" | grep -F "Hello from LeMarketJames!"
                    echo "Spring Boot container logs:"
                    if docker compose version >/dev/null 2>&1; then
                        docker compose logs app
                    else
                        docker-compose logs app
                    fi
                '''
            }
        }
    }

    post {
        always {
            sh '''
                if docker compose version >/dev/null 2>&1; then
                    docker compose down --rmi local --remove-orphans || true
                elif command -v docker-compose >/dev/null 2>&1; then
                    docker-compose down --rmi local --remove-orphans || true
                fi
            '''
        }
    }
}