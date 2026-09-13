# GitHub Actions 公网部署

当前仓库的 CI/CD 默认部署目标是一台可以通过 SSH 访问、安装了 Docker Engine 和 Docker Compose v2 的公网 VPS：

```text
Pull Request / push
  -> 后端 Maven + Testcontainers 测试
  -> 前端 Vitest + Vite build
main push
  -> 构建 backend/frontend 镜像
  -> 推送 GitHub Container Registry
  -> SSH 同步生产 Compose、Caddyfile 和环境文件
  -> VPS 拉取指定 Commit 镜像并重启
  -> Caddy 自动申请/续期 HTTPS
  -> https://部署域名/healthz
```

## 1. VPS 前置条件

部署服务器需要：

- Ubuntu 22.04/24.04 或兼容 Linux；
- Docker Engine 和 Docker Compose v2；
- 一个专用部署用户，加入 `docker` 用户组；
- 防火墙放行 TCP 22、80、443；
- DNS 的 A/AAAA 记录已经指向 VPS；
- 部署用户目录中由 Workflow 写入 `DEPLOY_PATH`，不需要手工复制仓库。

Caddy 负责公网入口和 HTTPS，PostgreSQL、Spring Boot 和前端容器只在 Compose 内网通信，不直接暴露数据库或后端端口。PostgreSQL 使用命名卷 `agent-collab-postgres`，仍应在 VPS 上配置备份。

## 2. GitHub Secrets

在仓库 `Settings -> Secrets and variables -> Actions` 中创建以下 Secrets。建议在 `production` Environment 下创建，并为环境设置 required reviewers。

| Secret | 必填 | 用途 |
|---|---:|---|
| `DEPLOY_HOST` | 是 | VPS 公网域名或 IP |
| `DEPLOY_PORT` | 否 | SSH 端口，默认 `22` |
| `DEPLOY_USER` | 是 | VPS 部署用户 |
| `DEPLOY_PATH` | 是 | VPS 上的部署目录，例如 `/opt/agent-collab` |
| `DEPLOY_SSH_KEY` | 是 | 部署用户专用 SSH 私钥；对应公钥写入 `~/.ssh/authorized_keys` |
| `DEPLOY_KNOWN_HOSTS` | 是 | `ssh-keyscan -H <host>` 的完整输出，用于主机密钥校验 |
| `GHCR_USERNAME` | 是 | 能读取该 Package 的 GitHub 用户名 |
| `GHCR_TOKEN` | 是 | GitHub PAT，至少有 `read:packages`，供 VPS `docker login ghcr.io` 使用 |
| `PROD_DEPLOY_DOMAIN` | 是 | Caddy 对外域名，例如 `app.example.com` |
| `PROD_ACME_EMAIL` | 是 | Let's Encrypt 联系邮箱 |
| `PROD_DB_PASSWORD` | 是 | PostgreSQL 密码；建议使用 URL-safe 随机字符串 |
| `PROD_JWT_SECRET` | 是 | JWT 签名密钥，至少 32 字节；不要复用数据库密码 |
| `PROD_GIT_TOKEN` | 否 | GitHub Provider 读取仓库、PR、Checks/Actions 的 Token |
| `PROD_CI_WEBHOOK_SECRET` | 否 | GitHub Webhook HMAC Secret；启用 Webhook 时必须与 GitHub 配置一致 |
| `PROD_AGENT_PROVIDER` | 否 | 设为 `openai` 才启用真实 Agent；留空时使用未配置占位 Provider |
| `PROD_AGENT_API_KEY` | 否 | `PROD_AGENT_PROVIDER=openai` 时的服务端 OpenAI Key |
| `PROD_AGENT_MODEL` | 否 | `PROD_AGENT_PROVIDER=openai` 时的模型 ID |

`GITHUB_TOKEN` 不需要手工创建。它由 GitHub Actions 自动提供，只用于 Workflow 将镜像推送到当前仓库的 GHCR。VPS 端使用单独的 `GHCR_TOKEN`，不要把 PAT 写入仓库或镜像。

`DEPLOY_SSH_KEY` 建议使用只用于部署的专用密钥。`DEPLOY_KNOWN_HOSTS` 可以在可信终端生成：

```bash
ssh-keyscan -H your-vps.example.com
```

## 3. GitHub Container Registry

Workflow 会推送两个镜像：

```text
ghcr.io/<owner>/agent-collab-backend:<commit-sha>
ghcr.io/<owner>/agent-collab-frontend:<commit-sha>
```

部署使用 Commit SHA 标签，不依赖漂移的 `latest`。首次部署前，使用 `GHCR_USERNAME/GHCR_TOKEN` 在 VPS 上验证：

```bash
echo "$GHCR_TOKEN" | docker login ghcr.io --username "$GHCR_USERNAME" --password-stdin
```

## 4. 发布和回滚

- Pull Request：只运行后端和前端测试，不发布、不部署；
- `main` push：测试通过后发布镜像并部署 `main` 的 Commit；
- `workflow_dispatch`：当前用于手动触发 Workflow；生产部署仍要求 push 到 `main` 的条件；
- 回滚：在 VPS 的 `.env` 中把 `IMAGE_TAG` 改为上一个已发布 Commit SHA，然后执行：

```bash
cd /opt/agent-collab
docker compose --env-file .env -f compose.prod.yaml pull
docker compose --env-file .env -f compose.prod.yaml up -d --remove-orphans
```

数据库迁移由后端启动时 Flyway 执行。回滚应用镜像前必须确认迁移是否向后兼容；不要通过删除 PostgreSQL 命名卷来回滚数据。
