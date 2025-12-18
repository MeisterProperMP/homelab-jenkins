# Jenkins Setup Guide

## 1. Create Directory on Host

```bash
# Create workspace directory (must match Jenkinsfile customWorkspace!)
sudo mkdir -p /opt/homelab/workspace
sudo chown -R 1000:1000 /opt/homelab/workspace
```

## 2. Check Docker GID

```bash
# Find Docker group GID
getent group docker
# Output e.g.: docker:x:999:

# If the GID is not 999, adjust in docker-compose.yaml:
# group_add:
#   - "YOUR_GID"
```

## 3. Start Jenkins

```bash
cd jenkins

# Build image and start
docker compose up -d --build

# Watch logs
docker compose logs -f
```

## 4. Initial Admin Password

```bash
# On first start - read admin password
docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```

## 5. Configure Jenkins

1. Open browser: `http://YOUR-VM-IP:8080`
2. Enter Initial Admin Password
3. Select "Install suggested plugins"
4. Create admin user

## 6. Set Up GitHub Credentials (for private repos)

### Option A: Personal Access Token (recommended)

**1. Create token in GitHub:**
1. GitHub → Settings → Developer settings → Personal access tokens → **Tokens (classic)**
2. **Generate new token (classic)**
3. Name: `jenkins-homelab`
4. Expiration: As needed (e.g. 90 days or No expiration)
5. Select scopes:
   - ☑️ `repo` (Full control of private repositories)
6. **Generate token**
7. ⚠️ **Copy and save the token securely!** (shown only once)

**2. Save token in Jenkins:**
1. Jenkins → **Manage Jenkins** → **Credentials**
2. Click on **(global)** under "Stores scoped to Jenkins"
3. **Add Credentials**
4. Fill in:
   - Kind: `Username with password`
   - Scope: `Global`
   - Username: `your-github-username`
   - Password: `ghp_xxxxxxxxxxxx` (your token)
   - ID: `github-credentials`
   - Description: `GitHub PAT for Homelab Repo`
5. **Create**

### Option B: SSH Key

**1. Generate SSH key on the VM:**
```bash
# Inside Jenkins container
docker exec -it jenkins bash
ssh-keygen -t ed25519 -C "jenkins@homelab" -f /var/jenkins_home/.ssh/github_key
cat /var/jenkins_home/.ssh/github_key.pub
# Copy the public key!
exit
```

**2. Add public key in GitHub:**
1. GitHub → Repo → Settings → Deploy keys → **Add deploy key**
2. Title: `Jenkins Homelab`
3. Key: Paste the copied public key
4. ☑️ Allow write access (if needed)
5. **Add key**

**3. Save private key in Jenkins:**
1. Jenkins → **Manage Jenkins** → **Credentials**
2. **(global)** → **Add Credentials**
3. Fill in:
   - Kind: `SSH Username with private key`
   - Scope: `Global`
   - ID: `github-ssh`
   - Username: `git`
   - Private Key: **Enter directly** → Paste private key:
     ```bash
     docker exec jenkins cat /var/jenkins_home/.ssh/github_key
     ```
5. **Create**

## 7. Set Up Pipeline

1. **New Item** → Name: `Homelab-Deployment` → **Pipeline**
2. Configuration:
   - ☑️ GitHub project: `https://github.com/YOUR-USER/homelab-src`
   - Build Triggers: ☑️ Poll SCM: `H/5 * * * *`
   - Pipeline:
     - Definition: **Pipeline script from SCM**
     - SCM: **Git**
     - Repository URL: 
       - With Token: `https://github.com/YOUR-USER/homelab-src.git`
       - With SSH: `git@github.com:YOUR-USER/homelab-src.git`
     - **Credentials**: Select `github-credentials` or `github-ssh` ⬅️ IMPORTANT!
     - Branch: `*/main`
     - Script Path: `Jenkinsfile`
3. **Save**
4. **Build Now** to test

## Directory Structure After Setup

```
Host System:
/opt/homelab/
├── workspace/                    # Jenkins Workspace (Volume Mount)
│   └── homelab-deployment/       # Cloned Repo
│       ├── traefik/
│       │   ├── docker-compose.yaml
│       │   └── configs/
│       └── ...
└── jenkins-data/                 # Jenkins Home (optional)
```

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

# Docker socket permission
sudo chmod 666 /var/run/docker.sock  # Temporary
```

### Docker Commands Don't Work in Pipeline
```bash
# Test inside Jenkins container
docker exec -it jenkins bash
docker ps
docker compose version
```
