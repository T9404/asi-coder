# asi-coder

## Docker Compose Setup

This repository includes Docker Compose configuration for easy containerized deployment.

### Prerequisites

- Docker Engine 20.10+
- Docker Compose V2 or docker-compose 1.29+

### Quick Start

Start all services:
```bash
docker compose up -d
```

Stop all services:
```bash
docker compose down
```

View logs:
```bash
docker compose logs -f
```

### Services

The Docker Compose setup includes:

- **app**: Main application container (Node.js)
  - Port: 8080
  - Auto-restart enabled
  
- **db**: PostgreSQL 15 database
  - Port: 5432
  - Persistent data storage
  - Health checks enabled
  
- **redis**: Redis 7 cache
  - Port: 6379
  - Persistent data storage
  - Health checks enabled

### Configuration

Environment variables can be customized by creating a `.env` file:

```bash
cp .env.example .env
# Edit .env with your preferred values
```

Key environment variables:
- `POSTGRES_USER`: Database username (default: user)
- `POSTGRES_PASSWORD`: Database password (default: password) ⚠️ **Change in production**
- `POSTGRES_DB`: Database name (default: asidb)
- `NODE_ENV`: Application environment (default: production)

### Data Persistence

Data is persisted using Docker volumes:
- `db-data`: PostgreSQL database files
- `redis-data`: Redis data files

### Development

For development with hot-reload:
```bash
docker compose watch
```

### Troubleshooting

Check service health:
```bash
docker compose ps
```

Rebuild containers:
```bash
docker compose up -d --build
```

Remove all data (⚠️ destructive):
```bash
docker compose down -v
```