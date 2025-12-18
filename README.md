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

### Automated Agent Setup (Recommended) 🚀

Use the **Agent Setup Pipeline** to automatically install Jenkins agents on remote VMs.

---

### One-Time Setup (only needed once)

#### 1. Create SSH Key (without passphrase)

On your local machine, create an SSH key for Jenkins:

```bash
ssh-keygen -t ed25519 -f ~/.ssh/jenkins_agent_key -N ""
```

#### 2. Add SSH Credentials to Jenkins

1. **Manage Jenkins → Credentials → (global) → Add Credentials**
2. Configure:

| Field | Value |
|-------|-------|
| Kind | `SSH Username with private key` |
| Scope | `Global` |
| ID | `homelab-ssh-key` |
| Username | `Robin` (your SSH user) |
| Private Key | ☑️ Enter directly → Paste content of `~/.ssh/jenkins_agent_key` |
| Passphrase | Leave empty |

3. Click **Create**

#### 3. Create the Agent Setup Job

1. **New Item** → Name: `Agent-Setup` → **Pipeline**
2. Pipeline:
   - Definition: **Pipeline script from SCM**
   - SCM: **Git**
   - Repository URL: `https://github.com/YOUR-USER/homelab-jenkins.git`
   - Branch: `*/main` or `*/master`
   - Script Path: `Jenkinsfile.agent-setup`
3. **Save**

#### 4. Approve Script Signatures

The first run will fail because Jenkins needs to approve certain API calls.

1. Run the job once (it will fail)
2. Go to **Manage Jenkins → In-process Script Approval**
3. Click **Approve** for all pending signatures
4. Repeat until all are approved (may take 2-3 runs)

Expected signatures to approve:
```
staticMethod jenkins.model.Jenkins getInstance
new hudson.slaves.JNLPLauncher boolean
new hudson.slaves.DumbSlave java.lang.String java.lang.String hudson.slaves.ComputerLauncher
method jenkins.model.Jenkins addNode hudson.model.Node
method jenkins.model.Jenkins getNode java.lang.String
method hudson.model.Node toComputer
method hudson.slaves.SlaveComputer getJnlpMac
... and more
```

---

### Adding a New VM

#### Step 1: Prepare the Target VM

SSH into the new VM and run these commands:

```bash
# 1. Add your public key to authorized_keys
mkdir -p ~/.ssh
echo "YOUR_PUBLIC_KEY_HERE" >> ~/.ssh/authorized_keys
chmod 700 ~/.ssh
chmod 600 ~/.ssh/authorized_keys

# 2. Add your user to the docker group
sudo usermod -aG docker $USER

# 3. Create the agent installation directory
sudo mkdir -p /opt/jenkins-agent
sudo chown $USER:$USER /opt/jenkins-agent

# 4. Check Docker group GID (note this for the Jenkins job)
getent group docker
# Example output: docker:x:988:  → GID is 988

# 5. Log out and back in (for docker group to take effect)
exit
```

**Quick one-liner** (replace `YOUR_PUBLIC_KEY`):

```bash
ssh user@NEW_VM_IP 'mkdir -p ~/.ssh && chmod 700 ~/.ssh && echo "YOUR_PUBLIC_KEY" >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys && sudo usermod -aG docker $USER && sudo mkdir -p /opt/jenkins-agent && sudo chown $USER:$USER /opt/jenkins-agent'
```

#### Step 2: Verify SSH Access

From your local machine, test the connection:

```bash
ssh -i ~/.ssh/jenkins_agent_key Robin@NEW_VM_IP 'echo OK && docker ps'
```

Both commands should succeed without password prompts.

#### Step 3: Run the Agent Setup Job

1. In Jenkins, go to **Agent-Setup** job
2. Click **Build with Parameters**
3. Fill in:

| Parameter | Value | Notes |
|-----------|-------|-------|
| `TARGET_VM_IP` | `192.168.2.XX` | Complete the IP address |
| `SSH_CREDENTIAL_ID` | `homelab-ssh-key` | Select from dropdown |
| `SSH_USER` | `Robin` | Default is `Robin` |
| `JENKINS_MASTER_URL` | `http://192.168.2.31:8080` | Your Jenkins URL |
| `DOCKER_GID` | `988` | From `getent group docker` |
| `AGENT_INSTALL_PATH` | `/opt/jenkins-agent` | Default path |
| `FORCE_REINSTALL` | ☐ `false` | Check if reinstalling |

4. Click **Build**

#### Step 4: Verify Agent is Online

1. Go to **Manage Jenkins → Nodes**
2. The new agent (named by IP) should show as **online** (green icon)

If offline, check the logs:
```bash
ssh Robin@NEW_VM_IP 'cd /opt/jenkins-agent && docker compose logs -f'
```

---

### What the Pipeline Does

The `Jenkinsfile.agent-setup` pipeline:

1. ✅ Validates parameters and SSH connectivity
2. ✅ Checks Docker installation on target VM
3. ✅ Registers the agent node in Jenkins (with IP as label)
4. ✅ Retrieves the agent secret automatically
5. ✅ Deploys Dockerfile and docker-compose.yaml to the VM
6. ✅ Builds and starts the agent container
7. ✅ Waits for the agent to connect
8. ✅ Verifies the connection

---

### Troubleshooting Agent Setup

#### SSH Permission Denied

```bash
# Check if key works locally
ssh -i ~/.ssh/jenkins_agent_key Robin@VM_IP 'echo OK'

# If it fails:
# 1. Verify public key is in ~/.ssh/authorized_keys on target
# 2. Check file permissions (700 for .ssh, 600 for authorized_keys)
# 3. Ensure the private key in Jenkins matches
```

#### Docker Permission Denied

```bash
# On the target VM:
sudo usermod -aG docker Robin
# Then log out and back in, or:
newgrp docker
```

#### Agent Not Connecting

```bash
# Check agent logs on the VM:
ssh Robin@VM_IP 'cd /opt/jenkins-agent && docker compose logs --tail=100'

# Common issues:
# - JENKINS_URL not reachable from VM (check firewall)
# - Port 50000 blocked (agent communication port)
# - Wrong JENKINS_SECRET (use FORCE_REINSTALL=true)
```

#### Reinstalling an Agent

If you need to reinstall, run the job with:
- `FORCE_REINSTALL` = ☑️ `true`

This will:
- Remove and recreate the Jenkins node
- Generate a new secret
- Redeploy the agent container

---

### Manual Setup (Alternative)

If you prefer manual setup, see the `jenkins-agent/README.md` for step-by-step instructions.

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

