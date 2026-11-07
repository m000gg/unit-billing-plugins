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

    options {
        disableConcurrentBuilds()
    }

    parameters {
        choice(
                name: 'DEPLOY_TARGET',
                choices: ['NONE', 'TEST', 'PROD'],
                description: 'Deployment target. Select NONE to build, test, and package without deploying.'
        )
    }

    environment {
        SERVICE_NAME      = 'unit-billing'
        PLUGIN_OUTPUT_DIR = 'dist/plugins'
        MAVEN_ARGS        = "-Dmaven.repo.local=${WORKSPACE}/.m2/repository"
    }

    stages {
        stage('Prepare API Dependency') {
            steps {
                echo '=== Installing unit-billing-plugin-api (version from pom) ==='
                sh '''#!/usr/bin/env bash
                    set -euo pipefail

                    # Version is defined in the plugins parent pom: <unit-billing.api.version>0.1.0</unit-billing.api.version>
                    api_version="$(mvn -q -N help:evaluate -Dexpression=unit-billing.api.version -DforceStdout $MAVEN_ARGS)"

                    if [[ ! "$api_version" =~ ^[0-9]+\\.[0-9]+\\.[0-9]+$ ]]; then
                        echo "unit-billing.api.version must be a release version like 0.1.0, got: '$api_version'"
                        exit 1
                    fi

                    # The deploy stage reads the version from this file
                    echo "$api_version" > .api-version
                    echo "plugin-api version: $api_version"

                    api_jar="$WORKSPACE/.m2/repository/com/m000gg/unit-billing-plugin-api/$api_version/unit-billing-plugin-api-$api_version.jar"
                    if [[ -f "$api_jar" ]]; then
                        echo "plugin-api $api_version is already installed, skipping"
                        exit 0
                    fi

                    rm -rf /tmp/unit-billing
                    git clone --depth 1 --branch "v$api_version" https://github.com/m000gg/unit-billing.git /tmp/unit-billing

                    cd /tmp/unit-billing

                    # 1. Root parent POM only (-N), without building other services
                    mvn install -N -DskipTests $MAVEN_ARGS

                    # 2. plugin-api module
                    cd unit-billing-plugin-api
                    mvn install -DskipTests $MAVEN_ARGS
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
                            ! -path './.m2/*' \
                            ! -name 'original-*.jar' \
                            ! -name '*-sources.jar' \
                            ! -name '*-javadoc.jar' \
                            ! -name '*-tests.jar' \
                            -print0
                    )

                    if ((${#plugin_jars[@]} == 0)); then
                        echo "No plugin JAR files found."
                        exit 1
                    fi

                    for jar in "${plugin_jars[@]}"; do
                        jar_name="$(basename "$jar")"
                        destination="$PLUGIN_OUTPUT_DIR/$jar_name"

                        # plugin-api is provided by core, a plugin must not contain it
                        if grep -q '^com/m000gg/plugin/api/' < <(jar tf "$jar"); then
                            echo "ERROR: $jar contains plugin-api classes. Use scope=provided."
                            exit 1
                        fi

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

                api_version="$(cat .api-version)"
                archive="${WORKSPACE}/unit-billing-plugins-${BUILD_NUMBER}.tar.gz"
                deploy_script="${WORKSPACE}/remote_deploy.sh"
                remote_archive="/tmp/unit-billing-plugins-${BUILD_NUMBER}.tar.gz"
                remote_script="/tmp/remote_deploy-${BUILD_NUMBER}.sh"
                env_file="/opt/${SERVICE_NAME}/.env"

                trap 'rm -f "$archive" "$deploy_script"' EXIT

                tar -czf "$archive" -C "$PLUGIN_OUTPUT_DIR" .

                # Script that runs on the target VM. Keep it at column 0 and without backslashes.
                cat << 'EOF' > "$deploy_script"
#!/usr/bin/env bash
set -euo pipefail

archive="$1"
env_file="$2"
service_name="$3"
api_version="$4"

app_dir="/opt/$service_name"
core_jar="$app_dir/$service_name.jar"

# Must return 2xx when the app is really up. Change it if there is no actuator.
health_url="http://127.0.0.1:8080/actuator/health"

script_path="$0"
staging_dir="$(mktemp -d)"
plugin_dir=""
backup_dir=""
started=0

rollback() {
    echo "Deploy failed, restoring previous plugins"
    systemctl stop "$service_name" || true
    if [[ -d "$backup_dir" ]]; then
        rm -rf "$plugin_dir"
        mv "$backup_dir" "$plugin_dir"
    fi
    systemctl start "$service_name" || true
}

on_exit() {
    rc=$?
    if [[ "$rc" -ne 0 && "$started" -eq 1 ]]; then
        rollback
    fi
    rm -rf "$staging_dir"
    rm -f "$archive" "$script_path"
    exit "$rc"
}
trap on_exit EXIT

[[ -f "$env_file" ]] || { echo "Environment file not found: $env_file"; exit 1; }
[[ -f "$core_jar" ]] || { echo "Core JAR not found: $core_jar"; exit 1; }

# Read the plugin dir from .env without sourcing it
plugin_dir="$(grep -E '^PLUGIN_DIR=' "$env_file" | tail -n1 | cut -d= -f2- | tr -d '"' | tr -d "'" || true)"
if [[ -z "$plugin_dir" ]]; then
    plugin_dir="$(grep -E '^UNITBILLING_CONFIGURATION_PLUGINS_DIRECTORY=' "$env_file" | tail -n1 | cut -d= -f2- | tr -d '"' | tr -d "'" || true)"
fi
[[ -n "$plugin_dir" ]] || { echo "Plugin directory is missing from $env_file"; exit 1; }

if [[ "$plugin_dir" != /* ]]; then
    plugin_dir="$app_dir/$plugin_dir"
fi
plugin_dir="${plugin_dir%/}"

# Old JARs in this directory get deleted, so refuse paths like "/" or "/opt"
[[ "$plugin_dir" =~ ^/[^/]+/.+ ]] || { echo "Refusing unsafe plugin directory: $plugin_dir"; exit 1; }
backup_dir="$plugin_dir.bak"

# API compatibility: plugins were built against $api_version, core on this VM must have the same major.minor
mapfile -t core_api_jars < <(unzip -Z1 "$core_jar" 'BOOT-INF/lib/unit-billing-plugin-api-*.jar' 2>/dev/null || true)
if ((${#core_api_jars[@]} != 1)); then
    echo "Cannot determine plugin-api version inside $core_jar"
    exit 1
fi

core_api_version="$(basename "${core_api_jars[0]}" .jar)"
core_api_version="${core_api_version#unit-billing-plugin-api-}"

if [[ "$(cut -d. -f1,2 <<< "$api_version")" != "$(cut -d. -f1,2 <<< "$core_api_version")" ]]; then
    echo "API mismatch: plugins built against $api_version, core runs $core_api_version"
    exit 1
fi
echo "API compatible: plugins=$api_version core=$core_api_version"

tar -xzf "$archive" -C "$staging_dir"

shopt -s nullglob
plugin_jars=("$staging_dir"/*.jar)
shopt -u nullglob

if ((${#plugin_jars[@]} == 0)); then
    echo "No plugin JAR files found in deployment archive."
    exit 1
fi

echo "Deploying ${#plugin_jars[@]} plugin JAR(s) to $plugin_dir"

# Backup of the current plugins (stays until the next deploy)
mkdir -p "$plugin_dir"
rm -rf "$backup_dir"
cp -a "$plugin_dir" "$backup_dir"

# From here any failure restores the backup (see on_exit)
started=1
systemctl stop "$service_name"

# Remove old JARs so old and new versions of a plugin are never loaded together
find "$plugin_dir" -maxdepth 1 -type f -name '*.jar' -delete

install -o unitbilling -g unitbilling -m 0644 "${plugin_jars[@]}" "$plugin_dir/"

systemctl start "$service_name"

# Wait up to 60s until the app answers
healthy=0
for i in $(seq 1 30); do
    if systemctl is-active --quiet "$service_name" && curl -fs -o /dev/null --max-time 3 "$health_url"; then
        healthy=1
        break
    fi
    sleep 2
done

if [[ "$healthy" -ne 1 ]]; then
    echo "Service did not become healthy in 60s"
    exit 1
fi

systemctl status "$service_name" --no-pager
echo "Deploy OK"
EOF

                scp -o UserKnownHostsFile="$KNOWN_HOSTS" -o StrictHostKeyChecking=yes \
                    "$archive" "$DEPLOY_HOST:$remote_archive"

                scp -o UserKnownHostsFile="$KNOWN_HOSTS" -o StrictHostKeyChecking=yes \
                    "$deploy_script" "$DEPLOY_HOST:$remote_script"

                ssh -o UserKnownHostsFile="$KNOWN_HOSTS" -o StrictHostKeyChecking=yes "$DEPLOY_HOST" \
                    "sudo bash '$remote_script' '$remote_archive' '$env_file' '$SERVICE_NAME' '$api_version'"
            '''
        }
    }
}