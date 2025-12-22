# Adding New Jenkins Agents

Guide for adding new VMs as Jenkins agents.

---

## Prerequisites on Target VM

- ✅ SSH access works
- ✅ Docker and Docker Compose installed
- ✅ User is in `docker` group
- ✅ Passwordless sudo configured
- ✅ SSH public key deployed

### Verify

```bash
ssh Robin@NEW-VM-IP 'docker ps && sudo whoami'
# Should show container list and "root" without password prompt
```

### If Not Configured

```bash
# On target VM:

# 1. Add user to docker group
sudo usermod -aG docker $USER

# 2. Configure passwordless sudo
echo "$USER ALL=(ALL) NOPASSWD: ALL" | sudo tee /etc/sudoers.d/$USER

# 3. Log out and back in
exit
```

---

## Install Agent

### 1. Get Docker GID

```bash
ssh Robin@NEW-VM-IP 'getent group docker'
# Example: docker:x:988:  → GID is 988
```

### 2. Run Agent Setup Pipeline

In Jenkins: **Agent-Setup** → **Build with Parameters**

| Parameter | Value |
|-----------|-------|
| `TARGET_VM_IP` | IP of new VM (e.g. `192.168.2.34`) |
| `SSH_CREDENTIAL_ID` | `homelab-ssh-key` |
| `SSH_USER` | `Robin` |
| `JENKINS_MASTER_URL` | `http://192.168.2.31:8080` |
| `DOCKER_GID` | GID from above (e.g. `988`) |
| `FORCE_REINSTALL` | `false` (or `true` for reinstall) |

→ **Build**

### 3. Verify

**Manage Jenkins → Nodes** → Agent should be green/online

---

## What the Pipeline Does

| Stage | Description |
|-------|-------------|
| Validate | Check parameters |
| Check VM | Test SSH + Docker, check agent status |
| Start Existing | If agent exists but stopped → restart |
| Register Node | Create Jenkins node (new install only) |
| Deploy & Start | Copy files, start container |
| Verify | Check if agent is online |

---

## Scenarios

| State | Action |
|-------|--------|
| Agent running | ✅ Nothing to do |
| Agent stopped | 🚀 Automatically restarted |
| New agent | 📦 Full installation |
| `FORCE_REINSTALL=true` | 🔄 Complete reinstall |

---

## Files Installed on VM

```
/opt/jenkins-agent/
├── docker-compose.yaml
├── Dockerfile
└── .env                    # Auto-generated with secrets

/home/jenkins-agent/        # Persistent data (deployments)
```

---

## Manual Commands on VM

```bash
cd /opt/jenkins-agent

# Start agent
docker compose up -d --build

# View logs
docker compose logs -f

# Restart agent
docker compose restart

# Stop agent
docker compose down
```

---

## Troubleshooting

### Agent Not Connecting

```bash
# Check logs on VM
ssh Robin@VM-IP 'cd /opt/jenkins-agent && docker compose logs --tail=50'

# Common causes:
# - Jenkins URL not reachable (firewall?)
# - Port 50000 blocked
# - Wrong secret → FORCE_REINSTALL=true
```

### SSH Permission Denied

```bash
# Test key
ssh -i ~/.ssh/key Robin@VM-IP 'echo OK'

# Check:
# - Public key in ~/.ssh/authorized_keys on target VM?
# - Permissions: .ssh = 700, authorized_keys = 600
# - Private key in Jenkins correct?
```

### Docker Permission Denied

```bash
# On target VM:
sudo usermod -aG docker Robin
# Then log out and back in
```

### Test Network

```bash
# From target VM:
curl http://192.168.2.31:8080        # Jenkins Web UI
nc -zv 192.168.2.31 50000            # Agent port
```

---

## Reinstall Agent

Run pipeline with: `FORCE_REINSTALL` = ☑️ `true`

This will:
- Delete and recreate Jenkins node
- Generate new secret
- Redeploy container

---

## Start Stopped Agent

Just run the pipeline again - it detects stopped agents and restarts them automatically.
