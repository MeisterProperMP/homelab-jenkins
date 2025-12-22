# Jenkins Master Setup

Guide for setting up the Jenkins Master from scratch.

---

## 1. Prerequisites on Host VM

```bash
# Docker and Docker Compose must be installed
docker --version
docker compose version
```

---

## 2. Create Directories

```bash
# Workspace directory (must be mounted identically in container)
sudo mkdir -p /opt/homelab/workspace
sudo chown -R 1000:1000 /opt/homelab/workspace
```

---

## 3. Check Docker GID

```bash
getent group docker
# Example output: docker:x:988:
```

If GID is **not 988**, update `docker-compose.yaml`:

```yaml
group_add:
  - "YOUR_GID"
```

---

## 4. Start Jenkins

```bash
cd jenkins
docker compose up -d --build

# Follow logs
docker compose logs -f
```

---

## 5. Initial Admin Password

```bash
docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```

---

## 6. Configure Jenkins Web UI

1. Open browser: `http://VM-IP:8080`
2. Enter initial admin password
3. Select **Install suggested plugins**
4. Create admin user

---

## 7. Add Credentials

### A) SSH Key for Agent Setup

This key is used to install agents on remote VMs.

1. **Manage Jenkins → Credentials → (global) → Add Credentials**
2. Configuration:

| Field | Value |
|-------|-------|
| Kind | `SSH Username with private key` |
| ID | `homelab-ssh-key` |
| Username | `Robin` |
| Private Key | ☑️ Enter directly → Paste SSH private key |
| Passphrase | Leave empty (key must have no passphrase!) |

> ⚠️ Key must be **without passphrase**. Remove with: `ssh-keygen -p -f ~/.ssh/key`

### B) GitHub Credentials (for private repos)

**Option 1: Personal Access Token (recommended)**

1. GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic)
2. Generate new token:
   - Name: `jenkins-homelab`
   - Scopes: ☑️ `repo`
3. Copy token

In Jenkins:
1. **Manage Jenkins → Credentials → (global) → Add Credentials**
2. Configuration:

| Field | Value |
|-------|-------|
| Kind | `Username with password` |
| ID | `github-credentials` |
| Username | Your GitHub username |
| Password | The token (`ghp_xxx...`) |

**Option 2: SSH Deploy Key**

```bash
# Inside Jenkins container
docker exec -it jenkins bash
ssh-keygen -t ed25519 -C "jenkins@homelab" -f /var/jenkins_home/.ssh/github_key
cat /var/jenkins_home/.ssh/github_key.pub
exit
```

Add public key to GitHub: Repo → Settings → Deploy keys → Add

Add private key to Jenkins: Credentials → Add → SSH Username with private key

---

## 8. Create Pipeline Jobs

### Deployment Pipeline

1. **New Item** → Name: `Homelab-Deployment` → **Pipeline**
2. Configuration:
   - ☑️ GitHub project: `https://github.com/USER/homelab-deployments`
   - Build Triggers: ☑️ Poll SCM: `H/5 * * * *`
   - Pipeline:
     - Definition: **Pipeline script from SCM**
     - SCM: **Git**
     - Repository URL: `https://github.com/USER/homelab-deployments.git`
     - Credentials: `github-credentials`
     - Branch: `*/main`
     - Script Path: `Jenkinsfile`
3. **Save**

### Agent Setup Pipeline

1. **New Item** → Name: `Agent-Setup` → **Pipeline**
2. Configuration:
   - Pipeline:
     - Definition: **Pipeline script from SCM**
     - SCM: **Git**
     - Repository URL: `https://github.com/USER/homelab-jenkins.git`
     - Branch: `*/main`
     - Script Path: `Jenkinsfile.agent-setup`
3. **Save**

---

## 9. Script Approvals

The Agent Setup pipeline requires approval for Jenkins API calls.

1. Run Agent-Setup job once (will fail)
2. **Manage Jenkins → In-process Script Approval**
3. Approve all displayed signatures
4. Repeat until all are approved (2-3 runs)

Expected signatures:
```
staticMethod jenkins.model.Jenkins getInstance
new hudson.slaves.JNLPLauncher boolean
new hudson.slaves.DumbSlave java.lang.String java.lang.String hudson.slaves.ComputerLauncher
method jenkins.model.Jenkins addNode hudson.model.Node
method jenkins.model.Jenkins getNode java.lang.String
method hudson.model.Node toComputer
method hudson.slaves.SlaveComputer getJnlpMac
```

---

## 10. Set Up Webhook (Optional)

For automatic deployment on git push:

1. **GitHub → Repo → Settings → Webhooks → Add webhook**
   - Payload URL: `http://JENKINS-IP:8080/github-webhook/`
   - Content type: `application/json`
   - Events: Just the push event

2. **Jenkins → Pipeline → Build Triggers**
   - ☑️ GitHub hook trigger for GITScm polling

---

## Troubleshooting

### Container Won't Start

```bash
docker compose logs jenkins
```

### Permission Denied

```bash
# Check permissions
ls -la /opt/homelab/
ls -la /var/run/docker.sock

# Temporarily fix Docker socket
sudo chmod 666 /var/run/docker.sock
```

### Docker Commands Don't Work in Pipeline

```bash
# Test inside Jenkins container
docker exec -it jenkins bash
docker ps
docker compose version
```

### Workspace Issues

Workspace path must be identical (inside = outside):

```yaml
volumes:
  - /opt/homelab/workspace:/opt/homelab/workspace
```
