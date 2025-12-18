# Jenkins Agent Setup for Remote VMs

This directory contains the Docker configuration for running a Jenkins agent on remote VMs.

## Prerequisites

- Docker and Docker Compose installed on the remote VM
- Network connectivity to Jenkins master (port 8080 and 50000)

## Setup Steps

### 1. Register Agent in Jenkins

On the Jenkins master web UI:

1. Go to **Manage Jenkins → Nodes → New Node**
2. Enter node name: `192.168.2.32` (use the VM's actual IP)
3. Select **Permanent Agent** → OK
4. Configure:

| Field | Value |
|-------|-------|
| Remote root directory | `/home/jenkins/agent` |
| Labels | `192.168.2.32` (same as node name) |
| Usage | Only build jobs with label expressions matching this node |
| Launch method | Launch agent by connecting it to the controller |

5. Click **Save**
6. Click on the new node → **Copy the Secret**

### 2. Copy Files to Remote VM

```bash
# From your local machine
scp -r jenkins-agent/ user@192.168.2.32:/opt/
```

### 3. Configure the Agent

On the remote VM, edit `/opt/jenkins-agent/docker-compose.yaml`:

```yaml
environment:
  - JENKINS_URL=http://192.168.2.31:8080    # Your Jenkins master IP
  - JENKINS_AGENT_NAME=192.168.2.32          # This VM's IP
  - JENKINS_SECRET=abc123...                 # Secret from step 1
```

### 4. Check Docker Group ID

```bash
# On the remote VM
getent group docker
# Output: docker:x:999:

# If the GID is NOT 999, update docker-compose.yaml:
# group_add:
#   - "YOUR_GID"
```

### 5. Start the Agent

```bash
cd /opt/jenkins-agent
docker compose up -d --build

# Check logs
docker compose logs -f
```

You should see:
```
INFO: Connected
```

### 6. Verify in Jenkins

The agent should now appear **online** (green) in Jenkins → Nodes.

## Troubleshooting

### Agent Won't Connect

```bash
# Check logs
docker compose logs -f

# Common issues:
# - JENKINS_URL not reachable from this VM
# - Wrong JENKINS_SECRET
# - Firewall blocking ports 8080 or 50000
```

### Test Network Connectivity

```bash
# From remote VM
curl http://192.168.2.31:8080
# Should return HTML

# Check agent port
nc -zv 192.168.2.31 50000
# Should show "Connection succeeded"
```

### Permission Denied on Docker Commands

```bash
# Check if docker socket is accessible
docker ps

# If permission denied, verify group_add GID matches:
getent group docker
```

### Agent Disconnects Frequently

Check Jenkins master logs and ensure:
- Stable network connection
- Adequate resources on remote VM
- No firewall timeouts

## Updating the Agent

```bash
cd /opt/jenkins-agent
docker compose pull
docker compose up -d --build
```

## Multiple Agents on Same VM

If you need multiple agents on one VM (rare), use different container names:

```yaml
services:
  jenkins-agent-1:
    container_name: jenkins-agent-1
    environment:
      - JENKINS_AGENT_NAME=192.168.2.32-agent1
    # ...
```

