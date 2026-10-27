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
        string(
                name: 'API_BRANCH',
                defaultValue: 'develop',
                description: 'Branch of unit-billing repository to fetch unit-billing-plugin-api from.'
        )
    }

    environment {
        SERVICE_NAME        = 'unit-billing'
        PLUGIN_OUTPUT_DIR   = 'dist/plugins'
        MAVEN_ARGS          = "-Dmaven.repo.local=${WORKSPACE}/.m2/repository"
    }

    stages {
        stage('Prepare API Dependency') {
            environment {
                API_BRANCH_NAME = "${params.API_BRANCH}"
            }
            steps {
                echo "=== Building and installing unit-billing-plugin-api from branch: ${params.API_BRANCH} ==="
                sh '''#!/usr/bin/env bash
                    set -euo pipefail

                    rm -rf /tmp/unit-billing

                    git clone --depth 1 -b "$API_BRANCH_NAME" https://github.com/m000gg/unit-billing.git /tmp/unit-billing
                    cd /tmp/unit-billing/unit-billing-plugin-api
                    mvn clean install -DskipTests

                    rm -rf /tmp/unit-billing
                '''
            }
        }

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

                echo '=== Collecting plugin JARs ==='
                sh '''#!/usr/bin/env bash
                    set -euo pipefail

                    rm -rf "$PLUGIN_OUTPUT_DIR"
                    mkdir -p "$PLUGIN_OUTPUT_DIR"

                    mapfile -d '' plugin_jars < <(
                        find . -type f -path '*/target/*.jar' \
                            ! -name 'original-*.jar' \
                            -print0
                    )

                    if ((${#plugin_jars[@]} == 0)); then
                        echo "No plugin JAR files found."
                        exit 1
                    fi

                    for jar in "${plugin_jars[@]}"; do
                        jar_name="$(basename "$jar")"
                        destination="$PLUGIN_OUTPUT_DIR/$jar_name"

                        if [[ -e "$destination" ]]; then
                            echo "Duplicate plugin JAR name: $jar_name"
                            exit 1
                        fi

                        cp "$jar" "$destination"
                        echo "Collected: $jar"
                    done

                    echo "Plugin JARs ready in $PLUGIN_OUTPUT_DIR:"
                    ls -1 "$PLUGIN_OUTPUT_DIR"
                '''
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
            sh '''#!/usr/bin/env bash
                set -euo pipefail

                archive="${WORKSPACE}/unit-billing-plugins-${BUILD_NUMBER}.tar.gz"
                remote_archive="/tmp/unit-billing-plugins-${BUILD_NUMBER}.tar.gz"
                env_file="/opt/${SERVICE_NAME}/.env"

                tar -czf "$archive" -C "$PLUGIN_OUTPUT_DIR" .

                scp \
                    -o UserKnownHostsFile="$KNOWN_HOSTS" \
                    -o StrictHostKeyChecking=yes \
                    "$archive" \
                    "$DEPLOY_HOST:$remote_archive"

                ssh \
                    -o UserKnownHostsFile="$KNOWN_HOSTS" \
                    -o StrictHostKeyChecking=yes \
                    "$DEPLOY_HOST" \
                    "sudo bash -s -- '$remote_archive' '$env_file' '$SERVICE_NAME'" <<'REMOTE_SCRIPT'
                set -euo pipefail

                archive="$1"
                env_file="$2"
                service_name="$3"

                if [[ ! -f "$env_file" ]]; then
                    echo "Environment file not found: $env_file"
                    exit 1
                fi

                set -a
                source "$env_file"
                set +a

                plugin_dir="${PLUGIN_DIR:-${UNITBILLING_CONFIGURATION_PLUGINS_DIRECTORY:-}}"

                if [[ -z "$plugin_dir" ]]; then
                    echo "Plugin directory is missing from $env_file"
                    exit 1
                fi

                if [[ "$plugin_dir" != /* ]]; then
                    plugin_dir="/opt/$service_name/$plugin_dir"
                fi

                staging_dir="$(mktemp -d)"
                trap 'rm -rf "$staging_dir"; rm -f "$archive"' EXIT

                tar -xzf "$archive" -C "$staging_dir"

                shopt -s nullglob
                plugin_jars=("$staging_dir"/*.jar)

                if ((${#plugin_jars[@]} == 0)); then
                    echo "No plugin JAR files found in deployment archive."
                    exit 1
                fi

                echo "Deploying ${#plugin_jars[@]} plugin JAR(s) to $plugin_dir"

                systemctl stop "$service_name"
                mkdir -p "$plugin_dir"

                install \
                    -o unitbilling \
                    -g unitbilling \
                    -m 0644 \
                    "${plugin_jars[@]}" \
                    "$plugin_dir/"

                systemctl start "$service_name"
                systemctl status "$service_name" --no-pager
                REMOTE_SCRIPT
            '''
        }
    }
}