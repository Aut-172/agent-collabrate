# GitHub Actions + ACR + ECS 公网部署

当前仓库的 CI/CD 部署目标是一台可以通过 SSH 访问、安装了 Docker Engine 和 Docker Compose v2 的阿里云 ECS：

```text
Pull Request / push
  -> 后端 Maven + Testcontainers 测试
  -> 前端 Vitest + Vite build
main push
  -> 构建 backend/frontend 镜像
  -> 推送阿里云 ACR
  -> SSH 同步生产 Compose、Caddyfile 和镜像环境文件到 ECS
  -> ECS 拉取指定 Commit 镜像并重启
  -> Caddy 自动申请/续期 HTTPS
  -> ECS 本机和公网 HTTPS /healthz 检查
```

## 1. ECS 前置条件

ECS 需要准备：

- Ubuntu 22.04/24.04 或兼容 Linux；
- Docker Engine 和 Docker Compose v2；
- 一个专用部署用户，加入 `docker` 用户组；
- 安全组/防火墙放行 TCP `22`、`80`、`443`；
- DNS 的 A/AAAA 记录指向 ECS；
- `/opt/agent-collab/.env` 由管理员首次创建，权限设为 `600`。

Caddy 负责公网入口和 HTTPS，PostgreSQL、Spring Boot 和前端容器只在 Compose 内网通信，不直接暴露数据库或后端端口。PostgreSQL 使用命名卷 `agent-collab-postgres`，仍应在 ECS 上配置备份。

首次部署前，在 ECS 创建运行时 `.env`（不要提交到仓库）：

```dotenv
DB_NAME=agent_collab
DB_USERNAME=agent_collab
DB_PASSWORD=<strong-database-password>
JWT_SECRET=<at-least-32-byte-secret>
DEPLOY_DOMAIN=app.example.com
ACME_EMAIL=ops@example.com
GIT_PROVIDER=github
GIT_API_URL=https://api.github.com
GIT_TOKEN=
CI_WEBHOOK_SECRET=
AGENT_PROVIDER=unconfigured
AGENT_API_KEY=
AGENT_MODEL=
```

`DEPLOY_DOMAIN` 必须解析到 ECS，Caddy 才能申请公网证书。Workflow 每次只更新 `.env` 中的 `IMAGE_NAMESPACE` 和 `IMAGE_TAG`，不会覆盖上述运行时凭证。

## 2. GitHub Environment Secrets

在仓库 `Settings -> Environments -> production -> Environment secrets` 中创建以下 Secrets。Workflow 使用的名称必须与下表完全一致：

| Secret | 必填 | 用途 |
|---|---:|---|
| `ECS_HOST` | 是 | ECS 公网 IP 或 SSH 域名 |
| `ECS_SSH_KEY` | 是 | ECS 部署用户的 SSH 私钥；公钥写入 `~/.ssh/authorized_keys` |
| `ECS_SSH_PORT` | 是 | SSH 端口，通常为 `22` |
| `ECS_USER` | 是 | ECS 部署用户，必须能执行 Docker |
| `ACR_USERNAME` | 是 | 阿里云 ACR 用户名 |
| `ACR_NAMESPACE` | 是 | ACR 命名空间，例如 `agent-collab` |
| `ACR_PASSWORD` | 是 | ACR 登录密码或访问凭证 |
| `ACR_PULL_REGISTRY` | 是 | ECS 拉取镜像的 ACR Registry 地址；可使用 ECS 内网 Registry 地址 |
| `ACR_REGISTRY` | 是 | GitHub Actions 推送镜像的 ACR Registry 地址 |

建议对 `production` Environment 设置 required reviewers。`ACR_REGISTRY` 和 `ACR_PULL_REGISTRY` 只填写主机名，不要带 `https://` 或末尾 `/`，例如：

```text
registry.cn-hangzhou.aliyuncs.com
registry-vpc.cn-hangzhou.aliyuncs.com
```

Workflow 发布的镜像为：

```text
<ACR_REGISTRY>/<ACR_NAMESPACE>/agent-collab-backend:<commit-sha>
<ACR_REGISTRY>/<ACR_NAMESPACE>/agent-collab-frontend:<commit-sha>
```

ECS 使用 `ACR_PULL_REGISTRY` 拉取相同命名空间和 Commit SHA 的镜像。若 ECS 不能访问 ACR 内网地址，将 `ACR_PULL_REGISTRY` 设置为 ECS 可访问的公网 Registry 地址。

SSH 私钥建议使用专用 Ed25519 密钥。由于当前 Secrets 清单没有单独的 known-host Secret，Workflow 会在运行器上通过 `ssh-keyscan` 获取 `ECS_HOST` 的主机密钥；生产环境建议定期审阅并固定该主机密钥。

## 3. 发布、部署和回滚

- Pull Request：只运行后端和前端测试，不推送镜像、不部署；
- `main` push：测试通过后推送 ACR 镜像，并 SSH 部署到 `/opt/agent-collab`；
- `workflow_dispatch`：可手动运行测试，但生产部署仍只对 `main` push 生效；
- 部署前会检查 ECS `.env` 中的 `DB_PASSWORD`、`JWT_SECRET`、`DEPLOY_DOMAIN` 和 `ACME_EMAIL`；缺少时会明确失败，不会使用示例凭证；
- 回滚时在 ECS 的 `.env` 中将 `IMAGE_TAG` 改为上一个已发布 Commit SHA，然后执行：

```bash
cd /opt/agent-collab
docker compose --env-file .env -f compose.prod.yaml pull
docker compose --env-file .env -f compose.prod.yaml up -d --remove-orphans
```

数据库迁移由后端启动时 Flyway 执行。回滚应用镜像前必须确认迁移是否向后兼容；不要通过删除 PostgreSQL 命名卷来回滚数据。
