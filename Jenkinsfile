pipeline {
    agent any

    environment {
        IMAGE = 'le-market-james'
        CONTAINER = 'le-market-james-test'
    }

    stages {
        stage('Test Spring Boot application') {
            steps {
                sh 'mvn test'
            }
        }

        stage('Build image') {
            steps {
                sh 'docker build -t "$IMAGE:$BUILD_NUMBER" .'
            }
        }

        stage('Run smoke test') {
            steps {
                sh 'docker run -d --name "$CONTAINER" -p 8081:8081 "$IMAGE:$BUILD_NUMBER"'
                sh '''
                    response=$(curl --fail --silent --show-error --retry 15 --retry-all-errors --retry-delay 1 http://localhost:8081/)
                    echo "Spring Boot response: $response"
                    echo "$response" | grep -F "Hello from LeMarketJames!"
                    echo "Spring Boot container logs:"
                    docker logs "$CONTAINER"
                '''
            }
        }
    }

    post {
        always {
            sh 'docker rm -f "$CONTAINER" 2>/dev/null || true'
            sh 'docker image rm "$IMAGE:$BUILD_NUMBER" 2>/dev/null || true'
        }
    }
}