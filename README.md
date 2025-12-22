# Homelab Jenkins Infrastructure

Jenkins CI/CD system for automatic deployment of Docker Compose services to multiple VMs.

## What does this setup do?

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
│   1. Checkout deployments repo                                          │
│   2. Discover IP directories (192.168.2.31/, 192.168.2.33/, ...)       │
│   3. For each IP → select agent with label=IP → deploy services        │
└─────────────────────────────────────────────────────────────────────────┘
          │                         │                         │
          ▼                         ▼                         ▼
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  Agent          │     │  Agent          │     │  Agent          │
│  192.168.2.31   │     │  192.168.2.32   │     │  192.168.2.33   │
│                 │     │                 │     │                 │
│  traefik ✅     │     │  nginx ✅       │     │  grafana ✅     │
│  portainer ✅   │     │  postgres ✅    │     │  prometheus ✅  │
└─────────────────┘     └─────────────────┘     └─────────────────┘
```

### Workflow

1. **Git Push** to deployments repository
2. **Jenkins** detects changes (webhook or polling)
3. **Pipeline** reads all IP directories (e.g. `192.168.2.33/`)
4. For each directory, the **matching agent** is selected (label = IP)
5. Agent runs `docker compose up` for each service

### Result

- New service? → Create directory + `docker-compose.yaml` + push → runs automatically
- Change service? → Edit file + push → automatically updated
- Delete service? → Delete directory + push with `PRUNE=true` → stopped and removed

---

## Repository Structure

```
homelab-jenkins/
├── jenkins/                        # Jenkins Master
│   ├── docker-compose.yaml
│   ├── Dockerfile
│   └── SETUP.md                    # ⬅️ Guide: Setting up Jenkins
├── jenkins-agent/                  # Agent for remote VMs
│   ├── docker-compose.yaml
│   ├── Dockerfile
│   └── README.md                   # ⬅️ Guide: Adding new agents
├── Jenkinsfile.deployment          # Deployment pipeline (→ copy to deployments repo)
├── Jenkinsfile.agent-setup         # Agent installation pipeline
└── README.md                       # This file
```

---

## Pipelines

### Jenkinsfile.deployment

Deploys services from the deployments repository to VMs.

| Parameter | Description | Default |
|-----------|-------------|---------|
| `FORCE_RECREATE` | Recreate containers even without changes | `false` |
| `PRUNE` | Stop and remove deleted services | `false` |
| `DEPLOY_ONLY_VM` | Deploy only to specific VM | all |
| `DEPLOY_ONLY_SERVICE` | Deploy only specific services | all |

### Jenkinsfile.agent-setup

Automatically installs Jenkins agents on remote VMs via SSH.

| Parameter | Description |
|-----------|-------------|
| `TARGET_VM_IP` | IP of target VM |
| `SSH_CREDENTIAL_ID` | SSH credentials in Jenkins |
| `DOCKER_GID` | Docker group ID on the VM |
| `FORCE_REINSTALL` | Completely reinstall agent |

---

## Deployments Repository Structure

```
homelab-deployments/
├── 192.168.2.31/              # VM IP = directory name
│   ├── traefik/
│   │   └── docker-compose.yaml
│   └── portainer/
│       └── docker-compose.yaml
├── 192.168.2.33/
│   ├── grafana/
│   │   └── docker-compose.yaml
│   └── mosquitto/
│       ├── docker-compose.yaml
│       └── work/config/
└── Jenkinsfile                # ⬅️ Copy of Jenkinsfile.deployment
```

---

## Documentation

| Topic | File |
|-------|------|
| Setting up Jenkins Master | [jenkins/SETUP.md](jenkins/SETUP.md) |
| Adding new agents/VMs | [jenkins-agent/README.md](jenkins-agent/README.md) |
