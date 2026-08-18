// Phase 13 (requirement.md section 26). Declarative pipeline mirroring the same
// `mvn clean test -Denv=qa -Dgroups=... -Dbrowser=chrome -Dheadless=true` shape used
// locally and in the GitHub Actions workflow - see CI_CD.md for the full picture across
// all three CI systems.
//
// GROUPS defaults to blank (every group) rather than requirement.md's illustrative
// "regression" - verified live that this codebase's tests were never actually tagged with
// a "regression" group, and -Dgroups=regression silently runs zero tests and still reports
// success. See CI_CD.md's "found in practice" note.
//
// Requires on the Jenkins agent: JDK 17, Maven, and (for Web tests) Chrome/Firefox
// installed - the standard `maven:3.9-eclipse-temurin-17` Docker image does NOT include a
// browser; either use a custom image that does, or run this on a static/VM agent with one
// installed. Mobile is never run here (no emulator/Appium on a CI agent - see
// [[local-mobile-test-env]]-equivalent note in CI_CD.md).
//
// Two Jenkins credentials (Secret text) must exist, matching the IDs below:
//   eventhub-email, eventhub-password
// These become EVENTHUB_EMAIL/EVENTHUB_PASSWORD environment variables, resolved by
// SecretManager as CI/CD environment variables (highest precedence over .secret.env,
// which is never present on a CI agent at all).

pipeline {
    agent any

    parameters {
        choice(name: 'ENV', choices: ['qa', 'dev', 'uat', 'staging'], description: 'Environment (-Denv=...)')
        string(name: 'GROUPS', defaultValue: '', description: 'TestNG groups to run (-Dgroups=...); blank runs every group')
        choice(name: 'BROWSER', choices: ['chrome', 'firefox', 'edge'], description: 'Browser (-Dbrowser=...)')
        booleanParam(name: 'HEADLESS', defaultValue: true, description: 'Headless mode (-Dheadless=...)')
    }

    environment {
        EVENTHUB_EMAIL    = credentials('eventhub-email')
        EVENTHUB_PASSWORD = credentials('eventhub-password')
    }

    options {
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '30'))
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Test') {
            steps {
                sh """
                    mvn -B clean test \
                        -Denv=${params.ENV} \
                        -Dgroups=${params.GROUPS} \
                        -Dbrowser=${params.BROWSER} \
                        -Dheadless=${params.HEADLESS} \
                        -DexcludedGroups=mobile,frameworkSelfTest
                """
            }
        }
    }

    post {
        always {
            // Surefire's TestNG provider writes JUnit-format XML too (target/surefire-reports/TEST-*.xml),
            // so Jenkins' built-in JUnit step works without an extra TestNG-specific plugin.
            junit testResults: 'target/surefire-reports/TEST-*.xml', allowEmptyResults: true

            archiveArtifacts artifacts: 'logs/**, reports/extent/**, allure-results/**, target/screenshots/**',
                              allowEmptyArchive: true, fingerprint: false

            // Requires the Allure Jenkins plugin (https://plugins.jenkins.io/allure-jenkins-plugin/).
            // Comment out if that plugin is not installed on this Jenkins instance.
            script {
                if (fileExists('allure-results')) {
                    allure includeProperties: false, results: [[path: 'allure-results']]
                }
            }
        }
    }
}
