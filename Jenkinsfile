pipeline {
    agent any
    tools {
        jdk 'JDK21'
    }

    stages {
        stage('Test Spring Boot application') {
            steps {
                sh 'mvn test'
            }
        }

        stage('Start application with Docker Compose') {
            steps {
                sh 'docker compose up -d --build'
            }
        }

        stage('Run smoke test') {
            steps {
                sh '''
                    response=$(curl --fail --silent --show-error --retry 15 --retry-all-errors --retry-delay 1 http://localhost:8081/)
                    echo "Spring Boot response: $response"
                    echo "$response" | grep -F "Hello from LeMarketJames!"
                    echo "Spring Boot container logs:"
                    docker compose logs app
                '''
            }
        }
    }

    post {
        always {
            sh 'docker compose down --rmi local --remove-orphans || true'
        }
    }
}