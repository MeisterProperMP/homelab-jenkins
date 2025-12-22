pipeline {
    agent none  // No global agent - we select per VM

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timestamps()
        disableConcurrentBuilds()
    }

    environment {
        COMPOSE_PROJECT_PREFIX = 'homelab'
    }

    parameters {
        booleanParam(
            name: 'FORCE_RECREATE',
            defaultValue: false,
            description: 'Recreate containers even if there are no changes'
        )
        booleanParam(
            name: 'PRUNE',
            defaultValue: false,
            description: 'Remove services that no longer exist in the repository'
        )
        string(
            name: 'DEPLOY_ONLY_VM',
            defaultValue: '',
            description: 'Deploy only to specific VM (IP address, e.g. "192.168.2.32"). Empty = all VMs'
        )
        string(
            name: 'DEPLOY_ONLY_SERVICE',
            defaultValue: '',
            description: 'Deploy only specific services (comma-separated, e.g. "traefik,portainer"). Empty = all'
        )
    }

    stages {
        stage('Discover VMs') {
            agent any
            steps {
                checkout scm
                script {
                    env.GIT_COMMIT_SHORT = sh(
                        script: 'git rev-parse --short HEAD',
                        returnStdout: true
                    ).trim()
                    echo "Building commit: ${env.GIT_COMMIT_SHORT}"

                    // Find all IP directories (pattern: X.X.X.X)
                    def vmDirsRaw = sh(
                        script: '''
                            find . -maxdepth 1 -type d -regex '.*/[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+' | \
                            sed 's|^./||' | \
                            sort
                        ''',
                        returnStdout: true
                    ).trim()

                    if (vmDirsRaw.isEmpty()) {
                        error "No VM directories found! Expected directories named like IP addresses (e.g. 192.168.2.31/)"
                    }

                    // Store for next stages (simple string, no serialization issues)
                    env.VM_DIRS = vmDirsRaw

                    echo "\n=========================================="
                    echo "DISCOVERED VMs"
                    echo "==========================================\n"
                    
                    vmDirsRaw.split('\n').each { vmIp ->
                        echo "📍 ${vmIp}"
                    }
                }
            }
        }

        stage('Deploy to VMs') {
            steps {
                script {
                    def vmDirs = env.VM_DIRS.split('\n')
                    def deployOnlyVm = params.DEPLOY_ONLY_VM?.trim()
                    def deployOnlyService = params.DEPLOY_ONLY_SERVICE?.trim()
                    def forceRecreate = params.FORCE_RECREATE
                    def prune = params.PRUNE

                    // Filter VMs if specified
                    if (deployOnlyVm) {
                        vmDirs = vmDirs.findAll { it == deployOnlyVm }
                        if (vmDirs.size() == 0) {
                            error "VM ${deployOnlyVm} not found!"
                        }
                    }

                    // Track results
                    def allResults = [:]
                    def removedServices = []

                    // Deploy to each VM using its agent
                    vmDirs.each { vmIp ->
                        stage("Deploy to ${vmIp}") {
                            node(vmIp) {
                                checkout scm

                                // Find services for this VM in the repo
                                def servicesRaw = sh(
                                    script: """
                                        find ./${vmIp} -maxdepth 2 \\( -name "docker-compose.yaml" -o -name "docker-compose.yml" \\) | \
                                        sed 's|^./||' | \
                                        sed 's|/docker-compose.ya\\?ml\$||' | \
                                        sed 's|^${vmIp}/||' | \
                                        sort
                                    """,
                                    returnStdout: true
                                ).trim()

                                def repoServices = servicesRaw ? servicesRaw.split('\n').toList() : []

                                echo "\n=========================================="
                                echo "VM: ${vmIp}"
                                echo "==========================================\n"
                                echo "📦 Services in repo: ${repoServices.join(', ') ?: 'none'}"

                                // Prune: Find and remove services no longer in repo
                                if (prune && !deployOnlyService) {
                                    // Find running homelab services on this VM
                                    def runningRaw = sh(
                                        script: """
                                            docker compose ls --format json 2>/dev/null | \
                                            jq -r '.[].Name' 2>/dev/null | \
                                            grep '^${env.COMPOSE_PROJECT_PREFIX}-' | \
                                            sed 's|^${env.COMPOSE_PROJECT_PREFIX}-||' | \
                                            sort || true
                                        """,
                                        returnStdout: true
                                    ).trim()

                                    def runningServices = runningRaw ? runningRaw.split('\n').toList() : []
                                    echo "🐳 Running services: ${runningServices.join(', ') ?: 'none'}"

                                    // Find services to remove (running but not in repo)
                                    def toRemove = runningServices.findAll { running ->
                                        !repoServices.contains(running)
                                    }

                                    if (toRemove.size() > 0) {
                                        echo "\n🗑️ Pruning removed services: ${toRemove.join(', ')}"
                                        
                                        toRemove.each { serviceName ->
                                            def projectName = "${env.COMPOSE_PROJECT_PREFIX}-${serviceName}"
                                            
                                            echo "\n--- Removing: ${serviceName} ---"
                                            
                                            try {
                                                sh """
                                                    docker compose -p ${projectName} down --remove-orphans --volumes || true
                                                """
                                                echo "✅ ${serviceName} removed"
                                                removedServices.add("${vmIp}/${serviceName}")
                                            } catch (Exception e) {
                                                echo "⚠️ Failed to remove ${serviceName}: ${e.message}"
                                            }
                                        }
                                    }
                                }

                                // Filter services if specified
                                def servicesToDeploy = repoServices
                                if (deployOnlyService) {
                                    def allowedServices = deployOnlyService.split(',').collect { it.trim() }
                                    servicesToDeploy = repoServices.findAll { service ->
                                        allowedServices.any { allowed -> service.contains(allowed) }
                                    }
                                    if (servicesToDeploy.isEmpty()) {
                                        echo "⚠️ No matching services found for filter: ${deployOnlyService}"
                                        return
                                    }
                                }

                                if (servicesToDeploy.isEmpty()) {
                                    echo "⚠️ No services to deploy"
                                    return
                                }

                                echo "\n🚀 Deploying: ${servicesToDeploy.join(', ')}"

                                // Deploy each service
                                servicesToDeploy.each { serviceName ->
                                    def servicePath = "${vmIp}/${serviceName}"
                                    def composeFile = fileExists("${servicePath}/docker-compose.yaml") ?
                                        "${servicePath}/docker-compose.yaml" : "${servicePath}/docker-compose.yml"
                                    def projectName = "${env.COMPOSE_PROJECT_PREFIX}-${serviceName}"

                                    echo "\n--- Deploying: ${serviceName} ---"

                                    try {
                                        // Copy .env file if present
                                        if (fileExists("${servicePath}/.env.example") && !fileExists("${servicePath}/.env")) {
                                            sh "cp ${servicePath}/.env.example ${servicePath}/.env"
                                            echo "📋 Copied .env.example to .env"
                                        }

                                        // Validate compose file
                                        def validateResult = sh(
                                            script: "docker compose -f ${composeFile} config --quiet",
                                            returnStatus: true
                                        )
                                        
                                        if (validateResult != 0) {
                                            echo "❌ ${serviceName}: Invalid compose file"
                                            allResults[servicePath] = 'FAILED'
                                            return
                                        }

                                        // Docker Compose commands
                                        def composeCmd = "docker compose -f ${composeFile} -p ${projectName}"
                                        def upFlags = "-d --pull always --remove-orphans"
                                        
                                        if (forceRecreate) {
                                            upFlags += " --force-recreate"
                                        }

                                        // Pull images
                                        sh "${composeCmd} pull --ignore-pull-failures || true"

                                        // Start service
                                        sh "${composeCmd} up ${upFlags}"

                                        // Show status
                                        sh "${composeCmd} ps"

                                        echo "✅ ${serviceName} deployed successfully"
                                        allResults[servicePath] = 'SUCCESS'

                                    } catch (Exception e) {
                                        echo "❌ ${serviceName} deployment failed: ${e.message}"
                                        allResults[servicePath] = 'FAILED'
                                        currentBuild.result = 'UNSTABLE'
                                    }
                                }

                                // Cleanup unused Docker resources on this VM
                                echo "\nCleaning up unused Docker resources..."
                                sh '''
                                    docker image prune -f --filter "until=168h" || true
                                    docker volume prune -f --filter "label!=keep" || true
                                    docker network prune -f || true
                                '''
                            }
                        }
                    }

                    // Final Summary
                    echo "\n=========================================="
                    echo "DEPLOYMENT SUMMARY"
                    echo "==========================================\n"
                    
                    def successful = allResults.findAll { it.value == 'SUCCESS' }
                    def failed = allResults.findAll { it.value == 'FAILED' }

                    echo "✅ Deployed (${successful.size()}):"
                    successful.each { key, value -> echo "   - ${key}" }

                    if (removedServices.size() > 0) {
                        echo "\n🗑️ Pruned (${removedServices.size()}):"
                        removedServices.each { echo "   - ${it}" }
                    }
                    
                    if (failed.size() > 0) {
                        echo "\n❌ Failed (${failed.size()}):"
                        failed.each { key, value -> echo "   - ${key}" }
                        unstable("Some services could not be deployed")
                    }
                }
            }
        }
    }

    post {
        success {
            echo "🎉 Homelab deployment completed successfully!"
        }
        unstable {
            echo "⚠️ Homelab deployment completed with warnings"
        }
        failure {
            echo "💥 Homelab deployment failed!"
        }
    }
}
