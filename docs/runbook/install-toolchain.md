# Runbook — Installation de la toolchain locale

Ce document décrit l'installation reproductible des outils nécessaires au bootstrap
et au développement du SaaS multi-tenant : Docker Engine, kubectl, k3d, helm.

## Prérequis

- Ubuntu 22.04 (Jammy) ou équivalent Debian
- Accès `sudo` (passwordless recommandé pour l'automatisation)
- `curl`, `gpg`, `apt-transport-https`

## 1. Docker Engine

```bash
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg

echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list

sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io \
     docker-buildx-plugin docker-compose-plugin

sudo usermod -aG docker "$USER"
# Se reloguer ou lancer: newgrp docker
```

## 2. kubectl

```bash
curl -fsSL https://pkgs.k8s.io/core:/stable:/v1.30/deb/Release.key \
  | sudo gpg --dearmor -o /etc/apt/keyrings/kubernetes-apt-keyring.gpg

echo 'deb [signed-by=/etc/apt/keyrings/kubernetes-apt-keyring.gpg] \
https://pkgs.k8s.io/core:/stable:/v1.30/deb/ /' \
  | sudo tee /etc/apt/sources.list.d/kubernetes.list

sudo apt-get update
sudo apt-get install -y kubectl
```

## 3. k3d

```bash
curl -s https://raw.githubusercontent.com/k3d-io/k3d/main/install.sh | bash
```

## 4. helm

Le dépôt baltocdn apt est déprécié/pas fiable. Installation binaire recommandée :

```bash
HELM_VER="4.2.2"   # ajuster si besoin: curl -s https://api.github.com/repos/helm/helm/releases/latest | grep tag_name
curl -fsSL "https://get.helm.sh/helm-v${HELM_VER}-linux-amd64.tar.gz" -o /tmp/helm.tar.gz
sudo tar -xzf /tmp/helm.tar.gz -C /usr/local/bin --strip-components=1 linux-amd64/helm
rm /tmp/helm.tar.gz
```

## 5. Vérifications

```bash
docker run --rm hello-world
docker info | grep -i "storage driver"
k3d version
kubectl version --client
helm version

# Smoke test k3d
k3d cluster create smoke --agents 0
kubectl get nodes
k3d cluster delete smoke
```

## Versions attendues

| Outil    | Version installée |
|----------|-------------------|
| Docker   | 29.6.1            |
| k3d      | 5.9.0 (k3s 1.35.5)|
| kubectl  | 1.30.14           |
| helm     | 4.2.2             |

## Notes

- Le groupe `docker` n'est effectif qu'après re-login. En attendant, utiliser `sudo docker`.
- Le storage driver Docker observé est `overlayfs` (variante d'overlay2), compatible k3d.
- sudo NOPASSWD : `sudo bash -c 'echo "USER ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/USER-nopasswd && chmod 440 /etc/sudoers.d/USER-nopasswd'`