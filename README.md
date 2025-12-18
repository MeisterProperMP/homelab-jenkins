# Homelab Jenkins Infrastructure

Jenkins CI/CD infrastructure for deploying Docker Compose services to multiple VMs.

## Overview

This repository contains:
- **Jenkins Master** - Custom Docker image with Docker CLI & Compose
- **Jenkins Agent** - Template for remote VM agents
- **Jenkinsfile** - Deployment pipeline

The pipeline works with a separate **deployments repository** that contains VM-specific service definitions.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        Deployments Repository                           │
│                                                                         │
│   192.168.2.31/          192.168.2.32/          192.168.2.33/          │
│   ├── traefik/           ├── nginx/             ├── grafana/           │
│   └── portainer/         └── postgres/          └── prometheus/        │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ git push
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                          Jenkins Master                                 │
│                         (192.168.2.31)                                  │
│                                                                         │
│   Pipeline:                                                             │
│   1. Checkout deployments repo                                          │
│   2. Discover IP directories                                            │
│   3. For each IP → select agent (label=IP) → deploy services            │
└─────────────────────────────────────────────────────────────────────────┘
          │                         │                         │
          │ Agent                   │ Agent                   │ Agent
          ▼                         ▼                         ▼
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  192.168.2.31   │     │  192.168.2.32   │     │  192.168.2.33   │
│                 │     │                 │     │                 │
│  traefik ✅     │     │  nginx ✅       │     │  grafana ✅     │
│  portainer ✅   │     │  postgres ✅    │     │  prometheus ✅  │
└─────────────────┘     └─────────────────┘     └─────────────────┘
```

---

## Quick Start

### 1. Set Up Jenkins Master

```bash
# On the Jenkins host VM (e.g., 192.168.2.31)
cd jenkins

# Check Docker group GID
getent group docker
# If not 988, update group_add in docker-compose.yaml

# Create workspace directory
sudo mkdir -p /opt/homelab/workspace
sudo chown -R 1000:1000 /opt/homelab/workspace

# Start Jenkins
docker compose up -d --build

# Get initial admin password
docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```

### 2. Configure Jenkins

1. Open `http://YOUR-VM-IP:8080`
2. Enter the initial admin password
3. Install suggested plugins
4. Create admin user

See [`jenkins/SETUP.md`](jenkins/SETUP.md) for detailed setup including GitHub credentials.

### 3. Create Pipeline Job

1. **New Item** → Name: `Homelab-Deployment` → **Pipeline**
2. Configure:
   - ☑️ GitHub project: `https://github.com/YOUR-USER/homelab-deployments`
   - Build Triggers: ☑️ Poll SCM: `H/5 * * * *` (or use webhook)
   - Pipeline:
     - Definition: **Pipeline script from SCM**
     - SCM: **Git**
     - Repository URL: `https://github.com/YOUR-USER/homelab-deployments.git`
     - Credentials: Select your GitHub credentials
     - Branch: `*/main`
     - Script Path: `Jenkinsfile`

> ⚠️ **Important:** The `Jenkinsfile` must be in the **deployments repository**, not this one!
> Copy the `Jenkinsfile` from this repo to your deployments repo.

---

## Adding Remote VMs

### Option A: Automated Setup (Recommended) 🚀

Use the **Agent Setup Pipeline** to automatically install agents on remote VMs:

#### 1. Create SSH Credentials in Jenkins

1. **Manage Jenkins → Credentials → (global) → Add Credentials**
2. Configure:
   - Kind: `SSH Username with private key`
   - ID: `homelab-ssh-key` (or any name)
   - Username: `root` (or your SSH user)
   - Private Key: **Enter directly** → Paste your private key
3. **Create**

#### 2. Create the Agent Setup Job

1. **New Item** → Name: `Agent-Setup` → **Pipeline**
2. Pipeline:
   - Definition: **Pipeline script from SCM**
   - SCM: **Git**
   - Repository URL: `https://github.com/YOUR-USER/homelab-jenkins.git`
   - Branch: `*/main`
   - Script Path: `Jenkinsfile.agent-setup`
3. **Save**

#### 3. Run the Job

1. Click **Build with Parameters**
2. Fill in:
   - `TARGET_VM_IP`: IP of the target VM (e.g., `192.168.2.32`)
   - `SSH_CREDENTIAL_ID`: Select your SSH credential
   - `DOCKER_GID`: Docker group ID on target (check with `getent group docker`)
3. **Build**

The pipeline will:
- ✅ Connect to the VM via SSH
- ✅ Register the agent in Jenkins
- ✅ Deploy and start the agent container
- ✅ Verify the connection

---

### Option B: Manual Setup

#### Step 1: Register Agent in Jenkins

1. **Jenkins → Manage Jenkins → Nodes → New Node**
2. Node name: `192.168.2.32` (must match directory name in deployments repo!)
3. Type: **Permanent Agent**
4. Configure:

