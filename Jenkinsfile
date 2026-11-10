def dockerGid = ''
node {
    dockerGid = sh(
            script: "stat -c '%g' /var/run/docker.sock",
            returnStdout: true
    ).trim()
}

pipeline {
    agent {
        docker {
            image 'maven:3.10.0-sapmachine-25'
            args "--user 0:0 -v /var/run/docker.sock:/var/run/docker.sock --group-add ${dockerGid} -v maven-repository:/root/.m2"
        }
    }

    parameters {
        choice(
                name: 'DEPLOY_TARGET',
                choices: ['NONE', 'TEST', 'PROD'],
                description: 'Deployment target. Select NONE to build, test, and package without deploying.'
        )
    }

    environment {
        SERVICE_NAME = "unit-billing"
        PLUGIN_DIR   = "/opt/${SERVICE_NAME}/plugins"
        MAVEN_ARGS   = "-Dmaven.repo.local=${WORKSPACE}/.m2/repository"
    }

    stages {
        stage('Build') {
            steps {
                echo '=== Compiling unit-billing-plugins (all modules) ==='
                sh 'mvn clean compile $MAVEN_ARGS'
            }
        }

        stage('Test') {
            steps {
                echo '=== Running unit tests (all modules) ==='
                sh 'mvn test $MAVEN_ARGS'
            }
        }

        stage('Package') {
            steps {
                echo '=== Packaging all plugins ==='
                sh 'mvn package -DskipTests $MAVEN_ARGS'
            }
        }

        stage('Deploy to Test') {
            when {
                expression {
                    params.DEPLOY_TARGET == 'TEST'
                }
            }
            steps {
                echo '=== Deploying plugins to TEST ==='
                deploy('test-host', 'test-known-hosts', 'test-ssh-key')
            }
        }

        stage('Deploy to Prod') {
            when {
                expression {
                    params.DEPLOY_TARGET == 'PROD'
                }
            }
            steps {
                echo '=== Deploying plugins to PROD ==='
                deploy('prod-host', 'prod-known-hosts', 'prod-ssh-key')
            }
        }
    }

    post {
        success {
            echo 'Success!'
        }
        failure {
            echo 'Build failed. Please check the logs for details.'
        }
    }
}

def deploy(String hostCredentialId, String knownHostsCredentialId, String sshCredentialId) {
    withCredentials([
            string(credentialsId: hostCredentialId, variable: 'DEPLOY_HOST'),
            file(credentialsId: knownHostsCredentialId, variable: 'KNOWN_HOSTS')
    ]) {
        sshagent(credentials: [sshCredentialId]) {
            // email-notifier
            sh '''
                scp -o UserKnownHostsFile="$KNOWN_HOSTS" -o StrictHostKeyChecking=yes \\
                    notification-plugins/email-notifier/target/email-notifier-*.jar "$DEPLOY_HOST:$PLUGIN_DIR/email-notifier.jar.new"

                ssh -o UserKnownHostsFile="$KNOWN_HOSTS" -o StrictHostKeyChecking=yes "$DEPLOY_HOST" \
                    "sudo systemctl stop '$SERVICE_NAME' && \
                     sudo mv '$PLUGIN_DIR/email-notifier.jar.new' '$PLUGIN_DIR/email-notifier.jar' && \
                     sudo chown 'unitbilling:unitbilling' '$PLUGIN_DIR/email-notifier.jar' && \
                     sudo systemctl start '$SERVICE_NAME' && \
                     sudo systemctl status '$SERVICE_NAME' --no-pager"
            '''
        }
    }
}