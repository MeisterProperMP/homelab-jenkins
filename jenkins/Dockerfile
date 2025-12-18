# Custom Jenkins Image mit Docker CLI und Docker Compose
FROM jenkins/jenkins:lts

USER root

# Docker CLI installieren
RUN apt-get update && apt-get install -y \
    apt-transport-https \
    ca-certificates \
    curl \
    gnupg \
    jq \
    && install -m 0755 -d /etc/apt/keyrings \
    && curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc \
    && chmod a+r /etc/apt/keyrings/docker.asc \
    && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian bookworm stable" > /etc/apt/sources.list.d/docker.list \
    && apt-get update \
    && apt-get install -y docker-ce-cli docker-compose-plugin \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Jenkins User zur Docker Gruppe hinzufügen
RUN groupadd -g 999 docker || true \
    && usermod -aG docker jenkins

USER jenkins

# Standard-Plugins installieren
RUN jenkins-plugin-cli --plugins \
    git \
    workflow-aggregator \
    pipeline-stage-view \
    blueocean \
    docker-workflow \
    credentials-binding \
    timestamper \
    ansicolor

