# MiniMall 开发快速启动

本机自测时的常用命令清单。后端用 Docker Compose 整套起，客户前端与管理员后台分别用本地 Vite dev server 启动，并统一通过 api-gateway (`localhost:8080`) 通讯。

---

## 1. 启动后端

```bash
cd /home/oslab/projects/mini-mall-order
docker compose up -d
```

首次会拉镜像 + 构建 Java 服务，约 5–10 分钟；之后冷启动 30–90 秒。等到所有服务 `(healthy)`：

```bash
docker compose ps
# 简洁视图：
docker compose ps --format 'table {{.Service}}\t{{.Status}}'
```

跟日志（调试用）：

```bash
docker compose logs -f api-gateway      # 网关
docker compose logs -f order-service    # 单个服务
docker compose logs -f                  # 全部
```

停掉（保留数据）：

```bash
docker compose down
```

完全重置（**会删 mysql/redis/rabbitmq 数据**）：

```bash
docker compose down -v
```

### 后端端点

| 服务 | 地址 | 备注 |
|---|---|---|
| api-gateway | `http://localhost:8080` | 前端**只能**调这一个 |
| Grafana | `http://localhost:3000` | 监控仪表盘 |
| Prometheus | `http://localhost:9090` | 指标采集 |
| RabbitMQ 管理台 | `http://localhost:15672` | 消息队列管理 |

---

## 2. 启动客户前端

```bash
cd /home/oslab/projects/mini-mall-order/frontend
npm install        # 仅首次或 package.json 变更后
npm run dev        # 默认 http://localhost:5173/
```

前端默认通过 `frontend/.env.local` 把 `VITE_API_BASE_URL` 指向 `http://localhost:8080`。

### 后台跑（不占终端）

```bash
nohup npm run dev > /tmp/vite.log 2>&1 &
tail -f /tmp/vite.log     # 看日志
fuser -k 5173/tcp         # 关掉客户前端端口
```

---

## 3. 启动管理员后台

```bash
cd /home/oslab/projects/mini-mall-order/admin-frontend
npm install        # 仅首次或 package.json 变更后
npm run dev        # 默认 http://localhost:5174/
```

管理员后台默认通过 `admin-frontend/.env.local` 把 `VITE_API_BASE_URL` 指向 `http://localhost:8080`。

### 后台跑（不占终端）

```bash
nohup npm run dev > /tmp/admin-vite.log 2>&1 &
tail -f /tmp/admin-vite.log     # 看日志
fuser -k 5174/tcp               # 关掉管理员后台端口
```

---

## 4. 前端构建 / 测试

```bash
cd /home/oslab/projects/mini-mall-order/frontend

npm run build         # 生产构建（vue-tsc 类型检查 + vite 打包）
npm run preview       # 跑构建产物，默认 http://localhost:4173/
npm run test          # 跑一次 vitest
npm run test:watch    # 文件变化自动跑 vitest
```

管理员后台同理：

```bash
cd /home/oslab/projects/mini-mall-order/admin-frontend

npm run build
npm run preview       # 跑构建产物，默认 http://localhost:4173/
npm run test
npm run test:watch
```

---

## 5. 一键全停

```bash
fuser -k 5173/tcp
fuser -k 5174/tcp
cd /home/oslab/projects/mini-mall-order && docker compose down
```

---

## 6. 常用排查

端口被占用：

```bash
ss -tlnp | grep -E '8080|5173|5174'
```

网关健康检查：

```bash
curl -s http://localhost:8080/actuator/health | jq
```

前端能否调通后端：

```bash
curl -i http://localhost:8080/api/products
```

---

## 7. 典型测试工作流

```bash
# 终端 1：起后端
cd /home/oslab/projects/mini-mall-order
docker compose up -d
docker compose ps          # 等到全 (healthy)

# 终端 2：起前端
cd /home/oslab/projects/mini-mall-order/frontend
npm run dev                # 留这个终端开着看 HMR 日志

# 浏览器
# 打开 http://localhost:5173/
```

管理员后台测试：

```bash
# 终端 3：起管理员后台
cd /home/oslab/projects/mini-mall-order/admin-frontend
npm run dev                # 默认 http://localhost:5174/

# 浏览器
# 打开 http://localhost:5174/
```

### Phase 3 AI 演示数据

Phase 3 demo data is disabled by default. To seed repeatable local AI inventory
demo rows in Compose, set these values in `.env` before starting or recreating
`inventory-service`:

```bash
INVENTORY_SERVICE_SPRING_PROFILES_ACTIVE=dev
MINIMALL_DEMO_DATA_ENABLED=true
```

The seed runs only when `inventory-service` has the `dev` or `test` Spring
profile and refuses `prod` / `production`. It creates deterministic product,
inventory, sales, AI suggestion, and inbound-order review rows with business
keys such as `PH3-AI-LOW-TEA`, `PH3-AI-SUG-PENDING`,
`PH3-AI-SUG-DRAFT`, and `PH3-AI-INB-APPLIED`. Re-running the service refreshes
those demo rows without duplicates.

测完关：

- 终端 2：`Ctrl+C` 停 vite
- 终端 3：`Ctrl+C` 停管理员后台 vite
- 终端 1：`docker compose down`（数据卷保留，下次起来还在）
