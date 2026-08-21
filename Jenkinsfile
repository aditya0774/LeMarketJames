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
                sh 'docker run --rm --name "$CONTAINER" "$IMAGE:$BUILD_NUMBER" | grep -F "Hello from LeMarketJames!"'
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