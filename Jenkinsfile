pipeline {
    agent any

    environment {
        IMAGE = 'le-market-james'
        CONTAINER = 'le-market-james-test'
    }

    stages {
        stage('Build image') {
            steps {
                sh 'docker build -t "$IMAGE:$BUILD_NUMBER" .'
            }
        }

        stage('Run smoke test') {
            steps {
                sh 'docker run -d --name "$CONTAINER" -p 8081:8081 "$IMAGE:$BUILD_NUMBER"'
                sh 'curl --fail --retry 10 --retry-delay 1 http://localhost:8081/ | grep -F "Hello from LeMarketJames!"'
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