| Field | Value |
|-------|-------|
| Remote root directory | `/home/jenkins/agent` |
| **Labels** | `192.168.2.32` ⬅️ Must match IP! |
| Usage | Only build jobs with label expressions matching this node |
| Launch method | Launch agent by connecting it to the controller |

5. Save → Copy the **Secret**

#### Step 2: Deploy Agent on Remote VM

```bash
# Copy agent files to remote VM
scp -r jenkins-agent/ user@192.168.2.32:/opt/

# SSH to remote VM
ssh user@192.168.2.32
cd /opt/jenkins-agent

# Edit docker-compose.yaml:
# - JENKINS_URL=http://192.168.2.31:8080
# - JENKINS_AGENT_NAME=192.168.2.32
# - JENKINS_SECRET=<secret from step 1>

# Check Docker group GID and update if needed
getent group docker

# Start agent
docker compose up -d --build
```

#### Step 3: Verify Connection

In Jenkins, the agent should show as **online** (green).

---

## Pipeline Parameters

| Parameter | Description | Default |
|-----------|-------------|---------|
| `FORCE_RECREATE` | Recreate containers even without changes | `false` |
| `PRUNE` | Remove services that no longer exist in the repository | `false` |
| `DEPLOY_ONLY_VM` | Deploy only to specific VM (e.g. `192.168.2.32`) | empty (all) |
| `DEPLOY_ONLY_SERVICE` | Deploy only specific services (comma-separated) | empty (all) |

### Examples

**Deploy only to one VM:**
```
DEPLOY_ONLY_VM=192.168.2.32
```

**Deploy only traefik:**
```
DEPLOY_ONLY_SERVICE=traefik
```

**Force recreate everything:**
```
FORCE_RECREATE=true
```

**Remove deleted services:**
```
PRUNE=true
```

---

## Deployments Repository Structure

The pipeline expects the deployments repository to follow this structure:

```
homelab-deployments/
├── 192.168.2.31/              # VM IP address as directory name
│   ├── traefik/               # Service to deploy
│   │   ├── docker-compose.yaml
│   │   └── configs/
│   └── portainer/
│       └── docker-compose.yaml
├── 192.168.2.32/              # Another VM
│   ├── nginx/
│   │   └── docker-compose.yaml
│   └── postgres/
│       └── docker-compose.yaml
└── Jenkinsfile                # ⬅️ Copy from this repo!
```

**Key Concept:** The IP address in the directory name determines which VM the services inside will be deployed to.

---

## Files in This Repository

```
homelab-jenkins/
├── jenkins/                    # Jenkins Master
│   ├── docker-compose.yaml     # Container configuration
│   ├── Dockerfile              # Custom image with Docker CLI
│   ├── SETUP.md                # Detailed setup guide
│   └── configs/
│       └── init.groovy.d/      # Initialization scripts
├── jenkins-agent/              # Agent template for remote VMs
│   ├── docker-compose.yaml
│   ├── Dockerfile
│   └── README.md
├── Jenkinsfile                 # Deployment Pipeline (copy to deployments repo!)
├── Jenkinsfile.agent-setup     # Agent Installation Pipeline (run from this repo)
└── README.md
```

---

## Webhook Setup (Automatic Deployment on Push)

1. **GitHub → Repository Settings → Webhooks → Add webhook**
   - Payload URL: `http://192.168.2.31:8080/github-webhook/`
   - Content type: `application/json`
   - Events: "Just the push event"

2. **Jenkins → Pipeline → Build Triggers**
   - ☑️ "GitHub hook trigger for GITScm polling"

---

## Troubleshooting

### Agent Not Connecting

```bash
# On remote VM - check agent logs
docker compose logs -f

# Common issues:
# - Wrong JENKINS_URL (must be reachable from remote VM)
# - Wrong JENKINS_SECRET
# - Firewall blocking port 50000
```

### Permission Denied on Docker

```bash
# Check Docker socket GID
getent group docker
# Output: docker:x:988:

# Adjust group_add in docker-compose.yaml if different
```

### Volume Mounts Not Working (Jenkins Master)

The workspace path must be identical inside and outside the container:

```yaml
# jenkins/docker-compose.yaml
volumes:
  - /opt/homelab/workspace:/opt/homelab/workspace
```

---

## Tips

### Environment Variables
- Create `.env.example` with placeholder values in your service directories
- Pipeline automatically copies to `.env` if not exists
- Use Jenkins Credentials for sensitive values

### Service Order
Services within a VM are deployed alphabetically. For dependencies:
- Name with prefix: `01-network`, `02-traefik`, `03-apps`
- Or use `depends_on` in docker-compose

### Health Checks
Define in your compose files:

```yaml
services:
  app:
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost/health"]
      interval: 30s
      timeout: 10s
      retries: 3
```

